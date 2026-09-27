# CLAUDE.md

Java 25 (preview) + JavaFX 25.0.2 + Maven desktop dating app, with a Javalin REST
server that backs a separate Flutter frontend. Windows 11, PowerShell 7.

**Source of truth is the code** — `src/main/java`, `src/test/java`, `pom.xml`.
When a document and the source disagree, the source wins and the document gets
fixed in the same change.

Detail is path-scoped in `.claude/rules/` and loads itself when you touch a
matching file — architecture and wiring, UI/ViewModel threading, REST transport.
Don't read those pre-emptively "for context"; that spends the context they were
moved out of this file to save.

`AGENTS.md` holds the execution and verification workflow for agents.
Read it when you need that workflow, not by default.

Composition (verified): `ApplicationStartup.initialize()` →
`StorageFactory.buildSqlDatabase(...)` → `ServiceRegistry`; JavaFX via
`DatingApp` → `ViewModelFactory`. REST via `RestApiServer` (default
`http://localhost:7070`, `GET /api/health`).

**No counts live in this file.** File totals, LOC and test tallies were removed
deliberately — they were accurate when written, and would rot as soon as the tree
moves. Run `tokei src` or `mvn test` when you need a number.

## Critical Gotchas

| Gotcha | Wrong | Correct |
|---|---|---|
| User enum imports | `datingapp.core.model.Gender` | `datingapp.core.model.User.Gender` (`UserState`, `VerificationMethod` share the same owner) |
| Match enum imports | `datingapp.core.model.MatchState` | `datingapp.core.model.Match.MatchState` (`MatchArchiveReason` shares the same owner) |
| `ProfileNote` ownership | `User.ProfileNote` | top-level record `datingapp.core.model.ProfileNote` |
| `Conversation` ownership | `core.model.Conversation` | `core.connection.ConnectionModels.Conversation` (`Message` is a record in the same owner) |
| Domain clock | `Instant.now()` in services/domain | `AppClock.now()` / `AppClock.today()`, or inject `AppClock.clock()` |
| Pair IDs | `a + "_" + b` | deterministic sorted-UUID `Match.generateId(a, b)` (`core.model`) / `Conversation.generateId(a, b)` (`core.connection.ConnectionModels`) |
| Runtime config | `AppConfig.defaults()` in runtime service code | injected `AppConfig` via `ServiceRegistry`; `defaults()` belongs at bootstrap/composition/test boundaries |
| Runtime storage wiring | assume `StorageFactory.buildH2(...)` is production | `StorageFactory.buildSqlDatabase(...)`; `buildH2` / `buildInMemory` are compat/test paths |
| JDBI binding | `@BindBean` on records | `@BindMethods` on records (`UserStats`/`PlatformStats`); classes like `Match`/`Conversation` keep `@BindBean` |
| ViewModel threading | ad-hoc `Thread.ofVirtual()` / `Platform.runLater()` in normal UI flows | `BaseViewModel` + `ViewModelAsyncScope` |
| UI image loading | synchronous `ImageCache.getImage(...)` on a visible path | `ImageCache.getImageAsync(...)` plus stale-request guards |
| Location availability | treat all listed countries as selectable | only `IL` is currently selectable/fully supported (`LocationService` rejects the rest via `resolveSelection`) |
| REST binding | assume loopback-only, or bind wide without a secret | loopback is only the *default*; a non-loopback bind **throws** unless a LAN shared secret is supplied — see `.claude/rules/rest-api.md` |

## Commands

```powershell
mvn compile && mvn exec:exec     # CLI  (exec:exec, never exec:java — see below)
mvn javafx:run                   # desktop UI
mvn test
mvn -Ptest-output-verbose test

.\scripts/run_verify.ps1                 # full gate: Maven quality gate + PostgreSQL smoke
mvn spotless:apply verify        # Maven-only quality gate

.\scripts/start_local_postgres.ps1
.\scripts/run_postgresql_smoke.ps1
.\scripts/stop_local_postgres.ps1
```

- **`exec:exec`, not `exec:java`** — `exec:java` cannot pass `--enable-preview`,
  and this project needs it.
- **`mvn --% ...` on PowerShell** whenever Maven arguments contain commas, so a
  comma-separated `-Dtest=` list is passed through intact.

## Build constraints (`pom.xml`)

Java release `25` with preview enabled; Surefire JVMs carry preview/native-access
flags. `verify` runs Spotless (Palantir Java Format), PMD, SpotBugs (report) and
a JaCoCo line coverage gate with minimum `0.60`. Checkstyle runs in `validate`.
JaCoCo excludes `datingapp/ui/**`, `datingapp/app/cli/**`, `datingapp/Main.class`.

## Keep out of new work

`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, and any `ui/controller`
reference. No new direct framework, database or web dependency from `core/`.
