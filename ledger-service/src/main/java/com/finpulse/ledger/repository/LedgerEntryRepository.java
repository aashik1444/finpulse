package com.finpulse.ledger.repository;

import com.finpulse.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * The N+1 fix: loads each entry together with its account and its transaction in
     * one SQL statement, so no proxy is ever left to initialise later.
     *
     * <p>Note this does not change the entity mapping. Both associations stay LAZY by
     * default, which is right, because most call sites do not need them. The eager
     * fetch is declared here, at the one query that is known to need both on every
     * row. That is the general rule: lazy globally, eager per query.
     *
     * <p>The explicit countQuery is required. Spring Data would otherwise derive the
     * count by wrapping this query, and JOIN FETCH is not valid inside COUNT. It also
     * avoids the join entirely for the count, which is cheaper.
     *
     * <p>Pagination over JOIN FETCH is safe here specifically because both targets are
     * @ManyToOne, so each entry joins to exactly one account and one transaction and
     * the row count is unchanged. It would NOT be safe over a @OneToMany: the join
     * would multiply rows per parent and LIMIT could slice through the middle of one
     * parent's collection. The fix for that case is to select the page of ids first,
     * then fetch the full graph with WHERE id IN (:ids).
     */
    @Query(value = "SELECT e FROM LedgerEntry e "
                 + "JOIN FETCH e.account "
                 + "JOIN FETCH e.transaction "
                 + "WHERE e.account.id = :accountId",
           countQuery = "SELECT COUNT(e) FROM LedgerEntry e WHERE e.account.id = :accountId")
    Page<LedgerEntry> findByAccountIdWithDetails(@Param("accountId") UUID accountId, Pageable pageable);

    List<LedgerEntry> findByTransactionId(UUID transactionId);
}
