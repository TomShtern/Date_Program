# PostgreSQL Runtime Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PostgreSQL runtime support to the existing JDBI/Hikari storage stack while preserving H2-backed tests and keeping the current service graph and business behavior intact.

**Architecture:** Generalize the runtime database bootstrap path instead of replacing the storage layer. Keep `StorageFactory.buildInMemory(...)` and the current H2-heavy test harness intact, but make `DatabaseManager`, `StorageFactory`, schema migration code, and H2-specific JDBI write SQL dialect-aware so production can run against PostgreSQL. Replace H2-only `MERGE ... KEY (...)`, `DATEDIFF(...)`, and `ADD CONSTRAINT IF NOT EXISTS` seams with explicit portable or dialect-specific implementations.

**Tech Stack:** Java 25, Maven, HikariCP, JDBI 3, H2 (tests), PostgreSQL JDBC, JUnit 5.

**Source of truth used for this plan:** `pom.xml`, `config/app-config.json`, `src/main/java/**`, and `src/test/java/**` only.

---

## Source-backed current state

- `pom.xml` includes `com.h2database:h2` and does **not** include a PostgreSQL JDBC dependency.
- `src/main/java/datingapp/storage/DatabaseManager.java` hard-codes H2 JDBC defaults (`jdbc:h2:./data/dating`, `...-dev`, `...-test`) and H2-specific URL/profile/password behavior.
- `src/main/java/datingapp/storage/StorageFactory.java` still exposes `buildH2(...)` and `buildInMemory(...)`, and `ApplicationStartup.initialize(...)` calls `StorageFactory.buildH2(...)` directly.
- `src/main/java/datingapp/core/AppConfig.java` exposes only `queryTimeoutSeconds` in `StorageConfig`; there is no runtime DB kind/URL/username surface yet.
- `config/app-config.json` has no database connection keys.
- `src/main/java/datingapp/storage/schema/MigrationRunner.java` uses H2-specific `MERGE INTO schema_version ... KEY (version)` and `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS ...`.
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`, `JdbiMetricsStorage.java`, and `JdbiMatchmakingStorage.java` contain multiple H2-specific `MERGE ... KEY (...)` upserts.
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java` uses `DATEDIFF('SECOND', ...)` in session aggregates.
- Key tests (`DatabaseManagerConfigurationTest`, `StorageFactoryInMemoryTest`, `SchemaInitializerTest`, `JdbiConnectionStorageAtomicityTest`, and many other storage/bootstrap tests) explicitly rely on H2 and should remain H2-backed.

---

## File map

### Create
- `src/main/java/datingapp/storage/DatabaseDialect.java` — runtime database kind enum plus helpers for dialect selection.
- `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java` — centralized dialect-specific SQL fragments/builders for upsert and date-diff operations.
- `src/test/java/datingapp/storage/DatabaseDialectTest.java` — pure unit coverage for dialect detection and SQL helper behavior.

### Modify
- `pom.xml` — add PostgreSQL JDBC dependency; keep H2 for tests and existing runtime/test plugins untouched.
- `src/main/java/datingapp/core/AppConfig.java` — expand `StorageConfig` and builder with runtime DB settings.
- `src/main/java/datingapp/core/AppConfigValidator.java` — validate new DB config fields.
- `config/app-config.json` — add runtime DB keys (non-secret defaults/placeholders only).
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` — load and environment-override the new DB config fields.
- `src/main/java/datingapp/storage/DatabaseManager.java` — generalize URL/profile/password logic and Hikari configuration beyond H2.
- `src/main/java/datingapp/storage/StorageFactory.java` — replace `buildH2(...)` as the runtime composition path with a generic SQL/JDBI runtime entrypoint while preserving `buildInMemory(...)` for H2 tests.
- `src/main/java/datingapp/storage/schema/MigrationRunner.java` — replace H2-only schema-version upsert and constraint-add syntax with dialect-aware behavior.
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` — replace `MERGE` upserts with dialect-aware implementations.
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java` — replace `MERGE` upserts and `DATEDIFF(...)` aggregate logic with dialect-aware SQL.
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` — replace `MERGE` upserts with dialect-aware implementations.
- `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java` — pin the new runtime DB resolution behavior while preserving H2 test/dev behavior.
- `src/test/java/datingapp/storage/StorageFactoryInMemoryTest.java` — ensure H2 in-memory isolation remains unchanged.
- `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java` — preserve H2 schema/migration idempotency expectations after the migration-runner changes.
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java` — keep normalized user-storage semantics green under the new dialect-aware SQL path.
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` — preserve normalized hydration and paging semantics.
- `src/test/java/datingapp/storage/jdbi/JdbiConnectionStorageAtomicityTest.java` — preserve H2 rollback semantics and the trigger-backed failure harness.
- `src/test/java/datingapp/core/ServiceRegistryTest.java` — keep service graph bootstrapping green with H2 tests after runtime factory renaming/generalization.
- `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java` — keep startup bootstrap green with test-profile H2 wiring.

---

## Task 1: Add runtime PostgreSQL config surfaces without breaking H2 tests

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `config/app-config.json`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Test: `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`
- Test: `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`

- [ ] **Step 1: Write the failing config/runtime tests**
  - Add a `DatabaseManagerConfigurationTest` case proving a PostgreSQL JDBC URL is preserved verbatim and not rewritten by H2 dev/test logic.
  - Add a startup/config test proving the new storage config fields are loaded from `config/app-config.json` / env override seams without disturbing existing `queryTimeoutSeconds` behavior.

- [ ] **Step 2: Run the focused config/bootstrap pack and confirm the red state**

Run:
`mvn -Dtest=DatabaseManagerConfigurationTest,ApplicationStartupBootstrapTest test`

Expected:
- FAIL because there is no PostgreSQL JDBC dependency and no runtime DB config surface in `AppConfig` / `ApplicationStartup`.

- [ ] **Step 3: Add the new runtime DB settings**
  - Add PostgreSQL JDBC dependency to `pom.xml` while keeping H2.
  - Expand `AppConfig.StorageConfig` beyond `queryTimeoutSeconds`, adding source-backed runtime fields such as database dialect, runtime JDBC URL, and runtime username.
  - Add matching validation in `AppConfigValidator`.
  - Add matching keys to `config/app-config.json` with non-secret defaults/placeholders only.
  - Update `ApplicationStartup.applyEnvironmentOverrides(...)` to support the new DB settings.

- [ ] **Step 4: Re-run the focused config/bootstrap pack until it passes**

Run:
`mvn -Dtest=DatabaseManagerConfigurationTest,ApplicationStartupBootstrapTest test`

Expected:
- PASS with PostgreSQL runtime config loading supported and H2 test profile behavior preserved.

---

## Task 2: Generalize runtime bootstrap from H2-only to generic SQL runtime

**Files:**
- Create: `src/main/java/datingapp/storage/DatabaseDialect.java`
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Test: `src/test/java/datingapp/storage/StorageFactoryInMemoryTest.java`
- Test: `src/test/java/datingapp/core/ServiceRegistryTest.java`

- [ ] **Step 1: Write the failing runtime bootstrap tests**
  - Add a `DatabaseDialectTest` for dialect detection from the configured runtime DB setting and/or JDBC URL.
  - Add a `ServiceRegistryTest`/`StorageFactoryInMemoryTest` assertion proving H2 in-memory registries still work after runtime factory generalization.

- [ ] **Step 2: Run the focused runtime/bootstrap pack and confirm the red state**

Run:
`mvn -Dtest=DatabaseDialectTest,StorageFactoryInMemoryTest,ServiceRegistryTest test`

Expected:
- FAIL because runtime bootstrap is still exposed only as `StorageFactory.buildH2(...)` and `DatabaseManager` still assumes H2 URL semantics.

- [ ] **Step 3: Implement generic runtime database bootstrap**
  - Introduce `DatabaseDialect` as the single place that names supported engines (`H2`, `POSTGRESQL`).
  - Generalize `DatabaseManager` so H2-specific URL rewrite/password rules apply only to H2, while PostgreSQL runtime URLs remain untouched.
  - Add a generic `StorageFactory` runtime entrypoint (for example, `buildSqlDatabase(...)` or equivalent) and switch `ApplicationStartup.initialize(...)` to use it.
  - Preserve `StorageFactory.buildInMemory(...)` as an explicitly H2-only test helper.

- [ ] **Step 4: Re-run the focused runtime/bootstrap pack until it passes**

Run:
`mvn -Dtest=DatabaseDialectTest,StorageFactoryInMemoryTest,ServiceRegistryTest test`

Expected:
- PASS with generic runtime bootstrap in place and H2 in-memory tests unchanged.

---

## Task 3: Make schema migration execution PostgreSQL-safe

**Files:**
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java` (only if a concrete incompatibility is confirmed during implementation)
- Test: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- Test: `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`

- [ ] **Step 1: Write the failing migration/dialect tests**
  - Add unit coverage (likely against `DatabaseDialect` / helper methods) for schema-version recording SQL and foreign-key DDL generation.
  - Keep `SchemaInitializerTest` focused on H2 behavior but add assertions that do not depend on H2-only `MERGE` or `ADD CONSTRAINT IF NOT EXISTS` implementation details.

- [ ] **Step 2: Run the focused schema pack and confirm the red state**

Run:
`mvn -Dtest=SchemaInitializerTest,DatabaseManagerConfigurationTest,DatabaseDialectTest test`

Expected:
- FAIL because `MigrationRunner.recordSchemaVersion(...)` still uses H2 `MERGE ... KEY (...)`, and `addForeignKeyIfMissing(...)` still uses `ADD CONSTRAINT IF NOT EXISTS`.

- [ ] **Step 3: Implement dialect-aware migration SQL**
  - Replace `recordSchemaVersion(...)` with a portable insert/update path or explicit H2/PostgreSQL dialect branching.
  - Replace `ADD CONSTRAINT IF NOT EXISTS` with metadata preflight + plain `ALTER TABLE ... ADD CONSTRAINT ...`.
  - Keep the partial-index fallback pattern already present in `MigrationRunner.createIndexWithFallback(...)`.
  - Touch `SchemaInitializer` only if a concrete PostgreSQL-incompatible DDL token is confirmed during implementation.

- [ ] **Step 4: Re-run the focused schema pack until it passes**

Run:
`mvn -Dtest=SchemaInitializerTest,DatabaseDialectTest test`

Expected:
- PASS with H2 migration tests preserved and PostgreSQL-safe migration SQL generation covered.

---

## Task 4: Replace H2-only JDBI write SQL with dialect-aware implementations

**Files:**
- Create: `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiConnectionStorageAtomicityTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/SqlRowReadersTest.java`

- [ ] **Step 1: Write the failing storage-dialect tests**
  - Add pure helper tests for SQL fragments emitted by `SqlDialectSupport` (upsert statements and the session-duration expression).
  - Keep the existing H2-backed storage tests as the semantic regression safety net.

- [ ] **Step 2: Run the focused storage pack and confirm the red state**

Run:
`mvn -Dtest=JdbiUserStorageMigrationTest,JdbiUserStorageNormalizationTest,JdbiConnectionStorageAtomicityTest,SqlRowReadersTest,DatabaseDialectTest test`

Expected:
- FAIL because `JdbiUserStorage`, `JdbiMetricsStorage`, and `JdbiMatchmakingStorage` still embed H2-specific `MERGE ... KEY (...)` and `DATEDIFF('SECOND', ...)` SQL.

- [ ] **Step 3: Implement dialect-aware write/query SQL**
  - Introduce `SqlDialectSupport` for the repeated H2/PostgreSQL SQL divergences.
  - Convert the H2-only `@SqlUpdate MERGE ... KEY (...)` paths into dialect-aware imperative `Handle.createUpdate(...)` or equivalent helper-backed execution.
  - Replace `DATEDIFF('SECOND', started_at, ended_at)` with a dialect-aware expression that preserves current semantics.
  - Keep the H2 trigger-backed rollback test (`JdbiConnectionStorageAtomicityTest`) intact.

- [ ] **Step 4: Re-run the focused storage pack until it passes**

Run:
`mvn -Dtest=JdbiUserStorageMigrationTest,JdbiUserStorageNormalizationTest,JdbiConnectionStorageAtomicityTest,SqlRowReadersTest test`

Expected:
- PASS with H2-backed storage semantics preserved under the new dialect-aware SQL path.

---

## Task 5: Final migration verification and handoff state

**Files:**
- Verify only: touched production/test files above

- [ ] **Step 1: Run the H2-preservation smoke packs**

Run:
`mvn -Dtest=DatabaseManagerConfigurationTest,StorageFactoryInMemoryTest,SchemaInitializerTest,JdbiUserStorageMigrationTest,JdbiUserStorageNormalizationTest,JdbiConnectionStorageAtomicityTest,ServiceRegistryTest,ApplicationStartupBootstrapTest test`

Expected:
- PASS, proving H2 tests remain intact after PostgreSQL runtime support work.

- [ ] **Step 2: Run the full quality gate**

Run:
`mvn spotless:apply verify`

Expected:
- PASS.

- [ ] **Step 3: Add a small runtime sanity note to the final handoff**
  - Call out whether PostgreSQL runtime support is configured but not yet integration-tested live, or whether a live local PostgreSQL smoke run was added during implementation.
  - Do **not** claim live PostgreSQL success unless it was actually executed in this workspace.

---

## Suggested implementation order rationale

1. **Config/dependency first** — the current source has no PostgreSQL driver and no runtime DB config surface.
2. **Runtime bootstrap second** — `ApplicationStartup` still hard-wires `StorageFactory.buildH2(...)`.
3. **Migration runner third** — schema versioning and FK DDL still contain H2-only constructs.
4. **JDBI SQL fourth** — the `MERGE` and `DATEDIFF` seams are numerous but mechanically isolated once dialect support exists.
5. **Keep H2 tests the whole time** — the current suite is deeply H2-backed and is the best existing regression net.

## Non-goals for this plan

- Do **not** migrate all tests to PostgreSQL.
- Do **not** replace JDBI or HikariCP.
- Do **not** convert the Java-embedded schema system into external SQL resources unless implementation discovers that the current Java-based approach becomes unworkable.
- Do **not** commit credentials or assume a live PostgreSQL server exists unless one is actually configured during implementation.

## Self-review

- Source coverage: every plan item maps back to current source seams in `pom.xml`, `DatabaseManager`, `StorageFactory`, `ApplicationStartup`, `AppConfig`, `MigrationRunner`, and the JDBI storages/tests.
- Placeholder scan: replaced vague “make it PostgreSQL compatible” language with concrete files and H2-specific constructs already present in source.
- Scope check: the plan is limited to runtime PostgreSQL support with H2 tests preserved; it does not silently expand into full cross-database test migration or cloud deployment work.
