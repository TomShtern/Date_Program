package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

@DisplayName("ActivityMetricsService diagnostics")
class ActivityMetricsServiceDiagnosticsTest {

    private static final Instant FIXED = Instant.parse("2026-03-12T10:00:00Z");

    private ActivityMetricsService service;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED);
        AppConfig config = AppConfig.builder()
                .maxSwipesPerSession(10)
                .suspiciousSwipeVelocity(5.0)
                .build();
        service = new ActivityMetricsService(
                new TestStorages.Interactions(), new TestStorages.TrustSafety(), new TestStorages.Analytics(), config);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("snapshot tracks notable branches and silent no-ops")
    void snapshotTracksNotableBranchesAndNoOps() {
        UUID swipeUserId = UUID.randomUUID();
        UUID matchNoOpUserId = UUID.randomUUID();
        UUID endNoOpUserId = UUID.randomUUID();

        service.recordMatch(matchNoOpUserId);
        service.endSession(endNoOpUserId);

        for (int i = 0; i < 10; i++) {
            service.recordSwipe(swipeUserId, Like.Direction.LIKE, false);
        }

        ActivityMetricsService.SwipeGateResult blocked = service.recordSwipe(swipeUserId, Like.Direction.LIKE, false);

        ActivityMetricsService.DiagnosticsSnapshot snapshot = service.getDiagnosticsSnapshot();

        assertFalse(blocked.allowed(), "The final swipe must still be blocked by the session limit");
        assertEquals("Session swipe limit reached. Take a break!", blocked.blockedReason());
        assertEquals(1L, snapshot.swipeLimitBlockedCount());
        assertEquals(1L, snapshot.velocityWarningCount());
        assertEquals(1L, snapshot.recordMatchNoOpCount());
        assertEquals(1L, snapshot.endSessionNoOpCount());
    }
}
