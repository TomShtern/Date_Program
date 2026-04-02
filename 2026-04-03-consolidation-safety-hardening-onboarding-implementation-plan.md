# Consolidation, Safety, Hardening, and Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate application boundaries, normalize safety and verification behavior across all current surfaces, harden the riskiest runtime seams, and add a minimal first-run onboarding flow without starting PostgreSQL, Kotlin, or Android work.

**Architecture:** Keep `core/` framework-agnostic and prefer extending existing app-layer slices (`Profile*UseCases`, `MatchingUseCases`, `SocialUseCases`, `VerificationUseCases`) over adding adapter-specific logic. Add one small dedicated app-layer aggregate (`DashboardUseCases`) where existing slices do not express the needed cross-domain read model cleanly. Preserve the current production composition roots: `ServiceRegistry` for runtime wiring and `ViewModelFactory` for JavaFX wiring.

**Tech Stack:** Java 25 (preview), JavaFX 25, Maven, JDBI 3, Javalin, H2, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo.

---

## Why this plan exists

The code already contains substantial business logic. The next highest-value work is not a technology migration; it is to make the existing Java app cleaner, more consistent, and easier for both humans and AI agents to extend safely.

This plan implements the four agreed workstreams:

1. **Application boundary consolidation**
2. **Safety and verification normalization**
3. **Hardening risky seams**
4. **First-run onboarding**

---

## Scope

### In scope

- Replace important adapter/storage bypasses with app-layer queries or commands.
- Normalize safety and verification behavior across CLI, JavaFX, and REST.
- Add direct tests for request-guard and identity-policy logic.
- Harden moderation/blocking transitions against partial-write behavior.
- Remove remaining runtime `ZoneId.systemDefault()` usage in feature code and re-enable the architecture guard.
- Route incomplete users into a minimal onboarding flow on first login.

### Explicit non-goals

- No PostgreSQL migration work.
- No Kotlin migration work.
- No Android client work.
- No broad package reshuffle or cosmetic refactor of unrelated files.
- No attempt to move all JavaFX-only photo filesystem logic behind the app layer in this sprint.
- Do **not** rewrite `RestApiServer.getCandidates()` unless the replacement app-layer query is intentionally implemented in the same pass; otherwise keep the direct-read exception explicit.

---

## Critical repository context for agentic workers

- Source of truth is the current code: `src/main/java`, `src/test/java`, and `pom.xml`.
- Keep `core/` free of UI, DB-framework, or REST concerns.
- Treat `ServiceRegistry` and `ViewModelFactory` as the composition roots.
- Use `AppClock` and `config.safety().userTimeZone()` instead of `Instant.now()` / `ZoneId.systemDefault()` in feature code.
- When changing behavior implemented in both JDBI storage and in-memory test storage, update **both** paths in the same pass.
- The repo’s full verification gate is `mvn spotless:apply verify`.
- In PowerShell, when selecting multiple tests, prefer `mvn --% ...`.

---

## Design decisions locked in for this sprint

1. **Boundary cleanup will extend existing app-layer slices before adding new ones.**
   - Use `ProfileUseCases` / `ProfileMutationUseCases` for profile-directory and creation work.
   - Use `SocialUseCases` for block/list-blocked/unblock flows.
   - Use `VerificationUseCases` for all verification start/confirm flows.

2. **One new app-layer aggregate is allowed:** `DashboardUseCases`.
   - Justification: dashboard state crosses profile, messaging, matching, and achievements; forcing that into an existing slice would make the wrong abstraction bigger.

3. **`MatchesViewModel` will be cleaned up by using existing use cases plus small new queries, not by inventing a large UI-only service layer.**
   - Prefer `MatchingUseCases.listPagedMatches(...)`, `MatchingUseCases.pendingLikers(...)`, `ProfileUseCases.getUsersByIds(...)`, and `SocialUseCases.listBlockedUsers(...)`.

4. **Safety wording will be made truthful.**
   - Persisted actions must not be labeled `[SIMULATED]`.
   - Verification delivery may still be described as dev/local delivery if the code is intentionally surfaced directly instead of using an external provider.

5. **Onboarding will be minimal and derived, not persisted.**
   - Incomplete users go to `PROFILE` immediately after login.
   - No durable onboarding flag unless a later implementation step proves one is necessary.
   - `ProfileController` stays on the profile screen until activation succeeds; completed users go to `DASHBOARD`.

6. **The deliberate REST direct-read candidates exception remains out of scope unless replaced explicitly.**

---

## Context map

### Files to modify by workstream

| Workstream                        | Primary production files                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Boundary consolidation            | `src/main/java/datingapp/app/cli/ProfileHandler.java`, `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`, `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`, `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`, `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`, `src/main/java/datingapp/core/ServiceRegistry.java`, `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`                                                               |
| Safety/verification normalization | `src/main/java/datingapp/app/cli/SafetyHandler.java`, `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`, `src/main/java/datingapp/ui/screen/SafetyController.java`, `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`, `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`, `src/main/java/datingapp/app/api/RestApiServer.java`, `src/main/java/datingapp/app/api/RestApiDtos.java`                                                                                    |
| Hardening                         | `src/main/java/datingapp/core/matching/TrustSafetyService.java`, `src/main/java/datingapp/core/storage/InteractionStorage.java`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`, `src/main/java/datingapp/core/testutil/TestStorages.java`, `src/main/java/datingapp/app/api/RestApiRequestGuards.java`, `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`, `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`, UI/CLI files still using `ZoneId.systemDefault()` |
| Onboarding                        | `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`, `src/main/java/datingapp/ui/screen/LoginController.java`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`, `src/main/java/datingapp/ui/screen/ProfileController.java`                                                                                                                                                                                                                                                                       |

### Test files that must remain in the verification loop

| Area                   | Test files                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Boundary consolidation | `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`, `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`, `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`, `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`, `src/test/java/datingapp/core/ServiceRegistryTest.java`, `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`, `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`                                                                                                                                                      |
| Safety                 | `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`, `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`, `src/test/java/datingapp/ui/screen/SafetyControllerTest.java`, `src/test/java/datingapp/app/usecase/profile/VerificationUseCasesTest.java`, `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`, `src/test/java/datingapp/core/TrustSafetyServiceTest.java`, `src/test/java/datingapp/core/matching/TrustSafetyServiceSecurityTest.java`, `src/test/java/datingapp/core/matching/TrustSafetyServiceAuditTest.java`, `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java` |
| Hardening              | `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`, `src/test/java/datingapp/app/api/RestApiHealthRoutesTest.java`, `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`, plus new `RestApiRequestGuardsTest.java` and `RestApiIdentityPolicyTest.java`                                                                                                                                                                                                                                                                                                                                                         |
| Onboarding             | `src/test/java/datingapp/ui/screen/LoginControllerTest.java`, `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`, `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`, `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`, `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`                                                                                                                                                                                                                                                                                                   |

### Reference patterns to follow

- `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java` — good use of `ProfileNotesUseCases`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java` — good use of `ProfileInsightsUseCases`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java` — good mutation-slice wiring
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` — existing use-case-backed UI adapter pattern
- `src/test/java/datingapp/app/api/RestApiTestFixture.java` — shared REST test wiring
- `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java` and `src/test/java/datingapp/ui/JavaFxTestSupport.java` — stable JavaFX test patterns

---

## Execution order

Implement in this exact order unless a task explicitly says otherwise:

1. **Task 1–4** create the cleaner app-layer seams and migrate the biggest boundary offenders.
2. **Task 5–7** normalize safety behavior and REST parity on top of those cleaner seams.
3. **Task 8–9** harden the risky seams and re-enable the currently disabled architecture guard.
4. **Task 10–12** add onboarding using the already-cleaned profile/login flows.
5. **Task 13** performs final verification.

Do not start onboarding before the profile/login boundaries are cleaned up. Do not start hardening atomic transitions before the direct-request guard tests exist.

---

## Task 1: Add boundary-support app-layer seams

**Files:**
- Create: `src/main/java/datingapp/app/usecase/dashboard/DashboardUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/core/ServiceRegistryTest.java`
- Test: create `src/test/java/datingapp/app/usecase/dashboard/DashboardUseCasesTest.java`

**Purpose:** Add the minimum app-layer surface needed so CLI and JavaFX code can stop reaching into storage/core services for directory reads, dashboard aggregation, and skeletal user creation.

**Required API additions:**
- Add `ProfileUseCases.GetUsersByIdsQuery` returning `Map<UUID, User>`.
- Add `ProfileMutationUseCases.CreateUserCommand` / `CreateUserResult` for creating a minimal incomplete user account.
- Create `DashboardUseCases` with:
  - `DashboardSummaryQuery`
  - `DashboardSummaryResult`
  - aggregated fields for completion text, total matches, unread count, daily status, daily pick summary, achievement summary, and nudge message.
- Add `ServiceRegistry.getDashboardUseCases()`.

- [ ] **Step 1: Add failing tests for the new app-layer seams**
  - Add `ProfileUseCasesTest` coverage for batched user lookup.
  - Add `DashboardUseCasesTest` for summary aggregation using existing services/test doubles.
  - Add `ServiceRegistryTest` coverage proving the new use case is wired.

- [ ] **Step 2: Implement `ProfileUseCases.GetUsersByIdsQuery`**
  - Reuse existing user lookup semantics.
  - Return a stable `Map<UUID, User>` and ignore unknown IDs instead of failing the whole query.
  - Keep the method read-only.

- [ ] **Step 3: Implement `ProfileMutationUseCases.CreateUserCommand`**
  - Preserve the current minimal-create semantics already used in `LoginViewModel`:
    - blank bio
    - no photos
    - no auto-activation
    - `Dealbreakers.none()`
    - state remains incomplete until activation policy passes later
  - Move validation and defaulting into the use-case layer, not the controller/viewmodel.

- [ ] **Step 4: Implement `DashboardUseCases`**
  - Aggregate exactly the data `DashboardViewModel` already needs.
  - Do not add UI formatting beyond what the ViewModel already expects.
  - Keep this class app-layer only; it may orchestrate services but must not import JavaFX types.

- [ ] **Step 5: Wire `DashboardUseCases` through `ServiceRegistry`**
  - Instantiate it once in the registry.
  - Expose a getter and keep naming aligned with existing `get*UseCases()` methods.

- [ ] **Step 6: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,DashboardUseCasesTest,ServiceRegistryTest test`

Expected:
- new tests pass
- no unrelated constructor wiring regressions

- [ ] **Step 7: Commit**

Suggested commit message:
`refactor: add app-layer dashboard and profile directory seams`

---

## Task 2: Migrate `ProfileHandler` and `DashboardViewModel` onto the new seams

**Files:**
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`

**Purpose:** Remove the most obvious direct storage/core-service leakage from CLI profile handling and dashboard aggregation.

**Implementation requirements:**
- `ProfileHandler` must use:
  - `ProfileUseCases.listUsers()` / `getUserById()` / `getUsersByIds()`
  - `ProfileMutationUseCases.createUser(...)`
  - an app-layer mutation for dealbreaker persistence instead of `userStorage.save(currentUser)`
- `DashboardViewModel` must depend on `DashboardUseCases`, not directly on `RecommendationService`, `AchievementService`, `ConnectionService`, `ProfileService`, or `UiMatchDataAccess`.

- [ ] **Step 1: Add failing characterization tests**
  - `ProfileHandlerTest` should prove create/select/note subject lookup/dealbreaker save go through the use-case layer.
  - `DashboardViewModelTest` should prove summary fields come from `DashboardUseCases`, not direct service composition.

- [ ] **Step 2: Refactor `ProfileHandler`**
  - Remove direct `UserStorage` use from user creation and selection paths.
  - Replace note subject per-ID lookups with batched `getUsersByIds(...)` where practical.
  - Preserve existing CLI prompts and output text unless a behavior change is intentional.

- [ ] **Step 3: Refactor `DashboardViewModel`**
  - Replace direct service dependencies with one `DashboardUseCases` dependency.
  - Keep existing observable properties and UI-facing strings stable.

- [ ] **Step 4: Update `ViewModelFactory`**
  - Inject `DashboardUseCases` into `DashboardViewModel`.
  - Keep factory cache/reset behavior unchanged.

- [ ] **Step 5: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileHandlerTest,DashboardViewModelTest,ViewModelFactoryTest test`

Expected:
- CLI and dashboard tests pass with no direct-storage assumptions left in the modified code paths

- [ ] **Step 6: Commit**

Suggested commit message:
`refactor: route profile handler and dashboard through app-layer seams`

---

## Task 3: Refactor `MatchesViewModel` to consume app-layer reads only

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- Test: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

**Purpose:** Stop `MatchesViewModel` from assembling core/storage-derived state directly when the app layer can provide the same information more consistently.

**Required API additions:**
- Add `SocialUseCases.ListBlockedUsersQuery` and a return type appropriate for the viewmodel.
- Reuse `MatchingUseCases.listPagedMatches(...)` and `pendingLikers(...)`.
- Reuse `ProfileUseCases.getUsersByIds(...)` for counterpart lookup.

- [ ] **Step 1: Add failing tests**
  - `MatchesViewModelTest` should prove the viewmodel can render:
    - paged match cards
    - pending likers
    - blocked IDs
    - counterpart names/ages
    using use cases instead of `UiMatchDataAccess` / `UiUserStore` reads.

- [ ] **Step 2: Add `SocialUseCases.listBlockedUsers(...)`**
  - Use existing `TrustSafetyService.getBlockedUsers(...)` internally.
  - Return an app-layer result type, not raw storage DTOs.

- [ ] **Step 3: Refactor `MatchesViewModel`**
  - Remove production-path fallback reads from `UiMatchDataAccess` and `UiUserStore`.
  - Build UI cards from `MatchingUseCases`, `ProfileUseCases`, and `SocialUseCases` only.
  - Keep note-related functionality untouched unless it is directly affected.

- [ ] **Step 4: Update `ViewModelFactory`**
  - Inject the newly required use-case slices.
  - Remove raw adapter dependencies from the production constructor path where possible.

- [ ] **Step 5: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=MatchesViewModelTest,ViewModelFactoryTest,SocialUseCasesTest test`

Expected:
- match list rendering and pending liker behavior remain stable
- blocking/friend-zone/unmatch actions still work

- [ ] **Step 6: Commit**

Suggested commit message:
`refactor: move matches screen reads behind use cases`

---

## Task 4: Keep the remaining intentional boundary exceptions explicit

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`

**Purpose:** Do not let cleanup work accidentally blur the existing documented exception around raw candidate reads.

- [ ] **Step 1: Decide and document the scope line**
  - Keep `/api/users/{id}/candidates` as the deliberate direct-read route for this sprint.
  - Do not replace it unless a new app-layer raw-candidate query is introduced in the same change.

- [ ] **Step 2: Tighten the in-source comment**
  - Make the exception explicit and narrow.
  - State that `browseCandidates` remains the app-layer daily-pick flow and `getCandidates` remains the raw projection route.

- [ ] **Step 3: Add or update a regression test**
  - `RestApiReadRoutesTest` should continue to assert the raw candidate route behavior.

- [ ] **Step 4: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiReadRoutesTest test`

Expected:
- the documented exception remains explicit and unchanged

- [ ] **Step 5: Commit**

Suggested commit message:
`docs: pin the remaining raw candidate read exception`

---

## Task 5: Normalize safety use-case boundaries and truthful wording in CLI/JavaFX

**Files:**
- Modify: `src/main/java/datingapp/app/cli/SafetyHandler.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/SafetyController.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java` (only if response shapes need cleanup)
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Test: `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/SafetyControllerTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/VerificationUseCasesTest.java`
- Test: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

**Purpose:** Make safety and verification behavior consistent and truthful across CLI and JavaFX before exposing the same flows over REST.

**Required outcomes:**
- CLI and JavaFX both use `VerificationUseCases` for verification start/confirm.
- CLI and JavaFX both use `SocialUseCases` for block/report/list-blocked/unblock.
- Persisted actions are no longer labeled `[SIMULATED]`.
- If a blocked-user UI subtitle is not a real timestamp, rename it from `blockedAtLabel` to a truthful name such as `statusLabel`.

- [ ] **Step 1: Add failing tests around wording and delegated flows**
  - Update or add tests proving:
    - verification start/confirm go through `VerificationUseCases`
    - list blocked users and unblock go through `SocialUseCases`
    - persisted actions no longer advertise simulation

- [ ] **Step 2: Extend `SocialUseCases`**
  - Add `listBlockedUsers(...)` and `unblockUser(...)` app-layer operations.
  - Keep report and block semantics unchanged.

- [ ] **Step 3: Refactor `SafetyViewModel`**
  - Replace inline verification-code generation and direct user mutation with `VerificationUseCases`.
  - Replace direct `TrustSafetyService` reads/writes with `SocialUseCases` where appropriate.
  - Rename misleading UI fields/messages if timestamps are not actually available.

- [ ] **Step 4: Refactor `SafetyHandler`**
  - Route unblock/list-blocked/report/block through use cases.
  - Keep the one truthful local/dev note about verification code delivery if the code is intentionally shown directly.

- [ ] **Step 5: Update `ViewModelFactory` and `SafetyController`**
  - Inject any new dependencies.
  - Keep the controller binding-only; do not move business logic there.

- [ ] **Step 6: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=SafetyHandlerTest,SafetyViewModelTest,SafetyControllerTest,VerificationUseCasesTest,SocialUseCasesTest test`

Expected:
- no `[SIMULATED]` text remains for persisted actions
- CLI and JavaFX use the same underlying flows

- [ ] **Step 7: Commit**

Suggested commit message:
`refactor: normalize safety and verification behavior across cli and javafx`

---

## Task 6: Add REST safety parity

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java` (if response data must be widened)
- Test: `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- Test: create `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiDtosTest.java`

**Purpose:** Bring REST up to the same functional surface as CLI/JavaFX for current safety features.

**Routes to add:**
- `GET /api/users/{id}/blocked-users`
- `DELETE /api/users/{id}/block/{targetId}`
- `POST /api/users/{id}/verification/start`
- `POST /api/users/{id}/verification/confirm`

**DTOs to add:**
- `BlockedUserDto`
- `BlockedUsersResponse`
- `StartVerificationRequest`
- `StartVerificationResponse`
- `ConfirmVerificationRequest`
- `ConfirmVerificationResponse`

**Important response contract note:**
- Because the current verification flow is local/dev-only and has no external provider, return the generated code only in a clearly named dev-only field such as `devVerificationCode`. Do **not** imply production-safe delivery.

- [ ] **Step 1: Add failing REST tests first**
  - New verification route tests:
    - start verification success
    - confirm verification success
    - invalid method/contact/code cases
  - Relationship route tests:
    - list blocked users
    - unblock success
    - unblock forbidden/not-found cases

- [ ] **Step 2: Add DTOs to `RestApiDtos.java`**
  - Keep naming aligned with the existing DTO style.
  - Reuse current user/match/notification DTO conventions.

- [ ] **Step 3: Add the new routes to `RestApiServer`**
  - Route list-blocked and unblock through `SocialUseCases`.
  - Route verification start/confirm through `VerificationUseCases`.
  - Keep all identity checks and request validation consistent with the existing REST adapter style.

- [ ] **Step 4: Update DTO and route tests**
  - Ensure malformed JSON and invalid enum handling remain consistent with existing error responses.

- [ ] **Step 5: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,RestApiDtosTest test`

Expected:
- new routes behave consistently with existing REST error handling and identity rules

- [ ] **Step 6: Commit**

Suggested commit message:
`feat: add rest safety and verification parity routes`

---

## Task 7: Lock down request-guard and identity-policy behavior with direct tests

**Files:**
- Create: `src/test/java/datingapp/app/api/RestApiRequestGuardsTest.java`
- Create: `src/test/java/datingapp/app/api/RestApiIdentityPolicyTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiHealthRoutesTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`

**Purpose:** Pin down the behavior of the REST guard/policy helpers before further route expansion and hardening work.

**Coverage to add:**
- `RestApiRequestGuardsTest`
  - localhost enforcement
  - mutating-route detection
  - exempt paths (`/api/health`, `/api/location/resolve`)
  - rate-limit retry/usage headers
  - stale-window eviction behavior if reachable
- `RestApiIdentityPolicyTest`
  - `resolveActingUserId`
  - `requireActingUserId`
  - path-param match validation for `id` and `authorId`
  - conversation membership parsing and rejection

- [ ] **Step 1: Add the two new direct test classes**
  - Keep them unit-level where possible.
  - Avoid over-relying on reflection now that direct tests exist.

- [ ] **Step 2: Stabilize or update route-level tests if contracts shift**
  - If response codes become stricter (for example, 400 vs 403), update the route tests intentionally rather than papering over the change.

- [ ] **Step 3: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiRequestGuardsTest,RestApiIdentityPolicyTest,RestApiRateLimitTest,RestApiHealthRoutesTest,RestApiPhaseTwoRoutesTest test`

Expected:
- direct helper behavior is pinned down independent of route smoke tests

- [ ] **Step 4: Commit**

Suggested commit message:
`test: add direct coverage for rest guards and identity policy`

---

## Task 8: Harden moderation/blocking atomicity

**Files:**
- Modify: `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- Modify: `src/main/java/datingapp/core/storage/InteractionStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Modify: `src/main/java/datingapp/core/testutil/TestStorages.java`
- Test: `src/test/java/datingapp/core/matching/TrustSafetyServiceSecurityTest.java`
- Test: `src/test/java/datingapp/core/matching/TrustSafetyServiceAuditTest.java`
- Test: create or extend `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`

**Purpose:** Prevent partial-write behavior when blocking users or performing report-followed-by-block flows.

**Design choice for this task:**
- Follow the existing repository pattern for atomic relationship transitions.
- Add a dedicated atomic block transition contract to `InteractionStorage` rather than scattering best-effort writes across service code.

**Required storage contract additions:**
- `supportsAtomicBlockTransition()`
- `blockTransition(...)`

**Required behavior changes:**
- `TrustSafetyService.block(...)` should prefer the atomic path.
- Use copy-on-write style aggregates when preparing match/conversation state to avoid in-memory mutation leaks on failure.
- If a non-atomic fallback remains for unsupported storages, it must be explicit, logged, and covered by tests.
- Keep `report(...)` success semantics explicit: reporting success does not imply follow-up block success unless the block succeeded.

- [ ] **Step 1: Add failing security/atomicity tests**
  - Add failure injection for:
    - `trustSafetyStorage.save(block)` failure
    - conversation archive failure
    - conversation visibility failure
    - report succeeded but follow-up block failed
  - Ensure `TestStorages` can model these failures deterministically.

- [ ] **Step 2: Extend `InteractionStorage` with atomic block-transition methods**
  - Follow the same contract style used by existing relationship transition methods.

- [ ] **Step 3: Implement the atomic path in `JdbiMatchmakingStorage`**
  - Keep transaction rollback semantics correct.
  - Do not return false after partial mutations inside a transaction; throw to force rollback where necessary.

- [ ] **Step 4: Update `TestStorages` to match the new semantics**
  - In-memory behavior must stay semantically aligned with the JDBI path.

- [ ] **Step 5: Refactor `TrustSafetyService.block(...)` to use the atomic path**
  - Maintain audit logging.
  - Preserve existing block/report rules.

- [ ] **Step 6: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=TrustSafetyServiceSecurityTest,TrustSafetyServiceAuditTest,JdbiMatchmakingStorageTransitionAtomicityTest test`

Expected:
- failures no longer leave partial persisted moderation state

- [ ] **Step 7: Commit**

Suggested commit message:
`fix: make block transitions atomic across moderation updates`

---

## Task 9: Remove remaining runtime `ZoneId.systemDefault()` usage and re-enable the architecture guard

**Files:**
- Modify: `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- Modify: `src/main/java/datingapp/ui/screen/ChatController.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Modify: `src/main/java/datingapp/ui/screen/SocialController.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java`
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MessagingHandler.java`
- Test: any UI/CLI test files affected by changed age/time formatting

**Purpose:** Close the known time-policy rollout gap and let the architecture test enforce it again.

**Required behavior:**
- Replace runtime `ZoneId.systemDefault()` usage with `config.safety().userTimeZone()` or an already-injected zone source.
- Do not introduce a new ad-hoc time source in UI/CLI code.

- [ ] **Step 1: Add or update tests around time-sensitive formatting where needed**
  - Pin any changed age/timestamp formatting behavior before modifying the production code.

- [ ] **Step 2: Replace remaining `ZoneId.systemDefault()` call sites**
  - Thread the configured zone through existing helpers or constructors rather than using globals.

- [ ] **Step 3: Remove `@Disabled` from `TimePolicyArchitectureTest`**
  - Keep the allowlist minimal.
  - Update the class comment to reflect the now-enforced state.

- [ ] **Step 4: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=TimePolicyArchitectureTest,LoginControllerTest,NotesViewModelTest,StatsHandlerTest,MessagingHandlerTest test`

Expected:
- the architecture test passes without `@Disabled`
- time formatting remains deterministic

- [ ] **Step 5: Commit**

Suggested commit message:
`refactor: enforce configured timezone usage in feature code`

---

## Task 10: Add post-login onboarding routing

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

**Purpose:** Route incomplete users directly to profile completion instead of dropping every user onto the dashboard.

**Required implementation shape:**
- Keep `LoginViewModel.login()` simple.
- Add a post-login routing helper such as `resolvePostLoginDestination()` or equivalent.
- Use existing profile readiness truth (`User` state and `ProfileActivationPolicy`) rather than inventing a new heuristic.

- [ ] **Step 1: Add failing tests**
  - complete user login → `DASHBOARD`
  - incomplete user login → `PROFILE`
  - login still sets `AppSession.currentUser`

- [ ] **Step 2: Add the routing helper to `LoginViewModel`**
  - Reuse the already-injected `ProfileActivationPolicy`.
  - Do not make the controller decide readiness rules itself.

- [ ] **Step 3: Update `LoginController` navigation**
  - After successful login, navigate based on the new helper.
  - Keep the login screen behavior unchanged on failure.

- [ ] **Step 4: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,LoginControllerTest test`

Expected:
- incomplete users are routed to profile immediately after login

- [ ] **Step 5: Commit**

Suggested commit message:
`feat: route incomplete users into onboarding after login`

---

## Task 11: Make profile save onboarding-aware

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`

**Purpose:** Keep incomplete users on the profile screen until activation actually succeeds, instead of treating any successful save as the end of onboarding.

**Required implementation shape:**
- Replace or supplement the existing `Consumer<Boolean>` save callback with a richer result object, for example `SaveOutcome`.
- `SaveOutcome` must distinguish at least:
  - save failed
  - save succeeded but profile remains incomplete
  - save succeeded and profile activated

- [ ] **Step 1: Add failing tests**
  - `ProfileViewModelTest`: save result distinguishes activated vs saved-draft
  - `ProfileControllerTest`: successful incomplete save keeps user on profile; activated save goes to dashboard
  - `ProfileUseCasesTest`: `ProfileCompleted` event only fires when activation succeeds

- [ ] **Step 2: Add the richer save outcome to `ProfileViewModel`**
  - Keep existing observable completion state intact.
  - Do not move activation logic into the controller.

- [ ] **Step 3: Update `ProfileController`**
  - Activated save → navigate to dashboard
  - Incomplete save → remain on profile and surface completion guidance using existing `completionStatus` / `completionDetails`

- [ ] **Step 4: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileViewModelTest,ProfileControllerTest,ProfileUseCasesTest test`

Expected:
- onboarding completion is based on activation success, not merely save success

- [ ] **Step 5: Commit**

Suggested commit message:
`feat: keep onboarding users on profile until activation succeeds`

---

## Task 12: Keep dashboard guidance as the soft fallback

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java` (only if needed after Task 2)
- Test: `src/test/java/datingapp/ui/viewmodel/DashboardViewModelTest.java`

**Purpose:** Ensure users who reach the dashboard while still incomplete still get clear guidance, even after onboarding routing is added.

- [ ] **Step 1: Review `DashboardViewModel` after Tasks 2, 10, and 11**
  - Confirm `profileNudgeMessage` remains correct for incomplete users.
  - Confirm completed users do not get stale onboarding nudges.

- [ ] **Step 2: Add or update tests if behavior changed**
  - incomplete user → non-empty nudge
  - complete user → empty or non-onboarding nudge

- [ ] **Step 3: Run targeted tests**

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=DashboardViewModelTest test`

Expected:
- dashboard remains a clean fallback surface, not the primary onboarding driver

- [ ] **Step 4: Commit**

Suggested commit message:
`test: confirm dashboard fallback guidance for incomplete users`

---

## Task 13: Final verification and cleanup pass

**Files:**
- Modify only if tests or quality gates expose necessary fixes.

**Purpose:** Prove the full sprint works end to end and leave the repository in a green, understandable state.

- [ ] **Step 1: Run focused regression suites by workstream**

Boundary:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileHandlerTest,MatchesViewModelTest,DashboardViewModelTest,ViewModelFactoryTest,ProfileUseCasesTest,ServiceRegistryTest test`

Safety and REST parity:
`mvn --% -Dcheckstyle.skip=true -Dtest=SafetyHandlerTest,SafetyViewModelTest,SafetyControllerTest,VerificationUseCasesTest,SocialUseCasesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest test`

Hardening:
`mvn --% -Dcheckstyle.skip=true -Dtest=TrustSafetyServiceSecurityTest,TrustSafetyServiceAuditTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest,TimePolicyArchitectureTest test`

Onboarding:
`mvn --% -Dcheckstyle.skip=true -Dtest=LoginViewModelTest,LoginControllerTest,ProfileViewModelTest,ProfileControllerTest,DashboardViewModelTest test`

Expected:
- all focused suites pass

- [ ] **Step 2: Run compile/build quality gate**

Run:
`mvn spotless:apply verify`

Expected:
- build success
- formatting, checkstyle, PMD, and JaCoCo all pass

- [ ] **Step 3: Perform a final code review sweep**
  - confirm there are no stale `[SIMULATED]` labels for persisted actions
  - confirm no new direct-storage bypasses were introduced
  - confirm `ZoneId.systemDefault()` is gone from feature code
  - confirm onboarding navigation is understandable and test-covered

- [ ] **Step 4: Commit**

Suggested commit message:
`chore: complete consolidation hardening and onboarding sprint`

---

## Review checklist for the agent implementing this plan

Before marking the sprint done, confirm all of the following:

- [ ] `ProfileHandler` no longer creates/selects/saves users through raw `UserStorage` in the cleaned code paths.
- [ ] `DashboardViewModel` no longer aggregates directly from raw core services.
- [ ] `MatchesViewModel` no longer uses raw UI adapters for production-path match/block/user reads.
- [ ] `SafetyViewModel` and `SafetyHandler` use the same app-layer safety and verification seams.
- [ ] REST exposes blocked-users, unblock, verification-start, and verification-confirm flows.
- [ ] Direct unit tests exist for `RestApiRequestGuards` and `RestApiIdentityPolicy`.
- [ ] Blocking no longer leaves partial persisted state on failure paths covered by tests.
- [ ] `TimePolicyArchitectureTest` is enabled and passing.
- [ ] Incomplete users go to `PROFILE` after login.
- [ ] Profile save stays on onboarding until activation succeeds.
- [ ] `mvn spotless:apply verify` passes.

---

## Recommended implementation mode

This plan is best executed in **subagent-driven** mode, with one fresh implementation subagent per task and a short parent-agent review between tasks. The safest grouping is:

- Tasks 1–4: sequential
- Tasks 5–7: sequential
- Tasks 8–9: sequential
- Tasks 10–12: sequential
- Task 13: final verification only after all earlier tasks are green

Avoid parallel edits to these shared files:
- `ServiceRegistry.java`
- `ViewModelFactory.java`
- `SocialUseCases.java`
- `ProfileUseCases.java`
- `ProfileMutationUseCases.java`
- `RestApiServer.java`

---

## Completion criteria

This implementation plan is complete when:

1. All thirteen tasks are checked off.
2. Focused test suites for each workstream are green.
3. `mvn spotless:apply verify` is green.
4. The codebase is cleaner in the intended ways:
   - boundaries are clearer,
   - safety behavior is truthful and consistent,
   - risky seams are hardened and tested,
   - onboarding is explicit and functional.
