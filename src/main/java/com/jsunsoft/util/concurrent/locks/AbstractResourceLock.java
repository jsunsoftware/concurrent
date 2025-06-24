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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

import static java.util.Objects.requireNonNull;

abstract class AbstractResourceLock implements ResourceLock {

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

        Lock lock = null;

        try {
            lock = tryToLock(resource, timeout);

            logLockedResource(resource);

            result = callback.call();
        } finally {
            if (lock != null) {
                try {
                    lock.unlock();

                    LOGGER.trace("The resource: [{}] has been unlocked", resource);

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
    public void lock(Object resource) {
        lock(resource, defaultTimeout);
    }

    @Override
    public void lock(Object resource, Duration timeout) {

        try {
            tryToLock(resource, timeout);

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
        tryToLock(resource, timeout);

        logLockedResource(resource);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        lockInterruptibly(resources, defaultTimeout);
    }

    @Override
    public void unlock(Collection<?> resources) {
        requireNonNull(resources, "Parameter [resources] must not be null");

        if (!resources.isEmpty()) {
            resources
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(this::unlock);
        }
    }

    protected abstract Lock tryToLock(Object resource, Duration timeout) throws InterruptedException;

    protected void logLockedResource(Object resource) {
        LOGGER.trace("The resource: [{}] has been locked", resource);
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
