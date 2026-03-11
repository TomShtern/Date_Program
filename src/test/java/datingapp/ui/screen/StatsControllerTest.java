package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.StatsViewModel;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("StatsController wiring and bindings")
class StatsControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("FXML binds stats labels and achievement list")
    void fxmlBindsStatsLabelsAndAchievementList() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        AchievementService achievementService = new SingleAchievementService(currentUser.getId());
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactions, trustSafety, analytics, config);
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        StatsViewModel viewModel = new StatsViewModel(
                achievementService, activityMetricsService, connectionService, AppSession.getInstance());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/stats.fxml", () -> new StatsController(viewModel));
        Parent root = loaded.root();
        Label totalLikesLabel = JavaFxTestSupport.lookup(root, "#totalLikesLabel", Label.class);
        @SuppressWarnings("unchecked")
        ListView<Achievement> achievementListView =
                JavaFxTestSupport.lookup(root, "#achievementListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> achievementListView.getItems().size())
                                == 1;
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals("0", JavaFxTestSupport.callOnFxAndWait(totalLikesLabel::getText));

        viewModel.dispose();
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name.replace(' ', '-') + ".jpg");
        user.setBio("Stats bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static final class SingleAchievementService implements AchievementService {
        private final UUID userId;

        private SingleAchievementService(UUID userId) {
            this.userId = userId;
        }

        @Override
        public List<UserAchievement> checkAndUnlock(UUID ignoredUserId) {
            return getUnlocked(ignoredUserId);
        }

        @Override
        public List<UserAchievement> getUnlocked(UUID ignoredUserId) {
            return List.of(UserAchievement.create(userId, Achievement.FIRST_SPARK));
        }

        @Override
        public List<AchievementProgress> getProgress(UUID ignoredUserId) {
            return List.of();
        }

        @Override
        public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID ignoredUserId) {
            return Map.of();
        }

        @Override
        public int countUnlocked(UUID ignoredUserId) {
            return 1;
        }
    }
}
