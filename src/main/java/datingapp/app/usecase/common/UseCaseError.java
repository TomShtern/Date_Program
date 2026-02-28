package datingapp.app.usecase.common;

import java.util.Objects;

/** Structured error metadata returned from use-case boundaries. */
public record UseCaseError(Code code, String message) {

    public enum Code {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        FORBIDDEN,
        DEPENDENCY,
        INTERNAL
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

    public static UseCaseError conflict(String message) {
        return new UseCaseError(Code.CONFLICT, message);
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
}
