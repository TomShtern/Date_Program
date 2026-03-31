> 🚀 **VERIFIED & UPDATED: 2026-03-31**
> This document has been refreshed against the current codebase and build output as of this date.

<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1, 2) locate affected doc fragment, 3) archive with <!--ARCHIVE:SEQ:agent:scope-->,
# 4) apply minimal edits, 5) append ChangeStamp to file-end changelog and edited fragment.
<!--/AGENT-DOCSYNC-->

# CLAUDE.md

Second-level guidance for AI coding agents working in this repository.

> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`
>
> **Source of truth:** code only (`src/main/java`, `src/test/java`, `pom.xml`).

<!--ARCHIVE:32:agent:github_copilot:scope:docs-hierarchy-refresh--> Replaced the stale source snapshot and operational guidance with a current hierarchy-aware repo map based on the 2026-03-30 codebase and build output.

## Environment

- Windows 11
- PowerShell 7.x
- VS Code Insiders (sometimes IntelliJ)
- Java 25 + JavaFX 25.0.2
- Maven

## Verified Source Snapshot (2026-03-31)

- Java files: **326 total** (`152` main / `174` test)
- Java LOC (`tokei src`, Java only): **96,157 total / 77,493 code / 13,642 blank / 5,022 comments**

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
    {DatabaseManager,DevDataSeeder,StorageFactory}.java
    jdbi/{JdbiAccountCleanupStorage,JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
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

## Build & Quality Commands

```powershell
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify
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
- `src/main/java/datingapp/app/api/RestApiServer.java` and `RestApiDtos.java` — REST route and DTO patterns
- `src/main/java/datingapp/core/profile/LocationService.java` — shared location dataset and formatting rules
- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java` — shared ViewModel lifecycle shell
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` — UI boundary adapters
- `src/test/java/datingapp/ui/JavaFxTestSupport.java` and `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` — shared UI test helpers
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — shared REST/API test graph builder
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java` — factory caching, lifecycle, and session-binding contracts

## Keep these out of new work

- `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, and `ui/controller` references
- stale architecture docs when they conflict with source
- new direct framework/database/web dependencies from `core/`

## Instruction-stack note

Use `.github/copilot-instructions.md` for the shortest always-on rules. Use this file for the verified repo map and current gotchas. Use `AGENTS.md` for execution workflow and validation discipline.

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Entries 1-9 archived.
10|2026-02-03 20:30:00|agent:claude_code|docs-optimize|Added critical gotchas and condensed architecture guidance|CLAUDE.md
11|2026-02-05 00:00:00|agent:claude_code|docs-ui-patterns|Added ViewModel error-handling and navigation context patterns|CLAUDE.md
12|2026-02-07 22:25:00|agent:claude_code|docs-audit|Refreshed stale stats and utility class references|CLAUDE.md
13|2026-02-08 11:15:00|agent:claude_code|config-audit|Synced quality-gate and command documentation|CLAUDE.md
14|2026-02-08 12:30:00|agent:claude_code|docs-pmd-gotcha|Added PMD+Spotless gotcha guidance|CLAUDE.md
15|2026-02-08 18:00:00|agent:claude_code|build-discipline|Added single-run output filtering discipline|CLAUDE.md
16|2026-02-10 00:00:00|agent:claude_code|docs-major-refactor|Updated package structure and model/type guidance|CLAUDE.md
17|2026-02-11 12:00:00|agent:github_copilot|scope:codebase-consolidation|Documented codebase consolidation changes|CLAUDE.md
18|2026-02-11 22:52:00|agent:codex|scope:docs-stats-sync|Synced documentation to verified baseline|CLAUDE.md
19|2026-02-13 10:15:00|agent:github_copilot|scope:test-output-workflow|Documented regular-first test flow and verbose reruns|CLAUDE.md
20|2026-02-14 00:00:00|agent:claude_code|scope:docs-domain-reorg|Updated domain-driven package references|CLAUDE.md
21|2026-02-18 00:00:00|agent:claude_code|scope:docs-full-sync|Performed broad source-verified synchronization|CLAUDE.md
22|2026-02-19 00:00:00|agent:github_copilot|scope:renesting-implementation|Documented nested enum/type ownership changes|CLAUDE.md
23|2026-02-21 00:00:00|agent:claude_code|scope:code-verified-sync|Fixed stale package and count references|CLAUDE.md
24|2026-02-21 16:30:00|agent:github_copilot|scope:docs-source-truth-refresh|Refreshed stats and wiring snippets|CLAUDE.md
25|2026-02-22 04:08:00|agent:gemini|scope:config-jackson-databinding|Documented AppConfig Jackson databinding strategy|CLAUDE.md
26|2026-02-28 13:35:00|agent:github_copilot|scope:source-truth-doc-sync|Rewrote CLAUDE.md from current code snapshot including app/usecase and ui/async architecture|CLAUDE.md
27|2026-03-01 01:20:00|agent:github_copilot|scope:source-truth-doc-sync|Updated counts, package tree, and CLI wiring callback from current source|CLAUDE.md
28|2026-03-01 03:20:00|agent:github_copilot|scope:docs-metrics-refresh|Updated LOC snapshot values to current tokei output|CLAUDE.md
29|2026-03-07 00:00:00|agent:claude_code|scope:source-truth-sync|Added event handlers subpackage, InterestMatcher, use-case wiring docs, config access gotcha, updated LOC|CLAUDE.md
30|2026-03-11 00:00:00|agent:claude_code|scope:architecture-corrections|Removed non-existent PerformanceMonitor and CompatibilityScoring; fixed popup package (MatchPopupController removed, MilestonePopupController moved to screen); added SafetyController and NotesController to screen list; added MatchingCliPresenter to cli list; added Default* matching impls, AchievementService, ProfileCompletionSupport; corrected false RestApiServer claim; added 2 new gotchas (AchievementType enum split, sessionTimeout scope)|CLAUDE.md
31|2026-03-13 00:00:00|agent:claude_code|scope:code-verified-sync|Updated snapshot (267 files, 73830 LOC via tokei/fd); added LocationModels+LocationService to arch; added DevDataSeeder to storage; added 4 new gotchas (@BindMethods, Locale.ENGLISH, LocationService scope, RestApiServer localhost-only); documented 33 REST endpoints, rate limiter, localhost bind; added ProfileUseCases new commands; added Recent Updates section|CLAUDE.md
32|2026-03-30 15:15:00|agent:github_copilot|scope:docs-hierarchy-refresh|Refreshed CLAUDE.md to current source snapshot, clarified instruction hierarchy, and updated repo-specific gotchas, patterns, and verification notes|CLAUDE.md
33|2026-03-31 22:00:00|agent:github_copilot|scope:cast-remediation-sync|Updated architecture tree, file counts, LOC stats, gotchas, and architecture notes after CAST structural remediation implementation (REST helper extraction, profile boundary decomposition, composition spine cleanup, storage/messaging optimization, ViewModelFactory simplification)|CLAUDE.md
34|2026-03-31 23:15:00|agent:github_copilot|scope:code-quality-sweep|16-fix code quality pass: rate limiter eviction, dead candidate cache fix, unused param cleanup (ProfileMutationUseCases, DefaultDailyPickService, MessagingUseCases), ProfileNotesUseCases author-check dedup, ViewModelAsyncScope key eviction, TypingIndicator animation lifecycle, DatabaseManager shutdown lifecycle, ViewModel errorSink dedup (Social/Standouts/Dashboard/Notes/Safety), dead field removal. Updated LOC and architecture notes.|CLAUDE.md
---AGENT-LOG-END---
