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

This is a **Phase 0.5B** console-based dating app using Java 21, Maven, and H2 embedded database. The project has evolved beyond basic matching to include sophisticated engagement tracking, quality scoring, and user safety features.

### Two-Layer Design

```
core/       Pure Java business logic (NO framework imports, NO database code)
storage/    H2 database implementations of storage interfaces
```

**Rule**: The `core` package must have ZERO framework or database imports. Storage interfaces are defined in `core` but implemented in `storage`.

### Key Components

**Domain Models** (in `core/`):
- `User` - Mutable entity with state machine: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Like` - Immutable record with `LIKE` or `PASS` direction and timestamp
- `Match` - Mutable entity with state machine: `ACTIVE → UNMATCHED | BLOCKED`. Deterministic ID (sorted UUID concatenation)
- `Block` - Immutable record for bidirectional blocking (when A blocks B, neither can see the other)
- `Report` - Immutable record with reason enum (SPAM, HARASSMENT, FAKE_PROFILE, etc.)
- `SwipeSession` - Mutable entity tracking continuous swiping activity with state machine: `ACTIVE → COMPLETED`
- `MatchQuality` - Immutable record containing compatibility score (0-100), star rating (1-5), highlights, and display methods
- `Dealbreakers` - Immutable record defining user's filtering preferences (lifestyle, physical, age-based)

**Core Services** (in `core/`):
- `CandidateFinder` - Multi-stage filter pipeline: not-self, ACTIVE state, no prior interaction, mutual gender preferences, mutual age preferences, within distance, dealbreakers evaluation. Sorts by distance.
- `MatchingService` - Records likes, creates matches on mutual like, integrates with UndoService
- `ReportService` - Files reports with auto-blocking and configurable auto-ban threshold

**Phase 1 Services** (in `core/`):
- `UndoService` - Allows users to undo their last swipe within configurable time window (default 30s). In-memory state tracking with lazy expiration cleanup
- `DailyLimitService` - Enforces daily quotas for likes (default 100/day) and passes (unlimited by default). Timezone-aware midnight reset
- `MatchQualityService` - Calculates 5-factor compatibility scores (distance, age, interests, lifestyle, response time) with configurable weights. Generates human-readable highlights
- `DealbreakersEvaluator` - One-way filter evaluator applying lifestyle, physical, and age dealbreakers to candidates
- `SessionService` - Manages swipe session lifecycle with timeout detection (default 5min), velocity tracking, and anti-bot checks (max 500 swipes/session, 30 swipes/min warning threshold)
- `ProfilePreviewService` - Generates profile completeness scores (12 tracked fields) with actionable improvement tips
- `StatsService` - Aggregates user and platform-wide statistics

**Storage Interfaces** (defined in `core/`, implemented in `storage/`):
- `UserStorage`, `LikeStorage`, `MatchStorage`, `BlockStorage`, `ReportStorage`
- `SwipeSessionStorage` - Session CRUD with timeout queries and aggregate statistics
- `UserStatsStorage`, `PlatformStatsStorage` - Metrics persistence

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

3. **Swipe Session Management**
   - `SessionService.getOrCreateSession()` - Auto-creates or resumes session
   - Session tracks: swipe count, like/pass/match counts, velocity, duration
   - Auto-timeout after 5 minutes of inactivity
   - Anti-bot checks: 500 max swipes/session, 30 swipes/min velocity warning

4. **Like Recording with Limits**
   - `DailyLimitService.canLike()` - Enforces daily quota (100/day default)
   - `MatchingService.recordLike()` - Saves like and creates `Match` if mutual
   - `UndoService.recordSwipe()` - Captures undo state (30s window)
   - Session counters updated via `SessionService.recordSwipe()`

5. **Match Quality Calculation**
   - `MatchQualityService.calculate()` - Computes 5-factor compatibility:
     - **Distance Score**: Linear decay from max distance (15% weight)
     - **Age Score**: Normalized against mutual age preferences (10% weight)
     - **Interest Score**: Jaccard similarity - placeholder, not yet implemented (30% weight)
     - **Lifestyle Score**: Smoking, drinking, kids, relationship goals (30% weight)
     - **Response Time Score**: Mutual like speed in tiers from <1hr to >1mo (15% weight)
   - Generates 1-5 star rating and up to 5 human-readable highlights
   - Customizable via `MatchQualityConfig` weight presets

6. **Undo Flow**
   - `UndoService.canUndo()` - Validates undo window hasn't expired
   - `UndoService.undo()` - Deletes Like and cascade-deletes Match if exists
   - State cleared to prevent double-undo
   - ⚠️ **Known Limitation**: Deletions not wrapped in transaction (Phase 0 constraint)

7. **Safety & Moderation**
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
- `userTimeZone` (default: system) - Timezone for midnight reset

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

## Recent Updates (Updated: 2026-01-09)

### Major Features Added (Phase 1 Implementation)

**Undo System**
- Users can undo their last swipe within 30 seconds
- In-memory state tracking with automatic expiration
- Cascade-deletes resulting matches when undoing a like
- Known limitation: Like/Match deletions not transactional

**Daily Limits & Quota Management**
- Configurable daily limits for likes (100/day default) and passes (unlimited)
- Timezone-aware midnight reset mechanism
- Real-time quota status with countdown to reset
- Separate tracking for likes vs passes

**Match Quality Scoring System**
- 5-factor weighted compatibility calculation (0-100 score)
- 1-5 star rating display with labels ("Excellent Match", "Great Match", etc.)
- Automatic generation of up to 5 human-readable highlights
  - "Lives nearby (2.3 km away)"
  - "You both enjoy hiking, photography"
  - "Both non-smokers"
  - "Quick mutual interest!"
- Configurable weight presets for different matching priorities
- Progress bar rendering utility for visual feedback

**Dealbreakers Filtering**
- 7 categories: smoking, drinking, kids stance, relationship goals, education, height range, age difference
- One-way filters: your dealbreakers hide candidates from you, but not vice versa
- Missing lifestyle data causes candidates to fail that dealbreaker (height is exception)
- Fluent builder API for configuration
- Debug support: `getFailedDealbreakers()` explains why candidates were filtered

**Swipe Session Tracking**
- Automatic session creation and timeout management (5min inactivity)
- Real-time metrics: swipe count, like/pass/match counts, velocity, duration
- Anti-bot protections:
  - 500 max swipes per session (hard limit)
  - 30 swipes/min velocity warning (soft limit)
- Session state machine: ACTIVE → COMPLETED
- Cross-session aggregate statistics

**Profile Completeness System**
- 12 tracked fields (7 core + 5 lifestyle)
- Percentage-based completeness score
- Context-aware improvement tips with engagement statistics
  - "Add 2 more photos - profiles with 3+ photos get 40% more matches"
  - "Expand your bio - 100+ character bios get 2x more likes"
- Visual progress bar rendering

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

### Enhanced CandidateFinder
- Integrated dealbreakers evaluation as 7th filter stage
- Now returns 0 candidates if user fails any dealbreaker (lifestyle, height, age)
- Missing lifestyle data on candidate profile causes automatic rejection

### Database Schema Updates
- New `swipe_sessions` table with state, counters, timestamps
- Added indexes on `last_activity_at` for timeout queries
- Extended `likes` table with `created_at` for daily counting

## Testing

Tests are in `src/test/java/datingapp/`:
- `core/` - Unit tests for domain models and services (pure Java, no DB)
  - All Phase 1 features have comprehensive test coverage
  - `DailyLimitServiceTest` - 5 test classes covering quota logic and time resets
  - `MatchQualityServiceTest` - Score calculation, highlights, progress bars
  - `DealbreakersTest` & `DealbreakersEvaluatorTest` - Validation and filtering logic
  - `SessionServiceTest` & `SwipeSessionTest` - Session lifecycle and timeout handling
  - `ProfilePreviewServiceTest` - Completeness calculation and tip generation
- `storage/` - Integration tests for H2 storage implementations

Tests use JUnit 5 with nested test classes for logical grouping.

## Known Limitations & Future Work

### Phase 0 Constraints Still Present
- **No Transactions**: Like/Match deletions in undo flow are not atomic
- **In-Memory Undo State**: Not persisted; lost on app restart
- **Single-User Console**: No web API, no multi-user concurrency handling
- **No Authentication**: User IDs are raw UUIDs with no auth layer

### Pending Features
- **Interests System**: Match quality interest score is placeholder (returns 0.5)
- **Photo Storage**: Photo URLs are strings, no actual image upload/storage
- **Advanced Undo**: Only last swipe is undoable; no multi-step undo history
- **Real-Time Notifications**: Match creation is synchronous, no push notifications
- **Pagination**: Candidate lists return all results, no cursor-based pagination

### Production Readiness Gaps
- Undo deletions should use database transactions
- In-memory undo state should persist to storage or use distributed cache
- Session cleanup should run as scheduled background job
- Daily limit counters could benefit from Redis/caching layer for high traffic
- Match quality calculation could be cached per match pair
