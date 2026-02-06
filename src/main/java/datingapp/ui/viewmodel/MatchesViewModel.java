package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.DailyService;
import datingapp.core.Match;
import datingapp.core.MatchingService;
import datingapp.core.MatchingService.PendingLiker;
import datingapp.core.User;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserState;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
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
 * Displays active matches plus received and sent likes for the current user.
 */
public class MatchesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MatchesViewModel.class);
    private static final String LIKE_REQUIRED = "like cannot be null";

    private final MatchStorage matchStorage;
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final MatchingService matchingService;
    private final DailyService dailyService;
    private final ObservableList<MatchCardData> matches = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesReceived = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesSent = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesReceivedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesSentCount = new SimpleIntegerProperty(0);

    private ErrorHandler errorHandler;

    private User currentUser;

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public MatchesViewModel(
            MatchStorage matchStorage,
            UserStorage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            MatchingService matchingService,
            DailyService dailyService) {
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = Objects.requireNonNull(blockStorage, "blockStorage cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
    }

    /** Initialize and load matches for current user. */
    public void initialize() {
        if (disposed.get()) {
            return;
        }
        currentUser = AppSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            refreshAll();
        }
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        matches.clear();
        likesReceived.clear();
        likesSent.clear();
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    /** Refresh all sections for the current user. */
    public void refreshAll() {
        refreshMatches();
        refreshLikesReceived();
        refreshLikesSent();
    }

    /** Refresh the matches list. */
    public void refresh() {
        refreshAll();
    }

    private void refreshMatches() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot load matches");
            return;
        }

        logInfo("Loading matches for user: {}", currentUser.getName());
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
        logInfo("Loaded {} matches", matches.size());
    }

    private void refreshLikesReceived() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot load likes received");
            return;
        }

        List<LikeCardData> received = new ArrayList<>();
        List<PendingLiker> pendingLikers = matchingService.findPendingLikersWithTimes(currentUser.getId());

        for (PendingLiker pending : pendingLikers) {
            User liker = pending.user();
            if (liker != null && liker.getState() == UserState.ACTIVE) {
                Like like =
                        likeStorage.getLike(liker.getId(), currentUser.getId()).orElse(null);
                if (like != null) {
                    Instant likedAt = pending.likedAt();
                    received.add(new LikeCardData(
                            liker.getId(),
                            like.id(),
                            liker.getName(),
                            liker.getAge(),
                            summarizeBio(liker),
                            formatTimeAgo(likedAt),
                            likedAt));
                }
            }
        }

        received.sort(likeTimeComparator());
        likesReceived.setAll(received);
        likesReceivedCount.set(likesReceived.size());
    }

    private void refreshLikesSent() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot load likes sent");
            return;
        }

        Set<UUID> blocked = blockStorage.getBlockedUserIds(currentUser.getId());
        Set<UUID> matched = getMatchedUserIds(currentUser.getId());

        List<LikeCardData> sent = new ArrayList<>();
        for (UUID otherUserId : likeStorage.getLikedOrPassedUserIds(currentUser.getId())) {
            if (!blocked.contains(otherUserId) && !matched.contains(otherUserId)) {
                Like like =
                        likeStorage.getLike(currentUser.getId(), otherUserId).orElse(null);
                if (like != null && like.direction() == Like.Direction.LIKE) {
                    User otherUser = userStorage.get(otherUserId);
                    if (otherUser != null && otherUser.getState() == UserState.ACTIVE) {
                        Instant likedAt = like.createdAt();
                        sent.add(new LikeCardData(
                                otherUser.getId(),
                                like.id(),
                                otherUser.getName(),
                                otherUser.getAge(),
                                summarizeBio(otherUser),
                                formatTimeAgo(likedAt),
                                likedAt));
                    }
                }
            }
        }

        sent.sort(likeTimeComparator());
        likesSent.setAll(sent);
        likesSentCount.set(likesSent.size());
    }

    public void likeBack(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot like back");
            return;
        }
        if (!dailyService.canLike(currentUser.getId())) {
            notifyError("Daily like limit reached", new IllegalStateException("Daily limit reached"));
            return;
        }

        matchingService.recordLike(Like.create(currentUser.getId(), like.userId(), Like.Direction.LIKE));
        refreshAll();
    }

    public void passOn(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot pass");
            return;
        }

        matchingService.recordLike(Like.create(currentUser.getId(), like.userId(), Like.Direction.PASS));
        refreshLikesReceived();
        refreshMatches();
    }

    public void withdrawLike(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (like.likeId() == null) {
            return;
        }

        try {
            likeStorage.delete(like.likeId());
            refreshLikesSent();
        } catch (Exception e) {
            logWarn("Failed to withdraw like {}", like.likeId(), e);
            notifyError("Failed to withdraw like", e);
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void notifyError(String userMessage, Exception e) {
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        if (Platform.isFxApplicationThread()) {
            errorHandler.onError(message);
        } else {
            Platform.runLater(() -> errorHandler.onError(message));
        }
    }

    /** Format a timestamp as "X days ago" or similar. */
    private String formatTimeAgo(Instant matchedAt) {
        if (matchedAt == null) {
            return "Unknown";
        }
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

    private static String summarizeBio(User user) {
        String bio = user.getBio();
        if (bio == null || bio.isBlank()) {
            return "No bio yet.";
        }

        String trimmed = bio.trim();
        if (trimmed.length() <= 80) {
            return trimmed;
        }

        return trimmed.substring(0, 77) + "...";
    }

    private Set<UUID> getMatchedUserIds(UUID userId) {
        Set<UUID> matched = new HashSet<>();
        for (Match match : matchStorage.getAllMatchesFor(userId)) {
            matched.add(match.getOtherUser(userId));
        }
        return matched;
    }

    private Comparator<LikeCardData> likeTimeComparator() {
        return (left, right) -> {
            Instant leftTime = left.likedAt();
            Instant rightTime = right.likedAt();
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        };
    }

    // --- Properties ---
    public ObservableList<MatchCardData> getMatches() {
        return matches;
    }

    public ObservableList<LikeCardData> getLikesReceived() {
        return likesReceived;
    }

    public ObservableList<LikeCardData> getLikesSent() {
        return likesSent;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public IntegerProperty matchCountProperty() {
        return matchCount;
    }

    public IntegerProperty likesReceivedCountProperty() {
        return likesReceivedCount;
    }

    public IntegerProperty likesSentCountProperty() {
        return likesSentCount;
    }

    /** Data class for a match card display. */
    public record MatchCardData(
            String matchId, UUID userId, String userName, String matchedTimeAgo, Instant matchedAt) {}

    /** Data class for a like card display. */
    public record LikeCardData(
            UUID userId,
            UUID likeId,
            String userName,
            int age,
            String bioSnippet,
            String likedTimeAgo,
            Instant likedAt) {}
}
