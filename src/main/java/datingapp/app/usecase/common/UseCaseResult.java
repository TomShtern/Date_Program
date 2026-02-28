package datingapp.app.usecase.common;

import java.util.Objects;

/** Generic success/failure envelope for application use-cases. */
public record UseCaseResult<T>(boolean success, T data, UseCaseError error) {

    public UseCaseResult {
        if (success) {
            if (error != null) {
                throw new IllegalArgumentException("error must be null for success result");
            }
        } else {
            Objects.requireNonNull(error, "error cannot be null for failure result");
            if (data != null) {
                throw new IllegalArgumentException("data must be null for failure result");
            }
        }
    }

    public static <T> UseCaseResult<T> success(T data) {
        return new UseCaseResult<>(true, data, null);
    }

    public static <T> UseCaseResult<T> failure(UseCaseError error) {
        return new UseCaseResult<>(false, null, Objects.requireNonNull(error, "error cannot be null"));
    }
}
