package datingapp.cli;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datingapp.core.Conversation;
import datingapp.core.Match;
import datingapp.core.MatchStorage;
import datingapp.core.Message;
import datingapp.core.MessagingService;
import datingapp.core.MessagingService.ConversationPreview;
import datingapp.core.MessagingService.SendResult;
import datingapp.core.ServiceRegistry;
import datingapp.core.User;

/** Handler for messaging functionality in the CLI. */
public class MessagingHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessagingHandler.class);
    private static final int MESSAGES_PER_PAGE = 20;
    private static final int PREVIEW_MAX_LENGTH = 28;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault());

    private final ServiceRegistry registry;
    private final InputReader input;
    private final UserSession session;

    public MessagingHandler(ServiceRegistry registry, InputReader input, UserSession session) {
        this.registry = registry;
        this.input = input;
        this.session = session;
    }

    /** Shows the conversation list and handles navigation. */
    public void showConversations() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        MessagingService messagingService = registry.getMessagingService();
        List<ConversationPreview> previews = messagingService.getConversations(currentUser.getId());

        while (true) {
            printConversationListHeader();

            if (previews.isEmpty()) {
                printEmptyConversations();
                return;
            }

            displayConversationPreviews(previews);
            String choice = input.readLine("> ").trim();

            if (choice.equalsIgnoreCase("b") || choice.isEmpty()) {
                return;
            }

            previews = handleConversationSelection(choice, previews, currentUser, messagingService);
        }
    }

    private void printConversationListHeader() {
        logger.info("\n{}", CliConstants.SEPARATOR_LINE);
        logger.info("       💬 YOUR CONVERSATIONS");
        logger.info("{}", CliConstants.SEPARATOR_LINE);
    }

    private void printEmptyConversations() {
        logger.info("\n  No conversations yet.");
        logger.info("  Start by matching with someone and send a message!\n");
        logger.info("{}", CliConstants.MENU_DIVIDER);
        logger.info("[B] Back");
        input.readLine("> ");
    }

    private void displayConversationPreviews(List<ConversationPreview> previews) {
        for (int i = 0; i < previews.size(); i++) {
            displayConversationPreview(i + 1, previews.get(i));
        }

        int totalUnread = previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
        if (totalUnread > 0) {
            logger.info("\nTotal unread: {} message(s)", totalUnread);
        }

        logger.info("{}", CliConstants.MENU_DIVIDER);
        logger.info("[#] Select conversation  [B] Back");
    }

    private List<ConversationPreview> handleConversationSelection(
            String choice,
            List<ConversationPreview> previews,
            User currentUser,
            MessagingService messagingService) {
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < previews.size()) {
                showConversation(currentUser, previews.get(idx));
                return messagingService.getConversations(currentUser.getId());
            } else {
                logger.info(CliConstants.INVALID_SELECTION);
            }
        } catch (NumberFormatException e) {
            logger.info(CliConstants.INVALID_INPUT);
        }
        return previews;
    }

    /** Displays a single conversation preview in the list. */
    private void displayConversationPreview(int index, ConversationPreview preview) {
        String name = preview.otherUser().getName();
        String unreadStr = preview.unreadCount() > 0 ? " (" + preview.unreadCount() + " new)" : "";
        String timeAgo = formatTimeAgo(preview.conversation().getLastMessageAt());

        logger.info("{}. {}{} · {}", index, name, unreadStr, timeAgo);

        preview.lastMessage().ifPresent(msg -> {
            String content = truncateMessage(msg.content(), PREVIEW_MAX_LENGTH);
            logger.info("   \"{}\"", content);
        });
        logger.info("");
    }

    /** Shows a single conversation with messages. */
    private void showConversation(User currentUser, ConversationPreview preview) {
        MessagingService messagingService = registry.getMessagingService();
        MatchStorage matchStorage = registry.getMatchStorage();
        Conversation conversation = preview.conversation();
        User otherUser = preview.otherUser();

        messagingService.markAsRead(currentUser.getId(), conversation.getId());

        int offset = 0;
        boolean showOlder = false;

        while (true) {
            List<Message> messages = messagingService.getMessages(
                    currentUser.getId(), otherUser.getId(), MESSAGES_PER_PAGE + offset, 0);

            String matchId = Match.generateId(currentUser.getId(), otherUser.getId());
            Optional<Match> matchOpt = matchStorage.get(matchId);
            boolean canMessage = matchOpt.isPresent() && matchOpt.get().isActive();

            printConversationHeader(otherUser.getName(), canMessage);
            printMessages(messages, currentUser, showOlder);
            printConversationFooter(canMessage, messages.size() >= MESSAGES_PER_PAGE && offset == 0);

            String userInput = input.readLine("> ").trim();

            if (userInput.isEmpty()) {
                continue;
            }

            ConversationAction action = processConversationInput(userInput, canMessage, currentUser, otherUser,
                    messagingService);
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

    private void printConversationHeader(String otherUserName, boolean canMessage) {
        logger.info("\n{}", CliConstants.SEPARATOR_LINE);
        logger.info("       💬 Conversation with {}", otherUserName);
        if (!canMessage) {
            logger.info("       ⚠️  Match ended - read only");
        }
        logger.info("{}", CliConstants.SEPARATOR_LINE);
    }

    private void printMessages(List<Message> messages, User currentUser, boolean showOlder) {
        if (messages.isEmpty()) {
            logger.info("\n  No messages yet. Start the conversation!");
        } else {
            int start = showOlder ? 0 : Math.max(0, messages.size() - MESSAGES_PER_PAGE);
            for (int i = start; i < messages.size(); i++) {
                displayMessage(messages.get(i), currentUser);
            }
        }
    }

    private void printConversationFooter(boolean canMessage, boolean hasMoreMessages) {
        if (hasMoreMessages) {
            logger.info("\n  [Type /older to see older messages]");
        }

        logger.info("{}", CliConstants.MENU_DIVIDER);
        if (canMessage) {
            logger.info("Commands: /back  /older  /block  /unmatch");
            logger.info("\nType your message:");
        } else {
            logger.info("[B] Back");
        }
    }

    private ConversationAction processConversationInput(
            String userInput,
            boolean canMessage,
            User currentUser,
            User otherUser,
            MessagingService messagingService) {

        if (userInput.equalsIgnoreCase("/back") || userInput.equalsIgnoreCase("b")) {
            return ConversationAction.EXIT;
        }

        if (userInput.equalsIgnoreCase("/older")) {
            return ConversationAction.SHOW_OLDER;
        }

        if (!canMessage) {
            logger.info("\n⚠️  Cannot send messages - match has ended.");
            return ConversationAction.CONTINUE;
        }

        if (userInput.equalsIgnoreCase("/block")) {
            if (confirmAction("Block " + otherUser.getName())) {
                blockUser(currentUser, otherUser);
                return ConversationAction.EXIT;
            }
            return ConversationAction.CONTINUE;
        }

        if (userInput.equalsIgnoreCase("/unmatch")) {
            if (confirmAction("Unmatch from " + otherUser.getName())) {
                unmatchUser(currentUser, otherUser);
                return ConversationAction.EXIT;
            }
            return ConversationAction.CONTINUE;
        }

        // Send message
        SendResult result = messagingService.sendMessage(currentUser.getId(), otherUser.getId(), userInput);

        if (result.success()) {
            logger.info("\n✓ Message sent");
            return ConversationAction.REFRESH;
        } else {
            logger.info("\n❌ {}", result.errorMessage());
            return ConversationAction.CONTINUE;
        }
    }

    /** Displays a single message. */
    private void displayMessage(Message message, User currentUser) {
        boolean isFromMe = message.senderId().equals(currentUser.getId());
        String sender = isFromMe ? "You" : "Them";
        String timestamp = TIME_FORMATTER.format(message.createdAt());

        logger.info("\n   [{}] {}:", timestamp, sender);
        logger.info("   {}", message.content());
    }

    /** Formats a timestamp as relative time (e.g., "5m ago"). */
    private String formatTimeAgo(Instant timestamp) {
        if (timestamp == null) {
            return "never";
        }

        Duration duration = Duration.between(timestamp, Instant.now());

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
        String response = input.readLine("\n" + action + "? (y/n): ").trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    /** Blocks another user. */
    private void blockUser(User currentUser, User otherUser) {
        datingapp.core.Block block = datingapp.core.Block.create(currentUser.getId(), otherUser.getId());
        registry.getBlockStorage().save(block);

        String matchId = Match.generateId(currentUser.getId(), otherUser.getId());
        Optional<Match> matchOpt = registry.getMatchStorage().get(matchId);
        if (matchOpt.isPresent()) {
            Match match = matchOpt.get();
            if (match.isActive()) {
                match.block(currentUser.getId());
                registry.getMatchStorage().update(match);
            }
        }

        logger.info("\n✓ {} has been blocked.", otherUser.getName());
    }

    /** Unmatches from another user. */
    private void unmatchUser(User currentUser, User otherUser) {
        String matchId = Match.generateId(currentUser.getId(), otherUser.getId());
        Optional<Match> matchOpt = registry.getMatchStorage().get(matchId);

        if (matchOpt.isPresent()) {
            Match match = matchOpt.get();
            if (match.isActive()) {
                match.unmatch(currentUser.getId());
                registry.getMatchStorage().update(match);
                logger.info("\n✓ Unmatched from {}.", otherUser.getName());
            } else {
                logger.info("\n⚠️  Match is already ended.");
            }
        } else {
            logger.info("\n⚠️  No match found.");
        }
    }

    /** Returns the total unread count for menu display. */
    public int getTotalUnreadCount() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            return 0;
        }
        return registry.getMessagingService().getTotalUnreadCount(currentUser.getId());
    }
}
