package datingapp.core.matching;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits structured moderation audit events.
 *
 * <p>Every event is written to the {@code audit.moderation} SLF4J logger for log aggregation. When a
 * durable {@link ModerationAuditStorage} is configured (SQL runtimes), the same event is also persisted
 * so the admin moderation queue survives restarts. Persistence is best-effort: a storage failure must
 * never break the user-facing moderation action, so storage exceptions are caught and logged.
 */
public final class ModerationAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger("audit.moderation");
    private static final String CLASSIFICATION = "restricted_pii";
    private static final String INDEXING_POLICY = "exclude_from_broad_analytics";
    private static final int RETENTION_DAYS = 30;

    private final ModerationAuditStorage storage;

    /** Creates a logger without durable persistence (SLF4J only). */
    public ModerationAuditLogger() {
        this(ModerationAuditStorage.noOp());
    }

    /** Creates a logger that also persists events to {@code storage}. */
    public ModerationAuditLogger(ModerationAuditStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
    }

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

        try {
            storage.append(event);
        } catch (RuntimeException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to persist moderation audit event for target {}", event.targetId(), exception);
            }
        }
    }
}
