package datingapp.core.matching;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for moderation audit events.
 *
 * <p>Framework-free by design: the {@code core} layer declares the contract, while the concrete
 * PostgreSQL/JDBI implementation lives in {@code storage/jdbi}. A {@link #noOp()} implementation is
 * provided so callers that do not need durable audits (tests, in-memory runtimes) can opt out
 * without a {@code null} check at every call site.
 */
public interface ModerationAuditStorage {

    /** Persists a single moderation audit event. Implementations must not throw for transient issues. */
    void append(ModerationAuditEvent event);

    /** Returns the most recent audit events for a target user, newest first, bounded by {@code limit}. */
    default List<ModerationAuditEvent> findRecentForTarget(UUID targetId, int limit) {
        return List.of();
    }

    /** A storage that intentionally persists nothing; used when durable audit is not configured. */
    static ModerationAuditStorage noOp() {
        return new ModerationAuditStorage() {
            @Override
            public void append(ModerationAuditEvent event) {
                // Intentionally discard: durable moderation audit not configured for this runtime.
            }
        };
    }
}
