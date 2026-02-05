package datingapp.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Manages H2 database connections and schema initialization. */
public final class DatabaseManager {

    private static String jdbcUrl = "jdbc:h2:./data/dating";
    private static final String DEFAULT_DEV_PASSWORD = "dev";
    private static final String USER = "sa";
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    // Password is determined at connection time based on JDBC URL
    // This allows tests to set a different URL before password is resolved

    private static String getPassword() {
        return getConfiguredPassword();
    }

    private static String getConfiguredPassword() {
        // Allow a password override via env var.
        String envPassword = System.getenv("DATING_APP_DB_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }

        // Test databases don't require authentication.
        if (isTestUrl(jdbcUrl)) {
            return "";
        }

        // Dev/local default: use a stable password so the on-disk DB can be reopened.
        if (isLocalFileUrl(jdbcUrl)) {
            return DEFAULT_DEV_PASSWORD;
        }

        throw new IllegalStateException(
                "Database password must be provided via DATING_APP_DB_PASSWORD environment variable");
    }

    private static boolean isTestUrl(String url) {
        return url.contains("test") || url.contains(":mem:");
    }

    private static boolean isLocalFileUrl(String url) {
        return url.startsWith("jdbc:h2:./") || url.startsWith("jdbc:h2:.\\");
    }

    private static DatabaseManager instance;
    private boolean initialized = false;

    public static void setJdbcUrl(String url) {
        jdbcUrl = url;
    }

    public static void resetInstance() {
        instance = null;
    }

    private DatabaseManager() {
        // Driver loaded automatically by SPI
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /** Gets a new database connection. */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeSchema();
        }
        return DriverManager.getConnection(jdbcUrl, USER, getPassword());
    }

    /** Initializes the database schema. */
    private synchronized void initializeSchema() {
        if (initialized) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, getPassword());
                Statement stmt = conn.createStatement()) {

            // Users table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id UUID PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        bio VARCHAR(500),
                        birth_date DATE,
                        gender VARCHAR(20),
                        interested_in VARCHAR(100),
                        lat DOUBLE,
                        lon DOUBLE,
                        max_distance_km INT DEFAULT 50,
                        min_age INT DEFAULT 18,
                        max_age INT DEFAULT 99,
                        photo_urls VARCHAR(1000),
                        state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        -- Lifestyle fields (Phase 0.5b)
                        smoking VARCHAR(20),
                        drinking VARCHAR(20),
                        wants_kids VARCHAR(20),
                        looking_for VARCHAR(20),
                        education VARCHAR(20),
                        height_cm INT,
                        -- Dealbreaker fields (Phase 0.5b) - CSV storage
                        db_smoking VARCHAR(100),
                        db_drinking VARCHAR(100),
                        db_wants_kids VARCHAR(100),
                        db_looking_for VARCHAR(100),
                        db_education VARCHAR(200),
                        db_min_height_cm INT,
                        db_max_height_cm INT,
                        db_max_age_diff INT,
                        interests VARCHAR(500),
                        -- Profile verification fields (Phase 2)
                        email VARCHAR(200),
                        phone VARCHAR(50),
                        is_verified BOOLEAN,
                        verification_method VARCHAR(10),
                        verification_code VARCHAR(10),
                        verification_sent_at TIMESTAMP,
                        verified_at TIMESTAMP,
                        -- Pace preference fields (Phase 4)
                        pace_messaging_frequency VARCHAR(30),
                        pace_time_to_first_date VARCHAR(30),
                        pace_communication_style VARCHAR(30),
                        pace_depth_preference VARCHAR(30)
                    )
                    """);

            // Add columns for existing databases (Phase 0.5b migration)
            migrateSchemaColumns(stmt);

            // Likes table
            stmt.execute("""
                            CREATE TABLE IF NOT EXISTS likes (
                                id UUID PRIMARY KEY,
                                who_likes UUID NOT NULL,
                                who_got_liked UUID NOT NULL,
                                direction VARCHAR(10) NOT NULL,
                                created_at TIMESTAMP NOT NULL,
                                CONSTRAINT fk_likes_who_likes FOREIGN KEY (who_likes) REFERENCES users(id) ON DELETE CASCADE,
                                CONSTRAINT fk_likes_who_got_liked FOREIGN KEY (who_got_liked) REFERENCES users(id) ON DELETE CASCADE,
                                CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked)
                            )
                            """);

            // Matches table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS matches (
                        id VARCHAR(100) PRIMARY KEY,
                        user_a UUID NOT NULL,
                        user_b UUID NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        ended_at TIMESTAMP,
                        ended_by UUID,
                        end_reason VARCHAR(30),
                        CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
                        CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE,
                        CONSTRAINT uk_matches UNIQUE (user_a, user_b)
                    )
                    """);

            // Swipe sessions table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS swipe_sessions (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        started_at TIMESTAMP NOT NULL,
                        last_activity_at TIMESTAMP NOT NULL,
                        ended_at TIMESTAMP,
                        state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        swipe_count INT NOT NULL DEFAULT 0,
                        like_count INT NOT NULL DEFAULT 0,
                        pass_count INT NOT NULL DEFAULT 0,
                        match_count INT NOT NULL DEFAULT 0,
                        CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_who_likes ON likes(who_likes)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_who_got_liked ON likes(who_got_liked)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_a ON matches(user_a)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_b ON matches(user_b)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_state ON matches(state)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON swipe_sessions(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_active ON " + "swipe_sessions(user_id, state)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON " + "swipe_sessions(user_id, started_at)");
            // Users indexes for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_state ON users(state)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_gender_state ON users(gender, state)");

            // User stats snapshots table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_stats (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        computed_at TIMESTAMP NOT NULL,
                        total_swipes_given INT NOT NULL DEFAULT 0,
                        likes_given INT NOT NULL DEFAULT 0,
                        passes_given INT NOT NULL DEFAULT 0,
                        like_ratio DOUBLE NOT NULL DEFAULT 0.0,
                        total_swipes_received INT NOT NULL DEFAULT 0,
                        likes_received INT NOT NULL DEFAULT 0,
                        passes_received INT NOT NULL DEFAULT 0,
                        incoming_like_ratio DOUBLE NOT NULL DEFAULT 0.0,
                        total_matches INT NOT NULL DEFAULT 0,
                        active_matches INT NOT NULL DEFAULT 0,
                        match_rate DOUBLE NOT NULL DEFAULT 0.0,
                        blocks_given INT NOT NULL DEFAULT 0,
                        blocks_received INT NOT NULL DEFAULT 0,
                        reports_given INT NOT NULL DEFAULT 0,
                        reports_received INT NOT NULL DEFAULT 0,
                        reciprocity_score DOUBLE NOT NULL DEFAULT 0.0,
                        selectiveness_score DOUBLE NOT NULL DEFAULT 0.5,
                        attractiveness_score DOUBLE NOT NULL DEFAULT 0.5,
                        CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);

            // Platform stats table (Phase 0.5b)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS platform_stats (
                        id UUID PRIMARY KEY,
                        computed_at TIMESTAMP NOT NULL,
                        total_active_users INT NOT NULL DEFAULT 0,
                        avg_likes_received DOUBLE NOT NULL DEFAULT 0.0,
                        avg_likes_given DOUBLE NOT NULL DEFAULT 0.0,
                        avg_match_rate DOUBLE NOT NULL DEFAULT 0.0,
                        avg_like_ratio DOUBLE NOT NULL DEFAULT 0.5
                    )
                    """);

            // Stats indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_user_id ON user_stats(user_id)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_user_stats_computed ON " + "user_stats(user_id, computed_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_platform_stats_computed_at ON "
                    + "platform_stats(computed_at DESC)");

            // Daily pick views table (Phase 1)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS daily_pick_views (
                        user_id UUID NOT NULL,
                        viewed_date DATE NOT NULL,
                        viewed_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (user_id, viewed_date)
                    )
                    """);

            // Daily pick views index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_pick_views_date ON daily_pick_views(viewed_date)");

            // User achievements table (Phase 1)
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_achievements (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL,
                        achievement VARCHAR(50) NOT NULL,
                        unlocked_at TIMESTAMP NOT NULL,
                        UNIQUE (user_id, achievement)
                    )
                    """);

            // User achievements index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_achievements_user_id ON user_achievements(user_id)");

            // Create all feature tables (Phase 4 consolidation)
            createMessagingSchema(stmt);
            createSocialSchema(stmt);
            createModerationSchema(stmt);
            createProfileSchema(stmt);
            createStandoutsSchema(stmt);
            createUndoStateSchema(stmt);

            // Additional indexes for query optimization (kept here for consistency)
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_conversations_last_msg ON conversations(last_message_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_user ON friend_requests(to_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_picks_user ON daily_pick_views(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewer ON profile_views(viewer_id)");

            // Add missing foreign key constraints
            addMissingForeignKeys(stmt);

            // Track schema version
            createSchemaVersionTable(stmt);
            recordSchemaVersion(stmt, 1, "Initial consolidated schema with all tables and FK constraints");

            initialized = true;

        } catch (SQLException e) {
            throw new StorageException("Failed to initialize database schema", e);
        }
    }

    /**
     * Creates messaging-related tables (conversations, messages).
     * Consolidated from H2ConversationStorage and H2MessageStorage.
     */
    private void createMessagingSchema(Statement stmt) throws SQLException {
        // Conversations table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id VARCHAR(100) PRIMARY KEY,
                    user_a UUID NOT NULL,
                    user_b UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    last_message_at TIMESTAMP,
                    user_a_last_read_at TIMESTAMP,
                    user_b_last_read_at TIMESTAMP,
                    archived_at TIMESTAMP,
                    archive_reason VARCHAR(20),
                    visible_to_user_a BOOLEAN DEFAULT TRUE,
                    visible_to_user_b BOOLEAN DEFAULT TRUE,
                    CONSTRAINT unq_conversation_users UNIQUE (user_a, user_b),
                    FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b)");

        // Messages table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id UUID PRIMARY KEY,
                    conversation_id VARCHAR(100) NOT NULL,
                    sender_id UUID NOT NULL,
                    content VARCHAR(1000) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation_created "
                + "ON messages(conversation_id, created_at)");
    }

    /**
     * Creates social-related tables (friend_requests, notifications).
     * Consolidated from H2SocialStorage.
     */
    private void createSocialSchema(Statement stmt) throws SQLException {
        // Friend requests table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS friend_requests (
                    id UUID PRIMARY KEY,
                    from_user_id UUID NOT NULL,
                    to_user_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    responded_at TIMESTAMP,
                    FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_users "
                + "ON friend_requests(from_user_id, to_user_id, status)");

        // Notifications table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    type VARCHAR(30) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    message TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    is_read BOOLEAN DEFAULT FALSE,
                    data_json TEXT,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read)");
    }

    /**
     * Creates moderation-related tables (blocks, reports).
     * Consolidated from H2ModerationStorage.
     */
    private void createModerationSchema(Statement stmt) throws SQLException {
        // Blocks table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS blocks (
                    id UUID PRIMARY KEY,
                    blocker_id UUID NOT NULL,
                    blocked_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE (blocker_id, blocked_id),
                    FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_blocks_blocker ON blocks(blocker_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks(blocked_id)");

        // Reports table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    id UUID PRIMARY KEY,
                    reporter_id UUID NOT NULL,
                    reported_user_id UUID NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    description VARCHAR(500),
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE (reporter_id, reported_user_id),
                    FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_user_id)");
    }

    /**
     * Creates profile-related tables (profile_notes, profile_views).
     * Consolidated from H2ProfileDataStorage.
     */
    private void createProfileSchema(Statement stmt) throws SQLException {
        // Profile notes table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS profile_notes (
                    author_id UUID NOT NULL,
                    subject_id UUID NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (author_id, subject_id),
                    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (subject_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_notes_author ON profile_notes(author_id)");

        // Profile views table
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS profile_views (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    viewer_id UUID NOT NULL,
                    viewed_id UUID NOT NULL,
                    viewed_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (viewer_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (viewed_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewed_id ON profile_views(viewed_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewed_at ON profile_views(viewed_at DESC)");
    }

    /**
     * Creates standouts table for daily ranked matches.
     */
    private void createStandoutsSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS standouts (
                    id UUID PRIMARY KEY,
                    seeker_id UUID NOT NULL,
                    standout_user_id UUID NOT NULL,
                    featured_date DATE NOT NULL,
                    rank INT NOT NULL,
                    score INT NOT NULL,
                    reason VARCHAR(200) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    interacted_at TIMESTAMP,
                    FOREIGN KEY (seeker_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (standout_user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT uk_standouts_daily UNIQUE (seeker_id, standout_user_id, featured_date)
                )
                """);

        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_standouts_seeker_date ON standouts(seeker_id, featured_date DESC)");
    }

    /**
     * Creates undo states table for persisting undo operations across restarts.
     */
    private void createUndoStateSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS undo_states (
                    user_id UUID PRIMARY KEY,
                    like_id UUID NOT NULL,
                    who_likes UUID NOT NULL,
                    who_got_liked UUID NOT NULL,
                    direction VARCHAR(10) NOT NULL,
                    like_created_at TIMESTAMP NOT NULL,
                    match_id VARCHAR(100),
                    expires_at TIMESTAMP NOT NULL
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_undo_states_expires ON undo_states(expires_at)");
    }

    /**
     * Creates schema version tracking table for migration management.
     */
    private void createSchemaVersionTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL,
                    description VARCHAR(255)
                )
                """);
    }

    /**
     * Records a schema version if not already recorded.
     *
     * @param stmt        The statement to use
     * @param version     The schema version number
     * @param description Description of what this version includes
     */
    private void recordSchemaVersion(Statement stmt, int version, String description) throws SQLException {
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

    /**
     * Adds missing foreign key constraints to existing tables.
     * Uses IF NOT EXISTS to safely handle already-constrained databases.
     * Silently skips tables that don't exist yet (they'll be created with FKs by
     * their storage classes).
     */
    private void addMissingForeignKeys(Statement stmt) throws SQLException {
        // daily_pick_views - add FK to users
        addForeignKeyIfPresent(stmt, "daily_pick_views", "fk_daily_pick_views_user", "user_id", "users", "id");

        // user_achievements - add FK to users
        addForeignKeyIfPresent(stmt, "user_achievements", "fk_user_achievements_user", "user_id", "users", "id");

        // friend_requests - add FKs (table may not exist yet)
        addForeignKeyIfPresent(stmt, "friend_requests", "fk_friend_requests_from", "from_user_id", "users", "id");
        addForeignKeyIfPresent(stmt, "friend_requests", "fk_friend_requests_to", "to_user_id", "users", "id");

        // notifications - add FK to users
        addForeignKeyIfPresent(stmt, "notifications", "fk_notifications_user", "user_id", "users", "id");

        // blocks - add FKs
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocker", "blocker_id", "users", "id");
        addForeignKeyIfPresent(stmt, "blocks", "fk_blocks_blocked", "blocked_id", "users", "id");

        // reports - add FKs
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reporter", "reporter_id", "users", "id");
        addForeignKeyIfPresent(stmt, "reports", "fk_reports_reported", "reported_user_id", "users", "id");

        // conversations - add FKs (column names match table definition: user_a, user_b)
        addForeignKeyIfPresent(stmt, "conversations", "fk_conversations_user_a", "user_a", "users", "id");
        addForeignKeyIfPresent(stmt, "conversations", "fk_conversations_user_b", "user_b", "users", "id");

        // messages - add FKs
        addForeignKeyIfPresent(stmt, "messages", "fk_messages_sender", "sender_id", "users", "id");
        addForeignKeyIfPresent(stmt, "messages", "fk_messages_conversation", "conversation_id", "conversations", "id");

        // profile_notes - add FKs (column names match table definition: author_id, subject_id)
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_author", "author_id", "users", "id");
        addForeignKeyIfPresent(stmt, "profile_notes", "fk_profile_notes_subject", "subject_id", "users", "id");

        // profile_views - add FKs (column names match table definition: viewer_id, viewed_id)
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewer", "viewer_id", "users", "id");
        addForeignKeyIfPresent(stmt, "profile_views", "fk_profile_views_viewed", "viewed_id", "users", "id");
    }

    private void addForeignKeyIfPresent(
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
        }
    }

    /**
     * Checks if the SQLException indicates a missing table (H2 error code 42102).
     * More specific than substring matching to avoid masking real errors.
     */
    private static boolean isMissingTable(SQLException e) {
        // H2 uses SQL state 42S02 for "table not found"
        String sqlState = e.getSQLState();
        if ("42S02".equals(sqlState)) {
            return true;
        }
        // Fallback: H2 error code 42102 for base table/view not found
        int errorCode = e.getErrorCode();
        return errorCode == 42102;
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label + " cannot be null");
        if (!SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL " + label + ": " + value);
        }
        return value;
    }

    /**
     * Migrates schema by adding new columns for existing databases. Uses IF NOT
     * EXISTS to safely
     * handle already-migrated databases.
     */
    private void migrateSchemaColumns(Statement stmt) throws SQLException {
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS smoking VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS drinking VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS wants_kids VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS looking_for VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS education VARCHAR(20)");
        stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS height_cm INT");
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
    }

    /** Shuts down the database gracefully. */
    public void shutdown() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, getPassword());
                Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (SQLException e) {
            // Shutdown errors are expected when database is already closed
            // Log at finest level only for debugging purposes
            Logger logger = Logger.getLogger(DatabaseManager.class.getName());
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Database shutdown: " + e.getMessage());
            }
        }
    }
}
