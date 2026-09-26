# Structural Refactoring Design — 2026-02-28

## Scope
8 refactoring items from MAJOR_REWRITE_CANDIDATES and RETROSPECTIVE_ARCHITECTURE_DECISIONS.

## Tier 1: Self-Contained (parallelizable)

### 1. ProfileService (822 → ~520 LOC)
- Replace 4 identical `scoreXxx()` methods with one parameterized `scoreCategory()` using `FieldCheck` records
- Achievement switches stay (they depend on storage calls, not pure thresholds)
- No new files — refactoring is internal to ProfileService

### 2. MatchPreferences (840 → ~540 LOC)
- Replace 6 paired `passesXxx()`/`addXxxFailure()` in `Dealbreakers.Evaluator` with `DealbreakDimension<E>` record + data list
- Remove `hasXxxDealbreaker()` convenience methods

### 3. AppConfig.Builder (671 → ~350 LOC)
- Each sub-record gets `defaults()` factory method
- Delete flat 57-field Builder; AppConfig.defaults() delegates to sub-records
- Keep Jackson `readerForUpdating` compatibility

### 4. Config single-sourcing (Decision #8)
- Remove all `AppConfig.defaults()` calls outside bootstrap
- Replace with injected config from constructors

### 5. MatchQualityService (734 → ~560 LOC)
- Extract `InterestMatcher` as standalone class in `core/matching/`
- Move presentation methods into `MatchQuality` record (they only use record data)

### 6. User.java (807 → ~575 LOC)
- Remove `synchronized` from all methods
- Replace 29-method `StorageBuilder` with compact factory method

## Tier 2: Cross-Cutting

### 7. Service locator → use-case facades (Decision #2)
- CLI handlers accept use-cases directly instead of raw services
- Simplify `Dependencies` records to use-cases + session + inputReader

### 8. Thin CLI handlers
- Move remaining orchestration from handlers into use-cases
- Handlers become thin input→use-case→output mappers

## Constraints
- No new unnecessary files/classes
- All existing tests must pass
- Spotless + Checkstyle + PMD compliance
- No breaking changes to public API surfaces used by UI layer
