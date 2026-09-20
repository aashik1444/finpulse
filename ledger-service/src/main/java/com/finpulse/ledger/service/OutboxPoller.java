package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.OutboxEvent;
import com.finpulse.ledger.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

/**
 * Drains the outbox: reads undelivered events and ships them to audit-service.
 *
 * <p>This is the half of the outbox pattern that deals with delivery. The transfer
 * itself never calls audit-service, so a transfer cannot fail because audit-service
 * is down, and an event cannot be lost because it was committed with the money.
 *
 * <p>The guarantee is at-least-once, not exactly-once. An event is marked published
 * only after audit-service acknowledges it, so any failure, a timeout, a refused
 * connection, a 5xx, leaves published_at null and the row is retried on the next
 * tick. The cost is that an event can arrive twice: if audit-service commits the
 * record but the response is lost on the way back, this poller never marks the row
 * and resends it. Exactly-once delivery is not achievable over an unreliable
 * network, so the pattern does not attempt it. It guarantees no event is ever
 * silently dropped and pushes deduplication to the consumer, which is where it
 * belongs, because only the consumer can tell whether it has already applied an
 * event. audit-service deduplicates on eventId.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private static final int BATCH_SIZE = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final RestClient auditServiceClient;

    /**
     * fixedDelay, not fixedRate: the next run starts two seconds after the previous
     * one finishes, rather than every two seconds regardless. With fixedRate, a slow
     * batch could overlap the next run, so two executions would compete for the same
     * rows. fixedDelay makes overlap impossible.
     *
     * <p>Needs @EnableScheduling on the application class, added in Milestone 1.
     * Without it Spring registers no scheduling infrastructure and this method is
     * simply never called, with no error and nothing in the log to explain why.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedBatchForUpdate(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        int delivered = 0;
        for (OutboxEvent event : batch) {
            try {
                auditServiceClient.post()
                        .uri("/api/audit/events")
                        .header("X-Correlation-Id", event.getAggregateId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new AuditEventRequest(
                                event.getId(), event.getAggregateId(), event.getEventType(),
                                event.getPayload(), event.getCreatedAt()))
                        .retrieve()
                        .toBodilessEntity();

                // Marked published only after the POST succeeds. Doing this before
                // would turn any delivery failure into permanent data loss.
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                delivered++;
            } catch (RestClientException e) {
                // Deliberately not rethrown. One unreachable consumer should not roll
                // back the events already delivered in this batch, and the row stays
                // unpublished so it is retried on the next tick, indefinitely.
                log.warn("Failed to publish outbox event {}, will retry: {}",
                        event.getId(), e.getMessage());
            }
        }

        if (delivered > 0) {
            log.info("Published {} of {} outbox event(s)", delivered, batch.size());
        }
    }
}
