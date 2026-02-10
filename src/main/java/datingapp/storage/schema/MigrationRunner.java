package datingapp.storage.schema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs schema migrations: column additions, foreign-key backfills, and version tracking. Extracted
 * from {@code DatabaseManager} for single-responsibility.
 *
 * <p><strong>Ordering constraint:</strong> column migrations in {@link
 * #migrateSchemaColumns(Statement)} must only run <em>after</em> the target tables exist. Fresh
 * databases create tables with all columns already present; migrations cover backward compatibility
 * for databases created by earlier schema versions.
 */
public final class MigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z]\\w*");

    private MigrationRunner() {
        // Utility class — static methods only
    }

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs the complete V1 migration: creates or upgrades the schema to version 1. On a fresh
     * database, delegates to {@link SchemaInitializer#createAllTables(Statement)} for full DDL. On
     * an existing database (version 1 already recorded), runs column migration for backward
     * compatibility.
     *
     * @param stmt a JDBC statement connected to the target database
     * @throws SQLException if any migration statement fails
     */
    public static void migrateV1(Statement stmt) throws SQLException {
        createSchemaVersionTable(stmt);

        if (isVersionApplied(stmt, 1)) {
            // Existing database — add any new columns for backward compatibility
            migrateSchemaColumns(stmt);
            return;
        }

        // Fresh database — create all tables from scratch
        SchemaInitializer.createAllTables(stmt);

        // Add foreign keys (safe on fresh DB — all tables already exist)
        addMissingForeignKeys(stmt);

        recordSchemaVersion(stmt, 1, "Initial consolidated schema with all tables and FK constraints");
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema versioning
    // ═══════════════════════════════════════════════════════════════

    /** Creates the schema_version tracking table if it does not exist. */
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
     * @return true if the version row exists, false otherwise (including if the table is missing)
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
     * @param stmt the statement to use
     * @param version the schema version number
     * @param description description of what this version includes
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
     * Adds columns that were introduced after the initial schema. Uses {@code IF NOT EXISTS} to
     * safely handle already-migrated databases.
     *
     * <p><strong>Important:</strong> This method must only be called when the tables already exist
     * (i.e., when {@code isVersionApplied(stmt, 1)} returns true).
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
    }

    // ═══════════════════════════════════════════════════════════════
    // Foreign key backfill
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds missing foreign key constraints to existing tables. Uses {@code IF NOT EXISTS} to safely
     * handle already-constrained databases. Silently skips tables that don't exist yet.
     */
    static void addMissingForeignKeys(Statement stmt) throws SQLException {
        // daily_pick_views - add FK to users
        addForeignKeyIfPresent(stmt, "daily_pick_views", "fk_daily_pick_views_user", "user_id", "users", "id");

        // user_achievements - add FK to users
        addForeignKeyIfPresent(stmt, "user_achievements", "fk_user_achievements_user", "user_id", "users", "id");

        // friend_requests
        addForeignKeyIfPresent(stmt, "friend_requests", "fk_friend_requests_from", "from_user_id", "users", "id");
        addForeignKeyIfPresent(stmt, "friend_requests", "fk_friend_requests_to", "to_user_id", "users", "id");

        // notifications
        addForeignKeyIfPresent(stmt, "notifications", "fk_notifications_user", "user_id", "users", "id");

        // blocks
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocker", "blocker_id", "users", "id");
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocked", "blocked_id", "users", "id");

        // reports
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reporter", "reporter_id", "users", "id");
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reported", "reported_user_id", "users", "id");

        // conversations
        addForeignKeyIfPresent(stmt, "conversations", "fk_conversations_user_a", "user_a", "users", "id");
        addForeignKeyIfPresent(stmt, "conversations", "fk_conversations_user_b", "user_b", "users", "id");

        // messages
        addForeignKeyIfPresent(stmt, "messages", "fk_messages_sender", "sender_id", "users", "id");
        addForeignKeyIfPresent(stmt, "messages", "fk_messages_conversation", "conversation_id", "conversations", "id");

        // profile_notes
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_author", "author_id", "users", "id");
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_subject", "subject_id", "users", "id");

        // profile_views
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewer", "viewer_id", "users", "id");
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewed", "viewed_id", "users", "id");
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
     * Checks if the SQLException indicates a missing table (H2 error code 42102 / SQL state
     * 42S02).
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
