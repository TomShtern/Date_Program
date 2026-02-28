# Dating App Architecture

> **Last verified against source code:** 2026-02-28
> **Java files:** 102 main + 77 test = 179
> **Java LOC (`tokei`):** 55,616 total / 42,154 code / 11,776 blank / 1,686 comments

This document describes current architecture from source (`src/main/java`, `src/test/java`, `pom.xml`).

---

## 1. Layer Model

```text
┌──────────────────────────────────────────────────────────────┐
│ PRESENTATION                                                 │
│   CLI (app/cli), JavaFX UI (ui), REST API (app/api)         │
├──────────────────────────────────────────────────────────────┤
│ APPLICATION ORCHESTRATION                                   │
│   app/usecase/* (matching, messaging, profile, social)      │
├──────────────────────────────────────────────────────────────┤
│ DOMAIN                                                       │
│   core/* (models, services, storage interfaces, utilities)  │
├──────────────────────────────────────────────────────────────┤
│ INFRASTRUCTURE                                               │
│   storage/* (JDBI implementations, schema, DB manager)      │
└──────────────────────────────────────────────────────────────┘
```

Key constraints:

- `core/` contains domain logic and storage interfaces.
- Infrastructure adapters live in `storage/`.
- UI and CLI adapters consume `ServiceRegistry` and use-case bundles.

---

## 2. Package Layout (Code-Verified)

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

## 3. Composition and Startup

### Shared bootstrap

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();
```

### CLI composition root (`Main.java`)

```java
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matching = new MatchingHandler(MatchingHandler.Dependencies.fromServices(services, session, inputReader));
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);
```

### JavaFX composition root (`ui/DatingApp.java`)

```java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

---

## 4. Domain Ownership Rules

- User-related enums live in `User`:
  - `User.Gender`
  - `User.UserState`
  - `User.VerificationMethod`
- Match-related enums live in `Match`:
  - `Match.MatchState`
  - `Match.MatchArchiveReason`
- `ProfileNote` is standalone: `core/model/ProfileNote.java`

Additional core conventions:

- Use `AppClock.now()` in domain/service logic.
- Use deterministic pair IDs via `generateId(UUID a, UUID b)` for two-user aggregates.
- Prefer result records for business failure paths.

---

## 5. UI Concurrency Architecture

ViewModels now use shared async infrastructure in `ui/async`:

- `UiThreadDispatcher`
- `JavaFxUiThreadDispatcher`
- `ViewModelAsyncScope`
- `TaskPolicy`
- `TaskHandle`
- `AsyncErrorRouter`

This standardizes:

- UI dispatch behavior
- latest-wins task semantics
- loading-state orchestration
- cancellation/disposal behavior
- async error routing

---

## 6. Build and Quality Gates (`pom.xml`)

- Java release 25 with preview features enabled
- Spotless check bound to `verify` (Palantir Java Format)
- Checkstyle bound to `validate`
- PMD bound to `verify`
- JaCoCo check bound to `verify` with minimum line coverage `0.60`

Recommended gate:

```bash
mvn spotless:apply verify
```

---

## 7. Dependency Direction Summary

```text
ui, app/cli, app/api
        │
        ▼
   app/usecase
        │
        ▼
      core
        ▲
        │
    storage (implements core/storage interfaces)
```

Avoid:

- importing `storage/*` or framework APIs into `core/*`
- direct `core.storage` dependencies in ViewModels (use `UiDataAdapters`)
