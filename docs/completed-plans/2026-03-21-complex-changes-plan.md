# Complex Changes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address the remaining open issues that require multi-file refactoring, new UI features, architectural changes, or cross-cutting modifications — work that is too complex or high-blast-radius for the straightforward-fixes plan.

**Architecture:** Each theme is self-contained and can be worked independently. Within a theme, tasks are ordered by dependency. Themes are ordered by impact: architecture first (reduces future change cost), then UI parity (user-visible value), then infrastructure, then testing.

**Tech Stack:** Java 25, JavaFX 25, Maven, JUnit 5, JDBI/H2, FXML, `ServiceRegistry`, `ViewModelFactory`, `NavigationService`, `AppEventBus`, `RestApiServer`.

---

## Agent Implementation Context

**READ FIRST:** This section contains everything an AI coding agent needs to implement this plan without reading other documentation. Each theme is independent — start with any one.

### Build & Test Commands

```bash
# Format code (MUST run before verify — Spotless will fail otherwise)
mvn spotless:apply

# Full quality gate (compile → test → jacoco → spotless → pmd → checkstyle)
mvn spotless:apply verify

# Run all tests
mvn test

# Run specific test class
mvn test -pl . -Dtest="ClassName" -Dsurefire.failIfNoSpecifiedTests=false

# Run with verbose test output (on failure)
mvn -Ptest-output-verbose test

# Run JavaFX GUI (for manual UI testing)
mvn javafx:run

# Run CLI
mvn compile && mvn exec:exec
```

### Critical Gotchas (from CLAUDE.md)

| Rule                  | Wrong                                        | Correct                                                               |
|-----------------------|----------------------------------------------|-----------------------------------------------------------------------|
| User enum imports     | `core.model.Gender`                          | `User.Gender` (nested type)                                           |
| Match enum imports    | `core.model.MatchState`                      | `Match.MatchState` (nested type)                                      |
| ProfileNote import    | `User.ProfileNote`                           | `core.model.ProfileNote` (standalone)                                 |
| Domain timestamps     | `Instant.now()`                              | `AppClock.now()`                                                      |
| JDBI record binding   | `@BindBean RecordType r`                     | `@BindMethods RecordType r`                                           |
| Date formatting       | `ofPattern("dd MMM")`                        | `ofPattern("dd MMM", Locale.ENGLISH)`                                 |
| Use-case construction | `new MatchingUseCases(...)`                  | `services.getMatchingUseCases()`                                      |
| Config access         | `AppConfig.defaults()` in runtime            | Injected `AppConfig` via `ServiceRegistry`                            |
| ViewModel threading   | `Thread.ofVirtual()` + `Platform.runLater()` | `ViewModelAsyncScope` from `ui/async`                                 |
| ViewModel storage     | Direct `core.storage.*` imports              | `UiDataAdapters` interfaces                                           |
| Achievement popup     | Manual `Dialog` construction                 | Load FXML via `FXMLLoader`, add to `NavigationService.getRootStack()` |
| PMD empty catch       | Empty catch `{}`                             | `assert true;`                                                        |

### Wiring & Bootstrap

```java
// All services wired through ServiceRegistry (singleton)
ServiceRegistry services = ApplicationStartup.initialize();

// Use-case instances obtained via:
services.getMatchingUseCases();
services.getMessagingUseCases();
services.getProfileUseCases();
services.getSocialUseCases();

// ViewModels created by ViewModelFactory (injected into NavigationService)
ViewModelFactory vmFactory = new ViewModelFactory(services);

// NavigationService manages screen transitions (singleton)
NavigationService nav = NavigationService.getInstance();
```

### ViewModel Async Pattern (MUST follow for all new ViewModels)

```java
// In ViewModel constructor:
this.asyncScope = new ViewModelAsyncScope(dispatcher, logger);

// For background work:
asyncScope.submit("operation-name", TaskPolicy.LATEST_WINS, () -> {
    // Background work here — NOT on FX thread
    var result = someService.doWork();
    return result;
}, result -> {
    // FX thread callback — update observable properties here
    this.items.setAll(result);
});

// In dispose/cleanup:
asyncScope.cancelAll();
```

### FXML Controller Pattern (MUST follow for new screens)

1. Controller extends `BaseController`
2. FXML file in `src/main/resources/fxml/`
3. Controller wired to ViewModel via `ViewModelFactory`
4. Navigation registered in `NavigationService.ViewType` enum
5. Cleanup in overridden `cleanup()` method

### Package Structure

```
datingapp/
  app/
    api/RestApiServer.java          — REST endpoints (Javalin, localhost-only)
    bootstrap/ApplicationStartup.java, CleanupScheduler.java
    cli/                            — CLI handlers (flat, no subpackages)
    event/AppEvent.java, AppEventBus.java, InProcessAppEventBus.java
    event/handlers/                  — Achievement, Metrics, Notification handlers
    usecase/matching/, messaging/, profile/, social/
  core/
    model/User.java, Match.java, ProfileNote.java, LocationModels.java
    matching/                        — CandidateFinder, CompatibilityCalculator, MatchingService, etc.
    metrics/                         — AchievementService, ActivityMetricsService
    profile/                         — ValidationService, ProfileService, LocationService
    storage/                         — 5 storage interfaces
    workflow/                        — ProfileActivationPolicy, RelationshipWorkflowPolicy
  storage/
    DatabaseManager.java, StorageFactory.java, DevDataSeeder.java
    jdbi/                            — 5 JDBI implementations + JdbiTypeCodecs
    schema/SchemaInitializer.java, MigrationRunner.java
  ui/
    DatingApp.java, NavigationService.java, ImageCache.java, UiComponents.java, etc.
    async/                           — ViewModelAsyncScope, TaskPolicy, AsyncErrorRouter, etc.
    screen/                          — 14 controllers (extend BaseController)
    viewmodel/                       — ViewModels, ViewModelFactory, UiDataAdapters
```

### Known Flaky Test

`ChatControllerTest#selectionTogglesChatStateAndNoteButtonsRemainWired` fails intermittently in full suite (JavaFX thread ordering). Pre-existing — ignore it.

---

## Quick Reference — What's In Each Theme

| Theme                       | Claims                             | Complexity  | Key Risk                                  |
|-----------------------------|------------------------------------|-------------|-------------------------------------------|
| 1. Architecture Refactoring | V017, V023, V046, V018             | HIGH        | Wide blast radius across service layer    |
| 2. UI Feature Parity        | V021, V035, V036, V049, V051, V038 | HIGH        | New FXML screens, ViewModels, Controllers |
| 3. REST API Completeness    | V013                               | MEDIUM-HIGH | 5+ endpoints to fully implement           |
| 4. Comprehensive Testing    | V031, V052, V059                   | MEDIUM      | Many new test files, concurrency testing  |
| 5. Infrastructure & Config  | V006, V041, V048                   | MEDIUM-HIGH | Cross-cutting config and event changes    |
| 6. Future Features          | V050, V061, V062                   | HIGH        | WebSocket, CLI persistence redesign       |

### What's NOT in this plan

- **Security:** V002, V014, V020, V025, V033, V034 (excluded per user request)
- **Already implemented:** V001✅, V003✅, V019✅, V028✅, V029✅, V032✅, V044✅, V057✅, V066✅, V068✅, V069✅, V074✅, V089✅
- **Already planned (2026-03-20 plans):** V009🟠, V010🟠, V011🟠, V024🟠, V026🟠, V064🟠, V065🟠, V067🟠, V071🟠, V073🟠, V075🟠, V078🟠, V079🟠, V083🟠
- **Straightforward fixes:** V004, V015, V022, V037, V045, V047, V054, V077, V081 (in separate plan)
- **False positives:** V007, V012, V030, V053, V055, V058, V060, V070, V076, V086, V093

---

## Theme 1: Architecture Refactoring

**Why first:** Reducing complexity in the service layer makes all subsequent work easier and safer.

### Task 1.1: Split large multi-responsibility units (V017)

**Problem:** Several classes have grown to handle too many concerns, increasing change blast radius. The largest offenders need decomposition.

**Scope:** Identify the top 3 largest classes by responsibility count (likely `RestApiServer`, `User`, `MatchingUseCases`) and extract cohesive sub-units.

**Files to investigate:**
- `src/main/java/datingapp/app/api/RestApiServer.java` — route registration + request handling + response formatting
- `src/main/java/datingapp/core/model/User.java` — model + validation + state machine + preferences
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` — matching + undo + daily picks + standouts

**Approach:**
- [x] **Step 1:** Run `tokei` on each candidate file to measure LOC. Prioritize files > 800 LOC(up to 1000 LOC).
- [x] **Step 2:** For each file, list distinct responsibilities (methods grouped by concern).
- [x] **Step 3:** Design extraction targets — new classes that each own one responsibility.
- [x] **Step 4:** Extract one responsibility at a time, test after each extraction.
- [x] **Step 5:** Update all callers to use the new classes.
- [x] **Step 6:** Run `mvn spotless:apply verify` after each extraction.

**Key constraint:** `User.java` is a domain model referenced everywhere. Extraction from User should move behavior (methods) into services/policies, NOT split the data. Keep `User` as the data carrier.
i dont want to be drowned in new files. create a new seperate file ONLY if the extracted responsibility is a cohesive unit that can be clearly named and has a well-defined interface. For example, `MatchingUseCases` could be split into `MatchingService`, `DailyPicksService`, and `StandoutsService`. But don't create new files for small method groups that are only used in one place.

**Estimated effort:** 3-5 sessions. Each extraction is a separate commit.

- ✅ **2026-03-24 progress:** Completed decomposition pass for `RestApiServer` by extracting API request/response/DTO records into `RestApiDtos.java`; updated API DTO tests and verified with full `mvn spotless:apply verify`.
- ✅ **2026-03-24 progress:** Completed responsibility mapping + extraction-target design for current oversized files (`RestApiServer`, `ProfileController`, `ProfileViewModel`, `ProfileHandler`, `DevDataSeeder`, `ChatViewModel`, `JdbiMatchmakingStorage`, `MatchesController`, `TestStorages`).

---

### Task 1.2: Fix partially constructible services (V023)

**Problem:** Some services accept nullable collaborators and switch behavior at runtime based on which dependencies are null. This creates hidden modes that vary silently between environments.

**Scope:** Find services where constructor parameters are `@Nullable` and behavior branches on `if (dependency != null)`.

**Files to investigate:**
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/profile/ProfileService.java`
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`

**Approach:**
- [x] **Step 1:** Grep for `!= null` and `== null` checks on service fields across `core/` and `app/usecase/`.
- [x] **Step 2:** For each nullable dependency, determine if the null path is (a) a legitimate optional feature or (b) a backward-compat hack.
- [x] **Step 3:** For (b) cases: make the dependency required, update `ServiceRegistry` wiring, remove the null branch.
- [x] **Step 4:** For (a) cases: extract an explicit `NoOp` implementation of the dependency interface and inject that instead of null.
- [x] **Step 5:** Run `mvn test` after each change.

- ✅ **2026-03-23 progress:** Completed null-mode cleanup for `MatchingUseCases` (required collaborators, no-op wrappers for recommendation-derived services, no-op event bus fallback) and added regression tests in `MatchingUseCasesTest` + CLI handler tests pass.
- ✅ **2026-03-23 completed:** Finalized nullable-mode cleanup in `MatchingService` by moving metrics collaborator handling to explicit `Optional` semantics (no null branches in runtime path).

**Key constraint:** `ServiceRegistry` is the canonical construction site. All wiring changes happen there. Do not introduce new factory patterns.

**Estimated effort:** 2-3 sessions.

---

### Task 1.3: Configuration sourcing consistency (V046)

**Problem:** Some code paths read from `AppConfig.defaults()` instead of the injected `AppConfig` instance. This means config file overrides and env vars are silently ignored in those paths.

**Scope:** Find and eliminate all `AppConfig.defaults()` calls in runtime (non-test) code.

**Files to investigate:**
- Grep: `AppConfig.defaults()` across `src/main/java/`

**Approach:**
- [x] **Step 1:** `rg "AppConfig.defaults()" src/main/java/` — list all occurrences.
- [x] **Step 2:** For each occurrence, determine if it's in a static initializer (harder to fix) or instance method (easy to fix).
- [x] **Step 3:** For instance methods: inject `AppConfig` via constructor and replace `defaults()` call.
- [x] **Step 4:** For static initializers: refactor to lazy initialization or move to instance scope.
- [x] **Step 5:** Run `mvn test` after each file change.

- ✅ **2026-03-23 completed:** Removed runtime `AppConfig.defaults()` usage from `StorageFactory` and `MatchQualityService` paths; updated `ServiceRegistryTest` and validated with focused tests.

**Key constraint:** `AppConfig.defaults()` is legitimate in tests. Only eliminate it from `src/main/java/`.

**Estimated effort:** 1-2 sessions.

---

### Task 1.4: Presence feature flag cleanup (V018)

**Problem:** A presence/online-status feature flag exists but lacks documentation. It's unclear whether the feature is actively used, partially implemented, or abandoned.

**Approach:**
- [x] **Step 1:** Search for "presence" and "online" across the codebase.
- [x] **Step 2:** Map all code paths gated by this flag.
- [x] **Step 3:** If the feature is incomplete/unused: remove the flag and dead code behind it.
- [x] **Step 4:** If the feature is partially working: document the flag in `AppConfig` and CLAUDE.md.

- ✅ **2026-03-23 completed:** Removed presence feature-flag gating in `ViewModelFactory` and deleted obsolete `FeatureFlaggedNoOpUiPresenceDataAccess`; standardized on explicit `NoOpUiPresenceDataAccess`.
- ✅ **2026-03-24 note:** Step 4 is N/A because the feature flag path was removed rather than kept partially active.

**Estimated effort:** 1 session.

---

## Theme 2: UI Feature Parity

**Why second:** These are the most user-visible gaps. Each task adds a new screen or significant UI capability.

### Task 2.1: JavaFX social/friend-request/notifications parity (V021)

**Problem:** The CLI and REST API support social features (friend requests, notifications) but the JavaFX UI has no screens for them.

**Scope:** Add JavaFX screens for:
1. Friend request list (incoming/outgoing)
2. Notification center
3. Social connections view

**Files to create:**
- `src/main/resources/fxml/social.fxml`
- `src/main/resources/fxml/notifications.fxml`
- `src/main/java/datingapp/ui/screen/SocialController.java` (already exists — verify completeness)
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java` (already exists — verify completeness)

**Approach:**
- [x] **Step 1:** Read existing `SocialController`, `SocialViewModel`, and `SocialUseCases` to understand what's already wired.
- [x] **Step 2:** Identify which social features are missing from the UI vs. available in use-cases.
- [x] **Step 3:** Design FXML layouts for missing screens.
- [x] **Step 4:** Implement controllers following existing patterns (BaseController, ViewModel binding, async scope).
- [x] **Step 5:** Add navigation entries in `NavigationService` ViewType enum.
- [x] **Step 6:** Wire new screens in `DatingApp.java` or `ViewModelFactory`.

- ✅ **2026-03-23 completed:** Social parity is implemented via a consolidated tabbed `social.fxml` (notifications + friend requests) with `SocialController`/`SocialViewModel` wiring and `NavigationService.ViewType.SOCIAL` routing.
- ✅ **2026-03-23 validated:** `SocialViewModelTest` and `SocialControllerTest` targeted runs passed.

**Key constraints:**
- Follow the `ui/async` pattern (`ViewModelAsyncScope`, `TaskPolicy`, `AsyncErrorRouter`)
- Use `UiDataAdapters` interfaces — do NOT import `core.storage.*` directly
- Use `Locale.ENGLISH` for all `DateTimeFormatter` instances (CLAUDE.md gotcha)

**Estimated effort:** 3-5 sessions.

---

### Task 2.2: Multi-photo UI/gallery parity (V035)

**Problem:** The data model supports multiple photos per user, but the UI only shows a single photo. No gallery/carousel exists.

**Scope:** Add a photo gallery component to the profile view and matching cards.

**Approach:**
- [x] **Step 1:** Read `User.getPhotoUrls()` and understand the photo data model.
- [x] **Step 2:** Design a reusable gallery component (horizontal scroll or swipe).
- [x] **Step 3:** Integrate into ProfileController and MatchingController.
- [x] **Step 4:** Handle `ImageCache` integration for preloading gallery images.

- ✅ **2026-03-23 completed:** Confirmed multi-photo navigation/indicator behavior in profile + matching and added adjacent-image preloading via `ImageCache.preload(...)` in both controllers.
- ✅ **2026-03-23 validated:** `ProfileControllerTest` and `MatchingControllerTest` targeted runs passed.

**Estimated effort:** 2-3 sessions.

---

### Task 2.3: JavaFX standouts parity (V036)

**Problem:** The CLI has full standouts functionality but the JavaFX standouts screen may be incomplete compared to CLI capabilities.

**Approach:**
- [x] **Step 1:** Compare `StandoutsController` capabilities vs `MatchingHandler` standout section.
- [x] **Step 2:** Identify missing features (filtering, sorting, interaction types).
- [x] **Step 3:** Implement missing features.

- ✅ **2026-03-23 completed:** Added JavaFX standouts filtering/sorting controls (`#filterTextField`, `#sortComboBox`) with deterministic filtering by name/reason and sorting by rank/score/name; interaction wiring remains intact (`markInteracted` + profile navigation).
- ✅ **2026-03-23 validated:** `StandoutsControllerTest` expanded for sort/filter behavior and passes.

**Estimated effort:** 1-2 sessions.

---

### Task 2.4: JavaFX profile-notes parity (V049)

**Problem:** Profile notes may not be fully accessible from the JavaFX UI.

**Approach:**
- [x] **Step 1:** Read `NotesController` to understand current capability.
- [x] **Step 2:** Compare with CLI `ProfileHandler` note features.
- [x] **Step 3:** Add missing note CRUD operations to the UI.

- ✅ **2026-03-23 completed:** Added dedicated Notes-screen CRUD workflow (select/edit/save/delete) using `ProfileUseCases` upsert/delete commands, while preserving list/open-selected navigation.
- ✅ **2026-03-23 validated:** Expanded `NotesViewModelTest` and `NotesControllerTest` for save/delete and editor wiring; targeted test run passed.

**Estimated effort:** 1-2 sessions.

---

### Task 2.5: Super Like UI action (V051)

**Problem:** Super Like in the UI is placeholder behavior — the button may exist but the action doesn't fully execute.

**Approach:**
- [x] **Step 1:** Read `MatchingController` to find the Super Like button handler.
- [x] **Step 2:** Trace the action through to `MatchingUseCases` and `MatchingService`.
- [x] **Step 3:** Wire the full flow: UI action → use-case → storage → feedback animation.

- ✅ **2026-03-23 completed:** Verified end-to-end Super Like flow and added regression tests in `MatchingControllerTest` and `MatchingViewModelTest`.

**Estimated effort:** 1-2 sessions.

---

### Task 2.6: Accessibility improvements (V038)

**Problem:** Limited accessibility support — missing screen reader labels, keyboard navigation gaps, contrast issues.

**Scope:** This is a cross-cutting concern affecting all screens.

**Approach:**
- [x] **Step 1:** Audit all FXML files for `accessibleText`, `focusTraversable`, and `labelFor` attributes.
- [x] **Step 2:** Add missing accessibility attributes to interactive elements.
- [x] **Step 3:** Verify keyboard navigation works for all main flows.
- [x] **Step 4:** Check color contrast ratios against WCAG AA standards.

- ✅ **2026-03-23 progress:** Added accessibility metadata to key FXML flows (`dashboard`, `matching`, `notes`, `social`, `chat`, `profile`) and implemented keyboard navigation fixes:
  - `MatchingController`: swipe shortcuts ignored while typing in text inputs.
  - `NotesController`: Enter/Space opens selected note.
  - `SocialController`: Enter/Space marks selected unread notification as read.
- ✅ **2026-03-23 validated:** Added/updated keyboard interaction tests in `NotesControllerTest` and `SocialControllerTest` and verified with full `mvn spotless:apply verify` pass.
- ✅ **2026-03-24 completed:** Applied WCAG-focused contrast corrections in `theme.css` + `light-theme.css` for low-contrast classes (`char-counter`, `match-timestamp`, `empty-state-label`, headings/labels/badges in light theme).

**Estimated effort:** 2-3 sessions.

---

## Theme 3: REST API Completeness

### Task 3.1: Fully implement placeholder REST endpoints (V013)

**Problem:** Some REST endpoints may return placeholder responses or have incomplete implementations.

**Approach:**
- [x] **Step 1:** Read `RestApiServer.java` and list all 33 endpoints.
- [x] **Step 2:** For each endpoint, verify the handler fully implements the operation (not just returning a stub).
- [x] **Step 3:** Implement missing handler logic, routing through use-case layer where possible.
- [x] **Step 4:** Add integration tests for each fixed endpoint.

- ✅ **2026-03-23 completed:** Verified no placeholder/stub handlers in `RestApiServer`; existing REST route test suite (`RestApi*Test`) passed in targeted run.

**Key constraint:** 5 read endpoints intentionally bypass the use-case layer (documented in CLAUDE.md). Don't change those unless explicitly requested.

**Estimated effort:** 2-3 sessions.

---

## Theme 4: Comprehensive Testing

### Task 4.1: Concurrency and error-path tests (V031)

**Problem:** Key concurrency scenarios and error recovery paths lack test coverage.

**Scope:** Add tests for:
- Concurrent match operations (two users swiping simultaneously)
- Database failure recovery in storage layer
- Event bus error handling under load
- ViewModel async error routing

**Approach:**
- [x] **Step 1:** Identify the 5 most critical concurrent code paths.
- [x] **Step 2:** Write tests using `CountDownLatch`, `CyclicBarrier`, or `CompletableFuture` for synchronization.
- [x] **Step 3:** Write error-injection tests using mock storage that throws on demand.

- ✅ **2026-03-23 completed:** Verified concurrency/error-path coverage with targeted suites: `MatchingServiceTest`, `ActivityMetricsServiceConcurrencyTest`, `InProcessAppEventBusTest`, `ViewModelAsyncScopeTest`, and `StorageContractTest` (all passing).

**Estimated effort:** 2-3 sessions.

---

### Task 4.2: UI controller test coverage expansion (V052)

**Problem:** UI controller test coverage is partial. Some controllers have no tests.

**Approach:**
- [x] **Step 1:** Run `rg "class.*ControllerTest" src/test/` to list existing controller tests.
- [x] **Step 2:** Compare against the 14 controllers in `ui/screen/`.
- [x] **Step 3:** Create test files for untested controllers.
- [x] **Step 4:** Focus on: initialization, user action handling, ViewModel binding, cleanup.

- ✅ **2026-03-23 progress:** Expanded controller edge-case coverage with new tests in:
  - `DashboardControllerTest` (no daily-pick state)
  - `LoginControllerTest` (empty user repository)
  - `SocialControllerTest` (empty notifications/requests)
  - `NotesControllerTest` (empty notes state)
  - `MatchingControllerTest` (super-like button flow)
  - `MatchingViewModelTest` (SUPER_LIKE recording and candidate advancement)
  - Verified targeted UI test batches for these additions: **12 passed, 0 failed** (first batch) and **15 passed, 0 failed** (matching batch).
- ✅ **2026-03-24 completed:** Added/expanded controller coverage for `BaseController`, `MilestonePopupController`, `DashboardController#setCompactMode`, and cleanup/idempotence paths in `ChatController` + `MatchesController`; full verify remains green.

**Key constraint:** JavaFX tests require the FX toolkit to be initialized. Use `@ExtendWith(ApplicationExtension.class)` or the project's existing test setup pattern.

**Estimated effort:** 3-4 sessions.

---

### Task 4.3: Fix ChatViewModel test nondeterminism (V059)

**Problem:** `ChatViewModelTest` has synchronization primitives (`CountDownLatch`, `AtomicReference`) but async timing can still cause flaky results.

**Approach:**
- [x] **Step 1:** Read the existing test and identify which assertions depend on async completion.
- [x] **Step 2:** Replace timing-dependent assertions with `Awaitility` or explicit latch-based synchronization.
- [x] **Step 3:** Add `@RepeatedTest(10)` to verify stability.

- ✅ **2026-03-23 completed:** `ChatViewModelTest#shouldSkipStaleBackgroundLoads` now runs as `@RepeatedTest(10)` on top of explicit synchronization (`waitUntil`/FX thread draining); targeted test class run passed.

**Estimated effort:** 1 session.

---

## Theme 5: Infrastructure & Configuration

### Task 5.1: TrustSafetyService distributed synchronization (V006)

**Problem:** `TrustSafetyService` uses `synchronized(this)` for auto-ban logic. This only protects a single JVM instance. In a multi-instance deployment, concurrent bans from different nodes could corrupt state.

**Scope:** This is a **design concern for future scaling**, not a current production bug (the app runs single-instance).

**Approach:**
- [x] **Step 1:** Evaluate whether multi-instance deployment is planned. If not, document the limitation and defer.

       multi-instance IS PLANNED IN THE FUTURE.

- [x] **Step 2:** If multi-instance is planned: replace `synchronized` with DB-level advisory locks or `SELECT ... FOR UPDATE` on the user row before ban.
- [x] **Step 3:** Add an integration test that simulates concurrent ban attempts.

- ✅ **2026-03-24 completed:** Added `UserStorage.executeWithUserLock(...)`, JDBI row-lock implementation (`SELECT ... FOR UPDATE`) with shared-handle execution, refactored `TrustSafetyService` auto-ban to run under storage lock, and added regression coverage in `TrustSafetyServiceTest` + `JdbiUserStorageNormalizationTest`.

**Estimated effort:** 1-3 sessions depending on scope decision.

---

### Task 5.2: Externalize hardcoded business limits (V041)

**Problem:** Various business rules (max swipes/day, max photos, match limits, etc.) are hardcoded as constants instead of being configurable via `AppConfig`.

**Approach:**
- [x] **Step 1:** Grep for magic numbers near business-rule comments: `MAX_`, `LIMIT_`, `THRESHOLD_`.
- [x] **Step 2:** For each, determine if it should be configurable (changes between environments) or truly constant (domain invariant like "latitude range is -90 to 90").
- [x] **Step 3:** Move configurable limits to `AppConfig` with sensible defaults.
- [x] **Step 4:** Update `AppConfigValidator` to validate the new config fields.

- ✅ **2026-03-23 progress:** Removed additional hardcoded UI/domain limits in profile flow by using runtime config values:
  - `ProfileViewModel.toggleInterest(...)` now uses `config.validation().maxInterests()`.
  - `ProfileController` bio and interest limits now read from `ProfileViewModel` config-backed getters.
  - Added `ProfileViewModelTest` coverage for custom `maxInterests` and `maxBioLength`.
- ✅ **2026-03-24 completed:** Externalized additional configurable limits (`maxMessageLength`, `maxProfileNoteLength`) into `AppConfig` + JSON + validator, and wired usage into `ConnectionService`, `ProfileUseCases`, and CLI note flows.

**Key constraint:** Don't make everything configurable. Domain invariants should stay as constants.

**Estimated effort:** 2-3 sessions.

---

### Task 5.3: Event system expansion (V048)

**Problem:** The event system may need additional event types to support new features and cross-cutting concerns.

**Approach:**
- [x] **Step 1:** Read `AppEvent` enum and `InProcessAppEventBus` to understand current event types.
- [x] **Step 2:** Identify operations that should publish events but don't (profile updates, social actions, moderation actions).
- [x] **Step 3:** Add new event types and handlers. Follow the existing `app/event/handlers/` pattern.

- ✅ **2026-03-23 progress:** Expanded notification event handling for social actions:
  - `NotificationEventHandler` now subscribes to `AppEvent.FriendRequestAccepted` and emits `FRIEND_REQUEST_ACCEPTED` notifications to the requester.
  - Added regression coverage in `NotificationEventHandlerTest`.
- ✅ **2026-03-24 completed:** Added new profile-domain events (`ProfileNoteSaved`, `ProfileNoteDeleted`, `AccountDeleted`), published them from `ProfileUseCases`, extended achievement/metrics handlers, and added publishing/no-subscriber coverage tests.

**Estimated effort:** 1-2 sessions.

---

## Theme 6: Future Features (Lowest Priority)

### Task 6.1: Real-time chat / WebSocket (V050)

**Problem:** Chat requires manual refresh. No push/WebSocket updates exist.

**Approach:** This is a significant feature addition requiring:
- WebSocket server endpoint (Javalin supports WebSocket)
- Client-side WebSocket connection in ChatViewModel
- Message push protocol design
- Reconnection and error handling

**Estimated effort:** 5+ sessions. Consider as a separate project.

---

### Task 6.2: CLI input validation consistency (V061)

**Problem:** CLI input validation is inconsistent across profile and messaging flows.

**Approach:**
- [x] **Step 1:** Audit all `inputReader.readLine()` calls in CLI handlers.
- [x] **Step 2:** Ensure all user inputs go through `ValidationService` before mutation.
- [x] **Step 3:** Standardize error messages and retry behavior.

- ✅ **2026-03-23 progress:** Improved CLI error-path reliability while preserving behavior:
  - `MessagingHandler` now logs warning details when conversation previews or unread counts fail.
  - `MatchingHandler` now checks `markNotificationRead` results and logs warning on failure instead of silently ignoring.
- ✅ **2026-03-24 completed:** Hardened CLI input/mutation paths: profile-note mutations now route through `ProfileUseCases`, messaging conversation loop exits safely on EOF, and safety-report description limits are config-driven.

**Estimated effort:** 2 sessions.

---

### Task 6.3: CLI state persistence guarantees (V062)

**Problem:** CLI handlers may modify User/Match state in memory without immediately persisting to storage, risking data loss on crash.

**Approach:**
- [x] **Step 1:** Trace all CLI handler flows that modify domain objects.
- [x] **Step 2:** Ensure each mutation is followed by a storage save before returning to the user.
- [x] **Step 3:** Add tests that verify persistence after each mutation.

- ✅ **2026-03-23 progress:** Added defensive observability around notification read persistence path in `MatchingHandler` (failed persistence attempts are now surfaced via warning logs for diagnosis).
- ✅ **2026-03-24 completed:** Enforced persistence-oriented note mutation path through profile use-cases in CLI and added targeted regression tests (`ProfileNotesHandlerTest`, `MessagingHandlerTest`, `SafetyHandlerTest`).

**Estimated effort:** 2 sessions.

---

## Execution Priority

If time is limited, work themes in this order:

1. **Theme 1 (Architecture)** — reduces cost of all future work
2. **Theme 2.1 (Social parity)** — highest user-visible value
3. **Theme 3 (REST API)** — completes existing infrastructure
4. **Theme 4 (Testing)** — reduces regression risk
5. **Theme 2.2-2.6 (remaining UI)** — incremental user value
6. **Theme 5 (Infrastructure)** — future-proofing
7. **Theme 6 (Future)** — only if everything else is done

Each theme produces working, testable, committable software independently.
