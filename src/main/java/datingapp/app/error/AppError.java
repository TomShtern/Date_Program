package datingapp.app.error;

import datingapp.app.usecase.common.UseCaseError;

/**
 * Sealed hierarchy for typed application errors.
 * Each variant carries a human-readable message and domain-specific context.
 */
public sealed interface AppError {

    String message();

    record Validation(String message, String field) implements AppError {}

    record NotFound(String message, String resourceType, String resourceId) implements AppError {}

    record Conflict(String message) implements AppError {}

    record Forbidden(String message) implements AppError {}

    record Dependency(String message, String serviceName) implements AppError {}

    record Infrastructure(String message, Throwable cause) implements AppError {}

    record Internal(String message) implements AppError {}

    /** Bridge FROM existing {@link UseCaseError}. */
    static AppError fromUseCaseError(UseCaseError error) {
        return switch (error.code()) {
            case VALIDATION -> new Validation(error.message(), null);
            case NOT_FOUND -> new NotFound(error.message(), null, null);
            case CONFLICT -> new Conflict(error.message());
            case FORBIDDEN -> new Forbidden(error.message());
            case DEPENDENCY -> new Dependency(error.message(), null);
            case INTERNAL -> new Internal(error.message());
        };
    }

    /** Bridge TO existing {@link UseCaseError} (for backward compat). */
    default UseCaseError toUseCaseError() {
        return switch (this) {
            case Validation v -> UseCaseError.validation(v.message());
            case NotFound n -> UseCaseError.notFound(n.message());
            case Conflict c -> UseCaseError.conflict(c.message());
            case Forbidden f -> UseCaseError.forbidden(f.message());
            case Dependency d -> UseCaseError.dependency(d.message());
            case Infrastructure i -> UseCaseError.internal(i.message());
            case Internal i -> UseCaseError.internal(i.message());
        };
    }
}
