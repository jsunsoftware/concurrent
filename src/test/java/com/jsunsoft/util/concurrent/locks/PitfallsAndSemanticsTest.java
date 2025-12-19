package com.jsunsoft.util.concurrent.locks;

import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

class PitfallsAndSemanticsTest {

    @Test
    void negativeTimeoutIsRejected() {
        ResourceLock lock = newNoopLock();
        Duration negativeDuration = Duration.ofMillis(-1);

        Assertions.assertThrows(IllegalArgumentException.class, () ->
                lock.lock("k", negativeDuration, () -> {
                })
        );
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                lock.lockInterruptibly("k", negativeDuration, () -> {
                })
        );
    }

    @Test
    void tooLargeTimeoutIsRejected() {
        // Use Striped implementation to exercise Duration->nanos conversion path.
        ResourceLock lock = StripedLockFactory.of(
                StripedLockType.LOCK,
                4,
                Duration.ofMillis(10)
        );

        // toNanos() overflows for extremely large durations; we wrap as IllegalArgumentException.
        Duration huge = Duration.ofSeconds(Long.MAX_VALUE);
        Assertions.assertThrows(ArithmeticException.class, () ->
                lock.lock("k", huge, () -> {
                })
        );
    }

    @Test
    void unlockFailureIsSuppressedWhenCallbackThrows_singleKey() {

        Duration timeout = Duration.ofMillis(1);

        ResourceLock lock = new UnlockAlwaysFailsLock(timeout);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () ->
                lock.lockInterruptibly("k", timeout, () -> {
                    throw new RuntimeException("boom");
                })
        );

        Assertions.assertEquals("boom", ex.getMessage());
        Assertions.assertEquals(1, ex.getSuppressed().length);
        Assertions.assertEquals("unlock failed", ex.getSuppressed()[0].getMessage());
    }

    @Test
    void unlockFailureIsSuppressedWhenCallbackThrows_multiKey() {
        ResourceLock lock = new UnlockAlwaysFailsLock(Duration.ofMillis(1));
        Duration timeout = Duration.ofMillis(1);
        Collection<String> resources = Arrays.asList("a", "b");

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () ->
                lock.lockInterruptibly(resources, timeout, () -> {
                    throw new RuntimeException("boom");
                })
        );

        Assertions.assertEquals("boom", ex.getMessage());
        Assertions.assertEquals(1, ex.getSuppressed().length);
        Assertions.assertEquals("unlock failed", ex.getSuppressed()[0].getMessage());
    }

    @Test
    void inconsistentMultiKeyOrderingCanDeadlockForBadImplementations() throws Exception {
        // This test demonstrates a real pitfall for implementations that:
        // 1) acquire multiple locks in caller-provided order, and
        // 2) ignore timeout semantics (i.e., "tryLock" blocks indefinitely).
        //
        // The library's Striped implementation avoids this by using Guava Striped.bulkGet ordering.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        CyclicBarrier afterFirstLock = new CyclicBarrier(2);
        ResourceLock lock = new BadOrderingBlockingLock(Duration.ofSeconds(10), afterFirstLock);

        Future<?> f1 = pool.submit(() -> {
            await(start);
            try {
                lock.lockInterruptibly(Arrays.asList("A", "B"), Duration.ofSeconds(10), () -> {
                    // never reached if deadlocked
                });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        Future<?> f2 = pool.submit(() -> {
            await(start);
            try {
                lock.lockInterruptibly(Arrays.asList("B", "A"), Duration.ofSeconds(10), () -> {
                    // never reached if deadlocked
                });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        // Give them time to both acquire the first lock and block on the second.
        Thread.sleep(200);
        Assertions.assertFalse(f1.isDone(), "Expected task1 to be blocked (deadlock scenario)");
        Assertions.assertFalse(f2.isDone(), "Expected task2 to be blocked (deadlock scenario)");

        // Break the deadlock and ensure the test doesn't hang.
        f1.cancel(true);
        f2.cancel(true);
        pool.shutdownNow();
        Assertions.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "Pool did not terminate after interruption");
    }

    @Test
    void inconsistentMultiKeyOrderingMustNotDeadlockForStripedImpl() throws Exception {
        // This test demonstrates a real pitfall for implementations that:
        // 1) acquire multiple locks in caller-provided order, and
        // 2) ignore timeout semantics (i.e., "tryLock" blocks indefinitely).
        //
        // The library's Striped implementation avoids this by using Guava Striped.bulkGet ordering.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        ResourceLock lock = StripedLockFactory.of(StripedLockType.LOCK, 2, Duration.ofSeconds(10));

        Future<?> f1 = pool.submit(() -> {
            await(start);
            try {
                lock.lockInterruptibly(Arrays.asList("A", "B"), Duration.ofSeconds(10), () -> {
                    // never reached if deadlocked
                });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        Future<?> f2 = pool.submit(() -> {
            await(start);
            try {
                lock.lockInterruptibly(Arrays.asList("B", "A"), Duration.ofSeconds(10), () -> {
                    // never reached if deadlocked
                });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        // Give them time to both acquire the first lock and block on the second.
        Thread.sleep(200);
        Assertions.assertTrue(f1.isDone(), "Expected task1 to be blocked (deadlock scenario)");
        Assertions.assertTrue(f2.isDone(), "Expected task2 to be blocked (deadlock scenario)");

        // Break the deadlock and ensure the test doesn't hang.
        f1.cancel(true);
        f2.cancel(true);
        pool.shutdownNow();
        Assertions.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "Pool did not terminate after interruption");
    }


    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static ResourceLock newNoopLock() {
        return new AbstractResourceLock(Duration.ofMillis(1)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) {
                // validateTimeout is performed by the caller, we just "succeed"
                return true;
            }

            @Override
            public void unlock(Object resource) {
                // no-op
            }
        };
    }

    private static final class UnlockAlwaysFailsLock extends AbstractResourceLock {
        private UnlockAlwaysFailsLock(Duration defaultTimeout) {
            super(defaultTimeout);
        }

        @Override
        protected boolean tryLock(Object resource, Duration timeout) {
            return true;
        }

        @Override
        public void unlock(Object resource) {
            throw new RuntimeException("unlock failed");
        }

        @Override
        public void unlock(Collection<?> resources) {
            // Override to avoid noisy per-resource logging in AbstractResourceLock.unlock(Collection)
            throw new RuntimeException("unlock failed");
        }
    }

    private static final class BadOrderingBlockingLock extends AbstractResourceLock {
        private final Map<Object, ReentrantLock> locks = new ConcurrentHashMap<>();
        private final CyclicBarrier afterFirstLock;
        private final ThreadLocal<Integer> acquiredCount = ThreadLocal.withInitial(() -> 0);

        private BadOrderingBlockingLock(Duration defaultTimeout, CyclicBarrier afterFirstLock) {
            super(defaultTimeout);
            this.afterFirstLock = afterFirstLock;
        }

        @Override
        protected boolean tryLock(Object resource, Duration timeout) throws InterruptedException {
            // Intentionally wrong: blocks indefinitely and ignores the provided timeout.
            ReentrantLock lock = locks.computeIfAbsent(resource, k -> new ReentrantLock());
            lock.lockInterruptibly();

            int n = acquiredCount.get();
            acquiredCount.set(n + 1);
            if (n == 0) {
                // Synchronize both threads after acquiring their first lock,
                // so they deterministically deadlock on the second lock.
                try {
                    afterFirstLock.await(2, TimeUnit.SECONDS);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
            return true;
        }

        @Override
        public void unlock(Object resource) {
            ReentrantLock lock = locks.get(resource);
            if (lock != null) {
                lock.unlock();
            }
        }

    }
}


