package datingapp.core.storage;

import java.util.List;
import java.util.UUID;

/**
 * Storage interface for tracking profile views.
 * Defined in core, implemented in storage layer.
 */
public interface ProfileViewStorage {

    /**
     * Records a profile view.
     *
     * @param viewerId ID of the user who viewed the profile
     * @param viewedId ID of the profile that was viewed
     */
    void recordView(UUID viewerId, UUID viewedId);

    /**
     * Gets the total number of views for a user's profile.
     *
     * @param userId ID of the user whose profile was viewed
     * @return total view count
     */
    int getViewCount(UUID userId);

    /**
     * Gets the number of unique viewers for a user's profile.
     *
     * @param userId ID of the user whose profile was viewed
     * @return unique viewer count
     */
    int getUniqueViewerCount(UUID userId);

    /**
     * Gets recent viewers of a user's profile.
     *
     * @param userId ID of the user whose profile was viewed
     * @param limit maximum number of viewers to return
     * @return list of viewer IDs (most recent first)
     */
    List<UUID> getRecentViewers(UUID userId, int limit);

    /**
     * Checks if a user has viewed another user's profile.
     *
     * @param viewerId ID of the potential viewer
     * @param viewedId ID of the profile owner
     * @return true if the viewer has viewed the profile
     */
    boolean hasViewed(UUID viewerId, UUID viewedId);
}
