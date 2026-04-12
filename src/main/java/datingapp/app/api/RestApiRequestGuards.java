package datingapp.app.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class RestApiRequestGuards {

    private static final String HEALTH_ROUTE = "/api/health";
    private static final String LOCALHOST_ONLY_MESSAGE = "REST API is restricted to localhost requests";
    private final RestApiIdentityPolicy identityPolicy;
    private final LocalRateLimiter rateLimiter;

    RestApiRequestGuards(RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests) {
        this(identityPolicy, window, maxRequests, System::nanoTime);
    }

    RestApiRequestGuards(
            RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests, LongSupplier monotonicTicker) {
        this.identityPolicy = identityPolicy;
        this.rateLimiter = new LocalRateLimiter(window, maxRequests, monotonicTicker);
    }

    void registerRequestGuards(Javalin app, Consumer<Context> localhostOnlyGuard) {
        app.beforeMatched(ctx -> {
            if (!ctx.path().startsWith("/api/")) {
                return;
            }
            localhostOnlyGuard.accept(ctx);
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

    void enforceRateLimit(Context ctx) {
        if (HEALTH_ROUTE.equals(ctx.path())) {
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
        return switch (ctx.method()) {
            case POST, PUT, DELETE -> !HEALTH_ROUTE.equals(ctx.path()) && !"/api/location/resolve".equals(ctx.path());
            default -> false;
        };
    }

    private boolean isLoopbackAddress(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
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
