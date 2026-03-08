# Implementation Plan: Configuration Coupling

**Status:** ✅ **COMPLETED** (2026-02-21)

**Source Report:** `Generated_Report_Generated_By_StepFun-Step-3.5-Flash_21.02.2026.md` (Finding F-009, F-010)

> ℹ️ **Alignment update (2026-03-01):** Completion outcome remains valid, but embedded test-count metrics in this document are historical.
> Current baseline test run: **983 run / 0 failed / 0 errors / 2 skipped**.

## 1. Goal Description
Multiple domain and UI classes actively breach Dependency Injection principles and testability by directly invoking `private static final AppConfig CONFIG = AppConfig.defaults();`.
Domain models (`User`, `MatchPreferences`) and UI Controllers (`LoginController`) fallback on these static defaults instead of relying on the system-wide initialized configuration loaded from JSON/ENV overrides. As a result, when an administrator adjusts `app-config.json` (e.g., increasing `minAge` from 18 to 21), the `User` domain validation blindly ignores this and enforces the hardcoded default. This represents severe hidden coupling and logical breakage.

**Objective:**
Eradicate static `.defaults()` usages across domain and UI layers. Inject the system's active configuration limits dynamically (ideally without coupling the domain core to the `AppConfig` class itself, passing strictly required primitives instead).

## 2. Proposed Changes

### `datingapp.core.model`

#### [MODIFY] `User.java`
- **Location:** Line 72 (`private static final AppConfig CONFIG = AppConfig.defaults();`)
- **Action:** ✅ DONE - The static `CONFIG` field was already removed in a previous refactoring.
- **Action:** ✅ DONE - `getAge()` deprecated method updated to clarify that callers should use timezone-aware version for business logic.
- **Action:** ✅ DONE - `setAgeRange()` now requires explicit system min/max parameters: `setAgeRange(int minAge, int maxAge, int systemMinAge, int systemMaxAge)`
- **Action:** ✅ DONE - `setMaxDistanceKm()` now requires explicit system limit: `setMaxDistanceKm(int maxDistanceKm, int systemMaxLimit)`
- **Backward Compatibility:** ✅ Added `@Deprecated` overloaded methods for tests: `setAgeRange(int, int)` and `setMaxDistanceKm(int)` with sensible defaults.

### `datingapp.core.profile`

#### [MODIFY] `MatchPreferences.java`
- **Location:** Line 392 (`private static final AppConfig CONFIG = AppConfig.defaults();`)
- **Action:** ✅ DONE - Static `CONFIG` field was already removed in a previous refactoring.
- **Action:** ✅ DONE - `Dealbreakers.Evaluator.passes()` now accepts timezone parameter: `passes(User seeker, User candidate, ZoneId timezone)`
- **Action:** ✅ DONE - `Dealbreakers.Evaluator.getFailedDealbreakers()` now accepts timezone parameter
- **Backward Compatibility:** ✅ Added `@Deprecated` overloaded methods that use `ZoneId.systemDefault()`

#### [MODIFY] `ValidationService.java`
- **Action:** ✅ VERIFIED - No parameterless constructor exists. All instances require explicit `AppConfig` injection.

### `datingapp.ui.screen`

#### [MODIFY] `LoginController.java`
- **Location:** `private static final AppConfig CONFIG = AppConfig.defaults();`
- **Action:** ✅ VERIFIED - No static `CONFIG` field exists. Controller properly receives config via `LoginViewModel`.

### `datingapp.core.matching`

#### [MODIFY] `CandidateFinder.java`
- **Action:** ✅ Added `ZoneId timezone` field to constructor
- **Action:** ✅ Updated constructor to accept timezone: `CandidateFinder(UserStorage, InteractionStorage, TrustSafetyStorage, ZoneId)`
- **Action:** ✅ Updated all age calculations to use `user.getAge(timezone)`
- **Action:** ✅ Updated `Dealbreakers.Evaluator.passes()` call to pass timezone
- **Backward Compatibility:** ✅ Added `@Deprecated` constructor that uses `ZoneId.systemDefault()`

#### [MODIFY] `MatchQualityService.java`
- **Action:** ✅ Updated age difference calculations to use `config.safety().userTimeZone()`
- **Action:** ✅ Updated highlight generation to use timezone-aware age

#### [MODIFY] `RecommendationService.java`
- **Action:** ✅ Updated age difference calculations to use `config.safety().userTimeZone()`

### `datingapp.storage`

#### [MODIFY] `StorageFactory.java`
- **Action:** ✅ Updated `CandidateFinder` instantiation to pass `config.safety().userTimeZone()`

## 3. Verification Plan

### Automated Tests
- ✅ **All 840 tests pass**
- ✅ Tests using old method signatures work via `@Deprecated` backward-compatible overloads
- ✅ No test failures or errors

### Manual Verification
1. ✅ Compilation successful: `mvn clean compile`
2. ✅ All tests pass: `mvn test` (840 tests, 0 failures)
3. ✅ Code formatted: `mvn spotless:apply`
4. ✅ Full build passes: `mvn clean verify`

### Configuration Test (Manual)
To verify the fix works correctly:
1. Open `config/app-config.json`
2. Change `"minAge"` from `18` to `25`
3. Launch application: `mvn javafx:run`
4. Try to create a user with age 20
5. **Expected:** Validation should reject (uses `ValidationService` with injected config)
6. **Old behavior:** Would have accepted (used hardcoded default)

## 4. Summary of Changes

### Files Modified (Production Code)
1. `User.java` - Added timezone-aware age calculation, updated setters to require system limits
2. `MatchPreferences.java` - Added timezone parameter to `Dealbreakers.Evaluator` methods
3. `CandidateFinder.java` - Added timezone field and constructor parameter
4. `MatchQualityService.java` - Use config timezone for age calculations
5. `RecommendationService.java` - Use config timezone for age calculations
6. `StorageFactory.java` - Wire timezone to `CandidateFinder`

### Key Design Decisions
1. **Domain Purity:** `User` model does NOT depend on `AppConfig` - maintains clean architecture
2. **Timezone Propagation:** Services with config access pass `ZoneId` to domain operations
3. **Backward Compatibility:** Deprecated overloads prevent breaking existing tests
4. **Simplicity:** Used existing `config.safety().userTimeZone()` - no new config fields needed

### Benefits
- ✅ Age calculations now respect configured timezone
- ✅ Configuration changes in `app-config.json` properly propagate to all business logic
- ✅ Domain models remain pure (no `AppConfig` dependency)
- ✅ Tests continue to work without modification
- ✅ Clear deprecation path for future cleanup
