package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for StatsHandler CLI commands: viewStatistics() and
 * viewAchievements().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class StatsHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private StatsHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        ActivityMetricsService statsService = new ActivityMetricsService(
                interactionStorage, trustSafetyStorage, analyticsStorage, AppConfig.defaults());
        ProfileService achievementService = new ProfileService(
                AppConfig.defaults(), analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
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
            assertTrue(analyticsStorage.getLatestUserStats(testUser.getId()).isPresent());
        }

        @Test
        @DisplayName("Uses cached stats if recent")
        void usesCachedStatsIfRecent() {
            // Pre-populate stats
            UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
            builder.likesGiven = 10;
            UserStats stats = UserStats.create(testUser.getId(), builder);
            analyticsStorage.saveUserStats(stats);

            StatsHandler handler = createHandler("\n");
            handler.viewStatistics();

            // Should have used existing stats (only 1 saved)
            assertEquals(
                    1,
                    analyticsStorage.getUserStatsHistory(testUser.getId(), 10).size());
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            StatsHandler handler = createHandler("\n");

            handler.viewStatistics();

            // Should return early without creating stats
            assertFalse(analyticsStorage.getLatestUserStats(testUser.getId()).isPresent());
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
            analyticsStorage.saveUserAchievement(achievement);

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
            interactionStorage.save(match);

            StatsHandler handler = createHandler("\n");
            handler.viewAchievements();

            // FIRST_SPARK should be unlocked
            assertTrue(analyticsStorage.hasAchievement(testUser.getId(), Achievement.FIRST_SPARK));
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            StatsHandler handler = createHandler("\n");

            handler.viewAchievements();

            // Should not check achievements
            assertFalse(analyticsStorage.hasAchievement(testUser.getId(), Achievement.FIRST_SPARK));
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
        user.setPacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.TEXT_ONLY,
                datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }
}
