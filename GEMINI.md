# GEMINI.md — AI Agent Operational Context

> **Verified against source code:** 2026-02-15
> **Codebase:** 135 Java files (77 main + 58 test) | ~43K lines (~33K code) | 802 tests | 60% line coverage min

---

## 1. Project Identity

A **Phase 2.1** console + GUI dating app built with **Java 25** (preview features enabled), Maven, H2/JDBI, JavaFX 25, and Javalin.
Features: matching, messaging, relationship transitions, pace compatibility, achievements, trust & safety, analytics.

**Philosophy:**
- **Domain Purity** — `core/` has **zero** imports from `storage/`, `ui/`, `app/`, or any framework (JDBI, JavaFX, Javalin, Jackson).
- **Fail-Fast** — Constructors use `Objects.requireNonNull`. State violations throw `IllegalStateException`.
- **Deterministic** — IDs, daily picks, and scores are reproducible. All timestamps use `AppClock.now()`, never `Instant.now()`.
- **Centralized Config** — All magic numbers live in `AppConfig` (57-parameter record).

---

## 2. Architecture (Four Layers — Strict Dependency Order)

```
┌──────────────────────────────────────────────────────────────┐
│  PRESENTATION              CLI  │  JavaFX (MVVM)  │  REST   │
│  (app/cli/, ui/, app/api/)                                   │
├──────────────────────────────────────────────────────────────┤
│  DOMAIN                    Services + Models + Interfaces    │
│  (core/**)                 Zero framework/DB imports         │
├──────────────────────────────────────────────────────────────┤
│  INFRASTRUCTURE            JDBI + Schema + DatabaseManager   │
│  (storage/**)              Implements core/storage/*         │
├──────────────────────────────────────────────────────────────┤
│  PLATFORM                  H2 (embedded) + JVM + OS          │
└──────────────────────────────────────────────────────────────┘
```

**Dependency rule:** Each layer depends only on layers **below** it. `core/` depends on nothing external. `storage/` depends on `core/`. `app/` and `ui/` depend on `core/` and sometimes `storage/`.

---

## 3. Package Map (Actual Source Tree)

```
datingapp/
├── Main.java                          CLI entry point + menu loop (20-option switch)
│
├── core/                              ─── DOMAIN LAYER ───
│   ├── AppClock.java                  Testable clock wrapper (TestClock in tests)
│   ├── AppConfig.java                 57-parameter immutable record + Builder
│   ├── AppSession.java                Singleton: current logged-in user
│   ├── EnumSetUtil.java               Null-safe EnumSet utilities
│   ├── LoggingSupport.java            Default logging methods (PMD-safe)
│   ├── PerformanceMonitor.java        Timing + metrics collection (256 stripes)
│   ├── ScoringConstants.java          Match quality tier thresholds
│   ├── ServiceRegistry.java           Holds all services + storages (DI container)
│   │
│   ├── model/                         Primary domain entities
│   │   ├── User.java                  Mutable entity, state machine, 843 lines
│   │   │                              Nested: Gender, UserState, VerificationMethod,
│   │   │                                      ProfileNote, StorageBuilder, Lifestyle.*
│   │   └── Match.java                 Mutable entity, state machine, deterministic ID
│   │
│   ├── storage/                       Storage INTERFACES (contracts only)
│   │   ├── UserStorage.java           Users + ProfileNotes
│   │   ├── InteractionStorage.java    Likes + Matches + atomic undo
│   │   ├── CommunicationStorage.java  Conversations + Messages + FriendRequests + Notifications
│   │   ├── AnalyticsStorage.java      Stats + Achievements + Sessions + DailyPicks + ProfileViews
│   │   └── TrustSafetyStorage.java    Blocks + Reports
│   │
│   ├── connection/                    Messaging & social domain
│   │   ├── ConnectionModels.java      Message, Conversation, Like, Block, Report, FriendRequest, Notification
│   │   └── ConnectionService.java     Send messages, relationship transitions, SendResult pattern
│   │
│   ├── matching/                      Discovery & matching domain
│   │   ├── CandidateFinder.java       7-stage filter pipeline (includes GeoUtils/Haversine)
│   │   ├── MatchingService.java       Like/pass/unmatch/block + mutual-like detection (Builder pattern)
│   │   ├── MatchQualityService.java   6-factor weighted compatibility scoring
│   │   └── UndoService.java           Time-windowed undo (default 30s)
│   │
│   ├── profile/                       Profile management domain
│   │   ├── ProfileService.java        Completion scoring, achievements, behavior analysis
│   │   ├── ValidationService.java     Field-level validation against AppConfig limits
│   │   └── MatchPreferences.java      Dealbreakers + PacePreferences + Interest enum (39 values, 6 categories) + Lifestyle enums
│   │
│   ├── metrics/                       Analytics & engagement domain
│   │   ├── ActivityMetricsService.java Session lifecycle, stats aggregation
│   │   ├── EngagementDomain.java      UserStats, PlatformStats, Achievement enum (11), UserAchievement
│   │   └── SwipeState.java            SwipeSession + Undo.Storage (nested interface)
│   │
│   ├── recommendation/                Discovery & recommendations domain
│   │   ├── RecommendationService.java Daily limits, daily picks, candidate ranking
│   │   └── Standout.java             Standout candidate data + nested Storage interface
│   │
│   └── safety/                        Trust & safety domain
│       └── TrustSafetyService.java    Block/report actions, auto-ban at threshold
│
├── app/                               ─── PRESENTATION LAYER ───
│   ├── bootstrap/
│   │   └── ApplicationStartup.java   Synchronized idempotent init, config loading from JSON, env overrides
│   ├── cli/
│   │   ├── shared/CliTextAndInput.java  Constants + nested InputReader + nested EnumMenu
│   │   ├── matching/MatchingHandler.java
│   │   ├── profile/ProfileHandler.java
│   │   ├── connection/MessagingHandler.java
│   │   ├── safety/SafetyHandler.java
│   │   └── metrics/StatsHandler.java
│   └── api/
│       └── RestApiServer.java         Javalin HTTP endpoints (routes inlined)
│
├── storage/                           ─── INFRASTRUCTURE LAYER ───
│   ├── DatabaseManager.java           H2 connection pool (HikariCP), singleton lifecycle
│   ├── StorageFactory.java            Wires JDBI impls → services → ServiceRegistry
│   ├── schema/
│   │   ├── SchemaInitializer.java     DDL: 18 application tables (+ schema_version metadata via MigrationRunner), indexes, FK constraints (IF NOT EXISTS)
│   │   └── MigrationRunner.java       Schema evolution (ALTER TABLE, etc.)
│   └── jdbi/
│       ├── profile/JdbiUserStorage.java         → UserStorage
│       ├── matching/JdbiMatchmakingStorage.java → InteractionStorage + Undo.Storage
│       ├── connection/JdbiConnectionStorage.java→ CommunicationStorage
│       ├── metrics/JdbiMetricsStorage.java      → AnalyticsStorage + Standout.Storage
│       ├── safety/JdbiTrustSafetyStorage.java   → TrustSafetyStorage (SqlObject directly)
│       └── shared/JdbiTypeCodecs.java           Null-safe RS readers, EnumSet codecs
│
└── ui/                                ─── JAVAFX PRESENTATION LAYER ───
    ├── DatingApp.java                 JavaFX Application entry point
    ├── NavigationService.java         Singleton: scene transitions, history stack (max 20)
    ├── UiComponents.java              Reusable UI component factories
    ├── ViewModelFactory.java          Lazy ViewModel creation, adapter wiring, session binding
    ├── screen/                        FXML Controllers (9 total, all extend BaseController)
    ├── popup/MilestonePopupController.java
    ├── viewmodel/screen/              One ViewModel per screen (8 total)
    ├── viewmodel/shared/ViewModelErrorSink.java  @FunctionalInterface for error callbacks
    ├── viewmodel/data/UiDataAdapters.java        UiUserStore + UiMatchDataAccess (interfaces + impls)
    ├── feedback/UiFeedbackService.java  Toast notifications, confirmation dialogs
    ├── animation/UiAnimations.java      Fade, slide transitions
    ├── constants/UiConstants.java       Timing durations, sizing constants
    └── util/ImageCache.java             Lazy-loaded image caching
```

---

## 4. Critical Gotchas (Compilation/Runtime Failure Sources)

| Mistake                   | Wrong                                                  | Correct                                                       |
|---------------------------|--------------------------------------------------------|---------------------------------------------------------------|
| Non-static nested type    | `public record Y() {}`                                 | `public **static** record Y() {}`                             |
| EnumSet null crash        | `EnumSet.copyOf(set)`                                  | `set != null ? EnumSet.copyOf(set) : EnumSet.noneOf(X.class)` |
| Exposed mutable field     | `return interests;`                                    | `return EnumSet.copyOf(interests);`                           |
| Missing `touch()`         | `this.name = name;`                                    | `this.name = name; touch();`                                  |
| Service throws exception  | `throw new MessagingException(...)`                    | `return SendResult.failure(msg, code)`                        |
| Hardcoded threshold       | `if (age < 18)`                                        | `if (age < CONFIG.minAge())`                                  |
| Wrong pair ID             | `a + "_" + b`                                          | `a.compareTo(b) < 0 ? a+"_"+b : b+"_"+a`                      |
| Raw `Instant.now()`       | `this.updatedAt = Instant.now()`                       | `this.updatedAt = AppClock.now()`                             |
| ViewModel imports storage | `import datingapp.core.storage.*`                      | `import datingapp.ui.viewmodel.data.UiDataAdapters.*`         |
| Standalone enum           | `import datingapp.core.Gender`                         | `User.Gender` (nested in User)                                |
| PMD + Spotless            | Add `// NOPMD` then verify fails                       | Run `spotless:apply` after NOPMD, then `verify`               |
| Stale class refs          | `Toast`, `UiSupport`, `HandlerFactory`, `AppBootstrap` | `UiFeedbackService`, deleted, deleted, `ApplicationStartup`   |

---

## 5. Key Patterns (Copy-Paste Ready)

### 5.1 AppConfig Access
```java
// ALWAYS access config this way in any service or utility
private static final AppConfig CONFIG = AppConfig.defaults();
// Custom: AppConfig.builder().dailyLikeLimit(50).minAge(21).build()
```

### 5.2 Entity Construction: New vs DB-Load
```java
// NEW entity — generates UUID + uses AppClock.now() for timestamp
User alice = new User(UUID.randomUUID(), "Alice");
Match match = Match.create(userA, userB); // deterministic ID

// FROM DATABASE — bypass validation via StorageBuilder
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(bio).birthDate(birthDate).gender(gender)
    .interestedIn(interestedIn).state(state).build();
```

### 5.3 Deterministic IDs (Two-User Entities)
```java
// Same ID regardless of parameter order (Match, Conversation)
public static String generateId(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
}
```

### 5.4 State Machine Pattern (User)
```
INCOMPLETE → activate() → ACTIVE ↔ pause()/resume() ↔ PAUSED → ban() → BANNED (terminal)
```
```java
// Always use state transition methods, never set state directly
user.activate(); // validates all required fields present
user.pause(); user.resume(); user.ban();
```

### 5.5 State Machine Pattern (Match)
```
ACTIVE → unmatch() → UNMATCHED | toFriends() → FRIENDS | gracefulExit() → GRACEFUL_EXIT | block() → BLOCKED
```
All terminal states set `endedAt`, `endedBy`, and `endReason`.

### 5.6 Result Pattern (Services Never Throw)
```java
// Services return result records, never throw business exceptions
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}
// Caller: if (result.success()) { ... } else { log.warn(result.errorMessage()); }
```

### 5.7 Touch Pattern (Mutable Entities)
```java
private void touch() { this.updatedAt = AppClock.now(); } // NOT Instant.now()
public void setBio(String bio) { this.bio = bio; touch(); } // EVERY setter calls touch()
```

### 5.8 EnumSet Defensive Patterns
```java
// Setter — handle null safely
public void setInterestedIn(Set<Gender> interestedIn) {
    this.interestedIn = interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
    touch();
}
// Getter — never expose internal reference
public Set<Gender> getInterestedIn() {
    return interestedIn.isEmpty() ? EnumSet.noneOf(Gender.class) : EnumSet.copyOf(interestedIn);
}
```

### 5.9 MatchingService Builder (Optional Dependencies)
```java
MatchingService matchingService = MatchingService.builder()
    .interactionStorage(interactionStorage)        // required
    .trustSafetyStorage(trustSafetyStorage)        // required
    .userStorage(userStorage)                      // required
    .activityMetricsService(activityMetricsService) // optional
    .undoService(undoService)                      // optional
    .dailyService(recommendationService)           // optional
    .build();
```

### 5.10 CLI Handler Dependencies
```java
// Each handler declares a Dependencies record; instantiated directly in Main.java
public class MatchingHandler {
    public static record Dependencies(CandidateFinder candidateFinderService, MatchingService matchingService,
            InteractionStorage interactionStorage, /*...*/ AppSession session, InputReader inputReader) {
        public Dependencies { Objects.requireNonNull(candidateFinderService); /*...*/ }
    }
}
```

### 5.11 JDBI Storage Implementation Pattern
```java
// Outer class implements core storage interface; inner Dao has JDBI annotations
public final class JdbiUserStorage implements UserStorage {
    private final Dao dao;
    public JdbiUserStorage(Jdbi jdbi) { this.dao = jdbi.onDemand(Dao.class); }

    @Override public void save(User user) { dao.save(new UserSqlBindings(user)); }

    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
        void save(@BindBean UserSqlBindings helper);
        @SqlQuery("SELECT ... FROM users WHERE id = :id")
        User get(@Bind("id") UUID id);
    }

    public static class Mapper implements RowMapper<User> { /* uses StorageBuilder */ }
    public static final class UserSqlBindings { /* wraps User getters for @BindBean */ }
}
// NOTE: JdbiTrustSafetyStorage is wired differently — jdbi.onDemand(JdbiTrustSafetyStorage.class) directly
```

### 5.12 ViewModel Error Handling (JavaFX)
```java
// ViewModelErrorSink.java — @FunctionalInterface
@FunctionalInterface
public interface ViewModelErrorSink { void onError(String message); }

// In ViewModel: set errorHandler, then notify via Platform.runLater
// In Controller: viewModel.setErrorHandler(UiFeedbackService::showError);
```

### 5.13 UI Data Access Adapters (No Direct Storage in ViewModels)
```java
// ViewModels use UiDataAdapters interfaces, NOT core storage interfaces
import datingapp.ui.viewmodel.data.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiMatchDataAccess;

// ViewModelFactory wires adapter implementations
UiUserStore userStore = new UiDataAdapters.StorageUiUserStore(services.getUserStorage());
```

### 5.14 Navigation Context Passing (JavaFX)
```java
// Before navigating — set context
navigationService.setNavigationContext(matchedUserId);
navigationService.navigateTo(ViewType.CHAT);

// In target controller — consume context
Object context = navigationService.consumeNavigationContext();
if (context instanceof UUID userId) { viewModel.selectConversationWithUser(userId); }
```

---

## 6. Dependency Injection (Wiring Mandate)

The project uses **manual DI** via `StorageFactory` + `ServiceRegistry`. There are **no** framework annotations.

### Wiring Flow
```
ApplicationStartup.initialize()
   │
   ├── load config (JSON file + env overrides → AppConfig)
   ├── DatabaseManager.getInstance() (HikariCP pool + SchemaInitializer)
   └── StorageFactory.buildH2(dbManager, config)
          │
          ├── Create Jdbi instance + register plugins/codecs
          ├── Instantiate 5 JDBI storage implementations
          ├── Wire 10 services in dependency order:
          │     CandidateFinder → ProfileService → RecommendationService →
          │     UndoService → ActivityMetricsService → MatchingService (builder) →
          │     MatchQualityService → ConnectionService → TrustSafetyService
          └── Return ServiceRegistry (all fields non-null via requireNonNull)
```

### Adding a New Component: Checklist
1. **Model** — Create `record`/`enum`/`class` in `core/` (appropriate domain subpackage).
2. **Storage Interface** — Add methods to existing `*Storage` interface in `core/storage/`, or create new interface.
3. **Storage Impl** — Add JDBI implementation in `storage/jdbi/<domain>/`.
4. **Schema** — Add/update SQL in `SchemaInitializer.java` (use `IF NOT EXISTS`).
5. **Service** — Create service in `core/<domain>/` using constructor injection.
6. **ServiceRegistry** — Add field + getter + constructor param in `ServiceRegistry.java`.
7. **StorageFactory** — Instantiate and wire in `StorageFactory.buildH2()`.
8. **UI** — Add/update ViewModel (in `ui/viewmodel/screen/`) and Controller (in `ui/screen/`). Wire in `ViewModelFactory`.
9. **Verify** — `mvn spotless:apply && mvn verify`.

**ServiceRegistry aliases:** `getSessionService()` and `getStatsService()` both return `ActivityMetricsService` — there is no separate SessionService or StatsService class.

---

## 7. Technology Stack (Exact Versions from pom.xml)

| Layer           | Technology                      | Version                            | Purpose                                    |
|-----------------|---------------------------------|------------------------------------|--------------------------------------------|
| Language        | Java                            | 25 (`--enable-preview` everywhere) | `maven.compiler.release = 25`              |
| Build           | Maven                           | 3.9+                               | Compile, test, quality checks              |
| Database        | H2                              | 2.4.240                            | Embedded in-process database               |
| SQL Framework   | JDBI 3                          | 3.51.0                             | `@SqlQuery` / `@SqlUpdate` declarative SQL |
| Connection Pool | HikariCP                        | 6.3.0                              | Database connection pooling                |
| GUI Framework   | JavaFX                          | 25.0.2                             | Desktop UI (FXML + CSS)                    |
| GUI Theme       | AtlantaFX                       | 2.1.0                              | Material Design-inspired theming           |
| GUI Icons       | Ikonli                          | 12.4.0                             | MaterialDesign2 icon pack                  |
| REST Framework  | Javalin                         | 6.7.0                              | Lightweight HTTP endpoints                 |
| JSON            | Jackson                         | 2.21.0                             | Config parsing + REST serialization        |
| Logging API     | SLF4J                           | 2.0.17                             | Logging facade                             |
| Logging Impl    | Logback                         | 1.5.28                             | Logging backend                            |
| Testing         | JUnit 5                         | 5.14.2                             | Unit + integration tests                   |
| Formatting      | Spotless (Palantir Java Format) | plugin: 3.2.1 / format: 2.85.0     | 4-space indentation                        |
| Style Check     | Checkstyle                      | 13.2.0 (via plugin 3.6.0)          | `checkstyle.xml`, fails on violation       |
| Static Analysis | PMD                             | plugin 3.28.0                      | Custom `pmd-rules.xml`, fails on violation |
| Coverage        | JaCoCo                          | 0.8.14                             | 60% minimum line coverage                  |
| Nullable        | JetBrains Annotations           | 24.1.0                             | `@Nullable` / `@NotNull`                   |

### Build Pipeline (`mvn verify`)
```
validate(checkstyle:check) → compile → test → jacoco:report → jar →
verify(spotless:check, pmd:check, jacoco:check)
```
**Always run `mvn spotless:apply` before `mvn verify`** to avoid formatting failures.

---

## 8. Testing Architecture

### Principles
- **No Mockito** — all tests use `TestStorages.*` (in-memory implementations in `core/testutil/`).
- **TestClock** — deterministic time via `AppClock.setTestClock(fixedInstant)`.
- **H2 in-memory** — CLI handler tests use real H2 databases (`jdbc:h2:mem:testname_UUID`).
- **Nested test classes** — `@Nested @DisplayName("scenario")` for grouping.
- **Timeouts** — `@Timeout(5)` or `@Timeout(10)` on all test classes.
- **No star imports** — always import specific classes.

### TestStorages (5 In-Memory Implementations)
```java
var users       = new TestStorages.Users();           // → UserStorage
var interactions = new TestStorages.Interactions();    // → InteractionStorage
var comms       = new TestStorages.Communications();   // → CommunicationStorage
var analytics   = new TestStorages.Analytics();        // → AnalyticsStorage
var trustSafety = new TestStorages.TrustSafety();     // → TrustSafetyStorage
```
These use `HashMap`/`ArrayList` internally and implement the **full** storage interface contract.

### Test Helper Pattern
```java
// Place at end of test class — create fully-populated active users for tests
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

### Test Structure Pattern
```java
@Timeout(5)
class MyServiceTest {
    @Nested @DisplayName("When user is active")
    class WhenActive {
        @Test @DisplayName("should allow messaging")
        void allowsMessaging() { /* Arrange → Act → Assert */ }
    }
}
```

### Test Organization (58 test files)
```
src/test/java/datingapp/
├── app/                     ConfigLoaderTest, RestApiRoutesTest
│   └── cli/                 9 CLI handler tests
├── core/                    30+ unit tests (domain logic)
│   └── testutil/            TestStorages.java
└── storage/                 JDBI integration tests (real H2 in-memory)
```

---

## 9. NEVER Do These (Hard Rules)

- ❌ Import framework/DB classes in `core/` — zero coupling to JDBI, JavaFX, Javalin, Jackson
- ❌ Skip `Objects.requireNonNull()` in constructors
- ❌ Return mutable collections directly (use `EnumSet.copyOf()`, `List.copyOf()`)
- ❌ Forget `static` on nested types (records, enums, classes inside other classes)
- ❌ Use Mockito anywhere (use `TestStorages.*`)
- ❌ Throw business exceptions from services (return `*Result` records)
- ❌ Hardcode thresholds or magic numbers (use `AppConfig.defaults()` or `ScoringConstants`)
- ❌ Call `new User(...)` in row mappers (use `User.StorageBuilder.create(...)`)
- ❌ Use `HashSet` for enums (use `EnumSet`)
- ❌ Forget `touch()` in entity setters
- ❌ Use `Instant.now()` directly (use `AppClock.now()` for testability)
- ❌ Import `core/storage/*` in ViewModels (use `UiDataAdapters` adapters)
- ❌ Reference `Gender` or `UserState` as standalone (they're `User.Gender`, `User.UserState`)
- ❌ Hardcode animation timings in UI (use `UiConstants.*`)
- ❌ Reference deleted classes: `Toast`, `UiSupport`, `HandlerFactory`, `AppBootstrap`, `CliSupport`
- ❌ Use star imports
- ❌ Use `System.out.println` (use SLF4J logger)
- ❌ Use Spring annotations (`@Service`, `@Repository`, `@Autowired`)
- ❌ Create SQL migration files (use `SchemaInitializer` for DDL)

---

## 10. Quick Commands

```bash
# Build & Run
mvn compile && mvn exec:exec          # CLI (forked JVM, --enable-preview)
mvn javafx:run                         # JavaFX GUI

# Testing & Quality
mvn test                               # All tests (concise output, default)
mvn -Ptest-output-verbose test         # Verbose rerun if failures need detail
mvn -Ptest-output-verbose -Dtest=StatsHandlerTest test   # Single test class verbose
mvn spotless:apply && mvn verify       # Format + full quality gate (REQUIRED before commit)
```

### Build Command Discipline
**NEVER run `mvn verify` or `mvn test` multiple times to extract different info.** Each run repeats the entire pipeline. Run ONCE, capture output, then filter:
```powershell
$out = mvn verify 2>&1 | Out-String
$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
$out | Select-String "Tests run:" | Select-Object -Last 1
$out | Select-String "ERROR|WARNING.*violation"
```

### Prerequisites
- **JDK 25+** with preview features
- **Maven 3.9+**
- **Windows:** `chcp 65001` before CLI for emoji/UTF-8 support

---

## 11. Key Data Flows

### Candidate Discovery (7-Stage Pipeline)
```
All ACTIVE users → exclude self → exclude prior interactions → mutual gender →
mutual age → distance (Haversine) → dealbreakers → sort by distance ASC
```

### Like → Match Creation
```
MatchingService.like(currentUser, candidateId)
  → save Like → record swipe → store undo state
  → check mutual like? → YES: Match.create(a, b), save, return Optional<Match>
                         → NO:  return Optional.empty()
```

### Match Quality Score (6 Weighted Factors = 100%)
| Factor        | Weight | Method                                                   |
|---------------|--------|----------------------------------------------------------|
| Distance      | 15%    | Inverse of distance                                      |
| Age           | 10%    | Inverse of age diff                                      |
| Interests     | 25%    | Jaccard similarity                                       |
| Lifestyle     | 25%    | Weighted smoking/drinking/wantsKids/lookingFor/education |
| Pace          | 15%    | PacePreferences compatibility                            |
| Response Time | 10%    | Average messaging response time                          |

Thresholds in `ScoringConstants`: 90+ Excellent, 75+ Great, 60+ Good, 40+ Fair, <40 Low.

### Report → Auto-Ban
```
TrustSafetyService.report() → validate → save report → auto-block reporter↔reported
  → count reports against user → ≥ autoBanThreshold (default 3) ? → ban user
```

### Messaging
```
ConnectionService.sendMessage() → validate sender ACTIVE → validate conversation exists
  → validate match ACTIVE or FRIENDS → validate content → create Message → save → SendResult
```

---

## 12. Database Schema

**18 application tables** managed by `SchemaInitializer` (DDL with `IF NOT EXISTS`) and `MigrationRunner` (ALTER TABLE evolution), plus the `schema_version` metadata table managed by `MigrationRunner`.

Key tables: `users` (42 columns), `likes`, `matches` (VARCHAR PK, deterministic), `conversations` (deterministic ID), `messages`, `blocks`, `reports`, `friend_requests`, `notifications`, `swipe_sessions`, `user_stats`, `user_achievements`, `profile_notes`, `profile_views`, `standouts`, `daily_pick_views`, `platform_stats`, `undo_states`.

All two-user tables have `ON DELETE CASCADE` FK to `users.id`. ~30 indexes cover lookup-by-user, state filtering, temporal ordering, and composite queries.

---

## 13. Domain-Specific Terminology

| Term                | Definition                                                                        |
|---------------------|-----------------------------------------------------------------------------------|
| **Candidate**       | A potential match found by `CandidateFinder`                                      |
| **Match**           | Mutual like. Deterministic ID: `min(uuidA,uuidB)_max(uuidA,uuidB)`                |
| **Dealbreaker**     | Strict lifestyle/age filter that immediately disqualifies a candidate             |
| **Interest**        | One of 39 predefined enums across 6 categories (in `MatchPreferences.Interest`)   |
| **Daily Pick**      | Seeded-random daily recommendation                                                |
| **Standout**        | High-quality candidate surfaced by standout ranking                               |
| **PacePreferences** | User preferences for dating speed (messaging frequency, time to first date, etc.) |
| **Touch**           | `updatedAt = AppClock.now()` called in every entity setter                        |
| **StorageBuilder**  | Pattern to hydrate entities from DB without triggering field validation           |

---

## 14. Environment & Tools

- **OS:** Windows 11 (PowerShell, `chcp 65001` for UTF-8)
- **IDE:** Antigravity (VS Code Fork)
- **Search:** `ast-grep` (`sg`) for structural code search, `ripgrep` (`rg`) for text search
- **Code Stats:** `tokei` for LOC counting
- **Code Style:** Spotless (Palantir Java Format, 4-space indent)
- **Quality:** Checkstyle + PMD (both fail on violation), JaCoCo (60% line coverage)
- **Config:** `config/app-config.json` (optional), `DATING_APP_*` env var overrides
- **DB Password:** `DATING_APP_DB_PASSWORD` env variable

---

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries.
1|2026-01-30 18:50:00|agent:antigravity|docs|Update GEMINI.md to reflect current tech stack (JDBI, Jackson, JavaFX 25) and architecture|GEMINI.md
2|2026-02-15 18:33:00|agent:antigravity|docs|Complete rewrite: verified all patterns against actual source code, added copy-paste code patterns, critical gotchas, wiring checklist, exact pom.xml versions, data flows, test architecture|GEMINI.md
---AGENT-LOG-END---
