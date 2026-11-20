package com.bankflow.common;

import com.bankflow.common.dto.BalanceOperationRequest;
import com.bankflow.common.enums.NotificationChannel;
import com.bankflow.common.enums.PaymentMode;
import com.bankflow.common.enums.RiskLevel;
import com.bankflow.common.enums.TransactionStatus;
import com.bankflow.common.events.FraudAlertEvent;
import com.bankflow.common.events.TransactionInitiatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonDtoSerializationTest{
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void shouldSerializeAndDeserializeTransactionInitiatedEvent() throws Exception {
        Instant now = Instant.parse("2026-11-20T07:10:00Z");
        TransactionInitiatedEvent original = new TransactionInitiatedEvent(
                "evt-1",
                "txn-100",
                "idem-100",
                "acc-101",
                "acc-102",
                new BigDecimal("12500.00"),
                "INR",
                PaymentMode.UPI,
                "127.0.0.1",
                "fp-abc",
                "Mozilla/5.0",
                "Rent",
                now
        );

        String json = objectMapper.writeValueAsString(original);
        TransactionInitiatedEvent restored = objectMapper.readValue(json, TransactionInitiatedEvent.class);

        assertEquals(original, restored);
        assertEquals(PaymentMode.UPI, restored.paymentMode());
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.valueOf("COMPLETED"));
        assertEquals(RiskLevel.LOW, RiskLevel.valueOf("LOW"));
        assertEquals(NotificationChannel.EMAIL, NotificationChannel.valueOf("EMAIL"));
    }

    @Test
    void shouldSerializeAndDeserializeBalanceOperationAndFraudAlert() throws Exception {
        BalanceOperationRequest req = new BalanceOperationRequest(
                "txn-100",
                "DEBIT",
                new BigDecimal("500.00"),
                "acc-102",
                "Transfer debit"
        );
        FraudAlertEvent alert = new FraudAlertEvent(
                "evt-2",
                "txn-100",
                "acc-101",
                "acc-102",
                new BigDecimal("95000.00"),
                0.88,
                RiskLevel.CRITICAL,
                List.of("HIGH_AMOUNT_RATIO", "NEW_DEVICE"),
                "High value from new device",
                Instant.parse("2026-11-20T07:11:00Z")
        );

        assertEquals(req, objectMapper.readValue(objectMapper.writeValueAsString(req), BalanceOperationRequest.class));
        assertEquals(alert, objectMapper.readValue(objectMapper.writeValueAsString(alert), FraudAlertEvent.class));
    }
}