package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountTransactionResponse;
import com.bankflow.account.dto.BalanceResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.common.dto.BalanceOperationRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountService {
    AccountResponse createAccount(CreateAccountRequest request);

    List<AccountResponse> getAllAccounts();

    AccountResponse getAccount(String accountIdentifier);

    BalanceResponse getBalance(String accountIdentifier);

    BalanceResponse updateBalance(String accountIdentifier, BalanceOperationRequest request);

    Page<AccountTransactionResponse> getTransactions(String accountIdentifier, Pageable pageable);
}