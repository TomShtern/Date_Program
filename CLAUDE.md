<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1, 2) locate affected doc fragment, 3) archive with <!--ARCHIVE:SEQ:agent:scope-->,
# 4) apply minimal edits, 5) append ChangeStamp to file-end changelog and edited fragment.
<!--/AGENT-DOCSYNC-->

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

# CLAUDE.md

Guidance for Claude Code when working with this repository.

> **For AI Agents**: See [`AGENTS.md`](./AGENTS.md) for comprehensive coding standards, testing patterns, and quality tools.

## ⚠️ Critical Gotchas (Read First!)

These are the **top errors** that cause compilation/runtime failures:

| Gotcha                   | Wrong                                        | Correct                                                                          |
|--------------------------|----------------------------------------------|----------------------------------------------------------------------------------|
| Non-static nested types  | `public class X { public record Y() {} }`    | `public class X { public static record Y() {} }`                                 |
| Externally-used nested type | `class User { enum Gender {...} }` used by other files | Extract to own file: `Gender.java` in same package (JLS/jdt.ls breaks on cross-file nested refs) |
| EnumSet null crash       | `EnumSet.copyOf(interests)`                  | `interests != null ? EnumSet.copyOf(interests) : EnumSet.noneOf(Interest.class)` |
| Exposed mutable field    | `return interests;`                          | `return EnumSet.copyOf(interests);`                                              |
| Missing touch()          | `this.name = name;`                          | `this.name = name; touch();`                                                     |
| Service throws exception | `throw new MessagingException(...)`          | `return SendResult.failure(msg, code)`                                           |
| Hardcoded thresholds     | `if (age < 18)`                              | `if (age < CONFIG.minAge())`                                                     |
| Wrong ID for pairs       | `a + "_" + b`                                | `a.compareTo(b) < 0 ? a+"_"+b : b+"_"+a`                                         |
| Raw `Instant.now()`      | `this.updatedAt = Instant.now()`             | `this.updatedAt = AppClock.now()` (enables TestClock in tests)                   |
| Java version mismatch    | `mvn test` fails: "release 25 not supported" | Install JDK 25+ or change `maven.compiler.release` in pom.xml                    |
| PMD + Spotless conflict  | Add `// NOPMD` then `mvn verify` fails       | Run `spotless:apply` after adding NOPMD comments, then re-verify with `verify`   |
| ViewModel storage import | `import datingapp.core.storage.UserStorage`  | Use adapter: `import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore`          |
| Nested `User.Gender` ref | `import datingapp.core.model.User.Gender`    | Use standalone: `import datingapp.core.model.Gender` (extracted to own file)     |
| Nested Match.State ref   | `Match.State.ACTIVE`                         | Use standalone: `MatchState.ACTIVE` (extracted to `core/model/MatchState`)       |

**Access config via:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Quick Commands

<!--ARCHIVE:19:agent:github_copilot:scope:test-output-workflow-->
```bash
# Build & Run
mvn compile && mvn exec:exec          # Compile + Run CLI (dev/debug, forked JVM)
mvn javafx:run                         # Run JavaFX GUI

# Testing & Quality (REQUIRED before commit)
mvn test                               # All tests
mvn spotless:apply && mvn verify       # Format + full quality checks
```
<!--/ARCHIVE-->

```bash
# Build & Run
mvn compile && mvn exec:exec          # Compile + Run CLI (dev/debug, forked JVM)
mvn javafx:run                         # Run JavaFX GUI

# Testing & Quality (REQUIRED before commit)
mvn test                               # Run this first (default concise output)
mvn -Ptest-output-verbose test         # If failures need more detail, rerun verbose
mvn -Ptest-output-verbose -Dtest=StatsHandlerTest test
mvn -Ptest-output-verbose -Dtest="StatsHandlerTest#displaysUnlockedAchievements" test
mvn spotless:apply && mvn verify       # Format + full quality checks
```
19|2026-02-13 10:15:00|agent:github_copilot|scope:test-output-workflow|Document regular-first test flow and verbose rerun commands|CLAUDE.md

### Build Command Discipline (Agents: READ THIS)

**NEVER run `mvn verify`, `mvn test`, or any expensive Maven goal multiple times to extract different info.** Each run repeats the entire build pipeline (compile → test → jacoco → jar → spotless → pmd → jacoco:check). Run ONCE, capture output, then filter:

```powershell
# CORRECT: one run, multiple queries
$out = mvn verify 2>&1 | Out-String
$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
$out | Select-String "Tests run:" | Select-Object -Last 1
$out | Select-String "ERROR|WARNING.*violation"

# WRONG: three separate Maven runs with different filters
mvn verify 2>&1 | Select-String "BUILD (SUCCESS|FAILURE)"   # ← 60s wasted
mvn verify 2>&1 | Select-String "Tests run:"                # ← 60s wasted
mvn verify 2>&1 | Select-String "ERROR|WARNING"             # ← 60s wasted
```

This applies to **any** long-running command (`mvn`, `docker build`, `npm run build`, etc.).

## Prerequisites

- **JDK 25+** (pom.xml targets `release 25` with `--enable-preview`)
- **Maven 3.9+**
- **Windows:** Run `chcp 65001` before CLI for emoji support

## Architecture Overview

**Phase 2.1** console dating app: **Java 25** + Maven + H2 + JDBI. Features: matching, messaging, relationship transitions, pace compatibility, achievements.

**Stats (2026-02-18):** 143 Java files (84 main + 59 test), ~43K lines (~33K code), 60% coverage min.

### Package Structure

| Package               | Purpose                                                              | Rule                                   |
|-----------------------|----------------------------------------------------------------------|----------------------------------------|
| `core/`               | Utilities + config (8 files): AppClock, AppConfig, AppSession, EnumSetUtil, LoggingSupport, PerformanceMonitor, ServiceRegistry, TextUtil | **ZERO** framework/DB imports |
| `core/model/`         | Entities + standalone enums (8 files): User, Match, Gender, UserState, VerificationMethod, MatchState, MatchArchiveReason, ProfileNote | Pure data + state machines |
| `core/connection/`    | ConnectionModels + ConnectionService                                 | Messaging + relationships              |
| `core/matching/`      | CandidateFinder, MatchingService, MatchQualityService, UndoService, RecommendationService, TrustSafetyService, Standout, LifestyleMatcher | All matching + trust + recommendations |
| `core/metrics/`       | ActivityMetricsService, EngagementDomain, SwipeState                 | Analytics + engagement tracking        |
| `core/profile/`       | ProfileService, ValidationService, MatchPreferences                  | Profile management                     |
| `core/storage/`       | 5 storage interfaces                                                 | Contracts only, no implementations     |
| `app/bootstrap/`      | `ApplicationStartup` (consolidated init + config loading)            | Initialization + infrastructure        |
| `app/cli/`            | 6 files flat: CliTextAndInput + 5 handlers (Matching, Messaging, Profile, Safety, Stats) | Thin layer over services |
| `app/api/`            | `RestApiServer` (routes inlined)                                     | Javalin-based HTTP endpoints           |
| `storage/`            | `DatabaseManager`, `StorageFactory`                                  | DB connection + wiring                 |
| `storage/jdbi/`       | 6 files flat: JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage, JdbiMetricsStorage, JdbiTrustSafetyStorage, JdbiTypeCodecs | Implements `core/storage/*` |
| `storage/schema/`     | `SchemaInitializer`, `MigrationRunner`                               | DDL + schema evolution                 |
| `ui/`                 | 7 files: DatingApp, NavigationService, UiComponents, UiFeedbackService, ImageCache, UiAnimations, UiConstants | JavaFX entry + utilities |
| `ui/viewmodel/`       | 11 files: 8 ViewModels + ViewModelErrorSink + UiDataAdapters + ViewModelFactory | MVVM + adapters + factory |
| `ui/screen/`          | 10 controllers: Base + 8 screen + MilestonePopupController           | FXML controllers                       |

### Bootstrap (Entry Points)

```java
// Main.java or DatingApp.java - SINGLE initialization
// ApplicationStartup lives in app/bootstrap/ — consolidated init + config loading
ServiceRegistry services = ApplicationStartup.initialize();  // Idempotent, loads config, uses StorageFactory
AppSession session = AppSession.getInstance();               // Unified CLI/JavaFX session

// CLI: Direct handler instantiation in Main.java (no HandlerFactory)
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matchingHandler = new MatchingHandler(new MatchingHandler.Dependencies(..., session, inputReader));
ProfileHandler profileHandler = new ProfileHandler(..., session, inputReader);

// JavaFX: MVVM adapter wiring
ViewModelFactory vmFactory = new ViewModelFactory(services);  // Creates UI adapters
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
```

### Domain Models

**No externally-used nested types.** Any enum, record, or class referenced by other files MUST be its own top-level `.java` file. The Java Language Server (jdt.ls) cannot reliably resolve cross-file nested type references — auto-import fails, refactoring misses usages, and false compile errors appear. Nested types are only acceptable when they are **private** or used exclusively within the declaring file (e.g., inner `Dao` interfaces in JDBI storage classes).

| Model                      | Location                | Key Info                                                              |
|----------------------------|-------------------------|-----------------------------------------------------------------------|
| `User`                     | `core/model/`           | Mutable; uses `UserState` for lifecycle; has `StorageBuilder`         |
| `Gender`                   | `core/model/`           | Standalone enum: `MALE`, `FEMALE`, `OTHER`                            |
| `UserState`                | `core/model/`           | Standalone enum: `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED`           |
| `MatchState`               | `core/model/`           | Standalone enum: `ACTIVE`, `FRIENDS`, `UNMATCHED`, `GRACEFUL_EXIT`, `BLOCKED` |
| `MatchArchiveReason`       | `core/model/`           | Standalone enum: `FRIEND_ZONE`, `GRACEFUL_EXIT`, `UNMATCH`, `BLOCK`   |
| `VerificationMethod`       | `core/model/`           | Standalone enum: `EMAIL`, `PHONE`                                     |
| `ProfileNote`              | `core/model/`           | Record: private notes on profiles; uses `AppClock.now()`              |
| `Match`                    | `core/model/`           | Mutable; uses `MatchState` + `MatchArchiveReason`; deterministic ID   |
| `ConnectionModels.*`       | `core/connection/`      | `Message` (record), `Conversation`, `FriendRequest`, `Like`, `Block`, `Report`, `Notification` |
| `EngagementDomain`         | `core/metrics/`         | `Achievement`, `UserAchievement`, `UserStats`, `PlatformStats`        |
| `SwipeState`               | `core/metrics/`         | `Session`, `Undo` (swipe session + undo state)                        |
| `MatchPreferences`         | `core/profile/`         | `Interest`, `Lifestyle`, `Dealbreakers`, `PacePreferences`            |
| `Standout`                 | `core/matching/`        | Standout candidate data + `Standout.Storage` interface                |

### Core Utilities (in `core/` root)

`AppClock`, `AppConfig`, `AppSession`, `EnumSetUtil`, `LoggingSupport`, `PerformanceMonitor`, `ServiceRegistry`, `TextUtil`

**ServiceRegistry:** Constructor-injected with 15 params (config + 5 storages + 9 services). Direct getters only — no aliases.

### Storage Interfaces (`core/storage/`)

5 interfaces: `UserStorage`, `InteractionStorage`, `CommunicationStorage`, `AnalyticsStorage`, `TrustSafetyStorage`

Implementations in `storage/jdbi/` (6 files, flat — no subpackages):

| JDBI Implementation        | Implements               | Notes                                             |
|----------------------------|--------------------------|---------------------------------------------------|
| `JdbiUserStorage`          | `UserStorage`            | `new JdbiUserStorage(jdbi)` — outer class + Dao   |
| `JdbiMatchmakingStorage`   | `InteractionStorage`     | `new JdbiMatchmakingStorage(jdbi)` + `undoStorage()` accessor |
| `JdbiConnectionStorage`    | `CommunicationStorage`   | `new JdbiConnectionStorage(jdbi)`                 |
| `JdbiMetricsStorage`       | `AnalyticsStorage`       | `new JdbiMetricsStorage(jdbi)` — also implements `Standout.Storage` |
| `JdbiTrustSafetyStorage`   | `TrustSafetyStorage`     | `jdbi.onDemand(...)` directly (SqlObject)         |
| `JdbiTypeCodecs`           | (utility)                | SqlRowReaders, EnumSet codecs, column mappers      |

## Key Patterns

### StorageBuilder (Loading from DB)
```java
// User.java - bypass validation when reconstructing from database
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio)
    .birthDate(birthDate)
    .gender(gender)
    .interestedIn(interestedIn)  // Handles null safely
    .state(state)
    .build();
```

### Factory Methods (Creating New)
```java
// New entity - generates UUID + timestamps (uses AppClock, not Instant.now()!)
public static Message create(String conversationId, UUID senderId, String content) {
    return new Message(UUID.randomUUID(), conversationId, senderId, content, AppClock.now());
}
```

### Deterministic IDs (Two-User Entities)
```java
// Match.java, ConnectionModels.Conversation - same ID regardless of parameter order
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

### State Transitions with Validation
```java
public void unmatch(UUID userId) {
    if (isInvalidTransition(this.state, MatchState.UNMATCHED)) {
        throw new IllegalStateException("Cannot unmatch from " + this.state);
    }
    this.state = MatchState.UNMATCHED;
    this.endedAt = AppClock.now();
    this.endedBy = userId;
}
```

### MatchingService Builder (Optional Dependencies)
```java
// MatchingService has 3 optional dependencies — uses builder, not constructor
MatchingService matchingService = MatchingService.builder()
    .interactionStorage(interactionStorage)        // required
    .trustSafetyStorage(trustSafetyStorage)        // required
    .userStorage(userStorage)                      // required
    .activityMetricsService(activityMetricsService) // optional
    .undoService(undoService)                      // optional
    .dailyService(recommendationService)           // optional
    .build();
```

### Result Pattern (Services Never Throw)
```java
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
// Usage: return SendResult.failure("Match not active", ErrorCode.MATCH_NOT_ACTIVE);
```

### EnumSet Defensive Patterns
```java
// Setter - handle null safely
public void setInterestedIn(Set<Gender> interestedIn) {
    this.interestedIn = interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
    touch();
}

// Getter - never expose internal reference
public Set<Gender> getInterestedIn() {
    return interestedIn.isEmpty() ? EnumSet.noneOf(Gender.class) : EnumSet.copyOf(interestedIn);
}
```

### Touch Pattern (Mutable Entities)
```java
private void touch() { this.updatedAt = AppClock.now(); }  // Uses AppClock, NOT Instant.now()
public void setBio(String bio) { this.bio = bio; touch(); }  // EVERY setter calls touch()
```

### Handler Dependencies (CLI)
```java
// All handlers flat in app/cli/ (no subpackages)
// Each handler declares a Dependencies record; instantiated directly in Main.java (no HandlerFactory)
public class MatchingHandler {
    public static record Dependencies(CandidateFinder candidateFinderService, MatchingService matchingService,
            InteractionStorage interactionStorage, /*...*/ AppSession session, InputReader inputReader) {
        public Dependencies { Objects.requireNonNull(candidateFinderService); /*...*/ }
    }
    public MatchingHandler(Dependencies deps) { this.candidateFinderService = deps.candidateFinderService(); /*...*/ }
}
```

### ViewModel Error Handling (JavaFX)
```java
// ViewModelErrorSink.java (ui/viewmodel/) - functional interface for ViewModel→Controller errors
@FunctionalInterface
public interface ViewModelErrorSink {
    void onError(String message);
}

// In ViewModel - add field and setter
private ViewModelErrorSink errorHandler;
public void setErrorHandler(ViewModelErrorSink handler) { this.errorHandler = handler; }

// In catch blocks - notify via handler
private void notifyError(String userMessage, Exception e) {
    if (errorHandler != null) {
        Platform.runLater(() -> errorHandler.onError(userMessage + ": " + e.getMessage()));
    }
}

// In Controller initialize() - wire up to UiFeedbackService (replaces old Toast class)
viewModel.setErrorHandler(UiFeedbackService::showError);
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

### Loading Overlays (BaseController)
```java
// In controller initialize()
StackPane loadingOverlay = UiComponents.createLoadingOverlay();
registerOverlay(loadingOverlay);  // BaseController tracks for cleanup
loadingOverlay.visibleProperty().bind(viewModel.loadingProperty());
loadingOverlay.managedProperty().bind(viewModel.loadingProperty());
```

### Soft-Delete Pattern (Direct on Entities)
```java
// Each entity implements soft-delete directly (no SoftDeletable interface)
private Instant deletedAt;
public Instant getDeletedAt() { return deletedAt; }
public void markDeleted(Instant when) { this.deletedAt = when; }
public boolean isDeleted() { return deletedAt != null; }
// StorageBuilder: .deletedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "deleted_at"))
```

### UI Data Access Adapters (ViewModel Layer)
```java
// All adapter interfaces + impls consolidated in UiDataAdapters.java (ui/viewmodel/)
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;

// ViewModelFactory wires the adapters
UiUserStore userStore = new UiDataAdapters.StorageUiUserStore(services.getUserStorage());
UiMatchDataAccess matchData = new UiDataAdapters.StorageUiMatchDataAccess(
    services.getInteractionStorage(), services.getTrustSafetyStorage());

// ViewModel constructor uses interface only
public MatchesViewModel(UiMatchDataAccess matchData, UiUserStore userStore, ...) { ... }
```

### Constants Pattern (No Magic Numbers)
```java
// Use centralized constants instead of hardcoded values
import datingapp.ui.UiConstants;
Duration duration = UiConstants.TOAST_ERROR_DURATION;  // 5 seconds

// Scoring constants are inlined in ProfileService (ScoringConstants was deleted)
// Use AppConfig for configurable thresholds
private static final AppConfig CONFIG = AppConfig.defaults();
if (age < CONFIG.minAge()) { ... }
```

## Testing

### Use TestStorages (Centralized Mocks)
```java
// In test class - NO Mockito! (TestStorages is in core/testutil/)
var userStorage = new TestStorages.Users();              // implements UserStorage
var interactionStorage = new TestStorages.Interactions(); // implements InteractionStorage
var commStorage = new TestStorages.Communications();      // implements CommunicationStorage
var analyticsStorage = new TestStorages.Analytics();       // implements AnalyticsStorage
var trustSafetyStorage = new TestStorages.TrustSafety();  // implements TrustSafetyStorage
```

### Test Helpers Pattern
```java
// At end of test class
private User createActiveUser(UUID id, String name) {
    User u = new User(id, name);
    u.setBirthDate(LocalDate.now().minusYears(25));
    u.setGender(Gender.MALE);           // standalone enum in core/model/
    u.setInterestedIn(Set.of(Gender.FEMALE));
    u.setMaxDistanceKm(50);
    u.setMinAge(20); u.setMaxAge(30);
    u.addPhotoUrl("http://example.com/photo.jpg");
    return u;
}
```

### Test Structure
```java
@Timeout(5) class MyServiceTest {
    @Nested @DisplayName("When user is active")
    class WhenActive {
        @Test @DisplayName("should allow messaging")
        void allowsMessaging() { /* AAA: Arrange → Act → Assert */ }
    }
}
```

## JDBI Storage Pattern

```java
// Outer class implements core storage interface; inner Dao has JDBI annotations
public final class JdbiUserStorage implements UserStorage {
    public static final String ALL_COLUMNS = "id, name, bio, birth_date, ...";
    private final Dao dao;

    public JdbiUserStorage(Jdbi jdbi) { this.dao = jdbi.onDemand(Dao.class); }

    @Override public void save(User user) { dao.save(new UserSqlBindings(user)); }
    @Override public User get(UUID id)    { return dao.get(id); }

    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
        void save(@BindBean UserSqlBindings helper);

        @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id")
        User get(@Bind("id") UUID id);
    }

    public static class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return User.StorageBuilder.create(
                JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),      // Null-safe helpers
                rs.getString("name"),
                JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at")
            ).birthDate(JdbiTypeCodecs.SqlRowReaders.readLocalDate(rs, "birth_date")).build();
        }
    }

    public static final class UserSqlBindings { /* wraps User for @BindBean */ }
}
```

## Configuration (`AppConfig`)

`AppConfig` is a **record with 57 parameters**. Key groups:

| Group      | Examples                                                                | Access                      |
|------------|-------------------------------------------------------------------------|-----------------------------|
| Limits     | `dailyLikeLimit(100)`, `maxSwipesPerSession(500)`                       | `CONFIG.dailyLikeLimit()`   |
| Validation | `minAge(18)`, `maxAge(120)`, `minHeightCm(50)`, `maxHeightCm(300)`      | `CONFIG.minAge()`           |
| Algorithm  | `nearbyDistanceKm(5)`, `similarAgeDiff(2)`, `minSharedInterests(3)`     | `CONFIG.nearbyDistanceKm()` |
| Weights    | `distanceWeight(0.15)`, `interestWeight(0.25)`, `lifestyleWeight(0.25)` | `CONFIG.distanceWeight()`   |

**Usage:** `private static final AppConfig CONFIG = AppConfig.defaults();`
**Custom:** `AppConfig.builder().dailyLikeLimit(50).minAge(21).build()`

## NEVER Do These

- ❌ Import framework/DB in `core/` (zero coupling)
- ❌ Skip `Objects.requireNonNull()` in constructors
- ❌ Return mutable collections directly
- ❌ Forget `static` on nested types (enums, records, classes)
- ❌ Define externally-used types as nested (JLS/jdt.ls can't resolve cross-file nested refs — extract to own file in same package)
- ❌ Use Mockito (use `TestStorages.*` instead)
- ❌ Throw from services (return `*Result` records)
- ❌ Hardcode thresholds (use `AppConfig.defaults()`)
- ❌ Call `new User(...)` in mappers (use `StorageBuilder`)
- ❌ Use `HashSet` for enums (use `EnumSet`)
- ❌ Forget `touch()` in setters
- ❌ Use `Instant.now()` directly (use `AppClock.now()` for testability with TestClock)
- ❌ Import `core/storage/*` in ViewModels (use `UiDataAdapters` adapters in `ui/viewmodel/`)
- ❌ Use `User.Gender`/`User.UserState` nested refs (extracted to standalone `Gender`, `UserState` in `core/model/`)
- ❌ Use `Match.State` (extracted to standalone `MatchState` in `core/model/`)
- ❌ Hardcode animation timings (use `UiConstants.*` in `ui/`)
- ❌ Reference `Toast`/`UiSupport`/`ScoringConstants` (deleted)
- ❌ Reference `AppBootstrap`/`HandlerFactory`/`CliSupport` (deleted; use `ApplicationStartup`/`CliTextAndInput`)

## Key Data Flows

**Candidate Discovery:** `CandidateFinder` → 7 filters: self → ACTIVE → no interaction → mutual gender → mutual age → distance → dealbreakers → sort by distance

**Match Quality:** Distance(15%) + Age(10%) + Interests(25%) + Lifestyle(25%) + Pace(15%) + Response(10%)

**Messaging:** ACTIVE match required → validate users → create conversation → save message → return `SendResult`

## Documentation Index

| Doc                                    | Purpose                          |
|----------------------------------------|----------------------------------|
| `AGENTS.md`                            | Full coding standards            |
| `docs/architecture.md`                 | Mermaid diagrams                 |
| `docs/completed-plans/`                | Completed designs                |
| `docs/core-module-overview.md`         | Core module structure & services |
| `docs/storage-module-overview.md`      | Storage layer & schema details   |
| `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` | Code review findings             |

## Recent Updates (2026-02)

- **02-18**: Docs sync — CLAUDE.md fully reconciled with actual source code. Major corrections: enums extracted from User to standalone files (Gender, UserState, VerificationMethod, MatchState, MatchArchiveReason, ProfileNote), `core/safety/` + `core/recommendation/` merged into `core/matching/`, ScoringConstants deleted (inlined in ProfileService), TextUtil+LifestyleMatcher added, all subpackages flattened (CLI handlers, JDBI storage, UI utilities), JDBI storage renamed (JdbiMatchmakingStorage, JdbiConnectionStorage, JdbiMetricsStorage), ViewModelFactory moved to `ui/viewmodel/`. 143 files (84 main + 59 test).
- **02-14**: Domain-driven package reorganization. `ApplicationStartup` in `app/bootstrap/`. `CliTextAndInput`. `ViewModelErrorSink`. `UiFeedbackService`. Controllers in `ui/screen/`.
- **02-13**: Test output workflow: default concise output + verbose profile rerun
- **02-11**: Codebase consolidation: storage interfaces 11→5, UiDataAdapters/UiConstants consolidated
- **02-08**: Enforced Checkstyle+PMD, Build Command Discipline rule

## Agent Changelog (append-only, trimmed to recent)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Entries 1-9 archived (2026-01-14 through 2026-02-03)
10|2026-02-03 20:30:00|agent:claude_code|docs-optimize|Added Critical Gotchas, StorageBuilder, TestStorages, EnumSet patterns; condensed Recent Updates|CLAUDE.md
11|2026-02-05 00:00:00|agent:claude_code|docs-ui-patterns|Added ViewModel ErrorHandler, navigation context, loading overlay patterns; updated package structure|CLAUDE.md
12|2026-02-07 22:25:00|agent:claude_code|docs-audit|Fixed stale stats, added Prerequisites/Java gotcha, 5 new utility classes, trimmed changelog|CLAUDE.md
13|2026-02-08 11:15:00|agent:claude_code|config-audit|Enforced Checkstyle+PMD, custom pmd-rules.xml, cleaned .gitignore, untracked sarif, fixed stale build commands in all docs, updated stats|CLAUDE.md;AGENTS.md;README.md;.github/copilot-instructions.md;pom.xml;.gitignore;pmd-rules.xml
14|2026-02-08 12:30:00|agent:claude_code|docs-pmd-gotcha|Added PMD+Spotless conflict gotcha to Critical Gotchas table; recorded PMD suppression patterns in MEMORY.md|CLAUDE.md;MEMORY.md
15|2026-02-08 18:00:00|agent:claude_code|build-discipline|Added Build Command Discipline rule: capture expensive commands once, query N times|CLAUDE.md;AGENTS.md
16|2026-02-10 00:00:00|agent:claude_code|docs-major-refactor|Updated stats 189/48K, added nested enums/UI adapters/StorageFactory/constants/soft-delete/schema patterns; updated package structure table (13 packages); added 3 new gotchas; 3 new NEVER rules|CLAUDE.md
17|2026-02-11 12:00:00|agent:github_copilot|scope:codebase-consolidation|Consolidated 189→160 files: models→core/model, services→core/service, merged 3 storage interfaces into TrustSafetyStorage, merged CleanupService→SessionService, consolidated UiDataAdapters/UiConstants/EnumSetJdbiSupport, updated all 3 doc files|CLAUDE.md;AGENTS.md;.github/copilot-instructions.md
18|2026-02-11 22:52:00|agent:codex|scope:docs-stats-sync|Synced docs to latest verified baseline (802 tests) and refreshed storage-consolidation documentation consistency|CLAUDE.md;AGENTS.md;.github/copilot-instructions.md
19|2026-02-13 10:15:00|agent:github_copilot|scope:test-output-workflow|Document regular-first test flow and verbose rerun commands|CLAUDE.md;AGENTS.md;.github/copilot-instructions.md
20|2026-02-14 00:00:00|agent:claude_code|scope:docs-domain-reorg|Domain-driven reorg: updated stats 135/43K, package table to domain subpackages, ApplicationStartup/CliTextAndInput/ViewModelErrorSink/UiFeedbackService, fixed touch()+factory to AppClock.now(), updated TestStorages/ErrorHandler/adapter patterns, removed stale Toast/UiSupport/HandlerFactory/AppBootstrap refs|CLAUDE.md
21|2026-02-18 00:00:00|agent:claude_code|scope:docs-full-sync|Full code-verified sync: 143 files (84+59), extracted enums (Gender/UserState/VerificationMethod/MatchState/MatchArchiveReason/ProfileNote standalone), merged safety+recommendation into matching, deleted ScoringConstants, added TextUtil+LifestyleMatcher, flattened all subpackages (CLI/JDBI/UI), renamed JDBI impls, fixed all import paths, reversed Gender gotcha, updated ServiceRegistry docs|CLAUDE.md
---AGENT-LOG-END---
