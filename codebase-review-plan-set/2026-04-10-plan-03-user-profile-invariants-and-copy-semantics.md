# Plan 03: User/Profile Invariants and Copy Semantics

> **Date:** 2026-04-10
> **Wave:** 1
> **Priority:** High
> **Parallel-safe with:** P01, P05
> **Must run before:** P04, P11
> **Status:** Planned

---

## Objective

Make `User` and the profile mutation path preserve one consistent set of invariants so copy behavior, mutation behavior, verification history, and profile-related use-case semantics stop drifting.

## Issues addressed

| Issue ID | Summary                                                                   |
|----------|---------------------------------------------------------------------------|
| 7.1      | `User.java` carries too many roles                                        |
| 7.3      | `User.StorageBuilder` omits `dealbreakers`                                |
| 11.2     | `ProfileDraftAssembler` manually copies too many fields                   |
| 11.3     | `ProfileUseCases.getOrComputeStats()` masks errors with fallback behavior |
| 11.5     | `User.copy()` can lose location state                                     |
| 13.3     | `User` mutator contracts are looser than supported flows                  |
| 16.3     | `deleteAccount()` side effects are under-documented                       |
| 17.9     | `User.markVerified()` drops part of the verification timeline             |

## Primary source files and seams

- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileDraftAssembler.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileInsightsUseCases.java`

## Primary verification slice

- `src/test/java/datingapp/core/UserTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileMutationUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileNormalizationSupportTest.java`
- `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`

## Execution slices

### Slice A — define the canonical `User` invariant surface

- decide which rules belong inside the model and which remain in collaborators
- make direct mutators either enforce the supported invariants or clearly signal that they are low-level hooks

### Slice B — repair builder and copy completeness

- add missing builder coverage such as `dealbreakers`
- make `User.copy()` preserve raw location/verification state instead of reinterpreting it
- replace broad manual field copying with a canonical copy/builder path

### Slice C — clarify profile use-case failure and side-effect contracts

- make `getOrComputeStats()` failure behavior explicit instead of silently falling back
- document `deleteAccount()` mutation-after-persist behavior in the public contract

### Slice D — preserve timeline state

- keep both verification send and verification completion history if the current model requires both
- verify that account deletion and verification flows still remain deterministic in tests

## Dependencies and orchestration notes

- Run this plan before P04 so verification/social workflow work inherits the final `User` semantics.
- Run this plan before P11 because the CLI cleanup removes wrapper indirection around `copy()` semantics.
- Treat `User.java` as a single-owner file during execution.

## Out of scope

- chat-specific state handling (P05)
- social workflow constructor-mode cleanup (P04)
- large-file decomposition of `User.java` beyond seam-local fixes (P13)