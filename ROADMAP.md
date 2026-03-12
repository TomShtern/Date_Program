# Dating App — Complete Project State & Development Roadmap

> **Generated:** 2026-03-12
> **Source of truth:** Verified by full codebase read (all 247 Java files across every layer).
> All claims in this document are backed by actual source, not documentation.
> This file is authoritative for human developers and AI coding agents alike.

---

## Table of Contents

1. [Current State — What the App Does Today](#1-current-state)
2. [What the App Cannot Do — Gaps](#2-what-the-app-cannot-do)
3. [The Core Architectural Problem](#3-the-core-architectural-problem)
4. [Recommended Target Architecture](#4-recommended-target-architecture)
5. [Free Tier Infrastructure Stack](#5-free-tier-infrastructure-stack)
6. [Task Breakdown](#6-task-breakdown)
   - [Tier 1 — MVP Blockers](#tier-1--mvp-blockers)
   - [Tier 2 — Real Product Feel](#tier-2--real-product-feel)
   - [Tier 3 — Small Fixes & Polish](#tier-3--small-fixes--polish)
   - [Not Appropriate Yet](#not-appropriate-yet)
7. [Implementation Order & Dependencies](#7-implementation-order--dependencies)
8. [Testing Strategy](#8-testing-strategy)
9. [The Kotlin / Android Future](#9-the-kotlin--android-future)
10. [Notes Specifically for AI Coding Agents](#10-notes-specifically-for-ai-coding-agents)

---

## 1. Current State

### What the App Does Today (Single-Machine, Verified)

The app is a **fully functional single-machine dating app demo**. Every feature below is implemented with no stubs or placeholder methods — the domain logic is production-quality. The limitation is purely architectural: all users share one embedded H2 database running in the same JVM process on one machine.

| Feature Area                 | What Works                                                                                                                                                                                           | Notes                                                              |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| **User Profiles**            | Create, edit, view. Bio, photos, interests (44 types, 6 categories), lifestyle (smoking/drinking/kids/goals/education), pace preferences, dealbreakers, location (lat/lon), age/distance preferences | EXIF validation on photos is implemented                           |
| **Profile Completion**       | 5-tier scoring (Starter → Bronze → Silver → Gold → Diamond). Points-based system with category breakdowns and improvement tips                                                                       | Min 95% for Diamond                                                |
| **Candidate Browsing**       | Filter by gender, age, distance (Haversine), dealbreakers. Sorted by distance. 5-minute TTL cache                                                                                                    | Returns `Optional.empty()` if no candidates                        |
| **Compatibility Scoring**    | 6 dimensions: age, interests (Jaccard), lifestyle, distance, pace, response time. Weighted composite score (0–100), star ratings, highlights                                                         | All weights configurable via `AppConfig`                           |
| **Swiping**                  | Like/pass with daily limits (configurable). Mutual like → automatic match creation (atomic DB transaction)                                                                                           | Concurrent swipes on same candidate are guarded                    |
| **Undo**                     | 15-second undo window after each swipe. Atomic delete of like + match if applicable                                                                                                                  | Configurable window via `AppConfig.SafetyConfig.undoWindowSeconds` |
| **Daily Pick**               | One featured profile per day per user. Seeded-random selection (deterministic by day + user hash). Reason generation (nearby, compatible, etc.)                                                      | Cached in analytics storage                                        |
| **Standouts**                | Top 10 ranked candidates with composite score, diversity filtering (excludes past 3 days), rank 1–10                                                                                                 | Configurable max via `AppConfig`                                   |
| **Matches**                  | Paginated list. Active/all/archived views. Match quality: MatchQualityService computes 16-field report with star rating and highlights                                                               |                                                                    |
| **Messaging**                | Full conversation + message thread. Pagination. Mark as read. Unread counts. 1000 char message limit                                                                                                 | 5–15s polling interval (not real-time)                             |
| **Relationship Transitions** | Friend-zone request → accept/decline (atomic). Graceful exit. Unmatch. Block. Enforced by `RelationshipWorkflowPolicy` state machine                                                                 | ACTIVE → {FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED}              |
| **Trust & Safety**           | Block, report (with reason + description). Auto-ban on configurable report threshold. Bidirectional block check                                                                                      | Threshold is `AppConfig.SafetyConfig.autoBanThreshold`             |
| **Verification**             | 6-digit code, 15-min TTL, email or phone. Code generation and validation implemented                                                                                                                 | **Delivery is simulated** — prints to console                      |
| **Achievements**             | 11 achievements across 4 categories (MATCHING, BEHAVIOR, PROFILE, SAFETY). XP points. Category grouping                                                                                              | `DefaultAchievementService` checks all criteria on demand          |
| **Metrics & Stats**          | Per-user: swipe counts, like ratio, match rate, reciprocity score, selectiveness score, attractiveness score. Platform-wide aggregates                                                               | Computed via `ActivityMetricsService`                              |
| **Notifications**            | Match, message, relationship transition notifications stored in DB. Unread tracking                                                                                                                  | No push delivery — polling only                                    |
| **Profile Notes**            | Private notes one user writes about another. Author-only visible. Max 500 chars                                                                                                                      | Used by both CLI and UI                                            |
| **JavaFX UI**                | 14 screens: Login, Dashboard, Matching (swipe), Matches, Chat, Profile (edit + read-only view), Preferences, Standouts, Social, Safety, Stats, Notes, Achievement Popup                              | Dark/light theme toggle. Animations.                               |
| **CLI**                      | 21 menu options. Full feature parity with GUI for most flows                                                                                                                                         | I18n-ready via `MatchingCliPresenter`                              |
| **REST API**                 | ~12 active routes via Javalin                                                                                                                                                                        | Incomplete — see Task 3                                            |
| **Tests**                    | 117 test files across all layers                                                                                                                                                                     | 60% JaCoCo line coverage gate enforced in `mvn verify`             |

### Key Technical Facts

- **Java 25 + preview features** enabled everywhere (compiler, surefire, exec:exec)
- **JDBI** for database access — no ORM. Row mappers are manual and explicit.
- **HikariCP** connection pool — `DatabaseManager` is a singleton
- **AppClock** abstraction for testable time — never use `Instant.now()` in domain code
- **ViewModelAsyncScope** — all ViewModel background work goes through this. Never create ad-hoc threads in ViewModels.
- **Event bus** — `InProcessAppEventBus` dispatches domain events (SwipeRecorded, MatchCreated, etc.) synchronously. Handlers are BEST_EFFORT (failures logged, not propagated).
- **Spotless** (Palantir Java Format) + **PMD** + **Checkstyle** + **JaCoCo** all run in `mvn verify`

---

## 2. What the App Cannot Do

These are the real gaps — things that prevent the app from being used by real people.

### Fundamental Blockers

| Gap                                                   | Root Cause                                                                                     | Impact                                                            |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| **Users on different machines cannot see each other** | H2 is embedded — each JVM has its own private database                                         | The entire app only works as a single-machine demo                |
| **No secure login**                                   | Login is "pick your name from a list" — no password, no session token                          | Anyone can log in as anyone                                       |
| **Photos are not shared across machines**             | Photos stored as local filesystem paths (e.g., `C:\Users\...`) — meaningless to other machines | Profile photos broken in any networked scenario                   |
| **Chat is not real-time**                             | 5–15 second polling interval. No WebSocket.                                                    | Feels laggy; database hammered with polls                         |
| **Verification does not send anything**               | `SafetyHandler` prints the code to the Java console — no email or SMS is sent                  | Verification feature is effectively non-functional for real users |
| **Location requires raw lat/lon**                     | Profile setup asks users to type latitude and longitude numbers directly                       | No normal user knows their coordinates; feature is unusable       |

### Secondary Gaps

| Gap                                                 | Root Cause                                                                             |
|-----------------------------------------------------|----------------------------------------------------------------------------------------|
| **No account recovery**                             | No password reset flow exists                                                          |
| **No real push notifications**                      | Notifications stored in DB but only retrieved by polling; no OS-level push             |
| **REST API is incomplete**                          | ~12 of ~35 needed endpoints exist; 5 of those bypass the use-case layer                |
| **No JWT / API auth**                               | REST API has no authentication — any caller can hit any route                          |
| **StatsHandler shows incomplete achievement count** | Comment in code: "I don't know total count easily" — shows unlocked only, not "X of Y" |
| **Presence always returns UNKNOWN**                 | Feature-flagged off (`datingapp.ui.presence.enabled`). No server infrastructure for it |

---

## 3. The Core Architectural Problem

```
CURRENT STATE (single machine):
┌─────────────────────────────────────────────────┐
│                   ONE JVM PROCESS               │
│                                                 │
│  JavaFX UI ──► ViewModelFactory                │
│                      │                         │
│                  ServiceRegistry               │
│                      │                         │
│               StorageLayer (JDBI)              │
│                      │                         │
│              H2 Embedded Database              │
│         (private, lives in this JVM only)      │
└─────────────────────────────────────────────────┘

User A and User B must be on THE SAME MACHINE for this to work.
There is no way around this without an architectural change.
```

```
TARGET STATE (real product):
┌───────────────────────────────┐    internet    ┌──────────────────────────────┐
│   User A's Machine            │ ◄───────────► │  Server (Railway / Fly.io)   │
│                               │               │                              │
│  JavaFX UI                    │               │  Javalin REST API            │
│      │                        │               │      │                       │
│  HTTP ApiClient               │               │  ServiceRegistry             │
│  (calls REST API over HTTP)   │               │      │                       │
│                               │               │  StorageLayer (JDBI)         │
└───────────────────────────────┘               │      │                       │
                                                │  PostgreSQL (Neon)           │
┌───────────────────────────────┐               └──────────────────────────────┘
│   User B's Machine            │ ◄───────────►
│  JavaFX UI + HTTP ApiClient   │     same server, same DB
└───────────────────────────────┘
```

**Why REST API (not remote JDBC):**

Exposing a PostgreSQL JDBC connection directly to client machines is a serious security risk (credentials in client config) and is technically incompatible with Android (Android cannot use JDBC). The Javalin REST API already exists. `ViewModelAsyncScope` is purpose-built for async HTTP calls. The HTTP API is the only viable path that also enables the future Android port.

---

## 4. Recommended Target Architecture

### Dual-Mode Design (Critical for Development Experience)

The JavaFX client should support two modes so developers can test locally without running a server:

```
LOCAL MODE (development):
  ViewModelFactory injects LocalDataSource → directly calls ServiceRegistry
  (current behavior — works today, no network needed)

REMOTE MODE (production):
  ViewModelFactory injects RemoteDataSource → calls REST API via HttpClient
  (new behavior — requires running server)

Toggle via: config file, system property, or UI setting
```

This is implemented by introducing a `*DataSource` interface layer in the client:

```
ui/api/
  ApiClient.java              — HTTP client wrapper (Java 11+ HttpClient + Jackson)
  ApiSession.java             — holds JWT token, base URL
  datasource/
    MatchingDataSource.java   — interface (same method shapes as MatchingUseCases)
    MessagingDataSource.java  — interface
    ProfileDataSource.java    — interface
    SocialDataSource.java     — interface
  local/
    LocalMatchingDataSource.java   — wraps MatchingUseCases directly
    LocalMessagingDataSource.java  — wraps MessagingUseCases directly
    ... (etc.)
  remote/
    RemoteMatchingDataSource.java  — makes HTTP calls via ApiClient
    RemoteMessagingDataSource.java — makes HTTP calls via ApiClient
    ... (etc.)
  dto/
    (request + response record types for each API call)
```

ViewModels receive a `*DataSource` interface. `ViewModelFactory` decides which implementation to inject based on mode.

### Server Project Profile (No JavaFX on Server)

The server process does not need JavaFX. JavaFX modules on a headless server cause startup issues. Add a Maven profile `server` that:
- Excludes JavaFX dependencies from the server build
- Includes only: `core/`, `storage/`, `app/` packages
- Produces a fat JAR via `maven-assembly-plugin` for deployment

The three entry points remain:
- `datingapp.Main` — CLI (developer use only)
- `datingapp.ui.DatingApp` — JavaFX GUI (client)
- `datingapp.app.api.RestApiServer` — Javalin server (the deployable backend)

---

## 5. Free Tier Infrastructure Stack

All recommended services have generous free tiers suitable for early-stage usage.

| Concern                         | Service                                                    | Free Tier Details                               | Notes                                              |
|---------------------------------|------------------------------------------------------------|-------------------------------------------------|----------------------------------------------------|
| **Backend hosting**             | [Railway](https://railway.app)                             | $5 credit/month (~500 hrs)                      | Best DX, Docker-native, env var UI                 |
| **Backend hosting (alt)**       | [Render](https://render.com)                               | 750 hrs/month (spins down after 15 min idle)    | Spins down = cold starts; fine for low traffic     |
| **Backend hosting (best free)** | [Oracle Cloud Always Free](https://oracle.com/cloud/free/) | 2 AMD micro VMs + 4 ARM Ampere cores, permanent | Most generous; requires account setup              |
| **Database**                    | [Neon](https://neon.tech)                                  | 0.5 GB PostgreSQL, 1 project, serverless        | Serverless = auto-pause when idle; good for dev    |
| **Database (alt)**              | [Supabase](https://supabase.com)                           | 500 MB PostgreSQL, pauses after 1 week inactive | More features (auth, realtime) if needed later     |
| **Photo storage**               | [Cloudinary](https://cloudinary.com)                       | 25 GB storage, 25 credits/month bandwidth       | Best option for image hosting with free transforms |
| **Photo storage (alt)**         | Server filesystem                                          | 0 cost if on Oracle Always Free                 | Store on server disk, serve via `/api/photos/`     |
| **Email**                       | [Brevo](https://brevo.com)                                 | 300 emails/day, unlimited contacts              | Reliable SMTP relay, simple setup                  |
| **Email (alt)**                 | [Mailgun](https://mailgun.com)                             | 100 emails/day                                  | Requires domain verification                       |

### Environment Variables Reference

All sensitive config must come from environment variables. Never hardcode credentials.

```bash
# Database
DATING_APP_DB_URL=jdbc:postgresql://ep-xxx.neon.tech/dating_app?sslmode=require

# Authentication
JWT_SECRET=<minimum-32-char-random-string>
JWT_EXPIRY_DAYS=30

# Photo storage (Cloudinary)
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# Email (Brevo SMTP)
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USER=your_brevo_login_email
SMTP_PASSWORD=your_brevo_smtp_key
SMTP_FROM=noreply@yourdomain.com

# App config overrides (already supported via DATING_APP_ prefix in ApplicationStartup)
DATING_APP_DAILY_LIKE_LIMIT=100
DATING_APP_SESSION_TIMEOUT_MINUTES=30
```

---

## 6. Task Breakdown

Each task is self-contained enough for an AI coding agent to implement end-to-end. Prerequisites are noted explicitly.

---

### TIER 1 — MVP Blockers

Nothing works for real users without all six of these tasks.

---

#### T1 — PostgreSQL Migration

**What:** Replace the embedded H2 database with PostgreSQL.
**Why:** H2 embedded is single-JVM. PostgreSQL runs as a standalone server that all clients connect to over a network. This is the prerequisite for everything else.

**Prerequisite:** None. Start here.

**Files to Modify:**
- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` — review all SQL
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` — review all SQL
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` — review all SQL
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java` — review all SQL
- `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java` — review all SQL
- `pom.xml`

**Maven Dependencies to Add:**
```xml
<!-- PostgreSQL JDBC driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

Keep H2 as a `<scope>test</scope>` dependency — unit/integration tests continue using H2 in-memory.

**H2 → PostgreSQL SQL Differences to Fix:**

| H2 Syntax                                                                                                 | PostgreSQL Equivalent                                                                                                              |
|-----------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `MERGE INTO t USING (VALUES ...) AS src ON ... WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT` | `INSERT INTO t (...) VALUES (...) ON CONFLICT (key) DO UPDATE SET ...`                                                             |
| `TIMESTAMP WITH TIME ZONE`                                                                                | `TIMESTAMPTZ` (functionally identical, just rename)                                                                                |
| `UUID()` (H2 function)                                                                                    | `gen_random_uuid()` (requires `pgcrypto` extension, or use application-generated UUIDs — already the case)                         |
| `BOOLEAN`                                                                                                 | `BOOLEAN` (same)                                                                                                                   |
| `MERGE INTO schema_version ...` (migration tracking)                                                      | `INSERT INTO schema_version ... ON CONFLICT DO NOTHING`                                                                            |
| H2's `IF NOT EXISTS` on columns                                                                           | PostgreSQL requires: check `information_schema.columns` first, or use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (PostgreSQL 9.6+) |

**Key Change in DatabaseManager:**
```java
// BEFORE (current): reads env var DATING_APP_DB_URL or defaults to H2 embedded
// AFTER: if DATING_APP_DB_URL starts with "jdbc:postgresql:", use PG driver
// Keep H2 fallback for test environments where DATING_APP_DB_URL is not set
private static String resolveJdbcUrl() {
    String envUrl = System.getenv("DATING_APP_DB_URL");
    if (envUrl != null && !envUrl.isBlank()) return envUrl;
    return "jdbc:h2:./dating_app_db;DB_CLOSE_DELAY=-1"; // test/dev fallback
}
```

**PostgreSQL-specific config for HikariCP:**
```java
config.setDriverClassName("org.postgresql.Driver");
config.addDataSourceProperty("sslmode", "require"); // for Neon
config.addDataSourceProperty("ApplicationName", "DatingApp");
config.setMaximumPoolSize(10); // Neon free tier has connection limits
config.setMinimumIdle(2);
```

**Testing:** All existing storage tests must pass against H2. Add one integration test that verifies the schema creates cleanly on PostgreSQL (can be a CI-only test controlled by env var).

---

#### T2 — Authentication System (JWT + BCrypt)

**What:** Replace "pick user from list" with real email + password authentication. Issue JWT tokens. Protect all API routes.

**Prerequisite:** T1 (needs PostgreSQL in place, though H2 works for the schema change).

**Schema Change — Migration V4:**
```sql
-- Add to MigrationRunner as V4
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
-- email column already exists; add unique constraint:
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique ON users (email) WHERE email IS NOT NULL;
```

**Maven Dependencies to Add:**
```xml
<!-- BCrypt (no Spring needed) -->
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**New Classes to Create:**
```
app/api/auth/
  AuthRoutes.java     — POST /api/auth/register, POST /api/auth/login
  JwtService.java     — issue + validate JWT tokens
  AuthMiddleware.java — Javalin before-handler: extract token, populate ctx.attribute("userId")
```

**JwtService contract:**
```java
public class JwtService {
    // Generate a signed JWT containing the userId as "sub" claim
    public String issueToken(UUID userId);
    // Validate signature + expiry; return userId or throw AuthException
    public UUID validateToken(String token) throws AuthException;
}
// Token expiry: 30 days (configurable via JWT_EXPIRY_DAYS env var)
// Algorithm: HS256 with secret from JWT_SECRET env var (min 32 chars)
```

**Register / Login API:**
```
POST /api/auth/register
Body: { "name": "Alice", "email": "alice@example.com", "password": "..." }
Success 201: { "token": "<jwt>", "userId": "<uuid>", "name": "Alice" }
Error 409: { "error": "CONFLICT", "message": "Email already registered" }
Error 422: { "error": "VALIDATION", "message": "Password too short" }

POST /api/auth/login
Body: { "email": "alice@example.com", "password": "..." }
Success 200: { "token": "<jwt>", "userId": "<uuid>", "name": "Alice" }
Error 401: { "error": "UNAUTHORIZED", "message": "Invalid credentials" }
```

**Password Rules (enforced by ValidationService — add there):**
- Minimum 8 characters
- Must contain at least one digit
- Maximum 128 characters

**UserStorage changes:**
```java
// Add to UserStorage interface:
Optional<User> findByEmail(String email);
void savePasswordHash(UUID userId, String passwordHash);
Optional<String> getPasswordHash(UUID userId);
```

**AuthMiddleware pattern:**
```java
// Javalin before("/api/*", ctx -> {
//   skip paths: /api/auth/*, /api/health
//   extract header: Authorization: Bearer <token>
//   validate via JwtService
//   ctx.attribute("userId", resolvedUUID)
// });
```

All existing API route handlers read `ctx.attribute("userId")` to get the authenticated user ID. This replaces the current pattern of reading user ID from path params without verification.

---

#### T3 — Complete REST API Endpoints

**What:** Add ~23 missing endpoints so the JavaFX client can access every app feature over HTTP. Fix the 5 endpoints that currently bypass the use-case layer.

**Prerequisite:** T2 (auth middleware must be in place before these routes go live).

**All routes use `ctx.attribute("userId")` for the authenticated user — never trust a userId from path/body.**

**Endpoint Specifications:**

```
# Profile
PUT  /api/me/profile
     Body: { bio?, birthDate?, gender?, heightCm?, location?: {lat, lon}?,
             smoking?, drinking?, wantsKids?, lookingFor?, education?,
             messagingFrequency?, timeToFirstDate?, communicationStyle?,
             depthPreference? }
     Use-case: ProfileUseCases.updateProfile()

PUT  /api/me/preferences
     Body: { minAge?, maxAge?, maxDistanceKm?, interestedIn?: ["MALE","FEMALE","OTHER"] }
     Use-case: ProfileUseCases.updateDiscoveryPreferences()

GET  /api/me/completion
     Response: { score: int, tier: string, breakdown: {...}, nextSteps: [...] }
     Use-case: ProfileUseCases.calculateCompletion()

DELETE /api/me
     Use-case: ProfileUseCases.deleteAccount()

# Matching
GET  /api/me/candidates
     Response: { candidates: [User], dailyPick: User?, locationMissing: bool }
     Use-case: MatchingUseCases.browseCandidates()

POST /api/me/like/{targetId}
     Body: { superLike?: bool }
     Response: { match?: Match, matchQuality?: MatchQuality }
     Use-case: MatchingUseCases.processSwipe() with LIKE direction

POST /api/me/pass/{targetId}
     Use-case: MatchingUseCases.processSwipe() with PASS direction

POST /api/me/undo
     Response: { success: bool, message: string }
     Use-case: MatchingUseCases.undoSwipe()

GET  /api/me/matches
     Query: ?offset=0&limit=20&activeOnly=true
     Response: { matches: [MatchWithUser], total: int }
     Use-case: MatchingUseCases.listActiveMatches()

GET  /api/me/pending-likers
     Response: { likers: [PendingLiker] }
     Use-case: MatchingUseCases.pendingLikers()

GET  /api/me/standouts
     Response: { standouts: [StandoutWithUser], total: int }
     Use-case: MatchingUseCases.standouts()

GET  /api/me/daily-pick
     Response: { pick: User?, alreadySeen: bool, reason: string? }
     Use-case: MatchingUseCases.browseCandidates() — daily pick is embedded in response

GET  /api/matches/{matchId}/quality
     Response: MatchQuality (full 16-field record)
     Use-case: MatchingUseCases.matchQuality()

# Messaging
GET  /api/me/conversations
     Query: ?offset=0&limit=20
     Response: { conversations: [ConversationPreview], totalUnread: int }
     Use-case: MessagingUseCases.listConversations()

GET  /api/conversations/{conversationId}/messages
     Query: ?offset=0&limit=50
     Response: { messages: [Message], total: int }
     Use-case: MessagingUseCases.loadConversation()  [CURRENTLY BYPASSES USE-CASE — FIX]

POST /api/conversations/{conversationId}/messages
     Body: { content: string }
     Use-case: MessagingUseCases.sendMessage()

DELETE /api/conversations/{conversationId}
     Use-case: MessagingUseCases.deleteConversation()

# Social / Safety
POST /api/me/block/{targetId}
     Use-case: SocialUseCases.blockUser()

DELETE /api/me/block/{targetId}
     (unblock — add to SocialUseCases if not present)

GET  /api/me/blocked
     Response: { users: [User] }
     (add query to SocialUseCases / TrustSafetyService)

POST /api/me/report/{targetId}
     Body: { reason: string, description?: string, blockUser?: bool }
     Use-case: SocialUseCases.reportUser()

POST /api/matches/{matchId}/unmatch
     Use-case: SocialUseCases.unmatch()

POST /api/matches/{matchId}/friend-request
     Use-case: SocialUseCases.requestFriendZone()

POST /api/matches/{matchId}/graceful-exit
     Use-case: SocialUseCases.gracefulExit()

PATCH /api/friend-requests/{requestId}
     Body: { action: "ACCEPT" | "DECLINE" }
     Use-case: SocialUseCases.respondToFriendRequest()

GET  /api/me/friend-requests
     Use-case: SocialUseCases.pendingFriendRequests()

# Notifications
GET  /api/me/notifications
     Query: ?unreadOnly=false
     Use-case: SocialUseCases.notifications()

PATCH /api/notifications/{id}/read
     Use-case: SocialUseCases.markNotificationRead()

POST /api/notifications/read-all
     Use-case: SocialUseCases.markAllNotificationsRead()

# Achievements & Stats
GET  /api/me/achievements
     Response: { unlocked: [UserAchievement], progress: [AchievementProgress] }
     Use-case: ProfileUseCases.getAchievements()

GET  /api/me/stats
     Response: UserStats record
     Use-case: ProfileUseCases.getOrComputeStats()

# Verification
POST /api/me/verification/send
     Body: { method: "EMAIL" | "PHONE", contact: string }
     (calls TrustSafetyService.generateVerificationCode() + EmailService.send())

POST /api/me/verification/confirm
     Body: { code: string }
     (calls TrustSafetyService.verifyCode())

# Profile Notes
GET  /api/users/{subjectId}/note
     Use-case: ProfileUseCases.getProfileNote()

PUT  /api/users/{subjectId}/note
     Body: { content: string }
     Use-case: ProfileUseCases.upsertProfileNote()

DELETE /api/users/{subjectId}/note
     Use-case: ProfileUseCases.deleteProfileNote()
```

**Standard Error Response Format:**
```json
{
  "error": "VALIDATION|NOT_FOUND|CONFLICT|FORBIDDEN|UNAUTHORIZED|INTERNAL",
  "message": "Human-readable description"
}
```

**Standard Success Pagination Format:**
```json
{
  "items": [...],
  "total": 42,
  "offset": 0,
  "limit": 20
}
```

**Fix — 5 Endpoints That Bypass Use-Case Layer:**
Currently `GET /api/users`, `GET /api/users/{id}`, `GET /api/users/{id}/candidates`, `GET /api/users/{id}/matches`, `GET /api/conversations/{id}/messages` call core services directly. Route all through their corresponding use-case methods.

---

#### T4 — Photo Upload & Serving

**What:** Photos are currently local filesystem paths. They need to be server-hosted URLs accessible to any client machine.

**Prerequisite:** T2 (auth required for upload endpoint), T3 (profile update route needed).

**Option A — Cloudinary (recommended):**

Maven dependency:
```xml
<dependency>
    <groupId>com.cloudinary</groupId>
    <artifactId>cloudinary-http5</artifactId>
    <version>1.39.0</version>
</dependency>
```

New class: `app/api/media/PhotoService.java`
```java
public interface PhotoService {
    // Upload bytes, return public URL
    String upload(byte[] imageBytes, String originalFilename) throws PhotoUploadException;
    // Delete by URL (for photo removal)
    void delete(String photoUrl);
}
// CloudinaryPhotoService implements PhotoService
// LocalFilePhotoService implements PhotoService (for dev without Cloudinary)
```

**Option B — Server Filesystem (simpler, works on Oracle Always Free):**
```java
// Store files in /app/uploads/ on server
// Serve via: GET /api/photos/{filename}
// Return URL: https://your-server.railway.app/api/photos/{filename}
```

**Upload Endpoint:**
```
POST /api/me/photos
Content-Type: multipart/form-data
Field: "photo" (file)
Response 201: { "url": "https://res.cloudinary.com/.../photo.jpg", "position": 3 }
Constraints:
  - Max size: 5 MB (enforce in Javalin config: app.maxRequestSize(5 * 1024 * 1024))
  - Accepted types: image/jpeg, image/png, image/webp
  - Max photos per user: AppConfig.ValidationConfig.maxPhotos (typically 6)
```

**Delete Endpoint:**
```
DELETE /api/me/photos/{position}
Removes photo at position, re-orders remaining photos
```

**JavaFX Client Changes:**
- `ProfileViewModel.addPhoto()`: instead of copying local file, POST to `/api/me/photos`, store returned URL in photo list
- `ProfileController`: file chooser still works — read bytes from chosen file, POST them
- `ImageCache` already handles loading from HTTP URLs — no change needed

**EXIF Stripping:** The core layer already validates EXIF. Ensure the upload endpoint strips EXIF metadata before storing (add Apache Commons Imaging or use Cloudinary's `eager` transformation `strip_exif`).

---

#### T5 — JavaFX HTTP API Client Layer

**What:** Introduce a network abstraction layer so ViewModels call the REST API instead of directly accessing the ServiceRegistry. This enables multi-user operation.

**Prerequisite:** T3 (all endpoints must exist before the client can call them).

**This is the largest single client-side change.** Break into two sub-tasks:

---

##### T5a — ApiClient + DTOs + DataSource Interfaces

**New package: `ui/api/`**

`ApiClient.java`:
```java
public class ApiClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper; // reuse Jackson already in pom.xml
    private final String baseUrl;       // e.g., "https://your-app.railway.app"
    private final ApiSession session;

    // Core method — all requests go through here
    public <T> T request(String method, String path, Object body, Class<T> responseType)
        throws ApiException;
    // Convenience
    public <T> T get(String path, Class<T> responseType);
    public <T> T post(String path, Object body, Class<T> responseType);
    public <T> T put(String path, Object body, Class<T> responseType);
    public void delete(String path);
    // Adds Authorization: Bearer <token> header automatically from ApiSession
}
```

`ApiSession.java`:
```java
public class ApiSession {
    private volatile String jwtToken;
    private volatile UUID currentUserId;

    public void setToken(String token, UUID userId);
    public String getToken();
    public UUID getCurrentUserId();
    public boolean isAuthenticated();
    public void clear(); // on logout
}
```

`DataSource interfaces` (one per use-case domain):
```java
// These mirror the *UseCases method signatures but return CompletableFuture<T>
// so ViewModels can use them with ViewModelAsyncScope.runLatest()
public interface MatchingDataSource {
    CompletableFuture<BrowseCandidatesResult> browseCandidates(BrowseCandidatesCommand cmd);
    CompletableFuture<ProcessSwipeResult> processSwipe(ProcessSwipeCommand cmd);
    CompletableFuture<UndoSwipeResult> undoSwipe(UndoSwipeCommand cmd);
    // ... etc
}
```

DTO record types: create a flat `ui/api/dto/` package with one record per API request/response pair. These are client-side types — they do not need to match server-side records exactly (though they often will).

---

##### T5b — ViewModel Wiring to RemoteDataSource

**New classes:**
```
ui/api/remote/
  RemoteMatchingDataSource.java    — implements MatchingDataSource via ApiClient
  RemoteMessagingDataSource.java   — implements MessagingDataSource
  RemoteProfileDataSource.java     — implements ProfileDataSource
  RemoteSocialDataSource.java      — implements SocialDataSource

ui/api/local/
  LocalMatchingDataSource.java     — wraps MatchingUseCases (already exists)
  LocalMessagingDataSource.java    — wraps MessagingUseCases
  LocalProfileDataSource.java      — wraps ProfileUseCases
  LocalSocialDataSource.java       — wraps SocialUseCases
```

**ViewModelFactory changes:**
```java
// Add a mode enum:
public enum Mode { LOCAL, REMOTE }

// Constructor variants:
public ViewModelFactory(ServiceRegistry services)         // LOCAL mode
public ViewModelFactory(ApiClient client, ApiSession s)   // REMOTE mode

// Internally: all ViewModel constructors receive *DataSource, not *UseCases
```

**ViewModel changes:** Each ViewModel currently holds `private final MatchingUseCases matchingUseCases`. Change to `private final MatchingDataSource dataSource`. The call sites stay the same — just the type changes.

**DatingApp.java wiring:**
```java
// Check for server URL in config:
String serverUrl = System.getProperty("datingapp.server.url",
    System.getenv("DATING_APP_SERVER_URL"));
if (serverUrl != null) {
    ApiSession session = new ApiSession();
    ApiClient client = new ApiClient(serverUrl, session);
    vmFactory = new ViewModelFactory(client, session);
} else {
    ServiceRegistry services = ApplicationStartup.initialize();
    vmFactory = new ViewModelFactory(services); // local mode
}
```

---

#### T6 — Real Login Screen (Email + Password)

**What:** Replace the user-list login with a real email/password form. Add account creation.

**Prerequisite:** T2 (auth API), T5 (ApiClient).

**Files to modify:**
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` — full rewrite
- `src/main/java/datingapp/ui/screen/LoginController.java` — full rewrite
- `src/main/resources/fxml/login.fxml` — full rewrite

**New files:**
- `src/main/java/datingapp/ui/viewmodel/SignupViewModel.java`
- `src/main/java/datingapp/ui/screen/SignupController.java`
- `src/main/resources/fxml/signup.fxml`
- Add `SIGNUP` to `NavigationService.ViewType` enum

**LoginViewModel new state:**
```java
// Observable properties:
StringProperty email = new SimpleStringProperty();
StringProperty password = new SimpleStringProperty();
BooleanProperty loading = new SimpleBooleanProperty(false);
StringProperty errorMessage = new SimpleStringProperty();

// Action:
void login(); // calls POST /api/auth/login, stores JWT in ApiSession, navigates to DASHBOARD
void navigateToSignup();
```

**login.fxml new layout:**
```
┌──────────────────────────────────┐
│  [App Logo / Name]               │
│                                  │
│  Email:    [________________]    │
│  Password: [________________]    │
│                                  │
│  [        Log In        ]        │
│  [    Create Account    ]        │
│                                  │
│  Error message (if any)          │
└──────────────────────────────────┘
```

**SignupViewModel:**
```java
StringProperty name, email, password, confirmPassword;
BooleanProperty loading;
StringProperty errorMessage;

void signup(); // calls POST /api/auth/register, then auto-login, navigate to profile completion
```

**On successful login:** store JWT in `ApiSession`, navigate to Dashboard. On app startup, if a valid stored token exists (e.g., in `UiPreferencesStore`), skip the login screen.

**Token persistence:** Save the JWT to `UiPreferencesStore` (or Java `Preferences` API) so users don't have to log in every time they open the app.

---

### TIER 2 — Real Product Feel

These don't block the MVP login-swipe-match-chat loop but are needed before real users will enjoy the app.

---

#### T7 — Real-Time Chat (WebSocket)

**What:** Replace 5–15 second polling with WebSocket-based message delivery.

**Prerequisite:** T2 (auth), T5 (ApiClient).

**Server (Javalin WebSocket):**
```java
// In RestApiServer:
app.ws("/ws", ws -> {
    ws.onConnect(ctx -> {
        String token = ctx.queryParam("token");
        UUID userId = jwtService.validateToken(token); // auth on connect
        sessionRegistry.register(userId, ctx); // Map<UUID, WsContext>
    });
    ws.onClose(ctx -> {
        sessionRegistry.remove(ctx);
    });
    ws.onError(ctx -> sessionRegistry.remove(ctx));
});
```

**Server — push on message send:**
In `MessagingUseCases.sendMessage()` (or the `MessageSent` event handler), after saving:
```java
// Push to recipient if connected:
wsSessionRegistry.sendIfConnected(recipientId, new WsMessage("NEW_MESSAGE", message));
// Push to sender too (confirms delivery, handles multi-device future):
wsSessionRegistry.sendIfConnected(senderId, new WsMessage("MESSAGE_SENT", message));
```

**WebSocket Message Format:**
```json
{
  "type": "NEW_MESSAGE",
  "payload": {
    "conversationId": "...",
    "message": { "id": "...", "senderId": "...", "content": "...", "createdAt": "..." }
  }
}
```

Also push these event types to relevant users:
- `MATCH_CREATED` — to both matched users (triggers match popup in UI)
- `NOTIFICATION` — for new notifications
- `FRIEND_REQUEST` — when someone requests friend-zone

**JavaFX Client (`ChatViewModel`):**
```java
// Connect WebSocket on login, keep alive
// On NEW_MESSAGE event: if for active conversation, append to messages list
// If for non-active conversation: increment unread count
// Fallback: if WebSocket fails/disconnects, fall back to existing 10s polling
```

Use `java.net.http.WebSocket` (Java 11+, no extra dependency).

---

#### T8 — Email Verification (SMTP)

**What:** The verification code is already generated and validated in `TrustSafetyService`. Only the delivery mechanism is missing.

**Prerequisite:** None (can be done standalone alongside any Tier 1 task).

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.sun.mail</groupId>
    <artifactId>jakarta.mail</artifactId>
    <version>2.0.1</version>
</dependency>
```

**New class: `app/email/EmailService.java`**
```java
public interface EmailService {
    void sendVerificationCode(String toEmail, String code) throws EmailException;
    void sendPasswordReset(String toEmail, String resetToken) throws EmailException;
    void sendWelcome(String toEmail, String name) throws EmailException;
}
// SmtpEmailService implements EmailService (production)
// NoOpEmailService implements EmailService (local dev — logs to console, current behavior)
```

**SmtpEmailService config (read from env vars):**
```
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USER=your_login@example.com
SMTP_PASSWORD=your_smtp_key
SMTP_FROM=noreply@yourdomain.com
```

**Email content for verification:**
```
Subject: Your Dating App Verification Code
Body: Your verification code is: 847293
      This code expires in 15 minutes.
```

**Wiring:** `ApplicationStartup` constructs `SmtpEmailService` (or `NoOpEmailService` if SMTP config absent) and passes to `TrustSafetyService`. The `/api/me/verification/send` endpoint calls this service.

---

#### T9 — Location Geocoding (Address → Lat/Lon)

**What:** Replace the raw lat/lon numeric input with a human-friendly address search.

**Prerequisite:** T4 (profile update endpoint should accept address for geocoding).

**Service:** [Nominatim](https://nominatim.openstreetmap.org) — OpenStreetMap geocoder, free, no API key required.

**Important Nominatim Rules:**
- Maximum 1 request per second — must rate-limit
- Requires `User-Agent` header identifying your application
- Acceptable Use Policy: must not cache results for more than a few days

**New class: `app/geo/GeocodingService.java`**
```java
public interface GeocodingService {
    record LatLon(double latitude, double longitude) {}
    record GeocodingResult(LatLon coordinates, String displayName) {}

    Optional<GeocodingResult> geocode(String address) throws GeocodingException;
    Optional<GeocodingResult> reverseGeocode(double lat, double lon) throws GeocodingException;
}
// NominatimGeocodingService implements GeocodingService
```

**NominatimGeocodingService:**
```java
// Endpoint: GET https://nominatim.openstreetmap.org/search
//   ?q={address}&format=json&limit=1&addressdetails=0
// Required header: User-Agent: DatingApp/1.0 (contact@yourdomain.com)
// Parse first result: lat, lon, display_name
// Rate limiter: simple AtomicLong lastCallMs, ensure 1000ms gap
```

**JavaFX UI changes:**
- `profile.fxml`: replace two number fields (latitude, longitude) with a single TextField for address + a search button
- `ProfileViewModel`: call `GeocodingService.geocode()` on search button click, display result address for confirmation, store lat/lon internally
- Show "Location set: [display_name]" after successful geocode
- Keep a "Set manually" option (advanced) for users who want raw coordinates

**REST API integration:**
```
PUT /api/me/profile — accept optional "address" string field
    Server: call GeocodingService.geocode(address), store resulting lat/lon
    (geocoding on server side avoids client having to know the API rules)
```

---

#### T10 — Server Deployment (Docker + Railway/Render)

**What:** Package the Javalin REST API as a deployable Docker image.

**Important Design Decision:** The server JAR must not include JavaFX. JavaFX requires display infrastructure that headless servers don't have, and including it wastes 50+ MB.

**Maven Server Profile:**
Add to `pom.xml`:
```xml
<profiles>
    <profile>
        <id>server</id>
        <dependencies>
            <!-- Exclude JavaFX from server build -->
        </dependencies>
        <build>
            <plugins>
                <!-- maven-assembly-plugin: fat JAR without JavaFX -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-assembly-plugin</artifactId>
                    <configuration>
                        <archive>
                            <manifest>
                                <mainClass>datingapp.app.api.RestApiServer</mainClass>
                            </manifest>
                        </archive>
                        <descriptorRefs>
                            <descriptorRef>jar-with-dependencies</descriptorRef>
                        </descriptorRefs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**Dockerfile:**
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -Pserver package -DskipTests assembly:single

# Stage 2: Run
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /build/target/*-jar-with-dependencies.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
```

**railway.json:**
```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "deploy": {
    "startCommand": "java --enable-preview -jar app.jar",
    "healthcheckPath": "/api/health",
    "healthcheckTimeout": 30
  }
}
```

**Health endpoint enhancement:**
```
GET /api/health
Response: {
  "status": "UP",
  "database": "UP" | "DOWN",
  "version": "1.0.0",
  "timestamp": "2026-03-12T..."
}
```

**`DevDataSeeder` in production:**
Add a guard in `ApplicationStartup`: only run `DevDataSeeder.seed()` if `DATING_APP_SEED_DATA=true` env var is set. In production, this must not run.

---

### TIER 3 — Small Fixes & Polish

These can be given to AI agents at any time — no prerequisites.

---

#### T11 — Achievement Count Display Fix

**File:** `src/main/java/datingapp/app/cli/StatsHandler.java`

**Current code comment:** "I don't know total count easily without asking service for all definitions" — displays unlocked count only.

**Fix:**
```java
// Achievement.values().length gives the total count
// Change the display from:
//   "Achievements unlocked: 3"
// To:
//   "Achievements: 3 / 11 unlocked"
int totalAchievements = Achievement.values().length;
int unlocked = unlockedList.size();
// Print: String.format("Achievements: %d / %d unlocked", unlocked, totalAchievements)
```

Also add this total to `StatsViewModel` for the JavaFX stats screen.

---

#### T12 — Password Reset Flow

**Prerequisite:** T8 (email), T2 (auth).

**New endpoints:**
```
POST /api/auth/forgot-password
     Body: { "email": "..." }
     Response: 200 always (don't confirm whether email exists — security)
     Action: generate reset token (UUID, 1-hour expiry), store in DB, send email

POST /api/auth/reset-password
     Body: { "token": "...", "newPassword": "..." }
     Response: 200 on success, 400 if token expired/invalid
```

**Schema change — Migration V5:**
```sql
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token       VARCHAR(36) PRIMARY KEY,
    user_id     UUID NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
```

**JavaFX:** Add "Forgot password?" link on login screen → opens email input dialog → shows "Check your email" confirmation.

---

#### T13 — Rate Limiting & API Security

**File:** `src/main/java/datingapp/app/api/RestApiServer.java`

**Add to Javalin before-handler:**
```java
// Simple in-memory rate limiter (per IP):
// - Auth endpoints: 5 requests per minute
// - General API: 100 requests per minute
// Return 429 Too Many Requests with Retry-After header

// CORS headers (for future web clients):
app.before(ctx -> {
    ctx.header("Access-Control-Allow-Origin", allowedOrigin);
    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
    ctx.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
});

// Security headers:
ctx.header("X-Content-Type-Options", "nosniff");
ctx.header("X-Frame-Options", "DENY");
```

**Input validation:** Verify that all API request bodies are validated before reaching use-cases. The `ValidationService` already exists in core — expose it from `ProfileUseCases.validationService()` accessor.

---

### Not Appropriate Yet

These items should not be implemented until the Tier 1 + 2 work is complete. Implementing them now would be premature optimization or require infrastructure that doesn't exist.

| Item                                        | Why Not Yet                                                                                                                                                              | When to Revisit                                   |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| **Android / Kotlin Port**                   | Port after the REST API is stable and battle-tested. The HTTP API is the contract the Android client will consume. Porting before the API is locked means porting twice. | After Tier 1 complete + 3 months of API stability |
| **SMS Verification (Twilio)**               | Twilio free tier only sends to pre-verified phone numbers — useless for real users. Email is sufficient for MVP.                                                         | After user base justifies the cost ($0.0075/SMS)  |
| **Presence / Typing Indicators**            | Requires WebSocket (T7) first. Even then, it's low value compared to the core loop.                                                                                      | After T7 is shipped and stable                    |
| **ML / AI Recommendation Improvements**     | The 6-dimension compatibility algorithm is already sophisticated. Improving it requires real usage data first.                                                           | After 100+ real user interactions                 |
| **Web Frontend**                            | Contradicts the "keep JavaFX" decision. Would require maintaining two UI codebases.                                                                                      | Post-Android port, if ever                        |
| **In-App Push Notifications (system tray)** | JavaFX system tray integration is complex and platform-inconsistent. Not needed for MVP.                                                                                 | Low priority indefinitely                         |
| **Super-Likes / Boosts / Premium Features** | Monetization layer adds significant product and legal complexity. Core product must work first.                                                                          | Post-launch                                       |
| **Multi-Language i18n**                     | `MatchingCliPresenter` is already i18n-ready but the rest of the app is not. Premature for pre-launch.                                                                   | When targeting non-English users                  |
| **Full-Text Search (profiles)**             | PostgreSQL supports it; not needed until large user base.                                                                                                                | 1000+ users                                       |

---

## 7. Implementation Order & Dependencies

```
PHASE 1 — Server Foundation
  T1: PostgreSQL migration       (no deps)
  T2: Authentication             (depends on T1)
  T3: Complete REST API          (depends on T2)
  T4: Photo upload               (depends on T2, T3)

PHASE 2 — Client Networking
  T5a: ApiClient + DTOs          (depends on T3 being complete)
  T5b: ViewModel wiring          (depends on T5a)
  T6:  Real login screen         (depends on T2, T5a)

At end of Phase 2: Two users on different machines can sign up, browse candidates,
                   swipe, match, and exchange messages.

PHASE 3 — Real Product Feel
  T7:  WebSocket chat            (depends on T2, T5)
  T8:  Email verification        (depends on T2 — can run parallel to Phase 2)
  T9:  Geocoding                 (depends on T4 partially — can run parallel)
  T10: Docker deployment         (depends on T1, T2 — can run late Phase 2)

QUICK WINS — any time, no dependencies
  T11: Achievement count fix
  T13: Rate limiting
  T12: Password reset            (depends on T8)
```

**Dependency Graph:**
```
T1 ──► T2 ──► T3 ──► T5a ──► T5b ──► T6
              T3 ──► T4
              T2 ──► T8 ──► T12
              T5 ──► T7
              T1 ──► T10
T9 (independent, parallels T4)
T11, T13 (fully independent)
```

---

## 8. Testing Strategy

### Existing Test Coverage (117 files — DO NOT BREAK)
The existing 117 tests are comprehensive and must continue to pass after every change. JaCoCo enforces a 60% line coverage gate on `mvn verify`.

Key test utilities already available:
- `TestStorages` — in-memory storage implementations for H2-based tests
- `TestClock` — fixed-time clock for deterministic tests
- `TestUserFactory` — factory methods for creating test User instances
- `JavaFxTestSupport` — base class for JavaFX controller/ViewModel tests

### New Tests Required Per Task

| Task            | Required New Tests                                                                                                                                                    |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| T1 (PostgreSQL) | Integration test: schema creates on PostgreSQL. Run via CI only (env var gate). Keep existing H2 tests.                                                               |
| T2 (Auth)       | Unit: `JwtService` issues and validates tokens. Unit: BCrypt hash/verify. Integration: register → login → protected route.                                            |
| T3 (REST API)   | Integration test per endpoint group (auth, matching, messaging, social). Add to existing `RestApi*Test` files.                                                        |
| T4 (Photos)     | Unit: `PhotoService` mock upload. Integration: multipart upload endpoint.                                                                                             |
| T5 (ApiClient)  | Unit tests for `RemoteMatchingDataSource` etc. using `MockServer` or WireMock. Contract test: local and remote data sources return equivalent results for same input. |
| T6 (Login)      | ViewModel test: login with valid credentials navigates to Dashboard. Invalid credentials shows error.                                                                 |
| T7 (WebSocket)  | Integration: connect WebSocket, send message, verify pushed to recipient.                                                                                             |
| T8 (Email)      | Unit: `SmtpEmailService` renders correct email content. Use mock SMTP in tests.                                                                                       |
| T9 (Geocoding)  | Unit: `NominatimGeocodingService` parses response JSON correctly. Use WireMock for HTTP mock.                                                                         |
| T11             | Trivial — verify display string format.                                                                                                                               |

### Known Flaky Test
`ChatControllerTest#selectionTogglesChatStateAndNoteButtonsRemainWired` — fails intermittently in full suite (JavaFX thread ordering). Passes in isolation. Pre-existing; do not investigate unless test suite becomes unreliable.

---

## 9. The Kotlin / Android Future

The current Java codebase is well-positioned for a Kotlin / Android port, but timing matters.

**Do not start porting until:**
1. The REST API (Tier 1) is complete and stable
2. The API has been used by real users for at least a few months
3. The API contract is not changing frequently

**What ports easily to Kotlin:**
- `core/model/` — `User` and `Match` become Kotlin data classes. Nested enums stay as-is.
- `core/matching/` — pure domain logic, no framework dependencies. Ports cleanly.
- `app/usecase/` — use-case records become data classes; methods become `suspend fun` with Kotlin coroutines
- `app/event/` — `AppEventBus` → `SharedFlow` or `EventBus` in Kotlin

**What does NOT port to Android:**
- `storage/jdbi/` — JDBI is JVM-only. Android uses Room, Realm, or direct SQLite.
- `ui/` — JavaFX has no Android equivalent. Replaced entirely by Android Views / Jetpack Compose.
- `app/api/RestApiServer.java` — stays in Java on the server. Android calls the HTTP API.
- `app/cli/` — not needed on Android.

**Recommended migration strategy:**
1. Start with a fresh Android project (Kotlin + Jetpack Compose)
2. Copy/translate `core/` domain classes first — they have no dependencies
3. Copy/translate `app/usecase/` as Android ViewModels calling the REST API via Retrofit
4. The `ViewModelAsyncScope` pattern maps to `viewModelScope + coroutines`
5. `AppEventBus` maps to `SharedFlow` in the ViewModel layer

---

## 10. Notes Specifically for AI Coding Agents

If you are an AI coding agent implementing tasks from this document, read this section first.

### Source of Truth
**Code only.** Read the actual source files before making changes. Do not trust documentation, comments, or this roadmap over what the code says. Package structure, method signatures, and class names are verified as of 2026-03-12 but may have changed.

### Critical Gotchas (from CLAUDE.md)
```
User.Gender             not core.model.Gender
Match.MatchState        not core.model.MatchState
AppClock.now()          not Instant.now()
generateId(a, b)        not a + "_" + b for pair IDs
ViewModelAsyncScope     not ad-hoc Thread.ofVirtual() + Platform.runLater()
services.getMatchingUseCases()  not new MatchingUseCases(...)
injected AppConfig      not AppConfig.defaults() in runtime code
```

### Build Rules
```bash
# Before committing, always run:
mvn spotless:apply     # formats code (required — Spotless check in verify)
mvn verify             # compile + test + spotless check + PMD + Checkstyle + JaCoCo

# For quick test cycle:
mvn test

# Verbose test output:
mvn -Ptest-output-verbose test
```

**Never run `mvn verify` multiple times with different output filters.** Capture output once, query the captured result multiple times.

### Package Rules
- **Do NOT import framework/DB APIs in `core/`** — core is framework-free
- All ViewModels use `ViewModelAsyncScope` for async work — see `ui/async/` package
- New API routes go through use-case layer only — never call core services or storage directly from `RestApiServer`
- New ViewModel data access goes through `*DataSource` interface (after T5) — never directly access `ServiceRegistry` from ViewModels in remote mode

### PMD Suppressions
```java
// Empty catch block:
} catch (SomeException ignored) {
    assert true; // NOPMD EmptyCatchBlock
}

// Inline suppression:
someMethodCall(); // NOPMD SomeRuleName
```

### Adding New API Routes
Pattern for new Javalin route:
```java
// 1. Add before-handler auth check will cover it automatically (T2)
// 2. Extract authenticated userId: UUID userId = ctx.attribute("userId");
// 3. Build UserContext: UserContext.api(userId)
// 4. Call use-case: UseCaseResult<X> result = useCases.doThing(new XCommand(userId, ...))
// 5. Map result: if (result.isSuccess()) { ctx.json(result.data()); ctx.status(200); }
//                else { ctx.json(errorBody(result.error())); ctx.status(httpStatus(result.error())); }
```

### HTTP Status Code Mapping for UseCaseError
```
VALIDATION  → 422 Unprocessable Entity
NOT_FOUND   → 404 Not Found
CONFLICT    → 409 Conflict
FORBIDDEN   → 403 Forbidden
INTERNAL    → 500 Internal Server Error
```
(Add 401 Unauthorized for auth failures — this is not a UseCaseError.Code variant, handle separately.)

### When Adding New Database Columns
1. Add to `MigrationRunner` as a new version (e.g., V4, V5)
2. Do NOT modify `SchemaInitializer` — it is frozen at V1 baseline
3. Use `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for safety
4. Update the relevant JDBI storage implementation (mapper + bind)
5. Update the storage interface if a new query is needed

### Test Coverage Gate
JaCoCo enforces **60% line coverage**. If you add new classes without tests, `mvn verify` will fail. Add unit tests for all new service/use-case code.

---

*End of roadmap. Verified against codebase 2026-03-12. Update this document when architectural decisions change.*
