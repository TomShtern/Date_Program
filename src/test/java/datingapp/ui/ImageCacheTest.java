package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ImageCache default avatar handling")
class ImageCacheTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO6qK9kAAAAASUVORK5CYII=");

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @BeforeEach
    void clearCache() {
        ImageCache.clearCache();
    }

    @AfterEach
    void clearCacheAfterTest() {
        ImageCache.clearCache();
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

    @Test
    @DisplayName("repeated preload calls stay bounded and still populate the cache")
    void repeatedPreloadCallsStayBoundedAndStillPopulateTheCache() throws Exception {
        try (BlockingImageServer server = new BlockingImageServer()) {
            String imageUrl = server.imageUrl();

            for (int i = 0; i < 25; i++) {
                ImageCache.preload(imageUrl, 48, 48);
            }

            assertTrue(server.awaitRequestStarted(), "Preload should reach the blocking image server");
            assertEquals(1, countPreloadWorkerThreads(), "Preload work should stay on one bounded worker thread");

            server.releaseResponse();
            assertTrue(server.awaitRequestFinished(), "Blocking preload should complete once released");

            Image first = ImageCache.getImage(imageUrl, 48, 48);
            Image second = ImageCache.getImage(imageUrl, 48, 48);

            assertNotNull(first);
            assertSame(first, second, "Cached image lookups should return the same instance");
            assertEquals(1, server.getRequestCount(), "Preload should only fetch the image once");
        }
    }

    @Test
    @DisplayName("slow image loading does not block unrelated fallback avatar reads")
    void slowImageLoadingDoesNotBlockUnrelatedFallbackAvatarReads() throws Exception {
        try (BlockingImageServer server = new BlockingImageServer()) {
            FutureTask<Image> slowLoad = new FutureTask<>(() -> ImageCache.getImage(server.imageUrl(), 64, 64));
            Thread loader = Thread.ofPlatform().start(slowLoad);

            assertTrue(server.awaitRequestStarted(), "slow image load should reach the blocking image server");

            FutureTask<Image> fallbackLoad = new FutureTask<>(() -> ImageCache.getImage("missing://avatar", 64, 64));
            Thread.ofPlatform().start(fallbackLoad);

            Image fallback = fallbackLoad.get(1, TimeUnit.SECONDS);
            assertNotNull(fallback);
            assertTrue(fallback.getWidth() > 0, "fallback avatar should not wait on unrelated slow image loads");

            server.releaseResponse();
            Image loaded = slowLoad.get(5, TimeUnit.SECONDS);
            assertNotNull(loaded);
            loader.join();
        }
    }

    private static int countPreloadWorkerThreads() {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("image-preload-worker"))
                .count();
    }

    private static final class BlockingImageServer implements AutoCloseable {

        private final HttpServer server;
        private final CountDownLatch requestStarted = new CountDownLatch(1);
        private final CountDownLatch requestFinished = new CountDownLatch(1);
        private final CountDownLatch releaseResponse = new CountDownLatch(1);
        private final AtomicInteger requestCount = new AtomicInteger();
        private final URI imageUri;

        private BlockingImageServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/image.png", new BlockingImageHandler());
            server.start();
            imageUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image.png");
        }

        private String imageUrl() {
            return imageUri.toString();
        }

        private boolean awaitRequestStarted() throws InterruptedException {
            return requestStarted.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitRequestFinished() throws InterruptedException {
            return requestFinished.await(5, TimeUnit.SECONDS);
        }

        private void releaseResponse() {
            releaseResponse.countDown();
        }

        private int getRequestCount() {
            return requestCount.get();
        }

        @Override
        public void close() {
            releaseResponse.countDown();
            server.stop(0);
        }

        private final class BlockingImageHandler implements HttpHandler {

            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCount.incrementAndGet();
                requestStarted.countDown();

                try {
                    releaseResponse.await(5, TimeUnit.SECONDS);
                    exchange.getResponseHeaders().add("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, PNG_1X1.length);
                    try (OutputStream outputStream = exchange.getResponseBody()) {
                        outputStream.write(PNG_1X1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while serving test image", e);
                } finally {
                    requestFinished.countDown();
                    exchange.close();
                }
            }
        }
    }
}
