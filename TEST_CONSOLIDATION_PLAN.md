# Test Consolidation and Optimization Plan

**Date:** January 25, 2026
**Project:** Dating App CLI - Date_Program
**Status:** ✅ **COMPLETED** (January 25, 2026)

## Final Results

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Test Files | 47 | 36 | **-11 (-23%)** |
| Main Files | 81 | 81 | 0 |
| **Total Java Files** | **128** | **117** | **-11 (-9%)** |
| Tests Passing | 464 | 535 | **+71 (+15%)** |

### Implementation Summary

| Phase | Description | Files Saved | Status |
|-------|-------------|-------------|--------|
| 1 | TestStorages.java (combine 4 InMemory*) | -3 | ✅ Complete |
| 2.1 | UserInteractionsTest (Like+Block+Report) | -2 | ✅ Complete |
| 2.2 | CoreUtilitiesTest (GeoUtils+Interest+Match) | -2 | ✅ Complete |
| 2.3 | StatsMetricsTest (PlatformStats+UserStats+Achievement) | -2 | ✅ Complete |
| 2.4 | MatchQualityServiceTest expanded (Config+InterestMatcher) | -2 | ✅ Complete |
| 2.5 | MessagingDomainTest (Message+Conversation) | -1 | ✅ Complete |
| 2.6 | TrustSafetyServiceTest (Report+Verification services) | -1 | ✅ Complete |
| 3 | EdgeCaseRegressionTest (BugInvestigation files) | -1 | ✅ Complete |

### Missing Test Coverage - Addressed

#### Critical Services - NOW TESTED ✅

| Service | Tests | File | Status |
|---------|-------|------|--------|
| StatsService | 18 | StatsServiceTest.java | ✅ NEW |
| UndoService | 20 | UndoServiceTest.java | ✅ NEW |
| ServiceRegistry | 33 | ServiceRegistryTest.java | ✅ NEW |

**Total new tests added: 71**

#### CLI Handlers - RECOMMENDATION: SKIP

User confirmed CLI was never fully functional and development moved to JavaFX UI.
Core services (now tested) are shared between CLI and UI, so service tests are sufficient.
If UI testing is needed, focus on ViewModel tests instead.

---

## Original Plan (for reference)

**Original Test Files:** 47
**Target Reduction:** ~15 files (-32%)
**Original Target Count:** ~32 test files

---

## Executive Summary

This plan comprehensively addresses test file consolidation, organization, and identifies gaps in test coverage. The goal is to:

1. **Consolidate** small test files into logical groupings using JUnit 5 `@Nested` classes
2. **Merge** test utilities into a single file
3. **Archive** or convert bug investigation tests
4. **Identify** missing test coverage for critical paths
5. **Establish** testing patterns for future development

---

## Current Test File Inventory

### By Category (47 total)

| Category            | Files | Total LOC | Notes                           |
|---------------------|-------|-----------|---------------------------------|
| Domain Record Tests | 14    | ~1,600    | Small, consolidation candidates |
| Service Tests       | 15    | ~4,500    | Core business logic             |
| Test Utilities      | 5     | ~396      | InMemory* + Factory             |
| Integration Tests   | 2     | ~800      | H2 storage tests                |
| Bug Investigation   | 2     | ~455      | Should be archived/converted    |
| CLI Tests           | 2     | ~380      | Need expansion                  |
| State/Session Tests | 5     | ~1,300    | Complex behavioral tests        |
| Config Tests        | 2     | ~230      | AppConfig + MatchQualityConfig  |

### File Size Distribution

```
Under 100 LOC (Consolidation Candidates):
├── GeoUtilsTest.java           41 lines
├── InMemoryUserStorage.java    45 lines
├── InterestTest.java           54 lines
├── MatchTest.java              58 lines
├── InMemoryBlockStorage.java   59 lines
├── InMemoryMatchStorage.java   66 lines
├── BlockTest.java              71 lines
├── PlatformStatsTest.java      96 lines
├── TestUserFactory.java        93 lines

100-150 LOC:
├── AppConfigTest.java              103 lines
├── UserAchievementTest.java        103 lines
├── ReportTest.java                 103 lines
├── VerificationServiceTest.java    111 lines
├── LikeTest.java                   118 lines
├── ProfileNoteTest.java            122 lines
├── MessageTest.java                123 lines
├── CandidateFinderTest.java        133 lines
├── InMemoryLikeStorage.java        133 lines
├── InterestMatcherTest.java        129 lines
├── MatchQualityConfigTest.java     129 lines
├── DealbreakersTest.java           148 lines

150-250 LOC:
├── UserSessionTest.java               160 lines
├── H2DailyPickViewStorageTest.java    169 lines
├── ProfilePreviewServiceTest.java     172 lines
├── UserTest.java                      177 lines
├── UserStatsTest.java                 181 lines
├── ConversationTest.java              215 lines
├── BugInvestigationTest.java          221 lines
├── ProfileCreateSelectTest.java       221 lines
├── ProfileCompletionServiceTest.java  222 lines
├── Round2BugInvestigationTest.java    234 lines
├── SessionServiceTest.java            246 lines
├── PaceCompatibilityTest.java         247 lines

250+ LOC (Keep Separate):
├── MatchStateTest.java                    256 lines
├── ReportServiceTest.java                 308 lines
├── LikerBrowserServiceTest.java           324 lines
├── RelationshipTransitionServiceTest.java 331 lines
├── MatchQualityTest.java                  338 lines
├── DealbreakersEvaluatorTest.java         359 lines
├── DailyLimitServiceTest.java             360 lines
├── DailyPickServiceTest.java              429 lines
├── MatchQualityServiceTest.java           431 lines
├── AchievementServiceTest.java            514 lines
├── H2StorageIntegrationTest.java          630 lines
├── SwipeSessionTest.java                  663 lines
├── MessagingServiceTest.java              711 lines
```

---

## PHASE 1: Test Utilities Consolidation

### 🎯 Objective
Combine all in-memory mock storage implementations into a single `TestStorages.java`

### Task 1.1: Create TestStorages.java
**Files to merge:**
- `InMemoryUserStorage.java` (45 lines)
- `InMemoryBlockStorage.java` (59 lines)
- `InMemoryMatchStorage.java` (66 lines)
- `InMemoryLikeStorage.java` (133 lines)

**Target:** `src/test/java/datingapp/core/testutil/TestStorages.java` (~310 lines)

**Implementation:**
```java
package datingapp.core.testutil;

import datingapp.core.*;
import datingapp.core.Match.MatchStorage;
import datingapp.core.User.Storage;
import datingapp.core.UserInteractions.*;
import java.time.Instant;
import java.util.*;

/**
 * Consolidated in-memory storage implementations for unit testing.
 * All implementations are simple HashMap-backed mocks with test helper methods.
 *
 * <p>Usage:
 * <pre>
 * var userStorage = new TestStorages.Users();
 * var likeStorage = new TestStorages.Likes();
 * var matchStorage = new TestStorages.Matches();
 * var blockStorage = new TestStorages.Blocks();
 * </pre>
 */
public final class TestStorages {
    private TestStorages() {} // Utility class

    /**
     * In-memory User.Storage implementation.
     */
    public static class Users implements Storage {
        private final Map<UUID, User> users = new HashMap<>();

        @Override
        public void save(User user) { users.put(user.getId(), user); }

        @Override
        public User get(UUID id) { return users.get(id); }

        @Override
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findAll() { return new ArrayList<>(users.values()); }

        // Test helpers
        public void clear() { users.clear(); }
        public int size() { return users.size(); }
    }

    /**
     * In-memory BlockStorage implementation.
     */
    public static class Blocks implements BlockStorage {
        private final Set<Block> blocks = new HashSet<>();

        @Override
        public void save(Block block) { blocks.add(block); }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.stream().anyMatch(b ->
                (b.blockerId().equals(userA) && b.blockedId().equals(userB)) ||
                (b.blockerId().equals(userB) && b.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Block block : blocks) {
                if (block.blockerId().equals(userId)) result.add(block.blockedId());
                else if (block.blockedId().equals(userId)) result.add(block.blockerId());
            }
            return result;
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int) blocks.stream().filter(b -> b.blockerId().equals(userId)).count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int) blocks.stream().filter(b -> b.blockedId().equals(userId)).count();
        }

        // Test helpers
        public void clear() { blocks.clear(); }
        public int size() { return blocks.size(); }
    }

    /**
     * In-memory MatchStorage implementation.
     */
    public static class Matches implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

        @Override public void save(Match match) { matches.put(match.getId(), match); }
        @Override public void update(Match match) { matches.put(match.getId(), match); }
        @Override public Optional<Match> get(String matchId) { return Optional.ofNullable(matches.get(matchId)); }
        @Override public boolean exists(String matchId) { return matches.containsKey(matchId); }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override public void delete(String matchId) { matches.remove(matchId); }

        // Test helpers
        public void clear() { matches.clear(); }
        public int size() { return matches.size(); }
        public List<Match> getAll() { return new ArrayList<>(matches.values()); }
    }

    /**
     * In-memory LikeStorage implementation.
     */
    public static class Likes implements LikeStorage {
        private final Map<String, Like> likes = new HashMap<>();

        @Override public boolean exists(UUID from, UUID to) { return likes.containsKey(key(from, to)); }
        @Override public void save(Like like) { likes.put(key(like.whoLikes(), like.whoGotLiked()), like); }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoLikes().equals(userId)) result.add(like.whoGotLiked());
            }
            return result;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.add(like.whoLikes());
                }
            }
            return result;
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            Map<UUID, Instant> result = new HashMap<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
        }

        @Override
        public boolean mutualLikeExists(UUID user1, UUID user2) {
            Like like1 = likes.get(key(user1, user2));
            Like like2 = likes.get(key(user2, user1));
            return like1 != null && like1.direction() == Like.Direction.LIKE
                && like2 != null && like2.direction() == Like.Direction.LIKE;
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId) && l.direction() == direction).count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoGotLiked().equals(userId) && l.direction() == direction).count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && mutualLikeExists(userId, l.whoGotLiked())).count();
        }

        @Override
        public Optional<Like> getLike(UUID from, UUID to) {
            return Optional.ofNullable(likes.get(key(from, to)));
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && l.createdAt().isAfter(startOfDay)).count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.PASS
                            && l.createdAt().isAfter(startOfDay)).count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.values().removeIf(like -> like.id().equals(likeId));
        }

        // Test helpers
        public void clear() { likes.clear(); }
        public int size() { return likes.size(); }

        private String key(UUID from, UUID to) { return from + "->" + to; }
    }
}
```

**Update imports in all test files:**
```java
// OLD
import datingapp.core.testutil.InMemoryUserStorage;
import datingapp.core.testutil.InMemoryLikeStorage;

// NEW
import datingapp.core.testutil.TestStorages;
// Usage: var userStorage = new TestStorages.Users();
```

**Delete after migration:**
- `src/test/java/datingapp/core/testutil/InMemoryUserStorage.java`
- `src/test/java/datingapp/core/testutil/InMemoryBlockStorage.java`
- `src/test/java/datingapp/core/testutil/InMemoryMatchStorage.java`
- `src/test/java/datingapp/core/testutil/InMemoryLikeStorage.java`

**Keep:** `TestUserFactory.java` (different purpose - factory, not storage)

**Savings:** -3 files (4→1)

---

## PHASE 2: Domain Record Test Consolidation

### 🎯 Objective
Group small domain entity tests using JUnit 5 `@Nested` classes

---

### Task 2.1: Create UserInteractionsTest.java
**Combine:** Like, Block, Report record tests (all part of `UserInteractions`)

**Files to merge:**
- `LikeTest.java` (118 lines)
- `BlockTest.java` (71 lines)
- `ReportTest.java` (103 lines)

**Target:** `src/test/java/datingapp/core/UserInteractionsTest.java` (~300 lines)

**Implementation:**
```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;
import datingapp.core.UserInteractions.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * Tests for UserInteractions domain records (Like, Block, Report).
 */
@DisplayName("UserInteractions Domain Records")
class UserInteractionsTest {

    @Nested
    @DisplayName("Like Record")
    class LikeRecordTests {
        @Test @DisplayName("Cannot like yourself")
        void cannotLikeSelf() { /* from LikeTest */ }

        @Test @DisplayName("Like creation succeeds with different users")
        void likeCreationSucceeds() { /* from LikeTest */ }

        @Test @DisplayName("Like IDs are unique")
        void likeIdsAreUnique() { /* from LikeTest */ }

        @Test @DisplayName("Direction enum contains exactly LIKE and PASS")
        void directionEnumValues() { /* from LikeTest */ }

        // ... all tests from LikeTest.java
    }

    @Nested
    @DisplayName("Block Record")
    class BlockRecordTests {
        @Test @DisplayName("Cannot block yourself")
        void cannotBlockSelf() { /* from BlockTest */ }

        @Test @DisplayName("Block creation succeeds with different users")
        void blockCreationSucceeds() { /* from BlockTest */ }

        // ... all tests from BlockTest.java
    }

    @Nested
    @DisplayName("Report Record")
    class ReportRecordTests {
        @Test @DisplayName("Cannot report yourself")
        void cannotReportSelf() { /* from ReportTest */ }

        @Test @DisplayName("Report creation succeeds with valid data")
        void reportCreationSucceeds() { /* from ReportTest */ }

        @Test @DisplayName("All report reasons are valid")
        void allReasonsAreValid() { /* from ReportTest */ }

        // ... all tests from ReportTest.java
    }
}
```

**Delete:**
- `src/test/java/datingapp/core/LikeTest.java`
- `src/test/java/datingapp/core/BlockTest.java`
- `src/test/java/datingapp/core/ReportTest.java`

**Savings:** -2 files (3→1)

---

### Task 2.2: Create CoreUtilitiesTest.java
**Combine:** Small utility/enum tests

**Files to merge:**
- `GeoUtilsTest.java` (41 lines) - Geographic distance calculation
- `InterestTest.java` (54 lines) - Interest enum tests
- `MatchTest.java` (58 lines) - Match ID generation tests

**Target:** `src/test/java/datingapp/core/CoreUtilitiesTest.java` (~160 lines)

**Implementation:**
```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;
import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.Preferences.Interest;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * Tests for core utility classes and enums.
 */
@DisplayName("Core Utilities")
class CoreUtilitiesTest {

    @Nested
    @DisplayName("GeoUtils - Distance Calculations")
    class GeoUtilsTests {
        @Test @DisplayName("Distance between same point is zero")
        void samePointDistanceIsZero() { /* from GeoUtilsTest */ }

        @Test @DisplayName("Distance Tel Aviv to Jerusalem is approximately 54km")
        void telAvivToJerusalemDistance() { /* from GeoUtilsTest */ }

        @Test @DisplayName("Distance New York to London is approximately 5570km")
        void newYorkToLondonDistance() { /* from GeoUtilsTest */ }
    }

    @Nested
    @DisplayName("Interest Enum")
    class InterestEnumTests {
        @Test @DisplayName("byCategory returns correct interests for OUTDOORS")
        void byCategoryOutdoors() { /* from InterestTest */ }

        @Test @DisplayName("All interests have display names")
        void allInterestsHaveDisplayNames() { /* from InterestTest */ }

        @Test @DisplayName("All interests have categories")
        void allInterestsHaveCategories() { /* from InterestTest */ }
    }

    @Nested
    @DisplayName("Match ID Generation")
    class MatchIdTests {
        @Test @DisplayName("Match ID is deterministic regardless of UUID order")
        void matchIdIsDeterministic() { /* from MatchTest */ }

        @Test @DisplayName("userA is always lexicographically smaller UUID")
        void userAIsAlwaysSmaller() { /* from MatchTest */ }

        @Test @DisplayName("Cannot create match with same user")
        void cannotMatchWithSelf() { /* from MatchTest */ }

        @Test @DisplayName("getOtherUser returns correct user")
        void getOtherUserReturnsCorrectly() { /* from MatchTest */ }
    }
}
```

**Delete:**
- `src/test/java/datingapp/core/GeoUtilsTest.java`
- `src/test/java/datingapp/core/InterestTest.java`
- `src/test/java/datingapp/core/MatchTest.java`

**Savings:** -2 files (3→1)

---

### Task 2.3: Create StatsMetricsTest.java
**Combine:** Stats and metrics record tests

**Files to merge:**
- `PlatformStatsTest.java` (96 lines)
- `UserAchievementTest.java` (103 lines)
- `UserStatsTest.java` (181 lines)

**Target:** `src/test/java/datingapp/core/StatsMetricsTest.java` (~390 lines)

**Implementation:**
```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Stats.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * Tests for statistics and metrics domain records.
 */
@DisplayName("Stats and Metrics Records")
class StatsMetricsTest {

    @Nested
    @DisplayName("PlatformStats Record")
    class PlatformStatsTests {
        // ... all tests from PlatformStatsTest.java
    }

    @Nested
    @DisplayName("UserStats Record")
    class UserStatsTests {
        // ... all tests from UserStatsTest.java
    }

    @Nested
    @DisplayName("UserAchievement Record")
    class UserAchievementTests {
        // ... all tests from UserAchievementTest.java
    }
}
```

**Delete:**
- `src/test/java/datingapp/core/PlatformStatsTest.java`
- `src/test/java/datingapp/core/UserAchievementTest.java`
- `src/test/java/datingapp/core/UserStatsTest.java`

**Savings:** -2 files (3→1)

---

### Task 2.4: Merge into MatchQualityServiceTest.java
**Combine:** MatchQuality-related tests into existing service test

**Files to merge:**
- `MatchQualityConfigTest.java` (129 lines) - nested Config record tests
- `InterestMatcherTest.java` (129 lines) - nested InterestMatcher tests

**Target:** Expand `MatchQualityServiceTest.java` (currently 431 lines) with @Nested groups

**Note:** Keep `MatchQualityTest.java` (338 lines) separate as it's substantial.

**Implementation:**
```java
// Add to existing MatchQualityServiceTest.java:

@Nested
@DisplayName("MatchQualityConfig (nested record)")
class ConfigTests {
    @Test @DisplayName("Default config has sensible weights")
    void defaultConfigHasSensibleWeights() { /* from MatchQualityConfigTest */ }
    // ... all tests from MatchQualityConfigTest.java
}

@Nested
@DisplayName("InterestMatcher (nested utility)")
class InterestMatcherTests {
    @Test @DisplayName("Exact matches score highest")
    void exactMatchesScoreHighest() { /* from InterestMatcherTest */ }
    // ... all tests from InterestMatcherTest.java
}
```

**Delete:**
- `src/test/java/datingapp/core/MatchQualityConfigTest.java`
- `src/test/java/datingapp/core/InterestMatcherTest.java`

**Savings:** -2 files (3→1)

---

### Task 2.5: Create MessagingDomainTest.java
**Combine:** Messaging record tests

**Files to merge:**
- `MessageTest.java` (123 lines)
- `ConversationTest.java` (215 lines)

**Target:** `src/test/java/datingapp/core/MessagingDomainTest.java` (~350 lines)

**Implementation:**
```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;
import datingapp.core.Messaging.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * Tests for Messaging domain records (Message, Conversation).
 */
@DisplayName("Messaging Domain Records")
class MessagingDomainTest {

    @Nested
    @DisplayName("Message Record")
    class MessageTests {
        // ... all tests from MessageTest.java
    }

    @Nested
    @DisplayName("Conversation Record")
    class ConversationTests {
        // ... all tests from ConversationTest.java
    }
}
```

**Delete:**
- `src/test/java/datingapp/core/MessageTest.java`
- `src/test/java/datingapp/core/ConversationTest.java`

**Savings:** -1 file (2→1)

---

### Task 2.6: Create TrustSafetyServiceTest.java
**Combine:** Trust and safety service tests

**Files to merge:**
- `ReportServiceTest.java` (308 lines) - Report handling tests
- `VerificationServiceTest.java` (111 lines) - Verification tests

**Target:** `src/test/java/datingapp/core/TrustSafetyServiceTest.java` (~430 lines)

**Rationale:** These services were consolidated into `TrustSafetyService` in Phase 2, so tests should match.

**Implementation:**
```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

/**
 * Tests for TrustSafetyService (reports and verification).
 */
@DisplayName("TrustSafetyService")
class TrustSafetyServiceTest {

    @Nested
    @DisplayName("Report Handling")
    class ReportTests {
        // ... all tests from ReportServiceTest.java
    }

    @Nested
    @DisplayName("User Verification")
    class VerificationTests {
        // ... all tests from VerificationServiceTest.java
    }
}
```

**Delete:**
- `src/test/java/datingapp/core/ReportServiceTest.java`
- `src/test/java/datingapp/core/VerificationServiceTest.java`

**Savings:** -1 file (2→1)

---

## PHASE 3: Bug Investigation Test Conversion

### 🎯 Objective
Convert bug investigation tests to regression tests or archive them

### Task 3.1: Archive/Convert Bug Investigation Tests
**Current files:**
- `BugInvestigationTest.java` (221 lines)
- `Round2BugInvestigationTest.java` (234 lines)

**Analysis:**
These tests document edge cases and potential bugs. They should be:
1. **Converted** to proper regression tests in relevant test files
2. **Archived** if the bugs were fixed

**Action Plan:**
```
BugInvestigationTest.java contains:
├── BUG 1: User.getInterestedIn() empty set → Move to UserTest.java
├── BUG 2: User at 0,0 coordinates → Move to UserTest.java
├── BUG 3: Age calculation edge cases → Move to UserTest.java
├── BUG 4: Match unmatch race condition → Move to MatchStateTest.java
├── BUG 5: CandidateFinder edge cases → Move to CandidateFinderTest.java

Round2BugInvestigationTest.java:
├── Analyze content and distribute to appropriate test files
```

**Implementation:**
1. Read each bug test
2. Add as `@Nested` class in the appropriate existing test file
3. Rename with `@DisplayName("Regression: [bug description]")`
4. Delete original bug investigation files

**Savings:** -2 files (move tests to existing files)

---

## Phase Summary

| Phase | Task              | Files Merged                          | New File                         | Savings |
|-------|-------------------|---------------------------------------|----------------------------------|---------|
| 1     | Test Utilities    | 4 InMemory*                           | TestStorages                     | -3      |
| 2.1   | UserInteractions  | Like, Block, Report                   | UserInteractionsTest             | -2      |
| 2.2   | Core Utilities    | GeoUtils, Interest, Match             | CoreUtilitiesTest                | -2      |
| 2.3   | Stats/Metrics     | PlatformStats, UserStats, Achievement | StatsMetricsTest                 | -2      |
| 2.4   | MatchQuality      | Config, InterestMatcher               | MatchQualityServiceTest (expand) | -2      |
| 2.5   | Messaging         | Message, Conversation                 | MessagingDomainTest              | -1      |
| 2.6   | TrustSafety       | Report, Verification services         | TrustSafetyServiceTest           | -1      |
| 3     | Bug Investigation | 2 files                               | Distributed to existing          | -2      |

**Total Savings: -15 files (47 → 32)**

---

## Implementation Order

### Recommended Sequence (Lowest Risk First)

| Order | Phase | Description                   | Risk      | Effort |
|-------|-------|-------------------------------|-----------|--------|
| 1     | 1     | Test Utilities → TestStorages | 🟢 Low    | Medium |
| 2     | 2.1   | UserInteractions records      | 🟢 Low    | Low    |
| 3     | 2.2   | Core utilities                | 🟢 Low    | Low    |
| 4     | 2.3   | Stats/Metrics records         | 🟢 Low    | Low    |
| 5     | 2.5   | Messaging records             | 🟢 Low    | Low    |
| 6     | 2.4   | MatchQuality expansion        | 🟡 Medium | Medium |
| 7     | 2.6   | TrustSafety services          | 🟡 Medium | Medium |
| 8     | 3     | Bug investigation conversion  | 🟡 Medium | High   |

---

## Validation Checklist

### Per-Phase Validation
```bash
# After each phase:
mvn test                     # All tests pass
mvn spotless:apply           # Code formatted
mvn checkstyle:check         # Style compliance

# Verify imports updated:
rg "import.*InMemoryUserStorage" src/test  # Should return 0 results after Phase 1
rg "import.*LikeTest" src/test             # Should return 0 results after Phase 2.1
```

### Final Validation
```bash
mvn verify                   # Full build + quality checks
Get-ChildItem src/test/java -Recurse -Filter *.java | Measure-Object  # Should be ~32
```

---

## Test Coverage Gap Analysis

### ❌ MISSING: Service Tests

| Service        | Current Test? | Priority  | Notes                                                  |
|----------------|---------------|-----------|--------------------------------------------------------|
| `StatsService` | ❌ None        | 🔴 High   | Core analytics service                                 |
| `UndoService`  | ❌ None        | 🔴 High   | Critical user-facing feature                           |
| `DailyService` | ⚠️ Partial     | 🟡 Medium | Has legacy DailyLimitServiceTest, DailyPickServiceTest |

### ❌ MISSING: CLI Handler Tests

| Handler               | Current Test? | Priority  | Notes                        |
|-----------------------|---------------|-----------|------------------------------|
| `MatchingHandler`     | ❌ None        | 🔴 High   | Core user interaction        |
| `MessagingHandler`    | ❌ None        | 🔴 High   | Critical feature             |
| `SafetyHandler`       | ❌ None        | 🟡 Medium | Safety features              |
| `StatsHandler`        | ❌ None        | 🟢 Low    | Read-only display            |
| `ProfileHandler`      | ⚠️ Partial     | 🟡 Medium | Only ProfileCreateSelectTest |
| `RelationshipHandler` | ❌ None        | 🟡 Medium | State transitions            |
| `LikerBrowserHandler` | ❌ None        | 🟢 Low    | Browse functionality         |

### ❌ MISSING: Storage Tests

| Storage                 | Current Test? | Priority  | Notes                              |
|-------------------------|---------------|-----------|------------------------------------|
| `H2UserStorage`         | ⚠️ Partial     | 🟡 Medium | In H2StorageIntegrationTest        |
| `H2LikeStorage`         | ⚠️ Partial     | 🟡 Medium | In H2StorageIntegrationTest        |
| `H2MatchStorage`        | ⚠️ Partial     | 🟡 Medium | In H2StorageIntegrationTest        |
| `H2MessageStorage`      | ❌ None        | 🟡 Medium | Complex queries                    |
| `H2ConversationStorage` | ❌ None        | 🟢 Low    | Simpler CRUD                       |
| Other H2* storages      | ⚠️ Partial     | 🟢 Low    | Basic coverage in integration test |

### ❌ MISSING: UI Tests

| Category    | Status | Priority  | Notes                 |
|-------------|--------|-----------|-----------------------|
| ViewModels  | ❌ None | 🟡 Medium | Business logic in VMs |
| Controllers | ❌ None | 🟢 Low    | Mostly delegation     |
| Components  | ❌ None | 🟢 Low    | UI rendering          |

---

## Suggested New Tests

### Priority 1: Critical Missing Tests (Create First)

#### 1.1 StatsServiceTest.java (~200 LOC)
```java
@DisplayName("StatsService")
class StatsServiceTest {
    @Nested
    @DisplayName("User Statistics")
    class UserStats {
        @Test void calculatesSwipeRatioCorrectly() {}
        @Test void tracksMatchConversionRate() {}
        @Test void aggregatesWeeklyActivity() {}
    }

    @Nested
    @DisplayName("Platform Statistics")
    class PlatformStats {
        @Test void countsActiveUsers() {}
        @Test void calculatesAverageResponseTime() {}
    }
}
```

#### 1.2 UndoServiceTest.java (~150 LOC)
```java
@DisplayName("UndoService")
class UndoServiceTest {
    @Nested
    @DisplayName("Undo Like")
    class UndoLike {
        @Test void undoesLikeWithin30Seconds() {}
        @Test void failsAfter30SecondWindow() {}
        @Test void tracksPendingUndoState() {}
    }

    @Nested
    @DisplayName("Undo Pass")
    class UndoPass {
        @Test void undoesPassSuccessfully() {}
        @Test void restoresUserToCandidatePool() {}
    }
}
```

### Priority 2: CLI Handler Tests

#### 2.1 MatchingHandlerTest.java (~250 LOC)
```java
@DisplayName("MatchingHandler CLI")
class MatchingHandlerTest {
    @Nested
    @DisplayName("Swiping Flow")
    class SwipingFlow {
        @Test void displaysCandidateProfile() {}
        @Test void handlesLikeInput() {}
        @Test void handlesPassInput() {}
        @Test void showsMatchCelebration() {}
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @Test void handlesNoCandidatesGracefully() {}
        @Test void handlesDailyLimitReached() {}
        @Test void handlesInvalidInput() {}
    }
}
```

#### 2.2 MessagingHandlerTest.java (~200 LOC)
```java
@DisplayName("MessagingHandler CLI")
class MessagingHandlerTest {
    @Nested
    @DisplayName("Conversation View")
    class ConversationView {
        @Test void listsActiveConversations() {}
        @Test void displaysUnreadCounts() {}
    }

    @Nested
    @DisplayName("Message Sending")
    class MessageSending {
        @Test void sendsTextMessage() {}
        @Test void validatesMessageLength() {}
    }
}
```

### Priority 3: Integration Test Expansion

#### 3.1 H2MessagingStorageTest.java (~180 LOC)
```java
@DisplayName("H2 Messaging Storage Integration")
class H2MessagingStorageTest {
    @Nested
    @DisplayName("Message Persistence")
    class MessagePersistence {
        @Test void savesAndRetrievesMessages() {}
        @Test void queriesMessagesByConversation() {}
        @Test void paginatesMessageHistory() {}
    }

    @Nested
    @DisplayName("Conversation Persistence")
    class ConversationPersistence {
        @Test void createsNewConversation() {}
        @Test void updatesLastMessageTimestamp() {}
    }
}
```

---

## Test Quality Improvements

### 1. Add `@Tag` for Test Categories
```java
@Tag("unit")        // Fast, no I/O
@Tag("integration") // Database, slower
@Tag("slow")        // Performance tests
@Tag("flaky")       // Known intermittent failures
```

### 2. Add Test Timeouts
```java
@Timeout(5)  // Fail if test takes > 5 seconds
class SlowServiceTest { }
```

### 3. Parameterized Tests for Enums
```java
@ParameterizedTest
@EnumSource(Report.Reason.class)
@DisplayName("All report reasons are valid")
void allReportReasonsWork(Report.Reason reason) {
    Report report = Report.create(userId, targetId, reason, null);
    assertEquals(reason, report.reason());
}
```

### 4. AssertAll for Multiple Assertions
```java
@Test
void userCreationSetsAllFields() {
    User user = new User(id, "Alice");

    assertAll("User creation",
        () -> assertEquals(id, user.getId()),
        () -> assertEquals("Alice", user.getName()),
        () -> assertEquals(User.State.INCOMPLETE, user.getState())
    );
}
```

---

## Test Organization Best Practices

### File Naming Convention
```
{ClassName}Test.java           - Unit tests for a class
{Feature}IntegrationTest.java  - Integration tests
{Feature}E2ETest.java          - End-to-end tests (if any)
```

### Package Structure
```
src/test/java/datingapp/
├── core/
│   ├── testutil/              # Test utilities & mocks
│   │   ├── TestStorages.java  # Consolidated in-memory storages
│   │   └── TestUserFactory.java
│   ├── CoreUtilitiesTest.java
│   ├── UserInteractionsTest.java
│   ├── StatsMetricsTest.java
│   ├── MessagingDomainTest.java
│   ├── UserTest.java
│   ├── MatchStateTest.java
│   ├── DealbreakersTest.java
│   ├── DealbreakersEvaluatorTest.java
│   ├── ...ServiceTest.java    # Service tests
│   └── ...
├── storage/
│   ├── H2StorageIntegrationTest.java
│   └── H2DailyPickViewStorageTest.java
└── cli/
    ├── ProfileCreateSelectTest.java
    ├── MatchingHandlerTest.java  # New
    └── MessagingHandlerTest.java # New
```

---

## Expected Final Test Structure (32 files)

```
src/test/java/datingapp/
├── core/ (26 files)
│   ├── testutil/ (2 files)
│   │   ├── TestStorages.java      # Phase 1: 4→1
│   │   └── TestUserFactory.java   # Keep
│   ├── UserInteractionsTest.java  # Phase 2.1: 3→1
│   ├── CoreUtilitiesTest.java     # Phase 2.2: 3→1
│   ├── StatsMetricsTest.java      # Phase 2.3: 3→1
│   ├── MessagingDomainTest.java   # Phase 2.5: 2→1
│   ├── TrustSafetyServiceTest.java # Phase 2.6: 2→1
│   ├── MatchQualityServiceTest.java # Phase 2.4: expanded
│   ├── MatchQualityTest.java      # Keep (substantial)
│   ├── MatchStateTest.java        # Keep
│   ├── UserTest.java              # Keep + bug regressions
│   ├── DealbreakersTest.java      # Keep
│   ├── DealbreakersEvaluatorTest.java # Keep
│   ├── ProfileNoteTest.java       # Keep
│   ├── ProfileCompletionServiceTest.java # Keep
│   ├── ProfilePreviewServiceTest.java # Keep
│   ├── CandidateFinderTest.java   # Keep + bug regressions
│   ├── MatchingServiceTest.java   # Keep
│   ├── MessagingServiceTest.java  # Keep (large)
│   ├── AchievementServiceTest.java # Keep (large)
│   ├── SessionServiceTest.java    # Keep
│   ├── SwipeSessionTest.java      # Keep (large)
│   ├── RelationshipTransitionServiceTest.java # Keep
│   ├── LikerBrowserServiceTest.java # Keep
│   ├── DailyLimitServiceTest.java # Keep (legacy name)
│   ├── DailyPickServiceTest.java  # Keep (legacy name)
│   ├── AppConfigTest.java         # Keep
│   └── PaceCompatibilityTest.java # Keep
├── storage/ (2 files)
│   ├── H2StorageIntegrationTest.java # Keep
│   └── H2DailyPickViewStorageTest.java # Keep
└── cli/ (2 files)
    ├── ProfileCreateSelectTest.java # Keep
    └── UserSessionTest.java       # Keep (CLI session)
```

**Total: 32 files (down from 47, -15 files, -32%)**

---

## Conclusion

This test consolidation plan achieves:

1. **-15 test files** through logical grouping with `@Nested` classes
2. **Improved organization** with clear test categories
3. **Maintained test coverage** - no tests are deleted, only reorganized
4. **Identified gaps** for future test development
5. **Established patterns** for consistent test structure

### Immediate Actions
1. ✅ Create `TestStorages.java` (Phase 1)
2. ✅ Create domain record test groups (Phase 2.1-2.6)
3. ✅ Convert bug investigation tests (Phase 3)

### Future Actions (Coverage Improvement)
1. 🔴 Create `StatsServiceTest.java`
2. 🔴 Create `UndoServiceTest.java`
3. 🟡 Create CLI handler tests
4. 🟢 Expand storage integration tests

---

**Generated:** January 25, 2026
**Author:** GitHub Copilot (Claude Opus 4.5)
**Status:** Ready for Implementation
