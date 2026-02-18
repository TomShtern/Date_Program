we are on windows 11, usually using powershell, we are working in VS Code-Insiders(sometimes in InteliJ). we are using java 25, and using javafx 25.
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
**Verified snapshot (2026-02-18):** 84 main + 59 test Java files (143 total) | 43,455 LOC / 33,227 code | JaCoCo line coverage gate: 60%

## ⚠️ Critical Gotchas (Compilation / Runtime / Agent Accuracy)

| Issue                           | Wrong                                           | Correct                                                |
|---------------------------------|-------------------------------------------------|--------------------------------------------------------|
| Non-static nested type          | `public record SendResult(...) {}` inside class | `public static record SendResult(...) {}`              |
| Nested enum in nested type      | `public enum ErrorCode { ... }`                 | `public static enum ErrorCode { ... }`                 |
| Externally-used nested type ref | `import datingapp.core.model.User.Gender`       | `import datingapp.core.model.Gender`                   |
| Clock usage                     | `Instant.now()` in domain/service logic         | `AppClock.now()` (testable via `TestClock`)            |
| Service error flow              | `throw new ...` for business failure            | Return `*Result.failure(...)` record                   |
| Pair IDs                        | `a + "_" + b`                                   | lexicographically sorted deterministic ID              |
| EnumSet null crash              | `EnumSet.copyOf(possiblyNull)`                  | `EnumSetUtil.safeCopy(...)` or null-safe conditional   |
| Mutable collection exposure     | `return internalSet;`                           | Return defensive copy / unmodifiable view              |
| Legacy bootstrap references     | `AppBootstrap`, `HandlerFactory`                | `ApplicationStartup` + direct handler wiring in `Main` |
| Legacy UI references            | `ui/controller`, `Toast`, `UiSupport`           | `ui/screen`, `UiFeedbackService`, `UiComponents`       |

**Config access pattern:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Current Architecture (source-of-truth)

```text
datingapp/
  Main.java                         # CLI entry point
  app/
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput, MatchingHandler, MessagingHandler, ProfileHandler, SafetyHandler, StatsHandler}.java
    api/RestApiServer.java
  core/
    AppClock, AppConfig, AppSession, EnumSetUtil, LoggingSupport, PerformanceMonitor, ServiceRegistry, TextUtil
    model/{User, Match, Gender, UserState, MatchState, MatchArchiveReason, VerificationMethod, ProfileNote}
    matching/{CandidateFinder, MatchingService, MatchQualityService, RecommendationService, TrustSafetyService, UndoService, Standout, LifestyleMatcher}
    connection/{ConnectionModels, ConnectionService}
    profile/{ProfileService, ValidationService, MatchPreferences}
    metrics/{ActivityMetricsService, EngagementDomain, SwipeState}
    storage/{UserStorage, InteractionStorage, CommunicationStorage, AnalyticsStorage, TrustSafetyStorage}
  storage/
    DatabaseManager, StorageFactory
    jdbi/{JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage, JdbiMetricsStorage, JdbiTrustSafetyStorage, JdbiTypeCodecs}
    schema/{SchemaInitializer, MigrationRunner}
  ui/
    DatingApp, NavigationService, UiComponents, UiFeedbackService, UiConstants, UiAnimations, ImageCache
    screen/{BaseController + 8 screen controllers + MilestonePopupController}
    viewmodel/{8 ViewModels + ViewModelFactory + ViewModelErrorSink + UiDataAdapters}
```

## Entry Points (current)

```java
// Shared initialization for CLI + JavaFX
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring in Main.java (no HandlerFactory)
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matching = new MatchingHandler(new MatchingHandler.Dependencies(...));

// JavaFX wiring in DatingApp.java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Domain Model Reality Check

| Model / Enum                                                                    | Location                                | Notes                                                                                 |
|---------------------------------------------------------------------------------|-----------------------------------------|---------------------------------------------------------------------------------------|
| `User`                                                                          | `core/model/User.java`                  | Mutable entity + `StorageBuilder` + `touch()` + soft-delete                           |
| `Match`                                                                         | `core/model/Match.java`                 | Mutable entity + deterministic ID + state transitions                                 |
| `Gender`, `UserState`, `MatchState`, `MatchArchiveReason`, `VerificationMethod` | `core/model/*.java`                     | Standalone top-level enums (not nested in `User`/`Match`)                             |
| `ProfileNote`                                                                   | `core/model/ProfileNote.java`           | Record                                                                                |
| `ConnectionModels.*`                                                            | `core/connection/ConnectionModels.java` | `Message`, `Conversation`, `Like`, `Block`, `Report`, `FriendRequest`, `Notification` |

**Deterministic ID rule (always):**
```java
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

## JavaFX + MVVM Patterns You Should Follow

### ViewModel creation and caching
- `ui/viewmodel/ViewModelFactory.java` owns lazy singleton ViewModels.
- FXML controller creation flows through `createController(...)` factory mapping.

### Error propagation pattern
```java
// ViewModel side
private ViewModelErrorSink errorHandler;
public void setErrorHandler(ViewModelErrorSink handler) { this.errorHandler = handler; }

// Controller side
viewModel.setErrorHandler(UiFeedbackService::showError);
```

### Base controller lifecycle
- All UI controllers extend `ui/screen/BaseController`.
- Register listeners via `addSubscription(...)`.
- Register overlays via `registerOverlay(...)`.
- Track long-running animations via `trackAnimation(...)`.
- Cleanup is centralized in `cleanup()`.

### Navigation context handoff
```java
navigationService.setNavigationContext(payload);
navigationService.navigateTo(ViewType.CHAT);

Object context = navigationService.consumeNavigationContext();
```

### Threading in ViewModels
- Use `Thread.ofVirtual()` for background work.
- Use `Platform.runLater(...)` for UI-bound property updates.

## Build / Test / Quality Commands

```bash
mvn compile && mvn exec:exec
mvn javafx:run

# test workflow
mvn test
mvn -Ptest-output-verbose test
mvn -Ptest-output-verbose -Dtest=StatsHandlerTest test
mvn -Ptest-output-verbose -Dtest="StatsHandlerTest#displaysUnlockedAchievements" test

# required pre-commit gate
mvn spotless:apply && mvn verify
```

## Maven / Tooling Facts That Matter

- Compiler release: `25` with `--enable-preview`.
- Surefire argLine includes preview + native access flags.
- JavaFX plugin runs `datingapp.ui.DatingApp` with preview/native-access options.
- Spotless uses **Palantir Java Format** (`2.85.0`) and runs check in `verify`.
- Checkstyle is enforced in `validate` (140-char limit + strict naming/braces rules).
- PMD check runs in `verify` using `pmd-rules.xml`.
- JaCoCo check in `verify` enforces bundle line coverage >= `0.60`.

## Test Patterns (current)

Use `TestStorages` (no Mockito):
```java
var userStorage = new TestStorages.Users();
var interactionStorage = new TestStorages.Interactions();
var commStorage = new TestStorages.Communications();
var analyticsStorage = new TestStorages.Analytics();
var trustSafetyStorage = new TestStorages.TrustSafety();
```

Utilities:
- `TestClock.setFixed(...)` / `TestClock.reset()` for deterministic time.
- `TestUserFactory` for quick user fixture creation.

## Core Patterns You Should Prefer

### Storage reconstruction
```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio)
    .birthDate(birthDate)
    .gender(gender)
    .build();
```

### Services return structured results
```java
return SendResult.failure("Cannot message: no active match", SendResult.ErrorCode.NO_ACTIVE_MATCH);
```

### EnumSet defensive handling
```java
this.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
return EnumSetUtil.safeCopy(interestedIn, Gender.class);
```

### Mutable entity touch pattern
```java
private void touch() { this.updatedAt = AppClock.now(); }
```

### ViewModel storage decoupling
- ViewModels depend on `UiDataAdapters.UiUserStore` / `UiMatchDataAccess`.
- Adapter implementations (`StorageUiUserStore`, `StorageUiMatchDataAccess`) bridge to `core.storage`.

## JDBI Layer Conventions

- Prefer `JdbiTypeCodecs.SqlRowReaders.*` for null-safe ResultSet reads.
- `JdbiUserStorage.ALL_COLUMNS` style constants reduce SQL column drift.
- Build domain objects through storage builders (e.g., `User.StorageBuilder`) in mappers.

## VS Code Java LS Stability (Important)

- Keep `target/` out of `search.exclude` only (already configured), not out of Java project resource model.
- `.vscode/tasks.json` includes:
  - `Set UTF-8 Console`
  - `Ensure Maven Output Dirs` (runs on folder open)
- If diagnostics look stale but Maven compiles:
  1. Run `mvn compile`
  2. Run **Java: Clean Java Language Server Workspace**
  3. Restart VS Code Java LS

## NEVER Do These

- ❌ Import framework/DB APIs into `core/` domain/service code.
- ❌ Use removed architecture names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, `ScoringConstants`).
- ❌ Use nested `User.Gender` / `User.UserState` / `Match.State` style imports.
- ❌ Throw business-flow exceptions from service operations when result records exist.
- ❌ Return mutable internal collections directly.
- ❌ Forget `touch()` in mutable entity setters.
- ❌ Use `Instant.now()` in domain logic where `AppClock.now()` is expected.
- ❌ Import `core.storage.*` directly into ViewModels (use `UiDataAdapters` interfaces).

## Known Limitations (intentional / accepted)

- Cross-storage writes in parts of `ConnectionService` are not fully transactional.
- Undo state is in-memory and does not survive restart.
- ViewModel instances are cached singletons within `ViewModelFactory` by design.

## Docs Worth Consulting

| Doc                                    | Purpose                                    |
|----------------------------------------|--------------------------------------------|
| `AGENTS.md`                            | Comprehensive standards and patterns       |
| `CLAUDE.md`                            | Project-specific rules and updated gotchas |
| `docs/architecture.md`                 | High-level architecture diagrams           |
| `docs/core-module-overview.md`         | Core module orientation                    |
| `docs/storage-module-overview.md`      | Storage + schema details                   |
| `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` | Historical review findings                 |
