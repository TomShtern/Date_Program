# Phone-Alpha REST API Specification

> **Status (2026-10-01):** auth + photo sections verified against
> `RestApiServer`, `AuthUseCases`, `ClerkJwtVerifier`, `AppConfig`, and
> `config/app-config.json`. This spec still covers only the phone-alpha
> auth/photo surface — for the full route list (users, location,
> matching, social, messaging, notes) read the route registration in
> `RestApiServer` (`app.get/post/put/delete` under `/api/`), which is
> authoritative.
> **Scope:** This document covers the phone-alpha auth and photo endpoints that the Flutter frontend will consume.
> **Auth model:** Clerk. Clerk owns sign-up, sign-in, passwords and sessions.
> The backend only verifies Clerk session tokens (RS256 JWTs, checked offline
> against Clerk's JWKS) and maps the Clerk user to a local profile
> (`AuthUseCases` + `ClerkJwtVerifier`). It holds no Clerk secret key and
> issues no tokens of its own.

---

## Base URL

```
http://localhost:7070
```

All paths below are relative to this base.

---

## Authentication

Most endpoints require a **Bearer** token in the `Authorization` header. The
token is a Clerk **session token**, not something this backend issued:

```
Authorization: Bearer <Clerk session token>
```

### Client contract

1. Sign the user in with Clerk in the Flutter app.
2. Send a **fresh** session token on every request. Clerk session tokens are
   short-lived (about a minute), so ask the Clerk SDK for the current token per
   call instead of caching one. A stale token returns 401.
3. Call `POST /api/auth/session` once after sign-in. It creates the local
   profile on the first call and returns the local user `id`.
4. Use that `id` in route paths such as `/api/users/{id}/...`. The token's
   Clerk user must own the `{id}` in the path, otherwise the call returns 403.
5. Keep sending the `X-DatingApp-Shared-Secret` header when the server is bound
   to a non-loopback address. It is separate from the Clerk token.

### Token validation rules

- The signature must verify with RS256 against the issuer's JWKS. HS256 and
  unsigned (`alg: none`) tokens are rejected.
- `iss` must equal the configured `clerkIssuer`. `exp` and `nbf` are
  checked against `AppClock` with `clockSkewSeconds` of leeway. `sub`
  must be non-blank.
- `azp` is checked only when `clerkAuthorizedParties` is configured **and**
  the token carries an `azp` claim. A token without `azp` is accepted.
- The `email` claim is optional. It is echoed back in the session response and
  never stored.
- Deleted or banned users are rejected at **every** authenticated call. A
  banned user also cannot open a session.
- A valid token whose Clerk user has no live local profile gets `401` with code
  `NOT_PROVISIONED`. Call `POST /api/auth/session` to create one.
- If the JWKS endpoint cannot be reached the token is rejected with 401 (not
  503). The failure is logged at warn level.

---

## Error format

All errors return a JSON body (`RestApiDtos.ErrorResponse` — note the
`code` key, not `error`):

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable description"
}
```

Common status codes:

| Status | Meaning |
|--------|---------|
| 400    | Bad request (malformed JSON, missing field, invalid value) |
| 401    | Unauthorized (missing/invalid/expired token, deleted/banned user, or `NOT_PROVISIONED` when the Clerk user has no local profile yet) |
| 403    | Forbidden (mismatched user-scoped route, spoofed sender ID, bad LAN secret) |
| 404    | Not found |
| 409    | Conflict (a swipe refused by a limit or gate, or a target that is not visible; see "Browse, swipe and report"). `code` is `CONFLICT` unless the route documents a specific one, such as `PROFILE_NOT_ACTIVE` in "Pause and resume" |
| 429    | Too many requests (per-IP+method rate limit, 240/min default; `X-RateLimit-*` headers) |
| 500    | Internal server error |

---

## Auth endpoints

There is exactly one auth route. Sign-up, sign-in, sign-out, password reset and
token refresh all happen in Clerk. The routes `POST /api/auth/signup`, `login`,
`refresh`, `logout` and `GET /api/auth/me` no longer exist and return 404.

### POST /api/auth/session

Open a session for the signed-in Clerk user. Creates the local profile on the
first call and returns the existing one afterwards. Safe to call on every app
start.

**Request headers:**
- `Authorization: Bearer <Clerk session token>`

**Request body:** none.

**Responses:**

- **201 Created** — first call for this Clerk user. A new local profile was created.
- **200 OK** — the Clerk user already had a live local profile.

Both return `AuthUserDto`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "displayName": null,
  "profileCompletionState": "needs_name"
}
```

| Field | Notes |
|-------|-------|
| id | The local user UUID. Use it in every `/api/users/{id}/...` path. |
| email | The token's optional `email` claim, echoed back. `null` when the claim is absent. It is **not** stored on the profile. |
| displayName | `null` until the user sets a name. |
| profileCompletionState | First missing profile field, e.g. `needs_name`. |

- **401 Unauthorized** — missing, malformed, expired or unverifiable token, or the
  local profile is banned.

**Notes:**
- A new profile is `INCOMPLETE`, with no email and no birth date. The client
  fills those in through the normal profile routes, which enforce the minimum
  age (`minAge = 18` in config). An `INCOMPLETE` user cannot become `ACTIVE`, so
  discovery never shows them.
- A Clerk user is never linked to a pre-existing local profile. Each Clerk user
  starts with a fresh one.
- Two simultaneous first calls for the same Clerk user create one profile.

---

## Photo endpoints

Photos are stored as managed filesystem paths under the configured `photoStorageRoot`.  
The database stores internal paths like `/photos/<userId>/<filename>`.  
API responses return **public URLs** that the Flutter client can render directly.

### POST /api/users/{id}/photos

Upload a new photo for the user.

**Auth:** Bearer token required. Token subject must match `{id}`.

**Request:**
- `Content-Type: multipart/form-data`
- Field name: `photo`

**Responses:**

- **201 Created** (`PhotoDtos.PhotoUploadResponse` — note the
  `primaryPhotoUrl` / `photoUrls` keys plus profile-completion fields):

```json
{
  "photo": {
    "id": "photo-uuid",
    "url": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg"
  },
  "primaryPhotoUrl": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg",
  "photoUrls": [
    "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg"
  ],
  "missingProfileFields": [],
  "profileComplete": false,
  "canActivate": false,
  "canBrowse": false
}
```

- **400 Bad Request** — missing file or invalid image.
- **401 Unauthorized** / **403 Forbidden**
- **404 Not Found** — user does not exist.

**Notes:**
- The uploaded file is validated (safe filename, size limit
  `maxPhotoUploadBytes = 5 MiB` from config). No EXIF-orientation
  handling exists in `RestApiPhotoStorage` — verified by search.

---

### DELETE /api/users/{id}/photos/{photoId}

Remove a specific photo.

**Auth:** Bearer token required. Token subject must match `{id}`.

**Path parameters:**
- `photoId` — the stable photo identifier. Accepts both managed UUIDs (from uploaded photos) and derived IDs (for legacy external URLs) as returned by `GET /api/users/{id}/photos`.

**Responses:**

- **200 OK** (`PhotoDtos.PhotoMutationResponse` — `primaryPhotoUrl` /
  `photoUrls` plus profile-completion fields, no `photo` wrapper):

```json
{
  "primaryPhotoUrl": null,
  "photoUrls": []
}
```

- **404 Not Found** — photo not found for this user.
- **401 Unauthorized** / **403 Forbidden**

---

### PUT /api/users/{id}/photos/order

Reorder the user's photos.

**Auth:** Bearer token required. Token subject must match `{id}`.

**Request headers:**
- `Content-Type: application/json`

**Request body:**

```json
{
  "photoIds": ["photo-uuid-2", "photo-uuid-1"]
}
```

Rules:
- `photoIds` must include **all** existing real photos (placeholders are excluded automatically).
- `photoIds` accepts both managed UUIDs (from uploaded photos) and derived IDs (for legacy external URLs) as returned by `GET /api/users/{id}/photos`.
- Order in the array becomes the new display order.

**Responses:**

- **200 OK** — `PhotoDtos.PhotoMutationResponse` (same shape as
  `POST /api/users/{id}/photos` 201 without the `photo` wrapper:
  `primaryPhotoUrl` / `photoUrls` plus profile-completion fields).
- **400 Bad Request** — missing/unknown photo IDs, or incomplete list.
- **401 Unauthorized** / **403 Forbidden**

---

### GET /api/users/{id}/photos

List all editable photos for the user.

**Auth:** Bearer token required. Token subject must match `{id}`.

**Path parameters:**
- `id` — UUID of the user.

**Responses:**

- **200 OK**

```json
{
  "primaryUrl": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg",
  "photos": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "url": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg"
    }
  ]
}
```

Each photo entry includes:
- `id` — a stable identifier for the photo. For backend-managed photos this is the managed photo UUID. For legacy external URLs this is a deterministic UUID derived from the URL.
- `url` — the public URL for rendering the photo.

`primaryUrl` is the public URL of the first photo in display order, or `null` if the user has no real photos.

Placeholder photos (`placeholder://default-avatar`) are **not** included. Only real editable photos are returned.

Every `id` returned by this endpoint is valid for use with `DELETE /api/users/{id}/photos/{photoId}` and `PUT /api/users/{id}/photos/order`.

- **401 Unauthorized** — missing or invalid bearer token.
- **403 Forbidden** — bearer token subject does not match `{id}`.
- **404 Not Found** — user does not exist.
- Deleted/banned users cannot use photo routes at all:
  `requirePhotoEligibleUser` throws (mapped to 500 `INTERNAL_ERROR`),
  it does not return 409.

---

### GET /photos/{userId}/{filename}

Serve a photo file. **Requires a bearer token** (verified against
`RestApiServer.registerStaticPhotoRoute`).

**Path parameters:**
- `userId` — UUID of the photo owner.
- `filename` — safe filename (alphanumeric, dots, dashes, underscores).

**Responses:**

- **200 OK** — image bytes with correct `Content-Type`, plus `ETag` and
  `Cache-Control: private, max-age=3600`.
- **304 Not Modified** — the `If-None-Match` header equals the current `ETag`.
- **401 Unauthorized** — no valid bearer token.
- **403 Forbidden** — the viewer and the owner are blocked in either direction,
  or the owner is not `ACTIVE`.
- **404 Not Found** — invalid userId, unsafe filename, or file missing.
- **429 Too Many Requests** — the API rate limit was hit.

**Notes:**
- This is the URL returned by all photo mutation endpoints.
- An owner can always load their own photos, whatever state their account is in
  (for example while still `INCOMPLETE` during onboarding).
- Flutter must send the `Authorization` header with image requests; a plain
  `Image.network(url)` without headers gets 401.

---

## Browse, swipe and report

Verified against `RestApiServer`, `RestApiRequestGuards`, `MatchingService`,
`ActivityMetricsService`, `TrustSafetyService` and `JdbiMatchmakingStorage`.

### Reading profiles

- `GET /api/users` and `GET /api/users/{id}` **require a logged-in user**
  (bearer token). Anonymous calls return 401.
- `GET /api/users` lists `ACTIVE` users only. The caller's own profile is always
  included. Anyone blocked in either direction is left out.
- `GET /api/users/{id}` for another user returns 403 when the two users are
  blocked in either direction, and 409 ("Target user is not visible") when the
  target is not `ACTIVE`. Reading your own profile always works.

### Swiping

`POST /api/users/{id}/like/{targetId}` and `/pass/{targetId}` return **429**
(`RATE_LIMITED`) when the **daily like, super-like or pass limit** is reached
(`-1` means unlimited). The count comes from an append-only ledger
(`swipe_quota_uses`), so `POST /api/users/{id}/undo` and unmatching do **not**
give a swipe back.

They return 409 when:

- the **session gate** trips: more than `maxSwipesPerSession` swipes in one
  session, or a swipe rate above `suspiciousSwipeVelocity` per minute after at
  least 10 swipes (while `suspiciousSwipeVelocityBlockingEnabled` is on, which is
  the default).

A swipe on a user who is blocked in either direction is also refused.

`POST /api/users/{id}/undo` can undo the latest like or pass made through the
REST like/pass routes, inside the undo window.

### Reporting

`POST /api/users/{id}/report/{targetId}` returns `ReportResponse`:

| Field | Meaning |
|-------|---------|
| `success` | The report was recorded. |
| `autoFlagged` | `true` when this report took the target to the report threshold. **The target is now flagged `UNDER_REVIEW`, not banned.** |
| `autoBanned` | Deprecated alias of `autoFlagged`, kept so older clients do not break. |
| `blockedByReporter` | The reporter also blocked the target. |
| `errorMessage` | Set on failure. |

- The threshold (`autoBanThreshold`, must be greater than 0) counts **distinct**
  reporters. Each reporter can report a target once.
- `UNDER_REVIEW` is a `UserState`. An account in it cannot activate itself.
- Moderators release or ban it through the admin routes: `GET /api/admin/reports`
  lists `UNDER_REVIEW` users with their report count, and
  `POST /api/admin/reports/{targetId}/uphold`, `/dismiss` and `/ban` act on one.
  Callers that are not on the admin list get 403. Dismissing returns the account
  to `ACTIVE` when its profile is complete, otherwise to `INCOMPLETE`.

### Daily swipe quota

`GET /api/users/{id}/daily-status` (own id only) returns today's quota:

```json
{
  "date": "2026-10-11",
  "resetsAt": "2026-10-11T21:00:00Z",
  "likes": { "used": 1, "remaining": 2, "unlimited": false },
  "superLikes": { "used": 0, "remaining": 2, "unlimited": false },
  "passes": { "used": 0, "remaining": null, "unlimited": true }
}
```

- `resetsAt` is an ISO-8601 instant: the start of the next day in the server's
  configured user time zone.
- When a kind has no limit, `unlimited` is `true` and `remaining` is `null`.
  Read `unlimited`; never compare `remaining` to a magic number.
- Errors: 401 (no/invalid token), 403 `FORBIDDEN` (another user's id), 404
  `NOT_FOUND` (unknown user).

### Pending likers

Each entry of `GET /api/users/{id}/pending-likers` carries `superLike`: `true`
when that person's like was a super like. The backend produces no like
notification, so the flag lives only here.

---

## Pause and resume

Verified against `ProfileMutationUseCases.pauseProfile` / `resumeProfile` and
`RelationshipWorkflowPolicy`.

`POST /api/users/{id}/pause` and `POST /api/users/{id}/resume` take no body, act
on the caller's own profile only, and return **200** with the same body as
`GET /api/users/{id}` (so `state` is `PAUSED` or `ACTIVE`).

| Status | `code` | When |
|--------|--------|------|
| 409 | `PROFILE_NOT_ACTIVE` | pause, and the profile is not `ACTIVE` (already paused, incomplete, under review) |
| 409 | `PROFILE_NOT_PAUSED` | resume, and the profile is not `PAUSED` |
| 409 | `PROFILE_INCOMPLETE` | resume, and the profile is `INCOMPLETE` or is paused but no longer complete enough to activate |
| 403 | `FORBIDDEN` | the path id is not the caller |
| 404 | `NOT_FOUND` | unknown user |

While paused, a user:

- is left out of other users' browse candidates, standouts and pending likers,
  and out of `GET /api/users`;
- keeps their matches and conversations, and can still send and receive
  messages in them;
- cannot browse or swipe, and cannot block or report;
- is not readable by others through `GET /api/users/{id}` (409, "Target user is
  not visible"). Their own `GET /api/users/{id}` works and shows `PAUSED`.

Editing a paused profile (`PUT /api/users/{id}/profile`) leaves it paused. Only
`/resume` reactivates it.

---

## Conversations and presence

Verified against `RestApiServer`, `MessagingUseCases`, `ConnectionService` and
`JdbiConnectionStorage`.

### Listing

`GET /api/users/{id}/conversations` returns every conversation the caller has
not deleted, archived or not. Each entry has an `archived` boolean, which is the
**caller's own** flag:

```json
[
  {
    "id": "0b6f...-..._7c1e...-...",
    "otherUserId": "7c1e...",
    "otherUserName": "Bob",
    "messageCount": 2,
    "lastMessageAt": "2026-10-11T18:04:11Z",
    "archived": false
  }
]
```

Optional `?archived=true` returns only archived ones, `?archived=false` only the
rest. Any other value is 400 `BAD_REQUEST`. `limit` and `offset` apply after the
filter.

### Archive and unarchive

- `POST /api/users/{id}/conversations/{conversationId}/archive` → 204.
- `POST /api/users/{id}/conversations/{conversationId}/unarchive` → 204. No
  body. Unarchiving a conversation that is not archived is also 204.
- Both are per user: the other participant's list is unaffected.
- **A new incoming message does not un-archive a conversation.** It stays
  archived until the user unarchives it.
- Errors: 400 `BAD_REQUEST` (the caller is not a participant, or the
  conversation does not exist), 403 `FORBIDDEN` (path id is not the caller).

Unmatching, graceful exit and blocking also set the archive flag, so `archived`
is `true` for those conversations too.

### Delete

`DELETE /api/users/{id}/conversations/{conversationId}` → 204. It hides the
conversation **for the caller only**; the other participant still sees it, and
no messages are removed. The next message from either side makes it visible
again for both, with its full earlier history.

### Presence

`GET /api/users/{id}/presence` returns:

```json
{ "status": "ONLINE", "typing": false, "checkedAt": "2026-10-11T18:04:11.512Z" }
```

`status` is one of `UNKNOWN`, `ONLINE`, `AWAY`, `OFFLINE`.

- `{id}` may be the caller, or **another user the caller can currently message**:
  an active match, with neither side having blocked the other. Anything else is
  403 `FORBIDDEN`. A past match or an old conversation is not enough.
- Optional `?conversationId=` scopes `typing` to one conversation. It must be
  the conversation between the caller and `{id}`; otherwise 403 `FORBIDDEN`.
- Other errors: 401 (no/invalid token), 404 `NOT_FOUND` (unknown `{id}`).

---

## Phone-alpha deleted-account behavior

Verified against `ProfileMutationUseCases.deleteAccount`,
`User.markDeleted`, and `JdbiAccountCleanupStorage.softDeleteAccount`
(one transaction):

1. The `users` row is **soft-deleted** (`deleted_at` set, `state` set to
   `BANNED`); the in-memory `User` is also paused when it was `ACTIVE`
   (`applyDeletionState`: `markDeleted` + `pause` + `email/phone = null`).
2. The user's `email` and `phone` are **nulled out** in the `users` row so
   reuse is not blocked (verified in `softDeleteUser` SQL; unique
   constraint names are not asserted in source — do not cite
   `uk_users_email` / `uk_users_phone` as verified).
3. The user's `clerk_identities` row is **hard-deleted**
   (`deleteClerkIdentity`), so the Clerk user no longer maps to this profile.
   The Clerk account itself is untouched: nothing in the backend calls Clerk.
5. Related graph rows are soft-deleted / deleted in the same transaction
   (likes, matches, conversations, messages, blocks, reports, notes,
   photos, interests, stats, achievements, picks, swipes, friend
   requests, notifications, undo state).
6. The next protected-route call with the same Clerk token returns 401
   (`NOT_PROVISIONED`), because the deleted profile no longer counts.

This means:
- A new `POST /api/auth/session` from the same Clerk user **succeeds with 201**
  and creates a fresh, empty profile (a new local `id`).
- If the deleted account was `UNDER_REVIEW`, `BANNED` or had a report filed
  against it, that fresh profile starts in `UNDER_REVIEW`. Only a SHA-256 hash of
  the Clerk id is kept for this (`departed_account_flags`), and it is used once.
- Protected routes called with the old local `id` **return 401**.
- Deleting the Clerk account itself is a separate step. A Clerk webhook for it
  is not implemented.

**Permanent erase.** `softDeleteRetentionDays` (default 30) after deletion, the
cleanup scheduler hard-deletes the account's remaining rows
(`JdbiAccountCleanupStorage.purgeDeletedBefore`). Reports the user filed or
received are kept with their free-text description removed, attached to an empty
`users` row marked `purged_at`; an account with no reports is removed entirely.
Set `DATING_APP_PURGE_DRY_RUN=true` to log how many accounts would be erased
without erasing them.

---

## Photo URL behavior summary

| Where | Format | Example |
|-------|--------|---------|
| DB storage | managed path | `/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg` |
| API responses | public URL | `http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg` |

Flutter should:
- Persist the public URLs from API responses.
- Use `GET /photos/{userId}/{filename}` for image rendering.
- Re-fetch the profile after photo mutations to get updated
  `primaryPhotoUrl` and `photoUrls` arrays.
