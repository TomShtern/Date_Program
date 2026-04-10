> 🚀 **VERIFIED & UPDATED: 2026-04-10**
> This document is optimized for Qwen Code to enhance generation quality in this project.
> Source code is the ONLY source of truth. Documentation is stale.

# QWEN.md — Dating App Project Guide

## 1. Hierarchy and source of truth

**Priority order (highest to lowest):**

1. `src/main/java` — production code (ultimate truth)
2. `src/test/java` — test code (behavioral truth)
3. `pom.xml` — build configuration (build truth)
4. `.github/copilot-instructions.md` — high-level AI instructions
5. `CLAUDE.md` — repo map (may be outdated)
6. `AGENTS.md` — workflow discipline
7. This file (QWEN.md) — Qwen-optimized guide

**When in doubt, read the code. Don't trust docs.**

## 2. Environment

- **OS:** Windows 11
- **Shell:** PowerShell 7.6+ (use PowerShell syntax for commands)
- **IDE:** VS Code Insiders (primary), IntelliJ (sometimes)
- **Java:** 25 with `--enable-preview`
- **JavaFX:** 25.0.2
- **Build:** Maven
- **Formatter:** Palantir Java Format 2.85.0 via Spotless 3.2.1
- **Language Support:** Java by Red Hat extension

**PowerShell note:** When Maven uses comma-separated `-Dtest=...,...` lists, use `mvn --% ...` to avoid parsing issues.

## 3. Codebase snapshot (2026-04-10)

| Metric | Count |
|--------|-------|
| **Total Java files** | **382** |
| Main source files | 180 |
| Test files | 202 |

## 4. Architecture overview

```
datingapp/
  Main.java
  app/
    api/              — REST API server (Javalin), DTOs, route handlers
      RestApiServer, RestApiDtos, RestApiUserDtos, RestApiRequestGuards,
      RestApiIdentityPolicy, RestRouteSupport, UserDtoMapper
    bootstrap/        — Application startup and cleanup
      ApplicationStartup, CleanupScheduler
    cli/              — CLI interface (alternative to JavaFX UI)
      CliTextAndInput, MainMenuRegistry, MatchingHandler, MessagingHandler,
      ProfileHandler, SafetyHandler, StatsHandler, MatchingCliPresenter
    event/            — Event bus and handlers
      AppEvent, AppEventBus, InProcessAppEventBus
      handlers/: AchievementEventHandler, MetricsEventHandler, NotificationEventHandler
    support/          — User presentation support
      UserPresentationSupport
    usecase/          — Application use cases (business flows)
      common/: UseCaseError, UseCaseResult, UserContext
      dashboard/: DashboardUseCases
      matching/: MatchingUseCases
      messaging/: MessagingUseCases
      profile/: ProfileUseCases, ProfileMutationUseCases, ProfileNotesUseCases,
                ProfileInsightsUseCases, VerificationUseCases
      social/: SocialUseCases
  core/               — Framework-agnostic domain logic
    connection/       — Connection models and service
      ConnectionModels, ConnectionService
    i18n/             — Internationalization
      I18n
    matching/         — Matching algorithms and services
      MatchingService, CandidateFinder, CompatibilityCalculator,
      DailyLimitService, DailyPickService, StandoutService, TrustSafetyService,
      UndoService, InterestMatcher, LifestyleMatcher, MatchQualityService,
      RecommendationService, BrowseRankingService, Default* implementations
      ModerationAuditEvent, ModerationAuditLogger, Standout
    metrics/          — Achievement and activity metrics
      AchievementService, ActivityMetricsService, DefaultAchievementService,
      EngagementDomain, SwipeState
    model/            — Domain entities
      User, Match, ProfileNote, LocationModels
    profile/          — Profile management
      ProfileService, LocationService, MatchPreferences, ValidationService,
      ProfileCompletionSupport, SanitizerUtils
    storage/          — Storage interfaces (framework-agnostic)
      UserStorage, InteractionStorage, CommunicationStorage, AnalyticsStorage,
      TrustSafetyStorage, AccountCleanupStorage, Operational* variants, PageData
    workflow/         — Workflow policies
      ProfileActivationPolicy, RelationshipWorkflowPolicy, WorkflowDecision
    [root]:           — Core infrastructure
      ServiceRegistry, AppConfig, AppConfigValidator, AppClock, AppSession,
      RuntimeEnvironment, TextUtil, EnumSetUtil, LoggingSupport
  storage/            — Storage implementations (JDBI + SQL)
    jdbi/             — JDBI-based storage implementations
      JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage,
      JdbiMetricsStorage, JdbiTrustSafetyStorage, JdbiAccountCleanupStorage,
      JdbiTypeCodecs, SqlDialectSupport
      NormalizedProfileRepository, NormalizedProfileHydrator,
      NormalizedEnumParser, DealbreakerAssembler
    schema/           — Database schema management
      SchemaInitializer, MigrationRunner
    [root]:           — Storage factory and configuration
      DatabaseDialect, DatabaseManager, StorageFactory, DevDataSeeder
  ui/                 — JavaFX user interface
    async/            — Async task management for UI
      JavaFxUiThreadDispatcher, TaskHandle, PollingTaskHandle, TaskPolicy,
      UiThreadDispatcher, ViewModelAsyncScope, AsyncErrorRouter
    screen/           — JavaFX controllers
      BaseController, DashboardController, LoginController, MatchingController,
      MatchesController, ChatController, ProfileController, PreferencesController,
      SafetyController, SocialController, StatsController, StandoutsController,
      NotesController, ProfileViewController, ProfileFormValidator,
      LocationSelectionDialog, CreateAccountDialogFactory, MilestonePopupController
    viewmodel/        — ViewModels and UI adapters
      BaseViewModel, ViewModelFactory, LoginViewModel, DashboardViewModel,
      MatchingViewModel, MatchesViewModel, ChatViewModel, ProfileViewModel,
      ProfileReadOnlyViewModel, PreferencesViewModel, SafetyViewModel,
      SocialViewModel, StatsViewModel, NotesViewModel, StandoutsViewModel
      ConversationLoader, MatchListLoader, PhotoCarouselState,
      PhotoMutationCoordinator, ProfileDraftAssembler, ProfileOnboardingState,
      RelationshipActionRunner, UiAdapterCache, UiDataAdapters, ViewModelErrorSink
    [root]:           — UI infrastructure
      DatingApp, ImageCache, LocalPhotoStore, NavigationService,
      OnboardingContext, UiAnimations, UiComponents, UiConstants, UiDialogs,
      UiFeedbackService, UiPreferencesStore, UiThemeService, UiUtils
```

## 5. Composition roots and boundaries

### Bootstrap

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();
```

### Production composition roots

- `ApplicationStartup.initialize()` — main bootstrap
- `ServiceRegistry` — service container
- `ViewModelFactory` — ViewModel composition
- `StorageFactory.buildSqlDatabase(...)` — PostgreSQL production storage
- `StorageFactory.buildH2(...)` — H2 storage for tests/compatibility

### Architectural boundaries

- **`core/`** — Framework-agnostic domain logic. NO JavaFX, NO JDBI, NO REST API.
- **`app/usecase/`** — Application boundary for business flows. Orchestrates core services.
- **`storage/`** — Storage implementations. JDBI + SQL. Depends on core storage interfaces.
- **`ui/`** — JavaFX UI layer. ViewModels, controllers, async task management.
- **`app/api/`** — REST API layer (Javalin). Loopback-only, local access.

**Rule:** ViewModels must go through `BaseViewModel`, `ui/async/*`, and `UiDataAdapters`. They should NOT directly orchestrate storage or services when a use-case seam exists.

## 6. Critical correctness rules

### Enums and types

- **Use nested enums from owner types:**
  - `User.Gender`, `User.UserState`, `User.VerificationMethod`
  - `Match.MatchState`, `Match.MatchArchiveReason`
- **`ProfileNote`** is standalone: `datingapp.core.model.ProfileNote`

### Time and dates

- **Use `AppClock` as the time source:** `AppClock.now()` / `AppClock.today()`
- Inject `AppClock.clock()` when a service needs a `Clock`
- Use `Locale.ENGLISH` for user-facing date formatters

### IDs and identifiers

- **Deterministic pair IDs:** `generateId(UUID a, UUID b)` — sorted concatenation

### Configuration

- **Use injected `AppConfig` at runtime**
- Reserve `AppConfig.defaults()` for bootstrap/tests
- Runtime PostgreSQL boot: `StorageFactory.buildSqlDatabase(...)`
- H2-backed storage: `StorageFactory.buildH2(...)` or `buildInMemory(...)` for tests

### JDBI patterns

- **Record-typed parameters:** Use `@BindMethods`, NOT `@BindBean`

### API server

- **`RestApiServer` is loopback-only** (`127.0.0.1`)
- Do NOT assume external host access
- The `/candidates` route is an explicit direct-read exception (returns candidates directly)

### Location

- **`LocationService`** is the single location engine
- Only **`IL` (Israel)** is fully supported/selectable today
- Built-in city data, no external geocoding services

### UI patterns

- **`UiDataAdapters.UiPage<T>`** is the UI paging boundary (record type)
- Do NOT leak `PageData` into ViewModels
- All production ViewModels extend `BaseViewModel`

## 7. Key patterns and conventions

### Storage

- `core/storage/` defines interfaces (framework-agnostic)
- `storage/jdbi/` implements them with JDBI + SQL
- `StorageFactory` wires everything together

### Events

- `AppEventBus` for domain events (achievements, metrics, notifications)
- `TestEventBus` for testing

### Testing

- Architecture tests in `src/test/java/datingapp/architecture/`
- Use `JavaFxTestSupport` and `UiAsyncTestSupport` for UI tests
- Use `RestApiTestFixture` for REST API tests
- Test utilities: `TestUserFactory`, `TestEventBus`, `TestAchievementService`, `TestClock`, `TestDatabaseStorageFactory`

### DevDataSeeder

- Creates 30 pre-defined users (10 MALE / 10 FEMALE / 10 OTHER)
- **Idempotent** — always creates/updates seed users
- NOT environment-gated (runs when configured)
- Stable UUIDs for deterministic testing

## 8. Build, test, and quality commands

### Development

```powershell
# Compile and run CLI
mvn compile && mvn exec:exec

# Run JavaFX UI
mvn javafx:run

# Run all tests
mvn test

# Run tests with verbose output
mvn -Ptest-output-verbose test

# Apply formatting and run all quality checks
mvn spotless:apply verify
```

### PostgreSQL management

```powershell
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\stop_local_postgres.ps1
.\reset_local_postgres.ps1
.\check_postgresql_runtime_env.ps1
.\export_local_postgresql_schema.ps1
```

### Test runners

```powershell
.\run_test.ps1
.\run_event_tests.ps1
.\run_verify.ps1
```

### Quality gates (pom.xml)

| Gate | Phase | Tool | Notes |
|------|-------|------|-------|
| **Java** | compile | Java 25 + `--enable-preview` | Preview features enabled |
| **Checkstyle** | validate | checkstyle.xml | Fails on error/violation |
| **Spotless** | verify | Palantir Java Format 2.85.0 | 4 spaces, removes unused imports |
| **PMD** | verify | pmd-rules.xml | Fails on violation, min tokens 100 |
| **SpotBugs** | verify | Medium threshold | Report-only; strict via `spotbugs-strict` profile |
| **JaCoCo** | verify | 60% line coverage minimum | Excludes `ui/**`, `app/cli/**`, `Main.class` |

### Dependencies (key)

| Dependency | Version |
|------------|---------|
| H2 Database | 2.4.240 |
| PostgreSQL | 42.7.8 |
| JDBI 3 | 3.51.0 |
| JUnit 5 | 5.14.2 |
| Jackson | 2.21.0 |
| JavaFX | 25.0.2 |
| Javalin | 6.7.0 |
| HikariCP | 6.3.0 |
| AtlantaFX | 2.1.0 |
| Ikonli | 12.4.0 |

## 9. Workflow defaults for Qwen

### Code exploration

- **Use `ast-grep`** when structure matters (finding classes, methods, patterns)
- **Use plain-text search** (`grep_search`, `glob`) only when structure is irrelevant
- **Use read-only subagents** for isolated codebase investigation
- **Read files fully** before editing them

### Making changes

- **Keep diffs small** — avoid unrelated refactors
- **Verify in this order:**
  1. Touched-file errors (IDE/compiler)
  2. Targeted tests (specific test classes)
  3. Broader smoke tests (if needed)
  4. Full `mvn spotless:apply verify` before completion claims

### Multi-step work

- **Keep a todo list** for tracking progress
- **Use parallel agents** when tasks are independent
- **Summarize results** back into the main workflow

## 10. Shared test support

### Test infrastructure files

- `src/test/java/datingapp/ui/JavaFxTestSupport.java` — JavaFX UI test support
- `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` — Async UI test support
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — REST API test fixture builder
- `src/test/java/datingapp/core/testutil/TestUserFactory.java` — Test user creation
- `src/test/java/datingapp/app/testutil/TestEventBus.java` — Test event bus
- `src/test/java/datingapp/core/testutil/TestAchievementService.java` — Test achievement service
- `src/test/java/datingapp/core/testutil/TestClock.java` — Fixed time clock for tests
- `src/test/java/datingapp/core/testutil/TestDatabaseStorageFactory.java` — Test storage factory

### Architecture tests

- `src/test/java/datingapp/architecture/ArchitectureTestSupport.java`
- `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java`
- `src/test/java/datingapp/architecture/EventCoverageArchitectureTest.java`
- `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`

## 11. Key pattern files (verified 2026-04-10)

### Bootstrap and composition

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`

### Storage and database

- `src/main/java/datingapp/storage/DatabaseDialect.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

### API and use cases

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/core/profile/LocationService.java`

### UI and ViewModels

- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`

### Test support

- `src/test/java/datingapp/ui/JavaFxTestSupport.java`
- `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`

## 12. PowerShell scripts (9 total)

| Script | Purpose |
|--------|---------|
| `check_postgresql_runtime_env.ps1` | Check PostgreSQL runtime environment |
| `export_local_postgresql_schema.ps1` | Export local PostgreSQL schema |
| `reset_local_postgres.ps1` | Reset local PostgreSQL database |
| `run_event_tests.ps1` | Run event-related tests |
| `run_postgresql_smoke.ps1` | Run PostgreSQL smoke tests |
| `run_test.ps1` | General test runner |
| `run_verify.ps1` | Run Maven verify phase |
| `start_local_postgres.ps1` | Start local PostgreSQL |
| `stop_local_postgres.ps1` | Stop local PostgreSQL |
