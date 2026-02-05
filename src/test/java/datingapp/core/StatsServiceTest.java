package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.UserStats;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

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
    private InMemoryStatsStorage statsStorage;
    private StatsService statsService;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        likeStorage = new InMemoryLikeStorage();
        matchStorage = new InMemoryMatchStorage();
        blockStorage = new InMemoryBlockStorage();
        reportStorage = new InMemoryReportStorage();
        statsStorage = new InMemoryStatsStorage();

        statsService = new StatsService(likeStorage, matchStorage, blockStorage, reportStorage, statsStorage);

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

            Optional<UserStats> saved = statsStorage.getLatestUserStats(userId);
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
            statsService.computeAndSaveStats(userId);

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

            Optional<PlatformStats> saved = statsStorage.getLatestPlatformStats();
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
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            List<Map.Entry<UUID, Instant>> result = new ArrayList<>();
            for (Like l : likes) {
                if (l.whoGotLiked().equals(userId) && l.direction() == Like.Direction.LIKE) {
                    result.add(Map.entry(l.whoLikes(), l.createdAt()));
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

    private static class InMemoryStatsStorage implements StatsStorage {
        private final Map<UUID, UserStats> userStats = new HashMap<>();
        private final Map<UUID, List<Achievement.UserAchievement>> achievements = new ConcurrentHashMap<>();
        private final Map<String, Integer> profileViews = new ConcurrentHashMap<>();
        private PlatformStats latestPlatformStats;

        @Override
        public void saveUserStats(UserStats stats) {
            userStats.put(stats.userId(), stats);
        }

        @Override
        public Optional<UserStats> getLatestUserStats(UUID userId) {
            return Optional.ofNullable(userStats.get(userId));
        }

        @Override
        public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
            UserStats s = userStats.get(userId);
            return s == null ? List.of() : List.of(s);
        }

        @Override
        public List<UserStats> getAllLatestUserStats() {
            return new ArrayList<>(userStats.values());
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            // No-op for tests - all stats are fresh
            return 0;
        }

        @Override
        public void savePlatformStats(PlatformStats stats) {
            this.latestPlatformStats = stats;
        }

        @Override
        public Optional<PlatformStats> getLatestPlatformStats() {
            return Optional.ofNullable(latestPlatformStats);
        }

        @Override
        public List<PlatformStats> getPlatformStatsHistory(int limit) {
            return latestPlatformStats == null ? List.of() : List.of(latestPlatformStats);
        }

        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {
            if (!viewerId.equals(viewedId)) {
                profileViews.merge(viewerId + "_" + viewedId, 1, Integer::sum);
            }
        }

        @Override
        public int getProfileViewCount(UUID userId) {
            return (int) profileViews.keySet().stream()
                    .filter(key -> key.endsWith("_" + userId))
                    .count();
        }

        @Override
        public int getUniqueViewerCount(UUID userId) {
            return getProfileViewCount(userId); // Same for in-memory test
        }

        @Override
        public List<UUID> getRecentViewers(UUID userId, int limit) {
            return Collections.emptyList(); // Not needed for stats tests
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return profileViews.containsKey(viewerId + "_" + viewedId);
        }

        @Override
        public void saveUserAchievement(Achievement.UserAchievement achievement) {
            achievements
                    .computeIfAbsent(achievement.userId(), k -> new ArrayList<>())
                    .add(achievement);
        }

        @Override
        public List<Achievement.UserAchievement> getUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, Collections.emptyList());
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.getOrDefault(userId, Collections.emptyList()).stream()
                    .anyMatch(ua -> ua.achievement() == achievement);
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, Collections.emptyList()).size();
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant cutoff) {
            return 0; // Not needed for stats tests
        }
    }
}
