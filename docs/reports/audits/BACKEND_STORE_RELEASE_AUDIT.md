# BACKEND STORE-RELEASE AUDIT

**Repo:** `Date_Program` (backend) · **Reconciled against:** `New_Flutter_Frontend\flutter_dating_application_1` · **Date:** 2026-09-13 · **Status labels:** `[verified]` code-read · `[inferred]` necessary implication from code. All paths relative to repo root unless prefixed `FE:` (frontend repo).

---

## 1. Executive summary

**What is genuinely real today** `[verified]`: a complete bearer-token auth flow (BCrypt-12, HS256 JWT 15-min access tokens, hashed+rotated+revocable 30-day refresh tokens, `AuthUseCases.java:45-117`); profile read/edit with computed completion state (8 required fields, `User.java:37-57`); photo upload/list/delete/reorder with public serving (`RestApiPhotoStorage.java:36-52`, `RestApiServer.java:433-470`); IL-only location resolution; full browse→like/pass→match→chat→safety flow with quotas, blocks, reports (auto-ban at 3), unmatch/graceful-exit; 5-type in-app notification system; 11-achievement stats engine; soft account deletion with email reuse and refresh-token revocation (`JdbiAccountCleanupStorage.java:26-76`). 59 route registrations (`RestApiServer.java:339-431`), 208 test files.

**What is broken or stubbed for a store release**:

- **P0 break, shipped today:** the Flutter client sends **no body** on `POST /api/users/{id}/report/{targetId}` (FE `api_client.dart:338-341`), but the backend requires `{reason,...}` (`SocialDtos.java:74`, handler `RestApiServer.java:1316-1336`) → every in-app report from the current client **fails 400**. This is exactly the "pick one contract" item the April doc flagged (`2026-04-30-phone-alpha-backend-api-requirements.md:398-408`); backend chose reason-required, client never updated.
- **P0 break, shipped today:** REST like/pass never record undo state (`MatchingService.java:151-165` vs `:307`), so `POST /undo` after a REST like always returns "No recent swipe to undo" — the client's undo affordance is dead.
- **P0 leak:** `GET /api/users` and `GET /api/users/{id}` are anonymous read routes (`RestApiRequestGuards.java:135-144`); anonymous callers bypass block/visibility checks (`RestApiServer.java:1592-1595`). Unauthenticated enumeration of all users + profile scraping.
- **P0 privacy:** `/photos/*` serves image bytes with **no auth at all** (guards skip non-`/api/` paths, `RestApiRequestGuards.java:57-61`; handler `RestApiServer.java:434-468`), raw upload bytes stored **unmodified** — EXIF/GPS preserved server-side (`RestApiPhotoStorage.java:36-52`).
- **P0 compliance:** no web deletion path, photo **files orphaned on account delete** (DB rows deleted, `data/photos/<userId>/` never cleaned, `JdbiAccountCleanupStorage.java:151-153`), no consent records, no data export, no password reset, no email delivery of any kind, no push, verification returns the dev code in the API response (`VerificationDtos.java:15-24`).
- **Product honesty:** conversation summaries omit unread count, preview, sender, photo (`MessageDtos.java:12-13`) — client invents all of them; `GET messages` silently clears unread as a side effect (`RestApiServer.java:1450-1451`); "verified" means only a dev-shown 6-digit code was echoed back.

**Bottom line:** the core journey works end-to-end over bearer auth; a store release is blocked by the report-contract break, the anonymous user-list/profile leak, photo privacy/EXIF, and Google Play's deletion/UGC-moderation/consent requirements. Everything else is enrichment (§5) or deferred product decisions (§8).

---

## 2. Endpoint inventory

Auth columns: **S** = shared secret `X-DatingApp-Shared-Secret` (enforced on all `/api/` except `/api/health`, `RestApiRequestGuards.java:77-85`) · **B** = bearer enforced · **U** = acting-identity required (bearer subject; a supplied `X-User-Id` must equal subject, `RestApiIdentityPolicy.java:157-165`; when no bearer exists — localhost mode — legacy `X-User-Id` alone is trusted for mutating routes, `:47-73,75-91`). Error body everywhere: `{"code":"...","message":"..."}` (`RestApiDtos.java:13`; doc claiming `{"error",...}` is wrong — `docs/api/API-SPECIFICATION.md:37-44` contradicts code).

| Method + path | S | B | U | Handler | Status | Request → Response |
|---|---|---|---|---|---|---|
| `GET /api/health` | no | no | no | `RestApiServer.java:351` | implemented | → `{status:"ok", timestamp:<epoch-millis>}` `[verified]` — client's epoch special-case is correct (FE `health_status.dart:9-15`) |
| `POST /api/auth/signup` | yes | no | no | `:491-500` | implemented | `{email,password,dateOfBirth,name?}` → 201 `AuthResponse{accessToken,refreshToken,expiresInSeconds,user}` |
| `POST /api/auth/login` | yes | no | no | `:502-510` | implemented | `{email,password}` → `AuthResponse` |
| `POST /api/auth/refresh` | yes | no | no | `:512-520` | implemented (rotates) | `{refreshToken}` → `AuthResponse` |
| `POST /api/auth/logout` | yes | no | no | `:522-530` | implemented (single token) | `{refreshToken}` → 204 |
| `GET /api/auth/me` | yes | yes | yes | `:532-539` | implemented | → `AuthUserDto{id,email,displayName,profileCompletionState}` |
| `GET /api/users` | yes | **no** | no | `:543-553` | implemented — **must not ship** | → bare `List<UserSummary>` of all users |
| `GET /api/users/{id}` | yes | **no** | no | `:555-567` | implemented — must require bearer | → `UserDetail` (fields §B3) |
| `GET /api/users/{id}/profile-edit-snapshot` | yes | yes | yes | `:569-581` | implemented | → `ProfileEditSnapshotDto` |
| `GET /api/users/{viewerId}/presentation-context/{targetId}` | yes | yes | yes | `:583-595` | implemented | → `PresentationContextDto{summary,reasonTags,details,generatedAt}` |
| `GET /api/users/{id}/browse` | yes | yes | yes | `:632-645` | implemented | → `BrowseCandidatesResponse` |
| `PUT /api/users/{id}/profile` | yes | yes | yes | `:647-702` | implemented | `ProfileUpdateRequest` → `ProfileUpdateResponse` |
| `GET /api/users/{id}/candidates` | yes | yes | yes | `:897-910` | implemented, deprecated alias (sends `Deprecation:true` + successor `Link`, `:1761-1764`) | → bare array |
| `DELETE /api/users/{id}` | yes | yes | yes | `:704-717` | implemented (soft) | → 204 |
| `GET/POST/DELETE/PUT /api/users/{id}/photos…` | yes | yes | yes | `:721-861` | implemented | see §E |
| `GET /api/location/countries` / `cities` / `POST resolve` | yes | no | resolve exempt (`RestApiRequestGuards.java:117`) | `:597-630` | implemented | see §B |
| `GET /api/users/{id}/matches` | yes | yes | yes | `:938-958` | implemented | `?limit&offset` → `PagedMatchResponse` |
| `GET …/pending-likers` / `standouts` / `match-quality/{matchId}` | yes | yes | yes | `:1048-1065,1067-1092,1109-1122` | implemented | see §C |
| `POST …/like/{targetId}` / `pass/{targetId}` / `undo` / `matches/{matchId}/archive` | yes | yes | yes | `:976-1010,1012-1030,1038-1045,1094-1107` | implemented; **undo broken for REST likes** | see §C |
| `GET …/stats` / `achievements` | yes | yes | yes | `:1124-1136,1138-1153` | implemented | see §H |
| `GET/POST …/notifications…` (3 routes) | yes | yes | yes | `:1155-1199` | implemented | see §G |
| `GET/POST …/friend-requests…` (4 routes) | yes | yes | yes | `:1201-1239` | implemented; **client does not call** (out-of-scope doc §9.1) | see agent F |
| `POST …/relationships/{targetId}/unmatch` / `graceful-exit` | yes | yes | yes | `:1241-1269` | implemented | → `TransitionResponse{success,friendRequestId,errorMessage}` |
| `GET …/blocked-users` | yes | yes | yes | `:1287-1299` | implemented (thin DTO) | → `{blockedUsers:[{userId,name,statusLabel}]}` |
| `POST …/block/{targetId}` / `DELETE …/block/{targetId}` | yes | yes | yes | `:1271-1285,1301-1314` | implemented | → `ModerationResponse` / 204 |
| `POST …/report/{targetId}` | yes | yes | yes | `:1316-1336` | implemented; **contract mismatch with client** | `{reason,description?,blockUser}` → `ReportResponse` |
| `POST …/verification/start` / `confirm` | yes | yes | yes | `:1338-1368` | implemented, simulated | see §B8 |
| `GET …/conversations` | yes | yes | yes | `:1372-1400` | implemented (thin DTO) | `?limit&offset` → bare list |
| `DELETE /api/users/{id}/conversations/{conversationId}` / `POST …/archive` | yes | yes | yes | `:1402-1424` | implemented | 204 |
| `GET/POST/DELETE /api/conversations/{conversationId}/messages…` | yes | yes | yes | `:1441-1497` | implemented | see §D |
| `GET/PUT/DELETE /api/users/{authorId}/notes…` (4 routes) | yes | yes | yes | `:427-431` | implemented; moderation/admin surface, client does not call | `ProfileNoteDto` |
| `GET /photos/{userId}/{file}` | **no** | **no** | **no** | `:434-468` | implemented — public by design | → image bytes |

Client calls that don't exist / behave differently: none missing outright; mismatches are (a) report body, (b) `PhotoDto` fields `thumbnailUrl/mediumUrl/moderationStatus/rejectionReason` are **parsed by the client but never sent by the backend** (FE `photo_dto.dart:36-53` vs `PhotoDtos.java`), (c) conversations are a bare list with only 5 fields while the client tries 8+ fallback keys (FE `conversation_summary.dart:24-61`), (d) achievements arrive as a snapshot the client has to recursively rummage through (FE `api_client.dart:945-1073`).

---

## 3. Journey-by-journey verdicts

### A. Identity, sessions, account lifecycle

- **A1 Real and solid** `[verified]`. Tables: `users` + `user_credentials` (BCrypt hash, `AuthUseCases.java:61-62`, rounds 12 `:30`) + `auth_refresh_tokens` (V19 migration, `MigrationRunner.java:679-681`). Access = hand-rolled HS256 JWT `{sub,email,iss,iat,exp}` (`AuthTokenService.java:26-46`), TTL 900s; refresh = 32-byte SecureRandom opaque, **SHA-256-hashed at rest** (`:226-235`), TTL 30d (`config/app-config.json:62-66`). Rotation on every refresh with `replaced_by_token_id` chaining (`AuthUseCases.java:97-102`); revoked/expired rejected (`:181-184`). **No replay family-kill** (reusing a rotated token doesn't revoke its descendants) — acceptable for alpha, list as P2. Logout revokes **only the presented token** (`:109-117`) — single device; there is no "log out everywhere" endpoint (A5: multi-device works implicitly via independent refresh tokens; no session listing, no device identification).
- **A2 Password reset: does not exist** `[verified]` (grep `forgot|resetPassword` = zero). Needs full build (proposal in §4, P0-3/P1-8).
- **A3 Email verification: none**; email IS normalized (`TextNormalization.normalizeEmail`) and unique (`uk_users_email`, `MigrationRunner.java:662`). Post-signup email confirmation would need an SMTP provider (none configured — grep `smtp|mail` = zero senders `[verified]`).
- **A4 Account deletion exists and is thorough on the DB** `[verified]`: `DELETE /api/users/{id}` (`RestApiServer.java:704-717`) → `ProfileMutationUseCases.deleteAccount` → single-txn cleanup (`JdbiAccountCleanupStorage.java:26-76`): hard-deletes photos rows/interests/lifestyle/stats/achievements/picks/sessions/standouts/friend-requests/notifications/undo/credentials, revokes **all** refresh tokens (`:236-244`), soft-deletes likes/matches/conversations/messages/blocks/reports/notes, sets state `BANNED` + `deleted_at`, **nulls email+phone → reuse allowed** (`:78-93`). Gaps: photo **files** not deleted (§E5); messages only soft-deleted (not anonymized, but hidden); **no web path** (Google Play requires one) — the web path needs an email-sent tokenized link, i.e. it inherits the A2 email gap.
- **A5** covered above; "log out everywhere" = revoke-all refresh tokens for user — trivially addable (the SQL already exists in cleanup, `:236-244`).
- **A6 States** `[verified]`: `UserState{INCOMPLETE,ACTIVE,PAUSED,BANNED}` (`User.java:74-79`). Enforcement: BANNED blocked from login/refresh (`AuthUseCases.java:73-75,93-96`), browse (`CandidateFinder.java:240-246`), swipe (`MatchingService.java:333-351`), profile read (409 "Target user is not visible", `RestApiServer.java:1610-1612`), messaging (`RelationshipWorkflowPolicy.java:99-110`). **PAUSED is unreachable from REST** (no route calls `User.pause()` — grep PAUSED in `app/` = zero `[verified]`). A suspended user sees: hard failures (401 on auth refresh → client logout; 409 on browse). There is no "suspended, contact support" surface — client must map 401-after-good-login / 409s to a generic restricted screen; a dedicated state in `/api/auth/me` would be better (P1).
- **A7** Pause/hide, incognito, travel mode: **absent** (`[verified]` — only the dormant PAUSED state).
- **A8 Age gate** `[verified]`: signup requires DOB ≥ `config.validation().minAge()` = **18** (`AuthUseCases.java:195-204`, `AppConfig.java:330`); profile-edit birthDate revalidated (`ValidationService.java:307-316`); discovery minAge floors at system min (`User.java:658-660`). Weaknesses: DOB is **fully editable** post-signup (B9) — must lock; UNDERAGE report reason exists (`ConnectionModels.java:392-399`) but no auto-action flow.
- **A9 Rate limits**: single global fixed window **240 req/min per IP+method** for all `/api/` incl. auth (`RestApiRequestGuards.java:93-103`, constants `RestApiServer.java:180-181`) → login brute force is throttled only ~240/min/IP. No per-account lockout, no CAPTCHA. P1 to add auth-specific limits.
- **A10 `GET /api/users`**: dev convenience, returns every user as `UserSummary` **with no auth and no state filter** (`RestApiServer.java:543-553`; guard exemption `RestApiRequestGuards.java:135-144`). Ship-blocker: replace with `GET /api/users/me` semantics + remove the route or gate it to an admin role. Nothing needs it (client uses it only for the dev-user picker, FE `api_client.dart:133`).
- **A11 Consent/ToS/privacy acceptance: nothing stored** `[verified]` (grep = zero). Needs `user_consents(user_id,document,version,accepted_at)`.
- **A12 Data export/erasure propagation**: no export; deletion propagation is good (above). Export = P2 (a `GET /api/users/{id}/data-export` JSON dump suffices at this scale).

### B. Onboarding and profile

- **B1** There is **no stored enum**. Stored: `users.state` (4 values). Returned: `AuthUser.profileCompletionState` = `"complete"` or `"needs_<firstMissingKey>"`, computed per call (`AuthUseCases.java:247-263`) — note this yields values like `needs_pacePreferences` / `needs_photoUrls`, **not** the `needs_photo/needs_name` set the April doc promised (`2026-04-30-…:299-304` — doc-code mismatch). Separately every profile-bearing response returns `ProfileCompletionView{missingProfileFields,missingProfileFieldLabels,requiredProfileFieldCount,profileComplete,canActivate,canBrowse}` (`ProfileCompletionView.java:9-15`). Enforcement: browse/swipe require ACTIVE (`MatchingUseCases.java:244-245,275-280`); activation requires completeness (`ProfileActivationPolicy.java:16-36`); incomplete users are invisible to others (`CandidateFinder.java:240-246`) and un-messageable. But an incomplete user **can** browse and like (only ACTIVE is checked, not completion) — matches the alpha doc's "unless the product explicitly allows it" ambiguity. Recommend: keep browse-allowed, document it. `complete` is computed, never stored `[verified]`.
- **B2** Keys are exactly: `name, bio, birthDate, gender, interestedIn, location, photoUrls, pacePreferences` (8) with labels `Name, Bio, Birth Date, Gender, Interested In, Location, Photo, Pace Preferences` (`User.java:37-57`). Client maps each to a screen; `dob` non-actionable client-side already (FE `profile_completion_info.dart:58-63`). `photoUrls` key is awkward — label is `Photo`; keep as-is for compat, note in client mapping.
- **B3 Symmetry gaps** (GET `UserDetail` `RestApiUserDtos.java:91-109` vs PUT `ProfileUpdateRequest` `ProfileDtos.java:25-50` vs snapshot `ProfileDtos.java:122-197`): missing from GET: `birthDate` (only derived `age`), `minAge/maxAge`, raw lat/lon (only `approximateLocation` label), `heightCm`, `smoking/drinking/wantsKids/lookingFor/education`, `interests`, all 8 `dealbreakers` sub-fields. Own-profile editing works because the snapshot returns them (`ProfileDtos.java:122-152`). For other-user views this is partly intentional privacy, but the client has **no way to render** lifestyle/interests/dealbreakers on another user's profile screen — propose a `presentation` section (P1, §5).
- **B4 Name**: no uniqueness, no placeholder leak risk; `"New User"` placeholder counts as missing (`User.java:31,41-43`), auth returns `displayName:null` for it (`AuthUseCases.java:249-253`). Email appears **only** in own-session `AuthUserDto` and verification response contact — never in `UserDetail`/`UserSummary`/snapshot `[verified]`. Safe.
- **B5** `Gender{MALE,FEMALE,OTHER}`; `interestedIn` = `Set<Gender>`, "everyone" = all three stored (`User.java:35-36,421-423`). Matching requires **mutual** gender+age preference satisfaction with both-ways checks (`CandidateFinder.java:340-376`). Non-binary = `OTHER`, supported. No gender-history audit.
- **B6** Richness present: bio, heightCm, smoking/drinking/wantsKids/lookingFor/education, 39 interests (max 10), 8-field dealbreakers, 4 pace preferences. Absent: prompts/answers, voice, video, captions, religion/politics/languages/diet/occupation `[verified]`.
- **B7** Completion needs **≥1 real photo** (no minimum beyond 1; `MAX_PHOTOS=6`), and photo endpoints **do** return updated completion flags (`PhotoDtos.java:20-52`) — client already parses this.
- **B8 Verification is simulated** `[verified]`: `start` generates a 6-digit code (15-min TTL) and **returns it in the response as `devVerificationCode`** (`VerificationDtos.java:15-24`); confirm checks equality; nothing is sent externally (`User.java:85-88` "Currently simulated"). `verified` today means "echoed a code the API itself handed you" — must not be shown as a trust badge in a store build (alpha doc P7 agrees, `:410-423`). Trustworthy path in §4 (P1-6).
- **B9** DOB/gender/name all freely editable, no cooldowns/audit (`ProfileMutationUseCases.java:306-331`). DOB lock is a compliance need (J3).

### C. Discovery and matching

- **C1 Browse**: seeker must be ACTIVE + have location, else `409`/`locationMissing=true` (`MatchingUseCases.java:244-254`). Filters verbatim: not-self, ACTIVE, not-already-interacted, not-blocked-either-direction, not-in-168h-unmatch-cooldown, mutual gender prefs, mutual age prefs, seeker's maxDistanceKm, dealbreakers; sorted by distance then deterministic rank (`CandidateFinder.java:147-158`, `BrowseRankingService.java:15-23`). **Candidate DTO includes photos/summary/location** (`UserSummary` = id,name,age,state,primaryPhotoUrl,photoUrls,approximateLocation,summaryLine — `RestApiUserDtos.java:51-59`; summaryLine = bio or top-3 interests). No pagination — **whole queue in one response**; results stable (deterministic ranking); acting removes a candidate (like/pass writes excluded the next call). Completion/verification are **not** candidate filters.
- **C2 Daily pick**: lazy per-day seeded random pick, persisted (`DailyPickService.java:50-89`); **it is a full User, actionable via the normal `POST /like/{targetId}`** — but `markDailyPickViewed` is never wired to the REST like/pass routes (`RestApiServer.java:976-1030` never sets it; use-case only runs via `processSwipe`), so `alreadySeen`/`dailyPickViewed` stays false for REST users. Client renders it non-interactive (FE `browse_screen.dart:926-938`). Fix: wire mark-viewed into `likeUser/passUser` (P1-1).
- **C3 `locationMissing`**: true when seeker has no stored location → empty candidates; client shows the set-location banner (`FE browse_screen.dart:564`) — correct semantics, keep.
- **C4 Like/pass**: quotas 100 likes / 1 super-like / unlimited passes per **userTimeZone-midnight** day (`AppConfig.java:313-315`, `DailyLimitService.java:37-108`), enforced with 409 `Daily like limit reached` etc. (`RestApiDailyLimitTest.java:42-95`). Re-like is idempotent (duplicate → 200 "Already swiped.", no quota consumption). **Undo window 30s** but **REST likes never record undo state** (`MatchingService.java:151-165` vs `:307`) → `POST /undo` after any REST like = 409 "No recent swipe to undo" `[verified by absence, inferred]`. The undo feature works only for the desktop `processSwipe` path. **P0-2 fix**: record undo state in `recordLikeWithinLock` or route REST through `processSwipe`.
- **C5 Match lifecycle**: mutual like → deterministic `uuidA_uuidB` match, `ACTIVE`; rematch after unmatch reactivates the same row after a **168h cooldown** (`JdbiMatchmakingStorage.java:306-345`, `CandidateFinder.java:382-394`). No "new match" concept, **no expiry/first-message deadline** (matches never expire). Archive = unmatch with reason `UNMATCH` (204). Unmatch archives the conversation for **both** sides and soft-deletes both likes; graceful-exit archives both sides with `GRACEFUL_EXIT`, keeps likes (no rematch via re-like — duplicate path returns likeOnly). Client "NEW MATCH = <24h" (`FE matches_screen.dart:931-934`) is invented; propose server `isNewMatch`.
- **C6 Pending likers**: full cards (name, age, likedAt, photos, location, summary) — no masking; like-back via the normal like endpoint creates the match. Works `[verified]`.
- **C7 Standouts**: daily per-seeker cached list, diversity window 3 days, composite score ≥40, max 10, rank/score/reason fields (`StandoutService.java:75-133`); liking is **not special**; no super-like REST route. `reason` is a generated sentence ("backend rank suggests…") — client rewrites it by string-matching (`FE standouts_screen.dart:763-769`). Propose `reasonCode` (§5).
- **C8 Match quality is real** `[verified]`: distance/age/interests/lifestyle/pace/response-time composite → 0-100 score, label, stars, highlights ≤5 (`MatchQualityService.java:214-243,258-323`). Dead on the client only because nothing opens it.
- **C9 Badge sources**: unread chats = sum over conversations (server computes `totalUnreadCount` but **discards it** in the REST mapping, `RestApiServer.java:1676-1687`); new matches = none server-side (client's 24h rule); pending likes = no count endpoint; notifications unread = `?unreadOnly=true` list length. Proposals in §5.
- **C10 Blocking**: symmetric (`isBlocked` either-direction) — excluded from browse, swipe (409), pending likers, profile read (403), matches (state BLOCKED). Chat blocked via match-state only, not a direct block check (`ConnectionService.java:72-87` — equivalent outcome). Notifications are **not** block-filtered (stale NEW_MESSAGE rows can exist — minor). Unblock does not restore the match/visibility (terminal states).

### D. Chat and conversations

- **D1 DTO**: `ConversationSummary(id, otherUserId, otherUserName, messageCount, lastMessageAt)` — 5 fields (`MessageDtos.java:12-13`). Ordering: `COALESCE(last_message_at, created_at) DESC, id DESC` (`JdbiConnectionStorage.java:86-112`); `?limit&offset` (default 50/0, max 100, 400 outside range). **No unread count, no preview, no last sender, no photo** — though the internal `ConversationPreview` already computes `unreadCount` + `lastMessage` and the server throws both away (`ConnectionService.java:712-713`, `RestApiServer.java:1676-1687`). This is the cheapest high-value enrichment in the codebase (§5, P0-5).
- **D2 Unread clearing: `GET messages` DOES mark read server-side** — `LoadConversationQuery(..., markAsRead=true)` → `markAsRead` best-effort (`RestApiServer.java:1450-1451`, `MessagingUseCases.java:91-115`, `ConnectionService.java:282-293`: sets `user_{a,b}_last_read_at = now()`). The client correctly assumes this (FE `conversation_thread_screen.dart:64-75`). An explicit `POST …/read` endpoint should still be added — the use case already exists (`MessagingUseCases.java:172-182`) and is CLI-only (P1-2).
- **D3 Messages: oldest-first + offset** (`ORDER BY created_at ASC, id ASC LIMIT/OFFSET` — `JdbiConnectionStorage.java:551-563`). Client's "newest-first + offset = older pages" mental model is **wrong**; for a thread UI the client today just takes the first 50 (the **oldest** 50!). No total count. This is a live pagination-semantics trap for the Flutter thread (P0-5: add `order=desc` or newest-first windowing).
- **D4 Send**: max 1000 chars, OWASP-sanitized (`SanitizerUtils.sanitizeMessage` keeps b/i/em/strong/u), blank rejected; `senderId` in body **must equal token subject** (403 otherwise, `RestApiServer.java:1480-1487`); recipient derived from the `uuidA_uuidB` conversation id, non-participant → 403. No per-message rate limit (only global 240/min), no idempotency key.
- **D5 Read receipts: none** (no delivered/seen concept). Proposal §4 (P2).
- **D6 Typing/presence: none server-side** (desktop simulates presence locally, `UiDataAdapters.java:120-146`). Client already stubs `false` with a TODO (FE `conversation_thread_screen.dart:54-55`).
- **D7 Attachments: none** (messages are text-only, `ConnectionModels.java:24-26`).
- **D8** Edit: none. Delete: soft-delete by **sender only** (`ConnectionService.java:414-431`), reads filter `deleted_at IS NULL` — no tombstone. Report-a-message: absent. Pin/mute: absent. Per-user conversation archive exists (`POST /api/users/{id}/conversations/{conversationId}/archive`, reason defaults `UNMATCH`).
- **D9** Conversations are persisted rows with deterministic ids (`smallerUUID_largerUUID`, `ConnectionModels.java:180-193`); created lazily on first message; **match-only** (no active match → 409 "Cannot message: no active match", `RelationshipWorkflowPolicy.java:99-110`). No first-message deadline.
- **D10 Unmatch**: archives both sides; **history remains readable** on the existing conversation (visibility untouched) but sends fail. Block: blocker's side hidden (`setVisibility(blocker,false)`), victim still sees history; sends fail 409. `DELETE conversation` soft-deletes for both users.
- **D11 Polling-only** (desktop polls at 15s/5s). `InProcessAppEventBus` is synchronous, in-process, no outbox — push later needs its own delivery path (§4 P1-9).
- **D12 Failure semantics**: documented map — 400 (validation/bad UUID), 401, 403 (identity/participant), 409 (`Sender/Recipient not found or inactive`, `Cannot message: no active match`, `Message too long (max 1000 characters)`), 429 (global). Client should render 409 messages verbatim.

### E. Photos and media

- **E1**: multipart field `photo`; JPEG/PNG only by **declared** content type (no sniffing); max 10 MiB config (5 MiB code default); **no dimension checks**; **no variants — raw bytes stored**; layout `data/photos/<userId>/<photoId>.<ext>`; served `GET /photos/{userId}/{photoId}.{ext}` **with zero auth** (guard skips non-`/api/` paths, `RestApiRequestGuards.java:57-61`); **no cache headers**; URL contains user UUID + random UUID (security-by-obscurity, no signed URLs) `[verified]`.
- **E2 EXIF: nothing is stripped server-side** — bytes written verbatim (`RestApiPhotoStorage.java:36-52`; round-trip test asserts byte equality, `RestApiPhotoRoutesTest.java:100-103`). EXIF/orientation code exists **only in the desktop UI** (`ui/LocalPhotoStore.java:160-171,269-288`), which the REST path never calls. GPS EXIF from a phone upload would be preserved and publicly served — **P0** (J4/J5).
- **E3 Moderation: no photo pipeline at all** — no pending/rejected states, no NSFW/face detection, uploads immediately live. (Client already parses `moderationStatus/rejectionReason` that the backend never sends — harmless today, ready for later.)
- **E4** Primary = first real photo; deleting primary implicitly promotes the next; max 6 (`User.java:30,858-862`); reorder requires an exact permutation of existing ids (400 otherwise).
- **E5** Single-photo delete removes managed files; **account delete removes DB rows but orphans all files** under `data/photos/<userId>/` (`JdbiAccountCleanupStorage.java:151-153` has no filesystem access) — also a J1/GDPR issue.
- **E6** No duplicate/abuse detection (no perceptual hashing).
- **E7** No selfie/pose verification — verification is a simulated contact-code flow (B8).

### F. Safety and moderation

- **F1 Block**: `blocks` table, soft-delete, bidirectional enforcement across browse/swipe/pending-likers/profile-read/matches; chat via match state; notifications NOT filtered; unblock does not restore anything. `[verified]`
- **F2 Report**: request `{reason,description?,blockUser}`; taxonomy `SPAM, INAPPROPRIATE_CONTENT, HARASSMENT, FAKE_PROFILE, UNDERAGE, OTHER` (`ConnectionModels.java:392-399`); persisted in `reports` (unique per reporter/reported, soft-revivable); **auto-ban at `autoBanThreshold` = 3 reports** (`TrustSafetyService.java:320-396`, `AppConfig.java:387`); response `ReportResponse{success,autoBanned,errorMessage,blockedByReporter}`. **Moderation queue/console: none** (no admin role, no endpoint, no UI). Reporter sees success + flags only.
- **F3**: global 240/min/IP rate limit; daily interaction limits; session swipe-velocity gate (`ActivityMetricsService.java:108-144`); **no message scanning, no link/doxx filtering, no behavioral scoring** (`[verified]` — only sanitizer + length).
- **F4** Ban evasion / duplicate accounts: **nothing** (no device fingerprinting, no IP linkage; email unique only).
- **F5 Appeals: none.** Banned is terminal; no re-activation path (`ProfileActivationPolicy.java:20-22`).
- **F6** Audit trail exists as **structured logs** (`ModerationAuditLogger`, retention 30d, PII-redacted) — no DB table, no staff console.
- **F7** Client should add: report-with-reason sheet (needs the fixed contract), block/report from chat thread, and a blocked-list screen — current DTO is `BlockedUserDto(userId,name,statusLabel:"Blocked profile")`, no blockedAt/reason/photo (`SocialDtos.java:51-68`); enrichment proposed §5.

### G. Notifications

- **G1 Taxonomy — exactly 5 types** `[verified]` (`ConnectionModels.java:458-497`): `MATCH_FOUND` (both users, data `{matchId,conversationId,otherUserId}`), `NEW_MESSAGE` (recipient, `{matchId,conversationId,senderId,messageId}`), `FRIEND_REQUEST` (`{requestId,fromUserId,matchId}`), `FRIEND_REQUEST_ACCEPTED` (two paths — note the transition-event variant writes `requestId=<pairId>` not the real request id, a data bug worth fixing), `GRACEFUL_EXIT` (`{matchId,conversationId,initiatorId}`). Deep-link targets exist for all four actionable types — the client's registry matches (`FE notification_item.dart:106-135`). Unread count = filtered list length; no limit/offset on the list (unbounded); ordering `created_at DESC`; `deleteOldNotifications` exists but has **no scheduled caller** in production paths.
- **G2 Preferences: none server-side** (device-local only in client, per out-of-scope doc §9.5). Proposal §4 (P1-9/P1-13).
- **G3 Push: zero code** (no FCM/APNs/token table/endpoint — grep-verified). Registration contract proposed §4 (P1-9).
- **G4 Email: nothing** (no SMTP client; verification code returned in-band instead).
- **G5 Missing triggers**: like-received, verification result, safety/ban notices, legal, match-expiry (matches don't expire), digests, friend-request-declined/unmatch acknowledgements. List in §4 (P1/P2).

### H. Engagement, stats, monetization

- **H1** Stats are honestly server-computed from DB counts, cached 24h (`ActivityMetricsService.java:234-291`); 11 achievements with config thresholds (`EngagementDomain.java:21-35`); `AchievementSnapshotDto` gives unlocked+newlyUnlocked but **no numeric progress** (client parses `"3/5"`-style strings that the backend never even sends — the progress bars are pure fiction, FE `stats_screen.dart:830-840` hash-fakes activity patterns). Fix = add `progressCurrent/progressTarget` (§5, P1-10).
- **H2** Quotas exist (100/1/∞, config+env overridable); **no super-like REST route** (desktop-only via `processSwipe`), no boosts/roses/rewind.
- **H3** No subscriptions/entitlements/receipt validation anywhere. If monetization ships: needs orders/entitlements tables + Play/App Store receipt endpoints — declared "no monetization at launch" (§7 J6).
- **H4** No re-engagement mechanics (no digests/nudges; cleanup scheduler only purges).

### I. API foundations

- **I1** Add-endpoint recipe (verified): register in `RestApiServer.registerXRoutes()` (`:331-431`); guards are automatic via `beforeMatched` (`RestApiRequestGuards.java:57-68`) — exempt only by editing `requiresActingUserIdentity()` (`:112-133`) or the LAN-secret/health bypasses; DTO record in a `*Dtos.java` file; usecase `*Command(UserContext.api(userId),…)`; failure mapping via `handleUseCaseFailure` (`:1778-1809`); tests via `RestApiTestFixture` + `bearerToken()` helper.
- **I2** Custom versioned migration runner V1–V19, append-only, transactional, dialect-branching H2/Postgres (`MigrationRunner.java:120-279`); fresh DBs get the V1 baseline from `SchemaInitializer`. New tables: `createXSchema` + `applyVN`. **Repo-root `scripts/postgresql-public-schema-snapshot.sql` is stale** (predates V19 — missing `auth_refresh_tokens`/`user_credentials`). 28+ tables.
- **I3** No `/v1`/version headers; the only compat device is the `/candidates` → `/browse` deprecation (`:1761-1764`). Installed-client policy must be defined (§8 decision D-1).
- **I4** `limit/offset` on matches (default 20), conversations (50), messages (50), cities (`limit` 10); wrapped `PagedMatchResponse{matches,totalCount,offset,limit,hasMore}` exists **only for matches**; conversations/messages return bare lists with no totals; everything else unpaginated (notifications unbounded!). No cursors.
- **I5** No `Idempotency-Key` support; natural idempotency from DB uniques (likes, matches, reports, blocks, refresh-hash); **messages have no dedupe**.
- **I6** 240 req/min/IP+method, exempt health/OPTIONS; 429 body `{"code":"TOO_MANY_REQUESTS","message":…}` + `Retry-After`, `X-RateLimit-Limit/Used` (`RestApiServer.java:1851-1858`). **Client has no 429/403 handling at all** (FE `api_error.dart:10-29` — only 401 and one 409).
- **I7** No feature flags/remote config/kill switch/forced version/maintenance mode. (Store release minimum: a maintenance-mode flag + min-app-version in `/api/health` — cheap, P1-16.)
- **I8** `/api/health` = constant `ok` + `System.currentTimeMillis()` — checks nothing, not even the DB (`:351`). Deeper status endpoint absent.
- **I9** Coverage: auth/REST-guards/photos/social/notes/verification/daily-limits well covered; matching/messaging/profile good; **no tests for**: password reset (missing feature), push (missing), payments (missing), moderation console (missing), and **undo-via-REST-like is untested** (would have caught P0-2).
- **I10** `core/` is JavaFX-free (grep clean) — mobile-first changes layer cleanly: REST adapter → usecases → core → JDBI storage `[verified]`.
- **I11** Config: `config/app-config.json` → env overrides (`DATING_APP_*`) → validation; production JWT-secret placeholder guard only fires when `DATING_APP_ENV=production` (`ApplicationStartup.java:223-236`). Historical release blockers noted at audit time: JWT placeholder, shared-secret configuration, localhost-only default binding (LAN requires `--host=0.0.0.0` + a secret; HTTPS must come from tunnel/proxy), empty `photoPublicBaseUrl` (relative URLs), and local DB credentials. Re-check current client/config behavior before relying on this dated finding.

---

## 4. Gap register

Format: **P0** = blocks store release or breaks a core journey. Effort: S ≤1d, M ≤3d, L ≤1.5w.

### P0

| # | Gap | Evidence | Fix | Effort |
|---|---|---|---|---|
| P0-1 | **Report contract break**: client sends no body → 400 on every report; backend requires `reason` | FE `api_client.dart:338-341`; `SocialDtos.java:74`; `RestApiServer.java:1316-1336` | Client change: send `{"reason":"…","description":"","blockUser":false}` from a reason-sheet UI (enum in §F2). Optionally make `reason` default `OTHER` server-side if description present | S |
| P0-2 | **Undo dead for REST likes** (no undo-state recording) | `MatchingService.java:151-165` vs `:307`; `UndoService.java:117-141` | Record undo state in `recordLikeWithinLock` (same 30s window), or have `likeUser`/`passUser` route through `processSwipe`. Add test | M |
| P0-3 | **Unauthenticated user enumeration/profile read**: `GET /api/users`, `GET /api/users/{id}` anonymous | `RestApiRequestGuards.java:135-144`; `RestApiServer.java:1592-1595,543-553` | Delete `/api/users` (or localhost-dev-only); require bearer + `id==subject-or-mutual-block-check` for `GET /api/users/{id}` (self-read bypass already exists, `:1593`). Add tests | S |
| P0-4 | **Photo privacy**: EXIF/GPS preserved; `/photos/*` unauthenticated public | `RestApiPhotoStorage.java:36-52`; `RestApiServer.java:434-468`; `RestApiRequestGuards.java:57-61` | Strip EXIF + re-encode on upload (reuse `metadata-extractor` + ImageIO; mirror `ui/LocalPhotoStore.java` normalization), or at minimum strip JPEG EXIF GPS. Serving: keep public short-term (URLs unguessable) but add `Cache-Control: private,max-age=86400`; plan signed URLs for post-launch | M |
| P0-5 | **Chat/matches render from invented data** (comps 1,2,8,9): conversation DTO lacks unread/preview/sender/photo; message pagination oldest-first; no totals | `MessageDtos.java:12-13`; `RestApiServer.java:1676-1687`; `JdbiConnectionStorage.java:551-563` | Enrich `ConversationSummary` with `unreadCount,lastMessagePreview,lastSenderId,otherUserPhotoUrl` + wrap list in `{conversations,totalCount,limit,offset,totalUnreadCount}` (data already computed); add `order=desc` default for messages + `totalCount`. Client drops fallbacks | M |
| P0-6 | **Google Play deletion**: no web deletion path; photo files orphaned on delete | `JdbiAccountCleanupStorage.java:151-153`; missing web routes | (a) delete `data/photos/<userId>/` recursively in `softDeleteAccount` path (inject `RestApiPhotoStorage`); (b) host `https://<domain>/account-deletion` web page → `POST /api/web/account/delete {email}` → email tokenized link → `POST /api/web/account/delete/confirm {token}` (depends on email delivery, P1-7) | L |
| P0-7 | **Consent/privacy records**: nothing stored; no retention policy surfaces | grep zero; `ModerationAuditLogger` 30d only | `user_consents` table + accept on signup (`acceptedTermsVersion` in signup request, optional field), GET in `/api/auth/me`; privacy-policy URL hosted by product (client-side) | S |
| P0-8 | **Release config**: JWT secret + shared secret defaults; localhost binding; health checks nothing | `AppConfig.java:27`; `FE env.dart:4-12`; `RestApiServer.java:163-164,351` | Ops checklist: set `DATING_APP_ENV=production` + `DATING_APP_AUTH_JWT_SECRET` + non-default `DATING_APP_REST_SHARED_SECRET`, bind LAN host, HTTPS tunnel. Extend `/api/health` with `{"database":"ok|degraded"}` check (optional field, non-breaking) | S |

### P1 (product credibility)

| # | Gap | Fix (endpoint + JSON) | Effort |
|---|---|---|---|
| P1-1 | Daily pick not marked viewed via REST; not deep-linkable | Wire `markDailyPickViewed` into `likeUser/passUser`; add `POST /api/users/{id}/daily-pick/viewed` → `{dailyPickViewed:true}` (use case exists, `DashboardUseCases.java:92-99`) | S |
| P1-2 | Explicit chat mark-read | `POST /api/users/{id}/conversations/{conversationId}/read` → 204 (use case exists, `MessagingUseCases.java:172-182`) | S |
| P1-3 | Quota visibility (client quota UI needs data) | `GET /api/users/{id}/daily-status` → `{likesUsed,likesRemaining,superLikesUsed,superLikesRemaining,passesUsed,passesRemaining,date,resetsAt}` (record exists, `DailyLimitService.java:110-118`) | S |
| P1-4 | New-match / badge authority | Add `isNewMatch` (server 24h rule) + `lastMessageAt`,`unreadCount` to `MatchSummary`; add `pendingLikesCount` to browse response or a `GET …/badges` returning `{unreadMessages,pendingLikes,unreadNotifications,activeMatches}` (`DashboardUseCases.UnreadSummary` already computes, `:82-85` — expose it) | M |
| P1-5 | Blocked-list enrichment | Add `blockedAt` (+`primaryPhotoUrl`) to `BlockedUserDto` (join `blocks.created_at`) | S |
| P1-6 | Honest verification | Keep dev-code behind `DATING_APP_ENV!=production` guard; production = real email send (P1-7) or hide verification entirely; add `verificationStatus:PENDING|VERIFIED|EXPIRED` to `/api/auth/me` | M |
| P1-7 | Email delivery (SMTP provider) | Prereq for: password reset, web deletion, verification. One `EmailSender` interface + provider; templates: reset, deletion-confirm, verification | M |
| P1-8 | Password reset | `POST /api/auth/password-reset/start {email}` → always 202 `{"message":"If the account exists, a reset link was sent."}`; `POST /api/auth/password-reset/confirm {token,newPassword}` → 204/400; table `password_reset_tokens(token_hash,user_id,expires_at,used_at)`, 60-min TTL, single-use | M |
| P1-9 | Push notifications | `POST /api/users/{id}/push-tokens {platform:"ANDROID"|"IOS", token, deviceId?}` → 201; `DELETE /api/users/{id}/push-tokens/{tokenId}`; `device_tokens` table; fan-out worker on `AppEvent` (bus already emits the events; outbox needed for at-least-once) | L |
| P1-10 | Honest stats/achievements | Add `progressCurrent`,`progressTarget` to `AchievementUnlockedDto`+catalog; stats fine as-is | M |
| P1-11 | Standout/pick trust | Add `reasonCode` enum to `StandoutDto` (`DISTANCE,AGE_FIT,SHARED_INTERESTS,LIFESTYLE_FIT,COMPLETENESS,ACTIVITY,COMPOSITE`) | S |
| P1-12 | Other-user profile depth | `presentation-context` or `UserDetail` gains `interests`, `lifestyle{smoking,drinking,wantsKids,lookingFor,education}`, `heightCm` (already computed server-side, `ProfileCompletionSupport`) | M |
| P1-13 | Notification list pagination + unread envelope | `GET …/notifications?unreadOnly&limit&offset` → `{notifications,totalCount,unreadCount,limit,offset}` (breaking-ish: keep bare-list fallback via content negotiation or version bump — see I3 decision) | S |
| P1-14 | Suspension surface | `/api/auth/me` gains `accountStatus:"ACTIVE"|"SUSPENDED"|"BANNED"|"DELETED"` distinct from `profileCompletionState` | S |
| P1-15 | Auth hardening | Per-account login throttle (e.g. 10/min) + lockout counter; refresh-replay family revocation | M |
| P1-16 | Remote config floor | `/api/health` extended: `{"maintenance":false,"minClientVersion":"1.0.0"}` or a `GET /api/config` | S |

### P2 (scale/polish)

Read receipts (D5 contract: `PUT /api/conversations/{id}/messages/{messageId}/seen` + per-message `seenAt`), typing/presence (polling contract: `GET /api/users/{id}/presence/{targetId}` → `{online,lastSeenAt}` with user opt-out), attachments (multipart → message `attachmentId`), moderation console + appeals, data export, cursor pagination, `Idempotency-Key`, per-endpoint rate limits, ETags/cache on lists, message search, multi-device session list + logout-all, photo thumbnail variants, ban-evasion device fingerprinting, notification digest/bundling, quiet hours.

---

## 5. DTO enrichment proposals (exact fields)

Keep all existing fields; **additions only**, names chosen to match client fallback keys where they exist (so the client deletes fallbacks, not models):

1. `GET /api/users/{id}/conversations` → envelope + enriched rows:

```json
{"conversations":[{"id":"a_b","otherUserId":"…","otherUserName":"…","otherUserPhotoUrl":"…|null","messageCount":3,"lastMessageAt":"ISO","lastMessagePreview":"…|null","lastSenderId":"…|null","unreadCount":0}],"totalCount":12,"limit":50,"offset":0,"totalUnreadCount":2}
```

(existing `otherUserName`,`messageCount`,`lastMessageAt` unchanged → client fallback chains retire safely)

2. `GET /api/conversations/{id}/messages` → `{"messages":[…],"totalCount":n,"limit":50,"offset":0,"oldestFirst":true}` (or `order=desc` param; client switch documented).
3. `MatchSummary` + `isNewMatch:boolean` (server rule: `createdAt` < 24h and `unreadCount>0 || messageCount==0` — server-defined, D-3), `unreadCount:int`, `lastMessageAt:Instant?`; `PagedMatchResponse` unchanged.
4. `UserSummary` (browse/pending likers) + `verified:boolean`, `bio:string|null` (today only `summaryLine`), `distanceKm:double|null`. `DailyPickDto` inherits via the same mapper.
5. `StandoutDto` + `reasonCode:string` (enum above).
6. `LikeResponse.match` unchanged; add top-level `dailyStatus:{likesUsed,likesRemaining,superLikesUsed,superLikesRemaining,resetsAt}`.
7. `BlockedUserDto` + `blockedAt:Instant`, `primaryPhotoUrl:string|null`.
8. `GET …/notifications` → envelope `{notifications:[…],unreadCount,totalCount,limit,offset}`; `NotificationDto.data` values must stay strings (client stringifies) — keep.
9. `AchievementUnlockedDto` + `progressCurrent:int|null`,`progressTarget:int|null`; response gains `catalogVersion:int`.
10. `AuthUserDto` + `accountStatus`, `verified:boolean`, `state`.
11. `PhotoDto` (server should send what client already parses): `thumbnailUrl`,`mediumUrl`,`moderationStatus`,`rejectionReason`,`primary:boolean`,`sortIndex:int`,`createdAt:Instant`.
12. `UserDetail` own-view additions (or via snapshot): `interests`,`lifestyle{…}` per P1-12; `birthDate` stays snapshot-only.
13. `ReportUserRequest` + optional `messageId` (report-a-message later); `ReportResponse` unchanged.
14. `SignupRequest` + optional `acceptedTermsVersion:string` (P0-7); `AuthResponse` unchanged shape.

---

## 6. Foundations and extendibility map

- Layers: `app/api/RestApiServer*` (transport, DTO records, guards) → `app/usecase/*` (commands/results, `UseCaseError{VALIDATION,NOT_FOUND,UNAUTHORIZED,FORBIDDEN,CONFLICT,DEPENDENCY,INTERNAL}`) → `core/*` (framework-free domain: `matching`, `connection`, `profile`, `metrics`, `workflow` policies, `model`) → `storage/jdbi/*` (dual-dialect SQL). Events: `app/event` (in-process, best-effort handlers) — add new notification types by publishing a new `AppEvent` + handler subscription.
- Where to add things: routes `RestApiServer.java:339-431` (grouped by `registerXRoutes`); DTO files per domain in `app/api/`; schema `SchemaInitializer.createAllTables` + new `applyVN` in `MigrationRunner` (append-only, both dialects, `IF NOT EXISTS` + index fallbacks); tests: `RestApiTestFixture.builder(...)` + `bearerToken(services,user)` pattern.
- Risk areas for mobile-first changes: (1) bare-list vs wrapped-envelope breaking changes — pick an envelope policy before enriching lists (D-1); (2) conversation-id format `uuid_uuid` is load-bearing in the identity policy (`RestApiIdentityPolicy.java:119-133`) — don't migrate to table ids casually; (3) `X-User-Id` legacy path (`resolveLegacyHeader`) must stay only for localhost tests — assert production server always constructed with `authUseCases` (it is, `RestApiServer.java:251`); (4) stale `scripts/postgresql-public-schema-snapshot.sql`; (5) `docs/api/API-SPECIFICATION.md` is 3-for-3 wrong on error shape, password minimum (says 8, code = 12), and scope — fix the doc, not the code.

---

## 7. Store-compliance checklist (J1–J7)

| Item | Verdict | Backend work required |
|---|---|---|
| **J1 In-app + web account deletion** | **Partial** | In-app `DELETE /api/users/{id}` works, revokes tokens, allows email reuse; missing: photo-file cleanup (P0-6a), web URL + tokenized email flow (P0-6b, needs P1-7) |
| **J2 UGC controls** | **Partial** | Block/report/unmatch enforced + auto-ban; missing: working report contract from client (P0-1), moderation review console, appeals, message-level reporting |
| **J3 Age assurance 18+** | **Partial** | DOB 18+ enforced at signup + age-pref floors; missing: DOB edit lock, underage-report action flow, (later) third-party age assurance |
| **J4 Data safety** | **Missing** | No privacy-policy/consent records (P0-7), no data export (P2), EXIF GPS leak (P0-4), orphaned files on delete (P0-6a), anonymous profile leak (P0-3) |
| **J5 Location precision/privacy** | **Good** | Raw coordinates never returned; only city/ZIP labels + `approximate` flag (`RestApiServer.java:929-934`); caveat: Nominatim external lookups send query terms (`NominatimGeocodingService.java:75-82`) — disclose in data-safety form; IL-only catalog must be widened or disclosed |
| **J6 Payments** | **N/A at launch** | No monetization code; declare "no purchases" in Play console (avoids billing policy scope) |
| **J7 Content rating** | **Ready to declare** | Dating/UGC + online interaction present; moderation = report + auto-ban (declare honestly); no sexual/violent content beyond user bios/messages |

---

## 8. Assumptions and decisions needed (recommended defaults included)

1. **Envelope policy for list responses** — *decision*: add wrapped envelopes to conversations/messages/notifications (breaking for old clients that parse bare lists). Recommended: accept, since the only installed client is Flutter and it must update for store anyway (no legacy users on stores yet).
2. **Browse access for incomplete profiles** — keep allowed (server checks only ACTIVE); route users to onboarding via `profileCompletionState`. *Default: keep.*
3. **"New match" definition** — server rule `createdAt < 24h && messageCount == 0`. *Default: 24h, matches client's existing behavior.*
4. **Undo window** — 30s today; keep 30s, but fix REST recording (P0-2).
5. **Rematch cooldown** — 168h. *Default: keep.*
6. **Match expiry / first-message deadline** — none today. *Recommended for launch: none (no expiry), revisit post-launch.*
7. **Presence/typing** — *Recommended: absent at launch*; client keeps its TODO stub.
8. **Verification** — *Recommended: non-blocking, hide `devVerificationCode` in production builds* (server: only include field when `DATING_APP_ENV != production`).
9. **Quotas** — accept 100/1/∞; expose via `daily-status` endpoint.
10. **Auto-ban threshold 3** — *Recommended: keep for launch; add manual review before scaling.*
11. **Versioning** — no `/v1`; adopt a documented "additive-only until v2" policy + `Deprecation` header pattern (already exists on `/candidates`).
12. **Incomplete-user browse visibility vs store policy** — browse gated on ACTIVE (not completeness) is fine; document as is.

---

## 9. Evidence log

**Files read directly (this session):** `pom.xml`; `.env.example`; `RestApiServer.java` (full 1,940 lines); `RestApiRequestGuards.java`; `RestApiIdentityPolicy.java`; `RestApiRequestContext.java`; `RestApiExceptions.java`; `AuthUseCases.java`; `AuthTokenService.java`; `JdbiAuthStorage.java`; `AuthStorage.java`; `JdbiAccountCleanupStorage.java`; repo-root `2026-04-30-phone-alpha-backend-api-requirements.md` (all 470 lines); directory listings (root, `docs/`, `docs/plans/`, frontend `lib/models/`); test inventory counts (208 files via `rg --files`).

**Subagent-audited (read-only explore agents, citations verified against the listed files):** frontend contract (all 32 models + `api_endpoints/api_client/api_headers/api_error/auth_token_holder` + providers/screens), chat (D1-D12), photos (E1-E7), safety+notifications (F,G), matching (C1-C10), profile+location+verification (B), stats+foundations (H,I incl. `SchemaInitializer`, `MigrationRunner`, `AppConfig`, config JSONs, `docs/api/API-SPECIFICATION.md`).

**Tests run:** none — audit executed in read-only mode. Deferred targeted set: `mvn -Dtest=RestApiAuthRoutesTest,RestApiIdentityPolicyTest,RestApiRequestGuardsTest,RestApiDailyLimitTest,RestApiPhotoRoutesTest,MatchingUseCasesTest,MessagingUseCasesTest,ProfileUseCasesTest test`. Full `mvn spotless:apply verify` not run.

**Known unverified / unknowns:**

- Whether JDBI upserts race-safely under concurrent first-message creation on H2 vs Postgres (transaction re-check exists, `ConnectionService.java:103-115` — needs a concurrency test).
- `loadExistingUser` behavior for soft-deleted users (whether `profileUseCases` filters `deleted_at`).
- Desktop `User.pause()` reachability beyond CLI/desktop (no REST route found; a CLI-only path may exist).
- Actual H2/Postgres parity of `MigrationRunner` V19 on a *pre-existing* pre-V19 database (fresh-DB path verified by code; in-place upgrade path not exercised here — `PostgresqlSchemaBootstrapSmokeTest` exists but was not run).
- Frontend widget-test health (explicitly out of scope per the feature-complete doc).

**Doc contradictions called out:** (1) `docs/plans/feature-complete-2026-07/` lives in the **frontend** repo, not backend; its OUT-OF-SCOPE list was reconciled in §3/§4 and remains accurate. (2) April doc promised `needs_photo/needs_name` states; backend computes `needs_<requiredFieldKey>`. (3) April doc promised EXIF strip + file deletion on account delete; neither exists. (4) `docs/api/API-SPECIFICATION.md` error shape/password-length/scope wrong (§6). (5) Backend `ConversationSummary` already computes but discards unread/preview (§D1).
