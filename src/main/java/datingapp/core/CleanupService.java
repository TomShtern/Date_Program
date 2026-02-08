package datingapp.core;

import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Service for cleaning up expired or stale database records.
 * Removes old daily pick views, expired sessions, and other data
 * that accumulates over time.
 *
 * <p>Usage: Call {@link #runCleanup()} periodically (e.g., daily via scheduler
 * or on application startup) to purge outdated records.
 *
 * <p>This service helps maintain database performance and reduces storage costs
 * by removing data that is no longer needed.
 */
public class CleanupService {

    private final StatsStorage statsStorage;
    private final SwipeSessionStorage sessionStorage;
    private final AppConfig config;

    /**
     * Creates a new CleanupService.
     *
     * @param statsStorage   Storage for daily pick views cleanup
     * @param sessionStorage Storage for swipe session cleanup
     * @param config         Application configuration with cleanup thresholds
     */
    public CleanupService(StatsStorage statsStorage, SwipeSessionStorage sessionStorage, AppConfig config) {
        this.statsStorage = Objects.requireNonNull(statsStorage, "statsStorage cannot be null");
        this.sessionStorage = Objects.requireNonNull(sessionStorage, "sessionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Runs all cleanup operations and returns a summary of what was cleaned.
     *
     * <p>This method is idempotent - calling it multiple times has no negative effects.
     * Already-cleaned data will simply return 0 for that category.
     *
     * @return CleanupResult with counts of deleted records
     */
    public CleanupResult runCleanup() {
        Instant cutoffDate = AppClock.now().minus(config.cleanupRetentionDays(), ChronoUnit.DAYS);

        int dailyPicksDeleted = statsStorage.deleteExpiredDailyPickViews(cutoffDate);
        int sessionsDeleted = sessionStorage.deleteExpiredSessions(cutoffDate);

        return new CleanupResult(dailyPicksDeleted, sessionsDeleted);
    }

    /**
     * Result of a cleanup operation.
     *
     * @param dailyPicksDeleted Number of expired daily pick view records deleted
     * @param sessionsDeleted   Number of expired session records deleted
     */
    public record CleanupResult(int dailyPicksDeleted, int sessionsDeleted) {

        /** Total number of records deleted across all categories. */
        public int totalDeleted() {
            return dailyPicksDeleted + sessionsDeleted;
        }

        /** Returns true if any records were deleted. */
        public boolean hadWork() {
            return totalDeleted() > 0;
        }

        @Override
        public String toString() {
            return "CleanupResult[dailyPicks=" + dailyPicksDeleted + ", sessions=" + sessionsDeleted + ", total="
                    + totalDeleted() + "]";
        }
    }
}
