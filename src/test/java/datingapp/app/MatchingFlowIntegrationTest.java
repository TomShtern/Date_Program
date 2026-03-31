package datingapp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.matching.DefaultDailyPickService;
import datingapp.core.matching.DefaultStandoutService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Matching flow integration")
class MatchingFlowIntegrationTest {

    @Test
    @DisplayName("browse swipe match then message succeeds end-to-end")
    void browseSwipeMatchThenMessageSucceedsEndToEnd() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        AppConfig config = AppConfig.defaults();

        UUID aliceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bobId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        User alice = activeUser(aliceId, "Alice", Gender.FEMALE, Set.of(Gender.MALE));
        User bob = activeUser(bobId, "Bob", Gender.MALE, Set.of(Gender.FEMALE));
        userStorage.save(alice);
        userStorage.save(bob);

        ServiceRegistry services =
                createServices(config, userStorage, interactionStorage, communicationStorage, standoutStorage);

        MatchingUseCases matchingUseCases = services.getMatchingUseCases();
        MessagingUseCases messagingUseCases = services.getMessagingUseCases();

        var browse = matchingUseCases.browseCandidates(new BrowseCandidatesCommand(UserContext.cli(aliceId), alice));
        assertTrue(browse.success());
        assertTrue(browse.data().candidates().isEmpty()
                || browse.data().candidates().stream()
                        .allMatch(candidate -> !candidate.getId().equals(aliceId)));

        var aliceLike = matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.cli(aliceId), bobId, Like.Direction.LIKE, true));
        assertTrue(aliceLike.success());
        assertFalse(aliceLike.data().match().isPresent());

        var bobLike = matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.cli(bobId), aliceId, Like.Direction.LIKE, true));
        assertTrue(bobLike.success());
        assertTrue(bobLike.data().match().isPresent());

        var send = messagingUseCases.sendMessage(new SendMessageCommand(UserContext.cli(aliceId), bobId, "Hi Bob!"));
        assertTrue(send.success());
        assertTrue(send.data().success());

        var conversations =
                messagingUseCases.listConversations(new ListConversationsQuery(UserContext.cli(aliceId), 20, 0));
        assertTrue(conversations.success());
        assertEquals(1, conversations.data().conversations().size());

        var thread = messagingUseCases.loadConversation(
                new MessagingUseCases.LoadConversationQuery(UserContext.cli(aliceId), bobId, 20, 0, false));
        assertTrue(thread.success());
        assertTrue(thread.data().canMessage());
        assertEquals(1, thread.data().messages().size());
        assertEquals("Hi Bob!", thread.data().messages().getFirst().content());
    }

    @Test
    @DisplayName("match then unmatch then rematch after cooldown succeeds end-to-end")
    void matchThenUnmatchThenRematchAfterCooldownSucceedsEndToEnd() {
        AppClock.setFixed(Instant.parse("2026-03-30T00:00:00Z"));
        try {
            TestStorages.Users userStorage = new TestStorages.Users();
            TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
            TestStorages.Communications communicationStorage = new TestStorages.Communications();
            TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
            AppConfig config = AppConfig.defaults();

            UUID aliceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID bobId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            User alice = activeUser(aliceId, "Alice", Gender.FEMALE, Set.of(Gender.MALE));
            User bob = activeUser(bobId, "Bob", Gender.MALE, Set.of(Gender.FEMALE));
            userStorage.save(alice);
            userStorage.save(bob);

            ServiceRegistry services =
                    createServices(config, userStorage, interactionStorage, communicationStorage, standoutStorage);

            MatchingUseCases matchingUseCases = services.getMatchingUseCases();
            SocialUseCases socialUseCases = services.getSocialUseCases();
            MessagingUseCases messagingUseCases = services.getMessagingUseCases();

            var aliceLike = matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.cli(aliceId), bobId, Like.Direction.LIKE, true));
            assertTrue(aliceLike.success());
            assertFalse(aliceLike.data().match().isPresent());

            var bobLike = matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.cli(bobId), aliceId, Like.Direction.LIKE, true));
            assertTrue(bobLike.success());
            assertTrue(bobLike.data().match().isPresent());

            var unmatch =
                    socialUseCases.unmatch(new SocialUseCases.RelationshipCommand(UserContext.cli(aliceId), bobId));
            assertTrue(unmatch.success());
            assertEquals(
                    datingapp.core.model.Match.MatchState.UNMATCHED,
                    interactionStorage
                            .get(datingapp.core.model.Match.generateId(aliceId, bobId))
                            .orElseThrow()
                            .getState());

            AppClock.setFixed(AppClock.now().plus(Duration.ofHours(169)));

            var aliceRematch = matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.cli(aliceId), bobId, Like.Direction.LIKE, true));
            assertTrue(aliceRematch.success());
            assertFalse(aliceRematch.data().match().isPresent());

            var bobRematch = matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.cli(bobId), aliceId, Like.Direction.LIKE, true));
            assertTrue(bobRematch.success());
            assertTrue(bobRematch.data().match().isPresent());

            var send = messagingUseCases.sendMessage(
                    new SendMessageCommand(UserContext.cli(aliceId), bobId, "We meet again!"));
            assertTrue(send.success());
            assertTrue(send.data().success());
        } finally {
            AppClock.reset();
        }
    }

    private static ServiceRegistry createServices(
            AppConfig config,
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            Standout.Storage standoutStorage) {
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
        ProfileService profileService = new ProfileService(userStorage);

        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, config);
        DailyPickService dailyPickService = new DefaultDailyPickService(analyticsStorage, candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator, userStorage, candidateFinder, standoutStorage, profileService, config);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactionStorage, new TestStorages.Undos(), config);

        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .candidateFinder(candidateFinder)
                .build();
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                .build();
        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        MatchQualityService matchQualityService =
                new MatchQualityService(userStorage, interactionStorage, config, compatibilityCalculator);
        ValidationService validationService = new ValidationService(config);
        LocationService locationService = new LocationService(validationService);
        AchievementService achievementService = new DefaultAchievementService(
                config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, profileService);

        return ServiceRegistry.builder()
                .config(config)
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .communicationStorage(communicationStorage)
                .analyticsStorage(analyticsStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .candidateFinder(candidateFinder)
                .matchingService(matchingService)
                .trustSafetyService(trustSafetyService)
                .activityMetricsService(activityMetricsService)
                .matchQualityService(matchQualityService)
                .profileService(profileService)
                .recommendationService(recommendationService)
                .dailyLimitService(dailyLimitService)
                .dailyPickService(dailyPickService)
                .standoutService(standoutService)
                .undoService(undoService)
                .compatibilityCalculator(compatibilityCalculator)
                .achievementService(achievementService)
                .connectionService(connectionService)
                .validationService(validationService)
                .locationService(locationService)
                .eventBus(new InProcessAppEventBus())
                .build();
    }

    private static User activeUser(UUID id, String name, Gender gender, Set<Gender> interestedIn) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(gender)
                .interestedIn(interestedIn)
                .location(32.0853, 34.7818)
                .bio("bio")
                .photoUrls(List.of("https://example.com/p.jpg"))
                .build();
    }
}
