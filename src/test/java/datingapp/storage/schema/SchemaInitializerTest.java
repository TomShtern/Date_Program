package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
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
 * Tests for {@link SchemaInitializer} — validates that all expected tables and indexes are created
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

            // Core tables
            assertTrue(tables.contains("USERS"), "Missing table: users");
            assertTrue(tables.contains("LIKES"), "Missing table: likes");
            assertTrue(tables.contains("MATCHES"), "Missing table: matches");
            assertTrue(tables.contains("SWIPE_SESSIONS"), "Missing table: swipe_sessions");

            // Stats tables
            assertTrue(tables.contains("USER_STATS"), "Missing table: user_stats");
            assertTrue(tables.contains("PLATFORM_STATS"), "Missing table: platform_stats");

            // Feature tables
            assertTrue(tables.contains("DAILY_PICK_VIEWS"), "Missing table: daily_pick_views");
            assertTrue(tables.contains("USER_ACHIEVEMENTS"), "Missing table: user_achievements");

            // Messaging
            assertTrue(tables.contains("CONVERSATIONS"), "Missing table: conversations");
            assertTrue(tables.contains("MESSAGES"), "Missing table: messages");

            // Social
            assertTrue(tables.contains("FRIEND_REQUESTS"), "Missing table: friend_requests");
            assertTrue(tables.contains("NOTIFICATIONS"), "Missing table: notifications");

            // Moderation
            assertTrue(tables.contains("BLOCKS"), "Missing table: blocks");
            assertTrue(tables.contains("REPORTS"), "Missing table: reports");

            // Profile
            assertTrue(tables.contains("PROFILE_NOTES"), "Missing table: profile_notes");
            assertTrue(tables.contains("PROFILE_VIEWS"), "Missing table: profile_views");

            // Standouts & Undo
            assertTrue(tables.contains("STANDOUTS"), "Missing table: standouts");
            assertTrue(tables.contains("UNDO_STATES"), "Missing table: undo_states");
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
        }
    }

    @Nested
    @DisplayName("MigrationRunner.migrateV1")
    @SuppressWarnings("unused")
    class MigrateV1 {

        @Test
        @DisplayName("should create all tables and record schema version on fresh database")
        void freshDatabaseMigration() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.migrateV1(stmt);
            }

            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"), "Missing table: users");
            assertTrue(tables.contains("SCHEMA_VERSION"), "Missing table: schema_version");

            // Verify version was recorded
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema version 1 should be recorded");
            }
        }

        @Test
        @DisplayName("should be idempotent — running twice does not fail")
        void isIdempotent() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.migrateV1(stmt);
                // Run again — should take the migration branch instead
                MigrationRunner.migrateV1(stmt);
            }

            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"));
        }

        @Test
        @DisplayName("should handle existing database with version already applied")
        void existingDatabaseMigration() throws SQLException {
            // First run — creates everything
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.migrateV1(stmt);
            }

            // Second run — should enter migration branch without error
            try (Statement stmt = connection.createStatement()) {
                MigrationRunner.migrateV1(stmt);
            }

            // Verify schema is still intact
            Set<String> tables = getTableNames();
            assertTrue(tables.contains("USERS"));
            assertTrue(tables.contains("MATCHES"));
            assertTrue(tables.contains("CONVERSATIONS"));
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
}
