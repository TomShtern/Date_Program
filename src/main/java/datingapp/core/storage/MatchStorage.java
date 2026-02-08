package datingapp.core.storage;

import datingapp.core.Match;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for Match entities.
 * Defined in core, implemented in storage layer.
 */
public interface MatchStorage {

    /** Saves a new match. */
    void save(Match match);

    /** Updates an existing match (e.g., state changes). */
    void update(Match match);

    /** Gets a match by ID. */
    Optional<Match> get(String matchId);

    /** Checks if a match exists by ID. */
    boolean exists(String matchId);

    /** Gets all ACTIVE matches for a given user. */
    List<Match> getActiveMatchesFor(UUID userId);

    /** Gets ALL matches for a given user (including ended ones). */
    List<Match> getAllMatchesFor(UUID userId);

    /** Gets a match by looking up the deterministic ID for two users. */
    default Optional<Match> getByUsers(UUID userA, UUID userB) {
        return get(Match.generateId(userA, userB));
    }

    /**
     * Delete a match by ID. Used for undo functionality when undoing a like that created a match.
     *
     * @param matchId the ID of the match to delete
     */
    void delete(String matchId);

    /**
     * Permanently removes all soft-deleted matches whose {@code deleted_at} is before the
     * given threshold. This is a hard delete for storage reclamation.
     *
     * @param threshold rows with {@code deleted_at < threshold} are purged
     * @return number of rows purged
     */
    default int purgeDeletedBefore(Instant threshold) {
        return 0;
    }
}
