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
| EnumSet null crash       | `EnumSet.copyOf(interests)`                  | `interests != null ? EnumSet.copyOf(interests) : EnumSet.noneOf(Interest.class)` |
| Exposed mutable field    | `return interests;`                          | `return EnumSet.copyOf(interests);`                                              |
| Missing touch()          | `this.name = name;`                          | `this.name = name; touch();`                                                     |
| Service throws exception | `throw new MessagingException(...)`          | `return SendResult.failure(msg, code)`                                           |
| Hardcoded thresholds     | `if (age < 18)`                              | `if (age < CONFIG.minAge())`                                                     |
| Wrong ID for pairs       | `a + "_" + b`                                | `a.compareTo(b) < 0 ? a+"_"+b : b+"_"+a`                                         |
| Java version mismatch    | `mvn test` fails: "release 25 not supported" | Install JDK 25+ or change `maven.compiler.release` in pom.xml                    |
| PMD + Spotless conflict  | Add `// NOPMD` then `mvn verify` fails       | Run `spotless:apply` after adding NOPMD comments, then re-verify with `verify`   |
| ViewModel storage import | `import datingapp.core.storage.UserStorage`  | Use adapter: `import datingapp.ui.viewmodel.data.UiUserStore`                    |
| Standalone Gender enum   | `import datingapp.core.Gender`               | Use nested: `User.Gender` (Gender.java was deleted)                              |

**Access config via:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Quick Commands

```bash
# Build & Run
mvn compile && mvn exec:exec          # Compile + Run CLI (dev/debug, forked JVM)
mvn javafx:run                         # Run JavaFX GUI

# Testing & Quality (REQUIRED before commit)
mvn test                               # All tests
mvn spotless:apply && mvn verify       # Format + full quality checks
```

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

**Stats (2026-02-11):** 160 Java files (102 main + 58 test), ~47K lines (~35K code), 802 tests, 60% coverage min.

### Package Structure

| Package              | Purpose                                                              | Rule                                   |
|----------------------|----------------------------------------------------------------------|----------------------------------------|
| `core/`              | Utilities + config + bootstrap                                       | **ZERO** framework/DB imports          |
| `core/model/`        | 11 domain models                                                     | Pure data + state machines             |
| `core/service/`      | 14 services                                                          | Business logic, returns Result records |
| `core/storage/`      | 5 storage interfaces                                                 | Contracts only, no implementations     |
| `app/`               | `AppBootstrap`, `ConfigLoader`                                       | Initialization + infrastructure        |
| `app/cli/`           | 5 CLI handlers + `HandlerFactory`                                    | Thin layer over services               |
| `app/api/`           | `RestApiServer` (routes inlined)                                     | Javalin-based HTTP endpoints           |
| `storage/`           | `DatabaseManager`, `StorageFactory`                                  | DB connection + wiring                 |
| `storage/jdbi/`      | 15 JDBI implementations                                              | Implements `core/storage/*`            |
| `storage/schema/`    | `SchemaInitializer`, `MigrationRunner`                               | DDL + schema evolution                 |
| `ui/`                | `DatingApp`, `NavigationService`, `UiComponents`, `ViewModelFactory` | JavaFX entry + navigation              |
| `ui/viewmodel/`      | 8 ViewModels + `ErrorHandler`                                        | Owns observable properties             |
| `ui/viewmodel/data/` | `UiDataAdapters` (consolidated)                                      | Adapter interfaces + impls             |
| `ui/constants/`      | `UiConstants`                                                        | Centralized UI timing/sizing           |
| `ui/util/`           | `Toast`, `UiSupport`, `ImageCache`, `UiAnimations`                   | Static UI utilities                    |

### Bootstrap (Entry Points)

```java
// Main.java or DatingApp.java - SINGLE initialization
// AppBootstrap lives in app/ (not core/) — it's infrastructure, not domain logic
ServiceRegistry services = AppBootstrap.initialize();  // Idempotent, uses StorageFactory
AppSession session = AppSession.getInstance();         // Unified CLI/JavaFX session

// CLI: Lazy handler creation
HandlerFactory handlers = new HandlerFactory(services, session, inputReader);
handlers.matching().runMatchingLoop();  // Created on first call

// JavaFX: MVVM adapter wiring
ViewModelFactory vmFactory = new ViewModelFactory(services);  // Creates UI adapters
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
```

### Domain Models

| Model                     | Type    | Key Info                                                              |
|---------------------------|---------|-----------------------------------------------------------------------|
| `User`                    | Mutable | `INCOMPLETE→ACTIVE↔PAUSED→BANNED`; has `StorageBuilder`               |
| `User.Gender`             | Enum    | `MALE`, `FEMALE`, `OTHER` (nested `public static enum`)               |
| `User.UserState`          | Enum    | `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED` (nested)                   |
| `User.VerificationMethod` | Enum    | `EMAIL`, `PHONE` (nested)                                             |
| `Match`                   | Mutable | `ACTIVE→FRIENDS\|UNMATCHED\|GRACEFUL_EXIT\|BLOCKED`; deterministic ID |
| `Messaging.*`             | Mixed   | `Message` (record), `Conversation` (class); deterministic ID          |
| `Preferences.*`           | Mixed   | `Interest` enum (39), `Lifestyle` records, `PacePreferences`          |
| `UserInteractions.*`      | Records | `Like`, `Block`, `Report` + their storage interfaces                  |
| `Standout`                | Record  | Standout candidate data                                               |
| `UndoState`               | Record  | In-memory undo state with 30s expiration                              |
| `Achievement`             | Enum    | 11 achievements in 4 categories (in `core/model/`)                    |

### Core Utilities (in `core/` root)

`AppClock`, `AppConfig`, `AppSession`, `EnumSetUtil`, `LoggingSupport`, `PerformanceMonitor`, `ScoringConstants`, `ServiceRegistry`

### Storage Interfaces (`core/storage/`)

5 interfaces: `UserStorage`, `InteractionStorage`, `CommunicationStorage`, `AnalyticsStorage`, `TrustSafetyStorage`

Implementations in `storage/jdbi/` (15 files) use `@SqlQuery`/`@SqlUpdate` annotations. Includes `MapperHelper`, `UserBindingHelper`, `EnumSetJdbiSupport`.

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
// New entity - generates UUID + timestamps
public static Message create(String conversationId, UUID senderId, String content) {
    return new Message(UUID.randomUUID(), conversationId, senderId, content, Instant.now());
}
```

### Deterministic IDs (Two-User Entities)
```java
// Match.java, Conversation.java - same ID regardless of parameter order
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

### State Transitions with Validation
```java
public void unmatch(UUID userId) {
    if (isInvalidTransition(this.state, State.UNMATCHED)) {
        throw new IllegalStateException("Cannot unmatch from " + this.state);
    }
    this.state = State.UNMATCHED;
    this.endedAt = Instant.now();
    this.endedBy = userId;
}
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
private void touch() { this.updatedAt = Instant.now(); }
public void setBio(String bio) { this.bio = bio; touch(); }  // EVERY setter calls touch()
```

### Handler Dependencies (CLI)
```java
public class MatchingHandler {
    public record Dependencies(CandidateFinder finder, MatchingService matching, /*...*/ AppSession session) {
        public Dependencies { Objects.requireNonNull(finder); /*...*/ }  // Validate in compact constructor
    }
    public MatchingHandler(Dependencies deps) { this.finder = deps.finder(); /*...*/ }
}
```

### ViewModel Error Handling (JavaFX)
```java
// ErrorHandler.java - functional interface for ViewModel→Controller error communication
@FunctionalInterface
public interface ErrorHandler {
    void onError(String message);
}

// In ViewModel - add field and setter
private ErrorHandler errorHandler;
public void setErrorHandler(ErrorHandler handler) { this.errorHandler = handler; }

// In catch blocks - notify via handler
private void notifyError(String userMessage, Exception e) {
    if (errorHandler != null) {
        Platform.runLater(() -> errorHandler.onError(userMessage + ": " + e.getMessage()));
    }
}

// In Controller initialize() - wire up to toast
viewModel.setErrorHandler(msg -> Toast.showError(msg));
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
// StorageBuilder: .deletedAt(MapperHelper.readInstant(rs, "deleted_at"))
```

### UI Data Access Adapters (ViewModel Layer)
```java
// All adapter interfaces + impls consolidated in UiDataAdapters.java
import datingapp.ui.viewmodel.data.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiMatchDataAccess;

// ViewModelFactory wires the adapters
UiUserStore userStore = new UiDataAdapters.StorageUiUserStore(services.getUserStorage());
UiMatchDataAccess matchData = new UiDataAdapters.StorageUiMatchDataAccess(matchStorage, likeStorage, blockStorage);

// ViewModel constructor uses interface only
public MatchesViewModel(UiUserStore userStore, UiMatchDataAccess matchData) { ... }
```

### Constants Pattern (No Magic Numbers)
```java
// Use centralized constants instead of hardcoded values
import datingapp.core.ScoringConstants;
if (score >= ScoringConstants.MatchQuality.STAR_EXCELLENT_THRESHOLD) { ... }  // 90

import datingapp.ui.constants.UiConstants;
Duration duration = UiConstants.TOAST_ERROR_DURATION;  // 5 seconds
```

## Testing

### Use TestStorages (Centralized Mocks)
```java
// In test class - NO Mockito!
var userStorage = new TestStorages.Users();
var likeStorage = new TestStorages.Likes();
var matchStorage = new TestStorages.Matches();
var trustSafetyStorage = new TestStorages.TrustSafety();
```

### Test Helpers Pattern
```java
// At end of test class
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
            ).birthDate(MapperHelper.readLocalDate(rs, "birth_date")).build();
        }
    }
}
```

## Configuration (`AppConfig`)

`AppConfig` is a **record with 40+ parameters**. Key groups:

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
- ❌ Use Mockito (use `TestStorages.*` instead)
- ❌ Throw from services (return `*Result` records)
- ❌ Hardcode thresholds (use `AppConfig.defaults()` or `ScoringConstants`)
- ❌ Call `new User(...)` in mappers (use `StorageBuilder`)
- ❌ Use `HashSet` for enums (use `EnumSet`)
- ❌ Forget `touch()` in setters
- ❌ Import `core/storage/*` in ViewModels (use `UiDataAdapters` adapters)
- ❌ Use standalone `Gender`/`UserState` (use `User.Gender`, `User.UserState` nested enums)
- ❌ Hardcode animation timings (use `UiConstants.*`)

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

- **02-11**: Codebase consolidation complete: 189→160 files (-29), models→`core/model/`, services→`core/service/`, storage interfaces consolidated to domain boundaries (11→9→5 via `InteractionStorage`, `CommunicationStorage`, `AnalyticsStorage`), CleanupService→SessionService, UiDataAdapters consolidated, UiConstants consolidated, EnumSetJdbiSupport consolidated, MapperHelper→`storage/jdbi/`, ScoringConstants→`core/`, UiComponents→`ui/`, StorageException deleted
- **02-11**: Documentation sync pass: refreshed tested baseline to 802 passing tests and aligned instruction docs with current consolidated storage architecture
- **02-10**: Nested enums in User (Gender/UserState/VerificationMethod), UI data access adapters, StorageFactory, soft-delete as direct fields, moved AppBootstrap/ConfigLoader to app/, LoggingSupport
- **02-08**: Project config audit: enforced Checkstyle+PMD (custom pmd-rules.xml), cleaned .gitignore, fixed stale build commands across all docs
- **02-05**: Enhanced UI/UX: ErrorHandler pattern in ViewModels, navigation context, loading overlays
- **02-03**: Fixed 25+ nested types to `public static`; added `NestedTypeVisibilityTest`
- **02-01**: Added `AppBootstrap`, `AppSession`, `HandlerFactory` for unified init
- **01-29**: JDBI migration complete; deleted 12 H2*Storage classes

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
---AGENT-LOG-END---
