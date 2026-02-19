package datingapp.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.image.Image;
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

    /** Path to default avatar resource. */
    private static final String DEFAULT_AVATAR_PATH = UiConstants.DEFAULT_AVATAR_PATH;

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

        String key = path + "@" + width + "x" + height;

        // Synchronized access for thread safety with LinkedHashMap
        synchronized (CACHE) {
            Image image = CACHE.get(key);
            if (image != null) {
                return image;
            }
            image = loadImage(path, width, height);
            CACHE.put(key, image);
            // LRU eviction handled automatically by removeEldestEntry
            return image;
        }
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
        String key = "default@" + width + "x" + height;

        synchronized (CACHE) {
            Image cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            Image avatar = loadDefaultAvatarImage(width, height);
            CACHE.put(key, avatar);
            return avatar;
        }
    }

    /** Loads the default avatar image from resources or creates a placeholder. */
    private static Image loadDefaultAvatarImage(double width, double height) {
        try {
            try (var stream = ImageCache.class.getResourceAsStream(DEFAULT_AVATAR_PATH)) {
                if (stream != null) {
                    return new Image(stream, width, height, true, true);
                }
            }
        } catch (Exception e) {
            logWarn("Failed to load default avatar", e);
        }
        // Fallback: create a simple colored placeholder
        return createPlaceholder(width, height);
    }

    /** Creates a simple placeholder image when default avatar is unavailable. */
    private static Image createPlaceholder(double width, double height) {
        // Return a 1x1 transparent image as last resort
        String base64Png = "data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQI12NgAAIAAAUAAeImBZsAAAAASUVORK5CYII=";
        return new Image(base64Png, width, height, true, true);
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

        // getImage() already handles cache-check atomically in its synchronized block,
        // so there is no need to pre-check containsKey outside the lock.
        Thread.ofVirtual().name("image-preload").start(() -> {
            getImage(path, width, height);
            logDebug("Preloaded image: {}", path);
        });
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
