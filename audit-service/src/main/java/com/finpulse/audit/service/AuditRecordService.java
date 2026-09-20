package com.finpulse.audit.service;

import com.finpulse.audit.domain.AuditRecord;
import com.finpulse.audit.repository.AuditRecordRepository;
import com.finpulse.audit.web.dto.IncomingAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecordService {

    private final AuditRecordRepository auditRecordRepository;

    /**
     * Stores an event, ignoring it if already stored.
     *
     * <p>Deduplication has two layers, and both are needed. The existsByEventId
     * check handles the ordinary case cheaply: a redelivery arriving after the
     * original was committed is recognised and dropped without an insert attempt.
     *
     * <p>But check-then-insert is not atomic. Two copies of the same event arriving
     * at once can both pass the check before either commits, exactly as with the
     * idempotency check in ledger-service. The unique constraint on event_id is what
     * actually guarantees uniqueness, and the catch below turns the resulting
     * violation into the same outcome as the fast path: the event was already
     * recorded, so there is nothing to do.
     *
     * <p>REQUIRES_NEW matters here. A constraint violation marks the surrounding
     * transaction rollback-only, so without its own transaction this method could not
     * swallow the exception and return normally. The caller needs a clean commit so
     * the producer sees 202 and stops retrying.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(IncomingAuditEvent event) {
        if (auditRecordRepository.existsByEventId(event.eventId())) {
            log.debug("Duplicate audit event {} ignored (at-least-once delivery)", event.eventId());
            return;
        }
        try {
            auditRecordRepository.saveAndFlush(new AuditRecord(
                    event.eventId(), event.aggregateId(), event.eventType(),
                    event.payload(), event.createdAt()));
        } catch (DataIntegrityViolationException e) {
            // Another delivery of this same event won the race. Same end state.
            log.debug("Concurrent duplicate of audit event {} rejected by constraint",
                    event.eventId());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditRecord> findByAccount(UUID aggregateId, Pageable pageable) {
        return auditRecordRepository.findByAggregateId(aggregateId, pageable);
    }
}
