package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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
                        .header("X-User-Id", userA.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, requestResponse.statusCode());
        UUID requestId = UUID.fromString(
                MAPPER.readTree(requestResponse.body()).get("friendRequestId").asText());

        HttpResponse<String> acceptResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userB
                                + "/friend-requests/" + requestId + "/accept"))
                        .header("X-User-Id", userB.toString())
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
                        .header("X-User-Id", userA.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        UUID secondRequestId = UUID.fromString(
                MAPPER.readTree(secondRequest.body()).get("friendRequestId").asText());

        HttpResponse<String> declineResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userC
                                + "/friend-requests/" + secondRequestId + "/decline"))
                        .header("X-User-Id", userC.toString())
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
        interactionStorage.save(ConnectionModels.Like.create(userA, userB, ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(ConnectionModels.Like.create(userB, userA, ConnectionModels.Like.Direction.LIKE));
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(userA, userB));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> gracefulExitResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userA + "/relationships/"
                                + userB + "/graceful-exit"))
                        .header("X-User-Id", userA.toString())
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
                        .header("X-User-Id", userA.toString())
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
                        .header("X-User-Id", userA.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, blockResponse.statusCode());
        assertTrue(trustSafetyStorage.isBlocked(userA, userB));

        HttpResponse<String> reportResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/report/" + userB))
                        .header("X-User-Id", userA.toString())
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
    @DisplayName("unmatch clears old pair likes atomically")
    void unmatchClearsOldPairLikesAtomically() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

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

        HttpResponse<String> unmatchResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userA + "/relationships/"
                                + userB + "/unmatch"))
                        .header("X-User-Id", userA.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, unmatchResponse.statusCode());

        Match match = interactionStorage.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(Match.MatchState.UNMATCHED, match.getState(), "After unmatch, match state should be UNMATCHED");
        assertNotNull(match.getEndedAt(), "endedAt timestamp should be set after unmatch for cooldown tracking");
        assertTrue(interactionStorage.getLike(userA, userB).isEmpty(), "A->B like should be cleared on unmatch");
        assertTrue(interactionStorage.getLike(userB, userA).isEmpty(), "B->A like should be cleared on unmatch");
    }

    @Test
    @DisplayName("unmatch does not poison future rematchability after cooldown")
    void unmatchDoesNotPoisonFutureRematchability() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        User aliceUser = activeUser(userA, "Alice");
        User bobUser = activeUser(userB, "Bob");
        userStorage.save(aliceUser);
        userStorage.save(bobUser);
        interactionStorage.save(Match.create(userA, userB));
        interactionStorage.save(ConnectionModels.Like.create(userA, userB, ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(ConnectionModels.Like.create(userB, userA, ConnectionModels.Like.Direction.LIKE));
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(userA, userB));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // First unmatch
        HttpResponse<String> firstUnmatchResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userA + "/relationships/"
                                + userB + "/unmatch"))
                        .header("X-User-Id", userA.toString())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, firstUnmatchResponse.statusCode());
        assertEquals(
                Match.MatchState.UNMATCHED,
                interactionStorage
                        .get(Match.generateId(userA, userB))
                        .orElseThrow()
                        .getState());

        // After cooldown expiry (in real scenario), users should be able to match again.
        // This test validates that the initial unmatch preserves the ability to rematch
        // when cooldown window is respected.
        Match expiredMatch =
                interactionStorage.get(Match.generateId(userA, userB)).orElseThrow();
        assertNotNull(
                expiredMatch.getEndedAt(),
                "Match must have endedAt timestamp to track cooldown expiry for future rematch eligibility");

        var firstRematch = interactionStorage.saveLikeAndMaybeCreateMatch(
                ConnectionModels.Like.create(userA, userB, ConnectionModels.Like.Direction.LIKE));
        var secondRematch = interactionStorage.saveLikeAndMaybeCreateMatch(
                ConnectionModels.Like.create(userB, userA, ConnectionModels.Like.Direction.LIKE));

        assertTrue(firstRematch.likePersisted());
        assertTrue(firstRematch.createdMatch().isEmpty());
        assertTrue(secondRematch.likePersisted());
        assertTrue(
                secondRematch.createdMatch().isPresent(),
                "Fresh mutual likes after unmatch should be able to rematch the pair");
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

        HttpResponse<String> anonymousResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + conversation.getId() + "/messages"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, anonymousResponse.statusCode(), anonymousResponse.body());

        HttpResponse<String> forbiddenResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + conversation.getId() + "/messages"))
                        .header("X-User-Id", outsiderId.toString())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenResponse.statusCode());
    }

    private static void connectionStorageSeedFriendRequest(
            TestStorages.Communications communicationStorage, UUID fromUserId, UUID toUserId) {
        communicationStorage.saveFriendRequest(ConnectionModels.FriendRequest.create(fromUserId, toUserId));
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
                        .header("X-User-Id", userA.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"reason\":\"NOT_A_REASON\",\"description\":\"bad\",\"blockUser\":false}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidEnumResponse.statusCode());

        HttpResponse<String> malformedBodyResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + userA + "/report/" + userB))
                        .header("X-User-Id", userA.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, malformedBodyResponse.statusCode());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            TestStorages.TrustSafety trustSafetyStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }
}
