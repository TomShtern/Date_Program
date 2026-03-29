package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import datingapp.core.metrics.ActivityMetricsService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("CleanupScheduler retry and failure accounting")
class CleanupSchedulerBehaviorTest {

    @Test
    @DisplayName("cleanup failures should be counted and retry with deterministic backoff")
    void failuresAreCountedAndRetriedWithBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        CleanupScheduler scheduler = new CleanupScheduler(Duration.ofMillis(100), () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new IllegalStateException("cleanup boom");
            }
            return new ActivityMetricsService.CleanupResult(1, 0, 0, 0, 0);
        });

        Duration firstDelay = scheduler.runCleanupOnce();
        CleanupScheduler.CleanupStatus afterFailure = scheduler.snapshot();

        assertEquals(1, attempts.get());
        assertEquals(Duration.ofMillis(25), firstDelay);
        assertEquals(1L, afterFailure.failedRuns());
        assertEquals(0L, afterFailure.successfulRuns());
        assertEquals(1L, afterFailure.consecutiveFailures());
        assertEquals(Duration.ofMillis(25), afterFailure.nextDelay());
        assertNotNull(afterFailure.lastFailure());

        Duration secondDelay = scheduler.runCleanupOnce();
        CleanupScheduler.CleanupStatus afterSuccess = scheduler.snapshot();

        assertEquals(2, attempts.get());
        assertEquals(Duration.ofMillis(100), secondDelay);
        assertEquals(1L, afterSuccess.failedRuns());
        assertEquals(1L, afterSuccess.successfulRuns());
        assertEquals(0L, afterSuccess.consecutiveFailures());
        assertEquals(Duration.ofMillis(100), afterSuccess.nextDelay());
        assertNotNull(afterSuccess.lastFailure());
    }
}
