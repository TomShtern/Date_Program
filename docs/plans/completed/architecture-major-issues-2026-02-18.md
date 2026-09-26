# Major Architecture Review - 2026-02-18

## Scope
Review focus: high-impact architecture/design flaws, risky implementations, major duplication, and over-engineered custom UI where native/platform features are better.

## Executive Summary
The largest risks are consistency failures caused by non-atomic multi-storage writes, architecture boundary erosion (presentation directly coupled to storage), and UI state/threading patterns that can produce stale or incorrect data views. There is also material duplication in scoring and formatting logic that will cause drift as rules evolve.

## Findings

### 1) Non-atomic cross-storage state transitions in connection flows
**Severity:** Critical
**Issue:** Connection/friend/graceful-exit flows update interaction state, conversation state, and notifications in separate calls without a transaction boundary.
**Why this is bad:** A partial failure leaves domain state inconsistent (for example, match state changed but conversation/archive/notification missing).
**Impact:** Users can see contradictory states, stale chats, or missing notifications; recovery requires manual compensation logic.
**Suggested solution:** Introduce a single transaction boundary spanning all writes involved in a transition (shared handle or coordinator), or implement explicit compensating rollback for every failure path.
**Evidence:** `src/main/java/datingapp/core/connection/ConnectionService.java:197`, `src/main/java/datingapp/core/connection/ConnectionService.java:202`, `src/main/java/datingapp/core/connection/ConnectionService.java:251`, `src/main/java/datingapp/core/connection/ConnectionService.java:308`

**Progress update (2026-02-19): ✅ Completed**
- Added composite friend-request write API: `CommunicationStorage.saveFriendRequestWithNotification(...)`.
- Implemented transactional social write in `JdbiConnectionStorage` (friend request + notification in one DB transaction).
- Added atomic relationship transition hooks to `InteractionStorage` and implemented DB-transactional accept/graceful-exit persistence in `JdbiMatchmakingStorage` (match + friend request/conversation + notification).
- Updated `ConnectionService` to use atomic transition path when storage supports it, while preserving compatibility fallback for non-transactional in-memory storages.
- Verified with focused regression run: `RelationshipTransitionServiceTest` (8/8 passing).

### 2) Like-to-match write path is not atomic
**Severity:** Critical
**Issue:** Like creation and mutual-match creation are executed as multiple storage calls without transactional wrapping.
**Why this is bad:** Concurrent requests or mid-flow failures can create orphan likes, duplicate matches, or inconsistent downstream metrics/undo state.
**Impact:** Data integrity issues in core matching behavior; difficult-to-reproduce race defects in production.
**Suggested solution:** Wrap `recordLike` persistence flow in a transaction and move side effects (metrics/undo) after successful commit or behind an outbox/event boundary.
**Evidence:** `src/main/java/datingapp/core/matching/MatchingService.java:115`, `src/main/java/datingapp/core/matching/MatchingService.java:146`, `src/main/java/datingapp/core/storage/InteractionStorage.java:15`

**Progress update (2026-02-19): ✅ Completed**
- Added unified persistence contract `InteractionStorage.saveLikeAndMaybeCreateMatch(...)` with structured result object.
- Implemented transactional like→match persistence in `JdbiMatchmakingStorage` (single DB transaction for like upsert + mutual check + match upsert).
- Refactored `MatchingService.recordLike(...)` to use the unified storage operation; metrics side effect remains after persistence result.
- Verified with focused regression run: `MatchingServiceTest` + `EdgeCaseRegressionTest` (10/10 passing).

### 3) Presentation layer bypasses services and couples directly to storages
**Severity:** High
**Issue:** CLI/API wiring pulls raw storage objects and passes them into handlers instead of enforcing service boundaries.
**Why this is bad:** Business rules leak into presentation code, increasing coupling and making future backend changes expensive/risky.
**Impact:** Inconsistent behavior across entry points and reduced maintainability of clean architecture boundaries.
**Suggested solution:** Make handlers/API depend on domain services only; treat storages as internal infrastructure behind service interfaces.
**Evidence:** `src/main/java/datingapp/Main.java:86`, `src/main/java/datingapp/Main.java:96`, `src/main/java/datingapp/Main.java:103`, `src/main/java/datingapp/app/api/RestApiServer.java:73`

**Progress update (2026-02-19): ✅ Completed**
- Refactored `RestApiServer` to consume service-layer APIs (`ProfileService`, `MatchingService`, `ConnectionService`) instead of direct storage interfaces.
- Added handler/service wiring factories so `Main` no longer assembles handlers from raw storage dependencies directly:
	- `MatchingHandler.Dependencies.fromServices(...)`
	- `MessagingHandler.fromServices(...)`
	- `ProfileHandler.fromServices(...)`
	- `SafetyHandler.fromServices(...)`
	- `StatsHandler.fromServices(...)`
- Updated `Main` to use these service-based factories for cleaner boundary ownership.

### 4) Global mutable session object is in core and shared everywhere
**Severity:** High
**Issue:** `AppSession` is a singleton in `core` and is consumed by CLI and JavaFX viewmodel wiring.
**Why this is bad:** Core becomes stateful and globally coupled, reducing test isolation and blocking safe multi-context execution.
**Impact:** Hidden cross-feature coupling and race risk as the app grows (especially with API/concurrency expansion).
**Suggested solution:** Move session lifecycle outside `core` or inject session context per flow/request; avoid global singleton session state for business logic. THINK HARDER ABOUT THIS AND DESIDE IF THIS IS REALLY A PROBLEM OR NOT, AND WHAT IS THE APPROPRIATE SOLUTION IF IT IS A PROBLEM.
**Evidence:** `src/main/java/datingapp/core/AppSession.java:12`, `src/main/java/datingapp/Main.java:77`, `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java:42`

**Progress update (2026-02-19): ✅ Completed**
- Injected `AppSession` into all UI ViewModels via `ViewModelFactory` instead of per-ViewModel static singleton calls.
- Migrated CLI login gate utility to explicit session parameter (`CliTextAndInput.requireLogin(AppSession, Runnable)`) and updated all handler call sites.
- Removed remaining static session usage from `DashboardController` and `Main.printMenu(...)` flow.
- Static singleton access is now confined to composition-root wiring points (bootstrap/factory defaults), not distributed across UI/CLI feature logic.

### 5) Navigation context is a single global untyped payload
**Severity:** High
**Issue:** Navigation stores context in one shared `AtomicReference` and screens consume it implicitly.
**Why this is bad:** Context ownership is unclear; rapid navigation or missing context writes can route users with stale/wrong payloads.
**Impact:** Wrong conversation/chat targets and high UX trust risk.
**Suggested solution:** Use typed per-navigation payloads (`navigateTo(view, context)`), validate ownership by target view, and clear payloads deterministically.
**Evidence:** `src/main/java/datingapp/ui/NavigationService.java:333`, `src/main/java/datingapp/ui/screen/MatchingController.java:410`, `src/main/java/datingapp/ui/screen/ChatController.java:87`

**Progress update (2026-02-19): ✅ Completed**
- Reworked `NavigationService` context storage to use a typed envelope with target view ownership.
- Added typed consume API: `consumeNavigationContext(ViewType consumerView, Class<T> expectedType)` with deterministic clear and mismatch handling.
- Migrated `MatchingController` and `MatchesController` to set target-scoped CHAT context.
- Migrated `ChatController` to typed consume path.
- Kept deprecated legacy methods for backward compatibility during transition.
- Added `NavigationServiceContextTest` (4 tests) to cover correct consume, wrong-view rejection, wrong-type rejection, and legacy compatibility.

### 6) Dashboard refresh spawns unmanaged background work
**Severity:** High
**Issue:** Refresh launches virtual threads repeatedly, but disposal tracks/interrupts only the latest reference.
**Why this is bad:** Earlier threads can continue and post UI updates after dispose/logout, causing stale state races.
**Impact:** Incorrect post-logout data rendering and intermittent UI state corruption.
**Suggested solution:** Track/cancel all in-flight tasks, gate updates with disposal token, and ignore late callbacks after disposed state.
**Evidence:** `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:90`, `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:114`

**Progress update (2026-02-19): ✅ Completed**
- Reworked `DashboardViewModel` refresh lifecycle with:
	- generation token to prevent stale callback application,
	- tracked set of all in-flight refresh threads,
	- cancellation of all in-flight refreshes on new refresh and on dispose,
	- explicit invalidation checks before background work and before FX-thread UI apply.
- This removes stale write windows where older refreshes could overwrite newer state after lifecycle changes.

### 7) UI depends on internal JavaFX API (`com.sun.*`)
**Severity:** High
**Issue:** Viewmodel logic checks toolkit/thread state via internal `com.sun.javafx` classes.
**Why this is bad:** Internal APIs are not stable and can break across JavaFX/JDK updates or restricted runtime environments.
**Impact:** Upgrade fragility and runtime failures in non-standard packaging/deployment.
**Suggested solution:** Replace with supported JavaFX public APIs and explicit lifecycle signals from app bootstrap.
**Evidence:** `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:145`

**Progress update (2026-02-19): ✅ Completed (already resolved in current codebase)**
- Verified current `MatchesViewModel` no longer uses `com.sun.*` internal JavaFX APIs.
- No code changes required for this finding in the current repository snapshot.

### 8) Matches screen rebuilds all cards on each update (no virtualization)
**Severity:** High
**Issue:** The controller clears and recreates all card nodes for section updates and replays animations each time.
**Why this is bad:** Work scales with total list size instead of visible items; unnecessary churn and GC pressure.
**Impact:** UI stutter and poor responsiveness as data size grows.
**Suggested solution:** Replace with `ListView`/virtualized cells, or implement diff-based incremental updates instead of full rebuild.
**Evidence:** `src/main/java/datingapp/ui/screen/MatchesController.java:381`, `src/main/java/datingapp/ui/screen/MatchesController.java:417`

**Progress update (2026-02-19): ✅ Completed**
- Replaced full rebuild strategy in `MatchesController` with per-section incremental diff/caching:
	- card cache keyed by user id,
	- snapshot comparison to recreate only changed cards,
	- stale-card eviction,
	- entrance animations applied only to newly inserted nodes.
- Added cache cleanup in controller `cleanup()`.

### 9) Domain scoring logic duplicated across two matching services
**Severity:** High
**Issue:** Interest/lifestyle scoring exists in both `MatchQualityService` and `RecommendationService`.
**Why this is bad:** Rule changes require multi-file updates and inevitably drift over time.
**Impact:** Users can receive inconsistent compatibility outcomes between product surfaces.
**Suggested solution:** Extract a shared compatibility scorer with centralized weights/config and shared tests.
**Evidence:** `src/main/java/datingapp/core/matching/MatchQualityService.java:463`, `src/main/java/datingapp/core/matching/MatchQualityService.java:476`, `src/main/java/datingapp/core/matching/RecommendationService.java:484`, `src/main/java/datingapp/core/matching/RecommendationService.java:501`

**Progress update (2026-02-19): ✅ Completed**
- Extracted shared scoring primitives into `CompatibilityScoring` (age, interest, lifestyle).
- Refactored both `MatchQualityService` and `RecommendationService` to use shared scoring logic instead of duplicated local implementations.
- Verified with focused regression run (`MatchQualityServiceTest`, `DailyServiceTest`, `DailyLimitServiceTest`, `DailyPickServiceTest`, `StandoutsServiceTest`): 105 tests passed.

### 10) Business rule enforcement is UI-only for interest limits
**Severity:** High
**Issue:** Interest cap is enforced in viewmodel flow, but core model setter accepts arbitrary-size sets.
**Why this is bad:** Non-UI callers can violate the same invariant, creating inconsistent persisted data.
**Impact:** Core assumptions become unreliable and matching behavior can degrade due to invalid profile state.
**Suggested solution:** Enforce limit in core domain/service layer; keep UI check only for immediate user feedback.
**Evidence:** `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:592`, `src/main/java/datingapp/core/model/User.java:578`

**Progress update (2026-02-19): ✅ Completed**
- Enforced interest cap directly in `User` domain model:
	- `setInterests(...)` now validates max size,
	- `addInterest(...)` now throws when adding beyond limit,
	- `StorageBuilder.interests(...)` also validates to prevent persistence-layer bypass.
- Added/updated tests in `UserTest` to cover over-limit add/set behavior.
- Verified with focused regression run: `UserTest` + `ProfileCompletionServiceTest` (53/53 passing).

### 11) DB connection acquisition is serialized by synchronization
**Severity:** Medium-High
**Issue:** `DatabaseManager.getConnection()` is synchronized around acquisition.
**Why this is bad:** Connection checkout becomes single-threaded and undermines pool concurrency.
**Impact:** Throughput bottleneck under concurrent operations (UI + background tasks + API).
**Suggested solution:** Synchronize initialization only; keep pooled connection checkout outside lock.
**Evidence:** `src/main/java/datingapp/storage/DatabaseManager.java:68`

**Progress update (2026-02-19): ✅ Completed**
- Removed method-level synchronization from `DatabaseManager.getConnection()`.
- Kept synchronization only on initialization paths (`initializeSchema()` / `initializePool()`), and moved pooled checkout to an unsynchronized fast path.
- Added defensive null-pool check before checkout.
- Verified with focused regression run: `DatabaseManagerThreadSafetyTest` (2/2 passing).

### 12) Storage schema/model inconsistency and drift risks
**Severity:** Medium-High
**Issue:** Multiple persistence patterns increase long-term integrity risk:
- manual `ALL_COLUMNS` lists duplicate schema shape in DAO code
- `likes.deleted_at` exists but DAO logic uses hard-delete style behavior and queries do not consistently treat soft-delete semantics
- multi-value profile fields are CSV-serialized strings in relational columns
**Why this is bad:** Schema evolution and query correctness become fragile; invalid enum values can be silently ignored; SQL-level filtering/indexing is constrained.
**Impact:** Runtime drift bugs, harder migrations, reduced query performance/correctness for filtering features.
**Suggested solution:** Centralize column mapping metadata, choose one delete semantics model (true soft-delete or hard-delete, think what is the most appropriate for the application's needs), and normalize or strongly-type multi-value fields (join tables/JSON with explicit codecs).
**Evidence:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:42`, `src/main/java/datingapp/storage/schema/SchemaInitializer.java:105`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java:253`, `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:483`

**Progress update (2026-02-19): ✅ Completed**
- Standardized `likes` delete semantics to soft-delete in `JdbiMatchmakingStorage` (`deleted_at` now respected by read/count/mutual queries; `delete` and undo-delete now mark soft-deleted instead of hard delete).
- Added upsert semantics for `likes` keyed by `(who_likes, who_got_liked)` to support safe re-like after soft delete.
- Hardened multi-value persistence in `JdbiUserStorage` by serializing enum sets as JSON arrays (with backward-compatible CSV parsing fallback).
- Reduced mapper drift by routing gender/interest parsing through a single generic enum-set parser.

### 13) Repeated business/UX formatting logic across CLI and UI
**Severity:** Medium
**Issue:** Relative-time and daily-limit/daily-pick messaging logic are duplicated across handlers/viewmodels.
**Why this is bad:** Text/rule changes drift between surfaces and increase maintenance cost.
**Impact:** Inconsistent user messaging between CLI and JavaFX and higher bug surface for simple rule updates.
**Suggested solution:** Centralize shared formatting/rule projection in one reusable service/helper DTO consumed by both surfaces.
**Evidence:** `src/main/java/datingapp/app/cli/MessagingHandler.java:362`, `src/main/java/datingapp/app/cli/MatchingHandler.java:890`, `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:398`, `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:140`

**Progress update (2026-02-19): ✅ Completed**
- Added shared `TextUtil.formatTimeAgo(Instant)` formatter.
- Removed duplicated relative-time formatters from:
	- `MessagingHandler`
	- `MatchingHandler`
	- `MatchesViewModel`
- Updated all affected call sites to use the shared formatter.
- Verified with focused regression runs (`MessagingHandlerTest`, `MatchesViewModelTest`) after refactor.

## Prioritized Remediation Sequence
1. Add transaction boundaries for connection and like/match write flows.
	- **Finding 11 (Medium-High, Owner: Storage/Infra, Quick Win ETA: 1 sprint):** Remove serialized DB checkout in `DatabaseManager.getConnection()` by limiting synchronization to initialization only.
2. Stop presentation-layer direct storage usage; enforce service boundaries.
	- **Finding 7 (High, Owner: UI Platform):** Replace internal JavaFX `com.sun.*` toolkit checks with public JavaFX APIs/lifecycle signals.
3. Replace global navigation payload/session patterns with typed scoped context.
	- **Finding 6 (High, Owner: UI/ViewModel):** Fix dashboard unmanaged background work by tracking/canceling all in-flight refresh tasks and gating late callbacks post-dispose.
	- **Finding 8 (High, Owner: UI Performance):** Move matches rendering to virtualized/diff-based updates instead of full-card rebuilds.
4. Remove UI-only invariant enforcement by moving rules to core.
5. Consolidate duplicated scoring and formatting logic.
6. Address persistence drift risks (column mapping strategy, delete semantics, normalized multi-value fields).
