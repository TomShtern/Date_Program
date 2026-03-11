package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.DashboardViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactions, trustSafetyStorage, users);
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

        User currentUser = createActiveUser("DashboardUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        User candidate = createActiveUser("Daily Pick", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        users.save(candidate);
        AppSession.getInstance().setCurrentUser(currentUser);

        DashboardViewModel viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(
                        dailyService,
                        new StorageUiMatchDataAccess(interactions, trustSafetyStorage),
                        profileService,
                        messagingService,
                        profileService,
                        config),
                AppSession.getInstance());

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
