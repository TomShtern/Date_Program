# GEMINI.md — AI Agent Operational Context

> **Verified against source code:** 2026-03-01
> **Codebase snapshot:** 116 main + 88 test Java files (204 total), 56,468 Java LOC (43,313 code)

---

## 1. Project Identity

Java 25 dating app with three adapters sharing one core:

- CLI (`Main.java`, `app/cli/*`)
- JavaFX UI (`ui/*`)
- REST API (`app/api/RestApiServer.java`)

Foundational rules:

- `core/` is framework-free domain logic.
- Business time uses `AppClock.now()`.
- Two-user aggregates use deterministic pair IDs (`generateId(a, b)`).
- Service business failures return result records where designed.

---

## 2. Architecture (Current Source Tree)

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

---

## 3. Critical Model Ownership

- `User` nested enums:
  - `User.Gender`
  - `User.UserState`
  - `User.VerificationMethod`
- `Match` nested enums:
  - `Match.MatchState`
  - `Match.MatchArchiveReason`
- `ProfileNote` is standalone: `core/model/ProfileNote.java`

---

## 4. Entry Wiring

```java
// Shared bootstrap
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring in Main.java
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
MatchingHandler matching = new MatchingHandler(
  MatchingHandler.Dependencies.fromServices(services, session, inputReader, profile::completeProfile));
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);

// JavaFX wiring in DatingApp.java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

---

## 5. ViewModel Async Standard

Use shared abstractions in `ui/async`:

- `UiThreadDispatcher`
- `JavaFxUiThreadDispatcher`
- `ViewModelAsyncScope`
- `TaskPolicy`
- `TaskHandle`
- `AsyncErrorRouter`

Avoid ad-hoc concurrency patterns in ViewModels when these primitives apply.

---

## 6. Build and Quality

```bash
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify
```

From `pom.xml`:

- Java release 25 with preview enabled
- Spotless check in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage minimum 0.60 in `verify`

---

## 7. Never Do These

- Import framework/DB APIs into `core/`.
- Use removed standalone model enum imports (`core.model.Gender`, etc.).
- Use `Instant.now()` in domain/service logic.
- Reintroduce legacy names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`).
- Return mutable internals directly.
- Import `core.storage.*` directly in ViewModels (use `UiDataAdapters`).

---

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries.
1|2026-01-30 18:50:00|agent:antigravity|docs|Update GEMINI.md to reflect current tech stack (JDBI, Jackson, JavaFX 25) and architecture|GEMINI.md
2|2026-02-15 18:33:00|agent:antigravity|docs|Complete rewrite: verified all patterns against actual source code, added copy-paste code patterns, critical gotchas, wiring checklist, exact pom.xml versions, data flows, test architecture|GEMINI.md
3|2026-02-28 13:35:00|agent:github_copilot|docs-source-truth-sync|Rewrote GEMINI.md from current source snapshot including app/usecase and ui/async layers|GEMINI.md
---AGENT-LOG-END---
