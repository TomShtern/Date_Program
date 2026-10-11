package datingapp.core.media;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Upload validation and metadata stripping shared by every {@link PhotoStore} implementation. */
public final class PhotoSanitizer {

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final long MAX_PIXELS = 40_000_000L;
    private static final String UNDECODABLE_PHOTO = "Photo could not be decoded";
    private static final String PNG = "png";

    private PhotoSanitizer() {}

    /**
     * Validates an upload and returns the bytes that are safe to store: size and type checked,
     * dimensions bounded, and re-encoded so EXIF is gone.
     */
    public static byte[] sanitize(String contentType, byte[] bytes, long maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Photo upload cannot be empty");
        }
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Photo exceeds maxPhotoUploadBytes");
        }
        String extension = extensionFor(contentType);
        requireMagicBytes(bytes, extension);
        requireSafeDimensions(bytes);
        return stripMetadata(bytes, extension);
    }

    /** File extension a supported content type is stored under. */
    public static String extensionFor(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Photo content type is required");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> PNG;
            default -> throw new IllegalArgumentException("Unsupported photo content type: " + normalized);
        };
    }

    public static String contentTypeFromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    public static boolean isSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.indexOf('\0') >= 0) {
            return false;
        }
        return SAFE_FILE_NAME.matcher(fileName).matches();
    }

    private static void requireMagicBytes(byte[] bytes, String extension) {
        boolean ok;
        if (PNG.equals(extension)) {
            ok = bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47;
        } else {
            ok = bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
        }
        if (!ok) {
            throw new IllegalArgumentException("Photo bytes do not match content type");
        }
    }

    /**
     * Reads the declared size from the image header and rejects oversized images before any pixel
     * buffer is allocated. The byte limit only bounds the compressed size; a small file can still
     * declare dimensions that decode to gigabytes.
     */
    private static void requireSafeDimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? Collections.emptyIterator() : ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(UNDECODABLE_PHOTO);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException("Photo dimensions are too large");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(UNDECODABLE_PHOTO, e);
        }
    }

    /**
     * Re-encodes the image so EXIF (including GPS) is dropped. An image that cannot be re-encoded is
     * rejected rather than stored as uploaded, because its metadata would be served to other users.
     */
    private static byte[] stripMetadata(byte[] bytes, String extension) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException(UNDECODABLE_PHOTO);
            }
            BufferedImage clean = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    PNG.equals(extension) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = clean.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
            ImageIO.write(clean, PNG.equals(extension) ? PNG : "jpg", out);
            byte[] rewritten = out.toByteArray();
            if (rewritten.length == 0) {
                throw new IllegalArgumentException(UNDECODABLE_PHOTO);
            }
            return rewritten;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException(UNDECODABLE_PHOTO, e);
        }
    }
}
