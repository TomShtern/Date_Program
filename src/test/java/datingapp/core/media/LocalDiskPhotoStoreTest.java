package datingapp.core.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LocalDiskPhotoStore")
class LocalDiskPhotoStoreTest {

    private static final String EXIF_MARKER_TEXT = "GPS-LAT-32.0853-SECRET";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("store re-encodes a JPEG so its EXIF segment is not written to disk")
    void storeStripsExifFromJpeg() throws Exception {
        LocalDiskPhotoStore store = new LocalDiskPhotoStore(tempDir, 1024 * 1024);
        UUID userId = UUID.randomUUID();
        byte[] withExif = jpegWithExifSegment();
        assertTrue(contains(withExif, EXIF_MARKER_TEXT), "fixture must carry the EXIF text");

        PhotoStore.ManagedPhoto stored = store.store(userId, "image/jpeg", withExif);

        String fileName = stored.storedPath().substring(stored.storedPath().lastIndexOf('/') + 1);
        byte[] onDisk = store.load(userId, fileName).orElseThrow().bytes();
        assertFalse(contains(onDisk, EXIF_MARKER_TEXT), "EXIF text must not survive the upload");
        assertFalse(contains(onDisk, "Exif"), "no EXIF segment may remain");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(onDisk));
        assertNotNull(decoded, "the stored file must still be a readable image");
        assertEquals(8, decoded.getWidth());
        assertTrue(Files.isRegularFile(tempDir.resolve(userId.toString()).resolve(stored.id() + "_thumb.jpg")));
    }

    @Test
    @DisplayName("store rejects bytes that do not match the declared content type")
    void storeRejectsMismatchedMagicBytes() throws Exception {
        LocalDiskPhotoStore store = new LocalDiskPhotoStore(tempDir, 1024 * 1024);
        byte[] jpeg = jpegWithExifSegment();

        assertThrows(IllegalArgumentException.class, () -> store.store(UUID.randomUUID(), "image/png", jpeg));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.store(UUID.randomUUID(), "image/jpeg", "<html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("store rejects an image whose declared dimensions are too large, before decoding it")
    void storeRejectsOversizedDimensions() throws Exception {
        LocalDiskPhotoStore store = new LocalDiskPhotoStore(tempDir, 1024 * 1024);
        UUID userId = UUID.randomUUID();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> store.store(userId, "image/png", pngHeaderDeclaring(30_000, 30_000)));

        assertEquals("Photo dimensions are too large", error.getMessage());
        assertFalse(Files.exists(tempDir.resolve(userId.toString())));
    }

    @Test
    @DisplayName("store rejects an image it cannot re-encode instead of keeping the original bytes")
    void storeRejectsUndecodableImage() {
        LocalDiskPhotoStore store = new LocalDiskPhotoStore(tempDir, 1024 * 1024);
        UUID userId = UUID.randomUUID();
        byte[] jpegMagicThenJunk = new byte[64];
        jpegMagicThenJunk[0] = (byte) 0xFF;
        jpegMagicThenJunk[1] = (byte) 0xD8;
        jpegMagicThenJunk[2] = (byte) 0xFF;

        assertThrows(IllegalArgumentException.class, () -> store.store(userId, "image/jpeg", jpegMagicThenJunk));
        assertFalse(Files.exists(tempDir.resolve(userId.toString())));
    }

    @Test
    @DisplayName("deleteAllForUser removes the photo, its thumbnail and the directory")
    void deleteAllForUserRemovesEverything() throws Exception {
        LocalDiskPhotoStore store = new LocalDiskPhotoStore(tempDir, 1024 * 1024);
        UUID userId = UUID.randomUUID();
        store.store(userId, "image/jpeg", jpegWithExifSegment());

        store.deleteAllForUser(userId);

        assertFalse(Files.exists(tempDir.resolve(userId.toString())));
    }

    /** A real JPEG with an APP1 "Exif" segment spliced in right after the start-of-image marker. */
    private static byte[] jpegWithExifSegment() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", plain);
        byte[] jpeg = plain.toByteArray();

        byte[] payload = ("Exif\0\0" + EXIF_MARKER_TEXT).getBytes(StandardCharsets.ISO_8859_1);
        int segmentLength = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** PNG signature plus an IHDR chunk only: enough for a reader to report the size, with no pixel data. */
    private static byte[] pngHeaderDeclaring(int width, int height) throws Exception {
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream ihdrData = new DataOutputStream(ihdr);
        ihdrData.writeBytes("IHDR");
        ihdrData.writeInt(width);
        ihdrData.writeInt(height);
        ihdrData.write(new byte[] {8, 2, 0, 0, 0});
        byte[] typeAndData = ihdr.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(typeAndData);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        data.writeInt(typeAndData.length - 4);
        data.write(typeAndData);
        data.writeInt((int) crc.getValue());
        return out.toByteArray();
    }

    private static boolean contains(byte[] bytes, String text) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(text);
    }
}
