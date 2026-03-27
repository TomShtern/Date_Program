
> 🚀 **VERIFIED & UPDATED: 2026-03-09**
> This document has been programmatically verified against the codebase as of this date.

# Code-First Gaps and Next Work Report

**Generated:** 2026-03-07
**Repository:** `Date_Program`
**Evidence basis:** live source code in `src/main/java/**`, `src/test/java/**`, selected runtime config, and a fresh `mvn test -DskipITs` run
**Source-of-truth rule:** this report is based on code and current test behavior, not on repository markdown documentation

---

## Executive summary

The project has a **strong architectural foundation** already in place:

- a real application use-case layer (`app/usecase/**`)
- a real event bus (`app/event/**`)
- grouped configuration via `AppConfig`
- Hikari-based database pooling in `DatabaseManager`
- paginated match/message APIs
- a shared async abstraction for most JavaFX ViewModels (`ui/async/ViewModelAsyncScope`)

The main problem is no longer “missing architecture.” The real problem is **unfinished seams**:

1. the current baseline is **not fully green**,
2. several user-facing flows are **present but not end-to-end complete**,
3. one important backend consistency seam is still **partially non-atomic**,
4. UI complexity has outrun UI-specific test coverage.

### Bottom line

If we want the highest ROI, the next work should **not** be a giant architecture restart. It should be:

1. **stabilize the current baseline**,
2. **finish incomplete product flows**,
3. **harden transition consistency and persistence**,
4. **add UI/controller coverage**,
5. **then** refactor the biggest hotspots safely.

---

## Current validated baseline

### Fresh test run

> ⚠️ **Update (2026-03-09):** The test suite is now fully green. A fresh `mvn test -DskipITs` run produced 1002 tests run, 0 failures, 2 skipped, and `BUILD SUCCESS`. The `DashboardViewModelTest` failure has been resolved.

A fresh `mvn test -DskipITs` run on 2026-03-07 produced:

- **Tests run:** 983
- **Failures:** 1
- **Errors:** 0
- **Skipped:** 2
- **Build status:** `BUILD FAILURE`

### Current failing test

- `DashboardViewModelTest.shouldHandleConcurrentRefreshes`

This is important because it points at a **real concurrent refresh convergence problem** in the dashboard path, not just a stale documentation claim.

### Current resource baseline

`src/main/resources/` currently contains only:

- `css/`
- `fxml/`
- `logback.xml`

There is **no `images/` resource directory**, even though `UiConstants.DEFAULT_AVATAR_PATH` points to `/images/default-avatar.png`.

---

## What is already in good shape

Before listing gaps, it is worth being explicit about what **does not** need to be rebuilt from scratch:

- `AppConfig` is already grouped into sub-records (`MatchingConfig`, `ValidationConfig`, `AlgorithmConfig`, `SafetyConfig`)
- `ServiceRegistry` already constructs and exposes real use-case bundles
- `MatchingUseCases`, `MessagingUseCases`, `ProfileUseCases`, and `SocialUseCases` already exist and are wired
- `AppEvent` + `InProcessAppEventBus` + event handlers are real and functioning
- `DatabaseManager` already uses Hikari connection pooling
- `CandidateFinder` already pushes primary filters into SQL via `UserStorage.findCandidates(...)`
- `InteractionStorage` and `RestApiServer` already support pagination for important list endpoints
- most ViewModels already use `ViewModelAsyncScope`

That means the best next work is **completion and tightening**, not foundational reinvention.

---

## Highest-priority findings

## 1) The current baseline is not fully stable

### Evidence

- Fresh test run fails at `DashboardViewModelTest.shouldHandleConcurrentRefreshes`
- `DashboardViewModel.refresh()` uses `asyncScope.runLatest("dashboard-refresh", ...)`
- the dashboard refresh pipeline loads multiple pieces of data sequentially inside `loadDashboardData(...)`

### Why this matters

A failing concurrency-oriented test in a ViewModel is exactly the kind of issue that later turns into:

- intermittent stale UI
- hard-to-reproduce refresh bugs
- confidence loss in further UI refactors

### Recommended next action

Treat this as **P0**:

- investigate `DashboardViewModel.refresh()` / `loadDashboardData(...)`
- decide whether the issue is in the ViewModel logic, the async contract, or the test expectations
- restore a green baseline before expanding features

---

## 12) A few major hotspots still deserve refactoring, but not first

### Evidence

#### `ProfileController`
Still a large multi-concern controller combining:

- field binding
- photo behavior
- interest selection dialogs
- dealbreaker dialogs
- validation glue

#### `MatchingHandler`
Still mixes:

- app-layer use-cases
- raw services
- display logic
- navigation/flow orchestration

#### `MatchingViewModel`
Constructs its own use-case objects directly instead of consuming the app-layer bundle from the registry.

#### `ViewModelFactory`
Still injects some raw services directly, e.g. `LoginController(getLoginViewModel(), services.getProfileService())`.

#### Global runtime state
Still present in:

- `AppSession`
- `NavigationService`
- `ServiceRegistry` as the main composition hub

### Why this matters

These are real architectural and maintenance issues, but they should be sequenced **after** stabilization and feature completion, otherwise we will just refactor around still-broken flows.

### Recommended next action

After the product/runtime gaps above are addressed:

1. add controller coverage
2. refactor `ProfileController`
3. simplify `MatchingHandler`
4. tighten adapter boundaries in ViewModels / `ViewModelFactory`
5. revisit singleton/global state reduction if still justified

---

## Additional structural observations

### Popup implementation is split and likely unfinished

### Evidence

- `MatchingController.showMatchPopup(...)` builds the match popup programmatically
- `ui/popup/MatchPopupController` exists separately
- `ui/popup/MilestonePopupController` exists
- `ui/screen/MilestonePopupController` also exists
- `src/main/resources/fxml/` contains both `match_popup.fxml` and `achievement_popup.fxml`

### Why this matters

This suggests a partially completed popup/UI consolidation story. It is not the highest-priority gap, but it is a clear cleanup candidate after more important product work is finished.

---

## Recommended execution order

## Phase 0 — Restore trust in the baseline

1. Fix the failing dashboard concurrent-refresh path
2. Fix the broken Preferences FXML route
3. Re-run the full suite and get back to green

## Phase 1 — Finish the user-visible incomplete flows

4. Make Daily Pick and Standouts context-aware
5. Add live chat refresh behavior
6. Decide and implement profile notes parity (or intentionally keep CLI-only)
7. Decide and implement moderation / relationship-transition parity
8. Finish Preferences behavior and theme persistence semantics
9. Clarify and complete the photo/media story

## Phase 2 — Harden backend consistency and persistence

## 10. Standardize atomicity for relationship transitions
> ⚠️ **Update (2026-03-09): Resolved.** `ConnectionService` was refactored to remove manual compensating writes. It now natively delegates to `InteractionStorage.acceptFriendZoneTransition()` and other cross-domain JDBI hooks to ensure perfect atomicity.
11. Finish or remove the half-migrated normalized user-data path
> ⚠️ **Update (2026-03-09): Resolved.** `JdbiUserStorage` has been fully migrated. The legacy JSON columns have been dropped via `MigrationRunner`, and the persistence layer now relies exclusively on the parsed normalized tables.
12. Review remaining persistence backdoors such as mapper-only mutation hooks where appropriate

## Phase 3 — Make the UI safe to evolve

13. Add controller/screen-level tests
14. Refactor `ProfileController`
15. Reduce `MatchingHandler` and adapter-layer coupling
16. Revisit singleton/global-state reduction if still worth the cost

---

## Recommended top 10 next work items

| Rank | Item                                                            | Why now                                          | Primary code anchors                                                                    |
|------|-----------------------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------------|
| 1    | Restore green baseline                                          | Current tests are red                            | `DashboardViewModel`, `DashboardViewModelTest`                                          |
| 2    | Fix Preferences navigation                                      | Live UI route is broken                          | `NavigationService`, `preferences.fxml`                                                 |
| 3    | Finish Daily Pick / Standouts context handoff                   | Feature exists but UX is incomplete              | `DashboardController`, `StandoutsController`, `NavigationService`, `MatchingController` |
| 4    | Add live chat refresh                                           | Messaging is not real-time yet                   | `ChatViewModel`, `ConnectionService`                                                    |
| 5    | Add Profile Notes beyond CLI                                    | Capability exists only in CLI/storage            | `ProfileHandler`, `UserStorage`, `JdbiUserStorage`                                      |
| 6    | Surface relationship transition / moderation flows consistently | Core behavior outruns UI/API                     | `MatchingHandler`, `SafetyHandler`, `RestApiServer`, UI screens                         |
| 7    | Finish Preferences/theme subsystem                              | Inconsistent lifecycle and no theme persistence  | `PreferencesViewModel`, `PreferencesController`                                         |
| 8    | Harden transition atomicity                                     | Important backend consistency seam still open    | `ConnectionService`, `JdbiMatchmakingStorage`                                           |
| 9    | Finish normalized user-data migration                           | Two persistence models currently coexist         | `JdbiUserStorage`                                                                       |
| 10   | Add controller-level UI tests before refactors                  | Biggest UI hotspots have weakest direct coverage | `src/test/java/datingapp/ui/**`, controllers                                            |

---

## Final recommendation

The repo is no longer at the stage where the right answer is “invent a better architecture.”

The right answer now is:

- **fix what is actively broken**,
- **finish what is clearly half-finished**,
- **harden the one remaining consistency seam**,
- **add test coverage where the complexity actually lives**,
- and only then do the larger refactors.

That sequence will produce more value, less churn, and better confidence than a broad rewrite pass.
