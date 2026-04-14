package datingapp.core.model;

import java.util.Locale;
import java.util.Objects;

/** Shared immutable records for the built-in location selection flow. */
public final class LocationModels {

    private LocationModels() {}

    public enum Precision {
        ADDRESS,
        CITY,
        ZIP
    }

    public record Country(String code, String name, String flagEmoji, boolean available, boolean defaultSelection) {
        public Country {
            code = requireText(code, "code");
            name = requireText(name, "name");
            flagEmoji = requireText(flagEmoji, "flagEmoji");
        }

        public String displayName() {
            return available ? flagEmoji + " " + name : flagEmoji + " " + name + " (Coming soon)";
        }
    }

    public record City(
            String name, String district, double latitude, double longitude, String countryCode, int priority) {
        public City {
            name = requireText(name, "name");
            district = requireText(district, "district");
            countryCode = requireText(countryCode, "countryCode");
            validateLatitude(latitude);
            validateLongitude(longitude);
            if (priority < 1) {
                throw new IllegalArgumentException("priority must be at least 1");
            }
        }

        public String displayName() {
            return name + ", " + district;
        }
    }

    public record ZipRange(
            String prefix, String countryCode, String city, String district, double latitude, double longitude) {
        public ZipRange {
            prefix = requireText(prefix, "prefix");
            countryCode = requireText(countryCode, "countryCode");
            city = requireText(city, "city");
            district = requireText(district, "district");
            validateLatitude(latitude);
            validateLongitude(longitude);
        }

        public String displayName() {
            return city + ", " + district;
        }
    }

    public record ResolvedLocation(double latitude, double longitude, String label, Precision precision) {
        public ResolvedLocation {
            validateLatitude(latitude);
            validateLongitude(longitude);
            label = requireText(label, "label");
            Objects.requireNonNull(precision, "precision cannot be null");
        }
    }

    public static String formatCoordinates(double latitude, double longitude) {
        validateLatitude(latitude);
        validateLongitude(longitude);
        return String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static void validateLatitude(double latitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
    }

    private static void validateLongitude(double longitude) {
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
