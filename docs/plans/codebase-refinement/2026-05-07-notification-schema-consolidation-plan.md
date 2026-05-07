# Notification Schema and Projection Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give notifications one dedicated roof, one canonical payload-building path, and one stable projection contract without exploding the number of files or changing user-facing notification behavior.

**Architecture:** Move notification model ownership out of `ConnectionModels`, centralize notification payload key construction, and make `NotificationEventHandler` the translator from app events to notification records rather than a place where payload conventions are invented ad hoc. Keep the raw `Map<String, String>` storage shape for now if typed payload classes would add more clutter than clarity.

**Tech Stack:** Java 25, Maven, JUnit 5, existing event bus, existing communication storage, REST and JavaFX notification consumers.

---

## Progress Tracking
- As you finish each step, mark it `✅ IMPLEMENTED`.
- When the plan is fully implemented end-to-end, add `✅ IMPLEMENTED` immediately below the title at the top of this file.

## Decision Check

- The current pain comes from ownership drift: notification model, event projection, DTO mapping, and read APIs all live under different roofs.
- This plan should centralize notification rules without creating one file per notification type.
- Keep the stored payload shape map-based unless a typed representation can be introduced without increasing overall mess.
- Do not split `CommunicationStorage` yet unless the interface extraction becomes trivial after the model move. Storage-interface splitting is not required for this plan to succeed.
- If the relationship and safety split plan has already landed, build on `NotificationUseCases`. If not, keep the temporary delegation minimal and remove it before the plan ends.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/core/connection/ConnectionModels.java`
- `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- `src/main/java/datingapp/app/api/NotificationDtos.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- `src/main/java/datingapp/ui/screen/SocialController.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/core/storage/OperationalCommunicationStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`

**New production files expected**
- `src/main/java/datingapp/core/notification/Notification.java`
- `src/main/java/datingapp/core/notification/NotificationPayloadKeys.java`

**Tests to pin behavior**
- `src/test/java/datingapp/app/event/handlers/NotificationEventHandlerTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/ui/viewmodel/SocialViewModelTest.java`
- `src/test/java/datingapp/ui/screen/SocialControllerTest.java`

## Task 1: Freeze Notification Behavior and Payload Shape

**Files:**
- Test: all tests listed above

- [ ] Step 1: Read `NotificationEventHandler`, `NotificationDtos`, the notification routes in `RestApiServer`, and every UI consumer that reads or marks notifications.
- [ ] Step 2: Add or tighten tests that lock down notification type names, payload keys, read and unread behavior, and route DTO shape.
- [ ] Step 3: Make sure the tests cover at least match creation, message sent, friend-request accepted, and account-deletion cleanup paths.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=NotificationEventHandlerTest,RestApiDtosTest,RestApiReadRoutesTest,SocialViewModelTest,SocialControllerTest
```

Expected:
- Notification type and payload behavior is pinned before the model move starts.

## Task 2: Move Notification Model Ownership to a Dedicated Roof

**Files:**
- Create: `src/main/java/datingapp/core/notification/Notification.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: all production files that currently import `ConnectionModels.Notification`

- [ ] Step 1: Extract `ConnectionModels.Notification` into `core/notification/Notification.java`, keeping its public API stable as much as possible.
- [ ] Step 2: Update every importer in storage, event handlers, DTOs, UI adapters, and routes to use the new notification type.
- [ ] Step 3: Remove the notification type from `ConnectionModels` once all imports are updated.

Guardrail:
- Keep `FriendRequest` in the relationship roof. Do not move friend-request state into the notification package just because the files are adjacent today.

## Task 3: Centralize Notification Payload Keys and Builders

**Files:**
- Create: `src/main/java/datingapp/core/notification/NotificationPayloadKeys.java`
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`

- [ ] Step 1: Replace repeated string constants and ad hoc `Map<String, String>` assembly in `NotificationEventHandler` with one shared payload-key owner.
- [ ] Step 2: Add one helper path for common payload contexts such as pair ID, conversation ID, and request ID.
- [ ] Step 3: Keep the persisted payload structure unchanged unless the tests prove the new representation is equivalent.

Target shape:
```java
public final class NotificationPayloadKeys {
    public static final String MATCH_ID = "matchId";
    public static final String CONVERSATION_ID = "conversationId";
    ...
}
```

## Task 4: Narrow NotificationEventHandler to Translation Only

**Files:**
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`

- [ ] Step 1: Keep `NotificationEventHandler` focused on translating app events into notification records.
- [ ] Step 2: Remove payload-key invention and repeated map-shaping from the handler now that a central payload-key owner exists.
- [ ] Step 3: Keep the registration and event coverage stable so `StorageFactory` still wires the same handler semantics.

Stop condition:
- Do not split the handler into many tiny per-event files unless the single handler becomes genuinely unreadable. The goal is one coherent notification projection roof, not more file churn.

## Task 5: Keep Application and Adapter Layers Thin

**Files:**
- Modify: `src/main/java/datingapp/app/api/NotificationDtos.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/screen/SocialController.java`

- [ ] Step 1: Repoint DTOs and UI adapters to the dedicated notification model type.
- [ ] Step 2: Make sure routes and view models treat notification data as already-shaped domain output rather than reconstructing payload details.
- [ ] Step 3: If `NotificationUseCases` already exists from the relationship and safety split, route all app-layer notification reads and marks through it. If not, introduce only the minimum delegation needed and delete the old path before finishing.

## Task 6: Verify the Notification Contract End to End

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Re-run the focused notification suite.

Run:
```powershell
mvn --% test -Dtest=NotificationEventHandlerTest,RestApiDtosTest,RestApiReadRoutesTest,SocialViewModelTest,SocialControllerTest
```

Expected:
- Same notification types, same payload keys, same read behavior.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No event, DTO, route, or UI regressions.

## Exit Criteria

- Notification model ownership no longer lives inside `ConnectionModels`.
- Payload keys are centralized.
- `NotificationEventHandler` translates events instead of inventing payload conventions ad hoc.
- Notification routes and UI consumers are thinner and more consistent.
- The plan simplifies notification ownership without spawning a large new hierarchy.