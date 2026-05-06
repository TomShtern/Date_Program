package datingapp.storage.jdbi;

import datingapp.core.storage.AuthStorage;
import datingapp.storage.DatabaseDialect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public final class JdbiAuthStorage implements AuthStorage {

    private static final String BIND_USER_ID = "userId";
    private static final String INSERT_REFRESH_TOKEN_SQL = """
            INSERT INTO auth_refresh_tokens (
                token_id,
                user_id,
                token_hash,
                issued_at,
                expires_at,
                revoked_at,
                replaced_by_token_id
            ) VALUES (
                :tokenId,
                :userId,
                :tokenHash,
                :issuedAt,
                :expiresAt,
                :revokedAt,
                :replacedByTokenId
            )
            """;
    private static final String REVOKE_REFRESH_TOKEN_SQL = "UPDATE auth_refresh_tokens "
            + "SET revoked_at = :revokedAt, replaced_by_token_id = :replacedByTokenId "
            + "WHERE token_id = :tokenId";

    private final Jdbi jdbi;
    private final String upsertPasswordHashSql;

    public JdbiAuthStorage(Jdbi jdbi) {
        this(jdbi, SqlDialectSupport.detectDialect(jdbi));
    }

    public JdbiAuthStorage(Jdbi jdbi, DatabaseDialect dialect) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        Objects.requireNonNull(dialect, "dialect cannot be null");
        this.upsertPasswordHashSql = buildUpsertPasswordHashSql(dialect);
    }

    @Override
    public Optional<String> findPasswordHash(UUID userId) {
        return jdbi.withHandle(
                handle -> handle.createQuery("SELECT password_hash FROM user_credentials WHERE user_id = :userId")
                        .bind(BIND_USER_ID, userId)
                        .mapTo(String.class)
                        .findOne());
    }

    @Override
    public void savePasswordHash(UUID userId, String passwordHash, Instant createdAt, Instant updatedAt) {
        jdbi.useHandle(handle -> handle.createUpdate(upsertPasswordHashSql)
                .bind(BIND_USER_ID, userId)
                .bind("passwordHash", passwordHash)
                .bind("createdAt", createdAt)
                .bind("updatedAt", updatedAt)
                .execute());
    }

    @Override
    public Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT token_id, user_id, token_hash, issued_at, expires_at, revoked_at, replaced_by_token_id "
                                + "FROM auth_refresh_tokens WHERE token_hash = :tokenHash")
                .bind("tokenHash", tokenHash)
                .map(new RefreshTokenMapper())
                .findOne());
    }

    @Override
    public void insertRefreshToken(RefreshTokenRecord refreshToken) {
        jdbi.useHandle(handle -> {
            var update = handle.createUpdate(INSERT_REFRESH_TOKEN_SQL)
                    .bind("tokenId", refreshToken.tokenId())
                    .bind(BIND_USER_ID, refreshToken.userId())
                    .bind("tokenHash", refreshToken.tokenHash())
                    .bind("issuedAt", refreshToken.issuedAt())
                    .bind("expiresAt", refreshToken.expiresAt());
            if (refreshToken.revokedAt() == null) {
                update.bindNull("revokedAt", Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                update.bind("revokedAt", refreshToken.revokedAt());
            }
            if (refreshToken.replacedByTokenId() == null) {
                update.bindNull("replacedByTokenId", Types.OTHER);
            } else {
                update.bind("replacedByTokenId", refreshToken.replacedByTokenId());
            }
            update.execute();
        });
    }

    @Override
    public void revokeRefreshToken(UUID tokenId, Instant revokedAt, UUID replacedByTokenId) {
        jdbi.useHandle(handle -> {
            var update = handle.createUpdate(REVOKE_REFRESH_TOKEN_SQL)
                    .bind("revokedAt", revokedAt)
                    .bind("tokenId", tokenId);
            if (replacedByTokenId == null) {
                update.bindNull("replacedByTokenId", Types.OTHER);
            } else {
                update.bind("replacedByTokenId", replacedByTokenId);
            }
            update.execute();
        });
    }

    private static String buildUpsertPasswordHashSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "user_credentials",
                List.of(
                        new SqlDialectSupport.ColumnBinding("user_id", "userId"),
                        new SqlDialectSupport.ColumnBinding("password_hash", "passwordHash"),
                        new SqlDialectSupport.ColumnBinding("created_at", "createdAt"),
                        new SqlDialectSupport.ColumnBinding("updated_at", "updatedAt")),
                List.of("user_id"));
    }

    private static final class RefreshTokenMapper implements RowMapper<RefreshTokenRecord> {
        @Override
        public RefreshTokenRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
            String replacedByTokenId = rs.getString("replaced_by_token_id");
            return new RefreshTokenRecord(
                    UUID.fromString(rs.getString("token_id")),
                    UUID.fromString(rs.getString("user_id")),
                    rs.getString("token_hash"),
                    rs.getObject("issued_at", Instant.class),
                    rs.getObject("expires_at", Instant.class),
                    rs.getObject("revoked_at", Instant.class),
                    replacedByTokenId == null ? null : UUID.fromString(replacedByTokenId));
        }
    }
}
