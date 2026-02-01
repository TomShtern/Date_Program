package datingapp.app.cli;

import datingapp.core.AppSession;
import datingapp.core.RelationshipTransitionService;
import datingapp.core.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.Notification;
import datingapp.core.User;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI Handler for relationship transitions (Friend Zone, Graceful Exit) and
 * notifications.
 */
public class RelationshipHandler {
    private static final Logger logger = LoggerFactory.getLogger(RelationshipHandler.class);

    private final RelationshipTransitionService transitionService;
    private final SocialStorage socialStorage;
    private final UserStorage userStorage;
    private final AppSession session;
    private final CliUtilities.InputReader inputReader;

    public RelationshipHandler(
            RelationshipTransitionService transitionService,
            SocialStorage socialStorage,
            UserStorage userStorage,
            AppSession session,
            CliUtilities.InputReader inputReader) {
        this.transitionService = transitionService;
        this.socialStorage = socialStorage;
        this.userStorage = userStorage;
        this.session = session;
        this.inputReader = inputReader;
    }

    /** Displays and manages pending friend requests. */
    public void viewPendingRequests() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logger.info("\n⚠️ Please log in first.\n");
            return;
        }
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
        if ("b".equalsIgnoreCase(choice)) {
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
                    .toLowerCase(Locale.ROOT);

            if ("a".equals(action)) {
                transitionService.acceptFriendZone(req.id(), currentUser.getId());
                logger.info("✅ You are now friends with {}! You can find them in your matches.\n", fromName);
            } else if ("d".equals(action)) {
                transitionService.declineFriendZone(req.id(), currentUser.getId());
                logger.info("Declined friend request from {}.\n", fromName);
            }
        } catch (NumberFormatException | TransitionValidationException e) {
            logger.info("Error processing request: {}\n", e.getMessage());
        }
    }

    /** Displays notifications for the current user. */
    public void viewNotifications() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logger.info("\n⚠️ Please log in first.\n");
            return;
        }
        List<Notification> notifications = socialStorage.getNotificationsForUser(currentUser.getId(), false);

        if (notifications.isEmpty()) {
            logger.info("\nNo notifications.\n");
            return;
        }

        logger.info("\n--- YOUR NOTIFICATIONS ---");
        for (Notification n : notifications) {
            String status = n.isRead() ? "  " : "🆕";
            logger.info("{} [{}] {}: {}", status, n.createdAt(), n.title(), n.message());
            if (!n.isRead()) {
                socialStorage.markNotificationAsRead(n.id());
            }
        }
        logger.info("--------------------------\n");
        inputReader.readLine("Press Enter to continue...");
    }
}
