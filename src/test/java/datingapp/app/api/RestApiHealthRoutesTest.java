package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.ServiceRegistry;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API health and validation routes")
class RestApiHealthRoutesTest {

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
    @DisplayName("health route responds from localhost-only server")
    void healthRouteRespondsFromLocalhostOnlyServer() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        server = new RestApiServer(services, 0);
        server.start();

        int port = server.getApp().port();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/health"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());
        assertEquals("ok", json.get("status").asText());
        assertTrue(InetAddress.getByName("localhost").isLoopbackAddress());
    }

    @Test
    @DisplayName("invalid UUID route returns bad request")
    void invalidUuidRouteReturnsBadRequest() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        server = new RestApiServer(services, 0);
        server.start();

        int port = server.getApp().port();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/not-a-uuid"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        JsonNode json = MAPPER.readTree(response.body());
        assertEquals("BAD_REQUEST", json.get("code").asText());
        assertTrue(json.get("message").asText().contains("Invalid UUID format"));
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }
}
