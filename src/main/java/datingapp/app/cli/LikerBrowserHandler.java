package datingapp.app.cli;

import datingapp.core.AppSession;
import datingapp.core.MatchingService;
import datingapp.core.MatchingService.PendingLiker;
import datingapp.core.User;
import datingapp.core.UserInteractions.Like;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for browsing users who have liked the current user's profile. */
public class LikerBrowserHandler {
    private static final Logger logger = LoggerFactory.getLogger(LikerBrowserHandler.class);

    private final MatchingService matchingService;
    private final AppSession session;
    private final CliUtilities.InputReader inputReader;

    /** Creates a new LikerBrowserHandler with the required dependencies. */
    public LikerBrowserHandler(
            MatchingService matchingService, AppSession session, CliUtilities.InputReader inputReader) {
        this.matchingService = Objects.requireNonNull(matchingService);
        this.session = Objects.requireNonNull(session);
        this.inputReader = Objects.requireNonNull(inputReader);
    }

    /**
     * Displays a list of users who have liked the current user and allows
     * interaction.
     */
    public void browseWhoLikedMe() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            List<PendingLiker> likers = matchingService.findPendingLikersWithTimes(currentUser.getId());

            if (likers.isEmpty()) {
                logger.info("\nNo new likes yet.\n");
                return;
            }

            logger.info("\n--- Who Liked Me ({} pending) ---\n", likers.size());

            for (PendingLiker liker : likers) {
                showCard(liker);
                logger.info("1. Like back");
                logger.info("2. Pass");
                logger.info("0. Stop\n");

                String choice = inputReader.readLine("Choice: ");
                switch (choice) {
                    case "1" -> handleSwipe(currentUser, liker.user(), Like.Direction.LIKE);
                    case "2" -> handleSwipe(currentUser, liker.user(), Like.Direction.PASS);
                    case "0" -> {
                        return;
                    }
                    default -> logger.info(CliConstants.INVALID_SELECTION);
                }
            }

            logger.info("\nEnd of list.\n");
        });
    }

    private void showCard(PendingLiker pending) {
        User user = pending.user();
        String verifiedBadge = Boolean.TRUE.equals(user.isVerified()) ? " ✅ Verified" : "";
        String likedAgo = formatTimeAgo(pending.likedAt());
        logger.info(CliConstants.BOX_TOP);
        logger.info("│ 💝 {}, {} years old{}", user.getName(), user.getAge(), verifiedBadge);
        logger.info("│ 🕒 Liked you {}", likedAgo);
        logger.info("│ 📍 Location: {}, {}", user.getLat(), user.getLon());
        String bio = user.getBio() == null ? "" : user.getBio();
        if (bio.length() > 50) {
            bio = bio.substring(0, 47) + "...";
        }
        logger.info(CliConstants.PROFILE_BIO_FORMAT, bio);
        logger.info(CliConstants.BOX_BOTTOM);
        logger.info("");
    }

    private void handleSwipe(User currentUser, User other, Like.Direction direction) {
        matchingService.recordLike(Like.create(currentUser.getId(), other.getId(), direction));
        logger.info("✅ Saved.\n");
    }

    private String formatTimeAgo(java.time.Instant likedAt) {
        if (likedAt == null) {
            return "(unknown)";
        }

        java.time.Duration duration = java.time.Duration.between(likedAt, java.time.Instant.now());
        if (duration.isNegative()) {
            duration = java.time.Duration.ZERO;
        }

        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }

        long days = hours / 24;
        return days + "d ago";
    }
}
