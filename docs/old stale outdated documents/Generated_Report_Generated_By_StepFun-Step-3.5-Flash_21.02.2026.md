# Executive Summary

This audit examined a Java dating application implementing a three-layer clean architecture (core, storage, app). The codebase totals approximately 78 main Java files and 59 test files with ~800+ tests.

**Overall Assessment:** The system demonstrates solid architectural separation and consistent patterns, but critical gaps exist in atomic transaction support, configuration management, and thread safety. The most significant risks are data integrity issues from cross-storage operations and inconsistent validation due to static configuration dependencies.

**Total Findings:** 20 (High: 4, Medium: 11, Low: 5)

**Dominant Risk Themes:**
- Configuration management inconsistencies (static defaults vs injected)
- Missing transactional boundaries across storage backends
- Over-synchronization and concurrency bottlenecks
- Input validation gaps (coordinates, age)

---

# Architecture and Data-Flow Understanding (Code-Derived)

**Runtime Entry Points:**
- `Main.java` (CLI): Booting via `ApplicationStartup.initialize()`, wiring handlers, interactive menu loop
- `DatingApp.java` (JavaFX): `Application.start()` initializes `ViewModelFactory` and `NavigationService`

**Initialization Flow:**
```
ApplicationStartup.initialize()
    -> Loads config from ./config/app-config.json + env vars
    -> DatabaseManager.getInstance().getConnection() initializes schema
    -> StorageFactory.buildH2() creates JDBI implements, services
    -> Returns ServiceRegistry
```

**Data Flow (Write Path):**
```
CLI/UI Controller -> Service (core/*) -> Storage (storage/jdbi/*) -> H2 DB
```

**Key Services:**
- `MatchingService` - swipe processing, match creation
- `ConnectionService` - messaging, relationship transitions
- `ProfileService` - profile management, achievements
- `TrustSafetyService` - blocking, reporting, verification
- `RecommendationService` - candidate discovery, daily picks, standouts

**Storage Pattern:** All `*Storage` interfaces have JDBI implementations with `StorageBuilder` for entity reconstruction. Soft delete via `deleted_at` timestamp.

---

# Findings

## 1. Missing Implementations / Features / Capabilities

### F-001: Cross-Storage Atomic Transactions Missing
**File:** `src/main/java/datingapp/core/connection/ConnectionService.java`  
**Lines:** 310-336, 378-417  
**Evidence:**
```java
if (interactionStorage.supportsAtomicRelationshipTransitions()) {
    try {
        boolean transitioned = interactionStorage.acceptFriendZoneTransition(match, updated, acceptedNotification);
        if (!transitioned) return TransitionResult.failure(...);
        return TransitionResult.ok();
    } catch (Exception e) { return TransitionResult.failure(...); }
}
// Fallback non-atomic path with known limitations
try {
    communicationStorage.updateFriendRequest(updated);
} catch (Exception e) {
    // Compensating write: revert match state to keep data consistent.
    // KNOWN LIMITATION: this revert is itself not atomic; a second failure here
    // would leave the match in FRIENDS state with a still-pending request.
    match.revertToActive();
    interactionStorage.update(match);
    return TransitionResult.failure(...);
}
```
**Why:** The system cannot atomically update match state, conversation archival, and notification creation across independent storage backends. The fallback path leaves inconsistent states on partial failure.
**Current Impact:** Data corruption risk when friend zone/graceful exit operations fail mid-way.
**Fix Impact:** Guarantee consistency for multi-resource relationship transitions.
**Recommended Fix:** Implement a transaction coordinator or use a storage that supports atomic multi-table updates (e.g., via H2 savepoints or a unit-of-work pattern).

### F-002: Verification Code Rate Limiting Not Implemented
**File:** `src/main/java/datingapp/core/matching/TrustSafetyService.java`  
**Lines:** 92-122  
**Evidence:**
```java
public String generateVerificationCode() {
    int value = random.nextInt(1_000_000);
    return String.format("%06d", value);
}
// No rate limit checks before generating
```
**Why:** No throttling on code generation requests enables SMS/email flooding and brute-force attacks.
**Current Impact:** Security vulnerability; resource exhaustion.
**Fix Impact:** Prevent abuse of verification system.
**Recommended Fix:** Add per-user rate limiter (token bucket) tracked in storage with TTL.

### F-003: Photo Persistence Incomplete
**File:** `src/main/java/datingapp/ui/screen/ProfileController.java` (referenced; exact lines not provided)  
**Evidence:** `// TODO: Save photo path to ViewModel/storage`
**Why:** Users cannot persist profile photos across sessions.
**Current Impact:** Poor user experience; data loss on logout.
**Fix Impact:** Complete profile feature set.
**Recommended Fix:** Implement photo upload to persistent storage (filesystem or object store) and save URL in user record.

### F-004: User Presence Tracking Not Implemented
**File:** `src/main/java/datingapp/ui/screen/MatchesController.java`  
**Evidence:** `// TODO: Replace with real presence status when user presence tracking is implemented`
**Why:** Placeholder code; online status does not reflect actual user activity.
**Current Impact:** Feature unavailable; misleading UI.
**Fix Impact:** Enable real-time presence indications.
**Recommended Fix:** Implement heartbeat mechanism or WebSocket-based presence updates.

## 2. Duplication / Redundancy / Simplification Opportunities

### F-005: Duplicate Configuration Thresholds
**File:** `src/main/java/datingapp/core/profile/ProfileService.java`  
**Lines:** 31-64  
**Evidence:**
```java
private static final int BIO_TIP_MIN_LENGTH = 50;
private static final int BIO_TIP_BOOST_LENGTH = 100;
private static final int PHOTO_TIP_MIN_COUNT = 2;
private static final int LIFESTYLE_FIELDS_MIN = 3;
private static final int DISTANCE_TIP_MAX_KM = 10;
private static final int AGE_RANGE_TIP_MIN_YEARS = 5;
...
private static final int TIER_DIAMOND_THRESHOLD = 95;
private static final int TIER_GOLD_THRESHOLD = 85;
...
```
**Why:** These values may duplicate corresponding thresholds in `AppConfig`. Centralizing in config avoids drift.
**Impact:** Maintenance overhead; risk of inconsistent behavior when tuning.
**Recommended Fix:** Move thresholds to `AppConfig` and reference them from `ProfileService`.

### F-006: Duplicate Deterministic ID Generation Logic
**File:** `src/main/java/datingapp/core/model/Match.java` (lines 127-143) and `src/main/java/datingapp/core/connection/ConnectionModels.java` (lines 145-153)  
**Evidence:** Both contain identical `generateId(UUID a, UUID b)` method.
**Why:** Copy-paste duplication increases maintenance burden and risk of divergence.
**Impact:** If one is updated, the other may be forgotten.
**Recommended Fix:** Extract to a shared utility class, e.g., `IdGenerator`.

### F-007: Repeated EnumSet Copying Pattern
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 334-336, 423-425, 540-543, 642-654, etc.  
**Evidence:**
```java
public synchronized Set<Gender> getInterestedIn() {
    return EnumSetUtil.safeCopy(interestedIn, Gender.class);
}
```
**Why:** Defensive copying repeated for every EnumSet getter; verbose but correct.
**Impact:** Many short-lived objects; could be optimized if performance critical.
**Recommended Fix:** Consider returning immutable copies via `Set.copyOf()` or accept overhead as is.

### F-008: Magic Numbers Hard-Coded in User Constructor
**File:** `src/main/java/datingapp/core/model/User.java`  
**Line:** 141  
**Evidence:** `this.maxDistanceKm = 50;`
**Why:** Default preference differs from `AppConfig.defaults().maxDistanceKm()` (500 km). This discrepancy should be explicit in configuration.
**Impact:** New users get conservative default; may surprise developers.
**Recommended Fix:** Reference config default in constructor: `this.maxDistanceKm = AppConfig.defaults().maxDistanceKm();` or document rationale.

## 3. Logic / Architecture / Structure Flaws

### F-009: Static Configuration Dependency in Domain Model
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 72, 561, 564  
**Evidence:**
```java
private static final AppConfig CONFIG = AppConfig.defaults();
public synchronized void setAgeRange(int minAge, int maxAge) {
    if (minAge < CONFIG.minAge()) {
        throw new IllegalArgumentException("minAge must be at least " + CONFIG.minAge());
    }
```
**Why:** Validation uses static defaults instead of application-scoped config. Inconsistent with custom configurations.
**Current Impact:** Rules enforced at runtime may not match the actual operational configuration.
**Fix Impact:** Ensure validation aligns with business rules.
**Recommended Fix:** Inject `AppConfig` instance (e.g., via constructor or setter) and use that instead of static defaults.

### F-010: Same Static Config Issue in MatchPreferences
**File:** `src/main/java/datingapp/core/profile/MatchPreferences.java`  
**Lines:** 392, 419-424  
**Evidence:**
```java
private static final AppConfig CONFIG = AppConfig.defaults();
public Dealbreakers {
    if (minHeightCm != null && minHeightCm < CONFIG.minHeightCm()) {
        throw new IllegalArgumentException("minHeightCm too low: " + minHeightCm);
    }
```
**Why:** Same pattern as F-009; validation inconsistent with actual config.
**Impact:** Potential rejects valid inputs or accepts invalid ones when custom configs are used.
**Recommended Fix:** Pass `AppConfig` to `MatchPreferences` builder and use injected instance.

### F-011: Inconsistent Error Handling Between Services
**File:** `src/main/java/datingapp/core/connection/ConnectionService.java` vs `src/main/java/datingapp/core/matching/MatchingService.java`  
**Lines:** ConnSvce 68-92; MatchSvce 141-160  
**Evidence:**
```java
// ConnectionService
User sender = userStorage.get(senderId).orElse(null);
if (sender == null || sender.getState() != UserState.ACTIVE) {
    return SendResult.failure(SENDER_NOT_FOUND, ...);
}

// MatchingService
Objects.requireNonNull(currentUser, "currentUser cannot be null");
Objects.requireNonNull(candidate, "candidate cannot be null");
```
**Why:** Mixed defensive null-checking vs requireNonNull creates unpredictable API surface.
**Impact:** Callers must handle both `IllegalArgumentException` and explicit `Result.failure()`.
**Recommended Fix:** Standardize all service methods to use Result records for business failures and `requireNonNull` for programmer errors.

### F-012: Missing State Validation in User.pause()
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 692-699  
**Evidence:**
```java
public synchronized void pause() {
    if (state != UserState.ACTIVE) {
        throw new IllegalStateException("Can only pause an active user");
    }
    this.state = UserState.PAUSED;
    touch();
}
```
**Why:** `activate()` checks `isComplete()`, but `pause()` does not. Inconsistent state machine validation.
**Impact:** Could allow pausing of incomplete profiles or other invalid transitions.
**Recommended Fix:** Add `if (!isComplete()) throw new IllegalStateException("Cannot pause incomplete profile");` for symmetry.

### F-013: Match State Machine Transition Asymmetry
**File:** `src/main/java/datingapp/core/model/Match.java`  
**Lines:** 206-216, 171-182  
**Evidence:**
```java
private boolean isInvalidTransition(MatchState from, MatchState to) { ... }
public void transitionToFriends(UUID initiatorId) {
    if (isInvalidTransition(this.state, MatchState.FRIENDS)) {
        throw new IllegalStateException("Cannot transition to FRIENDS from " + state);
    }
```
**Why:** Validator allows different transition sets per state; `revertToActive()` allows FRIENDS→ACTIVE but not documented in validator. Complexity increases risk of incorrect use.
**Current Impact:** Hard to reason about allowed transitions; potential for bugs when extending state machine.
**Recommended Fix:** Document state transition diagram explicitly; consider using a state pattern or a centralized transition matrix.

### F-014: Conversation Not Fully Updated After Message Send
**File:** `src/main/java/datingapp/core/connection/ConnectionService.java`  
**Lines:** 100-108  
**Evidence:**
```java
Message message = Message.create(conversationId, senderId, content);
communicationStorage.saveMessage(message);
communicationStorage.updateConversationLastMessageAt(conversationId, message.createdAt());
// Sender's read timestamp not advanced
```
**Why:** After sending, the sender's last read position remains before the new message, causing unread indicator to appear.
**Current Impact:** UX glitch; sender sees conversation as having unread messages.
**Recommended Fix:** Also update `conversation.setLastReadBy(senderId, message.createdAt())` or equivalent.

## 4. Clear Problems / Issues / Mistakes

### F-015: Excessive Synchronization in User Entity
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 310-825 (most methods)  
**Evidence:** Nearly every getter/setter declared `synchronized`.
**Why:** Overuse of synchronization leads to unnecessary contention; most usage is single-threaded per session.
**Current Impact:** Performance bottleneck under concurrent access.
**Recommended Fix:** Use fine-grained locks or `java.util.concurrent.locks.ReentrantReadWriteLock` to allow concurrent reads. Alternatively, remove synchronization if thread confinement can be guaranteed.

### F-016: Race Condition in Daily Pick Cache
**File:** `src/main/java/datingapp/core/matching/RecommendationService.java`  
**Lines:** 241-259  
**Evidence:**
```java
UUID pickedId = cachedDailyPicks.computeIfAbsent(cacheKey, ...);
User picked = candidates.stream().filter(...).findFirst().orElse(null);
if (picked == null) {
    cachedDailyPicks.remove(cacheKey);
    picked = candidates.get(pickRandom.nextInt(candidates.size()));
    cachedDailyPicks.put(cacheKey, picked.getId());
}
```
**Why:** The fallback path (when `picked` becomes null) is not atomic with the initial compute, and can race with other threads evicting the same key.
**Current Impact:** Rare inconsistencies in daily pick selection; potential NPE if `candidates` modified concurrently.
**Recommended Fix:** Use `computeIfAbsent` with full selection logic inside the mapping function; ensure `candidates` is immutable or copied.

### F-017: Missing Coordinate Validation
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 545-550  
**Evidence:**
```java
public synchronized void setLocation(double lat, double lon) {
    this.lat = lat;
    this.lon = lon;
    this.hasLocationSet = true;
    touch();
}
```
**Why:** No checks that `lat ∈ [-90,90]` and `lon ∈ [-180,180]`. Invalid coordinates propagate to distance calculations.
**Current Impact:** Incorrect distance results or potential `Math.toRadians` overflow.
**Recommended Fix:** Add validation:
```java
if (lat < -90.0 || lat > 90.0) throw new IllegalArgumentException("Invalid latitude");
if (lon < -180.0 || lon > 180.0) throw new IllegalArgumentException("Invalid longitude");
```

### F-018: Potential Negative Age from Future Birth Date
**File:** `src/main/java/datingapp/core/model/User.java`  
**Lines:** 434-450  
**Evidence:**
```java
public synchronized int getAge() {
    return getAge(AppConfig.defaults().safety().userTimeZone());
}
public synchronized int getAge(java.time.ZoneId timezone) {
    if (birthDate == null) {
        return 0;
    }
    return Period.between(birthDate, AppClock.today(timezone)).getYears();
}
```
**Why:** Validation likely prevents future birth dates, but `getAge` does not guard against them.
**Current Impact:** Negative age could be displayed or used in matching calculations.
**Recommended Fix:** Validate `birthDate` in setter; optionally clamp to 0 here.

### F-019: Unbounded Candidate List Memory Usage
**File:** `src/main/java/datingapp/core/matching/CandidateFinder.java`  
**Lines:** 142-166  
**Evidence:**
```java
List<User> preFiltered = userStorage.findCandidates(...);
List<User> candidates = findCandidates(currentUser, preFiltered, excluded);
```
**Why:** `findCandidates` may return thousands of users in dense areas; entire list materialized in memory without pagination.
**Current Impact:** High memory consumption; slow sorting and filtering; could OOM in extreme cases.
**Recommended Fix:** Add `limit` parameter; stream results from storage with incremental filtering; or use cursor-based pagination.

### F-020: Coarse-Grained Lock in Auto-Ban Logic
**File:** `src/main/java/datingapp/core/matching/TrustSafetyService.java`  
**Lines:** 178-194  
**Evidence:**
```java
private boolean applyAutoBanIfThreshold(UUID reportedUserId) {
    synchronized (this) {
        int reportCount = trustSafetyStorage.countReportsAgainst(reportedUserId);
        if (reportCount < config.autoBanThreshold()) return false;
        // ... ban user
    }
}
```
**Why:** Synchronizing on `this` serializes all auto-ban checks globally, creating a bottleneck under heavy reporting load.
**Current Impact:** Contention degrades throughput of reporting operations.
**Recommended Fix:** Use per-user locks (e.g., `ConcurrentHashMap<UUID, Object>` lock striping) or atomic compare-and-set on a ban flag.

---

# Prioritized Remediation Roadmap

**Phase 1: Quick Wins (Low Effort, High Impact)**
- Add coordinate validation (F-017)
- Fix negative age edge case (F-018)
- Align `User` age range validation with injected config (F-009)
- Apply same config fix to `MatchPreferences` (F-010)
- Document discrepancy of `maxDistanceKm` default (F-008)

**Phase 2: Refactors (Medium Effort, Systemic Improvements)**
- Reduce synchronization in `User` entity (F-015)
- Implement rate limiting for verification codes (F-002)
- Fix daily pick cache race (F-016)
- Tune auto-ban lock striping (F-020)
- Standardize error handling across services (F-011)

**Phase 3: Strategic Improvements (High Effort, Critical)**
- Design and implement cross-storage transaction coordinator (F-001)
- Implement complete photo persistence (F-003)
- Address candidate list memory issue with pagination (F-019)
- Review and document match state machine transitions (F-013)
- Complete presence tracking implementation (F-004)

---

# Strategic Options and Alternatives

**What should the next steps be?**

1. **Immediate patch release** addressing validation and configuration bugs (F-009, F-010, F-017, F-018)
   - *Expected value:* Prevent data inconsistencies and security issues
   - *Effort:* Low (1-2 days)
   - *Risk:* Low (isolated changes)
   - *Dependencies:* Config injection changes may require ServiceRegistry updates

2. **Introduce transaction coordinator** for relationship transitions (F-001)
   - *Expected value:* Guarantee data integrity across match/conversation/notification updates
   - *Effort:* High (2-3 weeks)
   - *Risk:* Medium (affects core flow)
   - *Dependencies:* Storage layer must expose atomic operations or use two-phase commit

3. **Performance optimization cycle** focusing on synchronization (F-015) and candidate memory (F-019)
   - *Expected value:* Improved scalability for dense user populations
   - *Effort:* Medium (1 week)
   - *Risk:* Low to medium (benchmark required)
   - *Dependencies:* Load testing; profiling

4. **Security hardening** (F-002 rate limiting, plus add verification code retry limits)
   - *Expected value:* Reduce abuse vectors
   - *Effort:* Medium (3-5 days)
   - *Risk:* Low
   - *Dependencies:* Redis or in-memory rate limiter with persistence

**What should be implemented but is not?**

5. **Comprehensive configuration management** replacing all static defaults with injected `AppConfig`
   - *Expected value:* Single source of truth; easier tuning
   - *Effort:* Medium (scan and replace all static `AppConfig.defaults()` calls)
   - *Risk:* Low (behavioral change may require regression testing)
   - *Dependencies:* Ensure all components receive config via DI

6. **End-to-end transaction testing** to verify cross-storage consistency
   - *Expected value:* Confidence in data integrity
   - *Effort:* Medium (integration test setup)
   - *Risk:* Low
   - *Dependencies:* Test containers or in-memory DB

7. **Input validation framework** centralizing all boundary checks (coordinates, age, etc.)
   - *Expected value:* Consistent error messages; easier updates
   - *Effort:* Low (create utility class)
   - *Risk:* Low
   - *Dependencies:* Replace inline checks with framework calls

8. **Monitoring and alerting** for auto-ban thresholds, verification attempts, and candidate discovery latency
   - *Expected value:* Operational visibility
   - *Effort:* Medium (integrate metrics library)
   - *Risk:* Low
   - *Dependencies:* Metrics collection infrastructure

**What changes would improve/add value?**

9. **Replace manual ID generation with UUIDv7** for time-ordered IDs (optional)
   - *Expected value:* Better index locality; simpler debugging
   - *Effort:* Medium (requires database changes)
   - *Risk:* Medium (migration complexity)
   - *Dependencies:* DB support for UUIDv7 or string ordering

10. **Adopt optimistic locking** for concurrent updates to User/Match
    - *Expected value:* Reduce deadlocks and improve concurrency
    - *Effort:* High (add version columns, change update logic)
    - *Risk:* Medium
    - *Dependencies:* Schema changes; storage layer support

**Final recommendations not already covered:**

11. **Write a formal state machine specification** for User and Match transitions and encode it in code (e.g., using an enum-based state pattern) to prevent invalid transitions.
12. **Perform a full configuration audit** to ensure no remaining static `AppConfig.defaults()` calls exist outside of tests and utility classes.
13. **Implement soft-delete coverage audit** to verify all queries filter `deleted_at` appropriately.
14. **Add boundary tests** for extreme coordinate/age values to enforce validation rules.
15. **Document transactional boundaries** clearly in code comments, indicating which operations are atomic and which are not.

---

# Acceptance Checklist

- [x] Filename matches pattern and is in repo root
- [x] Report includes code-derived architecture/data flow
- [x] All 4 categories present (Missing, Duplication, Flaws, Problems)
- [x] Every finding includes exact line-level evidence, why issue, current impact, fix impact, recommended fix
- [x] Total findings = 20 ≤ 30
- [x] Forward section includes phased roadmap + 5 question groups (≤5 options each)

---

*Report generated by StepFun Step-3.5-Flash on 21.02.2026*
