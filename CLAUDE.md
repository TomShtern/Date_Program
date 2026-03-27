
> 🚀 **VERIFIED & UPDATED: 2026-03-27**
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

## Verified Source Snapshot (2026-03-27)

- Java files: **296 total** (144 `src/main` + 152 `src/test`, via `find src/ -name "*.java" | wc -l`)

## Architecture (code-verified)

```text
datingapp/
  Main.java
  app/
    api/{RestApiServer,RestApiDtos}.java
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
    AppClock,AppConfig,AppConfigValidator,AppSession,EnumSetUtil,LoggingSupport,ServiceRegistry,TextUtil
    i18n/I18n
    model/{User,Match,ProfileNote,LocationModels}
    connection/{ConnectionModels,ConnectionService}
    matching/{CandidateFinder,CompatibilityCalculator,DefaultCompatibilityCalculator,DailyLimitService,DefaultDailyLimitService,DailyPickService,DefaultDailyPickService,InterestMatcher,LifestyleMatcher,MatchingService,MatchQualityService,ModerationAuditEvent,ModerationAuditLogger,RecommendationService,Standout,StandoutService,DefaultStandoutService,TrustSafetyService,UndoService}
    metrics/{AchievementService,ActivityMetricsService,DefaultAchievementService,EngagementDomain,SwipeState}
    profile/{LocationService,MatchPreferences,ProfileCompletionSupport,ProfileService,SanitizerUtils,ValidationService}
    storage/{AccountCleanupStorage,AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy,WorkflowDecision}
  storage/
    DatabaseManager.java
    StorageFactory.java
    DevDataSeeder.java
    jdbi/{JdbiAccountCleanupStorage,JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    DatingApp,ImageCache,LocalPhotoStore,NavigationService,UiAnimations,UiComponents,UiConstants,UiDialogs,UiFeedbackService,UiPreferencesStore,UiUtils
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,PollingTaskHandle,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,NotesController,PreferencesController,ProfileController,ProfileFormValidator,ProfileViewController,SafetyController,SocialController,StandoutsController,StatsController}
    viewmodel/{BaseViewModel,ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,NotesViewModel,PreferencesViewModel,ProfileReadOnlyViewModel,ProfileViewModel,SafetyViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}
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
| Achievement popup loading  | Manual `Dialog` in MatchingController for match popup          | Load `/fxml/achievement_popup.fxml` via `FXMLLoader`, add root to `NavigationService.getRootStack()`, call `popup.showAchievement(Achievement)` |
| Session timeout scope      | Assuming `sessionTimeoutMinutes` auto-logs-out users           | Only expires **swipe/metrics sessions** in `ActivityMetricsService`; `AppSession` (login) has no auto-logout |
| JDBI record binding        | `@BindBean ProfileNote note` in JDBI SQL objects               | `@BindMethods ProfileNote note` — `@BindBean` uses Java Beans `getX()` introspection which fails on records; `@BindMethods` works with record accessor names |
| Date formatter locale      | `DateTimeFormatter.ofPattern("dd MMM")`                        | `DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)` — JVM default locale renders Hebrew/other month names on non-English systems |
| LocationService scope      | Treating all 5 listed countries as available                   | Only Israel (`IL`) is production-ready; US/GB/CA/AU listed as coming soon — `country.available()` returns false for them; reverse-lookup only works for Israeli coordinates |
| RestApiServer binding      | Assuming server binds to all interfaces (0.0.0.0)             | Server binds **loopback only** (`127.0.0.1`) — not accessible from external hosts by design |

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

`RestApiServer` delegates most write operations through the use-case layer. However, 5 endpoints currently call core services directly and bypass the use-case layer: `GET /api/users`, `GET /api/users/{id}`, `GET /api/users/{id}/candidates`, `GET /api/users/{id}/matches`, and `GET /api/conversations/{id}/messages`. Prefer routing new endpoints through the use-case layer.

`ProfileUseCases` now exposes: `updateProfile(UpdateProfileCommand)`, `getAchievements(AchievementsQuery)` → `AchievementSnapshot`, `getOrComputeStats(StatsQuery)` → `UserStats`. Use these instead of calling `AchievementService` or `ActivityMetricsService` directly from UI code.

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

## Known Flaky Tests

- `ChatControllerTest#selectionTogglesChatStateAndNoteButtonsRemainWired` — fails intermittently in full suite (JavaFX thread ordering); passes in isolation. Pre-existing, not caused by typical code changes.

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

## Recent Updates (2026-03-27)

### Package removals
- `app/error/` (`AppError`, `AppResult`) — **removed**. Do not reference these types.
- `core/time/` (`DefaultTimePolicy`, `TimePolicy`) — **removed**. Time policy logic is now inline or handled elsewhere.

### Moderation Audit Subsystem (`core/matching/ModerationAuditEvent`, `ModerationAuditLogger`)
- `ModerationAuditEvent` — record capturing `actorId`, `targetId`, `action`, `outcome`, `context`, `timestamp`.
- `ModerationAuditLogger` — emits structured SLF4J audit log events (`audit.moderation` logger) with PII classification metadata and 30-day retention policy.
- Wire report/block/unblock/auto-ban flows through `ModerationAuditLogger`; never log raw user strings to non-audit loggers.

### CleanupScheduler (`app/bootstrap/CleanupScheduler`)
- Background periodic cleanup for `ActivityMetricsService` retention routines.
- Configured with a `Duration` interval; started/stopped via lifecycle methods. Wired during `ApplicationStartup`.

### I18n (`core/i18n/I18n`)
- Shared localization utility for CLI and JavaFX flows.
- `I18n.bundle()` / `I18n.bundle(Locale)` — loads `i18n/messages` `ResourceBundle`.
- Use instead of ad-hoc `ResourceBundle.getBundle(...)` calls.

### BaseViewModel (`ui/viewmodel/BaseViewModel`)
- Abstract base class for all ViewModels; provides `asyncScope`, loading-state `BooleanProperty`, and disposal tracking.
- All new ViewModels must extend `BaseViewModel`.

### New ViewModels
- `NotesViewModel` — profile notes CRUD.
- `SafetyViewModel` — safety/blocking/reporting flows.
- `ProfileReadOnlyViewModel` — read-only profile view for viewing other users' profiles.

### New Screen/UI classes
- `ProfileViewController` — controller for read-only profile view screen.
- `ProfileFormValidator` — client-side field validation for profile edit form; extracted from `ProfileController`.
- `UiDialogs` — centralised standard dialog factory (confirmation, error, input). Use instead of inline `Alert`/`Dialog` construction.
- `UiPreferencesStore` — persists UI user preferences (theme, etc.) locally.
- `LocalPhotoStore` — manages local photo file storage/caching for the UI layer.

### New async primitive
- `PollingTaskHandle` — `TaskHandle` implementation for long-lived polling tasks managed by `ViewModelAsyncScope`. Use for repeated background polling (e.g., conversation refresh).

### RestApiDtos (`app/api/RestApiDtos`)
- Dedicated DTO/request-body record types for the REST API. Keeps `RestApiServer` free of inline anonymous records.

### AccountCleanupStorage (`core/storage/AccountCleanupStorage`, `storage/jdbi/JdbiAccountCleanupStorage`)
- New storage interface + JDBI implementation for account-level cleanup operations (expired-session purge, soft-deleted user removal).
- Wired via `StorageFactory`; consumed by `CleanupScheduler`.

### SanitizerUtils (`core/profile/SanitizerUtils`)
- Input sanitization utilities (strip control chars, normalize whitespace, etc.) for profile text fields.
- Use from `ProfileService` / `ValidationService` instead of inline regex.

---

## Recent Updates (2026-03-13)

### Location Feature (`core/model/LocationModels`, `core/profile/LocationService`)
- **New domain model**: `LocationModels` record types — `Country`, `City`, `ZipRange`, `ResolvedLocation` (with `Precision` enum: CITY/ZIP). Static `formatCoordinates()` utility.
- **New service**: `LocationService` wired via `ServiceRegistry.getLocationService()`. Provides city/ZIP lookup, reverse-lookup, and country listing.
- **Israel-first**: 5 countries listed (IL, US, GB, CA, AU) but only IL is `available=true`. Reverse-lookup only supported for Israeli coordinates. Check `country.available()` before offering to users.
- **No DB persistence**: Location data is in-memory (hardcoded lists). Not stored in schema.

### REST API Expansion (`app/api/RestApiServer`)
- Now **33 endpoints** (was ~15). Route groups: health, users, matching, social, messaging, profile-notes.
- **Localhost-only**: Binds to `InetAddress.getLoopbackAddress()` (127.0.0.1). Not reachable from external hosts.
- **Rate limiting**: `LocalRateLimiter` — 240 requests/minute per IP, applied via `beforeMatched()` guard.
- **New endpoints**: `PUT /api/users/{id}/profile`, `GET /api/users/{id}/stats`, `GET /api/users/{id}/achievements`, notifications CRUD, friend-request flows, conversation archive/delete, message delete.
- 5 read endpoints still bypass the use-case layer (unchanged, see Use-Case Layer section).

### Dev Data Seeder (`storage/DevDataSeeder`)
- Seeds 30 stable-UUID users (10 M/F/Other) + sample matches + conversation.
- **Environment-gated**: only runs when `DATING_APP_SEED_DATA=true` env var is set (checked via `ApplicationStartup.isDevDataSeedingEnabled()`).
- **Idempotent**: sentinel UUID check (`11111111-1111-1111-1111-000000000001`) prevents duplicate inserts. Safe to call on every startup.

### ProfileUseCases Additions
- `updateProfile(UpdateProfileCommand)` — updates profile fields via use-case layer.
- `getAchievements(AchievementsQuery)` → `AchievementSnapshot` — preferred over calling `AchievementService` directly.
- `getOrComputeStats(StatsQuery)` → `UserStats` — preferred over calling `ActivityMetricsService` directly.

### Chat UI Phase 2 Fixes (commits `a99771b`, `31396c9`)
- **CRITICAL**: `@BindBean` on Java records fails in JDBI — replaced with `@BindMethods` for `ProfileNote` binding. Every record-typed JDBI parameter needs `@BindMethods`.
- **Locale fix**: `DateTimeFormatter` now uses `Locale.ENGLISH` explicitly — system Hebrew locale was rendering Hebrew month names.
- **Note status**: Raw exception text no longer propagated to UI; sanitized to short user-facing strings.
- **Chat stability**: `listConversations` failures no longer clear `selectedConversation` (was causing "send message exits chat" regression).
- **`markAsRead` is best-effort**: failures are logged, do not fail message loading.

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
32|2026-03-27 00:00:00|agent:claude_code|scope:code-verified-sync|Updated snapshot to 296 files (144 main+152 test); removed app/error and core/time packages; added RestApiDtos, CleanupScheduler, I18n, ModerationAuditEvent/Logger, SanitizerUtils, AccountCleanupStorage, JdbiAccountCleanupStorage, LocalPhotoStore, UiDialogs, UiPreferencesStore, PollingTaskHandle, ProfileFormValidator, ProfileViewController, BaseViewModel, NotesViewModel, SafetyViewModel, ProfileReadOnlyViewModel to arch; added Recent Updates 2026-03-27 section|CLAUDE.md
---AGENT-LOG-END---
