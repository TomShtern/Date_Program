# Comprehensive Dating App Codebase Gap Analysis

This report synthesizes findings from 5 AI analyses (Claude, Minimax, Qwen, GPT5.2, Code-First) into a single, deduplicated master list of codebase gaps, technical debt, and missing features.

**Note on Current Baseline:** The `Code-First` report verified that the core test suite (including `DashboardViewModelTest`) is currently **green** and stable, `InteractionStorage` atomicity for friend transitions is resolved, and the `JdbiUserStorage` normalized data migration is completed. The remaining gaps focus on the verified technical debt and feature parity.

---

## 🔴 1. Critical & High Priority (Bugs, Security, Thread Safety)

### Security & Authorization
- **SQL Injection Risk:** `MigrationRunner.tableExists()` uses raw string concatenation instead of parameterized queries. [Qwen]
- **Missing API Authorization:** `/api/users/{userId}` requires no validation of access. `/api/conversations/{conversationId}/messages` allows reading messages with predictable IDs and no ownership validation. [Qwen]

### Concurrency & Thread Safety
- **Event Bus Modification:** `AppEventBus` iterates over `subscribers` directly, making it vulnerable to `ConcurrentModificationException` during dispatch. [Qwen]
- **Unsynchronized Maps:** `ActivityMetricsService` uses a non-thread-safe `HashMap` for `swipeCounts` and `likeCounts`. [Qwen]
- **Async Task Race Conditions:** Shared `candidateQueue` in `MatchingViewModel` can cause race conditions during rapid navigation. [Qwen]

### Resource & Memory Leaks
- **Hikari Connection Leak:** `DatabaseManager` creates a `HikariDataSource` but has no shutdown/cleanup hook. [Qwen]
- **File Handle Leak:** `LocalPhotoStore.savePhoto()` opens `FileInputStream` without closing it (no try-with-resources). [Qwen]
- **Background Polling Leak:** `ChatViewModel` message polling handles are not properly cancelled on navigation (`dispose()` not guaranteed). [Qwen]
- **Unbounded Collections/Caches:** `ImageCache` has LRU eviction but no maximum memory bounds. `ChatViewModel`'s `messageCache` grows indefinitely with no eviction. [Qwen]

### Runtime Errors & Uncaught Exceptions
- **Hanging Pagination Loop:** `StatsViewModel.fetchMessagesExchanged()` iterates over all conversations synchronously with no upper bound or timeout. [Claude]
- **NPE Risks:** `RestApiServer.extractRecipientFromConversation()` splits strings without a null check. `ConnectionService.sendMessage()` sanitizer can return null, checked too late. [Qwen]
- **Silent Exception Swallowing:** `ConnectionService.markAsRead()` and `TrustSafetyService.updateMatchStateForBlock()` catch exceptions but swallow them, leaving callers unaware of failure. [Qwen, Claude]
- **Crashing UI Adapters:** `UseCaseUiProfileNoteDataAccess` throws `IllegalStateException` on missing use-case instead of degrading gracefully. [Claude]
- **JDBI Binding Failure:** `findCandidates()` gender list binding can fail entirely on an empty set. [Qwen]

---

## 🟠 2. Core Implementation Gaps (Logic & Data Integrity)

### Database Constraints & Schema
- **Duplicate Matches Possible:** `matches` table lacks a `UNIQUE` constraint on `(user1_id, user2_id, created_at)`. [Qwen]
- **Missing Geo Index:** Candidate table is missing a composite index for `(state, gender, birth_date, lat, lon)`. [Claude]
- **Orphaned Conversations:** `unmatch()` archives the match state, but the conversation remains accessible (no cascade delete). [Qwen]
- **Non-Atomic Actions:** Graceful exit and unmatch operations process conversation archival *outside* the atomic match transition transaction. [Qwen]

### Data Query Inefficiencies & Pagination
- **N+1 Query Pattern:** `ConnectionService.getConversations()` loops over a list to hit DB individually for the other user. Default `countMessagesByConversationIds` does N+1 COUNT queries. [Qwen, Claude]
- **Missing Bulk Pagination:** `CandidateFinder` and `TrustSafetyStorage.getBlockedUserIds()` load the entire system/user list into memory without LIMIT/OFFSET. [Claude, Minimax]
- **Pagination Edge Cases:** `JdbiConnectionStorage.getMessages()` doesn't handle empty result sets/last page correctly with its LIMIT/OFFSET. [Qwen]

### Event System Disconnects
- **Missing Handlers:** `MatchCreated`, `FriendRequestAccepted`, and `MessageSent` events are fired but have no UI notification handlers. Users are not notified of new matches or messages. [Claude, Qwen, Minimax]
- **Missing Event Fires:** `SocialUseCases.blockUser()` and `reportUser()` fire no events (breaking the `GUARDIAN` achievement and metrics). `ProfileUseCases.saveProfile()` never fires `ProfileSaved(activated=true)` (breaking profile achievements). `SocialUseCases` does not fire `FriendRequestReceived`. [Claude, Qwen]
- **Missing Subscriptions:** GUI ViewModels (e.g., Dashboard) don't subscribe to the `AppEventBus` to update counts responsively. [Claude]
- **Achievement Systems Disconnected:** Complete separation between Backend `EngagementDomain.Achievement` (11 items) and UI `MilestonePopupController.AchievementType` (10 items). Milestone popups are dead code and never trigger. [Claude]

### Configuration & Maintenance Traps
- **Logic Duplication:** `User.isComplete()` and `ProfileActivationPolicy.canActivate()` manually check the exact same 10 fields individually. [Claude, Minimax]
- **Config Validation Missing:** `AppConfigValidator` doesn't validate that Standout scoring weights sum to 1.0. [Claude]
- **Config Ignored:** `ValidationConfig.maxPhotos` (defaults to 2) and max interests (`MAX_PER_USER=10`) are bypassed by hardcoded constants in the UI/storage. Daily pass limits and `dailySuperLikeLimit` exist in config but are NOT enforced in the swipe flow. [Claude, GPT5.2]
- **Misleading Timers:** `SafetyConfig.sessionTimeoutMinutes` applies solely to metrics session grouping, not security login timeouts. [Claude]
- **Centralized Codecs:** Enum JDBI codecs are decoded inline via `SqlRowReaders.readEnum()` instead of centralized in `StorageFactory` (except `Interest`). [Claude]

### Soft Delete & Cleanup Lifecycle
- **Incomplete Soft Delete:** `markDeleted` exists on models but is unused. `softDeleteRetentionDays` is not wired, no purge job. [GPT5.2]
- **Inactive Cleanup:** Scheduled cleanup routines for sessions, daily picks, standouts, and undo logs are unimplemented. [GPT5.2]

---

## 🟡 3. REST API & Backend Missing Features

### Unimplemented REST Endpoints
- Over 60% of Use Case operations are missing REST counterparts, notably: Profile Update (PUT), Super Like, Block, Report, Notifications, Friend Request, Stats, Achievements, Undo Swipe, and Standouts. [Claude, Minimax]

### Bypassed Architecture
- **REST API Short-Circuit:** 5 endpoints (e.g., `GET /api/users/{id}`, `GET /api/users/{id}/candidates`) bypass `app/usecase` entirely and call core services directly, skipping events/validation. [Claude]

### Missing Domain Models & Logic Flaws
- **User Models:** Missing Occupation, Religion, Body Type, Education, Languages, Political Views, Social Media Links, Active Status/Last Seen. [Minimax]
- **Match Models:** Missing Match Expiration, Super Like persistence, Compatibility Score persistence (stored separately), Conversation ID reference. [Minimax]
- **Unimplemented Concepts:** Story/Reel, Virtual Gifts, Group Chat, Profile Boost. [Minimax]
- **Validation Missing:** Missing proper validation limits on `displayName` / `bio` max lengths, Email format validation, and true bounding box checks for age (allows negative or >150). [Qwen]
- **Time/Location Bugs:** Timezones use system-default `AppClock.now()` leading to inconsistent server behavior. Geo-location check fails for exactly `[0.0, 0.0]` (Gulf of Guinea). [Claude, Qwen]

### Missing Use Cases & Algorithms
- **Missing Use Cases:** User Registration/Login, Password Change/Reset, Account Deletion, Unblock User, Delete Message, Delete Conversation, Mark all notifications read, Archive Match. [Minimax]
- **Missing Business Policies:** `SwipePolicy`, `UndoPolicy`, `RecommendationPolicy`, `MessagePolicy`. [Minimax]
- **Missing Algorithms:** `CandidateFinder` has no "most compatible" sorting. `RecommendationService` lacks location-based recommendations. `TrustSafetyService` lacks automated content moderation (profanity/image scanning) and trust scores. No shadow banning or account recovery flow. [Minimax, Qwen]

---

## 🔵 4. UI/UX & Frontend Gaps (JavaFX & Continuity)

### "Faux Features" (Scaffolding but no Backend)
- **Presence & Typing:** Full UI logic exists in `ChatViewModel`, but is wired to `NoOpUiPresenceDataAccess` meaning it permanently forces OFFLINE/false. [Minimax, Claude]
- **Super Like:** Button and shortcuts exist, but dispatch as identical to standard "LIKE" with no distinct backend logic, daily limits, or persistence. [Minimax, Claude]

### Missing GUI / CLI Parity
- **Pace Preferences:** Required for profile completeness, but lacks a JavaFX GUI editor (only configurable in CLI). [GPT5.2]
- **Profile Verification:** Verification flow is simulated in the CLI but has no JavaFX or REST equivalent. [GPT5.2]
- **Profile Notes:** GUI allows basic saving (silently, without feedback) but lacks bulk viewing/management which CLI has. Safety and Notes controllers are thin list views with no inline actions (e.g., unblocking). [Claude, Code-First]

### Notifications & UX Indicators
- **Read Receipts:** Tracked in DB timestamp but completely missing from Chat UI display. [Minimax]
- **Message Sending Status:** Sending a message in `ChatController` has no loading spinner or anti-spam disable, allowing duplicate sends. [Claude]
- **Image Validation:** Uploading photos checks 5MB size but accepts non-mimetype images. Does not correct EXIF rotation, load progressively, or display missing validation text. [Qwen, Minimax]

### Missing Elements & Workflows
- **Context Missing:** Standouts and Daily Pick screens provide no context/reasoning labels on *why* they were chosen. Cannot view undo history. [Code-First, Minimax]
- **Silent Empty States:** `CandidateFinder` silently returns empty for users missing location without helping the user understand why. [Claude]
- **Missing Screens/Features:** No App Settings panel, Onboarding flow, interactive "Match Preferences", Search users, or "Who Liked Me" blurring implementations. Stats screen lacks basic charts. [Minimax]
- **State Bleed:** `ProfileController` doesn't clear state on navigation away, leaving forms stale. Unsaved change warning missing entirely. [Qwen, Claude]

---

## 🟢 5. Quality of Life, Testing & Tech Debt

### Testing & Observability Gaps
- **Missing Analytics Storage:** Not tracking Message response time, Conversation depth, Time-to-first-date, Retention/cohorts, Profile view trends, Revenue/monetization data. [Minimax]
- **Missing Logs:** No logging correlation IDs across layers, no performance metrics for slow queries. [Qwen]
- **Untested Hotspots:** `ProfileHandler` (~1k loc), `MatchingHandler` (~1k loc), `ConnectionService` (540 loc), `DefaultCompatibilityCalculator`, and all GUI UI screens. Integration Tests, load tests, and security tests are at 0%. [Claude, Qwen, Minimax, Code-First]

### Unwired Configurations & Background Jobs
- **Unused Limits:** `dailySuperLikeLimit` exists in config but is not enforced. Daily pass limits exist but are not enforced in the swipe flow. Max photos and interests are hardcoded as constants rather than using `AppConfig`. [GPT5.2]
- **Missing Background Jobs:** Cleanup routines for sessions, daily picks, standouts, and undo windows exist but lack a scheduler/wiring to actually run. [GPT5.2]
- **Incomplete Soft Delete:** `markDeleted` exists but is unused; `softDeleteRetentionDays` is not wired, and purge is never scheduled. [GPT5.2]

### Granular Feature & UI Gaps (Nice-to-Have)
- **Granular Message Features:** Missing message attachments, voice messages, video calls, message reactions, message editing, message delete for both, and delivery status. [Minimax]
- **Granular Media Features:** Missing image compression, EXIF orientation handling, dimension validation, thumbnail generation, true MIME type validation, progressive loading, image cropping, and GIF/WebP support. [Minimax]
- **Granular Social/Search Features:** Missing "People You May Know", in-app calls, full-text bio search, profile view history, and swipe analytics trends. [Minimax]
- **Missing UI Elements:** Charts/Graphs for Stats, Rich Text Editor for bios, and Map View for locations. Missing dedicated View Other Profile screen, Settings, Activity History, and Premium/Boost UI. Missing "Why we matched" explanations. [Minimax]
- **Missing Pace Preferences UI:** Pace preferences are required for completeness, but only the CLI allows editing them; JavaFX uses defaults and has no UI for it. [GPT5.2]

### Missing Technical Events & Policies
- **Missing Events:** `UserBlocked`, `UserReported`, `SwipeUndone`, `PreferencesUpdated`. [Minimax]
- **Missing Business Policies:** `MatchingThresholdPolicy`, `UserStatePolicy`. [Minimax]

### I18n & Hardcoded Strings
- **GUI Hardcoding:** FXML files and controllers ignore `UiText`/`I18n` with over 120 strings hardcoded. `TextUtil.formatTimeAgo()` hardcodes English formats. `Main.java` has stale "PHASE 0.5". [Claude, Qwen]

### Code Smells & Refactoring Targets
- **God Objects:** `ProfileController` (1,300+ lines), `RestApiServer` (700+ lines), and `NavigationService` (400+ lines) bundle too many disparate concerns. [Code-First, Qwen, Minimax]
- **Coupling:** `MatchingViewModel` and `ViewModelFactory` construct low-level raw services instead of consuming the app-layer bundle registry correctly. [Code-First]
- **String Manipulation:** `Match.generateId()` creates deterministic IDs via simple string concatenation (`userA + "_" + userB`) instead of correctly formatting UUIDs. [Minimax]
- **Missing Accessibility:** No `fx:id` tags or keyboard traversal hooks on the JavaFX layouts. [Qwen]

### Dead Code
- **Missing Source:** `PerformanceMonitor` is documented in `CLAUDE.md` architecture but is fundamentally missing from source `core/`. [Claude]
- **Unused Transitions:** 6 redundant transition animations exist inside `UiAnimations` with no callers. [Claude]
