# Frontend UI Overhaul Contract Response

Date: 2026-04-25

personal note from the backend lead engineer:  "The Stage A contract in that file answers the six P0 items first in the exact 8-point structure you requested.
The main outcomes are: match-to-conversation identity is confirmed as existing now, the match-quality endpoint already exists now,
and person-summary enrichment, presentation-context, profile-edit snapshot, and notification-schema stabilization are marked as will add with explicit target dates.
I also added a short P1/P2 status section after the six P0 items.
I did not start backend implementation. I ran file-level validation on the new Markdown file and there are no errors reported for it."

Stage A response plus Stage B implementation status. The Stage A contract is based on the actual backend code in this repository as of 2026-04-25. Stage B landed additively against the same JSON shapes.

Stage B implementation status as of 2026-04-25:

- Person-summary enrichment is implemented on browse, matches, pending-likers, standouts, and daily pick.
- `GET /api/users/{id}/profile-edit-snapshot` is implemented.
- `GET /api/users/{viewerId}/presentation-context/{targetId}` is implemented.
- Notification event payloads now include the stabilized data keys for match, message, friend-request, friend-request-accepted, and graceful-exit notifications. Legacy `responderId` remains present on the older friend-zone transition notification for compatibility.
- These four items are implemented in backend code; they are not separately confirmed as deployed to a shared environment in this document: person-summary enrichment, `GET /api/users/{id}/profile-edit-snapshot`, `GET /api/users/{viewerId}/presentation-context/{targetId}`, and notification event stabilization.
- No Stage A endpoint path, response field name, enum, or wrapper shape was intentionally changed. The profile snapshot location `precision` may be `ADDRESS`, `CITY`, or `ZIP` depending on the stored coordinates, as already allowed by the enum rules below.

Reviewed sources:

- `src/main/java/datingapp/app/api/RestRouteSupport.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- `src/main/java/datingapp/core/model/Match.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/core/model/LocationModels.java`
- `src/main/java/datingapp/core/profile/LocationService.java`
- `src/main/java/datingapp/core/profile/MatchPreferences.java`
- `src/main/java/datingapp/core/connection/ConnectionModels.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`

Conventions used below:

- All UUIDs are serialized as strings.
- All timestamps are ISO-8601 UTC timestamps.
- For `Exists now`, the sample JSON reflects the current live backend payload.
- For `Will add`, the sample JSON is the target contract the backend will implement.

## 1. Match-to-conversation identity

1. Status: Exists now
2. Exact endpoint path and method:
   - `POST /api/users/{id}/like/{targetId}`
   - `GET /api/users/{id}/matches`
   - `GET /api/users/{id}/conversations`
   - `GET /api/conversations/{conversationId}/messages`
   - `POST /api/conversations/{conversationId}/messages`
3. Example request JSON, if there is a request body: No request body is required for the identity-carrying match routes. Message send keeps the current body:

```json
{
  "senderId": "11111111-1111-1111-1111-111111111111",
  "content": "Hey, want to grab coffee this week?"
}
```

4. Example response JSON:

```json
{
  "isMatch": true,
  "message": "It's a match!",
  "match": {
    "matchId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
    "otherUserId": "22222222-2222-2222-2222-222222222222",
    "otherUserName": "Dana",
    "state": "ACTIVE",
    "createdAt": "2026-04-25T10:15:00Z"
  }
}
```

Current conversation summary shape:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
    "otherUserId": "22222222-2222-2222-2222-222222222222",
    "otherUserName": "Dana",
    "messageCount": 3,
    "lastMessageAt": "2026-04-25T10:20:00Z"
  }
]
```

5. Guaranteed fields:
   - `POST /like/{targetId}` always returns `isMatch` and `message`.
   - When `isMatch` is `true`, `match` is present and contains `matchId`, `otherUserId`, `otherUserName`, `state`, and `createdAt`.
   - `GET /matches` returns `matches`, `totalCount`, `offset`, `limit`, and `hasMore`.
   - Each current conversation row returns `id`, `otherUserId`, `otherUserName`, `messageCount`, and `lastMessageAt`.
6. Nullability and enum rules:
   - In `LikeResponse`, `match` is `null` when `isMatch` is `false`.
   - In match summaries, `state` is the current `Match.MatchState` string: `ACTIVE`, `FRIENDS`, `UNMATCHED`, `GRACEFUL_EXIT`, or `BLOCKED`.
   - In conversation summaries, `lastMessageAt` may be `null` if the conversation record exists before any message is saved.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - `matchId` and `conversationId` are the same deterministic normalized pair id: `<lower-uuid>_<higher-uuid>`.
   - There is no separate conversation UUID namespace today.
   - `GET /api/users/{id}/conversations` returns the conversation id under field name `id`, not `conversationId`.
   - The frontend may safely use `matchId` anywhere a `conversationId` is required.
   - `POST /api/conversations/{matchId}/messages` is valid even before the thread appears in `GET /api/users/{id}/conversations`; the first successful message creates the stored conversation record if the match is still messageable.
   - For `/api/conversations/{conversationId}/*`, the acting user must be one of the two UUIDs encoded in that id.
8. Availability: already live.

## 2. Person summary media and context

1. Status: Implemented (backend completed)
2. Exact endpoint path and method:
   - `GET /api/users/{id}/browse`
   - `GET /api/users/{id}/matches`
   - `GET /api/users/{id}/pending-likers`
   - `GET /api/users/{id}/standouts`

The contract decision is additive enrichment of the existing list endpoints. I am not introducing a separate batch summary endpoint in Stage A.

3. Example request JSON, if there is a request body: No request body.
4. Example response JSON:

Target `GET /api/users/{id}/browse` contract:

```json
{
  "candidates": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "name": "Maya",
      "age": 29,
      "state": "ACTIVE",
      "primaryPhotoUrl": "/photos/maya-1.jpg",
      "photoUrls": [
        "/photos/maya-1.jpg",
        "/photos/maya-2.jpg"
      ],
      "approximateLocation": "Tel Aviv",
      "summaryLine": "Designer, coffee walks, weekend hikes"
    }
  ],
  "dailyPick": {
    "userId": "44444444-4444-4444-4444-444444444444",
    "userName": "Noa",
    "userAge": 30,
    "date": "2026-04-25",
    "reason": "Lives nearby!",
    "alreadySeen": false,
    "primaryPhotoUrl": "/photos/noa-1.jpg",
    "photoUrls": [
      "/photos/noa-1.jpg"
    ],
    "approximateLocation": "Ramat Gan",
    "summaryLine": "Product designer, sunrise runs"
  },
  "dailyPickViewed": false,
  "locationMissing": false
}
```

Target additive fields on `matches`, `pending-likers`, and `standouts` are the same four summary fields:

```json
{
  "primaryPhotoUrl": "/photos/maya-1.jpg",
  "photoUrls": ["/photos/maya-1.jpg"],
  "approximateLocation": "Tel Aviv",
  "summaryLine": "Designer, coffee walks, weekend hikes"
}
```

5. Guaranteed fields:
   - Existing identity fields remain canonical and unchanged for each endpoint.
   - Each person-like item on the endpoints above will gain `primaryPhotoUrl`, `photoUrls`, `approximateLocation`, and `summaryLine`.
   - `dailyPick` remains nullable at the wrapper level, as it is today.
6. Nullability and enum rules:
   - `photoUrls` will always be present and will always be an array.
   - `primaryPhotoUrl` will always be present and may be `null`.
   - When `primaryPhotoUrl` is non-null, it is selected from `photoUrls` and is guaranteed to appear in that array.
   - `primaryPhotoUrl` is the authoritative primary display image when both fields exist; do not infer the primary image from `photoUrls[0]`.
   - `photoUrls` may be empty only when `primaryPhotoUrl` is `null`.
   - `approximateLocation` will always be present and may be `null`.
   - `summaryLine` will always be present and may be `null`.
   - `state` remains the current `User.UserState` string when that field already exists on the row.
   - This item does not add recommendation-explanation fields such as `reasonTags`; those belong to items 3 and 4.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - Browse candidates keep `id` as the user id.
   - Daily picks keep `userId`.
   - Pending likers keep `userId`.
   - Standouts keep `standoutUserId`.
   - Match summaries keep `matchId` plus `otherUserId`; no new summary-only ids will be introduced.
8. Availability: target date 2026-05-01.

## 3. Match quality

1. Status: Exists now
2. Exact endpoint path and method: `GET /api/users/{id}/match-quality/{matchId}`
3. Example request JSON, if there is a request body: No request body.
4. Example response JSON:

```json
{
  "matchId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
  "perspectiveUserId": "11111111-1111-1111-1111-111111111111",
  "otherUserId": "22222222-2222-2222-2222-222222222222",
  "compatibilityScore": 85,
  "compatibilityLabel": "Great Match",
  "starDisplay": "⭐⭐⭐⭐",
  "paceSyncLevel": "Good Sync",
  "distanceKm": 12.4,
  "ageDifference": 2,
  "highlights": [
    "Lives nearby (12.4 km away)",
    "You both enjoy Hiking",
    "Great communication sync"
  ]
}
```

5. Guaranteed fields:
   - `matchId`
   - `perspectiveUserId`
   - `otherUserId`
   - `compatibilityScore`
   - `compatibilityLabel`
   - `starDisplay`
   - `paceSyncLevel`
   - `distanceKm`
   - `ageDifference`
   - `highlights`
6. Nullability and enum rules:
   - All scalar fields above are non-null.
   - `compatibilityScore` is guaranteed to be an integer in the inclusive range `0..100`.
   - `compatibilityLabel` is currently one of `Excellent Match`, `Great Match`, `Good Match`, `Fair Match`, or `Low Compatibility`.
   - `paceSyncLevel` is currently one of `Perfect Sync`, `Good Sync`, `Fair Sync`, `Pace Lag`, or `Mismatched Pace`.
   - `starDisplay` is a display-ready star string generated by the backend.
   - `distanceKm` is numeric and may be `-1.0` when distance is unknown.
   - `ageDifference` is numeric and never negative.
   - `highlights` is always an array, is display-ready backend copy, and is capped at 5 items.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - `matchId` must belong to the `{id}` user or the route returns `403`.
   - The route returns `404` when the match id is not found.
   - The current response does not include `conversationId`; use item 1's contract because `conversationId == matchId`.
   - Current backend behavior does not reject by match state. If the stored match still exists, the route can return quality for `ACTIVE`, `FRIENDS`, `UNMATCHED`, `GRACEFUL_EXIT`, or `BLOCKED` matches.
8. Availability: already live.

## 4. Presentation context

1. Status: Implemented (backend completed)
2. Exact endpoint path and method: `GET /api/users/{viewerId}/presentation-context/{targetId}`
3. Example request JSON, if there is a request body: No request body.
4. Example response JSON:

```json
{
  "viewerUserId": "11111111-1111-1111-1111-111111111111",
  "targetUserId": "33333333-3333-3333-3333-333333333333",
  "summary": "Shown because this profile is nearby and overlaps with your current preferences.",
  "reasonTags": [
    "shared_interests",
    "nearby",
    "same_relationship_goals"
  ],
  "details": [
    "You both list Hiking and Coffee as interests.",
    "This profile is within your preferred distance.",
    "You are both looking for a long-term relationship."
  ],
  "generatedAt": "2026-05-08T10:15:00Z"
}
```

5. Guaranteed fields:
   - `viewerUserId`
   - `targetUserId`
   - `summary`
   - `reasonTags`
   - `details`
   - `generatedAt`
6. Nullability and enum rules:
   - All six fields above are guaranteed to be present.
   - `summary` is always non-null and is display-ready backend copy.
   - `reasonTags` is always an array and every entry is one of:
     - `shared_interests`
     - `nearby`
     - `age_compatible`
     - `compatible_lifestyle`
     - `same_relationship_goals`
     - `daily_pick`
     - `standout`
     - `eligible_match_pool`
     - `fallback`
   - `details` is always an array of display-ready backend strings.
   - When the backend has only sparse reasoning, it will still return a non-empty `summary` and at least one fallback-style tag.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - `{viewerId}` is the current user whose recommendation context is being evaluated.
   - `{targetId}` is the candidate user being explained.
   - This route does not take `matchId` or `conversationId`.
   - The intended contract is `404` if the target user is not currently visible/explainable for the viewer because the user does not exist, is deleted, is blocked, or is outside the current eligible surface.
8. Availability: target date 2026-05-08.

## 5. Profile edit read model

1. Status: Implemented (backend completed)
2. Exact endpoint path and method: `GET /api/users/{id}/profile-edit-snapshot`
3. Example request JSON, if there is a request body: No request body.
4. Example response JSON:

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "editable": {
    "bio": "Runner, coffee person, and weekend hiker.",
    "birthDate": "1996-07-18",
    "gender": "FEMALE",
    "interestedIn": ["MALE"],
    "maxDistanceKm": 25,
    "minAge": 27,
    "maxAge": 38,
    "heightCm": 168,
    "smoking": "NEVER",
    "drinking": "SOCIALLY",
    "wantsKids": "OPEN",
    "lookingFor": "LONG_TERM",
    "education": "BACHELORS",
    "interests": [
      "COFFEE",
      "HIKING",
      "TRAVEL"
    ],
    "dealbreakers": {
      "acceptableSmoking": ["NEVER"],
      "acceptableDrinking": [],
      "acceptableKidsStance": ["OPEN", "SOMEDAY"],
      "acceptableLookingFor": ["LONG_TERM", "MARRIAGE"],
      "acceptableEducation": ["BACHELORS", "MASTERS"],
      "minHeightCm": null,
      "maxHeightCm": null,
      "maxAgeDifference": 6
    },
    "location": {
      "label": "Tel Aviv, Tel Aviv District",
      "latitude": 32.0853,
      "longitude": 34.7818,
      "precision": "CITY",
      "countryCode": "IL",
      "cityName": "Tel Aviv",
      "zipCode": null,
      "approximate": false
    }
  },
  "readOnly": {
    "name": "Dana",
    "state": "ACTIVE",
    "photoUrls": [
      "/photos/dana-1.jpg",
      "/photos/dana-2.jpg"
    ],
    "verified": true,
    "verificationMethod": "EMAIL",
    "verifiedAt": "2026-04-24T08:30:00Z"
  }
}
```

5. Guaranteed fields:
   - Top-level: `userId`, `editable`, `readOnly`.
   - `editable` mirrors the current `PUT /api/users/{id}/profile` write contract: `bio`, `birthDate`, `gender`, `interestedIn`, `maxDistanceKm`, `minAge`, `maxAge`, `heightCm`, `smoking`, `drinking`, `wantsKids`, `lookingFor`, `education`, `interests`, `dealbreakers`, and `location`.
   - `readOnly` includes the current profile values that the mobile edit UI needs for prefilling but must not write via `PUT /profile`: `name`, `state`, `photoUrls`, `verified`, `verificationMethod`, and `verifiedAt`.
6. Nullability and enum rules:
   - `interestedIn`, `interests`, and all dealbreaker list fields are always arrays and are never `null`.
   - `dealbreakers` is always present. Empty-set dealbreakers are represented as empty arrays plus nullable scalar bounds.
   - `location` is nullable. When present, `latitude`, `longitude`, `precision`, `label`, and `countryCode` are guaranteed.
   - `zipCode` is nullable and is only set when the resolved location is ZIP-based.
   - `precision` uses `LocationModels.Precision`: `ADDRESS`, `CITY`, or `ZIP`.
   - `gender` uses `User.Gender`: `MALE`, `FEMALE`, or `OTHER`.
   - `state` uses `User.UserState`: `INCOMPLETE`, `ACTIVE`, `PAUSED`, or `BANNED`.
   - `verificationMethod` uses `User.VerificationMethod`: `EMAIL` or `PHONE`, and may be `null`.
   - Lifestyle enums use the current backend values:
     - `smoking`: `NEVER`, `SOMETIMES`, `REGULARLY`
     - `drinking`: `NEVER`, `SOCIALLY`, `REGULARLY`
     - `wantsKids`: `NO`, `OPEN`, `SOMEDAY`, `HAS_KIDS`
     - `lookingFor`: `CASUAL`, `SHORT_TERM`, `LONG_TERM`, `MARRIAGE`, `UNSURE`
     - `education`: `HIGH_SCHOOL`, `SOME_COLLEGE`, `BACHELORS`, `MASTERS`, `PHD`, `TRADE_SCHOOL`, `OTHER`
   - `countryCode` is currently only guaranteed for the supported selectable country set. Today that means `IL`.
   - Important current write-side rule: on `PUT /api/users/{id}/profile`, omitting a field or sending it as `null` does not clear stored server state; it means "leave unchanged". The current backend has no explicit clear-field contract for nullable profile fields.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - `{id}` is the current user id only. This is a self-snapshot route, not a public profile route.
   - This route does not involve `matchId`, `conversationId`, or target-user ids.
8. Availability: target date 2026-05-01.

## 6. Notification schema

1. Status: Implemented (backend completed)
2. Exact endpoint path and method: `GET /api/users/{id}/notifications`
3. Example request JSON, if there is a request body: No request body.
4. Example response JSON:

```json
[
  {
    "id": "55555555-5555-5555-5555-555555555555",
    "type": "MATCH_FOUND",
    "title": "New Match!",
    "message": "You have a new match!",
    "createdAt": "2026-04-25T10:15:00Z",
    "isRead": false,
    "data": {
      "matchId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
      "conversationId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
      "otherUserId": "22222222-2222-2222-2222-222222222222"
    }
  },
  {
    "id": "66666666-6666-6666-6666-666666666666",
    "type": "NEW_MESSAGE",
    "title": "New Message",
    "message": "Someone sent you a new message.",
    "createdAt": "2026-04-25T10:16:00Z",
    "isRead": false,
    "data": {
      "conversationId": "11111111-1111-1111-1111-111111111111_22222222-2222-2222-2222-222222222222",
      "senderId": "22222222-2222-2222-2222-222222222222",
      "messageId": "77777777-7777-7777-7777-777777777777"
    }
  }
]
```

Notification type registry for the stabilized contract:

| Type                      | Required data keys                                         | Optional data keys | Intended destination     |
|---------------------------|------------------------------------------------------------|--------------------|--------------------------|
| `MATCH_FOUND`             | `matchId`, `conversationId`, `otherUserId`                 | none               | match row or chat thread |
| `NEW_MESSAGE`             | `conversationId`, `senderId`, `messageId`                  | none               | chat thread              |
| `FRIEND_REQUEST`          | `requestId`, `fromUserId`, `matchId`                       | none               | friend-request sheet     |
| `FRIEND_REQUEST_ACCEPTED` | `requestId`, `accepterUserId`, `matchId`, `conversationId` | none               | match/chat               |
| `GRACEFUL_EXIT`           | `initiatorId`, `matchId`                                   | `conversationId`   | match/history state      |

5. Guaranteed fields:
   - Every notification row returns `id`, `type`, `title`, `message`, `createdAt`, `isRead`, and `data`.
   - `data` is always present.
   - `type` is the canonical routing key.
   - The table above is the Stage A type registry the frontend should build against once the schema stabilization lands.
6. Nullability and enum rules:
   - `data` is always a string-to-string map and is never `null`.
   - `type` is one of `MATCH_FOUND`, `NEW_MESSAGE`, `FRIEND_REQUEST`, `FRIEND_REQUEST_ACCEPTED`, or `GRACEFUL_EXIT`.
   - Unknown future types must be rendered with `title` and `message` only and must not trigger a deep link.
   - Current backend code already has a live notification endpoint, but the data-key contract is not yet stable enough to treat as frontend-safe for deep links. In particular, the current backend does not consistently include `conversationId`, and one current type name is emitted with more than one data shape.
7. Identity notes, especially matchId, conversationId, userId, target ids:
   - Notification row `id` is the notification id.
   - `{id}` in the route is the owning user id.
   - `matchId` and `conversationId` follow item 1's identity rule.
   - All ids inside `data` are serialized as strings.
8. Availability: `GET /api/users/{id}/notifications` is already live; the stabilized type/data contract above targets 2026-05-01.

## P1 / P2 Status Notes

- P1 `Stats and achievements semantics`: partially supported today by `GET /api/users/{id}/stats` and `GET /api/users/{id}/achievements`, but not in the richer grouped/canonical shapes the new UI wants. This needs a follow-up contract.
- P1 `Conversations`: easy additive. `ConnectionService.ConversationPreview` already carries `lastMessage` and `unreadCount`; the current REST adapter drops them and only returns `id`, `otherUserId`, `otherUserName`, `messageCount`, and `lastMessageAt`.
- P1 `Hide action`: deferred. I found no backend hide/unhide route or persisted hide-state contract.
- P1 `Verification resend/cooldown`: deferred. I found no resend endpoint and no `canResend`, `resendAvailableAt`, or cooldown metadata in the current verification REST contract.
- P2 `Safety action acks`: partially supported but not standardized. `block`, `unmatch`, `graceful-exit`, and `report` already return bodies, while `unblock` returns `204`. This is an easy cleanup, not a hard blocker.
- P2 `Wrapped vs raw list shapes`: mixed today. `matches`, `pending-likers`, `standouts`, `blocked-users`, and `achievements` are wrapped; `notifications`, `conversations`, and `messages` are raw lists.
- P2 `Dev-only behavior`: not cleanly separated today. `StartVerificationResponse` currently includes `devVerificationCode`, and the manager note about keeping dev-only behavior clearly separated is valid follow-up work.

## Frontend Build Guidance Right Now

- Safe to build now against current live backend: match-to-chat identity, match-quality screen wiring, current stats endpoint consumption, current achievements endpoint consumption, and current notification row shell without deep-link guarantees.
- Safe to wire after frontend DTO update: person-summary enrichment, presentation-context, profile-edit snapshot, and stabilized notification deep-link schema.
- Still blocked until follow-up backend work: richer grouped stats, canonical achievement details, hide/unhide, verification resend/cooldown, and optional conversation-summary enrichment.
