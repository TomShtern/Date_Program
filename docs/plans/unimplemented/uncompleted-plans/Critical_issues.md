---

# PART 1: CRITICAL ISSUES (Must Fix Immediately)

## CRIT-01: CLI String Literal Constants Not Resolved
- **Severity:** CRITICAL
- **Files:** `MatchingHandler.java`, `StatsHandler.java`
- **Description:** Code prints literal string "CliConstants.HEADER_BROWSE_CANDIDATES" instead of the actual constant value. 13 occurrences across 2 files.
- **Locations:**
  - `MatchingHandler.java:127, 177, 180, 259, 260, 289, 534`
  - `StatsHandler.java:41, 49, 72, 87, 114, 137`
- **Impact:** CLI UI is completely broken - users see garbage output
- **Fix:** Change `logger.info("CliConstants.HEADER_...");` to `logger.info(CliConstants.HEADER_...);`

## CRIT-02: Missing FXML onAction Handlers
- **Severity:** CRITICAL
- **Files:** `matching.fxml`, `chat.fxml`, Controllers
- **Description:** 5 FXML buttons reference handler methods that don't exist in controllers
- **Missing Handlers:**
  - `matching.fxml:18` → `handleBack()` - NOT in MatchingController
  - `matching.fxml:90` → `handleExpandPreferences()` - NOT in MatchingController
  - `matching.fxml:97` → `handleCheckLikes()` - NOT in MatchingController
  - `matching.fxml:104` → `handleImproveProfile()` - NOT in MatchingController
  - `chat.fxml:67` → `handleBrowseMatches()` - NOT in ChatController
- **Impact:** RuntimeException on button click; app crashes
- **Fix:** Add @FXML annotated methods to respective controllers

## CRIT-03: ResultSet Resource Leaks (30+ locations)
- **Severity:** CRITICAL
- **Files:** All `H2*Storage.java` classes
- **Description:** ResultSet not closed in try-with-resources; only Connection and PreparedStatement are managed
- **Pattern Found:**
```java
// WRONG - ResultSet leaks
try (Connection conn = ...; PreparedStatement stmt = ...) {
    ResultSet rs = stmt.executeQuery(); // NOT in try-with-resources
    if (rs.next()) { return rs.getInt(1); }
}
```
- **Impact:** Connection exhaustion under load; database lockups
- **Fix:** Add ResultSet to try-with-resources: `try (Connection conn = ...; PreparedStatement stmt = ...) { try (ResultSet rs = stmt.executeQuery()) {...} }`

## CRIT-04: Missing CASCADE DELETE on 13 Tables
- **Severity:** CRITICAL
- **File:** `DatabaseManager.java`, all `H2*Storage.java`
- **Tables Missing ON DELETE CASCADE:**
  1. `likes` - 2 FKs to users
  2. `matches` - 2 FKs to users
  3. `swipe_sessions` - 1 FK to users
  4. `user_stats` - 1 FK to users
  5. `friend_requests` - 2 FKs to users
  6. `notifications` - 1 FK to users
  7. `blocks` - 2 FKs to users
  8. `reports` - 2 FKs to users
  9. `profile_notes` - NO FK constraints at all
  10. `profile_views` - NO FK constraints at all
  11. `conversations` - NO FK constraints
  12. `messages` - Missing FK for sender_id
  13. `user_achievements` - Missing FK to users
- **Impact:** Cannot delete users; orphan data accumulates; FK violations
- **Fix:** Add `ON DELETE CASCADE` to all foreign key constraints

## CRIT-05: Missing CSS Class Definitions
- **Severity:** CRITICAL
- **Files:** `profile.fxml`, `login.fxml`, `preferences.fxml`, `theme.css`
- **Missing Classes (used in FXML but not defined in CSS):**
  - `.field-label` - Used 6 times in profile.fxml
  - `.button-secondary-small` - Used 2 times in profile.fxml
  - `.content-container` - Used in login.fxml
  - `.icon-button` - Used in preferences.fxml
  - `.toggle-group-container` - Used in preferences.fxml
  - `.settings-toggle` - Used 3 times in preferences.fxml
- **Impact:** Controls render with default JavaFX styling; broken UI appearance
- **Fix:** Add CSS class definitions to theme.css

## CRIT-06: User.Storage Missing Delete Method
- **Severity:** CRITICAL
- **File:** `src/main/java/datingapp/core/User.java:49-67`
- **Description:** User.Storage interface has no `delete(UUID id)` method. When user is banned/deleted, related data cannot be cleaned up.
- **Impact:** GDPR right-to-be-forgotten violation; data accumulates forever
- **Fix:** Add `void delete(UUID id);` to User.Storage interface and implement cascade delete

---