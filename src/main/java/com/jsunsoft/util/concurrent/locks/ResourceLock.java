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
public interface ResourceLock {

    /**
     * Uses the default {@code timeout}.
     *
     * @param resource   resource to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Object, Executable)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lock(Object, Duration, Executable)
     */
    <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resource resource to lock
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Object, Closure)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X;

    /**
     * @param resource   resource to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Object, Executable)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X;

    /**
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Object, Closure)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resources  collection of resources to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Collection, Executable)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lock(Collection, Duration, Executable)
     */
    <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resources collection of resources to lock
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Collection, Closure)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X;

    /**
     * @param resources  collection of resources to lock
     * @param timeout    the maximum time to wait for <b>each</b> lock. Worst case total wait is {@code resources.size() × timeout}.
     *                   See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Collection, Executable)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X;

    /**
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for <b>each</b> lock. Worst case total wait is {@code resources.size() × timeout}.
     *                  See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws LockInterruptedException If the thread was interrupted while waiting to acquire the lock.
     *                               Use the method {@link #lockInterruptibly(Collection, Closure)} if thread can be interrupted.
     * @throws LockAcquireException   if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Difference between the {@link #lock(Object, Executable)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resource   resource to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lockInterruptibly(Collection, Duration, Executable)
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Closure)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resource resource to lock
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Executable)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resource   resource to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Closure)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Executable)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resources  collection of resources to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lockInterruptibly(Collection, Duration, Executable)
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Closure)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout} as maximum time to wait for each resource lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}.
     *
     * @param resources collection of resources to lock
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Executable)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resources  collection of resources to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws X                    Custom exception which can be thrown from method execute.
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Closure)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for each resource lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws X                    Custom exception which can be thrown from method execute.
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
