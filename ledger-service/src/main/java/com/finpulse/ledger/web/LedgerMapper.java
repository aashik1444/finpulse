package com.finpulse.ledger.web;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.domain.LedgerTransaction;
import com.finpulse.ledger.web.dto.AccountResponse;
import com.finpulse.ledger.web.dto.LedgerEntryResponse;
import com.finpulse.ledger.web.dto.TransactionDetailResponse;

import java.util.List;

public final class LedgerMapper {

    private LedgerMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalanceMinor(),
                account.getVersion(),
                account.getCreatedAt()
        );
    }

    public static LedgerEntryResponse toResponse(LedgerEntry entry) {
        // getId() alone would NOT touch the database: a Hibernate lazy proxy already
        // holds the foreign key, because it is a column on ledger_entry itself, so
        // returning it needs no query. getOwnerName() and getDescription() are
        // different: those values live only in the other table, so each call forces
        // the proxy to initialise and issue its own SELECT. That is where the N+1
        // comes from, and why the fetch strategy for this endpoint matters.
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getAccount().getId(),
                entry.getAccount().getOwnerName(),
                entry.getTransaction().getDescription(),
                entry.getDirection(),
                entry.getAmountMinor(),
                entry.getCreatedAt()
        );
    }

    public static TransactionDetailResponse toDetailResponse(LedgerTransaction transaction,
                                                             List<LedgerEntry> entries) {
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                entries.stream().map(LedgerMapper::toResponse).toList()
        );
    }
}
