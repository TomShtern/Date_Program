package datingapp.ui;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Manages desktop-local profile photo storage under the user's home directory.
 *
 * <p>Photos are copied into a managed application folder and referenced via {@code file://} URIs.
 * The store also owns lifecycle operations such as delete and primary-photo reordering.
 */
public final class LocalPhotoStore {

    public static final int MAX_PHOTOS = 6;
    private static final String MIME_TYPE_JPEG = "image/jpeg";
    private static final String MIME_TYPE_PNG = "image/png";
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(MIME_TYPE_JPEG, MIME_TYPE_PNG);

    private final Path photoDirectory;
    private final ImageInspector imageInspector;

    public LocalPhotoStore() {
        this(Path.of(System.getProperty("user.home"), ".datingapp", "photos"), new DefaultImageInspector());
    }

    public LocalPhotoStore(Path photoDirectory) {
        this(photoDirectory, new DefaultImageInspector());
    }

    LocalPhotoStore(Path photoDirectory, ImageInspector imageInspector) {
        this.photoDirectory = Objects.requireNonNull(photoDirectory, "photoDirectory cannot be null");
        this.imageInspector = Objects.requireNonNull(imageInspector, "imageInspector cannot be null");
    }

    public List<String> importPhoto(UUID userId, List<String> existingPhotoUrls, Path sourceFile) throws IOException {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("Selected photo file does not exist");
        }

        Files.createDirectories(photoDirectory);

        ValidatedImage validatedImage = imageInspector.inspect(sourceFile);
        Path destination = createManagedPhotoPath(userId, validatedImage.extension());
        writeManagedImage(sourceFile, destination, validatedImage);

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

        ValidatedImage validatedImage = imageInspector.inspect(sourceFile);
        Path destination = createManagedPhotoPath(userId, validatedImage.extension());
        writeManagedImage(sourceFile, destination, validatedImage);

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

    private void writeManagedImage(Path sourceFile, Path destination, ValidatedImage validatedImage)
            throws IOException {
        if (!validatedImage.requiresNormalization()) {
            Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        BufferedImage normalized = rotate(validatedImage.image(), validatedImage.rotationDegrees());
        if (!ImageIO.write(normalized, validatedImage.formatName(), destination.toFile())) {
            throw new IOException("Failed to write normalized image");
        }
    }

    private Path createManagedPhotoPath(UUID userId, String extension) {
        return photoDirectory.resolve(userId + "_" + UUID.randomUUID() + extension);
    }

    private static BufferedImage rotate(BufferedImage image, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        boolean swapDimensions = rotationDegrees == 90 || rotationDegrees == 270;
        BufferedImage rotated = new BufferedImage(
                swapDimensions ? height : width,
                swapDimensions ? width : height,
                image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType());

        Graphics2D graphics = rotated.createGraphics();
        try {
            AffineTransform transform = new AffineTransform();
            switch (rotationDegrees) {
                case 90 -> {
                    transform.translate(height, 0);
                    transform.rotate(Math.toRadians(90));
                }
                case 180 -> {
                    transform.translate(width, height);
                    transform.rotate(Math.toRadians(180));
                }
                case 270 -> {
                    transform.translate(0, width);
                    transform.rotate(Math.toRadians(270));
                }
                default -> throw new IllegalArgumentException("Unsupported rotation: " + rotationDegrees);
            }
            graphics.drawImage(image, transform, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    interface ImageInspector {
        ValidatedImage inspect(Path sourceFile) throws IOException;
    }

    record ValidatedImage(BufferedImage image, String extension, String formatName, int rotationDegrees) {
        boolean requiresNormalization() {
            return rotationDegrees != 0;
        }
    }

    private static final class DefaultImageInspector implements ImageInspector {
        @Override
        public ValidatedImage inspect(Path sourceFile) throws IOException {
            String mimeType = detectMimeType(sourceFile);
            if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
                throw new IOException("Only PNG and JPEG images are supported");
            }

            BufferedImage image = ImageIO.read(sourceFile.toFile());
            if (image == null) {
                throw new IOException("Selected file is not a readable image");
            }

            int rotationDegrees = readRotationDegrees(sourceFile, mimeType);
            String extension = MIME_TYPE_PNG.equals(mimeType) ? ".png" : ".jpg";
            String formatName = MIME_TYPE_PNG.equals(mimeType) ? "png" : "jpg";
            return new ValidatedImage(image, extension, formatName, rotationDegrees);
        }

        private static String detectMimeType(Path sourceFile) throws IOException {
            byte[] header;
            try (InputStream inputStream = Files.newInputStream(sourceFile)) {
                header = inputStream.readNBytes(12);
            }
            if (header.length >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A) {
                return MIME_TYPE_PNG;
            }
            if (header.length >= 3
                    && header[0] == (byte) 0xFF
                    && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF) {
                return MIME_TYPE_JPEG;
            }
            throw new IOException("Selected file is not a supported image type");
        }

        private static int readRotationDegrees(Path sourceFile, String mimeType) {
            if (!MIME_TYPE_JPEG.equals(mimeType)) {
                return 0;
            }
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(sourceFile.toFile());
                ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                if (directory == null || !directory.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
                    return 0;
                }
                return switch (directory.getInt(ExifDirectoryBase.TAG_ORIENTATION)) {
                    case 3 -> 180;
                    case 6 -> 90;
                    case 8 -> 270;
                    default -> 0;
                };
            } catch (Exception _) {
                return 0;
            }
        }
    }
}
