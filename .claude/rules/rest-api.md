---
paths:
  - "src/main/java/datingapp/app/api/**"
  - "src/test/java/datingapp/app/api/**"
  - "start_phone_alpha_backend.ps1"
---

## REST transport and the Flutter frontend

`datingapp.app.api.RestApiServer` (Javalin) is the backend for the **separate
Flutter dating frontend**, which reaches it over LAN. Treat the two as one
system: an API change here is a frontend change there.

### The transport-security invariant

Loopback is the **default**, not a guarantee:

```java
this.host = normalizeHost(host);   // null/blank -> InetAddress.getLoopbackAddress()
this.restrictToLoopbackClients = RestApiRequestGuards.isLoopbackAddress(this.host);
...
if (!restrictToLoopbackClients && lanSharedSecret == null) {
    throw new IllegalStateException(
            "Non-loopback REST binding requires a LAN shared secret ...");
}
```

Two consequences that are easy to get backwards:

1. **The per-request localhost guard self-disables off loopback.**
   `enforceLocalhostOnly` returns immediately when `restrictToLoopbackClients` is
   false. The shared secret *replaces* that guard — it is not defence in depth
   layered on top of it.
2. **Binding wide is supported, not forbidden.** `--host=0.0.0.0` plus
   `--shared-secret` (or the `ENV_REST_SHARED_SECRET` environment variable) is the
   intended phone-alpha configuration. Never widen the bind without the secret:
   `validateTransportSecurity()` refuses to start, and that refusal is the
   feature.

Startup options are parsed from `--host=`, `--port=`, `--shared-secret` and the
allowed CORS origin list; a blank host falls back to loopback.

### The phone-alpha path

`start_phone_alpha_backend.ps1` detects the laptop LAN IP, starts the server on
`0.0.0.0:7070`, health-checks `/api/health` on loopback **and** LAN, and prints
the Flutter `dart-define` values:

```
--dart-define=API_BASE_URL=http://<lan-ip>:<port>
--dart-define=API_SHARED_SECRET=<secret>
```

**Never echo, commit or paste a real shared secret** into documents, logs, test
fixtures, screenshots or a bug report. Once the bind is wide, that secret is the
only thing in front of the API. The repository `.env` is ignored for this reason;
`.env.example` is the file that may carry placeholders.

### Tests

Build the graph with `RestApiTestFixture` rather than hand-wiring a
`ServiceRegistry` per test.
