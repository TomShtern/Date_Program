# Codebase Review

## Summary

**Scope:** 180 Java source files under `src/main/java`, totaling about 42,581 lines of code excluding blanks and comments.

**Analysis method:** Direct source review across `app`, `core`, `storage`, and `ui`, supported by targeted AST-grep queries for duplication, singleton usage, synchronization, exception patterns, and cross-layer imports.

**Overall assessment:** The project has a strong architectural base — layered boundaries, good use of modern Java, a clear composition root, and many sound domain abstractions. The biggest debt is concentrated in oversized files, repeated scoring/business logic, inconsistent construction patterns, and several correctness-sensitive async and concurrency contracts. The highest-value work is class decomposition, scoring unification, cleanup of chat/matching flow contracts, and safer schema/runtime behavior.

---

## Findings by Category

### 1. Readable Code

#### Issue 1.1 — Large files still mix multiple responsibilities - IGNORE THIS FOR NOW!
- **Location:** `MatchingUseCases.java`, `ProfileHandler.java`, `MatchingHandler.java`, `ProfileController.java`, `ProfileViewModel.java`, `RestApiServer.java`, `MigrationRunner.java`, `AppConfig.java`
- **What is wrong:** Several large adapter/use-case files still combine multiple responsibilities in a single unit.
- **Why it is wrong:** The source problem is responsibility mixing across one file, not file size by itself.
- **Suggested solution:** Defer this until a feature forces the split; when you do refactor, cut along existing seams such as `app/usecase/*`, route domains, and per-migration classes rather than by file size alone.
- **Impact:** Changes in those files are more likely to span unrelated code paths and require broader review context.

#### Issue 1.2 — CLI feedback formatting is inconsistent
- **Location:** `MatchingHandler.java`, `ProfileHandler.java`, `SafetyHandler.java`, `MessagingHandler.java`
- **What is wrong:** Handlers use different error template conventions.
- **Why it is wrong:** Output consistency depends on which handler produced the message.
- **Suggested solution:** Centralize CLI feedback formatting in one helper.
- **Impact:** Uniform CLI UX and a single point of change.

#### Issue 1.3 — `RestApiServer` imports too many nested DTOs individually
- **Location:** `RestApiServer.java`
- **What is wrong:** The import section is large enough to become maintenance noise.
- **Why it is wrong:** DTO churn increases merge conflicts and reduces readability.
- **Suggested solution:** Keep the explicit imports for now, but if DTO churn continues split `RestApiDtos` into domain-specific DTO files instead of replacing imports with noisy qualified names inline.
- **Impact:** Shorter imports and clearer ownership.

### 2. Low Complexity

#### Issue 2.1 — Scoring logic is duplicated across matching features
- **Location:** `MatchQualityService.java`, `DefaultBrowseRankingService.java`, `DefaultStandoutService.java`
- **What is wrong:** Composite scoring and weighting are assembled in more than one matching flow even though primitive score inputs already come from shared calculators.
- **Why it is wrong:** The overlap sits in the aggregation layer, so browse, standout, and match-quality behavior can drift even when they share lower-level score components.
- **Suggested solution:** Extract a shared scoring strategy.
- **Impact:** Consistent results and less duplication.

#### Issue 2.2 — Swipe eligibility is validated through too many layers
- **Location:** `MatchingService.java`
- **What is wrong:** `processSwipe()` and `recordLike()` re-check overlapping rules before and inside locked/storage-backed paths.
- **Why it is wrong:** The contract is harder to audit and easier to update partially.
- **Suggested solution:** One explicit validator plus the minimum race-condition re-check inside the critical section.
- **Impact:** Simpler control flow and easier reasoning.

#### Issue 2.3 — Password resolution is too indirect for a core runtime path
- **Location:** `DatabaseManager.java`
- **What is wrong:** `DatabaseManager` resolves passwords through several property, environment, and runtime-specific branches instead of one obvious precedence path.
- **Why it is wrong:** The effective precedence is split across multiple helper methods, so the runtime contract is harder to see at a glance.
- **Suggested solution:** Simplify the existing lookup chain into one small documented precedence method inside `DatabaseManager`, and extract a dedicated resolver only if the logic grows again.
- **Impact:** Clearer password-source precedence would make runtime configuration and fallback behavior easier to reason about.

### 3. Minimal Duplication

#### Issue 3.1 — Index parsing is repeated throughout the CLI layer
- **Location:** `MatchingHandler.java`, `MessagingHandler.java`, `ProfileHandler.java`, `SafetyHandler.java`
- **What is wrong:** The CLI parse-and-bounds-check helper logic is repeated across multiple handlers.
- **Why it is wrong:** Input-validation behavior changes require touching many CLI call sites instead of one helper.
- **Suggested solution:** Add a shared `parseIndex` helper.
- **Impact:** DRY input handling.

#### Issue 3.2 — UUID parsing is duplicated in the REST boundary
- **Location:** `RestApiServer.java`, `RestApiIdentityPolicy.java`
- **What is wrong:** Equivalent `parseUuid` logic exists in multiple places.
- **Why it is wrong:** Validation changes can drift.
- **Suggested solution:** Use one shared UUID parsing seam.
- **Impact:** One contract, fewer subtle differences.

#### Issue 3.3 — Multiple `ObjectMapper` instances duplicate JSON policy ownership
- **Location:** `JdbiMatchmakingStorage.java`, `JdbiConnectionStorage.java`
- **What is wrong:** Notification serialization setup is owned in more than one storage class.
- **Why it is wrong:** Notification JSON policy can drift because the mapping logic is duplicated.
- **Suggested solution:** Move storage-layer JSON mapping behind a small shared codec/helper and reuse a shared mapper there if you want one policy, rather than injecting a global mapper across the whole app.
- **Impact:** A shared helper would centralize notification JSON behavior.

### 4. Single Responsibility

#### Issue 4.1 — `RestApiServer` owns too many API concerns
- **Location:** `RestApiServer.java`
- **What is wrong:** Route registration, parsing, orchestration, and multiple API domains all live together.
- **Why it is wrong:** The class changes for unrelated reasons across several features.
- **Suggested solution:** Keep `RestApiServer` as the transport/bootstrap root, but extract the busiest route groups into small domain collaborators and leave request-guard wiring and top-level exception mapping here.
- **Impact:** Easier testing and smaller diffs.

#### Issue 4.2 — `MigrationRunner` mixes registry, execution, introspection, and DDL helpers
- **Location:** `MigrationRunner.java`
- **What is wrong:** Migration definitions and migration engine behavior are tightly bundled.
- **Why it is wrong:** Reviewing one migration requires traversing a multi-purpose file.
- **Suggested solution:** Extract migration implementations and schema helpers.
- **Impact:** Smaller migration-specific diffs and easier review of schema changes.

#### Issue 4.3 — `TrustSafetyService` spans verification, moderation, and relationship controls
- **Location:** `TrustSafetyService.java`
- **What is wrong:** One service changes for several unrelated business concerns.
- **Why it is wrong:** Verification and moderation/blocking do not share the same invariants.
- **Suggested solution:** Move only the verification-code generation/expiry path into `VerificationUseCases` or a tiny helper if it keeps growing, and keep `TrustSafetyService` focused on moderation, blocking, and relationship-safety orchestration.
- **Impact:** Lower blast radius for changes.

### 5. Clear Boundaries

#### Issue 5.1 — `SafetyViewModel` retains a fallback that bypasses the verification use-case seam
- **Location:** `SafetyViewModel.java`
- **What is wrong:** `SafetyViewModel` still contains a compatibility fallback that performs verification work directly with `TrustSafetyService` when `verificationUseCases` is absent.
- **Why it is wrong:** That fallback weakens the intended UI/application seam in non-canonical construction paths, even though the factory-wired UI path already goes through `VerificationUseCases`.
- **Suggested solution:** Use `VerificationUseCases` in the primary `SafetyViewModel` path and keep any direct `TrustSafetyService` branch only as a temporary compatibility shim until callers are migrated.
- **Impact:** Legacy or ad-hoc callers using the fallback can still mutate verification state outside the normal application boundary.

#### Issue 5.2 — `MatchingViewModel` depends on a nested core utility shape
- **Location:** `MatchingViewModel.java`
- **What is wrong:** It imports `CandidateFinder.GeoUtils.distanceKm` directly.
- **Why it is wrong:** The UI layer depends on an implementation shape instead of a stable seam.
- **Suggested solution:** Route distance calculation/formatting through `LocationService` or a small injected distance-calculator seam instead of importing `CandidateFinder.GeoUtils` from the UI layer.
- **Impact:** Safer refactors.

#### Issue 5.3 — `ChatViewModel` exposes mutable internal observable state
- **Location:** `ChatViewModel.java`
- **What is wrong:** `getConversations()` and `getActiveMessages()` return writable backing lists.
- **Why it is wrong:** External UI code can bypass ViewModel invariants.
- **Suggested solution:** Return read-only observable views.
- **Impact:** Prevents external state corruption.

### 6. Low Coupling

#### Issue 6.1 — `NavigationService` singleton is baked into many controllers
- **Location:** `NavigationService.java` and controller classes
- **What is wrong:** Controllers rely on global navigation state.
- **Why it is wrong:** Hidden dependencies make lifecycle and tests harder.
- **Suggested solution:** Keep `NavigationService` as the shared runtime singleton, but add a narrow navigation seam or overridable accessor for tests instead of pushing full constructor injection through every controller.
- **Impact:** Better testability and clearer ownership.

#### Issue 6.2 — `ServiceRegistry.Builder` exposes an oversized setter surface
- **Location:** `ServiceRegistry.java`
- **What is wrong:** Small dependency changes ripple through a very wide builder API.
- **Why it is wrong:** The composition root is coupled to every subsystem detail.
- **Suggested solution:** Keep `ServiceRegistry` as the single composition root, but if it grows further group builder inputs into small feature-specific records/helpers instead of splitting the registry itself into sub-registries.
- **Impact:** Cleaner composition.

#### Issue 6.3 — Direct `core` imports still create broad coupling outside core
- **Location:** Cross-cutting imports
- **What is wrong:** Some adapters and ViewModels still call core services directly where a use-case seam already exists.
- **Why it is wrong:** That bypasses the repository's preferred application boundary in those spots, even though direct core-type imports are otherwise normal.
- **Suggested solution:** Limit this cleanup to adapters that bypass an existing use-case boundary; direct `core` imports are fine when a class is only consuming shared domain types.
- **Impact:** Cleaner layering over time.

### 7. High Cohesion

#### Issue 7.1 — `User.java` carries too many roles
- **Location:** `User.java`
- **What is wrong:** Entity state, builder hosting, completeness rules, copy behavior, and soft-delete behavior all live together.
- **Why it is wrong:** The class changes for several unrelated reasons.
- **Suggested solution:** Keep `User` as the domain source of truth, but move reusable completeness metadata/rules into collaborators and only extract builder-heavy logic if a concrete storage seam needs it.
- **Impact:** Smaller, more focused entity surface.

#### Issue 7.2 — `RecommendationService` still mixes coordination with compatibility baggage
- **Location:** `RecommendationService.java`
- **What is wrong:** It coordinates leaf services but also carries compatibility-layer result shapes.
- **Why it is wrong:** The abstraction is broader than it needs to be.
- **Suggested solution:** Keep the facade and compatibility records for now; if you trim this area, first isolate the mapping/adapter logic into a small helper instead of retiring public result shapes immediately.
- **Impact:** Clearer API.

#### Issue 7.3 — `User.StorageBuilder` omits `dealbreakers` even though it behaves like a complete builder
- **Location:** `User.java`
- **What is wrong:** One important nested field must be patched outside the builder path.
- **Why it is wrong:** Construction cohesion is broken.
- **Suggested solution:** Add a `dealbreakers(...)` setter.
- **Impact:** Cleaner persistence/load code.

### 8. Consistent Style and Patterns

#### Issue 8.1 — ViewModel construction patterns are inconsistent
- **Location:** `ViewModelFactory.java` and multiple ViewModels
- **What is wrong:** Some use Dependencies records, others use telescoping constructors or direct parameter lists.
- **Why it is wrong:** The UI layer has multiple creation idioms.
- **Suggested solution:** Use `Dependencies` records for the multi-collaborator ViewModels, but keep tiny convenience constructors where they materially improve tests or compatibility.
- **Impact:** More uniform factories and tests.

#### Issue 8.2 — Inline JavaFX styles compete with stylesheet-driven theming
- **Location:** Multiple controller/UI utility files
- **What is wrong:** Theme behavior is split between CSS and code.
- **Why it is wrong:** Inline styling is harder to maintain and audit.
- **Suggested solution:** Move repeated theme constants and reusable control styling into CSS/style classes, but keep one-off computed inline styles when the value is genuinely data-driven at runtime.
- **Impact:** Better theming consistency.

#### Issue 8.3 — `Optional` is sometimes used as control-flow signaling
- **Location:** `RestApiServer.java`
- **What is wrong:** Some helpers use `Optional` mainly to mean “error already handled.”
- **Why it is wrong:** Absence-of-value and error signaling are being mixed.
- **Suggested solution:** Replace the `Optional`-as-side-effect helpers with a small local route-result type or a boolean-returning helper so the control-flow contract is explicit.
- **Impact:** Clearer contracts.

### 9. Good Abstraction Level

#### Issue 9.1 — `ActivityMetricsService` exposes a confusing construction mode split
- **Location:** `ActivityMetricsService.java`
- **What is wrong:** Behavior changes based on constructor and dependency shape.
- **Why it is wrong:** The overloads encode a real difference around whether cleanup can touch `OperationalUserStorage`, but that difference is only lightly documented in source.
- **Suggested solution:** Keep the overloads, but document the 5-arg constructor as the canonical runtime path and the 4-arg variant as the compatibility path that intentionally omits `OperationalUserStorage`.
- **Impact:** Clearer API.

#### Issue 9.2 — `ConnectionService` maps workflow denial reasons through strings
- **Location:** `ConnectionService.java`
- **What is wrong:** Error mapping depends on string reason codes.
- **Why it is wrong:** String drift can silently break behavior.
- **Suggested solution:** Use typed denial reasons.
- **Impact:** Compile-time safety.

### 10. Easy Testability

#### Issue 10.1 — `BaseController.cleanup()` assumes global navigation state exists
- **Location:** `BaseController.java`
- **What is wrong:** Isolated controller tests need singleton app state.
- **Why it is wrong:** Cleanup should not require full application bootstrap.
- **Suggested solution:** Keep the singleton default, but route cleanup/navigation through a protected accessor or narrow seam so controller tests can override it without bootstrapping the whole app.
- **Impact:** Better isolated controller tests.

#### Issue 10.2 — `MatchesViewModel` decides async behavior by probing JavaFX runtime state
- **Location:** `MatchesViewModel.java`
- **What is wrong:** Behavior differs depending on whether tests initialize JavaFX.
- **Why it is wrong:** Test determinism depends on runtime probing.
- **Suggested solution:** Inject an async-policy seam into `MatchesViewModel` and derive execution mode from that seam instead of probing the JavaFX runtime directly.
- **Impact:** Stable tests.

#### Issue 10.3 — `Standout.create()` hardcodes current time lookup
- **Location:** `Standout.java`
- **What is wrong:** Callers cannot supply a timestamp without mutating shared clock state.
- **Why it is wrong:** Explicit time inputs are clearer and easier to test.
- **Suggested solution:** Add a timestamp-accepting overload.
- **Impact:** Deterministic tests.

#### Issue 10.4 — `ChatViewModel.ensureCurrentUser()` caches session state too aggressively
- **Location:** `ChatViewModel.java`
- **What is wrong:** Session changes are not automatically reflected.
- **Why it is wrong:** Ambient session state becomes sticky without an explicit refresh contract.
- **Suggested solution:** Refresh `currentUser` from `AppSession` on initialize and explicit session changes; do not let a cached user outlive the screen/session boundary.
- **Impact:** Better account-switch behavior.

#### Issue 10.5 — `ChatViewModel.setCurrentUser(null)` does not reset visible UI state
- **Location:** `ChatViewModel.java`
- **What is wrong:** Logout/reset can leave stale conversations and messages visible.
- **Why it is wrong:** A null user should imply a real reset.
- **Suggested solution:** Clear UI-observable state and related properties.
- **Impact:** Predictable reset behavior.

### 11. Safe Changeability

#### Issue 11.1 — `MatchingUseCases.Builder.recommendationService()` has hidden side effects
- **Location:** `MatchingUseCases.java`
- **What is wrong:** Builder call order changes runtime behavior.
- **Why it is wrong:** Hidden overwrites are brittle and surprising.
- **Suggested solution:** Only auto-fill unset fields or document precedence clearly.
- **Impact:** More predictable configuration behavior.

#### Issue 11.2 — `ProfileDraftAssembler` manually copies too many fields
- **Location:** `ProfileDraftAssembler.java`
- **What is wrong:** New `User` fields are easy to forget here.
- **Why it is wrong:** Manual bulk-copy code is a drift risk.
- **Suggested solution:** Use a canonical copy/toBuilder path owned by `User`.
- **Impact:** Less silent data omission risk.

#### Issue 11.3 — `ProfileUseCases.getOrComputeStats()` masks errors with fallback behavior
- **Location:** `ProfileUseCases.java`
- **What is wrong:** The original failure semantics are obscured by retry behavior.
- **Why it is wrong:** Silent fallbacks make debugging harder.
- **Suggested solution:** Let the original error surface unless a very specific fallback is documented.
- **Impact:** Clearer failure contracts.

#### Issue 11.4 — `recordLike` and `processSwipe` do not share one concurrency contract
- **Location:** `MatchingService.java`
- **What is wrong:** Similar operations use different in-flight semantics.
- **Why it is wrong:** Concurrency behavior is harder to prove and easier to regress.
- **Suggested solution:** Treat `recordLike` as the storage/undo primitive and `processSwipe` as the higher-level guarded path; document the different lock scopes and back the shared invariant with targeted concurrency tests.
- **Impact:** Safer matching changes.

#### Issue 11.5 — `User.copy()` can lose location state through conflicting builder logic
- **Location:** `User.java`
- **What is wrong:** The copy path rebuilds location state through `StorageBuilder`, so the copied location fields can diverge from the source object.
- **Why it is wrong:** A copy helper should preserve source fields, not re-interpret them through builder normalization.
- **Suggested solution:** Preserve raw lat/lon/flag state without re-triggering conflicting normalization.
- **Impact:** Reliable copy semantics.

#### Issue 11.6 — `DatabaseManager.resetInstance()` resets more than its name promises
- **Location:** `DatabaseManager.java`
- **What is wrong:** `resetInstance()` tears down the singleton and also resets the static JDBC URL to `DEFAULT_JDBC_URL`.
- **Why it is wrong:** Any custom URL has to be re-applied after reset because later initialization reads the default URL again.
- **Suggested solution:** Separate instance reset from URL reset.
- **Impact:** Callers need to treat reset as both singleton teardown and JDBC URL reset.

#### Issue 11.7 — V3 schema cleanup is irreversible
- **Location:** `MigrationRunner.java`
- **What is wrong:** V3 drops several legacy serialized profile columns directly from `users`.
- **Why it is wrong:** Those columns are removed with direct `ALTER TABLE ... DROP COLUMN` statements, and the source does not define a down-migration or archival step for them.
- **Suggested solution:** Prefer staged removal, archival, or explicit rollback planning.
- **Impact:** Running V3 removes those legacy column values from the live schema.

### 12. No Dead or Obsolete Code

#### Issue 12.1 — `MatchingUseCases` exposes a contradictory no-op fallback contract
- **Location:** `MatchingUseCases.java`
- **What is wrong:** `MatchingUseCases` exposes both explicit service setters and wrapper-based no-op fallbacks around `RecommendationService`.
- **Why it is wrong:** The same seam can mean a real dependency or a compatibility fallback depending on how the object is constructed.
- **Suggested solution:** Keep the null-aware wrapper helpers as compatibility shims, but document that the canonical `MatchingUseCases.Builder` still expects fully wired services and that null fallback is not part of the normal runtime contract.
- **Impact:** Clearer service-construction semantics.

#### Issue 12.2 — `CandidateFinder.invalidateCacheFor` / `clearCache` are misleading public no-ops
- **Location:** `CandidateFinder.java`
- **What is wrong:** The API suggests mutable cache invalidation behavior that is not actually present.
- **Why it is wrong:** Readers can infer a cache contract that does not exist.
- **Suggested solution:** Document the freshness-first behavior clearly or narrow/remove the API.
- **Impact:** Fewer false assumptions.

#### Issue 12.3 — `copyForProfileEditing()` adds indirection without value
- **Location:** `ProfileHandler.java`
- **What is wrong:** It is effectively a wrapper around `source.copy()`.
- **Why it is wrong:** Extra indirection increases reading overhead without improving the contract.
- **Suggested solution:** Call `copy()` directly.
- **Impact:** Slightly simpler profile editing flow.

### 13. Clear Contracts and APIs

#### Issue 13.1 — `SocialUseCases` exposes too many constructor modes
- **Location:** `SocialUseCases.java`
- **What is wrong:** Callers have to infer feature availability from construction shape.
- **Why it is wrong:** Constructor shape carries too much behavioral meaning.
- **Suggested solution:** Keep the full constructor as the canonical runtime path, and document or replace the compatibility overloads with named factory methods so the supported modes are explicit.
- **Impact:** More predictable construction semantics.

#### Issue 13.2 — `InteractionStorage.saveLikeAndMaybeCreateMatch()` locks on `this` in an interface default
- **Location:** `InteractionStorage.java`
- **What is wrong:** The default method synchronizes on the storage instance itself (`synchronized (this)`).
- **Why it is wrong:** That only serializes callers sharing the same in-memory instance; it does not provide transactional or cross-instance isolation.
- **Suggested solution:** Keep the default as a compatibility fallback, document it as best-effort intra-instance synchronization, and rely on production storage implementations for real transactional isolation.
- **Impact:** The default path is best-effort intra-instance synchronization, not a production isolation guarantee.

#### Issue 13.3 — `User` mutator contracts are looser than supported profile-edit flows
- **Location:** `User.java`
- **What is wrong:** Key setters allow invalid/incomplete states more easily than the use-case/service paths do.
- **Why it is wrong:** Direct mutation can silently make a valid profile invalid.
- **Suggested solution:** Enforce the same invariants in setters or clearly mark them as low-level hooks.
- **Impact:** Better domain integrity.

#### Issue 13.4 — `archiveMatch()` name implies archival, but the current implementation deletes
- **Location:** `MatchingUseCases.java`
- **What is wrong:** The method name says archive, but the current implementation and tests treat it as removal.
- **Why it is wrong:** The source proves a naming mismatch here, not a retained-history bug.
- **Suggested solution:** Use the existing `Match` terminal-state/archive semantics for user-facing archive behavior and reserve hard delete for true data removal; rename to `deleteMatch` only if permanent deletion is the intended product behavior.
- **Impact:** Callers and tests can make false assumptions about whether an archived match remains queryable.

#### Issue 13.5 — `getDailyStatus()` bypasses the builder seam in mixed configurations
- **Location:** `MatchingUseCases.java`
- **What is wrong:** It consults `recommendationService` directly instead of the overridable daily-limit seam.
- **Why it is wrong:** Mixed custom configurations can diverge.
- **Suggested solution:** Route through `dailyLimitService` or explicitly reject mixed configurations.
- **Impact:** More consistent custom/test setups.

#### Issue 13.6 — `configurePoolSettings()` looks dynamic but does not affect the live pool
- **Location:** `DatabaseManager.java`
- **What is wrong:** The API suggests runtime tuning that does not actually happen.
- **Why it is wrong:** Callers can think they changed live runtime behavior when they did not.
- **Suggested solution:** Treat `configurePoolSettings()` as startup-only configuration for the next pool instance, and throw or document clearly if it is called after the live pool has already been created.
- **Impact:** The setting only affects the next pool construction; it does not retune an already-open pool.

### 14. Good Dependency Hygiene

#### Issue 14.1 — Dialect detection is repeated across JDBI storage implementations
- **Location:** `JdbiUserStorage.java`, `JdbiMatchmakingStorage.java`, `JdbiMetricsStorage.java`
- **What is wrong:** Runtime dialect knowledge is derived multiple times.
- **Why it is wrong:** This duplicates work and spreads responsibility.
- **Suggested solution:** Pass the resolved dialect from composition/wiring.
- **Impact:** Passing the resolved dialect once would remove repeated metadata lookups during storage construction.

#### Issue 14.2 — `DatabaseManager` still mixes static-global and instance responsibilities
- **Location:** `DatabaseManager.java`
- **What is wrong:** Ownership and lifecycle are harder to reason about.
- **Why it is wrong:** Static mutable runtime state is fragile in tests and complex lifecycles.
- **Suggested solution:** Keep the static helpers only as compatibility/test hooks, and keep `StorageFactory`/`ServiceRegistry` as the canonical production path that configures the live `DatabaseManager` instance.
- **Impact:** Better isolation.

#### Issue 14.3 — `GeoUtils` is nested even though it behaves like an independent utility
- **Location:** `CandidateFinder.java`
- **What is wrong:** `CandidateFinder` exposes `GeoUtils` as a public nested utility even though it behaves like a standalone helper.
- **Why it is wrong:** Call sites couple to `CandidateFinder.GeoUtils` even though the helper methods are not candidate-finder-specific.
- **Suggested solution:** Promote it to a top-level matching utility.
- **Impact:** Cleaner imports.

### 15. Good Discoverability

#### Issue 15.1 — `RestApiDtos` is too broad to navigate comfortably
- **Location:** `RestApiDtos.java`
- **What is wrong:** DTOs for many domains live together.
- **Why it is wrong:** Finding one DTO requires scanning a mixed-purpose file.
- **Suggested solution:** Split DTOs by feature/domain.
- **Impact:** Faster navigation.

#### Issue 15.2 — `CliTextAndInput` bundles too many CLI concerns
- **Location:** `CliTextAndInput.java`
- **What is wrong:** Constants, validation, menus, prompts, and nested helpers all live together.
- **Why it is wrong:** The file is both a text catalog and a behavior helper.
- **Suggested solution:** Keep `CliTextAndInput` as the umbrella CLI helper for now, but extract behavior-heavy nested pieces like menus and readers if the file continues to grow.
- **Impact:** Better CLI discoverability.

### 16. Useful Documentation Where Needed

#### Issue 16.1 — `ActivityMetricsService` constructor Javadoc is misleading
- **Location:** `ActivityMetricsService.java`
- **What is wrong:** The constructor Javadoc labels multiple overloads as if they were the single canonical path.
- **Why it is wrong:** That documentation hides the fact that the 4-arg and 5-arg overloads intentionally support different runtime and compatibility modes.
- **Suggested solution:** Document the 5-arg constructor as the canonical runtime path and the 4-arg overload as the compatibility/test constructor.
- **Impact:** Maintainers can misread which constructor is the normal production path.

#### Issue 16.2 — Weight-validation errors do not describe the accepted tolerance
- **Location:** `AppConfigValidator.java`
- **What is wrong:** The runtime message is less informative than the actual rule.
- **Why it is wrong:** Developers have to read source to understand valid near-edge input.
- **Suggested solution:** Include the tolerance in the message.
- **Impact:** Faster config debugging.

#### Issue 16.3 — `deleteAccount()` side effects are under-documented
- **Location:** `ProfileMutationUseCases.java`
- **What is wrong:** The public `deleteAccount()` contract does not state that it persists a deleted copy and then mutates the caller-visible `User` on success.
- **Why it is wrong:** That side effect is explained only in an inline implementation comment, not in the method contract.
- **Suggested solution:** Document that `deleteAccount()` persists a soft-deleted copy and then mutates the caller-visible `User`; keep the current result shape instead of returning the mutated object.
- **Impact:** Callers can assume the input object remains unchanged even though it is deliberately marked deleted after persistence.

#### Issue 16.4 — `DatabaseManager` Javadoc still reads as H2-only
- **Location:** `DatabaseManager.java`
- **What is wrong:** The documentation understates the class’s real runtime role.
- **Why it is wrong:** Storage/runtime maintainers can form the wrong mental model.
- **Suggested solution:** Update the Javadoc to describe shared runtime database management across supported dialects.
- **Impact:** Less confusion during storage work.

#### Issue 16.5 — Match/conversation ID length is encoded as a magic number
- **Location:** `MigrationRunner.java`, `SchemaInitializer.java`, `JdbiMatchmakingStorage.java`
- **What is wrong:** The constraint exists, but its meaning is buried in a bare number.
- **Why it is wrong:** Changes require hunting multiple literals.
- **Suggested solution:** Define and reuse one shared pair-ID length constant (for example `PAIR_ID_LENGTH`) across match, conversation, and related ID-length checks.
- **Impact:** Easier maintenance of pair-ID constraints.

### 17. Predictable Error Handling

#### Issue 17.1 — `MessagingUseCases.sendMessage()` treats event publication as best-effort after persistence
- **Location:** `MessagingUseCases.java`
- **What is wrong:** Event/data consistency semantics vary across use-case flows.
- **Why it is wrong:** Side-effect timing is harder to reason about.
- **Suggested solution:** Document and consistently follow the current repo rule: persistence determines success, and post-persistence event publication is best-effort with warning logs.
- **Impact:** More predictable downstream behavior.

#### Issue 17.2 — `ChatViewModel.reportSendFailure()` relies on external wiring for visibility
- **Location:** `ChatViewModel.java`
- **What is wrong:** Without an error sink, failures are mostly just logged.
- **Why it is wrong:** UI failure visibility should not be accidental.
- **Suggested solution:** Maintain an explicit visible error state in the ViewModel.
- **Impact:** More reliable chat UX under failure.

#### Issue 17.3 — Rate limiting relies on a non-monotonic clock source
- **Location:** `RestApiRequestGuards.java`
- **What is wrong:** Time jumps can skew enforcement behavior.
- **Why it is wrong:** Rate limiters are safer with a dedicated time source.
- **Suggested solution:** Use a monotonic source for rate-limit window math — for example `System.nanoTime()` or a tiny injectable ticker — and keep `AppClock` for user-facing timestamps only.
- **Impact:** More reliable throttling behavior.

#### Issue 17.4 — `handleUseCaseFailure()` has no explicit future `RATE_LIMITED` mapping
- **Location:** `RestApiServer.java`
- **What is wrong:** There is no current `RATE_LIMITED` use-case error code to map in `handleUseCaseFailure()`.
- **Why it is wrong:** The current throttle path is handled earlier by `RestApiRequestGuards.ApiTooManyRequestsException`, not by the use-case error mapper.
- **Suggested solution:** Keep throttling in `RestApiRequestGuards` for now; only add a `RATE_LIMITED` use-case error and a `handleUseCaseFailure()` mapping if the use-case layer actually starts emitting that code.
- **Impact:** This is a future extensibility gap only if the use-case layer later adds a throttle-specific error code.

#### Issue 17.5 — Profile-note save token handling in `ChatViewModel` is fragile
- **Location:** `ChatViewModel.java`
- **What is wrong:** The profile-note save path increments `noteLoadToken` and only applies the UI update if that exact token is still current when the callback runs.
- **Why it is wrong:** That stale-update guard is intentional, but it also lets a newer note load, save, or delete suppress a just-completed save confirmation.
- **Suggested solution:** Capture the initiating token before async work and validate against that token.
- **Impact:** A user can see stale note UI even after a save succeeded if another note action races it.

#### Issue 17.6 — `ChatViewModel.sendMessage()` boolean return can be mistaken for completion
- **Location:** `ChatViewModel.java`
- **What is wrong:** The `boolean` return from `ChatViewModel.sendMessage()` means "accepted and queued for async send," not "persisted or delivered."
- **Why it is wrong:** Real completion is reported later through `handleSendResult()` and the optional `onSuccess` callback.
- **Suggested solution:** Rename or document the current boolean as “accepted for async send”, and use the callback/error sink for real completion; if callers need definitive success, return a completion-aware type instead.
- **Impact:** Callers should not treat the return value as delivery confirmation.

#### Issue 17.7 — Swipe side effects are inconsistent across related flows
- **Location:** `MatchingService.java`, `MatchingUseCases.java`
- **What is wrong:** Undo tracking, event publication, and daily-pick state changes are split across `MatchingService`, `MatchingUseCases`, and follow-up callers.
- **Why it is wrong:** The same swipe flow mutates follow-up state in different layers, which makes sequencing harder to audit from source.
- **Suggested solution:** Define one side-effect sequencing rule for like/swipe flows.
- **Impact:** Easier reasoning and better tests.

#### Issue 17.8 — `loadMessagesInBackground()` can fail without a visible UI failure state
- **Location:** `ChatViewModel.java`
- **What is wrong:** The user can see “stuck” chat behavior rather than an explicit failure state.
- **Why it is wrong:** Background refresh failures should be represented explicitly.
- **Suggested solution:** Expose an error/retry state in the ViewModel.
- **Impact:** Better recoverability.

#### Issue 17.9 — `User.markVerified()` drops part of the verification timeline
- **Location:** `User.java`
- **What is wrong:** `User.markVerified()` clears `verificationSentAt`, so the model retains only the completion timestamp.
- **Why it is wrong:** That collapses the verification timeline to one instant instead of preserving both send and confirm times.
- **Suggested solution:** Preserve the original send timestamp or move verification history into an audit seam.
- **Impact:** Better traceability.

#### Issue 17.10 — Startup schema migration has weaker timeout and atomicity guarantees than the normal runtime path
- **Location:** `DatabaseManager.java`, `MigrationRunner.java`
- **What is wrong:** Startup schema initialization runs synchronously before `DatabaseManager.getConnection()` returns, and that bootstrap path does not apply `applySessionQueryTimeout(...)` before calling `MigrationRunner.runAllPending(...)`.
- **Why it is wrong:** Migrations are applied sequentially and versions are recorded individually without an explicit bootstrap transaction contract, so timeout and atomicity guarantees are weaker than the normal runtime path.
- **Suggested solution:** Run schema bootstrap through the same session-setup path used for normal connections, and execute migrations inside explicit transaction or auto-commit boundaries when the dialect supports it so startup either fully succeeds or clearly fails.
- **Impact:** A clearer bootstrap contract would make startup behavior and recovery boundaries easier to reason about.

### 18. Reasonable Performance and Resource Use

#### Issue 18.1 — Default `findCandidates()` fallback loads active users into memory
- **Location:** `UserStorage.java`
- **What is wrong:** The interface default for `UserStorage.findCandidates(...)` loads active users in memory and filters them on the JVM.
- **Why it is wrong:** That fallback would scale poorly if a runtime adapter inherited it, even though the current factory-wired `JdbiUserStorage` overrides it with a database-backed query.
- **Suggested solution:** Keep the default implementation as a dev/test fallback, but require every production-grade storage adapter to override it and verify that override in adapter tests.
- **Impact:** This is mainly a guardrail issue for future storage adapters, not a proven problem in the current runtime path.

#### Issue 18.2 — `saveStandouts()` uses repeated writes instead of batching
- **Location:** `JdbiMetricsStorage.java`
- **What is wrong:** It creates more database round trips than necessary.
- **Why it is wrong:** Bulk writes should use database batching.
- **Suggested solution:** Use JDBI batch APIs.
- **Impact:** Fewer database round trips during standout persistence.

#### Issue 18.3 — Hover animations allocate new transition objects per event
- **Location:** `MatchesController.java`
- **What is wrong:** Rapid hovering creates avoidable animation allocations.
- **Why it is wrong:** The UI hot path pays unnecessary allocation/GC cost.
- **Suggested solution:** Reuse a prepared animation instance or switch to a simple style/pseudo-class change; do not allocate a new `ScaleTransition` on every hover event.
- **Impact:** Smoother interactions.

#### Issue 18.4 — `DefaultCompatibilityCalculator` repeats config and age lookups unnecessarily
- **Location:** `DefaultCompatibilityCalculator.java`
- **What is wrong:** The method does repeated work inside one calculation.
- **Why it is wrong:** Small overhead accumulates in ranking/scoring paths.
- **Suggested solution:** Cache intermediate values locally.
- **Impact:** Cleaner code and lower overhead.

#### Issue 18.5 — `JdbiTypeCodecs` allocates a new UTC calendar per row read
- **Location:** `JdbiTypeCodecs.java`
- **What is wrong:** High-throughput row reading does avoidable allocation work.
- **Why it is wrong:** The calendar configuration is effectively constant.
- **Suggested solution:** Avoid creating a fresh UTC calendar per row; if the JDBC driver still needs one, use a thread-local or other safe reusable UTC calendar instead.
- **Impact:** Lower allocation pressure.

#### Issue 18.6 — The `ChatViewModel` polling path does duplicate UI and diff work
- **Location:** `ChatViewModel.java`
- **What is wrong:** Message and presence updates are dispatched separately, and message equality checks do a full $O(n)$ comparison each poll.
- **Why it is wrong:** Chat refresh overhead scales poorly with conversation size and update frequency.
- **Suggested solution:** Batch UI updates and compare cheaper summary signals before full list comparisons.
- **Impact:** Lower chat refresh cost.

#### Issue 18.7 — `processSwipe()` keeps the user lock longer than necessary
- **Location:** `MatchingService.java`
- **What is wrong:** Read-mostly checks lengthen lock duration.
- **Why it is wrong:** Longer critical sections increase contention under load.
- **Suggested solution:** Move purely read-only guards before `executeWithUserLock`, but keep persisted revalidation and writes inside the lock so the race-safety contract stays intact.
- **Impact:** Better throughput and lower contention.

---

## Overall Priorities

1. **Decompose God Classes** — `ProfileHandler`, `ProfileController`, `ProfileViewModel`, `MigrationRunner`, `RestApiServer`, `MatchingUseCases`, and `MatchingHandler` remain the largest maintainability bottlenecks.
2. **Unify Matching and Recommendation Scoring** — Extract a shared scoring strategy so the same candidate is scored consistently everywhere.
3. **Tighten `ChatViewModel` Async Contracts** — The profile-note token flow, async send contract, mutable list exposure, and silent background-load path are the clearest user-facing correctness risks in the UI layer.
4. **Fix the Archive/Delete Semantic Mismatch** — `archiveMatch()` currently behaves like deletion, which is a contract and caller-expectation problem.
5. **Align Swipe Concurrency and Side-Effect Rules** — `recordLike`, `processSwipe`, undo tracking, event publication, and daily-pick state changes need one coherent contract.
6. **Add Deterministic Migration Safety** — Startup migrations need query-timeout coverage, transactional boundaries, and a safer posture toward irreversible schema changes.
7. **Standardize ViewModel Construction** — Move remaining telescoping constructors to the same Dependencies-record pattern used elsewhere.
8. **Move Inline CSS to Stylesheets** — Inline styling still scatters theme logic across a dozen-plus JavaFX files.
9. **Strengthen `User` Mutation and Copy Contracts** — Public setters and `copy()` should preserve or enforce the same invariants the supported use-case flows rely on.
10. **Extract Shared CLI and Utility Seams** — Index parsing, UUID parsing, error-formatting templates, and shared ObjectMapper configuration remain easy wins.

## Notes

- All findings are grounded in actual source code evidence from `src/main/java`. No README, plans, or secondary docs were used as evidence.
- Test files were excluded from the evidence set for this review.
- AST-grep was used to confirm duplication, singleton usage, synchronization patterns, and style hotspots.
- The codebase demonstrates strong use of records, sealed interfaces, pattern matching, virtual threads, and a generally sound layered architecture.
- The issues here are debt-paydown and contract-cleanup opportunities, not evidence of a broken overall architecture.
- This file should remain a single canonical review. Future corrections belong in the relevant section, not in an append-only errata block.
