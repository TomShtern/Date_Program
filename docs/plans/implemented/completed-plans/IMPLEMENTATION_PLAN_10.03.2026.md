---
goal: Deterministic implementation plan for verified remaining work in Date_Program
version: 2026-03-10.2
date_created: 2026-03-10
last_updated: 2026-03-11
owner: GitHub Copilot
status: Completed
tags: [implementation-plan, java, javafx, mvvm, backend, testing, security, refactor, ai-agent]
---

# Introduction

![Status: Completed](https://img.shields.io/badge/status-Completed-brightgreen)

This is a comprehensive, execution-ready plan optimized for autonomous implementation by an AI coding agent. It contains only code-verified remaining work, with atomic tasks, explicit dependencies, deterministic completion criteria, and concrete file/symbol targets.

## 1. Requirements & Constraints

- **REQ-001**: Use `src/main/**` and `src/test/**` as the only source of truth.
- **REQ-002**: Do not re-implement already-complete features.
- **REQ-003**: Keep all changes within existing architecture boundaries unless a task explicitly extends a boundary.
- **REQ-004**: Every task must be independently verifiable.
- **REQ-005**: Every task must define exact target files and symbols.
- **REQ-006**: Preserve existing behavior unless task description explicitly changes it.
- **CON-001**: Keep UI async behavior on `ui/async` abstractions.
- **CON-002**: Keep navigation handoff through `NavigationService`.
- **CON-003**: Keep adapter/use-case boundaries in ViewModels (no ad-hoc direct storage access).
- **CON-004**: Keep deterministic, testable time handling in domain/service code.
- **SEC-001**: Keep unauthenticated REST API local-only.
- **SEC-002**: Preserve trust/safety and relationship-flow guarantees.
- **PAT-001**: Keep current MVVM shape: `Controller -> ViewModel -> adapter/use-case`.
- **PAT-002**: Keep result-record based flow where already used.
- **GUD-001**: Run `mvn spotless:apply compile` after each stream.
- **GUD-002**: Run `mvn spotless:apply verify` at full-plan completion.

## 2. Implementation Steps

### Global Execution Model

- **EXEC-001**: Execute streams in order: S1 -> S2 -> S3 -> S4.
- **EXEC-002**: Inside each stream, run tasks in task-number order unless marked parallelizable.
- **EXEC-003**: Do not start a downstream task until all listed prerequisites are complete.
- **EXEC-004**: After each stream, satisfy stream validation gates before moving forward.

### Implementation Phase 1 (S1) — Correctness-Critical User Flows

- **GOAL-001**: Eliminate confirmed correctness defects in chat, daily-pick flow, and local API exposure.

| Task     | Description                                                                                                                                                                                                                              | Completed   | Date       |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-001 | `ChatController.handleSendMessage()` + `ChatViewModel.sendMessage(String)` + `ChatViewModel.dispatchSendMessage(...)`: change message-clear behavior so input is cleared only on confirmed async send success. Preserve text on failure. | ✅ COMPLETED | 2026-03-10 |
| TASK-002 | `DashboardController.handleViewDailyPick()` + `DashboardViewModel`: add viewed-state mutation path that calls existing `RecommendationService.markDailyPickViewed(UUID)` and updates `dailyPickSeenProperty()` deterministically.        | ✅ COMPLETED | 2026-03-10 |
| TASK-003 | `MatchingViewModel.initialize(UUID)` + `MatchingViewModel.prioritizeCandidate(...)`: add deterministic fallback behavior when `prioritizedCandidateId` is requested but missing from fetched candidates.                                 | ✅ COMPLETED | 2026-03-10 |
| TASK-004 | `RestApiServer.start()`: replace broad host bind (`app.start(port)`) with explicit localhost-only bind and add startup warning log indicating local-only unauthenticated usage.                                                          | ✅ COMPLETED | 2026-03-10 |

#### ✅ COMPLETED S1 IMPLEMENTATION NOTES

- ✅ COMPLETED: `ChatController` now clears the composer only after confirmed async send success; failed sends preserve the draft text.
- ✅ COMPLETED: `DashboardController`/`DashboardViewModel` now mark the daily pick as viewed through the existing service path and update `dailyPickSeenProperty()` immediately.
- ✅ COMPLETED: `MatchingViewModel` deterministic fallback behavior was verified as already implemented and regression-covered by `MatchingViewModelTest`.
- ✅ COMPLETED: `RestApiServer` now binds explicitly to `127.0.0.1` and logs a localhost-only unauthenticated-use warning at startup.

#### S1 Task-Level Preconditions and Outputs

- **PRE-001** (`TASK-001`): `ChatController` currently clears `messageArea` immediately after `sendMessage(...)` returns.
- **OUT-001** (`TASK-001`): unsent user text remains available on failed async send.
- **PRE-002** (`TASK-002`): daily-pick viewed mutation exists in service (`RecommendationService.markDailyPickViewed(UUID)`) but is not invoked from dashboard view action.
- **OUT-002** (`TASK-002`): after viewing daily pick once, `dailyPickSeenProperty().get()` becomes true and remains true after dashboard refresh.
- **PRE-003** (`TASK-003`): `prioritizedCandidateId` path exists, but current prioritization only reorders fetched list entries.
- **OUT-003** (`TASK-003`): user receives deterministic behavior when requested candidate is absent (no random unrelated candidate focus).
- **PRE-004** (`TASK-004`): API currently starts via `app.start(port)`.
- **OUT-004** (`TASK-004`): API is reachable only via localhost binding and logs an explicit local-only warning.

#### S1 Acceptance Criteria

- **AC-001**: Failed chat send does not clear input text.
- **AC-002**: Successful chat send still refreshes active conversation as before.
- **AC-003**: Daily pick viewed flag updates and persists.
- **AC-004**: Prioritized-candidate handoff does not silently degrade into an arbitrary candidate.
- **AC-005**: REST API startup remains functional with localhost-only binding.

#### S1 Validation Gate

- **VAL-001**: `mvn spotless:apply compile` passes.
- **VAL-002**: targeted tests for changed symbols pass.

### Implementation Phase 2 (S2) — Missing UX and Test Baseline Completion

- **GOAL-002**: Implement still-missing high-value UX features and close known test-file gaps.

| Task     | Description                                                                                                                                                                                                          | Completed   | Date       |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-005 | `ProfileController.handleSetLocation()`: replace raw coordinate dialog UX with structured location-entry flow while preserving existing persistence call path.                                                       | ✅ COMPLETED | 2026-03-10 |
| TASK-006 | `chat.fxml` + `ChatController`: implement Enter-to-send and preserve Shift+Enter multiline semantics.                                                                                                                | ✅ COMPLETED | 2026-03-10 |
| TASK-007 | `chat.fxml` + `ChatController`: add visible message-length indicator consistent with current max message constraints in `ChatViewModel.sendMessage(...)`.                                                            | ✅ COMPLETED | 2026-03-10 |
| TASK-008 | (Optional, conditional) `chat.fxml` + `ChatController` + `ChatViewModel`: add manual refresh control that triggers immediate refresh without interfering with polling (`refreshConversations()` behavior preserved). | ✅ COMPLETED | 2026-03-10 |
| TASK-009 | Add `DashboardControllerTest`, `LoginControllerTest`, `StatsControllerTest` under `src/test/java/datingapp/ui/screen/`.                                                                                              | ✅ COMPLETED | 2026-03-10 |
| TASK-010 | Add `LoginViewModelTest`, `StatsViewModelTest` under `src/test/java/datingapp/ui/viewmodel/`.                                                                                                                        | ✅ COMPLETED | 2026-03-10 |

#### ✅ COMPLETED S2 SUBTASK NOTES

- ✅ COMPLETED: `chat.fxml` and `ChatController` now support Enter-to-send, preserve Shift+Enter drafts, show a live length indicator, and include a manual refresh control.
- ✅ COMPLETED: `ProfileController.handleSetLocation()` now uses a structured, guided coordinate-entry flow with live validation and preview while preserving existing persistence behavior.
- ✅ COMPLETED: Added controller tests `DashboardControllerTest`, `LoginControllerTest`, and `StatsControllerTest`.
- ✅ COMPLETED: Added view-model tests `LoginViewModelTest` and `StatsViewModelTest`.

#### S2 Preconditions and Outputs

- **PRE-005**: `handleSetLocation()` currently uses two raw `TextField`s for latitude/longitude.
- **OUT-005**: location-entry UI is clearer without regressing validation.
- **PRE-006**: chat key handling currently lacks explicit Enter send behavior.
- **OUT-006**: Enter sends; Shift+Enter inserts newline.
- **PRE-007**: chat view currently lacks explicit visual length indicator.
- **OUT-007**: user sees live length indicator.
- **PRE-008** (`TASK-008`): polling already exists (`refreshConversations()` paths in `ChatViewModel`).
- **OUT-008** (`TASK-008`): manual refresh is additive and non-disruptive.
- **PRE-009**: listed controller/viewmodel test files are absent.
- **OUT-009**: all listed test files exist and pass.

#### S2 Acceptance Criteria

- **AC-006**: location flow remains functional and validated.
- **AC-007**: keyboard send semantics are deterministic.
- **AC-008**: length indicator is accurate during typing.
- **AC-009**: missing controller/viewmodel test files compile and run.

#### S2 Validation Gate

- **VAL-003**: `mvn spotless:apply compile` passes.
- **VAL-004**: all newly added UI/controller/viewmodel tests pass.

### Implementation Phase 3 (S3) — Refactor and Consistency Work

- **GOAL-003**: Execute code-quality refactors that are still justified and bounded.

| Task     | Description                                                                                                                                                                                          | Completed   | Date       |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-011 | `TrustSafetyService`: replace overload-heavy constructor pattern with explicit builder (or equivalent deterministic factory), remove `java:S107` suppression where feasible, migrate all call sites. | ✅ COMPLETED | 2026-03-10 |
| TASK-012 | `ProfileService`: if prioritized, extract profile completion/tips responsibilities into focused service(s) while preserving behavior and public API contracts.                                       | ✅ COMPLETED | 2026-03-10 |
| TASK-013 | Normalize time usage in `DefaultDailyLimitService` and `UndoService` to a single repository-consistent strategy (testable, injected where required).                                                 | ✅ COMPLETED | 2026-03-10 |
| TASK-014 | Audit synchronous service/storage calls in `ui/screen/**` and `ui/viewmodel/**`; change only confirmed UI-thread risk points.                                                                        | ✅ COMPLETED | 2026-03-10 |
| TASK-015 | Audit `ImageCache` call sites for FX-thread responsiveness problems; fix caller behavior only when evidence exists.                                                                                  | ✅ COMPLETED | 2026-03-10 |

#### ✅ COMPLETED S3 SUBTASK NOTES

- ✅ COMPLETED: `TrustSafetyService` now uses an explicit builder-based construction path, with constructor-overload call sites migrated.
- ✅ COMPLETED: `ProfileService` profile-completion/tips responsibilities were extracted into focused support service `ProfileCompletionSupport` while preserving public behavior.
- ✅ COMPLETED: `DefaultDailyLimitService` and `UndoService` time usage was re-verified as already consistent with repository testable time strategy.
- ✅ COMPLETED: UI sync-call and `ImageCache` responsiveness audits were performed; no speculative changes were made where no confirmed UI-thread risk evidence existed.

#### S3 Preconditions and Outputs

- **PRE-010**: `TrustSafetyService` currently has multiple constructor overloads.
- **OUT-010**: construction path is explicit and less error-prone.
- **PRE-011**: `ProfileService` currently combines multiple responsibilities.
- **OUT-011**: extracted structure preserves behavior and test outcomes.
- **PRE-012**: `DefaultDailyLimitService` and `UndoService` still use direct `Instant.now(clock)` in multiple places.
- **OUT-012**: time handling is internally consistent and test-friendly.
- **PRE-013**: UI-thread concerns are not universally confirmed; audit must be evidence-driven.
- **OUT-013**: only real blocking risks are changed.

#### S3 Acceptance Criteria

- **AC-010**: refactor does not regress existing trust/safety behavior.
- **AC-011**: profile functionality remains behaviorally equivalent after extraction.
- **AC-012**: time-sensitive tests remain deterministic.
- **AC-013**: no speculative UI-thread edits.

#### S3 Validation Gate

- **VAL-005**: `mvn spotless:apply compile` passes.
- **VAL-006**: targeted refactor regression tests pass.

### Implementation Phase 4 (S4) — Integration and Coverage Expansion

- **GOAL-004**: Add targeted regression/integration coverage for changed flows and hardening behavior.

| Task     | Description                                                                                                                                    | Completed   | Date       |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-016 | Expand REST tests (`RestApiRoutesTest` family) for `/api/health`, invalid UUID handling, and localhost-bind startup behavior introduced in S1. | ✅ COMPLETED | 2026-03-10 |
| TASK-017 | Add regression tests for daily-pick viewed-state behavior and prioritized-candidate fallback behavior.                                         | ✅ COMPLETED | 2026-03-10 |
| TASK-018 | Add regression tests covering `TrustSafetyService` and/or `ProfileService` boundaries changed in S3.                                           | ✅ COMPLETED | 2026-03-10 |
| TASK-019 | Run full quality gate and record final test/verification result snapshot in PR/task notes.                                                     | ✅ COMPLETED | 2026-03-11 |

#### ✅ COMPLETED S4 SUBTASK NOTES

- ✅ COMPLETED: Added `RestApiHealthRoutesTest` covering `/api/health` and invalid UUID behavior for REST hardening paths.
- ✅ COMPLETED: Added/validated regressions for daily-pick viewed-state and prioritized-candidate fallback behavior in controller/viewmodel tests.
- ✅ COMPLETED: Refactor-boundary regressions passed for trust/safety and profile services after S3 changes.
- ✅ COMPLETED: Full quality gate re-verified on 2026-03-11 via `mvn spotless:apply verify` with `BUILD SUCCESS` and test summary `Tests run: 1026, Failures: 0, Errors: 0, Skipped: 2`.

#### S4 Acceptance Criteria

- **AC-014**: API edge-case coverage is broader and passing.
- **AC-015**: S1/S3 changed flows are regression-protected.
- **AC-016**: full quality gate succeeds.

#### S4 Validation Gate

- **VAL-007**: `mvn spotless:apply verify` passes.

## 3. Alternatives

- **ALT-001**: Re-implement already working features (photo filters, save-button disable, daily-pick empty state, theme restore, notes navigation). Rejected: already complete.
- **ALT-002**: Solve prioritized-candidate fallback via direct storage access in `MatchingViewModel`. Rejected: violates architecture boundary.
- **ALT-003**: Fold full presence-system feature into this plan. Rejected: separate product feature, not required for current defects.
- **ALT-004**: Perform broad visual redesign audit in this plan. Rejected: not deterministic enough for implementation agent execution.

## 4. Dependencies

- **DEP-001**: `RecommendationService.markDailyPickViewed(UUID)` (existing service hook).
- **DEP-002**: `MatchingViewModel.prioritizedCandidateId` and `prioritizeCandidate(...)` path.
- **DEP-003**: `ChatViewModel.refreshConversations()` polling behavior.
- **DEP-004**: `NavigationService` context handoff semantics.
- **DEP-005**: `ProfileUseCases.updateDiscoveryPreferences(...)` and `UiPreferencesStore` for preferences persistence (already present; do not re-implement).
- **DEP-006**: Existing API test suite structure under `src/test/java/datingapp/app/api/`.

## 5. Files

- **FILE-001**: `src/main/java/datingapp/ui/screen/ChatController.java`
- **FILE-002**: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- **FILE-003**: `src/main/java/datingapp/ui/screen/DashboardController.java`
- **FILE-004**: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- **FILE-005**: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- **FILE-006**: `src/main/java/datingapp/app/api/RestApiServer.java`
- **FILE-007**: `src/main/java/datingapp/ui/screen/ProfileController.java`
- **FILE-008**: `src/main/resources/fxml/chat.fxml`
- **FILE-009**: `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- **FILE-010**: `src/main/java/datingapp/core/profile/ProfileService.java`
- **FILE-011**: `src/main/java/datingapp/core/matching/DefaultDailyLimitService.java`
- **FILE-012**: `src/main/java/datingapp/core/matching/UndoService.java`
- **FILE-013**: `src/main/java/datingapp/ui/ImageCache.java`
- **FILE-014**: `src/test/java/datingapp/ui/screen/` (new controller tests)
- **FILE-015**: `src/test/java/datingapp/ui/viewmodel/` (new viewmodel tests)
- **FILE-016**: `src/test/java/datingapp/app/api/` (expanded API tests)

## 6. Testing

- **TEST-001**: Compile gate after each stream: `mvn spotless:apply compile`.
- **TEST-002**: Full gate at completion: `mvn spotless:apply verify`.
- **TEST-003**: Chat send failure preserves typed text.
- **TEST-004**: Chat send success clears input and refreshes thread.
- **TEST-005**: Daily pick viewed state persists after reload.
- **TEST-006**: Prioritized candidate fallback behavior is deterministic when requested candidate is absent.
- **TEST-007**: REST localhost-only bind behavior is verified.
- **TEST-008**: `/api/health` and invalid UUID API scenarios covered.
- **TEST-009**: Added controller tests (`Dashboard`, `Login`, `Stats`) pass.
- **TEST-010**: Added viewmodel tests (`LoginViewModel`, `StatsViewModel`) pass.
- **TEST-011**: Refactor regression tests for `TrustSafetyService`/`ProfileService` pass.
- **TEST-012**: Time-sensitive matching tests pass after clock-normalization updates.

## 7. Risks & Assumptions

- **RISK-001**: Candidate fallback may require adapter interface extension.
- **RISK-002**: Chat send-flow edits may regress conversation refresh behavior.
- **RISK-003**: `TrustSafetyService` refactor touches many call sites.
- **RISK-004**: `ProfileService` extraction may have broad test impact.
- **RISK-005**: Clock consistency changes may affect timeout/expiry semantics.
- **RISK-006**: Optional manual refresh can create duplicated refresh traffic if not carefully integrated.
- **ASSUMPTION-001**: Already-implemented features remain stable and should not be reopened unless failing tests indicate regression.
- **ASSUMPTION-002**: Local repository state on 2026-03-10 remains baseline for this plan.

## 8. Related Specifications / Further Reading

- Local code: `src/main/**`
- Local tests: `src/test/**`
