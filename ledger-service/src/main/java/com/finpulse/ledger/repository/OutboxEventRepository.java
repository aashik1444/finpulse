package com.finpulse.ledger.repository;

import com.finpulse.ledger.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of undelivered events for this poller.
     *
     * <p>FOR UPDATE SKIP LOCKED is what makes the outbox table safe as a work queue
     * when more than one instance of this service is running, which is the normal
     * production topology behind a load balancer. Each instance runs its own
     * scheduled poller, and without locking two of them would select the same rows
     * and both deliver them.
     *
     * <p>FOR UPDATE locks the rows this poller selects. SKIP LOCKED tells Postgres
     * that rows already locked by another transaction should be passed over rather
     * than waited for, so a second poller running at the same moment gets a
     * different batch instead of blocking. The table becomes a concurrent queue
     * with no coordination service, no leader election and no distributed lock.
     *
     * <p>Native SQL rather than JPQL because SKIP LOCKED has no JPQL equivalent.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE published_at IS NULL "
                 + "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<OutboxEvent> findUnpublishedBatchForUpdate(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
