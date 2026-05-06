package datingapp.core.model;

/**
 * Shared geographic validation utilities.
 */
public final class GeoValidation {

    private GeoValidation() {
        // Utility class
    }

    public static void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude)) {
            throw new IllegalArgumentException("Latitude cannot be NaN or Infinity");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got " + latitude);
        }
    }

    public static void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Longitude cannot be NaN or Infinity");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got " + longitude);
        }
    }
}
