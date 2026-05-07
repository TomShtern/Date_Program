# Messaging and Conversation Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate conversation identity ownership and message-loading helpers so messaging has one clear core roof and fewer leaked transport and UI helper seams.

**Architecture:** Keep `ConnectionService` as the core messaging engine and keep `MessagingUseCases` as the single application-facing façade. Move conversation-ID parsing and participant extraction into the connection layer, and remove `ConversationLoader` if it remains a single-use UI helper that only mirrors `ChatViewModel` behavior.

**Tech Stack:** Java 25, Maven, JUnit 5, connection and messaging services, Javalin REST routes, JavaFX view models.

---

## Decision Check

- The messaging problem is leakage, not the absence of another facade. Do not create a new conversation package or a new high-level coordinator unless the existing roofs cannot hold the behavior.
- `ConnectionService` should own conversation identity semantics.
- `RestApiIdentityPolicy` should consume shared conversation parsing logic, not define it.
- If `ConversationLoader` is only used by `ChatViewModel`, fold it into `ChatViewModel` and delete the file.
- Do not change the current conversation ID format in this plan.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/core/connection/ConnectionModels.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/ConversationLoader.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/MessageDtos.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`

**Tests to pin behavior**
- `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- `src/test/java/datingapp/app/api/RestApiIdentityPolicyTest.java`
- `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`

## Task 1: Freeze the Messaging and Conversation Contract

**Files:**
- Test: all tests listed above

- [ ] Step 1: Read `ConnectionService`, `MessagingUseCases`, `ConversationLoader`, `ChatViewModel`, `RestApiIdentityPolicy`, and the message routes in `RestApiServer`.
- [ ] Step 2: Add or tighten tests for conversation ID parsing, recipient extraction, list conversations, load conversation, send message, archive conversation, delete message, and unread-count behavior.
- [ ] Step 3: Make sure at least one UI test still covers stale-load protection and selection restore in `ChatViewModel`.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=ChatViewModelTest,RestApiIdentityPolicyTest,RestApiPhaseTwoRoutesTest,RestApiDtosTest,ViewModelFactoryTest
```

Expected:
- Current conversation identity and message-loading behavior is pinned before any helper moves.

## Task 2: Move Conversation Identity Parsing into the Connection Roof

**Files:**
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`

- [ ] Step 1: Add a shared helper in the connection layer for parsing a conversation ID into its participants and for extracting the other participant for a given actor.
- [ ] Step 2: Repoint `RestApiIdentityPolicy` and `RestApiServer` to that shared helper.
- [ ] Step 3: Delete the transport-local participant parsing code once the shared helper is authoritative.

Target shape:
```java
public static record ConversationParticipants(UUID firstUserId, UUID secondUserId) { ... }

public static ConversationParticipants parseId(String conversationId) { ... }
```

Guardrail:
- Preserve the normalized two-user conversation ID format exactly. This plan centralizes parsing; it does not redesign IDs.

## Task 3: Keep MessagingUseCases the Single App Façade

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`

- [ ] Step 1: Review `MessagingUseCases` for any logic that duplicates conversation-ID handling or read-path behavior that now belongs in the shared connection helper.
- [ ] Step 2: Keep validation, use-case error mapping, and event publication in `MessagingUseCases`.
- [ ] Step 3: Keep persistence and message-thread retrieval logic in `ConnectionService`.
- [ ] Step 4: Remove any now-unused helper branches or duplicate parsing logic from the app layer.

## Task 4: Fold or Delete the Single-Use Conversation Loader

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Delete: `src/main/java/datingapp/ui/viewmodel/ConversationLoader.java`

- [ ] Step 1: Confirm `ConversationLoader` is still only used by `ChatViewModel`.
- [ ] Step 2: Inline its behavior into `ChatViewModel` as private helper methods or a private nested record if that keeps the code readable.
- [ ] Step 3: Delete `ConversationLoader.java` once `ChatViewModel` fully owns the conversation-list and message-load orchestration.

Stop condition:
- If inlining would make `ChatViewModel` substantially harder to read, keep the helper as a package-private nested helper in the same file rather than reintroducing another top-level file.

## Task 5: Verify the Transport and UI Paths Still Match

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Re-run the focused messaging suite.

Run:
```powershell
mvn --% test -Dtest=ChatViewModelTest,RestApiIdentityPolicyTest,RestApiPhaseTwoRoutesTest,RestApiDtosTest,ViewModelFactoryTest
```

Expected:
- Same route behavior, same conversation parsing, same UI loading behavior.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No REST, UI, or message-flow regressions.

## Exit Criteria

- Conversation identity parsing has one owner in the connection roof.
- `RestApiIdentityPolicy` no longer duplicates pair-ID parsing rules.
- `MessagingUseCases` remains the single application façade.
- `ConversationLoader.java` is gone or fully absorbed into the same file as its only consumer.
- Messaging is simpler without adding another abstraction layer.