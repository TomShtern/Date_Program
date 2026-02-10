package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for CleanupService.
 */
@Timeout(5)
@SuppressWarnings("unused")
@DisplayName("CleanupService")
class CleanupServiceTest {

    private TestStatsStorage statsStorage;
    private TestSessionStorage sessionStorage;
    private CleanupService service;
    private AppConfig config;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        config = AppConfig.defaults();
        statsStorage = new TestStatsStorage();
        sessionStorage = new TestSessionStorage();
        service = new CleanupService(statsStorage, sessionStorage, config);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should require non-null statsStorage")
        void requiresStatsStorage() {
            assertThrows(NullPointerException.class, () -> new CleanupService(null, sessionStorage, config));
        }

        @Test
        @DisplayName("Should require non-null sessionStorage")
        void requiresSessionStorage() {
            assertThrows(NullPointerException.class, () -> new CleanupService(statsStorage, null, config));
        }

        @Test
        @DisplayName("Should require non-null config")
        void requiresConfig() {
            assertThrows(NullPointerException.class, () -> new CleanupService(statsStorage, sessionStorage, null));
        }
    }

    @Nested
    @DisplayName("runCleanup")
    class RunCleanup {

        @Test
        @DisplayName("Should return cleanup result with counts")
        void returnsCleanupResult() {
            statsStorage.setDeletedCount(5);
            sessionStorage.setDeletedCount(3);

            CleanupService.CleanupResult result = service.runCleanup();

            assertEquals(5, result.dailyPicksDeleted());
            assertEquals(3, result.sessionsDeleted());
            assertEquals(8, result.totalDeleted());
            assertTrue(result.hadWork());
        }

        @Test
        @DisplayName("Should report no work when nothing deleted")
        void reportsNoWork() {
            statsStorage.setDeletedCount(0);
            sessionStorage.setDeletedCount(0);

            CleanupService.CleanupResult result = service.runCleanup();

            assertEquals(0, result.totalDeleted());
            assertFalse(result.hadWork());
        }

        @Test
        @DisplayName("Should pass cutoff date to storage")
        void passesCutoffDate() {
            service.runCleanup();

            assertNotNull(statsStorage.receivedCutoff);
            assertNotNull(sessionStorage.receivedCutoff);
            // Cutoff should be in the past
            assertTrue(statsStorage.receivedCutoff.isBefore(AppClock.now()));
        }
    }

    @Nested
    @DisplayName("CleanupResult")
    class CleanupResultTests {

        @Test
        @DisplayName("Should calculate total correctly")
        void calculatesTotal() {
            var result = new CleanupService.CleanupResult(10, 20);
            assertEquals(30, result.totalDeleted());
        }

        @Test
        @DisplayName("Should format toString nicely")
        void formatsToString() {
            var result = new CleanupService.CleanupResult(5, 10);
            String str = result.toString();
            assertTrue(str.contains("dailyPicks=5"));
            assertTrue(str.contains("sessions=10"));
            assertTrue(str.contains("total=15"));
        }

        @Test
        @DisplayName("hadWork returns false for zero counts")
        void hadWorkFalseForZero() {
            var result = new CleanupService.CleanupResult(0, 0);
            assertFalse(result.hadWork());
        }

        @Test
        @DisplayName("hadWork returns true when any count > 0")
        void hadWorkTrueForPositive() {
            var result = new CleanupService.CleanupResult(1, 0);
            assertTrue(result.hadWork());
        }
    }

    // === Test Doubles ===

    private static class TestStatsStorage implements StatsStorage {
        int deletedCount = 0;
        Instant receivedCutoff;

        void setDeletedCount(int count) {
            this.deletedCount = count;
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant before) {
            this.receivedCutoff = before;
            return deletedCount;
        }

        // Unused methods - required by interface
        @Override
        public void saveUserStats(datingapp.core.Stats.UserStats stats) {
            // Not used in cleanup tests
        }

        @Override
        public java.util.Optional<datingapp.core.Stats.UserStats> getLatestUserStats(java.util.UUID userId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<datingapp.core.Stats.UserStats> getUserStatsHistory(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<datingapp.core.Stats.UserStats> getAllLatestUserStats() {
            return java.util.List.of();
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void savePlatformStats(datingapp.core.Stats.PlatformStats stats) {
            // Not used in cleanup tests
        }

        @Override
        public java.util.Optional<datingapp.core.Stats.PlatformStats> getLatestPlatformStats() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<datingapp.core.Stats.PlatformStats> getPlatformStatsHistory(int limit) {
            return java.util.List.of();
        }

        @Override
        public void recordProfileView(java.util.UUID viewerId, java.util.UUID viewedId) {
            // Not used in cleanup tests
        }

        @Override
        public int getProfileViewCount(java.util.UUID userId) {
            return 0;
        }

        @Override
        public int getUniqueViewerCount(java.util.UUID userId) {
            return 0;
        }

        @Override
        public java.util.List<java.util.UUID> getRecentViewers(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public boolean hasViewedProfile(java.util.UUID viewerId, java.util.UUID viewedId) {
            return false;
        }

        @Override
        public void saveUserAchievement(datingapp.core.Achievement.UserAchievement achievement) {
            // Not used in cleanup tests
        }

        @Override
        public java.util.List<datingapp.core.Achievement.UserAchievement> getUnlockedAchievements(
                java.util.UUID userId) {
            return java.util.List.of();
        }

        @Override
        public boolean hasAchievement(java.util.UUID userId, datingapp.core.Achievement achievement) {
            return false;
        }

        @Override
        public int countUnlockedAchievements(java.util.UUID userId) {
            return 0;
        }
    }

    private static class TestSessionStorage implements SwipeSessionStorage {
        int deletedCount = 0;
        Instant receivedCutoff;

        void setDeletedCount(int count) {
            this.deletedCount = count;
        }

        @Override
        public int deleteExpiredSessions(Instant before) {
            this.receivedCutoff = before;
            return deletedCount;
        }

        @Override
        public void save(SwipeSession session) {
            // Not used in cleanup tests
        }

        @Override
        public java.util.Optional<SwipeSession> get(java.util.UUID sessionId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<SwipeSession> getActiveSession(java.util.UUID userId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<SwipeSession> getSessionsFor(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<SwipeSession> getSessionsInRange(java.util.UUID userId, Instant start, Instant end) {
            return java.util.List.of();
        }

        @Override
        public SwipeSessionStorage.SessionAggregates getAggregates(java.util.UUID userId) {
            return SwipeSessionStorage.SessionAggregates.empty();
        }

        @Override
        public int endStaleSessions(java.time.Duration timeout) {
            return 0;
        }
    }
}
