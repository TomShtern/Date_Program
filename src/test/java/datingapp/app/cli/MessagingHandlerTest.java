package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for MessagingHandler CLI commands.
 * Uses a real H2 database for ServiceRegistry integration.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class MessagingHandlerTest {

    private AppSession session;
    private DatabaseManager dbManager;
    private ServiceRegistry registry;
    private User testUser;
    private UserStorage userStorage;
    private InteractionStorage interactionStorage;
    private CommunicationStorage communicationStorage;

    @BeforeAll
    static void setUpDatabase() {
        System.setProperty("datingapp.db.password", "test");
        System.setProperty("datingapp.db.profile", "test");
    }

    @AfterAll
    static void tearDown() {
        DatabaseManager.resetInstance();
    }

    @BeforeEach
    void setUp() {
        session = AppSession.getInstance();
        session.reset();

        System.setProperty("datingapp.db.password", "test");
        System.setProperty("datingapp.db.profile", "test");
        DatabaseManager.resetInstance();
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:messagingtest_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();
        registry = StorageFactory.buildH2(dbManager, AppConfig.defaults());

        userStorage = registry.getUserStorage();
        interactionStorage = registry.getInteractionStorage();
        communicationStorage = registry.getCommunicationStorage();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    @AfterEach
    void tearDownEach() {
        session.reset();
        DatabaseManager.resetInstance();
        System.clearProperty("datingapp.db.password");
        System.clearProperty("datingapp.db.profile");
    }

    private MessagingHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new MessagingHandler(
                registry.getMessagingUseCases(), registry.getSocialUseCases(), inputReader, session);
    }

    private MessagingHandler createHandler(String input, MessagingUseCases messagingUseCases) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new MessagingHandler(messagingUseCases, registry.getSocialUseCases(), inputReader, session);
    }

    private MessagingUseCases createMessagingUseCases(
            List<ConversationPreview> previews,
            boolean failListConversations,
            boolean failMarkRead,
            boolean failLoadConversation) {
        ConnectionService connectionService = registry.getConnectionService();
        return new MessagingUseCases(connectionService, registry.getEventBus()) {
            @Override
            public UseCaseResult<ConversationListResult> listConversations(ListConversationsQuery query) {
                if (failListConversations) {
                    return UseCaseResult.failure(UseCaseError.internal("boom"));
                }
                int limit = query.limit() > 0 ? query.limit() : 50;
                int offset = Math.max(0, query.offset());
                if (offset >= previews.size()) {
                    return UseCaseResult.success(new ConversationListResult(List.of(), 0));
                }
                int end = Math.min(previews.size(), offset + limit);
                List<ConversationPreview> page = previews.subList(offset, end);
                int totalUnread =
                        page.stream().mapToInt(ConversationPreview::unreadCount).sum();
                return UseCaseResult.success(new ConversationListResult(page, totalUnread));
            }

            @Override
            public UseCaseResult<Void> markConversationRead(MarkConversationReadCommand command) {
                if (failMarkRead) {
                    return UseCaseResult.failure(UseCaseError.internal("mark-read failed"));
                }
                return UseCaseResult.success(null);
            }

            @Override
            public UseCaseResult<ConversationThread> loadConversation(LoadConversationQuery query) {
                if (failLoadConversation) {
                    return UseCaseResult.failure(UseCaseError.internal("load-conversation failed"));
                }
                String conversationId = Conversation.generateId(query.context().userId(), query.otherUserId());
                return UseCaseResult.success(new ConversationThread(List.of(), true, conversationId));
            }
        };
    }

    private static List<ConversationPreview> createConversationPreviews(UUID currentUserId, int count) {
        List<ConversationPreview> previews = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User otherUser = new User(UUID.randomUUID(), String.format("User%02d", i));
            Conversation conversation = Conversation.create(currentUserId, otherUser.getId());
            previews.add(new ConversationPreview(conversation, otherUser, Optional.<Message>empty(), 0));
        }
        return previews;
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Show Conversations")
    class ShowConversations {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            MessagingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Shows empty message when no conversations")
        void showsEmptyMessageWhenNoConversations() {
            MessagingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Lists conversations with active match")
        void listsConversationsWithActiveMatch() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            MessagingHandler handler = createHandler("1\n/back\nb\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Handles back selection")
        void handlesBackSelection() {
            MessagingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Shows load failure instead of empty inbox when listing conversations fails")
        void showsLoadFailureInsteadOfEmptyInboxWhenListingConversationsFails() {
            Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(MessagingHandler.class);
            Level previousLevel = handlerLogger.getLevel();
            handlerLogger.setLevel(Level.INFO);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            handlerLogger.addAppender(appender);

            try {
                MessagingHandler handler = createHandler("b\n", createMessagingUseCases(List.of(), true, false, false));

                handler.showConversations();

                List<String> messages = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .toList();
                assertTrue(messages.stream().anyMatch(message -> message.contains("Failed to load conversations")));
                assertFalse(messages.stream().anyMatch(message -> message.contains("No conversations yet.")));
            } finally {
                handlerLogger.detachAppender(appender);
                handlerLogger.setLevel(previousLevel);
                appender.stop();
            }
        }

        @Test
        @DisplayName("Navigates to a second conversations page")
        void navigatesToASecondConversationsPage() {
            List<ConversationPreview> previews = createConversationPreviews(testUser.getId(), 51);
            MessagingHandler handler = createHandler("n\nb\n", createMessagingUseCases(previews, false, false, false));

            Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(MessagingHandler.class);
            Level previousLevel = handlerLogger.getLevel();
            handlerLogger.setLevel(Level.INFO);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            handlerLogger.addAppender(appender);

            try {
                handler.showConversations();

                assertTrue(appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.contains("User51")));
            } finally {
                handlerLogger.detachAppender(appender);
                handlerLogger.setLevel(previousLevel);
                appender.stop();
            }
        }

        @Test
        @DisplayName("Shows help without sending a message")
        void showsHelpWithoutSendingMessage() {
            User otherUser = createActiveUser("HelpUser" + UUID.randomUUID());
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            MessagingHandler handler = createHandler("1\n/help\n/back\nb\n");
            Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(MessagingHandler.class);
            Level previousLevel = handlerLogger.getLevel();
            handlerLogger.setLevel(Level.INFO);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            handlerLogger.addAppender(appender);

            try {
                handler.showConversations();

                assertEquals(0, communicationStorage.countMessages(convo.getId()));
                assertTrue(appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.contains("Type a message, or /help for commands.")));
                assertTrue(appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.contains("Available commands")));
            } finally {
                handlerLogger.detachAppender(appender);
                handlerLogger.setLevel(previousLevel);
                appender.stop();
            }
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Conversation")
    class ViewConversation {

        @Test
        @DisplayName("Shows no messages for empty conversation")
        void showsNoMessagesForEmptyConversation() {
            User otherUser = createActiveUser("OtherUser" + UUID.randomUUID());
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            MessagingHandler handler = createHandler("1\n/back\nb\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Exits conversation on EOF without hanging")
        void exitsConversationOnEOFWithoutHanging() {
            User otherUser = createActiveUser("EOFTestUser" + UUID.randomUUID());
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            // True EOF after selecting the conversation should exit cleanly.
            MessagingHandler handler = createHandler("1\n");

            assertDoesNotThrow(handler::showConversations);
        }

        @Test
        @DisplayName("Shows messages in conversation")
        void showsMessagesInConversation() {
            User otherUser = createActiveUser("OtherMsgUser" + UUID.randomUUID());
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(MessagingHandler.class);
            Level previousLevel = handlerLogger.getLevel();
            handlerLogger.setLevel(Level.INFO);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            handlerLogger.addAppender(appender);

            try {
                MessagingHandler handler = createHandler("1\nHello from the handler\nb\nb\n");

                assertDoesNotThrow(handler::showConversations);
                assertEquals(1, communicationStorage.countMessages(convo.getId()));

                List<String> messages = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .toList();
                assertTrue(messages.stream().anyMatch(message -> message.contains("Hello from the handler")));
                assertTrue(messages.stream().anyMatch(message -> message.contains("✓ Message sent")));
            } finally {
                handlerLogger.detachAppender(appender);
                handlerLogger.setLevel(previousLevel);
                appender.stop();
            }
        }

        @Test
        @DisplayName("Surfaces markConversationRead failures")
        void surfacesMarkConversationReadFailures() {
            User otherUser = createActiveUser("MarkReadUser" + UUID.randomUUID());
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            Conversation convo = Conversation.create(testUser.getId(), otherUser.getId());
            communicationStorage.saveConversation(convo);

            MessagingHandler handler = createHandler(
                    "1\nb\nb\n",
                    createMessagingUseCases(
                            List.of(new ConversationPreview(convo, otherUser, Optional.<Message>empty(), 0)),
                            false,
                            true,
                            false));

            Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(MessagingHandler.class);
            Level previousLevel = handlerLogger.getLevel();
            handlerLogger.setLevel(Level.INFO);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            handlerLogger.addAppender(appender);

            try {
                handler.showConversations();

                assertTrue(appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.contains("Failed to mark conversation as read")));
            } finally {
                handlerLogger.detachAppender(appender);
                handlerLogger.setLevel(previousLevel);
                appender.stop();
            }
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Unread Count")
    class UnreadCount {

        @Test
        @DisplayName("Returns zero when not logged in")
        void returnsZeroWhenNotLoggedIn() {
            session.logout();
            MessagingHandler handler = createHandler("");

            assertEquals(0, handler.getTotalUnreadCount());
        }

        @Test
        @DisplayName("Returns zero when no messages")
        void returnsZeroWhenNoMessages() {
            MessagingHandler handler = createHandler("");

            assertEquals(0, handler.getTotalUnreadCount());
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setLocation(32.0853, 34.7818);
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }
}
