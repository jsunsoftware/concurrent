package com.jsunsoft.util.concurrent.locks.striped;
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

import com.jsunsoft.util.concurrent.locks.ResourceLock;
import com.jsunsoft.util.concurrent.locks.StripedLock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class StripedLockFactory {

    private StripedLockFactory() {
    }

    /**
     * Creates and returns a ResourceLock according to the specified type.
     *
     * <p>Note: this factory returns a striped lock implementation based on Guava {@code Striped}. It provides
     * best-effort parallelism: different keys may map to the same stripe.</p>
     *
     * @param type           type of striped lock
     * @param stripes        Minimum number of stripes. See the documentation {@link com.google.common.util.concurrent.Striped}
     * @param defaultTimeout the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @return StripedLock instance
     */
    public static ResourceLock of(StripedLockType type, int stripes, Duration defaultTimeout) {
        // Delegates to the legacy type for now to preserve binary compatibility.
        // The legacy interface is deprecated but the underlying implementation is still used internally.
        @SuppressWarnings("deprecation")
        StripedLock lock = StripedLock.of(type, stripes, defaultTimeout);
        return lock;
    }

    /**
     * Creates and returns a ResourceLock according to the {@code StripedLockType.LOCK} type.
     *
     * @param stripes        Minimum number of stripes. See the documentation {@link com.google.common.util.concurrent.Striped}
     * @param defaultTimeout the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @return StripedLock instance
     * @see #of(StripedLockType, int, Duration)
     */
    public static ResourceLock of(int stripes, Duration defaultTimeout) {
        return of(StripedLockType.LOCK, stripes, defaultTimeout);
    }
}