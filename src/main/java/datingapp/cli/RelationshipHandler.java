package datingapp.cli;

import datingapp.core.NotificationStorage;
import datingapp.core.RelationshipTransitionService;
import datingapp.core.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.Notification;
import datingapp.core.User;
import datingapp.core.UserStorage;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI Handler for relationship transitions (Friend Zone, Graceful Exit) and
 * notifications.
 */
public class RelationshipHandler {
    private static final Logger logger = LoggerFactory.getLogger(RelationshipHandler.class);

    private final RelationshipTransitionService transitionService;
    private final NotificationStorage notificationStorage;
    private final UserStorage userStorage;
    private final UserSession userSession;
    private final InputReader inputReader;

    public RelationshipHandler(
            RelationshipTransitionService transitionService,
            NotificationStorage notificationStorage,
            UserStorage userStorage,
            UserSession userSession,
            InputReader inputReader) {
        this.transitionService = transitionService;
        this.notificationStorage = notificationStorage;
        this.userStorage = userStorage;
        this.userSession = userSession;
        this.inputReader = inputReader;
    }

    /** Initiates a Friend Zone request for the target user. */
    public void handleFriendZone(UUID targetUserId) {
        User currentUser = userSession.getCurrentUser();
        User targetUser = userStorage.get(targetUserId);
        if (targetUser == null) {
            return;
        }

        logger.info("\nAsking to move your match with {} to the Friend Zone...", targetUser.getName());
        logger.info("This will end the romantic match and transition to a Platonic Friendship if they accept.");

        String confirm = inputReader.readLine("Send friend request? (y/n): ");
        if (confirm.equalsIgnoreCase("y")) {
            try {
                transitionService.requestFriendZone(currentUser.getId(), targetUserId);
                logger.info("✅ Friend request sent to {}.\n", targetUser.getName());
            } catch (TransitionValidationException e) {
                logger.info("❌ Failed: {}\n", e.getMessage());
            }
        }
    }

    /** Initiates a Graceful Exit for the target user. */
    public void handleGracefulExit(UUID targetUserId) {
        User currentUser = userSession.getCurrentUser();
        User targetUser = userStorage.get(targetUserId);
        if (targetUser == null) {
            return;
        }

        logger.info("\nEnding your relationship with {} gracefully...", targetUser.getName());
        logger.info("This is a kind way to move on. They will be notified and the match/friendship will end.");

        String confirm = inputReader.readLine("Proceed with graceful exit? (y/n): ");
        if (confirm.equalsIgnoreCase("y")) {
            try {
                transitionService.gracefulExit(currentUser.getId(), targetUserId);
                logger.info("🕊️ You have gracefully moved on. Match ended.\n");
            } catch (TransitionValidationException e) {
                logger.info("❌ Failed: {}\n", e.getMessage());
            }
        }
    }

    /** Displays and manages pending friend requests. */
    public void viewPendingRequests() {
        User currentUser = userSession.getCurrentUser();
        List<FriendRequest> requests = transitionService.getPendingRequestsFor(currentUser.getId());

        if (requests.isEmpty()) {
            logger.info("\nNo pending friend requests.\n");
            return;
        }

        logger.info("\n--- PENDING FRIEND REQUESTS ---");
        for (int i = 0; i < requests.size(); i++) {
            FriendRequest req = requests.get(i);
            User from = userStorage.get(req.fromUserId());
            String fromName = from != null ? from.getName() : "Unknown User";
            logger.info("  {}. From: {} (Received: {})", i + 1, fromName, req.createdAt());
        }

        String choice = inputReader.readLine("\nEnter request number to respond (or 'b' to go back): ");
        if (choice.equalsIgnoreCase("b")) {
            return;
        }

        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx < 0 || idx >= requests.size()) {
                logger.info("Invalid selection.");
                return;
            }

            FriendRequest req = requests.get(idx);
            User from = userStorage.get(req.fromUserId());
            String fromName = from != null ? from.getName() : "Unknown User";

            logger.info("\nFriend Request from {}", fromName);
            String action = inputReader
                    .readLine("Do you want to (A)ccept or (D)ecline? ")
                    .toLowerCase();

            if (action.equals("a")) {
                transitionService.acceptFriendZone(req.id(), currentUser.getId());
                logger.info("✅ You are now friends with {}! You can find them in your matches.\n", fromName);
            } else if (action.equals("d")) {
                transitionService.declineFriendZone(req.id(), currentUser.getId());
                logger.info("Declined friend request from {}.\n", fromName);
            }
        } catch (NumberFormatException | TransitionValidationException e) {
            logger.info("Error processing request: {}\n", e.getMessage());
        }
    }

    /** Displays notifications for the current user. */
    public void viewNotifications() {
        User currentUser = userSession.getCurrentUser();
        List<Notification> notifications = notificationStorage.getForUser(currentUser.getId(), false);

        if (notifications.isEmpty()) {
            logger.info("\nNo notifications.\n");
            return;
        }

        logger.info("\n--- YOUR NOTIFICATIONS ---");
        for (Notification n : notifications) {
            String status = n.isRead() ? "  " : "🆕";
            logger.info("{} [{}] {}: {}", status, n.createdAt(), n.title(), n.message());
            if (!n.isRead()) {
                notificationStorage.markAsRead(n.id());
            }
        }
        logger.info("--------------------------\n");
        inputReader.readLine("Press Enter to continue...");
    }
}
