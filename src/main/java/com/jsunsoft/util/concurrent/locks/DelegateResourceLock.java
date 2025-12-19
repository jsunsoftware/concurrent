package com.jsunsoft.util.concurrent.locks;

import com.jsunsoft.util.Closure;
import com.jsunsoft.util.Executable;

import java.time.Duration;
import java.util.Collection;

import static java.util.Objects.requireNonNull;

public class DelegateResourceLock implements ResourceLock {

    private final ResourceLock delegate;

    public DelegateResourceLock(ResourceLock delegate) {
        this.delegate = requireNonNull(delegate, "Parameter [delegate] must not be null");
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X {
        delegate.lock(resource, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X {
        return delegate.lock(resource, callback);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X {
        delegate.lock(resource, timeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X {
        return delegate.lock(resource, timeout, callback);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X {
        delegate.lock(resources, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X {
        return delegate.lock(resources, callback);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X {
        delegate.lock(resources, timeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X {
        return delegate.lock(resources, timeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X {
        delegate.lockInterruptibly(resource, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X {
        return delegate.lockInterruptibly(resource, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        delegate.lockInterruptibly(resource, timeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        return delegate.lockInterruptibly(resource, timeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        delegate.lockInterruptibly(resources, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X {
        return delegate.lockInterruptibly(resources, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        delegate.lockInterruptibly(resources, timeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        return delegate.lockInterruptibly(resources, timeout, callback);
    }

    @Override
    public void lock(Object resource) {
        delegate.lock(resource);
    }

    @Override
    public void lock(Object resource, Duration timeout) {
        delegate.lock(resource, timeout);
    }

    @Override
    public void lock(Collection<?> resources) {
        delegate.lock(resources);
    }

    @Override
    public void lock(Collection<?> resources, Duration timeout) {
        delegate.lock(resources, timeout);
    }

    @Override
    public void lockInterruptibly(Object resource) throws InterruptedException {
        delegate.lockInterruptibly(resource);
    }

    @Override
    public void lockInterruptibly(Object resource, Duration timeout) throws InterruptedException {
        delegate.lockInterruptibly(resource, timeout);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        delegate.lockInterruptibly(resources);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException {
        delegate.lockInterruptibly(resources, timeout);
    }

    @Override
    public void unlock(Object resource) {
        delegate.unlock(resource);
    }

    @Override
    public void unlock(Collection<?> resources) {
        delegate.unlock(resources);
    }
}