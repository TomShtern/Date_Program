package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.UserStats;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Consolidated unit tests for statistics and metrics domain models.
 *
 * <p>Includes tests for:
 * <ul>
 *   <li>{@link PlatformStats} - platform-wide statistics</li>
 *   <li>{@link UserAchievement} - user achievement records</li>
 *   <li>{@link UserStats} - individual user statistics</li>
 * </ul>
 */
@SuppressWarnings("unused") // Test class with @Nested
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class StatsMetricsTest {

    // ==================== PLATFORM STATS TESTS ====================

    @Nested
    @DisplayName("PlatformStats Domain Model")
    class PlatformStatsTests {

        @Test
        @DisplayName("create() factory generates ID and timestamp")
        void createFactoryGeneratesIdAndTimestamp() {
            PlatformStats stats = PlatformStats.create(
                    100, // totalActiveUsers
                    50.0, // avgLikesReceived
                    45.0, // avgLikesGiven
                    0.35, // avgMatchRate
                    0.65 // avgLikeRatio
                    );

            assertNotNull(stats.id(), "ID should be generated");
            assertNotNull(stats.computedAt(), "Timestamp should be set");
            assertEquals(100, stats.totalActiveUsers());
            assertEquals(50.0, stats.avgLikesReceived());
            assertEquals(45.0, stats.avgLikesGiven());
            assertEquals(0.35, stats.avgMatchRate());
            assertEquals(0.65, stats.avgLikeRatio());
        }

        @Test
        @DisplayName("empty() factory returns default values for new platform")
        void emptyFactoryReturnsDefaults() {
            PlatformStats empty = PlatformStats.empty();

            assertNotNull(empty.id(), "ID should be generated");
            assertNotNull(empty.computedAt(), "Timestamp should be set");
            assertEquals(0, empty.totalActiveUsers(), "New platform has 0 users");
            assertEquals(0.0, empty.avgLikesReceived(), "No likes received");
            assertEquals(0.0, empty.avgLikesGiven(), "No likes given");
            assertEquals(0.0, empty.avgMatchRate(), "No matches yet");
            assertEquals(0.5, empty.avgLikeRatio(), "Default 50% like ratio");
        }

        @Test
        @DisplayName("IDs are unique for multiple stats snapshots")
        void idsAreUnique() {
            PlatformStats stats1 = PlatformStats.create(100, 50.0, 45.0, 0.35, 0.65);
            PlatformStats stats2 = PlatformStats.create(100, 50.0, 45.0, 0.35, 0.65);

            assertNotEquals(stats1.id(), stats2.id(), "Different snapshots should have unique IDs");
        }

        @Test
        @DisplayName("empty() creates unique instances")
        void emptyCreatesUniqueInstances() {
            PlatformStats empty1 = PlatformStats.empty();
            PlatformStats empty2 = PlatformStats.empty();

            assertNotEquals(empty1.id(), empty2.id(), "Each empty() call should generate unique ID");
        }

        @Test
        @DisplayName("Record equality works correctly")
        void recordEquality() {
            PlatformStats stats1 = new PlatformStats(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    Instant.parse("2026-01-10T12:00:00Z"),
                    100,
                    50.0,
                    45.0,
                    0.35,
                    0.65);

            PlatformStats stats2 = new PlatformStats(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    Instant.parse("2026-01-10T12:00:00Z"),
                    100,
                    50.0,
                    45.0,
                    0.35,
                    0.65);

            assertEquals(stats1, stats2, "Records with same values should be equal");
            assertEquals(stats1.hashCode(), stats2.hashCode(), "Hash codes should match");
        }

        @Test
        @DisplayName("Accepts zero and negative values without validation")
        void acceptsZeroAndNegativeValues() {
            // Note: PlatformStats has no compact constructor validation
            // This test documents the current behavior
            assertDoesNotThrow(() -> PlatformStats.create(0, -10.0, -5.0, -0.5, -1.0));
        }
    }

    // ==================== USER ACHIEVEMENT TESTS ====================

    @Nested
    @DisplayName("UserAchievement Domain Model")
    class UserAchievementTests {

        @Test
        @DisplayName("create() factory generates ID and timestamp")
        void createFactoryGeneratesIdAndTimestamp() {
            UUID userId = UUID.randomUUID();
            Achievement achievement = Achievement.FIRST_SPARK;

            UserAchievement ua = UserAchievement.create(userId, achievement);

            assertNotNull(ua.id(), "ID should be generated");
            assertEquals(userId, ua.userId(), "User ID should match");
            assertEquals(achievement, ua.achievement(), "Achievement should match");
            assertNotNull(ua.unlockedAt(), "Unlock timestamp should be set");
        }

        @Test
        @DisplayName("of() factory loads from storage with all fields")
        void ofFactoryLoadsFromStorage() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Achievement achievement = Achievement.SOCIAL_BUTTERFLY;
            Instant timestamp = Instant.now().minusSeconds(3600);

            UserAchievement ua = UserAchievement.of(id, userId, achievement, timestamp);

            assertEquals(id, ua.id());
            assertEquals(userId, ua.userId());
            assertEquals(achievement, ua.achievement());
            assertEquals(timestamp, ua.unlockedAt());
        }

        @Test
        @DisplayName("IDs are unique for multiple unlocks")
        void idsAreUnique() {
            UUID userId = UUID.randomUUID();

            UserAchievement ua1 = UserAchievement.create(userId, Achievement.FIRST_SPARK);
            UserAchievement ua2 = UserAchievement.create(userId, Achievement.SOCIAL_BUTTERFLY);

            assertNotEquals(ua1.id(), ua2.id(), "Different unlocks should have unique IDs");
        }

        @Test
        @DisplayName("Null ID throws NullPointerException")
        void nullIdThrows() {
            UUID userId = UUID.randomUUID();
            Instant unlockedAt = Instant.now();
            assertThrows(
                    NullPointerException.class,
                    () -> UserAchievement.of(null, userId, Achievement.FIRST_SPARK, unlockedAt));
        }

        @Test
        @DisplayName("Null userId throws NullPointerException")
        void nullUserIdThrows() {
            assertThrows(NullPointerException.class, () -> UserAchievement.create(null, Achievement.FIRST_SPARK));
        }

        @Test
        @DisplayName("Null achievement throws NullPointerException")
        void nullAchievementThrows() {
            UUID userId = UUID.randomUUID();
            assertThrows(NullPointerException.class, () -> UserAchievement.create(userId, null));
        }

        @Test
        @DisplayName("Null unlockedAt throws NullPointerException")
        void nullUnlockedAtThrows() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            assertThrows(
                    NullPointerException.class, () -> UserAchievement.of(id, userId, Achievement.FIRST_SPARK, null));
        }

        @Test
        @DisplayName("Record equality works correctly")
        void recordEquality() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Achievement achievement = Achievement.FIRST_SPARK;
            Instant timestamp = Instant.now();

            UserAchievement ua1 = UserAchievement.of(id, userId, achievement, timestamp);
            UserAchievement ua2 = UserAchievement.of(id, userId, achievement, timestamp);

            assertEquals(ua1, ua2, "Records with same values should be equal");
            assertEquals(ua1.hashCode(), ua2.hashCode(), "Hash codes should match");
        }
    }

    // ==================== USER STATS TESTS ====================

    @Nested
    @DisplayName("UserStats Domain Model")
    class UserStatsTests {

        @Nested
        @DisplayName("Validation tests")
        class ValidationTests {

            @Test
            @DisplayName("Ratios must be between 0.0 and 1.0")
            void validatesRatios() {
                var ex = assertThrows(IllegalArgumentException.class, this::createInvalidRatioStats);
                assertNotNull(ex);
            }

            @Test
            @DisplayName("Negative ratios are rejected")
            void rejectsNegativeRatios() {
                var ex = assertThrows(IllegalArgumentException.class, this::createNegativeRatioStats);
                assertNotNull(ex);
            }

            private void createInvalidRatioStats() {
                new UserStats(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        0,
                        0,
                        0,
                        1.5, // likeRatio > 1.0
                        0,
                        0,
                        0,
                        0.5,
                        0,
                        0,
                        0.5,
                        0,
                        0,
                        0,
                        0,
                        0.5,
                        0.5,
                        0.5);
            }

            private void createNegativeRatioStats() {
                new UserStats(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        0,
                        0,
                        0,
                        -0.1, // negative likeRatio
                        0,
                        0,
                        0,
                        0.5,
                        0,
                        0,
                        0.5,
                        0,
                        0,
                        0,
                        0,
                        0.5,
                        0.5,
                        0.5);
            }

            @Test
            @DisplayName("Valid stats are accepted")
            void acceptsValidStats() {
                UserStats stats = new UserStats(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        100,
                        70,
                        30,
                        0.7, // outgoing
                        80,
                        50,
                        30,
                        0.625, // incoming
                        15,
                        10,
                        0.214, // matches
                        2,
                        1,
                        1,
                        0, // safety
                        0.35,
                        0.6,
                        0.55 // scores
                        );

                assertEquals(100, stats.totalSwipesGiven());
                assertEquals(70, stats.likesGiven());
                assertEquals(0.7, stats.likeRatio(), 0.01);
                assertEquals(15, stats.totalMatches());
            }
        }

        @Nested
        @DisplayName("Builder tests")
        class BuilderTests {

            @Test
            @DisplayName("StatsBuilder creates valid stats through factory")
            void builderCreatesValidStats() {
                UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
                builder.likesGiven = 50;
                builder.passesGiven = 50;
                builder.totalSwipesGiven = 100;
                builder.likeRatio = 0.5;
                builder.totalMatches = 10;
                builder.matchRate = 0.2;

                UUID userId = UUID.randomUUID();
                UserStats stats = UserStats.create(userId, builder);

                assertNotNull(stats.id());
                assertEquals(userId, stats.userId());
                assertEquals(100, stats.totalSwipesGiven());
                assertEquals(0.5, stats.likeRatio(), 0.01);
            }

            @Test
            @DisplayName("Builder defaults to sensible values")
            void builderDefaults() {
                UserStats.StatsBuilder builder = new UserStats.StatsBuilder();

                assertEquals(0, builder.likesGiven);
                assertEquals(0.0, builder.likeRatio);
                assertEquals(0.5, builder.selectivenessScore); // 0.5 = average
                assertEquals(0.5, builder.attractivenessScore);
            }
        }

        @Nested
        @DisplayName("Display formatting tests")
        class DisplayTests {

            @Test
            @DisplayName("Like ratio displays as percentage")
            void likeRatioDisplay() {
                UserStats stats = createTestStats(0.75);
                assertEquals("75.0%", stats.getLikeRatioDisplay());
            }

            @Test
            @DisplayName("Match rate displays as percentage")
            void matchRateDisplay() {
                UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
                builder.matchRate = 0.214;
                UserStats stats = UserStats.create(UUID.randomUUID(), builder);
                assertEquals("21.4%", stats.getMatchRateDisplay());
            }

            private UserStats createTestStats(double likeRatio) {
                UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
                builder.likeRatio = likeRatio;
                return UserStats.create(UUID.randomUUID(), builder);
            }
        }
    }
}
