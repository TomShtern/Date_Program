package datingapp.core.storage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Storage interface for tracking when users view their daily picks.
 * Defined in core, implemented in storage layer.
 */
public interface DailyPickStorage {

    /**
     * Mark that a user has viewed their daily pick for a specific date.
     *
     * @param userId the user who viewed the pick
     * @param date the date of the pick
     */
    void markViewed(UUID userId, LocalDate date);

    /**
     * Check if a user has already viewed their daily pick for a date.
     *
     * @param userId the user to check
     * @param date the date to check
     * @return true if the user has viewed their pick for this date
     */
    boolean hasViewed(UUID userId, LocalDate date);

    /**
     * Remove view records older than a specified date. Used for cleanup/maintenance.
     *
     * @param before delete records before this date (exclusive)
     * @return number of records deleted
     */
    int cleanup(LocalDate before);
}
