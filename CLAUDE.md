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

## ⚠️ Critical Gotchas (Read First!)

These are the **top errors** that cause compilation/runtime failures:

| Gotcha | Wrong | Correct |
|--------|-------|---------|
| Non-static nested types | `public class X { public record Y() {} }` | `public class X { public static record Y() {} }` |
| EnumSet null crash | `EnumSet.copyOf(interests)` | `interests != null ? EnumSet.copyOf(interests) : EnumSet.noneOf(Interest.class)` |
| Exposed mutable field | `return interests;` | `return EnumSet.copyOf(interests);` |
| Missing touch() | `this.name = name;` | `this.name = name; touch();` |
| Service throws exception | `throw new MessagingException(...)` | `return SendResult.failure(msg, code)` |
| Hardcoded thresholds | `if (age < 18)` | `if (age < CONFIG.minAge())` |
| Wrong ID for pairs | `a + "_" + b` | `a.compareTo(b) < 0 ? a+"_"+b : b+"_"+a` |

**Access config via:** `private static final AppConfig CONFIG = AppConfig.defaults();`

## Quick Commands

```bash
# Build & Run
mvn compile && mvn exec:java          # Compile + Run CLI
mvn javafx:run                         # Run JavaFX GUI
mvn package && java -jar target/dating-app-1.0.0-shaded.jar  # Fat JAR

# Testing & Quality (REQUIRED before commit)
mvn test                               # All tests
mvn spotless:apply && mvn verify       # Format + full quality checks
```

## Architecture Overview

**Phase 2.1** console dating app: **Java 25** + Maven + H2 + JDBI. Features: matching, messaging, relationship transitions, pace compatibility, achievements.

**Stats (2026-02-03):** 92 Java files, 37 test files, ~16K LOC, 60% coverage min.

### Package Structure

| Package | Purpose | Rule |
|---------|---------|------|
| `core/` | Pure business logic | **ZERO** framework/DB imports |
| `app/cli/` | CLI handlers + HandlerFactory | Thin layer over services |
| `storage/` | JDBI SQL interfaces | Implements `core/storage/*` |
| `ui/` | JavaFX (AtlantaFX theme) | Uses BaseController pattern |

### Bootstrap (Entry Points)

```java
// Main.java or DatingApp.java - SINGLE initialization
ServiceRegistry services = AppBootstrap.initialize();  // Idempotent
AppSession session = AppSession.getInstance();         // Unified CLI/JavaFX session

// CLI: Lazy handler creation
HandlerFactory handlers = new HandlerFactory(services, session, inputReader);
handlers.matching().runMatchingLoop();  // Created on first call
```

### Domain Models

| Model | Type | Key Info |
|-------|------|----------|
| `User` | Mutable | `INCOMPLETE→ACTIVE↔PAUSED→BANNED`; has `StorageBuilder` |
| `Match` | Mutable | `ACTIVE→FRIENDS\|UNMATCHED\|GRACEFUL_EXIT\|BLOCKED`; deterministic ID |
| `Messaging.*` | Mixed | `Message` (record), `Conversation` (class); deterministic ID |
| `Preferences.*` | Mixed | `Interest` enum (39), `Lifestyle` records, `PacePreferences` |
| `UserInteractions.*` | Records | `Like`, `Block`, `Report` + their storage interfaces |
| `Achievement` | Enum | 11 achievements in 4 categories |

### Storage Interfaces (`core/storage/`)

9 standalone interfaces: `UserStorage`, `MatchStorage`, `LikeStorage`, `BlockStorage`, `MessagingStorage`, `StatsStorage`, `SocialStorage`, `SwipeSessionStorage`, `ReportStorage`

Implementations in `storage/jdbi/` use `@SqlQuery`/`@SqlUpdate` annotations.

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

## Testing

### Use TestStorages (Centralized Mocks)
```java
// In test class - NO Mockito!
var userStorage = new TestStorages.Users();
var likeStorage = new TestStorages.Likes();
var matchStorage = new TestStorages.Matches();
var blockStorage = new TestStorages.Blocks();
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

| Group | Examples | Access |
|-------|----------|--------|
| Limits | `dailyLikeLimit(100)`, `maxSwipesPerSession(500)` | `CONFIG.dailyLikeLimit()` |
| Validation | `minAge(18)`, `maxAge(120)`, `minHeightCm(50)`, `maxHeightCm(300)` | `CONFIG.minAge()` |
| Algorithm | `nearbyDistanceKm(5)`, `similarAgeDiff(2)`, `minSharedInterests(3)` | `CONFIG.nearbyDistanceKm()` |
| Weights | `distanceWeight(0.15)`, `interestWeight(0.25)`, `lifestyleWeight(0.25)` | `CONFIG.distanceWeight()` |

**Usage:** `private static final AppConfig CONFIG = AppConfig.defaults();`
**Custom:** `AppConfig.builder().dailyLikeLimit(50).minAge(21).build()`

## NEVER Do These

- ❌ Import framework/DB in `core/` (zero coupling)
- ❌ Skip `Objects.requireNonNull()` in constructors
- ❌ Return mutable collections directly
- ❌ Forget `static` on nested types
- ❌ Use Mockito (use `TestStorages.*` instead)
- ❌ Throw from services (return `*Result` records)
- ❌ Hardcode thresholds (use `AppConfig.defaults()`)
- ❌ Call `new User(...)` in mappers (use `StorageBuilder`)
- ❌ Use `HashSet` for enums (use `EnumSet`)
- ❌ Forget `touch()` in setters

## Key Data Flows

**Candidate Discovery:** `CandidateFinder` → 7 filters: self → ACTIVE → no interaction → mutual gender → mutual age → distance → dealbreakers → sort by distance

**Match Quality:** Distance(15%) + Age(10%) + Interests(25%) + Lifestyle(25%) + Pace(15%) + Response(10%)

**Messaging:** ACTIVE match required → validate users → create conversation → save message → return `SendResult`

## Documentation Index

| Doc | Purpose |
|-----|---------|
| `AGENTS.md` | Full coding standards |
| `docs/architecture.md` | Mermaid diagrams |
| `docs/completed-plans/` | Completed designs |
| `CONSOLIDATED_CODE_REVIEW_FINDINGS.md` | Code review findings |

## Recent Updates (2026-02)

- **02-03**: Fixed 25+ nested types to `public static`; added `NestedTypeVisibilityTest`
- **02-01**: Added `AppBootstrap`, `AppSession`, `HandlerFactory` for unified init
- **01-31**: Centralized validation in `AppConfig`; reorganized `ServiceRegistry`
- **01-30**: UI Action Handler pattern; `BaseController`; keyboard shortcuts
- **01-29**: JDBI migration complete; deleted 12 H2*Storage classes

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
2|2026-01-16 00:00:00|agent:claude_code|docs|CLAUDE.md slimmed 49k→20k chars|CLAUDE.md
3|2026-01-16 01:00:00|agent:claude_code|docs|Enhanced with coding standards, patterns, anti-patterns|CLAUDE.md
4|2026-01-25 12:00:00|agent:claude_code|docs|Updated CLAUDE.md for post-consolidation|CLAUDE.md
5|2026-01-28 12:00:00|agent:claude_code|docs|Updated stats, added ValidationService|CLAUDE.md
6|2026-01-29 16:30:00|agent:claude_code|storage|JDBI migration complete|storage/jdbi/*,CLAUDE.md
7|2026-01-30 14:00:00|agent:claude_code|ui-controllers|Action handler pattern|ui/controller/*,CLAUDE.md
8|2026-02-01 17:30:00|agent:claude_code|docs-fix|Fixed nested type visibility rules|User.java,CLAUDE.md
9|2026-02-03 19:50:00|agent:claude_code|docs-update|AppBootstrap/AppSession/HandlerFactory docs|CLAUDE.md
10|2026-02-03 20:30:00|agent:claude_code|docs-optimize|Added Critical Gotchas, StorageBuilder, TestStorages, EnumSet patterns; condensed Recent Updates|CLAUDE.md
---AGENT-LOG-END---
