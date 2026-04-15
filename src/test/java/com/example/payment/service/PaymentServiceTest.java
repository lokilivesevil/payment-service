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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private LedgerClient ledgerClient;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private UUID paymentId;
    private PaymentRequest paymentRequest;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        paymentRequest = new PaymentRequest(
                paymentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                100L,
                "AED",
                "Rent"
        );
        payment = new Payment(
                paymentId,
                paymentRequest.getPayerAccountId(),
                paymentRequest.getPayeeAccountId(),
                paymentRequest.getMinorAmount(),
                paymentRequest.getCurrency(),
                paymentRequest.getDescription(),
                PaymentStatus.PENDING
        );
    }

    @Test
    void processPayment_success() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        BalanceResponse balanceResponse = new BalanceResponse(paymentRequest.getPayerAccountId(), 500L, "AED");
        when(ledgerClient.getBalance(paymentRequest.getPayerAccountId())).thenReturn(balanceResponse);

        TransferResponse transferResponse = new TransferResponse("txn-123", "SUCCESS");
        when(ledgerClient.executeTransfer(any(TransferRequest.class))).thenReturn(transferResponse);

        PaymentResponse response = paymentService.processPayment(paymentRequest);

        assertThat(response.getId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PUBLISHED.name());

        verify(paymentRepository, times(3)).save(any(Payment.class));
        verify(eventPublisher, times(1)).publishPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void processPayment_insufficientBalance() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        BalanceResponse balanceResponse = new BalanceResponse(paymentRequest.getPayerAccountId(), 50L, "AED"); // Less than 100
        when(ledgerClient.getBalance(paymentRequest.getPayerAccountId())).thenReturn(balanceResponse);

        assertThatThrownBy(() -> paymentService.processPayment(paymentRequest))
                .isInstanceOf(InsufficientBalanceException.class);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        
        assertThat(paymentCaptor.getAllValues().get(1).getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(ledgerClient, never()).executeTransfer(any());
    }

    @Test
    void processPayment_idempotentCompleted() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.processPayment(paymentRequest);

        assertThat(response.getId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED.name());
        verify(ledgerClient, never()).getBalance(any());
    }

    @Test
    void processPayment_ledgerException_leavesPending() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        when(ledgerClient.getBalance(paymentRequest.getPayerAccountId())).thenThrow(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> paymentService.processPayment(paymentRequest))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());

        // Should be PENDING as per new logic
        assertThat(paymentCaptor.getAllValues().get(1).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
