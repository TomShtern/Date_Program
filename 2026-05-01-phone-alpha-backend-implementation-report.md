# Phone-Alpha Backend Verified-Issues Remediation Report

Date: 2026-05-01
Scope: Fix only the verified backend issues listed in the 2026-05-01 remediation request.

## 1. Findings Fixed

1. JWT secret/config hygiene
- `config/app-config.json` and `config/app-config.postgresql.local.json` now use the development placeholder value (`development-only-jwt-secret-change-me-please`) rather than any real-looking secret.
- Environment override path is preserved via `DATING_APP_AUTH_JWT_SECRET`.
- Production guard is enforced in startup: when `DATING_APP_ENV=production`, startup fails if JWT secret is missing/blank or still the development placeholder.
- `.env.example` now documents `DATING_APP_ENV` and `DATING_APP_AUTH_JWT_SECRET`.

2. Photo stored-path persistence
- `RestApiServer.uploadPhoto` persists `photo.storedPath()` to the user model/storage.
- Public URL conversion remains response-only (`toPublicUrl` / `toPublicUrls`).
- Regression test confirms stored rows hold managed relative paths (`/photos/{userId}/{file}`), not host-bound absolute URLs.

3. Deleted-user auth hardening
- Deleted/banned users are rejected for login, refresh, `/api/auth/me`, and protected user routes with existing bearer tokens.
- Rejection is enforced through `AuthUseCases` checks in login, refresh, authenticated-user lookup, and access-token authentication.
- Added/updated route tests assert 401 rejections for deleted/banned token use.

4. AuthUser defensive profile completion
- `AuthUser.from` no longer assumes missing-fields list has a first element.
- If profile is incomplete but missing-fields is empty, state falls back safely to `needs_unknown`.

5. BCrypt cost
- BCrypt salt rounds are fixed at cost 12 (`BCRYPT_LOG_ROUNDS = 12`).

6. JWT signature comparison
- JWT signature validation now uses `MessageDigest.isEqual(...)` on decoded/computed signature bytes.
- HS256 remains in place; no new JWT library introduced.

7. Route/path hardening
- `RestApiRequestGuards.isAnonymousUserReadRoute` does not treat `/api/users/` as anonymous direct-user read.
- Static photo serving now enforces explicit filename safety in route handling (rejects `..`, separators, NUL, and unexpected characters) while keeping normalized path containment checks.
- Added static-route tests for unexpected chars and traversal/separator attempts.

8. Test cleanup
- `RestApiRelationshipRoutesTest` bearer-token helper resolves fixture user email from storage so token subject/email aligns with fixtures.
- `RestApiVerificationRoutesTest` already uses fixture email via user model.
- `JdbiAuthStorage.RefreshTokenMapper` reads `replaced_by_token_id` once into a local variable.

9. Dependency hygiene
- `org.mindrot:jbcrypt` remains at `0.4`.
- Version is centralized in `<jbcrypt.version>` and referenced from the dependency.

10. Docs
- `2026-04-30-phone-alpha-backend-api-requirements.md` now explicitly states phone-alpha account deletion must allow email reuse.
- Added acceptance checklist line requiring `docs/API-SPECIFICATION.md` update for new/modified endpoints.
- `2026-04-30-path-to-first-alpha.md` now explicitly states JWT is the identity mechanism and `X-DatingApp-Shared-Secret` is only temporary LAN/dev transport guard if retained.

## 2. Intentionally Deferred

1. Full auth-platform redesign (Clerk/OAuth/CSRF-cookie auth/signed photo URLs)
- Deferred by scope: explicitly out of phone-alpha auth remediation.

2. Broad account-deletion redesign
- Deferred by scope.
- Current path enforces deleted/banned auth rejection reliably at login/refresh/me/protected-route boundaries, which satisfies the verified issue requirement for phone alpha.

3. Optional `mvn verify -Dcheckstyle.skip=true`
- Not run because local PostgreSQL was not reachable at `localhost:55432` during this pass.

## 3. Files Updated in This Pass

- `.env.example`
- `config/app-config.json`
- `config/app-config.postgresql.local.json`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- `src/main/java/datingapp/app/usecase/auth/AuthUseCases.java`
- `src/main/java/datingapp/app/usecase/auth/JwtAuthTokenService.java`
- `src/main/java/datingapp/core/AppConfig.java`
- `src/main/java/datingapp/storage/jdbi/JdbiAuthStorage.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/test/java/datingapp/app/ConfigLoaderTest.java`
- `src/test/java/datingapp/app/api/RestApiAuthRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiPhotoRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRequestGuardsTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`
- `2026-04-30-phone-alpha-backend-api-requirements.md`
- `2026-04-30-path-to-first-alpha.md`

## 4. Verification Commands and Exact Results

1. Command
`mvn --% test -Dtest=RestApiAuthRoutesTest,RestApiPhotoRoutesTest,RestApiRequestGuardsTest -Dcheckstyle.skip=true`

Result
- Final run: **BUILD SUCCESS**
- Tests run: **19**, Failures: **0**, Errors: **0**, Skipped: **0**

2. Command
`mvn --% test -Dtest=RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest -Dcheckstyle.skip=true`

Result
- **BUILD SUCCESS**
- Tests run: **14**, Failures: **0**, Errors: **0**, Skipped: **0**

3. Command
`mvn --% test -Dtest=RestApi*Test -Dcheckstyle.skip=true`

Result
- **BUILD SUCCESS**
- Tests run: **91**, Failures: **0**, Errors: **0**, Skipped: **0**

4. PostgreSQL runtime check
`check_postgresql_runtime_env.ps1`

Result
- PostgreSQL tools found.
- Connectivity check failed: PostgreSQL not reachable at `localhost:55432`.
- Therefore optional `mvn --% verify -Dcheckstyle.skip=true` was intentionally not run in this pass.
