# Phone-Alpha REST API Specification

> **Scope:** This document covers the phone-alpha auth and photo endpoints that the Flutter frontend will consume.  
> **Auth model:** The backend runs a phone-alpha auth shim (email + password, no Clerk/OAuth). Tokens are short-lived JWT access tokens plus opaque refresh tokens.

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

All errors return a JSON body:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description"
}
```

Common status codes:

| Status | Meaning |
|--------|---------|
| 400    | Bad request (malformed JSON, missing field, invalid value) |
| 401    | Unauthorized (missing/invalid token, revoked refresh, deleted/banned user) |
| 403    | Forbidden (mismatched user-scoped route, spoofed sender ID) |
| 404    | Not found |
| 409    | Conflict (duplicate email on signup) |
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
| email | string | yes | Trimmed, lower-cased, IDN-normalized |
| password | string | yes | Min length from config (default 8) |
| dateOfBirth | string (ISO date) | yes | User must be >= minAge (default 18) |

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

- **201 Created**

```json
{
  "photo": {
    "id": "photo-uuid",
    "url": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg"
  },
  "primaryUrl": "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg",
  "photos": [
    "http://localhost:7070/photos/550e8400-e29b-41d4-a716-446655440000/img_1234567890.jpg"
  ]
}
```

- **400 Bad Request** — missing file or invalid image.
- **401 Unauthorized** / **403 Forbidden**
- **404 Not Found** — user does not exist.

**Notes:**
- The uploaded file is validated (safe filename, size limit from config).
- EXIF orientation is handled automatically.

---

### DELETE /api/users/{id}/photos/{photoId}

Remove a specific photo.

**Auth:** Bearer token required. Token subject must match `{id}`.

**Responses:**

- **200 OK**

```json
{
  "primaryUrl": null,
  "photos": []
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
- `photoIds` must include **all** existing photos.
- Order in the array becomes the new display order.

**Responses:**

- **200 OK** — same shape as `POST /api/users/{id}/photos` 201 (without the `photo` wrapper).
- **400 Bad Request** — missing/unknown photo IDs, or incomplete list.
- **401 Unauthorized** / **403 Forbidden**

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

When a user deletes their account:

1. The `users` row is **soft-deleted** (`deleted_at` set, `state` set to `BANNED`).
2. The user's `email` and `phone` are **nulled out** in the `users` row so the unique constraints (`uk_users_email`, `uk_users_phone`) do **not** block reuse.
3. All `user_credentials` rows for that user are **hard-deleted**.
4. All active `auth_refresh_tokens` for that user are **revoked**.
5. The old access token becomes invalid on the next `me` or protected-route call.

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
- Re-fetch the profile after photo mutations to get updated `primaryUrl` and `photos` arrays.
