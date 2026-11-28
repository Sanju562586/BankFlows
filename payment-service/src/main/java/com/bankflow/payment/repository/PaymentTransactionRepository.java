package com.bankflow.payment.repository;

import com.bankflow.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String>{
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(
            String fromAccountId, String toAccountId
    );
}
