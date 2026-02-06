package datingapp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Centralized validation service for user input. Provides consistent validation
 * logic for profile
 * fields and preferences with user-friendly error messages.
 *
 * <p>
 * All validation thresholds are sourced from {@link AppConfig#defaults()} for
 * consistency with domain model validation (FI-CONS-004).
 */
public class ValidationService {

    /** Shared configuration for validation thresholds. */
    private final AppConfig config;

    public ValidationService() {
        this(AppConfig.defaults());
    }

    public ValidationService(AppConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Result of a validation operation. Contains success status and any error
     * messages.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
            if (valid && !errors.isEmpty()) {
                throw new IllegalArgumentException("Valid results cannot contain errors");
            }
            if (!valid && errors.isEmpty()) {
                throw new IllegalArgumentException("Invalid results must include errors");
            }
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Validates a user's name.
     *
     * @param name the name to validate
     * @return validation result
     */
    public ValidationResult validateName(String name) {
        if (name == null || name.isBlank()) {
            return ValidationResult.failure("Name cannot be empty");
        }
        if (name.length() > config.maxNameLength()) {
            return ValidationResult.failure("Name too long (max " + config.maxNameLength() + " chars)");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a user's age.
     *
     * @param age the age to validate
     * @return validation result
     */
    public ValidationResult validateAge(int age) {
        if (age < config.minAge()) {
            return ValidationResult.failure("Must be " + config.minAge() + " or older");
        }
        if (age > config.maxAge()) {
            return ValidationResult.failure("Invalid age");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a height in centimeters.
     *
     * @param heightCm the height to validate
     * @return validation result
     */
    public ValidationResult validateHeight(int heightCm) {
        if (heightCm < config.minHeightCm()) {
            return ValidationResult.failure("Height too short (min " + config.minHeightCm() + "cm)");
        }
        if (heightCm > config.maxHeightCm()) {
            return ValidationResult.failure("Height too tall (max " + config.maxHeightCm() + "cm)");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a distance in kilometers.
     *
     * @param distanceKm the distance to validate
     * @return validation result
     */
    public ValidationResult validateDistance(int distanceKm) {
        if (distanceKm < config.minDistanceKm()) {
            return ValidationResult.failure("Distance must be at least " + config.minDistanceKm() + "km");
        }
        if (distanceKm > config.maxDistanceKm()) {
            return ValidationResult.failure("Distance too far (max " + config.maxDistanceKm() + "km)");
        }
        return ValidationResult.success();
    }

    /**
     * Validates a user's bio.
     *
     * @param bio the bio to validate
     * @return validation result
     */
    public ValidationResult validateBio(String bio) {
        if (bio == null) {
            return ValidationResult.success();
        }
        if (bio.length() > config.maxBioLength()) {
            return ValidationResult.failure("Bio too long (max " + config.maxBioLength() + " chars)");
        }
        return ValidationResult.success();
    }

    /**
     * Validates an age range preference.
     *
     * @param min the minimum age
     * @param max the maximum age
     * @return validation result
     */
    public ValidationResult validateAgeRange(int min, int max) {
        List<String> errors = new ArrayList<>();

        if (min < config.minAge()) {
            errors.add("Minimum age must be " + config.minAge() + "+");
        }
        if (max > config.maxAge()) {
            errors.add("Maximum age invalid");
        }
        if (min > max) {
            errors.add("Min age cannot exceed max age");
        }
        if (max - min < config.minAgeRangeSpan()) {
            errors.add("Age range too narrow (min " + config.minAgeRangeSpan() + " years)");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Validates geographic coordinates.
     *
     * @param latitude  the latitude to validate
     * @param longitude the longitude to validate
     * @return validation result
     */
    public ValidationResult validateLocation(double latitude, double longitude) {
        List<String> errors = new ArrayList<>();

        if (latitude < -90 || latitude > 90) {
            errors.add("Invalid latitude (must be -90 to 90)");
        }
        if (longitude < -180 || longitude > 180) {
            errors.add("Invalid longitude (must be -180 to 180)");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
