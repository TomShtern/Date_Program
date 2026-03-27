# Master Project Audit & Code Review Findings
Date: 2026-02-19

> 🚀 **VERIFIED & UPDATED: 2026-03-10**
> This document has been programmatically re-verified against the codebase as of this date.

This document serves as the master consolidation of all previous project review efforts, including:
1. Architecture Review Report
2. Codebase Code Reviews (Gemini 3 Pro High/Low, Gemini 3 Flash, Claude Opus 4.5)
3. Codex Project Audit
4. Workspace Audit

> ⚠️ **Alignment status (2026-03-10): Historical consolidation with live revalidation**
> This file still contains broad architectural concerns that remain useful as backlog, but several concrete feature-gap and test-gap claims are now resolved.
> Refer to current source for authoritative structure/type ownership.

Duplications have been resolved, and findings are categorized for clarity, encompassing high-level design architectural constraints, specific codebase technical debt, project risks, and unimplemented features.

### Revalidation Delta (2026-03-10)
- **Verified resolved since the original audit:** JavaFX now includes dedicated `Safety` and `Notes` screens (`SafetyController`, `NotesController`) reachable from the dashboard, so blocked-user management and profile-notes browsing are no longer CLI-only.
- **Verified resolved since the original audit:** controller-level JavaFX coverage is no longer “CSS-only”. The suite now includes screen tests for `ChatController`, `MatchingController`, `MatchesController`, `ProfileController`, `PreferencesController`, `MilestonePopupController`, plus newly added `NotesControllerTest` and `SafetyControllerTest`.
- **Verified stale terminology:** the report still references `MessagingService` in several historical entries, while the current codebase routes messaging through `ConnectionService` and `MessagingUseCases`.
- **Verified current config guidance:** runtime code should prefer injected `AppConfig`; `AppConfig.defaults()` is appropriate mainly at bootstrap, composition, and test boundaries.
- **Still active as meaningful backlog:** authentication beyond user selection, real-time chat/push updates, global singleton reduction, and large controller/handler decomposition.

---

## 1. Executive Summary
The codebase is technically mature, utilizing modern Java features (Java 25, Records, JDBI) and following strict architectural principles. Significant refactoring campaigns have successfully resolved many major issues (especially concerning configuration centralization, DDL extraction to `SchemaInitializer`, scattered TODO elimination, and Builder pattern adoption for services).

However, architectural risks remain that increase change cost and regression risk, primarily around layer boundary leakage, global mutable state, and large multi-responsibility classes. Furthermore, several thread-safety issues, hardcoded-value anomalies, UI parity gaps, and memory leaks persist in the operational codebase.

---

## 2. Project & Workspace Audit: Key Risks & Gaps

### 2.1 Key Risks (Top Combined Priorities)
1. Data integrity can silently degrade due to foreign key constraints not being reliably applied. (RESOLVED 🟢)
2. The storage layer has no integration tests. (RESOLVED 🟢)
3. Blocking a user does not transition matches or messaging state. (RESOLVED 🟢)
4. Multiple features exist in core/CLI but are missing in JavaFX UI. (RESOLVED 🟢)
5. Documentation and issue trackers are out of sync with current code. (RESOLVED 🟢)
6. AppSession and related caching mechanisms exhibit memory leaks and thread-safety vulnerabilities. (RESOLVED 🟢)

### 2.2 Issue Register (Actionable Findings)

**Thread Safety & Concurrency**
- **FI-WKSP-001**: `User` is mutable + shared via `AppSession`. (RESOLVED 🟢)
- **FI-WKSP-002**: `AppSession.setCurrentUser` race conditions. (RESOLVED 🟢)

**Memory Leaks**
- **FI-WKSP-003**: `SessionService.userLocks` grows unbounded. (RESOLVED 🟢)
- **FI-WKSP-004**: `RecommendationService.cachedDailyPicks` grows unbounded. (RESOLVED 🟢)

**Architecture & API Inconsistencies**
- **FI-WKSP-005**: `UserStorage.get()` returns nullable User instead of Optional. (RESOLVED 🟢)
- **FI-WKSP-006**: `MessagingService.getMessages()` throws instead of returned Result. (VALID 🔴)
- **FI-WKSP-007**: `MessagingService.sendMessage()` spurious `userStorage.save(sender)`. (VALID 🔴)
- **FI-WKSP-008**: Match quality weights don't sum to 1.0. (VALID 🔴)
- **FI-WKSP-009**: `TrustSafetyService` report auto-blocks and auto-bans in same call. (VALID 🔴)
- **FI-WKSP-010**: `MessagingService.getConversations()` unbounded query. (RESOLVED 🟢)

**Feature Logic & UI Gaps**
- **FI-AUD-001**: Blocking does not update match state or conversation visibility. (RESOLVED 🟢)
- **FI-AUD-002**: Conversation archive/visibility fields exist in schema but are unused by service/UI. (RESOLVED 🟢)
- **FI-AUD-003**: Dashboard notifications and unread counts are defined but never populated. (RESOLVED 🟢)
- **FI-AUD-004**: Standouts are implemented in core and CLI but have no JavaFX UI. (RESOLVED 🟢)
- **FI-AUD-005**: Messaging “lastActiveAt” is not actually updated. (RESOLVED 🟢)
- **FI-AUD-006**: Age calculations use system default timezone instead of configured user timezone. (RESOLVED 🟢)
- **FI-AUD-007**: CSV serialization for preferences/sets limits queryability. (RESOLVED 🟢 - Pragmatic choice; formal array mapping adds unnecessary complexity for features not heavily queried.)
- **FI-AUD-008**: UI only supports a single profile photo despite domain allowing two. (RESOLVED 🟢)
- **FI-AUD-009**: Standouts data has no cleanup path and can grow unbounded. (RESOLVED 🟢)
- **FI-AUD-010**: Messaging pagination parameters are not validated. (RESOLVED 🟢)
- **FI-AUD-011**: Configuration documentation mismatch for DB password. (RESOLVED 🟢)

### 2.3 Unimplemented Or Partial Features (Cross-Layer Gaps)
*(All VALID 🟢 unless noted)*
1. JavaFX UI for trust & safety actions (block, report, verify) exists in CLI only. (RESOLVED 🟢 — Block/Report dialogs added to `MatchingController` and `ChatController`; delegates to `MatchingViewModel.blockCandidate/reportCandidate` and `ChatViewModel.blockUser/reportUser`.)
2. JavaFX UI for friend requests and notifications exists in CLI only. (RESOLVED 🟢 — `SocialViewModel`, `SocialController`, and `social.fxml` implemented; tabbed view with Notifications + Friend Requests tabs wired to `ConnectionService` and `UiSocialDataAccess` adapter. Dashboard Social card navigates here.)
3. JavaFX UI for profile notes exists in CLI only. (RESOLVED 🟢 — `NotesViewModel`, `NotesController`, and `notes.fxml` now provide a dedicated notes browser, and note editing is also available inline from Matching and Chat.)
4. JavaFX UI for standouts exists in CLI only. (RESOLVED 🟢 — `StandoutsViewModel`, `StandoutsController`, and `standouts.fxml` implemented; loads from `RecommendationService`, resolves users, exposes `StandoutEntry` records. Dashboard Standouts card navigates here.)
5. Authentication beyond user selection is not implemented. (VALID 🟢)
6. Real-time chat (push or WebSocket) is not implemented; UI relies on manual refresh. (VALID 🟢)
7. Read receipts are stored but not surfaced in UI. (RESOLVED 🟢)
8. Multi-photo profile gallery is not implemented in UI. (RESOLVED 🟢)
9. Notification counts on dashboard are defined but not wired. (RESOLVED 🟢)
10. REST API layer is not implemented (Wait, `RestApiServer.java` is implemented now, so RESOLVED 🟢).

### 2.4 Test And Quality Gaps
1. Storage layer has zero integration tests. (RESOLVED 🟢)
2. StandoutsService and JdbiStandoutStorage have no dedicated tests. (RESOLVED 🟢 - `StandoutsServiceTest.java` exists)
3. Social storage (friend requests/notifications) has no integration tests. (RESOLVED 🟢)
4. UI controllers are mostly untested beyond CSS validation. (PARTIAL 🟡 — controller tests now cover Chat, Matching, Matches, Profile, Preferences, Milestone popup, Notes, and Safety screens; some screens still have lighter direct coverage.)
5. Threading and race-condition tests for viewmodels are absent. (RESOLVED 🟢)

### 2.5 Documentation Drift And Process Debt
1. Multiple issue tracking docs in `docs/uncompleted-plans` appear stale. (RESOLVED 🟢)
**Document Drift and Configuration Status:**
- `DatabaseManager.java` manages H2 connections. For production/standalone mode, it requires the `DATING_APP_DB_PASSWORD` environment variable.
- For local file databases (`jdbc:h2:./...`), it auto-defaults to `dev`.
- For in-memory testing (`jdbc:h2:mem:...`), it auto-defaults to `""`.

---

## 3. Categorized Code & Architecture Review Findings

### 3.1 High-Level Architecture & Domain Boundaries
#### FI-ARCH-001: Layer boundaries are porous and service boundaries are bypassed
*   **Severity**: High
*   **Status**: VALID 🟢
*   **Summary**: Handlers/viewmodels/API frequently bypass core service boundaries. `ServiceRegistry.java` exposes storage objects directly to upper layers. Application entry points and `RestApiServer.java` inject storages directly and mutate state via `interactionStorage.update(match)`. UI adapters expose storage-level operations to ViewModels.
*   **Recommendation**: Enforce strict boundary rules where handlers and UI adapters rely only on service interfaces, not storage.

#### FI-ARCH-002: Global mutable singleton state is pervasive
*   **Severity**: High
*   **Status**: VALID 🟢
*   **Summary**: `ApplicationStartup.java`, `AppSession.java`, `AppClock.java`, and `NavigationService.java` are process-global mutable singletons. Multiple entry points initialize the same global startup path.
*   **Recommendation**: Reduce singleton usage or encapsulate global state to avoid hidden coupling and brittle tests.

#### FI-ARCH-003: Several services are partially constructible and rely on runtime null-check mode switching
*   **Severity**: High
*   **Status**: PARTIAL 🟡
*   **Summary**: Services like `MatchingService` historically left dependencies like `dailyService` and `undoService` as optional nulls, branching behavior at runtime.
*   **Resolution**: Recent refactoring has replaced many multiple constructor overloads with strict Builder patterns and `Objects.requireNonNull`, but some runtime mode switches persist in `MatchingService`.

#### FI-ARCH-004: Large multi-responsibility units and broad interfaces create high change blast radius
*   **Severity**: Medium-High
*   **Status**: VALID 🟢
*   **Summary**: Very large classes with mixed concerns (e.g., `ProfileHandler.java`, `MatchingHandler.java`, `User.java`, `MatchPreferences.java`). Consolidated interfaces cover many subdomains.
*   **Recommendation**: Split large classes into smaller, focused single-responsibility services or components.

### 3.2 Code Architecture & Organization
#### FI-CONS-001: God Object - DatabaseManager
*   **Status**: RESOLVED 🟢
*   **Summary**: `DatabaseManager.java` contained massive embedded SQL DDL strings.
*   **Resolution**: Extracted DDL into `SchemaInitializer.java` and standard schema resources.

#### FI-CONS-002: UI Controller Bloat - ProfileController
*   **Status**: VALID 🟢
*   **Summary**: `ProfileController` is a "God Controller" mixing event handling, complex styling logic, and redundant Enum-to-String converter factories.
*   **Recommendation**: Extract UI utility methods to a `UiUtils` class. Decomposition into sub-controllers or components is recommended.

#### FI-CONS-003: Core Entity Bloat - User.java
*   **Status**: RESOLVED 🟢
*   **Summary**: The `User` class was excessively large, partly due to the inclusion of the complex `ProfileNote` record as a nested entity.
*   **Resolution**: `ProfileNote` was promoted to a standalone top-level record at `core/model/ProfileNote.java`. It is also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention so existing import paths remain valid.

### 3.3 Redundancy & Duplication
#### FI-CONS-004: Duplicated Validation Logic
*   **Status**: RESOLVED 🟢
*   **Summary**: Validation rules were duplicated between domain setters and the validation layer.
*   **Resolution**: Domain setters in `User.java` no longer hardcode or duplicate these rules (e.g. `minAge >= 18`), deferring entirely to `ValidationService.java` which acts as the sole validator using `AppConfig.defaults()`.

#### FI-CONS-005: Proliferation of Test Stubs
*   **Status**: RESOLVED 🟢
*   **Summary**: Many test classes defined their own private in-memory storage stubs.
*   **Resolution**: Consolidated stubs into `TestStorages.java` which is heavily used across the suite.

#### FI-CONS-006: CSS Redundancy & Hardcoded Colors
*   **Status**: RESOLVED 🟢
*   **Summary**: `theme.css` contained redundant rules and hardcoded hex colors.
*   **Resolution**: Replaced with theme variables.

### 3.4 Technical Debt & Smells
#### FI-CONS-007: Broad Exception Catching - MatchingService
*   **Status**: RESOLVED 🟢
*   **Summary**: Used `catch (Exception _)` during match saving.
*   **Resolution**: Changed to `catch (RuntimeException _)`.

#### FI-CONS-008: Manual Dependency Injection Boilerplate
*   **Status**: RESOLVED 🟢
*   **Summary**: Massive blocks of manual wiring in registries.
*   **Resolution**: Organized with clear structured blocks.

#### FI-CONS-009: Scattered TODOs
*   **Status**: RESOLVED 🟢
*   **Summary**: 44+ `TODO` comments were scattered across source files.
*   **Resolution**: Source code has zero TODOs remaining.

### 3.5 Configuration & Constants
#### FI-CONS-010: Fragmented Configuration Constants / Inconsistent Sourcing
*   **Status**: PARTIAL 🟡
*   **Summary**: Critical business logic thresholds were centralized into `AppConfig.java`, but this historical note about `AppConfig.defaults()` is stale.
*   **Resolution**: Current project guidance is to use injected runtime config in production code and reserve `AppConfig.defaults()` mainly for bootstrap, composition, and tests.

### 3.6 Storage & JDBI Optimization
#### FI-CONS-011: JDBI SQL Redundancy & Interface Bloat
*   **Status**: PARTIAL / VALID 🟢
*   **Summary**: JDBI storage mappers contain repetitive SQL column lists.
*   **Resolution**: Extracted `ALL_COLUMNS` constant in some places, but still contains redundancy.

#### FI-CONS-012: Inefficient Complex Type Serialization
*   **Status**: VALID 🟡 (Pragmatic Choice)
*   **Summary**: CSV serialization is fragile.
*   **Recommendation**: Consider JDBI JSON or H2 arrays. Currently accepted as adequate.

#### FI-CONS-013: Over-engineered Stateless Algorithms
*   **Status**: INVALID 🔴
*   **Summary**: Complex logic in CandidateFinder.
*   **Resolution**: Actually well-structured and unit-tested; no simplification needed.

#### FI-CONS-014: Documentation Drift
*   **Status**: RESOLVED 🟢
*   **Summary**: Docs referenced nested storage interfaces instead of standalone.
*   **Resolution**: Documentation synced with reality.

---

## 4. Un-Actionable / Invalid / Resolved Items Ledger
*(For historical reference. Tracking items deemed invalid or successfully resolved over time.)*

- Foreign key enforcement failing silently (RESOLVED 🟢)
- No integration tests for JDBI/storage layer (RESOLVED 🟢)
- Standouts scoring weights hardcoded (RESOLVED 🟢)
- Migration strategy add-columns-only (RESOLVED 🟢)
- Database schema in large Java class (RESOLVED 🟢)
- Image cache eviction not LRU (INVALID 🔴)
- Documentation drift around phase status (RESOLVED 🟢)
- `maxInterests` vs `Interest.MAX_PER_USER` config mismatch (RESOLVED 🟢)
- `User.getInterestedIn()` EnumSet crash (RESOLVED 🟢)
- TrustSafetyService unsafe constructors (RESOLVED 🟢)
- MatchingService constructors inconsistent (RESOLVED 🟢)
- MatchingService catches RuntimeException blindly (RESOLVED 🟢)
- Hardcoded `minAge >= 18` checks (INVALID 🔴)
- User validates distance against config but not photos (INVALID 🔴)
- `AppSession` listeners leak (INVALID 🔴)
- `MatchQualityService` NPE on deleted user (INVALID 🔴)
- `RelationshipTransitionService` throws instead of returned Result (INVALID 🔴)
- `PerformanceMonitor` metrics map leak (INVALID 🔴)

---

## 5. Appendix: Consolidated Mapping of Original IDs

| Consolidated ID | Summary                            | Status                   | Source              |
|:----------------|:-----------------------------------|:-------------------------|:--------------------|
| **FI-ARCH-001** | Layer boundaries bypassed          | VALID 🟢                 | Architecture Review |
| **FI-ARCH-002** | Global mutable singletons          | VALID 🟢                 | Architecture Review |
| **FI-ARCH-003** | Partially constructible services   | PARTIAL 🟡               | Architecture Review |
| **FI-ARCH-004** | Large multi-responsibility units   | VALID 🟢                 | Architecture Review |
| **FI-CONS-001** | DatabaseManager (God Object)       | RESOLVED 🟢              | Code Review         |
| **FI-CONS-002** | ProfileController (Bloat)          | VALID 🟢                 | Code Review         |
| **FI-CONS-003** | User.java (Sprawl) / Nested logic  | RESOLVED 🟢              | Code Review         |
| **FI-CONS-004** | Duplicated Validation              | RESOLVED 🟢              | Code Review         |
| **FI-CONS-005** | Test Stub Proliferation            | RESOLVED 🟢              | Code Review         |
| **FI-CONS-006** | CSS Redundancy                     | RESOLVED 🟢              | Code Review         |
| **FI-CONS-007** | MatchingService Exception Handling | RESOLVED 🟢              | Code Review         |
| **FI-CONS-008** | Manual DI Boilerplate              | RESOLVED 🟢              | Code Review         |
| **FI-CONS-009** | Scattered TODOs                    | RESOLVED 🟢              | Code Review         |
| **FI-CONS-010** | Fragmented / Inconsistent Config   | RESOLVED 🟢 / INVALID 🔴 | Both                |
| **FI-CONS-011** | JDBI SQL Redundancy                | PARTIAL 🟡               | Code Review         |
| **FI-CONS-012** | Inefficient Serialization          | VALID (Pragmatic) 🟡     | Code Review         |
| **FI-CONS-013** | Algorithmic Over-engineering       | INVALID 🔴               | Code Review         |
| **FI-CONS-014** | Documentation Drift                | RESOLVED 🟢              | Code Review         |

## 6. Addendum: Workspace Audit Fixes (Feb 2026 Phase)
| ID              | Summary                                           | Status      | Resolution Notes                                                                                        |
|:----------------|:--------------------------------------------------|:------------|:--------------------------------------------------------------------------------------------------------|
| **FI-WKSP-006** | `getMessages` Exception signature                 | RESOLVED 🟢 | Wrapped response in `MessageLoadResult` record. Call-sites (API, CLI, JavaFX) updated.                  |
| **FI-WKSP-007** | `sendMessage` spurious `user.save`                | INVALID 🔴  | Code review confirms this was already removed in the current codebase state.                            |
| **FI-WKSP-008** | Match weights scale to 1.0 mismatch               | INVALID 🔴  | `AppConfig` weights literally sum to `1.0` exactly (`0.15 + 0.10 + 0.25 + 0.25 + 0.15 + 0.10`).         |
| **FI-WKSP-009** | Duplicated Auto-block/Ban in `TrustSafetyService` | RESOLVED 🟢 | Refactored `report()` to conditionally branch blocking only if `applyAutoBanIfThreshold` returns false. |

## 7. Addendum: Implementation Plan Fixes (2026-02-20)
| ID               | Summary                                                            | Status      | Resolution Notes                                                                                                                                                                                                                                                                                                                                                                                  |
|:-----------------|:-------------------------------------------------------------------|:------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **FI-AUD-001**   | Block does not update match state or conversation visibility       | RESOLVED 🟢 | `TrustSafetyService` now accepts an optional `CommunicationStorage` (5-param constructor). `updateMatchStateForBlock()` archives the conversation from the blocker's perspective (`MatchArchiveReason.BLOCK`) and sets `visibleToBlocker = false`. `StorageFactory` passes `communicationStorage` to the service. 4 new tests in `TrustSafetyServiceTest.BlockWithConversation`.                  |
| **FI-AUD-002**   | Archive/visibility fields unused by service/UI                     | RESOLVED 🟢 | Fields are now fully used: `ConnectionService.unmatch()` archives both sides with `MatchArchiveReason.UNMATCH`; `TrustSafetyService.block()` archives the blocker's side with `MatchArchiveReason.BLOCK` and hides it. `MatchingHandler` routes unmatch through `ConnectionService.unmatch()` instead of directly manipulating the `Match` entity. 5 new tests in `MessagingServiceTest.Unmatch`. |
| **FI-AUD-005**   | Messaging `lastActiveAt` not updated                               | RESOLVED 🟢 | Pre-existing: `ConnectionService.sendMessage()` already called `activityMetricsService.recordActivity()` (when non-null). `ActivityMetricsService.recordActivity()` updates session `lastActivityAt`. Confirmed in code review — no change required.                                                                                                                                              |
| **FI-AUD-006**   | Age uses system timezone instead of config timezone                | RESOLVED 🟢 | Pre-existing: `User.getAge()` already calls `AppConfig.defaults().safety().userTimeZone()` for the timezone. Confirmed in code review — no change required.                                                                                                                                                                                                                                       |
| **FI-AUD-009**   | Standouts data has no cleanup path                                 | RESOLVED 🟢 | Pre-existing: `ActivityMetricsService.runCleanup()` already called `analyticsStorage.deleteExpiredStandouts()`. `CleanupResult` now also reports `standoutsDeleted` in its `toString()` (field was missing). Fixed pre-existing bug where `CleanupResult.toString()` omitted `standoutsDeleted` and used abbreviated field names, causing a test assertion failure.                               |
| **FI-CONS-003**  | `ProfileNote` nested in `User` causes bloat                        | RESOLVED 🟢 | `ProfileNote` was extracted to a standalone top-level record at `core/model/ProfileNote.java`. Also re-exported as `User.ProfileNote` (public static nested) per the 2026-02-19 re-nesting convention for import-path compatibility.                                                                                                                                                              |
| *(pre-existing)* | `JdbiConnectionStorage.ConversationMapper` read stale column names | RESOLVED 🟢 | Mapper was reading obsolete `archived_at`/`archive_reason` columns instead of the split-schema aliases `user_a_archived_at`, `user_a_archive_reason`, `user_b_archived_at`, `user_b_archive_reason`. Fixed to read all 4 aliases and call the 13-param `Conversation` constructor. Root cause of 3 `MessagingHandlerTest` failures.                                                               |
| *(pre-existing)* | `RelationshipTransitionServiceTest` used deleted accessor methods  | RESOLVED 🟢 | Test called `getArchivedAt()`/`getArchiveReason()` which were removed when the schema was split into per-user fields. Updated to assert `getUserAArchivedAt()`, `getUserBArchivedAt()`, `getUserAArchiveReason()`, `getUserBArchiveReason()`.                                                                                                                                                     |

## 8. Addendum: UI Feature Parity Implementation (2026-02-21)
| ID                       | Summary                                                                   | Status      | Resolution Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|:-------------------------|:--------------------------------------------------------------------------|:------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **FI-AUD-004**           | Standouts UI missing in JavaFX                                            | RESOLVED 🟢 | `StandoutsViewModel` (`ui/viewmodel/`) loads standouts via `RecommendationService.getStandouts()` + `resolveUsers()` on a virtual thread, exposes `ObservableList<StandoutEntry>` (inner record combining `Standout` + resolved `User`). `StandoutsController` (`ui/screen/`) wires list selection to `markInteracted()` + navigation to Matching. `standouts.fxml` provides header with Back/Refresh + ranked list. Dashboard Standouts card + `NavigationService.ViewType.STANDOUTS` wired.                                                                                                                                 |
| **FI-WKSP-001 (UI)**     | Trust & safety block/report missing from JavaFX Matching and Chat screens | RESOLVED 🟢 | Block and Report buttons added to `matching.fxml` (over candidate card) and `chat.fxml` (conversation action bar). `MatchingController.handleBlock/handleReport` delegates to `MatchingViewModel.blockCandidate/reportCandidate`. `ChatController.handleBlock/handleReport` delegates to `ChatViewModel.blockUser/reportUser`. Both use a `Dialog<Report.Reason>` with `UiUtils.createEnumStringConverter()` for clean enum display.                                                                                                                                                                                          |
| **2.3 item 2**           | Social (friend requests + notifications) UI missing in JavaFX             | RESOLVED 🟢 | `UiSocialDataAccess` adapter interface + `StorageUiSocialDataAccess` impl added to `UiDataAdapters.java` (wraps `CommunicationStorage`). `SocialViewModel` loads notifications and pending friend requests on virtual threads, resolves sender names via `UiUserStore.findByIds()` batch lookup, exposes `FriendRequestEntry` inner record with `fromUserName`. `SocialController` renders a two-tab `TabPane` (Notifications + Friend Requests). `social.fxml` wired. Dashboard Social card + `NavigationService.ViewType.SOCIAL` wired. Accept/Decline delegates to `ConnectionService.acceptFriendZone/declineFriendZone`. |
| **2.4 item 5 (new VMs)** | ViewModel threading tests for new screens absent                          | RESOLVED 🟢 | `StandoutsViewModelTest` (5 tests): pre-seeded standout storage for determinism, verifies async load, `StandoutEntry` delegation, dispose-prevents-update, `markInteracted` idempotency. `SocialViewModelTest` (7 tests): notification load, friend-request name resolution, UUID fallback for unresolvable sender, `markNotificationRead`, already-read no-op, concurrent refresh safety, dispose clears both lists.                                                                                                                                                                                                         |
| *(pre-existing)*         | `ChatViewModelTest` race condition and non-deterministic ordering         | RESOLVED 🟢 | Two pre-existing bugs: (1) `setCurrentUser()` triggering an async refresh in setUp could post a stale `updateConversations([])` after the test's own refresh — fixed by adding `Thread.sleep(300)` + FX drain at end of setUp. (2) Conversations with equal `lastMessageAt` (fixed clock) had non-deterministic index order — fixed by looking up conversations by `otherUser.getId()` instead of assuming `get(0)`/`get(1)` positions.                                                                                                                                                                                       |
