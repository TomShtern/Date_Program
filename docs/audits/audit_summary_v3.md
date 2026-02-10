# Architecture Audit v3 — Summary (NEW FINDINGS ONLY)

**Date:** 2026-02-07 | **Auditor:** Claude Opus 4.6 | **Scope:** ~150 source files | **Focus:** Issues NOT in previous reports

---

## Executive Summary

This audit discovered **171 NEW issues** across 7 categories not previously identified in `summary.md`, `summaryByGPT.md`, `report.md`, or `reportByGPT.md`. While previous audits focused on layer violations, god classes, and duplicated logging helpers, this deep-dive uncovered critical thread safety bugs, test quality problems, and architectural gaps.

| Category                  | Issues Found | Severity Distribution          |
|---------------------------|--------------|--------------------------------|
| Thread Safety             | 13           | 6 CRITICAL, 4 HIGH, 3 MEDIUM   |
| Test Quality              | 28           | 12 HIGH, 10 MEDIUM, 6 LOW      |
| Exception Handling        | 14           | 4 HIGH, 7 MEDIUM, 3 LOW        |
| SQL/Storage               | 14           | 3 HIGH, 8 MEDIUM, 3 LOW        |
| Interface Design          | 16           | 5 HIGH, 8 MEDIUM, 3 LOW        |
| Null Safety/API Contracts | 26           | 8 HIGH, 12 MEDIUM, 6 LOW       |
| Magic Numbers/Constants   | 60+          | 0 CRITICAL, 20 MEDIUM, 40+ LOW |
| **TOTAL**                 | **171+**     | **6 CRITICAL, 57 HIGH**        |

---

## Top 8 Critical Findings (Fix Immediately)

### 1. Race Condition in DatabaseManager Lazy Initialization
**File:** `storage/DatabaseManager.java:97-104`
**Problem:** `initialized` field is not volatile; `getConnection()` has check-then-act race.
**Impact:** Multiple schema initializations, connection pool corruption.
**Fix:** Make `initialized` volatile, use double-checked locking with volatile `dataSource`.

### 2. Unsynchronized LinkedList in MatchingViewModel
**File:** `ui/viewmodel/MatchingViewModel.java:39,149-150`
**Problem:** `LinkedList<User> candidateQueue` accessed from virtual thread (writes) and FX thread (reads) without synchronization.
**Impact:** `ConcurrentModificationException`, wrong candidates displayed.
**Fix:** Replace with `ConcurrentLinkedQueue` or add explicit synchronization.

### 3. Non-Volatile AppBootstrap.services Field
**File:** `core/AppBootstrap.java:14-16,51`
**Problem:** `services` and `dbManager` not volatile; `getServices()` reads without synchronization.
**Impact:** Stale/null ServiceRegistry returned to callers.
**Fix:** Make both fields volatile.

### 4. No Transaction Boundary in MatchingService.recordLike()
**File:** `core/MatchingService.java:120-166`
**Problem:** Like save + mutual check + match creation spans 3-4 queries without transaction.
**Impact:** Duplicate matches, lost likes under concurrent load.
**Fix:** Wrap in `@Transactional` or use `TransactionTemplate`.

### 5. Tests Using LocalDate.now() — Date-Dependent Failures
**Files:** `DailyPickServiceTest.java:280`, `StandoutsServiceTest.java:48,68`, 5 other test files
**Problem:** `LocalDate.now().minusYears(age)` makes age calculations vary by run date.
**Impact:** Tests pass on some days, fail on others (especially around birthdays).
**Fix:** Inject fixed `Clock` for all date operations in tests.

### 6. Swallowed Exceptions in DatabaseManager.isVersionApplied()
**File:** `storage/DatabaseManager.java:363-369`
**Problem:** `SQLException` caught and returns `false` silently — can't distinguish "table missing" from "query failed".
**Impact:** Schema corruption masked as "version not applied".
**Fix:** Check SQL error code for missing table vs. other failures.

### 7. Fat Interface: StatsStorage Combines 5 Unrelated Concerns
**File:** `core/storage/StatsStorage.java`
**Problem:** Single interface for user stats, platform stats, profile views, achievements, and cleanup.
**Impact:** Every client depends on all 132 lines of methods it doesn't use.
**Fix:** Split into `UserStatsStorage`, `PlatformStatsStorage`, `ProfileViewStorage`, `UserAchievementStorage`, `StatsCleanupStorage`.

### 8. N+1 Query in getAllMatchesFor()
**File:** `storage/jdbi/JdbiMatchStorage.java:70-77`
**Problem:** Method executes 2 separate queries (`user_a`, `user_b`) instead of UNION.
**Impact:** 2 extra DB round-trips for every liker browser load.
**Fix:** Replace with single UNION query.

---

## Prioritized Action List

### Do First (Week 1) — Critical Thread Safety
| Action                                                    | Impact                 | Effort | Risk   |
|-----------------------------------------------------------|------------------------|--------|--------|
| Make `DatabaseManager.initialized` volatile               | Prevent race condition | 15 min | Low    |
| Add synchronization to `MatchingViewModel.candidateQueue` | Fix concurrent access  | 30 min | Low    |
| Make `AppBootstrap.services/dbManager` volatile           | Fix visibility         | 15 min | Low    |
| Wrap `recordLike()` in transaction                        | Fix race condition     | 1h     | Medium |

### Do Next (Week 2) — Test Reliability
| Action                                                  | Impact                               | Effort | Risk |
|---------------------------------------------------------|--------------------------------------|--------|------|
| Replace `LocalDate.now()` with fixed Clock in all tests | Eliminate date-dependent failures    | 2h     | Low  |
| Add setup validation assertions in test @BeforeEach     | Catch silent setup failures          | 1h     | Low  |
| Consolidate inline storage mocks to TestStorages        | Reduce 250+ LOC boilerplate per test | 3h     | Low  |

### Do Later (Week 3-4) — Exception & SQL
| Action                                                     | Impact                      | Effort | Risk   |
|------------------------------------------------------------|-----------------------------|--------|--------|
| Fix swallowed exceptions in DatabaseManager, ConfigLoader  | Visible error handling      | 2h     | Medium |
| Add Toast notification for NavigationService FXML failures | User-visible errors         | 30 min | Low    |
| Replace `getAllMatchesFor()` with UNION query              | Halve liker browser queries | 1h     | Low    |
| Split `StatsStorage` into 5 interfaces                     | Cleaner dependencies        | 3h     | Medium |

### Backlog (Month 2) — Constants & Contracts
| Action                                                | Impact                  | Effort | Risk   |
|-------------------------------------------------------|-------------------------|--------|--------|
| Create `AnimationConstants` class (50+ magic numbers) | Configurable UI timing  | 2h     | Low    |
| Migrate scoring thresholds to AppConfig               | Configurable scoring    | 2h     | Medium |
| Add @Nullable annotations (18 methods)                | Static analysis support | 1h     | Low    |
| Standardize Optional vs null in storage interfaces    | API consistency         | 4h     | Medium |

---

## Root Causes (NEW Insights)

1. **No concurrency testing** — Thread safety bugs survived because no concurrent stress tests exist
2. **Date-naive test fixtures** — `LocalDate.now()` used 7 times in test helpers instead of injected Clock
3. **Exception handling by-copy** — Developers copied catch-and-log patterns without rethinking semantics
4. **Interface design by accretion** — Storage interfaces grew methods without ISP consideration
5. **Constants scattered for convenience** — Animation/scoring values hardcoded where needed instead of centralized
6. **Result pattern inconsistently applied** — Some services use `*Result` records, others throw, others return null

## Prevention Recommendations

1. **Add ArchUnit tests for thread safety** — Ban mutable non-volatile shared state in specified packages
2. **Require Clock injection** — PMD custom rule: flag `LocalDate.now()`, `Instant.now()` without Clock
3. **Standardize Result pattern** — Document in CLAUDE.md; lint for exceptions in service layer
4. **Interface size limits** — Flag interfaces with >10 methods for review
5. **Create constants review checklist** — Review for magic literals in PRs

---

*See [audit_report_v3.md](audit_report_v3.md) for full file-by-file analysis, issue details, and implementation guidance.*
