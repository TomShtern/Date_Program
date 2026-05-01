package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API auth routes")
class RestApiAuthRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:";
    private static final String APPLICATION_JSON = "application/json";

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("signup normalizes email, creates an incomplete user, and rejects duplicates")
    void signupNormalizesEmailCreatesIncompleteUserAndRejectsDuplicates() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "  User@Example.com  ",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());
        assertEquals(1, userStorage.findAll().size());

        User createdUser = userStorage.findAll().getFirst();
        assertEquals("user@example.com", createdUser.getEmail());
        assertEquals(UserState.INCOMPLETE, createdUser.getState());

        HttpResponse<String> duplicateResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "user@example.com",
                  "password": "another password",
                  "dateOfBirth": "1995-01-01"
                }
                """);
        assertEquals(409, duplicateResponse.statusCode(), duplicateResponse.body());
        assertEquals(1, userStorage.findAll().size());
    }

    @Test
    @DisplayName("login, me, refresh rotation, and logout follow the phone-alpha auth contract")
    void loginMeRefreshRotationAndLogoutFollowPhoneAlphaContract() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "alpha@example.com",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());

        HttpResponse<String> wrongPasswordResponse = postJson(port, "/api/auth/login", """
                {
                  "email": "alpha@example.com",
                  "password": "wrong password"
                }
                """);
        assertEquals(401, wrongPasswordResponse.statusCode(), wrongPasswordResponse.body());

        HttpResponse<String> loginResponse = postJson(port, "/api/auth/login", """
                {
                  "email": "alpha@example.com",
                  "password": "correct horse battery staple"
                }
                """);
        assertEquals(200, loginResponse.statusCode(), loginResponse.body());
        JsonNode loginJson = MAPPER.readTree(loginResponse.body());
        assertFalse(loginJson.get("accessToken").asText().isBlank());
        assertFalse(loginJson.get("refreshToken").asText().isBlank());
        assertEquals(900, loginJson.get("expiresInSeconds").asInt());
        JsonNode loginUserJson = loginJson.get("user");
        assertEquals("alpha@example.com", loginUserJson.get("email").asText());
        assertTrue(loginUserJson.get("displayName").isNull());
        assertEquals("needs_name", loginUserJson.get("profileCompletionState").asText());

        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        HttpResponse<String> meResponse = authorizedRequest(port, "/api/auth/me", "GET", accessToken, null);
        assertEquals(200, meResponse.statusCode(), meResponse.body());
        JsonNode meJson = MAPPER.readTree(meResponse.body());
        assertEquals(loginUserJson.get("id").asText(), meJson.get("id").asText());
        assertEquals("alpha@example.com", meJson.get("email").asText());

        HttpResponse<String> refreshResponse = postJson(port, "/api/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        assertEquals(200, refreshResponse.statusCode(), refreshResponse.body());
        JsonNode refreshJson = MAPPER.readTree(refreshResponse.body());
        String rotatedRefreshToken = refreshJson.get("refreshToken").asText();
        assertNotEquals(refreshToken, rotatedRefreshToken);
        assertFalse(refreshJson.get("accessToken").asText().isBlank());

        HttpResponse<String> logoutResponse = postJson(port, "/api/auth/logout", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(rotatedRefreshToken));
        assertEquals(204, logoutResponse.statusCode(), logoutResponse.body());

        HttpResponse<String> revokedRefreshResponse =
                postJson(port, "/api/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(rotatedRefreshToken));
        assertEquals(401, revokedRefreshResponse.statusCode(), revokedRefreshResponse.body());
    }

    @Test
    @DisplayName("user-scoped routes require a matching bearer token subject")
    void userScopedRoutesRequireMatchingBearerTokenSubject() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        signupAndAssertCreated(port, "alice@example.com", "correct horse battery staple", "1998-04-30");
        signupAndAssertCreated(port, "bob@example.com", "correct horse battery staple", "1998-04-30");

        JsonNode aliceLogin = loginAndReadJson(port, "alice@example.com", "correct horse battery staple");
        JsonNode bobLogin = loginAndReadJson(port, "bob@example.com", "correct horse battery staple");

        String aliceToken = aliceLogin.get("accessToken").asText();
        String aliceId = aliceLogin.get("user").get("id").asText();
        String bobId = bobLogin.get("user").get("id").asText();

        HttpResponse<String> missingTokenResponse =
                request(port, "/api/users/" + aliceId + "/matches", "GET", null, null, null);
        assertEquals(401, missingTokenResponse.statusCode(), missingTokenResponse.body());

        HttpResponse<String> matchingTokenResponse =
                authorizedRequest(port, "/api/users/" + aliceId + "/matches", "GET", aliceToken, null);
        assertEquals(200, matchingTokenResponse.statusCode(), matchingTokenResponse.body());

        HttpResponse<String> mismatchedTokenResponse =
                authorizedRequest(port, "/api/users/" + bobId + "/matches", "GET", aliceToken, null);
        assertEquals(403, mismatchedTokenResponse.statusCode(), mismatchedTokenResponse.body());
    }

    @Test
    @DisplayName("login rejects deleted users")
    void loginRejectsDeletedUsers() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "deleted@example.com",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());
        JsonNode signupJson = MAPPER.readTree(signupResponse.body());
        UUID userId = UUID.fromString(signupJson.get("user").get("id").asText());

        HttpResponse<String> loginBeforeDelete = postJson(port, "/api/auth/login", """
                {
                  "email": "deleted@example.com",
                  "password": "correct horse battery staple"
                }
                """);
        assertEquals(200, loginBeforeDelete.statusCode(), loginBeforeDelete.body());

        User user = userStorage.get(userId).orElseThrow();
        user.markDeleted(AppClock.now());
        userStorage.save(user);

        HttpResponse<String> loginAfterDelete = postJson(port, "/api/auth/login", """
                {
                  "email": "deleted@example.com",
                  "password": "correct horse battery staple"
                }
                """);
        assertEquals(401, loginAfterDelete.statusCode(), loginAfterDelete.body());
    }

    @Test
    @DisplayName("deleted users cannot refresh, read me, or call protected routes with existing tokens")
    void deletedUsersCannotRefreshReadMeOrCallProtectedRoutesWithExistingTokens() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "deleted-session@example.com",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());

        JsonNode signupJson = MAPPER.readTree(signupResponse.body());
        UUID userId = UUID.fromString(signupJson.get("user").get("id").asText());

        JsonNode loginJson = loginAndReadJson(port, "deleted-session@example.com", "correct horse battery staple");
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        User user = userStorage.get(userId).orElseThrow();
        user.markDeleted(AppClock.now());
        userStorage.save(user);

        HttpResponse<String> refreshAfterDelete = postJson(port, "/api/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        assertEquals(401, refreshAfterDelete.statusCode(), refreshAfterDelete.body());

        HttpResponse<String> meAfterDelete = authorizedRequest(port, "/api/auth/me", "GET", accessToken, null);
        assertEquals(401, meAfterDelete.statusCode(), meAfterDelete.body());

        HttpResponse<String> protectedAfterDelete =
                authorizedRequest(port, "/api/users/" + userId + "/matches", "GET", accessToken, null);
        assertEquals(401, protectedAfterDelete.statusCode(), protectedAfterDelete.body());
    }

    @Test
    @DisplayName("deleted account email can be reused for signup and old login fails")
    void deletedAccountEmailCanBeReusedForSignupAndOldLoginFails() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "reuse@example.com",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());
        JsonNode signupJson = MAPPER.readTree(signupResponse.body());
        UUID userId = UUID.fromString(signupJson.get("user").get("id").asText());

        JsonNode loginJson = loginAndReadJson(port, "reuse@example.com", "correct horse battery staple");
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        HttpResponse<String> deleteResponse =
                authorizedRequest(port, "/api/users/" + userId, "DELETE", accessToken, null);
        assertEquals(204, deleteResponse.statusCode(), deleteResponse.body());

        HttpResponse<String> loginAfterDelete = postJson(port, "/api/auth/login", """
                {
                  "email": "reuse@example.com",
                  "password": "correct horse battery staple"
                }
                """);
        assertEquals(401, loginAfterDelete.statusCode(), loginAfterDelete.body());

        HttpResponse<String> refreshAfterDelete = postJson(port, "/api/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        assertEquals(401, refreshAfterDelete.statusCode(), refreshAfterDelete.body());

        HttpResponse<String> meAfterDelete = authorizedRequest(port, "/api/auth/me", "GET", accessToken, null);
        assertEquals(401, meAfterDelete.statusCode(), meAfterDelete.body());

        HttpResponse<String> reuseSignupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "reuse@example.com",
                  "password": "new password for reuse",
                  "dateOfBirth": "1995-01-01"
                }
                """);
        assertEquals(201, reuseSignupResponse.statusCode(), reuseSignupResponse.body());
        JsonNode reuseSignupJson = MAPPER.readTree(reuseSignupResponse.body());
        assertEquals(
                "reuse@example.com", reuseSignupJson.get("user").get("email").asText());
    }

    @Test
    @DisplayName("banned users cannot login, refresh, read me, or call protected routes with existing tokens")
    void bannedUsersCannotLoginRefreshReadMeOrCallProtectedRoutesWithExistingTokens() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> signupResponse = postJson(port, "/api/auth/signup", """
                {
                  "email": "banned-session@example.com",
                  "password": "correct horse battery staple",
                  "dateOfBirth": "1998-04-30"
                }
                """);
        assertEquals(201, signupResponse.statusCode(), signupResponse.body());
        JsonNode signupJson = MAPPER.readTree(signupResponse.body());
        UUID userId = UUID.fromString(signupJson.get("user").get("id").asText());

        JsonNode loginJson = loginAndReadJson(port, "banned-session@example.com", "correct horse battery staple");
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        User user = userStorage.get(userId).orElseThrow();
        user.ban();
        userStorage.save(user);

        HttpResponse<String> loginAfterBan = postJson(port, "/api/auth/login", """
                {
                  "email": "banned-session@example.com",
                  "password": "correct horse battery staple"
                }
                """);
        assertEquals(401, loginAfterBan.statusCode(), loginAfterBan.body());

        HttpResponse<String> refreshAfterBan = postJson(port, "/api/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        assertEquals(401, refreshAfterBan.statusCode(), refreshAfterBan.body());

        HttpResponse<String> meAfterBan = authorizedRequest(port, "/api/auth/me", "GET", accessToken, null);
        assertEquals(401, meAfterBan.statusCode(), meAfterBan.body());

        HttpResponse<String> protectedAfterBan =
                authorizedRequest(port, "/api/users/" + userId + "/matches", "GET", accessToken, null);
        assertEquals(401, protectedAfterBan.statusCode(), protectedAfterBan.body());
    }

    @Test
    @DisplayName("message send rejects spoofed sender ids when authenticated with bearer auth")
    void messageSendRejectsSpoofedSenderIdsWhenAuthenticatedWithBearerAuth() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);

        server = new RestApiServer(createServices(userStorage, interactionStorage, communicationStorage), 0);
        server.start();
        int port = server.getApp().port();

        signupAndAssertCreated(port, "alice@example.com", "correct horse battery staple", "1998-04-30");
        signupAndAssertCreated(port, "bob@example.com", "correct horse battery staple", "1998-04-30");

        JsonNode aliceLogin = loginAndReadJson(port, "alice@example.com", "correct horse battery staple");
        JsonNode bobLogin = loginAndReadJson(port, "bob@example.com", "correct horse battery staple");

        UUID aliceId = UUID.fromString(aliceLogin.get("user").get("id").asText());
        UUID bobId = UUID.fromString(bobLogin.get("user").get("id").asText());
        interactionStorage.save(Match.create(aliceId, bobId));
        communicationStorage.saveConversation(ConnectionModels.Conversation.create(aliceId, bobId));

        String conversationId = ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> spoofedSendResponse = authorizedRequest(
                port,
                "/api/conversations/" + conversationId + "/messages",
                "POST",
                aliceLogin.get("accessToken").asText(),
                """
                {
                  "senderId": "%s",
                  "content": "hello"
                }
                """.formatted(bobId));
        assertEquals(403, spoofedSendResponse.statusCode(), spoofedSendResponse.body());
    }

    private static ServiceRegistry createServices(
            TestStorages.Users userStorage,
            TestStorages.Interactions interactionStorage,
            TestStorages.Communications communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static void signupAndAssertCreated(int port, String email, String password, String dateOfBirth)
            throws Exception {
        HttpResponse<String> response = postJson(port, "/api/auth/signup", """
                {
                  "email": "%s",
                  "password": "%s",
                  "dateOfBirth": "%s"
                }
                """.formatted(email, password, dateOfBirth));
        assertEquals(201, response.statusCode(), response.body());
    }

    private static JsonNode loginAndReadJson(int port, String email, String password) throws Exception {
        HttpResponse<String> response = postJson(port, "/api/auth/login", """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password));
        assertEquals(200, response.statusCode(), response.body());
        return MAPPER.readTree(response.body());
    }

    private static HttpResponse<String> postJson(int port, String path, String jsonBody) throws Exception {
        return request(port, path, "POST", null, APPLICATION_JSON, jsonBody);
    }

    private static HttpResponse<String> authorizedRequest(
            int port, String path, String method, String accessToken, String jsonBody) throws Exception {
        return request(port, path, method, accessToken, APPLICATION_JSON, jsonBody);
    }

    private static HttpResponse<String> request(
            int port, String path, String method, String accessToken, String contentType, String jsonBody)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + port + path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
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
