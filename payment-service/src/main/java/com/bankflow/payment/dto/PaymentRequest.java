package com.bankflow.payment.dto;

import com.bankflow.common.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotBlank(message = "fromAccountId is required")
    @Size(max=64, message = "fromAccountId must not exceed 64 characters")
    String fromAccountId,

    @NotBlank(message = "toAccountId is required")
    @Size(max=64, message = "toAccountId must not exceed 64 characters")
    String toAccountId,

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "amount must be greater than zero")
    BigDecimal amount,

    @NotNull(message = "paymentMode is required")
    PaymentMode paymentMode,

    @Size(max = 255, message = "remarks must not exceed 255 characters")
    String remarks
    ){
}
