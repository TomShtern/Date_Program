
> 🚀 **VERIFIED & UPDATED: 2026-03-11**
> This document has been programmatically verified against the codebase as of this date.

<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1, 2) locate affected doc fragment, 3) archive with <!--ARCHIVE:SEQ:agent:scope-->,
# 4) apply minimal edits, 5) append ChangeStamp to file-end changelog and edited fragment.
<!--/AGENT-DOCSYNC-->

# CLAUDE.md

Guidance for AI coding agents working in this repository.

> **Source of truth:** code only (`src/main/java`, `src/test/java`, `pom.xml`).

## Environment

- Windows 11
- PowerShell
- VS Code Insiders (sometimes IntelliJ)
- Java 25 + JavaFX 25
- Maven

## Verified Source Snapshot (2026-03-11)

- Java files: **247 total**
- Java LOC (`tokei`): **66,698 total / 52,170 code / 9,728 blank / 4,800 comments**

## Architecture (code-verified)

```text
datingapp/
  Main.java
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
    event/handlers/{AchievementEventHandler,MetricsEventHandler,NotificationEventHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/ProfileUseCases.java
      social/SocialUseCases.java
  core/
    AppClock,AppConfig,AppSession,EnumSetUtil,LoggingSupport,PerformanceMonitor,ServiceRegistry,TextUtil
    model/{User,Match,ProfileNote}
    connection/{ConnectionModels,ConnectionService}
    matching/{CandidateFinder,CompatibilityScoring,InterestMatcher,LifestyleMatcher,MatchingService,MatchQualityService,RecommendationService,Standout,TrustSafetyService,UndoService}
    metrics/{ActivityMetricsService,EngagementDomain,SwipeState}
    profile/{MatchPreferences,ProfileService,ValidationService}
    storage/{AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}
    time/{DefaultTimePolicy,TimePolicy}
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy,WorkflowDecision}
  storage/
    DatabaseManager.java
    StorageFactory.java
    jdbi/{JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    DatingApp,NavigationService,ImageCache,UiAnimations,UiComponents,UiConstants,UiFeedbackService,UiUtils
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}
    popup/{MatchPopupController,MilestonePopupController}
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,PreferencesController,ProfileController,SocialController,StandoutsController,StatsController}
    viewmodel/{ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,PreferencesViewModel,ProfileViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}
```

## Critical Gotchas

| Gotcha                     | Wrong                                                          | Correct                                         |
|----------------------------|----------------------------------------------------------------|-------------------------------------------------|
| User enum imports          | `core.model.Gender`                                            | `User.Gender`                                   |
| Match enum imports         | `core.model.MatchState`                                        | `Match.MatchState`                              |
| ProfileNote import         | `User.ProfileNote`                                             | `core.model.ProfileNote`                        |
| Domain clock               | `Instant.now()`                                                | `AppClock.now()`                                |
| Pair IDs                   | `a + "_" + b`                                                  | deterministic `generateId(a,b)`                 |
| ViewModel threading        | ad-hoc `Thread.ofVirtual()` + `Platform.runLater()` everywhere | shared `ui/async` scope (`ViewModelAsyncScope`) |
| ViewModel storage coupling | direct `core.storage.*` imports                                | `UiDataAdapters` interfaces                     |
| Legacy bootstrap names     | `AppBootstrap`, `HandlerFactory`                               | `ApplicationStartup` + `fromServices(...)`      |
| Use-case construction      | `new MatchingUseCases(...)` in callers                         | `services.getMatchingUseCases()` from registry  |
| Config access              | `AppConfig.defaults()` in runtime code                         | injected `AppConfig` via `ServiceRegistry`      |

## Entrypoints and wiring

```java
// Shared bootstrap
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring (Main.java)
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
MatchingHandler matching = new MatchingHandler(MatchingHandler.Dependencies.fromServices(services, session, inputReader, profile::completeProfile));
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);

// JavaFX wiring (DatingApp.java)
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Use-Case Layer and Event Bus

`ServiceRegistry` internally constructs use-case instances from its core services:
- `getMatchingUseCases()`, `getMessagingUseCases()`, `getProfileUseCases()`, `getSocialUseCases()`

Callers (CLI handlers, REST API, ViewModels) should obtain use cases from the registry — never construct them directly.

`RestApiServer` delegates business operations (like/pass, send message, list conversations) through the use-case layer, not core services directly.

`AppEventBus` / `InProcessAppEventBus` provides in-process domain event dispatching. Event handlers live in `app/event/handlers/` and handle cross-cutting concerns (achievements, metrics, notifications).

## Async ViewModel Standard (current)

Use `ui/async` package primitives:

- `UiThreadDispatcher`
- `JavaFxUiThreadDispatcher`
- `ViewModelAsyncScope`
- `TaskPolicy`
- `TaskHandle`
- `AsyncErrorRouter`

This is now the shared pattern for ViewModel background work, loading-state tracking, latest-wins semantics, error routing, and disposal cancellation.

## Build & Quality Commands

```bash
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify
```

## Build constraints from `pom.xml`

- Java release `25` with preview enabled
- Spotless (Palantir Java Format) checked in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage gate in `verify` (minimum `0.60`)

## Never do these

- Import framework/DB APIs in `core/`
- Reintroduce removed legacy names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`)
- Import removed standalone enums (`core.model.Gender`, etc.)
- Return mutable internals directly
- Forget `touch()` updates on mutable entities
- Use `Instant.now()` in domain/service code
- Bypass `ui/async` abstractions in ViewModels for routine async flows
- Construct use-case classes directly — obtain them from `ServiceRegistry`
- Use `AppConfig.defaults()` in runtime service code — inject via `ServiceRegistry`

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
---AGENT-LOG-END---
