package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.ServiceRegistry;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API rate limiting behind a local tunnel")
class RestApiClientIpRateLimitTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String SECRET_HEADER = "X-DatingApp-Shared-Secret";
    private static final String SHARED_SECRET = "test-only-lan-secret";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";
    // Mirrors RestApiServer.DEFAULT_RATE_LIMIT_REQUESTS.
    private static final int LIMIT = 240;

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("with a client IP header configured, each forwarded client has its own bucket")
    void eachForwardedClientHasItsOwnBucket() throws Exception {
        int port = startServer("X-Forwarded-For");

        for (int i = 0; i < LIMIT; i++) {
            assertEquals(401, statusFor(port, "198.51.100.1"), "request " + (i + 1));
        }
        assertEquals(429, statusFor(port, "198.51.100.1"));
        assertEquals(401, statusFor(port, "198.51.100.2"));
    }

    @Test
    @DisplayName("without a client IP header every request shares the socket peer's bucket")
    void withoutHeaderEveryRequestSharesTheSocketPeersBucket() throws Exception {
        int port = startServer(null);

        for (int i = 0; i < LIMIT; i++) {
            assertEquals(401, statusFor(port, "198.51.100.1"), "request " + (i + 1));
        }
        assertEquals(429, statusFor(port, "198.51.100.2"));
    }

    private int startServer(String clientIpHeader) {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
        // 0.0.0.0 is the supported tunnel configuration: the shared secret stays enforced.
        server = new RestApiServer(services, "0.0.0.0", 0, SHARED_SECRET, Set.of(), clientIpHeader);
        server.start();
        return server.getApp().port();
    }

    /** GET /api/users with the shared secret but no bearer token: 401 until the rate limiter says 429. */
    private static int statusFor(int port, String forwardedFor) throws Exception {
        HttpResponse<Void> response = CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users"))
                        .header(SECRET_HEADER, SHARED_SECRET)
                        .header(FORWARDED_HEADER, forwardedFor)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}
