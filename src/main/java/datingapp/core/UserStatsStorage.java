package datingapp.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage interface for UserStats snapshots. */
public interface UserStatsStorage {

    /** Save a new stats snapshot. */
    void save(UserStats stats);

    /** Get the most recent stats for a user. */
    Optional<UserStats> getLatest(UUID userId);

    /** Get historical snapshots for a user (most recent first). */
    List<UserStats> getHistory(UUID userId, int limit);

    /** Get latest stats for all users (for computing platform averages). */
    List<UserStats> getAllLatestStats();

    /** Delete snapshots older than a certain date (cleanup). */
    int deleteOlderThan(Instant cutoff);
}
