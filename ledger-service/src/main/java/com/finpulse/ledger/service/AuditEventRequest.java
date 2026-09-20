package com.finpulse.ledger.service;

import java.time.Instant;
import java.util.UUID;

/**
 * The wire format sent to audit-service.
 *
 * <p>Deliberately a separate type from audit-service's IncomingAuditEvent, even
 * though the two are structurally identical today. They are two services with
 * independent deploy cycles: sharing a class would couple them at compile time and
 * defeat the point of separating them. Each owns its own view of the contract.
 */
public record AuditEventRequest(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt
) {
}
