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
**Database:** H2 2.4.240 (embedded, file-based)

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
mvn javafx:run                       # Run JavaFX GUI app (experimental UI)
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
- Always use constructor injection, never `new` for dependencies in services
- Tests use real H2 database, not mocks

## Architecture Rules

**Two-Layer Clean Architecture:**
- `core/` - Pure Java business logic, NO framework/database imports
- `storage/` - H2 database implementations (JDBI-based)
- `app/cli/` - Console UI for debugging/verification
- `ui/` - JavaFX GUI (primary user interface)

**Critical:** Storage interfaces defined in `core/`, implemented in `storage/`. Core services depend only on interfaces, never implementations.

**Dependency Injection:** Constructor injection only - all dependencies via constructors. Use `ServiceRegistry.Builder` to wire dependencies in production code.

## Code Style

**Formatting:** Palantir Java Format v2.39.0 (4-space indentation). Auto-apply with `mvn spotless:apply` before every commit.

**Imports:** No star imports (`import java.util.*`). Import ordering: static first, then third-party, then standard library.

**Naming Conventions:**
- Classes: PascalCase - `UserService`, `H2UserStorage`, `ProfileHandler`
- Methods: camelCase - `getUserById()`, `createMatch()`, `isComplete()`
- Predicates: `is`/`has`/`can` prefix - `isActive()`, `hasDealbreakers()`, `canLike()`
- Constants: UPPER_SNAKE_CASE - `MAX_DISTANCE_KM`, `DEFAULT_TIMEOUT`

**Types:**
- Use `record` for immutable data (e.g., `UserInteractions.Like`, `UserInteractions.Block`, `UserInteractions.Report`, `MatchQuality`)
- Use `class` for mutable entities with state machines (`User`, `Match`)
- Use `enum` for fixed sets (`Preferences.Interest`, `Achievement`)
- Use `Optional<T>` for nullable returns from storage

**State Machines:**
- `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Match`: `ACTIVE → UNMATCHED | BLOCKED`
- Validate state before transitions with `IllegalStateException`

## Error Handling

**Validation:**
- Constructor parameters: `Objects.requireNonNull(param, "param cannot be null")`
- Business rules: `IllegalArgumentException("Cannot like yourself")`
- State transitions: `IllegalStateException("User is not ACTIVE")`

**Storage Layer:** Wrap `SQLException` in `StorageException` (RuntimeException)

**CLI/UI Layer:** Never throw exceptions to users. Log user-friendly messages and continue gracefully.

## Testing Standards

**Test Structure:**
- Use JUnit 5 with `@Nested` classes for logical grouping
- Test class name: `{ClassName}Test.java`
- Test methods: `@DisplayName("Description")` + descriptive method name
- Use `@SuppressWarnings("unused")` on test classes with `@Nested`

<!--ARCHIVE:18:agent:codex:tests-sync-->
**Mocking:** Create in-memory implementations inline in tests (NO Mockito). Example:
```java
private static class InMemoryUserStorage implements UserStorage {
    private final Map<UUID, User> users = new HashMap<>();
    // Implement only methods needed for tests
}
```
<!--/ARCHIVE-->
**Mocking:** Create in-memory implementations inline in tests (NO Mockito). Example:
```java
private static class InMemoryUserStorage implements UserStorage {
    private final Map<UUID, User> users = new HashMap<>();
    // Implement only methods needed for tests
}
```
Keep in-memory storages aligned with core interfaces when new methods are added.
18|2026-01-27 04:30:52|agent:codex|scope:tests-sync|Align in-memory test storages with updated interfaces|src/test/java/datingapp/cli/ProfileCreateSelectTest.java;src/test/java/datingapp/core/DailyPickServiceTest.java;src/test/java/datingapp/core/LikerBrowserServiceTest.java;src/test/java/datingapp/core/StatsServiceTest.java;src/test/java/datingapp/core/testutil/TestStorages.java;src/test/java/datingapp/core/MessagingServiceTest.java;AGENTS.md

<!--ARCHIVE:20:agent:codex:storage-tests-->
**Integration Tests:** Use real H2 database with unique test DB name per test class.
<!--/ARCHIVE-->
**Integration Tests:** Use real H2 database with unique test DB name per test class. Create required user rows before inserting records with user_id foreign keys.
20|2026-01-27 19:17:26|agent:codex|scope:storage-tests|Harden SQL helpers and fix FK-aware storage tests|src/main/java/datingapp/storage/AbstractH2Storage.java;src/main/java/datingapp/storage/DatabaseManager.java;src/main/java/datingapp/core/UndoService.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/main/java/datingapp/core/Match.java;src/main/java/datingapp/core/Messaging.java;src/main/java/datingapp/core/User.java;src/main/java/datingapp/core/Dealbreakers.java;src/main/java/datingapp/ui/controller/BaseController.java;src/main/java/datingapp/ui/util/UiAnimations.java;src/test/java/datingapp/core/StatsMetricsTest.java;src/main/java/datingapp/storage/H2UserStorage.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;src/main/java/datingapp/core/MatchQualityService.java;src/test/java/datingapp/core/UndoServiceTest.java;src/main/java/datingapp/ui/controller/PreferencesController.java;src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java;src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/util/UiServices.java;src/test/java/datingapp/ui/JavaFxCssValidationTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/SwipeSessionTest.java;src/test/java/datingapp/storage/H2ProfileDataStorageTest.java;src/test/java/datingapp/storage/H2DailyPickViewStorageTest.java;src/test/java/datingapp/storage/H2MetricsStorageTest.java;src/test/java/datingapp/storage/H2ModerationStorageTest.java;src/test/java/datingapp/storage/H2StorageIntegrationTest.java;AGENTS.md

**Coverage:** Minimum 60% line coverage (enforced by JaCoCo, excludes ui/ and cli/)

## Special Patterns

**Match ID Generation:** Deterministic from sorted UUIDs - use `Match.generateId(userA, userB)` where ID is `userA_userB` with lexicographically smaller UUID first.

**Factory Methods:** `create()`, `of()`, `fromDatabase()` for object construction.

**Defensive Copying:** Always return defensive copies of collections: `return EnumSet.copyOf(interests);`

<!--ARCHIVE:23:agent:codex:core-candidate-distance-->
**Daily Pick Exclusions:** Use `LikeStorage.getLikedOrPassedUserIds()` to avoid resurfacing users already liked or passed.
16|2026-01-27 04:06:40|agent:codex|scope:core-daily|Use liked-or-passed set for daily pick exclusions|src/main/java/datingapp/core/DailyService.java;AGENTS.md
<!--/ARCHIVE-->
**Daily Pick Exclusions:** Use `LikeStorage.getLikedOrPassedUserIds()` to avoid resurfacing users already liked or passed.
**Candidate Distance:** If either user lacks a location (0,0), skip distance filtering and sort unknown distances last to avoid empty queues.
23|2026-01-27 20:16:10|agent:codex|scope:core-candidate-distance|Relax distance filtering when location is missing|src/main/java/datingapp/core/CandidateFinder.java;AGENTS.md

**Touch Pattern:** Update `updatedAt` timestamp on entity changes:
```java
public void setName(String name) {
    this.name = Objects.requireNonNull(name);
    touch();  // Updates updatedAt = Instant.now()
}
```

## File Locations

- Domain models: `src/main/java/datingapp/core/{User,Match,Messaging,Social,Stats,UserInteractions,Preferences,Dealbreakers,Achievement,SwipeSession}.java`
- Storage interfaces: `src/main/java/datingapp/core/storage/*Storage.java` (nested in core)
- Services: `src/main/java/datingapp/core/*Service.java`
- JDBI implementations: `src/main/java/datingapp/storage/jdbi/Jdbi*Storage.java`
- Service wiring: `src/main/java/datingapp/core/ServiceRegistry.java` (uses inner Builder class)
- CLI handlers: `src/main/java/datingapp/app/cli/*Handler.java`
- JavaFX UI controllers: `src/main/java/datingapp/ui/controller/*.java`
- JavaFX UI viewmodels: `src/main/java/datingapp/ui/viewmodel/*.java`

## Critical Rules

❌ NEVER:
- Import framework/database classes in `core/` package
- Skip constructor validation with `Objects.requireNonNull()`
- Throw exceptions in CLI/UI handlers
- Use Mockito or external mocking libraries
- Commit without running `mvn spotless:apply`
- Return direct collection references (defensive copy required)
- Use `new ArrayList<>()` with empty `EnumSet.copyOf()` - use `EnumSet.noneOf()` for empty sets
- Mix business logic in storage classes - only mapping to/from database
- Hardcode user input validation messages - use `CliConstants`

✅ ALWAYS:
- Use constructor injection for dependencies
- Add `@DisplayName` to test methods
- Update `updatedAt` timestamps on entity changes
- Run `mvn spotless:apply` before committing
- Write unit tests with in-memory mocks
- Validate inputs at boundaries
- Handle `NumberFormatException` gracefully in CLI - log user-friendly message
- Use try-with-resources for database connections
- Validate SQL parameters before execution

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
- Map old defaults in `fromDatabase()` for legacy rows

## Database Patterns

**H2 Database Configuration:**
- **Location:** `./data/dating.mv.db` (file-based, auto-created on first run)
- **JDBC URL:** `jdbc:h2:./data/dating`
- **Username:** `sa`
- **Password:** `changeit` (hardcoded for development; use env var `DATING_APP_DB_PASSWORD` in production)
- **Mode:** Embedded (no separate server process)
- **Auto-Server:** Disabled during tests to prevent locking issues

**H2 Storage Implementation:**
```java
// MERGE for upsert (insert or update)
MERGE INTO table_name (id, col1, col2) KEY (id) VALUES (?, ?, ?)

// Always use prepared statements to prevent SQL injection
try (Connection conn = dbManager.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setObject(1, id);
    stmt.executeUpdate();
}

// Use try-with-resources for all auto-closeables
// Handle nulls explicitly with rs.wasNull()
int value = rs.getInt("column");
if (rs.wasNull()) { value = null; }
```

**Complex Type Mapping:**
- **Enums**: Store as VARCHAR, convert with `enum.name()` / `Enum.valueOf()`
- **EnumSets**: Store as CSV `"A,B,C"`, use `parseEnumSet()` helper
- **Lists**: Use delimiter not in content (pipe `|` for URLs)
- **UUIDs**: Use `stmt.setObject()` and `rs.getObject(UUID.class)`
- **Instants**: Use `Timestamp.from()` / `rs.getTimestamp().toInstant()`

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
```

**Test Coverage Rules:**
- **Happy path**: Primary use cases
- **Sad path**: Invalid inputs, nulls, empty sets
- **Edge cases**: Boundary values (0, 1, MAX), enum all values
- **State transitions**: All valid and invalid transitions
- **Cross-boundary**: Integration points between services

## Performance Guidelines

**When to Optimize:**
- N+1 query problem in storage
- Operations called in tight loops (>1000 iterations)
- Large collections (>1000 items) repeatedly filtered
- Profiled bottleneck (never premature optimize)

**Collection Choices:**
- **EnumSet**: When elements are enums, need uniqueness
- **HashSet**: General-purpose unique elements
- **ArrayList**: Ordered, index access needed
- **HashMap**: Key-value lookups
- **Stream**: When functional style improves readability, NOT for raw speed

**Database Query Optimization:**
- Use `WHERE` clauses to filter early
- Avoid `SELECT *` - list needed columns
- Index frequently queried columns (auto in H2)
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
<!--ARCHIVE:17:agent:codex:ui-profile-photo-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
<!--/ARCHIVE-->
<!--ARCHIVE:19:agent:codex:ui-stylesheet-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
17|2026-01-27 04:19:23|agent:codex|scope:ui-profile-photo|Sync profile photo UI with stored URLs|src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:21:agent:codex:ui-profile-completion-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
- Guard stylesheet resource lookups and log missing stylesheets before applying them
19|2026-01-27 18:14:38|agent:codex|scope:ui-stylesheet|Guard stylesheet lookups and clean up UI diagnostics|src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/controller/MatchingController.java;src/main/java/datingapp/ui/controller/PreferencesController.java;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/util/UiServices.java;src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java;src/test/java/datingapp/ui/JavaFxCssValidationTest.java;src/test/java/datingapp/core/MatchQualityServiceTest.java;src/test/java/datingapp/core/SwipeSessionTest.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:22:agent:codex:ui-profile-completion-details-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
- Expose birth date inputs in profile editors so completion scoring can reach 100%
- Guard stylesheet resource lookups and log missing stylesheets before applying them
21|2026-01-27 19:41:11|agent:codex|scope:ui-profile-completion|Add birth date editing to match completion scoring|src/main/resources/fxml/profile.fxml;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;src/main/java/datingapp/core/ProfileCompletionService.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:24:agent:codex:ui-preferences-age-label-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
- Expose birth date inputs in profile editors so completion scoring can reach 100%
- Surface missing completion details in profile headers to guide users to 100%
- Guard stylesheet resource lookups and log missing stylesheets before applying them
22|2026-01-27 20:01:31|agent:codex|scope:ui-profile-completion-details|Show missing completion details in profile header|src/main/resources/fxml/profile.fxml;src/main/java/datingapp/ui/controller/ProfileController.java;src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:25:agent:codex:ui-likes-sections-->
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully
- Load persisted profile photos into profile views and keep avatar bindings in sync
- Expose birth date inputs in profile editors so completion scoring can reach 100%
- Surface missing completion details in profile headers to guide users to 100%
- Display age ranges with explicit separators to avoid concatenated labels
- Guard stylesheet resource lookups and log missing stylesheets before applying them
24|2026-01-27 20:16:52|agent:codex|scope:ui-preferences-age-label|Add explicit age range separator in discovery header|src/main/resources/fxml/preferences.fxml;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:26:agent:codex:ui-likes-checkstyle-->
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
- Guard stylesheet resource lookups and log missing stylesheets before applying them
25|2026-01-27 21:05:53|agent:codex|scope:ui-likes-sections|Add likes tabs and actions to matches screen|src/main/resources/fxml/matches.fxml;src/main/java/datingapp/ui/controller/MatchesController.java;src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java;src/main/java/datingapp/ui/ViewModelFactory.java;src/main/resources/css/theme.css;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:27:agent:codex:ui-login-polish-->
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
- Guard stylesheet resource lookups and log missing stylesheets before applying them
26|2026-01-27 21:09:00|agent:codex|scope:ui-likes-checkstyle|Add default switch branches for matches sections|src/main/java/datingapp/ui/controller/MatchesController.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:28:agent:codex:ui-login-cell-reset-->
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
- Polish login screen visuals and allow double-click login from user list
- Guard stylesheet resource lookups and log missing stylesheets before applying them
27|2026-01-27 21:19:03|agent:codex|scope:ui-login-polish|Polish login screen and add double-click login|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/controller/LoginController.java;src/main/resources/css/theme.css;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:29:agent:codex:ui-login-ux-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Guard stylesheet resource lookups and log missing stylesheets before applying them
28|2026-01-27 21:20:30|agent:codex|scope:ui-login-cell-reset|Reset login list cell text for reuse safety|src/main/java/datingapp/ui/controller/LoginController.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:30:agent:codex:ui-login-layout-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Guard stylesheet resource lookups and log missing stylesheets before applying them
29|2026-01-27 21:35:44|agent:codex|scope:ui-login-ux|Enhance login search, badges, and keyboard navigation|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/controller/LoginController.java;src/main/java/datingapp/ui/viewmodel/LoginViewModel.java;src/main/resources/css/theme.css;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:31:agent:codex:ui-login-avatar-fallback-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Guard stylesheet resource lookups and log missing stylesheets before applying them
30|2026-01-27 21:46:43|agent:codex|scope:ui-login-layout|Adjust login spacing and widths for cleaner layout|src/main/resources/fxml/login.fxml;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:32:agent:codex:ui-login-scroll-height-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Skip placeholder avatar URLs when rendering login list images
- Guard stylesheet resource lookups and log missing stylesheets before applying them
31|2026-01-27 21:47:59|agent:codex|scope:ui-login-avatar-fallback|Ignore placeholder avatar URLs in login list|src/main/java/datingapp/ui/controller/LoginController.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:34:agent:codex:ui-login-scroll-space-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Skip placeholder avatar URLs when rendering login list images
- Expand login list height for more visible rows
- Increase login list height again for larger scroll area
- Guard stylesheet resource lookups and log missing stylesheets before applying them
33|2026-01-27 21:53:33|agent:codex|scope:ui-login-scroll-height-2|Increase login list height for larger scroll area|src/main/resources/fxml/login.fxml;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:35:agent:codex:ui-login-scroll-balance-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Skip placeholder avatar URLs when rendering login list images
- Expand login list height for more visible rows
- Increase login list height again for larger scroll area
- Let the login card and account list expand by removing height caps and increasing default window height
- Guard stylesheet resource lookups and log missing stylesheets before applying them
34|2026-01-27 22:03:53|agent:codex|scope:ui-login-scroll-space|Expand login window and list area for more visible rows|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/DatingApp.java;src/main/java/datingapp/ui/NavigationService.java;AGENTS.md
<!--/ARCHIVE-->
<!--ARCHIVE:36:agent:codex:ui-login-top-spacing-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Skip placeholder avatar URLs when rendering login list images
- Expand login list height for more visible rows
- Increase login list height again for larger scroll area
- Let the login card and account list expand by removing height caps and increasing default window height
- Rebalance login window and list height so actions remain visible without collapsing the list
- Guard stylesheet resource lookups and log missing stylesheets before applying them
35|2026-01-27 22:06:57|agent:codex|scope:ui-login-scroll-balance|Reduce login window height and list size to keep actions visible|src/main/resources/fxml/login.fxml;src/main/java/datingapp/ui/DatingApp.java;src/main/java/datingapp/ui/NavigationService.java;AGENTS.md
<!--/ARCHIVE-->
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
- Polish login screen visuals and allow double-click login from user list
- Clear login list cell text on reuse to avoid stale content
- Add login search filtering, keyboard navigation, and dynamic empty-state hints
- Show avatars, completion/last-active badges, and selection animation in login list
- Tighten login layout spacing and width constraints for cleaner alignment
- Skip placeholder avatar URLs when rendering login list images
- Expand login list height for more visible rows
- Increase login list height again for larger scroll area
- Let the login card and account list expand by removing height caps and increasing default window height
- Rebalance login window and list height so actions remain visible without collapsing the list
- Reduce top padding and header icon size to reclaim vertical space
- Guard stylesheet resource lookups and log missing stylesheets before applying them
36|2026-01-27 22:11:35|agent:codex|scope:ui-login-top-spacing|Trim login header padding to reduce top whitespace|src/main/resources/fxml/login.fxml;AGENTS.md

## Logging Standards

**Level Usage:**
- **DEBUG**: Development diagnostics (disable in prod)
- **INFO**: User-facing messages, state changes
- **WARN**: Recoverable issues (missing optional fields)
- **ERROR**: Unrecoverable errors (shouldn't happen)

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

// 2. Weighted average (configurable)
double weighted = distanceScore * config.distanceWeight()
               + ageScore * config.ageWeight();

// 3. Normalize to 0-100 range
int score = (int) Math.round(weighted * 100);
```

**Multi-Step Workflow:**
```java
public Result complexOperation(Input input) {
    // 1. Validate
    if (!input.isValid()) {
        throw new IllegalArgumentException("Invalid input");
    }

    // 2. Load data
    User user = userStorage.get(input.userId());
    if (user == null) {
        return Result.error("User not found");
    }

    // 3. Process
    Processed processed = processData(user, input);

    // 4. Save
    storage.save(processed);

    // 5. Side effects (notifications, stats)
    notifyUser(user);
    updateStats(processed);

    return Result.success(processed);
}
```

## Code Quality Checklist

Before committing changes, verify:

**Architecture:**
- [ ] No framework imports in core/
- [ ] Interfaces in core/, implementations in storage/
- [ ] All dependencies via constructor injection
- [ ] Service uses only storage interfaces

**Implementation:**
- [ ] All constructors validate with `Objects.requireNonNull()`
- [ ] State transitions validated with `IllegalStateException`
- [ ] Collections defensively copied on return
- [ ] `updatedAt` timestamps updated on changes
- [ ] Storage exceptions wrapped in `StorageException`

**Testing:**
- [ ] Unit tests for new business logic
- [ ] In-memory mocks (no Mockito)
- [ ] `@DisplayName` on all tests
- [ ] `@Nested` for logical grouping
- [ ] Edge cases covered
- [ ] 80%+ line coverage

**CLI/UI:**
- [ ] No exceptions thrown to user
- [ ] User-friendly error messages
- [ ] Input validation with try/catch
- [ ] Confirmation messages logged
- [ ] Used `CliConstants` for display text

**Database:**
- [ ] Prepared statements for all SQL
- [ ] Try-with-resources for connections
- [ ] Nulls handled with `rs.wasNull()`
- [ ] Schema migration in constructor
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

**Last Updated:** 2026-01-30
**Phase:** 2.2 (file-consolidation complete: 133 Java files)
**Repository:** https://github.com/TomShtern/Date_Program.git
**Total Java Files:** 133 in `src/` (97 main + 36 test files)
**Test Coverage:** 60% minimum (JaCoCo enforced, excludes ui/ and cli/)




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
2|2026-01-20 00:00:00|agent:github_copilot|ui-utils|Consolidate UI animations; archive unused helpers|src/main/java/datingapp/ui/util/UiAnimations.java;src/main/java/datingapp/ui/util/ValidationHelper.java;src/main/java/datingapp/ui/controller/ProfileController.java;docs/archived-utils/*
3|2026-01-20 00:00:00|agent:github_copilot|core-sweep|Simplify daily limits, picks, and pending liker filtering|src/main/java/datingapp/core/DailyLimitService.java;src/main/java/datingapp/core/DailyPickService.java;src/main/java/datingapp/core/LikerBrowserService.java
4|2026-01-23 23:46:26|agent:github_copilot|core-plan|Add core consolidation plan doc|docs/core-consolidation-plan.md
5|2026-01-24 14:45:00|agent:github_copilot|core-consolidation|Nest TransitionValidationException; move MatchQualityConfig/InterestMatcher into MatchQualityService; move GeoUtils into CandidateFinder and drop CandidateFinderService; update usages/tests|src/main/java/datingapp/core/RelationshipTransitionService.java;src/main/java/datingapp/core/MatchQualityService.java;src/main/java/datingapp/core/CandidateFinder.java;src/main/java/datingapp/core/DailyPickService.java;src/main/java/datingapp/core/ServiceRegistry.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/cli/ProfileHandler.java;src/main/java/datingapp/cli/RelationshipHandler.java;src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;src/test/java/datingapp/core/MatchQualityConfigTest.java;src/test/java/datingapp/core/InterestMatcherTest.java;src/test/java/datingapp/core/GeoUtilsTest.java;docs/core-consolidation-plan.md
6|2026-01-24 15:10:00|agent:github_copilot|core-achievement|Nest UserAchievement into Achievement; update storage, CLI/UI, tests; remove old file|src/main/java/datingapp/core/Achievement.java;src/main/java/datingapp/core/AchievementService.java;src/main/java/datingapp/core/UserAchievementStorage.java;src/main/java/datingapp/storage/H2UserAchievementStorage.java;src/main/java/datingapp/cli/MatchingHandler.java;src/main/java/datingapp/cli/ProfileHandler.java;src/main/java/datingapp/cli/StatsHandler.java;src/main/java/datingapp/ui/viewmodel/StatsViewModel.java;src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java;src/test/java/datingapp/core/UserAchievementTest.java;src/test/java/datingapp/core/AchievementServiceTest.java;docs/core-consolidation-plan.md
7|2026-01-24 15:45:00|agent:github_copilot|core-social|Consolidate FriendRequest + Notification into Social; update storage/CLI/tests; remove old files|src/main/java/datingapp/core/Social.java;src/main/java/datingapp/core/FriendRequestStorage.java;src/main/java/datingapp/core/NotificationStorage.java;src/main/java/datingapp/core/RelationshipTransitionService.java;src/main/java/datingapp/cli/RelationshipHandler.java;src/main/java/datingapp/storage/H2FriendRequestStorage.java;src/main/java/datingapp/storage/H2NotificationStorage.java;src/test/java/datingapp/core/RelationshipTransitionServiceTest.java;docs/core-consolidation-plan.md
8|2026-01-24 16:05:00|agent:github_copilot|core-stats|Consolidate UserStats + PlatformStats into Stats; update storage/service/CLI/tests; remove old files|src/main/java/datingapp/core/Stats.java;src/main/java/datingapp/core/UserStatsStorage.java;src/main/java/datingapp/core/PlatformStatsStorage.java;src/main/java/datingapp/core/StatsService.java;src/main/java/datingapp/cli/StatsHandler.java;src/main/java/datingapp/storage/H2UserStatsStorage.java;src/main/java/datingapp/storage/H2PlatformStatsStorage.java;src/test/java/datingapp/core/UserStatsTest.java;src/test/java/datingapp/core/PlatformStatsTest.java;docs/core-consolidation-plan.md
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
---AGENT-LOG-END---
