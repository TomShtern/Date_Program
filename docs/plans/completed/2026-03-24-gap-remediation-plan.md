# Gap Remediation Plan
**Date:** 2026-03-24
**Scope:** All gaps identified by 6-agent codebase audit
**Status:** ✅ Fully implemented and verified (updated 2026-03-25)

## ✅ Completion Tracking (Updated 2026-03-25)

- ✅ **P0-1** REST Pass endpoint now enforces daily limits (`enforceDailyLimit=true`)
- ✅ **P0-2** Matching card now includes **View Full Profile** action wired to `PROFILE_VIEW`

- ✅ **P1-1** Added `GET /api/users/{id}/browse` endpoint via `MatchingUseCases.browseCandidates`
- ✅ **P1-2** Added `DELETE /api/users/{id}` account deletion endpoint (204 on success)
- ✅ **P1-3** Added `GET /api/users/{id}/friend-requests` endpoint
- ✅ **P1-4** Added ACTIVE-state gate for REST `/candidates`
- ✅ **P1-5** Latitude/longitude bounds validation confirmed in place
- ✅ **P1-6** Added `AccountDeleted` event subscriptions in notification + metrics handlers
- ✅ **P1-7** Added `ProfileNoteDeleted` event subscription in metrics handler
- ✅ **P1-8** Enforced conversation visibility flags in conversation queries
- ✅ **P1-9** Added 429 rate-limit headers (`Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Used`)

- ✅ **P2-1** Added migration V8 query-optimization indexes
- ✅ **P2-2** Added `UserStorage` pagination methods + JDBI overrides
- ✅ **P2-3** Added 5 domain events and publication wiring (`ProfileCompleted`, `ConversationArchived`, `LocationUpdated`, `DailyLimitReset`, `MatchExpired` definitions)
- ✅ **P2-4** Expanded cleanup flow to purge soft-deleted users/interactions and extended cleanup result
- ✅ **P2-5** Wired chat polling intervals from `AppConfig` into `ChatViewModel`
- ✅ **P2-6** Added suspicious-swipe hard-block toggle + blocked diagnostics
- ✅ **P2-7** Moved distance thresholds in `MatchQualityService` to config-driven values
- ✅ **P2-8** Added standout interaction API to analytics storage/JDBI implementation
- ✅ **P2-9** Fixed `RecommendationService.Builder` trust-safety wiring behavior

- ✅ **P3-1** Added REST rate-limit tests (`RestApiRateLimitTest`)
- ✅ **P3-2** Added end-to-end matching flow integration test (`MatchingFlowIntegrationTest`)
- ✅ **P3-3** Added REST daily-limit enforcement tests (`RestApiDailyLimitTest`)
- ✅ **P3-4** Added midnight/timezone daily-limit boundary tests (`DailyLimitBoundaryTest`)
- ✅ **P3-5** Added match transaction tests (`MatchingTransactionTest`)
- ✅ **P3-6** Added CLI matching handler tests (`MatchingHandlerTest`)

---

## How to Use This Plan

Work through tiers in order (P0 → P1 → P2 → P3). Each item includes:
- **Evidence** — exact file:line proving the gap exists
- **Steps** — what to change and where
- **Pattern** — existing code to model after
- **Validate** — how to confirm the fix is correct

**After each tier, run the full quality gate:**
```bash
mvn spotless:apply && mvn verify
```
Do not proceed to the next tier until all tests pass.

---

## ⚠️ VALIDATION PREAMBLE — Read Before Touching Anything

The initial audit contained false positives. **Before implementing any item, verify the claim is still true by reading the relevant file.** The following were reported as gaps but are already implemented:

| Claimed Gap                                    | Reality                                                                                 | Where to Verify                                                  |
|------------------------------------------------|-----------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `ProfileController.handleSave()` missing       | **Already implemented** at lines 851–878                                                | `src/main/java/datingapp/ui/screen/ProfileController.java`       |
| `ChatController` profile-note handlers missing | **Already implemented** at lines 812–820                                                | `src/main/java/datingapp/ui/screen/ChatController.java`          |
| `profile_notes.status` column unused           | **Column does not exist in schema** — `deleted_at` is the status mechanism              | `src/main/java/datingapp/storage/schema/SchemaInitializer.java`  |
| `paceCompatibilityThreshold` dead code         | **Already used** in `MatchQualityService.isLowPaceCompatibility()`                      | `src/main/java/datingapp/core/matching/MatchQualityService.java` |
| No `CleanupScheduler` wired                    | **Scheduler already exists** — `ApplicationStartup.startCleanupScheduler()` (line ~113) | `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`  |
| Like endpoint ignores daily limits             | Like endpoint already uses `enforceDailyLimit=true` — only **Pass** uses `false`        | `src/main/java/datingapp/app/api/RestApiServer.java:~387`        |
| No read-only profile viewer                    | `ProfileViewController` + `profile-view.fxml` already exist                             | `src/main/java/datingapp/ui/screen/ProfileViewController.java`   |

**Standard verification command to run before each fix:**
```bash
# Verify a specific claim (example)
grep -n "handleSave\|handleCancel" src/main/java/datingapp/ui/screen/ProfileController.java
```

---

## P0 — Critical Bugs (Fix These First)

These are behavioral defects where existing features silently don't work.

---

### P0-1: REST Pass Endpoint Ignores Daily Limits

**Evidence:**
`src/main/java/datingapp/app/api/RestApiServer.java` — the `passUser()` method calls:
```java
// WRONG (current):
var result = matchingUseCases.recordLike(new RecordLikeCommand(
        UserContext.api(userId), targetId,
        Like.Direction.PASS,
        false));  // ← enforceDailyLimit=false bypasses all limit checks
```
The `likeUser()` method directly above it correctly uses `true`. Only `passUser()` is wrong.

**Steps:**
1. Open `src/main/java/datingapp/app/api/RestApiServer.java`
2. Find the `passUser()` method — look for `Like.Direction.PASS`
3. Change the 4th argument of `RecordLikeCommand` from `false` to `true`
4. Do not change the `likeUser()` method — it is already correct

**Pattern:** Look at the `likeUser()` method in the same file — the correct form is `true` as the 4th constructor argument.

**Validate:**
```bash
mvn test -Dtest=RestApiPhaseTwoRoutesTest
# Also grep to confirm:
grep -A5 "Direction.PASS" src/main/java/datingapp/app/api/RestApiServer.java
# Must show: true (not false) as the 4th argument
```

---

### P0-2: "View Full Profile" Button Missing on Candidate Card

**Evidence:**
`src/main/resources/fxml/matching.fxml` — the candidate card's `VBox` (lines ~69–87) has no "View Full Profile" button. The `MatchingController` has no `handleViewFullProfile()` method (confirmed by grep).

**Important:** Navigate to `PROFILE_VIEW` (the existing read-only `ProfileViewController`), NOT to `PROFILE` (which is the edit screen for the current user's own profile).

**Steps:**

1. **Add button to `matching.fxml`** — insert inside the card info `VBox`, after the note HBox section and before the closing `</VBox>`:
   ```xml
   <Separator/>
   <Button fx:id="viewFullProfileButton"
           onAction="#handleViewFullProfile"
           styleClass="button-secondary"
           maxWidth="Infinity"
           text="View Full Profile"/>
   ```

2. **Add `@FXML` field to `MatchingController.java`** — after the last `@FXML` field declaration:
   ```java
   @FXML
   private Button viewFullProfileButton;
   ```

3. **Add `@FXML` handler method to `MatchingController.java`** — after `handleImproveProfile()`:
   ```java
   @SuppressWarnings("unused")
   @FXML
   private void handleViewFullProfile() {
       User candidate = viewModel.currentCandidateProperty().get();
       if (candidate == null) {
           return;
       }
       NavigationService nav = NavigationService.getInstance();
       nav.setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, candidate.getId());
       nav.navigateTo(NavigationService.ViewType.PROFILE_VIEW);
   }
   ```

4. **Wire disable binding in `wireActionHandlers()`** — after the `improveProfileCard` block:
   ```java
   if (viewFullProfileButton != null) {
       viewFullProfileButton.disableProperty()
               .bind(viewModel.currentCandidateProperty().isNull());
   }
   ```

**Pattern:**
- Handler pattern: `handleImproveProfile()` (existing, same file)
- Context + navigation pattern: the match popup's "Send Message" path that calls `nav.setNavigationContext(ViewType.CHAT, matchedUser.getId())`
- Null-safe wiring: `if (viewFullProfileButton != null)` guard, same as all other optional controls

**Validate:**
```bash
mvn compile
# Manual: run app, navigate to Matching, verify button appears on card
# Verify correct ViewType (PROFILE_VIEW not PROFILE):
grep "handleViewFullProfile" src/main/java/datingapp/ui/screen/MatchingController.java
grep "PROFILE_VIEW" src/main/java/datingapp/ui/screen/MatchingController.java
```

---

## P1 — High Priority Missing Features

---

### P1-1: Missing REST Endpoint — Browse Candidates with Daily Picks

**Evidence:**
`MatchingUseCases.browseCandidates(BrowseCandidatesCommand)` exists and returns `BrowseCandidatesResult` (with candidates list, daily pick, and flags). The existing `GET /api/users/{id}/candidates` intentionally bypasses this use-case and calls `candidateFinder` directly with no state gate, no daily pick, and no daily limit enforcement. There is no `/browse` endpoint.

**Steps:**

1. **Add handler method to `RestApiServer`** (after `getCandidates()`):
   ```java
   private void browseCandidates(Context ctx) {
       UUID userId = parseUuid(ctx.pathParam("id"));
       User user = loadUser(ctx, userId);
       if (user == null) return;
       var result = matchingUseCases.browseCandidates(
               new MatchingUseCases.BrowseCandidatesCommand(UserContext.api(userId), user));
       if (!result.success()) { handleUseCaseFailure(ctx, result.error()); return; }
       ctx.json(result.data());
   }
   ```

2. **Register the route** inside the appropriate route-registration method (near the existing candidates route):
   ```java
   app.get("/api/users/{id}/browse", server::browseCandidates);
   ```

3. **No new DTO needed** — `BrowseCandidatesResult` is a public record and can serialize directly. If `User` objects cause circular issues, map to `UserSummary` following the pattern used in `getCandidates()`.

**Pattern:** `getStandouts()` endpoint pattern — calls use-case, maps result, returns JSON.

**Validate:**
```bash
# Compile, then test:
mvn test -Dtest=RestApiReadRoutesTest
# Manual: GET /api/users/{activeUserId}/browse?userId={activeUserId}
# Expected: 200 with candidates array and optional dailyPick field
# Also: GET with PAUSED user must return 409 (handled by use-case layer)
```

---

### P1-2: Missing REST Endpoint — Account Deletion

**Evidence:**
`ProfileUseCases.deleteAccount(DeleteAccountCommand)` is fully implemented and fires `AccountDeleted` event. No `DELETE /api/users/{id}` route exists in `RestApiServer`.

**Steps:**

1. **Add handler method:**
   ```java
   private void deleteAccount(Context ctx) {
       UUID userId = parseUuid(ctx.pathParam("id"));
       if (loadUser(ctx, userId) == null) return;
       String reason = ctx.queryParamAsClass("reason", String.class).getOrDefault(null);
       var result = profileUseCases.deleteAccount(
               new ProfileUseCases.DeleteAccountCommand(UserContext.api(userId), reason));
       if (!result.success()) { handleUseCaseFailure(ctx, result.error()); return; }
       ctx.status(204);
   }
   ```

2. **Register the route** (with the other `/api/users/{id}` routes):
   ```java
   app.delete("/api/users/{id}", server::deleteAccount);
   ```

**Pattern:** `updateProfile()` for error handling structure; use HTTP 204 No Content on success (standard for DELETE).

**Validate:**
```bash
mvn test -Dtest=RestApiReadRoutesTest
# Manual: DELETE /api/users/{userId}?userId={userId}
# Expected: 204 No Content
# Verify user state is now PAUSED+deleted in DB
```

---

### P1-3: Missing REST Endpoint — Pending Friend Requests

**Evidence:**
`SocialUseCases.pendingFriendRequests(FriendRequestsQuery)` exists. The REST social routes include accept/decline endpoints but no listing endpoint for incoming pending requests.

**Steps:**

1. **Add handler method:**
   ```java
   private void getPendingFriendRequests(Context ctx) {
       UUID userId = parseUuid(ctx.pathParam("id"));
       if (loadUser(ctx, userId) == null) return;
       var result = socialUseCases.pendingFriendRequests(
               new SocialUseCases.FriendRequestsQuery(UserContext.api(userId)));
       if (!result.success()) { handleUseCaseFailure(ctx, result.error()); return; }
       ctx.json(result.data());
   }
   ```

2. **Register the route** (near other social routes):
   ```java
   app.get("/api/users/{id}/friend-requests", server::getPendingFriendRequests);
   ```

**Pattern:** `getNotifications()` endpoint — same structure, same use-case delegation pattern.

**Validate:**
```bash
mvn test -Dtest=RestApiRelationshipRoutesTest
# Manual: GET /api/users/{userId}/friend-requests?userId={userId}
# Expected: 200 with array (empty if none pending)
```

---

### P1-4: REST `/candidates` Has No Profile Completeness Gate

**Evidence:**
`RestApiServer.getCandidates()` calls `readCandidateSummaries(user)` with no state check. The use-case layer's `browseCandidates()` correctly gates on `user.getState() != UserState.ACTIVE`, but this raw endpoint bypasses it. A PAUSED or INCOMPLETE user can request candidates via REST.

**Steps:**

1. In `getCandidates()`, immediately after the `loadUser()` null check, add:
   ```java
   if (user.getState() != User.UserState.ACTIVE) {
       ctx.status(409);
       ctx.json(new ErrorResponse(CONFLICT, "User must be ACTIVE to browse candidates"));
       return;
   }
   ```

**Pattern:** Identical check in `MatchingUseCases.browseCandidates()` — copy the guard verbatim.

**Validate:**
```bash
# Create a PAUSED user, call GET /api/users/{id}/candidates
# Expected: 409 CONFLICT
# Create an ACTIVE user, call same — Expected: 200 OK
mvn test -Dtest=RestApiReadRoutesTest
```

---

### P1-5: No Lat/Lon Bounds Validation on Profile Update

**Evidence:**
`RestApiServer.updateProfile()` accepts `request.latitude()` and `request.longitude()` as raw `Double` values and passes them directly to the use-case. No bounds check exists at the REST boundary. Invalid values (e.g., lat=200) will corrupt distance calculations silently.

**Steps:**

1. In `updateProfile()`, after parsing the request body, before calling the use-case:
   ```java
   Double lat = request.latitude();
   Double lon = request.longitude();
   if (lat != null && (lat < -90 || lat > 90)) {
       ctx.status(400);
       ctx.json(new ErrorResponse(BAD_REQUEST, "Latitude must be between -90 and 90"));
       return;
   }
   if (lon != null && (lon < -180 || lon > 180)) {
       ctx.status(400);
       ctx.json(new ErrorResponse(BAD_REQUEST, "Longitude must be between -180 and 180"));
       return;
   }
   ```

**Pattern:** The numeric range check in `updateDiscoveryPreferences()` — same approach.

**Validate:**
```bash
# PUT /api/users/{id}/profile with {"latitude": 91} — expect 400
# PUT /api/users/{id}/profile with {"longitude": 200} — expect 400
# PUT /api/users/{id}/profile with {"latitude": 32.08, "longitude": 34.78} — expect 200
mvn test -Dtest=RestApiPhaseTwoRoutesTest
```

---

### P1-6: AccountDeleted Event Has No Handlers

**Evidence:**
`ProfileUseCases.deleteAccount()` publishes `AppEvent.AccountDeleted` (line ~409). Neither `NotificationEventHandler`, `MetricsEventHandler`, nor `AchievementEventHandler` subscribes to this event. Cached state (notifications, metrics sessions) is never cleaned up when a user deletes their account.

**Steps:**

1. In `NotificationEventHandler`, add handler method:
   ```java
   void onAccountDeleted(AppEvent.AccountDeleted event) {
       if (logger.isInfoEnabled()) {
           logger.info("Account deleted for userId={}, cleaning up notifications", event.userId());
       }
       // Optionally: communicationStorage.deleteNotificationsFor(event.userId());
       // Wire if method exists; otherwise log only is sufficient for now.
   }
   ```

2. In `NotificationEventHandler.register()`, after the last subscription:
   ```java
   eventBus.subscribe(AppEvent.AccountDeleted.class,
           this::onAccountDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
   ```

3. In `MetricsEventHandler`, add handler method:
   ```java
   void onAccountDeleted(AppEvent.AccountDeleted event) {
       activityMetricsService.endAllSessionsForUser(event.userId());
   }
   ```
   Check if `endAllSessionsForUser()` exists; if not, a best-effort log is acceptable for now.

4. In `MetricsEventHandler.register()`, add subscription:
   ```java
   eventBus.subscribe(AppEvent.AccountDeleted.class,
           this::onAccountDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
   ```

**Pattern:** `onFriendRequestAccepted()` in `NotificationEventHandler` — subscription + handler structure.

**Validate:**
```bash
mvn compile
# Grep for both subscriptions:
grep -n "AccountDeleted" src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java
grep -n "AccountDeleted" src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java
mvn test -Dtest=ProfileUseCasesTest
```

---

### P1-7: ProfileNoteDeleted Event Has No Handlers

**Evidence:**
`ProfileUseCases.deleteProfileNote()` publishes `AppEvent.ProfileNoteDeleted`. `MetricsEventHandler` subscribes to `ProfileNoteSaved` (line ~26) but not `ProfileNoteDeleted`. No handler tracks note deletions.

**Steps:**

1. In `MetricsEventHandler`, add handler method (after `onProfileNoteSaved()`):
   ```java
   void onProfileNoteDeleted(AppEvent.ProfileNoteDeleted event) {
       activityMetricsService.recordActivity(event.authorId());
   }
   ```

2. In `MetricsEventHandler.register()`, after the `ProfileNoteSaved` subscription:
   ```java
   eventBus.subscribe(AppEvent.ProfileNoteDeleted.class,
           this::onProfileNoteDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
   ```

**Pattern:** The adjacent `ProfileNoteSaved` subscription directly above.

**Validate:**
```bash
grep -n "ProfileNoteDeleted" src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java
mvn test -Dtest=ProfileUseCasesTest
```

---

### P1-8: Conversation Visibility Flags Not Enforced in Queries

**Evidence:**
`SchemaInitializer.java` creates columns `visible_to_user_a` and `visible_to_user_b` (both default TRUE). `JdbiConnectionStorage`'s `getConversationsFor()` SQL query fetches these columns but does not filter on them — soft-hidden conversations still appear to users.

**Steps:**

1. In `JdbiConnectionStorage`, find the `getConversationsFor()` `@SqlQuery` annotation.
   The current `WHERE` clause is:
   ```sql
   WHERE (user_a = :userId OR user_b = :userId) AND deleted_at IS NULL
   ```
   Change it to:
   ```sql
   WHERE (user_a = :userId OR user_b = :userId)
     AND deleted_at IS NULL
     AND (
       (user_a = :userId AND visible_to_user_a = TRUE) OR
       (user_b = :userId AND visible_to_user_b = TRUE)
     )
   ```

2. Apply the same change to `getAllConversationsFor()` if it exists.

**Pattern:** The `setConversationVisibility()` method in the same file already writes these columns — the read should apply them symmetrically.

**Validate:**
```bash
mvn test
# Manual: create conversation, call setConversationVisibility(id, userId, false)
# Call getConversationsFor(userId) — hidden conversation must NOT appear
```

---

### P1-9: Missing Rate-Limit Headers on 429 Responses

**Evidence:**
`RestApiServer` exception handler for `ApiTooManyRequestsException` (line ~1052–1055) returns `ctx.status(429)` with a JSON error body but sets no `Retry-After` or `X-RateLimit-*` headers. Clients cannot know when to retry.

**Steps:**

1. **Add `RateLimitStatus` record inside `LocalRateLimiter`** (nested private class):
   ```java
   private record RateLimitStatus(boolean allowed, int used, int limit, long millisUntilReset) {}
   ```

2. **Add `tryAcquireWithStatus(String key)` method to `LocalRateLimiter`** that returns `RateLimitStatus` instead of `boolean`. Keep existing `tryAcquire()` delegating to this.

3. **Update `enforceRateLimit()`** to call `tryAcquireWithStatus()` and store the result:
   ```java
   var status = rateLimiter.tryAcquireWithStatus(key);
   ctx.attribute("rateLimitStatus", status);
   if (!status.allowed()) throw new ApiTooManyRequestsException("...");
   ```

4. **Update the 429 exception handler** to add headers:
   ```java
   var status = (LocalRateLimiter.RateLimitStatus) ctx.attribute("rateLimitStatus");
   if (status != null) {
       long secondsUntilReset = (status.millisUntilReset() + 999) / 1000;
       ctx.header("Retry-After", String.valueOf(secondsUntilReset));
       ctx.header("X-RateLimit-Limit", String.valueOf(status.limit()));
       ctx.header("X-RateLimit-Used", String.valueOf(status.used()));
   }
   ctx.status(429);
   ctx.json(new ErrorResponse(TOO_MANY_REQUESTS, e.getMessage()));
   ```

**Pattern:** The `LocalRateLimiter` itself (already has a `Window` inner record) — follow the same nested-record approach.

**Validate:**
```bash
# Fire 241 requests to a rate-limited endpoint
# Verify response headers: Retry-After, X-RateLimit-Limit, X-RateLimit-Used are present
mvn test -Dtest=RestApiRateLimitTest  # (new test from P3-1)
```

---

## P2 — Medium Priority

---

### P2-1: Add Missing Database Indexes (Migration V8)

**Evidence:**
`SchemaInitializer.java` and `MigrationRunner.java` define migrations V1–V7. The following query paths lack indexes: `likes` table for direction+timestamp; `conversations` for per-user ordering; `messages` for sender queries; `swipe_sessions` for temporal range; `user_stats` for ranking; `standouts` for interaction tracking.

**Steps:**

1. **Add migration V8 to `MigrationRunner`** — add a new `VersionedMigration` entry to the `MIGRATIONS` list:
   ```java
   new VersionedMigration(8, "Add query-optimization indexes for likes, conversations, messages, sessions, stats, standouts", MigrationRunner::applyV8)
   ```

2. **Implement `applyV8(Statement stmt)`** (follow the exact pattern of existing `applyVN` methods):
   ```java
   private static void applyV8(Statement stmt) throws SQLException {
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_direction_created ON likes(direction, created_at DESC) WHERE deleted_at IS NULL");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_likes_received_created ON likes(who_got_liked, created_at DESC) WHERE deleted_at IS NULL");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last_msg ON conversations(user_a, last_message_at DESC) WHERE deleted_at IS NULL");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last_msg ON conversations(user_b, last_message_at DESC) WHERE deleted_at IS NULL");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages(sender_id, created_at DESC) WHERE deleted_at IS NULL");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_started_at_desc ON swipe_sessions(started_at DESC) WHERE state = 'ACTIVE'");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_stats_computed_desc ON user_stats(computed_at DESC)");
       stmt.execute("CREATE INDEX IF NOT EXISTS idx_standouts_interacted_at ON standouts(seeker_id, interacted_at DESC) WHERE interacted_at IS NOT NULL");
   }
   ```

   > **Note:** H2 has limited support for partial indexes (`WHERE` clause). If any index fails, remove the `WHERE` clause for that specific statement. Test with `mvn verify` after adding each index.

3. No schema DDL changes needed in `SchemaInitializer` — indexes are added via migration only.

**Pattern:** `applyV7()` in `MigrationRunner` — exact same structure.

**Validate:**
```bash
mvn verify
# Verify migration runs:
grep -n "applyV8\|version.*8" src/main/java/datingapp/storage/schema/MigrationRunner.java
# Clean DB test:
mvn test -Dtest=MigrationRunnerTest
```

---

### P2-2: Add Pagination to UserStorage findActive() / findAll()

**Evidence:**
`UserStorage.findActive()` and `findAll()` return unbounded `List<User>`. `InteractionStorage` already has `getPageOfMatchesFor()` and `getPageOfActiveMatchesFor()` using `PageData<T>` — `UserStorage` lacks equivalent variants.

**Steps:**

1. **Add two `default` methods to `UserStorage`** (after `findAll()`):
   ```java
   default PageData<User> getPageOfActiveUsers(int offset, int limit) {
       if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
       if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
       List<User> all = findActive();
       int total = all.size();
       if (offset >= total) return PageData.empty(limit, total);
       return new PageData<>(all.subList(offset, Math.min(offset + limit, total)), total, offset, limit);
   }

   default PageData<User> getPageOfAllUsers(int offset, int limit) {
       if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
       if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
       List<User> all = findAll();
       int total = all.size();
       if (offset >= total) return PageData.empty(limit, total);
       return new PageData<>(all.subList(offset, Math.min(offset + limit, total)), total, offset, limit);
   }
   ```

2. **Override with SQL in `JdbiUserStorage`** for real performance:
   ```java
   @Override
   public PageData<User> getPageOfActiveUsers(int offset, int limit) {
       return jdbi.withHandle(handle -> {
           int total = handle.createQuery("SELECT COUNT(*) FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL")
                   .mapTo(Integer.class).one();
           if (offset >= total) return PageData.empty(limit, total);
           List<User> page = applyNormalizedProfileDataBatch(handle,
                   handle.createQuery("SELECT * FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
                           .bind("limit", limit).bind("offset", offset)
                           .map(new Mapper()).list());
           return new PageData<>(page, total, offset, limit);
       });
   }
   ```
   Add the equivalent `getPageOfAllUsers()` override.

**Pattern:** `getPageOfMatchesFor()` in `JdbiMatchmakingStorage` — identical structure.

**Validate:**
```bash
mvn test
# Verify default impl works with TestStorages.Users (which wraps findActive())
# Verify JDBI impl works with real H2 DB in storage integration tests
```

---

### P2-3: Add 5 High-Value Domain Events to AppEvent

**Evidence:**
`AppEvent.java` defines a sealed interface with 11 permitted subtypes. These 5 cross-cutting transitions have no corresponding events: profile activation completion, conversation archival, location change, daily limit reset, match expiry.

**Steps:**

1. **Add 5 new `record` implementations to `AppEvent.java`** — add to both the `permits` clause and as nested records:
   ```java
   // Add to permits list:
   AppEvent.ProfileCompleted,
   AppEvent.ConversationArchived,
   AppEvent.LocationUpdated,
   AppEvent.DailyLimitReset,
   AppEvent.MatchExpired

   // Add record implementations:
   record ProfileCompleted(UUID userId, Instant occurredAt) implements AppEvent {}
   record ConversationArchived(String conversationId, UUID archivedByUserId, Instant occurredAt) implements AppEvent {}
   record LocationUpdated(UUID userId, double latitude, double longitude, Instant occurredAt) implements AppEvent {}
   record DailyLimitReset(UUID userId, Instant occurredAt) implements AppEvent {}
   record MatchExpired(String matchId, UUID userA, UUID userB, Instant occurredAt) implements AppEvent {}
   ```

2. **Fire `ProfileCompleted` in `ProfileUseCases.saveProfile()`** — after the `ProfileSaved` event publication, when `activated` is true:
   ```java
   if (activated) {
       publishEvent(new AppEvent.ProfileCompleted(user.getId(), AppClock.now()),
               "Post-profile-completed event failed for user " + user.getId());
   }
   ```

3. **Fire `LocationUpdated` in `ProfileUseCases.updateProfile()`** — after `saveProfile()` succeeds, when coordinates changed:
   ```java
   if (saveResult.isSuccess() && command.latitude() != null && command.longitude() != null) {
       Double oldLat = /* capture before applyProfileLocationFields */;
       if (!Objects.equals(oldLat, command.latitude())) {
           publishEvent(new AppEvent.LocationUpdated(user.getId(), command.latitude(), command.longitude(), AppClock.now()),
                   "Post-location-updated event failed");
       }
   }
   ```

4. **Fire `ConversationArchived` in `MessagingUseCases`** — find the archive/delete conversation method and add publication after success.

5. `DailyLimitReset` and `MatchExpired` events are defined for future use by scheduled jobs. Wire them only after the cleanup scheduler is extended (P2-4). Adding the event types now unblocks future subscribers.

**Compile first, then wire publishing:**
```bash
# Step 1: Add records to AppEvent.java
mvn compile  # Must pass with all 5 new records in permits clause
# Step 2: Add publishing calls
mvn verify
```

**Pattern:** `AppEvent.MatchCreated` record definition + `publishEvent()` call in use-cases.

---

### P2-4: Expand CleanupScheduler to Purge Soft-Deleted Records

**Evidence:**
`ApplicationStartup.startCleanupScheduler()` already exists and is wired. `ActivityMetricsService.runCleanup()` runs on schedule but only cleans analytics tables. `UserStorage.purgeDeletedBefore(Instant)` and `InteractionStorage.purgeDeletedBefore(Instant)` exist but are never called from the scheduler.

**Steps:**

1. **Inject `UserStorage` and `InteractionStorage` into `ActivityMetricsService`** constructor — add two new parameters. Update `ServiceRegistry` / `StorageFactory` to pass them when constructing `ActivityMetricsService`.

2. **Extend `runCleanup()`** in `ActivityMetricsService`:
   ```java
   public CleanupResult runCleanup() {
       Instant cutoff = AppClock.now().minus(config.safety().cleanupRetentionDays(), ChronoUnit.DAYS);
       int dailyPicksDeleted = analyticsStorage.deleteExpiredDailyPickViews(cutoff);
       int sessionsDeleted = analyticsStorage.deleteExpiredSessions(cutoff);
       int standoutsDeleted = analyticsStorage.deleteExpiredStandouts(cutoff);
       int usersDeleted = userStorage.purgeDeletedBefore(cutoff);        // NEW
       int interactionsDeleted = interactionStorage.purgeDeletedBefore(cutoff);  // NEW
       return new CleanupResult(dailyPicksDeleted, sessionsDeleted, standoutsDeleted, usersDeleted, interactionsDeleted);
   }
   ```

3. **Update `CleanupResult` record** to include `usersDeleted` and `interactionsDeleted` fields.

4. No changes needed to `ApplicationStartup` — the scheduler already calls `runCleanup()`.

**Pattern:** Constructor injection style follows `MatchingService` (which takes both `UserStorage` and `InteractionStorage`).

**Validate:**
```bash
mvn verify
# Integration: call runCleanup() with cleanupRetentionDays=0 on DB with soft-deleted records
# Verify usersDeleted > 0 and interactionsDeleted > 0
```

---

### P2-5: Wire chatPollSeconds into ChatViewModel

**Evidence:**
`AppConfig` defines `chatBackgroundPollSeconds` (default 15) and `chatActivePollSeconds` (default 5). `ChatViewModel` ignores these and uses hardcoded static constants:
```java
private static final Duration DEFAULT_CONVERSATION_POLL_INTERVAL = Duration.ofSeconds(15);
private static final Duration DEFAULT_ACTIVE_CONVERSATION_POLL_INTERVAL = Duration.ofSeconds(5);
```

**Steps:**

1. **Add `AppConfig`-accepting constructor to `ChatViewModel`** alongside the existing one:
   ```java
   public ChatViewModel(MessagingUseCases messagingUseCases, SocialUseCases socialUseCases,
                        AppSession session, AppConfig config) {
       this(messagingUseCases, socialUseCases, session,
               new JavaFxUiThreadDispatcher(),
               Duration.ofSeconds(config.validation().chatBackgroundPollSeconds()),
               Duration.ofSeconds(config.validation().chatActivePollSeconds()),
               ChatUiDependencies.noOp());
   }
   ```

2. **Update `ViewModelFactory`** to pass `services.getConfig()` when constructing `ChatViewModel`.

3. Remove the two `DEFAULT_*` static constants once the new constructor is the only code path in production.

**Pattern:** `DefaultDailyLimitService(InteractionStorage, AppConfig)` constructor — config injection alongside existing overloads.

**Validate:**
```bash
mvn test
# Verify ViewModelFactory passes AppConfig to ChatViewModel:
grep -n "ChatViewModel" src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java
```

---

### P2-6: Enforce suspiciousSwipeVelocity as a Hard Block

**Evidence:**
`ActivityMetricsService.recordSwipe()` (lines ~77–99) detects velocity exceeding `config.matching().suspiciousSwipeVelocity()` but returns `SwipeGateResult.success()` with only a warning string. The swipe is allowed. No actual throttling occurs.

**Steps:**

1. **Add a new boolean config field** `suspiciousSwipeVelocityBlockingEnabled` to `AppConfig.MatchingConfig` with default `true`.

2. **Modify the velocity check in `recordSwipe()`:**
   ```java
   if (session.getSwipeCount() >= 10
           && session.getSwipesPerMinute() > config.matching().suspiciousSwipeVelocity()) {
       if (config.matching().suspiciousSwipeVelocityBlockingEnabled()) {
           velocityBlockedCount.increment();
           return SwipeGateResult.blocked(session,
                   "Rapid swiping detected. Please slow down and review profiles carefully.");
       }
       warning = "Unusually fast swiping detected. Take a moment to review profiles!";
       velocityWarningCount.increment();
   }
   ```

3. **Add `velocityBlockedCount` field** (LongAdder) alongside existing `velocityWarningCount`.

4. **Expose the count in diagnostics** via the existing snapshot/diagnostic methods.

**Pattern:** The `maxSwipesPerSession` enforcement directly above in the same method — `SwipeGateResult.blocked(session, message)`.

**Validate:**
```bash
mvn test -Dtest=ActivityMetricsServiceTest
# Add test: simulate 11 swipes at >30/min, assert SwipeGateResult.isBlocked() == true
```

---

### P2-7: Move MatchQualityService Hardcoded Distance Thresholds to AppConfig

**Evidence:**
`MatchQualityService` defines:
```java
private static final int NEARBY_DISTANCE_KM = 5;
private static final int MID_DISTANCE_KM = 15;
```
`AppConfig.AlgorithmConfig` already has `nearbyDistanceKm` (default 5) and `closeDistanceKm` (default 10). The hardcoded `MID_DISTANCE_KM=15` is also inconsistent with `closeDistanceKm=10`.

**Steps:**

1. **Remove the two `static final` constants** from `MatchQualityService`.

2. **Add instance fields:**
   ```java
   private final int nearbyDistanceKm;
   private final int midDistanceKm;
   ```

3. **Initialize in constructor:**
   ```java
   this.nearbyDistanceKm = config.algorithm().nearbyDistanceKm();
   this.midDistanceKm = config.algorithm().closeDistanceKm();
   ```

4. **Replace usages** of `NEARBY_DISTANCE_KM` with `this.nearbyDistanceKm` and `MID_DISTANCE_KM` with `this.midDistanceKm`.

**Pattern:** `StarThresholdPolicy.from(config.algorithm())` in the same constructor — config-driven initialization.

**Validate:**
```bash
mvn test -Dtest=MatchQualityServiceTest
# Verify no compilation errors and constants are removed:
grep "NEARBY_DISTANCE_KM\|MID_DISTANCE_KM" src/main/java/datingapp/core/matching/MatchQualityService.java
# Must return no results
```

---

### P2-8: Add markStandoutInteracted() to AnalyticsStorage

**Evidence:**
`SchemaInitializer` creates `standouts.interacted_at TIMESTAMP` column (line ~420). No method in `AnalyticsStorage` or `JdbiMetricsStorage` ever writes to this column. It remains NULL after user engagement, making the interaction-tracking feature non-functional.

**Steps:**

1. **Add method signature to `AnalyticsStorage`:**
   ```java
   boolean markStandoutInteracted(UUID standoutId, Instant timestamp);
   ```

2. **Add `@SqlUpdate` to `JdbiMetricsStorage`'s standout DAO** (or equivalent inner interface):
   ```java
   @SqlUpdate("UPDATE standouts SET interacted_at = :timestamp WHERE id = :id AND interacted_at IS NULL")
   int markStandoutInteractedRaw(@Bind("id") UUID standoutId, @Bind("timestamp") Instant timestamp);
   ```

3. **Add override to `JdbiMetricsStorage`:**
   ```java
   @Override
   public boolean markStandoutInteracted(UUID standoutId, Instant timestamp) {
       return standoutDao.markStandoutInteractedRaw(standoutId, timestamp) > 0;
   }
   ```

4. **Call this method from `MatchingService` or `StandoutService`** when a user swipes on a standout or sends them a message. The exact call site depends on where standout ID is available — check `DefaultStandoutService.getStandouts()` return type for an ID field.

**Pattern:** `deleteExpiredStandouts()` — same DAO pattern in the same class.

**Validate:**
```bash
mvn test
grep -n "markStandoutInteracted" src/main/java/datingapp/core/storage/AnalyticsStorage.java
grep -n "markStandoutInteracted" src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java
```

---

### P2-9: Fix RecommendationService.Builder Silent trustSafetyStorage Bug

**Evidence:**
`RecommendationService.Builder` (lines ~56–59) accepts `trustSafetyStorage` as a setter parameter but silently does not assign it to any field. The value is discarded. This is a latent bug that will cause NullPointerExceptions or incorrect behavior if `RecommendationService` ever reads from `trustSafetyStorage`.

**Steps:**

1. Open `src/main/java/datingapp/core/matching/RecommendationService.java`
2. Find the `Builder` inner class, locate the `trustSafetyStorage` setter method
3. Verify the field assignment is missing (the setter accepts the parameter but does not do `this.trustSafetyStorage = trustSafetyStorage`)
4. Add the assignment
5. Verify `RecommendationService` has a corresponding `trustSafetyStorage` field — add if missing

**Pattern:** All other Builder setter methods in the same class — they all assign `this.field = value`.

**Validate:**
```bash
mvn compile
grep -A3 "trustSafetyStorage" src/main/java/datingapp/core/matching/RecommendationService.java
# Must show both field declaration and assignment in setter
```

---

## P3 — Tests

All new test classes live in `src/test/java/datingapp/`. Use the existing H2 test infrastructure (`StorageFactory.buildH2()`, `TestStorages.*`).

---

### P3-1: REST Rate-Limit 429 Test

**New file:** `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`

**Template:** `RestApiHealthRoutesTest` for server setup and teardown.

**Tests to write:**
- `fires241stRequestAndReceives429()` — fire 240 requests to `/api/users`, confirm all succeed, fire 241st, assert `status=429` and `code="TOO_MANY_REQUESTS"`
- `healthEndpointIsExemptFromRateLimit()` — fire 500 requests to `/api/health`, assert all return 200
- After implementing P1-9: `rateLimitResponseContainsRetryAfterHeader()` — assert `Retry-After` header present on 429

**Key setup detail:** Rate limiter key is `ip|method` — each HTTP method has its own bucket. Use the same method across all requests.

---

### P3-2: End-to-End Matching Flow Integration Test

**New file:** `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java`

**Template:** `MessagingHandlerTest` for H2 setup.

**Tests to write:**
- `fullJourneyRegisterToBrowseToMatchToMessage()` — uses real H2 + real `ServiceRegistry`. Creates two users, calls `browseCandidates()` use-case directly, then fires REST calls for like → match → send message → retrieve messages. Verifies message appears for recipient.

**Key setup detail:** Users need all required fields: `birthDate`, `gender`, `interestedIn`, `latitude`, `longitude`, `maxDistanceKm`, `ageRange`, at least one photo, `bio`, `pacePreferences`, and must be activated. Missing any of these will cause candidacy filtering to exclude them.

---

### P3-3: REST Daily Limit Enforcement Tests

**New file:** `src/test/java/datingapp/app/api/RestApiDailyLimitTest.java`

**Template:** `RestApiPhaseTwoRoutesTest`.

**Tests to write:**
- `userAtLikeLimitReceives409()` — config `dailyLikeLimit=3`, pre-populate 3 likes in `InteractionStorage`, fire 4th like via REST, assert 409
- `userAtPassLimitReceives409()` — same for passes
- `unlimitedConfigAllowsAnyNumberOfLikes()` — config `dailyLikeLimit=-1`, fire 50 likes, assert all succeed
- Note: These tests require P0-1 (Pass fix) to be done first, otherwise pass tests will fail because the limit is not enforced

---

### P3-4: Daily Limit Midnight Boundary Tests

**New file:** `src/test/java/datingapp/core/DailyLimitBoundaryTest.java`

**Template:** `DailyLimitServiceTest`.

**Tests to write:**
- `likeCountResetsAtMidnight()` — use `TestClock.setFixed(23:59:59)`, pre-populate a like, advance to `00:00:01` next day, assert `likesUsed=0`
- `resetDateIsInUserTimezone()` — create service with `ZoneId.of("America/Los_Angeles")`, verify reset date and time in local zone
- `differentTimezonesResetAtDifferentAbsoluteTimes()` — compare reset instants for UTC+11 vs UTC-11 configs

---

### P3-5: Match Creation Transaction Atomicity Tests

**New file:** `src/test/java/datingapp/app/MatchingTransactionTest.java`

**Template:** `MessagingHandlerTest` for H2 setup.

**Tests to write:**
- `mutualLikeCreatesExactlyOneMatch()` — A likes B, B likes A, assert exactly 1 match row, 2 like rows
- `passByTargetDoesNotCreateMatch()` — A likes B, B passes A, assert 0 matches
- `undoAfterMatchArchivesMatch()` — create match, undo A's like, verify match state is ARCHIVED and A's like is removed

---

### P3-6: MatchingHandler CLI Tests

**New file:** `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`

**Template:** `ProfileHandlerTest` and `MessagingHandlerTest` — same H2 setup + `InputReader(new Scanner(new StringReader(input)))` pattern.

**Tests to write (use `@Nested` classes):**
- `BrowseCandidates`: `showsErrorWhenNotLoggedIn()`, `returnsNoCandidatesWhenNoneMatch()`
- `Swiping`: `recordsLikeOnSwipeRight()`, `recordsPassOnSwipeLeft()`
- `Undo`: `undoesLastSwipeWhenUndoAvailable()`, `showsMessageWhenNothingToUndo()`
- `DailyStatus`: `showsDailyLimitStatusWithCorrectCounts()`

All handler tests should follow the `assertDoesNotThrow()` + storage-state verification pattern from existing handler tests.

---

## Execution Order and Dependencies

```
P0-1  (pass daily limit fix)
P0-2  (view full profile button)
  ↓
P1-1 through P1-9 (independent — can parallelize)
  ↓
mvn verify  ← gate
  ↓
P2-3  (add 5 new AppEvent types — must compile first before P2-x can publish them)
P2-1  (indexes — independent)
P2-2  (pagination — independent)
P2-4  (cleanup — depends on P2-3 for DailyLimitReset event)
P2-5  (chat poll config)
P2-6  (velocity blocking — add config field first)
P2-7  (distance thresholds)
P2-8  (markStandoutInteracted)
P2-9  (Builder bug)
  ↓
mvn verify  ← gate
  ↓
P3-1  (rate limit tests — requires P1-9 done)
P3-2  (integration test — requires P1-1 and P1-4 done)
P3-3  (daily limit tests — requires P0-1 done)
P3-4  (boundary tests — independent)
P3-5  (atomicity tests — independent)
P3-6  (MatchingHandler CLI tests — independent)
  ↓
mvn verify  ← final gate
```

---

## Appendix: Corrected False Positives

Do NOT attempt to implement these — they were reported as gaps but are already implemented:

| Item                                                               | Status                                                        | Evidence Location                      |
|--------------------------------------------------------------------|---------------------------------------------------------------|----------------------------------------|
| ProfileController.handleSave() / handleCancel()                    | Already implemented                                           | `ProfileController.java:851–878`       |
| ChatController handleSaveProfileNote() / handleDeleteProfileNote() | Already implemented                                           | `ChatController.java:812–820`          |
| profile_notes.status column                                        | Column does not exist — deleted_at IS the status              | `SchemaInitializer.java`               |
| paceCompatibilityThreshold dead code                               | Used correctly in isLowPaceCompatibility()                    | `MatchQualityService.java`             |
| CleanupScheduler not wired                                         | Already wired — startCleanupScheduler() in ApplicationStartup | `ApplicationStartup.java:~113`         |
| Like endpoint ignores daily limits                                 | Like already uses `true`; only pass uses `false`              | `RestApiServer.java` passUser()        |
| No read-only profile viewer                                        | ProfileViewController + profile-view.fxml already exist       | `ui/screen/ProfileViewController.java` |

---

*Plan generated: 2026-03-24. Evidence sourced from 6 parallel codebase audit agents.*
