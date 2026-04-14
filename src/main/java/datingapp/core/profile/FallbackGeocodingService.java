package datingapp.core.profile;

import java.util.List;
import java.util.Objects;

/** Uses local results first and falls back to a secondary provider only when needed. */
public final class FallbackGeocodingService implements GeocodingService {

    private final GeocodingService primary;
    private final GeocodingService fallback;

    public FallbackGeocodingService(GeocodingService primary, GeocodingService fallback) {
        this.primary = Objects.requireNonNull(primary, "primary cannot be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback cannot be null");
    }

    @Override
    public List<GeocodingResult> search(String query, int maxResults) {
        List<GeocodingResult> primaryResults = primary.search(query, maxResults);
        if (!primaryResults.isEmpty() || query == null || query.isBlank()) {
            return primaryResults;
        }
        return fallback.search(query, maxResults);
    }
}
