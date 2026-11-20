package com.bankflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceUpdatedEvent(
        String eventId,
        String transactionId,
        String accountId,
        String operationType,
        BigDecimal amount,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        String counterpartyAccountId,
        Instant updatedAt
){
}