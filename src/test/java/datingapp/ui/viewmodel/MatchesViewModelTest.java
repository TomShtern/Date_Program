package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.async.UiAsyncTestSupport;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("MatchesViewModel")
class MatchesViewModelTest {

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.Communications communications;
    private TestStorages.TrustSafety trustSafetyStorage;
    private MatchingUseCases matchingUseCases;
    private ProfileUseCases profileUseCases;
    private SocialUseCases socialUseCases;
    private MatchingService matchingService;
    private RecommendationService dailyService;
    private AppConfig config;
    private MatchesViewModel viewModel;
    private User currentUser;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        communications = new TestStorages.Communications();
        trustSafetyStorage = new TestStorages.TrustSafety();
        TestClock.setFixed(FIXED_INSTANT);

        config = AppConfig.defaults();

        // Create dependencies for RecommendationService
        var analyticsStorage = new TestStorages.Analytics();
        var candidateFinder = new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        var standoutStorage = new TestStorages.Standouts();
        var profileService = new ProfileService(users);

        dailyService = RecommendationService.builder()
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

        matchingService = MatchingService.builder()
                .interactionStorage(interactions)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(users)
                .undoService(undoService)
                .dailyService(dailyService)
                .candidateFinder(candidateFinder)
                .build();

        var matchQualityService = new MatchQualityService(users, interactions, config);
        matchingUseCases = new datingapp.app.usecase.matching.MatchingUseCases(
                candidateFinder,
                matchingService,
                datingapp.app.usecase.matching.MatchingUseCases.wrapDailyLimitService(dailyService),
                datingapp.app.usecase.matching.MatchingUseCases.wrapDailyPickService(dailyService),
                datingapp.app.usecase.matching.MatchingUseCases.wrapStandoutService(dailyService),
                undoService,
                interactions,
                users,
                matchQualityService,
                new InProcessAppEventBus(),
                dailyService);
        profileUseCases = new ProfileUseCases(
                users,
                profileService,
                new ValidationService(config),
                new ProfileMutationUseCases(
                        users,
                        new ValidationService(config),
                        TestAchievementService.empty(),
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new InProcessAppEventBus()),
                new ProfileNotesUseCases(users, new ValidationService(config), config, new InProcessAppEventBus()),
                new ProfileInsightsUseCases(TestAchievementService.empty(), null));
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        socialUseCases = datingapp.app.usecase.social.SocialUseCases.forWorkflowAccess(
                new ConnectionService(config, communications, interactions, users), trustSafetyService, communications);

        viewModel = new MatchesViewModel(
                new MatchesViewModel.Dependencies(
                        dailyService, matchingUseCases, profileUseCases, socialUseCases, config),
                AppSession.getInstance(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());
        currentUser = createActiveUser("Current");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("likeBack does not create like when daily limit reached")
    void likeBackRespectsDailyLimit() {
        AppConfig zeroLimitConfig = AppConfig.builder().dailyLikeLimit(0).build();

        // Re-create dependencies or reuse? Reusing mostly fine since they are
        // mocks/stubs.
        var analyticsStorage = new TestStorages.Analytics();
        var candidateFinder = new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        var standoutStorage = new TestStorages.Standouts();
        var profileService = new ProfileService(users);

        RecommendationService zeroLimitRecommendationService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(zeroLimitConfig)
                .build();

        MatchesViewModel limitViewModel = new MatchesViewModel(
                new MatchesViewModel.Dependencies(
                        zeroLimitRecommendationService,
                        buildMatchingUseCases(interactions, zeroLimitRecommendationService, zeroLimitConfig),
                        profileUseCases,
                        socialUseCases,
                        zeroLimitConfig),
                AppSession.getInstance(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());

        User otherUser = createActiveUser("Other");
        users.save(otherUser);

        MatchesViewModel.LikeCardData likeCard = new MatchesViewModel.LikeCardData(
                otherUser.getId(),
                UUID.randomUUID(),
                otherUser.getName(),
                otherUser.getAge(ZoneId.of("UTC")).orElse(0),
                "Bio",
                "Just now",
                null);

        limitViewModel.likeBack(likeCard);

        assertFalse(
                interactions.exists(currentUser.getId(), otherUser.getId()),
                "Like should not be created when daily limit is reached");
    }

    @Test
    @DisplayName("refreshMatches populates matches list")
    void refreshMatchesPopulatesList() {
        User otherUser = createActiveUser("MatchedUser");
        users.save(otherUser);
        Match match = Match.create(currentUser.getId(), otherUser.getId());
        interactions.save(match);

        viewModel.initialize(); // Calls refreshAll which calls refreshMatches

        assertEquals(1, viewModel.getMatches().size());
        assertEquals(otherUser.getName(), viewModel.getMatches().get(0).userName());
        assertEquals(1, viewModel.matchCountProperty().get());
        assertFalse(viewModel.loadFailedProperty().get());
    }

    @Test
    @DisplayName("empty refresh remains a successful empty state")
    void refreshEmptyStateIsNotAFailure() {
        viewModel.initialize();

        assertTrue(viewModel.getMatches().isEmpty());
        assertTrue(viewModel.getLikesReceived().isEmpty());
        assertTrue(viewModel.getLikesSent().isEmpty());
        assertFalse(viewModel.loadFailedProperty().get());
    }

    @Test
    @DisplayName("refresh failures are surfaced instead of collapsing to empty data")
    void refreshFailuresAreSurfaced() {
        MatchesViewModel failingViewModel = buildViewModel(
                buildMatchingUseCases(failingInteractions(), dailyService, config), dailyService, config);

        failingViewModel.initialize();

        assertTrue(failingViewModel.loadFailedProperty().get());
        assertTrue(failingViewModel.loadFailureMessageProperty().get().contains("match load failed"));
        assertTrue(failingViewModel.getMatches().isEmpty());
    }

    @Test
    @DisplayName("late-bound error handler receives refresh failures")
    void lateBoundErrorHandlerReceivesRefreshFailures() throws InterruptedException {
        MatchesViewModel failingViewModel = buildViewModel(
                buildMatchingUseCases(failingInteractions(), dailyService, config), dailyService, config);

        AtomicReference<String> routedMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        failingViewModel.setErrorHandler(message -> {
            routedMessage.set(message);
            latch.countDown();
        });

        failingViewModel.initialize();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(failingViewModel.loadFailedProperty().get());
        assertTrue(routedMessage.get().contains("Failed to refresh matches"));
        assertTrue(routedMessage.get().contains("match load failed"));
    }

    @Test
    @DisplayName("refreshLikesReceived populates likesReceived list")
    void refreshLikesReceivedPopulatesList() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        Like like = Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE);
        interactions.save(like);

        viewModel.initialize();

        assertEquals(1, viewModel.getLikesReceived().size());
        assertEquals(otherUser.getName(), viewModel.getLikesReceived().get(0).userName());
        assertEquals(1, viewModel.likesReceivedCountProperty().get());
    }

    @Test
    @DisplayName("refreshLikesSent populates likesSent list")
    void refreshLikesSentPopulatesList() {
        User otherUser = createActiveUser("LikedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        interactions.save(like);

        viewModel.initialize();

        assertEquals(1, viewModel.getLikesSent().size());
        assertEquals(otherUser.getName(), viewModel.getLikesSent().get(0).userName());
        assertEquals(1, viewModel.likesSentCountProperty().get());
    }

    @Test
    @DisplayName("refreshLikesSent excludes matched users")
    void refreshLikesSentExcludesMatches() {
        User otherUser = createActiveUser("MatchedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        interactions.save(like);

        // Create match
        Match match = Match.create(currentUser.getId(), otherUser.getId());
        interactions.save(match);

        viewModel.initialize();

        assertEquals(0, viewModel.getLikesSent().size(), "Matched users should not appear in likes sent");
    }

    @Test
    @DisplayName("refreshLikesSent excludes blocked users from SocialUseCases")
    void refreshLikesSentExcludesBlockedUsersFromSocialUseCases() {
        User otherUser = createActiveUser("BlockedUser");
        users.save(otherUser);
        interactions.save(Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE));
        socialUseCases.blockUser(new datingapp.app.usecase.social.SocialUseCases.RelationshipCommand(
                datingapp.app.usecase.common.UserContext.ui(currentUser.getId()), otherUser.getId()));

        viewModel.initialize();

        assertEquals(0, viewModel.getLikesSent().size(), "Blocked users should not appear in likes sent");
    }

    @Test
    @DisplayName("withdrawLike deletes like and refreshes list")
    void withdrawLikeDeletesAndRefreshes() {
        User otherUser = createActiveUser("LikedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        interactions.save(like);

        viewModel.initialize();
        assertEquals(1, viewModel.getLikesSent().size());

        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesSent().get(0);
        viewModel.withdrawLike(likeCard);

        assertFalse(interactions.exists(currentUser.getId(), otherUser.getId()));
        assertEquals(0, viewModel.getLikesSent().size());
    }

    @Test
    @DisplayName("passOn records PASS and refreshes lists")
    void passOnRecordsPassAndRefreshes() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        Like receivedLike = Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE);
        interactions.save(receivedLike);

        viewModel.initialize();
        assertEquals(1, viewModel.getLikesReceived().size());

        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesReceived().get(0);
        viewModel.passOn(likeCard);

        // Check that a PASS exists from current user to other user
        assertTrue(interactions
                .getLike(currentUser.getId(), otherUser.getId())
                .map(l -> l.direction() == Like.Direction.PASS)
                .orElse(false));

        assertEquals(0, viewModel.getLikesReceived().size());
        assertEquals(0, viewModel.getMatches().size());
    }

    @Test
    @DisplayName("likeBack creates match if user liked us")
    void likeBackCreatesMatch() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        interactions.save(Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE));

        viewModel.initialize();
        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesReceived().get(0);

        viewModel.likeBack(likeCard);

        assertTrue(interactions.getActiveMatchesFor(currentUser.getId()).stream()
                .anyMatch(m -> m.involves(otherUser.getId())));
        assertEquals(1, viewModel.getMatches().size());
        assertEquals(0, viewModel.getLikesReceived().size());
    }

    @Test
    @DisplayName("requestFriendZone creates a pending request for the matched user")
    void requestFriendZoneCreatesPendingRequest() {
        User otherUser = createActiveUser("FriendZoneUser");
        users.save(otherUser);
        interactions.save(Match.create(currentUser.getId(), otherUser.getId()));

        viewModel.initialize();
        MatchesViewModel.MatchCardData matchCard = viewModel.getMatches().getFirst();

        viewModel.requestFriendZone(matchCard);

        assertEquals(
                1,
                communications
                        .getPendingFriendRequestsForUser(otherUser.getId())
                        .size());
    }

    @Test
    @DisplayName("unmatch removes the match from the list after refresh")
    void unmatchRemovesMatchFromList() {
        User otherUser = createActiveUser("UnmatchUser");
        users.save(otherUser);
        interactions.save(Match.create(currentUser.getId(), otherUser.getId()));

        viewModel.initialize();
        assertEquals(1, viewModel.getMatches().size());

        viewModel.unmatch(viewModel.getMatches().getFirst());

        assertEquals(0, interactions.getActiveMatchesFor(currentUser.getId()).size());
        assertEquals(0, viewModel.getMatches().size());
    }

    @Test
    @DisplayName("loadNextMatchPage does not corrupt offset if refreshAll resets it concurrently")
    void loadNextMatchPageDoesNotCorruptOffsetOnConcurrentReset() throws Exception {
        // Setup: Current user has 5 matches (less than PAGE_SIZE=20)
        for (int i = 0; i < 5; i++) {
            User other = createActiveUser("Match" + i);
            users.save(other);
            interactions.save(Match.create(currentUser.getId(), other.getId()));
        }

        // Flag to trigger a nested refreshAll exactly once to simulate the race
        final AtomicBoolean raceTriggered = new AtomicBoolean(false);
        final AtomicReference<MatchesViewModel> vmRef = new AtomicReference<>();

        TestStorages.Interactions raceInteractions = new TestStorages.Interactions(communications) {
            @Override
            public datingapp.core.storage.PageData<Match> getPageOfActiveMatchesFor(
                    UUID userId, int offset, int limit) {
                if (raceTriggered.compareAndSet(false, true)) {
                    // Simulate refreshAll() happening after offset reservation but before the downward
                    // adjustment in fetchMatchesFromUseCases.
                    vmRef.get().refreshAll();
                }
                return interactions.getPageOfActiveMatchesFor(userId, offset, limit);
            }
        };

        MatchesViewModel raceViewModel =
                buildViewModel(buildMatchingUseCases(raceInteractions, dailyService, config), dailyService, config);
        vmRef.set(raceViewModel);

        // 1. Trigger the race via initialize -> refreshAll -> fetchMatchesFromUseCases
        // -> nested refreshAll
        raceViewModel.initialize();

        // 2. Verify internal offset state using reflection.
        // If the fix works, the outer fetch (epoch 1) skips its adjustment because
        // fetchEpoch is now 2.
        // The inner fetch (epoch 2) completes its adjustment correctly.
        Field offsetField = MatchesViewModel.class.getDeclaredField("currentMatchOffset");
        offsetField.setAccessible(true);
        AtomicInteger offset = (AtomicInteger) offsetField.get(raceViewModel);

        // Without the fix, the offset would be negative:
        // Nested refresh completes correctly (offset = 5).
        // Outer refresh adjustment executes: 5 + (5 - 20) = -10.
        assertTrue(offset.get() >= 0, "Offset should not be negative: " + offset.get());
        assertEquals(5, offset.get(), "Offset should reflect the state of the latest successful refresh");
    }

    private MatchesViewModel buildViewModel(
            MatchingUseCases matchingUseCases, RecommendationService recommendationService, AppConfig appConfig) {
        return new MatchesViewModel(
                new MatchesViewModel.Dependencies(
                        recommendationService, matchingUseCases, profileUseCases, socialUseCases, appConfig),
                AppSession.getInstance(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());
    }

    private MatchingUseCases buildMatchingUseCases(
            TestStorages.Interactions interactionStorage,
            RecommendationService recommendationService,
            AppConfig appConfig) {
        var candidateFinder = new CandidateFinder(users, interactionStorage, trustSafetyStorage, ZoneId.of("UTC"));
        var undoService =
                new datingapp.core.matching.UndoService(interactionStorage, new TestStorages.Undos(), appConfig);
        var localMatchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(users)
                .undoService(undoService)
                .dailyService(recommendationService)
                .candidateFinder(candidateFinder)
                .build();
        return new MatchingUseCases(
                candidateFinder,
                localMatchingService,
                MatchingUseCases.wrapDailyLimitService(recommendationService),
                MatchingUseCases.wrapDailyPickService(recommendationService),
                MatchingUseCases.wrapStandoutService(recommendationService),
                undoService,
                interactionStorage,
                users,
                new MatchQualityService(users, interactionStorage, appConfig),
                new InProcessAppEventBus(),
                recommendationService);
    }

    private TestStorages.Interactions failingInteractions() {
        return new TestStorages.Interactions(communications) {
            @Override
            public datingapp.core.storage.PageData<Match> getPageOfActiveMatchesFor(
                    UUID userId, int offset, int limit) {
                throw new IllegalStateException("match load failed");
            }
        };
    }

    private static User createActiveUser(String name) {
        User user = TestUserFactory.createActiveUser(name);
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User should be active for test");
        }
        return user;
    }
}
