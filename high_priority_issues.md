
# PART 2: HIGH PRIORITY ISSUES

## HIGH-01: Missing ViewModel Cleanup/Dispose Methods
- **Severity:** HIGH
- **Files:** All 8 ViewModels
- **Description:** ViewModels spawn background threads and add listeners but have no cleanup mechanism
- **Affected ViewModels:**
  - `LoginViewModel.java` - No cleanup for loaded users list
  - `DashboardViewModel.java` - No cleanup for subscriptions
  - `MatchingViewModel.java:92` - Virtual thread never stopped
  - `ChatViewModel.java:42` - Listener never removed
  - `MatchesViewModel.java` - No cleanup
  - `ProfileViewModel.java` - No cleanup
  - `PreferencesViewModel.java` - No cleanup
  - `StatsViewModel.java` - No cleanup
- **Impact:** Memory leaks accumulate on every screen navigation

## HIGH-02: Listener Cleanup Not Called (6 Controllers)
- **Severity:** HIGH
- **Files:** All controllers except ProfileController
- **Description:** BaseController provides `cleanup()` method but only ProfileController calls it
- **Affected:** LoginController, DashboardController, MatchingController, ChatController, MatchesController, PreferencesController, StatsController
- **Impact:** Subscriptions accumulate; memory leaks

## HIGH-03: Race Condition in MatchingService
- **Severity:** HIGH
- **File:** `MatchingService.java:94-109`
- **Description:** TOCTOU vulnerability:
```java
if (!matchStorage.exists(match.getId())) {  // Check
    matchStorage.save(match);               // Act - race window
}
```
- **Impact:** Duplicate matches created under concurrent load

## HIGH-04: Race Condition in SessionService
- **Severity:** HIGH
- **File:** `SessionService.java:68-87`
- **Description:** Swipe count check and save are not atomic
- **Impact:** Anti-bot limits bypassable under concurrent requests

## HIGH-05: Missing Login Checks in RelationshipHandler
- **Severity:** HIGH
- **File:** `RelationshipHandler.java:85-87, 136-138`
- **Description:** `viewPendingRequests()` and `viewNotifications()` don't check if user is logged in
- **Impact:** NullPointerException if called without logged-in user

## HIGH-06: Broken Observable Binding in MatchesController
- **Severity:** HIGH
- **File:** `MatchesController.java:80`
- **Code:** `viewModel.getMatches().subscribe(...)`
- **Problem:** ObservableList doesn't have `.subscribe()` - this is ReactiveX API, not JavaFX
- **Impact:** Runtime failure when matches list changes

## HIGH-07: Navigation History Not Tracked
- **Severity:** HIGH
- **File:** `NavigationService.java:190-192`
- **Description:** `goBack()` always goes to MATCHING, ignoring actual history
- **Impact:** Users can't reliably navigate backward

## HIGH-08: Zero Test Coverage for GeoUtils.distanceKm()
- **Severity:** HIGH
- **File:** `CandidateFinder.java:74-106`
- **Description:** Haversine formula implementation has zero unit tests
- **Impact:** Distance calculation bugs in matching algorithm go undetected

## HIGH-09: Zero Test Coverage for User.ProfileNote
- **Severity:** HIGH
- **File:** `User.java:930-993`
- **Description:** Nested record with validation logic completely untested
- **Impact:** Note length limits, blank prevention, self-note prevention all untested

## HIGH-10: Missing Transaction Handling
- **Severity:** HIGH
- **Files:** All storage classes
- **Description:** No explicit transaction support (setAutoCommit, commit, rollback)
- **Impact:** Multi-step operations can leave inconsistent state on partial failure

## HIGH-11: TrustSafetyService Constructor Null Acceptance
- **Severity:** HIGH
- **File:** `TrustSafetyService.java:30, 35`
- **Description:** Constructor allows null dependencies; NPE deferred to runtime
- **Impact:** Crashes when `report()` is called if dependencies not set

## HIGH-12: Missing Null Checks in DailyService
- **Severity:** HIGH
- **File:** `DailyService.java:95-99`
- **Description:** No null check for userStorage before calling findActive()
- **Impact:** NPE if getDailyPick() called before dependencies configured

---