package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountTransactionResponse;
import com.bankflow.account.dto.BalanceResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.Account;
import com.bankflow.account.entity.AccountTransaction;
import com.bankflow.account.exception.AccountNotFoundException;
import com.bankflow.account.exception.InsufficientBalanceException;
import com.bankflow.account.exception.InvalidOperationException;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.repository.AccountTransactionRepository;
import com.bankflow.common.dto.BalanceOperationRequest;
import com.bankflow.common.events.AccountCreatedEvent;
import com.bankflow.common.events.BalanceUpdatedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);
    private static final String ACCOUNT_EVENTS_TOPIC = "account-events";
    private static final String BALANCE_UPDATES_TOPIC = "balance-updates";

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String id = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        String accountNumber = generateUniqueAccountNumber();
        BigDecimal openingBalance = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;

        Account account = new Account();
        account.setId(id);
        account.setAccountNumber(accountNumber);
        account.setCustomerId(request.customerId());
        account.setCustomerName(request.customerName());
        account.setEmail(request.email());
        account.setPhoneNumber(request.phoneNumber());
        account.setCurrency(request.currency() != null && !request.currency().isBlank() ? request.currency() : "INR");
        account.setBalance(openingBalance);
        account.setStatus("ACTIVE");

        Account saved = accountRepository.save(account);

        if (openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            recordLedgerEntry(
                    "init-" + UUID.randomUUID().toString().substring(0, 8),
                    saved.getId(),
                    openingBalance,
                    "CREDIT",
                    "Initial Deposit",
                    null,
                    saved.getBalance()
            );
        }

        publishAccountCreated(saved);
        return toAccountResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toAccountResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountIdentifier) {
        return toAccountResponse(findAccountByIdOrNumber(accountIdentifier));
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountIdentifier) {
        Account account = findAccountByIdOrNumber(accountIdentifier);
        return new BalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public BalanceResponse updateBalance(String accountIdentifier, BalanceOperationRequest request) {
        Account account = findAccountForUpdate(accountIdentifier);

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new InvalidOperationException("Account is not active: " + account.getId());
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Amount must be greater than zero");
        }

        String operation = request.operationType() != null
                ? request.operationType().trim().toUpperCase(Locale.ROOT)
                : "";

        BigDecimal previousBalance = account.getBalance();
        BigDecimal updatedBalance;

        switch (operation) {
            case "DEBIT" -> {
                if (previousBalance.compareTo(request.amount()) < 0) {
                    throw new InsufficientBalanceException(
                            "Insufficient balance in account " + account.getId()
                    );
                }
                updatedBalance = previousBalance.subtract(request.amount());
            }
            case "CREDIT" -> updatedBalance = previousBalance.add(request.amount());
            default -> throw new InvalidOperationException("Unsupported operation type: " + request.operationType());
        }

        account.setBalance(updatedBalance);
        Account saved = accountRepository.save(account);

        recordLedgerEntry(
                request.transactionId(),
                saved.getId(),
                request.amount(),
                operation,
                request.description(),
                request.counterpartyAccountId(),
                updatedBalance
        );

        publishBalanceUpdated(saved, previousBalance, updatedBalance, request.amount(), operation, request.transactionId(), request.counterpartyAccountId());

        return new BalanceResponse(
                saved.getId(),
                saved.getAccountNumber(),
                saved.getCurrency(),
                saved.getBalance(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountTransactionResponse> getTransactions(String accountIdentifier, Pageable pageable) {
        Account account = findAccountByIdOrNumber(accountIdentifier);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable)
                .map(this::toTransactionResponse);
    }

    private Account findAccountByIdOrNumber(String identifier) {
        return accountRepository.findById(identifier)
                .or(() -> accountRepository.findByAccountNumber(identifier))
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + identifier));
    }

    private Account findAccountForUpdate(String identifier) {
        Optional<Account> byId = accountRepository.findByIdForUpdate(identifier);
        if (byId.isPresent()) {
            return byId.get();
        }
        return accountRepository.findByAccountNumberForUpdate(identifier)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + identifier));
    }

    private void recordLedgerEntry(
            String transactionId,
            String accountId,
            BigDecimal amount,
            String operationType,
            String description,
            String counterpartyAccount,
            BigDecimal balanceAfter
    ) {
        AccountTransaction entry = new AccountTransaction();
        entry.setTransactionId(transactionId);
        entry.setAccountId(accountId);
        entry.setAmount(amount);
        entry.setOperationType(operationType);
        entry.setDescription(description);
        entry.setCounterpartyAccount(counterpartyAccount);
        entry.setBalanceAfter(balanceAfter);
        transactionRepository.save(entry);
    }

    private void publishAccountCreated(Account account) {
        try {
            AccountCreatedEvent event = new AccountCreatedEvent(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    account.getId(),
                    account.getAccountNumber(),
                    account.getCustomerId(),
                    account.getCustomerName(),
                    account.getEmail(),
                    account.getPhoneNumber(),
                    account.getCurrency(),
                    account.getBalance(),
                    Instant.now()
            );
            kafkaTemplate.send(ACCOUNT_EVENTS_TOPIC, account.getId(), event);
        } catch (Exception ex) {
            log.warn("Failed to publish AccountCreatedEvent for account {}: {}", account.getId(), ex.getMessage());
        }
    }

    private void publishBalanceUpdated(
            Account account,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            BigDecimal amount,
            String operationType,
            String transactionId,
            String counterpartyAccountId
    ) {
        try {
            BalanceUpdatedEvent event = new BalanceUpdatedEvent(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    transactionId,
                    account.getId(),
                    operationType,
                    amount,
                    previousBalance,
                    newBalance,
                    counterpartyAccountId,
                    Instant.now()
            );
            kafkaTemplate.send(BALANCE_UPDATES_TOPIC, account.getId(), event);
        } catch (Exception ex) {
            log.warn("Failed to publish BalanceUpdatedEvent for account {}: {}", account.getId(), ex.getMessage());
        }
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
            candidate = String.valueOf(number);
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }

    private AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getCustomerName(),
                account.getEmail(),
                account.getPhoneNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private AccountTransactionResponse toTransactionResponse(AccountTransaction tx) {
        return new AccountTransactionResponse(
                tx.getId(),
                tx.getTransactionId(),
                tx.getAccountId(),
                tx.getAmount(),
                tx.getOperationType(),
                tx.getDescription(),
                tx.getCounterpartyAccount(),
                tx.getBalanceAfter(),
                tx.getCreatedAt()
        );
    }
}