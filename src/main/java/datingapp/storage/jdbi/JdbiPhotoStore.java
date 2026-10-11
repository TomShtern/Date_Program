package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.media.LocalDiskPhotoStore;
import datingapp.core.media.PhotoSanitizer;
import datingapp.core.media.PhotoStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * Database-backed {@link PhotoStore} for hosts without a persistent disk. Stored paths keep the
 * disk layout, {@code /photos/<userId>/<photoId>.<ext>}, so photo URLs do not depend on the backend.
 */
public final class JdbiPhotoStore implements PhotoStore {

    private static final String USER_ID = "userId";
    private static final String FILE_NAME = "fileName";
    private static final String INSERT_SQL = "INSERT INTO photo_blobs (user_id, file_name, content, created_at) "
            + "VALUES (:userId, :fileName, :content, :createdAt)";

    private final Jdbi jdbi;
    private final long maxBytes;

    public JdbiPhotoStore(Jdbi jdbi, long maxBytes) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public ManagedPhoto store(UUID userId, String contentType, byte[] bytes) {
        Objects.requireNonNull(userId, "userId cannot be null");
        byte[] sanitized = PhotoSanitizer.sanitize(contentType, bytes, maxBytes);
        String photoId = UUID.randomUUID().toString();
        String fileName = photoId + "." + PhotoSanitizer.extensionFor(contentType);
        jdbi.useHandle(handle -> handle.createUpdate(INSERT_SQL)
                .bind(USER_ID, userId)
                .bind(FILE_NAME, fileName)
                .bind("content", sanitized)
                .bind("createdAt", AppClock.now())
                .execute());
        return new ManagedPhoto(photoId, LocalDiskPhotoStore.PHOTOS_ROUTE_PREFIX + userId + "/" + fileName);
    }

    @Override
    public Optional<PhotoContent> load(UUID userId, String fileName) {
        if (!PhotoSanitizer.isSafeFileName(fileName)) {
            return Optional.empty();
        }
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT content FROM photo_blobs WHERE user_id = :userId AND file_name = :fileName")
                .bind(USER_ID, userId)
                .bind(FILE_NAME, fileName)
                .map((rs, ctx) ->
                        new PhotoContent(rs.getBytes("content"), PhotoSanitizer.contentTypeFromFileName(fileName)))
                .findFirst());
    }

    @Override
    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        String normalized = storedPath.startsWith("http://") || storedPath.startsWith("https://")
                ? storedPath.substring(storedPath.indexOf('/', storedPath.indexOf("://") + 3))
                : storedPath.startsWith("/") ? storedPath : "/" + storedPath;
        if (!normalized.startsWith(LocalDiskPhotoStore.PHOTOS_ROUTE_PREFIX)) {
            return;
        }
        String[] parts = normalized
                .substring(LocalDiskPhotoStore.PHOTOS_ROUTE_PREFIX.length())
                .split("/");
        if (parts.length != 2 || !PhotoSanitizer.isSafeFileName(parts[1])) {
            throw new IllegalArgumentException("Invalid managed photo path");
        }
        UUID userId;
        try {
            userId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid managed photo path", e);
        }
        jdbi.useHandle(handle -> handle.createUpdate(
                        "DELETE FROM photo_blobs WHERE user_id = :userId AND file_name = :fileName")
                .bind(USER_ID, userId)
                .bind(FILE_NAME, parts[1])
                .execute());
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        jdbi.useHandle(handle -> handle.createUpdate("DELETE FROM photo_blobs WHERE user_id = :userId")
                .bind(USER_ID, userId)
                .execute());
    }

    @Override
    public boolean exists(UUID userId, String fileName) {
        return PhotoSanitizer.isSafeFileName(fileName) && countRows(userId, fileName) > 0;
    }

    private int countRows(UUID userId, String fileName) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM photo_blobs WHERE user_id = :userId AND file_name = :fileName")
                .bind(USER_ID, userId)
                .bind(FILE_NAME, fileName)
                .mapTo(Integer.class)
                .one());
    }
}
