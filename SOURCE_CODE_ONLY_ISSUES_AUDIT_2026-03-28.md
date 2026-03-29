# Source-Code-Only Audit (Second Pass, Re-verified) — 70 Findings

Generated on: **2026-03-28**
Scope: **local source code only** (`src/main/**`, `src/test/**`, `src/main/resources/**`)
No docs were used as evidence.

This report is a full rework of the previous pass:
- every prior claim was re-checked against current code,
- incorrect/partial wording was fixed,
- findings were expanded to **70 total**,
- and the report was reorganized by category for readability.

---

## Severity legend

- **Critical**: data integrity/security/correctness failures with high blast radius
- **High**: significant correctness or contract drift
- **Medium**: reliability, determinism, UX correctness, maintainability risks

---

## A) API / CLI / Bootstrap / Event Wiring (F01–F20)

### F01 — `openConversation` can false-negative due to pagination coupling (**High**)
- **Evidence:** `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java` — open flow searches conversation preview inside `getConversations(... limit, offset)` page and returns not found when missing from that page.
- **Why wrong:** existence check is tied to page slice, not conversation identity.
- **Impact:** valid conversations can be reported as missing.
- **Fix:** lookup preview by conversation ID directly (or decouple open from list paging).

### F02 — Message load failures are flattened to `CONFLICT` (**High**)
- **Evidence:** `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java` — failed load mapped to `UseCaseError.conflict(...)`.
- **Why wrong:** not-found/auth/validation/storage failures collapse into one error class.
- **Impact:** wrong API status mapping and weaker client handling.
- **Fix:** propagate structured failure codes from service/storage and map explicitly.

### F03 — Several REST handlers hard-code `409` on use-case failure (**High**)
- **Evidence:** `src/main/java/datingapp/app/api/RestApiServer.java:408,440,736,858` — manual `ctx.status(409)` for `likeUser`, `passUser`, `getConversations`, `sendMessage` failure paths.
- **Why wrong:** bypasses centralized failure mapping (`handleUseCaseFailure`).
- **Impact:** incorrect HTTP semantics and reduced API consistency.
- **Fix:** route these failures through `handleUseCaseFailure(...)`.

### F04 — Sender identity check is optional in message send route (**High**)
- **Evidence:** `src/main/java/datingapp/app/api/RestApiServer.java` — `resolveActingUserId(ctx).ifPresent(...)` before send.
- **Why wrong:** if acting user is absent, body `senderId` is trusted.
- **Impact:** easier sender impersonation in weakly-protected contexts.
- **Fix:** require acting user identity for mutating routes.

### F05 — Acting-user identity accepted from query parameter (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/api/RestApiServer.java:1024` — fallback to `ctx.queryParam(QUERY_ACTING_USER_ID)`.
- **Why wrong:** query params leak more easily via logs/history.
- **Impact:** identity leakage and weaker request hygiene.
- **Fix:** accept acting identity from authenticated header/context only.

### F06 — Account deletion mutates session/entity before persistence success (**High**)
- **Evidence:** `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` — `user.markDeleted(deletedAt)` and `user.pause()` are applied before `accountCleanupStorage.softDeleteAccount(user, deletedAt)` success is known.
- **Why wrong:** in-memory entity state is mutated before durable state is confirmed.
- **Impact:** state divergence on persistence failure.
- **Fix:** persist first (or wrap with compensating rollback semantics) and only then commit in-memory state.

### F07 — Mark-all-notifications-read is non-atomic (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` — read unread list then mark each individually.
- **Why wrong:** partial updates possible under failures/races.
- **Impact:** inconsistent unread state.
- **Fix:** one transactional storage operation for mark-all.

### F08 — Account-deleted notification handler is effectively no-op (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` — `onAccountDeleted` logs only.
- **Why wrong:** event ownership exists but no side-effect handling.
- **Impact:** missing cleanup/notification semantics.
- **Fix:** implement explicit behavior or remove handler registration.

### F09 — Config file lookup is cwd-relative and brittle (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:55,151` — `CONFIG_FILE = "./config/app-config.json"`, `Path.of(CONFIG_FILE)`.
- **Why wrong:** runtime behavior depends on process working directory.
- **Impact:** config silently not found in different launch contexts.
- **Fix:** explicit config path resolution (env/system property/app root).

### F10 — Cleanup scheduler swallows periodic failures (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/bootstrap/CleanupScheduler.java` — catches `Exception`, logs warning, continues.
- **Why wrong:** no escalation/retry policy.
- **Impact:** long-term cleanup drift can remain silent.
- **Fix:** retry/backoff + failure counters/alerts.

### F11 — Main loop can spin forever on EOF (**High**)
- **Evidence:** `src/main/java/datingapp/app/cli/CliTextAndInput.java:135` returns `""` on exhausted input; `src/main/java/datingapp/Main.java:100` treats empty option as invalid and continues.
- **Why wrong:** EOF is not treated as termination.
- **Impact:** non-interactive runs can hang.
- **Fix:** when empty + input exhausted, exit loop gracefully.

### F12 — `ApplicationStartup.shutdown()` is not guaranteed on unexpected exception (**High**)
- **Evidence:** `src/main/java/datingapp/Main.java:76-111` — shutdown only at normal end, not in `finally`.
- **Why wrong:** exceptions can skip cleanup lifecycle.
- **Impact:** scheduler/data-source lifecycle leaks in abnormal exits.
- **Fix:** move shutdown into `finally` or shutdown hook.

### F13 — Numeric env-var override parsing fails open (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:285-286` — catches `NumberFormatException`, logs, continues.
- **Why wrong:** invalid env values silently fallback.
- **Impact:** deployment config mistakes remain hidden.
- **Fix:** fail startup (or aggregate hard config errors).

### F14 — Profile preview age uses host timezone, not app policy (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/ProfileHandler.java:229` — `getAge(ZoneId.systemDefault())`.
- **Why wrong:** user-facing age can vary by host timezone.
- **Impact:** inconsistent age display near birthday boundaries.
- **Fix:** use configured/app zone consistently.

### F15 — Preference prompts claim defaults were used without applying them (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/ProfileHandler.java:646-669` logs “Using default …” on invalid input but does not write fallback values.
- **Why wrong:** messaging and state diverge.
- **Impact:** users think defaults applied when old values remain.
- **Fix:** either set defaults explicitly or correct messaging.

### F16 — `MatchingHandler` EOF handling checks impossible `null` path (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/MatchingHandler.java:268` checks `input == null`; `InputReader.readLine` returns empty string on exhaustion.
- **Why wrong:** exhaustion path is not handled correctly.
- **Impact:** potential endless invalid-choice loop on EOF.
- **Fix:** branch on empty + `wasInputExhausted()`.

### F17 — Conversation-load failure is rendered as empty inbox (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/MessagingHandler.java:441-445` returns `List.of()` on failure; `:72-73` prints “No conversations yet”.
- **Why wrong:** error state collapses into empty state.
- **Impact:** debugging and UX are misleading.
- **Fix:** separate error rendering from empty-data rendering.

### F18 — CLI conversation list is hard-capped to first 50 (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/MessagingHandler.java:442` uses `ListConversationsQuery(..., 50, 0)`.
- **Why wrong:** no paging path to older conversations.
- **Impact:** inaccessible threads beyond first page.
- **Fix:** add paging/navigation for conversation pages.

### F19 — `markConversationRead` result is ignored in CLI flow (**Medium**)
- **Evidence:** `src/main/java/datingapp/app/cli/MessagingHandler.java:168` calls use-case and discards outcome.
- **Why wrong:** failure path is silent.
- **Impact:** unread counters/badges can drift silently.
- **Fix:** inspect result and surface/retry failures.

### F20 — Relationship transition handler has unreachable `FRIENDS` branch (**Medium**)
- **Evidence:** `NotificationEventHandler.java:56` handles `case "FRIENDS"`; `SocialUseCases.RelationshipTransitionState` (`SocialUseCases.java:30-36`) emits `FRIEND_ZONE_REQUESTED`, `GRACEFUL_EXIT`, etc., not `FRIENDS`.
- **Why wrong:** dead branch indicates contract drift.
- **Impact:** intended notification path never fires.
- **Fix:** align emitted state names and handler switch cases.

---

## B) Core Domain and Config Contracts (F21–F40)

### F21 — Location validation misses finite-number checks (**High**)
- **Evidence:** `src/main/java/datingapp/core/profile/ValidationService.java` latitude/longitude checks only compare ranges.
- **Why wrong:** `NaN` can bypass range comparisons.
- **Impact:** invalid coordinates propagate into matching/scoring.
- **Fix:** require `Double.isFinite(...)` before bounds checks.

### F22 — Verification code generation uses non-crypto RNG (**High**)
- **Evidence:** `src/main/java/datingapp/core/matching/TrustSafetyService.java` uses `Random.nextInt(1_000_000)`.
- **Why wrong:** predictable code stream under adversarial analysis.
- **Impact:** weaker verification security.
- **Fix:** `SecureRandom` + attempt throttling.

### F23 — Duplicate-report protection is race-prone check-then-write (**High**)
- **Evidence:** `TrustSafetyService.java` checks `hasReported(...)` then `save(report)`; schema already has a unique key on reporter/reported pair.
- **Why wrong:** pre-check is race-prone and not authoritative; DB constraint is the real guard.
- **Impact:** extra round-trip, possible duplicate-key exception path under concurrency, and inconsistent error handling.
- **Fix:** rely on DB uniqueness as source of truth and handle duplicate-key violations explicitly.

### F24 — Block flow updates multiple subsystems without atomic boundary (**High**)
- **Evidence:** `TrustSafetyService.java` saves block, then mutates match/conversation state separately.
- **Why wrong:** partial completion leaves cross-subsystem inconsistency.
- **Impact:** blocked users may still appear/message depending on failure point.
- **Fix:** one atomic transition in storage layer.

### F25 — Conversation preview assembly uses N+1 query pattern (**Medium**)
- **Evidence:** `src/main/java/datingapp/core/connection/ConnectionService.java` loops conversations and calls latest-message/unread per item.
- **Why wrong:** linear additional storage calls per row.
- **Impact:** latency grows with conversation count.
- **Fix:** batch latest-message + unread aggregation query.

### F26 — Send message path writes in two non-atomic steps (**High**)
- **Evidence:** `ConnectionService.java` calls `saveMessage(...)` then `updateConversationLastMessageAt(...)`.
- **Why wrong:** second step failure leaves timeline inconsistent.
- **Impact:** stale conversation ordering/preview metadata.
- **Fix:** transactional single write API.

### F27 — `gracefulExit` mutates entities before capability check (**High**)
- **Evidence:** `ConnectionService.java` mutates match/conversation and later checks atomic transition support.
- **Why wrong:** failure can occur after in-memory transition.
- **Impact:** observable divergence vs persisted state.
- **Fix:** pre-check capability before mutation; persist through one atomic call.

### F28 — `unmatch` has same pre-check mutation hazard (**High**)
- **Evidence:** `ConnectionService.java` mirrors `gracefulExit` pattern.
- **Why wrong:** same ordering bug.
- **Impact:** partial/unreliable relationship state.
- **Fix:** same atomic pre-check pattern as F27.

### F29 — `FriendRequest` allows non-`PENDING` states with `respondedAt == null` (**Medium**)
- **Evidence:** `src/main/java/datingapp/core/connection/ConnectionModels.java:432-436` only forbids `respondedAt` for `PENDING`.
- **Why wrong:** any non-`PENDING` state (including `ACCEPTED`) can be created without a response timestamp.
- **Impact:** broken timeline/audit semantics.
- **Fix:** require `respondedAt != null` for non-pending states.

### F30 — Daily-like limit enforcement is TOCTOU-prone (**High**)
- **Evidence:** `MatchingService` checks `dailyService.canLike(...)` then writes swipe separately.
- **Why wrong:** concurrent swipes can overrun limit.
- **Impact:** policy bypass under concurrency.
- **Fix:** enforce limit atomically at write boundary.

### F31 — Core candidate filter contract computes age in UTC (**Medium**)
- **Evidence:** `src/main/java/datingapp/core/storage/UserStorage.java` default candidate filtering uses `u.getAge(ZoneOffset.UTC)`.
- **Why wrong:** timezone semantics are hard-coded in core default.
- **Impact:** adapter-level timezone differences cause edge-case drift.
- **Fix:** define and apply one explicit age-timezone policy end-to-end.

### F32 — JDBI age filtering uses DB local-date semantics (**Medium**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` uses `DATEDIFF('YEAR', birth_date, CURRENT_DATE())`.
- **Why wrong:** can diverge from UTC-based contract near date boundaries.
- **Impact:** off-by-one candidate eligibility around birthdays.
- **Fix:** pass explicit policy date/timezone into query logic.

### F33 — `recordLike(Like)` is public and bypasses swipe guardrails (**Medium**)
- **Evidence:** `src/main/java/datingapp/core/matching/MatchingService.java:138-145` writes directly via `persistLikeAndMaybeCreateMatch(...)`.
- **Why wrong:** skips `processSwipe` checks (active-state, self-swipe, daily limits).
- **Impact:** alternate call paths can bypass policy checks.
- **Fix:** restrict method visibility or route through guarded path.

### F34 — `processSwipe` does not enforce block relationship at method boundary (**Medium**)
- **Evidence:** `MatchingService.processSwipe(...)` has state/self/daily checks but no explicit blocked-user check.
- **Why wrong:** safety policy relies on upstream filtering rather than hard guard.
- **Impact:** direct/internal calls can violate block expectations.
- **Fix:** add explicit block-state guard in `processSwipe`.

### F35 — `getOrCreateConversation` bypasses `canMessage`/relationship validation (**High**)
- **Evidence:** `src/main/java/datingapp/core/connection/ConnectionService.java:245-251` creates conversation if absent without match/relationship check.
- **Why wrong:** creation not gated by messaging eligibility.
- **Impact:** unauthorized conversation creation paths.
- **Fix:** require `canMessage(userA,userB)` before create.

### F36 — `deleteConversation` does two destructive operations non-atomically (**High**)
- **Evidence:** `ConnectionService.java:278-279` deletes messages then conversation.
- **Why wrong:** partial deletion possible on mid-step failure.
- **Impact:** orphaned/half-deleted conversation state.
- **Fix:** transactional storage method for full conversation deletion.

### F37 — Phone normalization accepts malformed `+` placements (**Medium**)
- **Evidence:** `ValidationService.java:42,341-348` allows `+` anywhere by regex then keeps leading plus if present.
- **Why wrong:** malformed numbers can be normalized into different valid-looking values.
- **Impact:** bad phone identity data accepted silently.
- **Fix:** permit exactly one leading `+` and reject other placements.

### F38 — `validateBio` accepts blank values while completeness requires non-blank (**Medium**)
- **Evidence:** `ValidationService.validateBio(...)` tolerates blank; `User.getMissingProfileFields()` flags blank bio as missing.
- **Why wrong:** validator and completeness contract diverge.
- **Impact:** services can accept data later treated as incomplete.
- **Fix:** align validation and completeness rules.

### F39 — Activation policy and entity semantics are inconsistent for `PAUSED` users (**Medium**)
- **Evidence:** `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java:26-27` denies `PAUSED`, while `User.activate()` documents and permits `PAUSED -> ACTIVE`.
- **Why wrong:** policy-level rule and entity-level rule conflict.
- **Impact:** adapters can observe contradictory behavior depending on which path is used.
- **Fix:** align policy and entity transition rules (single canonical activation contract).

### F40 — Placeholder avatar sentinel counts as real photo completeness (**Medium**)
- **Evidence:** `User.setPhotoUrls(...)` preserves placeholder (`User.java`), and `ProfileCompletionSupport` checks only `!photoUrls.isEmpty()`.
- **Why wrong:** sentinel value satisfies photo requirement.
- **Impact:** profile completion can be over-reported.
- **Fix:** exclude placeholder sentinel from completeness scoring.

---

## C) Storage / Schema / Data Integrity (F41–F56)

### F41 — Message fetch query ignores conversation deletion/visibility state (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` message query filters by `conversation_id` + `deleted_at` on message, no conversation join.
- **Why wrong:** message visibility can diverge from conversation visibility.
- **Impact:** hidden/deleted thread messages may leak into reads.
- **Fix:** join conversations and apply conversation-level visibility/deletion checks.

### F42 — Message count query has same visibility mismatch (**High**)
- **Evidence:** `JdbiConnectionStorage.java` `COUNT(*)` over messages by conversation only.
- **Why wrong:** counts can include messages from logically inaccessible threads.
- **Impact:** unread and message counters drift.
- **Fix:** apply identical visibility predicates as read query.

### F43 — Invalid enum values collapse to `null` instead of hard failure (**Medium**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java` `readEnum(...)` catches `IllegalArgumentException` and returns `null`.
- **Why wrong:** corruption is silently downgraded.
- **Impact:** delayed null-driven failures and weak diagnostics.
- **Fix:** throw `SQLException` with enum/value context.

### F44 — `@BindBean` is used for record `Like` (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:708` — `void save(@BindBean Like like);`
- **Why wrong:** record binding should use method accessors (`@BindMethods`).
- **Impact:** runtime binding failures on DAO writes.
- **Fix:** switch to `@BindMethods`.

### F45 — `@BindBean` is used for records `Block` and `Report` (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java:37,96`.
- **Why wrong:** same record-binding risk as F44.
- **Impact:** moderation persistence failures at runtime.
- **Fix:** use `@BindMethods` for record params.

### F46 — Active swipe-session fetch is nondeterministic (`LIMIT 1` without order) (**Medium**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java:479` — active session query without `ORDER BY`.
- **Why wrong:** arbitrary row chosen if multiple active rows exist.
- **Impact:** unstable session behavior under corruption/race.
- **Fix:** add deterministic order and enforce uniqueness invariants.

### F47 — Account cleanup omits `daily_picks` removal (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java` deletes `daily_pick_views` but not `daily_picks`.
- **Why wrong:** soft-deleted users can retain recommendation artifacts.
- **Impact:** stale/referentially inconsistent recommendation data.
- **Fix:** delete/soft-delete `daily_picks` for user actor/target rows.

### F48 — Test DB detection relies on broad substring heuristic (**Medium**)
- **Evidence:** `src/main/java/datingapp/storage/DatabaseManager.java:167` — `url.contains("test") || url.contains(":mem:")`.
- **Why wrong:** a substring match can misclassify JDBC URLs.
- **Impact:** misclassification can route credential handling through the test-path behavior unexpectedly.
- **Fix:** explicit environment/profile flagging.

### F49 — Local file DB falls back to default dev password (**High**)
- **Evidence:** `DatabaseManager.java:21,158-159` returns `DEFAULT_DEV_PASSWORD` (`"dev"`) for local file URLs.
- **Why wrong:** insecure credential default in runtime path.
- **Impact:** accidental weak credential posture.
- **Fix:** require explicit credentials except explicit dev profile.

### F50 — `friend_requests` schema lacks pair uniqueness invariant (**High**)
- **Evidence:** `src/main/java/datingapp/storage/schema/SchemaInitializer.java:298-311` defines table/index, no unique constraint on `(from_user_id, to_user_id)` / pending pair.
- **Why wrong:** duplicate pending requests can coexist.
- **Impact:** inconsistent request lifecycle and query ambiguity.
- **Fix:** add uniqueness constraint for active/pending pairs.

### F51 — `getPendingFriendRequestBetween` is unconstrained for one-row `Optional` contract (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java:466-471` query has no `ORDER BY`/`LIMIT` while return type is `Optional<FriendRequest>`.
- **Why wrong:** duplicates can violate expected cardinality.
- **Impact:** runtime mapping errors or unstable behavior.
- **Fix:** enforce uniqueness + deterministic selection.

### F52 — `deleteExpiredSessions` can delete active sessions by `started_at` only (**High**)
- **Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java:551-552` — `DELETE FROM swipe_sessions WHERE started_at < :cutoff`.
- **Why wrong:** active sessions are not excluded.
- **Impact:** active-state loss and metric/session distortion.
- **Fix:** filter by terminal states or ended timestamp criteria.

### F53 — Latest user/platform stats selection is tie-nondeterministic (**Medium**)
- **Evidence:** `JdbiMetricsStorage.java` latest queries sort only by `computed_at DESC` + `LIMIT 1`.
- **Why wrong:** equal timestamps can yield arbitrary winner.
- **Impact:** unstable “latest” reads.
- **Fix:** add deterministic secondary order (e.g., id DESC).

### F54 — `getAllLatestUserStats` can return multiple “latest” rows per user on ties (**Medium**)
- **Evidence:** `JdbiMetricsStorage.java` uses `NOT EXISTS (s2.computed_at > s.computed_at)`.
- **Why wrong:** equal timestamps satisfy condition for multiple rows.
- **Impact:** duplicated latest stats entries.
- **Fix:** use window function (`ROW_NUMBER`) with deterministic tie-break.

### F55 — Conversation/message ordering lacks deterministic tie-breakers (**Medium**)
- **Evidence:** `JdbiConnectionStorage.java` orders conversations by `COALESCE(last_message_at, created_at) DESC`; messages by `created_at ASC`.
- **Why wrong:** equal timestamps can reorder nondeterministically.
- **Impact:** flickering order and unstable pagination.
- **Fix:** append stable key (`id`) to ordering.

### F56 — Notification mutation by `id` is not user-scoped (**Medium**)
- **Evidence:** `JdbiConnectionStorage.java` — `markNotificationAsRead(id)` and `deleteNotification(id)` without user predicate.
- **Why wrong:** cross-user safety relies entirely on caller discipline.
- **Impact:** accidental/abusive cross-user mutation risk.
- **Fix:** scope updates/deletes by `(id, user_id)`.

---

## D) UI / ViewModel / Resource Layer (F57–F63)

### F57 — Chat presence data access is wired as no-op in factory (**Medium**)
- **Evidence:** `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` constructs `ChatUiDependencies(..., new NoOpUiPresenceDataAccess())`.
- **Why wrong:** production path intentionally disables presence backend.
- **Impact:** presence UX cannot function even if backend is ready.
- **Fix:** wire real `UiPresenceDataAccess` from service registry.

### F58 — Read-only profile screen clears image instead of fallback avatar (**Low/Medium**)
- **Evidence:** `src/main/java/datingapp/ui/screen/ProfileViewController.java` sets `profileImageView.setImage(null)` when URL is blank.
- **Why wrong:** missing-photo UX is inconsistent vs other screens with placeholders.
- **Impact:** broken visual consistency and ambiguous state.
- **Fix:** fallback to `ImageCache` default avatar.

### F59 — Note preview truncation is UTF-16 code-unit based (**Medium**)
- **Evidence:** `src/main/java/datingapp/ui/screen/NotesController.java` uses `substring(...)` for truncation.
- **Why wrong:** can split surrogate pairs/graphemes.
- **Impact:** broken emoji/non-BMP rendering.
- **Fix:** truncate by code points or grapheme boundaries.

### F60 — Login-streak computation depends on host clock/timezone (**Medium**)
- **Evidence:** `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java` uses `ZoneId.systemDefault()` and `LocalDate.now()`.
- **Why wrong:** non-deterministic across environments.
- **Impact:** inconsistent streak values and brittle tests.
- **Fix:** inject clock/zone policy.

### F61 — Stats refresh scans all conversation pages to compute messages exchanged (**Medium**)
- **Evidence:** `StatsViewModel.fetchMessagesExchanged(...)` loops pages until empty.
- **Why wrong:** O(total conversations) on each refresh.
- **Impact:** degraded dashboard responsiveness at scale.
- **Fix:** use pre-aggregated counter in use-case/storage.

### F62 — `stats.fxml` keeps a hidden hardcoded achievement fallback block (**Low/Medium**)
- **Evidence:** `src/main/resources/fxml/stats.fxml` contains `staticAchievementsContainer` with fixed entries while runtime list binding is in controller/viewmodel.
- **Why wrong:** hidden fallback markup duplicates achievement definitions in UI resources.
- **Impact:** if fallback is ever re-enabled or copied forward, it can drift from domain achievement data.
- **Fix:** remove the static fallback block or auto-generate it from the same data source.

### F63 — Some ViewModel failure paths collapse to empty data states (**Medium**)
- **Evidence:** `StatsViewModel.fetchAchievements(...)` uses `result.success() ? ... : List.of()`; `MatchesViewModel` sets pending likers to `List.of()` on failure.
- **Why wrong:** failure and true-empty states are merged in these paths.
- **Impact:** users see “no data” instead of “data failed to load,” reducing diagnosability.
- **Fix:** surface explicit load-error states (while keeping empty states distinct).

---

## E) Test and Architecture Guardrails (F64–F70)

### F64 — `DashboardViewModelTest` uses timing-sensitive concurrency pattern (**Medium**)
- **Evidence:** `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java` spawns many virtual threads + timeout polling.
- **Why wrong:** scheduler timing drives outcome.
- **Impact:** flaky CI with low signal.
- **Fix:** deterministic synchronization (latches/fakes) over timing waits.

### F65 — `StatsViewModelTest` is largely happy-path and weak on failure behavior (**Medium**)
- **Evidence:** `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java` focuses on success/default shape, limited failure-path assertions.
- **Why wrong:** fallback/error regressions can slip through.
- **Impact:** lower regression detection for async failure handling.
- **Fix:** add negative-path tests for use-case/storage failures.

### F66 — `SocialControllerTest` includes weak non-empty-list oracles in several paths (**Low/Medium**)
- **Evidence:** `src/test/java/datingapp/ui/screen/SocialControllerTest.java` contains checks like `!list.getItems().isEmpty()` alongside a smaller number of stronger assertions.
- **Why wrong:** non-empty assertions alone are weak for content-mapping correctness.
- **Impact:** some rendering/mapping regressions may still pass.
- **Fix:** increase concrete assertions for item fields/order/read-state in those paths.

### F67 — Time policy architecture guard is disabled (**High**)
- **Evidence:** `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java` has `@Disabled` on `noFeatureCodeUsesZoneIdSystemDefault`.
- **Why wrong:** key architectural constraint is not enforced by CI.
- **Impact:** timezone-policy drift can re-enter unnoticed.
- **Fix:** re-enable with strict allowlist + migration deadline enforcement.

### F68 — Adapter boundary test scans text import lines, not semantic references (**Medium**)
- **Evidence:** `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java` matches string lines like `import datingapp.core.storage.`.
- **Why wrong:** misses non-import usages / fully qualified references.
- **Impact:** false negatives in boundary enforcement.
- **Fix:** switch to AST/semantic checks.

### F69 — Safety verification tests are mostly smoke tests due random code generation (**Medium**)
- **Evidence:** `src/test/java/datingapp/app/cli/SafetyHandlerTest.java:355,366,374` uses `assertDoesNotThrow(handler::verifyProfile)` and comments random-code limitation.
- **Why wrong:** success semantics not asserted deterministically.
- **Impact:** verification-flow regressions can pass.
- **Fix:** inject deterministic verifier/code provider in tests.

### F70 — Messaging CLI tests skip actual message-save path (**Medium**)
- **Evidence:** `src/test/java/datingapp/app/cli/MessagingHandlerTest.java:209-214` comments “Skip saveMessage … binding issue” and asserts no-throw only.
- **Why wrong:** core persistence path is untested.
- **Impact:** message-write regressions can evade test suite.
- **Fix:** add dedicated DAO binding test and an integration path that exercises real save.

---

## What changed vs prior document

- Re-verified every prior claim and corrected inaccurate wording.
- Replaced weak/incorrect claim(s) with source-accurate ones.
- Expanded from 40 to **70** verified findings.
- Reorganized into domain categories with consistent per-finding structure.
