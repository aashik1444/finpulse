package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures how optimistic locking behaves as contention on a single row rises.
 *
 * <p>This exists because the headline 100-thread test is a deliberately extreme case:
 * every one of 100 transfers targets the SAME account, which is not what normal wallet
 * traffic looks like. This test sweeps the concurrency level to find where optimistic
 * locking stops converging, which is the honest basis for choosing between the two
 * strategies rather than picking one and defending it after the fact.
 */
@SpringBootTest
class ContentionProfileTest {

    private static final long AMOUNT = 10;
    private static final long OPENING = 1_000_000;

    @Autowired
    private TransferExecutor transferExecutor;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void profileOptimisticLockingAcrossContentionLevels() throws InterruptedException {
        System.out.println();
        System.out.println("=== OPTIMISTIC LOCKING CONTENTION PROFILE ===");
        System.out.println("All transfers target ONE account. Pool width = concurrency level.");
        System.out.println();
        System.out.printf("%-14s %-12s %-12s %-12s %-10s%n",
                "CONCURRENCY", "TRANSFERS", "SUCCEEDED", "REJECTED", "CORRECT?");

        for (int concurrency : new int[]{2, 4, 8, 16, 32}) {
            int transfers = 40;
            runAt(concurrency, transfers);
        }
        System.out.println();
    }

    private void runAt(int concurrency, int transfers) throws InterruptedException {
        Account source = accountRepository.save(
                new Account("Prof-" + concurrency, "INR", OPENING));
        Account sink = accountRepository.save(new Account("ProfSink-" + concurrency, "INR", 0));
        UUID sourceId = source.getId();
        UUID sinkId = sink.getId();

        String runId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(transfers);
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < transfers; i++) {
            final String key = "prof-" + concurrency + "-" + runId + "-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    transferExecutor.executeWithRetry(sourceId, sinkId, AMOUNT, key);
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        pool.shutdown();

        long balance = accountRepository.findById(sourceId).orElseThrow().getBalanceMinor();
        long expected = OPENING - (long) transfers * AMOUNT;
        int succeeded = transfers - failed.get();

        System.out.printf("%-14d %-12d %-12d %-12d %-10s%n",
                concurrency, transfers, succeeded, failed.get(),
                balance == expected ? "yes" : "NO");
    }
}
