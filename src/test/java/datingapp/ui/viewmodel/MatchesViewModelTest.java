package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.PageData;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
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
    private TestStorages.TrustSafety trustSafetyStorage;
    private UiMatchDataAccess matchData;
    private UiUserStore userStore;
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
        trustSafetyStorage = new TestStorages.TrustSafety();
        TestClock.setFixed(FIXED_INSTANT);

        config = AppConfig.defaults();

        // Create dependencies for RecommendationService
        var analyticsStorage = new TestStorages.Analytics();
        var candidateFinder = new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        var standoutStorage = new TestStorages.Standouts();
        var profileService = new ProfileService(config, analyticsStorage, interactions, trustSafetyStorage, users);

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

        matchData = new StorageUiMatchDataAccess(interactions, trustSafetyStorage);
        userStore = new StorageUiUserStore(users);

        matchingService = MatchingService.builder()
                .interactionStorage(interactions)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(users)
                .build();

        var undoService = new datingapp.core.matching.UndoService(interactions, new TestStorages.Undos(), config);
        var matchingUseCases = new datingapp.app.usecase.matching.MatchingUseCases(
                candidateFinder, matchingService, dailyService, undoService, interactions, users, null);

        viewModel = new MatchesViewModel(
                matchData,
                userStore,
                matchingService,
                dailyService,
                matchingUseCases,
                config,
                AppSession.getInstance());
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
        var profileService =
                new ProfileService(zeroLimitConfig, analyticsStorage, interactions, trustSafetyStorage, users);

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
                matchData,
                userStore,
                matchingService,
                zeroLimitRecommendationService,
                new datingapp.app.usecase.matching.MatchingUseCases(
                        candidateFinder,
                        matchingService,
                        new datingapp.core.matching.UndoService(
                                interactions, new TestStorages.Undos(), zeroLimitConfig)),
                zeroLimitConfig,
                AppSession.getInstance());

        User otherUser = createActiveUser("Other");
        users.save(otherUser);

        MatchesViewModel.LikeCardData likeCard = new MatchesViewModel.LikeCardData(
                otherUser.getId(),
                UUID.randomUUID(),
                otherUser.getName(),
                otherUser.getAge(ZoneId.of("UTC")),
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

        // Custom wrapper to inject a refreshAll() call mid-fetch in
        // fetchMatchesFromStorage
        UiMatchDataAccess raceMatchData = new UiMatchDataAccess() {
            @Override
            public PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit) {
                if (raceTriggered.compareAndSet(false, true)) {
                    // Simulate refreshAll() happening after offset reservation
                    // but before the downward adjustment in fetchMatchesFromStorage.
                    vmRef.get().refreshAll();
                }
                return matchData.getPageOfActiveMatchesFor(userId, offset, limit);
            }

            @Override
            public List<Match> getActiveMatchesFor(UUID userId) {
                return matchData.getActiveMatchesFor(userId);
            }

            @Override
            public List<Match> getAllMatchesFor(UUID userId) {
                return matchData.getAllMatchesFor(userId);
            }

            @Override
            public java.util.Optional<Like> getLike(UUID from, UUID to) {
                return matchData.getLike(from, to);
            }

            @Override
            public java.util.Set<UUID> getBlockedUserIds(UUID userId) {
                return matchData.getBlockedUserIds(userId);
            }

            @Override
            public java.util.Set<UUID> getLikedOrPassedUserIds(UUID userId) {
                return matchData.getLikedOrPassedUserIds(userId);
            }

            @Override
            public void deleteLike(UUID likeId) {
                matchData.deleteLike(likeId);
            }

            @Override
            public int countActiveMatchesFor(UUID userId) {
                return matchData.countActiveMatchesFor(userId);
            }
        };

        MatchesViewModel raceViewModel = new MatchesViewModel(
                raceMatchData, userStore, matchingService, dailyService, config, AppSession.getInstance());
        vmRef.set(raceViewModel);

        // 1. Trigger the race via initialize -> refreshAll -> fetchMatchesFromStorage
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
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User should be active for test");
        }
        return user;
    }
}
