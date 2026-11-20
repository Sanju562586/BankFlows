package com.bankflow.common.events;

import com.bankflow.common.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRejectedEvent(
        String eventId,
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        RiskLevel risklevel,
        String reason,
        Instant rejectedAt
){
}