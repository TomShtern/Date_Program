package datingapp.storage.schema;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Current fresh-install schema: creates the full SQL contract that new databases should land on
 * directly.
 *
 * <p>All methods remain idempotent via {@code IF NOT EXISTS} so re-running is safe. Historical
 * upgrade behavior for older databases still lives in {@link MigrationRunner}; this class defines
 * the clean baseline for fresh installs.
 */
public final class SchemaInitializer {

    static final String FLOAT64_SQL_TYPE = "DOUBLE PRECISION";

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
        createDailyPicksTable(stmt);
        createDailyPickViewsTable(stmt);
        createUserAchievementsTable(stmt);
        createMessagingSchema(stmt);
        createSocialSchema(stmt);
        createModerationSchema(stmt);
        createProfileSchema(stmt);
        createStandoutsSchema(stmt);
        createUndoStateSchema(stmt);
        createNormalizedProfileSchema(stmt);

        // Indexes & FKs
        createCoreIndexes(stmt);
        createStatsIndexes(stmt);
        createAdditionalIndexes(stmt);
    }

    // ═══════════════════════════════════════════════════════════════
    // Core tables
    // ═══════════════════════════════════════════════════════════════

    static void createUsersTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    bio VARCHAR(500),
                    birth_date DATE,
                    gender VARCHAR(20),
                    lat DOUBLE PRECISION,
                    lon DOUBLE PRECISION,
                    has_location_set BOOLEAN DEFAULT FALSE,
                    max_distance_km INT DEFAULT 50,
                    min_age INT DEFAULT 18,
                    max_age INT DEFAULT 99,
                    state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    smoking VARCHAR(20),
                    drinking VARCHAR(20),
                    wants_kids VARCHAR(20),
                    looking_for VARCHAR(20),
                    education VARCHAR(20),
                    height_cm INT,
                    db_min_height_cm INT,
                    db_max_height_cm INT,
                    db_max_age_diff INT,
                    email VARCHAR(200),
                    phone VARCHAR(50),
                    is_verified BOOLEAN,
                    verification_method VARCHAR(10),
                    verification_code VARCHAR(10),
                    verification_sent_at TIMESTAMP,
                    verified_at TIMESTAMP,
                    pace_messaging_frequency VARCHAR(30),
                    pace_time_to_first_date VARCHAR(30),
                    pace_communication_style VARCHAR(30),
                    pace_depth_preference VARCHAR(30),
                    deleted_at TIMESTAMP,
                    CONSTRAINT ck_users_state_values CHECK (
                        state IN ('INCOMPLETE', 'ACTIVE', 'PAUSED', 'BANNED')
                    ),
                    CONSTRAINT ck_users_gender_values CHECK (
                        gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')
                    ),
                    CONSTRAINT ck_users_smoking_values CHECK (
                        smoking IS NULL OR smoking IN ('NEVER', 'SOMETIMES', 'REGULARLY')
                    ),
                    CONSTRAINT ck_users_drinking_values CHECK (
                        drinking IS NULL OR drinking IN ('NEVER', 'SOCIALLY', 'REGULARLY')
                    ),
                    CONSTRAINT ck_users_wants_kids_values CHECK (
                        wants_kids IS NULL OR wants_kids IN ('NO', 'OPEN', 'SOMEDAY', 'HAS_KIDS')
                    ),
                    CONSTRAINT ck_users_looking_for_values CHECK (
                        looking_for IS NULL
                            OR looking_for IN ('CASUAL', 'SHORT_TERM', 'LONG_TERM', 'MARRIAGE', 'UNSURE')
                    ),
                    CONSTRAINT ck_users_education_values CHECK (
                        education IS NULL
                            OR education IN (
                                'HIGH_SCHOOL',
                                'SOME_COLLEGE',
                                'BACHELORS',
                                'MASTERS',
                                'PHD',
                                'TRADE_SCHOOL',
                                'OTHER'
                            )
                    ),
                    CONSTRAINT ck_users_verification_method_values CHECK (
                        verification_method IS NULL OR verification_method IN ('EMAIL', 'PHONE')
                    ),
                    CONSTRAINT ck_users_pace_msg_freq_values CHECK (
                        pace_messaging_frequency IS NULL
                            OR pace_messaging_frequency IN ('RARELY', 'OFTEN', 'CONSTANTLY', 'WILDCARD')
                    ),
                    CONSTRAINT ck_users_pace_first_date_values CHECK (
                        pace_time_to_first_date IS NULL
                            OR pace_time_to_first_date IN ('QUICKLY', 'FEW_DAYS', 'WEEKS', 'MONTHS', 'WILDCARD')
                    ),
                    CONSTRAINT ck_users_pace_comm_style_values CHECK (
                        pace_communication_style IS NULL
                            OR pace_communication_style IN (
                                'TEXT_ONLY',
                                'VOICE_NOTES',
                                'VIDEO_CALLS',
                                'IN_PERSON_ONLY',
                                'MIX_OF_EVERYTHING'
                            )
                    ),
                    CONSTRAINT ck_users_pace_depth_values CHECK (
                        pace_depth_preference IS NULL
                            OR pace_depth_preference IN ('SMALL_TALK', 'DEEP_CHAT', 'EXISTENTIAL', 'DEPENDS_ON_VIBE')
                    ),
                    CONSTRAINT ck_users_age_bounds CHECK (min_age <= max_age),
                    CONSTRAINT ck_users_height_bounds CHECK (
                        db_min_height_cm IS NULL
                            OR db_max_height_cm IS NULL
                            OR db_min_height_cm <= db_max_height_cm
                    ),
                    CONSTRAINT ck_users_max_age_diff_nonnegative CHECK (db_max_age_diff IS NULL OR db_max_age_diff >= 0)
                )
                """);
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
                            CONSTRAINT fk_likes_who_likes FOREIGN KEY (who_likes)
                                REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_likes_who_got_liked FOREIGN KEY (who_got_liked)
                                REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked),
                            CONSTRAINT ck_likes_direction_values CHECK (
                                direction IN ('LIKE', 'SUPER_LIKE', 'PASS')
                            ),
                            CONSTRAINT ck_likes_distinct_users CHECK (who_likes <> who_got_liked)
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
                    updated_at TIMESTAMP NOT NULL,
                    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    ended_at TIMESTAMP,
                    ended_by UUID,
                    end_reason VARCHAR(30),
                    deleted_at TIMESTAMP,
                    CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_matches_ended_by FOREIGN KEY (ended_by)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT uk_matches UNIQUE (user_a, user_b),
                    CONSTRAINT ck_matches_state_values CHECK (
                        state IN ('ACTIVE', 'FRIENDS', 'UNMATCHED', 'GRACEFUL_EXIT', 'BLOCKED')
                    ),
                    CONSTRAINT ck_matches_end_reason_values CHECK (
                        end_reason IS NULL OR end_reason IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK')
                    ),
                    CONSTRAINT ck_matches_id_length CHECK (CHAR_LENGTH(id) = 73),
                    CONSTRAINT ck_matches_ended_by_participant CHECK (
                        ended_by IS NULL OR ended_by = user_a OR ended_by = user_b
                    ),
                    CONSTRAINT ck_matches_distinct_users CHECK (user_a <> user_b)
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
                    like_ratio DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    total_swipes_received INT NOT NULL DEFAULT 0,
                    likes_received INT NOT NULL DEFAULT 0,
                    passes_received INT NOT NULL DEFAULT 0,
                    incoming_like_ratio DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    total_matches INT NOT NULL DEFAULT 0,
                    active_matches INT NOT NULL DEFAULT 0,
                    match_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    blocks_given INT NOT NULL DEFAULT 0,
                    blocks_received INT NOT NULL DEFAULT 0,
                    reports_given INT NOT NULL DEFAULT 0,
                    reports_received INT NOT NULL DEFAULT 0,
                    reciprocity_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    selectiveness_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                    attractiveness_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,
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
                    avg_likes_received DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    avg_likes_given DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    avg_match_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                    avg_like_ratio DOUBLE PRECISION NOT NULL DEFAULT 0.5
                )
                """);
    }

    // ═══════════════════════════════════════════════════════════════
    // Feature tables
    // ═══════════════════════════════════════════════════════════════

    static void createDailyPicksTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_picks (
                    user_id UUID NOT NULL,
                    pick_date DATE NOT NULL,
                    picked_user_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (user_id, pick_date),
                    CONSTRAINT fk_daily_picks_user FOREIGN KEY (user_id)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_daily_picks_picked_user FOREIGN KEY (picked_user_id)
                        REFERENCES users(id) ON DELETE CASCADE
                )
                """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_picks_pick_date ON daily_picks(pick_date)");
    }

    static void createDailyPickViewsTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_pick_views (
                    user_id UUID NOT NULL,
                    viewed_date DATE NOT NULL,
                    viewed_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (user_id, viewed_date),
                    CONSTRAINT fk_daily_pick_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
                    UNIQUE (user_id, achievement),
                    CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id)
                        REFERENCES users(id) ON DELETE CASCADE
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
                    CONSTRAINT uk_conversation_users UNIQUE (user_a, user_b),
                        CONSTRAINT ck_conversations_archive_reason_a_values CHECK (
                        archive_reason_a IS NULL
                            OR archive_reason_a IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK')
                        ),
                        CONSTRAINT ck_conversations_archive_reason_b_values CHECK (
                        archive_reason_b IS NULL
                            OR archive_reason_b IN ('FRIEND_ZONE', 'GRACEFUL_EXIT', 'UNMATCH', 'BLOCK')
                        ),
                        CONSTRAINT ck_conversations_id_length CHECK (CHAR_LENGTH(id) = 73),
                        CONSTRAINT ck_conversations_distinct_users CHECK (user_a <> user_b),
                        FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                        + "ON conversations(user_a, last_message_at DESC) WHERE deleted_at IS NULL",
                "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                        + "ON conversations(user_a, last_message_at DESC)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                        + "ON conversations(user_b, last_message_at DESC) WHERE deleted_at IS NULL",
                "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                        + "ON conversations(user_b, last_message_at DESC)");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id UUID PRIMARY KEY,
                    conversation_id VARCHAR(100) NOT NULL,
                    sender_id UUID NOT NULL,
                    content VARCHAR(1000) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted_at TIMESTAMP,
                    CONSTRAINT ck_messages_conversation_id_length CHECK (CHAR_LENGTH(conversation_id) = 73),
                    CONSTRAINT ck_messages_content_nonblank CHECK (CHAR_LENGTH(TRIM(content)) > 0),
                    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation_created "
                + "ON messages(conversation_id, created_at)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_messages_sender_created "
                        + "ON messages(sender_id, created_at DESC) WHERE deleted_at IS NULL",
                "CREATE INDEX IF NOT EXISTS idx_messages_sender_created " + "ON messages(sender_id, created_at DESC)");
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
                    pair_key VARCHAR(73),
                    pending_marker VARCHAR(10),
                    CONSTRAINT ck_friend_requests_status_values CHECK (
                        status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')
                    ),
                    CONSTRAINT ck_friend_requests_distinct_users CHECK (from_user_id <> to_user_id),
                    FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_users "
                + "ON friend_requests(from_user_id, to_user_id, status)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_requests_pending_pair "
                + "ON friend_requests(pair_key, pending_marker)");

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
                    CONSTRAINT ck_notifications_title_nonblank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
                    CONSTRAINT ck_notifications_message_nonblank CHECK (CHAR_LENGTH(TRIM(message)) > 0),
                    CONSTRAINT ck_notifications_type_values CHECK (
                        type IN (
                            'MATCH_FOUND',
                            'NEW_MESSAGE',
                            'FRIEND_REQUEST',
                            'FRIEND_REQUEST_ACCEPTED',
                            'GRACEFUL_EXIT'
                        )
                    ),
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
                    CONSTRAINT ck_blocks_distinct_users CHECK (blocker_id <> blocked_id),
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
                    CONSTRAINT ck_reports_reason_values CHECK (
                        reason IN ('SPAM', 'INAPPROPRIATE_CONTENT', 'HARASSMENT', 'FAKE_PROFILE', 'UNDERAGE', 'OTHER')
                    ),
                    CONSTRAINT ck_reports_distinct_users CHECK (reporter_id <> reported_user_id),
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
                    deleted_at TIMESTAMP,
                    PRIMARY KEY (author_id, subject_id),
                    CONSTRAINT ck_profile_notes_content_nonblank CHECK (CHAR_LENGTH(TRIM(content)) > 0),
                    CONSTRAINT ck_profile_notes_distinct_users CHECK (author_id <> subject_id),
                    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (subject_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_notes_author ON profile_notes(author_id)");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS profile_views (
                    viewer_id UUID NOT NULL,
                    viewed_id UUID NOT NULL,
                    viewed_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (viewer_id, viewed_id, viewed_at),
                    CONSTRAINT ck_profile_views_distinct_users CHECK (viewer_id <> viewed_id),
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

    /**
     * Creates normalized junction tables for multi-value profile fields (photos, interests,
     * gender preferences, dealbreakers). Fresh installs use these tables directly rather than
     * recreating the old serialized profile columns.
     */
    static void createNormalizedProfileSchema(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_photos (
                    user_id UUID NOT NULL,
                    position INT NOT NULL,
                    url VARCHAR(500) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, position),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_interests (
                    user_id UUID NOT NULL,
                    interest VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, interest),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_interested_in (
                    user_id UUID NOT NULL,
                    gender VARCHAR(30) NOT NULL,
                    PRIMARY KEY (user_id, gender),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_db_smoking (
                    user_id UUID NOT NULL,
                    "value" VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, "value"),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_db_drinking (
                    user_id UUID NOT NULL,
                    "value" VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, "value"),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_db_wants_kids (
                    user_id UUID NOT NULL,
                    "value" VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, "value"),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_db_looking_for (
                    user_id UUID NOT NULL,
                    "value" VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, "value"),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_db_education (
                    user_id UUID NOT NULL,
                    "value" VARCHAR(50) NOT NULL,
                    PRIMARY KEY (user_id, "value"),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);

        // Reverse lookup indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_interests_interest ON user_interests(interest)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_interested_in_gender ON user_interested_in(gender)");
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
                    expires_at TIMESTAMP NOT NULL,
                    CONSTRAINT ck_undo_states_direction_values CHECK (direction IN ('LIKE', 'SUPER_LIKE', 'PASS')),
                    CONSTRAINT ck_undo_states_match_id_length CHECK (match_id IS NULL OR CHAR_LENGTH(match_id) = 73)
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
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_likes_direction_created "
                        + "ON likes(direction, created_at DESC) WHERE deleted_at IS NULL",
                "CREATE INDEX IF NOT EXISTS idx_likes_direction_created " + "ON likes(direction, created_at DESC)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_likes_received_created "
                        + "ON likes(who_got_liked, created_at DESC) WHERE deleted_at IS NULL",
                "CREATE INDEX IF NOT EXISTS idx_likes_received_created " + "ON likes(who_got_liked, created_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_a ON matches(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_user_b ON matches(user_b)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_matches_state ON matches(state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON swipe_sessions(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_active ON swipe_sessions(user_id, state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_started_at ON swipe_sessions(user_id, started_at)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc "
                        + "ON swipe_sessions(started_at DESC) WHERE state = 'ACTIVE'",
                "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc " + "ON swipe_sessions(started_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_state ON users(state)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_gender_state ON users(gender, state)");
    }

    static void createStatsIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_user_id ON user_stats(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed ON user_stats(user_id, computed_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed_desc ON user_stats(computed_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_platform_stats_computed_at ON platform_stats(computed_at DESC)");
    }

    static void createAdditionalIndexes(Statement stmt) throws SQLException {
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_pick_views_date ON daily_pick_views(viewed_date)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_pick_views_user ON daily_pick_views(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_achievements_user_id ON user_achievements(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_last_msg ON conversations(last_message_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_user ON friend_requests(to_user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewer ON profile_views(viewer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_status ON friend_requests(to_user_id, status)");
        createIndexWithFallback(
                stmt,
                "CREATE INDEX IF NOT EXISTS idx_standouts_interacted_at "
                        + "ON standouts(seeker_id, interacted_at DESC) WHERE interacted_at IS NOT NULL",
                "CREATE INDEX IF NOT EXISTS idx_standouts_interacted_at "
                        + "ON standouts(seeker_id, interacted_at DESC)");
    }

    private static void createIndexWithFallback(Statement stmt, String preferredSql, String fallbackSql)
            throws SQLException {
        try {
            stmt.execute(preferredSql);
        } catch (SQLException preferredFailure) {
            try {
                stmt.execute(fallbackSql);
            } catch (SQLException fallbackFailure) {
                fallbackFailure.addSuppressed(preferredFailure);
                throw fallbackFailure;
            }
        }
    }
}
