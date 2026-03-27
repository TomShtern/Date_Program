# Retrospective Architecture Decisions To Revisit

Date: 2026-02-27
Scope: Project-wide architecture (not file-level refactors)

This document focuses on decisions that were reasonable early, but in hindsight now drive cross-cutting complexity.

## What This Is

- Not a style/lint/optimization list.
- Not "large-file cleanup".
- This is a list of foundational architectural choices that currently force complexity across CLI, JavaFX, core, and storage.

## Implementation Status Snapshot (as of 2026-03-01)

Legend: ✅ Implemented | ⚠️ Partial | ❌ Not implemented

| Decision                                          | Status            | Notes                                                                                  |
|---------------------------------------------------|-------------------|----------------------------------------------------------------------------------------|
| 1. Process-global runtime singletons              | ❌ Not implemented | Static lifecycle state remains (`ApplicationStartup`, `AppSession`, `DatabaseManager`) |
| 2. Service locator as primary composition         | ❌ Not implemented | `ServiceRegistry` remains composition backbone                                         |
| 3. No application use-case layer                  | ✅ Implemented     | `app/usecase/*` is present and wired                                                   |
| 4. Storage boundaries by technical area           | ❌ Not implemented | Storage remains split by technical concern                                             |
| 5. Global session context                         | ❌ Not implemented | `AppSession` singleton remains                                                         |
| 6. Navigation singleton does too much             | ❌ Not implemented | `NavigationService` remains monolithic                                                 |
| 7. Ad-hoc ViewModel async patterns                | ✅ Implemented     | Shared `ui/async` abstractions are in place and used broadly                           |
| 8. Runtime config source not single-sourced       | ⚠️ Partial         | Feature-level leaks mostly removed; one runtime `AppConfig.defaults()` path remains    |
| 9. Domain services include presentation semantics | ❌ Not implemented | Formatting/labels remain in core matching services                                     |
| 10. Java-coded schema/migrations                  | ❌ Not implemented | SQL-first migration strategy not adopted                                               |
| 11. Workflow/state rules scattered                | ⚠️ Partial         | `core/workflow` policy layer exists but not fully centralized                          |
| 12. Denormalized multi-value persistence debt     | ❌ Not implemented | Legacy serialized multi-value persistence remains                                      |
| 13. Cross-cutting side effects are imperative     | ✅ Implemented     | Event bus + handlers (`app/event`) now centralize side effects                         |
| 14. Fragmented failure semantics                  | ⚠️ Partial         | Use-case layer unified, domain services still mixed                                    |
| 15. Time/timezone policy not unified              | ⚠️ Partial         | `TimePolicy` exists, but non-policy time usage still exists                            |

## Decision 1: Process-Global Runtime Singletons

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Runtime/lifecycle state is global static singleton state.

Evidence:
- `ApplicationStartup` static runtime graph and lifecycle.
- `DatabaseManager` static mutable JDBC URL and singleton instance.
- `AppSession` singleton used across entry points.

Why it made sense early:
- Fast startup wiring with minimal DI ceremony.

Why it hurts now:
- Hidden initialization order and hidden coupling.
- Hard to run multiple isolated runtimes.
- Teardown/reset behavior leaks across tests/features/channels.

Retrospective replacement:
- Introduce explicit `ApplicationRuntime` object:
  - owns config, DB manager, services, session context, and lifecycle.
  - each entry point (CLI/UI/API) receives a runtime instance.
- Remove static mutable runtime holders from feature code.

Expected simplification:
- Clear lifecycle ownership and deterministic startup/shutdown.
- Fewer "global state surprises" and easier integration testing.

## Decision 2: Service Locator as Primary Composition Pattern - THIS SUGGESTION IS APPROVED BY THE DEVELOPER

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Most adapter construction is driven by a broad `ServiceRegistry` and `fromServices(...)`.

Evidence:
- `ServiceRegistry`, `StorageFactory`, `ViewModelFactory`, multiple CLI handlers.

Why it made sense early:
- Convenient one-stop wiring while features were small.

Why it hurts now:
- High fan-in and broad dependencies everywhere.
- Adapters depend on too many services, not feature-level contracts.
- Changes ripple across layers because boundaries are weak.

Retrospective replacement:
- Replace registry-first injection with feature facades/use-case bundles:
  - `ProfileFacade`, `MatchingFacade`, `MessagingFacade`, `SocialFacade`.
- Adapters consume only those feature interfaces.

Expected simplification:
- Smaller constructor surfaces.
- Better feature isolation and easier rewrites per slice.

## Decision 3: No Dedicated Application-UseCase Layer - THIS SUGGESTION IS APPROVED BY THE DEVELOPER - ✅ FIXED / IMPLEMENTED

**Status (2026-03-01): ✅ Implemented**

What was decided:
- UI and CLI adapters orchestrate domain/storage details directly.

Evidence:
- Complex orchestration in CLI handlers and UI viewmodels/controllers.
- Same business flows implemented per channel with channel-specific variants.

Why it made sense early:
- Fewer layers and fast feature delivery.

Why it hurts now:
- Behavior drift between CLI and UI.
- Duplicate orchestration, duplicate fallback logic, duplicate edge-case handling.
- Hard to change behavior once without touching both channels.

Retrospective replacement:
- Add an application layer with explicit use-case commands:
  - examples: `BrowseCandidates`, `ActOnMatch`, `SaveProfile`, `HandleFriendRequest`.
- Channel adapters map input/output only.

Expected simplification:
- One behavior implementation reused by both CLI and UI. THIS IS GOOD. THIS IS IMPORTANT. THIS IS THE KIND OF STUFF I WANT TO FIX.
- Major reduction in duplicated control flow.

## Decision 4: Storage Boundaries Chosen by Technical Area, Not Transactional Aggregate - THIS SUGGESTION IS MAYBE/PROBABLY APPROVED BY THE DEVELOPER

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Related transitions span multiple storages/services with optional atomic paths.

Evidence:
- `ConnectionService` contains explicit non-atomic fallback comments.
- `InteractionStorage` has optional atomic transition API.
- Transition logic split across connection and matchmaking storage implementations.

Why it made sense early:
- Incremental addition of messaging/social/matching subsystems.

Why it hurts now:
- Complex compensation paths.
- Inconsistent guarantees depending on storage backend capabilities.
- Transition consistency is harder than it should be.

Retrospective replacement:
- Redraw boundaries around transactional aggregates:
  - unified `RelationshipTransitionRepository` for friend-zone/graceful-exit/unmatch atomicity.
  - separate `MessagingRepository` for chat stream concerns.
- Use one explicit transaction boundary per relationship state transition.

Expected simplification:
- Remove compensating-write complexity.
- Predictable consistency model.

## Decision 5: Global Session Context Instead of Flow-Scoped User Context - NOT SURE IF THERE IS A CLEAR BENIFIT FOR THIS FOR NOW

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Current-user context is process-global mutable state (`AppSession`).

Evidence:
- Singleton session used by CLI, UI factory, controllers/viewmodels.

Why it made sense early:
- Easy login state sharing.

Why it hurts now:
- Hidden identity propagation.
- Listener lifecycle complexity.
- Harder multi-user isolation and request-scope reasoning.

Retrospective replacement:
- Make user context explicit in use-case execution:
  - `UserContext` passed to application layer.
- Keep a thin UI/CLI session holder only as adapter concern, not domain/global concern.

Expected simplification:
- Less implicit coupling, clearer data flow, safer concurrency.

## Decision 6: Navigation Singleton as Router + History + Context Bus + View Lifecycle Manager - NOT SURE IF THERE IS A CLEAR BENIFIT FOR THIS FOR NOW

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- One `NavigationService` does everything and passes payloads as `Object`.

Evidence:
- Singleton navigation service with global history and `setNavigationContext(Object)`.

Why it made sense early:
- Quick way to wire screen transitions.

Why it hurts now:
- Type-unsafe context passing.
- Navigation state and screen lifecycle concerns are tightly coupled.
- Difficult to evolve navigation behavior without broad UI impact.

Retrospective replacement:
- Split into:
  - `Navigator` (typed route commands),
  - `NavigationHistoryStore`,
  - `TransitionEngine`,
  - typed route payload contracts.

Expected simplification:
- Safer route context handoff and clearer navigation responsibilities.

## Decision 7: Ad-Hoc Async Model Implemented in Every ViewModel - THIS SUGGESTION IS APPROVED BY THE DEVELOPER - ✅ FIXED / IMPLEMENTED

**Status (2026-03-01): ✅ Implemented**

What was decided:
- Each viewmodel individually manages virtual threads, `Platform.runLater`, atomic guards, disposed flags.

Evidence:
- Repeated concurrency scaffolding across `LoginViewModel`, `MatchingViewModel`, `MatchesViewModel`, `ChatViewModel`, etc.

Why it made sense early:
- Localized async handling per screen.

Why it hurts now:
- Repeated and inconsistent concurrency patterns.
- Higher chance of lifecycle/race inconsistencies.
- Hard to reason globally about UI async behavior.

Retrospective replacement:
- Introduce shared UI async execution abstraction:
  - `UiTaskRunner` / `ViewModelAsyncScope` with cancellation + main-thread dispatch + standardized error handling.
- Viewmodels express intent, not thread mechanics.

Expected simplification:
- Less duplicate async code and fewer race/lifecycle bugs.

## Decision 8: Runtime Config Source Is Not Single-Sourced - THIS SUGGESTION IS APPROVED BY THE DEVELOPER

**Status (2026-03-01): ⚠️ Partial**

What was decided:
- Some runtime logic still calls `AppConfig.defaults()` directly instead of injected config.

Evidence:
- Defaults calls from adapters/viewmodels/handlers despite injected config in many paths.

Why it made sense early:
- Easy fallback before full config propagation matured.

Why it hurts now:
- Behavior drift risk between configured runtime and default assumptions.
- Makes behavior harder to predict/test.

Retrospective replacement:
- Enforce config source rule:
  - runtime behavior uses injected config only.
  - defaults only at composition root/bootstrap.

Expected simplification:
- Deterministic behavior and fewer hidden discrepancies.

## Decision 9: Domain Services Contain Presentation Semantics - THIS SUGGESTION IS APPROVED BY THE DEVELOPER

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Core services return/construct presentation-oriented labels/text formatting concerns.

Why it made sense early:
- Quick user-visible output with less mapping layers.

Why it hurts now:
- Channel coupling and unclear domain boundaries.
- Harder to evolve text/presentation independently.

Retrospective replacement:
- Core returns semantic data.
- CLI/UI formatters/adapters generate channel-specific text, labels, icons.

Expected simplification:
- Cleaner domain model and easier UI/CLI evolution.

## Decision 10: Java-Coded Schema/Migration as Primary Source of Truth - NOT SURE. THE IDEA OF MOVING TO SQL-FIRST MAKES SENSE TO ME, BUT I DON'T KNOW IF IT'S WORTH THE MIGRATION COST RIGHT NOW. THIS IS A MAYBE. ALSO, NOT SURE ABOUT WHICH FLAVOUR OF SQL SHOULD WE CHOSE. THINKING ABOUT REGULAR SQL OR SQLITE OR POSTGRESQL OR A NEW SUGGESTION IF IT MAKES SENSE, WE NEED TO THINK ABOUT THIS MORE.

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- DDL and migration evolution mostly embedded in Java code.

Evidence:
- Large imperative DDL in `SchemaInitializer`.
- Backfill-heavy migration code in `MigrationRunner`.

Why it made sense early:
- Single-language convenience and direct control.

Why it hurts now:
- Schema reasoning is harder than SQL-first migration.
- Compatibility/backfill code accumulates and obscures current schema intent.

Retrospective replacement:
- SQL-first schema strategy:
  - canonical baseline SQL.
  - versioned SQL migrations.
  - thin Java runner.

Expected simplification:
- Clear schema source-of-truth and lower migration complexity.

## Decision 11: Workflow/State Rules Are Scattered Instead of Centrally Modeled

**Status (2026-03-01): ⚠️ Partial**

What was decided:
- State transitions are partially in entities, partially in services, and partially orchestrated in adapters.

Evidence:
- `User` and `Match` enforce some transitions.
- `ConnectionService` enforces additional transition/business checks.
- CLI/UI adapters still include transition-related branching and fallback handling.

Why it made sense early:
- Fast feature delivery by adding checks where needed.

Why it hurts now:
- Transition policy is split across many classes.
- Hard to guarantee one canonical rule set for all channels.
- Easy to accidentally bypass/duplicate transition guards.

Retrospective replacement:
- Explicit workflow policy layer:
  - `RelationshipWorkflowPolicy`.
  - `ProfileActivationPolicy`.
- Central transition matrix/guard evaluation used by all use-cases.
- Adapters call use-cases, not transition internals.

Expected simplification:
- One source of truth for allowed states and transitions.
- Lower risk of behavior drift across CLI/UI/API.

## Decision 12: Denormalized Multi-Value Persistence Became a Long-Term Complexity Source

**Status (2026-03-01): ❌ Not implemented**

What was decided:
- Many multi-value profile fields were stored in serialized/string columns for convenience.

Evidence:
- `JdbiUserStorage` contains JSON/CSV/legacy parsing fallbacks.
- Schema stores `photo_urls`, `interests`, `interested_in`, and multiple `db_*` multi-value fields as strings.

Why it made sense early:
- Minimal schema footprint and quick iteration.

Why it hurts now:
- Mapper/codec complexity is high and fragile.
- Querying/filtering by these fields is awkward and less reliable.
- Backward-compatibility parsing paths accumulate permanent debt.

Retrospective replacement:
- Normalize to explicit tables:
  - `user_photos`, `user_interests`, `user_interested_in`, `user_dealbreakers_*`.
- Keep optional read-model projections/materialized views for fast UI queries.

Expected simplification:
- Cleaner storage code, fewer parser fallbacks, easier querying.

## Decision 13: Cross-Cutting Side Effects Are Triggered Imperatively in Many Places

**Status (2026-03-01): ✅ Implemented**

What was decided:
- Achievements, notifications, activation checks, metrics, and read-marking are called manually inside many flows.

Evidence:
- Achievement unlock checks in multiple CLI handlers.
- Notification writes/mark-read done in channel flows and services.
- Activation checks repeated in profile/login/viewmodel paths.
- Activity metrics called directly from service methods.

Why it made sense early:
- Direct and obvious side effects near the triggering action.

Why it hurts now:
- Side effects are inconsistent by channel/path.
- New features require editing many call sites.
- Hard to reason about what always happens vs. best-effort happens.

Retrospective replacement:
- Introduce domain/application event pipeline (can stay in-process and synchronous):
  - events like `SwipeRecorded`, `FriendRequestAccepted`, `ProfileSaved`.
  - handlers for achievements, metrics, notifications, read models.

Expected simplification:
- Consistent side effects and easier feature extension.
- Cleaner use-case code focused on primary business outcome.

## Decision 14: Failure Semantics Are Fragmented Across Layers

**Status (2026-03-01): ⚠️ Partial**

What was decided:
- Different modules use mixed failure models: exceptions, booleans, `Optional.empty`, result records with error strings.

Evidence:
- `ConnectionService` result records.
- Boolean-return transition/storage methods.
- Exception-based failures in models/storage.
- Optional-empty used both for “not found” and “cannot proceed” style outcomes.

Why it made sense early:
- Local choices per feature were simpler short-term.

Why it hurts now:
- Error handling is inconsistent and repetitive in adapters.
- Hard to map reliably to CLI/UI/API behavior.
- Harder to compose use-cases predictably.

Retrospective replacement:
- Canonical application error model:
  - `Result<T, AppError>` (or equivalent sealed error hierarchy).
- Keep infra exceptions at boundaries; convert to typed app errors once.
- Channel-specific mappers for user-facing messaging.

Expected simplification:
- Predictable error propagation and less duplicated handling logic.

## Decision 15: Time/Timezone Policy Is Not Architecturally Unified

**Status (2026-03-01): ⚠️ Partial**

What was decided:
- Time logic uses a mix of `AppClock`, injected `Clock`, `ZoneId.systemDefault()`, and direct defaults usage.

Evidence:
- Some paths use configured user timezone.
- Some paths use system default timezone.
- Some feature code still uses default-config lookups in adapters.

Why it made sense early:
- Practical shortcuts while timezone-sensitive features were still evolving.

Why it hurts now:
- Inconsistent behavior between screens/channels.
- Harder reproducibility in tests.
- Hidden date/time edge-case risk (daily limits, age display, recency formatting).

Retrospective replacement:
- Introduce explicit `TimePolicy`/`UserTimeContext`:
  - one canonical source for `now`, `today`, and user-facing zone.
- Ban direct `ZoneId.systemDefault()` usage outside composition/bootstrap.

Expected simplification:
- Uniform temporal behavior and simpler testing.

## Priority If You Want Maximum Architectural Clarity

1. Replace runtime globals (`ApplicationRuntime` + scoped context).
2. Replace service-locator flow with feature facades/use-case layer.
3. Add centralized workflow/state policy for transitions and activation.
4. Standardize async execution abstraction for all viewmodels.
5. Redraw transactional storage boundaries for relationship transitions.
6. Introduce event pipeline for cross-cutting side effects.
7. Unify runtime config/time policy usage (no defaults leakage in features).
8. Normalize multi-value persistence strategy where it reduces mapping complexity.
9. Split navigation service into typed subsystems.
10. Move schema/migration to SQL-first (when migration cost window is acceptable).

These moves are the highest leverage architectural resets and explain most present-day complexity in retrospective.

## If We Were Architecting This App Today (With Current Feature Set)

1. Composition:
   1. Explicit `ApplicationRuntime` per process entry point.
   2. No feature-level static singletons.
2. Application layer:
   1. Command/query use-cases as only orchestration surface.
   2. Typed `Result<T, AppError>` failures.
3. Domain:
   1. Aggregates + explicit workflow policies for state transitions.
   2. Domain returns semantic outcomes, not channel-formatted copy.
4. Cross-cutting:
   1. In-process event pipeline for achievements/notifications/metrics/read models.
   2. Unified `TimePolicy` and config source contract.
5. Persistence:
   1. Transaction boundaries aligned to business aggregates.
   2. Normalized write model where multi-value fields became complex.
   3. Optional denormalized read model for performance.
6. Adapters:
   1. CLI/UI/API are thin mappers over use-cases.
   2. Shared async/task abstraction in UI.
