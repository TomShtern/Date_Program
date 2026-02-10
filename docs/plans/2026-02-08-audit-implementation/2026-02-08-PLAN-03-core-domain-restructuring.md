# Plan 03: Core Domain Restructuring

**Date:** 2026-02-08
**Priority:** HIGH (Week 3–4)
**Estimated Effort:** 8–12 hours
**Risk Level:** HIGH (structural moves, mass import changes, file deletions)
**Parallelizable:** PARTIAL — Phases A–C run in parallel with P04–P08; Phase D requires P02 complete; Phase E requires ALL plans complete
**Status:** ✅ PHASES A–D COMPLETE (Tasks 1–11 done, 817 tests pass, mvn verify green) | Phase E deferred (requires ALL plans complete)
**Prerequisites:** P01 ✅ (DatabaseManager released), P02 ✅ required before Phase D

---

## Overview

This plan addresses all audit findings related to **core domain structure**: layer violations, dead interfaces, scattered enums, the bloated `DatabaseManager`, the redundant `ProfilePreviewService`, `SessionService` thread-safety, `AppConfig.defaults()` coupling, and the `StorageFactory` extraction. It also includes the deferred **sub-package reorganization** of `core/` (R-004) as a final gated phase.

### Audit Issues Addressed

| ID     | Severity   | Category      | Summary                                                                                        |
|--------|------------|---------------|------------------------------------------------------------------------------------------------|
| R-003  | **HIGH**   | Architecture  | `core/` layer violations: AppBootstrap + ServiceRegistry import from `storage/`                |
| R-004  | **HIGH**   | Architecture  | Flat `core/` package (42 files) needs domain sub-packages                                      |
| R-006  | **MEDIUM** | Architecture  | Extract `ServiceRegistry.Builder` → `StorageFactory`                                           |
| R-007  | **MEDIUM** | Architecture  | `AppConfig.defaults()` scattered across 8+ files (P03 handles User + ProfileCompletionService) |
| R-012  | **LOW**    | Dead Code     | Inline `SoftDeletable` interface (2 implementors, no polymorphic usage)                        |
| R-018  | **LOW**    | Architecture  | Move standalone enums (Gender, UserState, VerificationMethod) into `User.java`                 |
| TS-012 | **MEDIUM** | Thread Safety | `SessionService` lock-stripe initialization safety                                             |
| —      | **MEDIUM** | Duplication   | `ProfileCompletionService` + `ProfilePreviewService` overlap                                   |
| —      | **HIGH**   | Complexity    | `DatabaseManager` 784 LOC God class → split into 3 focused classes                             |

**NOT in scope** (owned by other plans):

| Item                                      | Reason                          | Assigned To       |
|-------------------------------------------|---------------------------------|-------------------|
| R-007 in ViewModels (Login/Profile/Prefs) | P05 owns ViewModel files        | P05               |
| R-007 in LoginController                  | P07 owns UI controller files    | P07               |
| R-007 in MessagingService                 | P02 owns for TS-005/006         | P02 → P03 Phase D |
| TS-003 AppBootstrap volatile fix          | P02 owns thread-safety changes  | P02               |
| EH-005/006/007 ConfigLoader logging       | P02 owns error-handling changes | P02               |
| Storage interface splits (IF-003/004)     | Touches consumers in ViewModels | P05               |
| Sub-package reorganization (R-004)        | Phase E — gated, runs LAST      | P03 Phase E       |

---

## Files Owned by This Plan

These files are **exclusively modified by this plan**. No other plan should touch them during P03 execution.

### Modified Files

| # | File                                                         | Changes                                                  |
|---|--------------------------------------------------------------|----------------------------------------------------------|
| 1 | `src/main/java/datingapp/core/User.java`                     | SoftDeletable inline, enum nesting, remove static CONFIG |
| 2 | `src/main/java/datingapp/core/Match.java`                    | SoftDeletable inline                                     |
| 3 | `src/main/java/datingapp/core/ServiceRegistry.java`          | Extract StorageFactory, remove storage imports           |
| 4 | `src/main/java/datingapp/core/SessionService.java`           | TS-012 lock-stripe init safety                           |
| 5 | `src/main/java/datingapp/core/ProfileCompletionService.java` | Merge with ProfilePreviewService, make instance-based    |

### Deleted Files

| #  | File                                                      | Reason                               |
|----|-----------------------------------------------------------|--------------------------------------|
| 6  | `src/main/java/datingapp/core/SoftDeletable.java`         | Inlined into User + Match (R-012)    |
| 7  | `src/main/java/datingapp/core/Gender.java`                | Nested into User (R-018)             |
| 8  | `src/main/java/datingapp/core/UserState.java`             | Nested into User (R-018)             |
| 9  | `src/main/java/datingapp/core/VerificationMethod.java`    | Nested into User (R-018)             |
| 10 | `src/main/java/datingapp/core/ProfilePreviewService.java` | Merged into ProfileCompletionService |

### Moved Files (Phase D — requires P02 complete)

| #  | From                                             | To                                              | Reason    |
|----|--------------------------------------------------|-------------------------------------------------|-----------|
| 11 | `src/main/java/datingapp/core/AppBootstrap.java` | `src/main/java/datingapp/app/AppBootstrap.java` | R-003 fix |
| 12 | `src/main/java/datingapp/core/ConfigLoader.java` | `src/main/java/datingapp/app/ConfigLoader.java` | R-003 fix |

### New Files

| #  | File                                                            | Purpose                                    |
|----|-----------------------------------------------------------------|--------------------------------------------|
| 13 | `src/main/java/datingapp/storage/StorageFactory.java`           | JDBI wiring extracted from ServiceRegistry |
| 14 | `src/main/java/datingapp/storage/schema/SchemaInitializer.java` | DDL table creation from DatabaseManager    |
| 15 | `src/main/java/datingapp/storage/schema/MigrationRunner.java`   | Schema migrations from DatabaseManager     |

### Test Files

| #  | File                                                                | Action                                                               |
|----|---------------------------------------------------------------------|----------------------------------------------------------------------|
| 16 | `src/test/java/datingapp/core/SoftDeletableTest.java`               | Update: remove interface default-method tests, keep User/Match tests |
| 17 | `src/test/java/datingapp/core/ProfilePreviewServiceTest.java`       | Delete or merge into ProfileCompletionServiceTest                    |
| 18 | `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java` | New: verify tables created                                           |
| 19 | `src/test/java/datingapp/storage/StorageFactoryTest.java`           | New: verify JDBI wiring                                              |

### Import-Only Updates (not owned, mechanical find-and-replace)

These files need **import statement changes only** — no logic changes:

| Change                                       | Affected Files (~count)              | Old Import                                 | New Import                                                    |
|----------------------------------------------|--------------------------------------|--------------------------------------------|---------------------------------------------------------------|
| Gender → User.Gender                         | ~11 production + ~4 test             | `import datingapp.core.Gender`             | `import datingapp.core.User.Gender` (or inline `User.Gender`) |
| UserState → User.UserState                   | ~8 production + ~6 test              | `import datingapp.core.UserState`          | `import datingapp.core.User.UserState`                        |
| VerificationMethod → User.VerificationMethod | ~2 production                        | `import datingapp.core.VerificationMethod` | `import datingapp.core.User.VerificationMethod`               |
| AppBootstrap package change                  | ~3 files (Main, DatingApp, tests)    | `import datingapp.core.AppBootstrap`       | `import datingapp.app.AppBootstrap`                           |
| ConfigLoader package change                  | ~2 files (AppBootstrap, tests)       | `import datingapp.core.ConfigLoader`       | `import datingapp.app.ConfigLoader`                           |
| ProfilePreviewService removed                | ~3 files (ServiceRegistry, handlers) | `import ...ProfilePreviewService`          | `import ...ProfileCompletionService`                          |

---

## Detailed Tasks

### Phase A: Dead Code Cleanup & Simplification (no P02 dependency) ✅ COMPLETE

#### Task 1: Inline SoftDeletable into User + Match (R-012) ✅ DONE

**Files:** `User.java`, `Match.java`, `SoftDeletable.java` (delete), `SoftDeletableTest.java`

**Current state:**
- `SoftDeletable` is a 28-line interface with 3 methods: `getDeletedAt()`, `markDeleted()`, `isDeleted()` (default)
- Only 2 implementors: `User` and `Match`
- No polymorphic usage anywhere (no code uses `SoftDeletable` as a type parameter or variable)
- Both classes already have `private Instant deletedAt` field and implement all methods directly

**Changes:**

1a. **User.java** — Remove `implements SoftDeletable` from class declaration. Add the `isDeleted()` method body (currently inherited as default):
```java
// Before:
public class User implements SoftDeletable {

// After:
public class User {
    // ... existing getDeletedAt() and markDeleted() stay unchanged ...

    /** Returns true if this user has been soft-deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
```

1b. **Match.java** — Same change: remove `implements SoftDeletable`, add `isDeleted()` method body.

1c. **SoftDeletable.java** — DELETE the file.

1d. **SoftDeletableTest.java** — Remove the `DefaultMethod` nested class (lines 139–179) that tests the interface contract directly using an anonymous implementation. Keep the `UserSoftDelete` and `MatchSoftDelete` nested classes unchanged. Rename the test class to `SoftDeleteTest` or distribute tests to `UserTest`/`MatchTest` if preferred.

**Verification:** `mvn test -pl . -Dtest=SoftDeletableTest` — all remaining tests pass.

---

#### Task 2: Nest Standalone Enums into User.java (R-018) ✅ DONE

**Files:** `Gender.java` (delete), `UserState.java` (delete), `VerificationMethod.java` (delete), `User.java`, ~25 import updates

**Current state:**
- `Gender` — 9 lines, 3 values (MALE, FEMALE, OTHER)
- `UserState` — 13 lines, 4 values (INCOMPLETE, ACTIVE, PAUSED, BANNED)
- `VerificationMethod` — 13 lines, 2 values (EMAIL, PHONE)
- All three are top-level files in `datingapp.core`
- User.java already references them as fields but has no nested enums for these

**Changes:**

2a. **User.java** — Add as `public static enum` nested types inside `User`:
```java
public class User {

    /** Gender options available for users. */
    public static enum Gender {
        MALE, FEMALE, OTHER
    }

    /**
     * Lifecycle state of a user account.
     * Valid transitions: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
     */
    public static enum UserState {
        INCOMPLETE, ACTIVE, PAUSED, BANNED
    }

    /**
     * Verification method used to verify a profile.
     * NOTE: Currently simulated - email/phone not sent externally.
     */
    public static enum VerificationMethod {
        EMAIL, PHONE
    }

    // ... rest of class ...
}
```

2b. **Delete** `Gender.java`, `UserState.java`, `VerificationMethod.java`

2c. **Import updates** — Use IDE-assisted or `sd`/`ast-grep` refactoring:

For files **inside** `datingapp.core` package: no import change needed (same-package access). However, code references change:
- `Gender.MALE` → `User.Gender.MALE` (or just `Gender.MALE` if `User.Gender` is statically imported or referenced via the nested type — since they're in the same package, `Gender` alone still works due to Java inner class rules... Actually NO — once `Gender` is nested inside `User`, plain `Gender` won't resolve from outside `User`. Files in the same package would need `User.Gender`.)

**Correction:** Once `Gender` becomes `User.Gender`, ALL files outside `User.java` must use:
- `User.Gender` (fully-qualified within package), OR
- `import datingapp.core.User.Gender;` (from outside `datingapp.core`)

The safest approach is a **two-step refactor**:
1. Add nested enums to User.java (keep old files temporarily)
2. Use `sd` or IDE to replace all `import datingapp.core.Gender;` → `import datingapp.core.User.Gender;` and similarly for UserState/VerificationMethod
3. Verify compilation (`mvn compile`)
4. Delete the old standalone enum files

**Import changes by file (production):**

| File                                     | Gender  | UserState | VerificationMethod |
|------------------------------------------|---------|-----------|--------------------|
| `storage/jdbi/JdbiUserStorage.java`      | ✓       | ✓         | ✓                  |
| `app/cli/ProfileHandler.java`            | ✓       | ✓         | —                  |
| `app/cli/MatchingHandler.java`           | —       | ✓         | —                  |
| `app/cli/SafetyHandler.java`             | —       | ✓         | ✓                  |
| `ui/viewmodel/ProfileViewModel.java`     | ✓       | —         | —                  |
| `ui/viewmodel/PreferencesViewModel.java` | ✓       | —         | —                  |
| `ui/viewmodel/LoginViewModel.java`       | ✓       | ✓         | —                  |
| `ui/viewmodel/MatchesViewModel.java`     | —       | ✓         | —                  |
| `ui/viewmodel/MatchingViewModel.java`    | —       | ✓         | —                  |
| `ui/controller/LoginController.java`     | ✓       | ✓         | —                  |
| `ui/controller/ProfileController.java`   | ✓       | —         | —                  |
| `core/testutil/TestUserFactory.java`     | ✓       | —         | —                  |
| `core/testutil/TestStorages.java`        | —       | ✓         | —                  |
| Test files (~7)                          | various | various   | —                  |

**Verification:** `mvn compile` — zero errors. Then `mvn test` — all 820+ tests pass.

---

#### Task 3: Fix SessionService Thread Safety (TS-012) ✅ DONE

**File:** `SessionService.java`

**Current state:**
- Uses lock-stripe pattern with 256 `Object[]` locks indexed by `userId.hashCode()`
- Lock stripes are initialized in the constructor: `lockStripes = new Object[LOCK_STRIPE_COUNT]`
- **Issue:** The `Object[]` array elements are initialized in a loop — if the constructor is called from multiple threads (unlikely but possible in tests), there's no visibility guarantee for partially constructed lock array

**Changes:**

3a. **Ensure lock-stripe array is fully initialized before use:**
```java
// Current:
this.lockStripes = new Object[LOCK_STRIPE_COUNT];
for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
    lockStripes[i] = new Object();
}

// Fix: Make lockStripes final (it already is based on the field declaration)
// Verify the field is declared as:
private final Object[] lockStripes;
// The final field semantics in Java guarantee that the fully-constructed array
// is visible to all threads after the constructor completes.
```

3b. **Verify no publication escape:** Ensure `this` is not leaked before the constructor completes (no `this` passed to external methods during construction). If the constructor registers `this` with any external observer, add a comment noting the thread-safety implication.

3c. **Add `@SuppressWarnings` comment** documenting the thread-safety design:
```java
// Thread-safety: lockStripes is a final field, ensuring safe publication
// per JLS §17.5. Each stripe is an independent monitor object, enabling
// concurrent swipe recording for different users.
```

**Verification:** Existing `SessionServiceTest` passes. No new test needed — the fix is a documentation + verification task.

---

### Phase B: Service Restructuring (no P02 dependency) ✅ COMPLETE

#### Task 4: Merge ProfileCompletionService + ProfilePreviewService ✅ DONE

**Files:** `ProfileCompletionService.java` (target), `ProfilePreviewService.java` (delete), tests

**Current state:**
- **ProfileCompletionService** (344 LOC): Static utility class. Weighted scoring with 4 categories (basic, interests, lifestyle, preferences), tier system ("Beginner" → "Complete"), `AppConfig.defaults()` dependency. Entry point: `static calculate(User)`.
- **ProfilePreviewService** (179 LOC): Instance-based class. Simple field counting for completeness percentage, tip generation, text progress bar rendering. Entry point: `generatePreview(User)`.
- **Overlap:** Both check the same fields (name, bio, birthDate, gender, interestedIn, location, photos, lifestyle) for completeness. Both generate "next steps" / "tips" with overlapping messages.

**Changes:**

4a. **Make ProfileCompletionService instance-based** (remove `private constructor`, make `static` methods instance methods):
```java
// Before:
public final class ProfileCompletionService {
    private static final AppConfig CONFIG = AppConfig.defaults();
    private ProfileCompletionService() {}
    public static CompletionResult calculate(User user) { ... }

// After:
public final class ProfileCompletionService {
    private final AppConfig config;
    public ProfileCompletionService() { this(AppConfig.defaults()); }
    public ProfileCompletionService(AppConfig config) {
        this.config = Objects.requireNonNull(config);
    }
    public CompletionResult calculate(User user) { ... }
```

4b. **Absorb ProfilePreviewService functionality:**
- Move `ProfilePreview` record into `ProfileCompletionService`
- Move `ProfileCompleteness` record (rename to avoid conflict with `CompletionResult` — keep both, they serve different purposes: `CompletionResult` is detailed/weighted, `ProfileCompleteness` is simple field list)
- Move `generatePreview(User)` method
- Move `generateTips(User)` method
- Move `renderProgressBar()` static utility
- Move `calculateCompleteness(User)` method (the simple one)

4c. **Update consumers:**
- `ServiceRegistry.java` — replace `ProfilePreviewService` field/getter with `ProfileCompletionService` (already instantiated)
- Any CLI handler or ViewModel referencing `ProfilePreviewService` → use `ProfileCompletionService`
- Update `ServiceRegistry.Builder` to create instance-based `ProfileCompletionService`

4d. **Delete** `ProfilePreviewService.java`

4e. **Update/merge tests:**
- Move `ProfilePreviewServiceTest` test cases into `ProfileCompletionServiceTest`
- Delete `ProfilePreviewServiceTest.java`

**Verification:** `mvn test` — all tests pass. No behavior change for consumers.

---

#### Task 5: Remove `AppConfig.defaults()` from User.java (R-007 partial) ✅ DONE

**File:** `User.java`

**Current state:**
```java
private static final AppConfig CONFIG = AppConfig.defaults();
// Used in: setMinAge(), setMaxAge(), setMinHeightCm(), etc. for validation
```

**Problem:** Domain entity should not self-validate against a static config. Validation belongs in `ValidationService`, which already exists and already takes `AppConfig` via constructor injection.

**Changes:**

5a. **Remove** `private static final AppConfig CONFIG = AppConfig.defaults();` from `User.java`

5b. **Remove validation logic from setters** that references `CONFIG`:
- Setters like `setMinAge(int)` currently validate against `CONFIG.minAge()` — remove these checks
- The validation is already duplicated in `ValidationService.validateAge()`, `validateHeight()`, etc.
- Keep basic null/empty checks in setters (e.g., `Objects.requireNonNull`) but remove threshold-based validation

5c. **Ensure all entry points validate via ValidationService** before calling User setters:
- CLI handlers → already use ValidationService
- ViewModels → already use ValidationService
- StorageBuilder → bypasses validation (correct for DB reconstitution)

**Verification:** `mvn test` — all existing tests pass. Tests that relied on User self-validation should be checked; if any break, move those assertions to ValidationServiceTest.

---

#### Task 6: Extract StorageFactory from ServiceRegistry (R-006) ✅ DONE

**Files:** `ServiceRegistry.java`, new `StorageFactory.java`

**Current state:**
- `ServiceRegistry.Builder.buildH2()` (in `ServiceRegistry.java`) contains ~120 LOC of JDBI setup:
  - Creates `Jdbi` instance
  - Installs `SqlObjectPlugin`
  - Registers custom type mappers
  - Creates all 10 storage interface implementations via `onDemand()`
  - Creates `TransactionTemplate`
- This violates the rule that `core/` should not import from `storage/`
- `ServiceRegistry` (the class itself) holds 27 final fields — it's a pure data holder, which is fine
- The `Builder` is the problem — it does infrastructure wiring in core/

**Changes:**

6a. **Create** `src/main/java/datingapp/storage/StorageFactory.java`:
```java
package datingapp.storage;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
// ... storage imports ...

/**
 * Creates storage implementations and wires them into a ServiceRegistry.
 * Extracted from ServiceRegistry.Builder to fix core/ → storage/ layer violation (R-003/R-006).
 */
public final class StorageFactory {

    private StorageFactory() {} // Utility class

    /** Build a ServiceRegistry backed by H2 via JDBI. */
    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        // ... moved from ServiceRegistry.Builder.buildH2() ...
    }

    /** Build a ServiceRegistry backed by H2 with default config. */
    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }

    /** Build an in-memory ServiceRegistry for testing. */
    public static ServiceRegistry buildInMemory(AppConfig config) {
        // ... moved from ServiceRegistry.Builder.buildInMemory() ...
    }
}
```

6b. **ServiceRegistry.java** — Remove the `Builder` nested class entirely. Make the constructor `public` (or package-private with a factory method). Remove the `@SuppressWarnings` on the now-simpler class. Remove `import datingapp.storage.*` statements.

6c. **Update consumers of `ServiceRegistry.Builder`:**
- `AppBootstrap.java` (line ~75): `ServiceRegistry.Builder.buildH2(dbManager, config)` → `StorageFactory.buildH2(dbManager, config)`
- Any test using `ServiceRegistry.Builder.buildInMemory()` → `StorageFactory.buildInMemory()`

6d. **Create** `StorageFactoryTest.java` — basic test that `buildInMemory()` returns a valid `ServiceRegistry` with all non-null fields.

**Verification:** `mvn compile` — ServiceRegistry has zero storage imports. `mvn test` — all tests pass.

---

#### Task 7: Split DatabaseManager (784 LOC → 3 classes) ✅ DONE

**File:** `DatabaseManager.java` (modify), new `SchemaInitializer.java`, new `MigrationRunner.java`

**Current state (784 LOC):**
- Connection pool management (HikariCP): `initializePool()`, `getConnection()`, `shutdown()` (~100 LOC)
- Schema DDL creation: `createUsersTable()`, `createLikesTable()`, etc. (~350 LOC, 18 `create*` methods)
- Schema migrations: `migrateSchemaColumns()`, `addMissingForeignKeys()` (~150 LOC)
- Schema versioning: `createSchemaVersionTable()`, `recordSchemaVersion()`, `isVersionApplied()` (~50 LOC)
- Password management: `getPassword()`, `getConfiguredPassword()`, `isTestUrl()`, `isLocalFileUrl()` (~60 LOC)
- Singleton + volatile fields + synchronized methods

**Changes:**

7a. **Create** `src/main/java/datingapp/storage/schema/SchemaInitializer.java`:
```java
package datingapp.storage.schema;

import java.sql.Statement;

/**
 * Creates all database tables. Extracted from DatabaseManager for SRP.
 * Called once during application startup.
 */
public final class SchemaInitializer {
    private SchemaInitializer() {}

    /** Create all application tables using the given JDBC statement. */
    public static void createAllTables(Statement stmt) { ... }

    // Move these methods here:
    // createUsersTable(), createLikesTable(), createMatchesTable(),
    // createSwipeSessionsTable(), createUserStatsTable(), createPlatformStatsTable(),
    // createDailyPickViewsTable(), createUserAchievementsTable(),
    // createCoreIndexes(), createStatsIndexes(), createAdditionalIndexes(),
    // createMessagingSchema(), createSocialSchema(), createModerationSchema(),
    // createProfileSchema(), createStandoutsSchema(), createUndoStateSchema()
}
```

7b. **Create** `src/main/java/datingapp/storage/schema/MigrationRunner.java`:
```java
package datingapp.storage.schema;

import java.sql.Statement;

/**
 * Runs schema migrations (column additions, FK additions, versioning).
 * Extracted from DatabaseManager for SRP.
 */
public final class MigrationRunner {
    private MigrationRunner() {}

    /** Run all pending migrations. */
    public static void runMigrations(Statement stmt) { ... }

    // Move these methods here:
    // createSchemaVersionTable(), recordSchemaVersion(), isVersionApplied(),
    // migrateSchemaColumns(), addMissingForeignKeys(), addForeignKeyIfPresent(),
    // requireIdentifier()
}
```

7c. **DatabaseManager.java** — Slim down to ~200 LOC:
- Keep: singleton pattern, `initializePool()`, `getConnection()`, `shutdown()`, password management
- Delegate: `initializeSchema()` calls `SchemaInitializer.createAllTables(stmt)` then `MigrationRunner.runMigrations(stmt)`
- Remove: all 18 `create*` methods, migration methods, versioning methods

7d. **Create** `SchemaInitializerTest.java` — verify that calling `createAllTables()` on a fresh in-memory H2 produces all expected tables.

**Verification:** `mvn test` — all integration tests pass. `DatabaseManager` is now ~200 LOC.

---

### Phase C: Verification & Stabilization ✅ COMPLETE

#### Task 8: Run Full Quality Pipeline ✅ DONE (817 tests pass, 0 failures, PMD/Checkstyle/Spotless/JaCoCo all green)

```bash
mvn spotless:apply && mvn verify
```

- All 820+ tests pass
- Spotless formatting clean
- PMD rules pass
- JaCoCo coverage ≥ 60%
- No compilation errors from import changes

**If any test breaks:** Fix in the same phase before proceeding to Phase D.

---

### Phase D: Layer Violation Fixes ✅ COMPLETE

#### Task 9: Move AppBootstrap from core/ to app/ (R-003) ✅ DONE

**File:** `core/AppBootstrap.java` → `app/AppBootstrap.java`

**Current state:**
```java
package datingapp.core;
import datingapp.storage.DatabaseManager;  // ← LAYER VIOLATION
```

**Changes:**

9a. **Move file:** `src/main/java/datingapp/core/AppBootstrap.java` → `src/main/java/datingapp/app/AppBootstrap.java`

9b. **Change package declaration:** `package datingapp.core;` → `package datingapp.app;`

9c. **Update imports in consumers:**
- `src/main/java/datingapp/Main.java` — `import datingapp.core.AppBootstrap` → `import datingapp.app.AppBootstrap`
- `src/main/java/datingapp/ui/DatingApp.java` — same
- `src/main/java/datingapp/app/api/RestApiServer.java` — same (if it uses AppBootstrap)
- Test files referencing AppBootstrap
- `StorageFactory.java` (the new file from Task 6) if it references AppBootstrap

9d. **Remove the `datingapp.storage.DatabaseManager` import** from AppBootstrap — it should now call `StorageFactory.buildH2()` instead of `ServiceRegistry.Builder.buildH2()` (already done in Task 6).

**Verification:** `mvn compile` — `datingapp.core` has zero imports from `datingapp.storage`.

---

#### Task 10: Move ConfigLoader from core/ to app/ (R-003) ✅ DONE

**File:** `core/ConfigLoader.java` → `app/ConfigLoader.java`

**Current state:**
```java
package datingapp.core;
// No storage imports, but uses Jackson (external lib) + file I/O — infrastructure concern, not domain logic
```

**Changes:**

10a. **Move file:** `src/main/java/datingapp/core/ConfigLoader.java` → `src/main/java/datingapp/app/ConfigLoader.java`

10b. **Change package declaration:** `package datingapp.core;` → `package datingapp.app;`

10c. **Update imports in consumers:**
- `AppBootstrap.java` (now in `datingapp.app`) — `import datingapp.core.ConfigLoader` → no import needed (same package!)
- `src/test/java/datingapp/core/ConfigLoaderTest.java` — move to `src/test/java/datingapp/app/ConfigLoaderTest.java` and update package

**Verification:** `mvn compile && mvn test`

---

#### Task 11: Run Full Quality Pipeline (Post-Phase D) ✅ DONE (817 tests, 0 failures, mvn verify green)

```bash
mvn spotless:apply && mvn verify
```

Verify:
- `datingapp.core` package has **ZERO** imports from `datingapp.storage`
- All tests pass
- No circular dependencies introduced

Optional: Add an ArchUnit test to prevent future `core/ → storage/` imports:
```java
@Test
void coreShouldNotDependOnStorage() {
    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("datingapp.core..")
        .should().dependOnClassesThat().resideInAPackage("datingapp.storage..")
        .check(importedClasses);
}
```

---

### Phase E: Sub-Package Reorganization (DEFERRED — requires ALL P01–P08 complete)

> **PREREQUISITE:** ALL plans (P01–P08) must be fully implemented, verified, and committed.
> This phase changes every file path in `core/`, which would break all other plans' file references.
> Execute this phase ONLY as the final cleanup step of the entire audit implementation.

#### Task 12: Reorganize core/ into Domain Sub-Packages (R-004)

**Current state:** 42 files in a flat `datingapp.core` package (after Phase A–D deletions/moves).

**Proposed sub-package layout:**

| Sub-Package       | Files (moved from core/)                                                                               | Count |
|-------------------|--------------------------------------------------------------------------------------------------------|-------|
| `core/matching/`  | CandidateFinder, MatchingService, MatchQualityService, Match, StandoutsService, Standout, Dealbreakers | 7     |
| `core/messaging/` | Messaging, MessagingService, PacePreferences                                                           | 3     |
| `core/profile/`   | User, ProfileCompletionService, Preferences, Social, ValidationService                                 | 5     |
| `core/safety/`    | TrustSafetyService, UserInteractions                                                                   | 2     |
| `core/stats/`     | Stats, StatsService, Achievement, AchievementService                                                   | 4     |
| `core/daily/`     | DailyPick, DailyService, SwipeSession, SessionService                                                  | 4     |
| `core/config/`    | AppConfig, AppClock, EnumSetUtil, PerformanceMonitor                                                   | 4     |
| `core/` (root)    | ServiceRegistry, UndoService, UndoState, CleanupService, RelationshipTransitionService                 | 5     |
| `core/storage/`   | (unchanged — 11 interfaces already here)                                                               | 11    |

**Total:** ~45 files across 8 sub-packages.

**Execution strategy:**
1. Create all sub-package directories
2. Move files one sub-package at a time
3. After each move: `mvn compile` to catch broken imports
4. After all moves: `mvn spotless:apply && mvn verify`
5. Update all import statements across the codebase (~200+ files)
6. Final verification: full test suite passes

**Risk mitigation:**
- Do this in a **single atomic commit** to enable clean revert
- Use IDE refactoring (IntelliJ "Move Class") for automatic import updates
- Alternatively, use `sd` for batch import replacement:
  ```bash
  sd 'import datingapp\.core\.CandidateFinder' 'import datingapp.core.matching.CandidateFinder' -r src/
  ```

**This phase is OPTIONAL** — the codebase works correctly without sub-packages. The reorganization is purely organizational. If time is limited, this can be deferred indefinitely.

---

## Execution Order

```
Phase A (no dependencies — can start immediately):
  Task 1: Inline SoftDeletable          (~30 min)
  Task 2: Nest enums into User          (~45 min)
  Task 3: SessionService TS-012 fix     (~15 min)

Phase B (no dependencies — can run after Phase A):
  Task 4: Merge ProfileCompletion/Preview (~60 min)
  Task 5: Remove AppConfig from User     (~30 min)
  Task 6: Extract StorageFactory         (~60 min)
  Task 7: Split DatabaseManager          (~90 min)

Phase C (checkpoint):
  Task 8: Full quality pipeline          (~15 min)

Phase D (BLOCKED on P02 completion):
  Task 9:  Move AppBootstrap             (~20 min)
  Task 10: Move ConfigLoader             (~20 min)
  Task 11: Full quality pipeline         (~15 min)

Phase E (BLOCKED on ALL plans complete):
  Task 12: Sub-package reorganization    (~120 min)
```

**Critical path:** Tasks 1–8 can run immediately. Phase D is blocked on P02. Phase E is blocked on everything.

---

## Verification Checklist

After all phases complete:

- [x] `SoftDeletable.java` deleted, `isDeleted()` method exists in both User + Match
- [x] `Gender.java`, `UserState.java`, `VerificationMethod.java` deleted; nested in `User`
- [x] All import changes compile cleanly
- [x] `SessionService` lock-stripe field is `final` with documented thread-safety
- [x] `ProfilePreviewService.java` deleted; functionality merged into `ProfileCompletionService`
- [x] `ProfileCompletionService` is instance-based with `AppConfig` injection
- [x] `User.java` has no `AppConfig.defaults()` reference
- [x] `StorageFactory.java` exists in `datingapp.storage`
- [x] `ServiceRegistry.java` has zero imports from `datingapp.storage`
- [x] `ServiceRegistry.Builder` class is removed
- [x] `DatabaseManager.java` ≤ 250 LOC
- [x] `SchemaInitializer.java` and `MigrationRunner.java` exist in `datingapp.storage.schema`
- [x] `AppBootstrap.java` and `ConfigLoader.java` in `datingapp.app` package
- [x] `datingapp.core` has zero imports from `datingapp.storage`
- [x] `mvn spotless:apply && mvn verify` passes — 817 tests, 0 failures
- [x] JaCoCo coverage ≥ 60%

---

## Rollback Strategy

| Phase                       | Risk                                      | Rollback                                           |
|-----------------------------|-------------------------------------------|----------------------------------------------------|
| A (SoftDeletable + enums)   | LOW — mechanical, no logic change         | `git revert` single commit                         |
| B (merge + extract + split) | MEDIUM — new files + restructuring        | Revert commit(s), restore originals                |
| D (package moves)           | MEDIUM — import changes across many files | Revert commit, move files back                     |
| E (sub-packages)            | HIGH — 200+ import changes                | **Single atomic commit** required for clean revert |

**Recommended commit strategy:** One commit per task (Tasks 1–11), one atomic commit for Phase E.
