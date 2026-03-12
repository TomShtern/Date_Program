package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalPhotoStore")
class LocalPhotoStoreTest {

    @Test
    @DisplayName("importPhoto rejects non-image content even when a jpg extension is used")
    void importPhotoRejectsNonImageContent() throws Exception {
        Path directory = Files.createTempDirectory("datingapp-photo-store");
        LocalPhotoStore store = new LocalPhotoStore(directory);
        Path fakeJpeg = Files.createTempFile("fake-image", ".jpg");
        Files.writeString(fakeJpeg, "not-an-image");

        assertThrows(IOException.class, () -> store.importPhoto(UUID.randomUUID(), List.of(), fakeJpeg));
    }

    @Test
    @DisplayName("importPhoto accepts a valid png image")
    void importPhotoAcceptsValidPng() throws Exception {
        Path directory = Files.createTempDirectory("datingapp-photo-store");
        LocalPhotoStore store = new LocalPhotoStore(directory);
        Path png = createImageFile("valid", "png", 12, 8);

        List<String> urls = store.importPhoto(UUID.randomUUID(), List.of(), png);

        assertEquals(1, urls.size());
        assertTrue(Files.exists(Path.of(java.net.URI.create(urls.getFirst()))));
    }

    @Test
    @DisplayName("replacePhoto normalizes rotated images before storing them")
    void replacePhotoNormalizesRotation() throws Exception {
        Path directory = Files.createTempDirectory("datingapp-photo-store");
        LocalPhotoStore.ImageInspector inspector =
                file -> new LocalPhotoStore.ValidatedImage(ImageIO.read(file.toFile()), ".png", "png", 90);
        LocalPhotoStore store = new LocalPhotoStore(directory, inspector);
        UUID userId = UUID.randomUUID();
        Path original = createImageFile("original", "png", 20, 10);
        Path replacement = createImageFile("replacement", "png", 20, 10);

        List<String> urls = store.importPhoto(userId, List.of(), original);
        List<String> updated = store.replacePhoto(userId, urls, 0, replacement);

        Path stored = Path.of(java.net.URI.create(updated.getFirst()));
        BufferedImage rotated = ImageIO.read(stored.toFile());
        assertEquals(10, rotated.getWidth());
        assertEquals(20, rotated.getHeight());
    }

    private static Path createImageFile(String prefix, String format, int width, int height) throws IOException {
        Path file = Files.createTempFile(prefix, "." + format);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, format, file.toFile());
        return file;
    }
}
