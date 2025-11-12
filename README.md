# concurrent

Lightweight Java utility that simplifies working with striped locks based on Google Guava’s `Striped`. It provides a
small, expressive API (`ResourceLock`) to synchronize execution by a resource key (single or multiple), with optional
timeouts and interruptible variants.

Internally it uses Guava’s `Striped` to balance contention vs. parallelism. The legacy `StripedLock` interface still
exists but is deprecated since 2.0.2 in favor of the factory that returns `ResourceLock` instances.

Links:

- Guava
  Striped: https://google.github.io/guava/releases/snapshot/api/docs/com/google/common/util/concurrent/Striped.html
- Javadoc (generated in project target): `target/reports/apidocs/index.html`

## Installation

Maven dependency:

```xml

<dependency>
    <groupId>com.jsunsoft.util</groupId>
    <artifactId>concurrent</artifactId>
    <version>2.0.8</version>
</dependency>
```

## Quick start

Most users should create locks via `StripedLockFactory` and use the `ResourceLock` API.

```java
import com.jsunsoft.util.concurrent.locks.ResourceLock;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;

import java.time.Duration;

ResourceLock lock = StripedLockFactory.of(8, Duration.ofSeconds(30)); // 8 stripes, 30s default timeout

String key = "taskA";

lock.lock(key, () -> {
        // the code which must be executed under the lock for key "taskA"
});
```

If your critical section needs to return a value:

```java
import com.jsunsoft.util.concurrent.locks.ResourceLock;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;

import java.time.Duration;

ResourceLock lock = StripedLockFactory.of(8, Duration.ofSeconds(30));

String key = "taskA";

String result = lock.lock(key, () -> {
    // protected code
    return "ok";
});
```

Note: You can throw your custom checked exception from the lambda and handle it outside of the `lock` method. The API is
generic for exceptions.

## Interruptible locking

If the current thread may be interrupted, use the `lockInterruptibly` methods. They throw `InterruptedException` if the
thread is interrupted while waiting.

```java
ResourceLock lock = StripedLockFactory.of(8, Duration.ofSeconds(30));

lock.

lockInterruptibly("taskA",() ->{
        // code executed under lock, can be interrupted
});
```

Timeout-aware and interruptible variants are also available, for example:

```java
lock.lockInterruptibly("taskA",Duration.ofSeconds(5), ()->{
        // try to acquire within 5 seconds or throw LockAcquireException
});
```

## Lock with multiple keys

When you need to acquire multiple locks consistently (to avoid deadlocks) for a collection of keys:

```java
import com.google.common.collect.ImmutableList;

ResourceLock lock = StripedLockFactory.of(8, Duration.ofSeconds(30));

lock.

lock(ImmutableList.of("taskA", "taskB"), ()->{
        // code executed while both keys are locked in a consistent order
        });
```

All `lock*` methods also have overloads returning a value via a callback, and overloads that accept a
`Duration timeout`.

## Choosing striped lock type

You can choose between eager and lazy-weak stripes via `StripedLockType`:

```java
import com.jsunsoft.util.concurrent.locks.ResourceLock;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockType;

ResourceLock lock = StripedLockFactory.of(StripedLockType.LAZY_WEAK_LOCK, 8, Duration.ofSeconds(30));
```

See Javadoc for the difference between `LOCK` and `LAZY_WEAK_LOCK` (mirrors Guava’s `Striped.lock` vs
`Striped.lazyWeakLock`).

## API overview

`ResourceLock` exposes the following groups of methods (simplified):

- `lock(resource, executable)` and `lock(resource, timeout, executable)`
- `lock(resourcesCollection, executable)` and timeout variants
- `lockInterruptibly(...)` counterparts for both single and multiple resources
- All above variants have callback overloads that return a value
- Convenience `lock(...)`/`unlock(...)` methods when you need manual control

If the lock cannot be obtained within the specified timeout, `LockAcquireException` is thrown. For interruptible
variants, `InterruptedException` can be thrown.

## Legacy note

The older `StripedLock` interface is deprecated since 2.0.2 and will be removed in 3.0.0. Prefer `StripedLockFactory`
which returns a `ResourceLock`.

If you still need it for migration purposes:

```java
import com.jsunsoft.util.concurrent.locks.StripedLock; // Deprecated

StripedLock legacy = StripedLock.of(8, Duration.ofSeconds(30));
legacy.

lock("key",() ->{ /* ... */ });
```

## Building locally

This is a standard Maven project. To run tests and build:

```bash
mvn -q -DskipTests=false clean verify
```

## License

Apache License 2.0. See `LICENSE` file.
