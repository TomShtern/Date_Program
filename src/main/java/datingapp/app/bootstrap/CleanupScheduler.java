package datingapp.app.bootstrap;

import datingapp.core.metrics.ActivityMetricsService;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically runs retention cleanup routines in the background.
 *
 * <p>Designed as a small lifecycle component so startup wiring can schedule cleanup
 * without leaking thread-management details into entry points.
 */
public final class CleanupScheduler {

    public static record CleanupStatus(
            boolean running,
            long totalRuns,
            long successfulRuns,
            long failedRuns,
            long consecutiveFailures,
            Duration nextDelay,
            Throwable lastFailure) {
        public Optional<Throwable> lastFailureOptional() {
            return Optional.ofNullable(lastFailure);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);
    private static final Duration MIN_BACKOFF_BASE = Duration.ofMillis(1);
    private static final Duration MAX_BACKOFF_BASE = Duration.ofMinutes(1);

    private final Duration interval;
    private final Supplier<ActivityMetricsService.CleanupResult> cleanupTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalRuns = new AtomicLong(0);
    private final AtomicLong successfulRuns = new AtomicLong(0);
    private final AtomicLong failedRuns = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private final AtomicReference<Duration> nextDelay = new AtomicReference<>(Duration.ZERO);
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledFuture;

    public CleanupScheduler(Duration interval, Supplier<ActivityMetricsService.CleanupResult> cleanupTask) {
        this.interval = Objects.requireNonNull(interval, "interval cannot be null");
        this.cleanupTask = Objects.requireNonNull(cleanupTask, "cleanupTask cannot be null");
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "datingapp-cleanup-scheduler");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadScheduledExecutor(factory);
        running.set(true);
        scheduleNextRun(interval);
        logInfo("Cleanup scheduler started (interval={}ms)", Math.max(1L, interval.toMillis()));
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        running.set(false);
        logInfo("Cleanup scheduler stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public CleanupStatus snapshot() {
        return new CleanupStatus(
                running.get(),
                totalRuns.get(),
                successfulRuns.get(),
                failedRuns.get(),
                consecutiveFailures.get(),
                nextDelay.get(),
                lastFailure.get());
    }

    Duration runCleanupOnce() {
        return executeCleanupCycle();
    }

    private void runCleanupSafely() {
        if (!running.get()) {
            return;
        }
        Duration delay = executeCleanupCycle();
        scheduleNextRun(delay);
    }

    private Duration executeCleanupCycle() {
        totalRuns.incrementAndGet();
        try {
            ActivityMetricsService.CleanupResult result = cleanupTask.get();
            successfulRuns.incrementAndGet();
            consecutiveFailures.set(0);
            if (result != null && result.hadWork()) {
                logInfo("Retention cleanup removed {} records ({})", result.totalDeleted(), result);
            }
            Duration delay = interval;
            nextDelay.set(delay);
            return delay;
        } catch (Exception e) {
            failedRuns.incrementAndGet();
            long failures = consecutiveFailures.incrementAndGet();
            lastFailure.set(e);
            Duration delay = computeRetryDelay(failures);
            nextDelay.set(delay);
            logWarn("Retention cleanup run failed; retrying in {}ms", delay.toMillis(), e);
            return delay;
        }
    }

    private void scheduleNextRun(Duration delay) {
        if (!running.get() || executor == null) {
            return;
        }
        long delayMillis = Math.max(1L, delay.toMillis());
        nextDelay.set(Duration.ofMillis(delayMillis));
        scheduledFuture = executor.schedule(this::runCleanupSafely, delayMillis, TimeUnit.MILLISECONDS);
    }

    private Duration computeRetryDelay(long failureCount) {
        long intervalMillis = Math.max(1L, interval.toMillis());
        long baseMillis = Math.clamp(intervalMillis / 4L, MIN_BACKOFF_BASE.toMillis(), MAX_BACKOFF_BASE.toMillis());
        long delayMillis = baseMillis;
        for (long attempt = 1L; attempt < failureCount; attempt++) {
            delayMillis = Math.clamp(delayMillis * 2L, baseMillis, intervalMillis);
        }
        return Duration.ofMillis(Math.clamp(delayMillis, baseMillis, intervalMillis));
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }
}
