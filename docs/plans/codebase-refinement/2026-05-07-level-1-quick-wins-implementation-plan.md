# Level 1 — Quick Wins Implementation Plan

> **Source:** [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) §Level 1
> **Created:** 2026-05-07
> **Scope:** 8 actionable items (4 of the original 12 invalidated against current code).
> **Risk profile:** Minimal. Almost entirely deletions of dead/no-op code or trivial deduplication.
> **Estimated net change:** ~5 files removed, ~0 files added, ~–200 LOC.

---

## Pre-flight

1. The user manages all git operations. **Do not run `git` commands** (no `status`, `add`, `commit`, `branch`, `checkout`, `mv`, `restore`, `reset`, `stash`, etc.).
2. Run the baseline build once so any failure later is attributable to this work:
   ```powershell
   mvn spotless:apply verify
   ```
   Expect: **1852 tests, 0 failures, 0 errors, 2 skipped** (per CLAUDE.md baseline).
3. Confirm with the user that the working tree is clean before starting; the user will branch and commit on their own cadence.

---

## Sequencing rationale

Deletions first (items 1–5) — they remove call sites and dead surface area, so later items touch less code. Constant extraction and the rename ride at the end because they're refactors, not removals.

| Order | Item                                                             | Reason for placement                            |
|-------|------------------------------------------------------------------|-------------------------------------------------|
| 1     | Delete `CheckDb.java` (L1.7)                                     | Zero coupling; warm-up step                     |
| 2     | Delete `AppEvent.MatchExpired` (L1.8)                            | Removes a subscription, no producers            |
| 3     | Delete `CandidateFinder` cache no-ops (L1.9)                     | Removes 4 call sites that pretend to invalidate |
| 4     | Delete `MatchingUseCases` no-op deprecated services (L1.10)      | Inner class removal, zero callers               |
| 5     | Remove unused `EnumSetUtil` overloads (L1.5)                     | API narrowing                                   |
| 6     | Merge `ModerationAuditLogger` into `ModerationAuditEvent` (L1.4) | One caller redirect                             |
| 7     | Extract `INVALID_EMAIL_FORMAT` constant (L1.6)                   | Pure refactor                                   |
| 8     | Rename `SwipeState.Session.MatchState` → `SessionState` (L1.2)   | Multi-file rename, do last                      |

---

## Item 1 — Delete `CheckDb.java` (L1.7)

**Goal:** Remove an orphaned `main()`-bearing class from the test tree that isn't a JUnit test and isn't referenced by any script.

**Files affected:**
- DELETE: [src/test/java/datingapp/tools/CheckDb.java](../../../src/test/java/datingapp/tools/CheckDb.java)

**Verification before delete:**
```powershell
# Confirm zero references in source and scripts.
rg -uu "CheckDb\b" --glob "!**/*.md" --glob "!**/CheckDbTest.java"
```
Expected: no matches. The unrelated `CheckDbTest.java` does not call `CheckDb`.

**Steps:**
1. Delete the file.
2. Run a fast sanity build:
   ```powershell
   mvn -pl . test-compile
   ```

**Verification after:**
```powershell
mvn -Dtest=CheckDbTest test
```
Should still pass — `CheckDbTest` is independent.

**Rollback:** Ask the user to restore the file via their git workflow if needed.

---

## Item 2 — Delete `AppEvent.MatchExpired` and its subscription (L1.8)

**Goal:** Remove an event type that has zero publishers. The subscription and handler in `MetricsEventHandler` are dead receive-side code.

**Files affected:**
- MODIFY: [src/main/java/datingapp/app/event/AppEvent.java](../../../src/main/java/datingapp/app/event/AppEvent.java) — remove the `MatchExpired` record (~line 60)
- MODIFY: [src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java](../../../src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java) — remove subscription at line 36 and handler `onMatchExpired` at line 75

**Verification before delete:**
```powershell
rg "MatchExpired" --glob "!**/*.md"
```
Expected: only the two files above plus possibly tests. Confirm no `new AppEvent.MatchExpired(` exists anywhere.

**Steps:**
1. In `AppEvent.java`, delete the `MatchExpired` record declaration. Preserve any leading comment that documents the deletion only if it provides historical context worth keeping (default: don't add comments).
2. In `MetricsEventHandler.java`:
   - Remove the `eventBus.subscribe(AppEvent.MatchExpired.class, ...)` line.
   - Remove the `onMatchExpired` handler method.
   - Remove any imports that become unused.
3. If any test references `MatchExpired`, delete that test or assertion (search results from Step 0 will tell you).

**Verification after:**
```powershell
mvn -Dtest=MetricsEventHandlerTest,AppEventTest test
```

**Risk:** Negligible. If anything *was* publishing this event off-the-tree (it isn't), the subscriber is removed cleanly; no NPE path opens up.

**Rollback:** Tell the user the change set; they'll revert via their git workflow.

---

## Item 3 — Delete `CandidateFinder` cache no-ops and their call sites (L1.9)

**Goal:** Remove `invalidateCacheFor(UUID)` and `clearCache()` — both have empty bodies labeled "No-op: candidate browsing is deliberately freshness-first" — plus the 4 call sites in `MatchingService` and `TrustSafetyService` that invoke them.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/matching/CandidateFinder.java](../../../src/main/java/datingapp/core/matching/CandidateFinder.java) — delete the two methods at ~lines 228–234
- MODIFY: [src/main/java/datingapp/core/matching/MatchingService.java](../../../src/main/java/datingapp/core/matching/MatchingService.java) — delete the two calls at ~lines 361–362
- MODIFY: [src/main/java/datingapp/core/matching/TrustSafetyService.java](../../../src/main/java/datingapp/core/matching/TrustSafetyService.java) — delete the two calls at ~lines 568–569

**Steps:**
1. Open `CandidateFinder.java`. Confirm method bodies are empty / comment-only. If you find any stateful caching that has been added since the audit (e.g., a Map that needs draining), STOP and re-evaluate.
2. Remove the two method declarations.
3. In `MatchingService.java` and `TrustSafetyService.java`, remove the now-unresolved calls. There should be no surrounding state that depended on them (verify by reading the surrounding 5–10 lines of each call site).
4. Compile.

**Verification after:**
```powershell
mvn -Dtest=CandidateFinderTest,MatchingServiceTest,TrustSafetyServiceTest test
```

**Risk:** None for behavior (removing no-ops). Only risk is if a method body had been quietly upgraded into real cache invalidation — Step 1 guards against that.

---

## Item 4 — Delete `MatchingUseCases` deprecated no-op services (L1.10)

**Goal:** Remove `NO_OP_DAILY_LIMIT_SERVICE` and `NO_OP_DAILY_PICK_SERVICE` inner static classes, both `@Deprecated` with zero call sites.

**Files affected:**
- MODIFY: [src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java](../../../src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java) — delete inner classes at ~lines 43–92

**Verification before delete:**
```powershell
rg "NO_OP_DAILY_LIMIT_SERVICE|NO_OP_DAILY_PICK_SERVICE" --glob "!**/*.md"
```
Expected: only declarations in `MatchingUseCases.java`.

**Steps:**
1. Delete both inner classes.
2. Confirm no `Builder` default-construction path silently used these constants (read the `Builder` and `recommendationService()` to verify the auto-fill logic — this is independently flagged in L5.24, so be careful not to wire the deletion to that fix).

**Verification after:**
```powershell
mvn -Dtest=MatchingUseCasesTest test
```

---

## Item 5 — Remove unused `EnumSetUtil` overloads (L1.5)

**Goal:** Delete the two `EnumSetUtil` overloads that have effectively no real callers, leaving only `safeCopy(Collection<E>, Class<E>)`.

**Audit summary:**
- `defensiveCopy(EnumSet<E>, Class<E>)` — 0 production callers.
- `safeCopy(Set<E>, Class<E>)` — 1 caller, which immediately delegates to `safeCopy(Collection<E>, Class<E>)`.
- `safeCopy(Collection<E>, Class<E>)` — 13 callers; this is the survivor.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/EnumSetUtil.java](../../../src/main/java/datingapp/core/EnumSetUtil.java) — delete `defensiveCopy(EnumSet, Class)` (~line 53) and `safeCopy(Set, Class)` (~line 40)
- MODIFY: callers of `safeCopy(Set, ...)` if the compiler complains — none expected, since the existing single caller already accepted a `Collection`-typed argument

**Steps:**
1. Confirm zero callers of `defensiveCopy`:
   ```powershell
   rg "EnumSetUtil\.defensiveCopy"
   ```
2. Find the single caller of `safeCopy(Set, Class)` and check whether removing the overload still type-checks (it should, because `Set extends Collection`).
3. Delete both methods.
4. Compile.

**Verification after:**
```powershell
mvn -Dtest=EnumSetUtilTest test
mvn test-compile
```

**Risk:** Low. Java's overload resolution will pick the `Collection` overload for any `Set` argument once the `Set` overload is gone — no behavior change.

---

## Item 6 — Merge `ModerationAuditLogger` into `ModerationAuditEvent.log(...)` (L1.4)

**Goal:** Eliminate a single-method logger class by promoting the operation to a static method on the event itself, then deleting the now-empty wrapper.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/matching/ModerationAuditEvent.java](../../../src/main/java/datingapp/core/matching/ModerationAuditEvent.java) — add `public static void log(ModerationAuditEvent event)`
- DELETE: [src/main/java/datingapp/core/matching/ModerationAuditLogger.java](../../../src/main/java/datingapp/core/matching/ModerationAuditLogger.java)
- MODIFY: [src/main/java/datingapp/core/matching/TrustSafetyService.java](../../../src/main/java/datingapp/core/matching/TrustSafetyService.java) — line ~622, redirect the single call from `auditLogger.log(event)` to `ModerationAuditEvent.log(event)`. If `auditLogger` is a field/parameter elsewhere, remove it.

**Steps:**
1. Read `ModerationAuditLogger.java` (32 lines) and copy the `log(ModerationAuditEvent)` body into a `public static void log(...)` method on `ModerationAuditEvent`.
2. In `TrustSafetyService.java`:
   - Remove the `ModerationAuditLogger` field/dependency injection.
   - Replace the call site at line ~622 with `ModerationAuditEvent.log(event)`.
   - Remove constructor parameter and update all callers (likely `ServiceRegistry`).
3. Search for any other `ModerationAuditLogger` references:
   ```powershell
   rg "ModerationAuditLogger"
   ```
4. Update `ServiceRegistry` to no longer construct or pass `ModerationAuditLogger`.
5. Delete `ModerationAuditLogger.java`.

**Verification after:**
```powershell
mvn -Dtest=TrustSafetyServiceTest,ModerationAuditEventTest test
```

**Risk:** Low. The only risk is a missed wiring site in `ServiceRegistry` or a test fixture that constructs `TrustSafetyService` directly with a `ModerationAuditLogger`. Step 3 catches both.

**Notes:**
- A static method on the event is appropriate here because the event already carries all the data the logger reads. There is no injectable state worth preserving.
- If `ModerationAuditLogger` had a `Logger logger` field with a non-trivial name (e.g., for log routing), preserve that exact `LoggerFactory.getLogger(...)` argument when porting the static method, otherwise log filters in `logback*.xml` will silently break.

---

## Item 7 — Extract `INVALID_EMAIL_FORMAT` constant in `TextNormalization` (L1.6)

**Goal:** Replace 5 hardcoded `"Invalid email format"` string literals with a `private static final String` constant.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/TextNormalization.java](../../../src/main/java/datingapp/core/TextNormalization.java)
  - ADD: `private static final String INVALID_EMAIL_FORMAT = "Invalid email format";` near the other private constants (line ~32)
  - REPLACE the literal at lines 58, 62, 70, 77, 80

**Steps:**
1. Open the file. Verify the literal occurs at exactly the 5 lines listed (audit confirmed):
   ```powershell
   rg -n '"Invalid email format"' src/main/java/datingapp/core/TextNormalization.java
   ```
2. Add the constant with the other private constants in the class header.
3. Replace each of the 5 literal occurrences with `INVALID_EMAIL_FORMAT`. Use Edit's `replace_all` with the unique string `"Invalid email format"` if and only if the literal does not appear in any other context (e.g., in a comment).
4. Run Spotless formatter to ensure consistent style:
   ```powershell
   mvn spotless:apply
   ```

**Verification after:**
```powershell
mvn -Dtest=TextNormalizationTest test
```

**Risk:** None. Pure compile-time constant extraction. Tests asserting exact error-message strings continue to match.

---

## Item 8 — Rename `SwipeState.Session.MatchState` → `SessionState` (L1.2)

**Goal:** Eliminate the type-name collision with `Match.MatchState`. The two enums live in different packages but share the simple name, which complicates IDE navigation and code review.

**Files affected (all reference `SwipeState.Session.MatchState` or import it):**

- MODIFY: [src/main/java/datingapp/core/metrics/SwipeState.java](../../../src/main/java/datingapp/core/metrics/SwipeState.java) — declaration at lines 31–34
- MODIFY: [src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java](../../../src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java)
- MODIFY: [src/test/java/datingapp/core/metrics/SwipeSessionTest.java](../../../src/test/java/datingapp/core/metrics/SwipeSessionTest.java)
- MODIFY: [src/test/java/datingapp/core/metrics/SessionServiceTest.java](../../../src/test/java/datingapp/core/metrics/SessionServiceTest.java)
- MODIFY: [src/test/java/datingapp/storage/jdbi/JdbiMetricsStorageTest.java](../../../src/test/java/datingapp/storage/jdbi/JdbiMetricsStorageTest.java)
- MODIFY: [src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java](../../../src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java)
- MODIFY: [src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java](../../../src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java)
- MODIFY: [src/test/java/datingapp/ui/viewmodel/UiDataAdaptersPresenceTest.java](../../../src/test/java/datingapp/ui/viewmodel/UiDataAdaptersPresenceTest.java)

**Steps:**
1. In `SwipeState.java`, rename the inner enum `MatchState` to `SessionState`. If there is a serialization concern (database column stores enum names), check `JdbiMetricsStorage` for column-binding code that does `MatchState.valueOf(...)`. The audit confirms this is a Java-only enum, not persisted by name to the DB schema — but verify before renaming.
2. If a DB persistence path *does* use the enum name, this item is **not** a Level 1 quick-win and should be deferred to a migration-aware change. Otherwise continue.
3. Run a project-wide find-and-replace on the qualified name first (most precise), then the unqualified one:
   ```powershell
   # Step 3a: qualified references
   ast-grep --pattern 'SwipeState.Session.MatchState' --rewrite 'SwipeState.Session.SessionState'
   # Step 3b: any direct imports or short-name usages inside SwipeState.java
   ```
   If `ast-grep` is not available, use the IDE's Refactor → Rename on the enum declaration; it will update all 7 callers atomically.
4. Verify the test files compile by running their packages:
   ```powershell
   mvn -Dtest=SwipeSessionTest,SessionServiceTest,JdbiMetricsStorageTest,StatsViewModelTest,UiDataAdaptersPresenceTest test
   ```

**Verification after:**
```powershell
rg "SwipeState.Session.MatchState"
```
Expected: zero matches in source.

**Risk:** Medium for a Level 1 item — it touches 7 files. The mitigation is using IDE-driven rename rather than text replacement, which avoids accidental partial matches.

**Caution:** The `Match.MatchState` enum is unaffected. Do not touch it.

---

## Final verification gate

After all 8 items are complete:

```powershell
mvn spotless:apply verify
```

Expected: same baseline as Pre-flight — **1852 tests, 0 failures, 0 errors, 2 skipped** (counts may shift slightly if any tests were keyed to the deleted `MatchExpired` event or the deleted no-op classes; expected delta is ≤ 5 tests removed and zero new failures).

If any test count shifts unexpectedly, bisect by reverting each item in reverse order until the suite is clean.

---

## Commit message — draft for the user

The user will commit this entire level as a **single commit**. Once all 8 items are complete and the final verification gate passes, draft the commit message below and present it as plain text in chat (do not run `git commit`). The user will copy/paste or edit as desired.

**Draft commit message** (subject + body, ~72-char wrap):

```text
refactor: level-1 quick-win cleanups (deletions, rename, constant extract)

Removes dead/no-op code and one type-name collision flagged in
CODEBASE_REFINEMENT_PLAN.md §Level 1.

- Delete orphaned datingapp.tools.CheckDb test tool (no callers)
- Drop AppEvent.MatchExpired and its MetricsEventHandler subscription
  (no publishers in the codebase)
- Drop CandidateFinder.invalidateCacheFor / clearCache no-ops and
  the four call sites in MatchingService and TrustSafetyService
- Remove deprecated NO_OP_DAILY_LIMIT_SERVICE and
  NO_OP_DAILY_PICK_SERVICE inner classes from MatchingUseCases
- Trim unused EnumSetUtil overloads (defensiveCopy, safeCopy(Set))
- Fold ModerationAuditLogger.log into ModerationAuditEvent.log;
  delete ModerationAuditLogger
- Extract TextNormalization.INVALID_EMAIL_FORMAT constant for the
  five literal occurrences
- Rename SwipeState.Session.MatchState to SessionState to remove
  the type-name collision with Match.MatchState

Net change: ~5 files removed, ~–200 LOC. No production behavior change.
```

If implementation work diverged from this draft (e.g., an item was deferred or expanded), update the bullets to match the actual diff before presenting to the user.

---

## Out of scope

- Renaming `Match.MatchState` (it is the canonical name in its own domain).
- Touching `AppConfigValidator.validateMatchingBehaviorFlags()` (intentional no-op hook; see appendix).
- Anything in `ProfileHandler.copyForProfileEditing()` (already removed; see appendix).
- The `CandidateFinder.GeoUtils` promotion (no such inner class; see appendix).

## Definition of done

- [ ] All 8 items merged.
- [ ] `mvn spotless:apply verify` passes.
- [ ] [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) Level 1 items are crossed off (or this plan moved from `unimplemented/` to `implemented/`).
- [ ] No new TODO comments left behind.
