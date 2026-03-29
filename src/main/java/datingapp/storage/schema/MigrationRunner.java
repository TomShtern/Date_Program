package datingapp.storage.schema;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MigrationRunner.class);
    private static final String WHERE_NOT_DELETED = " WHERE deleted_at IS NULL";
    private static final String TABLE_MATCHES = "MATCHES";

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
                    MigrationRunner::applyV4),
            new VersionedMigration(
                    5,
                    "Remove profile_views surrogate id and normalize the conversation unique constraint name",
                    MigrationRunner::applyV5),
            new VersionedMigration(
                    6,
                    "Add matches.updated_at column and backfill it from created_at for legacy rows",
                    MigrationRunner::applyV6),
            new VersionedMigration(
                    7,
                    "Add standalone messages(conversation_id) index alongside the composite message index",
                    MigrationRunner::applyV7),
            new VersionedMigration(
                    8,
                    "Add query-optimization indexes for likes, conversations, messages, sessions, stats, standouts",
                    MigrationRunner::applyV8),
            new VersionedMigration(
                    9,
                    "Repair matches.updated_at for legacy databases with already-recorded earlier versions",
                    MigrationRunner::applyV9),
            new VersionedMigration(
                    10, "Add named foreign keys for daily_pick_views and user_achievements", MigrationRunner::applyV10),
            new VersionedMigration(
                    11,
                    "Add pending friend-request uniqueness helpers and deterministic pair lookup columns",
                    MigrationRunner::applyV11));

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
            if (!isVersionApplied(stmt.getConnection(), migration.version())) {
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
     * <p>Fresh databases create this table through this migration, and the DDL uses
     * {@code IF NOT EXISTS} — fully idempotent and safe to run on any database state.
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

    /**
     * V5 migration: removes the dead surrogate key from {@code profile_views} and renames the legacy
     * conversation uniqueness constraint to the {@code uk_*} naming convention.
     */
    private static void applyV5(Statement stmt) throws SQLException {
        stmt.execute("ALTER TABLE profile_views DROP COLUMN IF EXISTS id");
        if (!hasPrimaryKey(stmt, "PROFILE_VIEWS")) {
            stmt.execute(
                    "ALTER TABLE profile_views ADD CONSTRAINT pk_profile_views PRIMARY KEY (viewer_id, viewed_id, viewed_at)");
        }
        stmt.execute("ALTER TABLE conversations DROP CONSTRAINT IF EXISTS unq_conversation_users");
        stmt.execute("ALTER TABLE conversations DROP CONSTRAINT IF EXISTS uk_conversation_users");
        stmt.execute("ALTER TABLE conversations ADD CONSTRAINT uk_conversation_users UNIQUE (user_a, user_b)");
    }

    /**
     * V6 migration: adds {@code updated_at} to {@code matches} for legacy databases that
     * predate the column, then backfills missing values from {@code created_at}.
     */
    private static void applyV6(Statement stmt) throws SQLException {
        repairMatchesUpdatedAtColumn(stmt);
    }

    /**
     * V7 migration: adds a dedicated index on {@code messages(conversation_id)}
     * for conversation lookups while preserving the existing composite
     * conversation/created_at index.
     */
    private static void applyV7(Statement stmt) throws SQLException {
        if (!hasTable(stmt, "MESSAGES")) {
            return;
        }
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id)");
    }

    /**
     * V8 migration: adds query-optimization indexes for hot read paths.
     *
     * <p>Partial indexes are used when supported. H2 does not support the WHERE
     * clause syntax, so each index falls back to a non-partial equivalent.
     */
    private static void applyV8(Statement stmt) throws SQLException {
        if (hasTable(stmt, "LIKES")) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_likes_direction_created ON likes(direction, created_at DESC)"
                            + WHERE_NOT_DELETED,
                    "CREATE INDEX IF NOT EXISTS idx_likes_direction_created ON likes(direction, created_at DESC)");
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_likes_received_created ON likes(who_got_liked, created_at DESC)"
                            + WHERE_NOT_DELETED,
                    "CREATE INDEX IF NOT EXISTS idx_likes_received_created ON likes(who_got_liked, created_at DESC)");
        }

        if (hasTable(stmt, "CONVERSATIONS")) {
            if (hasColumn(stmt, "CONVERSATIONS", "LAST_MESSAGE_AT")) {
                createIndexWithFallback(
                        stmt,
                        "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a, last_message_at DESC)"
                                + WHERE_NOT_DELETED,
                        "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a, last_message_at DESC)");
                createIndexWithFallback(
                        stmt,
                        "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b, last_message_at DESC)"
                                + WHERE_NOT_DELETED,
                        "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b, last_message_at DESC)");
            } else {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b)");
            }
        }

        if (hasTable(stmt, "MESSAGES")) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages(sender_id, created_at DESC)"
                            + WHERE_NOT_DELETED,
                    "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages(sender_id, created_at DESC)");
        }

        if (hasTable(stmt, "SWIPE_SESSIONS")) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc ON swipe_sessions(started_at DESC)"
                            + " WHERE state = 'ACTIVE'",
                    "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc ON swipe_sessions(started_at DESC)");
        }

        if (hasTable(stmt, "USER_STATS")) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed_desc ON user_stats(computed_at DESC)");
        }

        if (hasTable(stmt, "STANDOUTS")) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_standouts_interacted_at ON standouts(seeker_id, interacted_at DESC)"
                            + " WHERE interacted_at IS NOT NULL",
                    "CREATE INDEX IF NOT EXISTS idx_standouts_interacted_at ON standouts(seeker_id, interacted_at DESC)");
        }
    }

    /**
     * V9 migration: re-adds {@code matches.updated_at} when missing and backfills
     * any null legacy values from {@code created_at}.
     */
    private static void applyV9(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_MATCHES)) {
            return;
        }
        if (!hasColumn(stmt, TABLE_MATCHES, "UPDATED_AT")) {
            stmt.execute("ALTER TABLE matches ADD COLUMN updated_at TIMESTAMP");
        }
        stmt.execute("UPDATE matches SET updated_at = created_at WHERE updated_at IS NULL");
    }

    /**
     * V10 migration: adds deterministic foreign keys to daily_pick_views and
     * user_achievements with orphan preflight checks.
     */
    private static void applyV10(Statement stmt) throws SQLException {
        if (hasTable(stmt, "DAILY_PICK_VIEWS")) {
            addForeignKeyIfMissing(stmt, "daily_pick_views", "fk_daily_pick_views_user", "user_id", "users", "id");
        }
        if (hasTable(stmt, "USER_ACHIEVEMENTS")) {
            addForeignKeyIfMissing(stmt, "user_achievements", "fk_user_achievements_user", "user_id", "users", "id");
        }
    }

    /**
     * V11 migration: adds helper columns and a uniqueness invariant so only one
     * pending friend request can exist per user pair while preserving accepted/rejected
     * history rows.
     */
    private static void applyV11(Statement stmt) throws SQLException {
        if (!hasTable(stmt, "FRIEND_REQUESTS")) {
            return;
        }

        stmt.execute("ALTER TABLE friend_requests ADD COLUMN IF NOT EXISTS pair_key VARCHAR(73)");
        stmt.execute("ALTER TABLE friend_requests ADD COLUMN IF NOT EXISTS pending_marker VARCHAR(10)");

        stmt.execute("""
                UPDATE friend_requests
                SET pair_key = CASE
                    WHEN CAST(from_user_id AS VARCHAR) <= CAST(to_user_id AS VARCHAR)
                        THEN CONCAT(CAST(from_user_id AS VARCHAR), '|', CAST(to_user_id AS VARCHAR))
                    ELSE CONCAT(CAST(to_user_id AS VARCHAR), '|', CAST(from_user_id AS VARCHAR))
                END
                WHERE pair_key IS NULL
                """);

        stmt.execute("""
                UPDATE friend_requests
                SET pending_marker = CASE WHEN status = 'PENDING' THEN 'PENDING' ELSE NULL END
                WHERE pending_marker IS DISTINCT FROM CASE WHEN status = 'PENDING' THEN 'PENDING' ELSE NULL END
                """);

        long duplicatePendingPairs = countDuplicatePendingFriendRequestPairs(stmt);
        if (duplicatePendingPairs > 0) {
            throw new SQLException("Cannot enforce pending friend-request uniqueness: "
                    + duplicatePendingPairs
                    + " duplicate pending pair(s) exist in friend_requests");
        }

        stmt.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_requests_pending_pair ON friend_requests(pair_key, pending_marker)");
    }

    private static void repairMatchesUpdatedAtColumn(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_MATCHES)) {
            return;
        }
        stmt.execute("ALTER TABLE matches ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
        stmt.execute("UPDATE matches SET updated_at = COALESCE(updated_at, created_at)");
    }

    private static boolean hasTable(Statement stmt, String tableName) throws SQLException {
        try (ResultSet rs = stmt.getConnection().getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private static boolean hasPrimaryKey(Statement stmt, String tableName) throws SQLException {
        try (ResultSet rs = stmt.getConnection().getMetaData().getPrimaryKeys(null, null, tableName)) {
            return rs.next();
        }
    }

    private static boolean hasColumn(Statement stmt, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = stmt.getConnection().getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static boolean hasForeignKey(Statement stmt, String tableName, String foreignKeyName) throws SQLException {
        try (ResultSet rs =
                stmt.getConnection().getMetaData().getImportedKeys(null, null, tableName.toUpperCase(Locale.ROOT))) {
            while (rs.next()) {
                if (foreignKeyName.equalsIgnoreCase(rs.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void addForeignKeyIfMissing(
            Statement stmt,
            String tableName,
            String foreignKeyName,
            String columnName,
            String referencedTable,
            String referencedColumn)
            throws SQLException {
        if (hasForeignKey(stmt, tableName, foreignKeyName)) {
            return;
        }

        long orphanCount = countOrphanRows(stmt, tableName, columnName, referencedTable, referencedColumn);
        if (orphanCount > 0) {
            throw new SQLException("Cannot add foreign key "
                    + foreignKeyName
                    + ": "
                    + orphanCount
                    + " orphan row(s) found in "
                    + tableName
                    + "."
                    + columnName
                    + " referencing "
                    + referencedTable
                    + "."
                    + referencedColumn);
        }

        stmt.execute("ALTER TABLE "
                + tableName
                + " ADD CONSTRAINT IF NOT EXISTS "
                + foreignKeyName
                + " FOREIGN KEY ("
                + columnName
                + ") REFERENCES "
                + referencedTable
                + "("
                + referencedColumn
                + ") ON DELETE CASCADE");
    }

    private static long countOrphanRows(
            Statement stmt, String tableName, String columnName, String referencedTable, String referencedColumn)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM "
                + tableName
                + " t WHERE t."
                + columnName
                + " IS NOT NULL AND NOT EXISTS (SELECT 1 FROM "
                + referencedTable
                + " r WHERE r."
                + referencedColumn
                + " = t."
                + columnName
                + ")";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static long countDuplicatePendingFriendRequestPairs(Statement stmt) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM (
                    SELECT pair_key
                    FROM friend_requests
                    WHERE pending_marker = 'PENDING'
                    GROUP BY pair_key
                    HAVING COUNT(*) > 1
                ) duplicate_pairs
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static void createIndexWithFallback(Statement stmt, String partialSql, String fallbackSql)
            throws SQLException {
        try {
            stmt.execute(partialSql);
        } catch (SQLException partialFailure) {
            try {
                stmt.execute(fallbackSql);
            } catch (SQLException fallbackFailure) {
                fallbackFailure.addSuppressed(partialFailure);
                throw fallbackFailure;
            }
        }
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
    static boolean isVersionApplied(Connection conn, int version) throws SQLException {
        try (var pstmt = conn.prepareStatement("SELECT COUNT(*) FROM schema_version WHERE version = ?")) {
            pstmt.setInt(1, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
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
     * 42102 / 42104 / SQL state 42S02 / 42S04).
     */
    private static boolean isMissingTable(SQLException e) {
        return "42S02".equals(e.getSQLState())
                || "42S04".equals(e.getSQLState())
                || e.getErrorCode() == 42102
                || e.getErrorCode() == 42104;
    }
}
