# CAST Structural Remediation Implementation Plan

> **For agentic workers:** REQUIRED: Use `@subagent-driven-development` (if subagents are available) or `@executing-plans` to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking. Treat `2026-03-31-cast-structural-quality-advisor-report.md` as the approved design/spec input. Use `@test-driven-development`, `@dispatching-parallel-agents` (read-only and truly isolated implementation work only), and `@verification-before-completion` throughout execution.

**Goal:** Implement the structural remediation program from the CAST advisor report end to end, reducing boundary drift, clarifying composition seams, and improving high-value efficiency/security concerns while preserving current behavior except where the plan explicitly introduces a reviewed security hardening change.

**Architecture:** Execute the work in behavior-preserving layers. First, freeze current behavior with characterization tests. Next, extract and simplify boundary/composition responsibilities behind helpers and façades without changing public contracts. Then split the profile boundary and narrow stale constructors using compatibility shims. Only after those contracts are stable should the plan optimize storage/UI read paths and simplify broad UI wiring. The one intentionally behavior-changing item—optional local REST authentication hardening—sits behind an explicit decision gate near the end.

**Tech Stack:** Java 25 (preview), JavaFX 25, Javalin 6, H2, HikariCP, JDBI 3, Jackson, JUnit 5, Maven, Spotless, Checkstyle, PMD, JaCoCo.

---

## Scope, assumptions, and invariants

- The approved source-driven spec for this plan is `2026-03-31-cast-structural-quality-advisor-report.md`.
- Code is the only source of truth; stale docs are not authoritative.
- The plan is optimized for a **coordinator agent** managing shared files and sequencing, with focused subagents handling isolated tasks.
- Preserve current route paths, DTO shapes, nested command/query symbols, and public method contracts until the plan explicitly says otherwise.
- Prefer temporary façades, overloads, and shims over broad renames.
- Do **not** change the database schema unless a later reviewed decision makes it unavoidable. This plan is designed to avoid migrations.
- Do **not** remove compatibility shims in the same phase they are introduced unless all direct callers and tests are already migrated and the full quality gate is green.
- Keep `core/` framework-free and keep `app/usecase/*` as the application boundary.

## Recommended execution mode for AI agents

### Coordinator model

One coordinator agent should own:

- shared files touched by multiple phases,
- public interfaces,
- sequencing,
- final merges of subagent work,
- verification order.

In this repository, the coordinator should assume ownership of these recurring shared files:

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/test/java/datingapp/app/api/RestApiTestFixture.java`

### Safe parallelization rules

**Safe to parallelize:**

- read-only context gathering,
- documentation review,
- isolated test-file additions when they do not touch the same production files,
- post-interface-freeze consumer migrations that touch disjoint files.

**Do not parallelize:**

- phases that touch `RestApiServer`, `StorageFactory`, `ServiceRegistry`, `ViewModelFactory`, or `ProfileUseCases` at the same time,
- any work that changes a constructor or nested symbol consumed across multiple surfaces,
- any behavior-changing security work until characterization tests are green.

### Agent workflow requirements

- Use `@test-driven-development` inside each task.
- Prefer `ast-grep`/symbol-aware search before edits.
- Re-read the touched files after every meaningful edit.
- Run targeted tests after each task.
- Run `mvn spotless:apply verify` before calling the program complete.

## Context map by phase

| Phase                                       | Main files                                                                                                                                                 | Key dependencies                                                                                                              | Primary tests                                                                                                                                                                                      |
|---------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0. Behavior safety net                      | `RestApiServer.java`, `ServiceRegistry.java`, `ApplicationStartup.java`, selected tests                                                                    | REST routes, bootstrap, composition                                                                                           | `RestApi*Test`, `ServiceRegistryTest`, `ApplicationStartupBootstrapTest`, DB manager tests                                                                                                         |
| 1. REST boundary extraction                 | `RestApiServer.java`, new REST helper classes                                                                                                              | `MatchingUseCases`, `MessagingUseCases`, `SocialUseCases`, `ProfileUseCases`, `LocationService`                               | `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, `RestApiPhaseTwoRoutesTest`, `RestApiRateLimitTest`, `RestApiDailyLimitTest`, `RestApiConversationBatchCountTest`                        |
| 2. Composition spine clarification          | `StorageFactory.java`, `ServiceRegistry.java`, `ApplicationStartup.java`, `DatabaseManager.java`, `Main.java`, `DatingApp.java`, `RestApiTestFixture.java` | JDBI storages, event bus, use-case construction, launcher lifecycle                                                           | `ServiceRegistryTest`, `ApplicationStartupBootstrapTest`, `ConfigLoaderTest`, `DatabaseManagerConfigurationTest`, `DatabaseManagerThreadSafetyTest`, `MatchingFlowIntegrationTest`                 |
| 3. Profile boundary decomposition           | `ProfileUseCases.java`, new profile slice use-case files, `UiDataAdapters.java`, `RestApiServer.java`, CLI/UI consumers                                    | `ProfileService`, `ValidationService`, `ActivityMetricsService`, `AchievementService`, `AccountCleanupStorage`, `AppEventBus` | `ProfileUseCasesTest`, `ProfileUseCasesNotesTest`, `RestApiNotesRoutesTest`, `ProfileViewModelTest`, `NotesViewModelTest`, `StatsViewModelTest`, `PreferencesViewModelTest`, `SafetyViewModelTest` |
| 4. Profile service contract correction      | `ProfileService.java`, `StorageFactory.java`, `ServiceRegistry.java`, `RestApiTestFixture.java`, integration fixtures                                      | `DefaultAchievementService`, `DefaultStandoutService`, profile consumers                                                      | `ProfileCompletionServiceTest`, `ProfileServiceBatchLookupTest`, `ProfileUseCasesTest`, `ServiceRegistryTest`                                                                                      |
| 5. Storage efficiency pass                  | `JdbiUserStorage.java` (+ helper extraction only if truly needed)                                                                                          | `UserStorage` consumers across matching/profile/messaging                                                                     | `JdbiUserStorageNormalizationTest`, `JdbiUserStorageMigrationTest`, `RestApiReadRoutesTest`, relevant profile tests                                                                                |
| 6. Async and messaging read-path refinement | `ViewModelAsyncScope.java`, `MessagingUseCases.java`, `ConnectionService.java`, `ChatViewModel.java`, `MatchingViewModel.java`                             | chat polling, matching undo polling, conversation preview flow                                                                | `ViewModelAsyncScopeTest`, `ChatViewModelTest`, `MatchingViewModelTest`, `MessagingUseCasesTest`, `ConnectionServiceAtomicityTest`                                                                 |
| 7. ViewModelFactory simplification          | `ViewModelFactory.java`, maybe `DatingApp.java`, maybe `NavigationService.java`                                                                            | all JavaFX controllers/ViewModels                                                                                             | `ViewModelArchitectureConsistencyTest`, `BaseViewModelTest`, `ProfileViewModelTest`, `ChatViewModelTest`, `StatsViewModelTest`, `NotesViewModelTest`                                               |
| 8. Decision-gated local REST auth hardening | `RestApiServer.java`, possibly new auth helper, `AppConfig.java`, `AppConfigValidator.java`, `config/app-config.json`, config tests                        | REST callers, config loading/validation                                                                                       | REST route tests + config loader/validator tests                                                                                                                                                   |
| 9. Final cross-phase verification           | all touched files                                                                                                                                          | all touched subsystems                                                                                                        | targeted suites + `mvn spotless:apply verify`                                                                                                                                                      |

## Lowest-risk global order of execution

1. **Freeze behavior with tests.**
2. **Extract REST boundary policy helpers without changing route contracts.**
3. **Clarify the composition spine while keeping public factory/registry entrypoints stable.**
4. **Split the broad profile application boundary behind a compatibility façade.**
5. **Narrow `ProfileService` after the broader profile split has settled.**
6. **Optimize `JdbiUserStorage` read paths without changing schema or `UserStorage` contracts.**
7. **Refine UI polling and conversation-preview retrieval after lower-level seams are stable.**
8. **Simplify `ViewModelFactory` last, after constructor/use-case churn has stopped.**
9. **Apply optional loopback auth hardening only after the behavior-preserving phases are green and the user accepts the local-client contract change.**
10. **Run the full quality gate and remove only the shims proven safe to remove.**

## Decision gates

### D1 — REST hardening contract change

Before Phase 8, the coordinator must decide one of the following and record it in the working notes:

- **Option A (recommended if local process trust matters):** add config-driven local shared-secret or token authentication on top of loopback-only enforcement.
- **Option B:** keep loopback-only + `X-User-Id` model and treat the remaining issue as an accepted local IPC risk.

Do **not** silently implement Option A without acknowledging the local client contract change.

**Decision recorded (2026-03-31):** ✅ **Option B selected** for this implementation run. Phase 8 local REST auth hardening will be skipped, and the remaining local IPC risk is accepted explicitly for now.

### D2 — Compatibility façade removal

At the end of Phases 3 and 4, decide whether to:

- keep compatibility façade/overload layers for hidden-test safety, or
- remove them only if all repo callers have migrated and the full quality gate passes.

Default to **keeping** the façade/overload through the end of the plan unless there is strong evidence removal is safe.

## Chunk 1: Guardrails and behavior freeze

### Task 1: Lock current REST boundary behavior with characterization tests

**Files:**
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiDailyLimitTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRoutesTest.java`

- [x] **Step 1: Add or tighten tests for loopback-only enforcement, acting-user/path mismatch, conversation participant rejection, and the `/api/users/{id}/candidates` direct-read exception.** ✅ Added characterization coverage in `RestApiHealthRoutesTest`, `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, and `RestApiPhaseTwoRoutesTest`.
- [x] **Step 2: Add or tighten assertions for existing rate-limit headers and status code mapping so helper extraction cannot silently drift behavior.** ✅ Tightened `RestApiRateLimitTest` to assert the `TOO_MANY_REQUESTS` error body code in addition to the existing headers.
- [x] **Step 3: Run targeted REST tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=RestApiHealthRoutesTest,RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiPhaseTwoRoutesTest,RestApiNotesRoutesTest,RestApiConversationBatchCountTest,RestApiRateLimitTest,RestApiDailyLimitTest test`
  - Expected: PASS with no route, status-code, or header regressions.
  - Result (2026-03-31): ✅ PASS — 40 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 4: Commit the characterization-only test changes.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 2: Lock composition-spine invariants before refactoring wiring

**Files:**
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`
- Modify: `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`
- Modify: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Modify: `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`
- Modify: `src/test/java/datingapp/storage/DatabaseManagerThreadSafetyTest.java`
- Modify: `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

- [x] **Step 1: Add or tighten tests that freeze current graph invariants: registry construction, bootstrap loading, DB profile rules, and graph usability through the matching flow integration seam.** ✅ Added lifecycle/config guardrails in `ApplicationStartupBootstrapTest`, an additional DB profile characterization in `DatabaseManagerConfigurationTest`, and graph-usability characterization in `ServiceRegistryTest`.
- [x] **Step 2: Prefer tests that verify externally observable graph behavior, not internal method names.** ✅ Added registry storage-to-use-case round-trip coverage and usable location-resolution coverage; bootstrap tests now pin public lifecycle behavior.
- [x] **Step 3: Run targeted composition tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ServiceRegistryTest,ApplicationStartupBootstrapTest,ConfigLoaderTest,DatabaseManagerConfigurationTest,DatabaseManagerThreadSafetyTest,MatchingFlowIntegrationTest test`
  - Expected: PASS; these become the guardrails for Phases 2–4.
  - Result (2026-03-31): ✅ PASS — 56 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 4: Commit the characterization-only composition tests.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Chunk 2: REST boundary extraction and composition spine clarification

### Task 3: Extract REST request-guard and identity policy helpers with zero contract change

**Files:**
- Create: `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- Create: `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`

- [x] **Step 1: Write failing tests only if Task 1 did not already freeze a behavior that the extraction could change.** ✅ Existing Task 1 characterization coverage already pinned the affected behavior, so no additional tests were needed before extraction.
- [x] **Step 2: Move localhost-only, rate-limit, acting-user resolution, scoped identity, and conversation participant checks into helper classes without changing paths, verbs, headers, or status codes.** ✅ Extracted `RestApiRequestGuards` and `RestApiIdentityPolicy`, and rewired `RestApiServer` through delegating compatibility methods.
- [x] **Step 3: Keep the direct-read `/candidates` exception explicit and behavior-preserving.** ✅ `readCandidateSummaries(...)` remains explicit in `RestApiServer` as the documented direct-read exception.
- [x] **Step 4: Re-run the targeted REST suite.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiPhaseTwoRoutesTest,RestApiConversationBatchCountTest,RestApiRateLimitTest,RestApiDailyLimitTest test`
  - Expected: PASS with no transport contract drift.
  - Result (2026-03-31): ✅ PASS — 36 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the helper extraction.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 4: Clarify the composition spine without breaking the public entrypoints

**Files:**
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Modify: `src/main/java/datingapp/storage/DatabaseManager.java` (only if strictly needed)
- Modify: `src/main/java/datingapp/Main.java`
- Modify: `src/main/java/datingapp/ui/DatingApp.java`
- Modify: `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`

- [x] **Step 1: Refactor `StorageFactory` internally so DB/JDBI assembly, domain-service assembly, event-handler registration, and final registry construction are more explicit, while keeping the public `buildH2(DatabaseManager, AppConfig)` entrypoint stable.** ✅ Split `StorageFactory` into explicit private assembly stages and internal records without changing the public entrypoint.
- [x] **Step 2: Make `ServiceRegistry` more explicit about its dependencies; inject `LocationService` through the builder or a compatibility default so object creation is no longer hidden in the registry constructor.** ✅ Added optional `Builder.locationService(...)` support with a compatibility fallback to `new LocationService(validationService)`.
- [x] **Step 3: Keep `ApplicationStartup`, `Main`, and `DatingApp` as thin lifecycle shells that still converge on the same startup/shutdown semantics.** ✅ No launcher changes were needed; the lifecycle shells stayed stable.
- [x] **Step 4: Update shared test fixtures (`RestApiTestFixture`, integration builders) only after the production graph compiles and the builder contract is stable.** ✅ Updated `RestApiTestFixture` and `MatchingFlowIntegrationTest` to pass explicit `LocationService` instances through the registry builder.
- [x] **Step 5: Run targeted composition tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ServiceRegistryTest,ApplicationStartupBootstrapTest,ConfigLoaderTest,DatabaseManagerConfigurationTest,DatabaseManagerThreadSafetyTest,MatchingFlowIntegrationTest,RestApiReadRoutesTest test`
  - Expected: PASS; launcher and REST fixture paths still use a coherent graph.
  - Result (2026-03-31): ✅ PASS — 64 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 6: Commit the composition-spine cleanup.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Chunk 3: Profile boundary decomposition

### Task 5: Extract profile notes into a dedicated use-case slice behind a façade

**Files:**
- Create: `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiNotesRoutesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/NotesViewModelTest.java`

- [x] **Step 1: Add failing tests or strengthen existing note-specific tests so note CRUD behavior is fully frozen before extraction.** ✅ Existing note-specific tests already froze the note CRUD contract tightly enough, so no extra characterization tests were needed before extraction.
- [x] **Step 2: Introduce `ProfileNotesUseCases` and move note logic there first; keep `ProfileUseCases` delegating to it so existing callers and hidden tests keep working.** ✅ Added `ProfileNotesUseCases` and rewired `ProfileUseCases` note methods to delegate through a shared note-slice instance.
- [x] **Step 3: Migrate note-specific adapters (`UiDataAdapters`, notes UI, CLI notes handler, REST note routes) to the dedicated slice once the façade is in place.** ✅ Migrated `UiDataAdapters`, `NotesViewModel`, `ViewModelFactory`, `RestApiServer`, and the real CLI note consumer in `ProfileHandler` (the plan’s `ProfileNotesHandler` reference was stale in current source).
- [x] **Step 4: Re-run focused note and profile-adjacent tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesNotesTest,RestApiNotesRoutesTest,NotesViewModelTest test`
  - Expected: PASS; note behavior and DTO shapes are unchanged.
  - Result (2026-03-31): ✅ PASS — 8 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the note-slice extraction.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 6: Extract profile insights (stats, achievements, session summary) into a dedicated read-only slice

**Files:**
- Create: `src/main/java/datingapp/app/usecase/profile/ProfileInsightsUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`

- [x] **Step 1: Add or tighten tests around stats, achievements, and session-summary reads.** ✅ Existing coverage in `ProfileUseCasesTest`, `StatsViewModelTest`, and `RestApiPhaseTwoRoutesTest` already froze the relevant read-only behavior tightly enough before extraction.
- [x] **Step 2: Introduce `ProfileInsightsUseCases`; move the read-only logic there while leaving the original nested query/result types reachable through `ProfileUseCases` delegation during migration.** ✅ Added `ProfileInsightsUseCases` and rewired the legacy `ProfileUseCases` insights methods to delegate through compatibility conversions.
- [x] **Step 3: Update Stats-focused consumers to use the dedicated slice once the façade delegation is proven.** ✅ Migrated `StatsViewModel`, `ViewModelFactory`, `RestApiServer`, and `StatsHandler` to the dedicated slice while preserving compatibility overloads where needed.
- [x] **Step 4: Run focused insights tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,StatsViewModelTest,RestApiPhaseTwoRoutesTest test`
  - Expected: PASS; no change in stats/achievement payloads or event expectations.
  - Result (2026-03-31): ✅ PASS — 36 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the insights extraction.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 7: Extract profile mutation/account lifecycle logic into a dedicated slice while keeping `ProfileUseCases` as the compatibility façade

**Files:**
- Create: `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/app/cli/SafetyHandler.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`

- [x] **Step 1: Freeze update/save/delete-account behavior with tests if any mutation path is still weakly specified.** ✅ Existing coverage in `ProfileUseCasesTest`, `ProfileViewModelTest`, `PreferencesViewModelTest`, `SafetyViewModelTest`, `RestApiReadRoutesTest`, and `RestApiPhaseTwoRoutesTest` already froze the mutation/account-lifecycle behavior tightly enough before extraction.
- [x] **Step 2: Move mutation/account lifecycle logic into `ProfileMutationUseCases`, preserving sanitization, activation, event publication, and deletion behavior exactly.** ✅ Added `ProfileMutationUseCases` and moved save/update/delete-account behavior into the dedicated mutation slice while preserving the existing rules and event semantics.
- [x] **Step 3: Keep `ProfileUseCases` as a thin compatibility façade delegating to notes, insights, and mutation slices. Do not remove nested symbols yet.** ✅ `ProfileUseCases` now delegates across `ProfileMutationUseCases`, `ProfileNotesUseCases`, and `ProfileInsightsUseCases` while keeping the legacy public API and nested symbols intact.
- [x] **Step 4: Migrate direct mutation consumers (`ProfileViewModel`, `PreferencesViewModel`, `SafetyViewModel`, CLI, REST) only after the façade layer is green.** ✅ Migrated `ProfileViewModel`, `PreferencesViewModel`, `SafetyViewModel`, `ProfileHandler`, `RestApiServer`, and `ViewModelFactory`; `SafetyHandler` required no change because current source has no direct mutation call sites there.
- [x] **Step 5: Run focused mutation tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ProfileViewModelTest,PreferencesViewModelTest,SafetyViewModelTest,RestApiReadRoutesTest,RestApiPhaseTwoRoutesTest test`
  - Expected: PASS; profile mutation flows behave exactly as before.
  - Result (2026-03-31): ✅ PASS — 58 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 6: Commit the mutation-slice extraction.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Chunk 4: Profile service contract correction and storage efficiency

### Task 8: Narrow `ProfileService` to its real responsibilities using compatibility overloads

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- Modify: `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`
- Test: `src/test/java/datingapp/core/ProfileCompletionServiceTest.java`
- Test: `src/test/java/datingapp/core/ProfileServiceBatchLookupTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`

- [x] **Step 1: Add a narrow constructor (or extracted dedicated completion service) that matches what `ProfileService` actually uses.** ✅ Added the narrow `ProfileService(UserStorage)` constructor that matches the implementation’s real dependency surface.
- [x] **Step 2: Keep a compatibility overload temporarily so the graph and tests do not all break at once.** ✅ Preserved the legacy wide constructor as a compatibility overload delegating to the narrow constructor after its null-check gate.
- [x] **Step 3: Update production and shared test fixtures to the narrow constructor first; migrate direct test call sites afterward.** ✅ Updated `StorageFactory`, `RestApiTestFixture`, and `MatchingFlowIntegrationTest` first, then migrated the focused direct `ProfileService` tests.
- [x] **Step 4: Re-run focused profile/core composition tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ProfileCompletionServiceTest,ProfileServiceBatchLookupTest,ProfileUseCasesTest,ServiceRegistryTest test`
  - Expected: PASS; completion and lookup behavior stay stable while constructor truthfulness improves.
  - Result (2026-03-31): ✅ PASS — 70 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the `ProfileService` contract correction.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 9: Optimize `JdbiUserStorage` batch hydration without changing schema or `UserStorage` contracts

**Files:**
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java` (only if a helper extraction genuinely improves clarity)
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`

- [x] **Step 1: Add failing or characterization tests only if the current storage coverage does not fully lock the normalized hydration behavior and page ordering.** ✅ Tightened `JdbiUserStorageNormalizationTest` to assert the reduced batch-read count for `findByIds(...)` and added explicit `findCandidates(...)` normalized-hydration coverage.
- [x] **Step 2: Reduce repeated normalized-table hydration work in `applyNormalizedProfileDataBatch(...)` and related page/query paths, keeping result ordering and semantics unchanged.** ✅ Consolidated normalized enrichment reads in `JdbiUserStorage` behind a single `loadNormalizedProfileData(...)` batch query so page/query paths no longer fan out into repeated normalized-table reads.
- [x] **Step 3: Preserve the normalized schema, cache semantics, and `UserStorage` API. Do not introduce a migration in this phase.** ✅ No schema, cache, or `UserStorage` contract changes were introduced; the change stayed inside `JdbiUserStorage` read-path hydration.
- [x] **Step 4: Run focused storage + read-path tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,ProfileUseCasesTest,RestApiReadRoutesTest test`
  - Expected: PASS; no schema drift, no ordering regressions, no profile hydration regressions.
  - Result (2026-03-31): ✅ PASS — 60 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the storage efficiency pass.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Chunk 5: Async, messaging, and UI wiring refinement

### Task 10: Refine shared polling behavior in `ViewModelAsyncScope`

**Files:**
- Modify: `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
- Modify: `src/main/java/datingapp/ui/async/PollingTaskHandle.java` (only if needed)
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Test: `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/BaseViewModelTest.java`

- [x] **Step 1: Freeze current cancellation, loading-state, and latest-wins behavior with tests if any important edge is unguarded.** ✅ Added `ViewModelAsyncScopeTest` coverage for same-key polling supersession so the unguarded polling-latest-wins seam is now characterized explicitly.
- [x] **Step 2: Refine polling so cancellation, diagnostics, and callback suppression remain explicit and predictable; keep backward-compatible `TaskHandle` semantics.** ✅ Updated `ViewModelAsyncScope` polling teardown to record a single cancellation diagnostic for superseded/disposed polling loops while keeping `TaskHandle`/`PollingTaskHandle` behavior unchanged.
- [x] **Step 3: Re-run async/UI tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ViewModelAsyncScopeTest,ChatViewModelTest,MatchingViewModelTest,BaseViewModelTest test`
  - Expected: PASS; chat refresh and matching undo countdown behavior remain stable.
  - Result (2026-03-31): ✅ PASS — 45 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 4: Commit the polling refinement.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 11: Replace page-scanning conversation preview lookup with a direct or cheaper path

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/app/cli/MessagingHandler.java` (only if result-shape changes require it)
- Test: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`

- [x] **Step 1: Lock the "target conversation outside first page" behavior in `MessagingUseCasesTest` if the existing test is not already sufficient.** ✅ Existing `openConversationResolvesTargetConversationOutsideRequestedPreviewPage` test already locks the behavior; added `ChatViewModelTest` coverage for zero-duplicate list-conversation fetch during `openConversationWithUser`.
- [x] **Step 2: Add a cheaper preview lookup path in `ConnectionService` or equivalent so `openConversation(...)` no longer scans pages sequentially.** ✅ Added `ConnectionService.getConversationPreview(userId, conversationId)` — a direct single-conversation preview builder that bypasses page scanning entirely.
- [x] **Step 3: Remove duplicate preview fetches from `ChatViewModel` once the new path is stable.** ✅ Replaced `findConversationPreview` page scan in `MessagingUseCases.openConversation` with a single `getConversationPreview` call; replaced the second `listConversations` round-trip in `ChatViewModel.loadOpenConversation` with a local `upsertConversationPreview` merge.
- [x] **Step 4: Re-run focused messaging/UI tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=MessagingUseCasesTest,ChatViewModelTest,ConnectionServiceAtomicityTest,RestApiConversationBatchCountTest test`
  - Expected: PASS; open-conversation behavior is preserved with fewer redundant reads.
  - Result (2026-03-31): ✅ PASS — 46 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 5: Commit the messaging preview optimization.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

### Task 12: Simplify `ViewModelFactory` after lower-level seams have stabilized

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/ui/DatingApp.java` (only if constructor/wiring changes require it)
- Modify: `src/main/java/datingapp/ui/NavigationService.java` (only if bootstrap wiring changes require it)
- Create: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java` (recommended if no direct factory test exists)
- Test: `src/test/java/datingapp/ui/viewmodel/ViewModelArchitectureConsistencyTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/NotesViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/BaseViewModelTest.java`

- [x] **Step 1: Introduce internal helpers or dependency bundles that remove repetition and separate cache/lifecycle concerns from controller registry concerns, while keeping the public `ViewModelFactory(ServiceRegistry)` entrypoint stable if possible.** ✅ Cached the four shared UI adapters (`UiUserStore`, `UiMatchDataAccess`, `UiProfileNoteDataAccess`, `UiPresenceDataAccess`) as lazily-initialized singletons per factory lifecycle, cleared on reset/dispose alongside the ViewModel cache.
- [x] **Step 2: Keep reset/dispose/logout behavior and current controller creation semantics unchanged.** ✅ `reset()` and `dispose()` both clear adapter caches alongside ViewModel caches; controller-factory map remains immutable.
- [x] **Step 3: Add a focused `ViewModelFactoryTest` only if the simplification becomes hard to protect via existing UI tests.** ✅ Created `ViewModelFactoryTest` with 10 tests covering caching, reset/dispose lifecycle, session binding, and controller creation.
- [x] **Step 4: Re-run focused UI architecture tests.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ViewModelArchitectureConsistencyTest,BaseViewModelTest,ProfileViewModelTest,ChatViewModelTest,StatsViewModelTest,NotesViewModelTest,PreferencesViewModelTest,SafetyViewModelTest test`
  - Expected: PASS; factory simplification changes structure, not behavior.
  - Result (2026-03-31): ✅ PASS — 69 tests run, 0 failures (including new ViewModelFactoryTest).
- [x] **Step 5: Commit the factory simplification.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Chunk 6: Decision-gated REST hardening and final verification

### Task 13: Apply optional config-driven local REST authentication hardening only if Decision Gate D1 selects it

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Create: `src/main/java/datingapp/app/api/RestApiLocalAuthPolicy.java` (recommended if auth is added)
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `src/main/java/datingapp/core/AppConfigValidator.java`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` (only if config loading/wiring needs updates)
- Modify: `config/app-config.json`
- Modify: `src/test/java/datingapp/app/ConfigLoaderTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`

- [x] **Step 1: Add failing tests for the chosen authentication contract (missing token, invalid token, valid token, interaction with `X-User-Id`) before editing production code.** ⏭️ SKIPPED — Decision Gate D1 selected Option B (accept local IPC risk, keep loopback-only + `X-User-Id`).
- [x] **Step 2: Add config surface changes in all required places together (`AppConfig`, `AppConfigValidator`, runtime config, loader tests).** ⏭️ SKIPPED per D1 Option B.
- [x] **Step 3: Implement auth enforcement on top of loopback-only restrictions, preserving route-specific ownership checks and rate limiting.** ⏭️ SKIPPED per D1 Option B.
- [x] **Step 4: Re-run targeted REST + config tests.** ⏭️ SKIPPED per D1 Option B — no code changes, no regression risk.
- [x] **Step 5: Commit the hardening change and update any local client bootstrap/config docs only if the project wants that contract.** ⏭️ No commit needed; Task 13 is a deliberate no-op.

### Task 14: Final regression, shim review, and quality gate

**Files:**
- Modify: any remaining touched files from prior phases
- Review: compatibility façades/overloads introduced in Phases 3 and 4
- Test: repo-wide quality gate

- [x] **Step 1: Re-read all touched shared files and remove only the compatibility layers proven unnecessary by current call sites and tests.** ✅ Reviewed compatibility layers: the `ProfileService` wide constructor, `ProfileUseCases` façade, and `MessagingUseCases.findConversationPreview` shims all still have active callers or are harmless; kept them in place per Decision Gate D2 default.
- [x] **Step 2: Run targeted smoke suites spanning REST, profile, storage, messaging, and UI seams.**
  - Run: `mvn --% -Dcheckstyle.skip=true -Dtest=ServiceRegistryTest,ProfileUseCasesTest,ProfileUseCasesNotesTest,MessagingUseCasesTest,CandidateFinderTest,MatchingServiceTest,MatchQualityServiceTest,ConnectionServiceAtomicityTest,TrustSafetyServiceTest,JdbiUserStorageNormalizationTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiPhaseTwoRoutesTest,ViewModelAsyncScopeTest,ChatViewModelTest,ProfileViewModelTest test`
  - Expected: PASS; all main seams still hold together.
  - Result (2026-03-31): ✅ PASS — 260 tests run, 0 failures, 0 errors, 0 skipped.
- [x] **Step 3: Run the full quality gate.**
  - Run: `mvn spotless:apply verify`
  - Expected: BUILD SUCCESS.
  - Result (2026-03-31): ✅ BUILD SUCCESS — 1594 tests run, 0 failures, 0 errors, 1 skipped. 0 Checkstyle violations. 0 PMD violations. JaCoCo coverage gate passed. Spotless clean.
- [x] **Step 4: Only after the full gate is green, decide whether any remaining façade/overload can be removed safely; otherwise keep the compatibility layer in place.** ✅ Decision Gate D2 applied: keep all compatibility façades/overloads — they still have active callers and the risk/benefit of removal does not justify the churn.
- [x] **Step 5: Commit the final cleanup and verification pass.** ✅ Implementation work completed; git/commit intentionally deferred to the user per session instruction.

## Phase-by-phase success criteria

### Phase 0 success criteria
- Missing behavior protections are now expressed as tests.
- The coordinator can refactor without re-deriving current contracts from scratch.

### Phase 1 success criteria
- REST guard/identity logic is no longer concentrated entirely inside `RestApiServer`.
- Route contracts, DTOs, status codes, and the direct-read candidate exception are unchanged.

### Phase 2 success criteria
- `StorageFactory`/`ServiceRegistry`/`ApplicationStartup` responsibilities are clearer.
- Public entrypoints stay stable.
- Launcher and fixture behavior remain equivalent.

### Phase 3 success criteria
- Profile notes, insights, and mutation concerns each have their own use-case slice.
- `ProfileUseCases` remains as a thin compatibility façade until removal is proven safe.

### Phase 4 success criteria
- `ProfileService` constructor truthfully reflects what the implementation uses.
- Shared builders/fixtures use the narrower contract first.

### Phase 5 success criteria
- `JdbiUserStorage` does less repeated hydration work without changing results, schema, or `UserStorage` contracts.

### Phase 6 success criteria
- Polling semantics remain correct and better isolated.
- Conversation preview lookup no longer depends on repeated page scanning.

### Phase 7 success criteria
- `ViewModelFactory` is materially easier to reason about, while UI behavior stays stable.

### Phase 8 success criteria
- If enabled, local REST auth is enforced through an explicit and tested contract.
- If not enabled, the accepted risk is documented in the work notes and the code remains behavior-preserving.

### Final completion criteria
- All targeted suites pass.
- `mvn spotless:apply verify` passes.
- Shared-file ownership and shim removal decisions are explicit.
- No phase leaves behind ambiguous half-migrations.

## Notes for the coordinator agent

- Prefer the smallest correct change in each task.
- Do not rename broad public surfaces just because the internals are being cleaned up.
- Treat `ProfileUseCases` façade preservation as a feature, not a failure; it is how this plan contains blast radius.
- Treat `RestApiTestFixture` and `ServiceRegistryTest` as canonical composition oracles.
- Do not let a subagent “helpfully” refactor unrelated classes in the same phase.
- If a phase reveals a deeper design problem than expected, stop at the chunk boundary, summarize the evidence, and update the plan rather than improvising a repo-wide rewrite.
