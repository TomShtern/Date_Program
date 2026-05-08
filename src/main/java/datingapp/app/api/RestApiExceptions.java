package datingapp.app.api;

final class RestApiExceptions {

    private RestApiExceptions() {}

    static final class ApiForbiddenException extends RuntimeException {
        ApiForbiddenException(String message) {
            super(message);
        }
    }

    static final class ApiUnauthorizedException extends RuntimeException {
        ApiUnauthorizedException(String message) {
            super(message);
        }
    }

    static final class ApiConflictException extends RuntimeException {
        ApiConflictException(String message) {
            super(message);
        }
    }

    static final class ApiTooManyRequestsException extends RuntimeException {
        private final RateLimitStatus status;

        ApiTooManyRequestsException(String message, RateLimitStatus status) {
            super(message);
            this.status = status;
        }

        RateLimitStatus status() {
            return status;
        }
    }

    record RateLimitStatus(int limit, int used, long retryAfterSeconds) implements java.io.Serializable {}
}
