# Structural Refactoring Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement 8 structural refactoring items to reduce boilerplate, improve separation of concerns, and simplify the codebase.

**Architecture:** Data-driven patterns replace copy-pasted code. Presentation moves out of domain services. CLI handlers become thin mappers over use-cases. Config injection replaces static defaults.

**Tech Stack:** Java 25, Maven, Palantir format, JUnit 5

---

### Task 1: ProfileService — Data-Driven Category Scoring

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Test: existing tests in `src/test/java/datingapp/core/ProfileCompletionServiceTest.java`

**What to do:**
1. Add a `FieldCheck` record: `record FieldCheck(String name, int points, Predicate<User> isComplete, String nextStep)`
2. Define 4 static lists: `BASIC_FIELDS`, `LIFESTYLE_FIELDS`, `PREFERENCES_FIELDS` (interests handled separately)
3. Replace `scoreBasicInfo()`, `scoreLifestyle()`, `scorePreferences()` with ONE `scoreCategory(String name, List<FieldCheck> fields, User user)` method
4. Keep `scoreInterests()` as-is (it has non-trivial tiered scoring logic)
5. Run existing tests — behavior must be identical

**Expected reduction:** ~120 LOC (4 methods → 1 parameterized method + data lists)

---

### Task 2: MatchPreferences Dealbreakers.Evaluator — Data-Driven Dimensions

**Files:**
- Modify: `src/main/java/datingapp/core/profile/MatchPreferences.java`
- Test: existing tests (dealbreaker tests)

**What to do:**
1. Add a `DealbreakDimension` record inside `Evaluator`:
   ```java
   private record DealbreakDimension<E extends Enum<E>>(
       String label, Function<Dealbreakers, Set<E>> acceptableGetter,
       Function<User, E> candidateGetter, String nullMessage) {}
   ```
2. Define a `LIFESTYLE_DIMENSIONS` list with all 5 lifestyle dimensions
3. Replace the 5 paired `passesXxx()`/`addXxxFailure()` methods with loop-based `passes()` and `getFailedDealbreakers()`
4. Keep height and age methods separate (they have different logic patterns)
5. Remove `hasSmokingDealbreaker()` etc. convenience methods — replace usages with `!db.acceptableSmoking().isEmpty()` or keep `hasAnyDealbreaker()` only
6. Run existing tests

**Expected reduction:** ~100 LOC

---

### Task 3: AppConfig.Builder → Sub-Record Defaults

**Files:**
- Modify: `src/main/java/datingapp/core/AppConfig.java`
- Test: `src/test/java/datingapp/core/AppConfigTest.java`

**What to do:**
1. Add `defaults()` factory method to each sub-record (`MatchingConfig.defaults()`, etc.)
2. Change `AppConfig.defaults()` to: `return new AppConfig(MatchingConfig.defaults(), ValidationConfig.defaults(), AlgorithmConfig.defaults(), SafetyConfig.defaults())`
3. Delete the entire `Builder` class (lines 245-670)
4. For any code that needs custom config, use record `with*()` pattern or construct sub-records directly
5. Check `ApplicationStartup` config loading — if it uses Builder, refactor to construct sub-records from parsed values
6. Run ALL tests

**Expected reduction:** ~320 LOC (entire Builder deleted)

---

### Task 4: Config Single-Sourcing (Decision #8)

**Files:**
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java` (6 calls)
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java` (1 call)
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java` (2 calls)
- Modify: `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java` (1 call)
- Modify: `src/main/java/datingapp/storage/StorageFactory.java` (1 call)

**What to do:**
1. Add `AppConfig config` field to MatchingHandler Dependencies record
2. Replace all `AppConfig.defaults().safety().userTimeZone()` with `config.safety().userTimeZone()`
3. StatsHandler: accept config from constructor instead of creating defaults
4. ViewModels: accept config via ViewModelFactory (which already has ServiceRegistry with config)
5. StorageFactory: the `buildH2()` convenience overload is fine as a composition root entry point — leave it
6. Run all tests

---

### Task 5: MatchQualityService — Extract InterestMatcher

**Files:**
- Create: `src/main/java/datingapp/core/matching/InterestMatcher.java`
- Modify: `src/main/java/datingapp/core/matching/MatchQualityService.java`
- Modify: any file importing `MatchQualityService.InterestMatcher`

**What to do:**
1. Move the `InterestMatcher` class from inside `MatchQualityService` to its own file
2. Update all imports: `MatchQualityService.InterestMatcher` → `InterestMatcher`
3. Presentation methods (`getStarRating()`, `getCompatibilityLabel()`, `getStarDisplay()`, `getShortSummary()`, etc.) already live in the `MatchQuality` record — they're fine there
4. Remove unused `areKidsStancesCompatible()` private method (just delegates to LifestyleMatcher)
5. Run all tests

**Expected reduction:** ~20 LOC (method removal) + better organization

---

### Task 6: User.java — Remove synchronized, Compact StorageBuilder

**Files:**
- Modify: `src/main/java/datingapp/core/model/User.java`
- Test: existing User tests

**What to do:**
1. Remove `synchronized` keyword from ALL methods (47 occurrences)
2. Keep `StorageBuilder` but make it more compact — no structural change needed since each method is already one line
3. Actually: the StorageBuilder is fine as-is. The real win is removing synchronized.
4. Run all tests

**Expected reduction:** ~47 lines of `synchronized` keywords removed (clarity improvement)

---

### Task 7: MatchingHandler Dependencies → Use-Cases + Config

**Files:**
- Modify: `src/main/java/datingapp/app/cli/MatchingHandler.java`
- Test: existing MatchingHandler tests

**What to do:**
1. MatchingHandler already has `matchingUseCases` and `socialUseCases` fields
2. But it ALSO has raw service fields it shouldn't need: `matchingService`, `dailyService`, `undoService`, `matchQualityService`, `userStorage`, `analyticsStorage`, `standoutsService`
3. Move any remaining direct-service usage into MatchingUseCases
4. Simplify Dependencies record to: `MatchingUseCases, SocialUseCases, AppConfig, AppSession, InputReader, AnalyticsStorage (for profile views), Runnable profileCompleteCallback`
5. Update `fromServices()` factory method
6. Run all tests

---

### Task 8: StatsHandler Config Injection

**Files:**
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java`

**What to do:**
1. Accept ProfileUseCases from ServiceRegistry instead of constructing with `AppConfig.defaults()`
2. Verify constructor matches how Main.java wires it
3. Run tests

---

## Execution Order

Tasks 1, 2, 5, 6 are fully independent — can run in parallel.
Task 3 (AppConfig Builder deletion) must run before Task 4 (config single-sourcing) — actually no, Task 4 can run first.
Task 4 should run before Task 7 (since Task 7 restructures MatchingHandler which Task 4 modifies).
Task 8 is trivial and can run anytime.

**Parallel batch 1:** Tasks 1, 2, 5, 6 (independent file changes)
**Parallel batch 2:** Tasks 3, 4 (AppConfig changes)
**Sequential:** Task 7 (depends on Tasks 4's MatchingHandler changes)
**Anytime:** Task 8

## Verification

After all tasks: `mvn spotless:apply && mvn verify`
