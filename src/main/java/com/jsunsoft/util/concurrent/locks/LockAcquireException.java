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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Thrown when a {@link ResourceLock} cannot be acquired within the specified timeout.
 *
 * <p>The exception carries the requested {@link #getTimeout()} and the {@link #getResources()} involved.</p>
 *
 * <p>{@link #getResources()} returns an <b>immutable</b> snapshot of the keys whose acquisition failed.
 * For single-key throw sites the snapshot has one element; for multi-key throw sites the snapshot
 * contains each individual key (a defensive copy of the caller's collection).</p>
 */
public class LockAcquireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Collection<Object> resources;
    private final Duration timeout;

    LockAcquireException(String message, Object resource, Duration timeout) {
        super(message);
        this.resources = (resource == null) ? Collections.emptyList() : Collections.singleton(resource);
        this.timeout = timeout;
    }

    /**
     * Multi-key constructor.
     *
     * <p>The parameter type is {@code Collection<?>} (not {@code Collection<Object>}) so that callers passing
     * {@code Collection<?>} reach this overload by ordinary Java method-resolution rules. With
     * {@code Collection<Object>}, generic invariance would silently divert the call to the
     * single-{@link Object} overload and wrap the entire input collection in a {@code Collections.singleton(...)},
     * losing the individual keys.</p>
     *
     * <p>The input is defensively copied and wrapped as unmodifiable so the exception cannot be tampered with after
     * construction.</p>
     */
    LockAcquireException(String message, Collection<?> resources, Duration timeout) {
        super(message);
        this.resources = (resources == null || resources.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableCollection(new ArrayList<>(resources));
        this.timeout = timeout;
    }

    /**
     * Returns an immutable snapshot of the resources whose acquisition failed.
     *
     * <p>For single-key failures the returned collection has size 1. For multi-key failures it contains the
     * individual keys (the caller's collection is defensively copied at construction).</p>
     *
     * <p>Note: the underlying field is {@code transient}, so {@code getResources()} returns {@code null}
     * after deserialisation. A future fix will pre-format and persist the textual form; see the project's
     * deferred-issue list for details.</p>
     */
    public Collection<Object> getResources() {
        return resources;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
