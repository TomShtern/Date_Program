
> 🚀 **VERIFIED & UPDATED: 2026-03-09**
> This document has been programmatically verified against the codebase as of this date.

# AGENTS.md - Source-of-Truth Development Guide

## Verified Environment Snapshot

- **OS:** Windows 11
- **Shell:** PowerShell 7.x
- **IDE:** VS Code Insiders (Java by Red Hat extension)
- **Java:** 25 (preview enabled)
- **JavaFX:** 25.0.2
- **Build:** Maven

## Verified Codebase Snapshot (from source)

- **Total Java files:** **231**
- `tokei` (Java only):
  - **Total lines:** 63,974
  - **Code lines:** 50,038
  - **Blank lines:** 9,358
  - **Comment lines:** 4,578

## Required Build / Test Commands

```bash
# Build and run
mvn compile && mvn exec:exec
mvn javafx:run

# Tests
mvn test
mvn -Ptest-output-verbose test
mvn -Ptest-output-verbose -Dtest=StatsHandlerTest test
mvn -Ptest-output-verbose -Dtest="StatsHandlerTest#displaysUnlockedAchievements" test

# Quality gate
mvn spotless:apply verify
```

## Architecture (Code-Verified)

```text
datingapp/
  Main.java
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
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
    matching/{CandidateFinder,CompatibilityScoring,LifestyleMatcher,MatchingService,MatchQualityService,RecommendationService,Standout,TrustSafetyService,UndoService}
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

## Entry Points and Wiring

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
nav.initialize(primaryStage);
```

## Core Rules (Do / Don’t)

### Do

- Keep `core/` free from framework/DB/UI imports.
- Use `AppClock.now()` for domain/service time.
- Use deterministic pair IDs via `generateId(UUID a, UUID b)`.
- Use `EnumSetUtil.safeCopy(...)` for EnumSet safety.
- Return result records for business-flow failures.
- In ViewModels, use shared async abstraction under `ui/async` (`ViewModelAsyncScope`, `AsyncErrorRouter`, dispatcher abstractions).
- Use `UiDataAdapters` interfaces in ViewModels (not direct `core.storage` imports).

### Don’t

- Don’t import removed standalone enums (`core.model.Gender`, `core.model.UserState`, etc.).
- Don’t use `AppBootstrap`, `HandlerFactory`, `Toast`, or `UiSupport`.
- Don’t expose mutable internal collections directly.
- Don’t forget entity `touch()` updates in mutating setters.
- Don’t use ad-hoc threading patterns in ViewModels where shared async scope applies.

## Current Model Ownership

- `User` + nested enums: `User.Gender`, `User.UserState`, `User.VerificationMethod`
- `Match` + nested enums: `Match.MatchState`, `Match.MatchArchiveReason`
- `ProfileNote`: standalone record in `core/model/ProfileNote.java`

## Quality Gates (from `pom.xml`)

- Java compiler release 25 + preview enabled
- Surefire with preview/native-access args
- Spotless (Palantir Java Format) bound to `verify`
- Checkstyle bound to `validate`
- PMD bound to `verify`
- JaCoCo check bound to `verify` with line coverage minimum 0.60

## Useful System Tools

- `sg` (ast-grep): structural code search
- `rg`: fast text search
- `fd`: file discovery
- `tokei`: source metrics

Prefer `ast-grep` for syntax-aware code queries.
