package datingapp.core.storage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthStorage {

    Optional<String> findPasswordHash(UUID userId);

    void savePasswordHash(UUID userId, String passwordHash, Instant createdAt, Instant updatedAt);

    Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash);

    void insertRefreshToken(RefreshTokenRecord refreshToken);

    void revokeRefreshToken(UUID tokenId, Instant revokedAt, UUID replacedByTokenId);

    record RefreshTokenRecord(
            UUID tokenId,
            UUID userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedByTokenId) {}
}
