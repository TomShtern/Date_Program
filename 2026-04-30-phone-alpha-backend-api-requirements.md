# Phone Alpha Backend/API Requirements

Date: 2026-04-30

Purpose: define the backend and API work needed for a **phone-test alpha**: a build that can run on a real Android phone, sign in as a real user, upload real photos, and exercise Discover / Matches / Chats / Profile / Settings against a backend without developer-only user switching.

This is not a public launch plan. It does not require Play Store release, push notifications, paid hosting, public moderation operations, iOS, subscriptions, localization, or production-scale observability.

## 1. Recommendation

Stop broad UI polish now.

The visual system is good enough to lock with a tiny caveat list. The next work should be backend/API-first because the app cannot become usable on a phone until identity, media, reachable HTTPS, onboarding state, and account/safety behavior exist as real server-backed flows.

Keep UI work limited to:

- Removing or replacing the `Tap name to view profile` chat subtitle.
- Softening obvious no-photo fallbacks only if they still matter after real photos work.
- Wiring the new backend contracts into existing screens.

Do not start another general visual pass.

## 2. Source Of Truth And Boundaries

Flutter frontend repo:

- `lib/api/api_endpoints.dart`
- `lib/api/api_client.dart`
- `lib/api/api_headers.dart`
- `lib/features/auth/`
- `lib/features/profile/`
- `lib/features/location/`
- `lib/features/safety/`
- `lib/features/verification/`
- `lib/features/home/`
- `lib/shared/providers/selected_user_guard.dart`
- `android/app/src/main/res/xml/network_security_config.xml`

Backend repo to implement or verify:

- `C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program`

Backend owns:

- Authentication and authorization.
- Password hashing and token storage.
- User/profile persistence.
- Matching, browse, safety, reporting, blocking, verification, stats, achievements, and message rules.
- Photo storage, image processing, media serving, and cleanup.
- Database migrations and backups.

Flutter owns:

- Screens, navigation, local auth/session state, secure token storage, request orchestration, and presentation.
- It must not invent match reasons, verification state, safety state, photo URLs, user identity, or profile-completion state.

## 3. Current Frontend API Shape

The Flutter app currently calls:

- `GET /api/health`
- `GET /api/users`
- `GET /api/users/{id}`
- `PUT /api/users/{id}/profile`
- `GET /api/users/{id}/profile-edit-snapshot`
- `GET /api/users/{viewerId}/presentation-context/{targetId}`
- `GET /api/users/{id}/browse`
- `POST /api/users/{id}/like/{targetId}`
- `POST /api/users/{id}/pass/{targetId}`
- `POST /api/users/{id}/undo`
- `GET /api/users/{id}/matches`
- `GET /api/users/{id}/match-quality/{matchId}`
- `GET /api/users/{id}/conversations`
- `GET /api/conversations/{conversationId}/messages`
- `POST /api/conversations/{conversationId}/messages`
- `GET /api/users/{id}/stats`
- `GET /api/users/{id}/achievements`
- `GET /api/users/{id}/pending-likers`
- `GET /api/users/{id}/standouts`
- `GET /api/users/{id}/notifications`
- `POST /api/users/{id}/notifications/read-all`
- `POST /api/users/{id}/notifications/{notificationId}/read`
- `GET /api/users/{id}/blocked-users`
- `POST /api/users/{id}/block/{targetId}`
- `DELETE /api/users/{id}/block/{targetId}`
- `POST /api/users/{id}/report/{targetId}`
- `POST /api/users/{id}/relationships/{targetId}/unmatch`
- `GET /api/location/countries`
- `GET /api/location/cities`
- `POST /api/location/resolve`
- `POST /api/users/{id}/verification/start`
- `POST /api/users/{id}/verification/confirm`

Current auth/header behavior:

- `GET /api/health` receives no shared secret.
- Other calls receive `X-DatingApp-Shared-Secret`.
- User-scoped calls receive client-supplied `X-User-Id`.
- There is no `Authorization: Bearer ...` flow yet.

Phone alpha must replace client-supplied identity with server-authenticated identity.

## 4. P0 Requirement: Backend Contract Audit

Before implementation starts, audit the backend repo and mark each existing endpoint as:

- Implemented and covered.
- Implemented but not verified recently.
- Stubbed / placeholder.
- Missing.
- Shape mismatch with Flutter.

Audit specifically:

- Auth status: confirm there is no real REST signup/login/JWT flow.
- Existing migration system: the backend already has `MigrationRunner` and `schema_version`; decide whether to keep it for phone alpha instead of replacing it with Flyway.
- Photo support: backend appears to have local photo storage concepts and `user_photos`; confirm what is UI-only JavaFX behavior versus REST/mobile-ready behavior.
- Safety truth: confirm block, unblock, report, and unmatch persist and are enforced across browse, matches, chat, profile views, and message delivery.
- Verification truth: confirm whether verification start/confirm are real delivery flows, dev-code flows, or no-ops.
- Account delete: confirm whether `DELETE /api/users/{id}` is registered, implemented, and cleanup-safe.

Output should be a small backend audit note that becomes the input to P1-P5 below.

## 5. P1 Requirement: Real Authentication

Phone alpha requires real signup/login. The dev-user picker cannot be reachable in release builds.

### Endpoints

Add:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### Signup request

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple",
  "dateOfBirth": "1998-04-30"
}
```

Minimum validation:

- Email is normalized and unique.
- Password length is enforced.
- User must be 18 or older.
- Signup creates a user row in an incomplete onboarding state.

### Login request

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple"
}
```

### Auth response

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "opaque-refresh-token",
  "expiresInSeconds": 900,
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "user@example.com",
    "displayName": null,
    "profileCompletionState": "needs_name"
  }
}
```

### Token rules

- Access token: JWT, short-lived.
- Refresh token: opaque, stored hashed server-side.
- Refresh tokens rotate on use.
- Logout revokes the current refresh token.
- Passwords are hashed with Argon2id or BCrypt. No plaintext, SHA, or MD5.

### Identity migration

For phone alpha, keep existing `/api/users/{id}/...` paths to avoid a huge frontend rewrite, but enforce:

- `Authorization: Bearer <accessToken>` is required for user-scoped routes.
- The token subject must match `{id}` / `{authorId}` path params where present.
- The token subject must be a participant for conversation routes.
- `X-User-Id` should be deprecated. During migration it may be accepted only if it matches the authenticated token subject. It must not be trusted as identity.

## 6. P2 Requirement: Phone-Reachable HTTPS Backend

Phone alpha can use a Cloudflare Tunnel to the laptop backend. That is acceptable for this goal even if it is not production-grade.

Backend/ops requirements:

- Backend can bind to the local port used by the tunnel, expected `7070`.
- Shared secret is configurable and not hardcoded to the default value for phone alpha.
- Backend tolerates requests coming through the tunnel host.
- CORS is not critical for Android native, but should remain correct for Flutter web/dev tools if used.
- Process restart story is documented: manual is acceptable for first test, but a Windows scheduled task or service wrapper is better.

Flutter/release requirements:

- Release build uses `DATING_APP_API_BASE_URL=https://<tunnel-host>`.
- Android release should be HTTPS-only.
- Current `network_security_config.xml` permits cleartext globally; this must be split so debug/profile can use local HTTP but release cannot.

## 7. P3 Requirement: Mobile Photo Upload And Serving

Phone alpha is not credible without real photos.

### Endpoints

Add or expose:

- `GET /api/users/{id}/photos`
- `POST /api/users/{id}/photos`
- `DELETE /api/users/{id}/photos/{photoId}`
- `PUT /api/users/{id}/photos/order`

Optional but useful:

- `PUT /api/users/{id}/photos/{photoId}/primary`

### Upload request

- `multipart/form-data`
- Field name: `photo`
- Accepted image types: JPEG and PNG at minimum.
- Maximum upload size: 5 MB for phone alpha.

### Upload response

```json
{
  "photo": {
    "id": "photo-uuid-or-stable-id",
    "url": "/photos/00000000-0000-0000-0000-000000000000/display/photo-id.jpg",
    "thumbnailUrl": "/photos/00000000-0000-0000-0000-000000000000/thumb/photo-id.jpg",
    "sortIndex": 0,
    "primary": true,
    "createdAt": "2026-04-30T12:00:00Z"
  },
  "primaryPhotoUrl": "/photos/00000000-0000-0000-0000-000000000000/display/photo-id.jpg",
  "photoUrls": [
    "/photos/00000000-0000-0000-0000-000000000000/display/photo-id.jpg"
  ]
}
```

### List response

`GET /api/users/{id}/photos` returns the user's editable photo list:

```json
{
  "primaryUrl": "http://localhost:7070/photos/user-id/photo.jpg",
  "photos": [
    {
      "id": "managed-uuid-or-derived-uuid",
      "url": "http://localhost:7070/photos/user-id/photo.jpg"
    }
  ]
}
```

- Each `id` is a stable identifier. For backend-managed photos this is the upload UUID. For legacy external URLs (e.g. pre-seeded `randomuser.me` portraits) it is a deterministic UUID derived from the URL, ensuring the same ID is returned across requests.
- Every `id` returned by the list endpoint is valid for use with `DELETE /api/users/{id}/photos/{photoId}` and `PUT /api/users/{id}/photos/order`.
- Placeholder photos (`placeholder://default-avatar`) are not included; only real editable photos are returned.
- `primaryUrl` is `null` when no real photos exist.

### Storage and processing

- Store files outside the Git repo and outside the JAR.
- Re-encode uploaded images to JPEG display and thumbnail variants.
- Strip EXIF metadata, especially GPS.
- Delete physical files when a photo is deleted or the account is deleted.
- Serve relative `/photos/...` URLs from the backend so existing Flutter media URL handling continues to work.

### Existing backend note

The backend appears to already have local photo concepts (`LocalPhotoStore`, `user_photos`, and profile `photoUrls`). The requirement is not necessarily to invent storage from scratch. The requirement is to expose a REST/mobile-ready upload, delete, ordering, static-serving, and cleanup contract.

## 8. P4 Requirement: Onboarding State

Phone alpha needs a real funnel after signup. A user should not land in a fake populated shell before their profile is usable.

### Required states

Use a server-owned enum:

- `needs_name`
- `needs_gender`
- `needs_location`
- `needs_photo`
- `needs_bio`
- `complete`

Date of birth is collected during signup and should not be skipped.

### Where state is returned

Return `profileCompletionState` from:

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `GET /api/users/{id}`
- `GET /api/users/{id}/profile-edit-snapshot`
- `PUT /api/users/{id}/profile`
- Photo upload/delete/order responses, because photo changes can move the state to or from `needs_photo`.

### Enforcement

Backend should prevent incomplete profiles from acting like complete dating profiles:

- Incomplete users should not appear in other users' browse results.
- Incomplete users should not be able to like/pass as normal unless the product explicitly allows it.
- Incomplete users can access their own profile/edit/location/photo flows.

Flutter will use this state to route the user through onboarding.

## 9. P5 Requirement: Account Delete

Phone alpha needs a way to delete the test account and start over.

### Endpoint

Add or verify:

- `DELETE /api/users/{id}`

### Behavior

- Requires authenticated user matching `{id}`.
- Revokes refresh tokens.
- Removes or disables credentials so the email can be reused.
- For phone alpha, account deletion must allow email reuse for the same email address.
- Deletes photo files.
- Soft-deletes or anonymizes profile data according to the backend model.
- Preserves conversation integrity without leaking deleted-user identity more than necessary.

### Response

```json
{
  "success": true,
  "message": "Account deleted."
}
```

## 10. P6 Requirement: Safety Truth Check

Existing Flutter screens assume these actions are real:

- Block
- Unblock
- Report
- Unmatch
- Blocked users list

Verify and standardize:

- `POST /api/users/{id}/block/{targetId}`
- `DELETE /api/users/{id}/block/{targetId}`
- `POST /api/users/{id}/report/{targetId}`
- `POST /api/users/{id}/relationships/{targetId}/unmatch`
- `GET /api/users/{id}/blocked-users`

Required behavior:

- Blocking persists.
- Blocked users are excluded from browse.
- Blocked users cannot message the blocker.
- Blocked users should not appear as active matches for the blocker.
- Profile read access should respect blocks.
- Unblock reverses the block.
- Report creates a persisted report row.
- Unmatch removes or archives the match and conversation state consistently.

Standard action response:

```json
{
  "success": true,
  "message": "User blocked."
}
```

Report request should be standardized. The current Flutter API client sends no report body, while the backend appears to support reason/description/block fields. Pick one contract before frontend wiring:

```json
{
  "reason": "HARASSMENT",
  "description": "Short optional user-entered text",
  "blockUser": true
}
```

For first phone alpha, a minimal default report reason is acceptable if the UI does not collect a reason yet, but the backend contract should be explicit.

## 11. P7 Requirement: Verification Flow Decision

Verification does not need real SMS/email delivery for the first phone-test alpha unless it blocks signup. Decide one of:

1. Keep verification as a dev/manual code flow and hide it from the core onboarding gate.
2. Make email verification real and require it after signup.

Recommendation for phone alpha: option 1.

Requirements if keeping it non-blocking:

- Existing verification endpoints may remain available.
- Release UI must not expose raw dev verification codes as a real security signal.
- `verified` profile state should only be true if the backend considers the verification meaningful.

## 12. P8 Requirement: Database And Backup

Do not move the database to the cloud for the first phone alpha unless local reachability becomes the blocker.

Minimum:

- Keep local PostgreSQL.
- Use the existing backend migration system if it is healthy.
- Add a documented `pg_dump` backup path before creating real accounts/photos.
- Back up uploaded photos and DB dumps together.

Important correction: the backend already appears to have a custom `MigrationRunner` plus `schema_version`. Do not replace it with Flyway by default. Audit it first. Replace it only if the audit shows it is blocking reliable phone-alpha work.

## 13. P9 Requirement: Backend Acceptance Checklist

Backend is ready for Flutter phone-alpha integration when all of this is true:

- A new user can sign up with email/password/DOB.
- The password is stored as a modern hash, not plaintext.
- Login returns access and refresh tokens.
- Refresh works after access token expiry.
- Authenticated user-scoped routes reject missing/invalid/mismatched tokens.
- Dev-user identity switching is not needed for release use.
- The phone can reach the backend over an HTTPS tunnel.
- Android release config does not require cleartext HTTP.
- A user can upload, view, reorder, and delete at least one real photo.
- Relative photo URLs returned by the backend render through Flutter's existing media URL handling.
- A new user receives a backend-owned profile completion state.
- Incomplete profiles are not treated as normal discoverable profiles.
- Account delete works and lets the tester start over.
- Block/report/unmatch behavior is persisted and enforced, not just acknowledged.
- Local DB and photo backups are documented and tested once.
- `docs/API-SPECIFICATION.md` has been updated with all new/modified endpoints.

## 14. Suggested Execution Order

1. Backend contract audit.
2. Real auth and token identity.
3. Phone HTTPS tunnel and Android release network config.
4. Photo upload/serving.
5. Onboarding state.
6. Account delete.
7. Safety truth check.
8. Flutter integration pass across auth, media, onboarding routing, and settings.

This order keeps the work pointed at the phone-test goal. It avoids spending more time on visual polish before the app has real identity, real media, and real server-backed behavior.