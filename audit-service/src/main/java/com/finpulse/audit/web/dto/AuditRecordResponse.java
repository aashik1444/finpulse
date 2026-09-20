package com.finpulse.audit.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        UUID id,
        UUID eventId,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant sourceCreatedAt,
        Instant receivedAt
) {
}
