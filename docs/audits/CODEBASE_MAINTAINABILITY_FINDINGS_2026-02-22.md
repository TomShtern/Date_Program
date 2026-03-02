# Codebase Maintainability Findings (Code-Only)

Generated: 2026-02-22

> ⚠️ **Alignment status (2026-03-01): Historical snapshot**
> This document reflects findings at generation time; use current source for authoritative structure/metrics.
> Current baseline: **116 main + 88 test = 204 Java files**, **56,482 total Java LOC / 43,327 code LOC**, tests: **983/0/0/2**.

## Scope
- Analyzed source code only (`src/main/java`, `src/test/java`, `pom.xml`).
- Excluded documentation as requested.
- Focus: readability, maintainability, architecture clarity, duplication, complexity, and low-risk simplification opportunities.

## Codebase Snapshot
- Java files analyzed: 154
- Total LOC: 49,606
- Code LOC: 37,658
- Source: `tokei src/main/java src/test/java`

## Findings

### High Severity

1. **Schema evolution is split across two mechanisms, creating drift risk.**
- Evidence: `src/main/java/datingapp/storage/schema/SchemaInitializer.java:36`, `src/main/java/datingapp/storage/schema/MigrationRunner.java:59`, `src/main/java/datingapp/storage/schema/MigrationRunner.java:136`, `src/main/java/datingapp/storage/schema/MigrationRunner.java:191`
- Problem: Fresh schema creation and incremental migration logic are maintained separately with a long manual `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` list.
- How it manifests in this codebase: a brand-new database is built by `SchemaInitializer.createAllTables(...)`, but existing databases rely on `MigrationRunner`'s manual add-column/add-foreign-key logic. Those two paths are not generated from one model.
- Why this is risky: schema correctness becomes environment-dependent. Fresh installs and upgraded installs can diverge over time.
- Concrete failure scenario: a new index or constraint gets added in `SchemaInitializer` only. Local dev with fresh DB works, production upgrades miss it, and query behavior/performance differs.
- Maintenance impact: Easy to miss parity between new installs and upgraded installs.
- Low-risk direction: Move to versioned, ordered migrations (single source of truth), and keep initializer minimal.
- Practical first step: introduce migration `V2` as a class/script and stop adding new DDL directly to `SchemaInitializer` except baseline table creation.

2. **`NavigationService` is a UI god object with mixed concerns.**
- Evidence: `src/main/java/datingapp/ui/NavigationService.java:66`, `src/main/java/datingapp/ui/NavigationService.java:170`, `src/main/java/datingapp/ui/NavigationService.java:240`, `src/main/java/datingapp/ui/NavigationService.java:359`, `src/main/java/datingapp/ui/NavigationService.java:374`
- Problem: Enum registry, FXML navigation, transition handling, history stack, and navigation context are all in one singleton.
- How it manifests in this codebase: view loading and scene swaps sit next to animation decisions, stack mutation, and context payload APIs.
- Why this is risky: changes in one concern can break another (for example animation adjustments affecting navigation state behavior).
- Concrete failure scenario: adding a new transition rule unexpectedly breaks back-navigation because both share internal state and flow control.
- Maintenance impact: Small navigation changes require touching complex central logic; testing navigation behavior is harder.
- Low-risk direction: Split into `ViewRegistry`, `NavigationHistory`, and `NavigationContext` collaborators.
- Practical first step: extract history/context into separate classes while keeping public API unchanged.

3. **Main CLI menu has duplicated command definitions (guard + switch + printed menu).**
- Evidence: `src/main/java/datingapp/Main.java:90`, `src/main/java/datingapp/Main.java:93`, `src/main/java/datingapp/Main.java:102`, `src/main/java/datingapp/Main.java:148`
- Problem: Command metadata is repeated in multiple places.
- How it manifests in this codebase: login-required logic, printed labels, and dispatch actions all repeat the same numeric options.
- Why this is risky: one edit requires synchronized changes across multiple blocks.
- Concrete failure scenario: option text changes in `printMenu`, but switch/guard still points to old behavior or permission requirement.
- Maintenance impact: Menu changes are brittle and can desynchronize behavior vs display.
- Low-risk direction: Replace with a single menu option registry (`id`, `label`, `requiresLogin`, `action`).
- Practical first step: create a `List<MenuOption>` in `Main` and derive guard + printing + dispatch from it.

4. **`MatchingHandler` is oversized and mixes too many flows.**
- Evidence: `src/main/java/datingapp/app/cli/MatchingHandler.java:125`, `src/main/java/datingapp/app/cli/MatchingHandler.java:147`, `src/main/java/datingapp/app/cli/MatchingHandler.java:806`
- Problem: Candidate browsing, standouts, notifications, requests, and other flows coexist in one large class.
- How it manifests in this codebase: long methods interleave CLI rendering, selection parsing, and service orchestration in one file.
- Why this is risky: cognitive load is high; onboarding and safe edits are slower.
- Concrete failure scenario: a change to friend-request UI inadvertently modifies shared helper logic used by browse/match flows.
- Maintenance impact: High cognitive load and low locality for changes.
- Low-risk direction: Split by workflow (`BrowseFlow`, `NotificationsFlow`, `RequestsFlow`, `StandoutsFlow`).
- Practical first step: extract one vertical slice first (`viewPendingRequests` and helpers) into a dedicated class.

5. **`JdbiMatchmakingStorage` is also oversized and combines DAO, transaction, undo, and mapping concerns.**
- Evidence: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:36`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:156`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:591`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:621`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:727`
- Problem: One class coordinates too many persistence responsibilities.
- How it manifests in this codebase: transaction flow methods, SQL constants, DAO interfaces, and undo adaptation live together.
- Why this is risky: transaction changes and data-shape changes become tightly coupled.
- Concrete failure scenario: adjusting mutual-like write logic also requires touching nested DAO contract/mapping code in same class, increasing regression risk.
- Maintenance impact: Harder to reason about transaction correctness and make safe changes.
- Low-risk direction: Extract DAO interfaces/mappers/undo adapter into separate files; keep one coordinator class.
- Practical first step: move nested DAO interfaces to package-private top-level interfaces with no behavior changes.

6. **Handler tests manually rebuild service wiring repeatedly.**
- Evidence: `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java:28`, `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java:38`, `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java:52`, `src/main/java/datingapp/storage/StorageFactory.java:39`
- Problem: Tests duplicate production-like DI composition logic.
- How it manifests in this codebase: test setup re-creates a large set of storages/services in each handler test class.
- Why this is risky: constructor signature changes create broad test churn.
- Concrete failure scenario: adding one dependency to `MatchingService` breaks many tests with repetitive setup updates.
- Maintenance impact: Constructor or wiring changes cascade into many test rewrites.
- Low-risk direction: Create a shared `TestServiceRegistryFactory` fixture.
- Practical first step: centralize service assembly in one test utility and migrate one handler test class first.

7. **`TestStorages` duplicates production candidate-filtering behavior.**
- Evidence: `src/test/java/datingapp/core/testutil/TestStorages.java:91`, `src/main/java/datingapp/core/matching/CandidateFinder.java:122`, `src/main/java/datingapp/core/matching/CandidateFinder.java:179`
- Problem: Test doubles reimplement domain logic rather than reuse shared behavior.
- How it manifests in this codebase: candidate filters (state/orientation/age) exist in both production path and in-memory test path.
- Why this is risky: test semantics can drift from runtime semantics silently.
- Concrete failure scenario: production adds a new filter criterion; tests still pass because test doubles did not get updated.
- Maintenance impact: Silent drift risk between tests and runtime behavior.
- Low-risk direction: Reuse shared filtering helpers or keep filtering in one place.
- Practical first step: move common filter predicate into a shared utility and call it from both implementations.

### Medium Severity

8. **Navigation surface is declared twice (`ViewType` and controller factory map).**
- Evidence: `src/main/java/datingapp/ui/NavigationService.java:66`, `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java:103`
- Problem: Two registries must stay synchronized manually.
- How it manifests in this codebase: new screens require edits in two independent files.
- Why this is risky: one-sided updates create runtime wiring bugs.
- Concrete failure scenario: a view is added to enum but missing in controller factory map, causing navigation failure at runtime.
- Maintenance impact: Easy to add a screen in one place and forget the other.
- Low-risk direction: Use one typed registry that owns view metadata and controller construction.
- Practical first step: add a single `ViewDefinition` table and have both systems read from it.

9. **CLI index parsing and invalid-input handling are duplicated across handlers.**
- Evidence: `src/main/java/datingapp/app/cli/ProfileHandler.java:411`, `src/main/java/datingapp/app/cli/ProfileHandler.java:938`, `src/main/java/datingapp/app/cli/SafetyHandler.java:146`, `src/main/java/datingapp/app/cli/MatchingHandler.java:327`, `src/main/java/datingapp/app/cli/MessagingHandler.java:145`
- Problem: Repeated `Integer.parseInt(...) - 1` and `NumberFormatException` branches.
- How it manifests in this codebase: each handler has near-identical parse/bounds/error branches with slight message differences.
- Why this is risky: behavior diverges over time and bug fixes are repeated.
- Concrete failure scenario: one handler accepts `0` as cancel while another treats it as invalid due to small branch differences.
- Maintenance impact: Inconsistent UX and repetitive edits for validation behavior.
- Low-risk direction: Centralize in a shared parser helper in `CliTextAndInput`.
- Practical first step: add `parseOneBasedIndex(String input, int size)` and replace 2-3 highest-traffic call sites first.

10. **CLI list-render/select loops are duplicated.**
- Evidence: `src/main/java/datingapp/app/cli/ProfileHandler.java:463`, `src/main/java/datingapp/app/cli/ProfileHandler.java:807`, `src/main/java/datingapp/app/cli/SafetyHandler.java:247`, `src/main/java/datingapp/app/cli/MatchingHandler.java:840`
- Problem: Similar menu rendering and selection flows are reimplemented many times.
- How it manifests in this codebase: repeated loops print numbered entries, parse selection, and retrieve selected item.
- Why this is risky: presentation consistency and accessibility messaging are hard to standardize.
- Concrete failure scenario: one list shows 1-based indexes with cancel option, another omits cancel/validation and traps the user in retries.
- Maintenance impact: Harder to keep behavior/messages consistent.
- Low-risk direction: Introduce a reusable `CliMenu` utility.
- Practical first step: implement a generic `selectFromList(...)` helper and migrate one flow per handler.

11. **Dependency injection semantics are unclear in `MatchingHandler`, and defaults are hardcoded in profile preferences.**
- Evidence: `src/main/java/datingapp/app/cli/MatchingHandler.java:54`, `src/main/java/datingapp/app/cli/MatchingHandler.java:62`, `src/main/java/datingapp/app/cli/MatchingHandler.java:125`, `src/main/java/datingapp/app/cli/ProfileHandler.java:463`, `src/main/java/datingapp/app/cli/ProfileHandler.java:485`
- Problem: Same service type is injected for multiple conceptual roles; preference defaults (`50`, `18`, `99`) are inline literals.
- How it manifests in this codebase: two fields of identical type imply different meanings, but naming/wiring do not encode intent strongly.
- Why this is risky: engineers infer semantics by convention, not type boundaries.
- Concrete failure scenario: a future optimization for standouts also changes daily picks unexpectedly because both point to same service path.
- Maintenance impact: Ambiguous intent and config drift risk.
- Low-risk direction: Use role-specific abstractions and move defaults to config/constants.
- Practical first step: extract constants for defaults immediately; then introduce interface aliases (`DailyRecommendationRead`, `StandoutRecommendationRead`).

12. **`AppConfig` has heavy backward-compat delegation + large flat builder surface.**
- Evidence: `src/main/java/datingapp/core/AppConfig.java:197`, `src/main/java/datingapp/core/AppConfig.java:447`, `src/main/java/datingapp/core/AppConfig.java:490`, `src/main/java/datingapp/core/AppConfig.java:553`, `src/main/java/datingapp/core/AppConfig.java:833`
- Problem: 59 delegate methods and 57 builder setters in one file for one config aggregate.
- How it manifests in this codebase: many methods simply forward to nested records and duplicate naming surface area.
- Why this is risky: each new config field requires edits in multiple places.
- Concrete failure scenario: adding one new safety field but forgetting one delegate method leads to inconsistent API use across classes.
- Maintenance impact: High edit surface for each config change.
- Low-risk direction: Keep sub-record builders first-class and reduce top-level delegation.
- Practical first step: mark delegating methods as legacy and migrate call sites to `config.safety().x()` style gradually.

13. **`UserStorage` mixes user CRUD with profile-note persistence concerns.**
- Evidence: `src/main/java/datingapp/core/storage/UserStorage.java:148`, `src/main/java/datingapp/core/storage/UserStorage.java:156`, `src/main/java/datingapp/core/storage/UserStorage.java:165`, `src/main/java/datingapp/core/storage/UserStorage.java:173`, `src/main/java/datingapp/core/storage/UserStorage.java:182`
- Problem: One interface owns two domain surfaces.
- How it manifests in this codebase: profile-note methods sit beside user fetch/save lifecycle methods.
- Why this is risky: storage clients must depend on methods they may not need.
- Concrete failure scenario: a new user-storage implementation is blocked by unrelated note-method contract requirements.
- Maintenance impact: Increased coupling for consumers/implementations.
- Low-risk direction: Split note operations into `ProfileNoteStorage`.
- Practical first step: introduce new interface and have `JdbiUserStorage` implement both while preserving old interface temporarily.

14. **Distance calculations are duplicated and coupled to `CandidateFinder.GeoUtils`.**
- Evidence: `src/main/java/datingapp/core/storage/UserStorage.java:90`, `src/main/java/datingapp/core/matching/CandidateFinder.java:73`, `src/main/java/datingapp/core/matching/CandidateFinder.java:92`, `src/main/java/datingapp/core/matching/MatchQualityService.java:366`
- Problem: Similar geospatial math appears in multiple locations; `MatchQualityService` depends on nested helper in another service.
- How it manifests in this codebase: both storage defaults and matching services compute distance with separate utilities.
- Why this is risky: tiny formula/rounding changes can diverge recommendation and scoring behavior.
- Concrete failure scenario: one path changes Earth radius or rounding threshold while another path does not, yielding inconsistent candidate ordering.
- Maintenance impact: Behavior drift and tight coupling.
- Low-risk direction: Introduce a standalone geo utility/service and reuse it everywhere.
- Practical first step: create `GeoDistance` utility and replace two call sites first (`UserStorage` and `MatchQualityService`).

15. **Default pagination in `InteractionStorage` loads all rows and slices in memory.**
- Evidence: `src/main/java/datingapp/core/storage/InteractionStorage.java:172`, `src/main/java/datingapp/core/storage/InteractionStorage.java:182`, `src/main/java/datingapp/core/storage/InteractionStorage.java:186`, `src/main/java/datingapp/core/storage/InteractionStorage.java:214`, `src/main/java/datingapp/core/storage/InteractionStorage.java:219`
- Problem: Default method materializes full lists before paging.
- How it manifests in this codebase: the interface fallback reads all matches and only then computes `subList` windows.
- Why this is risky: hidden O(n) behavior remains unnoticed in large datasets.
- Concrete failure scenario: production-like data volume causes high latency or memory spikes in implementations that rely on defaults.
- Maintenance impact: Hidden scalability/performance risk for implementations that do not override.
- Low-risk direction: Make pagination abstract and DB-backed by default.
- Practical first step: deprecate default methods and enforce explicit paged query implementations.

16. **`JdbiUserStorage.Mapper` carries complex legacy+JSON parsing paths.**
- Evidence: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:275`, `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:342`, `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:351`, `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:405`, `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:452`
- Problem: Mapper parses mixed historical formats inline.
- How it manifests in this codebase: enum sets and photo URLs try JSON first, then fallback parsing in mapper code.
- Why this is risky: parser behavior is spread and difficult to test in isolation.
- Concrete failure scenario: a malformed legacy value silently falls into fallback parsing and produces partial/incorrect data.
- Maintenance impact: High fragility around storage format changes.
- Low-risk direction: Extract a dedicated codec/parser with focused tests.
- Practical first step: create one parser class per persisted composite type and test with representative legacy/new samples.

17. **REST pagination validation is copy-pasted across endpoints.**
- Evidence: `src/main/java/datingapp/app/api/RestApiServer.java:177`, `src/main/java/datingapp/app/api/RestApiServer.java:180`, `src/main/java/datingapp/app/api/RestApiServer.java:228`, `src/main/java/datingapp/app/api/RestApiServer.java:231`, `src/main/java/datingapp/app/api/RestApiServer.java:245`, `src/main/java/datingapp/app/api/RestApiServer.java:248`
- Problem: Same `limit`/`offset` checks repeated.
- How it manifests in this codebase: each route parses and validates query params separately.
- Why this is risky: bugfixes and policy changes are easy to miss in one endpoint.
- Concrete failure scenario: one endpoint starts clamping limits while another still throws exceptions, creating inconsistent API contracts.
- Maintenance impact: Easy inconsistency in behavior/messages.
- Low-risk direction: Centralize query-param validation helper.
- Practical first step: add a private helper that returns validated `(limit, offset)` pair for all list routes.

18. **`SwipeResult` type name is reused for unrelated semantics.**
- Evidence: `src/main/java/datingapp/core/matching/MatchingService.java:265`, `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:268`
- Problem: Same type name means different payload/meaning.
- How it manifests in this codebase: both types are nested records but represent different business concerns.
- Why this is risky: import confusion and misread code intent.
- Concrete failure scenario: engineer mistakenly references the wrong `SwipeResult` in a new feature and gets subtle compile-time or semantic confusion.
- Maintenance impact: Import confusion and cognitive overhead.
- Low-risk direction: Rename one type (`SessionSwipeOutcome` etc.) to make intent explicit.
- Practical first step: rename metrics variant only and update references in one PR.

19. **`ProfileController` does substantial orchestration beyond view binding.**
- Evidence: `src/main/java/datingapp/ui/screen/ProfileController.java:63`, `src/main/java/datingapp/ui/screen/ProfileController.java:78`, `src/main/java/datingapp/ui/screen/ProfileController.java:189`, `src/main/java/datingapp/ui/screen/ProfileController.java:390`, `src/main/java/datingapp/ui/screen/ProfileController.java:663`, `src/main/java/datingapp/ui/screen/ProfileController.java:808`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:133`
- Problem: Controller contains extensive event wiring and UI flow logic.
- How it manifests in this codebase: controller performs substantial behavior orchestration rather than only binding and dispatching commands.
- Why this is risky: view logic and business-like transitions are harder to test independently.
- Concrete failure scenario: editing UI interaction for one control affects data consistency because logic is split across controller and viewmodel.
- Maintenance impact: Higher coupling and harder unit testing.
- Low-risk direction: Move more workflow/state transformations into the ViewModel.
- Practical first step: migrate one interaction group (for example dealbreaker editing) into viewmodel commands/properties.

20. **UI ViewModel tests rely on `Thread.sleep`, which is brittle and slow.**
- Evidence: `src/test/java/datingapp/ui/viewmodel/SocialViewModelTest.java:98`, `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:89`, `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java:124`, `src/test/java/datingapp/ui/viewmodel/StandoutsViewModelTest.java:113`
- Problem: Time-based waiting instead of deterministic synchronization.
- How it manifests in this codebase: many tests sleep for fixed durations before asserting async state.
- Why this is risky: CI speed variance can create flaky passes/fails.
- Concrete failure scenario: test passes locally but fails in CI when asynchronous work exceeds hardcoded sleep budget.
- Maintenance impact: Flaky tests and slower CI feedback.
- Low-risk direction: Introduce FX test synchronization utilities/latches tied to completion events.
- Practical first step: add a shared `awaitFxCondition(...)` helper and replace sleeps in one test class first.

21. **Global `AppSession` singleton requires repeated explicit resets in tests.**
- Evidence: `src/main/java/datingapp/core/AppSession.java:13`, `src/main/java/datingapp/core/AppSession.java:70`, `src/test/java/datingapp/ui/viewmodel/SocialViewModelTest.java:80`, `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:99`
- Problem: Shared mutable singleton leaks across tests unless manually reset.
- How it manifests in this codebase: test suites repeatedly call reset in setup to avoid cross-test contamination.
- Why this is risky: one missing reset can produce non-deterministic failures.
- Concrete failure scenario: execution order changes and a stale current user from previous test causes wrong assertions in a later test.
- Maintenance impact: Boilerplate and accidental cross-test coupling risk.
- Low-risk direction: Provide test-scoped session harness or injectable session abstraction.
- Practical first step: add test utility wrapper that always resets before/after each test class and migrate incrementally.

## Suggested Refactor Order (Lowest Risk First)
1. Centralize duplicated validation/parsing utilities (CLI index parsing, REST pagination).
2. Unify metadata registries (CLI menu option table, navigation registry).
3. Split oversized classes at seam points (`MatchingHandler`, `JdbiMatchmakingStorage`, `NavigationService`).
4. Consolidate schema evolution into versioned migrations only.
5. Reduce test wiring duplication with shared fixtures and deterministic async test helpers.

## Notes
- This report reflects actual code at analysis time and intentionally does not rely on documentation.
