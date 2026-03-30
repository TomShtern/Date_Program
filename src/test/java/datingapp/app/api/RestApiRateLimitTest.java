package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API rate limit headers")
class RestApiRateLimitTest {

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("rate limit 429 responses include retry and quota headers")
    void rateLimitResponsesIncludeRetryAndQuotaHeaders() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userId = UUID.randomUUID();
        userStorage.save(activeUser(userId, "RateLimit", Gender.FEMALE, EnumSet.of(Gender.MALE)));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response = null;
        for (int i = 0; i < 241; i++) {
            response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertNotNull(response);
        assertEquals(429, response.statusCode());
        assertFalse(response.headers().firstValue("Retry-After").orElse("").isBlank());
        assertEquals("240", response.headers().firstValue("X-RateLimit-Limit").orElseThrow());
        assertEquals("241", response.headers().firstValue("X-RateLimit-Used").orElseThrow());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static User activeUser(UUID id, String name, Gender gender, EnumSet<Gender> interestedIn) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(gender)
                .interestedIn(interestedIn)
                .location(40.7128, -74.0060)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(List.of("https://example.com/" + name.toLowerCase() + ".jpg"))
                .build();
    }
}
