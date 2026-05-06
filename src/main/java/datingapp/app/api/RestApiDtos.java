package datingapp.app.api;

/**
 * Common REST API DTOs that are shared across endpoint domains.
 */
final class RestApiDtos {
    private RestApiDtos() {}

    /** Health check response. */
    static record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    static record ErrorResponse(String code, String message) {}
}
