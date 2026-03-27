---

# PART 3: MEDIUM PRIORITY ISSUES

## MED-01: Hardcoded Algorithm Constants
- **Files:** `DailyService.java`, `MatchQualityService.java`, `AchievementService.java`
- **Values Not in AppConfig:**
  - Distance thresholds: 5km, 10km (DailyService:136-139)
  - Age difference thresholds: 2, 5 years (DailyService:143-144)
  - Interest count threshold: 3 (DailyService:168)
  - Pace compatibility threshold: 50 (MatchQualityService:25)
  - Response time thresholds: 1hr, 24hr, 72hr (MatchQualityService:612-634)
  - Achievement thresholds: 1, 5, 10, 50 matches (AchievementService:144-148)
- **Impact:** Can't tune algorithms without code changes

## MED-02: Missing Input Validation in ProfileHandler
- **File:** `ProfileHandler.java:378-432`
- **Issues:**
  - Distance validation silently keeps default on error (no user feedback)
  - No bounds checking (could accept -500 distance)
  - Age range not validated (minAge <= maxAge)
  - Height has no range validation
- **Impact:** Invalid data can be entered

## MED-03: Missing Confirmation Dialogs
- **Files:** `DashboardController.java:175-179`, `ProfileController.java:600-602, 721-731`
- **Missing Confirmations:**
  - Logout (immediate session wipe)
  - Clear all dealbreakers
  - Friend zone request sends immediately
- **Impact:** Accidental data loss/logout

## MED-04: Missing Error State Handling in UI
- **Files:** `LoginViewModel.java`, `DashboardViewModel.java`, `MatchingViewModel.java`, `ChatViewModel.java`
- **Description:** All catch blocks log errors but don't show user feedback
- **Impact:** Users see silent failures; app appears broken

## MED-05: Database Indexes Missing
- **File:** `DatabaseManager.java`
- **Missing Indexes:**
  - `users.state` - used in findActive query
  - `users.gender, state` - composite for candidate finding
  - `messages.conversation_id, created_at DESC` - message pagination
  - `conversations.last_message_at DESC` - conversation ordering
  - `friend_requests.to_user_id` - pending requests query
  - `notifications.created_at` - notification ordering
  - `profile_views.viewer_id` - views by viewer
  - `daily_pick_views.user_id` - hasViewed queries
- **Impact:** Full table scans; slow queries at scale

## MED-06: Schema Initialization Split Across Classes
- **File:** `DatabaseManager.java` + individual H2*Storage classes
- **Description:** Tables created in both DatabaseManager AND storage class constructors
- **Impact:** Initialization order dependency; potential race conditions

## MED-07: Inconsistent INSERT vs MERGE Pattern
- **Files:** `H2UserStorage.java` (MERGE), `H2MatchStorage.java` (INSERT)
- **Description:** UserStorage uses upsert, MatchStorage uses insert-only
- **Impact:** Calling `save()` on existing match throws exception

## MED-08: Missing Delete Operations in Interfaces
- **Missing `delete()` methods:**
  - `UserInteractions.BlockStorage` - can't unblock users
  - `UserInteractions.ReportStorage` - can't retract reports
  - `Achievement.UserAchievementStorage` - can't reset achievements
  - `User.ProfileViewStorage` - can't clear view history

## MED-09: Dead Code - Unused Public Methods
- **File:** `RelationshipHandler.java:41-82`
- **Methods:** `handleFriendZone(UUID)`, `handleGracefulExit(UUID)`
- **Description:** Public methods never called from Main.java or anywhere else
- **Impact:** Maintenance burden; confusing API

## MED-10: Missing Pagination in CLI Lists
- **Files:** `SafetyHandler.java:56-59`, `ProfileHandler.java:759-768`
- **Description:** Lists all users without limit - could print thousands
- **Impact:** Console overflow; unusable with large datasets

## MED-11: User Invariant Violations via Setters
- **File:** `User.java`
- **Issues:**
  - `setInterestedIn(emptySet)` - violates isComplete() requirement
  - `setPhotoUrls(emptyList)` - violates isComplete() requirement
  - `setMaxDistanceKm()` - no maximum validation
  - `setAgeRange()` - no upper bound on maxAge
- **Impact:** Incomplete profiles can pass validation

## MED-12: Missing Defensive Copies
- **File:** `User.java:500-502`
- **Issue:** `getPacePreferences()` returns direct reference
- **File:** `User.DatabaseRecord.Builder:322-325, 352-355`
- **Issue:** Builder accepts direct references to mutable collections

## MED-13: Match Popup Navigation Dead End
- **File:** `MatchingController.java:333-384`
- **Description:** After match popup, "Send Message" navigates to CHAT without setting context
- **Impact:** User sees empty chat with no conversation selected

## MED-14: Missing Loading State Implementation
- **Files:** `DashboardController.java:92-115`, Chat, Matches, Profile controllers
- **Description:** Loading just dims content - no spinner/skeleton
- **Impact:** Unclear if app is working or frozen

## MED-15: Super Like Button Has No Handler
- **File:** `matching.fxml:145`
- **Description:** Fourth action button visible but no onAction binding
- **Impact:** Feature appears available but does nothing

## MED-16: Hardcoded Text (No i18n)
- **Files:** All FXML files
- **Count:** 50+ hardcoded English strings
- **Impact:** Cannot translate app; must modify FXML for localization
- **comment from the user(me):** This is acceptable for MVP but should be addressed later. also, english is great. we dont need to worry about it anytime soon.

## MED-17: Missing Keyboard Shortcuts
- **File:** `matching.fxml:131-149`
- **Description:** Tooltips show keyboard shortcuts (Ctrl+Z, arrows) but no KeyEvent handlers
- **Impact:** Documented shortcuts don't work
- **comment from the user(me):** i think that maybe its somehow already working, or parts of it.

## MED-18: Animation Performance Issues
- **File:** `theme.css`
- **Issues:**
  - Heavy drop shadows (25-30px blur radius) on hover states
  - Scale transforms without GPU optimization
  - Radial gradients on frequently-updated elements
- **Impact:** Laggy UI on lower-end hardware
- **comment from the user(me):** you can try to optimize it if you can, but i dont want to chnage or lower the quality of the design.


## MED-19: Stats Record Missing Validation
- **File:** `Stats.java:77-89`
- **Description:** UserStats allows negative counts; no validation that likesGiven <= totalSwipesGiven
- **Impact:** Invalid stats can be constructed

## MED-20: SwipeSession Missing Invariant Validation
- **File:** `SwipeSession.java:52-73, 106-110`
- **Issues:**
  - No validation swipeCount, likeCount, passCount non-negative
  - incrementMatchCount() doesn't check matchCount <= likeCount
- **Impact:** Invalid session states possible

---