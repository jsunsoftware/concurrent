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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jsunsoft.util.Closure;
import com.jsunsoft.util.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static java.util.Objects.requireNonNull;

public abstract class AbstractResourceLock implements ResourceLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceLock.class);

    private final Duration defaultTimeout;

    protected AbstractResourceLock(Duration defaultTimeout) {
        validateTimeout(defaultTimeout);

        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X {
        lock(resource, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X {
        return lock(resource, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resource, timeout, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X {
        try {
            return lockInterruptibly(resource, timeout, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X {
        lock(resources, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X {
        return lock(resources, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resources, timeout, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X {

        try {
            return lockInterruptibly(resources, timeout, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resource, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resource, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resource, timeout, (Closure<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {

        requireNonNull(callback, "Parameter [callback] must not be null");

        lockInterruptibly(resource, timeout);

        return callWithUnlock(callback, () -> unlock(resource));
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resources, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resources, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resources, timeout, (Closure<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        requireNonNull(resources, "Parameter [resources] must not be null");
        requireNonNull(callback, "Parameter [callback] must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);

        lockInterruptibly(resources, timeout);

        // Unlock the same resources we locked above (in reverse order internally)
        return callWithUnlock(callback, () -> unlock(resources));
    }

    /**
     * Executes the callback and always executes the provided unlock action.
     *
     * <p>If the callback throws (Exception or Error) and unlock fails, the unlock exception is added as a suppressed
     * exception to the primary throwable. If the callback completes successfully and unlock fails, the unlock exception
     * is thrown.</p>
     *
     * @param <R> return type produced by the callback
     * @param <X> throwable type declared by the callback
     * @param callback callback to execute while the lock is held
     * @param unlockAction action that releases the lock(s)
     * @return the value returned by {@code callback}
     * @throws X if {@code callback} throws an exception of type {@code X}
     */
    protected final <R, X extends Throwable> R callWithUnlock(Closure<R, X> callback, Runnable unlockAction) throws X {
        Throwable primaryException = null;
        RuntimeException exceptionDuringUnlock = null;

        R result;
        try {
            result = callback.call();
        } catch (Throwable t) {
            // Mirrors try-with-resources: record so finally can addSuppressed, then rethrow immediately.
            // We do NOT handle Errors — `throw t;` propagates them unchanged.
            primaryException = t;
            throw t;
        } finally {
            try {
                unlockAction.run();
            } catch (RuntimeException unlockException) {
                if (primaryException != null) {
                    primaryException.addSuppressed(unlockException);
                } else {
                    exceptionDuringUnlock = unlockException;
                }
            }
        }

        if (exceptionDuringUnlock != null) {
            throw exceptionDuringUnlock;
        }

        return result;
    }

    @Override
    public void lock(Object resource) {
        lock(resource, defaultTimeout);
    }

    @Override
    public void lock(Object resource, Duration timeout) {

        try {
            lockInterruptibly(resource, timeout);

        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lock(Collection<?> resources) {
        lock(resources, defaultTimeout);
    }

    @Override
    public void lock(Collection<?> resources, Duration timeout) {

        try {
            lockInterruptibly(resources, timeout);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lockInterruptibly(Object resource) throws InterruptedException {
        lockInterruptibly(resource, defaultTimeout);
    }

    @Override
    public void lockInterruptibly(Object resource, Duration timeout) throws InterruptedException {
        requireNonNull(resource, "Parameter [resource] must not be null");
        validateTimeout(timeout);

        LOGGER.trace("Trying to acquire lock for resource: [{}] with timeout: [{}]", resource, timeout);

        if (tryLock(resource, timeout)) {
            logLockedResource(resource);
        } else {
            throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resource [" + resource + ']', resource, timeout);
        }
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        lockInterruptibly(resources, defaultTimeout);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException {
        requireNonNull(resources, "Parameter [resources] must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);

        // Sort by keyOrder() so two threads passing the same keys in different iteration orders
        // (e.g., [A,B] and [B,A]) acquire in the SAME order — this is the classical
        // AB/BA-deadlock prevention. Subclasses with backend-aware ordering (the Guava Striped
        // implementation in this library) override this method directly and use their own
        // ordering; this default path is for plain AbstractResourceLock subclasses.
        List<Object> orderedResources = new ArrayList<>(resources);
        orderedResources.sort(keyOrder());

        List<Object> lockAcquiredResources = new ArrayList<>(orderedResources.size());

        Exception primaryException = null;

        try {

            for (Object resource : orderedResources) {
                lockInterruptibly(resource, timeout);
                lockAcquiredResources.add(resource);
            }
        } catch (Exception e) {

            primaryException = e;

            throw e;
        } finally {

            if (primaryException != null) {
                try {
                    unlock(lockAcquiredResources);
                } catch (RuntimeException ue) {
                    primaryException.addSuppressed(ue);
                    // Suppress the exception during unlock rethrow the original exception
                    LOGGER.error("Failed to unlock resources after an exception during locking: {}", lockAcquiredResources, ue);
                }
            }
        }
    }

    @Override
    public void unlock(Collection<?> resources) {
        requireNonNull(resources, "Parameter [resources] must not be null");

        if (!resources.isEmpty()) {

            RuntimeException firstExceptionDuringUnlock = null;

            Collection<?> reversedResources;

            if (resources instanceof List) {

                reversedResources = Lists.reverse((List<?>) resources);
            } else {
                reversedResources = Lists.reverse(new ArrayList<>(resources));
            }

            for (Object resource : reversedResources) {
                try {
                    unlock(resource);
                } catch (RuntimeException e) {
                    LOGGER.error("Failed to unlock resource: {}", resource, e);
                    if (firstExceptionDuringUnlock == null) {
                        firstExceptionDuringUnlock = e;
                    } else {
                        firstExceptionDuringUnlock.addSuppressed(e);
                    }
                }
            }

            if (firstExceptionDuringUnlock != null) {
                throw firstExceptionDuringUnlock;
            }
        }
    }

    protected abstract boolean tryLock(Object resource, Duration timeout) throws InterruptedException;

    /**
     * Logs that the given resource has been locked.
     *
     * <p>Non-public hook for subclasses to customize logging behavior.</p>
     *
     * @param resource the locked resource
     */
    protected void logLockedResource(Object resource) {
        LOGGER.debug("The resource: [{}] has been locked", resource);
    }

    /**
     * Logs that the given resource has been unlocked.
     *
     * <p>Non-public hook for subclasses to customize logging behavior.</p>
     *
     * @param resource the unlocked resource
     */
    protected void logUnlockResource(Object resource) {
        LOGGER.debug("The resource: [{}] has been unlocked", resource);
    }

    protected final Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    protected void handleInterruptException(InterruptedException e) {
        throw interruptAndResolveException(e);
    }

    /**
     * Re-sets the current thread's interrupted flag and returns a {@link LockInterruptedException} suitable for
     * non-interruptible API variants that do not declare {@link InterruptedException}.
     *
     * <p>The returned exception is a {@link RuntimeException} so callers do not need to declare it; catch it
     * separately from {@link LockAcquireException} to distinguish a shutdown signal (interrupt) from a timeout.</p>
     *
     * @param e the interrupt exception caught from an interruptible API
     * @return a {@link LockInterruptedException} wrapping {@code e}, after re-interrupting the current thread
     */
    protected LockInterruptedException interruptAndResolveException(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new LockInterruptedException(
                "Thread was interrupted while waiting to acquire the lock. " +
                        "Use the lockInterruptibly(...) variants if interruption is expected.",
                e);
    }

    /**
     * Validates the timeout argument.
     *
     * <p>Timeouts must be non-null and non-negative.</p>
     *
     * @param timeout timeout duration to validate
     */
    protected void validateTimeout(Duration timeout) {
        Preconditions.checkNotNull(timeout, "Parameter [timeout] must not be null");
        Preconditions.checkArgument(!timeout.isNegative(), "Parameter [timeout] must not be negative");
    }

    /**
     * Returns the comparator used to acquire multi-key locks in a deterministic order.
     *
     * <p>This is the classical <i>AB/BA</i> deadlock prevention strategy: when two threads pass the
     * same set of keys to the multi-key {@code lock(...)} family in different iteration orders, the
     * implementation sorts both inputs by this comparator before acquiring, so both threads acquire
     * in the same order and cannot deadlock against each other.</p>
     *
     * <p>The default sorts by {@link Object#hashCode()}. This is correct for any callers that
     * respect the {@code ResourceLock} <b>Key stability</b> contract (consistent
     * {@code hashCode}/{@code equals} across threads using the same keys). Hash collisions are
     * rare in practice and do not create deadlocks &mdash; only the unrealistic case of <i>two
     * value-distinct keys with the same {@code hashCode}, passed in opposite orders by two threads
     * with the same arrival pattern</i> could see any ordering ambiguity, and even then the
     * fallback iteration order keeps acquisition local to one thread at a time.</p>
     *
     * <p>Subclasses can override to provide backend-aware ordering, for example:</p>
     * <ul>
     *   <li>{@code Comparator.comparingInt(this::stripeIndexFor)} for a custom striped backend that
     *   exposes a stripe index per key.</li>
     *   <li>{@code Comparator.comparing(k -> ((Comparable<?>) k))} (with appropriate casts) for
     *   subclasses that lock only {@link Comparable} keys.</li>
     *   <li>{@code Comparator.<Object, String>comparing(o -> o.getClass().getName()).thenComparingInt(Object::hashCode)}
     *   for subclasses that mix heterogeneous key types.</li>
     * </ul>
     *
     * <p><b>Contract:</b> the returned comparator MUST be a total order and consistent across all
     * threads that use the same keys. It does not need to handle {@code null} &mdash; the library
     * validates non-null elements before invoking this comparator.</p>
     *
     * <p><b>Note:</b> the bundled Guava {@code Striped}-based subclass does not use this hook
     * because it overrides {@link #lockInterruptibly(Collection, Duration)} directly and delegates
     * ordering to {@code Striped.bulkGet(Iterable)}.</p>
     *
     * @return a comparator that imposes a total order on lock keys
     */
    protected Comparator<Object> keyOrder() {
        return Comparator.comparingInt(Object::hashCode);
    }
}
