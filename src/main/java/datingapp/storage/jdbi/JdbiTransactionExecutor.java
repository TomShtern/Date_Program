package datingapp.storage.jdbi;

import datingapp.core.storage.TransactionExecutor;
import datingapp.storage.StorageException;
import java.util.Objects;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * JDBI implementation of TransactionExecutor for atomic database operations.
 * Provides ACID guarantees for multi-table operations like undo.
 */
public final class JdbiTransactionExecutor implements TransactionExecutor {

    private final Jdbi jdbi;

    /**
     * Creates a new transaction executor.
     *
     * @param jdbi The JDBI instance to use for transactions
     */
    public JdbiTransactionExecutor(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    @Override
    public boolean atomicUndoDelete(UUID likeId, String matchId) {
        try {
            return jdbi.inTransaction(handle -> {
                // Delete the like
                int likesDeleted = handle.createUpdate("DELETE FROM likes WHERE id = :id")
                        .bind("id", likeId)
                        .execute();

                if (likesDeleted == 0) {
                    // Like not found - transaction rolls back automatically due to exception
                    return false;
                }

                // Delete the match if it exists
                if (matchId != null) {
                    handle.createUpdate("DELETE FROM matches WHERE id = :id")
                            .bind("id", matchId)
                            .execute();
                }

                return true;
            });
        } catch (Exception e) {
            throw new StorageException("Atomic undo delete failed", e);
        }
    }
}
