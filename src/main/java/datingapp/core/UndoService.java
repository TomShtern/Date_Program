package datingapp.core;

import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.TransactionExecutor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing undo state and executing undo operations. Tracks the
 * last swipe per user
 * with a configurable time window (default 30 seconds).
 *
 * <p>
 * Undo state is persisted to database via UndoState.Storage, surviving
 * application restarts.
 *
 * <p>
 * Phase 1 feature: Undo Last Swipe
 *
 * <p>
 * TRANSACTION SUPPORT: When a TransactionExecutor is provided, Like and Match
 * deletions
 * are executed atomically within a database transaction, ensuring data
 * consistency.
 * If no TransactionExecutor is provided, operations fall back to non-atomic
 * deletes.
 */
public class UndoService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final UndoState.Storage undoStorage;
    private final AppConfig config;
    private final Clock clock;
    private volatile TransactionExecutor transactionExecutor;

    /**
     * Constructor for UndoService with persistent storage.
     * Use {@link #setTransactionExecutor} to enable atomic undo operations.
     *
     * @param likeStorage  Storage interface for managing likes
     * @param matchStorage Storage interface for managing matches
     * @param undoStorage  Storage interface for persisting undo state
     * @param config       Application configuration with undo window setting
     */
    public UndoService(
            LikeStorage likeStorage, MatchStorage matchStorage, UndoState.Storage undoStorage, AppConfig config) {
        this(likeStorage, matchStorage, undoStorage, config, Clock.systemUTC());
    }

    UndoService(
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            UndoState.Storage undoStorage,
            AppConfig config,
            Clock clock) {
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
        this.undoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Sets the transaction executor for atomic undo operations.
     * When set, undo operations will be performed atomically within a database
     * transaction.
     *
     * @param transactionExecutor The executor for atomic operations
     */
    public void setTransactionExecutor(TransactionExecutor transactionExecutor) {
        this.transactionExecutor = transactionExecutor;
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

        UndoState state = UndoState.create(userId, like, matchId, expiresAt);
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
        Optional<UndoState> optState = undoStorage.findByUserId(userId);

        if (optState.isEmpty()) {
            return false;
        }

        UndoState state = optState.get();

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
        Optional<UndoState> optState = undoStorage.findByUserId(userId);

        if (optState.isEmpty()) {
            return 0;
        }

        long seconds =
                Duration.between(Instant.now(clock), optState.get().expiresAt()).getSeconds();
        return Math.max(0, (int) seconds);
    }

    /**
     * Executes an undo operation. Validates that undo is available, then deletes
     * the Like and any resulting Match. Clears the undo state so the same action
     * cannot be undone twice.
     *
     * <p>
     * When a TransactionExecutor is configured, deletions are performed atomically.
     *
     * @param userId The user requesting the undo
     * @return UndoResult with success status, message, and side effects
     */
    public UndoResult undo(UUID userId) {
        Optional<UndoState> optState = undoStorage.findByUserId(userId);

        // Validation: No undo state
        if (optState.isEmpty()) {
            return UndoResult.failure("No swipe to undo");
        }

        UndoState state = optState.get();

        // Validation: Window expired
        if (state.isExpired(Instant.now(clock))) {
            undoStorage.delete(userId);
            return UndoResult.failure("Undo window expired");
        }

        // Execute undo - use transaction if available
        try {
            boolean matchDeleted = state.matchId() != null;

            if (transactionExecutor != null) {
                // Atomic delete using transaction
                boolean success =
                        transactionExecutor.atomicUndoDelete(state.like().id(), state.matchId());
                if (!success) {
                    return UndoResult.failure("Like not found in database");
                }
            } else {
                // Fallback to non-atomic deletes (backward compatibility)
                likeStorage.delete(state.like().id());
                if (matchDeleted) {
                    matchStorage.delete(state.matchId());
                }
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
