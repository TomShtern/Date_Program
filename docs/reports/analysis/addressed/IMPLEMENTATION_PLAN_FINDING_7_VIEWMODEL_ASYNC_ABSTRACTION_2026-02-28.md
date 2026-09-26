# Implementation Plan: Finding 7 (Ad-Hoc Async Model in Every ViewModel)

Date: 2026-02-28
Source: `docs/audits/RETROSPECTIVE_ARCHITECTURE_DECISIONS_2026-02-27.md` (Decision 7)
Status: Implemented (2026-02-28)
Owner: UI architecture + ViewModel maintainers

## Implementation Result (Completed)

This plan has been implemented end-to-end.

Delivered:
1. New shared async package:
   - `src/main/java/datingapp/ui/async/UiThreadDispatcher.java`
   - `src/main/java/datingapp/ui/async/JavaFxUiThreadDispatcher.java`
   - `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
   - `src/main/java/datingapp/ui/async/TaskPolicy.java`
   - `src/main/java/datingapp/ui/async/TaskHandle.java`
   - `src/main/java/datingapp/ui/async/AsyncErrorRouter.java`
2. Migrated ViewModels to shared scope:
   - `ChatViewModel`, `MatchingViewModel`, `MatchesViewModel`, `DashboardViewModel`
   - `ProfileViewModel`, `SocialViewModel`, `StandoutsViewModel`, `StatsViewModel`, `LoginViewModel`
3. Factory/lifecycle wiring updates:
   - `ViewModelFactory` now uses a shared dispatcher for VM construction and session property dispatch.
   - `reset()` rebinds session listener after disposal cleanup.
4. New infrastructure tests:
   - `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`
   - `src/test/java/datingapp/ui/async/AsyncErrorRouterTest.java`
5. Suite-stability update:
   - `DashboardViewModelTest` concurrent refresh wait logic was hardened for full-suite timing variance.

Verification summary:
1. Focused regression tests: 44 passed, 0 failed.
2. Full quality gate: `mvn spotless:apply verify` -> BUILD SUCCESS.
3. Full suite: Tests run: 899, Failures: 0, Errors: 0, Skipped: 0.

## 1. Goal

Replace per-ViewModel async scaffolding (`Thread.ofVirtual`, `Platform.runLater`, disposed flags, generation guards, loading counters) with one shared UI async abstraction.

Primary outcomes:
1. Standardize async lifecycle behavior for all ViewModels.
2. Reduce race-condition and cleanup inconsistency risk.
3. Shrink repeated concurrency boilerplate.

## 2. Current Hotspots

Repeated async patterns are currently distributed across:

1. `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
2. `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
3. `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
4. `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
5. `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
6. `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
7. `src/main/java/datingapp/ui/viewmodel/StandoutsViewModel.java`
8. `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
9. `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`

Symptoms:
1. Each ViewModel implements its own thread creation and cancellation behavior.
2. Multiple incompatible "latest request wins" patterns (tokens, generations, thread refs).
3. Duplicated `runOnFx`, `beginLoading/endLoading`, `disposed` checks, and error-routing code.
4. Harder global reasoning about lifecycle and race guarantees.

## 3. Target Architecture

Add a shared async package:

1. `src/main/java/datingapp/ui/async/UiThreadDispatcher.java`
2. `src/main/java/datingapp/ui/async/JavaFxUiThreadDispatcher.java`
3. `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
4. `src/main/java/datingapp/ui/async/TaskPolicy.java`
5. `src/main/java/datingapp/ui/async/TaskHandle.java`
6. `src/main/java/datingapp/ui/async/AsyncErrorRouter.java`

Core responsibilities of `ViewModelAsyncScope`:
1. Run background tasks on virtual threads.
2. Dispatch success/error callbacks on JavaFX thread.
3. Support cancellation on dispose.
4. Support keyed "latest wins" semantics for refresh/load flows.
5. Expose standardized loading-state tracking.
6. Route errors consistently via `ViewModelErrorSink`.

ViewModels should express intent, not thread mechanics.

## 4. Scope

In scope:
1. Build reusable async infrastructure.
2. Refactor all ViewModels to use it.
3. Unify loading, error, cancellation, and latest-wins behavior.
4. Add tests for async semantics and ViewModel regressions.

Out of scope for this plan:
1. Navigation redesign (Decision 6).
2. Use-case layer creation itself (Decision 3), except integration touchpoints.
3. Replacing JavaFX threading model.

## 5. Step-by-Step Implementation Plan

## Phase 0: Baseline and Behavior Lock

1. Build an async behavior matrix per ViewModel:
   - Operations that run async.
   - Current race guard mechanism.
   - Current dispose behavior.
   - Current error mapping behavior.
2. Add characterization tests for current expected semantics:
   - Latest refresh replaces stale results.
   - No UI updates after dispose.
   - Loading flags clear correctly on success/failure/cancel.
3. Identify known edge cases to preserve or improve:
   - `ChatViewModel` tokenized message loading.
   - `MatchingViewModel` background thread interruption.
   - `DashboardViewModel` generation guards.

Exit gate:
1. Baseline semantics documented and test-covered for high-risk VMs.

## Phase 1: Build Shared Async Infrastructure

1. Introduce `UiThreadDispatcher` abstraction:
   - `isUiThread()`
   - `dispatch(Runnable)`
2. Implement `JavaFxUiThreadDispatcher` using `Platform.isFxApplicationThread()` and `Platform.runLater(...)`.
3. Implement `ViewModelAsyncScope` with:
   - Task launch API.
   - Keyed latest-wins API (`runLatest(key, ...)`).
   - Cancellation and disposal API.
   - Standard loading-state counter integration.
4. Implement `AsyncErrorRouter`:
   - Primary route: `ViewModelErrorSink`.
   - Secondary route: logger fallback.
5. Add structured task naming for logs/diagnostics.

Exit gate:
1. New async package compiles.
2. Scope APIs cover all current async patterns found in matrix.

## Phase 2: Unit Test the Infrastructure First

1. Add tests for `ViewModelAsyncScope`:
   - Latest-wins behavior.
   - Cancellation on dispose.
   - UI dispatch guarantee.
   - Error callback routing.
   - Loading counter correctness under overlapping tasks.
2. Add tests using a test dispatcher (non-JavaFX) for deterministic execution.
3. Add at least one integration-level JavaFX-thread test if required.

Exit gate:
1. Async infrastructure semantics are proven before refactoring VMs.

## Phase 3: Migrate Highest-Risk ViewModels First

Migration order:
1. `ChatViewModel`
2. `MatchingViewModel`
3. `DashboardViewModel`
4. `MatchesViewModel`

Per-ViewModel steps:
1. Replace raw `Thread.ofVirtual()` calls with async scope operations.
2. Replace manual token/generation logic with keyed latest-wins operations.
3. Replace manual `runOnFx` wrappers with dispatcher callbacks from scope.
4. Replace ad-hoc `beginLoading/endLoading` with shared loading tracker.
5. Keep public API unchanged during migration to minimize controller impact.

Exit gate:
1. Existing ViewModel tests pass.
2. No regressions in refresh/send/load workflows for migrated VMs.

## Phase 4: Migrate Remaining ViewModels

Apply same migration template to:
1. `ProfileViewModel`
2. `SocialViewModel`
3. `StandoutsViewModel`
4. `StatsViewModel`
5. `LoginViewModel`
6. Any other ViewModel with async behavior

For each:
1. Remove direct `Platform.runLater(...)` where scope callback already dispatches to UI.
2. Remove local thread fields, manual dispose guards, and duplicate loading helpers where redundant.
3. Route all async errors through a single ViewModel-level mechanism.

Exit gate:
1. No ViewModel directly manages virtual-thread lifecycle logic.
2. Async behavior is uniformly implemented through shared scope.

## Phase 5: Factory and Lifecycle Integration

1. Update `ViewModelFactory` to construct each ViewModel with:
   - `ViewModelAsyncScope`
   - shared `UiThreadDispatcher`
   - standardized error router configuration
2. Ensure disposal path closes each ViewModel scope on navigation reset and factory cleanup.
3. Validate `BaseController.cleanup()` behavior remains aligned with ViewModel disposal.

Exit gate:
1. No leaked tasks after navigation or logout/reset.
2. ViewModel disposal is deterministic and centralized.

## Phase 6: Cleanup and Simplification

1. Remove duplicated helper methods from ViewModels:
   - Local `runOnFx`
   - Custom loading counters (where replaced)
   - Redundant disposed checks (where scope enforces)
   - Duplicate error-notification helpers
2. Keep only ViewModel-specific state and business intent.
3. Normalize log style for async tasks.

Exit gate:
1. Boilerplate is removed and code is easier to reason about.

## Phase 7: Verification and Sign-Off

1. Run quality gates:
   - `mvn spotless:apply`
   - `mvn test`
   - `mvn verify`
2. Run UI smoke flows:
   - Login and profile load.
   - Matching refresh/like/pass.
   - Conversations refresh/open/send.
   - Social requests and notifications refresh.
3. Validate race/lifecycle behavior manually:
   - Rapid screen switching does not produce stale UI updates.
   - Dispose during background task does not crash or mutate detached screen.
   - Loading indicators clear in all success/failure paths.

Exit gate:
1. Async refactor is stable and behaviorally equivalent or improved.

## 6. API Design Requirements (Must-Have)

The shared async API must support all of the following:

1. `run(taskName, backgroundWork, onSuccess)` for standard async operations.
2. `runLatest(taskKey, taskName, backgroundWork, onSuccess)` for stale-result suppression.
3. `runFireAndForget(taskName, backgroundWork)` for side-effect operations.
4. `onError(taskName, throwable)` consistent callback pathway.
5. `dispose()` to cancel future callbacks and best-effort stop active tasks.
6. Optional task handles for explicit cancellation where needed.

## 7. Test Plan

Required new tests:
1. `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`
2. `src/test/java/datingapp/ui/async/AsyncErrorRouterTest.java`
3. Updated:
   - `ChatViewModelTest`
   - `MatchesViewModelTest`
   - `DashboardViewModelTest`
   - `SocialViewModelTest`
   - `StandoutsViewModelTest`

Required scenarios:
1. Latest-wins under rapid repeated refresh.
2. Cancel/dispose while task is in-flight.
3. Error callback exactly once per failed task.
4. Loading property transitions are balanced.
5. No stale callback mutates state after selection/context switch.

## 8. Risks and Mitigations

Risk 1: Hidden behavior changes in race handling.
Mitigation:
1. Keep characterization tests from Phase 0.
2. Migrate highest-risk VMs first and stabilize before broad rollout.

Risk 2: Over-abstracted API that still leaks thread details.
Mitigation:
1. Start from observed patterns only.
2. Reject abstractions that require each VM to re-implement guards.

Risk 3: Disposal leaks and hanging tasks.
Mitigation:
1. Mandatory scope disposal from `ViewModelFactory` and controllers.
2. Add tests that assert no post-dispose callbacks.

Risk 4: Error-reporting inconsistency.
Mitigation:
1. Central `AsyncErrorRouter`.
2. Standard policy for `ViewModelErrorSink` fallback.

## 9. Definition of Done

This finding is complete only when all are true:
1. ViewModels use the shared async scope abstraction for background work.
2. Direct async scaffolding is removed from individual ViewModels.
3. Loading, cancellation, latest-wins, and error-routing behavior are standardized.
4. Async infrastructure and migrated ViewModels are test-covered.
5. `mvn verify` passes.
6. Documentation is updated with final API and migration notes.

## 10. Recommended Execution Order (Practical)

1. Build and test async infrastructure.
2. Migrate `ChatViewModel` and `MatchingViewModel`.
3. Migrate remaining ViewModels.
4. Final cleanup and architecture guardrails.

This order reduces risk by proving the abstraction against the most concurrency-heavy screens first.

