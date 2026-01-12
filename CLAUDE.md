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

This is a **Phase 2.0** console-based dating app using Java 21, Maven, and H2 embedded database. The project has evolved from basic matching to include sophisticated engagement tracking, quality scoring, gamification, interests matching, serendipitous discovery, user safety features, profile quality systems, verification workflows, **and a full-featured messaging system enabling matched users to communicate**.

> **For AI Agents**: See [`AGENTS.md`](./AGENTS.md) for comprehensive development guidelines including coding standards, testing patterns, and quality tools configuration.

### Two-Layer Design

```
core/       Pure Java business logic (NO framework imports, NO database code)
storage/    H2 database implementations of storage interfaces
cli/        Console UI handlers (separated from Main.java)
```

**Rule**: The `core` package must have ZERO framework or database imports. Storage interfaces are defined in `core` but implemented in `storage`.

### Key Components

**Domain Models** (in `core/`):
- `User` - Mutable entity with state machine: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`. Now includes interests set (max 10), verification status, and profile completeness metrics
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
- `ProfileNote` - Immutable record for private notes about other users (max 500 chars)
- `Conversation` - Mutable entity with deterministic ID (userA_userB), per-user read timestamps ✨ NEW
- `Message` - Immutable record with max 1000 chars, timestamp, sender reference ✨ NEW

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

**Phase 1.5+ Services** (in `core/`):
- `InterestMatcher` - Stateless utility comparing interest sets between users. Calculates overlap ratio and Jaccard index
- `AchievementService` - Gamification system tracking 11 achievements (matches, behavior, profile, safety). Evaluates progress and unlocks achievements
- `DailyPickService` - Selects one serendipitous "Daily Pick" per user per day using deterministic seeding. Generates contextual reasons for selection
- `LikerBrowserService` - Browse users who have liked you, see incoming interest
- `ProfileCompletionService` - Comprehensive profile quality scoring with 12+ tracked fields
- `VerificationService` - Profile verification workflow management

**Phase 2.0 Services** (in `core/`):
- `MessagingService` - Full-featured messaging system with authorization checks, conversation management, unread tracking ✨ NEW

**Storage Interfaces** (defined in `core/`, implemented in `storage/`):
- `UserStorage`, `LikeStorage`, `MatchStorage`, `BlockStorage`, `ReportStorage`
- `SwipeSessionStorage` - Session CRUD with timeout queries and aggregate statistics
- `UserStatsStorage`, `PlatformStatsStorage` - Metrics persistence
- `UserAchievementStorage` - Achievement unlock tracking with duplicate prevention
- `DailyPickStorage` - Tracks daily pick view history per user
- `ProfileNoteStorage` - Private notes CRUD operations
- `ProfileViewStorage` - Profile view history tracking
- `ConversationStorage` - Conversation CRUD with user-based queries and timestamp updates ✨ NEW
- `MessageStorage` - Message CRUD with pagination, counting, and cascade deletion ✨ NEW

**CLI Layer** (in `cli/`):
- `Main` - Orchestrator with menu loop and dependency wiring (in `datingapp/` package)
- `UserManagementHandler` - User creation and selection
- `ProfileHandler` - Profile completion, dealbreakers configuration, profile preview
- `MatchingHandler` - Candidate browsing, daily pick display, match viewing
- `SafetyHandler` - Blocking and reporting
- `StatsHandler` - Statistics and achievement display
- `ProfileNotesHandler` - Private note-taking for profiles
- `LikerBrowserHandler` - Browse incoming likes
- `ProfileVerificationHandler` - Profile verification UI
- `MessagingHandler` - Conversation list, message view, send/receive UI ✨ NEW
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

10. **Messaging Flow - Phase 2.0** ✨ NEW
    - **Prerequisites**: Only available between users with `ACTIVE` match
    - `MessagingService.sendMessage()` - Multi-stage validation:
      1. Verify sender exists and is ACTIVE
      2. Verify recipient exists and is ACTIVE
      3. Check for ACTIVE match between users
      4. Validate message content (1-1000 chars, non-empty)
      5. Get or create conversation (deterministic ID: `userA_userB`)
      6. Save message to database
      7. Update conversation's `lastMessageAt` timestamp
      8. Return `SendResult` with success/error details
    - **Conversation Management**:
      - Conversations created automatically on first message
      - Per-user read timestamps track unread messages
      - `markAsRead()` updates viewing user's timestamp
      - Unread count calculated: messages after user's `lastReadAt`
    - **Match State Integration**:
      - When match becomes `UNMATCHED` or `BLOCKED`, messaging disabled
      - Conversation history remains visible as read-only
      - Attempting to send returns `NO_ACTIVE_MATCH` error
    - **UI Features**:
      - Conversation list sorted by most recent message
      - Unread indicators: `(N new)` per conversation
      - Total unread count displayed in main menu
      - Message pagination (20 per page in CLI)
      - In-conversation commands: `/back`, `/older`, `/block`, `/unmatch`

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

## Recent Updates (Updated: 2026-01-11 - Latest Commits: 28940f3, de20d44, 33f8568)

### 🚀 Phase 2.0: Messaging System (Latest) ✨ NEW

**Commits:** `33f8568 (messaging 1)`, `de20d44 (messaging 1.1)`, `28940f3 (messaging 1.2)`
**Status:** Production-ready with 28 unit tests passing, integration tests pending
**Design Doc:** [`docs/MESSAGING_SYSTEM_DESIGN.md`](./docs/MESSAGING_SYSTEM_DESIGN.md)
**Review:** [`docs/messaging-code-review.md`](./docs/messaging-code-review.md)

The messaging system enables matched users to communicate via text messages, completing the core dating app experience loop: discover → match → message → connect.

**Core Components:**
- **Domain Models:**
  - `Conversation` - Mutable entity with deterministic ID (`userA_userB`), per-user read timestamps
  - `Message` - Immutable record with 1000 char limit, auto-trimming, validation
  - `ConversationPreview` - DTO for list display with unread counts and last message
  - `SendResult` - Result object with error codes (`NO_ACTIVE_MATCH`, `USER_NOT_FOUND`, `EMPTY_MESSAGE`, `MESSAGE_TOO_LONG`)

- **Services:**
  - `MessagingService` - Core business logic with 4-dependency injection (conversation storage, message storage, match storage, user storage)
  - Methods: `sendMessage()`, `getMessages()`, `getConversations()`, `markAsRead()`, `getUnreadCount()`, `getTotalUnreadCount()`
  - Authorization: Validates active match before allowing message send
  - Integration: Updates match state, respects blocks, maintains conversation history

- **Storage Layer:**
  - `ConversationStorage` interface - 7 methods including user-based queries and timestamp updates
  - `MessageStorage` interface - 7 methods including pagination and counting
  - `H2ConversationStorage` - Full JDBC implementation with unique constraint on (userA, userB)
  - `H2MessageStorage` - Full JDBC implementation with foreign key to conversations (cascade delete)
  - Indexes: `idx_conversations_user_a/b`, `idx_messages_conversation_created`

- **CLI Layer:**
  - `MessagingHandler` - 371-line handler with conversation list and single-conversation views
  - Conversation list UI: sorted by recent, unread indicators `(N new)`, message previews
  - Single conversation UI: chronological messages, pagination, in-chat commands
  - Commands: `/back`, `/older`, `/block`, `/unmatch`
  - Read-only mode when match ended: history visible, sending disabled

**Key Design Decisions:**
| Decision | Rationale |
|----------|-----------|
| Deterministic conversation IDs | Matches existing `Match.generateId()` pattern for consistency |
| Per-user read timestamps | Enables accurate unread counts without storing per-message read status |
| Message immutability | Simplifies concurrency, prevents edit bugs, matches industry norms |
| Max 1000 chars | Balances expressiveness with database efficiency |
| Cascade delete on conversation | Ensures referential integrity, simplifies cleanup |
| Match state prerequisite | Prevents spam, maintains platform safety |

**Test Coverage:**
- **Unit Tests:** 28 tests in `MessagingServiceTest.java` covering all public methods
  - `SendMessage` (9 tests) - success, match states, validation errors
  - `GetMessages` (2 tests) - ordering, empty cases
  - `CanMessage` (3 tests) - active, no match, inactive users
  - `MarkAsRead` (2 tests) - timestamp update, non-participant
  - `GetUnreadCount` (4 tests) - count accuracy, after read, empty, own messages
  - `GetTotalUnreadCount` (2 tests) - aggregate, no conversations
  - `GetConversations` (4 tests) - sorting, unread, last message, empty
  - `GetOrCreateConversation` (2 tests) - create new, return existing
- **Integration Tests:** `H2StorageIntegrationTest.java` includes 12 storage-layer tests
  - `ConversationStorageTests` (5 tests) - CRUD, sorting, timestamps
  - `MessageStorageTests` (7 tests) - CRUD, pagination, counting
- **Mock Implementations:** 4 in-memory mocks for unit testing (ConversationStorage, MessageStorage, MatchStorage, UserStorage)

**Known Limitations:**
- Integration tests exist but not in separate test files (consolidated into `H2StorageIntegrationTest.java`)
- No achievements triggered by messaging activity (marked as optional in design)
- `lastActiveAt` update on message send not yet implemented (minor feature)
- No BlockStorage defensive check (relies on Match state, acceptable for Phase 2.0)

**Database Schema:**
```sql
-- Conversations table
CREATE TABLE conversations (
    id VARCHAR(100) PRIMARY KEY,
    user_a UUID NOT NULL,
    user_b UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_message_at TIMESTAMP,
    user_a_last_read_at TIMESTAMP,
    user_b_last_read_at TIMESTAMP,
    UNIQUE (user_a, user_b)
);

-- Messages table with FK cascade
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL
        REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

**User Experience Flow:**
1. Two users match (mutual like) → Match becomes ACTIVE
2. Either user navigates to "Conversations" menu → Initially empty
3. User sends first message → Conversation auto-created
4. Recipient sees `(1 new)` indicator in conversation list
5. Recipient opens conversation → Messages displayed, unread count clears
6. Users exchange messages in real-time (poll-based in CLI)
7. If match ends (unmatch/block) → Conversation becomes read-only, history preserved

**Quality Metrics:**
- **Architecture Compliance:** PASS - Pure core domain, storage implements interfaces, CLI uses DI
- **Code Review:** Excellent - 0 critical issues, 3 low-priority suggestions
- **Test Success Rate:** 100% (28/28 unit tests + 12/12 integration tests passing)
- **Design Document Fidelity:** 95% - All core features implemented, minor optional items deferred

---

### Major Features Added (Phase 1.5 Implementation)

**Profile Quality & Engagement Features** 🎯 NEW (Latest)
- **ProfileNotesHandler** - Users can write private notes about other profiles (e.g., "Great sense of humor", "Met at coffee shop")
- **ProfileNote** domain model - Immutable record with timestamp, note text (max 500 chars), linked to target user
- **ProfileNoteStorage** interface - CRUD operations for note persistence
- **LikerBrowserService** - Browse users who have liked you, see who's interested
- **LikerBrowserHandler** - CLI interface for viewing incoming likes
- **ProfileVerificationHandler** - UI for profile verification workflows
- **VerificationService** - Core service managing profile verification logic
- **ProfileCompletionService** - Comprehensive profile quality scoring (12+ fields tracked)
- **ProfileViewStorage** - Track profile view history for analytics
- Enhanced `User` model with verification status and profile completeness metrics

**Interests & Hobbies System** ✨
- 37 predefined interests across 6 categories now fully implemented
- Users can select up to 10 interests for their profile (minimum 3 for completeness)
- Interest matching integrated into `MatchQualityService` at 30% weight
- `InterestMatcher` calculates overlap ratio and Jaccard index
- Match quality highlights show shared interests (e.g., "You share 3 interests: Hiking, Coffee, Photography")
- Interest completeness tracked in profile preview with tips to add more
- **Breaking Change**: Interest score is no longer placeholder (0.5) - now uses real overlap calculation

**Achievement System** 🏆
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

**Daily Pick Feature** 🎲
- Serendipitous "match of the day" shown before regular browsing
- Deterministic selection algorithm using date + user ID hash seed
- Same user gets same pick throughout the day (resets at midnight)
- Contextual reason generation: distance, age, lifestyle, interests, or fun fallbacks
- View tracking prevents duplicate displays after user acts on pick
- User can skip (see again later), like, or pass the daily pick
- Integrates with daily like limits and undo system

**CLI Architecture Refactoring** 🏗️
- `Main.java` simplified from monolithic (~1500 lines) to orchestrator (~200 lines)
- Handler pattern with **8 specialized handlers** (updated from 5):
  - `UserManagementHandler` - User creation and selection
  - `ProfileHandler` - Profile completion, dealbreakers, preview
  - `MatchingHandler` - Candidate browsing, daily pick, match viewing
  - `SafetyHandler` - Blocking and reporting
  - `StatsHandler` - Statistics and achievements display
  - `ProfileNotesHandler` - Private note management ✨ NEW
  - `LikerBrowserHandler` - Incoming likes browser ✨ NEW
  - `ProfileVerificationHandler` - Verification workflows ✨ NEW
- `UserSession` tracks currently logged-in user across handlers
- `InputReader` abstracts Scanner for consistent I/O
- `CliConstants` eliminates string duplication with UI constants
- Dependency injection via constructors for testability

### Storage Layer Enhancements

**Extended LikeStorage Interface**
- `countLikesToday(UUID userId, Instant startOfDay)` - Daily quota tracking
- `countPassesToday(UUID userId, Instant startOfDay)` - Daily quota tracking
- `delete(UUID likeId)` - Undo support
- `getIncomingLikes(UUID userId)` - Retrieve users who liked this user
- Supports "Who Liked Me" feature via `LikerBrowserService`

**Extended MatchStorage Interface**
- `delete(String matchId)` - Cascade deletion for undo

**New ConversationStorage Interface** ✨ NEW
- `save(Conversation)` - Create or update conversation
- `get(String conversationId)` - Retrieve by ID
- `getByUsers(UUID userA, UUID userB)` - Find conversation between two users (order-independent)
- `getConversationsFor(UUID userId)` - Get all conversations for a user, sorted by `lastMessageAt DESC`
- `updateLastMessageAt(String conversationId, Instant timestamp)` - Update timestamp on new message
- `updateReadTimestamp(String conversationId, UUID userId, Instant timestamp)` - Mark as read for specific user
- `delete(String conversationId)` - Delete conversation (cascades to messages via FK)

**New MessageStorage Interface** ✨ NEW
- `save(Message)` - Create message
- `getMessages(String conversationId, int limit, int offset)` - Paginated retrieval, ordered by `createdAt ASC`
- `getLatestMessage(String conversationId)` - Get most recent message
- `countMessages(String conversationId)` - Total message count
- `countMessagesAfter(String conversationId, Instant after)` - Count messages after timestamp (for unread calculation)
- `countMessagesNotFromSender(String conversationId, UUID senderId)` - Count messages from other users ✨ NEW in commit 28940f3
- `deleteByConversation(String conversationId)` - Delete all messages for a conversation

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

**New DailyPickStorage Interface** ✨
- `markViewed(UUID userId, LocalDate date)` - Track daily pick views
- `hasViewed(UUID userId, LocalDate date)` - Check view status
- `cleanup(LocalDate before)` - Remove old view records

**New ProfileNoteStorage Interface** ✨ NEW (Latest)
- `save(ProfileNote note)` - Create or update private notes about profiles
- `get(UUID noteId)` - Retrieve specific note by ID
- `getAllByAuthor(UUID authorUserId)` - Get all notes written by a user
- `getByTarget(UUID authorId, UUID targetUserId)` - Get note about specific user
- `delete(UUID noteId)` - Remove a note
- Maximum 500 characters per note for focused feedback

**New ProfileViewStorage Interface** ✨
- `recordView(UUID viewerId, UUID viewedUserId, Instant timestamp)` - Track profile views
- `getViewsForUser(UUID userId)` - Analytics on profile view history
- `hasViewed(UUID viewerId, UUID viewedUserId)` - Check if user has seen profile before
- Supports future analytics features (view frequency, engagement patterns)

### Enhanced User Model
- Added `interests` field (EnumSet<Interest>) with max 10 interests
- Methods: `getInterests()`, `setInterests()`, `addInterest()`, `removeInterest()`
- Validation enforces `MAX_PER_USER = 10` constraint
- `fromDatabase()` factory method updated to accept interests parameter
- Verification status tracking (verified/unverified profiles)
- Profile completeness percentage calculation
- Enhanced profile quality metrics for matching algorithms

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
- `profile_notes` table with author_id, target_user_id, note_text (max 500 chars), timestamps
- `profile_views` table tracking viewer_id, viewed_user_id, view timestamps
- Verification status columns in `users` table for profile verification workflows
- Profile completeness scoring fields in `users` table
- **NEW**: `conversations` table with unique constraint on (user_a, user_b), per-user read timestamps ✨
- **NEW**: `messages` table with foreign key to conversations (CASCADE DELETE), indexed on (conversation_id, created_at) ✨

## Testing

Tests are in `src/test/java/datingapp/`:
- `core/` - Unit tests for domain models and services (pure Java, no DB)
  - All Phase 1 & 1.5 features have comprehensive test coverage
  - `DailyLimitServiceTest` - 5 test classes covering quota logic and time resets
  - `MatchQualityServiceTest` - Score calculation, highlights, progress bars
  - `DealbreakersTest` & `DealbreakersEvaluatorTest` - Validation and filtering logic
  - `SessionServiceTest` & `SwipeSessionTest` - Session lifecycle and timeout handling
  - `ProfilePreviewServiceTest` - Completeness calculation and tip generation
  - `InterestTest` & `InterestMatcherTest` - Interest enum and matching logic
  - `AchievementServiceTest` - Achievement evaluation and unlocking
  - `DailyPickServiceTest` - Daily pick selection and reason generation
  - `ProfileNoteTest` - Private note validation (max 500 chars)
  - `ProfileCompletionServiceTest` - Profile quality scoring
  - `LikerBrowserServiceTest` - Incoming likes browsing
  - **Phase 2.0 Tests:**
    - `MessageTest` - Immutable record validation, content trimming, length limits ✨ NEW
    - `ConversationTest` - Deterministic ID generation, user queries, timestamp management ✨ NEW
    - `MessagingServiceTest` - 28 tests covering all public methods with in-memory mocks ✨ NEW
      - SendMessage (9 tests), GetMessages (2), CanMessage (3), MarkAsRead (2)
      - GetUnreadCount (4), GetTotalUnreadCount (2), GetConversations (4), GetOrCreateConversation (2)
- `storage/` - Integration tests for H2 storage implementations
  - `H2ProfileNoteStorageTest` - Profile notes CRUD operations
  - `H2ProfileViewStorageTest` - Profile view tracking
  - **Phase 2.0 Integration Tests:**
    - `H2StorageIntegrationTest.java` - Consolidated integration tests ✨ NEW
      - `ConversationStorageTests` (5 tests) - CRUD, sorting by lastMessageAt, timestamp updates
      - `MessageStorageTests` (7 tests) - CRUD, pagination, counting methods including `countMessagesNotFromSender()`

Tests use JUnit 5 with nested test classes for logical grouping. Phase 2.0 messaging tests achieve 100% passing rate (40/40 tests).

> **Quality Tools**: Run `mvn spotless:check` for code formatting validation, `mvn test` for full test suite. See `AGENTS.md` for complete quality tool configuration.

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
- **Profile Note Editing**: Notes are create/delete only, no inline editing yet
- **Advanced Analytics**: Profile view data collected but not yet exposed in UI
- **Verification Automation**: Verification process requires manual intervention
- **Messaging Achievements**: No achievements triggered by messaging activity (first message, N messages sent) ✨ NEW
- **Message Reactions**: No emoji reactions or message interactions beyond text ✨ NEW
- **Read Receipts**: No indication of when recipient read message ✨ NEW
- **Typing Indicators**: No "user is typing..." feature ✨ NEW
- **Message Editing**: Cannot edit sent messages ✨ NEW
- **Message Deletion**: Cannot delete individual messages ✨ NEW
- **Message Search**: No full-text search within conversations ✨ NEW
- **lastActiveAt Update**: User's `lastActiveAt` not updated on message send ✨ NEW

### Production Readiness Gaps
- Undo deletions should use database transactions
- In-memory undo state should persist to storage or use distributed cache
- Session cleanup should run as scheduled background job
- Daily limit counters could benefit from Redis/caching layer for high traffic
- Match quality calculation could be cached per match pair
- Daily pick view cleanup should run as scheduled maintenance task
- Achievement progress could be cached to avoid re-calculation on every check
- Profile note storage needs full-text search indexing for scalability
- Profile view analytics aggregation should use materialized views
- Verification workflows need audit trail and admin review queue
- Profile completeness scoring should be denormalized for query performance
- **NEW**: Message delivery should use pub/sub for real-time updates ✨
- **NEW**: Conversation unread counts could be denormalized to conversation table for performance ✨
- **NEW**: Message content should be sanitized for XSS when moving to web UI ✨
- **NEW**: Rate limiting needed on message sends (e.g., 100 messages/hour per user) ✨
- **NEW**: Message storage could benefit from partitioning by date for scalability ✨

## Documentation & Resources

### Core Documentation Files
- **`CLAUDE.md`** (this file) - High-level project overview, architecture, and recent updates
- **`AGENTS.md`** (1224 lines) - Comprehensive AI agent development guide with:
  - Coding standards and naming conventions
  - Testing patterns and quality tools
  - Service structure templates
  - Database interaction patterns
  - CLI handler best practices
  - Build and deployment workflows
- **`README.md`** - Project introduction and quick start guide
- **`docs/architecture.md`** - Visual architecture diagrams with Mermaid

### Planning & Analysis Documents (New)
- **`DEVELOPMENT_PLAN.md`** - Roadmap for upcoming features and architectural improvements
- **`PROJECT_ANALYSIS.md`** - Codebase structure and complexity analysis
- **`PROJECT_REFLECTION.md`** - Lessons learned and design decisions rationale
- **`PROJECT-ASSESSMENT-2026-01-10.md`** - Current state assessment and quality metrics
- **`docs/inner-structure-analysis-2026-01-10.md`** - Deep dive into internal architecture (1123 lines)
- **`docs/source-code-analysis-2026-01-10.md`** - Source code quality and maintainability analysis (634 lines)
- **`docs/Agent-Readiness-Documentation.md`** - AI agent integration guidelines
- **`docs/agent-readiness-improvement-plan.md`** - Plan for enhancing agent capabilities
- **`.github/copilot-instructions.md`** - GitHub Copilot configuration for this project

### Design Documents
- **`docs/completed-plans/`** - Completed feature design documents:
  - `02-undo-last-swipe.md` - Undo feature design
  - `04-daily-match-limit.md` - Daily quota system design
  - `05-interests-hobbies.md` - Interests matching system design
  - `08-random-match-of-day.md` - Daily Pick feature design
  - `19-achievement-system.md` - Gamification design
  - `20-profile-preview.md` - Profile completeness design
- **`docs/2026-01-08-*-design.md`** - Phase 1 feature design documents (dealbreakers, match quality, swipe sessions, statistics)
- **`docs/MESSAGING_SYSTEM_DESIGN.md`** (770 lines) - Complete Phase 2.0 messaging system design ✨ NEW
  - Domain models, storage interfaces, service layer design
  - UI/UX specifications, security considerations
  - Implementation plan with 7 phases, verification checklist
- **`docs/messaging-code-review.md`** - Post-implementation code review (production-ready, 0 critical issues) ✨ NEW
- **`docs/plans/MESSAGING_COMPLETION_PLAN.md`** (777 lines) - Completion roadmap with remaining gaps ✨ NEW

> **Quick Navigation**: Start with `README.md` for overview → `CLAUDE.md` (this file) for architecture → `AGENTS.md` for development guidelines → `docs/architecture.md` for visual diagrams
