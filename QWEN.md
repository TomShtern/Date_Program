> 🚀 **VERIFIED & UPDATED: 2026-03-27**
> This document has been programmatically verified against the codebase as of this date.

# Dating App - Qwen Code Context

**Java 25** (preview) | **JavaFX 25** | **Maven** | **H2 + JDBI**

> **CODE IS THE ONLY SOURCE OF TRUTH**

---

## Quick Facts (Verified 2026-03-27)

- Entry points:
  - `Main.java` (CLI)
  - `ui/DatingApp.java` (JavaFX)
  - `app/api/RestApiServer.java` (REST)
- Bootstrap: `ApplicationStartup.initialize()`
- Java files: `298` total (144 main / 154 test)

---

## Canonical Package Structure

```text
datingapp/
  Main.java
  app/
    api/{RestApiDtos,RestApiServer}.java
    bootstrap/{ApplicationStartup,CleanupScheduler}.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingCliPresenter,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
    event/handlers/{AchievementEventHandler,MetricsEventHandler,NotificationEventHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/ProfileUseCases.java
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
    {DatingApp,ImageCache,LocalPhotoStore,NavigationService,UiAnimations,UiComponents,UiConstants,UiDialogs,UiFeedbackService,UiPreferencesStore,UiUtils}.java
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,PollingTaskHandle,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}.java
    popup/{MatchPopupController,MilestonePopupController}.java
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,NotesController,PreferencesController,ProfileController,ProfileFormValidator,ProfileViewController,SafetyController,SocialController,StandoutsController,StatsController}.java
    viewmodel/{BaseViewModel,ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,NotesViewModel,PreferencesViewModel,ProfileReadOnlyViewModel,ProfileViewModel,SafetyViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}.java
```

---

## High-Risk Accuracy Rules

1. Use nested enums from owner types:
   - `User.Gender`, `User.UserState`, `User.VerificationMethod`
   - `Match.MatchState`, `Match.MatchArchiveReason`
2. `ProfileNote` is standalone (`core/model/ProfileNote.java`).
3. Use `AppClock.now()` in domain/service logic.
4. Keep deterministic pair IDs for two-user aggregates.
5. Prefer use-case orchestration from `app/usecase/*` where wired by `ServiceRegistry`.
6. In ViewModels, use `ui/async/*` abstractions over ad-hoc async patterns.
7. ViewModels consume `UiDataAdapters` interfaces, not direct `core.storage` imports.

---

## Entrypoint Wiring Reference

### CLI (`Main.java`)

```java
ServiceRegistry services = ApplicationStartup.initialize();
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
AppSession session = AppSession.getInstance();

ProfileHandler profileHandler = ProfileHandler.fromServices(services, session, inputReader);
MatchingHandler matchingHandler = new MatchingHandler(
    MatchingHandler.Dependencies.fromServices(services, session, inputReader, profileHandler::completeProfile));
SafetyHandler safetyHandler = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler statsHandler = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messagingHandler = MessagingHandler.fromServices(services, session, inputReader);
```

### JavaFX (`DatingApp.java`)

```java
ServiceRegistry serviceRegistry = ApplicationStartup.initialize();
ViewModelFactory viewModelFactory = new ViewModelFactory(serviceRegistry);
NavigationService navigationService = NavigationService.getInstance();
navigationService.setViewModelFactory(viewModelFactory);
navigationService.setPreferencesStore(viewModelFactory.getPreferencesStore());
navigationService.initialize(primaryStage);
```

---

## Build / Test / Verify

```powershell
# Build and run CLI
mvn compile && mvn exec:exec

# Run JavaFX GUI
mvn javafx:run

# Tests
mvn test
mvn -Ptest-output-verbose test

# Quality gate
mvn spotless:apply verify
```

`pom.xml` quality gates:

- Spotless check in `verify` (Palantir Java Format)
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage minimum `0.60`

---

## Key Dependencies (from pom.xml)

| Library   | Version | Purpose               |
|-----------|---------|-----------------------|
| JavaFX    | 25.0.2  | GUI framework         |
| H2        | 2.4.240 | Embedded database     |
| JDBI      | 3.51.0  | SQL abstraction       |
| JUnit     | 5.14.2  | Testing               |
| Javalin   | 6.7.0   | REST API              |
| AtlantaFX | 2.1.0   | Theme (GitHub Primer) |
| Ikonli    | 12.4.0  | Icon library          |
| Jackson   | 2.21.0  | JSON processing       |
| Logback   | 1.5.28  | Logging               |
| HikariCP  | 6.3.0   | Connection pooling    |

---

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 retries append ":CONFLICT".
1|2026-02-22 00:00:00|agent:qwen_code|docs|Complete QWEN.md rewrite from SOURCE CODE: 87 main files (22,325 LOC), 65 tests (14,825 LOC), 18 tables, 10 services, 5 storage interfaces, Java 25, verified package structure, actual nested types, bootstrap flow|QWEN.md
2|2026-02-28 13:35:00|agent:github_copilot|docs-source-truth-sync|Rewrote QWEN.md from current source snapshot (179 Java files, app/usecase layer, ui/async layer, updated wiring)|QWEN.md
3|2026-03-01 03:20:00|agent:github_copilot|docs-metrics-refresh|Updated QWEN.md snapshot metrics to current source counts and LOC|QWEN.md
4|2026-03-27 00:00:00|agent:qwen_code|docs|Full QWEN.md refresh from source code: 298 Java files (144 main/154 test), verified package structure, updated entrypoint wiring, added dependencies table|QWEN.md
---AGENT-LOG-END---
