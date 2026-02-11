package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    private static final Map<String, OperationMetrics> METRICS = new ConcurrentHashMap<>();
    private static final int MAX_METRICS_SIZE = 1000;

    private PerformanceMonitor() {} // Utility class

    /**
     * Starts a timer for an operation. Use with try-with-resources for automatic
     * completion.
     *
     * @param operationName Name of the operation (e.g.,
     *                      "CandidateFinder.findCandidates")
     * @return A Timer that records duration when closed
     */
    public static Timer startTimer(String operationName) {
        return new Timer(operationName, AppClock.now());
    }

    /**
     * Records a completed operation.
     *
     * @param operationName Name of the operation
     * @param durationMs    Duration in milliseconds
     */
    public static void record(String operationName, long durationMs) {
        if (METRICS.size() >= MAX_METRICS_SIZE && !METRICS.containsKey(operationName)) {
            return; // Prevent unbounded growth
        }
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
     * Timer for a single operation. Implements AutoCloseable for
     * try-with-resources.
     */
    public static class Timer implements AutoCloseable {
        private final String operationName;
        private final Instant startTime;
        private boolean success;

        Timer(String operationName, Instant startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }

        public void markSuccess() {
            this.success = true;
        }

        @Override
        public void close() {
            Duration duration = Duration.between(startTime, AppClock.now());
            if (success) {
                record(operationName, duration.toMillis());
            } else {
                record(operationName + ".error", duration.toMillis());
            }

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
            return Duration.between(startTime, AppClock.now()).toMillis();
        }
    }

    /**
     * Aggregated METRICS for a single operation type.
     * All access is synchronized for thread safety (TS-008 simplification).
     */
    public static class OperationMetrics {
        private final String name;
        private long count;
        private long totalMs;
        private long minMs = Long.MAX_VALUE;
        private long maxMs = Long.MIN_VALUE;

        OperationMetrics(String name) {
            this.name = name;
        }

        synchronized void record(long durationMs) {
            count++;
            totalMs += durationMs;
            minMs = Math.min(minMs, durationMs);
            maxMs = Math.max(maxMs, durationMs);
        }

        public synchronized String getName() {
            return name;
        }

        public synchronized long getCount() {
            return count;
        }

        public synchronized long getTotalMs() {
            return totalMs;
        }

        public synchronized long getAverageMs() {
            return count > 0 ? totalMs / count : 0;
        }

        public synchronized long getMinMs() {
            return count > 0 ? minMs : 0;
        }

        public synchronized long getMaxMs() {
            return count > 0 ? maxMs : 0;
        }
    }
}
