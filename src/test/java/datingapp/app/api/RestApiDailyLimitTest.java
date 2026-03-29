package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
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
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API daily limit enforcement")
class RestApiDailyLimitTest {

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("user at like limit receives 409")
    void userAtLikeLimitReceives409() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();

        // Create config with dailyLikeLimit = 3
        AppConfig customConfig = AppConfig.builder().dailyLikeLimit(3).build();

        ServiceRegistry services =
                createServices(customConfig, userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");

        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);

        // Pre-populate 3 likes for alice (at the limit)
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, bobId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, carolId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, danaId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Fourth like should fail with 409
        UUID otherUserId = UUID.randomUUID();
        User otherUser = activeUser(otherUserId, "Other");
        userStorage.save(otherUser);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/like/" + otherUserId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode());
    }

    @Test
    @DisplayName("user at pass limit receives 409")
    void userAtPassLimitReceives409() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();

        // Create config with dailyPassLimit = 3
        AppConfig customConfig = AppConfig.builder().dailyPassLimit(3).build();

        ServiceRegistry services =
                createServices(customConfig, userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");

        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);

        // Pre-populate 3 passes for alice (at the limit)
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, bobId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, carolId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, danaId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Fourth pass should fail with 409
        UUID otherUserId = UUID.randomUUID();
        User otherUser = activeUser(otherUserId, "Other");
        userStorage.save(otherUser);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + otherUserId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode());
    }

    @Test
    @DisplayName("unlimited config allows any number of likes")
    void unlimitedConfigAllowsAnyNumberOfLikes() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();

        // Create config with unlimited likes (dailyLikeLimit = -1)
        AppConfig customConfig = AppConfig.builder().dailyLikeLimit(-1).build();

        ServiceRegistry services =
                createServices(customConfig, userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        userStorage.save(alice);

        // Create 50 other users
        java.util.List<UUID> targetIds = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "User-" + i);
            userStorage.save(user);
            targetIds.add(userId);
        }

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Like all 50 users - all should succeed
        int successCount = 0;
        for (int i = 0; i < 50; i++) {
            UUID targetId = targetIds.get(i);

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://localhost:" + port + "/api/users/" + aliceId + "/like/" + targetId))
                            .header("X-User-Id", aliceId.toString())
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                successCount++;
            }
        }

        // All 50 likes should succeed
        assertEquals(50, successCount);
    }

    private static ServiceRegistry createServices(
            AppConfig customConfig,
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            Standout.Storage standoutStorage) {
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, customConfig);
        ProfileService profileService =
                new ProfileService(customConfig, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(customConfig);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, customConfig);
        DailyPickService dailyPickService = new DefaultDailyPickService(
                userStorage, interactionStorage, analyticsStorage, candidateFinder, customConfig);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator, userStorage, candidateFinder, standoutStorage, profileService, customConfig);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactionStorage, new InMemoryUndoStorage(), customConfig);
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
                        trustSafetyStorage, interactionStorage, userStorage, customConfig, communicationStorage)
                .build();
        ConnectionService connectionService =
                new ConnectionService(customConfig, communicationStorage, interactionStorage, userStorage);
        MatchQualityService matchQualityService =
                new MatchQualityService(userStorage, interactionStorage, customConfig, compatibilityCalculator);
        ValidationService validationService = new ValidationService(customConfig);
        AchievementService achievementService = new DefaultAchievementService(
                customConfig, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, profileService);

        return ServiceRegistry.builder()
                .config(customConfig)
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
                .eventBus(new InProcessAppEventBus())
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }

    private static final class InMemoryUndoStorage implements Undo.Storage {
        @Override
        public void save(datingapp.core.metrics.SwipeState.Undo state) {
            // No-op for test
        }

        @Override
        public java.util.Optional<datingapp.core.metrics.SwipeState.Undo> findByUserId(UUID userId) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean delete(UUID userId) {
            return false;
        }

        @Override
        public int deleteExpired(java.time.Instant now) {
            return 0;
        }

        @Override
        public java.util.List<datingapp.core.metrics.SwipeState.Undo> findAll() {
            return java.util.List.of();
        }
    }

    private static final class SeededStandoutStorage implements Standout.Storage {
        @Override
        public void saveStandouts(UUID seekerId, java.util.List<Standout> standouts, java.time.LocalDate date) {
            // No-op for test
        }

        @Override
        public java.util.List<Standout> getStandouts(UUID seekerId, java.time.LocalDate date) {
            return java.util.List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, java.time.LocalDate date) {
            // No-op for test
        }

        @Override
        public int cleanup(java.time.LocalDate before) {
            return 0;
        }
    }
}
