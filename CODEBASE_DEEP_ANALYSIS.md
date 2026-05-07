# Codebase Deep Analysis — Read-Only Audit

> **Generated:** 2026-05-07 · **Passes:** 2 (7 initial agents + 3 verification agents)
> **Scope:** `src/main/java` (199 files) + `src/test/java` (219 files)
> **Excludes:** Markdown, config, scripts, docs — code is the only source of truth.

---

## 1. CRITICAL: High-Impact Code Duplication

### 1.1 `TextNormalization` and `ValidationService` each have their own email/phone normalization
<br>**CORRECTION from first pass:** `TextNormalization` IS used by `User.java` (lines 591, 596) and `AuthUseCases.java` (line 207) — it is NOT dead code. The issue is **duplication**, not dead code.

**Why included:** Exact copy-paste of ~130 lines of email/phone normalization across two files. `TextNormalization` serves the domain model (`User`, `AuthUseCases`); `ValidationService` retained its own copies for `validateEmail()`/`validatePhone()`. If a bug is fixed in one copy but not the other, behavior diverges silently.

**Suggestion:** Have `ValidationService.validateEmail()` and `ValidationService.validatePhone()` delegate to `TextNormalization.normalizeEmail()` and `TextNormalization.normalizePhone()` respectively (as `normalizePhotoUrl()` already does). Remove duplicate constants from `ValidationService`.

**Location:**
- `core/model/TextNormalization.java:52-103` — normalization used by domain layer
- `core/profile/ValidationService.java:297-354` — independent copies used only by itself

**Evidence:** The Javadoc on `TextNormalization` (line 28) states *"extracted from ValidationService to remove a service-to-model dependency"* but `ValidationService` was never updated to delegate for email/phone.

---

### 1.2 12 near-identical `buildXxxUpsertSql()` methods across JDBI storage classes

**Why included:** The same SQL upsert builder skeleton is repeated 12 times with only column lists differing. Adds ~200 lines of boilerplate and makes adding new upsert targets error-prone.

**Suggestion:** Create a generic `UpsertTemplate` class accepting `List<ColumnBinding>` and `List<String> conflictColumns`. All 12 usages reduce to constructor calls.

**Location:**
- `storage/jdbi/JdbiUserStorage.java` — `buildUserUpsertSql()`, `buildProfileNoteUpsertSql()`
- `storage/jdbi/JdbiMatchmakingStorage.java` — `buildLikeUpsertSql()`, `buildMatchUpsertSql()`, `buildUndoUpsertSql()`
- `storage/jdbi/JdbiMetricsStorage.java` — 6 upsert builders
- `storage/jdbi/JdbiAuthStorage.java` — `buildUpsertPasswordHashSql()`

**Evidence:**
```java
// Skeleton repeated 12 times:
private static String buildXxxUpsertSql(DatabaseDialect dialect) {
    return SqlDialectSupport.upsertSql(dialect, "table_name",
        List.of(new SqlDialectSupport.ColumnBinding("col", "bind"), ...),
        List.of("pk_col"));
}
```

---

### 1.3 5+ screen controllers duplicate block, report, photo carousel, and photo preloading logic

**Why included:** `handleBlock()`, `handleReport()`, photo carousel visibility toggling, and photo preloading are repeated with identical structure in `MatchingController`, `ChatController`, and `ProfileController`. Cut-paste of ~77 lines.

**Suggestion:** Extract shared handlers into a `ControllerInteractionSupport` class or a shared base. At minimum, factor out the block/report dialog flows.

**Location:**
- `ui/screen/MatchingController.java:684-709` — block + report handlers
- `ui/screen/ChatController.java:861-887` — identical block + report handlers
- `ui/screen/ProfileController.java:694-745` — photo controls + preloading
- `ui/screen/MatchingController.java:274-322` — identical photo controls + preloading

**Evidence:** Block handler pattern (both controllers, verbatim):
```java
User other = ...;
UiDialogs.confirmAndExecute("Block User", "Block " + other.getName() + "?", "...",
    () -> viewModel.blockXxx(other.getId()), other.getName() + " has been blocked.");
```

---

### 1.4 Logger wrapper methods copy-pasted across 5 controllers

**Why included:** Identical 4-method private helper blocks (`logInfo`, `logWarn`, `logDebug`, `logError`) duplicated across controllers. `BaseController` has no logger — unlike `BaseViewModel` which provides `protected final Logger logger`.

**Suggestion:** Add `protected final Logger logger = LoggerFactory.getLogger(getClass())` to `BaseController` (matching `BaseViewModel`). Remove individual declarations from the 5 controllers and the separate `LoggingSupport` interface implementation pattern used by 7 classes.

**Location:**
- `ui/screen/DashboardController.java`, `MatchingController.java`, `ProfileController.java`, `PreferencesController.java`, `MatchesController.java`

---

### 1.5 Notification INSERT SQL duplicated in `JdbiConnectionStorage` static method and DAO — with 2 different bind mechanisms

**Why included:** Same SQL executes via two paths: `SocialDao.saveNotificationInternal()` uses `@BindMethods` annotation while `JdbiConnectionStorage.insertNotification()` (static) uses manual `.bind()` calls. A schema change requires updating both.

**Suggestion:** Keep only the DAO path. Have static method delegate to the DAO.

**Location:** `storage/jdbi/JdbiConnectionStorage.java` — `insertNotification()` (static) and `SocialDao.saveNotificationInternal()`

---

### 1.6 `parseUuid()` copy-pasted between `RestApiServer` and `RestApiIdentityPolicy`

**Why included:** Same 6-line method appears in two files.

**Suggestion:** Extract to shared `RestApiUtils.parseUuid()` or make `RestApiIdentityPolicy.parseUuid()` package-accessible.

**Location:**
- `app/api/RestApiServer.java:1483-1489`
- `app/api/RestApiIdentityPolicy.java:136-142`

---

### 1.7 Photo file name validation + regex duplicated between `RestApiServer` and `RestApiPhotoStorage`

**Why included:** The identical regex `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` appears in both files.

**Suggestion:** Move regex and validation into `RestApiPhotoStorage`. Have `RestApiServer` delegate.

**Location:**
- `app/api/RestApiServer.java:180,373-384`
- `app/api/RestApiPhotoStorage.java:23,186-197`

---

### 1.8 5 copies of `InMemoryUserStorage` inner classes in test files — shared `TestStorages.Users` already exists

**Why included:** Ad-hoc inner-class fakes duplicate the shared `TestStorages.Users` utility. Adds ~400 lines of dead test infrastructure code.

**Suggestion:** Replace 3 of 5 with `TestStorages.Users`. The `TrustSafetyServiceTest` version has fault-injection that could be added to `TestStorages`. The `LikerBrowserServiceTest` uses a different interface and needs separate treatment.

**Location:**
- `core/DailyPickServiceTest.java:419` — simple list-based
- `core/AchievementServiceTest.java:160` — map-based
- `core/TrustSafetyServiceTest.java:598` — map-based + fault injection
- `core/LikerBrowserServiceTest.java:443` — `OperationalUserStorage`
- `app/cli/ProfileCreateSelectTest.java:213` — LinkedHashMap-based
- Additional: `InMemoryUndoStorage` (2 copies), `InMemoryStandoutStorage` (3 copies), `ThrowingEventBus` (2 copies), `SingleAchievementService` (2 copies)

---

### 1.9 `copyMatch()` constructor-pattern duplicated 3 times

**Why included:** Identical Match reconstruction via full constructor appears in three service classes. No `Match.copy()` method exists (unlike `User.copy()`).

**Suggestion:** Add `Match.copy()` to the Match model class. Replace all 3 call sites.

**Location:**
- `core/connection/ConnectionService.java:613-625`
- `core/matching/TrustSafetyService.java:417-429`
- `app/usecase/matching/MatchingUseCases.java` (around line 789)

---

## 2. Core Domain Layer

### 2.1 `Match.isInvalidTransition()` duplicates `RelationshipWorkflowPolicy.ALLOWED_TRANSITIONS`

**Why included:** Transition rules encoded as both a hardcoded `switch` in `Match` (lines 224-234) and as a `Map<MatchState, Set<MatchState>>` in `RelationshipWorkflowPolicy` (lines 53-56). Identical semantic content. Changing transitions requires updating both.

**Suggestion:** Have `Match.isInvalidTransition()` delegate to `RelationshipWorkflowPolicy.canTransition()`.

**Location:**
- `core/model/Match.java:224-234` — switch with 3 cases
- `core/workflow/RelationshipWorkflowPolicy.java:53-56` — canonical `ALLOWED_TRANSITIONS` map

---

### 2.2 `SwipeState.Session.MatchState` name collision with `Match.MatchState`

**Why included:** Two unrelated enums share the simple name `MatchState` in the same codebase.

**Suggestion:** Rename `SwipeState.Session.MatchState` to `SessionState` or `SwipeSessionState`.

**Location:**
- `core/metrics/SwipeState.java` — `Session.MatchState` (ACTIVE, COMPLETED)
- `core/model/Match.java` — `MatchState` (ACTIVE, FRIENDS, UNMATCHED, etc.)

---

### 2.3 `AppConfigValidator.validateMatchingBehaviorFlags()` — empty dead method

**Why included:** A public static method with a completely empty body (line 62-64). Comment says it's a "hook" but no behavior exists.

**Suggestion:** Remove or add actual validation logic.

**Location:** `core/AppConfigValidator.java:62-64`

---

### 2.4 `GeoValidation` (29 lines) and `GeoUtils` (38 lines) — two tiny geo utility files

**Why included:** 67 lines total in same package. Both are geo-related static utilities with private constructors.

**Suggestion:** Merge into `GeoUtils` containing `distanceKm()`, `validateLatitude()`, `validateLongitude()`.

**Location:** `core/model/GeoValidation.java`, `core/model/GeoUtils.java`

---

### 2.5 `InterestMatcher` (152 lines) + `LifestyleMatcher` (84 lines) — two static utilities for same consumer

**Why included:** Both stateless, all static methods, used together by `MatchQualityService` and `CompatibilityCalculator`.

**Suggestion:** Merge into `PreferencesMatcher` (236 lines total).

**Location:** `core/matching/InterestMatcher.java`, `core/matching/LifestyleMatcher.java`

---

### 2.6 `EnumSetUtil` has redundant overloads

**Suggestion:** Keep only `safeCopy(Collection, Class)`. Remove `defensiveCopy` and `safeCopy(Set)` overloads.

**Location:** `core/EnumSetUtil.java`

---

### 2.7 `ModerationAuditLogger` (31 lines) — thin wrapper

**Suggestion:** Merge as `ModerationAuditEvent.log()` static method.

**Location:** `core/matching/ModerationAuditLogger.java`, `core/matching/ModerationAuditEvent.java`

---

### 2.8 Dealbreaker data loaded from 2 separate sources

**Suggestion:** Either unify to all-junction-table or document the split explicitly.

**Location:** `storage/jdbi/JdbiUserStorage.Mapper` (scalar from users table), `storage/jdbi/DealbreakerAssembler.java` (sets from junction tables)

---

### 2.9 `NormalizedGroup` enum and `DealbreakerTable` enum — two enums for same taxonomy

**Suggestion:** Merge into single canonical enum or add explicit cross-references with tests.

**Location:** `storage/jdbi/JdbiUserStorage.java`, `storage/jdbi/NormalizedProfileRepository.java`

---

### 2.10 Soft-delete filter independently in every storage class

**Suggestion:** Create shared `SoftDeleteStatementCustomizer` or centralized SQL fragment.

**Location:** All files in `storage/jdbi/`

---

### 2.11 `bindNullable` helpers duplicated across 3 storage classes

**Suggestion:** Move to `JdbiTypeCodecs` or `SqlDialectSupport`.

**Location:** `JdbiMatchmakingStorage.java:1137-1159`, `JdbiMetricsStorage.java:718`, `JdbiAuthStorage.java:94-104`

---

### 2.12 Page-counting + paging pattern repeated 4+ times

**Suggestion:** Create `PagedQuery<T>` helper.

**Location:** `JdbiUserStorage.java:383-395`, `JdbiMatchmakingStorage.java:467-506`

---

### 2.13 44 duplicated schema constants in `MigrationRunner`

**Suggestion:** Extract into shared `SchemaConstants` class.

**Location:** `storage/schema/MigrationRunner.java:35-88`

---

### 2.14 Conversation indexes defined in 3 separate locations

**Suggestion:** Make `SchemaInitializer` the single source of truth.

**Location:** `SchemaInitializer.java:442-455`, `MigrationRunner.java:423-437,683-741`

---

### 2.15 V7 creates `idx_messages_conversation_id` that V16 drops — migration churn

**Suggestion:** Remove redundant index creation from V7.

**Location:** `MigrationRunner.java:400,595`

---

### 2.16 Enum check constraint lists duplicated between `SchemaInitializer` and `MigrationRunner`

**Suggestion:** Create `SchemaEnumValues` constants.

**Location:** Both `SchemaInitializer.java` and `MigrationRunner.java`

---

### 2.17 Distance calculation duplicated across 3 services

**Why included:** `StandoutService.calculateDistanceKm()`, `MatchQualityService.calculateDistanceKm()`, and `BrowseRankingService.calculateDistanceScore()` independently implement the same Haversine + score-to-percentage logic. The canonical version is in `CompatibilityCalculator`.

**Suggestion:** All services should delegate to `CompatibilityCalculator` or `GeoUtils.distanceKm()`.

**Location:**
- `core/matching/StandoutService.java:160-173`
- `core/matching/MatchQualityService.java:305-310`
- `core/matching/BrowseRankingService.java:88-95`

---

## 3. App Layer — Use Cases

### 3.1 `ProfileUseCases` is an ~80-line pure delegation facade — callers bypass it

**Why included:** Most methods delegate to `ProfileMutationUseCases`/`ProfileNotesUseCases`/`ProfileInsightsUseCases`. `RestApiServer` holds direct references to `profileMutationUseCases`, bypassing the facade.

**Suggestion:** Either drop the facade or enforce all callers through it.

**Location:** `app/usecase/profile/ProfileUseCases.java`

---

### 3.2 `ProfileService` in `core/profile/` is another redundant delegation facade

**Why included:** `ProfileService` (120 lines) has zero business logic — every method delegates to the package-private `ProfileCompletionSupport`. `ProfileService` also defines 4 public records consumed only by `ProfileCompletionSupport` callers.

**Suggestion:** Promote `ProfileCompletionSupport` to public and eliminate `ProfileService`, or consolidate records into `ProfileCompletionSupport`.

**Location:** `core/profile/ProfileService.java`, `core/profile/ProfileCompletionSupport.java`

---

### 3.3 `MatchingUseCases.listActiveMatches()` near-duplicate of `listPagedMatches()`

**Suggestion:** Deprecate `listActiveMatches()` in favor of `listPagedMatches()`.

**Location:** `app/usecase/matching/MatchingUseCases.java`

---

### 3.4 Deprecated `NO_OP_DAILY_LIMIT_SERVICE` / `NO_OP_DAILY_PICK_SERVICE` — ~50 lines of dead scaffolding

**Suggestion:** Remove if no longer needed.

**Location:** `app/usecase/matching/MatchingUseCases.java:43-80`

---

### 3.5 Match lifecycle split across `MatchingUseCases` and `SocialUseCases`

**Suggestion:** Document boundary or consolidate transitions under one use-case.

**Location:** `app/usecase/matching/MatchingUseCases.java`, `app/usecase/social/SocialUseCases.java`

---

### 3.6 `publishEvent` try-catch wrapper duplicated 4 times

**Why included:** `ProfileMutationUseCases`, `MessagingUseCases`, `ProfileNotesUseCases`, and `SocialUseCases` each have their own copy of the same event-publishing try-catch boilerplate.

**Suggestion:** Create static `EventPublishing.publishOrWarn(AppEventBus, AppEvent, String, Logger)` in the `app/event/` package.

**Location:** 4 use-case classes in `app/usecase/`

---

### 3.7 `RelationshipActionRunner` overlap with `SocialUseCases` relationship transitions

**Why included:** `RelationshipActionRunner` (in `ui/viewmodel/`) runs friend-zone, unmatch, and graceful-exit commands via `SocialUseCases`. The relationship transition logic is funneled through this ViewModel helper, but `SocialUseCases` also exposes related transitions. The boundary between the runner and the use cases is implicit.

**Suggestion:** Document the layering — the runner exists to keep UI-specific error handling out of viewmodels. This is fine but should be explicit.

**Location:** `ui/viewmodel/RelationshipActionRunner.java`, `app/usecase/social/SocialUseCases.java`

---

### 3.8 `RecommendationService` duplicates record types from inner services

**Why included:** `RecommendationService` defines shadow records (`DailyPick`, `Result`, `DailyStatus`) that are structurally identical to records defined in the services it wraps (`DailyPickService.DailyPick`, `StandoutService.Result`, `DailyLimitService.DailyStatus`). Conversion methods manually map every field — ~40 lines of ceremonial code.

**Suggestion:** Export canonical record types from owning services and have `RecommendationService` return those directly.

**Location:** `core/matching/RecommendationService.java`

---

## 4. App Layer — API / DTOs

### 4.1 `ReadPacePreferencesDto` vs `WritePacePreferencesDto` — two DTOs for same domain object

**Suggestion:** Merge into `PacePreferencesDto` handling both directions.

**Location:** `app/api/RestApiUserDtos.java`, `app/api/ProfileDtos.java`

---

### 4.2 Profile completion fields duplicated 3 ways

**Suggestion:** Make `ProfileCompletionView` the canonical container. Have DTOs embed it.

**Location:** `app/api/ProfileCompletionView.java`, `app/api/ProfileDtos.java` — `ProfileEditSnapshotDto`, `app/api/RestApiUserDtos.java` — `UserDetail`

---

### 4.3 13 separate DTO files — 6 under 40 lines

**Suggestion:** Consolidate the 6 smallest into domain-grouped files.

**Location:** `app/api/`

---

## 5. REST API Layer

### 5.1 14+ route handlers share an identical template structure

**Suggestion:** Extract `executeWithUser(ctx, dtoMapper, useCaseTask)` template.

**Location:** `app/api/RestApiServer.java` — handlers at lines 998-1479

---

### 5.2 Pagination parameter parsing duplicated 3x

**Suggestion:** Extract `parsePagination(ctx, defaultLimit)`.

**Location:** `app/api/RestApiServer.java:837-843,1278-1285,1354-1361`

---

### 5.3 Photo completion response building duplicated 6x (3 server + 3 DTO)

**Suggestion:** Add `ProfileCompletionDto.of(ProfileCompletionView)`.

**Location:** `app/api/RestApiServer.java:644-658,698-709,747-758`, `ProfileDtos.java:148-154,326-331`, `RestApiUserDtos.java:152-157`

---

### 5.4 Route classification logic split between `RestApiRequestGuards` and `RestApiIdentityPolicy`

**Suggestion:** Centralize into shared `RestApiRouteClassifier`. Move exception classes to `RestApiExceptions.java`.

**Location:** `app/api/RestApiRequestGuards.java:19-23,110-131,202-231`, `app/api/RestApiIdentityPolicy.java:20-21,36-47`

---

### 5.5 `resolveBearerToken()` near-duplicate between `RestApiIdentityPolicy` and `RestApiRequestContext`

**Suggestion:** Extract core `extractBearerToken()` returning `Optional<String>`.

**Location:** `RestApiIdentityPolicy.java:144-156`, `RestApiRequestContext.java:54-63`

---

### 5.6 `RestRouteSupport` (117 lines) — pure pass-through with no value

**Suggestion:** Inline into `RestApiServer.start()`.

**Location:** `app/api/RestRouteSupport.java`

---

### 5.7 Error response inconsistency — 3 mechanisms coexist

**Suggestion:** Minimize direct inline `ErrorResponse` construction. Prefer throwing typed exceptions.

**Location:** `app/api/RestApiServer.java:522,555,682,1690-1721,1737-1798`

---

### 5.8 `RestApiServer.main()` / `StartupOptions` inflate the 1852-line file

**Suggestion:** Extract into `RestApiLauncher` class.

**Location:** `app/api/RestApiServer.java:1801-1852`

---

### 5.9 Loopback address detection duplicated

**Suggestion:** Consolidate into `NetworkUtils.isLoopback(String)`.

**Location:** `RestApiServer.java:1659-1665`, `RestApiRequestGuards.java:144-150`

---

### 5.10 `normalizeSharedSecret()` double-called (Server + Guards) — redundant

**Suggestion:** Pass already-normalized value.

**Location:** `RestApiServer.java:1630-1632`, `RestApiRequestGuards.java:152-154`

---

## 6. UI Layer

### 6.1 Theme CSS loading duplicated in 4 classes

**Why included:** `UiUtils`, `UiDialogs`, `UiFeedbackService`, and `MatchingController` each load `/css/theme.css` with the same `getClass().getResource("/css/theme.css").toExternalForm()` pattern.

**Suggestion:** Create `UiStyles.getThemeUrl()` utility used by all 4 call sites.

**Location:**
- `ui/UiUtils.java:46-49`, `ui/UiDialogs.java:101-107`, `ui/UiFeedbackService.java:64-67`, `ui/screen/MatchingController.java:755-762`

---

### 6.2 `UiFeedbackService` mixes concerns — feedback + validation + avatar loading

**Suggestion:** Split validation into `core` layer. Remove thin proxy methods (`getAvatar()`, `clearValidation()`).

**Location:** `ui/UiFeedbackService.java`

---

### 6.3 `UiThemeService` (44 lines) — thin delegation wrapper

**Suggestion:** Merge into `NavigationService` or eliminate.

**Location:** `ui/UiThemeService.java`

---

### 6.4 Empty state visibility binding in 4 controllers

**Suggestion:** Add `bindEmptyState(ObservableList, Node, Node)` to `BaseController` or `UiUtils`.

**Location:** `MatchesController`, `NotesController`, `SafetyController`, `ChatController`

---

### 6.5 Alt+S save shortcut duplicated between `ProfileController` and `PreferencesController`

**Suggestion:** Move to `BaseController` as `registerSaveShortcut(Runnable)`.

**Location:** `ProfileController.java:1355-1365`, `PreferencesController.java:279-286`

---

### 6.6 `UiAnimations` vs `UiComponents` — animation responsibility split

**Suggestion:** Move `TypingIndicator` to `UiAnimations` or document boundary.

**Location:** `ui/UiAnimations.java`, `ui/UiComponents.java`

---

## 7. Core Utilities & Cross-Cutting

### 7.1 `LoggingSupport` interface provides negligible value

**Why included:** Wraps SLF4J with null-guards that SLF4J already checks internally. Forces 7 classes (5 CLI handlers + `CandidateFinder` + `InProcessAppEventBus`) to implement the interface boilerplate.

**Suggestion:** Remove `LoggingSupport`. If null-safety is a concern, provide a single static `safe(Logger)` utility. Use SLF4J directly everywhere.

**Location:** `core/LoggingSupport.java`

---

### 7.2 `Main.java` contains ~130 lines of presentation logic in entry point

**Why included:** Menu rendering, user status formatting, and menu wiring live in the entry point instead of in `MainMenuRegistry` or dedicated formatters.

**Suggestion:** Move `buildCurrentUserStatusLines()`, `printMenu()`, `createMainMenuRegistry()` to `MainMenuRegistry`.

**Location:** `src/main/java/datingapp/Main.java:160-260`

---

### 7.3 `Main.java` contains Win32 console UTF-8 FFM bootstrapping

**Suggestion:** Extract to `ConsoleSetup` utility class.

**Location:** `src/main/java/datingapp/Main.java:46-73`

---

### 7.4 `ServiceRegistry` is 519 lines with 2 different Builder conventions in the codebase

**Why included:** Manual DI container. The builder pattern differs from `AppConfig.Builder` — flat setters vs record assembly.

**Suggestion:** Make builder conventions consistent. Long-term, consider a DI framework or code generation.

**Location:** `core/ServiceRegistry.java`

---

### 7.5 `User.StorageBuilder` is 186 lines of brittle manual copy constructor

**Why included:** 35 setter methods. `User.copy()` chains 27 builder calls. Adding a field requires touching 3+ spots.

**Suggestion:** Consider record-based copy-with pattern or generic deep-copy utility.

**Location:** `core/model/User.java:185-370` (StorageBuilder), `:930-960` (copy())

---

### 7.6 `ProfileNote` duplicates validation 3 times

**Suggestion:** Keep validation only in the compact constructor. Remove redundant checks from `create()` and `withContent()`.

**Location:** `core/model/ProfileNote.java:17-35,47-64,72-81`

---

### 7.7 8 deprecated shim methods in `JdbiUserStorage`

**Suggestion:** Remove if all callers have migrated to aggregate save/load path.

**Location:** `storage/jdbi/JdbiUserStorage.java:322-367`

---

### 7.8 `Operational*` storage interfaces create a dead base-interface layer

**Why included:** `UserStorage`/`InteractionStorage`/`CommunicationStorage` bases are never independently implemented. Only `Operational*` variants are wired.

**Suggestion:** Merge base interfaces into `Operational*` or drop the non-operational bases entirely.

**Location:** `core/storage/UserStorage.java`, `InteractionStorage.java`, `CommunicationStorage.java`

---

### 7.9 Ad-hoc thread/executor creation — no centralized thread policy

**Why included:** 5 files create threads outside any central policy: `TextNormalization` (DNS pool, platform thread), `ImageCache` (preload worker), `NominatimGeocodingService` (Thread.sleep backoff), `ViewModelAsyncScope` (Thread.sleep), `CleanupScheduler` (scheduled executor).

**Suggestion:** Create `AppExecutors`/`ThreadPolicy` class with named factory methods (`dnsLookupExecutor()`, `imageLoader()`, `scheduledCleanup()`).

**Location:**
- `core/model/TextNormalization.java` — `DNS_LOOKUP_EXECUTOR`, `Thread.ofPlatform()`
- `ui/ImageCache.java` — `Thread.ofPlatform()`
- `app/geocoding/NominatimGeocodingService.java` — `Thread.sleep()`
- `ui/async/ViewModelAsyncScope.java` — `Thread.sleep()`
- `app/bootstrap/CleanupScheduler.java` — `Executors.newSingleThreadScheduledExecutor`

---

### 7.10 `System.getProperty` / `System.getenv` in `core/model/` — 3 read sites in `TextNormalization`

**Why included:** `TextNormalization` in `core/model/` reads system properties and env vars for file URL whitelist configuration. This couples the domain model layer to environment configuration. `RuntimeEnvironment` already exists in `core/` and is the proper home for env reads.

**Suggestion:** Move file-URL configuration resolution to `RuntimeEnvironment.readFlag(propKey, envKey, defaultValue)`.

**Location:** `core/model/TextNormalization.java` — `isFilePhotoUrlEnabled()`, `resolveAllowedFileUrlRoot()`, `firstNonBlank()`

---

### 7.11 `"Invalid email format"` hardcoded 5 times in `TextNormalization`

**Suggestion:** Define a local `private static final String INVALID_EMAIL_FORMAT` constant.

**Location:** `core/model/TextNormalization.java:58,62,70,77,80`

---

### 7.12 `AppConfig.Builder` — ~90 identical 3-line setters

**Why included:** Every setter is `public Builder field(T v) { this.field = v; return this; }`. Structural noise of ~305 lines.

**Suggestion:** Consider `@lombok.Builder` or staged builder migration.

**Location:** `core/AppConfig.java` (Builder class at lines 311-982)

---

### 7.13 `AppEvent.MatchExpired` is subscribed but never published

**Why included:** Declared in sealed hierarchy, `MetricsEventHandler` subscribes to it, but no code in the entire codebase publishes this event. The handler method is dead.

**Suggestion:** Either implement publishing or remove the event type and subscription.

**Location:** `app/event/AppEvent.java:60-61`, `app/event/handlers/MetricsEventHandler.java:75-78`

---

### 7.14 `CandidateFinder.invalidateCacheFor()` and `clearCache()` are documented no-ops

**Why included:** Called by `MatchingService` and `TrustSafetyService`, but both methods have empty bodies annotated "No-op: candidate browsing is deliberately freshness-first."

**Suggestion:** Remove the call sites and the dead methods, or implement the cache invalidation.

**Location:** `core/matching/CandidateFinder.java:228-233`

---

### 7.15 `StandoutService` and `DailyPickService` use `require*()` null-guard anti-pattern for test constructors

**Why included:** Both have protected no-arg constructors for anonymous test subclasses, then guard every method with `requireDependencies()` checks. This adds ~70 lines of boilerplate.

**Suggestion:** Replace the no-arg-test-constructor approach with proper test mocks. Eliminate all `require*()` methods.

**Location:** `core/matching/StandoutService.java:210-260`, `core/matching/DailyPickService.java:191-216`

---

## 8. Test Layer

### 8.1 `TestServiceRegistryBuilder` and `RestApiTestFixture.Builder` share ~80% wiring

**Suggestion:** Unify into single builder with extension points.

**Location:** `core/testutil/TestServiceRegistryBuilder.java`, `app/api/RestApiTestFixture.java`

---

### 8.2 `TestUiThreadDispatcher` duplicated in `AsyncErrorRouterTest`

**Suggestion:** Import from `UiAsyncTestSupport.TestUiThreadDispatcher`.

**Location:** `ui/async/UiAsyncTestSupport.java:52`, `ui/async/AsyncErrorRouterTest.java:60`

---

### 8.3 `CheckDb.java` in test tree is dead code — unreferenced `main()` method

**Why included:** A CLI diagnostic with `main()`, not a JUnit test. No script or test references it.

**Suggestion:** Remove or move to `docs/archived-utils/`.

**Location:** `src/test/java/datingapp/tools/CheckDb.java`

---

### 8.4 `EdgeCaseRegressionTest.java` is a grab-bag of 2 unrelated tests

**Why included:** Contains name validation test and duplicate-match test — unrelated concerns.

**Suggestion:** Move each test to its respective service test class.

**Location:** `src/test/java/datingapp/core/EdgeCaseRegressionTest.java`

---

### 8.5 `TestJdbiMapping.java` lives in wrong package (`datingapp` root)

**Suggestion:** Move to `datingapp/storage/jdbi/`.

**Location:** `src/test/java/datingapp/TestJdbiMapping.java`

---

### 8.6 Architecture tests enforce valuable design rules — preserve them

**Findings:** `AdapterBoundaryArchitectureTest`, `TimePolicyArchitectureTest`, and `EventCoverageArchitectureTest` enforce layer separation, timezone discipline, and event handler coverage. They reveal the codebase previously had problems with ViewModels taking direct storage deps, `ZoneId.systemDefault()` bypasses, and unregistered event handlers. These should be preserved and extended.

**Location:** `architecture/AdapterBoundaryArchitectureTest.java`, `TimePolicyArchitectureTest.java`, `EventCoverageArchitectureTest.java`

---

### 8.7 `RestApiPhaseTwoRoutesTest.java` (1007 lines) is monolithic

**Suggestion:** Split into smaller route-specific test classes.

**Location:** `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`

---

### 8.8 No `@Disabled` or `@Ignore` annotations anywhere in 219 test files

This is unusual and suggests either excellent discipline or that problematic tests were removed rather than skipped. Worth verifying that all tests actually pass.

---

## 9. Positive Findings (No Issue — Good Discipline)

| Area                         | Finding                                                                                                                                                                          |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Time calls**               | Zero `Instant.now()`/`LocalDate.now()` in production code. All 79 direct calls are in tests. `AppClock` discipline is strict.                                                    |
| **ValidationService access** | No UI-layer code calls `ValidationService` directly. All access goes through ViewModels and use-case boundaries.                                                                 |
| **Sync ImageCache**          | Only 1 synchronous `getImage()` call in UI layer (loading null-URL fallback/placeholder image — justified).                                                                      |
| **Test utilities**           | All 6 `core/testutil/` classes are actively imported. No dead test utility code.                                                                                                 |
| **Logging configs**          | All 3 logging config files (`logging-test.properties`, `logback-test.xml`, `logback-test-verbose.xml`) serve distinct purposes and are wired into `pom.xml`. None are redundant. |

---

## 10. Directory-Level Consolidation Opportunities

| Group               | Files                                                                                         | Lines | Suggestion                                      |
|---------------------|-----------------------------------------------------------------------------------------------|-------|-------------------------------------------------|
| Geo utilities       | `GeoUtils.java` + `GeoValidation.java`                                                        | 67    | Merge into `GeoUtils.java`                      |
| Preference matchers | `InterestMatcher.java` + `LifestyleMatcher.java`                                              | 236   | Merge into `PreferencesMatcher.java`            |
| Moderation audit    | `ModerationAuditEvent.java` + `ModerationAuditLogger.java`                                    | 188   | Merge logger as static method                   |
| Small DTOs          | `RestApiDtos`, `MessageDtos`, `NotificationDtos`, `VerificationDtos`, `AuthDtos`, `PhotoDtos` | ~190  | Consolidate into domain-grouped files           |
| API exceptions      | `RestApiRequestGuards` inner classes                                                          | ~30   | Extract to `RestApiExceptions.java`             |
| API route reg       | `RestRouteSupport.java`                                                                       | 117   | Inline into `RestApiServer`                     |
| Profile facade      | `ProfileService.java`                                                                         | 120   | Eliminate or promote `ProfileCompletionSupport` |

---

## 10.5. Findings from Prior Review (April 2026) — Still Valid

*Extracted from a prior codebase review. Only findings NOT already covered above. Verified against current source.*

### CR1. `ChatViewModel` exposes mutable backing lists

**Why included:** `getConversations()` and `getActiveMessages()` return writable backing lists — external UI code can bypass ViewModel invariants.

**Suggestion:** Return read-only observable views (`FXCollections.unmodifiableObservableList`).

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR2. `ChatViewModel.sendMessage()` boolean return is misleading

**Why included:** The `boolean` return means "accepted and queued for async send", not "persisted or delivered." Real completion is reported later through `handleSendResult()` and callbacks. Callers can misinterpret the return value as delivery confirmation.

**Suggestion:** Rename or document as "accepted for async send." Return a completion-aware type if callers need definitive success.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR3. `ChatViewModel` profile-note save token has a race condition

**Why included:** The profile-note save path increments `noteLoadToken` and only applies the UI update if that exact token is still current when the callback runs. This is intentional as a stale-update guard, but it also means a newer note load/save/delete can suppress a just-completed save confirmation. The user sees stale note UI even after a save succeeded.

**Suggestion:** Capture the initiating token before async work and validate against that token rather than the global current token.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR4. `ChatViewModel.setCurrentUser(null)` does not reset visible UI state

**Why included:** Logout/reset leaves stale conversations and messages visible in UI-observable state.

**Suggestion:** Clear all UI-observable state and related properties when currentUser is set to null.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR5. `ChatViewModel.ensureCurrentUser()` caches session state too aggressively

**Why included:** Session changes are not automatically reflected. The cached user outlives the screen/session boundary.

**Suggestion:** Refresh `currentUser` from `AppSession` on initialize and on explicit session changes.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR6. `ChatViewModel.loadMessagesInBackground()` fails without visible UI failure state

**Why included:** The user sees "stuck" chat behavior rather than an explicit error/retry state.

**Suggestion:** Expose an error/retry state in the ViewModel.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR7. `ChatViewModel.reportSendFailure()` relies on external wiring for visibility

**Why included:** Without an error sink, message send failures are mostly just logged.

**Suggestion:** Maintain an explicit visible error state in the ViewModel.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR8. ChatViewModel polling duplicates UI and diff work

**Why included:** Message and presence updates are dispatched separately. Message equality checks do full O(n) comparison each poll. Scales poorly with conversation size.

**Suggestion:** Batch UI updates and compare cheaper summary signals before full list comparisons.

**Location:** `ui/viewmodel/ChatViewModel.java`

---

### CR9. `SafetyViewModel` retains a fallback that bypasses `VerificationUseCases`

**Why included:** `SafetyViewModel` contains a compatibility fallback that performs verification work directly with `TrustSafetyService` when `verificationUseCases` is absent, weakening the intended UI/application seam.

**Suggestion:** Remove the fallback. Use `VerificationUseCases` exclusively as the canonical path.

**Location:** `ui/viewmodel/SafetyViewModel.java`

---

### CR10. `MatchingViewModel` imports `CandidateFinder.GeoUtils.distanceKm` directly

**Why included:** The UI layer depends on an implementation shape instead of a stable seam.

**Suggestion:** Route distance calculation/formatting through `LocationService` or a small injected distance-calculator seam.

**Location:** `ui/viewmodel/MatchingViewModel.java`

---

### CR11. `MatchesViewModel` decides async behavior by probing JavaFX runtime state

**Why included:** `Platform.isFxApplicationThread()` probes cause behavior to differ depending on whether tests initialize JavaFX. Test determinism depends on runtime probing.

**Suggestion:** Inject an async-policy seam into `MatchesViewModel` and derive execution mode from that seam instead.

**Location:** `ui/viewmodel/MatchesViewModel.java`

---

### CR12. `InteractionStorage.saveLikeAndMaybeCreateMatch()` synchronizes on `this` in an interface default

**Why included:** The default method `synchronized (this)` only serializes callers sharing the same in-memory instance — it does not provide transactional or cross-instance isolation.

**Suggestion:** Keep the default as a compatibility fallback, document it as best-effort intra-instance synchronization, and rely on production storage implementations (`JdbiMatchmakingStorage`) for real transactional isolation.

**Location:** `core/storage/InteractionStorage.java`

---

### CR13. Rate limiter relies on a non-monotonic clock source

**Why included:** `RestApiRequestGuards.LocalRateLimiter` uses `Instant.now()` (system clock) for rate-limit window math. Time jumps can skew enforcement.

**Suggestion:** Use a monotonic source for rate-limit window math (e.g. `System.nanoTime()` or an injectable ticker). Keep `AppClock` for user-facing timestamps.

**Location:** `app/api/RestApiRequestGuards.java`

---

### CR14. `User.StorageBuilder` omits `dealbreakers` even though it behaves like a complete builder

**Why included:** The important nested `Dealbreakers` field must be patched outside the builder path (via `User.setDealbreakers()` after construction). Construction cohesion is broken.

**Suggestion:** Add a `dealbreakers(Dealbreakers)` setter to `StorageBuilder` so persistence/load code can set it during construction.

**Location:** `core/model/User.java` — `StorageBuilder`

---

### CR15. `User.copy()` can lose location state through conflicting builder normalization

**Why included:** The copy path rebuilds location state through `StorageBuilder`, which re-triggers normalization logic on lat/lon. The copied location fields can diverge from the source object.

**Suggestion:** Preserve raw lat/lon/flag state without re-triggering conflicting normalization in the copy path.

**Location:** `core/model/User.java:930-960` — `copy()`

---

### CR16. `User.markVerified()` drops the original send timestamp

**Why included:** `markVerified()` clears `verificationSentAt`, retaining only the completion timestamp. The verification timeline collapses to one instant instead of preserving both send and confirm times.

**Suggestion:** Preserve the original send timestamp. Do not clear `verificationSentAt` on verification completion.

**Location:** `core/model/User.java` — `markVerified()`

---

### CR17. `ProfileDraftAssembler` manually copies too many fields

**Why included:** New `User` fields are easy to forget in the manual copy path. The manual bulk-copy is a drift risk.

**Suggestion:** Use `User.copy()` or a canonical `toBuilder()` path owned by `User` instead of manual field-by-field assembly.

**Location:** `ui/viewmodel/ProfileDraftAssembler.java`

---

### CR18. `MatchingUseCases.Builder.recommendationService()` has hidden side effects

**Why included:** Calling `recommendationService()` on the builder overwrites previously set `dailyLimitService`, `dailyPickService`, and `standoutService`. Builder call order changes runtime behavior.

**Suggestion:** Only auto-fill unset fields, or document the overwrite precedence clearly.

**Location:** `app/usecase/matching/MatchingUseCases.java` — Builder

---

### CR19. `MatchingUseCases.getDailyStatus()` bypasses the overridable daily-limit seam

**Why included:** It consults `recommendationService` directly instead of `dailyLimitService`. Mixed custom configurations can diverge.

**Suggestion:** Route through `dailyLimitService` or explicitly reject mixed configurations.

**Location:** `app/usecase/matching/MatchingUseCases.java` — `getDailyStatus()`

---

### CR20. `DatabaseManager.resetInstance()` resets more than its name promises

**Why included:** `resetInstance()` tears down the singleton AND resets the static JDBC URL to `DEFAULT_JDBC_URL`. Any custom URL must be re-applied after reset.

**Suggestion:** Separate instance reset from URL reset.

**Location:** `storage/DatabaseManager.java` — `resetInstance()`

---

### CR21. `DatabaseManager.configurePoolSettings()` looks dynamic but does not affect the live pool

**Why included:** The API suggests runtime tuning that does not actually happen — settings only affect the next pool construction.

**Suggestion:** Treat as startup-only configuration. Throw or document clearly if called after the live pool exists.

**Location:** `storage/DatabaseManager.java` — `configurePoolSettings()`

---

### CR22. `Standout.create()` hardcodes current time lookup

**Why included:** Callers cannot supply a timestamp without mutating shared clock state via `AppClock.setFixed()`.

**Suggestion:** Add a timestamp-accepting overload.

**Location:** `core/matching/Standout.java`

---

### CR23. Pair-ID length is encoded as a magic number across 3 files

**Why included:** The constraint `id VARCHAR(36)` or length checks for match/conversation IDs are hardcoded in `MigrationRunner`, `SchemaInitializer`, and `JdbiMatchmakingStorage`. Changes require hunting multiple literals.

**Suggestion:** Define and reuse one shared `PAIR_ID_LENGTH` constant across all ID-length checks.

**Location:** `storage/schema/MigrationRunner.java`, `storage/schema/SchemaInitializer.java`, `storage/jdbi/JdbiMatchmakingStorage.java`

---

### CR24. `saveStandouts()` uses repeated writes instead of batching

**Why included:** Individual upserts create more database round trips than necessary.

**Suggestion:** Use JDBI batch APIs for bulk standout persistence.

**Location:** `storage/jdbi/JdbiMetricsStorage.java` — `saveStandouts()`

---

### CR25. `JdbiTypeCodecs` allocates a new UTC calendar per row read

**Why included:** High-throughput row reading does avoidable allocation work — the calendar configuration is effectively constant.

**Suggestion:** Use a thread-local or cached reusable UTC calendar instead of allocating `Calendar.getInstance(UTC)` per row.

**Location:** `storage/jdbi/JdbiTypeCodecs.java`

---

### CR26. `processSwipe()` keeps the user lock longer than necessary

**Why included:** Read-mostly pre-checks lengthen lock duration within `executeWithUserLock`. Longer critical sections increase contention.

**Suggestion:** Move purely read-only guards before `executeWithUserLock`. Keep persisted revalidation and writes inside the lock.

**Location:** `core/matching/MatchingService.java` — `processSwipe()`

---

### CR27. `recordLike` and `processSwipe` do not share one concurrency contract

**Why included:** Similar swipe operations use different in-flight semantics. Concurrency behavior is harder to prove.

**Suggestion:** Treat `recordLike` as the storage/undo primitive and `processSwipe` as the higher-level guarded path. Document the different lock scopes.

**Location:** `core/matching/MatchingService.java`

---

### CR28. `ProfileUseCases.getOrComputeStats()` masks errors with fallback behavior

**Why included:** The original failure semantics are obscured by retry/fallback behavior. Silent fallbacks make debugging harder.

**Suggestion:** Surface the original error unless a very specific fallback is explicitly documented for the scenario.

**Location:** `app/usecase/profile/ProfileUseCases.java` — `getOrComputeStats()`

---

### CR29. `ConnectionService` maps workflow denial reasons through strings

**Why included:** Error mapping depends on string reason codes (`"INVALID_TRANSITION"`, `"SAME_STATE"`, etc.) from `WorkflowDecision`. String drift can silently break behavior.

**Suggestion:** Use typed denial reasons from `WorkflowDecision` instead of comparing against string reason codes.

**Location:** `core/connection/ConnectionService.java`

---

### CR30. Dialect detection repeated across JDBI storage constructors

**Why included:** `JdbiUserStorage`, `JdbiMatchmakingStorage`, and `JdbiMetricsStorage` each call `SqlDialectSupport.detectDialect(jdbi)` in their convenience constructors. Runtime dialect knowledge is derived multiple times.

**Suggestion:** Pass the resolved dialect from composition/wiring (`StorageFactory`). Remove the duplicate detection calls.

**Location:** `storage/jdbi/JdbiUserStorage.java`, `JdbiMatchmakingStorage.java`, `JdbiMetricsStorage.java`

---

### CR31. `GeoUtils` is nested inside `CandidateFinder` even though it behaves like an independent utility

**Why included:** `CandidateFinder` exposes `GeoUtils` as a public nested class, but its methods are not candidate-finder-specific. Call sites couple to `CandidateFinder.GeoUtils`.

**Suggestion:** Promote to a top-level utility in `core/matching/` or move into the existing `core/model/GeoUtils.java`.

**Location:** `core/matching/CandidateFinder.java`

---

### CR32. `Optional` used as control-flow signaling in `RestApiServer`

**Why included:** Some helpers use `Optional` to mean "error already handled" — mixing absence-of-value and error signaling.

**Suggestion:** Replace the `Optional`-as-side-effect helpers with a small local route-result type or a boolean-returning helper so the control-flow contract is explicit.

**Location:** `app/api/RestApiServer.java`

---

### CR33. `DefaultBrowseRankingService` and `DefaultStandoutService` referenced but do not exist under those names

**Note:** The prior review referenced `DefaultBrowseRankingService` and `DefaultStandoutService` — the current codebase uses `BrowseRankingService` and `StandoutService` (without the `Default` prefix). The prior review's observations about scoring logic duplication are covered by findings 2.17 and L2-11 in the refinement plan. The naming concern is stale.

---

### CR34. `DefaultCompatibilityCalculator` referenced but does not exist under that name

**Note:** The current codebase uses `CompatibilityCalculator` (without the `Default` prefix). The prior review's observation about repeated config/age lookups within one calculation is still valid if the behavior persists in the current `CompatibilityCalculator`, but the class name issue is stale.

---

### CR35. `copyForProfileEditing()` in `ProfileHandler` adds indirection without value

**Why included:** It is effectively a wrapper around `source.copy()`. Extra indirection increases reading overhead without improving the contract.

**Suggestion:** Call `User.copy()` directly.

**Location:** `app/cli/ProfileHandler.java` — `copyForProfileEditing()`

---

### CR36. Inline JavaFX styles compete with stylesheet-driven theming

**Why included:** Theme behavior is split between CSS and code. Inline styling is harder to maintain and audit.

**Suggestion:** Move repeated theme constants and reusable control styling into CSS/style classes. Keep one-off computed inline styles only when the value is genuinely data-driven at runtime.

**Location:** Multiple controller/UI utility files

---

### CR37. V3 schema migration drops legacy columns irreversibly

**Why included:** V3 drops several legacy serialized profile columns directly from `users` via `ALTER TABLE ... DROP COLUMN` with no down-migration or archival step.

**Suggestion:** Prefer staged removal, archival, or explicit rollback planning for irreversible schema changes.

**Location:** `storage/schema/MigrationRunner.java` — V3

---

---

## 11. Summary Statistics

| Category                            | Count   |
|-------------------------------------|---------|
| CRITICAL code duplication           | 9       |
| Core domain duplication/redundancy  | 17      |
| App/usecase layer issues            | 8       |
| API/DTO layer issues                | 3       |
| REST API layer issues               | 10      |
| UI layer duplication                | 6       |
| Core utilities & cross-cutting      | 15      |
| Test layer issues                   | 8       |
| Directory-level consolidations      | 7       |
| Prior review findings — correctness | 22      |
| Prior review findings — structural  | 15      |
| Positive findings (no issue)        | 5       |
| **Total findings**                  | **125** |

### Baseline Metrics (via `tokei`)

| Category                 | Files   | Code        | Comments  | Blanks     | Total       |
|--------------------------|---------|-------------|-----------|------------|-------------|
| Java                     | 418     | 95,590      | 5,057     | 16,319     | 116,966     |
| ├─ main                  | 199     | 46,392      | 3,951     | 6,991      | 57,334      |
| └─ test                  | 219     | 49,198      | 1,106     | 9,328      | 59,632      |
| FXML (JavaFX views)      | 14      | 1,512       | 109       | 44         | 1,665       |
| CSS (JavaFX themes)      | 2       | 2,178       | 261       | 447        | 2,886       |
| PowerShell (scripts)     | 6       | 1,324       | 0         | 284        | 1,608       |
| XML (logback configs)    | 3       | 101         | 28        | 17         | 146         |
| Properties (i18n + test) | 2       | 45          | 4         | 0          | 49          |
| **Total**                | **445** | **100,750** | **5,459** | **17,111** | **123,320** |
| Markdown (excluded)      | 15+     | —           | —         | —          | —           |

*Verified via `scc`. Markdown files excluded per audit scope. FXML, CSS, XML, PowerShell, and properties were not audited but are included for completeness.*

### Estimated Impact if All Resolved

| Metric               | Current | After   | Δ              |
|----------------------|---------|---------|----------------|
| Java main files      | 199     | ~184    | –15 (–7.5%)    |
| Java test files      | 219     | ~215    | –4 (–1.8%)     |
| Java main code lines | 46,392  | ~44,100 | –2,300 (–5.0%) |
| Java test code lines | 49,403  | ~48,880 | –520 (–1.1%)   |

---

*End of report. All findings are read-only observations. No code was modified.*
