package datingapp.app.usecase.common;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured error metadata returned from use-case boundaries.
 *
 * <p>{@code reason} is an optional stable machine-readable code that narrows {@code code} (for
 * example which kind of conflict); it is null when the category alone is enough.
 */
public record UseCaseError(Code code, String message, String reason) {

    private static final Logger LOG = LoggerFactory.getLogger(UseCaseError.class);

    public enum Code {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        UNAUTHORIZED,
        FORBIDDEN,
        DEPENDENCY,
        INTERNAL,
        RATE_LIMITED,
        GONE
    }

    public UseCaseError {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public static UseCaseError validation(String message) {
        return new UseCaseError(Code.VALIDATION, message);
    }

    public static UseCaseError notFound(String message) {
        return new UseCaseError(Code.NOT_FOUND, message);
    }

    public UseCaseError(Code code, String message) {
        this(code, message, null);
    }

    public static UseCaseError conflict(String message) {
        return new UseCaseError(Code.CONFLICT, message);
    }

    /** A conflict the caller can tell apart from other conflicts by its stable {@code reason} code. */
    public static UseCaseError conflict(String reason, String message) {
        return new UseCaseError(Code.CONFLICT, message, Objects.requireNonNull(reason, "reason cannot be null"));
    }

    public static UseCaseError unauthorized(String message) {
        return new UseCaseError(Code.UNAUTHORIZED, message);
    }

    public static UseCaseError forbidden(String message) {
        return new UseCaseError(Code.FORBIDDEN, message);
    }

    public static UseCaseError dependency(String message) {
        return new UseCaseError(Code.DEPENDENCY, message);
    }

    public static UseCaseError internal(String message) {
        return new UseCaseError(Code.INTERNAL, message);
    }

    public static UseCaseError rateLimited(String message) {
        return new UseCaseError(Code.RATE_LIMITED, message);
    }

    public static UseCaseError gone(String message) {
        return new UseCaseError(Code.GONE, message);
    }

    /**
     * Central internal-failure helper. Logs the cause server-side (with channel when known) and
     * returns a stable message so exception details never leak to API/CLI/UI surfaces.
     */
    public static UseCaseError internalFailure(String action, Exception cause) {
        return internalFailure(null, action, cause);
    }

    public static UseCaseError internalFailure(UserContext context, String action, Exception cause) {
        String channel = context == null ? "unknown" : context.channel();
        String safeAction = (action == null || action.isBlank()) ? "operation" : action.trim();
        if (cause == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Use-case internal failure action={} channel={}", safeAction, channel);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error(
                        "Use-case internal failure action={} channel={} error={}: {}",
                        safeAction,
                        channel,
                        cause.getClass().getSimpleName(),
                        cause.getMessage(),
                        cause);
            }
        }
        return internal("Failed to " + safeAction + ".");
    }
}
