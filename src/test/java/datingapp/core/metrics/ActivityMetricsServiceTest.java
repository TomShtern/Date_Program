package datingapp.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
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

    @Test
    @DisplayName("a burst inside the first second counts as fast, not as N per minute")
    void burstInsideFirstSecondIsSuspicious() {
        ActivityMetricsService service = createService(true, 100, 30.0);
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 9; i++) {
            assertTrue(service.recordSwipe(userId, Like.Direction.LIKE, false).allowed());
        }

        // Clock is frozen, so the session lasted 0 seconds: 10 swipes must not slip under a 30/min threshold.
        assertFalse(service.recordSwipe(userId, Like.Direction.LIKE, false).allowed());
    }

    @Test
    @DisplayName("checkSwipeAllowed refuses at the session limit without counting anything")
    void checkSwipeAllowedRefusesAtSessionLimitWithoutCounting() {
        ActivityMetricsService service = createService(false, 3, 5.0);
        UUID userId = UUID.randomUUID();

        assertTrue(service.checkSwipeAllowed(userId).allowed(), "no session yet means nothing to refuse");
        for (int i = 0; i < 3; i++) {
            assertTrue(service.recordSwipe(userId, Like.Direction.LIKE, false).allowed());
        }

        ActivityMetricsService.SwipeGateResult gate = service.checkSwipeAllowed(userId);

        assertFalse(gate.allowed());
        assertNotNull(gate.blockedReason());
        assertEquals(3, service.getCurrentSession(userId).orElseThrow().getSwipeCount());
    }

    @Test
    @DisplayName("checkSwipeAllowed refuses a velocity burst only when blocking is enabled")
    void checkSwipeAllowedHonoursVelocityBlockingSwitch() {
        for (boolean blocking : new boolean[] {true, false}) {
            ActivityMetricsService service = createService(blocking, 100, 5.0);
            UUID userId = UUID.randomUUID();
            for (int i = 0; i < 9; i++) {
                service.recordSwipe(userId, Like.Direction.LIKE, false);
            }

            assertEquals(!blocking, service.checkSwipeAllowed(userId).allowed());
        }
    }

    @Test
    @DisplayName("stats count a super like as a like given")
    void statsCountSuperLikesAsLikes() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Users(),
                interactions,
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        UUID userId = UUID.randomUUID();
        interactions.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
        interactions.save(Like.create(userId, UUID.randomUUID(), Like.Direction.SUPER_LIKE));
        interactions.save(Like.create(userId, UUID.randomUUID(), Like.Direction.PASS));

        var stats = service.computeAndSaveStats(userId);

        assertEquals(2, stats.likesGiven());
        assertEquals(3, stats.totalSwipesGiven());
    }

    private static ActivityMetricsService createService(boolean blockingEnabled) {
        return createService(blockingEnabled, 100, 5.0);
    }

    private static ActivityMetricsService createService(
            boolean blockingEnabled, int maxSwipesPerSession, double velocityThreshold) {
        AppConfig config = AppConfig.builder()
                .maxSwipesPerSession(maxSwipesPerSession)
                .suspiciousSwipeVelocity(velocityThreshold)
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
