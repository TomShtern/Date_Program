# Core Reorganization & Consolidation Plan (Hybrid Approach)

**Status:** AGGRESSIVE CONSOLIDATION (2026-01-24)
**Goal:** Package reorganization + maximum file count reduction
**Current:** 61 Java files in flat `core/` structure
**Target:** ~35 files in organized package structure (43% reduction)
**Strategy:** Reorganize into packages + aggressively consolidate related types
**Max file size:** 1000 lines

**Version Control:** This plan does NOT include git/version control directives. Handle version control according to your own discretion.

---

## Audit Findings Summary

**Original plan rejected** due to:
- ❌ Storage interfaces nested in services (architecture violation)
- ❌ Mega-files (1000+ lines)
- ❌ Mixing architectural layers (models + interfaces + services)
- ❌ Services with different concerns consolidated together

**See:** `docs/core-consolidation-audit-report.md` for full analysis

---

## Scope & Constraints

### Non-Negotiables
- ✅ No behavior changes; refactor structure only
- ✅ Keep `core/` free of framework/database imports
- ✅ No files >1000 lines
- ✅ Java rule: one public top-level type per file (use nested types for consolidation)
- ✅ Update all references, tests, storage implementations, and `ServiceRegistry` after each step

> **Note:** Storage interface consolidation is now ALLOWED (architectural boundary relaxed for aggressive file reduction).

### Success Metrics (AGGRESSIVE - 2026-01-24)
- **File count:** 61 → ~35 files (26 file reduction, **43% decrease**)
- **Organization:** Flat structure → 4 organized packages
- **Max file size:** <1000 lines per file
- **Maintainability:** Dramatically improved navigability with cohesive groupings

> **Consolidation Tiers:**
> | Tier | Category | Files Reduced |
> |------|----------|---------------|
> | Original | Exceptions, Configs, Utilities | -6 files |
> | Tier 1 | Domain Models | -6 files |
> | Tier 2 | Services | -4 files |
> | Tier 3 | Storage Interfaces | -10 files |
> | **Total** | | **-26 files** |

---

## New Package Structure (AGGRESSIVE)

```
core/
├── model/              # Domain models (AGGRESSIVE consolidation)
│   ├── User.java                    # Keep separate (595 lines, central entity)
│   ├── Match.java                   # Keep separate (259 lines, complex state machine)
│   ├── SwipeSession.java            # Keep separate (240 lines, complex state)
│   ├── UserInteractions.java        # NEW: Like + Block + Report (141 lines)
│   ├── Messaging.java               # NEW: Message + Conversation (319 lines)
│   ├── Stats.java                   # NEW: UserStats + PlatformStats (199 lines)
│   ├── Preferences.java             # NEW: Interest + Lifestyle enums (232 lines)
│   ├── Social.java                  # NEW: FriendRequest + Notification (55 lines)
│   ├── Achievement.java             # Consolidated: enum + UserAchievement (117 lines)
│   ├── Dealbreakers.java            # Keep separate (169 lines, complex filters)
│   ├── PacePreferences.java         # Keep separate (89 lines, distinct concept)
│   ├── MatchQuality.java            # Keep separate (126 lines)
│   └── ProfileNote.java             # Keep separate (79 lines)
│   (13 files, down from 21)
│
├── service/            # Business logic (AGGRESSIVE consolidation)
│   ├── MatchingService.java         # EXPANDED: + LikerBrowserService (161 lines)
│   ├── MessagingService.java        # Keep separate (254 lines)
│   ├── RelationshipTransitionService.java  # + TransitionValidationException (189 lines)
│   ├── MatchQualityService.java     # MEGA: + Config + InterestMatcher + PaceCompatibility (673 lines)
│   ├── CandidateFinder.java         # + Service interface + GeoUtils (205 lines)
│   ├── DealbreakersEvaluator.java   # Keep separate (175 lines, widely used)
│   ├── AchievementService.java      # Keep separate (269 lines)
│   ├── TrustSafetyService.java      # NEW: ReportService + VerificationService (135 lines)
│   ├── SessionService.java          # Keep separate (181 lines)
│   ├── StatsService.java            # Keep separate (176 lines)
│   ├── DailyService.java            # NEW: DailyLimitService + DailyPickService (278 lines)
│   ├── ProfileService.java          # NEW: ProfilePreviewService + ProfileCompletionService (476 lines)
│   └── UndoService.java             # Keep separate (209 lines)
│   (13 files, down from 18)
│
├── storage/            # Storage interfaces (AGGRESSIVE consolidation)
│   ├── UserStorages.java            # NEW: User + UserStats + UserAchievement (72 lines)
│   ├── MatchingStorages.java        # NEW: Match + Like + Block (132 lines)
│   ├── MessagingStorages.java       # NEW: Conversation + Message (102 lines)
│   ├── SocialStorages.java          # NEW: Report + Notification + FriendRequest (65 lines)
│   ├── SessionStorages.java         # NEW: SwipeSession + DailyPick (104 lines)
│   └── ProfileStorages.java         # NEW: ProfileNote + ProfileView + PlatformStats (112 lines)
│   (6 files, down from 16)
│
└── util/               # Utilities
    ├── AppConfig.java               # Keep separate (167 lines)
    └── ServiceRegistry.java         # Keep separate (377 lines)
    (2 files, down from 3 - GeoUtils moved to CandidateFinder)
```

**Total:** 61 files → **~34 files** (44% reduction, -27 files)

---

## Pre-Consolidation Audit Results (2026-01-24)

> **With 1000-line limit (user preference), size-based blocks are removed.**
>
> | Original Proposal | Status | Combined Size | Notes |
> |-------------------|--------|---------------|-------|
> | `MatchQualityConfig` → `MatchQualityService` | ✅ **UNBLOCKED** | 472 lines | Single-consumer, safe |
> | `InterestMatcher` → `MatchQualityService` | ✅ **UNBLOCKED** | 589 lines | Multi-consumer but nestable; CLI imports update required |
> | `GeoUtils` → `CandidateFinder` | ✅ **NEW** | 205 lines | Multi-consumer but nestable; import updates required |
> | `ProfileCompletionService` + `ProfilePreviewService` | ⚠️ **OPTIONAL** | 476 lines | Different responsibilities - consolidate only if desired |
>
> **All files now under 1000-line limit** - no pre-existing violations.

---

## Consolidation Strategy

### Category 1: Exception + Service (Safe ✅)
**Pattern:** Exceptions used only by one service → nest inside that service

**Consolidations:**
1. `TransitionValidationException` → nest in `RelationshipTransitionService`

**Usage Analysis (verified 2026-01-24):**
- Thrown by: `RelationshipTransitionService` (12 occurrences)
- Caught by: `MatchingHandler`, `RelationshipHandler` (CLI layers)
- **Verdict:** Safe to nest. CLI will import `RelationshipTransitionService.TransitionValidationException`

**File Reduction:** 1 file

---

### Category 2: Config + Service (✅ UNBLOCKED with 1000-line limit)
**Pattern:** Config classes used only by one service → nest inside that service

**Consolidations:**
1. `MatchQualityConfig` → nest in `MatchQualityService`

**Size Analysis:**
```
MatchQualityService.java = 407 lines
MatchQualityConfig.java  =  65 lines
Combined                 = 472 lines ✅ Under 1000
```

**Verdict:** ✅ Safe to consolidate. Single-consumer config.

**File Reduction:** 1 file

---

### Category 3: Utility + Service Consolidations (✅ UNBLOCKED with 1000-line limit)
**Pattern:** Utility classes → nest inside their primary consumer

**Consolidations:**
1. `InterestMatcher` → nest in `MatchQualityService` ✅ **UNBLOCKED**
2. `CandidateFinderService` → nest in `CandidateFinder` ✅ **SAFE**
3. `GeoUtils` → nest in `CandidateFinder` ✅ **NEW OPPORTUNITY**

**InterestMatcher - UNBLOCKED:**
```
Multi-consumer utility (requires import updates):
  - MatchQualityService.java (5 usages) - primary consumer
  - MatchingHandler.java (4 usages) - CLI → update to MatchQualityService.InterestMatcher
  - ProfileHandler.java (1 usage) - CLI → update to MatchQualityService.InterestMatcher

MatchQualityService + Config + InterestMatcher = 589 lines ✅ Under 1000
```

**CandidateFinderService - SAFE:**
```
Interface wrapper (22 lines), nest in CandidateFinder
CandidateFinder.java = 147 lines + 22 = 169 lines ✅
```

**GeoUtils - NEW:**
```
Multi-consumer utility (requires import updates):
  - CandidateFinder.java (1 usage) - primary consumer (geo filtering)
  - DailyPickService.java (1 usage) → update to CandidateFinder.GeoUtils
  - MatchQualityService.java (1 usage) → update to CandidateFinder.GeoUtils
  - MatchingHandler.java (2 usages) - CLI → update to CandidateFinder.GeoUtils

CandidateFinder + Service + GeoUtils = 205 lines ✅ Under 1000
```

**File Reduction:** 3 files

---

### Category 4: Enum + Related Record (Domain Cohesion ✅)
**Pattern:** Enum + tightly coupled record → consolidate in model/

**Consolidations:**
1. `Achievement` (enum) + `UserAchievement` (record) → `Achievement.java`
   ```java
   // model/Achievement.java
   public class Achievement {
       public enum Type { FIRST_SPARK, POPULAR, ... }
       public record UserRecord(UUID id, UUID userId, Type type, Instant unlockedAt) { }
   }
   ```

**Usage Analysis (verified 2026-01-24):**
- `UserAchievement` is widely used (CLI, UI, Storage, Tests)
- Consolidation valid but requires updating imports across ~12 files
- Combined size: 83 + 34 = 117 lines ✅ Under 400

**File Reduction:** 1 file

---

### Category 5: Service Consolidation (⚠️ OPTIONAL - Different Concerns)
**Pattern:** Two services with related responsibilities → consolidate if desired

**Proposal:**
1. `ProfileCompletionService` + `ProfilePreviewService` → merge into `ProfileService`

**Analysis:**

| Service | Lines | Purpose | Consumers |
|---------|-------|---------|-----------|
| `ProfileCompletionService` | 304 | Detailed scoring, tier labels, category breakdown | Main.java, DashboardViewModel, ProfileViewModel |
| `ProfilePreviewService` | 172 | Simple completeness %, preview generation, tips | CLI handlers |

**Size check:** 304 + 172 = 476 lines ✅ Under 1000

**Decision:** These have different concerns but are both "profile analytics":
- **Option A:** Keep separate (cleaner separation of concerns)
- **Option B:** Consolidate into single `ProfileService` with nested helpers (fewer files)

**If consolidating:**
```java
// service/ProfileService.java (476 lines)
public class ProfileService {
    // Detailed completion (from ProfileCompletionService)
    public static class Completion { ... }

    // Preview generation (from ProfilePreviewService)
    public ProfilePreview generatePreview(User user) { ... }
    public ProfileCompleteness calculateCompleteness(User user) { ... }
}
```

**File Reduction:** 1 file (if consolidated) / 0 files (if kept separate)

---

---

### TIER 1: Domain Model Consolidations (NEW - 6 files reduced)

**Pattern:** Group related domain records/enums into cohesive modules

| Consolidation | Components | Combined | Reduction |
|---------------|------------|----------|-----------|
| `UserInteractions.java` | Like (43L) + Block (43L) + Report (55L) | 141 lines | -2 files |
| `Messaging.java` | Message (51L) + Conversation (268L) | 319 lines | -1 file |
| `Stats.java` | UserStats (158L) + PlatformStats (41L) | 199 lines | -1 file |
| `Preferences.java` | Interest (131L) + Lifestyle (101L) | 232 lines | -1 file |
| `Social.java` | FriendRequest (25L) + Notification (30L) | 55 lines | -1 file |

**Example structure for `UserInteractions.java`:**
```java
package datingapp.core.model;

public final class UserInteractions {
    private UserInteractions() {}

    public record Like(UUID id, UUID fromUserId, UUID toUserId, Direction direction, Instant createdAt) {
        public enum Direction { LIKE, PASS }
        // ... factory methods ...
    }

    public record Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
        // ... factory methods ...
    }

    public record Report(UUID id, UUID reporterId, UUID reportedId, Reason reason, String details, Instant createdAt) {
        public enum Reason { INAPPROPRIATE, SPAM, FAKE_PROFILE, HARASSMENT, OTHER }
        // ... factory methods ...
    }
}
```

---

### TIER 2: Service Consolidations (NEW - 4 files reduced)

**Pattern:** Merge small related services into cohesive service modules

| Consolidation | Components | Combined | Reduction |
|---------------|------------|----------|-----------|
| `TrustSafetyService.java` | VerificationService (60L) + ReportService (75L) | 135 lines | -1 file |
| `DailyService.java` | DailyLimitService (109L) + DailyPickService (169L) | 278 lines | -1 file |
| `MatchingService.java` | MatchingService (86L) + LikerBrowserService (75L) | 161 lines | -1 file |
| `MatchQualityService.java` | + PaceCompatibilityService (84L) | +84 lines | -1 file |

**Example structure for `TrustSafetyService.java`:**
```java
package datingapp.core.service;

public class TrustSafetyService {
    // From VerificationService
    public boolean isVerified(User user) { ... }
    public void verifyUser(User user, User.VerificationMethod method) { ... }

    // From ReportService
    public record ReportResult(boolean success, boolean userWasBanned, String errorMessage) {}
    public ReportResult reportUser(UUID reporterId, UUID reportedId, Report.Reason reason, String details) { ... }
}
```

---

### TIER 3: Storage Interface Consolidations (NEW - 10 files reduced)

**Pattern:** Group related storage interfaces by domain area

| Consolidation | Components | Combined | Reduction |
|---------------|------------|----------|-----------|
| `UserStorages.java` | UserStorage (25L) + UserStatsStorage (25L) + UserAchievementStorage (22L) | 72 lines | -2 files |
| `MatchingStorages.java` | MatchStorage (41L) + LikeStorage (63L) + BlockStorage (28L) | 132 lines | -2 files |
| `MessagingStorages.java` | ConversationStorage (37L) + MessageStorage (65L) | 102 lines | -1 file |
| `SocialStorages.java` | ReportStorage (25L) + NotificationStorage (20L) + FriendRequestStorage (20L) | 65 lines | -2 files |
| `SessionStorages.java` | SwipeSessionStorage (67L) + DailyPickStorage (37L) | 104 lines | -1 file |
| `ProfileStorages.java` | ProfileNoteStorage (45L) + ProfileViewStorage (50L) + PlatformStatsStorage (17L) | 112 lines | -2 files |

**Example structure for `UserStorages.java`:**
```java
package datingapp.core.storage;

public final class UserStorages {
    private UserStorages() {}

    public interface User {
        void save(datingapp.core.model.User user);
        datingapp.core.model.User findById(UUID id);
        List<datingapp.core.model.User> findAll();
        // ...
    }

    public interface Stats {
        void save(datingapp.core.model.Stats.UserStats stats);
        datingapp.core.model.Stats.UserStats findByUserId(UUID userId);
        // ...
    }

    public interface Achievement {
        void save(datingapp.core.model.Achievement.UserRecord achievement);
        List<datingapp.core.model.Achievement.UserRecord> findByUserId(UUID userId);
        // ...
    }
}
```

---

### Category 7: NOT Consolidating (Core Entities)

**Keep Separate:**
- ❌ `User.java` (595 lines) - central domain entity, too large and important
- ❌ `Match.java` (259 lines) - complex state machine
- ❌ `SwipeSession.java` (240 lines) - complex session state
- ❌ `AppConfig.java` (167 lines) - cross-cutting configuration
- ❌ `ServiceRegistry.java` (377 lines) - application wiring
- ❌ `DealbreakersEvaluator.java` (175 lines) - widely used evaluator

---

## Step-by-Step Execution Plan

## Progress Updates (2026-01-24)

- [x] Phase 2: Nested `TransitionValidationException` in `RelationshipTransitionService`; updated CLI/tests; removed old file.
- [x] Phase 3: Nested `MatchQualityConfig` + `InterestMatcher` in `MatchQualityService`; updated CLI/tests; removed old files.
- [x] Phase 4: Nested `GeoUtils` in `CandidateFinder`; updated usages/tests; removed `GeoUtils.java`.
- [x] Phase 4 note: Removed `CandidateFinderService` interface; consumers now use `CandidateFinder` directly to avoid cyclic nesting.
- [ ] Phase 1: Package reorganization (pending).
- [ ] Phase 5+: Remaining consolidations (pending).

1|2026-01-24 14:30:00|agent:github_copilot|scope:core-consolidation-plan|Progress update: Phases 2-4 implemented; CandidateFinderService removed in favor of concrete CandidateFinder|docs/core-consolidation-plan.md

### Phase 0: Preflight (MANDATORY - Do Not Skip)

**Task 0.1:** Build usage map
```bash
# For each consolidation candidate, check all usages
rg "import.*TransitionValidationException" src/
rg "import.*MatchQualityConfig" src/
rg "import.*InterestMatcher" src/
rg "import.*CandidateFinderService" src/
rg "import.*ProfileCompletionService" src/
rg "new UserAchievement" src/  # Check if used outside Achievement context
```

**Task 0.2:** Create consolidation decision matrix

**COMPLETED (2026-01-24) — Updated with 1000-line limit:**

| Class | Used By | Usage Count | Safe to Consolidate? | Target Parent | Combined Size |
|-------|---------|-------------|----------------------|---------------|---------------|
| TransitionValidationException | Service + CLI | 3 files | ✅ YES | RelationshipTransitionService | 189 lines |
| MatchQualityConfig | MatchQualityService | 1 service | ✅ **YES** | MatchQualityService | 472 lines |
| InterestMatcher | Service + CLI | 3 files | ✅ **YES** | MatchQualityService | 589 lines |
| CandidateFinderService | Interface | 4 files | ✅ YES | CandidateFinder | 169 lines |
| GeoUtils | Multi-consumer | 4 files | ✅ **YES** | CandidateFinder | 205 lines |
| UserAchievement | Widely used | 12+ files | ✅ YES | Achievement | 117 lines |
| ProfileCompletionService | Main + UI | 5 files | ⚠️ **OPTIONAL** | ProfileService | 476 lines |

**Task 0.3:** Document current package dependencies
```bash
# Check cross-package dependencies
rg "import datingapp.core" src/main/java/datingapp/storage/ | wc -l
rg "import datingapp.core" src/main/java/datingapp/cli/ | wc -l
rg "import datingapp.core" src/main/java/datingapp/ui/ | wc -l
```

**Checkpoint:** Do not proceed until usage map is complete and reviewed.

---

### Phase 1: Create Package Structure (Foundation)

**Step 1.1:** Create new package directories
```bash
mkdir -p src/main/java/datingapp/core/model
mkdir -p src/main/java/datingapp/core/service
mkdir -p src/main/java/datingapp/core/storage
mkdir -p src/main/java/datingapp/core/util
```

**Step 1.2:** Move files to packages (NO consolidation yet)

**Models (domain entities + enums):**
Move the following files to `src/main/java/datingapp/core/model/`:
- User.java
- Match.java
- Like.java
- Block.java
- Report.java
- Conversation.java
- Message.java
- FriendRequest.java
- SwipeSession.java
- Achievement.java
- UserAchievement.java
- Interest.java
- Lifestyle.java
- Dealbreakers.java
- PacePreferences.java
- Notification.java
- UserStats.java
- PlatformStats.java
- MatchQuality.java
- ProfileNote.java
- UserStatus.java

**Services:**
Move all `*Service.java` files plus `CandidateFinder.java` and `DealbreakersEvaluator.java` to `src/main/java/datingapp/core/service/`

**Storage interfaces:**
Move all `*Storage.java` files to `src/main/java/datingapp/core/storage/`

**Utilities:**
Move the following to `src/main/java/datingapp/core/util/`:
- GeoUtils.java
- AppConfig.java
- ServiceRegistry.java

**Step 1.3:** Update package declarations

Update all moved files to have correct package declaration:
- model files: `package datingapp.core.model;`
- service files: `package datingapp.core.service;`
- storage files: `package datingapp.core.storage;`
- util files: `package datingapp.core.util;`

**Step 1.4:** Update imports across entire codebase

Use IDE refactoring or find/replace to update imports:
- `datingapp.core.User` → `datingapp.core.model.User`
- `datingapp.core.MatchingService` → `datingapp.core.service.MatchingService`
- `datingapp.core.UserStorage` → `datingapp.core.storage.UserStorage`
- etc.

**Step 1.5:** Run spotless + tests
```bash
mvn spotless:apply
mvn clean test
```

**Step 1.6:** Checkpoint

At this point:
- ✅ 61 files organized into packages
- ✅ All tests passing
- ✅ No behavior changes

**DECISION POINT:** You could STOP here if the organization alone is sufficient!

---

### Phase 2: Exception Consolidations (Safe)

**Step 2.1:** Nest `TransitionValidationException` in `RelationshipTransitionService`

Edit `service/RelationshipTransitionService.java`:
```java
// service/RelationshipTransitionService.java
public class RelationshipTransitionService {

    // Nested exception
    public static class TransitionValidationException extends Exception {
        // ... existing code from TransitionValidationException.java ...
    }

    // ... rest of service ...
}
```

**Step 2.2:** Update imports

Find and replace across codebase:
- `import datingapp.core.TransitionValidationException;`
- → `import datingapp.core.service.RelationshipTransitionService.TransitionValidationException;`

Or use static import:
- `import static datingapp.core.service.RelationshipTransitionService.TransitionValidationException;`

**Step 2.3:** Delete old file

Delete `src/main/java/datingapp/core/TransitionValidationException.java`

**Step 2.4:** Run spotless + tests
```bash
mvn spotless:apply
mvn test
```

**Step 2.5:** Checkpoint

File count: 61 → 60

---

### Phase 3: Config + Utility Consolidations (✅ UNBLOCKED)

**Step 3.1:** Nest `MatchQualityConfig` in `MatchQualityService`

Edit `service/MatchQualityService.java`:
```java
public class MatchQualityService {
    // Nested config record
    public record Config(
        double distanceWeight,
        double ageWeight,
        double interestWeight,
        double lifestyleWeight,
        double responseTimeWeight
    ) {
        public static Config defaults() { return new Config(0.15, 0.10, 0.30, 0.30, 0.15); }
    }

    private final Config config;
    // ... rest of service ...
}
```

**Step 3.2:** Nest `InterestMatcher` in `MatchQualityService`

```java
public class MatchQualityService {
    public record Config(...) { }

    // Nested utility class
    public static final class InterestMatcher {
        public record MatchResult(Set<Interest> shared, int totalUnique, double overlapRatio) { }
        public static MatchResult compare(Set<Interest> a, Set<Interest> b) { ... }
        public static String formatSharedInterests(Set<Interest> shared) { ... }
        // ... rest of InterestMatcher ...
    }
    // ... rest of service ...
}
```

**Step 3.3:** Update imports across codebase

Files requiring import updates:
- `MatchingHandler.java` → `import datingapp.core.service.MatchQualityService.InterestMatcher;`
- `ProfileHandler.java` → `import datingapp.core.service.MatchQualityService.InterestMatcher;`

**Step 3.4:** Delete old files + test
```bash
rm src/main/java/datingapp/core/MatchQualityConfig.java
rm src/main/java/datingapp/core/InterestMatcher.java
mvn spotless:apply && mvn test
```

**Checkpoint:** File count: 60 → 58
Combined MatchQualityService.java: 589 lines ✅

---

### Phase 4: CandidateFinder Consolidations (Interface + GeoUtils)

**Step 4.1:** Nest `CandidateFinderService` interface in `CandidateFinder`

Edit `service/CandidateFinder.java`:
```java
public class CandidateFinder {
    /**
     * Service interface for candidate finding operations.
     */
    public interface Service {
        List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> excluded);
    }
    // ... existing implementation ...
}
```

**Step 4.2:** Nest `GeoUtils` in `CandidateFinder`

```java
public class CandidateFinder {
    public interface Service { ... }

    /**
     * Geographic utilities for distance calculations.
     */
    public static final class GeoUtils {
        private static final double EARTH_RADIUS_KM = 6371.0;

        public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
            // Haversine formula implementation
        }

        private GeoUtils() {}
    }
    // ... rest of implementation ...
}
```

**Step 4.3:** Update imports across codebase

Files requiring GeoUtils import updates:
- `DailyPickService.java` → `import static datingapp.core.service.CandidateFinder.GeoUtils.distanceKm;`
- `MatchQualityService.java` → `import static datingapp.core.service.CandidateFinder.GeoUtils.distanceKm;`
- `MatchingHandler.java` → `import static datingapp.core.service.CandidateFinder.GeoUtils.distanceKm;`

Files requiring CandidateFinderService import updates:
- `MatchingHandler.java`, `MatchingViewModel.java`, `ServiceRegistry.java`

**Step 4.4:** Delete old files + test
```bash
rm src/main/java/datingapp/core/CandidateFinderService.java
rm src/main/java/datingapp/core/GeoUtils.java
mvn spotless:apply && mvn test
```

**Checkpoint:** File count: 58 → 56
CandidateFinder.java: 147 + 22 + 36 = 205 lines ✅

---

### Phase 5: Achievement Consolidation

**Step 5.1:** Consolidate `Achievement` + `UserAchievement`

```java
// model/Achievement.java
public class Achievement {
    public enum Type { FIRST_SPARK, POPULAR, ... }
    public record UserRecord(UUID id, UUID userId, Type type, Instant unlockedAt) { }
    private Achievement() {}
}
```

**Step 5.2:** Update usages: `Achievement` → `Achievement.Type`, `UserAchievement` → `Achievement.UserRecord`

**Step 5.3:** Delete `UserAchievement.java`, test

**Checkpoint:** File count: 56 → 55

---

### Phase 6: TIER 1 - Domain Model Consolidations (6 files reduced)

**Step 6.1:** Create `UserInteractions.java` (Like + Block + Report)

```java
// model/UserInteractions.java
package datingapp.core.model;

public final class UserInteractions {
    private UserInteractions() {}

    public record Like(UUID id, UUID fromUserId, UUID toUserId, Direction direction, Instant createdAt) {
        public enum Direction { LIKE, PASS }
        public static Like create(UUID from, UUID to, Direction dir) { ... }
    }

    public record Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {
        public static Block create(UUID blocker, UUID blocked) { ... }
    }

    public record Report(UUID id, UUID reporterId, UUID reportedId, Reason reason, String details, Instant createdAt) {
        public enum Reason { INAPPROPRIATE, SPAM, FAKE_PROFILE, HARASSMENT, OTHER }
        public static Report create(...) { ... }
    }
}
```

**Step 6.2:** Create `Messaging.java` (Message + Conversation)

```java
// model/Messaging.java
public final class Messaging {
    private Messaging() {}

    public record Message(UUID id, String conversationId, UUID senderId, String content, Instant createdAt) { ... }

    public static class Conversation {
        // Full Conversation class with state management
    }
}
```

**Step 6.3:** Create `Stats.java` (UserStats + PlatformStats)

```java
// model/Stats.java
public final class Stats {
    private Stats() {}

    public record User(UUID userId, int totalLikes, int totalMatches, ...) { ... }
    public record Platform(int totalUsers, int activeUsers, ...) { ... }
}
```

**Step 6.4:** Create `Preferences.java` (Interest + Lifestyle enums)

```java
// model/Preferences.java
public final class Preferences {
    private Preferences() {}

    public enum Interest { HIKING, COOKING, TRAVEL, ... }
    public enum Lifestyle { SMOKING, DRINKING, EXERCISE, ... }
}
```

**Step 6.5:** Create `Social.java` (FriendRequest + Notification)

```java
// model/Social.java
public final class Social {
    private Social() {}

    public record FriendRequest(UUID id, UUID fromUserId, UUID toUserId, Status status, Instant createdAt) {
        public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED }
    }

    public record Notification(UUID id, UUID userId, Type type, String title, String message, Instant createdAt) {
        public enum Type { MATCH, MESSAGE, LIKE, FRIEND_REQUEST, ACHIEVEMENT }
    }
}
```

**Step 6.6:** Update ALL imports across codebase, delete old files, test

```bash
rm src/main/java/datingapp/core/Like.java
rm src/main/java/datingapp/core/Block.java
rm src/main/java/datingapp/core/Report.java
rm src/main/java/datingapp/core/Message.java
rm src/main/java/datingapp/core/Conversation.java
rm src/main/java/datingapp/core/UserStats.java
rm src/main/java/datingapp/core/PlatformStats.java
rm src/main/java/datingapp/core/Interest.java
rm src/main/java/datingapp/core/Lifestyle.java
rm src/main/java/datingapp/core/FriendRequest.java
rm src/main/java/datingapp/core/Notification.java
mvn spotless:apply && mvn test
```

**Checkpoint:** File count: 55 → 49 (-6 files)

---

### Phase 7: TIER 2 - Service Consolidations (4 files reduced)

**Step 7.1:** Create `TrustSafetyService.java` (VerificationService + ReportService)

```java
// service/TrustSafetyService.java
public class TrustSafetyService {
    private final UserStorages.User userStorage;
    private final SocialStorages socialStorages;

    // From VerificationService
    public boolean isVerified(User user) { ... }
    public void verifyUser(User user, User.VerificationMethod method) { ... }

    // From ReportService
    public record ReportResult(boolean success, boolean userWasBanned, String errorMessage) {}
    public ReportResult reportUser(UUID reporterId, UUID reportedId, ...) { ... }
}
```

**Step 7.2:** Create `DailyService.java` (DailyLimitService + DailyPickService)

```java
// service/DailyService.java
public class DailyService {
    // From DailyLimitService
    public boolean canSwipe(UUID userId) { ... }
    public int getRemainingSwipes(UUID userId) { ... }

    // From DailyPickService
    public record DailyPick(User user, String reason, Instant generatedAt) {}
    public Optional<DailyPick> getDailyPick(User user) { ... }
}
```

**Step 7.3:** Expand `MatchingService.java` (+ LikerBrowserService)

```java
// service/MatchingService.java
public class MatchingService {
    // Existing matching logic
    public Optional<Match> createMatch(UUID user1, UUID user2) { ... }

    // From LikerBrowserService
    public record PendingLiker(User user, Instant likedAt) {}
    public List<PendingLiker> getPendingLikers(UUID userId) { ... }
}
```

**Step 7.4:** Expand `MatchQualityService.java` (+ PaceCompatibilityService)

Already has Config + InterestMatcher. Add PaceCompatibility:
```java
// Add to MatchQualityService.java
public double calculatePaceCompatibility(User a, User b) {
    // Logic from PaceCompatibilityService
}
```

**Step 7.5:** Create `ProfileService.java` (ProfilePreviewService + ProfileCompletionService)

```java
// service/ProfileService.java
public class ProfileService {
    // From ProfileCompletionService
    public record CompletionResult(int score, String tier, List<CategoryBreakdown> categories) {}
    public CompletionResult calculateDetailedCompletion(User user) { ... }

    // From ProfilePreviewService
    public record ProfilePreview(String name, int age, String bio) {}
    public ProfilePreview generatePreview(User user) { ... }
}
```

**Step 7.6:** Delete old files, test

```bash
rm src/main/java/datingapp/core/VerificationService.java
rm src/main/java/datingapp/core/ReportService.java
rm src/main/java/datingapp/core/DailyLimitService.java
rm src/main/java/datingapp/core/DailyPickService.java
rm src/main/java/datingapp/core/LikerBrowserService.java
rm src/main/java/datingapp/core/PaceCompatibilityService.java
rm src/main/java/datingapp/core/ProfilePreviewService.java
rm src/main/java/datingapp/core/ProfileCompletionService.java
mvn spotless:apply && mvn test
```

**Checkpoint:** File count: 49 → 45 (-4 files)

---

### Phase 8: TIER 3 - Storage Interface Consolidations (10 files reduced)

**Step 8.1:** Create `UserStorages.java`

```java
// storage/UserStorages.java
public final class UserStorages {
    private UserStorages() {}

    public interface User {
        void save(datingapp.core.model.User user);
        datingapp.core.model.User findById(UUID id);
        List<datingapp.core.model.User> findAll();
        Optional<datingapp.core.model.User> findByEmail(String email);
    }

    public interface Stats {
        void save(datingapp.core.model.Stats.User stats);
        datingapp.core.model.Stats.User findByUserId(UUID userId);
    }

    public interface Achievement {
        void save(datingapp.core.model.Achievement.UserRecord achievement);
        List<datingapp.core.model.Achievement.UserRecord> findByUserId(UUID userId);
    }
}
```

**Step 8.2:** Create `MatchingStorages.java`

```java
// storage/MatchingStorages.java
public final class MatchingStorages {
    private MatchingStorages() {}

    public interface Match { ... }
    public interface Like { ... }
    public interface Block { ... }
}
```

**Step 8.3:** Create `MessagingStorages.java`

```java
// storage/MessagingStorages.java
public final class MessagingStorages {
    private MessagingStorages() {}

    public interface Conversation { ... }
    public interface Message { ... }
}
```

**Step 8.4:** Create `SocialStorages.java`

```java
// storage/SocialStorages.java
public final class SocialStorages {
    private SocialStorages() {}

    public interface Report { ... }
    public interface Notification { ... }
    public interface FriendRequest { ... }
}
```

**Step 8.5:** Create `SessionStorages.java`

```java
// storage/SessionStorages.java
public final class SessionStorages {
    private SessionStorages() {}

    public interface SwipeSession { ... }
    public interface DailyPick { ... }
}
```

**Step 8.6:** Create `ProfileStorages.java`

```java
// storage/ProfileStorages.java
public final class ProfileStorages {
    private ProfileStorages() {}

    public interface ProfileNote { ... }
    public interface ProfileView { ... }
    public interface PlatformStats { ... }
}
```

**Step 8.7:** Update ALL storage implementations in `storage/` package

Each `H2*Storage.java` must be updated to implement the new nested interfaces.

**Step 8.8:** Delete old files, test

```bash
rm src/main/java/datingapp/core/UserStorage.java
rm src/main/java/datingapp/core/UserStatsStorage.java
rm src/main/java/datingapp/core/UserAchievementStorage.java
rm src/main/java/datingapp/core/MatchStorage.java
rm src/main/java/datingapp/core/LikeStorage.java
rm src/main/java/datingapp/core/BlockStorage.java
rm src/main/java/datingapp/core/ConversationStorage.java
rm src/main/java/datingapp/core/MessageStorage.java
rm src/main/java/datingapp/core/ReportStorage.java
rm src/main/java/datingapp/core/NotificationStorage.java
rm src/main/java/datingapp/core/FriendRequestStorage.java
rm src/main/java/datingapp/core/SwipeSessionStorage.java
rm src/main/java/datingapp/core/DailyPickStorage.java
rm src/main/java/datingapp/core/ProfileNoteStorage.java
rm src/main/java/datingapp/core/ProfileViewStorage.java
rm src/main/java/datingapp/core/PlatformStatsStorage.java
mvn spotless:apply && mvn test
```

**Checkpoint:** File count: 45 → 35 (-10 files)

---

### Phase 9: Final Cleanup & Documentation

**Step 9.1:** Run full verification
```bash
mvn clean verify
mvn spotless:check
mvn jacoco:report
```

**Step 9.2:** Update `ServiceRegistry` with new package paths and consolidated services

**Step 9.3:** Update storage implementations
- `H2UserStorage` → implements `UserStorages.User`
- `H2UserStatsStorage` → implements `UserStorages.Stats`
- etc.

**Step 9.4:** Update documentation
- Update `CLAUDE.md` with new package structure
- Update `AGENTS.md` with import patterns
- Update architecture diagrams in `docs/architecture.md`

**Step 7.4:** Final checkpoint

Final file count: 61 → **54-55 files** (10-11% reduction via consolidation)
+ Package organization into model/service/storage/util

**Summary of all consolidations:**
```
TransitionValidationException → RelationshipTransitionService  (-1 file)
MatchQualityConfig + InterestMatcher → MatchQualityService     (-2 files)
CandidateFinderService + GeoUtils → CandidateFinder            (-2 files)
Achievement + UserAchievement → Achievement                     (-1 file)
ProfileCompletionService + ProfilePreviewService (optional)    (-0/1 file)
─────────────────────────────────────────────────────────────────────────
Total reduction: 6-7 files
```
Package organization: model/ service/ storage/ util/

---

## Post-Refactor Checklist

### ✅ Verification Steps
- [ ] All tests passing (`mvn test`)
- [ ] No compilation errors (`mvn compile`)
- [ ] Code formatting correct (`mvn spotless:check`)
- [ ] Coverage maintained (`mvn jacoco:report`)
- [ ] CLI app runs (`mvn exec:java`)
- [ ] JavaFX GUI runs (`mvn javafx:run`)

### ✅ Documentation Updates
- [ ] `CLAUDE.md` updated with package structure
- [ ] `AGENTS.md` updated with import patterns
- [ ] `docs/architecture.md` diagrams updated
- [ ] `ServiceRegistry` updated with new paths
- [ ] This plan marked as completed

### ✅ Quality Checks
- [ ] No files >400 lines (**Note:** Pre-existing violations: User.java=595L, MatchQualityService.java=407L)
- [ ] Storage interfaces remain separate (not nested in services)
- [ ] No architectural violations (e.g., storage importing services)
- [ ] All imports updated across storage/, cli/, ui/, tests/

---

## Expected Outcomes

### File Count Reduction (Revised 2026-01-24 with 1000-line limit)
```
Starting:  61 files (flat core/)
Phase 1:   61 files (organized into packages)
Phase 2:   60 files (TransitionValidationException → RelationshipTransitionService)
Phase 3:   58 files (MatchQualityConfig + InterestMatcher → MatchQualityService)
Phase 4:   56 files (CandidateFinderService + GeoUtils → CandidateFinder)
Phase 5:   55 files (Achievement + UserAchievement consolidation)
Phase 6:   54-55 files (ProfileServices consolidation - OPTIONAL)
───────────────────────────────────────────
Final:     54-55 files (10-11% reduction via consolidation)
           + Package organization benefit
```

**Consolidation Summary:**
| Consolidation | Files Reduced | Combined Size |
|---------------|---------------|---------------|
| TransitionValidationException | 1 | 189 lines |
| MatchQualityConfig + InterestMatcher | 2 | 589 lines |
| CandidateFinderService + GeoUtils | 2 | 205 lines |
| Achievement + UserAchievement | 1 | 117 lines |
| ProfileServices (optional) | 0-1 | 476 lines |
| **Total** | **6-7** | — |

### Package Distribution (Target)
```
model/    : ~17 files (domain entities, enums - Achievement consolidation)
service/  : ~14 files (business logic - MatchQuality and CandidateFinder consolidations)
storage/  : ~16 files (persistence interfaces - NO consolidation)
util/     : ~2 files (AppConfig, ServiceRegistry - GeoUtils moved to CandidateFinder)
```

### File Size Distribution
- **Target:** No files >1000 lines ✅
- **All files compliant** with 1000-line limit
- **Largest files after consolidation:**
  - `User.java` = 595 lines ✅
  - `MatchQualityService.java` = 589 lines (after Config + InterestMatcher) ✅
  - `ProfileService.java` = 476 lines (if consolidated) ✅
  - `ServiceRegistry.java` = 377 lines ✅

### Maintainability Improvements
✅ **Clear separation of concerns** (model/service/storage/util)
✅ **Easier navigation** (packages group related types)
✅ **Reduced cognitive load** (fewer top-level types)
✅ **Better IDE experience** (package-based navigation)
✅ **Clearer architecture** (package dependencies visible)

---

## Alternative: If File Count Reduction Isn't Worth It

If after Phase 1 (package reorganization), the team finds the new structure sufficient:

**STOP after Phase 1** and keep:
- 61 files organized into clear packages
- No consolidation complexity
- Maximum navigability (one concern per file)

**Rationale:** Package organization alone provides most of the maintainability benefit. File count reduction through consolidation has diminishing returns and adds complexity.

---

## Decision Point: After Phase 1

After completing Phase 1 (package reorganization), **PAUSE and evaluate**:

### Questions to Answer:
1. Is the package structure alone sufficient?
2. Do we still feel 61 files is too many?
3. Are there specific pain points consolidation would solve?
4. Is the consolidation complexity worth the file count reduction?

### If "Package structure is enough":
- **STOP** at Phase 1
- Keep 61 files in organized structure
- Mark plan as "Partially Completed (Package Reorganization Only)"

### If "We want fewer files":
- **CONTINUE** to Phase 2+
- Target: 58 files with safe consolidations (realistic ceiling)
- **Note:** Original target of 42-45 files is NOT achievable without major refactoring of MatchQualityService and User.java first

**Recommendation:** Start with Phase 1 only, then decide based on actual experience.

---

## Future Work: Additional Opportunities

With the 1000-line limit, all planned consolidations are now achievable. These are additional opportunities for the future:

### 1. Further Service Consolidations (if desired)
- `DailyPickService` (169L) + `DailyLimitService` (109L) = 278 lines — both deal with daily operations
- `SessionService` (181L) could potentially absorb related session logic

### 2. Domain Model Refinements
- `User.java` (595L) could be split into `User` + `UserProfile` if growth continues
- Consider if `Dealbreakers` (169L) should become part of `User` preferences

### 3. Storage Interface Groupings (NOT recommended)
Storage interfaces are intentionally kept separate for the architectural boundary principle.
However, if ever relaxed, related interfaces could be grouped (e.g., `UserStorage` + `UserStatsStorage`).

---

## Changelog

| Date | Change | Reason |
|------|--------|--------|
| 2026-01-23 | Complete rewrite | Audit found critical issues with original plan |
| 2026-01-23 | Added hybrid approach | User requested package reorganization + file reduction |
| 2026-01-23 | Added decision point | Allow stopping after Phase 1 if sufficient |
| 2026-01-24 | Removed git directives | User will handle version control independently |
| 2026-01-24 | **Comprehensive audit** | Verified all consolidation candidates with actual file sizes and usage patterns |
| 2026-01-24 | **Relaxed limit: 400 → 1000 lines** | User preference allows larger consolidated files |
| 2026-01-24 | Unblocked MatchQualityConfig | Now fits within 1000-line limit (472L combined) |
| 2026-01-24 | Unblocked InterestMatcher | Now fits within 1000-line limit (589L combined) |
| 2026-01-24 | Added GeoUtils consolidation | New opportunity: nest in CandidateFinder (205L combined) |
| 2026-01-24 | ProfileServices made optional | Different concerns but consolidation now size-feasible (476L) |
| 2026-01-24 | Revised target: 61→54-55 files | 6-7 consolidations now achievable (~10-11% reduction) |

---

## References

- **Audit Report:** `docs/core-consolidation-audit-report.md`
- **Architecture:** `docs/architecture.md`
- **Coding Standards:** `AGENTS.md`
- **Project Overview:** `CLAUDE.md`

---

## Agent Changelog

1|2026-01-24 14:30:00|agent:github_copilot|scope:core-consolidation-plan|Progress update: Phases 2-4 implemented; CandidateFinderService removed in favor of concrete CandidateFinder|docs/core-consolidation-plan.md
