package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.storage.OperationalCommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API verification routes")
class RestApiVerificationRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = "http://localhost:";
    private static final String USERS_PATH = "/api/users/";
    private static final String START_PATH = "/verification/start";
    private static final String CONFIRM_PATH = "/verification/confirm";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
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
    @DisplayName("verification start and confirm routes succeed for a valid code")
    void verificationStartAndConfirmRoutesSucceedForValidCode() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "Verifier");
        userStorage.save(user);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> startResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + START_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"method\":\"EMAIL\",\"contact\":\"verified@example.com\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, startResponse.statusCode(), startResponse.body());
        JsonNode startJson = MAPPER.readTree(startResponse.body());
        assertEquals(userId.toString(), startJson.get("userId").asText());
        assertEquals("EMAIL", startJson.get("method").asText());
        assertEquals("verified@example.com", startJson.get("contact").asText());
        String verificationCode = startJson.get("devVerificationCode").asText();
        assertTrue(!verificationCode.isBlank());

        HttpResponse<String> confirmResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + CONFIRM_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"verificationCode\":\"" + verificationCode + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, confirmResponse.statusCode(), confirmResponse.body());
        JsonNode confirmJson = MAPPER.readTree(confirmResponse.body());
        assertTrue(confirmJson.get("verified").asBoolean());
        assertNotNull(confirmJson.get("verifiedAt").asText());
        assertTrue(userStorage.get(userId).orElseThrow().isVerified());
    }

    @Test
    @DisplayName("verification routes reject invalid method, blank contact, and incorrect code")
    void verificationRoutesRejectInvalidMethodBlankContactAndIncorrectCode() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "Verifier");
        userStorage.save(user);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> invalidMethodResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + START_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"method\":\"NOT_A_METHOD\",\"contact\":\"verified@example.com\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidMethodResponse.statusCode());

        HttpResponse<String> blankContactResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + START_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"method\":\"PHONE\",\"contact\":\"   \"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, blankContactResponse.statusCode());

        HttpResponse<String> startResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + START_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"method\":\"EMAIL\",\"contact\":\"verified@example.com\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, startResponse.statusCode());

        HttpResponse<String> invalidCodeResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + userId + CONFIRM_PATH))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, user.getId(), user.getEmail()))
                        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"verificationCode\":\"000000\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidCodeResponse.statusCode());
    }

    private static ServiceRegistry createServices(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(User.UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .email(name.toLowerCase() + "@example.com")
                .gender(Gender.OTHER)
                .interestedIn(EnumSet.of(Gender.OTHER))
                .location(40.7128, -74.0060)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(java.util.List.of("https://example.com/" + name.toLowerCase() + ".jpg"))
                .build();
    }
}
