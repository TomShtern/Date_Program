# UI Overhaul Backend Handoff

> Audience: Java/PostgreSQL backend engineer
> Date: 2026-04-25
> Purpose: define the backend contract decisions and additive API work needed so the Flutter UI overhaul can ship without inventing backend-owned product logic in Dart.

---

## Backend Response Needed

Please send a written contract response before the frontend wires new DTOs, API client methods, or data-rich screens.

Expected response artifact:

- Create a Markdown file with the backend answers.
- Preferred frontend-repo path if the response is committed here: `docs/2026-04-25-ui-overhaul-backend-response.md`
- Acceptable backend-repo path if the response belongs with backend work: `docs/frontend-ui-overhaul-contract-response-2026-04-25.md`
- The file should be committed or otherwise shared as a versioned review artifact, not only sent as an informal chat message.

The first response is **not** expected to include completed backend code. It should tell the manager and frontend which contracts already exist, which ones will be added, and which ones are deferred.

Answer the six immediate P0 items first, using this format for each item:

1. **Status:** Exists now / Will add / Deferred
2. **Exact endpoint path and method**
3. **Example request JSON** if the request has a body
4. **Example response JSON**
5. **Guaranteed fields**
6. **Nullability and enum rules**
7. **Identity notes:** especially `matchId`, `conversationId`, `userId`, and target ids
8. **Availability:** already live, target date, or explicitly deferred

The frontend does not need backend implementation details. It does need stable paths, fields, semantics, and examples before wiring DTOs and screens.

### Response stages

1. **Stage A — contract answer, due first**
   - Answer the six immediate P0 items below.
   - Mark each one as `Exists now`, `Will add`, or `Deferred`.
   - Provide sample JSON for anything that exists now or will be added.
   - Put the answers in the Markdown response file described above.
   - This stage unblocks frontend planning and tells the frontend which rich surfaces can be integrated later.

2. **Stage B — backend implementation or confirmation**
   - Implement the items marked `Will add`.
   - Confirm exact payloads after implementation if they changed from the Stage A examples.
   - Provide stable dev/seed data for frontend verification.

3. **Stage C — integration verification**
   - Backend and frontend verify that real responses match the agreed shapes.
   - Frontend then wires DTOs/screens against the confirmed contracts.

---

## Context

The Flutter app is intentionally a thin client.

Flutter owns:

- UI, navigation, local state, request orchestration, and presentation

The backend owns:

- matching and recommendation logic
- match/recommendation explanations
- conversation membership and persistence
- moderation, block, report, unmatch, and hide rules
- verification rules and state
- stats, achievements, notifications, and persistence
- location resolution and supported geography

The approved UI overhaul is people-first, photo-driven, more compact, and more explanatory. It includes richer surfaces such as `Why we match`, `Why this profile is shown`, grouped stats, achievement details, stronger notifications, and better profile editing.

The frontend must not fake compatibility reasons, recommendation reasons, moderation state, analytics semantics, or achievement meaning. If the UI needs richer data, the right fix is usually an additive backend contract, not a Dart workaround.

---

## Immediate P0 Contract Decisions

These are the decisions that matter most before the frontend executes the core data-rich parts of the overhaul.

| # | Area                             | Current frontend state                                                                                | Backend decision or change needed                                                                                                                     | Frontend impact if missing                                                             |
|---|----------------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| 1 | Match to conversation identity   | The current UI treats `matchId` as the conversation id when opening chat from matches or a new match. | Confirm that `matchId == conversationId`, or return a real `conversationId` from like and matches payloads.                                           | Correctness risk. `Message now` can open the wrong thread or fail.                     |
| 2 | Person summary media and context | Summary DTOs are too thin for photo-first cards.                                                      | Enrich browse, daily pick, matches, standouts, and pending likers with photo and compact context fields, or provide a batch profile-summary endpoint. | Core people cards can be laid out, but premium photo/context treatment is blocked.     |
| 3 | Match quality                    | No live frontend API method or model exists for match-quality.                                        | Add `GET /api/users/{id}/match-quality/{matchId}` or equivalent.                                                                                      | `Why we match` must remain blocked. It must not be inferred in Dart.                   |
| 4 | Presentation context             | No live presentation-context contract exists.                                                         | Add `GET /api/users/{viewerId}/presentation-context/{targetId}` or enrich browse/profile payloads with equivalent fields.                             | `Why this profile is shown` must remain blocked.                                       |
| 5 | Profile edit read model          | `GET /api/users/{id}` is thinner than the backend write-side profile contract.                        | Enrich current-user detail or add `GET /api/users/{id}/profile-edit-snapshot`.                                                                        | A complete edit form cannot prefill reliably.                                          |
| 6 | Notification schema              | `NotificationItem.data` is only a generic string map in Flutter.                                      | Provide a registry of notification `type` values and guaranteed `data` keys per type.                                                                 | Notification layout can ship, but deep links and quick actions must stay conservative. |

---

## P1 / P2 Improvements

These improve quality and reduce frontend fallback logic, but they should not block unrelated layout work.

| Priority | Area                             | Backend ask                                                                                                                                                                                                                      |
|----------|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P1       | Stats and achievements semantics | Provide grouped/typed stats and one stable achievement shape. Detail-rich stats and achievement sheets remain limited until this is answered.                                                                                    |
| P1       | Conversations                    | Add `otherUserPhotoUrl`, `lastMessagePreview`, `unreadCount`, and optionally `lastSenderId`.                                                                                                                                     |
| P1       | Hide action                      | Decide whether `Hide` is persistent backend state, intentionally local-only temporary behavior, or deferred. If persistent, add endpoints such as `POST /api/users/{viewerId}/hide/{targetId}` and optional unhide/undo support. |
| P1       | Verification resend/cooldown     | Add resend support or response metadata such as `canResend`, `resendAvailableAt`, and `cooldownSeconds`.                                                                                                                         |
| P2       | Safety action acks               | Standardize `block`, `unblock`, `report`, and `unmatch` success bodies, for example `{ "success": true, "message": "User blocked successfully" }`.                                                                               |
| P2       | Wrapped vs raw list shapes       | Standardize canonical response wrappers for endpoints where Flutter currently tolerates shape drift, especially achievements, pending likers, and blocked users.                                                                 |
| P2       | Dev-only behavior                | Keep `GET /api/users` dev-picker behavior and `devVerificationCode` explicitly separated from production behavior.                                                                                                               |

---

## Current Frontend Contract Evidence

This section is the current Flutter-side contract as verified from the live frontend code on 2026-04-25.

### Headers

From `lib/api/api_headers.dart`:

- `X-DatingApp-Shared-Secret` is sent on every request except `GET /api/health`.
- `X-User-Id` is sent when the path starts with `/api/users/` or `/api/conversations/` and a user id is available.

Please keep this stable or document exceptions before frontend integration.

### Pagination

The live frontend uses `limit` and `offset` for:

- `GET /api/users/{id}/matches`
- `GET /api/users/{id}/conversations`
- `GET /api/conversations/{conversationId}/messages`

### Media URLs

The current media helper accepts:

- absolute URLs
- relative URLs resolved against the configured API base URL

Either is workable, but the rule should be consistent and the URLs must be reachable from Android emulator and mobile devices.

### Live Endpoints Exposed In Flutter

- `GET /api/health`
- `GET /api/users`
- `GET /api/users/{id}`
- `PUT /api/users/{id}/profile`
- `GET /api/users/{id}/browse`
- `POST /api/users/{id}/like/{targetId}`
- `POST /api/users/{id}/pass/{targetId}`
- `POST /api/users/{id}/undo`
- `GET /api/users/{id}/matches`
- `GET /api/users/{id}/pending-likers`
- `GET /api/users/{id}/standouts`
- `GET /api/users/{id}/conversations`
- `GET /api/conversations/{conversationId}/messages`
- `POST /api/conversations/{conversationId}/messages`
- `GET /api/users/{id}/stats`
- `GET /api/users/{id}/achievements`
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

No live Flutter API method currently exists for:

- match-quality
- presentation-context
- hide/unhide
- verification resend
- profile-edit snapshot

---

## Current DTO Gaps

| Flutter model         | Current fields                                                                                                     | Main gap                                                                             |
|-----------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `BrowseCandidate`     | `id`, `name`, `age`, `state`                                                                                       | no photo, location, reason, or compact context                                       |
| `DailyPick`           | `userId`, `userName`, `userAge`, `date`, `reason`, `alreadySeen`                                                   | no photo or location; reason is too thin for a premium tile                          |
| `MatchSummary`        | `matchId`, `otherUserId`, `otherUserName`, `state`, `createdAt`                                                    | no photo, conversation id, reason preview, or compatibility data                     |
| `ConversationSummary` | `id`, `otherUserId`, `otherUserName`, `messageCount`, `lastMessageAt`                                              | no photo, last-message preview, unread count, or sender cue                          |
| `PendingLiker`        | `userId`, `name`, `age`, `likedAt`                                                                                 | no photo or compact context                                                          |
| `Standout`            | `id`, `standoutUserId`, `standoutUserName`, `standoutUserAge`, `rank`, `score`, `reason`, timestamps               | no photo, location, or typed reason category                                         |
| `UserDetail`          | `id`, `name`, `age`, `bio`, `gender`, `interestedIn`, `approximateLocation`, `maxDistanceKm`, `photoUrls`, `state` | useful for profile pages, but not enough for list screens without N+1 detail fetches |
| `UserStats`           | flattened `label/value` items from arbitrary JSON                                                                  | loses grouping, type, trend, unit, and detail semantics                              |
| `AchievementSummary`  | fuzzy title/subtitle/progress/unlocked parsing                                                                     | lacks stable id, category, canonical shape, and detail semantics                     |
| `NotificationItem`    | `id`, `type`, `title`, `message`, `createdAt`, `isRead`, `data: Map<String, String>`                               | no documented type-to-data schema for routing or quick actions                       |

### Profile Edit Read/Write Asymmetry

Live Flutter currently sends these profile update fields:

- `bio`
- `gender`
- `interestedIn`
- `maxDistanceKm`
- `minAge`
- `maxAge`
- `heightCm`
- nested `location`

Older frontend/backend docs also describe a larger backend write contract, including:

- `birthDate`
- latitude / longitude
- `smoking`
- `drinking`
- `wantsKids`
- `lookingFor`
- `education`
- `interests`
- `dealbreakers`

The backend should confirm the real current write contract and provide a read model that mirrors the meaningful editable state. If some write fields should remain unsupported in the mobile UI for now, say that explicitly.

---

## Recommended Payload Contracts

These shapes are recommendations. Equivalent shapes are fine if they are stable, documented, and server-driven.

### 1. Person Summary Enrichment

Keep each endpoint's existing identity fields unless we coordinate a broader rename. For example, existing payloads currently use fields such as `id`, `userId`, `name`, `userName`, `otherUserName`, `standoutUserName`, and `standoutUserId`.

Minimum useful additive fields for list/person cards:

```json
{
  "primaryPhotoUrl": "/photos/dana-1.jpg",
  "photoUrls": ["/photos/dana-1.jpg"],
  "approximateLocation": "Tel Aviv",
  "summaryLine": "Designer, coffee walks, weekend hikes"
}
```

Ideal optional fields when backend-owned and safe to expose:

```json
{
  "reasonSummary": "Nearby and shares several interests",
  "reasonTags": ["nearby", "shared interests", "active recently"],
  "state": "ACTIVE"
}
```

Rules to confirm:

- Which existing id/name fields should remain canonical for each endpoint?
- Can `primaryPhotoUrl` be null or absent?
- Is `photoUrls` always an array?
- Should frontend prefer `primaryPhotoUrl` over the first `photoUrls` item?
- Is `primaryPhotoUrl` guaranteed to appear in `photoUrls` when it is non-null?
- Can `primaryPhotoUrl` be independent of `photoUrls`, or is it always selected from that array?
- Can `photoUrls` be empty while `primaryPhotoUrl` is set?
- Are reason fields display-ready, or should they be treated as internal/debug data?

Backend rule now confirmed for the Stage B implementation: `photoUrls` is always present as an array, and
`primaryPhotoUrl` is always either `null` or selected from that same `photoUrls` array. The frontend should treat
`primaryPhotoUrl` as the authoritative primary display image when both fields exist; `photoUrls[0]` is only the first
gallery item and may be a placeholder or otherwise not the first real profile image. `photoUrls` may be empty only when
`primaryPhotoUrl` is `null`; it must not be empty while `primaryPhotoUrl` is set.

### 2. Match Quality

Suggested endpoint:

- `GET /api/users/{id}/match-quality/{matchId}`

Suggested response:

```json
{
  "matchId": "match-123",
  "conversationId": "conversation-123",
  "summaryScore": 85,
  "primaryFactors": [
    {
      "key": "shared_interests",
      "label": "Shared interest in hiking",
      "weight": "high"
    }
  ],
  "secondaryFactors": [
    {
      "key": "proximity",
      "label": "Both near Tel Aviv",
      "weight": "medium"
    }
  ],
  "lastCalculatedAt": "2026-04-25T10:15:00Z"
}
```

Rules to confirm:

- `summaryScore` range and nullability
- allowed `weight` values
- whether labels are safe for direct display
- what response is returned for expired, blocked, unmatched, or missing matches

### 3. Presentation Context

Suggested endpoint:

- `GET /api/users/{viewerId}/presentation-context/{targetId}`

Suggested response:

```json
{
  "targetUserId": "user-456",
  "summary": "Shown because you share interests and are nearby.",
  "reasonTags": ["shared interests", "nearby", "active recently"],
  "details": [
    "You both list hiking and coffee as interests.",
    "This profile is within your preferred distance."
  ],
  "generatedAt": "2026-04-25T10:15:00Z"
}
```

Rules to confirm:

- whether this is a separate endpoint or embedded in browse/profile responses
- whether details are safe display copy
- what happens if the profile is shown for sparse or fallback reasons

### 4. Profile Edit Snapshot

Suggested endpoint:

- `GET /api/users/{id}/profile-edit-snapshot`

Alternative:

- enrich `GET /api/users/{id}` when the acting user requests their own profile

The response should include every meaningful editable field the backend expects the mobile app to preserve or edit. It should distinguish:

- public display fields
- matching preferences
- location fields
- optional advanced filters
- server-owned or read-only fields

Rules to confirm:

- which fields can be omitted from update without clearing server state
- which fields can be explicitly set to null or empty
- valid enum values for all choice fields

### 5. Stats

Suggested response:

```json
{
  "lastUpdated": "2026-04-25T10:15:00Z",
  "groups": [
    {
      "key": "attraction",
      "label": "Attraction",
      "items": [
        {
          "key": "profile_views",
          "label": "Profile views",
          "value": 124,
          "displayValue": "124",
          "unit": null,
          "detail": "How many times your profile was opened"
        }
      ]
    }
  ]
}
```

Rules to confirm:

- stable group keys
- stable stat keys
- value type and display formatting
- whether trends, ranges, or comparisons are available

### 6. Achievements

Suggested item shape:

```json
{
  "id": "first_match",
  "category": "matching",
  "title": "First match",
  "subtitle": "Make your first mutual match",
  "description": "Unlocked when you and another user like each other.",
  "progress": {
    "current": 1,
    "target": 1,
    "displayValue": "1 / 1"
  },
  "isUnlocked": true,
  "unlockedAt": "2026-04-25T10:15:00Z",
  "rewardLabel": null
}
```

Rules to confirm:

- canonical wrapper shape, for example `{ "achievements": [...] }`
- stable id and category values
- whether locked/in-progress/unlocked achievements all appear in the same list

### 7. Notifications

Please provide a type registry, not only sample rows.

Suggested registry format. The rows below are examples only; replace them with the actual backend notification types and data keys.

| Type      | Required data keys           | Optional data keys | Destination               | Quick actions          |
|-----------|------------------------------|--------------------|---------------------------|------------------------|
| `MATCH`   | `matchId`, `otherUserId`     | `conversationId`   | match/chat                | message, view profile  |
| `MESSAGE` | `conversationId`, `senderId` | `messageId`        | conversation thread       | open thread, mark read |
| `LIKE`    | `otherUserId`                | `likeId`           | profile or pending likers | view profile           |

Current Flutter can parse notification `data` only as string key/value pairs, so ids should be representable as strings.

Rules to confirm:

- canonical type names
- guaranteed data keys per type
- route target for each type
- whether quick actions are allowed from notification rows
- what fallback behavior frontend should use for unknown types

---

## Seed Data Needed For Frontend Verification

The frontend visual-review workflow depends on realistic data. Please provide or preserve deterministic dev data for:

- users with no photo, one photo, and multiple photos
- active browse candidates with varied states
- a daily pick with photo/context
- mutual matches with known `matchId` and `conversationId`
- at least one newly created match path from like response
- pending likers with different recency values
- standouts with varied ranks, scores, and reasons
- conversations with enough messages to test scrolling and previews
- notifications across multiple documented types, read and unread
- stats with values in several groups
- achievements in locked, in-progress, and unlocked states
- blocked users
- verification dev flow
- at least one inactive, blocked, or conflict-style user case

---

## Suggested Backend Rollout Order

1. Confirm or fix match-to-conversation identity.
2. Enrich person-summary/list payloads with photo and compact context.
3. Add match-quality support.
4. Add presentation-context support.
5. Resolve profile-edit read/write asymmetry.
6. Document notification type/data schemas.
7. Improve stats and achievements structures.
8. Decide and implement hide behavior if desired.
9. Add optional conversation-summary enrichment.
10. Add optional verification resend/cooldown support.
11. Clean up wrapper/response consistency.

---

## What Is Not Backend Work

These are frontend responsibilities and should not block backend review:

- graphite/silver/ink-blue theme changes
- removing duplicate bottom shell chrome
- replacing shield icons with overflow menus
- denser layouts and shared UI components
- using `flagEmoji` in location UI
- most conversation-thread polish
- most blocked-users polish

---

## Final Takeaway

The Flutter overhaul can build the new layout shell without all backend additions, but the richest surfaces must wait for real server data.

Do not ask the frontend to infer:

- compatibility
- recommendation reasons
- notification routing semantics
- stat meaning
- achievement meaning
- moderation or hide state

If the backend provides the P0 decisions and stable payloads above, the frontend can implement the approved UI honestly and without degrading the product direction to fit today's thin DTOs.
