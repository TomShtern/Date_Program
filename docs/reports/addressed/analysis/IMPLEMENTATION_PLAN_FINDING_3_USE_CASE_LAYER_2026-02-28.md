# Implementation Plan: Finding 3 (No Dedicated Application Use-Case Layer)

Date: 2026-02-28
Source: `docs/audits/RETROSPECTIVE_ARCHITECTURE_DECISIONS_2026-02-27.md` (Decision 3)
Status: Implemented (2026-02-28)
Owner: Architecture + App layer maintainers

## 1. Goal

Create a dedicated application use-case layer so business orchestration is implemented once and reused by CLI and JavaFX adapters.

Primary outcomes:
1. Remove duplicated orchestration from handlers and viewmodels.
2. Keep CLI/UI as input-output mappers only.
3. Ensure behavior parity across channels for core flows.

## 2. Current Duplication Hotspots

The following files currently orchestrate business flow directly and duplicate logic across channels:

1. `src/main/java/datingapp/app/cli/MatchingHandler.java`
2. `src/main/java/datingapp/app/cli/MessagingHandler.java`
3. `src/main/java/datingapp/app/cli/ProfileHandler.java`
4. `src/main/java/datingapp/app/cli/SafetyHandler.java`
5. `src/main/java/datingapp/app/cli/StatsHandler.java`
6. `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
7. `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
8. `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
9. `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
10. `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

### 2.1 Flow Inventory (Baseline → Use-Case Mapping)

| Flow                           | CLI entry point                                        | JavaFX entry point                                 | Use-case path now used                                          | Dependencies coordinated by use-case                       | Output/failure contract                                              |
|--------------------------------|--------------------------------------------------------|----------------------------------------------------|-----------------------------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------------|
| Browse candidates              | `MatchingHandler.browseCandidates()`                   | `MatchingViewModel.refreshCandidates()`            | `MatchingUseCases.browseCandidates(...)`                        | `CandidateFinder`, `RecommendationService`                 | `BrowseCandidatesResult` or typed validation/conflict/internal error |
| Swipe + match detection        | `MatchingHandler.processCandidateInteraction(...)`     | `MatchingViewModel.processSwipe(...)`              | `MatchingUseCases.processSwipe(...)`                            | `MatchingService`, optional `RecommendationService`        | `SwipeResult` or conflict/internal error                             |
| Undo swipe                     | `MatchingHandler.promptUndo(...)`                      | `MatchingViewModel.undoLastSwipe()`                | `MatchingUseCases.undoSwipe(...)`                               | `UndoService`                                              | `UndoResult` or conflict/dependency/internal error                   |
| View matches/details           | `MatchingHandler.viewMatches()`                        | `MatchesViewModel.refresh()`                       | `MatchingUseCases.listActiveMatches(...)` + `matchQuality(...)` | `InteractionStorage`, `UserStorage`, `MatchQualityService` | `ActiveMatchesResult` / `MatchQuality` or typed failure              |
| Send message                   | `MessagingHandler.sendConversationMessage(...)`        | `ChatViewModel.sendMessage()`                      | `MessagingUseCases.sendMessage(...)`                            | `ConnectionService`                                        | `SendResult` wrapped in `UseCaseResult`                              |
| Load conversation              | `MessagingHandler.showConversation(...)`               | `ChatViewModel.loadMessages()`                     | `MessagingUseCases.loadConversation(...)`                       | `ConnectionService`                                        | `ConversationThread` + mark-read semantics                           |
| Friend request accept/decline  | `MatchingHandler.handleFriendRequestResponse(...)`     | `SocialViewModel.acceptRequest()/declineRequest()` | `SocialUseCases.respondToFriendRequest(...)`                    | `ConnectionService`                                        | `TransitionResult` or typed conflict/error                           |
| Block/unmatch/report           | `MatchingHandler`, `MessagingHandler`, `SafetyHandler` | `MatchingViewModel`, `ChatViewModel`               | `SocialUseCases.blockUser/unmatch/reportUser/...`               | `TrustSafetyService`, `ConnectionService`                  | `BlockResult` / `TransitionResult` / `ReportResult`                  |
| Profile save/activation checks | `ProfileHandler.completeProfile()`                     | `ProfileViewModel.saveProfile()`                   | `ProfileUseCases.saveProfile(...)`                              | `UserStorage`, `ProfileService`                            | `ProfileSaveResult` (activated flag + achievements)                  |

## 3. Target Architecture

Introduce `app/usecase` as the only orchestration surface:

1. `src/main/java/datingapp/app/usecase/common`
2. `src/main/java/datingapp/app/usecase/matching`
3. `src/main/java/datingapp/app/usecase/messaging`
4. `src/main/java/datingapp/app/usecase/profile`
5. `src/main/java/datingapp/app/usecase/social`

Each use-case:
1. Accepts explicit input record(s).
2. Returns explicit output/result record(s).
3. Depends on domain services and storage via constructor injection.
4. Contains channel-independent orchestration only.

Adapters (CLI/UI/API):
1. Parse input.
2. Call use-case.
3. Map output to text/properties/navigation.
4. Contain no branching business policy beyond presentation concerns.

## 4. Scope

In scope:
1. Add use-case classes and DTO contracts.
2. Move orchestration out of handlers/viewmodels.
3. Rewire composition root to provide use-cases.
4. Add tests for use-case behavior parity.
5. Delete dead orchestration code after migration.

Out of scope for this plan:
1. Replacing ServiceRegistry pattern globally (Decision 2 follow-up).
2. Full unified app-wide error model migration (Decision 14 follow-up).
3. Storage transactional boundary redesign (Decision 4 follow-up).

## 5. Step-by-Step Implementation Plan

## Phase 0: Baseline and Characterization (No functional changes)

1. Create a flow inventory doc section in this file with one row per flow:
   - Browse candidates
   - Swipe + match detection
   - Undo swipe
   - View matches/details
   - Send message
   - Load conversation
   - Friend request accept/decline
   - Block/unmatch/report
   - Profile save/activation checks
2. For each flow, capture:
   - Current CLI method entry point
   - Current ViewModel method entry point
   - Current domain/storage dependencies
   - Expected output and failure behavior
3. Add characterization tests before moving code:
   - Handler/viewmodel tests that lock current behavior where acceptable.
   - Cross-channel parity tests for shared flows (same inputs, same outcomes).

Exit gate:
1. Existing `mvn test` passes.
2. Baseline behavior is documented and covered for major flows.

## Phase 1: Define Application Contracts

1. Add package `app/usecase/common` with:
   - `UserContext` record (current user id and optional runtime metadata).
   - `UseCaseResult<T>` (or equivalent pattern compatible with current style).
   - Typed application errors for top-level mapping (validation, state, not-found, forbidden, infra).
2. Create feature-level input/output records:
   - Matching: `BrowseCandidatesCommand`, `ProcessSwipeCommand`, `BrowseCandidatesResult`, `ProcessSwipeResult`.
   - Messaging: `ListConversationsQuery`, `OpenConversationCommand`, `SendMessageCommand`, related results.
   - Profile: `SaveProfileCommand`, `UpdatePreferencesCommand`, `ProfileSaveResult`.
   - Social: `HandleFriendRequestCommand`, `TransitionRelationshipCommand`, `NotificationsQuery`.
3. Keep DTOs semantic and channel-neutral:
   - No CLI strings.
   - No JavaFX properties.
   - No UI-specific flags except semantic status fields.
4. Define mapping rules for adapters:
   - CLI maps errors to friendly text.
   - UI maps errors to `ViewModelErrorSink`.

Exit gate:
1. All contracts compile.
2. No adapter imports inside use-case packages.

## Phase 2: Implement First Vertical Slice (Messaging)

Why first: Messaging is high-value and bounded (`MessagingHandler` + `ChatViewModel`).

1. Add `MessagingUseCases` in `app/usecase/messaging`:
   - List conversations.
   - Open/get conversation.
   - Load messages.
   - Send message with match-state validation.
   - Mark as read.
2. Move orchestration currently split between:
   - `MessagingHandler.showConversations()`, `showConversation()`, `sendConversationMessage()`.
   - `ChatViewModel.refreshConversations()`, `loadMessages()`, `sendMessage()`.
3. Update adapters to call use-cases only:
   - Keep prompt rendering and JavaFX property updates in adapter.
   - Remove direct business branching where use-case now handles it.
4. Add dedicated tests:
   - `MessagingUseCasesTest` with `TestStorages`.
   - Parity tests verifying CLI and ViewModel consume same use-case results.

Exit gate:
1. Messaging behavior remains functionally equivalent.
2. CLI and UI both use the same messaging orchestration path.

## Phase 3: Implement Matching + Social Use-Cases

1. Add `MatchingUseCases`:
   - Browse candidates.
   - Process swipe.
   - Undo swipe.
   - Get standouts.
   - Browse pending likers.
2. Add `SocialUseCases`:
   - Pending friend requests query.
   - Accept/decline friend request command.
   - Notifications list + mark-read behavior.
   - Transition commands for unmatch/block/graceful exit/friend-zone.
3. Move orchestration from:
   - `MatchingHandler` and `MatchingViewModel`.
   - `SafetyHandler`, `StatsHandler` (where social/notification behavior overlaps).
   - `SocialViewModel`, `StandoutsViewModel`, `MatchesViewModel` (where relevant).
4. Preserve existing business-rule sources:
   - Keep domain state guards in core services/entities.
   - Use-case coordinates these calls and normalizes outcomes for adapters.

Exit gate:
1. Matching and social flows are no longer orchestrated separately in CLI and UI.
2. Existing tests pass and new use-case tests cover success/failure paths.

## Phase 4: Implement Profile Use-Cases

1. Add `ProfileUseCases`:
   - Save profile fields.
   - Update preferences/dealbreakers.
   - Profile activation readiness check and activation transition.
2. Move orchestration from:
   - `ProfileHandler`.
   - `ProfileViewModel`.
   - `PreferencesViewModel` where business flow exists.
3. Consolidate repeated activation and validation flow into a single use-case path.
4. Ensure adapters only handle:
   - Input collection/parsing.
   - Output display/binding.
   - Navigation concerns.

Exit gate:
1. Profile behavior shared by CLI and UI from one orchestration layer.
2. No direct storage orchestration from profile adapters.

## Phase 5: Composition Root and Dependency Wiring

1. Update `ApplicationStartup` to instantiate use-case bundles.
2. Update `ServiceRegistry` (or create an adjacent app-layer registry) to expose use-case facades.
3. Update factories/wiring points:
   - `Main.java` handler construction.
   - `ViewModelFactory` construction.
   - `RestApiServer` wiring where applicable.
4. Keep wiring explicit and testable:
   - No static mutable references inside use-cases.
   - Constructor-injected dependencies only.

Exit gate:
1. CLI/UI/API resolve dependencies without reaching directly into raw service graph for flow orchestration.

## Phase 6: Adapter Slim-Down and Dead Code Removal

1. Remove now-dead helper methods from handlers/viewmodels.
2. Remove duplicate control-flow branches now owned by use-cases.
3. Reduce constructor fan-in in adapters by depending on feature use-case bundles.
4. Keep adapter classes focused on channel concerns only.

Exit gate:
1. Adapter LOC and dependency counts are materially reduced.
2. No dead orchestration helpers remain.

## Phase 7: Verification, Regression, and Sign-Off

1. Run quality gates in this order:
   - `mvn spotless:apply`
   - `mvn test`
   - `mvn verify`
2. Execute targeted smoke tests:
   - CLI: browse candidates, swipe, send message, friend request handling.
   - UI: matching screen, chat screen, profile save, social actions.
3. Add architecture checks (lightweight):
   - Disallow `core.storage.*` imports in viewmodels (already expected by AGENTS guidance).
   - Flag direct business orchestration patterns in handlers/viewmodels where practical.
4. Record migration completion in retrospective docs.

Exit gate:
1. Build, tests, and quality checks pass.
2. All targeted flows route through use-case layer.
3. Behavior parity validated between CLI and UI.

## 6. Test Plan

Required new tests:
1. `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
2. `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
3. `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
4. `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
5. Cross-channel parity tests (CLI and ViewModel consume same use-case outputs for same fixtures).

Regression test priorities:
1. Match creation and transition edge-cases.
2. Message send validation across match states.
3. Profile activation rules and dealbreaker persistence.
4. Notification mark-read and friend request transitions.

## 7. Risks and Mitigations

Risk 1: Behavior drift during extraction.
Mitigation:
1. Characterization tests before refactor.
2. Vertical-slice migration (Messaging first), then expand.

Risk 2: Overly broad first pass causing long unstable branch.
Mitigation:
1. Deliver in small slices with compile-ready checkpoints after each phase.
2. Keep old and new paths side-by-side only briefly, then remove dead path.

Risk 3: Adapter breakage due to DTO mismatch.
Mitigation:
1. Keep DTOs small and semantic.
2. Add explicit mapper methods in adapters with unit tests.

Risk 4: Dependency explosion in use-cases.
Mitigation:
1. Feature-level use-case bundles/facades.
2. Constructor injection by feature, not whole registry pass-through.

## 8. Definition of Done

This finding is complete only when all are true:
1. Major business flows are implemented in `app/usecase/*`.
2. CLI handlers and JavaFX viewmodels no longer duplicate orchestration logic.
3. Adapter classes are input-output translators, not policy engines.
4. Tests cover each migrated use-case with both success and failure paths.
5. `mvn verify` passes.
6. Retrospective notes updated with before/after status and follow-up work.

## 9. Recommended Execution Order (Practical)

1. Messaging slice.
2. Matching + social slice.
3. Profile slice.
4. Final cleanup + architecture checks.

This order minimizes risk while proving the pattern on a self-contained flow first.

## 10. Implementation Completion Summary (Executed)

Implemented artifacts:
1. Added new application layer packages:
   - `src/main/java/datingapp/app/usecase/common/*`
   - `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
   - `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
   - `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
   - `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
2. Rewired composition root and consumers:
   - `ServiceRegistry` now constructs and exposes feature use-case bundles.
   - CLI adapters migrated to use-case calls: `MessagingHandler`, `MatchingHandler`, `ProfileHandler`, `SafetyHandler`, `StatsHandler`.
   - JavaFX viewmodels migrated (directly or with compatible fallback constructors):
     `ChatViewModel`, `MatchingViewModel`, `ProfileViewModel`, `PreferencesViewModel`,
     `SocialViewModel`, `StatsViewModel`, `MatchesViewModel`, `StandoutsViewModel`.
   - `ViewModelFactory` now injects use-case bundles where supported.
   - REST endpoints for matching/messaging key actions now use use-case calls.
3. Added required use-case tests:
   - `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
   - `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
   - `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
   - `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

Verification run (2026-02-28):
1. `mvn -DskipTests compile` ✅
2. `mvn test` ✅
3. `mvn spotless:apply verify` ✅

Definition-of-done status:
1. Major business flows implemented in `app/usecase/*` ✅
2. CLI and JavaFX now consume shared use-case orchestration for target flows ✅
3. Use-case success/failure tests added and passing ✅
4. Full quality gate (`verify`) passing ✅

