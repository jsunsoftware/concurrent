package com.jsunsoft.util.concurrent.locks;

import com.jsunsoft.util.Closure;
import com.jsunsoft.util.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public abstract class AbstractResourceLock implements ResourceLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceLock.class);

    private final Duration defaultTimeout;

    protected AbstractResourceLock(Duration defaultTimeout) {
        validateTimeout(defaultTimeout);

        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws X {
        lock(resource, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Closure<R, X> callback) throws X {
        return lock(resource, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Duration timeout, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resource, timeout, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Object resource, Duration timeout, Closure<R, X> callback) throws X {
        try {
            return lockInterruptibly(resource, timeout, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws X {
        lock(resources, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Closure<R, X> callback) throws X {
        return lock(resources, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Duration timeout, Executable<X> executable) throws X {

        try {
            lockInterruptibly(resources, timeout, executable);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public <R, X extends Throwable> R lock(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws X {

        try {
            return lockInterruptibly(resources, timeout, callback);
        } catch (InterruptedException e) {
            throw interruptAndResolveException(e);
        }
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resource, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Closure<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resource, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Object resource, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resource, timeout, (Closure<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Object resource, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {

        requireNonNull(callback, "Parameter [callback] must not be null");

        R result;
        boolean unlock = true;

        try {
            tryToLock(resource, timeout);
            result = callback.call();
        } catch (LockAcquireException e) {
            unlock = false;

            throw e;
        } finally {
            if (unlock) {
                unlock(resource);
            }
        }

        return result;
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        lockInterruptibly(resources, defaultTimeout, executable);
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Closure<R, X> callback) throws InterruptedException, X {
        return lockInterruptibly(resources, defaultTimeout, callback);
    }

    @Override
    public <X extends Throwable> void lockInterruptibly(Collection<?> resources, Duration timeout, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(executable, "Parameter [executable] must not be null");

        lockInterruptibly(resources, timeout, (Closure<Void, X>) () -> {
            executable.execute();
            return null;
        });
    }

    @Override
    public <R, X extends Throwable> R lockInterruptibly(Collection<?> resources, Duration timeout, Closure<R, X> callback) throws InterruptedException, X {
        requireNonNull(resources, "Parameter [resources] must not be null");
        requireNonNull(callback, "parameter 'callback' must not be null");
        validateTimeout(timeout);
        R result;

        if (resources.stream().allMatch(Objects::nonNull)) {
            List<Object> lockedResources = new ArrayList<>(resources.size());
            Consumer<Object> resourceConsumer = lockedResources::add;

            try {
                for (Object resource : resources) {
                    tryToLock(resource, timeout, resourceConsumer);
                }
                result = callback.call();
            } finally {
                unlock(lockedResources);
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }

        return result;
    }

    @Override
    public void lock(Object resource) {
        lock(resource, defaultTimeout);
    }

    @Override
    public void lock(Object resource, Duration timeout) {

        try {
            tryToLock(resource, timeout);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lock(Collection<?> resources) {
        lock(resources, defaultTimeout);
    }

    @Override
    public void lock(Collection<?> resources, Duration timeout) {

        try {
            lockInterruptibly(resources, timeout);
        } catch (InterruptedException e) {
            handleInterruptException(e);
        }
    }

    @Override
    public void lockInterruptibly(Object resource) throws InterruptedException {
        lockInterruptibly(resource, defaultTimeout);
    }

    @Override
    public void lockInterruptibly(Object resource, Duration timeout) throws InterruptedException {
        tryToLock(resource, timeout);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources) throws InterruptedException {
        lockInterruptibly(resources, defaultTimeout);
    }

    @Override
    public void lockInterruptibly(Collection<?> resources, Duration timeout) throws InterruptedException {
        requireNonNull(resources, "Parameter [resources] must not be null");
        validateTimeout(timeout);

        if (resources.stream().allMatch(Objects::nonNull)) {
            for (Object resource : resources) {
                tryToLock(resource, timeout);
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }
    }

    @Override
    public void unlock(Collection<?> resources) {
        requireNonNull(resources, "Parameter [resources] must not be null");

        if (!resources.isEmpty()) {
            resources
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(this::unlock);
        }
    }

    protected abstract void tryToLock(Object resource, Duration timeout, Consumer<Object> resourceConsumer) throws InterruptedException;

    protected final Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    protected void tryToLock(Object resource, Duration timeout) throws InterruptedException {
        tryToLock(resource, timeout, null);
    }

    protected void handleInterruptException(InterruptedException e) {
        throw interruptAndResolveException(e);
    }

    protected IllegalStateException interruptAndResolveException(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new IllegalStateException("thread was interrupted. Threads which use the lock  method  mustn't be interrupted.", e);
    }

    protected void validateTimeout(Duration timeout) {
        //noinspection ConstantConditions
    }
}
