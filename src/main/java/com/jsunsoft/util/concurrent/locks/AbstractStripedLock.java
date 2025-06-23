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

abstract class AbstractStripedLock extends AbstractResourceLock implements StripedLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStripedLock.class);

    private final Striped<Lock> striped;

    protected AbstractStripedLock(Duration defaultTimeout, Striped<Lock> striped) {
        super(defaultTimeout);

        requireNonNull(striped, "Parameter [striped] must not be null");
        validateTimeout(defaultTimeout);

        this.striped = striped;
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException {
        requireNonNull(resources, "Parameter [resources] must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);

        List<Lock> acquiredLocks = new ArrayList<>(resources.size());

        Iterable<Lock> locks = striped.bulkGet(resources);

        try {
            for (Lock lock : locks) {

                if (lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    acquiredLocks.add(lock);
                } else {
                    throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resources: " + resources, resources, timeout);
                }
            }

            LOGGER.trace("The resources: {} have been locked", resources);

        } catch (Exception e) {

            unlockAll(acquiredLocks, resources);

            throw e;
        }
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        requireNonNull(resources, "Parameter [resources] must not be null");
        requireNonNull(callback, "parameter 'callback' must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);
        R result;

        List<Lock> acquiredLocks = new ArrayList<>(resources.size());

        Iterable<Lock> locks = striped.bulkGet(resources);

        RuntimeException firstException;

        try {
            for (Lock lock : locks) {

                if (lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    acquiredLocks.add(lock);
                } else {
                    throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resources: " + resources, resources, timeout);
                }
            }

            LOGGER.trace("The resources: {} have been locked.", resources);

            result = callback.call();
        } finally {

            firstException = unlockAll(acquiredLocks, resources);
        }

        if (firstException != null) {
            throw firstException;
        }

        return result;
    }

    @Override
    public void unlock(Object resource) {
        requireNonNull(resource, "Parameter [resource] must not be null");

        striped.get(resource).unlock();

        LOGGER.trace("The resource: [{}] has been unlocked", resource);
    }

    @Override
    protected void tryToLock(Object resource, Duration timeout) throws InterruptedException {
        requireNonNull(resource, "Parameter [resource] must not be null");
        validateTimeout(timeout);
        if (striped.get(resource).tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {

            LOGGER.trace("The resource: [{}] has been locked", resource);
        } else {
            throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resource [" + resource + ']', resource, timeout);
        }
    }

    protected final Striped<Lock> getStriped() {
        return striped;
    }

    private RuntimeException unlockAll(List<Lock> locks, Collection<?> resources) {
        RuntimeException firstExceptionDuringUnlock = null;

        for (int i = locks.size() - 1; i >= 0; i--) {
            try {
                locks.get(i).unlock();
            } catch (RuntimeException e) {
                LOGGER.error("Failed to unlock resources: {}", resources, e);
                if (firstExceptionDuringUnlock == null) {
                    firstExceptionDuringUnlock = e;
                }
            }
        }

        return firstExceptionDuringUnlock;
    }
}
