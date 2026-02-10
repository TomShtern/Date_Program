# Plan 02: Core Infrastructure Thread Safety, Dead Code Removal & Workspace Hygiene

> **✅ COMPLETED: 2026-02-08** ✅
> - All 11 tasks implemented successfully
> - 812 tests pass (0 failures)
> - JaCoCo coverage checks passed
> - Spotless formatting applied

**Date:** 2026-02-08
**Priority:** CRITICAL + HIGH (Week 1)
**Estimated Effort:** 5–7 hours
**Risk Level:** Low–Medium (internal fixes, no public API changes)
**Parallelizable:** YES — no file overlap with Plan 01 or any other plan

---

## Overview

This plan addresses three complementary concerns that share the same core infrastructure files:

1. **Thread safety fixes** in 5 core service files (4 CRITICAL + 1 HIGH)
2. **Exception handling improvements** in 4 core files (1 HIGH + 6 MEDIUM)
3. **Dead code removal + workspace hygiene** (2 dead files + 2 non-source files + 13 root-level artifacts)

These changes are strictly internal. No public interface contracts change, no storage layer files are touched (Plan 01's domain), and no UI/CLI files are modified.

### Audit Issues Addressed

| ID | Severity | Category | Summary |
|----|----------|----------|---------|
| TS-003 | **CRITICAL** | Thread Safety | `AppBootstrap.dbManager` not volatile |
| TS-005 | **CRITICAL** | Thread Safety | `UndoService.transactionExecutor` not volatile |
| TS-006 | **CRITICAL** | Thread Safety | `DailyService` check-then-act on ConcurrentHashMap |
| TS-008 | **HIGH** | Thread Safety | `PerformanceMonitor.OperationMetrics` mixed sync strategies |
| TS-010 | **HIGH** | Thread Safety | `AppSession.notifyListeners()` incomplete exception info |
| EH-002 | **HIGH** | Exception Handling | `AppSession.notifyListeners()` swallows stack traces |
| EH-004 | **HIGH** | Exception Handling | `MatchingService.recordLike()` fallback query unguarded |
| SQL-002 | **HIGH** | SQL/Storage | No transaction boundary in `MatchingService.recordLike()` |
| EH-005–010 | **MEDIUM** | Exception Handling | `ConfigLoader` silent catches (IOException, NumberFormatException) |
| R-011 | **LOW** | Dead Code | `PurgeService` has zero inbound imports |
| R-014 | **LOW** | Dead Code | `ErrorMessages` only 3 consumers — merge into consumers |
| R-015 | **LOW** | Workspace | `MODULE_OVERVIEW.md` files in source packages |
| R-016 | **LOW** | Workspace | 13+ root-level audit artifact files |

**NOT in scope** (owned by other plans):
- `SoftDeletable.java` inline — requires editing `User.java` and `Match.java` (Plan 03: Core Restructuring)
- `EnumSetUtil.java` inline — requires editing `ProfileHandler.java` (Plan 06: CLI Refactoring)
- Standalone enum nesting (`Gender`, `UserState`, `VerificationMethod`) — mass import changes (Plan 03)
- `CliConstants`/`CliUtilities` merge — CLI files (Plan 06)
- `handleBack()` extraction — UI controllers (Plan 07)
- ViewModel thread safety (TS-002, TS-009) — ViewModels (Plan 05)
- NavigationService thread safety (TS-004) — UI layer (Plan 07)

---

## Files Owned by This Plan

These files are **exclusively modified by this plan**. No other plan should touch them.

### Content Edits (Core Services)
1. `src/main/java/datingapp/core/AppBootstrap.java` — TS-003
2. `src/main/java/datingapp/core/AppSession.java` — TS-010, EH-002
3. `src/main/java/datingapp/core/UndoService.java` — TS-005, ErrorMessages inline
4. `src/main/java/datingapp/core/DailyService.java` — TS-006
5. `src/main/java/datingapp/core/PerformanceMonitor.java` — TS-008
6. `src/main/java/datingapp/core/MatchingService.java` — SQL-002, EH-004, ErrorMessages inline
7. `src/main/java/datingapp/core/ConfigLoader.java` — EH-005–EH-010
8. `src/main/java/datingapp/core/ValidationService.java` — ErrorMessages inline
9. `src/main/java/datingapp/core/MessagingService.java` — ErrorMessages inline

### Deleted Files
10. `src/main/java/datingapp/core/PurgeService.java` — DELETE (zero imports)
11. `src/main/java/datingapp/core/ErrorMessages.java` — DELETE (after inlining)

### Moved Files
12. `src/main/java/datingapp/core/MODULE_OVERVIEW.md` → `docs/core-module-overview.md`
13. `src/main/java/datingapp/storage/MODULE_OVERVIEW.md` → `docs/storage-module-overview.md`

### Workspace Cleanup
14. Root-level audit artifacts → `docs/audits/` (13 files)
15. `.gitignore` — EDIT (add audit artifact ignores)

### Test Files
16. `src/test/java/datingapp/core/ErrorMessagesTest.java` — DELETE (tested class removed)
17. `src/test/java/datingapp/core/UndoServiceTest.java` — EDIT (update ErrorMessages refs)
18. `src/test/java/datingapp/core/MatchingServiceTest.java` — EDIT (add transaction test)
19. `src/test/java/datingapp/core/DailyServiceTest.java` — EDIT (verify computeIfAbsent behavior)
20. `src/test/java/datingapp/core/ValidationServiceTest.java` — EDIT (update ErrorMessages refs)
21. `src/test/java/datingapp/core/PerformanceMonitorTest.java` — EDIT (verify simplified metrics)

---

## Detailed Tasks

### Task 1: Fix AppBootstrap.dbManager Visibility (TS-003)

**File:** `AppBootstrap.java`
**Line:** 15

**Current state (partially fixed):**
```java
private static volatile ServiceRegistry services;     // Already volatile — good
private static DatabaseManager dbManager;              // NOT volatile!
private static volatile boolean initialized = false;   // Already volatile — good
```

The audit flagged both `services` and `dbManager` as non-volatile, but `services` was already fixed. Only `dbManager` remains.

**Why it matters:** Although `dbManager` is only accessed inside `synchronized` blocks (`initialize()`, `shutdown()`, `reset()`), making it volatile is defensive correctness. If a future developer adds an unsynchronized accessor, the field is already safely published.

**Fix:**
```java
private static volatile DatabaseManager dbManager;
```

**Verification:** Existing tests pass. This is a one-line safety improvement.

---

### Task 2: Fix AppSession Exception Handling (TS-010, EH-002)

**File:** `AppSession.java`
**Lines:** 64-73 (`notifyListeners()`)

**Current Problem:**
```java
private void notifyListeners(User user) {
    for (Consumer<User> listener : listeners) {
        try {
            listener.accept(user);
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Listener threw exception: {}", e.getMessage());
                // ^^^ Only logs the message string, not the stack trace!
            }
        }
    }
}
```

**Why it's dangerous:** When a listener throws, only `e.getMessage()` is logged — the stack trace is lost. This makes debugging listener failures nearly impossible. In production, a broken listener silently fails with no actionable diagnostic information.

**Fix:** Log with full exception (SLF4J's last-argument-exception convention):
```java
private void notifyListeners(User user) {
    for (Consumer<User> listener : listeners) {
        try {
            listener.accept(user);
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Session listener threw exception", e);
            }
        }
    }
}
```

**Design note:** The `notifyListeners()` method is intentionally called OUTSIDE the synchronized block in `setCurrentUser()` — this prevents deadlock if a listener tries to re-enter AppSession. The `CopyOnWriteArrayList` ensures safe iteration. This design is correct; only the logging needs fixing.

**Verification:** Existing tests pass. The behavioral change is purely diagnostic.

---

### Task 3: Fix UndoService.transactionExecutor Visibility (TS-005)

**File:** `UndoService.java`
**Line:** 38

**Current Problem:**
```java
private TransactionExecutor transactionExecutor;  // NOT volatile
```

`setTransactionExecutor()` (line 73) writes this field without synchronization. `undo()` (line 167) reads it without synchronization. If thread A calls `setTransactionExecutor()` and thread B calls `undo()`, thread B may see a stale `null`.

**Fix:**
```java
private volatile TransactionExecutor transactionExecutor;
```

**Also inline ErrorMessages constants** used in this file (lines 152, 160, 172, 189):
```java
// Replace:
return UndoResult.failure(ErrorMessages.NO_SWIPE_TO_UNDO);
// With:
return UndoResult.failure("No swipe to undo");

// Replace:
return UndoResult.failure(ErrorMessages.UNDO_WINDOW_EXPIRED);
// With:
return UndoResult.failure("Undo window expired");

// Replace:
return UndoResult.failure(ErrorMessages.LIKE_NOT_FOUND);
// With:
return UndoResult.failure("Like not found in database");

// Replace:
return UndoResult.failure(ErrorMessages.UNDO_FAILED.formatted(e.getMessage()));
// With:
return UndoResult.failure("Failed to undo: %s".formatted(e.getMessage()));
```

Remove `import datingapp.core.ErrorMessages;` after inlining.

**Verification:** Update `UndoServiceTest.java` assertions that compare against `ErrorMessages.*` constants — replace with the inline string literals.

---

### Task 4: Fix DailyService Check-Then-Act Race (TS-006)

**File:** `DailyService.java`
**Lines:** 141-157 (inside `getDailyPick()`)

**Current Problem:**
```java
String cacheKey = seeker.getId() + "_" + today;
UUID cachedPickId = cachedDailyPicks.get(cacheKey);          // READ
User picked = null;
if (cachedPickId != null) {
    picked = candidates.stream()
            .filter(candidate -> candidate.getId().equals(cachedPickId))
            .findFirst()
            .orElse(null);
}

long seed = today.toEpochDay() + seeker.getId().hashCode();
Random pickRandom = new Random(seed);
if (picked == null) {                                          // CHECK
    picked = candidates.get(pickRandom.nextInt(candidates.size()));
    cachedDailyPicks.put(cacheKey, picked.getId());            // ACT
}
```

**Why it's a problem:** Two threads calling `getDailyPick()` for the same user simultaneously can both see `cachedPickId == null`, both compute a candidate, and both `put()`. Because the seed is deterministic (`today.toEpochDay() + userId.hashCode()`), both threads select the same candidate — so the race is **benign** (result is correct). But the duplicated computation is wasteful, and the pattern violates ConcurrentHashMap's intended usage.

**Fix:** Use `computeIfAbsent` to atomically check-and-populate:
```java
String cacheKey = seeker.getId() + "_" + today;

// Deterministic random selection based on date + user ID
long seed = today.toEpochDay() + seeker.getId().hashCode();
Random pickRandom = new Random(seed);

UUID pickedId = cachedDailyPicks.computeIfAbsent(cacheKey,
        _ -> candidates.get(pickRandom.nextInt(candidates.size())).getId());

User picked = candidates.stream()
        .filter(candidate -> candidate.getId().equals(pickedId))
        .findFirst()
        .orElse(null);

if (picked == null) {
    // Cached pick no longer in candidate list (e.g., user was blocked since caching)
    cachedDailyPicks.remove(cacheKey);
    picked = candidates.get(pickRandom.nextInt(candidates.size()));
    cachedDailyPicks.put(cacheKey, picked.getId());
}
```

**Key insight:** `computeIfAbsent` is atomic for ConcurrentHashMap — if two threads race, only one executes the lambda. The other thread gets the result of the first. This eliminates the wasted computation.

**Verification:** Existing `DailyServiceTest.java` tests should pass. Consider adding a test that verifies the same daily pick is returned for the same user+date combination.

---

### Task 5: Simplify PerformanceMonitor.OperationMetrics (TS-008)

**File:** `PerformanceMonitor.java`
**Lines:** 150-192 (`OperationMetrics` inner class)

**Current Problem:**
```java
public static class OperationMetrics {
    private final LongAdder count = new LongAdder();   // Thread-safe accumulator
    private final LongAdder totalMs = new LongAdder(); // Thread-safe accumulator
    private long minMs = Long.MAX_VALUE;               // Plain field
    private long maxMs = Long.MIN_VALUE;               // Plain field

    synchronized void record(long durationMs) {        // Synchronized writer
        count.increment();
        totalMs.add(durationMs);
        minMs = Math.min(minMs, durationMs);
        maxMs = Math.max(maxMs, durationMs);
    }
```

**Why it's confusing:** `LongAdder` is designed for high-contention unsynchronized access, but here it's used inside a `synchronized` block. Meanwhile `minMs`/`maxMs` are plain longs that need the `synchronized` block. This mixes two concurrency strategies for no benefit — the `synchronized` block serializes everything anyway, making `LongAdder`'s multi-cell design pointless.

**Fix:** Replace `LongAdder` with plain `long` since we're already synchronizing:
```java
public static class OperationMetrics {
    private final String name;
    private long count;
    private long totalMs;
    private long minMs = Long.MAX_VALUE;
    private long maxMs = Long.MIN_VALUE;

    OperationMetrics(String name) {
        this.name = name;
    }

    synchronized void record(long durationMs) {
        count++;
        totalMs += durationMs;
        minMs = Math.min(minMs, durationMs);
        maxMs = Math.max(maxMs, durationMs);
    }

    public synchronized String getName() { return name; }
    public synchronized long getCount() { return count; }
    public synchronized long getTotalMs() { return totalMs; }
    public synchronized long getAverageMs() { return count > 0 ? totalMs / count : 0; }
    public synchronized long getMinMs() { return count > 0 ? minMs : 0; }
    public synchronized long getMaxMs() { return count > 0 ? maxMs : 0; }
}
```

Remove import: `import java.util.concurrent.atomic.LongAdder;`

**Why simpler is better:** All access is now consistently `synchronized`. No mixed strategies, no over-engineering. The `getName()` method doesn't technically need `synchronized` (it's final), but keeping it consistent avoids future confusion.

**Verification:** `PerformanceMonitorTest.java` should pass unchanged since the API is identical.

---

### Task 6: Add Transaction Boundary to MatchingService.recordLike() (SQL-002, EH-004)

**File:** `MatchingService.java`
**Lines:** 36-38 (new field), 120-166 (`recordLike()`)

**Current Problem (SQL-002):**
```java
likeStorage.save(like);                                    // Step 1: save like
if (likeStorage.mutualLikeExists(like.whoLikes(), ...)) {  // Step 2: check mutual
    Match match = Match.create(like.whoLikes(), ...);
    matchStorage.save(match);                              // Step 3: save match
}
```

If the application crashes between steps 1 and 3, the like is saved but the match is not created. Two concurrent `recordLike()` calls for mutual users could also race — though this is mitigated by the idempotent MERGE on match saves and the `exists()` check on likes.

**Current Problem (EH-004):**
```java
} catch (RuntimeException ex) {
    logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());
    matchResult = matchStorage.get(match.getId())   // This secondary query is NOT caught
            .filter(existing -> existing.getState() == Match.State.ACTIVE);
}
```

The fallback `matchStorage.get()` call on line 156-157 could itself throw a RuntimeException (e.g., connection failure), which would propagate uncaught.

**Fix — Add TransactionExecutor support (same pattern as UndoService):**

```java
// Add field (line ~38):
private volatile TransactionExecutor transactionExecutor;

// Add setter:
public void setTransactionExecutor(TransactionExecutor transactionExecutor) {
    this.transactionExecutor = transactionExecutor;
}
```

**Fix — Guard the fallback query (EH-004):**
```java
} catch (RuntimeException ex) {
    if (logger.isWarnEnabled()) {
        logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());
    }
    try {
        matchResult = matchStorage.get(match.getId())
                .filter(existing -> existing.getState() == Match.State.ACTIVE);
    } catch (RuntimeException fallbackEx) {
        if (logger.isErrorEnabled()) {
            logger.error("Fallback match lookup also failed for {}", match.getId(), fallbackEx);
        }
        // matchResult remains empty — safe to continue
    }
}
```

**Inline ErrorMessages constants** used in this file (from MessagingService pattern):
- MatchingService doesn't directly use `ErrorMessages.*` — confirmed by grep. No changes needed.

**Transaction wrapping note:** The full transaction wrapping of `recordLike()` requires either:
- a) Extending `TransactionExecutor` with a generic `<T> T inTransaction(Supplier<T>)` method, or
- b) Accepting a `Jdbi` handle parameter (leaks storage layer into core)

Option (a) is the clean approach. Add to `TransactionExecutor`:
```java
/**
 * Executes the given action within a transaction. Returns the result.
 * If the action throws, the transaction is rolled back.
 */
<T> T inTransaction(java.util.function.Supplier<T> action);
```

Then update `JdbiTransactionExecutor` to implement this using `jdbi.inTransaction()`.

Then wrap the `recordLike()` body:
```java
public Optional<Match> recordLike(Like like) {
    Objects.requireNonNull(like, LIKE_REQUIRED);
    if (likeStorage.exists(like.whoLikes(), like.whoGotLiked())) {
        return Optional.empty();
    }

    TransactionExecutor tx = this.transactionExecutor;
    if (tx != null) {
        return tx.inTransaction(() -> doRecordLike(like));
    }
    return doRecordLike(like);
}

private Optional<Match> doRecordLike(Like like) {
    // ... existing logic from lines 128-165, moved here
}
```

**Important:** The `TransactionExecutor` interface is in `core/storage/` (Plan 01's domain). However, Plan 01 does NOT modify `TransactionExecutor.java` — it's not in Plan 01's file list. If adding `inTransaction()` is needed, this plan owns that change. Alternatively, if you want to avoid touching `core/storage/TransactionExecutor.java`, the EH-004 fix (guard the fallback) is independently valuable without the full transaction wrapping.

**Verification:** Add a test in `MatchingServiceTest.java` that exercises the guarded fallback path (mock a storage failure on save, verify no unhandled exception).

---

### Task 7: Fix ConfigLoader Silent Exception Handling (EH-005–EH-010)

**File:** `ConfigLoader.java`

**Current Problems (6 instances):**

1. **Line 68-70** (EH-005): IOException loading config file — logged at WARN, swallowed:
   ```java
   } catch (IOException ex) {
       logWarn("Failed to load config file {}: {}", configPath, ex.getMessage());
   }
   ```
   **Verdict:** This is **acceptable**. Failing to load a config file falls back to defaults, which is intentional. But the exception stack trace should be logged for debugging.

2. **Line 165-167** (EH-006): IOException parsing JSON — logged at WARN, swallowed:
   ```java
   } catch (IOException ex) {
       logWarn("Failed to parse JSON config: {}", ex.getMessage());
   }
   ```
   **Verdict:** **Acceptable** (same fallback-to-defaults pattern), but needs stack trace.

3. **Line 189-191** (EH-007): Invalid timezone — logged at WARN, swallowed:
   ```java
   } catch (Exception ex) {
       logWarn("Invalid timezone in env var: {}", tz);
   }
   ```
   **Verdict:** **Acceptable** (invalid env var shouldn't crash the app), but should log the actual exception type and the env var name.

4. **Line 211-213** (EH-008): NumberFormatException in env var — logged at WARN, swallowed:
   ```java
   } catch (NumberFormatException ex) {
       logWarn("Invalid integer in env var {}: {}", ENV_PREFIX + suffix, value);
   }
   ```
   **Verdict:** **Acceptable** (bad env var shouldn't crash). Logging is already informative.

**Fix approach:** For ConfigLoader, the swallow-and-fallback pattern is correct by design (config loading should be resilient). The improvements are:

1. **Add stack traces to IOException catches** (EH-005, EH-006):
   ```java
   } catch (IOException ex) {
       logWarn("Failed to load config file {}: {}", configPath, ex.getMessage(), ex);
   }
   ```
   SLF4J interprets the last argument as the throwable when it's an Exception. But since we're using a guard-wrapped helper method, we need to update `logWarn` to accept an optional throwable:

   ```java
   private static void logWarn(String message, Object... args) {
       if (logger.isWarnEnabled()) {
           logger.warn(message, args);
       }
   }
   ```
   This already works! SLF4J's varargs correctly handles the last argument being a Throwable. So just add `ex` as the last argument:

   ```java
   } catch (IOException ex) {
       logWarn("Failed to load config file {}", configPath, ex);
   }
   // Note: removed ": {}" from the pattern since SLF4J extracts the throwable automatically
   ```

2. **Improve timezone error logging** (EH-007):
   ```java
   } catch (Exception ex) {
       logWarn("Invalid timezone in env var {}{}: {}",
               ENV_PREFIX, "USER_TIME_ZONE", tz);
   }
   ```

**Verification:** `ConfigLoaderTest.java` should pass unchanged. These are purely diagnostic improvements.

---

### Task 8: Delete PurgeService (Dead Code — R-011)

**File:** `src/main/java/datingapp/core/PurgeService.java`

**Evidence:** Zero inbound imports confirmed by grep search. No `PurgeServiceTest.java` exists. The class is 73 lines of unused code. `CleanupService.java` covers the same cleanup domain (expired daily picks + sessions) and is actively used.

**Action:** Delete the file.

**Impact on other files:** None. No file in the project imports `PurgeService`. No test file references it.

**Note:** If purge functionality for soft-deleted records is needed in the future, extend `CleanupService` at that time. Don't carry dead code "just in case."

---

### Task 9: Delete ErrorMessages & Inline Constants (R-014)

**File to delete:** `src/main/java/datingapp/core/ErrorMessages.java` (59 lines)
**Test to delete:** `src/test/java/datingapp/core/ErrorMessagesTest.java`

**Consumer Analysis (grep results — 4 consumers):**

| Consumer File | Constants Used | Action |
|---------------|---------------|--------|
| `UndoService.java` | `NO_SWIPE_TO_UNDO`, `UNDO_WINDOW_EXPIRED`, `LIKE_NOT_FOUND`, `UNDO_FAILED` | Inline as private `static final String` constants in UndoService |
| `ValidationService.java` | `NAME_EMPTY`, `NAME_TOO_LONG`, `AGE_TOO_YOUNG`, `AGE_INVALID`, `HEIGHT_TOO_SHORT`, `HEIGHT_TOO_TALL`, `DISTANCE_TOO_SHORT`, `DISTANCE_TOO_FAR`, `BIO_TOO_LONG` | Inline as private `static final String` constants in ValidationService |
| `MessagingService.java` | `SENDER_NOT_FOUND`, `RECIPIENT_NOT_FOUND`, `NO_ACTIVE_MATCH`, `EMPTY_MESSAGE`, `MESSAGE_TOO_LONG` | Inline as private `static final String` constants in MessagingService |
| `ErrorMessages.java` | Self-reference | Delete |

**Strategy:** Move each constant to the service that uses it as a `private static final String`. This keeps constants close to their usage and eliminates the shared utility class.

**Example for ValidationService:**
```java
public class ValidationService {

    // Error message constants (moved from ErrorMessages.java)
    private static final String NAME_EMPTY = "Name cannot be empty";
    private static final String NAME_TOO_LONG = "Name too long (max %d chars)";
    private static final String AGE_TOO_YOUNG = "Must be %d or older";
    private static final String AGE_INVALID = "Invalid age";
    private static final String HEIGHT_TOO_SHORT = "Height too short (min %dcm)";
    private static final String HEIGHT_TOO_TALL = "Height too tall (max %dcm)";
    private static final String DISTANCE_TOO_SHORT = "Distance must be at least %dkm";
    private static final String DISTANCE_TOO_FAR = "Distance too far (max %dkm)";
    private static final String BIO_TOO_LONG = "Bio too long (max %d chars)";

    // ... rest of class unchanged
}
```

**For MessagingService:** Same pattern — add the messaging constants as private fields. The existing `ErrorMessages.SENDER_NOT_FOUND` references become just `SENDER_NOT_FOUND`.

**After inlining all consumers:** Remove `import datingapp.core.ErrorMessages;` from all 3 files, then delete `ErrorMessages.java`.

**Verification:**
- Delete `ErrorMessagesTest.java` (tests the deleted class).
- Update `UndoServiceTest.java`: replace any assertions comparing to `ErrorMessages.X` with inline strings.
- Update `ValidationServiceTest.java`: same.
- Run full test suite to verify no compilation errors from missing import.

---

### Task 10: Move MODULE_OVERVIEW.md Files Out of Source Tree (R-015)

**Files:**
- `src/main/java/datingapp/core/MODULE_OVERVIEW.md` → `docs/core-module-overview.md`
- `src/main/java/datingapp/storage/MODULE_OVERVIEW.md` → `docs/storage-module-overview.md`

**Why:** Non-source files in Java source packages clutter the source tree and may confuse build tools or IDEs that scan source directories. Documentation belongs in `docs/`.

**Action:** `git mv` both files to `docs/` with descriptive names.

---

### Task 11: Clean Root-Level Audit Artifacts (R-016)

**Problem:** 13+ audit and analysis artifacts clutter the repository root. These files are currently untracked (per `git status`) and should be organized.

**Root-level artifacts to move:**

| File | Type | Destination |
|------|------|-------------|
| `report.md` | Audit report | `docs/audits/report.md` |
| `summary.md` | Audit summary | `docs/audits/summary.md` |
| `reportByGPT.md` | Audit report | `docs/audits/reportByGPT.md` |
| `summaryByGPT.md` | Audit summary | `docs/audits/summaryByGPT.md` |
| `audit_report_v3.md` | Audit report | `docs/audits/audit_report_v3.md` |
| `audit_summary_v3.md` | Audit summary | `docs/audits/audit_summary_v3.md` |
| `combined_report.md` | Combined audit | `docs/audits/combined_report.md` |
| `combined_summary.md` | Combined audit | `docs/audits/combined_summary.md` |
| `audit_metrics.json` | Audit data | `docs/audits/audit_metrics.json` |
| `flagged_metrics.json` | Audit data | `docs/audits/flagged_metrics.json` |
| `dependency_graph.json` | Audit data | `docs/audits/dependency_graph.json` |
| `dependency_graph_simple.json` | Audit data | `docs/audits/dependency_graph_simple.json` |
| `tokei.json` | Code stats | `docs/audits/tokei.json` |

**Also at root (already tracked — move if desired):**
- `analysis-summary.json` → `docs/audits/`
- `violations_structured.json` → `docs/audits/`

**Action:**
1. Create `docs/audits/` directory
2. Move all listed files
3. Update `.gitignore` — add comment section for audit outputs:

```gitignore
# Audit & analysis artifacts (regenerable)
/docs/audits/*.sarif
/docs/audits/*.sarif.json
```

**Files that stay at root (not moved):**
- `CLAUDE.md` — project configuration
- `AGENTS.md` — project configuration
- `README.md` — standard readme
- `prd.md`, `prd0.5.md` — product requirements
- `pom.xml` — Maven config
- `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` — active reference

---

## Execution Order

Tasks are ordered by dependency and risk:

1. **Task 8** (PurgeService delete) — Zero-risk dead code removal. Start here to build confidence.
2. **Task 10** (MODULE_OVERVIEW moves) — Zero-risk file moves.
3. **Task 11** (Root artifact cleanup) — Zero-risk workspace organization.
4. **Task 9** (ErrorMessages inline + delete) — Low risk; mechanical constant moves. Do this before thread safety tasks since it touches the same files (UndoService, ValidationService, MessagingService).
5. **Task 1** (AppBootstrap volatile) — One-line fix.
6. **Task 2** (AppSession logging) — One-line fix.
7. **Task 3** (UndoService volatile) — One-line fix + ErrorMessages already inlined.
8. **Task 5** (PerformanceMonitor simplify) — Medium change, self-contained.
9. **Task 4** (DailyService computeIfAbsent) — Medium change, self-contained.
10. **Task 7** (ConfigLoader exception logging) — Small diagnostic improvements.
11. **Task 6** (MatchingService transaction + EH-004) — Largest change; do last.

---

## Verification Checklist

After all tasks are complete, run:

```bash
# 1. Format first (Spotless)
mvn spotless:apply

# 2. Full verification pipeline
mvn verify

# 3. Specifically run affected tests
mvn test -Dtest="UndoServiceTest,MatchingServiceTest,DailyServiceTest,ValidationServiceTest,PerformanceMonitorTest,ConfigLoaderTest,CleanupServiceTest"

# 4. Verify dead code is gone
rg "import datingapp.core.PurgeService" src/
rg "import datingapp.core.ErrorMessages" src/
# Both should return zero results
```

**Expected outcomes:**
- [ ] All existing tests pass (zero regressions)
- [ ] `PurgeService.java` and `ErrorMessages.java` deleted
- [ ] `ErrorMessagesTest.java` deleted
- [ ] Zero imports of `PurgeService` or `ErrorMessages` remain
- [ ] `MODULE_OVERVIEW.md` files moved to `docs/`
- [ ] Root-level audit artifacts moved to `docs/audits/`
- [ ] `mvn verify` passes (Spotless + PMD + JaCoCo + tests)
- [ ] No new PMD violations introduced

---

## Files NOT Owned by This Plan (Boundary)

| File / Issue | Reason | Owner Plan |
|------|--------|------------|
| `core/SoftDeletable.java` | Requires editing `User.java` + `Match.java` | Plan 03: Core Restructuring |
| `core/EnumSetUtil.java` | Requires editing `ProfileHandler.java` | Plan 06: CLI Refactoring |
| `core/Gender.java`, `UserState.java`, `VerificationMethod.java` | Mass import changes across 50+ files (R-018) | Plan 03: Core Restructuring |
| `core/User.java`, `core/Match.java` | SoftDeletable inline + enum nesting | Plan 03 |
| `core/ServiceRegistry.java` | StorageFactory extraction (R-006) | Plan 03 |
| `core/SessionService.java` | TS-012 (synchronization on init) | Plan 03 |
| `app/cli/CliConstants.java`, `CliUtilities.java` | CLI merge (R-010) | Plan 06 |
| `app/api/MatchRoutes.java`, `MessagingRoutes.java` | EH-011 (HTTP routes throw 500s) | Plan 06 |
| `ui/viewmodel/MatchingViewModel.java` | TS-002 (ConcurrentLinkedQueue) | Plan 05 |
| `ui/viewmodel/ChatViewModel.java` | TS-009 (observable lists race) | Plan 05 |
| `ui/NavigationService.java` | TS-004 + TS-013 (thread-safe deque + singleton) + EH-003 (FXML errors) | Plan 07 |
| `ui/util/ImageCache.java` | TS-007 (preload TOCTOU race) | Plan 05 |
| `ui/ViewModelFactory.java` | TS-011 (synchronization) | Plan 05 |
| `ui/controller/BaseController.java` | handleBack() extraction (R-017) | Plan 07 |
| All ViewModels | Layer violations (R-001), FX-thread safety (R-002) | Plan 05 |
| All Controllers | Decomposition (R-008) | Plan 07 |
| All CLI Handlers | App service extraction, dealbreaker generic (R-009) | Plan 06 |
| `storage/*` files | Plan 01 domain | Plan 01 |
| `core/storage/TransactionExecutor.java` | Extended by Task 6 ONLY if transaction wrapping is implemented; otherwise untouched | This plan (conditional) |
| 23 files with logging helpers | LoggingSupport mixin (R-005) | Plan 04 |
| EH-012 (Result vs Exception inconsistency) | Cross-cutting pattern standardization | Plan 06 |
| EH-013–014 (Log level inconsistencies) | Logging pattern issue | Plan 04 |
| R-007 (AppConfig constructor injection, 8 files) | Requires touching User, VMs, services | Plan 03 |
| IF-001–005 (Fat interfaces, Optional vs null) | Interface redesign | Plan 05 / Backlog |
| TQ-001–028 (Test quality, date-dependence) | Test infrastructure | Plan 08 |
| NS-001, NS-005–026 (@Nullable, null safety) | Null safety annotations | Plan 08 |
| Magic Numbers (60+ items) | Constants extraction | Plan 08 |

---

## Dependencies on Other Plans

**This plan has ZERO hard dependencies.** It can be executed immediately and in parallel with Plan 01 and all other plans.

**Soft dependencies:**
- Plan 03 (Core Restructuring) may later MOVE files edited by this plan (e.g., `UndoService.java` to `core/matching/`). Those moves should happen AFTER this plan completes to avoid merge conflicts. But this is not a blocking dependency — git can merge the content changes + file moves separately if needed.

**Other plans that depend on this plan:** None directly. All plans benefit from thread-safe core infrastructure.

---

## Rollback Strategy

Each task is independently reversible:

1. **Task 1 (volatile):** Remove the keyword. No behavioral impact.
2. **Task 2 (logging):** Revert to message-only logging.
3. **Task 3 (volatile):** Remove the keyword.
4. **Task 4 (computeIfAbsent):** Revert to manual get/put pattern. Race is benign.
5. **Task 5 (OperationMetrics):** Restore `LongAdder` fields. API is identical.
6. **Task 6 (transaction + guard):** Revert to unguarded fallback. Transaction support is additive.
7. **Task 7 (ConfigLoader):** Revert to message-only logging.
8. **Task 8 (PurgeService):** Restore from git. Zero callers, so no cascading impact.
9. **Task 9 (ErrorMessages):** Restore `ErrorMessages.java` and revert constant inlining. This is the most tedious to revert manually — consider doing this task in a single commit for easy revert.
10. **Tasks 10-11 (file moves):** Reverse the `git mv` operations.
