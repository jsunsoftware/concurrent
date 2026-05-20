package com.jsunsoft.util.concurrent.locks;

/*
 * One @Test per testable issue from CLAUDE_FINAL_ANALIZE.md.
 *
 * IMPORTANT: tests in this file are EXPECTED TO FAIL on master.
 * They are the live "issue tracker" — each fix should turn one red to green.
 * Do not delete these without going through the corresponding issue in the
 * analysis doc.
 *
 * A small number of tests pass today: they document current (by-design or
 * non-bug) behaviour so that future refactors don't silently change it.
 * Those are marked with the comment "DOC TEST" near the assertion.
 */

import com.google.common.collect.ImmutableList;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

class FinalAnalysisIssuesTest {

    // ---------------------------------------------------------------------
    // I1 — LAZY_WEAK_LOCK + single-key paths → Lock object reclaimed before unlock
    // ---------------------------------------------------------------------

    @Test
    @Disabled("GC-dependent; non-deterministic. Documents the hazard scenario. " +
            "Enable after I1 fix (single-key path captures the Lock).")
    @DisplayName("I1 — LAZY_WEAK_LOCK: single-key manual pair can target different Lock if GC clears the weak ref")
    void lazyWeakLock_singleKey_manualPair_demonstratesLostMutualExclusionRisk() throws Exception {
        ResourceLock lock = StripedLockFactory.of(StripedLockType.LAZY_WEAK_LOCK, 16, Duration.ofSeconds(1));

        Object key = new Object();
        lock.lock(key);

        // Force GC pressure to try to clear the weak ref to the underlying Lock.
        for (int i = 0; i < 5; i++) {
            System.gc();
            byte[] garbage = new byte[10 * 1024 * 1024];
            Assertions.assertNotNull(garbage);
        }

        // Desired: unlock works no matter what GC did to the weak cache.
        // Today: may throw IllegalMonitorStateException when the weak ref was cleared.
        Assertions.assertDoesNotThrow(() -> lock.unlock(key),
                "unlock should release the originally-acquired stripe even if Guava's weak cache was cleared");
    }

    // ---------------------------------------------------------------------
    // I2 — callWithUnlock catches Exception, not Throwable
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I2 — when callback throws Error AND unlock throws, unlock exception should be suppressed onto the Error")
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

        // Today: callWithUnlock catches Exception (not Throwable). primaryException stays null,
        // unlock exception ends up in exceptionDuringUnlock but is never thrown (the Error is unwinding).
        Assertions.assertEquals(1, caught.getSuppressed().length,
                "When callback throws Error AND unlock throws, the unlock exception must be addSuppressed onto the Error. " +
                        "Today it is lost.");
    }

    // ---------------------------------------------------------------------
    // I3 — DOC TEST. Decision: Not Covered (intentional). Under JVM-level errors
    // (OutOfMemoryError, StackOverflowError, ...) the JVM is dying and lock
    // leaks are an accepted contract — catching Throwable inside an internal
    // unlock loop is over-engineering. The original catch (RuntimeException)
    // stays. This test documents the accepted behaviour. See I3 in
    // CLAUDE_FINAL_ANALIZE.md for the discussion.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I3 — DOC TEST: Error during unlock loop aborts the loop; leaks under JVM-level errors are accepted")
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
                        "Under JVM-level errors (OOM) this is by design, not a bug.");
    }

    // ---------------------------------------------------------------------
    // I4 — DOC TEST. Decision: Not Covered (intentional). Mirrors I3 — under
    // JVM-level errors (OutOfMemoryError, StackOverflowError, ...) the JVM is
    // dying and stripe leaks are an accepted contract. The catch in the
    // multi-key acquisition path stays at `catch (Exception)` /
    // `catch (InterruptedException | RuntimeException)`. See I4 in
    // CLAUDE_FINAL_ANALIZE.md for the rationale.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I4 — DOC TEST: Error during multi-key acquisition leaks already-acquired stripes; accepted under JVM-level errors")
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
            // expected today
        }

        // Documents accepted contract: Error during acquisition bypasses `catch (Exception)`,
        // primaryException stays null, finally skips cleanup, acquired stripes leak.
        // Under JVM-level errors this is by design — not a bug.
        Assertions.assertEquals(2, acquired.get(), "Two stripes acquired before BOOM");
        Assertions.assertEquals(0, released.get(),
                "Accepted contract: Error in tryLock aborts acquisition without releasing prior stripes. " +
                        "Under JVM-level errors (OOM) this is by design, not a bug.");
    }

    // ---------------------------------------------------------------------
    // I5 — Cross-thread acquire-while-holder-waits + collision → deadlock
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I5 — cross-thread same-stripe collision causes deadlock (only avoided by timeout)")
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

            // DOC TEST: passes today. Demonstrates that without a timeout, this is a hard deadlock.
            Assertions.assertFalse(innerEntered.get(),
                    "Cross-thread same-stripe collision: inner cannot enter while outer holds the stripe. " +
                            "Today only `defaultTimeout` saves us from deadlock — document this in javadoc.");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // I6 — Mutable key whose hashCode changes between lock and unlock
    // ---------------------------------------------------------------------

    static final class MutableKey {
        int id;

        MutableKey(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey && ((MutableKey) o).id == this.id;
        }

        @Override
        public String toString() {
            return "MK(" + id + ")";
        }
    }

    // DOC TEST. Decision: deferred to FUTURE_LIST.md → DF-3. Key stability is the
    // caller's documented contract (see ResourceLock class-level Javadoc "Key
    // stability" bullet). Mutating a key's hashCode mid-lock breaks that contract;
    // unlock then targets a different stripe than was acquired, throws
    // IllegalMonitorStateException, and leaks the originally-acquired stripe.
    // Code-level prevention is parked together with DF-1 (same engineering work).

    @Test
    @DisplayName("I6 — DOC TEST: mutating a key's hashCode between lock and unlock breaks unlock by contract")
    void mutableKey_hashCodeChangedBetweenLockAndUnlock_failsToRelease_byContract() {
        ResourceLock lock = StripedLockFactory.of(64, Duration.ofSeconds(2));
        MutableKey k = new MutableKey(1);

        lock.lock(k);
        k.id = 1_000_007;  // caller violates the documented key-stability contract

        // Documented consequence (ResourceLock Javadoc, "Key stability" bullet):
        // unlock resolves the lock by key, lands on a different stripe than was
        // acquired, fails with IllegalMonitorStateException, and leaks stripe S(1).
        // Tracked as DF-3 in FUTURE_LIST.md for future code-level prevention.
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> lock.unlock(k),
                "Caller violated key-stability contract; unlock targets a different stripe");
    }

    // ---------------------------------------------------------------------
    // I7 — DOC TEST. Decision: option B applied (Javadoc on unlock(Collection) spells out
    // the symmetry contract). The library does NOT try to infer what was "really" held;
    // mismatched lock/unlock collections produce exactly what was requested — including
    // permanently-held stripes for elements that were acquired but not in the unlock
    // collection. Caller's responsibility. See I7 in CLAUDE_FINAL_ANALIZE.md and the
    // Javadoc on ResourceLock.unlock(Collection).
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I7 — DOC TEST: manual unlock(Collection) is symmetric with lock(Collection); mismatched calls leak by contract")
    void manualMultiKey_unlockWithDifferentCollection_leaksOriginalStripes_byContract() throws Exception {
        ResourceLock lock = StripedLockFactory.of(64, Duration.ofMillis(300));

        // Acquire on [A, B]
        lock.lock(Arrays.asList("A", "B"));

        // Caller bug: unlock with mismatched collection [A, X] where X is NOT B
        // The library does NOT try to be smart about what was "really" held.
        try {
            lock.unlock(Arrays.asList("A", "X"));
        } catch (RuntimeException expected) {
            // unlocking X (not held) throws IllegalMonitorStateException — expected
        }

        // Documented contract: B was NOT in the passed collection, so it remains held.
        // The library does not infer "what the caller meant to unlock".
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

        // cleanup: release B properly
        try {
            lock.unlock("B");
        } catch (Exception ignored) { /* best-effort */ }
    }

    // ---------------------------------------------------------------------
    // I8 + I33 — base-class subclass with reversed multi-key order does not deadlock
    // thanks to the keyOrder() hook (default sorts by Object.hashCode()).
    //
    // The existing PitfallsAndSemanticsTest.inconsistentMultiKeyOrderingCanDeadlockForBadImplementations
    // still demonstrates the negative case (a deliberately bad impl whose tryLock IGNORES timeout
    // — sorting can't save you from that). This positive test demonstrates that a well-behaved
    // non-Striped subclass that DOES respect timeout no longer deadlocks on reversed key orders.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I8 + I33 — well-behaved base-class subclass with reversed multi-key order does not deadlock (keyOrder() default sorts by hashCode)")
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
            } catch (Exception ignored) { /* test will fail via the latch assertion */ }
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
            } catch (Exception ignored) { /* test will fail via the latch assertion */ }
        });

        try {
            Assertions.assertTrue(done.await(2, TimeUnit.SECONDS),
                    "Reversed multi-key acquisition on a well-behaved non-Striped subclass should not deadlock " +
                            "(keyOrder() sorts both inputs to the same order).");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // I9 — Non-interruptible paths rethrow interrupt as IllegalStateException
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I9 — interrupt in non-interruptible variant throws LockInterruptedException, with the InterruptedException as cause and interrupt flag re-set")
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
    // I10 — unlockAll loses subsequent exceptions
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I10 — multi-unlock with multiple failures should surface all of them (first + suppressed)")
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

        // Today: only the first exception is rethrown; subsequent ones are LOGGER.error'd only.
        Assertions.assertEquals(2, caught.getSuppressed().length,
                "Multi-unlock should report first failure + others as suppressed. Got: " +
                        caught.getSuppressed().length);
    }

    // ---------------------------------------------------------------------
    // I11 — Reentrancy + same-stripe collisions (documents behaviour)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I11 — same-thread nested lock on different keys that collide on the same stripe silently succeeds (reentrancy)")
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

        // DOC TEST: passes today. Locks current behaviour. Fix is documentation (javadoc).
        Assertions.assertEquals(2, maxDepth.get(),
                "Same-stripe nested lock from same thread succeeds via ReentrantLock reentrancy. " +
                        "This is by design (Guava Striped semantics); the fix is documentation.");
    }

    // ---------------------------------------------------------------------
    // I12 — lockInterruptibly always uses defaultTimeout
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I12 — lockInterruptibly(Object, Executable) should not silently use defaultTimeout (JDK Lock contract is indefinite-but-interruptible)")
    void lockInterruptibly_currentlyAlwaysUsesDefaultTimeout_notJdkContract() throws Exception {
        // Set a short defaultTimeout; another thread holds the lock for longer.
        // Desired: lockInterruptibly waits until the lock is available (or interrupted).
        // Today: it throws LockAcquireException after defaultTimeout — diverges from JDK contract.

        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(100));
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
        held.await();

        AtomicReference<Throwable> caughtOnWaiter = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                lock.lockInterruptibly("X", () -> {
                });
            } catch (Throwable t) {
                caughtOnWaiter.set(t);
            }
        });
        waiter.start();
        waiter.join(500);

        Assertions.assertTrue(waiter.isAlive(),
                "lockInterruptibly should wait until lock is available or interrupted. " +
                        "Today it throws LockAcquireException after defaultTimeout (" + caughtOnWaiter.get() + ").");

        waiter.interrupt();
        waiter.join(2000);
        release.countDown();
        holder.join(2000);
    }

    // ---------------------------------------------------------------------
    // I13 — LockAcquireException.resources is transient
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I13 — LockAcquireException.resources should survive serialisation")
    void lockAcquireException_serialiseRoundTrip_preservesResources() throws Exception {
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

            LockAcquireException caught = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock("HOLD", Duration.ofMillis(50), () -> {
                    })
            );

            Assertions.assertNotNull(caught.getResources());
            Assertions.assertFalse(caught.getResources().isEmpty());

            // round-trip
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(caught);
            }
            LockAcquireException restored;
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                restored = (LockAcquireException) ois.readObject();
            }

            // Today: `transient resources` → getResources() returns null after deserialise.
            Assertions.assertNotNull(restored.getResources(),
                    "After deserialisation, getResources() should not be null");
            Assertions.assertFalse(restored.getResources().isEmpty(),
                    "After deserialisation, getResources() should not be empty");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // I14 — No boolean tryLock(...) (non-throwing) overload
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I14 — ResourceLock should expose a boolean tryLock(Object) for non-throwing best-effort callers")
    void tryLock_nonThrowingApiIsMissing() {
        Method m = null;
        try {
            m = ResourceLock.class.getMethod("tryLock", Object.class);
        } catch (NoSuchMethodException ignored) {
            // expected today
        }
        Assertions.assertNotNull(m,
                "ResourceLock should have a boolean tryLock(Object) method (returns false instead of throwing)");
        Assertions.assertEquals(boolean.class, m.getReturnType());
    }

    // ---------------------------------------------------------------------
    // I15 — LockAcquireException carries no holder identity
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I15 — LockAcquireException should expose getHolder() / getStripeIndex() for diagnostics")
    void lockAcquireException_carriesHolderIdentity_forDiagnostics() {
        Method m = null;
        try {
            m = LockAcquireException.class.getMethod("getHolder");
        } catch (NoSuchMethodException ignored) {
            // expected today
        }
        Assertions.assertNotNull(m,
                "LockAcquireException should expose getHolder() (Thread, best-effort) to cut MTTR for incidents");
    }

    // ---------------------------------------------------------------------
    // I16 — Duration.ZERO accepted; message pretends a timeout expired
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I16 — Duration.ZERO should either be rejected or produce a 'no wait' message, not a timeout-style message")
    void durationZero_isAccepted_butMessageIsMisleading() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofSeconds(1));
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

            LockAcquireException ex = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock("X", Duration.ZERO, () -> {
                    })
            );

            // Today: message is "Unable to acquire lock within [PT0S] for resource [X]" — misleading.
            Assertions.assertFalse(ex.getMessage().contains("within [PT0S]"),
                    "Duration.ZERO should not produce a 'within [PT0S]' message — that suggests a timeout expired. " +
                            "Got: " + ex.getMessage());
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // I17 — lock(Object) uses defaultTimeout (documents current behaviour)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I17 — lock(Object) uses defaultTimeout (NOT JDK Lock.lock() semantics)")
    void lock_noTimeoutOverload_actuallyUsesDefaultTimeout_documentation() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(80));
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

            long start = System.currentTimeMillis();
            Assertions.assertThrows(LockAcquireException.class, () -> lock.lock("X"));
            long elapsed = System.currentTimeMillis() - start;

            // DOC TEST: passes today. Locks current divergence from JDK Lock.lock() (which waits indefinitely).
            Assertions.assertTrue(elapsed < 1000,
                    "lock(Object) waits at most defaultTimeout, NOT indefinitely like JDK Lock.lock(). " +
                            "Elapsed=" + elapsed + "ms.");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // I18 — 30-method interface
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I18 — ResourceLock should have a small core (~6 methods) with the rest as default methods")
    void resourceLock_publicInterfaceHasUnreasonableMethodCount() {
        int abstractCount = 0;
        for (Method m : ResourceLock.class.getMethods()) {
            if (m.getDeclaringClass() == ResourceLock.class
                    && Modifier.isAbstract(m.getModifiers())
                    && !m.isDefault()) {
                abstractCount++;
            }
        }
        // Target after refactoring: ≤ 10 abstract methods.
        Assertions.assertTrue(abstractCount <= 10,
                "ResourceLock has " + abstractCount + " abstract methods. " +
                        "Consider splitting into a small core + default convenience methods (target ≤ 10).");
    }

    // ---------------------------------------------------------------------
    // I21 — getResources() immutability (REFINED — see also I62)
    //
    // VERIFIED: today the single-key path uses Collections.singleton(...) (immutable),
    // and the multi-key path *also* uses Collections.singleton(...) because Java picks
    // the (String, Object, Duration) overload over (String, Collection<Object>, Duration)
    // for an argument typed Collection<?> (generic invariance).
    //
    // Result: I21 ("mutable view") is ACCIDENTALLY MITIGATED today. Status downgraded
    // to "Not an Issue (accidentally OK)". A second bug surfaces instead — see I62.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I21 — getResources() happens to be immutable today (accidental, via Collections.singleton)")
    void lockAcquireException_getResources_returnsImmutableView() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(1));
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

            LockAcquireException ex = Assertions.assertThrows(LockAcquireException.class, () ->
                    lock.lock("X", Duration.ofMillis(20), () -> {
                    })
            );

            Collection<Object> resources = ex.getResources();
            // DOC TEST: passes today; both single- and multi-key paths route through
            // Collections.singleton(...) which is immutable. Locks behaviour in case
            // a future refactor of LockAcquireException's ctor resolution changes this.
            Assertions.assertThrows(UnsupportedOperationException.class, resources::clear,
                    "getResources() returns Collections.singleton(...) today — accidentally immutable");
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // I62 — Multi-key LockAcquireException wraps the ENTIRE input collection
    //       in a single-element Set instead of carrying the individual keys.
    //       Root cause: Java method resolution prefers (String, Object, Duration)
    //       over (String, Collection<Object>, Duration) because Collection<?> is
    //       NOT assignable to Collection<Object>. The Collection<Object> ctor is
    //       therefore dead code from the library's own call sites.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I62 — multi-key LockAcquireException.getResources() should contain the individual keys, not the wrapped Collection")
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

            // Today: ex.getResources() == [Arrays.asList("A","B")] — a singleton Set
            //        containing the WHOLE input collection. Size = 1.
            // Desired: ex.getResources() contains "A" and "B" individually. Size = 2.
            Assertions.assertEquals(2, ex.getResources().size(),
                    "Multi-key LockAcquireException must carry individual keys, not the wrapped Collection. " +
                            "Today getResources() returns " + ex.getResources() + " (size=" + ex.getResources().size() + ").");

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
    // I22 — LockToken (AutoCloseable) API is missing
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I22 — LockToken (AutoCloseable) for try-with-resources is missing")
    void lockToken_autoCloseable_apiIsMissing() {
        try {
            Class<?> tokenClass = Class.forName("com.jsunsoft.util.concurrent.locks.LockToken");
            Assertions.assertTrue(AutoCloseable.class.isAssignableFrom(tokenClass),
                    "LockToken should implement AutoCloseable");
        } catch (ClassNotFoundException e) {
            Assertions.fail("LockToken class is missing — try-with-resources API not exposed");
        }
    }

    // ---------------------------------------------------------------------
    // I23 — Cross-thread unlock leaks bare IllegalMonitorStateException
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I23 — unlock from non-owner thread should throw a library-specific exception, not raw IllegalMonitorStateException")
    void unlockFromNonOwnerThread_throwsDedicatedException_notRawIllegalMonitorStateException() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofSeconds(5));
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

            Exception ex = Assertions.assertThrows(Exception.class, () -> lock.unlock("X"));
            Assertions.assertNotEquals(IllegalMonitorStateException.class, ex.getClass(),
                    "Expected a library-specific exception (e.g., LockOwnershipException), got raw " +
                            ex.getClass().getSimpleName());
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    // ---------------------------------------------------------------------
    // I28 — DelegateResourceLock cannot read stripe index for metrics
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I28 — ResourceLock should expose stripeIndexFor(Object) for observability decorators")
    void delegateResourceLock_cannotExposeStripeIndex_forMetrics() {
        Method m = null;
        try {
            m = ResourceLock.class.getMethod("stripeIndexFor", Object.class);
        } catch (NoSuchMethodException ignored) {
            // expected today
        }
        Assertions.assertNotNull(m,
                "ResourceLock should expose stripeIndexFor(Object) so metrics decorators can tag by stripe");
    }

    // ---------------------------------------------------------------------
    // I34 — Multi-key dedupe by Lock identity
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("I34 — lock(List.of(A,B)) on a single stripe should hold the stripe once (not twice via reentrancy)")
    void multiKey_sameStripeKeys_currentlyLockUnderlyingLockTwice_butShouldDedupe() throws Exception {
        ResourceLock lock = StripedLockFactory.of(1, Duration.ofMillis(300));  // 1 stripe ⇒ every key collides

        // Acquire two distinct keys that map to the same stripe.
        lock.lock(ImmutableList.of("A", "B"));

        // Today: stripe acquired with count=2 (reentrancy). One unlock leaves count=1.
        // Desired (after dedupe): stripe acquired with count=1. One unlock leaves it free.
        lock.unlock("A");

        AtomicBoolean otherAcquired = new AtomicBoolean(false);
        Thread other = new Thread(() -> {
            try {
                lock.lock("B", Duration.ofMillis(200), () -> otherAcquired.set(true));
            } catch (LockAcquireException ignored) {
                // bug: stripe still held by current thread reentrant count
            }
        });
        other.start();
        other.join(2000);

        Assertions.assertTrue(otherAcquired.get(),
                "After dedupe, lock([A,B]) + unlock(A) on a colliding stripe should free the stripe. " +
                        "Today the stripe is still held due to redundant double-acquisition.");

        // cleanup: release the residual hold
        try {
            lock.unlock("B");
        } catch (Exception ignored) { /* best-effort */ }
    }
}
