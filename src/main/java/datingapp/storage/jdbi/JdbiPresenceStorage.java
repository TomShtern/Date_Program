package datingapp.storage.jdbi;

import datingapp.core.presence.PresenceStorage;
import datingapp.storage.DatabaseDialect;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/** JDBI-backed {@link PresenceStorage}. */
public final class JdbiPresenceStorage implements PresenceStorage {

    private final Jdbi jdbi;
    private final String upsertSql;

    public JdbiPresenceStorage(Jdbi jdbi, DatabaseDialect dialect) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        Objects.requireNonNull(dialect, "dialect cannot be null");
        this.upsertSql = SqlDialectSupport.upsertSql(
                dialect,
                "presence_heartbeat",
                List.of(
                        new SqlDialectSupport.ColumnBinding("user_id", "userId"),
                        new SqlDialectSupport.ColumnBinding("last_seen", "lastSeen"),
                        new SqlDialectSupport.ColumnBinding("is_typing_in", "typingIn"),
                        new SqlDialectSupport.ColumnBinding("updated_at", "updatedAt")),
                List.of("user_id"));
    }

    @Override
    public void upsert(UUID userId, Instant lastSeen, String typingIn, Instant updatedAt) {
        jdbi.useHandle(handle -> handle.createUpdate(upsertSql)
                .bind("userId", userId)
                .bind("lastSeen", lastSeen)
                .bind("typingIn", typingIn)
                .bind("updatedAt", updatedAt)
                .execute());
    }

    @Override
    public Optional<Heartbeat> get(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT user_id, last_seen, is_typing_in, updated_at FROM presence_heartbeat WHERE user_id = :userId")
                .bind("userId", userId)
                .map((rs, ctx) -> new Heartbeat(
                        JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id"),
                        JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "last_seen"),
                        rs.getString("is_typing_in"),
                        JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "updated_at")))
                .findFirst());
    }

    @Override
    public void deleteStaleBefore(Instant cutoff) {
        jdbi.useHandle(handle -> handle.createUpdate("DELETE FROM presence_heartbeat WHERE updated_at < :cutoff")
                .bind("cutoff", cutoff)
                .execute());
    }
}
