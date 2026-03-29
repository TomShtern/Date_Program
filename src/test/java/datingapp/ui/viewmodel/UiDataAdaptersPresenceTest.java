package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UiDataAdapters presence adapter")
class UiDataAdaptersPresenceTest {

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("reports offline when the user has no active session")
    void reportsOfflineWithoutActiveSession() {
        ActivityMetricsService activityMetricsService = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        UiDataAdapters.MetricsUiPresenceDataAccess adapter =
                new UiDataAdapters.MetricsUiPresenceDataAccess(activityMetricsService, AppConfig.defaults());

        assertEquals(UiDataAdapters.PresenceStatus.OFFLINE, adapter.getPresence(UUID.randomUUID()));
        assertTrue(adapter.isSupported());
        assertFalse(adapter.isTyping(UUID.randomUUID()));
    }

    @Test
    @DisplayName("reports online when the active session is recent")
    void reportsOnlineForRecentActiveSession() {
        TestClock.setFixed(Instant.parse("2026-03-29T12:00:00Z"));
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        UUID userId = UUID.randomUUID();
        analytics.saveSession(new Session(
                UUID.randomUUID(),
                userId,
                Instant.parse("2026-03-29T11:55:00Z"),
                Instant.parse("2026-03-29T11:59:30Z"),
                null,
                Session.MatchState.ACTIVE,
                0,
                0,
                0,
                0));

        ActivityMetricsService activityMetricsService = new ActivityMetricsService(
                new TestStorages.Interactions(), new TestStorages.TrustSafety(), analytics, AppConfig.defaults());
        UiDataAdapters.MetricsUiPresenceDataAccess adapter =
                new UiDataAdapters.MetricsUiPresenceDataAccess(activityMetricsService, Duration.ofMinutes(2));

        assertEquals(UiDataAdapters.PresenceStatus.ONLINE, adapter.getPresence(userId));
    }

    @Test
    @DisplayName("reports away when the active session is stale but still active")
    void reportsAwayForStaleActiveSession() {
        TestClock.setFixed(Instant.parse("2026-03-29T12:00:00Z"));
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        UUID userId = UUID.randomUUID();
        analytics.saveSession(new Session(
                UUID.randomUUID(),
                userId,
                Instant.parse("2026-03-29T11:00:00Z"),
                Instant.parse("2026-03-29T11:30:00Z"),
                null,
                Session.MatchState.ACTIVE,
                0,
                0,
                0,
                0));

        ActivityMetricsService activityMetricsService = new ActivityMetricsService(
                new TestStorages.Interactions(), new TestStorages.TrustSafety(), analytics, AppConfig.defaults());
        UiDataAdapters.MetricsUiPresenceDataAccess adapter =
                new UiDataAdapters.MetricsUiPresenceDataAccess(activityMetricsService, Duration.ofMinutes(2));

        assertEquals(UiDataAdapters.PresenceStatus.AWAY, adapter.getPresence(userId));
    }
}
