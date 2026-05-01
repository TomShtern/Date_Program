package datingapp.app.api;

import datingapp.app.api.RestApiRequestGuards.ApiForbiddenException;
import datingapp.app.api.RestApiRequestGuards.ApiUnauthorizedException;
import datingapp.app.usecase.auth.AuthUseCases;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.util.Optional;
import java.util.UUID;

final class RestApiIdentityPolicy {

    private static final String HEADER_ACTING_USER_ID = "X-User-Id";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CONVERSATION_ROUTE_PREFIX = "/api/conversations/";
    private static final String USER_ROUTE_PREFIX = "/api/users/";
    private static final String USER_ROUTE_MESSAGE = "Acting user does not match requested user route";
    private static final String CONVERSATION_MESSAGE = "User is not part of this conversation";
    private static final String INVALID_BEARER_MESSAGE = "Missing or invalid bearer token";

    private final AuthUseCases authUseCases;

    RestApiIdentityPolicy() {
        this(null);
    }

    RestApiIdentityPolicy(AuthUseCases authUseCases) {
        this.authUseCases = authUseCases;
    }

    void enforceScopedIdentity(Context ctx) {
        validateActingUserMatchesPathParam(ctx, "id");
        validateActingUserMatchesPathParam(ctx, "authorId");
        validateActingUserMatchesPathParam(ctx, "viewerId");

        if (ctx.path().startsWith(CONVERSATION_ROUTE_PREFIX)
                && ctx.pathParamMap().containsKey("conversationId")) {
            resolveActingUserId(ctx)
                    .ifPresent(actingUserId -> parseConversationParticipants(ctx.pathParam("conversationId"))
                            .requireParticipant(actingUserId));
        }
    }

    Optional<UUID> resolveActingUserId(Context ctx) {
        if (authUseCases == null) {
            return resolveLegacyHeader(ctx);
        }
        Optional<String> bearerToken = resolveBearerToken(ctx);
        if (bearerToken.isEmpty()) {
            return Optional.empty();
        }
        UUID authenticatedUserId = authUseCases
                .authenticateAccessToken(bearerToken.get())
                .map(AuthUseCases.AuthIdentity::userId)
                .orElseThrow(() -> new ApiUnauthorizedException(INVALID_BEARER_MESSAGE));
        validateLegacyHeaderMatchesSubject(ctx, authenticatedUserId);
        return Optional.of(authenticatedUserId);
    }

    private Optional<UUID> resolveLegacyHeader(Context ctx) {
        String rawUserId = ctx.header(HEADER_ACTING_USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parseUuid(rawUserId));
    }

    UUID requireActingUserId(Context ctx) {
        if (authUseCases == null) {
            return resolveActingUserId(ctx)
                    .orElseThrow(() -> new IllegalArgumentException(
                            HEADER_ACTING_USER_ID + " header is required for scoped API routes"));
        }
        return resolveActingUserId(ctx).orElseThrow(() -> new ApiUnauthorizedException(INVALID_BEARER_MESSAGE));
    }

    void validateActingUserMatchesPathParam(Context ctx, String paramName) {
        if (!ctx.pathParamMap().containsKey(paramName)) {
            return;
        }
        if (isDirectUserReadRoute(ctx, paramName)) {
            return;
        }
        Optional<UUID> actingUserId = resolveActingUserId(ctx);
        if (actingUserId.isEmpty()) {
            return;
        }
        UUID routeUserId = parseUuid(ctx.pathParam(paramName));
        if (!routeUserId.equals(actingUserId.get())) {
            throw new ApiForbiddenException(USER_ROUTE_MESSAGE);
        }
    }

    private boolean isDirectUserReadRoute(Context ctx, String paramName) {
        if (!"id".equals(paramName) || ctx.method() != HandlerType.GET) {
            return false;
        }
        String path = ctx.path();
        return path.startsWith(USER_ROUTE_PREFIX) && path.indexOf('/', USER_ROUTE_PREFIX.length()) < 0;
    }

    ConversationParticipants parseConversationParticipants(String conversationId) {
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid conversation ID format");
        }
        try {
            return new ConversationParticipants(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid conversation ID format", ex);
        }
    }

    UUID extractRecipientFromConversation(String conversationId, UUID senderId) {
        return parseConversationParticipants(conversationId).otherParticipant(senderId);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, e);
        }
    }

    private Optional<String> resolveBearerToken(Context ctx) {
        String authorization = ctx.header(HEADER_AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() <= BEARER_PREFIX.length()) {
            throw new ApiUnauthorizedException(INVALID_BEARER_MESSAGE);
        }
        return Optional.of(authorization.substring(BEARER_PREFIX.length()).trim());
    }

    private void validateLegacyHeaderMatchesSubject(Context ctx, UUID authenticatedUserId) {
        String rawUserId = ctx.header(HEADER_ACTING_USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            return;
        }
        if (!authenticatedUserId.equals(parseUuid(rawUserId))) {
            throw new ApiForbiddenException(USER_ROUTE_MESSAGE);
        }
    }

    record ConversationParticipants(UUID firstUserId, UUID secondUserId) {
        boolean involves(UUID userId) {
            return firstUserId.equals(userId) || secondUserId.equals(userId);
        }

        UUID otherParticipant(UUID userId) {
            if (firstUserId.equals(userId)) {
                return secondUserId;
            }
            if (secondUserId.equals(userId)) {
                return firstUserId;
            }
            throw new ApiForbiddenException(CONVERSATION_MESSAGE);
        }

        void requireParticipant(UUID userId) {
            if (!involves(userId)) {
                throw new ApiForbiddenException(CONVERSATION_MESSAGE);
            }
        }
    }
}
