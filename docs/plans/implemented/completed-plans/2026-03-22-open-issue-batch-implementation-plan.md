# Open Issue Batch Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the still-valid parts of the requested issue batch, explicitly close or re-scope the stale issue titles, and land the code, schema, config, docs, and tests needed for a clean end-to-end remediation.

**Architecture:** Keep the existing layered structure intact. Route runtime-tunable behavior through `AppConfig` and `ApplicationStartup`, keep `core/` free of UI/framework imports, implement schema changes via `MigrationRunner` first and only then align `SchemaInitializer`, and keep risky cross-aggregate behavior behind narrow storage/use-case seams instead of ad-hoc SQL from UI or CLI layers.

**Tech Stack:** Java 25 preview, JavaFX 25, Maven, JDBI, H2, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo.

---

## Scope Summary

### Requested issues

- [x] ✅ `V094` Transaction timeout configurability is missing
- [x] ✅ `V095` User.isInterestedInEveryone relies on fragile full-set assumption
- [x] ✅ `V088` Compatibility activity thresholds duplicated across services
- [x] ✅ `V084` `profile_views` has unused `AUTO_INCREMENT` surrogate column
- [x] ✅ `V082` Constraint naming convention is inconsistent
- [x] ✅ `V080` Archived Java utility code remains in docs folder
- [x] ✅ `V076` Soft-delete propagation not cascaded to dependent entities
- [x] ✅ `V062` CLI mutates user state without immediate persistence guarantees
- [x] ✅ `V059` ChatViewModel test race and nondeterministic ordering
- [x] ✅ `V051` Super Like UI action is placeholder behavior
- [x] ✅ `V048` Event system may require expansion
- [x] ✅ `V046` Configuration sourcing inconsistency and stale defaults guidance
- [x] ✅ `V043` CLI login-gate checks are duplicated/inconsistent
- [x] ✅ `V041` Business limits/rules are hardcoded instead of config-driven
- [x] ✅ `V035` Multi-photo UI/gallery parity gap
- [x] ✅ `V031` Key concurrency/error-path test categories are missing
- [x] ✅ `V023` Partially constructible services with runtime null-mode switching
- [x] ✅ `V021` JavaFX social/friend-request/notifications parity gap
- [x] ✅ `V018` Presence feature flag lacks documentation
- [x] ✅ `V005` Dealbreakers mapper does not merge all normalized sources

### Revalidation result to use during implementation

- [x] ✅ Treat `V021` as stale: current `SocialController` / `SocialViewModel` already implement friend requests + notifications.
- [x] ✅ Treat `V035` as stale: current profile and matching UIs already support multi-photo browsing.
- [x] ✅ Treat `V059` as resolved unless a fresh flaky failure is reproduced.
- [x] ✅ Treat `V048` as a re-scope item, not a standalone platform rewrite. Fold it into `V051` only if super-like behavior needs an event contract that current `SwipeRecorded` cannot represent.
- [x] ✅ Treat `V005` as partially stale: normalized dealbreakers are merged during hydration, but candidate caching and large-radius filtering still need remediation.
- [x] ✅ Treat `V046` as cleanup, not architecture surgery: runtime loading is centralized, but `config/app-config.json` still contains ignored/stale keys.
- [x] ✅ Treat `V088` as a policy-ownership cleanup: the old duplication claim is overstated, but threshold ownership/comments are still muddy.

### Default implementation assumptions

- [x] ✅ For `V076`, if no human overrides this plan, use this policy: account deletion soft-deletes the user plus every dependent row that already has a `deleted_at` column and is tied to that user (`likes`, `matches`, `conversations`, `messages`, `profile_notes`, `blocks`, `reports`). Tables without `deleted_at` stay structurally unchanged in this batch, but user-facing reads must not surface artifacts for soft-deleted users.
- [x] ✅ For `V094`, solve actual statement/query timeout propagation, not only Hikari connection acquisition timeout.
- [x] ✅ For `V095`, do not add a new persisted gender-preference mode. Instead, centralize the set of matchable genders behind one explicit policy/helper and make `User.isInterestedInEveryone()` depend on that explicit set rather than `EnumSet.allOf(...)`.
- [x] ✅ For `V051`, prefer extending existing swipe plumbing with a real `SUPER_LIKE` direction end-to-end. Only add a brand new event type if `AppEvent.SwipeRecorded` cannot express the distinction cleanly enough for downstream consumers.
- [x] ✅ For `V041`, only move genuine product/deployment policy into config. Do not bloat `AppConfig` with purely internal math constants unless they affect externally meaningful behavior.

## Issue-To-Task Map

| Task | Issues                         | Outcome                                                                                               |
|------|--------------------------------|-------------------------------------------------------------------------------------------------------|
| 1    | `V021`, `V035`, `V048`, `V059` | Close stale items or re-scope them in the issue register so later work is not polluted by bad titles  |
| 2    | `V094`, `V046`, `V018`         | Add real storage timeout config, clean stale config keys, document presence flag                      |
| 3    | `V041`, `V088`                 | Consolidate policy ownership for hardcoded thresholds/rules                                           |
| 4    | `V095`, `V005`                 | Fix gender-preference fragility, candidate cache fingerprinting, and large-radius candidate filtering |
| 5    | `V023`                         | Remove runtime null-mode switching from `MatchingService` construction                                |
| 6    | `V051`, `V048`                 | Implement real super-like behavior and only the event changes that behavior truly requires            |
| 7    | `V043`, `V062`                 | Unify CLI login gating and make profile completion edits transactional-at-the-flow level              |
| 8    | `V076`                         | Introduce application-level account-deletion cascade/orchestration                                    |
| 9    | `V082`, `V084`, `V080`         | Land schema hygiene migrations and archive/doc cleanup                                                |
| 10   | `V031`                         | Add targeted missing edge-case tests after behavior changes settle                                    |

## File Map

### Core config and policy files

- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java`
- Modify: `config/app-config.json`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `README.md` or add `docs/configuration.md` if no runtime-config doc exists yet

### Matching / domain files

- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/core/matching/CandidateFinder.java`
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`
- Modify: `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java`
- Modify: `src/main/java/datingapp/core/matching/DefaultStandoutService.java`
- Modify: `src/main/java/datingapp/core/matching/MatchQualityService.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Modify: `src/main/java/datingapp/app/event/AppEvent.java` only if existing `SwipeRecorded` shape is insufficient
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` only if downstream distinction is required

### CLI / use-case files

- Modify: `src/main/java/datingapp/Main.java`
- Modify: `src/main/java/datingapp/app/cli/CliTextAndInput.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MessagingHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MainMenuRegistry.java` only if login-gate ownership is moved
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`

### Storage / schema files

- Modify: `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- Modify: `src/main/java/datingapp/core/storage/InteractionStorage.java`
- Modify: `src/main/java/datingapp/core/storage/TrustSafetyStorage.java`
- Modify: `src/main/java/datingapp/core/storage/UserStorage.java`
- Add: `src/main/java/datingapp/core/storage/AccountCleanupStorage.java`
- Add: `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

### Existing tests to update

- Test: `src/test/java/datingapp/core/UserTest.java`
- Test: `src/test/java/datingapp/core/CandidateFinderTest.java`
- Test: `src/test/java/datingapp/core/MatchingServiceTest.java`
- Test: `src/test/java/datingapp/core/DefaultCompatibilityCalculatorTest.java`
- Test: `src/test/java/datingapp/core/MatchQualityServiceTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`
- Test: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- Test: `src/test/java/datingapp/app/cli/MessagingHandlerTest.java`
- Test: `src/test/java/datingapp/app/cli/UserSessionTest.java`
- Test: `src/test/java/datingapp/app/event/InProcessAppEventBusTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Test: `src/test/java/datingapp/core/AppConfigTest.java`
- Test: `src/test/java/datingapp/core/AppConfigValidatorTest.java`
- Test: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Test: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

### New tests likely needed

- Add: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- Add: `src/test/java/datingapp/storage/jdbi/JdbiAccountCleanupStorageTest.java`
- Add: `src/test/java/datingapp/storage/jdbi/JdbiMetricsStorageTest.java` if none exists and `profile_views` migration needs direct coverage

## Recommended Parallelization

- Worker A owns Tasks 2-3: config surface, stale config cleanup, threshold ownership.
- Worker B owns Tasks 4-6: matching domain, super-like behavior, event narrowing.
- Worker C owns Tasks 7-8: CLI consistency, profile draft persistence, account-deletion cascade orchestration.
- Worker D owns Tasks 9-10: schema/doc hygiene and final targeted edge-case tests.
- Main integrator owns Task 1, integration sequencing, schema migration review, and final `spotless + verify`.

## Chunk 1: Scope Freeze And Policy Surface

### Task 1: Close Or Re-Scope The Stale Issue Titles

**Files:**
- Modify: `current issues/MERGED_CURRENT_ISSUES_2026-03-19.md`
- Verify only: `src/main/java/datingapp/ui/screen/SocialController.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- Verify only: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Verify only: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Verify only: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

- [x] ✅ Confirm from current code/tests that `V021` and `V035` are already implemented and should be closed, not worked around with duplicate UI additions.
- [x] ✅ Confirm from current code/tests that `V059` is historical and only re-open it if a fresh flaky test is reproducible locally.
- [x] ✅ Rewrite the `V048` note so it explicitly says the event system itself exists; the live work item is “add or extend event contracts only when a concrete feature requires them”.
- [x] ✅ Update the issue register entries with a short “current-code reality” note so later implementers do not reintroduce already-shipped UI features.
- [x] ✅ Do not change production code in this task unless the verification disproves the revalidation above.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="SocialControllerTest,SocialViewModelTest,ProfileViewModelTest,ChatViewModelTest" test
```

**Done when:**

- [x] ✅ The issue register no longer suggests fresh implementation work for `V021`, `V035`, or `V059`.
- [x] ✅ `V048` is narrowed to a concrete follow-on contract, not a vague architecture concern.

### Task 2: Add Real Storage Timeout Config, Clean Stale Config Keys, And Document Presence Flag

**Issues:** `V094`, `V046`, `V018`

**Files:**
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `config/app-config.json`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Add or modify: `README.md` or `docs/configuration.md`
- Test: `src/test/java/datingapp/core/AppConfigTest.java`
- Test: `src/test/java/datingapp/core/AppConfigValidatorTest.java`
- Test: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Add: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`

- [x] ✅ Introduce a dedicated storage/database config field for statement/query timeout. Do not hide it inside `SafetyConfig`; give it a storage-specific name and default.
- [x] ✅ Wire the new field from builder defaults, JSON loading, and environment overrides.
- [x] ✅ Apply the timeout centrally in the JDBI/DB setup path. Preferred order:
- [x] ✅ First choice: configure JDBI statement/query timeout globally if the API exists.
- [x] ✅ Fallback: install a central statement customizer that calls JDBC `setQueryTimeout(...)`.
- [x] ✅ Keep Hikari `connectionTimeout` / `validationTimeout` separate; do not rename them as transaction timeouts.
- [x] ✅ Reconcile `config/app-config.json` with `AppConfig.Builder`. Remove or rename stale keys currently ignored by Jackson, especially `dailyPickCount`, `sessionRetentionDays`, `standoutRefreshHours`, and any old aliases that no longer map to the builder.
- [x] ✅ Tighten config hygiene:
- [x] ✅ Preferred: surface unknown config keys as a startup failure or clearly logged warning.
- [x] ✅ Acceptable fallback: keep lenient loading but add a test that enumerates the currently allowed flat keys so drift is caught.
- [x] ✅ Document the presence feature flag. Capture the exact flag key `datingapp.ui.presence.enabled`, default-disabled behavior, and current effect in the UI.
- [x] ✅ Keep `V018` docs-only in this batch. Do not migrate the flag into `AppConfig` unless a later task proves that system-property wiring is actively harmful.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="AppConfigTest,AppConfigValidatorTest,ConfigLoaderTest,ViewModelFactoryTest" test
```

**Done when:**

- [x] ✅ A runtime-configurable storage/query timeout exists and is actually propagated.
- [x] ✅ `config/app-config.json` contains only supported keys or the loader now detects unsupported keys.
- [x] ✅ Presence flag behavior is documented in a runtime-facing doc, not only audit artifacts.

### Task 3: Consolidate Policy Ownership For Thresholds And Hardcoded Rules

**Issues:** `V041`, `V088`

**Files:**
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java`
- Modify: `src/main/java/datingapp/core/matching/DefaultStandoutService.java`
- Modify: `src/main/java/datingapp/core/matching/MatchQualityService.java`
- Modify: `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `config/app-config.json`
- Test: `src/test/java/datingapp/core/DefaultCompatibilityCalculatorTest.java`
- Test: `src/test/java/datingapp/core/MatchQualityServiceTest.java`
- Test: `src/test/java/datingapp/core/AppConfigTest.java`

- [x] ✅ Audit every hardcoded rule touched by the requested issues and classify it into one of two buckets:
- [x] ✅ Product/deployment policy that belongs in config.
- [x] ✅ Internal implementation constant that should remain code-local.
- [x] ✅ Move the following policy-facing values behind one explicit owner:
- [x] ✅ Match-quality star bands currently hardcoded in `MatchQualityService`.
- [x] ✅ Standout diversity window and standout-selection cutoffs in `DefaultStandoutService`.
- [x] ✅ Compatibility activity recency buckets only if they are meant to be deployment-tunable; otherwise leave them in one calculator-owned policy object and remove stale “from RecommendationService” comments.
- [x] ✅ Eliminate fake configurability where the entity cannot honor per-deployment values. Specifically resolve the `User.MAX_PHOTOS` vs config drift by either:
- [x] ✅ Making the config value match the entity invariant and validating equality, or
- [x] ✅ Removing the tunable config surface if dynamic photo limits are not actually supported.
- [x] ✅ Update comments so policy ownership is obvious. No class should claim thresholds come “from” a different service when they do not.
- [x] ✅ Keep this task narrow: do not refactor every score calculation in the app. Only relocate or centralize thresholds that are part of the requested issue set.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="AppConfigTest,DefaultCompatibilityCalculatorTest,MatchQualityServiceTest" test
```

**Done when:**

- [x] ✅ Threshold ownership is explicit.
- [x] ✅ The app no longer has stale comments implying duplicated policy owners.
- [x] ✅ Config now contains only thresholds the runtime is expected to tune.

## Chunk 2: Matching And CLI Behavior

### Task 4: Fix Gender-Preference Fragility, Candidate Fingerprinting, And Large-Radius Filtering

**Issues:** `V095`, `V005`

**Files:**
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/core/matching/CandidateFinder.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Test: `src/test/java/datingapp/core/UserTest.java`
- Test: `src/test/java/datingapp/core/CandidateFinderTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`

- [x] ✅ Introduce one explicit source of truth for “matchable genders” and make `User.isInterestedInEveryone()` use that explicit set instead of `EnumSet.allOf(Gender.class)`.
- [x] ✅ Update `CandidateFinder` to use the same policy helper so gender matching cannot silently drift from `User`.
- [x] ✅ Expand candidate-cache fingerprinting so it changes when matching-relevant user state changes. At minimum include:
- [x] ✅ interested-in set
- [x] ✅ dealbreakers
- [x] ✅ lifestyle fields used by filtering
- [x] ✅ pace preferences if the candidate list depends on them
- [x] ✅ Keep the fingerprint cheap and stable; prefer deterministic string/value-object composition rather than ad-hoc `toString()` of mutable collections.
- [x] ✅ Fix `JdbiUserStorage.findCandidates(...)` so very large radii do not bypass all DB-side bounding behavior. Preserve “global” behavior if intended, but keep at least one DB-side narrowing condition instead of falling back to an unconstrained scan.
- [x] ✅ Add one normalization regression test that proves scalar dealbreaker columns plus normalized dealbreaker tables still compose into one correct in-memory `Dealbreakers` object after load.
- [x] ✅ Update the issue register notes for `V005` to reflect the new narrowed reality once the fix lands.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="UserTest,CandidateFinderTest,JdbiUserStorageNormalizationTest" test
```

**Done when:**

- [x] ✅ A future `Gender` enum addition cannot silently redefine “everyone”.
- [x] ✅ Candidate caches invalidate when preference/filter inputs change.
- [x] ✅ Large-radius candidate queries remain bounded and tested.

### Task 5: Remove Runtime Null-Mode Switching From MatchingService Construction

**Issue:** `V023`

**Files:**
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: any direct `MatchingService.builder()` call sites in tests
- Test: `src/test/java/datingapp/core/MatchingServiceTest.java`

- [x] ✅ Decide which dependencies are mandatory for the public API actually used in production. Minimum expected mandatory set: `interactionStorage`, `trustSafetyStorage`, `userStorage`, `undoService`, `dailyService`, `candidateFinder`.
- [x] ✅ Keep `activityMetricsService` optional only if the service can genuinely operate without it and no config-error branch is needed.
- [x] ✅ Make the builder fail fast at construction time when required dependencies are missing.
- [x] ✅ Remove `processSwipe(...)` configuration-error branches caused by missing collaborators.
- [x] ✅ Remove silent cache-invalidation no-op behavior caused by missing `candidateFinder`.
- [x] ✅ Update `StorageFactory` so production wiring always constructs a fully usable service.
- [x] ✅ Replace old tests that asserted “config error when dependencies are missing” with construction-time validation tests.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="MatchingServiceTest" test
```

**Done when:**

- [x] ✅ `MatchingService` no longer has runtime mode-switching based on null collaborators.
- [x] ✅ Broken construction fails immediately and predictably.

### Task 6: Implement Real Super-Like Behavior And Only The Event Changes It Requires

**Issues:** `V051`, `V048`

**Files:**
- Modify: `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java` or daily-limit abstraction as needed
- Modify: `src/main/java/datingapp/app/event/AppEvent.java` only if `SwipeRecorded` cannot carry the new distinction
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` only if product behavior needs distinct handling
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`
- Test: matching use-case tests near `MatchingUseCasesTest.java`

- [x] ✅ Add a real `SUPER_LIKE` direction to `ConnectionModels.Like.Direction`.
- [x] ✅ Thread that direction end-to-end through the matching use-case and persistence path.
- [x] ✅ Add a distinct `MatchingViewModel.superLike()` action and wire `MatchingController.handleSuperLike()` to call it instead of `like()`.
- [x] ✅ Honor the already-existing daily super-like limit surface (`dailySuperLikeLimit`) rather than treating super-like as a cosmetic alias.
- [x] ✅ Decide whether a super-like should still create a match on reciprocity exactly like a normal like. Default assumption: yes, but analytics/notifications should still know it was a super-like.
- [x] ✅ Prefer reusing `AppEvent.SwipeRecorded` with a richer direction value. Only add a separate event class if downstream handlers cannot reasonably distinguish `SUPER_LIKE`.
- [x] ✅ Update user feedback text and controller animations so the action is visibly distinct from a normal like.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="MatchingViewModelTest,MatchingControllerTest,MatchingUseCasesTest" test
```

**Done when:**

- [x] ✅ Super-like is a real behavior with its own direction and limit handling.
- [x] ✅ The event story is concrete and minimal, not speculative.

### Task 7: Unify CLI Login Gates And Make Profile Completion Flow Persist Safely

**Issues:** `V043`, `V062`

**Files:**
- Modify: `src/main/java/datingapp/Main.java`
- Modify: `src/main/java/datingapp/app/cli/CliTextAndInput.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MessagingHandler.java`
- Modify: `src/main/java/datingapp/core/model/User.java` if a copy/snapshot helper is added
- Test: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- Test: `src/test/java/datingapp/app/cli/MessagingHandlerTest.java`
- Test: `src/test/java/datingapp/app/cli/UserSessionTest.java`

- [x] ✅ Make `CliTextAndInput.requireLogin(...)` the single handler-level gate for logged-in actions.
- [x] ✅ Keep `Main` / `MainMenuRegistry` login checks only as UX prechecks if desired, but route them through one shared message constant/helper so output is consistent.
- [x] ✅ Remove direct `session.getCurrentUser() == null` branches that print their own text in handlers.
- [x] ✅ Refactor `ProfileHandler.completeProfile()` to edit a draft copy of the current user, not the live in-session mutable instance.
- [x] ✅ Add a safe copy strategy for `User` that preserves mutable collections, dealbreakers, interests, pace preferences, and timestamps without aliasing the original collections.
- [x] ✅ Save the draft once at the end through `ProfileUseCases.saveProfile(...)`.
- [x] ✅ Only after a successful save should the session’s current user reference be updated to the saved draft/current result.
- [x] ✅ Leave standalone flows like `setDealbreakers()` alone unless they are explicitly part of the chosen persistence semantics. This task is about `completeProfile()` mutating live state before persistence.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="ProfileHandlerTest,MessagingHandlerTest,UserSessionTest" test
```

**Done when:**

- [x] ✅ CLI login failures are handled consistently.
- [x] ✅ Interrupted or failed profile-completion flows do not mutate persisted or in-session user state unexpectedly.

## Chunk 3: Data Consistency, Schema Hygiene, And Final Coverage

### Task 8: Add Application-Level Account-Deletion Cascade Orchestration

**Issue:** `V076`

**Files:**
- Add: `src/main/java/datingapp/core/storage/AccountCleanupStorage.java`
- Add: `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- Add: `src/test/java/datingapp/storage/jdbi/JdbiAccountCleanupStorageTest.java`

- [x] ✅ Introduce one storage-level orchestration seam for user-graph cleanup instead of scattering account-deletion SQL across use-cases.
- [x] ✅ Implement the default policy from the top of this plan:
- [x] ✅ soft-delete `users`
- [x] ✅ soft-delete `likes`
- [x] ✅ soft-delete `matches`
- [x] ✅ soft-delete `conversations`
- [x] ✅ soft-delete `messages`
- [x] ✅ soft-delete `profile_notes`
- [x] ✅ soft-delete `blocks`
- [x] ✅ soft-delete `reports`
- [x] ✅ Keep the entire cleanup in one transaction.
- [x] ✅ Update `ProfileUseCases.deleteAccount()` to call the orchestration seam rather than only `user.markDeleted(...)`.
- [x] ✅ Audit read paths after the cascade so soft-deleted artifacts are no longer surfaced. Favor query filtering over new schema columns for non-`deleted_at` tables in this batch.
- [x] ✅ Use `AppClock.now()` once per deletion request and propagate the same timestamp through the cascade so tests can assert consistently.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="ProfileUseCasesTest,SafetyViewModelTest,JdbiAccountCleanupStorageTest" test
```

**Done when:**

- [x] ✅ Deleting an account no longer leaves active user-facing artifacts behind in tables that already support soft-delete.
- [x] ✅ The cascade is transactional and timestamp-consistent.

### Task 9: Land Schema And Archive Hygiene Changes

**Issues:** `V082`, `V084`, `V080`

**Files:**
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- Modify: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- Modify or add: tests for `JdbiMetricsStorage`
- Delete or move: `docs/archived-utils/AnimationHelper.java`
- Delete or move: `docs/archived-utils/AsyncExecutor.java`
- Delete or move: `docs/archived-utils/ButtonFeedback.java`
- Add: `docs/archive/archived-utils.md` or equivalent markdown replacement

- [x] ✅ Add a migration for `profile_views` that removes the unused surrogate `id` column.
- [x] ✅ Default schema choice for this plan: rebuild `profile_views` with primary key `(viewer_id, viewed_id, viewed_at)` so repeated views remain representable without a meaningless surrogate key.
- [x] ✅ Update fresh-install schema in `SchemaInitializer` after the migration exists.
- [x] ✅ Align `JdbiMetricsStorage` SQL with the migrated table definition and verify it never relies on the old surrogate key (already aligned; verified by focused/full tests).
- [x] ✅ Normalize constraint naming on the tables touched in this batch, and at minimum fix the known inconsistency between `uk_*` and `unq_conversation_users`.
- [x] ✅ If H2 cannot rename constraints directly, rebuild the affected tables or drop/re-add the constraints through migration SQL. Do not fake consistency only in comments.
- [x] ✅ Remove raw `.java` files from `docs/archived-utils/` so repo tooling and humans do not confuse them with active source.
- [x] ✅ Preserve their content in markdown or a non-source archive location if you want to keep the reference material.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="SchemaInitializerTest" test
```

**Done when:**

- [x] ✅ Fresh schema and migrated schema converge on the same `profile_views` shape.
- [x] ✅ Constraint naming is no longer obviously inconsistent on the touched tables.
- [x] ✅ No raw `.java` files remain under the docs archive path.

### Task 10: Close The Remaining Edge-Case Test Gaps

**Issue:** `V031`

**Files:**
- Modify: `src/test/java/datingapp/app/cli/UserSessionTest.java`
- Modify: `src/test/java/datingapp/app/event/InProcessAppEventBusTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Modify: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Modify: any new tests introduced by Tasks 2-9

- [x] ✅ Add `AppSession` tests for listener exception isolation and listener add/remove during notification if they are not already covered.
- [x] ✅ Add `InProcessAppEventBus` tests for subscribe-during-publish or related registration edge cases without weakening required-handler semantics.
- [x] ✅ Add REST tests for invalid enum values / malformed bodies / rate-limit window edges if still missing after the current suite is reviewed.
- [x] ✅ Add config-loading tests for invalid timezone or unknown-key behavior, whichever path Task 2 chose.
- [x] ✅ Keep this task test-first and test-only unless a newly written test exposes a real bug that earlier tasks did not cover.

**Verification:**

```bash
mvn -Ptest-output-verbose -Dtest="UserSessionTest,InProcessAppEventBusTest,RestApiRelationshipRoutesTest,ConfigLoaderTest" test
```

**Done when:**

- [x] ✅ The remaining coverage gap is specific and documented, not a vague “missing concurrency tests” complaint.

## Integration Sequence

- [x] ✅ Implement Task 1 first so stale issue titles stop distorting the remaining work.
- [x] ✅ Implement Tasks 2 and 3 before Tasks 4-6, because matching fixes depend on stable config/policy ownership.
- [x] ✅ Implement Task 5 before Task 6 if super-like behavior will reuse `MatchingService` constructors/builders.
- [x] ✅ Implement Task 7 before Task 8 only if `ProfileUseCases.deleteAccount()` and CLI session mutation work are being touched by the same worker; otherwise they can proceed in parallel.
- [x] ✅ Implement Task 9 after Task 8 if account-cleanup SQL needs the new schema conventions; otherwise Task 9 can run in parallel as long as the write set is coordinated carefully.
- [x] ✅ Run Task 10 after the behavior-changing tasks so test gaps are filled against the final behavior, not a moving target.

## Final Verification

- [x] ✅ Run focused tests after each task, not only at the end.
- [x] ✅ Run the full verification gate after integration:

```bash
mvn -Ptest-output-verbose test
mvn spotless:apply verify
```

- [x] ✅ If migration-heavy work causes failures only on upgraded databases, add or run a migration-path test against a pre-migration schema snapshot before declaring success.
- [x] ✅ After all code/tests/docs land, update the issue register entries covered by this plan so the repo does not keep advertising stale problems.

## Commit Boundaries

- [ ] Commit 1: issue-register scope corrections (`V021`, `V035`, `V048`, `V059`)
- [ ] Commit 2: config/timeouts/docs (`V094`, `V046`, `V018`)
- [ ] Commit 3: policy-threshold ownership (`V041`, `V088`)
- [ ] Commit 4: candidate-selection robustness (`V095`, `V005`)
- [ ] Commit 5: `MatchingService` constructor hardening (`V023`)
- [ ] Commit 6: super-like implementation (`V051`, concrete `V048` follow-on if needed)
- [ ] Commit 7: CLI gate + profile draft persistence (`V043`, `V062`)
- [ ] Commit 8: account-deletion cascade (`V076`)
- [ ] Commit 9: schema/archive hygiene (`V082`, `V084`, `V080`)
- [ ] Commit 10: residual edge-case tests (`V031`)

Plan complete and saved to `docs/superpowers/plans/2026-03-22-open-issue-batch-implementation-plan.md`. Ready to execute?
