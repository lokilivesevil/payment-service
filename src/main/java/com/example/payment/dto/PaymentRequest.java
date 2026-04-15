package com.example.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Id is required")
    private UUID id;

    @NotNull(message = "Payer account ID is required")
    private UUID payerAccountId;

    @NotNull(message = "Payee account ID is required")
    private UUID payeeAccountId;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be strictly positive")
    private Long minorAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    private String description;
}
