# Pre-Kotlin Composition, Contract, and Bootstrap Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce compatibility-heavy construction clutter, replace weak runtime trap/null-sentinel boundary contracts with explicit contracts, and replace brittle reflection-heavy bootstrap assertions with stronger public-contract tests before any Java→Kotlin migration.

**Architecture:** Keep `ServiceRegistry` and `ViewModelFactory` as the production composition roots, but simplify the seams around them: collapse compatibility overloads, remove null-driven fallback wiring, and move construction helpers into smaller explicit collaborators where that reduces noise without changing behavior. Tighten boundaries by making absence and capability explicit in types (`Optional`, explicit validation errors, dedicated operational storage interfaces) rather than mixing `null`, `UnsupportedOperationException`, and boolean support flags. Rework bootstrap failure-path coverage to assert observable lifecycle behavior and narrow package-private test hooks instead of reflecting into private static state.

**Tech Stack:** Java 25, Maven, JUnit 5, Javalin 6, JDBI 3, PostgreSQL JDBC, H2 compatibility path, JavaFX 25, PowerShell 7.

---

## ✅ Execution status (2026-04-06)

- ✅ Task 1 completed — canonical `ProfileUseCases` construction, canonical CLI handler wiring, and `UiAdapterCache` characterization coverage were added and verified.
- ✅ Task 2 completed — the compatibility-heavy `ProfileUseCases` façade and CLI constructor paths were collapsed to explicit production wiring.
- ✅ Task 3 completed — `UiAdapterCache` was extracted and `ViewModelFactory` now delegates shared adapter lifecycle to it while remaining the UI composition root.
- ✅ Task 4 completed — targeted null-sentinel helper seams were replaced with explicit `Optional` / validation-result flows across connection, messaging, profile-note, and REST boundaries.
- ✅ Task 5 completed — operational storage interfaces now make required runtime capabilities explicit, and production/runtime services are wired against those contracts.
- ✅ Task 6 completed — `ApplicationStartup` bootstrap recovery tests now rely on narrow package-private test hooks and public lifecycle behavior rather than reflection into private static fields.
- ✅ Task 7 completed — focused packs, `mvn spotless:apply verify`, `./run_postgresql_smoke.ps1`, and the repo-level `./run_verify.ps1` verification path all passed in the implementation session.

---

## Source of truth used for this plan

This plan is based on the current source only:

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/app/cli/StatsHandler.java`
- `src/main/java/datingapp/app/cli/SafetyHandler.java`
- `src/main/java/datingapp/app/cli/MatchingHandler.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/core/storage/UserStorage.java`
- `src/main/java/datingapp/core/storage/InteractionStorage.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- `src/test/java/datingapp/app/cli/ProfileCreateSelectTest.java`
- `src/test/java/datingapp/app/cli/ProfileNotesHandlerTest.java`
- `src/test/java/datingapp/app/cli/StatsHandlerTest.java`
- `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`
- `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java`
- `src/test/java/datingapp/app/cli/RelationshipHandlerTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- `src/test/java/datingapp/core/storage/StorageContractTest.java`
- `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- `src/test/java/datingapp/core/testutil/TestStorages.java`
- `src/test/java/datingapp/app/bootstrap/ApplicationStartupFailureRecoveryTest.java`
- `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`
- `src/test/java/datingapp/app/bootstrap/MainBootstrapLifecycleTest.java`

Validated starting point before this plan:

- `mvn spotless:apply verify` is green.
- `ProfileUseCases` still exposes **3 public constructors + a builder** and internally manufactures slice use cases when passed `null`.
- `ServiceRegistry` still constructs the compatibility `ProfileUseCases` façade and still has several optional fallback assembly branches.
- `StatsHandler`, `ProfileHandler`, and `SafetyHandler` still carry compatibility-heavy constructor shapes; `SafetyHandler` still takes a dead `TrustSafetyService` parameter just to null-check it.
- `ViewModelFactory` still owns both view-model caching and three cached UI adapter singletons.
- `UserStorage`, `InteractionStorage`, and `CommunicationStorage` still expose runtime traps (`UnsupportedOperationException`) and/or capability sentinels (`supportsAtomic...()`).
- `ConnectionService.getConversationPreview(...)` returns `null` for absence.
- `ProfileNotesUseCases.requireAuthorExists(...)` returns `null` on success.
- `RestApiServer.loadUser(...)` returns `null`, and `dataOrHandleFailure(...)` can collapse a successful null payload into `Optional.empty()`.
- `ApplicationStartupFailureRecoveryTest` reflects into private static fields in `ApplicationStartup` to assert failure rollback.

---

## Repository guardrails this plan must preserve

- Keep `core/` framework-free.
- Keep `ServiceRegistry` and `ViewModelFactory` as the production composition roots.
- Preserve current runtime behavior across CLI, JavaFX, REST, H2 compatibility, and PostgreSQL runtime paths.
- Prefer explicit contracts over hidden compatibility fallbacks.
- Keep diffs small and layered even if the plan is comprehensive.

---

## Decisions locked in by this plan

1. **`ProfileUseCases` remains only as a thin compatibility façade.**
   - It will no longer self-assemble slice use cases from nullable constructor inputs.
   - The canonical path is: build the slices first, then pass them into the façade.

2. **The CLI handlers will move to one canonical construction path per handler.**
   - Keep `fromServices(...)` where it is useful.
   - Remove dead constructor parameters and null-driven dual-path logic.

3. **`ViewModelFactory` stays the root, but its adapter cache moves into a dedicated helper.**
   - This reduces construction clutter without replacing the root.

4. **Null sentinels at app/service/API boundaries are removed.**
   - Use `Optional` or explicit validation error objects instead.

5. **Storage capabilities required by production code become explicit at type level.**
   - Replace “call and maybe throw / call and maybe return false because unsupported” with operational interfaces wired at composition time.

6. **Bootstrap failure-path tests should assert public behavior, not private field state.**
   - A narrow package-private test hook is acceptable if needed to trigger deterministic failure.
   - Reflecting into private fields is not the target state.

---

## Non-goals

- Do **not** start Kotlin conversion.
- Do **not** rewrite business logic or change matching behavior.
- Do **not** broaden location scope or REST network exposure.
- Do **not** replace `ServiceRegistry` or `ViewModelFactory` as roots.
- Do **not** remove all runtime reflection tests in unrelated subsystems; this plan focuses on bootstrap/lifecycle assertions first.

---

## File map

### Create

- `src/main/java/datingapp/ui/viewmodel/UiAdapterCache.java` — owns the cached `UiUserStore`, `UiProfileNoteDataAccess`, and `UiPresenceDataAccess` instances for `ViewModelFactory`.
- `src/main/java/datingapp/core/storage/OperationalUserStorage.java` — explicit user-storage capability contract for lock-aware and purge-capable runtime paths.
- `src/main/java/datingapp/core/storage/OperationalInteractionStorage.java` — explicit interaction-storage capability contract for transition-capable and purge-capable runtime paths.
- `src/main/java/datingapp/core/storage/OperationalCommunicationStorage.java` — explicit communication-storage capability contract for counted and atomic write paths.
- `src/test/java/datingapp/ui/viewmodel/UiAdapterCacheTest.java` — focused cache lifecycle test for the extracted UI adapter helper.

### Modify

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/app/cli/StatsHandler.java`
- `src/main/java/datingapp/app/cli/SafetyHandler.java`
- `src/main/java/datingapp/app/cli/MatchingHandler.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/core/storage/UserStorage.java`
- `src/main/java/datingapp/core/storage/InteractionStorage.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- `src/test/java/datingapp/app/cli/ProfileCreateSelectTest.java`
- `src/test/java/datingapp/app/cli/ProfileNotesHandlerTest.java`
- `src/test/java/datingapp/app/cli/StatsHandlerTest.java`
- `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`
- `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java`
- `src/test/java/datingapp/app/cli/RelationshipHandlerTest.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- `src/test/java/datingapp/core/storage/StorageContractTest.java`
- `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- `src/test/java/datingapp/core/testutil/TestStorages.java`
- `src/test/java/datingapp/app/bootstrap/ApplicationStartupFailureRecoveryTest.java`
- `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`

### Verify only

- `src/test/java/datingapp/app/bootstrap/MainBootstrapLifecycleTest.java`
- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`
- `run_postgresql_smoke.ps1`

---

## Task 1: Lock the desired composition-root behavior with failing tests

**Files:**
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`
- Modify: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/ProfileCreateSelectTest.java`
- Modify: `src/test/java/datingapp/app/cli/ProfileNotesHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/StatsHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/LikerBrowserHandlerTest.java`
- Modify: `src/test/java/datingapp/app/cli/RelationshipHandlerTest.java`
- Create: `src/test/java/datingapp/ui/viewmodel/UiAdapterCacheTest.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`

- [ ] **Step 1: Add failing tests for the canonical `ProfileUseCases` construction path**

In `ProfileUseCasesTest.java`, add tests that target the post-cleanup constructor shape.

Target constructor shape:

```java
ProfileUseCases useCases = new ProfileUseCases(
        userStorage,
        profileService,
        validationService,
        profileMutationUseCases,
        profileNotesUseCases,
        profileInsightsUseCases);
```

Add at least these tests:

```java
@Test
void canonicalConstructorRequiresAllSliceDependencies() {
    assertThrows(
            NullPointerException.class,
            () -> new ProfileUseCases(
                    userStorage,
                    profileService,
                    validationService,
                    profileMutationUseCases,
                    profileNotesUseCases,
                    null));
}

@Test
void canonicalConstructorKeepsFacadeThinAndDelegatesToProvidedSlices() {
    ProfileUseCases useCases = new ProfileUseCases(
            userStorage,
            profileService,
            validationService,
            profileMutationUseCases,
            profileNotesUseCases,
            profileInsightsUseCases);

    assertSame(profileMutationUseCases, useCases.getProfileMutationUseCases());
    assertSame(profileNotesUseCases, useCases.getProfileNotesUseCases());
    assertSame(profileInsightsUseCases, useCases.getProfileInsightsUseCases());
}
```

- [ ] **Step 2: Add failing handler-construction tests that use the future canonical constructors**

Update the test helpers in:
- `ProfileHandlerTest.java`
- `ProfileCreateSelectTest.java`
- `ProfileNotesHandlerTest.java`
- `StatsHandlerTest.java`
- `SafetyHandlerTest.java`

Target shapes to lock in:

```java
new ProfileHandler(validationService, locationService, profileUseCases, config, session, inputReader)
```

```java
new StatsHandler(profileInsightsUseCases, session, inputReader, userTimeZone)
```

```java
new SafetyHandler(socialUseCases, profileUseCases, verificationUseCases, session, inputReader, config)
```

For `MatchingHandler`, stop treating `profileCompleteCallback` as nullable in tests; use an explicit no-op callback:

```java
MatchingHandler.Dependencies deps = MatchingHandler.Dependencies.fromServices(
        services,
        session,
        inputReader,
        () -> {});
```

- [ ] **Step 3: Add failing tests for `UiAdapterCache` and its interaction with `ViewModelFactory`**

Create `UiAdapterCacheTest.java` with concrete cache lifecycle coverage:

```java
@Test
void userStoreIsCachedUntilReset() {
    UiAdapterCache cache = new UiAdapterCache();

    UiUserStore first = cache.userStore(services);
    UiUserStore second = cache.userStore(services);

    assertSame(first, second);

    cache.reset();

    UiUserStore third = cache.userStore(services);
    assertNotSame(first, third);
}

@Test
void profileNotesAndPresenceAdaptersAreCachedIndependently() {
    UiAdapterCache cache = new UiAdapterCache();

    assertSame(cache.profileNotes(services), cache.profileNotes(services));
    assertSame(cache.presence(services), cache.presence(services));
}
```

Then add one `ViewModelFactoryTest` assertion that `reset()` / `dispose()` clears the adapter cache by behavior, not by private field inspection.

- [ ] **Step 4: Run the focused composition-root pack and confirm red state**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ServiceRegistryTest,ProfileHandlerTest,ProfileCreateSelectTest,ProfileNotesHandlerTest,StatsHandlerTest,SafetyHandlerTest,MatchingHandlerTest,LikerBrowserHandlerTest,RelationshipHandlerTest,ViewModelFactoryTest,UiAdapterCacheTest test
```

Expected:
- FAIL to compile and/or fail tests because the canonical constructors and `UiAdapterCache` do not exist yet.

---

## Task 2: Collapse the compatibility-heavy profile façade and CLI construction paths

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java`
- Modify: `src/main/java/datingapp/app/cli/SafetyHandler.java`
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java`
- Modify: tests from Task 1 as needed

- [ ] **Step 1: Replace `ProfileUseCases` overloads/builders with one explicit compatibility constructor**

Replace the current three-constructor-plus-builder surface with one constructor that requires fully built collaborators.

Target implementation shape:

```java
public final class ProfileUseCases {
    private final UserStorage userStorage;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final ProfileNotesUseCases profileNotesUseCases;
    private final ProfileInsightsUseCases profileInsightsUseCases;

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ProfileMutationUseCases profileMutationUseCases,
            ProfileNotesUseCases profileNotesUseCases,
            ProfileInsightsUseCases profileInsightsUseCases) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.profileMutationUseCases = Objects.requireNonNull(profileMutationUseCases, "profileMutationUseCases cannot be null");
        this.profileNotesUseCases = Objects.requireNonNull(profileNotesUseCases, "profileNotesUseCases cannot be null");
        this.profileInsightsUseCases = Objects.requireNonNull(profileInsightsUseCases, "profileInsightsUseCases cannot be null");
    }
}
```

Delete:
- the builder,
- the nullable fallback constructor branches,
- the internal self-assembly of slice use cases.

- [ ] **Step 2: Update `ServiceRegistry` to assemble the profile slice explicitly before creating the façade**

In `ServiceRegistry.java`, keep the slices as the primary construction units and build the façade last.

Target assembly shape:

```java
this.profileMutationUseCases = builder.profileMutationUseCases != null
        ? builder.profileMutationUseCases
        : new ProfileMutationUseCases(...);
this.profileInsightsUseCases = builder.profileInsightsUseCases != null
        ? builder.profileInsightsUseCases
        : new ProfileInsightsUseCases(...);
this.profileNotesUseCases = builder.profileNotesUseCases != null
        ? builder.profileNotesUseCases
        : new ProfileNotesUseCases(...);

this.profileUseCases = new ProfileUseCases(
        this.userStorage,
        this.profileService,
        this.validationService,
        this.profileMutationUseCases,
        this.profileNotesUseCases,
        this.profileInsightsUseCases);
```

Do **not** replace `ServiceRegistry` as a root. The goal is to make it more explicit, not to invent a new root.

- [ ] **Step 3: Collapse handler constructor clutter to one canonical path per handler**

`ProfileHandler.java`
- Remove the dead `userStorage` parameter from both constructors.
- Remove the constructor that silently creates a new `LocationService`.
- Keep one constructor plus `fromServices(...)`.

Target constructor:

```java
public ProfileHandler(
        ValidationService validationService,
        LocationService locationService,
        ProfileUseCases profileUseCases,
        AppConfig config,
        AppSession session,
        InputReader inputReader) {
    ...
}
```

`StatsHandler.java`
- Remove the `ProfileUseCases` constructor.
- Keep only the `ProfileInsightsUseCases` path.
- Delete the null-branching logic in `loadStats()` and `loadAchievements()`.

Target loader shape:

```java
private UseCaseResult<UserStats> loadStats(UUID userId) {
    return profileInsightsUseCases.getOrComputeStats(new StatsQuery(UserContext.cli(userId)));
}
```

`SafetyHandler.java`
- Remove the dead `TrustSafetyService` constructor parameter.
- Keep only the dependencies it actually stores.

`MatchingHandler.java`
- Replace nullable callback semantics with an explicit no-op callback.

Target `Dependencies` normalization:

```java
private static final Runnable NO_OP = () -> {};

public Dependencies {
    profileCompleteCallback = profileCompleteCallback == null ? NO_OP : profileCompleteCallback;
    ...
}
```

- [ ] **Step 4: Re-run the composition-root pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ServiceRegistryTest,ProfileHandlerTest,ProfileCreateSelectTest,ProfileNotesHandlerTest,StatsHandlerTest,SafetyHandlerTest,MatchingHandlerTest,LikerBrowserHandlerTest,RelationshipHandlerTest test
```

Expected:
- PASS with one canonical constructor path per composition-heavy handler and a thin explicit `ProfileUseCases` façade.

---

## Task 3: Extract the UI adapter cache out of `ViewModelFactory`

**Files:**
- Create: `src/main/java/datingapp/ui/viewmodel/UiAdapterCache.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- Create: `src/test/java/datingapp/ui/viewmodel/UiAdapterCacheTest.java`

- [ ] **Step 1: Create the failing cache helper tests**

Use the test file from Task 1 and add at least one `ViewModelFactoryTest` that proves `reset()` / `dispose()` clears adapter instances by behavior.

If no existing test makes this observable, add a package-private accessor on `ViewModelFactory` **only if absolutely necessary**. Prefer direct `UiAdapterCache` tests instead of exposing internals on the root.

- [ ] **Step 2: Implement `UiAdapterCache` as a focused helper**

Create `UiAdapterCache.java` with this structure:

```java
package datingapp.ui.viewmodel;

import datingapp.core.ServiceRegistry;
import datingapp.ui.viewmodel.UiDataAdapters.MetricsUiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;

final class UiAdapterCache {
    private UiUserStore userStore;
    private UiProfileNoteDataAccess profileNotes;
    private UiPresenceDataAccess presence;

    UiUserStore userStore(ServiceRegistry services) {
        if (userStore == null) {
            userStore = new StorageUiUserStore(services.getUserStorage());
        }
        return userStore;
    }

    UiProfileNoteDataAccess profileNotes(ServiceRegistry services) {
        if (profileNotes == null) {
            profileNotes = new UseCaseUiProfileNoteDataAccess(services.getProfileNotesUseCases());
        }
        return profileNotes;
    }

    UiPresenceDataAccess presence(ServiceRegistry services) {
        if (presence == null) {
            presence = new MetricsUiPresenceDataAccess(services.getActivityMetricsService(), services.getConfig());
        }
        return presence;
    }

    void reset() {
        userStore = null;
        profileNotes = null;
        presence = null;
    }
}
```

- [ ] **Step 3: Refactor `ViewModelFactory` to delegate adapter lifecycle to the new helper**

Replace these fields:

```java
private UiUserStore cachedUiUserStore;
private UiProfileNoteDataAccess cachedUiProfileNoteDataAccess;
private UiDataAdapters.UiPresenceDataAccess cachedUiPresenceDataAccess;
```

with:

```java
private final UiAdapterCache uiAdapterCache = new UiAdapterCache();
```

Update all call sites:

```java
uiAdapterCache.userStore(services)
uiAdapterCache.profileNotes(services)
uiAdapterCache.presence(services)
```

and update `disposeCachedViewModels()` to call:

```java
uiAdapterCache.reset();
```

- [ ] **Step 4: Re-run the focused UI-root pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ViewModelFactoryTest,UiAdapterCacheTest test
```

Expected:
- PASS with `ViewModelFactory` still the root, but no longer owning three separate adapter-cache fields.

---

## Task 4: Remove null sentinels from app/service/API helper boundaries

**Files:**
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- Modify: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`

- [ ] **Step 1: Write failing tests for the null-sentinel seams**

Add these focused tests:

`ConnectionServiceTest.java`

```java
@Test
void getConversationPreviewReturnsEmptyWhenConversationIsMissing() {
    Optional<ConnectionService.ConversationPreview> preview =
            connectionService.getConversationPreview(userId, "missing-conversation");

    assertTrue(preview.isEmpty());
}
```

`MessagingUseCasesTest.java`

```java
@Test
void openConversationMapsMissingPreviewToNotFound() {
    UseCaseResult<MessagingUseCases.OpenConversationResult> result =
            useCases.openConversation(new OpenConversationCommand(UserContext.cli(userId), "missing"));

    assertFalse(result.success());
    assertEquals("Conversation not found", result.error().message());
}
```

`ProfileUseCasesNotesTest.java`

```java
@Test
void missingAuthorReturnsNotFoundWithoutNullSentinelBranching() {
    UseCaseResult<List<ProfileNote>> result =
            useCases.listProfileNotes(new ProfileNotesQuery(UserContext.cli(missingAuthorId)));

    assertFalse(result.success());
    assertEquals("Author not found", result.error().message());
}
```

`RestApiReadRoutesTest.java`
- Add a test that exercises a missing user on a read route and ensures the helper path returns the correct HTTP error without relying on `null` control flow.

- [ ] **Step 2: Change `ConnectionService.getConversationPreview(...)` to `Optional<ConversationPreview>`**

Target method shape:

```java
public Optional<ConversationPreview> getConversationPreview(UUID userId, String conversationId) {
    Objects.requireNonNull(userId, USER_ID_REQUIRED);
    Objects.requireNonNull(conversationId, CONVERSATION_ID_REQUIRED);

    Optional<Conversation> conversationOpt = findAuthorizedConversation(userId, conversationId);
    if (conversationOpt.isEmpty()) {
        return Optional.empty();
    }

    Conversation conversation = conversationOpt.get();
    Optional<User> otherUser = userStorage.get(conversation.getOtherUser(userId));
    if (otherUser.isEmpty()) {
        return Optional.empty();
    }

    Optional<Message> lastMessage = communicationStorage.getLatestMessage(conversationId);
    int unreadCount = calculateUnreadCount(userId, conversation);
    return Optional.of(new ConversationPreview(conversation, otherUser.get(), lastMessage, unreadCount));
}
```

Update `MessagingUseCases.openConversation(...)` to map `Optional.empty()` explicitly to `UseCaseError.notFound(...)`.

- [ ] **Step 3: Replace `ProfileNotesUseCases.requireAuthorExists(...)` with an explicit validation result**

Change this private helper:

```java
private Optional<UseCaseError> validateAuthor(UUID authorId) {
    if (userStorage == null) {
        return Optional.of(UseCaseError.dependency(USER_STORAGE_REQUIRED));
    }
    if (userStorage.get(authorId).isEmpty()) {
        return Optional.of(UseCaseError.notFound(AUTHOR_NOT_FOUND));
    }
    return Optional.empty();
}
```

Update callers to do this:

```java
Optional<UseCaseError> authorError = validateAuthor(command.context().userId());
if (authorError.isPresent()) {
    return UseCaseResult.failure(authorError.get());
}
```

No method in this class should return `null` anymore.

- [ ] **Step 4: Replace `RestApiServer` null-helper control flow with explicit helpers**

Introduce these helper shapes:

```java
private Optional<User> loadExistingUser(Context ctx, UUID userId) {
    var result = profileUseCases.getUserById(userId);
    if (!result.success()) {
        handleUseCaseFailure(ctx, result.error());
        return Optional.empty();
    }
    if (result.data() == null) {
        ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "User lookup returned no data"));
        return Optional.empty();
    }
    return Optional.of(result.data());
}

private boolean ensureUsersExist(Context ctx, UUID... userIds) {
    for (UUID userId : userIds) {
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return false;
        }
    }
    return true;
}

private <T> Optional<T> requiredDataOrHandleFailure(
        Context ctx, UseCaseResult<T> result, String unexpectedNullMessage) {
    if (handleFailureIfNeeded(ctx, result)) {
        return Optional.empty();
    }
    if (result.data() == null) {
        ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", unexpectedNullMessage));
        return Optional.empty();
    }
    return Optional.of(result.data());
}
```

Then convert route handlers from:

```java
User user = loadUser(ctx, id);
if (user == null) {
    return;
}
```

to:

```java
Optional<User> user = loadExistingUser(ctx, id);
if (user.isEmpty()) {
    return;
}
```

- [ ] **Step 5: Re-run the boundary-null pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ConnectionServiceTest,MessagingUseCasesTest,ProfileUseCasesNotesTest,RestApiReadRoutesTest test
```

Expected:
- PASS with the same observable behavior, but without null-sentinel helper control flow.

---

## Task 5: Make required storage capabilities explicit at type level

**Files:**
- Create: `src/main/java/datingapp/core/storage/OperationalUserStorage.java`
- Create: `src/main/java/datingapp/core/storage/OperationalInteractionStorage.java`
- Create: `src/main/java/datingapp/core/storage/OperationalCommunicationStorage.java`
- Modify: `src/main/java/datingapp/core/storage/UserStorage.java`
- Modify: `src/main/java/datingapp/core/storage/InteractionStorage.java`
- Modify: `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`
- Modify: `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Modify: `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/storage/StorageFactory.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- Modify: `src/test/java/datingapp/core/storage/StorageContractTest.java`
- Modify: `src/test/java/datingapp/core/testutil/TestStorages.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiMatchmakingStorageTransitionAtomicityTest.java`
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`

- [ ] **Step 1: Write failing tests that lock in explicit capability types**

Add these expectations:

`StorageContractTest.java`

```java
@Test
void inMemoryUserStorageImplementsOperationalUserStorage() {
    assertTrue(new TestStorages.Users() instanceof OperationalUserStorage);
}

@Test
void inMemoryInteractionStorageImplementsOperationalInteractionStorage() {
    assertTrue(new TestStorages.Interactions() instanceof OperationalInteractionStorage);
}

@Test
void inMemoryCommunicationStorageImplementsOperationalCommunicationStorage() {
    assertTrue(new TestStorages.Communications() instanceof OperationalCommunicationStorage);
}
```

`ServiceRegistryTest.java`
- Add one test that the storage instances used for runtime/domain service assembly are of the operational interfaces required by the services.

- [ ] **Step 2: Introduce explicit operational storage interfaces**

Create these files:

`OperationalUserStorage.java`

```java
package datingapp.core.storage;

import java.time.Instant;
import java.util.UUID;

public interface OperationalUserStorage extends UserStorage {
    @Override
    int purgeDeletedBefore(Instant threshold);

    @Override
    void executeWithUserLock(UUID userId, Runnable operation);
}
```

`OperationalInteractionStorage.java`

```java
package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.model.Match;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OperationalInteractionStorage extends InteractionStorage {
    @Override
    Optional<datingapp.core.connection.ConnectionModels.Like> getLikeById(UUID likeId);

    @Override
    Set<UUID> getMatchedCounterpartIds(UUID userId);

    @Override
    int purgeDeletedBefore(Instant threshold);

    @Override
    boolean acceptFriendZoneTransition(Match updatedMatch, FriendRequest updatedRequest, Conversation archivedConversation);

    @Override
    boolean gracefulExitTransition(Match updatedMatch, Optional<Conversation> archivedConversation, Conversation ignoredCompatibilityConversation);

    @Override
    boolean unmatchTransition(Match updatedMatch, Optional<Conversation> archivedConversation);

    @Override
    boolean blockTransition(UUID blockerId, UUID blockedId, Optional<Match> updatedMatch, Optional<Conversation> archivedConversation);
}
```

`OperationalCommunicationStorage.java`

```java
package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import java.util.UUID;

public interface OperationalCommunicationStorage extends CommunicationStorage {
    @Override
    void saveMessageAndUpdateConversationLastMessageAt(Message message);

    @Override
    void deleteConversationWithMessages(String conversationId);

    @Override
    void saveFriendRequestWithNotification(FriendRequest request, Notification notification);

    @Override
    int countPendingFriendRequestsForUser(UUID userId);
}
```

- [ ] **Step 3: Narrow the production services to the operational interfaces and remove capability booleans**

Change these constructors/fields:

`MatchingService.java`

```java
private final OperationalUserStorage userStorage;
```

`ActivityMetricsService.java`

```java
private final OperationalUserStorage userStorage;
private final OperationalInteractionStorage interactionStorage;
```

`ConnectionService.java`

```java
private final OperationalInteractionStorage interactionStorage;
private final OperationalCommunicationStorage communicationStorage;
```

`TrustSafetyService.java`

```java
private final OperationalInteractionStorage interactionStorage;
```

Then delete or stop using:
- `supportsAtomicRelationshipTransitions()`
- `supportsAtomicBlockTransition()`

and replace the fallback branches with direct operational calls. After this task, a `false` return from a transition method means **actual persistence failure**, not “unsupported implementation.”

- [ ] **Step 4: Update production and test storages to implement the operational interfaces**

Update:
- `JdbiUserStorage implements OperationalUserStorage`
- `JdbiMatchmakingStorage implements OperationalInteractionStorage`
- `JdbiConnectionStorage implements OperationalCommunicationStorage`
- `TestStorages.Users implements OperationalUserStorage`
- `TestStorages.Interactions implements OperationalInteractionStorage`
- `TestStorages.Communications implements OperationalCommunicationStorage`

Update `StorageFactory` / `ServiceRegistry` assembly so the domain services receive the operational interfaces directly.

- [ ] **Step 5: Re-run the capability-contract pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=StorageContractTest,ServiceRegistryTest,ConnectionServiceTest,JdbiCommunicationStorageSocialTest,JdbiMatchmakingStorageTransitionAtomicityTest test
```

Expected:
- PASS with capability requirements made explicit in types and no production-code dependence on trap defaults or boolean support sentinels.

---

## Task 6: Replace reflection-heavy bootstrap failure assertions with public-contract tests

**Files:**
- Modify: `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- Modify: `src/test/java/datingapp/app/bootstrap/ApplicationStartupFailureRecoveryTest.java`
- Modify: `src/test/java/datingapp/app/bootstrap/ApplicationStartupBootstrapTest.java`
- Verify: `src/test/java/datingapp/app/bootstrap/MainBootstrapLifecycleTest.java`

- [ ] **Step 1: Write the failing public-contract recovery test**

Replace the reflection-based assertions in `ApplicationStartupFailureRecoveryTest.java` with a behavior-first target test.

Target test shape:

```java
@Test
void failedInitializeLeavesServicesUnavailableAndAllowsCleanRetry() {
    ApplicationStartup.setInitializationCompleteHookForTests(() -> {
        throw new IllegalStateException("synthetic startup failure");
    });

    IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> ApplicationStartup.initialize(AppConfig.defaults()));

    assertTrue(error.getMessage().contains("synthetic startup failure"));
    assertThrows(IllegalStateException.class, ApplicationStartup::getServices);

    ApplicationStartup.setInitializationCompleteHookForTests(null);
    ApplicationStartup.reset();

    ServiceRegistry services = ApplicationStartup.initialize(AppConfig.defaults());
    assertNotNull(services);
    assertSame(services, ApplicationStartup.getServices());
}
```

This should replace the current reflective `snapshotState()` assertions.

- [ ] **Step 2: Add a narrow package-private test hook in `ApplicationStartup`**

If the existing `INITIALIZATION_COMPLETE_HOOK` remains the best deterministic failure trigger, expose it through a package-private method rather than reflection.

Target helper:

```java
static void setInitializationCompleteHookForTests(Runnable hook) {
    INITIALIZATION_COMPLETE_HOOK.set(hook);
}
```

Do **not** expose private lifecycle state like `initialized`, `services`, or `dbManager` for tests.

- [ ] **Step 3: Rework `ApplicationStartupFailureRecoveryTest` to assert only public behavior**

Delete these reflection helpers from the test:
- `initializationCompleteHook()`
- `snapshotState()`
- any `Field` access for `initialized`, `services`, `dbManager`, or `CLEANUP_SCHEDULER_REF`

Keep these observable assertions instead:
- failed initialize throws,
- `getServices()` remains unavailable,
- `reset()` / retry works,
- subsequent `initialize()` succeeds,
- `shutdown()` remains safe after failure.

Add a companion assertion in `ApplicationStartupBootstrapTest.java` if needed:

```java
@Test
void resetAfterFailedInitializeAllowsFreshSuccessfulInitialize() {
    // explicit regression guard for recovery path
}
```

- [ ] **Step 4: Re-run the focused bootstrap pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ApplicationStartupFailureRecoveryTest,ApplicationStartupBootstrapTest,MainBootstrapLifecycleTest test
```

Expected:
- PASS with no reflection into `ApplicationStartup` private fields.

---

## Task 7: Full-system verification and PostgreSQL runtime smoke

**Files:**
- Verify only: all touched files above

- [ ] **Step 1: Run the focused packs in execution order**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ServiceRegistryTest,ProfileHandlerTest,ProfileCreateSelectTest,ProfileNotesHandlerTest,StatsHandlerTest,SafetyHandlerTest,MatchingHandlerTest,LikerBrowserHandlerTest,RelationshipHandlerTest,ViewModelFactoryTest,UiAdapterCacheTest test
mvn --% -Dcheckstyle.skip=true -Dtest=ConnectionServiceTest,MessagingUseCasesTest,ProfileUseCasesNotesTest,RestApiReadRoutesTest test
mvn --% -Dcheckstyle.skip=true -Dtest=StorageContractTest,ServiceRegistryTest,JdbiCommunicationStorageSocialTest,JdbiMatchmakingStorageTransitionAtomicityTest test
mvn --% -Dcheckstyle.skip=true -Dtest=ApplicationStartupFailureRecoveryTest,ApplicationStartupBootstrapTest,MainBootstrapLifecycleTest test
```

Expected:
- All focused packs pass.

- [ ] **Step 2: Run the full Maven quality gate**

Run:

```powershell
mvn spotless:apply verify
```

Expected:
- PASS.

- [ ] **Step 3: Run the PostgreSQL runtime smoke path because storage/runtime contracts changed**

Run:

```powershell
.\run_postgresql_smoke.ps1
```

Expected:
- `PostgresqlRuntimeSmokeTest` runs and passes against the local PostgreSQL runtime path.

- [ ] **Step 4: Re-read the touched roots and confirm the end state**

Verify by inspection that:
- `ServiceRegistry` is still the production root.
- `ViewModelFactory` is still the production UI root.
- `ProfileUseCases` is now thin and explicit.
- CLI handlers use one canonical constructor path.
- null sentinels are gone from the targeted boundaries.
- bootstrap failure-path tests are public-contract based.

---

## Suggested implementation order rationale

1. **Characterize the desired composition-root contracts first** so constructor cleanup is intentional rather than opportunistic.
2. **Collapse the `ProfileUseCases`/CLI compatibility seams next** because they are the noisiest pre-Kotlin construction paths.
3. **Extract the `ViewModelFactory` adapter cache** once the service-side roots are cleaner.
4. **Tighten app/service/API null contracts** before changing storage capability types, because these are the easiest explicit-contract wins.
5. **Make storage capabilities explicit in types** once the higher-level boundaries are already cleaner.
6. **Finish by rewriting bootstrap failure tests** so the refactor is protected by public-contract assertions.

## Risks and mitigations

- **Risk:** Over-cleaning could accidentally replace `ServiceRegistry` or `ViewModelFactory` instead of simplifying them.
  **Mitigation:** keep those classes as the production roots; only extract helper responsibilities or remove compatibility branches.

- **Risk:** Tightening storage capability contracts could create a large blast radius in tests.
  **Mitigation:** update `TestStorages` in the same task and keep the new operational interfaces narrow and production-driven.

- **Risk:** Replacing `null` with `Optional` in `ConnectionService` could cascade into UI or REST regressions.
  **Mitigation:** pin the use-case and route behavior first in `MessagingUseCasesTest` and `RestApiReadRoutesTest`.

- **Risk:** Exposing a test hook in `ApplicationStartup` could become a hidden production seam.
  **Mitigation:** keep it package-private and name it explicitly `...ForTests`.

- **Risk:** PostgreSQL runtime regressions could slip through if only H2-backed tests are run.
  **Mitigation:** end the plan with `run_postgresql_smoke.ps1` in addition to the full Maven gate.

## Self-review

- Spec coverage: all three requested workstreams are covered — composition-root cleanup, boundary contract tightening, and bootstrap test hardening.
- Placeholder scan: no `TODO`/`TBD` placeholders remain; every task has exact files, commands, and target code shapes.
- Type consistency: the plan consistently keeps `ServiceRegistry` and `ViewModelFactory` as roots, uses `Optional` for absence instead of `null`, and uses `Operational*Storage` interfaces for required runtime capabilities.
