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

import com.jsunsoft.util.Closure;
import com.jsunsoft.util.Executable;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Interface representing a lock mechanism that can be used to synchronize the execution of code blocks.
 */
public interface ResourceLock {

    /**
     * Uses the default {@code timeout}.
     *
     * @param resource   resource to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Object, Executable)} if thread can be interrupted.
     * @see #lock(Object, Duration, Executable)
     */
    <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resource resource to lock
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Object, Closure)} if thread can be interrupted.
     */
    <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X;

    /**
     * @param resource   resource to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Object, Executable)} if thread can be interrupted.
     */
    <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X;

    /**
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Object, Closure)} if thread can be interrupted.
     */
    <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resources  collection of resources to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Collection, Executable)} if thread can be interrupted.
     * @see #lock(Collection, Duration, Executable)
     */
    <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X;

    /**
     * Uses the default {@code timeout}.
     *
     * @param resources collection of resources to lock
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Collection, Closure)} if thread can be interrupted.
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X;

    /**
     * @param resources  collection of resources to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Collection, Executable)} if thread can be interrupted.
     */
    <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X;

    /**
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws X                     Custom exception which can be thrown from method execute.
     * @throws IllegalStateException If thread was interrupted.
     *                               Use the method {@link #lockInterruptibly(Collection, Closure)} if thread can be interrupted.
     */
    <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X;

    /**
     * Difference between the {@link #lock(Object, Executable)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resource   resource to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lockInterruptibly(Collection, Duration, Executable)
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Closure)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resource resource to lock
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Executable)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resource   resource to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Object, Closure)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback Callback which execution will be synchronized by resource.
     *                 The execute method will be called in synchronized block
     * @param <R>      Return type of the callback.
     * @param <X>      Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Executable)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout}.
     *
     * @param resources  collection of resources to lock
     * @param executable Mainly lambda expression which execution will be synchronized by resource.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     * @see #lockInterruptibly(Collection, Duration, Executable)
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Closure)} that this method throws InterruptedException when thread is interrupted.
     * Uses the default {@code timeout} as maximum time to wait for each resource lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}.
     *
     * @param resources collection of resources to lock
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     *                              (and interruption of lock acquisition is supported)
     * @throws X                    Custom exception which can be thrown from method execute.
     * @throws LockAcquireException Unable to acquire lock when the maximum time to wait for the lock is expired
     */
    <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Executable)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resources  collection of resources to lock
     * @param timeout    the maximum time to wait for the lock. See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param executable Mainly lambda expression which execution will be synchronized by resources.
     *                   The execute method will be called in synchronized block
     * @param <X>        Custom exception type which can be thrown from method execute.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws X                    Custom exception which can be thrown from method execute.
     */
    <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X;

    /**
     * Difference between the {@link #lock(Collection, Closure)} that this method throws InterruptedException when thread is interrupted.
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for each resource lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @param callback  Callback which execution will be synchronized by resources.
     *                  The execute method will be called in synchronized block
     * @param <R>       Return type of the callback.
     * @param <X>       Custom exception type which can be thrown from method execute.
     * @return The result of the callback execution.
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws X                    Custom exception which can be thrown from method execute.
     */
    <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X;

    /**
     * Locks the given resource.
     *
     * @param resource resource to lock
     */
    void lock(Object resource);

    /**
     * Locks the given resource for a specified time.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock
     */
    void lock(Object resource, Duration timeout);

    /**
     * Locks the given collection of resources.
     *
     * @param resources collection of resources to lock
     */
    void lock(Collection<?> resources);

    /**
     * Locks the given collection of resources for a specified time.
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for the lock
     */
    void lock(Collection<?> resources, Duration timeout);

    /**
     * Locks the given resource interruptibly.
     *
     * @param resource resource to lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Object resource) throws InterruptedException;

    /**
     * Locks the given resource interruptibly for a specified time.
     *
     * @param resource resource to lock
     * @param timeout  the maximum time to wait for the lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Object resource, Duration timeout) throws InterruptedException;

    /**
     * Locks the given collection of resources interruptibly.
     *
     * @param resources collection of resources to lock
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     */
    void lockInterruptibly(Collection<?> resources) throws InterruptedException;

    /**
     * Locks the given collection of resources interruptibly for a specified time.
     * Note that the method will unlock all resources if any of them cannot be locked within the specified timeout or any exception is thrown.
     *
     * @param resources collection of resources to lock
     * @param timeout   the maximum time to wait for the lock. Worst case is (n × timeout) See {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)}
     * @throws InterruptedException if the current thread is interrupted while acquiring the lock
     * @throws LockAcquireException if unable to acquire lock when the maximum time to wait for the lock is expired
     */
    void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException;

    /**
     * Unlocks the given resource.
     *
     * @param resource resource to unlock
     */
    void unlock(Object resource);

    /**
     * Unlocks the given collection of resources.
     *
     * @param resources collection of resources to unlock
     */
    void unlock(Collection<?> resources);
}
