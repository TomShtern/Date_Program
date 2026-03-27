# Final-Final Implementation Plan: Findings #5, #6, #7, #8, #9, #10, #12, #13, #14, #15, #16, #17, #18

## EXECUTION STATUS (2026-02-23)
- ✅ **PHASE 1 COMPLETED** — `InteractionStorage` contract hardened, atomic default write path synchronized, implementations updated.
- ✅ **PHASE 2 COMPLETED** — `SwipeGateResult` rename done, `MatchingService` finalized, pending-liker optimization applied.
- ✅ **PHASE 3 COMPLETED** — deprecated `CandidateFinder` constructor removed, missing-location candidate gate + CLI UX flow implemented.
- ✅ **PHASE 4 COMPLETED** — untyped deprecated navigation-context methods removed.
- ✅ **PHASE 5 COMPLETED** — `Conversation` hardened as final mutable aggregate with non-public ctor and `fromStorage(...)` factory.
- ✅ **PHASE 6 COMPLETED** — REST conversations N+1 fixed via batch message-count API + JDBI grouped query path.
- ✅ **PHASE 7 COMPLETED** — centralized `MainMenuRegistry` added and wired into `Main` dispatch/render/guard flow.
- ✅ **TEST PLAN COMPLETED** — all listed tests updated/added and passing.
- ✅ **QUALITY GATES COMPLETED** — `spotless:apply` and `mvn verify` passed.

## Summary
This plan is revalidated for simplicity, correctness, and project fit.
Key final decisions:
1. `Conversation` will **not** be converted to a `record` in this batch; it is a mutable aggregate in this codebase and converting now would be high-churn with low payoff.
2. `ConversationPreview` will **not** gain `messageCount`; REST N+1 is solved via a focused batch-count path so CLI preview model stays lean.
3. `InteractionStorage` count defaults will be removed (abstract contract) to prevent silent performance regressions.
4. Deprecated/untyped APIs will be removed directly where unused.
5. Menu hardcoding will be replaced by a registry; one new file is justified for clarity/testability and to keep `Main` smaller.

## Why These Are the Best Fit
1. `Conversation` as `record` is not idiomatic here because records are immutable data carriers; this class has domain mutations (`read/archive/visibility/last-message`) and current services/storages rely on that behavior.
2. Extending `ConversationPreview` with `messageCount` would force extra data into CLI-oriented previews and add needless coupling; batch counting in REST path is simpler and preserves current model boundaries.
3. Interface-level contract hardening in `InteractionStorage` is best practice when a default can hide expensive behavior.
4. Removing dead deprecated APIs now is cleaner than staged deprecation in a non-live internal codebase.
5. A dedicated menu registry file is justified because it centralizes numbering, guard rules, rendering, and dispatch and makes behavior testable.

## Parallel Workstreams
1. **Agent A: Storage Contract Hardening** for `#10 #12 #14`.
2. **Agent B: Matching/Metrics Types and Guards** for `#7 #8 #15` plus consumption of Agent A APIs.
3. **Agent C: Candidate Discovery + UX Gate** for `#9 #13`.
4. **Agent D: Conversation Model + Mapper Safety** for `#5 #18`.
5. **Agent E: Navigation + Menu Registry** for `#6 #16`.
6. **Agent F: REST Conversation Count N+1** for `#17` using Agent B/D outputs as needed.
7. **Integrator** merges all branches, resolves conflicts, runs format/check/test/verify once.

## Implementation Steps

### Phase 1: InteractionStorage hardening (`#10 #12 #14`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/core/storage/InteractionStorage.java`.
2. ✅ Convert `countMatchesFor(UUID)` and `countActiveMatchesFor(UUID)` from default to abstract methods.
3. ✅ Add `getMatchedCounterpartIds(UUID userId)` as a new method with a safe default fallback that derives IDs from `getAllMatchesFor`.
4. ✅ Wrap default `saveLikeAndMaybeCreateMatch(Like)` in `synchronized (this)` to make check-then-act atomic for non-transactional implementations.
5. ✅ Update implementations:
`src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`, `src/test/java/datingapp/core/testutil/TestStorages.java`, `src/test/java/datingapp/core/LikerBrowserServiceTest.java`.
6. ✅ In JDBI impl, override `getMatchedCounterpartIds(UUID)` with UUID-only SQL projection so pending-liker filtering does not hydrate full `Match` rows.

### Phase 2: Matching and metrics (`#7 #8 #12 #15`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`.
2. ✅ Rename nested type `SwipeResult` to `SwipeGateResult`; update factory methods and return type of `recordSwipe`.
3. ✅ Edit `src/main/java/datingapp/core/matching/MatchingService.java`.
4. ✅ Mark class `final`.
5. ✅ Move `Objects.requireNonNull(currentUser/candidate)` to top of `processSwipe`.
6. ✅ Replace pending-liker exclusion match loop with `interactionStorage.getMatchedCounterpartIds(currentUserId)` while preserving existing semantics (ended matches remain excluded).
7. ✅ Keep builder validation simple (no redundant checks added).

### Phase 3: Candidate discovery and missing-location flow (`#9 #13`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/core/matching/CandidateFinder.java`.
2. ✅ Remove deprecated 3-arg constructor.
3. ✅ In `findCandidatesForUser(User)`, if seeker has no location set, return empty immediately.
4. ✅ Implement chosen UX behavior in entry flow: block + warning + guidance + prompted redirect.
5. ✅ On Yes redirect target, call existing profile flow (`completeProfile`) as selected.
6. ✅ Place this gate where menu action for browse is dispatched so behavior is explicit and user-facing.

### Phase 4: Navigation cleanup (`#6`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/ui/NavigationService.java`.
2. ✅ Remove untyped deprecated methods:
`setNavigationContext(Object)` and `consumeNavigationContext()`.
3. ✅ Keep typed API only:
`setNavigationContext(ViewType, Object)` and `consumeNavigationContext(ViewType, Class<T>)`.

### Phase 5: Conversation construction safety (`#5 #18`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/core/connection/ConnectionModels.java`.
2. ✅ Keep `Conversation` as mutable class; declare it `final`.
3. ✅ Reduce raw constructor visibility from `public` to non-public.
4. ✅ Add `Conversation.fromStorage(...)` static factory for persistence rehydration.
5. ✅ Keep `Conversation.create(UUID, UUID)` as public normalized construction path.
6. ✅ Add concise Javadoc clarifying mutable aggregate intent and ID-based equality semantics.
7. ✅ Edit `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` mapper to use `Conversation.fromStorage(...)`.

### Phase 6: REST conversation count N+1 fix (`#17`) — ✅ **COMPLETED**
1. ✅ Edit `src/main/java/datingapp/core/storage/CommunicationStorage.java`.
2. ✅ Add `countMessagesByConversationIds(Set<String>)` with default fallback loop over existing `countMessages`.
3. ✅ Override in `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java` using one grouped `COUNT(*)` query.
4. ✅ Add delegating method in `src/main/java/datingapp/core/connection/ConnectionService.java`.
5. ✅ Edit `src/main/java/datingapp/app/api/RestApiServer.java`.
6. ✅ In `getConversations`, collect conversation IDs, batch fetch counts once, then map summaries with count lookup.
7. ✅ Remove per-item `countMessages(conversationId)` call path.

### Phase 7: Menu registry refactor (`#16`) — ✅ **COMPLETED**
1. ✅ Add one new file:
`src/main/java/datingapp/app/cli/MainMenuRegistry.java`.
2. ✅ Define immutable menu option model with:
option key, display label provider, requires-login flag, and action.
3. ✅ Build ordered registry in one place and expose lookup/render helpers.
4. ✅ Update `src/main/java/datingapp/Main.java` to:
render from registry, validate selection from registry, enforce login guard from registry, dispatch actions from registry.
5. ✅ Keep option numbers and current behavior unchanged.

## Important Public API / Interface / Type Changes
1. `InteractionStorage` now requires explicit implementations of `countMatchesFor` and `countActiveMatchesFor`.
2. `InteractionStorage` adds `getMatchedCounterpartIds(UUID)`.
3. `InteractionStorage` default `saveLikeAndMaybeCreateMatch` becomes synchronized.
4. `ActivityMetricsService.SwipeResult` renamed to `ActivityMetricsService.SwipeGateResult`.
5. `CandidateFinder` deprecated constructor removed.
6. `NavigationService` untyped context methods removed.
7. `Conversation` raw constructor no longer public; `fromStorage(...)` added.
8. `CommunicationStorage` adds `countMessagesByConversationIds(Set<String>)`.

## Test Plan
1. ✅ Update `src/test/java/datingapp/core/SessionServiceTest.java` for `SwipeGateResult`.
2. ✅ Update `src/test/java/datingapp/core/MatchingServiceTest.java` to assert null-check order behavior and pending-liker behavior parity.
3. ✅ Update `src/test/java/datingapp/core/LikerBrowserServiceTest.java` in-memory storage for new abstract methods and counterpart-id method.
4. ✅ Add candidate test in `src/test/java/datingapp/core/CandidateFinderTest.java` for missing seeker location in `findCandidatesForUser`.
5. ✅ Keep `src/test/java/datingapp/ui/NavigationServiceContextTest.java` green after untyped API removal.
6. ✅ Update/add messaging tests in `src/test/java/datingapp/core/MessagingServiceTest.java` for batch conversation counts.
7. ✅ Add menu registry tests in `src/test/java/datingapp/app/cli/MainMenuRegistryTest.java` for numbering, guard flags, dispatch mapping, and dynamic unread label behavior.
8. ✅ Add concurrency regression test for default `saveLikeAndMaybeCreateMatch` atomicity path in core tests using non-transactional storage.
9. ✅ Add API-layer regression test in `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java` for batch count path usage.

## Quality Gates
1. ✅ Run targeted tests per changed area first.
2. ✅ Run `mvn spotless:apply`.
3. ✅ Run `mvn verify` once and inspect output for build status, tests summary, and violations.

## Explicit Assumptions and Defaults
1. Clean API breaks are acceptable in this stage of the project.
2. Pending-liker behavior remains unchanged: users already matched before (including ended matches) remain excluded.
3. `Conversation` remains mutable by design for now; immutable record migration is deferred intentionally.
4. Only one new production file is added (`MainMenuRegistry`) because it has strong cohesion and testability value.
5. Missing-location browse handling is: block + warning + guidance + prompt + optional redirect to profile flow.
