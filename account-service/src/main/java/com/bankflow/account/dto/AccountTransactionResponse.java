package com.bankflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionResponse(
        Long id,
        String transactionId,
        String accountId,
        BigDecimal amount,
        String operationType,
        String description,
        String counterpartyAccount,
        BigDecimal balanceAfter,
        Instant createdAt
)
{}