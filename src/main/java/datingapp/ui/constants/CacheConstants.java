package datingapp.ui.constants;

/** Shared cache sizing and resource defaults for UI utilities. */
public final class CacheConstants {

    private CacheConstants() {
        // Utility class
    }

    /** Maximum number of images to cache before eviction. */
    public static final int IMAGE_CACHE_MAX_SIZE = 100;

    /** Default avatar resource path. */
    public static final String DEFAULT_AVATAR_PATH = "/images/default-avatar.png";
}
