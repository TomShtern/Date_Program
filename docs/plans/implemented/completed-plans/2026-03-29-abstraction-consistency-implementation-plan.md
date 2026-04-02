# Abstraction Consistency Remediation Implementation Plan

## Progress Summary (updated 2026-03-30)

| Task   | Status     | Notes                                                                                                                                                                                                                                                                                                                                             |
|--------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Task 1 | ✅ COMPLETE | Composition roots tightened                                                                                                                                                                                                                                                                                                                       |
| Task 2 | ✅ COMPLETE | Event bus mandatory, achievement facade unified                                                                                                                                                                                                                                                                                                   |
| Task 3 | ✅ COMPLETE | ProfileHandler notes routed, SafetyHandler verification/use-case seam completed, MatchingHandler undo/daily-status/standouts routed, `Main.printMenu(...)` use-case seam locked, REST candidate exception explicitly documented/tested                                                                                                            |
| Task 4 | ✅ COMPLETE | App-owned swipe/undo/match-quality/report/relationship-transition contracts introduced and consumed by CLI, REST, and UI adapters                                                                                                                                                                                                                 |
| Task 5 | ✅ COMPLETE | Deterministic ID factories now reuse canonical helpers, deep-copy paths route through `User.copy()`, seed message writes use the atomic helper, and empty collection updates now clear selections consistently                                                                                                                                    |
| Task 6 | ✅ COMPLETE | Validation ownership consolidated around `ValidationService`, delete contracts now use typed `AppEvent.DeletionReason` + `UseCaseResult<Void>`, direct consumers updated, and the missing session-summary seam was restored/verified                                                                                                              |
| Task 7 | ✅ COMPLETE | `ChatViewModel` and `ProfileViewModel` now follow `BaseViewModel`, `ViewModelFactory` no longer needs the reflection disposal fallback, `PreferencesController` routes save navigation through the shared base helper, and any remaining convenience constructors are treated as compatibility/test shims outside the production composition root |
| Task 8 | ✅ COMPLETE | Added shared `RestApiTestFixture` and `UiAsyncTestSupport`, converted the targeted REST/API and JavaFX tests to the shared harnesses, deduped low-risk user factories, and stabilized the stale-load chat regression                                                                                                                              |
| Task 9 | ✅ COMPLETE | Task 9 smoke suite green (203 tests), full `mvn spotless:apply verify` green (1552 tests, JaCoCo/PMD/Spotless/Checkstyle all passing), and plan/report reconciled with intentional exclusions recorded below                                                                                                                                      |

**Last verified:** 2026-03-30 — Task 7 expanded shared-UI pack green (116 tests including `ViewModelArchitectureConsistencyTest`, Chat/Profile ViewModels, controllers, and navigation context), Task 8 focused suite green (113 tests), Task 9 smoke suite green (203 tests), and full `mvn spotless:apply verify` green with `Tests run: 1552, Failures: 0, Errors: 0, Skipped: 1`; Spotless, Checkstyle, PMD, and JaCoCo all passed.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repository’s existing best abstractions the only normal production path, reducing architectural drift without a big-bang rewrite.

**Architecture:** This is a standardization program, not a redesign. Tighten composition roots first, then lock the app/event boundaries, then consolidate helper reuse and validation, then finish the UI migration, and only then normalize the test harness. Preserve behavior whenever possible; when a contract changes, make the new contract explicit, typed, and test-backed.

**Tech Stack:** Java 25 (preview), JavaFX 25, Maven, JDBI, H2/in-memory test doubles, VS Code/Red Hat Java tooling.

---

## Why this should be one plan, not several

This work touches multiple subsystems, but it should **not** be split into separate standalone plans.

The shared architectural decisions live in a small set of central files:

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/usecase/**`
- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`

If those decisions are allowed to drift across multiple plans, later tasks will optimize for their local slice and reintroduce inconsistency.

**Best execution model:** one **main coordinating agent** owns the foundation tasks and contract decisions, then dispatches **targeted subagents** only after shared seams are stable.

### Use subagents only in these windows

- **Do not parallelize** Tasks 1-6. They touch shared contracts, shared abstractions, and common composition roots.
- **Parallelize carefully** only after the shared seams are green in Task 7:
   - one subagent for leaf ViewModel/controller migrations that no longer touch `BaseViewModel`, `ViewModelFactory`, `NavigationService`, or `UiDataAdapters`
- **Parallelize again** during Task 8 when the new shared test fixtures already exist:
  - one subagent for JavaFX harness conversion
  - one subagent for REST/API fixture extraction
  - one subagent for user-factory/helper dedupe

### Do not parallelize these file clusters

- `ServiceRegistry.java`
- `ProfileUseCases.java`
- `MatchingUseCases.java`
- `SocialUseCases.java`
- `BaseViewModel.java`
- `ViewModelFactory.java`
- `UiDataAdapters.java`

Those are shared-state files and should stay under a single coordinator.

## Design locks for the implementation

These are the architectural decisions this plan assumes.

1. **`ServiceRegistry` and `ViewModelFactory` are the only production composition roots.**
   - Public convenience constructors may remain only when they are clearly test-only or compatibility shims.

2. **The use-case layer is the application boundary.**
   - Adapters should not reach directly into storage/services for business flows when an app-layer API already exists.
   - First-wave cleanup should remove leaks of **service-owned result wrappers** and **storage transport types**.
   - Immutable domain entities such as `User`, `Match`, and `ProfileNote` may remain exposed temporarily if promoting app-owned replacements would create large churn with little architectural payoff.

3. **`AchievementService` is the one achievement facade.**
   - `ProfileService` must stop constructing or re-exposing a parallel achievement path.

4. **Event publication is mandatory in production paths.**
   - Tests get an explicit test bus instead of relying on `null` or no-op substitution inside production constructors.

5. **Collection update semantics must become explicit and uniform.**
   - `null` means “field omitted / leave unchanged” for patch-like commands.
   - empty collection means “clear selection” when a field is present.

6. **`BaseViewModel` must support the real UI lifecycle.**
   - The base class should own late-bound error sink support centrally, so subclasses do not reintroduce parallel error-routing patterns.

7. **Once a UI adapter boundary exists, ViewModels should consume UI-owned results.**
   - `UiDataAdapters` should not leak `PageData<Match>` from `core.storage`.

8. **The test suite should standardize on explicit shared harnesses.**
   - `JavaFxTestSupport`
   - `TestUserFactory`
   - `TestStorages`
   - shared REST/API fixture builders
   - explicit `TestEventBus`

9. **Verification should have its own app-layer seam.**
   - `SafetyHandler` may reuse `ProfileUseCases.listUsers()` for candidate selection, but code generation / code verification / verified-state mutation should move behind a dedicated `VerificationUseCases` instead of staying in the handler.

10. **Theme changes should flow through an injected UI theme façade.**
   - ViewModels should not call `NavigationService.getInstance()` directly for theme changes.
   - Introduce `UiThemeService` (or equivalent) as the single ViewModel-facing theme API.

11. **Validation ownership should be explicit, not accidental.**
   - Extend `ValidationService` intentionally for shared message/note content rules.
   - Keep `ProfileFormValidator` only as a thin UI parsing/message adapter.

## Non-goals for this plan

Keep these out unless they become direct fallout from the refactor:

- `ActivityMetricsService.recordMatch()` / `endSession()` silent no-op behavior
- REST `limit` capping
- location dataset self-validation
- candidate distance policy behavior
- handler `BEST_EFFORT` semantics in the event bus
- replacing `JavaFxTestSupport.waitUntil(...)` polling implementation itself

## Execution checklist

Use this as the operational checklist while executing the plan.

### Preflight

- [ ] Work from an isolated branch/worktree; do **not** execute this plan directly on `main` without explicit approval.
- [ ] Re-read `2026-03-29-abstraction-consistency-report.md` and this plan before starting Task 1.
- [ ] Confirm the current source still matches the design locks in this plan; if not, patch the plan before coding.
- [ ] Create/update the execution tracker so exactly one task is marked in progress.
- [ ] Identify shared files likely touched by the current task and avoid concurrent edits to them.
- [ ] Keep one main coordinating agent through Tasks 1-6.

### Per-task loop

- [ ] Mark exactly one task as `in-progress`.
- [ ] Read every source file and test file listed for that task before editing.
- [ ] Add or extend characterization tests first.
- [ ] Run the task’s focused verification command and confirm the expected red/green starting state.
- [ ] Make the smallest change that satisfies the task’s contract.
- [ ] Run Problems/error checks on all touched files.
- [ ] Re-run the task’s focused tests until they are green.
- [ ] If a contract changed, update direct consumers in the same task before moving on.
- [ ] Record any intentional deferment or scope adjustment back into the plan/report immediately.
- [ ] Commit only after the task’s own verification steps pass.

### Parallelization gate

- [ ] Do **not** parallelize Tasks 1-6.
- [ ] Only parallelize work explicitly marked parallel-safe in Tasks 7-8.
- [ ] Never allow parallel edits to shared files such as `ServiceRegistry.java`, `*UseCases.java`, `BaseViewModel.java`, `ViewModelFactory.java`, `UiDataAdapters.java`, or `NavigationService.java`.
- [ ] After each subagent batch, integrate one result at a time and re-run focused regressions before continuing.

### Review and blocker protocol

- [ ] Stop and patch the plan if the current source disagrees with a design lock or task assumption.
- [ ] Stop and escalate if a new abstraction decision appears that this plan has not already locked down.
- [ ] Do not “fix forward” repeated verification failures; revisit the task boundary and assumptions first.
- [ ] Keep the listed non-goals out of scope unless they are direct fallout from the current task.

### Final completion gate

- [ ] Run the targeted smoke suite in Task 9.
- [ ] Run `mvn spotless:apply verify`.
- [ ] Reconcile completed work against both this plan and `2026-03-29-abstraction-consistency-report.md`.
- [ ] Mark every unfinished item as deferred, superseded, or intentionally excluded.
- [ ] Only then declare the remediation complete.

## Context Map

### Files to modify

| Workstream                 | Primary files                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Purpose                                                                                  |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Composition roots          | `src/main/java/datingapp/core/ServiceRegistry.java`, `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`, `src/main/java/datingapp/app/usecase/{ProfileUseCases,MatchingUseCases,SocialUseCases}.java`, `src/main/java/datingapp/app/cli/ProfileHandler.java`                                                                                                                                                                                                               | Make construction strict and centralize production wiring                                |
| App/use-case boundary      | `src/main/java/datingapp/app/cli/{ProfileHandler,SafetyHandler,MatchingHandler}.java`, `src/main/java/datingapp/app/api/RestApiServer.java`, `src/main/java/datingapp/app/usecase/{profile/ProfileUseCases,matching/MatchingUseCases,social/SocialUseCases}.java`                                                                                                                                                                                                                    | Remove adapter bypasses and replace service-owned result leaks                           |
| Event/achievement boundary | `src/main/java/datingapp/app/usecase/{matching/MatchingUseCases,messaging/MessagingUseCases,profile/ProfileUseCases,social/SocialUseCases}.java`, `src/main/java/datingapp/core/profile/ProfileService.java`, `src/main/java/datingapp/storage/StorageFactory.java`, `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`                                                                                                                                                  | Make event bus mandatory and collapse to one achievement facade                          |
| Domain helper reuse        | `src/main/java/datingapp/core/model/Match.java`, `src/main/java/datingapp/core/connection/ConnectionModels.java`, `src/main/java/datingapp/core/model/User.java`, `src/main/java/datingapp/core/matching/TrustSafetyService.java`, `src/main/java/datingapp/app/cli/ProfileHandler.java`, `src/main/java/datingapp/storage/DevDataSeeder.java`, `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`     | Reuse IDs, copy helpers, atomic message writes, and set semantics                        |
| Validation/typed contracts | `src/main/java/datingapp/core/profile/ValidationService.java`, `src/main/java/datingapp/app/usecase/{messaging/MessagingUseCases,profile/ProfileUseCases}.java`, `src/main/java/datingapp/ui/screen/{ProfileController,ProfileFormValidator}.java`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`                                                                                                                                                                     | Consolidate validation ownership and type loose commands/results                         |
| UI lifecycle/composition   | `src/main/java/datingapp/ui/viewmodel/{BaseViewModel,ProfileViewModel,PreferencesViewModel,MatchesViewModel,ChatViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,DashboardViewModel,LoginViewModel,NotesViewModel,SafetyViewModel}.java`, `src/main/java/datingapp/ui/NavigationService.java`, `src/main/java/datingapp/ui/UiComponents.java`, `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`, `src/main/java/datingapp/ui/screen/PreferencesController.java` | Finish BaseViewModel migration, unify theme ownership, stop leaking paging/storage types |
| Test harness               | `src/test/java/datingapp/ui/JavaFxTestSupport.java`, `src/test/java/datingapp/core/testutil/{TestStorages,TestUserFactory}.java`, `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`, REST/API tests, UI tests, CLI tests                                                                                                                                                                                                                                               | Extract shared fixtures and remove local harness duplication                             |

### Dependencies that will need coordinated updates

| File                                                          | Relationship                                                                   |
|---------------------------------------------------------------|--------------------------------------------------------------------------------|
| `src/main/java/datingapp/storage/StorageFactory.java`         | Builds the registry-managed `AchievementService`, event bus, and service graph |
| `src/main/java/datingapp/Main.java`                           | Still has small read-model seams to add later for CLI menu summaries           |
| `src/main/java/datingapp/ui/DatingApp.java`                   | Entry point affected by `ViewModelFactory` lifecycle behavior                  |
| `src/main/java/datingapp/ui/UiFeedbackService.java`           | Presentation boundary used by controllers and some ViewModels                  |
| `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`    | Central UI boundary that must stop leaking `PageData`                          |
| `src/main/java/datingapp/app/event/InProcessAppEventBus.java` | Behavior anchor once null/no-op event paths are removed                        |

### Test files to update first

| Test                                                                                                                                                                                                                                                                                                    | Coverage                                                       |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`                                                                                                                                                                                                                                  | constructor contracts, achievement fallback, note/delete flows |
| `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`                                                                                                                                                                                                                                | event bus, result contract, undo/standout behavior             |
| `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`                                                                                                                                                                                                                                    | event publication and result contract cleanup                  |
| `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`                                                                                                                                                                                                                              | event bus and validation cleanup                               |
| `src/test/java/datingapp/app/cli/{ProfileHandlerTest,MatchingHandlerTest,SafetyHandlerTest}.java`                                                                                                                                                                                                       | adapter boundary migrations                                    |
| `src/test/java/datingapp/app/api/{RestApiReadRoutesTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest,RestApiHealthRoutesTest,RestApiRelationshipRoutesTest}.java`                                                                                                                                   | REST boundary and fixture changes                              |
| `src/test/java/datingapp/ui/viewmodel/{ProfileViewModelTest,PreferencesViewModelTest,MatchesViewModelTest,ChatViewModelTest,SocialViewModelTest,StandoutsViewModelTest,StatsViewModelTest,DashboardViewModelTest,LoginViewModelTest,NotesViewModelTest,SafetyViewModelTest,MatchingViewModelTest}.java` | UI lifecycle and constructor migration                         |
| `src/test/java/datingapp/ui/screen/{ProfileControllerTest,ChatControllerTest,MatchingControllerTest,BaseControllerTest}.java`                                                                                                                                                                           | controller/viewmodel seams and JavaFX harness standardization  |

### Reference patterns to preserve or emulate

| File                                                             | Pattern                                |
|------------------------------------------------------------------|----------------------------------------|
| `src/main/java/datingapp/core/ServiceRegistry.java`              | strict central composition root        |
| `src/main/java/datingapp/app/event/InProcessAppEventBus.java`    | real event bus implementation          |
| `src/main/java/datingapp/core/storage/CommunicationStorage.java` | atomic logical write helper            |
| `src/main/java/datingapp/core/model/User.java`                   | preferred deep-copy entry point        |
| `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`        | shared lifecycle and async abstraction |
| `src/test/java/datingapp/ui/JavaFxTestSupport.java`              | canonical JavaFX test harness          |
| `src/test/java/datingapp/core/testutil/TestUserFactory.java`     | shared user test builder               |

### Risk assessment

- [x] Breaking changes to public API — **Yes**, especially use-case result contracts and constructor surfaces
- [ ] Database migrations needed — **No**
- [ ] Configuration changes required — **No**
- [x] Broad test fallout expected — **Yes**
- [x] Shared-file merge risk — **Yes** (`ServiceRegistry`, `*UseCases`, `BaseViewModel`, `ViewModelFactory`, `UiDataAdapters`)

## Chunk 1: Foundation contracts

### Task 1: Freeze production composition roots

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- Test: `src/test/java/datingapp/app/usecase/{profile/ProfileUseCasesTest,matching/MatchingUseCasesTest,social/SocialUseCasesTest}.java`
- Test: `src/test/java/datingapp/ui/viewmodel/{ProfileViewModelTest,DashboardViewModelTest}.java`

- [x] **Step 1: Write/extend characterization tests for constructor and builder contracts**

Add failing tests that prove the desired production rule:

- production builders/constructors reject missing required collaborators
- test-only helpers stay explicit
- `ViewModelFactory` remains the only normal place that assembles ViewModels
- `DashboardViewModel.Dependencies.fromServices(...)` is either removed or reduced to a compatibility shim, not a second composition root

- [x] **Step 2: Run the focused baseline tests**

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest,SocialUseCasesTest,ProfileViewModelTest,DashboardViewModelTest test`

Expected: currently mixed behavior; some new characterization tests should fail until constructors are tightened.

- [x] **Step 3: Tighten the production construction paths**

Make these concrete changes:

- `ServiceRegistry` remains the mandatory app/core composition root
- `ViewModelFactory` remains the mandatory UI composition root
- builders/constructors in `ProfileUseCases`, `MatchingUseCases`, and `SocialUseCases` fail fast on missing required dependencies
- `ProfileHandler` no longer self-creates `LocationService` in production code paths
- only the worst current self-composition leaks are fixed here (`ProfileViewModel`, `DashboardViewModel`); the broader ViewModel sweep is intentionally deferred to Task 7

- [x] **Step 4: Run IDE/Problems checks on all touched files**

Check the touched files for compile/lint issues before running broader tests.

- [x] **Step 5: Re-run the focused tests**

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest,SocialUseCasesTest,ProfileViewModelTest,DashboardViewModelTest test`

Expected: green.

- [x] **Step 6: Commit** ✅ (implementation complete, commit deferred per session policy)

   - 2026-03-29: implementation and focused verification completed; commit intentionally deferred while continuing through the plan in the same session.

Commit message:
`refactor: centralize production construction paths`

### Task 2: Make event publication and achievement ownership singular

**Execution mode:** main agent only

**Files:**
- Create: `src/test/java/datingapp/app/testutil/TestEventBus.java`
- Modify: `src/main/java/datingapp/app/usecase/{matching/MatchingUseCases,messaging/MessagingUseCases,profile/ProfileUseCases,social/SocialUseCases}.java`
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/core/{ProfileServiceTest.java,profile/ProfileServiceTest.java}`
- Test: `src/test/java/datingapp/app/usecase/{profile/ProfileUseCasesTest,matching/MatchingUseCasesTest,social/SocialUseCasesTest,messaging/MessagingUseCasesTest}.java`
- Test: `src/test/java/datingapp/ui/viewmodel/{DashboardViewModelTest,StatsViewModelTest}.java`

- [x] **Step 1: Add a first-class test event bus helper** ✅

Create a reusable test utility with one or both of these behaviors:

- recording published events for assertions
- no-op publishing for tests that only need a valid bus

This replaces `null`/embedded no-op production behavior.

- [x] **Step 2: Add failing tests for mandatory event bus behavior and single achievement ownership** ✅

Cover these rules:

- production use cases never silently drop events because `eventBus` is absent
- `ProfileUseCases` does not fall back to `ProfileService` for achievement reads/unlocks
- achievement reads in UI no longer depend on `ProfileService` as an alternate facade

- [x] **Step 3: Remove null/no-op event publication from production paths** ✅

Implement concrete changes:

- remove `NO_OP_EVENT_BUS` production fallback in `MatchingUseCases`
- remove `if (eventBus == null)` publication shortcuts in all use-case bundles
- update tests to inject `TestEventBus` explicitly

- [x] **Step 4: Migrate all achievement consumers to `AchievementService`** ✅

Implement concrete changes:

- `StorageFactory` remains the one place that creates `DefaultAchievementService`
- `ProfileUseCases` requires/use `AchievementService` directly
- `DashboardViewModel` stops treating `ProfileService` as its achievement facade

- [x] **Step 5: Remove the parallel achievement façade from `ProfileService` once consumers are off it** ✅

Concrete changes:

- `ProfileService` stops constructing its own `DefaultAchievementService`
- `ProfileService` stops re-exposing achievement methods publicly
- `ProfileServiceTest` duplication is reconciled as part of the same change (duplicate test files removed)

- [x] **Step 6: Run IDE/Problems checks on all touched files** ✅

- [x] **Step 7: Run the focused tests** ✅

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest,SocialUseCasesTest,MessagingUseCasesTest,DashboardViewModelTest,StatsViewModelTest test`

Expected: green.

- [ ] **Step 8: Commit** ✅ (implementation complete, commit deferred per session policy)

    - 2026-03-30: implementation and verification completed; commit intentionally deferred while continuing through the plan in the same session.

Commit message:
`refactor: require real event bus and unify achievement facade`

## Chunk 2: Boundary and helper normalization

### Task 3: Route adapters through existing use-case seams and add the missing CLI summary queries

**Execution mode:** main agent only

**Files:**
- Create: `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/cli/{ProfileHandler,SafetyHandler,MatchingHandler}.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/Main.java`
- Test: `src/test/java/datingapp/app/usecase/{matching/MatchingUseCasesTest,profile/ProfileUseCasesTest}.java`
- Test: `src/test/java/datingapp/app/cli/{MatchingHandlerTest,SafetyHandlerTest,ProfileHandlerTest}.java`
- Test: `src/test/java/datingapp/app/api/{RestApiReadRoutesTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest,RestApiRelationshipRoutesTest}.java`
- Test: `src/test/java/datingapp/MainLifecycleTest.java`

- [x] **Step 1: Add failing characterization tests for adapter purity and the CLI read-model gap**

   - 2026-03-30: added characterization coverage for `VerificationUseCases`, `ProfileUseCases.getSessionSummary(...)`, `SafetyHandler` adapter-boundary behavior, and `MainLifecycleTest` coverage that directly locks the `Main.printMenu(...)` use-case seam.

Lock these rules:

- notes/user selection in `ProfileHandler` use app-layer APIs consistently
- `SafetyHandler` does not own verification state mutation directly
- `MatchingHandler` undo and standout resolution stay inside `MatchingUseCases`
- `Main.printMenu(...)` and `MatchingHandler.showDailyLimitReached(...)` use app-layer read queries instead of raw service reads
- the REST candidate exception remains explicit and isolated if it is not removed yet

- [x] **Step 2: Introduce the missing use-case seams before changing result contracts**

Implement concrete changes:

- [x] `ProfileHandler` stops mixing `userStorage` note reads with `profileUseCases` writes — note reads now route through `profileUseCases`
- [x] `SafetyHandler` reuses `ProfileUseCases.listUsers()` for user-selection reads and moves verification start/confirm flows into `VerificationUseCases`
- [x] `MatchingHandler` stops calling `undoService` and `standoutsService.resolveUsers(...)` directly when `MatchingUseCases` already owns the flow
- [x] `MatchingUseCases` gets a small read API for daily-limit summary (`DailyStatusResult`/reset summary) so `MatchingHandler.showDailyLimitReached(...)` stops reaching into `RecommendationService`
- [x] `MatchingUseCases` gets `getUndoAvailability()` and `getDailyStatus()` query methods with typed result records
- [x] `ProfileUseCases` gets a small session-summary read API so `Main.printMenu(...)` stops reaching into `ActivityMetricsService`
- [x] `Main.printMenu(...)` now reads session summary through `ProfileUseCases` and daily-like status through `MatchingUseCases`

   - 2026-03-30 verification: focused stability pack green for `SafetyHandlerTest`, `MatchingHandlerTest`, `ProfileHandlerTest`, `ProfileUseCasesTest`, and `VerificationUseCasesTest`.
- [x] `MatchingUseCases` constructor now takes 11 params (added `RecommendationService` as last param)
- [x] `MatchingHandler.promptUndo()` now routes through `matchingUseCases.getUndoAvailability()` and `matchingUseCases.undoSwipe()`
- [x] `MatchingHandler.viewStandouts()` no longer has the `standoutsService.resolveUsers()` fallback
- [x] `MatchingCliPresenter.dailyLimitReachedLines()` simplified to accept `(int likesUsed, String timeUntilReset)` instead of `RecommendationService.DailyStatus`
- [x] `ProfileUseCases` gets a small session-summary read API so `Main.printMenu(...)` stops reaching into `ActivityMetricsService`
- [ ] 8 test files updated to pass 11th `recommendationService` param to `MatchingUseCases` constructor:
      - [x] `MatchingHandlerTest.java`
      - [x] `LikerBrowserHandlerTest.java`
      - [x] `RelationshipHandlerTest.java`
      - [x] `MatchingTransactionTest.java`
      - [x] `MatchingControllerTest.java`
      - [x] `MatchesViewModelTest.java` (2 constructor sites)
      - [x] `MatchesControllerTest.java`
      - [x] `RestApiPhaseTwoRoutesTest.java` (2 inner class `super()` calls)
- [x] All 1530 tests pass (`mvn -Dcheckstyle.skip=true test`)

- [x] **Step 3: Keep the REST candidate path explicit**

   - 2026-03-30: confirmed `RestApiServer.readCandidateSummaries(...)` remains the named direct-read helper with inline rationale, and `RestApiReadRoutesTest#candidatesRouteRemainsTheDeliberateDirectReadException` locks the exception explicitly.

Either:

- route it through `MatchingUseCases`, or
- leave it as a named, deliberate direct-read exception with one helper method and one test proving the exception is intentional

- [x] **Step 4: Run IDE/Problems checks on all touched files**

   - 2026-03-30: editor/error checks green for `Main.java`, `MainLifecycleTest.java`, `ProfileViewModelTest.java`, `SafetyHandler.java`, and `SafetyHandlerTest.java` after the final Task 3 seam cleanup.

- [x] **Step 5: Run the focused tests**

   - 2026-03-30: targeted Task 3 regression pack green (`MatchingUseCasesTest`, `ProfileUseCasesTest`, `MatchingHandlerTest`, `SafetyHandlerTest`, `ProfileHandlerTest`, `RestApiReadRoutesTest`, `RestApiNotesRoutesTest`, `RestApiPhaseTwoRoutesTest`, `RestApiRelationshipRoutesTest`, `MainLifecycleTest`) — 120 tests passed.

Run:
`mvn -Dcheckstyle.skip=true -Dtest=MatchingUseCasesTest,ProfileUseCasesTest,MatchingHandlerTest,SafetyHandlerTest,ProfileHandlerTest,RestApiReadRoutesTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest,RestApiRelationshipRoutesTest,MainLifecycleTest test`

Expected: green.

- [ ] **Step 6: Commit** ✅ (implementation complete, commit deferred per session policy)

   - 2026-03-30: Task 3 implementation and focused verification completed; commit intentionally deferred while continuing through later tasks in the same session.

Commit message:
`refactor: route adapters through app-layer seams`

### Task 4: Replace remaining service-owned use-case result leaks with app-owned contracts

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: adapter consumers under `src/main/java/datingapp/app/cli/`, `src/main/java/datingapp/app/api/`, and `src/main/java/datingapp/ui/viewmodel/`
- Test: `src/test/java/datingapp/app/usecase/{matching/MatchingUseCasesTest,social/SocialUseCasesTest,profile/ProfileUseCasesTest}.java`
- Test: `src/test/java/datingapp/app/cli/{MatchingHandlerTest,SafetyHandlerTest,ProfileHandlerTest}.java`
- Test: `src/test/java/datingapp/app/api/{RestApiReadRoutesTest,RestApiPhaseTwoRoutesTest}.java`

- [x] **Step 1: Add failing tests that lock the new app-layer contract policy**

   - 2026-03-30: added characterization coverage that locks the new app-layer contract access patterns in `MatchingUseCasesTest` and `SocialUseCasesTest` (swipe/undo snapshots, relationship transition snapshots, report outcome snapshot, and match-quality snapshot).

Lock this rule into tests/documentation:

- first wave removes service-owned result wrappers from public use-case APIs
- immutable domain entities may remain where that is intentionally the business language
- adapters no longer depend on `MatchingService.SwipeResult`, `UndoService.UndoResult`, `ConnectionService.TransitionResult`, `TrustSafetyService.ReportResult`, or `MatchQualityService.MatchQuality`

- [x] **Step 2: Introduce app-owned result records in the use-case layer**

   - 2026-03-30: introduced `MatchingUseCases.SwipeOutcome`, `UndoOutcome`, and `MatchQualitySnapshot`, plus `SocialUseCases.ReportOutcome` and `RelationshipTransitionOutcome`, while intentionally leaving domain entities such as `User`, `Match`, and `ProfileNote` unwrapped in this wave.

Define new app-layer records next to the use cases, e.g.:

- swipe outcome / undo outcome snapshots
- relationship transition result snapshots
- report result snapshots
- match quality snapshots

Do **not** wrap every `User`/`ProfileNote`/`Match` blindly in this step.

- [x] **Step 3: Migrate adapters to the new result records**

   - 2026-03-30: migrated CLI, REST, and UI consumers (`MatchingHandler`, `MatchingCliPresenter`, `SafetyHandler`, `RestApiDtos`, `RestApiServer`, `MatchingViewModel`, `ChatViewModel`) to the new app-owned result types.
   - 2026-03-30 source audit: no remaining adapter/UI references to `MatchingService.SwipeResult`, `UndoService.UndoResult`, `ConnectionService.TransitionResult`, `TrustSafetyService.ReportResult`, or `MatchQualityService.MatchQuality`.

Update CLI/REST/UI consumers to depend on the app-owned result types instead of service-owned wrappers.

- [x] **Step 4: Run IDE/Problems checks on all touched files**

   - 2026-03-30: editor/error checks green for the touched Task 4 production and test files after the consumer migration.

- [x] **Step 5: Run the focused tests**

   - 2026-03-30: focused Task 4 verification pack green (`MatchingUseCasesTest`, `SocialUseCasesTest`, `ProfileUseCasesTest`, `MatchingHandlerTest`, `SafetyHandlerTest`, `ProfileHandlerTest`, `RestApiReadRoutesTest`, `RestApiPhaseTwoRoutesTest`) — 121 tests passed.

Run:
`mvn -Dcheckstyle.skip=true -Dtest=MatchingUseCasesTest,SocialUseCasesTest,ProfileUseCasesTest,MatchingHandlerTest,SafetyHandlerTest,ProfileHandlerTest,RestApiReadRoutesTest,RestApiPhaseTwoRoutesTest test`

Expected: green.

- [ ] **Step 6: Commit** ✅ (implementation complete, commit deferred per session policy)

   - 2026-03-30: Task 4 implementation and focused verification completed; commit intentionally deferred while continuing through later tasks in the same session.

Commit message:
`refactor: make use-case results app-owned`

### Task 5: Standardize domain helper reuse and collection semantics

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/model/Match.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/storage/DevDataSeeder.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`
- Test: `src/test/java/datingapp/app/{cli/ProfileHandlerTest,usecase/profile/ProfileUseCasesTest}.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`

- [x] **Step 1: Add characterization tests for helper reuse and collection semantics**

   - 2026-03-30: added characterization coverage for canonical deterministic IDs in `Match.create(...)` and `Conversation.create(...)`, plus empty-set clearing semantics in `ProfileUseCasesTest` and `ProfileViewModelTest`.

Lock these rules:

- `Match.create(...)` and `Conversation.create(...)` use the same ID generation logic as `generateId(...)`
- `User.copy()` is the one deep-copy entry point used by application code
- empty selection means clear, `null` means leave unchanged
- seed conversation writes use the atomic helper path

- [x] **Step 2: Replace duplicated helper logic with the canonical helpers**

   - 2026-03-30: `Match.create(...)` now delegates its ID to `Match.generateId(...)`; `Conversation.create(...)` now delegates to `Conversation.generateId(...)`; `User.copy()` now uses `dealbreakers.toBuilder().build()`; `TrustSafetyService.copyUser(...)` and `ProfileHandler.copyForProfileEditing(...)` now delegate to `User.copy()`; seed conversation writes now use `saveMessageAndUpdateConversationLastMessageAt(...)`.

Concrete changes:

- `Match.create(...)` delegates to `Match.generateId(...)`
- `Conversation.create(...)` delegates to `Conversation.generateId(...)`
- `User.copy()` uses `dealbreakers.toBuilder().build()`
- `TrustSafetyService.copyUser(...)` and `ProfileHandler.copyForProfileEditing(...)` delegate to `User.copy()`
- `DevDataSeeder` uses `saveMessageAndUpdateConversationLastMessageAt(...)` and consistent enum-set helpers

- [x] **Step 3: Normalize set/clear semantics across profile update flows**

   - 2026-03-30: `ProfileUseCases.updateDiscoveryPreferences(...)`, `ProfileUseCases.updateProfile(...)`, and `ProfileViewModel` draft/save application now treat `null` as omit/leave-unchanged and an explicitly empty set as clear.

Concrete rule to implement:

- field omitted (`null`) → leave unchanged
- empty set provided → clear selection

Apply it consistently in `ProfileUseCases` and `ProfileViewModel`.

- [x] **Step 4: Run IDE/Problems checks on all touched files**

   - 2026-03-30: Problems checks green for the touched Task 5 source and test files after the helper-reuse and semantics changes.

- [x] **Step 5: Run the focused tests**

   - 2026-03-30: focused Task 5 verification pack green (`ConnectionServiceAtomicityTest`, `ProfileHandlerTest`, `ProfileUseCasesTest`, `ProfileViewModelTest`) — 50 tests passed.
   - 2026-03-30: additional targeted regressions for `ProfileHandlerTest`, `SafetyHandlerTest`, `SocialUseCasesTest`, and `VerificationUseCasesTest` also green — 60 tests passed.

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ConnectionServiceAtomicityTest,ProfileUseCasesTest,ProfileHandlerTest,ProfileViewModelTest test`

Expected: green.

- [ ] **Step 6: Commit** ✅ (implementation complete, commit deferred per session policy)

   - 2026-03-30: Task 5 implementation and focused verification completed; commit intentionally deferred while continuing through later tasks in the same session.

Commit message:
`refactor: reuse domain helpers consistently`

### Task 6: Consolidate validation ownership and typed mutator contracts

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ValidationService.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileFormValidator.java`
- Modify: consumers of `deleteAccount(...)` / `deleteProfileNote(...)`
- Test: `src/test/java/datingapp/app/usecase/{messaging/MessagingUseCasesTest,profile/ProfileUseCasesTest}.java`
- Test: `src/test/java/datingapp/ui/{viewmodel/ProfileViewModelTest,screen/ProfileControllerTest}.java`
- Test: `src/test/java/datingapp/app/api/{RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest}.java`

- [x] **Step 1: Add failing tests for the new validation ownership rules**

   - 2026-03-30: added/extended focused coverage locking the restored session-summary seam plus the Task 6 contract changes in `ProfileUseCasesTest`, `ProfileUseCasesNotesTest`, `MessagingUseCasesTest`, `MainLifecycleTest`, `ProfileControllerTest`, `RestApiNotesRoutesTest`, and `RestApiPhaseTwoRoutesTest`.

Lock these outcomes:

- the profile-edit UI does not create a fresh validator through `ProfileViewModel.getValidationService()`
- message and note validation live in a shared boundary
- deletion reason typing is explicit
- mutators stop returning redundant booleans where no extra state is conveyed

- [x] **Step 2: Choose and implement the validator boundary**

   - 2026-03-30: `ValidationService` now owns shared message-content and profile-note-content validation; `MessagingUseCases` and `ProfileUseCases` consume those validators; `ProfileViewModel` now returns the injected/shared `ValidationService`; `ProfileController` continues to use `ProfileFormValidator` only as the thin UI parsing/message adapter.

Recommended shape:

- `ValidationService` owns business validation rules, including shared message-content and profile-note-content validation
- `ProfileFormValidator` remains only as a thin UI parsing/message adapter
- `ProfileViewModel` stops owning duplicate field-validation logic and direct validation toasts

- [x] **Step 3: Type the deletion and delete-result contracts**

   - 2026-03-30: `DeleteAccountCommand` now takes `AppEvent.DeletionReason`; `deleteAccount(...)` and `deleteProfileNote(...)` now return `UseCaseResult<Void>`; direct consumers in CLI, REST, UI adapters, and tests were updated together.

Concrete changes:

- replace `DeleteAccountCommand(UserContext, String reason)` with a typed deletion-reason value
- replace `UseCaseResult<Boolean>` delete mutators with `UseCaseResult<Void>` or a dedicated app-owned result record where the extra payload truly matters

- [x] **Step 4: Run IDE/Problems checks on all touched files**

   - 2026-03-30: Problems/error checks were rerun on the touched Task 6 production and test files after the contract cleanup and were clean for the edited files.

- [x] **Step 5: Run the focused tests**

   - 2026-03-30: focused Task 6 verification pack green (`MessagingUseCasesTest`, `ProfileUseCasesTest`, `ProfileViewModelTest`, `ProfileControllerTest`, `RestApiNotesRoutesTest`, `RestApiPhaseTwoRoutesTest`) — 50 tests passed.

Run:
`mvn -Dcheckstyle.skip=true -Dtest=MessagingUseCasesTest,ProfileUseCasesTest,ProfileViewModelTest,ProfileControllerTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest test`

Expected: green.

- [ ] **Step 6: Commit** ✅ (implementation complete, commit deferred per session policy)

   - 2026-03-30: Task 6 implementation and focused verification completed; commit intentionally deferred while continuing through Task 7 in the same session.

Commit message:
`refactor: unify validation ownership and typed delete contracts`

## Chunk 3: UI and test harness completion

### Task 7: Finish the UI abstraction migration

**Execution mode:** main agent owns the framework changes; leaf ViewModel conversions may be parallelized only after `BaseViewModel` and `UiDataAdapters` stabilize

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Create: `src/main/java/datingapp/ui/UiThemeService.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/{ChatViewModel,MatchesViewModel,PreferencesViewModel,ProfileViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,DashboardViewModel,LoginViewModel,NotesViewModel,SafetyViewModel}.java`
- Modify: `src/main/java/datingapp/ui/NavigationService.java`
- Modify: `src/main/java/datingapp/ui/UiComponents.java`
- Modify: `src/main/java/datingapp/ui/screen/PreferencesController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/{ChatViewModelTest,MatchesViewModelTest,PreferencesViewModelTest,ProfileViewModelTest,SocialViewModelTest,StandoutsViewModelTest,StatsViewModelTest,DashboardViewModelTest,LoginViewModelTest,NotesViewModelTest,SafetyViewModelTest,MatchingViewModelTest}.java`
- Test: `src/test/java/datingapp/ui/{NavigationServiceTest,NavigationServiceContextTest}.java`
- Test: `src/test/java/datingapp/ui/screen/{ChatControllerTest,MatchingControllerTest,BaseControllerTest}.java`

- [x] **Step 1: Add failing tests for the base lifecycle and paging boundary decisions**

   - 2026-03-30: added/extended focused locking coverage in `BaseViewModelTest`, `UiDataAdaptersTest`, `PreferencesViewModelTest`, and `MatchesViewModelTest` for late-bound error sinks, the UI-owned page wrapper, and theme changes flowing through a dedicated UI theme façade.

Lock these rules:

- `BaseViewModel` can support late-bound error sinks centrally
- remaining ViewModels can migrate without reintroducing parallel async/error plumbing
- `UiDataAdapters` no longer leak `PageData`
- theme persistence has one owner only

- [x] **Step 2: Upgrade the shared UI abstractions first**

   - 2026-03-30: `BaseViewModel` now supports late-bound `setErrorSink(...)`; `UiDataAdapters` now exposes `UiPage<T>` instead of leaking `PageData` through the UI adapter boundary; `UiThemeService` was introduced and wired into `PreferencesViewModel`; `MatchesViewModel` was updated to consume `UiPage`; and `ViewModelFactory` now constructs `PreferencesViewModel` through the theme façade.

Concrete changes:

- add centralized late-bound error-sink support to `BaseViewModel`
- remove `ViewModelFactory`’s reflection fallback disposal path once all ViewModels comply
- introduce a UI-owned paged result type such as `UiPage<T>` or `PagedItems<T>` in `UiDataAdapters`
- introduce `UiThemeService` as the only ViewModel-facing theme API; it may delegate to `NavigationService`/`UiPreferencesStore`, but `PreferencesViewModel` must stop calling `NavigationService.getInstance()` directly

- [x] **Step 3: Migrate remaining ViewModels and controllers onto the stabilized abstractions**

   - 2026-03-30: completed the remaining production lifecycle migration slice by moving `ChatViewModel` and `ProfileViewModel` onto `BaseViewModel`, removing the final `ViewModelFactory` reflection disposal fallback, and routing `PreferencesController.handleSave()` through `BaseController.handleBack()`. Remaining convenience constructors stay outside the production composition root as compatibility/test shims.

Concrete changes:

- move remaining ViewModels onto `BaseViewModel`
- remove per-class `asyncScope` / `loading` / manual-dispose duplication where base support now exists
- stop direct `new JavaFxUiThreadDispatcher()` self-composition in production constructors
- replace `PreferencesController` direct `goBack()` call with the base helper
- keep raw `Platform.runLater(...)` / `Thread.ofVirtual()` only in deliberately low-level bridge code

- [x] **Step 4: Parallelize only after the shared abstractions are green**

Safe split after Step 2:

- Subagent A: `ChatViewModel`, `MatchesViewModel`, `SocialViewModel`, `StandoutsViewModel`
- Subagent B: `DashboardViewModel`, `LoginViewModel`, `NotesViewModel`, `SafetyViewModel`, `PreferencesViewModel`

Do **not** split `BaseViewModel`, `UiDataAdapters`, `ViewModelFactory`, or `NavigationService`.

   - 2026-03-30: superseded in practice. After the shared abstractions stabilized, the remaining Task 7 leaf work was completed directly in one coordinated slice rather than a separate subagent split.

- [x] **Step 5: Run IDE/Problems checks on all touched files**

   - 2026-03-30: Problems/error checks were rerun on the shared Task 7 foundation files (`BaseViewModel`, `UiThemeService`, `UiDataAdapters`, `MatchesViewModel`, `PreferencesViewModel`, `ViewModelFactory`, and the new/updated tests) and were clean for the edited files.

- [x] **Step 6: Run the focused tests**

   - 2026-03-30: shared Task 7 foundation verification green (`BaseViewModelTest`, `UiDataAdaptersTest`, `PreferencesViewModelTest`, `MatchesViewModelTest`, `SocialViewModelTest`, `StandoutsViewModelTest`, `StatsViewModelTest`, `NavigationServiceTest`, `NavigationServiceContextTest`) — 49 tests passed.

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ChatViewModelTest,MatchesViewModelTest,PreferencesViewModelTest,ProfileViewModelTest,SocialViewModelTest,StandoutsViewModelTest,StatsViewModelTest,DashboardViewModelTest,LoginViewModelTest,NotesViewModelTest,SafetyViewModelTest,MatchingViewModelTest,NavigationServiceTest,NavigationServiceContextTest,ChatControllerTest,MatchingControllerTest,BaseControllerTest test`

Expected: green.

- [ ] **Step 7: Commit**

   - 2026-03-30: implementation and verification completed; commit intentionally deferred per session policy.

Commit message:
`refactor: finish ui abstraction migration`

### Task 8: Standardize the test harness and helper layers

**Execution mode:** hybrid — main agent creates shared fixtures, then targeted subagents convert independent test clusters

**Files:**
- Modify: `src/test/java/datingapp/ui/JavaFxTestSupport.java`
- Modify: `src/test/java/datingapp/core/testutil/{TestStorages,TestUserFactory}.java`
- Create: `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- Create: `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` (or similar shared async helper)
- Modify: JavaFX tests currently bypassing `JavaFxTestSupport`
- Modify: REST/API tests currently duplicating `createServices(...)`
- Modify: tests with local `createActiveUser(...)` / `createEditableUser(...)` where `TestUserFactory` is sufficient
- Modify: reflection-heavy tests where a real public seam already exists
- Modify: `src/test/java/datingapp/ui/JavaFxCssValidationTest.java`
- Modify: `src/test/java/datingapp/app/api/{RestApiConversationBatchCountTest,RestApiRateLimitTest,RestApiDailyLimitTest}.java`
- Modify: `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

- [x] **Step 1: Add the shared fixtures/support types first**

Create the reusable pieces before bulk test conversion:

- shared REST fixture builder
- shared async queue/drain helper extracted from `ViewModelAsyncScopeTest`
- any small JavaFX convenience helpers still missing from `JavaFxTestSupport`

   - 2026-03-30: added `RestApiTestFixture` and `UiAsyncTestSupport`; existing `JavaFxTestSupport` already covered the needed JavaFX helper surface for the targeted migrations.

- [x] **Step 2: Convert JavaFX test harness usage**

Convert the JavaFX tests that still call `Platform.startup(...)`, local `waitUntil(...)`, or local FX-latch helpers.

   - 2026-03-30: converted `NotesViewModelTest`, `JavaFxCssValidationTest`, and `ViewModelAsyncScopeTest` to the shared harness/support helpers.

- [x] **Step 3: Convert REST/API graph construction**

Replace per-file `createServices(...)` duplication with the new shared fixture builder.

   - 2026-03-30: converted the targeted REST/API regression files (`RestApiHealthRoutesTest`, `RestApiReadRoutesTest`, `RestApiNotesRoutesTest`, `RestApiConversationBatchCountTest`, `RestApiRateLimitTest`, `RestApiDailyLimitTest`, `RestApiRelationshipRoutesTest`, and `RestApiPhaseTwoRoutesTest`) onto `RestApiTestFixture` where the wiring was truly duplicated; `PhaseTwo` kept its custom seeded standout storage.

- [x] **Step 4: Standardize on `TestUserFactory` where its default shape is enough**

Do not force it into tests that genuinely need specialized builders, but remove needless duplicate helpers.

   - 2026-03-30: removed low-risk duplicate user builders in `ProfileHandlerTest`, `StatsViewModelTest`, `MatchesViewModelTest`, and `ProfileViewModelTest` while preserving the test-specific field adjustments those cases needed.

- [x] **Step 5: Narrow or remove reflection-heavy tests when a real seam exists**

Prioritize:

- `ProfileHandlerTest`
- `MatchingHandlerTest`
- `BaseControllerTest`
- `MilestonePopupControllerTest`
- `MatchesControllerTest`

   - 2026-03-30: completed the low-risk cleanup slice inside the targeted file set. `BaseControllerTest` was intentionally left unchanged because removing its reflection would require introducing a new production seam, which was judged outside this plan’s low-risk test-harness scope.

- [x] **Step 6: Parallelize the leaf conversions**

Safe split after Step 1:

- Subagent A: JavaFX harness conversions
- Subagent B: REST/API fixture conversions
- Subagent C: helper/reflection cleanup

   - 2026-03-30: completed with one subagent per independent conversion batch and reintegrated the results into the main session.

- [x] **Step 7: Run IDE/Problems checks on all touched files**

   - 2026-03-30: Problems/error checks were rerun on the new helpers and the edited test files after integration; the touched files were clean.

- [x] **Step 8: Run the focused tests**

Run:
`mvn -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,SocialViewModelTest,StandoutsViewModelTest,SafetyViewModelTest,NotesViewModelTest,ChatViewModelTest,ProfileViewModelTest,PreferencesViewModelTest,MatchingViewModelTest,JavaFxCssValidationTest,RestApiHealthRoutesTest,RestApiReadRoutesTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest,RestApiConversationBatchCountTest,RestApiRateLimitTest,RestApiDailyLimitTest,MatchingFlowIntegrationTest,ProfileHandlerTest,MatchingHandlerTest,BaseControllerTest test`

Expected: green.

   - 2026-03-30: targeted Task 8 verification pack green (`LoginViewModelTest`, `SocialViewModelTest`, `StandoutsViewModelTest`, `SafetyViewModelTest`, `NotesViewModelTest`, `ChatViewModelTest`, `ProfileViewModelTest`, `PreferencesViewModelTest`, `MatchingViewModelTest`, `JavaFxCssValidationTest`, `RestApiHealthRoutesTest`, `RestApiReadRoutesTest`, `RestApiNotesRoutesTest`, `RestApiPhaseTwoRoutesTest`, `RestApiConversationBatchCountTest`, `RestApiRateLimitTest`, `RestApiDailyLimitTest`, `MatchingFlowIntegrationTest`, `ProfileHandlerTest`, `MatchingHandlerTest`, `BaseControllerTest`) — 113 tests passed.

- [ ] **Step 9: Commit**

   - 2026-03-30: implementation and verification completed; commit intentionally deferred per session policy.

Commit message:
`test: standardize shared harnesses and helpers`

### Task 9: Final verification and closeout

**Execution mode:** main agent only

**Files:**
- Review all touched source and test files
- Update: `2026-03-29-abstraction-consistency-report.md` only if implementation decisions intentionally changed the recommended target abstraction

- [x] **Step 1: Run targeted smoke suites by workstream**

Run:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest,SocialUseCasesTest,MessagingUseCasesTest,ProfileHandlerTest,SafetyHandlerTest,MatchingHandlerTest,ProfileViewModelTest,MatchesViewModelTest,ChatViewModelTest,PreferencesViewModelTest,StatsViewModelTest,DashboardViewModelTest,LoginViewModelTest,NotesViewModelTest,RestApiReadRoutesTest,RestApiNotesRoutesTest,RestApiPhaseTwoRoutesTest,RestApiRelationshipRoutesTest,MainLifecycleTest,ConnectionServiceAtomicityTest test`

Expected: green.

   - 2026-03-30: smoke suite green — 203 tests passed.

- [x] **Step 2: Run full quality verification**

Run:
`mvn spotless:apply verify`

Expected: exit code `0`, Spotless/Checkstyle/PMD/JaCoCo all passing.

   - 2026-03-30: `mvn spotless:apply verify` green — `Tests run: 1552, Failures: 0, Errors: 0, Skipped: 1`; Spotless, Checkstyle, PMD, and JaCoCo all passed.

- [x] **Step 3: Re-read the implementation report and confirm each item is either done, intentionally deferred, or superseded**

Do not close the work on “tests pass” alone. Reconcile the finished changes against:

- `2026-03-29-abstraction-consistency-report.md`
- this plan

   - 2026-03-30 reconciliation: the abstraction targets in the report and plan are satisfied by the implemented changes. Remaining non-blocking cleanup outside the final targeted slices was explicitly treated as intentionally excluded where it would have required new production seams rather than shared-harness normalization.

- [ ] **Step 4: Commit**

   - 2026-03-30: deferred per session policy.

Commit message:
`refactor: complete abstraction consistency remediation`

## Suggested execution order summary

1. Task 1 — composition roots and constructor contracts
2. Task 2 — event bus and achievement boundary
3. Task 3 — adapter boundary cleanup and missing CLI summary queries
4. Task 4 — app-owned use-case result contracts
5. Task 5 — domain helper reuse and set semantics
6. Task 6 — validation and typed mutator contracts
7. Task 7 — UI abstraction migration
8. Task 8 — test harness cleanup
9. Task 9 — final verification

## Definition of done

This plan is complete only when all of the following are true:

- production construction is centralized in `ServiceRegistry` / `ViewModelFactory`
- production use-cases no longer silently drop events because of `null`/no-op bus paths
- `AchievementService` is the only achievement facade
- adapters no longer bypass existing business-flow use-case APIs in the targeted areas
- duplicated ID/copy/message-write helpers are collapsed to the canonical helpers
- validation ownership is explicit and not split three ways across controller/viewmodel/use-case code
- remaining ViewModels follow the shared lifecycle pattern
- `UiDataAdapters` no longer leak storage paging types to ViewModels
- tests rely on shared harnesses/helpers instead of re-creating them file-by-file
- `mvn spotless:apply verify` passes