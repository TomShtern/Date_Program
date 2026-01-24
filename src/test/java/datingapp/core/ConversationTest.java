package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Messaging.Conversation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Conversation Entity Tests")
class ConversationTest {

    @Nested
    @DisplayName("Conversation Creation")
    class ConversationCreation {

        @Test
        @DisplayName("should create conversation with deterministic ID")
        void createWithDeterministicId() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

            Conversation convo = Conversation.create(a, b);

            assertNotNull(convo.getId());
            assertTrue(convo.getId().contains("_"));
            assertEquals(a, convo.getUserA()); // a is smaller
            assertEquals(b, convo.getUserB());
            assertNotNull(convo.getCreatedAt());
            assertNull(convo.getLastMessageAt());
        }

        @Test
        @DisplayName("should produce same ID regardless of argument order")
        void idIsOrderIndependent() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

            Conversation convo1 = Conversation.create(a, b);
            Conversation convo2 = Conversation.create(b, a);

            assertEquals(convo1.getId(), convo2.getId());
        }
    }

    @Nested
    @DisplayName("generateId")
    class GenerateId {

        @Test
        @DisplayName("should generate same ID regardless of order")
        void generateIdOrderIndependent() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

            String id1 = Conversation.generateId(a, b);
            String id2 = Conversation.generateId(b, a);

            assertEquals(id1, id2);
        }

        @Test
        @DisplayName("should format as userA_userB")
        void formatAsUnderscoreSeparated() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

            String id = Conversation.generateId(a, b);

            assertTrue(id.contains("_"));
            String[] parts = id.split("_");
            assertEquals(2, parts.length);
        }
    }

    @Nested
    @DisplayName("User Queries")
    class UserQueries {

        @Test
        @DisplayName("involves should return true for both users")
        void involvesReturnsTrue() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);

            assertTrue(convo.involves(a));
            assertTrue(convo.involves(b));
        }

        @Test
        @DisplayName("involves should return false for other users")
        void involvesReturnsFalse() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);

            assertFalse(convo.involves(c));
        }

        @Test
        @DisplayName("getOtherUser should return the other participant")
        void getOtherUserReturnsCorrect() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);

            assertEquals(b, convo.getOtherUser(a));
            assertEquals(a, convo.getOtherUser(b));
        }

        @Test
        @DisplayName("getOtherUser should throw for non-participant")
        void getOtherUserThrows() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);

            assertThrows(IllegalArgumentException.class, () -> convo.getOtherUser(c));
        }
    }

    @Nested
    @DisplayName("Timestamp Updates")
    class TimestampUpdates {

        @Test
        @DisplayName("should update last message timestamp")
        void updateLastMessageAt() {
            Conversation convo = Conversation.create(UUID.randomUUID(), UUID.randomUUID());
            assertNull(convo.getLastMessageAt());

            Instant now = Instant.now();
            convo.updateLastMessageAt(now);

            assertEquals(now, convo.getLastMessageAt());
        }

        @Test
        @DisplayName("should update read timestamp for userA")
        void updateReadTimestampForUserA() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);
            assertNull(convo.getLastReadAt(a));

            Instant now = Instant.now();
            convo.updateReadTimestamp(a, now);

            assertEquals(now, convo.getLastReadAt(a));
            assertNull(convo.getLastReadAt(b));
        }

        @Test
        @DisplayName("should update read timestamp for userB")
        void updateReadTimestampForUserB() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);

            Instant now = Instant.now();
            convo.updateReadTimestamp(b, now);

            assertEquals(now, convo.getLastReadAt(b));
            assertNull(convo.getLastReadAt(a));
        }

        @Test
        @DisplayName("should throw when updating read for non-participant")
        void updateReadTimestampThrows() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            Conversation convo = Conversation.create(a, b);
            Instant now = Instant.now();

            assertThrows(IllegalArgumentException.class, () -> convo.updateReadTimestamp(c, now));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject conversation with yourself")
        void rejectSelfConversation() {
            UUID a = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class, () -> Conversation.create(a, a));
        }

        @Test
        @DisplayName("should reject null userA")
        void rejectNullUserA() {
            UUID userB = UUID.randomUUID();
            assertThrows(NullPointerException.class, () -> Conversation.create(null, userB));
        }

        @Test
        @DisplayName("should reject null userB")
        void rejectNullUserB() {
            UUID userA = UUID.randomUUID();
            assertThrows(NullPointerException.class, () -> Conversation.create(userA, null));
        }
    }
}
