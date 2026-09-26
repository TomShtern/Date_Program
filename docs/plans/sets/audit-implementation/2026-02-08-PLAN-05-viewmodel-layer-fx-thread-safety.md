# Plan 05: ViewModel Layer Fixes and FX Thread Safety

**Date:** 2026-02-08
**Priority:** HIGH (Week 3)
**Estimated Effort:** 6-9 hours
**Risk Level:** Medium
**Parallelizable:** YES - no overlap with P01-P04 or P06-P07 file sets
**Status:** ✅ COMPLETED ✅ - implemented 2026-02-09 ✅

---

## Overview

This plan fixes the UI ViewModel layer problems from the audit: storage imports in ViewModels, FX thread blocking, and several concurrency defects. The work stays inside the UI layer plus small UI-only adapters, so it can run in parallel with core and CLI refactors.

### Audit Issues Addressed

| ID     | Severity | Category      | Summary                                |
|--------|----------|---------------|----------------------------------------|
| R-001  | HIGH     | Architecture  | ViewModel -> storage imports (6 files) |
| R-002  | HIGH     | Performance   | FX thread DB queries in ViewModels     |
| TS-002 | CRIT     | Thread Safety | MatchingViewModel candidateQueue       |
| TS-007 | HIGH     | Thread Safety | ImageCache.preload TOCTOU              |
| TS-009 | HIGH     | Thread Safety | ChatViewModel observable list updates  |
| TS-011 | MED      | Thread Safety | ViewModelFactory sync                  |

---

## Files Owned by This Plan

### New Files
1. `src/main/java/datingapp/ui/viewmodel/data/UiUserStore.java`
2. `src/main/java/datingapp/ui/viewmodel/data/StorageUiUserStore.java`
3. `src/main/java/datingapp/ui/viewmodel/data/UiMatchDataAccess.java`
4. `src/main/java/datingapp/ui/viewmodel/data/StorageUiMatchDataAccess.java`

### Modified Files
1. `src/main/java/datingapp/ui/ViewModelFactory.java`
2. `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
3. `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
4. `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
5. `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
6. `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
7. `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
8. `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
9. `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
10. `src/main/java/datingapp/ui/util/ImageCache.java`

### Test Files
1. `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java` (update constructor and fixtures)

---

## Detailed Tasks

### Task 1: Add UI Data Adapters to Remove Storage Imports

**Goal:** ViewModels must not import `datingapp.core.storage.*`.

**New interfaces and implementations:**
1. `UiUserStore` for user reads and writes required by Login, Profile, and Preferences.
2. `UiMatchDataAccess` for match and like retrieval used by Matches and Dashboard.
3. Implementations use existing storages and are constructed in `ViewModelFactory`.

**Suggested methods (minimal surface):**
- `UiUserStore`
  - `List<User> findAll()`
  - `void save(User user)`
  - `Map<UUID, User> findByIds(Set<UUID> ids)`
- `UiMatchDataAccess`
  - `List<Match> getActiveMatches(UUID userId)`
  - `Set<UUID> getBlockedUserIds(UUID userId)`
  - `Set<UUID> getLikedOrPassedUserIds(UUID userId)`
  - `Optional<Like> getLike(UUID fromUserId, UUID toUserId)`

**Note:** These are UI-layer adapters only. They do not change core services or ServiceRegistry.

---

### Task 2: Remove Storage Imports From ViewModels

**LoginViewModel**
- Replace `UserStorage` with `UiUserStore`.
- Keep current behavior (load users, save new users).

**ProfileViewModel**
- Replace `UserStorage` with `UiUserStore`.
- Ensure photo save and profile save still write via `UiUserStore`.

**PreferencesViewModel**
- Replace `UserStorage` with `UiUserStore`.

**MatchesViewModel**
- Replace `MatchStorage`, `UserStorage`, `LikeStorage`, and `BlockStorage` with `UiMatchDataAccess` and `UiUserStore`.
- Keep `MatchingService` and `DailyService` dependencies.
- All storage calls should flow through adapters.

**StatsViewModel**
- Replace `LikeStorage` and `MatchStorage` with `StatsService`.
- Use `StatsService.getOrComputeStats(userId)` to derive likes and match counts.

**DashboardViewModel**
- Replace `MatchStorage` with `UiMatchDataAccess` and use `getActiveMatches(userId).size()`.

**ViewModelFactory**
- Construct adapters using ServiceRegistry storages and pass adapters to ViewModels.
- Ensure no ViewModel constructor needs storage types anymore.

---

### Task 3: Fix FX Thread Blocking and Loading State

**Rule:** All storage or service calls that may block must run off the FX thread. UI updates must run on the FX thread.

**Apply to ViewModels:**
- LoginViewModel, MatchesViewModel, StatsViewModel, DashboardViewModel.
- Verify `loading.set(true)` and `loading.set(false)` are always called on FX thread.
- If a ViewModel saves data synchronously (ProfileViewModel, PreferencesViewModel), move the save into a background thread and marshal UI updates via `Platform.runLater`.

**Reference pattern:**
- Set `loading` on FX thread before background work.
- Perform storage work on a virtual thread.
- Update observable state on FX thread.
- Clear `loading` on FX thread after updates.

---

### Task 4: Thread Safety Fixes

**MatchingViewModel (TS-002)**
- Replace `LinkedList` with `ConcurrentLinkedQueue` or ensure all queue access is strictly on FX thread.
- If using `ConcurrentLinkedQueue`, document that it is used to avoid cross-thread races during refresh.

**ChatViewModel (TS-009)**
- Ensure all updates to `conversations` and `activeMessages` occur on the FX thread.
- Wrap any `setAll`, `clear`, or `addAll` in `Platform.runLater` or a shared `runOnFx` helper.

**ViewModelFactory (TS-011)**
- Make ViewModel lazy initialization thread-safe.
- Use a private lock or `synchronized` blocks around getters to avoid double creation.

**ImageCache.preload (TS-007)**
- Remove the check-then-act pattern.
- Always call `getImage` inside the background thread so the cache synchronization is authoritative.

---

### Task 5: Update Tests

- Update `MatchesViewModelTest` to use the new constructor with adapters.
- Provide adapter stubs backed by `TestStorages`.
- Verify at least one test covers the adapter path for received and sent likes.

---

## Execution Order

1. Add UI data adapter interfaces and implementations.
2. Update ViewModelFactory to construct adapters.
3. Update ViewModels to accept adapters and remove storage imports.
4. Fix FX thread and loading state patterns.
5. Apply thread safety fixes in MatchingViewModel, ChatViewModel, ViewModelFactory, and ImageCache.
6. Update tests.

---

## Verification Checklist

- `mvn spotless:apply`
- `mvn test -Dtest=MatchesViewModelTest`
- `rg "core.storage" src/main/java/datingapp/ui/viewmodel` returns 0 results
- Manual UI smoke: login, dashboard refresh, matches list, chat load

---

## Files NOT Owned by This Plan

| File / Area                                      | Reason                         | Owner Plan            |
|--------------------------------------------------|--------------------------------|-----------------------|
| `src/main/java/datingapp/core/*`                 | Core refactors and DI changes  | P03                   |
| `src/main/java/datingapp/app/cli/*`              | CLI refactors and app services | P06                   |
| `src/main/java/datingapp/ui/controller/*`        | Controller decomposition       | P07                   |
| `src/main/java/datingapp/ui/util/UiHelpers.java` | Utility merges                 | P07                   |
| `src/main/java/datingapp/core/storage/*`         | Interface splits               | Future plan if needed |

---

## Dependencies

- None. This plan is self-contained in the UI layer.

---

## Rollback Strategy

1. Revert UI adapter classes.
2. Restore original ViewModel constructors and storage imports.
3. Revert thread safety changes in MatchingViewModel, ChatViewModel, ViewModelFactory, and ImageCache.

Each change is isolated and reversible without touching core or CLI modules.
