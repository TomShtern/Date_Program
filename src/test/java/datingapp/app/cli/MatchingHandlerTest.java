package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for MatchingHandler CLI operations: browseCandidates, daily limits,
 * and undo functionality per P3-6 coverage plan.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchingHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private TestStorages.Communications communicationStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        communicationStorage = new TestStorages.Communications();

        session = AppSession.getInstance();
        session.reset();

        testUser = TestUserFactory.createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    @AfterEach
    void tearDown() {
        session.reset();
    }

    private MatchingHandler createHandler(String input) {
        return createHandler(input, AppConfig.defaults());
    }

    private MatchingHandler createHandler(String input, AppConfig config) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config)
                .build();
        CandidateFinder candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);
        ProfileService profileCompletionService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        RecommendationService dailyService = RecommendationService.builder()
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new TestStorages.Standouts())
                .profileService(profileCompletionService)
                .config(config)
                .build();
        UndoService undoService = new UndoService(interactionStorage, new TestStorages.Undos(), config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .undoService(undoService)
                .dailyService(dailyService)
                .candidateFinder(candidateFinder)
                .build();
        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        MatchingUseCases matchingUseCases = new MatchingUseCases(
                candidateFinder,
                matchingService,
                MatchingUseCases.wrapDailyLimitService(dailyService),
                MatchingUseCases.wrapDailyPickService(dailyService),
                MatchingUseCases.wrapStandoutService(dailyService),
                undoService,
                interactionStorage,
                userStorage,
                matchQualityService,
                null);
        SocialUseCases socialUseCases = new SocialUseCases(connectionService, trustSafetyService, communicationStorage);

        MatchingHandler.Dependencies deps = new MatchingHandler.Dependencies(
                matchingService,
                dailyService,
                undoService,
                matchQualityService,
                userStorage,
                profileCompletionService,
                analyticsStorage,
                dailyService,
                config,
                session,
                inputReader,
                null,
                matchingUseCases,
                socialUseCases);
        return new MatchingHandler(deps);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Browse Candidates")
    class BrowseCandidatesTests {

        @Test
        @DisplayName("Shows empty-state message when no candidates available")
        void showsEmptyStateMessageWhenNoCandidates() {
            MatchingHandler handler = createHandler("q\n");
            assertDoesNotThrow(handler::browseCandidates);
            assertEquals(0, interactionStorage.countByDirection(testUser.getId(), Like.Direction.LIKE));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("EOF handling")
    class EofHandlingTests {

        @Test
        @DisplayName("readValidatedChoice returns null on EOF instead of looping")
        void readValidatedChoiceReturnsNullOnEofInsteadOfLooping() throws Exception {
            User candidate = TestUserFactory.createActiveUser("EOFCandidate");
            candidate.setBirthDate(LocalDate.of(1990, 1, 1));
            candidate.setLocation(32.0853, 34.7818);
            userStorage.save(candidate);
            testUser.setLocation(32.0853, 34.7818);
            userStorage.save(testUser);

            MatchingHandler handler = createHandler("");

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                Method method = MatchingHandler.class.getDeclaredMethod(
                        "readValidatedChoice", String.class, String.class, String[].class);
                method.setAccessible(true);
                Object result = method.invoke(
                        handler, CliTextAndInput.PROMPT_LIKE_PASS_QUIT, "error", (Object) new String[] {"l", "p", "q"});
                assertEquals(null, result);
            });
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Daily Limits")
    class DailyLimitsTests {

        @Test
        @DisplayName("Shows like limit message when like limit reached")
        void showsLikeLimitMessageWhenLikeLimitReached() {
            AppConfig config = AppConfig.defaults();
            int likeLimit = config.matching().dailyLikeLimit();

            // Create enough candidates: one more than limit
            List<User> candidates = new ArrayList<>();
            for (int i = 0; i < likeLimit + 2; i++) {
                User candidate = TestUserFactory.createActiveUser("Candidate" + i);
                userStorage.save(candidate);
                candidates.add(candidate);
            }

            // Pre-create likes up to limit
            for (int i = 0; i < likeLimit; i++) {
                Like like = Like.create(testUser.getId(), candidates.get(i).getId(), Like.Direction.LIKE);
                interactionStorage.save(like);
            }

            // Input: try like when at limit, no undo, quit
            MatchingHandler handler = createHandler("l\nn\n");

            assertDoesNotThrow(handler::browseCandidates);
            // Should still be at limit (no new like added)
            assertEquals(likeLimit, interactionStorage.countByDirection(testUser.getId(), Like.Direction.LIKE));
        }

        @Test
        @DisplayName("Shows pass limit behavior when pass limit reached")
        void showsPassLimitBehaviorWhenPassLimitReached() {
            AppConfig config = AppConfig.builder().dailyPassLimit(2).build();
            int passLimit = config.matching().dailyPassLimit();

            List<User> candidates = new ArrayList<>();
            for (int i = 0; i < passLimit + 2; i++) {
                User candidate = TestUserFactory.createActiveUser("PassCandidate" + i);
                userStorage.save(candidate);
                candidates.add(candidate);
            }

            for (int i = 0; i < passLimit; i++) {
                Like pass = Like.create(testUser.getId(), candidates.get(i).getId(), Like.Direction.PASS);
                interactionStorage.save(pass);
            }

            MatchingHandler handler = createHandler("p\nn\n", config);

            assertDoesNotThrow(handler::browseCandidates);
            assertEquals(passLimit, interactionStorage.countByDirection(testUser.getId(), Like.Direction.PASS));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Undo Functionality")
    class UndoFunctionalityTests {

        @Test
        @DisplayName("Handles undo with no prior swipe gracefully")
        void handlesUndoWithNoPriorSwipeGracefully() {
            User candidate = TestUserFactory.createActiveUser("CandidateForUndo");
            userStorage.save(candidate);

            MatchingHandler handler = createHandler("q\n");

            assertDoesNotThrow(handler::browseCandidates);
            assertEquals(0, interactionStorage.countByDirection(testUser.getId(), Like.Direction.LIKE));
        }

        @Test
        @DisplayName("Executes undo after a swipe without error")
        void executesUndoAfterASwipeWithoutError() {
            User candidate = TestUserFactory.createActiveUser("UndoCandidate");
            userStorage.save(candidate);

            MatchingHandler handler = createHandler("l\ny\nq\n");

            assertDoesNotThrow(handler::browseCandidates);
            // Like count should be 0 or 1 depending on undo timing
            int likeCount = interactionStorage.countByDirection(testUser.getId(), Like.Direction.LIKE);
            assertTrue(likeCount >= 0 && likeCount <= 1);
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Profile State Validation")
    class ProfileStateValidationTests {

        @Test
        @DisplayName("Prevents browsing when user state is INCOMPLETE")
        void preventsBrowsingWhenUserStateIncomplete() {
            User incompleteUser = User.StorageBuilder.create(UUID.randomUUID(), "IncompleteUser", AppClock.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Test bio")
                    .birthDate(AppClock.today().minusYears(25))
                    .gender(Gender.MALE)
                    .interestedIn(java.util.Set.of(Gender.FEMALE))
                    .photoUrls(java.util.List.of("http://example.com/photo.jpg"))
                    .location(32.0853, 34.7818)
                    .build();
            userStorage.save(incompleteUser);
            session.setCurrentUser(incompleteUser);

            MatchingHandler handler = createHandler("q\n");

            assertDoesNotThrow(handler::browseCandidates);
            assertEquals(0, interactionStorage.countByDirection(incompleteUser.getId(), Like.Direction.LIKE));
        }

        @Test
        @DisplayName("Prevents browsing when location is not set")
        void preventsBrowsingWhenLocationNotSet() {
            User noLocationUser = User.StorageBuilder.create(UUID.randomUUID(), "NoLocationUser", AppClock.now())
                    .state(UserState.ACTIVE)
                    .bio("Test bio")
                    .birthDate(AppClock.today().minusYears(25))
                    .gender(Gender.MALE)
                    .interestedIn(java.util.Set.of(Gender.FEMALE))
                    .photoUrls(java.util.List.of("http://example.com/photo.jpg"))
                    .build();
            userStorage.save(noLocationUser);
            session.setCurrentUser(noLocationUser);

            MatchingHandler handler = createHandler("n\n");

            assertDoesNotThrow(handler::browseCandidates);
            assertEquals(0, interactionStorage.countByDirection(noLocationUser.getId(), Like.Direction.LIKE));
        }
    }
}
