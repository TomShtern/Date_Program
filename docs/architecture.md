<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1 (recheck before write),
# 2) locate affected doc fragment using prioritized search (see below),
# 3) archive replaced text with <!--ARCHIVE:SEQ:agent:scope-->...<!--/ARCHIVE-->,
# 4) apply minimal precise edits (edit only nearest matching fragment),
# 5) append one ChangeStamp line to the file-end changelog and inside the edited fragment (immediately after the edited paragraph or code fence),
# 6) if uncertain to auto-edit, append TODO+ChangeStamp next to nearest heading.
<!--/AGENT-DOCSYNC-->



# Dating App Architecture

## Overview

The Dating App follows a clean, four-layer architecture with strict separation of concerns:

- **`core/`** - Pure Java business logic (framework/database-free)
- **`storage/`** - JDBI declarative SQL implementations of storage interfaces
- **`cli/`** - Console UI handlers and user interaction
- **`ui/`** - JavaFX GUI with MVVM pattern and AtlantaFX theme
- **`Main.java`** - Application orchestrator and dependency wiring

**Current Stats (2026-01-30):** ~126 Java files, 581 tests passing, 60% coverage minimum.

## Package Structure Diagram

```mermaid
graph TB
    subgraph "Main Entry Point"
        Main[datingapp.Main<br/>Orchestrator<br/>Menu Loop]
    end

    subgraph "UI Layer (ui/)"
        direction TB
        subgraph "Controllers"
            BaseController[BaseController<br/>Subscription Lifecycle]
            LoginController[LoginController<br/>User Selection]
            DashboardController[DashboardController<br/>Navigation Hub]
            MatchingController[MatchingController<br/>Swipe Interface]
            MatchesController[MatchesController<br/>Match Management]
            ProfileController[ProfileController<br/>Profile Editing]
            PreferencesController[PreferencesController<br/>Discovery Settings]
        end
        subgraph "ViewModels"
            ProfileViewModel[ProfileViewModel]
            MatchingViewModel[MatchingViewModel]
            PreferencesViewModel[PreferencesViewModel]
        end
        subgraph "Utilities"
            UiAnimations[UiAnimations<br/>Animation Effects]
            UiServices[UiServices<br/>Toast + ImageCache]
            UiComponents[UiComponents<br/>Reusable Factories]
        end
    end

    subgraph "CLI Layer (cli/)"
        direction TB
        CliUtilities[CliUtilities<br/>UserSession + InputReader]
        CliConstants[CliConstants<br/>UI Strings]

        subgraph "Handlers"
            ProfileHandler[ProfileHandler<br/>Profile + User Management]
            MatchingHandler[MatchingHandler<br/>Candidate Browsing/Daily Pick]
            SafetyHandler[SafetyHandler<br/>Blocking/Reporting/Verification]
            StatsHandler[StatsHandler<br/>Statistics/Achievements]
        end
    end

    subgraph "Core Layer (core/)"
        direction TB

        subgraph "Domain Models (Grouped)"
            User[User<br/>+ nested Storage]
            Match[Match<br/>+ nested Storage]
            UserInteractions[UserInteractions<br/>Like/Block/Report + Storage]
            Messaging[Messaging<br/>Message/Conversation + Storage]
            Social[Social<br/>FriendRequest/Notification + Storage]
            Stats[Stats<br/>UserStats/MatchQuality + Storage]
            Preferences[Preferences<br/>Interest/Lifestyle/Pace]
            Achievement[Achievement<br/>+ nested Storage]
            SwipeSession[SwipeSession<br/>+ nested Storage]
            Dealbreakers[Dealbreakers<br/>+ nested Evaluator]
        end

        subgraph "Core Services (Consolidated)"
            CandidateFinder[CandidateFinder<br/>7-Stage Filter Pipeline]
            MatchingService[MatchingService<br/>Likes + LikerBrowser + PaceCompat]
            DailyService[DailyService<br/>Limits + Picks]
            TrustSafetyService[TrustSafetyService<br/>Verification + Reports]
            UndoService[UndoService<br/>30s Undo Window]
            SessionService[SessionService<br/>Session Lifecycle]
            MatchQualityService[MatchQualityService<br/>5-Factor Scoring]
            ProfileCompletionService[ProfileCompletionService<br/>Completeness Score]
            AchievementService[AchievementService<br/>Gamification System]
            MessagingService[MessagingService<br/>Conversation Management]
            RelationshipTransitionService[RelationshipTransitionService<br/>Friend Zone/Graceful Exit]
            StatsService[StatsService<br/>Statistics Aggregation]
            ValidationService[ValidationService<br/>Input Validation]
        end

        subgraph "Utilities"
            GeoUtils[GeoUtils<br/>Haversine Distance]
            AppConfig[AppConfig<br/>Configuration Record]
            ServiceRegistry[ServiceRegistry<br/>DI Container]
        end
    end

    subgraph "Storage Layer (storage/)"
        direction TB
        StorageModule[StorageModule<br/>JDBI Factory]
        DatabaseManager[DatabaseManager<br/>H2 + JDBI Setup]

        subgraph "JDBI Interfaces (jdbi/)"
            JdbiUserStorage[JdbiUserStorage]
            JdbiLikeStorage[JdbiLikeStorage]
            JdbiMatchStorage[JdbiMatchStorage]
            JdbiBlockStorage[JdbiBlockStorage]
            JdbiMessageStorage[JdbiMessageStorage]
        end

        subgraph "Row Mappers (mapper/)"
            UserMapper[UserMapper]
            MatchMapper[MatchMapper]
            MessageMapper[MessageMapper]
            EnumSetColumnMapper[EnumSetColumnMapper]
        end
    end

    subgraph "Infrastructure"
        H2Database[(H2 Database<br/>./data/dating.mv.db)]
    end

    %% Main orchestration
    Main --> CliUtilities
    Main --> ProfileHandler
    Main --> MatchingHandler
    Main --> SafetyHandler
    Main --> StatsHandler
    Main --> ServiceRegistry

    %% UI Controller relationships
    BaseController --> UiAnimations
    BaseController --> UiServices
    LoginController --> BaseController
    DashboardController --> BaseController
    MatchingController --> BaseController
    MatchesController --> BaseController
    ProfileController --> BaseController
    PreferencesController --> BaseController

    %% Controllers to ViewModels
    ProfileController --> ProfileViewModel
    MatchingController --> MatchingViewModel
    PreferencesController --> PreferencesViewModel

    %% Services use Storage interfaces
    CandidateFinder --> User
    MatchingService --> UserInteractions
    MatchingService --> Match
    MessagingService --> Messaging
    TrustSafetyService --> UserInteractions
    DailyService --> User

    %% Storage implementations
    StorageModule --> DatabaseManager
    JdbiUserStorage --> StorageModule
    JdbiLikeStorage --> StorageModule
    JdbiMatchStorage --> StorageModule
    JdbiBlockStorage --> StorageModule
    JdbiMessageStorage --> StorageModule

    DatabaseManager --> H2Database
```

## Layer Responsibilities

### 1. Core Layer (`datingapp.core`)

**Purpose:** Pure business logic with zero framework/database dependencies

**Key Classes (43 total):**

**Domain Models (14):**
- `User` - Mutable entity with state machine (INCOMPLETE → ACTIVE ↔ PAUSED → BANNED)
- `Like` - Immutable record (LIKE/PASS direction + timestamp)
- `Match` - Mutable entity (ACTIVE → UNMATCHED | BLOCKED)
- `Block` - Immutable bidirectional blocking record
- `Report` - Immutable moderation report (SPAM, HARASSMENT, FAKE_PROFILE, etc.)
- `SwipeSession` - Session lifecycle tracking (ACTIVE → COMPLETED)
- `MatchQuality` - Compatibility score (0-100), star rating, highlights
- `Dealbreakers` - User filtering preferences (lifestyle, physical, age)
- `Interest` - Enum of 37 predefined interests across 6 categories
- `Achievement` - Enum of 11 gamification achievements
- `UserAchievement` - User-achievement linking with timestamps
- `Lifestyle` - Lifestyle data model
- `UserStats` - User analytics metrics (swipes, likes, matches, etc.)
- `PlatformStats` - Platform-wide statistics

**Core Services (14):**
- `CandidateFinderService` - Interface for candidate discovery
- `CandidateFinder` - 7-stage filter pipeline implementation
- `MatchingService` - Like recording and match creation
- `UndoService` - 30-second undo window
- `DailyLimitService` - Daily quota enforcement (likes: 100/day, passes: unlimited)
- `SessionService` - Session lifecycle with timeout detection
- `MatchQualityService` - 5-factor compatibility scoring
- `DealbreakersEvaluator` - One-way filter evaluator
- `ProfilePreviewService` - Profile completeness scoring (12 fields)
- `DailyPickService` - Serendipitous daily discovery
- `InterestMatcher` - Interest overlap calculation (static utility)
- `AchievementService` - Gamification system (11 achievements)
- `ReportService` - Reporting and auto-moderation
- `StatsService` - Statistics aggregation

**Storage Interfaces (Nested in Domain Models):**
- `UserInteractions.LikeStorage` - CRUD for Like records + daily counting
- `UserInteractions.BlockStorage` - CRUD for Block records
- `UserInteractions.ReportStorage` - CRUD for Report records
- `Messaging.MessageStorage` - CRUD for Message records
- `Messaging.ConversationStorage` - CRUD for Conversation records
- `Social.FriendRequestStorage` - CRUD for FriendRequest records
- `Social.NotificationStorage` - CRUD for Notification records
- `Stats.UserStatsStorage` - User metrics persistence
- `Stats.PlatformStatsStorage` - Platform-wide metrics
- `Match.MatchStorage` - CRUD for Match entities + cascade delete
- `Achievement.UserAchievementStorage` - Achievement unlock tracking
- `SwipeSession.SwipeSessionStorage` - CRUD + timeout queries + aggregates
- `ProfilePreviewService.ProfileViewStorage` - Profile view tracking
- `User.ProfileNoteStorage` - Profile note persistence
- `DailyService.DailyPickStorage` - Daily pick view tracking
- `UserStorage` (standalone) - CRUD for User entities (primary entity storage)

**Utilities (5):**
- `GeoUtils` - Haversine distance calculation
- `AppConfig` - Immutable configuration record (13 parameters)
- `ServiceRegistry` - Dependency injection container
- `ServiceRegistryBuilder` - Factory for wiring implementations
- `MatchQualityConfig` - Weight distribution presets

**Rule:** The `core` package MUST have ZERO framework or database imports.

### 2. Storage Layer (`datingapp.storage`)

**Purpose:** H2 database implementations of storage interfaces

**Key Classes (12 total):**

**Database Management:**
- `DatabaseManager` - Singleton managing H2 connections and schema initialization
- `StorageException` - Custom RuntimeException wrapper

**H2 Implementations (10):**
- `H2UserStorage` - Users table implementation
- `H2LikeStorage` - Likes table implementation + daily counting
- `H2MatchStorage` - Matches table implementation + cascade delete
- `H2BlockStorage` - Blocks table implementation
- `H2ReportStorage` - Reports table implementation
- `H2SwipeSessionStorage` - Swipe sessions table + timeout queries
- `H2UserStatsStorage` - User stats table
- `H2PlatformStatsStorage` - Platform stats table
- `H2DailyPickViewStorage` - Daily pick views table
- `H2UserAchievementStorage` - User achievements table + duplicate prevention

**Database Schema:**
- `users` - 30 columns including identity, profile, preferences, lifestyle, dealbreakers, interests (CSV)
- `likes` - id, who_likes, who_got_liked, direction, created_at (5 columns)
- `matches` - id, user_a, user_b, created_at, state, ended_at, ended_by (7 columns with state machine)
- `blocks` - id, blocker_id, blocked_id, created_at (4 columns)
- `reports` - id, reporter_id, reported_user_id, reason, description, created_at (6 columns)
- `swipe_sessions` - id, user_id, started_at, last_activity_at, ended_at, state, swipe/like/pass/match counts (10 columns)
- `user_stats` - id, user_id, computed_at + 18 metric columns (21 columns total)
- `platform_stats` - id, computed_at, total_active_users, averages (7 columns)
- `daily_pick_views` - user_id, viewed_date, viewed_at (composite primary key)
- `user_achievements` - id, user_id, achievement, unlocked_at (unique constraint on user_id, achievement)

### 3. CLI Layer (`datingapp.cli`)

**Purpose:** Console user interface handlers (separated from Main.java)

**Key Classes (8 total in cli/ package):**

**Session & I/O:**
- `UserSession` - Currently logged-in user tracking
- `InputReader` - I/O abstraction over Scanner
- `CliConstants` - UI string constants and formatting

**Handlers (5):**
- `UserManagementHandler` - User creation and selection (~67 lines)
- `ProfileHandler` - Profile completion, dealbreakers, preview (~667 lines)
- `MatchingHandler` - Candidate browsing, daily pick display, match viewing (~512 lines)
- `SafetyHandler` - Blocking and reporting (~196 lines)
- `StatsHandler` - Statistics and achievement display (~169 lines)

**Note:** `Main.java` resides in the parent `datingapp` package (not in `cli/`), serving as the application entry point and orchestrator (~180 lines).

## Data Flow

### User Creation Flow
```
Main → UserManagementHandler → UserStorage (H2UserStorage) → DatabaseManager → H2 Database
                     → Create User entity (state: INCOMPLETE)
```

### Profile Completion Flow
```
ProfileHandler → UserStorage.update()
               → ProfilePreviewService.getPreview()
               → DealbreakersEvaluator.validate()
               → InterestMatcher.compare()
               → User.state = ACTIVE
```

### Candidate Discovery Flow
```
MatchingHandler → CandidateFinder.findCandidates()
                → Filter pipeline (7 stages):
                  1. Exclude self
                  2. ACTIVE state only
                  3. No prior interactions (likes, blocks)
                  4. Mutual gender preferences
                  5. Mutual age preferences
                  6. Within distance (GeoUtils.haversine())
                  7. Dealbreakers evaluation
                → Sort by distance
                → MatchQualityService.calculate()
                → Display to user
```

### Daily Pick Flow
```
MatchingHandler → DailyPickService.getDailyPick()
                → Deterministic seeding (date + user ID hash)
                → Filter: no blocks, no prior interactions
                → Generate reason (distance, age, lifestyle, interests)
                → DailyPickStorage.hasViewed() → Check if already shown
                → Display with banner
```

### Like Recording Flow
```
MatchingHandler → DailyLimitService.canLike()
               → MatchingService.recordLike()
               → LikeStorage.save()
               → SessionService.recordSwipe()
               → UndoService.recordSwipe()
               → If mutual like: MatchStorage.save()
```

### Undo Flow
```
MatchingHandler → UndoService.canUndo() (30s window)
               → UndoService.undo()
               → LikeStorage.delete()
               → MatchStorage.delete() (cascade)
```

### Achievement Unlocking Flow
```
StatsHandler → AchievementService.checkAndUnlock()
             → Evaluate all 11 achievements
             → UserAchievementStorage.save() (MERGE with duplicate prevention)
             → Display unlocked achievements with dates
```

### Reporting Flow
```
SafetyHandler → ReportService.report()
              → ReportStorage.save()
              → BlockStorage.save() (auto-block)
              → UserStorage.update() (auto-ban at threshold)
```

## Dependencies

### Maven Dependencies
- `com.h2database:h2:2.2.224` - Embedded database
- `org.junit.jupiter:junit-jupiter:5.10.2` - Testing framework
- `org.slf4j:slf4j-api:2.0.12` - Logging facade
- `ch.qos.logback:logback-classic:1.5.3` - Logging implementation

### Build Plugins
- `maven-compiler-plugin:3.12.1` - Java 21 compilation
- `maven-surefire-plugin:3.2.5` - Test runner (JUnit 5)
- `exec-maven-plugin:3.1.1` - Run main class
- `maven-shade-plugin:3.5.1` - Create fat JAR
- `spotless-maven-plugin:3.1.0` - Code formatting (google-java-format 1.33.0)
- `maven-checkstyle-plugin:3.3.1` - Code style validation (google_checks.xml)
- `maven-pmd-plugin:3.21.2` - Code quality (quickstart ruleset)

## Key architectural Rules

1. **Boundary Enforcement:** The `core` package MUST have ZERO framework or database imports.

2. **Interface Definition Pattern:** Storage interfaces are defined in `core/` but implemented in `storage/`.

3. **Immutable Domain Models:** Like, Block, Report, MatchQuality, Dealbreakers are immutable records.

4. **State Machine Pattern:** User, Match, SwipeSession entities have well-defined state transitions.

5. **Service Purity:** Core services depend only on interfaces, not implementations.

6. **DI Container:** ServiceRegistry holds all services and storage implementations.

7. **Handler Separation:** CLI handlers separate from Main orchestrator for testability.

## Testing Strategy

**Unit Tests** (`src/test/java/datingapp/core/`):
- 29 test classes covering all domain models and services
- Pure Java, no database (uses in-memory mock implementations)

**Integration Tests** (`src/test/java/datingapp/storage/`):
- H2StorageIntegrationTest - Full database layer testing
- H2DailyPickViewStorageTest - Daily pick tracking validation

**Test Running:**
```bash
mvn test                    # Run all tests
mvn test -Dtest=ClassName # Run specific test class
mvn test -Dtest=ClassName#methodName  # Run specific test method
```

## Configuration

**AppConfig Parameters (13 fields):**
- `autoBanThreshold: 3` - Reports needed for auto-ban
- `dailyLikeLimit: 100` - Max likes per day (-1 = unlimited)
- `dailySuperLikeLimit: 1` - Max super likes per day
- `dailyPassLimit: -1` - Max passes per day (-1 = unlimited)
- `userTimeZone: system` - Timezone for daily resets
- `maxInterests: 5` - Maximum interests per user
- `maxPhotos: 2` - Maximum photos per profile
- `maxBioLength: 500` - Maximum bio character length
- `maxReportDescLength: 500` - Maximum report description length
- `sessionTimeoutMinutes: 5` - Session inactivity timeout
- `maxSwipesPerSession: 500` - Anti-bot limit per session
- `suspiciousSwipeVelocity: 30.0` - Swipes/min warning threshold
- `undoWindowSeconds: 30` - Undo time window

**Database:**
- File: `./data/dating.mv.db`
- User: `sa`
- Password: `DATING_APP_DB_PASSWORD` environment variable (production)

**Logging:**
- Implementation: Logback Classic
- Level: INFO
- Pattern: `%msg%n` (minimal format)

## Build & Run

```bash
# Compile
mvn compile

# Format code
mvn spotless:apply

# Run application
mvn exec:java

# Run tests
mvn test

# Build fat JAR
mvn package

# Run packaged JAR
java -jar target/dating-app-1.0.0.jar
```

---

**Last Updated:** 2026-01-10
**Phase:** 1.5
**Repository:** https://github.com/TomShtern/Date_Program.git





## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
---AGENT-LOG-END---
