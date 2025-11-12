package com.jsunsoft.util.concurrent.locks;

/*
 * Additional concurrency scenario tests to ensure correctness under contention.
 */

import com.google.common.collect.ImmutableList;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ConcurrencyScenariosTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private ResourceLock newLock() {
        return StripedLockFactory.of(StripedLockType.LOCK, 16, DEFAULT_TIMEOUT);
    }

    @Test
    void exclusiveAccessSingleKeyUnderHighContention() throws Exception {
        ResourceLock lock = newLock();

        final String key = "res-1";
        final int threads = 20;
        final int iterationsPerThread = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger activeInCritical = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < iterationsPerThread; j++) {
                        lock.lock(key, () -> {
                            int now = activeInCritical.incrementAndGet();
                            maxConcurrent.accumulateAndGet(now, Math::max);
                            try {
                                // emulate some work
                                counter.incrementAndGet();
                            } finally {
                                activeInCritical.decrementAndGet();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Assertions.assertTrue(done.await(30, TimeUnit.SECONDS), "Workers did not finish in time");
        pool.shutdownNow();

        // Ensure no overlap in critical section and all iterations executed
        Assertions.assertEquals(threads * iterationsPerThread, counter.get());
        Assertions.assertEquals(1, maxConcurrent.get());
    }

    @Test
    void differentKeysRunInParallel() throws Exception {
        ResourceLock lock = newLock();

        final String keyA = "A";
        final String keyB = "B";

        CyclicBarrier barrier = new CyclicBarrier(2);
        long sleepMs = 500;

        Callable<Long> taskA = () -> {
            long start = System.nanoTime();
            lock.lock(keyA, () -> {
                awaitQuietly(barrier);
                sleepQuietly(sleepMs);
            });
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        };

        Callable<Long> taskB = () -> {
            long start = System.nanoTime();
            lock.lock(keyB, () -> {
                awaitQuietly(barrier);
                sleepQuietly(sleepMs);
            });
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> futures = pool.invokeAll(ImmutableList.of(taskA, taskB));
            long t1 = futures.get(0).get();
            long t2 = futures.get(1).get();

            long max = Math.max(t1, t2);
            // Both should complete roughly around sleepMs, allow generous overhead but must be < 2 * sleepMs
            Assertions.assertTrue(max < sleepMs * 2, "Tasks on different keys did not run in parallel: max=" + max);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void multiKeyLockingAvoidsDeadlock() throws Exception {
        ResourceLock lock = newLock();

        String a = "A";
        String b = "B";

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(2);

        Runnable r1 = () -> {
            lock.lock(ImmutableList.of(a, b), () -> sleepQuietly(200));
            done.countDown();
        };
        Runnable r2 = () -> {
            // Intentionally reverse order
            lock.lock(ImmutableList.of(b, a), () -> sleepQuietly(200));
            done.countDown();
        };

        pool.submit(r1);
        pool.submit(r2);

        Assertions.assertTrue(done.await(10, TimeUnit.SECONDS), "Deadlock detected between reversed multi-key acquisitions");
        pool.shutdownNow();
    }

    @Test
    void timeoutWhenLockIsHeld_singleKey() throws Exception {
        ResourceLock lock = newLock();
        String key = "K";

        CountDownLatch hold = new CountDownLatch(1);

        Thread t = new Thread(() -> lock.lock(key, () -> awaitQuietly(hold)));
        t.start();

        // Ensure the first thread acquired the lock
        sleepQuietly(50);

        Duration shortTimeout = Duration.ofMillis(100);
        try {
            Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock(key, shortTimeout, () -> { /* no-op */ })
            );
        } finally {
            hold.countDown();
            t.join(2000);
        }
    }

    @Test
    void timeoutReleasesPartiallyAcquiredMultiKeyLocks() throws Exception {
        ResourceLock lock = newLock();

        String a = "A";
        String b = "B";

        // Hold one of the keys so the second acquisition times out
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.lock(a, () -> awaitQuietly(release)));
        holder.start();
        sleepQuietly(50);

        Duration shortTimeout = Duration.ofMillis(150);

        List<String> lockKeys = ImmutableList.of(a, b);

        Assertions.assertThrows(LockAcquireException.class, () ->
                lock.lock(lockKeys, shortTimeout, () -> { /* no-op */ })
        );

        // After failure, both keys should be free once A is released
        release.countDown();
        holder.join(2000);

        Assertions.assertDoesNotThrow(() ->
                lock.lock(ImmutableList.of(a, b), () -> { /* success */ })
        );
    }

    @Test
    void waitingThreadCanBeInterrupted() throws Exception {
        ResourceLock lock = newLock();
        String key = "INT";

        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.lock(key, () -> awaitQuietly(release)));
        holder.start();
        sleepQuietly(50);

        CountDownLatch startedWaiting = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger(0);
        Thread waiter = new Thread(() -> {
            try {
                startedWaiting.countDown();
                lock.lockInterruptibly(key, observed::incrementAndGet);
            } catch (InterruptedException e) {
                // expected
                Thread.currentThread().interrupt();
            }
        });

        waiter.start();
        Assertions.assertTrue(startedWaiting.await(2, TimeUnit.SECONDS));
        // Interrupt while it's waiting
        waiter.interrupt();
        waiter.join(2000);

        // It must not have executed the critical section
        Assertions.assertEquals(0, observed.get());

        // After we release, we should be able to acquire normally
        release.countDown();
        holder.join(2000);

        Assertions.assertDoesNotThrow(() -> lock.lock(key, () -> { /* success */ }));
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
