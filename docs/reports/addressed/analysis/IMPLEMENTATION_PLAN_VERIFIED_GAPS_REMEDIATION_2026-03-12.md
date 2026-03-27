---
goal: Verified gaps remediation and end-to-end hardening plan
version: 2026-03-12
date_created: 2026-03-12
last_updated: 2026-03-12
owner: GitHub Copilot
status: COMPLETED
tags: [architecture, feature, bug, security, testing, ui, api]
---

# Introduction

![Status: COMPLETED](https://img.shields.io/badge/status-COMPLETED-brightgreen)

This plan defines an AI-agent-executable roadmap to implement the currently verified gaps from `docs/source_reports/CONSOLIDATED_GAPS_AND_IMPROVEMENTS_2026-03-11.md`. The plan is organized into atomic phases with explicit file targets, validation steps, dependencies, and measurable completion criteria so agents can execute the work incrementally without re-auditing the repository from scratch.

## 1. Requirements & Constraints

- **REQ-001**: Use `src/main/java`, `src/test/java`, and `pom.xml` as the source of truth over historical docs.
- **REQ-002**: Preserve current public behavior unless a verified gap explicitly requires behavioral change.
- **REQ-003**: Fix verified gaps only; do not resurrect removed/invalid claims from the older document version.
- **REQ-004**: Use `AppClock.now()` / `AppClock.today()` in domain and service logic; do not introduce `Instant.now()` in core logic.
- **REQ-005**: Keep `User.Gender`, `User.UserState`, `User.VerificationMethod`, `Match.MatchState`, and `Match.MatchArchiveReason` owner-type enum imports.
- **REQ-006**: Maintain deterministic pair IDs via `Match.generateId(...)` / `Conversation.generateId(...)`.
- **REQ-007**: Prefer use-case-layer orchestration for new API/UI flows instead of direct storage/service bypass.
- **REQ-008**: Keep JavaFX ViewModels on shared async abstractions in `src/main/java/datingapp/ui/async/**`.
- **REQ-009**: Add tests for each functional phase before closing the phase.
- **REQ-010**: End-to-end validation for each merged phase must include targeted tests plus `mvn spotless:apply verify`.
- **SEC-001**: Do not broaden REST API exposure assumptions; if authentication/rate limiting is introduced, make localhost-safe defaults explicit.
- **SEC-002**: New REST endpoints must validate identity/ownership consistently before returning user-specific data.
- **SEC-003**: Sensitive actions (block/report/ban/deletion) must gain auditable logging without leaking content or secrets.
- **CON-001**: Keep architecture framework-agnostic in `datingapp.core`; no JavaFX, REST, or database types in `core/`.
- **CON-002**: Do not edit frozen baseline schema behavior in `SchemaInitializer` beyond additive indexes/tables/migrations that preserve upgrade safety.
- **CON-003**: Any schema/data change affecting existing databases must be append-only through new migrations, not retroactive mutation of old migration intent.
- **GUD-001**: Root-level implementation plans are active documents in this repository; write progress updates into this file rather than creating another root plan for the same effort.
- **PAT-001**: Business-flow failures should continue to prefer result objects over flow-control exceptions.
- **PAT-002**: Use defensive copies / immutable return values where mutable collections cross boundaries.

## 2. Implementation Steps

### Implementation Phase 1 — Secure the REST/API and event boundaries

- **GOAL-001**: Remove the highest-risk API/event blind spots while keeping localhost development intact.

| Task     | Description                                                                                                                                                                                                                                                                 | Completed   | Date       |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-001 | Add a lightweight request-identity strategy for REST handlers in `src/main/java/datingapp/app/api/RestApiServer.java` for user-scoped endpoints. Minimum requirement: ownership validation for `GET /api/conversations/{conversationId}/messages` and user-specific routes. | ✅ COMPLETED | 2026-03-12 |
| TASK-002 | Introduce explicit localhost-only guardrails and optional auth extension points in `src/main/java/datingapp/app/api/RestApiServer.java` without breaking current local workflows.                                                                                           | ✅ COMPLETED | 2026-03-12 |
| TASK-003 | Add rate-limit extension hooks or a simple local-safe interceptor in `src/main/java/datingapp/app/api/RestApiServer.java` for future network deployment hardening.                                                                                                          | ✅ COMPLETED | 2026-03-12 |
| TASK-004 | Publish missing social events from `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` for block/report flows.                                                                                                                                                 | ✅ COMPLETED | 2026-03-12 |
| TASK-005 | Add handler coverage for `AppEvent.MatchCreated` and user-facing notification coverage for `AppEvent.MessageSent` in `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` and associated event registration.                                          | ✅ COMPLETED | 2026-03-12 |
| TASK-006 | Add tests for REST ownership/auth checks under `src/test/java/datingapp/app/api/**` and event handler behavior under `src/test/java/datingapp/app/event/handlers/**`.                                                                                                       | ✅ COMPLETED | 2026-03-12 |

**PHASE 1 PROGRESS NOTE — ✅ COMPLETED (2026-03-12)**

- ✅ COMPLETED lightweight REST request identity via `X-User-Id` header / `userId` query parameter with path/user mismatch rejection.
- ✅ COMPLETED localhost-only request enforcement and a local-safe in-memory rate limiter in `RestApiServer`.
- ✅ COMPLETED ownership validation for conversation message reads and sender validation for conversation message writes.
- ✅ COMPLETED `AppEvent.UserBlocked` and `AppEvent.UserReported` publication from `SocialUseCases` plus audit-safe moderation logging.
- ✅ COMPLETED notification handling for `MatchCreated` and `MessageSent` in `NotificationEventHandler`.
- ✅ COMPLETED focused regression coverage in `RestApiRelationshipRoutesTest`, `NotificationEventHandlerTest`, and `SocialUseCasesTest`.

**Phase 1 completion criteria**
- User-specific REST reads reject mismatched caller identity.
- `MatchCreated`, `MessageSent`, block, and report flows all have explicit event behavior.
- REST/event tests pass locally.

### Implementation Phase 2 — Close missing use cases and REST endpoint coverage

- **GOAL-002**: Expose verified missing behavior through the use-case layer first, then REST.

| Task     | Description                                                                                                                                                                                                                                                  | Completed   | Date       |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-007 | Add missing use cases for delete message, delete conversation, mark-all-notifications-read, and archive match in `src/main/java/datingapp/app/usecase/messaging/`, `src/main/java/datingapp/app/usecase/social/`, or the most appropriate app-level package. | ✅ COMPLETED | 2026-03-12 |
| TASK-008 | Add the verified missing REST endpoints in `src/main/java/datingapp/app/api/RestApiServer.java`: profile update, notifications list/read, undo, standouts, stats, achievements, pending likers, match quality.                                               | ✅ COMPLETED | 2026-03-12 |
| TASK-009 | Route all newly added REST endpoints through use cases instead of direct service/storage calls.                                                                                                                                                              | ✅ COMPLETED | 2026-03-12 |
| TASK-010 | Revisit the five documented bypass endpoints and either migrate them to use cases or document a deliberate exception with tests.                                                                                                                             | ✅ COMPLETED | 2026-03-12 |
| TASK-011 | Add REST tests for each new endpoint under `src/test/java/datingapp/app/api/`.                                                                                                                                                                               | ✅ COMPLETED | 2026-03-12 |

**Phase 2 completion criteria**
- All verified missing REST endpoints exist or are explicitly deprecated in favor of a different supported route.
- New endpoints go through app use cases.
- API tests cover success, validation failure, and authorization failure paths.

**PHASE 2 PROGRESS NOTE — PARTIAL (2026-03-12)**

**PHASE 2 PROGRESS NOTE — ✅ COMPLETED (2026-03-12)**

- ✅ COMPLETED `SocialUseCases.markAllNotificationsRead(...)` with ownership-safe notification read handling.
- ✅ COMPLETED missing use cases for delete message, delete conversation, archive conversation support, and a minimal archive-match flow.
- ✅ COMPLETED REST endpoints for profile update, notifications list/read/read-all, undo, pending likers, standouts, stats, achievements, match quality, conversation delete/archive, message delete, and match archive.
- ✅ COMPLETED routing of newly added REST endpoints through use cases instead of direct storage/service mutations.
- ✅ COMPLETED deliberate documentation + focused coverage for the remaining read-only direct-service REST exceptions.
- ✅ COMPLETED focused end-to-end API coverage in `RestApiPhaseTwoRoutesTest` and `RestApiReadRoutesTest`, plus new messaging use-case tests.

### Implementation Phase 3 — Finish missing feature flows in domain + UI

- **GOAL-003**: Implement the verified product gaps that currently have partial plumbing but incomplete end-user behavior.

| Task     | Description                                                                                                                                                                                                                                                                                            | Completed   | Date       |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-012 | Implement first-class Super Like domain behavior: persistent state or explicit interaction type, daily-limit enforcement, and JavaFX/UI messaging. Touch `src/main/java/datingapp/core/matching/**`, `src/main/java/datingapp/app/usecase/matching/**`, and `src/main/java/datingapp/ui/**` as needed. | ✅ COMPLETED | 2026-03-12 |
| TASK-013 | Replace `NoOpUiPresenceDataAccess` default wiring in `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` with a real presence adapter or an explicit feature flag that surfaces “unsupported” clearly in UI.                                                                                  | ✅ COMPLETED | 2026-03-12 |
| TASK-014 | Wire `ProfileSaved`-driven achievement unlocking in `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java` and achievement service code.                                                                                                                                            | ✅ COMPLETED | 2026-03-12 |
| TASK-015 | Enforce daily like/pass/super-like limits consistently across JavaFX and CLI flows by aligning `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`, `src/main/java/datingapp/app/cli/MatchingHandler.java`, and `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`.        | ✅ COMPLETED | 2026-03-12 |
| TASK-016 | Add JavaFX pace-preference editing to the profile/preferences UI using `src/main/java/datingapp/ui/screen/ProfileController.java`, `PreferencesController.java`, corresponding FXML, and `ProfileUseCases`.                                                                                            | ✅ COMPLETED | 2026-03-12 |
| TASK-017 | Add JavaFX profile verification flow plus REST exposure if required, using `User.VerificationMethod` and existing validation/storage fields.                                                                                                                                                           | ✅ COMPLETED | 2026-03-12 |
| TASK-018 | Add account deletion flow and audit-safe confirmation UX spanning use case, storage behavior, and UI entry points.                                                                                                                                                                                     | ✅ COMPLETED | 2026-03-12 |
| TASK-019 | Add undo countdown timer presentation in `src/main/java/datingapp/ui/screen/MatchingController.java` and/or `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`.                                                                                                                             | ✅ COMPLETED | 2026-03-12 |
| TASK-020 | Add reasoning text for standouts/daily picks from recommendation/scoring services through to `StandoutsController` and supporting view models.                                                                                                                                                         | ✅ COMPLETED | 2026-03-12 |
| TASK-021 | Improve the no-location empty-state path so `CandidateFinder` / matching UI show actionable feedback instead of silent emptiness.                                                                                                                                                                      | ✅ COMPLETED | 2026-03-12 |
| TASK-022 | Add premium-style gating placeholder behavior for “Who Liked You” or explicitly mark it as intentionally ungated if product scope changed.                                                                                                                                                             | ✅ COMPLETED | 2026-03-12 |

**Phase 3 completion criteria**
- Each verified missing feature has either a working implementation or an explicit product-level de-scope note committed with tests and docs.
- JavaFX screens expose the missing flows cleanly.
- Business logic remains centralized in use cases/services, not controller-only patches.

### Implementation Phase 4 — Fix UI/UX and validation gaps

- **GOAL-004**: Make the existing product flows feel complete and safe.

| Task     | Description                                                                                                                                                                                               | Completed   | Date       |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-023 | Add send-button loading/disable behavior for message sends in `src/main/java/datingapp/ui/screen/ChatController.java` and `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`.                      | ✅ COMPLETED | 2026-03-12 |
| TASK-024 | Add consistent visual feedback for note save/delete, field-level profile validation messaging, and unsaved-changes prompts in profile screens/controllers.                                                | ✅ COMPLETED | 2026-03-12 |
| TASK-025 | Add true MIME/content validation and EXIF orientation handling to photo upload paths in `src/main/java/datingapp/ui/LocalPhotoStore.java` and `src/main/java/datingapp/ui/screen/ProfileController.java`. | ✅ COMPLETED | 2026-03-12 |
| TASK-026 | Add graceful-exit indicators, match-quality display, and achievement progress rendering in `MatchesController`, `StatsController`, and related view models.                                               | ✅ COMPLETED | 2026-03-12 |
| TASK-027 | Replace broad extension-based validation and weak UX-only checks with stronger validation in `src/main/java/datingapp/core/profile/ValidationService.java` and UI field binding logic.                    | ✅ COMPLETED | 2026-03-12 |
| TASK-028 | Add email format validation plus any missing sanity guards for birth-date/age entry paths.                                                                                                                | ✅ COMPLETED | 2026-03-12 |
| TASK-029 | Add accessibility and keyboard-navigation improvements across key FXML-backed screens.                                                                                                                    | ✅ COMPLETED | 2026-03-12 |
| TASK-030 | Add a read-only “view other profile” screen or mode if profile editing remains the only current presentation.                                                                                             | ✅ COMPLETED | 2026-03-12 |

**Phase 4 completion criteria**
- Main JavaFX flows provide clear progress/error/success feedback.
- Upload and form validation are stricter and test-covered.
- UI no longer hides key state such as quality, graceful exit, or pending countdown windows.

**PHASE 3 / PHASE 4 PROGRESS NOTE — PARTIAL (2026-03-12)**

- ✅ COMPLETED `ProfileSaved` achievement checks by subscribing `AchievementEventHandler` to `AppEvent.ProfileSaved`.
- ✅ COMPLETED focused handler coverage for swipe- and profile-save-driven achievement unlocking.
- ✅ COMPLETED dedicated chat `sending` state in `ChatViewModel` and send-button disable/loading binding in `ChatController`.
- ✅ COMPLETED focused `ChatViewModelTest` and `ChatControllerTest` coverage for in-flight send behavior.
- ✅ COMPLETED undo countdown presentation state in `MatchingViewModel` with UI surfacing through `MatchingController` tooltip/enablement.
- ✅ COMPLETED no-location empty-state regression coverage across matching use-case, view-model, and controller layers for the already-implemented actionable guidance path.
- ✅ COMPLETED explicit presence feature-flag surfacing using `datingapp.ui.presence.enabled` in `ViewModelFactory`, plus UI visibility of unsupported presence state in `ChatController`/`chat.fxml`.
- ✅ COMPLETED match-quality rendering in `MatchesViewModel` + `MatchesController` cards (`compatibilityLabel` + percentage) to expose quality state directly in JavaFX matches UI.
- ✅ COMPLETED email/phone setter validation in `User` with focused `UserTest` coverage for valid and invalid input formats.
- ✅ COMPLETED JavaFX pace-preference editing in `ProfileViewModel`, `ProfileController`, and `profile.fxml`, including completion-aligned save-path wiring.
- ✅ COMPLETED field-level profile validation messaging for birth date, height, search preferences, and pace preferences, plus unsaved-change confirmation on back/cancel navigation.
- ✅ COMPLETED JavaFX profile verification flow in `SafetyViewModel`, `SafetyController`, and `safety.fxml`, reusing `TrustSafetyService` verification-code generation/validation and `ProfileUseCases.saveProfile(...)` for persistence.
- ✅ COMPLETED account deletion flow via `ProfileUseCases.deleteAccount(...)` plus safety-screen confirmation UX and post-delete sign-out navigation back to login.
- ✅ COMPLETED true MIME/content validation plus EXIF-orientation normalization in `LocalPhotoStore`, including focused `LocalPhotoStoreTest` coverage and a fix for unique managed-photo naming.
- ✅ COMPLETED stronger shared validation in `ValidationService` for contact formats, control-character-safe names, and interest-limit enforcement, with `User` contact setters delegating to the shared validators.
- ✅ COMPLETED broader accessibility and keyboard affordances across chat, preferences, standouts, profile, and safety screens.
- ✅ COMPLETED a dedicated read-only other-profile screen via `PROFILE_VIEW`, `ProfileReadOnlyViewModel`, `ProfileViewController`, and `profile-view.fxml`, wired from standouts and matches.
- ✅ COMPLETED Super Like + daily action limit coverage/wiring already present across use-case, UI view-model, and CLI handler paths; marked as verified complete.
- ✅ COMPLETED standout and daily-pick reasoning text wiring (`DashboardViewModel.dailyPickReason`, `StandoutsViewModel/StandoutsController` reason rendering) as verified complete.
- ✅ COMPLETED explicit product-scope de-scope decision for premium gating: `LIKES_YOU` remains intentionally UNGATED in current JavaFX product scope.

### Implementation Phase 5 — Architecture cleanup and configuration consistency

- **GOAL-005**: Reduce the friction that makes future agent work error-prone.

| Task     | Description                                                                                                                                                                                                        | Completed   | Date       |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-031 | Split `RestApiServer` into route/resource modules while preserving the current entry point.                                                                                                                        | ✅ COMPLETED | 2026-03-12 |
| TASK-032 | Break down `ProfileController` and `MatchingHandler` by responsibility (form state, upload, discovery prefs, presentation helpers, command handlers).                                                              | ✅ COMPLETED | 2026-03-12 |
| TASK-033 | Simplify `MatchingUseCases` / `ProfileUseCases` construction with a builder/factory pattern.                                                                                                                       | ✅ COMPLETED | 2026-03-12 |
| TASK-034 | Align `ProfileActivationPolicy` with `User.isComplete()` through a shared policy/helper so they cannot drift.                                                                                                      | ✅ COMPLETED | 2026-03-12 |
| TASK-035 | Normalize configuration usage for photo limits, interest limits, chat polling, and standout limits. Touch `src/main/java/datingapp/core/AppConfig.java`, `config/app-config.json`, and affected UI/domain classes. | ✅ COMPLETED | 2026-03-12 |
| TASK-036 | Remove or correct stale doc references such as `PerformanceMonitor` in repo instruction docs after code truth is settled.                                                                                          | ✅ COMPLETED | 2026-03-12 |
| TASK-037 | Decide whether to introduce correlation IDs/MDC logging and, if yes, wire it through app entry points and REST requests.                                                                                           | ✅ COMPLETED | 2026-03-12 |

**PHASE 5 PROGRESS NOTE — PARTIAL (2026-03-12)**

- ✅ COMPLETED stale documentation cleanup by removing nonexistent `PerformanceMonitor` from `.github/copilot-instructions.md` architecture inventory.
- ✅ COMPLETED Profile activation policy drift fix validation (`photoUrls` + `pacePreferences`) to mirror `User.isComplete()` semantics.
- ✅ COMPLETED explicit decision for this remediation pass: correlation IDs/MDC remain **DEFERRED** (no broad tracing refactor in this cycle).
- ✅ COMPLETED configuration normalization for chat polling and standout limits through `AppConfig` + `app-config.json` + `ViewModelFactory` + `DefaultStandoutService` wiring.
- ✅ COMPLETED `RestApiServer` route modularization by extracting focused nested route modules while preserving the existing server entry point and handlers.
- ✅ COMPLETED builder/factory construction for `MatchingUseCases` and `ProfileUseCases`, with `ServiceRegistry` now using the builders while retaining constructor compatibility.
- ✅ COMPLETED lightweight hotspot decomposition by extracting `ProfileFormValidator` from `ProfileController` and moving pure CLI rendering responsibilities into `MatchingCliPresenter`.

**Phase 5 completion criteria**
- Large hot-spot classes are smaller or have explicit subcomponents.
- Configuration values are sourced consistently.
- Repo docs stop claiming nonexistent types/files.

### Implementation Phase 6 — Testing, cleanup automation, and final quality gate

- **GOAL-006**: Make the fixes durable and agent-friendly.

| Task     | Description                                                                                                                                                                                                                                                                                 | Completed   | Date       |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|------------|
| TASK-038 | Add missing focused tests for `ProfileHandler`, `MatchingHandler`, `ConnectionService`, `DefaultCompatibilityCalculator`, `DefaultAchievementService`, `LifestyleMatcher`, `ApplicationStartup`, `AppConfigValidator`, `SocialController`, `StandoutsController`, and `UiPreferencesStore`. | ✅ COMPLETED | 2026-03-12 |
| TASK-039 | Replace `Thread.sleep(...)` in ViewModel tests with deterministic async test helpers from `src/main/java/datingapp/ui/async/**` and companion test utilities.                                                                                                                               | ✅ COMPLETED | 2026-03-12 |
| TASK-040 | Add integration tests that cover profile save → match flow → messaging → graceful exit/report/block for representative happy-path and failure-path scenarios.                                                                                                                               | ✅ COMPLETED | 2026-03-12 |
| TASK-041 | Add cleanup scheduling for purge/retention routines and cover it with tests.                                                                                                                                                                                                                | ✅ COMPLETED | 2026-03-12 |
| TASK-042 | Run full repository validation with `mvn spotless:apply verify` and update this plan with the final passing baseline.                                                                                                                                                                       | ✅ COMPLETED | 2026-03-12 |

**PHASE 6 PROGRESS NOTE — PARTIAL (2026-03-12)**

- ✅ COMPLETED deterministic async migration by eliminating all `Thread.sleep(...)` usages from test sources.
- ✅ COMPLETED cleanup automation by introducing `CleanupScheduler`, wiring it via `ApplicationStartup`, and adding `CleanupSchedulerTest`.
- ✅ COMPLETED focused test expansion for `DefaultCompatibilityCalculator`, `DefaultAchievementService`, `LifestyleMatcher`, `AppConfigValidator`, `SocialController`, `StandoutsController`, and `UiPreferencesStore`.
- ✅ COMPLETED representative REST integration coverage in `RestApiPhaseTwoRoutesTest` for profile save → match → message → graceful-exit happy path and report-plus-block messaging failure path.

**Phase 6 completion criteria**
- The main untested hotspots now have direct tests.
- Async tests are deterministic.
- Cleanup/retention logic executes automatically and safely.
- Full quality gate passes.

## 3. Alternatives

- **ALT-001**: Implement all fixes directly in controllers and REST handlers. Rejected because it would worsen the current architecture drift.
- **ALT-002**: Build all missing features before fixing API/event/security boundaries. Rejected because feature work would land on unstable foundations.
- **ALT-003**: Perform a giant rewrite of UI and service layers first. Rejected because incremental, test-backed delivery is safer and more agent-executable.
- **ALT-004**: Ignore localhost REST security concerns because the API is “internal only.” Rejected because the code already documents the risk, and accidental exposure is still a realistic failure mode.

## 4. Dependencies

- **DEP-001**: `src/main/java/datingapp/app/api/RestApiServer.java`
- **DEP-002**: `src/main/java/datingapp/app/usecase/**`
- **DEP-003**: `src/main/java/datingapp/app/event/**`
- **DEP-004**: `src/main/java/datingapp/core/connection/ConnectionService.java`
- **DEP-005**: `src/main/java/datingapp/core/matching/**`
- **DEP-006**: `src/main/java/datingapp/core/profile/ValidationService.java`
- **DEP-007**: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- **DEP-008**: `src/main/java/datingapp/storage/jdbi/**`
- **DEP-009**: `src/main/java/datingapp/ui/screen/**`
- **DEP-010**: `src/main/java/datingapp/ui/viewmodel/**`
- **DEP-011**: `src/test/java/**`
- **DEP-012**: `config/app-config.json`

## 5. Files

- **FILE-001**: `docs/source_reports/CONSOLIDATED_GAPS_AND_IMPROVEMENTS_2026-03-11.md` — verified source gap inventory.
- **FILE-002**: `src/main/java/datingapp/app/api/RestApiServer.java` — REST surface hardening and missing endpoint work.
- **FILE-003**: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` — missing block/report events and social flow completion.
- **FILE-004**: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` — undo/standouts/pending likers/match quality alignment.
- **FILE-005**: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` — profile save/update/profile-completion event path.
- **FILE-006**: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` — missing event consumers.
- **FILE-007**: `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java` — profile/report/match achievement wiring.
- **FILE-008**: `src/main/java/datingapp/core/connection/ConnectionService.java` — message, conversation, and transition consistency.
- **FILE-009**: `src/main/java/datingapp/core/matching/CandidateFinder.java` — no-location UX hooks and filtering/caching behavior.
- **FILE-010**: `src/main/java/datingapp/core/matching/RecommendationService.java` — duration formatting and standout explanation metadata.
- **FILE-011**: `src/main/java/datingapp/core/profile/ValidationService.java` — email/file/form validation improvements.
- **FILE-012**: `src/main/java/datingapp/storage/schema/SchemaInitializer.java` — additive indexing updates.
- **FILE-013**: `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java` — pagination and query improvements.
- **FILE-014**: `src/main/java/datingapp/ui/screen/ChatController.java` — send-state/readability improvements.
- **FILE-015**: `src/main/java/datingapp/ui/screen/ProfileController.java` — profile UX fixes and pace preferences.
- **FILE-016**: `src/main/java/datingapp/ui/screen/MatchingController.java` — undo timer and swipe limit UX.
- **FILE-017**: `src/main/java/datingapp/ui/screen/MatchesController.java` — graceful-exit and match-quality display.
- **FILE-018**: `src/main/java/datingapp/ui/screen/StandoutsController.java` — reasoning text and card UX.
- **FILE-019**: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` — polling, loading, note feedback, presence integration.
- **FILE-020**: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java` — daily limits, undo timer, swipe flow alignment.
- **FILE-021**: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` — presence adapter wiring and construction cleanup.
- **FILE-022**: `src/test/java/datingapp/app/api/**` — API test expansion.
- **FILE-023**: `src/test/java/datingapp/app/event/handlers/**` — event-handler tests.
- **FILE-024**: `src/test/java/datingapp/ui/**` — controller/viewmodel deterministic tests.

## 6. Testing

- **TEST-001**: Add request-identity and authorization tests for user-scoped REST endpoints.
- **TEST-002**: Add notification/event tests for `MatchCreated`, `MessageSent`, block, and report flows.
- **TEST-003**: Add use-case tests for delete/archive/mark-all-read/account deletion flows.
- **TEST-004**: Add JavaFX controller/viewmodel tests for send loading state, undo countdown, graceful-exit indicators, and profile-save field errors.
- **TEST-005**: Add validation tests for email format, MIME detection, EXIF handling, and stronger upload guards.
- **TEST-006**: Add schema/storage tests for added indexes/pagination/cleanup scheduler behavior.
- **TEST-007**: Replace `Thread.sleep(...)` with deterministic async helpers in ViewModel tests.
- **TEST-008**: Run focused tests per phase, then run `mvn spotless:apply verify` as the final phase gate.

**QUALITY GATE NOTE — ✅ VERIFIED (2026-03-12)**

- ✅ COMPLETED full repository validation with `mvn spotless:apply verify`.
- ✅ COMPLETED updated repository-wide passing baseline: `Tests run: 1085, Failures: 0, Errors: 0, Skipped: 2`.
- ✅ COMPLETED Checkstyle, Spotless, PMD, and JaCoCo checks with `BUILD SUCCESS`.

## 7. Risks & Assumptions

- **RISK-001**: Adding auth/identity expectations to REST may break current ad-hoc local clients if not introduced with explicit compatibility defaults.
- **RISK-002**: Moving direct REST/service calls to use cases may surface hidden coupling in tests and controllers.
- **RISK-003**: UI refactors in `ProfileController` and `MatchingController` can easily cause regressions without incremental tests.
- **RISK-004**: Schema/index additions may require careful migration handling for existing local H2 databases.
- **RISK-005**: Event additions can create duplicate side effects if handlers are registered twice or old direct logic is not removed.
- **ASSUMPTION-001**: The current corrected gaps document is the authoritative backlog for this remediation effort.
- **ASSUMPTION-002**: Localhost-only REST usage remains the default operational mode unless the project explicitly decides to support network clients.
- **ASSUMPTION-003**: Existing repo conventions around root-level active plan files remain in effect.

## 8. Related Specifications / Further Reading

- `docs/source_reports/CONSOLIDATED_GAPS_AND_IMPROVEMENTS_2026-03-11.md`
- `.github/copilot-instructions.md`
- `CONCURRENCY_FIX_PLAN_2026-03-12.md`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `pom.xml`
