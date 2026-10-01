package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestAccessTokenVerifier;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API auth routes")
class RestApiAuthRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:";
    private static final String APPLICATION_JSON = "application/json";
    private static final String SESSION_PATH = "/api/auth/session";

    private TestStorages.Users userStorage;
    private TestStorages.Communications communicationStorage;
    private TestStorages.Interactions interactionStorage;
    private RestApiServer server;
    private int port;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
        server = new RestApiServer(services, 0);
        server.start();
        port = server.getApp().port();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("POST /api/auth/session creates an incomplete profile (201) and then returns the same one (200)")
    void sessionCreatesThenReusesProfile() throws Exception {
        String token = TestAccessTokenVerifier.tokenFor("user_new_1");

        HttpResponse<String> created = postSession(token);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode createdJson = MAPPER.readTree(created.body());
        assertTrue(createdJson.get("email").isNull());
        assertTrue(createdJson.get("displayName").isNull());
        assertTrue(createdJson.get("profileCompletionState").asText().startsWith("needs_"));

        assertEquals(1, userStorage.findAll().size());
        User user = userStorage.findAll().getFirst();
        assertEquals(UserState.INCOMPLETE, user.getState());
        assertNull(user.getEmail());
        assertNull(user.getBirthDate());
        assertEquals(user.getId().toString(), createdJson.get("id").asText());

        HttpResponse<String> again = postSession(token);
        assertEquals(200, again.statusCode(), again.body());
        assertEquals(
                createdJson.get("id").asText(),
                MAPPER.readTree(again.body()).get("id").asText());
        assertEquals(1, userStorage.findAll().size());
    }

    @Test
    @DisplayName("POST /api/auth/session rejects missing, malformed and unverifiable tokens with 401")
    void sessionRejectsBadTokens() throws Exception {
        assertEquals(401, request(SESSION_PATH, "POST", null, null, null).statusCode());
        assertEquals(401, postSession("not-a-valid-token").statusCode());

        HttpRequest malformed = HttpRequest.newBuilder(URI.create(BASE_URL + port + SESSION_PATH))
                .header("Authorization", "Basic abc")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertEquals(
                401,
                CLIENT.send(malformed, HttpResponse.BodyHandlers.ofString()).statusCode());

        assertTrue(userStorage.findAll().isEmpty());
    }

    @Test
    @DisplayName("a valid token without a profile gets 401 NOT_PROVISIONED on a scoped route")
    void validTokenWithoutProfileIsNotProvisioned() throws Exception {
        HttpResponse<String> response = authorizedRequest(
                "/api/users/" + UUID.randomUUID() + "/matches", "GET", TestAccessTokenVerifier.tokenFor("user_none"));

        assertEquals(401, response.statusCode(), response.body());
        assertEquals(
                "NOT_PROVISIONED", MAPPER.readTree(response.body()).get("code").asText());
    }

    @Test
    @DisplayName("the old password routes no longer exist")
    void legacyPasswordRoutesAreGone() throws Exception {
        for (String path :
                new String[] {"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"}) {
            assertEquals(
                    404, request(path, "POST", null, APPLICATION_JSON, "{}").statusCode(), path);
        }
        assertEquals(404, request("/api/auth/me", "GET", null, null, null).statusCode());
    }

    @Test
    @DisplayName("user-scoped routes require a matching bearer token subject")
    void userScopedRoutesRequireMatchingBearerTokenSubject() throws Exception {
        String aliceToken = TestAccessTokenVerifier.tokenFor("user_alice");
        String aliceId = sessionUserId(aliceToken);
        String bobId = sessionUserId(TestAccessTokenVerifier.tokenFor("user_bob"));

        assertEquals(
                401,
                request("/api/users/" + aliceId + "/matches", "GET", null, null, null)
                        .statusCode());
        assertEquals(
                200,
                authorizedRequest("/api/users/" + aliceId + "/matches", "GET", aliceToken)
                        .statusCode());
        assertEquals(
                403,
                authorizedRequest("/api/users/" + bobId + "/matches", "GET", aliceToken)
                        .statusCode());
    }

    @Test
    @DisplayName("a deleted profile locks the old session out, and the next session call starts a fresh profile")
    void deletedProfileLocksOutThenReprovisions() throws Exception {
        String token = TestAccessTokenVerifier.tokenFor("user_deleted");
        UUID userId = UUID.fromString(sessionUserId(token));

        User user = userStorage.get(userId).orElseThrow();
        user.markDeleted(AppClock.now());
        userStorage.save(user);

        HttpResponse<String> protectedAfterDelete =
                authorizedRequest("/api/users/" + userId + "/matches", "GET", token);
        assertEquals(401, protectedAfterDelete.statusCode(), protectedAfterDelete.body());

        HttpResponse<String> fresh = postSession(token);
        assertEquals(201, fresh.statusCode(), fresh.body());
        assertNotEquals(
                userId.toString(), MAPPER.readTree(fresh.body()).get("id").asText());
    }

    @Test
    @DisplayName("deleting the account over REST invalidates the session and a later session call starts over")
    void deleteAccountThenSignInAgainStartsOver() throws Exception {
        String token = TestAccessTokenVerifier.tokenFor("user_reuse");
        String userId = sessionUserId(token);

        assertEquals(
                204, authorizedRequest("/api/users/" + userId, "DELETE", token).statusCode());
        assertEquals(
                401,
                authorizedRequest("/api/users/" + userId + "/matches", "GET", token)
                        .statusCode());

        HttpResponse<String> fresh = postSession(token);
        assertEquals(201, fresh.statusCode(), fresh.body());
        assertNotEquals(userId, MAPPER.readTree(fresh.body()).get("id").asText());
    }

    @Test
    @DisplayName("a banned user cannot call protected routes or open a session")
    void bannedUsersAreLockedOut() throws Exception {
        String token = TestAccessTokenVerifier.tokenFor("user_banned");
        UUID userId = UUID.fromString(sessionUserId(token));

        User user = userStorage.get(userId).orElseThrow();
        user.ban();
        userStorage.save(user);

        assertEquals(
                401,
                authorizedRequest("/api/users/" + userId + "/matches", "GET", token)
                        .statusCode());
        assertEquals(401, postSession(token).statusCode());
    }

    @Test
    @DisplayName("message send rejects spoofed sender ids when authenticated with bearer auth")
    void messageSendRejectsSpoofedSenderIdsWhenAuthenticatedWithBearerAuth() throws Exception {
        String aliceToken = TestAccessTokenVerifier.tokenFor("user_alice");
        UUID aliceId = UUID.fromString(sessionUserId(aliceToken));
        UUID bobId = UUID.fromString(sessionUserId(TestAccessTokenVerifier.tokenFor("user_bob")));
        interactionStorage.save(Match.create(aliceId, bobId));
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(aliceId, bobId));

        String conversationId = ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> spoofedSendResponse = request(
                "/api/conversations/" + conversationId + "/messages",
                "POST",
                aliceToken,
                APPLICATION_JSON,
                """
                {
                  "senderId": "%s",
                  "content": "hello"
                }
                """.formatted(bobId));
        assertEquals(403, spoofedSendResponse.statusCode(), spoofedSendResponse.body());
    }

    private String sessionUserId(String token) throws Exception {
        HttpResponse<String> response = postSession(token);
        assertEquals(201, response.statusCode(), response.body());
        return MAPPER.readTree(response.body()).get("id").asText();
    }

    private HttpResponse<String> postSession(String token) throws Exception {
        return request(SESSION_PATH, "POST", token, null, null);
    }

    private HttpResponse<String> authorizedRequest(String path, String method, String token) throws Exception {
        return request(path, method, token, null, null);
    }

    private HttpResponse<String> request(String path, String method, String token, String contentType, String jsonBody)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (contentType != null && jsonBody != null) {
            builder.header("Content-Type", contentType);
        }

        HttpRequest request =
                switch (method) {
                    case "GET" -> builder.GET().build();
                    case "POST" -> builder.POST(body(jsonBody)).build();
                    case "DELETE" -> builder.DELETE().build();
                    default -> throw new IllegalArgumentException("Unsupported method: " + method);
                };
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.BodyPublisher body(String jsonBody) {
        return jsonBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonBody);
    }
}
