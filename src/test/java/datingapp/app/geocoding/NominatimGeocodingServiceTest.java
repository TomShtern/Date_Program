package datingapp.app.geocoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.profile.GeocodingService;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NominatimGeocodingService")
class NominatimGeocodingServiceTest {

    @Test
    @DisplayName("search parses remote results and sends the configured user agent")
    void searchParsesRemoteResultsAndSendsConfiguredUserAgent() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            query.set(exchange.getRequestURI().getQuery());
            writeJson(exchange, 200, """
                    [
                      {
                        \"display_name\": \"Rothschild Boulevard, Tel Aviv-Yafo, Israel\",
                        \"lat\": \"32.0651\",
                        \"lon\": \"34.7778\"
                      }
                    ]
                    """);
        })) {
            NominatimGeocodingService service = new NominatimGeocodingService(
                    HttpClient.newHttpClient(), server.baseUri(), "DatingApp-Test/1.0", () -> 1_000L, _ -> {});

            List<GeocodingService.GeocodingResult> results = service.search("Rothschild", 5);

            assertEquals("DatingApp-Test/1.0", userAgent.get());
            assertTrue(query.get().contains("format=jsonv2"));
            assertTrue(query.get().contains("limit=5"));
            assertTrue(query.get().contains("countrycodes=il"));
            assertEquals(1, results.size());
            assertEquals(
                    "Rothschild Boulevard, Tel Aviv-Yafo, Israel",
                    results.getFirst().displayName());
            assertEquals(Precision.ADDRESS, results.getFirst().precision());
        }
    }

    @Test
    @DisplayName("search throttles remote calls to one request per second")
    void searchThrottlesRemoteCallsToOneRequestPerSecond() throws Exception {
        AtomicLong nowMillis = new AtomicLong(10_000L);
        AtomicLong sleptMillis = new AtomicLong();

        try (TestServer server = new TestServer(exchange -> writeJson(exchange, 200, "[]"))) {
            NominatimGeocodingService service = new NominatimGeocodingService(
                    HttpClient.newHttpClient(), server.baseUri(), "DatingApp-Test/1.0", nowMillis::get, millis -> {
                        sleptMillis.addAndGet(millis);
                        nowMillis.addAndGet(millis);
                    });

            service.search("first address", 5);
            service.search("second address", 5);

            assertEquals(1_000L, sleptMillis.get());
        }
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final URI baseUri;

        private TestServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/search", handler);
            executor = Executors.newSingleThreadExecutor();
            server.setExecutor(executor);
            server.start();
            baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private URI baseUri() {
            return baseUri;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
