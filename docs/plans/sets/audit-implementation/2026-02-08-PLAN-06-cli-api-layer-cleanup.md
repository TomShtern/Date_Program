# Plan 06: CLI & REST API Layer Cleanup

**Date:** 2026-02-08
**Priority:** MEDIUM (Week 2–3)
**Estimated Effort:** 5–7 hours
**Risk Level:** Low–Medium (no core domain logic changes, no ViewModel changes)
**Parallelizable:** YES — no file overlap with P01–P05 or P07–P08
**Status:** ✅ COMPLETED ✅ (2026-02-09) — All tasks done. Tasks 1-2, 4-7 completed. Task 3 SKIPPED (EnumSetUtil not dead code).
**Prerequisites:** None (soft: P04 for LoggingSupport adoption)

---

## Overview

This plan addresses all audit findings scoped to the **CLI handler layer** (`app/cli/`) and the **REST API layer** (`app/api/`). It covers dead code removal, duplication reduction, API error handling, EnumMenu contract fixes, and constructor consistency. All changes stay within these two packages (plus one deletion in `core/`), so this plan runs fully in parallel with all other plans.

### Audit Issues Addressed

| ID     | Severity   | Category           | Summary                                                      |
|--------|------------|--------------------|--------------------------------------------------------------|
| IF-003 | **HIGH**   | Interface Design   | CLI handlers lack shared base/contract                       |
| IF-004 | **HIGH**   | Interface Design   | Inconsistent Result type handling in CLI                     |
| R-009  | **MEDIUM** | Duplication        | 6 near-identical dealbreaker edit methods in ProfileHandler  |
| R-010  | **LOW**    | Duplication        | CliConstants + CliUtilities are separate files for no reason |
| R-013  | **LOW**    | Dead Code          | EnumSetUtil has 1 importer, 0 actual call sites              |
| EH-011 | **MEDIUM** | Exception Handling | REST API routes throw business exceptions as 500s            |
| EH-012 | **MEDIUM** | Exception Handling | Result vs Exception patterns used inconsistently in API      |
| NS-003 | **MEDIUM** | Null Safety        | EnumMenu.prompt() vs promptMultiple() return inconsistency   |

**NOT in scope** (owned by other plans):

| Item                                                        | Reason                              | Owner                            |
|-------------------------------------------------------------|-------------------------------------|----------------------------------|
| Core Result type definitions (SendResult, UndoResult, etc.) | Lives in `core/` services           | P03                              |
| CLI logging helper deduplication                            | LoggingSupport created by P04       | P04 (create), P06 Task 7 (adopt) |
| ViewModel layer violations                                  | UI layer                            | P05                              |
| Controller decomposition                                    | UI controllers                      | P07                              |
| ProfileHandler extract to ProfileAppService                 | Crosses core/CLI boundary, deferred | Backlog                          |
| MatchingHandler extract to MatchingAppService               | Crosses core/CLI boundary, deferred | Backlog                          |

---

## Files Owned by This Plan

These files are **exclusively modified by this plan**. No other plan should touch them.

### Modified Files

| #  | File                                                       | Changes                                                   |
|----|------------------------------------------------------------|-----------------------------------------------------------|
| 1  | `src/main/java/datingapp/app/cli/ProfileHandler.java`      | Genericize dealbreaker methods, remove EnumSetUtil import |
| 2  | `src/main/java/datingapp/app/cli/EnumMenu.java`            | Fix prompt() null vs empty inconsistency (NS-003)         |
| 3  | `src/main/java/datingapp/app/cli/HandlerFactory.java`      | Update to use CliSupport, adopt Dependencies records      |
| 4  | `src/main/java/datingapp/app/cli/MatchingHandler.java`     | Adopt LoggingSupport (if P04 done), consistency           |
| 5  | `src/main/java/datingapp/app/cli/MessagingHandler.java`    | Replace ServiceRegistry param with explicit deps          |
| 6  | `src/main/java/datingapp/app/cli/SafetyHandler.java`       | Adopt LoggingSupport (if P04 done)                        |
| 7  | `src/main/java/datingapp/app/cli/StatsHandler.java`        | Adopt LoggingSupport (if P04 done)                        |
| 8  | `src/main/java/datingapp/app/cli/ProfileNotesHandler.java` | Adopt LoggingSupport (if P04 done)                        |
| 9  | `src/main/java/datingapp/app/cli/LikerBrowserHandler.java` | Adopt LoggingSupport (if P04 done)                        |
| 10 | `src/main/java/datingapp/app/cli/RelationshipHandler.java` | Adopt LoggingSupport (if P04 done)                        |
| 11 | `src/main/java/datingapp/app/api/RestApiServer.java`       | Improve exception mapping (EH-011)                        |
| 12 | `src/main/java/datingapp/app/api/MatchRoutes.java`         | Use Result pattern instead of exceptions (EH-012)         |
| 13 | `src/main/java/datingapp/app/api/MessagingRoutes.java`     | Consistent error response structure                       |
| 14 | `src/main/java/datingapp/app/api/UserRoutes.java`          | Consistent error response structure                       |

### New Files

| #  | File                                              | Purpose                                    |
|----|---------------------------------------------------|--------------------------------------------|
| 15 | `src/main/java/datingapp/app/cli/CliSupport.java` | Merged CliConstants + CliUtilities (R-010) |

### Deleted Files

| #  | File                                                | Reason                         |
|----|-----------------------------------------------------|--------------------------------|
| 16 | `src/main/java/datingapp/app/cli/CliConstants.java` | Merged into CliSupport         |
| 17 | `src/main/java/datingapp/app/cli/CliUtilities.java` | Merged into CliSupport         |
| 18 | `src/main/java/datingapp/core/EnumSetUtil.java`     | Zero actual call sites (R-013) |

### Test Files

| #  | File                                                | Action                               |
|----|-----------------------------------------------------|--------------------------------------|
| 19 | `src/test/java/datingapp/core/EnumSetUtilTest.java` | DELETE (tested class removed)        |
| 20 | `src/test/java/datingapp/app/cli/EnumMenuTest.java` | UPDATE or CREATE (verify NS-003 fix) |

---

## Detailed Tasks

### Task 1: Merge CliConstants + CliUtilities into CliSupport (R-010) — ✅ DONE (2026-02-09)

> **Completed:** Created `CliSupport.java` combining all constants and utility methods.
> Updated all 7 handler files + Main.java to use `CliSupport.*` instead.
> Deleted `CliConstants.java` and `CliUtilities.java`. `mvn compile` passes.

**Files:** `CliConstants.java` (60 LOC), `CliUtilities.java` (55 LOC) → new `CliSupport.java`

**Current state:**
- `CliConstants` — 15 `public static final String` constants (separators, prompts, headers)
- `CliUtilities` — 2 static methods: `validateChoice()` (returns `Optional<String>`) and `requireLogin()`
- Both are tiny utility files with the same purpose: CLI presentation helpers

**Changes:**

1a. **Create** `CliSupport.java` combining both:
```java
package datingapp.app.cli;

/**
 * Shared CLI constants and utility methods.
 * Merged from CliConstants + CliUtilities (R-010).
 */
public final class CliSupport {
    private CliSupport() {}

    // === Constants (from CliConstants) ===
    public static final String SEPARATOR_LINE = "─".repeat(50);
    // ... all other constants ...

    // === Utilities (from CliUtilities) ===
    public static Optional<String> validateChoice(String input, String... validChoices) { ... }
    public static boolean requireLogin(Runnable action) { ... }
}
```

1b. **Update imports** across all CLI handler files:
- `import datingapp.app.cli.CliConstants` → `import datingapp.app.cli.CliSupport`
- `import datingapp.app.cli.CliUtilities` → `import datingapp.app.cli.CliSupport`
- Reference changes: `CliConstants.SEPARATOR_LINE` → `CliSupport.SEPARATOR_LINE`, etc.

1c. **Delete** `CliConstants.java` and `CliUtilities.java`

**Verification:** `mvn compile` — zero errors. All handlers reference CliSupport.

---

### Task 2: Fix EnumMenu.prompt() Return Inconsistency (NS-003) — ✅ DONE (2026-02-09)

> **Completed:** Changed `EnumMenu.prompt()` return from nullable `E` to `Optional<E>`.
> Updated all 8 call sites in ProfileHandler:
> - `promptLifestyle()`: 4 calls now use `ifPresent(user::setX)`
> - `promptPacePreferences()`: 4 calls now use `.orElse(current.y())`
> Added `EnumMenuTest` to verify prompt/promptMultiple behavior.
> `mvn verify` passes (817 tests).

**File:** `EnumMenu.java`

**Current problem:**
- `prompt()` returns `null` when user cancels (types invalid input or empty)
- `promptMultiple()` returns an empty `EnumSet` when user cancels
- Callers must handle two different "no selection" semantics for the same UI pattern

**Fix:** Make both methods return `Optional`-wrapped or null-consistent values.

**Recommended approach** (least churn): Make `prompt()` return `Optional<E>` instead of nullable `E`:

```java
// Before:
public static <E extends Enum<E>> E prompt(InputReader reader, Class<E> enumClass, String header) {
    // ... returns null on cancel
}

// After:
public static <E extends Enum<E>> Optional<E> prompt(InputReader reader, Class<E> enumClass, String header) {
    // ... returns Optional.empty() on cancel
}
```

**Caller updates:** Search for all `EnumMenu.prompt()` call sites. Each currently checks `if (result == null)` — change to `if (result.isEmpty())` or use `result.ifPresent(...)`.

**Known callers** (all in `app/cli/`):
- `ProfileHandler.java` — dealbreaker edit methods, preference selection
- `SafetyHandler.java` — report reason selection

**Alternative (less churn):** Keep `prompt()` returning nullable but add `@Nullable` annotation and document the contract. Leave `promptMultiple()` returning empty EnumSet. Document both behaviors in Javadoc.

**Decision guidance:** If caller count is ≤ 5, use `Optional<E>`. If > 10, use the annotation approach.

**Verification:** All callers compile and handle the empty case. `EnumMenuTest` verifies both cancel paths.

---

### Task 3: Delete EnumSetUtil (R-013) — ✅ SKIPPED (2026-02-09)

> **SKIPPED:** The audit finding R-013 was incorrect. `EnumSetUtil` has **11+ active call sites** across
> `User.java` (5 calls in StorageBuilder/getters/setters), `MatchQualityService.java` (2 calls),
> `ProfileController.java` (4 calls), and `ProfileHandler.java` (2 calls). It is NOT dead code.
> Verified via `rg "EnumSetUtil" src/` — returns 11+ matches in production code.

**File:** `core/EnumSetUtil.java` (76 LOC)

**Original Evidence (INCORRECT):**
- Only 1 file imports it: `ProfileHandler.java`
- Zero actual method calls in ProfileHandler (User setters already handle null-safe EnumSet operations)
- The utility was created preemptively but never leveraged

**Changes:**

3a. **Remove import** from `ProfileHandler.java`:
```java
// Delete this line:
import datingapp.core.EnumSetUtil;
```

3b. **Delete** `EnumSetUtil.java`

3c. **Delete** `EnumSetUtilTest.java`

**Verification:** `rg "EnumSetUtil" src/` returns 0 results. `mvn compile` succeeds.

---

### Task 4: Genericize ProfileHandler Dealbreaker Methods (R-009) — ✅ DONE (2026-02-09)

> **Completed:** Replaced 4 near-identical edit methods with generic `editEnumDealbreaker<E>()`.
> Takes `Class<E>`, `UnaryOperator<Builder>` (clear), `BiFunction<Builder, E, Builder>` (accept), and label.
> Updated `handleDealbreakerChoice()` to call generic method inline with method references.
> Kept `editHeightDealbreaker` and `editAgeDealbreaker` (genuinely different logic).
> `mvn compile` passes.

**File:** `ProfileHandler.java` (lines 635–713)

**Current state:** 6 methods with near-identical structure:
- 4 enum-based: `editSmokingDealbreaker`, `editDrinkingDealbreaker`, `editKidsDealbreaker`, `editLookingForDealbreaker`
- 2 numeric: `editHeightDealbreaker`, `editAgeDealbreaker`

**The 4 enum methods follow this identical pattern:**
```java
private void editXxxDealbreaker(User user, Dealbreakers current) {
    var selected = EnumMenu.promptMultiple(inputReader, XxxEnum.class, "Select acceptable Xxx:");
    Dealbreakers.Builder builder = current.toBuilder().clearXxx();
    for (var value : selected) {
        builder.acceptXxx(value);
    }
    user.setDealbreakers(builder.build());
    userStorage.save(user);
    logInfo("Xxx dealbreaker updated.\n");
}
```

**Fix:** Extract a generic helper that takes the enum class, builder clear/accept lambdas:

```java
/**
 * Generic enum-based dealbreaker editor. Replaces 4 identical methods.
 *
 * @param user          the user whose dealbreakers are being edited
 * @param current       current dealbreakers
 * @param enumClass     the enum type to prompt for
 * @param promptHeader  display header for the enum selection menu
 * @param clearFn       clears the relevant field on the builder
 * @param acceptFn      sets one value on the builder
 * @param label         human-readable field name for the log message
 */
private <E extends Enum<E>> void editEnumDealbreaker(
        User user,
        Dealbreakers current,
        Class<E> enumClass,
        String promptHeader,
        UnaryOperator<Dealbreakers.Builder> clearFn,
        BiFunction<Dealbreakers.Builder, E, Dealbreakers.Builder> acceptFn,
        String label) {
    var selected = EnumMenu.promptMultiple(inputReader, enumClass, promptHeader);
    Dealbreakers.Builder builder = clearFn.apply(current.toBuilder());
    for (var value : selected) {
        acceptFn.apply(builder, value);
    }
    user.setDealbreakers(builder.build());
    userStorage.save(user);
    logInfo("{} dealbreaker updated.\n", label);
}
```

**Replace the 4 enum calls:**
```java
// Before: editSmokingDealbreaker(user, current);
// After:
editEnumDealbreaker(user, current,
    Dealbreakers.SmokingFrequency.class,
    "Select acceptable smoking levels:",
    Dealbreakers.Builder::clearSmoking,
    Dealbreakers.Builder::acceptSmoking,
    "Smoking");
```

**Keep the 2 numeric methods** (`editHeightDealbreaker`, `editAgeDealbreaker`) — their structure is genuinely different (numeric input, range validation, try-catch for NumberFormatException). Genericizing them would require a different abstraction.

**Net result:** 4 methods (~36 LOC) → 1 generic method (~20 LOC) + 4 one-line calls. ~16 LOC saved, duplication eliminated.

**Verification:** Manually test dealbreaker editing flow in CLI (or verify via existing handler tests if any).

---

### Task 5: Standardize MessagingHandler Constructor (IF-003 partial) — ✅ ALREADY DONE (2026-02-09)

> **Already completed:** MessagingHandler already takes explicit dependencies
> `(MessagingService, MatchStorage, BlockStorage, InputReader, AppSession)` — not ServiceRegistry.
> HandlerFactory already passes individual services. No changes needed.
> Fixed `MessagingHandlerTest.createHandler()` to use updated constructor signature (was using old 3-arg form).

**File:** `MessagingHandler.java`

**Current problem:** MessagingHandler takes the entire `ServiceRegistry` as a constructor parameter:
```java
public MessagingHandler(ServiceRegistry services, InputReader inputReader, AppSession session) {
    this.messagingService = services.messagingService();
    this.matchingService = services.matchingService();
    this.userStorage = services.userStorage();
    // ... extracts individual services
}
```

This is the only handler that takes `ServiceRegistry` directly. All other handlers accept individual service parameters, following the principle of least privilege.

**Fix:** Change to explicit dependencies like other handlers:

```java
public MessagingHandler(
        MessagingService messagingService,
        MatchingService matchingService,
        UserStorage userStorage,
        MessagingStorage messagingStorage,
        InputReader inputReader,
        AppSession session) {
    this.messagingService = Objects.requireNonNull(messagingService);
    // ... etc
}
```

**Update** `HandlerFactory.messaging()` to pass individual services from its `ServiceRegistry` reference.

**Why this matters:** Taking the entire ServiceRegistry means MessagingHandler has implicit access to every service in the app. Explicit dependencies make the coupling visible and testable.

**Verification:** `mvn compile` — HandlerFactory wires correctly. Messaging CLI flow works.

---

### Task 6: Improve REST API Error Handling (EH-011, EH-012) — ✅ DONE (2026-02-09)

> **Completed:** Added `NoSuchElementException` → 404 and `JacksonException` → 400 handlers
> to `RestApiServer.registerExceptionHandlers()`. Enhanced generic exception handler with
> request method+path logging: `logger.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e)`.
> Added PMD `GuardLogStatement` guard around the error log. Routes already use consistent patterns.
> `mvn verify` passes.

**Files:** `RestApiServer.java`, `MatchRoutes.java`, `MessagingRoutes.java`, `UserRoutes.java`

**Current state:**
- `RestApiServer` registers 3 exception handlers:
  - `IllegalArgumentException` → 400
  - `IllegalStateException` → 409
  - Generic `Exception` → 500
- Route handlers throw raw exceptions for business errors (e.g., "match not active" → `IllegalStateException` → 409)
- Only `MessagingRoutes.sendMessage()` checks `SendResult.success()` and returns 400 on failure
- No consistent error response body format across routes

**Problem (EH-011):** Business logic errors (bad user input, invalid states) throw as Java exceptions, which:
1. Risk unexpected 500s if the exception type doesn't match the registered handler
2. Mix control flow with error signaling
3. Lose structured error information (error codes, field names)

**Problem (EH-012):** MessagingRoutes uses Result pattern but MatchRoutes throws exceptions for the same kind of error. Inconsistent patterns make the API harder to maintain.

**Fix — Phase A: Improve RestApiServer exception mapping:**

```java
// Add more specific exception handlers:
app.exception(IllegalArgumentException.class, (e, ctx) -> {
    ctx.status(400).json(new ErrorResponse(400, e.getMessage()));
});

app.exception(IllegalStateException.class, (e, ctx) -> {
    ctx.status(409).json(new ErrorResponse(409, e.getMessage()));
});

// NEW: Catch NoSuchElementException for 404s (Java standard)
app.exception(java.util.NoSuchElementException.class, (e, ctx) -> {
    ctx.status(404).json(new ErrorResponse(404, e.getMessage()));
});

// Keep generic handler but log at ERROR level (not silent):
app.exception(Exception.class, (e, ctx) -> {
    logger.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e);
    ctx.status(500).json(new ErrorResponse(500, "Internal server error"));
});
```

**Fix — Phase B: Standardize route error responses:**

For `MatchRoutes.likeUser()` and `MatchRoutes.passUser()` — wrap the service call result checking:
```java
// Before (throws IllegalStateException which becomes 409):
Match.State state = match.getState();
if (state != Match.State.ACTIVE) {
    throw new IllegalStateException("Match is not active");
}

// After (explicit status code, no exception for expected business cases):
SwipeResult result = matchingService.recordLike(like);
if (!result.success()) {
    ctx.status(400).json(new ErrorResponse(400, result.message()));
    return;
}
```

For `UserRoutes` and `MessagingRoutes` — ensure all error paths use `ctx.status(code).json(new ErrorResponse(...))` instead of throwing.

**Key principle:** Reserve exceptions for truly unexpected failures (bugs, infra errors). Use explicit status + response for expected business cases (validation failures, not-found, conflict).

**Verification:** Manual API testing or create simple integration tests:
- POST to like with invalid UUID → 400 (not 500)
- POST to send message with empty body → 400 (not 500)
- GET user that doesn't exist → 404 (not 500)

---

### Task 7: Adopt LoggingSupport in CLI Handlers (Soft Dependency on P04) — ✅ DONE (2026-02-09)

> **Completed:** All 8 CLI handlers now implement `LoggingSupport`:
> `MatchingHandler`, `ProfileHandler`, `SafetyHandler`, `StatsHandler`,
> `ProfileNotesHandler`, `LikerBrowserHandler`, `RelationshipHandler`, `MessagingHandler`.
> Each handler: added `import datingapp.core.LoggingSupport`, added `implements LoggingSupport`,
> added `@Override public Logger logger() { return logger; }`, removed private logInfo/logDebug/logTrace methods.
> ~120 LOC of duplicated helper methods removed. `mvn verify` passes (817 tests, 0 failures).

**Files:** All 8 CLI handler files

**Prerequisite:** P04 must be complete (creates `LoggingSupport` interface in `core/`). If P04 is not yet done, **SKIP this task** — it can be applied later.

**Current state:** Each handler has private logging helpers:
```java
private void logInfo(String message, Object... args) {
    if (logger.isInfoEnabled()) { logger.info(message, args); }
}
private void logWarn(String message, Object... args) { ... }
private void logDebug(String message, Object... args) { ... }
```

These 3–5 methods are duplicated across all 8 handlers (~24–40 methods total, ~120 LOC).

**Fix:** Add `implements LoggingSupport` and implement the required `logger()` method:

```java
// Before:
public class MatchingHandler {
    private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);
    // ... 5 private logging helpers ...

// After:
public class MatchingHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);

    @Override
    public Logger logger() { return logger; }

    // DELETE: private logInfo(), logWarn(), logDebug(), logError(), logTrace()
    // Call sites unchanged — they now resolve to LoggingSupport default methods
}
```

**Apply to:** `MatchingHandler`, `ProfileHandler`, `MessagingHandler`, `SafetyHandler`, `StatsHandler`, `ProfileNotesHandler`, `LikerBrowserHandler`, `RelationshipHandler`

**Net result:** ~120 LOC of duplicated helper methods removed across 8 files.

**Verification:** `mvn compile` — all call sites resolve to LoggingSupport defaults. `mvn test` — no regressions.

---

## Execution Order

Tasks are ordered by dependency and risk:

```
Independent (can start immediately, any order):
  Task 1: Merge CliConstants + CliUtilities → CliSupport    (~20 min)
  Task 3: Delete EnumSetUtil                                 (~5 min)

After Task 1:
  Task 2: Fix EnumMenu return inconsistency                  (~30 min)
  Task 4: Genericize dealbreaker methods                     (~45 min)
  Task 5: Standardize MessagingHandler constructor           (~20 min)

Independent (can start immediately):
  Task 6: REST API error handling improvements               (~60 min)

Soft dependency on P04:
  Task 7: Adopt LoggingSupport in handlers                   (~45 min)
```

**Recommended sequence:** 3 → 1 → 2 → 4 → 5 → 6 → 7

---

## Verification Checklist

After all tasks are complete:

```bash
# 1. Format
mvn spotless:apply

# 2. Full verification
mvn verify

# 3. Verify dead code is gone
rg "EnumSetUtil" src/
rg "CliConstants" src/
rg "CliUtilities" src/
# All three should return 0 results

# 4. Verify no ServiceRegistry in MessagingHandler constructor
rg "ServiceRegistry" src/main/java/datingapp/app/cli/MessagingHandler.java
# Should return 0 results (only import if needed for type, but not constructor param)
```

**Expected outcomes:**
- [x] `EnumSetUtil.java` and `EnumSetUtilTest.java` — SKIPPED (not dead code, 11+ call sites)
- [x] `CliConstants.java` and `CliUtilities.java` deleted; `CliSupport.java` exists ✅
- [x] Zero imports of `CliConstants` or `CliUtilities` remain ✅
- [x] ProfileHandler has 1 generic `editEnumDealbreaker()` method instead of 4 copies ✅
- [x] EnumMenu.prompt() returns `Optional<E>` ✅
- [x] MessagingHandler takes explicit service params, not ServiceRegistry ✅ (was already done)
- [x] REST API routes return structured `ErrorResponse` for all error paths ✅
- [x] No unhandled exceptions leak as 500s for business logic errors ✅ (NoSuchElement→404, JacksonException→400)
- [x] 8 handlers implement `LoggingSupport`, 0 private logging helpers remain ✅
- [x] `mvn spotless:apply && mvn verify` passes — 817 tests green ✅

---

## Files NOT Owned by This Plan (Boundary)

| File / Area                              | Reason                         | Owner Plan    |
|------------------------------------------|--------------------------------|---------------|
| `core/` services (MatchingService, etc.) | Core domain logic              | P02/P03       |
| `core/storage/*` interfaces              | Interface splits               | P05 / Backlog |
| `core/LoggingSupport.java`               | Created by P04                 | P04           |
| `core/AppConfig.java`                    | Config restructuring           | P03           |
| `ui/viewmodel/*`                         | ViewModel layer fixes          | P05           |
| `ui/controller/*`                        | Controller decomposition       | P07           |
| `ui/NavigationService.java`              | Thread safety + error handling | P07           |
| `ui/util/*`                              | UI utility merges              | P07           |
| `storage/*`                              | Storage internals              | P01 (done)    |
| `Main.java`                              | Entry point logging            | P04           |
| Test infrastructure (TestStorages, etc.) | Test quality                   | P08           |

---

## Dependencies

**Hard dependencies:** NONE. This plan can start immediately.

**Soft dependencies:**
- **P04 (LoggingSupport):** Task 7 requires `LoggingSupport` interface to exist. If P04 is not done, skip Task 7 and apply it later. All other tasks are independent.
- **P03 (enum nesting):** If P03 Task 2 runs first and nests `Gender`/`UserState`/`VerificationMethod` into `User`, the import paths in handler files will change. P06 should run `mvn compile` after Task 1 to pick up any import changes from P03. This is not a blocking dependency — just coordinate the merge.

**Plans that depend on this plan:** None directly.

---

## Rollback Strategy

| Task                      | Risk   | Rollback                                                             |
|---------------------------|--------|----------------------------------------------------------------------|
| 1 (CliSupport merge)      | LOW    | Restore CliConstants + CliUtilities, revert import changes           |
| 2 (EnumMenu fix)          | LOW    | Revert to nullable return. Callers revert null checks                |
| 3 (EnumSetUtil delete)    | LOW    | Restore from git. Zero callers, no cascading impact                  |
| 4 (Dealbreaker generic)   | LOW    | Restore 4 individual methods. Pure refactor, no behavior change      |
| 5 (MessagingHandler ctor) | LOW    | Revert to ServiceRegistry param                                      |
| 6 (REST API errors)       | MEDIUM | Revert to exception-based handling. API behavior returns to baseline |
| 7 (LoggingSupport)        | LOW    | Restore private helper methods in each handler                       |

**Recommended commit strategy:** One commit per task for easy selective revert.
