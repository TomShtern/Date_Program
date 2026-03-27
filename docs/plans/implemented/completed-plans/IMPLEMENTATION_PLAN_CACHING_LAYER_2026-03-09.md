# Implementation Plan: Missing Persistent Caching Layer

**Status:** ✅ **COMPLETED** (2026-03-09)

**Source Report:** `Generated_Report_Generated_By_GLM5_21.02.2026.md` (Findings F-002, F-011)

## 1. Goal Description
The application lacks a formalized caching strategy for highly active read paths. `CandidateFinder.findCandidatesForUser()` always executes an expensive SQL `findCandidates` query and subsequent in-memory Cartesian-like filtering on every invocation.
Simultaneously, `RecommendationService` relies on a rudimentary `LinkedHashMap` for caching daily picks, which gets wiped out on JVM restarts and suffers from potential key collision/filtering race conditions.

**Objective:**
Introduce a persistent, TTL-based caching layer for `CandidateFinder` and `RecommendationService` using `Caffeine` (or an equivalent robust caching mechanism adapted to `AppSession`) to dramatically reduce database read load and guarantee continuity across application restarts.

## 2. Proposed Changes

### `pom.xml`

#### [MODIFY] `pom.xml`
- Add the Caffeine caching dependency if not already present.
  ```xml
  <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
      <version>3.1.8</version>
  </dependency>
  ```

### `datingapp.core.matching`

#### [MODIFY] `CandidateFinder.java`
- Add a caffeine cache instance for candidate filtering results. Filtering queries are highly user-specific and transient.
  ```java
  private final Cache<UUID, List<User>> candidateCache = Caffeine.newBuilder()
      .expireAfterWrite(5, TimeUnit.MINUTES) // Short TTL, candidate availability changes rapidly
      .maximumSize(500)
      .build();
  ```
- Modify `findCandidatesForUser(User currentUser)` to check the cache first.
  ```java
  public List<User> findCandidatesForUser(User currentUser) {
      return candidateCache.get(currentUser.getId(), key -> {
          // Excecute the expensive DB + Stream filtering operation
          return executeExpensiveCandidateSearch(currentUser);
      });
  }
  ```
- Important: In `MatchingService.processSwipe()` and `TrustSafetyService.block()`, add logic to *invalidate* a user's `CandidateFinder` cache entry (`candidateCache.invalidate(userId)`) since their available candidate pool has definitively mutated.

#### [MODIFY] `RecommendationService.java`
- **Address F-002 & F-011**: Replace the `LinkedHashMap` `cachedDailyPicks`.
- Establish persistent daily picks tracking by creating a new `daily_picks` table in H2 (or leveraging an existing one) via `AnalyticsStorage`. Jdbi will handle fetching the cached pick for the day.
- Modify `getDailyPick(User seeker)` to:
  1. Check `AnalyticsStorage.getDailyPickForUser(userId, today)`. If it exists, return it.
  2. If it does not exist, fetch candidates via `CandidateFinder`.
  3. Deterministically pick a candidate using the seed logic (already existing).
  4. Save the pick to `AnalyticsStorage`: `analyticsStorage.saveDailyPick(userId, picked.getId(), today)`.
- **Validation Fallback:** If the db-cached pick is fetched, but that user was subsequently `BLOCKED` or set to `INACTIVE`, the method must detect this, gracefully select a *new* pick, update the storage, and return the new pick.

### `datingapp.storage`

#### [MODIFY] `AnalyticsStorage.java` & `JdbiMetricsStorage.java`
- Add `Optional<UUID> getDailyPickUser(UUID userId, LocalDate date);`
- Add `void saveDailyPick(UUID userId, UUID pickedId, LocalDate date);`
- Add the corresponding SQL (e.g., `INSERT INTO daily_picks ...`).

## 3. Verification Plan

### Automated Tests
1. **Cache Hit Test:** Add a test to `CandidateFinderTest.java`. Mock `UserStorage.findCandidates()`. Call `findCandidatesForUser()` twice. Verify with Mockito (if applicable, or via `TestStorages` instrumentation markers) that the underlying storage was only queried exactly once.
2. **Invalidation Test:** Create a test verifying that calling `MatchingService.processSwipe` immediately invalidates the seeker's `CandidateFinder` cache so the next call queries the database accurately excluding the swiped user.
3. **Persistence Test:** Add a test to `RecommendationServiceTest.java` that generates a daily pick, simulates a simulated "JVM Restart" (by building a fresh `RecommendationService` instance sharing the same `TestStorages` instance), and assert `getDailyPick()` returns the identical user instantaneously without computing new lists.

### Manual Verification
1. Launch the app (`mvn javafx:run`).
2. Traverse heavily between the "Dashboard", "Matches", and "Matching" tabs. Navigating back to the "Matching" tab should instantly render the next candidate profile without noticeable DB latency.
3. Shut down the application entirely and reboot. Go to the "Dashboard". Ensure the "Daily Pick" presented is exactly the same user as before the shutdown.

## Completion Notes (2026-03-09)

- ✅ Implemented robust TTL-based candidate caching in `CandidateFinder` using an internal concurrent cache (`CacheEntry` + 5-minute TTL) with explicit invalidation APIs:
  - `invalidateCacheFor(UUID userId)`
  - `clearCache()`
- ✅ Added cache invalidation wiring on mutation paths:
  - `MatchingService.processSwipe(...)` invalidates seeker + candidate cache entries.
  - `TrustSafetyService.block(...)` flow invalidates blocker + blocked cache entries (via `setCandidateFinder(...)` wiring in `StorageFactory`).
- ✅ Replaced ephemeral daily-pick `LinkedHashMap` behavior in `DefaultDailyPickService` with persistent `AnalyticsStorage`-backed daily-pick persistence.
- ✅ Added storage API and JDBI implementation for persisted daily picks:
  - `AnalyticsStorage.getDailyPickUser(UUID, LocalDate)`
  - `AnalyticsStorage.saveDailyPickUser(UUID, UUID, LocalDate)`
- ✅ Added append-only schema migration `V7` in `MigrationRunner` introducing `daily_picks` table + index + foreign keys.
- ✅ Implemented fallback behavior: if persisted pick is no longer valid in candidate set (e.g., inactive/blocked/removed), service selects a new deterministic pick and updates persistence.

## Verification Executed

- ✅ Added/updated tests:
  - `CandidateFinderTest#findCandidatesForUserCachesAndInvalidates`
  - `DailyPickServiceTest#getDailyPick_replacesPersistedPickWhenCachedUserBecomesUnavailable`
  - `DailyPickServiceTest#getDailyPick_persistsAcrossServiceRestart`
  - `SchemaInitializerTest` migration assertions for `DAILY_PICKS`
- ✅ Full quality gate passed: `mvn spotless:apply verify` (BUILD SUCCESS; tests/checkstyle/PMD/JaCoCo all green).
