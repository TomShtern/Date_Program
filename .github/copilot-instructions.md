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

**Platform:** Windows 11 | PowerShell | VS Code Insiders | Java 25 | JavaFX 25.0.1
**Stats:** 102 main + 58 test Java files | ~48K lines (~35K code) | 60% coverage min | 802 tests | 14 core services

## ⚠️ Critical Gotchas (Compilation Failures)

| Issue                    | Wrong                               | Correct                                                                          |
|--------------------------|-------------------------------------|----------------------------------------------------------------------------------|
| Non-static nested types  | `public record Y() {}` inside class | `public static record Y() {}`                                                    |
| EnumSet null crash       | `EnumSet.copyOf(interests)`         | `interests != null ? EnumSet.copyOf(interests) : EnumSet.noneOf(Interest.class)` |
| Exposed mutable field    | `return interests;`                 | `return EnumSet.copyOf(interests);`                                              |
| Missing touch()          | `this.name = name;`                 | `this.name = name; touch();`                                                     |
| Service throws exception | `throw new SomeException(...)`      | `return SendResult.failure(msg, code)`                                           |
| Hardcoded thresholds     | `if (age < 18)`                     | `if (age < CONFIG.minAge())`                                                     |
| Wrong pair ID            | `a + "_" + b`                       | `a.compareTo(b) < 0 ? a+"_"+b : b+"_"+a`                                         |

**Access config:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Architecture Overview

**Clean Architecture with MVVM for JavaFX UI:**
```
core/              Utility/infra: AppConfig, AppSession, ServiceRegistry, EnumSetUtil, ScoringConstants...
core/model/        11 domain models: User, Match, Messaging, Preferences, UserInteractions...
core/service/      14 services: MatchingService, MessagingService, CandidateFinder...
core/storage/      5 storage interfaces (UserStorage, InteractionStorage, CommunicationStorage, AnalyticsStorage, TrustSafetyStorage)
storage/           DatabaseManager, StorageFactory
storage/jdbi/      JDBI implementations + MapperHelper + EnumSetJdbiSupport
storage/schema/    SchemaInitializer, MigrationRunner
app/               AppBootstrap, ConfigLoader
app/cli/           5 CLI handlers + HandlerFactory + CliSupport
app/api/           RestApiServer (routes inlined)
ui/                DatingApp, NavigationService, UiComponents, ViewModelFactory
ui/viewmodel/      8 ViewModels + ErrorHandler + data/UiDataAdapters
ui/controller/     11 controllers (extend BaseController for lifecycle)
ui/constants/      UiConstants (animation + cache constants)
ui/util/           Toast, UiSupport, ImageCache, UiAnimations
```

**Entry Points:**
```java
// Core services - idempotent singleton initialization
ServiceRegistry services = AppBootstrap.initialize();
AppSession session = AppSession.getInstance();  // Current user context

// CLI - lazy handler creation
HandlerFactory handlers = new HandlerFactory(services, session, inputReader);
handlers.matching().runMatchingLoop();

// JavaFX - MVVM setup
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Key Domain Models

| Model              | Type          | Location                           | Notes                                                                        |
|--------------------|---------------|------------------------------------|------------------------------------------------------------------------------|
| `User`             | Mutable class | `core/model/User.java`             | State: `INCOMPLETE→ACTIVE↔PAUSED→BANNED`; has `StorageBuilder`               |
| `Match`            | Mutable class | `core/model/Match.java`            | State: `ACTIVE→FRIENDS\|UNMATCHED\|BLOCKED`; deterministic ID                |
| `UserInteractions` | Container     | `core/model/UserInteractions.java` | Contains: `Like`, `Block`, `Report`, `FriendRequest`, `Notification` records |
| `Messaging`        | Container     | `core/model/Messaging.java`        | Contains: `Message`, `Conversation` with deterministic ID                    |
| `Preferences`      | Container     | `core/model/Preferences.java`      | Contains: `Interest` enum (39 values), `Lifestyle`, `PacePreferences`        |

**Deterministic IDs for Two-User Entities:**
```java
// Match.java, Messaging.Conversation - ALWAYS use this pattern
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

## JavaFX UI Patterns (MVVM)

### ViewModelFactory (Singleton ViewModels)
```java
// ViewModelFactory.java - creates and caches ViewModels
public class ViewModelFactory {
    private LoginViewModel loginViewModel;  // Lazy-initialized

    public LoginViewModel getLoginViewModel() {
        if (loginViewModel == null) {
            loginViewModel = new LoginViewModel(services.getUserStorage());
        }
        return loginViewModel;
    }
}

// Usage in FXML loader
loader.setControllerFactory(viewModelFactory::createController);
```

### ErrorHandler Pattern (ViewModel→Controller)
```java
// In ViewModel - notify errors to controller
private ErrorHandler errorHandler;
public void setErrorHandler(ErrorHandler handler) { this.errorHandler = handler; }

private void notifyError(String userMessage, Exception e) {
    if (errorHandler != null) {
        Platform.runLater(() -> errorHandler.onError(userMessage + ": " + e.getMessage()));
    }
}

// In Controller initialize() - wire to toast
viewModel.setErrorHandler(msg -> Toast.showError(msg));
```

### BaseController Lifecycle
```java
// All controllers extend BaseController for automatic cleanup
public class MyController extends BaseController {
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Register subscriptions for auto-cleanup
        addSubscription(nameField.textProperty().subscribe(this::onNameChanged));

        // Register loading overlays
        StackPane loadingOverlay = UiComponents.createLoadingOverlay();
        registerOverlay(loadingOverlay);
        loadingOverlay.visibleProperty().bind(viewModel.loadingProperty());
        loadingOverlay.managedProperty().bind(viewModel.loadingProperty());
    }
}
// BaseController.cleanup() is called automatically on navigation
```

### Navigation Context (View-to-View Data)
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

### UI Utilities
```java
// Toast notifications
Toast.showSuccess("Match created!");
Toast.showError("Failed to send message");

// Loading overlays
StackPane overlay = UiComponents.createLoadingOverlay();
overlay.visibleProperty().bind(viewModel.loadingProperty());

// Confirmation dialogs
boolean confirmed = UiSupport.showConfirmation(
    "Unmatch",
    "Are you sure you want to unmatch with " + name + "?"
);
```

## Build & Test Commands

```bash
mvn compile && mvn exec:exec              # Compile + Run CLI (forked JVM with --enable-preview)
mvn javafx:run                            # Run JavaFX GUI
mvn test                                  # All tests (802+)
mvn test -Dtest=MatchingServiceTest#mutualLikesCreateMatch  # Single method
mvn spotless:apply && mvn verify          # Format + full quality checks (REQUIRED before commit)
```

## Testing Patterns

**Use centralized `TestStorages` - NO Mockito:**
```java
// In test class setup
var userStorage = new TestStorages.Users();
var likeStorage = new TestStorages.Likes();
var matchStorage = new TestStorages.Matches();
var trustSafetyStorage = new TestStorages.TrustSafety();
```

**Test Structure:**
```java
@Timeout(5) class MyServiceTest {
    @Nested @DisplayName("When user is active")
    class WhenActive {
        @Test @DisplayName("should allow messaging")
        void allowsMessaging() {
            // Arrange
            User user = createActiveUser();

            // Act
            var result = service.sendMessage(user.getId(), "Hello");

            // Assert
            assertTrue(result.success());
        }
    }
}
```

**Helper Methods at End of Test Class:**
```java
private User createActiveUser(UUID id, String name) {
    User u = new User(id, name);
    u.setBirthDate(LocalDate.now().minusYears(25));
    u.setGender(User.Gender.MALE);
    u.setInterestedIn(Set.of(User.Gender.FEMALE));
    u.setMaxDistanceKm(50);
    u.setMinAge(20); u.setMaxAge(30);
    u.addPhotoUrl("http://example.com/photo.jpg");
    return u;
}
```

## Core Patterns

### StorageBuilder (Loading from DB - bypass validation)
```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio).birthDate(birthDate).gender(gender)
    .interestedIn(interestedIn)  // Handles null safely
    .state(state).build();
```

### Factory Methods (Creating new entities)
```java
Match match = Match.create(userA, userB);  // Generates deterministic ID + timestamps
Like like = Like.create(fromId, toId, Like.Direction.LIKE);
Message msg = Message.create(conversationId, senderId, content);
```

### Result Pattern (Services never throw)
```java
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
// Usage: return SendResult.failure("Match not active", ErrorCode.MATCH_NOT_ACTIVE);
```

### Touch Pattern (All setters on mutable entities)
```java
private void touch() { this.updatedAt = Instant.now(); }
public void setBio(String bio) { this.bio = bio; touch(); }  // EVERY setter
```

### EnumSet Defensive Patterns
```java
// Setter - handle null safely
public void setInterestedIn(Set<Gender> interestedIn) {
    this.interestedIn = interestedIn != null
        ? EnumSet.copyOf(interestedIn)
        : EnumSet.noneOf(Gender.class);
    touch();
}

// Getter - never expose internal reference
public Set<Gender> getInterestedIn() {
    return interestedIn.isEmpty()
        ? EnumSet.noneOf(Gender.class)
        : EnumSet.copyOf(interestedIn);
}
```

## JDBI Storage Patterns

### Storage Interface with Mapper
```java
@RegisterRowMapper(JdbiUserStorage.Mapper.class)
public interface JdbiUserStorage extends UserStorage {
    String ALL_COLUMNS = "id, name, bio, birth_date, ...";  // Avoid copy-paste errors

    @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
    void save(@BindBean UserBindingHelper helper);

    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id")
    User get(@Bind("id") UUID id);

    class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return User.StorageBuilder.create(
                MapperHelper.readUuid(rs, "id"),      // Null-safe helpers
                rs.getString("name"),
                MapperHelper.readInstant(rs, "created_at")
            ).birthDate(MapperHelper.readLocalDate(rs, "birth_date"))
             .gender(MapperHelper.readEnum(rs, "gender", User.Gender.class))
             .build();
        }
    }
}
```

### MapperHelper Utilities (storage/jdbi/)
```java
// Null-safe reading from ResultSet
UUID id = MapperHelper.readUuid(rs, "id");
Instant ts = MapperHelper.readInstant(rs, "created_at");
LocalDate date = MapperHelper.readLocalDate(rs, "birth_date");
User.Gender gender = MapperHelper.readEnum(rs, "gender", User.Gender.class);
Integer age = MapperHelper.readInteger(rs, "age");  // Handles NULL
List<String> urls = MapperHelper.readCsvAsList(rs, "photo_urls");
```

## Configuration (`AppConfig`)

`AppConfig` is a **record with 40+ parameters** grouped as:

| Group      | Examples                                                            | Access                      |
|------------|---------------------------------------------------------------------|-----------------------------|
| Limits     | `dailyLikeLimit(100)`, `maxSwipesPerSession(500)`                   | `CONFIG.dailyLikeLimit()`   |
| Validation | `minAge(18)`, `maxAge(120)`, `minHeightCm(50)`, `maxHeightCm(300)`  | `CONFIG.minAge()`           |
| Algorithm  | `nearbyDistanceKm(5)`, `similarAgeDiff(2)`, `minSharedInterests(3)` | `CONFIG.nearbyDistanceKm()` |
| Weights    | `distanceWeight(0.15)`, `interestWeight(0.25)`, `paceWeight(0.15)`  | `CONFIG.distanceWeight()`   |

**Usage:** `private static final AppConfig CONFIG = AppConfig.defaults();`
**Custom:** `AppConfig.builder().dailyLikeLimit(50).minAge(21).build()`

## File Structure Reference

| Purpose            | Location                                                                           |
|--------------------|------------------------------------------------------------------------------------|
| Domain models      | `core/model/{User,Match,Messaging,UserInteractions,Preferences,Dealbreakers}.java` |
| Storage interfaces | `core/storage/*Storage.java` (5 interfaces)                                        |
| Services           | `core/service/*Service.java` (14 services)                                         |
| JDBI storage       | `storage/jdbi/Jdbi*Storage.java`                                                   |
| Storage mappers    | `storage/jdbi/MapperHelper.java`, `UserBindingHelper.java`                         |
| Service wiring     | `core/ServiceRegistry.java`, `app/AppBootstrap.java`                               |
| Session management | `core/AppSession.java`                                                             |
| CLI handlers       | `app/cli/{Matching,Messaging,Profile,Safety,Stats}Handler.java`                    |
| REST API           | `app/api/RestApiServer.java` (routes inlined)                                      |
| JavaFX entry       | `ui/DatingApp.java`                                                                |
| ViewModels         | `ui/viewmodel/*ViewModel.java`, `ErrorHandler.java`                                |
| Controllers        | `ui/controller/*Controller.java`, `BaseController.java`                            |
| Navigation         | `ui/NavigationService.java`, `ui/ViewModelFactory.java`                            |
| UI components      | `ui/UiComponents.java`                                                             |
| UI utilities       | `ui/util/{Toast,UiSupport,ImageCache,UiAnimations}.java`                           |
| UI constants       | `ui/constants/UiConstants.java`                                                    |
| Configuration      | `core/AppConfig.java` (40+ params)                                                 |
| Test utilities     | `test/.../testutil/{TestStorages,TestUserFactory,TestClock}.java`                  |

## Data Flow

1. **Candidate Discovery:** `CandidateFinder` → 7-stage filter: self → ACTIVE → no interaction → mutual gender → mutual age → distance → dealbreakers → sort by distance
2. **Matching:** `MatchingService.recordLike()` → saves Like → creates Match on mutual interest
3. **Quality Scoring:** Distance(15%) + Age(10%) + Interests(25%) + Lifestyle(25%) + Pace(15%) + Response(10%)
4. **Undo:** In-memory state tracking with 30s expiration (lost on restart)

## System Tools Available

**High-Performance Rust Tools:**
- **ripgrep** (`rg`) v14.1.0 - Ultra-fast regex search (use instead of grep)
- **ast-grep** (`sg`) v0.40.0 - AST-based structural search/refactoring
- **tokei** v12.1.2 - Fast LOC/comments/blanks counter
- **fd** v10.3.0 - Fast file finder (use instead of find)
- **bat** v0.26.0 - Syntax-highlighted file viewer
- **sd** v1.0.0 - Intuitive find & replace
- **jq** v1.8.1 - JSON processor
- **yq** v4.48.2 - YAML/TOML/XML processor
- **Semgrep** v1.140.0 - Polyglot SAST

**Usage Tip:** For code searches requiring syntax understanding, use `ast-grep --lang java -p '<pattern>'` instead of text-only search tools.

## NEVER Do These

- ❌ Import framework/DB in `core/` (zero coupling)
- ❌ Skip `Objects.requireNonNull()` in constructors
- ❌ Return mutable collections directly
- ❌ Forget `static` on nested types
- ❌ Use Mockito (use `TestStorages.*` instead)
- ❌ Throw from services (return `*Result` records)
- ❌ Hardcode thresholds (use `AppConfig.defaults()` or `ScoringConstants`)
- ❌ Call `new User(...)` in mappers (use `StorageBuilder`)
- ❌ Use `HashSet` for enums (use `EnumSet`)
- ❌ Forget `touch()` in setters
- ❌ Import `core/storage/*` in ViewModels (use `UiDataAdapters` adapters)
- ❌ Call database from ViewModel directly (use services)

## Known Limitations (Don't Fix)

- **No transactions**: Like/Match deletions in undo flow are not atomic
- **In-memory undo state**: Lost on restart (UndoService)
- **Simulated verification**: Email/phone codes not actually sent
- **No caching layer**: Repeated database queries acceptable for Phase 2
- **ViewModel singletons**: Shared across views (intentional for state persistence)

## Documentation Index

| Doc                                    | Purpose                      |
|----------------------------------------|------------------------------|
| `AGENTS.md`                            | Full coding standards        |
| `CLAUDE.md`                            | Project-specific AI guidance |
| `docs/architecture.md`                 | Mermaid diagrams             |
| `docs/completed-plans/`                | Completed design documents   |
| `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` | Code review findings         |
