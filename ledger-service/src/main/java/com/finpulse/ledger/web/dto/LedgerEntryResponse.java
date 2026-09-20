package com.finpulse.ledger.web.dto;

import com.finpulse.ledger.domain.EntryDirection;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of an account statement.
 *
 * <p>counterpartyName and transactionDescription are what make this readable as a
 * passbook rather than a table of opaque identifiers: a statement line needs to say
 * who the money moved to and what it was for, not just quote two UUIDs.
 *
 * <p>Both fields live on associated entities, so populating them forces Hibernate to
 * resolve the account and transaction associations on every row. That is what makes
 * the fetch strategy for this endpoint matter, and it is the subject of the N+1
 * measurement in Milestone 3.
 */
public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID accountId,
        String counterpartyName,
        String transactionDescription,
        EntryDirection direction,
        long amountMinor,
        Instant createdAt
) {
}
