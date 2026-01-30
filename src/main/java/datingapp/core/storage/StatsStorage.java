package datingapp.core.storage;

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
}
