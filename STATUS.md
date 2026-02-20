<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1 (recheck before write),
# 2) locate affected doc fragment using prioritized search (see below),
# 3) archive replaced text with <!--ARCHIVE:SEQ:agent:scope-->...<!--/ARCHIVE-->,
# 4) apply minimal precise edits (edit only nearest matching fragment),
# 5) append one ChangeStamp line to the file-end changelog and inside the edited fragment (immediately after the edited paragraph or code fence),
# 6) if uncertain to auto-edit, append TODO+ChangeStamp next to nearest heading.
<!--/AGENT-DOCSYNC-->

# Current System Status
> **Generated:** 2026-02-19
> **Source:** Derived strictly from codebase analysis.

This document represents the current, verifiable state of the Dating App codebase, overriding all historical PRDs and outdated documentation.

---

## 1. Core Architecture & Tech Stack

The application strictly enforces a separation of concerns, heavily prioritizing a pure domain layer.

*   **Language:** Java 25 (preview features enabled).
*   **Build System:** Maven.
*   **Inversion of Control:** Manual Dependency Injection via `ServiceRegistry` and `StorageFactory`. No Spring or external DI containers are used.
*   **Database:** H2 (Embedded).
*   **Persistence Layer:** JDBI 3 (mapping SQL to Java models).
*   **Graphical Interface:** JavaFX 25 with AtlantaFX theming (MVVM pattern).
*   **Console Interface:** Custom Java CLI loop.
*   **REST API:** Javalin 6.7.0.

---

## 2. Infrastructure & Storage (`datingapp.storage.jdbi`)

The database schema (`SchemaInitializer.java`) dictates the true capabilities of the system. There are 18 active tables handling the domain definitions:

1.  **Core Entities:**
    *   `users` (Profile details, preferences, state, location).
    *   `likes` (Swipes natively tracked).
    *   `matches` (Mutual likes creating an active match).
    *   `swipe_sessions` (Tracking user activity blocks).
2.  **Messaging & Social:**
    *   `conversations` & `messages` (Chat persistence).
    *   `friend_requests`
    *   `notifications`
3.  **Trust & Safety (Moderation):**
    *   `blocks` (Hard blocks preventing future interaction).
    *   `reports` (Rule violations).
4.  **Analytics & Features:**
    *   `user_stats` & `platform_stats`
    *   `daily_pick_views` & `standouts` (Daily feed algorithms).
    *   `user_achievements`
    *   `profile_notes` & `profile_views`
    *   `undo_states` (Time-windowed swipe reversal).

### JDBI Implementations
Storage interacts through flat DAO classes targeting 5 core interfaces:
*   `JdbiUserStorage`
*   `JdbiMatchmakingStorage`
*   `JdbiMetricsStorage`
*   `JdbiConnectionStorage`
*   `JdbiTrustSafetyStorage`

---

## 3. Core Domain Models (`datingapp.core.model`)

The fundamental application actors.

### User
State machine based entity transitioning between `INCOMPLETE`, `ACTIVE`, `PAUSED`, and `BANNED`. Tracks base demographics, detailed filter preferences (distance, age), and profile data.

### Match
A connection between two users with a deterministically generated ID (`userA_userB` sorted). Has its own active state lifecycle.

---

## 4. Operational Services (`datingapp.core`)

The business logic resides entirely in the `datingapp.core` package suite, completely separated from JDBI or HTTP.

*   **Matching:** `CandidateFinder` (filters candidates), `MatchQualityService` (scores compatibility), `MatchingService` (handles likes/passes and mutual logic), `RecommendationService`.
*   **Profile & Validation:** `ProfileService`, `ValidationService` (guarantees profile integrity against `AppConfig`).
*   **Trust & Safety:** `TrustSafetyService` (interpreting blocks and auto-banning).
*   **Metrics:** `ActivityMetricsService` (monitors swipe velocity and engagement).
*   **Connection:** `ConnectionService` (Messaging orchestration).

---

## 5. Presentation Layers

The app supports multiple co-existing interfaces.

### JavaFX GUI (`datingapp.ui`)
Implements the MVVM (Model-View-ViewModel) pattern, with `ViewModelFactory` handling injection.
Currently features 10 active controllers (e.g., `ProfileController`, `MatchingController`, `ChatController`, `DashboardController`, `MilestonePopupController`).

### CLI Console (`datingapp.app.cli`)
Acts as a fallback or debugging interface with 5 primary handlers:
*   `MatchingHandler`
*   `ProfileHandler`
*   `MessagingHandler`
*   `SafetyHandler`
*   `StatsHandler`

### REST API (`datingapp.app.api`)
A single Javalin server implementation (`RestApiServer.java`) providing HTTP endpoints for client consumption.

---

## 6. Verifiable Status Conclusions

1.  **Strict Layering:** The `core` layer genuinely remains pure, with `storage` and presentation depending on it.
2.  **Comprehensive Tooling:** Both JavaFX and CLI interfaces are actively developed alongside each other.
3.  **Data Maturity:** The schema reveals a highly mature domain extending far beyond basic matching into comprehensive behavioral analytics, safety moderation, and social interactions.

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
1|2026-02-19 23:05:00|agent:antigravity|docs|Completely rewrote STATUS.md based on active code structure instead of outdated PRDs|STATUS.md
---AGENT-LOG-END---
