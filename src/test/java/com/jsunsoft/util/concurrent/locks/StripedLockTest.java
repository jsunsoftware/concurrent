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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StripedLockTest {

    @Test
    void test() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);

        Res res = new Res();

        Collection<Thread> threads = Stream
                .generate(() -> (Runnable) () ->
                        lock.lock(res.id, () -> {
                            if (res.version == 0) {
                                try {
                                    Thread.sleep(1000); //for demonstrate that threads can enter here if lock not acquired
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                res.version++;
                            }
                        })
                )
                .limit(5)
                .map(Thread::new)
                .collect(Collectors.toSet());

        threads.forEach(Thread::start);

        for (Thread thread : threads) {
            thread.join();
        }
        Assertions.assertEquals(1, res.version);
    }

    @Test
    void lockSingleResource() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);
        Res res = new Res();

        lock.lock(res.id, () -> res.version++);

        Assertions.assertEquals(1, res.version);
    }

    @Test
    void lockMultipleResources() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);
        Res res1 = new Res();
        Res res2 = new Res();

        lock.lock(Stream.of(res1.id, res2.id).collect(Collectors.toList()), () -> {
            res1.version++;
            res2.version++;
        });

        Assertions.assertEquals(1, res1.version);
        Assertions.assertEquals(1, res2.version);
    }

    @Test
    void lockWithTimeout() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 1);
        Res res = new Res();

        lock.lock(res.id, 1, () -> res.version++);

        Assertions.assertEquals(1, res.version);
    }

    @Test
    void lockInterruptibly() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);
        Res res = new Res();

        Thread thread = new Thread(() -> {
            try {
                lock.lockInterruptibly(res.id, () -> res.version++);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread.start();
        thread.join();

        Assertions.assertEquals(1, res.version);
    }

    @Test
    void lockInterruptiblyWithTimeout() throws InterruptedException {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 1);
        Res res = new Res();

        Thread thread = new Thread(() -> {
            try {
                lock.lockInterruptibly(res.id, 1, () -> res.version++);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread.start();
        thread.join();

        Assertions.assertEquals(1, res.version);
    }

    @Test
    void unlockSingleResource() {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);
        Res res = new Res();

        lock.lock(res.id);
        lock.unlock(res.id);

        Assertions.assertDoesNotThrow(() -> lock.lock(res.id, () -> res.version++));
    }

    @Test
    void unlockMultipleResources() {
        Lock lock = StripedLockFactory.createLock(StripedLockType.LOCK, 5, 30);
        Res res1 = new Res();
        Res res2 = new Res();

        lock.lock(Stream.of(res1.id, res2.id).collect(Collectors.toList()));
        lock.unlock(Stream.of(res1.id, res2.id).collect(Collectors.toList()));

        Assertions.assertDoesNotThrow(() -> lock.lock(Stream.of(res1.id, res2.id).collect(Collectors.toList()), () -> {
            res1.version++;
            res2.version++;
        }));
    }


    private static class Res {
        int id;
        int version;
    }
}
