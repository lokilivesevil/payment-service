package com.example.payment.service;

import com.example.payment.client.LedgerClient;
import com.example.payment.client.dto.BalanceResponse;
import com.example.payment.client.dto.TransferRequest;
import com.example.payment.client.dto.TransferResponse;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.event.PaymentCompletedEvent;
import com.example.payment.event.PaymentEventPublisher;
import com.example.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final LedgerClient ledgerClient;
    private final PaymentEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository, LedgerClient ledgerClient, PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.ledgerClient = ledgerClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @CircuitBreaker(name = "ledgerService", fallbackMethod = "fallbackProcessPayment")
    public PaymentResponse processPayment(PaymentRequest request) {
        // Idempotency check
        Optional<Payment> existingPaymentOpt = paymentRepository.findById(request.getId());
        if (existingPaymentOpt.isPresent()) {
            Payment existingPayment = existingPaymentOpt.get();
            if (existingPayment.getStatus() == PaymentStatus.COMPLETED || existingPayment.getStatus() == PaymentStatus.PUBLISHED) {
                log.info("Idempotent request received. Returning existing completed payment: {}", existingPayment.getId());
                return new PaymentResponse(existingPayment.getId(), existingPayment.getStatus().name());
            } else if (existingPayment.getStatus() == PaymentStatus.PENDING) {
                log.info("Payment exists and is PENDING. Returning PENDING response: {}", existingPayment.getId());
                return new PaymentResponse(existingPayment.getId(), PaymentStatus.PENDING.name());
            }
            log.info("Payment exists with status {}. Re-processing: {}", existingPayment.getStatus(), existingPayment.getId());
        }

        // Save as PENDING if not exists or failed
        Payment payment = existingPaymentOpt.orElseGet(() -> new Payment(
                request.getId(),
                request.getPayerAccountId(),
                request.getPayeeAccountId(),
                request.getMinorAmount(),
                request.getCurrency(),
                request.getDescription(),
                PaymentStatus.PENDING
        ));
        
        if (payment.getStatus() != PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PENDING);
        }
        
        payment = paymentRepository.save(payment);
        log.info("Saved pending payment: {}", payment.getId());

        try {
            // Milestone 2: Balance Check
            BalanceResponse balanceResponse = ledgerClient.getBalance(payment.getPayerAccountId());
            if (balanceResponse.getAvailableBalanceMinorAmount() < payment.getMinorAmount()) {
                log.warn("Insufficient balance for payment: {}", payment.getId());
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw new InsufficientBalanceException("Insufficient balance");
            }

            // Milestone 2: Execute Transfer
            TransferRequest transferRequest = new TransferRequest(
                    payment.getId(), // Add paymentId mapped in previous manual user change
                    payment.getPayerAccountId(),
                    payment.getPayeeAccountId(),
                    payment.getMinorAmount(),
                    payment.getCurrency()
            );
            TransferResponse transferResponse = ledgerClient.executeTransfer(transferRequest);

            if ("SUCCESS".equals(transferResponse.getStatus())) {
                log.info("Transfer successful for payment: {}", payment.getId());
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setLedgerTransferId(transferResponse.getTransferId());
                paymentRepository.save(payment);

                // Milestone 3: Publish Event
                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        payment.getId(),
                        payment.getPayerAccountId(),
                        payment.getPayeeAccountId(),
                        payment.getMinorAmount(),
                        payment.getCurrency(),
                        payment.getLedgerTransferId()
                );
                eventPublisher.publishPaymentCompleted(event);

                payment.setStatus(PaymentStatus.PUBLISHED);
                paymentRepository.save(payment);
                
                return new PaymentResponse(payment.getId(), PaymentStatus.PUBLISHED.name());
            } else {
                log.error("Transfer failed with status {} for payment: {}", transferResponse.getStatus(), payment.getId());
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw new TransferExecutionException("Transfer execution failed");
            }

        } catch (InsufficientBalanceException e) {
            // Already handled, status set to FAILED
            throw e;
        } catch (Exception e) {
            // Timeout or ledger exception -> leave it PENDING for retry scheduler
            log.error("Error during payment processing, leaving it PENDING for retry mechanism. Error: {}", e.getMessage());
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
            throw e;
        }
    }

    public PaymentResponse fallbackProcessPayment(PaymentRequest request, io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.error("Circuit breaker OPEN. Ledger service unavailable for payment: {}", request.getId());
        throw new ServiceUnavailableException("Ledger Service is currently unavailable");
    }
}
