package datingapp.core.storage;

import datingapp.core.Stats.PlatformStats;
import java.util.List;
import java.util.Optional;

/**
 * Storage interface for PlatformStats.
 * Defined in core, implemented in storage layer.
 */
public interface PlatformStatsStorage {

    /** Save platform statistics snapshot. */
    void save(PlatformStats stats);

    /** Get the most recent platform stats. */
    Optional<PlatformStats> getLatest();

    /** Get historical platform stats (most recent first). */
    List<PlatformStats> getHistory(int limit);
}
