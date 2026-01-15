package datingapp.ui.viewmodel;

import datingapp.core.Match;
import datingapp.core.MatchStorage;
import datingapp.core.User;
import datingapp.core.UserStorage;
import datingapp.ui.UISession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Matches screen.
 * Displays all active matches for the current user.
 */
public class MatchesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MatchesViewModel.class);

    private final MatchStorage matchStorage;
    private final UserStorage userStorage;
    private final ObservableList<MatchCardData> matches = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);

    private User currentUser;

    public MatchesViewModel(MatchStorage matchStorage, UserStorage userStorage) {
        this.matchStorage = matchStorage;
        this.userStorage = userStorage;
    }

    /** Initialize and load matches for current user. */
    public void initialize() {
        currentUser = UISession.getInstance().getCurrentUser();
        if (currentUser != null) {
            refresh();
        }
    }

    /** Refresh the matches list. */
    public void refresh() {
        if (currentUser == null) {
            currentUser = UISession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logger.warn("No current user, cannot load matches");
            return;
        }

        logger.info("Loading matches for user: {}", currentUser.getName());
        loading.set(true);
        matches.clear();

        List<Match> activeMatches = matchStorage.getActiveMatchesFor(currentUser.getId());

        for (Match match : activeMatches) {
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);
            if (otherUser != null) {
                String timeAgo = formatTimeAgo(match.getCreatedAt());
                matches.add(new MatchCardData(
                        match.getId(), otherUser.getId(), otherUser.getName(), timeAgo, match.getCreatedAt()));
            }
        }

        matchCount.set(matches.size());
        loading.set(false);
        logger.info("Loaded {} matches", matches.size());
    }

    /** Format a timestamp as "X days ago" or similar. */
    private String formatTimeAgo(Instant matchedAt) {
        Instant now = Instant.now();
        long days = ChronoUnit.DAYS.between(matchedAt, now);

        if (days == 0) {
            long hours = ChronoUnit.HOURS.between(matchedAt, now);
            if (hours == 0) {
                return "Just now";
            }
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (days == 1) {
            return "Yesterday";
        } else if (days < 7) {
            return days + " days ago";
        } else if (days < 30) {
            long weeks = days / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else {
            long months = days / 30;
            return months + (months == 1 ? " month ago" : " months ago");
        }
    }

    // --- Properties ---
    public ObservableList<MatchCardData> getMatches() {
        return matches;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public IntegerProperty matchCountProperty() {
        return matchCount;
    }

    /** Data class for a match card display. */
    public record MatchCardData(
            String matchId, UUID userId, String userName, String matchedTimeAgo, Instant matchedAt) {}
}
