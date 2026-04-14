> 🚀 **VERIFIED & UPDATED: 2026-04-14**
> Refreshed against the current source tree and a passing `mvn spotless:apply verify` run on 2026-04-14.

# CLAUDE.md

Second-level guidance for AI coding agents working in this repository.

> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`
>
> **Source of truth:** code only (`src/main/java`, `src/test/java`, `pom.xml`).

## Environment

- Windows 11
- PowerShell 7.6
- VS Code Insiders (IntelliJ optional)
- Java 25 with preview enabled
- JavaFX 25.0.2
- Maven

## Verified Source Snapshot (2026-04-14)

- Java files: **396 total** (`186` main / `210` test)
- Java LOC (`tokei src`, Java only): **110,483 total / 89,850 code / 15,576 blank / 5,057 comments**

## Verified Build Snapshot (2026-04-14)

- `mvn spotless:apply verify` passed.
- Surefire summary during verify: **1852 tests, 0 failures, 0 errors, 2 skipped**.

## Architecture Snapshot (package-level, code-verified)

```text
datingapp/
  Main.java
  app/
    api/              REST server, DTOs, request guards, identity policy
    bootstrap/        application startup and cleanup
    cli/              CLI handlers and presenters
    event/handlers/   achievements, metrics, notifications
    geocoding/        NominatimGeocodingService
    support/          presentation helpers
    usecase/
      common/
      dashboard/
      matching/
      messaging/
      profile/
      social/
  core/
    {AppClock, AppConfig, AppConfigValidator, AppSession, EnumSetUtil,
     LoggingSupport, RuntimeEnvironment, ServiceRegistry, TextUtil}
    connection/
    i18n/
    matching/
    metrics/
    model/
    profile/          LocationService + GeocodingService + local/fallback adapters
    storage/
    workflow/
  storage/
    {DatabaseDialect, DatabaseManager, DevDataSeeder, StorageFactory}
    jdbi/
    schema/
  ui/
    async/
    screen/           controllers + LocationSelectionDialog + CreateAccountDialogFactory
    viewmodel/        BaseViewModel, loaders, coordinators, adapters, factory
    {DatingApp, ImageCache, LocalPhotoStore, NavigationService,
     OnboardingContext, UiAnimations, UiComponents, UiConstants,
     UiDialogs, UiFeedbackService, UiPreferencesStore, UiThemeService, UiUtils}
```

## Current High-Signal Seams

- Production composition roots are `ServiceRegistry` and `ViewModelFactory`.
- The location selection stack is now split cleanly:
  - `LocationService` owns the offline country/city/ZIP dataset, reverse lookup, and label formatting.
  - `GeocodingService` provides search results for the profile location UI.
  - `ViewModelFactory` wires `FallbackGeocodingService(LocalGeocodingService, NominatimGeocodingService)`.
  - `ProfileViewModel` stores the resolved label and coordinates; it does not own search logic.
  - `LocationSelectionDialog` owns the search-and-select UX.
- `LocationModels.Precision` now includes `ADDRESS` in addition to `CITY` and `ZIP`.
- `BaseViewModel` + `ViewModelAsyncScope` are the standard async UI seam. Matching swipe/undo, safety verification/delete, and chat reset paths should not block the FX thread.
- `ImageCache.getImageAsync(...)` is the controller-facing image path for user-visible loads; controllers guard stale callbacks with request ids.
- `StorageFactory.buildSqlDatabase(...)` is the runtime database path. `buildH2(...)` and `buildInMemory(...)` remain compatibility/test paths.
- `DevDataSeeder` is environment-gated (`DATING_APP_SEED_DATA=true`) and idempotent.

## Critical Gotchas

| Gotcha                  | Wrong                                                                             | Correct                                                                                                                   |
|-------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| User enum imports       | `datingapp.core.model.Gender`                                                     | `datingapp.core.model.User.Gender`                                                                                        |
| Match enum imports      | `datingapp.core.model.MatchState`                                                 | `datingapp.core.model.Match.MatchState`                                                                                   |
| `ProfileNote` ownership | `User.ProfileNote`                                                                | `datingapp.core.model.ProfileNote`                                                                                        |
| Domain clock            | `Instant.now()` in services/domain                                                | Use `AppClock.now()` / `AppClock.today()` or inject `AppClock.clock()`                                                    |
| Pair IDs                | `a + "_" + b`                                                                     | deterministic `generateId(a, b)`                                                                                          |
| Runtime config          | `AppConfig.defaults()` in service code                                            | injected `AppConfig` via `ServiceRegistry`                                                                                |
| Runtime storage wiring  | assume `StorageFactory.buildH2(...)` is the production path                       | use `StorageFactory.buildSqlDatabase(...)` for runtime                                                                    |
| JDBI record binding     | `@BindBean` on records                                                            | `@BindMethods` on records                                                                                                 |
| ViewModel threading     | ad-hoc `Thread.ofVirtual()` / `Platform.runLater()` in normal UI flows            | `BaseViewModel` + `ViewModelAsyncScope` + dispatcher abstractions                                                         |
| UI image loading        | synchronous `ImageCache.getImage(...)` on a visible controller path               | `ImageCache.getImageAsync(...)` plus stale-request guards                                                                 |
| Location UX wiring      | ad-hoc geocoding in controllers or direct lat/lon form ownership in the ViewModel | keep `LocationService`, `GeocodingService`, `LocationSelectionDialog`, `ProfileViewModel`, and `ViewModelFactory` aligned |
| Location availability   | treat all listed countries as selectable                                          | only `IL` is currently selectable/fully supported                                                                         |
| REST binding            | assume external host access                                                       | loopback-only binding in `RestApiServer` unless explicitly changed                                                        |

## Entrypoints and Wiring

### Shared bootstrap

```java
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();
```

### CLI (`Main.java`)

CLI handlers are created from `ServiceRegistry` and `AppSession`; the main entry path stays in `datingapp.Main`.

### JavaFX (`DatingApp.java`)

```java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.setPreferencesStore(vmFactory.getPreferencesStore());
nav.initialize(primaryStage);
```

## Use-Case Layer and Event Bus

`ServiceRegistry` constructs and owns the app-layer bundles:

- `getMatchingUseCases()`
- `getMessagingUseCases()`
- `getProfileUseCases()` — compatibility facade; prefer the dedicated slices below for new code
- `getProfileMutationUseCases()`
- `getProfileNotesUseCases()`
- `getProfileInsightsUseCases()`
- `getVerificationUseCases()`
- `getSocialUseCases()`

`AppEventBus` / `InProcessAppEventBus` provides in-process domain event dispatching. Event handlers in `app/event/handlers/` own achievements, metrics, and notification fan-out.

## Build and Quality Commands

```powershell
mvn compile && mvn exec:exec
mvn javafx:run
mvn test
mvn -Ptest-output-verbose test

# Full local verification (Maven quality gate + PostgreSQL smoke)
.\run_verify.ps1

# Maven-only quality gate
mvn spotless:apply verify

# Optional local PostgreSQL runtime validation
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\stop_local_postgres.ps1
```

When selecting multiple tests from PowerShell, prefer `mvn --% ...` so comma-separated `-Dtest=` values are passed through intact.

## Build Constraints from `pom.xml`

- Java release `25` with preview enabled
- Surefire test JVMs use preview/native-access flags
- Spotless (Palantir Java Format) checked in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage gate in `verify` (minimum `0.60`)

## Key Pattern Files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` — bootstrap and config loading
- `src/main/java/datingapp/core/ServiceRegistry.java` — central service and use-case wiring
- `src/main/java/datingapp/core/profile/LocationService.java` — offline location dataset and reverse lookup
- `src/main/java/datingapp/core/profile/GeocodingService.java` and `LocalGeocodingService.java` — location search abstraction and offline adapter
- `src/main/java/datingapp/app/geocoding/NominatimGeocodingService.java` — online geocoding adapter
- `src/main/java/datingapp/ui/screen/LocationSelectionDialog.java` — profile location selection flow
- `src/main/java/datingapp/ui/ImageCache.java` — shared async image loading and caching
- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java` — shared ViewModel lifecycle shell
- `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java` — async task policy and keyed task delivery
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` — JavaFX composition root
- `src/main/java/datingapp/storage/StorageFactory.java` and `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java` — runtime storage assembly and dialect seams
- `src/test/java/datingapp/ui/JavaFxTestSupport.java` and `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` — shared UI test helpers
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — shared REST/API test graph builder

## Keep These Out of New Work

- `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, and `ui/controller` references
- stale architecture docs when they conflict with source
- new direct framework/database/web dependencies from `core/`

## Instruction-Stack Note

Use `.github/copilot-instructions.md` for the shortest always-on rules. Use this file for the verified repo map and current gotchas. Use `AGENTS.md` for execution workflow and validation discipline.
