# First-Wave Implementation Plan (Valid Bucket + Selected Quick Wins)

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the highest-priority findings from the report’s `Valid` bucket, plus a small set of explicitly-selected quick-win exceptions, in a way that advances Phase 1 of the roadmap without broad auth hardening, speculative over-refactoring, or technology-scope creep.

**Architecture:** This plan is intentionally correctness-first and scope-disciplined. It favors narrow changes in existing seams, characterization/regression tests before behavior changes, and small helper extractions only where they reduce real duplication or lifecycle risk. Big structural rewrites should be avoided unless a task explicitly requires them to complete a current roadmap goal.

**Tech Stack:** Java 25 (preview enabled), JavaFX 25, Maven, JUnit 5, H2, JDBI, Javalin, Spotless, Checkstyle, PMD, JaCoCo.

---

## Inputs and source of truth

Use these documents as the primary planning context while implementing:

- `2026-03-29-dating-app-roadmap-design.md`
- `2026-04-01-sequential-java-code-quality-report.md`
- `src/main/java`
- `src/test/java`
- `pom.xml`

When the plan and the code disagree, trust the code.

## User intent distilled from the review pass

These constraints are **binding** for implementation:

- **Phase 1 dominates.** Favor current Java/H2/CLI/REST stabilization over long-term purity.
- **JavaFX is temporary, but not irrelevant.** Fix real user-journey and lifecycle issues; do not over-polish.
- **Do not turn this into a REST auth project.** Only fix concrete route-semantics problems explicitly in scope.
- **Timezone correctness matters now.** Broad `AppClock`/DI migration does **not**.
- **Broad class-splitting is not automatically valuable.** Only do it when it directly helps current roadmap work.
- **Database/global-state cleanup stays active.** Shared-state problems that hurt tests/migration work should be fixed now.
- **Event plumbing improvements stay active.** Better failure visibility and typed payloads are in scope; broad achievement-visibility feature work is not.
- **Prefer smaller, obvious helpers over package explosions.**

## Scope of this first-wave plan

### In scope from the report’s `Valid` bucket

- **13** Candidate cache invalidation
- **14** Daily-limit timezone correctness
- **15** Centralize profile-completeness rules
- **17** Unify swipe eligibility evaluation
- **23** Align `LocationService` advertised support with actual support
- **27** Extract generic normalized-collection writer in `JdbiUserStorage`
- **33** Remove blocking image load inside `ImageCache` lock
- **35** Reduce `ProfileViewModel` overload
- **36** Reduce `ChatViewModel` overload
- **37** Reduce `MatchingViewModel` overload
- **41** Reduce `ProfileController` overload
- **42** Extract create-account/onboarding dialog flow from `LoginController`
- **44** Extract shared photo-carousel state
- **48** Improve `TestStorages` / fake-vs-real parity
- **49** Reduce shadow composition in `RestApiTestFixture`
- **51** Reduce sleep/poll-heavy UI tests
- **52** Expand storage contract coverage
- **53** Require acting user for conversation message reads
- **57** Improve BEST_EFFORT event failure visibility
- **58** Type event payloads
- **60** Daily-pick timezone correctness
- **65** Remove `systemDefault()` fallback overloads in `MatchPreferences`
- **66** Tighten `User.StorageBuilder` location invariant
- **67** Fix `DatabaseManager` global mutable state issues
- **68** Make `StorageFactory.buildInMemory(...)` actually isolated
- **69** Make `DevDataSeeder` robust against partial-seed states
- **70** Type/strict the normalized-profile hydration path
- **71** Remove ambient `ThreadLocal` handle behavior in `JdbiUserStorage`
- **72** Track delayed achievement transitions in `DashboardController`
- **73** Cancel `MilestonePopupController` auto-dismiss timer
- **74** Track `MatchesController` NEW-badge animation lifecycle
- **75** Clear navigation history on logout
- **76** Rename/scope `RestApiRoutesTest` correctly
- **77** Add or clarify real `Main` lifecycle coverage

### Included quick-win exceptions outside the `Valid` bucket

These are included **by exception**, not because they were reclassified as `Valid`. They stay in this first wave only because they are localized, low-risk, and reduce ambiguity for adjacent active work:

- **2** Fail fast on malformed JSON/config parse failures
- **12** Validate conversation ID inputs consistently
- **18** Unify dealbreaker verdict + explanation evaluation path
- **46** Replace the remaining raw-line architecture scans that matter now
- **56** Roll back partial startup state on bootstrap failure
- **62** Batch total unread count instead of per-conversation looping

### Explicitly out of scope for this plan

Do **not** expand scope into these areas during implementation:

- broad REST local-auth/privacy hardening beyond the narrow route-semantics task below
- `/api/health` readiness/ops redesign
- full `AppClock` replacement with pervasive clock injection
- PostgreSQL migration work
- Kotlin migration work
- Android or cloud work
- broad `ServiceRegistry`, `MatchingUseCases`, `SocialUseCases`, `ConnectionService`, or `ViewModelFactory` rewrites for their own sake
- large achievement/notification surfacing features
- recommendation-ranking integration unless existing tests show current browsing semantics are already wrong and the fix is still small

## Roadmap Phase 1 coverage for this wave

This first wave is intentionally a **foundation slice**, not the entire Phase 1 roadmap. It fixes the most important semantic, shared-state, storage-parity, and JavaFX lifecycle issues first so later CLI/REST surfacing work lands on simpler seams.

| Roadmap Phase 1 area                             | Status in this wave                                                         | Notes                                                                                                                          |
|--------------------------------------------------|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Timezone/date-boundary correctness               | Covered now                                                                 | Daily limits, daily picks, and `systemDefault()` fallback cleanup are in scope                                                 |
| Matching/profile truthfulness                    | Covered now                                                                 | Candidate freshness, location honesty, swipe/dealbreaker truthfulness, and profile-completion rule unification                 |
| Signup auto-fill garbage                         | Covered now                                                                 | `LoginViewModel` cleanup will be included under the signup/onboarding task                                                     |
| JavaFX profile editing and lifecycle stability   | Covered now                                                                 | Focus on real user journeys and cleanup, not polish for its own sake                                                           |
| Storage/test parity and shared-state cleanup     | Covered now                                                                 | `DatabaseManager`, `StorageFactory.buildInMemory(...)`, `DevDataSeeder`, `JdbiUserStorage`, `TestStorages`, and fixture parity |
| Event failure visibility and typed payloads      | Covered now                                                                 | Plumbing hardening only, not broad user-facing achievement surfacing                                                           |
| MatchQuality surfacing                           | Deferred to later wave                                                      | Still desired, but not part of this foundation slice                                                                           |
| ActivityMetrics surfacing                        | Deferred to later wave                                                      | This wave prepares cleaner semantics/tests first                                                                               |
| Recommendation ranking integration               | Deferred unless current browsing proves semantically wrong during execution | Do not force it in by default                                                                                                  |
| Achievements / notifications surfacing           | Deferred to later wave                                                      | Plumbing yes, broad visibility feature work no                                                                                 |
| First-launch experience beyond signup correction | Deferred to later wave                                                      | Keep this wave focused on concrete signup/profile fixes                                                                        |
| Broad REST hardening / ops readiness             | Deferred                                                                    | Out of scope unless a task explicitly requires a narrow semantics fix                                                          |

## Execution strategy

Implement **in order**. Do not run phases in parallel.

1. Foundation correctness
2. Time/timezone semantics
3. Matching/profile truthfulness
4. Route and conversation correctness
5. Storage/global-state stabilization
6. Test parity and naming/coverage cleanup
7. JavaFX lifecycle and user-journey cleanup
8. Event-plumbing hardening
9. Full validation

After each task:
- run the targeted tests for that task,
- fix regressions before continuing,
- keep diffs small and scoped.

- [ ] Commit immediately after successful local verification to preserve atomic rollback points and smaller review diffs.

Do **not** open large refactors preemptively. If a task starts requiring broad restructuring, stop at the narrowest safe helper extraction that still solves the active finding.

## Task map

| Task                                                          | Findings addressed      |
|---------------------------------------------------------------|-------------------------|
| 1. Startup/config hardening                                   | 2, 56                   |
| 2. Timezone correctness and fallback cleanup                  | 14, 60, 65              |
| 3A. Candidate freshness and location truthfulness             | 13, 23                  |
| 3B. Profile-completeness rule unification and user invariants | 15, 66                  |
| 3C. Swipe/dealbreaker truthfulness                            | 17, 18                  |
| 4. REST conversation semantics + unread batching              | 12, 53, 62              |
| 5. Storage shared-state and seeder robustness                 | 67, 68, 69              |
| 6. `JdbiUserStorage` internal cleanup                         | 27, 70, 71              |
| 7A. Storage parity and fixture simplification                 | 48, 49, 52              |
| 7B. Architecture/test-support cleanup                         | 46, 51                  |
| 7C. Test naming and lifecycle coverage cleanup                | 76, 77                  |
| 8A. `ImageCache` and shared photo state                       | 33, 44                  |
| 8B. Controller lifecycle and logout/navigation cleanup        | 72, 73, 74, 75          |
| 9A. Signup/onboarding correction and dialog extraction        | 42 + roadmap signup gap |
| 9B. Profile screen/viewmodel overload reduction               | 35, 41                  |
| 10A. `ChatViewModel` state cleanup                            | 36                      |
| 10B. `MatchingViewModel` state cleanup                        | 37                      |
| 11. Event-bus and payload hardening                           | 57, 58                  |
| 12. Final validation                                          | all                     |

## Chunk 1: Foundation correctness

### Task 1: Startup/config hardening

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` *(commit after successful local verification)*
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java` *(only if needed for cleanup/reset semantics, not for redesign; commit after successful local verification)*
- Test: `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`
- Test: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Test: `src/test/java/datingapp/app/CleanupSchedulerTest.java`
- Test: `src/test/java/datingapp/MainLifecycleTest.java`
- Create if needed: `src/test/java/datingapp/app/bootstrap/ApplicationStartupFailureRecoveryTest.java`

- [x] **Step 1: Write/expand failing tests for malformed JSON handling**
  - Add coverage that malformed JSON / mapper deserialization failures do **not** silently fall back to defaults.
  - Keep unknown-key behavior as already-fail-fast; do not rewrite that part.

- [x] **Step 2: Write/expand failing tests for partial-startup rollback**
  - Simulate a failure after static state assignment but before successful initialization completes.
  - Assert that a failed initialize call does not leave `services`, `dbManager`, scheduler refs, or init flags in a dirty retry-blocking state.
  - If a test seam is required, add the **narrowest package-private hook possible**. Do **not** add a general DI container.

- [x] **Step 3: Implement the startup hardening**
  - Make JSON parse/deserialization failures fatal.
  - Wrap initialization in rollback-aware cleanup.
  - Ensure partially constructed state is cleared on failure.
  - Preserve current public API shape unless a tiny package-private seam is needed for tests.

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=ApplicationStartupBootstrapTest,ConfigLoaderTest,CleanupSchedulerTest,MainLifecycleTest,ApplicationStartupFailureRecoveryTest test`
  - Expected: all relevant startup/config tests pass.

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: harden startup rollback and config parse failures`

**Acceptance criteria:**
- malformed JSON can no longer boot with silent defaults
- startup can be retried cleanly after a partial failure
- no new bootstrap/global-state leak is introduced

**Pitfalls to avoid:**
- do not add a new framework or general dependency injection mechanism
- do not broaden this into operational health/readiness work

### Task 2: Timezone correctness and fallback cleanup

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/core/matching/DefaultDailyLimitService.java`
- Modify: `src/main/java/datingapp/core/matching/DefaultDailyPickService.java`
- Modify: `src/main/java/datingapp/core/profile/MatchPreferences.java`
- Test: `src/test/java/datingapp/core/AppClockTest.java`
- Test: `src/test/java/datingapp/core/DailyLimitBoundaryTest.java`
- Test: `src/test/java/datingapp/core/DailyLimitServiceTest.java`
- Test: `src/test/java/datingapp/core/DailyPickServiceTest.java`
- Test: `src/test/java/datingapp/core/DealbreakersEvaluatorTest.java`

- [x] **Step 1: Write/expand failing timezone-boundary tests**
  - Add/adjust tests proving daily limits use `config.safety().userTimeZone()` for day boundaries.
  - Add/adjust tests proving daily picks use the same configured timezone, not `clock.getZone()`.
  - Keep tests deterministic using `TestClock`/`AppClock` helpers.

- [x] **Step 2: Remove or quarantine implicit-timezone overloads**
  - Search for all internal callers of `MatchPreferences.Dealbreakers.Evaluator.passes(User, User)` and `getFailedDealbreakers(User, User)`.
  - Update callers to use explicit timezone overloads.
  - If the no-timezone overloads become unused, remove them; otherwise isolate them further and keep them out of production call paths.

- [x] **Step 3: Implement the timezone fixes**
  - Use `config.safety().userTimeZone()` consistently in `DefaultDailyLimitService` and `DefaultDailyPickService`.
  - Keep the change local; do **not** broaden to a full app-wide clock/zone abstraction migration.

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=AppClockTest,DailyLimitBoundaryTest,DailyLimitServiceTest,DailyPickServiceTest,DealbreakersEvaluatorTest test`
  - Expected: all time/date-boundary tests pass.

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: unify configured timezone usage for daily semantics`

**Acceptance criteria:**
- daily limits and daily picks both use the configured timezone
- no production code relies on the deprecated no-timezone dealbreaker overloads
- the disabled `TimePolicyArchitectureTest` remains disabled for now

## Chunk 2: Matching/profile truthfulness

### Task 3A: Candidate freshness and location truthfulness

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/core/matching/CandidateFinder.java`
- Modify: `src/main/java/datingapp/core/profile/LocationService.java`
- Modify if needed: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify if needed: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify if needed: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify if needed: `src/main/java/datingapp/ui/screen/LocationSelectionDialog.java`
- Test: `src/test/java/datingapp/core/CandidateFinderTest.java`
- Test: `src/test/java/datingapp/core/profile/LocationServiceTest.java`
- Test if needed: `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`

- [x] **Step 1: Add characterization tests around candidate freshness**
  - Cover candidate visibility after state/block/unmatch/profile changes.
  - Prefer a correctness-preserving simplification over an elaborate invalidation graph.

- [x] **Step 2: Add location-support truthfulness tests**
  - Assert the system only advertises or accepts locations that it truly supports for this phase.
  - Make the product message honest: “Israel-only for now” is better than fake broader support.

- [x] **Step 3: Implement the candidate/location fixes**
  - Prefer the least-complex correct solution for candidate freshness.
  - If the current cache cannot be made obviously correct with small changes, simplify or remove it instead of building a complex invalidation system.
  - Make location offerings honest across core + adapter touchpoints, without pretending broader support exists.

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=CandidateFinderTest,LocationServiceTest,MatchingUseCasesTest test`
  - Expected: candidate/location truthfulness tests pass.

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: align candidate freshness and location truthfulness`

### Task 3B: Profile-completeness rule unification and user invariants

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Modify: `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
- Modify: `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify if needed: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify if needed: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Test: `src/test/java/datingapp/core/ProfileCompletionServiceTest.java`
- Test: `src/test/java/datingapp/core/workflow/ProfileActivationPolicyTest.java`
- Test: `src/test/java/datingapp/core/UserTest.java`
- Test if needed: `src/test/java/datingapp/core/metrics/DefaultAchievementServiceTest.java`
- Create if needed: `src/test/java/datingapp/core/ProfileCompletionConsistencyTest.java`

- [x] **Step 1: Add profile-completion consistency tests**
  - Assert the same required-field truth drives profile completeness percentage/breakdown, activation eligibility, and achievement completion checks.
  - Prefer one rule source over duplicate field lists.

- [x] **Step 2: Tighten `User` storage reconstitution invariants**
  - Remove the easy-to-misuse split between setting raw coordinates and the `hasLocationSet` flag.
  - Keep the storage loader path simple and explicit.

- [x] **Step 3: Implement the rule unification**
  - Keep public behavior stable where already correct.
  - Centralize the rule source instead of cloning field lists in multiple places.

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=ProfileCompletionServiceTest,ProfileActivationPolicyTest,DefaultAchievementServiceTest,UserTest,ProfileCompletionConsistencyTest test`

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: unify profile completion rules and user invariants`

### Task 3C: Swipe and dealbreaker truthfulness

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`
- Modify: `src/main/java/datingapp/core/profile/MatchPreferences.java`
- Test: `src/test/java/datingapp/core/MatchingServiceTest.java`
- Test: `src/test/java/datingapp/core/DealbreakersEvaluatorTest.java`
- Test if needed: `src/test/java/datingapp/core/CandidateFinderTest.java`

- [x] **Step 1: Add failing tests for swipe/dealbreaker verdict drift**
  - Cover cases where boolean pass/fail logic and explanation logic could diverge.
  - Cover eligibility edge cases that should remain consistent across pre-check and persistence paths.

- [x] **Step 2: Implement the smallest shared verdict path**
  - Introduce a tiny internal result/helper type only if it reduces duplication clearly.
  - Reuse it for both the boolean verdict and failure explanation path.
  - Avoid turning this into a broad policy-engine rewrite.

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=MatchingServiceTest,DealbreakersEvaluatorTest,CandidateFinderTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: align swipe and dealbreaker evaluation truth`

### Task 4: REST conversation semantics and connection quick wins

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- Modify: `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- Test: `src/test/java/datingapp/core/connection/ConnectionModelsTest.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`

- [x] **Step 1: Write/expand failing tests for conversation-read semantics**
  - `GET /api/conversations/{conversationId}/messages` must require an acting user and must validate that user is a participant.
  - Keep this narrow. Do **not** expand into broad local-auth/privacy hardening.

- [x] **Step 2: Add failing core tests for conversation ID validation**
  - Cover null/invalid inputs for `Conversation.generateId(...)` and any direct service entry points that currently rely on implicit NPE behavior.

- [x] **Step 3: Add failing tests for total unread batching**
  - Verify `ConnectionService.getTotalUnreadCount(...)` preserves current semantics while using the batched unread-count path.

- [x] **Step 4: Implement the narrow fixes**
  - require acting-user identity for conversation message reads only
  - keep other local API hardening out of scope
  - harden conversation ID generation/validation
  - replace per-conversation unread summing with batched unread counting where possible

- [x] **Step 5: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=RestApiReadRoutesTest,RestApiConversationBatchCountTest,RestApiPhaseTwoRoutesTest,ConnectionModelsTest,ConnectionServiceAtomicityTest,ConnectionServiceTransitionTest test`
  - Expected: route semantics and connection-service tests pass.

- [ ] **Step 6: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: tighten conversation route semantics and unread counting`

## Chunk 3: Storage shared state and parity

### Task 5: Storage shared-state and dev-seed robustness

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java` *(commit after successful local verification)*
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/storage/DevDataSeeder.java`
- Test: `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`
- Test: `src/test/java/datingapp/storage/DatabaseManagerThreadSafetyTest.java`
- Create if needed: `src/test/java/datingapp/storage/DevDataSeederTest.java`
- Create if needed: `src/test/java/datingapp/storage/StorageFactoryInMemoryTest.java`

- [x] **Step 1: Add failing tests for shared DB state reset/isolation**
  - Assert `resetInstance()` fully resets relevant global DB state.
  - Assert `StorageFactory.buildInMemory(...)` does not share ambient singleton configuration across runs.

- [x] **Step 2: Add failing tests for partial-seed recovery**
  - Simulate a database where the sentinel exists but the full seed set does not.
  - Assert seeding completes/repairs the full set instead of skipping forever.

- [x] **Step 3: Implement the shared-state fixes**
  - remove or neutralize sticky global JDBC configuration behavior
  - ensure in-memory builds use isolated configuration
  - make seed completion idempotent at the full-dataset level, not only the sentinel-user level

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=DatabaseManagerConfigurationTest,DatabaseManagerThreadSafetyTest,DevDataSeederTest,StorageFactoryInMemoryTest test`
  - Expected: storage shared-state and seed tests pass.

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: isolate storage state and harden dev seeding`

### Task 6: `JdbiUserStorage` internal cleanup

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- Test: `src/test/java/datingapp/core/storage/StorageContractTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/RecordBindingTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/SqlRowReadersTest.java`

- [x] **Step 1: Add failing tests for hydration typing and lock-context behavior**
  - Cover unexpected normalized group handling.
  - Cover the locked-handle path so a future refactor cannot regress transaction/lock semantics.

- [x] **Step 2: Extract the normalized writer helper**
  - Replace the repeated delete-then-batch-insert pattern with one small internal helper.
  - Keep it private to `JdbiUserStorage` unless a second storage class genuinely needs it.

- [x] **Step 3: Type or constrain normalized-profile hydration**
  - Introduce typed internal group handling (enum/descriptor/private mapping) instead of silent stringly-typed routing.
  - Do **not** split the `UNION ALL` query into multiple queries unless the code is still clearly unmaintainable after typing and helper extraction.

- [x] **Step 4: Remove `ThreadLocal` ambient-handle behavior**
  - Keep transactional behavior explicit.
  - Preserve `executeWithUserLock(...)` semantics, but do not rely on hidden thread-local state in `save()`/`get()`.

- [x] **Step 5: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,StorageContractTest,RecordBindingTest,SqlRowReadersTest test`
  - Expected: storage normalization/contract tests pass.

- [ ] **Step 6: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `refactor: simplify and harden jdbi user storage internals`

### Task 7A: Storage parity and fixture simplification

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/test/java/datingapp/core/testutil/TestStorages.java`
- Modify: `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- Modify: `src/test/java/datingapp/core/storage/StorageContractTest.java`

- [x] **Step 1: Improve fake-vs-real storage parity**
  - Simplify `TestStorages` where it duplicates too much smart behavior.
  - Expand `StorageContractTest` around the behaviors the app actually relies on now.
  - Keep H2/JDBI parity strong for active Phase 1 flows.

- [x] **Step 2: Reduce shadow composition in REST tests**
  - Make `RestApiTestFixture` reuse `ServiceRegistry.Builder` more directly.
  - Do not duplicate production wiring logic unnecessarily.

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=StorageContractTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiPhaseTwoRoutesTest test`
  - These route suites exercise the shared REST fixture while `StorageContractTest` covers fake-vs-real storage expectations.

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `test: tighten storage parity and rest fixture scope`

### Task 7B: Architecture/test-support cleanup

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/test/java/datingapp/architecture/ArchitectureTestSupport.java`
- Modify: `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java` *(do not re-enable the disabled rule; only narrow the active scan support if touched)*
- Modify: `src/test/java/datingapp/ui/JavaFxTestSupport.java`
- Modify: `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java`
- Test: `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- Test: `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`

- [x] **Step 1: Narrow the architecture-scan quick win**
  - Replace only the remaining raw-line scans that matter for current active tests.
  - Do **not** broaden this into an architecture-test crusade.

- [x] **Step 2: Reduce sleep/poll reliance where the fix is local and cheap**
  - Improve `JavaFxTestSupport` / `UiAsyncTestSupport` only where it directly supports active UI tasks in this plan.
  - Do not launch a whole new test framework.

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=TimePolicyArchitectureTest,ViewModelAsyncScopeTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `test: narrow raw-line scans and reduce flaky ui waits`

### Task 7C: Test naming and lifecycle coverage cleanup

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/test/java/datingapp/MainLifecycleTest.java`
- Rename: `src/test/java/datingapp/app/api/RestApiRoutesTest.java` → `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- Create if needed: `src/test/java/datingapp/MainBootstrapLifecycleTest.java`

- [x] **Step 1: Clean up misleading test names/scope**
  - Rename `RestApiRoutesTest` to reflect DTO-only coverage.
  - Either expand `MainLifecycleTest` or add a dedicated lifecycle bootstrap test so test names match real coverage.

- [x] **Step 2: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,MainLifecycleTest,MainBootstrapLifecycleTest test`

- [ ] **Step 3: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `test: align route dto naming and main lifecycle coverage`

## Chunk 4: JavaFX lifecycle and user journeys

### Task 8A: `ImageCache` and shared photo state

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/ui/ImageCache.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileReadOnlyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Create: `src/main/java/datingapp/ui/viewmodel/PhotoCarouselState.java`
- Test: `src/test/java/datingapp/ui/ImageCacheTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Create if needed: `src/test/java/datingapp/ui/viewmodel/PhotoCarouselStateTest.java`

- [x] **Step 1: Fix `ImageCache` lock behavior**
  - move expensive load/decode work outside the global cache lock
  - keep cache mutation synchronized and bounded
  - preserve current API behavior

- [x] **Step 2: Extract `PhotoCarouselState`**
  - centralize photo-list/index/next/previous/current-url behavior in one small helper
  - wire `ProfileViewModel`, `ProfileReadOnlyViewModel`, and `MatchingViewModel` to use it
  - keep it small and UI-facing; do not create a viewmodel micro-framework

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=ImageCacheTest,ProfileViewModelTest,MatchingViewModelTest,PhotoCarouselStateTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: harden image cache and shared photo state`

### Task 8B: Controller lifecycle and logout/navigation cleanup

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/ui/NavigationService.java`
- Modify: `src/main/java/datingapp/ui/screen/DashboardController.java`
- Modify: `src/main/java/datingapp/ui/screen/MilestonePopupController.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchesController.java`
- Test: `src/test/java/datingapp/ui/screen/DashboardControllerTest.java`
- Test: `src/test/java/datingapp/ui/screen/MilestonePopupControllerTest.java`
- Test: `src/test/java/datingapp/ui/screen/MatchesControllerTest.java`
- Test: `src/test/java/datingapp/ui/NavigationServiceTest.java`
- Test: `src/test/java/datingapp/ui/NavigationServiceContextTest.java`

- [x] **Step 1: Add failing tests for UI lifecycle leaks/races**
  - dashboard delayed transitions after cleanup
  - popup auto-dismiss vs manual close
  - untracked NEW-badge animations
  - logout/history reset behavior

- [x] **Step 2: Implement the lifecycle fixes**
  - track or cancel delayed transitions in `DashboardController`
  - track or cancel auto-dismiss in `MilestonePopupController`
  - track `MatchesController` badge pulse lifecycle
  - clear navigation history on logout or at login reset boundary

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=DashboardControllerTest,MilestonePopupControllerTest,MatchesControllerTest,NavigationServiceTest,NavigationServiceContextTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: harden controller lifecycle and logout navigation`

### Task 9A: Signup/onboarding correction and dialog extraction

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Create: `src/main/java/datingapp/ui/screen/CreateAccountDialogFactory.java`
- Test: `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

- [x] **Step 1: Add/expand failing tests around profile and signup flow boundaries**
  - account creation dialog extraction should preserve behavior
  - remove the hardcoded Tel Aviv / placeholder photo / default bio / opportunistic auto-activation behavior from `LoginViewModel`
  - keep new-user creation aligned with the roadmap: incomplete-but-honest beats auto-filled garbage

- [x] **Step 2: Extract the create-account dialog**
  - move the large inline dialog builder out of `LoginController.showCreateAccountDialog()`
  - prefer a small factory/helper, not a new UI framework or FXML subtree unless clearly necessary

- [x] **Step 3: Correct the signup/onboarding defaults**
  - make account creation truthful instead of auto-filling fake profile completeness
  - keep the first-wave fix narrow; do not build a full onboarding wizard here

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,LoginControllerTest test`

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: make signup honest and extract account dialog`

### Task 9B: Profile screen/viewmodel overload reduction

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileFormValidatorTest.java`

- [x] **Step 1: Add/expand failing tests around profile editing boundaries**
  - profile editing/save/validation/dirty-state paths must remain green
  - preview/profile-completion interactions must remain intact

- [x] **Step 2: Reduce `ProfileViewModel` and `ProfileController` overload in-place**
  - favor cohesive helper extraction and method boundary cleanup over a giant rewrite
  - keep profile editing, preview, validation, and save flows intact
  - reuse `PhotoCarouselState` from Task 8A instead of re-implementing photo state

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=ProfileViewModelTest,ProfileControllerTest,ProfileFormValidatorTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `refactor: simplify profile screen and viewmodel seams`

### Task 10A: `ChatViewModel` state cleanup

**Status:** ✅ Verified complete in workspace on 2026-04-01 *(existing state/lifecycle cleanup plus targeted test coverage were already present; no additional code changes were required in this pass)*

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

- [x] **Step 1: Add/expand failing tests around lifecycle/state separation**
  - selected conversation / polling / note behavior in `ChatViewModel`

- [x] **Step 2: Refactor state ownership with the smallest useful extractions**
  - prefer small private helpers or one focused package-private helper over multi-file sprawl
  - keep the work centered on conversation list / active thread / private-note state
  - do not broaden this into a generic “split every big viewmodel” effort

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=ChatViewModelTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `refactor: untangle chat viewmodel state`

### Task 10B: `MatchingViewModel` state cleanup

**Status:** ✅ Verified complete in workspace on 2026-04-01 *(existing state/lifecycle cleanup plus targeted test coverage were already present; no additional code changes were required in this pass)*

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Modify if needed: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Test if needed: `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`

- [x] **Step 1: Add/expand failing tests around swipe/note/photo state separation**
  - candidate swipe/undo vs note/photo state in `MatchingViewModel`
  - only touch `MatchesViewModel` if tests show the cleanup is necessary for this wave

- [x] **Step 2: Refactor state ownership with the smallest useful extractions**
  - reuse `PhotoCarouselState` and any lifecycle helpers already introduced
  - keep the scope centered on matching-state ownership, not broad UI redesign

- [x] **Step 3: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=MatchingViewModelTest,MatchesViewModelTest test`

- [ ] **Step 4: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `refactor: untangle matching viewmodel state`

## Chunk 5: Event plumbing and final verification

### Task 11: Event-bus failure visibility and typed payloads

**Status:** ✅ Implemented and verified in workspace on 2026-04-01 *(commit immediately after successful local verification; keep one task per commit; use the listed message as the template/pattern)*

**Files:**
- Modify: `src/main/java/datingapp/app/event/AppEvent.java`
- Modify: `src/main/java/datingapp/app/event/InProcessAppEventBus.java`
- Modify: `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java`
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Test: `src/test/java/datingapp/app/event/InProcessAppEventBusTest.java`
- Test: `src/test/java/datingapp/app/event/handlers/MetricsEventHandlerTest.java`
- Test: `src/test/java/datingapp/app/event/handlers/NotificationEventHandlerTest.java`

- [x] **Step 1: Add failing tests for typed payloads and throwable visibility**
  - BEST_EFFORT handler failures should preserve full throwable logging/visibility
  - `SwipeRecorded` and `RelationshipTransitioned` should stop relying on fragile raw strings where practical

- [x] **Step 2: Type the event payloads**
  - prefer concrete enums/types already present in the domain (`Like.Direction`, relevant match state enum)
  - update publishers and consumers together in one pass
  - avoid inventing a new generic event framework

- [x] **Step 3: Improve BEST_EFFORT failure logging**
  - keep REQUIRED semantics unchanged
  - log the full throwable, not just `getMessage()`

- [x] **Step 4: Run targeted tests**
  - Run:
    - `mvn -Dcheckstyle.skip=true -Dtest=InProcessAppEventBusTest,MetricsEventHandlerTest,NotificationEventHandlerTest test`

- [ ] **Step 5: Commit** *(commit immediately after the targeted tests above pass; keep one task per commit; use the listed message as the template/pattern)*
  - Commit message: `fix: harden event payloads and handler failure visibility`

### Task 12: Final validation and completion gate

**Status:** ✅ Implemented and verified in workspace on 2026-04-02

**Files:**
- Verify: `src/main/java/**`
- Verify: `src/test/java/**`
- Verify: `pom.xml`
- Update only if needed: `2026-04-01-sequential-java-code-quality-report.md` *(optional, only if implementation materially changes classification or closes findings and the user later asks for a status refresh)*

- [x] **Step 1: Run final targeted regression batches**
  - Run the most relevant grouped suites again:
    - `mvn -Dcheckstyle.skip=true -Dtest=ApplicationStartupBootstrapTest,ConfigLoaderTest,CandidateFinderTest,DailyLimitBoundaryTest,DailyPickServiceTest,ConnectionModelsTest,ConnectionServiceTransitionTest,StorageContractTest,ProfileViewModelTest,ChatViewModelTest,MatchingViewModelTest,InProcessAppEventBusTest test`

- [x] **Step 2: Run full repo quality gate**
  - Run:
    - `mvn spotless:apply verify`
  - Expected: BUILD SUCCESS, no failing tests, formatting/lint/coverage satisfied.

- [x] **Step 3: Sanity-check scope creep**
  - Ensure no PostgreSQL, Kotlin, Android, or broad auth work was pulled in.
  - Ensure large refactors were not performed unless they were necessary to complete the listed tasks.

- [x] **Step 4: Produce a completion summary**
  - Report which findings were fully resolved
  - report which findings were partially addressed/narrowed
  - list any intentionally deferred items encountered during implementation

## Acceptance criteria for the whole plan

By the end of this plan:

- startup/config behavior fails loudly and recovers cleanly on partial init failures
- daily-limit and daily-pick semantics use the configured timezone consistently
- candidate browsing and profile completeness behavior are more truthful and less duplicated
- conversation read semantics are narrowed without turning the project into a full auth-hardening effort
- storage/shared-state behavior is safer for tests and later migration work
- `JdbiUserStorage` is less brittle and less context-dependent
- JavaFX profile/chat/matching flows are safer to navigate and easier to maintain without over-investing in throwaway UI
- event payloads and BEST_EFFORT logging are less fragile
- the repo passes `mvn spotless:apply verify`

## Notes to the implementing agent

- Start with **tests first** for every behavior change.
- Prefer the **smallest correct change**.
- When a report finding’s original wording is broader than the project wants, follow the **narrow interpretation** already encoded in this plan.
- For the JavaFX refactors, use helper extraction to reduce overload; do **not** turn temporary UI code into an enterprise framework.
- For storage and core semantics, correctness beats caching and cleverness.
- If you discover one of the “quick wins” is no longer quick, drop it from the current batch and keep the rest of the plan moving.
