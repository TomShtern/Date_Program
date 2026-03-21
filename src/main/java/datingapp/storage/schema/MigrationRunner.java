package datingapp.storage.schema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versioned migration runner: applies schema migrations in version order, each
 * exactly once.
 *
 * <p>
 * Fresh databases execute every migration in sequence. Upgraded databases skip
 * already-applied
 * versions (tracked in {@code schema_version}) and execute only what remains.
 * Both paths converge
 * on an identical final schema.
 *
 * <p>
 * <strong>Adding a future migration (V2+):</strong>
 *
 * <ol>
 * <li>Append a new {@code VersionedMigration} entry to {@link #MIGRATIONS}.
 * <li>Add the corresponding {@code applyVN(Statement)} private static method.
 * </ol>
 *
 * No other files need changes.
 */
public final class MigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);

    private MigrationRunner() {
        // Utility class — static methods only
    }

    // ═══════════════════════════════════════════════════════════════
    // Migration registry
    // ═══════════════════════════════════════════════════════════════

    /**
     * A single versioned migration step. The {@code action} is a
     * {@link MigrationAction} applied
     * exactly once when {@code version} has not yet been recorded in
     * {@code schema_version}.
     */
    private record VersionedMigration(int version, String description, MigrationAction action) {

        @FunctionalInterface
        interface MigrationAction {
            void apply(Statement stmt) throws SQLException;
        }
    }

    /**
     * Ordered registry of all schema migrations. Fresh databases execute all
     * entries; upgraded databases skip already-applied versions.
     *
     * <p>
     * <strong>APPEND-ONLY:</strong> never reorder or remove entries. New migrations
     * go at the end.
     */
    private static final List<VersionedMigration> MIGRATIONS = List.of(
            new VersionedMigration(
                    1, "Baseline schema: all tables, columns, indexes, FKs, and constraints", MigrationRunner::applyV1),
            new VersionedMigration(
                    2,
                    "Add daily_picks cache table (no-op on fresh databases where V1 already includes it)",
                    MigrationRunner::applyV2),
            new VersionedMigration(
                    3,
                    "Drop legacy serialized users-table columns after normalized profile rollout",
                    MigrationRunner::applyV3),
            new VersionedMigration(
                    4,
                    "Add soft-delete columns: deleted_at to conversations and profile_notes tables",
                    MigrationRunner::applyV4));

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs all pending migrations in version order. Each migration is applied
     * exactly once. Fresh and upgraded databases take the same code path.
     *
     * @param stmt a JDBC statement connected to the target database
     * @throws SQLException if any migration statement fails
     */
    public static void runAllPending(Statement stmt) throws SQLException {
        createSchemaVersionTable(stmt);

        for (VersionedMigration migration : MIGRATIONS) {
            if (!isVersionApplied(stmt, migration.version())) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Applying migration V{}: {}", migration.version(), migration.description());
                }
                migration.action().apply(stmt);
                recordSchemaVersion(stmt, migration.version(), migration.description());
                if (LOG.isInfoEnabled()) {
                    LOG.info("Migration V{} applied successfully", migration.version());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Migration implementations
    // ═══════════════════════════════════════════════════════════════

    /**
     * V1 baseline migration. Creates all tables via {@link SchemaInitializer}.
     *
     * <p>
     * <strong>FROZEN:</strong> do not modify. Future schema changes go into V2+.
     */
    private static void applyV1(Statement stmt) throws SQLException {
        SchemaInitializer.createAllTables(stmt);
    }

    /**
     * V2 migration: creates the {@code daily_picks} persistent cache table.
     *
     * <p>Fresh databases already have this table from V1 (SchemaInitializer includes it),
     * so all DDL uses {@code IF NOT EXISTS} — fully idempotent and safe to run on any database state.
     * Existing databases upgrading from the old multi-migration schema get this table added
     * non-destructively, preserving all existing user data.
     */
    private static void applyV2(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_picks (
                    user_id UUID NOT NULL,
                    pick_date DATE NOT NULL,
                    picked_user_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (user_id, pick_date),
                    CONSTRAINT fk_daily_picks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_daily_picks_picked_user FOREIGN KEY (picked_user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_picks_pick_date ON daily_picks(pick_date)");
    }

    /**
     * V3 migration: retires legacy serialized profile columns now that normalized
     * tables are the single source of truth.
     */
    private static void applyV3(Statement stmt) throws SQLException {
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS photo_urls");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS interests");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS interested_in");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_smoking");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_drinking");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_wants_kids");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_looking_for");
        stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_education");
    }

    /**
     * V4 migration: adds soft-delete support via deleted_at column to conversations
     * and profile_notes. Existing rows remain visible (deleted_at IS NULL).
     */
    private static void applyV4(Statement stmt) throws SQLException {
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE profile_notes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema versioning
    // ═══════════════════════════════════════════════════════════════

    /** Creates the {@code schema_version} tracking table if it does not exist. */
    static void createSchemaVersionTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL,
                    description VARCHAR(255)
                )
                """);
    }

    /**
     * Checks whether a given schema version has already been applied.
     *
     * @return {@code true} if the version row exists, {@code false} otherwise
     */
    static boolean isVersionApplied(Statement stmt, int version) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = " + version)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Records a schema version as applied.
     */
    static void recordSchemaVersion(Statement stmt, int version, String description) throws SQLException {
        String sql = """
                MERGE INTO schema_version (version, applied_at, description)
                KEY (version)
                VALUES (?, CURRENT_TIMESTAMP, ?)
                """;

        try (var pstmt = stmt.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, version);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if the {@link SQLException} indicates a missing table (H2 error code
     * 42102 / SQL state 42S02).
     */
    private static boolean isMissingTable(SQLException e) {
        return "42S02".equals(e.getSQLState()) || e.getErrorCode() == 42102;
    }
}
