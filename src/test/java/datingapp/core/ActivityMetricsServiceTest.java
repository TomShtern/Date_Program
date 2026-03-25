package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ActivityMetricsService velocity gating")
class ActivityMetricsServiceTest {

    private static final Instant FIXED = Instant.parse("2026-03-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("blocks suspicious swipe velocity when blocking is enabled")
    void blocksSuspiciousVelocityWhenEnabled() {
        ActivityMetricsService service = createService(true);
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 9; i++) {
            ActivityMetricsService.SwipeGateResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);
            assertTrue(result.allowed());
            assertFalse(result.hasWarning());
        }

        ActivityMetricsService.SwipeGateResult blocked = service.recordSwipe(userId, Like.Direction.LIKE, false);

        assertFalse(blocked.allowed());
        assertNotNull(blocked.blockedReason());
        assertEquals(9, service.getCurrentSession(userId).orElseThrow().getSwipeCount());
        ActivityMetricsService.DiagnosticsSnapshot snapshot = service.getDiagnosticsSnapshot();
        assertEquals(1L, snapshot.velocityBlockedCount());
        assertEquals(0L, snapshot.velocityWarningCount());
    }

    @Test
    @DisplayName("warns on suspicious swipe velocity when blocking is disabled")
    void warnsSuspiciousVelocityWhenDisabled() {
        ActivityMetricsService service = createService(false);
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 9; i++) {
            ActivityMetricsService.SwipeGateResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);
            assertTrue(result.allowed());
            assertFalse(result.hasWarning());
        }

        ActivityMetricsService.SwipeGateResult warned = service.recordSwipe(userId, Like.Direction.LIKE, false);

        assertTrue(warned.allowed());
        assertTrue(warned.hasWarning());
        assertNotNull(warned.warning());
        assertEquals(10, service.getCurrentSession(userId).orElseThrow().getSwipeCount());
        ActivityMetricsService.DiagnosticsSnapshot snapshot = service.getDiagnosticsSnapshot();
        assertEquals(0L, snapshot.velocityBlockedCount());
        assertEquals(1L, snapshot.velocityWarningCount());
    }

    private static ActivityMetricsService createService(boolean blockingEnabled) {
        AppConfig config = AppConfig.builder()
                .maxSwipesPerSession(100)
                .suspiciousSwipeVelocity(5.0)
                .suspiciousSwipeVelocityBlockingEnabled(blockingEnabled)
                .build();
        return new ActivityMetricsService(
                new TestStorages.Users(),
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                config);
    }
}
