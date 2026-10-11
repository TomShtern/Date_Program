package datingapp.core.media;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Local-disk {@link PhotoStore}. Layout: {@code <root>/<userId>/<photoId>.<ext>}. */
public final class LocalDiskPhotoStore implements PhotoStore {

    public static final String PHOTOS_ROUTE_PREFIX = "/photos/";
    private static final int THUMB_PX = 200;

    private final Path storageRoot;
    private final long maxBytes;

    public LocalDiskPhotoStore(Path storageRoot, long maxBytes) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot cannot be null")
                .toAbsolutePath()
                .normalize();
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public ManagedPhoto store(UUID userId, String contentType, byte[] bytes) throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        byte[] sanitized = PhotoSanitizer.sanitize(contentType, bytes, maxBytes);
        String extension = PhotoSanitizer.extensionFor(contentType);
        String photoId = UUID.randomUUID().toString();
        String fileName = photoId + "." + extension;
        Path dir = storageRoot.resolve(userId.toString()).normalize();
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), sanitized, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        writeThumbnailBestEffort(dir, photoId, extension, sanitized);
        return new ManagedPhoto(photoId, PHOTOS_ROUTE_PREFIX + userId + "/" + fileName);
    }

    @Override
    public Optional<PhotoContent> load(UUID userId, String fileName) throws IOException {
        if (!PhotoSanitizer.isSafeFileName(fileName)) {
            return Optional.empty();
        }
        Path base = storageRoot.resolve(userId.toString()).normalize();
        Path candidate = base.resolve(fileName).normalize();
        if (!candidate.startsWith(base) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(
                new PhotoContent(Files.readAllBytes(candidate), PhotoSanitizer.contentTypeFromFileName(fileName)));
    }

    @Override
    public void delete(String storedPath) throws IOException {
        managedPath(storedPath).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
                deleteThumbnailSibling(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void deleteAllForUser(UUID userId) throws IOException {
        Path dir = storageRoot.resolve(userId.toString()).normalize();
        if (!dir.startsWith(storageRoot) || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    if (!path.equals(dir)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                    // best-effort GDPR cleanup; individual failures must not block account deletion
                }
            });
        }
        Files.deleteIfExists(dir);
    }

    @Override
    public boolean exists(UUID userId, String fileName) {
        try {
            if (!PhotoSanitizer.isSafeFileName(fileName)) {
                return false;
            }
            Path base = storageRoot.resolve(userId.toString()).normalize();
            Path candidate = base.resolve(fileName).normalize();
            return candidate.startsWith(base) && Files.isRegularFile(candidate);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Optional<Path> managedPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }
        String normalized = storedPath.startsWith("http://") || storedPath.startsWith("https://")
                ? storedPath.substring(storedPath.indexOf('/', storedPath.indexOf("://") + 3))
                : storedPath.startsWith("/") ? storedPath : "/" + storedPath;
        if (!normalized.startsWith(PHOTOS_ROUTE_PREFIX)) {
            return Optional.empty();
        }
        Path candidate = storageRoot
                .resolve(normalized.substring(PHOTOS_ROUTE_PREFIX.length()))
                .normalize();
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid managed photo path");
        }
        return Optional.of(candidate);
    }

    private static void writeThumbnailBestEffort(Path dir, String photoId, String extension, byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double scale = Math.min(1.0, THUMB_PX / (double) Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));
            Image scaled = image.getScaledInstance(tw, th, Image.SCALE_SMOOTH);
            BufferedImage thumb = new BufferedImage(
                    tw, th, "png".equals(extension) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            try {
                g.drawImage(scaled, 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "png".equals(extension) ? "png" : "jpg", out);
            if (out.size() > 0) {
                Files.write(
                        dir.resolve(photoId + "_thumb." + extension),
                        out.toByteArray(),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
        } catch (IOException | RuntimeException ignored) {
            // thumbnails are best-effort; never fail uploads
        }
    }

    private static void deleteThumbnailSibling(Path original) {
        Path fileName = original.getFileName();
        if (fileName == null) {
            return;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        Path thumb = original.resolveSibling(name.substring(0, dot) + "_thumb" + name.substring(dot));
        try {
            Files.deleteIfExists(thumb);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
