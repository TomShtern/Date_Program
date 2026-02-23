package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Schema parity test: validates that fresh and upgraded databases produce
 * identical schemas.
 *
 * <p>
 * This is the automated safety net against schema drift. If ANY element (table,
 * column, index)
 * exists in one path but not the other, this test fails immediately.
 *
 * <p>
 * Approach: creates two independent in-memory H2 databases:
 *
 * <ol>
 * <li><b>Fresh path:</b> runs {@link MigrationRunner#runAllPending} on an empty
 * database.
 * <li><b>Upgrade path:</b> creates a partial legacy schema (3 core tables),
 * records version 1
 * as already applied (simulating a database that ran under the old migration
 * system which
 * only added columns), then runs {@code runAllPending}. V1 is skipped; V2
 * backfills the
 * missing FKs, indexes, and remaining tables.
 * </ol>
 *
 * Both paths must produce identical tables, columns, and indexes.
 */
@Timeout(15)
class SchemaParityTest {

    @Test
    @DisplayName("fresh and upgraded databases should have identical schemas")
    void freshAndUpgradedSchemasMatch() throws SQLException {
        SchemaSnapshot fresh = buildFreshSchema();
        SchemaSnapshot upgraded = buildUpgradedSchema();

        assertEquals(fresh.tables(), upgraded.tables(), "Table sets differ between fresh and upgraded databases");

        for (String table : fresh.tables()) {
            assertEquals(fresh.columnsFor(table), upgraded.columnsFor(table), "Columns differ for table: " + table);
            assertEquals(fresh.indexesFor(table), upgraded.indexesFor(table), "Indexes differ for table: " + table);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Database builders
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs the full migration pipeline on an empty database. This is the "fresh
     * install" scenario.
     */
    private static SchemaSnapshot buildFreshSchema() throws SQLException {
        String url = "jdbc:h2:mem:parity_fresh_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {
            MigrationRunner.runAllPending(stmt);
            return captureSchema(conn);
        }
    }

    /**
     * Simulates a pre-existing database that was migrated under the old system.
     * Creates 3 core
     * tables (matching the original schema before migration versioning was
     * introduced), records V1
     * as already applied, then runs the new pipeline. V1 is skipped; V2 backfills
     * everything else.
     */
    private static SchemaSnapshot buildUpgradedSchema() throws SQLException {
        String url = "jdbc:h2:mem:parity_upgrade_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement stmt = conn.createStatement()) {

            // Recreate the original "pre-migration-system" schema: only the 3 core tables
            // existed
            // before versioned migrations were introduced. These tables match what
            // SchemaInitializer
            // creates (same DDL), so the column/FK comparison should be a clean baseline.
            createLegacyCoreTables(stmt);

            // Mark V1 as already applied — this is exactly what the old migration system
            // did
            // (it ran only migrateSchemaColumns(), then recorded version 1).
            MigrationRunner.createSchemaVersionTable(stmt);
            MigrationRunner.recordSchemaVersion(stmt, 1, "Simulated old-V1 run (columns only, no FKs/indexes)");

            // Run the new pipeline. V1 is skipped (already recorded). V2 runs and backfills
            // all missing tables, FKs, indexes, and constraints.
            MigrationRunner.runAllPending(stmt);

            return captureSchema(conn);
        }
    }

    /**
     * Creates the 3 core tables in exactly the form SchemaInitializer defines them.
     * This simulates
     * the state of a production database that was created before the migration
     * versioning system
     * existed. The remaining ~15 tables will be created by applyV2() via
     * createAllTables().
     */
    private static void createLegacyCoreTables(Statement stmt) throws SQLException {
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
                            has_location_set BOOLEAN DEFAULT FALSE,
                            max_distance_km INT DEFAULT 50,
                            min_age INT DEFAULT 18,
                            max_age INT DEFAULT 99,
                            photo_urls VARCHAR(1000),
                            state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
                            created_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP NOT NULL,
                            smoking VARCHAR(20),
                            drinking VARCHAR(20),
                            wants_kids VARCHAR(20),
                            looking_for VARCHAR(20),
                            education VARCHAR(20),
                            height_cm INT,
                            db_smoking VARCHAR(100),
                            db_drinking VARCHAR(100),
                            db_wants_kids VARCHAR(100),
                            db_looking_for VARCHAR(100),
                            db_education VARCHAR(200),
                            db_min_height_cm INT,
                            db_max_height_cm INT,
                            db_max_age_diff INT,
                            interests VARCHAR(500),
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
                            deleted_at TIMESTAMP
                        )
                        """);

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
                            CONSTRAINT uk_likes UNIQUE (who_likes, who_got_liked)
                        )
                        """);

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
                            CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a)
                                REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b)
                                REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT uk_matches UNIQUE (user_a, user_b)
                        )
                        """);
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema snapshot infrastructure
    // ═══════════════════════════════════════════════════════════════

    /**
     * Immutable snapshot of a database schema: tables, columns per table, and
     * indexes per table.
     * SCHEMA_VERSION is excluded — it's migration infrastructure, not application
     * schema.
     */
    private record SchemaSnapshot(
            Set<String> tables, Map<String, Set<String>> columns, Map<String, Set<String>> indexes) {

        Set<String> columnsFor(String table) {
            return columns.getOrDefault(table, Set.of());
        }

        Set<String> indexesFor(String table) {
            return indexes.getOrDefault(table, Set.of());
        }
    }

    /**
     * Captures the schema of the given connection into a {@link SchemaSnapshot}.
     * Uses
     * {@link DatabaseMetaData} so the result is authoritative (not dependent on
     * application logic).
     * Table/column/index names are returned in uppercase by H2's
     * INFORMATION_SCHEMA.
     */
    private static SchemaSnapshot captureSchema(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        assertNotNull(meta, "DatabaseMetaData must not be null");

        Set<String> tables = new TreeSet<>();
        Map<String, Set<String>> columns = new TreeMap<>();
        Map<String, Set<String>> indexes = new TreeMap<>();

        // Collect all user-defined tables, excluding the migration infrastructure table
        try (ResultSet rs = meta.getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!"SCHEMA_VERSION".equals(name)) {
                    tables.add(name);
                }
            }
        }

        // Collect columns (name + type + size for structural comparison) and indexes
        // per table
        for (String table : tables) {
            Set<String> cols = new TreeSet<>();
            try (ResultSet rs = meta.getColumns(null, "PUBLIC", table, "%")) {
                while (rs.next()) {
                    // Include name, type, and size to catch both missing columns and type changes
                    String colDescriptor = rs.getString("COLUMN_NAME")
                            + ":"
                            + rs.getString("TYPE_NAME")
                            + "("
                            + rs.getInt("COLUMN_SIZE")
                            + ")";
                    cols.add(colDescriptor);
                }
            }
            columns.put(table, cols);

            Set<String> idxs = new TreeSet<>();
            try (ResultSet rs = meta.getIndexInfo(null, "PUBLIC", table, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        idxs.add(indexName);
                    }
                }
            }
            indexes.put(table, idxs);
        }

        return new SchemaSnapshot(tables, columns, indexes);
    }
}
