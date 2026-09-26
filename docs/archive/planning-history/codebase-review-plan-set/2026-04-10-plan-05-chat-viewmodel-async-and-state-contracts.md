# Plan 05: Chat ViewModel Async and State Contracts

> **Date:** 2026-04-10
> **Wave:** 3
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Status:** Planned

---

## Objective

Make `ChatViewModel` represent one consistent async, session, and failure-visibility contract so chat state does not become stale, silently fail, or expose writable internals.

## Issues addressed

| Issue ID | Summary                                                                |
|----------|------------------------------------------------------------------------|
| 5.3      | `ChatViewModel` exposes mutable internal observable state              |
| 10.4     | `ensureCurrentUser()` caches session state too aggressively            |
| 10.5     | `setCurrentUser(null)` does not reset visible UI state                 |
| 17.2     | `reportSendFailure()` relies on external wiring for visibility         |
| 17.5     | Profile-note save token handling is fragile                            |
| 17.6     | `sendMessage()` boolean return can be mistaken for completion          |
| 17.8     | `loadMessagesInBackground()` can fail without visible UI failure state |
| 18.6     | The polling path duplicates UI and diff work                           |

## Primary source files and seams

- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/ui/screen/ChatController.java`
- `src/main/java/datingapp/core/AppSession.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/ui/screen/ChatController.java`

### Supporting read-only seams

- `src/main/java/datingapp/core/AppSession.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`

### Escalate instead of expanding scope if

- the change requires `MessagingUseCases.java` edits instead of treating messaging as a consumed contract
- the fix starts redefining messaging success semantics instead of representing them clearly in the ViewModel

## Primary verification slice

- `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- `src/test/java/datingapp/ui/screen/ChatControllerTest.java`
- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`

## Execution slices

### Slice A — lock down visible state ownership

- return read-only observable views instead of writable backing lists
- make null-user transitions clear observable state deterministically

### Slice B — align session and lifecycle behavior

- refresh current-user state at explicit lifecycle points
- stop stale session state from surviving screen or account transitions

### Slice C — expose explicit failure and completion semantics

- surface visible error state without relying on incidental wiring
- document or rename the boolean send contract so “accepted for async send” is not confused with completion

### Slice D — fix async race handling

- stabilize note-token ownership across overlapping async operations
- ensure background-load failures become visible and retryable

### Slice E — reduce redundant polling work

- batch UI updates where possible
- compare cheaper summary signals before doing full message-list diff work

## Dependencies and orchestration notes

- Run this plan after P04B so workflow and messaging use-case semantics are already settled.
- If implementation begins editing `MessagingUseCases.java`, stop and amend P04B instead of widening this plan.
- Treat `ChatViewModel.java` as single-owner while this plan is active.

## Out of scope

- broad ViewModel-construction standardization (P06)
- controller/navigation seams outside chat (P07)
- event-publication rule changes at the use-case layer (P04B)