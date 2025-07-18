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

public enum StripedLockType {
    /**
     * Lock type with strongly referenced locks. See {@link com.google.common.util.concurrent.Striped#lock(int)}
     */
    LOCK,

    /**
     * Lock type with lazily initialized, weakly referenced locks. See {@link com.google.common.util.concurrent.Striped#lazyWeakLock(int)}
     */
    LAZY_WEAK_LOCK
}
