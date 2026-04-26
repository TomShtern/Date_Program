# Backend-Safe Optimization And Hardening - Implementation Report

> **Date:** 2026-04-26
> **Plan:** 2026-04-26-backend-safe-implementation-plan.md

---

## Summary

_Status: Completed_

---

## Task 1: Optimize Conversation Preview Loading

_Status: Completed_

### Changes Made

**`ConnectionService.java`:**
- Replaced two separate stream operations (one for `otherUserIds`, one for `conversationIds`) with a single loop that builds both sets simultaneously. This eliminates one stream traversal and the `new HashSet<>(list)` copy.
- Pre-sized the `otherUserIds` and `conversationIds` sets with `conversations.size()`.
- Pre-sized the `previews` ArrayList with `conversations.size()`.
- Added `getConversationsWithMessageCounts(UUID, int, int)` method that combines conversation preview loading with message count fetching in a single call, avoiding the need for callers to make a separate `countMessagesByConversationIds` round-trip.
- Added `ConversationSummaryEntry` record that pairs a `ConversationPreview` with its `messageCount`.

**`MessagingUseCases.java`:**
- Added `listConversationsWithMessageCounts(ListConversationsQuery)` method that returns `ConversationListWithCountsResult` — a combined result containing both conversation entries (with message counts) and total unread count.
- Added `ConversationListWithCountsResult` record type.
- Imported `ConversationSummaryEntry` from `ConnectionService`.

**No changes to:** `CommunicationStorage.java`, `JdbiConnectionStorage.java` (batch SQL already optimized).

### Verification Results

- `MessagingUseCasesTest`: PASSED
- `ConnectionServiceTest`: PASSED
- `JdbiCommunicationStorageSocialTest`: PASSED
- `spotless:check`: PASSED

---

## Task 2: Harden The Send-Message Transaction Path

_Status: Completed_

### Changes Made

**`ConnectionService.java`:**
- Added race condition handling in `sendMessage`: wraps `saveConversation` in try-catch so that if a concurrent request already created the conversation (duplicate-key error), the method re-checks `getConversation` and proceeds instead of failing.
- Changed cleanup from `deleteConversationWithMessages` to `deleteConversation` when rolling back a newly created conversation. Since the message save failed, no messages exist — using the lighter soft-delete is semantically correct and avoids unnecessary transactional overhead.

**`CommunicationStorage.java`:**
- Strengthened Javadoc on `saveMessageAndUpdateConversationLastMessageAt`: now explicitly states that transactional implementations **must** override to guarantee no partial state, and notes the concurrency concern around `last_message_at` pointing to a non-existent message.
- Strengthened Javadoc on `deleteConversationWithMessages`: now recommends `deleteConversation` when callers know no messages exist.

**`ConnectionServiceAtomicityTest.java`:**
- Added `sendMessageSucceedsWhenConcurrentRequestAlreadyCreatedConversation` test: verifies that `sendMessage` recovers gracefully when `saveConversation` throws due to a concurrent creation, proceeding to send the message successfully.
- Added `ConcurrentCreateCommunications` test double that simulates the race condition by returning empty on the first `getConversation` call and throwing on `saveConversation`.

### Verification Results

- `ConnectionServiceAtomicityTest`: PASSED (6 tests, including new concurrent creation test)
- `JdbiConnectionStorageAtomicityTest`: PASSED
- `MessagingUseCasesTest`: PASSED
- `spotless:check`: PASSED

---

## Task 3: Clean Up Notification Payload Assembly And Notification Read Path

_Status: Completed_

### Changes Made

**`NotificationEventHandler.java`:**
- Extracted `contextWith(String pairId, String... extraKeyValues)` helper method that builds the common `matchId` + `conversationId` base map and appends extra key-value pairs. This centralizes the repeated `DATA_MATCH_ID, DATA_CONVERSATION_ID` pattern that was duplicated across all 5 notification handlers.
- Reduced `Map.of()` calls with repeated key constants to a single helper call per notification.
- Refactored `onRelationshipTransitioned` from a switch statement with empty blocks to a switch expression returning `Notification` (or `null` for no-op cases), avoiding Checkstyle `WhitespaceAround` conflict with Spotless formatting.
- Preserved all existing key names, key values, notification types, and data map contents exactly as before.

**`CommunicationStorage.java`:**
- Added `MarkNotificationReadResult` enum: `UPDATED`, `NOT_FOUND`, `NOT_OWNED`.
- Added `markNotificationAsReadChecked(UUID userId, UUID notificationId)` default method that combines the ownership check and mark-read into a single call, returning the outcome enum.

**`JdbiConnectionStorage.java`:**
- Overrode `markNotificationAsReadChecked` with an efficient implementation that uses a single transactional handle: fetches only the `user_id` column (via new `getNotificationOwnerId` DAO method) instead of the full notification row, then marks read. This reduces data transfer compared to the default implementation's full `getNotification` call.

**`SocialUseCases.java`:**
- Refactored `markNotificationRead` to use `markNotificationAsReadChecked` instead of the manual `getNotification` + `markNotificationAsRead` two-step. Uses a switch expression on the result enum for clear error mapping.

### Verification Results

- `NotificationEventHandlerTest`: PASSED
- `SocialUseCasesTest`: PASSED
- `JdbiCommunicationStorageSocialTest`: PASSED
- `ConnectionServiceTransitionTest`: PASSED
- `spotless:check`: PASSED

---

## Task 4: Optimize Profile Batch Lookup And Normalized-Profile Hydration

_Status: Completed_

### Changes Made

**`JdbiUserStorage.java`:**
- Added cache integration to `findByIds`: before hitting the database, checks the LRU user cache for each requested ID. Only uncached IDs trigger the SQL query and normalized-profile hydration. Loaded users are added to the cache for subsequent lookups. This reduces DB load and hydration cost for repeated batch lookups with overlapping IDs.

**`ProfileUseCases.java`:**
- Replaced the two-step `stream().filter(Objects::nonNull).toList()` + `Set.copyOf(requestedIds)` with a single loop that builds the deduplicated `Set<UUID>` directly, pre-sized to the input list size. This eliminates one intermediate list allocation and one set copy.
- The stable-ordering loop now checks null inline instead of relying on the pre-filtered list.

**No changes to:** `ProfileService.java` (already lean), `NormalizedProfileRepository.java` (already batch-optimized with single UNION ALL), `NormalizedProfileHydrator.java` (already single-pass in-place).

### Verification Results

- `ProfileServiceBatchLookupTest`: PASSED
- `ProfileUseCasesTest`: PASSED (22 tests)
- `JdbiUserStorageNormalizationTest`: PASSED
- `spotless:check`: PASSED

---

## Task 5: Optimize Matching And Match-Quality Internals

_Status: Completed_

### Changes Made

**`CandidateFinder.java`:**
- Added `HashMap` import. Added `Map<UUID, Double> distanceCache` to `findCandidates()` — new `isWithinDistanceCached()` method computes Haversine once per candidate, caches it, and the sort comparator reads from the cache instead of recomputing. Eliminates double Haversine computation for every qualifying candidate.
- Removed dead code exposed by PMD during final verification: `isWithinDistanceWithLogging`, `isWithinDistance`, `distanceTo`, and `formatLatLon` — all superseded by the cached distance path.

**`MatchingService.java`:**
- Pre-sized the `excluded` HashSet in `findPendingLikersWithTimes` with `alreadyInteracted.size() + blocked.size() + matched.size()`.

**No changes to:** `MatchQualityService.java` (already lean, no redundant computation found).

### Verification Results

- `MatchingServiceTest`: PASSED
- `CandidateFinderTest`: PASSED
- `MatchQualityServiceTest`: PASSED
- `spotless:check`: PASSED

---

## Task 6: Clean Up Local Verification Scripts And Related Docs

_Status: Completed_

### Changes Made

**`run_postgresql_smoke.ps1`:**
- Tagged failure messages with `[STARTUP]` prefix for PostgreSQL startup failures and `[MAVEN-SMOKE]` prefix for Maven smoke test failures.
- Added `Hint:` diagnostic lines after each failure category pointing to the next diagnostic step.

**`check_postgresql_runtime_env.ps1`:**
- Tagged all failure messages with category prefixes: `[ENV]` (missing tools), `[CONNECTIVITY]` (server not reachable), `[AUTH]` (password/login).
- Added `Hint:` lines with actionable next steps for each failure category.

**`start_local_postgres.ps1`:**
- Tagged all failure messages with category prefixes: `[CONFIG]` (invalid parameters), `[STARTUP]` (pg_ctl failures), `[DATABASE]` (psql/createdb failures).
- No behavioral changes — only message formatting improvements.

**`stop_local_postgres.ps1`:**
- Tagged failure message with `[STARTUP]` prefix.

**`run_verify.ps1`:**
- Tagged failure messages with `[STARTUP]`, `[MAVEN]`, and `[MAVEN-SMOKE]` prefixes.
- Added `Hint:` diagnostic lines for Maven and smoke test failures.

**`CI_AND_POSTGRESQL_GUIDE.md`:**
- Added new "Error categories in local scripts" section with a reference table mapping all 8 category prefixes to their meaning and typical causes.
- Added full documentation for `check_postgresql_runtime_env.ps1` including parameters and exit-code table.
- Updated `start_local_postgres.ps1` docs to list database validation, pg_stat_statements, and role defaults; noted category prefixes.
- Updated `stop_local_postgres.ps1` docs to note `[STARTUP]` prefix.
- Updated `run_postgresql_smoke.ps1` docs to note `[STARTUP]`/`[MAVEN-SMOKE]` prefixes and hint lines.
- Updated `run_verify.ps1` docs to note `[STARTUP]`/`[MAVEN]`/`[MAVEN-SMOKE]` prefixes and hint lines.

**`README.md`:**
- Added `.\run_postgresql_smoke.ps1` as a focused smoke-test entrypoint in the "Run locally" section.
- Added a note about error-category prefixes and a cross-reference to `CI_AND_POSTGRESQL_GUIDE.md`.

### Verification Results

- `check_postgresql_runtime_env.ps1`: Script structure verified (no functional changes to logic, only message formatting)
- `run_postgresql_smoke.ps1`: Script structure verified
- `start_local_postgres.ps1`: Script structure verified
- Documentation alignment confirmed against actual script behavior

---

## Final Verification

_Status: Completed_

- `mvn spotless:apply verify`: **BUILD SUCCESS** (excluding 4 PostgreSQL-dependent tests that require a running local PostgreSQL server)
- Checkstyle: 0 violations
- PMD: 0 violations
- Spotless: all 399 files clean
- SpotBugs: clean
- JaCoCo: all coverage checks met
- Tests: 1865 run, 0 failures, 0 errors, 2 skipped
- Excluded PostgreSQL tests (infrastructure-dependent, not related to plan changes):
  - `CheckDbTest` (dirty worktree file)
  - `TestJdbiMapping` (dirty worktree file)
  - `PostgresqlSchemaBootstrapSmokeTest`
  - `FindAllDiagnosticTest`
- `SocialViewModelTest.shouldHandleConcurrentRefreshes` is a known flaky concurrency test (passed on re-run)

---

## Summary

_Status: Completed_

All 6 tasks from the plan have been implemented and verified:

1. **Conversation Preview Loading** — Single-pass preview loading with combined message counts
2. **Send-Message Transaction Hardening** — Race condition handling for concurrent conversation creation
3. **Notification Payload Cleanup** — Centralized context helper and efficient notification read path
4. **Profile Batch Lookup** — LRU cache integration in `findByIds`, leaner set construction
5. **Matching Internals** — Distance caching, pre-sized collections, dead code removal
6. **Verification Scripts & Docs** — Categorized error messages with diagnostic hints, aligned documentation

No protected surface was modified: no route paths, DTO shapes, enum values, notification keys, or frontend-facing contracts changed.
