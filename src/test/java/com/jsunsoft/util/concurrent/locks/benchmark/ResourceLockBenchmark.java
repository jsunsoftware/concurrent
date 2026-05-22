package com.jsunsoft.util.concurrent.locks.benchmark;

import com.google.common.util.concurrent.Striped;
import com.jsunsoft.util.concurrent.locks.ResourceLock;
import com.jsunsoft.util.concurrent.locks.striped.StripedLockFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Microbenchmarks for the {@code ResourceLock} family.
 *
 * <p>Three call shapes are measured against three alternative implementations of the same protected-section idiom:</p>
 *
 * <ul>
 *   <li><b>{@code resourceLock_*}</b> &mdash; this library's lambda API ({@code lock.lock(key, () -> work())}).</li>
 *   <li><b>{@code rawStriped_*}</b> &mdash; Guava {@code Striped<Lock>} directly + manual try/finally.</li>
 *   <li><b>{@code chmReentrant_*}</b> &mdash; DIY {@code ConcurrentHashMap<Key, ReentrantLock>} + manual try/finally.</li>
 *   <li><b>{@code plainReentrant_*}</b> &mdash; baseline: a single {@link ReentrantLock}, no per-key indirection.</li>
 * </ul>
 *
 * <p>To run, see {@link BenchmarkRunnerTest}. Default invocation from the project root:</p>
 *
 * <pre>{@code mvn test -Dgroups=benchmark -Dtest=BenchmarkRunnerTest }</pre>
 *
 * <p>Use the {@code work} {@code @Param} to model how heavy the protected section is. With {@code work=0} you are
 * measuring pure wrapper overhead; with {@code work=50} you are measuring the realistic case where the lock itself
 * is a tiny fraction of the cost.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResourceLockBenchmark {

    /**
     * Number of arithmetic ops inside the protected section. 0 = pure wrapper overhead.
     */
    @Param({"0", "50"})
    public int work;

    @Param({"16"})
    public int stripes;

    // Single-key contended: every thread tries to acquire the same key.
    static final String CONTENDED_KEY = "K";

    private ResourceLock libLock;
    private Striped<Lock> guavaStriped;
    private ConcurrentMap<Object, ReentrantLock> chmLocks;
    private ReentrantLock plainLock;

    // Reusable multi-key input for the multi-key benchmark.
    private static final List<String> MULTI_KEYS = Arrays.asList("A", "B", "C");

    // Pool of distinct keys for the diverse-keys benchmarks. Sized to match the default stripe
    // count so each key (with high probability) hashes to a different stripe; under -Dbenchmark.threads=N
    // this lets the striped backends scale near-linearly while a single-lock baseline serialises.
    // Length is 16 (== default stripes); modulo arithmetic uses bitmask, so changing this length
    // requires updating KEY_MASK below.
    private static final String[] DIVERSE_KEYS = new String[16];
    private static final int KEY_MASK = DIVERSE_KEYS.length - 1;

    static {
        for (int i = 0; i < DIVERSE_KEYS.length; i++) {
            DIVERSE_KEYS[i] = "DK-" + i;
        }
    }

    /**
     * Per-thread cursor that advances through {@link #DIVERSE_KEYS}.
     */
    @State(Scope.Thread)
    public static class KeyCursor {
        int idx;
    }

    @Setup
    public void setUp() {
        // Long timeout so we are measuring fast paths, not timeout handling.
        libLock = StripedLockFactory.of(stripes, Duration.ofSeconds(10));
        guavaStriped = Striped.lock(stripes);
        chmLocks = new ConcurrentHashMap<>();
        plainLock = new ReentrantLock();
    }

    // ------------------------------------------------------------------------
    // Uncontended single-key
    // ------------------------------------------------------------------------

    @Benchmark
    public void resourceLock_uncontended(Blackhole bh) {
        libLock.lock(CONTENDED_KEY, () -> bh.consume(doWork()));
    }

    @Benchmark
    public void rawStriped_uncontended(Blackhole bh) {
        Lock l = guavaStriped.get(CONTENDED_KEY);
        l.lock();
        try {
            bh.consume(doWork());
        } finally {
            l.unlock();
        }
    }

    @Benchmark
    public void chmReentrant_uncontended(Blackhole bh) {
        Lock l = chmLocks.computeIfAbsent(CONTENDED_KEY, k -> new ReentrantLock());
        l.lock();
        try {
            bh.consume(doWork());
        } finally {
            l.unlock();
        }
    }

    @Benchmark
    public void plainReentrant_uncontended(Blackhole bh) {
        plainLock.lock();
        try {
            bh.consume(doWork());
        } finally {
            plainLock.unlock();
        }
    }

    // ------------------------------------------------------------------------
    // Single-key CONTENDED: run with `-t N` to see contention
    // ------------------------------------------------------------------------

    @Benchmark
    public void resourceLock_contended_single(Blackhole bh) {
        libLock.lock(CONTENDED_KEY, () -> bh.consume(doWork()));
    }

    @Benchmark
    public void rawStriped_contended_single(Blackhole bh) {
        Lock l = guavaStriped.get(CONTENDED_KEY);
        l.lock();
        try {
            bh.consume(doWork());
        } finally {
            l.unlock();
        }
    }

    // ------------------------------------------------------------------------
    // Multi-key
    // ------------------------------------------------------------------------

    @Benchmark
    public void resourceLock_multiKey(Blackhole bh) {
        libLock.lock(MULTI_KEYS, () -> bh.consume(doWork()));
    }

    @Benchmark
    public void rawStriped_multiKey(Blackhole bh) {
        Iterable<Lock> locks = guavaStriped.bulkGet(MULTI_KEYS);
        List<Lock> acquired = new ArrayList<>(MULTI_KEYS.size());
        try {
            for (Lock l : locks) {
                l.lock();
                acquired.add(l);
            }
            bh.consume(doWork());
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).unlock();
            }
        }
    }

    // ------------------------------------------------------------------------
    // Diverse-keys: each thread cycles through a pool of distinct keys (sized
    // to match the stripe count). At {@code -Dbenchmark.threads=N > 1} this is
    // where Striped's parallelism actually shows up — distinct keys map to
    // distinct stripes, so threads acquire independent locks instead of
    // serialising. The {@code plainReentrant_uncontended} benchmark serves as
    // the "one lock, brutal contention" comparison point and is intentionally
    // not duplicated here.
    // ------------------------------------------------------------------------

    @Benchmark
    public void resourceLock_diverseKeys(KeyCursor cursor, Blackhole bh) {
        String key = DIVERSE_KEYS[cursor.idx++ & KEY_MASK];
        libLock.lock(key, () -> bh.consume(doWork()));
    }

    @Benchmark
    public void rawStriped_diverseKeys(KeyCursor cursor, Blackhole bh) {
        String key = DIVERSE_KEYS[cursor.idx++ & KEY_MASK];
        Lock l = guavaStriped.get(key);
        l.lock();
        try {
            bh.consume(doWork());
        } finally {
            l.unlock();
        }
    }

    @Benchmark
    public void chmReentrant_diverseKeys(KeyCursor cursor, Blackhole bh) {
        String key = DIVERSE_KEYS[cursor.idx++ & KEY_MASK];
        Lock l = chmLocks.computeIfAbsent(key, k -> new ReentrantLock());
        l.lock();
        try {
            bh.consume(doWork());
        } finally {
            l.unlock();
        }
    }

    // ------------------------------------------------------------------------
    // Work model: tight arithmetic loop, result consumed via Blackhole.
    // ------------------------------------------------------------------------
    private long doWork() {
        long r = 0;
        for (int i = 0; i < work; i++) {
            r = r * 31 + i;
        }
        return r;
    }
}
