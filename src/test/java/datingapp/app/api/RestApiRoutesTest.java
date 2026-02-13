package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for REST API DTOs.
 *
 * <p>Tests the DTO records used in API responses.
 */
@SuppressWarnings("unused")
@DisplayName("REST API DTOs")
class RestApiRoutesTest {

    @Nested
    @DisplayName("RestApiServer.MessageDto")
    class MessageDtoTests {

        @Test
        @DisplayName("Creates from message")
        void createsFromMessage() {
            java.util.UUID senderId = java.util.UUID.randomUUID();
            String conversationId = "test-conversation";
            datingapp.core.connection.ConnectionModels.Message message =
                    datingapp.core.connection.ConnectionModels.Message.create(conversationId, senderId, "Hello!");

            RestApiServer.MessageDto dto = RestApiServer.MessageDto.from(message);

            assertEquals(message.id(), dto.id());
            assertEquals(conversationId, dto.conversationId());
            assertEquals(senderId, dto.senderId());
            assertEquals("Hello!", dto.content());
            assertNotNull(dto.sentAt());
        }
    }

    @Nested
    @DisplayName("RestApiServer.ErrorResponse")
    class ErrorResponseTests {

        @Test
        @DisplayName("Creates error response")
        void createsErrorResponse() {
            RestApiServer.ErrorResponse error = new RestApiServer.ErrorResponse("NOT_FOUND", "User not found");

            assertEquals("NOT_FOUND", error.code());
            assertEquals("User not found", error.message());
        }
    }

    @Nested
    @DisplayName("RestApiServer.HealthResponse")
    class HealthResponseTests {

        @Test
        @DisplayName("Creates health response")
        void createsHealthResponse() {
            RestApiServer.HealthResponse health = new RestApiServer.HealthResponse("ok", 12345L);

            assertEquals("ok", health.status());
            assertEquals(12345L, health.timestamp());
        }
    }
}
