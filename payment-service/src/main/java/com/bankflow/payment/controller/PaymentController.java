package com.bankflow.payment.controller;

import com.bankflow.payment.dto.PaymentRequest;
import com.bankflow.payment.dto.PaymentResponse;
import com.bankflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : xIdempotencyKey;
        return paymentService.initiatePayment(effectiveKey, request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable("id") String id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/idempotency/{key}")
    public ResponseEntity<PaymentResponse> getPaymentByidempotencyKey(@PathVariable("key") String key) {
        return ResponseEntity.ok(paymentService.getPaymentByIdempotencyKey(key));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentsByAccount(@RequestParam("accountId") String accountId) {
        return ResponseEntity.ok(paymentService.getPaymentsByAccountId(accountId));
    }
}