package datingapp.app.api;

import datingapp.app.api.RestApiRequestGuards.ApiForbiddenException;
import io.javalin.http.Context;
import java.util.Optional;
import java.util.UUID;

final class RestApiIdentityPolicy {

    private static final String HEADER_ACTING_USER_ID = "X-User-Id";
    private static final String CONVERSATION_ROUTE_PREFIX = "/api/conversations/";
    private static final String USER_ROUTE_MESSAGE = "Acting user does not match requested user route";
    private static final String CONVERSATION_MESSAGE = "User is not part of this conversation";

    void enforceScopedIdentity(Context ctx) {
        validateActingUserMatchesPathParam(ctx, "id");
        validateActingUserMatchesPathParam(ctx, "authorId");

        if (ctx.path().startsWith(CONVERSATION_ROUTE_PREFIX)
                && ctx.pathParamMap().containsKey("conversationId")) {
            resolveActingUserId(ctx)
                    .ifPresent(actingUserId -> parseConversationParticipants(ctx.pathParam("conversationId"))
                            .requireParticipant(actingUserId));
        }
    }

    Optional<UUID> resolveActingUserId(Context ctx) {
        String rawUserId = ctx.header(HEADER_ACTING_USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parseUuid(rawUserId));
    }

    UUID requireActingUserId(Context ctx) {
        return resolveActingUserId(ctx)
                .orElseThrow(() -> new IllegalArgumentException(
                        HEADER_ACTING_USER_ID + " header is required for scoped API routes"));
    }

    void validateActingUserMatchesPathParam(Context ctx, String paramName) {
        if (!ctx.pathParamMap().containsKey(paramName)) {
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
