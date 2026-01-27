# Remediation Plan - January 27, 2026

Comprehensive fix plan for all issues identified in `FINDINGS_JAN_2026.md`.

---

## Phase 1: Critical Fixes (P0 - Immediate)

### Task 1.1: Fix CLI String Constant Rendering (CRIT-01)
**File:** `src/main/java/datingapp/cli/MatchingHandler.java:127`

**Current Issue:**
```java
logger.info("\nCliConstants.HEADER_BROWSE_CANDIDATES\n");
```

**Fix:**
```java
logger.info("\n" + CliConstants.HEADER_BROWSE_CANDIDATES + "\n");
```

**Steps:**
1. Search for all occurrences of string literals containing `"CliConstants.`
2. Replace with actual constant references
3. Test candidate browsing flow to verify headers display correctly

**Validation:**
- Run `mvn test -Dtest=MatchingHandlerTest`
- Manual CLI test: browse candidates and verify header renders properly

---

### Task 1.2: Add Missing Foreign Keys with CASCADE DELETE (CRIT-02)

**Problem:** Multiple tables lack proper foreign key constraints, causing:
- Orphaned data when users are deleted
- FK constraint violations preventing user deletion
- Missing FK relationships entirely (conversations, messages)

**Affected Tables:**
- `friend_requests`
- `notifications`
- `blocks`
- `reports`
- `user_achievements`
- `daily_pick_views`
- `conversations` (missing FK constraints entirely)
- `messages` (missing FK for `sender_id`)
- `profile_notes`
- `profile_views`

**Implementation Strategy:**

#### 1.2.1: Create Migration Method in DatabaseManager
Add new method to `DatabaseManager.java`:
```java
private void addMissingForeignKeys() throws SQLException {
    try (Connection conn = getConnection();
         Statement stmt = conn.createStatement()) {

        // friend_requests
        stmt.execute("ALTER TABLE friend_requests ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_friend_requests_from FOREIGN KEY (from_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE friend_requests ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_friend_requests_to FOREIGN KEY (to_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // notifications
        stmt.execute("ALTER TABLE notifications ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_notifications_user FOREIGN KEY (user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // blocks
        stmt.execute("ALTER TABLE blocks ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_blocks_blocker FOREIGN KEY (blocker_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE blocks ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_blocks_blocked FOREIGN KEY (blocked_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // reports
        stmt.execute("ALTER TABLE reports ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_reports_reporter FOREIGN KEY (reporter_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE reports ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_reports_reported FOREIGN KEY (reported_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // user_achievements
        stmt.execute("ALTER TABLE user_achievements ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_user_achievements_user FOREIGN KEY (user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // daily_pick_views
        stmt.execute("ALTER TABLE daily_pick_views ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_daily_pick_views_user FOREIGN KEY (user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE daily_pick_views ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_daily_pick_views_pick FOREIGN KEY (pick_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // profile_notes
        stmt.execute("ALTER TABLE profile_notes ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_profile_notes_author FOREIGN KEY (author_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE profile_notes ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_profile_notes_subject FOREIGN KEY (subject_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // profile_views
        stmt.execute("ALTER TABLE profile_views ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_profile_views_viewer FOREIGN KEY (viewer_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE profile_views ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_profile_views_viewed FOREIGN KEY (viewed_user_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // conversations - ADD MISSING FKs
        stmt.execute("ALTER TABLE conversations ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_conversations_user_a FOREIGN KEY (user_a_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE conversations ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_conversations_user_b FOREIGN KEY (user_b_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");

        // messages - ADD MISSING FK for sender
        stmt.execute("ALTER TABLE messages ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_messages_sender FOREIGN KEY (sender_id) " +
                    "REFERENCES users(id) ON DELETE CASCADE");
        stmt.execute("ALTER TABLE messages ADD CONSTRAINT IF NOT EXISTS " +
                    "fk_messages_conversation FOREIGN KEY (conversation_id) " +
                    "REFERENCES conversations(id) ON DELETE CASCADE");

        logger.info("Foreign key constraints added successfully");
    }
}
```

#### 1.2.2: Call Migration During Initialization
In `DatabaseManager.initializeSchema()`, add:
```java
addMissingForeignKeys();
```

#### 1.2.3: Update Storage Class Schema Methods
For tables where `ensureSchema()` exists in storage classes, add FK constraints:

**H2SocialStorage.java:**
```java
// Add to friend_requests table creation
FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE

// Add to notifications table creation
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

**H2ModerationStorage.java:**
```java
// Add to blocks table
FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE

// Add to reports table
FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE
```

**H2ConversationStorage.java:**
```java
// Add to conversations table
FOREIGN KEY (user_a_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (user_b_id) REFERENCES users(id) ON DELETE CASCADE
```

**H2MessageStorage.java:**
```java
// Add to messages table
FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
```

**H2ProfileDataStorage.java:**
```java
// Add to profile_notes table
FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (subject_user_id) REFERENCES users(id) ON DELETE CASCADE

// Add to profile_views table
FOREIGN KEY (viewer_id) REFERENCES users(id) ON DELETE CASCADE,
FOREIGN KEY (viewed_user_id) REFERENCES users(id) ON DELETE CASCADE
```

**Steps:**
1. Add `addMissingForeignKeys()` method to DatabaseManager
2. Call it from `initializeSchema()`
3. Update all storage class `ensureSchema()` methods with FK constraints
4. Test with fresh database initialization
5. Test user deletion to verify CASCADE works

**Validation:**
- Delete test user and verify all related data is removed
- Run integration tests: `mvn test -Dtest=H2*StorageTest`

---

## Phase 2: High Severity Fixes (P1 - This Week)

### Task 2.1: Fix Unread Message Count Logic (HIGH-01)

**File:** `src/main/java/datingapp/core/MessagingService.java:185`

**Current Issue:**
```java
private int countUnread(String conversationId, UUID userId, Instant lastReadAt) {
    return messageStorage.findByConversation(conversationId).stream()
            .filter(m -> m.sentAt().isAfter(lastReadAt))  // Counts sender's own messages!
            .toList()
            .size();
}
```

**Fix:**
```java
private int countUnread(String conversationId, UUID userId, Instant lastReadAt) {
    return messageStorage.findByConversation(conversationId).stream()
            .filter(m -> m.sentAt().isAfter(lastReadAt))
            .filter(m -> !m.senderId().equals(userId))  // Exclude own messages
            .toList()
            .size();
}
```

**Alternative Approach:** Update `lastReadAt` when user sends a message (auto-mark as read)

**Steps:**
1. Update `countUnread()` method to filter out sender's own messages
2. Add unit test for this scenario
3. Consider updating read timestamp on send for better UX

**Validation:**
- Add test case: User A sends 5 messages, User B sends 3 → User A should see 3 unread, not 8
- Run `mvn test -Dtest=MessagingServiceTest`

---

### Task 2.2: Apply Preference Filters to Daily Picks (HIGH-02)

**File:** `src/main/java/datingapp/core/DailyService.java:95`

**Current Issue:**
Daily pick selection uses `findActive()` which returns ALL active users, ignoring:
- Gender preferences
- Age range preferences
- Distance limits
- Dealbreakers

**Fix Strategy:**

#### 2.2.1: Extract Candidate Filtering Logic
Create new method in `DailyService`:
```java
private List<User> findEligibleCandidates(User currentUser, AppConfig config) {
    List<User> allActive = userStorage.findActive();

    // Reuse filtering logic from CandidateFinder
    return allActive.stream()
        .filter(u -> !u.getId().equals(currentUser.getId()))
        .filter(u -> !blockStorage.isBlocked(currentUser.getId(), u.getId()))
        .filter(u -> !likeStorage.hasLiked(currentUser.getId(), u.getId()))
        .filter(u -> matchesGenderPreference(currentUser, u))
        .filter(u -> matchesAgePreference(currentUser, u))
        .filter(u -> withinDistanceLimit(currentUser, u, config))
        .filter(u -> passesAllDealbreakers(currentUser, u))
        .collect(Collectors.toList());
}

private boolean matchesGenderPreference(User user, User candidate) {
    Set<Gender> seeking = user.getPreferences().getSeekingGenders();
    return seeking.isEmpty() || seeking.contains(candidate.getGender());
}

private boolean matchesAgePreference(User user, User candidate) {
    Preferences.AgeRange range = user.getPreferences().getAgeRange();
    if (range == null) return true;
    int age = candidate.getAge();
    return age >= range.min() && age <= range.max();
}

private boolean withinDistanceLimit(User user, User candidate, AppConfig config) {
    int maxKm = user.getPreferences().getMaxDistanceKm();
    if (maxKm <= 0) maxKm = config.getMaxDistanceKm();
    return user.getLocation().distanceKm(candidate.getLocation()) <= maxKm;
}

private boolean passesAllDealbreakers(User user, User candidate) {
    Dealbreakers db = user.getPreferences().getDealbreakers();
    if (db == null) return true;
    return db.evaluate(candidate).isEmpty();
}
```

#### 2.2.2: Update `selectDailyPick()`
```java
public Optional<User> selectDailyPick(UUID userId) {
    User user = userStorage.get(userId);
    if (user == null) return Optional.empty();

    // Use filtered candidates instead of all active
    List<User> eligible = findEligibleCandidates(user, config);

    // Filter out already viewed picks
    Set<UUID> viewedIds = dailyPickStorage.getViewedPickIds(userId);
    eligible = eligible.stream()
            .filter(u -> !viewedIds.contains(u.getId()))
            .collect(Collectors.toList());

    if (eligible.isEmpty()) return Optional.empty();

    // Random selection from filtered pool
    return Optional.of(eligible.get(random.nextInt(eligible.size())));
}
```

**Steps:**
1. Add preference filtering helper methods to DailyService
2. Update selectDailyPick() to use filtered candidates
3. Add unit tests for each filter type
4. Integration test with various preference combinations

**Validation:**
- Test: User seeks females 25-30 within 20km → daily pick must match all criteria
- Test: User has smoking dealbreaker → daily pick must be non-smoker
- Run `mvn test -Dtest=DailyServiceTest`

---

### Task 2.3: Validate CLI Input Before Processing (HIGH-03)

**Files:**
- `src/main/java/datingapp/cli/MatchingHandler.java:183`
- `src/main/java/datingapp/cli/MatchingHandler.java:541`

**Current Issue:**
Any input that isn't "l" is treated as PASS, causing:
- Typos to silently pass candidates
- Daily picks marked as viewed on invalid input
- No feedback to user about invalid choices

**Fix Strategy:**

#### 2.3.1: Create Input Validation Helper
Add to `CliUtilities`:
```java
public static Optional<String> validateChoice(String input, String... validChoices) {
    if (input == null || input.isBlank()) {
        return Optional.empty();
    }
    String normalized = input.trim().toLowerCase();
    for (String valid : validChoices) {
        if (normalized.equals(valid)) {
            return Optional.of(normalized);
        }
    }
    return Optional.empty();
}
```

#### 2.3.2: Update Candidate Browsing Logic
In `MatchingHandler.browseCandidates()`:
```java
String input = reader.readLine("\n[L]ike, [P]ass, [V]iew profile, [B]ack: ");

Optional<String> choice = CliUtilities.validateChoice(input, "l", "p", "v", "b");
if (choice.isEmpty()) {
    System.out.println("Invalid choice. Please enter L, P, V, or B.");
    continue;  // Re-prompt, don't consume the candidate
}

switch (choice.get()) {
    case "l" -> {
        matchingService.like(currentUser.getId(), candidate.getId());
        System.out.println("✓ Liked!");
    }
    case "p" -> System.out.println("Passed.");
    case "v" -> profileHandler.viewProfile(candidate, false);
    case "b" -> { return; }
}
```

#### 2.3.3: Update Daily Pick Logic
Similar validation in `viewDailyPick()`:
```java
String input = reader.readLine("\n[L]ike, [P]ass, [V]iew profile, [B]ack: ");

Optional<String> choice = CliUtilities.validateChoice(input, "l", "p", "v", "b");
if (choice.isEmpty()) {
    System.out.println("Invalid choice. Please enter L, P, V, or B.");
    continue;  // Don't mark as viewed on invalid input
}

// Only mark as viewed on valid action
dailyService.markPickViewed(currentUser.getId(), pick.getId());

switch (choice.get()) {
    // ... handle actions
}
```

**Steps:**
1. Add `validateChoice()` method to CliUtilities
2. Update all browse/pick menus to validate before processing
3. Add re-prompt loops for invalid input
4. Test with various invalid inputs

**Validation:**
- Manual test: Enter "x" when browsing → should show error and re-prompt
- Manual test: Enter "123" on daily pick → should not mark as viewed
- Ensure valid inputs still work correctly

---

## Phase 3: Medium Severity Fixes (P2 - Next Sprint)

### Task 3.1: Wire AppConfig Thresholds to Services (MED-01)

**Problem:** Hardcoded thresholds in:
- `DailyService.java:136` - "nearby" distance (5km, 10km)
- `MatchQualityService.java:25` - uses separate `MatchQualityConfig`
- `AchievementService.java:144` - "many" shared interests (3)

**Solution:**

#### 3.1.1: Extend AppConfig
Add fields to `AppConfig.java`:
```java
public class AppConfig {
    // Existing fields...

    // Matching algorithm thresholds
    private int nearbyDistanceKm = 5;
    private int regionalDistanceKm = 10;
    private int similarAgeYears = 2;
    private int broadAgeYears = 5;
    private int manySharedInterests = 3;

    // Match quality weights (consolidate from MatchQualityConfig)
    private double distanceWeight = 0.15;
    private double ageWeight = 0.10;
    private double interestWeight = 0.30;
    private double lifestyleWeight = 0.30;
    private double responseWeight = 0.15;

    // Getters and setters...
}
```

#### 3.1.2: Update DailyService Constructor
```java
public class DailyService {
    private final AppConfig config;

    public DailyService(User.Storage userStorage,
                       UserInteractions.LikeStorage likeStorage,
                       UserInteractions.BlockStorage blockStorage,
                       DailyPickStorage dailyPickStorage,
                       AppConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        // ... other fields
    }

    // Update generateHighlights() method:
    private List<String> generateHighlights(User user, User pick) {
        List<String> highlights = new ArrayList<>();
        double distKm = user.getLocation().distanceKm(pick.getLocation());

        if (distKm <= config.getNearbyDistanceKm()) {
            highlights.add("Lives nearby (" + (int) distKm + "km away)");
        } else if (distKm <= config.getRegionalDistanceKm()) {
            highlights.add("Lives in your area (" + (int) distKm + "km away)");
        }

        int ageDiff = Math.abs(user.getAge() - pick.getAge());
        if (ageDiff <= config.getSimilarAgeYears()) {
            highlights.add("Very similar age");
        } else if (ageDiff <= config.getBroadAgeYears()) {
            highlights.add("Similar age range");
        }

        int sharedCount = countSharedInterests(user, pick);
        if (sharedCount >= config.getManySharedInterests()) {
            highlights.add("Shares " + sharedCount + " interests");
        }

        return highlights;
    }
}
```

#### 3.1.3: Update MatchQualityService
Remove `MatchQualityConfig` record and use `AppConfig` instead:
```java
public class MatchQualityService {
    private final AppConfig config;

    public MatchQualityService(Match.Storage matchStorage,
                               User.Storage userStorage,
                               AppConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        // ...
    }

    private double calculateScore(User userA, User userB) {
        double distScore = scoreDistance(userA, userB) * config.getDistanceWeight();
        double ageScore = scoreAge(userA, userB) * config.getAgeWeight();
        double interestScore = scoreInterests(userA, userB) * config.getInterestWeight();
        double lifestyleScore = scoreLifestyle(userA, userB) * config.getLifestyleWeight();

        return (distScore + ageScore + interestScore + lifestyleScore) * 100;
    }
}
```

#### 3.1.4: Update AchievementService
```java
private void checkMatchingAchievements(User user) {
    int sharedInterests = getMaxSharedInterests(user);
    if (sharedInterests >= config.getManySharedInterests()) {
        grant(user.getId(), Achievement.KINDRED_SPIRIT);
    }
}
```

#### 3.1.5: Update All Service Instantiations
In `Main.java` and test files, pass `config` to affected services.

**Steps:**
1. Add threshold fields to AppConfig
2. Update DailyService constructor and logic
3. Remove MatchQualityConfig, update MatchQualityService
4. Update AchievementService to use config
5. Update all service instantiations
6. Update tests to use configurable thresholds

**Validation:**
- Test: Change `nearbyDistanceKm` to 20 → verify daily pick highlights change
- Test: Adjust match quality weights → verify scores recalculate correctly
- Run `mvn test` to ensure all tests pass

---

### Task 3.2: Create Centralized Input Validation Layer (MED-02)

**Problem:** CLI handlers can crash on out-of-range inputs:
- `ProfileHandler.java:384` - `setMaxDistanceKm()` can throw
- `ProfileHandler.java:419` - `setHeightCm()` can throw
- No consistent validation across profile edits

**Solution:**

#### 3.2.1: Create Validation Service
New file: `src/main/java/datingapp/core/ValidationService.java`

```java
package datingapp.core;

import java.util.ArrayList;
import java.util.List;

public class ValidationService {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    // Profile field validation
    public ValidationResult validateName(String name) {
        if (name == null || name.isBlank()) {
            return ValidationResult.failure("Name cannot be empty");
        }
        if (name.length() > 100) {
            return ValidationResult.failure("Name too long (max 100 chars)");
        }
        return ValidationResult.success();
    }

    public ValidationResult validateAge(int age) {
        if (age < 18) {
            return ValidationResult.failure("Must be 18 or older");
        }
        if (age > 120) {
            return ValidationResult.failure("Invalid age");
        }
        return ValidationResult.success();
    }

    public ValidationResult validateHeight(int heightCm) {
        if (heightCm < 50) {
            return ValidationResult.failure("Height too short (min 50cm)");
        }
        if (heightCm > 300) {
            return ValidationResult.failure("Height too tall (max 300cm)");
        }
        return ValidationResult.success();
    }

    public ValidationResult validateDistance(int distanceKm) {
        if (distanceKm < 1) {
            return ValidationResult.failure("Distance must be at least 1km");
        }
        if (distanceKm > 500) {
            return ValidationResult.failure("Distance too far (max 500km)");
        }
        return ValidationResult.success();
    }

    public ValidationResult validateBio(String bio) {
        if (bio == null) return ValidationResult.success();
        if (bio.length() > 1000) {
            return ValidationResult.failure("Bio too long (max 1000 chars)");
        }
        return ValidationResult.success();
    }

    public ValidationResult validateAgeRange(int min, int max) {
        List<String> errors = new ArrayList<>();

        if (min < 18) errors.add("Minimum age must be 18+");
        if (max > 120) errors.add("Maximum age invalid");
        if (min > max) errors.add("Min age cannot exceed max age");
        if (max - min < 5) errors.add("Age range too narrow (min 5 years)");

        return errors.isEmpty() ?
            ValidationResult.success() :
            ValidationResult.failure(errors);
    }

    public ValidationResult validateLocation(double latitude, double longitude) {
        List<String> errors = new ArrayList<>();

        if (latitude < -90 || latitude > 90) {
            errors.add("Invalid latitude (must be -90 to 90)");
        }
        if (longitude < -180 || longitude > 180) {
            errors.add("Invalid longitude (must be -180 to 180)");
        }

        return errors.isEmpty() ?
            ValidationResult.success() :
            ValidationResult.failure(errors);
    }
}
```

#### 3.2.2: Add Validation to ProfileHandler
```java
public class ProfileHandler {
    private final ValidationService validator;

    public ProfileHandler(..., ValidationService validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
        // ...
    }

    private void editMaxDistance() {
        System.out.print("New max distance (km): ");
        String input = reader.readLine();

        try {
            int distance = Integer.parseInt(input);
            ValidationResult result = validator.validateDistance(distance);

            if (!result.valid()) {
                System.out.println("❌ Invalid distance:");
                result.errors().forEach(e -> System.out.println("  - " + e));
                return;
            }

            currentUser.getPreferences().setMaxDistanceKm(distance);
            userStorage.save(currentUser);
            System.out.println("✓ Distance updated");

        } catch (NumberFormatException e) {
            System.out.println("❌ Please enter a valid number");
        }
    }

    private void editHeight() {
        System.out.print("New height (cm): ");
        String input = reader.readLine();

        try {
            int height = Integer.parseInt(input);
            ValidationResult result = validator.validateHeight(height);

            if (!result.valid()) {
                System.out.println("❌ Invalid height:");
                result.errors().forEach(e -> System.out.println("  - " + e));
                return;
            }

            currentUser.setHeightCm(height);
            userStorage.save(currentUser);
            System.out.println("✓ Height updated");

        } catch (NumberFormatException e) {
            System.out.println("❌ Please enter a valid number");
        }
    }
}
```

#### 3.2.3: Add Validation to Domain Models
Update `User.java` setters to use validation:
```java
public void setHeightCm(int heightCm) {
    if (heightCm < 50 || heightCm > 300) {
        throw new IllegalArgumentException("Height must be 50-300cm");
    }
    this.heightCm = heightCm;
    touch();
}
```

Update `Preferences.java`:
```java
public void setMaxDistanceKm(int maxDistanceKm) {
    if (maxDistanceKm < 1 || maxDistanceKm > 500) {
        throw new IllegalArgumentException("Distance must be 1-500km");
    }
    this.maxDistanceKm = maxDistanceKm;
}
```

**Steps:**
1. Create ValidationService class with all validation methods
2. Add ValidationService to ProfileHandler constructor
3. Wrap all profile edit operations with validation checks
4. Add comprehensive unit tests for ValidationService
5. Update domain model setters with validation
6. Test CLI flows with invalid inputs

**Validation:**
- Manual test: Enter -50 for distance → should show friendly error
- Manual test: Enter 500 for height → should show friendly error
- Run `mvn test -Dtest=ValidationServiceTest`
- Run `mvn test -Dtest=ProfileHandlerTest`

---

### Task 3.3: Implement Unblock Functionality (MED-03)

**Problem:**
- No way to unblock users without DB edits
- `BlockStorage` interface missing delete method
- CLI/UI have no unblock option

**Solution:**

#### 3.3.1: Add Delete Method to BlockStorage Interface
In `UserInteractions.java`:
```java
public interface BlockStorage {
    void save(Block block);
    List<Block> findByBlocker(UUID blockerId);
    boolean isBlocked(UUID blockerId, UUID blockedId);
    boolean delete(UUID blockerId, UUID blockedId);  // NEW
}
```

#### 3.3.2: Implement in H2ModerationStorage
```java
@Override
public boolean delete(UUID blockerId, UUID blockedId) {
    String sql = "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setObject(1, blockerId);
        stmt.setObject(2, blockedId);

        int rowsDeleted = stmt.executeUpdate();
        return rowsDeleted > 0;

    } catch (SQLException e) {
        throw new StorageException("Failed to delete block: " + blockerId + " -> " + blockedId, e);
    }
}
```

#### 3.3.3: Add Unblock Method to TrustSafetyService
```java
public boolean unblock(UUID blockerId, UUID blockedId) {
    Objects.requireNonNull(blockerId, "blockerId");
    Objects.requireNonNull(blockedId, "blockedId");

    boolean deleted = blockStorage.delete(blockerId, blockedId);
    if (deleted) {
        logger.info("User {} unblocked user {}", blockerId, blockedId);
    }
    return deleted;
}

public List<User> getBlockedUsers(UUID userId) {
    return blockStorage.findByBlocker(userId).stream()
            .map(block -> userStorage.get(block.blockedId()))
            .filter(Objects::nonNull)
            .toList();
}
```

#### 3.3.4: Add CLI Menu in SafetyHandler
```java
private void manageBlockedUsers() {
    List<User> blocked = trustSafetyService.getBlockedUsers(currentUser.getId());

    if (blocked.isEmpty()) {
        System.out.println("You haven't blocked anyone.");
        return;
    }

    System.out.println("\n" + CliConstants.HEADER_BLOCKED_USERS);
    for (int i = 0; i < blocked.size(); i++) {
        User user = blocked.get(i);
        System.out.printf("%d. %s (@%s)%n", i + 1, user.getName(), user.getId());
    }

    System.out.print("\nEnter number to unblock (or 0 to go back): ");
    String input = reader.readLine();

    try {
        int choice = Integer.parseInt(input);
        if (choice == 0) return;

        if (choice < 1 || choice > blocked.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        User toUnblock = blocked.get(choice - 1);
        boolean success = trustSafetyService.unblock(currentUser.getId(), toUnblock.getId());

        if (success) {
            System.out.println("✓ Unblocked " + toUnblock.getName());
        } else {
            System.out.println("❌ Failed to unblock user");
        }

    } catch (NumberFormatException e) {
        System.out.println("Please enter a valid number.");
    }
}

// Add to main safety menu
private void showSafetyMenu() {
    // ... existing options
    System.out.println("4. Manage blocked users");
    // ...
}
```

#### 3.3.5: Update InMemoryBlockStorage for Tests
```java
@Override
public boolean delete(UUID blockerId, UUID blockedId) {
    return blocks.removeIf(b ->
        b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId)
    );
}
```

**Steps:**
1. Add `delete()` method to BlockStorage interface
2. Implement in H2ModerationStorage
3. Add unblock methods to TrustSafetyService
4. Create CLI menu for managing blocked users
5. Add CliConstants for new UI strings
6. Update test mocks with delete method
7. Add unit and integration tests

**Validation:**
- Test: Block user → unblock → verify they appear in candidates again
- Test: Attempt to unblock non-blocked user → should return false
- Run `mvn test -Dtest=TrustSafetyServiceTest`
- Run `mvn test -Dtest=H2ModerationStorageTest`

---

## Phase 4: Architecture & Cleanup (P3 - Future)

### Task 4.1: Consolidate Schema Management

**Problem:** Schema creation split between:
- `DatabaseManager.initializeSchema()` (core tables)
- Individual `H2*Storage.ensureSchema()` methods (feature tables)

**Solution:**

#### 4.1.1: Move All Schema to DatabaseManager
Create dedicated methods:
```java
private void createCoreSchema() throws SQLException {
    // users, matches, likes, swipe_sessions
}

private void createMessagingSchema() throws SQLException {
    // conversations, messages
}

private void createSocialSchema() throws SQLException {
    // friend_requests, notifications
}

private void createModerationSchema() throws SQLException {
    // blocks, reports
}

private void createProfileSchema() throws SQLException {
    // profile_notes, profile_views, user_interests, user_achievements
}

private void createMatchingSchema() throws SQLException {
    // daily_pick_views, dealbreakers
}
```

Update `initializeSchema()`:
```java
public void initializeSchema() {
    try {
        createCoreSchema();
        createMessagingSchema();
        createSocialSchema();
        createModerationSchema();
        createProfileSchema();
        createMatchingSchema();
        addMissingForeignKeys();
        logger.info("Database schema initialized");
    } catch (SQLException e) {
        throw new StorageException("Failed to initialize schema", e);
    }
}
```

#### 4.1.2: Remove ensureSchema() from Storage Classes
Delete or simplify `ensureSchema()` methods in:
- H2SocialStorage
- H2ModerationStorage
- H2ProfileDataStorage
- H2ConversationStorage
- H2MessageStorage
- H2DailyPickStorage

#### 4.1.3: Create Schema Version Tracking
Add table:
```sql
CREATE TABLE IF NOT EXISTS schema_version (
    version INT PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL,
    description VARCHAR(255)
);
```

**Benefits:**
- Single source of truth for schema
- Predictable initialization order
- Easier to add migration system later

---

### Task 4.2: Consolidate Configuration

**Problem:** `MatchQualityConfig` exists separately from `AppConfig`

**Solution:** Already addressed in Task 3.1

---

### Task 4.3: Extract User Nested Classes

**Problem:** `User.java` contains nested interfaces making file large and complex

**Solution:** (OPTIONAL - Low priority)

Create separate files:
- `UserStorage.java` - Extract `User.Storage` interface
- `DatabaseRecord.java` - Extract `User.DatabaseRecord`

Update imports across codebase.

**Note:** This is cosmetic and can be deferred indefinitely.

---

## Phase 5: Feature Additions (P4 - Future)

### Task 5.1: Implement Unmatch Functionality

Similar to unblock:
1. Add `delete()` to Match.Storage
2. Implement in H2MatchStorage
3. Add unmatch method to MatchingService
4. Create CLI menu in MessagingHandler

---

### Task 5.2: Add Photo URL Validation

When user adds photo URLs:
1. Validate URL format
2. Optional: Ping URL to verify it's accessible
3. Optional: Validate Content-Type is image/*

---

### Task 5.3: Internationalization (i18n)

**Large effort - separate project:**
1. Extract all UI strings to resource bundles
2. Create `messages_en.properties`, `messages_es.properties`, etc.
3. Update CLI/UI to load strings from bundles
4. Add locale selection in settings

---

## Testing Strategy

### Unit Tests Required
- [ ] ValidationService - All validation methods
- [ ] MessagingService.countUnread() - Own messages excluded
- [ ] DailyService - Preference filtering
- [ ] MatchQualityService - Config-driven weights
- [ ] TrustSafetyService - Unblock functionality
- [ ] CliUtilities.validateChoice() - Input validation

### Integration Tests Required
- [ ] H2ModerationStorage - Unblock delete
- [ ] DatabaseManager - FK cascade on user delete
- [ ] H2ConversationStorage - Message FK cascade
- [ ] All storage classes with new FK constraints

### Manual Testing Checklist
- [ ] CLI candidate browsing with invalid input
- [ ] Daily pick with preference violations
- [ ] User deletion cascades all related data
- [ ] Unread count excludes own messages
- [ ] Block → Unblock → Candidate reappears
- [ ] Profile edits with out-of-range values

---

## Rollout Plan

### Week 1: Critical Fixes
- Day 1: Fix CLI string constant (CRIT-01)
- Day 2-3: Add FK constraints and test cascades (CRIT-02)
- Day 4: Verify no regressions, run full test suite

### Week 2: High Severity
- Day 1: Fix unread count logic (HIGH-01)
- Day 2: Add preference filters to daily picks (HIGH-02)
- Day 3: Implement input validation in CLI (HIGH-03)
- Day 4-5: Integration testing, edge cases

### Week 3: Medium Severity
- Day 1-2: Wire AppConfig to services (MED-01)
- Day 3: Create ValidationService (MED-02)
- Day 4-5: Implement unblock functionality (MED-03)

### Week 4+: Architecture & Features
- Schema consolidation
- Optional enhancements

---

## Success Criteria

### Phase 1 (Critical)
✅ CLI headers display correctly
✅ User deletion removes all related data without errors
✅ No orphaned records in any table

### Phase 2 (High)
✅ Unread counts are accurate
✅ Daily picks respect all user preferences
✅ Invalid CLI input prompts for correction, doesn't consume actions

### Phase 3 (Medium)
✅ All hardcoded thresholds moved to AppConfig
✅ Profile edits validate inputs and show friendly errors
✅ Users can view and unblock blocked users

### Phase 4+ (Architecture)
✅ Schema defined in single location
✅ Code coverage maintains >60%
✅ All tests pass: `mvn verify`

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| FK CASCADE deletes too much data | Low | High | Comprehensive testing with sample data |
| Config changes break existing tests | Medium | Low | Update tests to use AppConfig |
| Schema migration on production DB | N/A | High | Currently no production deployment |
| Breaking changes to Storage interfaces | Medium | Medium | Update all implementations together |
| CLI changes confuse existing users | Low | Low | Maintain existing command structure |

---

## Notes

- All changes must pass `mvn spotless:apply` before commit
- Maintain 60%+ test coverage (run `mvn jacoco:report`)
- Update CLAUDE.md if architecture patterns change
- Add entries to agent changelog for significant changes
- Consider creating feature branches for each phase

---

## Appendix: Quick Reference Commands

```bash
# Before starting work
git pull
mvn clean compile

# After each task
mvn spotless:apply
mvn test -Dtest=<SpecificTest>
mvn verify

# Before commit
mvn spotless:check
mvn jacoco:report
# Verify coverage >= 60%

# Build and test full flow
mvn clean package
java -jar target/dating-app-1.0.0-shaded.jar
```

---

**Document Version:** 1.0
**Created:** 2026-01-27
**Author:** AI Agent (Claude Code)
**Status:** Active - Ready for Implementation
