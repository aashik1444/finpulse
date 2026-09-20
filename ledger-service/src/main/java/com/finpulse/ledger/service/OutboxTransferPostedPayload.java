package com.finpulse.ledger.service;

import java.time.Instant;
import java.util.UUID;

/**
 * The body of a TRANSFER_POSTED event, serialised to JSON and stored in the
 * outbox row's payload column.
 *
 * <p>This is a published contract, not an internal type: once an event is written
 * it may be read by a consumer built against an older version of this shape, so
 * fields should be added rather than renamed or removed.
 */
public record OutboxTransferPostedPayload(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        Instant postedAt
) {
}
