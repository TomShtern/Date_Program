package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
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

@DisplayName("REST API daily limit enforcement")
class RestApiDailyLimitTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("user at like limit receives 429")
    void userAtLikeLimitReceives429() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        // Create config with dailyLikeLimit = 3
        AppConfig customConfig = AppConfig.builder().dailyLikeLimit(3).build();

        ServiceRegistry services = createServices(customConfig, userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");

        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);

        // Pre-populate 3 likes for alice (at the limit)
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, bobId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, carolId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, danaId, datingapp.core.connection.ConnectionModels.Like.Direction.LIKE));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Fourth like should fail with 429 and a RATE_LIMITED code
        UUID otherUserId = UUID.randomUUID();
        User otherUser = activeUser(otherUserId, "Other");
        userStorage.save(otherUser);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/like/" + otherUserId))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, aliceId, aliceId + "@example.com"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(429, response.statusCode());
        assertTrue(response.body().contains("RATE_LIMITED"), response.body());
    }

    @Test
    @DisplayName("user at pass limit receives 429")
    void userAtPassLimitReceives429() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        // Create config with dailyPassLimit = 3
        AppConfig customConfig = AppConfig.builder().dailyPassLimit(3).build();

        ServiceRegistry services = createServices(customConfig, userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");

        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);

        // Pre-populate 3 passes for alice (at the limit)
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, bobId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, carolId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));
        interactionStorage.save(datingapp.core.connection.ConnectionModels.Like.create(
                aliceId, danaId, datingapp.core.connection.ConnectionModels.Like.Direction.PASS));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Fourth pass should fail with 429 and a RATE_LIMITED code
        UUID otherUserId = UUID.randomUUID();
        User otherUser = activeUser(otherUserId, "Other");
        userStorage.save(otherUser);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + otherUserId))
                        .header(
                                AUTHORIZATION_HEADER,
                                RestApiTestFixture.bearerToken(services, aliceId, aliceId + "@example.com"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(429, response.statusCode());
        assertTrue(response.body().contains("RATE_LIMITED"), response.body());
    }

    @Test
    @DisplayName("unlimited config allows any number of likes")
    void unlimitedConfigAllowsAnyNumberOfLikes() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        // Create config with unlimited likes (dailyLikeLimit = -1)
        // Velocity blocking is off so this 50-swipe burst exercises only the daily limit.
        AppConfig customConfig = AppConfig.builder()
                .dailyLikeLimit(-1)
                .suspiciousSwipeVelocityBlockingEnabled(false)
                .build();

        ServiceRegistry services = createServices(customConfig, userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        userStorage.save(alice);

        // Create 50 other users
        java.util.List<UUID> targetIds = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "User-" + i);
            userStorage.save(user);
            targetIds.add(userId);
        }

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        // Like all 50 users - all should succeed
        int successCount = 0;
        for (int i = 0; i < 50; i++) {
            UUID targetId = targetIds.get(i);

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://localhost:" + port + "/api/users/" + aliceId + "/like/" + targetId))
                            .header(
                                    AUTHORIZATION_HEADER,
                                    RestApiTestFixture.bearerToken(services, aliceId, aliceId + "@example.com"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                successCount++;
            }
        }

        // All 50 likes should succeed
        assertEquals(50, successCount);
    }

    @Test
    @DisplayName("super-like is recorded once, then the daily super-like limit answers 429")
    void superLikeIsRecordedThenLimitedWith429() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        AppConfig customConfig = AppConfig.builder().dailySuperLikeLimit(1).build();

        ServiceRegistry services = createServices(customConfig, userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice"));
        userStorage.save(activeUser(bobId, "Bob"));
        userStorage.save(activeUser(carolId, "Carol"));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();
        String token = RestApiTestFixture.bearerToken(services, aliceId, aliceId + "@example.com");

        HttpResponse<String> first = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/super-like/" + bobId))
                        .header(AUTHORIZATION_HEADER, token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(
                datingapp.core.connection.ConnectionModels.Like.Direction.SUPER_LIKE,
                interactionStorage.getLike(aliceId, bobId).orElseThrow().direction());

        HttpResponse<String> second = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/super-like/" + carolId))
                        .header(AUTHORIZATION_HEADER, token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(429, second.statusCode(), second.body());
        assertTrue(second.body().contains("RATE_LIMITED"), second.body());
        assertTrue(interactionStorage.getLike(aliceId, carolId).isEmpty());
    }

    private static ServiceRegistry createServices(
            AppConfig customConfig,
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(customConfig)
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }
}
