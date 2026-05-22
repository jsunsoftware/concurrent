package com.jsunsoft.util.concurrent.locks.benchmark;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Manual JMH runner. Tagged {@code "benchmark"} so it is <b>excluded from {@code mvn test}</b> by default
 * (see {@code maven-surefire-plugin} configuration in {@code pom.xml}).
 *
 * <h2>Canonical: run from the Maven CLI</h2>
 *
 * <pre>{@code
 *   mvn test -Pbenchmark                                # full run with the defaults below
 *   mvn test -Pbenchmark -Dtest=BenchmarkRunnerTest
 *   mvn test -Pbenchmark -Dbenchmark.threads=4          # exercise real contention
 * }</pre>
 *
 * <p>The CLI is the canonical channel because forked benchmark JVMs run with a clean command line,
 * uncontaminated by anything the launching process may have injected.</p>
 *
 * <h2>Running from an IDE</h2>
 *
 * <p>IDEs (IntelliJ, Eclipse, VS Code) attach instrumentation agents to test JVMs &mdash; for
 * debugging, hot-swap, coroutine tracing, etc. By default JMH's forked benchmark JVMs <b>inherit
 * the parent's command line</b>, which means these agents come along for the ride. Two problems
 * follow:</p>
 *
 * <ol>
 *   <li>Cosmetic noise: agents typically need files (e.g. {@code /tmp/capture*.props}) that exist
 *   only in the parent process tree, so each fork dumps an initialisation stack trace into the
 *   benchmark log.</li>
 *   <li>Measurement integrity: an instrumentation agent in the forked JVM changes JIT decisions
 *   (inlining, deoptimisation, native compilation thresholds) and inflates baseline costs.</li>
 * </ol>
 *
 * <p>This runner mitigates both by explicitly setting the forked JVM args via
 * {@link OptionsBuilder#jvmArgs(String...)} below, which <i>replaces</i> the inherited list with a
 * clean minimal one. If you need different JVM flags for a benchmark (e.g. a specific GC), edit
 * that line.</p>
 */
@Tag("benchmark")
class BenchmarkRunnerTest {

    @Test
    @Disabled
    void runBenchmarks() throws RunnerException {
        // Thread count is configurable via -Dbenchmark.threads=N (default: 1).
        // 1 thread measures pure throughput / wrapper overhead; N > 1 measures contention.
        int threads = Integer.getInteger("benchmark.threads", 1);

        Options opts = new OptionsBuilder()
                .include(ResourceLockBenchmark.class.getSimpleName())
                .forks(1)
                .threads(threads)
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                // Replace inherited JVM args (which on IDE runs include -javaagent flags for debug
                // / hot-swap / coroutine tracing) with a clean minimal set. JMH still passes the
                // classpath separately, so we don't lose that.
                .jvmArgs("-ea")
                .build();
        new Runner(opts).run();
    }
}
