# Straightforward Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the remaining verified bugs, validation gaps, data-integrity inconsistencies, and code quality issues that are isolated, low-blast-radius, and have clear solutions — without touching security, performance, or large architectural refactors (those are covered by the existing 2026-03-20 plans or the separate complex-changes plan).

**Architecture:** Each fix is self-contained: touch the fewest files possible, add a regression test, commit. Phases are ordered by impact (critical/high first) and can be executed independently. No phase depends on another.

**Tech Stack:** Java 25, JavaFX 25, Maven, JUnit 5, JDBI/H2, `ValidationService`, `AppConfigValidator`, `ProfileUseCases`, `SchemaInitializer`.

---

## Agent Implementation Context

**READ FIRST:** This section contains everything an AI coding agent needs to implement this plan without reading other documentation.

### Build & Test Commands

```bash
# Compile only
mvn compile

# Run all tests
mvn test

# Run specific test class
mvn test -pl . -Dtest="UserTest" -Dsurefire.failIfNoSpecifiedTests=false

# Run with verbose test output (on failure)
mvn -Ptest-output-verbose test

# Format code (MUST run before verify — Spotless will fail otherwise)
mvn spotless:apply

# Full quality gate (compile → test → jacoco → spotless → pmd → checkstyle)
mvn spotless:apply verify

# Run CLI
mvn compile && mvn exec:exec

# Run JavaFX GUI
mvn javafx:run
```

### Critical Gotchas (from CLAUDE.md — will cause build failures if ignored)

| Rule | Wrong | Correct |
|------|-------|---------|
| User enum imports | `import datingapp.core.model.Gender` | `import datingapp.core.model.User.Gender` (nested type) |
| Match enum imports | `import datingapp.core.model.MatchState` | `import datingapp.core.model.Match.MatchState` (nested type) |
| ProfileNote import | `import datingapp.core.model.User.ProfileNote` | `import datingapp.core.model.ProfileNote` (standalone) |
| Domain timestamps | `Instant.now()` | `AppClock.now()` (always, for testability) |
| JDBI record binding | `@BindBean RecordType r` | `@BindMethods RecordType r` (records lack getX() introspection) |
| Date formatting | `DateTimeFormatter.ofPattern("dd MMM")` | `DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)` |
| Use-case construction | `new MatchingUseCases(...)` | `services.getMatchingUseCases()` from `ServiceRegistry` |
| Config access | `AppConfig.defaults()` in runtime code | Injected `AppConfig` via `ServiceRegistry` |
| PMD empty catch | Empty catch block `{}` | Use `assert true;` as no-op body |
| PMD suppression | `@SuppressWarnings` | `// NOPMD RuleName` inline comment |

### Code Style

- **Formatter:** Palantir Java Format via Spotless. Run `mvn spotless:apply` before every commit.
- **Java version:** 25 with preview features enabled.
- **No `Instant.now()`** — always use `AppClock.now()`.
- **Test naming:** `@DisplayName("descriptive sentence")` on every test method.
- **Logging:** Use `LoggingSupport` interface (default methods satisfying PMD GuardLogStatement).

### Package Structure (relevant to this plan)

```
src/main/java/datingapp/
  core/
    model/User.java, Match.java, ProfileNote.java, LocationModels.java
    profile/ValidationService.java, LocationService.java
    storage/UserStorage.java, CommunicationStorage.java, InteractionStorage.java
    AppConfig.java, AppConfigValidator.java, AppClock.java
  app/
    cli/MatchingHandler.java, ProfileHandler.java, SafetyHandler.java
    usecase/matching/MatchingUseCases.java
    usecase/messaging/MessagingUseCases.java
    usecase/profile/ProfileUseCases.java
  storage/
    DatabaseManager.java
    jdbi/JdbiConnectionStorage.java, JdbiUserStorage.java
    schema/SchemaInitializer.java, MigrationRunner.java
  ui/
    screen/StandoutsController.java
```

### Test Infrastructure

- Tests live in `src/test/java/datingapp/` mirroring main structure.
- Most domain tests use a `TestClock` to control `AppClock.now()`.
- JavaFX controller tests need the FX toolkit initialized — follow existing test patterns.
- The known flaky test `ChatControllerTest#selectionTogglesChatStateAndNoteButtonsRemainWired` fails intermittently in full suite — pre-existing, ignore it.

---

## Triage summary

This plan covers **only issues that are**:
1. Open (no existing implementation)
2. Not covered by the 2026-03-20 quick-wins or harder-longer-work plans
3. Not security-focused (V002, V014, V020, V025, V033, V034 excluded)
4. Verified as real issues against current code (false positives removed)

### False positives eliminated during code verification

These claims were reported as issues but are **not real problems** in the current codebase:

| Claim | Why it's a false positive |
|-------|--------------------------|
| V007 (FK constraints) | All 22+ foreign keys properly defined with `ON DELETE CASCADE` in `SchemaInitializer` |
| V012 (AppConfig.Builder incomplete) | Builder inner class exists and is fully functional |
| V030 (stale archive columns) | Columns exist correctly in schema; mapper is properly wired in `JdbiConnectionStorage` |
| V053 (UndoService robustness) | Proper error handling with try-catch; time-window validation correctly removes expired state |
| V055 (achievement thresholds duplicated) | All thresholds centralized in `AppConfig` via `config.safety().achievementMatchTier1()` through `Tier5()` |
| V058 (listener lifecycle) | `ChatController.cleanup()` properly removes listener and nulls reference |
| V060 (CleanupScheduler race) | Uses `AtomicBoolean` + `synchronized` methods — properly thread-safe |
| V070 (magic numbers) | All 15+ thresholds are named constants in `ProfileCompletionSupport` (lines 12-42) |
| V076 (soft-delete cascade) | DB-level `ON DELETE CASCADE` handles cleanup — application-level cascade not needed |
| V086 (BaseViewModel nullability) | `AsyncErrorRouter` gracefully handles null sink with fallback to logger |
| V093 (standouts navigation target) | Correctly navigates to `PROFILE_VIEW` (read-only profile), which is appropriate |

### Excluded — already covered by existing plans

- **2026-03-20 Quick Wins Plan:** V001✅, V003✅, V019✅, V028✅, V029✅, V032✅, V042, V044✅, V057✅, V066✅, V068✅, V069✅, V074✅, V089✅
- **2026-03-20 Harder/Longer Work Plan:** V009🟠, V010🟠, V011🟠, V024🟠, V026🟠, V064🟠, V065🟠, V067🟠, V071🟠, V073🟠, V075🟠, V078🟠, V079🟠, V083🟠

---

## File map

| File | Role | Why it changes |
|------|------|----------------|
| `src/main/java/datingapp/core/model/User.java` | domain model | Add NaN/Infinity guard in `setLocation` (line 568) |
| `src/test/java/datingapp/core/UserTest.java` | test | Regression tests for coordinate validation |
| `src/main/java/datingapp/core/AppConfigValidator.java` | config validation | Add monotonic threshold ordering checks |
| `src/test/java/datingapp/core/AppConfigValidatorTest.java` | test | Threshold ordering tests |
| `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` | storage | Convert conversation delete from hard to soft |
| `src/main/java/datingapp/storage/schema/SchemaInitializer.java` | schema | Add `deleted_at` column to conversations if missing |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java` | migration | Migration to add soft-delete column |
| `src/main/java/datingapp/core/storage/CommunicationStorage.java` | storage interface | Update delete contract |
| `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` | storage | Convert profile note delete from hard to soft |
| `src/main/java/datingapp/storage/DatabaseManager.java` | infra | Add pool validation query |
| `src/main/java/datingapp/app/cli/MatchingHandler.java` | CLI | Fix case normalization inconsistency (line 657) |
| `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` | use-case | Reduce constructor overloads |
| `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` | use-case | Reduce constructor overloads |
| `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java` | use-case | Reduce constructor overloads |
| `src/main/java/datingapp/ui/screen/StandoutsController.java` | UI | Add confirmation before navigation |
| `src/test/java/datingapp/core/matching/RecommendationServiceTest.java` | test (new) | Test coverage for untested service |

---

## Phase 1: Critical — Model Validation Guard

### Task 1.1: User.setLocation NaN/Infinity/bounds guard (V004)

**Context:** `User.setLocation(double, double)` at line 568 of `User.java` accepts any double value including `NaN`, `Infinity`, and out-of-range coordinates. While `ProfileUseCases` validates via `ValidationService.validateLocation()` before calling `setLocation`, any caller that bypasses the use-case layer (CLI direct calls, tests, DevDataSeeder) can inject garbage. The model should defend itself.

**Files:**
- Modify: `src/main/java/datingapp/core/model/User.java:568-573`
- Test: `src/test/java/datingapp/core/UserTest.java`

- [ ] **Step 1: Write failing tests for invalid coordinates**

```java
@Nested
@DisplayName("setLocation validation")
class SetLocationValidation {

    @Test
    @DisplayName("Rejects NaN latitude")
    void rejectsNanLatitude() {
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(Double.NaN, 34.78));
    }

    @Test
    @DisplayName("Rejects NaN longitude")
    void rejectsNanLongitude() {
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(32.08, Double.NaN));
    }

    @Test
    @DisplayName("Rejects positive infinity")
    void rejectsPositiveInfinity() {
        assertThrows(IllegalArgumentException.class,
                () -> user.setLocation(Double.POSITIVE_INFINITY, 34.78));
    }

    @Test
    @DisplayName("Rejects negative infinity")
    void rejectsNegativeInfinity() {
        assertThrows(IllegalArgumentException.class,
                () -> user.setLocation(32.08, Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("Rejects latitude out of range")
    void rejectsLatitudeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(91.0, 34.78));
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(-91.0, 34.78));
    }

    @Test
    @DisplayName("Rejects longitude out of range")
    void rejectsLongitudeOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(32.08, 181.0));
        assertThrows(IllegalArgumentException.class, () -> user.setLocation(32.08, -181.0));
    }

    @Test
    @DisplayName("Accepts valid coordinates at boundaries")
    void acceptsBoundaryCoordinates() {
        assertDoesNotThrow(() -> user.setLocation(90.0, 180.0));
        assertDoesNotThrow(() -> user.setLocation(-90.0, -180.0));
        assertDoesNotThrow(() -> user.setLocation(0.0, 0.0));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest="UserTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `setLocation` currently accepts all values without checking.

- [ ] **Step 3: Implement the guard in User.setLocation**

Replace the current `setLocation` method (lines 568-573) with:

```java
public void setLocation(double lat, double lon) {
    if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
        throw new IllegalArgumentException("Coordinates must be finite numbers");
    }
    if (lat < -90 || lat > 90) {
        throw new IllegalArgumentException("Latitude must be between -90 and 90");
    }
    if (lon < -180 || lon > 180) {
        throw new IllegalArgumentException("Longitude must be between -180 and 180");
    }
    this.lat = lat;
    this.lon = lon;
    this.hasLocationSet = true;
    touch();
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest="UserTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: ALL PASS

- [ ] **Step 5: Run full test suite to check for regressions**

Run: `mvn test`
Expected: If any test was passing invalid coords to `setLocation`, it will now fail and needs updating (check `DevDataSeeder` calls, `LoginViewModelTest`).

- [ ] **Step 6: Fix any regression tests that relied on invalid coordinates**

Update test fixtures that pass out-of-range or NaN coordinates. Use valid Tel Aviv coordinates (32.0853, 34.7818) as default.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/datingapp/core/model/User.java src/test/java/datingapp/core/UserTest.java
git commit -m "fix(V004): add coordinate validation guard to User.setLocation

Reject NaN, Infinity, and out-of-range lat/lon at the model layer.
Previously only validated at the use-case layer via ValidationService."
```

---

## Phase 2: Config Validation Completeness

### Task 2.1: Add monotonic threshold ordering to AppConfigValidator (V045)

**Context:** `AppConfigValidator` (lines 88-121) validates that distance values like `nearbyDistanceKm` and `closeDistanceKm` are non-negative, but never checks that `nearbyDistanceKm <= closeDistanceKm`. If someone configures `nearby=100, close=50`, the system silently accepts nonsensical thresholds.

**Files:**
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Test: `src/test/java/datingapp/core/AppConfigValidatorTest.java`

- [ ] **Step 1: Write failing test for threshold ordering**

```java
@Test
@DisplayName("Rejects nearby distance greater than close distance")
void rejectsNearbyGreaterThanClose() {
    // Build config where nearbyDistanceKm > closeDistanceKm
    AppConfig config = validConfigBuilder()
            .nearbyDistanceKm(100)
            .closeDistanceKm(50)
            .build();
    var result = AppConfigValidator.validate(config);
    assertFalse(result.valid());
    assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("nearbyDistanceKm") && e.contains("closeDistanceKm")));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest="AppConfigValidatorTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — no ordering check exists.

- [ ] **Step 3: Add monotonic ordering validation**

Add to the matching-config validation section of `AppConfigValidator`:

```java
if (matching.nearbyDistanceKm() > matching.closeDistanceKm()) {
    errors.add("nearbyDistanceKm (" + matching.nearbyDistanceKm()
            + ") must not exceed closeDistanceKm (" + matching.closeDistanceKm() + ")");
}
```

- [ ] **Step 4: Run tests — verify pass**

Run: `mvn test -pl . -Dtest="AppConfigValidatorTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/datingapp/core/AppConfigValidator.java src/test/java/datingapp/core/AppConfigValidatorTest.java
git commit -m "fix(V045): enforce monotonic ordering for distance thresholds in AppConfigValidator"
```

---

## Phase 3: Soft-Delete Consistency

### Task 3.1: Conversation deletion — hard delete → soft delete (V015)

**Context:** `JdbiConnectionStorage.deleteConversation()` uses `DELETE FROM conversations` (hard delete). This is inconsistent with the soft-delete pattern used elsewhere (User has `markDeleted` with `deleted_at` timestamp). Hard-deleting conversations destroys audit trails and makes recovery impossible.

**Files:**
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java` (add `deleted_at` column to conversations if not present)
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java` (migration for existing DBs)
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` (change DELETE to UPDATE)
- Modify: `src/main/java/datingapp/core/storage/CommunicationStorage.java` (update interface contract)
- Test: existing connection storage tests

- [ ] **Step 1: Write failing test for soft-delete behavior**

```java
@Test
@DisplayName("deleteConversation sets deleted_at instead of removing row")
void softDeleteConversation() {
    // Setup: create a conversation
    String convId = createTestConversation(userA.getId(), userB.getId());

    // Act: delete it
    storage.deleteConversation(convId);

    // Assert: row still exists but has deleted_at set
    // Direct SQL query to verify row presence
    var row = handle.createQuery("SELECT deleted_at FROM conversations WHERE id = :id")
            .bind("id", convId)
            .mapToMap()
            .findOne();
    assertTrue(row.isPresent(), "Row should still exist after soft delete");
    assertNotNull(row.get().get("deleted_at"), "deleted_at should be set");
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — row is physically deleted.

- [ ] **Step 3: Add `deleted_at` column to schema**

In `SchemaInitializer.java`, add `deleted_at TIMESTAMP` to the conversations table DDL.

In `MigrationRunner.java`, add migration:
```sql
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
```

- [ ] **Step 4: Change hard delete to soft delete in JdbiConnectionStorage**

Replace the `DELETE FROM conversations` SQL with:
```sql
UPDATE conversations SET deleted_at = :now WHERE id = :conversationId AND deleted_at IS NULL
```

Pass `AppClock.now()` as the `:now` parameter.

- [ ] **Step 5: Update all conversation queries to exclude soft-deleted rows**

Add `WHERE deleted_at IS NULL` (or `AND deleted_at IS NULL`) to every `SELECT` on the conversations table in `JdbiConnectionStorage`.

- [ ] **Step 6: Run tests — verify pass**

Run: `mvn test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/datingapp/storage/schema/SchemaInitializer.java \
       src/main/java/datingapp/storage/schema/MigrationRunner.java \
       src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java \
       src/main/java/datingapp/core/storage/CommunicationStorage.java
git commit -m "fix(V015): convert conversation deletion from hard delete to soft delete

Add deleted_at column to conversations table. DELETE becomes UPDATE.
All queries now exclude soft-deleted rows."
```

### Task 3.2: Profile note deletion — hard delete → soft delete (V037)

**Context:** `JdbiUserStorage.deleteProfileNoteInternal()` uses `DELETE FROM profile_notes` (hard delete, line 515). Same problem as conversations: no audit trail, no recovery.

**Current code** (`JdbiUserStorage.java` lines 515-522):
```java
@SqlUpdate("DELETE FROM profile_notes WHERE author_id = :authorId AND subject_id = :subjectId")
int deleteProfileNoteInternal(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);
```

**Files:**
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java` — find `profile_notes` CREATE TABLE, add `deleted_at TIMESTAMP` column
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java` — add migration: `ALTER TABLE profile_notes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:515-522` — change SQL
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` or similar

- [ ] **Step 1: Write failing test for profile note soft delete**

```java
@Test
@DisplayName("deleteProfileNote sets deleted_at instead of removing row")
void softDeleteProfileNote() {
    UUID authorId = testUser.getId();
    UUID subjectId = otherUser.getId();
    // Setup: create a profile note (use existing test helper or direct SQL insert)
    storage.saveProfileNote(new ProfileNote(authorId, subjectId, "test note", AppClock.now()));

    // Act: delete it
    storage.deleteProfileNote(authorId, subjectId);

    // Assert: row still exists with deleted_at set
    var row = handle.createQuery(
            "SELECT deleted_at FROM profile_notes WHERE author_id = :a AND subject_id = :s")
            .bind("a", authorId)
            .bind("s", subjectId)
            .mapToMap()
            .findOne();
    assertTrue(row.isPresent(), "Row should still exist after soft delete");
    assertNotNull(row.get().get("deleted_at"), "deleted_at should be set");
}
```

**GOTCHA:** `ProfileNote` is a standalone record in `core.model.ProfileNote` — NOT nested under `User`. Import as `datingapp.core.model.ProfileNote`. Use `@BindMethods` (not `@BindBean`) for JDBI binding since it's a record.

- [ ] **Step 2: Add `deleted_at` column to profile_notes DDL and migration**

In `SchemaInitializer.java`, find the `CREATE TABLE profile_notes` block and add `deleted_at TIMESTAMP` column.

In `MigrationRunner.java`, add:
```sql
ALTER TABLE profile_notes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
```

- [ ] **Step 3: Change DELETE to UPDATE**

Replace the SQL in `deleteProfileNoteInternal`:
```java
@SqlUpdate("UPDATE profile_notes SET deleted_at = :now WHERE author_id = :authorId AND subject_id = :subjectId AND deleted_at IS NULL")
int deleteProfileNoteInternal(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId, @Bind("now") Instant now);
```

Update the caller to pass `AppClock.now()` as the `now` parameter.

- [ ] **Step 4: Update all profile note SELECT queries to exclude soft-deleted rows**

Search `JdbiUserStorage.java` for all SQL touching `profile_notes` — add `AND deleted_at IS NULL` to WHERE clauses.

- [ ] **Step 5: Run tests — verify pass**

Run: `mvn test`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/datingapp/storage/schema/SchemaInitializer.java \
       src/main/java/datingapp/storage/schema/MigrationRunner.java \
       src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java
git commit -m "fix(V037): convert profile note deletion from hard delete to soft delete"
```

---

## Phase 4: Infrastructure Quick Wins

### Task 4.1: Add connection pool validation query (V047)

**Context:** `DatabaseManager` (lines 51-62) configures HikariCP without a connection validation query. This means stale/broken connections from the pool can be handed to callers, causing runtime failures.

**Files:**
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java:51-62`

- [ ] **Step 1: Add validation timeout to HikariConfig**

After the existing pool configuration lines, add:
```java
config.setConnectionTestQuery("SELECT 1");
config.setValidationTimeout(3000);
```

Note: H2 supports `SELECT 1`. For other databases, adjust accordingly.

- [ ] **Step 2: Run tests to verify no regression**

Run: `mvn test`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/datingapp/storage/DatabaseManager.java
git commit -m "fix(V047): add connection pool validation query to DatabaseManager

Stale connections are now detected before being handed to callers."
```

---

## Phase 5: CLI Quality

### Task 5.1: Fix CLI case normalization inconsistency (V081)

**Context:** All CLI handler input parsing uses `.toLowerCase(Locale.ROOT)` for menu actions except `MatchingHandler` line 657 which uses `.toUpperCase(Locale.ROOT)` for standout selection. This inconsistency could cause input parsing bugs.

**Files:**
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java:657`

- [ ] **Step 1: Find and fix the inconsistent case normalization**

Change `.toUpperCase(Locale.ROOT)` to `.toLowerCase(Locale.ROOT)` on line 657 and verify the comparison target is also lowercase.

- [ ] **Step 2: Check that comparison strings match the new casing**

If the code compares against uppercase letters (e.g., `"Y"`), change them to lowercase (`"y"`).

- [ ] **Step 3: Run tests — verify no regression**

Run: `mvn test -pl . -Dtest="*MatchingHandler*" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/datingapp/app/cli/MatchingHandler.java
git commit -m "fix(V081): normalize CLI standout input to lowercase for consistency"
```

---

## Phase 6: Use-Case Layer Cleanup

### Task 6.1: Reduce use-case constructor overloads (V054)

**Context:** `ProfileUseCases` has **4 public constructors** (lines 60, 78, 96, 111+), `MatchingUseCases` has 2, and `MessagingUseCases` has 2. These backward-compat overloads increase maintenance burden. Since all construction goes through `ServiceRegistry`, the extra constructors serve no purpose.

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`

- [ ] **Step 1: Identify which constructor `ServiceRegistry` uses**

Grep for `new ProfileUseCases(`, `new MatchingUseCases(`, `new MessagingUseCases(` in `ServiceRegistry.java` and `StorageFactory.java`. The constructor called there is the canonical one.

- [ ] **Step 2: Check all other callers**

Grep the entire codebase for each constructor. If only `ServiceRegistry` and tests call them, the extra overloads can be safely removed.

- [ ] **Step 3: Remove redundant constructor overloads**

Keep only the full-argument constructor and update any callers (mostly tests) to use it. For tests, add null/mock for parameters they don't care about.

- [ ] **Step 4: Run full test suite**

Run: `mvn test`
Expected: ALL PASS after caller updates.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java \
       src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java \
       src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java
git commit -m "refactor(V054): remove backward-compat constructor overloads from use-case classes

Keep only the canonical full-argument constructor used by ServiceRegistry."
```

---

## Phase 7: UI Polish

### Task 7.1: Message sanitizer over-stripping (V022)

**Context:** The message sanitizer uses an OWASP STRICT_TEXT policy that strips **all** HTML formatting. Legitimate formatting like bold or italic in chat messages is destroyed. This may be intentional for security, but the issue reports it as over-aggressive.

**Files:**
- Modify: `src/main/java/datingapp/core/profile/SanitizerUtils.java`
- Test: add/update sanitizer tests

- [ ] **Step 1: Read the current sanitizer implementation**

Read `SanitizerUtils.java` fully to understand the STRICT_TEXT policy. Check if it's from OWASP html-sanitizer or a custom implementation.

- [ ] **Step 2: Write tests for desired behavior**

```java
@Test
@DisplayName("Preserves basic formatting tags")
void preservesBasicFormatting() {
    assertEquals("<b>bold</b>", SanitizerUtils.sanitizeMessage("<b>bold</b>"));
    assertEquals("<i>italic</i>", SanitizerUtils.sanitizeMessage("<i>italic</i>"));
}

@Test
@DisplayName("Strips dangerous tags")
void stripsDangerousTags() {
    assertFalse(SanitizerUtils.sanitizeMessage("<script>alert('xss')</script>")
            .contains("<script>"));
}
```

- [ ] **Step 3: Create a CHAT_TEXT policy that allows safe formatting**

If using OWASP HtmlPolicyBuilder:
```java
private static final PolicyFactory CHAT_TEXT = new HtmlPolicyBuilder()
        .allowElements("b", "i", "em", "strong", "u")
        .toFactory();
```

Add a `sanitizeMessage()` method that uses this less restrictive policy, keeping the strict `sanitize()` method for profile fields.

- [ ] **Step 4: Run tests — verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(V022): add chat-specific sanitizer that preserves basic formatting

Profile fields still use STRICT_TEXT. Chat messages allow b/i/em/strong/u."
```

### Task 7.2: Standouts selection confirmation (V077)

**Context:** `StandoutsController.handleStandoutSelected()` (lines 72-79) immediately navigates to PROFILE_VIEW on selection without any confirmation or preview. This is a UX concern — accidental clicks trigger full navigation.

**Current code** (`StandoutsController.java` lines 72-79):
```java
viewModel.markInteracted(entry);
NavigationService.getInstance().setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, entry.userId());
NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE_VIEW);
```

**Files:**
- Modify: `src/main/java/datingapp/ui/screen/StandoutsController.java:72-79`
- Modify: corresponding FXML file if a button is added (check `src/main/resources/fxml/standouts.fxml`)

- [ ] **Step 1: Change selection behavior to populate a preview pane instead of navigating**

Option A (simpler): Replace the ListView `onMouseClicked` / selection listener with a dedicated "View Profile" button. Selection highlights the entry; button press navigates.

Option B: Use double-click for navigation instead of single-click. Keep single-click for selection/preview.

Preferred approach (Option A):
```java
// In handleStandoutSelected — just update preview, don't navigate
private void handleStandoutSelected(StandoutEntry entry) {
    if (entry == null) return;
    selectedEntry = entry;
    viewProfileButton.setDisable(false);
    // Update preview labels with entry info
}

// In a new handleViewProfile method — wired to button
@FXML
private void handleViewProfile() {
    if (selectedEntry == null) return;
    viewModel.markInteracted(selectedEntry);
    NavigationService.getInstance().setNavigationContext(
            NavigationService.ViewType.PROFILE_VIEW, selectedEntry.userId());
    NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE_VIEW);
}
```

Add the button to the FXML:
```xml
<Button fx:id="viewProfileButton" text="View Profile" onAction="#handleViewProfile" disable="true"/>
```

- [ ] **Step 2: Test manually in JavaFX**

Run: `mvn javafx:run` — navigate to Standouts, click an entry (should NOT navigate), then click "View Profile" button (should navigate).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/datingapp/ui/screen/StandoutsController.java \
       src/main/resources/fxml/standouts.fxml
git commit -m "fix(V077): require explicit button press to navigate from standouts selection

Single-click now selects/previews. View Profile button triggers navigation."
```

---

## Phase 8: Testing Gaps

### Task 8.1: Add RecommendationService test (V016/V027)

**Context:** `RecommendationService` is the only critical utility/service class without a dedicated test file. All other services listed in V016/V027 (`TextUtil`, `EnumSetUtil`, `AppConfigValidator`, `ConnectionService`, `CandidateFinder`, `MatchQualityService`, `DailyLimitService`, `DailyPickService`, `StandoutService`, `TrustSafetyService`) already have dedicated tests.

**Files:**
- Create: `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`
- Read: `src/main/java/datingapp/core/matching/RecommendationService.java`

- [ ] **Step 1: Read RecommendationService to understand its API**

Understand which methods it exposes and which services it delegates to.

- [ ] **Step 2: Write tests for each public method**

Test that each method correctly delegates to the underlying service and returns the expected result. Focus on:
- Correct delegation (calls reach the right sub-service)
- Null/empty input handling
- Edge cases specific to recommendation logic

- [ ] **Step 3: Run tests — verify pass**

Run: `mvn test -pl . -Dtest="RecommendationServiceTest"`

- [ ] **Step 4: Commit**

```bash
git add src/test/java/datingapp/core/matching/RecommendationServiceTest.java
git commit -m "test(V016/V027): add dedicated tests for RecommendationService"
```

---

## Phase 9: Optional Quick Cleanup

These are low-impact items. Do them if time permits, skip if not.

### Task 9.1: Remove unused event bus strict mode (V090)

- Locate the strict handling mode in `InProcessAppEventBus`
- If unused (no callers set it to true), remove the field and related code
- Commit: `refactor(V090): remove unused strict handling mode from event bus`

### Task 9.2: Fix purge cleanup no-op defaults (V072)

- Storage interface default methods that return 0/false for purge operations should throw `UnsupportedOperationException` instead of silently succeeding
- This aligns with V078 (already in the harder-work plan) — coordinate if both are being worked

### Task 9.3: CLI deprecated age method cleanup (V039)

- Find all callers of the deprecated no-arg `getAge()` method in `User.java`
- Migrate them to `getAge(ZoneId)` with an explicit timezone
- Remove the deprecated method if no callers remain
- Commit: `fix(V039): migrate all callers to timezone-aware getAge(ZoneId)`

### Task 9.4: Documentation sync (V040, V056)

- Update architecture docs to match current package structure
- Remove references to non-existent code paths
- Commit: `docs(V040/V056): sync architecture documentation with current codebase`

---

## Execution notes

- **Phase 1 is the highest priority** — it fixes a critical model-layer validation gap.
- **Phases 2-4** are quick wins with minimal risk.
- **Phase 5** (CLI) is cosmetic but prevents future bugs.
- **Phase 6** (constructor overloads) has wider blast radius but is mechanical.
- **Phase 7** (UI) requires manual JavaFX testing.
- **Phase 8** (testing) is purely additive.
- **Phase 9** is optional and lowest priority.

Each phase can be implemented independently and committed separately. No cross-phase dependencies exist.
