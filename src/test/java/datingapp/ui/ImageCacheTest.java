package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ImageCache default avatar handling")
class ImageCacheTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("default avatar resource is packaged and loadable")
    void defaultAvatarResourceIsPackagedAndLoadable() {
        try (InputStream stream = ImageCache.class.getResourceAsStream(UiConstants.DEFAULT_AVATAR_PATH)) {
            assertNotNull(stream, "Default avatar resource should exist on the classpath");
            Image image = new Image(stream);
            assertTrue(image.getWidth() > 0, "Default avatar should decode to a non-empty image");
        } catch (Exception e) {
            throw new AssertionError("Failed to load packaged default avatar", e);
        }
    }

    @Test
    @DisplayName("missing image paths fall back to a usable avatar image")
    void missingImagePathsFallBackToUsableAvatarImage() {
        Image image = ImageCache.getImage("missing://avatar", 96, 96);
        assertNotNull(image);
        assertTrue(image.getWidth() > 0, "Fallback avatar should be renderable");
    }
}
