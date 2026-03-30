package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API conversation batch counts")
class RestApiConversationBatchCountTest {

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
    @DisplayName("conversations endpoint uses batch count path instead of per-conversation counting")
    void conversationsEndpointUsesBatchCountPath() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        SpyCommunications communicationStorage = new SpyCommunications();
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        userStorage.save(activeUser(userC, "Cara"));

        interactionStorage.save(Match.create(userA, userB));
        interactionStorage.save(Match.create(userA, userC));

        ConnectionService connectionService = services.getConnectionService();
        connectionService.sendMessage(userA, userB, "AB-1");
        connectionService.sendMessage(userA, userC, "AC-1");

        server = new RestApiServer(services, 0);
        server.start();

        int port = server.getApp().port();
        URI uri = URI.create("http://localhost:" + port + "/api/users/" + userA + "/conversations");

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        java.util.List<?> conversations = MAPPER.readValue(response.body(), java.util.List.class);
        assertEquals(2, conversations.size());
        assertEquals(1, communicationStorage.batchCountCalls());
        assertEquals(0, communicationStorage.singleCountCalls());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }

    private static final class SpyCommunications extends TestStorages.Communications {
        private int singleCountCalls;
        private int batchCountCalls;
        private boolean countingWithinBatch;

        @Override
        public int countMessages(String conversationId) {
            if (!countingWithinBatch) {
                singleCountCalls++;
            }
            return super.countMessages(conversationId);
        }

        @Override
        public Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
            batchCountCalls++;
            countingWithinBatch = true;
            try {
                return super.countMessagesByConversationIds(conversationIds);
            } finally {
                countingWithinBatch = false;
            }
        }

        private int singleCountCalls() {
            return singleCountCalls;
        }

        private int batchCountCalls() {
            return batchCountCalls;
        }
    }
}
