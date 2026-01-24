package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.CandidateFinder.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for GeoUtils. */
class GeoUtilsTest {

    @Test
    @DisplayName("Distance between same point is zero")
    void samePointDistanceIsZero() {
        double distance = GeoUtils.distanceKm(32.0853, 34.7818, 32.0853, 34.7818);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Distance Tel Aviv to Jerusalem is approximately 54km")
    void telAvivToJerusalemDistance() {
        // Tel Aviv: 32.0853, 34.7818
        // Jerusalem: 31.7683, 35.2137
        double distance = GeoUtils.distanceKm(32.0853, 34.7818, 31.7683, 35.2137);

        // Should be approximately 54km
        assertTrue(distance > 50 && distance < 60, "Distance should be between 50-60km, was: " + distance);
    }

    @Test
    @DisplayName("Distance New York to London is approximately 5570km")
    void newYorkToLondonDistance() {
        // New York: 40.7128, -74.0060
        // London: 51.5074, -0.1278
        double distance = GeoUtils.distanceKm(40.7128, -74.0060, 51.5074, -0.1278);

        // Should be approximately 5570km
        assertTrue(distance > 5500 && distance < 5600, "Distance should be between 5500-5600km, was: " + distance);
    }
}
