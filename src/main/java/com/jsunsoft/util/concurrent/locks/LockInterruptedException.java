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

import static java.util.Objects.requireNonNull;

/**
 * Thrown by the non-interruptible {@code lock(...)} family when the calling thread is interrupted while waiting
 * to acquire the lock.
 *
 * <p>The thread's interrupted flag is re-set before this exception is thrown, so higher layers can detect the
 * interrupt with {@code Thread.currentThread().isInterrupted()}.</p>
 *
 * <p>Catch this separately from {@link LockAcquireException} to distinguish a graceful shutdown signal (interrupt)
 * from a timeout:</p>
 *
 * <pre>{@code
 * try {
 *     lock.lock(key, () -> work());
 * } catch (LockAcquireException e) {
 *     // timeout
 * } catch (LockInterruptedException e) {
 *     // shutdown signal — propagate or finish gracefully
 * }
 * }</pre>
 *
 * <p>For callers that explicitly want to handle interrupts, prefer the {@code lockInterruptibly(...)} variants —
 * those throw checked {@link InterruptedException} directly and never wrap it in this runtime exception.</p>
 *
 * @since 2.2.0
 */
public class LockInterruptedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LockInterruptedException(String message, InterruptedException cause) {
        super(message, requireNonNull(cause, "cause"));
    }

    /**
     * Returns the original {@link InterruptedException} that triggered this exception.
     *
     * @return the original interrupt
     */
    @Override
    public synchronized InterruptedException getCause() {
        return (InterruptedException) super.getCause();
    }
}
