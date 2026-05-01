package datingapp.storage.jdbi;

import datingapp.core.storage.AuthStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public final class JdbiAuthStorage implements AuthStorage {

    private static final String BIND_USER_ID = "userId";
    private static final String UPSERT_PASSWORD_HASH_SQL = """
            INSERT INTO user_credentials (user_id, password_hash, created_at, updated_at)
            VALUES (:userId, :passwordHash, :createdAt, :updatedAt)
            ON CONFLICT (user_id) DO UPDATE
            SET password_hash = EXCLUDED.password_hash,
                updated_at = EXCLUDED.updated_at
            """;
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

    public JdbiAuthStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
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
        jdbi.useHandle(handle -> handle.createUpdate(UPSERT_PASSWORD_HASH_SQL)
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
        jdbi.useHandle(handle -> handle.createUpdate(INSERT_REFRESH_TOKEN_SQL)
                .bind("tokenId", refreshToken.tokenId())
                .bind(BIND_USER_ID, refreshToken.userId())
                .bind("tokenHash", refreshToken.tokenHash())
                .bind("issuedAt", refreshToken.issuedAt())
                .bind("expiresAt", refreshToken.expiresAt())
                .bind("revokedAt", refreshToken.revokedAt())
                .bind("replacedByTokenId", refreshToken.replacedByTokenId())
                .execute());
    }

    @Override
    public void revokeRefreshToken(UUID tokenId, Instant revokedAt, UUID replacedByTokenId) {
        jdbi.useHandle(handle -> handle.createUpdate(REVOKE_REFRESH_TOKEN_SQL)
                .bind("revokedAt", revokedAt)
                .bind("replacedByTokenId", replacedByTokenId)
                .bind("tokenId", tokenId)
                .execute());
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
