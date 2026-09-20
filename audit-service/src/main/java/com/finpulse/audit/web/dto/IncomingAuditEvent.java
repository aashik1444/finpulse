package com.finpulse.audit.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * An event as it arrives from a producer.
 *
 * <p>Structurally identical to ledger-service's AuditEventRequest today, and
 * deliberately a separate type. Sharing a class across two independently deployed
 * services would couple their release cycles, which is exactly what splitting them
 * was meant to avoid.
 */
public record IncomingAuditEvent(
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt
) {
}
