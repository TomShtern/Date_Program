# Dating App - Qwen Code Context

**Phase:** 2.1 | **Java 25** (preview) | **Maven** | **H2 + JDBI 3** | **JavaFX 25** | **Javalin REST**

> **Purpose:** This document provides essential context for generating compatible code. Read this first before any implementation task. **CODE IS THE ONLY SOURCE OF TRUTH** — documentation may be stale.

---

## Quick Reference

| Aspect | Value |
|--------|-------|
| **Entry Points** | `Main.java` (CLI), `DatingApp.java` (JavaFX), `RestApiServer.java` (REST on port 7070) |
| **Bootstrap** | `ApplicationStartup.initialize()` → `StorageFactory.buildH2()` |
| **Session** | `AppSession` singleton (current user) |
| **Database** | H2 at `./data/dating.mv.db`, user: `sa`, password: `dev` or `DATING_APP_DB_PASSWORD` |
| **Config** | `./config/app-config.json` + env vars (`DATING_APP_*`) |
| **Run CLI** | `mvn compile && mvn exec:exec` |
| **Run JavaFX** | `mvn javafx:run` |
| **Run Tests** | `mvn test` |
| **Format** | `mvn spotless:apply` (REQUIRED before commit) |
| **Full Build** | `mvn clean verify package` |
| **Java Version** | Java 25 with `--enable-preview` and `--enable-native-access=ALL-UNNAMED` |
| **LOC** | Main: 22,325 code + 4,238 blanks + 3,056 comments = 29,619 total (87 files) |
| **Tests** | 14,825 code + 3,271 blanks + 779 comments = 18,875 total (65 Java files, 62 test classes) |

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│  PRESENTATION: CLI (app/cli/) │ JavaFX MVVM (ui/) │ REST    │
├──────────────────────────────────────────────────────────────┤
│  DOMAIN (core/): Models + Services + Storage Interfaces      │
│  → ZERO framework/database imports (pure java.* only)        │
├──────────────────────────────────────────────────────────────┤
│  INFRASTRUCTURE (storage/): JDBI implementations + Schema    │
├──────────────────────────────────────────────────────────────┤
│  PLATFORM: H2 embedded + HikariCP + JVM                      │
└──────────────────────────────────────────────────────────────┘
```

**Package Structure (verified from source):**
```
datingapp/
├── Main.java                    CLI entry point (20-option menu)
├── app/
│   ├── bootstrap/ApplicationStartup.java  Centralized init + config loading
│   ├── api/RestApiServer.java             Javalin REST API (port 7070)
│   └── cli/                   CLI handlers (flat, no subpackages)
│       ├── CliTextAndInput.java           Constants + InputReader + EnumMenu
│       ├── MatchingHandler.java           Swiping, matches, daily picks, standouts
│       ├── MessagingHandler.java          Conversations, messages
│       ├── ProfileHandler.java            Profile completion, dealbreakers, notes
│       ├── SafetyHandler.java             Block, report, verify
│       └── StatsHandler.java              Statistics, achievements
├── core/                      DOMAIN LAYER (pure Java, no framework imports)
│   ├── AppConfig.java         57-parameter config record with 4 sub-records
│   ├── AppClock.java          Testable clock (TestClock in tests)
│   ├── AppSession.java        Singleton: current logged-in user
│   ├── ServiceRegistry.java   Holds all services + storage interfaces (16 fields)
│   ├── EnumSetUtil.java       Null-safe EnumSet utilities
│   ├── LoggingSupport.java    Default logging methods
│   ├── PerformanceMonitor.java Timing + metrics
│   ├── TextUtil.java          Text utilities
│   │
│   ├── model/                 Primary domain entities
│   │   ├── User.java          Mutable, state machine, nested enums (Gender, UserState, VerificationMethod)
│   │   ├── Match.java         Mutable, deterministic ID, nested enums (MatchState, MatchArchiveReason)
│   │   └── ProfileNote.java   Standalone record: private notes on profiles
│   │
│   ├── connection/            Messaging & social domain
│   │   ├── ConnectionModels.java  7 nested types: Message, Conversation, Like, Block, Report, FriendRequest, Notification
│   │   └── ConnectionService.java   Send messages, relationship transitions
│   │
│   ├── matching/              Discovery & matching
│   │   ├── CandidateFinder.java     7-stage filter pipeline + GeoUtils (Haversine)
│   │   ├── MatchingService.java     Like/pass/unmatch/block, PendingLiker browser
│   │   ├── MatchQualityService.java 6-factor scoring + CompatibilityScoring utility
│   │   ├── RecommendationService.java Daily limits, daily picks, standouts
│   │   ├── UndoService.java         Time-windowed undo (default 30s)
│   │   ├── LifestyleMatcher.java    Lifestyle compatibility
│   │   ├── CompatibilityScoring.java Score calculation utilities
│   │   ├── TrustSafetyService.java  Block/report, auto-ban at threshold
│   │   └── Standout.java            Standout candidate data + Storage interface
│   │
│   ├── profile/               Profile management
│   │   ├── ProfileService.java      Completion scoring, achievements, behavior analysis
│   │   ├── ValidationService.java   Field validation against AppConfig
│   │   └── MatchPreferences.java    Dealbreakers + PacePreferences + Interest enum (37 values)
│   │
│   ├── metrics/               Analytics & engagement
│   │   ├── ActivityMetricsService.java Session lifecycle, stats aggregation (256 lock stripes)
│   │   ├── EngagementDomain.java       UserStats, PlatformStats, Achievement enum (11 values)
│   │   └── SwipeState.java             Session + Undo.Storage records
│   │
│   └── storage/               Storage INTERFACES (5 consolidated)
│       ├── UserStorage.java           User + ProfileNote
│       ├── InteractionStorage.java    Like + Match + atomic undo
│       ├── CommunicationStorage.java  Conversation + Message + FriendRequest + Notification
│       ├── AnalyticsStorage.java      Stats + Achievements + Sessions + DailyPicks
│       └── TrustSafetyStorage.java    Block + Report
│
├── storage/                   INFRASTRUCTURE LAYER
│   ├── DatabaseManager.java   H2 + HikariCP singleton
│   ├── StorageFactory.java    Wires JDBI impls → services → ServiceRegistry
│   ├── schema/
│   │   ├── SchemaInitializer.java  DDL (18 tables + indexes + FK)
│   │   └── MigrationRunner.java    Schema evolution
│   └── jdbi/                  JDBI implementations (flat, no subpackages)
│       ├── JdbiUserStorage.java           → UserStorage
│       ├── JdbiMatchmakingStorage.java    → InteractionStorage + Undo.Storage
│       ├── JdbiConnectionStorage.java     → CommunicationStorage
│       ├── JdbiMetricsStorage.java        → AnalyticsStorage + Standout.Storage
│       ├── JdbiTrustSafetyStorage.java    → TrustSafetyStorage (SqlObject onDemand)
│       └── JdbiTypeCodecs.java            EnumSet codec, SqlRowReaders (helper utility)
│
└── ui/                        JAVAFX LAYER (MVVM)
    ├── DatingApp.java         JavaFX Application entry point
    ├── NavigationService.java Singleton: scene transitions (10 views + history)
    ├── UiConstants.java       Window sizes, timing
    ├── UiComponents.java      Reusable UI factories
    ├── UiFeedbackService.java Toast notifications
    ├── UiAnimations.java      Fade, slide transitions
    ├── UiUtils.java           UI utilities
    ├── ImageCache.java        Lazy image caching
    ├── screen/                FXML Controllers (extend BaseController)
    │   ├── BaseController.java        Overlay mgmt, lifecycle hooks
    │   ├── LoginController.java
    │   ├── DashboardController.java
    │   ├── ProfileController.java
    │   ├── MatchingController.java
    │   ├── MatchesController.java
    │   ├── ChatController.java
    │   ├── StatsController.java
    │   ├── PreferencesController.java
    │   ├── StandoutsController.java
    │   ├── SocialController.java
    │   └── MilestonePopupController.java
    ├── popup/
    │   ├── MatchPopupController.java
    │   └── MilestonePopupController.java
    └── viewmodel/
        ├── ViewModelFactory.java  Lazy ViewModel creation, AppSession binding
        ├── ViewModelErrorSink.java Error callback functional interface
        ├── UiDataAdapters.java    UiUserStore + UiMatchDataAccess interfaces
        ├── LoginViewModel.java
        ├── DashboardViewModel.java
        ├── ProfileViewModel.java
        ├── MatchingViewModel.java
        ├── MatchesViewModel.java
        ├── ChatViewModel.java
        ├── StatsViewModel.java
        ├── PreferencesViewModel.java
        ├── StandoutsViewModel.java
        └── SocialViewModel.java
```

---

## Domain Models

### User (Mutable Entity)
**State Machine:** `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`

**Key Fields:**
- Identity: `id` (UUID), `name`, `bio`, `birthDate`, `gender`, `interestedIn` (Set<Gender>)
- Location: `lat`, `lon`, `hasLocationSet`, `maxDistanceKm`
- Preferences: `minAge`, `maxAge`, `photoUrls` (max 2), `interests` (max 10)
- Lifestyle: `smoking`, `drinking`, `wantsKids`, `lookingFor`, `education`, `heightCm`
- Dealbreakers: `MatchPreferences.Dealbreakers`
- Pace: `PacePreferences` (messaging frequency, time to first date, communication style, depth)
- Verification: `email`, `phone`, `isVerified`, `verificationMethod`, `verificationCode`, `verifiedAt`
- Timestamps: `createdAt`, `updatedAt`, `deletedAt`

**Nested Types (all `public static`):**
- `Gender`: MALE, FEMALE, OTHER
- `UserState`: INCOMPLETE, ACTIVE, PAUSED, BANNED
- `VerificationMethod`: EMAIL, PHONE

**Key Methods:**
- `activate()` - Requires `isComplete()`
- `pause()` - From ACTIVE only
- `ban()` - One-way, irreversible
- `isComplete()` - Checks all required fields + pace preferences
- `getAge()` - Computes from `birthDate` using configured `ZoneId`

### Match (Mutable Entity)
**State Machine:** `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`

**Deterministic ID:** `userA_userB` (lexicographically sorted UUIDs)

**Key Fields:**
- `id` (String), `userA`, `userB`, `createdAt`
- `state` (MatchState), `endedAt`, `endedBy`, `endReason` (MatchArchiveReason)
- `deletedAt` (soft-delete)

**Nested Types (all `public static`):**
- `MatchState`: ACTIVE, FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED
- `MatchArchiveReason`: FRIEND_ZONE, GRACEFUL_EXIT, UNMATCH, BLOCK

**Key Methods:**
- `canMessage()` - Returns true if ACTIVE or FRIENDS
- `involves(UUID)` - Checks if user is part of match
- `getOtherUser(UUID)` - Gets the other user in match
- State transitions: `unmatch()`, `block()`, `transitionToFriends()`, `gracefulExit()`, `revertToActive()`

### ConnectionModels (7 Nested Types in `core/connection/`)

| Type | Kind | Key Fields |
|------|------|------------|
| `Message` | record | `id`, `conversationId`, `senderId`, `content` (max 1000), `createdAt` |
| `Conversation` | class | Deterministic ID, `userA`, `userB`, `lastMessageAt`, per-user read timestamps, archive state, visibility flags |
| `Like` | record | `id`, `whoLikes`, `whoGotLiked`, `direction` (LIKE/PASS), `createdAt` |
| `Block` | record | `id`, `blockerId`, `blockedId`, `createdAt` |
| `Report` | record | `id`, `reporterId`, `reportedUserId`, `reason` (enum), `description`, `createdAt` |
| `FriendRequest` | record | `id`, `fromUserId`, `toUserId`, `status` (PENDING/ACCEPTED/DECLINED/EXPIRED), `createdAt`, `respondedAt` |
| `Notification` | record | `id`, `userId`, `type` (enum), `title`, `message`, `createdAt`, `isRead`, `data` (Map) |

### MatchPreferences (Profile Configuration)

**Interest Enum (37 values across 6 categories):**
- OUTDOORS (6): Hiking, Camping, Fishing, Cycling, Running, Climbing
- ARTS (8): Movies, Music, Concerts, Art Galleries, Theater, Photography, Reading, Writing
- FOOD (6): Cooking, Baking, Wine, Craft Beer, Coffee, Foodie
- SPORTS (7): Gym, Yoga, Basketball, Soccer, Tennis, Swimming, Golf
- TECH (5): Video Games, Board Games, Coding, Tech, Podcasts
- SOCIAL (7): Travel, Dancing, Volunteering, Pets, Dogs, Cats, Nightlife

**Lifestyle Enums:**
- `Smoking`: NEVER, SOMETIMES, REGULARLY
- `Drinking`: NEVER, SOCIALLY, REGULARLY
- `WantsKids`: NO, OPEN, SOMEDAY, HAS_KIDS
- `LookingFor`: CASUAL, SHORT_TERM, LONG_TERM, MARRIAGE, UNSURE
- `Education`: HIGH_SCHOOL, SOME_COLLEGE, BACHELORS, MASTERS, PHD, TRADE_SCHOOL, OTHER

**PacePreferences Record:**
- `MessagingFrequency`: RARELY, OFTEN, CONSTANTLY, WILDCARD
- `TimeToFirstDate`: QUICKLY, FEW_DAYS, WEEKS, MONTHS, WILDCARD
- `CommunicationStyle`: TEXT_ONLY, VOICE_NOTES, VIDEO_CALLS, IN_PERSON_ONLY, MIX_OF_EVERYTHING
- `DepthPreference`: SMALL_TALK, DEEP_CHAT, EXISTENTIAL, DEPENDS_ON_VIBE

**Dealbreakers Record:**
- Lifestyle: `acceptableSmoking`, `acceptableDrinking`, `acceptableKidsStance`, `acceptableLookingFor`, `acceptableEducation`
- Physical: `minHeightCm`, `maxHeightCm`
- Age: `maxAgeDifference`

### SwipeState (Session + Undo)

**Session Record:**
- `id`, `userId`, `startedAt`, `lastActivityAt`, `endedAt`
- `state` (ACTIVE/COMPLETED)
- `swipeCount`, `likeCount`, `passCount`, `matchCount`

**Undo Record:**
- `userId`, `like` (Like record), `matchId`, `expiresAt`
- `Storage` interface for persistence (**persisted to `undo_states` table**, one row per user, expires after configured window)

### EngagementDomain (Achievements + Stats)

**Achievement Enum (11 values across 4 categories):**
- Matching Milestones (5): FIRST_SPARK (1), SOCIAL_BUTTERFLY (5), POPULAR (10), SUPERSTAR (25), LEGEND (50)
- Behavior (2): SELECTIVE (<20% like ratio), OPEN_MINDED (>60% like ratio)
- Profile Excellence (3): COMPLETE_PACKAGE (100%), STORYTELLER (100+ bio chars), LIFESTYLE_GURU (all lifestyle fields)
- Safety (1): GUARDIAN (report fake profile)

**UserStats:** Aggregated metrics (likes given/received, match rate, reciprocity, selectiveness, attractiveness)
**PlatformStats:** Global aggregates for normalization

---

## Services (10 Domain Services)

| Service | Responsibility | Key Dependencies |
|---------|---------------|------------------|
| `CandidateFinder` | 7-stage filter pipeline | UserStorage, InteractionStorage, TrustSafetyStorage, ZoneId |
| `MatchingService` | Like/pass/unmatch/block, PendingLiker browser | InteractionStorage, TrustSafetyStorage, UserStorage + 3 optional |
| `MatchQualityService` | 6-factor compatibility scoring | UserStorage, InteractionStorage, AppConfig |
| `RecommendationService` | Daily limits, daily picks, standouts | UserStorage, InteractionStorage, TrustSafetyStorage, AnalyticsStorage, CandidateFinder, Standout.Storage, ProfileService, AppConfig, Clock |
| `UndoService` | Time-windowed undo (default 30s) | InteractionStorage, Undo.Storage, AppConfig |
| `ProfileService` | Completion scoring, achievements, behavior | AppConfig, AnalyticsStorage, InteractionStorage, TrustSafetyStorage, UserStorage |
| `ValidationService` | Field validation against AppConfig limits | AppConfig |
| `ConnectionService` | Messaging, relationship transitions | AppConfig, CommunicationStorage, InteractionStorage, UserStorage, ActivityMetricsService |
| `ActivityMetricsService` | Session tracking, stats aggregation (256 lock stripes) | InteractionStorage, TrustSafetyStorage, AnalyticsStorage, AppConfig |
| `TrustSafetyService` | Block/report, auto-ban at threshold | TrustSafetyStorage, InteractionStorage, UserStorage, AppConfig, CommunicationStorage |

### CandidateFinder Pipeline (7 Stages)
1. `filter(!self)` - Exclude current user
2. `filter(active)` - Only ACTIVE users
3. `filter(noPriorInteraction)` - No previous like/pass
4. `filter(mutualGender)` - Mutual interest in gender
5. `filter(mutualAge)` - Within age preferences (both ways)
6. `filter(distance)` - Within maxDistanceKm (Haversine)
7. `filter(dealbreakers)` - Pass Dealbreakers.Evaluator

Results sorted by distance ascending.

### MatchQualityService Scoring (6 Factors)

| Factor | Weight | Calculation |
|--------|--------|-------------|
| Distance | 15% | Linear decay from maxDistanceKm |
| Age | 10% | Inverse of age difference |
| Interests | 25% | Overlap ratio: `shared / min(setA, setB)` |
| Lifestyle | 25% | Smoking, drinking, kids, relationship goals match |
| Pace | 15% | 4-dimension compatibility (messaging, time to date, communication, depth) |
| Response Time | 10% | Time between mutual likes (tiers: <1h, <24h, <72h, <1w, <1mo) |

**Thresholds:** 90+ = Excellent (5★), 75+ = Great (4★), 60+ = Good (3★), 40+ = Fair (2★), <40 = Low (1★)

---

## Storage Layer

### 5 Consolidated Interfaces

| Interface | Entities | Methods |
|-----------|----------|---------|
| `UserStorage` | User, ProfileNote | ~15 methods: save, get, findActive, findCandidates, findByIds, delete, purgeDeletedBefore, profile notes CRUD |
| `InteractionStorage` | Like, Match, atomic undo | ~30 methods: saveLikeAndMaybeCreateMatch, getActiveMatchesFor, getAllMatchesFor, atomicUndoDelete, acceptFriendZoneTransition, gracefulExitTransition |
| `CommunicationStorage` | Conversation, Message, FriendRequest, Notification | ~30 methods: conversation CRUD, message CRUD, friend request CRUD, notification CRUD |
| `AnalyticsStorage` | UserStats, PlatformStats, Session, Achievement, DailyPick, ProfileView | ~30 methods: session tracking, stats aggregation, achievements, daily picks |
| `TrustSafetyStorage` | Block, Report | ~12 methods: block/unblock, report, count reports |

### JDBI Implementations (5 storage + 1 helper)

| Implementation | Implements | Notes |
|----------------|------------|-------|
| `JdbiUserStorage` | `UserStorage` | Outer class + inner Dao, Mapper, SqlBindings |
| `JdbiMatchmakingStorage` | `InteractionStorage` | Also provides `Undo.Storage` via `undoStorage()` accessor |
| `JdbiConnectionStorage` | `CommunicationStorage` | Outer class + inner Dao, Mappers |
| `JdbiMetricsStorage` | `AnalyticsStorage` | Also implements `Standout.Storage` |
| `JdbiTrustSafetyStorage` | `TrustSafetyStorage` | Uses `jdbi.onDemand(...)` directly (SqlObject) |
| `JdbiTypeCodecs` | (utility) | `SqlRowReaders`, `EnumSetSqlCodec`, column mappers |

### JDBI Implementation Pattern

```java
public final class JdbiUserStorage implements UserStorage {
    private final Jdbi jdbi;
    private final Dao dao;  // @SqlObject interface

    public JdbiUserStorage(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.dao = jdbi.onDemand(Dao.class);
    }

    @Override
    public void save(User user) {
        dao.save(new UserSqlBindings(user));
    }

    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlUpdate("MERGE INTO users (...) KEY (id) VALUES (...)")
        void save(@BindBean UserSqlBindings helper);

        @SqlQuery("SELECT ... FROM users WHERE id = :id")
        User get(@Bind("id") UUID id);
    }

    static class Mapper implements RowMapper<User> {
        public User map(ResultSet rs, StatementContext ctx) {
            return User.StorageBuilder.create(...)
                .bio(rs.getString("bio"))
                .build();
        }
    }
}
```

**Key Utilities:**
- `JdbiTypeCodecs.SqlRowReaders` - Null-safe readers (`readUuid`, `readInstant`, `readLocalDate`, `readEnum`)
- `JdbiTypeCodecs.EnumSetSqlCodec` - CSV ↔ EnumSet conversion
- `StorageBuilder` - Bypasses validation for DB reconstitution

---

## AppConfig (57 Parameters in 4 Sub-Records)

**MatchingConfig (13 params):**
- `dailyLikeLimit` (100), `dailySuperLikeLimit` (1), `dailyPassLimit` (-1=unlimited)
- `maxSwipesPerSession` (500), `suspiciousSwipeVelocity` (30.0 swipes/min)
- Weights: `distanceWeight` (0.15), `ageWeight` (0.10), `interestWeight` (0.25), `lifestyleWeight` (0.25), `paceWeight` (0.15), `responseWeight` (0.10)
- `minSharedInterests` (3), `maxDistanceKm` (500)

**ValidationConfig (12 params):**
- `minAge` (18), `maxAge` (120), `minHeightCm` (50), `maxHeightCm` (300)
- `maxBioLength` (500), `maxReportDescLength` (500), `maxNameLength` (100)
- `minAgeRangeSpan` (5), `minDistanceKm` (1), `maxInterests` (10), `maxPhotos` (2), `messageMaxPageSize` (100)

**AlgorithmConfig (16 params):**
- Distance: `nearbyDistanceKm` (5), `closeDistanceKm` (10)
- Age: `similarAgeDiff` (2), `compatibleAgeDiff` (5)
- Pace: `paceCompatibilityThreshold` (50)
- Response: `responseTimeExcellentHours` (1), `responseTimeGreatHours` (24), `responseTimeGoodHours` (72), `responseTimeWeekHours` (168), `responseTimeMonthHours` (720)
- Standout weights (6): distance (0.20), age (0.15), interest (0.25), lifestyle (0.20), completeness (0.10), activity (0.10)

**SafetyConfig (16 params):**
- `autoBanThreshold` (3 reports)
- `userTimeZone` (system default)
- `sessionTimeoutMinutes` (5), `undoWindowSeconds` (30)
- Achievement tiers: 1, 5, 10, 25, 50 matches
- `selectiveThreshold` (0.20), `openMindedThreshold` (0.60)
- `bioAchievementLength` (100), `lifestyleFieldTarget` (5)
- `cleanupRetentionDays` (30), `softDeleteRetentionDays` (90)

**Access:** `private static final AppConfig CONFIG = AppConfig.defaults();`

---

## Database Schema

**18 Tables** with soft-delete support (`deleted_at` column on all entity tables):

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `users` | 45 columns | Location, preferences, lifestyle, dealbreakers (`db_*`), verification, pace |
| `likes` | `who_likes`, `who_got_liked`, `direction`, `created_at` | UNIQUE(who_likes, who_got_liked) |
| `matches` | `id` (VARCHAR), `user_a`, `user_b`, `state`, `ended_at`, `end_reason` | Deterministic ID |
| `swipe_sessions` | `user_id`, `started_at`, `state`, swipe counts | |
| `user_stats` | `user_id`, computed metrics | |
| `platform_stats` | Aggregates | Standalone |
| `daily_pick_views` | `user_id`, `viewed_date` | Composite PK |
| `user_achievements` | `user_id`, `achievement`, `unlocked_at` | UNIQUE |
| `conversations` | `id` (VARCHAR), `user_a`, `user_b`, `last_message_at`, read timestamps, visibility | Deterministic ID |
| `messages` | `conversation_id`, `sender_id`, `content`, `created_at` | FK cascade |
| `friend_requests` | `from_user_id`, `to_user_id`, `status`, `responded_at` | |
| `notifications` | `user_id`, `type`, `title`, `message`, `is_read`, `data_json` | |
| `blocks` | `blocker_id`, `blocked_id`, `created_at` | UNIQUE |
| `reports` | `reporter_id`, `reported_user_id`, `reason`, `description` | UNIQUE |
| `profile_notes` | `author_id`, `subject_id`, `content` | Composite PK |
| `profile_views` | `viewer_id`, `viewed_id`, `viewed_at` | |
| `standouts` | `seeker_id`, `standout_user_id`, `featured_date` | |
| `undo_states` | `user_id` (PK), `like_id`, `who_likes`, `who_got_liked`, `direction`, `like_created_at`, `match_id`, `expires_at` | One per user; stores serialized Like + matchId for time-windowed undo (default 30s) |

**Indexes:** ~30 indexes on user IDs, states, timestamps, composite queries.

---

## Bootstrap Flow

```
ApplicationStartup.initialize()
    │
    ├── load() → app-config.json + env vars (DATING_APP_*)
    │
    ├── DatabaseManager.getInstance()
    │       └── HikariCP pool (max 10, min idle 2)
    │       └── MigrationRunner.migrateV1() → SchemaInitializer
    │
    ├── StorageFactory.buildH2(dbManager, config)
    │       ├── Jdbi instance + SqlObjectPlugin + type codecs
    │       ├── 5 JDBI storage implementations (+ JdbiTypeCodecs helper)
    │       └── 10 services constructed in dependency order
    │
    └── ServiceRegistry (immutable, all fields requireNonNull)
```

**Thread Safety:** `synchronized` init, `volatile` fields, idempotent.

---

## UI Architecture (JavaFX MVVM)

**NavigationService:**
- 10 ViewTypes: LOGIN, DASHBOARD, PROFILE, MATCHING, MATCHES, CHAT, STATS, PREFERENCES, STANDOUTS, SOCIAL
- History stack (max 20), context passing, transition animations (FADE, SLIDE_LEFT, SLIDE_RIGHT)

**ViewModelFactory:**
- Lazy ViewModel creation (cached singletons)
- `currentUserProperty()` bound to AppSession (Platform.runLater for thread safety)
- `UiDataAdapters` wrap storage interfaces for UI consumption

**ViewModels (11):**
- `LoginViewModel` - User selection
- `DashboardViewModel` - Overview, daily pick, notifications
- `ProfileViewModel` - Profile editing, preview
- `MatchingViewModel` - Swiping, candidate display
- `MatchesViewModel` - Match list, quality display
- `ChatViewModel` - Conversations, messaging
- `StatsViewModel` - Statistics, achievements
- `PreferencesViewModel` - Dealbreakers, settings
- `StandoutsViewModel` - Top 10 daily matches
- `SocialViewModel` - Friend requests, notifications

**BaseController:**
- Lifecycle: `initialize()` → wire ViewModel, `onLoad()` → fetch data, `onUnload()` → cleanup
- Overlay management (loading, error toasts)
- Subscription tracking for cleanup

---

## CLI Architecture

**Handler Pattern:** Each handler has `Dependencies` record for explicit DI.

| Handler | Methods |
|---------|---------|
| `MatchingHandler` | `browseCandidates()`, `viewMatches()`, `browseWhoLikedMe()`, `viewNotifications()`, `viewPendingRequests()`, `viewStandouts()` |
| `ProfileHandler` | `createUser()`, `selectUser()`, `completeProfile()`, `setDealbreakers()`, `previewProfile()`, `viewAllNotes()`, `viewProfileScore()` |
| `MessagingHandler` | `showConversations()`, `getTotalUnreadCount()` |
| `SafetyHandler` | `blockUser()`, `reportUser()`, `manageBlockedUsers()`, `verifyProfile()` |
| `StatsHandler` | `viewStatistics()`, `viewAchievements()` |

**CliTextAndInput:**
- ~40 display constants
- `InputReader` (Scanner wrapper)
- `EnumMenu` (generic enum selection)
- `requireLogin(Runnable)` guard

---

## Testing Strategy

**No Mockito** - All tests use `TestStorages.*` (in-memory HashMap/ArrayList implementations).

**TestClock:** `AppClock.setTestClock(fixedInstant)` for deterministic time.

**H2 In-Memory:** CLI handler tests use `jdbc:h2:mem:test_<UUID>`.

**Organization:**
- `@Nested @DisplayName` for scenario grouping
- `@Timeout(5)` or `@Timeout(10)` on all test classes
- 62 test classes across `app/`, `core/`, `storage/`, `ui/`

**Test Files:** 65 test classes.

---

## Key Design Patterns

### Result Pattern (No Exceptions in Services)
```java
public static record SendResult(
    boolean success, Message message, String errorMessage, ErrorCode errorCode
) {
    public static SendResult success(Message m) { ... }
    public static SendResult failure(String err, ErrorCode code) { ... }
}
```

### Builder Pattern (Optional Dependencies)
```java
MatchingService service = MatchingService.builder()
    .interactionStorage(storage)
    .activityMetricsService(metrics)  // optional
    .undoService(undo)                // optional
    .dailyService(daily)              // optional
    .build();
```

### StorageBuilder (DB Reconstitution)
```java
User user = User.StorageBuilder.create(id, name, createdAt)
    .bio(rs.getString("bio"))
    .gender(Gender.valueOf(rs.getString("gender")))
    .build();
```

### Lock Striping (Concurrent Stats)
```java
private static final int LOCK_STRIPE_COUNT = 256;
private final Object[] lockStripes = new Object[LOCK_STRIPE_COUNT];
// Usage: lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)]
```

---

## Critical Rules

### ❌ NEVER
1. Import frameworks/databases in `core/`
2. Skip constructor validation (`Objects.requireNonNull`)
3. Throw exceptions in CLI layer (use result records)
4. Use Mockito (use `TestStorages.*`)
5. Forget `mvn spotless:apply` before commit
6. Return direct collection references (use defensive copy)
7. Skip state validation in transitions
8. Hardcode config values (use `AppConfig`)
9. Access `AppSession` directly without initialization
10. Use deprecated `ServiceRegistryBuilder` (use `StorageFactory`)
11. Import removed standalone model enums (`Gender`, `UserState`, etc.) — use nested: `User.Gender`, `Match.MatchState`
12. Use `Instant.now()` directly (use `AppClock.now()`)
13. Import `core.storage.*` in ViewModels (use `UiDataAdapters`)

### ✅ ALWAYS
1. Run `mvn spotless:apply` before committing
2. Validate all constructor parameters
3. Use defensive copying for collections
4. Add `@DisplayName` to test methods
5. Update `updatedAt` on entity changes (touch pattern)
6. Use `Optional` for nullable returns
7. Group tests with `@Nested` classes
8. Use factory methods (`create()`, `of()`, `fromDatabase()`)
9. Check state before transitions
10. Use JDBI SQL Object pattern
11. Handle soft-delete consistently
12. Initialize `AppSession` via `ApplicationStartup`

---

## Common Workflows

### Add New Service
1. Add domain logic in `core/` (pure Java)
2. Add storage interface in `core/storage/` (if needed)
3. Implement in `storage/jdbi/` (JDBI pattern)
4. Wire in `StorageFactory.buildH2()`
5. Add to `ServiceRegistry`
6. Add tests (unit + integration)
7. `mvn spotless:apply && mvn verify`

### Add Config Parameter
1. Add to appropriate sub-record in `AppConfig`
2. Add validation in compact constructor
3. Add default in `AppConfig.Builder`
4. Add to `app-config.json`
5. Add env var handling in `ApplicationStartup` (if needed)
6. Use in service

---

## Known Limitations

- No authentication layer (any user can select any identity)
- No proactive notifications (UI-only alerts)
- Photo URLs are strings (no image processing)
- Manual location entry (lat/lon coordinates)
- JavaFX UI experimental (not fully integrated with all features)

---

## Data Flows

### Like → Match Creation
```
User likes candidate
    → RecommendationService.canLike() check daily limit
    → MatchingService.recordLike() creates Like record
    → ActivityMetricsService.recordSwipe() track session
    → UndoService.recordSwipe() store for potential undo
    → InteractionStorage.mutualLikeExists() check for match
    → If mutual: Match.create() + save → return Optional<Match>
```

### Send Message
```
User sends message
    → ConnectionService.sendMessage()
        1. Validate sender ACTIVE
        2. Validate recipient ACTIVE
        3. Validate match exists + canMessage() (ACTIVE or FRIENDS)
        4. Validate content (not blank, ≤1000 chars)
        5. Create/get Conversation (deterministic ID)
        6. Save Message + update conversation.lastMessageAt
        7. Return SendResult
```

### Report → Auto-Ban
```
User reports another user
    → TrustSafetyService.report()
        1. Validate not self-report
        2. Check no duplicate report exists
        3. Save Report
        4. Auto-block reporter ← reported user
        5. Count reports against reported user
        6. If count ≥ autoBanThreshold (3): User.ban()
```

### Candidate Discovery
```
Browse candidates
    → CandidateFinder.findCandidatesForUser(currentUser)
        1. Get excluded IDs (liked, passed, blocked, matched)
        2. UserStorage.findCandidates() with SQL pre-filter:
           - ACTIVE state, deleted_at IS NULL
           - gender IN interestedIn
           - age BETWEEN minAge AND maxAge
           - Optional: lat/lon bounding box
        3. In-memory filters:
           - Not self
           - No prior interaction
           - Mutual gender preferences
           - Mutual age preferences
           - Distance (Haversine ≤ maxDistanceKm)
           - Dealbreakers.Evaluator.pass()
        4. Sort by distance ascending
```

---

## Code Templates

### New Service Template
```java
package datingapp.core.matching;

import datingapp.core.storage.UserStorage;
import java.util.Objects;

public class NewService {
    private final UserStorage userStorage;

    public NewService(UserStorage userStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
    }

    public ResultType businessMethod(InputType input) {
        Objects.requireNonNull(input, "input cannot be null");
        // Business logic
        return result;
    }
}
```

### New Storage Interface Template
```java
package datingapp.core.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewStorage {
    void save(Entity entity);
    Optional<Entity> get(UUID id);
    List<Entity> findAll();
}
```

### JDBI Storage Implementation Template
```java
package datingapp.storage.jdbi;

import datingapp.core.storage.NewStorage;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public final class JdbiNewStorage implements NewStorage {
    private final Dao dao;

    public JdbiNewStorage(Jdbi jdbi) {
        this.dao = jdbi.onDemand(Dao.class);
    }

    @Override
    public void save(Entity entity) {
        dao.save(new EntityBindings(entity));
    }

    @Override
    public Entity get(UUID id) {
        return dao.get(id);
    }

    @Override
    public List<Entity> findAll() {
        return dao.findAll();
    }

    @RegisterRowMapper(Mapper.class)
    interface Dao {
        @SqlUpdate("MERGE INTO new_table (id, ...) KEY (id) VALUES (...)")
        void save(@BindBean EntityBindings b);

        @SqlQuery("SELECT * FROM new_table WHERE id = :id")
        Entity get(@Bind("id") UUID id);

        @SqlQuery("SELECT * FROM new_table")
        List<Entity> findAll();
    }

    static class Mapper implements RowMapper<Entity> {
        public Entity map(ResultSet rs, StatementContext ctx) {
            return new Entity(...);
        }
    }
}
```

### Immutable Record Template
```java
public record Entity(UUID id, String name, Instant createdAt) {
    public Entity {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public static Entity create(String name) {
        return new Entity(UUID.randomUUID(), name, AppClock.now());
    }
}
```

### Mutable Entity Template
```java
public class Entity {
    private final UUID id;
    private final Instant createdAt;
    private String name;
    private Instant updatedAt;

    private Entity(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Entity create(String name) {
        Entity e = new Entity(UUID.randomUUID(), AppClock.now());
        e.name = name;
        return e;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
        touch();
    }

    private void touch() {
        this.updatedAt = AppClock.now();
    }

    // Getters...
}
```

### Result Record Template
```java
public static record Result(boolean success, String message, DataType data) {
    public Result {
        if (success) {
            Objects.requireNonNull(data, "data required on success");
        }
    }

    public static Result success(DataType d) {
        return new Result(true, null, d);
    }

    public static Result failure(String msg) {
        return new Result(false, msg, null);
    }
}
```

---

## Debugging Tips

| Issue | Check |
|-------|-------|
| Service not wired | Verify `StorageFactory.buildH2()` includes new service |
| Database column missing | Check `SchemaInitializer.createAllTables()` DDL |
| Enum not persisting | Add codec to `JdbiTypeCodecs` |
| Test failing on time | Use `AppClock.setTestClock(fixedInstant)` |
| NullPointerException | Check `Objects.requireNonNull` in constructor |
| State transition error | Verify current state allows target state |
| Match not created | Check `mutualLikeExists()` returns true |
| Distance filter wrong | Verify `hasLocationSet()` is true, check Haversine |

---

## Key Files

| File | Purpose |
|------|---------|
| `CLAUDE.md` | Comprehensive project documentation |
| `AGENTS.md` | AI agent operational context & coding standards |
| `architecture.md` | Visual architecture diagrams |
| `STATUS.md` | Implementation status vs PRD |
| `pom.xml` | Maven build configuration |
| `Main.java` | CLI entry point |
| `DatingApp.java` | JavaFX entry point |
| `ApplicationStartup.java` | Centralized initialization |
| `StorageFactory.java` | Service wiring |
| `AppSession.java` | Current user singleton |
| `AppConfig.java` | 57-parameter configuration |

---

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 retries append ":CONFLICT".
1|2026-02-22 00:00:00|agent:qwen_code|docs|Complete QWEN.md rewrite from SOURCE CODE: 87 main files (22,325 LOC), 65 tests (14,825 LOC), 18 tables, 10 services, 5 storage interfaces, Java 25, verified package structure, actual nested types, bootstrap flow|QWEN.md
---AGENT-LOG-END---
