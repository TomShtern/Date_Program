package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link SchemaInitializer} — validates that all expected tables and
 * indexes are created
 * on a fresh in-memory H2 database.
 */
@Timeout(10)
class SchemaInitializerTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:h2:mem:schema_init_test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        connection = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Nested
    @DisplayName("createAllTables")
    @SuppressWarnings("unused")
    class CreateAllTables {

        @Test
        @DisplayName("should create all expected tables")
        void createsAllExpectedTables() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
            }

            Set<String> tables = getTableNames();

            Set<String> expectedTables = Set.of(
                    "USERS",
                    "LIKES",
                    "MATCHES",
                    "SWIPE_SESSIONS",
                    "USER_STATS",
                    "PLATFORM_STATS",
                    "DAILY_PICKS",
                    "DAILY_PICK_VIEWS",
                    "USER_ACHIEVEMENTS",
                    "CONVERSATIONS",
                    "MESSAGES",
                    "FRIEND_REQUESTS",
                    "NOTIFICATIONS",
                    "BLOCKS",
                    "REPORTS",
                    "PROFILE_NOTES",
                    "PROFILE_VIEWS",
                    "STANDOUTS",
                    "UNDO_STATES",
                    "USER_PHOTOS",
                    "USER_INTERESTS",
                    "USER_INTERESTED_IN",
                    "USER_DB_SMOKING",
                    "USER_DB_DRINKING",
                    "USER_DB_WANTS_KIDS",
                    "USER_DB_LOOKING_FOR",
                    "USER_DB_EDUCATION");

            Set<String> missingTables = new HashSet<>(expectedTables);
            missingTables.removeAll(tables);
            assertTrue(missingTables.isEmpty(), "Missing tables: " + missingTables);
        }

        @Test
        @DisplayName("should be idempotent — running twice does not fail")
        void isIdempotent() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
                // Run again — should not throw
                SchemaInitializer.createAllTables(stmt);
            }

            // Verify tables still exist after double-run
            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"));
            assertTrue(tables.contains("MATCHES"));
        }

        @Test
        @DisplayName("should create expected indexes on core tables")
        void createsExpectedIndexes() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
            }

            Set<String> indexes = getIndexNames();

            // Core indexes
            assertTrue(indexes.contains("IDX_LIKES_WHO_LIKES"), "Missing index: idx_likes_who_likes");
            assertTrue(indexes.contains("IDX_MATCHES_USER_A"), "Missing index: idx_matches_user_a");
            assertTrue(indexes.contains("IDX_USERS_STATE"), "Missing index: idx_users_state");

            // Stats indexes
            assertTrue(indexes.contains("IDX_USER_STATS_USER_ID"), "Missing index: idx_user_stats_user_id");

            // Additional indexes
            assertTrue(indexes.contains("IDX_ACHIEVEMENTS_USER_ID"), "Missing index: idx_achievements_user_id");
            assertTrue(indexes.contains("IDX_DAILY_PICKS_PICK_DATE"), "Missing index: idx_daily_picks_pick_date");
            assertTrue(indexes.contains("IDX_DAILY_PICK_VIEWS_USER"), "Missing index: idx_daily_pick_views_user");
            assertTrue(indexes.contains("IDX_MESSAGES_SENDER_ID"), "Missing index: idx_messages_sender_id");
            assertTrue(indexes.contains("IDX_LIKES_DIRECTION_CREATED"), "Missing index: idx_likes_direction_created");
            assertTrue(indexes.contains("IDX_LIKES_RECEIVED_CREATED"), "Missing index: idx_likes_received_created");
            assertTrue(
                    indexes.contains("IDX_CONVERSATIONS_USER_A_LAST_MSG"),
                    "Missing index: idx_conversations_user_a_last_msg");
            assertTrue(
                    indexes.contains("IDX_CONVERSATIONS_USER_B_LAST_MSG"),
                    "Missing index: idx_conversations_user_b_last_msg");
            assertTrue(indexes.contains("IDX_MESSAGES_SENDER_CREATED"), "Missing index: idx_messages_sender_created");
            assertTrue(indexes.contains("IDX_SESSIONS_STARTED_AT_DESC"), "Missing index: idx_sessions_started_at_desc");
            assertTrue(indexes.contains("IDX_USER_STATS_COMPUTED_DESC"), "Missing index: idx_user_stats_computed_desc");
            assertTrue(indexes.contains("IDX_STANDOUTS_INTERACTED_AT"), "Missing index: idx_standouts_interacted_at");

            // Normalized profile table indexes (V3)
            assertTrue(indexes.contains("IDX_USER_INTERESTS_INTEREST"), "Missing index: idx_user_interests_interest");
            assertTrue(
                    indexes.contains("IDX_USER_INTERESTED_IN_GENDER"), "Missing index: idx_user_interested_in_gender");
        }

        @Test
        @DisplayName("should create the final fresh-schema columns directly")
        void createsFinalFreshSchemaColumnsDirectly() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
            }

            Set<String> userColumns = getColumnNames("USERS");
            assertFalse(userColumns.contains("PHOTO_URLS"), "Fresh schema should not recreate users.photo_urls");
            assertFalse(userColumns.contains("INTERESTS"), "Fresh schema should not recreate users.interests");
            assertFalse(userColumns.contains("INTERESTED_IN"), "Fresh schema should not recreate users.interested_in");

            Set<String> matchColumns = getColumnNames("MATCHES");
            assertTrue(matchColumns.contains("UPDATED_AT"), "Fresh schema should create matches.updated_at");
            assertTrue(matchColumns.contains("ENDED_BY"), "Fresh schema should create matches.ended_by");

            Set<String> friendRequestColumns = getColumnNames("FRIEND_REQUESTS");
            assertTrue(
                    friendRequestColumns.contains("PAIR_KEY"), "Fresh schema should create friend_requests.pair_key");
            assertTrue(
                    friendRequestColumns.contains("PENDING_MARKER"),
                    "Fresh schema should create friend_requests.pending_marker");
        }

        @Test
        @DisplayName("should create the final fresh-schema foreign keys and invariant checks directly")
        void createsFinalFreshSchemaForeignKeysAndInvariantChecksDirectly() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
            }

            assertFreshSchemaForeignKeys();
            assertFreshSchemaUserConstraints();
            assertFreshSchemaInteractionConstraints();
            assertFreshSchemaNormalizedPreferenceConstraints();
            assertFreshSchemaTextConstraints();
        }

        private void assertFreshSchemaForeignKeys() throws SQLException {
            Set<String> dailyPickViewsForeignKeys = getForeignKeyNames("DAILY_PICK_VIEWS");
            assertTrue(
                    dailyPickViewsForeignKeys.contains("FK_DAILY_PICK_VIEWS_USER"),
                    "Fresh schema should create fk_daily_pick_views_user directly");

            Set<String> userAchievementsForeignKeys = getForeignKeyNames("USER_ACHIEVEMENTS");
            assertTrue(
                    userAchievementsForeignKeys.contains("FK_USER_ACHIEVEMENTS_USER"),
                    "Fresh schema should create fk_user_achievements_user directly");

            Set<String> matchesForeignKeys = getForeignKeyNames("MATCHES");
            assertTrue(
                    matchesForeignKeys.contains("FK_MATCHES_ENDED_BY"),
                    "Fresh schema should create fk_matches_ended_by directly");

            Set<String> indexes = getIndexNames();
            assertTrue(
                    indexes.contains("UK_FRIEND_REQUESTS_PENDING_PAIR"),
                    "Fresh schema should create uk_friend_requests_pending_pair directly");
        }

        private void assertFreshSchemaUserConstraints() throws SQLException {
            Set<String> userChecks = getCheckConstraintNames("USERS");
            assertTrue(userChecks.contains("CK_USERS_STATE_VALUES"), "Missing check: ck_users_state_values");
            assertTrue(userChecks.contains("CK_USERS_GENDER_VALUES"), "Missing check: ck_users_gender_values");
            assertTrue(userChecks.contains("CK_USERS_EMAIL_TRIMMED"), "Missing check: ck_users_email_trimmed");
            assertTrue(userChecks.contains("CK_USERS_PHONE_TRIMMED"), "Missing check: ck_users_phone_trimmed");

            Set<String> userUniqueConstraints = getUniqueConstraintNames("USERS");
            assertTrue(userUniqueConstraints.contains("UK_USERS_EMAIL"), "Missing unique constraint: uk_users_email");
            assertTrue(userUniqueConstraints.contains("UK_USERS_PHONE"), "Missing unique constraint: uk_users_phone");
        }

        private void assertFreshSchemaInteractionConstraints() throws SQLException {
            Set<String> matchChecks = getCheckConstraintNames("MATCHES");
            assertTrue(matchChecks.contains("CK_MATCHES_STATE_VALUES"), "Missing check: ck_matches_state_values");
            assertTrue(
                    matchChecks.contains("CK_MATCHES_END_REASON_VALUES"),
                    "Missing check: ck_matches_end_reason_values");
            assertTrue(matchChecks.contains("CK_MATCHES_ID_LENGTH"), "Missing check: ck_matches_id_length");
            assertTrue(
                    matchChecks.contains("CK_MATCHES_ENDED_BY_PARTICIPANT"),
                    "Missing check: ck_matches_ended_by_participant");
            assertTrue(matchChecks.contains("CK_MATCHES_DISTINCT_USERS"), "Missing check: ck_matches_distinct_users");

            Set<String> likeChecks = getCheckConstraintNames("LIKES");
            assertTrue(likeChecks.contains("CK_LIKES_DISTINCT_USERS"), "Missing check: ck_likes_distinct_users");

            Set<String> swipeSessionChecks = getCheckConstraintNames("SWIPE_SESSIONS");
            assertTrue(
                    swipeSessionChecks.contains("CK_SWIPE_SESSIONS_STATE_VALUES"),
                    "Missing check: ck_swipe_sessions_state_values");

            Set<String> friendRequestChecks = getCheckConstraintNames("FRIEND_REQUESTS");
            assertTrue(
                    friendRequestChecks.contains("CK_FRIEND_REQUESTS_DISTINCT_USERS"),
                    "Missing check: ck_friend_requests_distinct_users");

            Set<String> profileViewChecks = getCheckConstraintNames("PROFILE_VIEWS");
            assertTrue(
                    profileViewChecks.contains("CK_PROFILE_VIEWS_DISTINCT_USERS"),
                    "Missing check: ck_profile_views_distinct_users");
        }

        private void assertFreshSchemaNormalizedPreferenceConstraints() throws SQLException {
            Set<String> interestedInChecks = getCheckConstraintNames("USER_INTERESTED_IN");
            assertTrue(
                    interestedInChecks.contains("CK_USER_INTERESTED_IN_GENDER_VALUES"),
                    "Missing check: ck_user_interested_in_gender_values");

            Set<String> userDbSmokingChecks = getCheckConstraintNames("USER_DB_SMOKING");
            assertTrue(
                    userDbSmokingChecks.contains("CK_USER_DB_SMOKING_VALUE_VALUES"),
                    "Missing check: ck_user_db_smoking_value_values");

            Set<String> userDbDrinkingChecks = getCheckConstraintNames("USER_DB_DRINKING");
            assertTrue(
                    userDbDrinkingChecks.contains("CK_USER_DB_DRINKING_VALUE_VALUES"),
                    "Missing check: ck_user_db_drinking_value_values");

            Set<String> userDbWantsKidsChecks = getCheckConstraintNames("USER_DB_WANTS_KIDS");
            assertTrue(
                    userDbWantsKidsChecks.contains("CK_USER_DB_WANTS_KIDS_VALUE_VALUES"),
                    "Missing check: ck_user_db_wants_kids_value_values");

            Set<String> userDbLookingForChecks = getCheckConstraintNames("USER_DB_LOOKING_FOR");
            assertTrue(
                    userDbLookingForChecks.contains("CK_USER_DB_LOOKING_FOR_VALUE_VALUES"),
                    "Missing check: ck_user_db_looking_for_value_values");

            Set<String> userDbEducationChecks = getCheckConstraintNames("USER_DB_EDUCATION");
            assertTrue(
                    userDbEducationChecks.contains("CK_USER_DB_EDUCATION_VALUE_VALUES"),
                    "Missing check: ck_user_db_education_value_values");
        }

        private void assertFreshSchemaTextConstraints() throws SQLException {
            Set<String> messageChecks = getCheckConstraintNames("MESSAGES");
            assertTrue(
                    messageChecks.contains("CK_MESSAGES_CONTENT_NONBLANK"),
                    "Missing check: ck_messages_content_nonblank");

            Set<String> profileNoteChecks = getCheckConstraintNames("PROFILE_NOTES");
            assertTrue(
                    profileNoteChecks.contains("CK_PROFILE_NOTES_CONTENT_NONBLANK"),
                    "Missing check: ck_profile_notes_content_nonblank");

            Set<String> notificationChecks = getCheckConstraintNames("NOTIFICATIONS");
            assertTrue(
                    notificationChecks.contains("CK_NOTIFICATIONS_TITLE_NONBLANK"),
                    "Missing check: ck_notifications_title_nonblank");
            assertTrue(
                    notificationChecks.contains("CK_NOTIFICATIONS_MESSAGE_NONBLANK"),
                    "Missing check: ck_notifications_message_nonblank");
        }

        @Test
        @DisplayName("should reject blank user-facing text in the fresh schema")
        void rejectsBlankUserFacingTextInFreshSchema() throws SQLException {
            String userA = "00000000-0000-0000-0000-000000000001";
            String userB = "00000000-0000-0000-0000-000000000002";
            String conversationId = userA + "_" + userB;

            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
                stmt.execute("INSERT INTO users (id, name, created_at, updated_at, state) VALUES ('"
                        + userA
                        + "', 'User A', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')");
                stmt.execute("INSERT INTO users (id, name, created_at, updated_at, state) VALUES ('"
                        + userB
                        + "', 'User B', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')");
                stmt.execute("INSERT INTO conversations (id, user_a, user_b, created_at) VALUES ('"
                        + conversationId
                        + "', '"
                        + userA
                        + "', '"
                        + userB
                        + "', CURRENT_TIMESTAMP())");

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO messages "
                                + "(id, conversation_id, sender_id, content, created_at) VALUES "
                                + "('11111111-1111-1111-1111-111111111111', '"
                                + conversationId
                                + "', '"
                                + userA
                                + "', '   ', CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO profile_notes "
                                + "(author_id, subject_id, content, created_at, updated_at) VALUES "
                                + "('"
                                + userA
                                + "', '"
                                + userB
                                + "', '   ', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO notifications "
                                + "(id, user_id, type, title, message, created_at) VALUES "
                                + "('22222222-2222-2222-2222-222222222222', '"
                                + userA
                                + "', 'NEW_MESSAGE', '   ', 'hello', CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO notifications "
                                + "(id, user_id, type, title, message, created_at) VALUES "
                                + "('33333333-3333-3333-3333-333333333333', '"
                                + userA
                                + "', 'NEW_MESSAGE', 'Hello', '   ', CURRENT_TIMESTAMP())"));
            }
        }

        @Test
        @DisplayName("should not create a surrogate id column for profile_views")
        void profileViewsHasNoSurrogateIdColumn() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
            }

            Set<String> columns = getColumnNames("PROFILE_VIEWS");

            assertTrue(columns.contains("VIEWER_ID"));
            assertTrue(columns.contains("VIEWED_ID"));
            assertTrue(columns.contains("VIEWED_AT"));
            assertFalse(columns.contains("ID"), "profile_views should not expose a surrogate id column");

            Set<String> primaryKeyColumns = getPrimaryKeyColumns("PROFILE_VIEWS");
            assertEquals(Set.of("VIEWER_ID", "VIEWED_ID", "VIEWED_AT"), primaryKeyColumns);
        }
    }

    @Nested
    @DisplayName("MigrationRunner.runAllPending")
    @SuppressWarnings("unused")
    class RunAllPending {

        @Test
        @DisplayName("should create all tables and record schema version on fresh database")
        void freshDatabaseMigration() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.runAllPending(stmt);
            }

            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"), "Missing table: users");
            assertTrue(tables.contains("SCHEMA_VERSION"), "Missing table: schema_version");
            assertTrue(tables.contains("DAILY_PICKS"), "Missing table: daily_picks");

            // Verify both V1 and V2 were recorded (new migration system applies both on
            // fresh DB)
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 1 should be recorded");
            }

            Set<String> dailyPickViewsForeignKeys = getForeignKeyNames("DAILY_PICK_VIEWS");
            assertTrue(
                    dailyPickViewsForeignKeys.contains("FK_DAILY_PICK_VIEWS_USER"),
                    "Missing foreign key: fk_daily_pick_views_user");

            Set<String> userAchievementsForeignKeys = getForeignKeyNames("USER_ACHIEVEMENTS");
            assertTrue(
                    userAchievementsForeignKeys.contains("FK_USER_ACHIEVEMENTS_USER"),
                    "Missing foreign key: fk_user_achievements_user");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 18")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 18 should be recorded");
            }
        }

        @Test
        @DisplayName("should record historical versions as covered by the fresh baseline")
        void freshDatabaseMigrationRecordsHistoricalVersionsAsCoveredByBaseline() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.runAllPending(stmt);
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT description FROM schema_version WHERE version = 2")) {
                assertTrue(rs.next());
                assertTrue(
                        rs.getString(1).contains("fresh baseline"),
                        "Fresh bootstrap should mark legacy versions as covered by the fresh baseline");
            }
        }

        @Test
        @DisplayName("should be idempotent — running twice does not fail")
        void isIdempotent() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.runAllPending(stmt);
                // Run again — all versions already applied, each migration is skipped
                MigrationRunner.runAllPending(stmt);
            }

            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"));
            assertTrue(tables.contains("DAILY_PICKS"));

            Set<String> dailyPickViewsForeignKeys = getForeignKeyNames("DAILY_PICK_VIEWS");
            assertTrue(
                    dailyPickViewsForeignKeys.contains("FK_DAILY_PICK_VIEWS_USER"),
                    "Migration should keep fk_daily_pick_views_user on rerun");

            Set<String> userAchievementsForeignKeys = getForeignKeyNames("USER_ACHIEVEMENTS");
            assertTrue(
                    userAchievementsForeignKeys.contains("FK_USER_ACHIEVEMENTS_USER"),
                    "Migration should keep fk_user_achievements_user on rerun");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 18")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 18 should still be recorded once");
            }
        }

        @Test
        @DisplayName("should enforce latest invariant checks and match ended_by integrity")
        void latestMigrationEnforcesInvariantChecksAndEndedByIntegrity() throws SQLException {
            String userA = "00000000-0000-0000-0000-000000000001";
            String userB = "00000000-0000-0000-0000-000000000002";
            String outsider = "00000000-0000-0000-0000-000000000003";
            String canonicalMatchId = userA + "_" + userB;

            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.runAllPending(stmt);

                stmt.execute("INSERT INTO users (id, name, created_at, updated_at, state, gender) VALUES " + "('"
                        + userA + "', 'User A', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE', 'MALE')");
                stmt.execute("INSERT INTO users (id, name, created_at, updated_at, state, gender) VALUES " + "('"
                        + userB + "', 'User B', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE', 'FEMALE')");
                stmt.execute("INSERT INTO users (id, name, created_at, updated_at, state, gender) VALUES " + "('"
                        + outsider + "', 'Outsider', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE', 'OTHER')");

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute(
                                "INSERT INTO likes (id, who_likes, who_got_liked, direction, created_at) VALUES "
                                        + "('11111111-1111-1111-1111-111111111111', '"
                                        + userA
                                        + "', '"
                                        + userB
                                        + "', 'MAYBE', CURRENT_TIMESTAMP())"));

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute(
                                "INSERT INTO matches (id, user_a, user_b, created_at, updated_at, state) VALUES "
                                        + "('short-id', '"
                                        + userA
                                        + "', '"
                                        + userB
                                        + "', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')"));

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute(
                                "INSERT INTO matches (id, user_a, user_b, created_at, updated_at, state, ended_at, ended_by, end_reason) VALUES "
                                        + "('"
                                        + canonicalMatchId
                                        + "', '"
                                        + userA
                                        + "', '"
                                        + userB
                                        + "', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'UNMATCHED', CURRENT_TIMESTAMP(), '"
                                        + outsider
                                        + "', 'UNMATCH')"));
            }
        }

        @Test
        @DisplayName("should treat a missing schema_version table as not applied")
        void missingSchemaVersionTableIsNotApplied() throws SQLException {
            assertFalse(MigrationRunner.isVersionApplied(connection, 1));
        }

        @Test
        @DisplayName("should recognize PostgreSQL missing table SQL state as not applied")
        void postgresqlMissingSchemaVersionStateIsNotApplied() throws Exception {
            assertTrue(invokeIsMissingTable(new SQLException("missing table", "42P01")));
        }

        @Test
        @DisplayName("should add foreign keys and cascade deletes when upgrading a legacy database")
        void legacyDatabaseMigrationAddsForeignKeysAndCascadesOnDelete() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE daily_pick_views ("
                        + "user_id UUID NOT NULL, "
                        + "viewed_date DATE NOT NULL, "
                        + "viewed_at TIMESTAMP NOT NULL, "
                        + "PRIMARY KEY (user_id, viewed_date)"
                        + ")");
                stmt.execute("CREATE TABLE user_achievements ("
                        + "id UUID PRIMARY KEY, "
                        + "user_id UUID NOT NULL, "
                        + "achievement VARCHAR(50) NOT NULL, "
                        + "unlocked_at TIMESTAMP NOT NULL, "
                        + "UNIQUE (user_id, achievement)"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO users(id) VALUES ('11111111-1111-1111-1111-111111111111')");
                stmt.execute("INSERT INTO daily_pick_views(user_id, viewed_date, viewed_at) VALUES "
                        + "('11111111-1111-1111-1111-111111111111', DATE '2026-03-26', CURRENT_TIMESTAMP())");
                stmt.execute("INSERT INTO user_achievements(id, user_id, achievement, unlocked_at) VALUES "
                        + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', "
                        + "'legacy-achievement', CURRENT_TIMESTAMP())");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> dailyPickViewsForeignKeys = getForeignKeyNames("DAILY_PICK_VIEWS");
            assertTrue(
                    dailyPickViewsForeignKeys.contains("FK_DAILY_PICK_VIEWS_USER"),
                    "Legacy upgrade should add fk_daily_pick_views_user");

            Set<String> userAchievementsForeignKeys = getForeignKeyNames("USER_ACHIEVEMENTS");
            assertTrue(
                    userAchievementsForeignKeys.contains("FK_USER_ACHIEVEMENTS_USER"),
                    "Legacy upgrade should add fk_user_achievements_user");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DELETE FROM users WHERE id = '11111111-1111-1111-1111-111111111111'");
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet dailyPickViewsRs = stmt.executeQuery("SELECT COUNT(*) FROM daily_pick_views")) {
                assertTrue(dailyPickViewsRs.next());
                assertEquals(0, dailyPickViewsRs.getInt(1), "daily_pick_views rows should cascade delete");
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet userAchievementsRs = stmt.executeQuery("SELECT COUNT(*) FROM user_achievements")) {
                assertTrue(userAchievementsRs.next());
                assertEquals(0, userAchievementsRs.getInt(1), "user_achievements rows should cascade delete");
            }
        }

        @Test
        @DisplayName("should fail fast when orphan rows block foreign key creation")
        void rejectsOrphanRowsBeforeAddingForeignKeys() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE daily_pick_views ("
                        + "user_id UUID NOT NULL, "
                        + "viewed_date DATE NOT NULL, "
                        + "viewed_at TIMESTAMP NOT NULL, "
                        + "PRIMARY KEY (user_id, viewed_date)"
                        + ")");
                stmt.execute("CREATE TABLE user_achievements ("
                        + "id UUID PRIMARY KEY, "
                        + "user_id UUID NOT NULL, "
                        + "achievement VARCHAR(50) NOT NULL, "
                        + "unlocked_at TIMESTAMP NOT NULL, "
                        + "UNIQUE (user_id, achievement)"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO daily_pick_views(user_id, viewed_date, viewed_at) VALUES "
                        + "('33333333-3333-3333-3333-333333333333', DATE '2026-03-26', CURRENT_TIMESTAMP())");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair')");

                SQLException exception = assertThrows(SQLException.class, () -> MigrationRunner.runAllPending(stmt));
                assertEquals(
                        "Cannot add foreign key fk_daily_pick_views_user: 1 orphan row(s) found in daily_pick_views.user_id referencing users.id",
                        exception.getMessage());
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 10")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Schema version 10 must not be recorded when migration fails");
            }
        }

        @Test
        @DisplayName("should ignore null FK values during orphan preflight checks")
        void nullForeignKeyValuesDoNotBlockV10ForeignKeys() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE daily_pick_views ("
                        + "user_id UUID, "
                        + "viewed_date DATE NOT NULL, "
                        + "viewed_at TIMESTAMP NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE user_achievements ("
                        + "id UUID PRIMARY KEY, "
                        + "user_id UUID, "
                        + "achievement VARCHAR(50) NOT NULL, "
                        + "unlocked_at TIMESTAMP NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO daily_pick_views(user_id, viewed_date, viewed_at) VALUES "
                        + "(NULL, DATE '2026-03-26', CURRENT_TIMESTAMP())");
                stmt.execute("INSERT INTO user_achievements(id, user_id, achievement, unlocked_at) VALUES "
                        + "('44444444-4444-4444-4444-444444444444', NULL, 'legacy-achievement', CURRENT_TIMESTAMP())");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> dailyPickViewsForeignKeys = getForeignKeyNames("DAILY_PICK_VIEWS");
            assertTrue(
                    dailyPickViewsForeignKeys.contains("FK_DAILY_PICK_VIEWS_USER"),
                    "Null user_id rows should not block fk_daily_pick_views_user");

            Set<String> userAchievementsForeignKeys = getForeignKeyNames("USER_ACHIEVEMENTS");
            assertTrue(
                    userAchievementsForeignKeys.contains("FK_USER_ACHIEVEMENTS_USER"),
                    "Null user_id rows should not block fk_user_achievements_user");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 10")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 10 should be recorded when only null FK values exist");
            }
        }

        @Test
        @DisplayName("should upgrade legacy profile_views and conversation constraint names on V5")
        void v5MigrationRemovesLegacyProfileViewsIdAndRenamesConversationConstraint() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE conversations ("
                        + "id VARCHAR(100) PRIMARY KEY, "
                        + "user_a UUID NOT NULL, "
                        + "user_b UUID NOT NULL, "
                        + "deleted_at TIMESTAMP, "
                        + "CONSTRAINT unq_conversation_users UNIQUE (user_a, user_b)"
                        + ")");
                stmt.execute("CREATE TABLE profile_views ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "viewer_id UUID NOT NULL, "
                        + "viewed_id UUID NOT NULL, "
                        + "viewed_at TIMESTAMP NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 initial schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile tables'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 legacy conversation constraint')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> profileViewColumns = getColumnNames("PROFILE_VIEWS");
            assertFalse(profileViewColumns.contains("ID"), "V5 should remove the legacy profile_views id column");
            assertTrue(profileViewColumns.contains("VIEWER_ID"));
            assertTrue(profileViewColumns.contains("VIEWED_ID"));
            assertTrue(profileViewColumns.contains("VIEWED_AT"));

            Set<String> profileViewsPrimaryKeyColumns = getPrimaryKeyColumns("PROFILE_VIEWS");
            assertEquals(Set.of("VIEWER_ID", "VIEWED_ID", "VIEWED_AT"), profileViewsPrimaryKeyColumns);

            Set<String> uniqueConstraints = getUniqueConstraintNames("CONVERSATIONS");
            assertTrue(uniqueConstraints.contains("UK_CONVERSATION_USERS"));
            assertFalse(uniqueConstraints.contains("UNQ_CONVERSATION_USERS"));
        }

        @Test
        @DisplayName("should repair missing matches.updated_at even when earlier schema versions are already recorded")
        void repairsMissingMatchesUpdatedAtForAlreadyVersionedDatabase() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE matches ("
                        + "id VARCHAR(100) PRIMARY KEY, "
                        + "user_a UUID NOT NULL, "
                        + "user_b UUID NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', "
                        + "ended_at TIMESTAMP, "
                        + "ended_by UUID, "
                        + "end_reason VARCHAR(30), "
                        + "deleted_at TIMESTAMP"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> columns = getColumnNames("MATCHES");
            assertTrue(columns.contains("UPDATED_AT"), "Migration should re-add missing matches.updated_at column");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 9")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 9 should be recorded");
            }
        }

        @Test
        @DisplayName("should converge away from the standalone messages conversation index once later migrations run")
        void v7MigrationConvergesAwayFromStandaloneMessagesConversationIdIndex() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> indexes = getIndexNames();
            assertFalse(
                    indexes.contains("IDX_MESSAGES_CONVERSATION_ID"),
                    "Later migrations should drop idx_messages_conversation_id as redundant");
            assertTrue(
                    indexes.contains("IDX_MESSAGES_CONVERSATION_CREATED"),
                    "Migration should preserve idx_messages_conversation_created");
        }

        @Test
        @DisplayName("should add query-optimization indexes in V8 and record the migration")
        void v8MigrationAddsQueryOptimizationIndexes() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                SchemaInitializer.createAllTables(stmt);
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index')");

                MigrationRunner.runAllPending(stmt);
            }

            Set<String> indexes = getIndexNames();
            assertTrue(indexes.contains("IDX_LIKES_DIRECTION_CREATED"), "Missing idx_likes_direction_created");
            assertTrue(indexes.contains("IDX_LIKES_RECEIVED_CREATED"), "Missing idx_likes_received_created");
            assertTrue(
                    indexes.contains("IDX_CONVERSATIONS_USER_A_LAST_MSG"), "Missing idx_conversations_user_a_last_msg");
            assertTrue(
                    indexes.contains("IDX_CONVERSATIONS_USER_B_LAST_MSG"), "Missing idx_conversations_user_b_last_msg");
            assertTrue(indexes.contains("IDX_MESSAGES_SENDER_CREATED"), "Missing idx_messages_sender_created");
            assertTrue(indexes.contains("IDX_SESSIONS_STARTED_AT_DESC"), "Missing idx_sessions_started_at_desc");
            assertTrue(indexes.contains("IDX_USER_STATS_COMPUTED_DESC"), "Missing idx_user_stats_computed_desc");
            assertTrue(indexes.contains("IDX_STANDOUTS_INTERACTED_AT"), "Missing idx_standouts_interacted_at");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 8")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 8 should be recorded");
            }
        }

        @Test
        @DisplayName("should add fresh-baseline structural and nonblank text checks in V14")
        void v14MigrationAddsFreshBaselineStructuralAndTextChecks() throws SQLException {
            String userA = "00000000-0000-0000-0000-000000000001";
            String userB = "00000000-0000-0000-0000-000000000002";
            String conversationId = userA + "_" + userB;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users ("
                        + "id UUID PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL, "
                        + "min_age INT DEFAULT 18, "
                        + "max_age INT DEFAULT 99, "
                        + "db_min_height_cm INT, "
                        + "db_max_height_cm INT, "
                        + "db_max_age_diff INT, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "updated_at TIMESTAMP NOT NULL, "
                        + "state VARCHAR(20) NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE likes ("
                        + "id UUID PRIMARY KEY, "
                        + "who_likes UUID NOT NULL, "
                        + "who_got_liked UUID NOT NULL, "
                        + "direction VARCHAR(10) NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "deleted_at TIMESTAMP"
                        + ")");
                stmt.execute("CREATE TABLE friend_requests ("
                        + "id UUID PRIMARY KEY, "
                        + "from_user_id UUID NOT NULL, "
                        + "to_user_id UUID NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "status VARCHAR(20) NOT NULL, "
                        + "responded_at TIMESTAMP, "
                        + "pair_key VARCHAR(73), "
                        + "pending_marker VARCHAR(10)"
                        + ")");
                stmt.execute("CREATE TABLE profile_views ("
                        + "viewer_id UUID NOT NULL, "
                        + "viewed_id UUID NOT NULL, "
                        + "viewed_at TIMESTAMP NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE messages ("
                        + "id UUID PRIMARY KEY, "
                        + "conversation_id VARCHAR(100) NOT NULL, "
                        + "sender_id UUID NOT NULL, "
                        + "content VARCHAR(1000) NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "deleted_at TIMESTAMP"
                        + ")");
                stmt.execute("CREATE TABLE profile_notes ("
                        + "author_id UUID NOT NULL, "
                        + "subject_id UUID NOT NULL, "
                        + "content VARCHAR(500) NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "updated_at TIMESTAMP NOT NULL, "
                        + "deleted_at TIMESTAMP"
                        + ")");
                stmt.execute("CREATE TABLE notifications ("
                        + "id UUID PRIMARY KEY, "
                        + "user_id UUID NOT NULL, "
                        + "type VARCHAR(30) NOT NULL, "
                        + "title VARCHAR(200) NOT NULL, "
                        + "message TEXT NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "is_read BOOLEAN DEFAULT FALSE, "
                        + "data_json TEXT"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair'), "
                        + "(10, CURRENT_TIMESTAMP(), 'V10 named foreign keys'), "
                        + "(11, CURRENT_TIMESTAMP(), 'V11 friend request uniqueness helpers'), "
                        + "(12, CURRENT_TIMESTAMP(), 'V12 enum and length checks'), "
                        + "(13, CURRENT_TIMESTAMP(), 'V13 matches ended_by integrity')");

                MigrationRunner.runAllPending(stmt);

                stmt.execute("INSERT INTO users (id, name, min_age, max_age, created_at, updated_at, state) VALUES ('"
                        + userA
                        + "', 'User A', 18, 99, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')");
                stmt.execute("INSERT INTO users (id, name, min_age, max_age, created_at, updated_at, state) VALUES ('"
                        + userB
                        + "', 'User B', 18, 99, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')");

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO likes "
                                + "(id, who_likes, who_got_liked, direction, created_at) VALUES "
                                + "('44444444-4444-4444-4444-444444444444', '"
                                + userA
                                + "', '"
                                + userA
                                + "', 'LIKE', CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO friend_requests "
                                + "(id, from_user_id, to_user_id, created_at, status) VALUES "
                                + "('55555555-5555-5555-5555-555555555555', '"
                                + userA
                                + "', '"
                                + userA
                                + "', CURRENT_TIMESTAMP(), 'PENDING')"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO profile_views "
                                + "(viewer_id, viewed_id, viewed_at) VALUES "
                                + "('"
                                + userA
                                + "', '"
                                + userA
                                + "', CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO messages "
                                + "(id, conversation_id, sender_id, content, created_at) VALUES "
                                + "('66666666-6666-6666-6666-666666666666', '"
                                + conversationId
                                + "', '"
                                + userA
                                + "', '   ', CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO profile_notes "
                                + "(author_id, subject_id, content, created_at, updated_at) VALUES "
                                + "('"
                                + userA
                                + "', '"
                                + userB
                                + "', '   ', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO notifications "
                                + "(id, user_id, type, title, message, created_at) VALUES "
                                + "('77777777-7777-7777-7777-777777777777', '"
                                + userA
                                + "', 'NEW_MESSAGE', '   ', 'hello', CURRENT_TIMESTAMP())"));

                Set<String> userChecks = getCheckConstraintNames("USERS");
                assertTrue(userChecks.contains("CK_USERS_AGE_BOUNDS"), "Missing check: ck_users_age_bounds");
                assertTrue(userChecks.contains("CK_USERS_HEIGHT_BOUNDS"), "Missing check: ck_users_height_bounds");
                assertTrue(
                        userChecks.contains("CK_USERS_MAX_AGE_DIFF_NONNEGATIVE"),
                        "Missing check: ck_users_max_age_diff_nonnegative");
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 14")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 14 should be recorded");
            }
        }

        @Test
        @DisplayName("should add remaining enum-backed checks in V15")
        void v15MigrationAddsRemainingEnumBackedChecks() throws SQLException {
            String sampleUserId = "00000000-0000-0000-0000-000000000001";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
                stmt.execute("CREATE TABLE swipe_sessions ("
                        + "id UUID PRIMARY KEY, "
                        + "user_id UUID NOT NULL, "
                        + "started_at TIMESTAMP NOT NULL, "
                        + "last_activity_at TIMESTAMP NOT NULL, "
                        + "ended_at TIMESTAMP, "
                        + "state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', "
                        + "swipe_count INT NOT NULL DEFAULT 0, "
                        + "like_count INT NOT NULL DEFAULT 0, "
                        + "pass_count INT NOT NULL DEFAULT 0, "
                        + "match_count INT NOT NULL DEFAULT 0"
                        + ")");
                stmt.execute("CREATE TABLE user_interested_in ("
                        + "user_id UUID NOT NULL, "
                        + "gender VARCHAR(30) NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE user_db_smoking (user_id UUID NOT NULL, \"value\" VARCHAR(50) NOT NULL)");
                stmt.execute("CREATE TABLE user_db_drinking (user_id UUID NOT NULL, \"value\" VARCHAR(50) NOT NULL)");
                stmt.execute("CREATE TABLE user_db_wants_kids (user_id UUID NOT NULL, \"value\" VARCHAR(50) NOT NULL)");
                stmt.execute(
                        "CREATE TABLE user_db_looking_for (user_id UUID NOT NULL, \"value\" VARCHAR(50) NOT NULL)");
                stmt.execute("CREATE TABLE user_db_education (user_id UUID NOT NULL, \"value\" VARCHAR(50) NOT NULL)");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair'), "
                        + "(10, CURRENT_TIMESTAMP(), 'V10 named foreign keys'), "
                        + "(11, CURRENT_TIMESTAMP(), 'V11 friend request uniqueness helpers'), "
                        + "(12, CURRENT_TIMESTAMP(), 'V12 enum and length checks'), "
                        + "(13, CURRENT_TIMESTAMP(), 'V13 matches ended_by integrity'), "
                        + "(14, CURRENT_TIMESTAMP(), 'V14 structural and nonblank checks')");

                MigrationRunner.runAllPending(stmt);

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO swipe_sessions "
                                + "(id, user_id, started_at, last_activity_at, state, swipe_count, like_count, pass_count, match_count) VALUES "
                                + "('11111111-1111-1111-1111-111111111111', '"
                                + sampleUserId
                                + "', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'BROKEN', 0, 0, 0, 0)"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO user_interested_in (user_id, gender) VALUES ('"
                                + sampleUserId
                                + "', 'EVERYONE')"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute("INSERT INTO user_db_smoking (user_id, \"value\") VALUES ('"
                                + sampleUserId
                                + "', 'CHAIN_SMOKER')"));
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 15")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 15 should be recorded");
            }
        }

        @Test
        @DisplayName("should convert legacy timestamp columns to timestamp with time zone in V17")
        void v17MigrationConvertsLegacyTimestampColumnsToTimestampWithTimeZone() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users ("
                        + "id UUID PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "updated_at TIMESTAMP NOT NULL, "
                        + "state VARCHAR(20) NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE conversations ("
                        + "id VARCHAR(100) PRIMARY KEY, "
                        + "user_a UUID NOT NULL, "
                        + "user_b UUID NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL, "
                        + "last_message_at TIMESTAMP, "
                        + "deleted_at TIMESTAMP, "
                        + "visible_to_user_a BOOLEAN DEFAULT TRUE, "
                        + "visible_to_user_b BOOLEAN DEFAULT TRUE"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair'), "
                        + "(10, CURRENT_TIMESTAMP(), 'V10 named foreign keys'), "
                        + "(11, CURRENT_TIMESTAMP(), 'V11 friend request uniqueness helpers'), "
                        + "(12, CURRENT_TIMESTAMP(), 'V12 enum and length checks'), "
                        + "(13, CURRENT_TIMESTAMP(), 'V13 matches ended_by integrity'), "
                        + "(14, CURRENT_TIMESTAMP(), 'V14 structural and nonblank checks'), "
                        + "(15, CURRENT_TIMESTAMP(), 'V15 remaining enum checks'), "
                        + "(16, CURRENT_TIMESTAMP(), 'V16 query/index alignment')");

                MigrationRunner.runAllPending(stmt);
            }

            assertTrue(
                    getColumnTypeName("USERS", "CREATED_AT").contains("TIME ZONE"),
                    "users.created_at should migrate to TIMESTAMP WITH TIME ZONE");
            assertTrue(
                    getColumnTypeName("USERS", "UPDATED_AT").contains("TIME ZONE"),
                    "users.updated_at should migrate to TIMESTAMP WITH TIME ZONE");
            assertTrue(
                    getColumnTypeName("CONVERSATIONS", "CREATED_AT").contains("TIME ZONE"),
                    "conversations.created_at should migrate to TIMESTAMP WITH TIME ZONE");
            assertTrue(
                    getColumnTypeName("CONVERSATIONS", "LAST_MESSAGE_AT").contains("TIME ZONE"),
                    "conversations.last_message_at should migrate to TIMESTAMP WITH TIME ZONE");

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 17")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 17 should be recorded");
            }
        }

        @Test
        @DisplayName("should normalize and enforce unique email and phone values in V18")
        void v18MigrationNormalizesAndEnforcesUniqueContactValues() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE users ("
                        + "id UUID PRIMARY KEY, "
                        + "name VARCHAR(100) NOT NULL, "
                        + "email VARCHAR(200), "
                        + "phone VARCHAR(50), "
                        + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "state VARCHAR(20) NOT NULL"
                        + ")");
                stmt.execute("CREATE TABLE schema_version ("
                        + "version INT PRIMARY KEY, "
                        + "applied_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "description VARCHAR(255)"
                        + ")");
                stmt.execute("INSERT INTO schema_version(version, applied_at, description) VALUES "
                        + "(1, CURRENT_TIMESTAMP(), 'V1 baseline schema'), "
                        + "(2, CURRENT_TIMESTAMP(), 'V2 daily picks cache table'), "
                        + "(3, CURRENT_TIMESTAMP(), 'V3 normalized profile cleanup'), "
                        + "(4, CURRENT_TIMESTAMP(), 'V4 soft-delete columns'), "
                        + "(5, CURRENT_TIMESTAMP(), 'V5 profile view primary key'), "
                        + "(6, CURRENT_TIMESTAMP(), 'V6 matches updated_at backfill'), "
                        + "(7, CURRENT_TIMESTAMP(), 'V7 messages conversation index'), "
                        + "(8, CURRENT_TIMESTAMP(), 'V8 query optimization indexes'), "
                        + "(9, CURRENT_TIMESTAMP(), 'V9 matches updated_at repair'), "
                        + "(10, CURRENT_TIMESTAMP(), 'V10 named foreign keys'), "
                        + "(11, CURRENT_TIMESTAMP(), 'V11 friend request uniqueness helpers'), "
                        + "(12, CURRENT_TIMESTAMP(), 'V12 enum and length checks'), "
                        + "(13, CURRENT_TIMESTAMP(), 'V13 matches ended_by integrity'), "
                        + "(14, CURRENT_TIMESTAMP(), 'V14 structural and nonblank checks'), "
                        + "(15, CURRENT_TIMESTAMP(), 'V15 remaining enum checks'), "
                        + "(16, CURRENT_TIMESTAMP(), 'V16 query/index alignment'), "
                        + "(17, CURRENT_TIMESTAMP(), 'V17 timestamptz conversion')");

                stmt.execute(
                        "INSERT INTO users (id, name, email, phone, created_at, updated_at, state) VALUES "
                                + "('11111111-1111-1111-1111-111111111111', 'Alice', ' alice@example.com ', ' +1234567890 ', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')");

                MigrationRunner.runAllPending(stmt);

                assertThrows(
                        SQLException.class,
                        () -> stmt.execute(
                                "INSERT INTO users (id, name, email, created_at, updated_at, state) VALUES "
                                        + "('22222222-2222-2222-2222-222222222222', 'Bob', 'alice@example.com', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')"));
                assertThrows(
                        SQLException.class,
                        () -> stmt.execute(
                                "INSERT INTO users (id, name, phone, created_at, updated_at, state) VALUES "
                                        + "('33333333-3333-3333-3333-333333333333', 'Carol', '+1234567890', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'ACTIVE')"));
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT email, phone FROM users WHERE id = '11111111-1111-1111-1111-111111111111'")) {
                assertTrue(rs.next());
                assertEquals("alice@example.com", rs.getString(1));
                assertEquals("+1234567890", rs.getString(2));
            }

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 18")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 18 should be recorded");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private Set<String> getTableNames() throws SQLException {
        Set<String> tables = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private Set<String> getIndexNames() throws SQLException {
        Set<String> indexes = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        // Query indexes for all tables
        for (String table : getTableNames()) {
            try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        indexes.add(indexName);
                    }
                }
            }
        }
        return indexes;
    }

    private Set<String> getColumnNames(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private String getColumnTypeName(String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            assertTrue(rs.next(), "Expected column metadata for " + tableName + "." + columnName);
            return rs.getString("TYPE_NAME").toUpperCase();
        }
    }

    private Set<String> getUniqueConstraintNames(String tableName) throws SQLException {
        Set<String> constraints = new HashSet<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = '" + tableName + "' AND constraint_type = 'UNIQUE'")) {
            while (rs.next()) {
                constraints.add(rs.getString("CONSTRAINT_NAME"));
            }
        }
        return constraints;
    }

    private Set<String> getCheckConstraintNames(String tableName) throws SQLException {
        Set<String> constraints = new HashSet<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = '" + tableName + "' AND constraint_type = 'CHECK'")) {
            while (rs.next()) {
                constraints.add(rs.getString("CONSTRAINT_NAME"));
            }
        }
        return constraints;
    }

    private Set<String> getForeignKeyNames(String tableName) throws SQLException {
        Set<String> constraints = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                String constraintName = rs.getString("FK_NAME");
                if (constraintName != null) {
                    constraints.add(constraintName);
                }
            }
        }
        return constraints;
    }

    private Set<String> getPrimaryKeyColumns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private boolean invokeIsMissingTable(SQLException exception) throws Exception {
        var method = MigrationRunner.class.getDeclaredMethod("isMissingTable", SQLException.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, exception);
    }
}
