# Implementation Plan: Storage Schema Drift & PII Risks

**Status:** ✅ **COMPLETED** (2026-03-09)

**Source Report:** `Generated_Report_Generated_By_GLM5_21.02.2026.md` (Findings F-014, F-018)

## 1. Goal Description
The `JdbiUserStorage` class utilizes a manually curated, 44-column SQL string (`ALL_COLUMNS`) for `SELECT` queries. This constant is highly prone to schema drift; if a developer adds a column to the database and binds it in the `Mapper`, but forgets to add it to this string, the application will fail silently or throw SQL exceptions at runtime.
Furthermore, `CandidateFinder` actively logs exact user coordinates (Lat/Lon) and names during its filtering process. This exposes sensitive Personally Identifiable Information (PII) to whoever has access to the standard application logs.

**Objective:**
Eliminate schema drift risk by replacing the hardcoded `ALL_COLUMNS` string with a dynamic JDBI approach (or simple `SELECT *` with strict mapping). Sanitize all `CandidateFinder` debug logs to mask PII.

## 2. Proposed Changes

### `datingapp.storage.jdbi`

#### [MODIFY] `JdbiUserStorage.java`
- Delete the `public static final String ALL_COLUMNS = "...";` block entirely.
- Update the `@SqlQuery` annotations in `Dao` to use `SELECT *`:
  ```java
  @SqlQuery("SELECT * FROM users WHERE id = :id AND deleted_at IS NULL")
  Optional<User> get(@Bind("id") UUID id);
  ```
  *(Note: JDBI's `RowMapper` maps strictly by column name via ResultSet. Using `SELECT *` in this bounded, single-table DAO ensures that newly added columns are instantly available to the `Mapper` without maintaining a parallel string list. If exact column selection is heavily preferred for performance, we will write a `static String generateColumns()` method using reflection on `UserSqlBindings` instead).*
- Update `findCandidates` multi-line string builder to also use `SELECT * FROM users`.
- Update `findByIds` dynamic query to use `SELECT * FROM users`.

### `datingapp.core.matching`

#### [MODIFY] `CandidateFinder.java`
- Review all `logDebug`, `logTrace`, and `logInfo` calls.
- **Remove Identifiers:** Stop logging `candidate.getName()`. Log `candidate.getId().toString().substring(0,8)` (short UUID) instead.
- **Anonymize Coordinates:** Inside `formatLatLon()`, format the coordinates to at most 1 decimal place (approx ~11km precision) or replace it completely with a boolean `hasLocation` flag if the exact coordinates aren't strictly necessary for debugging distance calculation.
  ```java
  private String formatLatLon(User user) {
      if (!hasLocation(user)) return "missing";
      // Masking to 1 decimal place prevents exact location pinpointing (PII safe)
      return String.format("%.1f, %.1f", user.getLat(), user.getLon());
  }
  ```

## 3. Verification Plan

### Automated Tests
1. **Database Schema Parity Check:** Run the integration tests (`mvn test -Dtest=*StorageTest`). By testing `JdbiUserStorageTest` with `SELECT *`, we guarantee that JDBI maps all configured columns successfully without crashing or throwing "Column not found" errors on hydration.
2. **PII Logging Verification:** Add a test to `CandidateFinderTest.java` that intercepts the logger (using an in-memory slf4j appender or simply visually verifying test output) to ensure that exact names (e.g., "Alice") and highly precise coordinates (e.g., "40.7128") do not appear anywhere in the string outputs when `findCandidates` is executed with TRACE logging enabled.

### Manual Verification
1. Run `mvn spotless:apply` and `mvn verify`.
2. Start the application. Generate some candidates in the CLI mode or UI.
3. Check `logs/app.log`. Verify that `CandidateFinder` traces now look like `Rejecting 3f8a91b2: TOO FAR...` instead of `Rejecting Johnny Depp: TOO FAR...`.

## Completion Notes (2026-03-09)

- ✅ Removed `JdbiUserStorage.ALL_COLUMNS` constant and migrated DAO/dynamic queries to `SELECT *` for `users` projection paths, eliminating manual projection drift risk.
- ✅ Updated migration regression test (`JdbiUserStorageMigrationTest`) to validate `SELECT *` path.
- ✅ Sanitized `CandidateFinder` logs to avoid PII leakage:
  - names removed from logs and replaced with short anonymized user reference (`user-<8-char-id>`)
  - exact coordinates masked from 4-decimal precision to 1-decimal precision.

## Verification Executed

- ✅ Focused tests passed: `CandidateFinderTest`, `JdbiUserStorageNormalizationTest`, `JdbiUserStorageMigrationTest`
- ✅ Full quality gate passed: `mvn spotless:apply verify` (BUILD SUCCESS; tests/checkstyle/PMD/JaCoCo all green)
