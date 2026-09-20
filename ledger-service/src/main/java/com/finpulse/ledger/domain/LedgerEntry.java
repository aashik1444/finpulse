package com.finpulse.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "ledger_entry",
    // Composite index on (account_id, created_at), in that order, deliberately.
    //
    // A B-tree index is sorted by its first column, then by the second within each
    // first-column group. So this index serves a query that filters on account_id
    // alone, or filters on account_id AND orders by created_at, which is exactly the
    // statement query: always scoped to one account, always newest-first.
    //
    // It also lets the ORDER BY be satisfied by the index walk itself, so the planner
    // can drop the separate Sort step entirely.
    //
    // Reversing the order to (created_at, account_id) would be worse here: the index
    // would be sorted primarily by time across all accounts, which suits "everything
    // in the last hour system-wide" but not "this account's history". A composite
    // index is usable for a prefix of its columns, not for a suffix.
    indexes = @Index(name = "idx_ledger_entry_account_created",
                     columnList = "account_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryDirection direction;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry(LedgerTransaction transaction, Account account,
                       EntryDirection direction, long amountMinor) {
        this.transaction = transaction;
        this.account = account;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.createdAt = Instant.now();
    }
}
