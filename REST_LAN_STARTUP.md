# REST API LAN Startup

One-command startup path for phone-alpha backend LAN testing.

## Quick start

```powershell
.\start_phone_alpha_backend.ps1
```

The script will:

1. Run the PostgreSQL preflight (`check_postgresql_runtime_env.ps1`).
2. Start local PostgreSQL if it is not already running.
3. Auto-compile via `mvn -q compile` when `target/classes` is missing or stale.
4. Build the runtime classpath.
5. Detect your laptop LAN IP automatically.
6. Start the REST server on `0.0.0.0:7070` with the LAN shared secret.
7. Verify `GET /api/health` from `localhost` and from the LAN IP.
8. Print the exact Flutter `dart-define` commands and required headers.

Press **Ctrl+C** to stop the REST server. PostgreSQL remains running.
Run `.\stop_local_postgres.ps1` when you want to stop PostgreSQL.

## Required environment variables

Copy `.env.example` to `.env` and adjust values.

| Variable | Purpose | Dev default |
|---|---|---|
| `DATING_APP_DB_PASSWORD` | PostgreSQL password | `datingapp` |
| `DATING_APP_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:55432/datingapp` |
| `DATING_APP_REST_SHARED_SECRET` | LAN shared secret | `lan-dev-secret` |
| `DATING_APP_REST_ALLOWED_ORIGINS` | CORS origins (Flutter web only) | *(empty)* |

Native mobile clients (Flutter Android/iOS) do **not** need CORS. The allowlist is only for Flutter web or browser-based tools.

## Windows Firewall

The first time Java binds to `0.0.0.0:7070`, Windows Defender Firewall may prompt you to allow the connection. Choose **Private networks** so the phone on the same Wi-Fi can reach the server.

Quick firewall verification from another device:

```powershell
# From the laptop, test reachability to the LAN IP
Test-NetConnection -ComputerName <LAN-IP> -Port 7070
```

## Flutter integration

After the script prints the LAN URL, set the Flutter base URL and shared secret:

```bash
flutter run \
  --dart-define=API_BASE_URL=http://<LAN-IP>:7070 \
  --dart-define=API_SHARED_SECRET=lan-dev-secret
```

All non-health requests must include the header:

```
X-DatingApp-Shared-Secret: lan-dev-secret
```

## Advanced: manual startup

If you prefer to run each step manually:

```powershell
# 1. PostgreSQL
.\check_postgresql_runtime_env.ps1
.\start_local_postgres.ps1

# 2. Compile and build classpath
mvn -q compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt" "-Dmdep.pathSeparator=;" "-Dmdep.includeScope=runtime"

# 3. Start server
$cp = 'target/classes;' + (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()
java --enable-preview --enable-native-access=ALL-UNNAMED `
  -cp $cp `
  datingapp.app.api.RestApiServer `
  --host=0.0.0.0 `
  --port=7070 `
  --shared-secret=lan-dev-secret
```

Equivalent environment variables are also supported:

- `DATING_APP_REST_SHARED_SECRET`
- `DATING_APP_REST_ALLOWED_ORIGINS`

## Verified behavior

- `GET /api/health` does **not** require the shared secret.
- All other LAN requests must send `X-DatingApp-Shared-Secret`.
- Mutating/scoped routes still use `X-User-Id` as the acting-user header.
- CORS matters only for browser-based clients; native mobile clients do not need it.
