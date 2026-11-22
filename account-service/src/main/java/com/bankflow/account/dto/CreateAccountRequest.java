package com.bankflow.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String customerId,
        @NotBlank String customerName,
        @NotBlank @Email String email,
        @NotBlank String phoneNumber,
        String currency,
        @DecimalMin(value="0.00", inclusive=true) BigDecimal initialBalance
) {}