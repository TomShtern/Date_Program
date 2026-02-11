package datingapp.app.cli;

import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.AppClock;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.model.Match;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.Messaging.Message;
import datingapp.core.model.User;
import datingapp.core.service.MessagingService;
import datingapp.core.service.MessagingService.ConversationPreview;
import datingapp.core.service.MessagingService.SendResult;
import datingapp.core.service.TrustSafetyService;
import datingapp.core.storage.InteractionStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for messaging functionality in the CLI. */
public class MessagingHandler implements LoggingSupport {

    private static final Logger logger = LoggerFactory.getLogger(MessagingHandler.class);
    private static final int MESSAGES_PER_PAGE = 20;
    private static final int PREVIEW_MAX_LENGTH = 28;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private final MessagingService messagingService;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyService trustSafetyService;
    private final InputReader input;
    private final AppSession session;

    public MessagingHandler(
            MessagingService messagingService,
            InteractionStorage interactionStorage,
            TrustSafetyService trustSafetyService,
            InputReader input,
            AppSession session) {
        this.messagingService = Objects.requireNonNull(messagingService, "messagingService cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** Shows the conversation list and handles navigation. */
    public void showConversations() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logInfo(CliSupport.PLEASE_SELECT_USER);
            return;
        }

        List<ConversationPreview> previews = messagingService.getConversations(currentUser.getId());

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
        logInfo("\n{}", CliSupport.SEPARATOR_LINE);
        logInfo("       💬 YOUR CONVERSATIONS");
        logInfo("{}", CliSupport.SEPARATOR_LINE);
    }

    /** Prints a message when there are no conversations. */
    private void printEmptyConversations() {
        logInfo("\n  No conversations yet.");
        logInfo("  Start by matching with someone and send a message!\n");
        logInfo("{}", CliSupport.MENU_DIVIDER);
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

        logInfo("{}", CliSupport.MENU_DIVIDER);
        logInfo("[#] Select conversation  [B] Back");
    }

    /**
     * Handles the user's selection of a conversation from the list.
     *
     * @param choice The user's input choice
     * @param previews The current list of conversation previews
     * @param currentUser The current user
     * @return Updated list of conversation previews
     */
    private List<ConversationPreview> handleConversationSelection(
            String choice, List<ConversationPreview> previews, User currentUser) {
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < previews.size()) {
                showConversation(currentUser, previews.get(idx));
                return messagingService.getConversations(currentUser.getId());
            } else {
                logInfo(CliSupport.INVALID_SELECTION);
            }
        } catch (NumberFormatException _) {
            logInfo(CliSupport.INVALID_INPUT);
        }
        return previews;
    }

    /** Displays a single conversation preview in the list. */
    private void displayConversationPreview(int index, ConversationPreview preview) {
        String name = preview.otherUser().getName();
        String unreadStr = preview.unreadCount() > 0 ? " (" + preview.unreadCount() + " new)" : "";
        String timeAgo = formatTimeAgo(preview.conversation().getLastMessageAt());

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

        messagingService.markAsRead(currentUser.getId(), conversation.getId());

        int offset = 0;
        boolean showOlder = false;

        while (true) {
            List<Message> messages =
                    messagingService.getMessages(currentUser.getId(), otherUser.getId(), MESSAGES_PER_PAGE + offset, 0);

            String matchId = Match.generateId(currentUser.getId(), otherUser.getId());
            Optional<Match> matchOpt = interactionStorage.get(matchId);
            boolean canMessage = matchOpt.isPresent() && matchOpt.get().isActive();

            printConversationHeader(otherUser.getName(), canMessage);
            printMessages(messages, currentUser, showOlder);
            printConversationFooter(canMessage, messages.size() >= MESSAGES_PER_PAGE && offset == 0);

            String userInput = input.readLine("> ").trim();

            if (userInput.isEmpty()) {
                continue;
            }

            ConversationAction action = processConversationInput(userInput, canMessage, currentUser, otherUser);
            switch (action) {
                case EXIT -> {
                    return;
                }
                case SHOW_OLDER -> {
                    offset += MESSAGES_PER_PAGE;
                    showOlder = true;
                }
                case REFRESH -> {
                    offset = 0;
                    showOlder = false;
                }
                case CONTINUE -> {
                    /* No action needed, continue conversation loop */
                }
                default -> {
                    /* Unknown action - do nothing */
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
     * @param canMessage Whether the user can send messages in this conversation
     */
    private void printConversationHeader(String otherUserName, boolean canMessage) {
        logInfo("\n{}", CliSupport.SEPARATOR_LINE);
        logInfo("       💬 Conversation with {}", otherUserName);
        if (!canMessage) {
            logInfo("       ⚠️  Match ended - read only");
        }
        logInfo("{}", CliSupport.SEPARATOR_LINE);
    }

    /**
     * Prints the messages in a conversation.
     *
     * @param messages The list of messages to print
     * @param currentUser The current user
     * @param showOlder Whether to show older messages
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
     * @param canMessage Whether the user can send messages
     * @param hasMoreMessages Whether there are more messages to show
     */
    private void printConversationFooter(boolean canMessage, boolean hasMoreMessages) {
        if (hasMoreMessages) {
            logInfo("\n  [Type /older to see older messages]");
        }

        logInfo("{}", CliSupport.MENU_DIVIDER);
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
     * @param userInput The user's input
     * @param canMessage Whether the user can send messages
     * @param currentUser The current user
     * @param otherUser The other user in the conversation
     * @return The action to take based on the input
     */
    private ConversationAction processConversationInput(
            String userInput, boolean canMessage, User currentUser, User otherUser) {

        if ("/back".equalsIgnoreCase(userInput) || "b".equalsIgnoreCase(userInput)) {
            return ConversationAction.EXIT;
        }

        if ("/older".equalsIgnoreCase(userInput)) {
            return ConversationAction.SHOW_OLDER;
        }

        if (!canMessage) {
            logInfo("\n⚠️  Cannot send messages - match has ended.");
            return ConversationAction.CONTINUE;
        }

        if ("/block".equalsIgnoreCase(userInput)) {
            if (confirmAction("Block " + otherUser.getName())) {
                blockUser(currentUser, otherUser);
                return ConversationAction.EXIT;
            }
            return ConversationAction.CONTINUE;
        }

        if ("/unmatch".equalsIgnoreCase(userInput)) {
            if (confirmAction("Unmatch from " + otherUser.getName())) {
                unmatchUser(currentUser, otherUser);
                return ConversationAction.EXIT;
            }
            return ConversationAction.CONTINUE;
        }

        // Send message
        SendResult result = messagingService.sendMessage(currentUser.getId(), otherUser.getId(), userInput);

        if (result.success()) {
            logInfo("\n✓ Message sent");
            return ConversationAction.REFRESH;
        } else {
            logInfo("\n❌ {}", result.errorMessage());
            return ConversationAction.CONTINUE;
        }
    }

    /** Displays a single message. */
    private void displayMessage(Message message, User currentUser) {
        boolean isFromMe = message.senderId().equals(currentUser.getId());
        String sender = isFromMe ? "You" : "Them";
        String timestamp = TIME_FORMATTER.format(message.createdAt());

        logInfo("\n   [{}] {}:", timestamp, sender);
        logInfo("   {}", message.content());
    }

    /** Formats a timestamp as relative time (e.g., "5m ago"). */
    private String formatTimeAgo(Instant timestamp) {
        if (timestamp == null) {
            return "never";
        }

        Duration duration = Duration.between(timestamp, AppClock.now());

        if (duration.toMinutes() < 60) {
            return duration.toMinutes() + "m ago";
        } else if (duration.toHours() < 24) {
            return duration.toHours() + "h ago";
        } else if (duration.toDays() < 7) {
            return duration.toDays() + "d ago";
        } else {
            return DateTimeFormatter.ofPattern("MMM d")
                    .withZone(ZoneId.systemDefault())
                    .format(timestamp);
        }
    }

    /** Truncates a message for preview. */
    private String truncateMessage(String message, int maxLen) {
        if (message == null) {
            return "";
        }
        message = message.replace('\n', ' ').replace('\r', ' ');
        if (message.length() <= maxLen) {
            return message;
        }
        return message.substring(0, maxLen - 3) + "...";
    }

    /** Asks for confirmation of an action. */
    private boolean confirmAction(String action) {
        String response = input.readLine("\n" + action + "? (y/n): ").trim().toLowerCase(Locale.ROOT);
        return "y".equals(response) || "yes".equals(response);
    }

    /** Blocks another user. */
    private void blockUser(User currentUser, User otherUser) {
        TrustSafetyService.BlockResult result = trustSafetyService.block(currentUser.getId(), otherUser.getId());
        if (result.success()) {
            logInfo("\n✓ {} has been blocked.", otherUser.getName());
        } else {
            logInfo("\n❌ {}", result.errorMessage());
        }
    }

    /** Unmatches from another user. */
    private void unmatchUser(User currentUser, User otherUser) {
        String matchId = Match.generateId(currentUser.getId(), otherUser.getId());
        Optional<Match> matchOpt = interactionStorage.get(matchId);

        if (matchOpt.isPresent()) {
            Match match = matchOpt.get();
            if (match.isActive()) {
                match.unmatch(currentUser.getId());
                interactionStorage.update(match);
                logInfo("\n✓ Unmatched from {}.", otherUser.getName());
            } else {
                logInfo("\n⚠️  Match is already ended.");
            }
        } else {
            logInfo("\n⚠️  No match found.");
        }
    }

    /**
     * Returns the total unread count for menu display.
     *
     * @return The number of unread messages across all conversations
     */
    public int getTotalUnreadCount() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            return 0;
        }
        return messagingService.getTotalUnreadCount(currentUser.getId());
    }
}
