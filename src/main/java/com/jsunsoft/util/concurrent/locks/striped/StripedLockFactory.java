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
// NOTE: the (deprecated) com.jsunsoft.util.concurrent.locks.StripedLock interface is NOT imported.
// Javac issues a deprecation warning at the import token regardless of @SuppressWarnings on the
// class (the annotation does not cover declarations outside the type). Using the fully-qualified
// name inside the method body keeps the warning suppression effective via the class-level
// @SuppressWarnings("deprecation") below.

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// Class-level @SuppressWarnings("deprecation") covers the fully-qualified reference to the
// (now deprecated) StripedLock interface in of(...). The legacy interface is still used
// internally to preserve binary compatibility; the factory's public surface is unchanged.
@SuppressWarnings("deprecation")
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
     * @return ResourceLock instance
     */
    public static ResourceLock of(StripedLockType type, int stripes, Duration defaultTimeout) {
        // Delegates to the legacy type for now to preserve binary compatibility.
        // The legacy interface is deprecated but the underlying implementation is still used internally.
        return com.jsunsoft.util.concurrent.locks.StripedLock.of(type, stripes, defaultTimeout);
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