package datingapp.app.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.profile.GeocodingService;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional online geocoding backed by Nominatim. */
public final class NominatimGeocodingService implements GeocodingService {

    private static final Logger logger = LoggerFactory.getLogger(NominatimGeocodingService.class);
    private static final URI DEFAULT_BASE_URI = URI.create("https://nominatim.openstreetmap.org");
    private static final String DEFAULT_USER_AGENT = "DatingApp/1.0 (Date_Program geocoding)";
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 1_000L;

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String userAgent;
    private final LongSupplier currentTimeMillis;
    private final LongConsumer sleepMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong nextAllowedRequestMillis = new AtomicLong();

    public NominatimGeocodingService() {
        this(
                HttpClient.newHttpClient(),
                DEFAULT_BASE_URI,
                DEFAULT_USER_AGENT,
                () -> AppClock.now().toEpochMilli(),
                waitMillis -> {
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    public NominatimGeocodingService(
            HttpClient httpClient,
            URI baseUri,
            String userAgent,
            LongSupplier currentTimeMillis,
            LongConsumer sleepMillis) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri cannot be null");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent cannot be null");
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis cannot be null");
        this.sleepMillis = Objects.requireNonNull(sleepMillis, "sleepMillis cannot be null");
    }

    @Override
    public List<GeocodingResult> search(String query, int maxResults) {
        if (query == null || query.isBlank() || maxResults <= 0) {
            return List.of();
        }

        waitForPermit();

        try {
            String requestQuery = "q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&format=jsonv2&limit=" + maxResults + "&countrycodes=il";
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/search?" + requestQuery))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Nominatim lookup failed with status {}", response.statusCode());
                }
                return List.of();
            }
            return parseResults(response.body(), maxResults);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (logger.isDebugEnabled()) {
                logger.debug("Nominatim lookup interrupted: {}", exception.getMessage());
            }
            return List.of();
        } catch (IOException | RuntimeException exception) {
            if (logger.isDebugEnabled()) {
                logger.debug("Nominatim lookup failed: {}", exception.getMessage());
            }
            return List.of();
        }
    }

    private void waitForPermit() {
        while (true) {
            long now = currentTimeMillis.getAsLong();
            long nextAllowed = nextAllowedRequestMillis.get();
            if (now >= nextAllowed) {
                if (nextAllowedRequestMillis.compareAndSet(nextAllowed, now + MIN_REQUEST_INTERVAL_MILLIS)) {
                    return;
                }
                continue;
            }
            sleepMillis.accept(nextAllowed - now);
        }
    }

    private List<GeocodingResult> parseResults(String body, int maxResults) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<GeocodingResult> results = new ArrayList<>();
        for (JsonNode node : root) {
            GeocodingResult result = parseResult(node);
            if (result != null) {
                results.add(result);
                if (results.size() >= maxResults) {
                    return List.copyOf(results);
                }
            }
        }
        return List.copyOf(results);
    }

    private GeocodingResult parseResult(JsonNode node) {
        String displayName = node.path("display_name").asText("").trim();
        String latText = node.path("lat").asText("").trim();
        String lonText = node.path("lon").asText("").trim();
        if (displayName.isBlank() || latText.isBlank() || lonText.isBlank()) {
            return null;
        }

        try {
            return new GeocodingResult(
                    displayName, Double.parseDouble(latText), Double.parseDouble(lonText), Precision.ADDRESS);
        } catch (NumberFormatException _) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping Nominatim result with invalid coordinates: {}", displayName);
            }
            return null;
        }
    }
}
