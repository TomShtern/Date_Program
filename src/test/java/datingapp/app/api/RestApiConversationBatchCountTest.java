package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
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
                .send(
                        HttpRequest.newBuilder(uri)
                                .header(
                                        "Authorization",
                                        RestApiTestFixture.bearerToken(services, userA, userA + "@example.com"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        java.util.List<?> conversations = MAPPER.readValue(response.body(), java.util.List.class);
        assertEquals(2, conversations.size());
        assertEquals(1, communicationStorage.batchCountCalls());
        assertEquals(0, communicationStorage.singleCountCalls());
    }

    @Test
    @DisplayName("getTotalUnreadCount uses the batched unread-count path")
    void totalUnreadCountUsesBatchUnreadPath() {
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
        connectionService.sendMessage(userB, userA, "AB-unread");
        connectionService.sendMessage(userC, userA, "AC-unread");

        int unread = connectionService.getTotalUnreadCount(userA);

        assertEquals(2, unread);
        assertEquals(1, communicationStorage.batchUnreadCountCalls());
        assertEquals(0, communicationStorage.singleUnreadCountCalls());
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

    private static final class SpyCommunications extends TestStorages.Communications {
        private int singleCountCalls;
        private int batchCountCalls;
        private int singleUnreadCountCalls;
        private int batchUnreadCountCalls;
        private boolean countingWithinBatch;
        private boolean countingUnreadWithinBatch;

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

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            if (!countingUnreadWithinBatch) {
                singleUnreadCountCalls++;
            }
            return super.countMessagesNotFromSender(conversationId, senderId);
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, java.time.Instant after, UUID senderId) {
            if (!countingUnreadWithinBatch) {
                singleUnreadCountCalls++;
            }
            return super.countMessagesAfterNotFrom(conversationId, after, senderId);
        }

        @Override
        public Map<String, Integer> countUnreadMessagesByConversationIds(UUID userId, Set<String> conversationIds) {
            batchUnreadCountCalls++;
            countingUnreadWithinBatch = true;
            try {
                return super.countUnreadMessagesByConversationIds(userId, conversationIds);
            } finally {
                countingUnreadWithinBatch = false;
            }
        }

        private int singleCountCalls() {
            return singleCountCalls;
        }

        private int batchCountCalls() {
            return batchCountCalls;
        }

        private int singleUnreadCountCalls() {
            return singleUnreadCountCalls;
        }

        private int batchUnreadCountCalls() {
            return batchUnreadCountCalls;
        }
    }
}
