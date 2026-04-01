# Phase 1A Candidate Truthfulness — Completion Report

> **Execution status:** All tasks completed. Executed by `github_copilot` agent, 2026-03-30. All checkboxes below reflect verified agent execution; the plan is fully implemented and merged into the working branch.

> **For agentic workers:** This plan has been fully executed. All checkbox steps below are checked `[x]` and verified. No further implementation is needed.

**Goal:** Fix the first Phase 1a correctness slice so candidate browsing and swipe acceptance tell the truth about who is actually eligible, and so an `UNMATCH` pair becomes genuinely rematchable again after cooldown expiry, while preserving the existing location-missing empty-state contract across CLI, REST, and JavaFX.

**Architecture:** Put eligibility truth in the core matching pipeline, not in adapters. `CandidateFinder` remains the source of truth for browse/read eligibility and must enforce symmetric block filtering, configurable rematch cooldown, and stale-cache revalidation by rerunning the full eligibility pipeline on fresh user data. `MatchingService` must re-check persisted user state under the write lock before accepting a swipe so stale UI or REST snapshots can never create ghost likes or matches, and the interaction layer must clear old pair-likes on `UNMATCH` while preserving the ended match row as the cooldown anchor so a fresh mutual like can reactivate the pair after cooldown.

**Tech Stack:** Java 25 (preview), Maven, JavaFX 25 regression coverage, JDBI/H2 storages, in-memory `TestStorages`, JUnit 5.

---

## Spec linkage

- Source design: `2026-03-29-dating-app-roadmap-design.md`
- This plan operationalizes only the first recommended implementation batch from Phase 1a:
  - paused users must not remain swipeable/matchable through stale snapshots
  - block filtering must work in both directions during candidate browsing
  - unmatching must create a temporary rematch cooldown
  - existing `locationMissing` behavior must remain explicit and non-regressing

## Scope boundary

### In scope

- Core browse eligibility truth
- Core swipe acceptance truth under lock
- Config-driven rematch cooldown
- Real post-cooldown rematch eligibility for `UNMATCH` pairs
- Regression coverage for CLI/REST/UI adapters that already consume matching flows
- Production composition wiring for the new cooldown setting

### Out of scope for this batch

- Friend-zone cleanup and graceful-exit cleanup semantics
- Undo-expiry feedback UX improvements
- MatchQuality / ActivityMetrics / ProfileCompletion surfacing work
- Unsupported-country heuristics beyond the current `locationMissing` contract
- New tables, schema migrations, or REST route redesign
- Android-facing API redesign

## Design locks for this plan

1. **Core owns truth.** No adapter-specific filtering is allowed for paused, blocked, or cooldowned candidates.
2. **`/browse` and `/candidates` must stay behaviorally aligned.** `/api/users/{id}/candidates` remains the deliberate direct-read exception in `RestApiServer`, but it must still inherit the same filtering truth because it uses `CandidateFinder`.
3. **Swipe acceptance must re-check persisted state under lock.** The `User candidate` object passed into `MatchingService.processSwipe(...)` is advisory only.
4. **Block filtering must be symmetric.** `TrustSafetyStorage.getBlockedUserIds(userId)` is one-way storage data; candidate eligibility must use `TrustSafetyStorage.isBlocked(a, b)` or equivalent symmetric logic.
5. **Cooldown must reuse existing match terminal metadata.** Use `Match.getEndedAt()` and `Match.getEndReason()`; do not add a new cooldown table for this batch.
6. **Cooldown must be config-driven.** No magic constants in `CandidateFinder` or `MatchingService`; runtime behavior must come from injected `AppConfig`.
7. **Do not widen the browse contract in this batch.** Preserve the existing `BrowseCandidatesResult.locationMissing` boolean and the current empty-state behavior unless a regression forces a minimal copy correction.
8. **Blocked users remain permanently excluded; cooldown applies only to unmatches.** Do not silently extend cooldown to block, graceful exit, or friend-zone paths in this batch.
9. **Cooldown expiry must restore real rematchability.** The implementation is incomplete if a candidate reappears after cooldown but stale likes or a terminal pair record still prevent a new match.

## Execution model

- Keep one main coordinating agent on all shared files until the core seams are green.
- Do **not** parallelize edits to these files:
  - `src/main/java/datingapp/core/AppConfig.java`
  - `src/main/java/datingapp/core/AppConfigValidator.java`
  - `src/main/java/datingapp/core/matching/CandidateFinder.java`
  - `src/main/java/datingapp/core/matching/MatchingService.java`
  - `src/main/java/datingapp/storage/StorageFactory.java`
- After Tasks 2-4 are green, leaf regression updates in REST/UI tests can be parallelized cautiously.

## Context map

### Files to modify

| File                                                                                      | Purpose                                     | Expected change                                                                                                                                              |
|-------------------------------------------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/core/AppConfig.java`                                             | Canonical app config record + builder       | Add `matching.rematchCooldownHours`, default value, builder setter, sub-record mapping                                                                       |
| `src/main/java/datingapp/core/AppConfigValidator.java`                                    | Validation for config sections              | Validate the new cooldown field deterministically                                                                                                            |
| `config/app-config.json`                                                                  | Runtime config source in repo               | Add the new flat JSON key with the default cooldown value                                                                                                    |
| `src/main/java/datingapp/core/matching/CandidateFinder.java`                              | Candidate browse/read truth                 | Add symmetric block filtering, recent-unmatch cooldown filtering, and cached-candidate revalidation                                                          |
| `src/main/java/datingapp/core/matching/MatchingService.java`                              | Swipe/match acceptance truth                | Re-check persisted ACTIVE/block state under lock before saving likes or matches                                                                              |
| `src/main/java/datingapp/core/storage/InteractionStorage.java`                            | Atomic pair-like/match persistence contract | Update default rematch semantics for ended `UNMATCHED` pairs without widening the schema                                                                     |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`                        | Real storage implementation of transitions  | Make `unmatchTransition(...)` clear pair likes atomically and let `saveLikeAndMaybeCreateMatch(...)` reactivate an `UNMATCHED` pair after fresh mutual likes |
| `src/main/java/datingapp/storage/StorageFactory.java`                                     | Production composition root                 | Pass config-driven cooldown into `CandidateFinder` construction                                                                                              |
| `src/test/java/datingapp/core/testutil/TestStorages.java`                                 | In-memory transition semantics for tests    | Mirror pair-like cleanup and rematch semantics in the in-memory `Interactions` storage                                                                       |
| `src/test/java/datingapp/core/AppConfigTest.java`                                         | Config defaults + builder mapping coverage  | Add assertions for default, custom, and mapped cooldown values                                                                                               |
| `src/test/java/datingapp/core/AppConfigValidatorTest.java`                                | Config validation coverage                  | Add valid/invalid cooldown validation tests                                                                                                                  |
| `src/test/java/datingapp/app/ConfigLoaderTest.java`                                       | JSON config loader coverage                 | Verify `rematchCooldownHours` is parsed from JSON and defaults correctly                                                                                     |
| `src/test/java/datingapp/core/CandidateFinderTest.java`                                   | Browse filtering regressions                | Add reverse-block, cooldown, and stale-cache tests                                                                                                           |
| `src/test/java/datingapp/core/MatchingServiceTest.java`                                   | Swipe guard regressions                     | Add stale candidate snapshot tests for pause/block before swipe commit                                                                                       |
| `src/test/java/datingapp/core/InteractionStorageAtomicityTest.java`                       | Default interaction persistence contract    | Add rematch semantics coverage for the default in-memory `saveLikeAndMaybeCreateMatch(...)` path                                                             |
| `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java` | Real DB atomic transition coverage          | Add unmatch-like cleanup and ended-pair rematch coverage for the JDBI implementation                                                                         |
| `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`                       | Relationship transition orchestration       | Verify `UNMATCH` clears pair likes as part of the atomic transition                                                                                          |
| `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`                  | App-layer browse/swipe regression coverage  | Add browse/use-case coverage for blocked/cooldowned candidates                                                                                               |
| `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`                      | App-layer unmatch/rematch behavior          | Verify the social-layer unmatch path leaves the pair truly eligible for a future rematch after cooldown                                                      |
| `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`                              | REST browse/direct-read regression coverage | Prove `/browse` and `/candidates` both exclude blocked/cooldowned users                                                                                      |
| `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`                      | REST unmatch/block/report flow coverage     | Verify unmatch clears old pair likes and does not poison future rematchability                                                                               |
| `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`                            | End-to-end app flow coverage                | Add a rematch-after-cooldown happy path proving the slice works across use cases                                                                             |
| `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`                         | JavaFX matching regression coverage         | Keep `locationMissing` behavior green after core changes                                                                                                     |
| `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`                           | JavaFX empty-state copy coverage            | Keep location guidance copy green; only change production UI code if this breaks                                                                             |

### Dependencies that may need coordinated updates

| File                                                                 | Relationship                                                                                            |
|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` | Should not need public contract changes, but its tests must reflect new candidate truth                 |
| `src/main/java/datingapp/app/api/RestApiServer.java`                 | Direct-read `/candidates` route depends on `CandidateFinder`; route contract should stay unchanged      |
| `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`        | Consumes `browseCandidates(...)` and should not regress its location-missing behavior                   |
| `src/main/java/datingapp/ui/screen/MatchingController.java`          | Existing empty-state copy already depends on `locationMissing`; change only if tests prove a regression |
| `src/main/java/datingapp/core/matching/RecommendationService.java`   | Uses `CandidateFinder` through daily-pick/standout flows; candidate-truth changes can ripple here       |
| `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`           | Useful smoke coverage if error/candidate behavior shifts through the CLI                                |

### Reference patterns to preserve

| File                                                                 | Pattern                                                             |
|----------------------------------------------------------------------|---------------------------------------------------------------------|
| `src/main/java/datingapp/core/model/Match.java`                      | Existing `endedAt` / `endReason` terminal metadata                  |
| `src/main/java/datingapp/core/storage/InteractionStorage.java`       | Existing read surface for matches/likes; avoid schema expansion     |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`   | Existing atomic relationship transition and UPSERT pattern          |
| `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` | Keep the app boundary stable while core logic changes beneath it    |
| `src/main/java/datingapp/app/api/RestApiServer.java`                 | Keep `/candidates` as the explicit direct-read exception            |
| `src/test/java/datingapp/core/MatchingServiceTest.java`              | Existing style for lock-time guard regressions                      |
| `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`  | Existing atomic transition assertions to extend rather than replace |
| `src/test/java/datingapp/app/ConfigLoaderTest.java`                  | Existing flat-key config loader coverage pattern                    |
| `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`      | Existing location-missing empty-state assertion pattern             |

### Risk assessment

- [x] Breaking changes to public API — low/medium (`AppConfig` field addition, possible constructor overloads)
- [ ] Database migrations needed — no
- [x] Configuration changes required — yes
- [x] Cross-adapter behavior changes expected — yes, by design
- [x] Hidden stale-cache risk — yes; detached-snapshot tests must be added intentionally

## Alternatives considered

- **ALT-001: Add a new rematch-cooldown table.** Rejected because `Match` already persists `endedAt` and `endReason`, and this batch should avoid schema churn.
- **ALT-002: Filter blocked/cooldowned users only in `MatchingUseCases` or REST.** Rejected because `/candidates` is a direct-read exception and would drift from `/browse`.
- **ALT-003: Disable candidate caching entirely.** Rejected as the first choice because the bug is stale eligibility, not caching itself; however, if truth-preserving full-source revalidation makes the current cache architecture semantically unsafe or valueless, removing the final-list cache in this batch is acceptable.
- **ALT-004: Introduce a richer browse empty-state enum now.** Rejected for this slice because it would widen REST/UI contracts before the core truth bug is fixed.

## Non-goals and deferrals

- Do not attempt to detect unsupported countries or unsupported geographies in this batch.
- Do not widen `TrustSafetyStorage`; if `InteractionStorage` can stay source-compatible, prefer that.
- Do not add a new REST field such as `emptyStateReason` in this batch.
- Do not change friend-zone, graceful-exit, or block cleanup semantics beyond what is already required for candidate exclusion.

## Chunk 1: Lock the behavior before changing production code

### Task 1: Add failing characterization tests for candidate truthfulness

**Execution mode:** main agent only

**Files:**
- Modify: `src/test/java/datingapp/core/AppConfigTest.java`
- Modify: `src/test/java/datingapp/core/AppConfigValidatorTest.java`
- Modify: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Modify: `src/test/java/datingapp/core/CandidateFinderTest.java`
- Modify: `src/test/java/datingapp/core/MatchingServiceTest.java`
- Modify: `src/test/java/datingapp/core/InteractionStorageAtomicityTest.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- Modify: `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`
- Modify: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Modify: `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`
- Review-only for non-regression: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Review-only for non-regression: `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`

- [x] ✅ **Step 1: Add failing config tests for `rematchCooldownHours`**

Add assertions covering all three config entry points:

- `AppConfig.defaults().matching().rematchCooldownHours()` returns `168`
- `AppConfig.builder().rematchCooldownHours(72).build()` stores `72`
- `ApplicationStartup.fromJson("{\"rematchCooldownHours\": 24}")` stores `24`

Also add a validator test that `rematchCooldownHours < 0` is rejected.

- [x] ✅ **Step 2: Run the config-only test pack and confirm it fails for the missing field**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest test`

Expected before implementation: compile or test failure mentioning the missing cooldown field/setter/parser.

- [x] ✅ **Step 3: Add failing `CandidateFinderTest` coverage for the browse bugs**

Add tests for all of the following:

- reverse-direction block exclusion: if `B` blocks `A`, then `A` must not see `B` as a candidate
- rematch cooldown exclusion: if `A` unmatched `B` recently, `B` must not appear in `A`’s candidates
- cooldown expiry: if the unmatch is older than the cooldown window, the candidate may reappear
- stale cache truth: a cached detached candidate snapshot must disappear after the candidate pauses or becomes blocked

Implementation detail for the stale-cache test: create a nested `SnapshottingUsers` or equivalent helper inside `CandidateFinderTest` that returns detached `User.copy()` snapshots from `findCandidates(...)` and `findByIds(...)` so the cache bug reproduces even with in-memory tests.

- [x] ✅ **Step 4: Add failing `MatchingServiceTest` coverage for stale swipe snapshots**

Add tests proving that `processSwipe(...)` fails and persists nothing when:

- the current user pauses after the UI/browse snapshot was taken but before the swipe commits
- the candidate pauses after the UI/browse snapshot was taken but before the swipe commits
- a block is created after the browse snapshot but before the swipe commits

Keep the expected conflict messages aligned with the current user-facing strings if possible.

- [x] ✅ **Step 5: Add failing use-case and REST coverage for cross-adapter alignment**

Add tests that prove:

- `MatchingUseCases.browseCandidates(...)` excludes blocked and recently-unmatched candidates
- `GET /api/users/{id}/browse` excludes the same users
- `GET /api/users/{id}/candidates` excludes the same users even though the route remains the direct-read exception
- `GET /api/users/{id}/browse` returns `locationMissing=true` when the seeker has no location

- [x] ✅ **Step 6: Add failing relationship/storage tests for true rematchability**

Add failing tests that prove all of the following:

- `ConnectionService.unmatch(...)` / `InteractionStorage.unmatchTransition(...)` clears old pair likes in both directions as part of the atomic transition
- the default `InteractionStorage.saveLikeAndMaybeCreateMatch(...)` path can reactivate an ended `UNMATCHED` pair when fresh mutual likes happen again
- the JDBI implementation preserves the same semantics transactionally
- a full app flow can `match -> unmatch -> wait past cooldown -> rematch` successfully

- [x] ✅ **Step 7: Run the focused regression pack and confirm the failures are the intended ones**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=CandidateFinderTest,MatchingServiceTest,MatchingUseCasesTest,RestApiReadRoutesTest,ConnectionServiceTransitionTest,SocialUseCasesTest,JdbiMatchmakingStorageTransitionAtomicityTest,MatchingFlowIntegrationTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=CandidateFinderTest,MatchingServiceTest,MatchingUseCasesTest,RestApiReadRoutesTest,ConnectionServiceTransitionTest,SocialUseCasesTest,JdbiMatchmakingStorageTransitionAtomicityTest,MatchingFlowIntegrationTest test`

Expected before implementation: failures around reverse block filtering, cooldown exclusion, stale cached candidates, stale swipe acceptance, or fake post-cooldown rematchability.

- [x] ✅ **Step 8: Commit**

Commit message:
`test: lock candidate truthfulness regressions`

## Chunk 2: Add the config and wire the new core contract

### Task 2: Introduce a config-driven rematch cooldown

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `config/app-config.json`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Re-run/update: `src/test/java/datingapp/core/AppConfigTest.java`
- Re-run/update: `src/test/java/datingapp/core/AppConfigValidatorTest.java`
- Re-run/update: `src/test/java/datingapp/app/ConfigLoaderTest.java`

- [x] ✅ **Step 1: Add `matching.rematchCooldownHours` to `AppConfig` with deterministic defaults**

Concrete contract:

- record location: `AppConfig.MatchingConfig`
- builder method: `AppConfig.Builder.rematchCooldownHours(int v)`
- default value: `168`
- semantics: `0` disables cooldown; positive values represent hours

Do not create a new config section or nested JSON shape.

- [x] ✅ **Step 2: Validate the new field in `AppConfigValidator`**

Validation rule for this batch:

- `rematchCooldownHours >= 0`

Keep validation deterministic and error-message-specific.

- [x] ✅ **Step 3: Add the flat JSON key to `config/app-config.json`**

Add:
`"rematchCooldownHours": 168`

Place it near the other matching behavior fields so the config remains readable.

- [x] ✅ **Step 4: Wire production composition through `StorageFactory`**

Update the production `CandidateFinder` construction so runtime behavior uses the injected config instead of a magic number.

Recommended primary constructor shape:

- keep a compatibility constructor for existing tests/shims
- add a constructor that accepts a cooldown `Duration` (or the full `AppConfig` if that is simpler and cleaner)
- use `Duration.ofHours(config.matching().rematchCooldownHours())` from `StorageFactory`
- update all remaining production/semi-production construction paths that instantiate `CandidateFinder` directly, especially `MatchingService.defaultCandidateFinder(...)` and any builder/fixture paths that bypass `StorageFactory`

Do **not** leave production code relying on `AppConfig.defaults()` for the new behavior.

- [x] ✅ **Step 5: Re-run the config-only test pack**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest test`

Expected: green.

- [x] ✅ **Step 6: Commit**

Commit message:
`feat: add config-driven rematch cooldown`

### Task 3: Make `CandidateFinder` enforce truthful browse eligibility

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/matching/CandidateFinder.java`
- Modify: `src/test/java/datingapp/core/CandidateFinderTest.java`
- Re-run/update if constructor signatures change: tests that instantiate `CandidateFinder` directly

- [x] ✅ **Step 1: Extend the candidate exclusion model without changing adapter contracts**

Implement these browse rules centrally in `CandidateFinder`:

- exclude any candidate where `trustSafetyStorage.isBlocked(seekerId, candidateId)` is `true`
- exclude any candidate whose most recent match with the seeker ended as `UNMATCH` within the cooldown window
- keep existing location-missing behavior: seeker without location returns an empty list and no candidates are browsed

Do not add adapter-specific patches in REST/CLI/UI.

- [x] ✅ **Step 2: Reuse existing match metadata for cooldown filtering**

Add a helper that derives recent cooldown exclusions from existing matches, using:

- `interactionStorage.getAllMatchesFor(seekerId)`
- `match.getEndReason() == Match.MatchArchiveReason.UNMATCH`
- `match.getEndedAt() != null`
- `match.getEndedAt().isAfter(now.minus(rematchCooldown))`

Do not create a new storage table or schema migration.

- [x] ✅ **Step 3: Revalidate cached candidates from a fresh source set**

On a cache hit, do **not** return the cached `List<User>` blindly.

Instead:

- re-query the fresh prefiltered source set via `userStorage.findCandidates(...)` using the seeker’s current criteria
- rebuild the candidate list from fresh user objects
- rerun the same full eligibility/sorting contract that a non-cached path would apply: self exclusion, active-state filtering, interaction exclusion, gender, age, distance, dealbreakers, symmetric block filtering, cooldown filtering, and deterministic distance ordering

This is required because detached JDBI-style user snapshots can go stale while the cache entry is still live, and newly eligible users must not be hidden until TTL expiry.

- [x] ✅ **Step 4: Keep cache invalidation behavior narrow and deterministic**

Do not remove caching entirely.

Keep the existing fingerprint cache, but make sure the returned list is revalidated against fresh state on cache hits. If a helper method is added, name it clearly, e.g.:

- `refreshCachedCandidates(...)`
- `recentlyUnmatchedCounterpartIds(...)`
- `isBlockedEitherDirection(...)`

If full-source refresh makes the final candidate cache effectively useless, it is acceptable to remove or narrow the cache in this batch rather than keep a semantically misleading cache.

- [x] ✅ **Step 5: Re-run the focused candidate tests**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=CandidateFinderTest,MatchingUseCasesTest,RestApiReadRoutesTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=CandidateFinderTest,MatchingUseCasesTest,RestApiReadRoutesTest test`

Expected: green for the browse path regressions.

- [x] ✅ **Step 6: Commit**

Commit message:
`fix: enforce truthful candidate browsing`

### Task 4: Make cooldown expiry restore real rematchability

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/storage/InteractionStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Modify: `src/test/java/datingapp/core/testutil/TestStorages.java`
- Modify: `src/test/java/datingapp/core/InteractionStorageAtomicityTest.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- Modify: `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`
- Modify: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Modify: `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

- [x] ✅ **Step 1: Clear old pair likes when `UNMATCH` is persisted**

Update the atomic unmatch transition implementations so they remove or soft-delete the pair’s old likes in both directions as part of the same atomic transition that persists the `UNMATCHED` match and archives the conversation.

Concrete target behavior:

- `ConnectionService.unmatch(...)` still leaves the match row in `UNMATCHED`
- `InteractionStorage.unmatchTransition(...)` clears `A -> B` and `B -> A` likes/passes atomically
- in-memory and JDBI implementations behave the same way

- [x] ✅ **Step 2: Allow fresh mutual likes to reactivate an ended `UNMATCHED` pair**

Update `saveLikeAndMaybeCreateMatch(...)` semantics in both the default and JDBI paths so that:

- fresh mutual likes can reactivate an existing `UNMATCHED` pair record
- the deterministic pair ID is reused
- no schema change or match-versioning scheme is introduced in this batch

- [x] ✅ **Step 3: Keep the rematch rule narrow**

Only `UNMATCHED` pairs are reactivatable in this batch.

Do **not** reactivate:

- `BLOCKED`
- `GRACEFUL_EXIT`
- `FRIENDS`

- [x] ✅ **Step 4: Re-run the relationship/storage regression pack**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=InteractionStorageAtomicityTest,JdbiMatchmakingStorageTransitionAtomicityTest,ConnectionServiceTransitionTest,SocialUseCasesTest,RestApiRelationshipRoutesTest,MatchingFlowIntegrationTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=InteractionStorageAtomicityTest,JdbiMatchmakingStorageTransitionAtomicityTest,ConnectionServiceTransitionTest,SocialUseCasesTest,RestApiRelationshipRoutesTest,MatchingFlowIntegrationTest test`

Expected: green.

- [x] ✅ **Step 5: Commit**

Commit message:
`fix: restore real rematchability after cooldown`

### Task 5: Re-check persisted user eligibility inside `MatchingService`

**Execution mode:** main agent only

**Files:**
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`
- Modify: `src/test/java/datingapp/core/MatchingServiceTest.java`
- Modify: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`

- [x] ✅ **Step 1: Extract a locked revalidation helper for swipe eligibility**

Inside `MatchingService`, add a helper that re-loads the current user and candidate from `userStorage` inside the locked path and re-checks:

- current user exists and is `ACTIVE`
- candidate exists and is `ACTIVE`
- the pair is not blocked in either direction
- self-swipe is still impossible

The helper must run inside `processSwipeWithinLock(...)`, not only in the pre-lock fast-fail path.

- [x] ✅ **Step 2: Reuse existing messages where possible**

Preserve the current conflict strings if the same failure condition still applies:

- `Current user must be ACTIVE to swipe.`
- `Candidate must be ACTIVE to receive swipes.`
- `Cannot swipe on a blocked user.`

This minimizes adapter churn and keeps REST/UI/CLI expectations stable.

- [x] ✅ **Step 3: Avoid split logic between `recordLike(...)` and `processSwipe(...)`**

Where practical, share the same allowability logic or a common helper so the direct like path and the swipe path cannot drift again.

It is acceptable if daily-limit checks remain in `processSwipeWithinLock(...)`, but ACTIVE/block truth must not be duplicated inconsistently.

- [x] ✅ **Step 4: Re-run the swipe-guard regression pack**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=MatchingServiceTest,MatchingUseCasesTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=MatchingServiceTest,MatchingUseCasesTest test`

Expected: green, with stale-snapshot pause/block cases now rejected correctly.

- [x] ✅ **Step 5: Commit**

Commit message:
`fix: revalidate swipe targets under lock`

## Chunk 3: Cross-adapter proof and final verification

### Task 6: Prove the behavior end to end without widening contracts

**Execution mode:** leaf tests may be parallelized after Tasks 2-4 are green

**Files:**
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- Review and keep green: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Review and keep green: `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`
- Optional only if copy breaks: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Review and keep green: `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`

- [x] ✅ **Step 1: Keep `/candidates` as the explicit direct-read exception**

Do not reroute `/api/users/{id}/candidates` through `MatchingUseCases` in this batch.

Instead, lock in tests that the route still behaves correctly because `CandidateFinder` is now truthful.

- [x] ✅ **Step 2: Keep the current `locationMissing` contract and UI copy green**

Do not introduce a new enum or `emptyStateReason` field in this batch.

Re-run and keep green:

- `MatchingUseCasesTest.browseCandidatesReportsLocationMissingWhenSeekerHasNoLocation`
- `MatchingViewModelTest.initializeMarksLocationMissingWhenCurrentUserHasNoLocation`
- `MatchingControllerTest.emptyStateShowsLocationGuidanceWhenSeekerHasNoLocation`

If one of these fails because the empty-state copy becomes misleading after the core changes, make the smallest production copy fix in `MatchingController.updateEmptyStateCopy(...)` and nothing broader.

- [x] ✅ **Step 3: Run the adapter regression pack**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=MatchingUseCasesTest,RestApiReadRoutesTest,MatchingViewModelTest,MatchingControllerTest,MatchingHandlerTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=MatchingUseCasesTest,RestApiReadRoutesTest,MatchingViewModelTest,MatchingControllerTest,MatchingHandlerTest test`

Expected: green.

- [x] ✅ **Step 4: Commit**

Commit message:
`test: verify candidate truthfulness across adapters`

### Task 7: Final verification and closeout

**Execution mode:** main agent only

**Files:**
- Review all touched production and test files
- No documentation updates are required unless the implemented behavior intentionally diverges from this plan

- [x] ✅ **Step 1: Run the combined targeted regression suite**

Run:
> **PowerShell:** `mvn --% -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest,CandidateFinderTest,MatchingServiceTest,MatchingUseCasesTest,InteractionStorageAtomicityTest,JdbiMatchmakingStorageTransitionAtomicityTest,ConnectionServiceTransitionTest,SocialUseCasesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,MatchingFlowIntegrationTest,MatchingViewModelTest,MatchingControllerTest,MatchingHandlerTest test`
> **Bash/Zsh:** `mvn -Dcheckstyle.skip=true -Dtest=AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest,CandidateFinderTest,MatchingServiceTest,MatchingUseCasesTest,InteractionStorageAtomicityTest,JdbiMatchmakingStorageTransitionAtomicityTest,ConnectionServiceTransitionTest,SocialUseCasesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,MatchingFlowIntegrationTest,MatchingViewModelTest,MatchingControllerTest,MatchingHandlerTest test`

Expected: green.

- [x] ✅ **Step 2: Run the full repository quality gate**

Run:
`mvn spotless:apply verify`

Expected: exit code `0`, all tests green, Spotless/Checkstyle/PMD/JaCoCo passing.

- [x] ✅ **Step 3: Re-check the changed files for IDE errors**

Run Problems/error checks on all touched files before declaring success.

- [x] ✅ **Step 4: Confirm the acceptance criteria**

Do not stop at “tests pass.” Confirm all of these are true:

- browse results exclude blocked users regardless of who initiated the block
- browse results exclude recently unmatched users until cooldown expiry
- stale cached candidate snapshots are revalidated before reuse
- `UNMATCH` clears stale pair likes so the cooldown can expire into a real rematch
- a fresh mutual like after cooldown can restore the pair to `ACTIVE`
- swipes cannot create likes or matches after either user pauses or a block is created
- `/browse` and `/candidates` stay aligned without rerouting the direct-read exception
- the existing `locationMissing` contract still works in use-case, REST, and JavaFX tests
- no schema migration or new REST field was introduced

- [x] ✅ **Step 5: Commit**

Commit message:
`fix: complete phase 1a candidate truthfulness slice`

## Suggested execution order summary

1. Task 1 — failing characterization tests
2. Task 2 — config field + production wiring
3. Task 3 — truthful browse filtering + cache revalidation
4. Task 4 — real rematchability after cooldown
5. Task 5 — lock-time swipe revalidation
6. Task 6 — cross-adapter proof and no-regression pass
7. Task 7 — full verification

## Definition of done

This plan is complete only when all of the following are true:

- `rematchCooldownHours` exists in config defaults, builder, validator, JSON loader, and repo config
- `CandidateFinder` excludes blocked users symmetrically and excludes recently unmatched users within cooldown
- cached candidate lists are revalidated against fresh user state by rerunning the full eligibility pipeline before reuse
- `UNMATCH` clears stale pair likes while preserving the ended match row as the cooldown anchor
- a fresh mutual like after cooldown can restore the pair to `ACTIVE`
- `MatchingService.processSwipe(...)` cannot save likes or create matches from stale paused/blocked snapshots
- `MatchingUseCases`, REST browse, REST direct candidates, and JavaFX matching surfaces all observe the corrected behavior without route-specific hacks
- the existing `locationMissing` contract remains green
- `mvn spotless:apply verify` passes
