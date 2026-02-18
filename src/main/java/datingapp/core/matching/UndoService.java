package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.Match;
import datingapp.core.storage.InteractionStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class UndoService {

    private final InteractionStorage interactionStorage;
    private final Undo.Storage undoStorage;
    private final AppConfig config;
    private final Clock clock;

    /**
     * Constructor for UndoService with persistent storage.
     *
     * @param interactionStorage Storage interface for managing likes and atomic undo
     * @param undoStorage        Storage interface for persisting undo state
     * @param config             Application configuration with undo window setting
     */
    public UndoService(InteractionStorage interactionStorage, Undo.Storage undoStorage, AppConfig config) {
        this(interactionStorage, undoStorage, config, AppClock.clock());
    }

    public UndoService(InteractionStorage interactionStorage, Undo.Storage undoStorage, AppConfig config, Clock clock) {
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.undoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Records a swipe for potential undo. Called after each like/pass action to
     * store the undo state. Persists to database.
     *
     * @param userId       The user who swiped
     * @param like         The like/pass that was recorded
     * @param matchCreated Match created by the swipe, or null if none
     */
    public void recordSwipe(UUID userId, Like like, Match matchCreated) {
        Instant expiresAt = Instant.now(clock).plusSeconds(config.undoWindowSeconds());
        String matchId = matchCreated != null ? matchCreated.getId() : null;

        Undo state = Undo.create(userId, like, matchId, expiresAt);
        undoStorage.save(state);
    }

    /**
     * Checks if a user can undo their last swipe. Returns false if no undo state
     * exists or the window has expired. Removes expired state on access.
     *
     * @param userId The user to check
     * @return true if undo is available and not expired
     */
    public boolean canUndo(UUID userId) {
        Optional<Undo> optState = undoStorage.findByUserId(userId);

        if (optState.isEmpty()) {
            return false;
        }

        Undo state = optState.get();

        // Check if window has expired
        if (state.isExpired(Instant.now(clock))) {
            undoStorage.delete(userId);
            return false;
        }

        return true;
    }

    /**
     * Gets the seconds remaining for the current undo window. Returns 0 if no undo
     * available or expired.
     *
     * @param userId The user to check
     * @return Seconds remaining (0 if expired or no state)
     */
    public int getSecondsRemaining(UUID userId) {
        Optional<Undo> optState = undoStorage.findByUserId(userId);

        if (optState.isEmpty()) {
            return 0;
        }

        long seconds =
                Duration.between(Instant.now(clock), optState.get().expiresAt()).getSeconds();
        return Math.max(0, (int) seconds);
    }

    /**
     * Executes an undo operation. Validates that undo is available, then deletes
     * the Like and any resulting Match atomically. Clears the undo state so the
     * same action cannot be undone twice.
     *
     * @param userId The user requesting the undo
     * @return UndoResult with success status, message, and side effects
     */
    public UndoResult undo(UUID userId) {
        Optional<Undo> optState = undoStorage.findByUserId(userId);

        // Validation: No undo state
        if (optState.isEmpty()) {
            return UndoResult.failure("No swipe to undo");
        }

        Undo state = optState.get();

        // Validation: Window expired
        if (state.isExpired(Instant.now(clock))) {
            undoStorage.delete(userId);
            return UndoResult.failure("Undo window expired");
        }

        // Execute undo - use atomic delete
        try {
            boolean matchDeleted = state.matchId() != null;

            boolean success = interactionStorage.atomicUndoDelete(state.like().id(), state.matchId());
            if (!success) {
                return UndoResult.failure("Like not found in database");
            }

            // Clear undo state (no re-undo)
            undoStorage.delete(userId);

            return UndoResult.success(state.like(), matchDeleted);

        } catch (Exception e) {
            // Return error but don't clear state (user might retry)
            return UndoResult.failure("Failed to undo: %s".formatted(e.getMessage()));
        }
    }

    /**
     * Manually clears the undo state for a user. Called when starting a new swipe
     * or on explicit expiry.
     *
     * @param userId The user to clear
     */
    public void clearUndo(UUID userId) {
        undoStorage.delete(userId);
    }

    /**
     * Cleans up expired undo states. Should be called periodically.
     *
     * @return Number of expired states removed
     */
    public int cleanupExpired() {
        return undoStorage.deleteExpired(Instant.now(clock));
    }

    /**
     * Result of an undo operation. Immutable record containing success status,
     * message, and side effects.
     */
    public static record UndoResult(boolean success, String message, Like undoneSwipe, boolean matchDeleted) {

        /**
         * Creates a successful undo result.
         *
         * @param like         The swipe that was undone
         * @param matchDeleted Whether a match was also deleted
         * @return Success UndoResult
         */
        public static UndoResult success(Like like, boolean matchDeleted) {
            return new UndoResult(true, null, like, matchDeleted);
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

        public UndoResult {
            if (success) {
                Objects.requireNonNull(undoneSwipe, "undoneSwipe cannot be null on success");
                if (message != null && !message.isBlank()) {
                    throw new IllegalArgumentException("message must be null or blank on success");
                }
            } else {
                if (message == null || message.isBlank()) {
                    throw new IllegalArgumentException("message is required on failure");
                }
                if (undoneSwipe != null || matchDeleted) {
                    throw new IllegalArgumentException("undo details must be empty on failure");
                }
            }
        }
    }
}
