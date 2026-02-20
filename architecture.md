# Dating App Architecture

> **Last verified against source code:** 2026-02-19
> **Phase:** 2.1 | **Java 25** + Maven + H2/JDBI + JavaFX 25 + Javalin
> **Codebase:** 139 Java files (81 main + 58 test) | ~45K lines (~34K code) | 802 tests | 60% coverage min

This document describes the **system design** of the Dating App. For quick-reference coding
patterns and gotchas, see [CLAUDE.md](../CLAUDE.md). For coding standards and quality tools,
see [AGENTS.md](../AGENTS.md).

---

## 1. System Layers

The application follows a **four-layer architecture** with strict dependency rules.
Each layer may only depend on layers below it — never upward or sideways.

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

**Dependency rule:** Code in `core/` has **zero** imports from `storage/`, `app/`, `ui/`,
or any framework (JDBI, JavaFX, Javalin). This boundary is enforced by convention and
code review. Services depend only on storage *interfaces* defined in `core/storage/`.

### Why this matters

The clean separation means:
- **Domain logic is testable** without a database (tests use `TestStorages.*` in-memory implementations)
- **Storage can be swapped** (the old H2*Storage classes were replaced by JDBI without touching any service)
- **Multiple UIs coexist** — CLI, JavaFX GUI, and REST API all share the same ServiceRegistry
- **No circular dependencies** — the build order is always `core → storage → app/ui`

---

## 2. Package Map

```
datingapp/
├── Main.java                          CLI entry point + menu loop
│
├── core/                              ─── DOMAIN LAYER ───
│   ├── AppClock.java                  Testable clock (TestClock in tests)
│   ├── AppConfig.java                 57-parameter config record + Builder
│   ├── AppSession.java                Singleton: current logged-in user
│   ├── EnumSetUtil.java               Null-safe EnumSet utilities
│   ├── LoggingSupport.java            Default logging methods (PMD-safe)
│   ├── PerformanceMonitor.java        Timing + metrics collection
│   ├── ScoringConstants.java          Thresholds for match quality tiers
│   ├── ServiceRegistry.java           Holds all services + storages (DI container)
│   │
│   ├── model/                         Primary domain entities
│   │   ├── User.java                  Mutable entity, state machine, nested enums+StorageBuilder
│   │   └── Match.java                 Mutable entity, state machine, deterministic ID
│   │
│   ├── storage/                       Storage INTERFACES (contracts only)
│   │   ├── UserStorage.java
│   │   ├── InteractionStorage.java    Likes + Matches + undo transactions
│   │   ├── CommunicationStorage.java  Conversations + Messages + FriendRequests + Notifications
│   │   ├── AnalyticsStorage.java      Stats + Achievements + Sessions + DailyPicks + ProfileViews
│   │   └── TrustSafetyStorage.java    Blocks + Reports
│   │
│   ├── connection/                    Messaging & social domain
│   │   ├── ConnectionModels.java      Message, Conversation, Like, Block, Report,
│   │   │                              FriendRequest, Notification (6 records/classes)
│   │   └── ConnectionService.java     Send messages, relationship transitions
│   │
│   ├── matching/                      Discovery & matching domain
│   │   ├── CandidateFinder.java       7-stage filter pipeline + GeoUtils
│   │   ├── MatchingService.java       Like/pass/unmatch/block + mutual-like detection
│   │   ├── MatchQualityService.java   6-factor weighted compatibility scoring
│   │   └── UndoService.java           Time-windowed undo (default 30s)
│   │
│   ├── profile/                       Profile management domain
│   │   ├── ProfileService.java        Completion scoring, achievements, behavior analysis
│   │   ├── ValidationService.java     Field-level validation against AppConfig
│   │   └── MatchPreferences.java      Dealbreakers + PacePreferences records
│   │
│   ├── metrics/                       Analytics & engagement domain
│   │   ├── ActivityMetricsService.java Session lifecycle, stats aggregation (256 stripes)
│   │   ├── EngagementDomain.java       UserStats, PlatformStats, Achievement, UserAchievement
│   │   └── SwipeState.java             SwipeSession + Undo.Storage records
│   │
│   ├── recommendation/                Discovery & recommendations domain
│   │   ├── RecommendationService.java  Daily limits, daily picks, candidate ranking
│   │   └── Standout.java              Standout candidate data + Storage interface
│   │
│   └── safety/                        Trust & safety domain
│       └── TrustSafetyService.java    Block/report actions, auto-ban at threshold
│
├── app/                               ─── PRESENTATION LAYER ───
│   ├── bootstrap/
│   │   └── ApplicationStartup.java    Synchronized idempotent init, config loading
│   │
│   ├── cli/                           Console UI
│   │   ├── shared/
│   │   │   └── CliTextAndInput.java   Constants + InputReader + EnumMenu (nested)
│   │   ├── matching/
│   │   │   └── MatchingHandler.java   Browse, matches, likes, notifications, standouts
│   │   ├── profile/
│   │   │   └── ProfileHandler.java    Create/select user, complete profile, notes
│   │   ├── connection/
│   │   │   └── MessagingHandler.java  Conversations, send messages, unread counts
│   │   ├── safety/
│   │   │   └── SafetyHandler.java     Block, report, manage blocks, verify
│   │   └── metrics/
│   │       └── StatsHandler.java      Statistics, achievements
│   │
│   └── api/
│       └── RestApiServer.java         Javalin HTTP endpoints (routes inlined)
│
├── storage/                           ─── INFRASTRUCTURE LAYER ───
│   ├── DatabaseManager.java           H2 connection pool (HikariCP), singleton lifecycle
│   ├── StorageFactory.java            Wires JDBI impls → services → ServiceRegistry
│   │
│   ├── schema/
│   │   ├── SchemaInitializer.java     DDL: 18 tables + indexes + FK constraints
│   │   └── MigrationRunner.java       Schema evolution (ALTER TABLE, etc.)
│   │
│   └── jdbi/                          JDBI implementations (domain subpackages)
│       ├── profile/
│       │   └── JdbiUserStorage.java        → implements UserStorage
│       ├── matching/
│       │   └── JdbiMatchmakingStorage.java → implements InteractionStorage
│       ├── connection/
│       │   └── JdbiConnectionStorage.java  → implements CommunicationStorage
│       ├── metrics/
│       │   └── JdbiMetricsStorage.java     → implements AnalyticsStorage
│       ├── safety/
│       │   └── JdbiTrustSafetyStorage.java → implements TrustSafetyStorage
│       └── shared/
│           └── JdbiTypeCodecs.java         Null-safe RS readers, EnumSet codecs
│
└── ui/                                ─── JAVAFX PRESENTATION LAYER ───
    ├── DatingApp.java                 JavaFX Application entry point
    ├── NavigationService.java         Singleton: scene transitions, history stack
    ├── UiComponents.java              Reusable UI component factories
    ├── ViewModelFactory.java          Lazy ViewModel creation, adapter wiring
    │
    ├── screen/                        FXML Controllers (extend BaseController)
    │   ├── BaseController.java        Abstract: overlay mgmt, lifecycle hooks
    │   ├── LoginController.java
    │   ├── DashboardController.java
    │   ├── ProfileController.java
    │   ├── MatchingController.java
    │   ├── MatchesController.java
    │   ├── ChatController.java
    │   ├── StatsController.java
    │   └── PreferencesController.java
    │
    ├── popup/
    │   └── MilestonePopupController.java
    │
    ├── viewmodel/
    │   ├── screen/                    One ViewModel per screen (8 total)
    │   │   ├── LoginViewModel.java
    │   │   ├── DashboardViewModel.java
    │   │   ├── ProfileViewModel.java
    │   │   ├── MatchingViewModel.java
    │   │   ├── MatchesViewModel.java
    │   │   ├── ChatViewModel.java
    │   │   ├── StatsViewModel.java
    │   │   └── PreferencesViewModel.java
    │   │
    │   ├── shared/
    │   │   └── ViewModelErrorSink.java   @FunctionalInterface for error callbacks
    │   │
    │   └── data/
    │       └── UiDataAdapters.java       UiUserStore + UiMatchDataAccess (interfaces + impls)
    │
    ├── feedback/
    │   └── UiFeedbackService.java     Toast notifications, confirmation dialogs
    │
    ├── animation/
    │   └── UiAnimations.java          Fade, slide transitions
    │
    ├── constants/
    │   └── UiConstants.java           Timing durations, sizing constants
    │
    └── util/
        └── ImageCache.java            Lazy-loaded image caching
```

---

## 3. Domain Models & State Machines

### 3.1 User Lifecycle

The `User` entity is the central domain object. It is **mutable** — setters update fields
and call `touch()` to record the modification time via `AppClock.now()`.

```
                    ┌─────────────┐
   new User(id,name)│  INCOMPLETE │
                    └──────┬──────┘
                           │ activate()
                           │ (requires: bio, birthDate, gender,
                           │  interestedIn, photo, pacePreferences)
                           ▼
                    ┌─────────────┐
              ┌────▶│   ACTIVE    │◀────┐
              │     └──────┬──────┘     │
              │            │            │
     resume() │    pause() │            │ resume()
              │            ▼            │
              │     ┌─────────────┐     │
              └─────│   PAUSED    │─────┘
                    └──────┬──────┘
                           │
                           │ ban() (auto or manual)
                           ▼
                    ┌─────────────┐
                    │   BANNED    │  (terminal)
                    └─────────────┘
```

**Nested types in User:**
- `User.Gender` — `MALE`, `FEMALE`, `OTHER`
- `User.UserState` — `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED`
- `User.VerificationMethod` — `EMAIL`, `PHONE`
- `User.ProfileNote` — private notes about other users
- `User.StorageBuilder` — factory for hydrating from DB (bypasses validation)

**Key invariant:** Every setter calls `touch()` → `this.updatedAt = AppClock.now()`. Tests
use `TestClock` (via `AppClock.setTestClock()`) to control time.

### 3.2 Match Lifecycle

A `Match` is created when two users mutually like each other. The ID is **deterministic**:
the lexicographically smaller UUID comes first, joined by `_`.

```
                    ┌─────────────┐
  Match.create(a,b) │   ACTIVE    │  canMessage() = true
                    └──┬──┬──┬──┬┘
                       │  │  │  │
           unmatch()   │  │  │  │  block()
              ┌────────┘  │  │  └────────┐
              ▼           │  │           ▼
       ┌────────────┐    │  │    ┌────────────┐
       │  UNMATCHED  │    │  │    │  BLOCKED   │
       └────────────┘    │  │    └────────────┘
                         │  │
         toFriends()     │  │  gracefulExit()
              ┌──────────┘  └──────────┐
              ▼                        ▼
       ┌────────────┐          ┌──────────────┐
       │  FRIENDS   │          │ GRACEFUL_EXIT│
       └────────────┘          └──────────────┘
```

All terminal states set `endedAt`, `endedBy`, and `endReason`. Soft-delete via `deletedAt` field.

### 3.3 ConnectionModels (core/connection/)

Six nested types consolidated into a single file:

| Type | Kind | Purpose | Key Fields |
|------|------|---------|------------|
| `Message` | record | Chat message | `id, conversationId, senderId, content, createdAt` |
| `Conversation` | class | Chat thread between two users | Deterministic ID, read timestamps per user, archive state, visibility flags |
| `Like` | record | Swipe action | `whoLikes, whoGotLiked, direction (LIKE/PASS)` |
| `Block` | record | User block | `blockerId, blockedId` |
| `Report` | record | Abuse report | `reporterId, reportedUserId, reason, description` |
| `FriendRequest` | record | Friendship transition request | States: `PENDING → ACCEPTED/DECLINED/EXPIRED` |
| `Notification` | record | System notification | Types: `MATCH_FOUND, NEW_MESSAGE, FRIEND_REQUEST, ...` |

**Design rationale:** These types were originally spread across `Messaging.java`,
`UserInteractions.java`, and `Social.java`. Consolidating into `ConnectionModels` reflects
their shared domain: they all describe interactions between two users.

### 3.4 Supporting Domain Types

| Type | Package | Purpose |
|------|---------|---------|
| `MatchPreferences` | `core/profile/` | `Dealbreakers` record (lifestyle/physical filters) + `PacePreferences` record |
| `EngagementDomain` | `core/metrics/` | `UserStats`, `PlatformStats`, `Achievement` enum (11 values), `UserAchievement` |
| `SwipeState` | `core/metrics/` | `Session` (swipe session lifecycle) + `Undo` (undo state + Storage interface) |
| `Standout` | `core/recommendation/` | Standout candidate data + nested `Storage` interface |

---

## 4. Storage Layer

### 4.1 Interface Design (5 Interfaces)

Storage interfaces live in `core/storage/` and are the **only** way services access data.
Each interface is organized by **aggregate boundary**, not by entity type:

| Interface | Entities Managed | Method Count |
|-----------|-----------------|--------------|
| `UserStorage` | User, ProfileNote | ~11 methods |
| `InteractionStorage` | Like, Match + atomic undo | ~20 methods |
| `CommunicationStorage` | Conversation, Message, FriendRequest, Notification | ~25 methods |
| `AnalyticsStorage` | UserStats, PlatformStats, ProfileView, Achievement, DailyPick, Session | ~25 methods |
| `TrustSafetyStorage` | Block, Report | ~10 methods |

**Design rationale:** The original design had 11 fine-grained interfaces (LikeStorage,
MatchStorage, BlockStorage, MessageStorage, etc.). These were consolidated to 5 because:
1. Most JDBI implementations were thin wrappers around a single Jdbi instance
2. Transactions (e.g., atomic undo of like+match) required crossing interface boundaries
3. 5 interfaces map cleanly to 5 bounded contexts in the domain

### 4.2 JDBI Implementation Pattern

Each JDBI implementation follows the **outer-class-wraps-inner-Dao** pattern:

```java
// Outer class implements the core storage interface
public final class JdbiUserStorage implements UserStorage {

    private final Dao dao;

    public JdbiUserStorage(Jdbi jdbi) {
        this.dao = jdbi.onDemand(Dao.class);
    }

    @Override
    public void save(User user) {
        dao.save(new UserSqlBindings(user));
    }

    // Inner DAO with JDBI annotations
    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
        void save(@BindBean UserSqlBindings helper);

        @SqlQuery("SELECT ... FROM users WHERE id = :id")
        User get(@Bind("id") UUID id);
    }

    // Inner mapper: ResultSet → domain entity
    public static class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            return User.StorageBuilder.create(
                JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                rs.getString("name"),
                JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at")
            ).bio(rs.getString("bio"))
             .build();
        }
    }

    // Inner bindings helper for @BindBean
    public static final class UserSqlBindings { /* wraps User getters */ }
}
```

**Why this pattern:**
- JDBI's `@SqlObject` requires an interface — can't annotate a concrete class
- `onDemand()` creates a proxy that opens/closes connections per method call
- Row mappers use `StorageBuilder` (not constructors) to bypass validation on DB load
- `JdbiTypeCodecs.SqlRowReaders` provides null-safe reading (`readUuid`, `readInstant`,
  `readLocalDate`, `readEnum`) to handle nullable DB columns

### 4.3 JDBI Implementation Map

| Implementation | Interface | Package | Notes |
|---------------|-----------|---------|-------|
| `JdbiUserStorage` | `UserStorage` | `storage/jdbi/profile/` | Users + profile notes |
| `JdbiMatchmakingStorage` | `InteractionStorage` | `storage/jdbi/matching/` | Likes + matches + undo. Also provides `Undo.Storage` |
| `JdbiConnectionStorage` | `CommunicationStorage` | `storage/jdbi/connection/` | Conversations, messages, friend requests, notifications |
| `JdbiMetricsStorage` | `AnalyticsStorage` | `storage/jdbi/metrics/` | Stats, achievements, sessions, daily picks, profile views. Also implements `Standout.Storage` |
| `JdbiTrustSafetyStorage` | `TrustSafetyStorage` | `storage/jdbi/safety/` | Blocks + reports. Uses `@SqlObject` directly (no outer wrapper) |
| `JdbiTypeCodecs` | (shared utilities) | `storage/jdbi/shared/` | `SqlRowReaders`, `EnumSetSqlCodec`, column mappers |

### 4.4 StorageFactory — Wiring Everything Together

`StorageFactory.buildH2(DatabaseManager, AppConfig)` is the single point where
all implementations are instantiated and services are wired:

```
DatabaseManager (HikariCP pool)
    │
    ▼
Jdbi instance ─── SqlObjectPlugin + type codecs registered
    │
    ├── JdbiUserStorage ─────────────────────→ UserStorage
    ├── JdbiMatchmakingStorage ──────────────→ InteractionStorage + Undo.Storage
    ├── JdbiConnectionStorage ───────────────→ CommunicationStorage
    ├── JdbiMetricsStorage ──────────────────→ AnalyticsStorage + Standout.Storage
    └── JdbiTrustSafetyStorage (onDemand) ──→ TrustSafetyStorage
         │
         ▼
    9 services constructed in dependency order:
    CandidateFinder → ProfileService → RecommendationService →
    UndoService → ActivityMetricsService → MatchingService (builder) →
    MatchQualityService → ConnectionService → TrustSafetyService
         │
         ▼
    ServiceRegistry (immutable, all fields non-null via requireNonNull)
```

---

## 5. Service Architecture

### 5.1 Service Inventory

The system has **9 domain services** organized by bounded context:

| Service | Package | Primary Responsibility | Key Dependencies |
|---------|---------|----------------------|------------------|
| `CandidateFinder` | `core/matching/` | 7-stage candidate filter pipeline | UserStorage, InteractionStorage, TrustSafetyStorage, AppConfig |
| `MatchingService` | `core/matching/` | Like/pass/unmatch/block operations | InteractionStorage, TrustSafetyStorage, UserStorage + 3 optional |
| `MatchQualityService` | `core/matching/` | 6-factor compatibility scoring | UserStorage, InteractionStorage, AppConfig |
| `UndoService` | `core/matching/` | Time-windowed undo of swipes | InteractionStorage, Undo.Storage, AppConfig |
| `ProfileService` | `core/profile/` | Profile completion, achievements, behavior | AppConfig, AnalyticsStorage, InteractionStorage, TrustSafetyStorage, UserStorage |
| `ValidationService` | `core/profile/` | Field validation against config limits | AppConfig |
| `ConnectionService` | `core/connection/` | Messaging + relationship transitions | CommunicationStorage, InteractionStorage, UserStorage |
| `ActivityMetricsService` | `core/metrics/` | Session tracking + stats aggregation | InteractionStorage, TrustSafetyStorage, AnalyticsStorage, AppConfig |
| `RecommendationService` | `core/recommendation/` | Daily limits, daily picks, standouts | UserStorage, InteractionStorage, TrustSafetyStorage, AnalyticsStorage, CandidateFinder, ProfileService, AppConfig |
| `TrustSafetyService` | `core/safety/` | Block/report + auto-ban enforcement | TrustSafetyStorage, InteractionStorage, UserStorage, AppConfig |

### 5.2 Service Dependency Graph

```
                    ┌─────────────────┐
                    │  AppConfig      │ ◄── used by all services
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────────┐
        │                    │                         │
        ▼                    ▼                         ▼
 ┌──────────────┐   ┌───────────────┐   ┌───────────────────────┐
 │CandidateFinder│   │ProfileService │   │ActivityMetricsService │
 └───────┬──────┘   └───────┬───────┘   └───────────┬───────────┘
         │                   │                       │
         │         ┌─────────┼───────────────────────┘
         │         │         │
         ▼         ▼         ▼
 ┌────────────────────────────────┐    ┌──────────────────┐
 │    RecommendationService       │    │  UndoService      │
 └────────────────┬───────────────┘    └────────┬─────────┘
                  │                              │
                  ▼                              ▼
         ┌─────────────────┐           ┌─────────────────┐
         │ MatchingService  │◄──────── │ (builder pattern)│
         │ (builder-based)  │           └─────────────────┘
         └────────┬────────┘
                  │
    ┌─────────────┼─────────────┐
    ▼             ▼             ▼
┌──────────┐ ┌──────────┐ ┌──────────────────┐
│Connection │ │MatchQual.│ │TrustSafetyService│
│Service    │ │Service   │ │                  │
└──────────┘ └──────────┘ └──────────────────┘
```

**MatchingService uses a builder** because it has 3 optional dependencies
(`ActivityMetricsService`, `UndoService`, `RecommendationService`). The builder
validates required deps and allows nil for optionals.

### 5.3 Candidate Discovery Pipeline (CandidateFinder)

The `findCandidates()` method applies 7 sequential filters:

```
All ACTIVE users from UserStorage.findActive()
    │
    ├── [1] Exclude self (userId != currentUser)
    ├── [2] Exclude prior interactions (liked, passed, matched, blocked)
    ├── [3] Mutual gender preferences (each user's interestedIn includes other's gender)
    ├── [4] Mutual age preferences (each user's min/max age range includes other's age)
    ├── [5] Distance filter (Haversine formula ≤ user's maxDistanceKm)
    ├── [6] Dealbreaker evaluation (lifestyle/physical must-haves)
    └── [7] Sort by distance ascending
    │
    ▼
List<User> candidates (nearest first)
```

The `GeoUtils.haversine(lat1, lon1, lat2, lon2)` method is embedded directly in
`CandidateFinder` as a private static utility.

### 5.4 Match Quality Scoring (MatchQualityService)

Six weighted factors (must sum to 1.0, configured via AppConfig):

| Factor | Default Weight | Calculation |
|--------|---------------|-------------|
| Distance | 15% | Inverse of distance (closer = higher) |
| Age | 10% | Inverse of age difference |
| Interests | 25% | Jaccard similarity of interest sets |
| Lifestyle | 25% | Weighted match across smoking, drinking, wantsKids, lookingFor, education |
| Pace | 15% | PacePreferences compatibility (4 dimensions) |
| Response Time | 10% | Average response time in messaging |

Final score: 0-100 integer. Thresholds defined in `ScoringConstants`:
- 90+ = Excellent (star)
- 75+ = Great
- 60+ = Good
- 40+ = Fair
- Below 40 = Low

### 5.5 Result Pattern (Services Never Throw)

Services return structured result objects instead of throwing exceptions:

```java
public static record SendResult(
    boolean success,
    Message message,
    String errorMessage,
    ErrorCode errorCode
) {
    public static SendResult success(Message m) { ... }
    public static SendResult failure(String err, ErrorCode code) { ... }
}
```

This pattern appears in `ConnectionService.SendResult`, and similar result records
exist across services. Callers check `result.success()` instead of catching exceptions.

---

## 6. Bootstrap & Configuration

### 6.1 ApplicationStartup (app/bootstrap/)

Single entry point for all three application modes (CLI, GUI, REST):

```java
// In Main.java (CLI):
ServiceRegistry services = ApplicationStartup.initialize();

// In DatingApp.java (JavaFX):
@Override public void init() {
    ServiceRegistry services = ApplicationStartup.initialize();
    // ... create ViewModelFactory, NavigationService
}
```

**Initialization sequence:**
1. `load()` — reads `./config/app-config.json` (optional, defaults if missing)
2. `applyEnvironmentOverrides()` — `DATING_APP_*` env vars override JSON values
3. `DatabaseManager.getInstance()` — creates HikariCP pool, runs `SchemaInitializer`
4. `StorageFactory.buildH2(dbManager, config)` — wires everything → `ServiceRegistry`
5. `initialized = true` — idempotent flag, subsequent calls return existing registry

**Thread safety:** The `initialize()` method is `synchronized`. The `services` and
`dbManager` fields are `volatile`.

### 6.2 AppConfig (57 Parameters)

`AppConfig` is an immutable Java `record` with 57 parameters and a `Builder` for construction.
Parameters are grouped by purpose:

| Category | Count | Examples |
|----------|-------|---------|
| Limits | 9 | `dailyLikeLimit(100)`, `maxPhotos(2)`, `maxBioLength(500)`, `userTimeZone` |
| Session | 4 | `sessionTimeoutMinutes(5)`, `undoWindowSeconds(30)`, `suspiciousSwipeVelocity` |
| Algorithm | 17 | `nearbyDistanceKm(5)`, `minSharedInterests(3)`, response time thresholds (5), achievement tiers (5+1) |
| Validation | 8 | `minAge(18)`, `maxAge(120)`, `maxDistanceKm(500)`, `minHeightCm`, `maxNameLength` |
| Match weights | 6 | `distanceWeight(0.15)`, `interestWeight(0.25)` — must sum to 1.0 |
| Standout weights | 6 | Separate weighting for standout ranking |
| Achievement thresholds | 4 | `selectiveThreshold(0.20)`, `bioAchievementLength(100)` |
| Data retention | 2 | `cleanupRetentionDays(30)`, `softDeleteRetentionDays(90)` |
| Other | 1 | `messageMaxPageSize(100)` |

**Access pattern:** `private static final AppConfig CONFIG = AppConfig.defaults();`

**Environment override prefix:** `DATING_APP_` — e.g., `DATING_APP_DAILY_LIKE_LIMIT=50`

---

## 7. Database Schema

H2 embedded database with **18 tables**. Schema is managed by `SchemaInitializer`
(DDL) and `MigrationRunner` (ALTER TABLE evolution). All DDL uses `IF NOT EXISTS`
for idempotent re-runs.

### 7.1 Entity-Relationship Overview

```
users (PK: id UUID)
  │
  ├──< likes (FK: who_likes, who_got_liked → users.id)
  │      UNIQUE(who_likes, who_got_liked)
  │
  ├──< matches (FK: user_a, user_b → users.id)
  │      PK: VARCHAR id (deterministic: "uuidA_uuidB")
  │      UNIQUE(user_a, user_b)
  │
  ├──< conversations (FK: user_a, user_b → users.id)
  │      PK: VARCHAR id (deterministic: "uuidA_uuidB")
  │      │
  │      └──< messages (FK: conversation_id, sender_id)
  │
  ├──< blocks (FK: blocker_id, blocked_id → users.id)
  │      UNIQUE(blocker_id, blocked_id)
  │
  ├──< reports (FK: reporter_id, reported_user_id → users.id)
  │      UNIQUE(reporter_id, reported_user_id)
  │
  ├──< friend_requests (FK: from_user_id, to_user_id → users.id)
  │
  ├──< notifications (FK: user_id → users.id)
  │
  ├──< swipe_sessions (FK: user_id → users.id)
  │
  ├──< user_stats (FK: user_id → users.id)
  │
  ├──< user_achievements (FK: user_id → users.id)
  │      UNIQUE(user_id, achievement)
  │
  ├──< profile_notes (composite PK: author_id, subject_id → users.id)
  │
  ├──< profile_views (FK: viewer_id, viewed_id → users.id)
  │
  ├──< standouts (FK: seeker_id, standout_user_id → users.id)
  │      UNIQUE(seeker_id, standout_user_id, featured_date)
  │
  └──< daily_pick_views (composite PK: user_id, viewed_date)

platform_stats (standalone — no FK, stores aggregates)

undo_states (PK: user_id — one active undo per user)
```

### 7.2 Key Table Details

**users** — 42 columns covering identity, profile, location, preferences, lifestyle,
dealbreakers (`db_*` prefix), verification, pace, and timestamps. The `interested_in`
and `interests` fields store CSV strings (parsed to `EnumSet` by codecs).

**matches** — VARCHAR primary key (deterministic `userA_userB` format). State machine
column tracks `ACTIVE/FRIENDS/UNMATCHED/GRACEFUL_EXIT/BLOCKED`.

**conversations** — Same deterministic ID pattern as matches. Per-user read timestamps
and visibility flags support "archive" without deletion.

**All two-user tables** (likes, matches, blocks, reports, conversations) have
`ON DELETE CASCADE` foreign keys to `users.id`, so deleting a user cleans up all
related data.

### 7.3 Indexing Strategy

~30 indexes cover the primary query patterns:
- **Lookup by user:** `idx_likes_who_likes`, `idx_matches_user_a/b`, `idx_sessions_user_id`
- **State filtering:** `idx_matches_state`, `idx_users_state`, `idx_users_gender_state`
- **Temporal ordering:** `idx_user_stats_computed`, `idx_conversations_last_msg`, `idx_notifications_created`
- **Composite queries:** `idx_sessions_user_active`, `idx_friend_req_to_status`

---

## 8. UI Architecture (JavaFX + MVVM)

### 8.1 MVVM Pattern

```
┌─────────────┐     binds to      ┌──────────────┐    calls     ┌──────────────┐
│  Controller │ ──────────────── │  ViewModel    │ ──────────── │  Service     │
│  (FXML)     │                   │  (Observable) │              │  (via SR)    │
│             │ ◄──── errors ──── │              │              │              │
│  calls      │   ViewModelError  │  owns state   │              │              │
│  UiFeedback │       Sink        │  properties   │              │              │
│  Service    │                   │              │              │              │
└─────────────┘                   └──────────────┘              └──────────────┘
```

**Controllers** (in `ui/screen/`):
- Extend `BaseController` which manages loading overlays and lifecycle
- Lifecycle: `initialize()` → wire ViewModel + overlays, `onLoad()` → fetch data, `onUnload()` → cleanup
- Depend on ViewModels, **never** on services or storage directly

**ViewModels** (in `ui/viewmodel/screen/`):
- Own `ObservableProperty` fields for reactive UI binding
- Use `UiDataAdapters` interfaces (not raw storage) for data access
- Report errors via `ViewModelErrorSink` functional interface
- Have `dispose()` for cleanup

**UiDataAdapters** (in `ui/viewmodel/data/`):
- `UiUserStore` — read-only user access for UI
- `UiMatchDataAccess` — read-only match/interaction data
- Adapter implementations wrap core storage interfaces with null safety

### 8.2 Navigation

`NavigationService` is a singleton managing scene transitions:

```java
// ViewTypes: LOGIN, DASHBOARD, PROFILE, MATCHING, MATCHES, CHAT, STATS, PREFERENCES
navigationService.navigateTo(ViewType.CHAT);

// With context passing:
navigationService.setNavigationContext(matchedUserId);
navigationService.navigateTo(ViewType.CHAT);

// In target controller:
Object context = navigationService.consumeNavigationContext();
if (context instanceof UUID userId) { ... }
```

Transition animations: `FADE`, `SLIDE_LEFT`, `SLIDE_RIGHT`, `NONE`.
History stack (max 20 entries) supports back navigation.

### 8.3 ViewModelFactory

Bridge between `ServiceRegistry` and the UI layer:
- Lazy-creates ViewModels (cached singletons)
- Wires `UiDataAdapters` implementations around core storage interfaces
- Provides `currentUserProperty()` synchronized with `AppSession`
- `reset()` disposes all ViewModels on logout

---

## 9. CLI Architecture

### 9.1 Handler Structure

Each CLI handler lives in a domain subpackage under `app/cli/` and receives
dependencies via an explicit `Dependencies` record:

```
Main.java
  │
  ├── MatchingHandler (app/cli/matching/)
  │     Dependencies: CandidateFinder, MatchingService, InteractionStorage,
  │                   RecommendationService, UndoService, MatchQualityService,
  │                   UserStorage, ProfileService, AnalyticsStorage,
  │                   TrustSafetyService, ConnectionService, RecommendationService,
  │                   CommunicationStorage, AppSession, InputReader
  │     Methods: browseCandidates, viewMatches, browseWhoLikedMe,
  │              viewNotifications, viewPendingRequests, viewStandouts
  │
  ├── ProfileHandler (app/cli/profile/)
  │     Dependencies: UserStorage, ProfileService (×2), ValidationService,
  │                   AppSession, InputReader
  │     Methods: createUser, selectUser, completeProfile, setDealbreakers,
  │              previewProfile, viewAllNotes, viewProfileScore
  │
  ├── MessagingHandler (app/cli/connection/)
  │     Dependencies: ConnectionService, InteractionStorage, TrustSafetyService,
  │                   InputReader, AppSession
  │     Methods: showConversations, getTotalUnreadCount
  │
  ├── SafetyHandler (app/cli/safety/)
  │     Dependencies: UserStorage, TrustSafetyService, AppSession, InputReader
  │     Methods: blockUser, reportUser, manageBlockedUsers, verifyProfile
  │
  └── StatsHandler (app/cli/metrics/)
        Dependencies: ActivityMetricsService, ProfileService, AppSession, InputReader
        Methods: viewStatistics, viewAchievements
```

**CliTextAndInput** (shared constants + utilities):
- `~40 display constants` (separators, headers, prompts)
- `validateChoice(input, validChoices...)` — case-insensitive input validation
- `requireLogin(Runnable)` — guard for logged-in actions
- Nested `InputReader` — Scanner wrapper with `readLine(prompt)`
- Nested `EnumMenu` — generic `prompt()` and `promptMultiple()` for enum selection

### 9.2 Main Menu Loop

`Main.java` runs a simple `while(running)` loop with a 20-option switch statement.
Each case calls `safeExecute(handler::method)` which wraps exceptions in a user-friendly
error message (never crashes the menu loop).

---

## 10. Data Flows

### 10.1 Like → Match Creation

```
User presses [L]ike on candidate
    │
    ▼
MatchingHandler.browseCandidates()
    │
    ├── RecommendationService.getStatus() ← check daily like limit
    │
    ├── MatchingService.like(currentUser, candidateId)
    │       │
    │       ├── Create Like record (direction=LIKE)
    │       ├── InteractionStorage.save(like)
    │       ├── ActivityMetricsService.recordSwipe() ← session tracking
    │       ├── UndoService.recordSwipe() ← store for potential undo
    │       │
    │       ├── InteractionStorage.mutualLikeExists(a, b)?
    │       │       │
    │       │       ├── YES → Match.create(a, b)
    │       │       │         InteractionStorage.save(match)
    │       │       │         → return Optional.of(match)
    │       │       │
    │       │       └── NO → return Optional.empty()
    │       │
    │       └── Return LikeResult (match or not, like record)
    │
    └── Display "It's a match!" or continue browsing
```

### 10.2 Messaging Flow

```
User selects conversation and types message
    │
    ▼
MessagingHandler.showConversations()
    │
    └── ConnectionService.sendMessage(senderId, conversationId, content)
            │
            ├── Validate sender exists and is ACTIVE
            ├── Validate conversation exists and sender is a participant
            ├── Validate associated match is in ACTIVE or FRIENDS state
            ├── Validate content (not blank, ≤ 1000 chars)
            │
            ├── Message.create(conversationId, senderId, content)
            ├── CommunicationStorage.saveMessage(message)
            ├── CommunicationStorage.updateConversationLastMessageAt(...)
            │
            └── Return SendResult.success(message)
                OR SendResult.failure(reason, ErrorCode)
```

### 10.3 Statistics Computation

```
StatsHandler.viewStatistics()
    │
    ▼
ActivityMetricsService.computeUserStats(userId)
    │
    ├── InteractionStorage.countByDirection(userId, LIKE)     → likesGiven
    ├── InteractionStorage.countByDirection(userId, PASS)     → passesGiven
    ├── InteractionStorage.countReceivedByDirection(userId, LIKE)  → likesReceived
    ├── InteractionStorage.countReceivedByDirection(userId, PASS)  → passesReceived
    ├── InteractionStorage.getActiveMatchesFor(userId).size() → activeMatches
    ├── InteractionStorage.getAllMatchesFor(userId).size()     → totalMatches
    ├── TrustSafetyStorage.countBlocksGiven/Received(userId)  → blocks
    ├── TrustSafetyStorage.countReportsBy/Against(userId)     → reports
    │
    ├── Compute derived scores:
    │     likeRatio = likesGiven / totalSwipesGiven
    │     reciprocityScore = mutualLikes / likesGiven
    │     selectivenessScore = 1.0 - likeRatio (clamped 0-1)
    │     attractivenessScore = incomingLikeRatio (weighted)
    │
    ├── AnalyticsStorage.saveUserStats(stats)
    │
    └── Return UserStats snapshot
```

### 10.4 Report → Auto-Ban Flow

```
SafetyHandler.reportUser()
    │
    ▼
TrustSafetyService.report(reporterId, reportedUserId, reason, description)
    │
    ├── Validate not self-reporting
    ├── TrustSafetyStorage.hasReported(reporterId, reportedUserId)? → error if duplicate
    │
    ├── Report.create(reporterId, reportedUserId, reason, description)
    ├── TrustSafetyStorage.save(report)
    │
    ├── Auto-block: TrustSafetyService.block(reporterId, reportedUserId)
    │
    ├── Check threshold: TrustSafetyStorage.countReportsAgainst(reportedUserId)
    │       │
    │       └── ≥ AppConfig.autoBanThreshold (default 3)?
    │               │
    │               └── YES → User.ban(), UserStorage.save(user)
    │
    └── Return success/failure result
```

---

## 11. Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 25 (preview features enabled) | `--enable-preview` everywhere |
| Build | Maven | 3.9+ | Compile, test, quality checks |
| Database | H2 | 2.4.240 | Embedded in-process database |
| SQL Framework | JDBI 3 | 3.51.0 | Declarative SQL (`@SqlQuery`/`@SqlUpdate`) |
| Connection Pool | HikariCP | 6.3.0 | Database connection pooling |
| GUI Framework | JavaFX | 25.0.2 | Desktop UI (FXML + CSS) |
| GUI Theme | AtlantaFX | 2.1.0 | Material Design-inspired theming |
| GUI Icons | Ikonli | 12.4.0 | MaterialDesign2 icon pack |
| REST Framework | Javalin | 6.7.0 | Lightweight HTTP endpoints |
| JSON | Jackson | 2.21.0 | Config parsing + REST serialization |
| Logging API | SLF4J | 2.0.17 | Logging facade |
| Logging Impl | Logback | 1.5.28 | Logging backend |
| Testing | JUnit 5 | 5.14.2 | Unit + integration tests |
| Formatting | Spotless | (plugin) | Palantir Java Format |
| Static Analysis | PMD | (plugin) | Custom `pmd-rules.xml` |
| Coverage | JaCoCo | (plugin) | 60% minimum threshold |

### Build Pipeline (`mvn verify`)

```
compile → test → jacoco:report → jar → spotless:check → pmd:check → jacoco:check
```

Always run `spotless:apply` before `verify` to avoid formatting failures.

---

## 12. Testing Architecture

### 12.1 Test Strategy

- **No Mockito** — all tests use `TestStorages.*` (in-memory implementations)
- **TestClock** — deterministic time via `AppClock.setTestClock(fixedInstant)`
- **H2 in-memory** — CLI handler tests use real H2 databases (`jdbc:h2:mem:testname_UUID`)
- **Nested test classes** — `@Nested @DisplayName` for scenario grouping
- **Timeouts** — `@Timeout(5)` or `@Timeout(10)` on all test classes

### 12.2 TestStorages (5 Inner Classes)

```java
var users       = new TestStorages.Users();           // → UserStorage
var interactions = new TestStorages.Interactions();    // → InteractionStorage
var comms       = new TestStorages.Communications();   // → CommunicationStorage
var analytics   = new TestStorages.Analytics();        // → AnalyticsStorage
var trustSafety = new TestStorages.TrustSafety();     // → TrustSafetyStorage
```

These live in `core/testutil/` (test source tree) and use `HashMap`/`ArrayList`
internally. They implement the full storage interface contract — no stubs or partial
mocks.

### 12.3 Test Organization

```
src/test/java/datingapp/
├── app/
│   ├── ConfigLoaderTest.java
│   ├── api/RestApiRoutesTest.java
│   └── cli/
│       ├── EnumMenuTest.java
│       ├── LikerBrowserHandlerTest.java
│       ├── MessagingHandlerTest.java
│       ├── ProfileCreateSelectTest.java
│       ├── ProfileNotesHandlerTest.java
│       ├── RelationshipHandlerTest.java
│       ├── SafetyHandlerTest.java
│       ├── StatsHandlerTest.java
│       └── UserSessionTest.java
├── core/
│   ├── AppClockTest.java
│   ├── AppConfigTest.java
│   ├── CandidateFinderTest.java
│   ├── ... (30+ test classes)
│   └── NestedTypeVisibilityTest.java  ← guards public static on nested types
└── storage/
    └── (JDBI integration tests)
```

---

## 13. Design Decisions & Rationale

### Why domain-driven packages (not layered)?

Services and their domain-specific models are **co-located** in subpackages
(`core/connection/`, `core/matching/`, etc.) rather than separated into
`core/service/` and `core/model/`. This was done because:
1. Services and their models have high cohesion — they change together
2. Navigation is easier: finding `ConnectionService` and `ConnectionModels` in the
   same package is faster than searching across two flat directories
3. The 5 domain boundaries (`connection`, `matching`, `metrics`, `profile`,
   `recommendation`, `safety`) map naturally to bounded contexts

### Why 5 storage interfaces (not 11)?

Originally there were 11 fine-grained interfaces (LikeStorage, MatchStorage, etc.).
These were consolidated because:
1. **Transaction boundaries** — atomic undo requires deleting a Like and a Match together
2. **Implementation simplicity** — most were thin wrappers around the same Jdbi instance
3. **Aggregate alignment** — the 5 interfaces match the 5 bounded contexts

### Why no HandlerFactory?

Handlers are instantiated directly in `Main.java` with explicit `Dependencies` records
rather than through a factory. This was chosen because:
1. **Explicitness** — every dependency is visible at the call site
2. **No reflection** — dependencies are validated at compile time via the record constructor
3. **Simplicity** — a factory added indirection without real benefit for 5 handlers

### Why CliTextAndInput nests InputReader and EnumMenu?

These three concerns (display constants, input reading, enum selection) all serve the
CLI layer and have zero reuse outside it. Nesting avoids creating three tiny files and
makes the CLI "toolkit" discoverable as a single import.

### Why AppClock instead of Instant.now()?

Every timestamp in the system goes through `AppClock.now()`. In tests, `TestClock`
(set via `AppClock.setTestClock()`) returns a fixed or advancing instant. This makes
tests deterministic — no flaky failures from timing differences.

### Why StorageBuilder instead of constructors?

When loading from the database, entities may have nullable fields that would fail
validation in the normal constructor. `StorageBuilder` bypasses validation and directly
sets fields, producing valid domain objects from potentially incomplete DB rows.

---

## Appendix: Entry Point Comparison

| Aspect | CLI (`Main.java`) | JavaFX (`DatingApp.java`) | REST (`RestApiServer.java`) |
|--------|-------------------|---------------------------|-----------------------------|
| Bootstrap | `ApplicationStartup.initialize()` | Same | Same |
| Session | `AppSession.getInstance()` | Same (synced with `ViewModelFactory.currentUserProperty()`) | Per-request (no shared session) |
| Input | `CliTextAndInput.InputReader` (Scanner) | FXML + JavaFX bindings | Javalin `Context` |
| Error handling | `safeExecute()` wraps + logs | `ViewModelErrorSink` → `UiFeedbackService` | Javalin exception handlers |
| Navigation | Switch statement (20 options) | `NavigationService` (8 views, history stack) | HTTP routes |
| Shutdown | `ApplicationStartup.shutdown()` | `stop()` → shutdown | Server.stop() |
