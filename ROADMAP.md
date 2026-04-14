# Dating App — Roadmap V2

> **Created:** 2026-04-13
> **Role:** Intended to be the definitive roadmap moving forward.
> **Historical references:** `ROADMAP.md` and `2026-03-29-dating-app-roadmap-design.md` remain as historical reference until archived.
> **Source of truth:** Code only (`src/main/java`, `src/test/java`, `pom.xml`).

---

## Table of Contents

1. [The Big Realization](#1-the-big-realization)
2. [What We Actually Have](#2-what-we-actually-have)
3. [Why the Old Roadmap Was Wrong](#3-why-the-old-roadmap-was-wrong)
4. [The New Understanding](#4-the-new-understanding)
5. [Phase 1 — Polish & Harden the Server (Current)](#5-phase-1--polish--harden-the-server-current)
6. [Phase 2 — Make the Server Reachable](#6-phase-2--make-the-server-reachable)
7. [Phase 3 — Build the Mobile App (Flutter)](#7-phase-3--build-the-mobile-app-flutter)
8. [Phase 4 — Cloud & Real Users (Later)](#8-phase-4--cloud--real-users-later)
9. [What We Are NOT Doing](#9-what-we-are-not-doing)
10. [Development Environment](#10-development-environment)
11. [Architecture Diagrams](#11-architecture-diagrams)

---

## 1. The Big Realization

Previous roadmaps included a **"Java → Kotlin migration" phase** — the idea that every Java file in the server would be rewritten in Kotlin before building the Android app.

**This was wrong.**

The mobile app is a **completely separate project** — a new folder, new build system, new codebase. It talks to the server exclusively over HTTP REST. The phone never runs server code. The server never runs phone code. They're two entirely different programs.

```
┌──────────────────────────────────┐       ┌──────────────────────────────────┐
│  YOUR LAPTOP (SERVER)            │       │  YOUR PHONE (CLIENT)             │
│                                  │       │                                  │
│  Stays in Java. Forever.         │  HTTP │  New Flutter project.            │
│  ~390 Java files.                │◄─────►│  ~50 Dart files.                 │
│  Maven build.                    │ JSON  │  Flutter/Dart build.             │
│  Large automated test suite.     │       │  Zero shared code.               │
│  PostgreSQL database.            │       │  Calls REST endpoints.           │
│                                  │       │                                  │
└──────────────────────────────────┘       └──────────────────────────────────┘
         THE KITCHEN                                THE DRIVE-THROUGH WINDOW
   (already built, works)                     (new UI, same kitchen)
```

The server is the kitchen. The mobile app is a drive-through window. Same kitchen, new way to order. **You don't rebuild the kitchen to add a window.**

### What this means

| Old plan                                                           | New plan                                                                |
|--------------------------------------------------------------------|-------------------------------------------------------------------------|
| Phase 3: Convert ~390 Java files to Kotlin (~months of work)       | **Skip entirely**                                                       |
| Phase 4: Build Android app in Kotlin (blocked by server migration) | **Build mobile app in Flutter** — starts immediately, no server changes |
| iOS support requires a separate Swift project                      | **iOS included for free** — same Flutter codebase                       |
| Total: 4–5 phases before phone app                                 | Total: 2–3 phases before phone app                                      |

**We removed an entire phase of work that wasn't needed.** The path from here to a working mobile app is shorter than we thought.

---

## 2. What We Actually Have

A source-grounded snapshot of the codebase with counts refreshed from the current workspace on 2026-04-13.

### Numbers

| Metric           | Value                                                                           |
|------------------|---------------------------------------------------------------------------------|
| Java files       | **390 total** (182 main / 208 test)                                             |
| Lines of code    | **~109K total** (88.8K code / 15.4K blank / 5.1K comments)                      |
| Test suite       | Large automated suite; the roadmap still targets a clean full verification path |
| REST endpoints   | **~45+ routes**                                                                 |
| Quality gate     | Spotless + PMD + JaCoCo in `verify`; Checkstyle in `validate`                   |
| Database support | H2 (test) + PostgreSQL (runtime)                                                |
| UI frontends     | JavaFX (desktop), CLI (terminal), REST API (HTTP)                               |

### What's Done and Working

Every item below is **implemented in production code**, not stubs or placeholders:

- **User profiles** — bio, photos, interests (44 types), lifestyle, pace, dealbreakers, location
- **Profile completion scoring** — 5-tier system (Starter → Bronze → Silver → Gold → Diamond)
- **Candidate browsing** — filtered by gender, age, distance (Haversine), dealbreakers, ranked by recommendation algorithm
- **Compatibility scoring** — 6 dimensions, 0–100 score with star ratings
- **Swiping** — like/pass, daily limits, atomic mutual match creation, concurrent-swipe guard
- **Undo** — 15-second atomic undo window
- **Daily pick** — one featured profile/day, deterministic per user+day
- **Standouts** — top 10 ranked candidates with diversity filtering
- **Matches** — paginated list, quality reports, star ratings, highlights
- **Messaging** — conversations, pagination, mark-as-read, unread counts
- **Relationship transitions** — friend-zone, graceful exit, unmatch, block — enforced by state machine
- **Trust & safety** — block, report, auto-ban, bidirectional block check
- **Verification** — 6-digit code with 15-min TTL (delivery simulated, prints to console)
- **Achievements** — 11 achievements across 4 categories with XP points
- **Stats & metrics** — swipe counts, like ratio, match rate, reciprocity, selectiveness
- **Notifications** — match/message/transition notifications, polling delivery
- **Profile notes** — private notes on other users
- **REST API** — ~45+ routes covering every feature through the use-case layer
- **PostgreSQL** — full runtime support with dialect-aware SQL, migrations, smoke tests
- **Quality gate** — established and expected as the standard validation path

### What's NOT Done

| Item                          | Status     | Impact                                                                           |
|-------------------------------|------------|----------------------------------------------------------------------------------|
| Geocoding (address → lat/lon) | ❌ Not done | Users must type raw coordinates — nobody does this                               |
| Server accessible from LAN    | ❌ Not done | Currently binds to `127.0.0.1` (localhost-only)                                  |
| Authentication (JWT/BCrypt)   | ❌ Not done | Not needed for LAN/MVP iteration, but required for real-user internet deployment |
| Photo upload/serving          | ❌ Not done | Photos are local filesystem paths, not HTTP URLs                                 |

The important takeaway is that the project is not missing a backend product. What still makes it feel incomplete is mostly a handful of high-impact experience and validation gaps: usable location entry, and a real end-to-end smoke pass.

---

## 3. Why the Old Roadmap Was Wrong

### The misunderstanding

Both previous roadmaps treated "Kotlin migration" as a prerequisite for the Android app. The logic was:

> "Android uses Kotlin → therefore the server must be in Kotlin → convert everything → then build the app."

This was wrong on two levels:

1. **The server doesn't need to be in Kotlin at all.** The phone communicates with it through HTTP — it doesn't care what language the server is written in.
2. **The mobile app doesn't have to be Kotlin either.** We chose Flutter (Dart) as the mobile framework (see [Why Flutter](#why-flutter) below). The mobile app is a completely separate project that calls REST endpoints.

|             | Rewriting the server to Kotlin                 | Building a new mobile app                 |
|-------------|------------------------------------------------|-------------------------------------------|
| **What**    | Rewrite ~390 existing Java files in Kotlin     | Write ~50 new files in a new project      |
| **Needed?** | **No** — the phone never runs server code      | **Yes** — but in Dart/Flutter, not Kotlin |
| **Scope**   | Months of mechanical conversion                | Weeks of new UI code                      |
| **Risk**    | High — every changed file is a regression risk | None — nothing in the server changes      |

The server can stay Java forever. The mobile app uses Flutter/Dart because it offers cross-platform support (Android + iOS from one codebase), excellent VS Code integration, and sub-second hot reload. See the [Jetpack Compose alternative](#jetpack-compose-alternative) section if native Android-only is preferred.

### The cost of the misunderstanding

If we had followed the old plan:
- **Months** converting ~390 Java files to Kotlin — mechanical work with zero new functionality
- The Android app would have been **blocked** behind this conversion
- Risk of introducing regressions during conversion (every changed file is a potential bug)
- Two build systems to maintain simultaneously during the transition (mixed Java/Kotlin Maven build)

### The corrected path

- **Skip the server language migration entirely**
- Go straight from "server works" → "server reachable from phone" → "Flutter app calls server"
- Save months of work
- Zero regression risk — the server code stays exactly as-is
- Get iOS support for free from the same Flutter codebase

---

## 4. The New Understanding

### The server is the product

The `core/` package — matching algorithms, compatibility scoring, user profiles, messaging, trust & safety, achievements, stats — **this is the product**. It's by far the most valuable part of the codebase.

Everything else (CLI, JavaFX, REST API, and eventually the Flutter app) are **frontends** — different ways to interact with the same core engine.

This is why the roadmap stays foundation-first: finish the highest-impact local experience and backend-readiness gaps before taking on LAN, mobile, and cloud complexity.

### The mobile app is just a new frontend

The Flutter project adds a **4th frontend** to a system that already has 3:

```
               ┌─────────┐
               │  core/   │  ← The product (matching, messaging, profiles, safety)
               │  Java    │
               └────┬─────┘
                    │
          ┌─────────┼─────────┐
          │         │         │
    ┌─────┴──┐ ┌───┴────┐ ┌──┴───────┐
    │  CLI   │ │ JavaFX  │ │ REST API │  ← Existing frontends
    │  ✅    │ │  ✅     │ │   ✅     │
    └────────┘ └────────┘ └─────┬────┘
                               │ HTTP/JSON
                          ┌────┴─────┐
                          │ Flutter  │  ← New frontend (Phase 3)
                          │  Dart    │
                          │ 📱 NEW   │  Android + iOS from one codebase
                          └──────────┘
```

The Flutter app doesn't reimplement any business logic. It calls the same REST endpoints that already exist. `GET /api/users/{id}/candidates` returns ranked candidates — the Flutter app just displays them on a phone screen instead of a desktop window.

### Why Flutter

We chose Flutter over the alternative (Kotlin + Jetpack Compose) for these reasons:

| Factor                  | Flutter advantage                                                                                                                                       |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| **VS Code**             | First-class support — hot reload, debugger, widget inspector all work in VS Code (you prefer VS Code over Android Studio)                               |
| **Hot reload**          | Sub-second — save a file and see the UI update on the phone instantly. Compose's live edit is slower and more limited                                   |
| **Cross-platform**      | Same codebase builds for Android + iOS. If someone wants to try the app on an iPhone, you just build for iOS — no separate project                      |
| **Dart language**       | Very similar to Java — familiar syntax, strong typing, async/await. Minimal learning curve                                                              |
| **Dating app packages** | Flutter has ready-made swipe-card packages (`flutter_card_swiper`), mature image caching (`cached_network_image`), and a rich Material 3 widget library |
| **Portfolio value**     | Demonstrates cross-platform mobile development — a growing skill in the industry                                                                        |

### What the languages mean in this project

| Context                      | Language | Why                                                      |
|------------------------------|----------|----------------------------------------------------------|
| **Server** (`Date_Program/`) | Java     | Already written, tested, working. No reason to change.   |
| **Mobile app** (new project) | Dart     | Required by Flutter. Dart is Java-like with async/await. |

The Flutter project will have Dart files that look like this:

```dart
// Not a "port" — a tiny class that maps API JSON responses
class User {
  final String id;
  final String name;
  final String bio;
  final int age;

  User({required this.id, required this.name, required this.bio, required this.age});

  factory User.fromJson(Map<String, dynamic> json) => User(
    id: json['id'],
    name: json['name'],
    bio: json['bio'],
    age: json['age'],
  );
}
```

Compare to the server's `User.java` — a large domain model with builders, enums, validation, storage mapping, and copy methods. The Flutter version is tiny by comparison because it only models the API contract.

---

## 5. Phase 1 — Polish & Harden the Server (Current)

**Goal:** Make the server solid, complete, and ready for a mobile client to consume.

**Status:** Mostly done. The core engine is complete and tested. Remaining work is high-impact polish, validation, and seam hardening — not missing core product features.

The point of this phase is not to endlessly redesign JavaFX. It is to close the realism and validation gaps that most directly affect mobile readiness.

### 1.1 — Dev Experience: Photos in Seeded Data ✅

|          |                                                                                                                     |
|----------|---------------------------------------------------------------------------------------------------------------------|
| **What** | Add photo URLs to `DevDataSeeder`'s 30 seeded users                                                                 |
| **Why**  | Without photos, the entire app experience feels hollow — matching, standouts, profiles all show placeholder avatars |
| **Done** | Each seeded user gets 3 deterministic, gender-appropriate portrait photos from `randomuser.me`                      |
| **File** | `src/main/java/datingapp/storage/DevDataSeeder.java`                                                                |

### 1.2 — Dev Experience: Geocoding

|                     |                                                                                        |
|---------------------|----------------------------------------------------------------------------------------|
| **What**            | Replace raw lat/lon input fields with an address search box                            |
| **Why**             | Nobody knows their coordinates by heart — location-dependent features break without it |
| **How**             | Client-side geocoding via Nominatim (OpenStreetMap, free, no API key)                  |
| **New files**       | `GeocodingService.java`, `NominatimGeocodingService.java`                              |
| **Files to modify** | Profile screen FXML, `ProfileViewModel`, `ProfileController`                           |
| **Effort**          | Medium — new service + UI changes, but purely client-side                              |

### 1.3 — Smoke Test by Human

|                        |                                                                                        |
|------------------------|----------------------------------------------------------------------------------------|
| **What**               | Run `mvn javafx:run` and actually use every screen                                     |
| **Why**                | Static analysis can't catch real UX problems                                           |
| **How**                | Create user → browse → like → match → chat → block → check achievements → toggle theme |
| **Output**             | List of real issues found → becomes tasks for fixes                                    |
| **This is a YOU task** | Not an agent task — you need to feel the app                                           |

### 1.4 — Fix Issues from Smoke Test

|          |                                                           |
|----------|-----------------------------------------------------------|
| **What** | Fix real issues found during 1.3                          |
| **How**  | Can't be defined in advance — they come from real testing |

### 1.5 — PostgreSQL as Routine Validation

|            |                                                                                         |
|------------|-----------------------------------------------------------------------------------------|
| **What**   | Make PostgreSQL testing a normal part of development, not an optional one-off           |
| **Why**    | The Flutter app will talk to the PostgreSQL-backed server — this path needs to be solid |
| **How**    | Broaden `run_postgresql_smoke.ps1` usage; reduce H2-first assumptions in storage tests  |
| **Effort** | Incremental — shift existing tests toward PostgreSQL where they add value               |

### 1.6 — Targeted Hardening & Consistency Pass

|            |                                                                                                                                     |
|------------|-------------------------------------------------------------------------------------------------------------------------------------|
| **What**   | Do a focused pass on seams that matter for mobile readiness: async/UI boundaries, PostgreSQL vs H2 mismatches, and REST rough edges |
| **Why**    | Foundation-first work only pays off if the important seams are trustworthy before LAN/mobile expansion                              |
| **How**    | Small targeted fixes, guided by smoke findings and PostgreSQL validation — not broad refactors or a JavaFX beautification project   |
| **Effort** | Incremental — take only the fixes that materially improve confidence                                                                |

### Phase 1 Exit Criteria

- [x] `mvn spotless:apply verify` passes (already does)
- [x] Seeded users have photos (each user gets 3 deterministic portraits via `randomuser.me`)
- [ ] Location input uses address search (geocoding)
- [ ] Human smoke test done — all found issues fixed
- [ ] PostgreSQL smoke path runs as part of normal validation
- [ ] The important seam inconsistencies found during smoke/validation are closed

---

## 6. Phase 2 — Make the Server Reachable

**Goal:** Allow your phone to reach the server over your home WiFi.

**Prerequisite:** Phase 1 should be mostly complete.

This is a **small phase** — just a few targeted changes to the server configuration.

### 2.1 — Bind to LAN Instead of Localhost

|                  |                                                                                                                                       |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| **What**         | Change `RestApiServer` from `127.0.0.1` to configurable binding (e.g., `0.0.0.0` or specific LAN interface)                           |
| **Current code** | `app.start(InetAddress.getLoopbackAddress().getHostAddress(), port)` — localhost-only                                                 |
| **Also**         | The localhost enforcement guard (`enforceLocalhostOnly`) needs to become configurable                                                 |
| **Safety**       | When binding to LAN, keep localhost-only mode as default for dev safety. Add explicit opt-in (env var or config flag) for LAN binding |
| **File**         | `src/main/java/datingapp/app/api/RestApiServer.java`                                                                                  |
| **Effort**       | Small — ~20 lines changed, plus removing the localhost guard when in LAN mode                                                         |

### 2.2 — Verify Phone → Server Connectivity

|            |                                                                                                                  |
|------------|------------------------------------------------------------------------------------------------------------------|
| **What**   | Start server on laptop, connect phone to same WiFi, call `http://192.168.x.x:7070/api/health` from phone browser |
| **Why**    | Confirms the network path works before building the Flutter app                                                  |
| **Effort** | Minutes — just testing, no code                                                                                  |

### 2.3 — Defer Real Auth Without Forgetting It

|            |                                                                                                               |
|------------|---------------------------------------------------------------------------------------------------------------|
| **What**   | Keep the current dev-style user selection/login shortcut for LAN testing and the first Flutter MVP            |
| **Why**    | Real auth matters, but it should not block phone-to-server connectivity or the first round of mobile learning |
| **When**   | Move JWT/BCrypt into the real-user internet phase, where it becomes mandatory                                 |
| **Effort** | None now — this is a sequencing choice, not a missing implementation                                          |

### Phase 2 Exit Criteria

- [ ] Server binds to LAN interface when configured to do so
- [ ] Phone browser can reach `http://{laptop-ip}:7070/api/health`
- [ ] localhost-only mode still works as default for safe development
- [ ] The dev login shortcut can remain in place for the first mobile iteration
- [ ] `mvn spotless:apply verify` still passes

---

## 7. Phase 3 — Build the Mobile App (Flutter)

**Goal:** A Flutter app running on your Android phone first, with iOS available from the same codebase if we want it, calling your server.

**This is the main event.** The server is ready. The API is ready. Now we build the mobile experience.

### Project Setup

|                      |                                                                                             |
|----------------------|---------------------------------------------------------------------------------------------|
| **Tool**             | VS Code + Flutter extension (primary); Android Studio optional for emulator/profiling       |
| **Language**         | Dart                                                                                        |
| **UI framework**     | Flutter (Material 3 widgets)                                                                |
| **Architecture**     | MVVM + Riverpod (state management)                                                          |
| **HTTP client**      | `dio` (feature-rich HTTP client for Dart)                                                   |
| **Build system**     | Flutter CLI (Gradle under the hood for Android)                                             |
| **Project location** | **New folder** — completely separate from `Date_Program/`                                   |
| **API contract**     | REST/JSON only — no shared server business logic or direct model porting                    |
| **Platforms**        | Android first (API 21+, broad device coverage) + iOS available from the same codebase later |

### Prerequisites (one-time setup)

```powershell
# Install Flutter SDK
winget install Flutter.Flutter

# Verify installation
flutter doctor

# Create the project
flutter create dating_app
```

### Project Structure

```
dating_app/                                ← NEW PROJECT (separate repo or folder)
├── lib/
│   ├── main.dart                          ← App entry point
│   ├── api/
│   │   ├── api_client.dart                ← HTTP client wrapper (dio)
│   │   └── api_endpoints.dart             ← REST endpoint definitions
│   ├── models/                            ← JSON response data classes
│   │   ├── user.dart
│   │   ├── match.dart
│   │   ├── message.dart
│   │   └── candidate.dart
│   ├── screens/
│   │   ├── login_screen.dart              ← Dev login (user picker)
│   │   ├── browse_screen.dart             ← Swipe card stack
│   │   ├── matches_screen.dart            ← Match list
│   │   ├── chat_screen.dart               ← Messaging
│   │   ├── profile_screen.dart            ← View/edit profile
│   │   └── settings_screen.dart           ← Preferences
│   ├── viewmodels/
│   │   ├── browse_viewmodel.dart
│   │   ├── matches_viewmodel.dart
│   │   ├── chat_viewmodel.dart
│   │   └── profile_viewmodel.dart
│   └── theme/
│       ├── app_theme.dart                 ← Material 3 theming
│       └── app_colors.dart                ← Color palette
├── pubspec.yaml                           ← Dependencies (Flutter's pom.xml)
└── test/                                  ← Widget and unit tests
```

### Incremental Delivery

Build the app version by version. Each version is a usable app on your phone.

#### v0.1 — MVP (Login → Browse → Match → Chat)

The core loop. If this works, you have a dating app on your phone.

| Screen    | What it does                                 | API calls                                                                  |
|-----------|----------------------------------------------|----------------------------------------------------------------------------|
| Login     | Dev-mode user picker (same as JavaFX)        | `GET /api/users`                                                           |
| Browse    | Cards with photo/name/age, like/pass buttons | `GET /api/users/{id}/candidates`                                           |
| Like/Pass | Swipe right = like, left = pass              | `POST /api/users/{id}/like/{target}`, `POST /api/users/{id}/pass/{target}` |
| Matches   | List of mutual matches                       | `GET /api/users/{id}/matches`                                              |
| Chat      | Message thread with a match                  | `GET/POST /api/users/{id}/conversations/{id}/messages`                     |

**Estimated scope:** ~15–20 Dart files, ~2000 lines of code.

#### v0.2 — Profile & Polish

| Screen/Feature        | What it adds                         |
|-----------------------|--------------------------------------|
| Profile view          | View own profile and match profiles  |
| Swipe gestures        | Real swipe-left/right card animation |
| Compatibility display | Show match quality scores            |
| Standouts             | Featured profiles section            |
| Undo                  | Swipe undo with countdown            |

#### v0.3 — Full Feature Parity

| Screen/Feature       | What it adds                         |
|----------------------|--------------------------------------|
| Profile editing      | Edit bio, preferences, dealbreakers  |
| Safety               | Block/report screens                 |
| Notifications        | Match and message notifications      |
| Stats & achievements | Progress tracking                    |
| Preferences          | Notification settings, theme toggle  |
| Onboarding           | First-launch profile completion flow |

#### v0.4+ — Refinement

- Animations and transitions
- Offline caching
- Image loading optimization
- Accessibility
- Material 3 theming polish

### Phase 3 Exit Criteria

- [ ] v0.1 runs on a real Android device
- [ ] Phone connects to laptop server over WiFi
- [ ] Can create account, browse candidates, like/pass, view matches, send messages
- [ ] Server and Flutter app communicate reliably via REST API
- [ ] Same codebase remains iOS-capable; simulator/device verification is useful but not a blocker for Android-first completion

### Jetpack Compose Alternative

If you ever decide to go native Android-only instead of Flutter, the architecture stays identical — only the mobile codebase changes:

|                      | **Flutter (chosen)**  | **Jetpack Compose (alternative)**             |
|----------------------|-----------------------|-----------------------------------------------|
| **Language**         | Dart                  | Kotlin                                        |
| **IDE**              | VS Code (first-class) | Android Studio (required for Compose preview) |
| **Setup**            | `flutter create`      | New Android Studio project                    |
| **HTTP client**      | `dio`                 | Retrofit + OkHttp                             |
| **State management** | Riverpod              | ViewModel + StateFlow                         |
| **iOS**              | ✅ Same codebase       | ❌ Separate Swift project needed               |
| **Hot reload**       | Sub-second            | Slower, more limited                          |
| **File extension**   | `.dart`               | `.kt`                                         |

The server doesn't change. The REST API doesn't change. You'd just swap the Flutter project for a Kotlin/Jetpack Compose project that calls the same endpoints. This is a mobile-only decision — the server is immutable.

---

## 8. Phase 4 — Cloud & Real Users (Later)

**Goal:** Make the app accessible from anywhere (not just your home WiFi).

**When:** Only after the mobile app works reliably on LAN. This is later work, not current work.

### What changes

| Component          | Current                     | After Phase 4                        |
|--------------------|-----------------------------|--------------------------------------|
| Server location    | Your laptop                 | Cloud VM (free tier)                 |
| Database           | Local PostgreSQL            | Cloud PostgreSQL (Neon, Supabase)    |
| Phone connects via | WiFi LAN IP (`192.168.x.x`) | Internet URL (`yourapp.railway.app`) |
| Authentication     | None (dev mode)             | JWT + BCrypt                         |
| Photos             | Local filesystem            | Cloud storage or server-hosted       |

### Free-tier stack (verified available)

Treat this as a menu of candidate options, not as a locked implementation decision.

| Concern             | Service                  | Free Tier                      |
|---------------------|--------------------------|--------------------------------|
| **Backend hosting** | Oracle Cloud Always Free | 2 AMD + 4 ARM VMs, permanent   |
| Database (alt)      | Neon                     | 0.5 GB PostgreSQL, auto-pauses |
| Database (alt)      | Supabase                 | 500 MB PostgreSQL              |
| Photo storage       | Server filesystem        | $0 on Oracle Always Free       |
| Photo storage (alt) | Cloudinary               | 25 GB, 25 credits/month        |
| Email               | Brevo                    | 300 emails/day                 |

### Phase 4 Tasks (high-level)

1. **Authentication** — JWT + BCrypt. Replace user-picker login with email/password.
2. **Deployment** — Package server as Docker container, deploy to cloud VM.
3. **Database migration** — Point `DATING_APP_DB_URL` at cloud PostgreSQL.
4. **Photo hosting** — Upload endpoint + HTTP serving or cloud storage.
5. **Security** — Rate limiting, CORS, security headers.
6. **Real email** — Replace console-printed verification codes with SMTP delivery.

### Phase 4 Exit Criteria

- [ ] App works over the internet (not just WiFi)
- [ ] Real user authentication
- [ ] Photo upload and display
- [ ] At least one other person can install and use the app

---

## 9. What We Are NOT Doing

Being explicit about what we're skipping and why:

| Item                                     | Why we're skipping it                                                                                                                               |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **Java → Kotlin server migration**       | Unnecessary. The phone never runs server code. Converting ~390 files would waste months with zero new functionality. The server stays Java forever. |
| **iOS app (separate project)**           | Not needed — Flutter builds iOS from the same codebase. A separate Swift/SwiftUI project would be redundant.                                        |
| **Web app**                              | Not needed. Three frontends (CLI, JavaFX, Flutter) cover dev/testing/production.                                                                    |
| **Big JavaFX-only redesign**             | JavaFX is useful for validation and parity, but large redesign work that does not improve backend correctness or mobile readiness is a distraction. |
| **WebSocket real-time chat**             | Polling works fine for now. WebSocket is an optimization, not a requirement.                                                                        |
| **Production auth during Phases 1–2**    | Important later, but it should not block local/LAN/mobile MVP progress. It belongs in the real-user deployment phase.                               |
| **Push notifications**                   | Phase 4+, only when going to cloud. Polling is sufficient for LAN development.                                                                      |
| **Microservices / splitting the server** | A single JVM on a free-tier VM handles thousands of users. Premature distribution adds complexity with no benefit at this scale.                    |

---

## 10. Development Environment

### Tools

| Tool             | Purpose                                                                                             |
|------------------|-----------------------------------------------------------------------------------------------------|
| VS Code Insiders | Server development (Java + Maven) **and** Flutter development (Dart)                                |
| Flutter SDK      | Mobile app framework — install via `winget install Flutter.Flutter`                                 |
| Scrcpy           | Mirror laptop screen to phone for quick UI evaluation (install: `winget install Genymobile.scrcpy`) |

### Server commands (existing project)

```powershell
# Full quality gate
mvn spotless:apply verify

# Run tests
mvn test

# Run JavaFX GUI
mvn javafx:run

# Run CLI
mvn compile && mvn exec:exec

# Start local PostgreSQL + run smoke test
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1

# Full local verification (Maven + PostgreSQL)
.\run_verify.ps1
```

### Flutter commands (new project, when started)

```powershell
# Run on connected Android phone (with hot reload)
flutter run

# Build release APK
flutter build apk

# Build for iOS (requires Mac)
flutter build ios

# Run tests
flutter test
```

### Testing the phone-to-server connection (Phase 2+)

```powershell
# 1. Find your laptop's LAN IP
ipconfig | findstr "IPv4"
# e.g., 192.168.1.105

# 2. Start server with LAN binding (after Phase 2 changes)

# 3. On phone: open Chrome → http://192.168.1.105:7070/api/health
# Should return: {"status":"ok"}
```

---

## 11. Architecture Diagrams

### Full system (after Phase 3)

```
┌──────────────────────────────────────────────────────────────────┐
│                         YOUR LAPTOP                              │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    Java Server (Maven)                      │  │
│  │                                                            │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │  core/   │  │  app/    │  │ storage/ │  │REST API  │  │  │
│  │  │ matching │  │ usecases │  │  jdbi/   │  │ Javalin  │  │  │
│  │  │ profiles │  │ events   │  │          │  │ :7070    │  │  │
│  │  │ messaging│  │ cli      │  │          │  │          │  │  │
│  │  │ safety   │  │ api      │  │          │  │          │  │  │
│  │  └──────────┘  └──────────┘  └─────┬────┘  └────┬─────┘  │  │
│  │                                     │             │        │  │
│  │                               ┌─────┴─────┐      │        │  │
│  │                               │PostgreSQL  │      │        │  │
│  │                               │ :5432/55432│      │        │  │
│  │                               └───────────┘      │        │  │
│  └───────────────────────────────────────────────────┼────────┘  │
│                                                      │           │
└──────────────────────────────────────────────────────┼───────────┘
                                                        │
                                          WiFi / LAN / Internet
                                                        │
┌───────────────────────────────────────────────────────┼───────────┐
│                       YOUR PHONE                      │           │
│                                                       │           │
│  ┌───────────────────────────────────────────────────┴────────┐  │
│  │              Mobile App (Flutter + Dart)                    │  │
│  │                                                            │  │
│  │  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐    │  │
│  │  │   dio      │  │ ViewModels  │  │ Flutter Widgets  │    │  │
│  │  │ HTTP client│──│ Riverpod    │──│ Material 3 UI    │    │  │
│  │  └────────────┘  └─────────────┘  └──────────────────┘    │  │
│  │                                                            │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Data flow: Liking a candidate

```
PHONE                          SERVER
─────────                      ──────
1. User swipes right ─────────► POST /api/users/{id}/like/{target}
                                2. MatchingUseCases.likeUser()
                                3. MatchingService checks eligibility
                                4. If mutual match → create Match
                                5. AppEvent.MatchCreated fires
                                6. MetricsEventHandler records swipe
                                7. AchievementEventHandler checks unlock
                                8. ← 200 OK { "matched": true, "matchId": "..." }

9. Show match popup
```

The phone does step 1 and step 9. Steps 2–8 all run on the server, in Java — untouched and unchanged.

---

## Timeline Summary

| Phase        | What                                               | Effort  | Result                                                         |
|--------------|----------------------------------------------------|---------|----------------------------------------------------------------|
| **Phase 1**  | Polish server, add photos, geocoding, harden seams | Weeks   | Solid local development experience and trustworthy validation  |
| **Phase 2**  | Server reachable from LAN                          | Days    | Phone can talk to server over WiFi                             |
| **Phase 3**  | Build Flutter app (v0.1 MVP)                       | Weeks   | Dating app on your Android phone; iOS later from same codebase |
| **Phase 3+** | Polish mobile app (v0.2–v0.4)                      | Ongoing | Full-featured mobile app                                       |
| **Phase 4**  | Cloud deployment + auth/media/security             | Weeks   | App works from anywhere for real users                         |

**The critical insight:** Phase 3 starts as soon as Phase 2 is done. There's no server migration blocking you. The path is direct: **solid server → reachable server → Flutter app (Android first, iOS later from the same codebase).**

---

*End of Roadmap V2. Created 2026-04-13. Updated to Flutter. The mobile app is a new frontend, not a server rewrite.*