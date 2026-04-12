# Plan 04B: Relationship, Social, and Messaging Workflow Contracts

> **Date:** 2026-04-10
> **Wave:** 1
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Must run after:** P03, P04
> **Status:** Planned

---

## Objective

Clarify the workflow contracts that sit between relationship transitions, social use-case construction, and messaging success semantics so outer adapters inherit one explicit application contract instead of a mix of strings, overload shapes, and implicit event rules.

## Issues addressed

| Issue ID | Summary                                                                                     |
|----------|---------------------------------------------------------------------------------------------|
| 9.2      | `ConnectionService` maps workflow denial reasons through strings                            |
| 13.1     | `SocialUseCases` exposes too many constructor modes                                         |
| 17.1     | `MessagingUseCases.sendMessage()` treats event publication as best-effort after persistence |

## Primary source files and seams

- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`

### Supporting read-only seams

- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`

### Escalate instead of expanding scope if

- the change starts requiring `ChatViewModel.java` edits beyond black-box verification
- the change starts redefining REST transport/result mapping instead of application workflow semantics
- the change requires changing `User` invariants instead of consuming them

## Primary verification slice

- `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`
- `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`

## Execution slices

### Slice A — type workflow semantics

- replace string denial-reason mapping with typed reasons where the seam supports it
- keep the type surface close to the workflow/policy layer that actually owns those reasons

### Slice B — make construction modes explicit

- clarify or replace compatibility overloads in `SocialUseCases` so supported modes are obvious
- keep the full constructor as the canonical runtime path unless source proves otherwise

### Slice C — make messaging success semantics explicit

- preserve and document the persistence-first rule intentionally: the message/conversation persistence path in `ConnectionService` and `MessagingUseCases.sendMessage(...)` remains the success boundary, while `AppEvent.MessageSent` or similar event publication stays best-effort afterward
- ensure callers do not mistake event publication as the success boundary, and do not add rollback or compensation requirements unless a later plan explicitly adopts an atomic publish/outbox contract for a critical workflow with justified consistency-over-latency tradeoffs

## Dependencies and orchestration notes

- Run after P04 so verification ownership is already settled.
- Run before P05 and P08 so chat/UI and REST work consume a stable workflow contract.
- Treat these three files as a single workflow lane; do not split them across parallel edit agents.

## Out of scope

- safety/verification bypass cleanup (P04)
- chat ViewModel async and failure UX behavior (P05)
- REST-side transport/result mapping (P08)
