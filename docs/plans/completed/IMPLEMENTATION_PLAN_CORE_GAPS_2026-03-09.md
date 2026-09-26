# Agent Implementation Plan: Core Architecture & Security Gaps
**Date:** 2026-03-09
**Status:** VERIFIED AND IMPLEMENTED (2026-03-09)

## ✅ Execution Summary

- [x] **Phase 1 verified as stale in its original wording.** The repository already had an append-only versioned migration system via `MigrationRunner` + `SchemaInitializer`, so replacing it with Flyway would have been a risky architecture swap, not a missing-gap fix.
- [x] **Phase 2 implemented against the real migration architecture.** Added `MigrationRunner` V3 to drop legacy serialized `users` columns and refactored `JdbiUserStorage` to use normalized tables as the single source of truth.
- [x] **Phase 3 implemented.** `ConnectionService` now requires atomic relationship-transition support instead of using non-atomic compensating/fallback writes.
- [x] **Phase 4 implemented.** Added OWASP HTML sanitization and applied it at the profile, note, and messaging boundaries.
- [x] **Validation completed.** Focused regression suites for storage normalization, relationship transitions, messaging, and profile use-cases passed after the refactor.

This document is extremely optimized for AI code-completion agents (such as Antigravity or Claude). It contains exact file references, edge cases, and required outcomes to guarantee success when executing the four remaining core architectural gaps in this Java 25 / JDBI codebase.

---

## 🤖 Global Agent Rules for Execution
1. **Never break `mvn test`**: Execute `mvn test -DskipITs` after every sub-step. Do not proceed to the next phase if the build or test suite fails.
2. **Framework purity**: Do not import framework or database-specific libraries into `domain` or `core.model` classes under any circumstances.
3. **Java 25 constructs**: Prefer switch expressions, records, and pattern matching inside your refactors.

---

## Phase 1: Establish Schema Migration Tooling (Flyway/Liquibase)
**Context:** The app initializes via `SchemaInitializer.java`, preventing column modifications or drops. We must enforce a versioned migration strategy. We will choose Flyway for its simplicity and raw SQL approach.

### ✅ Verified Outcome
- [x] Verified that `DatabaseManager` already initializes schema through append-only `MigrationRunner.runAllPending(...)`.
- [x] Verified that `MigrationRunner` already tracks applied versions in `schema_version` and executes migrations sequentially.
- [x] Confirmed that deleting `SchemaInitializer.java` / `MigrationRunner.java` as originally proposed would have broken startup and tests.
- [x] Kept the current migration architecture and implemented Phase 2 within it, which is the correct codebase-aligned solution.
- [x] Explicitly **did not** introduce Flyway because that would have been a separate architectural migration, not required to close the verified core gaps.

### ⚙️ Step-by-Step Agent Execution:
1. **POM Update:**
   - Modify `c:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\pom.xml`.
   - Add `<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>10.10.0</version></dependency>`.
2. **Translate Baseline Schema:**
   - Read `src/main/java/datingapp/storage/schema/SchemaInitializer.java` thoroughly.
   - Extract **every single** `CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS` string fragment for all 26 tables.
   - Create `src/main/resources/db/migration/V1__Initial_Schema_Baseline.sql`.
   - Write the pure SQL DDL into this file. Ensure the SQL dialect is compliant with the backing database (e.g., SQLite or H2).
3. **Database Manager Hook-In:**
   - Modify `src/main/java/datingapp/storage/DatabaseManager.java`.
   - Find the initialization phase, currently calling `SchemaInitializer.createTables(...)`.
   - Replace it with Flyway initialization logic:
     ```java
     Flyway flyway = Flyway.configure().dataSource(jdbcUrl, user, pass).load();
     flyway.migrate();
     ```
4. **Cleanup & Verification:**
   - Delete the `SchemaInitializer.java` and `MigrationRunner.java` files.
   - Remove their respective usages from `DatabaseManager.java` and any startup scripts.
   - Run `mvn test` to ensure the in-memory/test DB spins up correctly using the new `V1` migration script instead of the old code-based schema.

---

## Phase 2: Finish Normalized Data Migration
**Context:** `JdbiUserStorage.java` performs a double-write. It writes to the new normalized tables (e.g., `user_photos`), but the primary `SELECT` statements and mapping paths still rely on legacy JSON columns like `photo_urls` and `interests` in the `users` table.

### ✅ Implemented Outcome
- [x] Added `MigrationRunner` **V3** (not `V2`, because `V2` already existed) to drop legacy serialized `users` columns: `photo_urls`, `interests`, `interested_in`, `db_smoking`, `db_drinking`, `db_wants_kids`, `db_looking_for`, and `db_education`.
- [x] Removed legacy-column bindings from `JdbiUserStorage` core `MERGE INTO users` upsert.
- [x] Kept normalized helper writes (`user_photos`, `user_interests`, `user_interested_in`, and normalized dealbreaker tables) as the only source of persisted multi-value profile data.
- [x] Removed legacy JSON / CSV mapper fallback logic from `JdbiUserStorage.Mapper`.
- [x] Updated normalization-compatibility tests to verify normalized persistence and the absence of legacy columns after migration.

### ⚙️ Step-by-Step Agent Execution:
1. **Write V2 Drop Script:**
   - Create `src/main/resources/db/migration/V2__Drop_Legacy_JSON_Columns.sql`.
   - Write the exact SQL commands:
     ```sql
     -- Remove legacy JSON arrays that have been normalized
     ALTER TABLE users DROP COLUMN IF EXISTS photo_urls;
     ALTER TABLE users DROP COLUMN IF EXISTS interests;
     ALTER TABLE users DROP COLUMN IF EXISTS interested_in;
     ALTER TABLE users DROP COLUMN IF EXISTS db_max_distance_km;
     ALTER TABLE users DROP COLUMN IF EXISTS db_age_range;
     ```
2. **Refactor JDBI Storage Write Paths:**
   - Open `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`.
   - In `saveUser(Handle handle, User user)` and `updateUser(...)`, absolutely remove the bindings mapping to `:photoUrls`, `:interests`, `:interestedIn`, etc.
   - Ensure the normalized helpers (`saveUserPhotos`, `saveUserInterests`, `saveDealbreakers`) are invoked immediately after the core `users` table insert/update.
3. **Refactor JDBI Storage Read Paths:**
   - Rewrite the core `RowMapper` attached to the `User` class (or the `fold` logic if utilizing `JoinRowMapper`). The application *must* execute a joined query or subsequent `SELECT`s to fetch the records from `user_photos` and `user_interests`, populating the `User` aggregate properties.
   - Remove any custom JSON parsing fallbacks from the mapper.
4. **Test Suite Verification:**
   - Seed data via `DevDataSeeder.java` might require minor updates if it was explicitly wiring JSON strings, but it likely uses the Domain object `User.setPhotoUrls(List.of(...))` which will trigger the new paths automatically.
   - Run `mvn test -Dtest=JdbiUserStorageTest` until green.

---

## Phase 3: Enforce Atomic Relationship Transitions
**Context:** `ConnectionService` handles changes like `acceptFriendZone()` via partial failure fallbacks (e.g., `match.revertToActive()`) because transactions aren't crossing the `CommunicationStorage` and `InteractionStorage` boundary smoothly.

### ✅ Implemented Outcome
- [x] Verified that `JdbiMatchmakingStorage` already provided atomic transition methods for friend-zone acceptance, graceful exit, and unmatch.
- [x] Removed the unsafe non-atomic fallback / compensating-write behavior from `ConnectionService`.
- [x] `ConnectionService` now returns a clear failure when atomic transition support is unavailable instead of attempting partial writes.
- [x] Updated in-memory test storage support so transition-oriented tests still model atomic behavior correctly.
- [x] Re-ran transition-focused tests covering `ConnectionService`, `MessagingService`, and JDBI atomic rollback behavior.

### ⚙️ Step-by-Step Agent Execution:
1. **Audit Storage Providers:**
   - Verify `JdbiMatchmakingStorage.java` and `JdbiConnectionStorage.java` (if applicable).
   - Look at `acceptFriendZoneTransition`, `gracefulExitTransition`, and `unmatchTransition`. These methods currently accept a `Handle` internally and use JDBI transactions, but they are localized to just the matchmaking domain.
2. **Refactor Service Layer Architecture:**
   - Open `src/main/java/datingapp/core/connection/ConnectionService.java`.
   - In `acceptFriendZone(UUID requestId, UUID responderId)`:
     - Remove the `try { ... } catch { match.revertToActive(); interactionStorage.update(match); }` block.
     - Call a single, unified method on `InteractionStorage` that wraps BOTH the `Match` update and the `FriendRequest` status update in one underlying JDBI transaction utilizing `handle.useTransaction(...)`.
     - *Hint:* You may need to inject a unified `DatabaseManager` into the service, OR pass the cross-domain write concern into a unified storage method mapping multiple DAOs.
3. **Implement Unified JDBI Transaction:**
   - If utilizing a JDBI `Handle`, create a cross-concern storage method (perhaps in `DatabaseManager` or a dedicated `TransactionManager`) that accepts lambdas:
     ```java
     jdbi.useTransaction(handle -> {
         handle.attach(MatchDao.class).updateMatchState(...);
         handle.attach(FriendRequestDao.class).updateStatus(...);
     });
     ```
4. **Run Assertions:**
   - Ensure you didn't break the CLI (`MatchesHandler`) or UI (`MatchingViewModel`) that call these transition methods. Run `mvn test` specifically targeting `ConnectionServiceTest` (if it exists) or create one specifically for atomicity verification.

---

## Phase 4: Implement XSS & Payload Sanitization
**Context:** Incoming strings (like user bios or chat messages) are saved verbatim to the database, risking XSS injection when rendered by web modules or HTML-enabled view layers.

### ✅ Implemented Outcome
- [x] Added `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1` to `pom.xml`.
- [x] Created `datingapp.core.profile.SanitizerUtils` with a strict strip-all-tags policy.
- [x] Sanitized `User.name` and `User.bio` at the `ProfileUseCases.saveProfile(...)` boundary.
- [x] Sanitized `ProfileNote` content in `ProfileUseCases.upsertProfileNote(...)`.
- [x] Sanitized message content in `ConnectionService.sendMessage(...)` before persistence.
- [x] Added focused sanitization regression tests for profile save, profile notes, and messaging.
- [x] Added a root `.env` placeholder for `DATING_APP_DB_PASSWORD` because the project requires it outside test / in-memory database modes.

### ⚙️ Step-by-Step Agent Execution:
1. **Add OWASP Dependency:**
   - Modify `pom.xml`.
   - Add `<dependency><groupId>com.googlecode.owasp-java-html-sanitizer</groupId><artifactId>owasp-java-html-sanitizer</artifactId><version>20240325.1</version></dependency>`.
2. **Create Sanitization Service:**
   - Create `src/main/java/datingapp/core/profile/SanitizerUtils.java`.
   - Configure a strict policy discarding all HTML tags to prevent XSS:
     ```java
     public static final PolicyFactory STRICT_TEXT = new HtmlPolicyBuilder().toFactory();
     public static String sanitize(String input) {
         return input == null ? null : STRICT_TEXT.sanitize(input);
     }
     ```
3. **Intercept Domain Setters / Updaters:**
   - Open the primary UI/CLI use-case boundaries. The best centralized spot is inside the setters of the models or inside `ValidationService.java` prior to storage.
   - In `ProfileUseCases` (or `ValidationService`):
     - Wrap updates to `User.name`, `User.bio`, and `ProfileNote.content`.
     - `String sanitizedBio = SanitizerUtils.sanitize(request.bio());`
   - In `MessagingUseCases` (or `ConnectionService.sendMessage`):
     - `String sanitizedContent = SanitizerUtils.sanitize(content);`
4. **Unit Tests Setup:**
   - Create `src/test/java/datingapp/core/profile/SanitizerUtilsTest.java`.
   - Test payloads like `"<script>alert('xss')</script>Hello"`. Assert it outputs `Hello` effectively stripping malicious vectors before touching the domain storage.
   - Run `mvn test`.
