# Level 4 — Test Cleanup Implementation Plan

> **Source:** [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) §Level 4
> **Created:** 2026-05-07
> **Scope:** 8 items (all valid against current code, with 2 minor count corrections from audit).
> **Risk profile:** Minimal. Test-only changes; zero production code impact.
> **Estimated net change:** ~1 file deleted, ~3 files added (test splits), ~–520 LOC.

---

## Progress Tracking
- As you finish each step, mark it `✅ IMPLEMENTED`.
- When the plan is fully implemented end-to-end, add `✅ IMPLEMENTED` immediately below the title at the top of this file.

## Pre-flight

1. The user manages all git operations. **Do not run `git` commands** at any point — including `git mv` for the file relocation in Item 2. Use the regular file-system move instead.
2. **Levels 1–3 should be merged first** if possible — Level 4 touches test fixtures that use production helpers like `TestStorages.Users`. If Level 3's `TestServiceRegistryBuilder` unification (L3.10) is in progress, coordinate with the user.
3. Run baseline:
   ```powershell
   mvn test
   ```
   Expect 1852 tests, 0 failures.
4. **Sequencing note.** Level 4 items are largely independent. The sequencing below is for a single-agent workflow that minimizes context-switching.

---

## Pre-condition check: shared test helpers must exist

Before items 3, 4, 5, 6 (which replace inner classes with shared helpers), confirm each helper actually exists:

```powershell
rg -l "class Users\b|class Undos\b" src/test/java/datingapp/core/testutil/TestStorages.java
rg "TestEventBus\.throwing|class ThrowingEventBus" src/test/java/datingapp/app/event/
rg "TestAchievementService\.unlocked|public static.*unlocked" src/test/java/datingapp
```

For each helper that doesn't exist:
- **`TestStorages.Users` / `TestStorages.Undos`** — confirmed in CLAUDE.md memory ("TestStorages inner classes: Users, Interactions, Communications, Analytics, TrustSafety"). `Undos` may not yet exist; verify and add a minimal `InMemoryUndoStorage` implementation under `TestStorages.Undos` if missing.
- **`TestEventBus.throwing()`** — if absent, add a static factory that returns a bus whose `publish(...)` always throws. This becomes a Level-4 prerequisite step (Item 0 below).
- **`TestAchievementService.unlocked(...)`** — same. If absent, add a static factory returning a `TestAchievementService` configured to unlock a single achievement.

**If any helper is missing**, do Item 0 first.

---

## Item 0 (conditional) — Add missing shared test helpers

Run only if the pre-condition check found gaps. Otherwise skip to Item 1.

**Goal:** Provide the canonical test helpers that Items 2–6 depend on.

**Files affected (by gap):**
- ADD/MODIFY: `src/test/java/datingapp/core/testutil/TestStorages.java` — add `Undos` inner class if missing.
- ADD/MODIFY: `src/test/java/datingapp/app/event/TestEventBus.java` — add `public static AppEventBus throwing()` if missing.
- ADD/MODIFY: `src/test/java/datingapp/.../TestAchievementService.java` — add `public static TestAchievementService unlocked(Achievement single)` if missing.

**Steps for each gap:**
1. Pick the most complete existing inner-class implementation in the test tree (e.g., the `InMemoryUndoStorage` in `RestApiTestFixture.java`) and lift its method bodies into the canonical helper.
2. Make sure the canonical helper has *all* the methods the inner-class versions used. Count surface area before lifting.
3. Run the test suite once to confirm the new helper compiles and passes.

**Verification:**
```powershell
mvn test-compile
mvn -Dtest=TestStoragesTest test    # If a smoke test exists
```

---

## Sequencing

| Order | Item                                                      | Why this order                               |
|-------|-----------------------------------------------------------|----------------------------------------------|
| 1     | L4.4 — Fix `AsyncErrorRouterTest` import                  | Smallest item; warm-up                       |
| 2     | L4.6 — Move `TestJdbiMapping.java`                        | File move only; no logic change              |
| 3     | L4.1 — Replace `InMemoryUserStorage` in 3 tests           | Needs `TestStorages.Users`                   |
| 4     | L4.2 — Replace `ThrowingEventBus` in 2 tests              | Needs `TestEventBus.throwing()`              |
| 5     | L4.3 — Replace `SingleAchievementService` in 2 tests      | Needs `TestAchievementService.unlocked(...)` |
| 6     | L4.8 — Replace `InMemoryUndoStorage` in 3 tests           | Needs `TestStorages.Undos`                   |
| 7     | L4.5 — Move 2 tests, delete `EdgeCaseRegressionTest.java` | Cross-file test relocation                   |
| 8     | L4.7 — Split `RestApiPhaseTwoRoutesTest.java`             | Largest single change; do last               |

---

## Item 1 — Fix `AsyncErrorRouterTest` import (L4.4)

**Goal:** Replace the test's private `TestUiThreadDispatcher` with the canonical `UiAsyncTestSupport.TestUiThreadDispatcher`.

**Audit confirmed:** `AsyncErrorRouterTest.java:60` defines a private duplicate; `UiAsyncTestSupport.TestUiThreadDispatcher` is the public version.

**Files affected:**
- MODIFY: [src/test/java/datingapp/ui/async/AsyncErrorRouterTest.java](../../../src/test/java/datingapp/ui/async/AsyncErrorRouterTest.java)

**Steps:**
1. Read both classes (the private one at line 60–78; the public one in `UiAsyncTestSupport`). Confirm the public version's API is a superset of the private version.
2. Delete the inner `TestUiThreadDispatcher`.
3. Import `datingapp.ui.async.UiAsyncTestSupport.TestUiThreadDispatcher` (or use a fully-qualified reference).
4. Replace any constructor call `new TestUiThreadDispatcher()` with the equivalent factory call from `UiAsyncTestSupport` if the public version uses a factory pattern.

**Verification:**
```powershell
mvn -Dtest=AsyncErrorRouterTest test
```

**Risk:** None. Compiler enforces correctness.

---

## Item 2 — Move `TestJdbiMapping.java` (L4.6)

**Goal:** Move from top-level test package `datingapp/` to `datingapp/storage/jdbi/` to match the package of the production code it tests.

**Audit confirmed:** `src/test/java/datingapp/TestJdbiMapping.java` exists at the top-level test package.

**Files affected:**
- MOVE: `src/test/java/datingapp/TestJdbiMapping.java` → `src/test/java/datingapp/storage/jdbi/TestJdbiMapping.java`

**Steps:**
1. Ensure target directory exists: `src/test/java/datingapp/storage/jdbi/`.
2. Move the file using the regular filesystem (read original contents, write to new path, delete original — or use the IDE's move-file refactor). Do **not** invoke `git mv`; the user will handle history preservation when they stage the change.
3. Update the package declaration at the top of the file to `package datingapp.storage.jdbi;`.
4. Search for callers/imports:
   ```powershell
   rg "import datingapp\.TestJdbiMapping" src/
   ```
   Update any matches to `import datingapp.storage.jdbi.TestJdbiMapping;`.
5. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=TestJdbiMapping test
```

**Risk:** Low. Only the package declaration and any imports need updating; the compiler enforces correctness.

**Note:** If `TestJdbiMapping` is itself a JUnit test class, ensure Surefire still picks it up after the move. Surefire's default include pattern (`**/Test*.java`, `**/*Test.java`, `**/*Tests.java`) covers `TestJdbiMapping.java` regardless of package.

---

## Item 3 — Replace `InMemoryUserStorage` inner classes (L4.1)

**Goal:** Three test files each define their own trivial `InMemoryUserStorage` inner class. Replace with `TestStorages.Users`.

**Audit confirmed:** Inner class exists in:
- `DailyPickServiceTest.java`
- `AchievementServiceTest.java`
- `ProfileCreateSelectTest.java`

**Files affected:**
- MODIFY: each of the 3 test files above.
- POSSIBLY MODIFY: `TestStorages.java` if its `Users` API is missing a method that an inner version provided.

**Steps:**
1. For each test file:
   - Read the inner `InMemoryUserStorage` and note any methods it implements.
   - Confirm `TestStorages.Users` provides each method. If not, lift the missing method into `TestStorages.Users`.
   - Delete the inner class.
   - Replace `new InMemoryUserStorage(...)` with `new TestStorages.Users(...)` (or whatever the canonical constructor signature is).
2. Verify imports.

**Verification:**
```powershell
mvn -Dtest=DailyPickServiceTest,AchievementServiceTest,ProfileCreateSelectTest test
```

**Risk:** Low. If the inner version had a method missing from `TestStorages.Users`, the compiler catches it.

---

## Item 4 — Replace `ThrowingEventBus` inner classes (L4.2)

**Goal:** Two test files each define `ThrowingEventBus`. Replace with `TestEventBus.throwing()`.

**Audit confirmed:** Inner class in `MessagingUseCasesTest.java` and `SocialUseCasesTest.java`.

**Files affected:**
- MODIFY: [src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java](../../../src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java)
- MODIFY: [src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java](../../../src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java)

**Steps:**
1. Confirm `TestEventBus.throwing()` exists (otherwise see Item 0).
2. In each test file:
   - Delete the inner `ThrowingEventBus` class.
   - Replace `new ThrowingEventBus()` with `TestEventBus.throwing()`.
3. Verify behavior parity. The inner versions might throw a specific exception type; if so, `TestEventBus.throwing()` must throw the same type or a compatible one. If not, parameterize the helper:
   ```java
   TestEventBus.throwing(() -> new SpecificException("..."))
   ```

**Verification:**
```powershell
mvn -Dtest=MessagingUseCasesTest,SocialUseCasesTest test
```

**Risk:** Low–medium. Tests asserting on specific exception types must continue to see the same type.

---

## Item 5 — Replace `SingleAchievementService` inner classes (L4.3)

**Goal:** Two test files duplicate `SingleAchievementService`. Replace with `TestAchievementService.unlocked(...)`.

**Audit confirmed:** Inner class in `StatsControllerTest.java` and `StatsViewModelTest.java`.

**Files affected:**
- MODIFY: [src/test/java/datingapp/ui/screen/StatsControllerTest.java](../../../src/test/java/datingapp/ui/screen/StatsControllerTest.java)
- MODIFY: [src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java](../../../src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java)

**Steps:**
1. Confirm `TestAchievementService.unlocked(...)` exists. If not, see Item 0.
2. In each test file:
   - Delete the inner `SingleAchievementService` class.
   - Replace `new SingleAchievementService(achievement)` with `TestAchievementService.unlocked(achievement)`.
3. Verify imports.

**Verification:**
```powershell
mvn -Dtest=StatsControllerTest,StatsViewModelTest test
```

**Risk:** Low.

---

## Item 6 — Replace `InMemoryUndoStorage` inner classes (L4.8)

**Goal:** Three test files duplicate `InMemoryUndoStorage`. Replace with `TestStorages.Undos`.

**Audit correction:** Plan claimed 2 copies; audit found 3:
- `RestApiPhaseTwoRoutesTest.java`
- `RestApiTestFixture.java`
- `LikerBrowserServiceTest.java`

`InMemoryStandoutStorage` (originally in scope) exists in only 1 file (`RestApiTestFixture.java`) — leave it as a private inner class. The plan's "consolidate 3 copies" claim was wrong.

**Files affected:**
- MODIFY: the 3 files listed above.
- POSSIBLY MODIFY: `TestStorages.java` to add an `Undos` inner class if missing (Item 0).

**Steps:**
1. Confirm `TestStorages.Undos` exists (Item 0 if not).
2. For each of the 3 test files:
   - Read the inner `InMemoryUndoStorage` and note its full method surface.
   - Make sure `TestStorages.Undos` covers all methods.
   - Delete the inner class.
   - Replace constructor calls.
3. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=RestApiPhaseTwoRoutesTest,LikerBrowserServiceTest test
mvn -Dtest=RestApiTestFixture* test    # If a smoke test exists
```

**Risk:** Low. The 3 inner versions are likely identical or near-identical.

---

## Item 7 — Move tests, delete `EdgeCaseRegressionTest.java` (L4.5)

**Goal:** `EdgeCaseRegressionTest` contains 2 tests that belong elsewhere. Move them and delete the file.

**Audit confirmed:** Two tests:
- `rejectsBlankNames()` (line 28–35) → belongs in `ValidationServiceTest`.
- `duplicateMatchCreationDoesNotCrash()` (line 37–84) → belongs in `MatchingServiceTest`.

**Files affected:**
- MODIFY: `src/test/java/datingapp/core/ValidationServiceTest.java` — add `rejectsBlankNames()`.
- MODIFY: `src/test/java/datingapp/core/matching/MatchingServiceTest.java` — add `duplicateMatchCreationDoesNotCrash()`.
- DELETE: `src/test/java/datingapp/core/EdgeCaseRegressionTest.java`.

**Steps:**
1. Read each test in `EdgeCaseRegressionTest` and note its imports, fixture setup, and assertions.
2. For `rejectsBlankNames()`:
   - Copy the test method body into `ValidationServiceTest`.
   - Reuse `ValidationServiceTest`'s existing fixture/setup if compatible. If `EdgeCaseRegressionTest` had unique setup, decide whether it's necessary or can be inlined.
   - Verify imports.
3. Same for `duplicateMatchCreationDoesNotCrash()` → `MatchingServiceTest`. The duplicate-match test is more complex (race-condition simulation); preserve every line verbatim before consolidating.
4. Run both target tests in isolation to confirm they pass before deleting the source file.
5. Delete `EdgeCaseRegressionTest.java`.

**Verification:**
```powershell
mvn -Dtest=ValidationServiceTest,MatchingServiceTest test
```

**Risk:** Medium. The race-condition test may rely on subtle setup ordering. If `MatchingServiceTest` has a different fixture, the test can drift in behavior. Read carefully and run repeatedly:
```powershell
mvn -Dtest=MatchingServiceTest#duplicateMatchCreationDoesNotCrash test
# Run a handful of times — flaky-detection
```

---

## Item 8 — Split `RestApiPhaseTwoRoutesTest.java` (L4.7)

**Goal:** Split a 1119-line test file into smaller, route-specific test classes.

**Audit correction:** Plan claimed ~1007 lines; actual is 1119.

**Audit identified categories:**
1. Notification, stats, achievement routes
2. Matching support (pending likers, standouts, quality, undo)
3. Profile edit snapshot routes
4. Presentation context routes
5. Profile update + conversation mutation
6. Mutating user route identity enforcement
7. Conversation route header/query enforcement
8. Shared use-case failure mapping
9. Profile save/match/message/exit flow
10. Pass route daily limit enforcement
11. Report/block flow messaging prevention
12. Location lookup + profile update API parity

**Proposed split (verify by reading actual test names first):**

| New file                               | Categories  |
|----------------------------------------|-------------|
| `RestApiNotificationStatsTest.java`    | 1           |
| `RestApiMatchingSupportTest.java`      | 2, 4, 10    |
| `RestApiProfileSnapshotTest.java`      | 3, 12       |
| `RestApiConversationFlowTest.java`     | 5, 7, 9, 11 |
| `RestApiSharedFailureMappingTest.java` | 6, 8        |

The exact split depends on which tests share fixture state — keep tests with shared `@BeforeEach` setup in the same file when possible.

**Files affected:**
- ADD: 4–5 new test files in `src/test/java/datingapp/app/api/`.
- DELETE: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`.

**Steps:**
1. Open `RestApiPhaseTwoRoutesTest.java` and list all `@Test`-annotated methods with a one-line summary of what each verifies.
2. Group methods by *fixture affinity* (which tests share setup/teardown), not just route name. A clean split keeps tests with the same fixture together.
3. Before any moves, confirm `RestApiTestFixture.Builder` (per L3.10) is stable enough to be shared across N test files. If 5 new test files each create their own fixture, this gets expensive — consider whether one or more of the new files can use a shared `@BeforeAll` fixture.
4. Create each new test file with:
   - Imports.
   - Fixture setup (preferably reusing `RestApiTestFixture`).
   - Moved test methods.
5. Run each new file's tests in isolation:
   ```powershell
   mvn --% -Dtest=RestApiNotificationStatsTest,RestApiMatchingSupportTest,RestApiProfileSnapshotTest,RestApiConversationFlowTest,RestApiSharedFailureMappingTest test
   ```
6. Delete `RestApiPhaseTwoRoutesTest.java`.

**Verification:**
```powershell
mvn test
```
Confirm the full count is unchanged (1852 tests minus or plus exactly the merged/split count delta).

**Risk:** Medium-high. Test count must remain identical. A tricky failure mode: copy-paste errors that swap two tests' setup. Mitigate by:
- Splitting in batches (one new test file at a time).
- Running the full Surefire output and comparing the test list before vs after.

---

## Final verification gate

```powershell
mvn spotless:apply verify
```

Expected: tests pass at 1852 total (give or take consolidations from L4.5).

Double-check the test count at the end:
```powershell
mvn test 2>&1 | rg "Tests run: \d+"
```

If the count drops by more than the L4.5 consolidation accounts for (which should be 0 net change — 2 tests moved, not removed), bisect.

---

## Commit message drafts — for the user

The user handles all git operations. **Do not run `git commit`.** After each phase below completes (verification gate green), draft the corresponding commit message in chat as plain text and **STOP**, asking the user whether to proceed. The user will commit on their own cadence and tell you to continue.

Phases group items that share helpers or risk profile:

### Phase A (conditional) — canonical helper additions (Item 0)

Run only if the pre-condition check found gaps. After Item 0:

```text
chore(test): add canonical helpers for shared test fixtures

Adds the canonical versions of the helpers used by later
Level 4 phases:
- TestStorages.Undos (from inner-class lift)
- TestEventBus.throwing()
- TestAchievementService.unlocked()

No production code changes. Existing inner-class duplicates
are kept until the next phase replaces their callers.
```

Skip this phase entirely if all helpers already exist.

### Phase B — small mechanical fixes (Items 1–2)

After Items 1 and 2:

```text
chore(test): import canonical TestUiThreadDispatcher; relocate TestJdbiMapping

- AsyncErrorRouterTest now imports
  UiAsyncTestSupport.TestUiThreadDispatcher; private duplicate removed
- TestJdbiMapping moved from src/test/java/datingapp/ to
  src/test/java/datingapp/storage/jdbi/ to match the production
  package it exercises
```

### Phase C — inner-class replacements (Items 3–6)

After Items 3, 4, 5, and 6 are all complete:

```text
chore(test): replace duplicate inner-class fixtures with canonical helpers

- InMemoryUserStorage inner classes in DailyPickServiceTest,
  AchievementServiceTest, ProfileCreateSelectTest -> TestStorages.Users
- ThrowingEventBus inner classes in MessagingUseCasesTest and
  SocialUseCasesTest -> TestEventBus.throwing()
- SingleAchievementService inner classes in StatsControllerTest and
  StatsViewModelTest -> TestAchievementService.unlocked()
- InMemoryUndoStorage inner classes in RestApiPhaseTwoRoutesTest,
  RestApiTestFixture, and LikerBrowserServiceTest -> TestStorages.Undos
```

If any of items 3–6 was skipped because the canonical helper was missing surface area, omit that bullet and tell the user.

### Phase D — EdgeCaseRegressionTest cleanup (Item 7)

After Item 7:

```text
chore(test): relocate EdgeCaseRegressionTest cases to domain homes

- rejectsBlankNames -> ValidationServiceTest
- duplicateMatchCreationDoesNotCrash -> MatchingServiceTest
- EdgeCaseRegressionTest.java deleted (was a catch-all for two
  drifted tests)
```

### Phase E — RestApiPhaseTwoRoutesTest split (Item 8)

After Item 8 (the largest single change in this level):

```text
chore(test): split RestApiPhaseTwoRoutesTest by route family

The 1119-line RestApiPhaseTwoRoutesTest is split into N route-
family test classes that group tests by fixture affinity:
- RestApiNotificationStatsTest
- RestApiMatchingSupportTest
- RestApiProfileSnapshotTest
- RestApiConversationFlowTest
- RestApiSharedFailureMappingTest

Test count is preserved at 1852 (delta from prior phases noted
in those commits). Original file deleted.
```

Adjust the file list to whatever the implementation actually produced.

If implementation diverged from any draft (e.g., the split produced 4 files instead of 5), edit the bullets to reflect what actually shipped before presenting to the user.

---

## Out of scope

- `InMemoryStandoutStorage` consolidation — only 1 occurrence; not duplication. (Audit-corrected from original plan.)
- Anything that changes production code. If a test fails because production code has a real bug, fix the bug in a separate non-Level-4 commit.
- Reorganizing test packages broadly — only the named moves (L4.6) are in scope.
- Tests for items added in L1–L3. Those land with their respective implementation commits.

## Definition of done

- [ ] All 8 items merged.
- [ ] Test count is 1852 (or higher after L4.5 reattribution; never lower).
- [ ] No new test fixture has fewer than 3 distinct test files using it (the same discipline gate as Level 2).
- [ ] `RestApiPhaseTwoRoutesTest.java` is deleted; the 5 new files together cover all 1119 lines of original test logic.
- [ ] `EdgeCaseRegressionTest.java` is deleted; both tests live in their domain-appropriate homes.
