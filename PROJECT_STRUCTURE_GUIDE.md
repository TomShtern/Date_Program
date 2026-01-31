# Dating App - Complete Project Structure Guide

> **Purpose**: This document provides a comprehensive overview of every source file in the Dating App codebase. For each file, you'll find what it is, what it does, how it works, why it exists, and why it's in its own file.

---

## Table of Contents

1. [Entry Points](#entry-points)
2. [Core Domain Models (`core/`)](#core-domain-models)
3. [Core Services (`core/`)](#core-services)
4. [Storage Interfaces (`core/storage/`)](#storage-interfaces)
5. [Storage Implementations (`storage/`)](#storage-implementations)
6. [JDBI Declarative Storage (`storage/jdbi/`)](#jdbi-declarative-storage)
7. [Row Mappers (`storage/mapper/`)](#row-mappers)
8. [CLI Handlers (`app/cli/`)](#cli-handlers)
9. [JavaFX UI (`ui/`)](#javafx-ui)
10. [UI Controllers (`ui/controller/`)](#ui-controllers)
11. [UI ViewModels (`ui/viewmodel/`)](#ui-viewmodels)
12. [UI Utilities (`ui/util/`)](#ui-utilities)
13. [UI Components (`ui/component/`)](#ui-components)
14. [Resources](#resources)
15. [Test Files](#test-files)

---

## Entry Points

### `Main.java`
- **What it is**: The console application entry point and main menu loop.
- **What it does**: Bootstraps the database, wires up all services via `ServiceRegistry`, initializes CLI handlers, and runs an interactive menu for user actions (create profile, browse candidates, message, etc.).
- **How it works**: Uses a `Scanner` for input, delegates each menu option to specialized handlers (ProfileHandler, MatchingHandler, etc.), and displays session/daily status in the menu header.
- **Why we need it**: The app requires a single entry point that orchestrates startup, service wiring, and the main interaction loop.
- **Why it's separate**: Clean separation of application bootstrapping from business logic; allows switching to a different UI (e.g., JavaFX) without modifying core logic.

---

## Core Domain Models

> Located in `src/main/java/datingapp/core/` — These files contain **pure Java business logic** with ZERO framework/database imports.

### `User.java`
- **What it is**: The central mutable entity representing a dating app user.
- **What it does**: Stores all user profile data (name, bio, birthDate, gender, interestedIn, location, photos, interests, lifestyle choices, dealbreakers, pace preferences, verification status). Manages state transitions (INCOMPLETE → ACTIVE ↔ PAUSED → BANNED).
- **How it works**: Uses private fields with getters/setters that call `touch()` to update `updatedAt`. Includes a `StorageBuilder` for reconstructing from database. Contains a nested `ProfileNote` record for private notes about other users.
- **Why we need it**: Every dating app needs a user entity to store profile data and manage account lifecycle.
- **Why it's separate**: Users are the core entity; having a dedicated file makes the domain model clear and maintainable.

### `Match.java`
- **What it is**: A mutable entity representing a mutual match between two users.
- **What it does**: Tracks match state (ACTIVE, FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED), who ended it, when, and why. Generates deterministic IDs from sorted user UUIDs.
- **How it works**: `Match.create(a, b)` sorts UUIDs lexicographically to ensure consistent ID generation. State transitions are enforced via validation methods.
- **Why we need it**: Matches are the core relationship in a dating app; tracking state enables features like Friend Zone and Graceful Exit.
- **Why it's separate**: Matches have complex lifecycle logic (state machine) that warrants dedicated encapsulation.

### `Messaging.java`
- **What it is**: Container class grouping messaging domain models.
- **What it does**: Contains `Message` (immutable, max 1000 chars) and `Conversation` (mutable, tracks per-user read timestamps, archive status, visibility).
- **How it works**: `Conversation` uses the same deterministic ID pattern as `Match`. Messages are validated on construction.
- **Why we need it**: Messaging is a core dating app feature requiring message and conversation tracking.
- **Why it's separate**: Groups related messaging concepts together while keeping the codebase organized.

### `Social.java`
- **What it is**: Container class for social interaction models.
- **What it does**: Contains `FriendRequest` (for Friend Zone transitions, status: PENDING/ACCEPTED/DECLINED/EXPIRED) and `Notification` (system notifications with type, title, message, metadata).
- **How it works**: Both are immutable records with factory methods and validation.
- **Why we need it**: Social features (friend requests, notifications) are needed for relationship transitions and user engagement.
- **Why it's separate**: Groups social concepts together; keeps notification logic separate from core matching.

### `Stats.java`
- **What it is**: Container class for statistics domain models.
- **What it does**: Contains `UserStats` (immutable snapshot of user engagement: likes given/received, matches, blocks, reports, derived scores) and `PlatformStats` (platform-wide averages for computing relative scores).
- **How it works**: `UserStats.StatsBuilder` accumulates metrics during computation, then `UserStats.create()` produces an immutable snapshot.
- **Why we need it**: Analytics drive gamification, insights, and moderation (e.g., detecting unusual behavior).
- **Why it's separate**: Statistics are a distinct concern from core matching; keeping them separate enables independent evolution.

### `Preferences.java`
- **What it is**: Container class for user preferences and lifestyle enums.
- **What it does**: Contains the `Interest` enum (39 interests in 6 categories), `Lifestyle` inner class with enums (Smoking, Drinking, WantsKids, LookingFor, Education), and `PacePreferences` record (messaging frequency, time to first date, communication style, depth preference).
- **How it works**: Interests have categories and display names; `PacePreferences` validates all-or-nothing completeness.
- **Why we need it**: Matching algorithms use interests and lifestyle to compute compatibility; pace preferences ensure communication style alignment.
- **Why it's separate**: Preferences are used across multiple services (matching, quality, display); centralizing them prevents duplication.

### `UserInteractions.java`
- **What it is**: Container class for user interaction records.
- **What it does**: Contains `Like` (immutable record for like/pass actions), `Block` (immutable record for blocking), and `Report` (immutable record with reason and optional description).
- **How it works**: All are records with factory methods (`create()`) that generate UUIDs and timestamps.
- **Why we need it**: Tracking likes, blocks, and reports is fundamental to matching, safety, and moderation.
- **Why it's separate**: Groups interaction types together; enables consistent patterns across all interaction records.

### `Achievement.java`
- **What it is**: Enum defining all gamification achievements.
- **What it does**: Defines 11 achievements across 4 categories (Matching, Behavior, Profile, Safety) with display names, descriptions, icons, and thresholds. Contains nested `UserAchievement` record for unlocked achievements.
- **How it works**: Each achievement has metadata; `AchievementService` evaluates user progress against thresholds.
- **Why we need it**: Gamification increases engagement and encourages profile completion.
- **Why it's separate**: Achievements are a distinct feature; keeping them separate makes it easy to add/modify achievements.

### `Dealbreakers.java`
- **What it is**: Immutable record representing a user's hard filters.
- **What it does**: Stores acceptable values for lifestyle fields (smoking, drinking, kids, relationship goals, education), height range, and max age difference. Contains nested `Evaluator` class with static methods to check if a candidate passes dealbreakers.
- **How it works**: Empty sets mean "no preference." `Evaluator.passes(seeker, candidate)` runs all checks; missing candidate fields fail dealbreakers by default.
- **Why we need it**: Dealbreakers are one-way filters that exclude incompatible candidates from matching.
- **Why it's separate**: Dealbreakers have complex evaluation logic; separating them keeps `CandidateFinder` focused on orchestration.

### `SwipeSession.java`
- **What it is**: Mutable entity tracking a user's swiping session.
- **What it does**: Records session start/end times, swipe counts (likes, passes, matches), like ratio, velocity (swipes per minute), and state (ACTIVE/COMPLETED).
- **How it works**: `recordSwipe()` updates counters and `lastActivityAt`. `isTimedOut()` checks inactivity against a configurable timeout.
- **Why we need it**: Session tracking enables anti-bot detection, session-based analytics, and swipe velocity warnings.
- **Why it's separate**: Sessions have distinct lifecycle management from individual swipes.

### `AppConfig.java`
- **What it is**: Centralized, immutable application configuration.
- **What it does**: Stores all configurable values: limits (daily likes, passes, swipes per session), timeouts (undo window, session timeout), algorithm thresholds (nearby distance, similar age), validation bounds (min/max age, height), and match quality weights.
- **How it works**: Java record with validation in compact constructor; `defaults()` provides sensible defaults; `Builder` enables custom configurations.
- **Why we need it**: Centralizing configuration enables easy tuning, different profiles (dev/prod), and testing with different thresholds.
- **Why it's separate**: Configuration is orthogonal to business logic; keeping it separate simplifies modification.

---

## Core Services

> Located in `src/main/java/datingapp/core/` — Business logic services that depend on storage interfaces.

### `ServiceRegistry.java`
- **What it is**: Central dependency injection container (composition root).
- **What it does**: Holds all storage and service instances; provides getters for each. `Builder.buildH2()` wires everything together for H2 database.
- **How it works**: Constructor takes all dependencies; `Builder` instantiates JDBI storage implementations and creates all services with their dependencies.
- **Why we need it**: Enables testability (mock implementations), backend swapping (H2 → PostgreSQL), and adding services without modifying Main.
- **Why it's separate**: **EXCEPTION to the "no imports in core" rule** — this is the composition root that wires storage implementations to core interfaces.

### `CandidateFinder.java`
- **What it is**: Stateless algorithm for finding matching candidates.
- **What it does**: Filters a list of users through 7 stages: not self, ACTIVE state, no prior interaction, mutual gender preferences, mutual age preferences, within distance, passes dealbreakers. Sorts by distance.
- **How it works**: Pure function `(User, List<User>, Set<UUID>) → List<User>`. Contains nested `GeoUtils` class with Haversine distance calculation.
- **Why we need it**: Candidate discovery is the core matching algorithm.
- **Why it's separate**: Stateless algorithm with no storage dependencies; enables easy algorithm swapping (e.g., ML-based ranking).

### `MatchingService.java`
- **What it is**: Business logic for processing likes and creating matches.
- **What it does**: `recordLike()` saves likes, checks for mutual likes, creates matches if mutual, and updates session tracking. Contains `LikerBrowser` functionality (`findPendingLikers()`) for "Who Liked Me" feature.
- **How it works**: Checks for existing likes, saves new like, checks mutual, creates `Match` with deterministic ID.
- **Why we need it**: Core matching workflow requires coordinating likes, matches, and sessions.
- **Why it's separate**: Matching is a distinct business workflow from candidate discovery.

### `ValidationService.java`
- **What it is**: Centralized input validation service.
- **What it does**: Validates user input (name, age, height, distance, bio, age range, coordinates) and returns `ValidationResult` with success/failure and error messages.
- **How it works**: Stateless methods check values against `AppConfig` thresholds; returns structured results.
- **Why we need it**: Consistent validation across CLI and UI with user-friendly error messages.
- **Why it's separate**: Validation logic is reusable across handlers; centralizing prevents duplication.

### `DailyService.java`
- **What it is**: Consolidated daily limits and daily pick service.
- **What it does**: Tracks daily like/pass usage, checks limits (`canLike()`, `canPass()`), provides status (`getStatus()`), and generates daily picks (`getDailyPick()`).
- **How it works**: Counts today's likes/passes against configured limits. Daily pick uses deterministic random selection based on date + user ID.
- **Why we need it**: Daily limits prevent spamming; daily picks add engagement through curated suggestions.
- **Why it's separate**: Daily features are a distinct engagement mechanism from core matching.

### `UndoService.java`
- **What it is**: Service for managing undo state and executing undo operations.
- **What it does**: Tracks last swipe per user in-memory with expiring window (default 30s). `undo()` deletes the Like and any resulting Match.
- **How it works**: `recordSwipe()` stores undo state with expiry. `canUndo()` checks expiry. `undo()` deletes from storage.
- **Why we need it**: Undo provides a safety net for accidental swipes.
- **Why it's separate**: Undo has specific lifecycle management and in-memory state.

### `SessionService.java`
- **What it is**: Service for managing swipe sessions.
- **What it does**: Creates/retrieves sessions (`getOrCreateSession()`), records swipes with anti-bot checks, ends sessions, provides session history and aggregates.
- **How it works**: Per-user locks prevent race conditions. Checks session timeout and swipe limits.
- **Why we need it**: Session tracking enables analytics and anti-bot protection.
- **Why it's separate**: Session lifecycle is distinct from individual swipe processing.

### `MatchQualityService.java`
- **What it is**: Service for computing match compatibility scores.
- **What it does**: Computes quality metrics for a match: distance, age, interest, lifestyle, pace, and response time scores. Generates human-readable highlights.
- **How it works**: Individual scores (0.0-1.0) are weighted and summed to produce 0-100 compatibility score. Contains nested `InterestMatcher` utility for interest comparison and `MatchQuality` record for results.
- **Why we need it**: Match quality helps users understand compatibility and prioritize matches.
- **Why it's separate**: Quality computation is complex enough to warrant dedicated service.

### `AchievementService.java`
- **What it is**: Service for checking and unlocking achievements.
- **What it does**: `checkAndUnlock()` evaluates all achievement criteria and unlocks new ones. Provides progress tracking via `AchievementProgress` record.
- **How it works**: Iterates through `Achievement` enum, checks if criteria are met (e.g., match count, profile completeness), saves unlocked achievements.
- **Why we need it**: Gamification drives engagement.
- **Why it's separate**: Achievement logic is complex and touches multiple data sources.

### `MessagingService.java`
- **What it is**: Service for messaging between matched users.
- **What it does**: Sends messages (`sendMessage()` with authorization), retrieves messages with pagination, manages conversations, tracks unread counts.
- **How it works**: Validates sender/recipient/match status, creates/updates conversations, saves messages, updates read timestamps.
- **Why we need it**: Messaging is essential for user interaction post-match.
- **Why it's separate**: Messaging has distinct authorization and conversation management logic.

### `RelationshipTransitionService.java`
- **What it is**: Service for relationship lifecycle transitions.
- **What it does**: Handles Friend Zone requests (`requestFriendZone()`, `acceptFriendZone()`, `declineFriendZone()`) and Graceful Exit (`gracefulExit()`).
- **How it works**: Updates match state, creates friend requests/notifications, archives conversations on exit.
- **Why we need it**: Relationship transitions enable graceful endings and friendship pivots.
- **Why it's separate**: Transition logic coordinates matches, conversations, and notifications.

### `TrustSafetyService.java`
- **What it is**: Consolidated safety and verification service.
- **What it does**: Generates verification codes, validates codes, processes reports (with auto-ban threshold), manages blocks/unblocks.
- **How it works**: Reports trigger automatic blocking; reaching report threshold triggers auto-ban.
- **Why we need it**: User safety requires reporting, blocking, and verification.
- **Why it's separate**: Safety features are distinct from core matching.

### `StatsService.java`
- **What it is**: Service for computing and managing user/platform statistics.
- **What it does**: Computes stats snapshots (`computeAndSaveStats()`), computes platform averages, caches stats for 24 hours.
- **How it works**: Aggregates data from likes, matches, blocks, reports storage into `UserStats` and `PlatformStats`.
- **Why we need it**: Analytics drive insights and relative scoring.
- **Why it's separate**: Stats computation is a distinct concern from core workflows.

### `ProfilePreviewService.java`
- **What it is**: Service for generating profile previews and completeness scores.
- **What it does**: Calculates profile completeness (percentage with filled/missing fields), generates improvement tips.
- **How it works**: Checks each field (name, bio, photos, interests, lifestyle) and produces `ProfileCompleteness` and `ProfilePreview` records.
- **Why we need it**: Profile previews help users see how they appear to others; tips drive completion.
- **Why it's separate**: Preview logic is presentation-focused but reusable across CLI and UI.

---

## Storage Interfaces

> Located in `src/main/java/datingapp/core/storage/` — Pure interfaces defined in core, implemented in storage layer.

### `UserStorage.java`
- **What it is**: Interface for User CRUD operations.
- **What it does**: `save()`, `get()`, `findActive()`, `findAll()`, `delete()`.
- **Why it's separate**: Abstracts persistence; enables swapping implementations (H2, PostgreSQL, in-memory).

### `LikeStorage.java`
- **What it is**: Interface for Like persistence and queries.
- **What it does**: Save/check likes, count by direction, check mutual likes, get likes for date range, delete for undo.
- **Why it's separate**: Likes have specific query patterns (mutual check, daily counts).

### `MatchStorage.java`
- **What it is**: Interface for Match persistence.
- **What it does**: Save/update/get matches, get active/all matches for user, delete for undo.
- **Why it's separate**: Matches have state updates and multi-user queries.

### `BlockStorage.java`
- **What it is**: Interface for Block persistence.
- **What it does**: Save/delete blocks, check if blocked, get blocked user IDs, count blocks given/received.
- **Why it's separate**: Block queries are bidirectional (affects both users).

### `ReportStorage.java`
- **What it is**: Interface for Report persistence.
- **What it does**: Save reports, check if already reported, count reports against a user.
- **Why it's separate**: Reports need unique constraints and counting for auto-ban.

### `SwipeSessionStorage.java`
- **What it is**: Interface for SwipeSession persistence.
- **What it does**: Save sessions, get active session, get session history, end stale sessions, get aggregates.
- **Why it's separate**: Sessions have specific lifecycle queries (active, history, cleanup).

### `StatsStorage.java`
- **What it is**: Interface for UserStats and PlatformStats persistence.
- **What it does**: Save/get latest user stats, save/get platform stats, get all latest stats.
- **Why it's separate**: Stats are time-series snapshots with "latest" queries.

### `DailyPickStorage.java`
- **What it is**: Interface for tracking daily pick views.
- **What it does**: Mark viewed, check if viewed for a date.
- **Why it's separate**: Simple tracking separate from user/like storage.

### `UserAchievementStorage.java`
- **What it is**: Interface for UserAchievement persistence.
- **What it does**: Save, get unlocked achievements, check if has achievement, count unlocked.
- **Why it's separate**: Achievements have unique constraint (user + achievement).

### `ProfileViewStorage.java`
- **What it is**: Interface for profile view tracking.
- **What it does**: Record view, count views, get recent viewers.
- **Why it's separate**: Analytics for "who viewed my profile."

### `ProfileNoteStorage.java`
- **What it is**: Interface for ProfileNote persistence.
- **What it does**: Save/get/delete notes by author+subject, get all notes by author.
- **Why it's separate**: Private notes have composite key.

### `MessagingStorage.java`
- **What it is**: Interface for Conversation and Message persistence.
- **What it does**: Save/get conversations, save/get messages, update timestamps, archive conversations, count unread.
- **Why it's separate**: Messaging has complex queries (pagination, unread counts).

### `SocialStorage.java`
- **What it is**: Interface for FriendRequest and Notification persistence.
- **What it does**: Save/get/update friend requests, check pending, save/get notifications.
- **Why it's separate**: Social features are distinct from core matching.

---

## Storage Implementations

### `storage/DatabaseManager.java`
- **What it is**: H2 database connection manager and schema initializer.
- **What it does**: Manages connection pooling, initializes schema (all tables, indexes, foreign keys), handles migrations, provides shutdown.
- **How it works**: Singleton pattern; lazy schema initialization on first connection. Creates ~15 tables with proper FK constraints.
- **Why we need it**: Centralized database setup for consistent schema across all storage implementations.
- **Why it's separate**: Database concerns are infrastructure, not business logic.

### `storage/StorageException.java`
- **What it is**: Unchecked exception for storage layer errors.
- **What it does**: Wraps SQLExceptions with meaningful messages.
- **Why it's separate**: Allows storage errors to propagate without checked exception handling everywhere.

---

## JDBI Declarative Storage

> Located in `src/main/java/datingapp/storage/jdbi/` — Declarative SQL interfaces using JDBI annotations.

### `JdbiUserStorage.java` + `JdbiUserStorageAdapter.java`
- **What they are**: JDBI interface for user CRUD and an adapter for complex User reconstruction.
- **How it works**: Interface uses `@SqlQuery`/`@SqlUpdate` annotations. Adapter handles complex `User` object reconstruction from ResultSet using `StorageBuilder`.
- **Why two files**: User has many fields; adapter provides manual mapping that JDBI can't auto-generate.

### `JdbiLikeStorage.java`
- **What it is**: JDBI interface implementing LikeStorage.
- **What it does**: Declarative SQL for all like operations.
- **Why it's separate**: Each storage interface gets its own JDBI implementation.

### `JdbiMatchStorage.java`
- **What it is**: JDBI interface implementing MatchStorage.
- **What it does**: Declarative SQL with row mapper for Match reconstruction.

### `JdbiBlockStorage.java`
- **What it is**: JDBI interface implementing BlockStorage.

### `JdbiReportStorage.java`
- **What it is**: JDBI interface implementing ReportStorage.

### `JdbiSwipeSessionStorage.java`
- **What it is**: JDBI interface implementing SwipeSessionStorage.
- **What it does**: Includes aggregate queries and stale session cleanup.

### `JdbiStatsStorage.java`
- **What it is**: JDBI interface implementing StatsStorage.

### `JdbiDailyPickStorage.java`
- **What it is**: JDBI interface implementing DailyPickStorage.

### `JdbiUserAchievementStorage.java`
- **What it is**: JDBI interface implementing UserAchievementStorage.

### `JdbiProfileViewStorage.java`
- **What it is**: JDBI interface implementing ProfileViewStorage.

### `JdbiProfileNoteStorage.java`
- **What it is**: JDBI interface implementing ProfileNoteStorage.

### `JdbiMessagingStorage.java`
- **What it is**: JDBI interface implementing MessagingStorage.
- **What it does**: Complex queries for conversations, messages, unread counts.

### `JdbiSocialStorage.java`
- **What it is**: JDBI interface implementing SocialStorage.

### `EnumSetArgumentFactory.java`
- **What it is**: JDBI argument factory for serializing `EnumSet<Interest>` to CSV.
- **Why it's separate**: Custom type handling requires dedicated factory.

### `EnumSetColumnMapper.java`
- **What it is**: JDBI column mapper for deserializing CSV back to `EnumSet<Interest>`.

### `UserBindingHelper.java`
- **What it is**: Helper for binding complex User fields to JDBI queries.
- **Why it's separate**: User has many fields; helper centralizes binding logic.

---

## Row Mappers

> Located in `src/main/java/datingapp/storage/mapper/`

### `MapperHelper.java`
- **What it is**: Utility class for null-safe ResultSet reading.
- **What it does**: Provides methods like `readUuid()`, `readInstant()`, `readEnum()` that handle null values.
- **Why it's separate**: Reusable across all row mappers; prevents null-handling duplication.

---

## CLI Handlers

> Located in `src/main/java/datingapp/app/cli/` — Command-line interface handlers.

### `CliUtilities.java`
- **What it is**: Container for CLI utility classes.
- **What it does**: Contains `InputReader` (prompt + read with Scanner) and `UserSession` (tracks logged-in user).
- **Why it's separate**: Common CLI infrastructure used by all handlers.

### `CliConstants.java`
- **What it is**: Constants for CLI messages and formatting.
- **What it does**: Stores repeated strings (separator lines, error messages).
- **Why it's separate**: Centralizes CLI strings for consistency.

### `EnumMenu.java`
- **What it is**: Generic utility for displaying enum selection menus.
- **What it does**: Shows numbered list of enum values, reads selection.
- **Why it's separate**: Reusable for any enum (Gender, Smoking, Interest, etc.).

### `ProfileHandler.java`
- **What it is**: CLI handler for profile management.
- **What it does**: Create user, select user, complete profile, set dealbreakers, preview profile, view profile score.
- **Why it's separate**: Profile operations are a cohesive feature set.

### `MatchingHandler.java`
- **What it is**: CLI handler for matching and browsing.
- **What it does**: Browse candidates (with like/pass/view), view matches, handle undo.
- **Why it's separate**: Matching is the core user workflow.

### `MessagingHandler.java`
- **What it is**: CLI handler for messaging.
- **What it does**: Show conversations, view messages, send messages.
- **Why it's separate**: Messaging has distinct interaction flow.

### `SafetyHandler.java`
- **What it is**: CLI handler for safety features.
- **What it does**: Block/unblock users, report users, verify profile.
- **Why it's separate**: Safety is a distinct concern.

### `StatsHandler.java`
- **What it is**: CLI handler for statistics and achievements.
- **What it does**: View statistics, view achievements.
- **Why it's separate**: Analytics is a distinct feature.

### `ProfileNotesHandler.java`
- **What it is**: CLI handler for profile notes.
- **What it does**: View/add/edit/delete notes about other users.
- **Why it's separate**: Notes are a distinct private feature.

### `LikerBrowserHandler.java`
- **What it is**: CLI handler for "Who Liked Me" feature.
- **What it does**: Browse pending likers, like back or pass.
- **Why it's separate**: Distinct workflow from candidate browsing.

### `RelationshipHandler.java`
- **What it is**: CLI handler for relationship transitions.
- **What it does**: View notifications, view/respond to friend requests, initiate graceful exit.
- **Why it's separate**: Relationship transitions are a distinct feature.

---

## JavaFX UI

> Located in `src/main/java/datingapp/ui/`

### `DatingApp.java`
- **What it is**: JavaFX Application entry point.
- **What it does**: Initializes services (same as Main.java), sets up primary stage, navigates to login screen.
- **Why it's separate**: GUI requires JavaFX Application subclass.

### `NavigationService.java`
- **What it is**: Singleton service for managing view navigation.
- **What it does**: Loads FXML views, injects controllers, manages view stack, handles back navigation.
- **Why it's separate**: Navigation logic is complex and used by all controllers.

### `ViewModelFactory.java`
- **What it is**: Factory for creating view models with their dependencies.
- **What it does**: Creates ViewModels with injected services from ServiceRegistry.
- **Why it's separate**: Centralizes ViewModel creation and dependency injection.

---

## UI Controllers

> Located in `src/main/java/datingapp/ui/controller/`

### `BaseController.java`
- **What it is**: Abstract base class for all controllers.
- **What it does**: Provides subscription lifecycle management (`addSubscription()`, cleanup on dispose), common utilities.
- **Why it's separate**: Prevents memory leaks; provides consistent controller lifecycle.

### `LoginController.java`
- **What it is**: Controller for the login/user selection screen.
- **What it does**: Displays user list with search, handles user selection, creates new accounts.
- **Why it's separate**: Login is a distinct screen.

### `DashboardController.java`
- **What it is**: Controller for the main dashboard.
- **What it does**: Shows user summary, navigation buttons to other screens.
- **Why it's separate**: Dashboard is the main hub screen.

### `MatchingController.java`
- **What it is**: Controller for the swiping screen.
- **What it does**: Displays candidates, handles like/pass/undo with keyboard shortcuts and animations.
- **Why it's separate**: Swiping is the core interaction screen.

### `MatchesController.java`
- **What it is**: Controller for viewing matches.
- **What it does**: Displays tabs for matches, received likes, sent likes.
- **Why it's separate**: Match management is a distinct screen.

### `ProfileController.java`
- **What it is**: Controller for profile editing.
- **What it does**: Edit profile fields, upload photos, manage dealbreakers.
- **Why it's separate**: Profile editing is a distinct screen.

### `PreferencesController.java`
- **What it is**: Controller for preferences and settings.
- **What it does**: Edit matching preferences, toggle themes.
- **Why it's separate**: Settings are a distinct screen.

### `ChatController.java`
- **What it is**: Controller for messaging.
- **What it does**: Displays conversation, sends messages.
- **Why it's separate**: Chat is a distinct interaction screen.

### `StatsController.java`
- **What it is**: Controller for statistics display.
- **What it does**: Shows user stats and achievements.
- **Why it's separate**: Statistics are a distinct screen.

### `MatchPopupController.java`
- **What it is**: Controller for match celebration popup.
- **What it does**: Shows animation when a match is made.
- **Why it's separate**: Popup is a distinct overlay.

### `AchievementPopupController.java`
- **What it is**: Controller for achievement unlock popup.
- **What it does**: Shows animation when achievement is unlocked.
- **Why it's separate**: Popup is a distinct overlay.

---

## UI ViewModels

> Located in `src/main/java/datingapp/ui/viewmodel/`

### `LoginViewModel.java`, `DashboardViewModel.java`, `MatchingViewModel.java`, `MatchesViewModel.java`, `ProfileViewModel.java`, `PreferencesViewModel.java`, `ChatViewModel.java`, `StatsViewModel.java`
- **What they are**: MVVM view models for each screen.
- **What they do**: Expose observable properties, handle business logic for the view, call services.
- **Why separate files**: Each screen has its own state and logic; MVVM separates concerns.

---

## UI Utilities

> Located in `src/main/java/datingapp/ui/util/`

### `UiAnimations.java`
- **What it is**: Reusable animation utilities.
- **What it does**: Provides pulse, fade, shake, bounce, parallax animations.
- **Why it's separate**: Animations are reusable across screens.

### `UiServices.java`
- **What it is**: Singleton for UI services.
- **What it does**: Provides toast notifications and ImageCache.
- **Why it's separate**: UI infrastructure used across screens.

### `UiHelpers.java`
- **What it is**: Miscellaneous UI helper methods.
- **What it does**: Formatting, styling utilities.
- **Why it's separate**: Common UI utilities.

---

## UI Components

> Located in `src/main/java/datingapp/ui/component/`

### `UiComponents.java`
- **What it is**: Factory for reusable UI components.
- **What it does**: Creates styled buttons, cards, badges, etc.
- **Why it's separate**: Component factories reduce duplication.

---

## Resources

### `src/main/resources/fxml/`
- **FXML files**: `login.fxml`, `dashboard.fxml`, `matching.fxml`, `matches.fxml`, `profile.fxml`, `preferences.fxml`, `chat.fxml`, `stats.fxml`, `match_popup.fxml`, `achievement_popup.fxml`
- **What they are**: JavaFX FXML view definitions.
- **Why separate files**: Each screen has its own layout.

### `src/main/resources/css/`
- **`theme.css`**: Dark theme styles.
- **`light-theme.css`**: Light theme styles.
- **Why separate**: Theme switching support.

### `src/main/resources/logback.xml`
- **What it is**: Logging configuration.
- **Why separate**: SLF4J/Logback configuration.

---

## Test Files

> Located in `src/test/java/datingapp/`

### Test Utilities (`core/testutil/`)

#### `TestStorages.java`
- **What it is**: In-memory storage implementations for testing.
- **What it does**: Provides HashMap-based implementations of all storage interfaces.
- **Why it's separate**: Enables fast unit tests without database.

#### `TestUserFactory.java`
- **What it is**: Factory for creating test users.
- **What it does**: `createCompleteUser()`, `createActiveUser()` with sensible defaults.
- **Why it's separate**: Reduces test setup boilerplate.

### Core Tests

Each service and domain model has corresponding test files following the pattern `*Test.java`:

- `UserTest.java` - User entity behavior
- `MatchStateTest.java` - Match state transitions
- `MessagingDomainTest.java` - Message/Conversation validation
- `DealbreakersTest.java` + `DealbreakersEvaluatorTest.java` - Dealbreaker logic
- `CandidateFinderTest.java` - Matching algorithm
- `MatchingServiceTest.java` - Like/match workflow
- `ValidationServiceTest.java` - Input validation
- `DailyLimitServiceTest.java` + `DailyPickServiceTest.java` - Daily features
- `UndoServiceTest.java` - Undo functionality
- `SessionServiceTest.java` - Session tracking
- `MatchQualityServiceTest.java` + `MatchQualityTest.java` - Quality computation
- `PaceCompatibilityTest.java` - Pace scoring
- `AchievementServiceTest.java` - Achievement unlocking
- `MessagingServiceTest.java` - Messaging workflow
- `RelationshipTransitionServiceTest.java` - Friend Zone/Graceful Exit
- `TrustSafetyServiceTest.java` - Reporting/blocking
- `StatsServiceTest.java` + `StatsMetricsTest.java` - Statistics
- `ProfilePreviewServiceTest.java` + `ProfileCompletionServiceTest.java` - Profile features
- `ServiceRegistryTest.java` - Dependency wiring
- `ProfileNoteTest.java` - Profile notes
- `EdgeCaseRegressionTest.java` - Regression tests

### CLI Tests
- `ProfileCreateSelectTest.java` - Profile CLI flows
- `UserSessionTest.java` - Session management

### UI Tests
- `JavaFxCssValidationTest.java` - CSS syntax validation

---

## Summary Statistics

| Category             | File Count | Description                                              |
|----------------------|------------|----------------------------------------------------------|
| Entry Points         | 2          | Main.java, DatingApp.java                                |
| Core Domain          | 12         | User, Match, Messaging, Social, Stats, Preferences, etc. |
| Core Services        | 15         | MatchingService, DailyService, MessagingService, etc.    |
| Storage Interfaces   | 13         | UserStorage, LikeStorage, MatchStorage, etc.             |
| JDBI Implementations | 16         | JdbiUserStorage, JdbiLikeStorage, etc.                   |
| CLI Handlers         | 11         | ProfileHandler, MatchingHandler, etc.                    |
| UI Controllers       | 11         | LoginController, MatchingController, etc.                |
| UI ViewModels        | 8          | One per screen                                           |
| Test Files           | ~35        | Unit and integration tests                               |
| **Total Java Files** | **~126**   | Production + test code                                   |

---

*Document generated for codebase understanding. Each file exists for a specific architectural reason, following clean architecture principles with core domain isolation.*
