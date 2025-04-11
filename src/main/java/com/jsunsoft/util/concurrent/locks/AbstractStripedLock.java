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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

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
    public void unlock(Object resource) {
        requireNonNull(resource, "Parameter [resource] must not be null");

        striped.get(resource).unlock();

        LOGGER.trace("The resource [{}] has been unlocked", resource);
    }

    @Override
    protected void tryToLock(Object resource, Duration timeout, Consumer<Object> resourceConsumer) throws InterruptedException {
        requireNonNull(resource, "Parameter [resource] must not be null");
        validateTimeout(timeout);
        if (striped.get(resource).tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {

            if (resourceConsumer != null) {
                resourceConsumer.accept(resource);
            }

            LOGGER.trace("The resource: [{}] has been locked", resource);
        } else {
            throw new LockAcquireException("Unable to acquire lock within [" + timeout + "] for resource [" + resource + ']', resource, timeout);
        }
    }

    protected final Striped<Lock> getStriped() {
        return striped;
    }

}
