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

### Authentication model (HS256 JWT + opaque refresh, not simulated)

- `AuthUseCases` + `AuthTokenService` (`app/usecase/auth/`) own the flow.
- Access tokens are HS256 JWTs (`alg HS256`, HMAC-SHA256 over
  `jwtSecret`), carrying `sub`/`email`/`iss`/`iat`/`exp`;
  issuer `dating-app-phone-alpha` by default, TTL
  `accessTokenTtlSeconds = 900` (15 min).
- Refresh tokens are opaque 32-byte random values, SHA-256-hashed at
  rest (`AuthStorage`), TTL `refreshTokenTtlDays = 30`.
  Refresh is **single-use rotation**: each success inserts a new token
  and revokes the old one (`revoked_at` + `replaced_by_token_id`).
- Passwords are BCrypt-hashed (12 rounds); minimum length
  `minPasswordLength = 12`; signup also enforces
  `validation.minAge = 18` via `AppClock.today()`.
- Deleted (`deleted_at != null`) or `BANNED` users are rejected at
  login, refresh, `me`, and every protected route
  (`isDeletedOrBanned`).
- Protected routes use `Authorization: Bearer <accessToken>`
  (`RestApiIdentityPolicy`); `X-User-Id` is only the legacy fallback
  when no `AuthUseCases` is wired, plus a spoof-check against the
  token subject. Scoped routes reject subject/path mismatches with 403.
- Error bodies are `{"code": "...", "message": "..."}`
  (`RestApiDtos.ErrorResponse`), e.g. `BAD_REQUEST`/`UNAUTHORIZED`/
  `FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`TOO_MANY_REQUESTS`/`INTERNAL_ERROR`.

### Data Persistence

- Runtime storage is PostgreSQL via
  `StorageFactory.buildSqlDatabase(...)` (bootstrap path in
  `ApplicationStartup.initialize()`); `buildH2(...)` / `buildInMemory(...)`
  are compatibility/test paths.
- Auth state is persisted: password hashes in `user_credentials`,
  refresh tokens in `auth_refresh_tokens` (hashed, revocable).
- Account deletion soft-deletes the graph in one transaction
  (`JdbiAccountCleanupStorage`): user row gets `deleted_at`,
  `state = BANNED`, `email/phone = NULL` (so unique constraints allow
  reuse); credentials are hard-deleted; refresh tokens revoked.

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
- `RestApiServer` + `AuthUseCases` (Bearer JWT auth for the Flutter client)
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