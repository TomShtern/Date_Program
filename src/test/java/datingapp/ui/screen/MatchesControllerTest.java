package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.MatchesViewModel;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.animation.Animation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("MatchesController wiring and binding tests")
class MatchesControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("FXML renders match actions and message button sets chat navigation context")
    void rendersMatchActionsAndMessageButtonSetsChatNavigationContext() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedSingleMatch();

        TrackingMatchesController controller = new TrackingMatchesController(fixture.viewModel);

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/matches.fxml", () -> controller);
        Parent root = loaded.root();

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> !fixture.viewModel.getMatches().isEmpty(), 5000));

        Button messageButton = JavaFxTestSupport.findButtonByText(root, "Message");
        JavaFxTestSupport.findButtonByText(root, "Friend zone");
        JavaFxTestSupport.findButtonByText(root, "Graceful exit");
        JavaFxTestSupport.findButtonByText(root, "Unmatch");
        JavaFxTestSupport.findButtonByText(root, "Block");
        JavaFxTestSupport.findButtonByText(root, "Report");

        JavaFxTestSupport.runOnFxAndWait(messageButton::fire);

        UUID targetUserId = JavaFxTestSupport.callOnFxAndWait(controller::chatTargetUserId);
        assertNotNull(targetUserId);
        assertEquals(fixture.otherUser.getId(), targetUserId);

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("cleanup clears observable particle resources and remains safe")
    void cleanupClearsObservableParticleResourcesAndRemainsSafe() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedCurrentUserOnly();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matches.fxml", () -> new MatchesController(fixture.viewModel));
        MatchesController controller = (MatchesController) loaded.controller();
        Pane particleLayer = JavaFxTestSupport.callOnFxAndWait(() -> getPrivateField(controller, "particleLayer"));

        assertNotNull(particleLayer);

        JavaFxTestSupport.runOnFxAndWait(() -> {
            controller.cleanup();
            controller.cleanup();
        });

        assertTrue(JavaFxTestSupport.callOnFxAndWait(particleLayer::getChildren).isEmpty());

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("new match badge pulse is tracked and stopped on cleanup")
    void newMatchBadgePulseIsTrackedAndStoppedOnCleanup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedSingleMatch();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matches.fxml", () -> new MatchesController(fixture.viewModel));
        MatchesController controller = (MatchesController) loaded.controller();

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> !fixture.viewModel.getMatches().isEmpty(), 5000));

        List<Animation> trackedAnimations = getTrackedAnimations(controller);
        assertTrue(!trackedAnimations.isEmpty(), "new match badge animation should be tracked for cleanup");

        JavaFxTestSupport.runOnFxAndWait(controller::cleanup);

        for (Animation animation : trackedAnimations) {
            assertEquals(Animation.Status.STOPPED, JavaFxTestSupport.callOnFxAndWait(animation::getStatus));
        }

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    private static final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.Communications communications = new TestStorages.Communications();
        private final TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        private final AppConfig config = AppConfig.defaults();
        private final User currentUser = createActiveUser("Current");
        private final User otherUser = createActiveUser("MatchedUser");
        private final MatchesViewModel viewModel;

        private Fixture() {
            var analyticsStorage = new TestStorages.Analytics();
            var candidateFinder = new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
            var standoutStorage = new TestStorages.Standouts();
            var profileService = new ProfileService(users);

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

            var undoService = new datingapp.core.matching.UndoService(interactions, new TestStorages.Undos(), config);

            MatchingService matchingService = MatchingService.builder()
                    .interactionStorage(interactions)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(users)
                    .undoService(undoService)
                    .dailyService(dailyService)
                    .candidateFinder(candidateFinder)
                    .build();

            var matchQualityService = new MatchQualityService(users, interactions, config);
            var matchingUseCases = new datingapp.app.usecase.matching.MatchingUseCases(
                    candidateFinder,
                    matchingService,
                    datingapp.app.usecase.matching.MatchingUseCases.wrapDailyLimitService(dailyService),
                    datingapp.app.usecase.matching.MatchingUseCases.wrapDailyPickService(dailyService),
                    datingapp.app.usecase.matching.MatchingUseCases.wrapStandoutService(dailyService),
                    undoService,
                    interactions,
                    users,
                    matchQualityService,
                    new datingapp.app.event.InProcessAppEventBus(),
                    dailyService);
            TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                            trustSafetyStorage, interactions, users, config, communications)
                    .build();
            var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(
                    new ConnectionService(config, communications, interactions, users),
                    trustSafetyService,
                    communications);
            var profileUseCases = new datingapp.app.usecase.profile.ProfileUseCases(
                    users,
                    profileService,
                    new ValidationService(config),
                    null,
                    TestAchievementService.empty(),
                    config,
                    new datingapp.core.workflow.ProfileActivationPolicy(),
                    new datingapp.app.event.InProcessAppEventBus());

            this.viewModel = new MatchesViewModel(
                    new MatchesViewModel.Dependencies(
                            dailyService, matchingUseCases, profileUseCases, socialUseCases, config),
                    AppSession.getInstance(),
                    JavaFxTestSupport.blockingUiDispatcher());
        }

        private void seedCurrentUserOnly() {
            users.save(currentUser);
            AppSession.getInstance().setCurrentUser(currentUser);
        }

        private void seedSingleMatch() {
            users.save(currentUser);
            users.save(otherUser);
            interactions.save(Match.create(currentUser.getId(), otherUser.getId()));
            AppSession.getInstance().setCurrentUser(currentUser);
        }

        private void dispose() {
            viewModel.dispose();
        }

        private static User createActiveUser(String name) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(25));
            user.setGender(Gender.OTHER);
            user.setInterestedIn(EnumSet.of(Gender.OTHER));
            user.setAgeRange(18, 60, 18, 120);
            user.setMaxDistanceKm(50, 500);
            user.setLocation(40.7128, -74.0060);
            user.addPhotoUrl("http://example.com/" + name + ".jpg");
            user.setBio("Bio");
            user.setPacePreferences(new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEEP_CHAT));
            user.activate();
            return user;
        }
    }

    private static final class TrackingMatchesController extends MatchesController {
        private UUID chatTargetUserId;

        private TrackingMatchesController(MatchesViewModel viewModel) {
            super(viewModel);
        }

        @Override
        protected void navigateToChat(UUID userId) {
            chatTargetUserId = userId;
        }

        private UUID chatTargetUserId() {
            return chatTargetUserId;
        }
    }

    private static Pane getPrivateField(MatchesController controller, String fieldName) throws Exception {
        Field field = MatchesController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Pane) field.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static List<Animation> getTrackedAnimations(MatchesController controller) throws Exception {
        Field field = BaseController.class.getDeclaredField("animations");
        field.setAccessible(true);
        return List.copyOf((List<Animation>) field.get(controller));
    }
}
