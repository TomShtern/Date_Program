package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
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
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
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

@DisplayName("REST API Phase 2 routes")
class RestApiPhaseTwoRoutesTest {

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
    @DisplayName("notification, stats, and achievement routes support list/read/read-all")
    void notificationStatsAndAchievementRoutesSupportListReadAndReadAll() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        Notification firstNotification = Notification.create(
                aliceId,
                Notification.Type.NEW_MESSAGE,
                "New Message",
                "Bob sent a message.",
                Map.of("senderId", bobId.toString()));
        Notification secondNotification = Notification.create(
                aliceId,
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You matched with Bob.",
                Map.of("otherUserId", bobId.toString()));
        communicationStorage.saveNotification(firstNotification);
        communicationStorage.saveNotification(secondNotification);
        services.getAchievementService().checkAndUnlock(aliceId);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> notificationsResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/notifications"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, notificationsResponse.statusCode());
        JsonNode notificationsJson = MAPPER.readTree(notificationsResponse.body());
        assertEquals(2, notificationsJson.size());

        HttpResponse<String> unreadOnlyResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/notifications?unreadOnly=true"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, unreadOnlyResponse.statusCode());
        assertEquals(2, MAPPER.readTree(unreadOnlyResponse.body()).size());

        HttpResponse<String> readSingleResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/notifications/" + firstNotification.id() + "/read"))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, readSingleResponse.statusCode());

        HttpResponse<String> forbiddenReadResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + bobId + "/notifications/"
                                + secondNotification.id() + "/read"))
                        .header("X-User-Id", bobId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenReadResponse.statusCode());

        HttpResponse<String> readAllResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/notifications/read-all"))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readAllResponse.statusCode());
        JsonNode readAllJson = MAPPER.readTree(readAllResponse.body());
        assertEquals(1, readAllJson.get("updatedCount").asInt());

        HttpResponse<String> statsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/stats"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, statsResponse.statusCode());
        JsonNode statsJson = MAPPER.readTree(statsResponse.body());
        assertEquals(aliceId.toString(), statsJson.get("userId").asText());

        HttpResponse<String> achievementsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/achievements?checkForNew=true"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, achievementsResponse.statusCode());
        JsonNode achievementsJson = MAPPER.readTree(achievementsResponse.body());
        assertTrue(achievementsJson.get("unlockedCount").asInt() >= 1);
    }

    @Test
    @DisplayName("matching support routes expose pending likers, standouts, quality, and undo")
    void matchingSupportRoutesExposePendingLikersStandoutsQualityAndUndo() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();
        UUID eveId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");
        User eve = activeUser(eveId, "Eve");
        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);
        userStorage.save(eve);

        Match aliceBobMatch = Match.create(aliceId, bobId);
        interactionStorage.save(aliceBobMatch);
        interactionStorage.save(Like.create(carolId, aliceId, Like.Direction.LIKE));

        standoutStorage.saveStandouts(
                aliceId,
                List.of(Standout.create(aliceId, danaId, LocalDate.now(), 1, 97, "Shared interests and strong fit")),
                LocalDate.now());

        services.getMatchingUseCases()
                .processSwipe(new ProcessSwipeCommand(UserContext.api(aliceId), alice, eve, true, false, false));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> pendingLikersResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pending-likers"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pendingLikersResponse.statusCode());
        JsonNode pendingLikersJson = MAPPER.readTree(pendingLikersResponse.body());
        assertEquals(1, pendingLikersJson.get("pendingLikers").size());
        assertEquals(
                carolId.toString(),
                pendingLikersJson.get("pendingLikers").get(0).get("userId").asText());

        HttpResponse<String> standoutsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/standouts"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, standoutsResponse.statusCode());
        JsonNode standoutsJson = MAPPER.readTree(standoutsResponse.body());
        assertEquals(1, standoutsJson.get("standouts").size());
        assertEquals(
                danaId.toString(),
                standoutsJson.get("standouts").get(0).get("standoutUserId").asText());

        HttpResponse<String> matchQualityResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/match-quality/" + aliceBobMatch.getId()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, matchQualityResponse.statusCode());
        JsonNode matchQualityJson = MAPPER.readTree(matchQualityResponse.body());
        assertEquals(aliceBobMatch.getId(), matchQualityJson.get("matchId").asText());

        HttpResponse<String> undoResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/undo"))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, undoResponse.statusCode());
        JsonNode undoJson = MAPPER.readTree(undoResponse.body());
        assertTrue(undoJson.get("success").asBoolean());
    }

    @Test
    @DisplayName("profile update and conversation mutation routes work end to end")
    void profileUpdateAndConversationMutationRoutesWorkEndToEnd() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        alice.setBio("Original bio");
        User bob = activeUser(bobId, "Bob");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "bio":"Updated bio",
                                  "maxDistanceKm":60,
                                  "minAge":21,
                                  "maxAge":32,
                                  "heightCm":170,
                                  "smoking":"NEVER",
                                  "drinking":"SOCIALLY",
                                  "wantsKids":"OPEN",
                                  "lookingFor":"LONG_TERM",
                                  "education":"BACHELORS",
                                  "interests":["MUSIC","TRAVEL"]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateProfileResponse.statusCode());
        JsonNode profileJson = MAPPER.readTree(updateProfileResponse.body());
        assertEquals("Updated bio", profileJson.get("bio").asText());

        User savedAlice = userStorage.get(aliceId).orElseThrow();
        assertEquals("Updated bio", savedAlice.getBio());
        assertEquals(60, savedAlice.getMaxDistanceKm());
        assertTrue(savedAlice.getInterests().contains(Interest.MUSIC));
        assertEquals(Lifestyle.LookingFor.LONG_TERM, savedAlice.getLookingFor());

        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId)
                                + "/messages"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());
        UUID messageId = UUID.fromString(
                MAPPER.readTree(sendMessageResponse.body()).get("id").asText());
        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);

        HttpResponse<String> deleteMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/" + conversationId
                                + "/messages/" + messageId))
                        .header("X-User-Id", aliceId.toString())
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteMessageResponse.statusCode());

        HttpResponse<String> archiveConversationResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/conversations/" + conversationId + "/archive"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"UNMATCH\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, archiveConversationResponse.statusCode());
        var archivedConversation =
                communicationStorage.getConversation(conversationId).orElseThrow();
        MatchArchiveReason archiveReason = archivedConversation.getUserA().equals(aliceId)
                ? archivedConversation.getUserAArchiveReason()
                : archivedConversation.getUserBArchiveReason();
        assertEquals(MatchArchiveReason.UNMATCH, archiveReason);

        HttpResponse<String> deleteConversationResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/conversations/" + conversationId))
                        .header("X-User-Id", aliceId.toString())
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteConversationResponse.statusCode());
        assertTrue(communicationStorage.getConversation(conversationId).isEmpty());

        HttpResponse<String> archiveMatchResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/matches/"
                                + Match.generateId(aliceId, bobId) + "/archive"))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, archiveMatchResponse.statusCode());
        assertTrue(interactionStorage.get(Match.generateId(aliceId, bobId)).isEmpty());
    }

    @Test
    @DisplayName("mutating conversation routes require X-User-Id and do not accept query fallback")
    void mutatingConversationRoutesRequireHeaderAndDoNotAcceptQueryFallback() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice"));
        userStorage.save(activeUser(bobId, "Bob"));
        interactionStorage.save(Match.create(aliceId, bobId));

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> missingHeaderResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/" + conversationId
                                + "/messages?userId=" + aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, missingHeaderResponse.statusCode(), missingHeaderResponse.body());
    }

    @Test
    @DisplayName("like, pass, conversations, and send message failures use the shared use-case failure mapper")
    void affectedRoutesUseTheSharedUseCaseFailureMapper() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice"));
        userStorage.save(activeUser(bobId, "Bob"));
        interactionStorage.save(Match.create(aliceId, bobId));

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        server = new RestApiServer(services, 0);
        replaceMatchingUseCases(server, new FailingMatchingUseCases(services));
        replaceMessagingUseCases(server, new FailingMessagingUseCases(services));
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> likeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/like/" + bobId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, likeResponse.statusCode(), likeResponse.body());

        HttpResponse<String> passResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + bobId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, passResponse.statusCode(), passResponse.body());

        HttpResponse<String> conversationsResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/conversations"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, conversationsResponse.statusCode(), conversationsResponse.body());

        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, sendMessageResponse.statusCode(), sendMessageResponse.body());
    }

    @Test
    @DisplayName("profile save, match, message, and graceful-exit flow work end to end")
    void profileSaveMatchMessageAndGracefulExitFlowWorksEndToEnd() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice HappyPath");
        alice.setBio("Before update");
        User bob = activeUser(bobId, "Bob HappyPath");
        userStorage.save(alice);
        userStorage.save(bob);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "bio":"Updated happy-path bio",
                                  "maxDistanceKm":70,
                                  "minAge":22,
                                  "maxAge":34,
                                  "heightCm":168,
                                  "smoking":"NEVER",
                                  "drinking":"SOCIALLY",
                                  "wantsKids":"OPEN",
                                  "lookingFor":"LONG_TERM",
                                  "education":"BACHELORS",
                                  "interests":["MUSIC","TRAVEL"]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateProfileResponse.statusCode());
        assertEquals(
                "Updated happy-path bio", userStorage.get(aliceId).orElseThrow().getBio());

        HttpResponse<String> firstLikeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/like/" + bobId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, firstLikeResponse.statusCode());

        HttpResponse<String> secondLikeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + bobId + "/like/" + aliceId))
                        .header("X-User-Id", bobId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, secondLikeResponse.statusCode());
        JsonNode likeJson = MAPPER.readTree(secondLikeResponse.body());
        assertTrue(likeJson.get("isMatch").asBoolean());

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello from the happy path\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());

        HttpResponse<String> gracefulExitResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/relationships/" + bobId + "/graceful-exit"))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, gracefulExitResponse.statusCode());

        Match updatedMatch =
                interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.MatchState.GRACEFUL_EXIT, updatedMatch.getState());
        var storedConversation =
                communicationStorage.getConversation(conversationId).orElseThrow();
        assertNotNull(storedConversation.getLastMessageAt());
    }

    @Test
    @DisplayName("pass route forwards daily-limit enforcement on record-like commands")
    void passRouteForwardsDailyLimitEnforcementOnRecordLikeCommands() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice Pass"));
        userStorage.save(activeUser(bobId, "Bob Pass"));

        CapturingMatchingUseCases matchingUseCases = new CapturingMatchingUseCases(services);
        server = new RestApiServer(services, 0);
        replaceMatchingUseCases(server, matchingUseCases);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> passResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + bobId))
                        .header("X-User-Id", aliceId.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, passResponse.statusCode());
        assertNotNull(matchingUseCases.lastCommand);
        assertEquals(UserContext.api(aliceId), matchingUseCases.lastCommand.context());
        assertEquals(bobId, matchingUseCases.lastCommand.targetUserId());
        assertEquals(Like.Direction.PASS, matchingUseCases.lastCommand.direction());
        assertTrue(matchingUseCases.lastCommand.enforceDailyLimit());
    }

    @Test
    @DisplayName("report-plus-block flow prevents follow-up messaging and preserves moderation state")
    void reportAndBlockFlowPreventsFollowUpMessaging() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice FailurePath");
        User bob = activeUser(bobId, "Bob FailurePath");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Before block\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());

        HttpResponse<String> reportResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/report/" + bobId))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"reason\":\"HARASSMENT\",\"description\":\"Unsafe behavior\",\"blockUser\":true}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportResponse.statusCode());
        JsonNode reportJson = MAPPER.readTree(reportResponse.body());
        assertTrue(reportJson.get("success").asBoolean());
        assertTrue(reportJson.get("blockedByReporter").asBoolean());

        HttpResponse<String> blockedMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header("X-User-Id", aliceId.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"This should fail\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, blockedMessageResponse.statusCode());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            Standout.Storage standoutStorage) {
        AppConfig config = AppConfig.defaults();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();

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
                compatibilityCalculator, userStorage, candidateFinder, standoutStorage, profileService, config);
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

    private static void replaceMatchingUseCases(RestApiServer server, MatchingUseCases matchingUseCases) {
        try {
            java.lang.reflect.Field field = RestApiServer.class.getDeclaredField("matchingUseCases");
            field.setAccessible(true);
            field.set(server, matchingUseCases);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to replace matching use cases in RestApiServer", e);
        }
    }

    private static void replaceMessagingUseCases(RestApiServer server, MessagingUseCases messagingUseCases) {
        try {
            java.lang.reflect.Field field = RestApiServer.class.getDeclaredField("messagingUseCases");
            field.setAccessible(true);
            field.set(server, messagingUseCases);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to replace messaging use cases in RestApiServer", e);
        }
    }

    private static final class CapturingMatchingUseCases extends MatchingUseCases {
        private datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand lastCommand;

        private CapturingMatchingUseCases(ServiceRegistry services) {
            super(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getDailyLimitService(),
                    services.getDailyPickService(),
                    services.getStandoutService(),
                    services.getUndoService(),
                    services.getInteractionStorage(),
                    services.getUserStorage(),
                    services.getMatchQualityService(),
                    services.getEventBus());
        }

        @Override
        public UseCaseResult<datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult> recordLike(
                datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand command) {
            lastCommand = command;
            Like like = Like.create(command.context().userId(), command.targetUserId(), command.direction());
            return UseCaseResult.success(
                    new datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult(like, Optional.empty()));
        }
    }

    private static final class FailingMatchingUseCases extends MatchingUseCases {
        private FailingMatchingUseCases(ServiceRegistry services) {
            super(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getDailyLimitService(),
                    services.getDailyPickService(),
                    services.getStandoutService(),
                    services.getUndoService(),
                    services.getInteractionStorage(),
                    services.getUserStorage(),
                    services.getMatchQualityService(),
                    services.getEventBus());
        }

        @Override
        public UseCaseResult<datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult> recordLike(
                datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand command) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated matching failure"));
        }
    }

    private static final class FailingMessagingUseCases extends MessagingUseCases {
        private FailingMessagingUseCases(ServiceRegistry services) {
            super(services.getConnectionService(), services.getEventBus());
        }

        @Override
        public UseCaseResult<MessagingUseCases.ConversationListResult> listConversations(ListConversationsQuery query) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated conversation failure"));
        }

        @Override
        public UseCaseResult<datingapp.core.connection.ConnectionService.SendResult> sendMessage(
                SendMessageCommand command) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated message failure"));
        }
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

    private static final class SeededStandoutStorage implements Standout.Storage {
        private final Map<String, List<Standout>> standoutsByKey = new HashMap<>();

        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            standoutsByKey.put(key(seekerId, date), new ArrayList<>(standouts));
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return List.copyOf(standoutsByKey.getOrDefault(key(seekerId, date), List.of()));
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            String key = key(seekerId, date);
            List<Standout> existing = standoutsByKey.getOrDefault(key, List.of());
            List<Standout> updated = existing.stream()
                    .map(standout -> standout.standoutUserId().equals(standoutUserId)
                            ? standout.withInteraction(AppClock.now())
                            : standout)
                    .toList();
            standoutsByKey.put(key, new ArrayList<>(updated));
        }

        @Override
        public int cleanup(LocalDate before) {
            int originalSize = standoutsByKey.size();
            standoutsByKey.entrySet().removeIf(entry -> LocalDate.parse(
                            entry.getKey().split("\\|")[1])
                    .isBefore(before));
            return originalSize - standoutsByKey.size();
        }

        private static String key(UUID seekerId, LocalDate date) {
            return seekerId + "|" + date;
        }
    }
}
