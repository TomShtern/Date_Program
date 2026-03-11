package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
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
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("MatchingViewModel selected-candidate handoff")
class MatchingViewModelTest {

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

    private final AppSession session = AppSession.getInstance();

    @AfterEach
    void tearDown() {
        session.reset();
    }

    @Test
    @DisplayName("initialize prioritizes requested candidate when still eligible")
    void initializePrioritizesRequestedCandidateWhenStillEligible() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId());
        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize falls back and emits info message when requested candidate is unavailable")
    void initializeFallsBackWhenRequestedCandidateUnavailable() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(UUID.randomUUID());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);
        waitUntil(() -> viewModel.infoMessageProperty().get() != null, 5000);

        assertNotNull(viewModel.currentCandidateProperty().get());
        assertTrue(viewModel.infoMessageProperty().get().contains("no longer available"));
        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize loads existing private note for prioritized candidate")
    void initializeLoadsExistingPrivateNoteForCandidate() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();
        fixture.saveNote("Existing note");

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> "Existing note".equals(viewModel.noteContentProperty().get()), 5000);

        assertEquals("Existing note", viewModel.noteContentProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("save and delete note update matching note state")
    void saveAndDeleteCandidateNote() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);
        viewModel.noteContentProperty().set("Fresh private note");
        viewModel.saveCurrentCandidateNote();

        waitUntil(
                () -> "Private note saved."
                        .equals(viewModel.noteStatusMessageProperty().get()),
                5000);
        assertEquals(
                "Fresh private note",
                fixture.lookupNote().map(ProfileNote::content).orElseThrow());

        viewModel.deleteCurrentCandidateNote();

        waitUntil(
                () -> "Private note deleted."
                        .equals(viewModel.noteStatusMessageProperty().get()),
                5000);
        assertTrue(fixture.lookupNote().isEmpty());
        assertEquals("", viewModel.noteContentProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("rapid double like only advances one candidate")
    void rapidDoubleLikeOnlyAdvancesOneCandidate() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();
        QueuedUiDispatcher dispatcher = new QueuedUiDispatcher();

        MatchingViewModel viewModel = fixture.createViewModel(dispatcher);
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        drainUntil(() -> viewModel.currentCandidateProperty().get() != null, dispatcher, 5000);

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId(),
                "Prioritized candidate should be shown first");

        viewModel.like();
        viewModel.like();

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId(),
                "Candidate should not change until queued UI work is processed");

        dispatcher.drain();

        assertEquals(
                fixture.fallbackCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId(),
                "Rapid double-like should only advance to the next candidate once");
        assertTrue(viewModel.hasMoreCandidatesProperty().get(), "Next candidate should still be available");
        assertEquals(
                1,
                fixture.interactions.countByDirection(fixture.currentUser.getId(), Like.Direction.LIKE),
                "Only one like should be recorded for the original candidate");

        viewModel.dispose();
    }

    private static void drainUntil(
            CheckedBooleanSupplier condition, QueuedUiDispatcher dispatcher, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            dispatcher.drain();
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        dispatcher.drain();
        if (!condition.getAsBoolean()) {
            throw new IllegalStateException("Timed out waiting for queued UI work");
        }
    }

    private static void waitUntil(CheckedBooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        if (!condition.getAsBoolean()) {
            throw new IllegalStateException("Timed out waiting for condition");
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean();
    }

    private final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        private final TestStorages.Communications communications = new TestStorages.Communications();
        private final TestStorages.Analytics analytics = new TestStorages.Analytics();
        private final TestStorages.Standouts standouts = new TestStorages.Standouts();
        private final TestStorages.Undos undos = new TestStorages.Undos();
        private final AppConfig config = AppConfig.defaults();
        private final User currentUser = createUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE));
        private final User prioritizedCandidate = createUser("Blair", Gender.FEMALE, EnumSet.of(Gender.MALE));
        private final User fallbackCandidate = createUser("Casey", Gender.FEMALE, EnumSet.of(Gender.MALE));

        private Fixture() {
            currentUser.setLocation(40.7128, -74.0060);
            prioritizedCandidate.setLocation(40.7130, -74.0050);
            fallbackCandidate.setLocation(40.7140, -74.0040);
            session.setCurrentUser(currentUser);
        }

        private void saveUsers() {
            users.save(currentUser);
            users.save(prioritizedCandidate);
            users.save(fallbackCandidate);
        }

        private MatchingViewModel createViewModel() {
            return createViewModel(TEST_DISPATCHER);
        }

        private MatchingViewModel createViewModel(UiThreadDispatcher dispatcher) {
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
                    .build();
            TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                            trustSafetyStorage, interactions, users, config, communications)
                    .build();
            ProfileService noteProfileService =
                    new ProfileService(config, analytics, interactions, trustSafetyStorage, users);
            var noteUseCases =
                    new datingapp.app.usecase.profile.ProfileUseCases(users, noteProfileService, null, null, config);

            return new MatchingViewModel(
                    new MatchingViewModel.Dependencies(
                            candidateFinder,
                            matchingService,
                            undoService,
                            trustSafetyService,
                            new datingapp.app.usecase.matching.MatchingUseCases(
                                    candidateFinder, matchingService, undoService),
                            new datingapp.app.usecase.social.SocialUseCases(trustSafetyService),
                            new UseCaseUiProfileNoteDataAccess(noteUseCases)),
                    session,
                    dispatcher);
        }

        private void saveNote(String content) {
            users.saveProfileNote(ProfileNote.create(currentUser.getId(), prioritizedCandidate.getId(), content));
        }

        private java.util.Optional<ProfileNote> lookupNote() {
            return users.getProfileNote(currentUser.getId(), prioritizedCandidate.getId());
        }

        private User createUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(27));
            user.setGender(gender);
            user.setInterestedIn(interestedIn);
            user.setAgeRange(20, 45, 18, 120);
            user.setMaxDistanceKm(100, config.matching().maxDistanceKm());
            user.addPhotoUrl("http://example.com/" + name + ".jpg");
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

    private static final class QueuedUiDispatcher implements UiThreadDispatcher {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public boolean isUiThread() {
            return false;
        }

        @Override
        public void dispatch(Runnable action) {
            synchronized (pending) {
                pending.add(action);
            }
        }

        private void drain() {
            while (true) {
                Runnable next;
                synchronized (pending) {
                    next = pending.poll();
                }
                if (next == null) {
                    return;
                }
                next.run();
            }
        }
    }
}
