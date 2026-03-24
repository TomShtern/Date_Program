package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.MatchingViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("MatchingController wiring and binding tests")
class MatchingControllerTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = new UiThreadDispatcher() {
        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void dispatch(Runnable action) {
            action.run();
        }
    };

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("FXML handoff loads prioritized candidate note and note actions stay wired")
    void prioritizedCandidateNoteUiRemainsWired() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();
        fixture.saveNote("Existing note");

        NavigationService.getInstance()
                .setNavigationContext(NavigationService.ViewType.MATCHING, fixture.prioritizedCandidate.getId());

        MatchingViewModel viewModel = fixture.createViewModel();
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matching.fxml", () -> new MatchingController(viewModel));
        Parent root = loaded.root();
        TextArea noteTextArea = JavaFxTestSupport.lookup(root, "#noteTextArea", TextArea.class);
        Button saveNoteButton = JavaFxTestSupport.lookup(root, "#saveNoteButton", Button.class);
        Button deleteNoteButton = JavaFxTestSupport.lookup(root, "#deleteNoteButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return viewModel.currentCandidateProperty().get() != null
                                && fixture.prioritizedCandidate
                                        .getId()
                                        .equals(viewModel
                                                .currentCandidateProperty()
                                                .get()
                                                .getId())
                                && "Existing note".equals(JavaFxTestSupport.callOnFxAndWait(noteTextArea::getText));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(saveNoteButton::isDisabled);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            noteTextArea.setText("Updated note from controller");
            saveNoteButton.fire();
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return "Private note saved."
                                .equals(JavaFxTestSupport.callOnFxAndWait(viewModel.noteStatusMessageProperty()::get));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> fixture.lookupNote()
                        .map(ProfileNote::content)
                        .filter("Updated note from controller"::equals)
                        .isPresent(),
                5000));

        JavaFxTestSupport.runOnFxAndWait(deleteNoteButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(() -> fixture.lookupNote().isEmpty(), 5000));

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("like button still advances to the next candidate after animated exit")
    void likeButtonStillAdvancesAfterAnimation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matching.fxml", () -> new MatchingController(viewModel));
        Parent root = loaded.root();
        Button likeButton = JavaFxTestSupport.lookup(root, "#likeButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.currentCandidateProperty().get() != null
                        && fixture.prioritizedCandidate
                                .getId()
                                .equals(viewModel
                                        .currentCandidateProperty()
                                        .get()
                                        .getId()),
                5000));

        JavaFxTestSupport.runOnFxAndWait(likeButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.currentCandidateProperty().get() != null
                        && fixture.fallbackCandidate
                                .getId()
                                .equals(viewModel
                                        .currentCandidateProperty()
                                        .get()
                                        .getId()),
                5000));

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("super like button still advances to the next candidate after animated exit")
    void superLikeButtonStillAdvancesAfterAnimation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matching.fxml", () -> new MatchingController(viewModel));
        Parent root = loaded.root();
        Button superLikeButton = JavaFxTestSupport.lookup(root, "#superLikeButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.currentCandidateProperty().get() != null
                        && fixture.prioritizedCandidate
                                .getId()
                                .equals(viewModel
                                        .currentCandidateProperty()
                                        .get()
                                        .getId()),
                5000));

        JavaFxTestSupport.runOnFxAndWait(superLikeButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.currentCandidateProperty().get() != null
                        && fixture.fallbackCandidate
                                .getId()
                                .equals(viewModel
                                        .currentCandidateProperty()
                                        .get()
                                        .getId()),
                5000));

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("undo button becomes enabled and shows countdown after swipe")
    void undoButtonBecomesEnabledAndShowsCountdownAfterSwipe() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matching.fxml", () -> new MatchingController(viewModel));
        Parent root = loaded.root();
        Button likeButton = JavaFxTestSupport.lookup(root, "#likeButton", Button.class);
        Button undoButton = JavaFxTestSupport.lookup(root, "#undoButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.currentCandidateProperty().get() != null, 5000));

        JavaFxTestSupport.runOnFxAndWait(likeButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        Tooltip tooltip = JavaFxTestSupport.callOnFxAndWait(undoButton::getTooltip);
                        return !JavaFxTestSupport.callOnFxAndWait(undoButton::isDisabled)
                                && tooltip != null
                                && tooltip.getText().contains("remaining");
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("empty state shows actionable location guidance when seeker has no location")
    void emptyStateShowsLocationGuidanceWhenSeekerHasNoLocation() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/matching.fxml", () -> new MatchingController(viewModel));
        Parent root = loaded.root();
        Label noCandidatesHeading = JavaFxTestSupport.lookup(root, "#noCandidatesHeading", Label.class);
        Label noCandidatesBody = JavaFxTestSupport.lookup(root, "#noCandidatesBody", Label.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return "Location not set"
                                        .equals(JavaFxTestSupport.callOnFxAndWait(noCandidatesHeading::getText))
                                && JavaFxTestSupport.callOnFxAndWait(noCandidatesBody::getText)
                                        .contains("Add your location in your profile");
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertEquals("Location not set", JavaFxTestSupport.callOnFxAndWait(noCandidatesHeading::getText));
        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    private static final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        private final TestStorages.Communications communications = new TestStorages.Communications();
        private final TestStorages.Analytics analytics = new TestStorages.Analytics();
        private final TestStorages.Standouts standouts = new TestStorages.Standouts();
        private final TestStorages.Undos undos = new TestStorages.Undos();
        private final InProcessAppEventBus eventBus = new InProcessAppEventBus();
        private final AppConfig config = AppConfig.defaults();
        private final User currentUser = createUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE));
        private final User prioritizedCandidate = createUser("Blair", Gender.FEMALE, EnumSet.of(Gender.MALE));
        private final User fallbackCandidate = createUser("Casey", Gender.FEMALE, EnumSet.of(Gender.MALE));

        private Fixture() {
            this(true);
        }

        private Fixture(boolean withCurrentUserLocation) {
            if (withCurrentUserLocation) {
                currentUser.setLocation(40.7128, -74.0060);
            }
            prioritizedCandidate.setLocation(40.7130, -74.0050);
            fallbackCandidate.setLocation(40.7140, -74.0040);
            AppSession.getInstance().setCurrentUser(currentUser);
        }

        private void saveUsers() {
            users.save(currentUser);
            users.save(prioritizedCandidate);
            users.save(fallbackCandidate);
        }

        private MatchingViewModel createViewModel() {
            CandidateFinder candidateFinder =
                    new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
            ProfileService profileService =
                    new ProfileService(config, analytics, interactions, trustSafetyStorage, users);
            RecommendationService recommendationService = RecommendationService.builder()
                    .interactionStorage(interactions)
                    .userStorage(users)
                    .trustSafetyStorage(trustSafetyStorage)
                    .analyticsStorage(analytics)
                    .candidateFinder(candidateFinder)
                    .standoutStorage(standouts)
                    .profileService(profileService)
                    .config(config)
                    .build();
            UndoService undoService = new UndoService(interactions, undos, config);
            MatchingService matchingService = MatchingService.builder()
                    .interactionStorage(interactions)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(users)
                    .undoService(undoService)
                    .dailyService(recommendationService)
                    .candidateFinder(candidateFinder)
                    .build();
            TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                            trustSafetyStorage, interactions, users, config, communications)
                    .build();
            ProfileService noteProfileService =
                    new ProfileService(config, analytics, interactions, trustSafetyStorage, users);
            var noteUseCases = new datingapp.app.usecase.profile.ProfileUseCases(
                    users,
                    noteProfileService,
                    null,
                    null,
                    null,
                    config,
                    new datingapp.core.workflow.ProfileActivationPolicy(),
                    eventBus);

            return new MatchingViewModel(
                    new MatchingViewModel.Dependencies(
                            candidateFinder,
                            matchingService,
                            undoService,
                            trustSafetyService,
                            new datingapp.app.usecase.matching.MatchingUseCases(
                                    candidateFinder,
                                    matchingService,
                                    datingapp.app.usecase.matching.MatchingUseCases.wrapDailyLimitService(
                                            recommendationService),
                                    datingapp.app.usecase.matching.MatchingUseCases.wrapDailyPickService(
                                            recommendationService),
                                    datingapp.app.usecase.matching.MatchingUseCases.wrapStandoutService(
                                            recommendationService),
                                    undoService,
                                    interactions,
                                    users,
                                    new MatchQualityService(users, interactions, config),
                                    eventBus),
                            new datingapp.app.usecase.social.SocialUseCases(trustSafetyService),
                            new UseCaseUiProfileNoteDataAccess(noteUseCases)),
                    AppSession.getInstance(),
                    TEST_DISPATCHER);
        }

        private void saveNote(String content) {
            users.saveProfileNote(ProfileNote.create(currentUser.getId(), prioritizedCandidate.getId(), content));
        }

        private java.util.Optional<ProfileNote> lookupNote() {
            return users.getProfileNote(currentUser.getId(), prioritizedCandidate.getId());
        }

        private static User createUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(27));
            user.setGender(gender);
            user.setInterestedIn(interestedIn);
            user.setAgeRange(20, 45, 18, 120);
            user.setMaxDistanceKm(100, AppConfig.defaults().matching().maxDistanceKm());
            user.addPhotoUrl("http://example.com/" + name + "-1.jpg");
            user.addPhotoUrl("http://example.com/" + name + "-2.jpg");
            user.setBio("Matching test user");
            user.setPacePreferences(new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEEP_CHAT));
            user.activate();
            return user;
        }
    }
}
