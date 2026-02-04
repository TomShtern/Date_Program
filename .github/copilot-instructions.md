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
- **fzf** (`fzf`) `v0.67.0`
  - **Context:** Interactive filtering.
  - **Capabilities:** General-purpose command-line fuzzy finder.
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

**Stats:** 92 main + 37 test Java files | ~26K LOC | 60% coverage min

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

## Architecture Overview

**Two-Layer Clean Architecture:**
```
core/           Pure Java business logic - ZERO framework/database imports
core/storage/   9 storage interfaces (UserStorage, LikeStorage, MatchStorage...)
storage/jdbi/   JDBI implementations (JdbiUserStorage, JdbiLikeStorage...)
app/cli/        CLI handlers + HandlerFactory
ui/             JavaFX (AtlantaFX theme)
```

**Entry Points:**
```java
ServiceRegistry services = AppBootstrap.initialize();  // Idempotent singleton
AppSession session = AppSession.getInstance();         // Current user context
HandlerFactory handlers = new HandlerFactory(services, session, inputReader);
handlers.matching().runMatchingLoop();  // Lazy handler creation
```

## Key Domain Models

| Model              | Type          | Location                     | Notes                                                          |
|--------------------|---------------|------------------------------|----------------------------------------------------------------|
| `User`             | Mutable class | `core/User.java`             | State: `INCOMPLETE→ACTIVE↔PAUSED→BANNED`; has `StorageBuilder` |
| `Match`            | Mutable class | `core/Match.java`            | State: `ACTIVE→FRIENDS\|UNMATCHED\|BLOCKED`; deterministic ID  |
| `UserInteractions` | Container     | `core/UserInteractions.java` | Contains: `Like`, `Block`, `Report` records                    |
| `Messaging`        | Container     | `core/Messaging.java`        | Contains: `Message`, `Conversation`                            |
| `Preferences`      | Container     | `core/Preferences.java`      | Contains: `Interest` enum (39 values), `Lifestyle` records     |

**Deterministic IDs for Two-User Entities:**
```java
// Match.java, Messaging.Conversation - ALWAYS use this pattern
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

## Build & Test Commands

```bash
mvn compile && mvn exec:java              # Compile + Run CLI
mvn javafx:run                            # Run JavaFX GUI
mvn test                                  # All tests
mvn test -Dtest=MatchingServiceTest#mutualLikesCreateMatch  # Single method
mvn spotless:apply && mvn verify          # Format + full quality checks (REQUIRED before commit)
mvn package                               # Fat JAR → target/dating-app-1.0.0-shaded.jar
```

## Testing Patterns

**Use centralized `TestStorages` - NO Mockito:**
```java
var userStorage = new TestStorages.Users();
var likeStorage = new TestStorages.Likes();
var matchStorage = new TestStorages.Matches();
var blockStorage = new TestStorages.Blocks();
```

**Test Structure:**
```java
@Timeout(5) class MyServiceTest {
    @Nested @DisplayName("When user is active")
    class WhenActive {
        @Test @DisplayName("should allow messaging")
        void allowsMessaging() { /* AAA: Arrange → Act → Assert */ }
    }
}
```

## Key Patterns

**StorageBuilder (Loading from DB - bypass validation):**
```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio).birthDate(birthDate).gender(gender)
    .interestedIn(interestedIn)  // Handles null safely
    .state(state).build();
```

**Factory Methods (Creating new entities):**
```java
Match match = Match.create(userA, userB);  // Generates deterministic ID
Like like = Like.create(fromId, toId, Like.Direction.LIKE);
```

**Result Pattern (Services never throw):**
```java
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
```

**Touch Pattern (All setters on mutable entities):**
```java
private void touch() { this.updatedAt = Instant.now(); }
public void setBio(String bio) { this.bio = bio; touch(); }  // EVERY setter
```

## File Structure Reference

| Purpose            | Location                                                                     |
|--------------------|------------------------------------------------------------------------------|
| Domain models      | `core/{User,Match,Messaging,UserInteractions,Preferences,Dealbreakers}.java` |
| Storage interfaces | `core/storage/*Storage.java` (9 interfaces)                                  |
| Services           | `core/*Service.java`                                                         |
| JDBI storage       | `storage/jdbi/Jdbi*Storage.java`                                             |
| Service wiring     | `core/ServiceRegistry.java` (inner `Builder` class)                          |
| Bootstrap          | `core/AppBootstrap.java`, `core/AppSession.java`                             |
| CLI handlers       | `app/cli/*Handler.java`, `app/cli/HandlerFactory.java`                       |
| Configuration      | `core/AppConfig.java` (40+ params: limits, validation, weights)              |
| Test utilities     | `test/.../testutil/TestStorages.java`, `TestUserFactory.java`                |

## Data Flow

1. **Candidate Discovery:** `CandidateFinder` → 7-stage filter: self → ACTIVE → no interaction → mutual gender → mutual age → distance → dealbreakers → sort by distance
2. **Matching:** `MatchingService.recordLike()` → saves Like → creates Match on mutual interest
3. **Quality Scoring:** Distance(15%) + Age(10%) + Interests(25%) + Lifestyle(25%) + Pace(15%) + Response(10%)
4. **Undo:** In-memory state tracking with 30s expiration (lost on restart)

## Known Limitations (Don't Fix)

- **No transactions**: Like/Match deletions in undo flow are not atomic
- **In-memory undo state**: Lost on restart (UndoService)
- **Simulated verification**: Email/phone codes not actually sent
- **No caching layer**: Repeated database queries acceptable for Phase 2
