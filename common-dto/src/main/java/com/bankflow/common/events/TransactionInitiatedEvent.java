package com.bankflow.common.events;

import com.bankflow.common.enums.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionInitiatedEvent(
        String eventId,
        String transactionId,
        String idempotencyKey,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        String currency,
        PaymentMode paymentMode,
        String clientIp,
        String deviceFingerprint,
        String userAgent,
        String remarks,
        Instant occuredAt
) {
}