# Plan 11: CLI Handler Seams and Shared Helpers

> **Date:** 2026-04-10
> **Wave:** 4
> **Priority:** Medium
> **Parallel-safe with:** none recommended
> **Must run after:** P03
> **Status:** Planned

---

## Objective

Extract the repetitive parsing and formatting seams in the CLI layer so handlers stop carrying duplicated helper logic and trivial profile-copy indirection.

## Issues addressed

| Issue ID | Summary                                                  |
|----------|----------------------------------------------------------|
| 1.2      | CLI feedback formatting is inconsistent                  |
| 3.1      | Index parsing is repeated throughout the CLI layer       |
| 12.3     | `copyForProfileEditing()` adds indirection without value |
| 15.2     | `CliTextAndInput` bundles too many CLI concerns          |

## Primary source files and seams

- `src/main/java/datingapp/app/cli/MatchingHandler.java`
- `src/main/java/datingapp/app/cli/MessagingHandler.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/app/cli/SafetyHandler.java`
- `src/main/java/datingapp/app/cli/CliTextAndInput.java`

## Primary verification slice

- `src/test/java/datingapp/app/cli/MatchingHandlerTest.java`
- `src/test/java/datingapp/app/cli/MessagingHandlerTest.java`
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`
- `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`
- `src/test/java/datingapp/app/cli/MatchingCliPresenterTest.java`

## Execution slices

### Slice A — centralize CLI feedback templates

- define one helper or presenter seam for error/success formatting that handlers can share
- preserve existing CLI behavior where user-facing wording is already intentional

### Slice B — extract shared index parsing

- move parse-and-bounds-check logic into one reusable helper
- remove duplicated parsing branches from handlers

### Slice C — delete trivial wrapper behavior

- remove `copyForProfileEditing()` indirection once P03 has stabilized `User.copy()` semantics

### Slice D — simplify `CliTextAndInput`

- separate behavior-heavy pieces from the text catalog only as far as the current seam requires
- avoid broad CLI re-architecture outside the listed issues

## Dependencies and orchestration notes

- Execute this plan after P03 and after P04/P04B so handler behavior inherits settled copy and workflow semantics.
- Keep this plan single-layer: if it starts changing use-case or controller semantics, stop and re-scope.

## Out of scope

- REST/API adapter cleanup (P08)
- controller/navigation work (P07)
- deferred large-file cleanup (P13)