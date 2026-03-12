package datingapp.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.bootstrap.CleanupScheduler;
import datingapp.core.metrics.ActivityMetricsService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
@DisplayName("CleanupScheduler periodic execution")
class CleanupSchedulerTest {

    @Test
    @DisplayName("start schedules periodic cleanup and stop halts execution")
    void startSchedulesCleanupAndStopHalts() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        CleanupScheduler scheduler = new CleanupScheduler(Duration.ofMillis(100), () -> {
            calls.incrementAndGet();
            latch.countDown();
            return new ActivityMetricsService.CleanupResult(0, 0, 0);
        });

        scheduler.start();
        assertTrue(scheduler.isRunning());
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Expected cleanup to run periodically");

        scheduler.stop();
        assertFalse(scheduler.isRunning());
        int afterStop = calls.get();

        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
        assertTrue(calls.get() <= afterStop + 1, "Cleanup should not continue running after stop");
    }
}
