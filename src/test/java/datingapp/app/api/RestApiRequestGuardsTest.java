package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.api.RestApiExceptions.ApiForbiddenException;
import datingapp.app.api.RestApiExceptions.ApiTooManyRequestsException;
import io.javalin.http.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RestApiRequestGuards")
class RestApiRequestGuardsTest {

    private static final String HEALTH_PATH = "/api/health";
    private static final String LAN_SHARED_SECRET_HEADER = "X-DatingApp-Shared-Secret";
    private static final String USERS_PATH = "/api/users/abc";
    private static final String LOOPBACK_IP = "127.0.0.1";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    @Test
    @DisplayName("enforceLocalhostOnly allows loopback and rejects non-loopback clients")
    void enforceLocalhostOnlyAllowsLoopbackAndRejectsNonLoopbackClients() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 5);

        assertDoesNotThrow(
                () -> guards.enforceLocalhostOnly(context(HEALTH_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertDoesNotThrow(() -> guards.enforceLocalhostOnly(context(HEALTH_PATH, "GET", "::1", Map.of(), Map.of())));
        ApiForbiddenException exception = assertThrows(
                ApiForbiddenException.class,
                () -> guards.enforceLocalhostOnly(context(HEALTH_PATH, "GET", "203.0.113.10", Map.of(), Map.of())));
        assertEquals("REST API is restricted to localhost requests", exception.getMessage());
    }

    @Test
    @DisplayName("requiresActingUserIdentity marks mutating routes and every user read route")
    void requiresActingUserIdentityMarksMutatingAndUserReadRoutes() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 5);

        assertTrue(guards.requiresActingUserIdentity(
                context("/api/users/1/block/2", "POST", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(!guards.requiresActingUserIdentity(context(HEALTH_PATH, "POST", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(
                context("/api/location/resolve", "POST", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(context("/api/users/1", "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(context("/api/users", "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(context("/api/users/", "GET", LOOPBACK_IP, Map.of(), Map.of())));
    }

    @Test
    @DisplayName("every /api/location route requires an acting user, while health and auth stay open")
    void locationRoutesRequireActingUserWhileHealthAndAuthStayOpen() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 5);

        assertTrue(guards.requiresActingUserIdentity(
                context("/api/location/countries", "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(
                context("/api/location/cities", "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(guards.requiresActingUserIdentity(
                context("/api/location/resolve", "POST", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(!guards.requiresActingUserIdentity(context(HEALTH_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(!guards.requiresActingUserIdentity(
                context("/api/auth/session", "POST", LOOPBACK_IP, Map.of(), Map.of())));
        assertTrue(!guards.requiresActingUserIdentity(
                context("/api/location/countries", "OPTIONS", LOOPBACK_IP, Map.of(), Map.of())));
    }

    @Test
    @DisplayName("enforceRateLimit exposes retry and usage status")
    void enforceRateLimitExposesRetryAndUsageStatus() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 2);
        Context ctx = context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of());

        guards.enforceRateLimit(ctx);
        guards.enforceRateLimit(ctx);

        ApiTooManyRequestsException exception =
                assertThrows(ApiTooManyRequestsException.class, () -> guards.enforceRateLimit(ctx));
        assertEquals(2, exception.status().limit());
        assertEquals(3, exception.status().used());
        assertTrue(exception.status().retryAfterSeconds() >= 1);
    }

    @Test
    @DisplayName("LAN mode requires the configured shared secret for non-health requests")
    void lanModeRequiresTheConfiguredSharedSecretForNonHealthRequests() {
        RestApiRequestGuards guards = new RestApiRequestGuards(
                new RestApiIdentityPolicy(), Duration.ofMinutes(1), 5, "dev-secret", System::nanoTime);

        assertDoesNotThrow(
                () -> guards.enforceLanSharedSecret(context(HEALTH_PATH, "GET", "203.0.113.10", Map.of(), Map.of())));
        assertDoesNotThrow(() -> guards.enforceLanSharedSecret(context(
                USERS_PATH,
                "OPTIONS",
                "203.0.113.10",
                Map.of("Origin", "http://localhost:3000", "Access-Control-Request-Method", "GET"),
                Map.of())));

        ApiForbiddenException exception = assertThrows(
                ApiForbiddenException.class,
                () -> guards.enforceLanSharedSecret(context(USERS_PATH, "GET", "203.0.113.10", Map.of(), Map.of())));
        assertEquals("Missing or invalid LAN shared secret", exception.getMessage());

        assertDoesNotThrow(() -> guards.enforceLanSharedSecret(
                context(USERS_PATH, "GET", "203.0.113.10", Map.of(LAN_SHARED_SECRET_HEADER, "dev-secret"), Map.of())));
    }

    @Test
    @DisplayName("rate limiting skips CORS preflight requests")
    void rateLimitingSkipsCorsPreflightRequests() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 1);
        Context preflight = context(
                USERS_PATH,
                "OPTIONS",
                LOOPBACK_IP,
                Map.of("Origin", "http://localhost:3000", "Access-Control-Request-Method", "GET"),
                Map.of());

        assertDoesNotThrow(() -> guards.enforceRateLimit(preflight));
        assertDoesNotThrow(() -> guards.enforceRateLimit(preflight));
        assertDoesNotThrow(() -> guards.enforceRateLimit(preflight));
    }

    @Test
    @DisplayName("stale rate-limit windows are evicted on the scheduled sweep")
    void staleRateLimitWindowsAreEvictedOnTheScheduledSweep() throws Exception {
        AtomicLong ticker = new AtomicLong(0L);
        RestApiRequestGuards guards =
                new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofSeconds(5), 1000, ticker::get);

        for (int i = 0; i < 255; i++) {
            guards.enforceRateLimit(context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of()));
        }

        ticker.set(Duration.ofSeconds(11).toNanos());
        guards.enforceRateLimit(context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of()));

        Field rateLimiterField = RestApiRequestGuards.class.getDeclaredField("rateLimiter");
        rateLimiterField.setAccessible(true);
        Object rateLimiter = rateLimiterField.get(guards);
        Field windowsField = rateLimiter.getClass().getDeclaredField("windows");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, ?> windows =
                (java.util.concurrent.ConcurrentHashMap<String, ?>) windowsField.get(rateLimiter);

        assertEquals(1, windows.size());
    }

    @Test
    @DisplayName("rate limiting uses the injected monotonic ticker instead of AppClock")
    void rateLimitingUsesInjectedMonotonicTickerInsteadOfAppClock() {
        AtomicLong ticker = new AtomicLong(0L);
        RestApiRequestGuards guards =
                new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofSeconds(5), 1, ticker::get);
        Context ctx = context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of());

        guards.enforceRateLimit(ctx);
        ApiTooManyRequestsException blocked =
                assertThrows(ApiTooManyRequestsException.class, () -> guards.enforceRateLimit(ctx));
        assertEquals(2, blocked.status().used());

        ticker.set(Duration.ofSeconds(6).toNanos());
        assertDoesNotThrow(() -> guards.enforceRateLimit(ctx));
    }

    private static RestApiRequestGuards guardsTrusting(String header, int maxRequests) {
        return new RestApiRequestGuards(
                new RestApiIdentityPolicy(),
                Duration.ofMinutes(1),
                maxRequests,
                "dev-secret",
                header,
                System::nanoTime);
    }

    @Test
    @DisplayName("without a configured header every request is charged to the socket peer")
    void withoutConfiguredHeaderEveryRequestIsChargedToTheSocketPeer() {
        RestApiRequestGuards guards = new RestApiRequestGuards(new RestApiIdentityPolicy(), Duration.ofMinutes(1), 1);

        guards.enforceRateLimit(
                context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(FORWARDED_HEADER, "198.51.100.1"), Map.of()));

        assertThrows(
                ApiTooManyRequestsException.class,
                () -> guards.enforceRateLimit(
                        context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(FORWARDED_HEADER, "198.51.100.2"), Map.of())));
    }

    @Test
    @DisplayName("a loopback peer is charged separately per forwarded client address")
    void loopbackPeerIsChargedPerForwardedClientAddress() {
        RestApiRequestGuards guards = guardsTrusting(FORWARDED_HEADER, 1);
        Context first = context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(FORWARDED_HEADER, "198.51.100.1"), Map.of());
        Context second = context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(FORWARDED_HEADER, "198.51.100.2"), Map.of());

        assertDoesNotThrow(() -> guards.enforceRateLimit(first));
        assertDoesNotThrow(() -> guards.enforceRateLimit(second));
        assertThrows(ApiTooManyRequestsException.class, () -> guards.enforceRateLimit(first));
    }

    @Test
    @DisplayName("a non-loopback peer cannot choose its own bucket by sending the header")
    void nonLoopbackPeerCannotChooseItsOwnBucket() {
        RestApiRequestGuards guards = guardsTrusting(FORWARDED_HEADER, 1);
        String lanPeer = "192.168.1.50";

        assertDoesNotThrow(() -> guards.enforceRateLimit(
                context(USERS_PATH, "GET", lanPeer, Map.of(FORWARDED_HEADER, "198.51.100.1"), Map.of())));
        assertThrows(
                ApiTooManyRequestsException.class,
                () -> guards.enforceRateLimit(
                        context(USERS_PATH, "GET", lanPeer, Map.of(FORWARDED_HEADER, "198.51.100.2"), Map.of())));
        assertEquals(
                lanPeer,
                guards.clientIp(
                        context(USERS_PATH, "GET", lanPeer, Map.of(FORWARDED_HEADER, "198.51.100.9"), Map.of())));
    }

    @Test
    @DisplayName("clientIp uses the last list entry, normalizes IPv6, and falls back to the peer when unusable")
    void clientIpUsesLastEntryAndFallsBackToPeer() {
        RestApiRequestGuards guards = guardsTrusting(FORWARDED_HEADER, 5);

        assertEquals("198.51.100.7", guards.clientIp(forwarded("203.0.113.9, 198.51.100.7")));
        assertEquals("0:0:0:0:0:0:0:1", guards.clientIp(forwarded("::1")));
        assertEquals("2001:db8:0:0:0:0:0:1", guards.clientIp(forwarded("2001:db8::1")));
        assertEquals(LOOPBACK_IP, guards.clientIp(context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(), Map.of())));
        assertEquals(LOOPBACK_IP, guards.clientIp(forwarded("")));
        assertEquals(LOOPBACK_IP, guards.clientIp(forwarded("example.com")));
        assertEquals(LOOPBACK_IP, guards.clientIp(forwarded("deadbeef")));
        assertEquals(LOOPBACK_IP, guards.clientIp(forwarded("999.1.1.1.1")));
        assertEquals(LOOPBACK_IP, guards.clientIp(forwarded("198.51.100.7, unknown")));
    }

    @Test
    @DisplayName("a malformed client IP header name is rejected at construction")
    void malformedClientIpHeaderNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> guardsTrusting("X-Bad Header", 5));
        assertThrows(IllegalArgumentException.class, () -> guardsTrusting("X-Bad:Header", 5));
        assertDoesNotThrow(() -> guardsTrusting("  ", 5));
    }

    private static Context forwarded(String value) {
        return context(USERS_PATH, "GET", LOOPBACK_IP, Map.of(FORWARDED_HEADER, value), Map.of());
    }

    private static Context context(
            String path, String method, String ip, Map<String, String> headers, Map<String, String> pathParams) {
        Map<String, String> headerCopy = new HashMap<>(headers);
        Map<String, String> pathParamCopy = new HashMap<>(pathParams);
        return (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[] {Context.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "path" -> path;
                    case "method" -> io.javalin.http.HandlerType.valueOf(method);
                    case "ip" -> ip;
                    case "header" -> headerCopy.get(args[0].toString());
                    case "pathParamMap" -> pathParamCopy;
                    case "pathParam" -> pathParamCopy.get(args[0].toString());
                    default -> defaultValue(invokedMethod.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(byte.class)) {
            return (byte) 0;
        }
        if (returnType.equals(short.class)) {
            return (short) 0;
        }
        if (returnType.equals(char.class)) {
            return '\0';
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(float.class)) {
            return 0.0f;
        }
        if (returnType.equals(double.class)) {
            return 0.0d;
        }
        return null;
    }
}
