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

Press **Ctrl+C** to stop the REST server. PostgreSQL remains running.
Run `.\scripts/stop_local_postgres.ps1` when you want to stop PostgreSQL.

## Required environment variables

Copy `.env.example` to `.env` for local database settings. The startup script reads
`DATING_APP_REST_SHARED_SECRET` from its process environment (or `-SharedSecret`);
do not put a real LAN secret in a tracked file.

| Variable | Purpose | Dev default |
|---|---|---|
| `DATING_APP_DB_PASSWORD` | PostgreSQL password | local helper's development-only default |
| `DATING_APP_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:55432/datingapp` |
| `DATING_APP_REST_SHARED_SECRET` | LAN shared secret | supply a fresh random value for each LAN session |
| `DATING_APP_REST_ALLOWED_ORIGINS` | CORS origins (Flutter web only) | *(empty)* |

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
flutter run --dart-define=API_BASE_URL=http://<LAN-IP>:7070 --dart-define=API_SHARED_SECRET=$env:DATING_APP_REST_SHARED_SECRET
```

All non-health requests must include the header:

```
X-DatingApp-Shared-Secret: <the same locally configured shared secret>
```

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
- Mutating/scoped routes still use `X-User-Id` as the acting-user header.
- CORS matters only for browser-based clients; native mobile clients do not need it.
