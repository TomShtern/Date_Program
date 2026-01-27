package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Match.MatchStorage;
import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.PlatformStatsStorage;
import datingapp.core.Stats.UserStats;
import datingapp.core.Stats.UserStatsStorage;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.LikeStorage;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserInteractions.ReportStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for StatsService - user and platform statistics computation.
 */
@SuppressWarnings("unused")
@DisplayName("StatsService")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class StatsServiceTest {

    private InMemoryLikeStorage likeStorage;
    private InMemoryMatchStorage matchStorage;
    private InMemoryBlockStorage blockStorage;
    private InMemoryReportStorage reportStorage;
    private InMemoryUserStatsStorage userStatsStorage;
    private InMemoryPlatformStatsStorage platformStatsStorage;
    private StatsService statsService;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        likeStorage = new InMemoryLikeStorage();
        matchStorage = new InMemoryMatchStorage();
        blockStorage = new InMemoryBlockStorage();
        reportStorage = new InMemoryReportStorage();
        userStatsStorage = new InMemoryUserStatsStorage();
        platformStatsStorage = new InMemoryPlatformStatsStorage();

        statsService = new StatsService(
                likeStorage, matchStorage, blockStorage, reportStorage, userStatsStorage, platformStatsStorage);

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
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);

            // User passed on 2 people
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.PASS);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.PASS);

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(3, stats.likesGiven());
            assertEquals(2, stats.passesGiven());
            assertEquals(5, stats.totalSwipesGiven());
        }

        @Test
        @DisplayName("Computes correct like ratio")
        void computesCorrectLikeRatio() {
            // 2 likes out of 4 swipes = 0.5 ratio
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.PASS);
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.PASS);

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
            likeStorage.addLike(UUID.randomUUID(), userId, Like.Direction.LIKE);
            likeStorage.addLike(UUID.randomUUID(), userId, Like.Direction.LIKE);
            likeStorage.addLike(UUID.randomUUID(), userId, Like.Direction.LIKE);
            // 1 person passed on this user
            likeStorage.addLike(UUID.randomUUID(), userId, Like.Direction.PASS);

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

            matchStorage.save(match1);
            matchStorage.save(match2);

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(2, stats.totalMatches());
            assertEquals(1, stats.activeMatches());
        }

        @Test
        @DisplayName("Computes match rate correctly")
        void computesMatchRateCorrectly() {
            // 5 likes given, 2 matches = 40% rate
            for (int i = 0; i < 5; i++) {
                likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            }
            matchStorage.save(Match.create(userId, UUID.randomUUID()));
            matchStorage.save(Match.create(userId, UUID.randomUUID()));

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(0.4, stats.matchRate(), 0.001);
        }

        @Test
        @DisplayName("Counts blocks given and received")
        void countsBlocksGivenAndReceived() {
            blockStorage.addBlock(userId, UUID.randomUUID()); // User blocked someone
            blockStorage.addBlock(userId, UUID.randomUUID()); // User blocked another
            blockStorage.addBlock(UUID.randomUUID(), userId); // Someone blocked user

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(2, stats.blocksGiven());
            assertEquals(1, stats.blocksReceived());
        }

        @Test
        @DisplayName("Counts reports given and received")
        void countsReportsGivenAndReceived() {
            reportStorage.addReport(userId, UUID.randomUUID()); // User reported someone
            reportStorage.addReport(UUID.randomUUID(), userId); // Someone reported user
            reportStorage.addReport(UUID.randomUUID(), userId); // Another reported user

            UserStats stats = statsService.computeAndSaveStats(userId);

            assertEquals(1, stats.reportsGiven());
            assertEquals(2, stats.reportsReceived());
        }

        @Test
        @DisplayName("Saves stats to storage")
        void savesStatsToStorage() {
            statsService.computeAndSaveStats(userId);

            Optional<UserStats> saved = userStatsStorage.getLatest(userId);
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
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);
            UserStats first = statsService.computeAndSaveStats(userId);

            // Add more likes but don't recompute
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);

            // Should return cached (1 like, not 2)
            UserStats cached = statsService.getOrComputeStats(userId);

            assertEquals(1, cached.likesGiven()); // Still shows old count
        }

        @Test
        @DisplayName("Computes new stats if none exist")
        void computesNewIfNoneExist() {
            likeStorage.addLike(userId, UUID.randomUUID(), Like.Direction.LIKE);

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
                likeStorage.addLike(user1, UUID.randomUUID(), Like.Direction.LIKE);
            }
            likeStorage.addLike(UUID.randomUUID(), user1, Like.Direction.LIKE);
            likeStorage.addLike(UUID.randomUUID(), user1, Like.Direction.LIKE);
            matchStorage.save(Match.create(user1, UUID.randomUUID()));

            // User 2: 2 likes given, 4 received
            likeStorage.addLike(user2, UUID.randomUUID(), Like.Direction.LIKE);
            likeStorage.addLike(user2, UUID.randomUUID(), Like.Direction.LIKE);
            for (int i = 0; i < 4; i++) {
                likeStorage.addLike(UUID.randomUUID(), user2, Like.Direction.LIKE);
            }
            matchStorage.save(Match.create(user2, UUID.randomUUID()));
            matchStorage.save(Match.create(user2, UUID.randomUUID()));

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

            Optional<PlatformStats> saved = platformStatsStorage.getLatest();
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

    // ============================================================
    // IN-MEMORY MOCK STORAGES
    // ============================================================

    private static class InMemoryLikeStorage implements LikeStorage {
        private final List<Like> likes = new ArrayList<>();

        void addLike(UUID from, UUID to, Like.Direction direction) {
            likes.add(Like.create(from, to, direction));
        }

        @Override
        public void save(Like like) {
            likes.add(like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return likes.stream()
                    .anyMatch(l -> l.whoLikes().equals(from) && l.whoGotLiked().equals(to));
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            boolean aLikesB = likes.stream()
                    .anyMatch(l -> l.whoLikes().equals(a)
                            && l.whoGotLiked().equals(b)
                            && l.direction() == Like.Direction.LIKE);
            boolean bLikesA = likes.stream()
                    .anyMatch(l -> l.whoLikes().equals(b)
                            && l.whoGotLiked().equals(a)
                            && l.direction() == Like.Direction.LIKE);
            return aLikesB && bLikesA;
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like l : likes) {
                if (l.whoLikes().equals(userId)) {
                    result.add(l.whoGotLiked());
                }
            }
            return result;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like l : likes) {
                if (l.whoGotLiked().equals(userId) && l.direction() == Like.Direction.LIKE) {
                    result.add(l.whoLikes());
                }
            }
            return result;
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            Map<UUID, Instant> result = new HashMap<>();
            for (Like l : likes) {
                if (l.whoGotLiked().equals(userId) && l.direction() == Like.Direction.LIKE) {
                    result.put(l.whoLikes(), l.createdAt());
                }
            }
            return result;
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.stream()
                    .filter(l -> l.whoGotLiked().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && mutualLikeExists(userId, l.whoGotLiked()))
                    .count();
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return likes.stream()
                    .filter(l ->
                            l.whoLikes().equals(fromUserId) && l.whoGotLiked().equals(toUserId))
                    .findFirst();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && l.createdAt().isAfter(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.PASS
                            && l.createdAt().isAfter(startOfDay))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.removeIf(l -> l.id().equals(likeId));
        }
    }

    private static class InMemoryMatchStorage implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

        @Override
        public void save(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public void update(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Match> get(String matchId) {
            return Optional.ofNullable(matches.get(matchId));
        }

        @Override
        public boolean exists(String matchId) {
            return matches.containsKey(matchId);
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.remove(matchId);
        }
    }

    private static class InMemoryBlockStorage implements BlockStorage {
        private final List<Block> blocks = new ArrayList<>();

        void addBlock(UUID blocker, UUID blocked) {
            blocks.add(Block.create(blocker, blocked));
        }

        @Override
        public void save(Block block) {
            blocks.add(block);
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.stream()
                    .anyMatch(b -> (b.blockerId().equals(userA) && b.blockedId().equals(userB))
                            || (b.blockerId().equals(userB) && b.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Block b : blocks) {
                if (b.blockerId().equals(userId)) {
                    result.add(b.blockedId());
                } else if (b.blockedId().equals(userId)) {
                    result.add(b.blockerId());
                }
            }
            return result;
        }

        @Override
        public List<Block> findByBlocker(UUID blockerId) {
            return blocks.stream().filter(b -> b.blockerId().equals(blockerId)).toList();
        }

        @Override
        public boolean delete(UUID blockerId, UUID blockedId) {
            return blocks.removeIf(
                    b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId));
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockerId().equals(userId)).count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockedId().equals(userId)).count();
        }
    }

    private static class InMemoryReportStorage implements ReportStorage {
        private final List<Report> reports = new ArrayList<>();

        void addReport(UUID reporter, UUID reported) {
            reports.add(Report.create(reporter, reported, Report.Reason.INAPPROPRIATE_CONTENT, null));
        }

        @Override
        public void save(Report report) {
            reports.add(report);
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return reports.stream()
                    .anyMatch(r -> r.reporterId().equals(reporterId)
                            && r.reportedUserId().equals(reportedUserId));
        }

        @Override
        public int countReportsBy(UUID userId) {
            return (int)
                    reports.stream().filter(r -> r.reporterId().equals(userId)).count();
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return (int) reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .count();
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .toList();
        }
    }

    private static class InMemoryUserStatsStorage implements UserStatsStorage {
        private final Map<UUID, UserStats> stats = new HashMap<>();

        @Override
        public void save(UserStats userStats) {
            stats.put(userStats.userId(), userStats);
        }

        @Override
        public Optional<UserStats> getLatest(UUID userId) {
            return Optional.ofNullable(stats.get(userId));
        }

        @Override
        public List<UserStats> getHistory(UUID userId, int limit) {
            UserStats s = stats.get(userId);
            return s == null ? List.of() : List.of(s);
        }

        @Override
        public List<UserStats> getAllLatestStats() {
            return new ArrayList<>(stats.values());
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            // No-op for tests - all stats are fresh
            return 0;
        }
    }

    private static class InMemoryPlatformStatsStorage implements PlatformStatsStorage {
        private PlatformStats latest;

        @Override
        public void save(PlatformStats stats) {
            this.latest = stats;
        }

        @Override
        public Optional<PlatformStats> getLatest() {
            return Optional.ofNullable(latest);
        }

        @Override
        public List<PlatformStats> getHistory(int limit) {
            return latest == null ? List.of() : List.of(latest);
        }
    }
}
