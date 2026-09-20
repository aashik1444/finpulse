package com.finpulse.ledger.web.dto;

import com.finpulse.ledger.domain.EntryDirection;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID accountId,
        EntryDirection direction,
        long amountMinor,
        Instant createdAt
) {
}
