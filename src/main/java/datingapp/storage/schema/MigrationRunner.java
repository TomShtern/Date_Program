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
 * <p>Fresh databases bootstrap the current baseline directly, then record every historical version as
 * covered in {@code schema_version}. Upgraded databases skip already-applied versions (tracked in
 * {@code schema_version}) and execute only what remains. Both paths should converge on an identical
 * final schema.
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
    private static final String FRESH_BASELINE_DESCRIPTION_PREFIX = "Included in fresh baseline: ";
    private static final String WHERE_NOT_DELETED = " WHERE deleted_at IS NULL";
    private static final String TABLE_MATCHES = "MATCHES";
    private static final String SQL_TABLE_MATCHES = "matches";
    private static final String TABLE_USERS = "USERS";
    private static final String TABLE_CONVERSATIONS = "CONVERSATIONS";
    private static final String TABLE_MESSAGES = "MESSAGES";
    private static final String TABLE_UNDO_STATES = "UNDO_STATES";
    private static final String TABLE_LIKES = "LIKES";
    private static final String TABLE_FRIEND_REQUESTS = "FRIEND_REQUESTS";
    private static final String TABLE_REPORTS = "REPORTS";
    private static final String TABLE_PROFILE_NOTES = "PROFILE_NOTES";
    private static final String TABLE_PROFILE_VIEWS = "PROFILE_VIEWS";
    private static final String TABLE_BLOCKS = "BLOCKS";
    private static final String TABLE_STANDOUTS = "STANDOUTS";
    private static final String TABLE_SWIPE_SESSIONS = "SWIPE_SESSIONS";
    private static final String TABLE_USER_STATS = "USER_STATS";
    private static final String TABLE_PLATFORM_STATS = "PLATFORM_STATS";
    private static final String TABLE_DAILY_PICKS = "DAILY_PICKS";
    private static final String TABLE_DAILY_PICK_VIEWS = "DAILY_PICK_VIEWS";
    private static final String TABLE_USER_ACHIEVEMENTS = "USER_ACHIEVEMENTS";
    private static final String TABLE_USER_PHOTOS = "USER_PHOTOS";
    private static final String TABLE_SCHEMA_VERSION = "SCHEMA_VERSION";
    private static final String TABLE_NOTIFICATIONS = "notifications";
    private static final String SQL_SELECT_COUNT_FROM = "SELECT COUNT(*) FROM ";
    private static final String SQL_WHERE_FRAGMENT = " WHERE ";
    private static final String SQL_CREATE_IDX_CONVERSATIONS_USER_A_LAST_MSG =
            "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a, last_message_at DESC)";
    private static final String SQL_CREATE_IDX_CONVERSATIONS_USER_B_LAST_MSG =
            "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b, last_message_at DESC)";
    private static final String SQL_ALTER_TABLE_PREFIX = "ALTER TABLE ";
    private static final String SQL_ALTER_COLUMN_FRAGMENT = " ALTER COLUMN ";
    private static final String SQL_ADD_CONSTRAINT_FRAGMENT = " ADD CONSTRAINT ";
    private static final String COLUMN_STATE = "state";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_DELETED_AT = "deleted_at";
    private static final String VALUE_ACTIVE = "ACTIVE";
    private static final String VALUE_NEVER = "NEVER";
    private static final String VALUE_REGULARLY = "REGULARLY";
    private static final String VALUE_OTHER = "OTHER";
    private static final String VALUE_GRACEFUL_EXIT = "GRACEFUL_EXIT";
    private static final String VALUE_FRIEND_ZONE = "FRIEND_ZONE";
    private static final String VALUE_UNMATCH = "UNMATCH";
    private static final String VALUE_BLOCK = "BLOCK";

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
     * Ordered registry of all schema migrations. Fresh databases bootstrap the latest baseline and
     * record these versions as covered; upgraded databases execute only the versions that are still
     * pending.
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
                    MigrationRunner::applyV11),
            new VersionedMigration(
                    12,
                    "Add safe enum-backed checks and deterministic pair-ID length constraints",
                    MigrationRunner::applyV12),
            new VersionedMigration(
                    13,
                    "Enforce matches.ended_by integrity against users and match participants",
                    MigrationRunner::applyV13),
            new VersionedMigration(
                    14,
                    "Converge legacy databases on fresh-baseline structural checks and nonblank text constraints",
                    MigrationRunner::applyV14),
            new VersionedMigration(
                    15,
                    "Add remaining enum-backed checks for swipe sessions and normalized profile preference tables",
                    MigrationRunner::applyV15),
            new VersionedMigration(
                    16,
                    "Realign conversation activity indexes with current query shapes and drop redundant message conversation index",
                    MigrationRunner::applyV16),
            new VersionedMigration(
                    17,
                    "Convert event and audit timestamps to TIMESTAMP WITH TIME ZONE using UTC semantics for legacy data",
                    MigrationRunner::applyV17),
            new VersionedMigration(
                    18,
                    "Normalize and enforce unique email/phone verification contacts in users",
                    MigrationRunner::applyV18));

    // ═══════════════════════════════════════════════════════════════
    // Public entry point
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs all pending migrations in version order. Each migration is applied exactly once.
     * Fresh-database bootstraps use the direct baseline path; existing databases execute pending
     * migrations incrementally.
     *
     * @param stmt a JDBC statement connected to the target database
     * @throws SQLException if any migration statement fails
     */
    public static void runAllPending(Statement stmt) throws SQLException {
        boolean freshDatabase = isFreshApplicationSchema(stmt);
        createSchemaVersionTable(stmt);

        if (freshDatabase) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Applying current fresh baseline schema without replaying historical migrations");
            }
            applyV1(stmt);
            recordFreshBaselineCoverage(stmt);
            return;
        }

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
        if (!hasPrimaryKey(stmt, TABLE_PROFILE_VIEWS)) {
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
        if (!hasTable(stmt, TABLE_MESSAGES)) {
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
        if (hasTable(stmt, TABLE_LIKES)) {
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

        if (hasTable(stmt, TABLE_CONVERSATIONS)) {
            if (hasColumn(stmt, TABLE_CONVERSATIONS, "LAST_MESSAGE_AT")) {
                createIndexWithFallback(
                        stmt,
                        SQL_CREATE_IDX_CONVERSATIONS_USER_A_LAST_MSG + WHERE_NOT_DELETED,
                        SQL_CREATE_IDX_CONVERSATIONS_USER_A_LAST_MSG);
                createIndexWithFallback(
                        stmt,
                        SQL_CREATE_IDX_CONVERSATIONS_USER_B_LAST_MSG + WHERE_NOT_DELETED,
                        SQL_CREATE_IDX_CONVERSATIONS_USER_B_LAST_MSG);
            } else {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b)");
            }
        }

        if (hasTable(stmt, TABLE_MESSAGES)) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages(sender_id, created_at DESC)"
                            + WHERE_NOT_DELETED,
                    "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages(sender_id, created_at DESC)");
        }

        if (hasTable(stmt, TABLE_SWIPE_SESSIONS)) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc ON swipe_sessions(started_at DESC)"
                            + " WHERE state = 'ACTIVE'",
                    "CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc ON swipe_sessions(started_at DESC)");
        }

        if (hasTable(stmt, TABLE_USER_STATS)) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed_desc ON user_stats(computed_at DESC)");
        }

        if (hasTable(stmt, TABLE_STANDOUTS)) {
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
        if (hasTable(stmt, TABLE_DAILY_PICK_VIEWS)) {
            addForeignKeyIfMissing(stmt, "daily_pick_views", "fk_daily_pick_views_user", "user_id", "users", "id");
        }
        if (hasTable(stmt, TABLE_USER_ACHIEVEMENTS)) {
            addForeignKeyIfMissing(stmt, "user_achievements", "fk_user_achievements_user", "user_id", "users", "id");
        }
    }

    /**
     * V11 migration: adds helper columns and a uniqueness invariant so only one
     * pending friend request can exist per user pair while preserving accepted/rejected
     * history rows.
     */
    private static void applyV11(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_FRIEND_REQUESTS)) {
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

    /**
     * V12 migration: adds safe enum-backed checks plus deterministic ID length
     * constraints that preserve current behavior while tightening database invariants.
     */
    private static void applyV12(Statement stmt) throws SQLException {
        applyUserValueConstraints(stmt);
        applyInteractionValueConstraints(stmt);
    }

    /**
     * V13 migration: ensures {@code matches.ended_by} references a real user and,
     * when present, one of the match participants.
     */
    private static void applyV13(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_MATCHES) || !hasColumn(stmt, TABLE_MATCHES, "ENDED_BY")) {
            return;
        }

        long invalidParticipants = countInvalidMatchEnders(stmt);
        if (invalidParticipants > 0) {
            throw new SQLException("Cannot enforce matches ended_by participant integrity: "
                    + invalidParticipants
                    + " row(s) have ended_by outside the matched pair");
        }

        addForeignKeyIfMissing(stmt, SQL_TABLE_MATCHES, "fk_matches_ended_by", "ended_by", "users", "id");
        addCheckConstraintIfMissing(
                stmt,
                SQL_TABLE_MATCHES,
                "ck_matches_ended_by_participant",
                "ended_by IS NULL OR ended_by = user_a OR ended_by = user_b");
    }

    /**
     * V14 migration: adds remaining fresh-baseline structural checks to upgraded databases and
     * enforces nonblank user-facing text where the domain already requires it.
     */
    private static void applyV14(Statement stmt) throws SQLException {
        applyFreshBaselineStructuralConstraints(stmt);
        applyNonBlankTextConstraints(stmt);
    }

    /**
     * V15 migration: adds remaining enum-backed checks that fresh installs already
     * expect on swipe sessions and normalized profile preference tables.
     */
    private static void applyV15(Statement stmt) throws SQLException {
        applySwipeSessionValueConstraints(stmt);
        applyNormalizedProfileValueConstraints(stmt);
    }

    /**
     * V16 migration: drop the redundant standalone messages(conversation_id) index and rebuild
     * conversation list indexes so they match the visible-conversation query path more closely.
     */
    private static void applyV16(Statement stmt) throws SQLException {
        if (hasTable(stmt, TABLE_MESSAGES)) {
            stmt.execute("DROP INDEX IF EXISTS idx_messages_conversation_id");
        }

        if (!hasTable(stmt, TABLE_CONVERSATIONS)) {
            return;
        }

        rebuildConversationActivityIndexes(stmt);
    }

    private static void applyV17(Statement stmt) throws SQLException {
        alterTimestampColumnsToTimeZone(
                stmt,
                "users",
                COLUMN_CREATED_AT,
                COLUMN_UPDATED_AT,
                "verification_sent_at",
                "verified_at",
                COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_LIKES, COLUMN_CREATED_AT, COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(
                stmt, SQL_TABLE_MATCHES, COLUMN_CREATED_AT, COLUMN_UPDATED_AT, "ended_at", COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_SWIPE_SESSIONS, "started_at", "last_activity_at", "ended_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_USER_STATS, "computed_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_PLATFORM_STATS, "computed_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_DAILY_PICKS, COLUMN_CREATED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_DAILY_PICK_VIEWS, "viewed_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_USER_ACHIEVEMENTS, "unlocked_at");
        alterTimestampColumnsToTimeZone(
                stmt,
                "conversations",
                COLUMN_CREATED_AT,
                "last_message_at",
                "user_a_last_read_at",
                "user_b_last_read_at",
                "archived_at_a",
                "archived_at_b",
                COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_MESSAGES, COLUMN_CREATED_AT, COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_FRIEND_REQUESTS, COLUMN_CREATED_AT, "responded_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_NOTIFICATIONS, COLUMN_CREATED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_BLOCKS, COLUMN_CREATED_AT, COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_REPORTS, COLUMN_CREATED_AT, COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(
                stmt, TABLE_PROFILE_NOTES, COLUMN_CREATED_AT, COLUMN_UPDATED_AT, COLUMN_DELETED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_PROFILE_VIEWS, "viewed_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_STANDOUTS, COLUMN_CREATED_AT, "interacted_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_USER_PHOTOS, COLUMN_CREATED_AT);
        alterTimestampColumnsToTimeZone(stmt, TABLE_UNDO_STATES, "like_created_at", "expires_at");
        alterTimestampColumnsToTimeZone(stmt, TABLE_SCHEMA_VERSION, "applied_at");
    }

    private static void applyV18(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_USERS)) {
            return;
        }

        if (hasColumn(stmt, TABLE_USERS, "EMAIL")) {
            stmt.execute("UPDATE users SET email = NULL WHERE email IS NOT NULL AND TRIM(email) = ''");
            stmt.execute("UPDATE users SET email = TRIM(email) WHERE email IS NOT NULL");
            long duplicateEmails = countDuplicateTrimmedValues(stmt, "users", "email");
            if (duplicateEmails > 0) {
                throw new SQLException("Cannot enforce unique users.email values: " + duplicateEmails
                        + " duplicate trimmed value(s) exist");
            }
            addCheckConstraintIfMissing(
                    stmt, "users", "ck_users_email_trimmed", "email IS NULL OR email = TRIM(email)");
            addUniqueConstraintIfMissing(stmt, "users", "uk_users_email", "email");
        }

        if (hasColumn(stmt, TABLE_USERS, "PHONE")) {
            stmt.execute("UPDATE users SET phone = NULL WHERE phone IS NOT NULL AND TRIM(phone) = ''");
            stmt.execute("UPDATE users SET phone = TRIM(phone) WHERE phone IS NOT NULL");
            long duplicatePhones = countDuplicateTrimmedValues(stmt, "users", "phone");
            if (duplicatePhones > 0) {
                throw new SQLException("Cannot enforce unique users.phone values: " + duplicatePhones
                        + " duplicate trimmed value(s) exist");
            }
            addCheckConstraintIfMissing(
                    stmt, "users", "ck_users_phone_trimmed", "phone IS NULL OR phone = TRIM(phone)");
            addUniqueConstraintIfMissing(stmt, "users", "uk_users_phone", "phone");
        }
    }

    private static void rebuildConversationActivityIndexes(Statement stmt) throws SQLException {
        stmt.execute("DROP INDEX IF EXISTS idx_conversations_user_a_last_msg");
        stmt.execute("DROP INDEX IF EXISTS idx_conversations_user_b_last_msg");

        boolean hasLastMessageAt = hasColumn(stmt, TABLE_CONVERSATIONS, "LAST_MESSAGE_AT");
        boolean hasCreatedAt = hasColumn(stmt, TABLE_CONVERSATIONS, "CREATED_AT");
        boolean hasDeletedAt = hasColumn(stmt, TABLE_CONVERSATIONS, "DELETED_AT");
        boolean hasVisibleToUserA = hasColumn(stmt, TABLE_CONVERSATIONS, "VISIBLE_TO_USER_A");
        boolean hasVisibleToUserB = hasColumn(stmt, TABLE_CONVERSATIONS, "VISIBLE_TO_USER_B");

        if (hasLastMessageAt && hasCreatedAt && hasVisibleToUserA && hasVisibleToUserB && hasDeletedAt) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                            + "ON conversations(user_a, last_message_at DESC, created_at DESC, id DESC) "
                            + "WHERE deleted_at IS NULL AND visible_to_user_a = TRUE",
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                            + "ON conversations(user_a, visible_to_user_a, deleted_at, last_message_at DESC, created_at DESC, id DESC)");
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                            + "ON conversations(user_b, last_message_at DESC, created_at DESC, id DESC) "
                            + "WHERE deleted_at IS NULL AND visible_to_user_b = TRUE",
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                            + "ON conversations(user_b, visible_to_user_b, deleted_at, last_message_at DESC, created_at DESC, id DESC)");
            return;
        }

        if (hasLastMessageAt && hasCreatedAt && hasDeletedAt) {
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                            + "ON conversations(user_a, last_message_at DESC, created_at DESC, id DESC) WHERE deleted_at IS NULL",
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg "
                            + "ON conversations(user_a, deleted_at, last_message_at DESC, created_at DESC, id DESC)");
            createIndexWithFallback(
                    stmt,
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                            + "ON conversations(user_b, last_message_at DESC, created_at DESC, id DESC) WHERE deleted_at IS NULL",
                    "CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg "
                            + "ON conversations(user_b, deleted_at, last_message_at DESC, created_at DESC, id DESC)");
            return;
        }

        if (hasLastMessageAt) {
            createIndexWithFallback(
                    stmt,
                    SQL_CREATE_IDX_CONVERSATIONS_USER_A_LAST_MSG + (hasDeletedAt ? WHERE_NOT_DELETED : ""),
                    SQL_CREATE_IDX_CONVERSATIONS_USER_A_LAST_MSG);
            createIndexWithFallback(
                    stmt,
                    SQL_CREATE_IDX_CONVERSATIONS_USER_B_LAST_MSG + (hasDeletedAt ? WHERE_NOT_DELETED : ""),
                    SQL_CREATE_IDX_CONVERSATIONS_USER_B_LAST_MSG);
            return;
        }

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b)");
    }

    private static void alterTimestampColumnsToTimeZone(Statement stmt, String tableName, String... columnNames)
            throws SQLException {
        if (!hasTable(stmt, tableName)) {
            return;
        }

        for (String columnName : columnNames) {
            if (hasColumn(stmt, tableName, columnName)) {
                alterTimestampColumnToTimeZone(stmt, tableName, columnName);
            }
        }
    }

    private static void alterTimestampColumnToTimeZone(Statement stmt, String tableName, String columnName)
            throws SQLException {
        String postgresSql = SQL_ALTER_TABLE_PREFIX + tableName + SQL_ALTER_COLUMN_FRAGMENT + columnName
                + " TYPE TIMESTAMP WITH TIME ZONE USING " + columnName + " AT TIME ZONE 'UTC'";
        try {
            stmt.execute(postgresSql);
        } catch (SQLException postgresFailure) {
            try {
                stmt.execute(SQL_ALTER_TABLE_PREFIX + tableName + SQL_ALTER_COLUMN_FRAGMENT + columnName
                        + " TIMESTAMP WITH TIME ZONE");
            } catch (SQLException firstFallbackFailure) {
                try {
                    stmt.execute(SQL_ALTER_TABLE_PREFIX + tableName + SQL_ALTER_COLUMN_FRAGMENT + columnName
                            + " SET DATA TYPE TIMESTAMP WITH TIME ZONE");
                } catch (SQLException secondFallbackFailure) {
                    secondFallbackFailure.addSuppressed(firstFallbackFailure);
                    secondFallbackFailure.addSuppressed(postgresFailure);
                    throw secondFallbackFailure;
                }
            }
        }
    }

    private static void repairMatchesUpdatedAtColumn(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_MATCHES)) {
            return;
        }
        stmt.execute("ALTER TABLE matches ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
        stmt.execute("UPDATE matches SET updated_at = COALESCE(updated_at, created_at)");
    }

    private static boolean isFreshApplicationSchema(Statement stmt) throws SQLException {
        String currentSchema = stmt.getConnection().getSchema();
        String metadataTableName = metadataIdentifier(stmt.getConnection(), "users");
        String metadataSchema = currentSchema == null ? null : metadataIdentifier(stmt.getConnection(), currentSchema);
        try (ResultSet rs = stmt.getConnection()
                .getMetaData()
                .getTables(null, metadataSchema, metadataTableName, new String[] {"TABLE"})) {
            return !rs.next();
        }
    }

    private static void recordFreshBaselineCoverage(Statement stmt) throws SQLException {
        for (VersionedMigration migration : MIGRATIONS) {
            String description = migration.version() == 1
                    ? migration.description()
                    : FRESH_BASELINE_DESCRIPTION_PREFIX + migration.description();
            recordSchemaVersion(stmt, migration.version(), description);
        }
    }

    private static boolean hasTable(Statement stmt, String tableName) throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        try (ResultSet rs = stmt.getConnection().getMetaData().getTables(null, null, metadataTableName, null)) {
            return rs.next();
        }
    }

    private static boolean hasPrimaryKey(Statement stmt, String tableName) throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        try (ResultSet rs = stmt.getConnection().getMetaData().getPrimaryKeys(null, null, metadataTableName)) {
            return rs.next();
        }
    }

    private static boolean hasColumn(Statement stmt, String tableName, String columnName) throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        String metadataColumnName = metadataIdentifier(stmt.getConnection(), columnName);
        try (ResultSet rs =
                stmt.getConnection().getMetaData().getColumns(null, null, metadataTableName, metadataColumnName)) {
            return rs.next();
        }
    }

    private static boolean hasForeignKey(Statement stmt, String tableName, String foreignKeyName) throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        try (ResultSet rs = stmt.getConnection().getMetaData().getImportedKeys(null, null, metadataTableName)) {
            while (rs.next()) {
                if (foreignKeyName.equalsIgnoreCase(rs.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean hasConstraint(Statement stmt, String tableName, String constraintName) throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        String metadataConstraintName = metadataIdentifier(stmt.getConnection(), constraintName);
        try (var pstmt = stmt.getConnection()
                .prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_name = ? AND constraint_name = ?")) {
            pstmt.setString(1, metadataTableName);
            pstmt.setString(2, metadataConstraintName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean hasColumnIgnoreCase(Statement stmt, String tableName, String columnName)
            throws SQLException {
        String metadataTableName = metadataIdentifier(stmt.getConnection(), tableName);
        try (var pstmt = stmt.getConnection()
                .prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND UPPER(column_name) = UPPER(?)")) {
            pstmt.setString(1, metadataTableName);
            pstmt.setString(2, columnName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
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

        stmt.execute(SQL_ALTER_TABLE_PREFIX
                + tableName
                + SQL_ADD_CONSTRAINT_FRAGMENT
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
        String sql = SQL_SELECT_COUNT_FROM
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

    private static long countInvalidMatchEnders(Statement stmt) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM matches
                WHERE ended_by IS NOT NULL
                  AND ended_by <> user_a
                  AND ended_by <> user_b
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static void applyUserValueConstraints(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_USERS)) {
            return;
        }
        addAllowedValuesConstraint(
                stmt,
                "users",
                COLUMN_STATE,
                "ck_users_state_values",
                false,
                "INCOMPLETE",
                VALUE_ACTIVE,
                "PAUSED",
                "BANNED");
        addAllowedValuesConstraint(
                stmt, "users", "gender", "ck_users_gender_values", true, "MALE", "FEMALE", VALUE_OTHER);
        addAllowedValuesConstraint(
                stmt, "users", "smoking", "ck_users_smoking_values", true, VALUE_NEVER, "SOMETIMES", VALUE_REGULARLY);
        addAllowedValuesConstraint(
                stmt, "users", "drinking", "ck_users_drinking_values", true, VALUE_NEVER, "SOCIALLY", VALUE_REGULARLY);
        addAllowedValuesConstraint(
                stmt, "users", "wants_kids", "ck_users_wants_kids_values", true, "NO", "OPEN", "SOMEDAY", "HAS_KIDS");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "looking_for",
                "ck_users_looking_for_values",
                true,
                "CASUAL",
                "SHORT_TERM",
                "LONG_TERM",
                "MARRIAGE",
                "UNSURE");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "education",
                "ck_users_education_values",
                true,
                "HIGH_SCHOOL",
                "SOME_COLLEGE",
                "BACHELORS",
                "MASTERS",
                "PHD",
                "TRADE_SCHOOL",
                VALUE_OTHER);
        addAllowedValuesConstraint(
                stmt, "users", "verification_method", "ck_users_verification_method_values", true, "EMAIL", "PHONE");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "pace_messaging_frequency",
                "ck_users_pace_msg_freq_values",
                true,
                "RARELY",
                "OFTEN",
                "CONSTANTLY",
                "WILDCARD");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "pace_time_to_first_date",
                "ck_users_pace_first_date_values",
                true,
                "QUICKLY",
                "FEW_DAYS",
                "WEEKS",
                "MONTHS",
                "WILDCARD");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "pace_communication_style",
                "ck_users_pace_comm_style_values",
                true,
                "TEXT_ONLY",
                "VOICE_NOTES",
                "VIDEO_CALLS",
                "IN_PERSON_ONLY",
                "MIX_OF_EVERYTHING");
        addAllowedValuesConstraint(
                stmt,
                "users",
                "pace_depth_preference",
                "ck_users_pace_depth_values",
                true,
                "SMALL_TALK",
                "DEEP_CHAT",
                "EXISTENTIAL",
                "DEPENDS_ON_VIBE");
    }

    private static void applyInteractionValueConstraints(Statement stmt) throws SQLException {
        if (hasTable(stmt, TABLE_LIKES)) {
            addAllowedValuesConstraint(
                    stmt, "likes", "direction", "ck_likes_direction_values", false, "LIKE", "SUPER_LIKE", "PASS");
        }
        if (hasTable(stmt, TABLE_MATCHES)) {
            addAllowedValuesConstraint(
                    stmt,
                    SQL_TABLE_MATCHES,
                    COLUMN_STATE,
                    "ck_matches_state_values",
                    false,
                    VALUE_ACTIVE,
                    "FRIENDS",
                    "UNMATCHED",
                    VALUE_GRACEFUL_EXIT,
                    "BLOCKED");
            addAllowedValuesConstraint(
                    stmt,
                    SQL_TABLE_MATCHES,
                    "end_reason",
                    "ck_matches_end_reason_values",
                    true,
                    VALUE_FRIEND_ZONE,
                    VALUE_GRACEFUL_EXIT,
                    VALUE_UNMATCH,
                    VALUE_BLOCK);
            if (hasColumn(stmt, TABLE_MATCHES, "ID")) {
                addCheckConstraintIfMissing(stmt, SQL_TABLE_MATCHES, "ck_matches_id_length", "CHAR_LENGTH(id) = 73");
            }
        }
        if (hasTable(stmt, TABLE_CONVERSATIONS)) {
            addAllowedValuesConstraint(
                    stmt,
                    "conversations",
                    "archive_reason_a",
                    "ck_conversations_archive_reason_a_values",
                    true,
                    VALUE_FRIEND_ZONE,
                    VALUE_GRACEFUL_EXIT,
                    VALUE_UNMATCH,
                    VALUE_BLOCK);
            addAllowedValuesConstraint(
                    stmt,
                    "conversations",
                    "archive_reason_b",
                    "ck_conversations_archive_reason_b_values",
                    true,
                    VALUE_FRIEND_ZONE,
                    VALUE_GRACEFUL_EXIT,
                    VALUE_UNMATCH,
                    VALUE_BLOCK);
            if (hasColumn(stmt, TABLE_CONVERSATIONS, "ID")) {
                addCheckConstraintIfMissing(
                        stmt, "conversations", "ck_conversations_id_length", "CHAR_LENGTH(id) = 73");
            }
        }
        if (hasTable(stmt, TABLE_MESSAGES) && hasColumn(stmt, TABLE_MESSAGES, "CONVERSATION_ID")) {
            addCheckConstraintIfMissing(
                    stmt, "messages", "ck_messages_conversation_id_length", "CHAR_LENGTH(conversation_id) = 73");
        }
        if (hasTable(stmt, TABLE_FRIEND_REQUESTS)) {
            addAllowedValuesConstraint(
                    stmt,
                    "friend_requests",
                    "status",
                    "ck_friend_requests_status_values",
                    false,
                    "PENDING",
                    "ACCEPTED",
                    "DECLINED",
                    "EXPIRED");
        }
        if (hasTable(stmt, "NOTIFICATIONS")) {
            addAllowedValuesConstraint(
                    stmt,
                    TABLE_NOTIFICATIONS,
                    "type",
                    "ck_notifications_type_values",
                    false,
                    "MATCH_FOUND",
                    "NEW_MESSAGE",
                    "FRIEND_REQUEST",
                    "FRIEND_REQUEST_ACCEPTED",
                    VALUE_GRACEFUL_EXIT);
        }
        if (hasTable(stmt, TABLE_REPORTS)) {
            addAllowedValuesConstraint(
                    stmt,
                    "reports",
                    "reason",
                    "ck_reports_reason_values",
                    false,
                    "SPAM",
                    "INAPPROPRIATE_CONTENT",
                    "HARASSMENT",
                    "FAKE_PROFILE",
                    "UNDERAGE",
                    VALUE_OTHER);
        }
        if (hasTable(stmt, TABLE_UNDO_STATES)) {
            addAllowedValuesConstraint(
                    stmt,
                    "undo_states",
                    "direction",
                    "ck_undo_states_direction_values",
                    false,
                    "LIKE",
                    "SUPER_LIKE",
                    "PASS");
            if (hasColumn(stmt, TABLE_UNDO_STATES, "MATCH_ID")) {
                addCheckConstraintIfMissing(
                        stmt,
                        "undo_states",
                        "ck_undo_states_match_id_length",
                        "match_id IS NULL OR CHAR_LENGTH(match_id) = 73");
            }
        }
    }

    private static void applySwipeSessionValueConstraints(Statement stmt) throws SQLException {
        if (!hasTable(stmt, TABLE_SWIPE_SESSIONS)) {
            return;
        }

        addAllowedValuesConstraint(
                stmt,
                "swipe_sessions",
                COLUMN_STATE,
                "ck_swipe_sessions_state_values",
                false,
                VALUE_ACTIVE,
                "COMPLETED");
    }

    private static void applyNormalizedProfileValueConstraints(Statement stmt) throws SQLException {
        if (hasTable(stmt, "USER_INTERESTED_IN")) {
            addAllowedValuesConstraint(
                    stmt,
                    "user_interested_in",
                    "gender",
                    "ck_user_interested_in_gender_values",
                    false,
                    "MALE",
                    "FEMALE",
                    VALUE_OTHER);
        }

        addAllowedValuesConstraintForQuotedValueColumn(
                stmt, "user_db_smoking", "ck_user_db_smoking_value_values", VALUE_NEVER, "SOMETIMES", VALUE_REGULARLY);
        addAllowedValuesConstraintForQuotedValueColumn(
                stmt, "user_db_drinking", "ck_user_db_drinking_value_values", VALUE_NEVER, "SOCIALLY", VALUE_REGULARLY);
        addAllowedValuesConstraintForQuotedValueColumn(
                stmt, "user_db_wants_kids", "ck_user_db_wants_kids_value_values", "NO", "OPEN", "SOMEDAY", "HAS_KIDS");
        addAllowedValuesConstraintForQuotedValueColumn(
                stmt,
                "user_db_looking_for",
                "ck_user_db_looking_for_value_values",
                "CASUAL",
                "SHORT_TERM",
                "LONG_TERM",
                "MARRIAGE",
                "UNSURE");
        addAllowedValuesConstraintForQuotedValueColumn(
                stmt,
                "user_db_education",
                "ck_user_db_education_value_values",
                "HIGH_SCHOOL",
                "SOME_COLLEGE",
                "BACHELORS",
                "MASTERS",
                "PHD",
                "TRADE_SCHOOL",
                VALUE_OTHER);
    }

    private static void applyFreshBaselineStructuralConstraints(Statement stmt) throws SQLException {
        if (hasTable(stmt, TABLE_USERS)) {
            if (hasColumn(stmt, TABLE_USERS, "MIN_AGE") && hasColumn(stmt, TABLE_USERS, "MAX_AGE")) {
                addCheckConstraintIfMissing(stmt, "users", "ck_users_age_bounds", "min_age <= max_age");
            }
            if (hasColumn(stmt, TABLE_USERS, "DB_MIN_HEIGHT_CM") && hasColumn(stmt, TABLE_USERS, "DB_MAX_HEIGHT_CM")) {
                addCheckConstraintIfMissing(
                        stmt,
                        "users",
                        "ck_users_height_bounds",
                        "db_min_height_cm IS NULL OR db_max_height_cm IS NULL OR db_min_height_cm <= db_max_height_cm");
            }
            if (hasColumn(stmt, TABLE_USERS, "DB_MAX_AGE_DIFF")) {
                addCheckConstraintIfMissing(
                        stmt,
                        "users",
                        "ck_users_max_age_diff_nonnegative",
                        "db_max_age_diff IS NULL OR db_max_age_diff >= 0");
            }
        }
        addDistinctUsersConstraintIfPresent(stmt, "likes", "who_likes", "who_got_liked", "ck_likes_distinct_users");
        addDistinctUsersConstraintIfPresent(stmt, SQL_TABLE_MATCHES, "user_a", "user_b", "ck_matches_distinct_users");
        addDistinctUsersConstraintIfPresent(
                stmt, "conversations", "user_a", "user_b", "ck_conversations_distinct_users");
        addDistinctUsersConstraintIfPresent(
                stmt, "friend_requests", "from_user_id", "to_user_id", "ck_friend_requests_distinct_users");
        addDistinctUsersConstraintIfPresent(stmt, "blocks", "blocker_id", "blocked_id", "ck_blocks_distinct_users");
        addDistinctUsersConstraintIfPresent(
                stmt, "reports", "reporter_id", "reported_user_id", "ck_reports_distinct_users");
        addDistinctUsersConstraintIfPresent(
                stmt, "profile_notes", "author_id", "subject_id", "ck_profile_notes_distinct_users");
        addDistinctUsersConstraintIfPresent(
                stmt, "profile_views", "viewer_id", "viewed_id", "ck_profile_views_distinct_users");
    }

    private static void applyNonBlankTextConstraints(Statement stmt) throws SQLException {
        addTrimmedTextNotBlankConstraint(stmt, "messages", "content", "ck_messages_content_nonblank");
        addTrimmedTextNotBlankConstraint(stmt, "profile_notes", "content", "ck_profile_notes_content_nonblank");
        addTrimmedTextNotBlankConstraint(stmt, TABLE_NOTIFICATIONS, "title", "ck_notifications_title_nonblank");
        addTrimmedTextNotBlankConstraint(stmt, TABLE_NOTIFICATIONS, "message", "ck_notifications_message_nonblank");
    }

    private static void addDistinctUsersConstraintIfPresent(
            Statement stmt, String tableName, String leftColumn, String rightColumn, String constraintName)
            throws SQLException {
        if (!hasTable(stmt, tableName)
                || !hasColumn(stmt, tableName, leftColumn)
                || !hasColumn(stmt, tableName, rightColumn)) {
            return;
        }
        long violations = countDistinctUserViolations(stmt, tableName, leftColumn, rightColumn);
        if (violations > 0) {
            throw new SQLException("Cannot enforce " + constraintName + ": " + violations + " row(s) have " + leftColumn
                    + " = " + rightColumn);
        }
        addCheckConstraintIfMissing(stmt, tableName, constraintName, leftColumn + " <> " + rightColumn);
    }

    private static long countDistinctUserViolations(
            Statement stmt, String tableName, String leftColumn, String rightColumn) throws SQLException {
        String sql = SQL_SELECT_COUNT_FROM + tableName + SQL_WHERE_FRAGMENT + leftColumn + " = " + rightColumn;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private static void addTrimmedTextNotBlankConstraint(
            Statement stmt, String tableName, String columnName, String constraintName) throws SQLException {
        if (!hasTable(stmt, tableName) || !hasColumn(stmt, tableName, columnName)) {
            return;
        }
        long violations = countBlankTrimmedText(stmt, tableName, columnName);
        if (violations > 0) {
            throw new SQLException("Cannot enforce " + constraintName + ": " + violations + " row(s) have blank "
                    + tableName + "." + columnName);
        }
        addCheckConstraintIfMissing(stmt, tableName, constraintName, "CHAR_LENGTH(TRIM(" + columnName + ")) > 0");
    }

    private static long countBlankTrimmedText(Statement stmt, String tableName, String columnName) throws SQLException {
        String sql = SQL_SELECT_COUNT_FROM + tableName + SQL_WHERE_FRAGMENT + columnName
                + " IS NOT NULL AND CHAR_LENGTH(TRIM(" + columnName + ")) = 0";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private static long countDuplicateTrimmedValues(Statement stmt, String tableName, String columnName)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM (SELECT TRIM(" + columnName + ") AS normalized_value FROM " + tableName
                + SQL_WHERE_FRAGMENT + columnName + " IS NOT NULL GROUP BY TRIM(" + columnName
                + ") HAVING COUNT(*) > 1) duplicates";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private static void addAllowedValuesConstraint(
            Statement stmt,
            String tableName,
            String columnName,
            String constraintName,
            boolean nullable,
            String... allowedValues)
            throws SQLException {
        if (!hasColumn(stmt, tableName, columnName)) {
            return;
        }
        String inClause = String.join(
                ", ",
                java.util.Arrays.stream(allowedValues)
                        .map(MigrationRunner::quoteSqlLiteral)
                        .toList());
        String condition = nullable
                ? columnName + " IS NULL OR " + columnName + " IN (" + inClause + ")"
                : columnName + " IN (" + inClause + ")";
        addCheckConstraintIfMissing(stmt, tableName, constraintName, condition);
    }

    private static void addAllowedValuesConstraintForQuotedValueColumn(
            Statement stmt, String tableName, String constraintName, String... allowedValues) throws SQLException {
        if (!hasTable(stmt, tableName) || !hasColumnIgnoreCase(stmt, tableName, "value")) {
            return;
        }

        String inClause = String.join(
                ", ",
                java.util.Arrays.stream(allowedValues)
                        .map(MigrationRunner::quoteSqlLiteral)
                        .toList());
        addCheckConstraintIfMissing(stmt, tableName, constraintName, "\"value\" IN (" + inClause + ")");
    }

    private static void addCheckConstraintIfMissing(
            Statement stmt, String tableName, String constraintName, String condition) throws SQLException {
        if (hasConstraint(stmt, tableName, constraintName)) {
            return;
        }
        stmt.execute(SQL_ALTER_TABLE_PREFIX + tableName + SQL_ADD_CONSTRAINT_FRAGMENT + constraintName + " CHECK ("
                + condition + ")");
    }

    private static void addUniqueConstraintIfMissing(
            Statement stmt, String tableName, String constraintName, String columnName) throws SQLException {
        if (hasConstraint(stmt, tableName, constraintName)) {
            return;
        }
        stmt.execute(SQL_ALTER_TABLE_PREFIX + tableName + SQL_ADD_CONSTRAINT_FRAGMENT + constraintName + " UNIQUE ("
                + columnName + ")");
    }

    private static String quoteSqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
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

    static String metadataIdentifier(
            String identifier, boolean storesUpperCaseIdentifiers, boolean storesLowerCaseIdentifiers) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be blank");
        }
        if (storesUpperCaseIdentifiers) {
            return identifier.toUpperCase(Locale.ROOT);
        }
        if (storesLowerCaseIdentifiers) {
            return identifier.toLowerCase(Locale.ROOT);
        }
        return identifier;
    }

    private static String metadataIdentifier(Connection connection, String identifier) throws SQLException {
        var metaData = connection.getMetaData();
        return metadataIdentifier(
                identifier, metaData.storesUpperCaseIdentifiers(), metaData.storesLowerCaseIdentifiers());
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema versioning
    // ═══════════════════════════════════════════════════════════════

    /** Creates the {@code schema_version} tracking table if it does not exist. */
    static void createSchemaVersionTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY,
                    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
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
        String updateSql =
                "UPDATE schema_version SET applied_at = CURRENT_TIMESTAMP, description = ? WHERE version = ?";
        try (var pstmt = stmt.getConnection().prepareStatement(updateSql)) {
            pstmt.setString(1, description);
            pstmt.setInt(2, version);
            if (pstmt.executeUpdate() > 0) {
                return;
            }
        }

        String insertSql =
                "INSERT INTO schema_version (version, applied_at, description) VALUES (?, CURRENT_TIMESTAMP, ?)";
        try (var pstmt = stmt.getConnection().prepareStatement(insertSql)) {
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
                || "42P01".equals(e.getSQLState())
                || e.getErrorCode() == 42102
                || e.getErrorCode() == 42104;
    }
}
