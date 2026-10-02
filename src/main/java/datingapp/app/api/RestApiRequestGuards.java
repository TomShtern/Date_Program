package datingapp.app.api;

import datingapp.app.api.RestApiExceptions.ApiForbiddenException;
import datingapp.app.api.RestApiExceptions.ApiTooManyRequestsException;
import datingapp.app.api.RestApiExceptions.RateLimitStatus;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

final class RestApiRequestGuards {

    static final String HEADER_LAN_SHARED_SECRET = "X-DatingApp-Shared-Secret";
    private static final String HEALTH_ROUTE = "/api/health";
    private static final String AUTH_ROUTE_PREFIX = "/api/auth/";
    static final String CONVERSATION_ROUTE_PREFIX = "/api/conversations/";
    private static final String LOCATION_ROUTE_PREFIX = "/api/location/";
    static final String USERS_ROUTE_PREFIX = "/api/users/";
    private static final String USERS_LIST_ROUTE = "/api/users";
    private static final String LOCALHOST_ONLY_MESSAGE = "REST API is restricted to localhost requests";
    private static final String INVALID_LAN_SHARED_SECRET_MESSAGE = "Missing or invalid LAN shared secret";
    private static final Pattern CLIENT_IP_HEADER_NAME = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private final RestApiIdentityPolicy identityPolicy;
    private final LocalRateLimiter rateLimiter;
    private final String lanSharedSecret;
    private final String clientIpHeader;

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
        this(identityPolicy, window, maxRequests, lanSharedSecret, null, monotonicTicker);
    }

    /**
     * @param clientIpHeader name of a header set by a trusted reverse proxy that runs on this machine (for example a
     *     tunnel) and carries the real client address; {@code null} or blank disables it. The header is honored only
     *     when the socket peer is itself a loopback address.
     */
    RestApiRequestGuards(
            RestApiIdentityPolicy identityPolicy,
            Duration window,
            int maxRequests,
            String lanSharedSecret,
            String clientIpHeader,
            LongSupplier monotonicTicker) {
        this.identityPolicy = identityPolicy;
        this.rateLimiter = new LocalRateLimiter(window, maxRequests, monotonicTicker);
        this.lanSharedSecret = lanSharedSecret == null ? null : lanSharedSecret.trim();
        this.clientIpHeader = normalizeClientIpHeader(clientIpHeader);
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
        String key = clientIp(ctx) + '|' + ctx.method();
        RateLimitDecision decision = rateLimiter.tryAcquire(key);
        if (decision.allowed()) {
            return;
        }
        throw new ApiTooManyRequestsException("Local API rate limit exceeded", decision.status());
    }

    /**
     * The address the rate limiter charges a request to. Without a configured proxy header this is the socket peer.
     * With one, the header is trusted only when the peer is loopback: the bind is wide, so any LAN client could
     * otherwise send the header itself and pick its own bucket. A missing or malformed value falls back to the peer.
     */
    String clientIp(Context ctx) {
        String peer = ctx.ip();
        if (clientIpHeader == null || !isLoopbackAddress(peer)) {
            return peer;
        }
        String forwarded = ctx.header(clientIpHeader);
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        // The nearest proxy appends last, so the last entry is the only one a client cannot have written.
        String candidate = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
        return normalizeIpLiteral(candidate).orElse(peer);
    }

    private static Optional<String> normalizeIpLiteral(String candidate) {
        // Only numeric literals reach InetAddress.getByName, so this can never trigger a DNS lookup.
        boolean ipv4 = IPV4_LITERAL.matcher(candidate).matches();
        boolean ipv6 =
                candidate.indexOf(':') >= 0 && IPV6_LITERAL.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(candidate).getHostAddress());
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private static String normalizeClientIpHeader(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            return null;
        }
        String trimmed = headerName.trim();
        if (!CLIENT_IP_HEADER_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid client IP header name: " + trimmed);
        }
        return trimmed;
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
        if (HEALTH_ROUTE.equals(path)) {
            return false;
        }
        if (path.startsWith(AUTH_ROUTE_PREFIX)) {
            // Auth routes verify the Clerk token themselves; there is no local acting user yet on first sign-in.
            return false;
        }
        if (path.startsWith(LOCATION_ROUTE_PREFIX)) {
            // Reference and geocoding data, but the API is public-facing: only a signed-in user may call it.
            return true;
        }
        if (path.startsWith(CONVERSATION_ROUTE_PREFIX)) {
            return true;
        }
        if (path.startsWith(USERS_ROUTE_PREFIX) || USERS_LIST_ROUTE.equals(path)) {
            // Profile reads need a viewer too: block and visibility checks cannot run for an anonymous caller.
            return true;
        }
        return switch (ctx.method()) {
            case POST, PUT, DELETE -> true;
            default -> false;
        };
    }

    static boolean isLoopbackAddress(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception _) {
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
}
