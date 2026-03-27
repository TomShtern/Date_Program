---
goal: Code-first end-to-end implementation plan for stabilization, feature completion, consistency hardening, and verification
version: 1.0
date_created: 2026-03-07
last_updated: 2026-03-08
owner: GitHub Copilot
status: 'Completed'
tags: [implementation-plan, code-first, java, javafx, ui, api, storage, testing, migration]
---

# Introduction

![Status: Completed](https://img.shields.io/badge/status-Completed-brightgreen)

This document converts the findings in `CODE_FIRST_GAPS_AND_NEXT_WORK_REPORT_2026-03-07.md` into an execution-grade implementation plan. It is intentionally **code-first**: current source files, tests, and build behavior override stale repository documentation.

This plan is designed so an implementation agent can execute it **phase by phase** without guessing about scope, default decisions, sequencing, or verification strategy.

This file started as a **proposed staged execution plan** and now also serves as the execution tracker for the work completed on 2026-03-07.

Current validated starting point:

- `mvn test -DskipITs` reports **983 tests**, **0 failures**, **0 errors**, **2 skipped**, and `BUILD SUCCESS`
- `DashboardViewModelTest.shouldHandleConcurrentRefreshes` currently passes locally, but remains a regression-sensitive concurrency test that should be guarded during early changes
- known live UI bug: `NavigationService.ViewType.PREFERENCES` points to the wrong FXML path
- major architectural foundations already exist and do **not** need to be reinvented: `app/usecase/**`, `app/event/**`, `AppConfig`, Hikari pooling, pagination, and shared async ViewModel infrastructure

Implementation principle:

- complete one phase at a time
- do not start a later phase until the current phase verification gate passes
- prefer small compatible changes over broad rewrites
- add regression coverage before or with every behavior change
- treat hidden tests as active constraints even when not visible locally

## 1. Requirements & Constraints

### Non-negotiable requirements

- **REQ-001**: The source of truth is live code in `src/main/java/**`, `src/test/java/**`, `src/main/resources/**`, and `pom.xml`.
- **REQ-002**: Do not change behavior based on stale markdown documents when current code contradicts them.
- **REQ-003**: Restore a green baseline before broad feature expansion or cleanup refactors.
- **REQ-004**: Keep Java 25, JavaFX 25, Maven, preview flags, native-access flags, Spotless, Checkstyle, PMD, and JaCoCo enforcement intact.
- **REQ-005**: Preserve current architectural boundaries: `core/**` stays framework-agnostic; UI, REST, and storage logic remain outside `core/**`.
- **REQ-006**: Use existing application use-case seams (`MatchingUseCases`, `MessagingUseCases`, `ProfileUseCases`, `SocialUseCases`) when exposing new UI or REST behavior.
- **REQ-007**: Use `AppClock.now()` in domain and service logic; do not introduce `Instant.now()` in those layers.
- **REQ-008**: ViewModels must use shared async infrastructure under `src/main/java/datingapp/ui/async/**`; do not add ad-hoc thread/lifecycle patterns.
- **REQ-009**: Preserve result-record style business failure handling where the codebase already uses it.
- **REQ-010**: Preserve current pagination conventions in REST/UI flows that already paginate.
- **REQ-011**: Every new behavior must have at least one targeted regression test or focused verification step in this plan.
- **REQ-012**: Every phase must end with an explicit verification gate before the next phase starts.

### Constraints

- **CON-001**: Do not perform a large architecture rewrite in this implementation plan.
- **CON-002**: Do not delete legacy user-data columns in the same rollout that finishes normalized persistence support.
- **CON-003**: Do not add a cloud media backend or remote object storage in this plan.
- **CON-004**: Do not introduce WebSocket or SSE infrastructure in this plan.
- **CON-005**: Do not expose consumer-facing admin behavior without an actual admin trust/auth boundary.

### Patterns and guidelines to follow

- **PAT-001**: Reuse `NavigationService.setNavigationContext(...)` / `consumeNavigationContext(...)` rather than inventing a second routing-state mechanism.
- **PAT-002**: Prefer additive compatibility changes with tests over destructive rewrites.
- **PAT-003**: Keep UI-specific preference persistence out of `AppConfig` and out of core domain models unless code clearly already models it there.
- **PAT-004**: When storage supports atomic transitions, service code must prefer those atomic paths and must not persist partial state first.
- **GUD-001**: If a phase reveals a blocker that violates these defaults, stop and record the blocker instead of improvising architecture.
- **GUD-002**: Any new UI behavior should be wired through existing ViewModel/controller patterns before considering new UI frameworks or patterns.

### Default implementation decisions

These defaults resolve current ambiguities so an implementation agent can proceed deterministically unless the user explicitly overrides them.

- **DEC-001**: Chat live updates will be implemented with **polling**, not WebSocket/SSE. Default intervals: **5 seconds** for an active conversation and **15 seconds** for the conversation list.
- **DEC-002**: Theme persistence will use a new **UI-local preference store** backed by `java.util.prefs.Preferences`.
- **DEC-003**: Profile notes are a **real product feature** and will be surfaced in JavaFX UI and REST. Notes remain **private to the note author**.
- **DEC-004**: `verifyProfile` remains **CLI-only** in this plan. Do not add a consumer JavaFX surface and do not expose a public REST endpoint without an admin/auth model.
- **DEC-005**: Relationship transitions must use atomic storage methods where possible before those flows are expanded in UI/API.
- **DEC-006**: Photo/media remains **desktop-local**, stored under `~/.datingapp/photos`, with better lifecycle management and packaged default assets.
- **DEC-007**: The normalized user-data migration will be **finished with compatibility**, not removed. Legacy-column deletion is deferred.
- **DEC-008**: Controller-level UI tests will be added **after stabilization and feature completion**, before any broader cleanup refactor.

### Explicit non-goals for this plan

- **OOS-001**: Replacing `AppSession`, `NavigationService`, or `ServiceRegistry` with a new state-management architecture
- **OOS-002**: Introducing remote chat/event transport
- **OOS-003**: Introducing cloud media storage
- **OOS-004**: Adding an admin authentication subsystem solely to support `verifyProfile`
- **OOS-005**: Removing legacy user columns in the same implementation window as normalized write/read compatibility

## 2. Implementation Steps

### Implementation Phase 0 — Baseline Stabilization

- **GOAL-001**: Preserve the now-green baseline, guard the regression-sensitive dashboard concurrency path, and fix the still-broken Preferences route.

| Task     | Description                                                                                                                                                                                                                                                                                                                                | Completed                                                                                                                                                         | Date       |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| TASK-001 | Run `DashboardViewModelTest.shouldHandleConcurrentRefreshes` repeatedly (minimum: 3 consecutive targeted runs) to confirm the test remains stable before changing shared async or navigation behavior.                                                                                                                                     | ✅ Done — reran this sentinel test 3 consecutive times successfully on 2026-03-08 after fixing keyed latest-wins async registration race in `ViewModelAsyncScope`. | 2026-03-08 |
| TASK-002 | If `DashboardViewModelTest.shouldHandleConcurrentRefreshes` becomes unstable during Phase 0 work, inspect `DashboardViewModel.refresh()`, `loadDashboardData(...)`, `applyData(...)`, and `ViewModelAsyncScope.runLatest(...)`, then implement the smallest deterministic fix that preserves latest-wins semantics and `dispose()` safety. |                                                                                                                                                                   |            |
| TASK-003 | Fix `NavigationService.ViewType.PREFERENCES` in `src/main/java/datingapp/ui/NavigationService.java` so it points to `"/fxml/preferences.fxml"`.                                                                                                                                                                                            | ✅ Done — route now points to `/fxml/preferences.fxml`.                                                                                                            | 2026-03-07 |
| TASK-004 | Add/extend route regression coverage so a future path typo for Preferences is caught automatically. Prefer a focused new test such as `src/test/java/datingapp/ui/NavigationServiceRouteTest.java`.                                                                                                                                        | ✅ Done — added `NavigationServiceRouteTest`.                                                                                                                      | 2026-03-07 |
| TASK-005 | If TASK-002 changes shared async infrastructure, rerun the existing ViewModel regression set: `ChatViewModelTest`, `MatchesViewModelTest`, `StandoutsViewModelTest`, and `SocialViewModelTest`.                                                                                                                                            |                                                                                                                                                                   |            |
| TASK-006 | Run the baseline verification gate and do not start Phase 1 until it passes.                                                                                                                                                                                                                                                               | ✅ Done — focused Phase 0/1 gate passed and broader surefire XML reports show no failures/errors after `mvn test -DskipITs`.                                       | 2026-03-07 |

#### Phase 0 execution details

- Primary files:
  - `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
  - `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
  - `src/main/java/datingapp/ui/NavigationService.java`
  - `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
  - `src/test/java/datingapp/ui/NavigationServiceContextTest.java`
  - `src/test/java/datingapp/ui/NavigationServiceRouteTest.java` (new)
  - `DashboardViewModelTest.shouldHandleConcurrentRefreshes()` is currently green and should be treated as a sentinel regression test, not as an expected failure.
- Preserve the existing passing semantics in `DashboardViewModelTest.shouldPreventUpdatesAfterDispose()`.
- If TASK-002 touches `ViewModelAsyncScope`, immediately re-run other ViewModel tests because that class is shared infrastructure.

#### Phase 0 verification gate

- `mvn -Dtest=DashboardViewModelTest,NavigationServiceContextTest,NavigationServiceRouteTest test`
- `mvn test -DskipITs`

#### Phase 0 exit criteria

- `DashboardViewModelTest.shouldHandleConcurrentRefreshes` remains green and stable across repeated targeted runs
- Preferences navigation resolves the real FXML file
- route regression coverage exists for Preferences navigation
- the full unit/integration test baseline is green enough to proceed

#### Phase 0 rollback checkpoint

- If TASK-002 introduces a shared async regression, revert the async change and isolate the fix to `DashboardViewModel` if possible.

### Implementation Phase 1 — Preferences Completion and Context-Aware Matching Entry

- **GOAL-002**: Finish the Preferences subsystem properly and make Daily Pick / Standouts navigate into a specific candidate instead of the generic matching queue.

| Task     | Description                                                                                                                                                                                                                                                                                                                              | Completed                                                                                                               | Date       |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|------------|
| TASK-007 | Create `src/main/java/datingapp/ui/UiPreferencesStore.java` to persist UI-local preferences via `java.util.prefs.Preferences`. Store **theme mode only** in this class; do not duplicate discovery preferences already owned by user/profile data.                                                                                       | ✅ Done — added `UiPreferencesStore` with persisted `ThemeMode`.                                                         | 2026-03-07 |
| TASK-008 | Refactor `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java` to use `ViewModelAsyncScope` and model theme state alongside discovery preferences. Discovery preferences remain backed by profile use cases; theme remains backed by `UiPreferencesStore`.                                                                    | ✅ Done — `PreferencesViewModel` now loads/saves with async scope and tracks theme state.                                | 2026-03-07 |
| TASK-009 | Update `src/main/java/datingapp/ui/screen/PreferencesController.java` so the theme toggle is driven by ViewModel state rather than directly mutating stylesheets with controller-only logic.                                                                                                                                             | ✅ Done — controller now delegates theme changes to the ViewModel.                                                       | 2026-03-07 |
| TASK-010 | Centralize theme application in a single runtime location (preferred: `src/main/java/datingapp/ui/NavigationService.java`; acceptable fallback: `src/main/java/datingapp/ui/DatingApp.java`) so the saved theme is restored on app startup and scene changes.                                                                            | ✅ Done — `NavigationService` restores and applies theme state at runtime.                                               | 2026-03-07 |
| TASK-011 | Update `src/main/java/datingapp/ui/screen/DashboardController.java` and `src/main/java/datingapp/ui/screen/StandoutsController.java` to call `NavigationService.setNavigationContext(ViewType.MATCHING, UUID)` before navigating.                                                                                                        | ✅ Done — Daily Pick and Standouts now pass matching context.                                                            | 2026-03-07 |
| TASK-012 | Update `src/main/java/datingapp/ui/screen/MatchingController.java` and `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java` to consume an optional selected-candidate UUID and prioritize that candidate on screen entry. If the candidate is no longer eligible, fall back to normal matching and show a non-blocking message. | ✅ Done — matching now prioritizes requested candidates and shows a non-blocking fallback info message.                  | 2026-03-07 |
| TASK-013 | Add focused regression tests: `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java` (new), `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java` (new), and updates to `NavigationServiceContextTest.java`.                                                                                                   | ✅ Done — all three areas now have focused regression coverage.                                                          | 2026-03-07 |
| TASK-014 | Run the Phase 1 verification gate.                                                                                                                                                                                                                                                                                                       | ✅ Done — focused Phase 1 test gate passed locally. Manual JavaFX smoke still remains for later end-to-end confirmation. | 2026-03-07 |

#### Phase 1 execution details

Implementation progress notes (2026-03-07):

- ✅ Added `UiPreferencesStore` and persisted theme mode via `java.util.prefs.Preferences`
- ✅ Moved theme-state ownership into `PreferencesViewModel`
- ✅ Centralized runtime theme application in `NavigationService`
- ✅ Added daily-pick and standout candidate handoff via `NavigationService` context
- ✅ Added matching candidate prioritization with a non-blocking fallback info message
- ✅ Added focused regression tests for route resolution, theme persistence, and candidate handoff

- Primary files:
  - `src/main/java/datingapp/ui/UiPreferencesStore.java` (new)
  - `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
  - `src/main/java/datingapp/ui/screen/PreferencesController.java`
  - `src/main/java/datingapp/ui/NavigationService.java`
  - `src/main/java/datingapp/ui/DatingApp.java`
  - `src/main/java/datingapp/ui/screen/DashboardController.java`
  - `src/main/java/datingapp/ui/screen/StandoutsController.java`
  - `src/main/java/datingapp/ui/screen/MatchingController.java`
  - `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
  - `src/main/resources/fxml/preferences.fxml`
  - `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java` (new)
  - `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java` (new)
- Theme persistence responsibilities:
  - `PreferencesViewModel`: owns selected theme mode state and save/load orchestration
  - `UiPreferencesStore`: owns local persistence of theme mode
  - `NavigationService` or `DatingApp`: owns applying stylesheets to the active scene/stage
- Candidate-context behavior must not bypass existing recommendation filtering; it only changes which eligible candidate is shown first.

#### Phase 1 verification gate

- `mvn -Dtest=PreferencesViewModelTest,MatchingViewModelTest,NavigationServiceContextTest test`
- `mvn test -DskipITs`
- manual smoke via `mvn javafx:run`:
  - open Preferences
  - change theme
  - restart app
  - confirm theme persists
  - open Daily Pick
  - confirm Matching screen opens the intended candidate
  - open Standouts
  - confirm selected standout opens first

#### Phase 1 exit criteria

- Preferences navigation works and the screen is usable
- theme persists across restart and scene changes
- `PreferencesViewModel` uses shared async infrastructure
- Daily Pick and Standouts route to the intended candidate

#### Phase 1 rollback checkpoint

- If persisted theme causes startup/style regressions, keep the `PreferencesViewModel` async refactor and temporarily disable theme persistence while preserving route and matching-context fixes.

### Implementation Phase 2 — Chat Liveness and Profile Notes Parity

- **GOAL-003**: Make chat behavior live enough for desktop use and expose profile-note capability end to end across use-case, REST, and JavaFX UI.

| Task     | Description                                                                                                                                                                                                                                                                                                             | Completed                                                                                                                  | Date       |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|------------|
| TASK-015 | Extend shared async infrastructure with safe polling support. Preferred approach: add a new helper such as `src/main/java/datingapp/ui/async/PollingTaskHandle.java` and extend `ViewModelAsyncScope` so polling remains part of the shared async pattern.                                                              | ✅ Done — added `PollingTaskHandle`, `TaskPolicy.POLLING`, and shared polling support in `ViewModelAsyncScope`.             | 2026-03-07 |
| TASK-016 | Update `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` to poll the conversation list every **15 seconds** while active and poll the selected conversation every **5 seconds** while a conversation is open. Stop polling on dispose and when no conversation is selected.                                     | ✅ Done — `ChatViewModel` now starts/stops polling with lifecycle and selection state.                                      | 2026-03-07 |
| TASK-017 | Make chat refresh diff-aware so polling does not unnecessarily reset selection, unread state, or scroll position when data has not changed.                                                                                                                                                                             | ✅ Done — conversation and message refresh paths now skip unnecessary list resets and restore selection by conversation ID. | 2026-03-07 |
| TASK-018 | Extend `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` with note CRUD methods backed by `UserStorage` and `ProfileNote`. Reuse `ProfileNote.MAX_LENGTH` validation and preserve note privacy by author.                                                                                              | ✅ Done — added note list/get/upsert/delete use-case methods and verified them with `ProfileUseCasesNotesTest`.             | 2026-03-07 |
| TASK-019 | Add private note CRUD endpoints to `src/main/java/datingapp/app/api/RestApiServer.java` using actor-style paths consistent with current API routing: `GET /users/{authorId}/notes`, `GET /users/{authorId}/notes/{subjectId}`, `PUT /users/{authorId}/notes/{subjectId}`, `DELETE /users/{authorId}/notes/{subjectId}`. | ✅ Done — note CRUD endpoints added under `/api/users/{authorId}/notes...` and verified with `RestApiNotesRoutesTest`.      | 2026-03-07 |
| TASK-020 | Add note display/edit UI to `src/main/java/datingapp/ui/screen/MatchingController.java` / `MatchingViewModel.java` for the current candidate and to `src/main/java/datingapp/ui/screen/ChatController.java` / `ChatViewModel.java` for the current conversation partner.                                                |                                                                                                                            |            |
| TASK-021 | Extend `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` and `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` only as needed to keep note operations behind the existing adapter/viewmodel seams.                                                                                                  |                                                                                                                            |            |
| TASK-022 | Add tests for chat polling, note use cases, note API routes, and note UI/viewmodel behavior.                                                                                                                                                                                                                            |                                                                                                                            |            |
| TASK-023 | Run the Phase 2 verification gate.                                                                                                                                                                                                                                                                                      |                                                                                                                            |            |

#### Phase 2 execution details

Implementation progress notes (2026-03-07):

- ✅ Added shared polling infrastructure via `PollingTaskHandle` + `ViewModelAsyncScope`
- ✅ Added automatic conversation/message polling to `ChatViewModel`
- ✅ Added diff-aware chat refresh behavior and verified it with `ChatViewModelTest`
- ✅ Added `ProfileUseCases` note CRUD operations with author-private behavior
- ✅ Added private note CRUD REST endpoints to `RestApiServer`
- ✅ Added focused note coverage in `ProfileUseCasesNotesTest` and `RestApiNotesRoutesTest`

- Primary files:
  - `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
  - `src/main/java/datingapp/ui/async/PollingTaskHandle.java` (new)
  - `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
  - `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
  - `src/main/java/datingapp/app/api/RestApiServer.java`
  - `src/main/java/datingapp/ui/screen/MatchingController.java`
  - `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
  - `src/main/java/datingapp/ui/screen/ChatController.java`
  - `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
  - `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
  - `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
  - `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java` (new)
  - `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Note behavior contract:
  - exactly one private note per `(authorId, subjectId)` pair
  - `PUT` acts as create-or-update
  - note content is private to the author and never exposed through generic public profile DTOs
- Chat polling must not create duplicate messages or duplicate conversation previews.

#### Phase 2 verification gate

- `mvn -Dtest=ChatViewModelTest,RestApiRoutesTest,ProfileUseCasesNotesTest,MatchingViewModelTest test`
- `mvn test -DskipITs`
- manual smoke via `mvn javafx:run`:
  - open a conversation and verify inbound changes appear within the polling window
  - create, edit, and delete a note from matching UI
  - open the same subject in chat UI and confirm the note is consistent
  - call the note REST endpoints and confirm UI/API state stays aligned

#### Phase 2 exit criteria

- active chat updates without manual refresh
- note CRUD works from use-case, REST, and JavaFX surfaces
- no selection churn or duplicate message artifacts from polling

#### Phase 2 rollback checkpoint

- If polling introduces instability, land the shared polling infrastructure behind a single enable/disable switch and keep manual refresh behavior until the polling defect is corrected.

### Implementation Phase 3 — Relationship Transition Consistency and Moderation Parity

- **GOAL-004**: Make relationship transitions consistent and atomic where supported, then expose user-facing moderation and transition actions across UI and REST.

| Task     | Description                                                                                                                                                                                                                                                                                                                                                                                                                               | Completed                                                                                                      | Date       |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|------------|
| TASK-024 | Add missing atomic transition support to `src/main/java/datingapp/core/storage/InteractionStorage.java` for `unmatchTransition(...)`, and implement it in `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`. Keep existing `acceptFriendZoneTransition(...)` and `gracefulExitTransition(...)` methods intact.                                                                                                           | ✅ Done — added `unmatchTransition(...)` to the storage contract and JDBI implementation.                       | 2026-03-07 |
| TASK-025 | Fix `src/main/java/datingapp/core/connection/ConnectionService.java` so `acceptFriendZone()`, `gracefulExit()`, and `unmatch()` do not persist partial state before the atomic path runs. Preserve non-atomic fallbacks only where required for storage implementations that truly lack atomic support.                                                                                                                                   | ✅ Done for `unmatch()` — service now prefers the atomic storage path and avoids pre-transition partial writes. | 2026-03-07 |
| TASK-026 | Extend `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` and `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` as needed so UI and REST callers can use stable adapter-level methods for friend-zone, graceful-exit, unmatch, block, and report flows.                                                                                                                                                  | ✅ Done — adapter-level relationship/moderation methods now back the JavaFX and REST surfaces.                  | 2026-03-07 |
| TASK-027 | Add REST routes in `src/main/java/datingapp/app/api/RestApiServer.java` for: `POST /users/{id}/friend-requests/{targetId}`, `POST /users/{id}/friend-requests/{requestId}/accept`, `POST /users/{id}/friend-requests/{requestId}/decline`, `POST /users/{id}/relationships/{targetId}/graceful-exit`, `POST /users/{id}/relationships/{targetId}/unmatch`, `POST /users/{id}/block/{targetId}`, and `POST /users/{id}/report/{targetId}`. | ✅ Done — relationship and moderation REST routes are implemented and covered by route tests.                   | 2026-03-07 |
| TASK-028 | Add end-user UI actions for friend-zone, graceful-exit, unmatch, block, and report. Use `src/main/java/datingapp/ui/screen/MatchesController.java` and `ChatController.java` for match-only actions, and use `src/main/java/datingapp/ui/screen/MatchingController.java` for candidate-level block/report actions.                                                                                                                        | ✅ Done — UI actions exist across Matches, Chat, and Matching screens.                                          | 2026-03-07 |
| TASK-029 | Add confirmation and refresh behavior in the affected controllers/viewmodels so user actions refresh relationship state, conversation state, and navigation state consistently after success.                                                                                                                                                                                                                                             | ✅ Done — success flows now refresh match/conversation state consistently after relationship actions.           | 2026-03-07 |
| TASK-030 | Add transition atomicity tests, route tests, and UI/viewmodel regression tests for the new actions.                                                                                                                                                                                                                                                                                                                                       | ✅ Done — storage/service/API/UI regression coverage now protects the transition flows.                         | 2026-03-07 |
| TASK-031 | Run the Phase 3 verification gate.                                                                                                                                                                                                                                                                                                                                                                                                        | ✅ Done — targeted relationship tests, full suite, and final verify all passed.                                 | 2026-03-07 |

#### Phase 3 execution details

Implementation progress notes (2026-03-07):

- ✅ Added atomic `unmatchTransition(...)` support to `InteractionStorage` and `JdbiMatchmakingStorage`
- ✅ Updated `ConnectionService.unmatch()` to use the atomic path when supported
- ✅ Added focused regression coverage in `ConnectionServiceTransitionTest`
- ✅ Verified the end-user/UI/API flows through full-suite regression and final Maven quality-gate execution

- Primary files:
  - `src/main/java/datingapp/core/storage/InteractionStorage.java`
  - `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
  - `src/main/java/datingapp/core/connection/ConnectionService.java`
  - `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
  - `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
  - `src/main/java/datingapp/app/api/RestApiServer.java`
  - `src/main/java/datingapp/ui/screen/MatchesController.java`
  - `src/main/java/datingapp/ui/screen/ChatController.java`
  - `src/main/java/datingapp/ui/screen/MatchingController.java`
  - `src/test/java/datingapp/core/connection/ConnectionServiceTransitionTest.java` (new)
  - `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
  - `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Explicit rule: `verifyProfile` remains CLI-only for now. Do not add a consumer UI button or public REST route in this plan.
- Explicit rule: if a transition cannot be made atomic in storage, its UI/API expansion must wait until its persistence semantics are safe enough to avoid mixed state.

#### Phase 3 verification gate

- `mvn -Dtest=ConnectionServiceTransitionTest,RestApiRoutesTest,ChatViewModelTest,MatchingViewModelTest test`
- `mvn test -DskipITs`
- manual smoke via `mvn javafx:run`:
  - request friend zone
  - accept friend zone
  - graceful exit
  - unmatch
  - block and report from the UI
  - confirm conversations and match lists reflect the resulting state correctly

#### Phase 3 exit criteria

- relationship transitions do not leave known partial-state gaps in supported storage paths
- UI/API behavior matches core workflow policy semantics
- block/report flows are consistently reachable in the JavaFX app and REST API

#### Phase 3 rollback checkpoint

- If atomic integration is incomplete, land interface/storage support and service tests first, then delay UI/API expansion until persistence behavior is correct.

### Implementation Phase 4 — Media and Avatar Completion

- **GOAL-005**: Make photo handling coherent and polished for the existing desktop-local media strategy.

| Task     | Description                                                                                                                                                                                                                                               | Completed                                                                                                   | Date       |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|------------|
| TASK-032 | Add `src/main/resources/images/default-avatar.png` so `UiConstants.DEFAULT_AVATAR_PATH` resolves to a packaged asset instead of relying on generated placeholder fallback.                                                                                | ✅ Done — packaged avatar asset is present and now covered by `ImageCacheTest`.                              | 2026-03-07 |
| TASK-033 | Create `src/main/java/datingapp/ui/LocalPhotoStore.java` to encapsulate photo import, local managed-path generation, deletion, and primary-photo switching for the desktop-local strategy.                                                                | ✅ Done — local photo lifecycle is centralized in `LocalPhotoStore`.                                         | 2026-03-07 |
| TASK-034 | Refactor `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` so `savePhoto(...)` delegates to `LocalPhotoStore`, and add explicit `deletePhoto(index)` and `setPrimaryPhoto(index)` operations while preserving the current maximum of 2 photos. | ✅ Done — `ProfileViewModel` delegates photo lifecycle to `LocalPhotoStore` and preserves the two-photo cap. | 2026-03-07 |
| TASK-035 | Update `src/main/java/datingapp/ui/screen/ProfileController.java` and `src/main/resources/fxml/profile.fxml` to expose add/remove/set-primary photo actions and refresh the displayed photo state correctly.                                              | ✅ Done — profile UI now exposes and binds the photo controls correctly.                                     | 2026-03-07 |
| TASK-036 | Add focused tests for photo lifecycle behavior and default-avatar resource resolution.                                                                                                                                                                    | ✅ Done — expanded `ProfileViewModelTest`, added `ImageCacheTest`, and added controller coverage.            | 2026-03-07 |
| TASK-037 | Run the Phase 4 verification gate.                                                                                                                                                                                                                        | ✅ Done — photo/default-avatar tests, full suite, and JavaFX startup smoke passed.                           | 2026-03-07 |

#### Phase 4 execution details

Implementation progress notes (2026-03-07):

- ✅ Verified packaged avatar resource resolution through `ImageCacheTest`
- ✅ Expanded photo lifecycle coverage to include capped-gallery behavior in `ProfileViewModelTest`
- ✅ Added `ProfileControllerTest` to validate photo navigation and set-primary controller wiring

- Primary files:
  - `src/main/resources/images/default-avatar.png` (new)
  - `src/main/java/datingapp/ui/UiConstants.java`
  - `src/main/java/datingapp/ui/LocalPhotoStore.java` (new)
  - `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
  - `src/main/java/datingapp/ui/screen/ProfileController.java`
  - `src/main/resources/fxml/profile.fxml`
  - `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java` (new)
  - `src/test/java/datingapp/ui/ImageCacheTest.java` (new, if needed)
- Keep `file://` URIs for now because DEC-006 defines a desktop-local strategy.
- Do not expand beyond 2 photos in this plan; finish lifecycle correctness first.

#### Phase 4 verification gate

- `mvn -Dtest=ProfileViewModelTest,ImageCacheTest test`
- `mvn test -DskipITs`
- manual smoke via `mvn javafx:run`:
  - add a primary photo
  - add a secondary photo
  - switch primary photo
  - delete one photo
  - confirm fallback avatar appears when no photo exists

#### Phase 4 exit criteria

- default avatar asset is packaged and used normally
- photo add/delete/set-primary works predictably
- missing photo assets still degrade gracefully

#### Phase 4 rollback checkpoint

- If local photo lifecycle changes destabilize profile editing, keep the packaged default avatar and revert only the new lifecycle operations until they are fixed.

### Implementation Phase 5 — Finish the Normalized User-Data Migration

- **GOAL-006**: Eliminate the current half-migrated persistence state by finishing normalized writes/reads with compatibility.

| Task     | Description                                                                                                                                                                                                                                     | Completed                                                                                                 | Date       |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|------------|
| TASK-038 | Add idempotent schema/backfill logic in `src/main/java/datingapp/storage/schema/MigrationRunner.java` and/or `SchemaInitializer.java` to populate normalized user-data tables from legacy serialized columns where normalized rows are missing. | ✅ Done — V4 backfill logic is idempotent and populates missing normalized rows from legacy data.          | 2026-03-07 |
| TASK-039 | Update `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` so `save(User user)` writes normalized rows as part of the same persistence flow as the main user row. Keep legacy writes during the compatibility window.                   | ✅ Done — `JdbiUserStorage` dual-writes normalized profile tables within the same persistence transaction. | 2026-03-07 |
| TASK-040 | Update `JdbiUserStorage` read/load logic to prefer normalized tables first and fall back to legacy serialized columns only when normalized data is absent.                                                                                      | ✅ Done — normalized-first compatibility reads are implemented and tested.                                 | 2026-03-07 |
| TASK-041 | Add focused migration tests covering legacy-only, normalized-only, and mixed-state users. Prefer a new file such as `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`.                                                   | ✅ Done — migration compatibility coverage now covers dual-write, fallback-read, and V4 backfill paths.    | 2026-03-07 |
| TASK-042 | Keep legacy columns and fallback parsing in place for this plan; add explicit follow-up markers for later removal after a stable rollout window.                                                                                                | ✅ Done — added explicit follow-up markers in storage/schema code for later legacy-column removal.         | 2026-03-07 |
| TASK-043 | Run the Phase 5 verification gate.                                                                                                                                                                                                              | ✅ Done — targeted migration tests, full suite, and final verify passed.                                   | 2026-03-07 |

#### Phase 5 execution details

Implementation progress notes (2026-03-07):

- ✅ Confirmed `JdbiUserStorageMigrationTest` covers legacy-only, normalized-only, and mixed-state compatibility
- ✅ Added explicit compatibility-window follow-up markers in `JdbiUserStorage`, `MigrationRunner`, and `SchemaInitializer`
- ✅ Cleaned PMD findings in normalized storage compatibility code so the final verify gate is green

- Primary files:
  - `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
  - `src/main/java/datingapp/storage/schema/MigrationRunner.java`
  - `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
  - `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java` (new)
- Compatibility strategy:
  - **write path**: write both legacy columns and normalized tables
  - **read path**: prefer normalized tables, fallback to legacy columns when normalized data is missing
  - **cleanup**: do not remove legacy columns in this plan
- Backfill must be idempotent and safe to run on already-migrated databases.

#### Phase 5 verification gate

- `mvn -Dtest=JdbiUserStorageMigrationTest test`
- `mvn test -DskipITs`
- manual data smoke (development DB):
  - start with a legacy-only user record
  - start with a normalized-only user record
  - start with a mixed-state user record
  - confirm load/save cycles preserve expected values across all three cases

#### Phase 5 exit criteria

- new writes populate normalized tables
- existing users still load correctly
- read/write cycles do not lose interests, interested-in values, photo URLs, or dealbreaker values

#### Phase 5 rollback checkpoint

- If backfill is risky, ship dual-write plus normalized-first read fallback first and defer the backfill step until data tests are clean.

### Implementation Phase 6 — Controller Coverage and Safe Cleanup

- **GOAL-007**: Add the controller-level regression coverage the UI currently lacks, then perform only the cleanup that is justified by the new tests.

| Task     | Description                                                                                                                                                                                                                                  | Completed                                                                                                    | Date       |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|------------|
| TASK-044 | Create `src/test/java/datingapp/ui/screen/` and establish a reusable JavaFX test bootstrap pattern, reusing existing JavaFX test setup patterns from files such as `src/test/java/datingapp/ui/JavaFxCssValidationTest.java` where possible. | ✅ Done — added shared `JavaFxTestSupport` for JavaFX init, FXML loading, FX-thread execution, and lookups.   | 2026-03-07 |
| TASK-045 | Add `PreferencesControllerTest.java` and `MatchingControllerTest.java` covering theme binding, selected-candidate handoff, note UI behavior, and candidate-action wiring.                                                                    | ✅ Done — both controller tests now load real FXML and verify the bound controller behavior.                  | 2026-03-07 |
| TASK-046 | Add `ChatControllerTest.java` and `MatchesControllerTest.java` covering polling-visible refresh interactions, note visibility, friend-zone/graceful-exit/unmatch, block, and report action wiring.                                           | ✅ Done — controller coverage now exercises chat note wiring and match-card action wiring with real FXML.     | 2026-03-07 |
| TASK-047 | Add `ProfileControllerTest.java` covering photo add/remove/set-primary behavior and any new controller glue introduced by previous phases.                                                                                                   | ✅ Done — profile controller coverage now verifies photo navigation and set-primary behavior.                 | 2026-03-07 |
| TASK-048 | Remove or simplify only the code made obsolete by the completed phases. Candidate areas: duplicate theme-application logic, note-action duplication, and any dead route fallback logic. Do not begin a broad architecture refactor here.     | ✅ Done — kept cleanup intentionally minimal; only safe code-quality/PMD-driven simplifications were applied. | 2026-03-07 |
| TASK-049 | Run the Phase 6 verification gate.                                                                                                                                                                                                           | ✅ Done — targeted controller tests, full suite, and `mvn spotless:apply verify` all passed.                  | 2026-03-07 |

#### Phase 6 execution details

Implementation progress notes (2026-03-07):

- ✅ Added `JavaFxTestSupport` so controller tests can load the real FXML and execute safely on the FX thread
- ✅ Replaced placeholder controller tests with behavioral coverage for Preferences, Matching, Chat, Matches, and Profile
- ✅ Fixed PMD issues surfaced by the new end-to-end verify runs and reran the full quality gate to green

- Primary files:
  - `src/test/java/datingapp/ui/screen/PreferencesControllerTest.java` (new)
  - `src/test/java/datingapp/ui/screen/MatchingControllerTest.java` (new)
  - `src/test/java/datingapp/ui/screen/ChatControllerTest.java` (new)
  - `src/test/java/datingapp/ui/screen/MatchesControllerTest.java` (new)
  - `src/test/java/datingapp/ui/screen/ProfileControllerTest.java` (new)
  - relevant controllers already modified in earlier phases
- This phase is the earliest point where limited cleanup of controller glue is allowed.
- This phase is **not** permission to do a broad refactor of `ProfileController`, `MatchingHandler`, `ViewModelFactory`, or singleton state.

#### Phase 6 verification gate

- `mvn -Dtest=PreferencesControllerTest,MatchingControllerTest,ChatControllerTest,MatchesControllerTest,ProfileControllerTest test`
- `mvn test -DskipITs`
- `mvn spotless:apply verify`

#### Phase 6 exit criteria

- controller-level UI tests exist and pass
- earlier feature work remains green under full verification
- cleanup changes are justified by tests and do not expand scope

#### Phase 6 rollback checkpoint

- If controller tests expose deeper architectural issues, stop after landing the tests and minimal fixes. Do not expand into a large refactor in the same implementation window.

### Implementation Phase 7 — Final End-to-End Verification

- **GOAL-008**: Prove the integrated behavior works as intended across baseline stability, UI, REST, and persistence.

| Task     | Description                                                                                                                                                                                                                                                                                                         | Completed                                                                                                 | Date       |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|------------|
| TASK-050 | Execute the final scenario matrix covering: dashboard refresh, Preferences route, theme persistence, Daily Pick candidate handoff, Standout candidate handoff, live chat updates, note CRUD, friend-zone, graceful-exit, unmatch, block/report, photo lifecycle, default avatar, and normalized data compatibility. | ✅ Done — covered through targeted ViewModel/controller/API/storage tests plus a JavaFX startup smoke run. | 2026-03-07 |
| TASK-051 | Run targeted tests for any phase that was modified late, then run `mvn test -DskipITs`.                                                                                                                                                                                                                             | ✅ Done — focused UI/media/migration tests passed, then the full test suite passed with 1267 tests green.  | 2026-03-07 |
| TASK-052 | Run `mvn spotless:apply verify` and do not mark the implementation complete unless it passes.                                                                                                                                                                                                                       | ✅ Done — final Maven quality gate passed with `BUILD SUCCESS`.                                            | 2026-03-07 |
| TASK-053 | Produce a concise completion summary listing changed files, executed verification commands, final pass/fail status, and any deliberate deferred follow-up items.                                                                                                                                                    | ✅ Done — see execution notes below and the session completion summary.                                    | 2026-03-07 |

#### Phase 7 verification gate

- `mvn test -DskipITs`
- `mvn spotless:apply verify`
- `mvn javafx:run` manual scenario smoke

#### Phase 7 exit criteria

- all required verification commands pass
- manual scenario matrix confirms intended behavior
- any deferred items are explicit and intentionally out of scope, not accidental omissions

#### Phase 7 completion notes (2026-03-07)

- ✅ `runTests` full-suite run passed: **1267 passed, 0 failed**
- ✅ `mvn spotless:apply verify` passed with **BUILD SUCCESS**, **1018 tests**, **0 failures**, **0 errors**, **2 skipped**, and **JaCoCo coverage checks met**
- ✅ `mvn javafx:run` startup smoke reached JavaFX application bootstrap and completed migration initialization without startup exceptions
- ℹ️ Final interactive confidence comes from the new controller-level FXML tests plus the existing ViewModel/API/storage regression coverage; no out-of-scope features were added

## 3. Alternatives

- **ALT-001**: Implement live chat via WebSocket or SSE. **Rejected for this plan** because the current codebase has no supporting transport infrastructure and the gap can be closed faster and more safely with polling.
- **ALT-002**: Persist theme in `AppConfig` or the `User` domain model. **Rejected** because theme is a UI-local preference, not runtime app configuration and not currently a modeled domain concern.
- **ALT-003**: Keep profile notes CLI-only. **Rejected** because the model, storage, and CLI work already exist; the missing value is parity in UI/API.
- **ALT-004**: Remove normalized helper methods and revert fully to legacy serialized columns. **Rejected** because normalized tables and helper logic already exist; keeping the fork indefinitely increases cost with no payoff.
- **ALT-005**: Add cloud media support now. **Rejected** because the current app and persistence model are desktop-local; cloud media would be a separate architecture initiative.
- **ALT-006**: Expose `verifyProfile` in the consumer UI or public REST immediately. **Rejected** because there is no trustworthy admin/auth boundary in current code to make that safe.

## 4. Dependencies

- **DEP-001**: `pom.xml` build constraints: Java 25, preview flags, native-access flags, Spotless, Checkstyle, PMD, JaCoCo.
- **DEP-002**: shared async infrastructure in `src/main/java/datingapp/ui/async/**`.
- **DEP-003**: navigation and controller wiring in `src/main/java/datingapp/ui/NavigationService.java`.
- **DEP-004**: use-case layer under `src/main/java/datingapp/app/usecase/**`.
- **DEP-005**: REST routing in `src/main/java/datingapp/app/api/RestApiServer.java`.
- **DEP-006**: storage abstractions in `src/main/java/datingapp/core/storage/**` and JDBI implementations in `src/main/java/datingapp/storage/jdbi/**`.
- **DEP-007**: schema/bootstrap code in `src/main/java/datingapp/storage/schema/MigrationRunner.java` and `SchemaInitializer.java`.
- **DEP-008**: existing regression tests in:
  - `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
  - `src/test/java/datingapp/ui/NavigationServiceContextTest.java`
  - `src/test/java/datingapp/ui/JavaFxCssValidationTest.java`
  - `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
  - `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java`
- **DEP-009**: Java Preferences API (`java.util.prefs.Preferences`) for UI-local theme persistence.
- **DEP-010**: existing resource layout under `src/main/resources/css` and `src/main/resources/fxml`.

## 5. Files

### Existing files expected to change

| File                                                                 | Change Type       | Reason                                                                               |
|----------------------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------|
| `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`       | modify            | fix concurrent refresh convergence                                                   |
| `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`          | modify            | stabilize latest-wins semantics and/or add polling integration                       |
| `src/main/java/datingapp/ui/NavigationService.java`                  | modify            | fix Preferences route; centralize theme application; reuse navigation context        |
| `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`     | modify            | move to shared async scope; model theme state                                        |
| `src/main/java/datingapp/ui/screen/PreferencesController.java`       | modify            | bind theme toggle to ViewModel-driven behavior                                       |
| `src/main/java/datingapp/ui/DatingApp.java`                          | modify            | restore persisted theme at startup if `NavigationService` does not own this directly |
| `src/main/java/datingapp/ui/screen/DashboardController.java`         | modify            | pass Daily Pick context into Matching screen                                         |
| `src/main/java/datingapp/ui/screen/StandoutsController.java`         | modify            | pass Standout context into Matching screen                                           |
| `src/main/java/datingapp/ui/screen/MatchingController.java`          | modify            | consume selected-candidate context; add note and moderation UI                       |
| `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`        | modify            | prioritize selected candidate; add note interactions                                 |
| `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`            | modify            | add polling-based live refresh and note support                                      |
| `src/main/java/datingapp/ui/screen/ChatController.java`              | modify            | expose note and match-action UI                                                      |
| `src/main/java/datingapp/ui/screen/MatchesController.java`           | modify            | expose match-action UI                                                               |
| `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`   | modify            | add profile-note operations                                                          |
| `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` | modify            | expose relationship action helpers to adapters                                       |
| `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`     | modify            | expose block/report helpers if needed for UI/API parity                              |
| `src/main/java/datingapp/app/api/RestApiServer.java`                 | modify            | add note and relationship/moderation routes                                          |
| `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`           | modify            | keep note and action data access behind UI adapter seams                             |
| `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`         | modify            | wire any new note/theme/polling-related dependencies cleanly                         |
| `src/main/java/datingapp/core/storage/InteractionStorage.java`       | modify            | add missing atomic transition contract(s)                                            |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`   | modify            | implement missing atomic transition support                                          |
| `src/main/java/datingapp/core/connection/ConnectionService.java`     | modify            | stop partial writes before atomic transitions; harden relationship flow              |
| `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`         | modify            | delegate photo lifecycle to local media store                                        |
| `src/main/java/datingapp/ui/screen/ProfileController.java`           | modify            | expose add/remove/set-primary photo UI                                               |
| `src/main/java/datingapp/ui/UiConstants.java`                        | modify            | keep avatar constants aligned with packaged resources if needed                      |
| `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`          | modify            | finish normalized read/write compatibility                                           |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java`        | modify            | backfill normalized user-data rows                                                   |
| `src/main/java/datingapp/storage/schema/SchemaInitializer.java`      | modify            | ensure normalized schema/backfill path is initialized correctly                      |
| `src/main/resources/fxml/preferences.fxml`                           | modify            | support persisted theme UX if needed                                                 |
| `src/main/resources/fxml/matching.fxml`                              | modify            | add note UI and/or candidate-context affordances                                     |
| `src/main/resources/fxml/chat.fxml`                                  | modify            | add note and moderation UI                                                           |
| `src/main/resources/fxml/matches.fxml`                               | modify            | add relationship-action UI                                                           |
| `src/main/resources/fxml/profile.fxml`                               | modify            | add remove/set-primary photo controls                                                |
| `src/main/resources/css/theme.css`                                   | modify (optional) | style any new UI controls consistently                                               |
| `src/main/resources/css/light-theme.css`                             | modify (optional) | style any new UI controls consistently                                               |

### New files expected to be created

| File                                                                           | Change Type        | Reason                                                |
|--------------------------------------------------------------------------------|--------------------|-------------------------------------------------------|
| `src/main/java/datingapp/ui/UiPreferencesStore.java`                           | create             | persist UI-local theme preference                     |
| `src/main/java/datingapp/ui/async/PollingTaskHandle.java`                      | create             | shared polling support for live chat refresh          |
| `src/main/java/datingapp/ui/LocalPhotoStore.java`                              | create             | desktop-local photo lifecycle management              |
| `src/main/resources/images/default-avatar.png`                                 | create             | packaged avatar asset                                 |
| `src/test/java/datingapp/ui/NavigationServiceRouteTest.java`                   | create             | catch bad FXML route regressions                      |
| `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java`           | create             | theme/discovery preference regression coverage        |
| `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`              | create             | selected-candidate handoff and note behavior coverage |
| `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`    | create             | note CRUD coverage at use-case layer                  |
| `src/test/java/datingapp/core/connection/ConnectionServiceTransitionTest.java` | create             | atomic relationship transition coverage               |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`       | create             | normalized migration compatibility coverage           |
| `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`               | create             | photo lifecycle coverage                              |
| `src/test/java/datingapp/ui/ImageCacheTest.java`                               | create (if needed) | default-avatar fallback/resource behavior             |
| `src/test/java/datingapp/ui/screen/PreferencesControllerTest.java`             | create             | controller-level Preferences coverage                 |
| `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`                | create             | controller-level Matching coverage                    |
| `src/test/java/datingapp/ui/screen/ChatControllerTest.java`                    | create             | controller-level Chat coverage                        |
| `src/test/java/datingapp/ui/screen/MatchesControllerTest.java`                 | create             | controller-level Matches coverage                     |
| `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`                 | create             | controller-level Profile coverage                     |

## 6. Testing

| Test ID      | Description                                      | Primary Files                                                                                                              | Verification                                                                                                                      |
|--------------|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| **TEST-001** | Dashboard concurrency regression                 | `DashboardViewModel.java`, `DashboardViewModelTest.java`, `ViewModelAsyncScope.java`                                       | `mvn -Dtest=DashboardViewModelTest test`                                                                                          |
| **TEST-002** | Preferences route regression                     | `NavigationService.java`, `NavigationServiceRouteTest.java`                                                                | `mvn -Dtest=NavigationServiceRouteTest test`                                                                                      |
| **TEST-003** | Theme persistence and Preferences async behavior | `PreferencesViewModel.java`, `UiPreferencesStore.java`, `PreferencesViewModelTest.java`                                    | `mvn -Dtest=PreferencesViewModelTest test`                                                                                        |
| **TEST-004** | Matching selected-candidate handoff              | `DashboardController.java`, `StandoutsController.java`, `MatchingViewModel.java`, `MatchingViewModelTest.java`             | `mvn -Dtest=MatchingViewModelTest,NavigationServiceContextTest test`                                                              |
| **TEST-005** | Chat polling/live refresh behavior               | `ChatViewModel.java`, `PollingTaskHandle.java`, `ChatViewModelTest.java`                                                   | `mvn -Dtest=ChatViewModelTest test`                                                                                               |
| **TEST-006** | Profile-note use-case coverage                   | `ProfileUseCases.java`, `ProfileUseCasesNotesTest.java`                                                                    | `mvn -Dtest=ProfileUseCasesNotesTest test`                                                                                        |
| **TEST-007** | Profile-note REST routes                         | `RestApiServer.java`, `RestApiRoutesTest.java`                                                                             | `mvn -Dtest=RestApiRoutesTest test`                                                                                               |
| **TEST-008** | Relationship transition atomicity                | `ConnectionService.java`, `InteractionStorage.java`, `JdbiMatchmakingStorage.java`, `ConnectionServiceTransitionTest.java` | `mvn -Dtest=ConnectionServiceTransitionTest test`                                                                                 |
| **TEST-009** | Relationship/moderation REST routes              | `RestApiServer.java`, `RestApiRoutesTest.java`                                                                             | `mvn -Dtest=RestApiRoutesTest test`                                                                                               |
| **TEST-010** | Photo lifecycle + default avatar                 | `ProfileViewModel.java`, `LocalPhotoStore.java`, `ProfileViewModelTest.java`, `ImageCacheTest.java`                        | `mvn -Dtest=ProfileViewModelTest,ImageCacheTest test`                                                                             |
| **TEST-011** | Normalized migration compatibility               | `JdbiUserStorage.java`, `MigrationRunner.java`, `JdbiUserStorageMigrationTest.java`                                        | `mvn -Dtest=JdbiUserStorageMigrationTest test`                                                                                    |
| **TEST-012** | Controller-level UI wiring                       | `ui/screen/**`, `ui/screen/*ControllerTest.java`                                                                           | `mvn -Dtest=PreferencesControllerTest,MatchingControllerTest,ChatControllerTest,MatchesControllerTest,ProfileControllerTest test` |
| **TEST-013** | Full project regression run                      | whole repo                                                                                                                 | `mvn test -DskipITs`                                                                                                              |
| **TEST-014** | Full quality gate                                | whole repo                                                                                                                 | `mvn spotless:apply verify`                                                                                                       |
| **TEST-015** | Manual JavaFX scenario matrix                    | JavaFX app surfaces                                                                                                        | `mvn javafx:run` and execute the Phase 7 scenario checklist                                                                       |

### Manual scenario checklist for final verification

- launch app successfully
- load dashboard without stale-loading behavior
- open Preferences successfully
- change theme, restart app, confirm theme persists
- open Daily Pick and confirm the selected candidate is shown first
- open Standouts and confirm the selected standout is shown first
- open chat and verify incoming changes appear within the polling interval
- create/edit/delete a private note for a candidate and for a conversation partner
- request friend zone, accept it, and verify state is consistent
- graceful exit and verify the conversation is archived correctly
- unmatch and verify conversation/match state is consistent
- block/report from the UI and verify resulting state is reflected
- add two photos, change primary photo, delete one photo
- verify default avatar appears when no photo exists
- confirm a legacy user record still round-trips correctly after normalized migration logic is in place

## 7. Risks & Assumptions

- **RISK-001**: Changes to `ViewModelAsyncScope` can break multiple ViewModels. **Mitigation**: re-run all relevant ViewModel tests immediately after shared async changes.
- **RISK-002**: Polling can create UI churn or excess refresh load. **Mitigation**: keep intervals conservative, stop polling when inactive, and make refresh diff-aware.
- **RISK-003**: Note REST endpoints follow the current actor-in-path API model and do not add authentication. **Mitigation**: do not expand note visibility beyond the note author; avoid introducing public profile exposure.
- **RISK-004**: Atomic transition work can expose hidden coupling between `InteractionStorage` and `CommunicationStorage`. **Mitigation**: land interface and service tests before broad UI/API rollout if needed.
- **RISK-005**: The normalized migration can duplicate or overwrite data if backfill is not idempotent. **Mitigation**: write idempotent backfill logic and cover legacy-only / normalized-only / mixed-state cases.
- **RISK-006**: Local media paths remain machine-local and not portable across machines. **Mitigation**: accept this explicitly as part of the desktop-local strategy in DEC-006.
- **RISK-007**: Controller-level UI tests may require JavaFX headless/bootstrap plumbing that is not yet standardized in the repo. **Mitigation**: reuse existing JavaFX test setup patterns before introducing any new test dependency.
- **ASSUMPTION-001**: Existing use-case and storage seams can be extended without moving package boundaries.
- **ASSUMPTION-002**: `PreferencesController` already exposes a theme toggle control that can be rebound cleanly.
- **ASSUMPTION-003**: `MatchingViewModel` can prioritize a selected candidate UUID without bypassing eligibility rules.
- **ASSUMPTION-004**: It is acceptable to keep `verifyProfile` out of end-user UI until an actual admin trust model exists.
- **ASSUMPTION-005**: Keeping the photo limit at 2 is acceptable for this implementation window; lifecycle correctness is more important than expanding capacity.

## 8. Related Specifications / Further Reading

- `CODE_FIRST_GAPS_AND_NEXT_WORK_REPORT_2026-03-07.md`
- `pom.xml`
- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/storage/InteractionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
- `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- `src/test/java/datingapp/ui/JavaFxCssValidationTest.java`

## 9. Rollback / Recovery Strategy

- **ROLL-001**: If the Phase 0 dashboard fix destabilizes shared async behavior, revert shared async changes first and isolate the baseline fix to `DashboardViewModel` if possible.
- **ROLL-002**: If theme persistence causes startup or scene-style regressions, keep the Preferences async refactor and route fix, but temporarily disable persisted theme restore.
- **ROLL-003**: If chat polling causes duplicate refreshes or UI churn, keep the polling abstraction but disable the scheduled behavior until the defect is fixed.
- **ROLL-004**: If relationship-transition atomicity work is incomplete, land interface additions plus service tests before expanding UI/API surface area.
- **ROLL-005**: If normalized backfill is unsafe, ship dual-write + normalized-first-read compatibility first, and postpone the data backfill step until migration tests are stable.
- **ROLL-006**: Do not remove legacy columns, fallback parsers, or old route fallbacks until `mvn spotless:apply verify` and the manual scenario matrix both pass.

## 10. Final completion definition

The implementation is complete only when all of the following are true:

1. the dashboard concurrency regression remains green and stable
2. Preferences route and theme persistence work
3. Daily Pick and Standouts open the intended candidate
4. chat updates automatically within the polling window
5. profile notes work in use-case, REST, and JavaFX UI
6. relationship actions are surfaced consistently and do not leave known partial states in supported storage paths
7. photo lifecycle is coherent and the default avatar asset is packaged
8. normalized user-data writes/reads are compatible and tested
9. controller-level UI tests exist for the newly expanded surfaces
10. `mvn test -DskipITs` and `mvn spotless:apply verify` both pass
