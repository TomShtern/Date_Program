# Matching Recommendation Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate browse and recommendation responsibilities under one canonical recommendation roof, remove shim and wrapper duplication, and keep swipe and write behavior unchanged.

**Architecture:** Keep `RecommendationService` as the single core facade for browse ranking, daily status, daily picks, and standouts. Keep `MatchingUseCases` as the application boundary for orchestration, validation, and event publishing. Keep `MatchingService`, `UndoService`, and `CandidateFinder` as separate focused collaborators; do not merge write-path logic into recommendation code.

**Tech Stack:** Java 25, Maven, JUnit 5, core matching services, `ServiceRegistry`, `StorageFactory`.

---

## Decision Check

- This plan simplifies by deleting wrapper behavior, not by adding a new umbrella layer.
- `RecommendationService` should become the one obvious read and recommendation roof.
- `MatchingUseCases` should stop manufacturing compatibility adapters and should become a straightforward consumer of canonical services.
- `CandidateFinder` stays a shared eligibility filter. Do not fold candidate eligibility into ranking.
- `MatchingService` and `UndoService` stay as write-path services. Do not move swipe persistence, match creation, undo rollback, or transactional limits into `RecommendationService`.
- This plan is independent of the other six plans and can be executed on its own.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/core/matching/RecommendationService.java`
- `src/main/java/datingapp/core/matching/BrowseRankingService.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/storage/StorageFactory.java`

**Read carefully before editing**
- `src/main/java/datingapp/core/matching/CandidateFinder.java`
- `src/main/java/datingapp/core/matching/DailyLimitService.java`
- `src/main/java/datingapp/core/matching/DailyPickService.java`
- `src/main/java/datingapp/core/matching/StandoutService.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/matching/UndoService.java`

**Tests to pin behavior**
- `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`
- `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/storage/StorageFactoryInMemoryTest.java`
- `src/test/java/datingapp/storage/StorageFactorySqlDatabaseTest.java`
- `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

## Task 1: Freeze the Current Browse and Recommendation Contract

**Files:**
- Test: `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`
- Test: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- Test: `src/test/java/datingapp/core/ServiceRegistryTest.java`
- Test: `src/test/java/datingapp/storage/StorageFactoryInMemoryTest.java`
- Test: `src/test/java/datingapp/storage/StorageFactorySqlDatabaseTest.java`

- [ ] Step 1: Read `MatchingUseCases`, `RecommendationService`, `ServiceRegistry`, and `StorageFactory` and write down the current ownership matrix for these operations: browse, daily status, daily pick load, standout load, swipe, undo, and match-quality lookup.
- [ ] Step 2: Add or tighten tests that prove browse ordering, daily-status reads, daily-pick reads, standout reads, and swipe and undo behavior all still work through the current wiring.
- [ ] Step 3: Make sure at least one wiring test proves `ServiceRegistry` and `StorageFactory` build a graph that reaches browse, daily status, and standouts without null fallbacks or no-op adapters.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=RecommendationServiceTest,MatchingUseCasesTest,ServiceRegistryTest,StorageFactoryInMemoryTest,StorageFactorySqlDatabaseTest,MatchingFlowIntegrationTest
```

Expected:
- All targeted tests pass before any refactor.
- Any missing coverage is added before production changes begin.

## Task 2: Make Composition Roots Build the Real Graph Once

**Files:**
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`

- [ ] Step 1: Remove the idea that `MatchingUseCases` is the place where `DailyLimitService`, `DailyPickService`, and `StandoutService` are reconstructed from a wrapper or fed through compatibility shims.
- [ ] Step 2: In each composition root, instantiate the canonical core collaborators once and pass them through directly.
- [ ] Step 3: Keep constructor injection explicit. Do not add another builder, factory, or wrapper layer to replace the old wrapper layer.
- [ ] Step 4: Confirm that the runtime graph has exactly one `RecommendationService` and that it is the same one consumed by browse-related app flows.

Target shape:
```java
RecommendationService recommendationService = new RecommendationService(...);
MatchingUseCases matchingUseCases = new MatchingUseCases(
        candidateFinder,
        matchingService,
        recommendationService,
        matchQualityService,
        undoService,
        ...);
```

## Task 3: Make MatchingUseCases a Consumer, Not a Wrapper Factory

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`

- [ ] Step 1: Remove or inline the compatibility-only helper paths that re-express `RecommendationService` as separate service objects inside `MatchingUseCases`.
- [ ] Step 2: Keep browse-oriented read methods delegated to `RecommendationService` or its canonical collaborators, not to temporary adapter fields.
- [ ] Step 3: Keep write-oriented methods delegated to `MatchingService` and `UndoService`.
- [ ] Step 4: Keep `MatchQualityService` as its own collaborator; do not absorb match-quality logic into recommendation or swipe code.
- [ ] Step 5: Simplify the builder so it fills only real dependencies and no longer manufactures wrapper objects or no-op services behind the caller's back.

Guardrail:
- If a step would make `MatchingUseCases` own more business logic than it owns today, stop and push that logic back down into the canonical core service instead.

## Task 4: Decide Whether BrowseRankingService Should Stay Separate

**Files:**
- Modify: `src/main/java/datingapp/core/matching/RecommendationService.java`
- Modify or delete: `src/main/java/datingapp/core/matching/BrowseRankingService.java`
- Test: `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`

- [ ] Step 1: Check how many remaining non-test callers `BrowseRankingService` has after Tasks 2 and 3.
- [ ] Step 2: If `BrowseRankingService` is now a single-consumer helper and folding it keeps `RecommendationService` readable, inline or nest it as package-private implementation detail.
- [ ] Step 3: If folding it would create a bloated file or blur responsibility, keep the file and stop. Simplification is the goal; fewer files is secondary.
- [ ] Step 4: Update tests so browse ranking behavior is still pinned at the recommendation seam regardless of whether the helper remains separate.

Stop condition:
- Do not inline `BrowseRankingService` if `RecommendationService` starts mixing candidate eligibility, write-path logic, and ranking math in one unreadable file.

## Task 5: Delete Compatibility Surface and Dead Paths

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Test: `src/test/java/datingapp/core/ServiceRegistryTest.java`

- [ ] Step 1: Remove deprecated inner classes, wrapper methods, and compatibility builder behavior once all production callers use the real collaborators.
- [ ] Step 2: Delete any now-unused fields, imports, constructor branches, and comments that only make sense for the compatibility layer.
- [ ] Step 3: Confirm there is no code path left where the app layer can accidentally observe different recommendation collaborators than the core layer is using.

## Task 6: Verify the Simplification End to End

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Run the focused matching and wiring suite again.

Run:
```powershell
mvn --% test -Dtest=RecommendationServiceTest,MatchingUseCasesTest,ServiceRegistryTest,StorageFactoryInMemoryTest,StorageFactorySqlDatabaseTest,MatchingFlowIntegrationTest
```

Expected:
- Same behavior, fewer wrappers, no missing dependency paths.

- [ ] Step 2: Run the repo-wide quality gate before merging.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- Formatting, compilation, tests, and static analysis all pass.

## Exit Criteria

- `RecommendationService` is the single obvious read and recommendation roof.
- `MatchingUseCases` no longer hides wrapper construction or compatibility factories.
- `ServiceRegistry` and `StorageFactory` assemble the same canonical graph directly.
- Browse, daily-status, daily-pick, standout, swipe, undo, and match-quality behavior remain unchanged.
- File count is reduced or unchanged without creating new abstraction layers.