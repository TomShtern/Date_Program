package datingapp.core.profile;

import datingapp.core.model.LocationModels.Precision;
import java.util.List;
import java.util.Objects;

/** Offline geocoding adapter backed by the built-in location dataset. */
public final class LocalGeocodingService implements GeocodingService {

    private static final String DEFAULT_COUNTRY_CODE = "IL";

    private final LocationService locationService;

    public LocalGeocodingService(LocationService locationService) {
        this.locationService = Objects.requireNonNull(locationService, "locationService cannot be null");
    }

    @Override
    public List<GeocodingResult> search(String query, int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }
        return locationService.searchCities(DEFAULT_COUNTRY_CODE, query, maxResults).stream()
                .map(city -> new GeocodingResult(city.displayName(), city.latitude(), city.longitude(), Precision.CITY))
                .toList();
    }
}
