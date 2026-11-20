package com.bankflow.common.events;

import com.bankflow.common.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FraudAlertEvent(
        String eventId,
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        double riskScore,
        RiskLevel risklevel,
        List<String> triggeredRules,
        String rationale,
        Instant detectedAt
){
}