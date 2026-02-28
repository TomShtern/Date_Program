package datingapp.app.error;

import datingapp.app.usecase.common.UseCaseResult;
import java.util.Objects;

/**
 * Generic result type for application operations.
 * Interoperable with existing {@link UseCaseResult}.
 */
public record AppResult<T>(T data, AppError error) {

    public AppResult {
        if (data != null && error != null) {
            throw new IllegalArgumentException("Cannot have both data and error");
        }
    }

    public boolean success() {
        return error == null;
    }

    public static <T> AppResult<T> ok(T data) {
        return new AppResult<>(data, null);
    }

    public static <T> AppResult<T> ok() {
        return new AppResult<>(null, null);
    }

    public static <T> AppResult<T> fail(AppError error) {
        Objects.requireNonNull(error);
        return new AppResult<>(null, error);
    }

    /** Bridge FROM {@link UseCaseResult}. */
    public static <T> AppResult<T> fromUseCaseResult(UseCaseResult<T> r) {
        if (r.success()) {
            return ok(r.data());
        }
        return fail(AppError.fromUseCaseError(r.error()));
    }

    /** Bridge TO {@link UseCaseResult}. */
    public UseCaseResult<T> toUseCaseResult() {
        if (success()) {
            return UseCaseResult.success(data);
        }
        return UseCaseResult.failure(error.toUseCaseError());
    }
}
