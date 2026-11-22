package com.bankflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        String id,
        String accountNumber,
        String customerId,
        String customerName,
        String email,
        String phoneNumber,
        String currency,
        BigDecimal balance,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}