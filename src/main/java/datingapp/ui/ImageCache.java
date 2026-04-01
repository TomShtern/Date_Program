package datingapp.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe LRU image cache for avatars and profile photos.
 *
 * <p>Uses LinkedHashMap with access-order for true LRU eviction.
 * Cache keys are composed of path and requested size for efficient lookups.
 */
public final class ImageCache {

    private static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

    /** Maximum number of images to cache before eviction. */
    private static final int MAX_CACHE_SIZE = UiConstants.IMAGE_CACHE_MAX_SIZE;

    /** Maximum number of queued preload requests waiting behind the worker. */
    private static final int PRELOAD_QUEUE_CAPACITY = Math.max(16, MAX_CACHE_SIZE / 2);

    /** Path to default avatar resource. */
    private static final String DEFAULT_AVATAR_PATH = UiConstants.DEFAULT_AVATAR_PATH;

    /**
     * Single bounded preload worker that prevents repeated calls from creating
     * an unbounded number of blocked threads.
     */
    private static final ExecutorService PRELOAD_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(PRELOAD_QUEUE_CAPACITY),
            new PreloadThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    /** Tracks in-flight preload requests so repeated calls for the same image stay cheap. */
    private static final Set<String> IN_FLIGHT_PRELOADS = ConcurrentHashMap.newKeySet();

    private static final Map<String, CompletableFuture<Image>> IN_FLIGHT_LOADS = new ConcurrentHashMap<>();

    /**
     * Thread-safe LRU cache using LinkedHashMap with access-order.
     * Access-order (true) means get() operations move entries to the end,
     * so oldest/least-recently-used entries are at the beginning.
     */
    private static final Map<String, Image> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            boolean shouldRemove = size() > MAX_CACHE_SIZE;
            if (shouldRemove) {
                logDebug("LRU evicted cached image: {}", eldest.getKey());
            }
            return shouldRemove;
        }
    });

    private ImageCache() {
        // Utility class - no instantiation
    }

    /**
     * Gets a square avatar image from cache or loads it.
     *
     * @param path image path (URL or file path)
     * @param size width and height in pixels
     * @return cached or newly loaded image, or default avatar on error
     */
    public static Image getAvatar(String path, double size) {
        return getImage(path, size, size);
    }

    /**
     * Gets an image from cache or loads it with specified dimensions.
     *
     * @param path   image path (URL, file:// URI, or resource path)
     * @param width  requested width in pixels
     * @param height requested height in pixels
     * @return cached or newly loaded image, or default avatar on error
     */
    public static Image getImage(String path, double width, double height) {
        if (path == null || path.isBlank()) {
            return getDefaultAvatar(width, height);
        }

        String key = cacheKey(path, width, height);

        return getOrLoadCachedImage(key, () -> loadImage(path, width, height));
    }

    /** Loads an image with error handling. */
    private static Image loadImage(String path, double width, double height) {
        try {
            // Synchronous loading to ensure isError() reflects final load status
            Image image = new Image(path, width, height, true, true, false);

            // Check for load errors
            if (image.isError()) {
                logWarn("Failed to load image: {}", path, image.getException());
                return getDefaultAvatar(width, height);
            }

            return image;
        } catch (Exception e) {
            logWarn("Exception loading image: {}", path, e);
            return getDefaultAvatar(width, height);
        }
    }

    /** Returns the default avatar placeholder image. */
    private static Image getDefaultAvatar(double width, double height) {
        String key = cacheKey("default", width, height);
        return getOrLoadCachedImage(key, () -> loadDefaultAvatarImage(width, height));
    }

    private static Image getOrLoadCachedImage(String key, java.util.function.Supplier<Image> loader) {
        synchronized (CACHE) {
            Image cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }

        CompletableFuture<Image> newLoad = new CompletableFuture<>();
        CompletableFuture<Image> inFlight = IN_FLIGHT_LOADS.putIfAbsent(key, newLoad);
        if (inFlight != null) {
            return inFlight.join();
        }

        try {
            Image loaded = loader.get();
            synchronized (CACHE) {
                Image cached = CACHE.get(key);
                if (cached != null) {
                    newLoad.complete(cached);
                    return cached;
                }
                CACHE.put(key, loaded);
            }
            newLoad.complete(loaded);
            return loaded;
        } catch (RuntimeException exception) {
            newLoad.completeExceptionally(exception);
            throw exception;
        } finally {
            IN_FLIGHT_LOADS.remove(key, newLoad);
        }
    }

    /** Loads the default avatar image from resources or creates a placeholder. */
    private static Image loadDefaultAvatarImage(double width, double height) {
        try {
            var resource = ImageCache.class.getResource(DEFAULT_AVATAR_PATH);
            if (resource != null) {
                Image rawImage = new Image(resource.toExternalForm(), false);
                if (!rawImage.isError() && rawImage.getWidth() > 1 && rawImage.getHeight() > 1) {
                    return new Image(resource.toExternalForm(), width, height, true, true, false);
                }
                logWarn(
                        "Default avatar resource is too small to be visible ({}x{}); using generated placeholder",
                        rawImage.getWidth(),
                        rawImage.getHeight());
            }
        } catch (Exception e) {
            logWarn("Failed to load default avatar", e);
        }

        return createPlaceholder(width, height);
    }

    /** Creates a simple placeholder image when default avatar is unavailable. */
    private static Image createPlaceholder(double width, double height) {
        int imageWidth = Math.max(64, (int) Math.ceil(width));
        int imageHeight = Math.max(64, (int) Math.ceil(height));
        WritableImage image = new WritableImage(imageWidth, imageHeight);
        PixelWriter pixelWriter = image.getPixelWriter();

        Color topColor = Color.web("#334155");
        Color bottomColor = Color.web("#1e293b");
        Color silhouetteColor = Color.web("#cbd5e1");

        for (int y = 0; y < imageHeight; y++) {
            double blend = imageHeight == 1 ? 0 : (double) y / (imageHeight - 1);
            Color rowColor = topColor.interpolate(bottomColor, blend);
            for (int x = 0; x < imageWidth; x++) {
                pixelWriter.setColor(x, y, rowColor);
            }
        }

        double centerX = imageWidth / 2.0;
        double centerY = imageHeight / 2.0;
        double headRadius = Math.min(imageWidth, imageHeight) * 0.18;
        double shoulderRadiusX = Math.min(imageWidth, imageHeight) * 0.30;
        double shoulderRadiusY = Math.min(imageWidth, imageHeight) * 0.22;
        double shoulderCenterY = centerY + imageHeight * 0.18;

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                double dxHead = x - centerX;
                double dyHead = y - (centerY - imageHeight * 0.14);
                boolean inHead = (dxHead * dxHead) + (dyHead * dyHead) <= headRadius * headRadius;

                double dxShoulders = (x - centerX) / shoulderRadiusX;
                double dyShoulders = (y - shoulderCenterY) / shoulderRadiusY;
                boolean inShoulders = (dxShoulders * dxShoulders) + (dyShoulders * dyShoulders) <= 1.0;

                if (inHead || inShoulders) {
                    pixelWriter.setColor(x, y, silhouetteColor);
                }
            }
        }

        return image;
    }

    /**
     * Clears the entire image cache.
     * Useful when user logs out or memory pressure is detected.
     */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        logInfo("Image cache cleared");
    }

    /**
     * Returns the current cache size.
     *
     * @return number of cached images
     */
    public static int getCacheSize() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    /**
     * Pre-loads an image into the cache asynchronously.
     * Useful for pre-caching profile photos before they're displayed.
     *
     * @param path   image path
     * @param width  requested width
     * @param height requested height
     */
    public static void preload(String path, double width, double height) {
        if (path == null || path.isBlank()) {
            return;
        }

        String key = cacheKey(path, width, height);
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) {
                return;
            }
        }

        if (!IN_FLIGHT_PRELOADS.add(key)) {
            return;
        }

        try {
            PRELOAD_EXECUTOR.execute(() -> {
                try {
                    getImage(path, width, height);
                    logDebug("Preloaded image: {}", path);
                } finally {
                    IN_FLIGHT_PRELOADS.remove(key);
                }
            });
        } catch (RejectedExecutionException _) {
            IN_FLIGHT_PRELOADS.remove(key);
            logDebug("Dropped preload request for {} because the queue is full", path);
        }
    }

    private static String cacheKey(String path, double width, double height) {
        return path + "@" + width + "x" + height;
    }

    private static final class PreloadThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread =
                    Thread.ofPlatform().name("image-preload-worker", 0).daemon().unstarted(runnable);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }

    private static void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private static void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    private static void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }
}
