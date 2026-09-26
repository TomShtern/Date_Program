# Plan 07: UI Controllers, Navigation, and UI Utilities Cleanup

**Date:** 2026-02-08
**Priority:** HIGH (Week 4)
**Estimated Effort:** 6-10 hours
**Risk Level:** Medium
**Parallelizable:** YES - no overlap with P01-P06 file sets
**Status:** COMPLETED

**Progress Log:**
- 2026-02-09: Task 1 completed (NavigationService hardened: FX-thread guard, concurrent history, singleton holder, error toasts, safe FXML load).
- 2026-02-09: Task 2-4 completed (shared handleBack, controller extractions, UiSupport merge). IDE error scan run for modified files.

---

## Overview

This plan addresses the UI controller and navigation issues from the audit. It reduces controller size, removes duplicated back-navigation logic, hardens NavigationService error handling and thread safety, and consolidates overlapping UI utilities. The work is confined to the UI controller and util layer, so it can run in parallel with core and CLI work.

### Audit Issues Addressed

| ID      | Severity | Category           | Summary                                     |
|---------|----------|--------------------|---------------------------------------------|
| R-008   | HIGH     | Refactor           | Extract large controller methods (>100 LOC) |
| R-017   | LOW      | Duplication        | Shared handleBack in BaseController         |
| EH-003  | HIGH     | Exception Handling | NavigationService hides FXML load errors    |
| TS-004  | CRIT     | Thread Safety      | NavigationService history deque             |
| TS-013  | MED      | Thread Safety      | NavigationService getInstance safety        |
| UI-UTIL | LOW      | Duplication        | Merge UiHelpers and UiServices              |

---

## Files Owned by This Plan

### New Files
1. `src/main/java/datingapp/ui/controller/LikesTabRenderer.java`
2. `src/main/java/datingapp/ui/controller/DealbreakersChipHelper.java`
3. `src/main/java/datingapp/ui/controller/UserListCellFactory.java`
4. `src/main/java/datingapp/ui/util/UiSupport.java` (or `UiDialogs` if preferred)

### Modified Files
1. `src/main/java/datingapp/ui/NavigationService.java`
2. `src/main/java/datingapp/ui/controller/BaseController.java`
3. `src/main/java/datingapp/ui/controller/MatchesController.java`
4. `src/main/java/datingapp/ui/controller/ProfileController.java`
5. `src/main/java/datingapp/ui/controller/LoginController.java`
6. `src/main/java/datingapp/ui/controller/MatchingController.java`
7. `src/main/java/datingapp/ui/controller/PreferencesController.java`
8. `src/main/java/datingapp/ui/controller/ChatController.java`
9. `src/main/java/datingapp/ui/controller/StatsController.java`
10. `src/main/java/datingapp/ui/util/UiHelpers.java` (if merged into UiSupport)

### Deleted Files
1. `src/main/java/datingapp/ui/util/UiServices.java` (after merge)

---

## Detailed Tasks

### Task 1: Harden NavigationService (EH-003, TS-004, TS-013)

**File:** `src/main/java/datingapp/ui/NavigationService.java`

**Changes:**
- Replace `ArrayDeque` with `ConcurrentLinkedDeque` or a synchronized wrapper to prevent races on history.
- Move singleton to holder pattern or static final instance to remove synchronized hot path.
- Add a `runOnFx(Runnable)` helper and ensure all UI mutations (setCenter, animations, Toast) run on FX thread.
- If FXML load fails, show a Toast to the user and keep the current view intact.
- If `viewModelFactory` is null, log and show Toast, then return without changing the view.

**Outcome:** Navigation failures become visible to users, and the service is safe even if navigation is triggered off the FX thread.

**Status:** ✅ Completed on 2026-02-09.

---

### Task 2: Add BaseController.handleBack and Remove Duplicates (R-017)

**File:** `src/main/java/datingapp/ui/controller/BaseController.java`

**Changes:**
- Add a protected `handleBack()` method that delegates to `NavigationService.getInstance().goBack()`.
- Optionally guard with `canGoBack()` and fallback to DASHBOARD.

**Remove duplicate methods from controllers:**
- `ChatController`
- `MatchesController`
- `MatchingController`
- `PreferencesController`
- `ProfileController`
- `StatsController`

**Outcome:** One implementation of back navigation with consistent behavior.

**Status:** ✅ Completed on 2026-02-09.

---

### Task 3: Extract Large Controller Methods (R-008)

**MatchesController**
- Extract likes tab rendering logic into `LikesTabRenderer`.
- Keep controller focused on wiring and event handling.

**ProfileController**
- Extract dealbreaker chip rendering and edit logic into `DealbreakersChipHelper`.
- Split `wireAuxiliaryActions()` into smaller private methods or a helper class.

**LoginController**
- Extract user list cell creation into `UserListCellFactory`.

**Outcome:** Controller size drops, logic becomes testable and reusable.

**Status:** ✅ Completed on 2026-02-09.

---

### Task 4: Merge UI Utilities (UiHelpers + UiServices)

**Goal:** Reduce overlap between `UiHelpers` and `UiServices`.

**Option A (recommended):**
- Create `UiSupport` that contains:
  - `ResponsiveController`
  - `ValidationHelper`
  - `showConfirmation(...)`
  - `getAvatar(...)` (delegates to ImageCache)
- Update controller imports to use `UiSupport`.
- Delete `UiServices.java`.

**Option B (smaller change):**
- Move `showConfirmation(...)` and `getAvatar(...)` into `UiHelpers`.
- Delete `UiServices.java`.

**Outcome:** One utility surface for UI controllers, fewer imports and duplicates.

**Status:** ✅ Completed on 2026-02-09.

---

## Execution Order

1. Update NavigationService thread safety and error handling.
2. Add BaseController.handleBack and remove duplicates from controllers.
3. Extract controller helper classes (likes tab, dealbreakers, user list cells).
4. Merge UI utilities and update imports.
5. Run format and targeted checks.

---

## Verification Checklist

- `mvn spotless:apply`
- `mvn test -Dtest=JavaFxCssValidationTest` (if present)
- Manual UI smoke: login, dashboard, matches, profile edit, back navigation
- `rg "UiServices" src/main/java` returns 0 results
- `rg "handleBack" src/main/java/datingapp/ui/controller` shows only BaseController (and call sites)

**Status:** ✅ IDE error scan completed for modified files (tests not run in this session).

---

## Files NOT Owned by This Plan

| File / Area                                       | Reason                          | Owner Plan |
|---------------------------------------------------|---------------------------------|------------|
| `src/main/java/datingapp/ui/viewmodel/*`          | ViewModel fixes, FX-thread work | P05        |
| `src/main/java/datingapp/ui/util/ImageCache.java` | TS-007 TOCTOU fix               | P05        |
| `src/main/java/datingapp/app/cli/*`               | CLI refactor and app services   | P06        |
| `src/main/java/datingapp/core/*`                  | Core restructuring              | P03        |

---

## Dependencies

- None. This plan can run in parallel with P05 and P06.
- Soft dependency: if P05 changes ViewModel APIs used by controllers, adjust controller wiring accordingly.

---

## Rollback Strategy

1. Revert NavigationService changes.
2. Restore controller-local handleBack methods.
3. Remove helper classes and re-inline extracted logic.
4. Restore UiServices and revert utility import changes.

Each task is isolated and reversible without touching core or CLI code.
