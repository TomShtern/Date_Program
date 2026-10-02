# REST API LAN Startup

One-command startup path for phone-alpha backend LAN testing.

## Quick start

```powershell
.\scripts/start_phone_alpha_backend.ps1
```

The script will:

1. Run the PostgreSQL preflight (`scripts/check_postgresql_runtime_env.ps1`).
2. Start local PostgreSQL if it is not already running.
3. Auto-compile via `mvn -q compile` when `target/classes` is missing or stale.
4. Build the runtime classpath.
5. Detect your laptop LAN IP automatically.
6. Start the REST server on `0.0.0.0:7070` with a locally supplied LAN shared secret.
7. Verify `GET /api/health` from `localhost` and from the LAN IP.
8. Print the Flutter configuration shape and required header name without exposing the secret.

To reach the backend from phones **off your home network** over a stable HTTPS URL
(release builds block cleartext HTTP), use the Tailscale Funnel path instead:
`docs/guides/public-funnel-runbook.md`.

Press **Ctrl+C** to stop the REST server. PostgreSQL remains running.
Run `.\scripts/stop_local_postgres.ps1` when you want to stop PostgreSQL.

## Required environment variables

Copy `.env.example` to `.env` for local database settings. The startup script reads
`DATING_APP_REST_SHARED_SECRET` from its process environment, then `-SharedSecret`, then the
file `%LOCALAPPDATA%\DateProgram\rest-shared-secret.txt` (create it once with
`scripts\new_rest_shared_secret.ps1`). The server reads this variable with `System.getenv`, so a
value placed in `.env` is **not** picked up. Do not put a real LAN secret in a tracked file.

| Variable | Purpose | Dev default |
|---|---|---|
| `DATING_APP_DB_PASSWORD` | PostgreSQL password | local helper's development-only default |
| `DATING_APP_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:55432/datingapp` |
| `DATING_APP_AUTH_CLERK_ISSUER` | Clerk Frontend API URL, e.g. `https://<name>.clerk.accounts.dev`. **Required**: the REST server refuses to start without it | *(empty)* |
| `DATING_APP_REST_SHARED_SECRET` | LAN shared secret (process environment only, never `.env`) | the secret file above, or a random value per session |
| `DATING_APP_PHOTO_PUBLIC_BASE_URL` | Base URL used in photo URLs; the start script sets it from `-PublicUrl` | *(derived from the request)* |
| `DATING_APP_REST_CLIENT_IP_HEADER` | Header a local tunnel uses for the real client IP (rate limiting) | *(unset: peer address)* |
| `DATING_APP_REST_ALLOWED_ORIGINS` | CORS origins (Flutter web only) | *(empty)* |

The Clerk issuer is a public URL, so it can live in `.env`. The server needs no
Clerk secret key; it checks session tokens against `<issuer>/.well-known/jwks.json`
and therefore needs outbound HTTPS to Clerk. The optional
`DATING_APP_AUTH_CLERK_JWKS_URL`, `DATING_APP_AUTH_CLERK_AUTHORIZED_PARTIES` and
`DATING_APP_AUTH_CLOCK_SKEW_SECONDS` are described in `.env.example`.

### Finding your Clerk issuer

The issuer is your instance's Frontend API URL. It is public. Either route works:

- **Dashboard:** open your application in the Clerk dashboard, go to **Domains**, and copy the
  Frontend API URL.
- **Clerk CLI:** `npx clerk@latest auth login`, then `npx clerk@latest apps list --json`. Take the
  entry in `instances` whose `environment_type` is development and read its `publishable_key`
  (`pk_test_...`). Everything after `pk_test_` is base64 of
  `<frontend-api-host>$`; decode it and prefix `https://`.

Check it before starting the server. This should return HTTP 200 with a `keys` array:

```powershell
Invoke-RestMethod "https://<name>.clerk.accounts.dev/.well-known/jwks.json"
```

### Trying a real session token without a client

Useful to prove the setup before the Flutter app exists. This creates a user in your Clerk **dev**
instance, so delete it afterwards.

1. Start the REST server with `DATING_APP_AUTH_CLERK_ISSUER` set.
2. With the Clerk CLI logged in, create a test user, a session for it (`POST /sessions`) and a token
   for that session (`POST /sessions/{session_id}/tokens`), using `npx clerk@latest api ... --app <app-id> --instance dev`.
   Keep the returned `jwt` in a local variable. Do not paste it into chat, files or logs.
3. `POST /api/auth/session` with `Authorization: Bearer <jwt>`. Expect 201 the first time and 200 after.
4. Delete the test user (`DELETE /users/{user_id}`).

Expect these properties from a dev-instance token: it is valid for 60 seconds, it is signed
RS256, and by default it has no `azp` and no `email` claim. The server accepts both absences.
To get an `email` echoed in the session response, add it as a custom claim in the dashboard's
session token settings. The backend does not need it.

Native mobile clients (Flutter Android/iOS) do **not** need CORS. The allowlist is only for Flutter web or browser-based tools.

Generate a fresh secret in the PowerShell session that will start the backend, then run the helper. The helper fails closed if no secret is supplied and does not print the value:

```powershell
$env:DATING_APP_REST_SHARED_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\scripts/start_phone_alpha_backend.ps1
```

Keep that environment variable available in the local Flutter launch session so the client can use the same value. Do not paste the secret into tracked files, screenshots, or logs.

## Windows Firewall

The first time Java binds to `0.0.0.0:7070`, Windows Defender Firewall may prompt you to allow the connection. Choose **Private networks** so the phone on the same Wi-Fi can reach the server.

Quick firewall verification from another device:

```powershell
# From the laptop, test reachability to the LAN IP
Test-NetConnection -ComputerName <LAN-IP> -Port 7070
```

## Flutter integration

After the script prints the LAN URL, configure Flutter in the same PowerShell session, using the locally held secret:

```powershell
flutter run --dart-define=DATING_APP_API_BASE_URL=http://<LAN-IP>:7070 --dart-define=DATING_APP_SHARED_SECRET=$env:DATING_APP_REST_SHARED_SECRET
```

All non-health requests must include the header:

```
X-DatingApp-Shared-Secret: <the same locally configured shared secret>
```

Every other `/api/` route (`/api/users/...`, `/api/conversations/...`, `/api/location/...`) also needs
`Authorization: Bearer <Clerk session token>`; only `/api/health` and `POST /api/auth/session`
(which verifies the token itself) are exempt.
Sign in with Clerk first, then call `POST /api/auth/session` once to get the local
user id. See `docs/api/API-SPECIFICATION.md`.

## Advanced: manual startup

If you prefer to run each step manually:

```powershell
# 1. PostgreSQL
.\scripts/check_postgresql_runtime_env.ps1
.\scripts/start_local_postgres.ps1

# 2. Compile and build classpath
mvn -q compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt" "-Dmdep.pathSeparator=;" "-Dmdep.includeScope=runtime"

# 3. Start server
$cp = 'target/classes;' + (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()
$env:DATING_APP_REST_SHARED_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
java --enable-preview --enable-native-access=ALL-UNNAMED `
  -cp $cp `
  datingapp.app.api.RestApiServer `
  --host=0.0.0.0 `
  --port=7070
```

Equivalent environment variables are also supported:

- `DATING_APP_REST_SHARED_SECRET`
- `DATING_APP_REST_ALLOWED_ORIGINS`

## Verified behavior

- `GET /api/health` does **not** require the shared secret.
- All other LAN requests must send `X-DatingApp-Shared-Secret`.
- Scoped routes identify the caller from the `Authorization: Bearer` token. If an `X-User-Id` header is also sent, it must match the token subject. `X-User-Id` alone is accepted only when no auth use cases are wired (`RestApiIdentityPolicy.resolveActingUserId`).
- CORS matters only for browser-based clients; native mobile clients do not need it.
