# Dating App

Java 25 (preview) backend with shared domain logic and three adapters: a CLI, a
JavaFX desktop UI, and a Javalin REST API that backs a separate Flutter client
(not in this repository).

- CLI — `src/main/java/datingapp/Main.java` + `src/main/java/datingapp/app/cli/*`
- JavaFX desktop UI — `src/main/java/datingapp/ui/*`
  (`DatingApp.java` entry point, `ViewModelFactory` composition root)
- REST API — `src/main/java/datingapp/app/api/RestApiServer.java` (default
  `http://localhost:7070`, health at `GET /api/health`)

The REST backend is the primary integration surface; CLI and JavaFX are
supporting adapters. All three share `ApplicationStartup.initialize()` (in
`src/main/java/datingapp/app/bootstrap/`) to build the app-wide
`ServiceRegistry` (`src/main/java/datingapp/core/ServiceRegistry.java`);
runtime storage is assembled in
`src/main/java/datingapp/storage/StorageFactory.java`.

> Source of truth is `src/main/java`, `src/test/java`, and `pom.xml`.
> If any document disagrees with code, the code wins.

## Tech stack

- Java 25 (preview enabled) + Maven
- JavaFX 25.0.2 desktop UI (AtlantaFX 2.1.0 theme, Ikonli 12.4.0 icons)
- Javalin 6.7.0 REST API + Jackson 2.21.0
- PostgreSQL 42.7.8 + JDBI 3.51.0 + HikariCP 6.3.0 for runtime;
  H2 and in-memory paths exist for compatibility/tests
  (`StorageFactory.buildSqlDatabase` is the runtime path)
- SLF4J 2.0.17 + Logback 1.5.28
- Quality gate: Spotless (Palantir Java Format), Checkstyle, PMD, SpotBugs,
  JaCoCo line coverage minimum `0.60`

## Run locally

```powershell
# PostgreSQL preflight, then start local PostgreSQL
.\scripts/check_postgresql_runtime_env.ps1
.\scripts/start_local_postgres.ps1

# CLI (exec:exec — exec:java cannot pass --enable-preview)
mvn compile && mvn exec:exec

# JavaFX desktop UI
mvn javafx:run

# Tests
mvn test

# Full local verification (Maven quality gate + PostgreSQL smoke)
.\scripts/run_verify.ps1

# Maven quality gate only
mvn spotless:apply verify
```

Copy `.env.example` to `.env` for local settings (`.env` is gitignored).
Config loads from `config/app-config.json` with `DATING_APP_*` environment
overrides (`ApplicationStartup`). Local PostgreSQL runs on port 55432
(`start_local_postgres.ps1` default). For phone-alpha LAN testing against the
Flutter client,
use `.\scripts/start_phone_alpha_backend.ps1` — it binds `0.0.0.0:7070`,
health-checks `/api/health` on loopback and LAN, and prints the Flutter
`dart-define` values. A non-loopback bind requires a LAN shared secret
(`DATING_APP_REST_SHARED_SECRET`); never commit a real secret.

## Project structure

```text
src/main/java/datingapp/
  Main.java                 # CLI entry point
  app/
    api/                    # RestApiServer + DTOs, guards, identity policy
    bootstrap/              # ApplicationStartup (config + ServiceRegistry wiring)
    cli/                    # CLI handlers and presenters
    event/                  # AppEventBus + handlers (achievements, metrics, notifications)
    support/                # presentation helpers
    usecase/                # auth, common, dashboard, matching, messaging, profile, social
  core/                     # framework-free domain: AppClock, AppConfig,
                            # ServiceRegistry + connection, i18n, matching,
                            # metrics, model, profile, storage, workflow
  location/                 # LocationService, GeocodingService + local/Nominatim
  storage/                  # StorageFactory, DatabaseManager, DevDataSeeder + jdbi/, schema/
  ui/
    DatingApp.java          # JavaFX entry point
    async/                  # ViewModelAsyncScope
    screen/                 # controllers + dialogs
    viewmodel/              # ViewModels + ViewModelFactory
```

`core/` stays framework-free; `app/usecase/*` is the application boundary;
`StorageFactory.buildSqlDatabase(...)` is the runtime storage path.
Contributors: see `AGENTS.md` (workflow) and `CLAUDE.md` (repo map + gotchas).

## API

`GET /api/health` plus auth, users, photos, location, matching, social,
messaging, and notes routes — see `RestApiServer` route registration
(`app.get/post/put/delete` under `/api/`). The phone-alpha auth/photo
contract in `docs/api/API-SPECIFICATION.md` was verified 2026-09-27
against `RestApiServer`/`AuthUseCases`/`AppConfig`; it is still scoped
to auth/photos only, so treat the server source as authoritative for
everything else.

## Repository guide

- [Documentation index](docs/README.md)
- [CI and PostgreSQL guide](docs/guides/ci-and-postgresql.md)
- [PostgreSQL PowerShell guide](docs/guides/postgresql-powershell.md)
- [LAN backend startup guide](docs/guides/lan-backend-startup.md)
