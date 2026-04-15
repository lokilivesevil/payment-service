package com.example.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    private UUID paymentId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private Long minorAmount;
    private String currency;
}
