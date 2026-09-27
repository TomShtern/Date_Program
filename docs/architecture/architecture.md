# Dating App Architecture

> **Status:** package layout verified against `src/main/java` (2026-09-27).
> Counts and LOC are intentionally omitted — they rot on every commit.
> Run `tokei src` when you need a number.

This document describes current architecture from source (`src/main/java`, `src/test/java`, `pom.xml`).

---

## 1. Layer Model

```text
┌──────────────────────────────────────────────────────────────┐
│ PRESENTATION                                                 │
│   CLI (app/cli), JavaFX UI (ui), REST API (app/api)         │
├──────────────────────────────────────────────────────────────┤
│ APPLICATION ORCHESTRATION                                   │
│   app/usecase/* (auth, common, dashboard, matching,          │
│                  messaging, profile, social)                 │
├──────────────────────────────────────────────────────────────┤
│ DOMAIN                                                       │
│   core/* (models, services, storage interfaces, utilities)  │
│   location/* (top-level: LocationService + geocoding)        │
├──────────────────────────────────────────────────────────────┤
│ INFRASTRUCTURE                                               │
│   storage/* (JDBI implementations, schema, DB manager)      │
└──────────────────────────────────────────────────────────────┘
```

Key constraints:

- `core/` contains domain logic and storage interfaces.
- `location/` is top-level, not under `core/` (owns offline
  country/city/ZIP dataset, `LocationService`, `GeocodingService`,
  `Fallback/Local/Nominatim` implementations, `LocationModels`, `GeoUtils`).
- Infrastructure adapters live in `storage/`.
- UI and CLI adapters consume `ServiceRegistry` and use-case bundles.

---

## 2. Package Layout (verified 2026-09-27)

Package-level snapshot. Do not expand into exhaustive class lists here —
they rot on every commit. Run `list_dir` on a package when you need
the current files.

```text
datingapp/
  Main.java                       # CLI entry point
  app/
    api/                          # RestApiServer + DTOs, guards, identity policy
    bootstrap/                    # ApplicationStartup, CleanupScheduler
    cli/                          # CLI handlers + MatchingCliPresenter
    event/ + event/handlers/      # AppEventBus + achievement/metrics/notification
    support/                      # UserPresentationSupport
    usecase/auth|common|dashboard|matching|messaging|profile|social/
  core/
    {AppClock, AppConfig, AppConfigValidator, AppSession, EnumSetUtil,
     LoggingSupport, RuntimeEnvironment, ServiceRegistry, TextUtil}
    connection/ i18n/ matching/ metrics/ model/ profile/ storage/ workflow/
  location/                       # LocationService, GeocodingService + impls
  storage/
    {DatabaseDialect, DatabaseManager, DevDataSeeder, StorageFactory}
    jdbi/ schema/
  ui/
    {DatingApp, ImageCache, LocalPhotoStore, NavigationService, ...}
    async/ screen/ viewmodel/
```

Notes grounded in `src/main/java`:

- `app/api/` has no `RestRouteSupport` or `UserDtoMapper`.
- `core/model/` is `{Match, ProfileNote, TextNormalization, User}` —
  `LocationModels` lives in `location/`, not `core/model/`.
- `core/matching/` has no `Default*` classes and no
  `InterestMatcher`/`LifestyleMatcher`; it has `PreferencesMatcher`.
- `core/metrics/` has no `DefaultAchievementService`.
- `core/storage/` includes `AuthStorage` plus `Operational*` variants.
- `location/` is the single location engine; only `IL` is
  `available=true` (`LocationService.resolveSelection` rejects the rest).

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
