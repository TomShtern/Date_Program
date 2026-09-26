# Plan 06: ViewModel Seams and Async-Policy Standardization

> **Date:** 2026-04-10
> **Wave:** 3
> **Priority:** Medium-High
> **Parallel-safe with:** none recommended
> **Status:** Planned

---

## Objective

Standardize how ViewModels are constructed and how they choose async behavior so UI code stops depending on nested implementation details or runtime probing.

## Issues addressed

| Issue ID | Summary                                                                   |
|----------|---------------------------------------------------------------------------|
| 5.2      | `MatchingViewModel` depends on `CandidateFinder.GeoUtils.distanceKm`      |
| 8.1      | ViewModel construction patterns are inconsistent                          |
| 10.2     | `MatchesViewModel` decides async behavior by probing JavaFX runtime state |
| 14.3     | `GeoUtils` is nested even though it behaves like an independent utility   |

## Primary source files and seams

- `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/core/matching/CandidateFinder.java`
- `src/main/java/datingapp/core/profile/LocationService.java`

## Primary verification slice

- `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelArchitectureConsistencyTest.java`
- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`

## Execution slices

### Slice A — choose the stable UI seam for distance/geo behavior

- remove direct UI dependence on nested matching implementation shapes
- either route through `LocationService` or introduce a narrow distance-calculator seam

### Slice B — standardize construction patterns

- define where `Dependencies` records are preferred versus where tiny convenience constructors remain acceptable
- make `ViewModelFactory` the canonical production path

### Slice C — replace runtime probing with explicit policy

- move `MatchesViewModel` async mode decisions to an injected or explicit policy seam
- make tests deterministic regardless of JavaFX runtime initialization state

### Slice D — decide the fate of `GeoUtils`

- promote it to a top-level matching utility only if the UI cannot be safely decoupled through a narrower seam
- otherwise remove the UI’s direct coupling without unnecessary matching-layer churn

## Dependencies and orchestration notes

- Run this plan after P05 if chat-related factory or async-policy conventions are still changing.
- Treat `ViewModelFactory.java` as the single hot-file owner here.
- If this plan changes public factory construction rules, re-check P05 and any UI-adjacent plans before execution.

## Out of scope

- chat-specific async/error semantics (P05)
- controller lifecycle and navigation seams (P07)
- deferred broad core-import cleanup (P13)