package com.bankflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        String accountId,
        String accountNumber,
        String currency,
        BigDecimal balance,
        Instant updatedAt
) {}