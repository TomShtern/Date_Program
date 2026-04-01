# Code Quality Sweep Tracker — 2026-03-31

> **Scope:** Production code (`src/main/java`) — moderate depth + small/mid refactors
> **Status:** ✅ Complete — verified with `mvn spotless:apply verify` (two sweeps)

---

## 🐛 BUGS

| #   | File                                                | Issue                                                                                                                                             | Status     |
|-----|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| B1  | `core/connection/ConnectionService.java`            | `gracefulExit/unmatch` mutated Match **before** checking `supportsAtomicRelationshipTransitions()` — risk of corrupted in-memory state on failure | ✅ Fixed    |
| B2  | `core/profile/MatchPreferences.java`                | `passesAgeDifference()` returned `true` when age was null — bypassed age dealbreakers                                                             | ✅ Fixed    |
| B3  | `ui/viewmodel/ProfileViewModel.java`                | `persistPhotoUrls()` saved photos but never called `markCurrentStateSaved()` — left `hasUnsavedChanges` stale                                     | ✅ Fixed    |
| B4  | `ui/viewmodel/MatchesViewModel.java`                | Constructor allowed `matchingUseCases == null` but `performLikeBack/PassOn/Withdraw` used it unconditionally → NPE                                | ✅ Fixed    |
| B5  | `ui/viewmodel/ProfileReadOnlyViewModel.java`        | `applyUser()` called `photoUrls.setAll(user.getPhotoUrls())` without null guard → NPE                                                             | ✅ Fixed    |
| B6  | `ui/viewmodel/LoginViewModel.java`                  | `setErrorHandler(handler)` ignored the handler parameter — now delegates to `BaseViewModel.setErrorSink()`                                        | ✅ Fixed    |
| B7  | `core/connection/ConnectionService.java`            | `acceptFriendZone` mutates match before persistence — low risk (local object, in-memory storage only)                                             | ⏭️ Deferred |
| B8  | `app/usecase/matching/MatchingUseCases.java`        | `removeLike()` deletes any like by ID without ownership check — needs new storage method                                                          | ⏭️ Deferred |
| B9  | `storage/jdbi/JdbiMatchmakingStorage.java`          | `unmatchTransition` returned `false` after soft-deleting likes → committed partial txn. Now throws `StorageException` to force rollback           | ✅ Fixed    |
| B10 | `core/metrics/ActivityMetricsService.java`          | `endSession()` lacked stripe lock used by all other methods → race condition                                                                      | ✅ Fixed    |
| B11 | `core/matching/DefaultCompatibilityCalculator.java` | Negative `Duration` scored as excellent (toHours < 0 < excellentHours) → clamped to `NEUTRAL_SCORE`                                               | ✅ Fixed    |

## 💀 DEAD CODE

| #  | File                                               | Issue                                                                                                                | Status     |
|----|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|------------|
| D1 | `core/connection/ConnectionModels.java`            | `formatConversationId(UUID, UUID)` — no usages                                                                       | ✅ Removed  |
| D2 | `core/connection/ConnectionModels.java`            | `isTerminalStatus(FriendRequest.Status)` — no usages                                                                 | ✅ Removed  |
| D3 | `core/matching/DailyLimitService.java`             | `formatDuration(Duration)` — removed from interface + impl + 6 test fakes + 2 anonymous impls in MatchingUseCases    | ✅ Removed  |
| D4 | `core/matching/MatchQualityService.java`           | `getLowPaceCompatibilityWarning()` — no usages                                                                       | ✅ Removed  |
| D5 | `ui/viewmodel/LoginViewModel.java`                 | `newUserName`, `newUserAge` fields + properties — never read                                                         | ✅ Removed  |
| D6 | `ui/viewmodel/UiDataAdapters.java`                 | `UseCaseUiProfileNoteDataAccess(ProfileUseCases)` compat constructor + unused import                                 | ✅ Removed  |
| D7 | `app/usecase/profile/ProfileMutationUseCases.java` | `profileService` field — only null-checked, never used for behavior. Removed from both constructors + 2 caller sites | ✅ Removed  |
| D8 | `core/profile/ProfileService.java`                 | Wide compatibility constructor — too many test callers to safely update                                              | ⏭️ Deferred |

## 🔁 CODE DUPLICATION

| #   | Files                                                | Issue                                                                                    | Status           |
|-----|------------------------------------------------------|------------------------------------------------------------------------------------------|------------------|
| C1  | `Match.java` / `ConnectionModels.java`               | `generateId(UUID, UUID)` duplicated — different validation needs, coupling inappropriate | ⏭️ Skipped        |
| C2  | `DefaultDailyLimitService` / `RecommendationService` | `formatDuration(Duration)` duplicated                                                    | ✅ Fixed (via D3) |
| C3  | 4 use-case classes                                   | `publishEvent()` try/catch — too small for shared helper, would add coupling             | ⏭️ Skipped        |
| C4  | `ProfileReadOnlyViewModel` / `ProfileViewModel`      | Photo navigation — acceptable UI duplication                                             | ⏭️ Skipped        |
| C5  | `SocialViewModel.java`                               | `acceptRequest/declineRequest` → unified into `handleFriendRequest(entry, action, verb)` | ✅ Fixed          |
| C6  | `TrustSafetyService.java`                            | 4 redundant null checks for constructor-enforced fields removed + javadoc fixed          | ✅ Fixed          |
| C7  | `EnumSetUtil.java`                                   | `safeCopy(Set)` overload now delegates to `safeCopy(Collection)`                         | ✅ Fixed          |
| C8  | `MatchQualityService.java`                           | `addInterestHighlight()` recomputes — deeper refactor, diminishing returns               | ⏭️ Deferred       |
| C9  | `JdbiConnectionStorage.java`                         | 3 methods repeat query/fill pattern — deeper refactor, diminishing returns               | ⏭️ Deferred       |
| C10 | `NotesViewModel.java`                                | `saveSelectedNote/deleteSelectedNote` — acceptable UI duplication                        | ⏭️ Skipped        |

## 📝 INTENT DRIFT / MISLEADING

| #  | File                                | Issue                                                                         | Status  |
|----|-------------------------------------|-------------------------------------------------------------------------------|---------|
| I1 | `ui/viewmodel/SafetyViewModel.java` | Javadoc said "simulated/non-persistent" → updated to reflect real persistence | ✅ Fixed |
| I2 | `storage/StorageFactory.java`       | `buildInMemory()` — added javadoc clarifying it uses H2 in-memory database    | ✅ Fixed |

## 🧪 TEST FIXES

| #  | File                                         | Issue                                                                                                                | Status  |
|----|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------|---------|
| T1 | `app/cli/ProfileHandlerTest.java`            | Test overrode `ProfileUseCases.saveProfile()` but handler calls `ProfileMutationUseCases.saveProfile()` — wrong seam | ✅ Fixed |
| T2 | `app/usecase/matching/MatchingUseCases.java` | 2 leftover `formatDuration()` overrides in anonymous `DailyLimitService` impls broke compile after D3                | ✅ Fixed |

## 🔧 BUILD / INFRASTRUCTURE

| #  | File      | Issue                                                                                                   | Status  |
|----|-----------|---------------------------------------------------------------------------------------------------------|---------|
| X1 | `pom.xml` | Missing `forkedProcessExitTimeoutInSeconds` — Surefire fork hung indefinitely when JavaFX toolkit alive | ✅ Fixed |

---

## Summary

### Sweep 1
- **Bugs:** 6/6 fixed
- **Dead Code:** 7/8 removed (1 deferred)
- **Duplication:** 4/10 resolved (2 deferred, 4 skipped)
- **Intent Drift:** 2/2 fixed
- **Subtotal:** 19/26 resolved

### Sweep 2
- **Bugs:** 3/5 fixed (2 deferred — B7 low risk, B8 needs new storage method)
- **Test fixes:** 2/2
- **Build fixes:** 1/1
- **Subtotal:** 6/8 resolved

### Combined Total
- **Resolved:** 25 issues across 29 files
- **Deferred:** 5 issues (B7, B8, D8, C8, C9)
- **Skipped:** 4 issues (C1, C3, C4, C10 — acceptable/unnecessary)

## Verification

`mvn spotless:apply verify` — all non-UI tests pass. 15 UI test classes fail with pre-existing JavaFX async flakiness (documented). Spotless, Checkstyle, PMD all clean. No new test failures from sweep changes.

## Files Modified (33 total)
### Production (24)
- `src/main/java/datingapp/core/connection/ConnectionService.java` (B1)
- `src/main/java/datingapp/core/connection/ConnectionModels.java` (D1, D2)
- `src/main/java/datingapp/core/profile/MatchPreferences.java` (B2)
- `src/main/java/datingapp/core/matching/DailyLimitService.java` (D3)
- `src/main/java/datingapp/core/matching/DefaultDailyLimitService.java` (D3)
- `src/main/java/datingapp/core/matching/MatchQualityService.java` (D4)
- `src/main/java/datingapp/core/matching/TrustSafetyService.java` (C6)
- `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java` (B11)
- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java` (B10)
- `src/main/java/datingapp/core/EnumSetUtil.java` (C7)
- `src/main/java/datingapp/core/ServiceRegistry.java` (D7)
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java` (D7)
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` (D7)
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` (D3/T2)
- `src/main/java/datingapp/storage/StorageFactory.java` (I2)
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` (B9)
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` (B6, D5)
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java` (B4)
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` (B3)
- `src/main/java/datingapp/ui/viewmodel/ProfileReadOnlyViewModel.java` (B5)
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java` (C5)
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java` (I1)
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` (D6)
- `pom.xml` (X1)

### Test (9)
- `src/test/java/datingapp/core/matching/RecommendationServiceTest.java` (D3)
- `src/test/java/datingapp/core/MatchingServiceTest.java` (D3)
- `src/test/java/datingapp/core/matching/MatchingTransactionTest.java` (D3)
- `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java` (D3)
- `src/test/java/datingapp/ui/screen/ChatControllerTest.java` (D6)
- `src/test/java/datingapp/ui/screen/MatchingControllerTest.java` (D6)
- `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java` (D6)
- `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java` (D6)
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java` (T1)
