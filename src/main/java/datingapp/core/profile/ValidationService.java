package datingapp.core.profile;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.TextNormalization;
import datingapp.core.profile.MatchPreferences.Interest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ValidationService {

    // Error message constants (inlined from ErrorMessages.java)
    private static final String NAME_EMPTY = "Name cannot be empty";
    private static final String NAME_TOO_LONG = "Name too long (max %d chars)";
    private static final String AGE_TOO_YOUNG = "Must be %d or older";
    private static final String AGE_INVALID = "Invalid age";
    private static final String HEIGHT_TOO_SHORT = "Height too short (min %dcm)";
    private static final String HEIGHT_TOO_TALL = "Height too tall (max %dcm)";
    private static final String DISTANCE_TOO_SHORT = "Distance must be at least %dkm";
    private static final String DISTANCE_TOO_FAR = "Distance too far (max %dkm)";
    private static final String BIO_EMPTY = "Bio cannot be blank";
    private static final String BIO_TOO_LONG = "Bio too long (max %d chars)";
    private static final String INVALID_ZIP = "Israeli ZIP code must be 7 digits (e.g., 6701101)";

    /** Shared configuration for validation thresholds. */
    private final AppConfig config;

    public ValidationService(AppConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Result of a validation operation. Contains success status and any error
     * messages.
     */
    public static record ValidationResult(boolean valid, List<String> errors) {
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
            return ValidationResult.failure(NAME_EMPTY);
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC);
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            return ValidationResult.failure("Name contains invalid control characters");
        }
        if (normalized.length() > config.validation().maxNameLength()) {
            return ValidationResult.failure(
                    NAME_TOO_LONG.formatted(config.validation().maxNameLength()));
        }
        return ValidationResult.success();
    }

    public ValidationResult validateEmail(String email) {
        return validateNormalized(() -> TextNormalization.normalizeEmail(email));
    }

    public ValidationResult validatePhone(String phone) {
        return validateNormalized(() -> TextNormalization.normalizePhone(phone));
    }

    public ValidationResult validatePhotoUrl(String photoUrl) {
        return validateNormalized(() -> normalizePhotoUrl(photoUrl));
    }

    /**
     * Validates a user's age.
     *
     * @param age the age to validate
     * @return validation result
     */
    public ValidationResult validateAge(int age) {
        return validateAgeBounds(age);
    }

    /**
     * Validates a birth date against configured age bounds and chronology.
     *
     * @param birthDate the birth date to validate
     * @return validation result
     */
    public ValidationResult validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            return ValidationResult.failure("Invalid date");
        }

        LocalDate today = AppClock.today();
        if (birthDate.isAfter(today)) {
            return ValidationResult.failure("Invalid date");
        }

        int age = Period.between(birthDate, today).getYears();
        return validateAgeBounds(age);
    }

    /**
     * Validates a height in centimeters.
     *
     * @param heightCm the height to validate
     * @return validation result
     */
    public ValidationResult validateHeight(int heightCm) {
        if (heightCm < config.validation().minHeightCm()) {
            return ValidationResult.failure(
                    HEIGHT_TOO_SHORT.formatted(config.validation().minHeightCm()));
        }
        if (heightCm > config.validation().maxHeightCm()) {
            return ValidationResult.failure(
                    HEIGHT_TOO_TALL.formatted(config.validation().maxHeightCm()));
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
        if (distanceKm < config.validation().minDistanceKm()) {
            return ValidationResult.failure(
                    DISTANCE_TOO_SHORT.formatted(config.validation().minDistanceKm()));
        }
        if (distanceKm > config.matching().maxDistanceKm()) {
            return ValidationResult.failure(
                    DISTANCE_TOO_FAR.formatted(config.matching().maxDistanceKm()));
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
        if (bio == null || bio.isBlank()) {
            return ValidationResult.failure(BIO_EMPTY);
        }
        if (bio.length() > config.validation().maxBioLength()) {
            return ValidationResult.failure(
                    BIO_TOO_LONG.formatted(config.validation().maxBioLength()));
        }
        return ValidationResult.success();
    }

    public ValidationResult validateInterests(Set<Interest> interests) {
        if (interests == null) {
            return ValidationResult.success();
        }
        if (interests.size() > config.validation().maxInterests()) {
            return ValidationResult.failure(
                    "Cannot select more than " + config.validation().maxInterests() + " interests");
        }
        return ValidationResult.success();
    }

    /**
     * Validates message content for send operations.
     *
     * @param content the message text to validate
     * @return validation result
     */
    public ValidationResult validateMessageContent(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.failure("Message content cannot be empty");
        }
        return ValidationResult.success();
    }

    /**
     * Validates profile note content for upsert operations.
     *
     * @param content the note text to validate (after sanitization)
     * @return validation result
     */
    public ValidationResult validateProfileNoteContent(String content) {
        if (content != null && content.length() > config.validation().maxProfileNoteLength()) {
            return ValidationResult.failure(
                    "Note too long (max " + config.validation().maxProfileNoteLength() + " characters)");
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

        if (min < config.validation().minAge()) {
            errors.add("Minimum age must be " + config.validation().minAge() + "+");
        }
        if (max > config.validation().maxAge()) {
            errors.add("Maximum age invalid");
        }
        if (min > max) {
            errors.add("Min age cannot exceed max age");
        }
        if (max - min < config.validation().minAgeRangeSpan()) {
            errors.add("Age range too narrow (min " + config.validation().minAgeRangeSpan() + " years)");
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

        if (!Double.isFinite(latitude)) {
            errors.add("Invalid latitude (must be finite and -90 to 90)");
        } else if (latitude < -90 || latitude > 90) {
            errors.add("Invalid latitude (must be -90 to 90)");
        }
        if (!Double.isFinite(longitude)) {
            errors.add("Invalid longitude (must be finite and -180 to 180)");
        } else if (longitude < -180 || longitude > 180) {
            errors.add("Invalid longitude (must be -180 to 180)");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public ValidationResult validateZipCode(String zipCode, String countryCode) {
        if (zipCode == null || zipCode.isBlank()) {
            return ValidationResult.failure("ZIP code is required");
        }
        if (countryCode == null || countryCode.isBlank()) {
            return ValidationResult.failure("Country is required");
        }

        String normalized = zipCode.replaceAll("[\\s-]", "");
        return switch (countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "IL" ->
                normalized.matches("\\d{7}") ? ValidationResult.success() : ValidationResult.failure(INVALID_ZIP);
            default -> ValidationResult.failure("ZIP validation is not available for this country yet");
        };
    }

    public static String normalizeEmail(String email) {
        return TextNormalization.normalizeEmail(email);
    }

    public static String normalizePhone(String phone) {
        return TextNormalization.normalizePhone(phone);
    }

    public static String normalizePhotoUrl(String photoUrl) {
        return TextNormalization.normalizePhotoUrl(photoUrl);
    }

    private static ValidationResult validateNormalized(Runnable normalization) {
        try {
            normalization.run();
            return ValidationResult.success();
        } catch (IllegalArgumentException e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

    private ValidationResult validateAgeBounds(int age) {
        if (age < config.validation().minAge()) {
            return ValidationResult.failure(
                    AGE_TOO_YOUNG.formatted(config.validation().minAge()));
        }
        if (age > config.validation().maxAge()) {
            return ValidationResult.failure(AGE_INVALID);
        }
        return ValidationResult.success();
    }
}
