# Core Module Overview

> Verified against `src/main/java/datingapp/core` (2026-09-27).
> Package-level snapshot only — run `list_dir` on a subpackage for the current files.

## Package purpose

`datingapp.core` holds domain models, business logic, and storage interfaces.
It is the framework-free zone: no JDBC, Jackson, JavaFX, or web imports.
Infrastructure lives in `datingapp.storage`; orchestration lives in
`datingapp.app.usecase/*`; location lives top-level in `datingapp.location`.

## Layout

```text
core/
  AppClock.java               # authoritative time source (now/today/clock)
  AppConfig.java              # 7-group config record (matching/validation/algorithm/storage/safety/media/auth)
  AppConfigValidator.java     # config validation
  AppSession.java             # JavaFX/CLI in-memory session (not REST auth)
  EnumSetUtil.java            # enum-set helpers
  LoggingSupport.java         # logging helpers
  RuntimeEnvironment.java     # env/property lookup
  ServiceRegistry.java        # app-wide composition root + use-case bundles
  TextUtil.java               # text helpers
  connection/                 # ConnectionModels (Conversation/Message/Like/Block/Report/FriendRequest/Notification) + ConnectionService
  i18n/                       # I18n
  matching/                   # CandidateFinder, MatchingService, CompatibilityCalculator, DailyLimitService,
                              # DailyPickService, MatchQualityService, RecommendationService, Standout(+Service),
                              # TrustSafetyService, UndoService, PreferencesMatcher, WeightedScore, moderation audit
  metrics/                    # AchievementService, ActivityMetricsService, EngagementDomain (Achievement/UserAchievement/UserStats/PlatformStats), SwipeState
  model/                      # Match (MatchState/MatchArchiveReason + generateId), User (Gender/UserState/VerificationMethod),
                              # ProfileNote (top-level record), TextNormalization
  profile/                    # ProfileService, ValidationService, MatchPreferences (Interest/Lifestyle enums), helpers
  storage/                    # interfaces: UserStorage (+LockedUserAccess), InteractionStorage (+LikeMatchWriteResult),
                              # CommunicationStorage, AnalyticsStorage, TrustSafetyStorage, AuthStorage,
                              # AccountCleanupStorage, Operational{User,Interaction,Communication}Storage, PageData
  workflow/                   # ProfileActivationPolicy, RelationshipWorkflowPolicy, WorkflowDecision
```

## State machines (verified in code)

User (`User.UserState`): `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
(`User.java:72-78`; `activate()` only from INCOMPLETE/PAUSED with complete profile,
`pause()` only from ACTIVE, `ban()` terminal).

Match (`Match.MatchState`): `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`
(`Match.java:24-30`; transitions via `unmatch()`, `block()`,
`gracefulExit()`, `reactivateFromUnmatch()`).

## Entry points (verified signatures)

| Use case | Class | Method |
|---|---|---|
| Find candidates | `matching.CandidateFinder` | `findCandidates(User, List<User>, Set<UUID>)`, `findCandidatesForUser(User)` |
| Record swipe | `matching.MatchingService` | `recordLike(ConnectionModels.Like)` → `RecordLikeOutcome`, `processSwipe(...)` |
| Report + optional block | `matching.TrustSafetyService` | `report(...)` → `ReportResult`, `block(UUID, UUID)` → `BlockResult`, `unblock(...)`, `isBlocked(...)` |
| Session/stats | `metrics.ActivityMetricsService` | `getOrCreateSession`, `recordSwipe`, `recordActivity`, `recordMatch`, `computeAndSaveStats`, `getOrComputeStats`, `computeAndSavePlatformStats` |

`ServiceRegistry` exposes the use-case bundles (`getAuthUseCases`,
`getDashboardUseCases`, `getMatchingUseCases`, `getMessagingUseCases`,
`getProfileMutationUseCases`, `getProfileNotesUseCases`,
`getProfileInsightsUseCases`, `getVerificationUseCases`, `getSocialUseCases`);
`getProfileUseCases()` is a compatibility facade — prefer the dedicated
profile slices in new code.

## Naming conventions

- Services: `*Service` (business-logic coordinators).
- Storage contracts: `*Storage` interfaces in `core/storage/`.
- Immutable values: `record` (`Like`, `Message`, `UserStats`, `PageData`, ...).
- Mutable aggregates: `class` (`User`, `Match`, `Conversation`).
- Nested enums live on their owner: `User.Gender` / `User.UserState` /
  `User.VerificationMethod`, `Match.MatchState` / `Match.MatchArchiveReason`,
  `ConnectionModels.Like.Direction`, `EngagementDomain.Achievement`.
- `ProfileNote` is a top-level record in `core.model`, not nested in `User`.
- Pair IDs are deterministic sorted-UUID joins: `Match.generateId(a, b)` /
  `ConnectionModels.Conversation.generateId(a, b)` — never `a + "_" + b`.
- Time comes from `AppClock.now()` / `AppClock.today()` (or injected
  `AppClock.clock()`), never `Instant.now()` in services/domain.
