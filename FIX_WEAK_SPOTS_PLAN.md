# Fix-All-Weak-Spots Plan — Dating App

> **Date:** 2026-10-10
> **Scope:** every "not good enough" item from `DATING_APP_GAP_ANALYSIS.md` §2. Missing-greenfield features are out of scope.
> **Source of truth:** `src/main/java`, `src/test/java`, `pom.xml`.
> **Locked choices:** verification = provider seam + local impl; photos = abstract + local; presence/typing = build realtime, remove dead UI; moderation = backend + admin UI.
> **Status (2026-10-11): implemented, except Phase 6a/6b, which are dropped.** This project is backend-only; the Flutter app is the UI, so the JavaFX polish in 6a/6b is not planned. The JavaFX admin panel from Phase 4 is kept as built. Verified by `.\scripts\run_verify.ps1` (strict SpotBugs, PMD, JaCoCo gates, PostgreSQL smoke).
> **Decisions made after the plan was written:** retention before permanent erase is **30 days** (not 90); reports about an erased user are kept without their free text; a user who deletes an account while reported or under review starts their next profile `UNDER_REVIEW`; verification codes are stored hashed; an owner can always load their own photos.
> **Schema versions:** `V22` = verification attempts/lockout; `V23` = `moderation_audit`; `V24` = `presence_heartbeat`; `V25` = hashed verification codes, `users.purged_at`, `departed_account_flags`. Fresh `SchemaInitializer` + migrated `MigrationRunner` must both end identical (proven by 7.1 convergence test).

## Phase 0 — Contracts + test seams (do first)

### 0.1 Add RATE_LIMITED + GONE
- `app/usecase/common/UseCaseError.java:8-16` — add `RATE_LIMITED, GONE`.
- `app/api/RestApiServer.java:1731-1762 handleUseCaseFailure` — map `RATE_LIMITED → 429`, `GONE → 410`.
- Tests: new `UseCaseErrorMappingTest`; extend `RestApiDailyLimitTest`.

### 0.2 Stop e.getMessage() leak
- Files: `ProfileUseCases.java:66,80,116,146,158`, `ProfileMutationUseCases.java:111,152,216,295`, `ProfileInsightsUseCases.java:45,59,79,95`, `ProfileNotesUseCases.java:56,74,115,140`, `MatchingUseCases.java:262,312,328,358,382,394,416,429,442,499,520,536,577,592,606`, `MessagingUseCases.java:59,75,99,149,180,191,206,220,244,262`, `DashboardUseCases.java:88,102`, `SocialUseCases.java:126,144,162,204,228,252,276,291,326,342,362,377` via `internalFailure`.
- Change: central `internalFailure(op, e)` logs `e` server-side, returns `UseCaseError.internal("Failed to " + op + ".")`.
- `VerificationUseCases.java:50-63,77-90` — wrap `userStorage.save` in try/catch (currently unguarded).

### 0.3 UserContext.channel — use it
- `common/UserContext.java:7,11,14-28` — keep factory; log `channel` in `internalFailure` + audit events. No API break.

## Phase 1 — Verification provider seam
Files: `core/model/User.java:141-148,604-622`, `app/usecase/profile/VerificationUseCases.java:19-131`, `app/api/VerificationDtos.java:15-24`, `RestApiServer.java:1295-1325`, `ui/viewmodel/SafetyViewModel.java:221-277,306-319,403-406`, `app/cli/SafetyHandler.java:368-391`, `storage/jdbi/JdbiUserStorage.java:614-621,793-821,880-886`, `storage/schema/SchemaInitializer.java:106-112`.

1. `User.java` — add `attemptCount:int, lockedUntil:Instant, lastSentAt:Instant`; add `recordAttempt()/resetAttempts()/lockUntil()`; call `clearVerificationAttempt()` on success + expiry.
2. New `core/verification/VerificationSender.java` (`send(method, contact, code)`) + `LocalLogVerificationSender` (logs code; returns it only when `datingapp.showVerificationCode=true`). Inject via `ServiceRegistry.java:179`.
3. `startVerification` — 60s resend cooldown (`VALIDATION` if too soon), reset attempts, `sender.send()`.
4. `confirmVerification` — `lockedUntil > now → RATE_LIMITED`; +1 attempt per mismatch; lock 15 min after 5 fails; success → `markVerified() + resetAttempts() + clearVerificationAttempt()`.
5. `VerificationDtos.StartVerificationResponse.from()` — `devVerificationCode` only when flag true else null.
6. Schema `V22`: `attempt_count INT DEFAULT 0, locked_until TIMESTAMPTZ, last_sent_at TIMESTAMPTZ`; update fresh DDL + Jdbi mapper/bindings.
7. Tests: `VerificationUseCasesTest` (cooldown/lockout/expiry), `RestApiVerificationRoutesTest:79-83`, `SafetyViewModelTest:195,254-257`.

## Phase 2 — Single guarded swipe
Files: `app/usecase/matching/MatchingUseCases.java:266-314,446-501`, `core/matching/MatchingService.java:155-200,285-338`, `RestApiServer.java:378-389,933-1003`.

1. Delete `processSwipe` public entry; `recordLike(RecordLikeCommand(context,target,direction,enforceDailyLimit))` becomes sole path. Desktop `MatchingController`/`MatchingViewModel` switch to `recordLike`.
2. Move precheck inside try with `INTERNAL` mapping; limit failures → `RATE_LIMITED` + `resetsAt` from `DailyLimitService.getStatus():55-76`.
3. Delete `processSwipeWithinLock:301-311`; keep `recordLikeWithinLock:178-200` (duplicate-bypass `183-185` stays).
4. Publish `SwipeRecorded/MatchCreated` only on `outcome.persisted()` (keep `482-494`, drop `294-306` variant).
5. Add `POST /api/users/{id}/super-like/{targetId}` → `recordLike(SUPER_LIKE,true)`.
6. Tests: `MatchingUseCasesTest:303-338`, new `SwipeLimitRateLimitedTest`, `SuperLikeRouteTest`.

## Phase 3 — Photo abstraction + secure serving
Files: `app/api/RestApiPhotoStorage.java:27-63,137-259`, `RestApiServer.java:365-370,425-462,678-848`, `app/api/RestApiRequestGuards.java:57-135`, `ui/LocalPhotoStore.java:31-288`, `ui/ImageCache.java:72-449`, `core/AppConfig.java:192-198,394`.

1. New `core/media/PhotoStore.java` (`store/load/delete/deleteAllForUser/exists`) + `LocalDiskPhotoStore` (current logic moved). `RestApiPhotoStorage` becomes thin adapter via `StorageFactory`.
2. Harden local impl: magic-byte sniff (copy `LocalPhotoStore:244-267`), EXIF-strip on JPEG write, 200px thumbnail on upload, keep 5MB + jpg/png allowlist.
3. `GET /photos/*` — require rate-limit + acting-user + `ensureViewerCanReadUserProfile:1563-1570` + ACTIVE check; add `Cache-Control: private,max-age=3600` + `ETag`.
4. Tests: `RestApiPhotoRoutesTest` + `PhotoAuthTest` (401 anon, 403 blocked) + `PhotoCacheHeaderTest` + `ExifStripTest`.

## Phase 4 — Moderation backend + admin UI
Files: `core/matching/TrustSafetyService.java:140-181,317-405,476-556`, `core/model/User.java:76-82,808-824`, `core/connection/ConnectionModels.java:389-418`, `storage/jdbi/JdbiTrustSafetyStorage.java:24-114`, `storage/schema/SchemaInitializer.java:517-555`, `app/usecase/social/SocialUseCases.java:427-435`, `app/api/SocialDtos.java:48-74`, `RestApiServer.java:391-407,1228-1293`, `core/connection/ConnectionService.java:72-87`.

1. Rename: `applyAutoBanIfThreshold → applyAutoFlagIfThreshold`, `AUTO_BAN → AUTO_FLAG`, `userWasBanned → userWasFlagged`, `autoBanned → autoFlagged`. Update `TrustSafetyServiceTest:169-209`.
2. Reason-aware thresholds: `UNDERAGE/HARASSMENT` → threshold 1 + `escalated=true`; others 3.
3. Persistent audit: `V23 moderation_audit(id, actor, target, action, outcome, reason, context_json, created_at)`; logger writes DB + SLF4J.
4. Explicit `isBlocked` in `ConnectionService.sendMessage` + conversation reads.
5. Admin: `GET /api/admin/reports?status=pending` + `POST /api/admin/reports/{id}/{uphold|dismiss|ban}` (admin allowlist from config, else `FORBIDDEN`); minimal `admin.fxml` + `AdminViewModel/Controller` (reuses `BaseViewModel + AsyncScope`).
6. Tests: per-reason thresholds, `AdminRoutesTest` (403 vs 200).

## Phase 5 — GDPR true purge
Files: `storage/jdbi/JdbiAccountCleanupStorage.java:26-231`, `app/usecase/profile/ProfileMutationUseCases.java:256-297,400-407`, `app/usecase/auth/AuthUseCases.java:50-110`, `core/metrics/ActivityMetricsService.java:250-259`, `core/AppConfig.java:245-247,410-411`, `JdbiUserStorage.java:256-258`, `JdbiMatchmakingStorage.java:726-728`.

1. `applyDeletionState` — null name/bio/birthDate/latlon/photos/interests/lifestyle/dealbreakers/pace/verification + email/phone.
2. `deleteAccount` — `PhotoStore.deleteAllForUser` after `softDeleteAccount` (fixes orphan).
3. New `PurgeDeletedAccountsWorker` (hard-delete `deleted_at < now - softDeleteRetentionDays`, default 30); wire into `CleanupScheduler`; add `SOFT_DELETE_RETENTION_DAYS` env (`ApplicationStartup.java:306`).
4. Add `purgeDeletedBefore` to trust-safety, connection, likes DAOs (by `deleted_at`).
5. Pin `BANNED+deletedAt` (reusable) vs `BANNED+null` (blocked) with test.
6. Tests: `AccountDeleteTest`, `PurgeWorkerTest`.

## Phase 6 — UI bundle
**6a and 6b are dropped** (JavaFX-only; the Flutter app owns the UI). Only 6c's backend half applies.
Files: `MatchesController.java:530-653,938-970`, `ChatController.java:498-554`, `ProfileViewController.java:22-109`, `StandoutsController.java:133-242`, `matching.fxml:53-62`, `MatchingController.java:254-320,322-367,559-565`, `UiDataAdapters.java:341-410`, `ChatViewModel.java:82-85,172-207`, `NavigationService.java:179-189`, `PreferencesViewModel.java:35-43`, `ProfileController.java:244-250`, `ProfileNormalizationSupport.java:20-37`.

- 6a: avatars via `getImageAsync` + preload(20) in match/like/chat cells; `ProfileView` full rows + Like/Pass/Message/Block/Report; Standouts Like+Pass; discovery dots + tap-zones + fullscreen + visible SuperLike + vertical-drag→superLike.
- 6b: bind `loadFailed:70-71` + retry; `SkeletonLoader/forNode` in populate paths; `FADE` default nav; `OTHER` toggle + shared clamp/swap + min-span error.
- 6c realtime: `presence_heartbeat(user_id,last_seen,is_typing_in,updated_at)` + `PresenceService` (heartbeat 15s; ONLINE<2min/AWAY<10min) + `POST /typing` + `GET /presence`; `isTyping` reads heartbeat; `MatchesController:573-577` uses shared adapter; delete FUTURE comment + `NoOp` if unused.

## Phase 7 — Storage convergence + pool
Files: `MigrationRunner.java:14-17,215-288,326-328,614-654,723-781,796-817,819-825,838-845,1521-1587`, `SchemaInitializer.java:45-74,423-459,762-824`, `SqlDialectSupport.java:34-61`, `DatabaseManager.java:22-24,39,51,143-210,370-389,446-469`, `scripts/start_local_postgres.ps1:264-270`.

1. `SchemaBootstrapConvergenceTest` — fresh vs migrated-from-V1 diff on `information_schema`, fail on drift.
2. Post-baseline `verifyConvergence()` for V17/V16 outcomes.
3. `WARN` log on partial-index fallback; comment H2-always-fallback.
4. Per-version commits; `lock_timeout` set explicitly in `applySessionQueryTimeout`.
5. Pool: keepalive `0 → 30`, `leakDetectionThreshold 10s` dev; absolute H2 path `${user.dir}/data/...`.

## Phase 8 — Quality gates
Files: `pom.xml:296-553`, `.circleci/config.yml:31-34,121-186`, `scripts/run_verify.ps1:13-53`, `scripts/run_postgresql_smoke.ps1:2,74-93`, `LivePostgresqlTestConfig.java:34-38`.

1. CI + `run_verify.ps1` pass `-Pspotbugs-strict`; JaCoCo keeps 60% bundle + adds 70% LINE for `core/matching, core/connection, app/api, storage/jdbi`.
2. CircleCI single `mvn verify` (no split test/skipTests); pass `-Ddatingapp.pgtest.*`.
3. Replace 3 local `isConfigured()` with `assumeConfigured()`.
4. `run_verify.ps1:22 spotless:apply → spotless:check`; align smoke port `55432 → 5432` or parametrize.

## Execution order, acceptance + verification

**Order:** 0 → 1+2 (parallel-safe, different seams) → 3 → 4 → 5 → 6 → 7 → 8. Do not start 6c UI realtime before 6c storage table exists; do not start 5.2 file-GC before 3.1 `PhotoStore` exists.

**Per-phase commands (Windows PowerShell):**
- Focused: `mvn --% -Dcheckstyle.skip=true -Dtest=<FocusedTests> test`
- Gate: `mvn spotless:apply verify`
- Full local: `.\scripts/run_verify.ps1` at ends of 2 / 4 / 6 / 8.
- Keep layer boundaries: `core/` framework-free, usecases stay app boundary, UI via `BaseViewModel + ViewModelAsyncScope + UiDataAdapters`; use `AppClock.now()`, `Match.generateId`, `User.copy()`. No legacy names (`AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, `ui/controller`).

**Acceptance per phase (must all hold before moving on):**
- 0: `UseCaseErrorMappingTest` green; no `getMessage()` string reaches any API response (assert message equals stable text); `channel` appears in server log on INTERNAL.
- 1: wrong code 5x → 6th returns 429; resend within 60s → 400; expired code → 400 + attempts cleared; `devVerificationCode` null unless flag true; `V22` applies on fresh + migrated DBs.
- 2: no `processSwipe` references outside tests; limit-hit returns 429 with `resetsAt`; duplicate like after limit still succeeds; `POST super-like` works; `SwipeRecorded` fires once per persisted swipe.
- 3: anon `GET /photos/*` → 401; blocked viewer → 403; response has `Cache-Control + ETag`; uploaded JPEG has no EXIF/GPS; `PhotoStore` has no Javalin/FX imports.
- 4: `UNDERAGE` report flags on 1st report; normal spam needs 3; `moderation_audit` row per action; `sendMessage` from blocked user fails; non-admin `GET /api/admin/reports` → 403, admin → 200; keep deprecated JSON alias `autoBanned` alongside `autoFlagged` for one release (phone-alpha compat), remove next.
- 5: deleted user row has NULL name/bio/DOB/location + email/phone; `data/photos/<id>/` gone; re-provision mints new UUID; 30d-deleted kept, 91d-deleted purged including blocks/reports/messages/likes.
- 6: match/chat/likes cells show photos (fallback initials); `ProfileView` shows age/distance/score + 5 action buttons; Standouts Like/Pass works inline; discovery dots/tap/fullscreen/superLike-drag work; `loadFailed` shows retry; nav defaults FADE; `OTHER` selectable in both filter UIs; presence dot reflects heartbeat, typing shows only when heartbeat fresh.
- 7: `SchemaBootstrapConvergenceTest` green (fresh == migrated); fallback taken logs WARN; migrate under simulated lock succeeds per-version; pool keepalive 30 + absolute H2 path.
- 8: CI runs `-Pspotbugs-strict` green; JaCoCo 60% bundle + 70% scoped packages; all 3 PG tests run (not skipped) in `postgres-verify`; `spotless:check` fails on dirty format (no silent `apply`).

**Risks + mitigations:**
- V22/V23/V24 DDL on existing local DBs: per-version commits (7.4) so partial failure resumes; test migrate-from-V21 fixture before fresh.
- Photo auth is breaking: old anon `<img src=/photos/...>` breaks → bump `GET /photos/*` to require auth only after desktop + phone clients send bearer (coordinate) or support dual-mode with 1-release deprecation header.
- Swipe API removal: grep `processSwipe` callers first (desktop VM + CLI + tests); keep `@Deprecated processSwipe` delegating to `recordLike` for one release if phone-alpha still calls it.
- GDPR purge irreversible: dry-run mode (`--dry-run` logs would-delete counts) + keep 90d default; never shorten without backup.
- Presence traffic: heartbeat 15s per open chat only (not global poll); `presence_heartbeat` cleaned by existing `CleanupScheduler` (>24h stale deleted); typing `is_typing_in` auto-clears after 10s server-side so stuck "typing..." impossible.

**New config keys (add to AppConfig + app-config.json + validator):**
- `verificationResendCooldownSeconds=60, verificationMaxAttempts=5, verificationLockMinutes=15`
- `adminClerkIds=[] (allowlist for /api/admin/*)`
- `presenceOnlineSeconds=120, presenceAwaySeconds=600, typingFreshSeconds=10`

**Focused-test matrix:**
- 0: `UseCaseErrorMappingTest, ProfileUseCasesTest, MatchingUseCasesTest`
- 1: `VerificationUseCasesTest, RestApiVerificationRoutesTest, SafetyViewModelTest`
- 2: `MatchingUseCasesTest, RestApiDailyLimitTest, SwipeLimitRateLimitedTest, SuperLikeRouteTest, MatchingViewModelTest`
- 3: `RestApiPhotoRoutesTest, PhotoAuthTest, PhotoCacheHeaderTest, ExifStripTest, ImageCacheTest`
- 4: `TrustSafetyServiceTest, ReportValidationTest, AdminRoutesTest, ChatViewModelTest`
- 5: `AccountDeleteTest, PurgeWorkerTest, AuthUseCasesTest`
- 6: `MatchesViewModelTest, ChatViewModelTest, ProfileViewModelTest, StandoutsControllerTest, MatchingViewModelTest, LocationServiceTest`
- 7: `SchemaBootstrapConvergenceTest, PostgresqlRuntimeSmokeTest, PostgresqlSchemaBootstrapSmokeTest`
- 8: full `mvn spotless:apply verify` + `.\scripts/run_verify.ps1`
