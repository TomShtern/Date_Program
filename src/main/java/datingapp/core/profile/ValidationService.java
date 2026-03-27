package datingapp.core.profile;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.profile.MatchPreferences.Interest;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final String BIO_TOO_LONG = "Bio too long (max %d chars)";
    private static final String INVALID_EMAIL = "Invalid email format";
    private static final String INVALID_PHONE = "Invalid phone format";
    private static final String INVALID_ZIP = "Israeli ZIP code must be 7 digits (e.g., 6701101)";
    private static final String INVALID_PHOTO_URL = "Invalid photo URL";
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PHONE_DIGITS = 7;
    private static final int MAX_PHONE_DIGITS = 15;
    private static final Pattern EMAIL_LOCAL_PATTERN = Pattern.compile("^[^\\s@]+$");
    private static final Pattern DOMAIN_LABEL_PATTERN =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");
    private static final Pattern PHONE_ALLOWED_PATTERN = Pattern.compile("^[+0-9()\\-\\s]+$");
    private static final Set<String> PHOTO_URL_SCHEMES = Set.of("http", "https");
    private static final String FILE_URLS_ENABLED_ENV = "DATING_APP_ALLOW_FILE_URLS";
    private static final String FILE_URLS_ENABLED_PROPERTY = "datingapp.allowFileUrls";
    private static final String FILE_URL_ROOT_ENV = "DATING_APP_ALLOWED_FILE_URL_ROOT";
    private static final String FILE_URL_ROOT_PROPERTY = "datingapp.allowedFileUrlRoot";

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
        try {
            normalizeEmail(email);
            return ValidationResult.success();
        } catch (IllegalArgumentException e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

    public ValidationResult validatePhone(String phone) {
        try {
            normalizePhone(phone);
            return ValidationResult.success();
        } catch (IllegalArgumentException e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

    public ValidationResult validatePhotoUrl(String photoUrl) {
        try {
            normalizePhotoUrl(photoUrl);
            return ValidationResult.success();
        } catch (IllegalArgumentException e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

    /**
     * Validates a user's age.
     *
     * @param age the age to validate
     * @return validation result
     */
    public ValidationResult validateAge(int age) {
        if (age < config.validation().minAge()) {
            return ValidationResult.failure(
                    AGE_TOO_YOUNG.formatted(config.validation().minAge()));
        }
        if (age > config.validation().maxAge()) {
            return ValidationResult.failure(AGE_INVALID);
        }
        return ValidationResult.success();
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
        if (age < config.validation().minAge()) {
            return ValidationResult.failure(
                    AGE_TOO_YOUNG.formatted(config.validation().minAge()));
        }
        if (age > config.validation().maxAge()) {
            return ValidationResult.failure(AGE_INVALID);
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
        if (bio == null) {
            return ValidationResult.success();
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

        if (latitude < -90 || latitude > 90) {
            errors.add("Invalid latitude (must be -90 to 90)");
        }
        if (longitude < -180 || longitude > 180) {
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
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(email.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException(INVALID_EMAIL);
        }
        int atIndex = normalized.lastIndexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1 || normalized.indexOf('@') != atIndex) {
            throw new IllegalArgumentException(INVALID_EMAIL);
        }

        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);
        if (!EMAIL_LOCAL_PATTERN.matcher(localPart).matches()
                || containsControlCharacters(localPart)
                || domainPart.isBlank()) {
            throw new IllegalArgumentException(INVALID_EMAIL);
        }

        String asciiDomain;
        try {
            asciiDomain = IDN.toASCII(domainPart);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException(INVALID_EMAIL);
        }
        if (!isValidAsciiDomain(asciiDomain)) {
            throw new IllegalArgumentException(INVALID_EMAIL);
        }

        return localPart + "@" + asciiDomain.toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.trim();
        if (!PHONE_ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(INVALID_PHONE);
        }
        String digitsOnly = normalized.replaceAll("\\D", "");
        if (digitsOnly.length() < MIN_PHONE_DIGITS || digitsOnly.length() > MAX_PHONE_DIGITS) {
            throw new IllegalArgumentException(INVALID_PHONE);
        }
        return normalized.startsWith("+") ? "+" + digitsOnly : digitsOnly;
    }

    public static String normalizePhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(photoUrl.trim()).normalize();
        } catch (URISyntaxException _) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if ("file".equals(normalizedScheme)) {
            return normalizeFilePhotoUrl(uri);
        }
        if (!PHOTO_URL_SCHEMES.contains(normalizedScheme)) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        return uri.toASCIIString();
    }

    private static String normalizeFilePhotoUrl(URI uri) {
        if (!isFilePhotoUrlEnabled()) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
        if (uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
        if (uri.getAuthority() != null && !uri.getAuthority().isBlank()) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
        if (uri.getPath().contains("..")) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        final Path normalizedPath;
        try {
            normalizedPath = Path.of(uri).toAbsolutePath().normalize();
        } catch (RuntimeException _) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        Path allowedRoot = resolveAllowedFileUrlRoot();
        if (!normalizedPath.startsWith(allowedRoot)) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }

        return normalizedPath.toUri().toASCIIString();
    }

    private static boolean isFilePhotoUrlEnabled() {
        String configured =
                firstNonBlank(System.getProperty(FILE_URLS_ENABLED_PROPERTY), System.getenv(FILE_URLS_ENABLED_ENV));
        return "true".equalsIgnoreCase(configured);
    }

    private static Path resolveAllowedFileUrlRoot() {
        String configured = firstNonBlank(System.getProperty(FILE_URL_ROOT_PROPERTY), System.getenv(FILE_URL_ROOT_ENV));
        try {
            if (configured != null) {
                return Path.of(configured).toAbsolutePath().normalize();
            }
            return Path.of(System.getProperty("user.home"), ".datingapp", "photos")
                    .toAbsolutePath()
                    .normalize();
        } catch (RuntimeException _) {
            throw new IllegalArgumentException(INVALID_PHOTO_URL);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch));
    }

    private static boolean isValidAsciiDomain(String domain) {
        if (domain == null || domain.isBlank() || domain.length() > 253) {
            return false;
        }
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isBlank()
                    || label.length() > 63
                    || !DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
