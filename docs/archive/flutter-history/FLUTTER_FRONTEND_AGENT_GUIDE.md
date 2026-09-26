# Flutter Frontend Agent Guide

This document is the source-of-truth handoff for building the separate Flutter mobile frontend for this dating app. Copy it into the new Flutter project if you want the mobile workspace to start with the right product, platform, and API assumptions.

## 1. Mission

Build a new Flutter frontend for an existing Java backend. The mobile app is a separate project, not a rewrite of the server.

The Flutter app should be:

- Android first
- Later iOS-capable from the same codebase
- REST/JSON only
- Thin-client oriented
- Focused on UI, navigation, local session state, and HTTP orchestration

The backend remains the authority for all business rules, matching logic, messaging rules, safety rules, verification, stats, and storage.

## 2. Product Definition

This is a dating app.

The frontend should feel like a dating app, not a generic CRUD app. The main product surfaces are discovery, match management, chat, profile viewing/editing, and trust and safety.

The app should support the real current product, not an idealized one. The mobile frontend is expected to reflect the current backend behavior, including its current limitations.

## 3. Hard Constraints

Do not build any of the following into the Flutter app as if they already exist:

- Server-side business logic reimplementation
- Shared domain code with the Java backend
- Kotlin or Jetpack Compose migration work
- Real signup flow
- Real production auth flow
- JWT or BCrypt login
- WebSocket chat
- Offline-first synchronization
- Push notifications as a blocking dependency for v0.1
- Cloud deployment as a prerequisite

The current first mobile loop is:

1. Select a dev user
2. Browse candidates
3. Like or pass
4. See matches
5. Open a conversation
6. Send messages

## 4. What The Backend Already Provides

The backend already supports the core product domains a dating app needs:

- User profiles
- Candidate browsing and ranking
- Like and pass actions
- Mutual match creation
- Conversations and messages
- Relationship transitions like unmatch, block, and graceful exit
- Verification flows
- Stats and achievements
- Notifications
- Standouts and daily pick
- Profile notes
- Location search and resolve

Important current reality:

- The current location stack is effectively Israel-only for fully supported selectable data
- There is no REST signup/create-account route right now
- The dev login is a user picker backed by `GET /api/users`
- Polling is acceptable for chat in the first mobile phase

## 5. Runtime And Development Rules

The mobile app talks to the Java backend over HTTP. For local LAN development, the server is started separately and the phone points at the laptop's LAN IP.

Required request behavior:

- `GET /api/health` does not require the shared secret
- All other non-local requests should send `X-DatingApp-Shared-Secret`
- User-scoped requests should also send `X-User-Id`
- Do not scatter header logic across screens
- Centralize headers in one API client/interceptor layer

The backend currently expects plain HTTP over LAN during development, so Android cleartext HTTP may need to be allowed in app configuration.

The phone should use the backend's response codes and error body shape instead of guessing local behavior.

## 6. Recommended Flutter Stack

Use the stack already implied by the project roadmap unless there is a strong reason not to:

- Flutter
- Dart
- Material 3
- Riverpod for state management
- Dio for HTTP

Good starter dependencies:

- `flutter_riverpod`
- `dio`
- `shared_preferences`
- `go_router`
- `intl`

Likely later or optional dependencies:

- `cached_network_image`
- `flutter_card_swiper`
- `freezed`
- `json_serializable`

Keep the first version straightforward. Do not front-load tooling or architecture complexity that does not help the first usable mobile loop.

## 7. Suggested Project Shape

Recommended structure for the new Flutter project:

```text
lib/
  main.dart
  app/
    app.dart
    app_config.dart
    env.dart
  api/
    api_client.dart
    api_endpoints.dart
    api_error.dart
    api_headers.dart
  features/
    auth/
      dev_user_picker_screen.dart
      selected_user_store.dart
      selected_user_provider.dart
    browse/
    matches/
    chat/
    profile/
    safety/
    settings/
    stats/
    notifications/
  models/
  shared/
    widgets/
    formatting/
    result/
  theme/
    app_theme.dart
    app_colors.dart
test/
```

Good boundaries:

- Keep the API client thin
- Keep screen widgets mostly declarative
- Keep state transitions in Riverpod providers or notifiers
- Keep local persistence limited to the selected dev user and configuration

## 8. Screen And Feature Map

### 8.1 v0.1 core loop

These screens are the minimum viable app.

| Screen          | Purpose                                 | Notes                                   |
|-----------------|-----------------------------------------|-----------------------------------------|
| Dev user picker | Select the acting user                  | This is the first screen, not real auth |
| Browse          | Show candidate cards and swipe actions  | Must support like and pass              |
| Matches         | Show mutual matches                     | Use the server as the source of truth   |
| Conversations   | Show available chats                    | Conversation list is thin right now     |
| Chat            | Show a message thread and send messages | Polling is acceptable if it is modest   |

### 8.2 v0.2 and later product areas

| Area                   | Purpose                                   | Notes                                                   |
|------------------------|-------------------------------------------|---------------------------------------------------------|
| Profile view           | Show profile details for self and others  | Use rich profile data when available                    |
| Profile edit           | Edit the current user profile             | May need a richer read path than the current detail DTO |
| Daily pick             | Highlight one featured profile            | Already present in browse response                      |
| Standouts              | Surface high-ranked candidates            | Good later discovery feature                            |
| Undo                   | Undo the last swipe                       | Already exists in the backend                           |
| Match quality          | Surface compatibility or score data       | Nice enhancement after the core loop                    |
| Safety                 | Block, report, unmatch, graceful exit     | Trust and safety should be visible and easy to reach    |
| Verification           | Start and confirm verification            | Dev code flow exists now                                |
| Notifications          | Show match/message related notifications  | Not required for the first loop                         |
| Stats and achievements | Show progress and engagement              | Read-only UI is enough initially                        |
| Preferences            | Theme, notifications, and app preferences | Keep it simple at first                                 |
| Profile notes          | Moderation or admin notes                 | Likely hidden from normal users                         |

### 8.3 Navigation guidance

A bottom navigation shell is a good default for a mobile dating app, but do not overfit the exact shell too early.

Good primary destinations are:

- Browse
- Matches
- Chat or conversations
- Profile or More

Secondary screens can be pushed from those destinations.

## 9. API Contract Cheat Sheet

### 9.1 Health and startup

| Endpoint          | Purpose                          | Notes                     |
|-------------------|----------------------------------|---------------------------|
| `GET /api/health` | Confirm the backend is reachable | No shared secret required |

### 9.2 Identity and session

| Endpoint         | Purpose         | Notes                          |
|------------------|-----------------|--------------------------------|
| `GET /api/users` | Dev user picker | Use existing users, not signup |

### 9.3 Browse and match flow

| Endpoint                                        | Purpose                       | Notes                   |
|-------------------------------------------------|-------------------------------|-------------------------|
| `GET /api/users/{id}/browse`                    | Get candidates and daily pick | Preferred browse route  |
| `POST /api/users/{id}/like/{targetId}`          | Like a candidate              | Can return a match      |
| `POST /api/users/{id}/pass/{targetId}`          | Pass a candidate              | Simple success response |
| `POST /api/users/{id}/undo`                     | Undo last swipe               | Already implemented     |
| `GET /api/users/{id}/matches?limit=20&offset=0` | List matches                  | Paginated               |
| `GET /api/users/{id}/standouts`                 | Show featured candidates      | Later feature           |
| `GET /api/users/{id}/pending-likers`            | Show pending likers           | Later feature           |
| `GET /api/users/{id}/match-quality/{matchId}`   | Show match quality details    | Later enhancement       |

### 9.4 Conversations and chat

| Endpoint                                                             | Purpose            | Notes                                  |
|----------------------------------------------------------------------|--------------------|----------------------------------------|
| `GET /api/users/{id}/conversations?limit=50&offset=0`                | List conversations | Thin summary data                      |
| `GET /api/conversations/{conversationId}/messages?limit=50&offset=0` | Load a thread      | This is the real message route         |
| `POST /api/conversations/{conversationId}/messages`                  | Send a message     | `senderId` must match the current user |

Important rule:

- Chat is conversation-scoped, not nested under a user-scoped messages route
- Do not build the frontend around a fake `/api/users/{id}/conversations/{id}/messages` path

### 9.5 Profile and location

| Endpoint                                            | Purpose             | Notes                                        |
|-----------------------------------------------------|---------------------|----------------------------------------------|
| `GET /api/users/{id}`                               | Read profile detail | Richer than the browse list                  |
| `PUT /api/users/{id}/profile`                       | Update profile      | Write payload is richer than read payload    |
| `GET /api/location/countries`                       | Load countries      | Only some are currently fully available      |
| `GET /api/location/cities?countryCode=IL&query=...` | Search cities       | Location UX should be server-driven          |
| `POST /api/location/resolve`                        | Resolve location    | Use this for label and coordinate resolution |

### 9.6 Social, safety, and verification

| Endpoint                                                      | Purpose                    | Notes                                             |
|---------------------------------------------------------------|----------------------------|---------------------------------------------------|
| `GET /api/users/{id}/notifications`                           | List notifications         | Later feature                                     |
| `POST /api/users/{id}/notifications/read-all`                 | Mark all read              | Later feature                                     |
| `POST /api/users/{id}/notifications/{notificationId}/read`    | Mark one read              | Later feature                                     |
| `POST /api/users/{id}/friend-requests/{targetId}`             | Send friend request        | Later social feature                              |
| `POST /api/users/{id}/block/{targetId}`                       | Block a user               | Important safety action                           |
| `DELETE /api/users/{id}/block/{targetId}`                     | Unblock a user             | Safety management                                 |
| `POST /api/users/{id}/report/{targetId}`                      | Report a user              | Safety management                                 |
| `POST /api/users/{id}/relationships/{targetId}/graceful-exit` | End a relationship cleanly | Later social flow                                 |
| `POST /api/users/{id}/relationships/{targetId}/unmatch`       | Unmatch                    | Later social flow                                 |
| `POST /api/users/{id}/verification/start`                     | Start verification         | Dev flow currently returns a code                 |
| `POST /api/users/{id}/verification/confirm`                   | Confirm verification       | Keep the UI ready for a future real delivery flow |

### 9.7 Stats and notes

| Endpoint                                         | Purpose            | Notes                       |
|--------------------------------------------------|--------------------|-----------------------------|
| `GET /api/users/{id}/stats`                      | Show user stats    | Read-only                   |
| `GET /api/users/{id}/achievements`               | Show achievements  | Read-only                   |
| `GET /api/users/{authorId}/notes`                | Load profile notes | Moderation or admin surface |
| `GET /api/users/{authorId}/notes/{subjectId}`    | Load one note      | Moderation or admin surface |
| `PUT /api/users/{authorId}/notes/{subjectId}`    | Save a note        | Moderation or admin surface |
| `DELETE /api/users/{authorId}/notes/{subjectId}` | Delete a note      | Moderation or admin surface |

## 10. Current Backend Caveats The Flutter App Must Respect

These are important and easy to get wrong if you only look at an idealized roadmap.

### 10.1 Browse candidates are thin

Current browse items do not include the full profile payload. Do not assume browse cards already have rich photos, bios, or all profile fields.

If the browse UI needs richer cards later, that is a backend-enrichment follow-up, not something the Flutter app should invent client-side.

### 10.2 Conversation summaries are thin

The conversation list is also minimal. Do not assume a preview string, avatar, or rich summary exists unless the backend provides it.

### 10.3 Profile read and write payloads are not symmetric

The profile edit payload is richer than the profile detail payload. Profile editing may require sourcing state from more than one endpoint or a future richer read DTO.

### 10.4 Location support is not global yet

Only Israel is fully supported for selectable location data right now. The app should not present global location coverage as if it is already complete.

### 10.5 Browse requires an active user

Inactive users can hit conflict behavior. Surface this cleanly in the UI.

### 10.6 Polling must be gentle

Chat polling is acceptable, but do not be aggressive. Respect the server rate limit and keep polling scoped to the visible screen.

## 11. Data And State Model Cheat Sheet

Treat server data as the source of truth. The app should mirror the backend contract and keep local state minimal.

Useful data shapes to expect:

- User summary: id, name, age, state
- User detail: id, name, age, bio, gender, interestedIn, approximateLocation, maxDistanceKm, photoUrls, state
- Browse response: candidates, dailyPick, dailyPickViewed, locationMissing
- Like response: isMatch, message, match
- Match summary: matchId, otherUserId, otherUserName, state, createdAt
- Conversation summary: id, otherUserId, otherUserName, messageCount, lastMessageAt
- Message: id, conversationId, senderId, content, sentAt
- Error: code, message
- Health: status, timestamp

Use these as read models in Flutter. Keep them small and serializable.

## 12. UI And UX Rules

The app should feel intentional and mobile-native.

Recommended UI direction:

- Use Material 3, but do not accept the default Flutter look as the final design
- Favor strong visual hierarchy and large touch targets
- Build around cards, profiles, and clear actions
- Make the like and pass actions obvious and one-handed
- Use clean loading states, empty states, and error states everywhere
- Keep animations subtle and meaningful, not decorative noise

Practical UX rules:

- Always provide a fallback button for swipe actions
- Always show an explicit retry action on recoverable failures
- Always preserve the selected dev user between launches
- Avoid burying the core browse/match/chat loop behind too many taps
- Avoid desktop-style layouts on a phone

## 13. State Management And Data Flow

Use Riverpod as the ViewModel equivalent.

Suggested pattern:

- A single app config provider for base URL and shared secret
- A selected-user provider backed by local persistence
- An API client provider with central request headers and error mapping
- Feature providers or notifiers for browse, matches, chat, profile, and settings

Data flow should be:

1. Screen loads
2. Provider fetches from the API
3. UI renders loading, empty, data, or error states
4. User actions update state through the provider
5. API responses remain the source of truth

Avoid putting business logic in widgets. Keep widgets presentational and keep request orchestration in providers or repositories.

## 14. Error Handling Rules

The backend returns structured JSON errors. The Flutter app should parse and surface them consistently.

Expected error shape:

```json
{
  "code": "BAD_REQUEST",
  "message": "Human-readable message"
}
```

Important status codes:

- 400 BAD_REQUEST
- 403 FORBIDDEN
- 404 NOT_FOUND
- 409 CONFLICT
- 429 TOO_MANY_REQUESTS
- 500 INTERNAL_ERROR

The app should:

- Show the server message when possible
- Map common network failures to friendly retry states
- Treat 429 as a real rate-limit signal, not a silent failure
- Treat 403 or 409 as meaningful state, not just a toast

## 15. Implementation Order For The New Flutter Project

Build the app in a tight sequence:

### Step 1: App shell

Create:

- App config
- Base URL config
- Shared secret config
- Selected-user persistence
- Dio client with interceptors

### Step 2: Dev user picker

Build:

- `GET /api/users`
- Select and persist the acting user
- Continue into the app shell

### Step 3: Browse

Build:

- `GET /api/users/{id}/browse`
- Like and pass actions
- Loading, empty, error, and retry states
- A simple card-based presentation that respects the current thin DTOs

### Step 4: Matches and conversations

Build:

- Match list
- Conversation list
- Navigation into chat threads

### Step 5: Chat

Build:

- Message thread view
- Send message action
- Modest polling or refresh strategy

### Step 6: Profile and polish

Add:

- Profile view
- Profile edit
- Daily pick
- Undo
- Standouts
- Match quality

### Step 7: Safety and later features

Add:

- Block and report
- Verification
- Notifications
- Stats and achievements
- Preferences
- Onboarding / profile completion

## 16. Testing And Validation

At minimum, the Flutter project should have:

- Unit tests for DTO parsing and API error mapping
- Widget tests for the main screens and state transitions
- A manual smoke flow on a real Android device or emulator

Recommended smoke flow:

1. Start the backend
2. Confirm `/api/health`
3. Pick a dev user
4. Browse candidates
5. Like or pass one profile
6. Open matches
7. Open a conversation
8. Send a message

For later phases, add tests for profile editing, safety actions, and verification.

## 17. What To Preserve

When you build the Flutter frontend, preserve these principles:

- The backend owns the rules
- The client owns presentation and flow
- The app starts with an existing user, not signup
- The UI should stay honest about missing backend richness
- The mobile app should remain separate from the Java backend

If a feature feels tempting to fake in Flutter, stop and check whether the backend already owns it or whether it needs a backend follow-up first.