package com.bankflow.common.events;

import com.bankflow.common.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionApprovedEvent(
        String eventId,
        String transactionId,
        String fromAccountId,
        BigDecimal amount,
        double riskScore,
        RiskLevel riskLevel,
        String evaluationSource,
        Instant evaluatedAt
){
}