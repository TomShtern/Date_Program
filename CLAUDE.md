> 🚀 **VERIFIED & UPDATED: 2026-04-05**
> This document has been refreshed against the current codebase and build output as of this date.

# CLAUDE.md

Second-level guidance for AI coding agents working in this repository.

> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`
>
> **Source of truth:** code only (`src/main/java`, `src/test/java`, `pom.xml`).

<!--ARCHIVE:32:agent:github_copilot:scope:docs-hierarchy-refresh--> Replaced the stale source snapshot and operational guidance with a current hierarchy-aware repo map based on the 2026-03-30 codebase and build output.

## Environment

- Windows 11
- PowerShell 7.5.6
- VS Code Insiders (sometimes IntelliJ)
- Java 25 + JavaFX 25.0.2
- Maven

## Verified Source Snapshot (2026-04-05)

- Java files: **373 total** (`175` main / `198` test)
- Java LOC (`tokei src`, Java only): **104,527 total / 84,743 code / 14,882 blank / 4,902 comments**

## Architecture (code-verified)

```text
datingapp/
  Main.java
  app/
    api/{RestApiDtos,RestApiIdentityPolicy,RestApiRequestGuards,RestApiServer}.java
    bootstrap/{ApplicationStartup,CleanupScheduler}.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingCliPresenter,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
    event/handlers/{AchievementEventHandler,MetricsEventHandler,NotificationEventHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/{ProfileInsightsUseCases,ProfileMutationUseCases,ProfileNotesUseCases,ProfileUseCases,VerificationUseCases}.java
      social/SocialUseCases.java
  core/
    {AppClock,AppConfig,AppConfigValidator,AppSession,EnumSetUtil,LoggingSupport,ServiceRegistry,TextUtil}.java
    connection/{ConnectionModels,ConnectionService}.java
    i18n/I18n.java
    matching/{CandidateFinder,CompatibilityCalculator,DailyLimitService,DailyPickService,DefaultCompatibilityCalculator,DefaultDailyLimitService,DefaultDailyPickService,DefaultStandoutService,InterestMatcher,LifestyleMatcher,MatchingService,MatchQualityService,ModerationAuditEvent,ModerationAuditLogger,RecommendationService,Standout,StandoutService,TrustSafetyService,UndoService}.java
    metrics/{AchievementService,ActivityMetricsService,DefaultAchievementService,EngagementDomain,SwipeState}.java
    model/{LocationModels,Match,ProfileNote,User}.java
    profile/{LocationService,MatchPreferences,ProfileCompletionSupport,ProfileService,SanitizerUtils,ValidationService}.java
    storage/{AccountCleanupStorage,AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}.java
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy,WorkflowDecision}.java
  storage/
    {DatabaseDialect,DatabaseManager,DevDataSeeder,StorageFactory}.java
    jdbi/{JdbiAccountCleanupStorage,JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage,SqlDialectSupport}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    {DatingApp,ImageCache,LocalPhotoStore,NavigationService,UiAnimations,UiComponents,UiConstants,UiDialogs,UiFeedbackService,UiPreferencesStore,UiThemeService,UiUtils}.java
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,PollingTaskHandle,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}.java
    screen/{BaseController,ChatController,DashboardController,LocationSelectionDialog,LoginController,MatchesController,MatchingController,MilestonePopupController,NotesController,PreferencesController,ProfileController,ProfileFormValidator,ProfileViewController,SafetyController,SocialController,StandoutsController,StatsController}.java
    viewmodel/{BaseViewModel,ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,NotesViewModel,PreferencesViewModel,ProfileReadOnlyViewModel,ProfileViewModel,SafetyViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}.java
```

## Critical Gotchas

| Gotcha                  | Wrong                                                                  | Correct                                                                                                                    |
|-------------------------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| User enum imports       | `datingapp.core.model.Gender`                                          | `datingapp.core.model.User.Gender`                                                                                         |
| Match enum imports      | `datingapp.core.model.MatchState`                                      | `datingapp.core.model.Match.MatchState`                                                                                    |
| `ProfileNote` ownership | `User.ProfileNote`                                                     | `datingapp.core.model.ProfileNote`                                                                                         |
| Domain clock            | `Instant.now()` in services/domain                                     | Use `AppClock` as the time source: `AppClock.now()` / `AppClock.today()` or `AppClock.clock()` for clock-injected services |
| Pair IDs                | `a + "_" + b`                                                          | deterministic `generateId(a, b)`                                                                                           |
| Event publication       | null/no-op bus in production paths                                     | real injected `AppEventBus`                                                                                                |
| Runtime config          | `AppConfig.defaults()` in service code                                 | injected `AppConfig` via `ServiceRegistry`                                                                                 |
| Runtime storage wiring  | assume `StorageFactory.buildH2(...)` is the production path            | use `StorageFactory.buildSqlDatabase(...)` for runtime; keep `buildH2(...)` / `buildInMemory(...)` for H2-backed tests     |
| JDBI record binding     | `@BindBean` on records                                                 | `@BindMethods` on records                                                                                                  |
| Date formatting         | locale-implicit month/day names                                        | `Locale.ENGLISH` for user-facing month/day names                                                                           |
| ViewModel threading     | ad-hoc `Thread.ofVirtual()` / `Platform.runLater()` in normal UI flows | `BaseViewModel` + `ViewModelAsyncScope` + dispatcher abstractions                                                          |
| UI paging boundary      | expose `core.storage.PageData` to ViewModels                           | use `UiDataAdapters.UiPage<T>`                                                                                             |
| REST binding            | assume external host access                                            | loopback-only binding in `RestApiServer`                                                                                   |
| Location availability   | treat all listed countries as available                                | only `IL` is currently selectable/fully supported                                                                          |
| Profile use-case wiring | `ProfileUseCases` directly for notes/stats/mutation                    | use `ProfileNotesUseCases`, `ProfileInsightsUseCases`, or `ProfileMutationUseCases` from `ServiceRegistry`                 |
| REST guard logic        | inline guard logic in `RestApiServer`                                  | delegate to `RestApiRequestGuards` and `RestApiIdentityPolicy`                                                             |

## Entrypoints and wiring

### Shared bootstrap

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();
```

### CLI (`Main.java`)

```java
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
MatchingHandler matching = new MatchingHandler(
    MatchingHandler.Dependencies.fromServices(services, session, inputReader, profile::completeProfile));
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);
```

### JavaFX (`DatingApp.java`)

```java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.setPreferencesStore(vmFactory.getPreferencesStore());
nav.initialize(primaryStage);
```

## Use-case layer and event bus

`ServiceRegistry` constructs and owns the app-layer bundles:

- `getMatchingUseCases()`
- `getMessagingUseCases()`
- `getProfileUseCases()` — compatibility façade; prefer dedicated slices below
- `getProfileMutationUseCases()` — profile save/update/delete account
- `getProfileNotesUseCases()` — note CRUD
- `getProfileInsightsUseCases()` — stats, achievements, session summary (read-only)
- `getVerificationUseCases()`
- `getSocialUseCases()`

Callers should obtain use cases from the registry rather than constructing them directly. For profile operations, prefer the dedicated slices over the `ProfileUseCases` compatibility façade.

`AppEventBus` / `InProcessAppEventBus` provides in-process domain event dispatching. Event handlers in `app/event/handlers/` own cross-cutting concerns like achievements, metrics, and notifications.

The current deliberate direct-read exception remains the candidates route in `RestApiServer`; keep that exception explicit instead of letting adapters drift back into raw service orchestration.

## Current architecture notes

- `ProfileUseCases` is a thin compatibility façade delegating to `ProfileMutationUseCases`, `ProfileNotesUseCases`, and `ProfileInsightsUseCases`. New consumers should use the dedicated slices directly.
- `RestApiRequestGuards` and `RestApiIdentityPolicy` own REST request-guard and identity-resolution logic; `RestApiServer` delegates to them. The rate limiter auto-evicts stale windows every 256 calls.
- `ViewModelFactory` lazily caches four shared UI adapters (`UiUserStore`, `UiMatchDataAccess`, `UiProfileNoteDataAccess`, `UiPresenceDataAccess`) and clears them on reset/dispose.
- `ConnectionService.getConversationPreview()` provides direct single-conversation lookup without page scanning.
- `ProfileService` has a narrow `ProfileService(UserStorage)` constructor (truthful) and a wide compatibility overload.
- `StorageFactory` is split into explicit private assembly stages and internal records.
- `StorageFactory.buildSqlDatabase(...)` is the production/runtime composition path; `buildH2(...)` and `buildInMemory(...)` remain the H2-only compatibility/test paths.
- `DatabaseManager` now accepts runtime `AppConfig.StorageConfig` (dialect/url/username/query timeout) and applies PostgreSQL `statement_timeout` instead of H2 `QUERY_TIMEOUT` when appropriate.
- `DatabaseDialect` and `SqlDialectSupport` centralize the H2/PostgreSQL runtime and SQL divergence points.
- `ServiceRegistry.Builder` supports an explicit `locationService(...)` setter with a compatibility fallback.
- `VerificationUseCases` owns email/phone verification flows so adapters do not mutate verification state directly.
- All current production ViewModels extend `BaseViewModel`.
- `UiThemeService` is the ViewModel-facing theme facade.
- `LocationService` is the single shared location engine across CLI, JavaFX, and REST.
- `RestApiTestFixture` is the shared `ServiceRegistry` builder for targeted REST/API tests.
- `UiAsyncTestSupport` complements `JavaFxTestSupport` for shared async ViewModel test helpers.
- `DevDataSeeder` remains environment-gated (`DATING_APP_SEED_DATA=true`) and idempotent.
- `CandidateFinder` caches candidates per user with TTL; cache hits return directly without re-fetching.
- `ViewModelAsyncScope` evicts `latestVersions` keys when tasks complete to prevent unbounded growth.
- `DatabaseManager.shutdown()` resets its `initialized` flag; `resetInstance()` calls `shutdown()` first.
- `DefaultDailyPickService` requires only `DailyLimitService`, `MatchingService`, and `AppConfig` — no storage dependencies.
- ViewModel `setErrorHandler` methods delegate to `BaseViewModel.setErrorSink()` via the `notifyError(msg, throwable)` dispatch path.
- `PostgresqlRuntimeSmokeTest` is the local-first PostgreSQL runtime validation seam; `start_local_postgres.ps1`, `run_postgresql_smoke.ps1`, and `stop_local_postgres.ps1` support project-local PostgreSQL validation without Docker.

## Build & Quality Commands

```powershell
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify

# Optional local PostgreSQL runtime validation
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\stop_local_postgres.ps1
```

When selecting multiple tests from PowerShell, prefer `mvn --% ...` so comma-separated `-Dtest=` values are passed through intact.

## Build constraints from `pom.xml`

- Java release `25` with preview enabled
- Surefire test JVMs use preview/native-access flags
- Spotless (Palantir Java Format) checked in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage gate in `verify` (minimum `0.60`)

## Key pattern files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` — bootstrap, config loading, and Jackson mix-in pattern
- `src/main/java/datingapp/core/ServiceRegistry.java` — central service and use-case wiring
- `src/main/java/datingapp/storage/DatabaseDialect.java` — runtime dialect detection and selection
- `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java` — dialect-specific upsert and duration SQL generation
- `src/main/java/datingapp/app/api/RestApiServer.java` and `RestApiDtos.java` — REST route and DTO patterns
- `src/main/java/datingapp/core/profile/LocationService.java` — shared location dataset and formatting rules
- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java` — shared ViewModel lifecycle shell
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` — UI boundary adapters
- `src/test/java/datingapp/ui/JavaFxTestSupport.java` and `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` — shared UI test helpers
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — shared REST/API test graph builder
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java` — factory caching, lifecycle, and session-binding contracts
- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java` — live local PostgreSQL smoke validation for the runtime storage path

## Keep these out of new work

- `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, and `ui/controller` references
- stale architecture docs when they conflict with source
- new direct framework/database/web dependencies from `core/`

## Instruction-stack note

Use `.github/copilot-instructions.md` for the shortest always-on rules. Use this file for the verified repo map and current gotchas. Use `AGENTS.md` for execution workflow and validation discipline.
