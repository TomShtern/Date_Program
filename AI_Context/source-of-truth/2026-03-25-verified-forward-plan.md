# Multi-Device and Postgres Readiness Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the project forward using repo-verified priorities, starting from the reality that the current app is still prototype-stage and missing several core product foundations.

**Architecture:** Treat the current app as a local-first prototype monolith that already has meaningful structure, tests, pagination, and versioned schema migrations, but still lacks several real-user foundations. Prioritize replacing prototype identity/session behavior, fixing correctness gaps, stabilizing contracts, and cleaning up composition before deeper platform-transition work.

**Tech Stack:** Java 25, JavaFX 25, Maven, Javalin, JDBI, H2 today, Postgres planned.

---

## Verified Starting Point

- The current codebase is not close to production-ready. Several user-facing flows are still explicitly prototype or simulated implementations.
- The JavaFX login flow is currently a profile picker / account creator, not a real authentication flow.
- New users are still created with fake bootstrap defaults such as placeholder photos and a default Tel Aviv location.
- Incomplete users can be auto-completed at login time rather than being forced through a real onboarding flow.
- Session state is still an in-memory singleton intended for development and testing, not a durable multi-user session model.
- Verification fields exist on the user model, but verification delivery is still simulated and not backed by real email/SMS integration.
- The safety/privacy screen still presents key account actions with `[SIMULATED]` status text, which makes core account behavior feel unfinished even inside the current desktop app.
- Chat currently loads a fixed recent slice of conversation history (`100` messages) and does not expose an older-history paging flow in the JavaFX UI.
- Presence indicators default to unavailable, and private-note support is only partially wired in the current JavaFX chat path.
- Matching empty states currently focus mainly on missing location or candidate exhaustion, not on broader incomplete-profile blocking and recovery guidance.
- Profile media is still a desktop-local `file://` workflow under the user's home directory, and placeholder-avatar behavior is still entangled with profile completion.
- The project already has versioned schema migrations in `src/main/java/datingapp/storage/schema/MigrationRunner.java`, and they are invoked from `src/main/java/datingapp/storage/DatabaseManager.java`.
- The REST API is currently localhost-only and intentionally unauthenticated in `src/main/java/datingapp/app/api/RestApiServer.java`.
- The event bus is synchronous and in-process in `src/main/java/datingapp/app/event/InProcessAppEventBus.java`.
- Pagination already exists for matches, conversations, and messages in the current storage and service layers.
- Candidate search already uses DB prefiltering in `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`, with final distance checks in `src/main/java/datingapp/core/matching/CandidateFinder.java`.
- The most immediate repo-backed issues are architecture ownership drift, error-contract leakage, the chat-note wiring mismatch, deterministic time usage in stats, missing targeted infrastructure tests, and conversation preview query inefficiency.

## Chunk 0: Prototype-Stage Missing Foundations

### Task 0: Replace the prototype login and session model

**Files:**
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/core/AppSession.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`

- [ ] Stop treating login as "pick an existing profile from a list and enter the app."
- [ ] Replace the current in-memory simulated session model with an explicit session/auth boundary that can later support real remote clients.
- [ ] Separate account selection for local demo/dev use from any future real sign-in flow so prototype shortcuts are no longer the default user path.
- [ ] Make session ownership and lifecycle explicit instead of relying on a single global current-user singleton.
- [ ] Verify with the existing login-controller and login-viewmodel tests after updating them to match the intended product flow.

### Task 0.1: Remove fake profile bootstrapping and forced auto-completion

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Test: `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`

- [ ] Remove the current behavior that auto-fills missing bios, placeholder photos, default pace preferences, and default location just to make profiles usable.
- [ ] Stop using hardcoded demo defaults such as the Tel Aviv location and placeholder avatar as part of the normal account-creation path.
- [ ] Introduce an explicit onboarding or profile-completion flow where incomplete users remain incomplete until the required fields are truly supplied.
- [ ] Ensure activation rules are enforced by real user input rather than hidden convenience behavior.
- [ ] Verify with login and profile-use-case tests that incomplete users remain blocked until they complete the intended data.

### Task 0.2: Build real account verification and identity foundations

**Files:**
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Create: `src/main/java/datingapp/app/account/VerificationService.java`
- Create: `src/main/java/datingapp/app/account/VerificationCodeSender.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`

- [ ] Treat the current email/phone verification fields as unfinished prototype support, not as a completed feature.
- [ ] Add a real verification flow with code generation, expiry, attempt limits, and a sender abstraction for email/SMS delivery.
- [ ] Define the minimal identity model for future remote clients: account identifier, verification state, and how login establishes trust.
- [ ] Keep transport and delivery abstract so the feature can be tested without requiring live providers during development.
- [ ] Verify with use-case tests for the verification lifecycle before connecting any real delivery provider.

### Task 0.3: Make incomplete-profile state visible and actionable across the app

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`

- [ ] Stop relying on hidden profile auto-fill to make the rest of the app usable; show users exactly why their profile is blocked instead.
- [ ] Reuse the existing completion and nudge logic to drive users from login, dashboard, and matching into the profile editor when required fields are missing.
- [ ] Make matching empty states distinguish between "no candidates," "location missing," and "profile not ready to browse."
- [ ] Ensure profile completion rules are explained in the UI with concrete next steps instead of silent log warnings or vague empty screens.
- [ ] Verify with dashboard and matching-viewmodel tests that blocked/incomplete users get clear app-level guidance.

### Task 0.4: Fix basic chat usability before deeper messaging work

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ChatController.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- Test: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`

- [ ] Replace the current fixed `100`-message thread load with a basic older-history paging path in the chat UI.
- [ ] Keep conversation refresh lightweight while making long conversations actually navigable.
- [ ] Decide which chat extras are real in the current app and which should stay hidden until fully wired; do not present half-configured presence/typing affordances as if they are complete.
- [ ] Preserve the existing message-send path, but make chat history and message-loading behavior understandable and predictable for normal use.
- [ ] Verify with focused chat and messaging-use-case tests before any broader chat refactor.

### Task 0.5: Remove misleading prototype UX from safety, photo, and location flows

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`

- [ ] Remove or clearly isolate misleading `[SIMULATED]` user-facing messaging in the safety/account flow so the app honestly reflects what it actually does today.
- [ ] Treat "no photo yet" as a first-class state instead of sneaking placeholder-avatar data into account creation and profile completion.
- [ ] Keep the current desktop-local photo workflow for now, but make it explicit that it is a local gallery flow rather than a finished cross-device media model.
- [ ] Remove hardcoded location assumptions and generic UI copy that still point users toward Tel Aviv or Israeli ZIP-only thinking as part of the normal app path.
- [ ] Verify with safety and profile-viewmodel tests that empty-photo, location, and verification states behave consistently.

## Chunk 1: Correctness and Contract Hardening

### Task 1: Sanitize API and use-case error contracts

**Files:**
- Create: `src/main/java/datingapp/app/api/ApiErrorMapper.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Test: `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`

- [ ] Add a dedicated API error-mapping helper so `RestApiServer` stops returning raw internal exception text for internal/dependency failures.
- [ ] Replace `UseCaseError.internal(... e.getMessage())` patterns with stable safe messages plus server-side logging of the original exception.
- [ ] Keep validation, not-found, and conflict responses user-meaningful, but make internal failures generic and predictable.
- [ ] Add route-level tests that lock the new response contract before broad refactoring.
- [ ] Verify with `mvn -Ptest-output-verbose -Dtest="RestApiRoutesTest,RestApiPhaseTwoRoutesTest" test`.

### Task 2: Fix chat profile-note correctness and stats determinism

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java`

- [ ] Wire `ChatViewModel` to real profile-note support by default, or explicitly disable the UI actions when note support is unavailable.
- [ ] Make delete-note UX reflect the actual boolean result instead of always claiming success.
- [ ] Replace `ZoneId.systemDefault()` and `LocalDate.now()` usage in `StatsViewModel` with the app-configured time path used elsewhere in the project.
- [ ] Clean up the small `StatsViewModel` warning-level issues only after the correctness behavior is covered by tests.
- [ ] Verify with `mvn -Ptest-output-verbose -Dtest="ChatViewModelTest,StatsViewModelTest" test`.

## Chunk 2: Architecture Cleanup Without Rewrite

### Task 3: Move composition ownership toward the app/bootstrap layer

**Files:**
- Create: `src/main/java/datingapp/app/bootstrap/ServiceAssembly.java`
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java`

- [ ] Introduce a single assembly/composition class under `app/bootstrap` that constructs use cases and wires event handlers.
- [ ] Reduce `core/ServiceRegistry` to a registry/service bag instead of a place that imports and constructs app-layer use cases directly.
- [ ] Reduce `storage/StorageFactory` to storage and service creation; move app event-handler registration out of storage.
- [ ] Extend architecture tests to forbid `core -> app.usecase` imports and constrain `storage -> app` wiring leakage.
- [ ] Verify with `mvn -Ptest-output-verbose -Dtest="AdapterBoundaryArchitectureTest" test`.

### Task 4: Fix the conversation preview performance hotspot

**Files:**
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Modify: `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- Test: `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`

- [ ] Remove the current per-conversation preview N+1 pattern by batching last-message and unread-count retrieval.
- [ ] Keep the external `ConversationPreview` shape stable while improving the query plan underneath.
- [ ] Reuse existing pagination rather than redesigning the API contract here.
- [ ] Add focused tests that lock conversation preview counts and ordering while the query path changes.
- [ ] Verify with `mvn -Ptest-output-verbose -Dtest="RestApiConversationBatchCountTest,ConnectionServiceTransitionTest" test`.

## Chunk 3: Multi-Device and Remote-Client Readiness

### Task 5: Add remote-safe API foundations before adding async workers

**Files:**
- Create: `src/main/java/datingapp/app/api/ApiAuthService.java`
- Create: `src/main/java/datingapp/app/api/AuthenticatedUser.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Modify: `config/app-config.json`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`

- [ ] Add an explicit API mode in config: local-dev mode can keep current localhost assumptions, but remote mode must require real authentication.
- [ ] Replace header/query-string acting-user trust with an authenticated-user resolution path suitable for future multi-device clients.
- [ ] Keep localhost-only developer ergonomics for now, but make accidental public deployment harder.
- [ ] Add route tests for auth-required behavior and user-scope enforcement.
- [ ] Verify with `mvn -Ptest-output-verbose -Dtest="RestApiReadRoutesTest,RestApiRelationshipRoutesTest" test`.

### Task 6: Introduce async/outbox only when there is a durable cross-process need

**Files:**
- Create later if needed: `src/main/java/datingapp/app/event/outbox/OutboxEvent.java`
- Create later if needed: `src/main/java/datingapp/app/event/outbox/OutboxPublisher.java`
- Create later if needed: `src/main/java/datingapp/app/event/outbox/OutboxWorker.java`
- Modify later if needed: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify later if needed: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

- [ ] Do not convert current in-process domain events to async just for the sake of being async.
- [ ] Keep synchronous in-process events for local domain consistency until there is a real worker/external side effect such as push notifications, email, webhooks, analytics export, or cross-service processing.
- [ ] When such a requirement exists, add an outbox table plus a worker in one coherent change set instead of half-adopting the pattern.
- [ ] Gate this work behind an explicit product milestone: remote clients plus at least one must-not-lose background side effect.
- [ ] Verify later with targeted integration tests once an outbox-backed workflow exists.

## Chunk 4: Postgres Transition Strategy

### Task 7: Treat Flyway as a transition decision, not a correction to a broken current state

**Files:**
- Modify when migration begins: `pom.xml`
- Modify when migration begins: `src/main/java/datingapp/storage/DatabaseManager.java`
- Create when migration begins: `src/main/resources/db/migration/`
- Retire only after parity is proven: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Retire only after parity is proven: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- Test: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`

- [ ] Keep the current Java migration runner as the valid source of truth until the Postgres migration project is active.
- [ ] At the start of the Postgres transition, choose one migration authority: either continue with the current runner or replace it with Flyway. Do not run two competing migration systems long-term.
- [ ] If choosing Flyway, translate existing schema versions into ordered migrations, switch bootstrap in one step, and prove fresh-install plus upgrade parity before retiring current migration code.
- [ ] Add migration tests for both fresh schema creation and upgrade from representative existing states.
- [ ] Verify with targeted migration tests first, then full `mvn test`.

### Task 8: Optimize candidate search for Postgres only after measuring current limits

**Files:**
- Modify later if needed: `src/main/java/datingapp/core/matching/CandidateFinder.java`
- Modify later if needed: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify later if needed: Postgres-specific SQL and indexes
- Test later if needed: storage and matching tests covering query correctness and ordering

- [ ] Keep the current DB-prefilter plus JVM-final-filter approach while H2/local remains the default runtime.
- [ ] When Postgres becomes the active target, benchmark real candidate-search workloads before choosing the next optimization step.
- [ ] Prefer staged evolution: better indexes and tighter SQL prefilters first, Postgres-native geospatial features second, PostGIS only if benchmarks justify the extra operational weight.
- [ ] Keep matching behavior and ranking tests stable across the storage change so optimization does not silently change product semantics.
- [ ] Verify later with benchmark evidence plus matching regression tests.

## Chunk 5: Operational and Compatibility Readiness

### Task 9: Add observability and request-tracing foundations

**Files:**
- Create: `src/main/java/datingapp/app/api/RequestContext.java`
- Create: `src/main/java/datingapp/app/api/RequestTracing.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Test: `src/test/java/datingapp/app/api/RestApiRoutesTest.java`

- [ ] Add request IDs or correlation IDs to API request handling so logs can be tied back to one failing request once remote clients exist.
- [ ] Include safe structured context in server logs for use-case failures, auth failures, and dependency errors without leaking sensitive data to clients.
- [ ] Make internal error responses compatible with future tracing by including a stable error code and optional request identifier.
- [ ] Keep this lightweight and local-friendly; do not introduce full distributed tracing infrastructure yet.
- [ ] Verify with focused route tests plus any affected API contract tests.

### Task 10: Define retry, idempotency, and cutover safety rules

**Files:**
- Create: `docs/remote-client-write-semantics.md`
- Create: `docs/h2-to-postgres-cutover-checklist.md`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Test: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Test: `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`

- [ ] Define which mutating operations must be safe to retry from remote clients, especially likes, passes, friend requests, note upserts, and message sends.
- [ ] Decide where the project will rely on natural idempotency from existing domain/storage constraints and where explicit idempotency keys are needed later.
- [ ] Write a cutover checklist for the future H2-to-Postgres migration covering backup, restore, rollback, data verification, and freeze windows.
- [ ] Add tests for any endpoint whose retry semantics are tightened during this phase.
- [ ] Verify with the focused REST and connection-service tests that cover duplicate or repeated write attempts.

## Suggested Execution Order

- [ ] Phase 0: Task 0, Task 0.1, Task 0.2
- [ ] Phase 0.5: Task 0.3, Task 0.4, Task 0.5
- [ ] Phase 1: Task 1, Task 2, Task 3, Task 4
- [ ] Phase 2: Task 5
- [ ] Phase 3: Task 6 only when durable async side effects are introduced
- [ ] Phase 4: Task 7 and Task 8 as part of the Postgres cutover project
- [ ] Phase 5: Task 9 and Task 10 before public or multi-device rollout

## Non-Goals Right Now

- [ ] Do not rewrite the whole architecture around microservices.
- [ ] Do not add Flyway immediately just because it is popular.
- [ ] Do not make the event system async without a durability requirement.
- [ ] Do not jump to PostGIS until Postgres is real and query measurements justify it.
- [ ] Do not confuse prototype helper behavior with finished product behavior.

## Final Recommendation

- [ ] Use the first audit as the base for current priorities.
- [ ] Keep the good future-looking ideas from the second document, but treat them as staged roadmap items rather than present-tense repo defects.
- [ ] For the next implementation cycle, first replace prototype identity/session behavior and fake profile bootstrapping, then focus on contract safety, correctness bugs, composition cleanup, and measured performance work.
- [ ] Immediately after identity/onboarding cleanup, address the app-facing holes users will feel first: incomplete-profile guidance, long-chat usability, misleading safety labels, and placeholder-driven photo/location behavior.

## Additional Suggestions That Fit This Project

- [ ] Add one short ADR for each major future decision: auth model, migration authority, event durability, and Postgres rollout. This will stop the project from re-debating the same architectural choices every few weeks.
- [ ] Add a small benchmark/profiling harness for candidate search and conversation preview loading before deeper storage work. Measured bottlenecks will give you a much better Postgres transition plan than intuition alone.
- [ ] Create a clear definition of the first real remote deployment target. Decide whether that target is "single backend + Postgres + token auth" before discussing workers, queues, or distributed eventing.
- [ ] Add a focused API contract test suite for error shapes, auth behavior, and pagination semantics. Those contracts will matter much more once mobile or web clients start depending on them.
- [ ] Keep the current local-first developer workflow intact while building remote readiness behind config flags. That will let you keep fast iteration without mixing dev-only shortcuts into the production path.
- [ ] Add a short "prototype behavior inventory" section to this plan or a sibling doc listing every current shortcut that is still user-visible: profile picker login, auto-complete, placeholder photo completion, simulated verification messaging, no-op presence, and fixed-size chat history loading. That will make it easier to kill them one by one instead of rediscovering them later.
- [ ] Decompose the largest orchestration files only after their behavior is locked by tests. The highest-value candidates remain `RestApiServer`, `ChatViewModel`, `ProfileViewModel`, and `ProfileController`, but they should be split under test protection rather than as a pure cleanup exercise.
- [ ] Decide on a minimal API versioning policy before external clients ship, even if v1 is only pathless plus a documented compatibility rule. This will matter once mobile/web clients lag behind server releases.
- [ ] Add a small set of end-to-end smoke checks for the shared user journeys that span storage, use cases, and transport: browse, like/match, message, note, and report/block. Those will give you more confidence than unit coverage alone during the remote-readiness phase.
