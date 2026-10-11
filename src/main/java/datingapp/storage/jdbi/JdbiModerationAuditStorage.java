package datingapp.storage.jdbi;

import datingapp.core.matching.ModerationAuditEvent;
import datingapp.core.matching.ModerationAuditStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * PostgreSQL persistence for moderation audit events.
 *
 * <p>Mirrors the {@code moderation_audit} table created by {@code SchemaInitializer.createModerationSchema}
 * and migration {@code V23}. The JSON context blob is stored via {@link JdbiNotificationJson} so the same
 * PII-safe, whitelisted metadata survives across restarts and can be rendered by the admin queue.
 */
public final class JdbiModerationAuditStorage implements ModerationAuditStorage {

    private static final String INSERT_SQL = """
            INSERT INTO moderation_audit (id, actor_id, target_id, action, outcome, reason, context_json, created_at)
            VALUES (:id, :actorId, :targetId, :action, :outcome, :reason, :contextJson, :createdAt)
            """;

    private static final String SELECT_BY_TARGET_SQL = """
            SELECT actor_id, target_id, action, outcome, context_json, created_at
            FROM moderation_audit
            WHERE target_id = :targetId
            ORDER BY created_at DESC
            LIMIT :limit
            """;

    private static final String REASON_KEY = "moderation_reason_code";

    private final Jdbi jdbi;

    public JdbiModerationAuditStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    @Override
    public void append(ModerationAuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        Map<String, String> context = event.context();
        jdbi.useHandle(handle -> {
            var update = handle.createUpdate(INSERT_SQL)
                    .bind("id", UUID.randomUUID())
                    .bind("action", event.action().name())
                    .bind("outcome", event.outcome().name())
                    .bind("reason", reasonOf(context))
                    .bind("contextJson", JdbiNotificationJson.write(context))
                    .bind("createdAt", event.timestamp());
            JdbiTypeCodecs.bindNullableUuid(update, "actorId", event.actorId());
            JdbiTypeCodecs.bindNullableUuid(update, "targetId", event.targetId());
            update.execute();
        });
    }

    @Override
    public List<ModerationAuditEvent> findRecentForTarget(UUID targetId, int limit) {
        Objects.requireNonNull(targetId, "targetId cannot be null");
        if (limit <= 0) {
            return List.of();
        }
        return jdbi.withHandle(handle -> {
            List<ModerationAuditEvent> results = new ArrayList<>();
            handle.createQuery(SELECT_BY_TARGET_SQL)
                    .bind("targetId", targetId)
                    .bind("limit", limit)
                    .map((rs, ctx) -> new ModerationAuditEvent(
                            JdbiTypeCodecs.SqlRowReaders.readRequiredInstant(rs, "created_at"),
                            JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "actor_id"),
                            JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "target_id"),
                            parseAction(rs.getString("action")),
                            parseOutcome(rs.getString("outcome")),
                            JdbiNotificationJson.read(rs.getString("context_json"))))
                    .forEach(results::add);
            return List.copyOf(results);
        });
    }

    private static String reasonOf(Map<String, String> context) {
        if (context == null) {
            return null;
        }
        String reason = context.get(REASON_KEY);
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.length() > 50 ? reason.substring(0, 50) : reason;
    }

    private static ModerationAuditEvent.Action parseAction(String raw) {
        if (raw == null) {
            return ModerationAuditEvent.Action.REPORT;
        }
        try {
            return ModerationAuditEvent.Action.valueOf(raw);
        } catch (IllegalArgumentException _) {
            return ModerationAuditEvent.Action.REPORT;
        }
    }

    private static ModerationAuditEvent.Outcome parseOutcome(String raw) {
        if (raw == null) {
            return ModerationAuditEvent.Outcome.FAILURE;
        }
        try {
            return ModerationAuditEvent.Outcome.valueOf(raw);
        } catch (IllegalArgumentException _) {
            return ModerationAuditEvent.Outcome.FAILURE;
        }
    }
}
