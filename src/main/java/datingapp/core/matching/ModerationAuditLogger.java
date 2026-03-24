package datingapp.core.matching;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits structured moderation audit events. */
public final class ModerationAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger("audit.moderation");
    private static final String CLASSIFICATION = "restricted_pii";
    private static final String INDEXING_POLICY = "exclude_from_broad_analytics";
    private static final int RETENTION_DAYS = 30;

    public void log(ModerationAuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        logger.atInfo()
                .addKeyValue("dataClassification", CLASSIFICATION)
                .addKeyValue("indexingPolicy", INDEXING_POLICY)
                .addKeyValue("retentionDays", RETENTION_DAYS)
                .addKeyValue("containsSensitiveIds", true)
                .addKeyValue("timestamp", event.timestamp())
                .addKeyValue("actorId", event.actorId())
                .addKeyValue("targetId", event.targetId())
                .addKeyValue("action", event.action())
                .addKeyValue("outcome", event.outcome())
                .addKeyValue("context", event.context())
                .log("moderation_audit");
    }
}
