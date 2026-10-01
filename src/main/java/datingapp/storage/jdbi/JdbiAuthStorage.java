package datingapp.storage.jdbi;

import datingapp.core.storage.AuthStorage;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

public final class JdbiAuthStorage implements AuthStorage {

    private static final String BIND_USER_ID = "userId";
    private static final String BIND_CLERK_USER_ID = "clerkUserId";
    /** SQLSTATE class 23 is "integrity constraint violation" on both H2 and PostgreSQL. */
    private static final String INTEGRITY_VIOLATION_CLASS = "23";

    private final Jdbi jdbi;

    public JdbiAuthStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    @Override
    public Optional<UUID> findUserIdByClerkId(String clerkUserId) {
        return jdbi.withHandle(
                handle -> handle.createQuery("SELECT user_id FROM clerk_identities WHERE clerk_user_id = :clerkUserId")
                        .bind(BIND_CLERK_USER_ID, clerkUserId)
                        .mapTo(UUID.class)
                        .findOne());
    }

    @Override
    public boolean linkClerkId(String clerkUserId, UUID userId, Instant createdAt) {
        try {
            jdbi.useHandle(handle -> handle.createUpdate("""
                    INSERT INTO clerk_identities (clerk_user_id, user_id, created_at)
                    VALUES (:clerkUserId, :userId, :createdAt)
                    """)
                    .bind(BIND_CLERK_USER_ID, clerkUserId)
                    .bind(BIND_USER_ID, userId)
                    .bind("createdAt", createdAt)
                    .execute());
            return true;
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof SQLException sql
                    && sql.getSQLState() != null
                    && sql.getSQLState().startsWith(INTEGRITY_VIOLATION_CLASS)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void deleteIdentityForUser(UUID userId) {
        jdbi.useHandle(handle -> handle.createUpdate("DELETE FROM clerk_identities WHERE user_id = :userId")
                .bind(BIND_USER_ID, userId)
                .execute());
    }
}
