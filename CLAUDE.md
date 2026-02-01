<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1, 2) locate affected doc fragment, 3) archive with <!--ARCHIVE:SEQ:agent:scope-->,
# 4) apply minimal edits, 5) append ChangeStamp to file-end changelog and edited fragment.
<!--/AGENT-DOCSYNC-->

# CLAUDE.md

Guidance for Claude Code when working with this repository.

> **For AI Agents**: See [`AGENTS.md`](./AGENTS.md) for comprehensive coding standards, testing patterns, and quality tools.

## Quick Commands

```bash
# Build & Run
mvn compile                              # Compile
mvn exec:java                            # Run CLI app
mvn javafx:run                           # Run JavaFX GUI
mvn package                              # Build fat JAR
java -jar target/dating-app-1.0.0-shaded.jar  # Run from JAR (best terminal support)

# Testing
mvn test                                 # All tests
mvn test -Dtest=ClassName#methodName     # Single test

# Code Quality (REQUIRED before commit)
mvn spotless:apply                       # Auto-fix formatting
mvn spotless:check                       # Check formatting
mvn verify                               # Full build + all quality checks
mvn jacoco:report                        # Generate coverage report
```

## Architecture Overview

**Phase 2.1** console dating app: **Java 25** + Maven + H2 embedded DB. Features: matching, messaging, relationship transitions (Friend Zone/Graceful Exit), pace compatibility, achievements, interests matching.

**Current Stats (2026-01-30):** ~126 Java files, 581 tests passing, 60% coverage minimum.

### Package Structure

| Package    | Purpose                  | Rule                                |
|------------|--------------------------|-------------------------------------|
| `core/`    | Pure Java business logic | **ZERO** framework/database imports |
| `storage/` | JDBI declarative SQL     | Implements interfaces from `core/`  |
| `cli/`     | Console UI handlers      | Thin layer calling services         |
| `ui/`      | JavaFX UI (experimental) | Uses AtlantaFX theme                |

> **Exception:** `ServiceRegistry` is the composition root—the **ONLY** file in `core/` allowed to import storage implementations for dependency injection wiring.

### UI Controller Architecture (`ui/`)

All JavaFX controllers follow the **Action Handler Pattern** introduced in 2026-01-30:

| Component | Purpose | Location |
|-----------|---------|----------|
| `BaseController` | Abstract base with subscription lifecycle management | `ui/controller/` |
| `UiAnimations` | Reusable animation utilities (pulse, fade, shake, bounce) | `ui/util/` |
| `UiServices` | Toast notifications + ImageCache | `ui/util/` |
| `UiComponents` | Reusable UI component factories | `ui/component/` |

**Action Handler Wiring Pattern:**
```java
public class MyController extends BaseController {
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        wireActionHandlers();  // Centralized event binding
    }

    private void wireActionHandlers() {
        if (myButton != null) {
            myButton.setOnAction(event -> {
                event.consume();  // Prevent event bubbling
                handleMyAction();
            });
            myButton.disableProperty().bind(viewModel.loadingProperty());
        }
    }
}
```

**Key Patterns:**
- Null-check FXML elements before binding (some views may not have all elements)
- Always call `event.consume()` to prevent unintended propagation
- Bind disable properties to loading states for async operations
- Use `BaseController.addSubscription()` for listener cleanup

### Domain Models (`core/`)

| Model                 | Type      | Location / Key Fields                                                           |
|-----------------------|-----------|---------------------------------------------------------------------------------|
| `User`                | Mutable   | Own file; `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`                              |
| `Match`               | Mutable   | Own file; `ACTIVE → FRIENDS \| UNMATCHED \| GRACEFUL_EXIT \| BLOCKED`        |
| `Messaging.Message`   | Immutable | `Messaging.java`; max 1000 chars, sender, timestamp                             |
| `Messaging.Conversation` | Mutable | `Messaging.java`; deterministic ID (`userA_userB`), per-user read timestamps   |
| `Social.FriendRequest`| Immutable | `Social.java`; status: `PENDING → ACCEPTED \| DECLINED \| EXPIRED`           |
| `Social.Notification` | Immutable | `Social.java`; type, title, message, metadata, read status                      |
| `Stats.UserStats`     | Immutable | `Stats.java`; user activity metrics                                             |
| `Stats.MatchQuality`  | Immutable | `Stats.java`; score (0-100), star rating (1-5), highlights                      |
| `Preferences.Interest`| Enum      | `Preferences.java`; 39 interests in 6 categories                                |
| `Preferences.Lifestyle`| Records  | `Preferences.java`; Smoking, Drinking, WantsKids, etc.                          |
| `Preferences.PacePreferences` | Immutable | `Preferences.java`; messaging frequency, communication style            |
| `Achievement`         | Enum      | `Achievement.java`; 11 achievements in 4 categories                             |
| `SwipeSession`        | Mutable   | Own file; `ACTIVE → COMPLETED`, velocity tracking                              |
| `Dealbreakers`        | Immutable | Own file; lifestyle, physical, age filters + `Evaluator` inner class            |
| `UserInteractions`    | Container | `UserInteractions.java`; `Like`, `Block`, `Report` records + storage interfaces |

### Services (`core/`)

**Core**: `CandidateFinder` (7-stage filter), `MatchingService` (includes LikerBrowser, PaceCompatibility), `TrustSafetyService` (includes Verification, Reports)

**Phase 1**: `UndoService`, `DailyService` (merged Limits + Picks), `MatchQualityService`, `SessionService`, `ProfilePreviewService`, `StatsService`, `ValidationService`

**Phase 1.5+**: `AchievementService`, `ProfileCompletionService`

**Phase 2.0+**: `MessagingService`, `RelationshipTransitionService`

### Storage Interfaces (`core/storage/`)

Storage interfaces are **standalone files** in the `core/storage/` package:

| Interface | Purpose |
|-----------|----------|
| `UserStorage` | User CRUD and queries |
| `MatchStorage` | Match persistence |
| `LikeStorage` | Like/pass tracking |
| `BlockStorage` | Block lists |
| `MessagingStorage` | Conversations and messages |
| ... | 13 total interfaces |

All implementations use **JDBI declarative SQL** interfaces in `storage/jdbi/` (e.g., `JdbiUserStorage implements UserStorage`) with `@SqlQuery`/`@SqlUpdate` annotations. Data: `./data/dating.mv.db`

## Coding Standards Quick Reference

### Naming Conventions
| Element    | Convention              | Example                                        |
|------------|-------------------------|------------------------------------------------|
| Classes    | PascalCase              | `UserService`, `JdbiUserStorage`               |
| Methods    | camelCase               | `getUserById()`, `createMatch()`               |
| Predicates | `is`/`has`/`can` prefix | `isActive()`, `hasInterests()`, `canMessage()` |
| Constants  | UPPER_SNAKE             | `MAX_DISTANCE_KM`, `DEFAULT_TIMEOUT`           |
| Timestamps | `*At` suffix            | `createdAt`, `updatedAt`, `endedAt`            |

### Type Usage
| Use           | When                                             | Nested? Add `static`! |
|---------------|--------------------------------------------------|-----------------------|
| `record`      | Immutable data (Message, Like, Block)            | ✅ `public static record` |
| `class`       | Mutable entities with state (User, Match)        | ✅ `public static class` |
| `enum`        | Fixed sets with metadata (Interest, Achievement) | ✅ `public static enum` |
| `Optional<T>` | Nullable return values                           | N/A |

**⚠️ Nested Type Visibility:**
- **Rule:** Nested classes/records/interfaces MUST be `static` to be accessible outside their package
- **Why:** Non-static nested types are inner classes with implicit references to the enclosing instance—this makes them package-private regardless of `public` modifier
- **Error:** `"X is not public in Y; cannot be accessed from outside package"`
- **Fix:** Add `static` keyword: `public static record ProfileNote(...)`
- **Prevention:** Always write `public static` when nesting types (e.g., `User.ProfileNote`, `User.Storage`)
- **Exception:** Truly internal types never used outside the class (rare; usually interfaces/records should be static)

### Key Patterns

**Nested Storage Interface (NEW - Post-Consolidation):**
```java
public class DomainEntity {
    public interface Storage {
        void save(DomainEntity entity);
        DomainEntity get(UUID id);
    }
    // ... entity fields and methods
}
```

**Factory Methods:**
```java
public static Entity create(params) { return new Entity(UUID.randomUUID(), params, Instant.now()); }
public static Entity fromDatabase(allFields) { /* reconstruct from DB */ }
```

**Constructor Validation:**
```java
public Service(Storage storage) {
    this.storage = Objects.requireNonNull(storage, "storage cannot be null");
}
```

**Result Pattern (for operations that can fail):**
```java
public record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
```

**Defensive Copying:**
```java
public Set<Interest> getInterests() { return EnumSet.copyOf(interests); }  // Never return direct reference
```

**Touch Pattern (mutable entities):**
```java
private void touch() { this.updatedAt = Instant.now(); }
public void setName(String name) { this.name = name; touch(); }
```

**Deterministic IDs:**
```java
public static String generateId(UUID a, UUID b) {
    return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;  // Lexicographic ordering
}
```

## NEVER Do These (Critical Anti-Patterns)

- **Import framework/database in `core/`** - Zero coupling rule
- **Skip constructor null checks** - Always `Objects.requireNonNull()`
- **Return direct collection references** - Always defensive copy
- **Forget `static` on nested types** - Non-static nested classes/records/interfaces are NOT accessible outside the package (see "Nested Type Visibility" above)
- **Use Mockito** - Use in-memory implementations instead
- **Commit without `mvn spotless:apply`** - Formatting is enforced
- **Throw exceptions from CLI handlers** - Return user-friendly messages
- **Mix business logic in storage** - Storage does mapping only
- **Use `new ArrayList<>()` for EnumSets** - Use `EnumSet.noneOf()`
- **Write manual JDBC in storage** - Use JDBI declarative SQL interfaces

## Storage Layer Patterns (JDBI)

**Declarative SQL Interfaces:**
All storage implementations use JDBI interfaces with `@SqlQuery`/`@SqlUpdate` annotations:
```java
@RegisterRowMapper(UserMapper.class)
public interface JdbiUserStorage extends User.Storage {
    @SqlUpdate("MERGE INTO users (id, name, email) KEY (id) VALUES (:id, :name, :email)")
    @Override void save(@BindBean User user);

    @SqlQuery("SELECT * FROM users WHERE id = :id")
    @Override User get(@Bind("id") UUID id);
}
```

**Row Mappers (in `storage/mapper/`):**
```java
public class UserMapper implements RowMapper<User> {
    @Override
    public User map(ResultSet rs, StatementContext ctx) throws SQLException {
        return User.fromDatabase(
            MapperHelper.readUuid(rs, "id"),
            rs.getString("name"),
            MapperHelper.readInstant(rs, "created_at")
        );
    }
}
```

**Custom Type Handling:**
- `EnumSetArgumentFactory` - Serializes `EnumSet<Interest>` to CSV for storage
- `EnumSetColumnMapper` - Deserializes CSV back to `EnumSet<Interest>`
- `MapperHelper` - Null-safe ResultSet reading utilities

**Instantiation via StorageModule:**
```java
StorageModule storage = StorageModule.forH2(dbManager);
UserStorage users = storage.users();  // Returns jdbi.onDemand(JdbiUserStorage.class)
```

## Testing Standards

### Structure
- **Unit tests**: `src/test/java/datingapp/core/` - In-memory mocks, no DB
- **Integration tests**: `src/test/java/datingapp/storage/` - Real H2

### Rules
- `@DisplayName("description")` on ALL test methods
- `@Nested` classes for logical grouping
- **NO Mockito** - Create `InMemory*Storage` implementations
- AAA pattern: Arrange → Act → Assert
- Use test helpers: `createCompleteUser(name)`, `createActiveUser(id, name)`

### Mock Pattern:
```java
static class InMemoryUserStorage implements User.Storage {
    private final Map<UUID, User> users = new HashMap<>();
    @Override public void save(User u) { users.put(u.getId(), u); }
    @Override public User get(UUID id) { return users.get(id); }
}
```

## Configuration (`AppConfig`)

| Parameter               | Default | Description             |
|-------------------------|---------|-------------------------|
| `maxDistanceKm`         | 50      | Candidate search radius |
| `dailyLikeLimit`       | 100     | Likes per day (-1=∞)    |
| `undoWindowSeconds`     | 30      | Time to undo swipe      |
| `sessionTimeoutMinutes` | 5       | Inactivity timeout      |
| `maxSwipesPerSession`   | 500     | Anti-bot limit          |
| `autoBanThreshold`      | 3       | Reports for auto-ban    |

## Key Data Flows

**Candidate Discovery:** `CandidateFinder` → 7-stage filter: self → ACTIVE → no prior interaction → mutual gender → mutual age → distance → dealbreakers → sorted by distance

**Match Quality:** 5 factors: Distance (15%) + Age (10%) + Interests (30%) + Lifestyle (30%) + Response Time (15%)

**Messaging:** Requires `ACTIVE` match → validates users → creates conversation → saves message → returns `SendResult`

## Build Tools & Dependencies

| Tool/Lib   | Version | Purpose                               |
|------------|---------|---------------------------------------|
| **JDBI**   | 3.51.0  | Declarative SQL (SqlObject plugin)    |
| H2         | 2.4.240 | Embedded database                     |
| Spotless   | 3.1.0   | Palantir Java Format (4-space indent) |
| Checkstyle | 3.6.0   | Style validation (advisory)           |
| PMD        | 3.28.0  | Bug detection (advisory)              |
| JaCoCo     | 0.8.14  | Coverage (60% min, excludes ui/cli)   |

## Recent Updates

### UI Controller Action Handlers (2026-01-30)
All 6 JavaFX controllers enhanced with consistent action handler patterns:

**What Changed:**
- Introduced `BaseController` abstract class with subscription lifecycle management
- All event handlers moved to dedicated `wireActionHandlers()` / `wireNavigationButtons()` / `wireAuxiliaryActions()` methods
- Added keyboard shortcuts in `MatchingController` (Ctrl+Z for undo, arrow keys for swipe)
- Enhanced `UiAnimations` with 8+ reusable animation utilities (pulse, fade, shake, bounce, parallax)
- Consolidated `UiServices` with Toast notification system and ImageCache

**Controllers Updated:**
- `MatchingController` - Action handlers + keyboard shortcuts
- `PreferencesController` - Save/back/theme toggle buttons
- `ProfileController` - Photo upload + dealbreakers editor
- `DashboardController` - Six navigation buttons
- `MatchesController` - Navigation wiring
- `LoginController` - Create account button + keyboard setup

**Benefits:**
- Separation of concerns (action binding isolated from initialization)
- Memory leak prevention via `BaseController.addSubscription()`
- Consistent `event.consume()` pattern prevents event bubbling
- Accessibility improvements with keyboard shortcuts

### JDBI Migration Complete (2026-01-29)
All 16 storage implementations migrated from manual JDBC (`H2*Storage`) to **JDBI declarative SQL** (`Jdbi*Storage`):

**What Changed:**
- Deleted 12 `H2*Storage` classes (~1,500 lines of boilerplate)
- Created 17 JDBI interfaces with `@SqlQuery`/`@SqlUpdate` annotations
- Added `storage/mapper/` package with `RowMapper` implementations
- Added `storage/jdbi/` package with type handlers (`EnumSetArgumentFactory`, `EnumSetColumnMapper`)
- Updated `StorageModule.forH2()` to use `jdbi.onDemand()` for zero-boilerplate instantiation
- Refactored `ServiceRegistry` to use `StorageModule` instead of direct H2 instantiation

**Benefits:**
- Zero manual `Connection`/`PreparedStatement` management
- Type-safe parameter binding with `@Bind`/`@BindBean`
- Declarative SQL in annotations (easier to read and maintain)
- Automatic resource cleanup

### Previous Changes (2026-01-27)
- **New `ValidationService`**: Centralized input validation with `ValidationResult` pattern for names, ages, heights, distances, bios, age ranges, and coordinates
- **JavaFX UI Polish**: Enhanced login screen with search filtering, keyboard navigation, avatars, completion badges, double-click login
- **Matches Screen**: Added tabs for received/sent likes with like-back, pass, and withdraw actions
- **Profile Completion**: Birth date editing, missing completion details shown in header
- **CandidateFinder**: Relaxed distance filtering when user location is (0,0) to avoid empty queues
- **Storage Tests**: Hardened FK-aware tests—create user rows before inserting records with `user_id` foreign keys
- **Daily Pick Exclusions**: Now uses `LikeStorage.getLikedOrPassedUserIds()` to avoid resurfacing already-interacted users

### Major Consolidation Complete (2026-01-25)
The codebase underwent significant consolidation reducing file count by ~26% (159→118 files):

**Service Consolidations:**
- `DailyLimitService` + `DailyPickService` → `DailyService`
- `VerificationService` + `ReportService` → `TrustSafetyService`
- `LikerBrowserService` + `PaceCompatibilityService` → merged into `MatchingService`
- `DealbreakersEvaluator` → nested `Evaluator` class in `Dealbreakers`

**Storage Interface Nesting:**
- 10 standalone `*Storage.java` interfaces moved into their domain classes as nested interfaces
- Examples: `UserStorage` → `User.Storage`, `MatchStorage` → `Match.Storage`

**CLI Handler Merges:**
- `ProfileVerificationHandler` → merged into `SafetyHandler`
- `UserManagementHandler` → merged into `ProfileHandler`
- `InputReader`, `UserSession` → merged into `CliUtilities`

**Domain Model Grouping:**
- `Message` + `Conversation` → `Messaging.java`
- `FriendRequest` + `Notification` → `Social.java`
- `UserStats` + `PlatformStats` + `MatchQuality` → `Stats.java`
- `Interest` + `Lifestyle` + `PacePreferences` → `Preferences.java`
- `Like` + `Block` + `Report` → `UserInteractions.java`

**New Infrastructure:**
- `AbstractH2Storage` base class reducing boilerplate across 11 H2 storage implementations

## Known Limitations

**Phase 0**: No transactions (undo not atomic), in-memory undo state, single-user console, no auth

**Pending**: Custom interests, photo upload, real-time notifications, pagination, message editing/search

## Documentation Index

| Doc                               | Purpose                                                |
|-----------------------------------|--------------------------------------------------------|
| `AGENTS.md`                       | Full coding standards, testing patterns, quality tools |
| `docs/architecture.md`            | Visual diagrams (Mermaid)                              |
| `docs/MESSAGING_SYSTEM_DESIGN.md` | Phase 2.0 messaging design                             |
| `docs/completed-plans/`           | Feature design docs                                    |

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
2|2026-01-16 00:00:00|agent:claude_code|docs|CLAUDE.md slimmed 49k→20k chars|CLAUDE.md
3|2026-01-16 01:00:00|agent:claude_code|docs|Enhanced with coding standards, patterns, anti-patterns|CLAUDE.md
4|2026-01-25 12:00:00|agent:claude_code|docs|Updated CLAUDE.md for post-consolidation: nested Storage interfaces, new domain groupings (Messaging/Social/Stats/Preferences/UserInteractions), service merges, AbstractH2Storage|CLAUDE.md
5|2026-01-28 12:00:00|agent:claude_code|docs|Updated stats (118 files), added ValidationService, ServiceRegistry exception note, 39 interests, 2026-01-27 UI/storage changes|CLAUDE.md
6|2026-01-29 16:30:00|agent:claude_code|storage|JDBI migration complete: H2*Storage→Jdbi*Storage, declarative SQL, deleted 12 files (~1500 LOC), 581 tests passing|storage/jdbi/*,storage/mapper/*,CLAUDE.md
7|2026-01-30 14:00:00|agent:claude_code|ui-controllers|Action handler pattern: BaseController, wireActionHandlers(), UiAnimations, UiServices, keyboard shortcuts|ui/controller/*,ui/util/*,CLAUDE.md
8|2026-02-01 17:30:00|agent:claude_code|docs-fix|Fixed User.ProfileNote visibility (static); added nested type visibility rules to Type Usage table and anti-patterns|User.java,CLAUDE.md
---AGENT-LOG-END---
