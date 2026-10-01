# Runtime Configuration Notes

> Verified against source 2026-09-27 (`ApplicationStartup`, `AppConfig`,
> `DatabaseManager`, `RestApiServer`, `AuthUseCases`, `UiDataAdapters`).
> If this file and code disagree, the code wins.

## Presence Indicators (adapter-based, no feature flag)

- There is **no** `datingapp.ui.presence.enabled` system property in source
  (verified by repo-wide search — zero hits).
- Chat presence goes through `UiDataAdapters.UiPresenceDataAccess`:
  - `NoOpUiPresenceDataAccess` — `isSupported() == false`,
    reason `"Presence indicators are currently unavailable."`,
    presence always `UNKNOWN`.
  - `MetricsUiPresenceDataAccess` — backed by `ActivityMetricsService`
    active-session tracking; `ONLINE` within a 2-minute inactivity
    threshold, else `AWAY`; no active session means `OFFLINE`.
- `UiAdapterCache.presence(services)` wires the metrics-backed adapter;
  `ChatViewModel` surfaces `presenceSupported` /
  `presenceUnavailableMessage` from the adapter.

## Database Query Timeout

- **Config key:** `queryTimeoutSeconds` (`AppConfig.StorageConfig`)
- **Environment override:** `DATING_APP_QUERY_TIMEOUT_SECONDS`
  (`ApplicationStartup.applyEnvInt(..., "QUERY_TIMEOUT_SECONDS", ...)` with
  `DATING_APP_` prefix)
- **Default:** `30` (`AppConfig.Builder`; `DatabaseManager` field default)
- **Runtime effect:** `DatabaseManager.applySessionQueryTimeout` runs per
  connection — PostgreSQL gets `SET search_path TO public`,
  `SET TIME ZONE 'UTC'`, `SET statement_timeout TO <millis>`; H2 gets
  `SET TIME ZONE 'UTC'` + `SET QUERY_TIMEOUT <millis>`.
  (Not a bare `SET QUERY_TIMEOUT` on PostgreSQL.)

## REST API transport (loopback default, LAN requires a secret)

`RestApiServer` defaults to the loopback address on port `7070`
(`normalizeHost`: null/blank → loopback). Loopback is the **default**,
not a guarantee:

- `restrictToLoopbackClients = isLoopbackAddress(host)`.
- The per-request localhost guard **self-disables off loopback**
  (`enforceLocalhostOnly` returns immediately when the bind is wide).
- A non-loopback bind **refuses to start** without a LAN shared secret
  (`validateTransportSecurity` throws unless `--shared-secret` /
  `DATING_APP_REST_SHARED_SECRET` is supplied).
- Once wide, every non-health request must carry
  `X-DatingApp-Shared-Secret` (`RestApiRequestGuards`), compared in
  constant time. `GET /api/health` stays unauthenticated.
- Plain HTTP, no TLS. CORS allowlist via
  `DATING_APP_REST_ALLOWED_ORIGINS` (browser clients only).
- Rate limiting is per `ip + method`, 240 requests/minute by default
  (`DEFAULT_RATE_LIMIT_REQUESTS`, `DEFAULT_RATE_LIMIT_WINDOW`);
  exceeding it returns `429 TOO_MANY_REQUESTS` with
  `X-RateLimit-Limit` / `X-RateLimit-Used` headers.
- Phone-alpha LAN path: `scripts/start_phone_alpha_backend.ps1`
  binds `0.0.0.0:7070`, health-checks loopback **and** LAN, prints
  Flutter `--dart-define=API_BASE_URL` / `API_SHARED_SECRET`.
  See `docs/guides/lan-backend-startup.md`.

### Authentication model (Clerk session tokens, verified offline)

- Clerk owns sign-up, sign-in, passwords and sessions. The backend issues no
  tokens and stores no credentials.
- `AuthUseCases` + `AccessTokenVerifier` (`app/usecase/auth/`) own the flow.
  `ClerkJwtVerifier` (Nimbus JOSE+JWT) checks the RS256 signature against the
  issuer's JWKS, exact `iss`, `exp`/`nbf` against `AppClock` with
  `clockSkewSeconds` of leeway (default 5), and a non-blank `sub`.
  `azp` must match `clerkAuthorizedParties` only when that list is set
  and the token carries an `azp`.
- Config (`AppConfig.AuthConfig`): `clerkIssuer` (Clerk Frontend API URL, e.g.
  `https://<name>.clerk.accounts.dev`), `clerkJwksUrl` (defaults to
  `<issuer>/.well-known/jwks.json`), `clerkAuthorizedParties`,
  `clockSkewSeconds`. Env overrides: `DATING_APP_AUTH_CLERK_ISSUER`,
  `DATING_APP_AUTH_CLERK_JWKS_URL`, `DATING_APP_AUTH_CLERK_AUTHORIZED_PARTIES`,
  `DATING_APP_AUTH_CLOCK_SKEW_SECONDS`.
- A blank issuer is legal for the CLI and desktop. `RestApiServer.main()`
  refuses to start without one, and with none configured the verifier rejects
  every token.
- A Clerk `sub` maps to the local user UUID through the `clerk_identities`
  table (`AuthStorage`). `POST /api/auth/session` creates the local profile on
  first use (`INCOMPLETE`, no email, no birth date). `validation.minAge` is
  enforced later, when the client sets a birth date on the profile.
- Deleted (`deleted_at != null`) or `BANNED` users are rejected at the
  session route and every protected route (`isDeletedOrBanned`). A valid token
  with no live profile gets 401 `NOT_PROVISIONED`.
- Protected routes use `Authorization: Bearer <Clerk session token>`
  (`RestApiIdentityPolicy`); `X-User-Id` is only the legacy fallback
  when no `AuthUseCases` is wired, plus a spoof-check against the
  token subject. Scoped routes reject subject/path mismatches with 403.
  The resolved user id is memoized per request in the ctx attribute
  `RestApiIdentityPolicy.ATTR_ACTING_USER_ID`.
- Error bodies are `{"code": "...", "message": "..."}`
  (`RestApiDtos.ErrorResponse`), e.g. `BAD_REQUEST`/`UNAUTHORIZED`/
  `FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`TOO_MANY_REQUESTS`/`INTERNAL_ERROR`.

### Data Persistence

- Runtime storage is PostgreSQL via
  `StorageFactory.buildSqlDatabase(...)` (bootstrap path in
  `ApplicationStartup.initialize()`); `buildH2(...)` / `buildInMemory(...)`
  are compatibility/test paths.
- Auth state is persisted in one table, `clerk_identities` (Clerk user id to
  local user UUID). Migration V20 drops the old `user_credentials` and
  `auth_refresh_tokens` tables; that drop is irreversible.
- Account deletion soft-deletes the graph in one transaction
  (`JdbiAccountCleanupStorage`): user row gets `deleted_at`,
  `state = BANNED`, `email/phone = NULL` (so unique constraints allow
  reuse); the `clerk_identities` row is hard-deleted.

## Desktop Session Model (`AppSession`)

`AppSession` is the **JavaFX/CLI** in-memory session holder
(singleton `AtomicReference<User>` + listeners), documented in its own
javadoc as a simplified development/testing model: state is lost on
shutdown, unsigned/unencrypted, and not a production session store.
It is **separate** from REST auth above — the REST API authenticates
per request with Bearer JWTs, not via `AppSession`.

### Design Constraints
- **In-memory only:** Session state exists only in application RAM (`AtomicReference<User>` + listeners).
- **No persistence:** Sessions are lost on shutdown.
- **No encryption:** Session data is not encrypted or signed.
- **Not production-ready:** This model is unsuitable for real-world applications handling sensitive user data.

### Use Cases
- Local development and manual testing
- Technology demonstrations and prototyping
- Training and educational contexts
- Feature-gating scenarios (e.g., restricting UI access to logged-in users)

### Related Components
- `AppSession` (singleton session holder for desktop/CLI)
- `RestApiServer` + `AuthUseCases` (Clerk session-token auth for the Flutter client)
- `SafetyHandler` / `SafetyViewModel` (block/report/verify flows; no
  `[SIMULATED]` marker exists in current source — verified by search)

### Migration Path for Production
A production application would replace this model with:
- Persistent session store (e.g., Redis, database)
- Cryptographic session tokens (e.g., JWT with RS256 or similar)
- CSRF protection (SameSite cookies, CSRF tokens)
- Comprehensive authentication (OAuth 2.0, SAML, or similar)
- Rate limiting and abuse prevention per user
- Audit logging of all session-related actions