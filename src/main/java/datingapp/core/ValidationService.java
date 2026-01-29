package datingapp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized validation service for user input. Provides consistent validation
 * logic for profile
 * fields and preferences with user-friendly error messages.
 */
public class ValidationService {

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
        if (name.length() > 100) {
            return ValidationResult.failure("Name too long (max 100 chars)");
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
        if (age < 18) {
            return ValidationResult.failure("Must be 18 or older");
        }
        if (age > 120) {
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
        if (heightCm < 50) {
            return ValidationResult.failure("Height too short (min 50cm)");
        }
        if (heightCm > 300) {
            return ValidationResult.failure("Height too tall (max 300cm)");
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
        if (distanceKm < 1) {
            return ValidationResult.failure("Distance must be at least 1km");
        }
        if (distanceKm > 500) {
            return ValidationResult.failure("Distance too far (max 500km)");
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
        if (bio.length() > 1000) {
            return ValidationResult.failure("Bio too long (max 1000 chars)");
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

        if (min < 18) {
            errors.add("Minimum age must be 18+");
        }
        if (max > 120) {
            errors.add("Maximum age invalid");
        }
        if (min > max) {
            errors.add("Min age cannot exceed max age");
        }
        if (max - min < 5) {
            errors.add("Age range too narrow (min 5 years)");
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
