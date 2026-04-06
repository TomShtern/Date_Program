package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.OperationalCommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
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

@DisplayName("REST API profile-note routes")
class RestApiNotesRoutesTest {

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
    @DisplayName("note endpoints support put get list and delete")
    void noteEndpointsSupportPutGetListAndDelete() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        userStorage.save(activeUser(authorId, "Author"));
        userStorage.save(activeUser(subjectId, "Subject"));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpClient client = HttpClient.newHttpClient();
        String baseUri = "http://localhost:" + port + "/api/users/" + authorId + "/notes/" + subjectId;

        HttpResponse<String> putResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri))
                        .header("X-User-Id", authorId.toString())
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"content\":\"Met at coffee shop\"}"))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode());
        JsonNode putJson = MAPPER.readTree(putResponse.body());
        assertEquals("Met at coffee shop", putJson.get("content").asText());

        HttpResponse<String> getResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        JsonNode getJson = MAPPER.readTree(getResponse.body());
        assertEquals(subjectId.toString(), getJson.get("subjectId").asText());

        HttpResponse<String> listResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + authorId + "/notes"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        JsonNode listJson = MAPPER.readTree(listResponse.body());
        assertEquals(1, listJson.size());
        assertTrue(listJson.get(0).get("content").asText().contains("coffee shop"));

        HttpResponse<String> deleteResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri))
                        .header("X-User-Id", authorId.toString())
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());

        HttpResponse<String> missingResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingResponse.statusCode());
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
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }
}
