package com.bankflow.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record BalanceOperationRequest(
        @NotBlank String transactionId,
        @NotBlank @Pattern(regexp = "DEBIT|CREDIT") String operationType,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
        String counterpartyAccountId,
        String description
) {
}