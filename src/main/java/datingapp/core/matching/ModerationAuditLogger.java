package datingapp.core.matching;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits structured moderation audit events. */
public final class ModerationAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger("audit.moderation");

    public void log(ModerationAuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        logger.atInfo()
                .addKeyValue("timestamp", event.timestamp())
                .addKeyValue("actorId", event.actorId())
                .addKeyValue("targetId", event.targetId())
                .addKeyValue("action", event.action())
                .addKeyValue("outcome", event.outcome())
                .addKeyValue("context", event.context())
                .log("moderation_audit");
    }
}
