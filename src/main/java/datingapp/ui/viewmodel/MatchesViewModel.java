package datingapp.ui.viewmodel;

import datingapp.core.AppClock;
import datingapp.core.AppSession;
import datingapp.core.model.ConnectionModels.Like;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.service.MatchingService;
import datingapp.core.service.MatchingService.PendingLiker;
import datingapp.core.service.RecommendationService;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiMatchDataAccess;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.shared.ViewModelErrorSink;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * Displays active matches plus received and sent likes for the current user.
 */
public class MatchesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MatchesViewModel.class);
    private static final String LIKE_REQUIRED = "like cannot be null";

    private final UiMatchDataAccess matchData;
    private final UiUserStore userStore;
    private final MatchingService matchingService;
    private final RecommendationService dailyService;
    private final ObservableList<MatchCardData> matches = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesReceived = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesSent = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesReceivedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesSentCount = new SimpleIntegerProperty(0);

    private ViewModelErrorSink errorHandler;

    private User currentUser;

    public MatchesViewModel(
            UiMatchDataAccess matchData,
            UiUserStore userStore,
            MatchingService matchingService,
            RecommendationService dailyService) {
        this.matchData = Objects.requireNonNull(matchData, "matchData cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
    }

    /** Initialize and load matches for current user. */
    public void initialize() {
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
        matches.clear();
        likesReceived.clear();
        likesSent.clear();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
    }

    /** Refresh all sections for the current user. */
    public void refreshAll() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        if (currentUser == null) {
            logWarn("No current user, cannot refresh matches");
            return;
        }

        loading.set(true);
        UUID userId = currentUser.getId();
        String userName = currentUser.getName();

        // Check if FX toolkit is available (will be false in unit tests)
        boolean fxAvailable = isFxToolkitAvailable();

        if (fxAvailable) {
            // Execute blocking storage calls on virtual thread to avoid FX thread blocking
            // (H-12)
            Thread.ofVirtual().name("matches-refresh").start(() -> {
                try {
                    List<MatchCardData> matchCardDataList = fetchMatchesFromStorage(userId);
                    List<LikeCardData> received = fetchReceivedLikesFromStorage(userId);
                    List<LikeCardData> sent = fetchSentLikesFromStorage(userId);

                    javafx.application.Platform.runLater(() -> {
                        updateObservableLists(matchCardDataList, received, sent, userName);
                        loading.set(false);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        logWarn("Failed to refresh matches: {}", e.getMessage(), e);
                        notifyError("Failed to refresh matches", e);
                        loading.set(false);
                    });
                }
            });
        } else {
            // Synchronous execution for tests (no FX toolkit available)
            try {
                List<MatchCardData> matchCardDataList = fetchMatchesFromStorage(userId);
                List<LikeCardData> received = fetchReceivedLikesFromStorage(userId);
                List<LikeCardData> sent = fetchSentLikesFromStorage(userId);
                updateObservableLists(matchCardDataList, received, sent, userName);
            } catch (Exception e) {
                logWarn("Failed to refresh matches: {}", e.getMessage(), e);
                notifyError("Failed to refresh matches", e);
            } finally {
                loading.set(false);
            }
        }
    }

    /**
     * Check if the JavaFX toolkit is available. Returns false in unit test
     * environments.
     */
    private boolean isFxToolkitAvailable() {
        try {
            return javafx.application.Platform.isFxApplicationThread()
                    || com.sun.javafx.application.PlatformImpl.isFxApplicationThread();
        } catch (IllegalStateException | NoClassDefFoundError _) {
            return false;
        }
    }

    /**
     * Updates the observable lists with fetched data.
     */
    private void updateObservableLists(
            List<MatchCardData> matchCardDataList,
            List<LikeCardData> received,
            List<LikeCardData> sent,
            String userName) {
        matches.setAll(matchCardDataList);
        matchCount.set(matches.size());

        likesReceived.setAll(received);
        likesReceivedCount.set(likesReceived.size());

        likesSent.setAll(sent);
        likesSentCount.set(likesSent.size());

        if (logger.isInfoEnabled()) {
            logger.info("Refreshed all matches and likes for {}", userName);
        }
    }

    private List<MatchCardData> fetchMatchesFromStorage(UUID userId) {
        List<Match> activeMatches = matchData.getActiveMatchesFor(userId);
        List<UUID> otherUserIds =
                activeMatches.stream().map(m -> m.getOtherUser(userId)).toList();
        Map<UUID, User> otherUsers = userStore.findByIds(new HashSet<>(otherUserIds));

        List<MatchCardData> cardData = new ArrayList<>();
        for (Match match : activeMatches) {
            UUID otherUserId = match.getOtherUser(userId);
            User otherUser = otherUsers.get(otherUserId);
            if (otherUser != null) {
                cardData.add(new MatchCardData(
                        match.getId(),
                        otherUser.getId(),
                        otherUser.getName(),
                        formatTimeAgo(match.getCreatedAt()),
                        match.getCreatedAt()));
            }
        }
        return cardData;
    }

    private List<LikeCardData> fetchReceivedLikesFromStorage(UUID userId) {
        List<LikeCardData> received = new ArrayList<>();
        List<PendingLiker> pendingLikers = matchingService.findPendingLikersWithTimes(userId);

        for (PendingLiker pending : pendingLikers) {
            User liker = pending.user();
            if (liker != null && liker.getState() == UserState.ACTIVE) {
                Like like = matchData.getLike(liker.getId(), userId).orElse(null);
                if (like != null) {
                    received.add(new LikeCardData(
                            liker.getId(),
                            like.id(),
                            liker.getName(),
                            liker.getAge(),
                            summarizeBio(liker),
                            formatTimeAgo(pending.likedAt()),
                            pending.likedAt()));
                }
            }
        }
        received.sort(likeTimeComparator());
        return received;
    }

    private List<LikeCardData> fetchSentLikesFromStorage(UUID userId) {
        Set<UUID> blocked = matchData.getBlockedUserIds(userId);
        Set<UUID> matched = getMatchedUserIds(userId);
        Set<UUID> allLikedOrPassedIds = matchData.getLikedOrPassedUserIds(userId);

        List<UUID> candidateIds = allLikedOrPassedIds.stream()
                .filter(id -> !blocked.contains(id) && !matched.contains(id))
                .toList();

        Map<UUID, User> potentialUsers = userStore.findByIds(new HashSet<>(candidateIds));
        List<LikeCardData> sent = new ArrayList<>();

        for (UUID otherUserId : candidateIds) {
            Like like = matchData.getLike(userId, otherUserId).orElse(null);
            if (like != null && like.direction() == Like.Direction.LIKE) {
                User otherUser = potentialUsers.get(otherUserId);
                if (otherUser != null && otherUser.getState() == UserState.ACTIVE) {
                    sent.add(new LikeCardData(
                            otherUser.getId(),
                            like.id(),
                            otherUser.getName(),
                            otherUser.getAge(),
                            summarizeBio(otherUser),
                            formatTimeAgo(like.createdAt()),
                            like.createdAt()));
                }
            }
        }
        sent.sort(likeTimeComparator());
        return sent;
    }

    /** Refresh the matches list. */
    public void refresh() {
        refreshAll();
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

        loading.set(true);
        UUID userId = currentUser.getId();

        if (isFxToolkitAvailable()) {
            Thread.ofVirtual().name("matches-like-back").start(() -> {
                try {
                    if (!dailyService.canLike(userId)) {
                        javafx.application.Platform.runLater(() -> {
                            notifyError("Daily like limit reached", new IllegalStateException("Daily limit reached"));
                            loading.set(false);
                        });
                        return;
                    }
                    matchingService.recordLike(Like.create(userId, like.userId(), Like.Direction.LIKE));
                    javafx.application.Platform.runLater(this::refreshAll);
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        logWarn("Failed to like back: {}", e.getMessage(), e);
                        notifyError("Failed to like back", e);
                        loading.set(false);
                    });
                }
            });
        } else {
            try {
                if (!dailyService.canLike(userId)) {
                    notifyError("Daily like limit reached", new IllegalStateException("Daily limit reached"));
                    return;
                }
                matchingService.recordLike(Like.create(userId, like.userId(), Like.Direction.LIKE));
                refreshAll();
            } catch (Exception e) {
                logWarn("Failed to like back: {}", e.getMessage(), e);
                notifyError("Failed to like back", e);
                loading.set(false);
            }
        }
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

        loading.set(true);
        UUID userId = currentUser.getId();

        if (isFxToolkitAvailable()) {
            Thread.ofVirtual().name("matches-pass-on").start(() -> {
                try {
                    matchingService.recordLike(Like.create(userId, like.userId(), Like.Direction.PASS));
                    javafx.application.Platform.runLater(this::refreshAll);
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        logWarn("Failed to pass: {}", e.getMessage(), e);
                        notifyError("Failed to pass", e);
                        loading.set(false);
                    });
                }
            });
        } else {
            try {
                matchingService.recordLike(Like.create(userId, like.userId(), Like.Direction.PASS));
                refreshAll();
            } catch (Exception e) {
                logWarn("Failed to pass: {}", e.getMessage(), e);
                notifyError("Failed to pass", e);
                loading.set(false);
            }
        }
    }

    public void withdrawLike(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (like.likeId() == null) {
            return;
        }

        loading.set(true);

        if (isFxToolkitAvailable()) {
            Thread.ofVirtual().name("matches-withdraw").start(() -> {
                try {
                    matchData.deleteLike(like.likeId());
                    javafx.application.Platform.runLater(this::refreshAll);
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        logWarn("Failed to withdraw like {}", like.likeId(), e);
                        notifyError("Failed to withdraw like", e);
                        loading.set(false);
                    });
                }
            });
        } else {
            try {
                matchData.deleteLike(like.likeId());
                refreshAll();
            } catch (Exception e) {
                logWarn("Failed to withdraw like {}", like.likeId(), e);
                notifyError("Failed to withdraw like", e);
                loading.set(false);
            }
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
        errorHandler.onError(message);
    }

    /** Format a timestamp as "X days ago" or similar. */
    private String formatTimeAgo(Instant matchedAt) {
        if (matchedAt == null) {
            return "Unknown";
        }
        Instant now = AppClock.now();
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
        for (Match match : matchData.getAllMatchesFor(userId)) {
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
