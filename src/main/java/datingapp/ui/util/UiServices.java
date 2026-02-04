package datingapp.ui.util;

import javafx.scene.image.Image;

/**
 * Consolidated UI service utilities for cross-cutting concerns.
 * Delegates to top-level {@link Toast} and {@link ImageCache} utilities.
 *
 * <p>
 * Access via top-level classes:
 * <ul>
 * <li>{@link Toast} - Toast notification helpers</li>
 * <li>{@link ImageCache} - Static image caching with LRU eviction</li>
 * </ul>
 */
public final class UiServices {

    private UiServices() {
        // Utility class - no instantiation
    }

    /** Returns a cached avatar image or a default placeholder. */
    public static Image getAvatar(String path, double size) {
        return ImageCache.getAvatar(path, size);
    }
}
