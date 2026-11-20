package com.bankflow.common.dto;

import com.bankflow.common.enums.NotificationChannel;

import java.time.Instant;

public record NotificationPayload(
        String notificationId,
        String customerId,
        String transactionId,
        String recipient,
        String subject,
        String message,
        NotificationChannel channel,
        Instant createdAt
) {
}