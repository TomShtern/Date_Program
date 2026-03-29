# Dating App Roadmap — Design Spec

**Date:** 2026-03-29
**Status:** Approved (brainstorming complete)

## Context

A ~70K LOC Java 25 + JavaFX 25 dating app with clean architecture (core/app/storage/ui layers), 3 frontends (CLI, JavaFX GUI, REST API), and comprehensive test coverage. The core business logic is well-engineered but has semantic gaps, hollow features, and broken user journeys. The project serves as a portfolio piece, learning exercise, and eventually a real product.

### Problem Statement

The codebase looks good on paper but:
- Features are computed but never surfaced to the user (MatchQuality, ActivityMetrics, ProfileCompletion)
- Semantic bugs exist where code runs but does the wrong thing (ghost-matchable paused users, one-way blocking)
- User journeys are broken (garbage auto-fill signup, no onboarding, no profile editing in GUI)
- Three frontends at different completeness levels create confusion about what actually works
- Every audit adds depth to the engine without closing the gap between what the system computes and what a human sees

### End Goal

A working dating app on the user's Android phone, backed by a Kotlin server deployed to the cloud, usable by real people.

### Key Principles

- **AI agents do most of the implementation work** — phases and tasks should be structured for autonomous agent execution
- **JavaFX is a temporary test harness** — don't over-polish, it's throwaway
- **Core business logic is the real asset** — it carries forward through every phase
- **Each phase completes before the next begins** — no parallel phase work
- **Android-only first** — no iOS until the Android app is solid
- **Local network first, cloud later** — backend on PC, phone on WiFi

---

## Phase 1: Stabilize Core (Java, H2, CLI)

**Goal:** Make the core business logic correct and complete. Test everything via CLI and REST API. No technology changes.

### 1a. Semantic/Logic Fixes

| Bug | Description |
|-----|-------------|
| Paused users ghost-matchable | Someone can like/match a paused user who won't see it |
| One-way blocking | Blocker can't see blocked, but blocked can still see blocker in candidates |
| No re-match cooldown | Unmatch someone, immediately see them again as a candidate |
| Friend-zone doesn't clean up | Conversations and match artifacts linger after friend-zoning |
| Undo silently expires | User gets no feedback when the undo window closes |
| Location silently kills experience | Non-Israeli users get zero candidates with no explanation |

### 1b. Hollow Features → Real Features

| Feature | Current State | Target |
|---------|--------------|--------|
| MatchQuality scores | Computed (0-100, 5-star, 6-factor breakdown) but JavaFX shows only 2 fields, CLI shows nothing | Surfaced in CLI output and REST API responses |
| RecommendationService ranking | Partially wired into CLI (daily limit display only); candidate-ranking algorithm is unused — candidates shown in storage order, not ranked | Ranking algorithm integrated into candidate browsing flow |
| ActivityMetrics | Tracked silently, never surfaced | Exposed via CLI stats and REST API |
| ProfileCompletion score | Computed but not used to guide users | Used to prompt users to complete their profile |
| Event handlers | Achievements/notifications fire but produce no visible effect | Surfaced in CLI and REST API |

### 1c. User Journey Gaps

| Gap | Description |
|-----|-------------|
| Signup auto-fills garbage | LoginViewModel hardcodes Tel Aviv, placeholder photo, default bio, auto-activates |
| No first-launch experience | New user sees the same screen as a returning user |
| No profile editing in JavaFX | Can create but can't edit |
| 5 REST endpoints bypass use-case layer | Inconsistent behavior for GET users, candidates, matches, messages |

### 1d. Testing & Validation

- CLI exercises every feature (it's the most complete frontend)
- REST API exposes every feature through the use-case layer
- All semantic fixes get regression tests
- Quality gate passes: Spotless, Checkstyle, PMD, JaCoCo

### Phase 1 Exit Criteria

- All semantic bugs in 1a are fixed with regression tests
- All hollow features in 1b are surfaced in at least CLI and REST API
- All user journey gaps in 1c are addressed (functional via REST API and CLI; polished JavaFX screens are not required given the throwaway nature of that frontend)
- `mvn spotless:apply verify` passes
- CLI can exercise every feature end-to-end

---

## Phase 2: PostgreSQL Migration (still Java)

**Goal:** Replace H2 with PostgreSQL for runtime. Keep H2 for tests.

### Work Items

- Swap HikariCP DataSource from H2 to PostgreSQL (connection string, driver dependency)
- Audit all SQL in JDBI storage classes and schema files for H2-specific syntax that has no direct PostgreSQL equivalent (e.g., `MERGE` → `INSERT ... ON CONFLICT`, H2 identity column syntax, date arithmetic functions). The implementation plan will enumerate the specific tokens found.
- Update `SchemaInitializer` DDL for PostgreSQL column types and constraints
- Update `MigrationRunner` migrations for PostgreSQL compatibility
- Ensure `DevDataSeeder` works on PostgreSQL
- Local PostgreSQL setup (Docker container or native install)
- Config-driven database selection: `AppConfig` determines H2 (tests) vs PostgreSQL (runtime)
- Connection pooling tuning for PostgreSQL

### Phase 2 Exit Criteria

- Backend runs against local PostgreSQL
- All tests pass against H2 (unchanged)
- DevDataSeeder populates PostgreSQL correctly
- REST API works identically on PostgreSQL as it did on H2
- `mvn spotless:apply verify` passes

---

## Phase 3: Kotlin Migration

**Goal:** Convert Java → Kotlin file by file. Mechanical transformation, no behavior changes.

### Approach

- Add `kotlin-maven-plugin` to `pom.xml` for mixed Java/Kotlin compilation
- Convert bottom-up (leaf files first, entry points last):
  1. `core/model/` — `User`, `Match` → Kotlin data classes (biggest line reduction)
  2. `core/` utilities — `AppClock`, `AppConfig`, `TextUtil`, etc.
  3. `core/` services — matching, connection, profile, metrics
  4. `core/storage/` interfaces
  5. `storage/jdbi/` implementations
  6. `app/` layer — use cases, event bus, REST API
  7. **Skip `ui/`** — JavaFX UI is temporary, leave it in Java
- Tests convert alongside their subjects
- Quality gate stays green after each conversion batch
- AI agents convert + apply Kotlin idioms in one pass (data classes, `when`, null safety, extension functions)
- Update quality gate for Kotlin: configure ktlint via Spotless, replace PMD with Detekt, scope Checkstyle to remaining Java files only

### Phase 3 Exit Criteria

- All non-UI code is Kotlin
- All tests pass
- Quality gate passes (Kotlin-compatible linting configured)
- REST API behaves identically

---

## Phase 4: Android App

**Goal:** Build a native Android app (Kotlin + Jetpack Compose) that connects to the backend over local WiFi.

### Backend Changes

- `RestApiServer` binds to `0.0.0.0` (or configurable network interface) instead of `127.0.0.1`
- **Note:** Authentication is deferred to Phase 5. The LAN-exposed backend in Phase 4 is intentionally unauthenticated and should only be run on a trusted home network.
- Formalize JSON request/response contracts
- Ensure every feature has a REST endpoint (already covered by Phase 1)

### Android Project

- New Android Studio project (Kotlin, Jetpack Compose, Material 3)
- HTTP client: industry-standard library for calling REST API (e.g., Retrofit + OkHttp)
- Architecture: MVVM with Kotlin coroutines
- Configurable backend URL (e.g., `192.168.x.x:7070`)

### Incremental Delivery

| Version | Screens | Features |
|---------|---------|----------|
| v0.1 (MVP) | Login, Browse, Matches, Chat | Create account, view candidates, like/pass, view matches, basic messaging |
| v0.2 | Profile wizard, Swipe UI | Step-by-step profile creation, swipe gestures (card stack), compatibility scores |
| v0.3 | Settings, Profile progress | Preferences editing, profile completion progress, local notifications |
| v0.4+ | Full parity | Everything the backend supports, polish, animations, Material 3 theming |

Each version is independently usable — v0.1 is already a functional dating app on the phone.

### Phase 4 Exit Criteria

- v0.1 MVP runs on a real Android device
- Can create account, browse, match, and chat over local WiFi
- Backend and Android app communicate reliably

---

## Phase 5: Cloud & Services

**Goal:** Make the app accessible outside the local network. Enable real-world usage.

### Service Categories (tool choices left open)

| Category | Purpose | Examples (not prescriptive) |
|----------|---------|---------------------------|
| Authentication | Real user identity, secure login | Firebase Auth, Auth0, Supabase Auth, custom JWT |
| Database hosting | Cloud PostgreSQL | Railway, Supabase, managed PostgreSQL on any cloud |
| Photo storage | Real photo upload and serving | S3-compatible storage, Firebase Storage, Cloudflare R2 |
| Push notifications | Match alerts, new messages | FCM, OneSignal, or equivalent |
| Backend deployment | Host the Kotlin server | Docker on VPS, Railway, Fly.io, cloud VM |
| Domain (optional) | Custom API domain | Any registrar + DNS provider |

Specific tool choices will be evaluated when Phase 5 begins, based on cost, complexity, and the project's needs at that point.

### Phase 5 Exit Criteria

- App works over the internet (not just WiFi)
- Real user authentication
- Real photo upload/display
- Push notifications for matches and messages
- At least one other person can install and use the app
