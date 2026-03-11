package datingapp.ui.viewmodel;

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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("StatsViewModel behavior")
class StatsViewModelTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
    }

    @AfterEach
    void tearDown() {
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("refresh loads achievements and zero-state metrics")
    void refreshLoadsAchievementsAndZeroStateMetrics() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        StatsViewModel viewModel = new StatsViewModel(
                new SingleAchievementService(currentUser.getId()),
                new ActivityMetricsService(interactions, trustSafety, analytics, config),
                new ConnectionService(config, communications, interactions, users),
                AppSession.getInstance());

        viewModel.initialize();

        assertTrue(waitUntil(() -> viewModel.getAchievements().size() == 1, 5000));
        assertEquals(0, viewModel.totalLikesGivenProperty().get());
        assertEquals(0, viewModel.totalLikesReceivedProperty().get());
        assertEquals(0, viewModel.totalMatchesProperty().get());
        assertEquals("--", viewModel.responseRateProperty().get());

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
        user.setBio("Stats user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }

    private static void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout waiting for FX thread");
        }
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
