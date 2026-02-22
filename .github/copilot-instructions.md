we are on windows 11, usually using powershell, we are working in VS Code-Insiders(sometimes in InteliJ). we are using java 25, and using javafx 25, maven, palantir format/style and the java by redhat extention.

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
**Verified snapshot (2026-02-22):** 87 main + 65 test Java files (152 total) | 48,494 LOC / 37,150 code | JaCoCo line coverage gate: 60%

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

**Config access pattern:** `private static final AppConfig CONFIG = AppConfig.defaults();`

**Config JSON loading:** `ApplicationStartup.applyJsonConfig()` uses Jackson databinding (`readerForUpdating(builder).readValue(json)`) via a `BuilderMixin` — no annotations in `core/`. Adding a new config property only requires a field + setter in `AppConfig.Builder` and an entry in `app-config.json`. No `ApplicationStartup` edits needed.

> Note: `ConnectionService.SendResult.ErrorCode` is currently declared as `public enum` inside a `public static record`; Java treats nested enums as implicitly static.

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
    model/{User, Match, ProfileNote}
    matching/{CandidateFinder, CompatibilityScoring, MatchingService, MatchQualityService, RecommendationService, TrustSafetyService, UndoService, Standout, LifestyleMatcher}
    connection/{ConnectionModels, ConnectionService}
    profile/{ProfileService, ValidationService, MatchPreferences}
    metrics/{ActivityMetricsService, EngagementDomain, SwipeState}
    storage/{UserStorage, InteractionStorage, CommunicationStorage, AnalyticsStorage, TrustSafetyStorage}
  storage/
    DatabaseManager, StorageFactory
    jdbi/{JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage, JdbiMetricsStorage, JdbiTrustSafetyStorage, JdbiTypeCodecs}
    schema/{SchemaInitializer, MigrationRunner}
  ui/
    DatingApp, NavigationService, UiComponents, UiFeedbackService, UiConstants, UiAnimations, ImageCache, UiUtils
    screen/{BaseController + 10 screen controllers + MilestonePopupController}
    popup/{MatchPopupController, MilestonePopupController}
    viewmodel/{10 ViewModels + ViewModelFactory + ViewModelErrorSink + UiDataAdapters}
```

## Entry Points (current)

```java
// Shared initialization for CLI + JavaFX
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring in Main.java (service-based helper construction)
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matching = new MatchingHandler(MatchingHandler.Dependencies.fromServices(services, session, inputReader));
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);

// JavaFX wiring in DatingApp.java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Domain Model Reality Check

| Model / Enum                                               | Location                                | Notes                                                                                 |
|------------------------------------------------------------|-----------------------------------------|---------------------------------------------------------------------------------------|
| `User`                                                     | `core/model/User.java`                  | Mutable entity + `StorageBuilder` + `touch()` + soft-delete + nested enums            |
| `Match`                                                    | `core/model/Match.java`                 | Mutable entity + deterministic ID + state transitions + nested enums                  |
| `ProfileNote`                                              | `core/model/ProfileNote.java`           | Standalone immutable record (not nested in `User`)                                    |
| `User.Gender`, `User.UserState`, `User.VerificationMethod` | `core/model/User.java`                  | Public nested user domain types                                                       |
| `Match.MatchState`, `Match.MatchArchiveReason`             | `core/model/Match.java`                 | Public nested match domain types                                                      |
| `ConnectionModels.*`                                       | `core/connection/ConnectionModels.java` | `Message`, `Conversation`, `Like`, `Block`, `Report`, `FriendRequest`, `Notification` |

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
- All screen controllers extend `ui/screen/BaseController`.
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
- ViewModels depend on `UiDataAdapters` interfaces (`UiUserStore`, `UiMatchDataAccess`, `UiSocialDataAccess`).
- Adapter implementations bridge to `core.storage` in `ViewModelFactory`.

## JDBI Layer Conventions

- Prefer `JdbiTypeCodecs.SqlRowReaders.*` for null-safe ResultSet reads.
- `JdbiUserStorage.ALL_COLUMNS` style constants reduce SQL column drift.
- Build domain objects through storage builders (e.g., `User.StorageBuilder`) in mappers.

## VS Code Java LS Stability (Important)

- Keep `target/**` excluded from file/search/watch noise, but not from Java project resource filters.
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
- ❌ Import removed standalone model enums (`core.model.Gender`, `UserState`, `VerificationMethod`, `MatchState`, `MatchArchiveReason`).
- ❌ Throw business-flow exceptions from service operations when result records exist.
- ❌ Return mutable internal collections directly.
- ❌ Forget `touch()` in mutable entity setters.
- ❌ Use `Instant.now()` in domain logic where `AppClock.now()` is expected.
- ❌ Import `core.storage.*` directly into ViewModels (use `UiDataAdapters` interfaces).

## Known Limitations (intentional / accepted)

- Cross-storage writes in parts of `ConnectionService` are not fully transactional.
- Undo state is in-memory and does not survive restart.
- ViewModel instances are cached singletons within `ViewModelFactory` by design.
- Recommendation daily picks use in-memory LRU caching (`MAX_CACHED_PICKS=1000`).

## Docs Worth Consulting

> Docs may lag. Always verify against `src/main/java` and `pom.xml` first.

| Doc                                    | Purpose                                    |
|----------------------------------------|--------------------------------------------|
| `AGENTS.md`                            | Comprehensive standards and patterns       |
| `CLAUDE.md`                            | Project-specific rules and updated gotchas |
| `docs/architecture.md`                 | High-level architecture diagrams           |
| `docs/core-module-overview.md`         | Core module orientation                    |
| `docs/storage-module-overview.md`      | Storage + schema details                   |
| `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` | Historical review findings                 |
