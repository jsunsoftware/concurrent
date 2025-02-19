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

public class LockAcquireException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Object resource;
    private final Duration timeout;

    LockAcquireException(String message, Object resource, Duration timeout) {
        super(message);
        this.resource = resource;
        this.timeout = timeout;
    }

    public Object getResource() {
        return resource;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
