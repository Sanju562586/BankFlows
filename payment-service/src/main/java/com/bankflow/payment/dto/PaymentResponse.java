package com.bankflow.payment.dto;

import com.bankflow.common.enums.PaymentMode;
import com.bankflow.common.enums.TransactionStatus;
import com.bankflow.payment.domain.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String transactionId,
        String idempotencyKey,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        String currency,
        PaymentMode paymentMode,
        TransactionStatus status,
        String sagaState,
        String remarks,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(PaymentTransaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getIdempotencyKey(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getPaymentMode(),
                tx.getStatus(),
                tx.getSagaState(),
                tx.getRemarks(),
                tx.getFailureReason(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}
