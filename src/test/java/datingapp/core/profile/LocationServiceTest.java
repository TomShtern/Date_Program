package datingapp.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationService")
class LocationServiceTest {

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(new ValidationService(AppConfig.defaults()));
    }

    @Test
    @DisplayName("default country is available Israel")
    void defaultCountryIsAvailableIsrael() {
        Country defaultCountry = locationService.getDefaultCountry();

        assertEquals("IL", defaultCountry.code());
        assertTrue(defaultCountry.available());
        assertTrue(defaultCountry.defaultSelection());
    }

    @Test
    @DisplayName("city search is case insensitive and priority ordered")
    void citySearchIsCaseInsensitiveAndPriorityOrdered() {
        List<City> results = locationService.searchCities("IL", "tel", 10);

        assertFalse(results.isEmpty());
        assertEquals("Tel Aviv", results.getFirst().name());
        assertTrue(results.stream()
                .allMatch(city -> city.displayName().toLowerCase().contains("tel")));
    }

    @Test
    @DisplayName("supported zip resolves to a precise location")
    void supportedZipResolvesToPreciseLocation() {
        LocationService.ZipLookupResult result = locationService.lookupZip("IL", "6701101");

        assertTrue(result.valid());
        assertEquals("6701", result.normalizedZip());
        assertTrue(result.resolvedLocation().isPresent());
        assertEquals(
                "Tel Aviv, Tel Aviv District",
                result.resolvedLocation().orElseThrow().label());
    }

    @Test
    @DisplayName("reverse lookup finds a supported label for known coordinates")
    void reverseLookupFindsSupportedLabelForKnownCoordinates() {
        Optional<ResolvedLocation> resolvedLocation = locationService.reverseLookup(32.0853, 34.7818);

        assertTrue(resolvedLocation.isPresent());
        assertTrue(resolvedLocation.orElseThrow().label().contains("Tel Aviv"));
    }

    @Test
    @DisplayName("selection seed rehydrates city-only saved coordinates")
    void selectionSeedRehydratesCityOnlySavedCoordinates() {
        LocationService.SelectionSeed seed =
                locationService.seedSelection(32.3215, 34.8532).orElseThrow();

        assertEquals("IL", seed.country().code());
        assertTrue(seed.city().isPresent());
        assertEquals("Netanya", seed.city().orElseThrow().name());
        assertTrue(seed.zipPrefix().isEmpty());
        assertEquals(Precision.CITY, seed.resolvedLocation().precision());
    }

    @Test
    @DisplayName("selection resolution can fall back to an approximate supported area for valid unknown zips")
    void selectionResolutionCanFallBackToApproximateSupportedAreaForValidUnknownZips() {
        LocationService.ResolveSelectionResult result = locationService.resolveSelection("IL", null, "9999999", true);

        assertTrue(result.valid());
        assertTrue(result.approximate());
        assertTrue(result.resolvedLocation().isPresent());
        assertTrue(result.message().contains("approximate"));
        assertEquals(Precision.ZIP, result.resolvedLocation().orElseThrow().precision());
    }
}
