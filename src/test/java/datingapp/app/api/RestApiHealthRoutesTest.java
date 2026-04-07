package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.ServiceRegistry;
import datingapp.core.testutil.TestStorages;
import io.javalin.http.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API health and validation routes")
class RestApiHealthRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestApiServer server;
    private TestStorages.Users userStorage;
    private TestStorages.Communications communicationStorage;
    private TestStorages.Interactions interactionStorage;
    private ServiceRegistry services;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);
        services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

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

    @Test
    @DisplayName("non-loopback requests are rejected by the localhost guard")
    void nonLoopbackRequestsAreRejectedByTheLocalhostGuard() throws Exception {
        server = new RestApiServer(services, 0);

        Method method = RestApiServer.class.getDeclaredMethod("enforceLocalhostOnly", Context.class);
        method.setAccessible(true);

        Context ctx = (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(), new Class<?>[] {Context.class}, (proxy, invokedMethod, args) -> {
                    if ("ip".equals(invokedMethod.getName())) {
                        return "203.0.113.10";
                    }
                    Class<?> returnType = invokedMethod.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(byte.class)) {
                        return (byte) 0;
                    }
                    if (returnType.equals(short.class)) {
                        return (short) 0;
                    }
                    if (returnType.equals(char.class)) {
                        return '\0';
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    if (returnType.equals(float.class)) {
                        return 0.0f;
                    }
                    if (returnType.equals(double.class)) {
                        return 0.0d;
                    }
                    return null;
                });

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, () -> method.invoke(server, ctx));
        assertEquals("ApiForbiddenException", thrown.getCause().getClass().getSimpleName());
        assertEquals(
                "REST API is restricted to localhost requests",
                thrown.getCause().getMessage());
    }
}
