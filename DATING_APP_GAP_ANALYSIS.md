# Dating App — What We Have, What's Weak, What's Missing

> **Date:** 2026-10-10
> **Source of truth:** `src/main/java`, `src/test/java`, `pom.xml` (docs disagree → trust code)
> **Method:** 8 parallel read-only explores (core, UI, REST, usecases, storage/ops, tests, safety, monetization). No edits made during exploration.
> **Workspace:** `C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program`

Verified facts (file:line) are below. Inferences are marked `[inferred]`.

---

## 1. What we currently have

### 1.1 Core domain (`core/`)

* **User aggregate** — `core/model/User.java:109-153`
  `id, name, bio, birthDate, gender, interestedIn, lat/lon+hasLocationSet, maxDistanceKm, minAge/maxAge, photoUrls(MAX_PHOTOS=6:30), state, lifestyle(smoking/drinking/wantsKids/lookingFor/education/heightCm), dealbreakers, interests(10 max), email/phone/isVerified/method/code/sentAt/verifiedAt, pacePreferences, deletedAt`.
  `UserState{INCOMPLETE,ACTIVE,PAUSED,UNDER_REVIEW,BANNED}:76-82`, `REQUIRED_PROFILE_FIELDS:37-57` (name/bio/birthDate/gender/interestedIn/location/photo/pace), `isInterestedInEveryone:424-426`, `copy():946-976`, id-only equality.
* **Match** — `core/model/Match.java:48-57,124-140`
  Deterministic `uuid_small_uuid_large` IDs, ordering guard `83-86`, `MatchState{ACTIVE,FRIENDS,UNMATCHED,GRACEFUL_EXIT,BLOCKED}:24-30`, transitions delegate to `RelationshipWorkflowPolicy`, `canMessage=ACTIVE||FRIENDS:229-231`.
* **ConnectionModels** — `core/connection/ConnectionModels.java:11`
  `Message(id,conversationId,senderId,content,createdAt):24 MAX_LENGTH=1000`, `Conversation(...lastMessageAt/readAt/archivedAt/visibleTo):57-69` same sorted-UUID scheme `:180-193`, `Like{LIKE,SUPER_LIKE,PASS}:344-348`, `Block:368`, `Report{SPAM,INAPPROPRIATE_CONTENT,HARASSMENT,FAKE_PROFILE,UNDERAGE,OTHER}:392-399`, `FriendRequest{PENDING,ACCEPTED,DECLINED,EXPIRED}:424-429`, `Notification{MATCH_FOUND,NEW_MESSAGE,FRIEND_REQUEST,...}:469-475`.
* **Matching filters** — `core/matching/CandidateFinder.java:148-159`
  8 hard gates: not-self, ACTIVE-only, not-interacted, blocked-either-way, UNMATCH-cooldown 168h (`AppConfig.java:27`), mutual gender/age, seeker-radius, both-ways dealbreakers. Distance-sorted. `findCandidatesForUser:185-198` returns `[]` without seeker location.
* **Scoring** — `core/matching/CompatibilityCalculator.java`
  age `66-94`, interest `96-117`, lifestyle `119-162` (education excluded), distance `164-175`, response `177-199`, pace `201-219`, activity from `updatedAt:221-244`. Aggregated in `MatchQualityService:313-323` (distance .15/age .10/interest .25/lifestyle .25/pace .15/response .10), `BrowseRankingService:65-86`, `StandoutService:135-158`, `DailyPickService:50-89` (seeded-random, not ML).
* **Trust/safety** — `core/matching/TrustSafetyService.java:140-181,476-556`
  report→`flagForReview` (not ban), block with match+conversation atomic transition + rollback, `ModerationAuditEvent/Logger` with PII redaction.
* **Limits/undo/activity** — `DailyLimitService:100/1/unlimited`, `UndoService:30s (AppConfig.java:399)`, `ActivityMetricsService` session-cap 500 + velocity 30/min.

### 1.2 App usecases (`app/usecase/`)

16 files. Envelope `UseCaseResult(success,data,error):common/UseCaseResult.java:6` + `UseCaseError{VALIDATION,NOT_FOUND,CONFLICT,UNAUTHORIZED,FORBIDDEN,DEPENDENCY,INTERNAL}:common/UseCaseError.java:8-16` + `UserContext(userId,channel):common/UserContext.java:7`.

* `auth/AuthUseCases.java:50-110` — Clerk RS256/JWKS verify, `findUserIdByClerkId`, striped-lock provision with `INCOMPLETE` placeholder, stale-link cleanup.
* `profile/` — `ProfileUseCases.java:20` facade + `ProfileMutationUseCases` (create/save/update/discovery-prefs/delete), `ProfileNotesUseCases`, `ProfileInsightsUseCases` (achievements/stats/sessions), `VerificationUseCases` (6-digit SecureRandom, 15-min TTL).
* `matching/MatchingUseCases.java:240-608` — browse/processSwipe/undo/listActive/listPaged/pendingLikers/sentLikes/standouts/recordLike/removeLike/matchQuality/archive/getDailyStatus/getUndoAvailability.
* `messaging/MessagingUseCases.java:46-263` — list/open/load/send/markRead/unread-count/delete/archive/deleteMessage.
* `social/SocialUseCases.java:105-379` — block/unblock/report/unmatch/friendZone/gracefulExit/friend-requests/notifications.
* `dashboard/DashboardUseCases.java:58-90` — aggregates user/completion/limits/pick/achievements/matches/unread + profile-nudge chain.

### 1.3 Backend (`app/api/RestApiServer.java:327-423`)

~45 routes: `GET /api/health`, `POST /api/auth/session`, `GET /api/users`, `GET /api/users/{id}`, `profile-edit-snapshot`, `presentation-context`, `browse` (+ `candidates` compat alias), `PUT profile`, `DELETE user`, photos CRUD + order + `GET /photos/*`, location countries/cities/resolve, matches/pending-likers/standouts/match-quality/like/pass/archive/undo, stats/achievements, notifications/read-all/read, friend-requests/accept/decline, graceful-exit/unmatch, blocked-users/block/report, verification start/confirm, conversations/messages/notes.

Guards `RestApiRequestGuards.java:57-68`: localhostOnly → LAN secret (`X-DatingApp-Shared-Secret` constant-time) → rate-limit (240/min `ip|method`) → identity (all `/api/users*` reads+writes + conversations + all POST/PUT/DELETE) → scoped identity (path `id/authorId/viewerId` == bearer, conversation participant). Clerk `ClerkJwtVerifier.java:32-119` RS256-only, iss/exp/nbf/sub/azp, `AppClock`-based time. `RestApiPhotoStorage.java:36-52` multipart `photo`, jpg/png only, 5MB streaming cap, traversal-safe, orphan cleanup.

### 1.4 UI (`ui/`, 13 `ViewType` in `NavigationService.java:76-89`)

LOGIN / DASHBOARD / PROFILE-editor / PROFILE_VIEW-readonly / MATCHING / MATCHES / CHAT / PREFS / STANDOUTS / SOCIAL / SAFETY / NOTES / STATS + achievement popup + `CreateAccountDialogFactory` + `LocationSelectionDialog` (Israel-only, debounce 250ms).

Patterns: `BaseViewModel.java:18-54` (loading+asyncScope+errorSink), `ViewModelAsyncScope` (virtual threads, runLatest, polling), `UiDataAdapters` (UiUserStore/MatchData/Social/ProfileNote/Presence), `ViewModelFactory` composition root, `BaseController` cleanup. Photos: `LocalPhotoStore(~/.datingapp/photos, MAX 6)` + `ImageCache(LRU100+async dedup+preload+EXIF+unsafe-host block)`. Discovery: drag-swipe `DRAG_THRESHOLD=137`, overlays, snap-back, keyboard ←/→/↑/Ctrl+Z/Esc, undo countdown, match popup. Chat: date separators, read receipts, unread badges, `Enter=send`, char counter.

### 1.5 Storage / ops (`storage/` + `scripts/`)

HikariCP 6.3 + Jdbi3-SqlObject 3.51, `DatabaseDialect{H2,POSTGRESQL}`, `DatabaseManager` singleton + pool `10/2/5s/3s/600s/1800s/keepalive 0`, per-conn `search_path+UTC+statement_timeout`. `StorageFactory` wires 7 Jdbi storages. `MigrationRunner V1..V21` transactional + `schema_version`, `SchemaInitializer` code-first DDL (users→clerk→likes→matches→messaging→social→moderation→profile→standouts→undo→quota→normalized). Local PG `localhost:55432/datingapp` via `start/check/reset/export/smoke/run_verify.ps1`, observability `pg_stat_statements+compute_query_id`, CI `postgres:17 + verify + smoke`.

### 1.6 Tests / gates

~190 Java +6 PS1 tests. Helpers: `TestStorages`, `TestUserFactory(Tel Aviv 32.0853,34.7818)`, `RestApiTestFixture`, `JavaFxTestSupport`, `ArchitectureTestSupport`. Gates `pom.xml:189-454`: compiler `release 25+preview`, surefire (+native-access, 120s kill), `spotless:check on verify (palantirJavaFormat 2.85)`, `checkstyle:validate (140col)`, `PMD CPD 100`, `SpotBugs report-only`, `JaCoCo LINE 60%` excluding `ui/**,cli/**,Main`.

---

## 2. What we have but is not good enough — needs change

1. **Verification simulated + leaks code.** `User.java:86-87 NOTE: simulated`, `VerificationDtos.java:22 devVerificationCode` in response, `SafetyViewModel.java:313-318` gating only message. No provider, attempt counter, lockout, resend cooldown. → Real SMS/email provider, hashed code, throttled confirm, remove code from API.
2. **Two swipe entries, one unguarded.** `MatchingUseCases:266-314 processSwipe` has zero limit check vs `446-501 recordLike` has `canLike/canSuperLike/canPass`. → Merge into single guarded `swipe`.
3. **Match grid faceless.** `MatchesController:530-653,938-970` icon-only `mdi2a-account`; chat cells initials `ChatController:498-554`; `573-577` always-offline. Discovery has photos, decision screen doesn't. → `getImageAsync` + preload in match/chat/likes cells.
4. **Stranger profile + standouts dead-ends.** `ProfileViewController:25-44` only name/bio/location/lookingFor/interests/photo (no age/distance/score/actions). `StandoutsController:166-242` View-Profile only. → Add age/distance/compat/lifestyle/badge + inline Like/Pass/Message/Block/Report.
5. **Presence/typing fake.** `UiDataAdapters:399-401 isTyping=false`, metrics-derived ONLINE/AWAY only. → Real channel or remove dots to stop confusion.
6. **Photo route open + local-only.** `GET /photos/*` bypasses guard chain `RestApiRequestGuards:59-61` (no auth/rate-limit), UUID-only security, no Cache/ETag/Range, no EXIF-strip/thumbnails/virus/NSFW. → Signed URLs/CDN/object-store + re-encode pipeline.
7. **Safety naming lie + no queue.** `applyAutoBanIfThreshold:323` only `flagForReview`, `ReportResult.userWasBanned:673` means flagged. No `moderation_audit` table, no admin list/review/ban/appeal, `UNDERAGE` same as spam, reporter must be ACTIVE. → Queue + persistent audit + fast-lane + block-check in `sendMessage`/conversation reads.
8. **GDPR incomplete.** `JdbiAccountCleanupStorage:26-92` nulls only email/phone, retains name/bio/DOB/location, orphans `data/photos/*` (deletes rows only `:150-152`), no purge worker for `softDeleteRetention 90d/cleanup 30d (AppConfig:410-411)`, `BANNED` reused for self-delete enables delete→unlink→re-provision evasion. → Purge job + file GC + anonymize + blocklist.
9. **Single-image discovery, keyboard-only superlike.** `matching.fxml` one `ImageView 400x350` + prev/next + `1/N`, no dots/tap-zones/fullscreen, `handleSuperLike:560-565` keyboard-only, no touch. → Dots + tap zones + visible SuperLike + fullscreen.
10. **Loading/error half-wired.** `SkeletonLoader/createLoadingOverlay (UiComponents:48-58,112-373)` dead in most screens; `MatchesViewModel:70-71 loadFailed` never bound; `ProfileView` no loading/error; `navigateTo:179-189` forces `NONE` so FADE/SLIDE `293-335` dead; two filter UIs diverge (Preferences sliders vs Profile text fields, `OTHER` unrepresentable). → Bind loading/retry, default FADE, unify filters.
11. **Usecase smells.** `UseCaseError` 7 codes too small (limits→CONFLICT, expired→VALIDATION, deleted→NOT_PROVISIONED); `catch(Exception)→INTERNAL(e.getMessage())` ~30 places leaks messages; `UserContext.channel` write-only; `saveProfile/savePhotoUrls` identical via `executeProfileSave:145`; `updateDiscoveryPrefs` silent-clamp vs `updateProfile` strict-reject; `processSwipe` vs `recordLike` event publish duplicated `:295-306 vs :483-493`; `sendMessage:129` validates content only; `countMessagesByConversationIds:195` no ownership check; `GUARDIAN=report≥1` farmable, `SUPER_LIKE` excluded from totalSwipes but included in stats, unlock only on saveProfile. → Add RATE_LIMITED/GONE, stable messages, merge paths, fix achievements.
12. **Storage convergence + pool.** Fresh path skips V2..V21 (`MigrationRunner:231-238`); H2 `MERGE KEY` vs PG `ON CONFLICT`; partial-index fallbacks diverge; single-txn 21-DDL under 5s lock-timeout; `keepalive 0`, `SELECT 1`, CWD-relative H2, `V3/V20` irreversible drops. → Fresh-vs-migrated diff test, per-version commits, prod pool profile.
13. **Gates blind where it hurts.** JaCoCo excludes UI/CLI, SpotBugs non-failing, CPD 100 misses clones, live-PG skip silently locally. → `-Pspotbugs-strict` in CI, 70%+ for matching/connection/api/jdbi, split PG execution.

---

## 3. What's missing entirely (no code found)

* **Liveness/ID/age assurance:** photo-verification, government ID, phone uniqueness, device fingerprint, anti-bot. Only `isVerified:boolean`.
* **Image safety:** nudity/violence/CSAM hash (PhotoDNA), manual queue, blur-by-default, duplicate-face/catfish detect, reporter evidence locker.
* **Realtime:** FCM/APNs registry + fan-out, websocket/SSE, typing/delivery, badge sync. Only in-DB `Notification` + polling (`chatBackground 15s/active 5s`).
* **Media pipeline:** S3/CDN, presigned URLs, video/voice, thumbnails, EXIF/GPS strip, perceptual hash.
* **Monetization (greenfield):** no `Subscription/Payment/Entitlement/Boost` model/table/route/config. Daily-limit `-1=unlimited` seam exists but nothing grants it. No `/super-like` route, no paywalled likers, no boost multiplier. `hasUnlimitedLikes/Passes:AppConfig:287-294` exists; super-like can never be unlimited by design.
* **Discovery scale:** geo-index/pagination (loads full `List<User>`), ELO/collaborative filtering, ML feedback, diversity guard beyond `standoutDiversityDays=3`, Boost/SuperBoost, Snooze/incognito/travel-mode, verified-only/online-now filters, multi-step undo + pass-expiry.
* **Messaging depth:** attachments, edit/react/search, report-message, harassment/URL/PII scan, first-message rules, disappearing messages.
* **Social graph:** mutual friends, Instagram/Spotify, success-story/unmatch-feedback (only `MatchArchiveReason` terse enum), date-safety (share plan/emergency/check-in), who-viewed-me, like-back shortcuts.
* **Notifications prefs:** per-type opt-in, quiet hours, digest, push prefs, mark-all-messages-read.
* **Compliance/ops:** GDPR export, Consent ledger, `ReportStatus/ModeratorReview/appeal`, retention purger, backups/PITR/restore-test, migration lock/checksum/dry-run, pool metrics/slow-query/VACUUM alerts, TLS/HSTS/headers, per-user throttles, idempotency keys, cursor pagination, `/ready`/metrics/tracing, API versioning/OpenAPI, Clerk webhook verify, token revocation.

---

## 4. Suggested order (smallest high-value first)

1. Merge `processSwipe+recordLike` + add `RATE_LIMITED/GONE` + `resetsAt` on limit failures.
2. Replace `devVerificationCode` with provider + hashed + throttled confirm.
3. Photos in match/chat/likes cells + flesh out `ProfileView` + inline Like/Pass on Standouts + bind `loading/loadFailed` + FADE default.
4. Move photos to signed-URL/object-store + EXIF-strip/thumbs/scan; auth photo GET.
5. Admin moderation queue + persistent audit + `UNDERAGE`/harassment fast-lane + block-check in messaging.
6. Retention purge + photo-file GC + true PII nulling + re-provision guard.
7. Push registry + fan-out alongside in-DB notifications; `/ready`, metrics, distributed limits.
8. Fresh-vs-migrated schema diff test + strict gates (`spotbugs-strict`, higher JaCoCo for core/api/jdbi).

