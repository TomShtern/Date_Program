package datingapp.storage;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/**
 * Simple transaction template for ACID operations.
 * Wraps JDBI's transaction support to provide atomic execution of database operations.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Execute multiple operations atomically
 * transactionTemplate.execute(ops -> {
 *     ops.deleteFromTable("likes", likeId);
 *     ops.deleteFromTable("matches", matchId);
 * });
 *
 * // Return a value from transaction
 * boolean success = transactionTemplate.executeWithResult(ops -> {
 *     ops.deleteFromTable("likes", likeId);
 *     return true;
 * });
 * }</pre>
 */
public final class TransactionTemplate {

    private final Jdbi jdbi;

    /**
     * Creates a new transaction template.
     *
     * @param jdbi The JDBI instance to use for transactions
     */
    public TransactionTemplate(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    /**
     * Executes operations within a transaction. All operations will either complete
     * successfully together or be rolled back if any operation fails.
     *
     * @param operations The operations to execute atomically
     * @throws StorageException if the transaction fails
     */
    public void execute(Consumer<TransactionOperations> operations) {
        try {
            jdbi.useTransaction(handle -> {
                TransactionOperations ops = new TransactionOperations(handle);
                operations.accept(ops);
            });
        } catch (Exception e) {
            throw new StorageException("Transaction failed", e);
        }
    }

    /**
     * Executes operations within a transaction and returns a result.
     * All operations will either complete successfully together or be rolled back.
     *
     * @param <T> The return type
     * @param operations The operations to execute atomically
     * @return The result of the operations
     * @throws StorageException if the transaction fails
     */
    public <T> T executeWithResult(Function<TransactionOperations, T> operations) {
        try {
            return jdbi.inTransaction(handle -> {
                TransactionOperations ops = new TransactionOperations(handle);
                return operations.apply(ops);
            });
        } catch (Exception e) {
            throw new StorageException("Transaction failed", e);
        }
    }

    /**
     * Provides atomic database operations within a transaction.
     * Operations on this object are executed on the same database handle.
     */
    public static final class TransactionOperations {

        private final Handle handle;

        TransactionOperations(Handle handle) {
            this.handle = handle;
        }

        /**
         * Gets the underlying JDBI handle for direct SQL operations.
         *
         * @return The JDBI handle
         */
        public Handle getHandle() {
            return handle;
        }

        /**
         * Deletes a record from a table by UUID primary key.
         *
         * @param tableName The table to delete from (e.g., "likes", "matches")
         * @param id The UUID of the record to delete
         * @return The number of rows deleted
         */
        public int deleteById(String tableName, Object id) {
            String sql = "DELETE FROM " + sanitizeTableName(tableName) + " WHERE id = :id";
            return handle.createUpdate(sql).bind("id", id).execute();
        }

        /**
         * Executes a custom SQL update (INSERT, UPDATE, DELETE).
         *
         * @param sql The SQL statement
         * @param bindings Pairs of parameter names and values
         * @return The number of rows affected
         */
        public int execute(String sql, Object... bindings) {
            try (var update = handle.createUpdate(sql)) {
                for (int i = 0; i < bindings.length; i += 2) {
                    String name = (String) bindings[i];
                    Object value = bindings[i + 1];
                    update.bind(name, value);
                }
                return update.execute();
            }
        }

        /**
         * Attaches a JDBI SQL object interface for transactional access.
         * Use this to call existing storage methods within the transaction.
         *
         * @param <T> The SQL object interface type
         * @param sqlObjectType The interface class
         * @return An attached instance that participates in the transaction
         */
        public <T> T attach(Class<T> sqlObjectType) {
            return handle.attach(sqlObjectType);
        }

        /**
         * Validates and sanitizes a table name to prevent SQL injection.
         */
        private static String sanitizeTableName(String tableName) {
            if (tableName == null || !tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid table name: " + tableName);
            }
            return tableName;
        }
    }
}
