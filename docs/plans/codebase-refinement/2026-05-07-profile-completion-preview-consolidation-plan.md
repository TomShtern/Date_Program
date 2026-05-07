# Profile Completion and Preview Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one core profile roof responsible for completion scoring, preview generation, tips, and completeness output without changing user-facing behavior.

**Architecture:** Keep `ProfileService` as the public core entry point and fold `ProfileCompletionSupport` into it so the real logic and the public seam stop living in separate files. Keep `ProfileUseCases` as a thin application boundary, and keep JavaFX and CLI layers as consumers only.

**Tech Stack:** Java 25, Maven, JUnit 5, core profile services, JavaFX view models, CLI handlers.

---

## Decision Check

- This plan reduces fragmentation by consolidating two tightly coupled files into one existing public roof.
- Do not introduce a new public `ProfileReadinessService` or `ProfileCompletionService` unless the merged file becomes clearly too large. A new service would increase moving parts before it reduces confusion.
- Do not eliminate `ProfileService` in this plan. That would widen the blast radius into storage and app wiring and would make the refactor messier than necessary.
- Do not move UI-specific preview rendering into the core layer.
- This plan should ideally land before the profile presentation and readiness plan so the adapter layer has one stable core source of truth.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/core/profile/ProfileService.java`
- `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/screen/ProfileController.java`
- `src/main/java/datingapp/ui/screen/LoginController.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`

**Read carefully before editing**
- `src/main/java/datingapp/core/metrics/AchievementService.java`

**Tests to pin behavior**
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

## Task 1: Freeze Completion and Preview Behavior

**Files:**
- Test: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Test: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Test: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Test: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

- [ ] Step 1: Read `ProfileService`, `ProfileCompletionSupport`, `ProfileUseCases`, `ProfileViewModel`, and the preview and score dialog paths in `ProfileController`.
- [ ] Step 2: Add or tighten tests for completion percentage, category breakdowns, preview tips, preview display text, and controller or viewmodel behavior that consumes completion results.
- [ ] Step 3: Make sure the login and onboarding path still observes the same completion semantics after the test additions.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=ProfileUseCasesTest,ProfileViewModelTest,ProfileControllerTest,LoginControllerTest
```

Expected:
- Completion, preview, and score-display behavior is pinned before any consolidation.

## Task 2: Fold ProfileCompletionSupport into ProfileService

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Delete: `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`

- [ ] Step 1: Move the implementation methods from `ProfileCompletionSupport` into `ProfileService`.
- [ ] Step 2: Keep the public method names stable: `calculate`, `generatePreview`, `calculateCompleteness`, and `generateTips`.
- [ ] Step 3: Keep the nested result records stable unless a rename is required for clarity and all callers are updated in the same change.
- [ ] Step 4: Remove the extra `completionSupport` field and constructor wiring once the logic lives directly in `ProfileService`.

Target shape:
```java
public class ProfileService {
    public CompletionResult calculate(User user) { ... }
    public ProfilePreview generatePreview(User user) { ... }
    public ProfileCompleteness calculateCompleteness(User user) { ... }
    public List<String> generateTips(User user) { ... }
}
```

## Task 3: Repoint All Consumers to the Canonical Service Implementation

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`

- [ ] Step 1: Update imports and references so every caller consumes `ProfileService` directly and no code still mentions `ProfileCompletionSupport`.
- [ ] Step 2: Keep `ProfileUseCases` as a thin façade. Do not delete it in this plan; the goal here is to consolidate logic, not to rewrite the app boundary.
- [ ] Step 3: Check the achievement or metrics callers that read profile completeness and make sure they still consume the same result shapes.

Guardrail:
- If a caller only breaks because of a renamed nested record or import path, fix the caller. Do not reintroduce a helper class just to preserve the old internal structure.

## Task 4: Delete the Redundant File and Clean Up Naming

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Delete: `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`

- [ ] Step 1: Delete the old support file once there are no remaining references.
- [ ] Step 2: Remove comments, imports, or intermediate method names that only make sense when the logic lives in two different files.
- [ ] Step 3: Confirm that the merged `ProfileService` is still readable. If it becomes unreasonably large or mixed-purpose, stop and refactor inside the file with private helper methods rather than re-creating a second public class.

## Task 5: Verify the Consolidation End to End

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Re-run the focused completion, preview, and UI suite.

Run:
```powershell
mvn --% test -Dtest=ProfileUseCasesTest,ProfileViewModelTest,ProfileControllerTest,LoginControllerTest
```

Expected:
- All completion and preview behavior remains stable.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No regressions in completion-dependent behavior or formatting.

## Exit Criteria

- `ProfileService` is the single obvious home for completion and preview logic.
- `ProfileCompletionSupport.java` is gone.
- `ProfileUseCases` remains thin and stable.
- UI and CLI layers still consume the same completion and preview contract.
- The refactor reduces file count and removes indirection instead of creating another service layer.