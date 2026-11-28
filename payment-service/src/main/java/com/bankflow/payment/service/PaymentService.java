package com.bankflow.payment.service;

import com.bankflow.payment.dto.PaymentRequest;
import com.bankflow.payment.dto.PaymentResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentService {
    ResponseEntity<PaymentResponse> initiatePayment(String idempotencyKey, PaymentRequest request);

    PaymentResponse getPaymentById(String transactionId);

    PaymentResponse getPaymentByIdempotencyKey(String idempotencyKey);

    List<PaymentResponse> getPaymentsByAccountId(String accountId);
}
