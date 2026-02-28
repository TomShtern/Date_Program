package datingapp.app.usecase.common;

import java.util.Objects;
import java.util.UUID;

/** Immutable execution context for an application use-case invocation. */
public record UserContext(UUID userId, String channel) {

    public UserContext {
        Objects.requireNonNull(userId, "userId cannot be null");
        channel = (channel == null || channel.isBlank()) ? "unknown" : channel.trim();
    }

    public static UserContext of(UUID userId, String channel) {
        return new UserContext(userId, channel);
    }

    public static UserContext cli(UUID userId) {
        return new UserContext(userId, "cli");
    }

    public static UserContext ui(UUID userId) {
        return new UserContext(userId, "ui");
    }

    public static UserContext api(UUID userId) {
        return new UserContext(userId, "api");
    }
}
