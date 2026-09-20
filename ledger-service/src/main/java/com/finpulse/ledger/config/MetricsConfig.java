package com.finpulse.ledger.config;

import com.finpulse.ledger.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter transfersPostedCounter(MeterRegistry registry) {
        return Counter.builder("finpulse.transfers.posted")
                .description("Transfers successfully posted (idempotent replays excluded)")
                .register(registry);
    }

    /**
     * Percentiles rather than an average. An average hides the tail completely: a
     * service can look healthy at the mean while a meaningful fraction of users wait
     * far longer. p50 shows the typical request, p95 and p99 show where contention
     * retries and slow queries actually surface.
     */
    @Bean
    public Timer transferLatencyTimer(MeterRegistry registry) {
        return Timer.builder("finpulse.transfers.latency")
                .description("End to end latency of a transfer, including any retries")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * How many events are waiting to reach audit-service.
     *
     * <p>This is the metric worth alerting on. The counter and timer describe work
     * that succeeded; this one describes work that is stuck. A backlog that grows
     * without draining means the consumer is unreachable or failing, and because the
     * outbox never drops events the only outward symptom is this number climbing.
     * Without it, audit-service could be down for hours while every ledger metric
     * looks perfectly healthy.
     *
     * <p>A Gauge rather than a Counter because it measures a current level that moves
     * in both directions, not a total that only rises.
     */
    @Bean
    public Gauge outboxBacklogGauge(MeterRegistry registry, OutboxEventRepository outboxEventRepository) {
        return Gauge.builder("finpulse.outbox.backlog",
                        outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events not yet delivered to audit-service")
                .register(registry);
    }
}
