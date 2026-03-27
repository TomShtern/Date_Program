we are on windows 11, usually using powershell, we are working in VS Code-Insiders (sometimes in IntelliJ). we are using java 25, and using javafx 25, maven, palantir format/style and the java by Red Hat extension.
make sure to leverage the tools you have as an ai coding agent together with the IDE tools and also the tools we have here on this system.

You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.

Deploy multiple parallel subagents for different tasks when needed, and coordinate their work through a parent agent. Use the `agent` tool to invoke subagents with specific instructions and context. For example, you might have one subagent focused on code analysis using ast-grep, while another handles code edits or refactoring. The parent agent can manage the overall workflow, ensuring that each subagent has the information it needs to perform its task effectively while keeping the process organized and efficient.
The goal is to have you, the parent agent, orchestrate the work of multiple specialized subagents to achieve complex tasks that require different types of expertise or operations, such as code analysis, refactoring, testing, and documentation. Each subagent can focus on its specific area while you coordinate their efforts to ensure a cohesive and efficient workflow.
Dont forget to use the specialized agents when appropriate, such as the executionSubagent for executing shell commands or the searchSubagent, or the runSubagent command.
You should use the 'gpt-5.4-mini' model for subagents as it is optimized for fast, focused tasks that benefit from lower latency and cost.

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

Optimize for: predictable, minimal, contract-driven code
Determinism: same inputs → same outputs. Seed randomness and isolate side effects.
Explicit contracts: types + schemas + pre/post conditions. Use OpenAPI/JSON Schema/Protobuf where relevant.
Small modules & functions: tiny units of behavior that are easy to test and swap.
Idempotent interfaces: safe to retry; clear state transitions.
Observability: structured logs, metrics, clear errors — machines need signals.
Stable public surface: semantic versioning, changelogs, and strict backward-compat rules.
Concrete rules / thresholds (safe defaults)
Max function length: ≤ 85 lines.
Max nesting depth: ≤ 4.
Cyclomatic complexity per function: ≤ 9.
One responsibility per function/module (SRP).

# Dating App - AI Agent Instructions

**Platform:** Windows 11 | PowerShell 7.5.x | VS Code Insiders | Java 25 (preview enabled) | JavaFX 25.0.2
**Verified snapshot (2026-03-11):** 140 main + 107 test Java files (247 total) | 66,698 LOC / 52,170 code | JaCoCo line coverage gate: 60%

**SOURCE-OF-TRUTH RULE:** If any document conflicts with code, trust `src/main/java`, `src/test/java`, and `pom.xml`.

## Archived reference

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

## Archived architecture snapshot

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
    AppClock,AppConfig,AppSession,EnumSetUtil,LoggingSupport,ServiceRegistry,TextUtil
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

## Archived correctness rules

1. Use nested model enums from owners:
   - `User.Gender`, `User.UserState`, `User.VerificationMethod`
   - `Match.MatchState`, `Match.MatchArchiveReason`
2. `ProfileNote` is standalone: `datingapp.core.model.ProfileNote`.
3. Use `AppClock.now()` in domain/service logic (not `Instant.now()`).
4. Use deterministic pair IDs (`generateId(UUID a, UUID b)`) for two-user aggregates.
5. Service business failures should return result records when provided (do not throw flow exceptions).
6. ViewModels should use shared async abstractions in `ui/async` instead of ad-hoc thread/lifecycle patterns.
7. ViewModels must use `UiDataAdapters` interfaces (avoid direct `core.storage` imports).

## Archived build and test commands

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

## Archived architecture notes

- `src/main/java/datingapp/core/**` is the framework-agnostic domain layer. Do not import UI, database, or web-framework types into `core/`.
- `src/main/java/datingapp/app/**` contains adapters and orchestration: bootstrap, CLI, REST API, event bus, and app-level use cases.
- `src/main/java/datingapp/storage/**` contains concrete storage implementations, schema setup, and database management.
- `src/main/java/datingapp/ui/**` contains the JavaFX application, screens, popups, async utilities, and view models.
- Shared bootstrapping goes through `datingapp.app.bootstrap.ApplicationStartup` and `datingapp.core.ServiceRegistry`.
- Entrypoints are `src/main/java/datingapp/Main.java` for the CLI and `src/main/java/datingapp/ui/DatingApp.java` for JavaFX.

## Archived conventions

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

## Archived UI and async notes

- View models should use the shared async abstractions in `src/main/java/datingapp/ui/async/**`.
- Prefer `ViewModelAsyncScope`, `UiThreadDispatcher`, and `AsyncErrorRouter` over ad-hoc `Thread.ofVirtual()` or direct `Platform.runLater()` orchestration.
- View models should depend on `UiDataAdapters` interfaces rather than concrete `core.storage` implementations.

## Archived key pattern files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` - bootstrap, config loading, and Jackson mix-in pattern.
- `src/main/java/datingapp/core/ServiceRegistry.java` - central service and use-case wiring.
- `src/main/java/datingapp/Main.java` - CLI composition root and handler factory usage.
- `src/main/java/datingapp/ui/DatingApp.java` - JavaFX composition root.
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` and `MatchesViewModel.java` - current async ViewModel patterns.

## Archived caveats

- Treat `PROJECT_STRUCTURE_GUIDE.md` as historical/deprecated for current structure.
- When docs disagree, prefer `pom.xml` and the current source tree.

## Tooling and MCP inventory

Use the smallest tool that gives the needed answer. Prefer structure-aware tools first, then fall back to text search.

### Local code search and refactors

- `ast-grep` (`sg`) for syntax-aware search and transformations; use this first for code structure queries.
- `search_subagent` for fast read-only codebase exploration.
- `semantic_search` when you need broad workspace context and exact wording is uncertain.
- `grep_search` for exact text searches and `file_search` for path existence checks.
- `vscode_listCodeUsages` and `vscode_renameSymbol` for symbol-safe navigation and refactors.

### Workspace editing and orchestration

- `apply_patch` for file edits.
- `create_file`, `create_directory`, and `list_dir` for scaffolding or file creation.
- `runSubagent` for isolated analysis tasks that should return one consolidated result.
- `multi_tool_use.parallel` for independent read-only lookups that can run together.
- `manage_todo_list` to keep multi-step work explicit and current.

### Commands, tasks, and execution

- `run_in_terminal` for direct shell commands when you need full command output.
- `execution_subagent` for execution-focused shell work that should be summarized.
- `run_task` and `create_and_run_task` for repo task execution through VS Code.
- `get_task_output`, `get_terminal_output`, `terminal_last_command`, and `terminal_selection` for follow-up inspection.
- `kill_terminal` to clean up long-running or stale processes.

### Validation and debugging

- `runTests` for targeted or full test execution.
- `get_errors` after edits to catch compile or lint issues quickly.
- `sonarqube_analyze_file` for deep file-level analysis when needed.
- Java debug tools: `debug_java_application`, `get_debug_session_info`, `get_debug_threads`, `get_debug_stack_trace`, `get_debug_variables`, `evaluate_debug_expression`, `set_java_breakpoint`, `debug_step_operation`, `remove_java_breakpoints`, and `stop_debug_session`.
- `get_changed_files` when you need the active git diff.

### External research and documentation

- `fetch_webpage` for user-provided URLs and any linked pages that matter.
- `mcp_exa_web_search_exa` and `mcp_exa_get_code_context_exa` for current web and code examples.
- `mcp_upstash_conte_resolve-library-id` and `mcp_upstash_conte_query-docs` for library documentation lookups.
- `get_vscode_api` for VS Code extension API questions.
- `vscode-websearchforcopilot_webSearch` for quick web search inside the editor experience.

### Memory and clarifying questions

- `memory` for durable user, session, and repository notes.
- `resolve_memory_file_uri` when a memory file URI is needed elsewhere.
- `vscode_askQuestions` when a small clarification would prevent guesswork.

### Practical usage rules

- For syntax-aware code questions, default to `ast-grep` before plain-text search.
- For independent reads, batch tools in parallel instead of serially.
- For changes that touch several symbols, prefer symbol-aware rename/usages tools over manual find/replace.
- After edits, verify the touched files before broad test runs.

## Verified source tree snapshot

```text
datingapp/
  Main.java
  app/
    api/{RestApiDtos,RestApiServer}.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    error/{AppError,AppResult}.java
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
    model/{User,Match,ProfileNote}
    connection/{ConnectionModels,ConnectionService}
    matching/{CandidateFinder,CompatibilityScoring,LifestyleMatcher,MatchingService,MatchQualityService,RecommendationService,Standout,TrustSafetyService,UndoService}
    metrics/{ActivityMetricsService,EngagementDomain,SwipeState}
    profile/{LocationService,MatchPreferences,ProfileCompletionSupport,ProfileService,ValidationService}
    storage/{AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}
    time/{DefaultTimePolicy,TimePolicy}
    workflow/{ProfileActivationPolicy,RelationshipWorkflowPolicy,WorkflowDecision}
  storage/
    DatabaseManager.java
    DevDataSeeder.java
    StorageFactory.java
    jdbi/{JdbiAccountCleanupStorage,JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    DatingApp,NavigationService,ImageCache,UiAnimations,UiComponents,UiConstants,UiFeedbackService,UiUtils
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,PollingTaskHandle,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}
    popup/{MilestonePopupController}.java
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,NotesController,PreferencesController,ProfileController,ProfileViewController,SafetyController,SocialController,StandoutsController,StatsController}.java
    viewmodel/{BaseViewModel,ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,NotesViewModel,PreferencesViewModel,ProfileReadOnlyViewModel,ProfileViewModel,SafetyViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}
```

## High-friction conventions

- Build wiring should flow through `ServiceRegistry` and `fromServices(...)`; avoid direct cross-layer construction in callers.
- ViewModels should use `ViewModelAsyncScope`, `PollingTaskHandle`, `AsyncErrorRouter`, and `UiDataAdapters`; avoid ad-hoc threading and direct storage coupling.
- Use `AppClock.now()` in domain and service logic; do not introduce `Instant.now()` in those layers.
- Use deterministic pair IDs with `generateId(UUID a, UUID b)` for two-user aggregates.
- Keep `ProfileNote` as the standalone model type: `datingapp.core.model.ProfileNote`.
- Prefer result records for business failures when that pattern already exists.
- Return defensive copies or unmodifiable views instead of exposing mutable internal collections.
- Use `EnumSetUtil.safeCopy(...)` or equivalent null-safe handling for `EnumSet` values.
- Keep runtime config injection on `AppConfig` from `ServiceRegistry`; reserve `AppConfig.defaults()` for bootstrap, composition, or tests.

## Runtime gotchas

- `RestApiServer` binds to loopback only by design; do not assume external host access.
- `DevDataSeeder` is env-gated and idempotent; it should remain safe to call during startup.
- Record-typed JDBI parameters should use `@BindMethods`, not `@BindBean`.
- Keep date/time formatting locale-explicit when user-facing text depends on month names or weekday names.

## Build and verification order

1. Make the smallest correct edit.
2. Run `get_errors` on the touched file(s).
3. Run targeted tests for the affected area.
4. Use `mvn test` for broader confirmation when needed.
5. Reserve `mvn spotless:apply verify` for final quality-gate checks or broader refactors.

## Key pattern files

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` - bootstrap, config loading, and Jackson mix-in pattern.
- `src/main/java/datingapp/core/ServiceRegistry.java` - central service and use-case wiring.
- `src/main/java/datingapp/Main.java` - CLI composition root and handler wiring.
- `src/main/java/datingapp/ui/DatingApp.java` - JavaFX composition root.
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`, `MatchesViewModel.java`, and `ProfileViewModel.java` - current async ViewModel patterns.

## Keep these out of new work

- Historical or removed names such as `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, and `ui/controller`.
- Stale architecture docs when they conflict with the source tree.
- New direct dependencies from `core/` into UI, database, or web-framework types.

