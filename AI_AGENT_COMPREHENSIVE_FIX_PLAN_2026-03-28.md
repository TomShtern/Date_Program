# Source-Code Audit Remediation Plan (F01–F70)

> **For agentic workers:** This plan is optimized for end-to-end execution by a parent AI agent coordinating parallel subagents. Keep work deterministic, test-first where practical, and enforce one-owner-per-hotspot-file per wave.

**Date:** 2026-03-28
**Primary input:** `SOURCE_CODE_ONLY_ISSUES_AUDIT_2026-03-28.md`
**Scope:** All 70 findings (`F01`–`F70`) in source code, test code, and resources.

---

## 1) Goal and success definition

### Goal
Implement durable fixes for all audit findings (`F01`–`F70`) with strong regression protection, minimal rework, and controlled parallelization.

### Global done criteria
1. Every finding ID `F01`–`F70` has:
   - a merged code change,
   - at least one direct verification test (or architecture check),
   - traceability to this plan.
2. No newly disabled tests.
3. Critical correctness/security findings are fixed before medium/UX/perf-only changes.
4. Final repo gate passes: `mvn spotless:apply verify`.

### Live traceability status (2026-03-28)

| Findings                                               | Package | Status        | Verification evidence                                                                                                                                                                                                                                 |
|--------------------------------------------------------|---------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `F21`, `F37`, `F38`, `F40`                             | `P05`   | ✅ Re-verified | `ValidationServiceTest`, `ProfileCompletionServiceTest`                                                                                                                                                                                               |
| `F43`, `F44`, `F45`, `F47`, `F50`, `F51`               | `P11`   | ✅ Re-verified | `SqlRowReadersTest`, `RecordBindingTest`, `FriendRequestUniquenessTest`, `JdbiAccountCleanupStorageTest`                                                                                                                                              |
| `F58`, `F59`, `F62`                                    | `P12`   | ✅ Re-verified | `ProfileViewControllerTest`, `NotesControllerTest`, `StatsControllerTest`                                                                                                                                                                             |
| `F03`, `F04`, `F05`                                    | `P01`   | ✅ Implemented | `RestApiRoutesTest`, `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, `RestApiPhaseTwoRoutesTest`, `RestApiNotesRoutesTest`, `RestApiDailyLimitTest`, `RestApiRateLimitTest`, `RestApiConversationBatchCountTest`, `RestApiHealthRoutesTest` |
| `F06`, `F07`, `F08`                                    | `P02`   | ✅ Implemented | `ProfileUseCasesTest`, `SocialUseCasesTest`, `NotificationEventHandlerTest`, `JdbiCommunicationStorageSocialTest`, `StorageContractTest`                                                                                                              |
| `F09`, `F10`, `F12`, `F13`                             | `P03`   | ✅ Implemented | `ApplicationStartupBootstrapTest`, `CleanupSchedulerBehaviorTest`, `MainLifecycleTest`, `DatabaseManagerConfigurationTest`, `DatabaseManagerThreadSafetyTest`                                                                                         |
| `F11`, `F14`, `F15`, `F16`, `F17`, `F18`, `F19`, `F20` | `P04`   | ✅ Implemented | `MainLifecycleTest`, `MatchingHandlerTest`, `ProfileHandlerTest`, `MessagingHandlerTest`, `SafetyHandlerTest`, `NotificationEventHandlerTest`                                                                                                         |
| `F29`, `F39`                                           | `P05`   | ✅ Implemented | `ConnectionModelsTest`, `ProfileActivationPolicyTest`                                                                                                                                                                                                 |
| `F22`, `F23`, `F24`                                    | `P06`   | ✅ Implemented | `TrustSafetyServiceSecurityTest`, `TrustSafetyServiceTest`                                                                                                                                                                                            |
| `F25`, `F26`, `F35`, `F36`                             | `P07`   | ✅ Implemented | `ConnectionServiceAtomicityTest`, `MessagingServiceTest`, `JdbiConnectionStorageAtomicityTest`                                                                                                                                                        |
| `F30`, `F33`, `F34`                                    | `P08`   | ✅ Implemented | `MatchingServiceTest`, `EdgeCaseRegressionTest`, `MatchingTransactionTest`, `MatchingUseCasesTest`                                                                                                                                                    |
| `F31`, `F32`, `F60`                                    | `P09`   | ✅ Implemented | `StatsViewModelTest`, `JdbiUserStorageMigrationTest`                                                                                                                                                                                                  |
| `F41`, `F42`, `F46`, `F52`, `F53`, `F54`, `F55`, `F56` | `P10`   | ✅ Implemented | `JdbiMetricsStorageTest`, `JdbiCommunicationStorageSocialTest`, `SocialUseCasesTest`, `NotificationEventHandlerTest`, `StorageContractTest`                                                                                                           |
| `F48`, `F49`                                           | `P11`   | ✅ Implemented | `DatabaseManagerConfigurationTest`, `DatabaseManagerThreadSafetyTest`                                                                                                                                                                                 |
| `F57`, `F61`, `F63`                                    | `P12`   | ✅ Implemented | `UiDataAdaptersPresenceTest`, `ChatViewModelTest`, `StatsViewModelTest`, `MatchesViewModelTest`                                                                                                                                                       |
| `F64`, `F65`, `F66`, `F67`, `F68`, `F69`, `F70`        | `P13`   | ✅ Implemented | `DashboardViewModelTest`, `StatsViewModelTest`, `SocialControllerTest`, `TimePolicyArchitectureTest`, `AdapterBoundaryArchitectureTest`, `SafetyHandlerTest`, `MessagingHandlerTest`                                                                  |

---

## 2) Execution model for AI agents

### Parent agent responsibilities
- Own sequencing, hotspot locks, and integration decisions.
- Dispatch parallel subagents only for independent packages.
- Run integration gates after each wave.
- Maintain a live traceability matrix (`Fxx -> package -> tests -> status`).

### Subagent roles (recommended)
- **Context/read-only:** `codebase-context-gatherer`, `Explore`.
- **Deep design/risk calls:** `Reasoning Agent`.
- **Mechanical edits:** `fast-edit-agent` / `fast-executor`.
- **Test execution:** `command-runner` (or equivalent execution agent).
- **Model preference:** when configurable, prefer `gpt-5.4-mini` for fast, focused subagent tasks.

### Parallelization guardrails
- Never let two subagents edit the same hotspot file in the same wave.
- For storage/domain atomicity work, treat interface + implementation as one lock-set.
- Merge in small vertical slices with immediate targeted verification.

---

## 3) Hotspot lock matrix (single owner per wave)

These files are high-conflict and must be single-owned per wave:
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/profile/ValidationService.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

---

## 4) Dependency-aware remediation waves

### Wave 0 — Baseline + prerequisites

**Objective:** Establish safe schema/binding preconditions and baseline test signals before high-risk refactors.

**Packages:** `P11` (partial), `P13` (test scaffolding only)

**Includes first:**
- ✅ `F44`, `F45` (`@BindBean` record binding -> `@BindMethods`)
- ✅ `F50`, `F51` (friend request uniqueness + deterministic optional contract)

**Exit criteria:**
- DAO record binding regression tests are green.
- Schema migration path for friend-request uniqueness is green on upgrade and fresh DB.

### Wave 1 — Critical security/correctness and atomic mutation boundaries

**Objective:** Remove high-risk authorization and partial-write behavior.

**Packages:** `P01` (partial), `P06`, `P07`, `P08`, `P02` (partial), `P10` (partial)

**Key findings:**
- Security/identity: `F04`, `F05`, `F22`, `F23`, `F24`
- Atomicity/integrity: `F06`, `F26`, `F27`, `F28`, `F35`, `F36`
- Policy enforcement: `F30`, `F33`, `F34`
- Visibility/scope: `F41`, `F42`, `F56`

**Exit criteria:**
- No unauthorized mutation path bypassing acting-user identity.
- Message/relationship transitions are atomic (or fail-safe with no partial state).
- Block and daily-like policy cannot be bypassed by direct/internal paths.

### Wave 2 — Determinism, contract consistency, and resiliency

**Objective:** Resolve policy/time/validation/CLI/bootstrap drifts and deterministic query behavior.

**Packages:** `P01`, `P03`, `P04`, `P05`, `P09`, `P10` (remaining), `P11` (remaining)

**Key findings:**
- API mapping/use-case contracts: `F01`, `F02`, `F03`
- Bootstrap/CLI: `F09`–`F20` (except already done in Wave 1)
- Validation/contract alignment: `F21`, `F29`, `F37`, `F38`, `F39`, `F40`
- Time policy consistency: `F31`, `F32`, `F60`
- Query determinism and cleanup: `F46`, `F47`, `F48`, `F49`, `F52`, `F53`, `F54`, `F55`, `F43`

**Exit criteria:**
- Deterministic ordering and timezone policy are consistent and test-enforced.
- Validation rules are aligned across service/entity/completeness semantics.
- Bootstrap and CLI lifecycle paths behave correctly under EOF/invalid config/error conditions.

### Wave 3 — UI/resource correctness + test/architecture hardening

**Objective:** Finish UX correctness/perf and lock fixes with robust test guardrails.

**Packages:** `P12`, `P13` (full)

**Key findings:**
- UI/ViewModel/resource: `F57`–`F63`
- Test and architecture guardrails: `F64`–`F70`

**Exit criteria:**
- UI states distinguish load-error vs true-empty.
- Time-policy and adapter-boundary architecture tests are active and stable.
- Flaky/timing-racy tests replaced with deterministic synchronization.

---

## 5) Work package catalog (parallel-ready)

Each package below is a parallel unit when dependencies and lock constraints are satisfied.

### P01 — Messaging/API mapping integrity

**Findings:** `F01`, `F02`, `F03`, `F04`, `F05`
**Primary files:**
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`

**Implementation tasks:**
1. Decouple `openConversation` identity lookup from paged previews (`F01`).
2. Replace failure flattening with structured error mapping in messaging use-cases (`F02`).
3. Route route-failure handling through centralized mapping instead of hardcoded `409` (`F03`).
4. Require acting-user identity on mutating routes (`F04`).
5. Remove query-param fallback for acting identity (`F05`).

**Targeted verification:**
- `MessagingUseCasesTest`
- `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, `RestApiPhaseTwoRoutesTest`
- New/extended route-mapping tests for failure-to-status behavior.

**Status update (2026-03-29):** ✅ `F01`, `F02`, `F03`, `F04`, and `F05` implemented and verified with `MessagingUseCasesTest` plus the REST API route suite.

---

### P02 — Use-case mutation ordering and social atomicity

**Findings:** `F06`, `F07`, `F08`
**Primary files:**
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`

**Implementation tasks:**
1. Ensure delete-account persistence success before mutating in-memory user state (`F06`).
2. Replace mark-all-notifications read-loop with transactional storage operation (`F07`).
3. Implement concrete account-deleted side effects (or remove dead registration) (`F08`).

**Targeted verification:**
- `ProfileUseCasesTest`
- social/notification handler tests.

**Status update (2026-03-28):** ✅ `F06`, `F07`, and `F08` implemented and verified in `ProfileUseCasesTest`, `SocialUseCasesTest`, `NotificationEventHandlerTest`, `JdbiCommunicationStorageSocialTest`, and `StorageContractTest`.

---

### P03 — Bootstrap/config/lifecycle hardening

**Findings:** `F09`, `F10`, `F12`, `F13`
**Primary files:**
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/app/bootstrap/CleanupScheduler.java`
- `src/main/java/datingapp/Main.java`

**Implementation tasks:**
1. Replace cwd-relative config discovery with explicit resolution chain (`F09`).
2. Add retry/backoff + observable failure counters for cleanup scheduler (`F10`).
3. Guarantee shutdown path in `finally`/hook (`F12`).
4. Convert numeric env-var parse failures from fail-open to hard config error flow (`F13`).

**Targeted verification:**
- Startup/config tests, `CleanupSchedulerTest`, new `Main` lifecycle tests.

**Status update (2026-03-28):** ✅ `F09`, `F10`, `F12`, and `F13` implemented and verified in `ApplicationStartupBootstrapTest`, `CleanupSchedulerBehaviorTest`, and `MainLifecycleTest`.

---

### P04 — CLI interaction correctness and pagination UX

**Findings:** `F11`, `F14`, `F15`, `F16`, `F17`, `F18`, `F19`, `F20`
**Primary files:**
- `src/main/java/datingapp/app/cli/CliTextAndInput.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/app/cli/MatchingHandler.java`
- `src/main/java/datingapp/app/cli/MessagingHandler.java`
- `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`

**Implementation tasks:**
1. EOF-safe exit behavior in main menu and matching flows (`F11`, `F16`).
2. Use app-level zone policy for profile age presentation (`F14`).
3. Make prompt fallback messaging match actual state mutation (`F15`).
4. Differentiate conversation-load failure from true-empty list (`F17`).
5. Add conversation paging navigation beyond first 50 (`F18`).
6. Surface `markConversationRead` failures (`F19`).
7. Align relationship transition state names with notification switch cases (`F20`).

**Targeted verification:**
- `MessagingHandlerTest`, `MatchingHandlerTest`, `ProfileHandlerTest`, `SafetyHandlerTest`.

**Status update (2026-03-29):** ✅ `F11`, `F14`, `F15`, `F16`, `F17`, `F18`, `F19`, and `F20` implemented and verified in `MainLifecycleTest`, `MatchingHandlerTest`, `ProfileHandlerTest`, `MessagingHandlerTest`, `SafetyHandlerTest`, and `NotificationEventHandlerTest`.

---

### P05 — Validation and profile/activation contract alignment

**Findings:** `F21`, `F29`, `F37`, `F38`, `F39`, `F40`
**Primary files:**
- `src/main/java/datingapp/core/profile/ValidationService.java`
- `src/main/java/datingapp/core/connection/ConnectionModels.java`
- `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`

**Implementation tasks:**
1. ✅ Add `Double.isFinite` checks for lat/lon (`F21`).
2. Enforce `respondedAt != null` for non-`PENDING` friend-request states (`F29`).
3. ✅ Tighten phone normalization to exactly one optional leading `+` (`F37`).
4. ✅ Align `validateBio` semantics with completeness requirements (`F38`).
5. Unify `PAUSED` activation semantics between policy and entity transitions (`F39`).
6. ✅ Exclude placeholder sentinel avatar from completion scoring (`F40`).

**Targeted verification:**
- `ValidationServiceTest`
- friend-request and profile-completion tests.

**Status update (2026-03-28):** ✅ `F21`, `F29`, `F37`, `F38`, `F39`, and `F40` implemented and verified with focused coverage in `ValidationServiceTest`, `ProfileCompletionServiceTest`, `ConnectionModelsTest`, and `ProfileActivationPolicyTest`.

---

### P06 — Trust/safety security and race resistance

**Findings:** `F22`, `F23`, `F24`
**Primary files:**
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`

**Implementation tasks:**
1. Replace non-crypto RNG with `SecureRandom` (+ attempt throttling hook) (`F22`).
2. Replace pre-check duplicate-report logic with DB-unique-key authority and explicit duplicate handling (`F23`).
3. Make block flow cross-subsystem transition atomic (`F24`).

**Targeted verification:**
- `TrustSafetyServiceTest`
- storage integration tests for block/report persistence.

**Status update (2026-03-29):** ✅ `F22`, `F23`, and `F24` implemented and verified in `TrustSafetyServiceSecurityTest` and `TrustSafetyServiceTest`.

---

### P07 — Connection service atomicity and authorization gates

**Findings:** `F25`, `F26`, `F27`, `F28`, `F35`, `F36`
**Primary files:**
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java` (if interface changes)

**Implementation tasks:**
1. Replace N+1 preview assembly with batched storage query path (`F25`).
2. Make send-message write + conversation timestamp update atomic (`F26`).
3. Check atomic transition capability before mutating in-memory entities (`F27`, `F28`).
4. Gate conversation creation on `canMessage`/relationship eligibility (`F35`).
5. Make conversation delete (messages + conversation row) atomic (`F36`).

**Targeted verification:**
- `ConnectionServiceTransitionTest`
- storage contract/integration tests around messaging and deletion.

**Status update (2026-03-29):** ✅ `F25`, `F26`, `F35`, and `F36` implemented and verified in `ConnectionServiceAtomicityTest`, `MessagingServiceTest`, and `JdbiConnectionStorageAtomicityTest`. `F27` and `F28` were already covered before this session.

---

### P08 — Matching guardrails and concurrency-safe daily limits

**Findings:** `F30`, `F33`, `F34`
**Primary files:**
- `src/main/java/datingapp/core/matching/MatchingService.java`
- relevant storage interfaces/implementations for atomic like-limit enforcement

**Implementation tasks:**
1. Enforce daily-like limit at atomic write boundary (not check-then-write) (`F30`).
2. Prevent guardrail bypass via public direct-like method (`F33`).
3. Add explicit blocked-user guard at swipe entrypoint (`F34`).

**Targeted verification:**
- `MatchingServiceTest`
- daily-limit and concurrency tests.

**Status update (2026-03-28):** ✅ `F30`, `F33`, and `F34` implemented and verified in `MatchingServiceTest`, `EdgeCaseRegressionTest`, `MatchingTransactionTest`, and `MatchingUseCasesTest`.

---

### P09 — Unified time policy from core to UI

**Findings:** `F31`, `F32`, `F60`
**Primary files:**
- `src/main/java/datingapp/core/storage/UserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
- plus composition/wiring files for clock/zone injection

**Implementation tasks:**
1. Define one canonical age/streak timezone policy.
2. Apply policy in core candidate filtering (`F31`).
3. Apply same policy in JDBI date filtering logic (`F32`).
4. Inject clock/zone into streak calculations; remove host-default dependency (`F60`).

**Targeted verification:**
- `StatsViewModelTest`
- storage age-filter tests around birthday boundary cases.

**Status update (2026-03-29):** ✅ `F31`, `F32`, and `F60` implemented and verified in `StatsViewModelTest` and `JdbiUserStorageMigrationTest`.

---

### P10 — Storage visibility, scope safety, deterministic queries

**Findings:** `F41`, `F42`, `F46`, `F52`, `F53`, `F54`, `F55`, `F56`
**Primary files:**
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`

**Implementation tasks:**
1. Enforce conversation visibility/deletion predicates in message list/count queries (`F41`, `F42`).
2. Add deterministic ordering and uniqueness assumptions to active swipe-session reads (`F46`).
3. Prevent deletion of active sessions in expiration cleanup (`F52`).
4. Add deterministic tie-breakers to latest-stats and conversation/message ordering (`F53`, `F54`, `F55`).
5. Scope notification mutation by `(id, user_id)` (`F56`).

**Targeted verification:**
- `JdbiMetricsStorageTest`
- `JdbiCommunicationStorageSocialTest` and related storage contract tests.

**Status update (2026-03-29):** ✅ `F41`, `F42`, `F46`, `F52`, `F53`, `F54`, `F55`, and `F56` implemented and verified in `JdbiMetricsStorageTest`, `JdbiCommunicationStorageSocialTest`, `SocialUseCasesTest`, `NotificationEventHandlerTest`, and `StorageContractTest`.

---

### P11 — Schema/JDBI/DB safety infrastructure

**Findings:** `F43`, `F44`, `F45`, `F47`, `F48`, `F49`, `F50`, `F51`
**Primary files:**
- `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`

**Implementation tasks:**
1. ✅ Invalid enum decode should throw hard SQL errors with context (`F43`).
2. ✅ Replace record `@BindBean` with `@BindMethods` in affected DAOs (`F44`, `F45`).
3. ✅ Extend account cleanup to include `daily_picks` rows (`F47`).
4. Replace URL substring test-DB heuristic with explicit profile/env controls (`F48`).
5. Remove weak default local-file DB password fallback (`F49`).
6. ✅ Add/enforce pending friend-request uniqueness invariant (`F50`).
7. ✅ Ensure pending-request query contract is deterministic one-row (`F51`).

**Targeted verification:**
- `SchemaInitializerTest`
- `SqlRowReadersTest`
- DAO binding tests and account-cleanup tests.

**Status update (2026-03-28):** ✅ `F43`, `F44`, `F45`, `F47`, `F48`, `F49`, `F50`, and `F51` implemented and verified with focused regression coverage in `SqlRowReadersTest`, `RecordBindingTest`, `FriendRequestUniquenessTest`, `JdbiAccountCleanupStorageTest`, `DatabaseManagerConfigurationTest`, and `DatabaseManagerThreadSafetyTest`.

---

### P12 — UI/ViewModel/resource correctness and performance

**Findings:** `F57`, `F58`, `F59`, `F61`, `F62`, `F63`
**Primary files:**
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/screen/ProfileViewController.java`
- `src/main/java/datingapp/ui/screen/NotesController.java`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/resources/fxml/stats.fxml`

**Implementation tasks:**
1. Replace no-op chat presence data access wiring with real adapter (`F57`).
2. ✅ Use consistent fallback avatar instead of clearing image (`F58`).
3. ✅ Truncate notes by code points/graphemes (not UTF-16 units) (`F59`).
4. Replace full paging scan message-count refresh with aggregate path (`F61`).
5. ✅ Remove or unify hidden static achievements fallback block (`F62`).
6. Distinguish failure states from true-empty data states in ViewModels (`F63`).

**Targeted verification:**
- `ProfileViewControllerTest`, `NotesControllerTest`, `StatsViewModelTest`, `MatchesViewModel` tests.

**Status update (2026-03-29):** ✅ `F57`, `F58`, `F59`, `F61`, `F62`, and `F63` implemented and verified with focused regression coverage in `UiDataAdaptersPresenceTest`, `ChatViewModelTest`, `StatsViewModelTest`, `MatchesViewModelTest`, `ProfileViewControllerTest`, `NotesControllerTest`, and `StatsControllerTest`.

---

### P13 — Test quality and architecture guardrail hardening

**Findings:** `F64`, `F65`, `F66`, `F67`, `F68`, `F69`, `F70`
**Primary files:**
- `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
- `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java`
- `src/test/java/datingapp/ui/screen/SocialControllerTest.java`
- `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java`
- `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- `src/test/java/datingapp/app/cli/MessagingHandlerTest.java`

**Implementation tasks:**
1. Replace timing-sensitive thread/poll patterns with deterministic synchronization (`F64`).
2. Add strong negative/failure-path stats tests (`F65`).
3. Replace weak non-empty list oracles with concrete field/order assertions (`F66`).
4. Re-enable time-policy architecture guard after migration cleanup (`F67`).
5. Replace text-line import scanning with AST/semantic boundary assertions (`F68`).
6. Remove random-code smoke-only safety tests via deterministic verifier injection (`F69`).
7. Restore real message-save path coverage in messaging CLI tests (`F70`).

**Targeted verification:**
- architecture tests, deterministic JavaFX tests, CLI integration tests with real persistence path.

**Status update (2026-03-29):** ✅ `F64`, `F65`, `F66`, `F67`, `F68`, `F69`, and `F70` implemented and verified in `DashboardViewModelTest`, `StatsViewModelTest`, `SocialControllerTest`, `TimePolicyArchitectureTest`, `AdapterBoundaryArchitectureTest`, `SafetyHandlerTest`, and `MessagingHandlerTest`.

---

## 6) Full finding-to-package traceability map

### A) API/CLI/bootstrap/event findings
- `F01` -> `P01`
- `F02` -> `P01`
- `F03` -> `P01`
- `F04` -> `P01`
- `F05` -> `P01`
- `F06` -> `P02`
- `F07` -> `P02`
- `F08` -> `P02`
- `F09` -> `P03`
- `F10` -> `P03`
- `F11` -> `P04`
- `F12` -> `P03`
- `F13` -> `P03`
- `F14` -> `P04`
- `F15` -> `P04`
- `F16` -> `P04`
- `F17` -> `P04`
- `F18` -> `P04`
- `F19` -> `P04`
- `F20` -> `P04`

### B) Core/domain/config findings
- `F21` -> `P05`
- `F22` -> `P06`
- `F23` -> `P06`
- `F24` -> `P06`
- `F25` -> `P07`
- `F26` -> `P07`
- `F27` -> `P07`
- `F28` -> `P07`
- `F29` -> `P05`
- `F30` -> `P08`
- `F31` -> `P09`
- `F32` -> `P09`
- `F33` -> `P08`
- `F34` -> `P08`
- `F35` -> `P07`
- `F36` -> `P07`
- `F37` -> `P05`
- `F38` -> `P05`
- `F39` -> `P05`
- `F40` -> `P05`

### C) Storage/schema/data-integrity findings
- `F41` -> `P10`
- `F42` -> `P10`
- `F43` -> `P11`
- `F44` -> `P11`
- `F45` -> `P11`
- `F46` -> `P10`
- `F47` -> `P11`
- `F48` -> `P11`
- `F49` -> `P11`
- `F50` -> `P11`
- `F51` -> `P11`
- `F52` -> `P10`
- `F53` -> `P10`
- `F54` -> `P10`
- `F55` -> `P10`
- `F56` -> `P10`

### D) UI/viewmodel/resource findings
- `F57` -> `P12`
- `F58` -> `P12`
- `F59` -> `P12`
- `F60` -> `P09`
- `F61` -> `P12`
- `F62` -> `P12`
- `F63` -> `P12`

### E) Test/architecture findings
- `F64` -> `P13`
- `F65` -> `P13`
- `F66` -> `P13`
- `F67` -> `P13`
- `F68` -> `P13`
- `F69` -> `P13`
- `F70` -> `P13`

---

## 7) Parallel subagent dispatch blueprint

Use this blueprint repeatedly per wave.

### Step A — Read-only decomposition (parallel)
Dispatch in parallel:
1. File-impact extractor per package.
2. Test-impact extractor per package.
3. Risk/dependency reviewer per package.

### Step B — Implementation (parallel by package)
- Launch one implementation subagent per package that has no shared hotspot lock conflict.
- Keep one integration subagent validating compile + targeted tests continuously.

### Step C — Integration gate (serial)
- Parent agent rebases/merges package branches in dependency order.
- Run wave integration tests + `get_errors` pass.
- If green, advance wave; if red, rollback package and rerun with narrowed patch.

### Suggested parallel batches
- **Batch 1:** `P11` + `P13` (scaffolding only)
- **Batch 2:** `P01` (partial: `F04`, `F05`) + `P06` + `P08` + `P02` (partial) while `P07` is single-owner
- **Batch 3:** `P03` + `P04` + `P05` + `P09` (if no lock overlap)
- **Batch 4:** `P12` + `P13` (final hardening)

---

## 8) Verification strategy by cadence

### After each package
- Run compile + package-targeted tests.
- Run `get_errors` on touched files.

### After each wave
- Run broader integration suites for all modified subsystems.
- Re-run previously green critical tests (anti-regression sweep).

### Final gate
- Run `mvn spotless:apply verify`.
- Ensure no architecture test remains disabled due to this remediation (especially `F67` context).

---

## 9) Risk controls and rollback rules

1. **Schema-first caution:** for uniqueness and stricter validation changes, run preflight data checks before enforcing constraints.
2. **No partial atomicity rollout:** interface + implementation + tests for a transactional path must merge together.
3. **Compatibility shims:** where API status/error semantics change, optionally provide transitional mapping if clients depend on old behavior.
4. **Time-policy migration:** only re-enable strict time-policy architecture guard after all runtime system-default usages are removed/migrated.
5. **Deterministic tests only:** avoid timeout/polling-only pass criteria where a deterministic synchronization primitive is possible.

---

## 10) Agent execution checklist

- [x] Baseline run captured and archived.
- [x] Wave 0 complete (`P11` prerequisites + scaffolding).
- [x] Wave 1 complete (critical security/correctness).
- [x] Wave 2 complete (determinism/contracts/resiliency).
- [x] Wave 3 complete (UI/perf + guardrails).
- [x] Traceability matrix updated with evidence links for all `F01`–`F70`.
- [x] Final quality gate green.

---

## 11) Minimal PR slicing guidance

Prefer small vertical PRs inside each package:
1. failing/strengthened tests,
2. minimal implementation,
3. targeted verification,
4. package-level integration verification.

Do not split a single atomic contract change across independent PRs if it risks temporary invariant breakage (notably `P06`, `P07`, `P10`, `P11`).

---

## 12) Completion artifact expectations

For each package, produce:
- changed files list,
- finding IDs closed,
- tests added/updated,
- command evidence summary,
- residual risks (if any),
- follow-up ticket(s) if deferred.

This produces auditable closure for every finding and keeps the parent agent’s orchestration deterministic and efficient.
