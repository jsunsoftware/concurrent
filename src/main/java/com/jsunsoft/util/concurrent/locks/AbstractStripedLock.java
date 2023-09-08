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

import com.google.common.util.concurrent.Striped;
import com.jsunsoft.util.Executable;
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
        requireNonNull(striped, "parameter 'striped' must not be null");

        if (defaultLockTimeSec <= 0) {
            throw new IllegalArgumentException("Parameter [lockTimeSec] must be positive. Current value: [" + defaultLockTimeSec + ']');
        }

        this.defaultLockTimeSec = defaultLockTimeSec;
        this.striped = striped;
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X {
        lock(resource, defaultLockTimeSec, executable);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, int lockTimeSec, Executable<X> executable) throws X {
        requireNonNull(resource, "parameter 'resources' must not be null");
        requireNonNull(executable, "parameter 'executable' must not be null");

        try {
            lockInterruptibly(resource, lockTimeSec, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X {
        lock(resources, defaultLockTimeSec, executable);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, int lockTimeSec, Executable<X> executable) throws X {
        requireNonNull(resources, "parameter 'resources' must not be null");
        requireNonNull(executable, "parameter 'executable' must not be null");

        try {
            lockInterruptibly(resources, lockTimeSec, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resource, defaultLockTimeSec, executable);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, int lockTimeSec, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(resource, "parameter 'resources' must not be null");
        requireNonNull(executable, "parameter 'executable' must not be null");

        boolean unlock = true;

        try {
            tryToLock(resource, lockTimeSec);
            executable.execute();
        } catch (LockAcquireException e) {
            unlock = false;

            throw e;
        } finally {
            if (unlock) {
                unlock(resource);
            }
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resources, defaultLockTimeSec, executable);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, int lockTimeSec, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(resources, "parameter 'resources' must not be null");
        requireNonNull(executable, "parameter 'executable' must not be null");

        if (resources.stream().allMatch(Objects::nonNull)) {
            List<Object> lockedResources = new ArrayList<>(resources.size());
            Consumer<Object> resourceConsumer = lockedResources::add;

            try {
                for (Object resource : resources) {
                    tryToLock(resource, lockTimeSec, resourceConsumer);
                }
                executable.execute();
            } finally {
                unlock(lockedResources);
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }
    }

    @Override
    public void lock(Object resource) {
        lock(resource, defaultLockTimeSec);
    }

    @Override
    public void lock(Object resource, int lockTimeSec) {
        requireNonNull(resource, "parameter 'resource' must not be null");

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
        requireNonNull(resources, "parameter 'resources' must not be null");

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
        requireNonNull(resource, "parameter 'resource' must not be null");
        tryToLock(resource, lockTimeSec);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        lockInterruptibly(resources, defaultLockTimeSec);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, int lockTimeSec) throws InterruptedException {
        requireNonNull(resources, "parameter 'resources' must not be null");

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
        requireNonNull(resource, "parameter 'resource' must not be null");

        striped.get(resource).unlock();

        LOGGER.trace("The resource [{}] has been unlocked", resource);
    }

    @Override
    public void unlock(Collection<?> resources) {
        requireNonNull(resources, "parameter 'resources' must not be null");

        if (!resources.isEmpty()) {
            resources
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(this::unlock);
        }
    }

    private void tryToLock(Object resource, int lockTimeSec, Consumer<Object> resourceConsumer) throws InterruptedException {
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
        Thread.currentThread().interrupt();
        throw new IllegalStateException("thread was interrupted. Threads which use the lock  method  mustn't be interrupted.", e);
    }
}
