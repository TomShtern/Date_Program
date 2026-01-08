package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing undo state and executing undo operations.
 * Tracks the last swipe per user with a configurable time window (default 30 seconds).
 *
 * Undo state is stored in-memory per user (not persisted to database).
 * When a user swipes, their undo state is recorded. If they undo within the time window,
 * the Like is deleted and any resulting Match is also deleted.
 *
 * Phase 1 feature: Undo Last Swipe
 *
 * KNOWN LIMITATIONS:
 * - Like and Match deletions are NOT in a transaction. If Match deletion fails after Like
 *   deletion, the system may be left in an inconsistent state. For production systems with
 *   concurrent writes, wrap deletions in database transactions.
 * - In-memory undo state uses lazy cleanup (expired entries removed on access). Long-running
 *   applications with many users may accumulate stale entries. Consider implementing periodic
 *   cleanup or using a time-expiring cache library (e.g., Caffeine) for future versions.
 * - For multi-threaded or web deployments, TOCTOU (time-of-check-to-time-of-use) race conditions
 *   may occur between expiry checks and user input. Current implementation is acceptable for
 *   single-user console application.
 */
public class UndoService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final AppConfig config;

    // In-memory state per user: userId -> UndoState
    private final Map<UUID, UndoState> undoStates = new ConcurrentHashMap<>();

    /**
     * Constructor for UndoService.
     *
     * @param likeStorage   Storage interface for managing likes
     * @param matchStorage  Storage interface for managing matches
     * @param config        Application configuration with undo window setting
     */
    public UndoService(LikeStorage likeStorage, MatchStorage matchStorage, AppConfig config) {
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
        this.config = config;
    }

    /**
     * Records a swipe for potential undo.
     * Called after each like/pass action to store the undo state.
     *
     * @param userId        The user who swiped
     * @param like          The like/pass that was recorded
     * @param matchCreated  Optional match if the swipe created a match
     */
    public void recordSwipe(UUID userId, Like like, Optional<Match> matchCreated) {
        Instant expiresAt = Instant.now().plusSeconds(config.undoWindowSeconds());

        UndoState state = new UndoState(
                userId,
                like,
                matchCreated.map(Match::getId).orElse(null),
                expiresAt);

        undoStates.put(userId, state);
    }

    /**
     * Checks if a user can undo their last swipe.
     * Returns false if no undo state exists or the window has expired.
     * Lazy cleanup: removes expired state on access.
     *
     * @param userId The user to check
     * @return true if undo is available and not expired
     */
    public boolean canUndo(UUID userId) {
        UndoState state = undoStates.get(userId);

        if (state == null) {
            return false;
        }

        // Check if window has expired
        if (Instant.now().isAfter(state.expiresAt)) {
            undoStates.remove(userId);
            return false;
        }

        return true;
    }

    /**
     * Gets the seconds remaining for the current undo window.
     * Returns 0 if no undo available or expired.
     *
     * @param userId The user to check
     * @return Seconds remaining (0 if expired or no state)
     */
    public int getSecondsRemaining(UUID userId) {
        UndoState state = undoStates.get(userId);

        if (state == null) {
            return 0;
        }

        long seconds = Duration.between(Instant.now(), state.expiresAt).getSeconds();
        return Math.max(0, (int) seconds);
    }

    /**
     * Executes an undo operation.
     * Validates that undo is available, then deletes the Like and any resulting Match.
     * Clears the undo state so the same action cannot be undone twice.
     *
     * @param userId The user requesting the undo
     * @return UndoResult with success status, message, and side effects
     */
    public UndoResult undo(UUID userId) {
        UndoState state = undoStates.get(userId);

        // Validation: No undo state
        if (state == null) {
            return UndoResult.failure("No swipe to undo");
        }

        // Validation: Window expired
        if (Instant.now().isAfter(state.expiresAt)) {
            undoStates.remove(userId);
            return UndoResult.failure("Undo window expired");
        }

        // Execute undo
        boolean matchDeleted = false;

        try {
            // Delete the Like from database
            likeStorage.delete(state.like.id());

            // If a match was created, delete it too (cascade)
            if (state.matchId != null) {
                matchStorage.delete(state.matchId);
                matchDeleted = true;
            }

            // Clear undo state (no re-undo)
            undoStates.remove(userId);

            return UndoResult.success(state.like, matchDeleted);

        } catch (Exception e) {
            // Return error but don't clear state (user might retry)
            return UndoResult.failure("Failed to undo: " + e.getMessage());
        }
    }

    /**
     * Manually clears the undo state for a user.
     * Called when starting a new swipe or on explicit expiry.
     *
     * @param userId The user to clear
     */
    public void clearUndo(UUID userId) {
        undoStates.remove(userId);
    }

    // ===== Inner Classes =====

    /**
     * Represents the undo state for a single swipe action.
     * Immutable - created once, never modified.
     */
    private static class UndoState {
        final UUID userId;
        final Like like;
        final String matchId; // null if no match was created
        final Instant expiresAt;

        UndoState(UUID userId, Like like, String matchId, Instant expiresAt) {
            this.userId = userId;
            this.like = like;
            this.matchId = matchId;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Result of an undo operation.
     * Immutable record containing success status, message, and side effects.
     */
    public record UndoResult(
            boolean success,
            String message,
            Like undoneSwipe,
            boolean matchDeleted) {

        /**
         * Creates a successful undo result.
         *
         * @param like          The swipe that was undone
         * @param matchDeleted  Whether a match was also deleted
         * @return Success UndoResult
         */
        public static UndoResult success(Like like, boolean matchDeleted) {
            return new UndoResult(true, "", like, matchDeleted);
        }

        /**
         * Creates a failed undo result.
         *
         * @param message Error message explaining the failure
         * @return Failed UndoResult
         */
        public static UndoResult failure(String message) {
            return new UndoResult(false, message, null, false);
        }
    }
}
