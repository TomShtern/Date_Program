package datingapp.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessagingService Tests")
class MessagingServiceTest {

    private InMemoryConversationStorage conversationStorage;
    private InMemoryMessageStorage messageStorage;
    private InMemoryMatchStorage matchStorage;
    private InMemoryUserStorage userStorage;
    private MessagingService messagingService;

    private UUID userA;
    private UUID userB;
    private UUID userC;

    @BeforeEach
    void setUp() {
        conversationStorage = new InMemoryConversationStorage();
        messageStorage = new InMemoryMessageStorage();
        matchStorage = new InMemoryMatchStorage();
        userStorage = new InMemoryUserStorage();
        messagingService = new MessagingService(conversationStorage, messageStorage, matchStorage, userStorage);

        // Create test users
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();

        userStorage.save(createActiveUser(userA, "Alice"));
        userStorage.save(createActiveUser(userB, "Bob"));
        userStorage.save(createActiveUser(userC, "Carol"));
    }

    private User createActiveUser(UUID id, String name) {
        // Use fromDatabase factory to create an ACTIVE user
        return User.fromDatabase(
                id,
                name,
                "Test bio",
                null, // birthDate
                null, // gender
                null, // interestedIn
                0.0, // lat
                0.0, // lon
                50, // maxDistanceKm
                18, // minAge
                99, // maxAge
                List.of(), // photoUrls
                User.State.ACTIVE, // state
                Instant.now(), // createdAt
                Instant.now(), // updatedAt
                null, // interests
                null, // email
                null, // phone
                null, // isVerified
                null, // verificationMethod
                null, // verificationCode
                null, // verificationSentAt
                null); // verifiedAt
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("should succeed with active match")
        void succeedWithActiveMatch() {
            // Create active match
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, "Hello Bob!");

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
            assertTrue(conversationStorage.get(conversationId).isEmpty());

            messagingService.sendMessage(userA, userB, "First message!");

            assertTrue(conversationStorage.get(conversationId).isPresent());
        }

        @Test
        @DisplayName("should fail without match")
        void failWithoutMatch() {
            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with unmatched state")
        void failWithUnmatchedState() {
            Match match = Match.create(userA, userB);
            match.unmatch(userA);
            matchStorage.save(match);

            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with blocked state")
        void failWithBlockedState() {
            Match match = Match.create(userA, userB);
            match.block(userA);
            matchStorage.save(match);

            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.NO_ACTIVE_MATCH, result.errorCode());
        }

        @Test
        @DisplayName("should fail with empty content")
        void failWithEmptyContent() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, "   ");

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.EMPTY_MESSAGE, result.errorCode());
        }

        @Test
        @DisplayName("should fail with too long content")
        void failWithTooLongContent() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            String longContent = "a".repeat(Message.MAX_LENGTH + 1);
            MessagingService.SendResult result = messagingService.sendMessage(userA, userB, longContent);

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.MESSAGE_TOO_LONG, result.errorCode());
        }

        @Test
        @DisplayName("should fail if sender not found")
        void failIfSenderNotFound() {
            UUID unknownUser = UUID.randomUUID();
            Match match = Match.create(unknownUser, userB);
            matchStorage.save(match);

            MessagingService.SendResult result = messagingService.sendMessage(unknownUser, userB, "Hello!");

            assertFalse(result.success());
            assertEquals(MessagingService.SendResult.ErrorCode.USER_NOT_FOUND, result.errorCode());
        }

        @Test
        @DisplayName("should update lastMessageAt on conversation")
        void updateLastMessageAt() {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "Hello!");

            String conversationId = Conversation.generateId(userA, userB);
            Conversation convo = conversationStorage.get(conversationId).orElseThrow();
            assertNotNull(convo.getLastMessageAt());
        }
    }

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("should return messages in order")
        void returnMessagesInOrder() throws InterruptedException {
            Match match = Match.create(userA, userB);
            matchStorage.save(match);

            messagingService.sendMessage(userA, userB, "First");
            Thread.sleep(10); // Ensure different timestamps
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
    }

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

    // ===== In-Memory Test Implementations =====

    static class InMemoryConversationStorage implements ConversationStorage {
        private final Map<String, Conversation> conversations = new HashMap<>();

        @Override
        public void save(Conversation conversation) {
            conversations.put(conversation.getId(), conversation);
        }

        @Override
        public Optional<Conversation> get(String conversationId) {
            return Optional.ofNullable(conversations.get(conversationId));
        }

        @Override
        public Optional<Conversation> getByUsers(UUID userA, UUID userB) {
            return get(Conversation.generateId(userA, userB));
        }

        @Override
        public List<Conversation> getConversationsFor(UUID userId) {
            return conversations.values().stream()
                    .filter(c -> c.involves(userId))
                    .sorted(
                            (a, b) -> {
                                Instant aTime = a.getLastMessageAt() != null ? a.getLastMessageAt() : a.getCreatedAt();
                                Instant bTime = b.getLastMessageAt() != null ? b.getLastMessageAt() : b.getCreatedAt();
                                return bTime.compareTo(aTime);
                            })
                    .toList();
        }

        @Override
        public void updateLastMessageAt(String conversationId, Instant timestamp) {
            Conversation convo = conversations.get(conversationId);
            if (convo != null) {
                convo.updateLastMessageAt(timestamp);
            }
        }

        @Override
        public void updateReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
            Conversation convo = conversations.get(conversationId);
            if (convo != null) {
                convo.updateReadTimestamp(userId, timestamp);
            }
        }

        @Override
        public void delete(String conversationId) {
            conversations.remove(conversationId);
        }
    }

    static class InMemoryMessageStorage implements MessageStorage {
        private final Map<String, List<Message>> messagesByConvo = new HashMap<>();

        @Override
        public void save(Message message) {
            messagesByConvo
                    .computeIfAbsent(message.conversationId(), k -> new java.util.ArrayList<>())
                    .add(message);
        }

        @Override
        public List<Message> getMessages(String conversationId, int limit, int offset) {
            List<Message> msgs = messagesByConvo.getOrDefault(conversationId, List.of());
            return msgs.stream()
                    .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Message> getLatestMessage(String conversationId) {
            List<Message> msgs = messagesByConvo.getOrDefault(conversationId, List.of());
            return msgs.stream().max((a, b) -> a.createdAt().compareTo(b.createdAt()));
        }

        @Override
        public int countMessages(String conversationId) {
            return messagesByConvo.getOrDefault(conversationId, List.of()).size();
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            return (int) messagesByConvo.getOrDefault(conversationId, List.of()).stream()
                    .filter(m -> m.createdAt().isAfter(after))
                    .count();
        }

        @Override
        public void deleteByConversation(String conversationId) {
            messagesByConvo.remove(conversationId);
        }
    }

    static class InMemoryMatchStorage implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

        @Override
        public void save(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public void update(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Match> get(String matchId) {
            return Optional.ofNullable(matches.get(matchId));
        }

        @Override
        public boolean exists(String matchId) {
            return matches.containsKey(matchId);
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.remove(matchId);
        }
    }

    static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();

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
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findAll() {
            return List.copyOf(users.values());
        }
    }
}
