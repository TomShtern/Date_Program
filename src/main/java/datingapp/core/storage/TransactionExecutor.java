package datingapp.core.storage;

import java.util.UUID;

/**
 * Interface for executing atomic storage operations, specifically for undo.
 * Defined in core, implemented in storage layer to provide transaction support.
 *
 * <p>This allows core services to request atomic multi-table operations
 * without depending on database-specific transaction APIs.
 */
public interface TransactionExecutor {

    /**
     * Atomically deletes a like and optionally its associated match.
     * Used by UndoService to ensure data consistency during undo operations.
     *
     * @param likeId  The UUID of the like to delete
     * @param matchId The match ID to delete (can be null if no match was created)
     * @return true if all deletions succeeded, false otherwise
     */
    boolean atomicUndoDelete(UUID likeId, String matchId);
}
