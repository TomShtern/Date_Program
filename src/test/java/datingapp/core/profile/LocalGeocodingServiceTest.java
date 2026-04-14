package datingapp.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.model.LocationModels.Precision;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalGeocodingService")
class LocalGeocodingServiceTest {

    private LocalGeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        LocationService locationService = new LocationService(new ValidationService(AppConfig.defaults()));
        geocodingService = new LocalGeocodingService(locationService);
    }

    @Test
    @DisplayName("blank query returns popular local cities as city-precision results")
    void blankQueryReturnsPopularLocalCitiesAsCityPrecisionResults() {
        List<GeocodingService.GeocodingResult> results = geocodingService.search("", 3);

        assertEquals(3, results.size());
        assertEquals("Tel Aviv, Tel Aviv District", results.getFirst().displayName());
        assertEquals(Precision.CITY, results.getFirst().precision());
    }

    @Test
    @DisplayName("search maps expanded offline city matches into geocoding results")
    void searchMapsExpandedOfflineCityMatchesIntoGeocodingResults() {
        List<GeocodingService.GeocodingResult> results = geocodingService.search("eil", 10);

        assertTrue(results.stream().anyMatch(result -> "Eilat, Southern District".equals(result.displayName())));
        assertTrue(results.stream().allMatch(result -> result.precision() == Precision.CITY));
    }
}
