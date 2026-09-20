package com.finpulse.ledger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retries a transfer when optimistic locking rejects it.
 *
 * <p>This is a separate bean from {@link TransferService}, and that separation is
 * load bearing rather than stylistic. {@code @Transactional} is proxy based: the
 * transaction opens when a call reaches the proxy and closes when that method
 * returns or throws. If this loop lived inside {@code transfer()} itself, the
 * failed attempt's transaction would still be open, and in a rolled-back,
 * unusable state, when the retry ran. A retry has to run in a genuinely new
 * transaction, and the only way to get one is to call back in through the proxy
 * from outside, which means from another bean.
 *
 * <p>This is the same proxy mechanism behind the self-invocation gotcha, showing
 * up here as a structural constraint rather than as a bug.
 */
@Component
@RequiredArgsConstructor
public class TransferExecutor {

    // Bounded by elapsed time rather than by attempt count. The distinction is
    // deliberate and was driven by measurement, not preference.
    //
    // A fixed attempt cap does not work here. With N threads contending for one row,
    // only one can win per round, so a thread's chance of winning any single attempt
    // is roughly 1/N. At N=20 a 5-attempt cap leaves each thread a (19/20)^5 = 77%
    // chance of exhausting its attempts, and measurement matched that closely: 100
    // concurrent transfers completed only 51. Raising the cap does not rescue it
    // either, since even 20 attempts still predicts ~36 failures.
    //
    // A deadline instead means a thread keeps trying as long as the system is still
    // making progress, and gives up only when it genuinely cannot get through. For
    // money movement that is the right trade: rejecting a valid transfer is worse
    // than making the caller wait, and the request is idempotent, so a retry that
    // does eventually land cannot double-post.
    private static final long DEADLINE_MILLIS = 15_000;
    private static final long BASE_BACKOFF_MILLIS = 5;
    private static final long MAX_BACKOFF_MILLIS = 100;

    private final TransferService transferService;

    public TransferResult executeWithRetry(UUID fromId, UUID toId, long amountMinor,
                                           String idempotencyKey) {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return transferService.transfer(fromId, toId, amountMinor, idempotencyKey);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new TransferRetriesExhaustedException(
                            "Transfer abandoned after " + attempt + " attempts over "
                                    + DEADLINE_MILLIS + "ms of sustained contention on the same account", e);
                }
                sleepWithBackoff(attempt);
            }
        }
    }

    /**
     * Exponential backoff with jitter, capped.
     *
     * <p>Exponential: doubling the wait each attempt spreads contending threads out
     * instead of having them all collide again immediately.
     *
     * <p>Jitter: without randomness, threads that collided once tend to wake at the
     * same moment and collide again in lockstep. Randomising within the window
     * breaks that synchronisation, which matters more than the backoff itself at
     * high thread counts.
     *
     * <p>Capped: so a thread that has backed off many times still checks back often
     * enough to claim its turn once the queue ahead of it drains.
     */
    private void sleepWithBackoff(int attempt) {
        int shift = Math.min(attempt - 1, 20);   // guard against overflow on long runs
        long window = Math.min(BASE_BACKOFF_MILLIS * (1L << shift), MAX_BACKOFF_MILLIS);
        long delay = ThreadLocalRandom.current().nextLong(1, window + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off transfer retry", ie);
        }
    }
}
