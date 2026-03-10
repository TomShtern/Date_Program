package datingapp.ui;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages desktop-local profile photo storage under the user's home directory.
 *
 * <p>Photos are copied into a managed application folder and referenced via {@code file://} URIs.
 * The store also owns lifecycle operations such as delete and primary-photo reordering.
 */
public final class LocalPhotoStore {

    public static final int MAX_PHOTOS = 6;

    private final Path photoDirectory;

    public LocalPhotoStore() {
        this(Path.of(System.getProperty("user.home"), ".datingapp", "photos"));
    }

    public LocalPhotoStore(Path photoDirectory) {
        this.photoDirectory = Objects.requireNonNull(photoDirectory, "photoDirectory cannot be null");
    }

    public List<String> importPhoto(UUID userId, List<String> existingPhotoUrls, Path sourceFile) throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("Selected photo file does not exist");
        }

        Files.createDirectories(photoDirectory);

        Path destination = photoDirectory.resolve(userId + "_" + System.currentTimeMillis() + extensionOf(sourceFile));
        Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);

        List<String> updated = new ArrayList<>(normalizedPhotoUrls(existingPhotoUrls));
        if (updated.size() >= MAX_PHOTOS) {
            deleteManagedFileIfPresent(updated.removeLast());
        }
        updated.add(destination.toUri().toString());
        return List.copyOf(updated);
    }

    public List<String> replacePhoto(UUID userId, List<String> existingPhotoUrls, int index, Path sourceFile)
            throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("Selected photo file does not exist");
        }

        Files.createDirectories(photoDirectory);

        List<String> updated = new ArrayList<>(normalizedPhotoUrls(existingPhotoUrls));
        validatePhotoIndex(updated, index);

        Path destination = photoDirectory.resolve(userId + "_" + System.currentTimeMillis() + extensionOf(sourceFile));
        Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);

        String previousPhoto = updated.set(index, destination.toUri().toString());
        deleteManagedFileIfPresent(previousPhoto);
        return List.copyOf(updated);
    }

    public List<String> deletePhoto(List<String> existingPhotoUrls, int index) throws IOException {
        List<String> updated = new ArrayList<>(normalizedPhotoUrls(existingPhotoUrls));
        validatePhotoIndex(updated, index);

        String removedPhoto = updated.remove(index);
        deleteManagedFileIfPresent(removedPhoto);
        return List.copyOf(updated);
    }

    public List<String> setPrimaryPhoto(List<String> existingPhotoUrls, int index) {
        List<String> updated = new ArrayList<>(normalizedPhotoUrls(existingPhotoUrls));
        validatePhotoIndex(updated, index);
        if (index == 0) {
            return List.copyOf(updated);
        }

        String selected = updated.remove(index);
        updated.addFirst(selected);
        return List.copyOf(updated);
    }

    private List<String> normalizedPhotoUrls(List<String> existingPhotoUrls) {
        if (existingPhotoUrls == null || existingPhotoUrls.isEmpty()) {
            return List.of();
        }
        return existingPhotoUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .toList();
    }

    private void validatePhotoIndex(List<String> photoUrls, int index) {
        if (photoUrls.isEmpty()) {
            throw new IllegalArgumentException("No photos available");
        }
        if (index < 0 || index >= photoUrls.size()) {
            throw new IllegalArgumentException("Photo index out of range: " + index);
        }
    }

    private void deleteManagedFileIfPresent(String photoUrl) throws IOException {
        if (photoUrl == null || photoUrl.isBlank()) {
            return;
        }
        Path managedPath = toManagedPath(photoUrl);
        if (managedPath != null && Files.exists(managedPath)) {
            Files.delete(managedPath);
        }
    }

    private Path toManagedPath(String photoUrl) {
        try {
            URI uri = URI.create(photoUrl);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            Path path = Path.of(uri).normalize();
            Path normalizedRoot = photoDirectory.toAbsolutePath().normalize();
            return path.startsWith(normalizedRoot) ? path : null;
        } catch (Exception _) {
            return null;
        }
    }

    private static String extensionOf(Path sourceFile) {
        String fileName = sourceFile.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot) : ".jpg";
    }
}
