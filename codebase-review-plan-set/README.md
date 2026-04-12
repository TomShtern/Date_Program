# Codebase Review Plan Set

> **Created:** 2026-04-10
> **Source issue bank:** `CODEBASE_REVIEW.md`
> **Purpose:** Turn the single review bank into execution-safe, seam-based sub-plans for future AI-agent implementation.

---

## What this folder is

This folder is the approved split of the repository-wide issue bank in `CODEBASE_REVIEW.md`.

The guiding rule is simple:

- **Do not execute the review by category labels** like readability, performance, or testability.
- **Execute it by seam** — one plan per shared contract, subsystem, or ownership boundary.

That keeps related changes together, prevents issue drift, and makes AI-agent handoff safer.

## What is inside

- `README.md` — entry point and catalog
- `FIRST_EXECUTION_RECOMMENDATION.md` — recommended first plan, wait order, and first-wave verification matrix
- `EXECUTION_RED_FLAGS_CHECKLIST.md` — stop/go checklist for detecting plan drift before execution goes off the rails
- `EXECUTION_ORCHESTRATION.md` — wave order, concurrency rules, stop/go gates, and agent workflow
- `ISSUE_MANIFEST.md` — exhaustive map of every review issue to exactly one destination
- the numbered and lettered executable workstream plans — active implementation lanes
- `2026-04-10-plan-13-deferred-backlog-and-not-now-items.md` — explicit holding plan for deferred, future-gap, and intentional-preserve items

## Coverage summary

- **Total review issues covered:** 74
- **Executable workstreams:** 14
- **Executable issues:** 68
- **Deferred / not-now issues:** 6

Nothing from `CODEBASE_REVIEW.md` is left unassigned.

## Scope note

This plan set is intentionally scoped to the issue bank in `CODEBASE_REVIEW.md`.

The later hotspot review in `2026-04-08-source-only-code-simplification-review.md` identifies adjacent seams that are **not** independently owned here, including `JdbiAccountCleanupStorage`, `DashboardUseCases`, and broader bootstrap/runtime simplification. If execution of one of these plans starts spilling into those non-owned seams, the coordinator should stop and open a follow-on plan instead of silently widening scope.

## How to use this plan set

1. Start with `EXECUTION_ORCHESTRATION.md`.
2. Use `ISSUE_MANIFEST.md` to confirm issue ownership before opening a plan.
3. Execute plans by **wave order**, not by filename alone.
4. If two plans touch the same hot file, they are **not** parallel-safe even if they live in different waves.
5. Update the manifest and the specific plan file whenever execution reveals a changed dependency or scope boundary.

## Plan catalog

| Plan | File                                                                            | Wave | Issues | Short purpose                                                              |
|------|---------------------------------------------------------------------------------|-----:|-------:|----------------------------------------------------------------------------|
| P01  | `2026-04-10-plan-01-matching-scoring-and-recommendation-contracts.md`           |    1 |      7 | Unify scoring, result-shape, and recommendation contract behavior          |
| P02  | `2026-04-10-plan-02-swipe-concurrency-and-side-effect-sequencing.md`            |    1 |      5 | Normalize swipe critical-path concurrency and side-effect order            |
| P03  | `2026-04-10-plan-03-user-profile-invariants-and-copy-semantics.md`              |    1 |      8 | Stabilize `User` invariants, copy behavior, and profile mutation contracts |
| P04  | `2026-04-10-plan-04-safety-and-verification-boundary-contracts.md`              |    1 |      2 | Lock down the verification boundary and remove safety-layer bypass paths   |
| P04B | `2026-04-10-plan-04b-relationship-social-and-messaging-workflow-contracts.md`   |    1 |      3 | Clarify relationship, social, and messaging workflow semantics             |
| P05  | `2026-04-10-plan-05-chat-viewmodel-async-and-state-contracts.md`                |    3 |      8 | Fix `ChatViewModel` async, state visibility, and failure UX contracts      |
| P06  | `2026-04-10-plan-06-viewmodel-seams-and-async-policy-standardization.md`        |    3 |      4 | Standardize ViewModel construction and async-policy seams                  |
| P07  | `2026-04-10-plan-07-controllers-navigation-theming-and-ui-micro-performance.md` |    3 |      4 | Clean up controller/navigation ownership and UI presentation hot spots     |
| P08  | `2026-04-10-plan-08-rest-boundary-dtos-and-request-guard-contracts.md`          |    4 |      6 | Refine REST route, DTO, parsing, and request-guard boundaries              |
| P09  | `2026-04-10-plan-09-database-runtime-lifecycle-and-configuration-contracts.md`  |    2 |      5 | Clarify runtime database lifecycle, configuration, and singleton behavior  |
| P09B | `2026-04-10-plan-09b-migration-and-schema-safety.md`                            |    2 |      4 | Isolate migration-engine, schema, and bootstrap safety work                |
| P10  | `2026-04-10-plan-10-jdbi-storage-plumbing-serialization-and-batching.md`        |    2 |      4 | Consolidate JDBI-side dialect, JSON, batching, and codec behavior          |
| P11  | `2026-04-10-plan-11-cli-handler-seams-and-shared-helpers.md`                    |    4 |      4 | Extract shared CLI parsing/formatting helpers and trim handler indirection |
| P12  | `2026-04-10-plan-12-service-construction-and-contract-clarity.md`               |    2 |      4 | Clean up builder/constructor mode clarity and contract messaging           |
| P13  | `2026-04-10-plan-13-deferred-backlog-and-not-now-items.md`                      |    5 |      6 | Hold all intentionally deferred or future-only items with revisit triggers |

## Design decisions behind the split

### Why seam-based instead of category-based

`CODEBASE_REVIEW.md` groups findings by review quality dimensions, but the implementation work is not organized that way.

For example:

- matching issues span performance, contracts, concurrency, and documentation
- chat issues span async behavior, error handling, mutability, and polling efficiency
- runtime storage issues span lifecycle, migration safety, codec behavior, and JDBI plumbing

If the plan set followed the review categories directly, the same source files would end up owned by multiple plans.

### Why the deferred plan exists

Some issues are real but should **not** become active implementation workstreams yet.

Examples include:

- explicitly deferred structural refactors
- future-gap-only notes
- intentional compatibility behavior that should be preserved until adjacent seams settle
- storage guardrails that only matter for future adapters

Those items still need a home, but not an execution lane right now.

### Why the tightening pass added subplans

The first seam-based draft still had two serious collision risks:

- safety/verification work was bundled together with relationship and messaging workflow semantics
- runtime lifecycle/configuration work was bundled together with migration/schema safety

Those are now split into `P04` + `P04B` and `P09` + `P09B`, which makes future implementation less likely to get stranded in a hot shared file.

## Recommended first reads for a future execution agent

1. `EXECUTION_ORCHESTRATION.md`
2. `FIRST_EXECUTION_RECOMMENDATION.md`
3. `EXECUTION_RED_FLAGS_CHECKLIST.md`
4. the target plan file
5. `ISSUE_MANIFEST.md`
6. the owning source files named in that plan
7. the referenced test files for that plan’s verification slice

## Completion rule for future execution

A future execution agent should not claim a plan complete until it has:

- validated touched files for errors
- run the plan’s targeted verification slice
- updated the manifest if scope changed
- recorded any new cross-plan dependency discovered during execution

For runtime or PostgreSQL-impacting plans, use the repository’s canonical verification path described in `EXECUTION_ORCHESTRATION.md`.
