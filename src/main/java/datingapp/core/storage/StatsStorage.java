package datingapp.core.storage;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.UserStats;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage interface for statistics: user stats and platform stats.
 * Extends both individual interfaces for backward compatibility while providing
 * a single injection point for statistics-related storage.
 *
 * <p>
 * This interface combines:
 * <ul>
 * <li>{@code UserStatsStorage} - Individual user statistics snapshots</li>
 * <li>{@code PlatformStatsStorage} - Platform-wide aggregate statistics</li>
 * </ul>
 */
public interface StatsStorage {

    // ═══════════════════════════════════════════════════════════════
    // User Stats Operations (from UserStatsStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Save a new user stats snapshot. */
    void saveUserStats(UserStats stats);

    /** Get the most recent stats for a user. */
    Optional<UserStats> getLatestUserStats(UUID userId);

    /** Get historical snapshots for a user (most recent first). */
    List<UserStats> getUserStatsHistory(UUID userId, int limit);

    /** Get latest stats for all users (for computing platform averages). */
    List<UserStats> getAllLatestUserStats();

    /** Delete snapshots older than a certain date (cleanup). */
    int deleteUserStatsOlderThan(Instant cutoff);

    // ═══════════════════════════════════════════════════════════════
    // Platform Stats Operations (from PlatformStatsStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Save platform statistics snapshot. */
    void savePlatformStats(PlatformStats stats);

    /** Get the most recent platform stats. */
    Optional<PlatformStats> getLatestPlatformStats();

    /** Get historical platform stats (most recent first). */
    List<PlatformStats> getPlatformStatsHistory(int limit);

    // ═══════════════════════════════════════════════════════════════
    // Profile View Tracking (from ProfileViewStorage)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Records a profile view.
     *
     * @param viewerId ID of the user who viewed the profile
     * @param viewedId ID of the profile that was viewed
     */
    void recordProfileView(UUID viewerId, UUID viewedId);

    /**
     * Gets the total number of views for a user's profile.
     *
     * @param userId ID of the user whose profile was viewed
     * @return total view count
     */
    int getProfileViewCount(UUID userId);

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
    boolean hasViewedProfile(UUID viewerId, UUID viewedId);

    // ═══════════════════════════════════════════════════════════════
    // Achievement Tracking (from UserAchievementStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Saves a new achievement unlock. */
    void saveUserAchievement(UserAchievement achievement);

    /** Gets all achievements unlocked by a user. */
    List<UserAchievement> getUnlockedAchievements(UUID userId);

    /** Checks if a user has a specific achievement. */
    boolean hasAchievement(UUID userId, Achievement achievement);

    /** Counts total achievements unlocked by a user. */
    int countUnlockedAchievements(UUID userId);
}
