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
import com.jsunsoft.util.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

abstract class AbstractStripedLock implements Lock {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripedLock.class);

    private final int lockTimeSec;
    private final Striped<java.util.concurrent.locks.Lock> striped;

    AbstractStripedLock(int lockTimeSec, Striped<java.util.concurrent.locks.Lock> striped) {
        if (lockTimeSec <= 0) {
            throw new IllegalArgumentException("Parameter [lockTimeSec] must be positive. Current value: ]" + lockTimeSec + ']');
        }

        this.lockTimeSec = lockTimeSec;
        this.striped = striped;
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Command<X> command) throws X {
        requireNonNull(resource, "parameter 'resources' must not be null");
        lock(Collections.singleton(resource), command);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Command<X> command) throws X {
        requireNonNull(resources, "parameter 'resources' must not be null");
        requireNonNull(command, "parameter 'command' must not be null");

        try {
            lockInterruptibly(resources, command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("thread was interrupted. Threads which use the lock  method  mustn't be interrupted.", e);
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Command<X> command) throws InterruptedException, X {
        requireNonNull(resource, "parameter 'resources' must not be null");
        lockInterruptibly(Collections.singleton(resource), command);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Command<X> command) throws InterruptedException, X {
        requireNonNull(resources, "parameter 'resources' must not be null");
        requireNonNull(command, "parameter 'command' must not be null");

        if (resources.stream().allMatch(Objects::nonNull)) {
            List<Object> lockedResources = new ArrayList<>(resources.size());
            try {
                for (Object resource : resources) {
                    if (striped.get(resource).tryLock(lockTimeSec, TimeUnit.SECONDS)) {

                        lockedResources.add(resource);
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("The resource: [{}] has been locked", resource);
                        }
                    } else {
                        throw new LockAcquireException("Unable to acquire lock within [" + lockTimeSec + "] seconds for [" + resource + ']');
                    }
                }
                command.run();
            } finally {
                lockedResources.forEach(resource -> {
                    striped.get(resource).unlock();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("The resource [{}] has been unlocked", resource);
                    }
                });
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }
    }
}
