package datingapp.app.bootstrap;

import datingapp.core.metrics.ActivityMetricsService;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);

    private final Duration interval;
    private final Supplier<ActivityMetricsService.CleanupResult> cleanupTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

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
        long everyMillis = Math.max(1L, interval.toMillis());
        executor.scheduleAtFixedRate(this::runCleanupSafely, everyMillis, everyMillis, TimeUnit.MILLISECONDS);
        running.set(true);
        logInfo("Cleanup scheduler started (interval={}ms)", everyMillis);
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
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

    private void runCleanupSafely() {
        try {
            ActivityMetricsService.CleanupResult result = cleanupTask.get();
            if (result != null && result.hadWork()) {
                logInfo("Retention cleanup removed {} records ({})", result.totalDeleted(), result);
            }
        } catch (Exception e) {
            logWarn("Retention cleanup run failed", e);
        }
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
