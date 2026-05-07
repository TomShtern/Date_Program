# Verification Workflow Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate verification workflow interpretation under `VerificationUseCases` so CLI, JavaFX, and REST adapters stop re-deriving the same start and confirm outcome logic.

**Architecture:** Keep verification state in `User` and keep the workflow in `VerificationUseCases`. Extend the use-case result contract so adapters consume one canonical outcome shape, then update CLI, JavaFX, and REST layers to map from that canonical result instead of rebuilding verification state and dev-code behavior separately.

**Tech Stack:** Java 25, Maven, JUnit 5, profile use cases, JavaFX safety view model, CLI safety handler, REST DTOs.

---

## Progress Tracking
- As you finish each step, mark it `✅ IMPLEMENTED`.
- When the plan is fully implemented end-to-end, add `✅ IMPLEMENTED` immediately below the title at the top of this file.

## Decision Check

- The workflow logic is already mostly centralized. The duplication is in result interpretation and presentation, not in the state machine itself.
- Do not introduce another service below `VerificationUseCases`. That would increase indirection without removing the real duplication.
- Do not move verification state out of `User`.
- Preserve the current dev-code exposure behavior: API still exposes the dev code intentionally, JavaFX exposes it conditionally, and CLI prints it today.
- This plan should reduce duplicated interpretation logic without changing verification semantics.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- `src/main/java/datingapp/app/cli/SafetyHandler.java`
- `src/main/java/datingapp/app/api/VerificationDtos.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`

**Tests to pin behavior**
- `src/test/java/datingapp/app/usecase/profile/VerificationUseCasesTest.java`
- `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`
- `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`

## Task 1: Freeze the Verification Contract

**Files:**
- Test: all tests listed above

- [ ] Step 1: Read `User`, `VerificationUseCases`, `SafetyViewModel`, `SafetyHandler`, `VerificationDtos`, and the verification route handlers in `RestApiServer`.
- [ ] Step 2: Add or tighten tests that pin the start-verification flow, confirm-verification flow, dev-code exposure, `verifiedAt`, and user-state transitions.
- [ ] Step 3: Make sure at least one viewmodel test and one CLI test prove that user-session state is updated correctly after both start and confirm operations.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=VerificationUseCasesTest,SafetyViewModelTest,SafetyHandlerTest,RestApiDtosTest,RestApiVerificationRoutesTest
```

Expected:
- Verification semantics and adapter behavior are pinned before any consolidation.

## Task 2: Give VerificationUseCases One Canonical Outcome Shape

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- Modify if needed: `src/main/java/datingapp/core/model/User.java`

- [ ] Step 1: Extend the use-case results or add a nested helper record so adapters can read one canonical snapshot of verification state after start and confirm.
- [ ] Step 2: Keep the existing business fields stable: user, method, contact, generated code, verified flag, and verified timestamp.
- [ ] Step 3: Keep the state-transition logic in `User` unchanged unless a test proves a bug.

Target shape:
```java
public record VerificationSnapshot(
        UUID userId,
        boolean verified,
        Instant verifiedAt,
        VerificationMethod method,
        String contact,
        String devVerificationCode) { }
```

Guardrail:
- The snapshot is for adapter consumption. It should not become a second source of truth for verification state.

## Task 3: Repoint JavaFX and CLI to the Canonical Outcome

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/app/cli/SafetyHandler.java`

- [ ] Step 1: Replace duplicated start and confirm result interpretation in `SafetyViewModel` with the canonical outcome shape from `VerificationUseCases`.
- [ ] Step 2: Replace duplicated start and confirm result interpretation in `SafetyHandler` with the same canonical outcome shape.
- [ ] Step 3: Keep UI- and CLI-specific presentation text local to each adapter, but keep the underlying result interpretation shared.
- [ ] Step 4: Preserve the existing session update behavior after start and confirm.

Stop condition:
- Do not create a second presentation service if the canonical result shape is enough. Prefer one richer use-case result over one more helper layer.

## Task 4: Keep REST Transport Thin and Consistent

**Files:**
- Modify: `src/main/java/datingapp/app/api/VerificationDtos.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`

- [ ] Step 1: Repoint `VerificationDtos` to the canonical use-case outcome shape.
- [ ] Step 2: Keep `RestApiServer` as a thin delegator that reads requests, invokes the use case, and serializes the result.
- [ ] Step 3: Preserve the current API contract, especially `devVerificationCode` in the start response and `verifiedAt` in the confirm response.

## Task 5: Verify the Workflow End to End

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Re-run the focused verification suite.

Run:
```powershell
mvn --% test -Dtest=VerificationUseCasesTest,SafetyViewModelTest,SafetyHandlerTest,RestApiDtosTest,RestApiVerificationRoutesTest
```

Expected:
- Same verification semantics, less duplicated interpretation logic.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No verification, DTO, or adapter regressions.

## Exit Criteria

- `VerificationUseCases` is the single obvious workflow owner.
- CLI, JavaFX, and REST adapters consume the same canonical verification outcome shape.
- Verification state still lives in `User`.
- The current dev-code exposure semantics are preserved.
- The workflow is clearer without adding a new service layer.