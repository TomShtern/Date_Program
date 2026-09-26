# Execution Orchestration for the Codebase Review Plan Set

> **Purpose:** Define how future AI coding agents should execute the plan set safely, efficiently, and with minimal overlap.

---

## Coordinator model

Use a **single coordinator agent** plus a small number of specialized helpers.

### Coordinator responsibilities

- choose the active plan from this folder
- confirm dependencies from `ISSUE_MANIFEST.md`
- gather the final context for the target seam
- decide whether any read-only helpers are useful
- enforce hot-file ownership boundaries
- review edits, run verification, and decide stop/go

### Helper agent responsibilities

- **read-only helpers** may explore files, tests, call sites, or docs in parallel
- **edit agents** should own one plan at a time and one hot seam at a time
- complex reasoning, boundary decisions, and plan interpretation stay with the coordinator

## Default concurrency policy

### Safe default

- **Maximum read-only helpers in parallel:** 2–3
- **Maximum edit agents in parallel:** 2
- **Maximum edit agents touching hot files:** 1 per hot file or seam

If a plan starts touching files owned by another open plan, the coordinator should stop parallel execution and re-sequence the work.

## Global hot files and single-owner rule

These files should be treated as **single-owner files** during implementation:

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`

If two plans want the same hot file, they must run serially.

## Scope guard against adjacent hotspot drift

This plan set is scoped to `CODEBASE_REVIEW.md`, not to every hotspot identified later by the source-only review.

If execution of one of these plans starts materially expanding into adjacent non-owned seams such as:

- `JdbiAccountCleanupStorage`
- `DashboardUseCases`
- broad `ApplicationStartup` simplification
- broad `JdbiUserStorage` transactional/hydration restructuring

the coordinator should stop and open a follow-on plan rather than silently widening the current one.

## Plan-to-hot-file ownership map

| Plan | Primary hot-file owner(s)                                                 | Supporting-only hot files |
|------|---------------------------------------------------------------------------|---------------------------|
| P01  | `MatchingUseCases.java`                                                   | `MatchingService.java`    |
| P02  | `MatchingService.java`                                                    | `MatchingUseCases.java`   |
| P03  | `User.java`                                                               | none                      |
| P04  | `TrustSafetyService.java`                                                 | none                      |
| P04B | `ConnectionService.java`, `SocialUseCases.java`, `MessagingUseCases.java` | none                      |
| P05  | `ChatViewModel.java`                                                      | none                      |
| P06  | `ViewModelFactory.java`                                                   | none                      |
| P07  | `NavigationService.java`                                                  | none                      |
| P08  | `RestApiServer.java`                                                      | none                      |
| P09  | `DatabaseManager.java`                                                    | `StorageFactory.java`     |
| P09B | `MigrationRunner.java`, `SchemaInitializer.java`                          | `DatabaseManager.java`    |
| P10  | `StorageFactory.java` and the `Jdbi*Storage` cluster                      | none                      |
| P11  | none                                                                      | none                      |
| P12  | `ServiceRegistry.java`                                                    | none                      |

### Wave 1 — core behavior and application workflow contracts

Execute in this order:

- P01 — matching scoring and recommendation contracts
- P02 — swipe concurrency and side-effect sequencing
- P03 — user/profile invariants and copy semantics
- P04 — safety and verification boundary contracts
- P04B — relationship, social, and messaging workflow contracts

**Why this order:** These are the core and app-facing contracts that outer layers depend on. P02 consumes the stabilized matching contract from P01. P04 and P04B consume the final user/profile semantics from P03.

### Wave 2 — runtime, migration, storage, and composition seams

- P09 — database runtime lifecycle and configuration contracts
- P09B — migration and schema safety
- P10 — JDBI storage plumbing, serialization, and batching
- P12 — service construction and contract clarity

**Why this wave exists:** Once behavior contracts are stable, central runtime and persistence seams can be clarified without fighting moving application semantics.

### Wave 3 — UI/ViewModel/controller seams

- P05 — chat ViewModel async and state contracts
- P06 — ViewModel seams and async-policy standardization
- P07 — controllers, navigation, theming, and UI micro-performance

**Why this wave exists:** These plans become much safer after workflow semantics and runtime/storage seams stop moving underneath them.

### Wave 4 — outer adapter layers

- P08 — REST boundary, DTO, and request-guard contracts
- P11 — CLI handler seams and shared helpers

**Why this wave exists:** REST and CLI should mirror the settled interior seams, not define them while the core is still moving.

### Wave 5 — deferred / revisit-only items

- P13 — deferred, future-gap, and intentional-preserve ledger

P13 is not a normal execution plan. It is a holding plan that decides what should be revisited later and under what trigger.

## Recommended parallel pairings

These pairings are the only ones worth considering for future parallel implementation **after** confirming the hot-file map stays clean:

| Pairing   | Why it can work                                                   |
|-----------|-------------------------------------------------------------------|
| P01 + P03 | Matching scoring and `User`/profile invariants are separate seams |

## Pairings that should stay serial

| Sequence                           | Why                                                                                                  |
|------------------------------------|------------------------------------------------------------------------------------------------------|
| P01 -> P02                         | Swipe sequencing depends on stabilized matching result and use-case contract boundaries              |
| P03 -> P04 -> P04B                 | Verification and workflow contracts should inherit the final `User` semantics and safety boundary    |
| P09 -> P09B -> P10                 | Runtime lifecycle should settle before migration safety, and both should settle before JDBI plumbing |
| Wave 1 + Wave 2 -> Wave 3          | UI seams should consume stable workflow and runtime/storage behavior                                 |
| Wave 1 + Wave 2 + Wave 3 -> Wave 4 | REST and CLI should mirror a settled interior                                                        |

## Per-plan execution recipe

For each future plan execution:

1. Read the target plan file completely.
2. Read the owning source files listed in that plan.
3. Read the referenced verification slice test files.
4. Open a plan-local todo list.
5. If helpful, dispatch read-only helpers for usages, callers, or test-surface discovery.
6. Let exactly one edit owner implement the plan.
7. Run targeted verification.
8. If the plan changes a public seam, re-check dependent plans in `ISSUE_MANIFEST.md`.
9. Update the plan file if a scope assumption changed.

## Stop / go gates

Stop and re-baseline if any of these happen:

- the edit spills into another plan’s hot files
- the plan changes a public API promised as stable in the manifest
- new compile errors appear outside the owned seam
- a runtime/storage plan changes PostgreSQL behavior without updated smoke validation
- a UI plan changes async behavior without updated ViewModel tests

## Verification ladder

### Minimum per plan

- check touched files for errors
- run the plan’s targeted tests
- re-read the changed plan-owned region if the implementation drifted

### After each wave

- run a broader smoke slice that covers the wave’s main seams
- update any dependent plan docs if assumptions changed

### For storage/runtime/PostgreSQL-impacting plans

Use the repository’s canonical verification path:

1. start local PostgreSQL if needed
2. run targeted storage/runtime tests
3. use `./run_verify.ps1` for repo-level validation when the change is substantial

Relevant helpers in this repo:

- `start_local_postgres.ps1`
- `run_postgresql_smoke.ps1`
- `stop_local_postgres.ps1`
- `run_verify.ps1`

## Reporting back to the coordinator

Every future execution agent should report:

- what it changed
- which issue IDs it addressed
- what tests it ran
- whether any dependency or ownership boundary changed
- whether the plan file or manifest needs to be updated

## Scope discipline

These plan files are intentionally seam-scoped.

Future execution should avoid:

- opportunistic broad refactors
- “while we’re here” rewrites across adjacent plans
- mixing backlog items from P13 into active plans without coordinator approval
- parallel edits to `MatchingUseCases`, `User`, `ViewModelFactory`, `RestApiServer`, `DatabaseManager`, or `ServiceRegistry`

When in doubt, shrink the active scope and keep the seam clean.
