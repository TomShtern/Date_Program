# Dating App - Qwen Code Context

**Java 25** (preview) | **JavaFX 25** | **Maven** | **H2 + JDBI**

> **CODE IS THE ONLY SOURCE OF TRUTH**

---

## Quick Facts (Verified 2026-02-28)

- Entry points:
  - `Main.java` (CLI)
  - `ui/DatingApp.java` (JavaFX)
  - `app/api/RestApiServer.java` (REST)
- Bootstrap: `ApplicationStartup.initialize()`
- Java files: `116` main + `88` test = `204`
- Java LOC (`tokei`): `56,482 total / 43,327 code`

---

## Canonical Package Structure

```text
datingapp/
  Main.java
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
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

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matching = new MatchingHandler(MatchingHandler.Dependencies.fromServices(services, session, inputReader));
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);

ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

---

## Build / Test / Verify

```bash
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify
```

`pom.xml` quality gates:

- Spotless check in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage minimum `0.60`

---

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 retries append ":CONFLICT".
1|2026-02-22 00:00:00|agent:qwen_code|docs|Complete QWEN.md rewrite from SOURCE CODE: 87 main files (22,325 LOC), 65 tests (14,825 LOC), 18 tables, 10 services, 5 storage interfaces, Java 25, verified package structure, actual nested types, bootstrap flow|QWEN.md
2|2026-02-28 13:35:00|agent:github_copilot|docs-source-truth-sync|Rewrote QWEN.md from current source snapshot (179 Java files, app/usecase layer, ui/async layer, updated wiring)|QWEN.md
3|2026-03-01 03:20:00|agent:github_copilot|docs-metrics-refresh|Updated QWEN.md snapshot metrics to current source counts and LOC|QWEN.md
---AGENT-LOG-END---
