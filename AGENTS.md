<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1 (recheck before write),
# 2) locate affected doc fragment using prioritized search (see below),
# 3) archive replaced text with <!--ARCHIVE:SEQ:agent:scope-->...<!--/ARCHIVE-->,
# 4) apply minimal precise edits (edit only nearest matching fragment),
# 5) append one ChangeStamp line to the file-end changelog and inside the edited fragment (immediately after the edited paragraph or code fence),
# 6) if uncertain to auto-edit, append TODO+ChangeStamp next to nearest heading.
<!--/AGENT-DOCSYNC-->



# AGENTS.md - AI Agent Development Guide

## Development Environment

**Platform:** Windows 11 (10.0, amd64)
**Shell:** PowerShell 7.5.4
**IDE:** VS Code Insiders
**Java:** OpenJDK 25.0.1 (Eclipse Adoptium Temurin) with `--enable-preview`
**Maven:** Apache Maven 3.9.12
**JavaFX:** 25.0.1
**UI Theme:** AtlantaFX 2.1.0 (GitHub Primer-based modern theme)
**Icons:** Ikonli 12.4.0 with Material Design 2 icon pack
**Database:** H2 2.4.240 (embedded, file-based) + JDBI 3.51.0

You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.

### Windows Setup Requirements

**UTF-8 Console Encoding** (REQUIRED for emoji display in CLI):
```powershell
chcp 65001  # Run before starting the app
```

Or set permanently:
1. Run `intl.cpl`
2. Administrative → Change system locale
3. Check "Beta: Use Unicode UTF-8 for worldwide language support"
4. Restart

**Note:** Platform encoding is already UTF-8 by default on this system.

### Available System Tools

These tools are installed on the development machine and can be leveraged:

- **ripgrep** (`rg`) v14.1.0 - Ultra-fast regex search, respects `.gitignore` by default
- **ast-grep** (`sg`) v0.40.0 - AST-based structural code search and refactoring
- **tokei** v12.1.2 - Fast lines-of-code/comments/blanks counter across all languages
- **fd** v10.3.0 - User-friendly alternative to `find` for file system traversal
- **fzf** v0.67.0 - Interactive fuzzy finder for command-line filtering
- **bat** v0.26.0 - `cat` clone with syntax highlighting and Git integration
- **sd** v1.0.0 - Intuitive find & replace (simpler than `sed`)
- **jq** v1.8.1 - Command-line JSON processor
- **yq** v4.48.2 - YAML/TOML/XML processor
- **Semgrep** v1.140.0 - Polyglot SAST and logic checker

**Usage Tip:** Prefer `rg` over `grep` and `fd` over `find` for faster searches.

## Essential Commands

```bash
# Build & Run
mvn compile                          # Compile source
mvn exec:java                        # Run CLI app (may have input buffering)
mvn javafx:run                       # Run JavaFX GUI app
mvn package                          # Build fat JAR (creates target/dating-app-1.0.0-shaded.jar)
java -jar target/dating-app-1.0.0-shaded.jar  # Run from JAR (RECOMMENDED - better terminal support)

# Testing
mvn test                             # All tests
mvn test -Dtest=CandidateFinderTest                    # Single test class
mvn test -Dtest=CandidateFinderTest#excludesSelf      # Single test method

# Code Quality
mvn spotless:apply                   # Format code (REQUIRED before commit)
mvn spotless:check                   # Check formatting
mvn checkstyle:check                 # Run Checkstyle
mvn pmd:check                        # Run PMD analysis
mvn verify                           # Full build + all quality checks

# Database Management (H2)
rm ./data/dating.mv.db               # Reset database (delete all data)
mvn clean                            # Clean build artifacts
```

## Quick Start for New Agents

**First time working on this codebase?**
1. Run `mvn verify` to ensure everything builds and tests pass
2. Explore `src/main/java/datingapp/core/` for domain models
3. Use `mvn javafx:run` to launch the GUI, or `mvn exec:java` for CLI
4. Run `mvn spotless:apply` before any commit

**Key things to know:**
- Domain models are consolidated (e.g., `UserInteractions` contains Like/Block/Report records)
- Storage interfaces are in `core/storage/`, JDBI implementations in `storage/jdbi/`
- Initialize app via `AppBootstrap.initialize()` → returns `ServiceRegistry`
- Access shared session via `AppSession.getInstance()`
- Tests use centralized `TestStorages` utility (NO Mockito)
- Services return Result records instead of throwing exceptions

## Architecture Rules

**Three-Layer Clean Architecture:**

**Layer 1: Core Domain** (`core/`)
- Pure Java business logic - **ZERO** framework/database imports allowed
- Domain models: `User`, `Match`, `Messaging`, `UserInteractions`, `Achievement`, `Preferences`, `Dealbreakers`, `Social`, `Stats`, `SwipeSession`, `PacePreferences`, `DailyPick`
- Standalone enums: `Gender`, `UserState`, `VerificationMethod`
- Services (14 total): `MatchingService`, `MessagingService`, `CandidateFinder`, `MatchQualityService`, `DailyService`, `AchievementService`, `TrustSafetyService`, `StatsService`, `ProfilePreviewService`, `ProfileCompletionService`, `SessionService`, `UndoService`, `RelationshipTransitionService`, `ValidationService`
- Storage interfaces (9 total): `core/storage/{UserStorage,MatchStorage,LikeStorage,BlockStorage,MessagingStorage,SocialStorage,StatsStorage,ReportStorage,SwipeSessionStorage}.java`
- Bootstrap: `AppBootstrap` (singleton init), `AppSession` (unified session), `ServiceRegistry` (DI container)
- Configuration: `AppConfig` (40+ immutable parameters)

**Layer 2: Storage** (`storage/`)
- JDBI 3 + H2 database implementations
- `jdbi/` package: JDBI storage interfaces (9: `JdbiUserStorage`, `JdbiMatchStorage`, `JdbiLikeStorage`, `JdbiBlockStorage`, `JdbiMessagingStorage`, `JdbiSocialStorage`, `JdbiStatsStorage`, `JdbiReportStorage`, `JdbiSwipeSessionStorage`)
- `jdbi/` helpers: `JdbiUserStorageAdapter` (wraps JdbiUserStorage), `UserBindingHelper` (User→SQL binding), `EnumSetArgumentFactory`, `EnumSetColumnMapper`
- `mapper/` package: `MapperHelper` (null-safe ResultSet helpers)
- `DatabaseManager` (connection pooling, schema auto-creation)
- `StorageException` (wraps SQLException as RuntimeException)

**Layer 3A: CLI** (`app/cli/`)
- Entry point: `Main.java` (root package - console app with interactive menu)
- Console handlers with lazy initialization via `HandlerFactory`
- 8 handlers: `MatchingHandler`, `ProfileHandler`, `SafetyHandler`, `StatsHandler`, `ProfileNotesHandler`, `LikerBrowserHandler`, `MessagingHandler`, `RelationshipHandler`
- Utilities: `InputReader`, `EnumMenu`, `CliUtilities`, `CliConstants`

**Layer 3B: JavaFX UI** (`ui/`)
- MVVM architecture with AtlantaFX theme
- Entry point: `DatingApp.java` (JavaFX Application)
- Controllers (11 total): extend `BaseController` for lifecycle management; includes `AchievementPopupController`, `MatchPopupController` for dialogs
- ViewModels (8 total): observable properties + `ErrorHandler` pattern
- Navigation: `NavigationService` (view routing with context passing), `ViewModelFactory`
- UI Utilities: `Toast` (notifications), `UiComponents` (loading overlays), `ImageCache`, `UiAnimations`, `UiHelpers`, `UiServices`, `ConfettiAnimation`

**Critical Rules:**
- Storage interfaces defined in `core/storage/`, implemented in `storage/jdbi/`
- Core services depend only on interfaces, never implementations
- Constructor injection only - all dependencies via constructors
- Initialize once via `AppBootstrap.initialize()` → returns fully wired `ServiceRegistry`
- Access shared session via `AppSession.getInstance()`

## Bootstrap & Initialization Pattern

**AppBootstrap** (singleton, thread-safe):
```java
// Idempotent initialization - safe to call multiple times
ServiceRegistry services = AppBootstrap.initialize();

// With custom config
ServiceRegistry services = AppBootstrap.initialize(customConfig);

// Reset for testing
AppBootstrap.reset();
```
- Single responsibility: initialize DatabaseManager and ServiceRegistry
- Returns fully wired ServiceRegistry with all 13 services
- Thread-safe singleton pattern

**AppSession** (singleton, thread-safe):
```java
// Get singleton instance
AppSession session = AppSession.getInstance();

// Session management
session.setCurrentUser(user);
boolean loggedIn = session.isLoggedIn();
boolean active = session.isActive();  // Checks ACTIVE state
session.logout();

// Listener support for JavaFX
session.addListener(() -> updateUI());
```
- Unified user session for both CLI and JavaFX
- Replaces separate CliUtilities.UserSession and ViewModelFactory.UISession
- Observable pattern for UI binding

**HandlerFactory** (CLI handlers with lazy initialization):
```java
// Constructor takes ServiceRegistry, AppSession, InputReader
HandlerFactory handlers = new HandlerFactory(services, session, inputReader);

// Lazy initialization - created on first call and cached
handlers.matching().runMatchingLoop();
handlers.profile().viewProfile();
handlers.stats().showStats();
```
- 8 factory methods for handlers
- Each handler created once and cached
- Handler dependencies injected via nested `Dependencies` record with compact constructor validation

**Example full initialization:**
```java
public class Main {
    public static void main(String[] args) {
        ServiceRegistry services = AppBootstrap.initialize();
        AppSession session = AppSession.getInstance();
        InputReader inputReader = new InputReader(System.in);

        HandlerFactory handlers = new HandlerFactory(services, session, inputReader);
        handlers.matching().runMatchingLoop();
    }
}
```

## Result Pattern (Services Never Throw)

Services return Result records instead of throwing exceptions. Example from `MessagingService`:

```java
public static record SendResult(
    boolean success,
    Message message,
    String errorMessage,
    ErrorCode errorCode
) {
    public static SendResult success(Message m) {
        return new SendResult(true, m, null, null);
    }
    public static SendResult failure(String err, ErrorCode code) {
        return new SendResult(false, null, err, code);
    }
}

// Usage in service
public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {
    // Validation
    if (match == null) {
        return SendResult.failure("Match not found", ErrorCode.MATCH_NOT_FOUND);
    }
    if (!match.isActive()) {
        return SendResult.failure("Match not active", ErrorCode.MATCH_NOT_ACTIVE);
    }
    // Create and save message
    Message message = Message.create(conversationId, senderId, content);
    messagingStorage.saveMessage(message);
    return SendResult.success(message);
}
```

**Services with Result pattern:**
- `MessagingService.SendResult`
- `MatchingService` returns `Optional<Match>`
- `ValidationService.ValidationResult`
- `ProfileCompletionService` returns completion %
- `MatchQualityService.MatchQuality` record
- `UndoService` returns boolean
- `SessionService.SessionResult`
- `TrustSafetyService` returns boolean or User

## MVVM UI Architecture (JavaFX)

**ErrorHandler Pattern** (ViewModel → Controller communication):

```java
// ErrorHandler.java - functional interface
@FunctionalInterface
public interface ErrorHandler {
    void onError(String message);
}

// In ViewModel - add field and setter
private ErrorHandler errorHandler;
public void setErrorHandler(ErrorHandler handler) {
    this.errorHandler = handler;
}

// In catch blocks - notify via handler
private void notifyError(String userMessage, Exception e) {
    if (errorHandler != null) {
        Platform.runLater(() -> errorHandler.onError(userMessage + ": " + e.getMessage()));
    }
}

// In Controller initialize() - wire up to toast
viewModel.setErrorHandler(msg -> Toast.showError(msg));
```

**BaseController** (lifecycle management):
- All controllers extend `BaseController`
- Manages subscription cleanup (prevents memory leaks)
- Methods: `addSubscription()`, `cleanup()`, `registerOverlay()`
- Call `cleanup()` before navigating away

**NavigationService** (context passing):
```java
// Before navigating - set context
navigationService.setNavigationContext(matchedUserId);
navigationService.navigateTo(ViewType.CHAT);

// In target controller initialize() - consume context
Object context = navigationService.consumeNavigationContext();
if (context instanceof UUID userId) {
    viewModel.selectConversationWithUser(userId);
}
```

**Loading Overlays** (BaseController pattern):
```java
// In controller initialize()
StackPane loadingOverlay = UiComponents.createLoadingOverlay();
registerOverlay(loadingOverlay);  // BaseController tracks for cleanup
loadingOverlay.visibleProperty().bind(viewModel.loadingProperty());
loadingOverlay.managedProperty().bind(viewModel.loadingProperty());
```

## Code Style

**Formatting:** Palantir Java Format v2.39.0 (4-space indentation). Auto-apply with `mvn spotless:apply` before every commit.

**Imports:** No star imports (`import java.util.*`). Import ordering: static first, then third-party, then standard library.
**FXML Controllers:** Use `@SuppressWarnings("unused")` at class level when members/handlers are only referenced from FXML to silence false-positive unused warnings.

**Naming Conventions:**
- Classes: PascalCase - `UserService`, `JdbiUserStorage`, `ProfileHandler`
- Methods: camelCase - `getUserById()`, `createMatch()`, `isComplete()`
- Predicates: `is`/`has`/`can` prefix - `isActive()`, `hasDealbreakers()`, `canLike()`
- Constants: UPPER_SNAKE_CASE - `MAX_DISTANCE_KM`, `DEFAULT_TIMEOUT`
- Identifiers: Avoid `_` as a standalone name; Java reserves it (use `event`, `ignored`, etc.).

**Types:**
- Use `record` for immutable data (e.g., `UserInteractions.Like`, `UserInteractions.Block`, `UserInteractions.Report`, `MatchQuality`, Result records)
- Use `class` for mutable entities with state machines (`User`, `Match`)
- Use `enum` for fixed sets (`Preferences.Interest`, `Achievement`, `User.Gender`)
- Use `Optional<T>` for nullable returns from storage
- **Nested types MUST be `public static`** (compiler requirement for cross-package access)

**State Machines:**
- `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Match`: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`
- Validate state before transitions with `IllegalStateException`

## Error Handling

**Validation:**
- Constructor parameters: `Objects.requireNonNull(param, "param cannot be null")`
- Business rules: `IllegalArgumentException("Cannot like yourself")`
- State transitions: `IllegalStateException("User is not ACTIVE")`

**Storage Layer:** Wrap `SQLException` in `StorageException` (RuntimeException)

**Service Layer:** Return Result records (e.g., `SendResult`, `ValidationResult`)

**CLI/UI Layer:** Never throw exceptions to users. Log user-friendly messages and continue gracefully.

## Testing Standards

**Test Structure:**
- Use JUnit 5 with `@Nested` classes for logical grouping
- Test class name: `{ClassName}Test.java`
- Test methods: `@DisplayName("Description")` + descriptive method name
- Use `@SuppressWarnings("unused")` on test classes with `@Nested`
- Use `@Timeout(5)` to catch infinite loops

**Mocking with TestStorages** (centralized in-memory implementations):

```java
// TestStorages.java - centralized mock factory
package datingapp.core.testutil;

public class TestStorages {
    public static class Users implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();
        @Override public void save(User user) { users.put(user.getId(), user); }
        @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(users.get(id)); }
        public void clear() { users.clear(); }
        public int size() { return users.size(); }
    }

    public static class Matches implements MatchStorage { /* similar */ }
    public static class Likes implements LikeStorage { /* similar */ }
    public static class Blocks implements BlockStorage { /* similar */ }
}

// Usage in test
@BeforeEach
void setUp() {
    var userStorage = new TestStorages.Users();
    var likeStorage = new TestStorages.Likes();
    var matchStorage = new TestStorages.Matches();
    matchingService = new MatchingService(likeStorage, matchStorage);
}
```

**NO Mockito** - Use `TestStorages` utility class for all mocking. Keep in-memory storages aligned with core interfaces when new methods are added.

**Integration Tests:** Use real H2 database with unique test DB name per test class. Create required user rows before inserting records with user_id foreign keys.

**Coverage:** Minimum 60% line coverage (enforced by JaCoCo, excludes ui/ and cli/)

**Test Coverage Rules:**
- **Happy path**: Primary use cases
- **Sad path**: Invalid inputs, nulls, empty sets
- **Edge cases**: Boundary values (0, 1, MAX), enum all values
- **State transitions**: All valid and invalid transitions
- **Cross-boundary**: Integration points between services

## Special Patterns

**Deterministic IDs** (two-user entities):
<!--ARCHIVE:46:agent:codex:messaging-conversation-id-->
```java
// Match.java, Conversation.java - same ID regardless of parameter order
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```
<!--/ARCHIVE-->
```java
// Match.java, Conversation.java - same ID regardless of parameter order
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```
Conversation IDs are strings; treat `conversationId` as `String` in UI/viewmodel code when calling messaging APIs.
46|2026-02-05 04:02:41|agent:codex|scope:messaging-conversation-id|Clarify conversation ID type for UI/viewmodel usage|AGENTS.md

**StorageBuilder Pattern** (reconstruct from database):
```java
// User.java - bypass validation when loading from database
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio)
    .birthDate(birthDate)
    .interestedIn(interestedIn)  // Handles null safely
    .state(state)
    .build();
```

**Factory Methods** (creating new entities):
```java
public static Message create(String conversationId, UUID senderId, String content) {
    return new Message(UUID.randomUUID(), conversationId, senderId, content, Instant.now());
}
```

**EnumSet Defensive Patterns**:
```java
// Setter - handle null safely
public void setInterestedIn(Set<Gender> interestedIn) {
    this.interestedIn = interestedIn != null ?
        EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
    touch();
}

// Getter - never expose internal reference
public Set<Gender> getInterestedIn() {
    return interestedIn.isEmpty() ? EnumSet.noneOf(Gender.class) : EnumSet.copyOf(interestedIn);
}
```

**Touch Pattern** (mutable entities):
```java
private Instant updatedAt;
private void touch() { this.updatedAt = Instant.now(); }
public void setBio(String bio) {
    this.bio = bio;
    touch();  // EVERY setter calls touch()
}
```

**Daily Pick Exclusions:** Use `LikeStorage.getLikedOrPassedUserIds()` to avoid resurfacing users already liked or passed.

**Candidate Distance:** If either user lacks a location (0,0), skip distance filtering and sort unknown distances last to avoid empty queues.

## Handler Dependencies Pattern (CLI)

```java
public class MatchingHandler {
    // Nested Dependencies record with validation
    public record Dependencies(
        CandidateFinder candidateFinder,
        MatchingService matchingService,
        // ... 10 more dependencies
        AppSession session,
        InputReader inputReader
    ) {
        public Dependencies {  // Compact constructor - validates all fields
            Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
            Objects.requireNonNull(matchingService, "matchingService cannot be null");
            // ... validate all fields
        }
    }

    // Constructor takes Dependencies record
    public MatchingHandler(Dependencies dependencies) {
        this.candidateFinder = dependencies.candidateFinder();
        this.matchingService = dependencies.matchingService();
        // ... extract all dependencies
    }
}
```

## File Locations

**Core Domain** (`src/main/java/datingapp/core/`)
- Domain models (12 total): `User.java`, `Match.java`, `Messaging.java`, `Social.java`, `Stats.java`, `UserInteractions.java`, `Preferences.java`, `Dealbreakers.java`, `Achievement.java`, `SwipeSession.java`, `PacePreferences.java`, `DailyPick.java`
- Standalone enums: `Gender.java`, `UserState.java`, `VerificationMethod.java`
- Storage interfaces (9 total): `storage/{UserStorage,MatchStorage,LikeStorage,BlockStorage,MessagingStorage,SocialStorage,StatsStorage,ReportStorage,SwipeSessionStorage}.java`
- Services (14 total): `MatchingService.java`, `MessagingService.java`, `CandidateFinder.java`, `MatchQualityService.java`, `DailyService.java`, `AchievementService.java`, `TrustSafetyService.java`, `StatsService.java`, `ProfilePreviewService.java`, `ProfileCompletionService.java`, `SessionService.java`, `UndoService.java`, `RelationshipTransitionService.java`, `ValidationService.java`
- Bootstrap: `AppBootstrap.java`, `AppSession.java`, `ServiceRegistry.java`
- Configuration: `AppConfig.java`

**Storage Layer** (`src/main/java/datingapp/storage/`)
- JDBI implementations (9 storage interfaces): `jdbi/{JdbiUserStorage,JdbiMatchStorage,JdbiLikeStorage,JdbiBlockStorage,JdbiMessagingStorage,JdbiSocialStorage,JdbiStatsStorage,JdbiReportStorage,JdbiSwipeSessionStorage}.java`
- JDBI helpers: `jdbi/{JdbiUserStorageAdapter,UserBindingHelper,EnumSetArgumentFactory,EnumSetColumnMapper}.java`
- SQL helpers: `mapper/MapperHelper.java`, `DatabaseManager.java`, `StorageException.java`

**CLI Layer**
- Entry point: `src/main/java/datingapp/Main.java` (console app main method)
- Handler factory: `app/cli/HandlerFactory.java`
- Handlers (8 total): `app/cli/{MatchingHandler,ProfileHandler,SafetyHandler,StatsHandler,ProfileNotesHandler,LikerBrowserHandler,MessagingHandler,RelationshipHandler}.java`
- Utilities: `app/cli/{InputReader,EnumMenu,CliUtilities,CliConstants}.java`

**JavaFX UI** (`src/main/java/datingapp/ui/`)
- Entry: `DatingApp.java`, `NavigationService.java`, `ViewModelFactory.java`
- Controllers (11 total): `controller/{BaseController,LoginController,ProfileController,DashboardController,MatchingController,MatchesController,ChatController,PreferencesController,StatsController,AchievementPopupController,MatchPopupController}.java`
- ViewModels (8 total + ErrorHandler): `viewmodel/{LoginViewModel,ProfileViewModel,DashboardViewModel,MatchingViewModel,MatchesViewModel,ChatViewModel,PreferencesViewModel,StatsViewModel,ErrorHandler}.java`
- UI Utilities (6 files): `util/{Toast,UiServices,UiHelpers,UiAnimations,ImageCache,ConfettiAnimation}.java`
- UI Components: `component/UiComponents.java`

**Testing** (`src/test/java/datingapp/`)
- Test utilities: `core/testutil/TestStorages.java` (in-memory storage mocks), `core/testutil/TestUserFactory.java` (user creation helpers)
- Core tests (30+ test classes): `core/*Test.java` (service, domain, and regression tests)
- CLI tests: `app/cli/*Test.java`
- UI tests: `ui/*Test.java` (CSS validation, etc.)

## Critical Rules

❌ NEVER:
- Import framework/database classes in `core/` package
- Skip constructor validation with `Objects.requireNonNull()`
- Throw exceptions in services (return Result records)
- Throw exceptions in CLI/UI handlers (log user-friendly messages)
- Use Mockito or external mocking libraries (use `TestStorages`)
- Commit without running `mvn spotless:apply`
- Return direct collection references (defensive copy required)
- Use `new ArrayList<>()` with empty `EnumSet.copyOf()` - use `EnumSet.noneOf()` for empty sets
- Mix business logic in storage classes - only mapping to/from database
- Hardcode validation thresholds - use `AppConfig.defaults()`
- Forget `static` on nested types (records, enums, classes)

✅ ALWAYS:
- Use constructor injection for dependencies
- Initialize via `AppBootstrap.initialize()` and `AppSession.getInstance()`
- Return Result records from services instead of throwing
- Add `@DisplayName` to test methods
- Update `updatedAt` timestamps on entity changes (call `touch()`)
- Run `mvn spotless:apply` before committing
- Write unit tests with `TestStorages` in-memory mocks
- Validate inputs at boundaries
- Handle `NumberFormatException` gracefully in CLI - log user-friendly message
- Use try-with-resources for database connections
- Validate SQL parameters before execution
- Make nested types `public static` for cross-package access

## Design Decision Framework

**When to Create a New Service:**
- Service has >10 public methods OR >3 distinct responsibilities
- Need to mock independently in tests
- Different lifecycle/bounds than existing services
- Will have multiple implementations (strategy pattern)

**When to Add to Existing Service:**
- Tightly coupled to existing operations
- Shares same storage dependencies
- Logical grouping with current methods
- <10 total methods after addition

**Service vs Utility Class:**
- **Service**: Has dependencies, stateful operations, business workflows (e.g., `MatchingService`, `ProfilePreviewService`)
- **Utility**: Stateless, static methods, pure functions (e.g., `GeoUtils`, `InterestMatcher`, `CandidateFinder`)

**Storage Schema Evolution:**
- Use `addColumnIfNotExists()` pattern in storage constructor
- Keep backward compatibility - never drop columns
- Add new fields with NULL constraints
- Map old defaults in `StorageBuilder` for legacy rows

## Database Patterns (JDBI + H2)

**H2 Database Configuration:**
- **Location:** `./data/dating.mv.db` (file-based, auto-created on first run)
- **JDBC URL:** `jdbc:h2:./data/dating`
- **Username:** `sa`
- **Password:** `changeit` (hardcoded for development; use env var `DATING_APP_DB_PASSWORD` in production)
- **Mode:** Embedded (no separate server process)
- **Auto-Server:** Disabled during tests to prevent locking issues

**JDBI Storage Implementation:**
```java
@RegisterRowMapper(JdbiUserStorage.Mapper.class)
public interface JdbiUserStorage extends UserStorage {
    String ALL_COLUMNS = "id, name, bio, birth_date, ...";  // Avoid copy-paste errors

    @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
    void save(@BindBean UserBindingHelper helper);

    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id")
    User findById(@Bind("id") UUID id);

    class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return User.StorageBuilder.create(
                MapperHelper.readUuid(rs, "id"),      // Null-safe helpers
                rs.getString("name"),
                MapperHelper.readInstant(rs, "created_at")
            ).birthDate(MapperHelper.readLocalDate(rs, "birth_date")).build();
        }
    }
}
```

**Complex Type Mapping:**
- **Enums**: Store as VARCHAR, convert with `enum.name()` / `Enum.valueOf()`
- **EnumSets**: Store as CSV `"A,B,C"`, use custom `EnumSetArgumentFactory` + `EnumSetColumnMapper`
- **Lists**: Use delimiter not in content (pipe `|` for URLs)
- **UUIDs**: Use `stmt.setObject()` and `MapperHelper.readUuid(rs, "column")`
- **Instants**: Use `MapperHelper.readInstant(rs, "column")`
- **LocalDates**: Use `MapperHelper.readLocalDate(rs, "column")`

**Database Query Optimization:**
- Use `WHERE` clauses to filter early
- Avoid `SELECT *` - list needed columns (use `ALL_COLUMNS` constant)
- Index frequently queried columns (auto in H2)
- Keep additional index definitions grouped in `DatabaseManager.initializeSchema()` for consistency
- Use `JOIN` instead of multiple queries

## CLI Handler Patterns

**Input Validation Template:**
```java
private void promptNumber(User user) {
    String input = inputReader.readLine("Value: ");
    try {
        int value = Integer.parseInt(input);
        // Validate business rules
        if (value < 0) {
            logger.info("❌ Must be positive.\n");
            return;
        }
        user.setValue(value);
        logger.info("✅ Updated.\n");
    } catch (NumberFormatException e) {
        logger.info("⚠️  Invalid number format.\n");
    }
}
```

**Menu Handling:**
```java
public void handleMenu() {
    boolean running = true;
    while (running) {
        displayMenu();
        String choice = inputReader.readLine(PROMPT);
        switch (choice) {
            case "0" -> running = false;
            case "1" -> handleOption1();
            default -> logger.info(CliConstants.INVALID_SELECTION);
        }
    }
}
```

**Common UI Patterns:**
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
- Expose birth date inputs in profile editors so completion scoring can reach 100%
- Surface missing completion details in profile headers to guide users to 100%
- Display age ranges with explicit separators to avoid concatenated labels
- Provide matches tabs for received and sent likes with clear actions (like back, pass, withdraw)
- Include default branches in UI switches to satisfy Checkstyle
<!--ARCHIVE:45:agent:codex:ui-loading-timing-->
- Keep loading overlays responsive by toggling loading state on the FX thread before background work and clearing it in finally
<!--/ARCHIVE-->
- Keep loading overlays responsive by binding overlays before triggering loads, setting loading on the FX thread before background work, and clearing it on the FX thread after UI updates complete
45|2026-02-05 03:54:37|agent:codex|scope:ui-loading-timing|Refine loading overlay timing guidance for FX-thread updates and initialization|src/main/java/datingapp/ui/controller/ChatController.java;src/main/java/datingapp/ui/controller/DashboardController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/viewmodel/ChatViewModel.java;src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;AGENTS.md
- Guard stylesheet resource lookups and log missing stylesheets before applying them

## Logging Standards

**Level Usage:**
- **DEBUG**: Development diagnostics (disable in prod)
- **INFO**: User-facing messages, state changes
- **WARN**: Recoverable issues (missing optional fields)
- **ERROR**: Unrecoverable errors (shouldn't happen)
- Guard log statements with `logger.isXEnabled()` (or helper) before formatting.

**Message Format:**
- User messages: `"✅ Profile saved!"` (no stack traces)
- Error messages: `"⚠️  Invalid date format."` (helpful hint)
- State changes: `"Profile activated."` (clear verb)
- Progress: `"Processing 15 of 50..."` (if batch operation)

## Complex Business Logic

**Scoring Algorithm Pattern:**
```java
// 1. Extract components
double distanceScore = calculateDistanceScore(...);
double ageScore = calculateAgeScore(...);

// 2. Weighted average (configurable via AppConfig)
double weighted = distanceScore * config.distanceWeight()
               + ageScore * config.ageWeight();

// 3. Normalize to 0-100 range
int score = (int) Math.round(weighted * 100);
```

**Multi-Step Workflow with Result Pattern:**
```java
public SendResult complexOperation(Input input) {
    // 1. Validate
    if (!input.isValid()) {
        return SendResult.failure("Invalid input", ErrorCode.INVALID_INPUT);
    }

    // 2. Load data
    User user = userStorage.findById(input.userId()).orElse(null);
    if (user == null) {
        return SendResult.failure("User not found", ErrorCode.USER_NOT_FOUND);
    }

    // 3. Process
    Processed processed = processData(user, input);

    // 4. Save
    storage.save(processed);

    // 5. Side effects (notifications, stats)
    notifyUser(user);
    updateStats(processed);

    return SendResult.success(processed);
}
```

## Testing Anti-Patterns

**❌ Bad Test Design:**
```java
// Tests implementation details
@Test
void testMapUserMethod() { ... }  // Don't test private methods

// No description
@Test
void test1() { ... }

// Magic numbers
User user = createUser();  // What state is this?

// Using Mockito
@Mock private UserStorage userStorage;  // Use TestStorages instead
```

**✅ Good Test Design:**
```java
// Tests public behavior
@Nested
@DisplayName("Profile Activation")
class ActivationTests {
    @Test
    @DisplayName("Activates when profile complete")
    void activatesWhenComplete() { ... }

    @Test
    @DisplayName("Remains incomplete when missing required fields")
    void remainsIncompleteWhenMissingFields() { ... }
}

// Descriptive factory methods
private User createCompleteUser() { ... }
private User createIncompleteUserWithoutBio() { ... }
private User createActiveUserWithInterests() { ... }

// Using TestStorages
@BeforeEach
void setUp() {
    userStorage = new TestStorages.Users();
    matchStorage = new TestStorages.Matches();
    service = new MatchingService(userStorage, matchStorage);
}
```

## Performance Guidelines

**When to Optimize:**
- N+1 query problem in storage
- Operations called in tight loops (>1000 iterations)
- Large collections (>1000 items) repeatedly filtered
- Profiled bottleneck (never premature optimize)

**Collection Choices:**
- **EnumSet**: When elements are enums, need uniqueness (most efficient)
- **HashSet**: General-purpose unique elements
- **ArrayList**: Ordered, index access needed
- **HashMap**: Key-value lookups
- **Stream**: When functional style improves readability, NOT for raw speed

## Configuration Management (AppConfig)

**AppConfig** is a record with 40+ parameters organized in groups:

**Limits:**
- `dailyLikeLimit(100)` - Max likes per day (-1 = unlimited)
- `maxSwipesPerSession(500)` - Anti-bot protection
- `maxPhotos(6)` - Max photos per user
- `maxInterests(10)` - Max interests per user

**Validation:**
- `minAge(18)`, `maxAge(120)` - Valid age range
- `minHeightCm(50)`, `maxHeightCm(300)` - Valid height range
- `maxBioLength(500)` - Max bio characters

**Algorithm Thresholds:**
- `nearbyDistanceKm(5)` - Distance considered "nearby"
- `similarAgeDiff(2)` - Age difference considered "similar"
- `minSharedInterests(3)` - Min shared interests for "many"
- `paceCompatibilityThreshold(50)` - Min pace score for compatibility

**Match Quality Weights:**
- `distanceWeight(0.15)` - Distance component (15%)
- `ageWeight(0.10)` - Age component (10%)
- `interestWeight(0.25)` - Shared interests (25%)
- `lifestyleWeight(0.25)` - Lifestyle compatibility (25%)
- `paceWeight(0.15)` - Communication pace (15%)
- `responseWeight(0.10)` - Response time (10%)

**Usage:**
```java
// Access singleton config
private static final AppConfig CONFIG = AppConfig.defaults();

// Use in validation
if (age < CONFIG.minAge() || age > CONFIG.maxAge()) {
    throw new IllegalArgumentException("Age must be between " + CONFIG.minAge() + " and " + CONFIG.maxAge());
}

// Custom config for testing
AppConfig testConfig = AppConfig.builder()
    .dailyLikeLimit(50)
    .minAge(21)
    .build();
```

## Code Quality Checklist

Before committing changes, verify:

**Architecture:**
- [ ] No framework imports in core/
- [ ] Interfaces in core/storage/, implementations in storage/jdbi/
- [ ] All dependencies via constructor injection
- [ ] Service uses only storage interfaces
- [ ] Nested types are `public static`

**Implementation:**
- [ ] All constructors validate with `Objects.requireNonNull()`
- [ ] State transitions validated with `IllegalStateException`
- [ ] Collections defensively copied on return
- [ ] `updatedAt` timestamps updated on changes (call `touch()`)
- [ ] Storage exceptions wrapped in `StorageException`
- [ ] Services return Result records instead of throwing

**Testing:**
- [ ] Unit tests for new business logic
- [ ] Using `TestStorages` (NO Mockito)
- [ ] `@DisplayName` on all tests
- [ ] `@Nested` for logical grouping
- [ ] Edge cases covered
- [ ] 60%+ line coverage

**CLI/UI:**
- [ ] No exceptions thrown to user
- [ ] User-friendly error messages
- [ ] Input validation with try/catch
- [ ] Confirmation messages logged
- [ ] Used `CliConstants` for display text
- [ ] ViewModel errors via `ErrorHandler` pattern

**Database:**
- [ ] JDBI annotations (`@SqlQuery`, `@SqlUpdate`)
- [ ] Using `MapperHelper` for null-safe reads
- [ ] Try-with-resources for JDBI handles
- [ ] Schema migration in `DatabaseManager.initializeSchema()`
- [ ] MERGE for upsert operations

**Final:**
- [ ] `mvn spotless:apply` run
- [ ] `mvn test` passes
- [ ] `mvn verify` passes (all quality checks)

## Known Limitations

**Do NOT Fix These (Phase Constraints):**
- No transaction support in H2 (atomic multi-table operations not possible)
- In-memory undo state lost on restart (UndoService)
- Email/phone verification simulated (no real sending)
- No caching layer (repeated database queries)
- MatchQualityService returns static scores (simplified algorithm)

**If You Must Work Around:**
- Use application-level transactions (try/catch/rollback in service layer)
- Document in-memory-only state clearly
- Use feature flags for incomplete features
- Add TODO comments for future improvements

**Last Updated:** 2026-02-05
**Phase:** 2.2 (file-consolidation complete: 139 Java files)
**Repository:** https://github.com/TomShtern/Date_Program.git
**Total Java Files:** 139 in `src/` (102 main + 37 test files)
**Lines of Code:** ~27K total, ~16K production code
**Test Coverage:** 60% minimum (JaCoCo enforced, excludes ui/ and cli/)




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
2|2026-01-20 00:00:00|agent:github_copilot|ui-utils|Consolidate UI animations; archive unused helpers|src/main/java/datingapp/ui/util/UiAnimations.java;src/main/java/datingapp/ui/util/ValidationHelper.java;src/main/java/datingapp/ui/controller/ProfileController.java;docs/archived-utils/*
3|2026-01-20 00:00:00|agent:github_copilot|core-sweep|Simplify daily limits, picks, and pending liker filtering|src/main/java/datingapp/core/DailyLimitService.java;src/main/java/datingapp/core/DailyPickService.java;src/main/java/datingapp/core/LikerBrowserService.java
4|2026-01-23 23:46:26|agent:github_copilot|core-plan|Add core consolidation plan doc|docs/core-consolidation-plan.md
5|2026-01-24 14:45:00|agent:github_copilot|core-consolidation|Nest TransitionValidationException; move MatchQualityConfig/InterestMatcher into MatchQualityService; move GeoUtils into CandidateFinder and drop CandidateFinderService; update usages/tests|src/main/java/datingapp/core/RelationshipTransitionService.java;src/main/java/datingapp/core/MatchQualityService.java;src/main/java/datingapp/core/CandidateFinder.java;src/main/java/datingapp/core/DailyPickService.java;src/main/java/datingapp/core/ServiceRegistry.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/cli/ProfileHandler.java;src/main/java/datingapp/cli/RelationshipHandler.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;src/main/java/datingapp/core/MatchQualityConfigTest.java;src/test/java/datingapp/core/InterestMatcherTest.java;src/test/java/datingapp/core/GeoUtilsTest.java;docs/core-consolidation-plan.md
6|2026-01-24 15:10:00|agent:github_copilot|core-achievement|Nest UserAchievement into Achievement; update storage, CLI/UI, tests; remove old file|src/main/java/datingapp/core/Achievement.java;src/main/java/datingapp/core/AchievementService.java;src/main/java/datingapp/core/UserAchievementStorage.java;src/main/java/datingapp/storage/H2UserAchievementStorage.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/cli/ProfileHandler.java;src/main/java/datingapp/cli/StatsHandler.java;src/main/java/datingapp/ui/viewmodel/StatsViewModel.java;src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/test/java/datingapp/core/UserAchievementTest.java;src/test/java/datingapp/core/AchievementServiceTest.java;docs/core-consolidation-plan.md
7|2026-01-24 15:45:00|agent:github_copilot|core-social|Consolidate FriendRequest + Notification into Social; update storage/CLI/tests; remove old files|src/main/java/datingapp/core/Social.java;src/main/java/datingapp/core/FriendRequestStorage.java;src/main/java/datingapp/core/NotificationStorage.java;src/main/java/datingapp/core/RelationshipTransitionService.java;src/main/java/datingapp/cli/RelationshipHandler.java;src/main/java/datingapp/storage/H2FriendRequestStorage.java;src/main/java/datingapp/storage/H2NotificationStorage.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;docs/core-consolidation-plan.md
8|2026-01-24 16:05:00|agent:github_copilot|core-stats|Consolidate UserStats + PlatformStats into Stats; update storage/service/CLI/tests; remove old files|src/main/java/datingapp/core/Stats.java;src/main/java/datingapp/core/UserStatsStorage.java;src/main/java/datingapp/core/PlatformStatsStorage.java;src/main/java/datingapp/core/StatsService.java;src/main/java/datingapp/cli/StatsHandler.java;src/main/java/datingapp/storage/H2UserStatsStorage.java;src/main/java/datingapp/storage/H2PlatformStatsStorage.java;src/main/java/datingapp/core/UserStatsTest.java;src/test/java/datingapp/core/PlatformStatsTest.java;docs/core-consolidation-plan.md
9|2026-01-24 16:20:00|agent:github_copilot|core-interactions|Consolidate Like + Block + Report into UserInteractions; update storage/services/CLI/UI/tests; remove old files|src/main/java/datingapp/core/UserInteractions.java;src/main/java/datingapp/core/LikeStorage.java;src/main/java/datingapp/core/BlockStorage.java;src/main/java/datingapp/core/ReportStorage.java;src/main/java/datingapp/core/StatsService.java;src/main/java/datingapp/core/MatchQualityService.java;src/main/java/datingapp/core/MatchingService.java;src/main/java/datingapp/core/SessionService.java;src/main/java/datingapp/core/SwipeSession.java;src/main/java/datingapp/core/UndoService.java;src/main/java/datingapp/core/AchievementService.java;src/main/java/datingapp/core/ReportService.java;src/main/java/datingapp/storage/H2LikeStorage.java;src/main/java/datingapp/storage/H2BlockStorage.java;src/main/java/datingapp/storage/H2ReportStorage.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/cli/LikerBrowserHandler.java;src/main/java/datingapp/cli/SafetyHandler.java;src/main/java/datingapp/cli/MessagingHandler.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/main/java/datingapp/ui/viewmodel/StatsViewModel.java;src/test/java/datingapp/storage/H2StorageIntegrationTest.java;src/test/java/datingapp/core/SwipeSessionTest.java;src/test/java/datingapp/core/SessionServiceTest.java;src/test/java/datingapp/core/Round2BugInvestigationTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/MatchingServiceTest.java;src/test/java/datingapp/core/LikeTest.java;src/test/java/datingapp/core/ReportServiceTest.java;src/test/java/datingapp/core/LikerBrowserServiceTest.java;src/test/java/datingapp/core/DailyPickServiceTest.java;src/test/java/datingapp/core/BlockTest.java;src/test/java/datingapp/core/ReportTest.java;src/test/java/datingapp/core/AchievementServiceTest.java;docs/core-consolidation-plan.md
10|2026-01-24 16:35:00|agent:github_copilot|core-messaging|Consolidate Message + Conversation into Messaging; update storage/CLI/UI/tests; remove old files|src/main/java/datingapp/core/Messaging.java;src/main/java/datingapp/core/MessagingService.java;src/main/java/datingapp/core/MessageStorage.java;src/main/java/datingapp/core/ConversationStorage.java;src/main/java/datingapp/core/RelationshipTransitionService.java;src/main/java/datingapp/storage/H2MessageStorage.java;src/main/java/datingapp/storage/H2ConversationStorage.java;src/main/java/datingapp/cli/MessagingHandler.java;src/main/java/datingapp/ui/viewmodel/ChatViewModel.java;src/main/java/datingapp/ui/controller/ChatController.java;src/test/java/datingapp/storage/H2StorageIntegrationTest.java;src/test/java/datingapp/core/MessageTest.java;src/test/java/datingapp/core/ConversationTest.java;src/test/java/datingapp/core/MessagingServiceTest.java;docs/core-consolidation-plan.md
11|2026-01-24 17:10:00|agent:github_copilot|core-preferences|Consolidate Interest + Lifestyle into Preferences; refactor User.fromDatabase mapping; update storage/CLI/tests; remove old files|src/main/java/datingapp/core/Preferences.java;src/main/java/datingapp/core/User.java;src/main/java/datingapp/core/Dealbreakers.java;src/main/java/datingapp/core/ProfilePreviewService.java;src/main/java/datingapp/core/MatchQualityService.java;src/main/java/datingapp/storage/H2UserStorage.java;src/main/java/datingapp/cli/ProfileHandler.java;src/test/java/datingapp/core/UserTest.java;src/test/java/datingapp/core/ProfilePreviewServiceTest.java;src/test/java/datingapp/core/ProfileCompletionServiceTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/LikerBrowserServiceTest.java;src/test/java/datingapp/core/InterestTest.java;src/test/java/datingapp/core/InterestMatcherTest.java;src/test/java/datingapp/core/DealbreakersTest.java;src/test/java/datingapp/core/DealbreakersEvaluatorTest.java;src/test/java/datingapp/core/AchievementServiceTest.java;src/test/java/datingapp/core/VerificationServiceTest.java;src/test/java/datingapp/core/MessagingServiceTest.java;src/test/java/datingapp/storage/H2StorageIntegrationTest.java;docs/core-consolidation-plan.md
12|2026-01-24 18:10:00|agent:github_copilot|core-trust-safety|Consolidate VerificationService + ReportService into TrustSafetyService; update CLI/service registry/tests; remove old files|src/main/java/datingapp/core/TrustSafetyService.java;src/main/java/datingapp/core/ServiceRegistry.java;src/main/java/datingapp/cli/SafetyHandler.java;src/main/java/datingapp/cli/ProfileVerificationHandler.java;src/main/java/datingapp/Main.java;src/test/java/datingapp/core/ReportServiceTest.java;src/test/java/datingapp/core/VerificationServiceTest.java;docs/core-consolidation-plan.md
13|2026-01-24 18:40:00|agent:github_copilot|core-daily|Consolidate DailyLimitService + DailyPickService into DailyService; update CLI/UI/tests; remove old files|src/main/java/datingapp/core/DailyService.java;src/main/java/datingapp/core/ServiceRegistry.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/Main.java;src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/main/java/datingapp/ui/ViewModelFactory.java;src/test/java/datingapp/core/DailyLimitServiceTest.java;src/test/java/datingapp/core/DailyPickServiceTest.java;docs/core-consolidation-plan.md
14|2026-01-25 05:05:00|agent:github_copilot|file-consolidation|Complete Batches 4-6: Nested 10 storage interfaces into domain files; merged ProfileVerificationHandler→SafetyHandler, UserManagementHandler→ProfileHandler. Reduced from 159 to 132 Java files (-27, -17%). All 464 tests pass.|src/main/java/datingapp/core/Messaging.java;src/main/java/datingapp/core/Social.java;src/main/java/datingapp/core/Stats.java;src/main/java/datingapp/core/Match.java;src/main/java/datingapp/core/Achievement.java;src/main/java/datingapp/core/ProfilePreviewService.java;src/main/java/datingapp/core/User.java;src/main/java/datingapp/core/SwipeSession.java;src/main/java/datingapp/app/cli/SafetyHandler.java;src/main/java/datingapp/app/cli/ProfileHandler.java;src/main/java/datingapp/Main.java;src/test/java/datingapp/cli/ProfileCreateSelectTest.java;FILE_CONSOLIDATION_IMPLEMENTATION_PLAN.md
15|2026-01-25 08:30:00|agent:github_copilot|doc-finalize|Verify file consolidation complete (159→128 files, -31, -19.5%); update docs with actual results; mark plan complete|FILE_COUNT_REDUCTION_REPORT.md;docs/architecture.md;FILE_CONSOLIDATION_IMPLEMENTATION_PLAN.md;AGENTS.md
16|2026-01-27 04:06:40|agent:codex|scope:core-daily|Use liked-or-passed set for daily pick exclusions|src/main/java/datingapp/core/DailyService.java;AGENTS.md
17|2026-01-27 04:19:23|agent:codex|scope:ui-profile-photo|Sync profile photo UI with stored URLs|src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;AGENTS.md
18|2026-01-27 04:30:52|agent:codex|scope:tests-sync|Align in-memory test storages with updated interfaces|src/test/java/datingapp/cli/ProfileCreateSelectTest.java;src/test/java/datingapp/core/DailyPickServiceTest.java;src/test/java/datingapp/core/LikerBrowserServiceTest.java;src/test/java/datingapp/core/StatsServiceTest.java;src/test/java/datingapp/core/testutil/TestStorages.java;src/test/java/datingapp/core/MessagingServiceTest.java;AGENTS.md
19|2026-01-27 18:14:38|agent:codex|scope:ui-stylesheet|Guard stylesheet lookups and clean up UI diagnostics|src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/controller/PreferencesController.java;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/util/UiServices.java;src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java;src/test/java/datingapp/ui/JavaFxCssValidationTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/SwipeSessionTest.java;AGENTS.md
20|2026-01-27 19:17:26|agent:codex|scope:storage-tests|Harden SQL helpers and fix FK-aware storage tests|src/main/java/datingapp/storage/AbstractH2Storage.java;src/main/java/datingapp/storage/DatabaseManager.java;src/main/java/datingapp/core/UndoService.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/main/java/datingapp/core/Match.java;src/main/java/datingapp/core/Messaging.java;src/main/java/datingapp/core/User.java;src/main/java/datingapp/core/Dealbreakers.java;src/main/java/datingapp/ui/controller/BaseController.java;src/main/java/datingapp/ui/util/UiAnimations.java;src/test/java/datingapp/core/StatsMetricsTest.java;src/main/java/datingapp/storage/H2UserStorage.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;src/main/java/datingapp/core/MatchQualityService.java;src/test/java/datingapp/core/UndoServiceTest.java;src/main/java/datingapp/ui/controller/PreferencesController.java;src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java;src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/util/UiServices.java;src/test/java/datingapp/ui/JavaFxCssValidationTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/SwipeSessionTest.java;src/test/java/datingapp/storage/H2ProfileDataStorageTest.java;src/test/java/datingapp/storage/H2DailyPickViewStorageTest.java;src/test/java/datingapp/storage/H2MetricsStorageTest.java;src/test/java/datingapp/storage/H2ModerationStorageTest.java;src/test/java/datingapp/storage/H2StorageIntegrationTest.java;AGENTS.md
21|2026-01-27 19:41:11|agent:codex|scope:ui-profile-completion|Add birth date editing to match completion scoring|src/main/resources/fxml/profile.fxml;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;src/main/java/datingapp/core/ProfileCompletionService.java;AGENTS.md
22|2026-01-27 20:01:31|agent:codex|scope:ui-profile-completion-details|Show missing completion details in profile header|src/main/resources/fxml/profile.fxml;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;AGENTS.md
23|2026-01-27 20:16:10|agent:codex|scope:core-candidate-distance|Relax distance filtering when location is missing|src/main/java/datingapp/core/CandidateFinder.java;AGENTS.md
24|2026-01-27 20:16:52|agent:codex|scope:ui-preferences-age-label|Add explicit age range separator in discovery header|src/main/resources/fxml/preferences.fxml;AGENTS.md
25|2026-01-27 21:05:53|agent:codex|scope:ui-likes-sections|Add likes tabs and actions to matches screen|src/main/resources/fxml/matches.fxml;src/main/java/datingapp/ui/controller/MatchesController.java;src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java;src/main/java/datingapp/ui/ViewModelFactory.java;src/main/resources/css/theme.css;AGENTS.md
26|2026-01-27 21:09:00|agent:codex|scope:ui-likes-checkstyle|Add default switch branches for matches sections|src/main/java/datingapp/ui/controller/MatchesController.java;AGENTS.md
27|2026-01-27 21:19:03|agent:codex|scope:ui-login-polish|Polish login screen and add double-click login|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/controller/LoginController.java;src/main/resources/css/theme.css;AGENTS.md
28|2026-01-27 21:20:30|agent:codex|scope:ui-login-cell-reset|Reset login list cell text for reuse safety|src/main/java/datingapp/ui/controller/LoginController.java;AGENTS.md
29|2026-01-27 21:35:44|agent:codex|scope:ui-login-ux|Enhance login search, badges, and keyboard navigation|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/viewmodel/LoginViewModel.java;src/main/resources/css/theme.css;AGENTS.md
30|2026-01-27 21:46:43|agent:codex|scope:ui-login-layout|Adjust login spacing and widths for cleaner layout|src/main/resources/fxml/login.fxml;AGENTS.md
31|2026-01-27 21:47:59|agent:codex|scope:ui-login-avatar-fallback|Ignore placeholder avatar URLs in login list|src/main/java/datingapp/ui/controller/LoginController.java;AGENTS.md
32|2026-01-27 21:50:46|agent:codex|scope:ui-login-scroll-height|Increase login list height for better visibility|src/main/resources/fxml/login.fxml;AGENTS.md
33|2026-01-27 21:53:33|agent:codex|scope:ui-login-scroll-height-2|Increase login list height for larger scroll area|src/main/resources/fxml/login.fxml;AGENTS.md
34|2026-01-27 22:03:53|agent:codex|scope:ui-login-scroll-space|Expand login window and list area for more visible rows|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/DatingApp.java;src/main/java/datingapp/ui/NavigationService.java;AGENTS.md
35|2026-01-27 22:06:57|agent:codex|scope:ui-login-scroll-balance|Reduce login window height and list size to keep actions visible|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/DatingApp.java;src/main/java/datingapp/ui/NavigationService.java;AGENTS.md
36|2026-01-27 22:11:35|agent:codex|scope:ui-login-top-spacing|Trim login header padding to reduce top whitespace|src/main/resources/fxml/login.fxml;AGENTS.md
37|2026-01-30 18:00:00|agent:opencode|scope:doc-sync|Update AGENTS.md to reflect actual codebase: 133 files, correct package paths (app.cli, core.storage), ServiceRegistry.Builder pattern|AGENTS.md
38|2026-02-04 22:51:33|agent:codex|scope:logging-guards|Guard log calls with level checks for PMD|src/main/java/datingapp/Main.java;src/main/java/datingapp/app/cli/LikerBrowserHandler.java;src/main/java/datingapp/app/cli/MatchingHandler.java;src/main/java/datingapp/app/cli/MessagingHandler.java;src/main/java/datingapp/app/cli/ProfileHandler.java;src/main/java/datingapp/ui/viewmodel/LoginViewModel.java;src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;src/main/java/datingapp/ui/viewmodel/StatsViewModel.java;AGENTS.md
39|2026-02-04 23:28:48|agent:codex|scope:code-style-identifiers|Avoid '_' identifiers in Java and use descriptive lambda parameters|src/main/java/datingapp/ui/controller/AchievementPopupController.java;AGENTS.md
40|2026-02-04 23:32:59|agent:codex|scope:ui-fxml-unused|Suppress false-positive unused warnings for FXML-only members|src/main/java/datingapp/ui/controller/AchievementPopupController.java;src/main/java/datingapp/ui/controller/MatchPopupController.java;src/main/java/datingapp/ui/controller/ProfileController.java;AGENTS.md
41|2026-02-05 02:47:40|agent:codex|scope:db-indexes-consistency|Group additional schema indexes in initializeSchema for consistency|src/main/java/datingapp/storage/DatabaseManager.java;AGENTS.md
42|2026-02-05 02:59:07|agent:codex|scope:ui-loading-overlay|Fix loading overlay timing by toggling loading state on FX thread around background tasks|src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/main/java/datingapp/ui/viewmodel/ChatViewModel.java;src/main/java/datingapp/ui/controller/ChatController.java;AGENTS.md
43|2026-02-05 15:45:00|agent:claude_code|scope:comprehensive-update|Comprehensive AGENTS.md update based on actual codebase: 139 files (102 main + 37 test), Bootstrap patterns (AppBootstrap/AppSession/HandlerFactory), Result pattern, TestStorages, MVVM ErrorHandler, JDBI storage layer, AppConfig usage, 3-layer architecture|AGENTS.md
44|2026-02-05 16:15:00|agent:claude_code|scope:verification-fixes|Verified and corrected AGENTS.md: 14 services (added ProfileCompletionService), 11 controllers (added popup controllers), 12 domain models (added DailyPick), standalone enums (Gender/UserState/VerificationMethod), JDBI helpers (adapter, binding, enum mappers), TestUserFactory utility, Main.java CLI entry point, correct file counts (102 main + 37 test)|AGENTS.md
45|2026-02-05 03:54:37|agent:codex|scope:ui-loading-timing|Refine loading overlay timing guidance for FX-thread updates and initialization|src/main/java/datingapp/ui/controller/ChatController.java;src/main/java/datingapp/ui/controller/DashboardController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/viewmodel/ChatViewModel.java;src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;AGENTS.md
46|2026-02-05 04:02:41|agent:codex|scope:messaging-conversation-id|Clarify conversation ID type for UI/viewmodel usage|src/main/java/datingapp/ui/viewmodel/ChatViewModel.java;AGENTS.md
47|2026-02-05 13:35:19|agent:codex|scope:project-audit|Add project audit report|PROJECT_AUDIT_2026-02-05_codex.md;AGENTS.md
---AGENT-LOG-END---
