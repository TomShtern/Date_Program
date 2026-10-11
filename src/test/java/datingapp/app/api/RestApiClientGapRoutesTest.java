package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("REST API routes the Flutter client was missing")
class RestApiClientGapRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final HttpClient client = HttpClient.newHttpClient();

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.Communications communicationStorage;
    private ServiceRegistry services;
    private RestApiServer server;
    private int port;

    private UUID aliceId;
    private UUID bobId;
    private UUID carolId;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        communicationStorage = new TestStorages.Communications();
        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();
        carolId = UUID.randomUUID();
        userStorage.save(TestUserFactory.createActiveUser(aliceId, "Alice"));
        userStorage.save(TestUserFactory.createActiveUser(bobId, "Bob"));
        userStorage.save(TestUserFactory.createActiveUser(carolId, "Carol"));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/presence")
    class Presence {

        @Test
        @DisplayName("a matched user can read the other user's presence")
        void matchedUserCanReadPresence() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));

            HttpResponse<String> response = get("/api/users/" + bobId + "/presence", aliceId);

            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = MAPPER.readTree(response.body());
            assertTrue(body.hasNonNull("status"), response.body());
            assertTrue(body.has("typing"), response.body());
            assertNotNull(Instant.parse(body.get("checkedAt").asText()));
        }

        @Test
        @DisplayName("typing is reported for the pair's own conversation")
        void typingIsReportedForOwnConversation() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));
            String conversationId = Conversation.generateId(aliceId, bobId);

            assertEquals(
                    200,
                    post("/api/users/" + bobId + "/typing?conversationId=" + conversationId, bobId, null)
                            .statusCode());
            HttpResponse<String> response =
                    get("/api/users/" + bobId + "/presence?conversationId=" + conversationId, aliceId);

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(MAPPER.readTree(response.body()).get("typing").asBoolean(), response.body());
        }

        @Test
        @DisplayName("a user can still read their own presence")
        void userCanReadOwnPresence() throws Exception {
            startServer(AppConfig.defaults());

            assertEquals(
                    200, get("/api/users/" + aliceId + "/presence", aliceId).statusCode());
        }

        @Test
        @DisplayName("an unmatched user gets 403")
        void unmatchedUserIsForbidden() throws Exception {
            startServer(AppConfig.defaults());

            HttpResponse<String> response = get("/api/users/" + bobId + "/presence", aliceId);

            assertEquals(403, response.statusCode(), response.body());
            assertEquals(
                    "FORBIDDEN", MAPPER.readTree(response.body()).get("code").asText());
        }

        @Test
        @DisplayName("a conversationId that is not the pair's conversation gets 403")
        void foreignConversationIdIsForbidden() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));
            interactionStorage.save(Match.create(bobId, carolId));

            HttpResponse<String> response = get(
                    "/api/users/" + bobId + "/presence?conversationId=" + Conversation.generateId(bobId, carolId),
                    aliceId);

            assertEquals(403, response.statusCode(), response.body());
        }

        @Test
        @DisplayName("a blocked pair gets 403 even though they were matched")
        void blockedPairIsForbidden() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));
            HttpResponse<String> block = post("/api/users/" + bobId + "/block/" + aliceId, bobId, null);
            assertTrue(block.statusCode() < 300, block.body());

            assertEquals(403, get("/api/users/" + bobId + "/presence", aliceId).statusCode());
            assertEquals(403, get("/api/users/" + aliceId + "/presence", bobId).statusCode());
        }

        @Test
        @DisplayName("a request without a token gets 401")
        void missingTokenIsUnauthorized() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri("/api/users/" + bobId + "/presence"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode(), response.body());
        }
    }

    @Nested
    @DisplayName("conversation delete and archive")
    class Conversations {

        private String conversationId;

        @BeforeEach
        void matchAliceAndBob() {
            startServer(AppConfig.defaults());
            interactionStorage.save(Match.create(aliceId, bobId));
            conversationId = Conversation.generateId(aliceId, bobId);
        }

        @Test
        @DisplayName("delete hides the conversation for the caller only and a new message brings it back")
        void deleteHidesForCallerOnlyAndNewMessageRevivesIt() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());

            HttpResponse<String> delete = delete("/api/users/" + aliceId + "/conversations/" + conversationId, aliceId);
            assertEquals(204, delete.statusCode(), delete.body());

            assertEquals(0, listConversations(aliceId, "").size());
            assertEquals(1, listConversations(bobId, "").size());

            HttpResponse<String> reply = sendMessage(bobId, "Hi Alice");
            assertEquals(201, reply.statusCode(), reply.body());

            JsonNode aliceList = listConversations(aliceId, "");
            assertEquals(1, aliceList.size());
            assertEquals(2, aliceList.get(0).get("messageCount").asInt());
        }

        @Test
        @DisplayName("the caller who deleted can also start the conversation again")
        void callerCanMessageAgainAfterDeleting() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());
            assertEquals(
                    204,
                    delete("/api/users/" + aliceId + "/conversations/" + conversationId, aliceId)
                            .statusCode());

            HttpResponse<String> again = sendMessage(aliceId, "Me again");

            assertEquals(201, again.statusCode(), again.body());
            assertEquals(1, listConversations(aliceId, "").size());
        }

        @Test
        @DisplayName("archive is per user, is reported on each conversation, and can be filtered")
        void archiveIsReportedAndFilterable() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());
            assertFalse(listConversations(aliceId, "").get(0).get("archived").asBoolean());

            assertEquals(
                    204,
                    post("/api/users/" + aliceId + "/conversations/" + conversationId + "/archive", aliceId, null)
                            .statusCode());

            assertTrue(listConversations(aliceId, "").get(0).get("archived").asBoolean());
            assertFalse(listConversations(bobId, "").get(0).get("archived").asBoolean());
            assertEquals(1, listConversations(aliceId, "?archived=true").size());
            assertEquals(0, listConversations(aliceId, "?archived=false").size());
            assertEquals(0, listConversations(bobId, "?archived=true").size());
            assertEquals(1, listConversations(bobId, "?archived=false").size());
        }

        @Test
        @DisplayName("a new incoming message does not un-archive the conversation")
        void incomingMessageKeepsConversationArchived() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());
            assertEquals(
                    204,
                    post("/api/users/" + aliceId + "/conversations/" + conversationId + "/archive", aliceId, null)
                            .statusCode());

            assertEquals(201, sendMessage(bobId, "Still there?").statusCode());

            assertTrue(listConversations(aliceId, "").get(0).get("archived").asBoolean());
        }

        @Test
        @DisplayName("unarchive clears the caller's archive flag")
        void unarchiveClearsTheFlag() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());
            assertEquals(
                    204,
                    post("/api/users/" + aliceId + "/conversations/" + conversationId + "/archive", aliceId, null)
                            .statusCode());

            HttpResponse<String> unarchive =
                    post("/api/users/" + aliceId + "/conversations/" + conversationId + "/unarchive", aliceId, null);

            assertEquals(204, unarchive.statusCode(), unarchive.body());
            assertFalse(listConversations(aliceId, "").get(0).get("archived").asBoolean());
            assertEquals(1, listConversations(aliceId, "?archived=false").size());
        }

        @Test
        @DisplayName("unarchive of a conversation the caller is not part of gets 400")
        void unarchiveOfForeignConversationIsRejected() throws Exception {
            assertEquals(201, sendMessage(aliceId, "Hello Bob").statusCode());

            HttpResponse<String> response =
                    post("/api/users/" + carolId + "/conversations/" + conversationId + "/unarchive", carolId, null);

            assertEquals(400, response.statusCode(), response.body());
            assertEquals(
                    "BAD_REQUEST", MAPPER.readTree(response.body()).get("code").asText());
        }

        @Test
        @DisplayName("an archived filter value other than true or false gets 400")
        void invalidArchivedFilterIsRejected() throws Exception {
            HttpResponse<String> response = get("/api/users/" + aliceId + "/conversations?archived=maybe", aliceId);

            assertEquals(400, response.statusCode(), response.body());
        }

        private HttpResponse<String> sendMessage(UUID senderId, String content) throws Exception {
            return post(
                    "/api/conversations/" + conversationId + "/messages",
                    senderId,
                    "{\"senderId\":\"" + senderId + "\",\"content\":\"" + content + "\"}");
        }

        private JsonNode listConversations(UUID userId, String query) throws Exception {
            HttpResponse<String> response = get("/api/users/" + userId + "/conversations" + query, userId);
            assertEquals(200, response.statusCode(), response.body());
            return MAPPER.readTree(response.body());
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/daily-status")
    class DailyStatus {

        @Test
        @DisplayName("reports used and remaining per kind with the reset instant")
        void reportsUsedAndRemainingPerKind() throws Exception {
            startServer(AppConfig.builder()
                    .dailyLikeLimit(3)
                    .dailyPassLimit(5)
                    .dailySuperLikeLimit(2)
                    .build());
            assertTrue(post("/api/users/" + aliceId + "/like/" + bobId, aliceId, null)
                            .statusCode()
                    < 300);
            assertTrue(post("/api/users/" + aliceId + "/pass/" + carolId, aliceId, null)
                            .statusCode()
                    < 300);

            HttpResponse<String> response = get("/api/users/" + aliceId + "/daily-status", aliceId);

            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = MAPPER.readTree(response.body());
            assertQuota(body.get("likes"), 1, 2);
            assertQuota(body.get("passes"), 1, 4);
            assertQuota(body.get("superLikes"), 0, 2);
            assertTrue(Instant.parse(body.get("resetsAt").asText()).isAfter(AppClock.now()), response.body());
            assertTrue(body.hasNonNull("date"), response.body());
        }

        @Test
        @DisplayName("an unlimited kind is flagged and carries no remaining number")
        void unlimitedKindIsFlagged() throws Exception {
            startServer(AppConfig.builder().dailyLikeLimit(-1).build());

            HttpResponse<String> response = get("/api/users/" + aliceId + "/daily-status", aliceId);

            assertEquals(200, response.statusCode(), response.body());
            JsonNode likes = MAPPER.readTree(response.body()).get("likes");
            assertTrue(likes.get("unlimited").asBoolean(), response.body());
            assertTrue(likes.get("remaining").isNull(), response.body());
            assertEquals(0, likes.get("used").asInt());
        }

        @Test
        @DisplayName("another user's daily status gets 403")
        void otherUsersDailyStatusIsForbidden() throws Exception {
            startServer(AppConfig.defaults());

            assertEquals(
                    403, get("/api/users/" + bobId + "/daily-status", aliceId).statusCode());
        }

        private void assertQuota(JsonNode quota, int used, int remaining) {
            assertEquals(used, quota.get("used").asInt(), quota.toString());
            assertEquals(remaining, quota.get("remaining").asInt(), quota.toString());
            assertFalse(quota.get("unlimited").asBoolean(), quota.toString());
        }
    }

    @Nested
    @DisplayName("POST /api/users/{id}/pause and /resume")
    class PauseResume {

        @BeforeEach
        void start() {
            startServer(AppConfig.defaults());
        }

        @Test
        @DisplayName("pause then resume moves the profile through PAUSED and back to ACTIVE")
        void pauseThenResume() throws Exception {
            HttpResponse<String> pause = post("/api/users/" + aliceId + "/pause", aliceId, null);
            assertEquals(200, pause.statusCode(), pause.body());
            assertEquals("PAUSED", MAPPER.readTree(pause.body()).get("state").asText());
            assertEquals("PAUSED", readState(aliceId));

            HttpResponse<String> resume = post("/api/users/" + aliceId + "/resume", aliceId, null);
            assertEquals(200, resume.statusCode(), resume.body());
            assertEquals("ACTIVE", MAPPER.readTree(resume.body()).get("state").asText());
            assertEquals("ACTIVE", readState(aliceId));
        }

        @Test
        @DisplayName("pausing a profile that is not active gets 409 PROFILE_NOT_ACTIVE")
        void pauseWhenNotActiveConflicts() throws Exception {
            assertEquals(
                    200, post("/api/users/" + aliceId + "/pause", aliceId, null).statusCode());

            HttpResponse<String> again = post("/api/users/" + aliceId + "/pause", aliceId, null);

            assertEquals(409, again.statusCode(), again.body());
            assertEquals(
                    "PROFILE_NOT_ACTIVE",
                    MAPPER.readTree(again.body()).get("code").asText());
        }

        @Test
        @DisplayName("resuming a profile that is not paused gets 409 PROFILE_NOT_PAUSED")
        void resumeWhenNotPausedConflicts() throws Exception {
            HttpResponse<String> response = post("/api/users/" + aliceId + "/resume", aliceId, null);

            assertEquals(409, response.statusCode(), response.body());
            assertEquals(
                    "PROFILE_NOT_PAUSED",
                    MAPPER.readTree(response.body()).get("code").asText());
        }

        @Test
        @DisplayName("resuming an incomplete profile gets 409 PROFILE_INCOMPLETE")
        void resumeIncompleteProfileConflicts() throws Exception {
            UUID incompleteId = UUID.randomUUID();
            userStorage.save(new User(incompleteId, "Incomplete"));
            UUID pausedIncompleteId = UUID.randomUUID();
            userStorage.save(User.StorageBuilder.create(pausedIncompleteId, "PausedIncomplete", AppClock.now())
                    .state(UserState.PAUSED)
                    .build());

            for (UUID userId : new UUID[] {incompleteId, pausedIncompleteId}) {
                HttpResponse<String> response = post("/api/users/" + userId + "/resume", userId, null);
                assertEquals(409, response.statusCode(), response.body());
                assertEquals(
                        "PROFILE_INCOMPLETE",
                        MAPPER.readTree(response.body()).get("code").asText());
            }
        }

        @Test
        @DisplayName("pausing someone else's profile gets 403")
        void pausingAnotherUserIsForbidden() throws Exception {
            assertEquals(
                    403, post("/api/users/" + bobId + "/pause", aliceId, null).statusCode());
            assertEquals(UserState.ACTIVE, userStorage.get(bobId).orElseThrow().getState());
        }

        @Test
        @DisplayName("editing a paused profile does not silently resume it")
        void profileEditKeepsProfilePaused() throws Exception {
            assertEquals(
                    200, post("/api/users/" + aliceId + "/pause", aliceId, null).statusCode());

            HttpResponse<String> update = client.send(
                    HttpRequest.newBuilder(uri("/api/users/" + aliceId + "/profile"))
                            .header(AUTHORIZATION_HEADER, token(aliceId))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString("{\"bio\":\"Taking a break\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, update.statusCode(), update.body());
            assertEquals(
                    UserState.PAUSED, userStorage.get(aliceId).orElseThrow().getState());
        }

        @Test
        @DisplayName("a paused user keeps matches and can still chat, but leaves other users' pending likers")
        void pausedUserKeepsChatsButLeavesPendingLikers() throws Exception {
            interactionStorage.save(Match.create(aliceId, bobId));
            interactionStorage.save(Like.create(aliceId, carolId, Like.Direction.LIKE));
            assertEquals(
                    1,
                    MAPPER.readTree(get("/api/users/" + carolId + "/pending-likers", carolId)
                                    .body())
                            .get("pendingLikers")
                            .size());

            assertEquals(
                    200, post("/api/users/" + aliceId + "/pause", aliceId, null).statusCode());

            HttpResponse<String> matches = get("/api/users/" + aliceId + "/matches", aliceId);
            assertEquals(200, matches.statusCode(), matches.body());
            assertEquals(1, MAPPER.readTree(matches.body()).get("matches").size());

            String conversationId = Conversation.generateId(aliceId, bobId);
            String path = "/api/conversations/" + conversationId + "/messages";
            assertEquals(
                    201,
                    post(path, aliceId, "{\"senderId\":\"" + aliceId + "\",\"content\":\"Paused but here\"}")
                            .statusCode());
            assertEquals(
                    201,
                    post(path, bobId, "{\"senderId\":\"" + bobId + "\",\"content\":\"Good to know\"}")
                            .statusCode());

            assertEquals(
                    0,
                    MAPPER.readTree(get("/api/users/" + carolId + "/pending-likers", carolId)
                                    .body())
                            .get("pendingLikers")
                            .size());
        }

        private String readState(UUID userId) throws Exception {
            HttpResponse<String> response = get("/api/users/" + userId, userId);
            assertEquals(200, response.statusCode(), response.body());
            return MAPPER.readTree(response.body()).get("state").asText();
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/pending-likers")
    class PendingLikers {

        @Test
        @DisplayName("each pending liker says whether the like was a super like")
        void pendingLikerCarriesSuperLikeFlag() throws Exception {
            startServer(AppConfig.defaults());
            interactionStorage.save(Like.create(bobId, aliceId, Like.Direction.LIKE));
            interactionStorage.save(Like.create(carolId, aliceId, Like.Direction.SUPER_LIKE));

            HttpResponse<String> response = get("/api/users/" + aliceId + "/pending-likers", aliceId);

            assertEquals(200, response.statusCode(), response.body());
            JsonNode likers = MAPPER.readTree(response.body()).get("pendingLikers");
            assertEquals(2, likers.size(), response.body());
            for (JsonNode liker : likers) {
                boolean expected = liker.get("userId").asText().equals(carolId.toString());
                assertEquals(expected, liker.get("superLike").asBoolean(), liker.toString());
            }
        }
    }

    private void startServer(AppConfig config) {
        services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(config)
                .build();
        server = new RestApiServer(services, 0);
        server.start();
        port = server.getApp().port();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String token(UUID userId) {
        return RestApiTestFixture.bearerToken(services, userId, userId + "@example.com");
    }

    private HttpResponse<String> get(String path, UUID actingUserId) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .header(AUTHORIZATION_HEADER, token(actingUserId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, UUID actingUserId) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .header(AUTHORIZATION_HEADER, token(actingUserId))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, UUID actingUserId, String jsonBody) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path)).header(AUTHORIZATION_HEADER, token(actingUserId));
        if (jsonBody == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
