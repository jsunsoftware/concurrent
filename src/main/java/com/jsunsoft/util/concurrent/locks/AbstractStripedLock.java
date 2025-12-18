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
import com.google.common.util.concurrent.Striped;
import com.jsunsoft.util.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static java.util.Objects.requireNonNull;

/**
 * Striped {@link ResourceLock} implementation backed by Guava {@link Striped}.
 *
 * <p>This class overrides some multi-key behavior from {@link AbstractResourceLock}:</p>
 * <ul>
 *   <li><b>Multi-key acquisition order</b>: uses {@link Striped#bulkGet(Iterable)} to obtain the underlying locks in a
 *   deterministic order intended to avoid deadlocks even if callers pass keys in different orders.</li>
 *   <li><b>Multi-key unlock</b>: unlocks based on the underlying striped locks returned by {@code bulkGet}, rather than
 *   iterating the original key collection and calling {@code striped.get(key).unlock()} for each key.</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
abstract class AbstractStripedLock extends AbstractResourceLock implements StripedLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStripedLock.class);

    private final Striped<Lock> striped;

    protected AbstractStripedLock(Duration defaultTimeout, Striped<Lock> striped) {
        super(defaultTimeout);

        requireNonNull(striped, "Parameter [striped] must not be null");
        validateTimeout(defaultTimeout);

        this.striped = striped;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Differs from {@link AbstractResourceLock#lockInterruptibly(Collection, Duration)} by acquiring the underlying
     * striped locks via {@link Striped#bulkGet(Iterable)} (deterministic order), which helps avoid deadlocks for reversed
     * multi-key acquisition order.</p>
     */
    @Override
    public void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException {
        requireNonNull(resources, "Parameter [resources] must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);

        List<Lock> acquiredLocks = new ArrayList<>(resources.size());

        Iterable<Lock> locks = striped.bulkGet(resources);

        try {
            for (Lock lock : locks) {

                if (tryLock(lock, timeout)) {
                    acquiredLocks.add(lock);
                } else {
                    throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resources: " + resources, resources, timeout);
                }
            }

            LOGGER.trace("The resources: {} have been locked", resources);

        } catch (Exception e) {
            RuntimeException unlockException = unlockAll(acquiredLocks, resources);

            if (unlockException != null) {
                e.addSuppressed(unlockException);
            }

            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Differs from {@link AbstractResourceLock#lockInterruptibly(Collection, Duration, Closure)} by acquiring the
     * underlying striped locks via {@link Striped#bulkGet(Iterable)} (deterministic order), and by unlocking using the
     * same acquired lock list (in reverse order).</p>
     */
    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        requireNonNull(resources, "Parameter [resources] must not be null");
        requireNonNull(callback, "parameter 'callback' must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);

        List<Lock> acquiredLocks = new ArrayList<>(resources.size());

        Iterable<Lock> locks = striped.bulkGet(resources);

        //Acquire all locks. If acquisition fails, release what is acquired and rethrow.
        try {
            for (Lock lock : locks) {
                if (tryLock(lock, timeout)) {
                    acquiredLocks.add(lock);
                } else {
                    throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resources: " + resources, resources, timeout);
                }
            }

            LOGGER.debug("The resources: {} have been locked.", resources);
        } catch (InterruptedException | RuntimeException e) {
            // Failed during acquisition; release what we acquired and rethrow.
            RuntimeException unlockException = unlockAll(acquiredLocks, resources);
            if (unlockException != null) {
                e.addSuppressed(unlockException);
            }
            throw e;
        }

        // Phase 2: execute callback and always unlock exactly once.
        return callWithUnlock(callback, () -> {
            RuntimeException unlockException = unlockAll(acquiredLocks, resources);
            if (unlockException != null) {
                throw unlockException;
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note: the resource key must be stable (consistent {@code hashCode}/{@code equals}) while held. Unlocking calls
     * {@code striped.get(resource).unlock()}.</p>
     */
    @Override
    public void unlock(Object resource) {
        requireNonNull(resource, "Parameter [resource] must not be null");

        striped.get(resource).unlock();

        logUnlockResource(resource);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Differs from {@link AbstractResourceLock#unlock(Collection)}: this implementation unlocks based on the
     * underlying striped locks returned by {@link Striped#bulkGet(Iterable)}. This avoids relying on the iteration order
     * of the provided {@link Collection} and matches the multi-key acquisition strategy used by this class.</p>
     */
    @Override
    public void unlock(Collection<?> resources) {
        requireNonNull(resources, "Parameter [resources] must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");

        List<Lock> locks = new ArrayList<>();
        for (Lock lock : striped.bulkGet(resources)) {
            locks.add(requireNonNull(lock, "Striped returned null lock"));
        }

        RuntimeException unlockException = unlockAll(locks, resources);
        if (unlockException != null) {
            throw unlockException;
        }
    }

    @Override
    protected boolean tryLock(Object resource, Duration timeout) throws InterruptedException {
        requireNonNull(resource, "Parameter [resource] must not be null");
        return tryLock(striped.get(resource), timeout);
    }

    protected boolean tryLock(Lock lock, Duration timeout) throws InterruptedException {
        requireNonNull(lock, "Parameter [lock] must not be null");
        validateTimeout(timeout);

        return lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Exposes the underlying Guava {@link Striped} instance used by this lock.
     *
     * <p>Intended for advanced usage and testing.</p>
     */
    protected final Striped<Lock> getStriped() {
        return striped;
    }

    /**
     * Unlocks the acquired locks in reverse order.
     *
     * <p>Non-public helper used to keep multi-key unlock behavior consistent between acquisition methods and the manual
     * {@link #unlock(Collection)} method.</p>
     *
     * @param locks     locks to unlock (in acquisition order)
     * @param resources original resources collection (used only for logging)
     * @return the first {@link RuntimeException} thrown during unlock (if any)
     */
    private RuntimeException unlockAll(List<Lock> locks, Collection<?> resources) {
        RuntimeException firstExceptionDuringUnlock = null;

        List<Lock> reversedLocks = Lists.reverse(locks);
        for (Lock lock : reversedLocks) {
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                LOGGER.error("Failed to unlock resources: {}", resources, e);
                if (firstExceptionDuringUnlock == null) {
                    firstExceptionDuringUnlock = e;
                }
            }
        }

        LOGGER.debug("The resources: {} have been unlocked.", resources);

        return firstExceptionDuringUnlock;
    }
}
