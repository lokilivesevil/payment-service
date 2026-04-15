package com.example.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private UUID id;

    @Column(name = "payer_account_id", nullable = false)
    private UUID payerAccountId;

    @Column(name = "payee_account_id", nullable = false)
    private UUID payeeAccountId;

    @Column(name = "minor_amount", nullable = false)
    private Long minorAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "ledger_transfer_id")
    private String ledgerTransferId;

    @Column(name = "retry_attempts", nullable = false)
    private Integer retryAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Payment(UUID id, UUID payerAccountId, UUID payeeAccountId, Long minorAmount, String currency, String description, PaymentStatus status) {
        this.id = id;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.minorAmount = minorAmount;
        this.currency = currency;
        this.description = description;
        this.status = status;
        this.retryAttempts = 0;
    }
}
