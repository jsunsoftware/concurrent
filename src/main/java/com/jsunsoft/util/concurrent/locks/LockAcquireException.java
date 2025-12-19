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
import java.util.Collection;
import java.util.Collections;

/**
 * Thrown when a {@link ResourceLock} cannot be acquired within the specified timeout.
 *
 * <p>The exception carries the requested {@link #getTimeout()} and the {@link #getResources()} involved.</p>
 */
public class LockAcquireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Collection<Object> resources;
    private final Duration timeout;

    LockAcquireException(String message, Object resource, Duration timeout) {
        this(message, resource == null ? Collections.emptyList() : Collections.singleton(resource), timeout);
    }

    LockAcquireException(String message, Collection<Object> resources, Duration timeout) {
        super(message);
        this.resources = resources;
        this.timeout = timeout;
    }

    public Collection<Object> getResources() {
        return resources;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
