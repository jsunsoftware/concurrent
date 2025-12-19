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
import com.jsunsoft.util.concurrent.locks.striped.StripedLockType;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * StripedLock is a ResourceLock implementation. It is a wrapper around {@link com.google.common.util.concurrent.Striped}
 * and provides ResourceLock based on {@link com.google.common.util.concurrent.Striped}.
 * </p>
 * <p>
 * StripedLock is a lock that allows multiple threads to hold the lock at the same time, but only one thread can hold
 * the lock for a given stripe. This allows for better concurrency and performance in multi-threaded applications.
 * </p>
 *
 * @see com.google.common.util.concurrent.Striped
 * @deprecated since 2.0.2, use {@link com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory} instead. Will be removed in 3.0.0.
 */
@Deprecated
public interface StripedLock extends ResourceLock {


    /**
     * Creates and returns a StripedLock according to the specified type.
     *
     * @param type           type of striped lock
     * @param stripes        Minimum number of stripes. See the documentation {@link com.google.common.util.concurrent.Striped}
     * @param defaultTimeout the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @return StripedLock instance
     */
    static StripedLock of(StripedLockType type, int stripes, Duration defaultTimeout) {
        Objects.requireNonNull(type, "Parameter [type] must not be null");

        StripedLock result;
        switch (type) {
            case LOCK:
                result = new StripedLockImpl(Striped.lock(stripes), defaultTimeout);
                break;
            case LAZY_WEAK_LOCK:
                result = new StripedLockImpl(Striped.lazyWeakLock(stripes), defaultTimeout);
                break;
            default:
                throw new IllegalArgumentException("Unhandled type: " + type);
        }
        return result;
    }

    /**
     * Creates and returns a StripedLock according to the {@code StripedLockType.LOCK} type.
     *
     * @param stripes        Minimum number of stripes. See the documentation {@link com.google.common.util.concurrent.Striped}
     * @param defaultTimeout the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @return StripedLock instance
     * @see #of(StripedLockType, int, Duration)
     */
    static StripedLock of(int stripes, Duration defaultTimeout) {
        return of(StripedLockType.LOCK, stripes, defaultTimeout);
    }
}
