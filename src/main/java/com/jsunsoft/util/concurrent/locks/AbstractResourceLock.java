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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

        R result;

        RuntimeException exceptionDuringUnlock = null;

        boolean acquired = false;

        try {
            lockInterruptibly(resource, timeout);
            acquired = true;

            logLockedResource(resource);

            result = callback.call();
        } finally {
            if (acquired) {
                try {
                    unlock(resource);
                } catch (RuntimeException e) {
                    exceptionDuringUnlock = e;
                }
            }
        }

        if (exceptionDuringUnlock != null) {
            throw exceptionDuringUnlock;
        }

        return result;
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
        requireNonNull(callback, "parameter 'callback' must not be null");
        Preconditions.checkArgument(resources.stream().allMatch(Objects::nonNull), "Parameter [resources] must not contain null elements");
        validateTimeout(timeout);
        R result;

        List<Object> lockedResources = new ArrayList<>(resources.size());

        RuntimeException unlockFirstException = null;

        boolean acquired = false;

        try {
            lockInterruptibly(resources, timeout);

            acquired = true;

            result = callback.call();
        } finally {
            if (acquired) {
                try {
                    unlock(lockedResources);
                } catch (RuntimeException e) {
                    unlockFirstException = e;
                }
            }
        }

        if (unlockFirstException != null) {
            throw unlockFirstException;
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

            logLockedResource(resource);

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

        if (!tryLock(resource, timeout)) {
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

        List<Object> lockAcquiredResources = new ArrayList<>(resources.size());

        try {

            for (Object resource : resources) {
                lockInterruptibly(resource, timeout);
                lockAcquiredResources.add(resource);
                logLockedResource(resource);
            }
        } catch (Exception e) {
            try {
                unlock(lockAcquiredResources);
            } catch (RuntimeException ue) {
                // Suppress the exception during unlock rethrow the original exception
                LOGGER.error("Failed to unlock resources after an exception during locking: {}", lockAcquiredResources, ue);
            }

            throw e;
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
                    }
                }
            }

            if (firstExceptionDuringUnlock != null) {
                throw firstExceptionDuringUnlock;
            }
        }
    }

    protected abstract boolean tryLock(Object resource, Duration timeout) throws InterruptedException;

    protected void logLockedResource(Object resource) {
        LOGGER.trace("The resource: [{}] has been locked", resource);
    }

    protected void logUnlockResource(Object resource) {
        LOGGER.trace("The resource: [{}] has been unlocked", resource);
    }

    protected final Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    protected void handleInterruptException(InterruptedException e) {
        throw interruptAndResolveException(e);
    }

    protected IllegalStateException interruptAndResolveException(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new IllegalStateException("thread was interrupted. Threads which use the lock  method  mustn't be interrupted.", e);
    }

    protected void validateTimeout(Duration timeout) {
        //noinspection ConstantConditions
    }
}
