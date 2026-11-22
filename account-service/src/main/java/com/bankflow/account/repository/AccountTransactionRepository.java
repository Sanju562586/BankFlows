package com.bankflow.account.repository;

import com.bankflow.account.entity.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    Page<AccountTransaction> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);
}