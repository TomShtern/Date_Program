# Flutter Frontend Project Handoff

> Snapshot date: 2026-04-18
> Purpose: copy this file into the new blank Flutter project so the mobile codebase starts with the right mental model, server contract, and scope.
> Source of truth for this document: current code and current verified runtime behavior, not stale docs.

## 1. What This Mobile Project Is

This mobile app is a new Flutter frontend for an already-existing Java backend.

- The server stays Java.
- The mobile app is a separate Flutter/Dart project in a new folder/repo.
- The contract between them is REST/JSON only.
- The mobile app is a thin client: UI, local app state, HTTP requests, and presentation logic.
- Matching, messaging, profile rules, safety rules, verification flows, stats, and storage stay on the server.

This is not:

- a Java-to-Dart port
- a server rewrite
- a Kotlin migration
- a shared-domain-code project
- a cloud-first build

## 2. Current Server Snapshot

As of 2026-04-18, the backend is in a good enough state to start the Flutter app now.

- Full repo verification passed: 1866 tests, 0 failures, 0 errors, 2 skipped.
- PostgreSQL smoke verification passed.
- LAN REST startup was verified successfully.
- Non-loopback REST mode now requires a shared secret.
- Browser/web clients can use CORS allowlisting; native mobile does not need CORS.

High-level stack behind the mobile app:

- Java 25
- Maven
- Javalin REST adapter
- PostgreSQL as the real runtime database path
- H2 retained for compatibility and test scenarios

## 3. Product Reality For The Flutter Project

The mobile project should start from the real current backend, not from idealized assumptions.

Current reality:

- Phase 2 is effectively complete: the phone can talk to the server over LAN.
- Phase 3 starts with a Flutter app for Android first.
- iOS remains possible from the same codebase later, but iOS builds still require a Mac.
- The current location feature is effectively Israel-only for supported selectable data.
- Development login is currently a dev-mode user picker backed by `GET /api/users`.
- There is currently no REST signup/create-account route.
- There is currently no real production auth/JWT flow.
- Polling is acceptable for chat in Phase 3. WebSocket is not required.

## 4. Non-Negotiable Constraints

These are the things the blank Flutter project must assume from day one.

### 4.1 Server ownership

The server is the authority for:

- candidate eligibility
- like/pass rules
- match creation
- conversation and message persistence
- moderation/block/report rules
- verification rules
- stats and achievements
- location resolution

Do not duplicate or reimplement those rules in Dart.

### 4.2 Mobile ownership

The Flutter app is responsible for:

- screen composition
- navigation
- loading/error/empty states
- request orchestration
- storing the selected dev user locally
- keeping headers and API base URL consistent
- rendering server responses correctly

### 4.3 Scope discipline

Do not start by building:

- real auth
- signup
- payments
- subscriptions
- push notifications
- cloud deployment
- WebSocket chat
- large offline-sync systems

First build the working core loop:

1. pick a user
2. browse candidates
3. like/pass
4. see matches
5. open conversation
6. send messages

## 5. Recommended Flutter Stack

Use the current roadmap assumptions unless there is a strong reason to change them.

Recommended stack:

- Flutter
- Dart
- Material 3
- Riverpod for state management
- Dio for HTTP

Safe starter dependencies:

- `flutter_riverpod`
- `dio`
- `shared_preferences` for local dev-user persistence
- `intl` if formatting dates/times in the UI becomes necessary

Optional later dependencies, not required on day one:

- `cached_network_image`
- `go_router`
- `freezed` / `json_serializable`

Do not front-load tooling complexity. Keep the first version boring and direct.

## 6. Suggested Flutter Project Structure

Recommended starting structure:

```text
dating_app/
├── lib/
│   ├── main.dart
│   ├── app/
│   │   ├── app.dart
│   │   ├── app_config.dart
│   │   └── env.dart
│   ├── api/
│   │   ├── api_client.dart
│   │   ├── api_headers.dart
│   │   ├── api_endpoints.dart
│   │   └── api_error.dart
│   ├── features/
│   │   ├── auth/
│   │   │   ├── dev_user_picker_screen.dart
│   │   │   ├── selected_user_store.dart
│   │   │   └── selected_user_provider.dart
│   │   ├── browse/
│   │   ├── matches/
│   │   ├── chat/
│   │   ├── profile/
│   │   └── settings/
│   ├── models/
│   │   ├── user_summary.dart
│   │   ├── user_detail.dart
│   │   ├── browse_candidates_response.dart
│   │   ├── match_summary.dart
│   │   ├── conversation_summary.dart
│   │   ├── message_dto.dart
│   │   └── api_error_response.dart
│   ├── shared/
│   │   ├── widgets/
│   │   ├── formatting/
│   │   └── result/
│   └── theme/
│       ├── app_theme.dart
│       └── app_colors.dart
├── test/
└── pubspec.yaml
```

Two structural rules matter most:

- split by feature and responsibility, not by giant shared abstractions
- keep the API client thin and keep state changes in providers/viewmodels

## 7. Backend Startup For Mobile Development

The blank Flutter project needs to know how the backend is actually started for LAN development.

### 7.1 PostgreSQL is a prerequisite

The current default app configuration expects the local PostgreSQL runtime.

Server repo commands:

```powershell
.\check_postgresql_runtime_env.ps1
.\start_local_postgres.ps1
```

### 7.2 Compile and build the runtime classpath

Use these commands in the server repo:

```powershell
mvn -q -DskipTests compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt" "-Dmdep.pathSeparator=;" "-Dmdep.includeScope=runtime"
```

### 7.3 Start the REST server directly with Java

Do not rely on `mvn exec:exec` for LAN/mobile startup.

Reason:

- the Maven exec plugin is wired to `datingapp.Main`
- mobile/LAN startup needs `datingapp.app.api.RestApiServer`
- LAN mode also needs explicit arguments like `--host`, `--shared-secret`, and optionally `--allowed-origins`

Use this instead:

```powershell
$cp = 'target/classes;' + (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()

java --enable-preview --enable-native-access=ALL-UNNAMED `
  -cp $cp `
  datingapp.app.api.RestApiServer `
  --host=0.0.0.0 `
  --port=7070 `
  --shared-secret=lan-dev-secret `
  --allowed-origins=http://localhost:3000,http://192.168.1.194:3000
```

Supported environment variables:

- `DATING_APP_REST_SHARED_SECRET`
- `DATING_APP_REST_ALLOWED_ORIGINS`

### 7.4 Base URL examples

Physical phone on WiFi:

```text
http://192.168.1.194:7070
```

Replace `192.168.1.194` with the current laptop LAN IP.

Health-check URL example:

```text
http://192.168.1.194:7070/api/health
```

### 7.5 Android cleartext note

Phase 3 development currently uses plain HTTP over LAN, not HTTPS.

That means the Flutter Android app may need cleartext HTTP to be explicitly allowed in Android app configuration. Do not wait until late in the project to discover this.

## 8. Transport Contract The Flutter App Must Obey

### 8.1 Required headers

Health route:

- `GET /api/health` does not require the shared secret.

All other LAN requests:

- send `X-DatingApp-Shared-Secret: <secret>`

Mutating and user-scoped requests should also send:

- `X-User-Id: <current user UUID>`

Recommended client rule:

- send `X-DatingApp-Shared-Secret` on every request except health
- send `X-User-Id` on every user-scoped request, including reads

Why send `X-User-Id` on reads too even though some reads work without it right now:

- it keeps the client behavior consistent
- it avoids surprises if read-side guards get stricter later
- it makes conversation and scoped-route debugging simpler

### 8.2 Scoped identity rules

The server currently enforces the following:

- `POST`, `PUT`, and `DELETE` require `X-User-Id`, except `/api/health` and `/api/location/resolve`
- when `X-User-Id` is present on a route with `{id}` or `{authorId}`, it must match the path parameter
- conversation message routes validate that the acting user is part of the conversation
- sending a message also requires the body `senderId` to match the acting user when the header is present

### 8.3 Rate limiting

The local REST adapter applies a rate limit of:

- 240 requests per minute
- keyed by client IP and HTTP method
- health and preflight requests are excluded

When rate-limited, the server returns:

- HTTP `429`
- JSON error body
- `Retry-After`
- `X-RateLimit-Limit`
- `X-RateLimit-Used`

Do not build an aggressive short-interval polling strategy without accounting for this.

### 8.4 CORS

Native Flutter mobile:

- does not need CORS

Flutter web or browser tooling:

- does need an allowlisted origin via `--allowed-origins` or `DATING_APP_REST_ALLOWED_ORIGINS`

### 8.5 Error shape

The common error body shape is:

```json
{
  "code": "BAD_REQUEST",
  "message": "Human-readable message"
}
```

Important status/code mapping:

- `400 BAD_REQUEST`
- `403 FORBIDDEN`
- `404 NOT_FOUND`
- `409 CONFLICT`
- `429 TOO_MANY_REQUESTS`
- `500 INTERNAL_ERROR`

### 8.6 Time format rules

Most server date/time values are JSON strings because Jackson is configured to serialize Java time types as ISO-8601 values.

Examples:

- `createdAt`
- `lastMessageAt`
- `sentAt`
- `verifiedAt`

One important exception:

- `GET /api/health` returns `timestamp` as a numeric epoch-millis `long`

Treat that as an intentional inconsistency the client must handle.

## 8.7 Minimal Flutter client wiring pattern

The blank Flutter project should centralize runtime configuration and headers immediately.

Minimal example:

```dart
class AppConfig {
  const AppConfig({
    required this.baseUrl,
    required this.lanSharedSecret,
  });

  final String baseUrl;
  final String lanSharedSecret;
}

class CurrentSession {
  const CurrentSession({required this.userId});

  final String userId;
}
```

```dart
final dio = Dio(BaseOptions(baseUrl: config.baseUrl))
  ..interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) {
        final isHealth = options.path == '/api/health';

        if (!isHealth) {
          options.headers['X-DatingApp-Shared-Secret'] = config.lanSharedSecret;
        }

        final isUserScoped = options.path.startsWith('/api/users/') ||
            options.path.startsWith('/api/conversations/');

        if (isUserScoped && session != null) {
          options.headers['X-User-Id'] = session.userId;
        }

        handler.next(options);
      },
    ),
  );
```

Important note:

- treat `session.userId` as the current acting user selected from the dev picker
- do not scatter header logic across screens or repositories
- keep this behavior in one place

## 9. Core API Surface For Flutter v0.1

These are the most important routes for the first real mobile loop.

### 9.1 Health

`GET /api/health`

Purpose:

- verify the backend is reachable from the phone

Example response:

```json
{
  "status": "ok",
  "timestamp": 1763462400000
}
```

### 9.2 Dev login / user picker

`GET /api/users`

Purpose:

- current dev-mode login source
- use this to let the user pick which existing profile they act as

Example response:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "Dana",
    "age": 27,
    "state": "ACTIVE"
  }
]
```

Important note:

- there is no REST signup/create-account route right now
- first mobile login should be a picker, not a true authentication flow

### 9.3 User detail

`GET /api/users/{id}`

Purpose:

- profile detail screen
- richer user data than the list response

Current response fields:

- `id`
- `name`
- `age`
- `bio`
- `gender`
- `interestedIn`
- `approximateLocation`
- `maxDistanceKm`
- `photoUrls`
- `state`

Example response:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "Dana",
  "age": 27,
  "bio": "Loves coffee and beach walks.",
  "gender": "FEMALE",
  "interestedIn": ["MALE"],
  "approximateLocation": "Tel Aviv",
  "maxDistanceKm": 50,
  "photoUrls": ["/photos/dana-1.jpg"],
  "state": "ACTIVE"
}
```

### 9.4 Browse candidates

Preferred route:

`GET /api/users/{id}/browse`

Deprecated alias:

`GET /api/users/{id}/candidates`

Use `/browse` in the Flutter app.

Current `browse` response shape:

```json
{
  "candidates": [
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "name": "Noa",
      "age": 29,
      "state": "ACTIVE"
    }
  ],
  "dailyPick": {
    "userId": "33333333-3333-3333-3333-333333333333",
    "userName": "Maya",
    "userAge": 30,
    "date": "2026-04-18",
    "reason": "High compatibility",
    "alreadySeen": false
  },
  "dailyPickViewed": false,
  "locationMissing": false
}
```

Important notes:

- browsing requires the current user to be `ACTIVE`
- inactive users can hit a `409 CONFLICT`
- current browse candidate items are thin: `id`, `name`, `age`, `state`
- they do not currently include `bio` or `photoUrls`

This is a real mobile concern. If swipe cards need richer data, the backend may need enrichment later.

### 9.5 Like a user

`POST /api/users/{id}/like/{targetId}`

Headers:

- `X-DatingApp-Shared-Secret`
- `X-User-Id`

Responses:

- `200` when like recorded but no match created
- `201` when a mutual match was created

Example non-match response:

```json
{
  "isMatch": false,
  "message": "Like recorded",
  "match": null
}
```

Example match response:

```json
{
  "isMatch": true,
  "message": "It's a match!",
  "match": {
    "matchId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
    "otherUserId": "22222222-2222-2222-2222-222222222222",
    "otherUserName": "Noa",
    "state": "ACTIVE",
    "createdAt": "2026-04-18T12:34:56Z"
  }
}
```

### 9.6 Pass a user

`POST /api/users/{id}/pass/{targetId}`

Example response:

```json
{
  "message": "Passed"
}
```

### 9.7 Undo last swipe

`POST /api/users/{id}/undo`

Example response:

```json
{
  "success": true,
  "message": "Last swipe undone",
  "matchDeleted": false
}
```

This is useful for later polish, but it already exists.

### 9.8 Matches list

`GET /api/users/{id}/matches?limit=20&offset=0`

Example response:

```json
{
  "matches": [
    {
      "matchId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
      "otherUserId": "22222222-2222-2222-2222-222222222222",
      "otherUserName": "Noa",
      "state": "ACTIVE",
      "createdAt": "2026-04-18T12:34:56Z"
    }
  ],
  "totalCount": 1,
  "offset": 0,
  "limit": 20,
  "hasMore": false
}
```

### 9.9 Conversations list

`GET /api/users/{id}/conversations?limit=50&offset=0`

Example response:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
    "otherUserId": "22222222-2222-2222-2222-222222222222",
    "otherUserName": "Noa",
    "messageCount": 5,
    "lastMessageAt": "2026-04-18T14:20:00Z"
  }
]
```

Important note:

- conversation list responses are also fairly thin
- they do not currently include avatar URLs or a last-message preview string

### 9.10 Messages in a conversation

`GET /api/conversations/{conversationId}/messages?limit=50&offset=0`

Important note:

- this is the real route
- not `/api/users/{id}/conversations/{id}/messages`

Example response:

```json
[
  {
    "id": "44444444-4444-4444-4444-444444444444",
    "conversationId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
    "senderId": "11111111-1111-1111-1111-111111111111",
    "content": "Hey there",
    "sentAt": "2026-04-18T14:20:00Z"
  }
]
```

### 9.11 Send a message

`POST /api/conversations/{conversationId}/messages`

Request body:

```json
{
  "senderId": "11111111-1111-1111-1111-111111111111",
  "content": "Hey there"
}
```

Response:

- `201 Created`
- response body is the created `MessageDto`

Important note:

- `senderId` must be present in the body
- if `X-User-Id` is present, it must match that `senderId`

## 10. API Surface Already Available For Later Mobile Phases

The backend already exposes more than the v0.1 core loop.

### Location

- `GET /api/location/countries`
- `GET /api/location/cities?countryCode=IL&query=tel&limit=10`
- `POST /api/location/resolve`

Location resolve request body:

```json
{
  "countryCode": "IL",
  "cityName": "Tel Aviv",
  "zipCode": null,
  "allowApproximate": false
}
```

Important notes:

- supported selectable country is effectively `IL`
- other countries are present in metadata but marked unavailable
- location selection and approximate labeling should come from the server, not client-side geocoding logic

### Profile update

- `PUT /api/users/{id}/profile`

Current write-side profile payload supports:

- bio
- birthDate
- gender
- interestedIn
- latitude / longitude
- maxDistanceKm
- minAge / maxAge
- heightCm
- smoking
- drinking
- wantsKids
- lookingFor
- education
- interests
- dealbreakers
- nested `location` object

Important note:

- the read-side user detail DTO is thinner than the write-side profile DTO
- a future profile edit screen may require either a richer read endpoint or careful client-side sourcing of editable state

### Matching and discovery extras

- `GET /api/users/{id}/pending-likers`
- `GET /api/users/{id}/standouts`
- `GET /api/users/{id}/match-quality/{matchId}`
- `POST /api/users/{id}/matches/{matchId}/archive`
- `POST /api/users/{id}/undo`
- `GET /api/users/{id}/stats`
- `GET /api/users/{id}/achievements`

### Social and safety

- `GET /api/users/{id}/notifications`
- `POST /api/users/{id}/notifications/read-all`
- `POST /api/users/{id}/notifications/{notificationId}/read`
- `GET /api/users/{id}/friend-requests`
- `POST /api/users/{id}/friend-requests/{targetId}`
- `POST /api/users/{id}/friend-requests/{requestId}/accept`
- `POST /api/users/{id}/friend-requests/{requestId}/decline`
- `POST /api/users/{id}/relationships/{targetId}/graceful-exit`
- `POST /api/users/{id}/relationships/{targetId}/unmatch`
- `GET /api/users/{id}/blocked-users`
- `POST /api/users/{id}/block/{targetId}`
- `DELETE /api/users/{id}/block/{targetId}`
- `POST /api/users/{id}/report/{targetId}`

### Verification

- `POST /api/users/{id}/verification/start`
- `POST /api/users/{id}/verification/confirm`

Important note:

- the current verification start response includes `devVerificationCode`
- that is useful for local dev and test flows
- it is not a production auth model

### Profile notes

- `GET /api/users/{authorId}/notes`
- `GET /api/users/{authorId}/notes/{subjectId}`
- `PUT /api/users/{authorId}/notes/{subjectId}`
- `DELETE /api/users/{authorId}/notes/{subjectId}`

## 11. Code-Over-Docs Corrections The Flutter Project Must Know

These are important because a blank project might otherwise follow stale assumptions.

### 11.1 There is no REST create-account route right now

The roadmap still contains language that can imply account creation is part of the first mobile milestone. The actual current REST surface does not expose a `POST /api/users` or similar signup route.

For Phase 3 v0.1, assume:

- dev-mode user picker
- existing users
- no true signup/auth flow yet

### 11.2 Use `/browse`, not `/candidates`

`/api/users/{id}/candidates` still exists, but the server marks it as deprecated and points to `/browse`.

### 11.3 Chat messages are conversation-scoped, not user-scoped

The actual message routes are:

- `GET /api/conversations/{conversationId}/messages`
- `POST /api/conversations/{conversationId}/messages`

The Flutter app should model chat around conversation IDs, not around a nested user-scoped messages route.

### 11.4 Browse DTOs are thinner than the ideal mobile card UI

Current browse candidate items do not include:

- `bio`
- `photoUrls`
- rich profile attributes

That means one of two things will eventually happen:

1. the mobile browse UI starts simpler than the JavaFX vision
2. the backend browse DTO gets enriched later

Do not accidentally assume the richer shape already exists.

### 11.5 Profile read/write symmetry is incomplete

The current update payload is richer than the current detail payload.

That is not fatal for Phase 3 start, but it matters for profile editing later.

## 12. Implementation Order For The Blank Flutter Project

Recommended order:

### Step 1: App shell and environment

Build:

- app config object
- base URL config
- shared secret config
- selected-user persistence
- one Dio client with interceptors

Required behaviors:

- add shared secret on all non-health calls
- add `X-User-Id` on user-scoped calls
- centralize error parsing

### Step 2: Dev user picker

Build:

- startup screen that calls `GET /api/users`
- user selection persistence
- simple "continue as this user" flow

### Step 3: Browse

Build:

- browse screen backed by `GET /api/users/{id}/browse`
- like and pass actions
- loading, empty, conflict, and retry states

### Step 4: Matches and conversations

Build:

- matches list
- conversations list
- basic navigation from match or conversation into chat

### Step 5: Chat

Build:

- message thread
- send message
- refresh/polling strategy that is not rate-limit-hostile

### Step 6: Later-phase enhancements

Then add:

- profile screen
- profile editing
- standouts
- match quality
- undo
- safety screens
- notifications
- stats and achievements

## 13. Things Most Likely To Go Wrong Early

These are the highest-probability beginner mistakes for this project.

### 13.1 Treating the mobile app like a rewrite

Wrong direction:

- rebuilding backend rules in Dart
- inventing alternate matching logic client-side

Correct direction:

- server decides
- Flutter renders

### 13.2 Starting with too much architecture

Wrong direction:

- excessive abstraction
- many generic base classes
- too much code generation before first API success

Correct direction:

- one API client
- straightforward providers/viewmodels
- a few clear models

### 13.3 Using the wrong server startup path

Wrong direction:

- trying to launch LAN REST mode through `mvn exec:exec`

Correct direction:

- direct Java launch of `datingapp.app.api.RestApiServer`

### 13.4 Using `localhost` from the phone

Wrong direction:

- `http://localhost:7070` on a physical phone

Correct direction:

- laptop LAN IP like `http://192.168.1.194:7070`

### 13.5 Forgetting the shared secret

Symptom:

- health works
- everything else returns `403`

Root cause:

- missing `X-DatingApp-Shared-Secret`

### 13.6 Assuming rich mobile DTOs already exist

The current API is functional, but some list/browse payloads are thinner than ideal mobile UI payloads.

## 14. What The First Successful Flutter Milestone Looks Like

The first real win is not polish. It is this:

- app launches on Android phone
- app can hit `/api/health`
- app can load the dev user picker
- selected user can browse candidates
- selected user can like/pass
- app can load matches
- app can open a conversation
- app can send and render messages

Once that works on a physical device over WiFi, the project is on the right track.

## 15. Minimal Bootstrap Checklist

When starting the blank Flutter project, make sure these are true before writing fancy UI.

- Flutter SDK installed and `flutter doctor` is clean enough to run Android
- physical Android device or emulator available
- backend repo can start PostgreSQL locally
- backend REST server can start in LAN mode
- phone can reach `/api/health`
- app configuration contains base URL and LAN shared secret
- Dio interceptor injects required headers
- one user can be selected and stored locally

## 16. Original Server Files Used To Build This Handoff

If the original server repo is available, these were the main source-of-truth files behind this handoff:

- `RoadMap.md`
- `REST_LAN_STARTUP.md`
- `2026-04-18-backend-audit-and-remediation.md`
- `src/main/java/datingapp/app/api/RestRouteSupport.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- `src/main/java/datingapp/core/profile/LocationService.java`

## 17. Final Takeaway

The blank Flutter project should begin with one clear assumption:

the backend is already the product brain, and the Flutter app is a new phone-friendly surface over that brain.

If the mobile project stays disciplined about that boundary, Phase 3 is straightforward.