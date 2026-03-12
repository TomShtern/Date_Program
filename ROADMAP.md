# Dating App — Project State & Development Roadmap

> **Last verified:** 2026-03-12 — Full codebase read (247 Java files, all layers).
> **Source of truth:** Code only. This document reflects what was found in the source.
> Re-verify specific claims against code before acting on them — things may have changed.

---

## Table of Contents

1. [Development Philosophy](#1-development-philosophy)
2. [Current Reality — What the Audit Found](#2-current-reality)
3. [Why It Feels Like a Draft (The Real Reasons)](#3-why-it-feels-like-a-draft)
4. [The Correct Development Order](#4-the-correct-development-order)
5. [Phase 1 — Finish & Polish (Current Priority)](#5-phase-1--finish--polish-current-priority)
6. [Phase 2 — Code Quality Review](#6-phase-2--code-quality-review)
7. [Phase 3 — Multi-User Networking (Future)](#7-phase-3--multi-user-networking-future)
8. [Phase 4 — Kotlin / Android Port (Later)](#8-phase-4--kotlin--android-port-later)
9. [Android Phone Preview Right Now](#9-android-phone-preview-right-now)
10. [Infrastructure & Free Tier Stack (When Ready)](#10-infrastructure--free-tier-stack-when-ready)
11. [Notes for AI Coding Agents](#11-notes-for-ai-coding-agents)

---

## 1. Development Philosophy

**The rule: do not add complexity to an unfinished foundation.**

The correct order is:
1. Finish what exists — make the single-machine app actually work and feel complete
2. Review and clean the code — before adding anything new
3. Test every flow end-to-end — find and fix real issues
4. Then, and only then, add the networking layer
5. Then prepare for Kotlin/Android

Skipping steps creates compounding debt. A networking layer built on top of unpolished,
untested code is twice as hard to debug. Clean the foundation first.

---

## 2. Current Reality

### What the App Does (Single-Machine, Verified from Code)

The app is a **complete single-machine dating app**. Every feature below is implemented
in production-quality code — no stubs, no placeholder methods, zero TODO/FIXME markers
found anywhere in the codebase. The limitation is purely operational: all users share
one embedded H2 database in the same JVM on one machine.

| Feature                      | Status           | Notes                                                                                   |
|------------------------------|------------------|-----------------------------------------------------------------------------------------|
| **User Profiles**            | Complete         | Bio, photos, interests (44 types/6 categories), lifestyle, pace, dealbreakers, location |
| **Profile Completion**       | Complete         | 5-tier scoring: Starter→Bronze→Silver→Gold→Diamond                                      |
| **Candidate Browsing**       | Complete         | Filter by gender, age, distance (Haversine), dealbreakers. 5-min TTL cache              |
| **Compatibility Scoring**    | Complete         | 6 dimensions (age, interests/Jaccard, lifestyle, distance, pace, response). 0–100 score |
| **Swiping**                  | Complete         | Like/pass, daily limits, atomic mutual match creation, concurrent-swipe guard           |
| **Undo**                     | Complete         | 15-second atomic undo window, deletes like + match                                      |
| **Daily Pick**               | Complete         | One featured profile/day, seeded-random, deterministic per user+day                     |
| **Standouts**                | Complete         | Top 10 ranked candidates, diversity filtering (excludes past 3 days)                    |
| **Matches**                  | Complete         | Paginated list, match quality (16-field report, star ratings, highlights)               |
| **Messaging**                | Complete         | Conversation threads, pagination, mark-as-read, unread counts (1000 char limit)         |
| **Relationship Transitions** | Complete         | Friend-zone, graceful exit, unmatch, block — enforced by state machine                  |
| **Trust & Safety**           | Complete         | Block, report, auto-ban on threshold, bidirectional block check                         |
| **Verification**             | Complete (logic) | 6-digit code, 15-min TTL — **delivery is simulated**, prints to console                 |
| **Achievements**             | Complete         | 11 achievements across 4 categories, XP points                                          |
| **Stats & Metrics**          | Complete         | Swipe counts, like ratio, match rate, reciprocity, selectiveness, attractiveness        |
| **Notifications**            | Complete         | Match/message/transition notifications in DB, polling-only delivery                     |
| **Profile Notes**            | Complete         | Private author-only notes on other users (max 500 chars)                                |
| **JavaFX UI**                | Complete         | 14 screens, dark/light theme (full CSS system), animations, skeleton loaders            |
| **CLI**                      | Complete         | 21 menu options, full feature parity with GUI                                           |
| **REST API**                 | Partial          | ~12 of ~35 needed routes exist; 5 bypass the use-case layer                             |
| **Tests**                    | 117 files        | 60% JaCoCo line coverage gate enforced in `mvn verify`                                  |

### Key Technical Facts (read from code)

- Java 25 + preview features enabled everywhere (compiler, surefire, exec:exec)
- JDBI for database access — no ORM, row mappers are manual and explicit
- HikariCP connection pool — `DatabaseManager` is a singleton
- `AppClock` abstraction for testable time — **never use `Instant.now()` in domain code**
- `ViewModelAsyncScope` — all ViewModel background work must go through this
- `InProcessAppEventBus` — synchronous domain event dispatching (SwipeRecorded, MatchCreated, etc.)
- Spotless (Palantir Java Format) + PMD + Checkstyle + JaCoCo all enforced in `mvn verify`
- **CSS:** `theme.css` is a full dark design system (~27k tokens). `light-theme.css` is its override.
  Colors: deep slate palette, `#667eea`/`#764ba2` purple gradient primary, Inter font, drop shadows.
- **Dev data:** 30 users seeded (`DevDataSeeder`), all with complete profiles clustered around
  Tel Aviv coordinates. **No photos assigned** — intentional placeholder behavior.

---

## 3. Why It Feels Like a Draft

The code is more complete than it appears. The "draft" feeling comes from three specific
developer-experience problems — not from incomplete code.

### Problem 1 — No Photos in Dev Data (highest impact)
`DevDataSeeder` creates 30 complete user profiles but assigns zero photos. A dating app
without photos is functionally broken from a user experience perspective. The matching
screen, standouts, profile view — all look empty and hollow. This is the single biggest
reason the app feels unreal during development.

**Fix:** Add photo URLs to the seeder using a free placeholder service (`picsum.photos`
returns stable images by seed, no API key needed). Each test user gets 2–3 photo URLs.

### Problem 2 — Location Requires Raw Lat/Lon
During profile setup, the user is asked to type raw latitude/longitude numbers
(e.g., `32.0740`, `34.7925`). Nobody knows their coordinates. This breaks the
distance-based matching experience because nobody ever fills in location during testing.

**Fix:** Geocoding service — replace the two number fields with an address text field
and a search button. Call Nominatim (OpenStreetMap, free, no key required) to convert
address → lat/lon. This is purely client-side and can be done right now with no
networking or server changes needed.

### Problem 3 — Login Is a User-List Picker
The login screen shows a scrollable list of user names to pick from. This immediately
signals "developer scaffolding" rather than "app." It's the first thing you see when
you open the application.

**Note:** This should NOT be fixed right now — fixing it properly requires auth (JWT +
BCrypt), which is a Phase 3 task. For now, accept it as a dev tool. When Phase 3 begins,
the login screen gets a proper email + password form.

---

## 4. The Correct Development Order

```
PHASE 1  Finish & Polish              ← CURRENT — fix dev experience, test everything
PHASE 2  Code Quality Review          ← clean before adding anything new
PHASE 3  Multi-User Networking        ← PostgreSQL, auth, REST API, HTTP client
PHASE 4  Kotlin / Android Port        ← only after Phase 3 is stable

ANDROID PREVIEW (right now, parallel to Phase 1):
  Install Scrcpy — mirror laptop screen to phone. 5 minutes. Free.
```

**The most important rule:** Do not start Phase 3 until Phase 1 and 2 are complete.
Adding a networking layer on top of untested, unpolished code doubles the debugging cost.

---

## 5. Phase 1 — Finish & Polish (Current Priority)

These tasks make the single-machine app actually usable and testable. No server, no
networking, no new dependencies — just fixing the experience of working with the app.

---

### P1-A — Add Photos to DevDataSeeder

**What:** The seeder creates 30 users but assigns no photos. Add 2–3 photo URLs per user.

**Why:** Without photos, the matching UI looks like an empty skeleton. You can't evaluate
swipe UX, profile view, standouts, or the match popup — they all show placeholder avatars.

**How:** Use `https://picsum.photos/seed/{userId}/400/500` as the URL pattern.
`picsum.photos` serves stable, deterministic images based on a seed string — no API key,
no registration, no rate limits. Each user UUID as seed gives a consistent photo.

**File:** `src/main/java/datingapp/storage/DevDataSeeder.java`

**What to add:** After each user is saved, call the photo storage methods to assign
2–3 photo URLs per user. Example URL pattern:
```java
// For user with UUID "abc123...":
List<String> photos = List.of(
    "https://picsum.photos/seed/abc123-1/400/500",
    "https://picsum.photos/seed/abc123-2/400/500"
);
// Store via userStorage.saveUserPhotos(userId, photos)
// (method already exists in JdbiUserStorage)
```

**Gotcha:** The seeder is idempotent (has a sentinel check). Ensure photo assignment
is also idempotent — skip if photos already exist for the user.

**Tests required:** Add a test that verifies seeded users have at least 1 photo URL.

---

### P1-B — Geocoding (Address → Lat/Lon in Profile Screen)

**What:** Replace the two raw lat/lon number fields in the profile editor with a single
address text field + search button. No server required — this calls Nominatim directly
from the JavaFX client.

**Why:** Location is completely unusable during development. Nobody enters raw coordinates.
Without location, distance-based candidate filtering returns nobody, making the swipe
feature feel broken.

**Service:** Nominatim — OpenStreetMap's free geocoder. No API key, no account needed.

**New class:** `app/geo/GeocodingService.java`

```java
public interface GeocodingService {
    record LatLon(double latitude, double longitude) {}
    record GeocodingResult(LatLon coordinates, String displayName) {}

    Optional<GeocodingResult> geocode(String address) throws GeocodingException;
    Optional<GeocodingResult> reverseGeocode(double lat, double lon) throws GeocodingException;
}
// NominatimGeocodingService implements GeocodingService
```

**Nominatim rules (must follow):**
- Maximum 1 request per second — throttle with `AtomicLong lastCallMs`
- Required header: `User-Agent: DatingApp/1.0 (dev@youremail.com)`
- Endpoint: `GET https://nominatim.openstreetmap.org/search?q={address}&format=json&limit=1`

**JavaFX changes:**
- `profile.fxml`: replace lat/lon TextField pair with one address TextField + "Search" Button
  + result confirmation Label ("📍 Tel Aviv, Israel")
- `ProfileViewModel`: add `StringProperty address`, `BooleanProperty geocoding`,
  `ObjectProperty<LatLon> resolvedLocation`; call `GeocodingService.geocode()` on search
- Keep a small "Set manually" collapsible section for raw lat/lon (power-user fallback)

**Files to modify:**
- `src/main/resources/fxml/profile.fxml`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/screen/ProfileController.java`
- New: `src/main/java/datingapp/app/geo/GeocodingService.java`
- New: `src/main/java/datingapp/app/geo/NominatimGeocodingService.java`

**Note:** This is purely client-side. In Phase 3, geocoding moves server-side so the REST
API can accept an `address` field. For now, client geocodes → stores lat/lon normally.

**Tests required:** Unit test `NominatimGeocodingService` with WireMock mocking HTTP.

---

### P1-C — Run the App and Do a Real Smoke Test

**What:** This is a YOU task, not an agent task. Run `mvn javafx:run` and actually use
every screen. Note what feels broken, slow, confusing, or visually off.

**Why:** The code audit confirmed every handler is wired and every screen is complete.
But static code analysis cannot catch real UX problems. You need to actually swipe,
match, chat, change preferences — and see what breaks.

**What to test (each flow):**
1. Create a user → fill profile completely → set location (after P1-B) → activate
2. Browse candidates → like some → undo → pass others
3. Create mutual matches (two users both liking each other)
4. Open chat with a match → send messages
5. View standouts → like one → check notifications
6. Check achievements → view stats
7. Block a user → report a user → view blocked list
8. Change preferences → verify candidate list updates
9. Dark/light theme toggle

**Output:** Write down every issue you find. Those become P1-D tasks for agents.

---

### P1-D — Fix Issues Found in Smoke Test

**What:** After P1-C, create specific fix tasks based on what you actually found.
These cannot be defined in advance — they come from real testing.

**Pattern for each fix task handed to an agent:**
```
Screen: [name]
Issue: [what exactly is wrong]
Steps to reproduce: [1, 2, 3]
Expected: [what should happen]
Actual: [what happens]
```

This is more valuable than any theoretical gap analysis. Real issues found by a human
using the app beat guesses every time.

---

### P1-E — Quick Wins (Agent tasks, no dependencies)

These are small, self-contained fixes that an agent can implement any time.

#### Achievement Count Display (StatsHandler + StatsViewModel)
**File:** `src/main/java/datingapp/app/cli/StatsHandler.java` and
`src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

**Current:** Shows "Achievements unlocked: 3" with no total.
**Fix:** `Achievement.values().length` gives the total. Change to "Achievements: 3 / 11 unlocked"
in both the CLI stats display and the `StatsViewModel` achievements property.

#### Presence Indicator — Hide or Remove
**File:** `src/main/resources/fxml/chat.fxml`, `src/main/java/datingapp/ui/screen/ChatController.java`

**Current:** The chat screen shows a presence status indicator that always reads UNKNOWN
(feature-flagged off: `datingapp.ui.presence.enabled`). This looks broken to a user.

**Fix:** Either hide the presence indicator entirely from the FXML until presence is
implemented, or replace it with "Active recently" static text as a placeholder that
doesn't imply real-time status.

#### DevDataSeeder — Disable in Non-Dev Mode
**File:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`

**Current:** `DevDataSeeder.seed()` runs on every startup unconditionally.
**Fix:** Guard with an env var: only run if `DATING_APP_SEED_DATA=true`. This prevents
the seeder from running in future production deployments.
```java
if ("true".equals(System.getenv("DATING_APP_SEED_DATA"))) {
    DevDataSeeder.seed(services);
}
```

---

## 6. Phase 2 — Code Quality Review

**Do this after Phase 1 is complete and you've done the smoke test (P1-C).**

A proper code quality pass makes the codebase easier to reason about — for you, for
future agents, and for the eventual Kotlin port. Doing this before Phase 3 means you're
building the networking layer on a clean foundation.

### What "Code Quality Review" Means Here

1. **Consistency pass:** Are patterns consistent across the codebase? The code was built
   over multiple sessions. Some parts may use older patterns that were later refined.
   - All ViewModels should use `ViewModelAsyncScope` for async work (no ad-hoc threads)
   - All storage access from ViewModels should go through `UiDataAdapters` interfaces
   - All use-case access should go through `services.get*UseCases()`, never `new *UseCases(...)`

2. **Dead code removal:** Any unused methods, fields, or imports after the various
   refactors should be removed. Use the IDE's "Find Usages" or run `mvn verify` to
   catch PMD UnusedPrivateField violations.

3. **Test quality:** Do the 117 tests actually test meaningful behavior, or are some
   just checking that things don't throw? Read the test files for the most complex
   features (matching, messaging, transitions) and verify they test real scenarios.

4. **Controller size:** If any controller is over ~400 lines, it is likely doing too much.
   Presentation logic belongs in ViewModels, not controllers.

5. **Magic numbers / inline constants:** Are there numbers or strings in code that should
   be in `AppConfig` or `UiConstants`?

### How to Do It (Agent-Friendly)

Dispatch one agent per domain area with instructions to:
- Read every file in the area
- List: inconsistent patterns, dead code, oversized classes, missing test coverage,
  magic numbers
- Propose specific, minimal fixes (not rewrites)
- Do NOT refactor for its own sake — only fix things that are genuinely unclear or wrong

---

## 7. Phase 3 — Multi-User Networking (Future)

**Do not start this phase until Phases 1 and 2 are complete.**

This phase turns the single-machine app into a real networked product where users on
different machines can see and match with each other.

### The Architecture Change

```
CURRENT (all in one JVM):
JavaFX UI → ViewModelFactory → ServiceRegistry → StorageLayer → H2 embedded

TARGET (client-server):
JavaFX UI → HTTP ApiClient → REST API (Javalin) → ServiceRegistry → PostgreSQL
```

Why REST API (not remote JDBC): JDBC from client to DB is a security risk and is
incompatible with Android. The Javalin REST API already exists. `ViewModelAsyncScope`
is built for async HTTP calls. HTTP is the only path that works for the Android port.

### Dual-Mode Strategy

The JavaFX client should support both local and remote mode so development stays easy:

```
LOCAL MODE (developer):  ViewModelFactory → ServiceRegistry (current behavior)
REMOTE MODE (production): ViewModelFactory → HTTP ApiClient → REST API
Toggle via: DATING_APP_SERVER_URL env var (if set = remote, if absent = local)
```

### Phase 3 Tasks (in order)

#### P3-T1 — PostgreSQL Migration

**Why first:** H2 is single-JVM. PostgreSQL is the shared server database all clients
will connect to.

**Key SQL differences to fix (H2 → PostgreSQL):**

| H2                                                                         | PostgreSQL                                                   |
|----------------------------------------------------------------------------|--------------------------------------------------------------|
| `MERGE INTO t USING (VALUES ...) AS src ON ... WHEN MATCHED / NOT MATCHED` | `INSERT INTO t ... ON CONFLICT (key) DO UPDATE SET ...`      |
| `TIMESTAMP WITH TIME ZONE`                                                 | `TIMESTAMPTZ`                                                |
| `MERGE INTO schema_version`                                                | `INSERT INTO schema_version ... ON CONFLICT DO NOTHING`      |
| `ALTER TABLE ADD COLUMN` (H2 has same syntax)                              | `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (PostgreSQL 9.6+) |

**Keep H2 as `<scope>test</scope>`** — unit and integration tests continue using H2 in-memory.

**Maven:** Add `org.postgresql:postgresql:42.7.3`

**Files:** `DatabaseManager.java`, `SchemaInitializer.java`, `MigrationRunner.java`,
all `Jdbi*Storage.java` files (review SQL), `pom.xml`

#### P3-T2 — Authentication (JWT + BCrypt)

**What:** Real email + password login. JWT tokens. All API routes protected.

**Schema change (Migration V4):**
```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON users (email) WHERE email IS NOT NULL;
```

**New classes:**
- `app/api/auth/AuthRoutes.java` — POST /api/auth/register, POST /api/auth/login
- `app/api/auth/JwtService.java` — issue + validate JWT (HS256, secret from `JWT_SECRET` env var)
- `app/api/auth/AuthMiddleware.java` — Javalin before-handler, extracts userId from token

**Maven:** Add `org.mindrot:jbcrypt:0.4`, `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6`

**Password rules:** min 8 chars, at least one digit, max 128 chars (add to `ValidationService`)

#### P3-T3 — Complete REST API Endpoints

**What:** Add the ~23 missing routes so the JavaFX client can reach every feature over HTTP.
All routes must go through the use-case layer — never call core services or storage directly
from `RestApiServer`.

**Also fix:** 5 existing endpoints that currently bypass the use-case layer:
`GET /api/users`, `GET /api/users/{id}`, `GET /api/users/{id}/candidates`,
`GET /api/users/{id}/matches`, `GET /api/conversations/{id}/messages`

**All new routes use `ctx.attribute("userId")` (from JWT middleware) — never trust userId from path/body.**

**Missing endpoints to add:**

```
# Profile
PUT  /api/me/profile                      → ProfileUseCases.updateProfile()
PUT  /api/me/preferences                  → ProfileUseCases.updateDiscoveryPreferences()
GET  /api/me/completion                   → ProfileUseCases.calculateCompletion()
DELETE /api/me                            → ProfileUseCases.deleteAccount()

# Matching
GET  /api/me/candidates                   → MatchingUseCases.browseCandidates()
POST /api/me/like/{targetId}              → MatchingUseCases.processSwipe() LIKE
POST /api/me/pass/{targetId}              → MatchingUseCases.processSwipe() PASS
POST /api/me/undo                         → MatchingUseCases.undoSwipe()
GET  /api/me/matches                      → MatchingUseCases.listActiveMatches()
GET  /api/me/pending-likers               → MatchingUseCases.pendingLikers()
GET  /api/me/standouts                    → MatchingUseCases.standouts()
GET  /api/matches/{matchId}/quality       → MatchingUseCases.matchQuality()

# Messaging
GET  /api/me/conversations                → MessagingUseCases.listConversations()
DELETE /api/conversations/{id}            → MessagingUseCases.deleteConversation()

# Social / Safety
POST /api/me/block/{targetId}             → SocialUseCases.blockUser()
DELETE /api/me/block/{targetId}           → (unblock)
GET  /api/me/blocked                      → TrustSafetyService.getBlockedUsers()
POST /api/me/report/{targetId}            → SocialUseCases.reportUser()
POST /api/matches/{matchId}/unmatch       → SocialUseCases.unmatch()
POST /api/matches/{matchId}/friend-request → SocialUseCases.requestFriendZone()
POST /api/matches/{matchId}/graceful-exit → SocialUseCases.gracefulExit()
PATCH /api/friend-requests/{id}           → SocialUseCases.respondToFriendRequest()
GET  /api/me/friend-requests              → SocialUseCases.pendingFriendRequests()

# Notifications
GET  /api/me/notifications                → SocialUseCases.notifications()
PATCH /api/notifications/{id}/read        → SocialUseCases.markNotificationRead()
POST /api/notifications/read-all          → SocialUseCases.markAllNotificationsRead()

# Achievements & Stats
GET  /api/me/achievements                 → ProfileUseCases.getAchievements()
GET  /api/me/stats                        → ProfileUseCases.getOrComputeStats()

# Verification
POST /api/me/verification/send            → TrustSafetyService.generateVerificationCode() + EmailService
POST /api/me/verification/confirm         → TrustSafetyService.verifyCode()

# Notes
GET/PUT/DELETE /api/users/{id}/note       → ProfileUseCases note methods
```

**Standard error format:**
```json
{ "error": "VALIDATION|NOT_FOUND|CONFLICT|FORBIDDEN|UNAUTHORIZED|INTERNAL", "message": "..." }
```

**HTTP status mapping for UseCaseError:**
- VALIDATION → 422, NOT_FOUND → 404, CONFLICT → 409, FORBIDDEN → 403, INTERNAL → 500
- Auth failures (not a UseCaseError) → 401

#### P3-T4 — Photo Upload & Serving

**What:** Photos are currently local filesystem paths. They need to be server-hosted HTTP URLs.

**Two options:**
- **Option A (Cloudinary):** `com.cloudinary:cloudinary-http5:1.39.0`. Free tier: 25 GB, 25 credits/month.
- **Option B (Server filesystem):** Store in `/app/uploads/`, serve via `GET /api/photos/{filename}`.
  Zero cost if hosting on Oracle Always Free.

**New interface:** `app/api/media/PhotoService.java`
```java
public interface PhotoService {
    String upload(byte[] imageBytes, String originalFilename) throws PhotoUploadException;
    void delete(String photoUrl);
}
```

**Upload endpoint:**
```
POST /api/me/photos  (multipart/form-data, field "photo")
Response 201: { "url": "https://...", "position": 3 }
Max size: 5 MB (enforce via Javalin maxRequestSize)
Types: image/jpeg, image/png, image/webp
Max per user: AppConfig.ValidationConfig.maxPhotos
```

**JavaFX:** `ProfileViewModel.addPhoto()` POSTs file bytes to upload endpoint, receives URL,
stores it. `ImageCache` already handles HTTP URLs — no change needed there.

#### P3-T5 — JavaFX HTTP Client Layer

**What:** The JavaFX app currently calls `ServiceRegistry` directly. Replace with HTTP calls
to the REST API.

**New package: `ui/api/`**
- `ApiClient.java` — wraps `java.net.http.HttpClient` (Java 11+, no extra dep) + Jackson
- `ApiSession.java` — stores JWT token, provides it to all requests
- `datasource/` — interfaces: `MatchingDataSource`, `MessagingDataSource`, `ProfileDataSource`, `SocialDataSource`
- `local/` — implementations wrapping existing use-cases (current behavior, no change for dev)
- `remote/` — implementations making HTTP calls via `ApiClient`
- `dto/` — request/response record types per API call

**ViewModelFactory change:**
```java
// If DATING_APP_SERVER_URL env var is set → inject Remote*DataSource
// Otherwise → inject Local*DataSource (current behavior, unchanged)
```

**Each ViewModel:** change `private final MatchingUseCases` to `private final MatchingDataSource`.
Call sites are the same — just the type changes. Local implementations wrap the existing use-cases.

#### P3-T6 — Real Login Screen

**What:** Replace the user-list picker with email + password form. Add account creation.

**Files:** `LoginViewModel.java`, `LoginController.java`, `login.fxml` — full rewrite.
New files: `SignupViewModel.java`, `SignupController.java`, `signup.fxml`,
add `SIGNUP` to `NavigationService.ViewType`.

**On login:** store JWT in `ApiSession`, navigate to Dashboard.
**Token persistence:** save to `UiPreferencesStore` (or Java `Preferences` API) so users
stay logged in between app restarts.

#### P3-T7 — Real-Time Chat (WebSocket)

**What:** Replace 5–15 second polling with WebSocket push delivery.

**Server:** Javalin supports WebSocket natively (`app.ws("/ws", ...)`). Maintain a
`Map<UUID, WsContext>` of connected users. On message save, push to recipient.

**Client:** `java.net.http.WebSocket` (Java 11+, no extra dep). ChatViewModel subscribes
on connect. Falls back to polling if WebSocket fails.

**Push event types:**
- `NEW_MESSAGE` — to recipient (and sender for delivery confirmation)
- `MATCH_CREATED` — to both users
- `NOTIFICATION` — to target user

#### P3-T8 — Email Verification (SMTP)

**What:** Replace console-printed verification codes with real email delivery.

**New interface:** `app/email/EmailService.java`
- `SmtpEmailService` — production (Brevo SMTP free tier: 300 emails/day)
- `NoOpEmailService` — local dev (logs to console, current behavior)

**Maven:** `com.sun.mail:jakarta.mail:2.0.1`

**Config env vars:** `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`
**Brevo SMTP relay:** `smtp-relay.brevo.com:587`

**Wiring:** `ApplicationStartup` creates `SmtpEmailService` if SMTP config present,
otherwise `NoOpEmailService`. Passes to `TrustSafetyService`.

#### P3-T9 — Password Reset Flow

**Prerequisite:** P3-T8 (email) + P3-T2 (auth)

**New endpoints:**
- `POST /api/auth/forgot-password` — always returns 200, sends reset email if address found
- `POST /api/auth/reset-password` — validates token, sets new password hash

**Schema (Migration V5):**
```sql
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token       VARCHAR(36) PRIMARY KEY,
    user_id     UUID NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
```

#### P3-T10 — Rate Limiting & API Security

**File:** `RestApiServer.java`

- In-memory rate limiter per IP: 5 req/min for auth routes, 100 req/min general
- CORS headers for future web clients
- Security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`
- Return 429 with `Retry-After` header on limit exceeded

#### P3-T11 — Server Deployment (Docker)

**Important:** The server JAR must NOT include JavaFX — JavaFX requires a display, servers
don't have one. Add a Maven `server` profile that builds a fat JAR with only
`core/`, `storage/`, `app/` packages and excludes JavaFX.

**The three entry points remain:**
- `datingapp.Main` — CLI (dev only)
- `datingapp.ui.DatingApp` — JavaFX client
- `datingapp.app.api.RestApiServer` — Javalin server (the deployable backend)

**Free tier options:**
- [Railway](https://railway.app) — $5 credit/month (~500 hrs), best DX
- [Render](https://render.com) — 750 hrs/month (spins down after 15 min idle)
- [Oracle Cloud Always Free](https://oracle.com/cloud/free/) — 2 AMD VMs + 4 ARM cores, permanent

---

## 8. Phase 4 — Kotlin / Android Port (Later)

**Do not start until Phase 3 is stable and the REST API has been used for some months.**
The HTTP API is the contract the Android app will consume. Porting before it's stable
means porting twice.

### What Ports Well to Kotlin

| Layer                      | Portability | Notes                                                            |
|----------------------------|-------------|------------------------------------------------------------------|
| `core/model/`              | Excellent   | User, Match become Kotlin data classes. Nested enums stay.       |
| `core/matching/`           | Excellent   | Pure domain logic, no framework deps. Converts cleanly.          |
| `app/usecase/`             | Excellent   | Records → data classes. Methods → `suspend fun` with coroutines. |
| `app/event/`               | Good        | `AppEventBus` → `SharedFlow` or Kotlin EventBus.                 |
| `core/storage/` interfaces | Good        | The interfaces port; implementations don't (see below).          |

### What Does NOT Port to Android

| Layer                        | Reason                                                          |
|------------------------------|-----------------------------------------------------------------|
| `storage/jdbi/`              | JDBI is JVM-only. Android uses Room or direct SQLite.           |
| `ui/` (all JavaFX)           | JavaFX has no Android equivalent. Android uses Jetpack Compose. |
| `app/api/RestApiServer.java` | Stays in Java on the server. Android calls the HTTP API.        |
| `app/cli/`                   | Not relevant to Android.                                        |

### Recommended Migration Strategy

1. Start a fresh Android project (Kotlin + Jetpack Compose)
2. Port `core/` domain classes first — they have no framework dependencies
3. Port `app/usecase/` as Android ViewModels calling the REST API via Retrofit or Ktor
4. `ViewModelAsyncScope` pattern → `viewModelScope + coroutines`
5. `AppEventBus` → `SharedFlow` in the ViewModel layer
6. The server stays in Java. Android calls the same REST API endpoints.

---

## 9. Android Phone Preview Right Now

JavaFX doesn't run natively on Android. But you can see your app on your phone today.

### Option A — Scrcpy (5 minutes, do this now)

Mirrors your laptop screen to your Android phone over USB or WiFi. Hold your phone,
interact with the JavaFX app. Immediately feel font sizes, layout density, button targets.

```bash
# Install (Windows)
winget install Genymobile.scrcpy

# Enable USB debugging on Android phone, plug in via USB, then:
scrcpy
```

The app appears on your phone screen in real-time. You can touch it (input is forwarded
back to the laptop). This is the fastest way to evaluate the UI at phone scale.

### Option B — JPro (browser-based JavaFX)

JPro renders JavaFX apps in a web browser via WebSockets. Add one dependency, run a local
server, open `http://localhost:8080` on your phone's browser. Closest to a real mobile
feel without any code rewrite. Free for development (commercial for production).

### Option C — Real Android (future)

The Kotlin/Jetpack Compose app in Phase 4. Android Studio has excellent hot-reload
("Apply Changes") that handles most code changes without reinstalling. Physical device
and emulator both work. This is the "real" mobile experience but requires writing
the Kotlin app first.

---

## 10. Infrastructure & Free Tier Stack (When Ready for Phase 3)

| Concern                         | Service                                                    | Free Tier                    | Notes                         |
|---------------------------------|------------------------------------------------------------|------------------------------|-------------------------------|
| **Backend hosting**             | [Railway](https://railway.app)                             | $5 credit/month              | Best DX, Docker-native        |
| **Backend hosting (alt)**       | [Render](https://render.com)                               | 750 hrs/month                | Cold starts after 15 min idle |
| **Backend hosting (best free)** | [Oracle Cloud Always Free](https://oracle.com/cloud/free/) | 2 AMD + 4 ARM VMs, permanent | Most generous                 |
| **Database**                    | [Neon](https://neon.tech)                                  | 0.5 GB PostgreSQL            | Auto-pauses when idle         |
| **Database (alt)**              | [Supabase](https://supabase.com)                           | 500 MB PostgreSQL            | Pauses after 1 week inactive  |
| **Photo storage**               | [Cloudinary](https://cloudinary.com)                       | 25 GB, 25 credits/month      | Best for image hosting        |
| **Photo (alt)**                 | Server filesystem                                          | 0 cost on Oracle Always Free | No CDN, simpler               |
| **Email**                       | [Brevo](https://brevo.com)                                 | 300 emails/day               | Reliable SMTP relay           |

### Environment Variables (Phase 3 onwards)

```bash
DATING_APP_DB_URL=jdbc:postgresql://ep-xxx.neon.tech/dating_app?sslmode=require
DATING_APP_SERVER_URL=https://your-app.railway.app   # enables remote mode in JavaFX
DATING_APP_SEED_DATA=true                            # only in dev, never in production

JWT_SECRET=<minimum-32-char-random-string>
JWT_EXPIRY_DAYS=30

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USER=your_brevo_login_email
SMTP_PASSWORD=your_brevo_smtp_key
SMTP_FROM=noreply@yourdomain.com
```

---

## 11. Notes for AI Coding Agents

Read this section before touching any code.

### Source of Truth

**Code only.** Read actual source files before making changes. Do not trust this roadmap,
CLAUDE.md, or any documentation over what the code says. This document was verified on
2026-03-12 — things may have changed. Always read the file you are about to modify.

### Critical Gotchas

```
WRONG                          RIGHT
core.model.Gender              User.Gender
core.model.MatchState          Match.MatchState
Instant.now()                  AppClock.now()
a + "_" + b (pair IDs)         generateId(a, b)   ← deterministic sorted concatenation
new Thread() / Platform.runLater()  ViewModelAsyncScope methods
new MatchingUseCases(...)      services.getMatchingUseCases()
AppConfig.defaults()           injected AppConfig via ServiceRegistry
```

### Build Commands

```bash
mvn spotless:apply             # ALWAYS run before verify (formats code)
mvn verify                     # full: compile + test + spotless + PMD + checkstyle + JaCoCo
mvn test                       # quick test cycle only
mvn -Ptest-output-verbose test  # verbose test output
mvn javafx:run                 # run the JavaFX GUI
mvn compile && mvn exec:exec   # run the CLI
```

**Never run `mvn verify` multiple times with different filters.** Capture output once,
query the captured output multiple times.

### Package Rules

- **`core/` is framework-free.** No JDBI, no JavaFX, no Javalin imports in `core/`.
- **ViewModels use `ViewModelAsyncScope`.** Never create `Thread.ofVirtual()` or call
  `Platform.runLater()` directly in ViewModels.
- **Use-cases via registry.** `services.getMatchingUseCases()` — never construct directly.
- **ViewModel data access via `UiDataAdapters` interfaces** — not direct storage imports.
- **API routes via use-case layer** — `RestApiServer` never calls storage directly.

### PMD Suppressions

```java
// Empty catch block:
} catch (SomeException ignored) {
    assert true; // NOPMD EmptyCatchBlock
}
// Inline:
someCall(); // NOPMD RuleName
```

### Adding Database Columns

1. Add migration to `MigrationRunner` as new version (V4, V5, ...)
2. Do NOT modify `SchemaInitializer` — it is frozen at V1 baseline
3. Use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
4. Update JDBI storage mapper + bind methods
5. Update storage interface if new query needed

### Test Coverage Gate

JaCoCo enforces **60% line coverage** in `mvn verify`. New classes need tests or
the build fails. Use `TestStorages`, `TestClock`, `TestUserFactory`, `JavaFxTestSupport`
(existing test utilities in `src/test/java/`).

### Known Flaky Test

`ChatControllerTest#selectionTogglesChatStateAndNoteButtonsRemainWired` — fails
intermittently in full suite (JavaFX thread ordering). Passes in isolation. Pre-existing,
not caused by typical code changes. Do not investigate unless the test suite becomes
systematically unreliable.

---

*End of roadmap. Source-verified 2026-03-12. Update when architectural decisions change.*
