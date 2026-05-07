# Codebase Refinement Plan

> **Sources:**
> - [CODEBASE_DEEP_ANALYSIS.md](./CODEBASE_DEEP_ANALYSIS.md) — 125 findings (88 from fresh audit + 37 from prior review, May 2026)
>
> **Baseline** (via `scc`):
> - **Java main:** 199 files · 46,392 code lines
> - **Java test:** 219 files · 49,198 code lines
> - **FXML:** 14 files · 1,512 code lines (JavaFX views)
> - **CSS:** 2 files · 2,178 code lines (JavaFX themes)
> - **PowerShell:** 6 files · 1,324 code lines (root scripts)
> - **XML:** 3 files · 101 code lines
> - **Properties:** 2 files · 45 code lines
> - **Markdown:** 15+ files (excluded — not audited)
> - **Total:** 445 files · 100,750 code lines
>
> **Ordered by:** impact-to-effort ratio, lowest effort first.

> **Baseline** (via `tokei`):
> - **Main:** 199 files · 46,392 code lines · 57,334 total
> - **Test:** 219 files · 49,403 code lines · 59,632 total
> - **Combined:** 418 files · 95,795 code lines · 116,966 total

---

## Level 1 — Quick Wins

*Low effort, high impact, minimal risk. Each item is independent and self-contained.*

1. Delete the empty `validateMatchingBehaviorFlags()` method from `AppConfigValidator`. (Analysis 2.3)

2. Rename `SwipeState.Session.MatchState` → `SessionState` to eliminate the name collision with `Match.MatchState`. Update all references in `SwipeState`, `ActivityMetricsService`, `MetricsEventHandler`, and tests. (Analysis 2.2)

3. Merge `GeoValidation` into `GeoUtils` — one file of 67 lines instead of two. (Analysis 2.4)

4. Merge `ModerationAuditLogger` into `ModerationAuditEvent` as a `static log(ModerationAuditEvent)` method. Delete `ModerationAuditLogger.java`. (Analysis 2.7)

5. Remove `EnumSetUtil.defensiveCopy(EnumSet, Class)` and `EnumSetUtil.safeCopy(Set, Class)` overloads. The `safeCopy(Collection, Class)` method handles all cases. (Analysis 2.6)

6. Define `private static final String INVALID_EMAIL_FORMAT` in `TextNormalization` and replace 5 hardcoded occurrences at lines 58, 62, 70, 77, 80. (Analysis 7.11)

7. Delete `CheckDb.java` from `src/test/java/datingapp/tools/`. It has a `main()` method, is not a JUnit test, and no script references it. (Analysis 8.3)

8. Delete `AppEvent.MatchExpired` from `AppEvent.java` and remove its subscription in `MetricsEventHandler`. No code publishes this event. (Analysis 7.13)

9. Delete `CandidateFinder.invalidateCacheFor()` and `CandidateFinder.clearCache()` — both are documented no-ops with empty bodies. Remove the call sites in `MatchingService` and `TrustSafetyService` that invoke these no-ops. (Analysis 7.14)

10. Delete the deprecated `NO_OP_DAILY_LIMIT_SERVICE` and `NO_OP_DAILY_PICK_SERVICE` inner classes from `MatchingUseCases`. (Analysis 3.4)

11. Promote `CandidateFinder.GeoUtils` to a top-level utility in `core/matching/` or merge into `core/model/GeoUtils.java`. Update imports in `MatchingViewModel` and other callers. (Analysis CR31)

12. Remove `ProfileHandler.copyForProfileEditing()` — it is a wrapper around `source.copy()`. Call `User.copy()` directly at the single call site. (Analysis CR35)

---

## Level 2 — Deduplication

*Medium effort, high impact. Fixes actual copy-paste duplication. Do one finding at a time; verify with existing tests.*

1. **`ValidationService` email/phone → delegate to `TextNormalization`.** Have `ValidationService.validateEmail()` call `TextNormalization.normalizeEmail()` instead of its own private copy. Same for `validatePhone()`. Remove the duplicate constants (`MAX_EMAIL_LENGTH`, `EMAIL_LOCAL_PATTERN`, `DOMAIN_LABEL_PATTERN`, `PHONE_ALLOWED_PATTERN`, `MIN_PHONE_DIGITS`, `MAX_PHONE_DIGITS`, `containsControlCharacters()`, `isValidAsciiDomain()`) from `ValidationService`. The `normalizePhotoUrl()` delegation already follows this pattern. (Analysis 1.1)

2. Add `Match.copy()` to the `Match` model (mirroring `User.copy()`). Replace the 3 call sites in `ConnectionService:613`, `TrustSafetyService:417`, and `MatchingUseCases:789` that reconstruct Match via full constructor. (Analysis 1.9)

3. Extract `EventPublishing.publishOrWarn(AppEventBus, AppEvent, String, Logger)` into `app/event/`. Replace the 4 copies of the try-catch wrapper in `ProfileMutationUseCases`, `MessagingUseCases`, `ProfileNotesUseCases`, and `SocialUseCases`. (Analysis 3.6)

4. Create `PagedQuery<T>` helper accepting `Supplier<Integer>` (count) and `BiFunction<Integer, Integer, List<T>>` (fetch). Replace the 4+ identical count-then-page patterns in `JdbiUserStorage` and `JdbiMatchmakingStorage`. (Analysis 2.12)

5. Move `bindNullableUuid()`, `bindNullableInstant()`, and `bindNullableString()` from `JdbiMatchmakingStorage`, `JdbiMetricsStorage`, and `JdbiAuthStorage` into `JdbiTypeCodecs` as shared static methods. (Analysis 2.11)

6. **REST API utility consolidation.** Create a package-level `RestApiUtils` class containing:
   - `parseUuid(String)` — remove from `RestApiServer` and `RestApiIdentityPolicy` (Analysis 1.6)
   - `isLoopback(String)` — remove from `RestApiServer` and `RestApiRequestGuards` (Analysis 5.9)
   - `extractBearerToken(Context)` returning `Optional<String>` — remove from `RestApiIdentityPolicy` and `RestApiRequestContext` (Analysis 5.5)
   - Have `RestApiServer` pass already-normalized shared secret to `RestApiRequestGuards`; remove the redundant second `normalizeSharedSecret()` call. (Analysis 5.10)

7. Extract a shared route handler template in `RestApiServer` for the 14+ handlers following the "Lookup User → Execute → Result → DTO → Respond" pattern. Also extract `parsePagination(ctx, defaultLimit)` for the 3 repeated pagination parsing blocks. (Analysis 5.1, 5.2)

8. Create `UiStyles` utility with `getThemeUrl()` for the 4 call sites that load `/css/theme.css` via `getClass().getResource("/css/theme.css").toExternalForm()`. The 4 sites: `UiUtils`, `UiDialogs`, `UiFeedbackService`, `MatchingController`. (Analysis 6.1)

9. Add `protected final Logger logger = LoggerFactory.getLogger(getClass())` to `BaseController` (matching `BaseViewModel`'s existing pattern). Remove the individual logger declarations and the `logInfo`/`logWarn`/`logDebug`/`logError` wrapper methods from `DashboardController`, `MatchingController`, `ProfileController`, `PreferencesController`, and `MatchesController`. (Analysis 1.4)

10. Export canonical record types (`DailyPick`, `Result`, `DailyStatus`) from their owning services (`DailyPickService`, `StandoutService`, `DailyLimitService`). Have `RecommendationService` return those directly instead of defining shadow records with manual field-to-field conversion methods. (Analysis 3.8)

11. Delegate distance calculation in `StandoutService`, `MatchQualityService`, and `BrowseRankingService` to the canonical implementation in `CompatibilityCalculator` (or `GeoUtils.distanceKm()` for the raw Haversine). (Analysis 2.17)

12. Have `Match.isInvalidTransition()` delegate to `RelationshipWorkflowPolicy.canTransition()` instead of maintaining its own hardcoded switch. (Analysis 2.1)

---

## Level 3 — Consolidation

*Higher effort, structural file changes. Do one subsystem at a time.*

1. Merge `InterestMatcher` + `LifestyleMatcher` → `PreferencesMatcher`. Both are stateless static utility classes serving the same consumer. Update imports in `MatchQualityService` and `CompatibilityCalculator`. (Analysis 2.5)

2. Inline `RestRouteSupport` route registration into `RestApiServer.start()`. Delete `RestRouteSupport.java`. Preserve the TRANSPORT NOTE comment in `RestApiServer`. (Analysis 5.6)

3. Extract `ApiForbiddenException`, `ApiUnauthorizedException`, `ApiConflictException`, `ApiTooManyRequestsException` from `RestApiRequestGuards` inner classes into a standalone `RestApiExceptions.java` file. Update imports in `RestApiIdentityPolicy` and `RestApiServer`. (Analysis 5.4)

4. Centralize route classification into a shared `RestApiRouteClassifier` (enum or helper). Eliminate the duplicate `CONVERSATION_ROUTE_PREFIX` / `USER_ROUTE_PREFIX` / `AUTH_ROUTE_PREFIX` constants and route-matching logic currently split between `RestApiRequestGuards` and `RestApiIdentityPolicy`. (Analysis 5.4)

5. Consolidate the 6 smallest DTO files into domain-grouped files: `RestApiDtos` (14 lines), `MessageDtos` (28 lines), `NotificationDtos` (34 lines), `VerificationDtos` (36 lines), `AuthDtos` (35 lines), `PhotoDtos` (39 lines). (Analysis 4.3)

6. Extract the 44 schema constants (`TABLE_MATCHES`, `TABLE_USERS`, `COLUMN_CREATED_AT`, etc.) from `MigrationRunner` into a shared `SchemaConstants` class. Reference them from both `SchemaInitializer` and `MigrationRunner`. (Analysis 2.13)

7. Add a `ProfileCompletionDto.of(ProfileCompletionView)` method that encapsulates the 8-field unwrapping pattern. Replace the 6 sites that manually unpack `ProfileCompletionView` into DTOs. (Analysis 5.3)

8. Make `ProfileCompletionView` the canonical container for completion fields. Have `ProfileEditSnapshotDto` and `UserDetail` embed it rather than unpacking its fields. (Analysis 4.2)

9. Merge `ReadPacePreferencesDto` and `WritePacePreferencesDto` into a single bidirectional `PacePreferencesDto`. (Analysis 4.1)

10. Unify `TestServiceRegistryBuilder.buildWithStorages()` and `RestApiTestFixture.Builder.build()` into a single builder with extension points for REST-specific additions. (Analysis 8.1)

11. Extract `SchemaEnumValues` constants for each enum's valid SQL CHECK constraint values, referenced by both `SchemaInitializer` and `MigrationRunner`. (Analysis 2.16)

---

## Level 4 — Test Cleanup

*Test-only changes. Safe to parallelize. No production code impact.*

1. Replace the 3 trivial `InMemoryUserStorage` inner classes in `DailyPickServiceTest`, `AchievementServiceTest`, and `ProfileCreateSelectTest` with `TestStorages.Users`. (Analysis 1.8)

2. Replace the 2 `ThrowingEventBus` inner classes with `TestEventBus.throwing()`. (Analysis 1.8)

3. Replace the 2 `SingleAchievementService` inner classes with `TestAchievementService.unlocked(...)`. (Analysis 1.8)

4. Fix the import in `AsyncErrorRouterTest` to use `UiAsyncTestSupport.TestUiThreadDispatcher` instead of its private copy. (Analysis 8.2)

5. Move the name-validation test from `EdgeCaseRegressionTest` to a `ValidationServiceTest` and the duplicate-match test to `MatchingServiceTest`. Delete `EdgeCaseRegressionTest.java`. (Analysis 8.4)

6. Move `TestJdbiMapping.java` from `src/test/java/datingapp/` to `src/test/java/datingapp/storage/jdbi/`. (Analysis 8.5)

7. Split `RestApiPhaseTwoRoutesTest.java` (1007 lines) into smaller route-specific test classes. (Analysis 8.7)

8. Replace `InMemoryUndoStorage` (2 copies) and `InMemoryStandoutStorage` (3 copies) with `TestStorages.Undos` and `TestStorages.Standouts`. (Analysis 1.8)

---

## Level 5 — Deep Refactors

*Highest effort, touches core architecture. Plan each before starting. Verify full test suite after each.*

1. Create a generic `UpsertTemplate` class accepting `List<ColumnBinding>` and `List<String> conflictColumns`. Replace the 12 `buildXxxUpsertSql()` methods in all 5 JDBI storage classes with constructor calls. (Analysis 1.2)

2. Resolve the `ProfileUseCases` facade: either drop the delegation facade and wire `ProfileMutationUseCases`/`ProfileNotesUseCases`/`ProfileInsightsUseCases` directly, or enforce all callers through the facade and remove the getter exposure. (Analysis 3.1)

3. Eliminate `ProfileService` (120 lines, zero business logic). Promote `ProfileCompletionSupport` to public. Move the 4 records (`CompletionResult`, `CategoryBreakdown`, `ProfileCompleteness`, `ProfilePreview`) into `ProfileCompletionSupport`. (Analysis 3.2)

4. Centralize the soft-delete filter (`deleted_at IS NULL`). Create a shared `SoftDeleteStatementCustomizer` or centralized SQL fragment used by all JDBI storage classes. (Analysis 2.10)

5. Extract `Main.java` presentation logic (`buildCurrentUserStatusLines()`, `printMenu()`, `createMainMenuRegistry()`) and the Win32 console UTF-8 FFM bootstrapping into dedicated classes (`MainMenuRegistry`, `ConsoleSetup`). (Analysis 7.2, 7.3)

6. Simplify `User.StorageBuilder`. Consider a record-based copy-with pattern or a generic deep-copy utility to eliminate the 186-line manual builder + 27-call `copy()` chain. (Analysis 7.5)

7. Merge `UserStorage`/`InteractionStorage`/`CommunicationStorage` base interfaces into their `Operational*` variants or drop the non-operational bases entirely. (Analysis 7.8)

8. Create a centralized `AppExecutors`/`ThreadPolicy` class with named factory methods (`dnsLookupExecutor()`, `imageLoader()`, `scheduledCleanup()`). Route all ad-hoc thread/executor creation in `TextNormalization`, `ImageCache`, `NominatimGeocodingService`, `ViewModelAsyncScope`, and `CleanupScheduler` through it. (Analysis 7.9)

9. Move file-URL configuration resolution from `TextNormalization` to `RuntimeEnvironment`. Add `RuntimeEnvironment.readFlag(propKey, envKey, defaultValue)`. Remove the `firstNonBlank()` private helper from `TextNormalization`. (Analysis 7.10)

10. Replace the `require*()` null-guard anti-pattern in `StandoutService` and `DailyPickService` with proper test mocks. Eliminate the protected no-arg constructors and all `requireDependencies()` boilerplate. (Analysis 7.15)

11. Consolidate the notification INSERT path in `JdbiConnectionStorage`. Have the static `insertNotification()` delegate to `SocialDao`, eliminating the duplicate SQL with two different bind mechanisms. (Analysis 1.5)

12. Centralize the conversation index definition in `SchemaInitializer` as the single source of truth. Remove the redundant index rebuild logic from `MigrationRunner.applyV8()` and `MigrationRunner.rebuildConversationActivityIndexes()`. Remove the V7 index creation that V16 drops. (Analysis 2.14, 2.15)

13. Extract `RestApiServer.main()` / `StartupOptions` into a dedicated `RestApiLauncher` class. (Analysis 5.8)

14. Standardize error response construction in `RestApiServer`. Route all errors through global exception handlers. Eliminate inline `ctx.status(x).json(new ErrorResponse(...))` calls. (Analysis 5.7)

15. Split `JdbiConnectionStorage` (851 lines, 2 DAOs, 4 entities) into `JdbiConversationMessageStorage` and `JdbiSocialGraphStorage`. (Analysis — storage layer size concern)

16. Extract shared block/report handlers and photo carousel/preloading logic from `MatchingController`, `ChatController`, and `ProfileController` into a shared `ControllerInteractionSupport` or base class. (Analysis 1.3)

17. Resolve the match lifecycle split: document the boundary between `MatchingUseCases.archiveMatch()` and `SocialUseCases.unmatch()`/`gracefulExit()`, or consolidate all match state transitions under one use-case. (Analysis 3.5)

18. **ChatViewModel correctness pass.** Fix the following in one coordinated change set (Analysis CR1–CR8):
    - Return read-only observable views from `getConversations()` and `getActiveMessages()`.
    - Document `sendMessage()` boolean return as "accepted for async send."
    - Fix the profile-note save token race condition — capture the initiating token before async work.
    - Clear all UI-observable state when `setCurrentUser(null)` is called.
    - Refresh `currentUser` from `AppSession` on initialize and explicit session changes.
    - Expose error/retry state for `loadMessagesInBackground()` failures.
    - Maintain explicit visible error state for message send failures.
    - Batch UI updates and compare cheaper summary signals before full list comparisons in polling.

19. Remove the `SafetyViewModel` compatibility fallback that performs verification work directly with `TrustSafetyService`. Route all verification through `VerificationUseCases`. (Analysis CR9)

20. Add a `dealbreakers(Dealbreakers)` setter to `User.StorageBuilder` so persistence/load code can set dealbreakers during construction rather than patching them after build. (Analysis CR14)

21. Fix `User.copy()` to preserve raw lat/lon/flag state without re-triggering normalization through `StorageBuilder`. (Analysis CR15)

22. Fix `User.markVerified()` to preserve the original `verificationSentAt` timestamp instead of clearing it. (Analysis CR16)

23. Replace `ProfileDraftAssembler` manual field-by-field assembly with `User.copy()` or a canonical `toBuilder()` path. (Analysis CR17)

24. Fix `MatchingUseCases.Builder.recommendationService()` to only auto-fill unset fields (do not overwrite already-set `dailyLimitService` / `dailyPickService` / `standoutService`). (Analysis CR18)

25. Route `MatchingUseCases.getDailyStatus()` through `dailyLimitService` instead of `recommendationService` directly. (Analysis CR19)

26. Separate `DatabaseManager.resetInstance()` into two methods: instance teardown and URL reset. (Analysis CR20)

27. Document `DatabaseManager.configurePoolSettings()` as startup-only. Throw if called after the live pool exists. (Analysis CR21)

28. Add a timestamp-accepting overload to `Standout.create()`. (Analysis CR22)

29. Define and reuse a shared `PAIR_ID_LENGTH` constant across `MigrationRunner`, `SchemaInitializer`, and `JdbiMatchmakingStorage`. (Analysis CR23)

30. Use JDBI batch APIs in `saveStandouts()` instead of individual upserts. (Analysis CR24)

31. Replace per-row `Calendar.getInstance(UTC)` allocation in `JdbiTypeCodecs` with a thread-local or cached reusable UTC calendar. (Analysis CR25)

32. Move read-only guards in `processSwipe()` before `executeWithUserLock`. Keep writes inside the lock. (Analysis CR26)

33. Document the concurrency contract split between `recordLike` (storage/undo primitive) and `processSwipe` (higher-level guarded path). (Analysis CR27)

34. Surface the original error in `ProfileUseCases.getOrComputeStats()` instead of masking with fallback. (Analysis CR28)

35. Replace string-based denial reason matching in `ConnectionService` with typed `WorkflowDecision` reason usage. (Analysis CR29)

36. Pass resolved `DatabaseDialect` from `StorageFactory` wiring into JDBI storage constructors. Remove the duplicate `SqlDialectSupport.detectDialect(jdbi)` calls in convenience constructors. (Analysis CR30)

37. Replace `Optional`-as-side-effect helpers in `RestApiServer` with a small route-result type or boolean-returning helper. (Analysis CR32)

38. Move repeated inline JavaFX theme constants and control styling into CSS/style classes. (Analysis CR36)

---

## Items to Defer

*These are valid observations but are not recommended for near-term action.*

| Item                                                      | Reason                                                                                                                                            |
|-----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `AppConfig.Builder` → record-builder migration            | ~672 lines of Builder class; moving to Lombok or staged builder produces massive diff with marginal structural improvement                        |
| `ServiceRegistry` → DI framework                          | 519 lines of poor-man's DI; introducing a framework is a major architectural decision outside the scope of a cleanup pass                         |
| `LoggingSupport` removal                                  | Touches 7 classes; SLF4J already handles level checks; the null-guard provides negligible protection but the churn-to-payoff ratio is unfavorable |
| `MatchesViewModel` async policy injection                 | Worth doing but depends on broader async infrastructure changes first (Analysis CR11)                                                             |
| `InteractionStorage` synchronized default                 | Document as best-effort only; production uses `JdbiMatchmakingStorage` which overrides the default (Analysis CR12)                                |
| Rate limiter monotonic clock                              | Valid but low-severity — time jumps are rare and the impact is limited (Analysis CR13)                                                            |
| V3 irreversible schema migration                          | Historical migration for databases already through it; document the risk for future irreversible migrations (Analysis CR37)                       |
| Inline JavaFX styles → CSS migration (full)               | Large surface area; do incrementally as controllers are touched (Analysis CR36)                                                                   |
| `EnumSetUtil` used by `UiFeedbackService` validation path | Valid controlled access through ViewModels                                                                                                        |
| `UiAnimations` / `UiComponents` boundary                  | The split is fine; document it rather than restructure                                                                                            |
| V2 migration `IF NOT EXISTS` for `daily_picks`            | Historical no-op for newly created databases; harmless, document as "historical-only"                                                             |
| 8 deprecated shim methods in `JdbiUserStorage`            | Verify callers exist before removal; tagged `forRemoval = false`                                                                                  |
| `AppConfigValidator` pass-through methods                 | `requireNonNegative`, `requirePositive`, `requireInRange` are legitimate single-use validation helpers                                            |

---

## Estimated Impact Summary

| Level                  | Items  | Files Eliminated | New Files     | LOC Net Change | Risk                               |
|------------------------|--------|------------------|---------------|----------------|------------------------------------|
| **1 — Quick Wins**     | 12     | ~5 deleted       | ~1 added      | –200           | Minimal — deleting dead/no-op code |
| **2 — Deduplication**  | 12     | ~1 deleted       | ~4 added      | –450           | Low — each item has existing tests |
| **3 — Consolidation**  | 11     | ~6 deleted       | ~3 added      | –300           | Medium — structural file reorg     |
| **4 — Test Cleanup**   | 8      | ~1 deleted       | ~3 added      | –520           | Minimal — test-only                |
| **5 — Deep Refactors** | 38     | ~6 deleted       | ~5 added      | –1,000         | High — touches core architecture   |
| **Total**              | **81** | **~19 deleted**  | **~16 added** | **–2,470**     |                                    |

**Post-refinement projection (Java only):**
- **Main:** 199 → **~184 files** · 46,392 → **~44,100 code lines** (–5.0%)
- **Test:** 219 → **~215 files** · 49,198 → **~48,680 code lines** (–1.1%)
- **Total Java:** 418 → **~399 files** · 95,590 → **~92,780 code lines** (–2.9%)

*FXML (14 files), CSS (2), PowerShell (6), XML (3), properties (2), and markdown (15+) are unaffected by this plan.*

*Numbers are conservative estimates. Actual savings depend on implementation choices (e.g., how generics replace boilerplate, how large files are split).*

*Numbers are conservative estimates. Actual savings depend on implementation choices (e.g., how generics replace boilerplate, how large files are split).*

---

*End of plan.*
