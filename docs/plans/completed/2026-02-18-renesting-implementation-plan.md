# Re-Nesting & Codebase Compaction — Agent Implementation Plan

**Date:** 2026-02-18
**Baseline:** Branch `main`, commit `fb60855`
**Goal:** Re-nest 6 extracted types, clean up project root, update docs. Net result: **-6 Java files, -15+ root artifacts, cleaner docs**.
**Execution Status:** ✅ Completed on 2026-02-19 (tracking file intentionally retained for future sessions)

---

## CRITICAL: Rules for the Executing Agent

1. Do ONE phase at a time. Verify before proceeding.
2. After each phase, run the verification command. Do NOT proceed if it fails.
3. Do NOT add features, refactor logic, or make changes not listed here.
4. Use `sd -F` (fixed string mode) for bulk find-replace. Fall back to Edit tool if `sd` fails on Windows paths.
5. Always run `mvn spotless:apply` before `mvn compile` or `mvn verify` — Spotless reformats code that may break checkstyle otherwise.

---

## Context: Why This Is Safe Now

The jdt.ls "nested type visibility" false positives were caused by `java.jdt.ls.javac.enabled: "on"` in VS Code user settings — NOT by nested types themselves. Fix applied: javac mode set to `"off"`, `nul` file deleted, vmargs unified. Nested types now resolve correctly in jdt.ls (ECJ compiler). This reversal is safe.

---

## PHASE 1: Re-Nest 4 Types into User.java

**Status (2026-02-19): ✅ Completed**

**Execution note:** Nested `User.Gender`, `User.UserState`, `User.VerificationMethod`, and `User.ProfileNote` were added to `User.java`. Verification was completed at the first stable point after import migration (see Phase 3), because mixed old/new type imports temporarily break compilation.

### Step 1.1: Add nested types to User.java

Open `src/main/java/datingapp/core/model/User.java`. Insert the following 4 nested types **immediately after the opening `public class User {` line (line 25) and before the `private static final AppConfig CONFIG` line (line 27)**. Add a blank line separator after the nested types block.

```java
    // ── Nested domain types ────────────────────────────────────────────

    /** Gender options available for users. */
    public static enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    /**
     * Lifecycle state of a user account.
     * Valid transitions: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
     */
    public static enum UserState {
        INCOMPLETE,
        ACTIVE,
        PAUSED,
        BANNED
    }

    /**
     * Verification method used to verify a profile.
     * NOTE: Currently simulated - email/phone not sent externally.
     */
    public static enum VerificationMethod {
        EMAIL,
        PHONE
    }

    /**
     * A private note that a user can attach to another user's profile. Notes are
     * only visible to the author — the subject never sees them.
     *
     * <p>Use cases:
     * <ul>
     *   <li>Remember where you met someone ("Coffee shop downtown")
     *   <li>Note conversation topics ("Loves hiking, has a dog named Max")
     *   <li>Track date plans ("Dinner Thursday @ Olive Garden")
     * </ul>
     */
    public static record ProfileNote(
            UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {

        /** Maximum length for note content. */
        public static final int MAX_LENGTH = 500;

        public ProfileNote {
            Objects.requireNonNull(authorId, "authorId cannot be null");
            Objects.requireNonNull(subjectId, "subjectId cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

            if (authorId.equals(subjectId)) {
                throw new IllegalArgumentException("Cannot create a note about yourself");
            }
            if (content.isBlank()) {
                throw new IllegalArgumentException("Note content cannot be blank");
            }
            if (content.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Note content exceeds maximum length of " + MAX_LENGTH + " characters");
            }
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("updatedAt cannot be before createdAt");
            }
        }

        /**
         * Creates a new profile note with current timestamp.
         *
         * @param authorId  ID of the user creating the note
         * @param subjectId ID of the user the note is about
         * @param content   the note content
         * @return a new ProfileNote
         */
        public static ProfileNote create(UUID authorId, UUID subjectId, String content) {
            Objects.requireNonNull(authorId, "authorId cannot be null");
            Objects.requireNonNull(subjectId, "subjectId cannot be null");
            Objects.requireNonNull(content, "content cannot be null");

            if (content.isBlank()) {
                throw new IllegalArgumentException("Note content cannot be blank");
            }
            if (content.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Note content exceeds maximum length of " + MAX_LENGTH + " characters");
            }
            if (authorId.equals(subjectId)) {
                throw new IllegalArgumentException("Cannot create a note about yourself");
            }

            Instant now = AppClock.now();
            return new ProfileNote(authorId, subjectId, content, now, now);
        }

        /**
         * Creates an updated version of this note with new content.
         *
         * @param newContent the new content
         * @return a new ProfileNote with updated content and timestamp
         */
        public ProfileNote withContent(String newContent) {
            if (newContent == null || newContent.isBlank()) {
                throw new IllegalArgumentException("Note content cannot be blank");
            }
            if (newContent.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Note content exceeds maximum length of " + MAX_LENGTH + " characters");
            }
            return new ProfileNote(authorId, subjectId, newContent, createdAt, AppClock.now());
        }

        /**
         * Gets a preview of the note content (first 50 chars).
         *
         * @return truncated preview with ellipsis if content is longer
         */
        public String getPreview() {
            if (content.length() <= 50) {
                return content;
            }
            return content.substring(0, 47) + "...";
        }
    }

    // ── End nested domain types ────────────────────────────────────────

```

**IMPORTANT:** User.java already imports `AppClock`, `Instant`, `Objects`, `UUID` — no new imports needed. The `ProfileNote` record uses these from the enclosing class's imports.

### Step 1.1 Verification

```bash
mvn spotless:apply -q && mvn compile -Dcheckstyle.skip=true -q && echo "STEP 1.1 OK"
```

If this fails, the nested type insertion has a syntax error. Fix before proceeding.

---

## PHASE 2: Re-Nest 2 Types into Match.java

**Status (2026-02-19): ✅ Completed**

**Execution note:** Nested `Match.MatchState` and `Match.MatchArchiveReason` were added to `Match.java`.

### Step 2.1: Add nested types to Match.java

Open `src/main/java/datingapp/core/model/Match.java`. Insert the following 2 nested types **immediately after the opening `public class Match {` line (line 19) and before the `private final String id;` line (line 21)**. Add a blank line separator.

```java
    // ── Nested domain types ────────────────────────────────────────────

    /** Represents the current state of a match. */
    public static enum MatchState {
        ACTIVE, // Both users are matched
        FRIENDS, // Mutual transition to platonic friendship
        UNMATCHED, // One user ended the match
        GRACEFUL_EXIT, // One user ended the match kindly
        BLOCKED // One user blocked the other
    }

    /**
     * Reasons why a relationship/match was archived or ended.
     *
     * <p>Note: Some reasons overlap with terminal {@link MatchState} values. We keep
     * both for analytics/history without changing the state machine.
     */
    public static enum MatchArchiveReason {
        FRIEND_ZONE,
        GRACEFUL_EXIT,
        UNMATCH,
        BLOCK
    }

    // ── End nested domain types ────────────────────────────────────────

```

**IMPORTANT:** Match.java already references `MatchState` and `MatchArchiveReason` throughout its body. Since these are now nested types within Match itself, all existing `MatchState.ACTIVE`, `MatchArchiveReason.UNMATCH` etc. references work WITHOUT any changes. No new imports needed.

### Step 2.1 Verification

```bash
mvn spotless:apply -q && mvn compile -Dcheckstyle.skip=true -q && echo "STEP 2.1 OK"
```

---

## PHASE 3: Update All Imports Across the Codebase

**Status (2026-02-19): ✅ Completed**

**Execution note:** All imports of `Gender`, `UserState`, `VerificationMethod`, `ProfileNote`, `MatchState`, and `MatchArchiveReason` were migrated to `User.*` / `Match.*` nested imports. Remaining old imports were verified to be zero.

**Strategy:** Each extracted type had a standalone import like `import datingapp.core.model.Gender;`. After re-nesting, the import becomes `import datingapp.core.model.User.Gender;`. The usage code (`Gender.MALE`, `UserState.ACTIVE`, etc.) does NOT change — only the import line changes.

**KEY EXCEPTION:** Files in the `datingapp.core.model` package (User.java, Match.java) do NOT use imports for same-package types. After re-nesting, these types are inner members of the class itself — no change needed in User.java or Match.java.

### Step 3.1: Replace Gender imports

**Find affected files:**
```bash
rg -l "import datingapp\.core\.model\.Gender;" --glob "*.java" src/
```
Expected: ~24 files (all outside `core.model` package).

**Replace:**
```bash
sd -F "import datingapp.core.model.Gender;" "import datingapp.core.model.User.Gender;" $(rg -l "import datingapp\.core\.model\.Gender;" --glob "*.java" src/)
```

If `sd` fails with Windows path issues, use the Edit tool to replace in each file individually:
- Old: `import datingapp.core.model.Gender;`
- New: `import datingapp.core.model.User.Gender;`

### Step 3.2: Replace UserState imports

```bash
sd -F "import datingapp.core.model.UserState;" "import datingapp.core.model.User.UserState;" $(rg -l "import datingapp\.core\.model\.UserState;" --glob "*.java" src/)
```

### Step 3.3: Replace VerificationMethod imports

```bash
sd -F "import datingapp.core.model.VerificationMethod;" "import datingapp.core.model.User.VerificationMethod;" $(rg -l "import datingapp\.core\.model\.VerificationMethod;" --glob "*.java" src/)
```

### Step 3.4: Replace ProfileNote imports

```bash
sd -F "import datingapp.core.model.ProfileNote;" "import datingapp.core.model.User.ProfileNote;" $(rg -l "import datingapp\.core\.model\.ProfileNote;" --glob "*.java" src/)
```

### Step 3.5: Replace MatchState imports

```bash
sd -F "import datingapp.core.model.MatchState;" "import datingapp.core.model.Match.MatchState;" $(rg -l "import datingapp\.core\.model\.MatchState;" --glob "*.java" src/)
```

### Step 3.6: Replace MatchArchiveReason imports

```bash
sd -F "import datingapp.core.model.MatchArchiveReason;" "import datingapp.core.model.Match.MatchArchiveReason;" $(rg -l "import datingapp\.core\.model\.MatchArchiveReason;" --glob "*.java" src/)
```

### Step 3.7: Verify no old imports remain

```bash
rg "import datingapp\.core\.model\.(Gender|UserState|VerificationMethod|ProfileNote|MatchState|MatchArchiveReason);" --glob "*.java" src/
```

Expected: **ZERO results.** All should now be `User.Gender`, `User.UserState`, `Match.MatchState`, etc.

If any remain, fix them manually.

### Phase 3 Verification

```bash
mvn spotless:apply -q && mvn compile -Dcheckstyle.skip=true -q && echo "PHASE 3 OK"
```

---

## PHASE 4: Delete the 6 Standalone Files

**Status (2026-02-19): ✅ Completed**

**Execution note:** Deleted `Gender.java`, `UserState.java`, `VerificationMethod.java`, `ProfileNote.java`, `MatchState.java`, and `MatchArchiveReason.java` from `core/model/`. Compile verification passed.

Only delete AFTER Phase 3 verification passes. The standalone files are now redundant — their content lives inside User.java and Match.java.

```bash
rm src/main/java/datingapp/core/model/Gender.java
rm src/main/java/datingapp/core/model/UserState.java
rm src/main/java/datingapp/core/model/VerificationMethod.java
rm src/main/java/datingapp/core/model/ProfileNote.java
rm src/main/java/datingapp/core/model/MatchState.java
rm src/main/java/datingapp/core/model/MatchArchiveReason.java
```

### Phase 4 Verification

```bash
mvn spotless:apply -q && mvn compile -Dcheckstyle.skip=true -q && echo "PHASE 4 OK"
```

**If compilation fails** after deleting the files, it means some import was missed in Phase 3. Check the error message to find which file still references the old standalone type. Add the correct `User.Gender` or `Match.MatchState` import.

---

## PHASE 5: Update NestedTypeVisibilityTest

**Status (2026-02-19): ✅ Completed**

**Execution note:** `NestedTypeVisibilityTest` now validates `User` and `Match` re-nested types as `public static` and keeps cross-package accessibility checks for CLI/UI nested types.

The test at `src/test/java/datingapp/core/NestedTypeVisibilityTest.java` currently tests that the 4 extracted types are public top-level classes. After re-nesting, they're public static nested types. Rewrite the `topLevelTypesArePublic` test.

### Step 5.1: Update imports in the test file

Replace these 4 imports:
```java
import datingapp.core.model.Gender;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.UserState;
import datingapp.core.model.VerificationMethod;
```

With:
```java
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
```

**NOTE:** These should have already been replaced in Phase 3. Verify and fix if not.

### Step 5.2: Also add Match imports

Add these imports (the test should also verify Match nested types):
```java
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.Match.MatchArchiveReason;
```

### Step 5.3: Rewrite the `topLevelTypesArePublic` test method

Replace the current `topLevelTypesArePublic()` method with:

```java
    @Test
    @DisplayName("Re-nested types in User must be public and static")
    void userNestedTypesArePublicAndStatic() {
        assertTrue(Modifier.isPublic(Gender.class.getModifiers()), "User.Gender must be public");
        assertTrue(Modifier.isStatic(Gender.class.getModifiers()), "User.Gender must be static");
        assertTrue(Modifier.isPublic(UserState.class.getModifiers()), "User.UserState must be public");
        assertTrue(Modifier.isStatic(UserState.class.getModifiers()), "User.UserState must be static");
        assertTrue(
                Modifier.isPublic(VerificationMethod.class.getModifiers()),
                "User.VerificationMethod must be public");
        assertTrue(
                Modifier.isStatic(VerificationMethod.class.getModifiers()),
                "User.VerificationMethod must be static");
        assertTrue(Modifier.isPublic(ProfileNote.class.getModifiers()), "User.ProfileNote must be public");
        assertTrue(Modifier.isStatic(ProfileNote.class.getModifiers()), "User.ProfileNote must be static");
    }

    @Test
    @DisplayName("Re-nested types in Match must be public and static")
    void matchNestedTypesArePublicAndStatic() {
        assertTrue(Modifier.isPublic(MatchState.class.getModifiers()), "Match.MatchState must be public");
        assertTrue(Modifier.isStatic(MatchState.class.getModifiers()), "Match.MatchState must be static");
        assertTrue(
                Modifier.isPublic(MatchArchiveReason.class.getModifiers()),
                "Match.MatchArchiveReason must be public");
        assertTrue(
                Modifier.isStatic(MatchArchiveReason.class.getModifiers()),
                "Match.MatchArchiveReason must be static");
    }

    @Test
    @DisplayName("Cross-package nested types used by CLI/UI are accessible")
    void crossPackageNestedTypesAccessible() {
        assertTrue(Modifier.isPublic(PacePreferences.class.getModifiers()), "PacePreferences must be public");
        assertTrue(
                Modifier.isPublic(RecommendationService.DailyPick.class.getModifiers()),
                "DailyPick must be public");
        assertTrue(Modifier.isPublic(InputReader.class.getModifiers()), "InputReader must be public");
        assertTrue(
                Modifier.isPublic(UiAnimations.ConfettiAnimation.class.getModifiers()),
                "ConfettiAnimation must be public");
    }
```

Remove the unused `Match` import if you added it but only use `Match.MatchState` and `Match.MatchArchiveReason` imports (the nested types import automatically makes the outer class accessible for reflection).

### Phase 5 Verification

```bash
mvn spotless:apply -q && mvn compile -Dcheckstyle.skip=true -q && echo "PHASE 5 OK"
```

---

## PHASE 6: Run Full Test Suite

**Status (2026-02-19): ✅ Completed**

**Execution note:** Ran clean test compilation and full test suite successfully after fixing all nested-type import fallout in tests (`mvn clean test-compile`, then `mvn test`).

```bash
mvn spotless:apply -q && mvn test -q 2>&1 | tail -20
```

All tests must pass. If any test fails:
1. Read the failure message
2. It's almost certainly a missed import replacement
3. Fix the import and re-run

---

## PHASE 7: Update Documentation

**Status (2026-02-19): ✅ Completed**

**Execution note:** Updated `.github/copilot-instructions.md`, `AGENTS.md`, and `CLAUDE.md` to nested-type source of truth; also refreshed external memory notes (`~/.claude/projects/.../memory/MEMORY.md`) to match the current architecture.

### Step 7.1: CLAUDE.md Updates and copilot-instructions.md Updates

**7.1a — Critical Gotchas table:** Remove or update these rows:

| Old Row                                                                                                                     | Action                                                                                                                     |
|-----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `Nested User.Gender ref` / `import datingapp.core.model.User.Gender` → `Use standalone: import datingapp.core.model.Gender` | **REVERSE**: Now the nested import IS correct. Update to say `import datingapp.core.model.User.Gender` is the correct form |
| `Nested Match.State ref` / `Match.State.ACTIVE` → `Use standalone: MatchState.ACTIVE`                                       | **REVERSE**: Update to say `import datingapp.core.model.Match.MatchState` then use `MatchState.ACTIVE`                     |
| `Externally-used nested type` row                                                                                           | **DELETE** this row entirely — nested types work fine now                                                                  |
| `Non-static nested types` row                                                                                               | **KEEP** — this is still a valid gotcha (inner classes need `static`)                                                      |

**7.1b — Package Structure table:** Update `core/model/` row:

Old: `Entities + standalone enums (8 files): User, Match, Gender, UserState, VerificationMethod, MatchState, MatchArchiveReason, ProfileNote`

New: `Entities (2 files): User (+ Gender, UserState, VerificationMethod, ProfileNote nested), Match (+ MatchState, MatchArchiveReason nested)`

**7.1c — Domain Models table:** Update to reflect nested types:

| Model                      | Location          | Key Info                                                                                                           |
|----------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------|
| `User`                     | `core/model/`     | Mutable; contains `Gender`, `UserState`, `VerificationMethod`, `ProfileNote` as nested types; has `StorageBuilder` |
| `User.Gender`              | nested in `User`  | `MALE`, `FEMALE`, `OTHER`                                                                                          |
| `User.UserState`           | nested in `User`  | `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED`                                                                         |
| `User.VerificationMethod`  | nested in `User`  | `EMAIL`, `PHONE`                                                                                                   |
| `User.ProfileNote`         | nested in `User`  | Record: private notes on profiles; uses `AppClock.now()`                                                           |
| `Match`                    | `core/model/`     | Mutable; contains `MatchState`, `MatchArchiveReason` as nested types; deterministic ID                             |
| `Match.MatchState`         | nested in `Match` | `ACTIVE`, `FRIENDS`, `UNMATCHED`, `GRACEFUL_EXIT`, `BLOCKED`                                                       |
| `Match.MatchArchiveReason` | nested in `Match` | `FRIEND_ZONE`, `GRACEFUL_EXIT`, `UNMATCH`, `BLOCK`                                                                 |

**7.1d — "No externally-used nested types" paragraph:** Replace the current paragraph under "Domain Models" that says:

> **No externally-used nested types.** Any enum, record, or class referenced by other files MUST be its own top-level `.java` file...

With:

> **Nested types are fine.** The jdt.ls visibility issue was caused by experimental javac mode (`java.jdt.ls.javac.enabled: "on"`), NOT by nested types themselves. With javac mode off (ECJ compiler), nested types resolve correctly. Use `public static` for any nested type accessed from other files. Keep the `static` keyword — non-static inner classes cannot be accessed cross-package.

**7.1e — NEVER Do These list:**

Remove these 3 items:
- ❌ `Define externally-used types as nested (JLS/jdt.ls can't resolve cross-file nested refs — extract to own file in same package)`
- ❌ `Use User.Gender/User.UserState nested refs (extracted to standalone Gender, UserState in core/model/)`
- ❌ `Use Match.State (extracted to standalone MatchState in core/model/)`

Add this 1 item:
- ❌ `Forget static on nested types used by other files (public static enum/record/class — non-static inner classes break cross-package access)`

**7.1f — Key Patterns section — StorageBuilder pattern:** Update the code example to use `User.Gender` import or explain that Gender is now nested:
```java
// Gender, UserState, etc. are now nested in User
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
```

**7.1g — Stats line:** Update file count:
```
Old: **Stats (2026-02-18):** 143 Java files (84 main + 59 test)
New: **Stats (2026-02-18):** 137 Java files (78 main + 59 test)
```

Wait — 84 main - 6 deleted = 78 main. Total = 78 + 59 = 137.

**7.1h — Recent Updates section:** Add entry:
```
- **02-18**: Re-nested 6 types (Gender, UserState, VerificationMethod, ProfileNote → User; MatchState, MatchArchiveReason → Match). jdt.ls fix (javac mode off) made extraction unnecessary. -6 files. Relaxed nested type rules. Cleaned project root.
```

**7.1i — Agent Changelog:** Append entry:
```
22|2026-02-18 HH:MM:00|agent:claude_code|scope:renesting|Re-nested 6 types into User/Match, updated imports across 50+ files, relaxed nested type rules, cleaned project root|CLAUDE.md;User.java;Match.java;NestedTypeVisibilityTest.java
```

### Step 7.2: AGENTS.md Updates

Search for any references to standalone `Gender`, `UserState`, `MatchState`, `MatchArchiveReason`, `VerificationMethod`, `ProfileNote` in AGENTS.md and update them to reflect the nested type pattern.

**Find references:**
```bash
rg "(standalone|top-level).*(Gender|UserState|MatchState|MatchArchiveReason|VerificationMethod|ProfileNote)" AGENTS.md
```

Update any guidance about "extract to own file" → "nest with public static".

### Step 7.3: MEMORY.md Updates

In `C:\Users\tom7s\.claude\projects\c--Users-tom7s-Desktopp-Claude-Folder-2-Date-Program\memory\MEMORY.md`:

**Update the Architecture Decisions section:**

Old:
```
- **`core/model/` has 8 files**: User, Match + 6 standalone enums/records: Gender, UserState, VerificationMethod, MatchState, MatchArchiveReason, ProfileNote. **Enums are NOT nested in User anymore** — all extracted to standalone files.
```

New:
```
- **`core/model/` has 2 files**: User.java (+ Gender, UserState, VerificationMethod, ProfileNote nested), Match.java (+ MatchState, MatchArchiveReason nested). **Re-nested 2026-02-18** after jdt.ls javac fix made standalone extraction unnecessary.
```

---

## PHASE 8: Project Root Cleanup

**Status (2026-02-19): ✅ Completed (with one intentional skip)**

**Execution note:** Deleted all listed stale analysis/log artifacts from the project root and moved `new_Issues/` to `docs/new_Issues/` (preserved, not deleted).

**Step 8.4 decision:** Kept this plan file intentionally (did **not** delete) to satisfy cross-session progress tracking requested by the user.

### Step 8.1: Delete analysis/plan files from project root

These files were created during the nested type investigation and are no longer needed:

```bash
rm "NESTED_TYPE_EXTRACTION_AGENT_PLAN.md"
rm "CODEBASE_AUDIT_REPORT_2026-02-16.md"
rm "CODEBASE_AUDIT_REPORT_Number_2_2026-02-16.md"
rm "FILE_COUNT_REDUCTION_ANALYSIS.md"
rm "IMPLEMENTATION_PLAN_2026-02-16.md"
rm "IMPLEMENTATION_PLAN_Number_2_2026-02-16.md"
rm "PROJECT_STRUCTURE_ANALYSIS_2026-02-18.md"
rm "cross-package-nested-types-extraction-plan-2026-02-16.md"
rm "jdt.ls_investigation.md"
rm "2026-02-18-nested-types-reversal-assessment.md"
```

### Step 8.2: Delete stale log/temp files

```bash
rm "check_user_nested.txt"
rm "compile_log.txt"
rm "compile_log_full.txt"
rm "test_compile_log.txt"
rm "test_compile_log_2.txt"
rm "test_run_log.txt"
```

### Step 8.3: Move new_Issues to docs/

```bash
mv new_Issues/ docs/new_Issues/
```

Or if the user prefers, delete them:
```bash
rm -rf new_Issues/
```

**ASK THE USER** before deleting `new_Issues/` — it may contain actionable items.

### Step 8.4: Delete THIS plan file after execution

```bash
rm "2026-02-18-renesting-implementation-plan.md"
```

---

## PHASE 9: Final Full Verification

**Status (2026-02-19): ✅ Completed**

**Execution note:** Completed final quality gate with `mvn spotless:apply` + `mvn clean verify`.

**Verified results:**
- `BUILD SUCCESS`
- `Tests run: 797, Failures: 0, Errors: 0, Skipped: 0`
- `You have 0 Checkstyle violations`
- PMD check passed

**Additional note:** A non-clean `verify` run initially surfaced stale test-class artifacts; root cause resolved by running a clean verify build.

```bash
mvn spotless:apply && mvn verify 2>&1 | tee verify_output.txt
```

Capture and check:
```bash
# Check build result
grep "BUILD" verify_output.txt
# Check test count
grep "Tests run:" verify_output.txt
# Check for errors
grep -E "ERROR|FAILURE" verify_output.txt
```

**Expected results:**
- `BUILD SUCCESS`
- All tests pass (800+ tests)
- Zero PMD/Spotless/Checkstyle violations

Clean up:
```bash
rm verify_output.txt
```

---

## PHASE 10: Additional Improvements (Now That Nested Types Work)

**Status (2026-02-19): ✅ Partially completed (optional phase)**

**Execution note:**
- ✅ 10.2 completed: added a dedicated nested type guideline subsection to `CLAUDE.md`.
- ✅ 10.1 reflected in memory guidance: `CliTextAndInput` nested helper types are explicitly marked as intentionally nested.
- ⏭️ 10.3 not executed (no `.gemini/` deletion attempted; user confirmation required by plan before removal).

These are OPTIONAL improvements the agent MAY do after the core re-nesting is complete and verified. Each is independent.

### 10.1: Relax CliTextAndInput nested type guidance

`CliTextAndInput` contains `InputReader` and `EnumMenu` as nested types. These were scheduled for extraction in Batch 7 of the old plan. **No longer needed.** They can stay nested. Verify CLAUDE.md reflects this:

Old: `Contains nested InputReader and EnumMenu classes` (implying they should be extracted)
New: `Contains nested InputReader and EnumMenu classes (stays nested — jdt.ls handles fine)`

### 10.2: Future-proof the CLAUDE.md guidance

Add a new "Nested Type Guidelines" subsection under Key Patterns:

```markdown
### Nested Type Guidelines (Updated 2026-02-18)
- **Nested types are welcome.** The old "no cross-package nested types" rule is retired.
- Always use `public static` for nested enums, records, and classes accessed from other files.
- Good candidates for nesting: enums that belong to a model (Gender in User), result records that belong to a service (SwipeResult in MatchingService), inner builder classes.
- Consider standalone files only for types imported by 15+ files AND representing independent domain concepts.
- The jdt.ls javac bug is environment-specific. Keep `java.jdt.ls.javac.enabled: "off"` in VS Code settings.
```

### 10.3: Consider merging .gemini investigation files

The `.gemini/` directory has 5 investigation files from the IDE diagnostics analysis. These could be consolidated or deleted if no longer useful.

**ASK THE USER** before deleting `.gemini/` content.

---

## Summary: Before vs After

| Metric                      | Before          | After                         |
|-----------------------------|-----------------|-------------------------------|
| Main Java files             | 84              | **78** (-6)                   |
| Test Java files             | 59              | 59 (unchanged)                |
| Total Java files            | 143             | **137** (-6)                  |
| `core/model/` files         | 8               | **2** (User.java, Match.java) |
| Root analysis files         | ~16             | **0** (deleted/archived)      |
| Root temp .txt files        | 6               | **0** (deleted)               |
| CLAUDE.md nested type rules | 3 "NEVER" items | **1** (just "use static")     |
| Packages                    | 17              | 17 (unchanged)                |

---

## Quick Reference: Import Changes

| Old Import                                        | New Import                                              |
|---------------------------------------------------|---------------------------------------------------------|
| `import datingapp.core.model.Gender;`             | `import datingapp.core.model.User.Gender;`              |
| `import datingapp.core.model.UserState;`          | `import datingapp.core.model.User.UserState;`           |
| `import datingapp.core.model.VerificationMethod;` | `import datingapp.core.model.User.VerificationMethod;`  |
| `import datingapp.core.model.ProfileNote;`        | `import datingapp.core.model.User.ProfileNote;`         |
| `import datingapp.core.model.MatchState;`         | `import datingapp.core.model.Match.MatchState;`         |
| `import datingapp.core.model.MatchArchiveReason;` | `import datingapp.core.model.Match.MatchArchiveReason;` |

**Usage code does NOT change.** Only import lines change. `Gender.MALE`, `UserState.ACTIVE`, `MatchState.BLOCKED` etc. remain exactly the same.

---

## Risk Assessment

| Risk                                      | Mitigation                                                                    |
|-------------------------------------------|-------------------------------------------------------------------------------|
| Missed import replacement → compile error | Phase 3 verification catches this. `rg` check finds stragglers.               |
| ProfileNote record loses functionality    | Content is copy-pasted verbatim including all methods.                        |
| Test failures from stale references       | Phase 6 full test suite catches this.                                         |
| Doc/code drift                            | Phase 7 updates all 3 doc files systematically.                               |
| Accidental deletion of needed files       | Phase 8 explicitly lists each file. Agent asks before deleting `new_Issues/`. |

---

## Naming Decision: Why `Match.MatchState` Not `Match.State`

The extracted name `MatchState` is kept (not reverted to original `State`) because:
1. **4 files import both `UserState` and `MatchState`** — having `State` would be ambiguous
2. **Zero code changes in file bodies** — only import lines change (minimal blast radius)
3. **`MatchState.ACTIVE` is clearer than `State.ACTIVE`** in isolation
4. The redundancy `Match.MatchState` only appears in the import line; everywhere else it's just `MatchState`
