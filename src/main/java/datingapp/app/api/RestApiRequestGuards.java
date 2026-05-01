package datingapp.app.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class RestApiRequestGuards {

    static final String HEADER_LAN_SHARED_SECRET = "X-DatingApp-Shared-Secret";
    private static final String HEALTH_ROUTE = "/api/health";
    private static final String AUTH_ROUTE_PREFIX = "/api/auth/";
    private static final String AUTH_ME_ROUTE = "/api/auth/me";
    private static final String CONVERSATION_ROUTE_PREFIX = "/api/conversations/";
    private static final String LOCATION_RESOLVE_ROUTE = "/api/location/resolve";
    private static final String USERS_ROUTE_PREFIX = "/api/users/";
    private static final String LOCALHOST_ONLY_MESSAGE = "REST API is restricted to localhost requests";
    private static final String INVALID_LAN_SHARED_SECRET_MESSAGE = "Missing or invalid LAN shared secret";
    private final RestApiIdentityPolicy identityPolicy;
    private final LocalRateLimiter rateLimiter;
    private final String lanSharedSecret;

    RestApiRequestGuards(RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests) {
        this(identityPolicy, window, maxRequests, null, System::nanoTime);
    }

    RestApiRequestGuards(
            RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests, LongSupplier monotonicTicker) {
        this(identityPolicy, window, maxRequests, null, monotonicTicker);
    }

    RestApiRequestGuards(
            RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests, String lanSharedSecret) {
        this(identityPolicy, window, maxRequests, lanSharedSecret, System::nanoTime);
    }

    RestApiRequestGuards(
            RestApiIdentityPolicy identityPolicy,
            Duration window,
            int maxRequests,
            String lanSharedSecret,
            LongSupplier monotonicTicker) {
        this.identityPolicy = identityPolicy;
        this.rateLimiter = new LocalRateLimiter(window, maxRequests, monotonicTicker);
        this.lanSharedSecret = normalizeSharedSecret(lanSharedSecret);
    }

    void registerRequestGuards(Javalin app, Consumer<Context> localhostOnlyGuard) {
        app.beforeMatched(ctx -> {
            if (!ctx.path().startsWith("/api/")) {
                return;
            }
            localhostOnlyGuard.accept(ctx);
            enforceLanSharedSecret(ctx);
            enforceRateLimit(ctx);
            enforceMutatingRouteIdentity(ctx);
            identityPolicy.enforceScopedIdentity(ctx);
        });
    }

    void enforceLocalhostOnly(Context ctx) {
        if (isLoopbackAddress(ctx.ip())) {
            return;
        }
        throw new ApiForbiddenException(LOCALHOST_ONLY_MESSAGE);
    }

    void enforceLanSharedSecret(Context ctx) {
        if (lanSharedSecret == null || HEALTH_ROUTE.equals(ctx.path()) || ctx.method() == HandlerType.OPTIONS) {
            return;
        }
        String providedSecret = ctx.header(HEADER_LAN_SHARED_SECRET);
        if (providedSecret == null || !constantTimeEquals(lanSharedSecret, providedSecret)) {
            throw new ApiForbiddenException(INVALID_LAN_SHARED_SECRET_MESSAGE);
        }
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    void enforceRateLimit(Context ctx) {
        if (HEALTH_ROUTE.equals(ctx.path()) || ctx.method() == HandlerType.OPTIONS) {
            return;
        }
        String key = ctx.ip() + '|' + ctx.method();
        RateLimitDecision decision = rateLimiter.tryAcquire(key);
        if (decision.allowed()) {
            return;
        }
        throw new ApiTooManyRequestsException("Local API rate limit exceeded", decision.status());
    }

    void enforceMutatingRouteIdentity(Context ctx) {
        if (!requiresActingUserIdentity(ctx)) {
            return;
        }
        identityPolicy.requireActingUserId(ctx);
    }

    boolean requiresActingUserIdentity(Context ctx) {
        if (ctx.method() == HandlerType.OPTIONS) {
            return false;
        }
        String path = ctx.path();
        if (HEALTH_ROUTE.equals(path) || LOCATION_RESOLVE_ROUTE.equals(path)) {
            return false;
        }
        if (path.startsWith(AUTH_ROUTE_PREFIX)) {
            return AUTH_ME_ROUTE.equals(path);
        }
        if (path.startsWith(CONVERSATION_ROUTE_PREFIX)) {
            return true;
        }
        if (path.startsWith(USERS_ROUTE_PREFIX)) {
            return ctx.method() != HandlerType.GET || !isAnonymousUserReadRoute(path);
        }
        return switch (ctx.method()) {
            case POST, PUT, DELETE -> true;
            default -> false;
        };
    }

    private boolean isAnonymousUserReadRoute(String path) {
        if ("/api/users".equals(path)) {
            return true;
        }
        if (!path.startsWith(USERS_ROUTE_PREFIX)) {
            return false;
        }
        String remainingPath = path.substring(USERS_ROUTE_PREFIX.length());
        return !remainingPath.isBlank() && remainingPath.indexOf('/') < 0;
    }

    private boolean isLoopbackAddress(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }

    private static String normalizeSharedSecret(String lanSharedSecret) {
        return lanSharedSecret == null || lanSharedSecret.isBlank() ? null : lanSharedSecret.trim();
    }

    private static final class LocalRateLimiter {
        private static final int EVICTION_INTERVAL = 256;
        private static final long NANOS_PER_SECOND = 1_000_000_000L;
        private final long windowNanos;
        private final int maxRequests;
        private final LongSupplier monotonicTicker;
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger callCounter =
                new java.util.concurrent.atomic.AtomicInteger(0);

        private LocalRateLimiter(Duration window, int maxRequests, LongSupplier monotonicTicker) {
            this.windowNanos = window.toNanos();
            this.maxRequests = maxRequests;
            this.monotonicTicker = monotonicTicker;
        }

        private RateLimitDecision tryAcquire(String key) {
            long now = monotonicTicker.getAsLong();
            Window window = windows.compute(key, (ignored, current) -> {
                if (current == null || now - current.windowStartedAtNanos >= windowNanos) {
                    return new Window(now, 1);
                }
                return new Window(current.windowStartedAtNanos, current.requestCount + 1);
            });
            if (callCounter.incrementAndGet() % EVICTION_INTERVAL == 0) {
                evictStaleEntries(now);
            }
            long retryAfterNanos = Math.max(0L, windowNanos - (now - window.windowStartedAtNanos));
            long retryAfterSeconds = Math.max(1L, (retryAfterNanos + NANOS_PER_SECOND - 1L) / NANOS_PER_SECOND);
            return new RateLimitDecision(
                    window.requestCount <= maxRequests,
                    new RateLimitStatus(maxRequests, window.requestCount, retryAfterSeconds));
        }

        private void evictStaleEntries(long now) {
            long expiryThreshold = windowNanos * 2;
            windows.values().removeIf(w -> now - w.windowStartedAtNanos >= expiryThreshold);
        }

        private record Window(long windowStartedAtNanos, int requestCount) {}
    }

    private record RateLimitDecision(boolean allowed, RateLimitStatus status) {}

    record RateLimitStatus(int limit, int used, long retryAfterSeconds) implements java.io.Serializable {}

    static final class ApiForbiddenException extends RuntimeException {
        ApiForbiddenException(String message) {
            super(message);
        }
    }

    static final class ApiUnauthorizedException extends RuntimeException {
        ApiUnauthorizedException(String message) {
            super(message);
        }
    }

    static final class ApiTooManyRequestsException extends RuntimeException {
        private final RateLimitStatus status;

        ApiTooManyRequestsException(String message, RateLimitStatus status) {
            super(message);
            this.status = status;
        }

        RateLimitStatus status() {
            return status;
        }
    }
}
