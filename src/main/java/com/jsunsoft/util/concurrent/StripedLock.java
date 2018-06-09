package com.jsunsoft.util.concurrent;

/* Copyrights 2015 FIP Software
 * Date: 6/9/18.
 * Developer: Beno Arakelyan
 * This software is the proprietary information of FIP Software.
 * Its use is subject to License terms.
 */

import com.google.common.util.concurrent.Striped;
import com.jsunsoft.util.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class StripedLock implements Lock {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripedLock.class);

    private final int lockTimeSec;
    private final Striped<java.util.concurrent.locks.Lock> striped;

    public StripedLock(int minimumNumberOfStripes, int lockTimeSec) {
        if (lockTimeSec <= 0) {
            throw new IllegalArgumentException("Parameter [lockTimeSec] must be positive. Current value: ]" + lockTimeSec + ']');
        }

        this.lockTimeSec = lockTimeSec;
        this.striped = Striped.lock(minimumNumberOfStripes);
    }

    @Override
    public <X extends Throwable> void lock(Object resource, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(resource, "parameter 'resources' must not be null");
        lock(Collections.singleton(resource), executable);
    }

    @Override
    public <X extends Throwable> void lock(Collection<?> resources, Executable<X> executable) throws InterruptedException, X {
        requireNonNull(resources, "parameter 'resources' must not be null");
        requireNonNull(executable, "parameter 'executable' must not be null");

        if (resources.stream().allMatch(Objects::nonNull)) {
            List<Object> lockedResources = new ArrayList<>();
            try {
                for (Object resource : resources) {
                    if (striped.get(resource).tryLock(lockTimeSec, TimeUnit.SECONDS)) {

                        lockedResources.add(resource);
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("The resource: [{}] has been locked", resource);
                        }
                    } else {
                        throw new LockAcquireException("Unable to acquire lock within [" + lockTimeSec + "] seconds for [" + resource + ']');
                    }
                }
                executable.execute();
            } finally {
                lockedResources.forEach(resource -> {
                    striped.get(resource).unlock();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("The resource [{}] has been unlocked", resource);
                    }
                });
            }
        } else {
            throw new IllegalArgumentException("Unable to initiate lock on null object.  resources: " + resources);
        }
    }
}
