# Codebase Cleanup & Consolidation Plan

> **Generated:** 2026-04-03
> **Scope:** Cleanup, deduplication, consolidation, logic simplification
> **Goal:** Optimize existing code without adding features
> **Source:** 166 Java files in `src/main/java/` (82,468 LOC)

---

## Part 1: Executive Summary

### Codebase Metrics

| Metric                    | Value                                                                                                                 |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Total Java Files          | 166                                                                                                                   |
| Total Java LOC            | 82,468 code / 101,876 total                                                                                           |
| Catch Blocks              | 103 instances across 32 files                                                                                         |
| `requireNonNull` Calls    | 656 instances                                                                                                         |
| Null-Check Guards         | 744 instances                                                                                                         |
| PMD Suppressed Violations | 37                                                                                                                    |
| Largest File              | `ProfileViewModel.java` (999 LOC)                                                                                     |
| Top 5 Largest             | `ProfileViewModel` (999), `ChatViewModel` (935), `ChatController` (840), `MatchesController` (839), `User.java` (832) |

### Quality Assessment

The codebase is **well-architected** with clear layer boundaries (`core/`, `app/`, `storage/`, `ui/`), consistent use of use-case patterns, and proper separation of concerns. However, there are still **meaningful consolidation opportunities** that would remove about **~1,100 LOC from the currently itemized main-source cleanup steps** (see the per-phase ledger in Parts 3 and 5), eliminate dead code, simplify complex patterns, and improve maintainability without changing any behavior. The earlier `~2,000+ LOC` claim overstated the savings relative to the phase-by-phase breakdown.

---

## Part 2: Findings Report

### Category A: Duplicated Code (HIGH IMPACT)

#### A1. `ensureCurrentUser()` — Duplicated 5 Times

**Files:**
- `ui/viewmodel/StatsViewModel.java` (line ~217)
- `ui/viewmodel/StandoutsViewModel.java` (line ~182)
- `ui/viewmodel/SocialViewModel.java` (line ~198)
- `ui/viewmodel/MatchingViewModel.java` (line ~120)
- `ui/viewmodel/ChatViewModel.java` (line ~407)

**Issue:** Every ViewModel has an identical private method:
```java
private User ensureCurrentUser() {
    if (currentUser == null) {
        currentUser = session.getCurrentUser();
    }
    return currentUser;
}
```

**Fix:** Add `protected final User ensureCurrentUser()` to `BaseViewModel`. All 5 ViewModels extend it.

**Savings:** ~25 LOC removed, 5 files simplified.

---

#### A2. `parseEnumNames()` — Duplicated 2 Times

**Files:**
- `storage/jdbi/NormalizedProfileHydrator.java` (line ~42)
- `storage/jdbi/DealbreakerAssembler.java` (line ~63)

**Issue:** Identical generic method in both files:
```java
private static <E extends Enum<E>> Set<E> parseEnumNames(Collection<String> values, Class<E> enumType) {
    return values.stream()
        .map(name -> Enum.valueOf(enumType, name))
        .collect(Collectors.toSet());
}
```

**Fix:** Move to `core/EnumSetUtil.java` as a public static utility method. Both files already import `EnumSetUtil`.

**Savings:** ~16 LOC removed, 1 centralized utility improved.

---

#### A3. Logger Helper Methods — Duplicated Across 6+ Controllers

**Files:**
- `ui/screen/ProfileController.java` (has `logInfo`, `logWarn`, `logError`)
- `ui/screen/MatchingController.java` (has `logInfo`, `logWarn`, `logError`)
- `ui/screen/MatchesController.java` (has `logInfo`, `logWarn`)
- `ui/screen/PreferencesController.java` (has `logInfo`, `logWarn`)
- `ui/screen/LoginController.java` (has `logInfo`, `logWarn`, `logError`)
- `ui/screen/DashboardController.java` (has `logInfo`, `logWarn`)

**Issue:** Each controller reimplements:
```java
private void logInfo(String message, Object... args) {
    logger.log(Level.INFO, MessageFormat.format(message, args));
}
```

**Fix:** Add these as `protected final` methods in `BaseController`. Controllers already extend it.

**Savings:** ~120 LOC removed across 6 files.

---

#### A4. Photo Carousel Visibility Logic — Duplicated 2 Times

**Files:**
- `ui/screen/ProfileController.java` (`updatePhotoControlsVisibility()`)
- `ui/screen/MatchingController.java` (`updatePhotoControlsVisibility()`)

**Issue:** Both controllers implement nearly identical logic to show/hide previous/next photo buttons and update photo count labels based on the same `PhotoCarouselState` observable.

**Fix:** Extract to a `PhotoCarouselBinder.bind(Button prev, Button next, Label count, ObservableValue<PhotoCarouselState> state)` static utility or add to `BaseController`.

**Savings:** ~40 LOC removed, behavior centralized.

---

#### A5. `summarizeBio()` / `fallbackBio()` — Semantic Duplicates

**Files:**
- `ui/viewmodel/MatchListLoader.java` — `summarizeBio(String bio, int maxLen)`
- `core/support/UserPresentationSupport.java` — `fallbackBio(User user)`

**Issue:** Both truncate bios to a max length and append "..." when exceeded. Same logic, different names, different signatures.

**Fix:** Consolidate into `TextUtil.truncateWithEllipsis(String text, int maxLen)` and have both callers use it.

**Savings:** ~15 LOC removed, 1 utility improved.

---

### Category B: Dead Code (HIGH IMPACT, ZERO RISK)

#### B1. Location Dialog Code in `ProfileController` — ~120 LOC Dead

**File:** `ui/screen/ProfileController.java` (lines ~950-1070)

**Dead Methods:**
- `createCountryCombo(LocationService, Country)`
- `createCityListView()`
- `refreshCitySuggestions(LocationService, LocationDialogRefs)`
- `wireLocationDialogInteractions(Country, LocationDialogRefs, Runnable, Runnable)`
- `refreshLocationValidation(LocationService, LocationDialogRefs, ResolvedLocation[])`
- `setLocationDialogState(LocationDialogRefs, boolean)`
- `LocationDialogRefs` (inner record — unused)

**Issue:** The actual location dialog is handled by `LocationSelectionDialog.show()` — a separate shared class. All these methods in `ProfileController` are **never called**.

**Fix:** Delete all 7 methods + the `LocationDialogRefs` record.

**Savings:** ~120 LOC removed, 0 behavior changed.

---

#### B2. `SkeletonLoader` Class — Never Used

**File:** `ui/UiComponents.java` — `SkeletonLoader` inner class (~100 LOC)

**Methods:**
- `loadWithSkeleton()`
- `loadDataWithSkeleton()`

**Issue:** Zero callers anywhere in the codebase. `DashboardController` was supposed to use skeleton loading but uses `performRefresh()` directly.

**Fix:** Delete the entire `SkeletonLoader` class.

**Savings:** ~100 LOC removed, 0 behavior changed.

---

#### B3. `UiFeedbackService` Redundant Passthroughs

**File:** `ui/UiFeedbackService.java`

**Issue:** Two methods that add no value:
1. `getAvatar(String path, double size)` — just calls `ImageCache.getAvatar(path, size)`. Used by 1 caller.
2. `clearValidation(TextInputControl, Label)` — just calls `ValidationHelper.clearValidation(...)`. Only 1 public caller (itself).

**Fix:**
- Inline `getAvatar()` to `ImageCache.getAvatar()` directly in the 1 caller (`LoginController.UserListCell`).
- Inline `clearValidation()` to `ValidationHelper.clearValidation()` or remove the wrapper entirely.

**Savings:** ~8 LOC removed.

---

#### B4. `ResponsiveController.setExpandedMode()` — Never Called

**File:** `ui/UiFeedbackService.java` — `ResponsiveController` interface

**Issue:** The interface defines `setExpandedMode(boolean)` with a default no-op. `DashboardController` implements `ResponsiveController` but only implements `setCompactMode`. No caller of `setExpandedMode` exists anywhere.

**Fix:** Remove from interface or implement it in `DashboardController`.

**Savings:** ~3 LOC removed or 1 method implemented.

---

### Category C: Overly Complex Patterns (MEDIUM IMPACT)

#### C1. 17 Sequential `if (command.xxx() != null)` in `ProfileMutationUseCases`

**File:** `app/usecase/profile/ProfileMutationUseCases.java` (lines ~289-369)

**Issue:** The `executeUpdateProfile()` method has 17 consecutive conditional blocks, each checking if a field is non-null and updating it:
```java
if (command.displayName() != null) user = user.copy().displayName(command.displayName()).build();
if (command.bio() != null) user = user.copy().bio(command.bio()).build();
// ... 15 more identical patterns
```

**Fix:** Use a **functional update chain** approach:
```java
User updated = Stream.<Function<User, User>>of(
    u -> command.displayName() != null ? u.copy().displayName(command.displayName()).build() : u,
    u -> command.bio() != null ? u.copy().bio(command.bio()).build() : u,
    // ...
).reduce(u -> u, Function::andThen).apply(user);
```

Or better, collect all non-null updates and apply them in a loop. More maintainable and significantly shorter.

**Savings:** ~40 LOC simplified, readability improved.

---

#### C2. `catch (Exception e)` Pattern — 103 Instances

**Files:** 32 files, primarily:
- `app/usecase/matching/MatchingUseCases.java` (15 catch blocks)
- `app/usecase/social/SocialUseCases.java` (13 catch blocks)
- `app/usecase/messaging/MessagingUseCases.java` (12 catch blocks)
- `ui/viewmodel/ChatViewModel.java` (12 catch blocks)
- `app/usecase/profile/ProfileUseCases.java` (5 catch blocks)
- `app/usecase/profile/ProfileMutationUseCases.java` (6 catch blocks)
- `app/usecase/profile/ProfileNotesUseCases.java` (5 catch blocks)

**Issue:** Every use-case method follows the same pattern:
```java
try {
    // business logic
    return UseCaseResult.success(data);
} catch (Exception e) {
    return UseCaseResult.failure(UseCaseError.unexpected(e.getMessage()));
}
```

**Fix:** Create a static helper in `UseCaseResult`:
```java
public static <T> UseCaseResult<T> wrap(Callable<T> operation) {
    try {
        return success(operation.call());
    } catch (Exception e) {
        return failure(UseCaseError.unexpected(e.getMessage()));
    }
}
```

Then each method becomes:
```java
return UseCaseResult.wrap(() -> { /* business logic */ return data; });
```

**Note:** This is a **medium effort** refactor due to the number of files, but it reduces 5-8 lines per method to 2-3 lines.

**Savings:** ~300 LOC simplified across 32 files.

---

#### C3. `StatsViewModel` Has 8 Constructors

**File:** `ui/viewmodel/StatsViewModel.java`

**Issue:** Combinatorial explosion of constructors to handle different combinations of `ProfileInsightsUseCases`, `ProfileUseCases`, `Clock`, and `UiThreadDispatcher`:
```java
public StatsViewModel(ServiceRegistry services, UiThreadDispatcher uiThread) { ... }
public StatsViewModel(ProfileInsightsUseCases insights, UiThreadDispatcher uiThread) { ... }
public StatsViewModel(ProfileInsightsUseCases insights, ProfileUseCases profile, Clock clock, UiThreadDispatcher uiThread) { ... }
// ... 5 more
```

**Fix:** Use a `Dependencies` record pattern (already used in `DashboardViewModel` and `MatchingViewModel`):
```java
public record Dependencies(
    ProfileInsightsUseCases insights,
    ProfileUseCases profile,
    Clock clock,
    UiThreadDispatcher uiThread
) {}

public StatsViewModel(Dependencies deps) { ... }
```

**Savings:** ~30 LOC removed, consistency improved.

---

#### C4. `ChatViewModel` Has 6 Constructors

**File:** `ui/viewmodel/ChatViewModel.java`

**Issue:** Same combinatorial explosion as `StatsViewModel`.

**Fix:** Same solution — use a `Dependencies` record.

**Savings:** ~25 LOC removed.

---

#### C5. `DashboardController.wireNavigationButtons()` — 13 Button Bindings

**File:** `ui/screen/DashboardController.java` (lines ~162-210)

**Issue:** 13 `if (button != null)` blocks with 50+ lines of repetitive boilerplate:
```java
if (profileButton != null) {
    profileButton.setOnAction(e -> handleNavigateToProfile());
}
if (matchingButton != null) {
    matchingButton.setOnAction(e -> handleNavigateToMatching());
}
// ... 11 more
```

**Fix:** Add a helper to `BaseController`:
```java
protected final void bindButton(Button button, Runnable action) {
    if (button != null) {
        button.setOnAction(e -> action.run());
    }
}
```

Then:
```java
bindButton(profileButton, this::handleNavigateToProfile);
bindButton(matchingButton, this::handleNavigateToMatching);
// ... much shorter
```

**Savings:** ~25 LOC removed.

---

#### C6. Haversine Distance Formula Duplicated 3 Times

**Files:** (from core layer analysis)
- `core/model/LocationModels.java`
- `core/matching/CandidateFinder.java`
- `core/matching/InterestMatcher.java` (or similar matching service)

**Issue:** The Haversine distance calculation (latitude/longitude distance formula) is copy-pasted in 3 places with minor variations. ~20 LOC each.

**Fix:** Extract to a single `LocationModels.haversineDistance(double lat1, double lon1, double lat2, double lon2)` and have all 3 callers use it.

**Savings:** ~40 LOC removed, 1 centralized formula easier to test.

---

### Category D: Inconsistent Patterns (LOW-MEDIUM IMPACT)

#### D1. `setErrorHandler()` Passthrough — 10 ViewModels

**Files:** Nearly every ViewModel that extends `BaseViewModel`.

**Issue:** `BaseViewModel` already has `setErrorSink(ViewModelErrorSink)`. But 10 ViewModels also define their own `setErrorHandler()` method that just delegates to `setErrorSink()`. Some name it `setErrorHandler`, some have different signatures.

**Fix:** Remove all passthrough `setErrorHandler()` methods. Controllers call `viewModel.setErrorSink(UiFeedbackService::showError)` directly.

**Savings:** ~30 LOC removed, naming consistency achieved.

---

#### D2. `notifyError(String)` Overload — 4 ViewModels

**Files:**
- `ui/viewmodel/SocialViewModel.java`
- `ui/viewmodel/SafetyViewModel.java`
- `ui/viewmodel/NotesViewModel.java`
- `ui/viewmodel/StandoutsViewModel.java`

**Issue:** Each has:
```java
private void notifyError(String message) {
    notifyError(message, null);
}
```

**Fix:** Add `protected final void notifyError(String message)` to `BaseViewModel`.

**Savings:** ~16 LOC removed.

---

#### D3. Offset/Limit Validation — Duplicated 4+ Times

**Files:**
- `core/storage/UserStorage.java` (line ~170)
- `core/storage/InteractionStorage.java` (lines ~216, ~248)
- `storage/jdbi/JdbiUserStorage.java` (line ~405)
- `storage/jdbi/JdbiMatchmakingStorage.java` (lines ~460, ~483)

**Issue:** Each validates pagination parameters with the same logic:
```java
if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1-100");
```

**Fix:** Add `StorageUtil.validatePagination(int offset, int limit)` static method.

**Savings:** ~24 LOC removed, validation centralized.

---

#### D4. Coordinate Validation — Scattered 3 Places

**Files:**
- `ui/viewmodel/ProfileViewModel.java` (line ~645) — `validateCoordinates()`
- `core/model/User.java` (lines ~615-628) — inline validation
- `core/model/LocationModels.java` (lines ~84-92) — `validateLatitude()`, `validateLongitude()`

**Issue:** Three different places validate latitude/longitude ranges with slightly different messages and bounds.

**Fix:** Single `LocationModels.validateCoords(double lat, double lon)` that throws `IllegalArgumentException` with consistent messages.

**Savings:** ~20 LOC simplified, consistency improved.

---

#### D5. Empty-State Visibility Binding — Inconsistent Patterns

**Files:**
- `ui/screen/SafetyController.java` — uses `Bindings.isEmpty()` bidirectional binding (clean)
- `ui/screen/NotesController.java` — uses `Bindings.isEmpty()` pattern (clean)
- `ui/screen/MatchesController.java` — manual `populateCards()` with imperative show/hide (verbose)
- `ui/screen/ChatController.java` — manual `applySelectedConversationState()` (verbose)

**Fix:** Convert `MatchesController` and `ChatController` to use `Bindings.isEmpty()` pattern for consistency.

**Savings:** ~30 LOC simplified in 2 files.

---

### Category E: Single-Use Classes That Could Be Inlined (LOW IMPACT)

#### E1. `RelationshipActionRunner` — Only Used by `MatchesViewModel`

**File:** `ui/viewmodel/RelationshipActionRunner.java` (~65 LOC)

**Issue:** A single-purpose class with a complex `SyncCallbacks` record, only instantiated once in `MatchesViewModel`. The async/sync branching logic could be inlined.

**Fix:** Inline into `MatchesViewModel` as private methods.

**Savings:** 1 file removed, ~65 LOC consolidated.

---

#### E2. `PhotoMutationCoordinator` and `ProfileDraftAssembler` — Only Used by `ProfileViewModel`

**Files:**
- `ui/viewmodel/PhotoMutationCoordinator.java` (~75 LOC)
- `ui/viewmodel/ProfileDraftAssembler.java` (~60 LOC)

**Issue:** Both are well-designed (SRP), but each is used by exactly 1 class (`ProfileViewModel`). The `synchronized` lock in `PhotoMutationCoordinator` is unnecessary since `ProfileViewModel` already runs photo ops sequentially via `asyncScope.runFireAndForget`.

**Fix:** Inline both into `ProfileViewModel` as private inner classes or private methods.

**Savings:** 2 files removed, ~135 LOC consolidated.

---

#### E3. `MatchListLoader` and `ConversationLoader` — Similar Patterns

**Files:**
- `ui/viewmodel/MatchListLoader.java`
- `ui/viewmodel/ConversationLoader.java`

**Issue:** Both are package-private final classes that wrap use-case calls and map results with the same pattern: call use-case, check `success()`, throw or return data.

**Fix:** A generic `UseCaseExecutor<T>` helper or inline into their parent ViewModel (`MatchesViewModel`).

**Savings:** ~40 LOC simplified or 2 files removed.

---

### Category F: Minor Consolidation Opportunities (LOW IMPACT)

#### F1. Format Methods Scattered Across 6+ Files

**Files:**
- `core/TextUtil.java` — `formatTimeAgo()` (centralized — good)
- `ui/viewmodel/ProfileViewModel.java` — `formatLocation()`, `formatLocationLabel()`
- `core/matching/CandidateFinder.java` — `formatLatLon()`
- `ui/screen/ChatController.java` — `formatTimestamp()` (inner class)
- `ui/screen/LoginController.java` — `formatFilter()`, `formatState()`, `formatActivity()`

**Fix:** Consolidate location formatting into `TextUtil`. Timestamp formatting could use `DateTimeFormatter` constants.

**Savings:** ~30 LOC consolidated.

---

#### F2. `Objects.requireNonNull` (656 instances) vs `if (x == null)` (744 instances)

**Issue:** Two different null-guard styles coexist even in the same file:
- `Objects.requireNonNull(param, "param cannot be null")` — fail-fast
- `if (param == null) return error;` — defensive return

**Fix:** Not a cleanup priority — both patterns serve different purposes (constructor validation vs. business logic guards). But consider standardizing on one style per layer.

---

#### F3. Confetti Animation Setup — Duplicated 2 Places

**Files:**
- `ui/screen/DashboardController.java` (achievement celebration)
- `ui/screen/MilestonePopupController.java`

**Issue:** Both create a `Canvas`, bind it to `rootStack` dimensions, create a `ConfettiAnimation`, and manage cleanup.

**Fix:** A `ConfettiOverlay.show(StackPane rootStack, Duration duration)` utility could encapsulate this.

**Savings:** ~20 LOC simplified.

---

## Part 3: Implementation Plan

### Phase 1: Dead Code Removal (ZERO RISK, IMMEDIATE BENEFIT)

**Estimated Effort:** 30 minutes
**Estimated Savings:** ~250 LOC across 3 files

| Step | Action                                                    | File(s)                  | LOC Change |
|------|-----------------------------------------------------------|--------------------------|------------|
| 1.1  | Delete dead location dialog methods                       | `ProfileController.java` | -120 LOC   |
| 1.2  | Delete `SkeletonLoader` class                             | `UiComponents.java`      | -100 LOC   |
| 1.3  | Inline `getAvatar()` and `clearValidation()` passthroughs | `UiFeedbackService.java` | -8 LOC     |
| 1.4  | Remove unused `setExpandedMode()` default method          | `UiFeedbackService.java` | -3 LOC     |

**Verification:** `mvn compile` + `mvn test` (no behavior should change)

---

### Phase 2: High-Impact Deduplication (LOW RISK, HIGH VALUE)

**Estimated Effort:** 1 hour
**Estimated Savings:** ~200 LOC across 15 files

| Step | Action                                               | File(s)                           | LOC Change |
|------|------------------------------------------------------|-----------------------------------|------------|
| 2.1  | Add `ensureCurrentUser()` to `BaseViewModel`         | `BaseViewModel.java`              | +5 LOC     |
| 2.2  | Remove `ensureCurrentUser()` from 5 ViewModels       | 5 ViewModel files                 | -25 LOC    |
| 2.3  | Add `parseEnumNames()` to `EnumSetUtil`              | `EnumSetUtil.java`                | +8 LOC     |
| 2.4  | Remove `parseEnumNames()` from 2 JDBI files          | 2 JDBI files                      | -16 LOC    |
| 2.5  | Add logger helpers to `BaseController`               | `BaseController.java`             | +15 LOC    |
| 2.6  | Remove logger helpers from 6 controllers             | 6 controller files                | -120 LOC   |
| 2.7  | Extract photo carousel binder                        | `BaseController.java` or new util | +20 LOC    |
| 2.8  | Remove duplicated photo visibility logic             | 2 controller files                | -40 LOC    |
| 2.9  | Add `truncateWithEllipsis()` to `TextUtil`           | `TextUtil.java`                   | +5 LOC     |
| 2.10 | Remove `summarizeBio()` / `fallbackBio()` duplicates | 2 files                           | -15 LOC    |

**Verification:** `mvn compile` + targeted tests for ViewModels and controllers

---

### Phase 3: ViewModel Cleanup (LOW-MEDIUM RISK)

**Estimated Effort:** 1.5 hours
**Estimated Savings:** ~120 LOC across 12 files

| Step | Action                                                     | File(s)                 | LOC Change |
|------|------------------------------------------------------------|-------------------------|------------|
| 3.1  | Add `notifyError(String)` to `BaseViewModel`               | `BaseViewModel.java`    | +3 LOC     |
| 3.2  | Remove `notifyError(String)` overloads from 4 ViewModels   | 4 ViewModel files       | -16 LOC    |
| 3.3  | Remove `setErrorHandler()` passthroughs from 10 ViewModels | 10 ViewModel files      | -30 LOC    |
| 3.4  | Convert `StatsViewModel` to `Dependencies` record          | `StatsViewModel.java`   | -30 LOC    |
| 3.5  | Convert `ChatViewModel` to `Dependencies` record           | `ChatViewModel.java`    | -25 LOC    |
| 3.6  | Update `ViewModelFactory` for new constructors             | `ViewModelFactory.java` | +10 LOC    |

**Verification:** `mvn compile` + ViewModel tests + UI smoke test

---

### Phase 4: Use-Case Pattern Simplification (MEDIUM RISK, INCREMENTAL ROLLOUT REQUIRED)

**Estimated Effort:** 2-3 hours plus full-suite verification after each batch
**Estimated Savings:** ~270 LOC in targeted use-case files (the cleanup ledger counts only the main-source helper/addition/removal rows below; rollout safeguards such as the feature flag and semantic regression tests are tracked separately because they add safety rather than remove duplication)

| Step | Action                                                                                                                                                                                                                                                                                                                                      | File(s)                                                                  | LOC Change                                 |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|--------------------------------------------|
| 4.1  | Add `UseCaseResult.wrap(Callable<T>)` helper                                                                                                                                                                                                                                                                                                | `app/usecase/common/UseCaseResult.java`                                  | +10 LOC                                    |
| 4.2  | Add a feature flag for staged rollout; default it off until semantic parity is verified                                                                                                                                                                                                                                                     | `AppConfig.java`, `AppConfigValidator.java`, `config/app-config.json`    | Safety work (excluded from cleanup ledger) |
| 4.3  | Run an automated pre-migration verifier over `ProfileNotesUseCases`, `ProfileUseCases`, `ProfileMutationUseCases`, `MessagingUseCases`, `MatchingUseCases`, and `SocialUseCases`; only catches that exactly match `return UseCaseResult.failure(UseCaseError.unexpected(e.getMessage()))` are eligible for `UseCaseResult.wrap()` migration | targeted use-case modules + verifier harness                             | Safety work                                |
| 4.4  | Add integration/semantic regression tests that assert migrated callers depend on `UseCaseResult` / `UseCaseError` semantics rather than raw exception types                                                                                                                                                                                 | matching/messaging/profile/social use-case tests                         | Test-only safety work                      |
| 4.5  | Convert one pilot module (`ProfileNotesUseCases`, 5 methods) to `UseCaseResult.wrap()`                                                                                                                                                                                                                                                      | `ProfileNotesUseCases.java`                                              | -25 LOC                                    |
| 4.6  | Run the full test suite and explicitly verify exact error messages, `UseCaseError.Code` values, and caller-visible exception-type expectations before the next batch                                                                                                                                                                        | Whole repo                                                               | 0                                          |
| 4.7  | Convert `ProfileUseCases` + `ProfileMutationUseCases` in controlled 5-10 method batches                                                                                                                                                                                                                                                     | `ProfileUseCases.java`, `ProfileMutationUseCases.java`                   | -55 LOC                                    |
| 4.8  | Convert `MessagingUseCases`, `MatchingUseCases`, and `SocialUseCases` in controlled 5-10 method batches; keep UI/framework catch blocks out of scope and exclude verifier deviations pending manual review                                                                                                                                  | `MessagingUseCases.java`, `MatchingUseCases.java`, `SocialUseCases.java` | -200 LOC                                   |
| 4.9  | Remove the staged rollout flag from `AppConfig`, `AppConfigValidator`, and runtime config once the default-on path has passed repeated full-suite verification                                                                                                                                                                              | `AppConfig.java`, `AppConfigValidator.java`, `config/app-config.json`    | Safety cleanup                             |

**Rollout rule:** This phase only targets use-case `execute()` / `handle()` style methods. Do **not** blanket-convert ViewModel, UI, storage, or framework-level catch blocks. Only modules whose candidate catches pass the pre-migration verifier should enter `UseCaseResult.wrap()` batches; deviations stay out of scope until manually reviewed. The feature flag must remain available as a rollback lever until the semantic-regression tests and full-suite runs prove parity across the migrated use-case modules.

**Verification:** After 4.5 and after every later batch, run the full suite and explicitly verify exact error messages, `UseCaseError.Code` values, constructor exception messages, and the migrated callers' reliance on `UseCaseError` rather than raw exception types before enabling the flag by default or removing it in 4.9.

---

### Phase 5: Core Layer Consolidation (LOW RISK)

**Estimated Effort:** 1 hour
**Estimated Savings:** ~80 LOC across 6 files

| Step | Action                                         | File(s)               | LOC Change |
|------|------------------------------------------------|-----------------------|------------|
| 5.1  | Extract Haversine distance to `LocationModels` | `LocationModels.java` | +12 LOC    |
| 5.2  | Replace 3 Haversine copies with shared call    | 3 files               | -40 LOC    |
| 5.3  | Add `validateCoords()` to `LocationModels`     | `LocationModels.java` | +8 LOC     |
| 5.4  | Replace 3 coordinate validation copies         | 3 files               | -20 LOC    |
| 5.5  | Add `validatePagination()` to storage utils    | New or existing util  | +8 LOC     |
| 5.6  | Replace 4 pagination validation copies         | 4 storage files       | -24 LOC    |

**Verification:** Storage tests + matching tests

---

### Phase 6: Controller Simplification (LOW RISK)

**Estimated Effort:** 1 hour
**Estimated Savings:** ~80 LOC across 5 files

| Step | Action                                              | File(s)                    | LOC Change |
|------|-----------------------------------------------------|----------------------------|------------|
| 6.1  | Add `bindButton()` helper to `BaseController`       | `BaseController.java`      | +5 LOC     |
| 6.2  | Use `bindButton()` in `DashboardController`         | `DashboardController.java` | -25 LOC    |
| 6.3  | Convert `MatchesController` to `Bindings.isEmpty()` | `MatchesController.java`   | -15 LOC    |
| 6.4  | Convert `ChatController` to `Bindings.isEmpty()`    | `ChatController.java`      | -15 LOC    |
| 6.5  | Consolidate location formatting into `TextUtil`     | `TextUtil.java` + callers  | -20 LOC    |

**Verification:** UI smoke test + controller tests

---

### Phase 7: Single-Use Class Inlining (LOW RISK)

**Estimated Effort:** 45 minutes
**Estimated Savings:** ~200 LOC, 5 files removed

| Step | Action                                                    | File(s) | LOC Change |
|------|-----------------------------------------------------------|---------|------------|
| 7.1  | Inline `RelationshipActionRunner` into `MatchesViewModel` | 2 files | -55 LOC    |
| 7.2  | Inline `PhotoMutationCoordinator` into `ProfileViewModel` | 2 files | -60 LOC    |
| 7.3  | Inline `ProfileDraftAssembler` into `ProfileViewModel`    | 2 files | -45 LOC    |
| 7.4  | Inline or remove `MatchListLoader` / `ConversationLoader` | 3 files | -40 LOC    |

**Verification:** ViewModel tests + full compile

### Savings Accounting Note

Summing every numeric row in **Phases 1-8** yields **~1,118 LOC** of main-source cleanup savings:

- Phase 1: **-231 LOC**
- Phase 2: **-163 LOC**
- Phase 3: **-88 LOC**
- Phase 4: **-270 LOC** (counting `4.1` plus the cleanup rows `4.3`, `4.6`, `4.7`, and `4.8`; `4.5` is the zero-delta verification gate, and the feature-flag/test safeguards are intentionally excluded from the cleanup ledger)
- Phase 5: **-56 LOC**
- Phase 6: **-70 LOC**
- Phase 7: **-200 LOC**
- Phase 8: **-40 LOC**

That is the corrected basis for the top-line savings claims. Category **D-F** items are **already represented** in Phases **3, 5, 6, and 7**; they should not be added again on top of this total.

---

### Phase 8: Complex Logic Rewrites (MEDIUM RISK)

**Estimated Effort:** 1 hour
**Estimated Savings:** ~40 LOC simplified, readability improved

| Step | Action                                                         | File(s)                        | LOC Change |
|------|----------------------------------------------------------------|--------------------------------|------------|
| 8.1  | Rewrite 17 sequential `if` blocks in `ProfileMutationUseCases` | `ProfileMutationUseCases.java` | -40 LOC    |

**Verification:** Targeted profile mutation tests

---

## Part 4: Priority Matrix

| Priority | Phase                              | Impact | Risk   | Effort | Net LOC Savings |
|----------|------------------------------------|--------|--------|--------|-----------------|
| **P0**   | Phase 1: Dead Code                 | High   | None   | 30min  | ~250            |
| **P1**   | Phase 2: Deduplication             | High   | Low    | 1hr    | ~200            |
| **P2**   | Phase 4: Use-Case Pattern          | High   | Medium | 2-3hr  | ~270            |
| **P3**   | Phase 3: ViewModel Cleanup         | Medium | Low    | 1.5hr  | ~120            |
| **P4**   | Phase 5: Core Consolidation        | Medium | Low    | 1hr    | ~80             |
| **P5**   | Phase 6: Controller Simplification | Medium | Low    | 1hr    | ~80             |
| **P6**   | Phase 7: Class Inlining            | Low    | Low    | 45min  | ~200 (5 files)  |
| **P7**   | Phase 8: Complex Rewrites          | Low    | Medium | 1hr    | ~40             |

---

## Part 5: Expected Outcomes

### After All Phases Complete

| Metric                                  | Before                                                                                                              | After                          | Change                                        |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------|--------------------------------|-----------------------------------------------|
| Total Java Files                        | 166                                                                                                                 | ~161                           | -5 files                                      |
| Total Java LOC                          | ~82,468                                                                                                             | ~81,350                        | -~1,100 LOC                                   |
| Dead Code Methods                       | ~15                                                                                                                 | 0                              | Eliminated                                    |
| Duplicated Patterns                     | ~20 instances                                                                                                       | Single source                  | Consolidated                                  |
| ViewModel Constructors                  | 6-8 each                                                                                                            | 1 each                         | Simplified                                    |
| Catch Block Boilerplate (Phase 4 scope) | 56 targeted use-case catch blocks (subset of the 103 total catch blocks; UI/framework handlers remain out of scope) | 0 in migrated use-case modules | Eliminated via `UseCaseResult.wrap()` batches |
| PMD Suppressed Violations               | 37                                                                                                                  | ~30                            | Reduced                                       |

**Accounting note:** The `-~1,100 LOC` figure comes from the itemized phase ledger above, including positive additions such as `BaseViewModel.ensureCurrentUser()`, logger helpers in `BaseController`, `TextUtil.truncateWithEllipsis(...)`, and `UseCaseResult.wrap(...)`. The original `-1,700+ LOC` / `~2,000+ LOC` claims were not supported by the current phase-by-phase deltas and effectively over-counted planned removals.

### Quality Improvements

- ✅ **Zero dead code** — all methods are used
- ✅ **Single source of truth** for duplicated utilities
- ✅ **Consistent patterns** — ViewModels, controllers, use-cases all follow clean templates
- ✅ **Simpler constructors** — no more combinatorial explosion
- ✅ **Shorter methods** — use-case methods 40% shorter on average
- ✅ **Fewer files** — single-use classes inlined
- ✅ **Better testability** — centralized utilities easier to test

---

## Part 6: Execution Notes

### Pre-Execution Checklist

1. Run `mvn spotless:apply verify` — capture current baseline
2. Run full test suite — ensure all tests pass
3. Commit current state with message: `chore: snapshot before cleanup consolidation`

### Execution Guidelines

- Execute phases **in order** — earlier phases may affect later ones
- After each phase: `mvn compile` → run the phase-specific verification pack below → perform the listed smoke checks when required → commit if green
- If a phase introduces test failures, **fix before proceeding** to next phase
- Phase 1: `mvn -Dtest=ProfileControllerTest,LoginControllerTest,DashboardControllerTest test`
- Phase 2: `mvn -Dtest=ProfileViewModelTest,MatchingViewModelTest,StatsViewModelTest,ProfileControllerTest,MatchingControllerTest,MatchesControllerTest,ChatControllerTest,DashboardControllerTest test`
- Phase 3: `mvn -Dtest=ProfileViewModelTest,MatchingViewModelTest,MatchesViewModelTest,ChatViewModelTest,StatsViewModelTest,ViewModelFactoryTest test`
- Phase 4: `mvn test` after **every** migration batch, plus an explicit review of exact error messages, `UseCaseError.Code` values, and caller-visible exception-type expectations before changing the feature-flag default or proceeding to the next batch
- Phase 5: `mvn -Dtest=MatchQualityServiceTest,RecommendationServiceTest,StorageContractTest,JdbiUserStorageNormalizationTest test`
- Phase 6: `mvn -Dtest=ProfileControllerTest,MatchingControllerTest,MatchesControllerTest,ChatControllerTest,DashboardControllerTest test`
- Phase 7: `mvn -Dtest=ProfileViewModelTest,MatchesViewModelTest,ChatViewModelTest,ViewModelFactoryTest test`
- Phase 8: `mvn -Dtest=ProfileMutationUseCasesTest,ProfileUseCasesTest test`
- Manual smoke plan for Phase 3: launch the app and exercise `Profile`, `Matching`, and `Stats`; verify load, navigation, save/update flows, and that no duplicate listeners or background-task errors appear
- Manual smoke plan for Phase 6: launch the app and step through `Dashboard` → `Profile` / `Matching` / `Matches` / `Chat`; verify button wiring, empty-state visibility, card rendering, and back-navigation flows
- Manual smoke plan for Phase 7: exercise profile photo add/set-primary/delete, match actions, chat selection/send/refresh, and the main ViewModel-driven refresh flows after the inlining work
- Use `git diff --stat` after each phase to track LOC changes

### Post-Execution Verification

1. `mvn spotless:apply verify` — full quality gate must pass
2. `mvn test` — all tests must pass
3. Re-run the stricter Phase 4 exact-message / `UseCaseError.Code` / exception-type verification whenever the completed work touched the catch-block migration seam
4. Manual smoke test: launch the app and execute the Phase 3 / 6 / 7 screen-and-workflow checks that match the phases you changed
5. `mvn jacoco:report` — verify coverage hasn't dropped below 0.60 threshold
6. Count `@SuppressWarnings(...PMD...)` occurrences with `rg -n '@SuppressWarnings\(.*PMD' src/main/java src/test/java` and confirm the total is `<= 30`
7. If any verification step fails, stop, capture the failing command/output, reset to the baseline snapshot commit from the pre-execution checklist (`git reset --hard <baseline-commit>` or equivalent restore point), and investigate before retrying

---

## Appendix: File Reference Map

### Files Modified by Most Phases

| File                       | Phases   | Changes                                             |
|----------------------------|----------|-----------------------------------------------------|
| `BaseViewModel.java`       | 2, 3     | +10 LOC (new helpers)                               |
| `BaseController.java`      | 2, 6     | +25 LOC (new helpers)                               |
| `ProfileViewModel.java`    | 3, 7     | -150 LOC (inline coordinators, remove passthroughs) |
| `ChatViewModel.java`       | 3, 4     | -50 LOC (Dependencies record, wrap helper)          |
| `ProfileController.java`   | 1, 2     | -120 LOC (dead code)                                |
| `DashboardController.java` | 2, 6     | -25 LOC (bindButton helper)                         |
| `EnumSetUtil.java`         | 2        | +8 LOC (parseEnumNames)                             |
| `TextUtil.java`            | 2, 6, F1 | +15 LOC (truncateWithEllipsis, formatting)          |
| `UseCaseResult.java`       | 4        | +10 LOC (wrap helper)                               |
| `LocationModels.java`      | 5        | +20 LOC (haversine, validateCoords)                 |
| `ViewModelFactory.java`    | 3        | +10 LOC (updated constructors)                      |

### Files Deleted

| File                                           | Phase | LOC                 |
|------------------------------------------------|-------|---------------------|
| `RelationshipActionRunner.java`                | 7.1   | ~65                 |
| `PhotoMutationCoordinator.java`                | 7.2   | ~75                 |
| `ProfileDraftAssembler.java`                   | 7.3   | ~60                 |
| `SkeletonLoader` (inner class)                 | 1.2   | ~100                |
| `MatchListLoader.java`                         | 7.4   | ~20                 |
| `ConversationLoader.java`                      | 7.4   | ~20                 |
| Location dialog methods (in ProfileController) | 1.1   | ~120 (partial file) |

> **Note:** The file-LOC values above are raw file sizes / removed blocks, not the **net** cleanup savings. Phase 7's net savings in the ledger are lower because part of the behavior is intentionally inlined back into parent ViewModels instead of being deleted outright.

---

**End of Plan**
