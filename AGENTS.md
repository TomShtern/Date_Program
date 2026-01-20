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

## Essential Commands

```bash
# Build & Run
mvn compile                          # Compile source
mvn exec:java                        # Run CLI app
mvn javafx:run                       # Run JavaFX GUI app
mvn package                          # Build fat JAR

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
```

## Architecture Rules

**Two-Layer Clean Architecture:**
- `core/` - Pure Java business logic, NO framework/database imports
- `storage/` - H2 database implementations
- `cli/` - Console UI, `ui/` - JavaFX GUI

**Critical:** Storage interfaces defined in `core/`, implemented in `storage/`. Core services depend only on interfaces, never implementations.

**Dependency Injection:** Constructor injection only - all dependencies via constructors. Use `ServiceRegistryBuilder` to wire dependencies in production code.

## Code Style

**Formatting:** Palantir Java Format v2.39.0 (4-space indentation). Auto-apply with `mvn spotless:apply` before every commit.

**Imports:** No star imports (`import java.util.*`). Import ordering: static first, then third-party, then standard library.

**Naming Conventions:**
- Classes: PascalCase - `UserService`, `H2UserStorage`, `ProfileHandler`
- Methods: camelCase - `getUserById()`, `createMatch()`, `isComplete()`
- Predicates: `is`/`has`/`can` prefix - `isActive()`, `hasDealbreakers()`, `canLike()`
- Constants: UPPER_SNAKE_CASE - `MAX_DISTANCE_KM`, `DEFAULT_TIMEOUT`

**Types:**
- Use `record` for immutable data (`Like`, `Block`, `Report`, `MatchQuality`)
- Use `class` for mutable entities with state machines (`User`, `Match`)
- Use `enum` for fixed sets (`Interest`, `Achievement`)
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

**Mocking:** Create in-memory implementations inline in tests (NO Mockito). Example:
```java
private static class InMemoryUserStorage implements UserStorage {
    private final Map<UUID, User> users = new HashMap<>();
    // Implement only methods needed for tests
}
```

**Integration Tests:** Use real H2 database with unique test DB name per test class.

**Coverage:** Minimum 80% line coverage (enforced by JaCoCo)

## Special Patterns

**Match ID Generation:** Deterministic from sorted UUIDs - use `Match.generateId(userA, userB)` where ID is `userA_userB` with lexicographically smaller UUID first.

**Factory Methods:** `create()`, `of()`, `fromDatabase()` for object construction.

**Defensive Copying:** Always return defensive copies of collections: `return EnumSet.copyOf(interests);`

**Touch Pattern:** Update `updatedAt` timestamp on entity changes:
```java
public void setName(String name) {
    this.name = Objects.requireNonNull(name);
    touch();  // Updates updatedAt = Instant.now()
}
```

## File Locations

- Domain models: `src/main/java/datingapp/core/{User,Like,Match,Block,Report}.java`
- Storage interfaces: `src/main/java/datingapp/core/*Storage.java`
- Services: `src/main/java/datingapp/core/*Service.java`
- H2 implementations: `src/main/java/datingapp/storage/H2*.java`
- Service wiring: `src/main/java/datingapp/core/ServiceRegistryBuilder.java`
- CLI handlers: `src/main/java/datingapp/cli/*Handler.java`
- JavaFX UI: `src/main/java/datingapp/ui/*.java`

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
- Use `CliConstants` for all display strings
- Log with emojis for user-friendly feedback: ✅ ❌ ⚠️ 🎉
- Always save to storage after user edits
- Log confirmation messages
- Handle all NumberFormatExceptions gracefully

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

**Last Updated:** 2026-01-14
**Phase:** 1.5
**Repository:** https://github.com/TomShtern/Date_Program.git




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
2|2026-01-20 00:00:00|agent:github_copilot|ui-utils|Consolidate UI animations; archive unused helpers|src/main/java/datingapp/ui/util/UiAnimations.java;src/main/java/datingapp/ui/util/ValidationHelper.java;src/main/java/datingapp/ui/controller/ProfileController.java;docs/archived-utils/*
3|2026-01-20 00:00:00|agent:github_copilot|core-sweep|Simplify daily limits, picks, and pending liker filtering|src/main/java/datingapp/core/DailyLimitService.java;src/main/java/datingapp/core/DailyPickService.java;src/main/java/datingapp/core/LikerBrowserService.java
---AGENT-LOG-END---
