package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.http.Context;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("RestApiRequestContext")
class RestApiRequestContextTest {

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("beforeRequest generates UUID requestId in MDC and context attribute")
    void beforeRequestGeneratesRequestId() {
        RestApiRequestContext context = new RestApiRequestContext(null);
        Map<String, Object> attributes = new HashMap<>();
        Context ctx = contextWithAttributes(attributes);

        context.beforeRequest(ctx);

        String requestId = MDC.get(RestApiRequestContext.MDC_REQUEST_ID);
        assertNotNull(requestId, "requestId should be set in MDC");
        assertTrue(requestId.matches("[0-9a-f-]{36}"), "requestId should be a UUID format");

        Object attrRequestId = attributes.get(RestApiRequestContext.MDC_REQUEST_ID);
        assertNotNull(attrRequestId, "requestId should be set as context attribute");
        assertTrue(((String) attrRequestId).matches("[0-9a-f-]{36}"));

        context.afterRequest(ctx);
    }

    @Test
    @DisplayName("afterRequest clears requestId and userId from MDC")
    void afterRequestClearsMdc() {
        RestApiRequestContext context = new RestApiRequestContext(null);
        Map<String, Object> attributes = new HashMap<>();
        Context ctx = contextWithAttributes(attributes);

        context.beforeRequest(ctx);
        assertNotNull(MDC.get(RestApiRequestContext.MDC_REQUEST_ID));

        context.afterRequest(ctx);
        assertEquals(null, MDC.get(RestApiRequestContext.MDC_REQUEST_ID));
        assertEquals(null, MDC.get(RestApiRequestContext.MDC_USER_ID));
    }

    @Test
    @DisplayName("beforeRequest with legacy X-User-Id header sets userId MDC")
    void beforeRequestWithLegacyHeaderSetsUserId() {
        RestApiRequestContext context = new RestApiRequestContext(null);
        String userId = java.util.UUID.randomUUID().toString();
        Map<String, Object> attributes = new HashMap<>();
        Context ctx = contextWithAttributesAndHeaders(attributes, Map.of("X-User-Id", userId));

        context.beforeRequest(ctx);

        assertEquals(userId, MDC.get(RestApiRequestContext.MDC_USER_ID));

        context.afterRequest(ctx);
    }

    @SuppressWarnings("unchecked")
    private static Context contextWithAttributes(Map<String, Object> attributes) {
        return contextWithAttributesAndHeaders(attributes, new HashMap<>());
    }

    private static Context contextWithAttributesAndHeaders(
            Map<String, Object> attributes, Map<String, String> headers) {
        Map<String, String> headerCopy = new HashMap<>(headers);
        return (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[] {Context.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "attribute" -> {
                        if (args.length == 2) {
                            attributes.put((String) args[0], args[1]);
                            yield null;
                        }
                        yield attributes.get(args[0]);
                    }
                    case "header" -> headerCopy.get(args[0].toString());
                    default -> defaultValue(invokedMethod.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) return false;
        if (returnType.equals(byte.class)) return (byte) 0;
        if (returnType.equals(short.class)) return (short) 0;
        if (returnType.equals(char.class)) return '\0';
        if (returnType.equals(int.class)) return 0;
        if (returnType.equals(long.class)) return 0L;
        if (returnType.equals(float.class)) return 0.0f;
        if (returnType.equals(double.class)) return 0.0d;
        return null;
    }
}
