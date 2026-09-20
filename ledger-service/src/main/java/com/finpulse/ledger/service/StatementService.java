package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.LedgerEntry;
import com.finpulse.ledger.repository.LedgerEntryRepository;
import com.finpulse.ledger.web.LedgerMapper;
import com.finpulse.ledger.web.dto.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Builds account statements.
 *
 * <p>This exists because entity-to-DTO mapping has to happen inside a transaction.
 * The controller previously called the repository directly and mapped afterwards,
 * by which point the Hibernate session was closed, so touching any lazy association
 * threw LazyInitializationException. That exception is not a nuisance; it is
 * open-in-view=false doing its job, failing loudly and in the right place instead
 * of silently issuing extra queries during response serialisation.
 *
 * <p>Mapping inside the transaction is also what makes the fetch strategy visible:
 * with a lazy fetch, every row resolves its own associations and the query count
 * explodes; with a JOIN FETCH, one query serves the whole page.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private final LedgerEntryRepository entryRepository;

    /**
     * The naive version, kept as the measured baseline. Both associations are LAZY,
     * so each row triggers a SELECT for its account and another for its transaction:
     * 1 page query + 1 count query + 50 + 50 for a page of 50.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getStatementNaive(UUID accountId, Pageable pageable) {
        Page<LedgerEntry> entries = entryRepository.findByAccountId(accountId, pageable);
        return entries.map(LedgerMapper::toResponse);
    }

    /**
     * The fixed version. JOIN FETCH loads both associations in the same SQL statement
     * as the entries themselves, so no proxy ever needs initialising and the whole
     * page costs one query, plus the count query pagination requires.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getStatement(UUID accountId, Pageable pageable) {
        Page<LedgerEntry> entries = entryRepository.findByAccountIdWithDetails(accountId, pageable);
        return entries.map(LedgerMapper::toResponse);
    }
}
