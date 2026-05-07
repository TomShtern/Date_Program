# Relationship and Safety Workflow Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split relationship and safety workflows out of the `SocialUseCases` umbrella so relationship transitions, moderation actions, and notification reads no longer share one mixed application facade.

**Architecture:** Keep `RelationshipWorkflowPolicy` as a pure policy seam. Keep `ConnectionService` as the relationship and friend-request core, keep `TrustSafetyService` as the moderation core, and introduce focused app-layer facades for relationship and safety. Introduce a thin `NotificationUseCases` only to let `SocialUseCases` disappear; do not do the deeper notification-schema cleanup in this plan.

**Tech Stack:** Java 25, Maven, JUnit 5, existing app/usecase/core layering, Javalin REST, JavaFX view models.

---

## Decision Check

- The current mess is not just naming. `SocialUseCases` mixes three different workflows with different collaborators and failure modes.
- Splitting the app-layer façade is worth doing even if file count increases by one or two, as long as `SocialUseCases` is deleted in the same change set.
- Do not move notification model or payload-schema cleanup into this plan. That belongs to the separate notification plan.
- Do not move relationship rules into controllers or view models. The split must happen at the application boundary.
- Do not turn `RelationshipWorkflowPolicy` into another use-case façade. It stays a pure policy object.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`

**New production files expected**
- `src/main/java/datingapp/app/usecase/relationship/RelationshipUseCases.java`
- `src/main/java/datingapp/app/usecase/safety/SafetyUseCases.java`
- `src/main/java/datingapp/app/usecase/notification/NotificationUseCases.java`

**Read carefully before editing**
- `src/main/java/datingapp/core/workflow/RelationshipWorkflowPolicy.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`

**Tests to pin behavior**
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`
- `src/test/java/datingapp/core/TrustSafetyServiceTest.java`
- `src/test/java/datingapp/core/matching/TrustSafetyServiceAuditTest.java`

## Task 1: Freeze the Current Relationship and Safety Contract

**Files:**
- Test: all tests listed above

- [ ] Step 1: Read `SocialUseCases` and classify every method into one of three buckets: relationship, safety, or notification.
- [ ] Step 2: Add or tighten tests that prove the current behavior of friend-zone requests, unmatch, graceful exit, block, unblock, report, blocked-user queries, friend-request reads, and friend-request responses.
- [ ] Step 3: Make sure at least one wiring test proves `ViewModelFactory` and `RestApiServer` still resolve the same behavior through the current façade before the split starts.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=SocialUseCasesTest,RestApiRelationshipRoutesTest,MatchesViewModelTest,ChatViewModelTest,SafetyViewModelTest,ViewModelFactoryTest,ConnectionServiceTransitionTest,TrustSafetyServiceTest,TrustSafetyServiceAuditTest
```

Expected:
- The current app-layer contract is pinned before any file moves or class splits.

## Task 2: Create Focused Application Facades

**Files:**
- Create: `src/main/java/datingapp/app/usecase/relationship/RelationshipUseCases.java`
- Create: `src/main/java/datingapp/app/usecase/safety/SafetyUseCases.java`
- Create: `src/main/java/datingapp/app/usecase/notification/NotificationUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`

- [ ] Step 1: Move relationship methods into `RelationshipUseCases`: `requestFriendZone`, `gracefulExit`, `unmatch`, `pendingFriendRequests`, and `respondToFriendRequest`.
- [ ] Step 2: Move moderation methods into `SafetyUseCases`: `blockUser`, `listBlockedUsers`, `unblockUser`, and `reportUser`.
- [ ] Step 3: Move notification read methods into a thin `NotificationUseCases`: `notifications`, `markNotificationRead`, and `markAllNotificationsRead`.
- [ ] Step 4: Keep the method signatures, command records, and result types stable wherever possible so the caller migration is mechanical.

Target shape:
```java
public final class RelationshipUseCases { ... }
public final class SafetyUseCases { ... }
public final class NotificationUseCases { ... }
```

Guardrail:
- Do not deepen the internals in this step. The first pass should mostly be extraction and delegation, not logic rewrites.

## Task 3: Rewire Composition Roots and Call Sites

**Files:**
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`

- [ ] Step 1: Add the three focused use-case instances to `ServiceRegistry` and wire them through `ViewModelFactory`.
- [ ] Step 2: Update `RestApiServer` so relationship routes call `RelationshipUseCases`, safety routes call `SafetyUseCases`, and notification routes call `NotificationUseCases`.
- [ ] Step 3: Update `SocialViewModel`, `SafetyViewModel`, `MatchesViewModel`, and `ChatViewModel` to depend on the narrower façade that matches the action they perform.
- [ ] Step 4: Keep the external route and UI behavior unchanged; this plan changes ownership, not product behavior.

## Task 4: Delete the Umbrella Facade

**Files:**
- Delete: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: any production or test file that still imports it

- [ ] Step 1: Remove the last imports and references to `SocialUseCases` from production code.
- [ ] Step 2: Update tests to reference the new focused facades.
- [ ] Step 3: Delete `SocialUseCases.java` only after there are no remaining imports, comments, or registry fields that still point to it.

Stop condition:
- If deleting `SocialUseCases` would force notification-schema changes or payload rewrites, stop and keep the notification logic thin. Schema cleanup is a separate plan.

## Task 5: Verify the Split Without Behavioral Drift

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Re-run the focused suite.

Run:
```powershell
mvn --% test -Dtest=SocialUseCasesTest,RestApiRelationshipRoutesTest,MatchesViewModelTest,ChatViewModelTest,SafetyViewModelTest,ViewModelFactoryTest,ConnectionServiceTransitionTest,TrustSafetyServiceTest,TrustSafetyServiceAuditTest
```

Expected:
- Relationship and safety workflows behave the same, but ownership is clearer.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No route, wiring, or regression failures.

## Exit Criteria

- Relationship transitions live behind `RelationshipUseCases`.
- Moderation actions live behind `SafetyUseCases`.
- Notification reads live behind `NotificationUseCases`.
- `SocialUseCases.java` is gone.
- The resulting split is clearer without dragging notification-schema work into the same refactor.