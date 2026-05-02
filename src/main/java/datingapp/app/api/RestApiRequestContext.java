package datingapp.app.api;

import datingapp.app.usecase.auth.AuthUseCases;
import datingapp.app.usecase.auth.AuthUseCases.AuthIdentity;
import io.javalin.http.Context;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

final class RestApiRequestContext {

    static final String HEADER_REQUEST_ID = "X-Request-Id";
    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_USER_ID = "userId";

    private final AuthUseCases authUseCases;

    RestApiRequestContext(AuthUseCases authUseCases) {
        this.authUseCases = authUseCases;
    }

    void beforeRequest(Context ctx) {
        String requestId = UUID.randomUUID().toString();
        ctx.attribute(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
        resolveUserIdToMdc(ctx);
        ctx.header(HEADER_REQUEST_ID, requestId);
    }

    void afterRequest(Context ctx) {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_USER_ID);
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void resolveUserIdToMdc(Context ctx) {
        try {
            Optional<String> bearerToken = resolveBearerToken(ctx);
            if (bearerToken.isPresent()) {
                authUseCases
                        .authenticateAccessToken(bearerToken.get())
                        .map(AuthIdentity::userId)
                        .ifPresent(userId -> MDC.put(MDC_USER_ID, userId.toString()));
            }
            String legacyHeader = ctx.header("X-User-Id");
            if (legacyHeader != null && !legacyHeader.isBlank() && MDC.get(MDC_USER_ID) == null) {
                MDC.put(MDC_USER_ID, legacyHeader.trim());
            }
        } catch (Exception _) { // nopmd
            // This filter must never throw; userId MDC is best-effort diagnostics
        }
    }

    private Optional<String> resolveBearerToken(Context ctx) {
        String authorization = ctx.header("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix) || authorization.length() <= prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(authorization.substring(prefix.length()).trim());
    }
}
