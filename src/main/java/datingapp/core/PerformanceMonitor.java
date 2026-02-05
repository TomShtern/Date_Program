package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple performance monitoring utility for tracking operation timings.
 * Thread-safe and designed for minimal overhead.
 *
 * <p>Usage:
 * <pre>
 * try (var timer = PerformanceMonitor.startTimer("CandidateFinder.findCandidates")) {
 *     // ... operation
 * }
 * </pre>
 *
 * <p>METRICS can be queried and logged periodically:
 * <pre>
 * PerformanceMonitor.logMetrics();
 * PerformanceMonitor.reset();
 * </pre>
 */
public final class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    private static final Map<String, OperationMetrics> METRICS = new ConcurrentHashMap<>();

    private PerformanceMonitor() {} // Utility class

    /**
     * Starts a timer for an operation. Use with try-with-resources for automatic completion.
     *
     * @param operationName Name of the operation (e.g., "CandidateFinder.findCandidates")
     * @return A Timer that records duration when closed
     */
    public static Timer startTimer(String operationName) {
        return new Timer(operationName, Instant.now());
    }

    /**
     * Records a completed operation.
     *
     * @param operationName Name of the operation
     * @param durationMs Duration in milliseconds
     */
    public static void record(String operationName, long durationMs) {
        METRICS.computeIfAbsent(operationName, OperationMetrics::new).record(durationMs);
    }

    /**
     * Gets METRICS for an operation.
     *
     * @param operationName The operation name
     * @return METRICS or null if no data recorded
     */
    public static OperationMetrics getMetrics(String operationName) {
        return METRICS.get(operationName);
    }

    /**
     * Logs all current METRICS at INFO level.
     */
    public static void logMetrics() {
        if (METRICS.isEmpty()) {
            logger.info("No performance METRICS recorded");
            return;
        }

        StringBuilder sb = new StringBuilder("\n=== Performance METRICS ===\n");
        METRICS.forEach((name, m) -> {
            sb.append(String.format(
                    "  %s: count=%d, avg=%dms, min=%dms, max=%dms%n",
                    name, m.getCount(), m.getAverageMs(), m.getMinMs(), m.getMaxMs()));
        });
        if (logger.isInfoEnabled()) {
            logger.info(sb.toString());
        }
    }

    /**
     * Logs METRICS at DEBUG level (for frequent logging without noise).
     */
    public static void logMetricsDebug() {
        if (logger.isDebugEnabled()) {
            METRICS.forEach((name, m) -> logger.debug("{}: count={}, avg={}ms", name, m.getCount(), m.getAverageMs()));
        }
    }

    /**
     * Resets all METRICS. Useful for periodic reporting windows.
     */
    public static void reset() {
        METRICS.clear();
    }

    /**
     * Timer for a single operation. Implements AutoCloseable for try-with-resources.
     */
    public static class Timer implements AutoCloseable {
        private final String operationName;
        private final Instant startTime;

        Timer(String operationName, Instant startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }

        @Override
        public void close() {
            Duration duration = Duration.between(startTime, Instant.now());
            record(operationName, duration.toMillis());

            // Log slow operations (>100ms) at DEBUG level
            if (logger.isDebugEnabled() && duration.toMillis() > 100) {
                logger.debug("Slow operation: {} took {}ms", operationName, duration.toMillis());
            }
        }

        /**
         * Gets elapsed time without completing the timer.
         *
         * @return Elapsed milliseconds
         */
        public long elapsedMs() {
            return Duration.between(startTime, Instant.now()).toMillis();
        }
    }

    /**
     * Aggregated METRICS for a single operation type.
     */
    public static class OperationMetrics {
        private final String name;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalMs = new LongAdder();
        private long minMs = Long.MAX_VALUE;
        private long maxMs = Long.MIN_VALUE;

        OperationMetrics(String name) {
            this.name = name;
        }

        synchronized void record(long durationMs) {
            count.increment();
            totalMs.add(durationMs);
            minMs = Math.min(minMs, durationMs);
            maxMs = Math.max(maxMs, durationMs);
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count.sum();
        }

        public long getTotalMs() {
            return totalMs.sum();
        }

        public long getAverageMs() {
            long c = count.sum();
            return c > 0 ? totalMs.sum() / c : 0;
        }

        public long getMinMs() {
            return count.sum() > 0 ? minMs : 0;
        }

        public long getMaxMs() {
            return count.sum() > 0 ? maxMs : 0;
        }
    }
}
