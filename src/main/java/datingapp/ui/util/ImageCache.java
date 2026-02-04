package datingapp.ui.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe LRU-style image cache for avatars and profile photos.
 *
 * <p>Uses ConcurrentHashMap for thread safety. Cache keys are composed of
 * path and requested size for efficient lookups.
 */
public final class ImageCache {

    private static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

    /** Maximum number of images to cache before eviction. */
    private static final int MAX_CACHE_SIZE = 100;

    /** Path to default avatar resource. */
    private static final String DEFAULT_AVATAR_PATH = "/images/default-avatar.png";

    /** Thread-safe cache storage. */
    private static final ConcurrentHashMap<String, Image> CACHE = new ConcurrentHashMap<>();

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

        String key = path + "@" + width + "x" + height;

        // computeIfAbsent is atomic with ConcurrentHashMap
        Image image = CACHE.computeIfAbsent(key, k -> {
            Objects.requireNonNull(k, "cacheKey");
            return loadImage(path, width, height);
        });

        // Simple eviction: remove oldest entry when over size
        if (CACHE.size() > MAX_CACHE_SIZE) {
            evictOldest();
        }

        return image;
    }

    /** Loads an image with error handling. */
    private static Image loadImage(String path, double width, double height) {
        try {
            // Background loading (true), smooth scaling (true), preserve ratio (true)
            Image image = new Image(path, width, height, true, true, true);

            // Check for load errors
            if (image.isError()) {
                logger.warn("Failed to load image: {}", path, image.getException());
                return getDefaultAvatar(width, height);
            }

            return image;
        } catch (Exception e) {
            logger.warn("Exception loading image: {}", path, e);
            return getDefaultAvatar(width, height);
        }
    }

    /** Returns the default avatar placeholder image. */
    private static Image getDefaultAvatar(double width, double height) {
        String key = "default@" + width + "x" + height;

        return CACHE.computeIfAbsent(key, k -> {
            Objects.requireNonNull(k, "cacheKey");
            try {
                try (var stream = ImageCache.class.getResourceAsStream(DEFAULT_AVATAR_PATH)) {
                    if (stream != null) {
                        return new Image(stream, width, height, true, true);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to load default avatar", e);
            }
            // Fallback: create a simple colored placeholder
            return createPlaceholder(width, height);
        });
    }

    /** Creates a simple placeholder image when default avatar is unavailable. */
    private static Image createPlaceholder(double width, double height) {
        // Return a 1x1 transparent image as last resort
        String base64Png = "data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIAAAUAAeImBZsAAAAASUVORK5CYII=";
        return new Image(base64Png, width, height, true, true);
    }

    /**
     * Evicts the oldest entry from the cache. Simple strategy - just remove first
     * entry found.
     */
    private static void evictOldest() {
        CACHE.keySet().stream().findFirst().ifPresent(key -> {
            CACHE.remove(key);
            logger.debug("Evicted cached image: {}", key);
        });
    }

    /**
     * Clears the entire image cache.
     * Useful when user logs out or memory pressure is detected.
     */
    public static void clearCache() {
        CACHE.clear();
        logger.info("Image cache cleared");
    }

    /**
     * Returns the current cache size.
     *
     * @return number of cached images
     */
    public static int getCacheSize() {
        return CACHE.size();
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

        String key = path + "@" + width + "x" + height;
        if (CACHE.containsKey(key)) {
            return; // Already cached
        }

        Thread.ofVirtual().name("image-preload").start(() -> {
            getImage(path, width, height);
            logger.debug("Preloaded image: {}", key);
        });
    }
}
