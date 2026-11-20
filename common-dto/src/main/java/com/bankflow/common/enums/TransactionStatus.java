package com.bankflow.common.enums;

public enum TransactionStatus {
    INITIATED,
    PENDING_FRAUD_CHECK,
    FRAUD_FLAGGED,
    APPROVED,
    REJECTED,
    COMPLETED,
    COMPENSATED,
    FAILED
}
