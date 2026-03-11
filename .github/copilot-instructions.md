we are on windows 11, usually using powershell, we are working in VS Code-Insiders (sometimes in IntelliJ). we are using java 25, and using javafx 25, maven, palantir format/style and the java by Red Hat extension.
make sure to leverage the tools you have as an ai coding agent together with the IDE tools and also the tools we have here on this system.

You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.

<system_tools>

# 💻 SYSTEM_TOOL_INVENTORY

### 🛠 CORE UTILITIES: Search, Analysis & Refactoring

- **ripgrep** (`rg`) `v14.1.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Primary text search engine.
  - **Capabilities:** Ultra-fast regex search, ignores `.gitignore` by default.
- **fd** (`fd`) `v10.3.0`
  - **Context:** File system traversal.
  - **Capabilities:** User-friendly, fast alternative to `find`.
- **tokei** (`tokei`) `v12.1.2` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Codebase Statistics.
  - **Capabilities:** Rapidly counts lines of code (LOC), comments, and blanks across all languages.
- **ast-grep** (`sg`) `v0.40.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Advanced Refactoring & Linting.
  You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.
  - **Capabilities:** Structural code search and transformation using Abstract Syntax Trees (AST). Supports precise pattern matching and large-scale automated refactoring beyond regex limitations.
- **bat** (`bat`) `v0.26.0`
  - **Context:** File Reading.
  - **Capabilities:** `cat` clone with automatic syntax highlighting and Git integration.
- **sd** (`sd`) `v1.0.0`
  - **Context:** Text Stream Editing.
  - **Capabilities:** Intuitive find & replace tool (simpler `sed` replacement).
- **jq** (`jq`) `v1.8.1`
  - **Context:** JSON Parsing.
  - **Capabilities:** Command-line JSON processor/filter.
- **yq** (`yq`) `v4.48.2`
  - **Context:** Structured Data Parsing.
  - **Capabilities:** Processor for YAML, TOML, and XML.
- **Semgrep** (`semgrep`) `v1.140.0`
  - **Capabilities:** Polyglot Static Application Security Testing (SAST) and logic checker.

### 🌐 SECONDARY RUNTIMES

- **Node.js** (`node`) `v24.11.1` - JavaScript runtime.
- **Bun** (`bun`) `v1.3.1` - All-in-one JS runtime, bundler, and test runner.
- **Java** (`java`) `JDK 25 & javafx 25` - Java Development Kit.

</system_tools>



# Dating App - AI Agent Instructions

**Platform:** Windows 11 | PowerShell 7.5.x | VS Code Insiders | Java 25 (preview enabled) | JavaFX 25.0.2
**Verified snapshot (2026-03-11):** 140 main + 107 test Java files (247 total) | 66,698 LOC / 52,170 code | JaCoCo line coverage gate: 60%

**SOURCE-OF-TRUTH RULE:** If any document conflicts with code, trust `src/main/java`, `src/test/java`, and `pom.xml`.

## ⚠️ Critical Gotchas (Compilation / Runtime / Agent Accuracy)

| Issue                       | Wrong                                           | Correct                                                                 |
|-----------------------------|-------------------------------------------------|-------------------------------------------------------------------------|
| Non-static nested type      | `public record SendResult(...) {}` inside class | `public static record SendResult(...) {}`                               |
| Model enum import           | `import datingapp.core.model.Gender`            | `import datingapp.core.model.User.Gender`                               |
| `ProfileNote` import        | `import datingapp.core.model.User.ProfileNote`  | `import datingapp.core.model.ProfileNote`                               |
| Clock usage                 | `Instant.now()` in domain/service logic         | `AppClock.now()` (testable via `TestClock`)                             |
| Service error flow          | `throw new ...` for business failure            | Return `*Result.failure(...)` record                                    |
| Pair IDs                    | `a + "_" + b`                                   | lexicographically sorted deterministic ID (`generateId(a, b)`)          |
| EnumSet null crash          | `EnumSet.copyOf(possiblyNull)`                  | `EnumSetUtil.safeCopy(...)` or null-safe conditional                    |
| Mutable collection exposure | `return internalSet;`                           | Return defensive copy / unmodifiable view                               |
| Legacy bootstrap references | `AppBootstrap`, `HandlerFactory`                | `ApplicationStartup` + service-based `fromServices(...)` handler wiring |
| Legacy UI references        | `ui/controller`, `Toast`, `UiSupport`           | `ui/screen`, `ui/popup`, `UiFeedbackService`, `UiComponents`            |

**Config access pattern:** use injected `AppConfig` from `ServiceRegistry` for runtime behavior; keep `AppConfig.defaults()` at composition/bootstrap/test boundaries only.

**Config JSON loading:** `ApplicationStartup.applyJsonConfig()` uses Jackson databinding (`readerForUpdating(builder).readValue(json)`) via a `BuilderMixin` — no annotations in `core/`. Adding a new config property only requires a field + setter in `AppConfig.Builder` and an entry in `app-config.json`. No `ApplicationStartup` edits needed.

> Note: `ConnectionService.SendResult.ErrorCode` is currently declared as `public enum` inside a `public static record`; Java treats nested enums as implicitly static.

## Current Architecture (source-of-truth)

```text
datingapp/
  Main.java
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
    event/{AppEvent,AppEventBus,InProcessAppEventBus}.java
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
    time/{DefaultTimePolicy,TimePolicy}
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy,WorkflowDecision}
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

## Critical correctness rules

1. Use nested model enums from owners:
   - `User.Gender`, `User.UserState`, `User.VerificationMethod`
   - `Match.MatchState`, `Match.MatchArchiveReason`
2. `ProfileNote` is standalone: `datingapp.core.model.ProfileNote`.
3. Use `AppClock.now()` in domain/service logic (not `Instant.now()`).
4. Use deterministic pair IDs (`generateId(UUID a, UUID b)`) for two-user aggregates.
5. Service business failures should return result records when provided (do not throw flow exceptions).
6. ViewModels should use shared async abstractions in `ui/async` instead of ad-hoc thread/lifecycle patterns.
7. ViewModels must use `UiDataAdapters` interfaces (avoid direct `core.storage` imports).

## Build and Test

Use the commands already wired in `pom.xml`:

```bash
mvn test
mvn -Ptest-output-verbose test
mvn compile && mvn exec:exec
mvn javafx:run
mvn spotless:apply verify
```

Notes:

- `validate` runs Checkstyle.
- `verify` runs Spotless, PMD, and JaCoCo.
- The JaCoCo bundle line-coverage gate is `0.60`.
- Preview and native-access flags are required; do not remove them casually.

## Architecture

- `src/main/java/datingapp/core/**` is the framework-agnostic domain layer. Do not import UI, database, or web-framework types into `core/`.
- `src/main/java/datingapp/app/**` contains adapters and orchestration: bootstrap, CLI, REST API, event bus, and app-level use cases.
- `src/main/java/datingapp/storage/**` contains concrete storage implementations, schema setup, and database management.
- `src/main/java/datingapp/ui/**` contains the JavaFX application, screens, popups, async utilities, and view models.
- Shared bootstrapping goes through `datingapp.app.bootstrap.ApplicationStartup` and `datingapp.core.ServiceRegistry`.
- Entrypoints are `src/main/java/datingapp/Main.java` for the CLI and `src/main/java/datingapp/ui/DatingApp.java` for JavaFX.

## Project Conventions

- Import nested enums from their owner types:
  - `User.Gender`, `User.UserState`, `User.VerificationMethod`
  - `Match.MatchState`, `Match.MatchArchiveReason`
- `ProfileNote` is a standalone type: `datingapp.core.model.ProfileNote`.
- Use `AppClock.now()` in domain and service logic; do not use `Instant.now()` there.
- Use deterministic pair IDs via `generateId(UUID a, UUID b)` for two-user aggregates.
- Prefer result records such as `*Result.failure(...)` for business-flow failures when that pattern already exists; avoid introducing flow-control exceptions.
- Return defensive copies or unmodifiable views instead of exposing mutable internal collections.
- Use `EnumSetUtil.safeCopy(...)` or equivalent null-safe handling for `EnumSet` values.
- Use injected runtime config from `ServiceRegistry`; keep `AppConfig.defaults()` at bootstrap, composition, or test boundaries only.
- Configuration JSON loading is handled in `ApplicationStartup` via Jackson mix-ins. When adding a config field, update `AppConfig.Builder` and `config/app-config.json`; do not add Jackson annotations in `core/`.
- Do not reintroduce removed names or packages such as `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, or legacy `ui/controller` references.

## UI and Async Rules

- View models should use the shared async abstractions in `src/main/java/datingapp/ui/async/**`.
- Prefer `ViewModelAsyncScope`, `UiThreadDispatcher`, and `AsyncErrorRouter` over ad-hoc `Thread.ofVirtual()` or direct `Platform.runLater()` orchestration.
- View models should depend on `UiDataAdapters` interfaces rather than concrete `core.storage` implementations.

## Key Pattern Files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` - bootstrap, config loading, and Jackson mix-in pattern.
- `src/main/java/datingapp/core/ServiceRegistry.java` - central service and use-case wiring.
- `src/main/java/datingapp/Main.java` - CLI composition root and handler factory usage.
- `src/main/java/datingapp/ui/DatingApp.java` - JavaFX composition root.
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` and `MatchesViewModel.java` - current async ViewModel patterns.

## Avoid Stale References

- Treat `PROJECT_STRUCTURE_GUIDE.md` as historical/deprecated for current structure.
- When docs disagree, prefer `pom.xml` and the current source tree.
