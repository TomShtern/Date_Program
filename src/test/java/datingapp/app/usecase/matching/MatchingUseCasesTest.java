package datingapp.app.usecase.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.ListActiveMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ListPagedMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.UndoSwipeCommand;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MatchingUseCases")
class MatchingUseCasesTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private CandidateFinder candidateFinder;
    private DailyLimitService dailyLimitService;
    private DailyPickService dailyPickService;
    private StandoutService standoutService;
    private UndoService undoService;
    private MatchingService matchingService;
    private MatchQualityService matchQualityService;
    private MatchingUseCases useCases;
    private User currentUser;
    private User candidate;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        var undoStorage = new TestStorages.Undos();
        dailyLimitService = alwaysAllowDailyLimitService();
        dailyPickService = noDailyPickService();
        standoutService = noStandoutService();
        var recommendationService = new RecommendationService(dailyLimitService, dailyPickService, standoutService);

        currentUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Current");
        candidate = TestUserFactory.createActiveUser(UUID.randomUUID(), "Candidate");
        candidate.setGender(User.Gender.FEMALE);
        candidate.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(currentUser);
        userStorage.save(candidate);

        candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);
        undoService = new UndoService(interactionStorage, undoStorage, config);
        matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .undoService(undoService)
                .dailyService(recommendationService)
                .candidateFinder(candidateFinder)
                .build();

        useCases = new MatchingUseCases(
                candidateFinder,
                matchingService,
                dailyLimitService,
                dailyPickService,
                standoutService,
                undoService,
                interactionStorage,
                userStorage,
                matchQualityService,
                new TestEventBus(),
                recommendationService);
    }

    @Test
    @DisplayName("browseCandidates should return compatible users")
    void browseCandidatesReturnsCompatibleUsers() {
        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));

        assertTrue(result.success());
        assertFalse(result.data().candidates().isEmpty());
    }

    @Test
    @DisplayName("browseCandidates reports location missing when seeker has no location")
    void browseCandidatesReportsLocationMissingWhenSeekerHasNoLocation() {
        User noLocationUser = new User(UUID.randomUUID(), "NoLocation");
        noLocationUser.setBirthDate(AppClock.today().minusYears(27));
        noLocationUser.setGender(User.Gender.MALE);
        noLocationUser.setInterestedIn(Set.of(User.Gender.FEMALE));
        noLocationUser.setAgeRange(20, 45, 18, 120);
        noLocationUser.setMaxDistanceKm(100, AppConfig.defaults().matching().maxDistanceKm());
        noLocationUser.addPhotoUrl("http://example.com/no-location.jpg");
        noLocationUser.setBio("No location test user");
        noLocationUser.setPacePreferences(currentUser.getPacePreferences());
        noLocationUser.activate();
        userStorage.save(noLocationUser);

        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(noLocationUser.getId()), noLocationUser));

        assertTrue(result.success());
        assertTrue(result.data().candidates().isEmpty());
        assertTrue(result.data().locationMissing());
    }

    @Test
    @DisplayName("processSwipe and undoSwipe succeed with configured services")
    void processAndUndoSucceedForCurrentSetup() {
        var swipeResult = useCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, true, false, false));

        assertTrue(swipeResult.success());
        assertNotNull(swipeResult.data().like());
        assertEquals(Like.Direction.LIKE, swipeResult.data().like().direction());

        var undoResult = useCases.undoSwipe(new UndoSwipeCommand(UserContext.cli(currentUser.getId())));
        assertTrue(undoResult.success());
        assertNotNull(undoResult.data().undoneSwipe());
    }

    @Test
    @DisplayName("duplicate processSwipe stays idempotent and keeps undo valid")
    void duplicateProcessSwipeStaysIdempotentAndKeepsUndoValid() {
        var firstSwipe = useCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, true, false, false));
        assertTrue(firstSwipe.success());

        var duplicateSwipe = useCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, true, false, false));

        assertTrue(duplicateSwipe.success());
        assertEquals("Already swiped.", duplicateSwipe.data().message());

        var undoResult = useCases.undoSwipe(new UndoSwipeCommand(UserContext.cli(currentUser.getId())));
        assertTrue(undoResult.success());
        assertNotNull(undoResult.data().undoneSwipe());
        assertTrue(interactionStorage
                .getLike(currentUser.getId(), candidate.getId())
                .isEmpty());
    }

    @Test
    @DisplayName("matchQuality returns an app-owned snapshot")
    void matchQualityReturnsAnAppOwnedSnapshot() {
        Match match = Match.create(currentUser.getId(), candidate.getId());
        interactionStorage.save(match);

        var result = useCases.matchQuality(
                new MatchingUseCases.MatchQualityQuery(UserContext.cli(currentUser.getId()), match));

        assertTrue(result.success());
        assertNotNull(result.data().compatibilityLabel());
        assertNotNull(result.data().starDisplay());
        assertNotNull(result.data().shortSummary());
    }

    @Test
    @DisplayName("processSwipe fails when context user does not match current user")
    void processSwipeFailsWhenContextUserDoesNotMatchCurrentUser() {
        var mismatchedContext = UserContext.cli(UUID.randomUUID());

        var result = useCases.processSwipe(
                new ProcessSwipeCommand(mismatchedContext, currentUser, candidate, true, false, false));

        assertFalse(result.success());
        assertEquals("Context user does not match current user", result.error().message());
    }

    @Test
    @DisplayName("processSwipe fails when current user is not active")
    void processSwipeFailsWhenCurrentUserIsNotActive() {
        User pausedUser = User.StorageBuilder.create(UUID.randomUUID(), "PausedUser", AppClock.now())
                .state(UserState.PAUSED)
                .build();

        var result = useCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(pausedUser.getId()), pausedUser, candidate, true, false, false));

        assertFalse(result.success());
        assertEquals("Current user must be ACTIVE to swipe", result.error().message());
    }

    @Test
    @DisplayName("processSwipe fails when candidate is not active")
    void processSwipeFailsWhenCandidateIsNotActive() {
        User pausedCandidate = User.StorageBuilder.create(UUID.randomUUID(), "PausedCandidate", AppClock.now())
                .state(UserState.PAUSED)
                .build();

        var result = useCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, pausedCandidate, true, false, false));

        assertFalse(result.success());
        assertEquals("Candidate must be ACTIVE to swipe", result.error().message());
    }

    @Test
    @DisplayName("processSwipe should not include literal null when exception message is missing")
    void processSwipeShouldNotIncludeLiteralNullWhenExceptionMessageMissing() {
        var config = AppConfig.defaults();
        var localTrustSafetyStorage = new TestStorages.TrustSafety();
        var undoStorage = new TestStorages.Undos();
        DailyLimitService failingDailyLimitService = alwaysAllowDailyLimitService();
        DailyPickService failingDailyPickService = noDailyPickService();
        StandoutService failingStandoutService = noStandoutService();
        var recommendationService =
                new RecommendationService(failingDailyLimitService, failingDailyPickService, failingStandoutService);

        InteractionStorage failingInteractionStorage = new TestStorages.Interactions() {
            @Override
            public LikeMatchWriteResult saveLikeAndMaybeCreateMatch(
                    datingapp.core.connection.ConnectionModels.Like like) {
                throw new RuntimeException();
            }
        };

        var failingCandidateFinder = new CandidateFinder(
                userStorage,
                failingInteractionStorage,
                localTrustSafetyStorage,
                config.safety().userTimeZone());
        var failingUndoService = new UndoService(failingInteractionStorage, undoStorage, config);
        var failingMatchingService = MatchingService.builder()
                .interactionStorage(failingInteractionStorage)
                .trustSafetyStorage(localTrustSafetyStorage)
                .userStorage(userStorage)
                .undoService(failingUndoService)
                .dailyService(recommendationService)
                .candidateFinder(failingCandidateFinder)
                .build();

        var failingUseCases = new MatchingUseCases(
                failingCandidateFinder,
                failingMatchingService,
                failingDailyLimitService,
                failingDailyPickService,
                failingStandoutService,
                failingUndoService,
                failingInteractionStorage,
                userStorage,
                new MatchQualityService(userStorage, failingInteractionStorage, config),
                new TestEventBus(),
                recommendationService);

        var result = failingUseCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, true, false, false));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertFalse(result.error().message().contains("null"));
    }

    @Test
    @DisplayName("listActiveMatches should return map of opposite users")
    void listActiveMatchesReturnsUserMap() {
        interactionStorage.save(Match.create(currentUser.getId(), candidate.getId()));

        var result = useCases.listActiveMatches(new ListActiveMatchesQuery(UserContext.cli(currentUser.getId())));

        assertTrue(result.success());
        assertEquals(1, result.data().matches().size());
        assertTrue(result.data().usersById().containsKey(candidate.getId()));
    }

    @Test
    @DisplayName("listPagedMatches should return page data and resolved users")
    void listPagedMatchesReturnsPageDataAndResolvedUsers() {
        interactionStorage.save(Match.create(currentUser.getId(), candidate.getId()));

        var result = useCases.listPagedMatches(new ListPagedMatchesQuery(UserContext.cli(currentUser.getId()), 20, 0));

        assertTrue(result.success());
        assertEquals(1, result.data().page().items().size());
        assertEquals(1, result.data().page().totalCount());
        assertTrue(result.data().usersById().containsKey(candidate.getId()));
    }

    @Test
    @DisplayName("wrapDailyLimitService(null) returns non-null permissive service")
    void wrapDailyLimitServiceNullReturnsNonNullPermissiveService() {
        var wrappedService = MatchingUseCases.wrapDailyLimitService(null);

        assertNotNull(wrappedService);
        assertTrue(wrappedService.canLike(currentUser.getId()));
        assertTrue(wrappedService.canPass(currentUser.getId()));
        assertTrue(wrappedService.canSuperLike(currentUser.getId()));
    }

    @Test
    @DisplayName("wrapDailyPickService(null) returns non-null empty service")
    void wrapDailyPickServiceNullReturnsNonNullEmptyService() {
        var wrappedService = MatchingUseCases.wrapDailyPickService(null);

        assertNotNull(wrappedService);
        assertTrue(wrappedService.getDailyPick(currentUser).isEmpty());
        assertFalse(wrappedService.hasViewedDailyPick(currentUser.getId()));
        assertEquals(0, wrappedService.cleanupOldDailyPickViews(AppClock.today()));
    }

    @Test
    @DisplayName("wrapStandoutService(null) returns non-null empty service")
    void wrapStandoutServiceNullReturnsNonNullEmptyService() {
        var wrappedService = MatchingUseCases.wrapStandoutService(null);

        assertNotNull(wrappedService);
        assertEquals(0, wrappedService.getStandouts(currentUser).standouts().size());
        assertEquals(0, wrappedService.resolveUsers(List.of()).size());
    }

    @Test
    @DisplayName("browseCandidates excludes symmetrically blocked users")
    void browseCandidatesExcludesSymmetricallyBlockedUsers() {
        var blockedCandidate = TestUserFactory.createActiveUser(UUID.randomUUID(), "BlockedCandidate");
        blockedCandidate.setGender(User.Gender.FEMALE);
        blockedCandidate.setInterestedIn(Set.of(User.Gender.MALE));
        userStorage.save(blockedCandidate);
        trustSafetyStorage.save(
                datingapp.core.connection.ConnectionModels.Block.create(blockedCandidate.getId(), currentUser.getId()));

        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));

        assertTrue(result.success());
        assertTrue(
                result.data().candidates().stream().noneMatch(c -> c.getId().equals(blockedCandidate.getId())),
                "Blocked user should not appear in browse candidates");
    }

    @Test
    @DisplayName("browseCandidates excludes recently unmatched users within cooldown window")
    void browseCandidatesExcludesRecentlyUnmatchedUsers() {
        var unmatched = TestUserFactory.createActiveUser(UUID.randomUUID(), "RecentlyUnmatched");
        unmatched.setGender(User.Gender.FEMALE);
        unmatched.setInterestedIn(Set.of(User.Gender.MALE));
        userStorage.save(unmatched);

        var match = Match.create(currentUser.getId(), unmatched.getId());
        match.unmatch(currentUser.getId());
        interactionStorage.save(match);

        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));

        assertTrue(result.success());
        assertTrue(
                result.data().candidates().stream().noneMatch(c -> c.getId().equals(unmatched.getId())),
                "Recently unmatched user should not appear within cooldown window");
    }

    @Test
    @DisplayName("browseCandidates permits rematch after cooldown expires")
    void browseCandidatesPermitsRematchAfterCooldownExpires() {
        var previouslyUnmatched = TestUserFactory.createActiveUser(UUID.randomUUID(), "PreviouslyUnmatched");
        previouslyUnmatched.setGender(User.Gender.FEMALE);
        previouslyUnmatched.setInterestedIn(Set.of(User.Gender.MALE));
        userStorage.save(previouslyUnmatched);

        try {
            AppClock.setFixed(AppClock.now().minus(Duration.ofHours(169)));
            var expiredMatch = Match.create(currentUser.getId(), previouslyUnmatched.getId());
            expiredMatch.unmatch(currentUser.getId());
            interactionStorage.save(expiredMatch);
        } finally {
            AppClock.reset();
        }

        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));

        assertTrue(result.success());
        assertTrue(
                result.data().candidates().stream().anyMatch(c -> c.getId().equals(previouslyUnmatched.getId())),
                "User should reappear after cooldown expires");
    }

    @Test
    @DisplayName("browseCandidates still preserves locationMissing contract after filtering")
    void browseCandidatesPreservesLocationMissingContractAfterFiltering() {
        User noLocationUser = new User(UUID.randomUUID(), "NoLocationFiltering");
        noLocationUser.setBirthDate(AppClock.today().minusYears(27));
        noLocationUser.setGender(User.Gender.MALE);
        noLocationUser.setInterestedIn(Set.of(User.Gender.FEMALE));
        noLocationUser.setAgeRange(20, 45, 18, 120);
        noLocationUser.setMaxDistanceKm(100, AppConfig.defaults().matching().maxDistanceKm());
        noLocationUser.addPhotoUrl("http://example.com/no-location-filter.jpg");
        noLocationUser.setBio("No location but otherwise complete");
        noLocationUser.setPacePreferences(currentUser.getPacePreferences());
        noLocationUser.activate();
        userStorage.save(noLocationUser);

        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(noLocationUser.getId()), noLocationUser));

        assertTrue(result.success());
        assertTrue(
                result.data().locationMissing(),
                "locationMissing must remain true for seeker without location, even with new filtering");
        assertTrue(result.data().candidates().isEmpty(), "Candidates list must be empty when location is missing");
    }

    @Test
    @DisplayName("builder rejects missing event bus for production wiring")
    void builderRejectsMissingEventBusForProductionWiring() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, this::buildMatchingUseCasesWithoutEventBus);

        assertEquals("eventBus cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("constructor rejects missing event bus")
    void constructorRejectsMissingEventBus() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MatchingUseCases(
                        candidateFinder,
                        matchingService,
                        dailyLimitService,
                        dailyPickService,
                        standoutService,
                        undoService,
                        interactionStorage,
                        userStorage,
                        matchQualityService,
                        null,
                        null));

        assertEquals("eventBus cannot be null", exception.getMessage());
    }

    private MatchingUseCases buildMatchingUseCasesWithoutEventBus() {
        return MatchingUseCases.builder()
                .candidateFinder(candidateFinder)
                .matchingService(matchingService)
                .dailyLimitService(dailyLimitService)
                .dailyPickService(dailyPickService)
                .standoutService(standoutService)
                .undoService(undoService)
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .matchQualityService(matchQualityService)
                .build();
    }

    private static DailyLimitService alwaysAllowDailyLimitService() {
        return new DailyLimitService() {
            @Override
            public boolean canLike(UUID userId) {
                return true;
            }

            @Override
            public boolean canSuperLike(UUID userId) {
                return true;
            }

            @Override
            public boolean canPass(UUID userId) {
                return true;
            }

            @Override
            public DailyStatus getStatus(UUID userId) {
                return new DailyStatus(0, 999, 0, 999, 0, 999, AppClock.today(), AppClock.now());
            }

            @Override
            public Duration getTimeUntilReset() {
                return Duration.ZERO;
            }
        };
    }

    private static DailyPickService noDailyPickService() {
        return new DailyPickService() {
            @Override
            public Optional<DailyPick> getDailyPick(User seeker) {
                return Optional.empty();
            }

            @Override
            public boolean hasViewedDailyPick(UUID userId) {
                return false;
            }

            @Override
            public void markDailyPickViewed(UUID userId) {
                // No-op: the test only needs a deterministic, side-effect-free daily-pick service.
            }

            @Override
            public int cleanupOldDailyPickViews(java.time.LocalDate before) {
                return 0;
            }
        };
    }

    private static StandoutService noStandoutService() {
        return new StandoutService() {
            @Override
            public Result getStandouts(User seeker) {
                return Result.empty("none");
            }

            @Override
            public void markInteracted(UUID seekerId, UUID standoutUserId) {
                // No-op: standouts are irrelevant for this use-case test setup.
            }

            @Override
            public java.util.Map<UUID, User> resolveUsers(List<Standout> standouts) {
                return java.util.Map.of();
            }
        };
    }
}
