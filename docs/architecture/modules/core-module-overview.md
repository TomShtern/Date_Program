# Core Module Overview

> Domain layer for the Dating App - Pure Java models and business logic.

## Package Purpose

The `datingapp.core` package contains all domain models, business logic, and service interfaces. This is a **POJO-only zone** - no framework dependencies (JDBC, Jackson, JavaFX) are allowed here.

## Key Design Principles

1. **Immutable Value Objects** - Use `record` for data containers (e.g., `Like`, `MatchQuality`)
2. **Stateful Entities** - Use `class` for mutable entities (e.g., `User`, `Match`)
3. **Fail-Fast Validation** - Validate in constructors using `Objects.requireNonNull`
4. **Deterministic Logic** - IDs, scores, and daily picks must be reproducible

## Module Structure

```
core/
├── Achievement.java          # Gamification model (UserAchievement inner record)
├── AppConfig.java            # Application configuration (record)
├── CandidateFinder.java      # Matching algorithm (excludes blocked, liked, mismatched)
├── Dealbreakers.java         # Hard filters (lifestyle requirements)
├── DailyService.java         # Daily limits and premium status
├── Match.java                # Mutual like outcome (state machine: ACTIVE → FRIENDS/BLOCKED/etc)
├── MatchingService.java      # Like/pass logic, match creation
├── Messaging.java            # Conversation + Message records
├── Preferences.java          # Interest enum + Lifestyle constants
├── ServiceRegistry.java      # Dependency injection (composition root)
├── SessionService.java       # Swipe session tracking
├── Social.java               # FriendRequest, Notification records
├── Stats.java                # UserStats, PlatformStats analytics records
├── SwipeSession.java         # Active swiping session entity
├── TrustSafetyService.java   # Blocking, reporting, moderation
├── UndoService.java          # Undo last swipe functionality
├── User.java                 # Main user entity (41 fields, StorageBuilder pattern)
├── UserInteractions.java     # Like, Block, Report records
└── storage/                  # Storage interfaces (contracts)
    ├── BlockStorage.java
    ├── ConversationStorage.java
    ├── LikeStorage.java
    ├── MatchStorage.java
    ├── MessageStorage.java
    ├── NotificationStorage.java
    ├── ProfileNoteStorage.java
    ├── ProfileViewStorage.java
    ├── ReportStorage.java
    ├── SwipeSessionStorage.java
    ├── UserAchievementStorage.java
    ├── UserStatsStorage.java
    └── UserStorage.java
```

## State Machines

### User Lifecycle
```
INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
```

### Match Lifecycle
```
ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED
```

## Entry Points

| Use Case        | Class                | Method                                   |
|-----------------|----------------------|------------------------------------------|
| Find candidates | `CandidateFinder`    | `findCandidates(UUID userId, int limit)` |
| Record swipe    | `MatchingService`    | `recordLike(Like like)`                  |
| Get user stats  | `StatsService`       | `getLatestStats(UUID userId)`            |
| Block user      | `TrustSafetyService` | `blockUser(UUID blocker, UUID blocked)`  |

## Naming Conventions

- **Services**: `*Service` (business logic coordinators)
- **Storage interfaces**: `*Storage` (data access contracts)
- **Records**: Use for immutable value objects
- **Enums**: Prefer top-level for cross-package use (e.g., `Gender`, `UserState`, `VerificationMethod`)
