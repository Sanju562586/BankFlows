package com.bankflow.payment.service;

import com.bankflow.common.enums.TransactionStatus;
import com.bankflow.payment.config.PaymentProperties;
import com.bankflow.payment.domain.IdempotencyRecord;
import com.bankflow.payment.domain.IdempotencyStatus;
import com.bankflow.payment.domain.PaymentTransaction;
import com.bankflow.payment.dto.PaymentRequest;
import com.bankflow.payment.dto.PaymentResponse;
import com.bankflow.payment.exception.DuplicatePaymentException;
import com.bankflow.payment.exception.PaymentNotFoundException;
import com.bankflow.payment.exception.PaymentValidationException;
import com.bankflow.payment.repository.IdempotencyRecordRepository;
import com.bankflow.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ResponseEntity<PaymentResponse> initiatePayment(String idempotencyKey, PaymentRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        validateRequest(request);

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findById(normalizedKey);
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (existing.getStatus() == IdempotencyStatus.PROCESSED && existing.getResponseBody() != null) {
                PaymentResponse cachedResponse = deserializeResponse(existing.getResponseBody());
                int cachedStatus = existing.getHttpStatus() != null
                        ? existing.getHttpStatus()
                        : HttpStatus.ACCEPTED.value();
                return ResponseEntity.status(cachedStatus).body(cachedResponse);
            }
            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw new DuplicatePaymentException(
                        "Payment with idempotency key '" + normalizedKey + "' is currently being processed"
                );
            }
            throw new DuplicatePaymentException(
                    "Idempotency key '" + normalizedKey + "' has already been used"
            );
        }

        String transactionId = "txn-" + UUID.randomUUID();
        IdempotencyRecord reservation = IdempotencyRecord.builder()
                .idempotencyKey(normalizedKey)
                .transactionId(transactionId)
                .status(IdempotencyStatus.IN_PROGRESS)
                .httpStatus(HttpStatus.ACCEPTED.value())
                .build();

        try {
            idempotencyRecordRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord raced = idempotencyRecordRepository.findById(normalizedKey).orElse(null);
            if (raced != null && raced.getStatus() == IdempotencyStatus.PROCESSED && raced.getResponseBody() != null) {
                PaymentResponse cachedResponse = deserializeResponse(raced.getResponseBody());
                int cachedStatus = raced.getHttpStatus() != null
                        ? raced.getHttpStatus()
                        : HttpStatus.ACCEPTED.value();
                return ResponseEntity.status(cachedStatus).body(cachedResponse);
            }
            throw new DuplicatePaymentException(
                    "Concurrent or duplicate request detected for idempotency key '" + normalizedKey + "'"
            );
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(transactionId)
                .idempotencyKey(normalizedKey)
                .fromAccountId(request.fromAccountId().trim())
                .toAccountId(request.toAccountId().trim())
                .amount(request.amount())
                .currency("INR")
                .paymentMode(request.paymentMode())
                .status(TransactionStatus.PENDING_FRAUD_CHECK)
                .sagaState("STARTED")
                .remarks(request.remarks() != null ? request.remarks().trim() : null)
                .build();

        PaymentTransaction saved = paymentTransactionRepository.saveAndFlush(transaction);
        PaymentResponse response = PaymentResponse.from(saved);

        reservation.setStatus(IdempotencyStatus.PROCESSED);
        reservation.setHttpStatus(HttpStatus.ACCEPTED.value());
        reservation.setResponseBody(serializeResponse(response));
        idempotencyRecordRepository.save(reservation);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(String transactionId) {
        PaymentTransaction tx = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + transactionId));
        return PaymentResponse.from(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByIdempotencyKey(String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        PaymentTransaction tx = paymentTransactionRepository.findByIdempotencyKey(normalizedKey)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for idempotency key: " + normalizedKey
                ));
        return PaymentResponse.from(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByAccountId(String accountId) {
        String trimmed = accountId == null ? "" : accountId.trim();
        if (trimmed.isEmpty()) {
            throw new PaymentValidationException("accountId must not be blank");
        }
        return paymentTransactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(trimmed, trimmed)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentValidationException("Idempotency-Key header is required");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 128) {
            throw new PaymentValidationException("Idempotency-Key must not exceed 128 characters");
        }
        return trimmed;
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null) {
            throw new PaymentValidationException("Payment request body is required");
        }
        String from = request.fromAccountId().trim();
        String to = request.toAccountId().trim();
        if (from.equalsIgnoreCase(to)) {
            throw new PaymentValidationException("Self-transfer is not allowed: source and destination accounts must differ");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException("Payment amount must be greater than zero");
        }

        if (request.paymentMode() == null) {
            throw new PaymentValidationException("Payment mode is required");
        }

        switch (request.paymentMode()) {
            case UPI -> {
                if (amount.compareTo(paymentProperties.getLimits().getUpiMax()) > 0) {
                    throw new PaymentValidationException(
                            "UPI amount exceeds maximum limit of " + paymentProperties.getLimits().getUpiMax()
                    );
                }
            }
            case IMPS -> {
                if (amount.compareTo(paymentProperties.getLimits().getImpsMax()) > 0) {
                    throw new PaymentValidationException(
                            "IMPS amount exceeds maximum limit of " + paymentProperties.getLimits().getImpsMax()
                    );
                }
            }
            case NEFT -> {
                // No upper cap configured for NEFT
            }
        }
    }

    private String serializeResponse(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize payment response for idempotency cache", ex);
        }
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize cached payment response", ex);
        }
    }
}