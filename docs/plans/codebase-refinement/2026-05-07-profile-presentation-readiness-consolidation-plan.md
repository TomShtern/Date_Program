# Profile Presentation and Readiness Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate REST-facing profile presentation and readiness shaping so there is one canonical support path for summary text, readiness fields, and completion snapshots.

**Architecture:** Keep completion math in the core profile layer and keep REST DTO files as thin transport wrappers. Move generic user-presentation logic into `UserPresentationSupport`, keep HTTP-specific photo URL resolution in REST DTO helpers, and make one readiness snapshot type the single source of truth for API projections.

**Tech Stack:** Java 25, Maven, JUnit 5, Javalin REST adapters, existing DTO pattern in `app/api`.

---

## Decision Check

- This plan is about adapter-layer duplication, not core completion math. Do not rewrite completion scoring here.
- Do not build a new mega-assembler package. Prefer consolidating into existing files that already own the behavior.
- `UserPresentationSupport` should own generic text and age shaping.
- `RestApiUserDtos` should keep only REST-specific mapping concerns such as photo URL resolution.
- `ProfileCompletionView` should stop floating as a separate top-level type once there is a better home for it.
- This plan is easier after the profile completion and preview plan, but it can be executed before it if the public `ProfileService` contract stays stable.

## Files In Scope

**Primary production files**
- `src/main/java/datingapp/app/support/UserPresentationSupport.java`
- `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- `src/main/java/datingapp/app/api/ProfileDtos.java`
- `src/main/java/datingapp/app/api/ProfileCompletionView.java`
- `src/main/java/datingapp/app/api/PhotoDtos.java`
- `src/main/java/datingapp/app/api/MatchDtos.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`

**Read carefully before editing**
- `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`

**Tests to pin behavior**
- `src/test/java/datingapp/app/support/UserPresentationSupportTest.java`
- `src/test/java/datingapp/app/api/ProfileCompletionViewTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`

## Task 1: Freeze the Existing API Contract

**Files:**
- Test: `src/test/java/datingapp/app/support/UserPresentationSupportTest.java`
- Test: `src/test/java/datingapp/app/api/ProfileCompletionViewTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- Test: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`

- [ ] Step 1: Read `RestApiUserDtos`, `ProfileDtos`, `PhotoDtos`, `ProfileCompletionView`, and the three `RestApiServer` call sites that currently build completion responses.
- [ ] Step 2: Add or tighten tests that lock down these fields: `approximateLocation`, summary text, safe age, `profileComplete`, `canActivate`, `canBrowse`, and the exact null behavior when no activation policy is provided.
- [ ] Step 3: Make sure at least one route test proves that the same readiness fields are emitted consistently across the relevant profile and photo responses.
- [ ] Step 4: Run the focused suite.

Run:
```powershell
mvn --% test -Dtest=UserPresentationSupportTest,ProfileCompletionViewTest,RestApiDtosTest,RestApiReadRoutesTest,ProfileViewModelTest
```

Expected:
- Current API and helper behavior is pinned before any refactor.

## Task 2: Move Generic User Presentation Logic to the Real Shared Home

**Files:**
- Modify: `src/main/java/datingapp/app/support/UserPresentationSupport.java`
- Modify: `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- Modify: `src/main/java/datingapp/app/api/MatchDtos.java`
- Modify: `src/main/java/datingapp/app/api/ProfileDtos.java`

- [ ] Step 1: Identify logic in `RestApiUserDtos` that is generic user presentation rather than REST transport behavior. The likely candidates are summary-line building and generic bio fallback rules.
- [ ] Step 2: Move only the generic parts into `UserPresentationSupport` next to `safeAge`.
- [ ] Step 3: Leave REST-only behavior such as `UnaryOperator<String>` photo URL resolution inside `RestApiUserDtos`.
- [ ] Step 4: Update `MatchDtos`, `ProfileDtos`, and any other callers so they depend on the shared support class for generic presentation rules.

Guardrail:
- Do not move HTTP-specific concerns into `UserPresentationSupport`. If a method needs a URL resolver or a transport-only field, it stays REST-local.

## Task 3: Give Readiness Fields One Canonical DTO Home

**Files:**
- Modify: `src/main/java/datingapp/app/api/ProfileDtos.java`
- Delete or empty and remove references from: `src/main/java/datingapp/app/api/ProfileCompletionView.java`
- Modify: `src/main/java/datingapp/app/api/PhotoDtos.java`
- Modify: `src/main/java/datingapp/app/api/RestApiUserDtos.java`

- [ ] Step 1: Move `ProfileCompletionView` into `ProfileDtos` as a package-private nested record or helper type. Keep the field names and null behavior stable.
- [ ] Step 2: Update `PhotoDtos`, `ProfileDtos`, and `RestApiUserDtos` to consume that single readiness type instead of constructing or unpacking the same booleans in parallel.
- [ ] Step 3: Delete the standalone `ProfileCompletionView.java` file once all imports are gone.

Target shape:
```java
final class ProfileDtos {
    static record CompletionSnapshot(... ) {
        static CompletionSnapshot from(User user, ProfileActivationPolicy activationPolicy) { ... }
    }
}
```

## Task 4: Remove Repeated Readiness Unwrapping from RestApiServer

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/api/ProfileDtos.java`
- Modify: `src/main/java/datingapp/app/api/PhotoDtos.java`

- [ ] Step 1: Replace the repeated `ProfileCompletionView.from(...)` call-and-unpack blocks in `RestApiServer` with one DTO factory path.
- [ ] Step 2: Make the DTO constructors or `from(...)` methods accept the canonical readiness snapshot instead of six separate readiness arguments.
- [ ] Step 3: Verify that `RestApiServer` is no longer the place where readiness booleans are manually reassembled.

Stop condition:
- Do not push DTO assembly into controllers or view models. The goal is to thin `RestApiServer`, not to spread DTO logic outward.

## Task 5: Verify the Presentation Layer Did Not Drift

**Files:**
- Verify: all files touched above
- Verify: all tests listed above

- [ ] Step 1: Run the focused presentation and REST suite again.

Run:
```powershell
mvn --% test -Dtest=UserPresentationSupportTest,ProfileCompletionViewTest,RestApiDtosTest,RestApiReadRoutesTest,ProfileViewModelTest
```

Expected:
- The same summary text, readiness flags, and route payloads still pass.

- [ ] Step 2: Run the repo-wide quality gate.

Run:
```powershell
mvn spotless:apply verify
```

Expected:
- No DTO regressions, route regressions, or formatting issues.

## Exit Criteria

- Generic user presentation rules live in `UserPresentationSupport`, not half in REST DTOs and half elsewhere.
- Readiness fields have one canonical DTO home.
- `RestApiServer` no longer reconstructs readiness payloads by hand.
- `ProfileCompletionView.java` is gone or fully absorbed.
- The resulting structure is simpler without inventing a new presentation framework.