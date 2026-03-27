# Codebase Simplification — Full Implementation Plan

Based on [SIMPLIFICATION_AUDIT_2026-03-08.md](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/SIMPLIFICATION_AUDIT_2026-03-08.md), all claims verified against source.

## Execution Status (Updated 2026-03-08)

### Completed
- ✅ **Phase 1** (Dead code deletion): `app/error` types removed, `core/time` types removed, `PerformanceMonitor` removed, popup stubs removed, and related tests removed.
- ✅ **Phase 2** (Event bus bypass fix): CLI handlers now use service-registered use-case wiring (including event-bus-aware paths).
- ✅ **Phase 3** (ViewModel base extraction): `BaseViewModel` exists and shared async/lifecycle behavior is centralized.
- ✅ **Phase 4** (Dialog deduplication): `UiDialogs` exists and controller call sites use shared dialog utilities.
- ✅ **Phase 5A/5B/5C/5D/5E**: service splits and calculator extraction are implemented (`DailyLimitService`, `DailyPickService`, `StandoutService`, `AchievementService`, `CompatibilityCalculator`) and corresponding wiring is active.

### Deferred by Design
- ⏸️ **Phase 5F** (`UserContext.channel` removal): intentionally still deferred (field still present), consistent with this plan’s “high-churn, low-reward” recommendation.

### Final Verification (this execution)
- ✅ `mvn spotless:apply verify` **BUILD SUCCESS**
- ✅ Checkstyle: **0 violations**
- ✅ PMD: **pass**
- ✅ Tests: **990 run, 0 failures, 0 errors, 2 skipped**
- ✅ JaCoCo gate: **All coverage checks have been met**

---

## User Review Required

> [!IMPORTANT]
> **Phase 2 (Event Bus Bug Fix)** changes runtime behavior — events will fire where they didn't before. Achievement unlocking, metrics recording, and notifications will start working for CLI paths that previously skipped them silently. This is the *correct* behavior, but downstream effects should be tested.

> [!WARNING]
> **Phase 5 (Structural Splits)** touches 15+ files per refactor. Each sub-phase should be a separate PR/commit. These can be deferred to the strategic backlog if desired.

> [!CAUTION]
> **Finding 3.5 (UserContext.channel removal)** touches **~40+ call sites**. While `.channel()` is never read, removing it is high-churn low-reward. **Recommendation: defer or skip.** Included only as Phase 5F if explicitly desired.

---

## Phase 1: Dead Code Deletion (zero risk)

**Estimated effort:** 1–2 hours | **Lines removed:** ~470 | **Files deleted:** 9 (6 main + 3 test)

---

### 1A — Delete [AppError](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/error/AppError.java#9-52) / [AppResult](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/error/AppResult.java#10-51)

#### [DELETE] [AppError.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/error/AppError.java)
- 52 lines, zero imports outside `app/error/`

#### [DELETE] [AppResult.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/error/AppResult.java)
- 51 lines, zero imports outside `app/error/`

#### [DELETE] [AppErrorTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/app/error/AppErrorTest.java)
- Tests dead code

---

### 1B — Delete [TimePolicy](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/TimePolicy.java#13-27) / [DefaultTimePolicy](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/DefaultTimePolicy.java#11-39)

#### [DELETE] [TimePolicy.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/TimePolicy.java)
- 27 lines, interface never called from production code

#### [DELETE] [DefaultTimePolicy.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/DefaultTimePolicy.java)
- 39 lines, implementation of dead interface

#### [DELETE] [TimePolicyTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/core/time/TimePolicyTest.java)
- Tests dead code

#### [MODIFY] [ServiceRegistry.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java)
- Remove [TimePolicy](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/TimePolicy.java#13-27) import (line 23)
- Remove `private final TimePolicy timePolicy` field (line 52)
- Remove `timePolicy` from both constructors' parameter lists and `Objects.requireNonNull` (lines 80, 123, 143)
- Remove [getTimePolicy()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#250-253) method (lines 250-252)

#### [MODIFY] [StorageFactory.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java)
- Remove imports for [DefaultTimePolicy](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/DefaultTimePolicy.java#11-39) and [TimePolicy](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/time/TimePolicy.java#13-27) (lines 27-28)
- Remove `TimePolicy timePolicy = new DefaultTimePolicy(...)` construction (line 119)
- Remove `timePolicy` from [ServiceRegistry](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#28-266) constructor call (line 146)

---

### 1C — Delete [PerformanceMonitor](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/PerformanceMonitor.java#10-183)

#### [DELETE] [PerformanceMonitor.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/PerformanceMonitor.java)
- 183 lines, only caller is [CandidateFinder](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/CandidateFinder.java#21-348)

#### [DELETE] [PerformanceMonitorTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/core/PerformanceMonitorTest.java)
- Tests dead code

#### [MODIFY] [CandidateFinder.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/CandidateFinder.java)
- Remove `import datingapp.core.PerformanceMonitor` (line 4)
- Replace [findCandidatesForUser()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/CandidateFinder.java#146-188) method (lines 154-187): remove `PerformanceMonitor.Timer` try-with-resources wrapper, keep all logic intact, replace timer logging with simple `System.nanoTime()` elapsed-time log at TRACE level

---

### 1D — Delete Popup Controller Stubs

#### [DELETE] [MilestonePopupController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/popup/MilestonePopupController.java)
- 61 lines, stub "to resolve FXML missing controller warnings"

#### [DELETE] [MatchPopupController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/popup/MatchPopupController.java)
- 57 lines, stub

#### [MODIFY] [achievement_popup.fxml](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/resources/fxml/achievement_popup.fxml)
- Line 14: change `fx:controller="datingapp.ui.popup.MilestonePopupController"` → `fx:controller="datingapp.ui.screen.MilestonePopupController"` (the real implementation)

#### [MODIFY] [match_popup.fxml](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/resources/fxml/match_popup.fxml)
- Line 14: change `fx:controller="datingapp.ui.popup.MatchPopupController"` → verify where the real match popup controller lives, or remove `fx:controller` if the popup is created programmatically. Need to check if `NavigationService` or [MatchPopupController](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/popup/MatchPopupController.java#12-57) in `ui/screen/` handles match popups.

> [!IMPORTANT]
> Before deleting the popup stubs, we must verify:
> 1. `ui/screen/MilestonePopupController.java` has compatible `@FXML` bindings for [achievement_popup.fxml](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/resources/fxml/achievement_popup.fxml)
> 2. The match popup FXML is actually loaded somewhere — if it's orphaned FXML, we should delete it too

---

## Phase 2: Event Bus Bug Fix (functional defect)

**Estimated effort:** 1–2 hours | **Risk:** Medium — changes runtime behavior for the better

---

### 2A — Fix [MatchingHandler](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#63-1044) Event Bus Bypass

#### [MODIFY] [MatchingHandler.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java)
- **Problem:** Constructor (lines 93-104) creates `new MatchingUseCases(...)` and `new SocialUseCases(...)` **without passing the event bus**
- **Fix:** Accept pre-built use-cases from [ServiceRegistry](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#28-266) instead of constructing them directly
- Change the [MatchingHandler(Dependencies)](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#63-1044) constructor to use `ServiceRegistry.getMatchingUseCases()` and `ServiceRegistry.getSocialUseCases()` instead of `new MatchingUseCases(...)`
- The [Dependencies](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#112-180) record needs to include [MatchingUseCases](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#238-241) and [SocialUseCases](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#246-249) as fields (obtained from [ServiceRegistry](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#28-266)), or add `AppEventBus` as a dependency and pass it through
- **Preferred approach:** Add `matchingUseCases` and `socialUseCases` to the [Dependencies](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#112-180) record, populate them from [ServiceRegistry](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#28-266) in `Dependencies.fromServices()`, and stop constructing them in the handler constructor

#### Update [Dependencies](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#112-180) record (lines 112-178):
```java
// Add these fields to the record:
MatchingUseCases matchingUseCases,
SocialUseCases socialUseCases

// In fromServices(), populate from ServiceRegistry:
services.getMatchingUseCases(),
services.getSocialUseCases()
```

#### Update constructor (lines 81-105):
```java
// Replace lines 93-104 with:
this.matchingUseCases = dependencies.matchingUseCases();
this.socialUseCases = dependencies.socialUseCases();
```

This also lets us **remove** several direct service dependencies from [Dependencies](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#112-180) that were only needed to construct use-cases (`candidateFinderService`, `interactionStorage`, `transitionService`, `communicationStorage`).

---

### 2B — Fix [SafetyHandler](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/SafetyHandler.java#31-359) Event Bus Bypass

#### [MODIFY] [SafetyHandler.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/SafetyHandler.java)
- **Problem:** Constructor at line 45 does `new SocialUseCases(trustSafetyService)` — this calls a constructor that doesn't accept [ConnectionService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#226-229), [CommunicationStorage](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#182-185), or `AppEventBus`
- **Fix:** Remove the convenience constructor (lines 40-46) that bypasses the event bus. All callers should use [fromServices()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#150-175) factory method or the 5-arg constructor
- Verify existing tests in [SafetyHandlerTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/app/cli/SafetyHandlerTest.java) use the correct constructor path

---

### 2C — Verify No Other Event Bus Bypasses

Check all CLI handlers' test files for direct use-case construction:
- [LikerBrowserHandlerTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java)
- [MessagingHandlerTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/app/cli/MessagingHandlerTest.java)
- [RelationshipHandlerTest.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/test/java/datingapp/app/cli/RelationshipHandlerTest.java)

---

## Phase 3: ViewModel Base Extraction (mechanical)

**Estimated effort:** 3–4 hours | **Lines saved:** ~300-400 | **Risk:** Low

---

### 3A — Create `BaseViewModel` Abstract Class

#### [NEW] `BaseViewModel.java` in `ui/viewmodel/`
- Extract from the 10 existing ViewModels:
  - `ViewModelAsyncScope asyncScope` field + `createAsyncScope(UiThreadDispatcher)` factory
  - `BooleanProperty loadingProperty` + `setLoadingState(boolean)` + `isLoading()` / `loadingProperty()` accessors
  - `ViewModelErrorSink errorHandler` field + `setErrorHandler()` setter + `notifyError(String, Exception)`
  - `dispose()` method calling `asyncScope.dispose()`
- Keep it abstract with a single abstract method: `String viewModelName()` (used in `createAsyncScope` name argument)
- Also have it implement `LoggingSupport` since all ViewModels use `logInfo`/`logWarn`
- ~70 lines

### 3B — Refactor All 10 ViewModels

#### [MODIFY] Each of the 10 ViewModels:
- [ChatViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/ChatViewModel.java)
- [DashboardViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java)
- [LoginViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/LoginViewModel.java)
- [MatchesViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java)
- [MatchingViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java)
- [PreferencesViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java)
- [ProfileViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java)
- [SocialViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/SocialViewModel.java)
- [StandoutsViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/StandoutsViewModel.java)
- [StatsViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/StatsViewModel.java)

For each:
- Extend `BaseViewModel` instead of implementing `LoggingSupport` directly
- Remove duplicated fields/methods now in base class
- Implement `viewModelName()` returning the ViewModel's name string
- ~30-50 lines saved per ViewModel

---

## Phase 4: Dialog Deduplication (mechanical)

**Estimated effort:** 2–3 hours | **Lines saved:** ~130 | **Risk:** Very low

---

### 4A — Extract Shared Report Dialog

#### [NEW] Add method to [UiComponents.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/UiComponents.java) (or new `UiDialogs.java`)
- `showReportDialog()` → shared `Dialog<Report.Reason>` builder
- Extracted from identical implementations in 3 controllers

#### [MODIFY] Three controllers:
- [MatchingController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/MatchingController.java)
- [MatchesController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/MatchesController.java)
- [ChatController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ChatController.java)
- Replace inline `showReportDialog()` with call to shared utility

### 4B — Extract Shared Confirmation Dialog Pattern

#### [NEW] Add `confirmAndExecute()` to `UiDialogs.java`
- Signature: `confirmAndExecute(String title, String header, String body, Runnable action, String successMsg)`
- Replaces 11 instances of the same pattern across 4 controllers

#### [MODIFY] Same controllers as 4A plus:
- [ProfileController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/ProfileController.java)

---

## Phase 5: Structural Splits (medium risk, design-heavy)

**Estimated effort:** 1–2 weeks total | Each sub-phase should be a separate commit

---

### 5A — Split [RecommendationService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#210-213) (622 lines → 3 services)

#### [NEW] `DailyLimitService.java` in `core/matching/`
- Extract: `canLike()`, `canPass()`, `getStatus()`, daily rate limiting (~70 lines)

#### [NEW] `DailyPickService.java` in `core/matching/`
- Extract: `getDailyPick()`, caching, reason generation (~150 lines)

#### [NEW] `StandoutsService.java` in `core/matching/`
- Extract: `getStandouts()`, scoring, generation (~200 lines)

#### [MODIFY] [RecommendationService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/RecommendationService.java)
- Becomes a thin facade delegating to the 3 new services, then gradually removed

#### Touches: ~15 files (all callers of [RecommendationService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#210-213))

---

### 5B — Extract `AchievementService` from [ProfileService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#222-225) (772 lines)

#### [NEW] `AchievementService.java` in `core/profile/` (or `core/gamification/`)
- Extract: achievement tracking, unlocking, progress methods (~200 lines)
- Depends on: [InteractionStorage](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#178-181), [TrustSafetyStorage](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#190-193), [AnalyticsStorage](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#186-189)

#### [MODIFY] [ProfileService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/profile/ProfileService.java)
- Remove achievement methods, drop 3 of 5 dependencies

---

### 5C — Shared `CompatibilityCalculator`

#### [NEW] `CompatibilityCalculator.java` in `core/matching/`
- Consolidate duplicated scoring logic from [MatchQualityService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#206-209) and [RecommendationService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#210-213)
- Shared: `ageScore`, `interestScore`, `lifestyleScore`, `distanceScore`, reason string generation

#### [MODIFY] [MatchQualityService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/MatchQualityService.java) and [RecommendationService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/RecommendationService.java)
- Delegate to shared calculator

---

### 5D — `ViewModelFactory` Generic Cache

#### [MODIFY] [ViewModelFactory.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java)
- Replace 10 explicit fields + 10 synchronized getters + [reset()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/PerformanceMonitor.java#86-92) with:
  - `Map<Class<?>, Object> cache`
  - `<T> T getOrCreate(Class<T> type, Supplier<T> factory)`
  - [reset()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/PerformanceMonitor.java#86-92) → `cache.clear()`

---

### 5E — Haversine Deduplication (minor)

#### [MODIFY] [UserStorage.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/storage/UserStorage.java)
- Remove local `haversineKm()` method
- Use `CandidateFinder.GeoUtils.distanceKm()` instead (or extract [GeoUtils](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/CandidateFinder.java#56-90) to a standalone `core/GeoUtils.java`)

---

### 5F — `UserContext.channel` Removal (OPTIONAL — defer recommended)

#### [MODIFY] [UserContext.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/usecase/common/UserContext.java)
- Remove `channel` field from record
- Remove [cli()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/usecase/common/UserContext.java#18-21), [ui()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/usecase/common/UserContext.java#22-25), [api()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/usecase/common/UserContext.java#26-29) factory methods (replace with [of(UUID userId)](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/usecase/common/UserContext.java#14-17))
- ~40+ call site updates

> [!NOTE]
> This is high-churn, low-reward. The field is harmless. Skip unless there's a specific reason to remove it.

---

## What We Are NOT Changing

These patterns look over-engineered but earn their keep (per audit's own assessment):

- **`ViewModelAsyncScope` / `TaskPolicy` / `TaskHandle`** — well-designed async infrastructure
- **Use-case layer** — thin wrappers are questionable but orchestration methods provide real value
- **`AppClock`** — critical for testability
- **`UiDataAdapters`** — correctly decouples ViewModels from storage interfaces
- **Event bus + handlers** — clean cross-cutting concern separation
- **[MatchingService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/ServiceRegistry.java#198-201)** — audit self-corrected on 4.4, not dissolving it

---

## Verification Plan

### Automated Tests

Each phase has a checkpoint where ALL tests must pass:

```powershell
# After each phase: full test suite
mvn -Ptest-output-verbose test

# After each phase: quality gates
mvn spotless:apply verify
```

#### Phase 1 Verification
```powershell
# Confirm dead code removal doesn't break anything
mvn clean test
# Specifically verify CandidateFinder still works after PerformanceMonitor removal
mvn -Ptest-output-verbose -Dtest="CandidateFinder*" test
```

#### Phase 2 Verification
```powershell
# Verify CLI handler tests pass with event bus wired correctly
mvn -Ptest-output-verbose -Dtest="SafetyHandlerTest" test
mvn -Ptest-output-verbose -Dtest="LikerBrowserHandlerTest" test
mvn -Ptest-output-verbose -Dtest="RelationshipHandlerTest" test
mvn -Ptest-output-verbose -Dtest="MessagingHandlerTest" test

# Full test suite to catch any ripple effects
mvn clean test
```

#### Phase 3 Verification
```powershell
# Full test suite — ViewModel changes affect many paths
mvn clean test
# Build and run JavaFX UI to verify no runtime issues
mvn compile && mvn javafx:run
```

#### Phase 4 Verification
```powershell
# Full test suite
mvn clean test
# Build and run JavaFX UI — dialog changes must be visually verified
mvn compile && mvn javafx:run
```

#### Phase 5 Verification
Each sub-phase (5A-5F) should run:
```powershell
mvn clean test
mvn spotless:apply verify
```

### Manual Verification
- **Phase 1:** Run `rg "AppError|AppResult|TimePolicy|PerformanceMonitor" src/main/java` → should return zero hits (except comments if any)
- **Phase 2:** Run the CLI app (`mvn compile && mvn exec:exec`), perform a block action through [SafetyHandler](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/SafetyHandler.java#31-359), and verify the event bus fires (check logs for achievement/notification events)
- **Phase 3-4:** Launch JavaFX UI, navigate through all screens, verify no visual regressions
- **After all phases:** `tokei src/main/java` to confirm LOC reduction
