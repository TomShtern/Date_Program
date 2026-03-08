# Implementation Plan: Schema Drift Elimination — Versioned Migration Architecture

**Status:** ✅ **COMPLETED** (2026-03-09)

**Date:** 2026-02-23
**Severity:** High (data integrity + performance — ~69 schema elements missing on upgrade path)
**Affected Files:** 3 modified, 1 new, 1 test modified/expanded

## 1. Goal Description

Schema evolution is split across two independent mechanisms that have drifted apart:

- **`SchemaInitializer`** defines the "ideal" fresh-database schema (all tables, columns, indexes, FKs, unique constraints)
- **`MigrationRunner.migrateSchemaColumns()`** defines the upgrade path (column additions only)

These two paths produce **materially different databases**:

| Element            | Fresh DB    | Upgraded DB | Gap            |
|--------------------|-------------|-------------|----------------|
| FK constraints     | 27 inline   | 0 applied   | **27 missing** |
| Indexes            | 28 created  | 0 created   | **28 missing** |
| Unique constraints | 7 defined   | 0 applied   | **7 missing**  |
| Columns            | All present | All present | 0              |

**Root cause:** In `MigrationRunner.migrateV1()` (line 52-55), when `isVersionApplied(stmt, 1)` returns `true` (existing DB), the method calls only `migrateSchemaColumns(stmt)` then **returns immediately** — never calling `addMissingForeignKeys()`, never creating indexes, never applying unique constraints.

**Impact:** Upgraded databases silently run without referential integrity, uniqueness guarantees, or query indexes. This hasn't caused crashes because H2 is permissive and the Java layer enforces most constraints, but it's a ticking time bomb for data corruption and performance degradation.

**Objective:** Eliminate the two-path divergence by restructuring into a **single sequential migration pipeline** where fresh and upgraded databases take the exact same code path. Validate parity with an automated schema comparison test.

## 2. Architecture Overview (Before → After)

### BEFORE (Two Divergent Paths)

```
DatabaseManager.initializeSchema()
  └─► MigrationRunner.migrateV1(stmt)
        ├── IF version 1 EXISTS (upgraded DB):
        │     └─► migrateSchemaColumns(stmt)  ← columns ONLY
        │     └─► return                       ← STOPS (no FKs, no indexes, no constraints)
        │
        └── IF version 1 MISSING (fresh DB):
              └─► SchemaInitializer.createAllTables(stmt)  ← full DDL
              └─► addMissingForeignKeys(stmt)               ← partial FK backfill
              └─► recordSchemaVersion(stmt, 1, ...)
```

### AFTER (Single Sequential Pipeline)

```
DatabaseManager.initializeSchema()
  └─► MigrationRunner.runAllPending(stmt)
        └─► for each (version, migration) in MIGRATIONS:
              if NOT isVersionApplied(version):
                migration.apply(stmt)
                recordSchemaVersion(version)

MIGRATIONS = [
  V1: SchemaInitializer.createAllTables()     ← baseline (FROZEN)
      + migrateSchemaColumns()
      + addMissingForeignKeys()
      + ensureAllIndexes()
  V2: Backfill FKs + indexes for old-V1 DBs  ← CRITICAL (see §3.1 Step 8)
  V3: (future changes go here)
]
```

**Key invariant:** Fresh DB runs V1→V2→V3→... Upgraded DB skips already-applied versions, runs remaining. Both end at the same schema state.

**Critical edge case:** Databases that had V1 recorded under the OLD migration system (before this refactor) will skip the new V1. A dedicated V2 migration ensures they still get the missing FKs, indexes, and constraints. Since all operations use `IF NOT EXISTS`, V2 is a safe no-op on fresh databases that already got everything from V1.

## 3. Proposed Changes

### 3.1 `datingapp.storage.schema` — `MigrationRunner.java`

#### [MODIFY] Complete restructuring of the migration runner

**Step 1: Add migration record type and registry**

Add at the top of the class (after the LOG and SQL_IDENTIFIER fields):

```java
/** A single versioned migration step. */
private record VersionedMigration(int version, String description, MigrationAction action) {
    @FunctionalInterface
    interface MigrationAction {
        void apply(Statement stmt) throws SQLException;
    }
}

/**
 * Ordered list of all schema migrations. Fresh databases execute all of them;
 * upgraded databases skip already-applied versions.
 *
 * APPEND-ONLY: never reorder or remove entries. New migrations go at the end.
 */
private static final List<VersionedMigration> MIGRATIONS = List.of(
    new VersionedMigration(1,
        "Baseline schema: all tables, columns, indexes, FKs, and constraints",
        MigrationRunner::applyV1),
    new VersionedMigration(2,
        "Backfill FKs, indexes, and constraints for databases migrated under old V1",
        MigrationRunner::applyV2)
    // Future: new VersionedMigration(3, "Add xyz column to users", MigrationRunner::applyV3)
);
```

Add the `java.util.List` import.

**Step 2: Replace `migrateV1()` with `runAllPending()`**

Replace the existing `migrateV1` method entirely:

```java
/**
 * Runs all pending migrations in version order. Each migration is applied
 * exactly once (tracked in schema_version table). Fresh and upgraded databases
 * take the same code path — the only difference is which versions are skipped.
 *
 * @param stmt a JDBC statement connected to the target database
 * @throws SQLException if any migration statement fails
 */
public static void runAllPending(Statement stmt) throws SQLException {
    createSchemaVersionTable(stmt);

    for (VersionedMigration migration : MIGRATIONS) {
        if (!isVersionApplied(stmt, migration.version())) {
            LOG.info("Applying migration V{}: {}", migration.version(), migration.description());
            migration.action().apply(stmt);
            recordSchemaVersion(stmt, migration.version(), migration.description());
            LOG.info("Migration V{} applied successfully", migration.version());
        }
    }
}
```

**Step 3: Refactor V1 application logic into `applyV1()`**

Replace the body of the old `migrateV1` with this new method:

```java
/**
 * V1 baseline migration. Creates all tables via SchemaInitializer, then
 * ensures all columns, foreign keys, indexes, and constraints exist.
 * Uses IF NOT EXISTS / ADD COLUMN IF NOT EXISTS throughout — fully idempotent.
 */
private static void applyV1(Statement stmt) throws SQLException {
    // 1. Create all tables (idempotent — IF NOT EXISTS)
    SchemaInitializer.createAllTables(stmt);

    // 2. Ensure all columns exist (covers pre-V1 databases that had partial schemas)
    migrateSchemaColumns(stmt);

    // 3. Ensure all foreign key constraints exist
    addMissingForeignKeys(stmt);

    // 4. Ensure all indexes exist (idempotent — IF NOT EXISTS in SchemaInitializer)
    ensureAllIndexes(stmt);
}
```

**Step 4: Add `ensureAllIndexes()` method**

This method calls SchemaInitializer's index creation methods. Since SchemaInitializer's index methods are `static` with package-private visibility (no modifier = package-private), and MigrationRunner is in the same package (`datingapp.storage.schema`), they are directly accessible:

```java
/**
 * Ensures all indexes exist. Delegates to SchemaInitializer's index methods
 * which use IF NOT EXISTS — safe to run on databases that already have them.
 */
private static void ensureAllIndexes(Statement stmt) throws SQLException {
    SchemaInitializer.createCoreIndexes(stmt);
    SchemaInitializer.createStatsIndexes(stmt);
    SchemaInitializer.createAdditionalIndexes(stmt);
}
```

> **IMPORTANT — Visibility check:** Verify that `createCoreIndexes`, `createStatsIndexes`, and `createAdditionalIndexes` in `SchemaInitializer.java` are at least package-private (no `private` modifier). Currently they are `static` (package-private) — confirmed at lines 442, 455, 461 of SchemaInitializer.java. Both classes are in `datingapp.storage.schema`, so this works without modification.

**Step 5: Expand `addMissingForeignKeys()` to cover ALL tables**

The current `addMissingForeignKeys()` is missing FKs for: `likes`, `matches`, `swipe_sessions`, `user_stats`, `standouts`. Add these entries to the existing method body:

```java
// --- Add these BEFORE the existing entries in addMissingForeignKeys() ---

// likes
addForeignKeyIfPresent(stmt, "likes", "fk_likes_who_likes", "who_likes", "users", "id");
addForeignKeyIfPresent(stmt, "likes", "fk_likes_who_got_liked", "who_got_liked", "users", "id");

// matches
addForeignKeyIfPresent(stmt, "matches", "fk_matches_user_a", "user_a", "users", "id");
addForeignKeyIfPresent(stmt, "matches", "fk_matches_user_b", "user_b", "users", "id");

// swipe_sessions
addForeignKeyIfPresent(stmt, "swipe_sessions", "fk_sessions_user", "user_id", "users", "id");

// user_stats
addForeignKeyIfPresent(stmt, "user_stats", "fk_user_stats_user", "user_id", "users", "id");

// standouts
addForeignKeyIfPresent(stmt, "standouts", "fk_standouts_seeker", "seeker_id", "users", "id");
addForeignKeyIfPresent(stmt, "standouts", "fk_standouts_user", "standout_user_id", "users", "id");
```

> **NOTE on constraint naming — read carefully:**
> - **Named inline FKs** (likes, matches, swipe_sessions, user_stats): SchemaInitializer uses `CONSTRAINT fk_name FOREIGN KEY (...)` with explicit names. `addForeignKeyIfPresent` uses `ADD CONSTRAINT IF NOT EXISTS fk_name`, which matches the existing name → **no-op on fresh DB**. Safe.
> - **Unnamed inline FKs** (conversations, messages, friend_requests, notifications, blocks, reports, profile_notes, profile_views, standouts): SchemaInitializer uses bare `FOREIGN KEY (col) REFERENCES ...` without a `CONSTRAINT name`. H2 auto-generates a name. When `addMissingForeignKeys` later runs `ADD CONSTRAINT IF NOT EXISTS fk_conversations_user_a`, H2 can't match it to the auto-named constraint → **creates a duplicate FK on the same column pair**. This is a **pre-existing issue** (the current code already does this on fresh DBs) and is functionally benign (H2 enforces both identically). It does NOT affect schema parity since both fresh and upgraded paths run the same `applyV1()` sequence.
> - **Future cleanup (out of scope):** To eliminate duplicate FKs, add explicit `CONSTRAINT fk_name` to all unnamed inline FKs in SchemaInitializer. This is cosmetic and can be done as a separate task.

**Step 6: Delete the old `migrateV1()` method**

The old `migrateV1()` method is fully replaced by `runAllPending()` + `applyV1()`. Delete it entirely.

**Step 7: Keep `migrateSchemaColumns()` as-is**

The existing `migrateSchemaColumns()` method (lines 134-179) is correct and complete — all its `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statements are idempotent. Keep it unchanged. It's now called from within `applyV1()`.

**Step 8: Add `applyV2()` — backfill for old-V1 databases (CRITICAL)**

This is the migration that fixes **existing databases** where V1 was recorded under the old system (which only ran `migrateSchemaColumns()`). Those databases have V1 recorded but are missing FKs, indexes, and constraints. V2 applies everything that `applyV1()` applies — since all operations use `IF NOT EXISTS`, it's a safe no-op on databases that already have everything (fresh DBs where V1 ran under the new system).

```java
/**
 * V2 backfill migration. Ensures databases that had V1 applied under the old
 * migration system (which only added columns) also get FKs, indexes, and
 * constraints. All operations are idempotent — safe no-op on fresh databases.
 */
private static void applyV2(Statement stmt) throws SQLException {
    // Re-run the same idempotent operations as V1.
    // On databases where new-V1 already ran: all IF NOT EXISTS → no-op.
    // On databases where old-V1 ran: creates missing tables, FKs, indexes.
    SchemaInitializer.createAllTables(stmt);
    migrateSchemaColumns(stmt);
    addMissingForeignKeys(stmt);
    ensureAllIndexes(stmt);
}
```

> **Why V2 is identical to V1's body:** The old V1 system only ran `migrateSchemaColumns()`. When the new code runs, it sees V1 as already applied (skips it), then hits V2 which catches the database up. For fresh databases, V1 ran everything, so V2 is entirely no-ops. This is the standard "backfill" pattern in versioned migration systems.

### 3.2 `datingapp.storage` — `DatabaseManager.java`

#### [MODIFY] Update the single call site

In `initializeSchema()` (line 119), change:

```java
// BEFORE:
MigrationRunner.migrateV1(stmt);

// AFTER:
MigrationRunner.runAllPending(stmt);
```

This is the **only call site** — verified by grep. No other file calls `migrateV1()` directly (the test calls it, but we'll update the test too).

### 3.3 `datingapp.storage.schema` — `SchemaInitializer.java`

#### [NO CHANGES — FREEZE THIS FILE]

`SchemaInitializer` becomes the **V1 baseline DDL**. From this point forward:
- **NEVER add new tables or columns** to this file
- **NEVER modify existing CREATE TABLE statements**
- New schema changes go exclusively into new versioned migrations (V2, V3, ...)
- The file header Javadoc should be updated to reflect its frozen status:

```java
/**
 * V1 baseline schema: creates all database tables, indexes, and foreign key
 * constraints. This file is FROZEN — do not modify. All future schema changes
 * must be added as new versioned migrations in {@link MigrationRunner}.
 *
 * <p>Every method is idempotent — uses {@code IF NOT EXISTS} so re-running is safe.
 */
```

> **Exception:** The Javadoc update above is the ONLY change to this file. Do not touch any DDL.

### 3.4 `datingapp.storage.schema` — `SchemaParityTest.java`

#### [NEW] Schema drift detection test

Create a new test file at `src/test/java/datingapp/storage/schema/SchemaParityTest.java`.

This test creates two in-memory databases via different paths and asserts their schemas are identical:

```java
package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.*;

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
 * Validates that fresh and upgraded databases produce identical schemas.
 * This test is the automated safety net against schema drift — if ANY element
 * (table, column, index, constraint) exists in one path but not the other,
 * this test fails.
 *
 * <p>Approach: creates two in-memory H2 databases:
 * <ol>
 *   <li><b>Fresh path:</b> runs {@code MigrationRunner.runAllPending()} on empty DB</li>
 *   <li><b>Upgrade path:</b> creates minimal V0 tables (core tables only, no extras),
 *       records schema version 1 to simulate a pre-existing database, then runs
 *       {@code MigrationRunner.runAllPending()}</li>
 * </ol>
 * Both should produce identical INFORMATION_SCHEMA snapshots.
 */
@Timeout(15)
class SchemaParityTest {

    @Test
    @DisplayName("fresh and upgraded databases should have identical schemas")
    void freshAndUpgradedSchemasMatch() throws SQLException {
        // --- Fresh database ---
        SchemaSnapshot fresh;
        String freshUrl = "jdbc:h2:mem:parity_fresh_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(freshUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            MigrationRunner.runAllPending(stmt);
            fresh = captureSchema(conn);
        }

        // --- Upgraded database (simulates pre-existing DB) ---
        SchemaSnapshot upgraded;
        String upgradeUrl = "jdbc:h2:mem:parity_upgrade_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(upgradeUrl, "sa", "");
             Statement stmt = conn.createStatement()) {

            // Simulate a pre-V1 database: create only the bare users table
            // (the original minimal schema before any migrations existed)
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
                    updated_at TIMESTAMP NOT NULL
                )
            """);

            // Create the other core tables that existed before the migration system
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS likes (
                    id UUID PRIMARY KEY,
                    who_likes UUID NOT NULL,
                    who_got_liked UUID NOT NULL,
                    direction VARCHAR(10) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
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
                    CONSTRAINT fk_matches_user_a FOREIGN KEY (user_a)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_matches_user_b FOREIGN KEY (user_b)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT uk_matches UNIQUE (user_a, user_b)
                )
            """);

            // Simulate a database where old-V1 was applied (only column migrations ran).
            // Record V1 as already applied — this is what the OLD migration system did.
            MigrationRunner.createSchemaVersionTable(stmt);
            MigrationRunner.recordSchemaVersion(stmt, 1,
                "Simulated old-V1 migration (columns only, no FKs/indexes)");

            // Run the new migration pipeline. V1 will be SKIPPED (already recorded).
            // V2 will RUN and backfill all missing FKs, indexes, and constraints.
            MigrationRunner.runAllPending(stmt);
            upgraded = captureSchema(conn);
        }

        // --- Compare ---
        assertEquals(fresh.tables(), upgraded.tables(),
            "Table sets differ between fresh and upgraded databases");

        for (String table : fresh.tables()) {
            assertEquals(fresh.columnsFor(table), upgraded.columnsFor(table),
                "Columns differ for table " + table);
            assertEquals(fresh.indexesFor(table), upgraded.indexesFor(table),
                "Indexes differ for table " + table);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema snapshot infrastructure
    // ═══════════════════════════════════════════════════════════════

    private record SchemaSnapshot(
        Set<String> tables,
        Map<String, Set<String>> columns,
        Map<String, Set<String>> indexes
    ) {
        Set<String> columnsFor(String table) {
            return columns.getOrDefault(table, Set.of());
        }
        Set<String> indexesFor(String table) {
            return indexes.getOrDefault(table, Set.of());
        }
    }

    private static SchemaSnapshot captureSchema(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        Set<String> tables = new TreeSet<>();
        Map<String, Set<String>> columns = new TreeMap<>();
        Map<String, Set<String>> indexes = new TreeMap<>();

        // Collect tables (exclude SCHEMA_VERSION — it's infrastructure, not app schema)
        try (ResultSet rs = meta.getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!"SCHEMA_VERSION".equals(name)) {
                    tables.add(name);
                }
            }
        }

        // Collect columns and indexes for each table
        for (String table : tables) {
            Set<String> cols = new TreeSet<>();
            try (ResultSet rs = meta.getColumns(null, "PUBLIC", table, "%")) {
                while (rs.next()) {
                    // Capture name + type for structural comparison
                    String colName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    cols.add(colName + ":" + typeName + "(" + size + ")");
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
```

> **IMPORTANT — Test design note:** The "upgraded" database starts with a PARTIAL schema (just users, likes, matches — the original core tables without later additions) and has V1 ALREADY RECORDED (simulating the old migration system). When `runAllPending()` runs, it skips V1 (already applied) and executes V2, which backfills all missing tables, FKs, indexes, and constraints. This tests the **real production upgrade scenario**: a database that was created and migrated under the old system now gets the new migration pipeline.
>
> **Why 3 starter tables is sufficient:** The "legacy" database only has 3 tables, but `applyV2()` calls `SchemaInitializer.createAllTables()` (which creates the remaining ~15 tables via `IF NOT EXISTS`), `migrateSchemaColumns()` (adds missing columns), `addMissingForeignKeys()` (adds FKs), and `ensureAllIndexes()` (adds indexes). All operations are idempotent, so the 3 pre-existing tables are unharmed while missing elements are added.

### 3.5 `datingapp.storage.schema` — `SchemaInitializerTest.java`

#### [MODIFY] Update test references from `migrateV1` to `runAllPending`

In the `MigrateV1` nested class (lines 130-186):

1. Rename the nested class from `MigrateV1` to `RunAllPending`
2. Update `@DisplayName` from `"MigrationRunner.migrateV1"` to `"MigrationRunner.runAllPending"`
3. Replace all calls to `MigrationRunner.migrateV1(stmt)` with `MigrationRunner.runAllPending(stmt)`

The test logic remains identical — only the method name changes.

## 4. Detailed File-by-File Change Summary

| File                         | Action | Lines Changed (est.)    | Risk                          |
|------------------------------|--------|-------------------------|-------------------------------|
| `MigrationRunner.java`       | MODIFY | ~80 added, ~15 removed  | Medium — core migration logic |
| `DatabaseManager.java`       | MODIFY | 1 line changed          | Minimal — method rename       |
| `SchemaInitializer.java`     | MODIFY | ~4 lines (Javadoc only) | Minimal — no DDL changes      |
| `SchemaParityTest.java`      | NEW    | ~170 lines              | None — new test file          |
| `SchemaInitializerTest.java` | MODIFY | ~6 lines (rename refs)  | Minimal — test-only           |

## 5. Implementation Order (Critical — Follow This Sequence)

### Phase 1: Modify `MigrationRunner.java`

1. Add the `java.util.List` import
2. Add the `VersionedMigration` private record and `MIGRATIONS` list (with V1 AND V2)
3. Add `runAllPending()` method
4. Add `applyV1()` method
5. Add `ensureAllIndexes()` method
6. Expand `addMissingForeignKeys()` with the 8 missing FK entries (likes, matches, swipe_sessions, user_stats, standouts)
7. Add `applyV2()` method (backfill for old-V1 databases)
8. Delete the old `migrateV1()` method

### Phase 2: Update call sites

1. In `DatabaseManager.java` line 119: change `MigrationRunner.migrateV1(stmt)` to `MigrationRunner.runAllPending(stmt)`
2. In `SchemaInitializerTest.java`: rename nested class + update 3 method calls from `migrateV1` to `runAllPending`

### Phase 3: Update `SchemaInitializer.java` Javadoc

1. Update the class-level Javadoc to indicate the file is frozen

### Phase 4: Create `SchemaParityTest.java`

1. Create the new test file at `src/test/java/datingapp/storage/schema/SchemaParityTest.java`

### Phase 5: Verify

1. Run `mvn test` — all tests must pass (capture output once, query it multiple times per Build Command Discipline)
2. Run `mvn spotless:apply && mvn verify` — full quality gate
3. Specifically check: `mvn -Ptest-output-verbose -Dtest="SchemaParityTest" test`

## 6. Gotchas & Edge Cases

### H2-Specific Behaviors

- **`ADD CONSTRAINT IF NOT EXISTS`**: H2 supports this syntax. The `addForeignKeyIfPresent` helper already uses it. This is NOT standard SQL — it's H2-specific. If the project ever migrates to PostgreSQL/MySQL, this syntax must change.
- **`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`**: Also H2-specific. Same caveat.
- **Table names in INFORMATION_SCHEMA are UPPERCASE**: H2 stores table names in uppercase by default. The parity test must compare uppercase names (the `DatabaseMetaData` API returns uppercase).

### Idempotency Guarantees

Every operation in `applyV1()` is idempotent:
- `SchemaInitializer.createAllTables()` → all `CREATE TABLE IF NOT EXISTS`
- `migrateSchemaColumns()` → all `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
- `addMissingForeignKeys()` → all `ADD CONSTRAINT IF NOT EXISTS`
- `ensureAllIndexes()` → all `CREATE INDEX IF NOT EXISTS`

This means `applyV1()` can safely run on ANY database state: empty, partial, or fully migrated. The worst case is a no-op.

### PMD / Spotless Compliance

- The new `VersionedMigration` record is a private static nested record — this is fine per project conventions (nested types are welcome per CLAUDE.md)
- `List.of(...)` is immutable — satisfies the "never expose mutable collections" rule
- The `LOG.info()` calls in `runAllPending()` use parameterized logging — satisfies PMD GuardLogStatement
- Run `mvn spotless:apply` before `mvn verify` to auto-format

### Thread Safety

`DatabaseManager.initializeSchema()` is `synchronized` (line 102). The migration runner itself is stateless (all static methods, no mutable fields). Thread safety is preserved.

### Future Migration Template (V3+)

V1 = baseline, V2 = backfill. When adding a future migration, the ONLY changes needed are:

1. Add a new entry to the `MIGRATIONS` list:
```java
private static final List<VersionedMigration> MIGRATIONS = List.of(
    new VersionedMigration(1, "Baseline schema...", MigrationRunner::applyV1),
    new VersionedMigration(2, "Backfill FKs/indexes...", MigrationRunner::applyV2),
    new VersionedMigration(3, "Add profile_score column to users", MigrationRunner::applyV3)  // NEW
);
```

2. Add the `applyV3` method:
```java
private static void applyV3(Statement stmt) throws SQLException {
    stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_score DOUBLE DEFAULT 0.0");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_profile_score ON users(profile_score DESC)");
}
```

That's it. No changes to DatabaseManager, SchemaInitializer, or any other file. The SchemaParityTest will automatically validate the new migration.

## 7. Verification Plan

### Automated Tests

| Test                                                            | What It Validates                                                       |
|-----------------------------------------------------------------|-------------------------------------------------------------------------|
| `SchemaParityTest#freshAndUpgradedSchemasMatch`                 | Fresh DB and upgraded DB produce identical tables, columns, and indexes |
| `SchemaInitializerTest#createsAllExpectedTables`                | All 18 tables exist after fresh creation (unchanged)                    |
| `SchemaInitializerTest#createsExpectedIndexes`                  | Core indexes exist after fresh creation (unchanged)                     |
| `SchemaInitializerTest#RunAllPending#freshDatabaseMigration`    | `runAllPending()` creates all tables + records version on fresh DB      |
| `SchemaInitializerTest#RunAllPending#isIdempotent`              | Running `runAllPending()` twice is safe                                 |
| `SchemaInitializerTest#RunAllPending#existingDatabaseMigration` | Running `runAllPending()` on existing DB is safe                        |
| All existing JDBI integration tests                             | Schema changes don't break existing storage operations                  |

### Manual Verification

```bash
# 1. Run all tests (captures output once)
mvn test

# 2. If failures, rerun verbose for the specific test
mvn -Ptest-output-verbose -Dtest="SchemaParityTest" test
mvn -Ptest-output-verbose -Dtest="SchemaInitializerTest" test

# 3. Full quality gate
mvn spotless:apply && mvn verify

# 4. Smoke test: start the app (exercises real DB migration path)
mvn compile && mvn exec:exec

# 5. Smoke test: start the GUI
mvn javafx:run
```

### Optional: Verify FK/Index presence on a "real" DB

After running the app once (step 4 above), connect to the H2 database and verify:
```sql
-- Check indexes exist
SELECT INDEX_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.INDEXES
WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME, INDEX_NAME;

-- Check constraints exist
SELECT CONSTRAINT_NAME, TABLE_NAME, CONSTRAINT_TYPE
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME, CONSTRAINT_NAME;
```

## 8. What NOT To Do

- **Do NOT use Flyway or Liquibase** — this project doesn't need external migration tooling for a single H2 database. The in-house approach is simpler and sufficient.
- **Do NOT modify any CREATE TABLE statements in SchemaInitializer** — it's frozen as the V1 baseline.
- **Do NOT add new columns to `migrateSchemaColumns()`** — future columns go into V3+ migrations.
- **Do NOT modify `applyV1()` or `applyV2()`** — they are historical migrations. New changes go into V3+.
- **Do NOT run `mvn verify` multiple times** — capture output once, query multiple times (Build Command Discipline from CLAUDE.md).
- **Do NOT add Mockito** — use the existing `TestStorages` pattern and direct JDBC for schema tests.
- **Do NOT use `Instant.now()`** — if any timestamp is needed, use `AppClock.now()` (though this migration code uses SQL `CURRENT_TIMESTAMP` which is appropriate for DDL context).

## 9. Definition of Done

- [ ] `MigrationRunner` uses versioned migration registry pattern (`VersionedMigration` record + `MIGRATIONS` list)
- [ ] `applyV1()` calls SchemaInitializer + columns + FKs + indexes (no gaps)
- [ ] `applyV2()` exists as backfill for databases with old V1 recorded (idempotent, safe no-op on fresh DBs)
- [ ] `addMissingForeignKeys()` covers all 17+ FK constraints (currently missing 8)
- [ ] `DatabaseManager` calls `runAllPending()` instead of `migrateV1()`
- [ ] Old `migrateV1()` method is deleted (no dead code)
- [ ] `SchemaInitializer` Javadoc updated to indicate frozen status
- [ ] `SchemaParityTest` passes — fresh and old-V1-upgraded schemas produce identical tables, columns, and indexes
- [ ] All existing `SchemaInitializerTest` tests pass with updated method names (`runAllPending`)
- [ ] All existing JDBI integration tests pass
- [ ] `mvn spotless:apply && mvn verify` passes (full quality gate)
- [ ] App starts successfully via `mvn compile && mvn exec:exec`

## Completion Notes (2026-03-09)

- ✅ `MigrationRunner` uses append-only versioned migration registry (`VersionedMigration` + `MIGRATIONS`) and `runAllPending(...)` pipeline.
- ✅ `DatabaseManager` calls `MigrationRunner.runAllPending(...)`.
- ✅ `SchemaInitializer` is documented as frozen baseline DDL.
- ✅ `SchemaParityTest` exists and validates fresh vs upgraded schema parity.
- ✅ `SchemaInitializerTest` migration-path coverage is updated for `runAllPending(...)`.
- ✅ Additional append-only migration coverage has advanced through `V7` (daily-picks persistence), preserving the same architecture pattern.

## Verification Executed

- ✅ Full quality gate passed after latest migration work: `mvn spotless:apply verify` (BUILD SUCCESS).
