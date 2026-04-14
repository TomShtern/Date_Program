package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
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
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("MatchingViewModel selected-candidate handoff")
class MatchingViewModelTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = datingapp.ui.JavaFxTestSupport.immediateUiDispatcher();

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

        assertTrue(viewModel.infoMessageProperty().get().contains("no longer available"));
        assertTrue(viewModel.hasMoreCandidatesProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize marks location missing when current user has no location")
    void initializeMarksLocationMissingWhenCurrentUserHasNoLocation() {
        Fixture fixture = new Fixture(false);
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(null);

        waitUntil(() -> viewModel.locationMissingProperty().get(), 5000);

        assertTrue(viewModel.locationMissingProperty().get());
        assertNull(viewModel.currentCandidateProperty().get());
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
    @DisplayName("distance display uses injected distance calculator")
    void distanceDisplayUsesInjectedDistanceCalculator() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel(
                TEST_DISPATCHER,
                new UseCaseUiProfileNoteDataAccess(fixture.createNoteUseCases().getProfileNotesUseCases()),
                (lat1, lon1, lat2, lon2) -> 4.2);
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        assertEquals("4.2 km away", viewModel.getDistanceDisplay(fixture.prioritizedCandidate));
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
    @DisplayName("delete note failure keeps note state intact")
    void deleteCurrentCandidateNoteFailureKeepsStateIntact() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel(TEST_DISPATCHER, new NoOpUiProfileNoteDataAccess());
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);
        viewModel.noteContentProperty().set("Staged note");

        viewModel.deleteCurrentCandidateNote();

        waitUntil(
                () -> "Failed to delete note"
                        .equals(viewModel.noteStatusMessageProperty().get()),
                5000);

        assertEquals("Staged note", viewModel.noteContentProperty().get());
        assertEquals(
                "Failed to delete note", viewModel.noteStatusMessageProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("successful swipe starts undo countdown state")
    void successfulSwipeStartsUndoCountdownState() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);
        viewModel.like();

        waitUntil(() -> viewModel.undoAvailableProperty().get(), 5000);

        assertTrue(viewModel.undoCountdownSecondsProperty().get() > 0);
        assertTrue(viewModel.undoAvailableProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("like does not block caller while swipe work is running")
    void likeDoesNotBlockCallerWhileSwipeWorkIsRunning() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();
        CountDownLatch swipeStarted = new CountDownLatch(1);
        CountDownLatch releaseSwipe = new CountDownLatch(1);

        MatchingViewModel viewModel =
                fixture.createBlockingViewModel(TEST_DISPATCHER, swipeStarted, releaseSwipe, null, null);
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                viewModel.like();
            } catch (Throwable throwable) {
                callerFailure.set(throwable);
            }
        });

        assertTrue(swipeStarted.await(5, TimeUnit.SECONDS));

        caller.join(250);

        assertNull(callerFailure.get());
        assertFalse(caller.isAlive(), "like() should return promptly and leave swipe work to asyncScope");

        releaseSwipe.countDown();
        waitUntil(() -> viewModel.undoAvailableProperty().get(), 5000);
        viewModel.dispose();
    }

    @Test
    @DisplayName("undo without recent swipe publishes info message")
    void undoWithoutRecentSwipePublishesInfoMessage() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);
        viewModel.undo();

        waitUntil(() -> viewModel.infoMessageProperty().get() != null, 5000);

        assertEquals("No recent swipe to undo", viewModel.infoMessageProperty().get());
        viewModel.dispose();
    }

    @Test
    @DisplayName("undo does not block caller while undo work is running")
    void undoDoesNotBlockCallerWhileUndoWorkIsRunning() throws Exception {
        Fixture fixture = new Fixture();
        fixture.saveUsers();
        CountDownLatch undoStarted = new CountDownLatch(1);
        CountDownLatch releaseUndo = new CountDownLatch(1);

        MatchingViewModel viewModel =
                fixture.createBlockingViewModel(TEST_DISPATCHER, null, null, undoStarted, releaseUndo);
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                viewModel.undo();
            } catch (Throwable throwable) {
                callerFailure.set(throwable);
            }
        });

        assertTrue(undoStarted.await(5, TimeUnit.SECONDS));

        caller.join(250);

        assertNull(callerFailure.get());
        assertFalse(caller.isAlive(), "undo() should return promptly and leave undo work to asyncScope");

        releaseUndo.countDown();
        waitUntil(() -> viewModel.infoMessageProperty().get() != null, 5000);
        assertEquals("No recent swipe to undo", viewModel.infoMessageProperty().get());
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

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId());

        dispatcher.setQueuedMode(true);

        viewModel.like();
        viewModel.like();

        waitUntil(
                () -> fixture.interactions.countByDirection(fixture.currentUser.getId(), Like.Direction.LIKE) == 1
                        && dispatcher.hasPending(),
                5000);

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId());

        dispatcher.drain();

        assertEquals(
                fixture.fallbackCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId());
        assertTrue(viewModel.hasMoreCandidatesProperty().get());
        assertEquals(1, fixture.interactions.countByDirection(fixture.currentUser.getId(), Like.Direction.LIKE));

        viewModel.dispose();
    }

    @Test
    @DisplayName("superLike records SUPER_LIKE and advances exactly once")
    void superLikeRecordsSuperLikeAndAdvancesExactlyOnce() {
        Fixture fixture = new Fixture();
        fixture.saveUsers();

        MatchingViewModel viewModel = fixture.createViewModel();
        viewModel.initialize(fixture.prioritizedCandidate.getId());

        waitUntil(() -> viewModel.currentCandidateProperty().get() != null, 5000);

        assertEquals(
                fixture.prioritizedCandidate.getId(),
                viewModel.currentCandidateProperty().get().getId());

        viewModel.superLike();

        waitUntil(
                () -> {
                    var current = viewModel.currentCandidateProperty().get();
                    return current != null && fixture.fallbackCandidate.getId().equals(current.getId());
                },
                5000);

        assertEquals(1, fixture.interactions.countByDirection(fixture.currentUser.getId(), Like.Direction.SUPER_LIKE));
        assertEquals(0, fixture.interactions.countByDirection(fixture.currentUser.getId(), Like.Direction.LIKE));

        viewModel.dispose();
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
        private final User currentUser;
        private final User prioritizedCandidate;
        private final User fallbackCandidate;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean withCurrentUserLocation) {
            currentUser = withCurrentUserLocation
                    ? createUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE), true)
                    : createStorageActiveUserWithoutLocation("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE));
            prioritizedCandidate = createUser("Blair", Gender.FEMALE, EnumSet.of(Gender.MALE), true);
            fallbackCandidate = createUser("Casey", Gender.FEMALE, EnumSet.of(Gender.MALE), true);
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
            return createViewModel(
                    dispatcher,
                    new UseCaseUiProfileNoteDataAccess(createNoteUseCases().getProfileNotesUseCases()));
        }

        private MatchingViewModel createViewModel(
                UiThreadDispatcher dispatcher, UiProfileNoteDataAccess noteDataAccess) {
            return createViewModel(dispatcher, noteDataAccess, CandidateFinder.GeoUtils::distanceKm);
        }

        private MatchingViewModel createViewModel(
                UiThreadDispatcher dispatcher,
                UiProfileNoteDataAccess noteDataAccess,
                MatchingViewModel.DistanceCalculator distanceCalculator) {
            return createViewModel(dispatcher, noteDataAccess, distanceCalculator, null, null, null, null);
        }

        private MatchingViewModel createBlockingViewModel(
                UiThreadDispatcher dispatcher,
                CountDownLatch swipeStarted,
                CountDownLatch releaseSwipe,
                CountDownLatch undoStarted,
                CountDownLatch releaseUndo) {
            return createViewModel(
                    dispatcher,
                    new UseCaseUiProfileNoteDataAccess(createNoteUseCases().getProfileNotesUseCases()),
                    CandidateFinder.GeoUtils::distanceKm,
                    swipeStarted,
                    releaseSwipe,
                    undoStarted,
                    releaseUndo);
        }

        private MatchingViewModel createViewModel(
                UiThreadDispatcher dispatcher,
                UiProfileNoteDataAccess noteDataAccess,
                MatchingViewModel.DistanceCalculator distanceCalculator,
                CountDownLatch swipeStarted,
                CountDownLatch releaseSwipe,
                CountDownLatch undoStarted,
                CountDownLatch releaseUndo) {
            CandidateFinder candidateFinder =
                    new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
            ProfileService profileService = new ProfileService(users);
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
            MatchingUseCases matchingUseCases =
                    new MatchingUseCases(
                            candidateFinder,
                            matchingService,
                            datingapp.app.usecase.matching.MatchingUseCases.wrapDailyLimitService(
                                    recommendationService),
                            datingapp.app.usecase.matching.MatchingUseCases.wrapDailyPickService(recommendationService),
                            datingapp.app.usecase.matching.MatchingUseCases.wrapStandoutService(recommendationService),
                            undoService,
                            interactions,
                            users,
                            new MatchQualityService(users, interactions, config),
                            new InProcessAppEventBus(),
                            recommendationService) {
                        @Override
                        public UseCaseResult<MatchingUseCases.SwipeOutcome> processSwipe(
                                MatchingUseCases.ProcessSwipeCommand command) {
                            awaitGate(swipeStarted, releaseSwipe, "swipe");
                            return super.processSwipe(command);
                        }

                        @Override
                        public UseCaseResult<MatchingUseCases.UndoOutcome> undoSwipe(
                                MatchingUseCases.UndoSwipeCommand command) {
                            awaitGate(undoStarted, releaseUndo, "undo");
                            return super.undoSwipe(command);
                        }
                    };

            return new MatchingViewModel(
                    new MatchingViewModel.Dependencies(
                            candidateFinder,
                            matchingService,
                            undoService,
                            trustSafetyService,
                            matchingUseCases,
                            datingapp.app.usecase.social.SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                            noteDataAccess,
                            distanceCalculator),
                    session,
                    dispatcher);
        }

        private void awaitGate(CountDownLatch started, CountDownLatch release, String operationName) {
            if (started == null || release == null) {
                return;
            }
            started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS), operationName + " gate was never released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to release " + operationName, exception);
            }
        }

        private ProfileUseCases createNoteUseCases() {
            InProcessAppEventBus eventBus = new InProcessAppEventBus();
            ValidationService validationService = new ValidationService(config);
            return new ProfileUseCases(
                    users,
                    new ProfileService(users),
                    validationService,
                    new ProfileMutationUseCases(
                            users,
                            validationService,
                            TestAchievementService.empty(),
                            config,
                            new ProfileActivationPolicy(),
                            eventBus),
                    new ProfileNotesUseCases(users, validationService, config, eventBus),
                    new ProfileInsightsUseCases(TestAchievementService.empty(), null));
        }

        private void saveNote(String content) {
            users.saveProfileNote(ProfileNote.create(currentUser.getId(), prioritizedCandidate.getId(), content));
        }

        private Optional<ProfileNote> lookupNote() {
            return users.getProfileNote(currentUser.getId(), prioritizedCandidate.getId());
        }

        private User createUser(String name, Gender gender, EnumSet<Gender> interestedIn, boolean withLocation) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(27));
            user.setGender(gender);
            user.setInterestedIn(interestedIn);
            if (withLocation) {
                user.setLocation(40.7128, -74.0060);
            }
            user.setAgeRange(20, 45, 18, 120);
            user.setMaxDistanceKm(100, config.matching().maxDistanceKm());
            user.addPhotoUrl("http://example.com/" + name + ".jpg");
            user.setBio("Matching test user");
            user.setPacePreferences(new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEEP_CHAT));
            if (withLocation) {
                user.activate();
            }
            return user;
        }

        private User createStorageActiveUserWithoutLocation(String name, Gender gender, EnumSet<Gender> interestedIn) {
            return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                    .birthDate(AppClock.today().minusYears(27))
                    .gender(gender)
                    .interestedIn(interestedIn)
                    .ageRange(20, 45)
                    .maxDistanceKm(100)
                    .photoUrls(java.util.List.of("http://example.com/" + name + ".jpg"))
                    .bio("Matching test user")
                    .pacePreferences(new PacePreferences(
                            PacePreferences.MessagingFrequency.OFTEN,
                            PacePreferences.TimeToFirstDate.FEW_DAYS,
                            PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                            PacePreferences.DepthPreference.DEEP_CHAT))
                    .state(User.UserState.ACTIVE)
                    .build();
        }
    }

    private static final class QueuedUiDispatcher implements UiThreadDispatcher {
        private final Queue<Runnable> pending = new ArrayDeque<>();
        private final ThreadLocal<Boolean> uiThread = ThreadLocal.withInitial(() -> false);
        private volatile boolean queuedMode;

        @Override
        public boolean isUiThread() {
            return uiThread.get();
        }

        @Override
        public void dispatch(Runnable action) {
            if (!queuedMode) {
                boolean previous = uiThread.get();
                uiThread.set(true);
                try {
                    action.run();
                } finally {
                    uiThread.set(previous);
                }
                return;
            }
            synchronized (pending) {
                pending.add(action);
            }
        }

        private void setQueuedMode(boolean queuedMode) {
            this.queuedMode = queuedMode;
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
                boolean previous = uiThread.get();
                uiThread.set(true);
                try {
                    next.run();
                } finally {
                    uiThread.set(previous);
                }
            }
        }

        private boolean hasPending() {
            synchronized (pending) {
                return !pending.isEmpty();
            }
        }
    }
}
