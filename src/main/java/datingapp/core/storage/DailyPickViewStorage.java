package datingapp.core.storage;

import java.time.LocalDate;
import java.util.UUID;

/** Storage interface for tracking daily pick views to ensure persistence across restarts. */
public interface DailyPickViewStorage {
    /**
     * Records that a user has viewed their daily pick for a specific date.
     *
     * @param userId The user who viewed the pick
     * @param date The date of the pick
     */
    void markAsViewed(UUID userId, LocalDate date);

    /**
     * Checks if a user has viewed their daily pick for a specific date.
     *
     * @param userId The user to check
     * @param date The date to check
     * @return true if viewed, false otherwise
     */
    boolean isViewed(UUID userId, LocalDate date);

    /**
     * Deletes view records older than the specified date.
     *
     * @param before The cutoff date (exclusive)
     * @return Number of records deleted
     */
    int deleteOlderThan(LocalDate before);
}
