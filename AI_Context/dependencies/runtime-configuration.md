# Runtime Configuration Notes

## Presence Indicator Feature Flag

- **Key:** `datingapp.ui.presence.enabled`
- **Type:** Java system property (not `AppConfig`)
- **Default:** `false`
- **Current behavior when disabled:** UI uses a no-op presence data access path and shows an explicit disabled/unavailable presence status message rather than synthetic online state.

## Database Query Timeout

- **Config key:** `queryTimeoutSeconds` (`AppConfig.StorageConfig`)
- **Environment override:** `DATING_APP_QUERY_TIMEOUT_SECONDS`
- **Default:** `30`
- **Runtime effect:** each database connection applies `SET QUERY_TIMEOUT <millis>` (converted from seconds to milliseconds) so JDBI-backed queries use the configured statement timeout.

## REST API (Development-Only)

The REST API (`datingapp.app.api.RestApiServer`) is provided for development, testing, and demonstration purposes only. It is **not suitable for production use**.

### Binding Posture
- **Localhost-only binding:** The server binds exclusively to the loopback interface (`127.0.0.1`) on the configured REST port (default `7070`).
- **Not externally accessible:** External hosts cannot connect to the server.
- **No TLS/HTTPS:** Server operates over plain HTTP.

### Authentication Model
The REST API uses a **simulated session model** backed by `AppSession`. Login operations update the in-memory session but do not enforce comprehensive authentication:
- No JWT or token-signing enforcement
- No CSRF protection
- No rate limiting per user identity (only per IP)
- Session expires on application shutdown

### Data Persistence
- Persistence depends on the configured storage backend (for example, local H2 file-backed or in-memory modes in development).
- REST session/auth state itself remains in-memory via `AppSession` and resets on shutdown.

## Authentication & Session Model

The application implements a **simulated, development-only session model** via `AppSession`:

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
- `AppSession` (singleton session holder)
- `RestApiServer` (dev-only REST API using simulated login)
- Moderation simulation features in `SafetyHandler` and `SafetyViewModel` (all operations marked `[SIMULATED]`)

### Migration Path for Production
A production application would replace this model with:
- Persistent session store (e.g., Redis, database)
- Cryptographic session tokens (e.g., JWT with RS256 or similar)
- CSRF protection (SameSite cookies, CSRF tokens)
- Comprehensive authentication (OAuth 2.0, SAML, or similar)
- Rate limiting and abuse prevention per user
- Audit logging of all session-related actions