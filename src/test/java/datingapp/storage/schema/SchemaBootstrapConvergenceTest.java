package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Schema bootstrap convergence")
class SchemaBootstrapConvergenceTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        System.setProperty("datingapp.db.profile", "test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("datingapp.db.profile");
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("fresh baseline contains V22/V23/V24 objects and migrations are idempotent")
    void freshBaselineConvergesWithMigrations() throws Exception {
        String dbName = "convergence_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        DatabaseManager dbManager = DatabaseManager.getInstance();
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement()) {
            Set<String> userColumns = columns(stmt, "USERS");
            assertTrue(userColumns.contains("VERIFICATION_ATTEMPT_COUNT"), "V22 column missing: " + userColumns);
            assertTrue(userColumns.contains("VERIFICATION_LOCKED_UNTIL"), "V22 column missing");
            assertTrue(userColumns.contains("VERIFICATION_LAST_SENT_AT"), "V22 column missing");
            assertTrue(hasTable(stmt, "MODERATION_AUDIT"), "V23 table missing");
            assertTrue(hasTable(stmt, "PRESENCE_HEARTBEAT"), "V24 table missing");
            // Idempotent re-run must not fail (per-version commits + IF NOT EXISTS).
            MigrationRunner.runAllPending(stmt);
            assertTrue(hasTable(stmt, "PRESENCE_HEARTBEAT"), "V24 table missing after re-run");
        }
    }

    @Test
    @DisplayName("V25 upgrades a V24 database to the same shape as a fresh one")
    void v25UpgradeMatchesFreshBaseline() throws Exception {
        String dbName = "convergence_v25_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        DatabaseManager dbManager = DatabaseManager.getInstance();
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement()) {
            // Fresh baseline already has the V25 shape.
            assertEquals(64, columnSize(stmt, "USERS", "VERIFICATION_CODE"));
            assertTrue(columns(stmt, "USERS").contains("PURGED_AT"), "fresh users.purged_at missing");
            assertTrue(hasTable(stmt, "DEPARTED_ACCOUNT_FLAGS"), "fresh departed_account_flags missing");

            // Rewind to the V24 shape, with a plaintext code in flight.
            stmt.execute("DELETE FROM schema_version WHERE version = 25");
            stmt.execute("DROP TABLE departed_account_flags");
            stmt.execute("ALTER TABLE users DROP COLUMN purged_at");
            stmt.execute("ALTER TABLE users ALTER COLUMN verification_code SET DATA TYPE VARCHAR(10)");
            UUID userId = UUID.randomUUID();
            stmt.execute("INSERT INTO users (id, name, state, created_at, updated_at, verification_code) VALUES ('"
                    + userId + "', 'Legacy', 'INCOMPLETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '123456')");
            conn.commit();

            MigrationRunner.runAllPending(stmt);

            assertEquals(64, columnSize(stmt, "USERS", "VERIFICATION_CODE"));
            assertTrue(columns(stmt, "USERS").contains("PURGED_AT"), "V25 column missing");
            assertTrue(hasTable(stmt, "DEPARTED_ACCOUNT_FLAGS"), "V25 table missing");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM users WHERE id = '" + userId + "' AND verification_code IS NULL")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "in-flight plaintext code must be dropped");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version WHERE version = 25")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    private static int columnSize(Statement stmt, String table, String column) throws Exception {
        try (ResultSet rs = stmt.getConnection().getMetaData().getColumns(null, null, table, column)) {
            assertTrue(rs.next(), "column not found: " + table + "." + column);
            return rs.getInt("COLUMN_SIZE");
        }
    }

    private static boolean hasTable(Statement stmt, String table) throws Exception {
        try (ResultSet rs = stmt.getConnection().getMetaData().getTables(null, null, table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static Set<String> columns(Statement stmt, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = stmt.getConnection().getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                names.add(rs.getString("COLUMN_NAME"));
            }
        }
        return names;
    }
}
