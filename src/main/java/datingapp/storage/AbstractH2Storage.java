package datingapp.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;

/**
 * Base class for all H2 storage implementations.
 * Provides shared infrastructure: schema management, nullable handling, error wrapping.
 */
public abstract class AbstractH2Storage {

    protected final DatabaseManager dbManager;

    protected AbstractH2Storage(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager cannot be null");
    }

    /**
     * Called during construction to ensure the table schema exists.
     * Subclasses should override this to create their tables if needed.
     * Default implementation does nothing.
     */
    protected void ensureSchema() {
        // Default: no schema initialization needed
    }

    // ========== Schema Helpers ==========

    /**
     * Adds a column to a table if it doesn't already exist.
     * Safe to call multiple times (idempotent).
     */
    protected void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        String checkSql = """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
            """;
        String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, tableName.toUpperCase());
            checkStmt.setString(2, columnName.toUpperCase());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement alterStmt = conn.prepareStatement(alterSql)) {
                        alterStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to add column " + columnName + " to " + tableName, e);
        }
    }

    // ========== Nullable Parameter Helpers ==========

    /**
     * Sets a nullable Instant as a Timestamp parameter.
     */
    protected void setNullableTimestamp(PreparedStatement stmt, int index, Instant value) throws SQLException {
        if (value != null) {
            stmt.setTimestamp(index, Timestamp.from(value));
        } else {
            stmt.setNull(index, Types.TIMESTAMP);
        }
    }

    /**
     * Sets a nullable Integer parameter.
     */
    protected void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value != null) {
            stmt.setInt(index, value);
        } else {
            stmt.setNull(index, Types.INTEGER);
        }
    }

    /**
     * Sets a nullable Long parameter.
     */
    protected void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value != null) {
            stmt.setLong(index, value);
        } else {
            stmt.setNull(index, Types.BIGINT);
        }
    }

    /**
     * Sets a nullable String parameter.
     */
    protected void setNullableString(PreparedStatement stmt, int index, String value) throws SQLException {
        if (value != null) {
            stmt.setString(index, value);
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }

    // ========== Nullable Result Helpers ==========

    /**
     * Gets a nullable Integer from a ResultSet.
     */
    protected Integer getNullableInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Gets a nullable Long from a ResultSet.
     */
    protected Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Gets a nullable Instant from a ResultSet Timestamp column.
     */
    protected Instant getNullableInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts != null ? ts.toInstant() : null;
    }

    // ========== Connection Helper ==========

    /**
     * Gets a database connection. Caller is responsible for closing.
     */
    protected Connection getConnection() throws SQLException {
        return dbManager.getConnection();
    }
}
