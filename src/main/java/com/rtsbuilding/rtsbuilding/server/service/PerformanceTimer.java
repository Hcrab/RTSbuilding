package com.rtsbuilding.rtsbuilding.server.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple nanoTime-based performance timer for benchmarking storage operations.
 * <p>
 * Collects samples and reports min/avg/max in microseconds.
 */
public final class PerformanceTimer {

    private final String name;
    private long startNanos;
    private long totalNanos;
    private long minNanos = Long.MAX_VALUE;
    private long maxNanos = Long.MIN_VALUE;
    private int samples;

    private PerformanceTimer(String name) {
        this.name = name;
    }

    /** Creates a new timer and starts it immediately. */
    public static PerformanceTimer start(String name) {
        PerformanceTimer t = new PerformanceTimer(name);
        t.startNanos = System.nanoTime();
        return t;
    }

    /**
     * Stops the current lap and records it as a sample.
     * The timer auto-restarts for the next lap.
     */
    public void lap() {
        long now = System.nanoTime();
        long elapsed = now - this.startNanos;
        this.totalNanos += elapsed;
        if (elapsed < this.minNanos) this.minNanos = elapsed;
        if (elapsed > this.maxNanos) this.maxNanos = elapsed;
        this.samples++;
        this.startNanos = now; // auto-restart
    }

    /**
     * Stops and records the current sample without auto-restart.
     * Call {@link #start(String)} to resume.
     */
    public void stop() {
        long now = System.nanoTime();
        long elapsed = now - this.startNanos;
        this.totalNanos += elapsed;
        if (elapsed < this.minNanos) this.minNanos = elapsed;
        if (elapsed > this.maxNanos) this.maxNanos = elapsed;
        this.samples++;
    }

    /** Returns the name of this timer. */
    public String name() {
        return this.name;
    }

    /** Returns the number of recorded samples. */
    public int samples() {
        return this.samples;
    }

    /** Returns average lap time in microseconds. */
    public double avgMicros() {
        return this.samples == 0 ? 0 : (double) this.totalNanos / this.samples / 1000.0;
    }

    /** Returns minimum lap time in microseconds. */
    public double minMicros() {
        return this.samples == 0 ? 0 : (double) this.minNanos / 1000.0;
    }

    /** Returns maximum lap time in microseconds. */
    public double maxMicros() {
        return this.samples == 0 ? 0 : (double) this.maxNanos / 1000.0;
    }

    /** Returns total elapsed time in microseconds. */
    public long totalMicros() {
        return this.totalNanos / 1000;
    }

    /**
     * Formats a one-line summary for this timer.
     */
    public String summary() {
        if (this.samples == 0) return String.format("  §7%s: (no samples)", this.name);
        return String.format("  §e%s: §6%d samples  §aavg=%.3fms  §7min=%.3fms  §cmax=%.3fms",
                this.name, this.samples, this.avgMicros() / 1000.0,
                this.minMicros() / 1000.0, this.maxMicros() / 1000.0);
    }

    /**
     * Formats a compact single-line summary.
     */
    public String compact() {
        if (this.samples == 0) return String.format("§7%s: N/A", this.name);
        return String.format("%s: §aavg=%.3fms  §cpeak=%.3fms  §7(n=%d)",
                this.name, this.avgMicros() / 1000.0, this.maxMicros() / 1000.0, this.samples);
    }

    /**
     * Runs a benchmark function {@code iterations} times, timing each
     * execution with a fresh timer and reporting results.
     */
    public static <T> PerformanceTimer benchmark(String name, int iterations, Runnable action) {
        // Warmup: let JIT compile
        for (int i = 0; i < Math.min(iterations, 100); i++) {
            action.run();
        }

        PerformanceTimer timer = new PerformanceTimer(name);
        timer.startNanos = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            action.run();
            timer.lap(); // records current lap, auto-restarts
        }
        timer.stop();
        return timer;
    }

    /**
     * Runs a benchmark with variable size, producing a list of results.
     * Each entry in {@code sizes} gets {@code iterations} runs.
     */
    public static List<PerformanceTimer> benchmarkScale(String baseName, int[] sizes,
                                                         int iterationsPerSize,
                                                         java.util.function.IntConsumer setup,
                                                         Runnable action,
                                                         java.util.function.IntConsumer teardown) {
        List<PerformanceTimer> results = new ArrayList<>();
        for (int size : sizes) {
            if (setup != null) setup.accept(size);
            results.add(benchmark(baseName + "[" + size + "]", iterationsPerSize, action));
            if (teardown != null) teardown.accept(size);
        }
        return results;
    }
}
