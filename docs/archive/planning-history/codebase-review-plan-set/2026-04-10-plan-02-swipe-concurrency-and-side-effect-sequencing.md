# Plan 02: Swipe Concurrency and Side-Effect Sequencing

> **Date:** 2026-04-10
> **Wave:** 1
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Must run after:** P01
> **Status:** Planned

---

## Objective

Define one auditable concurrency and side-effect contract for like/pass flows so `processSwipe()`, `recordLike()`, undo, and storage-backed transitions stop drifting across layers.

## Issues addressed

| Issue ID | Summary                                                                   |
|----------|---------------------------------------------------------------------------|
| 2.2      | Swipe eligibility is validated through too many layers                    |
| 11.4     | `recordLike` and `processSwipe` do not share one concurrency contract     |
| 13.2     | `InteractionStorage.saveLikeAndMaybeCreateMatch()` synchronizes on `this` |
| 17.7     | Swipe side effects are inconsistent across related flows                  |
| 18.7     | `processSwipe()` keeps the user lock longer than necessary                |

## Primary source files and seams

- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/storage/InteractionStorage.java`
- `src/main/java/datingapp/core/matching/UndoService.java`
- `src/main/java/datingapp/core/matching/DefaultDailyPickService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/storage/InteractionStorage.java`
- `src/main/java/datingapp/core/matching/UndoService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`

### Supporting read-only seams

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/core/matching/DefaultDailyPickService.java`

### Escalate instead of expanding scope if

- the implementation requires `MatchingUseCases.java` edits beyond black-box verification
- the implementation starts changing scoring/result-shape behavior rather than concurrency/side-effect order

## Primary verification slice

- `src/test/java/datingapp/core/MatchingServiceTest.java`
- `src/test/java/datingapp/core/matching/MatchingTransactionTest.java`
- `src/test/java/datingapp/core/InteractionStorageAtomicityTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

## Execution slices

### Slice A — choose the canonical critical-path rule

- decide which layer owns pre-validation, persisted re-validation, and final side effects
- preserve race-safety while reducing duplicate eligibility checks

### Slice B — align `recordLike()` and `processSwipe()`

- define the primitive operation versus orchestration operation explicitly
- make lock scope, storage calls, and undo behavior line up with that rule

### Slice C — clarify storage-level guarantees

- document the best-effort nature of the interface default path
- ensure production storage implementations remain the source of real transactional isolation

### Slice D — sequence side effects consistently

- normalize when daily-pick state, undo tracking, event publication, and match creation occur
- remove cross-layer ambiguity about what constitutes success

### Slice E — trim lock duration safely

- move only read-mostly checks out of the lock
- keep persisted revalidation and writes inside the critical section

## Dependencies and orchestration notes

- This plan assumes P01 already stabilized matching result shapes and builder behavior.
- Treat `MatchingService.java` as the single hot-file owner here.
- If `MatchingUseCases.java` must change, stop and amend P01 instead of co-owning it here.
- Do not run this plan in parallel with P01 or P08.

## Out of scope

- recommendation scoring duplication (P01)
- UI chat/state handling (P05)
- runtime database lifecycle work (P09)