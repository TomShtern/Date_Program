# Codebase Simplification Audit

**Date:** 2026-03-08
**Scope:** Full `src/main/java/datingapp/` (~116 files, ~43k LOC)
**Method:** Three independent deep-dive agents scanned core domain, UI layer, and app/storage layers
**Goal:** Identify major structural simplification opportunities without losing functionality

---

## Executive Summary

The codebase has solid architecture but has accumulated significant dead code, duplicated patterns, and over-abstracted layers. The findings below are ordered by **effort-to-reward ratio** — quick wins first, then progressively larger refactors. Estimated total recoverable: **~2,000+ lines** of dead, duplicated, or ceremonial code.

---

## Tier 1: Zero-Risk Quick Wins (delete dead code)

### 1.1 Dead `AppError` / `AppResult` Types

| Detail    | Value                                                 |
|-----------|-------------------------------------------------------|
| **Files** | `app/error/AppError.java`, `app/error/AppResult.java` |
| **Lines** | ~100                                                  |
| **Risk**  | None                                                  |

`AppError` and `AppResult` are **never imported outside `app/error/`**. Every handler, use-case, REST route, and ViewModel uses `UseCaseResult` and `UseCaseError` exclusively. The bridge methods (`fromUseCaseError`, `toUseCaseResult`) have zero callers. Delete both files.

---

### 1.2 Dead `TimePolicy` / `DefaultTimePolicy` Abstraction

| Detail    | Value                                                           |
|-----------|-----------------------------------------------------------------|
| **Files** | `core/time/TimePolicy.java`, `core/time/DefaultTimePolicy.java` |
| **Lines** | ~64                                                             |
| **Risk**  | Very low (test-only references)                                 |

`ServiceRegistry.getTimePolicy()` exists but is **never called from production code**. Every service uses `AppClock.now()` directly. Delete both files and remove the getter from `ServiceRegistry`.

---

### 1.3 `PerformanceMonitor` Has a Single Caller

| Detail    | Value                          |
|-----------|--------------------------------|
| **Files** | `core/PerformanceMonitor.java` |
| **Lines** | ~182                           |
| **Risk**  | Very low                       |

182 lines of metrics infrastructure used by exactly **one** `startTimer()` call in `CandidateFinder` that logs at TRACE level. The collected metrics (`OperationMetrics`, `logMetrics()`, `getMetrics()`) are never consumed. Replace with a simple `System.nanoTime()` call or delete entirely.

---

### 1.4 Duplicate Popup Controller Stubs

| Detail    | Value                                                                          |
|-----------|--------------------------------------------------------------------------------|
| **Files** | `ui/popup/MilestonePopupController.java`, `ui/popup/MatchPopupController.java` |
| **Lines** | ~118                                                                           |
| **Risk**  | Low                                                                            |

Two 57-61 line stubs exist "to resolve FXML missing controller warnings." The real implementation lives in `ui/screen/MilestonePopupController.java` (335 lines). `NavigationService.ViewType` has no popup routes — these stubs are orphaned. Delete both and update any FXML references.

**Tier 1 total: ~464 lines deleted, 6 files removed, near-zero risk.**

---

## Tier 2: Mechanical Extractions (low risk, high reward)

### 2.1 ViewModel Base Class Extraction

| Detail          | Value             |
|-----------------|-------------------|
| **Affects**     | All 10 ViewModels |
| **Lines saved** | ~300-400          |
| **Risk**        | Low               |

Every ViewModel independently copy-pastes:
- `createAsyncScope()` factory (identical in all 10, only the name string differs)
- `setLoadingState(boolean)` (identical in 9)
- `notifyError(String, Exception)` (identical in 7)
- `errorHandler` field + `setErrorHandler()` setter (all 10)
- `loadingProperty` field + accessor (9)
- `dispose()` calling `asyncScope.dispose()` (all 10)
- Guarded log methods (`logInfo`, `logWarn`, etc.) — 37 occurrences across 16 files

An abstract `BaseViewModel` (~70 lines) providing all of the above would eliminate ~30-50 lines of boilerplate per ViewModel. The `LoggingSupport` interface already exists in `core/` but ViewModels don't use it.

---

### 2.2 Report Dialog Deduplication

| Detail          | Value                                                       |
|-----------------|-------------------------------------------------------------|
| **Files**       | `MatchingController`, `MatchesController`, `ChatController` |
| **Lines saved** | ~45                                                         |
| **Risk**        | Very low                                                    |

Three controllers contain near-identical `showReportDialog()` methods building a `Dialog<Report.Reason>` with the same `ChoiceBox`, converter, button types, and result converter. Extract to a shared `UiDialogs.showReportDialog()` utility.

---

### 2.3 Confirmation Dialog Pattern (11 copies)

| Detail          | Value                                                                            |
|-----------------|----------------------------------------------------------------------------------|
| **Files**       | `MatchingController`, `MatchesController`, `ChatController`, `ProfileController` |
| **Lines saved** | ~85                                                                              |
| **Risk**        | Very low                                                                         |

The pattern `showConfirmation() -> if confirmed -> viewModel.doAction() -> showSuccess()` appears **11 times** across 4 controllers. A shared `UiDialogs.confirmAndExecute(title, header, body, action, successMsg)` would reduce each to a one-liner.

---

### 2.4 Photo Carousel Logic Duplicated

| Detail          | Value                                                                                |
|-----------------|--------------------------------------------------------------------------------------|
| **Files**       | `ProfileViewModel` + `ProfileController`, `MatchingViewModel` + `MatchingController` |
| **Lines saved** | ~80                                                                                  |
| **Risk**        | Low-medium                                                                           |

Both ViewModel/Controller pairs implement identical photo navigation: `showNextPhoto()`, `showPreviousPhoto()`, `updatePhotoControlsVisibility()`, photo index tracking, and URL list management. Extract a reusable `PhotoCarousel` helper.

**Tier 2 total: ~510-610 lines removed, low risk.**

---

## Tier 3: Structural Simplifications (medium risk, high impact)

### 3.1 `RecommendationService` Is a God Class (622 lines, 3 unrelated features)

| Detail      | Value                                      |
|-------------|--------------------------------------------|
| **File**    | `core/matching/RecommendationService.java` |
| **Touches** | ~15 files                                  |
| **Risk**    | Medium                                     |

Bundles three orthogonal features with no shared state:
1. **Daily rate limiting** (~70 lines) — `canLike`, `canPass`, `getStatus`
2. **Daily Pick** (~150 lines) — `getDailyPick`, caching, reason generation
3. **Standouts** (~200 lines) — `getStandouts`, scoring, generation

Split into `DailyLimitService`, `DailyPickService`, and `StandoutsService`. Each becomes single-purpose and independently testable. The current class takes 9 constructor dependencies.

---

### 3.2 `ProfileService` Overloaded with Achievement Tracking (772 lines)

| Detail              | Value                              |
|---------------------|------------------------------------|
| **File**            | `core/profile/ProfileService.java` |
| **Lines extracted** | ~200                               |
| **Touches**         | ~15 files                          |
| **Risk**            | Low-medium                         |

Contains two distinct feature sets:
- **Profile completeness** — needs only `User` + `AppConfig`
- **Achievement tracking** — needs `InteractionStorage`, `TrustSafetyStorage`, `AnalyticsStorage`

Achievements are a cross-cutting gamification concern, not a profile concern. Extracting an `AchievementService` would let `ProfileService` drop 3 of its 5 dependencies. The `AchievementEventHandler` already calls into `ProfileService` for this, showing the boundary is natural.

---

### 3.3 Duplicated Scoring Logic Between `MatchQualityService` and `RecommendationService`

| Detail          | Value                                                                                |
|-----------------|--------------------------------------------------------------------------------------|
| **Files**       | `core/matching/MatchQualityService.java`, `core/matching/RecommendationService.java` |
| **Lines saved** | ~150                                                                                 |
| **Risk**        | Low                                                                                  |

Both independently compute compatibility scores using identical primitives (`CompatibilityScoring.ageScore`, `interestScore`, `lifestyleScore`, `InterestMatcher.compare`), independently compute distance scores, and generate near-identical human-readable reason strings. A shared `CompatibilityCalculator` would eliminate the duplication and prevent drift.

---

### 3.4 `ViewModelFactory` Hardcoded Lazy Singletons

| Detail          | Value                                |
|-----------------|--------------------------------------|
| **File**        | `ui/viewmodel/ViewModelFactory.java` |
| **Lines saved** | ~140                                 |
| **Risk**        | Medium                               |

10 explicit fields, 10 synchronized getter methods with identical `if (x == null) { x = new ...; } return x;` patterns, and a `reset()` that manually nulls each one. Replace with a `Map<Class<?>, Object>` cache and a single generic `getOrCreate(Class, Supplier)`. Adding new screens becomes one line instead of four.

---

### 3.5 `UserContext.channel` Is Never Read

| Detail      | Value                             |
|-------------|-----------------------------------|
| **Affects** | ~40+ call sites across all layers |
| **Churn**   | ~200 lines                        |
| **Risk**    | Low-medium                        |

Every use-case method requires `UserContext` wrapping `userId` + `channel`. The `channel` field is **never read** by any consumer — `grep '.channel()'` returns zero hits in `src/main/java`. Every method only reads `context.userId()`. Each use-case method could simply accept `UUID userId`.

---

### 3.6 `LoginController` is 657 Lines with Embedded Dialog + Cell Renderer

| Detail                | Value                            |
|-----------------------|----------------------------------|
| **File**              | `ui/screen/LoginController.java` |
| **Lines reorganized** | ~330                             |
| **Risk**              | Low-medium                       |

Contains a 135-line programmatic account-creation dialog (should be FXML), a 192-line `UserListCellRenderer` inner class (should be standalone), and a `formatActivity()` method that duplicates `TextUtil.formatTimeAgo()` from core.

**Tier 3 total: ~800-1000 lines simplified, medium risk.**

---

## Tier 4: Larger Refactors (higher risk, strategic value)

### 4.1 CLI Handlers Bypass `ServiceRegistry` for Use-Case Construction

| Detail    | Value                                                                                                          |
|-----------|----------------------------------------------------------------------------------------------------------------|
| **Files** | `ProfileHandler`, `MessagingHandler`, `SafetyHandler`, `MatchingHandler`, `ChatViewModel`, `MatchingViewModel` |
| **Risk**  | Medium (functional defect)                                                                                     |

Multiple handlers construct **private** use-case instances without the `AppEventBus`, meaning swipe events, message events, and relationship transitions through these paths **silently skip** achievement unlocking, metrics recording, and notification creation. This contradicts the CLAUDE.md rule "obtain use-cases from ServiceRegistry — never construct them directly." The `fromServices()` factory methods exist and work correctly, but older constructors bypass them.

**This is not just a simplification — it's a latent bug.**

---

### 4.2 `JdbiUserStorage` Dual-Write Compatibility Window Is Unbounded

| Detail          | Value                               |
|-----------------|-------------------------------------|
| **File**        | `storage/jdbi/JdbiUserStorage.java` |
| **Lines saved** | ~200                                |
| **Risk**        | Medium                              |

Every `save()` performs 10 extra DELETE+INSERT operations for normalized tables alongside the legacy columns. Every `get()` performs 8 extra SELECT queries. The comment says "remove dual-write once V4 backfill validation is done." For a single-deployment H2 app, the migration already ran. Drop the legacy column writes and reads.

---

### 4.3 `InteractionStorage` Is a God Interface (~30 methods, 3 responsibilities)

| Detail      | Value                                  |
|-------------|----------------------------------------|
| **File**    | `core/storage/InteractionStorage.java` |
| **Touches** | ~15 files                              |
| **Risk**    | High                                   |

Spans likes, matches, AND atomic multi-table transactions. Transaction methods pull in `FriendRequest`, `Conversation`, and `Notification` types from the connection/communication domains. `JdbiMatchmakingStorage` (954 lines) implements it all. Split into `LikeStorage` + `MatchStorage` + `TransactionCoordinator`.

---

### 4.4 `MatchingService` Is Largely a Pass-Through (284 lines)

| Detail               | Value                                |
|----------------------|--------------------------------------|
| **File**             | `core/matching/MatchingService.java` |
| **Lines eliminated** | ~220 (of 284)                        |
| **Touches**          | ~10 files                            |
| **Risk**             | Medium-high                          |

Most methods are single-line delegations to `InteractionStorage`. The `processSwipe` orchestration (its main value) is duplicated in `MatchingUseCases`. The pending-likers query (~40 lines) is the only non-trivial logic. Could be dissolved into `MatchingUseCases` + `CandidateFinder`.

---

### 4.5 `SchemaInitializer` Is Redundant Alongside `MigrationRunner`

| Detail           | Value                                                                          |
|------------------|--------------------------------------------------------------------------------|
| **Files**        | `storage/schema/SchemaInitializer.java`, `storage/schema/MigrationRunner.java` |
| **Lines merged** | ~570 + ~610 -> ~900                                                            |
| **Risk**         | Low                                                                            |

`SchemaInitializer` is only called from `MigrationRunner`. Its methods are all `static` and package-private. Multiple migration versions (V2, V5, V6) re-run the same methods. Could be inlined into `MigrationRunner` as private methods.

---

### 4.6 `MatchesController` Is 943 Lines of Inline Rendering + Animation

| Detail                | Value                              |
|-----------------------|------------------------------------|
| **File**              | `ui/screen/MatchesController.java` |
| **Lines reorganized** | ~500                               |
| **Risk**              | Medium                             |

Contains ~200 lines of card rendering, ~130 lines of particle animation, ~80 lines of card caching, and ~80 lines of empty-state animations. Extract `MatchCardFactory`, move particle effects to `UiAnimations`, and genericize the card cache.

---

### 4.7 `ServiceRegistry` Takes 20 Constructor Parameters

| Detail   | Value                       |
|----------|-----------------------------|
| **File** | `core/ServiceRegistry.java` |
| **Risk** | Medium                      |

Accepts 20 individual parameters and internally constructs use-case instances. Tests must provide all 20 even when testing one feature. A builder pattern or having `StorageFactory` pre-build use-cases would reduce the constructor to a simple bag of pre-built objects.

**Tier 4 total: ~1000-1500 lines simplified, higher risk, requires careful incremental execution.**

---

## Haversine Duplication (Minor)

The haversine distance formula is duplicated in `CandidateFinder.GeoUtils.distanceKm()` and `UserStorage.haversineKm()`. Extract to a `GeoUtils` utility in `core/`. ~15 lines, very low risk.

---

## Priority Recommendation

| Priority              | What                            | Effort    | Reward                         |
|-----------------------|---------------------------------|-----------|--------------------------------|
| **Do first**          | Tier 1 (dead code deletion)     | Hours     | ~464 lines gone, zero risk     |
| **Do second**         | Tier 2 (mechanical extractions) | 1-2 days  | ~550 lines gone, low risk      |
| **Do third**          | Fix 4.1 (use-case bypass bug)   | Hours     | Fixes silent event loss        |
| **Plan carefully**    | Tier 3 (structural splits)      | 2-3 days  | ~900 lines, better separation  |
| **Strategic backlog** | Tier 4 (large refactors)        | 1-2 weeks | Major architecture improvement |

---

## Cross-Cutting Observations

### The `core/matching/` Package Is Over-Fragmented

The package currently has **8 substantial classes**: `MatchingService`, `CandidateFinder`, `CompatibilityScoring`, `InterestMatcher`, `LifestyleMatcher`, `MatchQualityService`, `RecommendationService`, `TrustSafetyService` — plus `UndoService` and `Standout`. Findings 3.1, 3.3, and 3.4 all point at the same root cause: matching logic was split too finely. After executing the Tier 3 refactors (splitting `RecommendationService`, merging duplicated scoring), the package could consolidate from 8 classes down to ~5 with clearer boundaries.

### Half the Entry Points Skip the Event Bus

The REST API inconsistency (some routes go through use-cases, some bypass to core services directly) combines with Finding 4.1 (CLI handlers constructing use-cases without the event bus) to reveal a broader pattern: **roughly half the entry points in the app silently skip event publishing**. This means achievement unlocking, metrics recording, and notification creation happen inconsistently depending on whether the user triggers an action through the GUI, CLI, or REST API — and even within the CLI, depending on which constructor path was used.

This deserves a deliberate decision: either events matter (and all paths must publish them) or they don't (and the event infrastructure itself — `AppEventBus`, `InProcessAppEventBus`, 3 event handlers — becomes a simplification target).

### Test Layer Needs Parallel Cleanup

The 88 test files were not audited, but some directly test dead code flagged in Tier 1. For example, `AppErrorTest.java` tests the dead `AppError` type. Any Tier 1 deletion should include removing the corresponding test files. Beyond that, the test layer likely contains its own duplication (e.g., repeated test setup patterns for `ServiceRegistry` construction with 20 parameters) that would benefit from a separate audit.

### Reconsidering Finding 4.4 (`MatchingService` Dissolution)

On reflection, Finding 4.4 (dissolving `MatchingService`) is probably **not worth pursuing**. Even though many of its methods are currently thin pass-throughs, it serves as a natural aggregation point for matching operations. If the codebase grows, those methods will accumulate real logic. The other findings hold up well, but this one trades a known stable structure for marginal line savings.

---

## What NOT to Simplify

These patterns may look over-engineered but earn their keep:

- **`ViewModelAsyncScope` / `TaskPolicy` / `TaskHandle`** — the async infrastructure is well-designed and prevents threading bugs across all ViewModels
- **Use-case layer** (for methods that coordinate multiple services + events) — the thin wrappers are questionable but the orchestration methods provide real value
- **`AppClock`** — critical for testability
- **`UiDataAdapters`** — correctly decouples ViewModels from storage interfaces
- **Event bus + handlers** — clean cross-cutting concern separation (achievements, metrics, notifications)
