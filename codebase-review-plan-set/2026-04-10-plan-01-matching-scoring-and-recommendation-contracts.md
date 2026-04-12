# Plan 01: Matching Scoring and Recommendation Contracts

> **Date:** 2026-04-10
> **Wave:** 1
> **Priority:** High
> **Parallel-safe with:** P03 only
> **Must stay serial with:** P02, P08
> **Status:** Planned

---

## Objective

Stabilize the scoring and recommendation surface so browse ranking, standouts, daily status, and match-quality behavior all speak one coherent contract before concurrency work begins.

## Issues addressed

| Issue ID | Summary                                                                    |
|----------|----------------------------------------------------------------------------|
| 2.1      | Scoring logic is duplicated across matching features                       |
| 10.3     | `Standout.create()` hardcodes current time lookup                          |
| 11.1     | `MatchingUseCases.Builder.recommendationService()` has hidden side effects |
| 12.1     | `MatchingUseCases` fallback contract is contradictory                      |
| 13.4     | `archiveMatch()` behaves like delete                                       |
| 13.5     | `getDailyStatus()` bypasses the builder seam                               |
| 18.4     | `DefaultCompatibilityCalculator` repeats config and age lookups            |

## Primary source files and seams

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`
- `src/main/java/datingapp/core/matching/RecommendationService.java`
- `src/main/java/datingapp/core/matching/DefaultBrowseRankingService.java`
- `src/main/java/datingapp/core/matching/DefaultStandoutService.java`
- `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java`
- `src/main/java/datingapp/core/matching/Standout.java`
- `src/main/java/datingapp/core/model/Match.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/core/matching/RecommendationService.java`
- `src/main/java/datingapp/core/matching/DefaultBrowseRankingService.java`
- `src/main/java/datingapp/core/matching/DefaultStandoutService.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`
- `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java`
- `src/main/java/datingapp/core/matching/Standout.java`

### Supporting read-only seams

- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/model/Match.java`

### Escalate instead of expanding scope if

- the change starts modifying lock scope, undo persistence, or transaction boundaries
- the change requires `InteractionStorage` or `JdbiMatchmakingStorage` edits rather than scoring/recommendation cleanup

## Primary verification slice

- `src/test/java/datingapp/core/MatchingServiceTest.java`
- `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`
- `src/test/java/datingapp/core/DefaultCompatibilityCalculatorTest.java`
- `src/test/java/datingapp/core/StandoutsServiceTest.java`
- `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

## Execution slices

### Slice A — freeze scoring vocabulary and result ownership

- identify the current score inputs, weights, and result-shape entry points
- decide which type owns final aggregation versus leaf calculators
- document the public surface that downstream callers may rely on

### Slice B — unify recommendation aggregation

- remove duplicate aggregation logic from browse, standout, and match-quality flows
- centralize the shared aggregation seam without re-merging already-split leaf services
- keep `RecommendationService` as the façade unless a smaller adapter extraction is enough

### Slice C — normalize `MatchingUseCases` configuration behavior

- make builder precedence explicit
- stop hidden overwrites or implicit fallback surprises
- route `getDailyStatus()` through the authoritative seam used by the configured instance

### Slice D — resolve public semantic mismatches

- decide whether `archiveMatch()` should become true archive behavior or be renamed/de-scoped to delete behavior
- align names, tests, and result expectations to the chosen semantics

### Slice E — tighten deterministic time and hot-path work

- add an explicit timestamp seam for standout creation
- remove repeated config/age lookups inside the compatibility path

## Dependencies and orchestration notes

- Run this plan before P02 so swipe sequencing inherits the final scoring/result contract.
- If this plan changes `MatchingUseCases` public behavior, re-open P08 before executing it.
- Do not mix deferred Recommendation façade cleanup from P13 into this plan unless the façade itself becomes the blocker.

## Out of scope

- lock-scope and concurrency changes in `MatchingService` (P02)
- UI/ViewModel usage of matching utilities (P06)
- deferred compatibility baggage in `RecommendationService` (P13 issue 7.2)