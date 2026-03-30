> 🚀 **VERIFIED & UPDATED: 2026-03-30**
> This document merges the repo-wide rules from `.github/copilot-instructions.md`, the verified repo map from `CLAUDE.md`, and the workflow discipline from `AGENTS.md`.

# QWEN.md — Merged Repo Guide

## 1. Hierarchy and source of truth

Use this file as a model-specific merged guide, but keep the repo hierarchy in mind:

1. `.github/copilot-instructions.md` — highest-level always-on rules
2. `CLAUDE.md` — verified repo map and current gotchas
3. `AGENTS.md` — execution workflow and validation discipline

If any markdown guidance conflicts with the implementation, trust:

- `src/main/java`
- `src/test/java`
- `pom.xml`

## 2. Verified environment

- Windows 11
- PowerShell 7.x
- VS Code Insiders (sometimes IntelliJ)
- Java 25 with preview enabled
- JavaFX 25.0.2
- Maven
- Palantir Java Format / Spotless
- Java by Red Hat extension

PowerShell note: when Maven uses a comma-separated `-Dtest=...,...` list, prefer `mvn --% ...`.

## 3. Verified codebase snapshot (2026-03-30)

- Java files: **320 total** (`147` main / `173` test)
- Java LOC (`tokei src`, Java only): **93,412 total / 75,136 code / 13,289 blank / 4,987 comments**

## 4. Architecture snapshot

```text
datingapp/
  Main.java
  app/
    api/{RestApiDtos,RestApiServer}.java
    bootstrap/{ApplicationStartup,CleanupScheduler}.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingCliPresenter,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
    event/handlers/{AchievementEventHandler,MetricsEventHandler,NotificationEventHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/{ProfileUseCases,VerificationUseCases}.java
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

## 5. Composition roots and boundaries

### Shared bootstrap

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();
```

### Production composition roots

- `ApplicationStartup.initialize()`
- `ServiceRegistry`
- `ViewModelFactory`

### Boundary rules

- `core/` stays framework-agnostic.
- `app/usecase/*` is the application boundary for business flows.
- ViewModels go through `BaseViewModel`, `ui/async/*`, and `UiDataAdapters`.
- Adapters should not drift back into direct service/storage orchestration when a use-case seam exists.

## 6. Critical correctness rules

- Use nested enums from owner types:
  - `User.Gender`, `User.UserState`, `User.VerificationMethod`
  - `Match.MatchState`, `Match.MatchArchiveReason`
- `ProfileNote` is standalone: `datingapp.core.model.ProfileNote`
- Use `AppClock` as the repo time source: prefer `AppClock.now()` / `AppClock.today()`, or inject `AppClock.clock()` when a service intentionally works with a `Clock`
- Use deterministic pair IDs via `generateId(UUID a, UUID b)`
- Use injected `AppConfig` at runtime; reserve `AppConfig.defaults()` for bootstrap/composition/tests
- Record-typed JDBI parameters should use `@BindMethods`, not `@BindBean`
- Use `Locale.ENGLISH` for user-facing date formatters when month/day names are rendered
- `RestApiServer` is loopback-only; do not assume external host access
- `LocationService` is the single location engine; only `IL` is fully supported/selectable today
- `UiDataAdapters.UiPage<T>` is the UI paging boundary; do not leak `PageData` into ViewModels
- All current production ViewModels extend `BaseViewModel`

## 7. Current repo-specific notes

- `VerificationUseCases` owns email/phone verification flows.
- `UiThemeService` is the ViewModel-facing theme facade.
- `RestApiTestFixture` is the shared `ServiceRegistry` builder for REST/API tests.
- `UiAsyncTestSupport` complements `JavaFxTestSupport` for async UI tests.
- `DevDataSeeder` is environment-gated (`DATING_APP_SEED_DATA=true`) and idempotent.
- The candidates route in `RestApiServer` remains the explicit direct-read exception.

## 8. Build, test, and quality commands

```powershell
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test
mvn spotless:apply verify
```

`pom.xml` quality gates:

- Java 25 with preview enabled
- Surefire preview/native-access args
- Checkstyle in `validate`
- Spotless in `verify`
- PMD in `verify`
- JaCoCo line coverage minimum `0.60`

## 9. Agent workflow defaults

- Prefer `ast-grep` when structure matters; use plain-text search only when structure is irrelevant.
- Use read-only subagents for isolated codebase investigation.
- Use execution helpers for Maven/test runs and summarize the result back into the main workflow.
- Keep a todo list for multi-step work.
- Read the relevant files fully before editing them.
- Keep diffs small and avoid unrelated refactors.
- Verify in this order:
  1. touched-file errors
  2. targeted tests
  3. broader smoke tests if needed
  4. full `mvn spotless:apply verify` before completion claims

## 10. Shared test support to prefer

- `src/test/java/datingapp/ui/JavaFxTestSupport.java`
- `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java`
- `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- `src/test/java/datingapp/core/testutil/TestUserFactory.java`
- `src/test/java/datingapp/app/testutil/TestEventBus.java`
- `src/test/java/datingapp/core/testutil/TestAchievementService.java`

## 11. Key pattern files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/core/profile/LocationService.java`
- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- `src/test/java/datingapp/ui/JavaFxTestSupport.java`
- `src/test/java/datingapp/app/api/RestApiTestFixture.java`

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 retries append ":CONFLICT".
1|2026-02-22 00:00:00|agent:qwen_code|docs|Complete QWEN.md rewrite from SOURCE CODE: 87 main files (22,325 LOC), 65 tests (14,825 LOC), 18 tables, 10 services, 5 storage interfaces, Java 25, verified package structure, actual nested types, bootstrap flow|QWEN.md
2|2026-02-28 13:35:00|agent:github_copilot|docs-source-truth-sync|Rewrote QWEN.md from current source snapshot (179 Java files, app/usecase layer, ui/async layer, updated wiring)|QWEN.md
3|2026-03-01 03:20:00|agent:github_copilot|docs-metrics-refresh|Updated QWEN.md snapshot metrics to current source counts and LOC|QWEN.md
4|2026-03-27 00:00:00|agent:qwen_code|docs|Full QWEN.md refresh from source code: 298 Java files (144 main/154 test), verified package structure, updated entrypoint wiring, added dependencies table|QWEN.md
5|2026-03-30 15:20:00|agent:github_copilot|scope:docs-hierarchy-refresh|Rewrote QWEN.md as a merged repo guide aligned with copilot-instructions, CLAUDE.md, AGENTS.md, and current source/build facts|QWEN.md
---AGENT-LOG-END---
