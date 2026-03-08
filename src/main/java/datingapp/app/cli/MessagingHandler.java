package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.LoadConversationQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.MarkConversationReadCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.TextUtil;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.User;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for messaging functionality in the CLI. */
public class MessagingHandler implements LoggingSupport {

    private static final Logger logger = LoggerFactory.getLogger(MessagingHandler.class);
    private static final String ERROR_TEMPLATE = "\n❌ {}";
    private static final int MESSAGES_PER_PAGE = 20;
    private static final int PREVIEW_MAX_LENGTH = 28;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private final MessagingUseCases messagingUseCases;
    private final SocialUseCases socialUseCases;
    private final InputReader input;
    private final AppSession session;

    public MessagingHandler(
            MessagingUseCases messagingUseCases, SocialUseCases socialUseCases, InputReader input, AppSession session) {
        this.messagingUseCases = Objects.requireNonNull(messagingUseCases, "messagingUseCases cannot be null");
        this.socialUseCases = Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public static MessagingHandler fromServices(ServiceRegistry services, AppSession session, InputReader input) {
        Objects.requireNonNull(services, "services cannot be null");
        return new MessagingHandler(services.getMessagingUseCases(), services.getSocialUseCases(), input, session);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** Shows the conversation list and handles navigation. */
    public void showConversations() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logInfo(CliTextAndInput.PLEASE_SELECT_USER);
            return;
        }

        List<ConversationPreview> previews = loadConversationPreviews(currentUser);

        while (true) {
            printConversationListHeader();

            if (previews.isEmpty()) {
                printEmptyConversations();
                return;
            }

            displayConversationPreviews(previews);
            String choice = input.readLine("> ").trim();

            if ("b".equalsIgnoreCase(choice) || choice.isEmpty()) {
                return;
            }

            previews = handleConversationSelection(choice, previews, currentUser);
        }
    }

    /** Prints the header for the conversation list. */
    private void printConversationListHeader() {
        logInfo("\n{}", CliTextAndInput.SEPARATOR_LINE);
        logInfo("       💬 YOUR CONVERSATIONS");
        logInfo("{}", CliTextAndInput.SEPARATOR_LINE);
    }

    /** Prints a message when there are no conversations. */
    private void printEmptyConversations() {
        logInfo("\n  No conversations yet.");
        logInfo("  Start by matching with someone and send a message!\n");
        logInfo("{}", CliTextAndInput.MENU_DIVIDER);
        logInfo("[B] Back");
        input.readLine("> ");
    }

    /**
     * Displays the list of conversation previews.
     *
     * @param previews The list of conversation previews to display
     */
    private void displayConversationPreviews(List<ConversationPreview> previews) {
        for (int i = 0; i < previews.size(); i++) {
            displayConversationPreview(i + 1, previews.get(i));
        }

        int totalUnread =
                previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
        if (totalUnread > 0) {
            logInfo("\nTotal unread: {} message(s)", totalUnread);
        }

        logInfo("{}", CliTextAndInput.MENU_DIVIDER);
        logInfo("[#] Select conversation  [B] Back");
    }

    /**
     * Handles the user's selection of a conversation from the list.
     *
     * @param choice      The user's input choice
     * @param previews    The current list of conversation previews
     * @param currentUser The current user
     * @return Updated list of conversation previews
     */
    private List<ConversationPreview> handleConversationSelection(
            String choice, List<ConversationPreview> previews, User currentUser) {
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < previews.size()) {
                showConversation(currentUser, previews.get(idx));
                return loadConversationPreviews(currentUser);
            } else {
                logInfo(CliTextAndInput.INVALID_SELECTION);
            }
        } catch (NumberFormatException _) {
            logInfo(CliTextAndInput.INVALID_INPUT);
        }
        return previews;
    }

    /** Displays a single conversation preview in the list. */
    private void displayConversationPreview(int index, ConversationPreview preview) {
        String name = preview.otherUser().getName();
        String unreadStr = preview.unreadCount() > 0 ? " (" + preview.unreadCount() + " new)" : "";
        String timeAgo = TextUtil.formatTimeAgo(preview.conversation().getLastMessageAt());

        logInfo("{}. {}{} · {}", index, name, unreadStr, timeAgo);

        preview.lastMessage().ifPresent(msg -> {
            String content = truncateMessage(msg.content(), PREVIEW_MAX_LENGTH);
            logInfo("   \"{}\"", content);
        });
        logInfo("");
    }

    /** Shows a single conversation with messages. */
    private void showConversation(User currentUser, ConversationPreview preview) {
        Conversation conversation = preview.conversation();
        User otherUser = preview.otherUser();

        messagingUseCases.markConversationRead(
                new MarkConversationReadCommand(UserContext.cli(currentUser.getId()), conversation.getId()));

        int offset = 0;
        boolean showOlder = false;

        boolean active = true;
        while (active) {
            var result = messagingUseCases.loadConversation(new LoadConversationQuery(
                    UserContext.cli(currentUser.getId()), otherUser.getId(), MESSAGES_PER_PAGE + offset, 0, false));
            if (!result.success()) {
                logInfo("\n❌ Failed to load messages: {}", result.error().message());
                active = false;
            } else {
                List<Message> messages = result.data().messages();
                boolean canMessage = result.data().canMessage();

                printConversationHeader(otherUser.getName(), canMessage);
                printMessages(messages, currentUser, showOlder);
                printConversationFooter(canMessage, messages.size() >= MESSAGES_PER_PAGE);

                String userInput = input.readLine("> ").trim();

                if (!userInput.isEmpty()) {
                    ConversationAction action = processConversationInput(userInput, canMessage, currentUser, otherUser);
                    if (action == ConversationAction.EXIT) {
                        active = false;
                    } else if (action == ConversationAction.SHOW_OLDER) {
                        offset += MESSAGES_PER_PAGE;
                        showOlder = true;
                    } else if (action == ConversationAction.REFRESH) {
                        offset = 0;
                        showOlder = false;
                    }
                }
            }
        }
    }

    private enum ConversationAction {
        EXIT,
        SHOW_OLDER,
        REFRESH,
        CONTINUE
    }

    /**
     * Prints the header for a conversation view.
     *
     * @param otherUserName The name of the other user in the conversation
     * @param canMessage    Whether the user can send messages in this conversation
     */
    private void printConversationHeader(String otherUserName, boolean canMessage) {
        logInfo("\n{}", CliTextAndInput.SEPARATOR_LINE);
        logInfo("       💬 Conversation with {}", otherUserName);
        if (!canMessage) {
            logInfo("       ⚠️  Match ended - read only");
        }
        logInfo("{}", CliTextAndInput.SEPARATOR_LINE);
    }

    /**
     * Prints the messages in a conversation.
     *
     * @param messages    The list of messages to print
     * @param currentUser The current user
     * @param showOlder   Whether to show older messages
     */
    private void printMessages(List<Message> messages, User currentUser, boolean showOlder) {
        if (messages.isEmpty()) {
            logInfo("\n  No messages yet. Start the conversation!");
        } else {
            int start = showOlder ? 0 : Math.max(0, messages.size() - MESSAGES_PER_PAGE);
            for (int i = start; i < messages.size(); i++) {
                displayMessage(messages.get(i), currentUser);
            }
        }
    }

    /**
     * Prints the footer for a conversation view.
     *
     * @param canMessage      Whether the user can send messages
     * @param hasMoreMessages Whether there are more messages to show
     */
    private void printConversationFooter(boolean canMessage, boolean hasMoreMessages) {
        if (hasMoreMessages) {
            logInfo("\n  [Type /older to see older messages]");
        }

        logInfo("{}", CliTextAndInput.MENU_DIVIDER);
        if (canMessage) {
            logInfo("Commands: /back  /older  /block  /unmatch");
            logInfo("\nType your message:");
        } else {
            logInfo("[B] Back");
        }
    }

    /**
     * Processes user input in a conversation.
     *
     * @param userInput   The user's input
     * @param canMessage  Whether the user can send messages
     * @param currentUser The current user
     * @param otherUser   The other user in the conversation
     * @return The action to take based on the input
     */
    private ConversationAction processConversationInput(
            String userInput, boolean canMessage, User currentUser, User otherUser) {
        if (isBackCommand(userInput)) {
            return ConversationAction.EXIT;
        }

        if (isOlderCommand(userInput)) {
            return ConversationAction.SHOW_OLDER;
        }

        if (!canMessage) {
            logInfo("\n⚠️  Cannot send messages - match has ended.");
            return ConversationAction.CONTINUE;
        }

        return handleSendOrRelationshipCommand(userInput, currentUser, otherUser);
    }

    private ConversationAction handleSendOrRelationshipCommand(String userInput, User currentUser, User otherUser) {
        if (isBlockCommand(userInput)) {
            return handleBlockCommand(currentUser, otherUser);
        }

        if (isUnmatchCommand(userInput)) {
            return handleUnmatchCommand(currentUser, otherUser);
        }

        return sendConversationMessage(currentUser, otherUser, userInput);
    }

    private ConversationAction handleBlockCommand(User currentUser, User otherUser) {
        if (confirmAction("Block " + otherUser.getName())) {
            blockUser(currentUser, otherUser);
            return ConversationAction.EXIT;
        }
        return ConversationAction.CONTINUE;
    }

    private ConversationAction handleUnmatchCommand(User currentUser, User otherUser) {
        if (confirmAction("Unmatch from " + otherUser.getName())) {
            unmatchUser(currentUser, otherUser);
            return ConversationAction.EXIT;
        }
        return ConversationAction.CONTINUE;
    }

    private ConversationAction sendConversationMessage(User currentUser, User otherUser, String userInput) {
        var result = messagingUseCases.sendMessage(
                new SendMessageCommand(UserContext.cli(currentUser.getId()), otherUser.getId(), userInput));

        if (result.success()) {
            logInfo("\n✓ Message sent");
            return ConversationAction.REFRESH;
        }

        logInfo(ERROR_TEMPLATE, result.error().message());
        return ConversationAction.CONTINUE;
    }

    private boolean isBackCommand(String userInput) {
        return "/back".equalsIgnoreCase(userInput) || "b".equalsIgnoreCase(userInput);
    }

    private boolean isOlderCommand(String userInput) {
        return "/older".equalsIgnoreCase(userInput);
    }

    private boolean isBlockCommand(String userInput) {
        return "/block".equalsIgnoreCase(userInput);
    }

    private boolean isUnmatchCommand(String userInput) {
        return "/unmatch".equalsIgnoreCase(userInput);
    }

    /** Displays a single message. */
    private void displayMessage(Message message, User currentUser) {
        boolean isFromMe = message.senderId().equals(currentUser.getId());
        String sender = isFromMe ? "You" : "Them";
        String timestamp = TIME_FORMATTER.format(message.createdAt());

        logInfo("\n   [{}] {}:", timestamp, sender);
        logInfo("   {}", message.content());
    }

    /** Truncates a message for preview. */
    private String truncateMessage(String message, int maxLen) {
        if (message == null) {
            return "";
        }
        String sanitizedMessage = message.replace('\n', ' ').replace('\r', ' ');
        if (sanitizedMessage.length() <= maxLen) {
            return sanitizedMessage;
        }
        return sanitizedMessage.substring(0, maxLen - 3) + "...";
    }

    /** Asks for confirmation of an action. */
    private boolean confirmAction(String action) {
        String response = input.readLine("\n" + action + "? (y/n): ").trim().toLowerCase(Locale.ROOT);
        return "y".equals(response) || "yes".equals(response);
    }

    /** Blocks another user. */
    private void blockUser(User currentUser, User otherUser) {
        var result = socialUseCases.blockUser(
                new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUser.getId()));
        if (result.success()) {
            logInfo("\n✓ {} has been blocked.", otherUser.getName());
        } else {
            logInfo(ERROR_TEMPLATE, result.error().message());
        }
    }

    /** Unmatches from another user. */
    private void unmatchUser(User currentUser, User otherUser) {
        var result = socialUseCases.unmatch(
                new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUser.getId()));
        if (result.success()) {
            logInfo("\n✓ Unmatched from {}.", otherUser.getName());
        } else {
            logInfo(ERROR_TEMPLATE, result.error().message());
        }
    }

    public int getTotalUnreadCount() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            return 0;
        }
        return getTotalUnreadCount(currentUser);
    }

    private int getTotalUnreadCount(User currentUser) {
        var result = messagingUseCases.totalUnreadCount(UserContext.cli(currentUser.getId()));
        return result.success() ? result.data() : 0;
    }

    private List<ConversationPreview> loadConversationPreviews(User currentUser) {
        var result = messagingUseCases.listConversations(
                new MessagingUseCases.ListConversationsQuery(UserContext.cli(currentUser.getId()), 50, 0));
        return result.success() ? result.data().conversations() : List.of();
    }
}
