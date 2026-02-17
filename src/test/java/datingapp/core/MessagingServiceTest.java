package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.*;
import datingapp.core.model.ProfileNote;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("ConnectionService Tests")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MessagingServiceTest {

    private CommunicationStorage messagingStorage;
    private InteractionStorage matchStorage;
    private InMemoryUserStorage userStorage;
    private ConnectionService messagingService;

    private UUID userA;
    private UUID userB;
    private UUID userC;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        messagingStorage = new TestStorages.Communications();
        matchStorage = new TestStorages.Interactions();
        userStorage = new InMemoryUserStorage();
        messagingService = new ConnectionService(AppConfig.defaults(), messagingStorage, matchStorage, userStorage);

        // Create test users
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();

        userStorage.save(createActiveUser(userA, "Alice"));
        userStorage.save(createActiveUser(userB, "Bob"));
        userStorage.save(createActiveUser(userC, "Carol"));
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    private User createActiveUser(UUID id, String name) {
        Instant now = AppClock.now();
        return User.StorageBuilder.create(id, name, now)
                .bio("Test bio")
                .state(UserState.ACTIVE)
                .updatedAt(now)
                .build();
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("should succeed with active match")
        void succeedWithActiveMatch() {
            // Create active match
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, "Hello Bob!");

            assertTrue(result.success());
            assertNotNull(result.message());
            assertEquals("Hello Bob!", result.message().content());
            assertEquals(userA, result.message().senderId());
        }

        @Test
        @DisplayName("should create conversation on first message")
        void createConversationOnFirst() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            String conversationId = Conversation.generateId(userA, userB);
            assertTrue(messagingStorage.getConversation(conversationId).isEmpty());

            messagingService.sendMessage(userA, userB, "First message!");

            assertTrue(messagingStorage.getConversation(conversationId).isPresent());
        }

        @Test
        @DisplayName("should fail without match")
        void failWithoutMatch() {
            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with unmatched state")
        void failWithUnmatchedState() {
            Match match = Match.create(userA, userB);
            match.unmatch(userA);
            matchStorage.save(match);

            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with blocked state")
        void failWithBlockedState() {
            Match match = Match.create(userA, userB);
            match.block(userA);
            matchStorage.save(match);

            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with empty content")
        void failWithEmptyContent() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, "   ");

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.EMPTY_MESSAGE, result.errorCode());
        }

        @Test
        @DisplayName("should fail with too long content")
        void failWithTooLongContent() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            String longContent = "a".repeat(Message.MAX_LENGTH + 1);
            ConnectionService.SendResult result = messagingService.sendMessage(userA, userB, longContent);

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.MESSAGE_TOO_LONG, result.errorCode());
        }

        @Test
        @DisplayName("should fail if sender not found")
        void failIfSenderNotFound() {
            UUID unknownUser = UUID.randomUUID();
            Match match = Match.create(unknownUser, userB);
            matchStorage.save(match);

            ConnectionService.SendResult result = messagingService.sendMessage(unknownUser, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(ConnectionService.SendResult.ErrorCode.USER_NOT_FOUND, result.errorCode());
        }

        @Test
        @DisplayName("should update lastMessageAt on conversation")
        void updateLastMessageAt() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "Hello!");

            String conversationId = Conversation.generateId(userA, userB);
            Conversation convo =
                    messagingStorage.getConversation(conversationId).orElseThrow();
            assertNotNull(convo.getLastMessageAt());
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("should return messages in order")
        void returnMessagesInOrder() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "First");
            messagingService.sendMessage(userB, userA, "Second");

            List<Message> messages = messagingService.getMessages(userA, userB, 10, 0);

            assertEquals(2, messages.size());
            assertEquals("First", messages.get(0).content());
            assertEquals("Second", messages.get(1).content());
        }

        @Test
        @DisplayName("should return empty list for no conversation")
        void returnEmptyForNoConversation() {
            List<Message> messages = messagingService.getMessages(userA, userB, 10, 0);
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("should return empty list for zero limit")
        void returnsEmptyForZeroLimit() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "Hello");

            List<Message> msgs = messagingService.getMessages(userA, userB, 0, 0);
            assertNotNull(msgs);
            assertTrue(msgs.isEmpty(), "Zero limit should return empty list");
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("canMessage")
    class CanMessage {

        @Test
        @DisplayName("should return true for active match")
        void trueForActiveMatch() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            assertTrue(messagingService.canMessage(userA, userB));
        }

        @Test
        @DisplayName("should return true for friends match")
        void trueForFriendsMatch() {
            Match match = Match.create(userA, userB);
            match.transitionToFriends(userA);
            matchStorage.save(match);

            assertTrue(messagingService.canMessage(userA, userB));
        }

        @Test
        @DisplayName("should return false for no match")
        void falseForNoMatch() {
            assertFalse(messagingService.canMessage(userA, userB));
        }

        @Test
        @DisplayName("should return false for inactive match")
        void falseForInactiveMatch() {
            Match match = Match.create(userA, userB);
            match.unmatch(userA);
            matchStorage.save(match);

            assertFalse(messagingService.canMessage(userA, userB));
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("should update read timestamp")
        void updateReadTimestamp() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            // Send message from A to B
            messagingService.sendMessage(userA, userB, "Hello!");
            String conversationId = Conversation.generateId(userA, userB);

            // Mark as read for userB
            messagingService.markAsRead(userB, conversationId);

            // Verify timestamp was updated
            Conversation convo =
                    messagingStorage.getConversation(conversationId).orElseThrow();
            assertNotNull(convo.getLastReadAt(userB));
        }

        @Test
        @DisplayName("should ignore non-participant")
        void ignoreNonParticipant() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "Hello!");
            String conversationId = Conversation.generateId(userA, userB);

            // userC is not part of the conversation - should not throw
            messagingService.markAsRead(userC, conversationId);

            // Verify conversation still exists and is unchanged
            assertTrue(messagingStorage.getConversation(conversationId).isPresent());
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCount {

        @Test
        @DisplayName("should return correct count for unread messages")
        void returnCorrectCount() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            // Send 3 messages from A to B
            messagingService.sendMessage(userA, userB, "Message 1");
            messagingService.sendMessage(userA, userB, "Message 2");
            messagingService.sendMessage(userA, userB, "Message 3");

            String conversationId = Conversation.generateId(userA, userB);
            int unreadForB = messagingService.getUnreadCount(userB, conversationId);

            assertEquals(3, unreadForB);
        }

        @Test
        @DisplayName("should return 0 after marking as read")
        void returnZeroAfterRead() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "Hello!");
            String conversationId = Conversation.generateId(userA, userB);

            // Mark as read
            messagingService.markAsRead(userB, conversationId);

            // Count should be 0 now (no messages after the read timestamp)
            int unreadForB = messagingService.getUnreadCount(userB, conversationId);
            assertEquals(0, unreadForB);
        }

        @Test
        @DisplayName("should return 0 for empty conversation")
        void returnZeroForEmpty() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            // Create conversation but don't send any messages
            Conversation convo = messagingService.getOrCreateConversation(userA, userB);

            int unread = messagingService.getUnreadCount(userB, convo.getId());
            assertEquals(0, unread);
        }

        @Test
        @DisplayName("should not count own messages as unread")
        void notCountOwnMessages() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            // A sends 2 messages, B sends 1 message
            messagingService.sendMessage(userA, userB, "From A - 1");
            messagingService.sendMessage(userB, userA, "From B");
            messagingService.sendMessage(userA, userB, "From A - 2");

            String conversationId = Conversation.generateId(userA, userB);

            // B should see 2 unread (from A), A should see 1 unread (from B)
            assertEquals(2, messagingService.getUnreadCount(userB, conversationId));
            assertEquals(1, messagingService.getUnreadCount(userA, conversationId));
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("getTotalUnreadCount")
    class GetTotalUnreadCount {

        @Test
        @DisplayName("should aggregate across conversations")
        void aggregateAcrossConversations() {
            // Create matches: A-B and A-C
            Match matchAB = Match.create(userA, userB);
            Match matchAC = Match.create(userA, userC);
            matchStorage.save(matchAB);
            matchStorage.save(matchAC);

            // B sends 2 messages to A
            messagingService.sendMessage(userB, userA, "From B - 1");
            messagingService.sendMessage(userB, userA, "From B - 2");

            // C sends 3 messages to A
            messagingService.sendMessage(userC, userA, "From C - 1");
            messagingService.sendMessage(userC, userA, "From C - 2");
            messagingService.sendMessage(userC, userA, "From C - 3");

            int totalUnread = messagingService.getTotalUnreadCount(userA);
            assertEquals(5, totalUnread);
        }

        @Test
        @DisplayName("should return 0 for user with no conversations")
        void returnZeroForNoConversations() {
            int totalUnread = messagingService.getTotalUnreadCount(userA);
            assertEquals(0, totalUnread);
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("getConversations")
    class GetConversations {

        @Test
        @DisplayName("should return sorted by most recent")
        void returnSortedByMostRecent() {
            Match matchAB = Match.create(userA, userB);
            Match matchAC = Match.create(userA, userC);
            matchStorage.save(matchAB);
            matchStorage.save(matchAC);

            // Send older message to B, newer to C
            messagingService.sendMessage(userA, userB, "Older");
            messagingService.sendMessage(userA, userC, "Newer");

            String convoIdAB = Conversation.generateId(userA, userB);
            String convoIdAC = Conversation.generateId(userA, userC);
            Instant now = AppClock.now();
            messagingStorage.updateConversationLastMessageAt(convoIdAB, now);
            messagingStorage.updateConversationLastMessageAt(convoIdAC, now.plusSeconds(1));

            List<ConnectionService.ConversationPreview> previews = messagingService.getConversations(userA);

            assertEquals(2, previews.size());
            // C conversation should be first (more recent)
            assertEquals(userC, previews.get(0).otherUser().getId());
            assertEquals(userB, previews.get(1).otherUser().getId());
        }

        @Test
        @DisplayName("should include unread counts")
        void includeUnreadCounts() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            // B sends 3 messages to A
            messagingService.sendMessage(userB, userA, "1");
            messagingService.sendMessage(userB, userA, "2");
            messagingService.sendMessage(userB, userA, "3");

            List<ConnectionService.ConversationPreview> previews = messagingService.getConversations(userA);

            assertEquals(1, previews.size());
            assertEquals(3, previews.get(0).unreadCount());
        }

        @Test
        @DisplayName("should include last message")
        void includeLastMessage() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "First");
            String conversationId = Conversation.generateId(userA, userB);
            Instant later = AppClock.now().plusSeconds(1);
            Message lastMessage = new Message(UUID.randomUUID(), conversationId, userB, "Last message", later);
            messagingStorage.saveMessage(lastMessage);
            messagingStorage.updateConversationLastMessageAt(conversationId, later);

            List<ConnectionService.ConversationPreview> previews = messagingService.getConversations(userA);

            assertEquals(1, previews.size());
            assertTrue(previews.get(0).lastMessage().isPresent());
            assertEquals("Last message", previews.get(0).lastMessage().get().content());
        }

        @Test
        @DisplayName("should return empty for user with no conversations")
        void returnEmptyForNoConversations() {
            List<ConnectionService.ConversationPreview> previews = messagingService.getConversations(userA);

            assertTrue(previews.isEmpty());
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("getOrCreateConversation")
    class GetOrCreateConversation {

        @Test
        @DisplayName("should create if not exists")
        void createIfNotExists() {
            String conversationId = Conversation.generateId(userA, userB);
            assertTrue(messagingStorage.getConversation(conversationId).isEmpty());

            Conversation convo = messagingService.getOrCreateConversation(userA, userB);

            assertNotNull(convo);
            assertTrue(messagingStorage.getConversation(conversationId).isPresent());
        }

        @Test
        @DisplayName("should return existing if exists")
        void returnExistingIfExists() {
            // Create conversation first via sending message
            Match match = Match.create(userA, userB);
            matchStorage.save(match);
            messagingService.sendMessage(userA, userB, "Hello!");

            String conversationId = Conversation.generateId(userA, userB);
            Conversation existing =
                    messagingStorage.getConversation(conversationId).orElseThrow();

            // Get or create should return the same one
            Conversation returned = messagingService.getOrCreateConversation(userA, userB);

            assertEquals(existing.getId(), returned.getId());
            assertEquals(existing.getCreatedAt(), returned.getCreatedAt());
        }
    }

    // ===== In-Memory Test Implementations =====

    static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();
        private final Map<String, ProfileNote> profileNotes = new java.util.concurrent.ConcurrentHashMap<>();

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
        }

        @Override
        public void save(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == UserState.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(users.values());
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(noteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(noteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(noteKey(authorId, subjectId)) != null;
        }
    }
}
