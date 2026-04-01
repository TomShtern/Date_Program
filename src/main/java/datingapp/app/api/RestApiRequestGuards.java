package datingapp.app.api;

import datingapp.core.AppClock;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class RestApiRequestGuards {

    private static final String HEALTH_ROUTE = "/api/health";
    private static final String LOCALHOST_ONLY_MESSAGE = "REST API is restricted to localhost requests";
    private final RestApiIdentityPolicy identityPolicy;
    private final LocalRateLimiter rateLimiter;

    RestApiRequestGuards(RestApiIdentityPolicy identityPolicy, Duration window, int maxRequests) {
        this.identityPolicy = identityPolicy;
        this.rateLimiter = new LocalRateLimiter(window, maxRequests);
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
        private final long windowMillis;
        private final int maxRequests;
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger callCounter =
                new java.util.concurrent.atomic.AtomicInteger(0);

        private LocalRateLimiter(Duration window, int maxRequests) {
            this.windowMillis = window.toMillis();
            this.maxRequests = maxRequests;
        }

        private RateLimitDecision tryAcquire(String key) {
            long now = AppClock.now().toEpochMilli();
            Window window = windows.compute(key, (ignored, current) -> {
                if (current == null || now - current.windowStartedAtMillis >= windowMillis) {
                    return new Window(now, 1);
                }
                return new Window(current.windowStartedAtMillis, current.requestCount + 1);
            });
            if (callCounter.incrementAndGet() % EVICTION_INTERVAL == 0) {
                evictStaleEntries(now);
            }
            long retryAfterMillis = Math.max(0L, windowMillis - (now - window.windowStartedAtMillis));
            long retryAfterSeconds = Math.max(1L, (retryAfterMillis + 999L) / 1000L);
            return new RateLimitDecision(
                    window.requestCount <= maxRequests,
                    new RateLimitStatus(maxRequests, window.requestCount, retryAfterSeconds));
        }

        private void evictStaleEntries(long now) {
            long expiryThreshold = windowMillis * 2;
            windows.values().removeIf(w -> now - w.windowStartedAtMillis >= expiryThreshold);
        }

        private record Window(long windowStartedAtMillis, int requestCount) {}
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
