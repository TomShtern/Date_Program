# Recommendation-Ranked Browse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route normal browse results through the recommendation stack so JavaFX, CLI, and REST `/browse` return recommendation-ranked candidates instead of raw `CandidateFinder` distance order.

**Architecture:** Keep `CandidateFinder` as the eligibility/filtering seam, then apply deterministic recommendation ranking inside `RecommendationService` before `MatchingUseCases.browseCandidates(...)` returns the list. Reuse existing compatibility, completion, and activity signals so we do not create a second browse-ranking rules engine.

**Tech Stack:** Java 25, Maven, JUnit 5, existing `RecommendationService`, `CandidateFinder`, `CompatibilityCalculator`, `ProfileService`, `MatchingUseCases`.

---

### Task 1: Add red tests for browse ranking

**Files:**
- Modify: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- Create: `src/test/java/datingapp/core/matching/DefaultBrowseRankingServiceTest.java`

- [ ] Write a failing use-case test proving browse output is recommendation-ranked, not raw candidate order.
- [ ] Write a focused ranking-service test proving a strong farther candidate can outrank a weaker nearer candidate.
- [ ] Run the focused tests and confirm they fail for the expected reason.

### Task 2: Implement browse ranking in the recommendation seam

**Files:**
- Create: `src/main/java/datingapp/core/matching/BrowseRankingService.java`
- Create: `src/main/java/datingapp/core/matching/DefaultBrowseRankingService.java`
- Modify: `src/main/java/datingapp/core/matching/RecommendationService.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`

- [ ] Add a deterministic browse-ranking service contract.
- [ ] Implement default browse ranking using existing compatibility, completion, and activity signals.
- [ ] Expose ranking through `RecommendationService` with a backwards-compatible constructor path.
- [ ] Apply ranking in `MatchingUseCases.browseCandidates(...)` after candidate filtering.
- [ ] Wire the production composition root to build the real ranking service.

### Task 3: Verify the change set

**Files:**
- Verify only: touched production/test files above

- [ ] Run the focused browse ranking tests.
- [ ] Run the broader matching/recommendation regression pack.
- [ ] Run `mvn spotless:apply verify`.
- [ ] Confirm no new IDE/compiler errors remain.

---

## Self-review

- Spec coverage: this plan covers the remaining Phase 1 browse-ranking seam end to end.
- Placeholder scan: removed vague “improve ranking” language in favor of exact files and responsibilities.
- Consistency check: `CandidateFinder` remains the filter and `RecommendationService` becomes the ranking seam for normal browse.