package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ActivityMetricsService concurrency")
class ActivityMetricsServiceConcurrencyTest {

    private static final Instant FIXED = Instant.parse("2026-03-12T10:00:00Z");

    private ActivityMetricsService service;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED);
        AppConfig config = AppConfig.builder()
                .maxSwipesPerSession(10_000)
                .suspiciousSwipeVelocityBlockingEnabled(false)
                .build();
        service = new ActivityMetricsService(
                new TestStorages.Interactions(), new TestStorages.TrustSafety(), new TestStorages.Analytics(), config);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("Concurrent recordMatch preserves every increment")
    void concurrentRecordMatchPreservesEveryIncrement() throws Exception {
        UUID userId = UUID.randomUUID();
        int threads = 16;
        int matchesPerThread = 250;
        int expectedMatches = threads * matchesPerThread;

        for (int i = 0; i < expectedMatches; i++) {
            service.recordSwipe(userId, Like.Direction.LIKE, false);
        }

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int thread = 0; thread < threads; thread++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < matchesPerThread; i++) {
                            service.recordMatch(userId);
                        }
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();
            assertTrue(done.await(8, TimeUnit.SECONDS), "Workers did not complete");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        Session session = service.getCurrentSession(userId).orElseThrow();
        assertEquals(expectedMatches, session.getLikeCount(), "Seeded likes must remain intact");
        assertEquals(expectedMatches, session.getMatchCount(), "Every concurrent recordMatch call must be preserved");
        assertEquals(expectedMatches, session.getSwipeCount(), "recordMatch must not affect swipe count");
    }
}
