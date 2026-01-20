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

### Package Structure

| Package    | Purpose                  | Rule                                |
|------------|--------------------------|-------------------------------------|
| `core/`    | Pure Java business logic | **ZERO** framework/database imports |
| `storage/` | H2 JDBC implementations  | Implements interfaces from `core/`  |
| `cli/`     | Console UI handlers      | Thin layer calling services         |
| `ui/`      | JavaFX UI (experimental) | Uses AtlantaFX theme                |

### Domain Models (`core/`)

| Model             | Type      | State Machine / Key Fields                                                      |
|-------------------|-----------|---------------------------------------------------------------------------------|
| `User`            | Mutable   | `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`, interests (max 10), verification    |
| `Match`           | Mutable   | `ACTIVE → FRIENDS \| UNMATCHED \| GRACEFUL_EXIT \| BLOCKED`, deterministic ID|
| `Like`            | Immutable | direction (`LIKE`/`PASS`), timestamp                                            |
| `Block`, `Report` | Immutable | Safety records                                                                  |
| `Conversation`    | Mutable   | deterministic ID (`userA_userB`), per-user read timestamps                      |
| `Message`         | Immutable | max 1000 chars, sender, timestamp                                               |
| `FriendRequest`   | Immutable | status: `PENDING → ACCEPTED \| DECLINED \| EXPIRED`                          |
| `Notification`    | Immutable | type, title, message, metadata, read status                                     |
| `PacePreferences` | Immutable | messagingFrequency, timeToFirstDate, communicationStyle, depthPreference        |
| `SwipeSession`    | Mutable   | `ACTIVE → COMPLETED`, velocity tracking                                      |
| `MatchQuality`    | Immutable | score (0-100), star rating (1-5), highlights                                    |
| `Dealbreakers`    | Immutable | lifestyle, physical, age filters                                                |
| `Interest`        | Enum      | 37 interests in 6 categories                                                    |
| `Achievement`     | Enum      | 11 achievements in 4 categories                                                 |

### Services (`core/`)

**Core**: `CandidateFinder` (7-stage filter), `MatchingService`, `ReportService`

**Phase 1**: `UndoService`, `DailyLimitService`, `MatchQualityService`, `DealbreakersEvaluator`, `SessionService`, `ProfilePreviewService`, `StatsService`

**Phase 1.5+**: `InterestMatcher`, `AchievementService`, `DailyPickService`, `LikerBrowserService`, `ProfileCompletionService`, `VerificationService`

**Phase 2.0+**: `MessagingService`, `PaceCompatibilityService`, `RelationshipTransitionService`

### Storage Interfaces (`core/` → `storage/`)

All interfaces defined in `core/`, implemented as `H2*Storage` in `storage/`. Data: `./data/dating.mv.db`

## Coding Standards Quick Reference

### Naming Conventions
| Element    | Convention              | Example                                        |
|------------|-------------------------|------------------------------------------------|
| Classes    | PascalCase              | `UserService`, `H2UserStorage`                 |
| Methods    | camelCase               | `getUserById()`, `createMatch()`               |
| Predicates | `is`/`has`/`can` prefix | `isActive()`, `hasInterests()`, `canMessage()` |
| Constants  | UPPER_SNAKE             | `MAX_DISTANCE_KM`, `DEFAULT_TIMEOUT`           |
| Timestamps | `*At` suffix            | `createdAt`, `updatedAt`, `endedAt`            |

### Type Usage
| Use           | When                                             |
|---------------|--------------------------------------------------|
| `record`      | Immutable data (Message, Like, Block)            |
| `class`       | Mutable entities with state (User, Match)        |
| `enum`        | Fixed sets with metadata (Interest, Achievement) |
| `Optional<T>` | Nullable return values                           |

### Key Patterns

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
- **Use Mockito** - Use in-memory implementations instead
- **Commit without `mvn spotless:apply`** - Formatting is enforced
- **Throw exceptions from CLI handlers** - Return user-friendly messages
- **Mix business logic in storage** - Storage does mapping only
- **Use `new ArrayList<>()` for EnumSets** - Use `EnumSet.noneOf()`

## Storage Layer Patterns

**JDBC Pattern:**
```java
try (Connection conn = dbManager.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setObject(1, uuid);  // UUIDs
    stmt.setString(2, enum.name());  // Enums as VARCHAR
    stmt.setTimestamp(3, Timestamp.from(instant));  // Timestamps
    // ...
} catch (SQLException e) {
    throw new StorageException("Failed to save: " + id, e);
}
```

**Null Handling:**
```java
if (value != null) { stmt.setTimestamp(i, Timestamp.from(value)); }
else { stmt.setNull(i, Types.TIMESTAMP); }

int val = rs.getInt("col"); if (rs.wasNull()) { val = null; }
```

**H2 Upsert:** `MERGE INTO table (id, col) KEY (id) VALUES (?, ?)`

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
static class InMemoryUserStorage implements UserStorage {
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

## Build Tools

| Tool       | Version | Purpose                               |
|------------|---------|---------------------------------------|
| Spotless   | 3.1.0   | Palantir Java Format (4-space indent) |
| Checkstyle | 3.6.0   | Style validation (advisory)           |
| PMD        | 3.28.0  | Bug detection (advisory)              |
| JaCoCo     | 0.8.14  | Coverage (60% min, excludes ui/cli)   |

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
---AGENT-LOG-END---
