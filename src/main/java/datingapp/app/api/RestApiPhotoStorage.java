package datingapp.app.api;

import datingapp.core.AppConfig;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class RestApiPhotoStorage {
    private static final String PHOTOS_ROUTE_PREFIX = "/photos/";
    private static final int COPY_BUFFER_BYTES = 8 * 1024;
    private static final Pattern SAFE_MANAGED_FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private final Path storageRoot;
    private final String configuredPublicBaseUrl;
    private final long maxPhotoUploadBytes;

    RestApiPhotoStorage(AppConfig config) {
        this.storageRoot =
                Path.of(config.media().photoStorageRoot()).toAbsolutePath().normalize();
        this.configuredPublicBaseUrl = normalizeBaseUrl(config.media().photoPublicBaseUrl());
        this.maxPhotoUploadBytes = config.media().maxPhotoUploadBytes();
    }

    ManagedPhoto storePhoto(UUID userId, UploadedFile uploadedFile) throws IOException {
        if (uploadedFile == null) {
            throw new IllegalArgumentException("Missing multipart file field 'photo'");
        }
        String contentType = normalizeContentType(uploadedFile.contentType());
        String extension = extensionForContentType(contentType);
        byte[] bytes;
        try (InputStream content = uploadedFile.content()) {
            bytes = readLimited(content);
        }
        String photoId = UUID.randomUUID().toString();
        String fileName = photoId + "." + extension;
        Path userDirectory = userDirectory(userId);
        Files.createDirectories(userDirectory);
        Files.write(userDirectory.resolve(fileName), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new ManagedPhoto(photoId, storedPath(userId, fileName));
    }

    Optional<PhotoContent> loadPhoto(UUID userId, String fileName) throws IOException {
        if (!isSafeManagedFileName(fileName)) {
            return Optional.empty();
        }
        Path photoPath = resolveManagedPath(userId, fileName);
        if (!Files.isRegularFile(photoPath)) {
            return Optional.empty();
        }
        return Optional.of(new PhotoContent(Files.readAllBytes(photoPath), contentTypeFromFileName(fileName)));
    }

    void deleteManagedPhoto(String storedPath) throws IOException {
        Optional<Path> managedPath = managedPathFromStoredPath(storedPath);
        if (managedPath.isEmpty()) {
            return;
        }
        Files.deleteIfExists(managedPath.get());
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

    private Path userDirectory(UUID userId) {
        return storageRoot.resolve(userId.toString()).normalize();
    }

    private Path resolveManagedPath(UUID userId, String fileName) {
        Path baseDirectory = userDirectory(userId);
        Path candidate = baseDirectory.resolve(fileName).normalize();
        if (!candidate.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid photo path");
        }
        return candidate;
    }

    private static boolean isSafeManagedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.indexOf('\0') >= 0) {
            return false;
        }
        return SAFE_MANAGED_FILE_NAME.matcher(fileName).matches();
    }

    private Optional<Path> managedPathFromStoredPath(String storedPath) {
        if (!isManagedPhotoPath(storedPath)) {
            return Optional.empty();
        }
        String normalized = normalizeManagedPhotoPath(storedPath);
        String relativePath = normalized.substring(PHOTOS_ROUTE_PREFIX.length());
        Path candidate = storageRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid managed photo path");
        }
        return Optional.of(candidate);
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

    private static String storedPath(UUID userId, String fileName) {
        return PHOTOS_ROUTE_PREFIX + userId + "/" + fileName;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Photo content type is required");
        }
        return contentType.toLowerCase(Locale.ROOT).trim();
    }

    private static String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException("Unsupported photo content type: " + contentType);
        };
    }

    private static String contentTypeFromFileName(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCaseName.endsWith(".jpg") || lowerCaseName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
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
