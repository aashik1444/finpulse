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
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the identical 100-thread workload against both locking strategies and prints
 * the comparison, so the tradeoff is measured on this machine rather than asserted
 * from a textbook.
 *
 * <p>Both strategies must end with a correct balance. The interesting difference is
 * how much work each wastes getting there, and how many requests each rejects.
 */
@SpringBootTest
class LockingStrategyComparisonTest {

    private static final int THREAD_COUNT = 100;
    private static final long AMOUNT = 100;
    private static final long OPENING = 10_000;

    @Autowired
    private TransferExecutor transferExecutor;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void compareOptimisticAndPessimisticUnderIdenticalLoad() throws InterruptedException {
        Result optimistic = runWorkload("OPTIMISTIC + retry",
                (ids, key) -> transferExecutor.executeWithRetry(ids[0], ids[1], AMOUNT, key));

        Result pessimistic = runWorkload("PESSIMISTIC (SELECT FOR UPDATE)",
                (ids, key) -> transferService.transferPessimistic(ids[0], ids[1], AMOUNT, key));

        System.out.println();
        System.out.println("=== LOCKING STRATEGY COMPARISON ===");
        System.out.println("Workload: " + THREAD_COUNT + " concurrent transfers of " + AMOUNT
                + " out of ONE account, 20-thread pool");
        System.out.println();
        System.out.printf("%-34s %12s %12s%n", "", "OPTIMISTIC", "PESSIMISTIC");
        System.out.printf("%-34s %12d %12d%n", "Transfers succeeded",
                optimistic.succeeded, pessimistic.succeeded);
        System.out.printf("%-34s %12d %12d%n", "Transfers rejected",
                optimistic.failed, pessimistic.failed);
        System.out.printf("%-34s %12d %12d%n", "Final balance (expect "
                + (OPENING - THREAD_COUNT * AMOUNT) + ")", optimistic.finalBalance, pessimistic.finalBalance);
        System.out.printf("%-34s %12d %12d%n", "Wall clock (ms)",
                optimistic.millis, pessimistic.millis);
        System.out.println();

        assertEquals(THREAD_COUNT, pessimistic.succeeded,
                "Pessimistic locking should complete every transfer: conflicts wait, they do not fail");
        assertEquals(OPENING - THREAD_COUNT * AMOUNT, pessimistic.finalBalance,
                "Pessimistic locking must still produce the correct balance");
    }

    private Result runWorkload(String label, BiConsumer<UUID[], String> transferFn)
            throws InterruptedException {

        Account source = accountRepository.save(new Account("Src-" + label, "INR", OPENING));
        Account sink = accountRepository.save(new Account("Snk-" + label, "INR", 0));
        UUID[] ids = {source.getId(), sink.getId()};

        String runId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String key = label.charAt(0) + "-" + runId + "-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    transferFn.accept(ids, key);
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        long millis = System.currentTimeMillis() - t0;
        pool.shutdown();

        long balance = accountRepository.findById(ids[0]).orElseThrow().getBalanceMinor();
        return new Result(THREAD_COUNT - failed.get(), failed.get(), balance, millis);
    }

    private record Result(int succeeded, int failed, long finalBalance, long millis) {
    }
}
