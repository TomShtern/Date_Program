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



# Dating App CLI - Project Context

## Overview
This is a command-line based dating application built with Java 21, Maven, and H2 embedded database. The project follows a clean architecture with strict separation between business logic and persistence layers. It has evolved from basic matching to include sophisticated features like messaging, relationship transitions, pace compatibility matching, gamification, profile verification, standout recommendations, and comprehensive safety features.

The application has both CLI and UI interfaces, with the UI layer built using JavaFX. The architecture emphasizes clean separation of concerns with core business logic completely isolated from database and framework dependencies.

## Key Features
- **User Management**: Complete user lifecycle with profile creation, verification, and state management
- **Advanced Matching**: Multi-factor compatibility scoring with configurable weights
- **Messaging System**: Full-featured conversation management between matches
- **Relationship Transitions**: Friend zone requests and graceful exit options
- **Gamification**: Achievement system with 11 different milestones
- **Safety & Trust**: Reporting, blocking, and verification systems
- **Profile Enhancement**: Notes, standouts, and profile completion scoring
- **Configurable Parameters**: Extensive configuration options for all aspects of the app
- **Data Retention**: Soft-delete support with configurable cleanup policies
- **Performance Monitoring**: Built-in performance monitoring capabilities

## Project Structure
```
datingapp/
├── app/           Application layer (CLI handlers, UI controllers)
│   └── cli/       Console UI handlers
├── core/          Pure Java business logic (NO framework/database imports)
│   ├── storage/   Storage interfaces (defined in core, implemented in storage/)
│   └── Messaging.java  Messaging domain models (Message, Conversation)
│   └── Preferences.java  User preferences (Interests, Lifestyle choices)
│   └── Social.java       Social domain models (FriendRequest, Notification)
├── storage/       H2 database implementations
│   ├── jdbi/      JDBI adapter implementations
│   ├── mapper/    Custom type mappers for JDBI
│   └── DatabaseManager.java  Database connection and schema management
└── ui/            JavaFX UI layer (controllers, viewmodels, views)
└── Main.java      Application entry point
```

## Architecture Principles
1. **Core Purity**: The `core/` package has ZERO framework or database imports
2. **Interface Definition**: Storage interfaces defined in `core/`, implemented in `storage/`
3. **Dependency Injection**: Constructor injection ONLY
4. **Immutability**: Use records for immutable data
5. **State Machines**: Mutable entities have explicit state transitions
6. **MVC Pattern**: UI layer follows Model-View-Controller pattern with ViewModels
7. **Consolidated Storage**: Using JDBI for database access with SQL Object pattern
8. **Centralized Configuration**: All configurable values in `AppConfig` record
9. **Service Registry**: Single point of access for all services via `ServiceRegistry`
10. **Single Responsibility**: Each class has a single, well-defined responsibility
11. **Defensive Copying**: Collections are defensively copied when returned from getters
12. **Fail-Fast Validation**: Constructor parameters are validated immediately
13. **Soft-Delete Support**: All entities support soft-delete with configurable retention
14. **Performance Monitoring**: Built-in performance monitoring for critical operations
15. **External Configuration**: Configurable via JSON files and environment variables

## Key Features (Phase 4)

### Core Domain Models
- `User` - Mutable entity with state machine: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`. Includes interests set (max 10), verification status, profile completeness metrics, pace preferences, soft-delete support. Contains nested enums: `Gender`, `UserState`, `VerificationMethod`
- `Like` - Immutable record with `LIKE` or `PASS` direction and timestamp
- `Match` - Mutable entity with state machine: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`. Deterministic ID generated from sorted UUIDs (lexicographically smaller UUID first) using `Match.generateId(userA, userB)`. Contains nested enums: `State`, `ArchiveReason`
- `Block` - Immutable record for bidirectional blocking
- `Report` - Immutable record with reason enum (SPAM, HARASSMENT, FAKE_PROFILE, etc.)
- `SwipeSession` - Mutable entity tracking continuous swiping activity
- `MatchQuality` - Immutable record containing compatibility score (0-100), star rating (1-5), highlights
- `Dealbreakers` - Immutable record defining user's filtering preferences
- `Interest` - Enum of 37 predefined interests across 6 categories (OUTDOORS, ARTS & CULTURE, FOOD & DRINK, SPORTS & FITNESS, GAMES & TECH, SOCIAL). Contains nested `Category` enum.
- `Achievement` - Enum of 11 gamification achievements across 4 categories (Matching Milestones, Behavior, Profile Excellence, Safety & Community). Contains nested `Category` enum.
- `UserAchievement` - Immutable record linking users to unlocked achievements
- `ProfileNote` - Immutable record for private notes about other users (max 500 chars)
- `Messaging.Message` - Immutable record with max 1000 chars, timestamp, sender reference
- `Messaging.Conversation` - Mutable entity with deterministic ID (userA_userB where UUIDs are sorted lexicographically), per-user read timestamps, archiving support
- `Social.FriendRequest` - Immutable record for Friend Zone requests with status tracking
- `Social.Notification` - Immutable record for user notifications with type and metadata
- `PacePreferences` - Immutable record tracking messaging frequency, time to first date, communication style, and depth preference. Contains nested enums: `MessagingFrequency`, `TimeToFirstDate`, `CommunicationStyle`, `DepthPreference`
- `Standout` - Immutable record for standout recommendations with scoring algorithm
- `Lifestyle` - Container class with nested enums: `Smoking`, `Drinking`, `WantsKids`, `LookingFor`, `Education`

### Core Services
- `CandidateFinder` - Multi-stage filter pipeline following 7-stage Stream pattern:
  1. `filter(!self)` - Exclude current user
  2. `filter(active)` - Only ACTIVE users
  3. `filter(noPriorInteraction)` - No previous like/pass
  4. `filter(mutualGender)` - Mutual interest in gender
  5. `filter(mutualAge)` - Within age preferences
  6. `filter(distance)` - Within distance threshold
  7. `filter(dealbreakers)` - Pass dealbreaker evaluation
  Sorts results by distance.
- `MatchingService` - Records likes, creates matches on mutual like, integrates with UndoService
- `ReportService` - Files reports with auto-blocking and configurable auto-ban threshold
- `UndoService` - Allows users to undo their last swipe within configurable time window (default 30s)
- `DailyService` - Enforces daily quotas for likes (default 100/day) and passes (unlimited by default)
- `MatchQualityService` - Calculates 5-factor compatibility scores (distance, age, interests, lifestyle, pace) with configurable weights
- `DealbreakersEvaluator` - One-way filter evaluator applying lifestyle, physical, and age dealbreakers
- `SessionService` - Manages swipe session lifecycle with timeout detection, velocity tracking
- `ProfilePreviewService` - Generates profile completeness scores with actionable improvement tips
- `StatsService` - Aggregates user and platform-wide statistics
- `InterestMatcher` - Stateless utility comparing interest sets between users
- `AchievementService` - Gamification system tracking 11 achievements
- `DailyPickService` - Selects one serendipitous "Daily Pick" per user per day using deterministic seeding
- `LikerBrowserService` - Browse users who have liked you
- `ProfileCompletionService` - Comprehensive profile quality scoring
- `VerificationService` - Profile verification workflow management
- `MessagingService` - Full-featured messaging system with authorization checks
- `RelationshipTransitionService` - Manages relationship lifecycle transitions: Friend Zone requests and Graceful Exit
- `StandoutsService` - Generates standout recommendations based on compatibility and activity
- `CleanupService` - Handles soft-delete cleanup and data retention policies
- `TrustSafetyService` - Consolidated trust and safety operations

### Storage Interfaces (defined in `core/`, implemented in `storage/`)
- `UserStorage`, `LikeStorage`, `MatchStorage`, `BlockStorage`, `ReportStorage`
- `SwipeSessionStorage` - Session CRUD with timeout queries and aggregate statistics
- `StatsStorage` - Consolidated metrics persistence (user and platform stats)
- `MessagingStorage` - Consolidated messaging persistence (conversations and messages)
- `SocialStorage` - Consolidated social features persistence (friend requests and notifications)

### CLI Layer
- `Main` - Orchestrator with menu loop and dependency wiring (in `datingapp/` package)
- `HandlerFactory` - Factory for creating CLI handlers with proper dependency injection
- `ProfileHandler` - Profile completion, dealbreakers configuration, profile preview
- `MatchingHandler` - Candidate browsing, daily pick display, match viewing, standout recommendations
- `SafetyHandler` - Blocking and reporting
- `StatsHandler` - Statistics and achievement display
- `ProfileNotesHandler` - Private note-taking for profiles
- `LikerBrowserHandler` - Browse incoming likes
- `MessagingHandler` - Conversation list, message view, send/receive UI
- `RelationshipHandler` - Friend Zone requests, notifications, and relationship transitions UI
- `InputReader` - I/O abstraction over Scanner
- `CliSupport` - UI string constants and formatting utilities
- `AppBootstrap` - Centralized application initialization for both CLI and JavaFX
- `ConfigLoader` - Configuration loading from JSON files and environment variables

### UI Layer (JavaFX)
- `DatingApp` - JavaFX Application entry point
- `NavigationService` - Routing and navigation between views
- `UISession` - Session management for UI state
- `ViewFactory` - Factory for creating view instances
- `ViewModelFactory` - Factory for creating view model instances
- `controller/` - JavaFX controller classes (e.g., `MatchingController`)
- `viewmodel/` - ViewModel classes for managing UI state
- FXML files for UI layout
- CSS files for styling (theme.css)
- MVVM Pattern: Uses Model-View-ViewModel architecture for clean separation of concerns

### API Layer
- `RestApiServer` - REST API server implementation with endpoints for all major functionality
- `Endpoints` - HTTP endpoint definitions following REST conventions

## Interests System
The application includes 37 predefined interests across 6 categories:

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

## Achievement System
The application includes 11 gamification achievements across 4 categories:

**Matching Milestones** (5):
- `FIRST_SPARK` - Get 1 match
- `SOCIAL_BUTTERFLY` - Get 5 matches
- `POPULAR` - Get 10 matches
- `SUPERSTAR` - Get 25 matches
- `LEGEND` - Get 50 matches

**Behavior** (2):
- `SELECTIVE` - Maintain <20% like ratio
- `OPEN_MINDED` - Maintain >60% like ratio

**Profile Excellence** (3):
- `COMPLETE_PACKAGE` - Achieve 100% profile completeness
- `STORYTELLER` - Write a bio with 100+ characters
- `LIFESTYLE_GURU` - Complete all lifestyle fields

**Safety & Community** (1):
- `GUARDIAN` - Report a fake profile

## Service Registry & Dependency Injection
The application uses a Service Registry pattern for dependency management:

- `ServiceRegistry` - Central container holding all services and storage implementations
- `StorageFactory` - Factory for wiring H2 implementations and service initialization (replaces deprecated ServiceRegistryBuilder)
- All services are injected via constructors following pure dependency injection
- The registry is built once in `Main.java` and dependencies are passed to handlers

## GeoUtils & Location-Based Matching
The application uses Haversine distance calculation for location-based matching:

- `GeoUtils` - Utility class with static methods for distance calculation
- **Haversine Formula**: Calculates great-circle distances between two points on Earth
- Distance is calculated in kilometers between user coordinates (lat/lon)
- Used in `CandidateFinder` to filter candidates within user's `maxDistanceKm`
- Distance factor contributes 15% to match quality score (configurable)

## Match Quality Configuration
The match quality system calculates compatibility across 6 factors with configurable weights:

- **Distance Score** (15% default): Linear decay from max distance
- **Age Score** (10% default): Normalized against mutual age preferences
- **Interest Score** (25% default): Overlap ratio via `InterestMatcher.compare()`
  - Uses `shared / min(setA, setB)` metric rewarding smaller set completeness
  - Returns 0.5 if neither has interests, 0.3 if only one has interests
- **Lifestyle Score** (25% default): Smoking, drinking, kids, relationship goals
- **Pace Score** (15% default): Compatibility based on messaging frequency, time to first date, communication style, and depth preferences
- **Response Time Score** (10% default): Mutual like speed in tiers from <1hr to >1mo

## Dealbreakers Configuration
The dealbreakers system allows users to set filtering preferences across multiple dimensions:

- **Lifestyle Dealbreakers**: Smoking, drinking, children, relationship goals
- **Physical Dealbreakers**: Height range requirements
- **Age Dealbreakers**: Age range preferences beyond basic min/max
- Applied as 7th filter stage in `CandidateFinder`
- If any dealbreaker is violated, candidate is excluded from results
- Missing lifestyle data on candidate profile causes automatic rejection

## User State Machine
The User entity has a well-defined state machine with explicit transitions:

```
INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
```

**State Transitions:**
- `activate()`: INCOMPLETE/PAUSED → ACTIVE (requires `isComplete()`)
- `pause()`: ACTIVE → PAUSED
- `ban()`: ANY → BANNED (one-way, irreversible)
- Profile completion is required before activation

## Database Management
The application uses H2 embedded database with proper connection management:

- `DatabaseManager` - Singleton managing H2 connections and schema initialization
- Connection pooling handled internally by H2
- Schema initialized automatically on first access
- Data persists to `./data/dating.mv.db`
- Proper cleanup via `shutdown()` method


## Profile Completion Service
The profile completion system evaluates 12+ tracked fields for quality scoring:

- **Required Fields**: Name, bio, birth date, gender, interested in, location
- **Optional Enhancements**: Photos (up to 2), interests (up to 10), lifestyle details
- **Scoring Algorithm**: Percentage of completed recommended fields
- **Actionable Tips**: Provides specific improvement suggestions
- **Tier System**: Emoji indicators based on completion percentage


## Advanced Database Queries
The application implements several optimized database patterns:

- **Indexing Strategy**: Strategic indexes on frequently queried columns (user IDs, timestamps)
- **Batch Operations**: Efficient bulk operations where appropriate
- **Transaction Boundaries**: Though full ACID transactions aren't implemented for undo, proper isolation where needed

## Performance Considerations
The application addresses several performance aspects:

- **Memory Management**: Proper resource cleanup and garbage collection
- **Lazy Loading**: Load data only when needed in UI
- **Caching Strategies**: Potential for caching frequently accessed data
- **Efficient Algorithms**: Optimized matching and filtering algorithms
- **UI Responsiveness**: Background threads for long-running operations in UI

## Security Considerations
The application implements several security measures:

- **Authentication Ready**: Architecture prepared for authentication layer (though not implemented)
- **Data Privacy**: Personal information properly stored and accessed
- **Access Control**: Proper service layer authorization checks
- **Secure Defaults**: Safe default configurations



## Build & Development Commands

### Environment Setup (Windows 11)
To ensure emojis and special characters display correctly in the terminal:
```powershell
chcp 65001
```

### Essential Commands
```bash
# Compile
mvn compile

# Run CLI application
mvn exec:java

# Run JavaFX UI application
mvn javafx:run

# Run tests
mvn test

# Format code (REQUIRED before committing)
mvn spotless:apply

# Build fat JAR
mvn package

# Full build with quality checks
mvn clean verify package
```

### Quality Tools
- **Spotless**: Code formatting (Google Java Format) - BUILD FAILS if violated
- **Checkstyle**: Code style validation (non-blocking)
- **PMD**: Code quality analysis (non-blocking)
- **JaCoCo**: Code coverage (target: 80%+)

## Dependencies
- **H2 Database**: 2.4.240 - Embedded database
- **JDBI**: 3.51.0 - Declarative SQL framework (core and sqlobject)
- **JUnit Jupiter**: 5.14.2 - Testing framework
- **SLF4J API**: 2.0.17 - Logging facade
- **Logback Classic**: 1.5.25 - Logging implementation
- **Jackson**: Core and Databind (2.21.0) - JSON support
- **JavaFX**: Controls, FXML, Graphics (25.0.1) - UI framework
- **AtlantaFX**: 2.1.0 - Modern theme based on GitHub Primer
- **Ikonli**: Core, JavaFX, MaterialDesign2 pack (12.4.0) - Professional SVG icon library

## Configuration
The application uses `AppConfig` with 60+ configurable parameters:

### Matching & Discovery
- `maxDistanceKm` (default: 500) - Maximum candidate search radius
- `minAgeRange`, `maxAgeRange` (default: 18-120) - Global age boundaries

### Daily Limits
- `dailyLikeLimit` (default: 100) - Max likes per day (-1 = unlimited)
- `dailySuperLikeLimit` (default: 1) - Max super likes per day
- `dailyPassLimit` (default: -1) - Max passes per day (-1 = unlimited)
- `userTimeZone` (default: system) - Timezone for midnight reset and daily pick rotation

### Undo Feature
- `undoWindowSeconds` (default: 30) - Time window to undo swipes

### Session Management
- `sessionTimeoutMinutes` (default: 5) - Inactivity timeout for sessions
- `maxSwipesPerSession` (default: 500) - Hard limit per session (anti-bot)
- `suspiciousSwipeVelocity` (default: 30.0) - Swipes/min warning threshold

### Safety & Moderation
- `autoBanThreshold` (default: 3) - Reports needed for auto-ban

### Match Quality
- `distanceWeight` (default: 0.15) - Weight for distance score
- `ageWeight` (default: 0.10) - Weight for age score
- `interestWeight` (default: 0.25) - Weight for interest score
- `lifestyleWeight` (default: 0.25) - Weight for lifestyle score
- `paceWeight` (default: 0.15) - Weight for pace score
- `responseWeight` (default: 0.10) - Weight for response time score

### Standout Scoring Weights
- `standoutDistanceWeight` (default: 0.20) - Weight for distance in standouts
- `standoutAgeWeight` (default: 0.15) - Weight for age in standouts
- `standoutInterestWeight` (default: 0.25) - Weight for interests in standouts
- `standoutLifestyleWeight` (default: 0.20) - Weight for lifestyle in standouts
- `standoutCompletenessWeight` (default: 0.10) - Weight for profile completeness
- `standoutActivityWeight` (default: 0.10) - Weight for activity recency

### Algorithm Thresholds
- `nearbyDistanceKm` (default: 5) - Distance considered "nearby"
- `closeDistanceKm` (default: 10) - Distance considered "close"
- `similarAgeDiff` (default: 2) - Age difference considered "similar"
- `compatibleAgeDiff` (default: 5) - Age difference considered "compatible"
- `minSharedInterests` (default: 3) - Min shared interests for "many"
- `paceCompatibilityThreshold` (default: 50) - Min pace score for compatibility
- `responseTimeExcellentHours` (default: 1) - Response time for "excellent"
- `responseTimeGreatHours` (default: 24) - Response time for "great"
- `responseTimeGoodHours` (default: 72) - Response time for "good"
- `responseTimeWeekHours` (default: 168) - Response time threshold for "okay"
- `responseTimeMonthHours` (default: 720) - Response time threshold for "low"

### Achievement Thresholds
- `achievementMatchTier1` (default: 1) - First match milestone
- `achievementMatchTier2` (default: 5) - Second match milestone
- `achievementMatchTier3` (default: 10) - Third match milestone
- `achievementMatchTier4` (default: 25) - Fourth match milestone
- `achievementMatchTier5` (default: 50) - Fifth match milestone
- `minSwipesForBehaviorAchievement` (default: 50) - Min swipes to evaluate behavior
- `selectiveThreshold` (default: 0.20) - Like ratio below which behavior is "selective"
- `openMindedThreshold` (default: 0.60) - Like ratio above which behavior is "open-minded"
- `bioAchievementLength` (default: 100) - Min bio length for detailed writer achievement
- `lifestyleFieldTarget` (default: 5) - Lifestyle fields needed for guru achievement

### Validation Bounds
- `minAge` (default: 18) - Min legal age
- `maxAge` (default: 120) - Max valid age
- `minHeightCm` (default: 50) - Min valid height
- `maxHeightCm` (default: 300) - Max valid height
- `minDistanceKm` (default: 1) - Min search distance
- `maxNameLength` (default: 100) - Max name length
- `minAgeRangeSpan` (default: 5) - Min age range span
- `maxInterests` (default: 10) - Max interests per user
- `maxPhotos` (default: 2) - Max photos per user
- `maxBioLength` (default: 500) - Max bio length
- `maxReportDescLength` (default: 500) - Max report description length

### Data Retention & Cleanup
- `cleanupRetentionDays` (default: 30) - Days to retain expired data before cleanup
- `softDeleteRetentionDays` (default: 90) - Days before purging soft-deleted rows
- `messageMaxPageSize` (default: 100) - Max messages per page query

### Additional Configuration Options
Beyond the basic configuration, the application supports additional settings:

- **UI Configuration**: Window sizes, themes, animations
- **Performance Tuning**: Database connection pool settings, cache sizes
- **Feature Flags**: Enable/disable specific features dynamically
- **Localization**: Potential for multi-language support (though not currently implemented)
- **External Configuration**: JSON files and environment variables support


## Database Schema
- Persistent data stored in `./data/dating.mv.db`
- **Users table**: Stores user profiles with all attributes (location, preferences, interests, lifestyle, verification, pace preferences, etc.) with soft-delete support
- **Likes table**: Records user interactions (likes/passes) with timestamps and soft-delete support
- **Matches table**: Stores mutual likes with deterministic IDs and soft-delete support
- **Blocks table**: Bidirectional blocking relationships with soft-delete support
- **Reports table**: User reports with reasons and soft-delete support
- **Conversations table**: Messaging conversations between matched users with soft-delete support
- **Messages table**: Individual messages with foreign key to conversations and soft-delete support
- **Swipe sessions table**: Session tracking with timestamps and counters
- **User stats table**: User statistics snapshots
- **Platform stats table**: Platform-wide statistics
- **Daily pick views table**: Tracks which daily picks users have seen
- **User achievements table**: Gamification tracking
- **Friend requests table**: Friend zone request tracking
- **Notifications table**: User notifications with read status
- **Profile notes table**: Private notes about other users
- **Profile views table**: Tracks profile viewing history
- **Schema version table**: Tracks database schema version for migrations
- **Foreign Key Constraints**: Added for referential integrity with cascade deletes
- **Migration Support**: Automated schema migration system for backward compatibility



## Testing Strategy
- **Unit Tests**: Pure Java, no database (in `core/`)
- **Integration Tests**: With H2 database (in `storage/`)
- **In-Memory Mocks**: Create inline implementations in tests instead of Mockito (NO external mocking libraries)
- **Test Coverage**: Minimum 80% line coverage (enforced by JaCoCo)
- **Naming Convention**: `{ClassName}Test.java` with `@DisplayName` annotations
- **Organization**: `@Nested` classes for logical grouping
- **Setup**: `@BeforeEach` for test initialization

## Coding Standards & Patterns

### Philosophy
- **Pure Core:** The `core/` package is a "POJO-only" zone. No JDBC, no Jackson, no JavaFX.
- **Deterministic Logic:** Everything from Match IDs to Daily Picks must be reproducible (seeded randomness or sorted IDs).
- **Safety First:** Use a "fail-fast" approach. Validate state and nulls in constructors, not during execution.
- **Single Responsibility:** Each class has a single, well-defined responsibility.
- **Defensive Programming:** Use defensive copying for collections and validate inputs.
- **External Configuration:** Support configuration via JSON files and environment variables.

### Naming Conventions
**Classes:**
- Domain models: Singular, clear entity names (`User`, `Match`, `Like`)
- Services: Suffix with "Service" (`MatchingService`, `DailyPickService`)
- Storage interfaces: Suffix with "Storage" (`UserStorage`, `LikeStorage`)
- Storage implementations: Prefix with database type (`H2UserStorage`)
- Handlers: Suffix with "Handler" (`ProfileHandler`, `MatchingHandler`)
- Utilities: Descriptive names (`GeoUtils`, `InterestMatcher`)
- Containers: For grouping related classes (`Messaging`, `Social`, `Preferences`, `Lifestyle`)

**Methods:**
- Factory methods: `create()`, `of()`, `fromDatabase()`, `builder()`
- Getters/Setters: `getId()`, `getState()`, `getName()`, `setName()`
- Predicates: `isComplete()`, `hasAnyDealbreaker()`, `canLike()`
- State transitions: `activate()`, `pause()`, `ban()`, `unmatch()`
- Bulk operations: `findAll()`, `findByUser()`, `deleteAll()`

**Variables:**
- Specific entity references: `userId`, `matchId`, `likeId`
- Ordered pairs: `userA`, `userB` (when ordering matters)
- Directional clarity: `perspectiveUserId`, `otherUserId`
- Collections: `alreadyInteracted`, `blockedUsers`, `sharedIntersects`

### Storage Pattern
- **JDBI Framework:** Using JDBI 3 with SQL Object pattern for database access
- **SQL Patterns:**
  - Schema initialization handled by `MigrationRunner.migrateV1()` and `SchemaInitializer.createAllTables()` using `IF NOT EXISTS`
  - Use JDBI's SQL Object pattern for type-safe queries
  - Wrap `SQLException` in custom `RuntimeException` (e.g., `StorageException`)
- **Null Safety:** Use `Optional<T>` for all storage lookups and nullable service returns
- **Soft Deletes:** Support soft-delete with configurable retention period
- **Foreign Keys:** Use foreign key constraints with cascade deletes for referential integrity

### Configuration Pattern
- **AppConfig Record:** Centralized configuration using a record with validation
- **Builder Pattern:** Support for custom configuration via builder
- **External Loading:** Load from JSON files and environment variables
- **Validation:** Validate configuration values at startup

### Service Initialization Pattern
- **StorageFactory:** Uses `StorageFactory.buildH2()` method to wire all services and storage implementations
- **AppBootstrap:** Centralized application initialization for both CLI and JavaFX entry points
- **AppSession:** Unified session management singleton for current user state across all interfaces

### Logging
- **Framework:** Use SLF4J
- **Declaration:** `private static final Logger logger = LoggerFactory.getLogger(YourClass.class);`
- **Placeholders:** Use `logger.info("User {} matched", id);` instead of string concatenation
- **Levels:** Use appropriate log levels (info, warn, debug, error)

### Code Organization Patterns
**Service Structure:**
```java
public class ServiceName {
  // Immutable dependencies (final)
  private final DependencyA dependencyA;
  private final DependencyB dependencyB;

  // Constructor with all dependencies
  public ServiceName(DependencyA a, DependencyB b) {
    this.dependencyA = Objects.requireNonNull(a);
    this.dependencyB = Objects.requireNonNull(b);
  }

  // Public business logic methods
  public ResultType publicMethod(InputType input) {
    // Implementation
  }

  // Private helper methods
  private ResultType privateHelper() {
    // Implementation
  }
}
```

### Immutability Patterns
**Records (for immutable data):**
```java
public record Like(
    UUID id, UUID whoLikes, UUID whoGotLiked,
    Direction direction, Instant createdAt) {

  // Compact constructor for validation
  public Like {
    Objects.requireNonNull(id, "id cannot be null");
    if (whoLikes.equals(whoGotLiked)) {
      throw new IllegalArgumentException("Cannot like yourself");
    }
  }

  // Factory method
  public static Like create(UUID whoLikes, UUID whoGotLiked, Direction direction) {
    return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, Instant.now());
  }
}
```

**Mutable Classes (for entities with state):**
```java
public class Match {
  private final String id;      // Immutable
  private final UUID userA;     // Immutable
  private State state;          // Mutable with state machine

  // State transition with validation
  public void unmatch(UUID userId) {
    if (this.state != State.ACTIVE) {
      throw new IllegalStateException("Match is not active");
    }
    if (!involves(userId)) {
      throw new IllegalArgumentException("User is not part of this match");
    }
    this.state = State.UNMATCHED;
    this.endedAt = Instant.now();
    this.endedBy = userId;
  }
}
```

### Error Handling
**Validation in Constructors:**
```java
public record Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
  public Block {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(blockerId, "blockerId cannot be null");
    Objects.requireNonNull(blockedId, "blockedId cannot be null");
    if (blockerId.equals(blockedId)) {
      throw new IllegalArgumentException("Cannot block yourself");
    }
  }
}
```

**Storage Exception Wrapping:**
```java
public void save(Like like) {
  try (Connection conn = dbManager.getConnection();
      PreparedStatement stmt = conn.prepareStatement(sql)) {
    // SQL operations
  } catch (SQLException e) {
    throw new StorageException("Failed to save like: " + like.id(), e);
  }
}
```

### Performance Optimization
**Monitoring:** Built-in performance monitoring for critical operations
**Efficiency:** Use efficient algorithms and minimize database queries
**Caching:** Consider caching for frequently accessed data
**Pagination:** Implement pagination for large datasets

## Templates

### New Service Implementation
```java
public class MyNewService {
    private final UserStorage userStorage;

    public MyNewService(UserStorage userStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
    }
}
```

### New Storage Method
```java
public Optional<User> get(UUID id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setObject(1, id);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Optional.of(mapRow(rs));
        }
    } catch (SQLException e) {
        throw new StorageException("Lookup failed", e);
    }
    return Optional.empty();
}
```

## Common Workflows

### Adding a New Feature
1. Plan: Create feature design doc in `docs/plans/phase-X-features/`
2. Core Layer: Add domain models, services, storage interfaces
3. Storage Layer: Implement H2*Storage classes
4. CLI Layer: Add handler methods or new handler
5. UI Layer: Add controller, viewmodel, and FXML if needed
6. API Layer: Add REST endpoints if needed
7. Wire Service: Register the new service in `StorageFactory` for proper dependency injection
8. Tests: Write unit tests (core/) and integration tests (storage/)
9. Format: Run `mvn spotless:apply`
10. Verify: Run `mvn clean verify`
11. Document: Update CLAUDE.md and architecture.md

### Adding a New Domain Model
**Immutable (Record):**
```java
package datingapp.core;

public record NewModel(UUID id, String field, Instant createdAt) {
  public NewModel {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(field, "field cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
  }

  public static NewModel create(String field) {
    return new NewModel(UUID.randomUUID(), field, Instant.now());
  }
}
```

**Mutable (Class with State):**
```java
package datingapp.core;

public class NewEntity {
  private final UUID id;
  private final Instant createdAt;
  private Instant updatedAt;
  private String field;
  private State state;

  public enum State { PENDING, ACTIVE, INACTIVE }

  private NewEntity(UUID id, Instant createdAt) {
    this.id = id;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
    this.state = State.PENDING;
  }

  public static NewEntity create(String field) {
    NewEntity entity = new NewEntity(UUID.randomUUID(), Instant.now());
    entity.field = field;
    return entity;
  }

  public void setField(String field) {
    this.field = Objects.requireNonNull(field);
    touch();
  }

  public void activate() {
    if (state != State.PENDING) {
      throw new IllegalStateException("Cannot activate from state: " + state);
    }
    this.state = State.ACTIVE;
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  // Getters with defensive copying for collections
}
```

### Adding a New Service
```java
package datingapp.core;

public class NewService {
  private final Dependency1 dep1;
  private final Dependency2 dep2;

  public NewService(Dependency1 dep1, Dependency2 dep2) {
    this.dep1 = Objects.requireNonNull(dep1);
    this.dep2 = Objects.requireNonNull(dep2);
  }

  public ResultType businessMethod(InputType input) {
    // Implementation with validation
    Objects.requireNonNull(input, "input cannot be null");

    // Business logic

    return result;
  }
}
```

### Adding a New Storage Interface
```java
package datingapp.core;

public interface NewStorage {
  void save(NewModel model);
  Optional<NewModel> get(UUID id);
  List<NewModel> findAll();
}
```

### Implementing Storage
```java
package datingapp.storage;

public class H2NewStorage implements NewStorage {
  private final DatabaseManager dbManager;

  public H2NewStorage(DatabaseManager dbManager) {
    this.dbManager = Objects.requireNonNull(dbManager);
  }

  @Override
  public void save(NewModel model) {
    String sql = "MERGE INTO new_table (id, field, created_at) VALUES (?, ?, ?)";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, model.id());
      stmt.setString(2, model.field());
      stmt.setTimestamp(3, Timestamp.from(model.createdAt()));
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new StorageException("Failed to save NewModel: " + model.id(), e);
    }
  }

  // Implement other methods
}
```

### Adding Configuration Parameter
1. Add parameter to `AppConfig` record with validation
2. Update `AppConfig.defaults()` with default value
3. Add to `AppConfig.Builder` with setter method
4. Update JSON configuration loading in `ConfigLoader`
5. Update environment variable loading in `ConfigLoader`
6. Use parameter in relevant services

### Adding Soft-Delete Support
1. Add `deleted_at` column to relevant tables
2. Update storage interfaces and implementations to handle soft deletes
3. Add methods to check if entity is deleted
4. Update queries to exclude soft-deleted records
5. Implement cleanup service to periodically purge old records

## Critical Rules & Guardrails

### ❌ NEVER DO THIS
1. Import frameworks/databases in `core/`
2. Skip constructor validation
3. Throw exceptions in CLI layer
4. Use implementation in service
5. Forget to format before committing
6. Create mutable records
7. Skip state validation in transitions
8. Return direct collection references
9. Use Mockito or external mocking
10. Update documentation without timestamp
11. Use raw JDBC directly - always use JDBI
12. Store services as static variables outside ServiceRegistry
13. Bypass validation in storage layer
14. Expose mutable internal state
15. Ignore soft-delete status in queries
16. Hardcode configuration values outside AppConfig
17. Skip null checks in public APIs
18. Use magic numbers in business logic
19. Create circular dependencies between services
20. Bypass the service registry for dependency access
21. Use deprecated ServiceRegistryBuilder instead of StorageFactory
22. Access AppSession directly without proper initialization

### ✅ ALWAYS DO THIS
1. Run `mvn spotless:apply` before committing
2. Validate all constructor parameters with `Objects.requireNonNull()`
3. Use defensive copying when returning collections
4. Add `@DisplayName` to test methods
5. Update `updatedAt` timestamp on entity changes (touch pattern)
6. Write complete, compilable code in documentation (not pseudocode)
7. Group related tests with `@Nested` classes
8. Use factory methods for object creation (`create()`, `of()`, `fromDatabase()`)
9. Check state before state transitions
10. Update QWEN.md "Recent Updates" section with changes
11. Use JDBI SQL Object pattern for database operations
12. Access all services through ServiceRegistry
13. Handle soft-delete status consistently across all layers
14. Use external configuration for all configurable values
15. Implement proper logging with appropriate log levels
16. Follow naming conventions consistently
17. Use enums for fixed sets of values
18. Implement proper error handling and exception wrapping
19. Use records for immutable data structures
20. Apply the single responsibility principle to all classes
21. Use StorageFactory for service initialization instead of ServiceRegistryBuilder
22. Initialize AppSession properly through AppBootstrap

## Final Checklist Before Completion
Before considering a task complete, verify:
1. Did I run `mvn spotless:apply`?
2. Did I wire the service in `StorageFactory` (not deprecated ServiceRegistryBuilder)?
3. Did I add a `@Nested` test class for this feature?
4. Did I use `Optional` for null safety?
5. Did I properly initialize `AppSession` through `AppBootstrap`?
6. Did I update documentation to reflect nested enums in domain models?

## Current Status (Phase 4)
- **Completed**: Basic matching, daily limits, undo, interests, achievements, messaging, relationship transitions, verification, pace preferences, comprehensive stats, social features, standout recommendations, soft-delete support, external configuration
- **In Development**: Advanced UI features, notification delivery mechanisms, performance optimizations
- **Architecture**: Clean, well-tested, extensible with JDBI integration and external configuration support

## Known Limitations
- No database transactions (undo deletions not atomic)
- Identity: Currently lacks a password/auth layer (anyone can select any user)
- No proactive notifications delivery (UI alerts/email)
- Media: Photo support uses string URLs; no actual image processing yet
- Location: Geolocation is manual (users enter lat/lon coordinates)
- JavaFX UI is experimental and not fully integrated
- JDBI integration: Some complex queries may require custom solutions
- Configuration: External configuration supports JSON and environment variables but could be extended to other formats

## Key Files to Know
- `CLAUDE.md` - Comprehensive project documentation for and from Claude-Code.
- `AGENTS.md` - AI agent development guidelines for and from OpenCode+supporting agents.
- `GEMINI.md` - AI agent development guidelines for and from Gemini-CLI.
- `QWEN.md` - AI agent development guidelines for and from Qwen-Code (this file).
- `docs/architecture.md` - Visual architecture diagrams
- `pom.xml` - Build configuration
- `Main.java` - CLI application entry point
- `datingapp.ui.DatingApp` - JavaFX UI application entry point
- `datingapp.app.api.RestApiServer` - REST API server implementation
- `src/main/resources/logback.xml` - Logging configuration
- `datingapp.app.AppBootstrap` - Centralized application initialization
- `datingapp.app.ConfigLoader` - Configuration loading from JSON and environment variables
- `datingapp.core.AppSession` - Unified session management for current user state
- `datingapp.storage.StorageFactory` - Factory for wiring all services and storage implementations

## Recent Additions (Phase 4)
- **JDBI Integration**: Migration from raw JDBC to JDBI 3 for type-safe database access
- **Service Registry Consolidation**: Unified service creation using StorageFactory instead of ServiceRegistryBuilder
- **Messaging System**: Full conversation and message management
- **Relationship Transitions**: Friend zone requests and graceful exit
- **Social Features**: Friend requests and notification system
- **Enhanced User Profiles**: Verification system and pace preferences
- **Interests System**: 37 predefined interests across 6 categories with matching algorithm
- **Achievement System**: 11 gamification achievements across 4 categories
- **Advanced Matching**: Multi-factor compatibility scoring with configurable weights
- **Comprehensive Stats**: User and platform statistics with snapshot system
- **Profile Management**: Notes, views, and privacy controls
- **Safety & Moderation**: Reporting, blocking, and verification systems
- **Standout Recommendations**: Personalized standout recommendations based on compatibility
- **Soft-Delete Support**: Configurable soft-delete with retention policies
- **External Configuration**: JSON file and environment variable configuration support
- **Performance Monitoring**: Built-in performance monitoring capabilities
- **Migration System**: Automated schema migration for backward compatibility
- **REST API**: Full-featured REST API server implementation
- **Unified Session Management**: AppSession singleton for consistent user state across interfaces
- **Centralized Bootstrapping**: AppBootstrap for unified application initialization

This project serves as an excellent example of clean architecture principles applied to a real-world application with comprehensive testing and quality controls.





## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
---AGENT-LOG-END---
