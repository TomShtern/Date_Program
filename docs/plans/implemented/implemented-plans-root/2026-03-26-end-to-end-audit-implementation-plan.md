# Comprehensive Audit Closure Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:subagent-driven-development` (if subagents available) or `superpowers:executing-plans` to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fully implement and close end-to-end all verified gaps and follow-up items in `COMPREHENSIVE_AUDIT_REPORT.md` (2026-03-26), with code + tests + evidence.

**Architecture:** Use additive, deterministic changes only. Keep `SchemaInitializer` frozen; perform schema evolution through new versioned migrations in `MigrationRunner`. Close lifecycle and policy gaps via focused updates in ViewModel/use-case/event layers, and lock behavior with targeted tests plus architecture guardrails.

**Tech Stack:** Java 25 (preview), JavaFX 25, Maven, JUnit 5, H2, in-process event bus.

---

## Scope map (everything mentioned in the report)

1. **Schema integrity**
   - Add missing FKs for:
     - `daily_pick_views.user_id -> users(id)`
     - `user_achievements.user_id -> users(id)`
2. **Event model and handler coverage**
   - `AppEvent` defines 16 event types; current handler subscriptions are partial.
   - Implement explicit coverage policy so no event type is silently unowned.
3. **Lifecycle and cleanup behavior**
   - Re-evaluate and implement `ChatViewModel.dispose()` profile-note cleanup semantics.
4. **Messaging use-case behavior**
   - Keep `loadConversation(markAsRead=true)` best-effort semantics and harden regression checks.
5. **Feature existence checks**
   - Preserve and protect verified existing capabilities with regression checks.
6. **Test coverage facts**
   - Add focused `InterestMatcher` tests.
   - Resolve disabled tests in `TimePolicyArchitectureTest` (justify with durable rationale or re-enable by fixing violations).

---

## Delivery strategy

- **Phase A (safety rails first):** add/expand tests before behavior changes where practical.
- **Phase B (implementation):** minimal, contract-driven code edits.
- **Phase C (verification):** targeted + full quality gate verification.
- **Phase D (documentation/evidence):** update closure notes with exact pass/fail evidence.

---

## Task 1: Schema integrity closure via migration (FK backfill)

**Files:**
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- Optional new test helper methods inside existing test class

- [x] ✅ **Step 1: Add migration V10 to `MIGRATIONS` (append-only).**
  - Description: add missing foreign keys for `daily_pick_views.user_id` and `user_achievements.user_id`.
  - Keep prior versions untouched.

- [x] ✅ **Step 2: Implement `applyV10(Statement)` with idempotent FK creation.**
  - Add robust existence checks before `ALTER TABLE ... ADD CONSTRAINT`.
  - Prefer deterministic constraint names:
    - `fk_daily_pick_views_user`
    - `fk_user_achievements_user`

- [x] ✅ **Step 3: Add metadata helpers if needed (e.g., `hasForeignKey(...)`).**
  - Must be safe on H2 and existing DB states.

- [x] ✅ **Step 4: Add migration tests for V10.**
  - Fresh DB path (`runAllPending`) results in FK presence.
  - Legacy DB upgrade path adds constraints.
  - Idempotency preserved on repeated runs.

- [x] ✅ **Step 5: Add behavior test for referential integrity.**
  - Deleting a user cascades/blocks per FK semantics (expected `ON DELETE CASCADE` contract).

**Acceptance criteria:**
- Schema version 10 recorded.
- Both FK constraints observable through metadata.
- Existing migrations remain stable and idempotent.

---

## Task 2: Event model coverage completion (16 events, explicit ownership)

**Files:**
- Modify: `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java`
- Modify: `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java`
- Modify: `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- Add/Modify: `src/test/java/datingapp/app/event/handlers/*Test.java`
- Add: `src/test/java/datingapp/architecture/EventCoverageArchitectureTest.java` (new)

- [x] ✅ **Step 1: Build event ownership matrix from `AppEvent` sealed records.**
  - For each of the 16 events, define: `handled-by`, `intentional-no-op`, or `future-work`.

- [x] ✅ **Step 2: Implement missing high-value subscriptions and handlers.**
  - Prioritize events with user-facing or metrics impact (`ProfileCompleted`, `ConversationArchived`, `UserBlocked`, `UserReported`, `MatchExpired`, etc.).

- [x] ✅ **Step 3: Add explicit no-op policy for events intentionally not handled.**
  - Must be coded and test-visible (not tribal knowledge).

- [x] ✅ **Step 4: Add architecture test ensuring every `AppEvent` type is either subscribed or explicitly exempted.**
  - Fail fast on newly-added unowned event types.

- [x] ✅ **Step 5: Extend handler tests to verify new subscriptions and behavior.**

**Acceptance criteria:**
- No silent event ownership gaps.
- Tests fail if a new event type is introduced without routing policy.

---

## Task 3: ChatViewModel dispose lifecycle hardening

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

- [x] ✅ **Step 1: Update `dispose()` to explicitly clear profile-note transition/state.**
  - Ensure pending dismiss transition is stopped.
  - Ensure note content/status/busy state are reset consistently.

- [x] ✅ **Step 2: Preserve existing cleanup ordering guarantees.**
  - No listener leaks.
  - No polling handles left running.

- [x] ✅ **Step 3: Add regression test(s) for disposal semantics.**
  - Profile-note status transition does not outlive disposed VM.
  - Post-dispose state remains stable.

**Acceptance criteria:**
- Deterministic disposal with no pending profile-note transition side effects.

---

## Task 4: Messaging mark-as-read best-effort regression lock

**Files:**
- Modify (if needed): `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- Optional: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

- [x] ✅ **Step 1: Keep `markConversationAsReadBestEffort` non-fatal semantics intact.**
  - No flow-control exceptions for mark-as-read failures.

- [x] ✅ **Step 2: Add/strengthen assertions around warning path behavior.**
  - Conversation load succeeds when mark-as-read fails.
  - Returned message thread remains valid.

- [x] ✅ **Step 3: Add UI-facing regression case (optional but recommended).**
  - Chat still loads and remains selected under mark-as-read failure.

**Acceptance criteria:**
- Behavior remains best-effort and user-visible flow is resilient.

---

## Task 5: Feature existence assertions upgraded to regression checks

**Files (likely):**
- Modify/Add tests under:
  - `src/test/java/datingapp/storage/` (for `StorageFactory.buildInMemory(AppConfig)` path)
  - `src/test/java/datingapp/ui/screen/` (profile preview/score dialog wiring smoke checks)
  - `src/test/java/datingapp/ui/viewmodel/ProfileViewModel*` (dealbreakers API read/write)

- [x] ✅ **Step 1: Add targeted tests that lock currently-verified feature existence.**
  - Storage in-memory path remains constructible.
  - Profile preview and score dialog wiring remain reachable.
  - Dealbreakers read/write APIs remain operational.

- [x] ✅ **Step 2: Keep tests focused (do not over-couple to rendering details).**

**Acceptance criteria:**
- Future regressions in these "exists today" features are caught automatically.

---

## Task 6: InterestMatcher-focused test suite

**Files:**
- Add: `src/test/java/datingapp/core/matching/InterestMatcherTest.java`
- Optional cleanup: trim duplicated nested `InterestMatcher` tests in `MatchQualityServiceTest` after parity is achieved

- [x] ✅ **Step 1: Create dedicated `InterestMatcherTest` with focused coverage.**
  - `compare(...)`: null/empty/identical/partial/asymmetric/no-overlap cases.
  - `formatSharedInterests(...)`: default/custom preview/validation behavior.
  - `formatAsList(...)`: sorted deterministic output.

- [x] ✅ **Step 2: Add edge-case tests not currently explicit.**
  - `previewCount < 1` throws.
  - `MatchResult` constructor validation constraints.

- [x] ✅ **Step 3: Ensure no behavioral drift in `MatchQualityService` integration.**

**Acceptance criteria:**
- `InterestMatcher` behavior is independently covered and deterministic.

---

## Task 7: Resolve disabled architecture tests (`TimePolicyArchitectureTest`)

**Files:**
- Modify: `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- Potentially modify violating files in `src/main/java/**` (timezone/config-default usages)

- [x] ✅ **Step 1: Run violation scan exactly matching architecture-test logic.**
  - `ZoneId.systemDefault()` outside allowlist
  - `AppConfig.defaults()` outside allowlist/comments

- [x] ✅ **Step 2: Decision gate (must close one path):**
  - **Path A (preferred):** remove violations, then re-enable tests.
  - **Path B (acceptable only with durable rationale):** keep disabled but rewrite `@Disabled` reasons with explicit tracking issue/owner/exit criteria, and add TODO deadline.

- [x] ✅ **Step 3: If Path A chosen, remediate violating files and enable both tests.** *(N/A — Path B chosen with durable rationale and explicit re-enable criteria/deadline.)*
  - Current known timezone violations include UI/CLI/core call sites; ensure replacements follow project time-policy conventions.

- [x] ✅ **Step 4: Add regression evidence in test output docs/log.**

**Acceptance criteria:**
- Disabled-test status is no longer ambiguous; either fully re-enabled and passing, or strongly justified with an enforceable re-enable plan.

---

## Task 8: Verification and quality gate

**Files:**
- No mandatory code file; run verification commands and capture output artifacts in root docs if needed.

- [x] ✅ **Step 1: Run focused tests for changed areas first.**
  - Schema migration tests
  - Event handler + architecture tests
  - ChatViewModel + MessagingUseCases tests
  - InterestMatcher tests

- [x] ✅ **Step 2: Run repository quality gate.**
  - `mvn spotless:apply verify`

- [x] ✅ **Step 3: If failures occur, fix root cause and rerun until green.**

**Acceptance criteria:**
- All changed tests pass.
- Full verify passes with formatting/lint/coverage checks.

---

## Execution order (recommended)

1. Task 1 (Schema FK migration + tests)
2. Task 6 (InterestMatcher dedicated tests)
3. Task 3 (ChatViewModel dispose cleanup)
4. Task 4 (Messaging best-effort regression lock)
5. Task 2 (Event coverage completion + architecture guardrail)
6. Task 7 (Disabled architecture tests closure)
7. Task 5 (Feature existence regression checks)
8. Task 8 (Final full verification)

---

## Risks and mitigations

- **Migration risk on legacy DBs:** FK add can fail if orphan rows exist.
  - Mitigation: preflight orphan detection + deterministic failure message (or controlled cleanup migration if product policy allows).
- **Event over-subscription risk:** adding handlers may duplicate side effects.
  - Mitigation: idempotent handler logic + explicit event ownership tests.
- **JavaFX flakiness risk in Chat tests:** asynchronous timing can be non-deterministic.
  - Mitigation: use existing `waitUntil` + FX-thread synchronization pattern already present in `ChatViewModelTest`.
- **Architecture-test re-enable blast radius:** multiple files currently use `ZoneId.systemDefault()`.
  - Mitigation: remediate in small commits, run target architecture tests repeatedly.

---

## Definition of done

- [x] ✅ Both missing FKs are added through versioned migration and validated by tests.
- [x] ✅ Event ownership is explicit for all 16 `AppEvent` types and enforced by tests.
- [x] ✅ `ChatViewModel.dispose()` fully clears/stops profile-note dismiss state.
- [x] ✅ Messaging mark-as-read best-effort behavior remains resilient and tested.
- [x] ✅ Feature existence checks are protected by regression tests.
- [x] ✅ Dedicated `InterestMatcherTest` added and passing.
- [x] ✅ `TimePolicyArchitectureTest` disabled-state issue is resolved (re-enabled or strongly justified with exit criteria).
- [x] ✅ Full Maven verify pipeline passes.

---

## Evidence to attach after implementation

- Test run summaries (focused + full verify)
- List of modified files
- Notes on decisions for event ownership and architecture-test path
- Any follow-up backlog items created during implementation
