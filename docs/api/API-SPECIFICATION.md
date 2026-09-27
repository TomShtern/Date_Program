# Phone-Alpha REST API Specification

> **Status (2026-09-27):** auth + photo sections verified against
> `RestApiServer`, `AuthUseCases`, `AuthTokenService`, `AppConfig`, and
> `config/app-config.json`. This spec still covers only the phone-alpha
> auth/photo surface — for the full route list (users, location,
> matching, social, messaging, notes) read the route registration in
> `RestApiServer` (`app.get/post/put/delete` under `/api/`), which is
> authoritative.
> **Scope:** This document covers the phone-alpha auth and photo endpoints that the Flutter frontend will consume.
> **Auth model:** email + password (no Clerk/OAuth). Short-lived HS256 JWT
> access tokens plus single-use-rotated opaque refresh tokens
> (`AuthUseCases` + `AuthTokenService`).

---

## Base URL

```
http://localhost:7070
```

All paths below are relative to this base.

---

## Authentication

Most endpoints require a **Bearer** token in the `Authorization` header:

```
Authorization: Bearer <accessToken>
```

Access tokens expire after `expiresInSeconds` (default 900s). Use the refresh endpoint to rotate tokens.

### Token validation rules

- Deleted or banned users are rejected at **every** authenticated call (login, refresh, me, and all protected routes).
- Refresh tokens are single-use: each successful refresh issues a new pair and revokes the old refresh token.

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
| 401    | Unauthorized (missing/invalid token, revoked refresh, deleted/banned user) |
| 403    | Forbidden (mismatched user-scoped route, spoofed sender ID, bad LAN secret) |
| 404    | Not found |
| 409    | Conflict (duplicate email on signup) |
| 429    | Too many requests (per-IP+method rate limit, 240/min default; `X-RateLimit-*` headers) |
| 500    | Internal server error |

---

## Auth endpoints

### POST /api/auth/signup

Create a new incomplete user account.

**Request headers:**
- `Content-Type: application/json`

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple",
  "dateOfBirth": "1998-04-30"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| email | string | yes | Trimmed, lower-cased, IDN-normalized (`TextNormalization`) |
| password | string | yes | Min length from config (`minPasswordLength = 12` in `config/app-config.json`) |
| dateOfBirth | string (ISO date) | yes | User must be >= minAge (`minAge = 18` in config) |

**Responses:**

- **201 Created**

```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "AbCdEf...",
  "expiresInSeconds": 900,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": null,
    "profileCompletionState": "needs_name"
  }
}
```

- **409 Conflict** — email already exists for an active/undeleted account.
- **400 Bad Request** — missing field, underage, or password too short.

**Notes:**
- The user is created in `INCOMPLETE` state.
- `profileCompletionState` tells the UI which field is missing first (e.g. `needs_name`).

---

### POST /api/auth/login

Authenticate and receive a token pair.

**Request headers:**
- `Content-Type: application/json`

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple"
}
```

**Responses:**

- **200 OK** — same shape as signup 201.
- **401 Unauthorized** — bad credentials, or account is deleted/banned.

---

### POST /api/auth/refresh

Rotate the refresh token and issue a new access token.

**Request headers:**
- `Content-Type: application/json`

**Request body:**

```json
{
  "refreshToken": "AbCdEf..."
}
```

**Responses:**

- **200 OK**

```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "GhIjKl...",
  "expiresInSeconds": 900,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "displayName": null,
    "profileCompletionState": "needs_name"
  }
}
```

- **401 Unauthorized** — token invalid, expired, revoked, or user deleted/banned.

**Notes:**
- The old refresh token is revoked after a successful call.
- Store the new `refreshToken` and discard the old one.

---

### POST /api/auth/logout

Revoke the current refresh token.

**Request headers:**
- `Content-Type: application/json`

**Request body:**

```json
{
  "refreshToken": "AbCdEf..."
}
```

**Responses:**

- **204 No Content**
- **401 Unauthorized** — invalid or already-revoked token.

---

### GET /api/auth/me

Return the current authenticated user.

**Request headers:**
- `Authorization: Bearer <accessToken>`

**Responses:**

- **200 OK**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "displayName": null,
  "profileCompletionState": "needs_name"
}
```

- **401 Unauthorized** — missing/invalid token, or user deleted/banned.

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
3. All `user_credentials` rows for that user are **hard-deleted**
   (`deleteUserCredentials`).
4. All active `auth_refresh_tokens` for that user are **revoked**
   (`revokeUserRefreshTokens` sets `revoked_at`).
5. Related graph rows are soft-deleted / deleted in the same transaction
   (likes, matches, conversations, messages, blocks, reports, notes,
   photos, interests, stats, achievements, picks, swipes, friend
   requests, notifications, undo state).
6. The old access token becomes invalid on the next `me` or protected-route call.

This means:
- A new signup with the same email **succeeds** after deletion.
- Login with the old email **returns 401**.
- Refresh with an old refresh token **returns 401**.
- `me` with an old access token **returns 401**.

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
