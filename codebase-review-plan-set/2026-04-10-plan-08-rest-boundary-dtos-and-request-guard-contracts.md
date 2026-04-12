# Plan 08: REST Boundary, DTOs, and Request-Guard Contracts

> **Date:** 2026-04-10
> **Wave:** 4
> **Priority:** Medium-High
> **Parallel-safe with:** none recommended
> **Must run after:** Wave 1 and Wave 2 plans
> **Status:** Planned

---

## Objective

Shrink the REST boundary to a cleaner transport layer by consolidating parsing and request-guard behavior, reducing `RestApiServer` sprawl, and making REST-side control flow explicit.

## Issues addressed

| Issue ID | Summary                                                   |
|----------|-----------------------------------------------------------|
| 1.3      | `RestApiServer` imports too many nested DTOs individually |
| 3.2      | UUID parsing is duplicated in the REST boundary           |
| 4.1      | `RestApiServer` owns too many API concerns                |
| 8.3      | `Optional` is used as control-flow signaling              |
| 15.1     | `RestApiDtos` is too broad to navigate comfortably        |
| 17.3     | Rate limiting relies on a non-monotonic clock source      |

## Primary source files and seams

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`

## Primary verification slice

- `src/test/java/datingapp/app/api/RestApiRequestGuardsTest.java`
- `src/test/java/datingapp/app/api/RestApiIdentityPolicyTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`

## Execution slices

### Slice A — separate transport concerns from domain/use-case orchestration

- keep `RestApiServer` as transport/bootstrap root
- extract busy route groups and parsing helpers into smaller collaborators where the seam is already obvious

### Slice B — unify boundary parsing and route-result signaling

- centralize UUID parsing
- replace `Optional`-as-side-effect helpers with an explicit route-result pattern or other direct control-flow contract

### Slice C — make DTO ownership easier to navigate

- split `RestApiDtos` by feature/domain if that is the least noisy path
- reduce import churn without replacing it with hard-to-read inline qualified names

### Slice D — fix rate-limit time math

- move window math to a monotonic time source or narrow ticker seam
- keep user-facing timestamps on the existing application clock where appropriate

## Dependencies and orchestration notes

- Run this plan only after the workflow and runtime/storage plans settle, so the REST boundary mirrors stable semantics instead of inventing them early.
- Treat `RestApiServer.java` as single-owner during execution.
- The future-only `RATE_LIMITED` mapper gap stays in P13 unless the use-case layer starts emitting that code.

## Out of scope

- runtime database lifecycle and migration safety (P09, P09B)
- broad server-side storage cleanup (P10)
- future-only throttle mapper extension (P13)