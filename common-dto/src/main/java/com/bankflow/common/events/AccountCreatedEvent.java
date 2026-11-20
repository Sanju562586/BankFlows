package com.bankflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountCreatedEvent(
        String eventId,
        String accountId,
        String accountNumber,
        String customerId,
        String customerName,
        String email,
        String phoneNumber,
        String currency,
        BigDecimal initialBalance,
        Instant createdAt
) {
}