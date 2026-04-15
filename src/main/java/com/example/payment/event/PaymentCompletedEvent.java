package com.example.payment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private UUID paymentId;
    private UUID payerAccountId;
    private UUID payeeAccountId;
    private Long minorAmount;
    private String currency;
    private String ledgerTransferId;
}
