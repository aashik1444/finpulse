package com.finpulse.ledger.web.dto;

import com.finpulse.ledger.domain.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionDetailResponse(
        UUID id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        Instant createdAt,
        List<LedgerEntryResponse> entries
) {
}
