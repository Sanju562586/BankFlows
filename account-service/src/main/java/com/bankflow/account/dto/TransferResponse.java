package com.bankflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        BigDecimal senderBalanceAfter,
        String status,
        Instant completedAt
) {}
