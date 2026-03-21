# Dating App Architecture

> **Last verified against source code:** 2026-03-21
> **Java files:** 140 main + 107 test = 247
> **Java LOC (`tokei`):** 66,698 total / 52,170 code / 8,217 blank / 6,311 comments

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
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
    event/handlers/{AchievementEventHandler,MetricsEventHandler,NotificationEventHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/ProfileUseCases.java
      social/SocialUseCases.java
  core/
    AppClock,AppConfig,AppConfigValidator,AppSession,EnumSetUtil,LoggingSupport,ServiceRegistry,TextUtil
    i18n/I18n.java
    model/{User,Match,ProfileNote}
    connection/{ConnectionModels,ConnectionService}
    matching/{CandidateFinder,CompatibilityCalculator,DailyLimitService,DailyPickService,DefaultCompatibilityCalculator,DefaultDailyLimitService,DefaultDailyPickService,DefaultStandoutService,InterestMatcher,LifestyleMatcher,MatchingService,MatchQualityService,RecommendationService,Standout,StandoutService,TrustSafetyService,UndoService}
    metrics/{AchievementService,ActivityMetricsService,DefaultAchievementService,EngagementDomain,SwipeState}
    profile/{MatchPreferences,ProfileService,ValidationService}
    storage/{AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy}
  storage/
    DatabaseManager.java
    StorageFactory.java
    jdbi/{JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    DatingApp,ImageCache,LocalPhotoStore,NavigationService,UiAnimations,UiComponents,UiConstants,UiDialogs,UiFeedbackService,UiPreferencesStore,UiUtils
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,NotesController,PreferencesController,ProfileController,ProfileFormValidator,ProfileViewController,SafetyController,SocialController,StandoutsController,StatsController}
    viewmodel/{ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,NotesViewModel,PreferencesViewModel,ProfileViewModel,SafetyViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}
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
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
MatchingHandler matching = new MatchingHandler(
  MatchingHandler.Dependencies.fromServices(services, session, inputReader, profile::completeProfile));
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
