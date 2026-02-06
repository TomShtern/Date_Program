# Implementation Plan — Security & Privacy Fixes (PLAN_FIX_SECURITY)

**Generated:** 2026-02-06
**Source Audit:** `AUDIT_PROBLEMS_SECURITY.md`
**Scope:** 14 findings (H-09, H-18, M-25, API-01 through API-11)
**Estimated Effort:** ~30-40 hours total

> **Architecture Note:** The REST API layer (`app/api/`) is built on **Javalin 6.6.0**. All route handlers are in three classes: `UserRoutes`, `MatchRoutes`, `MessagingRoutes`, registered in `RestApiServer.registerRoutes()`. The server has **zero** authentication, authorization, rate limiting, or input sanitization.

---

## Pre-Implementation Checklist

- [ ] Run `mvn test` and confirm all 736 tests pass (baseline)
- [ ] Run `mvn spotless:apply && mvn verify` (baseline formatting)
- [ ] Create a git branch: `fix/security-audit-fixes`

---

## Implementation Order

The fixes are ordered for maximum safety — standalone cryptography/config fixes first, then foundational auth infrastructure that later fixes depend on:

| Phase | Fixes | Description |
|-------|-------|-------------|
| 1 — Quick Wins | Fix 1-3 | SecureRandom, hardcoded password, PII logging |
| 2 — Auth Foundation | Fix 4-5 | Authentication middleware + authorization checks |
| 3 — API Hardening | Fix 6-11 | Rate limiting, daily limits, input sanitization, HTTPS, sessions, CSRF |
| 4 — Access Control | Fix 12-14 | IDOR protection, sensitive data logging, MatchRoutes.from() fix |

---

## Fix 1: H-09 — Verification Code Uses `java.util.Random` (Not Cryptographically Secure)

**Severity:** High
**File:** `src/main/java/datingapp/core/TrustSafetyService.java`
**Lines:** 13 (import), 30 (field), 34 (constructor), 55 (constructor), 77 (usage)
**Risk:** Verification codes are predictable; an attacker who knows the seed or observes a few outputs can predict future codes

### Problem

The class uses `java.util.Random` for generating 6-digit verification codes. `Random` uses a 48-bit LCG (linear congruential generator) — trivially predictable.

```java
// CURRENT — line 13
import java.util.Random;

// CURRENT — line 30
private final Random random;

// CURRENT — line 34 (test constructor)
TrustSafetyService() {
    this(DEFAULT_VERIFICATION_TTL, new Random());
}

// CURRENT — line 55 (production constructor)
public TrustSafetyService(
        ReportStorage reportStorage,
        /*...*/) {
    this(/*...*/, new Random());
}

// CURRENT — line 77 (generation)
public String generateVerificationCode() {
    int value = random.nextInt(1_000_000);
    return String.format("%06d", value);
}
```

### Fix

Replace `java.util.Random` with `java.security.SecureRandom`. The API is identical (`nextInt(int bound)`) so no usage changes needed beyond the type.

**Step 1:** Change the import (line 13):
```java
// BEFORE
import java.util.Random;

// AFTER
import java.security.SecureRandom;
```

**Step 2:** Change the field type (line 30):
```java
// BEFORE
private final Random random;

// AFTER
private final SecureRandom random;
```

**Step 3:** Update the test-only no-arg constructor (line 33-35):
```java
// BEFORE
TrustSafetyService() {
    this(DEFAULT_VERIFICATION_TTL, new Random());
}

// AFTER
TrustSafetyService() {
    this(DEFAULT_VERIFICATION_TTL, new SecureRandom());
}
```

**Step 4:** Update the test-only 2-arg constructor signature (line 38):
```java
// BEFORE
TrustSafetyService(Duration verificationTtl, Random random) {

// AFTER
TrustSafetyService(Duration verificationTtl, SecureRandom random) {
```

**Step 5:** Update the production constructor (lines 54-56):
```java
// BEFORE (inside the constructor delegation at line 55)
                new Random());

// AFTER
                new SecureRandom());
```

**Step 6:** Update the full 7-arg constructor signature (line 65):
```java
// BEFORE
            Random random) {

// AFTER
            SecureRandom random) {
```

**Step 7:** Update test file `TrustSafetyServiceTest.java` (line 290):
```java
// BEFORE
TrustSafetyService trustSafetyService = new TrustSafetyService(Duration.ofMinutes(15), new Random(123));

// AFTER
// Note: SecureRandom does not support seeded construction the same way.
// For deterministic test behavior, use SecureRandom with a fixed seed via setSeed().
java.security.SecureRandom seededRandom = new java.security.SecureRandom();
seededRandom.setSeed(123L);
TrustSafetyService trustSafetyService = new TrustSafetyService(Duration.ofMinutes(15), seededRandom);
```

Also update the import at the top of the test file — add:
```java
import java.security.SecureRandom;
```
And remove (if no longer used):
```java
import java.util.Random;
```

> **Note:** `DailyService.java` (line 130) also uses `new Random(seed)` for deterministic daily picks. That usage is intentional (reproducible selection, not security-sensitive) — do NOT change it.

### Test

```bash
mvn test -pl . -Dtest=TrustSafetyServiceTest
```

- Verify "Returns false when code expired" still passes
- Verify "Returns false when code mismatches" still passes
- Add a new test:

```java
@Test
@DisplayName("Generated code is 6-digit string")
void generatedCodeIsSixDigitString() {
    TrustSafetyService service = new TrustSafetyService();
    for (int i = 0; i < 100; i++) {
        String code = service.generateVerificationCode();
        assertEquals(6, code.length(), "Code should be 6 digits");
        assertTrue(code.matches("\\d{6}"), "Code should contain only digits");
    }
}
```

---

## Fix 2: H-18 — Hardcoded Password in Development

**Severity:** High
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Lines:** 16, 23-46
**Risk:** Hardcoded credentials in source code; if the app is ever deployed beyond local dev, the default password provides zero protection

### Problem

```java
// CURRENT — line 16
private static final String DEFAULT_DEV_PASSWORD = "dev";

// CURRENT — lines 27-46 (getConfiguredPassword)
private static String getConfiguredPassword() {
    String envPassword = System.getenv("DATING_APP_DB_PASSWORD");
    if (envPassword != null && !envPassword.isEmpty()) {
        return envPassword;
    }
    if (isTestUrl(jdbcUrl)) {
        return "";
    }
    if (isLocalFileUrl(jdbcUrl)) {
        return DEFAULT_DEV_PASSWORD;  // Falls back to "dev"
    }
    throw new IllegalStateException(
            "Database password must be provided via DATING_APP_DB_PASSWORD environment variable");
}
```

The logic already has the env var check and the fail-fast for remote URLs. The issue is the `DEFAULT_DEV_PASSWORD = "dev"` fallback for local file URLs.

### Fix

**Approach:** Log a warning when using the dev password, and make the constant private + documented. The app is an embedded H2 file database for local development — removing the fallback entirely would break the dev workflow. Instead, add a loud warning.

**Step 1:** Add a logger field (the file currently uses `java.util.logging.Logger` in `shutdown()` — keep consistent):

```java
// ADD after line 18 (after SQL_IDENTIFIER pattern)
private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
```

> **Note:** `DatabaseManager` already imports `java.util.logging.Logger` and `java.util.logging.Level` (lines 8-9). No new imports needed.

**Step 2:** Update `getConfiguredPassword()` to log a warning (lines 39-42):
```java
// BEFORE
    if (isLocalFileUrl(jdbcUrl)) {
        return DEFAULT_DEV_PASSWORD;
    }

// AFTER
    if (isLocalFileUrl(jdbcUrl)) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.warning(
                    "Using default dev password for local database. "
                    + "Set DATING_APP_DB_PASSWORD environment variable for production use.");
        }
        return DEFAULT_DEV_PASSWORD;
    }
```

**Step 3:** Rename constant to clarify intent:
```java
// BEFORE
private static final String DEFAULT_DEV_PASSWORD = "dev";

// AFTER
@SuppressWarnings("java:S2068") // Sonar: hardcoded password — intentional for local H2 dev only
private static final String LOCAL_DEV_FALLBACK_PASSWORD = "dev";
```

Update all references (only one reference at the `return` statement).

### Test

```bash
mvn test -pl . -Dtest=DatabaseManager*
```

Manual verification: run the app, check logs for the warning message when no env var is set.

---

## Fix 3: M-25 / API-11 — Logs May Include PII / Sensitive Data Logging

**Severity:** Medium (M-25), Medium (API-11)
**Files:** Multiple — `TrustSafetyService.java`, `RestApiServer.java`, `MatchingService.java`
**Risk:** User IDs (UUIDs) appear in log messages; while UUIDs are pseudonymous, they are still personally identifiable when correlated with other data

### Problem

Several log statements include user UUIDs at INFO level:

```java
// TrustSafetyService.java line 164
logger.info("Match {} transitioned to BLOCKED by user {}", match.getId(), blockerId);

// TrustSafetyService.java line 207
logger.info("User {} blocked user {}", blockerId, blockedId);

// TrustSafetyService.java line 240
logger.info("User {} unblocked user {}", blockerId, blockedId);

// RestApiServer.java line 132 — logs full exception (may contain user data in message)
logger.error("Unhandled exception", e);
```

### Fix

**Strategy:** Downgrade user-ID-bearing messages from `info` → `debug`, and sanitize the exception handler to avoid leaking user data.

**Step 1:** `TrustSafetyService.java` — downgrade INFO to DEBUG (lines 164, 207, 240):

```java
// BEFORE — line 164
logger.info("Match {} transitioned to BLOCKED by user {}", match.getId(), blockerId);

// AFTER
logger.debug("Match {} transitioned to BLOCKED by user {}", match.getId(), blockerId);
```

```java
// BEFORE — line 207
logger.info("User {} blocked user {}", blockerId, blockedId);

// AFTER
logger.debug("User {} blocked user {}", blockerId, blockedId);
```

```java
// BEFORE — line 240
logger.info("User {} unblocked user {}", blockerId, blockedId);

// AFTER
logger.debug("User {} unblocked user {}", blockerId, blockedId);
```

> The line 242 `logger.debug(...)` is already at debug level — no change needed.

**Step 2:** `RestApiServer.java` — sanitize error handler (lines 131-135):

```java
// BEFORE
app.exception(Exception.class, (e, ctx) -> {
    logger.error("Unhandled exception", e);
    ctx.status(500);
    ctx.json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
});

// AFTER
app.exception(Exception.class, (e, ctx) -> {
    logger.error("Unhandled exception in {} {}: {}",
            ctx.method(), ctx.path(), e.getClass().getSimpleName());
    logger.debug("Full exception details", e);
    ctx.status(500);
    ctx.json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
});
```

This logs the exception class at ERROR level (useful for alerting) but defers the full stack trace (which may contain user data in messages) to DEBUG level.

**Step 3:** `MatchingService.java` line 139 — already at `warn` level but includes match ID:

```java
// BEFORE
logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());

// AFTER — keep warn level (it's operationally important) but omit exception message which may have user data
logger.warn("Match save conflict for match ID {}", match.getId());
logger.debug("Match save conflict details", ex);
```

### Test

```bash
mvn test -pl . -Dtest=TrustSafetyServiceTest,RestApiRoutesTest
```

No behavioral changes — only log levels change. Verify tests still pass.

---

## Fix 4: API-01 — No Authentication or Authorization

**Severity:** Critical
**File:** `src/main/java/datingapp/app/api/RestApiServer.java` (new middleware), new file `src/main/java/datingapp/app/api/AuthMiddleware.java`
**Lines:** `RestApiServer.java` lines 57-60 (Javalin config), 96-118 (route registration)
**Risk:** Complete impersonation — any caller can act as any user, read any data, send messages as anyone

### Problem

`RestApiServer.registerRoutes()` registers all endpoints with zero authentication. The user ID is taken from the URL path parameter (`ctx.pathParam("id")`), meaning the "authenticated" user is whoever the caller claims to be.

### Fix

**Approach:** Implement a simple token-based authentication using Javalin's `beforeMatched` handler. This is Phase 1 — a lightweight approach suitable for the current app stage. The design:

1. Create an `AuthMiddleware` class that validates a `Bearer` token in the `Authorization` header
2. Tokens are simple UUID-based session tokens stored in a `ConcurrentHashMap`
3. Add a `/api/auth/login` endpoint that returns a token for a valid user
4. Protected routes extract the authenticated user from the token, not from URL path params

**Step 1:** Create `src/main/java/datingapp/app/api/AuthMiddleware.java`:

```java
package datingapp.app.api;

import datingapp.core.User;
import datingapp.core.UserState;
import datingapp.core.storage.UserStorage;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple token-based authentication middleware for the REST API.
 *
 * <p>Validates Bearer tokens in the Authorization header. Tokens are mapped
 * to user IDs in an in-memory session store.
 */
public class AuthMiddleware {

    /** Key for storing the authenticated user in Javalin's request attributes. */
    public static final String AUTHENTICATED_USER_KEY = "authenticatedUser";

    private final UserStorage userStorage;
    private final ConcurrentMap<String, UUID> tokenStore = new ConcurrentHashMap<>();

    /** Paths that do not require authentication. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health",
            "/api/auth/login");

    public AuthMiddleware(UserStorage userStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage required");
    }

    /**
     * Javalin beforeMatched handler. Validates token and stores the authenticated
     * user in request attributes.
     */
    public void handle(Context ctx) {
        String path = ctx.path();
        if (PUBLIC_PATHS.contains(path)) {
            return; // Skip auth for public endpoints
        }

        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        UUID userId = tokenStore.get(token);
        if (userId == null) {
            throw new UnauthorizedResponse("Invalid or expired token");
        }

        User user = userStorage.get(userId);
        if (user == null || user.getState() == UserState.BANNED) {
            tokenStore.remove(token);
            throw new UnauthorizedResponse("User not found or banned");
        }

        ctx.attribute(AUTHENTICATED_USER_KEY, user);
    }

    /**
     * Creates a new session token for the given user.
     *
     * @param userId the user ID to create a token for
     * @return the generated token string
     */
    public String createToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, userId);
        return token;
    }

    /**
     * Removes a session token (logout).
     *
     * @param token the token to invalidate
     */
    public void invalidateToken(String token) {
        tokenStore.remove(token);
    }

    /**
     * Retrieves the authenticated user from request attributes.
     *
     * @param ctx the Javalin context
     * @return the authenticated User
     * @throws UnauthorizedResponse if no authenticated user is present
     */
    public static User getAuthenticatedUser(Context ctx) {
        User user = ctx.attribute(AUTHENTICATED_USER_KEY);
        if (user == null) {
            throw new UnauthorizedResponse("Not authenticated");
        }
        return user;
    }
}
```

**Step 2:** Add a login endpoint in `RestApiServer.registerRoutes()` (after line 102):

```java
// ADD inside registerRoutes(), after health check (line 102)

// Authentication
AuthMiddleware authMiddleware = new AuthMiddleware(services.getUserStorage());
app.beforeMatched(authMiddleware::handle);

app.post("/api/auth/login", ctx -> {
    record LoginRequest(UUID userId) {}
    LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
    if (request.userId() == null) {
        throw new IllegalArgumentException("userId is required");
    }
    User user = services.getUserStorage().get(request.userId());
    if (user == null) {
        throw new io.javalin.http.NotFoundResponse("User not found");
    }
    String token = authMiddleware.createToken(request.userId());
    ctx.json(new LoginResponse(token, request.userId()));
});
```

Add the record after `HealthResponse` (line 139):
```java
/** Login response. */
public record LoginResponse(String token, UUID userId) {}
```

**Step 3:** Wire `UnauthorizedResponse` handler in `registerExceptionHandlers()` (add before the generic Exception handler):

```java
// ADD before line 131 (before the generic Exception handler)
app.exception(io.javalin.http.UnauthorizedResponse.class, (e, ctx) -> {
    ctx.status(401);
    ctx.json(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
});
```

### Test

Create `src/test/java/datingapp/app/api/AuthMiddlewareTest.java`:

```java
package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("AuthMiddleware")
@Timeout(5)
class AuthMiddlewareTest {

    // NOTE: Full integration tests require Javalin test server.
    // Unit tests below verify token store logic.

    @Nested
    @DisplayName("Token Management")
    class TokenManagement {

        private AuthMiddleware middleware;
        private TestUserStorage userStorage;

        @BeforeEach
        void setUp() {
            userStorage = new TestUserStorage(); // Use TestStorages.Users if available
            middleware = new AuthMiddleware(userStorage);
        }

        @Test
        @DisplayName("Creates unique tokens")
        void createsUniqueTokens() {
            UUID userId = UUID.randomUUID();
            String token1 = middleware.createToken(userId);
            String token2 = middleware.createToken(userId);

            assertNotNull(token1);
            assertNotNull(token2);
            assertNotEquals(token1, token2, "Each token should be unique");
        }

        @Test
        @DisplayName("Invalidates token on logout")
        void invalidatesToken() {
            UUID userId = UUID.randomUUID();
            String token = middleware.createToken(userId);
            middleware.invalidateToken(token);
            // Token should no longer be in store
            // (Full verification requires Javalin Context mock)
        }
    }

    // Use TestStorages.Users or a minimal stub
    private static class TestUserStorage implements datingapp.core.storage.UserStorage {
        // ... minimal implementation for test (same as TrustSafetyServiceTest.InMemoryUserStorage)
        // Omitted for brevity — copy from TestStorages or TrustSafetyServiceTest
        @Override public void save(datingapp.core.User u) {}
        @Override public datingapp.core.User get(UUID id) { return null; }
        @Override public java.util.List<datingapp.core.User> findAll() { return java.util.List.of(); }
        @Override public java.util.List<datingapp.core.User> findActive() { return java.util.List.of(); }
        @Override public void delete(UUID id) {}
        @Override public void saveProfileNote(datingapp.core.User.ProfileNote n) {}
        @Override public java.util.Optional<datingapp.core.User.ProfileNote> getProfileNote(UUID a, UUID s) { return java.util.Optional.empty(); }
        @Override public java.util.List<datingapp.core.User.ProfileNote> getProfileNotesByAuthor(UUID a) { return java.util.List.of(); }
        @Override public boolean deleteProfileNote(UUID a, UUID s) { return false; }
    }
}
```

```bash
mvn test -pl . -Dtest=AuthMiddlewareTest,RestApiRoutesTest
```

---

## Fix 5: API-02 — `MessagingRoutes.getMessages()` Bypasses Authorization

**Severity:** Critical
**File:** `src/main/java/datingapp/app/api/MessagingRoutes.java`
**Lines:** 49-58 (getMessages handler)
**Risk:** Anyone who knows/guesses a conversation ID can read all messages — no membership check

### Problem

```java
// CURRENT — line 49-58
public void getMessages(Context ctx) {
    String conversationId = ctx.pathParam("conversationId");
    int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
    int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

    // PROBLEM: Calls messagingStorage.getMessages() directly
    // MessagingService.getMessages() performs membership checks, but it's not used here
    List<MessageDto> messages = messagingStorage.getMessages(conversationId, limit, offset).stream()
            .map(MessageDto::from)
            .toList();
    ctx.json(messages);
}
```

The `MessagingService.getMessages()` method checks that the requesting user is a participant in the conversation. The route handler skips that entirely.

### Fix

**Step 1:** Use the authenticated user (from Fix 4) to validate conversation membership. Update `getMessages()`:

```java
// AFTER — with auth + authorization
public void getMessages(Context ctx) {
    User authenticatedUser = AuthMiddleware.getAuthenticatedUser(ctx);
    String conversationId = ctx.pathParam("conversationId");

    // Verify the authenticated user is a participant in this conversation
    if (!isConversationParticipant(conversationId, authenticatedUser.getId())) {
        ctx.status(403);
        ctx.json(new RestApiServer.ErrorResponse("FORBIDDEN", "You are not a participant in this conversation"));
        return;
    }

    int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
    int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

    List<MessageDto> messages = messagingStorage.getMessages(conversationId, limit, offset).stream()
            .map(MessageDto::from)
            .toList();
    ctx.json(messages);
}
```

**Step 2:** Add the helper method to `MessagingRoutes`:

```java
/**
 * Checks if a user is a participant in a conversation.
 * Conversation IDs follow the deterministic format "uuid1_uuid2".
 */
private boolean isConversationParticipant(String conversationId, UUID userId) {
    String[] parts = conversationId.split("_");
    if (parts.length != 2) {
        return false;
    }
    try {
        UUID id1 = UUID.fromString(parts[0]);
        UUID id2 = UUID.fromString(parts[1]);
        return id1.equals(userId) || id2.equals(userId);
    } catch (IllegalArgumentException ex) {
        return false;
    }
}
```

**Step 3:** Similarly protect `sendMessage()` (line 61-79):

```java
// ADD at the top of sendMessage(), after parsing conversationId
User authenticatedUser = AuthMiddleware.getAuthenticatedUser(ctx);
if (!isConversationParticipant(conversationId, authenticatedUser.getId())) {
    ctx.status(403);
    ctx.json(new RestApiServer.ErrorResponse("FORBIDDEN", "You are not a participant in this conversation"));
    return;
}

// Also verify the senderId matches the authenticated user
if (!request.senderId().equals(authenticatedUser.getId())) {
    ctx.status(403);
    ctx.json(new RestApiServer.ErrorResponse("FORBIDDEN", "Cannot send messages as another user"));
    return;
}
```

### Test

Add integration test cases verifying:
1. Authenticated user CAN read messages from their own conversation
2. Authenticated user CANNOT read messages from someone else's conversation (expect 403)
3. Authenticated user CANNOT send messages as another user (expect 403)

```bash
mvn test -pl . -Dtest=RestApiRoutesTest
```

---

## Fix 6: API-03 — No Rate Limiting on Any Endpoint

**Severity:** High
**File:** New file `src/main/java/datingapp/app/api/RateLimitMiddleware.java`, update `RestApiServer.java`
**Risk:** Attackers can scrape all profiles, like every user, or flood messages in seconds

### Fix

**Approach:** Add a per-IP rate limiter using a sliding window counter stored in a `ConcurrentHashMap`. Javalin 6.x supports `beforeMatched` handlers for this.

**Step 1:** Create `src/main/java/datingapp/app/api/RateLimitMiddleware.java`:

```java
package datingapp.app.api;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple sliding-window rate limiter for REST API endpoints.
 *
 * <p>Tracks requests per IP address within a configurable time window.
 * Returns HTTP 429 (Too Many Requests) when the limit is exceeded.
 */
public class RateLimitMiddleware {

    private final int maxRequests;
    private final long windowMs;
    private final Map<String, Queue<Long>> requestLog = new ConcurrentHashMap<>();

    /** Maximum tracked IPs before cleanup. */
    private static final int MAX_TRACKED_IPS = 50_000;

    /**
     * Creates a rate limiter.
     *
     * @param maxRequests maximum requests allowed within the window
     * @param windowMs   window size in milliseconds
     */
    public RateLimitMiddleware(int maxRequests, long windowMs) {
        if (maxRequests <= 0 || windowMs <= 0) {
            throw new IllegalArgumentException("maxRequests and windowMs must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /** Javalin beforeMatched handler. */
    public void handle(Context ctx) {
        String clientIp = ctx.ip();
        long now = Instant.now().toEpochMilli();

        if (requestLog.size() > MAX_TRACKED_IPS) {
            requestLog.clear(); // Prevent unbounded memory growth
        }

        Queue<Long> timestamps = requestLog.computeIfAbsent(clientIp, k -> new ConcurrentLinkedQueue<>());

        // Remove expired entries
        long cutoff = now - windowMs;
        while (!timestamps.isEmpty() && timestamps.peek() < cutoff) {
            timestamps.poll();
        }

        if (timestamps.size() >= maxRequests) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS);
            ctx.json(new RestApiServer.ErrorResponse("RATE_LIMITED",
                    "Too many requests. Please try again later."));
            ctx.skipRemainingHandlers();
            return;
        }

        timestamps.add(now);
    }
}
```

**Step 2:** Register in `RestApiServer.registerRoutes()` — add before route registration (before line 97):

```java
// Rate limiting: 100 requests per minute per IP
RateLimitMiddleware rateLimiter = new RateLimitMiddleware(100, 60_000);
app.beforeMatched(rateLimiter::handle);
```

> **Note:** Rate limiter MUST be registered BEFORE auth middleware so rate-limited requests don't burn auth processing.

**Step 3:** Make the limits configurable. Add to `AppConfig`:

```java
// ADD to AppConfig record parameters (or builder)
int apiRateLimitPerMinute   // default: 100
```

If `AppConfig` modification is too invasive, hardcode `100` per minute initially and add a TODO for configuration.

### Test

```java
@Test
@DisplayName("Rate limiter blocks after max requests")
void rateLimiterBlocks() {
    RateLimitMiddleware limiter = new RateLimitMiddleware(5, 60_000);
    // Would need a mock Context — or test via integration test
    // Verify: 5 requests succeed, 6th returns 429
}
```

```bash
mvn test -pl . -Dtest=RateLimitMiddlewareTest
```

---

## Fix 7: API-04 — `MatchRoutes.likeUser()` Bypasses Daily Limits

**Severity:** High
**File:** `src/main/java/datingapp/app/api/MatchRoutes.java`
**Lines:** 48-65 (likeUser handler)
**Risk:** API callers can like unlimited users per day — only the CLI/UI path checks `dailyService.canLike()`

### Problem

```java
// CURRENT — line 48-65
public void likeUser(Context ctx) {
    UUID userId = parseUuid(ctx.pathParam("id"));
    UUID targetId = parseUuid(ctx.pathParam("targetId"));
    validateUserExists(userId);
    validateUserExists(targetId);

    // PROBLEM: No dailyService.canLike() check
    Like like = Like.create(userId, targetId, Like.Direction.LIKE);
    Optional<Match> match = matchingService.recordLike(like);
    // ...
}
```

### Fix

**Step 1:** Add `DailyService` dependency to `MatchRoutes`:

```java
// BEFORE — fields (lines 25-27)
private final UserStorage userStorage;
private final MatchStorage matchStorage;
private final MatchingService matchingService;

// AFTER
private final UserStorage userStorage;
private final MatchStorage matchStorage;
private final MatchingService matchingService;
private final DailyService dailyService;
```

**Step 2:** Update constructor (lines 29-34):

```java
// BEFORE
public MatchRoutes(ServiceRegistry services) {
    Objects.requireNonNull(services, "services cannot be null");
    this.userStorage = services.getUserStorage();
    this.matchStorage = services.getMatchStorage();
    this.matchingService = services.getMatchingService();
}

// AFTER
public MatchRoutes(ServiceRegistry services) {
    Objects.requireNonNull(services, "services cannot be null");
    this.userStorage = services.getUserStorage();
    this.matchStorage = services.getMatchStorage();
    this.matchingService = services.getMatchingService();
    this.dailyService = services.getDailyService();
}
```

**Step 3:** Add daily limit check in `likeUser()` (after `validateUserExists` calls):

```java
// ADD after line 53 (after validateUserExists(targetId))

// Check daily like limit
if (!dailyService.canLike(userId)) {
    ctx.status(429);
    ctx.json(new RestApiServer.ErrorResponse("DAILY_LIMIT_REACHED",
            "Daily like limit reached. Try again tomorrow."));
    return;
}
```

**Step 4:** Similarly check pass limit in `passUser()` (after line 73):

```java
// ADD after validateUserExists(targetId) in passUser()
if (!dailyService.canPass(userId)) {
    ctx.status(429);
    ctx.json(new RestApiServer.ErrorResponse("DAILY_LIMIT_REACHED",
            "Daily pass limit reached. Try again tomorrow."));
    return;
}
```

**Step 5:** Add import:
```java
import datingapp.core.DailyService;
```

### Test

Verify in `RestApiRoutesTest` or a new test class that:
1. Like succeeds when within daily limit
2. Like returns 429 when daily limit exceeded

```bash
mvn test -pl . -Dtest=RestApiRoutesTest
```

---

## Fix 8: API-05 — `MatchRoutes.from()` Sets `otherUserName` to "Unknown" Always

**Severity:** Medium
**File:** `src/main/java/datingapp/app/api/MatchRoutes.java`
**Lines:** 106-110 (static `MatchSummary.from()` method)
**Risk:** Every match created via `likeUser()` shows "Unknown" for the other user's name in the response

### Problem

```java
// CURRENT — lines 104-111
public record MatchSummary(
        String matchId, UUID otherUserId, String otherUserName, String state, Instant createdAt) {
    // Static factory — hardcodes "Unknown"
    public static MatchSummary from(Match match, UUID currentUserId) {
        UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
        return new MatchSummary(
                match.getId(), otherUserId, "Unknown", match.getState().name(), match.getCreatedAt());
    }
}
```

The instance method `toSummary()` (lines 87-93) correctly does a `userStorage.get(otherUserId)` lookup, but the static `from()` used in `likeUser()` cannot access `userStorage`.

### Fix

Remove the static `from()` and use the instance method `toSummary()` instead.

**Step 1:** Delete the static `from()` method entirely (lines 106-110):
```java
// DELETE this static method
public static MatchSummary from(Match match, UUID currentUserId) {
    UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
    return new MatchSummary(
            match.getId(), otherUserId, "Unknown", match.getState().name(), match.getCreatedAt());
}
```

**Step 2:** Update `likeUser()` to use the instance method (line 60):

```java
// BEFORE
ctx.json(new LikeResponse(true, "It's a match!", MatchSummary.from(match.get(), userId)));

// AFTER
ctx.json(new LikeResponse(true, "It's a match!", toSummary(match.get(), userId)));
```

### Test

```bash
mvn test -pl . -Dtest=RestApiRoutesTest
```

Verify: when a match is created via `likeUser()`, the response includes the actual other user's name (not "Unknown").

---

## Fix 9: API-06 — No Input Sanitization

**Severity:** Medium
**File:** `src/main/java/datingapp/app/api/MessagingRoutes.java` (primary), all routes
**Risk:** User-supplied content stored as-is; XSS risk if ever rendered in a web frontend

### Problem

`sendMessage()` stores `request.content()` directly without sanitization. If this content is ever displayed in a web UI without escaping, it enables XSS attacks.

### Fix

**Approach:** Add a lightweight `InputSanitizer` utility and apply it at the API boundary. This is defense-in-depth — the UI should also escape output, but sanitizing input prevents stored XSS.

**Step 1:** Create `src/main/java/datingapp/app/api/InputSanitizer.java`:

```java
package datingapp.app.api;

/**
 * Sanitizes user input for storage. Strips HTML tags and limits length.
 *
 * <p>This is a defense-in-depth measure. Output escaping should also be applied
 * when rendering user content.
 */
public final class InputSanitizer {

    private InputSanitizer() {}

    /** Maximum message content length. */
    public static final int MAX_MESSAGE_LENGTH = 1000;

    /** Maximum bio length. */
    public static final int MAX_BIO_LENGTH = 500;

    /**
     * Strips HTML tags from input.
     *
     * @param input raw user input
     * @return sanitized string with HTML tags removed
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        // Remove HTML tags
        return input.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Sanitizes and truncates a message.
     *
     * @param content raw message content
     * @return sanitized, length-limited content
     */
    public static String sanitizeMessage(String content) {
        if (content == null) {
            return null;
        }
        String clean = stripHtml(content);
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            clean = clean.substring(0, MAX_MESSAGE_LENGTH);
        }
        return clean;
    }

    /**
     * Sanitizes and truncates a bio.
     *
     * @param bio raw bio text
     * @return sanitized, length-limited bio
     */
    public static String sanitizeBio(String bio) {
        if (bio == null) {
            return null;
        }
        String clean = stripHtml(bio);
        if (clean.length() > MAX_BIO_LENGTH) {
            clean = clean.substring(0, MAX_BIO_LENGTH);
        }
        return clean;
    }
}
```

**Step 2:** Apply in `MessagingRoutes.sendMessage()` (line 71):

```java
// BEFORE
var result = messagingService.sendMessage(request.senderId(), recipientId, request.content());

// AFTER
String sanitizedContent = InputSanitizer.sanitizeMessage(request.content());
if (sanitizedContent == null || sanitizedContent.isBlank()) {
    throw new IllegalArgumentException("Message content cannot be empty");
}
var result = messagingService.sendMessage(request.senderId(), recipientId, sanitizedContent);
```

### Test

Create `src/test/java/datingapp/app/api/InputSanitizerTest.java`:

```java
@Test
@DisplayName("Strips HTML tags")
void stripsHtmlTags() {
    assertEquals("Hello world", InputSanitizer.stripHtml("<script>alert('xss')</script>Hello world"));
    assertEquals("Bold text", InputSanitizer.stripHtml("<b>Bold text</b>"));
}

@Test
@DisplayName("Truncates to max message length")
void truncatesLongMessages() {
    String longMessage = "a".repeat(2000);
    String result = InputSanitizer.sanitizeMessage(longMessage);
    assertEquals(1000, result.length());
}

@Test
@DisplayName("Handles null input gracefully")
void handlesNull() {
    assertNull(InputSanitizer.stripHtml(null));
    assertNull(InputSanitizer.sanitizeMessage(null));
}
```

```bash
mvn test -pl . -Dtest=InputSanitizerTest
```

---

## Fix 10: API-07 — No HTTPS Enforcement

**Severity:** Medium
**File:** `src/main/java/datingapp/app/api/RestApiServer.java`
**Risk:** Credentials and tokens sent over plaintext HTTP if no TLS proxy is in front

### Fix

**Approach:** This app uses Javalin (embedded Jetty). For production, HTTPS should be handled by a reverse proxy (nginx, Caddy). For defense-in-depth, add an HSTS header and optionally a before-handler that checks for HTTPS.

**Step 1:** Add HSTS header in `RestApiServer.start()` config block (line 57-60):

```java
// BEFORE
app = Javalin.create(config -> {
    config.jsonMapper(createJsonMapper(mapper));
    config.http.defaultContentType = "application/json";
});

// AFTER
app = Javalin.create(config -> {
    config.jsonMapper(createJsonMapper(mapper));
    config.http.defaultContentType = "application/json";
});

// Add security headers to all responses
app.after(ctx -> {
    ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    ctx.header("X-Content-Type-Options", "nosniff");
    ctx.header("X-Frame-Options", "DENY");
});
```

> **Note:** Full TLS termination is a deployment concern, not an application code concern. Document the requirement for a TLS proxy in production in a README or deployment guide. Adding these headers ensures browsers enforce HTTPS once they've connected via TLS once.

### Test

No unit test needed — this is a configuration/header addition. Verify manually:

```bash
curl -i http://localhost:7070/api/health
# Should see Strict-Transport-Security, X-Content-Type-Options, X-Frame-Options headers
```

---

## Fix 11: API-08 — Weak Session Management (Sessions Never Expire)

**Severity:** Medium
**File:** `src/main/java/datingapp/app/api/AuthMiddleware.java` (from Fix 4)
**Risk:** Session tokens live forever — a leaked token grants permanent access

### Problem

The `AuthMiddleware` from Fix 4 stores tokens in a `ConcurrentHashMap` with no TTL. A stolen token works indefinitely.

### Fix

**Step 1:** Add expiration tracking to the token store. Replace the simple `ConcurrentMap<String, UUID>` with a map of `TokenEntry` records:

```java
// ADD inside AuthMiddleware class

/** Token with expiration. */
private record TokenEntry(UUID userId, Instant expiresAt) {
    boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

/** Default token TTL: 24 hours. */
private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(24);

private final Duration tokenTtl;
```

**Step 2:** Replace the token store type:
```java
// BEFORE
private final ConcurrentMap<String, UUID> tokenStore = new ConcurrentHashMap<>();

// AFTER
private final ConcurrentMap<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();
```

**Step 3:** Add constructor overload:
```java
public AuthMiddleware(UserStorage userStorage) {
    this(userStorage, DEFAULT_TOKEN_TTL);
}

public AuthMiddleware(UserStorage userStorage, Duration tokenTtl) {
    this.userStorage = Objects.requireNonNull(userStorage, "userStorage required");
    this.tokenTtl = Objects.requireNonNull(tokenTtl, "tokenTtl required");
}
```

**Step 4:** Update `handle()` to check expiration:
```java
// BEFORE
UUID userId = tokenStore.get(token);
if (userId == null) {
    throw new UnauthorizedResponse("Invalid or expired token");
}

// AFTER
TokenEntry entry = tokenStore.get(token);
if (entry == null || entry.isExpired()) {
    if (entry != null) {
        tokenStore.remove(token); // Cleanup expired token
    }
    throw new UnauthorizedResponse("Invalid or expired token");
}
UUID userId = entry.userId();
```

**Step 5:** Update `createToken()`:
```java
// BEFORE
public String createToken(UUID userId) {
    String token = UUID.randomUUID().toString();
    tokenStore.put(token, userId);
    return token;
}

// AFTER
public String createToken(UUID userId) {
    String token = UUID.randomUUID().toString();
    tokenStore.put(token, new TokenEntry(userId, Instant.now().plus(tokenTtl)));
    return token;
}
```

**Step 6:** Add imports:
```java
import java.time.Duration;
import java.time.Instant;
```

### Test

```java
@Test
@DisplayName("Expired token is rejected")
void expiredTokenIsRejected() {
    // Create middleware with 0-duration TTL
    AuthMiddleware middleware = new AuthMiddleware(userStorage, Duration.ZERO);
    String token = middleware.createToken(userId);
    // Token should be immediately expired
    // (Full verification requires Context mock)
}
```

```bash
mvn test -pl . -Dtest=AuthMiddlewareTest
```

---

## Fix 12: API-09 — No CSRF Protection

**Severity:** Medium
**File:** `src/main/java/datingapp/app/api/RestApiServer.java`
**Risk:** State-changing operations (POST/PUT/DELETE) could be triggered by a malicious page via the user's browser

### Fix

**Approach:** For a REST API using Bearer token auth (Fix 4), CSRF is largely mitigated because:
1. Bearer tokens are not automatically included by browsers (unlike cookies)
2. The `Authorization: Bearer <token>` header must be explicitly set by JavaScript

However, as defense-in-depth, add the following header checks:

**Step 1:** Add CORS configuration in `RestApiServer.start()`:

```java
// ADD inside Javalin.create config block
config.bundledPlugins.enableCors(cors -> {
    cors.addRule(it -> {
        it.allowHost("http://localhost:3000", "http://localhost:7070");
        it.allowCredentials = false;
    });
});
```

> **Note:** If the API is consumed only by the JavaFX desktop app (not a browser), CORS is not needed. However, since the API exists and could be consumed by a web frontend in the future, configure CORS proactively. Adjust allowed origins for production.

**Step 2:** For state-changing endpoints, verify the `Content-Type` header:

```java
// ADD as a beforeMatched handler in registerRoutes()
app.beforeMatched(ctx -> {
    if (ctx.method().name().equals("POST") || ctx.method().name().equals("PUT")
            || ctx.method().name().equals("DELETE")) {
        String contentType = ctx.contentType();
        if (contentType == null || !contentType.contains("application/json")) {
            ctx.status(415);
            ctx.json(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE",
                    "Content-Type must be application/json"));
            ctx.skipRemainingHandlers();
        }
    }
});
```

This blocks form-based CSRF attacks since browsers auto-submit forms as `application/x-www-form-urlencoded`, not `application/json`.

### Test

Manual verification or integration test:
```bash
# Should be rejected (no Content-Type)
curl -X POST http://localhost:7070/api/users/xxx/like/yyy
# Should be rejected (wrong Content-Type)
curl -X POST -H "Content-Type: application/x-www-form-urlencoded" http://localhost:7070/api/users/xxx/like/yyy
```

---

## Fix 13: API-10 — Insecure Direct Object Reference (IDOR)

**Severity:** High
**File:** `src/main/java/datingapp/app/api/UserRoutes.java`, `MatchRoutes.java`, `MessagingRoutes.java`
**Risk:** Users can access/modify other users' data by guessing/changing IDs in URLs

### Problem

All route handlers take the user ID from the URL path parameter. After Fix 4 (authentication), we have an authenticated user — but the routes don't verify that the authenticated user matches the user ID in the path.

### Fix

**Step 1:** Add an authorization check helper in each routes class (or as a shared utility):

```java
/**
 * Verifies the authenticated user matches the path parameter user ID.
 * Throws ForbiddenResponse if not.
 */
private void authorizeUser(Context ctx, UUID pathUserId) {
    User authenticated = AuthMiddleware.getAuthenticatedUser(ctx);
    if (!authenticated.getId().equals(pathUserId)) {
        throw new io.javalin.http.ForbiddenResponse(
                "You can only access your own data");
    }
}
```

**Step 2:** Add to every endpoint that takes `{id}` as a user ID:

In `UserRoutes.java`:
```java
// getUser() — line 37
public void getUser(Context ctx) {
    UUID id = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, id);  // ADD THIS
    // ... rest unchanged
}

// getCandidates() — line 47
public void getCandidates(Context ctx) {
    UUID id = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, id);  // ADD THIS
    // ... rest unchanged
}

// listUsers() — REMOVE or restrict to admin only
// This endpoint exposes ALL users and should not be public
```

In `MatchRoutes.java`:
```java
// getMatches() — line 37
public void getMatches(Context ctx) {
    UUID userId = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, userId);  // ADD THIS
    // ... rest unchanged
}

// likeUser() — line 48
public void likeUser(Context ctx) {
    UUID userId = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, userId);  // ADD THIS
    // ... rest unchanged
}

// passUser() — line 68
public void passUser(Context ctx) {
    UUID userId = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, userId);  // ADD THIS
    // ... rest unchanged
}
```

In `MessagingRoutes.java`:
```java
// getConversations() — line 38
public void getConversations(Context ctx) {
    UUID userId = parseUuid(ctx.pathParam("id"));
    authorizeUser(ctx, userId);  // ADD THIS
    // ... rest unchanged
}
```

**Step 3:** Add `ForbiddenResponse` handler in `RestApiServer.registerExceptionHandlers()`:

```java
app.exception(io.javalin.http.ForbiddenResponse.class, (e, ctx) -> {
    ctx.status(403);
    ctx.json(new ErrorResponse("FORBIDDEN", e.getMessage()));
});
```

**Step 4:** Handle `listUsers()` — this endpoint exposes all user data:

```java
// OPTION A: Remove the endpoint entirely
// OPTION B: Restrict to authenticated user's own profile (effectively same as getUser)
// OPTION C: Return only minimal data (name, age) with no IDs

// Recommended: Remove from public API. If needed for admin, gate behind admin auth.
```

### Test

```bash
mvn test -pl . -Dtest=RestApiRoutesTest
```

Verify:
1. Authenticated user accessing their own data → 200
2. Authenticated user accessing another user's data → 403

---

## Fix 14: API-05 Bonus — `MatchSummary` Record Cleanup

**Severity:** Low
**File:** `src/main/java/datingapp/app/api/MatchRoutes.java`
**Note:** This is covered by Fix 8 above. Included here as a reminder to verify the static `from()` removal is complete.

After Fix 8, verify:
- No remaining references to `MatchSummary.from()` anywhere in the codebase
- All match creation responses use the instance `toSummary()` method

```bash
# Search for any remaining references
grep -rn "MatchSummary.from" src/
# Should return 0 results after fix
```

---

## Post-Implementation Checklist

- [ ] Run `mvn spotless:apply && mvn verify` — all quality checks pass
- [ ] Run `mvn test` — all tests pass (existing + new)
- [ ] Grep for `new Random()` in security-sensitive code — should be `SecureRandom` everywhere except `DailyService` (intentionally deterministic)
- [ ] Grep for `logger.info.*userId` — should be zero (all downgraded to debug)
- [ ] Manual test: start API server, verify:
  - `GET /api/health` → 200 (no auth required)
  - `GET /api/users/{id}` without token → 401
  - `POST /api/auth/login` → returns token
  - `GET /api/users/{id}` with valid token → 200
  - `GET /api/users/{id}` with expired token → 401
  - 101st request within 1 minute → 429
  - Accessing another user's data → 403
  - Message with HTML tags → tags stripped
- [ ] Verify security headers present: `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`

---

## Deferred Items

These were identified in the audit but are deferred due to effort/scope:

### DEFER: Full JWT with Refresh Token Rotation (API-08 upgrade)
The current token-based auth (Fix 4 + Fix 11) is adequate for the app's current stage. Full JWT with refresh tokens, token rotation, and revocation lists should be implemented when:
- The app scales beyond single-server deployment
- Multiple API consumers exist (mobile app, web frontend)
- Estimated effort: 1-2 days

### DEFER: Full RBAC (Role-Based Access Control)
The current auth model is user-level. Admin roles for the `listUsers()` endpoint and moderation features should be added when an admin interface is planned.

---

## New Files Created

| File | Purpose |
|------|---------|
| `src/main/java/datingapp/app/api/AuthMiddleware.java` | Token-based authentication + session management |
| `src/main/java/datingapp/app/api/RateLimitMiddleware.java` | Per-IP sliding window rate limiter |
| `src/main/java/datingapp/app/api/InputSanitizer.java` | HTML stripping + length validation |
| `src/test/java/datingapp/app/api/AuthMiddlewareTest.java` | Auth middleware tests |
| `src/test/java/datingapp/app/api/InputSanitizerTest.java` | Input sanitization tests |
| `src/test/java/datingapp/app/api/RateLimitMiddlewareTest.java` | Rate limiter tests |

## Files Modified

| File | Changes |
|------|---------|
| `core/TrustSafetyService.java` | `Random` → `SecureRandom`; log levels INFO → DEBUG |
| `core/MatchingService.java` | Split warn log to avoid PII in error message |
| `storage/DatabaseManager.java` | Rename constant; add warning log for dev password |
| `app/api/RestApiServer.java` | Auth middleware, rate limiter, security headers, CORS, login endpoint, exception handlers |
| `app/api/MatchRoutes.java` | Add DailyService dependency; daily limit checks; remove static `from()`; IDOR auth checks |
| `app/api/MessagingRoutes.java` | Authorization checks on getMessages/sendMessage; input sanitization; IDOR auth checks |
| `app/api/UserRoutes.java` | IDOR auth checks; restrict/remove listUsers endpoint |
| `core/TrustSafetyServiceTest.java` | `Random` → `SecureRandom` in test constructors |

---

*Generated from AUDIT_PROBLEMS_SECURITY.md — February 6, 2026*
*14 findings: 3 quick wins + 11 API hardening fixes*

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 18:30:00|agent:claude_code|plan-security|Create PLAN_FIX_SECURITY.md with 14 security/privacy fixes|Audit and Suggestions/PLAN_FIX_SECURITY.md
---AGENT-LOG-END---
