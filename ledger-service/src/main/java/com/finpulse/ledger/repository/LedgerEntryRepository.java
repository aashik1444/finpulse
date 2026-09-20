package com.finpulse.ledger.repository;

import com.finpulse.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // NOTE: this method is deliberately left in its naive form for now. Because both
    // @ManyToOne associations on LedgerEntry are LAZY, mapping a page of results to a
    // DTO touches entry.getAccount() and entry.getTransaction() on every row, and each
    // of those touches issues its own SELECT. A page of 50 entries therefore costs
    // 1 + 50 + 50 = 101 queries. Milestone 3 measures that, then fixes it with an
    // explicit JOIN FETCH. Fixing it here would remove the very problem that the
    // measurement exercise depends on.
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}
