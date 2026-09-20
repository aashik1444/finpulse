package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public Account createAccount(String ownerName, String currency, long openingBalanceMinor) {
        Account account = new Account(ownerName, currency, openingBalanceMinor);
        return accountRepository.save(account);
    }

    // readOnly = true lets Hibernate skip dirty checking, the bookkeeping it otherwise
    // does to detect whether a loaded entity changed and needs an UPDATE at flush time.
    // A read never flushes, so that work is pure overhead. It also documents intent:
    // the signature alone tells a reader this method cannot write.
    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
