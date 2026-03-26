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
            assertTrue(indexes.contains("IDX_MESSAGES_SENDER_ID"), "Missing index: idx_messages_sender_id");
            assertTrue(indexes.contains("IDX_MESSAGES_CONVERSATION_ID"), "Missing index: idx_messages_conversation_id");

            // Normalized profile table indexes (V3)
            assertTrue(indexes.contains("IDX_USER_INTERESTS_INTEREST"), "Missing index: idx_user_interests_interest");
            assertTrue(
                    indexes.contains("IDX_USER_INTERESTED_IN_GENDER"), "Missing index: idx_user_interested_in_gender");
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
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 10")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 10 should be recorded");
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
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 10")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 10 should still be recorded once");
            }
        }

        @Test
        @DisplayName("should treat a missing schema_version table as not applied")
        void missingSchemaVersionTableIsNotApplied() throws SQLException {
            assertFalse(MigrationRunner.isVersionApplied(connection, 1));
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
        @DisplayName("should add standalone messages conversation index when upgrading a legacy database")
        void v7MigrationAddsMessagesConversationIdIndex() throws SQLException {
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
            assertTrue(
                    indexes.contains("IDX_MESSAGES_CONVERSATION_ID"),
                    "Migration should add idx_messages_conversation_id");
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
}
