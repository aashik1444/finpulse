package com.finpulse.ledger.service;

import com.finpulse.ledger.domain.Account;
import com.finpulse.ledger.domain.EntryDirection;
import com.finpulse.ledger.repository.AccountRepository;
import com.finpulse.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fires 100 concurrent transfers out of one account and asserts the final balance
 * is exactly right.
 *
 * <p>Run this against {@code transferService.transfer(...)} directly and it fails:
 * threads read the same balance before any of them writes, so updates are lost and
 * the final balance is too high by a non-deterministic amount. Run it against
 * {@link TransferExecutor#executeWithRetry} and it passes every time, because the
 * {@code @Version} column turns a silent lost update into an exception that the
 * retry wrapper handles.
 *
 * <p>The CountDownLatch matters: without it, threads trickle in as the pool schedules
 * them and mostly miss each other. Holding all 100 at a start gate and releasing them
 * at once is what makes the race reliably observable.
 */
@SpringBootTest
class ConcurrentTransferLostUpdateTest {

    private static final int THREAD_COUNT = 100;
    private static final long AMOUNT_PER_TRANSFER = 100;
    private static final long OPENING_BALANCE = 10_000;

    @Autowired
    private TransferExecutor transferExecutor;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Test
    void hundredConcurrentTransfersLeaveExactlyTheRightBalance() throws InterruptedException {
        Account source = accountRepository.save(new Account("Source", "INR", OPENING_BALANCE));
        Account sink = accountRepository.save(new Account("Sink", "INR", 0));
        UUID sourceId = source.getId();
        UUID sinkId = sink.getId();

        String runId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String key = "concurrent-" + runId + "-" + i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    transferExecutor.executeWithRetry(sourceId, sinkId, AMOUNT_PER_TRANSFER, key);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        Account reloadedSource = accountRepository.findById(sourceId).orElseThrow();
        Account reloadedSink = accountRepository.findById(sinkId).orElseThrow();
        long expectedSource = OPENING_BALANCE - (THREAD_COUNT * AMOUNT_PER_TRANSFER);
        long expectedSink = THREAD_COUNT * AMOUNT_PER_TRANSFER;

        long debits = entryRepository.findAll().stream()
                .filter(e -> e.getAccount().getId().equals(sourceId))
                .filter(e -> e.getDirection() == EntryDirection.DEBIT)
                .count();

        System.out.println("=== CONCURRENCY RESULT ===");
        System.out.println("All threads finished:  " + finished);
        System.out.println("Expected source bal:   " + expectedSource);
        System.out.println("Actual source bal:     " + reloadedSource.getBalanceMinor());
        System.out.println("Expected sink bal:     " + expectedSink);
        System.out.println("Actual sink bal:       " + reloadedSink.getBalanceMinor());
        System.out.println("Source version:        " + reloadedSource.getVersion());
        System.out.println("DEBIT entries written: " + debits);
        System.out.println("Failed transfers:      " + failures.get());

        assertEquals(0, failures.get(), "No transfer should fail outright");
        assertEquals(expectedSource, reloadedSource.getBalanceMinor(),
                "Source balance drifted: a lost update occurred");
        assertEquals(expectedSink, reloadedSink.getBalanceMinor(),
                "Sink balance drifted: a lost update occurred");
        assertEquals(THREAD_COUNT, debits,
                "Every transfer must have written exactly one DEBIT entry");
    }
}
