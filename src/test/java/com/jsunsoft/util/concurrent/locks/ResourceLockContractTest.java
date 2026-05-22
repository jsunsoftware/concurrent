package com.jsunsoft.util.concurrent.locks;

/*
 * Regression and DOC tests for the locking library's established contract.
 *
 * The tests in this file fall into two categories:
 *
 *   1. RESOLVED-issue regression tests — green today; assert the fixed behaviour
 *      so a future refactor cannot silently revert it.
 *
 *   2. ACCEPTED / BY-DESIGN doc tests — green today; lock in behaviour that the
 *      maintainer reviewed and explicitly chose not to change (e.g., the JVM-level
 *      error contracts that accept lock leaks under OOM; the same-thread reentrancy
 *      on stripe collisions; the intentional absence of a stripeIndexFor(...) hook).
 *
 * Tests for parked work (not-yet-applied fixes; hazards documented as DOC
 * tests) live in DeferredIssuesTest.java. Do not mix the two.
 *
 * Conventions:
 *   * Tests labelled "DOC TEST" assert intentional behaviour (category 2 above).
 *   * Every assertion message names the contract being protected.
 */

import com.google.errorprone.annotations.ThreadSafe;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

class ResourceLockContractTest {

    // ---------------------------------------------------------------------
    // callWithUnlock: when callback throws Error AND unlock throws,
    // the unlock exception is preserved as a suppressed throwable on the Error.
    // (Matches try-with-resources semantics.)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("callback throws Error + unlock throws → unlock exception is suppressed onto the Error")
    void callbackThrowsError_unlockFailure_isPreservedAsSuppressed() {
        AbstractResourceLock lock = new AbstractResourceLock(Duration.ofMillis(1)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) {
                return true;
            }

            @Override
            public void unlock(Object resource) {
                throw new RuntimeException("unlock-failed");
            }
        };

        Error caught = Assertions.assertThrows(Error.class, () ->
                lock.lockInterruptibly("k", Duration.ofMillis(1), () -> {
                    throw new Error("callback-failed");
                })
        );

        Assertions.assertEquals(1, caught.getSuppressed().length,
                "When callback throws Error AND unlock throws, the unlock exception must be addSuppressed onto the Error.");
    }

    // ---------------------------------------------------------------------
    // DOC TEST — Accepted contract under JVM-level errors (OOM, StackOverflow).
    // Internal unlock loop catches RuntimeException only; an Error aborts the
    // loop and leaks remaining stripes. Under JVM-level errors the JVM is
    // dying anyway; this is by design, not a bug.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: Error during unlock loop aborts the loop; leaks under JVM-level errors are accepted")
    void unlockAll_errorFromOneUnlock_abortsLoop_acceptedConsequenceOfJvmLevelError() {
        AtomicInteger unlocksCompleted = new AtomicInteger(0);

        AbstractResourceLock lock = new AbstractResourceLock(Duration.ofMillis(1)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) {
                return true;
            }

            @Override
            public void unlock(Object resource) {
                if ("BOOM".equals(resource)) {
                    throw new OutOfMemoryError("simulated");
                }
                unlocksCompleted.incrementAndGet();
            }
        };

        // Reversed iteration unlocks E (✓), D (✓), BOOM (Error escapes loop),
        // and B/A are never reached. The leak of B and A is the accepted contract.
        List<String> keys = Arrays.asList("A", "B", "BOOM", "D", "E");
        Assertions.assertThrows(OutOfMemoryError.class, () -> lock.unlock(keys));

        Assertions.assertEquals(2, unlocksCompleted.get(),
                "Accepted contract: the unlock loop aborts on Error; locks past the failing entry leak. " +
                        "Under JVM-level errors (OOM) this is by design.");
    }

    // ---------------------------------------------------------------------
    // DOC TEST — Same contract on the multi-key acquisition side. Error during
    // tryLock aborts the loop without releasing already-acquired stripes.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: Error during multi-key acquisition leaks already-acquired stripes; accepted under JVM-level errors")
    void multiKeyAcquisition_errorFromTryLock_leaksAlreadyAcquiredStripes_acceptedUnderJvmLevelErrors() {
        AtomicInteger acquired = new AtomicInteger(0);
        AtomicInteger released = new AtomicInteger(0);

        AbstractResourceLock lock = new AbstractResourceLock(Duration.ofMillis(1)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) {
                if ("BOOM".equals(resource)) {
                    throw new OutOfMemoryError("simulated");
                }
                acquired.incrementAndGet();
                return true;
            }

            @Override
            public void unlock(Object resource) {
                released.incrementAndGet();
            }
        };

        try {
            lock.lockInterruptibly(Arrays.asList("A", "B", "BOOM"), Duration.ofMillis(1));
            Assertions.fail("expected OutOfMemoryError");
        } catch (OutOfMemoryError | InterruptedException expected) {
            // expected
        }

        Assertions.assertEquals(2, acquired.get(), "Two stripes acquired before BOOM");
        Assertions.assertEquals(0, released.get(),
                "Accepted contract: Error in tryLock aborts acquisition without releasing prior stripes. " +
                        "Under JVM-level errors (OOM) this is by design.");
    }

    // ---------------------------------------------------------------------
    // DOC TEST — Cross-thread same-stripe collision. With one stripe every key
    // collides, so a holder thread that waits for a worker that needs ANY key on
    // the same lock will deadlock unless `defaultTimeout` cuts it loose. The
    // ResourceLock Javadoc spells this out under "Cross-thread acquisition".
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: cross-thread same-stripe collision is broken only by defaultTimeout")
    void crossThreadCollision_holderWaitsForWaiter_deadlocks() throws Exception {
        // 1 stripe → every key collides with every other key.
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(300));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicBoolean innerEntered = new AtomicBoolean(false);

        try {
            Future<?> outer = pool.submit(() -> {
                lock.lock("X", () -> {
                    Future<?> inner = pool.submit(() -> {
                        try {
                            lock.lock("Y", () -> innerEntered.set(true));
                        } catch (LockAcquireException timedOut) {
                            // expected: cross-thread collision → inner times out
                        }
                    });
                    try {
                        inner.get(5, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        // ignore
                    }
                });
            });
            outer.get(10, TimeUnit.SECONDS);

            Assertions.assertFalse(innerEntered.get(),
                    "Cross-thread same-stripe collision: inner cannot enter while outer holds the stripe. " +
                            "Only `defaultTimeout` saves us from deadlock — documented in ResourceLock Javadoc.");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // DOC TEST — Symmetry contract for the manual lock(Collection) / unlock(Collection)
    // pair. The library does NOT infer "what was really held"; mismatched calls
    // produce exactly what was requested.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: manual unlock(Collection) must match lock(Collection); mismatched calls leak by contract")
    void manualMultiKey_unlockWithDifferentCollection_leaksOriginalStripes_byContract() throws Exception {
        ResourceLock lock = StripedLockFactory.of(64, Duration.ofMillis(300));

        // Acquire on [A, B]
        lock.lock(Arrays.asList("A", "B"));

        // Caller bug: unlock with mismatched collection [A, X] where X is NOT B
        try {
            lock.unlock(Arrays.asList("A", "X"));
        } catch (RuntimeException expected) {
            // unlocking X (not held) throws IllegalMonitorStateException — expected
        }

        // Documented contract: B was NOT in the passed collection, so it remains held.
        AtomicBoolean otherAcquired = new AtomicBoolean(false);
        Thread other = new Thread(() -> {
            try {
                lock.lock("B", Duration.ofMillis(300), () -> otherAcquired.set(true));
            } catch (LockAcquireException expected) {
                // expected: B is still held by the original thread
            }
        });
        other.start();
        other.join(2000);

        Assertions.assertFalse(otherAcquired.get(),
                "Documented contract: unlock(Collection) is symmetric with lock(Collection); " +
                        "mismatched calls leave the locked-but-not-unlocked stripes held. " +
                        "Use lock(Collection, Executable) for automatic safety.");

        // cleanup
        try {
            lock.unlock("B");
        } catch (Exception ignored) { /* best-effort */ }
    }

    // ---------------------------------------------------------------------
    // keyOrder() — a well-behaved non-Striped subclass with reversed multi-key
    // order does not deadlock thanks to the keyOrder() hook (default sorts by
    // Object.hashCode()).
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("well-behaved base-class subclass with reversed multi-key order does not deadlock (keyOrder() default sorts by hashCode)")
    void baseClassMultiKey_reversedOrder_sortedByKeyOrder_doesNotDeadlock() throws Exception {
        ConcurrentMap<Object, ReentrantLock> backingLocks = new ConcurrentHashMap<>();
        ResourceLock lock = new AbstractResourceLock(Duration.ofMillis(500)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) throws InterruptedException {
                return backingLocks.computeIfAbsent(resource, k -> new ReentrantLock())
                        .tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
            }

            @Override
            public void unlock(Object resource) {
                ReentrantLock l = backingLocks.get(resource);
                if (l != null) l.unlock();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(2);
        CyclicBarrier start = new CyclicBarrier(2);

        pool.submit(() -> {
            try {
                start.await(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                return;
            }
            try {
                lock.lock(Arrays.asList("A", "B"), () -> {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {
                    }
                    done.countDown();
                });
            } catch (Exception ignored) { /* test fails via the latch assertion */ }
        });

        pool.submit(() -> {
            try {
                start.await(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                return;
            }
            try {
                lock.lock(Arrays.asList("B", "A"), () -> {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {
                    }
                    done.countDown();
                });
            } catch (Exception ignored) { /* test fails via the latch assertion */ }
        });

        try {
            Assertions.assertTrue(done.await(2, TimeUnit.SECONDS),
                    "Reversed multi-key acquisition on a well-behaved non-Striped subclass must not deadlock " +
                            "(keyOrder() sorts both inputs to the same order).");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // Interrupting a waiter in a non-interruptible lock(...) variant throws
    // LockInterruptedException (not raw IllegalStateException), with the
    // InterruptedException as cause and the thread interrupt flag re-set.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("interrupt in non-interruptible variant throws LockInterruptedException, cause = InterruptedException, interrupt flag re-set")
    void nonInterruptibleLock_interruptedWhileWaiting_throwsLockInterruptedException() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofSeconds(10));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> lock.lock("X", () -> {
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        }));
        holder.start();
        try {
            held.await();

            AtomicReference<Throwable> caught = new AtomicReference<>();
            AtomicBoolean interruptedFlagAfterCatch = new AtomicBoolean(false);
            Thread waiter = new Thread(() -> {
                try {
                    lock.lock("X", () -> {
                    });  // non-interruptible variant
                } catch (Throwable t) {
                    caught.set(t);
                    interruptedFlagAfterCatch.set(Thread.currentThread().isInterrupted());
                }
            });
            waiter.start();
            Thread.sleep(100);
            waiter.interrupt();
            waiter.join(2000);

            Assertions.assertNotNull(caught.get(), "expected the waiter to throw something");
            Assertions.assertInstanceOf(LockInterruptedException.class, caught.get(),
                    "Expected LockInterruptedException. Got: " + caught.get());
            Assertions.assertInstanceOf(InterruptedException.class, caught.get().getCause(),
                    "Cause should be the original InterruptedException");
            Assertions.assertTrue(interruptedFlagAfterCatch.get(),
                    "Thread.interrupted flag should be re-set so higher layers can detect the interrupt");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // Multi-unlock with multiple RuntimeException failures surfaces all of them
    // (first one thrown + the rest attached as suppressed).
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("multi-unlock with multiple failures surfaces all of them (first + suppressed)")
    void multiKeyUnlock_multipleFailures_allSurfacedAsSuppressed() {
        AbstractResourceLock lock = new AbstractResourceLock(Duration.ofMillis(1)) {
            @Override
            protected boolean tryLock(Object resource, Duration timeout) {
                return true;
            }

            @Override
            public void unlock(Object resource) {
                throw new RuntimeException("unlock-" + resource);
            }
        };

        List<String> keys = Arrays.asList("A", "B", "C");

        RuntimeException caught = Assertions.assertThrows(RuntimeException.class, () -> lock.unlock(keys));

        Assertions.assertEquals(2, caught.getSuppressed().length,
                "Multi-unlock must report first failure + others as suppressed. Got: " +
                        caught.getSuppressed().length);
    }

    // ---------------------------------------------------------------------
    // DOC TEST — Same-thread nested lock on two same-stripe keys succeeds via
    // ReentrantLock reentrancy. Documented in ResourceLock's "Reentrancy on
    // same-stripe keys" Javadoc bullet.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: same-thread nested lock on two same-stripe keys succeeds via reentrancy")
    void sameThread_differentKeysSameStripe_silentlySerialiseNothing() {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofSeconds(1));  // 1 stripe → every key collides
        AtomicInteger maxDepth = new AtomicInteger(0);
        AtomicInteger depth = new AtomicInteger(0);

        lock.lock("A", () -> {
            maxDepth.accumulateAndGet(depth.incrementAndGet(), Math::max);
            lock.lock("B", () -> {
                maxDepth.accumulateAndGet(depth.incrementAndGet(), Math::max);
                depth.decrementAndGet();
            });
            depth.decrementAndGet();
        });

        Assertions.assertEquals(2, maxDepth.get(),
                "Same-stripe nested lock from same thread should succeed via ReentrantLock reentrancy " +
                        "(documented in ResourceLock Javadoc).");
    }

    // ---------------------------------------------------------------------
    // LockAcquireException.getResources() returns an immutable view on BOTH
    // single-key (Collections.singleton) and multi-key (defensive copy +
    // Collections.unmodifiableCollection) throw sites.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("LockAcquireException.getResources() is immutable on both single-key and multi-key throw sites")
    void lockAcquireException_getResources_returnsImmutableView() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(1));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> lock.lock("HOLD", () -> {
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        }));
        holder.start();
        try {
            held.await();

            // Single-key throw site — Collections.singleton(...) is immutable.
            LockAcquireException singleEx = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock("HOLD", Duration.ofMillis(20), () -> {
                    })
            );
            Assertions.assertThrows(UnsupportedOperationException.class, singleEx.getResources()::clear,
                    "single-key getResources() must be immutable");

            // Multi-key throw site — defensive copy + Collections.unmodifiableCollection(...).
            LockAcquireException multiEx = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock(Arrays.asList("HOLD", "Y"), Duration.ofMillis(20), () -> {
                    })
            );
            Assertions.assertThrows(UnsupportedOperationException.class, multiEx.getResources()::clear,
                    "multi-key getResources() must be immutable");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // Multi-key LockAcquireException carries individual keys (size = N), not
    // the wrapped Collection (size = 1). The ctor signature is Collection<?>
    // so Java method resolution routes multi-key calls to it.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("multi-key LockAcquireException.getResources() carries individual keys (size = N), not the wrapped Collection (size = 1)")
    void multiKeyLockAcquireException_getResources_containsIndividualKeys_notWrappedCollection() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(50));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> lock.lock("HOLD", () -> {
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        }));
        holder.start();
        try {
            held.await();

            List<String> keys = Arrays.asList("A", "B");
            LockAcquireException ex = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock(keys, Duration.ofMillis(20), () -> {
                    })
            );

            Assertions.assertEquals(2, ex.getResources().size(),
                    "Multi-key LockAcquireException must carry individual keys, not the wrapped Collection. " +
                            "Got " + ex.getResources() + " (size=" + ex.getResources().size() + ").");
            Assertions.assertTrue(ex.getResources().contains("A"),
                    "getResources() should contain individual key 'A'");
            Assertions.assertTrue(ex.getResources().contains("B"),
                    "getResources() should contain individual key 'B'");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // DOC TEST — ResourceLock intentionally does NOT expose stripeIndexFor(Object).
    // Guava's Striped does not publicly expose its indexFor(key) method; adding
    // a stripeIndexFor(...) hook would need either reflection (fragile,
    // module-hostile) or a re-implementation of Guava's private smear() hashing.
    // Metrics callers can compute their own bucket as
    // Math.floorMod(key.hashCode(), bucketCount) from outside.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DOC TEST: ResourceLock intentionally does NOT expose stripeIndexFor(Object)")
    void resourceLock_intentionallyDoesNotExposeStripeIndexFor() {
        Assertions.assertThrows(NoSuchMethodException.class,
                () -> ResourceLock.class.getMethod("stripeIndexFor", Object.class),
                "ResourceLock.stripeIndexFor(Object) is intentionally absent — Guava Striped does not expose its index publicly");
    }

    // ---------------------------------------------------------------------
    // LockInterruptedException rejects a null cause at construction (the cause
    // is the contract-meaningful payload; we don't want a "wrapped nothing"
    // exception sneaking past).
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("LockInterruptedException rejects a null cause at construction")
    void lockInterruptedException_rejectsNullCause() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new LockInterruptedException("msg", null),
                "LockInterruptedException must require a non-null InterruptedException cause");
    }

    // ---------------------------------------------------------------------
    // LockAcquireException defensively copies the multi-key Collection at
    // construction. Mutating the caller's original collection after the
    // exception is constructed must not affect getResources().
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("LockAcquireException defensively copies multi-key resources — mutating the caller's collection later does not affect getResources()")
    void lockAcquireException_multiKey_defensivelyCopiesResources() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(50));
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        Thread holder = new Thread(() -> lock.lock("HOLD", () -> {
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        }));
        holder.start();
        try {
            held.await();

            // Use a mutable List that we'll modify AFTER the throw.
            List<String> keys = new ArrayList<>(Arrays.asList("A", "B"));
            LockAcquireException ex = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock(keys, Duration.ofMillis(20), () -> {
                    })
            );

            // Snapshot what the exception saw.
            Collection<Object> snapshot = ex.getResources();
            Assertions.assertEquals(2, snapshot.size(), "exception should carry 2 keys");
            Assertions.assertTrue(snapshot.containsAll(Arrays.asList("A", "B")));

            // Mutate the caller's original collection. Defensive copy means the exception is unaffected.
            keys.clear();
            keys.add("Z");

            Assertions.assertEquals(2, ex.getResources().size(),
                    "Defensive copy: caller mutating the original collection must not change getResources(). " +
                            "After clear()+add(Z), got " + ex.getResources());
            Assertions.assertTrue(ex.getResources().contains("A"));
            Assertions.assertTrue(ex.getResources().contains("B"));
            Assertions.assertFalse(ex.getResources().contains("Z"));
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // ResourceLock is annotated @ThreadSafe (com.google.errorprone.annotations).
    // Static-analysis tools that understand this annotation now treat the
    // interface as a thread-safe type without any pom changes.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("ResourceLock is annotated @ThreadSafe (com.google.errorprone.annotations)")
    void resourceLock_isAnnotated_ThreadSafe() {
        Assertions.assertTrue(ResourceLock.class.isAnnotationPresent(ThreadSafe.class),
                "ResourceLock must carry @com.google.errorprone.annotations.ThreadSafe " +
                        "so static-analysis tools treat it as a thread-safe type");
    }
}
