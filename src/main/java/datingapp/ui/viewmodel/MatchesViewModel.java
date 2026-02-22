package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.TextUtil;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.PageData;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 *
 * <p>
 * Matches are loaded in pages of {@value #PAGE_SIZE} to avoid loading an
 * unbounded
 * number of {@link Match} objects into the JVM heap simultaneously. Call
 * {@link #loadNextMatchPage()} to append the next page, or
 * {@link #resetMatchPage()} to
 * start over from the beginning.
 */
public class MatchesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MatchesViewModel.class);
    private static final String LIKE_REQUIRED = "like cannot be null";

    /** Number of matches returned per page from storage. */
    private static final int PAGE_SIZE = 20;

    private final UiMatchDataAccess matchData;
    private final UiUserStore userStore;
    private final MatchingService matchingService;
    private final RecommendationService dailyService;
    private final AppSession session;
    private final ObservableList<MatchCardData> matches = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesReceived = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesSent = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesReceivedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesSentCount = new SimpleIntegerProperty(0);

    /**
     * Total number of active matches in storage for the current user (across all
     * pages).
     */
    private final IntegerProperty totalMatchCount = new SimpleIntegerProperty(0);

    /**
     * {@code true} when at least one more page of matches exists beyond what is
     * currently loaded.
     */
    private final BooleanProperty hasMoreMatches = new SimpleBooleanProperty(false);

    /**
     * Zero-based index of the next match to load; incremented as pages are
     * appended.
     */
    private final AtomicInteger currentMatchOffset = new AtomicInteger(0);

    private final AtomicInteger fetchEpoch = new AtomicInteger(0);

    private ViewModelErrorSink errorHandler;

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    public MatchesViewModel(
            UiMatchDataAccess matchData,
            UiUserStore userStore,
            MatchingService matchingService,
            RecommendationService dailyService,
            AppSession session) {
        this.matchData = Objects.requireNonNull(matchData, "matchData cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    /** Initialize and load matches for current user. */
    public void initialize() {
        currentUser.set(session.getCurrentUser());
        if (currentUser.get() != null) {
            refreshAll();
        }
    }

    private User resolveCurrentUser() {
        User user = currentUser.get();
        if (user == null) {
            user = session.getCurrentUser();
            if (user != null) {
                currentUser.set(user);
            }
        }
        return user;
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
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot refresh matches");
            return;
        }

        fetchEpoch.incrementAndGet();
        loading.set(true);
        // Reset pagination state so we always start from the first page on a full
        // refresh.
        currentMatchOffset.set(0);
        UUID userId = user.getId();
        String userName = user.getName();

        // Run async only when invoked from FX thread; otherwise keep deterministic
        // synchronous behavior for background/test callers.
        boolean runAsync = isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread();

        if (runAsync) {
            // Execute blocking storage calls on virtual thread to avoid FX thread blocking
            // (H-12)
            Thread.ofVirtual().name("matches-refresh").start(() -> {
                try {
                    MatchFetchResult matchResult = fetchMatchesFromStorage(userId);
                    List<LikeCardData> received = fetchReceivedLikesFromStorage(userId);
                    List<LikeCardData> sent = fetchSentLikesFromStorage(userId);

                    javafx.application.Platform.runLater(() -> {
                        updateObservableLists(matchResult, received, sent, userName);
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
                MatchFetchResult matchResult = fetchMatchesFromStorage(userId);
                List<LikeCardData> received = fetchReceivedLikesFromStorage(userId);
                List<LikeCardData> sent = fetchSentLikesFromStorage(userId);
                updateObservableLists(matchResult, received, sent, userName);
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
            if (javafx.application.Platform.isFxApplicationThread()) {
                return true;
            }
            javafx.application.Platform.runLater(() -> {
                // probe only
            });
            return true;
        } catch (IllegalStateException _) {
            return false;
        }
    }

    /**
     * Updates the observable lists with fetched data.
     */
    private void updateObservableLists(
            MatchFetchResult matchResult, List<LikeCardData> received, List<LikeCardData> sent, String userName) {
        matches.setAll(matchResult.cards());
        matchCount.set(matches.size());
        totalMatchCount.set(matchResult.totalCount());
        hasMoreMatches.set(matchResult.hasMore());

        likesReceived.setAll(received);
        likesReceivedCount.set(likesReceived.size());

        likesSent.setAll(sent);
        likesSentCount.set(likesSent.size());

        if (logger.isInfoEnabled()) {
            logger.info("Refreshed all matches and likes for {}", userName);
        }
    }

    /**
     * Private result record returned by {@link #fetchMatchesFromStorage(UUID)} so
     * that
     * pagination metadata (total count, has-more flag) can travel alongside the UI
     * card data
     * without an extra storage round-trip.
     */
    private record MatchFetchResult(List<MatchCardData> cards, int totalCount, boolean hasMore) {}

    private MatchFetchResult fetchMatchesFromStorage(UUID userId) {
        // Use the paginated method: only load PAGE_SIZE matches, not the entire table.
        // getAndAdd atomically reads the current offset and advances it by the page
        // size
        // before the DB call completes; we correct it below with the actual items
        // fetched.
        int offset = currentMatchOffset.get();
        PageData<Match> page = matchData.getPageOfActiveMatchesFor(userId, offset, PAGE_SIZE);

        // Advance by actual items fetched (may be < PAGE_SIZE on the last page).
        currentMatchOffset.addAndGet(page.items().size());

        List<UUID> otherUserIds =
                page.items().stream().map(m -> m.getOtherUser(userId)).toList();
        Map<UUID, User> otherUsers = userStore.findByIds(new HashSet<>(otherUserIds));

        List<MatchCardData> cardData = new ArrayList<>();
        for (Match match : page.items()) {
            UUID otherUserId = match.getOtherUser(userId);
            User otherUser = otherUsers.get(otherUserId);
            if (otherUser != null) {
                cardData.add(new MatchCardData(
                        match.getId(),
                        otherUser.getId(),
                        otherUser.getName(),
                        TextUtil.formatTimeAgo(match.getCreatedAt()),
                        match.getCreatedAt()));
            }
        }
        return new MatchFetchResult(List.copyOf(cardData), page.totalCount(), page.hasMore());
    }

    private List<LikeCardData> fetchReceivedLikesFromStorage(UUID userId) {
        List<LikeCardData> received = new ArrayList<>();
        List<PendingLiker> pendingLikers = matchingService.findPendingLikersWithTimes(userId);

        for (PendingLiker pending : pendingLikers) {
            User liker = pending.user();
            if (liker != null && liker.getState() == UserState.ACTIVE) {
                Like like = matchData.getLike(liker.getId(), userId).orElse(null);
                if (like != null) {
                    @SuppressWarnings("deprecation") // UI display - system timezone appropriate
                    int age = liker.getAge();
                    received.add(new LikeCardData(
                            liker.getId(),
                            like.id(),
                            liker.getName(),
                            age,
                            summarizeBio(liker),
                            TextUtil.formatTimeAgo(pending.likedAt()),
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
                    @SuppressWarnings("deprecation") // UI display - system timezone appropriate
                    int age = otherUser.getAge();
                    sent.add(new LikeCardData(
                            otherUser.getId(),
                            like.id(),
                            otherUser.getName(),
                            age,
                            summarizeBio(otherUser),
                            TextUtil.formatTimeAgo(like.createdAt()),
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

    /**
     * Loads the next page of active matches and appends them to the current list.
     *
     * <p>
     * This is a "load more" operation — it does not clear the existing list. Useful
     * for
     * infinite-scroll or "Show More" button patterns in the UI.
     */
    public void loadNextMatchPage() {
        if (!hasMoreMatches.get()) {
            return;
        }
        User user = resolveCurrentUser();
        if (user == null) {
            return;
        }
        UUID userId = user.getId();
        int capturedEpoch = fetchEpoch.get();
        loading.set(true);

        if (isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread()) {
            Thread.ofVirtual().name("matches-load-more").start(() -> {
                try {
                    MatchFetchResult result = fetchMatchesFromStorage(userId);
                    javafx.application.Platform.runLater(() -> {
                        if (capturedEpoch != fetchEpoch.get()) {
                            return;
                        }
                        matches.addAll(result.cards());
                        matchCount.set(matches.size());
                        totalMatchCount.set(result.totalCount());
                        hasMoreMatches.set(result.hasMore());
                        loading.set(false);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> {
                        if (capturedEpoch != fetchEpoch.get()) {
                            return;
                        }
                        logWarn("Failed to load next match page: {}", e.getMessage(), e);
                        notifyError("Failed to load more matches", e);
                        loading.set(false);
                    });
                }
            });
        } else {
            try {
                MatchFetchResult result = fetchMatchesFromStorage(userId);
                if (capturedEpoch != fetchEpoch.get()) {
                    return;
                }
                matches.addAll(result.cards());
                matchCount.set(matches.size());
                totalMatchCount.set(result.totalCount());
                hasMoreMatches.set(result.hasMore());
            } catch (Exception e) {
                if (capturedEpoch != fetchEpoch.get()) {
                    return;
                }
                logWarn("Failed to load next match page: {}", e.getMessage(), e);
                notifyError("Failed to load more matches", e);
            } finally {
                if (capturedEpoch == fetchEpoch.get()) {
                    loading.set(false);
                }
            }
        }
    }

    /**
     * Resets the match page offset to zero and performs a full refresh.
     * Call this when navigating back to the screen or after a match state change.
     */
    public void resetMatchPage() {
        refreshAll();
    }

    public void likeBack(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot like back");
            return;
        }

        loading.set(true);
        UUID userId = user.getId();

        if (isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread()) {
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
                    loading.set(false);
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
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot pass");
            return;
        }

        loading.set(true);
        UUID userId = user.getId();

        if (isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread()) {
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

        if (isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread()) {
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

    /** Total number of active matches in storage (across all pages). */
    public IntegerProperty totalMatchCountProperty() {
        return totalMatchCount;
    }

    /**
     * {@code true} when there are more pages of active matches that have not yet
     * been loaded.
     * Bind to a "Load More" button's visibility in the UI.
     */
    public BooleanProperty hasMoreMatchesProperty() {
        return hasMoreMatches;
    }

    /** Data class for a match card display. */
    public static record MatchCardData(
            String matchId, UUID userId, String userName, String matchedTimeAgo, Instant matchedAt) {}

    /** Data class for a like card display. */
    public static record LikeCardData(
            UUID userId,
            UUID likeId,
            String userName,
            int age,
            String bioSnippet,
            String likedTimeAgo,
            Instant likedAt) {}
}
