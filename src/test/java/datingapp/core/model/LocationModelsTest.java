package datingapp.core.model;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationModels")
class LocationModelsTest {

    @Test
    @DisplayName("resolved location rejects NaN latitude")
    void resolvedLocationRejectsNaNLatitude() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedLocation(Double.NaN, 34.7818, "Tel Aviv", Precision.CITY));

        assertTrue(exception.getMessage().contains("Latitude cannot be NaN or Infinity"));
    }

    @Test
    @DisplayName("formatCoordinates rejects Infinity longitude")
    void formatCoordinatesRejectsInfinityLongitude() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LocationModels.formatCoordinates(32.0853, Double.POSITIVE_INFINITY));

        assertTrue(exception.getMessage().contains("Longitude cannot be NaN or Infinity"));
    }
}
