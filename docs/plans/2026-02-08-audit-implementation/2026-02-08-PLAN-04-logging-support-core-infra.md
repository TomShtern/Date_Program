# Plan 04: Logging Support & Level Consistency (Core + Infra)

**Date:** 2026-02-08
**Priority:** MEDIUM (Week 2)
**Estimated Effort:** 2-3 hours
**Risk Level:** Low
**Parallelizable:** YES - no overlap with P03/P05/P06/P07 file sets
**Status:**  ✅ COMPLETED ✅ - implemented 2026-02-08 ✅
**Implemented by:** Codex

---

## Overview

This plan introduces a shared `LoggingSupport` abstraction and applies it only to core/app-infra files that are not touched by other plans. It eliminates duplicated guard logic, standardizes log levels for core entrypoints, and sets the foundation for UI/CLI logging cleanup that will be handled in P05-P07.

### Audit Issues Addressed

| ID                    | Severity | Category           | Summary                                                   |
|-----------------------|----------|--------------------|-----------------------------------------------------------|
| R-005                 | LOW      | Duplication        | Logging helper duplication (GuardLogStatement workaround) |
| EH-013-EH-014         | LOW      | Exception Handling | Log level inconsistencies for similar errors              |
| PMD GuardLogStatement | LOW      | Code Quality       | Logger calls should be surrounded by log level guards     |

---

## Files Owned by This Plan

### New Files
1. `src/main/java/datingapp/core/LoggingSupport.java`

### Modified Files
2. `src/main/java/datingapp/core/CandidateFinder.java`
3. `src/main/java/datingapp/Main.java`

---

## Detailed Tasks

### Task 1: Add `LoggingSupport` Interface

**File:** `src/main/java/datingapp/core/LoggingSupport.java`

**Goal:** Centralize guarded logging and make it reusable across the codebase.

**Implementation Notes:**
- Define `Logger logger()` as the required method.
- Provide default methods: `logTrace`, `logDebug`, `logInfo`, `logWarn`, `logError`.
- Each method must guard the underlying call with `logger.isXEnabled()`.
- Add static helper overloads for static contexts:
  - `static void logInfo(Logger logger, String message, Object... args)`
  - `static void logWarn(Logger logger, String message, Object... args)`
  - `static void logError(Logger logger, String message, Object... args)`
  - `static void logDebug(Logger logger, String message, Object... args)`
  - `static void logTrace(Logger logger, String message, Object... args)`

**Why static overloads:** `Main` is static-heavy and should not be forced into instance refactors.

---

### Task 2: Migrate `CandidateFinder` to LoggingSupport

**File:** `src/main/java/datingapp/core/CandidateFinder.java`

**Steps:**
1. Add `implements LoggingSupport` to the class.
2. Implement `logger()` to return the existing `Logger` field.
3. Remove local `logInfo`, `logDebug`, and `logTrace` helper methods.
4. Leave call sites intact (`logInfo(...)`, `logDebug(...)`, `logTrace(...)`) to use the default methods.

**Outcome:** Guarded logging stays, duplication removed, no call-site churn.

---

### Task 3: Migrate `Main` to LoggingSupport Static Helpers

**File:** `src/main/java/datingapp/Main.java`

**Steps:**
1. Replace the current guarded `logInfo(...)` helper with a one-line delegate to `LoggingSupport.logInfo(logger, ...)`.
2. Replace the guarded debug call in the exception path with `LoggingSupport.logDebug(logger, ...)` to keep guard logic centralized.
3. Review the error path log level and update to `WARN` or `ERROR` (see Task 4).

**Note:** Keep the `logInfo(...)` method for minimal call-site changes, but remove guard logic duplication by delegating to `LoggingSupport`.

---

### Task 4: Log Level Alignment (EH-013/014) for Plan-Owned Files

**Scope:** `CandidateFinder.java`, `Main.java`

**Rubric:**
- `DEBUG`/`TRACE` for algorithm decisions and high-volume details.
- `INFO` for normal lifecycle milestones.
- `WARN` for recoverable errors or unexpected states.
- `ERROR` for unrecoverable failures or exceptions that abort flow.

**Concrete change:**
- In `Main`, change the \"An error occurred\" log from `INFO` to `WARN` (or `ERROR` if the exception aborts the run).

---

## Execution Order

1. Add `LoggingSupport.java`.
2. Update `CandidateFinder.java` to implement `LoggingSupport`.
3. Update `Main.java` to delegate to static helpers and adjust log level.
4. Run formatting and targeted tests.

---

## Verification Checklist

- `mvn spotless:apply`
- `mvn test -Dtest=CandidateFinderTest`
- `rg "logInfo\(|logDebug\(|logTrace\(" src/main/java/datingapp/core/CandidateFinder.java` shows no local helper methods
- `rg "private static void logInfo" src/main/java/datingapp/Main.java` shows delegate-only helper
- PMD GuardLogStatement violations are not reintroduced in these files

---

## Files NOT Owned by This Plan (Explicit Boundaries)

| File / Area                                                   | Reason                          | Owner Plan |
|---------------------------------------------------------------|---------------------------------|------------|
| `src/main/java/datingapp/core/ConfigLoader.java`              | Moved in P03 Phase D            | P03        |
| `src/main/java/datingapp/storage/DatabaseManager.java`        | Split in P03 Task 7             | P03        |
| `src/main/java/datingapp/app/cli/*` (handlers + `EnumMenu`)   | CLI refactor + app services     | P06        |
| `src/main/java/datingapp/ui/viewmodel/*` + `ViewModelFactory` | VM layer fixes + FX-thread work | P05        |
| `src/main/java/datingapp/ui/controller/*`                     | Controller decomposition        | P07        |
| `src/main/java/datingapp/ui/NavigationService.java`           | Thread safety + error handling  | P07        |
| `src/main/java/datingapp/ui/util/ImageCache.java`             | TS-007 TOCTOU fix               | P05        |
| `src/main/java/datingapp/ui/util/UiServices.java`             | Utility merge                   | P07        |

---

## Dependencies

- None. This plan can run immediately without blocking or requiring other plans.
- P03 should apply the same `LoggingSupport` pattern when moving `ConfigLoader` and splitting `DatabaseManager`.

---

## Rollback Strategy

1. Remove `LoggingSupport.java`.
2. Restore local logging helper methods in `CandidateFinder` and `Main`.
3. Revert the log level change in `Main` if needed.

Each change is isolated and reversible without touching other modules.
