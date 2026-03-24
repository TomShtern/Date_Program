package datingapp.core.matching;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Structured moderation audit event. */
public record ModerationAuditEvent(
        Instant timestamp, UUID actorId, UUID targetId, Action action, Outcome outcome, Map<String, String> context) {

    public enum Action {
        REPORT,
        BLOCK,
        UNBLOCK,
        AUTO_BAN
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public ModerationAuditEvent {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(outcome, "outcome cannot be null");
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
