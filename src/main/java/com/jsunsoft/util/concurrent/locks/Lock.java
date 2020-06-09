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

import com.jsunsoft.util.Command;

import java.util.Collection;

public interface Lock {

    /**
     * @param resource resource to lock
     * @param command  Mainly lambda expression which execution will synchronized by resource.
     *                 The run method will called in synchronized block
     * @param <X>      Custom exception type which can be thrown from method run.
     * @throws X                     Custom exception which can be thrown from method run.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Object, Command)} if thread can be interrupted.
     */
    <X extends Throwable> void lock(Object resource, Command<X> command) throws X;

    /**
     * @param resources collection of resource to lock
     * @param command   mainly lambda expression which execution will synchronized by resources.
     *                  The run method will called in synchronized block
     * @param <X>       Custom exception type which can be thrown from method run.
     * @throws X                     Custom exception which can be thrown from method run.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Collection, Command)} if thread can be interrupted.
     */
    <X extends Throwable> void lock(Collection<?> resources, Command<X> command) throws X;

    /**
     * Difference between the {@link #lock(Object, Command)} that this method throws InterruptedException when thread is interrupted
     *
     * @param resource resource to lock
     * @param command  mainly lambda expression which execution will synchronized by resource.
     *                 The run method will called in synchronized block
     * @param <X>      Custom exception type which can be thrown from method run.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method run.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Command<X> command) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Command)} that this method throws InterruptedException when thread is interrupted
     *
     * @param resources collection of resource to lock
     * @param command   mainly lambda expression which execution will synchronized by resource.
     *                  The run method will called in synchronized block
     * @param <X>       Custom exception type which can be thrown from method run.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method run.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Command<X> command) throws InterruptedException, X;
}
