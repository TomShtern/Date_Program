# Level 3 — Consolidation Implementation Plan

✅ IMPLEMENTED

> **Source:** [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) §Level 3
> **Created:** 2026-05-07
> **Scope:** 9 actionable items (2 invalidated against current code: L3.6 schema constants, L3.11 enum values).
> **Risk profile:** Medium. Items move file boundaries and may change public API shapes (REST/JSON).
> **Estimated net change:** ~6 files deleted, ~3 files added, ~–300 LOC.

---

## Progress Tracking
- As you finish each step, mark it `✅ IMPLEMENTED`.
- When the plan is fully implemented end-to-end, add `✅ IMPLEMENTED` immediately below the title at the top of this file.

## Pre-flight

1. The user manages all git operations. **Do not run `git` commands** at any point during implementation.
2. **Levels 1 and 2 must be merged first.** Several L3 items inherit assumptions from L2 (e.g., L3.2 inlines `RestRouteSupport` into `RestApiServer`, which is tighter post-L2.7). Confirm with the user before starting.
3. Run baseline:
   ```powershell
   mvn spotless:apply verify
   ```
4. **API consumer check.** Recent commits (`33a8b9d`) reference the `phone-alpha` Flutter client consuming the REST API. Before any item that changes JSON response shape (notably L3.8), confirm with the user whether the alpha client must remain compatible. If yes, version the response or defer.

---

## Conflict resolution: L3.7 vs L3.8

The original plan lists both items but they solve the *same* duplication using *opposite* strategies:

- **L3.7** adds a helper `ProfileCompletionDto.of(ProfileCompletionView)` that performs the 8-field unwrapping in one place. Result: still 8 fields per DTO, but the unwrapping code is centralized.
- **L3.8** has DTOs *embed* `ProfileCompletionView` rather than unwrapping it. Result: DTO JSON gains a nested `completion: {...}` object instead of inlined fields.

**Decision:** L3.8 is the deeper fix and supersedes L3.7. **Pick L3.8 if API shape is mutable; otherwise execute L3.7 as a fallback.** Do not do both — they are mutually exclusive.

The implementation plan documents L3.8 as the primary path and leaves L3.7 as an explicit fallback below.

---

## Sequencing

| Order | Item                                                        | Reason for placement                                                    |
|-------|-------------------------------------------------------------|-------------------------------------------------------------------------|
| 1     | L3.3 — Extract `RestApiExceptions.java`                     | Foundational; L3.4 references the exceptions                            |
| 2     | L3.4 — Route classifier (gate first)                        | Depends on L3.3; needs discipline gate                                  |
| 3     | L3.2 — Inline `RestRouteSupport`                            | Touches `RestApiServer`; do once API helpers are stable                 |
| 4     | L3.1 — Merge `InterestMatcher` + `LifestyleMatcher`         | Self-contained; matching layer only                                     |
| 5     | L3.9 — Merge `Read`/`Write` Pace DTOs                       | Small, isolated DTO change                                              |
| 6     | L3.5 — Consolidate small DTO files                          | **Verify line counts and domain coherence first**                       |
| 7     | L3.8 (or L3.7 fallback) — `ProfileCompletionView` embedding | API-shape decision; needs explicit go-ahead                             |
| 8     | L3.10 — Unify test fixture builders                         | Test-only; last so production changes are stable when re-running suites |

---

## Item 1 — Extract `RestApiExceptions.java` (L3.3)

✅ IMPLEMENTED

**Goal:** Move the four inner exception classes (`ApiForbiddenException`, `ApiUnauthorizedException`, `ApiConflictException`, `ApiTooManyRequestsException`) from `RestApiRequestGuards` to a standalone file.

**Audit confirmed:** Inner classes at `RestApiRequestGuards.java:202–231`. Used by `RestApiServer` and `RestApiIdentityPolicy`.

**Files affected:**
- ADD: `src/main/java/datingapp/app/api/RestApiExceptions.java`
- MODIFY: [src/main/java/datingapp/app/api/RestApiRequestGuards.java](../../../src/main/java/datingapp/app/api/RestApiRequestGuards.java) — remove the four inner classes
- MODIFY: [src/main/java/datingapp/app/api/RestApiIdentityPolicy.java](../../../src/main/java/datingapp/app/api/RestApiIdentityPolicy.java) — update imports
- MODIFY: [src/main/java/datingapp/app/api/RestApiServer.java](../../../src/main/java/datingapp/app/api/RestApiServer.java) — update imports

**Steps:**
1. Create `RestApiExceptions.java`:
   ```java
   package datingapp.app.api;

   /** Sealed-or-final exception holders for REST API error mapping. */
   public final class RestApiExceptions {
     public static final class ApiForbiddenException extends RuntimeException { ... }
     public static final class ApiUnauthorizedException extends RuntimeException { ... }
     public static final class ApiConflictException extends RuntimeException { ... }
     public static final class ApiTooManyRequestsException extends RuntimeException { ... }
     private RestApiExceptions() {}
   }
   ```
   Preserve existing constructor signatures, message handling, and serialVersionUID values from the originals.
2. Move each class body verbatim. Keep field/method visibility identical.
3. Delete the inner classes from `RestApiRequestGuards`.
4. In every file that referenced `RestApiRequestGuards.ApiForbiddenException` (etc.), update to `RestApiExceptions.ApiForbiddenException`. Use grep to confirm all sites:
   ```powershell
   rg "Api(Forbidden|Unauthorized|Conflict|TooManyRequests)Exception"
   ```
5. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=RestApiServerTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest test
```

**Risk:** Low. This is a pure mechanical move; behavior is unchanged.

**Discipline gate:** Met — 4 classes, ≥2 callers each, plus they cohere (all are HTTP-status-code exceptions).

---

## Item 2 — Centralize route classification (L3.4)

✅ IMPLEMENTED

**Goal:** Eliminate the duplicated `CONVERSATION_ROUTE_PREFIX` and `USERS_ROUTE_PREFIX` constants between `RestApiRequestGuards` (lines 19–23) and `RestApiIdentityPolicy` (lines 20–21). The audit found `AUTH_ROUTE_PREFIX` only in `RestApiRequestGuards`, so it's not duplicated.

**Audit caveat:** Only 2 of the 3 originally claimed prefixes are actually duplicated. The discipline gate (≥3 callers per shared item) is borderline — applying it strictly suggests *not* creating a new `RestApiRouteClassifier` class. Instead, pick one file as the owner and have the other reference it.

**Decision tree:**

```
Are >=3 prefixes shared?              No -> Inline-share via package-private constants in one file
                                        Yes -> Create RestApiRouteClassifier
```

Audit answer: 2 prefixes shared → **inline-share**. Skip creating a new classifier class.

**Files affected:**
- MODIFY: [src/main/java/datingapp/app/api/RestApiRequestGuards.java](../../../src/main/java/datingapp/app/api/RestApiRequestGuards.java) — make the two shared constants `package-private static final`
- MODIFY: [src/main/java/datingapp/app/api/RestApiIdentityPolicy.java](../../../src/main/java/datingapp/app/api/RestApiIdentityPolicy.java) — delete its local copies, import from `RestApiRequestGuards`

**Steps:**
1. In `RestApiRequestGuards`, change the access modifier on `CONVERSATION_ROUTE_PREFIX` and `USERS_ROUTE_PREFIX` from `private` to package-private (default).
2. In `RestApiIdentityPolicy`, remove the duplicate constants and reference `RestApiRequestGuards.CONVERSATION_ROUTE_PREFIX` / `RestApiRequestGuards.USERS_ROUTE_PREFIX` instead.
3. Verify no third file has its own copies:
   ```powershell
   rg "CONVERSATION_ROUTE_PREFIX|USERS_ROUTE_PREFIX|AUTH_ROUTE_PREFIX"
   ```

**Verification:**
```powershell
mvn -Dtest=RestApiRequestGuardsTest,RestApiIdentityPolicyTest test
```

**Risk:** Low. Constants are string literals; reference change is mechanical.

**Out of scope:**
- Building a `RouteCategory` enum or `RestApiRouteClassifier` class — discipline gate says no with only 2 callers.
- Touching the route-matching *logic* (split between guards/policy intentionally; that's an L5 architectural concern).

---

## Item 3 — Inline `RestRouteSupport` into `RestApiServer.start()` (L3.2)

✅ IMPLEMENTED

**Goal:** Delete `RestRouteSupport.java`. The audit confirms it's a thin wrapper around `RestApiServer.start()`-time route registration.

**Audit confirmed:** `RestRouteSupport.java` (lines 6–33) — its `registerRoutes()` is called from `RestApiServer`.

**Files affected:**
- DELETE: `src/main/java/datingapp/app/api/RestRouteSupport.java`
- MODIFY: [src/main/java/datingapp/app/api/RestApiServer.java](../../../src/main/java/datingapp/app/api/RestApiServer.java) — fold the route registration calls inline

**Steps:**
1. Read `RestRouteSupport.registerRoutes()` and its private helpers. Note the route categories: health, auth, user, photo, location, matching, social, messaging, profile-note.
2. In `RestApiServer.start()`, replace the `RestRouteSupport.registerRoutes(...)` call with the equivalent inlined registration. **Preserve the TRANSPORT NOTE comment** that the plan flags — it documents the loopback-only binding decision.
3. If a private helper's body is large enough that inlining bloats `start()`, leave it as a private method on `RestApiServer` instead of moving the body into `start()` directly. Goal: net file count reduction without a 600-line `start()`.
4. Delete `RestRouteSupport.java`.
5. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=RestApiPhaseOneTest,RestApiPhaseTwoRoutesTest test
```

**Risk:** Medium. If the inline form makes `RestApiServer.start()` exceed ~150 lines, the readability win evaporates. **Stop and reconsider** if so — keeping `RestRouteSupport` may be the better outcome despite the file count.

**Acceptance criterion:** `RestApiServer.start()` post-inline is under 200 lines, OR `RestRouteSupport` survives with a docstring explaining why.

---

## Item 4 — Merge `InterestMatcher` + `LifestyleMatcher` → `PreferencesMatcher` (L3.1)

✅ IMPLEMENTED

**Goal:** Both are stateless static utility classes used by `MatchQualityService` and `CompatibilityCalculator`. Merging into a single `PreferencesMatcher` reduces matching-layer fragmentation.

**Audit confirmed:**
- `InterestMatcher.java:24` — final class with private constructor; computes interest overlap/Jaccard.
- `LifestyleMatcher.java:10` — final class with `safeCopy` calls; provides dealbreaker checking and lifestyle equality.

**Files affected:**
- ADD: `src/main/java/datingapp/core/matching/PreferencesMatcher.java`
- DELETE: `src/main/java/datingapp/core/matching/InterestMatcher.java`
- DELETE: `src/main/java/datingapp/core/matching/LifestyleMatcher.java`
- MODIFY: callers — `MatchQualityService.java`, `CompatibilityCalculator.java`, plus any test fixtures

**Steps:**
1. Create `PreferencesMatcher.java` and copy both classes' static methods into it. Preserve method names verbatim (callers won't need re-binding logic).
2. Update imports in `MatchQualityService` and `CompatibilityCalculator`:
   ```powershell
   rg "InterestMatcher|LifestyleMatcher"
   ```
   Update each occurrence.
3. Delete the two source files.
4. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=PreferencesMatcherTest,MatchQualityServiceTest,CompatibilityCalculatorTest test
```

If `InterestMatcherTest.java` and `LifestyleMatcherTest.java` exist as separate files, either rename them to `PreferencesMatcherTest.java` (one file) or keep them as two test files that exercise the same target — the latter is fine if test class size is the concern.

**Risk:** Low. Stateless static merge with no shared state. The only failure mode is import errors that the compiler catches immediately.

**Discipline gate:** Met — 2 source classes, 2+ callers each, semantically related (both are user-preference matchers).

---

## Item 5 — Merge `ReadPacePreferencesDto` + `WritePacePreferencesDto` → `PacePreferencesDto` (L3.9)

✅ IMPLEMENTED — Option B (`PacePreferencesDtos.Read` / `PacePreferencesDtos.Write`)

**Goal:** Two DTOs with identical 4-field structure (`messagingFrequency`, `timeToFirstDate`, `communicationStyle`, `depthPreference`) but different field types (Strings vs enums).

**Audit confirmed:**
- `ReadPacePreferencesDto` in `RestApiUserDtos.java:48–64` — read-side, String types.
- `WritePacePreferencesDto` (labeled `PacePreferencesEditableDto`) in `ProfileDtos.java:220–236` — write-side, enum types with `toPacePreferences()` converter.

**Files affected:**
- MODIFY: `RestApiUserDtos.java` and `ProfileDtos.java`
- POSSIBLY ADD: a new shared `PacePreferencesDto.java` if the merged shape doesn't fit cleanly inside either existing file

**Critical decision:** **JSON serialization shape determines feasibility.** Read-side String types and write-side enum types may be intentional — the read side returns "labels" (e.g., `"FAST"`) while the write side accepts the canonical enum values. If the JSON contracts differ, do NOT force a single bidirectional DTO; instead:

- **Option A (preferred if shapes match):** Single `PacePreferencesDto` with enum-typed fields. Jackson will (de)serialize enums as strings on the wire. Both sides converge on this DTO.
- **Option B (if shapes differ):** Keep both DTOs but consolidate them into a single nested `PacePreferences` namespace inside one file:
  ```java
  public final class PacePreferences {
    public record Read(String messagingFrequency, ...) { }
    public record Write(MessagingFrequency messagingFrequency, ...) {
      public PacePreferencesValue toDomain() { ... }
    }
  }
  ```
  This still reduces file count to 1 and clarifies the read/write asymmetry.

**Implemented outcome:** The live code uses `src/main/java/datingapp/app/api/PacePreferencesDtos.java` as the shared namespace file. `RestApiUserDtos` consumes `PacePreferencesDtos.Read`, while `ProfileDtos` uses `PacePreferencesDtos.Write` for update requests and `PacePreferencesDtos.Read` for read/edit snapshot flows.

**Steps:**
1. Confirm wire-shape with a test: write a `Read` DTO to JSON; write a `Write` DTO to JSON; verify they would round-trip if unified. If they would, choose Option A. Otherwise Option B.
2. Refactor accordingly. Update callers.
3. If `WritePacePreferencesDto.toPacePreferences()` is critical, preserve the converter under the same or equivalent name.

**Verification:**
```powershell
mvn -Dtest=ProfileDtosTest,RestApiUserDtosTest,RestApiPhaseOneTest,RestApiPhaseTwoRoutesTest test
```

**Risk:** Medium. Wrong choice between A and B silently changes the JSON contract. Step 1 is the guard.

---

## Item 6 — Consolidate small DTO files (L3.5)

✅ REVIEWED — NO CHANGE (cohesion gate rejected consolidation)

**Goal:** Group the 6 smallest DTO files in `app/api/` to reduce file count.

**Audit caveat:** Plan's claimed line counts are wrong:
- `RestApiDtos`: 11 (claimed 14)
- `MessageDtos`: 22 (claimed 28)
- `NotificationDtos`: 30 (claimed 34)
- `VerificationDtos`: 30 (claimed 36)
- `AuthDtos`: 28 (claimed 35)
- `PhotoDtos`: 32 (claimed 39)

Total: 153 lines across 6 files — perfectly reasonable to consolidate, but the plan as written doesn't say *into what*. The naming "`RestApiDtos` (14 lines)" lists `RestApiDtos` as one of the inputs, not the target — so we need to choose a target.

**Critical question:** Are these DTOs *cohesive*? "Small" is not a domain. A mega-`MiscApiDtos.java` mixing message DTOs with verification DTOs and notification DTOs would be **worse** than the current state.

**Cohesion check (run before any moves):**
For each of the 6 files, ask:
- Does its DTO type only appear in one route family? (e.g., MessageDtos → messaging routes only.)
- Are the DTOs read-only (response shapes) or read-write (also used for request bodies)?
- Could two of these files be merged based on *domain*, not size?

**Likely groupings (verify case-by-case):**
- **Group A — auth/identity:** `AuthDtos` + parts of `RestApiDtos` (if `RestApiDtos` holds the generic `ErrorResponse`-style records).
- **Group B — moderation/safety:** `VerificationDtos` (alone is fine if no peer exists).
- **Group C — messaging/notifications:** `MessageDtos` + `NotificationDtos` only if their record names don't collide.
- **Group D — photos:** `PhotoDtos` (alone, already cohesive).

**Recommendation:** Merge only where domain coherence is clear. Likely outcome: 6 files → 4 files (saving 2), not 6 → 1.

**Implemented outcome:** No merge was applied. The cohesion check rejected consolidation because `AuthDtos`, `MessageDtos`, `NotificationDtos`, `VerificationDtos`, `PhotoDtos`, and `RestApiDtos` are already clean, domain-shaped files. Merging them would have created the junk-drawer structure this item was meant to avoid.

**Files affected:** Up to 6 deleted, up to 3 added — exact list determined by the cohesion check.

**Steps:**
1. List the type names in each file:
   ```powershell
   rg -n "public record |public final class " src/main/java/datingapp/app/api/AuthDtos.java src/main/java/datingapp/app/api/MessageDtos.java src/main/java/datingapp/app/api/NotificationDtos.java src/main/java/datingapp/app/api/VerificationDtos.java src/main/java/datingapp/app/api/PhotoDtos.java src/main/java/datingapp/app/api/RestApiDtos.java
   ```
2. Group by domain affinity (Steps above).
3. For each group with ≥2 members, create the merged file. Move records verbatim. Delete the originals.
4. Update imports across the project:
   ```powershell
   rg "import datingapp\.app\.api\.(AuthDtos|MessageDtos|NotificationDtos|VerificationDtos|PhotoDtos|RestApiDtos)"
   ```
5. Run Spotless.

**Verification:**
```powershell
mvn -Dtest=RestApiPhaseOneTest,RestApiPhaseTwoRoutesTest test
```

**Risk:** Medium. If domain coherence is misjudged, the merged file becomes a junk drawer that future reviewers fight against. The pre-merge cohesion check is the load-bearing step.

**Acceptance criterion:** Each merged file has a clear, single-domain name (e.g., `MessagingDtos`, not `MiscDtos`). If you can't pick a clean name, the merge is wrong — revert and leave the file alone.

---

## Item 7 — Embed `ProfileCompletionView` in DTOs (L3.8) — supersedes L3.7

✅ IMPLEMENTED VIA L3.7 FALLBACK — flat API contract preserved

**Goal:** Stop unpacking `ProfileCompletionView`'s 8 fields into every DTO that needs them. Instead, embed the view as a single nested object (`completion: { ... }`).

**Implemented outcome:** The deeper L3.8 JSON-shape change was not taken. The live code uses `ProfileCompletionDto.of(ProfileCompletionView)` to centralize the current flat completion mapping without changing the wire contract. In current source this helper mirrors the live 6-field completion contract, not the older 8-field description in the original audit text.

**Audit confirmed:**
- `UserDetail` (in `RestApiUserDtos.java:102–157`) defines its own copy of the 8 fields.
- `ProfileEditSnapshotDto` (in `ProfileDtos.java:118–154`) does the same.
- 6 call sites manually unpack: `RestApiUserDtos:152–157`, `ProfileDtos:148–153`, plus 4 more in `RestApiServer` (photo upload/delete/reorder, etc.).

**API-shape concern:** This change adds a `completion: { missingProfileFields: [...], ... }` nested object to JSON responses, replacing 8 inline fields. Any client (including the live `phone-alpha` Flutter alpha) that reads those fields directly will break.

**Decision gate (BEFORE implementing):**
- ✅ If the team can break the alpha API contract OR the alpha client doesn't read these fields → proceed with L3.8 below.
- ❌ If the alpha client must keep working unchanged → skip L3.8 and execute **L3.7 fallback** (next section).

**Files affected (L3.8 path):**
- MODIFY: `RestApiUserDtos.java` — `UserDetail` embeds `ProfileCompletionView` instead of unpacking.
- MODIFY: `ProfileDtos.java` — `ProfileEditSnapshotDto` embeds `ProfileCompletionView`.
- MODIFY: `RestApiServer.java` — photo handlers return `completion` directly, no unwrap step.
- MODIFY: API tests that asserted on the 8 inline fields — update to read `completion.missingProfileFields()` etc.
- POSSIBLY MODIFY: phone-alpha API consumer code (out of scope for this repo, but flag in the PR).

**Steps:**
1. Add `ProfileCompletionView` (or a public DTO mirror) as a field on `UserDetail` and `ProfileEditSnapshotDto`. Replace the 8 individual fields.
2. In `RestApiServer.java`, replace `completion != null ? completion.missingProfileFields() : null` (and the other 7 unwraps) with passing the whole view through.
3. Update Jackson serialization if needed (record types should serialize automatically).
4. Update API tests to read from the nested object.
5. Add an entry to `docs/API-SPECIFICATION.md` documenting the shape change.

**Verification:**
```powershell
mvn -Dtest=ProfileCompletionViewTest,RestApiPhaseOneTest,RestApiPhaseTwoRoutesTest,UserDetailTest,ProfileEditSnapshotDtoTest test
```

**Risk:** High for API consumers; low for internal correctness.

---

### L3.7 Fallback — `ProfileCompletionDto.of()` helper

✅ IMPLEMENTED

If L3.8 is rejected (API shape must remain stable):

**Goal:** Same duplication problem; smaller solution. Add a static factory method that does the 8-field unwrapping in one place.

**Files affected:**
- ADD or MODIFY: a `ProfileCompletionDto` (or static helper on `ProfileCompletionView`) with method `public static ProfileCompletionDto of(ProfileCompletionView view) { ... }` returning a record that holds the 8 inline fields.
- MODIFY: 6 sites — each replaces its inline unwrap with `ProfileCompletionDto.of(completion)`.

**Steps:**
1. Define the helper method.
2. Replace each manual unwrap with the helper call. The DTOs (`UserDetail`, `ProfileEditSnapshotDto`) still hold 8 inline fields — wire them from the helper's record.

**Verification:** Same as L3.8.

**Risk:** Low. JSON contract unchanged; only deduplicates the unwrap code.

**Out of scope:** The deeper structural fix (L3.8). L3.7 is intentionally a band-aid.

---

## Item 8 — Unify `TestServiceRegistryBuilder` + `RestApiTestFixture.Builder` (L3.10)

✅ IMPLEMENTED

**Goal:** Merge two test-builder classes that have overlapping responsibilities for wiring `ServiceRegistry` with test storages.

**Audit confirmed:**
- `TestServiceRegistryBuilder.buildWithStorages()` in `core/testutil/`.
- `RestApiTestFixture.Builder.build()` in `app/api/` test support.
- Overlap: matching, messaging, profile, achievement service wiring.
- `RestApiTestFixture` adds: auth token generation, standout/undo storage customization.

**Files affected:**
- MODIFY: [src/test/java/datingapp/core/testutil/TestServiceRegistryBuilder.java](../../../src/test/java/datingapp/core/testutil/TestServiceRegistryBuilder.java) — gain extension points for REST-specific additions.
- MODIFY: [src/test/java/datingapp/app/api/RestApiTestFixture.java](../../../src/test/java/datingapp/app/api/RestApiTestFixture.java) — delegate the shared parts to `TestServiceRegistryBuilder`; keep only REST-specific helpers.

**Steps:**
1. Read both builders. Identify the shared core (storage wiring, achievement registry, etc.) vs the RestApi-specific extras (auth tokens, standout customization).
2. In `TestServiceRegistryBuilder`, add:
   - Optional setters for storages that `RestApiTestFixture` customizes (e.g., `withStandoutStorage(...)`, `withUndoStorage(...)`).
   - A way to expose the built `ServiceRegistry` AND `RestApiTestFixture`-specific bits like the auth token generator.
3. In `RestApiTestFixture.Builder`, replace the duplicated wiring with delegation to `TestServiceRegistryBuilder`. Keep the auth-token + REST-specific code.
4. Confirm no test depends on the *order* of service construction (rare but possible if shutdown hooks fire in registration order).

**Implemented outcome:** `RestApiTestFixture.Builder` now delegates shared service-graph creation to `TestServiceRegistryBuilder.builder(...)` while keeping the REST-facing bearer-token helper, REST-specific storage knobs, and post-build event-handler registration local.

**Verification:**
```powershell
mvn test
```
Run the full suite — both builders are touched by hundreds of tests indirectly.

**Risk:** Medium. Test-only change, but a misstep here cascades into many tests failing for confusing reasons. Bisect with small commits if regressions appear.

**Out of scope:** Do not introduce a third "unified mega-builder" class. Keep `TestServiceRegistryBuilder` as the canonical, with `RestApiTestFixture.Builder` as a thin extension.

---

## Final verification gate

✅ VERIFIED ON 2026-05-08

After all items merged:

```powershell
mvn spotless:apply verify
.\run_postgresql_smoke.ps1
```

Expected: tests pass; ~6 files deleted; ~3 files added; LOC trimmed by ~300.

**Actual result:**
- `mvn spotless:apply verify` passed.
- `run_postgresql_smoke.ps1` passed.
- The small-DTO consolidation item was intentionally skipped by the cohesion gate, so the actual production-source file delta differs from the original estimate.

---

## Commit message drafts — for the user

The user handles all git operations. **Do not run `git commit`.** After each phase below completes (verification gate green), draft the corresponding commit message in chat as plain text and **STOP**, asking the user whether to proceed. The user will commit on their own cadence and tell you to continue.

Phases group items that share files or risk profile, so each commit stays reviewable:

### Phase A — REST API exception/route plumbing (Items 1–3)

After Items 1, 2, and 3 are complete and `mvn -Dtest=RestApi*Test test` passes:

```text
refactor(api): consolidate exception types and route plumbing

- Extract RestApiExceptions.java (ApiForbidden/Unauthorized/Conflict/
  TooManyRequestsException) from RestApiRequestGuards inner classes;
  update imports in RestApiServer and RestApiIdentityPolicy
- Centralize the duplicated CONVERSATION_ROUTE_PREFIX and
  USERS_ROUTE_PREFIX constants on RestApiRequestGuards;
  RestApiIdentityPolicy now references the package-private copies
- Inline RestRouteSupport.registerRoutes into RestApiServer.start
  (or its private helpers); delete RestRouteSupport.java. The
  TRANSPORT NOTE comment is preserved in RestApiServer.
```

### Phase B — matching helper merge (Item 4)

```text
refactor(matching): merge InterestMatcher and LifestyleMatcher

Both were stateless static utility classes used by the same two
consumers. Folded into PreferencesMatcher; old files removed and
imports updated in MatchQualityService and CompatibilityCalculator.
```

### Phase C — Pace DTO unification (Item 5)

After Item 5:

```text
refactor(api): unify Read/Write Pace preferences DTO

[Choose one based on which option the implementation took:]
- Option A: collapse onto a single bidirectional PacePreferencesDto
  (enum-typed fields; Jackson handles enum<->string)
- Option B: nest Read and Write records under a shared PacePreferences
  namespace in one file, preserving the existing JSON contracts
```

The implementer should pick the bullet that matches the chosen path before presenting.

### Phase D — small-DTO consolidation (Item 6)

Only after the cohesion check has produced clean, single-domain merged file names:

```text
refactor(api): consolidate small DTO files by domain

Reduces app/api/ DTO file count by merging files with strong domain
affinity. Each merged file has a single-domain name; no junk-drawer
"misc" files. Imports updated across callers.
```

If the cohesion check rejected all merges, skip this phase and tell the user.

### Phase E — ProfileCompletionView refactor (Item 7)

After the user confirms which path was taken:

**If L3.8 (deeper fix, JSON-shape change):**
```text
refactor(api): embed ProfileCompletionView in completion-aware DTOs

UserDetail and ProfileEditSnapshotDto now embed ProfileCompletionView
directly instead of unpacking its eight fields. RestApiServer photo
handlers return the view as a nested object.

API change: completion fields move from inline keys to a nested
"completion" object. docs/API-SPECIFICATION.md updated.
```

**If L3.7 fallback (preserve JSON shape):**
```text
refactor(api): centralize ProfileCompletionView unwrapping

Adds ProfileCompletionDto.of(view) helper. Six call sites that
manually unpacked the eight completion fields now delegate to the
helper. JSON contract unchanged.
```

### Phase F — test fixture builder unification (Item 8)

```text
refactor(test): unify ServiceRegistry test builders

RestApiTestFixture.Builder now delegates the shared service-graph
wiring to TestServiceRegistryBuilder; only REST-specific extras
(auth tokens, standout/undo storage customization) remain locally.
```

If implementation diverged from any draft (e.g., a discipline gate failed and the helper class was not added), edit the bullets to reflect what actually shipped before presenting to the user.

---

## Out of scope (per audit)

- L3.6 (44 schema constants) — invalidated; only ~25 exist and `SchemaInitializer` doesn't duplicate them.
- L3.11 (`SchemaEnumValues`) — invalidated; neither file uses Java enums for CHECK constraints.
- Building `RestApiRouteClassifier` — discipline gate fails (only 2 prefixes shared).
- Forcing a single `MiscApiDtos.java` — the cohesion check rejects this if no clear domain emerges.

## Definition of done

- [x] All 9 actionable items merged or explicitly invalidated.
- [x] L3.7 vs L3.8 decision documented in commit/PR.
- [x] `mvn spotless:apply verify` passes.
- [x] No merged DTO file mixes domains (cohesion check).
- [x] Actual production-source file delta documented: item 6 was skipped by the cohesion gate, so the shipped delta is 3 deleted vs 4 added → net +1 file.
- [x] If `RestApiServer.start()` exceeds 200 lines after L3.2, document why or revert.
