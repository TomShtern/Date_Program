package datingapp.app.usecase.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.ListActiveMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ListPagedMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.UndoSwipeCommand;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
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
    private MatchingUseCases useCases;
    private User currentUser;
    private User candidate;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        var trustSafetyStorage = new TestStorages.TrustSafety();
        var undoStorage = new TestStorages.Undos();
        DailyLimitService dailyLimitService = alwaysAllowDailyLimitService();
        DailyPickService dailyPickService = noDailyPickService();
        StandoutService standoutService = noStandoutService();
        var recommendationService = new RecommendationService(dailyLimitService, dailyPickService, standoutService);

        currentUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Current");
        candidate = TestUserFactory.createActiveUser(UUID.randomUUID(), "Candidate");
        candidate.setGender(User.Gender.FEMALE);
        candidate.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(currentUser);
        userStorage.save(candidate);

        var candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        var undoService = new UndoService(interactionStorage, undoStorage, config);
        var matchingService = MatchingService.builder()
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
                null,
                null);
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
        assertTrue(swipeResult.data().success());

        var undoResult = useCases.undoSwipe(new UndoSwipeCommand(UserContext.cli(currentUser.getId())));
        assertTrue(undoResult.success());
        assertTrue(undoResult.data().success());
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

            @Override
            public String formatDuration(Duration duration) {
                return "00:00:00";
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
