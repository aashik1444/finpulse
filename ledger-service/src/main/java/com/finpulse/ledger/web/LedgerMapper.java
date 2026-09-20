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
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getAccount().getId(),
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
