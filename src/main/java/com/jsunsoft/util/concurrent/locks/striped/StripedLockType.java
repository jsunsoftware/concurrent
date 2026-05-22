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
     * Lock type with strongly referenced locks. See {@link com.google.common.util.concurrent.Striped#lock(int)}.
     */
    LOCK,

    /**
     * Lock type with lazily initialised, weakly referenced locks. See
     * {@link com.google.common.util.concurrent.Striped#lazyWeakLock(int)}.
     *
     * <p><b>Discouraged.</b> This mode has a known correctness hazard that the library does not mitigate.
     * Under GC pressure, the manual {@code lock(key)} / {@code unlock(key)} pair and the single-key lambda
     * variant ({@code lock(key, () -> ...)}) may release a different {@code Lock} instance than was acquired,
     * throwing {@link IllegalMonitorStateException} and silently breaking mutual exclusion in the window
     * before GC clears the weak reference. The multi-key lambda variant ({@code lock(List.of(...), () -> ...)})
     * is the only call pattern that is safe under this mode &mdash; the implementation accumulates the resolved
     * locks into a list and captures it in the unlock closure, keeping a strong reference for the duration of
     * the critical section.</p>
     *
     * <p>If you need the memory profile of weak-referenced locks (very large stripe counts where eager
     * allocation is too expensive) and your code uses <i>only</i> the multi-key lambda API, suppress the
     * deprecation warning at the call site with {@code @SuppressWarnings("deprecation")}.</p>
     *
     * @deprecated Discouraged; see above. Prefer {@link #LOCK} unless your code is restricted to the safe
     *             multi-key lambda call pattern documented above.
     */
    @Deprecated
    LAZY_WEAK_LOCK
}
