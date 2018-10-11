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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class StripedLockFactory {

    private StripedLockFactory() {
    }

    /**
     * Creates and return Lock  according to type
     *
     * @param type        type of striped lock
     * @param stripes     Minimum number of stripes. See the documentation {@link com.google.common.util.concurrent.Striped}
     * @param lockTimeSec the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @return Lock instance
     */
    public static Lock createLock(StripedLockType type, int stripes, int lockTimeSec) {
        Objects.requireNonNull(type, "parameter 'type' may not be null");

        Lock result;
        switch (type) {
            case LOCK:
                result = new StripedLock(stripes, lockTimeSec);
                break;
            case LAZY_WEAK_LOCK:
                result = new StripedLazyWeakLock(stripes, lockTimeSec);
                break;
            default:
                throw new IllegalStateException("Unhandled type: " + type);
        }
        return result;
    }
}
