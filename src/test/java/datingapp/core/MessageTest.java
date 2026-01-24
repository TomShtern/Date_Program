package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Messaging.Message;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Message Entity Tests")
class MessageTest {

    private static final String CONVERSATION_ID = "conv_123";
    private static final UUID SENDER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Message Creation")
    class MessageCreation {

        @Test
        @DisplayName("should create message with valid inputs")
        void createWithValidInputs() {
            Message message = Message.create(CONVERSATION_ID, SENDER_ID, "Hello!");

            assertNotNull(message.id());
            assertEquals(CONVERSATION_ID, message.conversationId());
            assertEquals(SENDER_ID, message.senderId());
            assertEquals("Hello!", message.content());
            assertNotNull(message.createdAt());
        }

        @Test
        @DisplayName("should trim whitespace from content")
        void trimWhitespace() {
            Message message = Message.create(CONVERSATION_ID, SENDER_ID, "  Hello World  ");
            assertEquals("Hello World", message.content());
        }

        @Test
        @DisplayName("should allow message at max length")
        void allowMaxLength() {
            String content = "a".repeat(Message.MAX_LENGTH);
            Message message = Message.create(CONVERSATION_ID, SENDER_ID, content);
            assertEquals(1000, message.content().length());
        }

        @Test
        @DisplayName("should allow emoji-only messages")
        void allowEmojiOnly() {
            Message message = Message.create(CONVERSATION_ID, SENDER_ID, "🏔️❤️👋");
            assertEquals("🏔️❤️👋", message.content());
        }

        @Test
        @DisplayName("should preserve newlines in content")
        void preserveNewlines() {
            Message message = Message.create(CONVERSATION_ID, SENDER_ID, "Hello\nWorld");
            assertEquals("Hello\nWorld", message.content());
        }
    }

    @Nested
    @DisplayName("Message Validation")
    class MessageValidation {

        @Test
        @DisplayName("should reject null content")
        void rejectNullContent() {
            assertThrows(IllegalArgumentException.class, () -> Message.create(CONVERSATION_ID, SENDER_ID, null));
        }

        @Test
        @DisplayName("should reject empty content")
        void rejectEmptyContent() {
            assertThrows(IllegalArgumentException.class, () -> Message.create(CONVERSATION_ID, SENDER_ID, ""));
        }

        @Test
        @DisplayName("should reject whitespace-only content")
        void rejectWhitespaceContent() {
            assertThrows(IllegalArgumentException.class, () -> Message.create(CONVERSATION_ID, SENDER_ID, "   "));
        }

        @Test
        @DisplayName("should reject content exceeding max length")
        void rejectTooLongContent() {
            String content = "a".repeat(Message.MAX_LENGTH + 1);
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> Message.create(CONVERSATION_ID, SENDER_ID, content));
            assertTrue(ex.getMessage().contains("too long"));
        }

        @Test
        @DisplayName("should reject null conversationId")
        void rejectNullConversationId() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.now();
            assertThrows(NullPointerException.class, () -> new Message(id, null, SENDER_ID, "Hello", createdAt));
        }

        @Test
        @DisplayName("should reject null senderId")
        void rejectNullSenderId() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.now();
            assertThrows(NullPointerException.class, () -> new Message(id, CONVERSATION_ID, null, "Hello", createdAt));
        }

        @Test
        @DisplayName("should reject null id")
        void rejectNullId() {
            Instant createdAt = Instant.now();
            assertThrows(
                    NullPointerException.class,
                    () -> new Message(null, CONVERSATION_ID, SENDER_ID, "Hello", createdAt));
        }
    }
}
