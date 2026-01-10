# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile
mvn compile

# Run application
mvn exec:java

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=CandidateFinderTest

# Run a single test method
mvn test -Dtest=CandidateFinderTest#excludesSelf

# Build fat JAR (includes all dependencies)
mvn package

# Run the packaged JAR
java -jar target/dating-app-1.0.0.jar
```

## Architecture

This is a **Phase 1.5** console-based dating app using Java 21, Maven, and H2 embedded database. The project has evolved beyond basic matching to include sophisticated engagement tracking, quality scoring, gamification, interests matching, serendipitous discovery, and user safety features.

### Two-Layer Design

```
core/       Pure Java business logic (NO framework imports, NO database code)
storage/    H2 database implementations of storage interfaces
cli/        Console UI handlers (separated from Main.java)
```

**Rule**: The `core` package must have ZERO framework or database imports. Storage interfaces are defined in `core` but implemented in `storage`.

### Key Components

**Domain Models** (in `core/`):
- `User` - Mutable entity with state machine: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`. Now includes interests set (max 10)
- `Like` - Immutable record with `LIKE` or `PASS` direction and timestamp
- `Match` - Mutable entity with state machine: `ACTIVE → UNMATCHED | BLOCKED`. Deterministic ID (sorted UUID concatenation)
- `Block` - Immutable record for bidirectional blocking (when A blocks B, neither can see the other)
- `Report` - Immutable record with reason enum (SPAM, HARASSMENT, FAKE_PROFILE, etc.)
- `SwipeSession` - Mutable entity tracking continuous swiping activity with state machine: `ACTIVE → COMPLETED`
- `MatchQuality` - Immutable record containing compatibility score (0-100), star rating (1-5), highlights, and display methods
- `Dealbreakers` - Immutable record defining user's filtering preferences (lifestyle, physical, age-based)
- `Interest` - Enum of 37 predefined interests across 6 categories (OUTDOORS, ARTS, FOOD, SPORTS, GAMES, SOCIAL)
- `Achievement` - Enum of 11 gamification achievements across 4 categories (MATCHING, BEHAVIOR, PROFILE, SAFETY)
- `UserAchievement` - Immutable record linking users to unlocked achievements with timestamps

**Core Services** (in `core/`):
- `CandidateFinder` - Multi-stage filter pipeline: not-self, ACTIVE state, no prior interaction, mutual gender preferences, mutual age preferences, within distance, dealbreakers evaluation. Sorts by distance.
- `MatchingService` - Records likes, creates matches on mutual like, integrates with UndoService
- `ReportService` - Files reports with auto-blocking and configurable auto-ban threshold

**Phase 1 Services** (in `core/`):
- `UndoService` - Allows users to undo their last swipe within configurable time window (default 30s). In-memory state tracking with lazy expiration cleanup
- `DailyLimitService` - Enforces daily quotas for likes (default 100/day) and passes (unlimited by default). Timezone-aware midnight reset
- `MatchQualityService` - Calculates 5-factor compatibility scores (distance, age, **interests**, lifestyle, response time) with configurable weights. Generates human-readable highlights
- `DealbreakersEvaluator` - One-way filter evaluator applying lifestyle, physical, and age dealbreakers to candidates
- `SessionService` - Manages swipe session lifecycle with timeout detection (default 5min), velocity tracking, and anti-bot checks (max 500 swipes/session, 30 swipes/min warning threshold)
- `ProfilePreviewService` - Generates profile completeness scores (12 tracked fields) with actionable improvement tips
- `StatsService` - Aggregates user and platform-wide statistics

**Phase 1.5 Services** (in `core/`):
- `InterestMatcher` - Stateless utility comparing interest sets between users. Calculates overlap ratio and Jaccard index
- `AchievementService` - Gamification system tracking 11 achievements (matches, behavior, profile, safety). Evaluates progress and unlocks achievements
- `DailyPickService` - Selects one serendipitous "Daily Pick" per user per day using deterministic seeding. Generates contextual reasons for selection

**Storage Interfaces** (defined in `core/`, implemented in `storage/`):
- `UserStorage`, `LikeStorage`, `MatchStorage`, `BlockStorage`, `ReportStorage`
- `SwipeSessionStorage` - Session CRUD with timeout queries and aggregate statistics
- `UserStatsStorage`, `PlatformStatsStorage` - Metrics persistence
- `UserAchievementStorage` - Achievement unlock tracking with duplicate prevention
- `DailyPickStorage` - Tracks daily pick view history per user

**CLI Layer** (in `cli/`):
- `Main` - Orchestrator with menu loop and dependency wiring
- `UserManagementHandler` - User creation and selection
- `ProfileHandler` - Profile completion, dealbreakers configuration, profile preview
- `MatchingHandler` - Candidate browsing, daily pick display, match viewing
- `SafetyHandler` - Blocking and reporting
- `StatsHandler` - Statistics and achievement display
- `UserSession` - Tracks currently logged-in user
- `InputReader` - I/O abstraction over Scanner
- `CliConstants` - UI string constants and formatting

**Utilities** (in `core/`):
- `GeoUtils` - Haversine distance calculation
- `AppConfig` - Immutable configuration record with 15+ configurable parameters
- `ServiceRegistry` - Dependency container holding all services and storage
- `ServiceRegistryBuilder` - Factory for wiring H2 or in-memory implementations
- `MatchQualityConfig` - Weight distribution presets (default, proximity-focused, lifestyle-focused)

**Database** (in `storage/`):
- `DatabaseManager` - Singleton managing H2 connections and schema initialization
- `H2*Storage` classes - JDBC implementations of storage interfaces
- Data persists to `./data/dating.mv.db`

### Enhanced Data Flow

1. **Profile Creation & Validation**
   - User created → state = `INCOMPLETE`
   - Profile completed → state = `ACTIVE`
   - User can add up to 10 interests from 37 available (minimum 3 for completeness)
   - `ProfilePreviewService.getPreview()` shows completeness (0-100%) and improvement tips

2. **Candidate Discovery with Advanced Filtering**
   - `CandidateFinder.findCandidates()` applies 7-stage filter pipeline:
     1. Exclude self
     2. Only ACTIVE users
     3. No prior interactions (likes, blocks)
     4. Mutual gender preferences
     5. Mutual age preferences
     6. Within distance radius
     7. **Dealbreakers evaluation** (lifestyle, physical, age)
   - Results sorted by distance (nearest first)

3. **Daily Pick - Serendipitous Discovery**
   - `DailyPickService.getDailyPick()` - Deterministically selects one wildcard candidate per day
   - Selection algorithm:
     - Filters out self, blocked users, and prior interactions
     - Uses seeded Random (date + user ID hash) for consistency
     - Same user gets same pick throughout the day
   - Generates contextual reasons: distance, age similarity, lifestyle matches, shared interests, or fun fallbacks
   - Shown before regular browsing with special banner
   - User can skip, like, or pass (marks as viewed on action)
   - View tracking via `DailyPickStorage` prevents duplicate displays

4. **Swipe Session Management**
   - `SessionService.getOrCreateSession()` - Auto-creates or resumes session
   - Session tracks: swipe count, like/pass/match counts, velocity, duration
   - Auto-timeout after 5 minutes of inactivity
   - Anti-bot checks: 500 max swipes/session, 30 swipes/min velocity warning

5. **Like Recording with Limits**
   - `DailyLimitService.canLike()` - Enforces daily quota (100/day default)
   - `MatchingService.recordLike()` - Saves like and creates `Match` if mutual
   - `UndoService.recordSwipe()` - Captures undo state (30s window)
   - Session counters updated via `SessionService.recordSwipe()`

6. **Match Quality Calculation with Real Interest Matching**
   - `MatchQualityService.calculate()` - Computes 5-factor compatibility:
     - **Distance Score**: Linear decay from max distance (15% weight)
     - **Age Score**: Normalized against mutual age preferences (10% weight)
     - **Interest Score**: Overlap ratio via `InterestMatcher.compare()` (30% weight)
       - Uses `shared / min(setA, setB)` metric to reward smaller set completeness
       - Returns 0.5 if neither has interests, 0.3 if only one has interests
       - Highlights shared interests in match quality display
     - **Lifestyle Score**: Smoking, drinking, kids, relationship goals (30% weight)
     - **Response Time Score**: Mutual like speed in tiers from <1hr to >1mo (15% weight)
   - Generates 1-5 star rating and up to 5 human-readable highlights
   - Customizable via `MatchQualityConfig` weight presets

7. **Achievement System - Gamification**
   - `AchievementService.checkAndUnlock()` - Evaluates all 11 achievements for a user
   - **Matching Milestones**: First Spark (1 match), Social Butterfly (5), Popular (10), Superstar (25), Legend (50)
   - **Behavior**: Selective (<20% like ratio), Open-Minded (>60% like ratio)
   - **Profile Excellence**: Complete Package (100%), Storyteller (100+ char bio), Lifestyle Guru (all lifestyle fields)
   - **Safety**: Guardian (report a fake profile)
   - Progress tracking with current/target values and unlock status
   - Achievements stored in `user_achievements` table with unique constraint preventing duplicates
   - Displayed via `StatsHandler.viewAchievements()` with unlock dates

8. **Undo Flow**
   - `UndoService.canUndo()` - Validates undo window hasn't expired
   - `UndoService.undo()` - Deletes Like and cascade-deletes Match if exists
   - State cleared to prevent double-undo
   - ⚠️ **Known Limitation**: Deletions not wrapped in transaction (Phase 0 constraint)

9. **Safety & Moderation**
   - `ReportService.report()` - Files report, auto-blocks reporter→reported, auto-bans at threshold
   - Blocking is bidirectional and permanent

## Configuration Reference

`AppConfig` supports the following configurable parameters:

### Matching & Discovery
- `maxDistanceKm` (default: 50) - Maximum candidate search radius
- `minAgeRange`, `maxAgeRange` (default: 18-100) - Global age boundaries

### Daily Limits
- `dailyLikeLimit` (default: 100) - Max likes per day (-1 = unlimited)
- `dailyPassLimit` (default: -1) - Max passes per day (-1 = unlimited)
- `userTimeZone` (default: system) - Timezone for midnight reset and daily pick rotation

### Undo Feature
- `undoWindowSeconds` (default: 30) - Time window to undo swipes

### Session Management
- `sessionTimeoutMinutes` (default: 5) - Inactivity timeout for sessions
- `maxSwipesPerSession` (default: 500) - Hard limit per session (anti-bot)
- `suspiciousSwipeVelocity` (default: 30) - Swipes/min warning threshold

### Safety & Moderation
- `autoBanThreshold` (default: 3) - Reports needed for auto-ban

### Match Quality
- `matchQualityConfig` (default: balanced) - Weight distribution preset
  - **Default**: 15% distance, 10% age, 30% interests, 30% lifestyle, 15% response
  - **Proximity**: 35% distance, 10% age, 20% interests, 25% lifestyle, 10% response
  - **Lifestyle**: 10% distance, 10% age, 25% interests, 40% lifestyle, 15% response

## Interests System

**37 Available Interests** organized into 6 categories:

- **OUTDOORS** (6): Hiking, Camping, Fishing, Cycling, Running, Climbing
- **ARTS & CULTURE** (8): Movies, Music, Concerts, Art Galleries, Theater, Photography, Reading, Writing
- **FOOD & DRINK** (6): Cooking, Baking, Wine, Craft Beer, Coffee, Foodie
- **SPORTS & FITNESS** (7): Gym, Yoga, Basketball, Soccer, Tennis, Swimming, Golf
- **GAMES & TECH** (5): Video Games, Board Games, Coding, Tech, Podcasts
- **SOCIAL** (7): Travel, Dancing, Volunteering, Pets, Dogs, Cats, Nightlife

**Constraints:**
- `MAX_PER_USER = 10` - Maximum interests per profile
- `MIN_FOR_COMPLETE = 3` - Minimum interests for profile completeness

**Matching Algorithm:**
- Uses **Overlap Ratio**: `shared / min(userA.size, userB.size)`
- Rewards scenarios where smaller interest set is fully satisfied
- Also calculates Jaccard Index for reference: `shared / union`
- Highlights shared interests in match quality display

## Recent Updates (Updated: 2026-01-10)

### Major Features Added (Phase 1.5 Implementation)

**Interests & Hobbies System** ✨ NEW
- 37 predefined interests across 6 categories now fully implemented
- Users can select up to 10 interests for their profile (minimum 3 for completeness)
- Interest matching integrated into `MatchQualityService` at 30% weight
- `InterestMatcher` calculates overlap ratio and Jaccard index
- Match quality highlights show shared interests (e.g., "You share 3 interests: Hiking, Coffee, Photography")
- Interest completeness tracked in profile preview with tips to add more
- **Breaking Change**: Interest score is no longer placeholder (0.5) - now uses real overlap calculation

**Achievement System** 🏆 NEW
- 11 gamification achievements across 4 categories:
  - **Matching Milestones** (5): First Spark, Social Butterfly, Popular, Superstar, Legend
  - **Behavior** (2): Selective, Open-Minded
  - **Profile Excellence** (3): Complete Package, Storyteller, Lifestyle Guru
  - **Safety & Community** (1): Guardian
- Each achievement has display name, description, icon emoji, and threshold
- `AchievementService` evaluates all achievements and tracks progress
- Progress display shows current/target values and unlock status
- Achievements unlock automatically on `checkAndUnlock()` calls
- Stored in `user_achievements` table with unique constraint preventing duplicates
- Viewable via Stats menu with unlock dates

**Daily Pick Feature** 🎲 NEW
- Serendipitous "match of the day" shown before regular browsing
- Deterministic selection algorithm using date + user ID hash seed
- Same user gets same pick throughout the day (resets at midnight)
- Contextual reason generation: distance, age, lifestyle, interests, or fun fallbacks
- View tracking prevents duplicate displays after user acts on pick
- User can skip (see again later), like, or pass the daily pick
- Integrates with daily like limits and undo system

**CLI Architecture Refactoring** 🏗️ NEW
- `Main.java` simplified from monolithic (~1500 lines) to orchestrator (~200 lines)
- Handler pattern with 5 specialized handlers:
  - `UserManagementHandler` - User creation and selection
  - `ProfileHandler` - Profile completion, dealbreakers, preview
  - `MatchingHandler` - Candidate browsing, daily pick, match viewing
  - `SafetyHandler` - Blocking and reporting
  - `StatsHandler` - Statistics and achievements display
- `UserSession` tracks currently logged-in user across handlers
- `InputReader` abstracts Scanner for consistent I/O
- `CliConstants` eliminates string duplication with UI constants
- Dependency injection via constructors for testability

### Storage Layer Enhancements

**Extended LikeStorage Interface**
- `countLikesToday(UUID userId, Instant startOfDay)` - Daily quota tracking
- `countPassesToday(UUID userId, Instant startOfDay)` - Daily quota tracking
- `delete(UUID likeId)` - Undo support

**Extended MatchStorage Interface**
- `delete(String matchId)` - Cascade deletion for undo

**New SwipeSessionStorage Interface**
- Full CRUD for session management
- `getActiveSession(UUID userId)` - Session resumption
- `endStaleSessions(Duration timeout)` - Batch cleanup
- `getAggregates(UUID userId)` - Cross-session analytics

**New UserAchievementStorage Interface** ✨ NEW
- `save(UserAchievement)` - Idempotent unlock via MERGE statement
- `getUnlocked(UUID userId)` - Retrieve all unlocked achievements
- `hasAchievement(UUID userId, Achievement)` - Check specific achievement
- `countUnlocked(UUID userId)` - Total unlocked count

**New DailyPickStorage Interface** ✨ NEW
- `markViewed(UUID userId, LocalDate date)` - Track daily pick views
- `hasViewed(UUID userId, LocalDate date)` - Check view status
- `cleanup(LocalDate before)` - Remove old view records

### Enhanced User Model
- Added `interests` field (EnumSet<Interest>) with max 10 interests
- Methods: `getInterests()`, `setInterests()`, `addInterest()`, `removeInterest()`
- Validation enforces `MAX_PER_USER = 10` constraint
- `fromDatabase()` factory method updated to accept interests parameter

### Enhanced CandidateFinder
- Integrated dealbreakers evaluation as 7th filter stage
- Now returns 0 candidates if user fails any dealbreaker (lifestyle, height, age)
- Missing lifestyle data on candidate profile causes automatic rejection

### Database Schema Updates
- New `swipe_sessions` table with state, counters, timestamps
- New `user_achievements` table with unique constraint on (user_id, achievement)
- New `daily_pick_views` table tracking view history per user/date
- Added indexes on `last_activity_at` for timeout queries
- Extended `likes` table with `created_at` for daily counting
- Extended `users` table to store interests (implementation-specific - check H2UserStorage)

## Testing

Tests are in `src/test/java/datingapp/`:
- `core/` - Unit tests for domain models and services (pure Java, no DB)
  - All Phase 1 features have comprehensive test coverage
  - `DailyLimitServiceTest` - 5 test classes covering quota logic and time resets
  - `MatchQualityServiceTest` - Score calculation, highlights, progress bars
  - `DealbreakersTest` & `DealbreakersEvaluatorTest` - Validation and filtering logic
  - `SessionServiceTest` & `SwipeSessionTest` - Session lifecycle and timeout handling
  - `ProfilePreviewServiceTest` - Completeness calculation and tip generation
  - `InterestTest` & `InterestMatcherTest` - Interest enum and matching logic ✨ NEW
  - `AchievementServiceTest` - Achievement evaluation and unlocking ✨ NEW
  - `DailyPickServiceTest` - Daily pick selection and reason generation ✨ NEW
- `storage/` - Integration tests for H2 storage implementations

Tests use JUnit 5 with nested test classes for logical grouping.

## Known Limitations & Future Work

### Phase 0 Constraints Still Present
- **No Transactions**: Like/Match deletions in undo flow are not atomic
- **In-Memory Undo State**: Not persisted; lost on app restart
- **Single-User Console**: No web API, no multi-user concurrency handling
- **No Authentication**: User IDs are raw UUIDs with no auth layer

### Pending Features
- **Custom Interests**: Users cannot add custom interests beyond the 37 predefined
- **Photo Storage**: Photo URLs are strings, no actual image upload/storage
- **Advanced Undo**: Only last swipe is undoable; no multi-step undo history
- **Real-Time Notifications**: Match creation is synchronous, no push notifications
- **Pagination**: Candidate lists return all results, no cursor-based pagination
- **Achievement Notifications**: No in-app notification when achievements unlock
- **Daily Pick History**: Cannot review previous daily picks

### Production Readiness Gaps
- Undo deletions should use database transactions
- In-memory undo state should persist to storage or use distributed cache
- Session cleanup should run as scheduled background job
- Daily limit counters could benefit from Redis/caching layer for high traffic
- Match quality calculation could be cached per match pair
- Daily pick view cleanup should run as scheduled maintenance task
- Achievement progress could be cached to avoid re-calculation on every check
