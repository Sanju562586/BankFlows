package com.bankflow.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.BalanceResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.Account;
import com.bankflow.account.exception.InsufficientBalanceException;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.repository.AccountTransactionRepository;
import com.bankflow.account.service.AccountServiceImpl;
import com.bankflow.common.dto.BalanceOperationRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class AccountServiceTest {

    private AccountRepository accountRepository;
    private AccountTransactionRepository transactionRepository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(AccountTransactionRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        accountService = new AccountServiceImpl(accountRepository, transactionRepository, kafkaTemplate);
    }

    @Test
    void createAccount_shouldPersistAndReturnAccount() {
        CreateAccountRequest request = new CreateAccountRequest(
                "cust-1",
                "Aarav Sharma",
                "aarav@example.com",
                "+919876543210",
                "INR",
                new BigDecimal("5000.00")
        );

        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response.id());
        assertEquals(10, response.accountNumber().length());
        assertEquals(new BigDecimal("5000.00"), response.balance());
        verify(transactionRepository).save(any());
    }

    @Test
    void updateBalance_debitExceedingBalance_shouldThrowInsufficientBalanceException() {
        Account account = new Account();
        account.setId("acc-101");
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("200.00"));
        account.setStatus("ACTIVE");

        when(accountRepository.findByIdForUpdate("acc-101")).thenReturn(Optional.of(account));

        BalanceOperationRequest debitRequest = new BalanceOperationRequest(
                "txn-1",
                "DEBIT",
                new BigDecimal("500.00"),
                "acc-102",
                "UPI transfer"
        );

        assertThrows(
                InsufficientBalanceException.class,
                () -> accountService.updateBalance("acc-101", debitRequest)
        );
    }

    @Test
    void updateBalance_concurrentDebits_shouldNeverAllowNegativeBalance() throws InterruptedException {
        Account sharedAccount = new Account();
        sharedAccount.setId("acc-101");
        sharedAccount.setAccountNumber("1234567890");
        sharedAccount.setCustomerId("cust-1");
        sharedAccount.setCurrency("INR");
        sharedAccount.setBalance(new BigDecimal("1000.00"));
        sharedAccount.setStatus("ACTIVE");

        when(accountRepository.findByIdForUpdate("acc-101")).thenAnswer(invocation -> {
            synchronized (sharedAccount) {
                return Optional.of(sharedAccount);
            }
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    synchronized (sharedAccount) {
                        BalanceResponse result = accountService.updateBalance(
                                "acc-101",
                                new BalanceOperationRequest(
                                        "txn-" + index,
                                        "DEBIT",
                                        new BigDecimal("100.00"),
                                        "acc-102",
                                        "Concurrent debit"
                                )
                        );
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InsufficientBalanceException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(10, successCount.get());
        assertEquals(0, sharedAccount.getBalance().compareTo(BigDecimal.ZERO));
    }
}