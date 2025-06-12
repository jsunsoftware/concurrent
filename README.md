# concurrent

This lib is built on [google/guava](https://github.com/google/guava) based on [Striped](https://google.github.io/guava/releases/snapshot/api/docs/com/google/common/util/concurrent/Striped.html).

Main purpose of **concurrent** create clean quick and simple usage of Striped lock.

### How to use

**Synchronize some execution block by some resource(key).**


```java
StripedLock lock = StripedLock.of(8, Duration.ofSeconds(30)); // See the javadoc to params information

String key = "taskA";

lock.lock(key, () -> {
    //the code which must be executed.
});
```

**or if locked statement returns result**

```java
StripedLock lock = StripedLock.of(8, Duration.ofSeconds(30)); // See the javadoc to params information

String key = "taskA";

Object lockResult = lock(key, () -> {
        //the code which must be executed.
        return result;
});
```

Note: **You can throw your custom checked exception from above lambda block and handle it outside of lock method**

**Handel InterruptedException**

If thread can be interrupted you must use the method `lockInterruptibly`. 
This method throws InterruptedException when current thread is interrupted.

```java
StripedLock lock = StripedLock.of(8, Duration.ofSeconds(30)); // See the javadoc to params information

String key = "taskA";

lock.lockInterruptibly(key, () -> {
    //the code which must be executed.
});
```

**Lock with various keys**

When you have some keys collection to lock some block you can do following:

```java
StripedLock lock = StripedLock.of(8, Duration.ofSeconds(30)); // See the javadoc to params information

Collection<String> keys = ImmutableList.of("taskA", "taskB");

lock.lock(keys, () -> {
    //the code which must be executed.
});
```

**Create lazy weak StripedLock instance**

```java
StripedLock lock = StripedLock.of(StripedLockType.LAZY_WEAK_LOCK, 8, Duration.ofSeconds(30)); // See the javadoc to params information
```

For difference between StripedLockType.LOCK and StripedLockType.LAZY_WEAK_LOCK you can see javadoc.

**To use from maven add this snippet to the pom.xml `dependencies` section:**

```xml

<dependency>
    <groupId>com.jsunsoft.util</groupId>
    <artifactId>concurrent</artifactId>
    <version>2.0.3</version>
</dependency>
```
