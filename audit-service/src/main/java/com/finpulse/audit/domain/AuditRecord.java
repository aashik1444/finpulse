package com.finpulse.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One received event, stored permanently.
 *
 * <p>Note there are no setters, only getters and a constructor. An audit trail whose
 * rows can be edited after the fact is not an audit trail. Every field is set once,
 * at construction, and the absence of mutators makes that a property of the type
 * rather than a convention someone has to remember.
 */
@Entity
@Table(
    name = "audit_record",
    // The unique constraint is what actually enforces deduplication under
    // at-least-once delivery. The application-level existsByEventId check handles
    // the common case, but two copies of the same event arriving concurrently could
    // both pass it before either commits. Only the database can arbitrate that.
    uniqueConstraints = @UniqueConstraint(name = "uk_audit_record_event_id",
                                          columnNames = "event_id"),
    // The read endpoint always filters by aggregate_id, so it gets an index.
    indexes = @Index(name = "idx_audit_record_aggregate_id", columnList = "aggregate_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRecord {

    @Id
    @GeneratedValue
    private UUID id;

    /** The producer's event id. Stable across redeliveries, which is what makes dedup work. */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** What the event is about: here, the ledger transaction id. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** When the producer created the event. */
    @Column(nullable = false)
    private Instant sourceCreatedAt;

    /**
     * When this service received it. Kept separate from sourceCreatedAt because the
     * gap between them is exactly the delivery lag, and during an outage that gap can
     * be hours. Collapsing them into one timestamp would destroy that information.
     */
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    public AuditRecord(UUID eventId, UUID aggregateId, String eventType,
                       String payload, Instant sourceCreatedAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.sourceCreatedAt = sourceCreatedAt;
        this.receivedAt = Instant.now();
    }
}
