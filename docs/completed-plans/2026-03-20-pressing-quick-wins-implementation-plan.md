# Pressing Matters + Quick Wins Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the highest-impact correctness and UX defects in the current issues register: persistence inconsistencies, missing metadata updates, weak validation, and silent UI/CLI failures.

**Architecture:** Keep orchestration in `ProfileUseCases` and the existing UI/CLI handlers, but move invariants into shared validation helpers so the domain remains testable and the app layer remains responsible for user-facing messaging. Treat this as a correctness-first tranche: do not widen the scope into performance or architectural cleanup unless a fix is already touching the same files. Each issue must gain a regression test before the production change lands.

**Tech Stack:** Java 25, JavaFX 25, Maven, JUnit 5, JDBI/H2, `ValidationService`, `UiFeedbackService`, `AppClock`, `ProfileUseCases`, `ProfileHandler`, `DashboardController`, `ChatController`, `NavigationService`.

---

## Scope at a glance

### In scope
- `V019` profile save and post-save side effects are not transactional
- `V001` match entity lacks `updatedAt`
- `V003` `User.markDeleted()` does not update `updatedAt`
- `V032` photo count constraints are not enforced at model boundary
- `V029` / `V089` email validation regex is too narrow
- `V068` phone normalization validates but does not normalize
- `V069` photo URL ingestion lacks validation
- `V044` CLI profile inputs lack complete validation
- `V042` CLI exception paths silently swallow errors
- `V028` dashboard achievement popup load failure is silent
- `V057` chat message-length indicator styling can race with text clear
- `V066` navigation context can be dropped silently
- `V074` report dialog does not propagate description text

### Explicitly out of scope for this plan
- Security/authentication hardening (`V002`, `V020`, `V025`, `V033`)
- Performance work and storage batching (`V010`, `V011`, `V065`, `V079`, `V064`, `V067`)
- Architecture refactors and controller/service cleanup (`V009`, `V024`, `V026`, `V071`, `V073`, `V078`, `V083`, `V075`)
- Historical/resolved items already marked invalid in the register

---

## File map

| File                                                                        | Role in this plan          | Why it changes                                                                     |
|-----------------------------------------------------------------------------|----------------------------|------------------------------------------------------------------------------------|
| `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`          | orchestration boundary     | make profile save + post-save effects behave atomically or fail cleanly            |
| `src/main/java/datingapp/core/model/Match.java`                             | domain metadata            | add `updatedAt` and update it in mutators                                          |
| `src/main/java/datingapp/core/model/User.java`                              | domain invariants          | add timestamp touch in `markDeleted`, enforce photo limit, tighten mutators        |
| `src/main/java/datingapp/core/profile/ValidationService.java`               | validation source of truth | unify email/phone/url checks and canonicalization                                  |
| `src/main/java/datingapp/ui/UiFeedbackService.java`                         | UI feedback helper         | remove duplicate validation logic and keep user-visible error messaging consistent |
| `src/main/java/datingapp/app/cli/ProfileHandler.java`                       | CLI validation UX          | reject invalid profile input before state mutation                                 |
| `src/main/java/datingapp/app/cli/SafetyHandler.java`                        | CLI error handling         | stop swallowing exceptions silently                                                |
| `src/main/java/datingapp/ui/screen/DashboardController.java`                | UI feedback                | show a warning when achievement popup loading fails                                |
| `src/main/java/datingapp/ui/screen/ChatController.java`                     | UI polish                  | make message-length indicator updates atomic                                       |
| `src/main/java/datingapp/ui/NavigationService.java`                         | navigation contract        | stop dropping navigation context silently                                          |
| `src/main/java/datingapp/ui/UiDialogs.java`                                 | dialog payloads            | carry report description through the callback                                      |
| `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`      | regression coverage        | profile save side-effect behavior                                                  |
| `src/test/java/datingapp/core/model/UserTest.java`                          | regression coverage        | timestamps, photo limits, delete behavior                                          |
| `src/test/java/datingapp/core/model/MatchTest.java`                         | regression coverage        | timestamps on mutating match operations                                            |
| `src/test/java/datingapp/core/profile/ValidationServiceTest.java`           | regression coverage        | email, phone, and URL validation/normalization                                     |
| `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`                   | regression coverage        | CLI validation failures and user feedback                                          |
| `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`                    | regression coverage        | exception handling and feedback                                                    |
| `src/test/java/datingapp/ui/screen/DashboardControllerTest.java`            | regression coverage        | popup load failure fallback                                                        |
| `src/test/java/datingapp/ui/screen/ChatControllerTest.java`                 | regression coverage        | style-class / length-indicator update stability                                    |
| `src/test/java/datingapp/ui/NavigationServiceTest.java`                     | regression coverage        | context consumption and fallback behavior                                          |
| `src/test/java/datingapp/ui/UiDialogsTest.java` or existing dialog coverage | regression coverage        | report description propagation                                                     |

## Implementation notes and end-to-end gotchas

These are the details an implementer needs to avoid turning a small fix into a half-finished refactor:

- `V019` must stay inside the existing profile use-case boundary. Keep storage mutation, post-save achievement logic, and event publication in one deterministic flow; do not push this into UI code.
- If `ProfileUseCases.saveProfile()` already has any hidden transaction support through storage, preserve it and only add the minimum atomic wrapper needed for the post-save side effects.
- `User` lives in `core/`, so avoid wiring `AppConfig` directly into the model just to enforce the photo limit. If the limit needs to be passed in, prefer a small policy/helper boundary that keeps the model testable.
- `ValidationService` must remain the single source of truth for shared validation rules. `UiFeedbackService` should delegate to it or mirror it exactly; do not leave two different regexes or normalization rules behind.
- `V068` should return a canonical phone string, not merely a boolean pass/fail result.
- `V069` should reject unsafe photo URLs before persistence. Keep the policy explicit enough that UI and CLI callers can surface a specific error message.
- `V042`, `V028`, and `V066` should change user-visible feedback without changing the larger application flow. Make the error visible, but keep the flow non-blocking and predictable.
- `V074` requires the dialog callback contract to change in lockstep with the UI. Update the dialog, the caller, and the test in the same pass so the compiler guards the migration.

## Current code paths to preserve

- `ProfileUseCases.saveProfile()` and `ProfileUseCases.unlockAchievements()` are the profile-side effect path.
- `User.markDeleted()`, `User.addPhotoUrl()`, and `User.setPhotoUrls()` are the core mutation points for the domain invariants in this plan.
- `ValidationService.normalizePhone()` and `ValidationService` email helpers are the shared validation points.
- `DashboardController.showSingleAchievementPopup()` is the popup failure path.
- `ChatController.updateMessageLengthIndicator()` is the chat polish path.
- `NavigationService` owns the context handoff contract.
- `UiDialogs.showReportDialog()` owns the report description callback shape.

## Verification commands

- Run the smallest relevant JUnit slice for the issue you are touching first.
- After each chunk, run the affected tests together so the new validation or metadata rules do not drift across layers.
- Finish with `mvn spotless:apply verify` only after the targeted slices are green.

---

## Chunk 1: persistence correctness and domain metadata

### Why this chunk comes first
These fixes are the highest-value correctness items in the register. They remove partial-write risk, make audit metadata trustworthy, and enforce a core limit that the UI already assumes. Everything else becomes easier to reason about once these invariants are stable.

### Issues covered
- `V019`
- `V001`
- `V003`
- `V032`

### Primary files
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/core/model/Match.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Modify: `src/test/java/datingapp/core/model/MatchTest.java`
- Modify: `src/test/java/datingapp/core/model/UserTest.java`

### Implementation shape
1. Add failing tests that describe the desired behavior before changing production code.
2. Make the profile-save path either fully succeed or fail cleanly without partial post-save effects.
3. Add `updatedAt` to `Match` and ensure every mutator updates it via `AppClock.now()`.
4. Add `touch()` to `User.markDeleted()`.
5. Enforce the photo-count limit at the domain boundary without leaking app-layer config into `core/`.
6. Re-run targeted tests and then the full quality gate.

### Step-by-step plan
- [ ] **Step 1: Write regression tests first**
  - Add a test in `ProfileUseCasesTest` that simulates profile save success followed by achievement/event failure and asserts the final state is deterministic.
  - Add a test in `MatchTest` proving every mutating path refreshes metadata.
  - Add a test in `UserTest` proving `markDeleted()` updates the timestamp.
  - Add a test proving photo addition rejects overflow beyond the configured limit.

- [ ] **Step 2: Run the targeted tests to confirm they fail for the right reason**
  - Run the smallest relevant test slice first.
  - Confirm the failures point to missing transactional behavior, missing timestamp updates, and the missing photo-count guard.

- [ ] **Step 3: Implement the minimal production changes**
  - Refactor `ProfileUseCases.saveProfile()` so the persistence step and post-save side effects are handled through one explicit success/failure contract.
  - Add `updatedAt` and metadata refresh behavior to `Match`.
  - Add `touch()` to `User.markDeleted()`.
  - Add a clean photo-count guard path that keeps `User` behavior deterministic and testable.

- [ ] **Step 4: Re-run the targeted tests**
  - Confirm the new tests pass and existing `User`/`Match` behavior is unchanged except where the plan explicitly intends a fix.

- [ ] **Step 5: Run a broader verification sweep**
  - Run the profile, model, and related UI/CLI tests that depend on these invariants.
  - Finish with `mvn spotless:apply verify` once the chunk is green.

### Acceptance criteria
- Profile save does not leave half-completed post-save side effects behind.
- `Match` exposes a trustworthy last-modified timestamp.
- `User.markDeleted()` updates the last-modified timestamp.
- Photo additions exceeding the limit fail consistently across the app.

### Rollback notes
- If the transactional refactor in `ProfileUseCases` needs a follow-up, keep the existing public API shape and introduce the new atomic path behind the current method first.
- If photo-limit enforcement needs a policy object to avoid `AppConfig` leakage into `core/`, prefer that over wiring framework config directly into the model.

---

## Chunk 2: validation and normalization

### Why this chunk comes second
These are small, high-payoff fixes that remove confusing edge cases for users and reduce duplicate validation logic. They also create a single source of truth before the UI/CLI feedback work leans on it.

### Issues covered
- `V029` / `V089`
- `V068`
- `V069`
- `V044`

### Primary files
- Modify: `src/main/java/datingapp/core/profile/ValidationService.java`
- Modify: `src/main/java/datingapp/ui/UiFeedbackService.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/test/java/datingapp/core/profile/ValidationServiceTest.java`
- Modify: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`

### Implementation shape
1. Consolidate email validation so UI and core do not drift.
2. Make phone normalization actually normalize, not just validate.
3. Add URL validation for photo ingestion.
4. Gate CLI profile input with the same validation rules the rest of the app expects.

### Step-by-step plan
- [ ] **Step 1: Write failing tests for all validation edge cases**
  - Add IDN-email test coverage in `ValidationServiceTest`.
  - Add phone normalization tests that assert the returned value changes to canonical form.
  - Add photo URL validation tests for scheme and format.
  - Add CLI profile validation tests for name, bio, and photo URL failures.

- [ ] **Step 2: Run the validation tests to see the current gaps**
  - Verify the current implementation fails where the tests expect canonicalization and stricter checks.

- [ ] **Step 3: Implement the shared validation rules**
  - Make `ValidationService` the source of truth for email, phone, and photo URL checks.
  - Remove duplicate email-pattern logic from `UiFeedbackService` or make it delegate to the shared validator.
  - Add `ProfileHandler` guards so invalid input never reaches mutation methods.

- [ ] **Step 4: Re-run the targeted tests**
  - Confirm validation failures now produce explicit messages and accepted input is normalized.

- [ ] **Step 5: Verify integration with the profile flow**
  - Run the profile and validation test slices together so the user-facing flow and the validator agree on the same rules.

### Acceptance criteria
- Email validation accepts the intended internationalized cases or intentionally documents its boundary.
- Phone normalization returns a canonical phone string.
- Invalid photo URLs are rejected before persistence.
- CLI profile input shows actionable feedback instead of allowing invalid state.

### Rollback notes
- Keep validation messages specific and preserve existing user-facing wording where possible.
- If a more robust email validator library is introduced later, keep `ValidationService` as the wrapper so callers do not depend on the library directly.

---

## Chunk 3: UI and CLI feedback polish

### Why this chunk comes third
These are mostly small, localized changes, but they matter a lot for perceived quality. The goal is to remove silent failures and make confusing transitions visible to the user.

### Issues covered
- `V028`
- `V042`
- `V057`
- `V066`
- `V074`

### Primary files
- Modify: `src/main/java/datingapp/ui/screen/DashboardController.java`
- Modify: `src/main/java/datingapp/app/cli/SafetyHandler.java`
- Modify: `src/main/java/datingapp/ui/screen/ChatController.java`
- Modify: `src/main/java/datingapp/ui/NavigationService.java`
- Modify: `src/main/java/datingapp/ui/UiDialogs.java`
- Modify: `src/test/java/datingapp/ui/screen/DashboardControllerTest.java`
- Modify: `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/ChatControllerTest.java`
- Modify: `src/test/java/datingapp/ui/NavigationServiceTest.java`
- Modify: `src/test/java/datingapp/ui/UiDialogsTest.java` or equivalent dialog coverage

### Implementation shape
1. Turn silent failures into visible, non-blocking feedback.
2. Make chat composer style updates atomic so the UI cannot race itself.
3. Stop navigation context from disappearing without a signal.
4. Ensure report descriptions actually reach the callback.

### Step-by-step plan
- [ ] **Step 1: Write focused UI/CLI regression tests**
  - Mock the dashboard popup loader to throw `IOException` and assert user feedback appears.
  - Assert CLI parse failures show something visible to the user instead of being swallowed.
  - Exercise the chat length indicator with rapid clear/input updates.
  - Assert navigation context consumption either succeeds or warns clearly.
  - Assert report descriptions are not lost in the dialog callback.

- [ ] **Step 2: Run the focused tests and confirm the failure modes**
  - Keep the test scope narrow so failures are easy to map back to each fix.

- [ ] **Step 3: Implement the user-visible feedback path**
  - Use `UiFeedbackService` or the existing UI messaging pattern for the dashboard popup failure.
  - Replace silent CLI catches with logged exceptions plus explicit user-facing feedback.
  - Make chat label/style updates happen in one deterministic UI-thread action.
  - Tighten `NavigationService` so missing context is not silently discarded.
  - Pass the report description through `UiDialogs` and into the consuming handler.

- [ ] **Step 4: Re-run the UI/CLI tests**
  - Confirm each failure now produces visible output or stable UI behavior.

- [ ] **Step 5: Smoke test the user journey**
  - Open dashboard, trigger a popup failure path, validate feedback.
  - Exercise profile and safety CLI commands with invalid input.
  - Verify chat composer behavior during clear/send typing churn.
  - Verify report dialog content is preserved end to end.

### Acceptance criteria
- No silent dashboard popup failure.
- CLI parse errors are visible to the user.
- Chat length indicator updates do not race with composer clearing.
- Navigation context loss becomes a controlled, observable event.
- Report dialog descriptions reach the business logic.

### Rollback notes
- Keep UX feedback non-blocking; do not replace these warnings with modal dialogs unless the flow already expects a modal.
- Preserve current navigation APIs if possible; add a clearer signal first and refactor the caller later only if needed.

---

## Final verification

### Targeted test pass order
1. `ProfileUseCasesTest`, `UserTest`, `MatchTest`
2. `ValidationServiceTest`, `ProfileHandlerTest`
3. `DashboardControllerTest`, `SafetyHandlerTest`, `ChatControllerTest`, `NavigationServiceTest`, dialog tests

### Full quality gate
- Run `mvn spotless:apply verify` only after the targeted slices are green.
- If a test failure points to an already-resolved historical item, stop and re-check the issue register before changing code.

### Completion checklist
- [ ] All in-scope issues have a matching regression test.
- [ ] All production changes are limited to the files in the file map or adjacent helpers.
- [ ] No security/performance/architecture work from the excluded buckets sneaks into this tranche.
- [ ] Full Maven quality gate passes.
