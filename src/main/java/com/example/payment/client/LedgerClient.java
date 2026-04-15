package com.example.payment.client;

import com.example.payment.client.dto.BalanceResponse;
import com.example.payment.client.dto.TransferRequest;
import com.example.payment.client.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);
    private final RestClient restClient;

    public LedgerClient(RestClient.Builder restClientBuilder,
            @Value("${ledger.service.url:http://localhost:8081}") String ledgerUrl) {
        this.restClient = restClientBuilder.baseUrl(ledgerUrl).build();
    }

    public BalanceResponse getBalance(UUID accountId) {
        log.info("Fetching balance for account: {}", accountId);
        return restClient.get()
                .uri("/api/ledger/accounts/{accountId}/balance", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new RuntimeException("Ledger client error: " + response.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new RuntimeException("Ledger server error: " + response.getStatusCode());
                })
                .body(BalanceResponse.class);
    }

    public TransferResponse executeTransfer(TransferRequest request) {
        log.info("Executing transfer from {} to {}", request.getFromAccountId(), request.getToAccountId());
        return restClient.post()
                .uri("/api/ledger/transfers")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, response) -> {
                    throw new RuntimeException("Ledger transfer error: " + response.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, response) -> {
                    throw new RuntimeException("Ledger server error: " + response.getStatusCode());
                })
                .body(TransferResponse.class);
    }
}
