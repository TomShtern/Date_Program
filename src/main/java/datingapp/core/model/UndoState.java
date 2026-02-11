package datingapp.core.model;

import datingapp.core.model.UserInteractions.Like;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents the undo state for a single swipe action.
 * Persisted to database to survive restarts.
 *
 * @param userId    The user who can undo this swipe
 * @param like      The like/pass that was recorded
 * @param matchId   Match ID if a match was created, null otherwise
 * @param expiresAt When the undo window expires
 */
public record UndoState(UUID userId, Like like, String matchId, Instant expiresAt) {

    public UndoState {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(like, "like cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    /**
     * Factory method to create a new undo state.
     *
     * @param userId    The user who can undo this swipe
     * @param like      The like/pass that was recorded
     * @param matchId   Match ID if a match was created, null otherwise
     * @param expiresAt When the undo window expires
     * @return New UndoState instance
     */
    public static UndoState create(UUID userId, Like like, String matchId, Instant expiresAt) {
        return new UndoState(userId, like, matchId, expiresAt);
    }

    /**
     * Checks if this undo state has expired.
     *
     * @param now Current time
     * @return true if the undo window has expired
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * Storage interface for UndoState persistence.
     * Stores one undo state per user (latest swipe only).
     */
    public interface Storage {

        /**
         * Saves or replaces the undo state for a user.
         * Each user has at most one active undo state.
         *
         * @param state The undo state to save
         */
        void save(UndoState state);

        /**
         * Gets the undo state for a user.
         *
         * @param userId The user ID
         * @return The undo state if present, empty otherwise
         */
        Optional<UndoState> findByUserId(UUID userId);

        /**
         * Deletes the undo state for a user.
         *
         * @param userId The user ID
         * @return true if a state was deleted, false if none existed
         */
        boolean delete(UUID userId);

        /**
         * Deletes all expired undo states.
         *
         * @param now Current time for expiration check
         * @return Number of expired states deleted
         */
        int deleteExpired(Instant now);

        /**
         * Gets all undo states (for testing/admin purposes).
         *
         * @return List of all undo states
         */
        List<UndoState> findAll();
    }
}
