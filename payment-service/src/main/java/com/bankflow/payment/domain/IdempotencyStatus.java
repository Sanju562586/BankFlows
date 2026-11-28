package com.bankflow.payment.domain;

public enum IdempotencyStatus {
    IN_PROGRESS,
    PROCESSED,
    FAILED
}