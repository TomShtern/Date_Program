package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.*;
import datingapp.core.model.Achievement;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.Match;
import datingapp.core.model.Stats.UserStats;
import datingapp.core.model.User;
import datingapp.core.service.*;
import datingapp.core.service.AchievementService;
import datingapp.core.service.ProfileCompletionService;
import datingapp.core.service.StatsService;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for StatsHandler CLI commands: viewStatistics() and viewAchievements().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class StatsHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Likes likeStorage;
    private TestStorages.Matches matchStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private InMemoryStatsStorage statsStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        likeStorage = new TestStorages.Likes();
        matchStorage = new TestStorages.Matches();
        trustSafetyStorage = new TestStorages.TrustSafety();
        statsStorage = new InMemoryStatsStorage();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private StatsHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        StatsService statsService = new StatsService(likeStorage, matchStorage, trustSafetyStorage, statsStorage);
        ProfileCompletionService profileCompletionService = new ProfileCompletionService(AppConfig.defaults());
        AchievementService achievementService = new AchievementService(
                statsStorage,
                matchStorage,
                likeStorage,
                userStorage,
                trustSafetyStorage,
                profileCompletionService,
                AppConfig.defaults());
        return new StatsHandler(statsService, achievementService, session, inputReader);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Statistics")
    class ViewStatistics {

        @Test
        @DisplayName("Displays statistics for logged-in user")
        void displaysStatsForLoggedInUser() {
            StatsHandler handler = createHandler("\n");

            // Should not throw - displays stats
            assertDoesNotThrow(handler::viewStatistics);
        }

        @Test
        @DisplayName("Creates fresh stats if none exist")
        void createsFreshStatsIfNoneExist() {
            StatsHandler handler = createHandler("\n");

            handler.viewStatistics();

            // Stats should have been computed and saved
            assertTrue(statsStorage.getLatestUserStats(testUser.getId()).isPresent());
        }

        @Test
        @DisplayName("Uses cached stats if recent")
        void usesCachedStatsIfRecent() {
            // Pre-populate stats
            UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
            builder.likesGiven = 10;
            UserStats stats = UserStats.create(testUser.getId(), builder);
            statsStorage.saveUserStats(stats);

            StatsHandler handler = createHandler("\n");
            handler.viewStatistics();

            // Should have used existing stats (only 1 saved)
            assertEquals(
                    1, statsStorage.getUserStatsHistory(testUser.getId(), 10).size());
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            StatsHandler handler = createHandler("\n");

            handler.viewStatistics();

            // Should return early without creating stats
            assertFalse(statsStorage.getLatestUserStats(testUser.getId()).isPresent());
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Achievements")
    class ViewAchievements {

        @Test
        @DisplayName("Shows message when no achievements unlocked")
        void showsMessageWhenNoAchievements() {
            StatsHandler handler = createHandler("\n");

            // Should not throw
            assertDoesNotThrow(handler::viewAchievements);
        }

        @Test
        @DisplayName("Displays unlocked achievements")
        void displaysUnlockedAchievements() {
            // Unlock an achievement
            UserAchievement achievement = UserAchievement.create(testUser.getId(), Achievement.FIRST_SPARK);
            statsStorage.saveUserAchievement(achievement);

            StatsHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewAchievements);
        }

        @Test
        @DisplayName("Checks and unlocks new achievements")
        void checksAndUnlocksNewAchievements() {
            // Create a match to unlock FIRST_SPARK
            User otherUser = createActiveUser("Other");
            userStorage.save(otherUser);
            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            StatsHandler handler = createHandler("\n");
            handler.viewAchievements();

            // FIRST_SPARK should be unlocked
            assertTrue(statsStorage.hasAchievement(testUser.getId(), Achievement.FIRST_SPARK));
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            StatsHandler handler = createHandler("\n");

            handler.viewAchievements();

            // Should not check achievements
            assertFalse(statsStorage.hasAchievement(testUser.getId(), Achievement.FIRST_SPARK));
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.OTHER);
        user.setInterestedIn(EnumSet.of(User.Gender.OTHER));
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new datingapp.core.model.Preferences.PacePreferences(
                datingapp.core.model.Preferences.PacePreferences.MessagingFrequency.OFTEN,
                datingapp.core.model.Preferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                datingapp.core.model.Preferences.PacePreferences.CommunicationStyle.TEXT_ONLY,
                datingapp.core.model.Preferences.PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }

    // === In-Memory Mock Storage ===

    private static class InMemoryStatsStorage implements datingapp.core.storage.StatsStorage {
        private final Map<UUID, List<UserStats>> userStatsMap = new HashMap<>();
        private final List<datingapp.core.model.Stats.PlatformStats> platformStats = new ArrayList<>();
        private final Map<UUID, Set<Achievement>> achievements = new HashMap<>();

        @Override
        public void saveUserStats(UserStats stats) {
            userStatsMap.computeIfAbsent(stats.userId(), k -> new ArrayList<>()).add(stats);
        }

        @Override
        public Optional<UserStats> getLatestUserStats(UUID userId) {
            List<UserStats> list = userStatsMap.get(userId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }

        @Override
        public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
            List<UserStats> list = userStatsMap.getOrDefault(userId, List.of());
            return list.subList(0, Math.min(list.size(), limit));
        }

        @Override
        public List<UserStats> getAllLatestUserStats() {
            return userStatsMap.values().stream()
                    .filter(l -> !l.isEmpty())
                    .map(l -> l.get(l.size() - 1))
                    .toList();
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void savePlatformStats(datingapp.core.model.Stats.PlatformStats stats) {
            platformStats.add(stats);
        }

        @Override
        public Optional<datingapp.core.model.Stats.PlatformStats> getLatestPlatformStats() {
            return platformStats.isEmpty()
                    ? Optional.empty()
                    : Optional.of(platformStats.get(platformStats.size() - 1));
        }

        @Override
        public List<datingapp.core.model.Stats.PlatformStats> getPlatformStatsHistory(int limit) {
            return platformStats.subList(0, Math.min(platformStats.size(), limit));
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
        public void saveUserAchievement(UserAchievement achievement) {
            achievements
                    .computeIfAbsent(achievement.userId(), k -> new HashSet<>())
                    .add(achievement.achievement());
        }

        @Override
        public List<UserAchievement> getUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, Set.of()).stream()
                    .map(a -> UserAchievement.create(userId, a))
                    .toList();
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.getOrDefault(userId, Set.of()).contains(achievement);
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, Set.of()).size();
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant cutoff) {
            return 0;
        }

        @Override
        public void markDailyPickAsViewed(UUID userId, java.time.LocalDate date) {}

        @Override
        public boolean isDailyPickViewed(UUID userId, java.time.LocalDate date) {
            return false;
        }

        @Override
        public int deleteDailyPickViewsOlderThan(java.time.LocalDate before) {
            return 0;
        }
    }
}
