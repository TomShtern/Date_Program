package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.metrics.*;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.*;
import datingapp.core.testutil.TestStorages;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Tests for ActivityMetricsService - user and platform statistics computation.
 */
@SuppressWarnings("unused")
@DisplayName("ActivityMetricsService")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class StatsServiceTest {

    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private ActivityMetricsService statsService;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();

        statsService = new ActivityMetricsService(
                interactionStorage, trustSafetyStorage, analyticsStorage, AppConfig.defaults());

        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("computeAndSaveStats")
    class ComputeAndSaveStats {

        @Test
        @DisplayName("Computes correct like counts")
        void computesCorrectLikeCounts() {
            // User liked 3 people
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));

            // User passed on 2 people
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.PASS));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.PASS));

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(3, stats.likesGiven());
            assertEquals(2, stats.passesGiven());
            assertEquals(5, stats.totalSwipesGiven());
        }

        @Test
        @DisplayName("Computes correct like ratio")
        void computesCorrectLikeRatio() {
            // 2 likes out of 4 swipes = 0.5 ratio
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.PASS));
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.PASS));

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(0.5, stats.likeRatio(), 0.001);
        }

        @Test
        @DisplayName("Handles zero swipes without division error")
        void handlesZeroSwipes() {
            // No swipes at all

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(0, stats.likesGiven());
            assertEquals(0, stats.passesGiven());
            assertEquals(0.0, stats.likeRatio());
            assertEquals(0.0, stats.matchRate());
        }

        @Test
        @DisplayName("Computes incoming likes received")
        void computesIncomingLikesReceived() {
            // 3 people liked this user
            interactionStorage.save(Like.create(UUID.randomUUID(), userId, Like.Direction.LIKE));
            interactionStorage.save(Like.create(UUID.randomUUID(), userId, Like.Direction.LIKE));
            interactionStorage.save(Like.create(UUID.randomUUID(), userId, Like.Direction.LIKE));
            // 1 person passed on this user
            interactionStorage.save(Like.create(UUID.randomUUID(), userId, Like.Direction.PASS));

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(3, stats.likesReceived());
            assertEquals(1, stats.passesReceived());
            assertEquals(4, stats.totalSwipesReceived());
            assertEquals(0.75, stats.incomingLikeRatio(), 0.001);
        }

        @Test
        @DisplayName("Counts matches correctly")
        void countsMatchesCorrectly() {
            // 2 total matches, 1 active
            Match match1 = Match.create(userId, UUID.randomUUID());
            Match match2 = Match.create(userId, UUID.randomUUID());
            match2.unmatch(userId); // Make one inactive

            interactionStorage.save(match1);
            interactionStorage.save(match2);

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(2, stats.totalMatches());
            assertEquals(1, stats.activeMatches());
        }

        @Test
        @DisplayName("Computes match rate correctly")
        void computesMatchRateCorrectly() {
            // 5 likes given, 2 matches = 40% rate
            for (int i = 0; i < 5; i++) {
                interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            }
            interactionStorage.save(Match.create(userId, UUID.randomUUID()));
            interactionStorage.save(Match.create(userId, UUID.randomUUID()));

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(0.4, stats.matchRate(), 0.001);
        }

        @Test
        @DisplayName("Counts blocks given and received")
        void countsBlocksGivenAndReceived() {
            trustSafetyStorage.save(Block.create(userId, UUID.randomUUID())); // User blocked someone
            trustSafetyStorage.save(Block.create(userId, UUID.randomUUID())); // User blocked another
            trustSafetyStorage.save(Block.create(UUID.randomUUID(), userId)); // Someone blocked user

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(2, stats.blocksGiven());
            assertEquals(1, stats.blocksReceived());
        }

        @Test
        @DisplayName("Counts reports given and received")
        void countsReportsGivenAndReceived() {
            trustSafetyStorage.save(
                    Report.create(userId, UUID.randomUUID(), Report.Reason.SPAM, null)); // User reported someone
            trustSafetyStorage.save(
                    Report.create(UUID.randomUUID(), userId, Report.Reason.SPAM, null)); // Someone reported user
            trustSafetyStorage.save(
                    Report.create(UUID.randomUUID(), userId, Report.Reason.SPAM, null)); // Another reported user

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(1, stats.reportsGiven());
            assertEquals(2, stats.reportsReceived());
        }

        @Test
        @DisplayName("Saves stats to storage")
        void savesStatsToStorage() {
            statsService.computeAndSaveStats(userId);

            Optional<UserStats> saved = analyticsStorage.getLatestUserStats(userId);
            assertTrue(saved.isPresent());
            assertEquals(userId, saved.get().userId());
        }
    }

    @Nested
    @DisplayName("getOrComputeStats")
    class GetOrComputeStats {

        @Test
        @DisplayName("Returns cached stats if fresh")
        void returnsCachedIfFresh() {
            // First compute
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));
            statsService.computeAndSaveStats(userId);

            // Add more likes but don't recompute
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));

            // Should return cached (1 like, not 2)
            UserStats cached = statsService.getOrComputeStats(userId);

            assertEquals(1, cached.likesGiven()); // Still shows old count
        }

        @Test
        @DisplayName("Computes new stats if none exist")
        void computesNewIfNoneExist() {
            interactionStorage.save(Like.create(userId, UUID.randomUUID(), Like.Direction.LIKE));

            UserStats stats = statsService.getOrComputeStats(userId);

            assertNotNull(stats);
            assertEquals(1, stats.likesGiven());
        }
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("Returns empty if no stats computed")
        void returnsEmptyIfNoStats() {
            Optional<UserStats> stats = statsService.getStats(userId);

            assertTrue(stats.isEmpty());
        }

        @Test
        @DisplayName("Returns saved stats without computing")
        void returnsSavedStats() {
            statsService.computeAndSaveStats(userId);

            Optional<UserStats> stats = statsService.getStats(userId);

            assertTrue(stats.isPresent());
        }
    }

    @Nested
    @DisplayName("computeAndSavePlatformStats")
    class ComputeAndSavePlatformStats {

        @Test
        @DisplayName("Returns empty stats when no users")
        void returnsEmptyWhenNoUsers() {
            PlatformStats stats = statsService.computeAndSavePlatformStats();

            assertEquals(0, stats.totalActiveUsers());
            assertEquals(0.0, stats.avgLikesReceived());
            assertEquals(0.0, stats.avgMatchRate());
        }

        @Test
        @DisplayName("Aggregates stats from all users")
        void aggregatesAllUserStats() {
            // Create stats for 2 users
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();

            // User 1: 4 likes given, 2 received
            for (int i = 0; i < 4; i++) {
                interactionStorage.save(Like.create(user1, UUID.randomUUID(), Like.Direction.LIKE));
            }
            interactionStorage.save(Like.create(UUID.randomUUID(), user1, Like.Direction.LIKE));
            interactionStorage.save(Like.create(UUID.randomUUID(), user1, Like.Direction.LIKE));
            interactionStorage.save(Match.create(user1, UUID.randomUUID()));

            // User 2: 2 likes given, 4 received
            interactionStorage.save(Like.create(user2, UUID.randomUUID(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(user2, UUID.randomUUID(), Like.Direction.LIKE));
            for (int i = 0; i < 4; i++) {
                interactionStorage.save(Like.create(UUID.randomUUID(), user2, Like.Direction.LIKE));
            }
            interactionStorage.save(Match.create(user2, UUID.randomUUID()));
            interactionStorage.save(Match.create(user2, UUID.randomUUID()));

            statsService.computeAndSaveStats(user1);
            statsService.computeAndSaveStats(user2);

            PlatformStats platform = statsService.computeAndSavePlatformStats();

            assertEquals(2, platform.totalActiveUsers());
            // Avg likes received: (2 + 4) / 2 = 3.0
            assertEquals(3.0, platform.avgLikesReceived(), 0.001);
            // Avg likes given: (4 + 2) / 2 = 3.0
            assertEquals(3.0, platform.avgLikesGiven(), 0.001);
        }

        @Test
        @DisplayName("Saves platform stats to storage")
        void savesPlatformStatsToStorage() {
            statsService.computeAndSavePlatformStats();

            Optional<PlatformStats> saved = analyticsStorage.getLatestPlatformStats();
            assertTrue(saved.isPresent());
        }
    }

    @Nested
    @DisplayName("getPlatformStats")
    class GetPlatformStats {

        @Test
        @DisplayName("Returns empty if not computed")
        void returnsEmptyIfNotComputed() {
            Optional<PlatformStats> stats = statsService.getPlatformStats();

            assertTrue(stats.isEmpty());
        }

        @Test
        @DisplayName("Returns saved platform stats")
        void returnsSavedPlatformStats() {
            statsService.computeAndSavePlatformStats();

            Optional<PlatformStats> stats = statsService.getPlatformStats();

            assertTrue(stats.isPresent());
        }
    }
}
