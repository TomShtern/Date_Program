package datingapp.core;

/**
 * Centralized error message constants for consistent, maintainable error reporting.
 *
 * <p>Messages that include dynamic values (e.g. limits) use format templates with {@code %d}/{@code
 * %s} placeholders and should be used with {@link String#formatted(Object...)}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Static message
 * return SendResult.failure(ErrorMessages.SENDER_NOT_FOUND, ErrorCode.USER_NOT_FOUND);
 *
 * // Parameterized message
 * return ValidationResult.failure(ErrorMessages.NAME_TOO_LONG.formatted(config.maxNameLength()));
 * }</pre>
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Utility class — not instantiable
    }

    // ── Messaging ──────────────────────────────────────────────────────

    public static final String SENDER_NOT_FOUND = "Sender not found or inactive";
    public static final String RECIPIENT_NOT_FOUND = "Recipient not found or inactive";
    public static final String NO_ACTIVE_MATCH = "Cannot message: no active match";
    public static final String EMPTY_MESSAGE = "Message cannot be empty";
    public static final String MESSAGE_TOO_LONG = "Message too long (max %d characters)";

    // ── Validation ─────────────────────────────────────────────────────

    public static final String NAME_EMPTY = "Name cannot be empty";
    public static final String NAME_TOO_LONG = "Name too long (max %d chars)";
    public static final String AGE_TOO_YOUNG = "Must be %d or older";
    public static final String AGE_INVALID = "Invalid age";
    public static final String HEIGHT_TOO_SHORT = "Height too short (min %dcm)";
    public static final String HEIGHT_TOO_TALL = "Height too tall (max %dcm)";
    public static final String DISTANCE_TOO_SHORT = "Distance must be at least %dkm";
    public static final String DISTANCE_TOO_FAR = "Distance too far (max %dkm)";
    public static final String BIO_TOO_LONG = "Bio too long (max %d chars)";

    // ── Undo ───────────────────────────────────────────────────────────

    public static final String NO_SWIPE_TO_UNDO = "No swipe to undo";
    public static final String UNDO_WINDOW_EXPIRED = "Undo window expired";
    public static final String LIKE_NOT_FOUND = "Like not found in database";
    public static final String UNDO_FAILED = "Failed to undo: %s";

    // ── General ────────────────────────────────────────────────────────

    public static final String USER_NOT_FOUND = "User not found";
    public static final String USER_NOT_ACTIVE = "User is not active";
    public static final String MATCH_NOT_FOUND = "Match not found";
    public static final String CANNOT_LIKE_SELF = "Cannot like yourself";
    public static final String NULL_PARAMETER = "%s cannot be null";
}
