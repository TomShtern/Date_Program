# Technical Audit Report: Java Dating Application

**Generated:** 2026-02-21  
**Model:** GL4  
**Codebase:** Date_Program  
**Java Files Analyzed:** 152 files (~48k lines)

---

## Executive Summary

This audit analyzed a layered Java dating application with CLI and JavaFX UIs, JDBI-based persistence with H2 database, and a clean architecture separating core business logic from infrastructure. The codebase demonstrates solid engineering practices including immutable records, Builder patterns, and Result types for error handling. However, several architectural gaps and implementation issues warrant attention.

**Key Themes:**
- Transaction safety gaps in cross-storage operations
- Configuration complexity and boilerplate
- Optional dependency injection leading to runtime failures
- Inconsistent state management patterns

---

## Architecture/Data-Flow Understanding

### Layered Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Entry Points                                          │
│  Main.java (CLI) / DatingApp.java (JavaFX)                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: Application Services (core.matching, core.connection) │
│  MatchingService, ConnectionService, ProfileService,            │
│  RecommendationService, TrustSafetyService, UndoService         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Domain Models (core.model)                            │
│  User (781 lines), Match (325 lines), ConnectionModels          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Storage Layer: JDBI + H2                                       │
│  JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage │
└─────────────────────────────────────────────────────────────────┘
```

### Key Data Flows
1. **Swipe Flow:** User → MatchingService.processSwipe() → InteractionStorage.saveLikeAndMaybeCreateMatch() → Match creation
2. **Messaging Flow:** User → ConnectionService.sendMessage() → CommunicationStorage (conversation + message persistence)
3. **Candidate Discovery:** User → CandidateFinder.findCandidatesForUser() → UserStorage.findCandidates() → In-memory filtering

---

## Findings

### Category 1: Missing Implementations/Features/Capabilities

#### F-001: Non-Atomic Cross-Storage Operations
| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **ScopeTag** | [MAIN] |
| **Impact** | 5/5 |
| **Effort** | 4/5 |
| **Confidence** | 5/5 |

**Location:** `ConnectionService.java:327-332, 406-414`

**Evidence:**
```java
// Line 327-332
// KNOWN LIMITATION: this revert is itself not atomic; a second failure here
// would leave the match in FRIENDS state with a still-pending request.
// A proper fix requires cross-storage transaction support.
match.revertToActive();
interactionStorage.update(match);

// Line 406-414
// KNOWN LIMITATION: the match state update and conversation archive are not atomic.
// If archiveConversation fails, the match is already GRACEFUL_EXIT but the conversation
// remains unarchived. Proper fix requires cross-storage transaction support.
```

**Why This Is an Issue:** Compensating transactions are documented but not reliable. A second failure during rollback leaves the system in an inconsistent state.

**Current Negative Impact:** Data corruption risk when relationship transitions fail mid-operation. Match states can become inconsistent with friend requests or conversation archives.

**Impact of Fix:** Guarantees data integrity for all relationship state transitions.

**Recommended Fix Direction:** Implement Saga pattern with proper compensation logging, or use JTA/XA transactions for cross-storage operations.

---

#### F-002: Missing Caching Layer
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 3/5 |
| **Confidence** | 4/5 |

**Location:** `CandidateFinder.java:142-166`, `RecommendationService.java:62-69`

**Evidence:**
```java
// CandidateFinder.java - No caching, database query on every candidate search
public List<User> findCandidatesForUser(User currentUser) {
    List<User> preFiltered = userStorage.findCandidates(...);
    // Direct database call every time
}

// RecommendationService.java - In-memory cache with LRU eviction
private final Map<String, UUID> cachedDailyPicks =
    Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
            return size() > MAX_CACHED_PICKS;
        }
    });
```

**Why This Is an Issue:** No persistent caching. In-memory daily picks cache is lost on restart. Frequently accessed user data is queried repeatedly.

**Current Negative Impact:** Unnecessary database load, slower response times for repeated queries.

**Impact of Fix:** Reduced database load, improved response times for hot data paths.

**Recommended Fix Direction:** Introduce Caffeine or similar caching library with TTL-based eviction and persistent cache support.

---

#### F-003: Simulated Verification System
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 5/5 |
| **Confidence** | 5/5 |

**Location:** `User.java:49-54`, `User.java:509-528`

**Evidence:**
```java
// Line 49-54
/**
 * Verification method used to verify a profile.
 * NOTE: Currently simulated - email/phone not sent externally.
 */
public static enum VerificationMethod {
    EMAIL,
    PHONE
}

// Line 509-528
public synchronized void startVerification(VerificationMethod method, String verificationCode) {
    this.verificationMethod = Objects.requireNonNull(method, "method cannot be null");
    this.verificationCode = Objects.requireNonNull(verificationCode, "verificationCode cannot be null");
    this.verificationSentAt = AppClock.now();
    touch();
}
```

**Why This Is an Issue:** Email/phone verification is completely simulated. No actual email or SMS is sent, meaning users cannot truly verify their identity.

**Current Negative Impact:** Trust and safety features are compromised. Verified badges provide false sense of security.

**Impact of Fix:** Real verification would enable trust features and reduce fake profiles.

**Recommended Fix Direction:** Integrate with email service (SendGrid/AWS SES) and SMS provider (Twilio) for actual verification delivery.

---

#### F-004: Undo State Lost on Restart for In-Memory Storage
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [TEST] |
| **Impact** | 2/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `TestStorages.java:984-1013`

**Evidence:**
```java
public static class Undos implements Undo.Storage {
    private final Map<UUID, Undo> byUser = new HashMap<>();
    // All state lost when JVM restarts
}
```

**Why This Is an Issue:** Test implementation loses undo state, but production (JdbiMatchmakingStorage) persists correctly. This is intentional for test isolation but could mask bugs.

**Current Negative Impact:** Tests may not catch undo-related persistence bugs.

**Impact of Fix:** Better test coverage for undo persistence scenarios.

**Recommended Fix Direction:** Add explicit persistence tests for undo functionality.

---

### Category 2: Duplication/Redundancy/Simplification Opportunities

#### F-005: AppConfig Excessive Boilerplate
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 2/5 |
| **Confidence** | 5/5 |

**Location:** `AppConfig.java:1-904`

**Evidence:**
```java
// 904 lines total
// Lines 196-434: 60+ delegating accessors that just call sub-record methods
public int dailyLikeLimit() {
    return matching.dailyLikeLimit();
}
public int dailySuperLikeLimit() {
    return matching.dailySuperLikeLimit();
}
// ... repeated 60+ times
```

**Why This Is an Issue:** 60+ delegate methods exist solely for backward compatibility with the flat accessor pattern. Adds 240 lines of boilerplate.

**Current Negative Impact:** Maintenance burden. Adding new config options requires updating multiple locations.

**Impact of Fix:** ~200 lines reduction, clearer structure, easier maintenance.

**Recommended Fix Direction:** Remove delegate accessors, update all call sites to use sub-record accessors directly (e.g., `config.matching().dailyLikeLimit()`).

---

#### F-006: Repetitive Builder Pattern Implementation
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 3/5 |
| **Confidence** | 4/5 |

**Location:** `MatchingService.java:49-100`, `RecommendationService.java:71-134`

**Evidence:**
```java
// MatchingService.java - Builder pattern
public static Builder builder() { return new Builder(); }
public static class Builder {
    private InteractionStorage interactionStorage;
    private TrustSafetyStorage trustSafetyStorage;
    // ... setters ...
    public MatchingService build() { return new MatchingService(...); }
}

// RecommendationService.java - Nearly identical Builder pattern
public static Builder builder() { return new Builder(); }
public static class Builder {
    private UserStorage userStorage;
    private InteractionStorage interactionStorage;
    // ... setters ...
    public RecommendationService build() { return new RecommendationService(this); }
}
```

**Why This Is an Issue:** Each service has a nearly identical Builder implementation. No shared abstraction or code reuse.

**Current Negative Impact:** Boilerplate duplication across services.

**Impact of Fix:** Reduced code duplication, consistent builder behavior.

**Recommended Fix Direction:** Consider using a dependency injection framework (Guice/Dagger) or generate builders via annotation processing (AutoValue/Immutables).

---

#### F-007: ProfileService Hardcoded Scoring Constants
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 2/5 |
| **Confidence** | 5/5 |

**Location:** `ProfileService.java:31-64`

**Evidence:**
```java
// Inlined constants that could be configurable
private static final int BIO_TIP_MIN_LENGTH = 50;
private static final int BIO_TIP_BOOST_LENGTH = 100;
private static final int PHOTO_TIP_MIN_COUNT = 2;
private static final int LIFESTYLE_FIELDS_MIN = 3;
private static final int DISTANCE_TIP_MAX_KM = 10;
private static final int AGE_RANGE_TIP_MIN_YEARS = 5;

private static final int BASIC_NAME_POINTS = 5;
private static final int BASIC_BIO_POINTS = 10;
// ... 15+ more hardcoded scoring values
```

**Why This Is an Issue:** Scoring weights are hardcoded rather than configurable. Cannot tune profile completion algorithm without code changes.

**Current Negative Impact:** Inflexible scoring system, requires deployment for tuning.

**Impact of Fix:** Allows runtime tuning of profile scoring via configuration.

**Recommended Fix Direction:** Move scoring weights to AppConfig under a new `ProfileScoringConfig` sub-record.

---

#### F-008: TestStorages Repetitive Implementation Patterns
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [TEST] |
| **Impact** | 1/5 |
| **Effort** | 2/5 |
| **Confidence** | 4/5 |

**Location:** `TestStorages.java:68-1063`

**Evidence:**
```java
// Repetitive pattern across all storage implementations:
public void save(User user) {
    users.put(user.getId(), user);
}
public Optional<User> get(UUID id) {
    return Optional.ofNullable(users.get(id));
}
// Same pattern repeated for:
// - Users, Interactions, Communications, Analytics, TrustSafety, Undos, Standouts
```

**Why This Is an Issue:** 1000+ lines of nearly identical CRUD operations across storage implementations.

**Current Negative Impact:** Maintenance burden for test infrastructure.

**Impact of Fix:** Reduced test code, easier to add new test storage implementations.

**Recommended Fix Direction:** Extract common patterns to generic base class `InMemoryStorage<K, V>` with default CRUD implementations.

---

### Category 3: Logic/Architecture/Structure Flaws

#### F-009: Match.transitionToFriends Missing End Metadata
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `Match.java:171-182`

**Evidence:**
```java
/** transitionToFriends - transitions the match to FRIENDS state. */
public void transitionToFriends(UUID initiatorId) {
    if (isInvalidTransition(this.state, MatchState.FRIENDS)) {
        throw new IllegalStateException("Cannot transition to FRIENDS from " + state);
    }
    if (!involves(initiatorId)) {
        throw new IllegalArgumentException("User is not part of this match");
    }
    this.state = MatchState.FRIENDS;
    // We don't set endedAt/endedBy because the relationship is still "active" in a new way
}
```

**Why This Is an Issue:** All other terminal states (UNMATCHED, BLOCKED, GRACEFUL_EXIT) set endedAt, endedBy, and endReason. FRIENDS does not, creating inconsistency. The comment justifies this but the pattern is confusing for analytics.

**Current Negative Impact:** Analytics queries cannot uniformly use endedAt for state transition timestamps.

**Impact of Fix:** Consistent state transition metadata across all match states.

**Recommended Fix Direction:** Add `transitionedAt` and `transitionedBy` fields for non-terminal transitions, or document the distinction clearly in Match javadoc.

---

#### F-010: Optional Dependencies Leading to Runtime Errors
| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **ScopeTag** | [MAIN] |
| **Impact** | 4/5 |
| **Effort** | 3/5 |
| **Confidence** | 5/5 |

**Location:** `MatchingService.java:29-31, 141-144`, `TrustSafetyService.java:237, 290, 312`

**Evidence:**
```java
// MatchingService.java
private ActivityMetricsService activityMetricsService; // Optional
private UndoService undoService; // Optional
private RecommendationService dailyService; // Optional

public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
    if (dailyService == null || undoService == null) {
        return SwipeResult.configError("dailyService and undoService required for processSwipe");
    }
    // ...
}

// TrustSafetyService.java
public BlockResult block(UUID blockerId, UUID blockedId) {
    if (storage == null || userStorage == null) {
        throw new IllegalStateException("TrustSafetyStorage or UserStorage is not configured");
    }
    // ...
}
```

**Why This Is an Issue:** Optional dependencies cause runtime errors or degraded functionality rather than compile-time failures. Callers may not know a service is misconfigured until runtime.

**Current Negative Impact:** Production failures from misconfigured services. Difficult to reason about service requirements.

**Impact of Fix:** Compile-time guarantees that all dependencies are satisfied. Clear service contracts.

**Recommended Fix Direction:** Make all required dependencies non-nullable in constructors. Use separate factory methods for optional capability configuration.

---

#### F-011: RecommendationService Cache Key Collision Risk
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 2/5 |
| **Confidence** | 4/5 |

**Location:** `RecommendationService.java:236-259`

**Evidence:**
```java
long seed = today.toEpochDay() + seeker.getId().hashCode();
Random pickRandom = new Random(seed);

String cacheKey = seeker.getId() + "_" + today;

// Use computeIfAbsent for atomic check-and-populate
UUID pickedId = cachedDailyPicks.computeIfAbsent(cacheKey, ignoredKey -> candidates
        .get(pickRandom.nextInt(candidates.size()))
        .getId());

// Find the cached user in current candidates (may have been filtered out since caching)
User picked = candidates.stream()
        .filter(candidate -> candidate.getId().equals(pickedId))
        .findFirst()
        .orElse(null);

if (picked == null) {
    // Cached pick no longer in candidate list
    cachedDailyPicks.remove(cacheKey);
    picked = candidates.get(pickRandom.nextInt(candidates.size()));
    cachedDailyPicks.put(cacheKey, picked.getId());
}
```

**Why This Is an Issue:** Race condition between cache check and candidate filtering. If a cached user is filtered out (blocked, deactivated), the fallback picks a new random candidate but uses the same random seed, potentially picking the same filtered candidate again.

**Current Negative Impact:** Could return invalid candidates or cause repeated filtering attempts.

**Impact of Fix:** Guaranteed valid daily pick selection even when cached users become invalid.

**Recommended Fix Direction:** Use a retry loop with different seeds, or filter candidates before cache lookup.

---

#### F-012: StorageBuilder Bypasses User Synchronization
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| ScopeTag | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 2/5 |
| **Confidence** | 5/5 |

**Location:** `User.java:153-306`, `User.java:310-385`

**Evidence:**
```java
// User.StorageBuilder directly assigns to fields without synchronization
public static final class StorageBuilder {
    private final User user;
    
    public StorageBuilder bio(String bio) {
        user.bio = bio;  // Direct field access, no synchronization
        return this;
    }
    // ... all other setters bypass synchronized methods
}

// But all getters and regular setters are synchronized
public synchronized String getBio() {
    return bio;
}

public synchronized void setBio(String bio) {
    this.bio = bio;
    touch();
}
```

**Why This Is an Issue:** StorageBuilder directly writes to User fields without synchronization, while all other access is synchronized. This could cause visibility issues in concurrent access scenarios.

**Current Negative Impact:** Potential race conditions when reconstructing Users from database while other threads access the same User instance.

**Impact of Fix:** Thread-safe User construction from storage layer.

**Recommended Fix Direction:** Synchronize StorageBuilder methods or use a constructor-based approach where the User is fully constructed before being exposed.

---

#### F-013: Match.restoreDeletedAt Bypasses Domain Invariants
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `Match.java:293-301`

**Evidence:**
```java
/**
 * Restores the deleted-at timestamp from storage. This method is for storage
 * layer reconstitution only — production code should use
 * {@link #markDeleted(Instant)}.
 */
public void restoreDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
}
```

**Why This Is an Issue:** The method bypasses the `markDeleted` API that may have additional logic in the future. Storage reconstitution should ideally use the same domain methods.

**Current Negative Impact:** Maintenance risk if markDeleted ever adds behavior (e.g., audit logging).

**Impact of Fix:** Consistent state mutation through domain methods.

**Recommended Fix Direction:** Either remove restoreDeletedAt and pass deletedAt through constructor, or ensure markDeleted is safe for reconstitution.

---

### Category 4: Clear Problems/Issues/Mistakes

#### F-014: JdbiUserStorage ALL_COLUMNS Drift Risk
| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **ScopeTag** | [MAIN] |
| **Impact** | 4/5 |
| **Effort** | 2/5 |
| **Confidence** | 4/5 |

**Location:** `JdbiUserStorage.java:47-56`

**Evidence:**
```java
public static final String ALL_COLUMNS = """
        id, name, bio, birth_date, gender, interested_in, lat, lon,
        has_location_set, max_distance_km, min_age, max_age, photo_urls, state, created_at,
        updated_at, smoking, drinking, wants_kids, looking_for, education,
        height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
        db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
        interests, email, phone, is_verified, verification_method,
        verification_code, verification_sent_at, verified_at,
        pace_messaging_frequency, pace_time_to_first_date,
        pace_communication_style, pace_depth_preference, deleted_at""";
```

**Why This Is an Issue:** Manual column list can drift from actual schema. No validation that all columns exist. Adding a column requires updating multiple places (ALL_COLUMNS, SELECT queries, INSERT/MERGE statements, Mapper).

**Current Negative Impact:** Schema migrations may break silently if columns are added/removed without updating storage code.

**Impact of Fix:** Compile-time or startup-time validation of column names against schema.

**Recommended Fix Direction:** Use JDBI's `@ColumnName` annotations or generate column lists from schema metadata at startup.

---

#### F-015: Inconsistent Error Handling - IllegalStateException vs Result Types
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 3/5 |
| **Confidence** | 5/5 |

**Location:** `TrustSafetyService.java:237, 290, 312`, `MatchingService.java:242-271`

**Evidence:**
```java
// TrustSafetyService throws exceptions for business failures
public BlockResult block(UUID blockerId, UUID blockedId) {
    if (storage == null || userStorage == null) {
        throw new IllegalStateException("TrustSafetyStorage or UserStorage is not configured");
    }
    // ...
}

// MatchingService uses Result records for business failures
public static record SwipeResult(boolean success, boolean matched, Match match, Like like, String message) {
    public static SwipeResult configError(String reason) {
        return new SwipeResult(false, false, null, null, reason);
    }
}
```

**Why This Is an Issue:** Inconsistent approach to error handling. Some services throw exceptions for configuration failures, others return Result records. AGENTS.md states "Services return Result records instead of throwing" but this is not uniformly applied.

**Current Negative Impact:** Inconsistent error handling makes it harder to build reliable error handling in calling code.

**Impact of Fix:** Consistent error handling pattern across all services.

**Recommended Fix Direction:** Define clear policy: Result types for business failures, exceptions for infrastructure/configuration failures. Document and enforce consistently.

---

#### F-016: ServiceRegistry Excessive Constructor Parameters
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 2/5 |
| **Confidence** | 5/5 |

**Location:** `ServiceRegistry.java:44-78`

**Evidence:**
```java
@SuppressWarnings("java:S107")  // Suppressing "Methods should not have too many parameters"
public ServiceRegistry(
        AppConfig config,
        UserStorage userStorage,
        InteractionStorage interactionStorage,
        CommunicationStorage communicationStorage,
        AnalyticsStorage analyticsStorage,
        TrustSafetyStorage trustSafetyStorage,
        CandidateFinder candidateFinder,
        MatchingService matchingService,
        TrustSafetyService trustSafetyService,
        ActivityMetricsService activityMetricsService,
        MatchQualityService matchQualityService,
        ProfileService profileService,
        RecommendationService recommendationService,
        UndoService undoService,
        ConnectionService connectionService,
        ValidationService validationService) {
    // 16 parameters!
}
```

**Why This Is an Issue:** 16 constructor parameters indicates a god object pattern. The suppression of S107 (too many parameters) is a code smell.

**Current Negative Impact:** Difficult to test, difficult to understand dependencies, difficult to add new services.

**Impact of Fix:** More modular service organization, easier testing.

**Recommended Fix Direction:** Split into logical sub-registries (MatchingServices, StorageServices, ProfileServices) or use dependency injection framework.

---

#### F-017: User.getAge() Returns 0 for Missing Birth Date
| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **ScopeTag** | [MAIN] |
| **Impact** | 3/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `User.java:434-450`

**Evidence:**
```java
public synchronized int getAge() {
    return getAge(AppConfig.defaults().safety().userTimeZone());
}

public synchronized int getAge(java.time.ZoneId timezone) {
    if (birthDate == null) {
        return 0;  // Returns 0 instead of throwing or returning Optional
    }
    return Period.between(birthDate, AppClock.today(timezone)).getYears();
}
```

**Why This Is an Issue:** Returning 0 for missing birth date is ambiguous. A user could genuinely be 0 years old (edge case) or have no birth date set. Downstream code may not handle this correctly.

**Current Negative Impact:** CandidateFinder treats age 0 as invalid and filters out the user, but other code may not.

**Impact of Fix:** Clear distinction between "no birth date" and "age is 0".

**Recommended Fix Direction:** Return `Optional<Integer>` or throw `IllegalStateException` for missing birth date.

---

#### F-018: CandidateFinder Location Logging May Leak PII
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 2/5 |
| **Effort** | 1/5 |
| **Confidence** | 4/5 |

**Location:** `CandidateFinder.java:291-296`

**Evidence:**
```java
logDebug(
    "Skipping distance filter for {} ({}): missing location (seekerLatLon={}, candidateLatLon={}).",
    candidate.getName(),
    candidate.getId(),
    formatLatLon(seeker),
    formatLatLon(candidate));
```

**Why This Is an Issue:** Debug logs include user names, IDs, and potentially precise coordinates. In production environments with debug logging enabled, this could expose PII.

**Current Negative Impact:** PII exposure risk in debug logs.

**Impact of Fix:** Reduced PII in logs, compliance with privacy regulations.

**Recommended Fix Direction:** Anonymize or truncate coordinates in debug logs. Use user IDs instead of names.

---

#### F-019: RecommendationService Activity Score Constants
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [MAIN] |
| **Impact** | 1/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `RecommendationService.java:36-50`

**Evidence:**
```java
// ══════ ACTIVITY SCORE THRESHOLDS (hours since last active) ══════
private static final long ACTIVITY_VERY_RECENT_HOURS = 1;
private static final long ACTIVITY_RECENT_HOURS = 24;
private static final long ACTIVITY_MODERATE_HOURS = 72;
private static final long ACTIVITY_WEEKLY_HOURS = 168;
private static final long ACTIVITY_MONTHLY_HOURS = 720;

// ══════ ACTIVITY SCORE VALUES ══════
private static final double ACTIVITY_SCORE_VERY_RECENT = 1.0;
private static final double ACTIVITY_SCORE_RECENT = 0.9;
private static final double ACTIVITY_SCORE_MODERATE = 0.7;
private static final double ACTIVITY_SCORE_WEEKLY = 0.5;
private static final double ACTIVITY_SCORE_MONTHLY = 0.3;
private static final double ACTIVITY_SCORE_INACTIVE = 0.1;
private static final double ACTIVITY_SCORE_UNKNOWN = 0.5;
```

**Why This Is an Issue:** Activity thresholds and scores are hardcoded, making it difficult to tune the recommendation algorithm. Similar to F-007 but for recommendations.

**Current Negative Impact:** Cannot tune recommendation scoring without code changes.

**Impact of Fix:** Allows A/B testing and tuning of recommendation parameters.

**Recommended Fix Direction:** Move to AppConfig under a new `RecommendationScoringConfig` sub-record.

---

#### F-020: TestStorages.Interactions Missing Soft Delete Check in Some Methods
| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **ScopeTag** | [TEST] |
| **Impact** | 2/5 |
| **Effort** | 1/5 |
| **Confidence** | 5/5 |

**Location:** `TestStorages.java:170-176`

**Evidence:**
```java
@Override
public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
    return likes.values().stream()
            .filter(like -> like.whoLikes().equals(fromUserId))
            .filter(like -> like.whoGotLiked().equals(toUserId))
            .findFirst();
    // No check for deleted likes!
}
```

**Why This Is an Issue:** Unlike Match storage which checks `deletedAt`, Like storage doesn't filter soft-deleted records. Could cause test inconsistencies.

**Current Negative Impact:** Tests may not accurately reflect production behavior for soft-deleted likes.

**Impact of Fix:** Test storage behavior matches production.

**Recommended Fix Direction:** Add soft-delete tracking to TestStorages.Interactions.

---

---

## Prioritized Remediation Roadmap

### Phase 1: Critical Security & Integrity (Week 1-2)
**Goal:** Eliminate data corruption risk and improve error visibility

| Finding | Action | Effort |
|---------|--------|--------|
| F-001 | Implement Saga pattern for cross-storage operations | High |
| F-010 | Make required dependencies non-nullable | Medium |
| F-014 | Add column validation at startup | Low |

### Phase 2: Code Quality & Maintainability (Week 3-4)
**Goal:** Reduce boilerplate and improve code organization

| Finding | Action | Effort |
|---------|--------|--------|
| F-005 | Remove AppConfig delegate accessors | Low |
| F-016 | Split ServiceRegistry into sub-registries | Medium |
| F-015 | Standardize error handling approach | Medium |

### Phase 3: Performance & Features (Week 5-6)
**Goal:** Improve performance and complete missing features

| Finding | Action | Effort |
|---------|--------|--------|
| F-002 | Add caching layer (Caffeine) | Medium |
| F-003 | Integrate real email/SMS verification | High |
| F-007, F-019 | Move scoring constants to configuration | Low |

---

## Strategic Options/Alternatives

### Option A: Minimal Intervention
Continue with current architecture. Accept the limitations documented in KNOWN LIMITATION comments. Focus only on F-001 (transaction safety) to prevent data corruption.

**Pros:** Low effort, minimal disruption  
**Cons:** Technical debt accumulation, maintenance burden

### Option B: Incremental Modernization (Recommended)
Follow the Phase 1-3 roadmap. Address critical issues first, then improve code quality and performance incrementally.

**Pros:** Balanced risk/reward, measurable progress  
**Cons:** Requires sustained effort over 6 weeks

### Option C: Architectural Refactoring
Introduce dependency injection framework (Guice/Dagger), implement proper transaction management (JTA), add caching layer, and complete verification system.

**Pros:** Modern architecture, easier future maintenance  
**Cons:** High effort, significant code changes, risk of regression

---

## Acceptance Checklist

### Critical Fixes Completed
- [ ] F-001: Cross-storage operations are atomic or have reliable compensation
- [ ] F-010: All required service dependencies are non-nullable
- [ ] F-014: Schema validation at application startup

### Quality Improvements Completed
- [ ] F-005: AppConfig delegate accessors removed
- [ ] F-015: Consistent error handling documented and implemented
- [ ] F-016: ServiceRegistry split into logical sub-registries

### Performance Improvements Completed
- [ ] F-002: Caching layer implemented with TTL and size limits
- [ ] F-007, F-019: Scoring constants moved to configuration

### Tests Passing
- [ ] All existing unit tests pass
- [ ] Integration tests cover cross-storage transactions
- [ ] Performance tests verify cache effectiveness

---

**Report Generated:** 2026-02-21  
**Total Findings:** 20  
**Critical:** 1 | **High:** 3 | **Medium:** 7 | **Low:** 9  
**Main Scope:** 18 | **Test Scope:** 2
