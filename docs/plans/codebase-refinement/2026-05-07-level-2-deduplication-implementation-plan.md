# Level 2 — Deduplication Implementation Plan

> **Source:** [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) §Level 2
> **Created:** 2026-05-07
> **Scope:** 12 items. Two have audit caveats (L2.4 PagedQuery, L2.9 BaseController callers) and require a verification step before implementing.
> **Risk profile:** Low–medium. Most items are mechanical replacements; the two new-abstraction items (PagedQuery, RestApiUtils) need a discipline gate to avoid premature consolidation.
> **Estimated net change:** ~1 file deleted, ~4 added, ~–450 LOC.

---

## Pre-flight

1. The user manages all git operations. **Do not run `git` commands** at any point. Branching, staging, committing, reverting are entirely the user's responsibility.
2. **Level 1 must be merged first.** Several Level 2 items touch files Level 1 modifies (`MatchingService`, `TrustSafetyService`, `EnumSetUtil` neighbors). Confirm with the user that Level 1 is committed before starting.
3. Run baseline:
   ```powershell
   mvn spotless:apply verify
   ```

---

## Discipline gate: when NOT to extract

Before adding any new utility class (`UiStyles`, `EventPublishing`, `PagedQuery`, `RestApiUtils`), confirm at least **3 distinct, real-world callers** exist. Two callers is a coincidence; three is a pattern. If a proposed helper turns out to have only 2 callers after audit, **inline the second call site instead of extracting** — that satisfies "deduplicate" without growing the file count.

This gate especially applies to L2.4 (PagedQuery) and the reduced-scope L2.6 (RestApiUtils).

---

## Sequencing

| Order | Item                                             | Reason for placement                           |
|-------|--------------------------------------------------|------------------------------------------------|
| 1     | L2.5 — bind helpers → `JdbiTypeCodecs`           | Storage-internal, low blast radius             |
| 2     | L2.8 — `UiStyles.getThemeUrl()`                  | 5 mechanical replacements; pure UI plumbing    |
| 3     | L2.11 — distance calculation delegation          | 3 mechanical call-site changes                 |
| 4     | L2.2 — `Match.copy()`                            | Adds one method; replaces 4 constructor calls  |
| 5     | L2.12 — `Match.isInvalidTransition` delegation   | Single-method redirect                         |
| 6     | L2.6 — REST API utility (reduced scope)          | Just `isLoopback` + `normalizeSharedSecret`    |
| 7     | L2.1 — `ValidationService` → `TextNormalization` | Larger surface, but well-isolated              |
| 8     | L2.3 — `EventPublishing.publishOrWarn`           | 4 use-case files                               |
| 9     | L2.10 — canonical record types                   | Touches `RecommendationService` public API     |
| 10    | L2.9 — `BaseController` logger                   | 5 controllers; verify call list first          |
| 11    | L2.4 — `PagedQuery<T>`                           | **Verification step required** before any code |
| 12    | L2.7 — REST route handler template               | Largest item; do last when L2.6 is in place    |

---

## Item 1 — Move `bindNullableUuid` / `bindNullableInstant` into `JdbiTypeCodecs` (L2.5)

**Goal:** Promote two helpers currently duplicated in `JdbiMatchmakingStorage` and `JdbiMetricsStorage` into the shared `JdbiTypeCodecs` utility class.

**Audit confirmed:**
- `JdbiMatchmakingStorage.java:1137–1153` defines both methods.
- `JdbiMetricsStorage.java:718` defines `bindNullableInstant`.
- `JdbiTypeCodecs.java` has null-safe row *readers* but no `bindNullable*` *binders*.
- `JdbiAuthStorage` uses inline `bindNull(... Types.X)` rather than the same shape — leave it alone for now.

**Files affected:**
- MODIFY: [src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java](../../../src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java) — add public static `bindNullableUuid(SqlStatement<?>, String, UUID)` and `bindNullableInstant(SqlStatement<?>, String, Instant)`
- MODIFY: [src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java](../../../src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java) — remove local helpers, switch call sites to `JdbiTypeCodecs.bindNullableX`
- MODIFY: [src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java](../../../src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java) — same

**Steps:**
1. Copy the existing method bodies (they are identical) into `JdbiTypeCodecs` with `public static` modifiers. Use the exact same SQL type constants (`Types.OTHER` vs `Types.TIMESTAMP_WITH_TIMEZONE`) — these are dialect-relevant.
2. In the two storage classes, replace each call to the local method with `JdbiTypeCodecs.bindNullableUuid(...)` / `JdbiTypeCodecs.bindNullableInstant(...)`.
3. Delete the local method definitions.
4. Remove now-unused imports.

**Verification:**
```powershell
mvn -Dtest=JdbiMatchmakingStorageTest,JdbiMetricsStorageTest,JdbiTypeCodecsTest test
.\run_postgresql_smoke.ps1     # Optional: confirms PostgreSQL bindings still round-trip
```

**Risk:** Low. The method is a pass-through — same `Types.X` constants applied via the same JDBI APIs.

---

## Item 2 — `UiStyles.getThemeUrl()` (L2.8)

**Goal:** Centralize the 5 inline `getClass().getResource("/css/theme.css").toExternalForm()` calls behind one helper.

**Audit confirmed:** 5 call sites in `UiUtils`, `UiDialogs`, `UiFeedbackService`, `MatchingController`, `NavigationService`.

**Files affected:**
- ADD: `src/main/java/datingapp/ui/UiStyles.java` — `public final class UiStyles { public static String getThemeUrl() { ... } }`
- MODIFY (5 sites): `UiUtils.java`, `UiDialogs.java`, `UiFeedbackService.java`, `MatchingController.java`, `NavigationService.java`

**Steps:**
1. Create `UiStyles.java` in `ui/` (the project keeps UI utilities flat — confirmed by CLAUDE.md memory). Single-method class; private constructor.
2. Method body should match the existing pattern verbatim:
   ```java
   public static String getThemeUrl() {
     return UiStyles.class.getResource("/css/theme.css").toExternalForm();
   }
   ```
   Note: use `UiStyles.class` rather than `getClass()` since this is static.
3. In each of the 5 sites, replace the inline call with `UiStyles.getThemeUrl()`. Some sites may use `getClass()` because they are inside a JavaFX `Application` or `Controller`; the static replacement still works.
4. Audit for any *other* `theme.css` lookups using a different pattern (e.g., `Resources.getResource(...)`):
   ```powershell
   rg "theme\.css" --glob "*.java"
   ```
   If you find a 6th site, add it. If not, the discipline gate is satisfied (>=3).

**Verification:**
```powershell
mvn -Dtest=NavigationServiceTest,UiUtilsTest test
mvn javafx:run    # Manual smoke: dashboard renders with theme applied
```

**Risk:** Low. The behavior is identical; resource lookup uses the same classloader.

**Out of scope:** Do NOT inline arbitrary FXML or stylesheet paths. The plan only covers the literal `/css/theme.css` lookup.

---

## Item 3 — Distance calculation delegation (L2.11)

**Goal:** Have `StandoutService`, `MatchQualityService`, and `BrowseRankingService` delegate to the canonical `GeoUtils.distanceKm()` (or `CompatibilityCalculator.calculateDistanceScore()` if they need the score, not the raw distance).

**Audit confirmed:**
- Canonical: `GeoUtils.distanceKm()` at line ~42 (in `datingapp.location` package, NOT `core/model/`).
- `CompatibilityCalculator.calculateDistanceScore()` at line ~164 — scales distance, takes `distanceKm` as a parameter; does not reimplement Haversine.

**Files affected:**
- MODIFY: `StandoutService.java`, `MatchQualityService.java`, `BrowseRankingService.java`

**Verification before implementing:**
For each of the three target files, read the actual distance code. The audit said they "reference distance calculation" but did not confirm full Haversine reimplementations. If the file already calls `GeoUtils.distanceKm` and only the *scoring* differs, this item is a no-op for that file — skip it.

```powershell
rg -A 10 "Math\.sin|Math\.cos|haversine|distance" src/main/java/datingapp/core/matching/StandoutService.java src/main/java/datingapp/core/matching/MatchQualityService.java src/main/java/datingapp/core/matching/BrowseRankingService.java
```

**Steps:**
1. For each file that *does* contain a Haversine reimplementation, replace it with `GeoUtils.distanceKm(latA, lonA, latB, lonB)`.
2. Delete the now-orphaned helper methods.
3. Update imports.

**Verification:**
```powershell
mvn -Dtest=StandoutServiceTest,MatchQualityServiceTest,BrowseRankingServiceTest test
```

**Risk:** Floating-point edge cases. Haversine reimplementations sometimes round differently from the canonical version (e.g., slightly different Earth-radius constant). Read the canonical `GeoUtils.distanceKm()` and confirm the constant matches what the three services were using; if they diverge, document the choice (or pick the more accurate constant).

---

## Item 4 — `Match.copy()` (L2.2)

**Goal:** Add a `copy()` method to `Match` mirroring `User.copy()`. Replace 4 call sites that reconstruct `Match` via the full constructor.

**Audit confirmed:** 4 call sites at:
- `ConnectionService.java:614`
- `TrustSafetyService.java:418`
- `JdbiMatchmakingStorage.java:1091`
- `MatchingUseCases.java:789`

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/model/Match.java](../../../src/main/java/datingapp/core/model/Match.java) — add `public Match copy() { ... }` mirroring `User.copy()`'s shape (likely a builder-or-record-style copy).
- MODIFY: 4 call sites listed above.

**Steps:**
1. Read `User.copy()` to learn the established pattern. If `User` uses a `StorageBuilder` chain, `Match` should too — but `Match` may already be a record-like class with simpler structure. Adopt the simpler form if possible.
2. Add `Match.copy()`. If `Match` is a record, `copy()` simply returns a new instance with all current fields. If `Match` is a regular class, decide between:
   - A `with*(...)` builder (one method per mutable axis) — useful if call sites are mutating individual fields.
   - A plain `copy()` returning an identical instance — useful if call sites use it as a defensive snapshot.
   Read the 4 call sites to determine which shape is needed.
3. Replace each `new Match(...)` reconstruction with `existing.copy()` (and any subsequent mutations).
4. **Important:** L5.21 flags a separate issue with `User.copy()` re-triggering normalization. Verify `Match.copy()` does not have a similar normalization seam — if the constructor normalizes, the copy must bypass that.

**Verification:**
```powershell
mvn -Dtest=MatchTest,ConnectionServiceTest,TrustSafetyServiceTest,JdbiMatchmakingStorageTest,MatchingUseCasesTest test
```

**Risk:** Medium. If `Match`'s constructor enforces invariants (e.g., normalizes user IDs, validates `MatchState`), `copy()` must apply the same invariants. The safest implementation calls the same constructor — so re-validation is guaranteed but pays the cost again.

---

## Item 5 — `Match.isInvalidTransition` delegation (L2.12)

**Goal:** `Match.isInvalidTransition(MatchState from, MatchState to)` currently has a hardcoded switch. Have it delegate to `RelationshipWorkflowPolicy.canTransition(...)`.

**Audit confirmed:** Both implementations exist; both encode the same state machine.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/model/Match.java](../../../src/main/java/datingapp/core/model/Match.java) — `isInvalidTransition(...)` body becomes `return !RelationshipWorkflowPolicy.canTransition(this, to);` (or similar — read both signatures first).

**Verification before implementing:**
- Confirm `RelationshipWorkflowPolicy.canTransition(...)` is in `core/` (not in an upper layer that would cause `core/model/` → `core/workflow/` reverse import issues).
- Confirm `RelationshipWorkflowPolicy` has no dependencies that `Match` cannot legally import.

**Steps:**
1. Read both methods. Confirm semantic equivalence: every `(from, to)` pair classified as invalid by `Match.isInvalidTransition` must produce `canTransition(...) == false` in `RelationshipWorkflowPolicy`.
2. If equivalence holds, replace the switch with delegation.
3. If equivalence does NOT hold (e.g., `RelationshipWorkflowPolicy` is more permissive in some cases), STOP and treat this as a Level 5 item — there's a real semantic decision to make.

**Verification:**
```powershell
mvn -Dtest=MatchTest,RelationshipWorkflowPolicyTest test
```

**Risk:** Medium. If the two implementations have drifted, the delegation silently changes match-state legality. Step 1's equivalence check is the critical guard.

---

## Item 6 — REST API utility (reduced scope) (L2.6)

**Goal:** Per the audit, only two of the originally listed sub-items remain valid:
- Unify `RestApiServer.isLoopbackHost` and `RestApiRequestGuards.isLoopbackAddress` into one helper.
- Have `RestApiServer` pass the already-normalized shared secret into `RestApiRequestGuards`, removing the redundant second `normalizeSharedSecret()` call.

The original `parseUuid` and `extractBearerToken` items are **invalidated** — see [CODEBASE_REFINEMENT_PLAN.md](./CODEBASE_REFINEMENT_PLAN.md) appendix.

**Files affected:**
- ADD: `src/main/java/datingapp/app/api/RestApiUtils.java` — only created if the discipline gate is satisfied (≥3 callers across the package). With only `isLoopback` to share, **2 callers**, the gate is *not* met. **Recommended path:** put `isLoopback` as a `package-private static` method directly on the file that has the more general implementation, and make the other file call it. Skip creating `RestApiUtils.java`.
- MODIFY: `RestApiServer.java`, `RestApiRequestGuards.java`

**Steps:**
1. Compare `isLoopbackHost` and `isLoopbackAddress`. Merge to the more permissive/correct version (likely the one with IPv6 handling).
2. Pick a host file. Suggested: `RestApiRequestGuards` since it already owns network-level concerns. Make the method package-private.
3. Remove the duplicate from the other file; route its callers to the consolidated method.
4. For the `normalizeSharedSecret()` redundancy: in `RestApiServer.start()` (or wherever the secret is first normalized), pass the normalized value into `RestApiRequestGuards`'s constructor or initialization. Remove `RestApiRequestGuards`'s second normalization call.

**Verification:**
```powershell
mvn -Dtest=RestApiServerTest,RestApiRequestGuardsTest test
```

**Risk:** Low. Two pure functions and one constructor parameter change.

**Notes:** If a future Level 3 item (L3.4: route classification) lands, that *may* reintroduce a shared `RestApi*` utility class. Don't pre-build it now — let real demand justify it.

---

## Item 7 — `ValidationService` email/phone delegation (L2.1)

**Goal:** Remove duplicate email/phone validation logic from `ValidationService.java` by routing through `TextNormalization.normalizeEmail()` / `normalizePhone()`. The `normalizePhotoUrl()` delegation already follows this pattern.

**Audit confirmed:** Identical constants `MAX_EMAIL_LENGTH`, `EMAIL_LOCAL_PATTERN`, `DOMAIN_LABEL_PATTERN`, `PHONE_ALLOWED_PATTERN`, `MIN_PHONE_DIGITS`, `MAX_PHONE_DIGITS` and methods `containsControlCharacters()`, `isValidAsciiDomain()` exist in both files.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/ValidationService.java](../../../src/main/java/datingapp/core/ValidationService.java)
- POSSIBLY MODIFY: [src/main/java/datingapp/core/TextNormalization.java](../../../src/main/java/datingapp/core/TextNormalization.java) if validation needs a slightly different return shape

**Steps:**
1. Read `ValidationService.validateEmail()` and `TextNormalization.normalizeEmail()`. Determine whether `normalizeEmail` already returns enough information (validity + canonical form) for `validateEmail` to delegate. If `normalizeEmail` only normalizes valid input and throws otherwise, you may need a `tryNormalizeEmail()` variant that returns `Optional<String>` or a Result type — add it if needed.
2. Same for `validatePhone()` ↔ `normalizePhone()`.
3. Have `ValidationService.validateEmail()` call into `TextNormalization`. Remove the duplicate constants.
4. Remove `containsControlCharacters()` and `isValidAsciiDomain()` from `ValidationService`. They remain in `TextNormalization` (where Level 1 Item 7 just added the `INVALID_EMAIL_FORMAT` constant).
5. Update any callers of the now-removed `ValidationService` private constants (there should be none — they were private).

**Verification:**
```powershell
mvn -Dtest=ValidationServiceTest,TextNormalizationTest test
```

Pay particular attention to error-message strings if tests do exact-match assertions; if `TextNormalization` and `ValidationService` returned slightly different messages before, decide which is canonical and adjust tests.

**Risk:** Medium. If the two implementations diverged on edge cases (length boundaries, Unicode handling, trailing-dot domains), delegation will change behavior. Run the full validation test suite before merging.

---

## Item 8 — `EventPublishing.publishOrWarn(...)` (L2.3)

**Goal:** Extract the 4 copies of the publish-with-warn try/catch wrapper into a single helper.

**Audit confirmed (4 copies):**
- `ProfileMutationUseCases.java:299–306`
- `MessagingUseCases.java:302–309`
- `ProfileNotesUseCases.java:169–176`
- `SocialUseCases.java:409–416`

**Files affected:**
- ADD: `src/main/java/datingapp/app/event/EventPublishing.java`
- MODIFY: 4 use-case files above

**Steps:**
1. Create the helper:
   ```java
   public final class EventPublishing {
     public static void publishOrWarn(AppEventBus bus, AppEvent event, String contextLabel, Logger logger) {
       try {
         bus.publish(event);
       } catch (RuntimeException e) {
         if (logger.isWarnEnabled()) {
           logger.warn("Failed to publish {}: {}", contextLabel, e.toString(), e);
         }
       }
     }
     private EventPublishing() {}
   }
   ```
   The exact log format string should match the existing dominant pattern across the 4 files. Read all 4 first; pick the variant used by ≥3 of them. If all 4 differ, pick the most informative.
2. Replace each of the 4 try/catch blocks with `EventPublishing.publishOrWarn(eventBus, event, "<context>", logger);`.
3. Remove now-unused private helpers like `publishEvent(...)` from each use case.
4. Update imports.

**Verification:**
```powershell
mvn -Dtest=ProfileMutationUseCasesTest,MessagingUseCasesTest,ProfileNotesUseCasesTest,SocialUseCasesTest test
```

**Risk:** Low. Same try/catch semantics; only the message format unifies.

**Discipline gate:** Met (4 callers).

---

## Item 9 — Canonical record types from owning services (L2.10)

**Goal:** Have `RecommendationService` return canonical `DailyPickService.DailyPick`, `StandoutService.Result`, and `DailyLimitService.DailyStatus` directly, instead of defining shadow records with manual conversion.

**Audit confirmed:** Shadow records at `RecommendationService.java:227–287` with conversion methods at lines 289, 301, 305.

**Files affected:**
- MODIFY: [src/main/java/datingapp/core/matching/RecommendationService.java](../../../src/main/java/datingapp/core/matching/RecommendationService.java)
- MODIFY: callers that read the shadow records (any code that currently writes `RecommendationService.DailyPick`)

**Steps:**
1. Make sure each canonical record (`DailyPickService.DailyPick`, etc.) is `public` and accessible from `RecommendationService`'s call sites. If they are nested with `package-private` visibility, you'll need to widen.
2. Find all callers that consume `RecommendationService.DailyPick` (and the other two shadow types):
   ```powershell
   rg "RecommendationService\.(DailyPick|DailyStatus|Result)"
   ```
3. For each caller, switch the import and the reference type to the canonical service's record.
4. Delete the shadow records and conversion methods from `RecommendationService`.
5. If `RecommendationService` was being used as a "facade" that hides the underlying services, this change leaks the underlying types — that's intentional. But if a caller was specifically chosen to depend only on `RecommendationService`'s API for layering reasons, document that and decide whether to keep one shadow type as the boundary.

**Verification:**
```powershell
mvn -Dtest=RecommendationServiceTest,DailyPickServiceTest,StandoutServiceTest,DailyLimitServiceTest test
```

**Risk:** Medium. If `RecommendationService` is part of a public-stable API (e.g., consumed across module boundaries), removing the shadow types is an API break. Confirm this is internal-only before proceeding.

---

## Item 10 — `BaseController` logger + remove wrapper methods (L2.9)

**Goal:** Add `protected final Logger logger = LoggerFactory.getLogger(getClass())` to `BaseController`, then remove the per-controller `logInfo`/`logWarn`/`logDebug`/`logError` wrappers.

**Audit findings:**
- `BaseController` has no logger field today.
- Wrapper methods exist in `MatchingController.java:764–780` and other controllers (audit could not exhaustively confirm `MatchesController` vs `LoginController`).

**Verification before implementing:**
List the actual controllers that have these wrappers:
```powershell
rg "private void logInfo\(|private void logWarn\(|private void logDebug\(|private void logError\(" src/main/java/datingapp/ui/screen
```

This produces the authoritative caller set. Any controller in the result list is in scope; any controller NOT in the list stays untouched.

**Files affected:**
- MODIFY: [src/main/java/datingapp/ui/screen/BaseController.java](../../../src/main/java/datingapp/ui/screen/BaseController.java)
- MODIFY: every controller from the verification step

**Steps:**
1. Add the logger field to `BaseController`. Pattern matches `BaseViewModel`'s existing approach — confirm by reading [BaseViewModel.java](../../../src/main/java/datingapp/ui/viewmodel/BaseViewModel.java) first.
2. For each controller in the verification list:
   - Remove the local logger field (if any).
   - Remove the four wrapper methods.
   - Replace `logInfo("msg")` with `logger.info("msg")` (etc.). Preserve any guards (`if (logger.isDebugEnabled())`) since SLF4J already short-circuits these — but PMD's `GuardLogStatement` rule may require keeping the explicit guard; check `pmd-ruleset.xml`.
3. Run Spotless to clean up imports.

**Verification:**
```powershell
mvn -Dtest=DashboardControllerTest,MatchingControllerTest,ProfileControllerTest,PreferencesControllerTest test
mvn pmd:check
```

**Risk:** Low. SLF4J behavior is identical with or without the wrapper. PMD may complain about ungated `logger.debug(...)` calls — fix by adding `if (logger.isDebugEnabled())` guards where required.

---

## Item 11 — `PagedQuery<T>` helper (L2.4) — VERIFY FIRST

**Goal:** Create a generic `PagedQuery<T>` helper accepting a count supplier and a page-fetch function. Replace 4+ count-then-page patterns in `JdbiUserStorage` and `JdbiMatchmakingStorage`.

**Audit caveat:** The grep for `COUNT(*)` found 5 storage files but did NOT confirm 4+ identical count-then-page patterns. The plan claim is unverified.

**Mandatory verification step:**

```powershell
# Find every method body that has both a `COUNT(*)` and a `LIMIT`/`OFFSET` query.
rg -B 5 -A 30 "COUNT\(\*\)" src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java
```

Read each result. Count how many distinct methods follow the exact pattern: `int total = jdbi.withHandle(h -> h.createQuery("SELECT COUNT(*)... ").mapTo(Integer.class).one()); ... List<T> page = jdbi.withHandle(h -> h.createQuery("SELECT ... LIMIT :limit OFFSET :offset").bindBean(...).map(...).list()); return new Page<>(total, page);`

**Decision tree:**
- **≥3 distinct methods** with this shape → proceed with the helper as planned.
- **2 methods** → inline the second one to follow the first; skip helper creation. Document this in the commit message.
- **0–1 methods** → mark this item invalidated; add to the "False Claims" appendix in `CODEBASE_REFINEMENT_PLAN.md`.

**If proceeding (≥3 callers):**

**Files affected:**
- ADD: `src/main/java/datingapp/storage/jdbi/PagedQuery.java`
- MODIFY: `JdbiUserStorage.java`, `JdbiMatchmakingStorage.java`

**Steps:**
1. Define:
   ```java
   public final class PagedQuery<T> {
     public static <T> Page<T> execute(Supplier<Integer> count, BiFunction<Integer, Integer, List<T>> fetch, int offset, int limit) {
       return new Page<>(count.get(), fetch.apply(offset, limit));
     }
   }
   ```
   (Add a `Page<T>` record if one doesn't already exist; if it does, reuse it.)
2. Refactor each of the identified methods to use the helper.
3. Run a full storage test suite — paged queries are correctness-sensitive.

**Verification:**
```powershell
mvn -Dtest=JdbiUserStorageTest,JdbiMatchmakingStorageTest test
.\run_postgresql_smoke.ps1
```

**Risk:** Medium. Generics + lambdas obscure the SQL; reviewer fatigue is real. If the helper makes the original SQL harder to read, prefer to leave the duplication in place — clarity > line count.

---

## Item 12 — REST route handler template (L2.7)

**Goal:** Extract a shared route handler template in `RestApiServer` for the 14+ handlers following the "Lookup User → Execute → Result → DTO → Respond" pattern. Also extract `parsePagination(ctx, defaultLimit)` for 3 repeated pagination parsing blocks.

**Verification before implementing:**

The "Lookup User → Execute → Result → DTO → Respond" claim of 14+ handlers is plausible but unverified. Run:

```powershell
rg -n "User user = lookupUser\(|UUID userId = parseUuid\(" src/main/java/datingapp/app/api/RestApiServer.java
```

For pagination:
```powershell
rg -n "ctx\.queryParam\(\"limit\"\)|ctx\.queryParam\(\"offset\"\)" src/main/java/datingapp/app/api/RestApiServer.java
```

Adjust the count of expected callers based on actual results. Apply the discipline gate (≥3 for `parsePagination`; the route template only makes sense with ≥6–8 followers, since the abstraction itself is non-trivial).

**Files affected:**
- ADD or MODIFY: `RestApiServer.java` (helper methods may stay in this file rather than moving to a new class — depends on access patterns).
- MODIFY: REST API tests if any use exact response shapes the template might re-order (unlikely, but possible).

**Steps (high-level — flesh out only after verification confirms the patterns):**
1. Extract `parsePagination(Context ctx, int defaultLimit)` → returns `(offset, limit)` record. Replace the 3 call sites.
2. Sketch the route handler template. The shape is roughly:
   ```java
   private <REQ, RES> void handle(Context ctx, Function<Context, REQ> parse, Function<REQ, RES> execute, Function<RES, ?> toDto) { ... }
   ```
   But this kind of generic handler adapter is often *less* readable than the original imperative code. Consider instead a small set of named helpers:
   - `requireUser(Context, UserStorage)` returning `User`
   - `respondJson(Context, Object dto)`
   - `handleUseCase(Context, Supplier<Result>, Function<Result, ?> toDto)` — only if this concrete shape repeats
3. Refactor handlers in batches of 3–5, running tests between each batch.

**Verification:**
```powershell
mvn -Dtest=RestApiPhaseOneTest,RestApiPhaseTwoRoutesTest test
```

**Risk:** Highest in Level 2. A poorly-shaped abstraction here will hurt every future REST-API change. **Do not over-engineer.** If the audit shows fewer than 8 truly identical handlers, the template is not worth its cognitive cost.

**Defer if:** L2.6 is in flight or `RestApiServer` is otherwise being modified by other Level 3 items (L3.2 inlines `RestRouteSupport`). Do not start this until those are merged.

---

## Final verification gate

```powershell
mvn spotless:apply verify
```

Expected: tests pass; LOC trimmed by ~450; ~1 file deleted, ~3–4 added (depending on which discipline gates were met).

---

## Commit message drafts — for the user

The user handles all git operations. **Do not run `git commit`.** After each phase below completes (verification gate green), draft the corresponding commit message in chat as plain text and **STOP**, asking the user whether to proceed. The user will commit on their own cadence and tell you to continue.

Phases group items that share files or testing surfaces, so each commit stays reviewable:

### Phase A — mechanical replacements (Items 1–3)

After Items 1, 2, and 3 are all complete and `mvn -Dtest=...` passes for each, draft:

```text
refactor: mechanical deduplication of bindings, theme URL, and distance calc

- Centralize bindNullableUuid/Instant in JdbiTypeCodecs (drops local
  copies in JdbiMatchmakingStorage and JdbiMetricsStorage)
- Introduce UiStyles.getThemeUrl() and reroute the five inline
  /css/theme.css resource loads through it
- Route StandoutService, MatchQualityService, and BrowseRankingService
  distance calculations through GeoUtils.distanceKm
```

### Phase B — Match model work (Items 4–5)

After Items 4 and 5 are complete:

```text
refactor(model): add Match.copy and delegate isInvalidTransition

- Add Match.copy() mirroring User.copy(); replace four full-constructor
  reconstructions in ConnectionService, TrustSafetyService,
  JdbiMatchmakingStorage, and MatchingUseCases
- Have Match.isInvalidTransition delegate to
  RelationshipWorkflowPolicy.canTransition (single state machine)
```

### Phase C — REST API edges (Item 6)

After Item 6:

```text
refactor(api): unify isLoopback + drop redundant normalizeSharedSecret

- Consolidate the two isLoopback variants in RestApiServer and
  RestApiRequestGuards onto one package-private helper
- Pass the already-normalized shared secret into RestApiRequestGuards
  to remove the second normalizeSharedSecret() call
```

### Phase D — validation and events (Items 7–8)

After Items 7 and 8:

```text
refactor: collapse duplicate validation and event-publish wrappers

- Delegate ValidationService.validateEmail / validatePhone to
  TextNormalization; remove duplicate constants and helpers
- Extract EventPublishing.publishOrWarn used by ProfileMutationUseCases,
  MessagingUseCases, ProfileNotesUseCases, and SocialUseCases
```

### Phase E — recommendation types and controller logging (Items 9–10)

After Items 9 and 10:

```text
refactor: canonical record types from owning services + BaseController logger

- RecommendationService returns DailyPickService.DailyPick,
  StandoutService.Result, and DailyLimitService.DailyStatus directly,
  removing shadow records and conversion methods
- Hoist a logger field to BaseController; drop the per-controller
  log{Info,Warn,Debug,Error} wrapper boilerplate
```

### Phase F — paginated query helper (Item 11, conditional)

If the verification step inside Item 11 confirmed >=3 callers and the helper was actually added:

```text
refactor(storage): extract PagedQuery<T> for repeated count-then-page reads

Replaces N nearly-identical count + paginated-select pairs in
JdbiUserStorage and JdbiMatchmakingStorage with a small helper.
```

If Item 11 was invalidated, skip this phase entirely and tell the user.

### Phase G — REST handler template (Item 12)

After Item 12:

```text
refactor(api): extract parsePagination and shared route handler scaffolding

Reduces inline duplication across the lookup -> execute -> respond
handlers in RestApiServer. Helper shape is intentionally narrow:
named per-step utilities rather than a generic adapter.
```

If implementation diverged from any draft (e.g., a discipline gate failed and an item was inlined instead of extracted), edit the bullets to reflect what actually shipped before presenting to the user.

---

## Out of scope

- New abstractions whose discipline gate (≥3 callers) is not met — inline the second site instead.
- Rewriting `BaseViewModel` (its existing logger pattern is the model we're copying).
- Anything that touches `RelationshipWorkflowPolicy`'s public surface; we only delegate *to* it.

## Definition of done

- [ ] All 12 items either merged or explicitly invalidated (with appendix entry in `CODEBASE_REFINEMENT_PLAN.md`).
- [ ] `mvn spotless:apply verify` passes.
- [ ] No new helper class has fewer than 3 callers.
- [ ] LOC delta is in the expected range (~–450). If actual savings are <100 LOC, audit which items underdelivered and document.
