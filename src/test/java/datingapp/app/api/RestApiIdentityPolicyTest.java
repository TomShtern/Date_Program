package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RestApiIdentityPolicy")
class RestApiIdentityPolicyTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Test
    @DisplayName("resolveActingUserId and requireActingUserId use the X-User-Id header")
    void resolveActingUserIdAndRequireActingUserIdUseTheHeader() {
        RestApiIdentityPolicy policy = new RestApiIdentityPolicy();
        UUID userId = UUID.randomUUID();
        Context withHeader = context(
                "/api/users/" + userId, Map.of(USER_ID_HEADER, userId.toString()), Map.of("id", userId.toString()));
        Context withoutHeader = context("/api/users/" + userId, Map.of(), Map.of("id", userId.toString()));

        assertEquals(userId, policy.resolveActingUserId(withHeader).orElseThrow());
        assertEquals(userId, policy.requireActingUserId(withHeader));
        assertEquals(java.util.Optional.empty(), policy.resolveActingUserId(withoutHeader));
        assertThrows(IllegalArgumentException.class, () -> policy.requireActingUserId(withoutHeader));
    }

    @Test
    @DisplayName("enforceScopedIdentity rejects mismatched id and authorId path params")
    void enforceScopedIdentityRejectsMismatchedIdAndAuthorIdPathParams() {
        RestApiIdentityPolicy policy = new RestApiIdentityPolicy();
        UUID actingUserId = UUID.randomUUID();

        Context mismatchedId = context(
                "/api/users/other/profile",
                Map.of(USER_ID_HEADER, actingUserId.toString()),
                Map.of("id", UUID.randomUUID().toString()));
        Context mismatchedAuthorId = context(
                "/api/users/author/notes",
                Map.of(USER_ID_HEADER, actingUserId.toString()),
                Map.of("authorId", UUID.randomUUID().toString()));

        assertThrows(
                RestApiRequestGuards.ApiForbiddenException.class, () -> policy.enforceScopedIdentity(mismatchedId));
        assertThrows(
                RestApiRequestGuards.ApiForbiddenException.class,
                () -> policy.enforceScopedIdentity(mismatchedAuthorId));
    }

    @Test
    @DisplayName("enforceScopedIdentity allows viewer headers on direct user read routes")
    void enforceScopedIdentityAllowsViewerHeadersOnDirectUserReadRoutes() {
        RestApiIdentityPolicy policy = new RestApiIdentityPolicy();
        UUID actingUserId = UUID.randomUUID();

        Context otherUserRead = context(
                "/api/users/" + UUID.randomUUID(),
                Map.of(USER_ID_HEADER, actingUserId.toString()),
                Map.of("id", UUID.randomUUID().toString()));

        assertDoesNotThrow(() -> policy.enforceScopedIdentity(otherUserRead));
    }

    @Test
    @DisplayName("conversation participant parsing supports members and rejects outsiders")
    void conversationParticipantParsingSupportsMembersAndRejectsOutsiders() {
        RestApiIdentityPolicy policy = new RestApiIdentityPolicy();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        String conversationId = first + "_" + second;

        RestApiIdentityPolicy.ConversationParticipants participants =
                policy.parseConversationParticipants(conversationId);

        assertDoesNotThrow(() -> participants.requireParticipant(first));
        assertDoesNotThrow(() -> participants.requireParticipant(second));
        assertEquals(second, participants.otherParticipant(first));
        assertEquals(first, participants.otherParticipant(second));
        assertThrows(RestApiRequestGuards.ApiForbiddenException.class, () -> participants.requireParticipant(outsider));
        assertThrows(
                IllegalArgumentException.class, () -> policy.parseConversationParticipants("not-a-conversation-id"));
    }

    private static Context context(String path, Map<String, String> headers, Map<String, String> pathParams) {
        Map<String, String> headerCopy = new HashMap<>(headers);
        Map<String, String> pathParamCopy = new HashMap<>(pathParams);
        return (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[] {Context.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "path" -> path;
                    case "method" -> HandlerType.GET;
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
