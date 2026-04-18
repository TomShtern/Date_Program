# 2026-04-18 Backend Audit And Remediation

> Scope: backend only (`core`, `app`, `storage`, REST adapter, runtime scripts).
> Source of truth: code and fresh verification only.
> Explicit non-issue for now: the current Israel-only location scope is acceptable and is not treated as a defect in this audit.

## Method

- Performed a code-first backend audit across workflow, storage/runtime, and REST adapter seams.
- Verified candidate findings against the owning source files before accepting them.
- Ran the repo-level verification path with `run_verify.ps1` to anchor the audit in fresh execution evidence.

## Findings

### 1. Popular-city ordering regression breaks the local verification gate

- Status: Resolved
- Severity: High
- Why it matters: the current full local verification path is red because `LocalGeocodingServiceTest.blankQueryReturnsPopularLocalCitiesAsCityPrecisionResults` expects the curated popular-city order to start with Tel Aviv, but the current implementation returns Haifa first.
- Root cause: `LocationService.getPopularCities(...)` sorts equal-priority cities alphabetically instead of preserving the curated list order that the blank-query experience depends on.
- Fix: `LocationService.getPopularCities(...)` now preserves the curated declaration order within the same priority tier, and `LocationServiceTest` now has a direct regression test for that contract.
- Evidence:
  - `src/test/java/datingapp/core/profile/LocalGeocodingServiceTest.java`
  - `src/test/java/datingapp/core/profile/LocationServiceTest.java`
  - `src/main/java/datingapp/core/profile/LocationService.java`

### 2. Duplicate likes stop being idempotent when the daily limit is exhausted

- Status: Resolved
- Severity: High
- Why it matters: a retry of an already-persisted swipe can fail as `Daily like limit reached` instead of returning the previously stored like, which breaks idempotency and can surface incorrect failures to the REST adapter or other callers.
- Root cause: daily-limit checks run before the duplicate-like path in both `MatchingService.recordLike(...)` and `MatchingUseCases.recordLike(...)`.
- Fix: `MatchingService` now checks for an already-persisted like before daily-limit rejection in both the primitive `recordLike(...)` path and `processSwipe(...)`, and `MatchingUseCases.recordLike(...)` skips its precheck for already-persisted swipes.
- Evidence:
  - `src/main/java/datingapp/core/matching/MatchingService.java`
  - `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
  - `src/main/java/datingapp/core/storage/InteractionStorage.java`

### 3. Storage validation accepts mismatched database dialect and JDBC URL pairs

- Status: Resolved
- Severity: High
- Why it matters: runtime config can declare `POSTGRESQL` with an H2 URL or vice versa, pass validation, and then proceed into runtime setup with conflicting semantics.
- Root cause: `AppConfigValidator.validateStorage(...)` validates dialect and JDBC URL independently and never checks that they agree.
- Fix: `AppConfigValidator.validateStorage(...)` now resolves both the configured dialect and the JDBC URL dialect and rejects mismatches directly.
- Evidence:
  - `src/main/java/datingapp/core/AppConfigValidator.java`
  - `src/main/java/datingapp/storage/DatabaseDialect.java`
  - `src/main/java/datingapp/storage/DatabaseManager.java`

### 4. Non-loopback REST mode trusts `X-User-Id` without a LAN secret

- Status: Resolved
- Severity: High
- Why it matters: as soon as the server is bound to a non-loopback host for phone access, any client on the LAN can impersonate any user by sending a chosen `X-User-Id` header.
- Root cause: localhost-only enforcement is disabled for non-loopback hosts, but the request identity model still treats `X-User-Id` as the only identity signal.
- Fix: non-loopback startup now fails without a configured shared secret, and non-health LAN requests must send `X-DatingApp-Shared-Secret` before the request reaches the scoped identity guard.
- Evidence:
  - `src/main/java/datingapp/app/api/RestApiServer.java`
  - `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
  - `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`

### 5. REST adapter has no CORS or preflight support

- Status: Resolved
- Severity: Medium
- Why it matters: browser-based clients and Flutter web tooling cannot call the API cross-origin, which blocks the intended LAN and Flutter readiness path.
- Root cause: the REST bootstrap config only sets JSON mapping and content type; no CORS configuration or `OPTIONS` preflight handling exists.
- Fix: `RestApiServer` now configures explicit CORS rules for allowlisted origins, exposes rate-limit headers, and allows `OPTIONS` preflight requests through the guard stack without secret or rate-limit failures.
- Evidence:
  - `src/main/java/datingapp/app/api/RestApiServer.java`
  - `src/main/java/datingapp/app/api/RestRouteSupport.java`

## Verification Outcome

- Targeted tests passed:
  - `LocationServiceTest`, `LocalGeocodingServiceTest`
  - `MatchingServiceTest`, `MatchingUseCasesTest`
  - `AppConfigValidatorTest`
  - `RestApiRequestGuardsTest`, `RestApiHealthRoutesTest`
- LAN verification passed on `192.168.1.194:7070`:
  - `GET /api/health` returned `200` without a secret
  - `GET /api/users` returned `403` without `X-DatingApp-Shared-Secret`
  - `GET /api/users` returned `200` with `X-DatingApp-Shared-Secret: lan-dev-secret`
  - `OPTIONS /api/users` from `http://192.168.1.194:3000` returned `200` with `Access-Control-Allow-Origin`
- Repo-level verification passed:
  - `run_verify.ps1`
  - Maven quality gate: `1866` tests, `0` failures, `0` errors, `2` skipped
  - PostgreSQL smoke verification: `2` tests, `0` failures, `0` errors

## Deliverables From This Pass

- Backend regressions fixed in the owning code paths.
- Verified LAN startup guide added at `REST_LAN_STARTUP.md`.
- Roadmap should now treat green verification and LAN REST readiness as completed, not pending.