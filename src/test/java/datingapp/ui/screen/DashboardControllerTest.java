package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.dashboard.DashboardUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.RecommendationService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.DashboardViewModel;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("DashboardController wiring and bindings")
class DashboardControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("FXML binds daily pick details and viewed state")
    void fxmlBindsDailyPickDetailsAndViewedState() throws Exception {
        TestClock.setFixed(FIXED_INSTANT);

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        AppConfig config = AppConfig.defaults();

        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        ProfileService profileService = new ProfileService(users);
        RecommendationService dailyService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .build();

        ConnectionService messagingService = new ConnectionService(config, communications, interactions, users);
        AchievementService achievementService = TestAchievementService.empty();

        User currentUser = createActiveUser("DashboardUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        User candidate = createActiveUser("Daily Pick", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        users.save(candidate);
        AppSession.getInstance().setCurrentUser(currentUser);

        DashboardViewModel viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(new DashboardUseCases(
                        users,
                        dailyService,
                        interactions,
                        achievementService,
                        messagingService,
                        profileService,
                        config)),
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/dashboard.fxml", () -> new DashboardController(viewModel));
        Parent root = loaded.root();
        Label dailyPickLabel = JavaFxTestSupport.lookup(root, "#dailyPickLabel", Label.class);
        Label dailyPickSeenLabel = JavaFxTestSupport.lookup(root, "#dailyPickSeenLabel", Label.class);
        Button dailyPickButton = JavaFxTestSupport.lookup(root, "#dailyPickButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(dailyPickButton::isDisabled)
                                && JavaFxTestSupport.callOnFxAndWait(dailyPickLabel::getText)
                                        .contains(candidate.getName());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        viewModel.markDailyPickViewed();

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(dailyPickSeenLabel::isVisible);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals(candidate.getName() + ", 25", JavaFxTestSupport.callOnFxAndWait(dailyPickLabel::getText));

        viewModel.dispose();
    }

    @Test
    @DisplayName("FXML safely handles no daily pick available edge case")
    void fxmlHandlesNoDailyPickAvailable() throws Exception {
        TestClock.setFixed(FIXED_INSTANT);

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        AppConfig config = AppConfig.defaults();

        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        ProfileService profileService = new ProfileService(users);
        RecommendationService dailyService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .build();

        ConnectionService messagingService = new ConnectionService(config, communications, interactions, users);
        AchievementService achievementService = TestAchievementService.empty();

        User currentUser = createActiveUser("DashboardUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        DashboardViewModel viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(new DashboardUseCases(
                        users,
                        dailyService,
                        interactions,
                        achievementService,
                        messagingService,
                        profileService,
                        config)),
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/dashboard.fxml", () -> new DashboardController(viewModel));
        Parent root = loaded.root();
        Label dailyPickLabel = JavaFxTestSupport.lookup(root, "#dailyPickLabel", Label.class);
        Label dailyPickSeenLabel = JavaFxTestSupport.lookup(root, "#dailyPickSeenLabel", Label.class);
        Button dailyPickButton = JavaFxTestSupport.lookup(root, "#dailyPickButton", Button.class);

        // Wait for viewModel to attempt daily pick loading and UI to stabilize:
        // button disabled + label populated with fallback text
        assertTrue(
                JavaFxTestSupport.waitUntil(
                        () -> {
                            try {
                                boolean buttonDisabled = JavaFxTestSupport.callOnFxAndWait(dailyPickButton::isDisabled);
                                String labelText = JavaFxTestSupport.callOnFxAndWait(dailyPickLabel::getText);
                                return buttonDisabled && labelText != null && !labelText.isEmpty();
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                        },
                        5000),
                "UI should stabilize with disabled button and non-empty label text");

        // Verify button remains disabled with no candidate
        assertTrue(
                JavaFxTestSupport.callOnFxAndWait(dailyPickButton::isDisabled),
                "Daily pick button should stay disabled when no candidate available");

        // Verify label shows deterministic fallback text (not null, not crash)
        String labelText = JavaFxTestSupport.callOnFxAndWait(dailyPickLabel::getText);
        assertTrue(
                labelText != null && !labelText.isEmpty(),
                "Daily pick label should show fallback/empty-safe text, not null or empty");

        // Verify seen label is not visible
        boolean seenLabelVisible = JavaFxTestSupport.callOnFxAndWait(dailyPickSeenLabel::isVisible);
        assertFalse(seenLabelVisible, "Daily pick seen label should not be visible when no daily pick available");

        viewModel.dispose();
    }

    @Test
    @DisplayName("setCompactMode toggles compact style and secondary stats visibility")
    void setCompactModeTogglesCompactStyleAndSecondaryStatsVisibility() throws Exception {
        TestClock.setFixed(FIXED_INSTANT);

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        AppConfig config = AppConfig.defaults();

        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        ProfileService profileService = new ProfileService(users);
        RecommendationService dailyService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .build();

        ConnectionService messagingService = new ConnectionService(config, communications, interactions, users);
        AchievementService achievementService = TestAchievementService.empty();

        User currentUser = createActiveUser("DashboardUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        DashboardViewModel viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(new DashboardUseCases(
                        users,
                        dailyService,
                        interactions,
                        achievementService,
                        messagingService,
                        profileService,
                        config)),
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/dashboard.fxml", () -> new DashboardController(viewModel));
        Parent root = loaded.root();
        DashboardController controller = (DashboardController) loaded.controller();
        Label totalMatchesLabel = JavaFxTestSupport.lookup(root, "#totalMatchesLabel", Label.class);

        assertFalse(JavaFxTestSupport.callOnFxAndWait(() -> root.getStyleClass().contains("viewport-compact")));

        JavaFxTestSupport.runOnFxAndWait(() -> controller.setCompactMode(true));

        assertTrue(JavaFxTestSupport.callOnFxAndWait(() -> root.getStyleClass().contains("viewport-compact")));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(
                () -> totalMatchesLabel.getParent().isVisible()));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(
                () -> totalMatchesLabel.getParent().isManaged()));

        JavaFxTestSupport.runOnFxAndWait(() -> controller.setCompactMode(false));

        assertFalse(JavaFxTestSupport.callOnFxAndWait(() -> root.getStyleClass().contains("viewport-compact")));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(
                () -> totalMatchesLabel.getParent().isVisible()));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(
                () -> totalMatchesLabel.getParent().isManaged()));

        viewModel.dispose();
    }

    @Test
    @DisplayName("cleanup stops pending achievement celebration timers")
    void cleanupStopsPendingAchievementCelebrationTimers() throws Exception {
        DashboardController controller = new DashboardController(null);
        PauseTransition removalDelay = new PauseTransition(Duration.seconds(5));
        PauseTransition firstStagger = new PauseTransition(Duration.seconds(5));
        PauseTransition secondStagger = new PauseTransition(Duration.seconds(5));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            removalDelay.play();
            firstStagger.play();
            secondStagger.play();
        });

        setPrivateField(controller, "achievementRemovalDelay", removalDelay);
        setPrivateField(controller, "achievementPopupStaggers", new ArrayList<>(List.of(firstStagger, secondStagger)));

        JavaFxTestSupport.runOnFxAndWait(controller::cleanup);

        assertEquals(Animation.Status.STOPPED, JavaFxTestSupport.callOnFxAndWait(removalDelay::getStatus));
        assertEquals(Animation.Status.STOPPED, JavaFxTestSupport.callOnFxAndWait(firstStagger::getStatus));
        assertEquals(Animation.Status.STOPPED, JavaFxTestSupport.callOnFxAndWait(secondStagger::getStatus));
        assertTrue(getPrivateList(controller, "achievementPopupStaggers").isEmpty());
        assertEquals(null, getPrivateField(controller, "achievementRemovalDelay"));
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = DashboardController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static List<PauseTransition> getPrivateList(Object target, String fieldName) throws Exception {
        Field field = DashboardController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<PauseTransition>) field.get(target);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = DashboardController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static User createActiveUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name.replace(' ', '-') + ".jpg");
        user.setBio("Dashboard bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
