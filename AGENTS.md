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

**Tip:** Use `ast-grep --lang java -p '<pattern>'` for structural searches. Prefer `rg` over `grep`.

## Essential Commands

```bash
# Build & Run
mvn compile                          # Compile source
mvn compile && mvn exec:exec         # Compile + Run CLI (forked JVM with --enable-preview)
mvn javafx:run                       # Run JavaFX GUI app

# Testing (default concise output)
mvn test                             # All tests
mvn -Ptest-output-verbose test       # All tests (verbose diagnostics)
mvn test -Dtest=CandidateFinderTest                    # Single test class
mvn -Ptest-output-verbose -Dtest="StatsHandlerTest#displaysUnlockedAchievements" test  # Single method

# Code Quality (REQUIRED before commit)
mvn spotless:apply                   # Format code (REQUIRED)
mvn spotless:check                   # Check formatting
mvn checkstyle:check                 # Run Checkstyle (validate phase)
mvn pmd:check                        # Run PMD analysis (verify phase)
mvn verify                           # Full build + all quality checks + JaCoCo

# Database Management
rm ./data/dating.mv.db               # Reset database (delete all data)
mvn clean                            # Clean build artifacts

### Database Password
The app uses an embedded H2 database managed via HikariCP. `DatabaseManager` handles passwords automatically based on the JDBC URL:
- Production/external: Requires `$env:DATING_APP_DB_PASSWORD` set in shell.
- Local File (`jdbc:h2:./...`): Auto-defaults to `dev`.
- Test Memory (`jdbc:h2:mem:...`): Auto-defaults to `""`.
```

### Build Command Discipline

**NEVER run expensive commands multiple times.** Capture once, query N times:

```powershell
# CORRECT: single run, multiple filters
$out = mvn verify 2>&1 | Out-String
$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
$out | Select-String "Tests run:" | Select-Object -Last 1
```

## Critical Gotchas (Compilation / Runtime)

| Issue                          | Wrong                                        | Correct                                               |
|--------------------------------|----------------------------------------------|-------------------------------------------------------|
| Nested type visibility         | `public record SendResult(...)` inside class | `public static record SendResult(...)`                |
| Model enum location            | `core.model.Gender`, `core.model.UserState`  | `core.model.User.Gender`, `core.model.User.UserState` |
| Clock usage in domain          | `Instant.now()`                              | `AppClock.now()` (testable)                           |
| Service error flow             | `throw new ...` for business failure         | Return `*Result.failure(...)` record                  |
| Pair IDs (Match, Conversation) | `a + "_" + b`                                | Lexicographically sorted: `generateId(a, b)`          |
| EnumSet null crash             | `EnumSet.copyOf(possiblyNull)`               | `EnumSetUtil.safeCopy(...)`                           |
| Mutable collection exposure    | `return internalSet;`                        | Return defensive copy                                 |
| Bootstrap reference            | `AppBootstrap`                               | `ApplicationStartup`                                  |
| UI controller package          | `ui/controller`                              | `ui/screen`                                           |

**Config access:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Architecture

```
datingapp/
  Main.java                           # CLI entry point
  app/
    bootstrap/ApplicationStartup.java # Singleton initializer
    cli/{MatchingHandler, ProfileHandler, SafetyHandler, StatsHandler, MessagingHandler, CliTextAndInput}.java
    api/RestApiServer.java
  core/
    AppClock, AppConfig, AppSession, EnumSetUtil, LoggingSupport, PerformanceMonitor, ServiceRegistry, TextUtil
        model/{User, Match}  # user/match enums and ProfileNote are nested public static types
    matching/{CandidateFinder, MatchingService, MatchQualityService, RecommendationService, TrustSafetyService, UndoService, Standout, LifestyleMatcher}
    connection/{ConnectionService, ConnectionModels}
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

**Three-Layer Clean Architecture:**
- **Layer 1 (core/):** Pure Java business logic - ZERO framework/database imports
- **Layer 2 (storage/):** JDBI 3 + H2 database implementations
- **Layer 3A (app/cli/):** Console handlers with interactive menu
- **Layer 3B (ui/):** JavaFX MVVM with AtlantaFX theme

## Entry Points

```java
// Shared initialization for CLI + JavaFX
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI wiring in Main.java (direct handler construction)
InputReader inputReader = new InputReader(scanner);
MatchingHandler matching = new MatchingHandler(new MatchingHandler.Dependencies(...));

// JavaFX wiring in DatingApp.java
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Code Style

**Formatting:** Palantir Java Format v2.85.0 (4-space indentation). Run `mvn spotless:apply` before every commit.

**Imports:** No star imports. Order: static first, then third-party, then standard library.

**Naming:**
- Classes: `PascalCase` - `UserService`, `JdbiUserStorage`
- Methods: `camelCase` - `getUserById()`, `createMatch()`
- Predicates: `is`/`has`/`can` prefix - `isActive()`, `hasDealbreakers()`
- Constants: `UPPER_SNAKE_CASE` - `MAX_DISTANCE_KM`
- Identifiers: Avoid `_` as standalone name (Java reserves it)

**Types:**
- `record` for immutable data (`ConnectionModels.Like`, `ConnectionModels.Message`, Result records)
- `class` for mutable entities with state (`User`, `Match`)
- `enum` for fixed sets - **nested public static** in `User`/`Match` for model-owned states
- `Optional<T>` for nullable returns from storage
- **Nested types MUST be `public static`** for cross-package access

**State Machines:**
- `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Match`: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`
- Validate state before transitions with `IllegalStateException`

## Error Handling

**Validation:**
- Constructor parameters: `Objects.requireNonNull(param, "param cannot be null")`
- Business rules: `IllegalArgumentException("Cannot like yourself")`
- State transitions: `IllegalStateException("User is not ACTIVE")`

**Storage Layer:** Wrap `SQLException` in `RuntimeException`

**Service Layer:** Return Result records (never throw for business failures)
```java
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
```

**CLI/UI Layer:** Never throw exceptions to users. Log user-friendly messages and continue gracefully.

## Testing Standards

**Test Structure:**
- JUnit 5 with `@Nested` classes for logical grouping
- Test class name: `{ClassName}Test.java`
- Methods: `@DisplayName("Description")` + descriptive method name
- `@Timeout(5)` to catch infinite loops

**Mocking:** Use `TestStorages` (NO Mockito):
```java
var userStorage = new TestStorages.Users();
var interactionStorage = new TestStorages.Interactions();
var commStorage = new TestStorages.Communications();
var analyticsStorage = new TestStorages.Analytics();
var trustSafetyStorage = new TestStorages.TrustSafety();
```

**Utilities:**
- `TestClock.setFixed(...)` / `TestClock.reset()` for deterministic time
- `TestUserFactory` for quick user fixtures

**Coverage:** Minimum 60% line coverage (JaCoCo enforced, excludes `ui/` and `app/cli/`)

## Special Patterns

**Deterministic IDs** (Match, Conversation):
```java
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

**StorageBuilder Pattern** (reconstruct from database):
```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio)
    .birthDate(birthDate)
    .gender(gender)
    .build();
```

**Touch Pattern** (mutable entities):
```java
private void touch() { this.updatedAt = AppClock.now(); }
public void setBio(String bio) { this.bio = bio; touch(); }  // EVERY setter calls touch()
```

**EnumSet Defensive Patterns:**
```java
// Setter - handle null safely
public void setInterestedIn(Set<Gender> interestedIn) {
    this.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
    touch();
}

// Getter - never expose internal reference
public Set<Gender> getInterestedIn() {
    return EnumSetUtil.safeCopy(interestedIn, Gender.class);
}
```

**LoggingSupport Pattern:**
```java
public interface LoggingSupport {
    Logger logger();
    default void logInfo(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isInfoEnabled()) { log.info(message, args); }
    }
}
```

## MVVM UI Architecture (JavaFX)

**BaseController** (lifecycle management):
- All controllers extend `ui.screen.BaseController`
- `addSubscription(Subscription)` - register for cleanup
- `registerOverlay(Node)` - track overlays
- `trackAnimation(Animation)` - track indefinite animations
- Call `cleanup()` before navigating away

**ViewModel Error Propagation:**
```java
// ViewModel side
private ViewModelErrorSink errorHandler;
public void setErrorHandler(ViewModelErrorSink handler) { this.errorHandler = handler; }

// Controller side
viewModel.setErrorHandler(UiFeedbackService::showError);
```

**Navigation Context:**
```java
navigationService.setNavigationContext(payload);
navigationService.navigateTo(ViewType.CHAT);
Object context = navigationService.consumeNavigationContext();
```

**Threading:** Use `Thread.ofVirtual()` for background work, `Platform.runLater()` for UI updates.

## JDBI Layer Conventions

- Use `JdbiTypeCodecs.SqlRowReaders.*` for null-safe ResultSet reads
- Define `ALL_COLUMNS` constant to avoid SQL column drift
- Build domain objects through `StorageBuilder` in mappers

```java
@RegisterRowMapper(Mapper.class)
public interface JdbiUserStorage extends UserStorage {
    String ALL_COLUMNS = "id, name, bio, birth_date, ...";

    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id")
    User findById(@Bind("id") UUID id);

    class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return User.StorageBuilder.create(
                JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                rs.getString("name"),
                JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at")
            ).build();
        }
    }
}
```

## CLI Handler Patterns

**Input Validation Template:**
```java
private void promptNumber(User user) {
    String input = inputReader.readLine("Value: ");
    try {
        int value = Integer.parseInt(input);
        if (value < 0) { logInfo("❌ Must be positive.\n"); return; }
        user.setValue(value);
        logInfo("✅ Updated.\n");
    } catch (NumberFormatException e) {
        logInfo("⚠️  Invalid number format.\n");
    }
}
```

**Common Patterns:**
- Log with emojis: ✅ ❌ ⚠️ 🎉
- Handle all `NumberFormatException` gracefully
- Include default branches in switches for Checkstyle

## Code Quality Checklist

**Before committing:**
1. Run `mvn spotless:apply`
2. Run `mvn verify` (compiles, tests, Checkstyle, PMD, JaCoCo)
3. No framework imports in `core/`
4. All dependencies via constructor injection
5. Nested types are `public static`
6. Services return Result records instead of throwing

## Never Do These

- ❌ Import framework/DB APIs into `core/` domain/service code
- ❌ Use removed names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`)
- ❌ Use removed standalone model enum imports (`core.model.Gender`, `UserState`, `MatchState`, etc.)
- ❌ Throw business-flow exceptions when Result records exist
- ❌ Return mutable internal collections directly
- ❌ Forget `touch()` in mutable entity setters
- ❌ Use `Instant.now()` where `AppClock.now()` is expected

## Known Limitations

- Cross-storage writes not fully transactional
- Undo state is in-memory (lost on restart)
- Email/phone verification simulated (no real sending)
- No caching layer

---
**Last Updated:** 2026-02-18 | **Phase:** 2.4 | **Files:** ~78 main + ~59 test
**Tests:** ~800+ | **Coverage:** 60% minimum (JaCoCo)
