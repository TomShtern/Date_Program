package datingapp.storage.schema;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates all database tables, indexes, and foreign key constraints. Extracted
 * from {@code
 * DatabaseManager} for single-responsibility: this class owns DDL, while
 * {@code DatabaseManager}
 * owns connection pooling and lifecycle.
 *
 * <p>
 * Every method is idempotent — uses {@code IF NOT EXISTS} so re-running is
 * safe.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {
        // Utility class — static methods only
    }

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates all application tables, indexes, and foreign keys using the given
     * JDBC statement.
     * Order matters: core tables first (users), then dependent tables, then
     * indexes/FKs.
     *
     * @param stmt a JDBC statement connected to the target database
     * @throws SQLException if any DDL statement fails
     */
    public static void createAllTables(Statement stmt) throws SQLException {
        // Core tables (order matters — users first due to FK references)
        createUsersTable(stmt);
        createLikesTable(stmt);
        createMatchesTable(stmt);
        createSwipeSessionsTable(stmt);

        // Stats tables
        createUserStatsTable(stmt);
        createPlatformStatsTable(stmt);

        // Feature tables
        createDailyPickViewsTable(stmt);
        createUserAchievementsTable(stmt);
        createMessagingSchema(stmt);
        createSocialSchema(stmt);
        createModerationSchema(stmt);
        createProfileSchema(stmt);
        createStandoutsSchema(stmt);
        createUndoStateSchema(stmt);

        // Indexes & FKs
        createCoreIndexes(stmt);
        createStatsIndexes(stmt);
        createAdditionalIndexes(stmt);
    }

    // ═══════════════════════════════════════════════════════════════
    // Core tables
    // ═══════════════════════════════════════════════════════════════

    static void createUsersTable(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id UUID PRIMARY KEY, "
                + "name VARCHAR(100) NOT NULL, "
                + "bio VARCHAR(500), "
                + "birth_date DATE, "
                + "gender VARCHAR(20), "
                + "interested_in VARCHAR(100), "
                + "lat DOUBLE, "
                + "lon DOUBLE, "
                + "has_location_set BOOLEAN DEFAULT FALSE, "
                + "max_distance_km INT DEFAULT 50, "
                + "min_age INT DEFAULT 18, "
                + "max_age INT DEFAULT 99, "
                + "photo_urls VARCHAR(1000), "
                + "state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE', "
                + "created_at TIMESTAMP NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL, "
                + "smoking VARCHAR(20), "
                + "drinking VARCHAR(20), "
                + "wants_kids VARCHAR(20), "
                + "looking_for VARCHAR(20), "
                + "education VARCHAR(20), "
                + "height_cm INT, "
                + "db_smoking VARCHAR(100), "
                + "db_drinking VARCHAR(100), "
                + "db_wants_kids VARCHAR(100), "
                + "db_looking_for VARCHAR(100), "
                + "db_education VARCHAR(200), "
                + "db_min_height_cm INT, "
                + "db_max_height_cm INT, "
                + "db_max_age_diff INT, "
                + "interests VARCHAR(500), "
                + "email VARCHAR(200), "
                + "phone VARCHAR(50), "
                + "is_verified BOOLEAN, "
                + "verification_method VARCHAR(10), "
                + "verification_code VARCHAR(10), "
                + "verification_sent_at TIMESTAMP, "
                + "verified_at TIMESTAMP, "
                + "pace_messaging_frequency VARCHAR(30), "
                + "pace_time_to_first_date VARCHAR(30), "
                + "pace_communication_style VARCHAR(30), "
                + "pace_depth_preference VARCHAR(30), "
                + "deleted_at TIMESTAMP"
                + ")");
    }

    static void createLikesTable(Statement stmt) throws SQLException {
        stmt.execute("""
                        CREATE TABLE IF NOT EXISTS likes (
                            id UUID PRIMARY KEY,
                            who_likes UUID NOT NULL,
                            who_got_liked UUID NOT NULL,
                            direction VARCHAR(10) NOT NULL,
                            created_at TIMESTAMP NOT NULL,
                            deleted_at TIMESTAMP,
                            CONSTRAINT fk_likes_who_likes FOREIGN KEY (who_likes) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_likes_who_got_liked FOREIGN KEY (who_got_liked) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked)
                        )
                        """);
    }

    static void createMatchesTable(Statement stmt) throws SQLException {
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
                    deleted_at TIMESTAMP,
                    CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT uk_matches UNIQUE (user_a, user_b)
                )
                """);
    }

    static void createSwipeSessionsTable(Statement stmt) throws SQLException {
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
    }

    // ═══════════════════════════════════════════════════════════════
    // Stats tables
    // ═══════════════════════════════════════════════════════════════

    static void createUserStatsTable(Statement stmt) throws SQLException {
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
    }

    static void createPlatformStatsTable(Statement stmt) throws SQLException {
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
    }

    // ═══════════════════════════════════════════════════════════════
    // Feature tables
    // ═══════════════════════════════════════════════════════════════

    static void createDailyPickViewsTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_pick_views (
                    user_id UUID NOT NULL,
                    viewed_date DATE NOT NULL,
                    viewed_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (user_id, viewed_date)
                )
                """);
    }

    static void createUserAchievementsTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_achievements (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    achievement VARCHAR(50) NOT NULL,
                    unlocked_at TIMESTAMP NOT NULL,
                    UNIQUE (user_id, achievement)
                )
                """);
    }

    // ═══════════════════════════════════════════════════════════════
    // Composite schemas (multi-table features)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates messaging-related tables (conversations, messages). Conversations and
     * messages are
     * retained after match ends (soft delete model).
     */
    static void createMessagingSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id VARCHAR(100) PRIMARY KEY,
                    user_a UUID NOT NULL,
                    user_b UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    last_message_at TIMESTAMP,
                    user_a_last_read_at TIMESTAMP,
                    user_b_last_read_at TIMESTAMP,
                    archived_at_a TIMESTAMP,
                    archive_reason_a VARCHAR(20),
                    archived_at_b TIMESTAMP,
                    archive_reason_b VARCHAR(20),
                    visible_to_user_a BOOLEAN DEFAULT TRUE,
                    visible_to_user_b BOOLEAN DEFAULT TRUE,
                    deleted_at TIMESTAMP,
                    CONSTRAINT unq_conversation_users UNIQUE (user_a, user_b),
                    FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b)");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id UUID PRIMARY KEY,
                    conversation_id VARCHAR(100) NOT NULL,
                    sender_id UUID NOT NULL,
                    content VARCHAR(1000) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP,
                    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation_created "
                + "ON messages(conversation_id, created_at)");
    }

    /** Creates social-related tables (friend_requests, notifications). */
    static void createSocialSchema(Statement stmt) throws SQLException {
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

    /** Creates moderation-related tables (blocks, reports). */
    static void createModerationSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS blocks (
                    id UUID PRIMARY KEY,
                    blocker_id UUID NOT NULL,
                    blocked_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP,
                    UNIQUE (blocker_id, blocked_id),
                    FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_blocks_blocker ON blocks(blocker_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks(blocked_id)");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                    id UUID PRIMARY KEY,
                    reporter_id UUID NOT NULL,
                    reported_user_id UUID NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    description VARCHAR(500),
                    created_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP,
                    UNIQUE (reporter_id, reported_user_id),
                    FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_user_id)");
    }

    /** Creates profile-related tables (profile_notes, profile_views). */
    static void createProfileSchema(Statement stmt) throws SQLException {
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

    /** Creates standouts table for daily ranked matches. */
    static void createStandoutsSchema(Statement stmt) throws SQLException {
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

    /** Creates undo states table for persisting undo operations across restarts. */
    static void createUndoStateSchema(Statement stmt) throws SQLException {
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

    // ═══════════════════════════════════════════════════════════════
    // Indexes
    // ═══════════════════════════════════════════════════════════════

    static void createCoreIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_who_likes ON likes(who_likes)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_who_got_liked ON likes(who_got_liked)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_a ON matches(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_b ON matches(user_b)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_state ON matches(state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON swipe_sessions(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_active ON swipe_sessions(user_id, state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON swipe_sessions(user_id, started_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_state ON users(state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_gender_state ON users(gender, state)");
    }

    static void createStatsIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_user_id ON user_stats(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed ON user_stats(user_id, computed_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_platform_stats_computed_at ON platform_stats(computed_at DESC)");
    }

    static void createAdditionalIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_pick_views_date ON daily_pick_views(viewed_date)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_achievements_user_id ON user_achievements(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_last_msg ON conversations(last_message_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_user ON friend_requests(to_user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_picks_user ON daily_pick_views(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewer ON profile_views(viewer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_status ON friend_requests(to_user_id, status)");
    }
}
