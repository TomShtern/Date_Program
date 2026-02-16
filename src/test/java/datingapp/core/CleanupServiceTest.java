package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.metrics.*;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for ActivityMetricsService cleanup functionality (runCleanup +
 * CleanupResult).
 */
@Timeout(5)
@SuppressWarnings("unused")
@DisplayName("ActivityMetricsService cleanup")
class CleanupServiceTest {

    private TestCleanupAnalytics analyticsStorage;
    private ActivityMetricsService service;
    private AppConfig config;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        config = AppConfig.defaults();
        analyticsStorage = new TestCleanupAnalytics();
        service = new ActivityMetricsService(
                new TestStorages.Interactions(), new TestStorages.TrustSafety(), analyticsStorage, config);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should require non-null analyticsStorage")
        void requiresAnalyticsStorage() {
            assertThrows(
                    NullPointerException.class,
                    () -> new ActivityMetricsService(
                            new TestStorages.Interactions(), new TestStorages.TrustSafety(), null, config));
        }

        @Test
        @DisplayName("Should require non-null config")
        void requiresConfig() {
            assertThrows(
                    NullPointerException.class,
                    () -> new ActivityMetricsService(
                            new TestStorages.Interactions(), new TestStorages.TrustSafety(), analyticsStorage, null));
        }
    }

    @Nested
    @DisplayName("runCleanup")
    class RunCleanup {

        @Test
        @DisplayName("Should return cleanup result with counts")
        void returnsCleanupResult() {
            analyticsStorage.setDailyPickDeletedCount(5);
            analyticsStorage.setSessionDeletedCount(3);

            ActivityMetricsService.CleanupResult result = service.runCleanup();

            assertEquals(5, result.dailyPicksDeleted());
            assertEquals(3, result.sessionsDeleted());
            assertEquals(8, result.totalDeleted());
            assertTrue(result.hadWork());
        }

        @Test
        @DisplayName("Should report no work when nothing deleted")
        void reportsNoWork() {
            analyticsStorage.setDailyPickDeletedCount(0);
            analyticsStorage.setSessionDeletedCount(0);

            ActivityMetricsService.CleanupResult result = service.runCleanup();

            assertEquals(0, result.totalDeleted());
            assertFalse(result.hadWork());
        }

        @Test
        @DisplayName("Should pass cutoff date to storage")
        void passesCutoffDate() {
            service.runCleanup();

            assertNotNull(analyticsStorage.receivedDailyPickCutoff);
            assertNotNull(analyticsStorage.receivedSessionCutoff);
            // Cutoff should be in the past
            assertTrue(analyticsStorage.receivedDailyPickCutoff.isBefore(AppClock.now()));
        }
    }

    @Nested
    @DisplayName("CleanupResult")
    class CleanupResultTests {

        @Test
        @DisplayName("Should calculate total correctly")
        void calculatesTotal() {
            var result = new ActivityMetricsService.CleanupResult(10, 20);
            assertEquals(30, result.totalDeleted());
        }

        @Test
        @DisplayName("Should format toString nicely")
        void formatsToString() {
            var result = new ActivityMetricsService.CleanupResult(5, 10);
            String str = result.toString();
            assertTrue(str.contains("dailyPicks=5"));
            assertTrue(str.contains("sessions=10"));
            assertTrue(str.contains("total=15"));
        }

        @Test
        @DisplayName("hadWork returns false for zero counts")
        void hadWorkFalseForZero() {
            var result = new ActivityMetricsService.CleanupResult(0, 0);
            assertFalse(result.hadWork());
        }

        @Test
        @DisplayName("hadWork returns true when any count > 0")
        void hadWorkTrueForPositive() {
            var result = new ActivityMetricsService.CleanupResult(1, 0);
            assertTrue(result.hadWork());
        }
    }

    // === Test Double ===

    /**
     * Analytics storage that tracks cleanup method calls for testing.
     * Implements only behavior needed for ActivityMetricsService cleanup tests.
     */
    private static class TestCleanupAnalytics implements AnalyticsStorage {
        int dailyPickDeletedCount = 0;
        int sessionDeletedCount = 0;
        Instant receivedDailyPickCutoff;
        Instant receivedSessionCutoff;

        void setDailyPickDeletedCount(int count) {
            this.dailyPickDeletedCount = count;
        }

        void setSessionDeletedCount(int count) {
            this.sessionDeletedCount = count;
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant before) {
            this.receivedDailyPickCutoff = before;
            return dailyPickDeletedCount;
        }

        @Override
        public int deleteExpiredSessions(Instant cutoff) {
            this.receivedSessionCutoff = cutoff;
            return sessionDeletedCount;
        }

        @Override
        public void saveUserStats(EngagementDomain.UserStats stats) {}

        @Override
        public Optional<EngagementDomain.UserStats> getLatestUserStats(UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<EngagementDomain.UserStats> getUserStatsHistory(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public List<EngagementDomain.UserStats> getAllLatestUserStats() {
            return List.of();
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void savePlatformStats(EngagementDomain.PlatformStats stats) {}

        @Override
        public Optional<EngagementDomain.PlatformStats> getLatestPlatformStats() {
            return Optional.empty();
        }

        @Override
        public List<EngagementDomain.PlatformStats> getPlatformStatsHistory(int limit) {
            return List.of();
        }

        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {}

        @Override
        public int getProfileViewCount(UUID userId) {
            return 0;
        }

        @Override
        public int getUniqueViewerCount(UUID userId) {
            return 0;
        }

        @Override
        public List<UUID> getRecentViewers(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return false;
        }

        @Override
        public void saveUserAchievement(Achievement.UserAchievement achievement) {}

        @Override
        public List<Achievement.UserAchievement> getUnlockedAchievements(UUID userId) {
            return List.of();
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return false;
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return 0;
        }

        @Override
        public void markDailyPickAsViewed(UUID userId, LocalDate date) {}

        @Override
        public boolean isDailyPickViewed(UUID userId, LocalDate date) {
            return false;
        }

        @Override
        public int deleteDailyPickViewsOlderThan(LocalDate before) {
            return 0;
        }

        @Override
        public void saveSession(Session session) {}

        @Override
        public Optional<Session> getSession(UUID sessionId) {
            return Optional.empty();
        }

        @Override
        public Optional<Session> getActiveSession(UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<Session> getSessionsFor(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public List<Session> getSessionsInRange(UUID userId, Instant start, Instant end) {
            return List.of();
        }

        @Override
        public SessionAggregates getSessionAggregates(UUID userId) {
            return SessionAggregates.empty();
        }

        @Override
        public int endStaleSessions(Duration timeout) {
            return 0;
        }
    }
}
