# 2026-03-29 Abstraction Consistency Report

> Purpose: document the places where this repository already has the **right abstraction**, but does **not use it consistently across layers or call sites**.
>
> Intended use: this file is the source document for a future implementation plan that standardizes those abstractions.
>
> Scope: `src/main/java` and `src/test/java` only. This document intentionally excludes many valid correctness/performance findings when they are **not primarily abstraction-consistency problems**.

## Core conclusion

The project usually has the correct building block already:

- a real composition root (`ServiceRegistry`, `ViewModelFactory`)
- a real application boundary (`*UseCases`, `UseCaseResult`, typed command/query records)
- a real event abstraction (`AppEventBus`, `InProcessAppEventBus`)
- real domain helpers for invariants (`generateId(...)`, `User.copy()`, `CommunicationStorage.saveMessageAndUpdateConversationLastMessageAt(...)`, `ValidationService`, `EnumSetUtil`)
- real UI lifecycle/async abstractions (`BaseViewModel`, `ViewModelAsyncScope`, `UiThreadDispatcher`, `ViewModelErrorSink`, `BaseController`)
- real shared test helpers (`JavaFxTestSupport`, `TestStorages`, `ServiceRegistry.builder()`)

The main architecture problem is **partial adoption**.

The codebase often introduces the right abstraction, proves it is useful, and then keeps several older or convenience paths alive. That creates:

- more than one construction path for the same object graph
- more than one way to validate or mutate the same concept
- more than one lifecycle style in the UI
- more than one way for adapters to cross into core logic
- more than one test harness for the same kind of test

That is the real source of “the repo knows what good looks like, but not every layer follows it yet.”

## The canonical abstractions to treat as source-of-truth

These are the abstractions that are already correct enough to standardize around.

1. **Composition roots**
   - `src/main/java/datingapp/core/ServiceRegistry.java`
   - `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
   - `fromServices(...)` adapter factories such as `ProfileHandler.fromServices(...)`

2. **Application boundary**
   - `src/main/java/datingapp/app/usecase/**`
   - `src/main/java/datingapp/app/usecase/common/UseCaseResult.java`
   - typed command/query/result records already present in the use-case layer

3. **Event boundary**
   - `src/main/java/datingapp/app/event/AppEventBus.java`
   - `src/main/java/datingapp/app/event/InProcessAppEventBus.java`

4. **Domain/helper reuse**
   - `src/main/java/datingapp/core/model/Match.java#generateId(...)`
   - `src/main/java/datingapp/core/connection/ConnectionModels.java#Conversation.generateId(...)`
   - `src/main/java/datingapp/core/model/User.java#copy()`
   - `src/main/java/datingapp/core/profile/MatchPreferences.java#Dealbreakers.toBuilder()`
   - `src/main/java/datingapp/core/storage/CommunicationStorage.java#saveMessageAndUpdateConversationLastMessageAt(...)`
   - `src/main/java/datingapp/core/profile/ValidationService.java`
   - `src/main/java/datingapp/core/EnumSetUtil.java`

5. **UI lifecycle and async**
   - `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java`
   - `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java`
   - `src/main/java/datingapp/ui/async/UiThreadDispatcher.java`
   - `src/main/java/datingapp/ui/viewmodel/ViewModelErrorSink.java`
   - `src/main/java/datingapp/ui/screen/BaseController.java`

6. **Test harness**
   - `src/test/java/datingapp/ui/JavaFxTestSupport.java`
   - `src/test/java/datingapp/core/testutil/TestStorages.java`
   - `ServiceRegistry.builder()` as the production-like graph builder for integration-style tests

## Verified abstraction-drift catalog

## 1. Composition roots exist, but construction still happens in too many places

### Best abstraction

Use **one production construction path per subsystem**:

- `ServiceRegistry` for app/core service graphs
- `ViewModelFactory` for UI composition
- `fromServices(...)` only at adapter edges
- validated builders / constructors that fail fast on missing required collaborators

Direct construction should be reserved for:

- narrowly scoped unit tests
- explicit test fixtures
- truly local value objects

### Verified drift sites

- `src/main/java/datingapp/core/ServiceRegistry.java:69-136`
  - This is the strongest existing composition root. It requires non-null core collaborators and wires `MessagingUseCases`, `MatchingUseCases`, `ProfileUseCases`, and `SocialUseCases` centrally.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:51-115, 145-149`
  - `ProfileUseCases` has a builder, but also public constructor paths that allow nullable collaborators and only fail later inside methods such as `saveProfile(...)` and `updateProfile(...)`.

- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:43-58`
  - `SocialUseCases` exposes partially configured constructor shapes, including a `TrustSafetyService`-only constructor.

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:38-53, 125-160, 568-645`
  - `MatchingUseCases` already has a builder, but still allows null-to-no-op substitution for `AppEventBus`, `DailyLimitService`, `DailyPickService`, and `StandoutService` when routing through `recommendationService(...)` wrappers.

- `src/main/java/datingapp/app/cli/ProfileHandler.java:91-114`
  - `ProfileHandler.fromServices(...)` correctly injects `services.getLocationService()`.
  - The public convenience constructor still does `new LocationService(validationService)`.

- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java:77, 166-170, 257-276`
  - `ViewModelFactory` is clearly intended to be the UI composition root.
  - It even contains a fallback disposal path for ViewModels “not yet migrated to `BaseViewModel`”, which is direct evidence that construction/lifecycle paths are still split.

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:59-202, 695`
  - Multiple public constructors create `new ValidationService(config)`, `new LocationService(new ValidationService(config))`, and `new JavaFxUiThreadDispatcher()` internally.
  - `getValidationService()` also recreates a validator instead of exposing a shared injected instance.

- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java:35-76`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:148, 162`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:115, 204`
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java:75`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java:88, 104, 121`
- `src/main/java/datingapp/ui/viewmodel/StandoutsViewModel.java:74, 79`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java:47`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java:54`
- `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java:49`
- `src/main/java/datingapp/ui/viewmodel/ProfileReadOnlyViewModel.java:33`
- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:84-97`
  - Many ViewModels still expose public convenience constructors that self-create `JavaFxUiThreadDispatcher`, preference stores, or service-derived dependencies.
  - `DashboardViewModel.Dependencies.fromServices(...)` is effectively a mini-composition root living inside the ViewModel package instead of in `ViewModelFactory`.

### Why this matters

When construction is split across builders, public convenience constructors, and hidden self-composition:

- missing dependencies are discovered late
- production wiring and test wiring drift apart
- singleton/shared services get recreated casually
- future refactors must update too many construction surfaces

### Recommended fix

- Make **validated builders / fully-required constructors** the only production path.
- Keep convenience constructors only when they are clearly **test-only**; otherwise make them package-private or remove them.
- Keep all UI assembly in `ViewModelFactory`.
- Keep all app/core assembly in `ServiceRegistry`.
- Treat any class that calls `new JavaFxUiThreadDispatcher()`, `new ValidationService(...)`, or `new LocationService(...)` internally as a candidate for constructor-surface cleanup.

## 2. The use-case boundary is real, but some adapters still bypass it

### Best abstraction

For user-facing adapter logic, the canonical boundary is:

- adapter → `*UseCases` → core services/storage

Adapters should not usually reach directly into `CandidateFinder`, `RecommendationService`, `ActivityMetricsService`, or similar services for business-facing orchestration. If a read path is intentionally lighter-weight than an existing use case, that exception should still be isolated in a dedicated read-model helper rather than in the adapter itself.

### Verified drift sites

- `src/main/java/datingapp/app/api/RestApiServer.java:316-324`
  - `browseCandidates(...)` already uses `matchingUseCases.browseCandidates(...)`.

- `src/main/java/datingapp/app/api/RestApiServer.java:1028-1031`
  - `readCandidateSummaries(...)` is still a deliberate direct-read exception that calls `candidateFinder.findCandidatesForUser(user)`.

- `src/main/java/datingapp/app/cli/StatsHandler.java:42, 61, 86`
  - `StatsHandler` is a good example of the desired pattern: it uses `ProfileUseCases` query APIs.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:309, 320, 437, 455, 475, 516`
  - The application layer already exposes profile-oriented read/write APIs for listing users, loading users, and managing profile notes.

- `src/main/java/datingapp/app/cli/ProfileHandler.java:979, 1017, 1029, 1042, 1069, 1165`
  - `ProfileHandler` still reads note and user data directly from `userStorage` even though overlapping `ProfileUseCases` APIs already exist.
  - The same handler uses `profileUseCases` for note writes at `1088, 1113, 1125`, so one feature area is split across raw storage and app-layer use cases.

- `src/main/java/datingapp/app/cli/SafetyHandler.java:216, 228, 346, 361`
  - `SafetyHandler` still uses `userStorage.findAll()` and `userStorage.save(...)` directly for user-selection and verification flows instead of going through a single application boundary.

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:323, 398, 404`
  - The app layer already exposes `undoSwipe(...)` and `standouts(...)`, and `standouts(...)` already resolves the user map.

- `src/main/java/datingapp/app/cli/MatchingHandler.java:610, 614, 619, 648, 665`
  - `MatchingHandler` uses `matchingUseCases` for most flows, but still reaches directly into `undoService` for undo and falls back to `standoutsService.resolveUsers(...)` after already calling `matchingUseCases.standouts(...)`.

- Related but slightly different: `Main.printMenu(...)` (`src/main/java/datingapp/Main.java:201-213`) and `MatchingHandler.showDailyLimitReached(...)` (`src/main/java/datingapp/app/cli/MatchingHandler.java:540-541`) are better described as **missing dedicated read-model seams** than as bypasses of an already-existing use-case API.

### Additional boundary drift inside the use-case layer

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:371, 386, 545, 547, 574`
  - `ProfileUseCases` already presents an app-layer command/result surface, but it still exposes core/service types in public contracts such as `SaveProfileCommand(User)`, `ProfileSaveResult(User, List<UserAchievement>)`, `AchievementSnapshot(List<UserAchievement>...)`, `UseCaseResult<ProfileService.CompletionResult>`, and `UseCaseResult<ProfileService.ProfilePreview>`.

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:273, 323, 386, 475, 491, 524-560`
  - `MatchingUseCases` still exposes `MatchingService.SwipeResult`, `UndoService.UndoResult`, `List<MatchingService.PendingLiker>`, `MatchQualityService.MatchQuality`, and command/result records that carry `User`, `Match`, `Like`, and `StandoutService.Result` directly.

- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:92, 132, 156, 181, 222`
  - `SocialUseCases` still returns core/service result types such as `TrustSafetyService.ReportResult` and `ConnectionService.TransitionResult` directly to adapters.

**Correct abstraction:** the use-case layer should be the app-facing contract. Adapters should depend on app-layer command/query/result records, while core entities and core-service result records stay behind that boundary unless they are intentionally promoted.

- `src/main/java/datingapp/core/metrics/AchievementService.java:10-34`
- `src/main/java/datingapp/storage/StorageFactory.java:101, 168`
- `src/main/java/datingapp/core/ServiceRegistry.java:61, 108-130, 383-384`
- `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java:13-14, 30-48`
  - A dedicated, registry-managed `AchievementService` abstraction already exists and is used directly by event handling.

- `src/main/java/datingapp/core/profile/ProfileService.java:28, 42, 167-191`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:638-652`
- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:48, 80, 113-114, 271`
  - `StorageFactory` already constructs the registry-managed `AchievementService`, but `ProfileService` still constructs its own `DefaultAchievementService` and re-exposes achievement methods.
  - `ProfileUseCases` still falls back from `AchievementService` to `ProfileService.checkAndUnlock(...)` / `getUnlocked(...)`, and `DashboardViewModel` still consumes `ProfileService` for achievement reads while `StatsViewModel` already uses `AchievementService`.

**Correct abstraction:** `AchievementService` should be the single achievement facade. Profile-related achievement flows should not keep a parallel public path alive through `ProfileService`.

### Why this matters

When the adapter boundary is only mostly respected:

- business rules become scattered between adapters and use cases
- read models evolve differently in CLI, REST, and UI
- “special case” adapter behavior becomes sticky and hard to eliminate later

### Recommended fix

- Keep **all business-facing adapter orchestration** behind `*UseCases` or dedicated read-model helpers.
- Move `ProfileHandler`, `SafetyHandler`, and `MatchingHandler` fully onto the existing app-layer seams for notes, user selection, undo, and standout resolution.
- For the remaining direct-read REST candidate path, choose one of two explicit directions:
  1. route it through `MatchingUseCases`, or
  2. create a named `CandidateReadModel` / query service and make that the sanctioned exception.
- Collapse the use-case public contract onto app-owned records instead of leaking core/service result types.
- Make `AchievementService` the one achievement boundary and retire the fallback achievement pathway through `ProfileService`.
- Add a tiny CLI dashboard query use case or read-model helper so `Main.printMenu(...)` and `MatchingHandler.showDailyLimitReached(...)` stop reaching directly into core services.

## 3. Event publication is modeled well, but still treated as optional in use-case code

### Best abstraction

The correct abstraction is:

- composition root always provides a real `AppEventBus`
- use cases publish through it
- event importance is modeled with handler policy (`REQUIRED` / `BEST_EFFORT`) at subscription time
- tests that want to suppress event effects use an **explicit test fixture**, not `null`

### Verified drift sites

- `src/main/java/datingapp/app/event/InProcessAppEventBus.java:12-48`
  - The repository already has a real in-process bus implementation.

- `src/main/java/datingapp/core/ServiceRegistry.java:103-136`
  - `ServiceRegistry` requires a non-null `eventBus` and wires it into the use cases.

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:38-53, 160`
  - `MatchingUseCases` installs a `NO_OP_EVENT_BUS` when `null` is passed.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:600-614`
  - `publishEvent(...)` returns immediately when `eventBus == null`.

- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:337-345`
  - `publishEvent(...)` returns immediately when `eventBus == null`.

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java:299-307`
  - `publishEvent(...)` returns immediately when `eventBus == null`.

### Why this matters

The codebase already treats events as part of the application contract, but these null/no-op paths mean that the same use case can either:

- publish events,
- silently skip them,
- or quietly replace them with a no-op bus,

depending on which constructor path was used.

That weakens observability and makes side effects a wiring accident instead of a design guarantee.

### Recommended fix

- Make `AppEventBus` mandatory in production-facing constructors/builders.
- Remove null guards and null-to-no-op substitution from use-case production paths.
- If tests need silence, provide an explicit `TestEventBus.noOp()` or fixture-level builder.

## 4. The repo already has helper abstractions for domain invariants, but some code still reimplements them manually

### Best abstraction

When the repo already has a helper for a domain invariant, use it everywhere:

- deterministic aggregate IDs → `generateId(...)`
- deep-copy semantics → model-level `copy()` or model builder helpers
- atomic logical writes → storage helper methods
- collection copy/clear semantics → domain setter or safe-copy helper

### Verified drift sites

#### 4.1 Deterministic ID generation

- `src/main/java/datingapp/core/model/Match.java:129-145`
  - `Match.create(...)` manually re-sorts the two user IDs and manually builds the ID string.
- `src/main/java/datingapp/core/model/Match.java:135-145`
  - `Match.generateId(...)` already exists.

- `src/main/java/datingapp/core/connection/ConnectionModels.java:176-199`
  - `Conversation.create(...)` manually re-sorts user IDs and manually builds the conversation ID.
- `src/main/java/datingapp/core/connection/ConnectionModels.java:201-209`
  - `Conversation.generateId(...)` already exists.

**Correct abstraction:** `create(...)` factories should delegate to `generateId(...)`, not duplicate the normalization algorithm.

#### 4.2 User copy semantics

- `src/main/java/datingapp/core/model/User.java:911-949`
  - `User.copy()` deep-copies the user, but still manually reconstructs `Dealbreakers` field-by-field.

- `src/main/java/datingapp/core/profile/MatchPreferences.java:493-504`
  - `Dealbreakers.toBuilder()` already exists and is designed for safe partial copying.

- `src/main/java/datingapp/core/matching/TrustSafetyService.java:422-445`
  - `TrustSafetyService.copyUser(...)` duplicates nearly the entire `User.copy()` flow instead of delegating to it.

- `src/main/java/datingapp/app/cli/ProfileHandler.java:135, 168-199`
  - `ProfileHandler.copyForProfileEditing(...)` also manually rebuilds a user-editing copy instead of delegating to `User.copy()`.

**Correct abstraction:** `User.copy()` should be the single user-deep-copy entry point for application code, and `User.copy()` itself should reuse `dealbreakers.toBuilder().build()` rather than rebuilding record fields manually.

#### 4.3 Atomic conversation message write path

- `src/main/java/datingapp/core/storage/CommunicationStorage.java:75-77`
  - `saveMessageAndUpdateConversationLastMessageAt(...)` already models the logical “save message + bump conversation timestamp” write.

- `src/main/java/datingapp/core/connection/ConnectionService.java:111-118`
  - `ConnectionService.sendMessage(...)` already uses the atomic helper.

- `src/main/java/datingapp/storage/DevDataSeeder.java:282-289`
  - Seeder code still calls `saveMessage(...)` three times and then manually updates `updateConversationLastMessageAt(...)`.

**Correct abstraction:** all code that means “append a message to a conversation” should use the same logical write helper, including seed and test setup.

#### 4.4 Collection copy / clear semantics

- `src/main/java/datingapp/core/EnumSetUtil.java:8-62`
  - The repo already has `EnumSetUtil.safeCopy(...)` and `defensiveCopy(...)`.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:206-214, 258-260, 659-669, 735-736`
  - `updateDiscoveryPreferences(...)` still uses raw `EnumSet.copyOf(...)`.
  - `updateProfile(...)` only applies `interestedIn` / `interests` when the incoming set is non-empty, which makes “clear to empty” semantics path-dependent.

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:544-558, 1459-1498`
  - The draft-building path only applies `interestedIn` / `interests` when the UI sets are non-empty.
  - That means clearing all selections can degrade into “leave old values in place”, depending on the path.

- `src/main/java/datingapp/storage/DevDataSeeder.java:1264, 1270`
  - Seeder helpers still use raw `EnumSet.copyOf(...)`.

**Correct abstraction:** let domain setters and safe-copy helpers own collection semantics, including the meaning of `null`, empty, and defensive copies.

### Why this matters

This category creates classic field-drift and semantic-drift bugs:

- two copies of the same invariant eventually diverge
- one path learns a new field and the other does not
- one path allows clearing and the other silently preserves stale data

### Recommended fix

- Delegate `create(...)` factories to `generateId(...)`.
- Make `User.copy()` the only user deep-copy implementation.
- Replace manual `Dealbreakers` rebuilding with `toBuilder().build()`.
- Use the atomic message-save helper everywhere a logical message write happens.
- Standardize enum-set semantics so “empty means clear” or “empty means ignore” is defined once and applied consistently across adapters and use cases.

## 5. Validation and typed application contracts are present, but not used uniformly

### Best abstraction

Rules about finite-state inputs, content validity, and mutator outcomes should be expressed through:

- injected validator services or small dedicated validator types
- typed command/query inputs where the value space is finite
- `UseCaseResult<Void>` or a dedicated result record for mutators, instead of “success plus redundant boolean”

### Verified drift sites

#### 5.1 Validation logic is centralized, but some content rules still live inline

- `src/main/java/datingapp/core/profile/ValidationService.java:61-254`
  - The repo already centralizes validation logic here for core profile-related rules.

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java:150-156`
  - `sendMessage(...)` still inlines the empty-content validation rule.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:492-504`
  - `upsertProfileNote(...)` still inlines max-length validation.

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:600-625, 1007, 1219-1332`
  - `ProfileViewModel` duplicates several validation rules and user-facing warning decisions in the ViewModel itself instead of routing through one shared validation boundary.

- `src/main/java/datingapp/ui/screen/ProfileController.java:292-296, 584, 622`
- `src/main/java/datingapp/ui/screen/ProfileFormValidator.java:10-67`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:694-695`
  - `ProfileController` constructs a second validation layer via `ProfileFormValidator(viewModel.getValidationService())`, and the ViewModel returns a freshly created `ValidationService` instance on demand.
  - That leaves the profile-edit flow split across `ValidationService`, controller-local validation wrappers, and inline ViewModel checks.

**Correct abstraction:** validation should live in one injected boundary or one small deliberate validator family. Message-content and note-content checks are currently extraction opportunities, but the profile-edit flow already shows concrete validation overlap that should be collapsed.

#### 5.2 Typed command inputs are inconsistent

- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:352-353`
  - `ReportCommand` already uses typed `Report.Reason`.

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java:294-295`
  - `ArchiveConversationCommand` already uses typed `MatchArchiveReason`.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:418, 578, 613-614`
  - `DeleteAccountCommand` still carries a raw `String reason` and converts it later through `sanitizeDeletionReason(...)`.

**Correct abstraction:** adapters should parse free-form external values before the use-case boundary; the use case should receive a typed deletion reason or a typed parser result.

#### 5.3 Mutator results are mostly standardized, but two profile mutators still return redundant booleans

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:401-431`
  - `deleteAccount(...)` returns `UseCaseResult<Boolean>`.

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:516-535`
  - `deleteProfileNote(...)` returns `UseCaseResult<Boolean>`.

- In contrast, other mutators already use `UseCaseResult<Void>` or dedicated result records, e.g.:
  - `MatchingUseCases.java:411, 463, 505`
  - `MessagingUseCases.java:179, 219, 233, 255`
  - `SocialUseCases.java:273`

**Correct abstraction:** use `UseCaseResult<Void>` when the boolean only mirrors success/failure, or promote the extra state into a dedicated result record if it matters.

### Why this matters

When validation and command/result typing are inconsistent:

- adapters and use cases disagree on where rules live
- the same rule is enforced with different messages or semantics
- external free-form data survives too deep into core logic
- result types become harder to reason about and harder to standardize

### Recommended fix

- Move inline validation into shared validators.
- Keep ViewModels/UI code focused on presentation and immediate UX, not ownership of business validation rules.
- Convert raw string command inputs to typed values before the use-case boundary.
- Normalize mutator return types across the use-case layer.

## 6. The UI already has solid lifecycle and async abstractions, but only some ViewModels follow them

### Best abstraction

The intended UI pattern is already visible:

- `BaseController` owns common controller lifecycle / back-navigation behavior
- `BaseViewModel` owns loading, disposal, async scope, and error routing infrastructure
- `ViewModelAsyncScope` owns background execution and UI dispatch
- `UiThreadDispatcher` abstracts JavaFX thread hopping
- `ViewModelErrorSink` lets controllers own feedback presentation
- `ViewModelFactory` owns composition

### Verified drift sites

#### 6.1 Partial `BaseViewModel` adoption

- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java:17-69`
  - The abstraction exists and is solid.

- Concrete ViewModels that **already** extend `BaseViewModel`:
  - `DashboardViewModel.java:38`
  - `LoginViewModel.java:35`
  - `MatchingViewModel.java:50`
  - `NotesViewModel.java:32`
  - `ProfileReadOnlyViewModel.java:19`
  - `SafetyViewModel.java:30`

- Concrete ViewModels that still reimplement similar lifecycle concerns instead of extending it:
  - `ChatViewModel.java:57-214`
  - `MatchesViewModel.java:66-175, 780-783`
  - `PreferencesViewModel.java:35-85, 293-296`
  - `ProfileViewModel.java:59-224, 1403-1406`
  - `SocialViewModel.java:40-90, 241-244`
  - `StandoutsViewModel.java:33-91, 214-217`
  - `StatsViewModel.java:43-138, 350-353`

- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java:336-341`
  - The factory still needs a reflection-based fallback disposal path for ViewModels “not yet migrated to `BaseViewModel`”.

- `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java:29-45`
  - The base class already supports constructor-time error-sink wiring through `ViewModelErrorSink`.

- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java:75, 109, 143`
- `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java:44, 57, 67`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java:42, 59, 70`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java:68, 78`
  - Some ViewModels already extend `BaseViewModel`, but still bypass the base class’s constructor-time error-routing contract by keeping separate mutable `errorHandler` fields and late-bound `setErrorHandler(...)` methods.

**Correct abstraction:** if a ViewModel owns async work and disposal, it should almost always extend `BaseViewModel`, and base-class capabilities like constructor-time error-sink wiring should be used consistently instead of being reintroduced per subclass.

#### 6.2 ViewModels still self-compose UI thread and dependency infrastructure

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:59-202`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java:35-76`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:148, 162`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:115, 204`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java:88, 104, 121`
- `src/main/java/datingapp/ui/viewmodel/StandoutsViewModel.java:74, 79`
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java:75`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java:47`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java:54`
- `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java:49`
  - These constructors create `new JavaFxUiThreadDispatcher()` themselves instead of relying on `ViewModelFactory` to supply dispatchers.

**Correct abstraction:** ViewModels should accept dependencies; factories should build them.

#### 6.3 Feedback ownership is split between controllers and ViewModels

- Many controllers already use a controller-owned feedback pattern, e.g.:
  - `DashboardController.java:125`
  - `ChatController.java:147`
  - `MatchesController.java:150`
  - `ProfileController.java:301`

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:600-625, 1007, 1054-1059, 1184, 1219-1332`
  - `ProfileViewModel` still calls `UiFeedbackService` directly for warnings/errors/successes.

**Correct abstraction:** controllers own feedback presentation; ViewModels should surface state/errors through `ViewModelErrorSink` or bindable properties unless a ViewModel is explicitly acting as a presenter.

#### 6.4 Navigation/theme side effects are not fully isolated to controllers

- `src/main/java/datingapp/ui/screen/BaseController.java:112`
  - `handleBack()` already exists as the shared back-navigation seam.

- `src/main/java/datingapp/ui/screen/PreferencesController.java:208`
  - One path still calls `NavigationService.getInstance().goBack()` directly instead of consistently using the base helper.

- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java:105-153, 197-217`
  - `PreferencesViewModel` mutates global theme/navigation state via `NavigationService.getInstance().setThemeMode(...)`.

- `src/main/java/datingapp/ui/NavigationService.java:164-167`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java:153-154, 217, 224`
  - Theme persistence ownership is also duplicated: `NavigationService.setThemeMode(...)` already saves the theme, but `PreferencesViewModel` separately calls `uiPreferencesStore.saveThemeMode(...)` around the same transitions.

**Correct abstraction:** controllers own navigation decisions; if a ViewModel must influence global UI state, it should do so through a small injected façade rather than directly reaching into a singleton.

#### 6.5 `UiDataAdapters` is a useful UI boundary, but it still leaks core paging types

- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java:15, 57-67, 190-193`
  - `UiDataAdapters` is clearly intended as the UI-facing boundary, but `UiMatchDataAccess` still exposes `PageData<Match>` from `datingapp.core.storage` directly.

- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:20, 291-337`
  - `MatchesViewModel` therefore still imports and reasons about `PageData` directly instead of consuming a UI-owned paged result abstraction.

**Correct abstraction:** once a UI adapter boundary exists, ViewModels should consume UI-facing DTOs/results rather than core-storage transport types.

#### 6.6 Async/threading helpers are still bypassed outside the dedicated async package

- `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java:141, 187`
- `src/main/java/datingapp/ui/async/JavaFxUiThreadDispatcher.java:29`
  - These are the intended async/threading primitives.

- `src/main/java/datingapp/ui/UiComponents.java:281-338`
  - Still uses raw `Thread.ofVirtual()` and `Platform.runLater(...)` for background/UI coordination.

- `src/main/java/datingapp/ui/NavigationService.java:437`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:243`
  - Still use direct `Platform.runLater(...)` instead of routing through a dispatcher abstraction.

**Correct abstraction:** keep raw JavaFX thread primitives in the smallest possible set of bridge classes; everywhere else should use `ViewModelAsyncScope` / `UiThreadDispatcher`.

### Why this matters

The UI layer already has a refactor target. The problem is not “we need to invent a UI architecture”; it is “we need to finish adopting the one we already wrote.”

### Recommended fix

- Make `BaseViewModel` the default for all async/disposable ViewModels.
- Use the base class’s constructor-time sink routing consistently instead of reintroducing mutable error-handler plumbing in subclasses.
- Shrink public constructor surfaces to one production constructor plus explicit test helpers if necessary.
- Push feedback and navigation back to controllers or injected façades.
- Stop persisting theme state in both `PreferencesViewModel` and `NavigationService`; one owner should commit the setting.
- Return UI-specific paged results from `UiDataAdapters` instead of exposing `PageData` directly to ViewModels.
- Keep raw `Platform.runLater(...)` / `Thread.ofVirtual()` out of ordinary UI code.

## 7. The test suite has shared support abstractions, but many tests still use bespoke harnesses and white-box access

### Best abstraction

The test suite should standardize on:

- `JavaFxTestSupport` for JavaFX startup, FX-thread execution, FXML loading, and waiting
- shared fixture builders around `ServiceRegistry.builder()` + `TestStorages`
- public-behavior tests first; reflection only where there is no credible public seam

### Verified drift sites

#### 7.1 JavaFX tests still bypass `JavaFxTestSupport`

- `src/test/java/datingapp/ui/JavaFxTestSupport.java:29-58, 142-155`
  - Canonical helper already exists.

- Tests that still reimplement startup or wait logic instead of using it:
  - `src/test/java/datingapp/ui/viewmodel/LoginViewModelTest.java:23-119`
  - `src/test/java/datingapp/ui/viewmodel/SocialViewModelTest.java:50, 243-260`
  - `src/test/java/datingapp/ui/viewmodel/StandoutsViewModelTest.java:60, 214-231`
  - `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java:56, 205-218`
  - `src/test/java/datingapp/ui/viewmodel/NotesViewModelTest.java:50, 196-209`
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:70, 153-170, 480`
  - `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java:64, 478-484`
  - `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java:50, 109-125`
  - `src/test/java/datingapp/ui/JavaFxCssValidationTest.java:40-56, 101-143`

#### 7.2 Async ViewModel test utilities are duplicated

- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java:322-389`
  - Already demonstrates a reusable queued-dispatcher / async-drain testing pattern.

- `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java:229-310`
  - Reimplements a similar queue-drain dispatcher pattern locally.

#### 7.3 REST/API tests repeatedly rebuild the same service graph

- `ServiceRegistry.builder()` is the natural composition abstraction.

- Duplicated `createServices(...)` graphs appear in:
  - `src/test/java/datingapp/app/api/RestApiHealthRoutesTest.java:119-177`
  - `src/test/java/datingapp/app/api/RestApiConversationBatchCountTest.java:111-...`
  - `src/test/java/datingapp/app/api/RestApiDailyLimitTest.java:232-308`
  - `src/test/java/datingapp/app/api/RestApiNotesRoutesTest.java:132-...`
  - `src/test/java/datingapp/app/api/RestApiRateLimitTest.java:103-170`
  - `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java:211-269`
  - `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java:352-362` and nearby overload
  - `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java:770-828`
  - `src/test/java/datingapp/app/MatchingFlowIntegrationTest.java:113-...`

- REST API test helper duplication also exists for local storage doubles such as:
  - `RestApiHealthRoutesTest.java:191-227`
  - `RestApiReadRoutesTest.java:296-332`
  - `RestApiNotesRoutesTest.java:212-248`
  - `RestApiRateLimitTest.java:188-224`
  - `RestApiRelationshipRoutesTest.java:444-480`
  - `RestApiPhaseTwoRoutesTest.java:934-970`

- The same composition drift also appears in UI-adjacent tests that hand-wire production-style graphs instead of reusing a shared fixture builder, e.g.:
  - `src/test/java/datingapp/ui/screen/DashboardControllerTest.java:60-83`
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:78-120`
  - `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java:66-80, 99-103, 146-160`

#### 7.4 Reflection is still used widely where the suite often has better seams available

- Verified reflection-heavy files include:
  - `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:122-123, 264-265, 281-282`
  - `src/test/java/datingapp/app/cli/MatchingHandlerTest.java:181-183`
  - `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java:850-861`
  - `src/test/java/datingapp/ui/screen/BaseControllerTest.java:64-65`
  - `src/test/java/datingapp/ui/screen/MilestonePopupControllerTest.java:122-129`
  - `src/test/java/datingapp/ui/screen/MatchesControllerTest.java:214-215`
  - `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java:504-505`

- The strongest cases here are the ones where the test bypasses a credible public/shared seam that already exists, such as `BaseController.handleBack()`, JavaFX controller actions, or public CLI entry points.

#### 7.5 Test helper duplication still exists beyond the big fixtures

- `src/test/java/datingapp/core/testutil/TestUserFactory.java:15-42`
  - The suite already has a shared `TestUserFactory` abstraction.

- Many tests use it successfully, e.g.:
  - `src/test/java/datingapp/ui/viewmodel/SocialViewModelTest.java:15, 78, 117`
  - `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java:35, 160-199`

- But many other tests still define local `createActiveUser(...)` / `createEditableUser(...)` helpers instead, e.g.:
  - `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:126-127, 629`
  - `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java:73, 106, 153, 182, 223`
  - `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java:96, 214, 489`
  - `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:81, 92, 132, 183, 227, 286`

- There is also a duplicated `ProfileServiceTest` under both:
  - `src/test/java/datingapp/core/ProfileServiceTest.java`
  - `src/test/java/datingapp/core/profile/ProfileServiceTest.java`

### Why this matters

The test suite already has the beginnings of a coherent harness. The inconsistency is making it expensive to maintain and easy to drift.

### Recommended fix

- Standardize JavaFX tests on `JavaFxTestSupport`.
- Extract one shared REST/API test fixture builder around `ServiceRegistry.builder()` + `TestStorages`.
- Reuse the same fixture-builder approach in UI near-integration tests instead of hand-wiring service graphs per file.
- Extract reusable async-test dispatch utilities from `ViewModelAsyncScopeTest` into a shared helper.
- Replace white-box reflection tests with public/package-private behavior seams wherever practical.
- Consolidate duplicated helper doubles and standardize user construction on `TestUserFactory` where its default shape is sufficient.

## What should **not** be mixed into the future abstraction-consistency implementation plan

These are real issues, but they are **not the same theme** and should be tracked separately unless they become direct fallout from the refactor:

- `ActivityMetricsService.recordMatch()` / `endSession()` silent no-op behavior
- REST `limit` values not being capped to a maximum
- candidate distance policy mixing permissive filtering with worst-distance sorting
- location dataset self-validation gaps
- achievement handlers using `BEST_EFFORT`
- the fact that `JavaFxTestSupport.waitUntil(...)` itself is polling-based

Those are worth fixing, but they are not primarily “the right abstraction exists but is not being used consistently.”

## Recommended remediation order

This is the order I would use for a later implementation plan.

1. **Standardize construction and composition roots**
   - tighten `ProfileUseCases`, `SocialUseCases`, `MatchingUseCases`
   - remove production self-composition from ViewModels/handlers

2. **Make the event boundary mandatory**
   - no more `null` / no-op event bus substitution in production paths

3. **Finish the use-case boundary cleanup**
   - eliminate remaining adapter-level direct service reads for user-facing flows

4. **Consolidate domain-helper reuse**
   - deterministic IDs
   - user copy/dealbreaker copy
   - atomic message write helper
   - collection copy/clear semantics

5. **Unify validation and typed command/result contracts**
   - remove inline validation drift
   - normalize command typing and mutator return types

6. **Finish the UI abstraction migration**
   - move remaining ViewModels to `BaseViewModel`
   - centralize navigation/feedback ownership
   - remove ad-hoc thread hopping

7. **Unify the test harness last, but thoroughly**
   - shared JavaFX harness
   - shared REST fixture builder
   - less reflection
   - fewer copied helpers

## Bottom line

The project does **not** need a brand-new architecture.

It already has the right architecture in many places.

The next step is to choose the existing abstractions that are already best-in-class in this repo and make them the **only normal way** to solve those problems:

- one composition root
- one application boundary
- one event boundary
- one set of domain helpers for invariants
- one UI lifecycle pattern
- one test harness style

That is the highest-leverage way to reduce architectural drift in this codebase.