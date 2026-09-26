---
paths:
  - "src/main/java/datingapp/core/**"
  - "src/main/java/datingapp/app/**"
  - "src/main/java/datingapp/storage/**"
  - "src/main/java/datingapp/location/**"
  - "src/main/java/datingapp/Main.java"
---

## Architecture and wiring

```text
datingapp/
  Main.java
  app/
    api/              REST server, DTOs, request guards, identity policy
    bootstrap/        application startup and cleanup
    cli/              CLI handlers and presenters
    event/handlers/   achievements, metrics, notifications
    support/          presentation helpers
    usecase/          auth, common, dashboard, matching, messaging, profile, social
  core/
    {AppClock, AppConfig, AppConfigValidator, AppSession, EnumSetUtil,
     LoggingSupport, RuntimeEnvironment, ServiceRegistry, TextUtil}
    connection/ i18n/ matching/ metrics/ model/ storage/ workflow/
    profile/          ProfileService, ValidationService, MatchPreferences, helpers
  location/           LocationService, GeocodingService, FallbackGeocodingService,
                      LocalGeocodingService, NominatimGeocodingService,
                      LocationModels, GeoUtils
  storage/
    {DatabaseDialect, DatabaseManager, DevDataSeeder, StorageFactory}
    jdbi/ schema/
  ui/                 see .claude/rules/ui-and-viewmodels.md
```

**Composition roots are `ServiceRegistry` (app-wide) and `ViewModelFactory`
(JavaFX).** Nothing else should be constructing the service graph.

### Three entry points

- `datingapp.Main` — CLI
- `datingapp.ui.DatingApp` — JavaFX desktop
- `datingapp.app.api.RestApiServer` — Javalin REST, backing the Flutter frontend

All share `ServiceRegistry services = ApplicationStartup.initialize();` and
`AppSession.getInstance()`.

### Seams that carry the design

- **`datingapp.location` is top-level, not under `core/`.** `LocationService`
  owns the offline country/city/ZIP dataset, reverse lookup and label formatting;
  `GeocodingService` serves the profile location UI; `ViewModelFactory` wires
  `FallbackGeocodingService(LocalGeocodingService, NominatimGeocodingService)`.
  `ProfileViewModel` stores the resolved label and coordinates and owns no search
  logic; `LocationSelectionDialog` owns the search-and-select UX. Keep those five
  aligned — ad-hoc geocoding in a controller breaks the fallback chain.
  `LocationModels.Precision` covers `ADDRESS`, `CITY` and `ZIP`.
- `AuthUseCases` + `AuthTokenService` (`app/usecase/auth/`) own login,
  registration and the HS256 JWT lifecycle, via
  `ServiceRegistry.getAuthUseCases()`.
- `StorageFactory.buildSqlDatabase(...)` is the runtime path; `buildH2(...)` and
  `buildInMemory(...)` are compatibility/test paths.
- `DevDataSeeder` is env-gated on `DATING_APP_SEED_DATA=true` and idempotent.
- `AppEventBus` / `InProcessAppEventBus` dispatch in-process domain events;
  handlers in `app/event/handlers/` own achievements, metrics and notification
  fan-out.

### Use-case bundles on `ServiceRegistry`

`getAuthUseCases`, `getDashboardUseCases`, `getMatchingUseCases`,
`getMessagingUseCases`, `getProfileMutationUseCases`, `getProfileNotesUseCases`,
`getProfileInsightsUseCases`, `getVerificationUseCases`, `getSocialUseCases`.

`getProfileUseCases()` is a **compatibility facade** — prefer the dedicated
profile slices in new code.

### Pattern files worth reading before adding to a layer

- `app/bootstrap/ApplicationStartup.java` — bootstrap and config loading
- `core/ServiceRegistry.java` — central service and use-case wiring
- `location/LocationService.java`, `GeocodingService.java`,
  `LocalGeocodingService.java`, `NominatimGeocodingService.java`
- `storage/StorageFactory.java`, `storage/jdbi/SqlDialectSupport.java` — runtime
  storage assembly and dialect seams
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — shared REST test
  graph builder

### Domain rules that bite

- **Never `Instant.now()`** in services or domain — use `AppClock.now()` /
  `AppClock.today()`, or inject `AppClock.clock()`. Tests pin the clock.
- **Pair IDs are deterministic** — `generateId(a, b)`, never `a + "_" + b`.
- **`AppConfig` is injected** via `ServiceRegistry`; `AppConfig.defaults()` in
  service code is a bug.
- **JDBI records bind with `@BindMethods`, not `@BindBean`.** Records have no
  bean accessors, so `@BindBean` fails at runtime rather than compile time.
- Enum nesting: `User.Gender` and `Match.MatchState` are nested; `ProfileNote` is
  top-level in `core.model`.
- No new direct framework, database or web dependency from `core/`.
