# Plan 07: Controllers, Navigation, Theming, and UI Micro-Performance

> **Date:** 2026-04-10
> **Wave:** 3
> **Priority:** Medium
> **Parallel-safe with:** none recommended
> **Status:** Planned

---

## Objective

Improve controller lifecycle ownership, navigation testability, styling consistency, and one known UI hot path without opening a broad UI rewrite.

## Issues addressed

| Issue ID | Summary                                                           |
|----------|-------------------------------------------------------------------|
| 6.1      | `NavigationService` singleton is baked into many controllers      |
| 8.2      | Inline JavaFX styles compete with stylesheet-driven theming       |
| 10.1     | `BaseController.cleanup()` assumes global navigation state exists |
| 18.3     | Hover animations allocate new transition objects per event        |

## Primary source files and seams

- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/ui/screen/BaseController.java`
- `src/main/java/datingapp/ui/screen/MatchesController.java`
- `src/main/resources/css/theme.css`
- `src/main/resources/css/light-theme.css`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/ui/screen/BaseController.java`
- `src/main/java/datingapp/ui/screen/MatchesController.java`
- `src/main/resources/css/theme.css`
- `src/main/resources/css/light-theme.css`

### Supporting read-only seams

- none

### Escalate instead of expanding scope if

- the navigation/controller cleanup starts requiring `ViewModelFactory` or ViewModel edits
- the styling cleanup turns into a broad repo-wide theming audit instead of the listed issue surface

## Primary verification slice

- `src/test/java/datingapp/ui/screen/MatchingControllerTest.java`
- `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- `src/test/java/datingapp/ui/screen/StandoutsControllerTest.java`
- `src/test/java/datingapp/ui/screen/SocialControllerTest.java`

## Execution slices

### Slice A — isolate navigation access

- add a narrow seam or overridable accessor for navigation-dependent controller behavior
- preserve the runtime singleton while improving testability

### Slice B — make controller cleanup independent of ambient global state

- ensure `cleanup()` can run in isolated tests without bootstrapping the whole app shell

### Slice C — move repeatable styling into CSS

- migrate repeated theme constants and control styling into style classes or shared stylesheet rules
- keep the Slice C migration scoped to the minimal `theme.css` and `light-theme.css` constant/class updates needed for that move, not a broad theming rewrite
- keep only genuinely data-driven inline styles in code

### Slice D — remove the hover-allocation hot path

- stop creating a fresh `ScaleTransition` on every hover event
- reuse prepared animation state or replace it with CSS/pseudo-class based feedback

## Dependencies and orchestration notes

- Run this plan after P05 and P06 so chat/state and factory conventions are already settled.
- Keep `NavigationService.java` single-owner while the plan is active.
- If this plan starts changing `ViewModelFactory` or ViewModels directly, stop and re-scope into P06.

## Out of scope

- chat-specific async state issues (P05)
- ViewModel-construction and policy standardization (P06)
- broad UI consistency rewrites beyond the issues listed here