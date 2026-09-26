# Plan 08: Tests, Constants, and Null Safety

**Date:** 2026-02-08
**Priority:** MEDIUM-HIGH (Week 5-6)
**Estimated Effort:** 6-10 hours
**Risk Level:** Medium
**Parallelizable:** NO - run after P03, P05, P06, and P07 to avoid file overlap
**Status:** COMPLETED

---

## Overview

This plan finishes the audit by stabilizing tests, adding null-safety annotations, and extracting remaining magic numbers into shared constants. It is intentionally scheduled last because it touches files already covered by earlier plans.

### Audit Issues Addressed

| ID               | Severity | Category        | Summary                                               |
|------------------|----------|-----------------|-------------------------------------------------------|
| TQ-001 to TQ-028 | HIGH-LOW | Test Quality    | Date dependence, missing edge cases, vague assertions |
| NS-001           | HIGH     | Null Safety     | Missing @Nullable on 18 methods                       |
| Magic Numbers    | MED      | Maintainability | 60+ literals in UI and scoring code                   |

---

## Files Owned by This Plan

### New Files
1. `src/test/java/datingapp/core/testutil/TestClock.java`
2. `src/main/java/datingapp/core/constants/ScoringConstants.java`
3. `src/main/java/datingapp/ui/constants/AnimationConstants.java`
4. `src/main/java/datingapp/ui/constants/CacheConstants.java`

### Modified Files (production)
1. `pom.xml` (add nullable annotations dependency)
2. `src/main/java/datingapp/storage/mapper/MapperHelper.java` (add @Nullable)
3. `src/main/java/datingapp/app/cli/EnumMenu.java` (add @Nullable)
4. `src/main/java/datingapp/app/cli/SafetyHandler.java` (add @Nullable)
5. `src/main/java/datingapp/app/cli/ProfileHandler.java` (add @Nullable)
6. `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` (add @Nullable)
7. `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` (add @Nullable)
8. `src/main/java/datingapp/core/User.java` (use AppClock for age calculation)
9. `src/main/java/datingapp/core/SessionService.java` (use AppClock.today instead of LocalDate.now)
10. `src/main/java/datingapp/core/StandoutsService.java` (use AppClock.today)
11. `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` (use AppClock.today)
12. `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` (use AppClock.today)
13. `src/main/java/datingapp/core/MatchQualityService.java` (replace numeric thresholds)
14. `src/main/java/datingapp/core/ProfileCompletionService.java` (replace numeric thresholds)
15. `src/main/java/datingapp/ui/util/UiAnimations.java` (use AnimationConstants)
16. `src/main/java/datingapp/ui/component/UiComponents.java` (use AnimationConstants)
17. `src/main/java/datingapp/ui/util/Toast.java` (use AnimationConstants)
18. `src/main/java/datingapp/ui/util/ImageCache.java` (use CacheConstants)

### Modified Files (tests)
- All tests that call `Instant.now()` or `LocalDate.now()` directly, including:
  - `src/test/java/datingapp/core/UserTest.java`
  - `src/test/java/datingapp/core/StandoutsServiceTest.java`
  - `src/test/java/datingapp/core/DailyPickServiceTest.java`
  - `src/test/java/datingapp/core/DailyLimitServiceTest.java`
  - `src/test/java/datingapp/core/SwipeSessionTest.java`
  - `src/test/java/datingapp/core/MatchQualityServiceTest.java`
  - `src/test/java/datingapp/core/MatchingServiceTest.java`
  - `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`
  - Any other files surfaced by `rg "LocalDate\\.now|Instant\\.now" src/test/java`

---

## Detailed Tasks

### Task 1: Add Nullable Annotations (NS-001)

**Goal:** Explicitly annotate nullable return values and parameters for better static analysis and clearer contracts.

**Steps:**
1. Add dependency in `pom.xml`:
   - Recommended: `org.jetbrains:annotations` (compile scope).
   - Alternative: `jakarta.annotation:jakarta.annotation-api` if preferred.
2. Annotate the 18 methods listed in the audit report, including:
   - `MapperHelper` null-returning read helpers.
   - `EnumMenu` prompt methods that can return null.
   - CLI parsing helpers in `SafetyHandler` and `ProfileHandler`.
   - UI getters in `LoginViewModel` and `ChatViewModel`.

**Note:** Apply annotations after P05 and P06 to avoid merge conflicts.

**Status: COMPLETE**
- Added `org.jetbrains:annotations` dependency in `pom.xml`.
- Annotated nullable return values in `MapperHelper`, `SafetyHandler`, `ProfileHandler`, `LoginViewModel`, and `ChatViewModel`.
- `EnumMenu` prompt methods already return non-null `Optional`/`Set` and `InputReader` returns empty strings, so no @Nullable annotations were applied there.

---

### Task 2: Stabilize Time-Dependent Tests (TQ-001)

**Goal:** Remove reliance on the system clock.

**Steps:**
1. Add `TestClock` helper:
   - `setFixed(Instant)` and `reset()` wrappers around `AppClock`.
2. Update production code that still calls `LocalDate.now()` or `Instant.now()` directly to use `AppClock`:
   - `User.getAge()` should use `AppClock.today(timezone)`.
   - `SessionService`, `StandoutsService`, and UI ViewModels should use `AppClock.today()`.
3. Update tests to call `TestClock.setFixed(...)` in `@BeforeEach` and `TestClock.reset()` in `@AfterEach`.
4. Replace direct `Instant.now()` and `LocalDate.now()` in tests with `AppClock.now()` and `AppClock.today()`.

**Status: COMPLETE**
- Added `TestClock` helper in test utilities.
- Replaced production time sources with `AppClock` in `User`, `SessionService`, `StandoutsService`, `LoginViewModel`, and `ProfileViewModel`.
- Updated all test usages of `Instant.now()`/`LocalDate.now()` to `AppClock` and wrapped tests with `TestClock.setFixed()`/`reset()`.

---

### Task 3: Extract Magic Numbers into Constants

**Goal:** Replace hard-coded values with named constants.

**Constants to add:**
- `ScoringConstants` for match quality and completion thresholds.
- `AnimationConstants` for UI timing values (fade, slide, toast durations).
- `CacheConstants` for cache sizes and TTLs in UI utilities.

**Update target files:**
- `MatchQualityService`, `ProfileCompletionService`.
- `UiAnimations`, `UiComponents`, `Toast`, `ImageCache`.

**Status: COMPLETE**
- Added `ScoringConstants`, `AnimationConstants`, and `CacheConstants`.
- Replaced match-quality and profile-completion literals with `ScoringConstants`.
- Replaced UI timing/motion literals with `AnimationConstants` and cache defaults with `CacheConstants`.

---

### Task 4: Expand Test Coverage (TQ-006 to TQ-028)

**Goal:** Add missing edge cases and tighten assertions.

**Examples (non-exhaustive):**
- Boundary values for age, distance, and height validation.
- Empty collections and null input handling.
- State transition invalid paths (User and Match).
- Concurrency edge cases for session tracking and undo expiration.
- Replace vague assertions with exact expected values.

**Note:** Use `TestStorages` for mocks and keep tests deterministic.

**Status: COMPLETE**
- Added undo expiry boundary coverage in `UndoServiceTest`.
- Added suspicious swipe velocity warning coverage and strengthened blocked-reason assertions in `SessionServiceTest`.
- Added highlight cap coverage in `MatchQualityServiceTest`.

---

## Execution Order

1. Add nullable annotations dependency.
2. Add `TestClock` and update production time sources to use `AppClock`.
3. Update time-dependent tests to fixed clock.
4. Add constants classes and replace literals.
5. Add missing edge-case tests.

---

## Verification Checklist

- `mvn spotless:apply`
- `mvn test`
- `rg "LocalDate\\.now|Instant\\.now" src/test/java` returns 0 (or only deliberate cases)
- `rg "@Nullable" src/main/java` includes all audited methods

---

## Dependencies

- Must run after P03, P05, P06, and P07 due to shared files.

---

## Rollback Strategy

1. Remove new constants classes and revert literal replacements.
2. Remove nullable annotations and dependency.
3. Revert time source changes and restore test usage of `now()`.

All changes are isolated and can be reverted by file.
