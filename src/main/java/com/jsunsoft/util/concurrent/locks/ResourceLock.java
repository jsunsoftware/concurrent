package com.jsunsoft.util.concurrent.locks;
/*
 * Copyright 2017 Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.errorprone.annotations.ThreadSafe;
import com.jsunsoft.util.Closure;
import com.jsunsoft.util.Executable;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Interface representing a lock mechanism that can be used to synchronize the execution of code blocks.
 *
 * <p><b>Important notes</b></p>
 * <ul>
 *   <li><b>Key stability</b>: {@code resource} keys must have a stable {@link Object#hashCode()} / {@link Object#equals(Object)}
 *   while the lock is held. The library's unlock path resolves the underlying lock by key (effectively
 *   {@code striped.get(key).unlock()}), so if the key's {@code hashCode} or {@code equals} changes between
 *   {@code lock(key)} and {@code unlock(key)}, unlock will target a different stripe than the one acquired &mdash; the
 *   unlock will fail with {@link IllegalMonitorStateException} and <b>the originally-acquired stripe will be leaked</b>
 *   until process exit. The same hazard applies if the caller passes a logically-different collection to
 *   {@code unlock(Collection)} than to {@code lock(Collection)}. Prefer immutable keys (e.g., {@code String},
 *   {@code UUID}, value-typed objects, boxed primitives), or freeze the key's identifying fields before passing it to
 *   any {@code lock(...)} method. Arrays must never be used as keys &mdash; their {@code hashCode}/{@code equals} are
 *   based on identity, not contents.</li>
 *   <li><b>Striped locks semantics</b>: implementations based on Guava {@code Striped} provide <i>striped</i> locking, not
 *   one-lock-per-key. Different keys may map to the same stripe, so parallelism is best-effort.</li>
 *   <li><b>Reentrancy on same-stripe keys (same thread)</b>: the underlying locks are reentrant (Guava's
 *   {@code Striped} uses {@link java.util.concurrent.locks.ReentrantLock}). Combined with the previous bullet's
 *   "different keys may share a stripe" property, this means a single thread can <i>silently nest</i> lock calls
 *   on two distinct keys that happen to hash to the same stripe &mdash; the inner {@code lock(...)} succeeds via
 *   reentrancy rather than blocking. Multi-key calls like {@code lock(List.of("A","B"))} share the same property:
 *   if both keys map to the same stripe, the same underlying lock is acquired (and released) twice. This is by
 *   design and is what makes the library correct under collisions, but is worth being explicit about:
 *   <i>different keys do not imply independent locks</i>. Callers that depend on mutual exclusion holding between
 *   two specific distinct keys must either size the stripe count high enough to make collisions negligible, or
 *   accept that collisions serialize.</li>
 *   <li><b>Multi-key deadlock avoidance</b>: the bundled implementations (Guava {@code Striped}-based and the
 *   {@code AbstractResourceLock} base class) sort the caller's collection into a deterministic order before
 *   acquiring, so two threads passing the same multiset of keys in different iteration orders (e.g., {@code [A,B]}
 *   and {@code [B,A]}) acquire in the same order and cannot deadlock against each other. The Guava-based
 *   implementation uses {@code Striped.bulkGet(Iterable)} (stripe-index order); the base class uses a hashCode-based
 *   comparator that subclasses can override via the protected {@code keyOrder()} hook. Custom implementations that
 *   neither inherit from {@code AbstractResourceLock} nor sort internally must ensure callers provide keys in a
 *   consistent global order.</li>
 *   <li><b>Timeout for collections</b>: collection-based methods apply the timeout <i>per lock acquisition</i>. Worst case
 *   waiting time can be {@code resources.size() × timeout}.</li>
 *   <li><b>Cross-thread acquisition (stripe-collision deadlock)</b>: never hold a {@code ResourceLock} while waiting
 *   on another thread that may try to acquire any key on the same {@code ResourceLock} instance. Because striping is
 *   best-effort (two seemingly distinct keys can map to the same underlying lock), this hazard is more common than
 *   "different keys → no contention" intuition suggests. Concretely: if thread A holds {@code lock("X")} and waits
 *   (e.g., via {@code future.get()}) for thread B which then calls {@code lock("Y")} where the two keys collide on
 *   the same stripe, the two threads deadlock. {@code ReentrantLock} reentrancy does not help because B is a
 *   different thread. <b>The {@code defaultTimeout} configured at construction is the safety net</b>: once it
 *   expires on the waiter, {@link LockAcquireException} is thrown and the holder can unwind. Choose
 *   {@code defaultTimeout} accordingly &mdash; pick a value that bounds incident-recovery time, and avoid
 *   extremely large values (e.g., hours, days) that would turn this scenario into a true deadlock.</li>
 * </ul>
 */
@ThreadSafe
public interface ResourceLock {

    /**
     * Void-returning variant of {@link #lock(Object, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lock(Object, Duration, Closure)
     */
    <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X;

    /**
     * Variant of {@link #lock(Object, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lock(Object, Duration, Closure)
     */
    <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X;

    /**
     * Void-returning variant of {@link #lock(Object, Duration, Closure)}.
     *
     * @see #lock(Object, Duration, Closure)
     */
    <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X;

    /**
     * Acquires the lock for {@code resource}, runs {@code callback}, and releases the lock &mdash; reliably,
     * even if {@code callback} or the release itself throws. This is the canonical single-key lambda variant
     * of the {@code lock(...)} family; all the shorter overloads delegate here.
     *
     * <p>Acquisition behaviour:</p>
     * <ul>
     *   <li>If the lock cannot be acquired within {@code timeout}, throws {@link LockAcquireException}.</li>
     *   <li>If the calling thread is interrupted while waiting, throws {@link LockInterruptedException}
     *   (this method is the non-interruptible variant &mdash; use {@link #lockInterruptibly(Object, Duration, Closure)}
     *   if your caller wants to handle interrupts via checked {@link InterruptedException}).</li>
     *   <li>If acquisition succeeds, {@code callback.call()} runs while the lock is held. Any throwable propagates;
     *   the unlock attempt happens in a finally block, and an unlock failure is attached as a suppressed exception
     *   on the primary throwable (mirroring try-with-resources).</li>
     * </ul>
     *
     * @param resource the key to lock on; must have stable {@link Object#hashCode()} / {@link Object#equals(Object)}
     *                 between this call and the corresponding unlock (see the "Key stability" bullet on the
     *                 {@code ResourceLock} class Javadoc)
     * @param timeout  maximum time to wait for the lock &mdash; see
     *                 {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback code to run while the lock is held; receives no arguments, may return any value, may
     *                 throw any checked exception type {@code X}
     * @param <R>      return type of {@code callback}
     * @param <X>      checked exception type that {@code callback} may throw
     * @return whatever {@code callback} returns
     * @throws X                          if {@code callback} throws
     * @throws LockAcquireException       if the lock cannot be acquired within {@code timeout}
     * @throws LockInterruptedException   if the current thread is interrupted while waiting
     */
    <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Void-returning variant of {@link #lock(Collection, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lock(Collection, Duration, Closure)
     */
    <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X;

    /**
     * Variant of {@link #lock(Collection, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lock(Collection, Duration, Closure)
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X;

    /**
     * Void-returning variant of {@link #lock(Collection, Duration, Closure)}.
     *
     * @see #lock(Collection, Duration, Closure)
     */
    <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X;

    /**
     * Acquires all {@code resources} in a deterministic global order, runs {@code callback}, and releases the locks
     * in reverse order &mdash; reliably, even if any step throws. This is the canonical multi-key lambda variant
     * of the {@code lock(...)} family; all the shorter overloads delegate here.
     *
     * <p>Acquisition behaviour:</p>
     * <ul>
     *   <li>The {@code timeout} is applied <b>per stripe</b>, not as a total budget. Worst-case wait time is
     *   {@code resources.size() × timeout}.</li>
     *   <li>The bundled implementations sort {@code resources} into a deterministic order before locking, so two
     *   threads passing the same multiset of keys in different iteration orders cannot AB/BA-deadlock against each
     *   other &mdash; see the "Multi-key deadlock avoidance" bullet on the {@code ResourceLock} class Javadoc.</li>
     *   <li>If any lock cannot be acquired within its {@code timeout}, already-acquired stripes are released and
     *   {@link LockAcquireException} is thrown.</li>
     *   <li>If the calling thread is interrupted while waiting, throws {@link LockInterruptedException}.</li>
     *   <li>If acquisition fully succeeds, {@code callback.call()} runs while the locks are held. Any throwable
     *   propagates; release happens in a finally block; release failures are attached as suppressed exceptions on
     *   the primary throwable.</li>
     * </ul>
     *
     * @param resources the keys to lock; the collection may not contain {@code null} elements (rejected
     *                  with {@link IllegalArgumentException})
     * @param timeout   per-stripe acquisition timeout
     * @param callback  code to run while all locks are held
     * @param <R>       return type of {@code callback}
     * @param <X>       checked exception type that {@code callback} may throw
     * @return whatever {@code callback} returns
     * @throws X                          if {@code callback} throws
     * @throws LockAcquireException       if any lock cannot be acquired within its per-stripe {@code timeout}
     * @throws LockInterruptedException   if the current thread is interrupted while waiting
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Void-returning variant of {@link #lockInterruptibly(Object, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lockInterruptibly(Object, Duration, Closure)
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X;

    /**
     * Variant of {@link #lockInterruptibly(Object, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lockInterruptibly(Object, Duration, Closure)
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Void-returning variant of {@link #lockInterruptibly(Object, Duration, Closure)}.
     *
     * @see #lockInterruptibly(Object, Duration, Closure)
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Interruptible counterpart of {@link #lock(Object, Duration, Closure)}: if the calling thread is interrupted
     * while waiting to acquire {@code resource}, this method throws checked {@link InterruptedException}
     * (the non-interruptible {@code lock(...)} family throws {@link LockInterruptedException} instead).
     *
     * <p>Otherwise behaves identically to {@link #lock(Object, Duration, Closure)} &mdash; see that method for
     * the rest of the contract (timeout, callback execution, suppressed-exception handling).</p>
     *
     * @param resource the key to lock on
     * @param timeout  maximum time to wait for the lock
     * @param callback code to run while the lock is held
     * @param <R>      return type of {@code callback}
     * @param <X>      checked exception type that {@code callback} may throw
     * @return whatever {@code callback} returns
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws X                    if {@code callback} throws
     * @throws LockAcquireException if the lock cannot be acquired within {@code timeout}
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Void-returning variant of {@link #lockInterruptibly(Collection, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lockInterruptibly(Collection, Duration, Closure)
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X;

    /**
     * Variant of {@link #lockInterruptibly(Collection, Duration, Closure)} that uses the configured default timeout.
     *
     * @see #lockInterruptibly(Collection, Duration, Closure)
     */
    <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Void-returning variant of {@link #lockInterruptibly(Collection, Duration, Closure)}.
     *
     * @see #lockInterruptibly(Collection, Duration, Closure)
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Interruptible counterpart of {@link #lock(Collection, Duration, Closure)}: if the calling thread is interrupted
     * while waiting to acquire any of {@code resources}, this method throws checked {@link InterruptedException}
     * (the non-interruptible {@code lock(...)} family throws {@link LockInterruptedException} instead).
     *
     * <p>Otherwise behaves identically to {@link #lock(Collection, Duration, Closure)} &mdash; multi-key sorted
     * acquisition, per-stripe timeout, partial-acquisition rollback, callback execution, suppressed-exception
     * handling.</p>
     *
     * @param resources the keys to lock; the collection may not contain {@code null} elements
     * @param timeout   per-stripe acquisition timeout
     * @param callback  code to run while all locks are held
     * @param <R>       return type of {@code callback}
     * @param <X>       checked exception type that {@code callback} may throw
     * @return whatever {@code callback} returns
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws X                    if {@code callback} throws
     * @throws LockAcquireException if any lock cannot be acquired within its per-stripe {@code timeout}
     */
    <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Locks the given resource.
     *
     * @param resource resource to lock
     */
    void lock(Object resource);

    /**
     * Locks the given resource for a specified time.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock
     */
    void lock(Object resource, Duration timeout);

    /**
     * Locks the given collection of resources.
     *
     * <p><b>Symmetry contract.</b> This is the manual counterpart of {@link #unlock(Collection)}. The caller is
     * responsible for passing the same logical multiset of keys to {@code unlock(Collection)} when releasing &mdash;
     * see {@link #unlock(Collection)} for the consequences of mismatched calls. For automatic acquire/release
     * symmetry, prefer the lambda variants {@link #lock(Collection, Executable)} / {@link #lock(Collection, Closure)}.</p>
     *
     * @param resources collection of resources to lock
     */
    void lock(Collection<?> resources);

    /**
     * Locks the given collection of resources for a specified time.
     *
     * <p><b>Symmetry contract.</b> See {@link #unlock(Collection)} for the matching-call requirement.</p>
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for the lock
     */
    void lock(Collection<?> resources, Duration timeout);

    /**
     * Locks the given resource interruptibly.
     *
     * @param resource resource to lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Object resource) throws InterruptedException;

    /**
     * Locks the given resource interruptibly for a specified time.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Object resource, Duration timeout) throws InterruptedException;

    /**
     * Locks the given collection of resources interruptibly.
     *
     * <p><b>Symmetry contract.</b> See {@link #unlock(Collection)} for the matching-call requirement.</p>
     *
     * @param resources collection of resources to lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Collection<?> resources) throws InterruptedException;

    /**
     * Locks the given collection of resources interruptibly for a specified time.
     * Note that the method will unlock all resources if any of them cannot be locked within the specified timeout or any exception is thrown.
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for the lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws LockAcquireException if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException;

    /**
     * Unlocks the given resource.
     *
     * @param resource resource to unlock
     */
    void unlock(Object resource);

    /**
     * Unlocks the given collection of resources.
     *
     * <p><b>Symmetry contract</b>: this is the manual counterpart of {@link #lock(Collection)}. The implementation
     * calls {@code unlock} for each element in {@code resources} (in reverse order in the bundled striped
     * implementation); it does <b>not</b> infer what was acquired by a previous {@code lock(...)} call and it does
     * <b>not</b> attempt to "fix up" mismatched arguments. Passing a different multiset of keys than was originally
     * acquired produces exactly the behavior requested:</p>
     *
     * <ul>
     *   <li>Elements present in {@code resources} but <b>not</b> currently held by the calling thread will throw
     *   {@link IllegalMonitorStateException} (propagated by the underlying lock).</li>
     *   <li>Elements that were acquired but are <b>not</b> in the {@code resources} passed to this method remain
     *   <b>permanently held</b> until the calling thread either calls {@code unlock} on them explicitly or the JVM
     *   exits. The library does not attempt to detect or recover from this caller bug.</li>
     * </ul>
     *
     * <p>For callers who want automatic, exception-safe acquire/release with no chance of asymmetric calls, use the
     * lambda variants {@link #lock(Collection, Executable)} or {@link #lock(Collection, Closure)} instead of the
     * manual {@code lock(Collection)} / {@code unlock(Collection)} pair.</p>
     *
     * @param resources collection of resources to unlock; must match the multiset of keys previously passed to
     *                  {@link #lock(Collection)} / {@link #lockInterruptibly(Collection)}
     */
    void unlock(Collection<?> resources);
}
