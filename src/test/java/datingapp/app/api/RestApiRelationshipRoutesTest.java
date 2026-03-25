package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels;
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
import datingapp.core.model.Match;
import datingapp.core.model.User;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API relationship and moderation routes")
class RestApiRelationshipRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("friend request routes support request accept and decline")
    void friendRequestRoutesSupportRequestAcceptAndDecline() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        interactionStorage.save(Match.create(userA, userB));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> requestResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + userA + "/friend-requests/" + userB))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, requestResponse.statusCode());
        UUID requestId = UUID.fromString(
                MAPPER.readTree(requestResponse.body()).get("friendRequestId").asText());

        HttpResponse<String> acceptResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userB
                                + "/friend-requests/" + requestId + "/accept"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, acceptResponse.statusCode());

        Match acceptedMatch =
                interactionStorage.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(Match.MatchState.FRIENDS, acceptedMatch.getState());

        // Create another request and decline it
        UUID userC = UUID.randomUUID();
        userStorage.save(activeUser(userC, "Cara"));
        interactionStorage.save(Match.create(userA, userC));

        HttpResponse<String> secondRequest = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + userA + "/friend-requests/" + userC))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        UUID secondRequestId = UUID.fromString(
                MAPPER.readTree(secondRequest.body()).get("friendRequestId").asText());

        HttpResponse<String> declineResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userC
                                + "/friend-requests/" + secondRequestId + "/decline"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, declineResponse.statusCode());

        ConnectionModels.FriendRequest declined =
                communicationStorage.getFriendRequest(secondRequestId).orElseThrow();
        assertEquals(ConnectionModels.FriendRequest.Status.DECLINED, declined.status());
    }

    @Test
    @DisplayName("friend request list route returns pending requests for a user")
    void friendRequestListRouteReturnsPendingRequestsForAUser() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        interactionStorage.save(Match.create(userA, userB));

        connectionStorageSeedFriendRequest(communicationStorage, userA, userB);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> listResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userB + "/friend-requests"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        JsonNode json = MAPPER.readTree(listResponse.body());
        assertEquals(1, json.get("friendRequests").size());
        assertEquals(
                userA.toString(),
                json.get("friendRequests").get(0).get("fromUserId").asText());
        assertEquals(
                userB.toString(),
                json.get("friendRequests").get(0).get("toUserId").asText());
    }

    @Test
    @DisplayName("relationship and moderation routes support graceful exit unmatch block and report")
    void relationshipAndModerationRoutesSupportGracefulExitUnmatchBlockAndReport() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, trustSafetyStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        interactionStorage.save(Match.create(userA, userB));
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(userA, userB));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> gracefulExitResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userA + "/relationships/"
                                + userB + "/graceful-exit"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, gracefulExitResponse.statusCode());
        assertEquals(
                Match.MatchState.GRACEFUL_EXIT,
                interactionStorage
                        .get(Match.generateId(userA, userB))
                        .orElseThrow()
                        .getState());

        // Reset for unmatch
        Match rematch = Match.create(userA, userB);
        interactionStorage.save(rematch);
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(userA, userB));

        HttpResponse<String> unmatchResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userA + "/relationships/"
                                + userB + "/unmatch"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, unmatchResponse.statusCode());
        assertEquals(
                Match.MatchState.UNMATCHED,
                interactionStorage
                        .get(Match.generateId(userA, userB))
                        .orElseThrow()
                        .getState());

        HttpResponse<String> blockResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/block/" + userB))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, blockResponse.statusCode());
        assertTrue(trustSafetyStorage.isBlocked(userA, userB));

        HttpResponse<String> reportResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/report/" + userB))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"reason\":\"HARASSMENT\",\"description\":\"Inappropriate messages\",\"blockUser\":true}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportResponse.statusCode());
        JsonNode reportJson = MAPPER.readTree(reportResponse.body());
        assertTrue(reportJson.get("success").asBoolean());
        assertTrue(trustSafetyStorage.hasReported(userA, userB));
    }

    @Test
    @DisplayName("conversation messages require a matching acting user and reject non-participants")
    void conversationMessagesRequireMatchingActingUser() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        userStorage.save(activeUser(outsiderId, "Mallory"));
        interactionStorage.save(Match.create(userA, userB));

        ConnectionModels.Conversation conversation = ConnectionModels.Conversation.create(userA, userB);
        communicationStorage.saveConversation(conversation);
        communicationStorage.saveMessage(ConnectionModels.Message.create(conversation.getId(), userA, "hello"));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> successResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + conversation.getId() + "/messages?userId=" + userA))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, successResponse.statusCode(), successResponse.body());

        HttpResponse<String> forbiddenResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + conversation.getId() + "/messages?userId=" + outsiderId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenResponse.statusCode());

        HttpResponse<String> missingIdentityResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + conversation.getId() + "/messages"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, missingIdentityResponse.statusCode());
    }

    @Test
    @DisplayName("report route rejects invalid enum values and malformed bodies")
    void reportRouteRejectsInvalidPayloads() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, trustSafetyStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        userStorage.save(activeUser(userA, "EnumAlice"));
        userStorage.save(activeUser(userB, "EnumBob"));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> invalidEnumResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/report/" + userB))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"reason\":\"NOT_A_REASON\",\"description\":\"bad\",\"blockUser\":false}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidEnumResponse.statusCode());

        HttpResponse<String> malformedBodyResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/report/" + userB))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, malformedBodyResponse.statusCode());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return createServices(userStorage, interactionStorage, communicationStorage, new TestStorages.TrustSafety());
    }

    private static void connectionStorageSeedFriendRequest(
            TestStorages.Communications communicationStorage, UUID fromUserId, UUID toUserId) {
        communicationStorage.saveFriendRequest(ConnectionModels.FriendRequest.create(fromUserId, toUserId));
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            TestStorages.TrustSafety trustSafetyStorage) {
        AppConfig config = AppConfig.defaults();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, config);
        DailyPickService dailyPickService =
                new DefaultDailyPickService(userStorage, interactionStorage, analyticsStorage, candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator,
                userStorage,
                candidateFinder,
                new InMemoryStandoutStorage(),
                profileService,
                config);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactionStorage, new InMemoryUndoStorage(), config);
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
                .eventBus(new InProcessAppEventBus())
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }

    private static final class InMemoryUndoStorage implements Undo.Storage {
        private final Map<UUID, Undo> byUserId = new HashMap<>();

        @Override
        public void save(Undo state) {
            byUserId.put(state.userId(), state);
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return byUserId.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            List<UUID> toDelete = new ArrayList<>();
            for (Undo undo : byUserId.values()) {
                if (undo.isExpired(now)) {
                    toDelete.add(undo.userId());
                }
            }
            toDelete.forEach(byUserId::remove);
            return toDelete.size();
        }

        @Override
        public List<Undo> findAll() {
            return List.copyOf(byUserId.values());
        }
    }

    private static final class InMemoryStandoutStorage implements Standout.Storage {
        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, java.time.LocalDate date) {
            // no-op
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, java.time.LocalDate date) {
            return List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, java.time.LocalDate date) {
            // no-op
        }

        @Override
        public int cleanup(java.time.LocalDate before) {
            return 0;
        }
    }
}
