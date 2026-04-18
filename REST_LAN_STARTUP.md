# REST API LAN Startup

This is the verified startup path for using the REST adapter from a phone, Flutter app, or other LAN client.

## Preconditions

- Local PostgreSQL runtime is required for the current default app configuration.
- Non-loopback REST binding now requires a shared secret.
- Browser-based clients also require an explicit CORS allowlist.

## 1. Start PostgreSQL

```powershell
.\check_postgresql_runtime_env.ps1
.\start_local_postgres.ps1
```

## 2. Compile and build the runtime classpath

```powershell
mvn -q -DskipTests compile
mvn -q dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt" "-Dmdep.pathSeparator=;" "-Dmdep.includeScope=runtime"
```

## 3. Start the REST server in LAN mode

PowerShell:

```powershell
$cp = 'target/classes;' + (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()

java --enable-preview --enable-native-access=ALL-UNNAMED `
  -cp $cp `
  datingapp.app.api.RestApiServer `
  --host=0.0.0.0 `
  --port=7070 `
  --shared-secret=lan-dev-secret `
  --allowed-origins=http://localhost:3000,http://192.168.1.194:3000
```

Equivalent environment variables are also supported:

- `DATING_APP_REST_SHARED_SECRET`
- `DATING_APP_REST_ALLOWED_ORIGINS`

## 4. Client requirements

- Health check: `GET /api/health` does not require the shared secret.
- All other LAN requests must send `X-DatingApp-Shared-Secret: <secret>`.
- Mutating/scoped routes still use `X-User-Id` as the acting-user header.
- CORS matters only for browser-based clients, Flutter web, or web tooling. Native mobile clients do not need CORS, but they still need the shared secret in LAN mode.

## 5. Verified LAN check

Verified on `2026-04-18` against the machine LAN IP `192.168.1.194`:

- `GET http://192.168.1.194:7070/api/health` returned `200` without a secret.
- `GET http://192.168.1.194:7070/api/users` returned `403` without `X-DatingApp-Shared-Secret`.
- `GET http://192.168.1.194:7070/api/users` returned `200` with `X-DatingApp-Shared-Secret: lan-dev-secret`.
- `OPTIONS http://192.168.1.194:7070/api/users` from origin `http://192.168.1.194:3000` returned `200` with `Access-Control-Allow-Origin: http://192.168.1.194:3000`.

## 6. Flutter notes

- Flutter mobile / Android emulator / physical phone: use the LAN IP and send the shared-secret header on non-health requests.
- Flutter web: add the dev origin to `--allowed-origins` and send the shared-secret header from the client.
- If the LAN bind host is loopback-only again, remove the phone/LAN path and use `localhost` clients only.