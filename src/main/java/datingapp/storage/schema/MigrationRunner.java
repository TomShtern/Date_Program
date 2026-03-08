package datingapp.storage.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
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
 * <strong>Adding a future migration (V3+):</strong>
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
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z]\\w*");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final String USERS_TABLE = "users";
    private static final String ID_COLUMN = "id";
    private static final String USER_ID_COLUMN = "user_id";
    private static final String CONVERSATIONS_TABLE = "conversations";

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
     * entries; upgraded
     * databases skip already-applied versions.
     *
     * <p>
     * <strong>APPEND-ONLY:</strong> never reorder or remove entries. New migrations
     * go at the
     * end.
     */
    private static final List<VersionedMigration> MIGRATIONS = List.of(
            new VersionedMigration(
                    1, "Baseline schema: all tables, columns, indexes, FKs, and constraints", MigrationRunner::applyV1),
            new VersionedMigration(
                    2,
                    "Backfill FKs, indexes, and constraints for databases migrated under old V1",
                    MigrationRunner::applyV2),
            new VersionedMigration(
                    3, "Normalize multi-value profile fields into junction tables", MigrationRunner::applyV3),
            new VersionedMigration(
                    4,
                    "Backfill normalized profile tables from legacy serialized user columns",
                    MigrationRunner::applyV4),
            new VersionedMigration(
                    5,
                    "Reapply idempotent user-column backfills for databases migrated under older V2 snapshots",
                    MigrationRunner::applyV5),
            new VersionedMigration(
                    6,
                    "Reapply user-column backfills for databases that already recorded V5 before later user projections were added",
                    MigrationRunner::applyV6));

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs all pending migrations in version order. Each migration is applied
     * exactly once
     * (tracked in the {@code schema_version} table). Fresh and upgraded databases
     * take the same
     * code path — the only difference is which versions are already recorded and
     * therefore skipped.
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
     * V1 baseline migration. Creates all tables via {@link SchemaInitializer}, then
     * ensures all
     * columns, foreign keys, and indexes exist. Every operation uses
     * {@code IF NOT EXISTS} — fully
     * idempotent.
     *
     * <p>
     * <strong>FROZEN:</strong> do not modify. Future schema changes go into V3+.
     */
    private static void applyV1(Statement stmt) throws SQLException {
        applyBaselineSchemaBackfill(stmt);
    }

    /**
     * V2 backfill migration. Databases where V1 was applied under the old migration
     * system (which
     * ran only {@code migrateSchemaColumns()}) have V1 recorded but are missing
     * FKs, indexes, and
     * constraints. This migration catches them up.
     *
     * <p>
     * On fresh databases where V1 ran under the new system, every operation is a
     * safe no-op
     * because all {@code IF NOT EXISTS} checks short-circuit.
     *
     * <p>
     * <strong>FROZEN:</strong> do not modify. Future schema changes go into V3+.
     */
    private static void applyV2(Statement stmt) throws SQLException {
        applyBaselineSchemaBackfill(stmt);
    }

    private static void applyBaselineSchemaBackfill(Statement stmt) throws SQLException {
        SchemaInitializer.createAllTables(stmt);
        migrateSchemaColumns(stmt);
        addMissingForeignKeys(stmt);
        ensureAllIndexes(stmt);
    }

    /**
     * V3 migration: creates normalized junction tables for multi-value profile
     * fields (photos, interests, gender preferences, dealbreakers). No data
     * migration — that is handled by WU-12 dual-write/read logic.
     *
     * <p>
     * On fresh databases these tables already exist (created by
     * {@link SchemaInitializer#createNormalizedProfileSchema}); all DDL uses
     * {@code IF NOT EXISTS} so this is a safe no-op.
     */
    private static void applyV3(Statement stmt) throws SQLException {
        SchemaInitializer.createNormalizedProfileSchema(stmt);
    }

    private static void applyV4(Statement stmt) throws SQLException {
        // Follow-up marker: keep this compatibility backfill until the dedicated legacy-column
        // removal migration lands after the normalized rollout has proven stable in the field.
        backfillNormalizedProfileData(stmt);
    }

    /**
     * V5 backfill migration for databases that already recorded earlier V2/V3/V4
     * snapshots before later `users` columns were added to migrateSchemaColumns().
     *
     * <p>This deliberately re-runs the column backfill only. All statements use
     * {@code ADD COLUMN IF NOT EXISTS}, so fresh or already-upgraded databases
     * see a safe no-op while stale databases gain the missing columns required by
     * current user-loading queries.
     */
    private static void applyV5(Statement stmt) throws SQLException {
        migrateSchemaColumns(stmt);
    }

    /**
     * V6 backfill migration for on-disk databases that already recorded V5 before
     * additional columns were added to the shared {@code users} projection used by
     * storage queries such as {@code JdbiUserStorage.ALL_COLUMNS}.
     *
     * <p>This intentionally re-runs only the idempotent column backfill so stale
     * local databases regain any missing {@code users} columns without affecting
     * fresh databases.
     */
    private static void applyV6(Statement stmt) throws SQLException {
        migrateSchemaColumns(stmt);
    }

    static void backfillNormalizedProfileData(Statement stmt) throws SQLException {
        try (ResultSet rs =
                stmt.executeQuery("SELECT id, photo_urls, interests, interested_in, db_smoking, db_drinking, "
                        + "db_wants_kids, db_looking_for, db_education FROM users")) {
            while (rs.next()) {
                String userId = rs.getString("id");
                backfillIfMissing(stmt, "user_photos", userId, parsePhotoUrls(rs.getString("photo_urls")), true);
                backfillIfMissing(stmt, "user_interests", userId, parseCsvOrJson(rs.getString("interests")), false);
                backfillIfMissing(
                        stmt, "user_interested_in", userId, parseCsvOrJson(rs.getString("interested_in")), false);
                backfillIfMissing(stmt, "user_db_smoking", userId, parseCsvOrJson(rs.getString("db_smoking")), false);
                backfillIfMissing(stmt, "user_db_drinking", userId, parseCsvOrJson(rs.getString("db_drinking")), false);
                backfillIfMissing(
                        stmt, "user_db_wants_kids", userId, parseCsvOrJson(rs.getString("db_wants_kids")), false);
                backfillIfMissing(
                        stmt, "user_db_looking_for", userId, parseCsvOrJson(rs.getString("db_looking_for")), false);
                backfillIfMissing(
                        stmt, "user_db_education", userId, parseCsvOrJson(rs.getString("db_education")), false);
            }
        }
    }

    private static void backfillIfMissing(
            Statement stmt, String tableName, String userId, List<String> values, boolean ordered) throws SQLException {
        if (values.isEmpty() || normalizedRowsExist(stmt, tableName, userId)) {
            return;
        }
        if (ordered) {
            try (PreparedStatement pstmt = stmt.getConnection()
                    .prepareStatement("INSERT INTO " + tableName + " (user_id, position, url) VALUES (?, ?, ?)")) {
                for (int i = 0; i < values.size(); i++) {
                    pstmt.setObject(1, java.util.UUID.fromString(userId));
                    pstmt.setInt(2, i);
                    pstmt.setString(3, values.get(i));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            return;
        }

        String sql;
        if ("user_interests".equals(tableName)) {
            sql = "INSERT INTO user_interests (user_id, interest) VALUES (?, ?)";
        } else if ("user_interested_in".equals(tableName)) {
            sql = "INSERT INTO user_interested_in (user_id, gender) VALUES (?, ?)";
        } else {
            sql = "INSERT INTO " + tableName + " (user_id, \"value\") VALUES (?, ?)";
        }
        try (PreparedStatement pstmt = stmt.getConnection().prepareStatement(sql)) {
            for (String value : values) {
                pstmt.setObject(1, java.util.UUID.fromString(userId));
                pstmt.setString(2, value);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private static boolean normalizedRowsExist(Statement stmt, String tableName, String userId) throws SQLException {
        try (PreparedStatement pstmt = stmt.getConnection()
                .prepareStatement(
                        "SELECT COUNT(*) FROM " + requireIdentifier(tableName, "table") + " WHERE user_id = ?")) {
            pstmt.setObject(1, java.util.UUID.fromString(userId));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static List<String> parsePhotoUrls(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.trim().startsWith("[") && raw.trim().endsWith("]")) {
            try {
                return OBJECT_MAPPER.readValue(raw.trim(), STRING_LIST_TYPE).stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
            } catch (Exception _) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Falling back to legacy-delimited photo URL parsing during normalized backfill");
                }
            }
        }
        List<String> values = new ArrayList<>();
        for (String token : raw.split("\\|")) {
            if (token != null && !token.isBlank()) {
                values.add(token.trim());
            }
        }
        return values;
    }

    private static List<String> parseCsvOrJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.trim().startsWith("[") && raw.trim().endsWith("]")) {
            try {
                return OBJECT_MAPPER.readValue(raw.trim(), STRING_LIST_TYPE).stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
            } catch (Exception _) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Falling back to CSV parsing during normalized profile backfill");
                }
            }
        }
        List<String> values = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (token != null && !token.isBlank()) {
                values.add(token.trim());
            }
        }
        return values;
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
     *         (including if the
     *         table is missing)
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
     *
     * @param stmt        the JDBC statement to use
     * @param version     the schema version number
     * @param description human-readable description of what this version includes
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
    // Column migrations (backward compatibility)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds columns that were introduced after the initial schema. Uses
     * {@code IF NOT EXISTS} to
     * safely handle already-migrated databases.
     *
     * <p>
     * Called from both {@link #applyV1} and {@link #applyV2} — safe to run on any
     * database
     * state.
     */
    static void migrateSchemaColumns(Statement stmt) throws SQLException {
        // Lifestyle fields
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS smoking VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS drinking VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS wants_kids VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS looking_for VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS education VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS height_cm INT");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS has_location_set BOOLEAN DEFAULT FALSE");
        // Dealbreaker fields
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_smoking VARCHAR(100)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_drinking VARCHAR(100)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_wants_kids VARCHAR(100)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_looking_for VARCHAR(100)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_education VARCHAR(200)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_min_height_cm INT");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_max_height_cm INT");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS db_max_age_diff INT");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS interests VARCHAR(500)");
        // Profile verification fields (Phase 2)
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(200)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(50)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_method VARCHAR(10)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_code VARCHAR(10)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_sent_at TIMESTAMP");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP");
        // Pace preference fields (Phase 4)
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS pace_messaging_frequency VARCHAR(30)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS pace_time_to_first_date VARCHAR(30)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS pace_communication_style VARCHAR(30)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS pace_depth_preference VARCHAR(30)");
        // Soft delete support (Phase 2.2)
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE matches ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE likes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE blocks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
        // Conversation individual archives (Phase 4.1)
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS archived_at_a TIMESTAMP");
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS archive_reason_a VARCHAR(20)");
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS archived_at_b TIMESTAMP");
        stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS archive_reason_b VARCHAR(20)");
    }

    // ═══════════════════════════════════════════════════════════════
    // Foreign key backfill
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds missing foreign key constraints to all tables. Uses
     * {@code IF NOT EXISTS} to safely
     * handle databases that already have them. Silently skips tables that do not
     * exist yet.
     */
    static void addMissingForeignKeys(Statement stmt) throws SQLException {
        // likes (named FKs in SchemaInitializer — IF NOT EXISTS is a true no-op on
        // fresh DBs)
        addForeignKeyIfPresent(stmt, "likes", "fk_likes_who_likes", "who_likes", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "likes", "fk_likes_who_got_liked", "who_got_liked", USERS_TABLE, ID_COLUMN);

        // matches (named FKs in SchemaInitializer — same logic)
        addForeignKeyIfPresent(stmt, "matches", "fk_matches_user_a", "user_a", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "matches", "fk_matches_user_b", "user_b", USERS_TABLE, ID_COLUMN);

        // swipe_sessions (named FK in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "swipe_sessions", "fk_sessions_user", USER_ID_COLUMN, USERS_TABLE, ID_COLUMN);

        // user_stats (named FK in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "user_stats", "fk_user_stats_user", USER_ID_COLUMN, USERS_TABLE, ID_COLUMN);

        // standouts (unnamed FKs in SchemaInitializer — produces benign double-FK on
        // fresh DBs)
        addForeignKeyIfPresent(stmt, "standouts", "fk_standouts_seeker", "seeker_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "standouts", "fk_standouts_user", "standout_user_id", USERS_TABLE, ID_COLUMN);

        // daily_pick_views
        addForeignKeyIfPresent(
                stmt, "daily_pick_views", "fk_daily_pick_views_user", USER_ID_COLUMN, USERS_TABLE, ID_COLUMN);

        // user_achievements
        addForeignKeyIfPresent(
                stmt, "user_achievements", "fk_user_achievements_user", USER_ID_COLUMN, USERS_TABLE, ID_COLUMN);

        // friend_requests (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(
                stmt, "friend_requests", "fk_friend_requests_from", "from_user_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "friend_requests", "fk_friend_requests_to", "to_user_id", USERS_TABLE, ID_COLUMN);

        // notifications (unnamed FK in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "notifications", "fk_notifications_user", USER_ID_COLUMN, USERS_TABLE, ID_COLUMN);

        // blocks (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocker", "blocker_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocked", "blocked_id", USERS_TABLE, ID_COLUMN);

        // reports (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reporter", "reporter_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reported", "reported_user_id", USERS_TABLE, ID_COLUMN);

        // conversations (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, CONVERSATIONS_TABLE, "fk_conversations_user_a", "user_a", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, CONVERSATIONS_TABLE, "fk_conversations_user_b", "user_b", USERS_TABLE, ID_COLUMN);

        // messages (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "messages", "fk_messages_sender", "sender_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(
                stmt, "messages", "fk_messages_conversation", "conversation_id", CONVERSATIONS_TABLE, ID_COLUMN);

        // profile_notes (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_author", "author_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_subject", "subject_id", USERS_TABLE, ID_COLUMN);

        // profile_views (unnamed FKs in SchemaInitializer)
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewer", "viewer_id", USERS_TABLE, ID_COLUMN);
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewed", "viewed_id", USERS_TABLE, ID_COLUMN);
    }

    // ═══════════════════════════════════════════════════════════════
    // Index backfill
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ensures all indexes exist by delegating to {@link SchemaInitializer}'s index
     * methods. All
     * three methods use {@code IF NOT EXISTS} — safe to call on databases that
     * already have the
     * indexes.
     *
     * <p>
     * Both classes are in {@code datingapp.storage.schema}, so package-private
     * visibility is
     * sufficient.
     */
    private static void ensureAllIndexes(Statement stmt) throws SQLException {
        SchemaInitializer.createCoreIndexes(stmt);
        SchemaInitializer.createStatsIndexes(stmt);
        SchemaInitializer.createAdditionalIndexes(stmt);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private static void addForeignKeyIfPresent(
            Statement stmt,
            String table,
            String constraint,
            String column,
            String referenceTable,
            String referenceColumn)
            throws SQLException {
        String sql = "ALTER TABLE "
                + requireIdentifier(table, "table")
                + " ADD CONSTRAINT IF NOT EXISTS "
                + requireIdentifier(constraint, "constraint")
                + " FOREIGN KEY ("
                + requireIdentifier(column, "column")
                + ") REFERENCES "
                + requireIdentifier(referenceTable, "reference table")
                + "("
                + requireIdentifier(referenceColumn, "reference column")
                + ") ON DELETE CASCADE";

        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            if (!isMissingTable(e)) {
                throw e;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Skipping FK constraint '{}' on '{}' — table not found (will be created later)",
                        constraint,
                        table);
            }
        }
    }

    /**
     * Checks if the {@link SQLException} indicates a missing table (H2 error code
     * 42102 / SQL
     * state 42S02).
     */
    private static boolean isMissingTable(SQLException e) {
        return "42S02".equals(e.getSQLState()) || e.getErrorCode() == 42102;
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label + " cannot be null");
        if (!SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL " + label + ": " + value);
        }
        return value;
    }
}
