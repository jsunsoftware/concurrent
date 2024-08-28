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
import com.jsunsoft.util.Executable;
import com.jsunsoft.util.LockCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

abstract class AbstractStripedLock implements Lock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStripedLock.class);

    private final int defaultLockTimeSec;
    private final Striped<java.util.concurrent.locks.Lock> striped;

    AbstractStripedLock(int defaultLockTimeSec, Striped<java.util.concurrent.locks.Lock> striped) {
        requireNonNull(striped, "Parameter [striped] must not be null");

        Preconditions.checkArgument(defaultLockTimeSec > 0, "Parameter [defaultLockTimeSec] must be positive. Current value: [%s]", defaultLockTimeSec);

        this.defaultLockTimeSec = defaultLockTimeSec;
        this.striped = striped;
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X {
        lock(resource, defaultLockTimeSec, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, LockCallback<R, X> callback) throws X {
        return lock(resource, defaultLockTimeSec, callback);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, int lockTimeSec, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resource, lockTimeSec, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, int lockTimeSec, LockCallback<R, X> callback) throws X {
        try {
            return lockInterruptibly(resource, lockTimeSec, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X {
        lock(resources, defaultLockTimeSec, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, LockCallback<R, X> callback) throws X {
        return lock(resources, defaultLockTimeSec, callback);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, int lockTimeSec, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resources, lockTimeSec, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, int lockTimeSec, LockCallback<R, X> callback) throws X {

        try {
            return lockInterruptibly(resources, lockTimeSec, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resource, defaultLockTimeSec, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, LockCallback<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resource, defaultLockTimeSec, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, int lockTimeSec, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resource, lockTimeSec, (LockCallback<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, int lockTimeSec, LockCallback<R, X> callback) throws InterruptedException, X {

        requireNonNull(callback, "Parameter [callback] must not be null");

        R result;
        boolean unlock = true;

        try {
            tryToLock(resource, lockTimeSec);
            result = callback.call();
        } catch (LockAcquireException e) {
            unlock = false;

            throw e;
        } finally {
            if (unlock) {
                unlock(resource);
            }
        }

        return result;
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resources, defaultLockTimeSec, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, LockCallback<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resources, defaultLockTimeSec, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, int lockTimeSec, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resources, lockTimeSec, (LockCallback<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, int lockTimeSec, LockCallback<R, X> callback) throws InterruptedException, X {
        requireNonNull(resources, "Parameter [resources] must not be null");
        requireNonNull(callback, "parameter 'callback' must not be null");
        Preconditions.checkArgument(lockTimeSec > 0, "Parameter [lockTimeSec] must be positive. Current value: [%s]", lockTimeSec);

        R result;

        if (resources.stream().allMatch(Objects::nonNull)) {
            List<Object> lockedResources = new ArrayList<>(resources.size());
            Consumer<Object> resourceConsumer = lockedResources::add;

            try {
                for (Object resource : resources) {
                    tryToLock(resource, lockTimeSec, resourceConsumer);
                }
                result = callback.call();
            } finally {
                unlock(lockedResources);
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }

        return result;
    }

    @Override
    public void lock(Object resource) {
        lock(resource, defaultLockTimeSec);
    }

    @Override
    public void lock(Object resource, int lockTimeSec) {

        try {
            tryToLock(resource, lockTimeSec);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lock(Collection<?> resources) {
        lock(resources, defaultLockTimeSec);
    }

    @Override
    public void lock(Collection<?> resources, int lockTimeSec) {

        try {
            lockInterruptibly(resources, lockTimeSec);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lockInterruptibly(Object resource) throws InterruptedException {
        lockInterruptibly(resource, defaultLockTimeSec);
    }

    @Override
    public void lockInterruptibly(Object resource, int lockTimeSec) throws InterruptedException {
        tryToLock(resource, lockTimeSec);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        lockInterruptibly(resources, defaultLockTimeSec);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, int lockTimeSec) throws InterruptedException {
        requireNonNull(resources, "Parameter [resources] must not be null");
        Preconditions.checkArgument(lockTimeSec > 0, "Parameter [lockTimeSec] must be positive. Current value: [%s]", lockTimeSec);

        if (resources.stream().allMatch(Objects::nonNull)) {
            for (Object resource : resources) {
                tryToLock(resource, lockTimeSec);
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }
    }

    @Override
    public void unlock(Object resource) {
        requireNonNull(resource, "Parameter [resource] must not be null");

        striped.get(resource).unlock();

        LOGGER.trace("The resource [{}] has been unlocked", resource);
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

    private void tryToLock(Object resource, int lockTimeSec, Consumer<Object> resourceConsumer) throws InterruptedException {
        Preconditions.checkArgument(lockTimeSec > 0, "Parameter [lockTimeSec] must be positive. Current value: [%s]", lockTimeSec);
        requireNonNull(resource, "Parameter [resource] must not be null");

        if (striped.get(resource).tryLock(lockTimeSec, TimeUnit.SECONDS)) {

            if (resourceConsumer != null) {
                resourceConsumer.accept(resource);
            }

            LOGGER.trace("The resource: [{}] has been locked", resource);
        } else {
            throw new LockAcquireException("Unable to acquire lock within [" + lockTimeSec + "] seconds for [" + resource + ']');
        }
    }

    private void tryToLock(Object resource, int lockTimeSec) throws InterruptedException {
        tryToLock(resource, lockTimeSec, null);
    }

    private void handleInterruptException(InterruptedException e) {
        throw interruptAndResolveException(e);
    }

    private IllegalStateException interruptAndResolveException(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new IllegalStateException("thread was interrupted. Threads which use the lock  method  mustn't be interrupted.", e);
    }
}
