# Refactor Plan (2026-02-04)

## Context
- Environment: Windows 11, PowerShell 7.5.4, VS Code Insiders
- Runtime: Java 25, JavaFX 25
- Goal: Reduce current warnings/errors while preserving readability and behavior.
- Constraints: No behavior changes; clean, maintainable refactors only.

## Scope of Current Problems (snapshot)
- JavaFX controller handler warnings (unused handlers)
- S1192 duplicate string literals ("like cannot be null", "None set")
- S135 loop break/continue counts (MatchesViewModel)
- S3776 / S6541 complexity (LoginViewModel.applyFilter, ProfileViewModel.save)
- Thread-safety warnings (volatile Thread fields)
- DailyService lossy cast
- Nested UI helper error for ConfettiAnimation

## Plan

### 1) Stabilize JavaFX controller/utility warnings (low risk)
- Ensure all FXML handlers are annotated with `@FXML`
- Verify UiHelpers.ConfettiAnimation is `public static` and constructed correctly
- Fix DailyService lossy cast (`Math.toIntExact` or widen accumulator)

**Status:** ✅ Done (UiHelpers ConfettiAnimation clarified; DailyService lossy cast fixed)

### 2) Eliminate duplicated literals (S1192)
- Introduce private constants for repeated literals:
  - `"like cannot be null"` (MatchingService, MatchesViewModel)
  - `"None set"` (ProfileViewModel)

**Status:** ✅ Done (shared constants for repeated literals; removed redundant toString)

### 3) Reduce loop break/continue counts (S135)
- Refactor MatchesViewModel loops into small helper predicates
- Preserve logic, improve clarity

**Status:** ✅ Done (loops restructured to eliminate extra continue statements)

### 4) Reduce cognitive complexity (S3776/S6541)
- Split large methods into cohesive helpers:
  - `LoginViewModel.applyFilter()`
  - `ProfileViewModel.save()`

**Status:** ✅ Done (applyFilter + save refactored into helpers)

### 5) Thread-safety improvements (S3077)
- Replace `volatile Thread` fields with safer primitives
- Prefer `AtomicReference<Thread>` or task/executor ownership pattern

**Status:** ✅ Done (AtomicReference used for background thread tracking)

### 6) Verification
- Run `mvn test` and check for new warnings/errors

**Status:** ✅ Done (mvn test: 588 tests, 0 failures)

### 7) De-nest types for stable IDE resolution
- Move nested types to top-level classes/enums:
  - `User.Gender` → `datingapp.core.Gender`
  - `User.State` → `datingapp.core.UserState`
  - `User.VerificationMethod` → `datingapp.core.VerificationMethod`
  - `Preferences.PacePreferences` → `datingapp.core.PacePreferences`
  - `DailyService.DailyPick` → `datingapp.core.DailyPick`
  - `UiHelpers.ConfettiAnimation` → `datingapp.ui.util.ConfettiAnimation`
  - `UiServices.Toast` → `datingapp.ui.util.Toast`
  - `UiServices.ImageCache` → `datingapp.ui.util.ImageCache`
  - `CliUtilities.InputReader` → `datingapp.app.cli.InputReader`
- Update all imports/usages and remove nested definitions
- Re-run formatting and tests

**Status:** ✅ Done

### 8) Post-de-nesting warning cleanup
- ✅ Replace repeated string literals in CLI/UI controllers with constants
- ✅ Refactor high-complexity handlers (ProfileController, SafetyHandler)
- ✅ Clean up unused fields/dependencies (MatchingViewModel, MatchingHandler)
- ✅ Stabilize Java LS classpath (remove simple-project sourcePaths)
- ✅ Fix test-only warnings (no-op stub comments, duplicate test logic)

**Status:** ✅ Done
