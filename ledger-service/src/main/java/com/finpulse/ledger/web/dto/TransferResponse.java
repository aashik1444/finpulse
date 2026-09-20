package com.finpulse.ledger.web.dto;

import com.finpulse.ledger.domain.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        TransactionStatus status,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        Instant createdAt
) {
}
