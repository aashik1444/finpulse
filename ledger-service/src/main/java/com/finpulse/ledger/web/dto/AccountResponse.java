package com.finpulse.ledger.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String ownerName,
        String currency,
        long balanceMinor,
        long version,
        Instant createdAt
) {
}
