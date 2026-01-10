# AGENTS.md - AI Agent Development Guide

**Project**: Dating App (Java 21 Console Application)
**Phase**: 1.5
**Last Updated**: 2026-01-10
**Purpose**: Comprehensive guide for AI agents working on this codebase

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Project Architecture](#project-architecture)
3. [Coding Standards](#coding-standards)
4. [Testing Standards](#testing-standards)
5. [Build & Quality Tools](#build--quality-tools)
6. [Documentation Standards](#documentation-standards)
7. [Common Workflows](#common-workflows)
8. [Critical Rules & Guardrails](#critical-rules--guardrails)
9. [File Organization](#file-organization)

---

## Quick Start

### Essential Commands
```bash
# Compile
mvn compile

# Run application
mvn exec:java

# Run tests
mvn test

# Format code (REQUIRED before committing)
mvn spotless:apply

# Build fat JAR
mvn package

# Full build with quality checks
mvn clean verify package
```

### First-Time Setup
1. **Read architecture**: Review `docs/architecture.md` and `CLAUDE.md`
2. **Understand layers**: `core/` (business logic), `storage/` (database), `cli/` (UI)
3. **Run tests**: `mvn test` to ensure everything works
4. **Check formatting**: `mvn spotless:check` to verify code style

### Key Files to Know
- `CLAUDE.md` - Comprehensive project instructions
- `docs/architecture.md` - Visual architecture with Mermaid diagrams
- `pom.xml` - Build configuration and dependencies
- `src/main/java/datingapp/Main.java` - Application entry point

---

## Project Architecture

### Three-Layer Design

```
datingapp/
├── core/          Pure Java business logic (NO framework/database imports)
│   ├── domain models (User, Match, Like, etc.)
│   ├── services (MatchingService, DailyPickService, etc.)
│   ├── storage interfaces (UserStorage, LikeStorage, etc.)
│   └── utilities (GeoUtils, AppConfig, ServiceRegistry)
├── storage/       H2 database implementations
│   ├── DatabaseManager (singleton)
│   ├── H2*Storage classes (10 implementations)
│   └── StorageException
└── cli/           Console UI handlers
    ├── handlers (ProfileHandler, MatchingHandler, etc.)
    ├── UserSession (current user tracking)
    ├── InputReader (I/O abstraction)
    └── CliConstants (UI strings)
```

### Architectural Rules (NON-NEGOTIABLE)

1. **Core Purity**: The `core/` package MUST have ZERO framework or database imports
2. **Interface Definition**: Storage interfaces defined in `core/`, implemented in `storage/`
3. **Dependency Injection**: Constructor injection ONLY - all dependencies via constructors
4. **Immutability**: Use records for immutable data (Like, Block, Report, MatchQuality)
5. **State Machines**: Mutable entities (User, Match, SwipeSession) have explicit state transitions
6. **Service Purity**: Core services depend only on interfaces, never implementations

### Key Components

**Domain Models (14 classes):**
- `User` - Mutable entity with state machine (INCOMPLETE → ACTIVE ↔ PAUSED → BANNED)
- `Like` - Immutable record with direction (LIKE/PASS)
- `Match` - Mutable entity with state (ACTIVE → UNMATCHED | BLOCKED)
- `Block`, `Report`, `UserAchievement` - Immutable records
- `Interest` - Enum (37 interests across 6 categories)
- `Achievement` - Enum (11 achievements across 4 categories)
- `SwipeSession`, `MatchQuality`, `Dealbreakers`, `Lifestyle`, `UserStats`, `PlatformStats`

**Core Services (14 services):**
- `CandidateFinder` - 7-stage filter pipeline for candidate discovery
- `MatchingService` - Like recording and match creation
- `DailyPickService` - Serendipitous daily discovery (deterministic)
- `AchievementService` - Gamification system
- `UndoService`, `DailyLimitService`, `SessionService`, `MatchQualityService`, etc.

**Storage Layer (12 classes):**
- `DatabaseManager` - H2 connection singleton
- `H2UserStorage`, `H2LikeStorage`, etc. (10 implementations)
- `StorageException` - RuntimeException wrapper

**CLI Layer (8 classes):**
- `Main` - Orchestrator (in `datingapp/` package, NOT `cli/`)
- 5 handlers: `UserManagementHandler`, `ProfileHandler`, `MatchingHandler`, `SafetyHandler`, `StatsHandler`
- `UserSession`, `InputReader`, `CliConstants`

---

## Coding Standards

### Naming Conventions

**Classes:**
```java
// Domain models: Singular, clear entity names
User, Match, Like, Block, Report

// Services: Suffix with "Service"
MatchingService, DailyPickService, AchievementService

// Storage interfaces: Suffix with "Storage"
UserStorage, LikeStorage, MatchStorage

// Storage implementations: Prefix with database type
H2UserStorage, H2LikeStorage, H2MatchStorage

// Handlers: Suffix with "Handler"
ProfileHandler, MatchingHandler, SafetyHandler

// Utilities: Descriptive names
GeoUtils, InterestMatcher
```

**Methods:**
```java
// Factory methods
create(), of(), fromDatabase(), builder()

// Getters/Setters
getId(), getState(), getName()
setName(), setBio(), setLocation()

// Predicates (boolean-returning)
isComplete(), hasAnyDealbreaker(), canLike(), involves(), isActive()

// State transitions
activate(), pause(), ban(), unmatch(), block()
```

**Variables:**
```java
// Specific entity references
userId, matchId, likeId

// Ordered pairs
userA, userB  // when ordering matters

// Directional clarity
perspectiveUserId, otherUserId

// Collections
alreadyInteracted, blockedUsers, sharedInterests
```

### Code Organization Patterns

**Service Structure (REQUIRED):**
```java
public class ServiceName {
  // Immutable dependencies (final)
  private final DependencyA dependencyA;
  private final DependencyB dependencyB;

  // Constructor with all dependencies
  public ServiceName(DependencyA a, DependencyB b) {
    this.dependencyA = Objects.requireNonNull(a);
    this.dependencyB = Objects.requireNonNull(b);
  }

  // Public business logic methods
  public ResultType publicMethod(InputType input) {
    // Implementation
  }

  // Private helper methods
  private ResultType privateHelper() {
    // Implementation
  }
}
```

**Handler Pattern (CLI):**
```java
public class ProfileHandler {
  private final UserStorage userStorage;
  private final ProfilePreviewService profilePreviewService;
  private final UserSession userSession;
  private final InputReader inputReader;

  // All dependencies via constructor
  public ProfileHandler(
      UserStorage userStorage,
      ProfilePreviewService profilePreviewService,
      UserSession userSession,
      InputReader inputReader) {
    this.userStorage = Objects.requireNonNull(userStorage);
    this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
    this.userSession = Objects.requireNonNull(userSession);
    this.inputReader = Objects.requireNonNull(inputReader);
  }

  // Public entry points
  public void completeProfile() { ... }

  // Private helpers
  private void promptBio(User currentUser) { ... }
}
```

### Immutability Patterns

**Records (for immutable data):**
```java
public record Like(
    UUID id, UUID whoLikes, UUID whoGotLiked,
    Direction direction, Instant createdAt) {

  // Compact constructor for validation
  public Like {
    Objects.requireNonNull(id, "id cannot be null");
    if (whoLikes.equals(whoGotLiked)) {
      throw new IllegalArgumentException("Cannot like yourself");
    }
  }

  // Factory method
  public static Like create(UUID whoLikes, UUID whoGotLiked, Direction direction) {
    return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, Instant.now());
  }
}
```

**Mutable Classes (for entities with state):**
```java
public class Match {
  private final String id;      // Immutable
  private final UUID userA;     // Immutable
  private State state;          // Mutable with state machine

  // State transition with validation
  public void unmatch(UUID userId) {
    if (this.state != State.ACTIVE) {
      throw new IllegalStateException("Match is not active");
    }
    if (!involves(userId)) {
      throw new IllegalArgumentException("User is not part of this match");
    }
    this.state = State.UNMATCHED;
    this.endedAt = Instant.now();
    this.endedBy = userId;
  }
}
```

**Touch Pattern (timestamp updates):**
```java
public class User {
  private Instant updatedAt;

  public void setName(String name) {
    this.name = Objects.requireNonNull(name);
    touch();  // Update timestamp on every change
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }
}
```

### Error Handling

**Validation in Constructors:**
```java
public record Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
  public Block {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(blockerId, "blockerId cannot be null");
    Objects.requireNonNull(blockedId, "blockedId cannot be null");
    if (blockerId.equals(blockedId)) {
      throw new IllegalArgumentException("Cannot block yourself");
    }
  }
}
```

**Setter Validation:**
```java
public void setMaxDistanceKm(int maxDistanceKm) {
  if (maxDistanceKm < 1) {
    throw new IllegalArgumentException("maxDistanceKm must be at least 1");
  }
  this.maxDistanceKm = maxDistanceKm;
  touch();
}
```

**Storage Exception Wrapping:**
```java
public void save(Like like) {
  try (Connection conn = dbManager.getConnection();
      PreparedStatement stmt = conn.prepareStatement(sql)) {
    // SQL operations
  } catch (SQLException e) {
    throw new StorageException("Failed to save like: " + like.id(), e);
  }
}
```

**CLI Error Handling (no exceptions to user):**
```java
private void editAgeDealbreaker(User currentUser, Dealbreakers current) {
  String input = inputReader.readLine("Max years: ");
  try {
    int age = Integer.parseInt(input);
    // Update dealbreaker
    logger.info("✅ Age dealbreaker updated.\n");
  } catch (NumberFormatException e) {
    logger.info("❌ Invalid input.\n");  // User-friendly, no stack trace
  }
}
```

### Documentation Style

**JavaDoc on Public APIs:**
```java
/**
 * Records a like action and checks for mutual match.
 *
 * @param like The like to record
 * @return The created Match if mutual like exists, empty otherwise
 */
public Optional<Match> recordLike(Like like) { ... }
```

**Inline Comments for Complex Logic:**
```java
// Use date + seeker ID hash as deterministic seed for daily pick
long seed = today.toEpochDay() + seeker.getId().hashCode();
Random random = new Random(seed);
```

**Phase Annotations:**
```java
// Interests (Phase 1.5 feature)
private Set<Interest> interests = EnumSet.noneOf(Interest.class);
```

### Special Patterns

**Deterministic Seeding:**
```java
// Same user gets same daily pick all day
long seed = today.toEpochDay() + seeker.getId().hashCode();
Random random = new Random(seed);
```

**Switch Expressions for Routing:**
```java
return switch (achievement) {
  case FIRST_SPARK -> getMatchCount(userId) >= 1;
  case SOCIAL_BUTTERFLY -> getMatchCount(userId) >= 5;
  case POPULAR -> getMatchCount(userId) >= 10;
  // ...
};
```

**Defensive Copying:**
```java
public Set<Interest> getInterests() {
  return interests.isEmpty()
      ? EnumSet.noneOf(Interest.class)
      : EnumSet.copyOf(interests);  // Always return defensive copy
}
```

---

## Testing Standards

### Test Directory Structure

```
src/test/java/datingapp/
├── core/                    Unit tests (pure Java, no database)
│   ├── UserTest.java
│   ├── MatchingServiceTest.java
│   ├── CandidateFinderTest.java
│   ├── DailyPickServiceTest.java
│   └── ... (27+ test classes)
└── storage/                 Integration tests (with H2 database)
    ├── H2StorageIntegrationTest.java
    └── H2DailyPickViewStorageTest.java
```

### Test Naming

**Class Names:** `{ClassName}Test.java`

**Method Names with @DisplayName:**
```java
@Test
@DisplayName("Excludes self from candidates")
void excludesSelf() { ... }

@Test
@DisplayName("Mutual likes create match")
void mutualLikesCreateMatch() { ... }
```

### Test Organization

**@Nested Classes for Logical Grouping:**
```java
@SuppressWarnings("unused")  // IDE false positives for @Nested
class MatchingServiceTest {

  @Nested
  @DisplayName("Recording Likes")
  class LikeProcessing {
    @Test void firstLikeDoesNotCreateMatch() { ... }
    @Test void mutualLikesCreateMatch() { ... }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {
    @Test void likePassLikeNoMatch() { ... }
    @Test void orderDoesNotMatter() { ... }
  }
}
```

**@BeforeEach Setup:**
```java
private LikeStorage likeStorage;
private MatchStorage matchStorage;
private MatchingService matchingService;

@BeforeEach
void setUp() {
  likeStorage = new InMemoryLikeStorage();
  matchStorage = new InMemoryMatchStorage();
  matchingService = new MatchingService(likeStorage, matchStorage);
}
```

### Mocking Strategy

**Use In-Memory Implementations (NO Mockito):**
```java
private static class InMemoryLikeStorage implements LikeStorage {
  private final Map<String, Like> likes = new HashMap<>();

  @Override
  public void save(Like like) {
    String key = like.whoLikes() + "_" + like.whoGotLiked();
    likes.put(key, like);
  }

  @Override
  public boolean exists(UUID from, UUID to) {
    String key = from + "_" + to;
    return likes.containsKey(key);
  }

  // Implement only methods needed for tests
}
```

### Test Data Helpers

```java
// Factory methods for test users
private User createCompleteUser(String name) {
  User user = new User(UUID.randomUUID(), name);
  user.setBio("Bio");
  user.setBirthDate(LocalDate.of(1990, 1, 1));
  user.setGender(User.Gender.MALE);
  user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
  user.setLocation(32.0, 34.0);
  user.setMaxDistanceKm(50);
  user.setAgeRange(20, 40);
  user.addPhotoUrl("photo.jpg");
  return user;
}
```

### Integration Tests

**Database Setup:**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2StorageIntegrationTest {

  @BeforeAll
  static void setUpOnce() {
    // Unique test database
    DatabaseManager.setJdbcUrl("jdbc:h2:./data/dating_test_" + UUID.randomUUID());
    DatabaseManager.resetInstance();
    dbManager = DatabaseManager.getInstance();
    userStorage = new H2UserStorage(dbManager);
  }

  @AfterAll
  static void tearDown() {
    if (dbManager != null) dbManager.shutdown();
  }
}
```

### Running Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=CandidateFinderTest

# Single test method
mvn test -Dtest=CandidateFinderTest#excludesSelf
```

---

## Build & Quality Tools

### Maven Build Configuration

**Java Version**: Java 21 (Release 21)
**Encoding**: UTF-8
**Main Class**: `datingapp.Main`

### Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| H2 Database | 2.2.224 | Embedded database |
| JUnit Jupiter | 5.10.2 | Testing framework |
| SLF4J API | 2.0.12 | Logging facade |
| Logback Classic | 1.5.3 | Logging implementation |

### Code Formatting (Spotless)

**Plugin**: `spotless-maven-plugin` v3.1.0
**Formatter**: Google Java Format v1.33.0
**Status**: ✅ ENFORCED (build fails if violated)

```bash
# Check formatting
mvn spotless:check

# Auto-fix formatting (REQUIRED before committing)
mvn spotless:apply
```

**Standards:**
- 100-character line limit
- 2-space indentation (no tabs)
- Google Java style
- Trailing whitespace removed
- Files end with newline

### Code Style (Checkstyle)

**Plugin**: `maven-checkstyle-plugin` v3.3.1
**Config**: Google Checks (`google_checks.xml`)
**Status**: ⚠️ NON-BLOCKING (warnings logged)

**Key Rules:**
- PascalCase for classes, camelCase for methods/variables
- No star imports (`import java.util.*`)
- Import ordering: static first, then third-party
- One top-level class per file
- Braces required on all control structures
- Egyptian braces (left brace on same line)
- JavaDoc for public APIs

### Code Quality (PMD)

**Plugin**: `maven-pmd-plugin` v3.21.2
**Ruleset**: Quickstart
**Status**: ⚠️ NON-BLOCKING (warnings logged)

**Checks:**
- Dead code detection
- Code duplication (>100 tokens)
- Empty catch blocks
- Unused variables
- Method complexity

### Logging Configuration

**Framework**: Logback v1.5.3
**Location**: `src/main/resources/logback.xml`

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%msg%n</pattern>  <!-- Messages only, no timestamps -->
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Build Commands Reference

```bash
# Development
mvn compile              # Compile only
mvn test                 # Run all tests
mvn clean               # Clean build artifacts

# Quality Checks
mvn verify              # Full build + all quality checks
mvn spotless:check      # Check formatting only
mvn spotless:apply      # Auto-fix formatting ⭐ USE THIS
mvn checkstyle:check    # Run Checkstyle only
mvn pmd:check           # Run PMD only

# Packaging & Execution
mvn package             # Build fat JAR
mvn exec:java           # Run via exec plugin
java -jar target/dating-app-1.0.0-shaded.jar  # Run packaged JAR

# Complete Pipeline
mvn clean verify package  # Full clean build with all checks
```

---

## Documentation Standards

### Document Hierarchy

1. **README.md** - Quick start (<50 lines, essentials only)
2. **CLAUDE.md** - Comprehensive project instructions
3. **docs/architecture.md** - Visual architecture with Mermaid
4. **docs/plans/** - Feature design documents

### README.md Standards

- Keep under 50 lines
- Include: Prerequisites, Running, Testing, Formatting
- Use tested commands (not pseudocode)
- Link to CLAUDE.md for details

### CLAUDE.md Standards

**Required Sections:**
1. Build & Run Commands
2. Architecture (two-layer design)
3. Key Components (domain, services, storage)
4. Data Flows (numbered workflows)
5. Configuration Reference
6. Recent Updates (timestamped)
7. Testing Strategy
8. Known Limitations

**Format:**
- Always end with "Last Updated: YYYY-MM-DD" and "Phase: X.X"
- Use bold for state machines (e.g., `INCOMPLETE → ACTIVE`)
- Document defaults in parentheses
- Update "Recent Updates" section with changes

### Feature Design Documents

**Header:**
```markdown
# Feature N: [Title]

**Priority:** High/Medium/Low
**Complexity:** Low/Medium/High
**Dependencies:** [List or None]
**Status:** ✅ COMPLETE / ⚠️ IN PROGRESS / 📋 PLANNED
```

**Required Sections:**
1. Overview
2. User Stories
3. AI Agent Implementation Guardrails (use `> [!CAUTION]` blocks)
4. Implementation Order
5. Proposed Changes (grouped by layer: Core, Storage, CLI)
6. Test Plan
7. Success Criteria
8. Last Updated (with timestamp)

**Code Standards:**
- Complete, compilable code (not pseudocode)
- File locations explicit
- Mark changes: [NEW], [MODIFY], [DELETE]
- Include validation checklists

### Emoji Usage

**Status:**
- ✅ Complete/Implemented
- ⚠️ In Progress/Needs Attention
- 📋 Planned
- 🏗️ Architecture/Building

**Categories:**
- 🎯 Goals/Objectives
- 🔧 Configuration/Tools
- 🏆 Achievements/Gamification
- 🔒 Security/Safety

### Comments in Code

**When to Comment:**
- State machine transitions
- Complex algorithms (with formula)
- Non-obvious business logic
- Public methods (JavaDoc)

**When NOT to Comment:**
- Self-documenting code
- Simple getters/setters
- Logic that belongs in docs

---

## Common Workflows

### Adding a New Feature

1. **Plan**: Create feature design doc in `docs/plans/phase-X-features/`
2. **Core Layer**: Add domain models, services, storage interfaces
3. **Storage Layer**: Implement H2*Storage classes
4. **CLI Layer**: Add handler methods or new handler
5. **Tests**: Write unit tests (core/) and integration tests (storage/)
6. **Format**: Run `mvn spotless:apply`
7. **Verify**: Run `mvn clean verify`
8. **Document**: Update CLAUDE.md and architecture.md

### Adding a New Domain Model

**Immutable (Record):**
```java
package datingapp.core;

public record NewModel(UUID id, String field, Instant createdAt) {
  public NewModel {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(field, "field cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
  }

  public static NewModel create(String field) {
    return new NewModel(UUID.randomUUID(), field, Instant.now());
  }
}
```

**Mutable (Class with State):**
```java
package datingapp.core;

public class NewEntity {
  private final UUID id;
  private final Instant createdAt;
  private Instant updatedAt;
  private String field;
  private State state;

  public enum State { PENDING, ACTIVE, INACTIVE }

  private NewEntity(UUID id, Instant createdAt) {
    this.id = id;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
    this.state = State.PENDING;
  }

  public static NewEntity create(String field) {
    NewEntity entity = new NewEntity(UUID.randomUUID(), Instant.now());
    entity.field = field;
    return entity;
  }

  public void setField(String field) {
    this.field = Objects.requireNonNull(field);
    touch();
  }

  public void activate() {
    if (state != State.PENDING) {
      throw new IllegalStateException("Cannot activate from state: " + state);
    }
    this.state = State.ACTIVE;
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  // Getters with defensive copying for collections
}
```

### Adding a New Service

```java
package datingapp.core;

public class NewService {
  private final Dependency1 dep1;
  private final Dependency2 dep2;

  public NewService(Dependency1 dep1, Dependency2 dep2) {
    this.dep1 = Objects.requireNonNull(dep1);
    this.dep2 = Objects.requireNonNull(dep2);
  }

  public ResultType businessMethod(InputType input) {
    // Implementation with validation
    Objects.requireNonNull(input, "input cannot be null");

    // Business logic

    return result;
  }
}
```

### Adding a New Storage Interface

```java
package datingapp.core;

public interface NewStorage {
  void save(NewModel model);
  Optional<NewModel> get(UUID id);
  List<NewModel> findAll();
}
```

### Implementing Storage

```java
package datingapp.storage;

public class H2NewStorage implements NewStorage {
  private final DatabaseManager dbManager;

  public H2NewStorage(DatabaseManager dbManager) {
    this.dbManager = Objects.requireNonNull(dbManager);
  }

  @Override
  public void save(NewModel model) {
    String sql = "MERGE INTO new_table (id, field, created_at) VALUES (?, ?, ?)";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, model.id());
      stmt.setString(2, model.field());
      stmt.setTimestamp(3, Timestamp.from(model.createdAt()));
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new StorageException("Failed to save NewModel: " + model.id(), e);
    }
  }

  // Implement other methods
}
```

### Adding Tests

**Unit Test:**
```java
package datingapp.core;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")  // IDE warning suppression
class NewServiceTest {

  private NewStorage storage;
  private NewService service;

  @BeforeEach
  void setUp() {
    storage = new InMemoryNewStorage();
    service = new NewService(storage);
  }

  @Nested
  @DisplayName("Business Method Tests")
  class BusinessMethodTests {

    @Test
    @DisplayName("Valid input produces expected result")
    void validInput() {
      // Arrange
      InputType input = createValidInput();

      // Act
      ResultType result = service.businessMethod(input);

      // Assert
      assertNotNull(result);
      assertEquals(expectedValue, result.getValue());
    }
  }

  // In-memory mock storage
  private static class InMemoryNewStorage implements NewStorage {
    private final Map<UUID, NewModel> storage = new HashMap<>();

    @Override
    public void save(NewModel model) {
      storage.put(model.id(), model);
    }

    // Implement other methods
  }
}
```

**Integration Test:**
```java
package datingapp.storage;

import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2NewStorageTest {

  private static DatabaseManager dbManager;
  private static H2NewStorage storage;

  @BeforeAll
  static void setUpOnce() {
    DatabaseManager.setJdbcUrl("jdbc:h2:./data/test_" + UUID.randomUUID());
    DatabaseManager.resetInstance();
    dbManager = DatabaseManager.getInstance();
    storage = new H2NewStorage(dbManager);
  }

  @AfterAll
  static void tearDown() {
    if (dbManager != null) dbManager.shutdown();
  }

  @Test
  @DisplayName("Data survives save and load")
  void persistenceRoundTrip() {
    NewModel model = NewModel.create("test");
    storage.save(model);

    Optional<NewModel> loaded = storage.get(model.id());

    assertTrue(loaded.isPresent());
    assertEquals(model.field(), loaded.get().field());
  }
}
```

---

## Critical Rules & Guardrails

### ❌ NEVER DO THIS

1. **Import frameworks/databases in `core/`**
   - VIOLATION: `import java.sql.*` in core package
   - WHY: Breaks architectural boundary
   - FIX: Use storage interfaces only

2. **Skip constructor validation**
   - VIOLATION: `this.dep = dep;` without null check
   - WHY: NPE at runtime
   - FIX: `this.dep = Objects.requireNonNull(dep);`

3. **Throw exceptions in CLI layer**
   - VIOLATION: `throw new IllegalArgumentException()` in handler
   - WHY: Crashes user experience
   - FIX: Log user-friendly message, continue gracefully

4. **Use implementation in service**
   - VIOLATION: `new H2UserStorage()` in core service
   - WHY: Tight coupling, can't test
   - FIX: Inject `UserStorage` interface

5. **Forget to format before committing**
   - VIOLATION: Committing without `mvn spotless:apply`
   - WHY: CI/CD will fail
   - FIX: Always run `mvn spotless:apply` first

6. **Create mutable records**
   - VIOLATION: `public record User(UUID id) { public void setId(...) }`
   - WHY: Records are immutable by design
   - FIX: Use a class for mutable entities

7. **Skip state validation in transitions**
   - VIOLATION: `this.state = State.ACTIVE;` without checking current state
   - WHY: Invalid state transitions
   - FIX: Validate current state before transition

8. **Return direct collection references**
   - VIOLATION: `return this.interests;`
   - WHY: External mutation of internal state
   - FIX: `return EnumSet.copyOf(interests);`

9. **Use Mockito or external mocking**
   - VIOLATION: `@Mock UserStorage storage`
   - WHY: Project uses in-memory implementations
   - FIX: Create `InMemory*Storage` class in test

10. **Update documentation without timestamp**
    - VIOLATION: Changing CLAUDE.md without "Last Updated"
    - WHY: Can't track documentation freshness
    - FIX: Always update timestamp and phase

### ✅ ALWAYS DO THIS

1. **Run `mvn spotless:apply` before committing**
2. **Validate all constructor parameters with `Objects.requireNonNull()`**
3. **Use defensive copying when returning collections**
4. **Add `@DisplayName` to test methods**
5. **Update `updatedAt` timestamp on entity changes (touch pattern)**
6. **Write complete, compilable code in documentation (not pseudocode)**
7. **Group related tests with `@Nested` classes**
8. **Use factory methods for object creation (`create()`, `of()`, `fromDatabase()`)**
9. **Check state before state transitions**
10. **Update CLAUDE.md "Recent Updates" section with changes**

### 🎯 Best Practices

1. **Favor immutability**: Use records unless state must change
2. **Keep services stateless**: All state via parameters or dependencies
3. **Make illegal states unrepresentable**: Use enums and state machines
4. **Test at boundaries**: Unit test business logic, integration test persistence
5. **Fail fast**: Validate early, close to the source
6. **Be explicit**: Clear names over clever code
7. **Document non-obvious**: Complex algorithms need comments
8. **Use switch expressions**: Modern Java for routing logic
9. **Prefer composition**: Inject dependencies, don't create them
10. **Think in layers**: Changes flow from core → storage → cli

---

## File Organization

### Source Tree
```
src/main/java/datingapp/
├── Main.java                 Application entry point (orchestrator)
├── core/                     Pure business logic
│   ├── User.java            (Mutable entity, state machine)
│   ├── Like.java            (Immutable record)
│   ├── Match.java           (Mutable entity, state machine)
│   ├── Block.java           (Immutable record)
│   ├── Report.java          (Immutable record)
│   ├── SwipeSession.java    (Mutable entity, session tracking)
│   ├── MatchQuality.java    (Immutable record, scoring)
│   ├── Dealbreakers.java    (Immutable record with builder)
│   ├── Interest.java        (Enum, 37 values)
│   ├── Achievement.java     (Enum, 11 values)
│   ├── UserAchievement.java (Immutable record)
│   ├── Lifestyle.java       (Utility with 5 nested enums)
│   ├── UserStats.java       (Immutable record with builder)
│   ├── PlatformStats.java   (Immutable record)
│   ├── CandidateFinderService.java  (Interface)
│   ├── CandidateFinder.java         (Implementation)
│   ├── MatchingService.java
│   ├── UndoService.java
│   ├── DailyLimitService.java
│   ├── SessionService.java
│   ├── MatchQualityService.java
│   ├── DealbreakersEvaluator.java
│   ├── ProfilePreviewService.java
│   ├── DailyPickService.java
│   ├── InterestMatcher.java         (Static utility)
│   ├── AchievementService.java
│   ├── ReportService.java
│   ├── StatsService.java
│   ├── UserStorage.java             (Interface)
│   ├── LikeStorage.java             (Interface)
│   ├── MatchStorage.java            (Interface)
│   ├── BlockStorage.java            (Interface)
│   ├── ReportStorage.java           (Interface)
│   ├── SwipeSessionStorage.java     (Interface)
│   ├── UserStatsStorage.java        (Interface)
│   ├── PlatformStatsStorage.java    (Interface)
│   ├── DailyPickStorage.java        (Interface)
│   ├── UserAchievementStorage.java  (Interface)
│   ├── GeoUtils.java                (Static utility)
│   ├── AppConfig.java               (Immutable record with builder)
│   ├── ServiceRegistry.java         (DI container)
│   ├── ServiceRegistryBuilder.java  (Factory)
│   └── MatchQualityConfig.java      (Immutable record)
├── storage/                  Database implementations
│   ├── DatabaseManager.java         (H2 singleton)
│   ├── StorageException.java        (RuntimeException)
│   ├── H2UserStorage.java
│   ├── H2LikeStorage.java
│   ├── H2MatchStorage.java
│   ├── H2BlockStorage.java
│   ├── H2ReportStorage.java
│   ├── H2SwipeSessionStorage.java
│   ├── H2UserStatsStorage.java
│   ├── H2PlatformStatsStorage.java
│   ├── H2DailyPickViewStorage.java
│   └── H2UserAchievementStorage.java
└── cli/                      Console UI
    ├── UserManagementHandler.java
    ├── ProfileHandler.java
    ├── MatchingHandler.java
    ├── SafetyHandler.java
    ├── StatsHandler.java
    ├── UserSession.java
    ├── InputReader.java
    └── CliConstants.java
```

### Test Tree
```
src/test/java/datingapp/
├── core/                     Unit tests (27+ test classes)
│   ├── UserTest.java
│   ├── MatchTest.java
│   ├── LikeTest.java
│   ├── MatchingServiceTest.java
│   ├── CandidateFinderTest.java
│   ├── DailyLimitServiceTest.java
│   ├── SessionServiceTest.java
│   ├── MatchQualityServiceTest.java
│   ├── DailyPickServiceTest.java
│   ├── AchievementServiceTest.java
│   ├── InterestMatcherTest.java
│   └── ... (and 15+ more)
└── storage/                  Integration tests
    ├── H2StorageIntegrationTest.java
    └── H2DailyPickViewStorageTest.java
```

### Documentation Tree
```
docs/
├── architecture.md           Visual architecture (Mermaid diagrams)
├── feature-suggestions.md    Backlog of ideas
├── agent-readiness-report.md Evaluation document
└── plans/                    Feature design docs
    ├── 2026-01-08-phase-0.5b-overview.md
    └── phase-1-features/
        ├── 01-daily-limits-feature.md
        ├── 02-undo-feature.md
        ├── 03-match-quality-scoring-feature.md
        ├── 04-interests-feature.md
        └── ... (more feature docs)
```

### Configuration Files
```
.
├── pom.xml                   Maven build config
├── README.md                 Quick start guide
├── CLAUDE.md                 Comprehensive project instructions
├── AGENTS.md                 This file (AI agent guide)
└── src/main/resources/
    └── logback.xml           Logging configuration
```

---

## Conclusion

This guide provides everything an AI agent needs to work effectively on this codebase. Key takeaways:

1. **Respect the layers**: Core is pure, storage implements, CLI displays
2. **Follow patterns**: Constructor injection, factory methods, state machines
3. **Test thoroughly**: Unit tests + integration tests = confidence
4. **Format always**: `mvn spotless:apply` before every commit
5. **Document changes**: Update CLAUDE.md and architecture.md
6. **Think in flows**: Understand data movement through layers
7. **Validate early**: Check inputs at boundaries
8. **Keep it simple**: Clear code beats clever code

When in doubt, reference:
- `CLAUDE.md` for architecture and features
- `docs/architecture.md` for visual structure
- `pom.xml` for build configuration
- This file for coding conventions

Happy coding! 🚀

---

**Last Updated**: 2026-01-10
**Phase**: 1.5
**Repository**: https://github.com/TomShtern/Date_Program.git
