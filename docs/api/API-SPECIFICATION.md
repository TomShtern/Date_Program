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
| 409    | Conflict (a swipe refused by a limit or gate, or a target that is not visible; see "Browse, swipe and report") |
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

Serve a photo file directly. No authentication required.

**Path parameters:**
- `userId` — UUID of the photo owner.
- `filename` — safe filename (alphanumeric, dots, dashes, underscores).

**Responses:**

- **200 OK** — image bytes with correct `Content-Type`.
- **404 Not Found** — invalid userId, unsafe filename, or file missing.

**Notes:**
- This is the public URL returned by all photo mutation endpoints.
- Flutter can cache these URLs with standard HTTP caching headers.

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

`POST /api/users/{id}/like/{targetId}` and `/pass/{targetId}` return 409 when:

- the **daily like or pass limit** is reached (`dailyLikeLimit` / `dailyPassLimit`;
  `-1` means unlimited). The count comes from an append-only ledger
  (`swipe_quota_uses`), so `POST /api/users/{id}/undo` and unmatching do **not**
  give a swipe back.
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
| `autoBanned` | `true` when this report took the target to the report threshold. **The target is now flagged `UNDER_REVIEW`, not banned.** The field name is kept so clients do not break. |
| `blockedByReporter` | The reporter also blocked the target. |
| `errorMessage` | Set on failure. |

- The threshold (`autoBanThreshold`, must be greater than 0) counts **distinct**
  reporters. Each reporter can report a target once.
- `UNDER_REVIEW` is a new `UserState`. An account in it cannot activate itself.
  There is no moderator tool to release it yet; only `BANNED` can be set from the
  app.

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
- Protected routes called with the old local `id` **return 401**.
- Deleting the Clerk account itself is a separate step. A Clerk webhook for it
  is not implemented.

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
