# Architecture Audit Report v3 — NEW FINDINGS ONLY

**Date:** 2026-02-07
**Auditor:** Claude Opus 4.6 (with parallel subagents)
**Scope:** Full repository — 126 main + 56 test files
**Baseline Exclusions:** Issues from summary.md, summaryByGPT.md, report.md, reportByGPT.md

---

## Project Metrics

| Metric               | Value                                            |
|----------------------|--------------------------------------------------|
| Total Files          | 185 code files (Java 182, CSS 2, XML 1)          |
| Primary Languages    | Java 25, JavaFX 25                               |
| Average LOC per File | 154.63 (main Java only)                          |
| Largest File         | `ui/controller/ProfileController.java` (667 LOC) |
| Most Complex File    | `app/cli/ProfileHandler.java` (~105 cyclomatic)  |
| **NEW Issues Found** | **171+**                                         |
| Issues by Severity   | 6 CRITICAL, 57 HIGH, 68 MEDIUM, 40+ LOW          |
| Estimated Fix Effort | ~45 hours over 6 weeks                           |

---

## Sources of NEW Mess and Confusion

| Category                 | Files Affected | Root Cause                        |
|--------------------------|----------------|-----------------------------------|
| Thread Safety Violations | 11 files       | No concurrency review/testing     |
| Test Date-Dependence     | 7 test files   | LocalDate.now() instead of Clock  |
| Swallowed Exceptions     | 6 files        | Copied catch-and-log anti-pattern |
| Fat Storage Interfaces   | 4 interfaces   | Interface grew without ISP review |
| Magic Numbers            | 25+ files      | Convenience over centralization   |
| Missing @Nullable        | 10 files       | No null-safety policy             |
| N+1 Queries              | 3 files        | Optimization not considered       |

---

## CATEGORY 1: THREAD SAFETY ISSUES (13 Issues)

### CRITICAL: TS-001 — DatabaseManager.getConnection() Race Condition
**File:** `src/main/java/datingapp/storage/DatabaseManager.java:97-104`

```java
public Connection getConnection() throws SQLException {
    if (!initialized) {  // CHECK (not synchronized)
        initializeSchema();
    }
    if (dataSource == null) {  // CHECK-THEN-ACT (race)
        initializePool();
    }
    return dataSource.getConnection();
}
```

**Problem:** `initialized` (line 24) is not volatile. Multiple threads calling `getConnection()` can pass the check simultaneously, triggering duplicate schema initializations.

**Impact:** Schema corruption, connection pool races, startup failures.

**Fix:**
```java
private static volatile boolean initialized = false;
private static volatile HikariDataSource dataSource;
```

---

### CRITICAL: TS-002 — MatchingViewModel.candidateQueue Unsynchronized Access
**File:** `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java:39,149-150,164`

```java
private final Queue<User> candidateQueue = new LinkedList<>();  // NOT thread-safe

public void refreshCandidates() {
    Thread.ofVirtual().start(() -> {
        candidateQueue.clear();           // Virtual thread WRITE
        candidateQueue.addAll(candidates); // Virtual thread WRITE
    });
}

public void nextCandidate() {
    User next = candidateQueue.poll();  // FX thread READ
}
```

**Problem:** `LinkedList` accessed from virtual thread (writes) and FX thread (reads) without synchronization.

**Impact:** `ConcurrentModificationException`, displaying wrong candidates.

**Fix:** Replace with `ConcurrentLinkedQueue<User>` or add explicit locking.

---

### CRITICAL: TS-003 — AppBootstrap.services Non-Volatile
**File:** `src/main/java/datingapp/core/AppBootstrap.java:14-16,51`

```java
private static ServiceRegistry services;      // NOT volatile!
private static DatabaseManager dbManager;     // NOT volatile!
private static volatile boolean initialized;  // ONLY this is volatile

public static ServiceRegistry getServices() {
    ServiceRegistry current = services;  // Unsynchronized read
    if (!initialized || current == null) {
        throw new IllegalStateException(...);
    }
    return current;  // May return stale reference
}
```

**Problem:** `services` written in synchronized `initialize()`, but read in unsynchronized `getServices()`. Memory visibility not guaranteed.

**Impact:** Stale/null ServiceRegistry returned to callers.

**Fix:** Make `services` and `dbManager` volatile.

---

### CRITICAL: TS-004 — NavigationService.navigationHistory ArrayDeque Not Thread-Safe
**File:** `src/main/java/datingapp/ui/NavigationService.java:40-41`

```java
private final Deque<ViewType> navigationHistory = new ArrayDeque<>();
```

Accessed from multiple threads: FX thread in `navigateWithTransition()`, background threads loading FXML, `goBack()` and `clearHistory()` with no synchronization.

**Impact:** ConcurrentModificationException, lost navigation entries.

---

### CRITICAL: TS-005 — UndoService.transactionExecutor Non-Volatile
**File:** `src/main/java/datingapp/core/UndoService.java:38,73-74`

```java
private TransactionExecutor transactionExecutor;  // NOT volatile

public void setTransactionExecutor(TransactionExecutor tx) {
    this.transactionExecutor = tx;  // Unsynchronized write
}

// Later in undo():
if (transactionExecutor != null) {  // Unsynchronized read
    transactionExecutor.atomicUndoDelete(...);
}
```

**Impact:** Thread may not see updated transactionExecutor, causing fallback to non-atomic deletes.

---

### CRITICAL: TS-006 — DailyService Check-Then-Act on ConcurrentHashMap
**File:** `src/main/java/datingapp/core/DailyService.java:35,142-157`

```java
private final Map<String, UUID> cachedDailyPicks = new ConcurrentHashMap<>();

public Optional<DailyPick> getDailyPick(User seeker) {
    UUID cachedPickId = cachedDailyPicks.get(cacheKey);  // READ
    if (cachedPickId != null) {
        picked = findUser(cachedPickId);
    }
    if (picked == null) {
        picked = selectRandom(candidates);
        cachedDailyPicks.put(cacheKey, picked.getId());  // WRITE
    }
}
```

**Problem:** Check-then-act is not atomic even with ConcurrentHashMap. Two threads can both select different picks.

**Fix:** Use `computeIfAbsent()`:
```java
UUID pickId = cachedDailyPicks.computeIfAbsent(cacheKey, k -> selectRandom(candidates).getId());
```

---

### HIGH: TS-007 — ImageCache.preload() TOCTOU Race
**File:** `src/main/java/datingapp/ui/util/ImageCache.java:172-188`

Lock released before Thread.start(), allowing duplicate preloads.

---

### HIGH: TS-008 — PerformanceMonitor.OperationMetrics Non-Atomic min/max
**File:** `src/main/java/datingapp/core/PerformanceMonitor.java:154-166`

`minMs` and `maxMs` are primitive `long`, updated inside synchronized block but LongAdder.sum() is not aligned.

---

### HIGH: TS-009 — ChatViewModel Observable Lists Race
**File:** `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:35-36`

Multiple background threads can modify `activeMessages` during refresh.

---

### HIGH: TS-010 — AppSession Listener Callback Outside Sync
**File:** `src/main/java/datingapp/core/AppSession.java:37-42`

Lock released before `notifyListeners()`, listeners may see stale state.

---

### MEDIUM: TS-011, TS-012, TS-013 — ViewModelFactory, SessionService Stripe Init, NavigationService getInstance

Various synchronization improvements needed.

---

## CATEGORY 2: TEST QUALITY ISSUES (28 Issues)

### HIGH: TQ-001 — Temporal Boundary Conditions (7 files)

**Files:**
- `DailyPickServiceTest.java:280`
- `StandoutsServiceTest.java:48,68`
- `AchievementServiceTest.java:120`
- `ProfilePreviewServiceTest.java:33`
- `DealbreakersEvaluatorTest.java:31,37`
- `DailyServiceTest.java:49,191`

**Problem:** Tests use `LocalDate.now().minusYears(age)` for birth dates. Age calculations vary by run date.

**Impact:** Tests become flaky around date boundaries.

**Fix:** Inject fixed `Clock` into all services and test helpers.

---

### HIGH: TQ-002 — Overly Complex Test Setup (250+ LOC boilerplate)

**Files:**
- `DailyPickServiceTest.java:295-535` — Inline InMemoryUserStorage, InMemoryLikeStorage, etc.
- `AchievementServiceTest.java:~500+ lines`

**Problem:** Test files contain massive storage implementations duplicating TestStorages.

**Fix:** Consolidate to centralized `TestStorages.*` classes.

---

### HIGH: TQ-003 — Tests Verify Mock State, Not Service Behavior

**File:** `DailyServiceTest.java:210,214-220`

```java
assertTrue(dailyPickViewStorage.isViewed(userId, LocalDate.now(fixedClock)));
```

Test verifies mock's internal state, not that service actually persisted data.

---

### HIGH: TQ-004 — "Cleanup" Test Doesn't Actually Verify Cleanup

**File:** `DailyServiceTest.java:214-220`

Test name says "removes old entries" but only verifies return value is 0, never adds old entries to verify deletion.

---

### HIGH: TQ-005 — Edge Case Regression Tests Document Bugs

**File:** `EdgeCaseRegressionTest.java:90-94,124-143`

Comments explicitly state "known limitation" — these are regression tests documenting bugs, not validating correct behavior.

---

### MEDIUM: TQ-006 through TQ-015 — Missing Edge Cases

- No null/empty boundary tests for age calculations
- No tests for exact boundary ages in filters
- No zero-distance edge case tests
- Empty collection edge cases not fully tested
- No concurrent modification tests
- Setup data not validated before test execution
- Incomplete mock implementations

---

### LOW: TQ-016 through TQ-028 — Test Parameter Handling, Vague Assertions

Various issues with vague assertions (e.g., `assertTrue(tier.equals("Bronze") || tier.equals("Silver"))`).

---

## CATEGORY 3: EXCEPTION HANDLING ISSUES (14 Issues)

### HIGH: EH-001 — DatabaseManager.isVersionApplied() Silent SQLException
**File:** `storage/DatabaseManager.java:363-369`

```java
} catch (SQLException _) {
    return false;  // Silent swallow - no logging, no context
}
```

Can't distinguish "table doesn't exist" from "actual query failure".

---

### HIGH: EH-002 — AppSession.notifyListeners() Generic Exception Swallow
**File:** `core/AppSession.java:64-74`

```java
} catch (Exception e) {
    LOGGER.warn("Listener threw exception: {}", e.getMessage());
    // Exception not rethrown; listener failure silently swallowed
}
```

Logs message only (no stack trace), continues to next listener.

---

### HIGH: EH-003 — NavigationService.navigateWithTransition() No User Notification
**File:** `ui/NavigationService.java:200-202`

IOException during FXML loading is caught but NOT shown to user. UI stays on old screen.

---

### HIGH: EH-004 — MatchingService.recordLike() Uncaught Exception in Fallback
**File:** `core/MatchingService.java:145-158`

```java
} catch (RuntimeException ex) {
    matchResult = matchStorage.get(match.getId())  // Can throw!
            .filter(existing -> existing.getState() == Match.State.ACTIVE);
}
```

If `matchStorage.get()` throws, exception escapes unhandled.

---

### MEDIUM: EH-005 through EH-010 — ConfigLoader Silent Catches

3 instances of IOException/NumberFormatException silently caught with log-only handling.

---

### MEDIUM: EH-011 — HTTP Routes Throwing Business Exceptions

`MatchRoutes.java`, `MessagingRoutes.java` throw `IllegalArgumentException` for validation failures — should return 400, not 500.

---

### MEDIUM: EH-012 — Inconsistent Result vs Exception Patterns

`MatchingService` uses 3 different patterns: Result objects, exceptions, and Optional.

---

### LOW: EH-013, EH-014 — Log Level Inconsistencies

Same error types logged at different levels across files.

---

## CATEGORY 4: SQL/STORAGE ISSUES (14 Issues)

### HIGH: SQL-001 — N+1 Query in getAllMatchesFor()
**File:** `storage/jdbi/JdbiMatchStorage.java:70-77`

```java
default List<Match> getActiveMatchesFor(UUID userId) {
    List<Match> fromA = getActiveMatchesForUserA(userId);  // Query 1
    List<Match> fromB = getActiveMatchesForUserB(userId);  // Query 2
    // ...
}
```

**Fix:** Single UNION query.

---

### HIGH: SQL-002 — No Transaction in MatchingService.recordLike()
**File:** `core/MatchingService.java:120-166`

4 queries without transaction boundary. Race condition for duplicate matches.

---

### HIGH: SQL-003 — Orphan Records Possible
**File:** `storage/DatabaseManager.java` (Schema)

When match ends, `conversations` and `profile_views` are not deleted/updated.

---

### MEDIUM: SQL-004 — SELECT * in 8 Locations

`JdbiBlockStorage.java:51`, `JdbiLikeStorage.java:30`, `JdbiMatchStorage.java:45,58,64,79,82`, `JdbiReportStorage.java:46`

---

### MEDIUM: SQL-005 — Missing Indexes

`conversations(user_a, user_b)`, `friend_requests(to_user_id, status)`, `messages(sender_id)`

---

### MEDIUM: SQL-006 — CSV Serialization Limits Query Capability

`interested_in`, `interests` stored as CSV — cannot filter at SQL level.

---

### MEDIUM: SQL-007 — getAllLatestUserStats() Subquery Inefficiency
**File:** `JdbiStatsStorage.java:84-97`

Uses nested subquery with GROUP BY + JOIN. Better: Window function or NOT EXISTS.

---

### MEDIUM: SQL-008 through SQL-011 — Mapping Issues

- Inconsistent null handling (`readInstant` vs `readInstantOptional`)
- Precision loss in Timestamp → Instant
- CSV parsing silently skips invalid enum values
- Timezone-naive Instant handling

---

### LOW: SQL-012 through SQL-014 — Minor inefficiencies

Unused column selection, soft delete inconsistency.

---

## CATEGORY 5: INTERFACE DESIGN ISSUES (16 Issues)

### HIGH: IF-001 — StatsStorage Fat Interface (5 unrelated concerns)
**File:** `core/storage/StatsStorage.java`

132 lines combining user stats, platform stats, profile views, achievements, cleanup.

**Fix:** Split into 5 focused interfaces.

---

### HIGH: IF-002 — MessagingStorage Mixed Abstraction Levels
**File:** `core/storage/MessagingStorage.java`

Conversation lifecycle + message querying + state updates all in one.

---

### HIGH: IF-003 — CLI Handlers No Common Interface
**Files:** 7 handler files in `app/cli/`

No shared `Handler` interface despite identical pattern (Dependencies record, constructor, runMenuLoop).

---

### HIGH: IF-004 — Service Result Types Inconsistent

`SendResult`, `UndoResult`, `CleanupResult`, `TransitionValidationException` — no shared pattern.

---

### HIGH: IF-005 — Optional vs Null Inconsistency Across Storage

`UserStorage.get()` returns null, `MatchStorage.get()` returns Optional.

---

### MEDIUM: IF-006 through IF-012

- SocialStorage mixed entity concerns
- UserStorage profile notes unrelated
- AchievementService 7 dependencies
- RelationshipTransitionService notification coupling
- SessionAggregates exposed in interface
- Default methods hide no-op behavior
- Ambiguous method names

---

### LOW: IF-013 through IF-016 — Contract clarity, documentation issues

---

## CATEGORY 6: NULL SAFETY/API CONTRACT ISSUES (26 Issues)

### HIGH: NS-001 — Missing @Nullable on 18 Methods

**Files:**
- `MapperHelper.java:30,49,58,67,87,96` — 6 methods
- `EnumMenu.java:48,58` — prompt() returns null
- `SafetyHandler.java:144,149,205,239,244` — 5 methods
- `ProfileHandler.java:390,395` — parseInterestIndex()
- `LoginViewModel.java:147,252,257` — 3 methods
- `ChatViewModel.java:280` — getCurrentUserId()

---

### MEDIUM: NS-002 — MapperHelper.readCsvAsList() Returns Mutable List
**File:** `storage/mapper/MapperHelper.java:108`

Returns `ArrayList`, callers can modify.

---

### MEDIUM: NS-003 — EnumMenu.prompt() vs promptMultiple() Inconsistency

`prompt()` returns null on invalid input; `promptMultiple()` returns empty EnumSet.

---

### MEDIUM: NS-004 — Missing Parameter Validation

`MapperHelper.readEnum()` doesn't validate `enumType` parameter.

---

### LOW: NS-005 through NS-026 — Various null handling, defensive copying gaps

---

## CATEGORY 7: MAGIC NUMBERS/CONSTANTS (60+ Issues)

### Animation & UI Timing (25 literals)

**Toast.java:** Duration.seconds(3,4,5), Duration.millis(200,300), Insets(30), translateY(50), HBox(12), iconSize(20), maxWidth(400)

**UiAnimations.java:** Duration.millis(50,100,150,200,400), scale factors (1.05, 1.1, 1.15), glow radius (15,25), spread (0.2-0.4), shake distance (-10,10), cycle count (6)

**ConfettiAnimation.java:** PARTICLE_COUNT(100), gravity(0.03), damping(0.99), rotation(360)

---

### Scoring Thresholds (15 literals)

**MatchQualityService.java:** Star rating boundaries (90,75,60,40), display limits (3), distance thresholds (5,15 km), age range (2 years), neutral score (0.5)

---

### Profile Completion Points (10 literals)

**ProfileCompletionService.java:** Name(5), Bio(10), BirthDate(5), Gender(5), InterestedIn(5), Photo(10), Interests(20)

---

### Cache Configuration (5 literals)

**ImageCache.java:** MAX_CACHE_SIZE(100), LinkedHashMap capacity(16), load factor(0.75f)

---

### Performance Thresholds (2 literals)

**PerformanceMonitor.java:** MAX_METRICS_SIZE(1000), slow threshold(100ms)

---

### Delimiter Strings (5 literals)

`","` CSV delimiter in 3 files, `"@"` cache key separator, `"x"` size separator

---

## Refactor Plan (High-Impact First)

### R-V3-001: Fix Critical Thread Safety (Week 1)
**Files:** DatabaseManager, MatchingViewModel, AppBootstrap, NavigationService, UndoService, DailyService
**Steps:**
1. Add volatile to 6 fields
2. Replace LinkedList with ConcurrentLinkedQueue
3. Use computeIfAbsent() for cache
**Effort:** 4h | **Risk:** Medium

### R-V3-002: Fix Transaction Boundaries (Week 1)
**Files:** MatchingService.java
**Steps:**
1. Wrap recordLike() in TransactionTemplate
2. Add integration test for concurrent likes
**Effort:** 2h | **Risk:** Medium

### R-V3-003: Fix Test Date-Dependence (Week 2)
**Files:** 7 test files
**Steps:**
1. Create TestClock utility
2. Replace LocalDate.now() with clock.today()
3. Add @BeforeEach setup validation
**Effort:** 3h | **Risk:** Low

### R-V3-004: Fix Swallowed Exceptions (Week 2)
**Files:** DatabaseManager, ConfigLoader, NavigationService, MatchingService
**Steps:**
1. Add specific SQLException handling
2. Add Toast for navigation failures
3. Wrap fallback queries in try-catch
**Effort:** 3h | **Risk:** Low

### R-V3-005: Fix N+1 Queries (Week 3)
**Files:** JdbiMatchStorage.java
**Steps:**
1. Replace dual query with UNION
2. Add missing indexes
**Effort:** 2h | **Risk:** Low

### R-V3-006: Split Fat Interfaces (Week 3-4)
**Files:** StatsStorage, MessagingStorage
**Steps:**
1. Create 5 focused interfaces from StatsStorage
2. Update all consumers
**Effort:** 4h | **Risk:** Medium

### R-V3-007: Create Constants Classes (Week 4)
**Steps:**
1. Create AnimationConstants with 25 values
2. Migrate scoring thresholds to AppConfig
3. Create CacheConstants
**Effort:** 4h | **Risk:** Low

### R-V3-008: Add @Nullable Annotations (Backlog)
**Files:** 10 files
**Steps:**
1. Add javax.annotation dependency
2. Annotate 18 methods
3. Run static analysis
**Effort:** 2h | **Risk:** Low

---

## High-Risk Architecture Warnings (NEW)

| Warning                                   | Files                             | Severity |
|-------------------------------------------|-----------------------------------|----------|
| Race condition: DatabaseManager lazy init | DatabaseManager.java:97-104       | CRITICAL |
| Race condition: Candidate queue access    | MatchingViewModel.java:39,149,164 | CRITICAL |
| Memory visibility: AppBootstrap fields    | AppBootstrap.java:14-16           | CRITICAL |
| No transaction: Like/match creation       | MatchingService.java:120-166      | CRITICAL |
| Silent exceptions: Schema version check   | DatabaseManager.java:363-369      | HIGH     |
| N+1 queries: Match loading                | JdbiMatchStorage.java:70-77       | HIGH     |
| Fat interface: 5 concerns combined        | StatsStorage.java                 | HIGH     |
| Date-dependent tests: 7 files             | *Test.java (7 files)              | HIGH     |

---

## Quick Wins (Low Risk, High Value)

| Action                         | Files                  | Effort | Impact                |
|--------------------------------|------------------------|--------|-----------------------|
| Make 6 fields volatile         | 4 files                | 30 min | Fix race conditions   |
| Add computeIfAbsent() to cache | DailyService.java      | 15 min | Fix race condition    |
| Add Toast for FXML errors      | NavigationService.java | 15 min | User-visible errors   |
| Replace dual query with UNION  | JdbiMatchStorage.java  | 30 min | Halve query count     |
| Add setup validation in tests  | 7 test files           | 1h     | Catch silent failures |

---

## Suggested Module Structure (Incremental to Previous)

Previous reports suggested splitting `core/` into sub-packages. This report adds:

```
core/
  storage/
    UserStatsStorage.java         # NEW: split from StatsStorage
    PlatformStatsStorage.java     # NEW: split from StatsStorage
    ProfileViewStorage.java       # NEW: split from StatsStorage
    UserAchievementStorage.java   # NEW: split from StatsStorage
    StatsCleanupStorage.java      # NEW: split from StatsStorage
  constants/
    AnimationConstants.java       # NEW: UI timing values
    ScoringConstants.java         # NEW: if not in AppConfig

app/
  Handler.java                    # NEW: common interface
  cli/
    ...existing handlers...
```

---

## Implementation Roadmap

| Phase     | Focus                                 | Effort  | Impact                    |
|-----------|---------------------------------------|---------|---------------------------|
| Week 1    | Thread safety fixes (6 critical)      | 6h      | Prevent race conditions   |
| Week 2    | Test reliability + exception handling | 6h      | Stable CI, visible errors |
| Week 3    | SQL optimization + interface splits   | 6h      | Performance, cleaner deps |
| Week 4    | Constants + null safety               | 6h      | Maintainability           |
| **Total** | **All NEW issues**                    | **24h** | **171 issues resolved**   |

---

*End of Architecture Audit Report v3 — NEW FINDINGS ONLY*

*Previous reports (summary.md, report.md, summaryByGPT.md, reportByGPT.md) address layer violations, god classes, logging duplication, and ViewModel→Storage bypasses.*
