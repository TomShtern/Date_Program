package datingapp.ui.util;

import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidated UI service utilities for cross-cutting concerns.
 * Contains Toast notification service and Image caching utilities.
 *
 * <p>Access via nested classes:
 * <ul>
 *   <li>{@link Toast} - Singleton toast notification service</li>
 *   <li>{@link ImageCache} - Static image caching with LRU eviction</li>
 * </ul>
 */
public final class UiServices {

    private UiServices() {
        // Utility class - no instantiation
    }

    // ========================================================================
    // TOAST SERVICE - Non-blocking notification toasts
    // ========================================================================

    /**
     * Singleton service for displaying toast notifications.
     *
     * <p>Toasts are non-blocking, auto-dismissing notifications that appear at
     * the bottom of the screen. They support four levels: SUCCESS, ERROR, WARNING, INFO.
     *
     * <p>Usage:
     * <pre>
     * // In controller's initialize():
     * UiServices.Toast.getInstance().setContainer((StackPane) rootPane);
     *
     * // Show notifications:
     * UiServices.Toast.getInstance().showSuccess("Profile saved!");
     * UiServices.Toast.getInstance().showError("Connection failed");
     * </pre>
     */
    public static final class Toast {

        private static Toast instance;
        private StackPane toastContainer;

        private Toast() {
            // Private constructor for singleton
        }

        /** Gets the singleton instance. Thread-safe lazy initialization. */
        public static synchronized Toast getInstance() {
            if (instance == null) {
                instance = new Toast();
            }
            return instance;
        }

        /**
         * Sets the container pane where toasts will be displayed.
         * Must be called before showing any toasts.
         *
         * @param container the StackPane to use as toast container
         */
        public void setContainer(StackPane container) {
            this.toastContainer = container;
        }

        /** Shows a success toast with green accent. */
        public void showSuccess(String message) {
            show(message, ToastLevel.SUCCESS, Duration.seconds(3));
        }

        /** Shows an error toast with red accent. */
        public void showError(String message) {
            show(message, ToastLevel.ERROR, Duration.seconds(5));
        }

        /** Shows a warning toast with amber accent. */
        public void showWarning(String message) {
            show(message, ToastLevel.WARNING, Duration.seconds(4));
        }

        /** Shows an info toast with blue accent. */
        public void showInfo(String message) {
            show(message, ToastLevel.INFO, Duration.seconds(3));
        }

        private void show(String message, ToastLevel level, Duration duration) {
            if (toastContainer == null) {
                return;
            }

            HBox toast = createToast(message, level);
            toastContainer.getChildren().add(toast);
            StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
            StackPane.setMargin(toast, new Insets(0, 0, 30, 0));

            // Initial state for entrance animation
            toast.setOpacity(0);
            toast.setTranslateY(50);

            // Entrance animation: fade in + slide up
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
            fadeIn.setToValue(1);

            TranslateTransition slideUp = new TranslateTransition(Duration.millis(200), toast);
            slideUp.setToY(0);

            ParallelTransition entrance = new ParallelTransition(fadeIn, slideUp);
            entrance.play();

            // Auto-dismiss after duration
            PauseTransition pause = new PauseTransition(duration);
            pause.setOnFinished(e -> dismiss(toast));
            pause.play();
        }

        private void dismiss(HBox toast) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> toastContainer.getChildren().remove(toast));
            fadeOut.play();
        }

        private HBox createToast(String message, ToastLevel level) {
            HBox toastBox = new HBox(12);
            toastBox.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase());
            toastBox.setAlignment(Pos.CENTER_LEFT);

            FontIcon icon = new FontIcon(level.getIcon());
            icon.setIconSize(20);

            Label label = new Label(message);
            label.getStyleClass().add("toast-message");

            toastBox.getChildren().addAll(icon, label);
            toastBox.setMaxWidth(400);
            return toastBox;
        }

        /** Toast notification levels with associated icons. */
        public enum ToastLevel {
            SUCCESS("mdi2c-check-circle"),
            ERROR("mdi2a-alert-circle"),
            WARNING("mdi2a-alert"),
            INFO("mdi2i-information");

            private final String icon;

            ToastLevel(String icon) {
                this.icon = icon;
            }

            public String getIcon() {
                return icon;
            }
        }
    }

    // ========================================================================
    // IMAGE CACHE - Thread-safe LRU image caching
    // ========================================================================

    /**
     * Thread-safe LRU-style image cache for avatars and profile photos.
     *
     * <p>Uses ConcurrentHashMap for thread safety. Cache keys are composed of
     * path and requested size for efficient lookups.
     *
     * <p>Usage:
     * <pre>
     * Image avatar = UiServices.ImageCache.getAvatar("/photos/user123.jpg", 64);
     * Image profile = UiServices.ImageCache.getImage("/photos/user123.jpg", 200, 200);
     * </pre>
     */
    public static final class ImageCache {

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
            Image image = CACHE.computeIfAbsent(key, k -> loadImage(path, width, height));

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
        @SuppressWarnings("java:S2095") // InputStream closed by Image constructor
        private static Image getDefaultAvatar(double width, double height) {
            String key = "default@" + width + "x" + height;

            return CACHE.computeIfAbsent(key, k -> {
                try {
                    var stream = ImageCache.class.getResourceAsStream(DEFAULT_AVATAR_PATH);
                    if (stream != null) {
                        return new Image(stream, width, height, true, true);
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

        /** Evicts the oldest entry from the cache. Simple strategy - just remove first entry found. */
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
}
