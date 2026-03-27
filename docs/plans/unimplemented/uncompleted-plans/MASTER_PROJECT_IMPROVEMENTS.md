# MASTER PROJECT IMPROVEMENTS & ENHANCEMENTS
**Consolidated Analysis** | **Status:** Synthesized | **Source Files:** 3

This document is a **comprehensive consolidation** of all project improvement suggestions, enhancements, and analysis reports. It combines functionality, architecture, security, and developer experience recommendations into a single master list.

---

## 🏗️ 1. Architecture & Core Engineering

### AR-01: Modular / Unified Registry System
**Problem:** `ServiceRegistry` is a monolithic God Class (~380+ lines).
**Solution:** Refactor into a **Module-based Registry** (e.g., `UserModule`, `MatchingModule`) or a **Unified `ApplicationContext`**.
**Details:**
*   Decouples wiring logic and reduces merge conflicts.
*   Consider using a compile-time DI framework like **Dagger**, **Micronaut**, or **Avaje Inject** to remove manual wiring code in `Main.java` entirely.
*   *Legacy Ref:* F1-32, F2-1, F3-1.

### AR-02: Event-Driven Architecture (Internal EventBus)
**Problem:** Services are tightly coupled (e.g., `RelationshipService` calling `NotificationStorage` directly). logic is strictly procedural.
**Solution:** Introduce an internal **EventBus** (Guava, simple Observer, or Java `Flow`).
**Details:**
*   Publish events like `MatchCreatedEvent`, `UserLikedEvent`.
*   Listeners (`NotificationListener`, `StatsListener`) subscribe independently.
*   Decouples domains: Adding a new reaction (e.g., email / push) requires zero changes to the core logic.
*   *Legacy Ref:* F1-4, F2-2, F3-2.

### AR-03: Rich Domain Models (DDD) & State Enforcement
**Problem:** `User.java` and `Match.java` are large, but logic leaks into Services. State transitions rely on implicit checks.
**Solution:** Move logic "closer to the data" and enforce states strictly.
**Details:**
*   **State Machines:** Use a structured pattern/library to enforce `User.State` and `Match.State` transitions (e.g. `INCOMPLETE -> ACTIVE`).
*   **Rich Error Hierarchy:** Replace generic `IllegalStateException` with `InvalidTransitionException`, `ActionBlockedException`.
*   **Logic:** `User.canMatchWith(User other)` instead of external validation.
*   *Legacy Ref:* F1-5, F1-6, F3-6.

### AR-04: Generic Repository Pattern
**Problem:** Identical CRUD code (`save`, `get`, `delete`) copied across 20+ `*Storage` files.
**Solution:** Create a `Repository<T, ID>` interface and `AbstractH2Repository<T>` implementation.
**Details:**
*   Reduces storage layer boilerplate by ~30-40%.
*   Allows shared logic for things like "Find All" or "Delete by ID".
*   **Refinement:** Consider **JOOQ** or **JDBI** inside the repository for type-safe SQL construction instead of raw strings.
*   *Legacy Ref:* F1-38, F2-3/4, F3-4.

### AR-05: Result Pattern & Error Handling
**Problem:** Implicit nulls and unchecked exceptions control flow.
**Solution:** Introduce a generic `Result<T, E>` or `Either<L, R>` type.
**Details:**
*   Usage: `Result<User, Info> register(...)`.
*   Forces developers to handle failure cases explicitly (Validation errors vs System errors).
*   *Legacy Ref:* F3-3.

### AR-06: Async/Non-Blocking Core
**Problem:** Heavy operations (Matching, Bulk Updates) block the main/UI thread.
**Solution:** Fully leverage **Java 25 Virtual Threads**.
**Details:**
*   Return `CompletableFuture` or use `ExecutorService` with Virtual Threads for inputs.
*   Ensure UI thread is NEVER blocked.
*   *Legacy Ref:* F1-44, F3-8.

### AR-07: Feature Flags System
**Problem:** New features are hardcoded or commented out.
**Solution:** Implement a `FeatureFlagService`.
**Details:**
*   Usage: `if (flags.isEnabled(Feature.VIDEO_CHAT)) ...`.
*   Allows Trunk-based development and safe "Phase 2" rollouts.
*   *Legacy Ref:* F2-5, F3-7.

---

## 💾 2. Data, Persistence & Storage

### DP-01: Connection Pooling (HikariCP)
**Problem:** `DatabaseManager` creates connections on demand or manages them poorly.
**Solution:** Integrate **HikariCP**.
**Details:**
*   Industry standard for high-performance JDBC pooling.
*   Resilience to timeouts and concurrency issues.
*   *Legacy Ref:* F1-37, F2-6, F3-11.

### DP-02: Database Migrations (Flyway/Liquibase)
**Problem:** Schema initialized via manual `CREATE TABLE IF NOT EXISTS` in code. Fragile.
**Solution:** Integrate **Flyway** or **Liquibase**.
**Details:**
*   Manage versioned SQL scripts (`V1__Init.sql`, `V2__AddIndex.sql`).
*   Deterministic state for production and collaboration.
*   *Legacy Ref:* F1-36, F2-7, F3-5.

### DP-03: Entity Caching (L1/L2)
**Problem:** Repeated DB reads for static data (like `User` profile) during swiping.
**Solution:** Implement **Caffeine Cache**.
**Details:**
*   Cache `MatchQuality` scores or `User` objects by ID.
*   Drastic reduction in DB I/O for high-frequency loops.
*   *Legacy Ref:* F1-10/19, F2-9, F3-9.

### DP-04: Advanced Spatial Support
**Problem:** Java-side Haversine calculation is slow for large datasets; loads all users.
**Solution:** Move spatial logic to DB or optimize query.
**Details:**
*   **Option A:** Use **H2GIS** extension for `ST_Distance`.
*   **Option B (Preferred):** SQL-level Bounding Box filtering (`WHERE lat BETWEEN min AND max`).
*   **Refinement:** Integrate specific Geocoding service to convert Zips/Cities to coords.
*   *Legacy Ref:* F1-2, F1-21, F2-8, F3-10.

### DP-05: Data Integrity & Recovery
**Problem:** `DELETE` destroys data permanently. Local DBs are risky.
**Solution:** Implement protection mechanisms.
**Details:**
*   **Soft Deletes:** Add `deleted_at` column; filter globally. (F2-10, F3-12).
*   **Automated Backups:** Trigger to copy `.mv.db` to `backups/` on shutdown. (F3-23).
*   **Multi-Backend:** Support config switch to PostgreSQL/MySQL for prod. (F1-40).

### DP-06: Specialized Domain Objects
**Problem:** Primitive fields led to confusion.
**Solution:** Introduce optimized Value Objects.
**Details:**
*   **Location:** Record with lat/lon and distance logic.
*   **Preferences:** Formal entity separate from User.
*   **Money/Subscriptions:** `Wallet` or `Subscription` model for premium.
*   **Object Storage:** `BlobStorage` interface (Local vs S3) for photos.
*   *Legacy Ref:* F1-2/3/7/13, F2-11.

---

## 🧠 3. Logic, Intelligence & Matching

### LG-01: Advanced Recommendation Engine
**Problem:** Basic rule-based filtering (Age/Distance).
**Solution:** Define `RecommendationSPI` to swap strategies.
**Details:**
*   **Strategies:** Distance-based, Interest-based, or **Collaborative Filtering** ("Users who liked you also liked...").
*   **Pagination:** Cursor-based pagination in `CandidateFinder` to handle large sets.
*   *Legacy Ref:* F1-8/9, F3-10/13.

### LG-02: Smart "Vibe" & Ghosting Logic
**Problem:** Matches are static and can go stale.
**Solution:** Add intelligence to the lifecycle.
**Details:**
*   **Vibe Check:** Logic in `MatchQualityService` using sentiment analysis or chat style.
*   **Ghosting Detection:** Flag inactive matches; suggest icebreakers or auto-archive.
*   *Legacy Ref:* F1-13, F1-14.

### LG-03: Batch Processing
**Problem:** "Daily Picks" might be slow if generated on-demand.
**Solution:** Background processing.
**Details:**
*   **Batch Processing:** Generate Daily Picks in overnight batch or background Virtual Thread.
*   **Background Jobs:** Integrate **Quartz Scheduler** for periodic tasks like "Cleanup Stale Sessions" or "Purge Deleted Users".
*   *Legacy Ref:* F1-11, F2-27.

---

## 🛡️ 4. Security, Trust & Safety

### SEC-01: Security Hardening (PII & Auth)
**Problem:** PII stored in plain text; inputs trusted.
**Solution:** Apply industry standards.
**Details:**
*   **Encryption:** Encrypt email/phone at rest (AES-256).
*   **Hashing:** Use **Argon2** for passwords/verification codes.
*   **Sanitization:** Use **OWASP Java Encoder** to strip HTML/Script from bios.
*   *Legacy Ref:* F1-16, F2-12/14/22.

### SEC-02: Rate Limiting & Circuit Breaking
**Problem:** Vulnerable to brute-force or bot scripts.
**Solution:** Resilience patterns.
**Details:**
*   **Rate Limiting:** **Bucket4j** implementation on API/Service level (swipes/sec).
*   **Circuit Breaker:** **Resilience4j** to fail fast if DB/External Service hangs.
*   *Legacy Ref:* F1-17, F2-13/15, F3-21.

### SEC-03: Real Verification & Moderation
**Problem:** Simulated security is insufficient.
**Solution:** Real integrations.
**Details:**
*   **Verification:** Integrate SendGrid/Twilio for real codes.
*   **Moderation:** NLP/Keywords to flag aggression/spam in Bios/Chat.
*   **Reporting:** Allow attaching "Evidence" (logs/snapshots) to reports.
*   **Shadow Banning:** Logic to "hide" bad actors without notifying them.
*   *Legacy Ref:* F1-15/18/19/20, F3-14.

### SEC-04: Audit Logging
**Problem:** Security-critical events are just regular logs.
**Solution:** Dedicated `AuditService`.
**Details:**
*   Log into separate immutable table/file (Bans, Exports, Admin actions).
*   *Legacy Ref:* F1-24, F2-16.

---

## 🎨 5. User Experience & Application Features

### UX-01: UI Modernization (JavaFX)
**Problem:** Basic UI feel.
**Solution:** Polish and responsiveness.
**Details:**
*   **Thematic Animations:** "Confetti" on match (AtlantaFX).
*   **Custom Swipe:** "Card Stack" component with physics/gestures.
*   **Theme Engine:** Hot-swap Light/Dark modes (sync with OS).
*   **Unified Icons:** Implement `IconFactory` (using Ikonli) to standardize sizing and style across the app.
*   **Accessibility:** Audit contrast, focus traversal, and screen reader labels.
*   *Legacy Ref:* F1-26/27/28, F2-23/26, F3-18/31.

### UX-02: Feature Enhancements
**Problem:** Limited interaction usage.
**Solution:** Premium/Engagement features.
**Details:**
*   **Undo History:** Stack-based Undo (last 5 actions) vs single undo.
*   **Super Like:** Weighted like with attached intro message.
*   **Incognito Mode:** "Only see people I liked".
*   **Rich Text Bios:** Markdown support.
*   **Notifications:** System Tray integration for background alerts.
*   *Legacy Ref:* F2-21/24/25, F3-15/19/20.

### UX-03: Internationalization (i18n)
**Problem:** Hardcoded strings.
**Solution:** Externalize to `ResourceBundle`.
**Details:**
*   Move text to `messages.properties`.
*   Support locale switching.
*   *Legacy Ref:* F1-30, F2-22, F3-16.

### UX-04: Real-Time Messaging
**Problem:** Polling DB is inefficient.
**Solution:** WebSockets or Push.
**Details:**
*   Upgrade `MessagingService` to use WebSockets or internal Pub/Sub.
*   *Legacy Ref:* F2-21.

---

## 🛠️ 6. Developer Experience, Testing & Ops

### DX-01: Advanced Testing Strategy
**Problem:** In-memory mocks are not enough.
**Solution:** Production-grade test harness.
**Details:**
*   **TestContainers:** Run tests against real H2/Postgres/Docker.
*   **ArchUnit:** Enforce architecture (e.g., "Core cannot read UI").
*   **Property-Based (Jqwik):** Generative testing for invariants.
*   **Mutation Testing (PITest):** Validating test quality.
*   **Test Fixtures:** Create `TestDataFactory` for fluent user generation in tests.
*   **UI Testing:** Headless automation (TestFX).
*   *Legacy Ref:* F2-17/18/19/20, F3-25/26/27/28.

### DX-02: Observability & Tooling
**Problem:** "Black box" runtime.
**Solution:** Insights.
**Details:**
*   **Structured Logging:** JSON logs (Logstash) with Correlation IDs (MDC).
*   **Metrics:** Micrometer integration (Timer, Guage).
*   **Developer Dashboard:** Hidden view to inspect DB/State.
*   **Health Checks:** SPI for component status.
*   *Legacy Ref:* F1-33/34/35, F2-29, F3-24/29.

### DX-03: Build & Deployment
**Problem:** Manual processes.
**Solution:** Automation.
**Details:**
*   **Pre-commit Hooks:** Run Spotless/Checkstyle before commit.
*   **Docker Compose:** One-command "Full Stack" (App + DB + Monitoring).
*   **Modules:** Full JPMS (`module-info.java`) support.
*   **CLI Args:** Deep linking for easy testing (`--user=... --screen=...`).
*   *Legacy Ref:* F2-28/30, F3-17/32.
