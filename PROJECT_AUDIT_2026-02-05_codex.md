**Project Audit 2026-02-05 (codex)**
Date: 2026-02-05
Scope: Full repo scan with focus on core, storage, CLI, UI, and docs.

**Method**
Commands used: `rg` for string search, `sg` for structural search, `Get-Content` for manual file review, and directory inventory with `Get-ChildItem`.

**Key Risks**
1. Data integrity can silently degrade due to foreign key constraints not being reliably applied in `src/main/java/datingapp/storage/DatabaseManager.java`.
2. The storage layer has no integration tests at all, so schema and query regressions can ship undetected.
3. Blocking a user does not transition matches or messaging state, leaving a path for blocked users to keep messaging.
4. Multiple features exist in core/CLI but are missing in JavaFX UI, causing inconsistent product behavior across interfaces.
5. Documentation and issue trackers are out of sync with current code, which slows decision-making and hides real risks.

**Issue Register (Actionable Findings)**
1. [Critical] Foreign key enforcement can fail silently due to mismatched column names and overly broad error suppression. Evidence: `src/main/java/datingapp/storage/DatabaseManager.java` uses `user_a_id`, `author_user_id`, `subject_user_id`, `viewed_user_id` in `addMissingForeignKeys`, while tables are created with `user_a`, `author_id`, `subject_id`, `viewed_id`. `isMissingTable()` treats any "not found" error as ignorable. Action: align column names and tighten error handling; add migration tests.
2. [High] No integration tests for JDBI/storage layer. Evidence: there is no `src/test/java/datingapp/storage` directory or storage tests; all tests are core/CLI/UI. Action: add JDBI/H2 tests for each storage interface and schema initialization.
3. [High] Blocking does not update match state or conversation visibility, so blocked users can still message. Evidence: `src/main/java/datingapp/core/TrustSafetyService.java` only saves blocks; `src/main/java/datingapp/core/Match.java` has `block()` but no service uses it; `src/main/java/datingapp/core/MessagingService.java` only checks match state. Action: connect block flow to match transition and conversation archive/visibility.
4. [High] Conversation archive/visibility fields exist in schema but are unused by service/UI. Evidence: `src/main/java/datingapp/storage/jdbi/JdbiMessagingStorage.java` exposes `archiveConversation` and `setConversationVisibility` while `src/main/java/datingapp/core/MessagingService.java` always returns all conversations. Action: implement filtering and UX around archived/hidden conversations.
5. [High] Dashboard notifications and unread counts are defined but never populated, making the UI misleading. Evidence: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java` declares `notificationCount` and `unreadMessages` but never sets them. Action: wire to `MessagingService` and `SocialStorage` or remove the fields.
6. [High] Standouts are implemented in core and CLI but have no JavaFX UI. Evidence: `src/main/java/datingapp/core/StandoutsService.java` and CLI handlers exist; no UI references. Action: add a JavaFX view or hide the menu option in UI until supported.
7. [Medium] Messaging “lastActiveAt” is not actually updated; the field does not exist. Evidence: `src/main/java/datingapp/core/MessagingService.java` comments about `lastActiveAt`, but `src/main/java/datingapp/core/User.java` has no such field and no setter is invoked before save. Action: add a real activity timestamp or remove misleading logic.
8. [Medium] Standouts scoring weights are hardcoded and diverge from `AppConfig`. Evidence: `src/main/java/datingapp/core/StandoutsService.java` uses fixed weights (0.20, 0.15, etc.). Action: move weights into `AppConfig` or document as intentionally separate.
9. [Medium] Age calculations use system default timezone instead of configured user timezone. Evidence: `src/main/java/datingapp/core/User.java` uses `LocalDate.now()` while `AppConfig` contains `userTimeZone`. Action: use a configured `Clock` or `userTimeZone` to avoid off-by-one-day issues.
10. [Medium] CSV serialization for preferences/sets limits queryability and increases data fragility. Evidence: `src/main/java/datingapp/storage/jdbi/UserBindingHelper.java` stores interests and dealbreakers as CSV. Action: consider JSON/H2 array types or join tables for queryable attributes.
11. [Medium] Migration strategy is add-columns-only with a static schema version. Evidence: `src/main/java/datingapp/storage/DatabaseManager.java` always writes version 1 and relies on `migrateSchemaColumns` without constraint/index evolution. Action: adopt versioned migrations (Flyway/Liquibase or internal version table with steps).
12. [Medium] Database schema lives inside a large Java class and duplicates storage definitions. Evidence: `src/main/java/datingapp/storage/DatabaseManager.java` embeds all DDL. Action: externalize schema to `.sql` resources and centralize column constants.
13. [Medium] UI only supports a single profile photo despite domain allowing two. Evidence: `src/main/java/datingapp/core/User.java` allows up to two photos; `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` writes a single URL. Action: add multi-photo gallery support or reduce model limit.
14. [Medium] Standouts data has no cleanup path and can grow unbounded. Evidence: `src/main/java/datingapp/core/StandoutsService.java` caches daily standouts without cleanup; no retention in `CleanupService`. Action: add retention and cleanup job.
15. [Low] Image cache eviction is not true LRU and may evict arbitrary entries. Evidence: `src/main/java/datingapp/ui/util/ImageCache.java` removes the first key found in a `ConcurrentHashMap`. Action: implement a real LRU or change naming to match behavior.
16. [Low] Messaging pagination parameters are not validated. Evidence: `src/main/java/datingapp/core/MessagingService.java` accepts `limit` and `offset` without bounds checks. Action: clamp values and return safe defaults on invalid input.
17. [Low] Documentation drift around current project phase and features. Evidence: `STATUS.md` still describes Phase 0, while `docs/completed-plans/PROJECT-ASSESSMENT-2026-02-05.md` marks later phases. Action: update or consolidate into a single source of truth.
18. [Low] Configuration documentation mismatch for DB password. Evidence: `AGENTS.md` says password is `changeit`, but `src/main/java/datingapp/storage/DatabaseManager.java` uses `dev`. Action: align docs and code to reduce onboarding friction.

**Unimplemented Or Partial Features (Cross-Layer Gaps)**
1. JavaFX UI for trust & safety actions (block, report, verify) exists in CLI only.
2. JavaFX UI for friend requests and notifications exists in CLI only.
3. JavaFX UI for profile notes exists in CLI only.
4. JavaFX UI for standouts exists in CLI only.
5. Authentication beyond user selection is not implemented.
6. Real-time chat (push or WebSocket) is not implemented; UI relies on manual refresh.
7. Read receipts are stored but not surfaced in UI.
8. Multi-photo profile gallery is not implemented in UI.
9. Notification counts on dashboard are defined but not wired.
10. REST API layer is not implemented.

**Test And Quality Gaps**
1. Storage layer has zero integration tests, including schema initialization and migrations.
2. StandoutsService and JdbiStandoutStorage have no dedicated tests.
3. Social storage (friend requests/notifications) has no integration tests.
4. UI controllers are mostly untested beyond CSS validation.
5. Threading and race-condition tests for viewmodels are absent.

**Documentation Drift And Process Debt**
1. Multiple issue tracking docs in `docs/uncompleted-plans` appear stale relative to current code, which creates false alarms and wastes time.
2. `STATUS.md` and several project assessment docs disagree about phase status and feature completion.
3. Password and environment setup values differ between `AGENTS.md` and runtime code.

**New Feature Suggestions (20)**
1. In-app safety check-in after first date.
2. Verified badge system with staged verification levels.
3. Multi-photo profile gallery with reorder and delete.
4. Icebreaker prompts and auto-suggestions in chat.
5. Real-time typing indicators and read receipts.
6. Location privacy controls with fuzzing and hide modes.
7. Advanced filters for discovery (education, lifestyle, values).
8. Interest-based events or group matching.
9. Match highlights that explain compatibility drivers.
10. “Pause visibility for X days” mode with auto-resume.
11. Date planning tools with shared availability.
12. Profile review coach with completion guidance.
13. In-app report history and safety center.
14. Premium boosts or priority discovery windows.
15. Quiet hours and message scheduling.
16. Profile timeline for recent updates.
17. Audio or short video intro clips.
18. Compatibility quizzes with shared results.
19. Post-match feedback loop for improving recommendations.
20. Web/mobile client parity (REST API + front-end).

**Improvement Suggestions (20)**
1. Extract database schema into versioned `.sql` migrations.
2. Add `Clock` or timezone injection for all time-based logic.
3. Centralize all weights and thresholds into `AppConfig` (including standouts).
4. Add dedicated storage integration test suite for JDBI/H2.
5. Introduce structured logging with request/session IDs.
6. Add a lightweight metrics layer for query times and candidate filtering.
7. Implement pagination in candidate discovery and CLI lists.
8. Add input validation for all CLI numeric and enum fields.
9. Add JSON schema validation for notification payloads.
10. Standardize error handling in UI viewmodels with a shared helper.
11. Add a cache for frequently accessed user previews.
12. Introduce service-level rate limiting for like/pass actions.
13. Add a cleanup job for standouts and notifications.
14. Upgrade ImageCache to true LRU with bounded memory.
15. Add load testing for match creation and message throughput.
16. Add schema checksums to detect drift between code and DB.
17. Document contract tests for storage interfaces.
18. Introduce feature flags for partially implemented features.
19. Standardize pagination conventions across services.
20. Add optional audit logging for moderation actions.

**Design Suggestions (15)**
1. Create a unified navigation hub with consistent primary actions.
2. Add a profile completion progress bar on the profile and dashboard screens.
3. Use a dedicated card layout for standouts and daily picks.
4. Add empty-state illustrations and guidance in each screen.
5. Add inline validation messages near inputs, not just toasts.
6. Provide a “Why this match?” detail panel.
7. Introduce a minimal design system with spacing and typography tokens.
8. Add compact and spacious layout modes for accessibility.
9. Use consistent badge styling for statuses and achievements.
10. Add a chat header with match status and safety shortcuts.
11. Add visible state indicators for paused and verified users.
12. Provide a dedicated notifications drawer.
13. Improve profile photo editing with crop and rotate tools.
14. Add subtle loading skeletons for slow views.
15. Use consistent iconography for actions (like, pass, block).

**Reliability Suggestions (20)**
1. Add integration tests for every storage interface and schema table.
2. Add migration tests to verify forward compatibility.
3. Add transaction boundaries for multi-write operations (match creation, block + archive, etc.).
4. Add defensive parameter validation for all public service methods.
5. Use retry/backoff for transient database failures.
6. Add health checks for DB connectivity at startup.
7. Introduce idempotency guards for like/pass actions.
8. Add a soft-delete strategy for users and related data.
9. Add background cleanup tasks for standouts, notifications, and old messages.
10. Add message content length validation at UI input level.
11. Add tests for concurrency and race conditions in messaging and matches.
12. Add circuit breakers or timeouts for slow storage operations.
13. Add a consistent error propagation model for UI toast vs log.
14. Add limits to prevent extremely large photo URLs or payloads.
15. Add integrity checks for CSV-serialized fields before persistence.
16. Add snapshot backups for the H2 file before migrations.
17. Add logging around schema initialization and migration steps.
18. Add a safe mode to recover from partial schema initialization.
19. Add monitoring for cache sizes and memory usage.
20. Add automated UI smoke tests for critical flows.
