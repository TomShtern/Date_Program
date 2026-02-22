# AGENTS.md - AI Agent Development Guide

## Development Environment

**Platform:** Windows 11 | **Shell:** PowerShell 7.5.4 | **IDE:** VS Code Insiders
**Java:** OpenJDK 25.0.2 (Eclipse Adoptium Temurin) with `--enable-preview`
**Maven:** Apache Maven 3.9.12 | **JavaFX:** 25.0.2 | **Database:** H2 2.4.240 + JDBI 3.51.0
**Theme:** AtlantaFX 2.1.0 | **Icons:** Ikonli 12.4.0 (Material Design 2)

### Available System Tools

- **ripgrep** (`rg`) v14.1.0 - Ultra-fast regex search (primary text search)
- **ast-grep** (`sg`) v0.40.0 - AST-based structural code search (use for code patterns)
- **fd** v10.3.0 - Fast file finder
- **tokei** v12.1.2 - LOC counter
- **bat**, **sd**, **jq**, **yq**, **Semgrep** - Additional utilities

**Tip:** Use `ast-grep --lang java -p '<pattern>'` for structural searches. Prefer `rg` over `grep` for plain-text scans.

## Essential Commands

```bash
# Build & Run
mvn compile                          # Compile source
mvn compile && mvn exec:exec         # Compile + Run CLI (forked JVM with --enable-preview)
mvn javafx:run                       # Run JavaFX GUI app

# Testing (default concise output)
mvn test                             # All tests
mvn -Ptest-output-verbose test       # All tests (verbose diagnostics)
mvn -Dtest=CandidateFinderTest test                    # Single test class
mvn -Ptest-output-verbose -Dtest="StatsHandlerTest#displaysUnlockedAchievements" test  # Single method

# Code Quality (REQUIRED before commit)
mvn spotless:apply                   # Format code (REQUIRED)
mvn spotless:check                   # Check formatting
mvn checkstyle:check                 # Run Checkstyle (validate phase)
mvn pmd:check                        # Run PMD analysis (verify phase)
mvn verify                           # Full build + all quality checks + JaCoCo

# Database Management
Remove-Item .\data\dating.mv.db     # Reset local DB file (PowerShell)
mvn clean                            # Clean build artifacts
```

### Database Password

The app uses an embedded H2 database via HikariCP. `DatabaseManager` resolves passwords by JDBC URL:

- Production/external URLs: require `$env:DATING_APP_DB_PASSWORD`
- Local file URLs (`jdbc:h2:./...`): default password is `dev`
- Test/memory URLs (`jdbc:h2:mem:...` or `*test*`): default password is empty string

### Build Command Discipline

**NEVER run expensive commands multiple times for different filters.** Capture once, query N times:

```powershell
$out = mvn verify 2>&1 | Out-String
$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
$out | Select-String "Tests run:" | Select-Object -Last 1
$out | Select-String "ERROR|WARNING.*violation"
```

## Critical Gotchas (Compilation / Runtime)

| Issue                          | Wrong                                        | Correct                                                           |
|--------------------------------|----------------------------------------------|-------------------------------------------------------------------|
| Nested record/class visibility | `public record SendResult(...)` inside class | `public static record SendResult(...)`                            |
| Model enum location            | `core.model.Gender`, `core.model.UserState`  | `core.model.User.Gender`, `core.model.User.UserState`             |
| `ProfileNote` import           | `core.model.User.ProfileNote`                | `core.model.ProfileNote`                                          |
| Clock usage in domain          | `Instant.now()`                              | `AppClock.now()` (testable)                                       |
| Service error flow             | `throw new ...` for business failure         | Return `*Result.failure(...)` record                              |
| Pair IDs (Match, Conversation) | `a + "_" + b`                                | Lexicographically sorted: `generateId(a, b)`                      |
| EnumSet null crash             | `EnumSet.copyOf(possiblyNull)`               | `EnumSetUtil.safeCopy(...)`                                       |
| Mutable collection exposure    | `return internalSet;`                        | Return defensive copy                                             |
| Bootstrap reference            | `AppBootstrap`, `HandlerFactory`             | `ApplicationStartup` + handler/service `fromServices(...)` wiring |
| UI package reference           | `ui/controller`                              | `ui/screen` and `ui/popup`                                        |

**Config access:** `private static final AppConfig CONFIG = AppConfig.defaults();`

> Note: `ConnectionService.SendResult.ErrorCode` is currently declared as `public enum` inside a `public static record`; nested enums are implicitly static in Java.

## Architecture

```
datingapp/
  Main.java                           # CLI entry point
  app/
    bootstrap/ApplicationStartup.java # Shared initialization
    cli/{CliTextAndInput, MatchingHandler, MessagingHandler, ProfileHandler, SafetyHandler, StatsHandler}.java
    api/RestApiServer.java
  core/
    AppClock, AppConfig, AppSession, EnumSetUtil, LoggingSupport, PerformanceMonitor, ServiceRegistry, TextUtil
    model/{User, Match, ProfileNote}
    matching/{CandidateFinder, CompatibilityScoring, LifestyleMatcher, MatchingService, MatchQualityService, RecommendationService, Standout, TrustSafetyService, UndoService}
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

**Three-Layer Clean Architecture:**

- **Layer 1 (`core/`)**: Pure Java business logic — ZERO framework/database imports
- **Layer 2 (`storage/`)**: JDBI + H2 implementations and schema
- **Layer 3A (`app/cli/`)**: Console handlers and interactive flows
- **Layer 3B (`ui/`)**: JavaFX MVVM app

## Entry Points

```java
// Shared initialization for CLI + JavaFX
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring in Main.java
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

## Code Style

**Formatting:** Palantir Java Format v2.85.0. Run `mvn spotless:apply` before committing.

**Imports:** No star imports. Keep imports clean; avoid accidental reordering churn.

**Naming:**

- Classes: `PascalCase`
- Methods/fields: `camelCase`
- Predicates: `is`/`has`/`can`
- Constants: `UPPER_SNAKE_CASE`

**Types:**

- `record` for immutable data contracts (`ConnectionModels.Message`, service result records)
- `class` for mutable entities (`User`, `Match`)
- `enum` for fixed sets (domain enums nested under owning model)
- `Optional<T>` for nullable-return contracts
- Nested records/classes used cross-file should be `public static`; enums are implicitly static

**State Machines:**

- `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Match`: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`

## Error Handling

**Validation:**

- Constructor args: `Objects.requireNonNull(param, "param cannot be null")`
- Business rules: `IllegalArgumentException`
- Invalid state transitions: `IllegalStateException`

**Storage layer:** wrap SQL/infrastructure failures in storage/runtime exceptions.

**Service layer:** return result records for business failures instead of throwing.

```java
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
```

**CLI/UI:** show user-friendly messages; do not leak raw exceptions to end users.

## Testing Standards

**Test structure:**

- JUnit 5 + `@Nested` grouping
- Test class naming: `{ClassName}Test`
- Deterministic timing with `@Timeout(...)` where appropriate

**Mocking strategy:** use `TestStorages` (no Mockito framework dependency in current tests).

```java
var userStorage = new TestStorages.Users();
var interactionStorage = new TestStorages.Interactions();
var commStorage = new TestStorages.Communications();
var analyticsStorage = new TestStorages.Analytics();
var trustSafetyStorage = new TestStorages.TrustSafety();
```

**Utilities:**

- `TestClock.setFixed(...)` / `TestClock.reset()`
- `TestUserFactory`

**Coverage:** JaCoCo minimum line coverage is **60%** (bundle-level check in `verify`, with UI and CLI exclusions).

## Special Patterns

**Config JSON loading (Jackson mix-in, `ApplicationStartup`):**

```java
// applyJsonConfig() — replaces the old manual applyInt/applyDouble dispatch.
// BuilderMixin uses @JsonAutoDetect(fieldVisibility=ANY) so Jackson populates
// AppConfig.Builder private fields directly by JSON key name. No Jackson
// annotations are needed inside core/.
MAPPER.readerForUpdating(builder).readValue(json);

// Adding a new config property requires ONLY:
// 1. Add field + setter to AppConfig.Builder (camelCase matches JSON key).
// 2. Add key to config/app-config.json.
// ApplicationStartup itself does NOT need to change.
```

**Deterministic IDs** (Match, Conversation):

```java
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

**StorageBuilder pattern** (reconstruct from storage):

```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio)
    .birthDate(birthDate)
    .gender(gender)
    .build();
```

**Touch pattern** (mutable entities):

```java
private void touch() { this.updatedAt = AppClock.now(); }
public void setBio(String bio) { this.bio = bio; touch(); }
```

**EnumSet defensive pattern:**

```java
this.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
return EnumSetUtil.safeCopy(interestedIn, Gender.class);
```

## MVVM UI Architecture (JavaFX)

**BaseController lifecycle:**

- `addSubscription(Subscription)`
- `registerOverlay(Node)`
- `trackAnimation(Animation)`
- `cleanup()` on navigation/disposal

**ViewModel error propagation:**

```java
private ViewModelErrorSink errorHandler;
public void setErrorHandler(ViewModelErrorSink handler) { this.errorHandler = handler; }
// Controller wiring
viewModel.setErrorHandler(UiFeedbackService::showError);
```

**Navigation context handoff:**

```java
navigationService.setNavigationContext(payload);
navigationService.navigateTo(ViewType.CHAT);
Object context = navigationService.consumeNavigationContext();
```

**Threading:** `Thread.ofVirtual()` for background work, `Platform.runLater()` for UI-bound updates.

**ViewModel storage decoupling:** depend on `UiDataAdapters.UiUserStore`, `UiMatchDataAccess`, `UiSocialDataAccess` interfaces, not `core.storage` interfaces directly.

## JDBI Layer Conventions

- Use `JdbiTypeCodecs.SqlRowReaders.*` for null-safe reads
- Prefer `ALL_COLUMNS` constants to avoid column drift
- Map persisted rows back through model `StorageBuilder`

```java
public final class JdbiUserStorage implements UserStorage {
    public static final String ALL_COLUMNS = "id, name, ...";

    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id")
        User get(@Bind("id") UUID id);
    }
}
```

## CLI Handler Patterns

**Construction boundary:** use `fromServices(...)` helper methods from handlers (`MatchingHandler.Dependencies.fromServices(...)`, `MessagingHandler.fromServices(...)`, etc.) at composition root.

**Input validation template:**

```java
try {
    int value = Integer.parseInt(inputReader.readLine("Value: "));
    // validate bounds...
} catch (NumberFormatException e) {
    logInfo("⚠️  Invalid number format.");
}
```

## Code Quality Checklist

**Before committing:**

1. Run `mvn spotless:apply`
2. Run `mvn verify`
3. Keep `core/` free from framework/DB imports
4. Keep dependency injection explicit in constructors/builders
5. Use safe nested-type visibility (`public static` for records/classes crossing files)
6. Use result records for business-flow failures

## Never Do These

- ❌ Import framework/DB APIs into `core/` domain/service code
- ❌ Use removed names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, `ScoringConstants`)
- ❌ Import removed standalone model enums (`core.model.Gender`, `core.model.UserState`, `core.model.MatchState`, etc.)
- ❌ Throw business-flow exceptions where result records exist
- ❌ Return mutable internal collections directly
- ❌ Forget `touch()` in mutable entity setters
- ❌ Use `Instant.now()` where `AppClock.now()` is expected
- ❌ Import `core.storage.*` directly into ViewModels

## Known Limitations

- Cross-storage writes are not fully transactional in some flows
- Undo state is in-memory (lost on restart)
- Email/phone verification delivery is simulated in CLI flow
- Recommendation daily-pick cache is in-memory LRU (`MAX_CACHED_PICKS=1000`)

---
**Last Updated:** 2026-02-22 | **Java Files:** 87 main + 65 test (152 total)
**LOC:** 48,494 total / 37,150 code | **Coverage Gate:** 60% minimum (JaCoCo)
