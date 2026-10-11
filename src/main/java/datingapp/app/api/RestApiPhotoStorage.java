package datingapp.app.api;

import datingapp.core.AppConfig;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RestApiPhotoStorage {
    private static final String PHOTOS_ROUTE_PREFIX = "/photos/";
    private static final int COPY_BUFFER_BYTES = 8 * 1024;

    private final datingapp.core.media.PhotoStore delegate;
    private final String configuredPublicBaseUrl;
    private final long maxPhotoUploadBytes;

    RestApiPhotoStorage(AppConfig config, datingapp.core.media.PhotoStore photoStore) {
        this.delegate = photoStore != null
                ? photoStore
                : new datingapp.core.media.LocalDiskPhotoStore(
                        Path.of(config.media().photoStorageRoot()),
                        config.media().maxPhotoUploadBytes());
        this.configuredPublicBaseUrl = normalizeBaseUrl(config.media().photoPublicBaseUrl());
        this.maxPhotoUploadBytes = config.media().maxPhotoUploadBytes();
    }

    ManagedPhoto storePhoto(UUID userId, UploadedFile uploadedFile) throws IOException {
        if (uploadedFile == null) {
            throw new IllegalArgumentException("Missing multipart file field 'photo'");
        }
        byte[] bytes;
        try (InputStream content = uploadedFile.content()) {
            bytes = readLimited(content);
        }
        datingapp.core.media.PhotoStore.ManagedPhoto stored = delegate.store(userId, uploadedFile.contentType(), bytes);
        return new ManagedPhoto(stored.id(), stored.storedPath());
    }

    Optional<PhotoContent> loadPhoto(UUID userId, String fileName) throws IOException {
        Optional<datingapp.core.media.PhotoStore.PhotoContent> content = delegate.load(userId, fileName);
        return content.map(c -> new PhotoContent(c.bytes(), c.contentType()));
    }

    void deleteManagedPhoto(String storedPath) throws IOException {
        delegate.delete(storedPath);
    }

    void deleteAllForUser(UUID userId) throws IOException {
        delegate.deleteAllForUser(userId);
    }

    String toPublicUrl(Context ctx, String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return storedPath;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            return storedPath;
        }
        String normalizedPath = storedPath.startsWith("/") ? storedPath : "/" + storedPath;
        return publicBaseUrl(ctx) + normalizedPath;
    }

    List<String> toPublicUrls(Context ctx, List<String> storedPaths) {
        return storedPaths == null
                ? List.of()
                : storedPaths.stream().map(path -> toPublicUrl(ctx, path)).toList();
    }

    String primaryPublicUrl(Context ctx, List<String> storedPaths) {
        return storedPaths == null
                ? null
                : storedPaths.stream()
                        .filter(path -> path != null && !path.isBlank())
                        .findFirst()
                        .map(path -> toPublicUrl(ctx, path))
                        .orElse(null);
    }

    Optional<String> photoIdFromStoredPath(String storedPath) {
        if (!isManagedPhotoPath(storedPath)) {
            return Optional.empty();
        }
        String normalized = normalizeManagedPhotoPath(storedPath);
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == normalized.length() - 1) {
            return Optional.empty();
        }
        String fileName = normalized.substring(slashIndex + 1);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(0, dotIndex));
    }

    String deriveStablePhotoId(String storedPath) {
        if (isManagedPhotoPath(storedPath)) {
            return photoIdFromStoredPath(storedPath)
                    .orElseThrow(() -> new IllegalArgumentException("Malformed managed photo path: " + storedPath));
        }
        return UUID.nameUUIDFromBytes(storedPath.getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    LinkedHashMap<String, String> photoIdsByStoredPath(List<String> storedPaths) {
        LinkedHashMap<String, String> photoIds = new LinkedHashMap<>();
        for (String storedPath : storedPaths) {
            String photoId = deriveStablePhotoId(storedPath);
            if (photoIds.put(photoId, storedPath) != null) {
                throw new IllegalArgumentException("Duplicate photo id: " + photoId);
            }
        }
        return photoIds;
    }

    private byte[] readLimited(InputStream content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long totalBytes = 0;
        int bytesRead;
        while ((bytesRead = content.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > maxPhotoUploadBytes) {
                throw new IllegalArgumentException("Photo exceeds maxPhotoUploadBytes");
            }
            output.write(buffer, 0, bytesRead);
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Photo upload cannot be empty");
        }
        return bytes;
    }

    private String publicBaseUrl(Context ctx) {
        if (configuredPublicBaseUrl != null) {
            return configuredPublicBaseUrl;
        }
        String scheme = Optional.ofNullable(ctx.req().getScheme())
                .filter(value -> !value.isBlank())
                .orElse("http");
        String hostHeader = ctx.header("Host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            return scheme + "://" + hostHeader;
        }
        int port = ctx.req().getServerPort();
        boolean defaultPort =
                ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + ctx.req().getServerName() + (defaultPort ? "" : ":" + port);
    }

    private static boolean isManagedPhotoPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return false;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            int pathStart = storedPath.indexOf('/', storedPath.indexOf("://") + 3);
            return pathStart >= 0 && storedPath.substring(pathStart).startsWith(PHOTOS_ROUTE_PREFIX);
        }
        return normalizeManagedPhotoPath(storedPath).startsWith(PHOTOS_ROUTE_PREFIX);
    }

    private static String normalizeManagedPhotoPath(String storedPath) {
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            int pathStart = storedPath.indexOf('/', storedPath.indexOf("://") + 3);
            return pathStart >= 0 ? storedPath.substring(pathStart) : storedPath;
        }
        return storedPath.startsWith("/") ? storedPath : "/" + storedPath;
    }

    private static String normalizeBaseUrl(String photoPublicBaseUrl) {
        if (photoPublicBaseUrl == null || photoPublicBaseUrl.isBlank()) {
            return null;
        }
        return photoPublicBaseUrl.endsWith("/")
                ? photoPublicBaseUrl.substring(0, photoPublicBaseUrl.length() - 1)
                : photoPublicBaseUrl;
    }

    record ManagedPhoto(String id, String storedPath) {}

    record PhotoContent(byte[] bytes, String contentType) {}
}
