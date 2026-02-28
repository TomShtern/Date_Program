package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.PendingLikersQuery;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.RemoveLikeCommand;
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
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private final MatchingUseCases matchingUseCases;
    private final AppSession session;
    private final ViewModelAsyncScope asyncScope;
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

    private final AtomicLong refreshEpoch = new AtomicLong(0);
    private final AtomicBoolean isFetchingNextPage = new AtomicBoolean(false);

    private ViewModelErrorSink errorHandler;

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    public MatchesViewModel(
            UiMatchDataAccess matchData,
            UiUserStore userStore,
            MatchingService matchingService,
            RecommendationService dailyService,
            AppSession session) {
        this(matchData, userStore, matchingService, dailyService, null, session, new JavaFxUiThreadDispatcher());
    }

    public MatchesViewModel(
            UiMatchDataAccess matchData,
            UiUserStore userStore,
            MatchingService matchingService,
            RecommendationService dailyService,
            MatchingUseCases matchingUseCases,
            AppSession session) {
        this(
                matchData,
                userStore,
                matchingService,
                dailyService,
                matchingUseCases,
                session,
                new JavaFxUiThreadDispatcher());
    }

    public MatchesViewModel(
            UiMatchDataAccess matchData,
            UiUserStore userStore,
            MatchingService matchingService,
            RecommendationService dailyService,
            MatchingUseCases matchingUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.matchData = Objects.requireNonNull(matchData, "matchData cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
        this.matchingUseCases = matchingUseCases;
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
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
        asyncScope.dispose();
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

        long capturedEpoch = refreshEpoch.incrementAndGet();
        // Reset pagination state so we always start from the first page on a full
        // refresh.
        currentMatchOffset.set(0);
        UUID userId = user.getId();
        String userName = user.getName();

        if (shouldRunAsync()) {
            refreshAllAsync(userId, userName, capturedEpoch);
            return;
        }
        refreshAllSync(userId, userName, capturedEpoch);
    }

    /**
     * Check if the JavaFX toolkit is available. Returns false in unit test
     * environments.
     */
    private boolean isFxToolkitAvailable() {
        try {
            if (Platform.isFxApplicationThread()) {
                return true;
            }
            Platform.runLater(() -> {
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

    private MatchFetchResult fetchMatchesFromStorage(UUID userId, long expectedEpoch) {
        // Reservce PAGE_SIZE items atomically to prevent concurrent callers of
        // fetchMatchesFromStorage from fetching the same page (TOCTOU race).
        // currentMatchOffset is advanced immediately; we adjust it downward below
        // if the storage call returns fewer items.
        int offset = currentMatchOffset.getAndAdd(PAGE_SIZE);

        // Call matchData.getPageOfActiveMatchesFor using the reserved offset.
        PageData<Match> page = matchData.getPageOfActiveMatchesFor(userId, offset, PAGE_SIZE);

        // If the returned page items size is less than PAGE_SIZE (last page),
        // adjust currentMatchOffset downward so the counter accurately reflects
        // the total items fetched.
        // GUARD: Only adjust if the current fetch is still valid for its epoch.
        // If a refreshAll() happened in between, currentMatchOffset has been reset
        // to 0 and adjusting it would corrupt the new state (possibly making it
        // negative).
        int actualCount = page.items().size();
        if (actualCount < PAGE_SIZE && refreshEpoch.get() == expectedEpoch) {
            currentMatchOffset.addAndGet(actualCount - PAGE_SIZE);
        }

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
        List<PendingLiker> pendingLikers;
        if (matchingUseCases != null) {
            var result = matchingUseCases.pendingLikers(new PendingLikersQuery(UserContext.ui(userId)));
            pendingLikers = result.success() ? result.data() : matchingService.findPendingLikersWithTimes(userId);
        } else {
            pendingLikers = matchingService.findPendingLikersWithTimes(userId);
        }

        for (PendingLiker pending : pendingLikers) {
            User liker = pending.user();
            if (liker != null && liker.getState() == UserState.ACTIVE) {
                Like like = matchData.getLike(liker.getId(), userId).orElse(null);
                if (like != null) {
                    int age = liker.getAge(
                            datingapp.core.AppConfig.defaults().safety().userTimeZone());
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
                    int age = otherUser.getAge(
                            datingapp.core.AppConfig.defaults().safety().userTimeZone());
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

        // Atomic guard to prevent concurrent fetches (H-12)
        if (!isFetchingNextPage.compareAndSet(false, true)) {
            return;
        }

        User user = resolveCurrentUser();
        if (user == null) {
            isFetchingNextPage.set(false);
            return;
        }

        UUID userId = user.getId();
        long capturedEpoch = refreshEpoch.get();

        if (shouldRunAsync()) {
            asyncScope.run(
                    "load more matches",
                    () -> fetchNextPagePayload(userId, capturedEpoch),
                    payload -> updateMatchesList(payload.result(), payload.epoch()));
            return;
        }

        setLoadingState(true);
        try {
            LoadMorePayload payload = fetchNextPagePayload(userId, capturedEpoch);
            updateMatchesList(payload.result(), payload.epoch());
        } catch (Exception e) {
            handleLoadError(e, capturedEpoch);
        } finally {
            setLoadingState(false);
        }
    }

    private void updateMatchesList(MatchFetchResult result, long capturedEpoch) {
        if (capturedEpoch != refreshEpoch.get()) {
            return;
        }
        matches.addAll(result.cards());
        matchCount.set(matches.size());
        totalMatchCount.set(result.totalCount());
        hasMoreMatches.set(result.hasMore());
    }

    private void handleLoadError(Exception e, long capturedEpoch) {
        if (capturedEpoch != refreshEpoch.get()) {
            return;
        }
        logWarn("Failed to load next match page: {}", e.getMessage(), e);
        notifyError("Failed to load more matches", e);
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

        UUID userId = user.getId();

        if (shouldRunAsync()) {
            asyncScope.run(
                    "like back",
                    () -> {
                        performLikeBack(userId, like.userId());
                        return Boolean.TRUE;
                    },
                    _ -> refreshAll());
            return;
        }

        executeActionSync("Failed to like back", () -> performLikeBack(userId, like.userId()), this::refreshAll);
    }

    public void passOn(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot pass");
            return;
        }

        UUID userId = user.getId();

        if (shouldRunAsync()) {
            asyncScope.run(
                    "pass on like",
                    () -> {
                        performPassOn(userId, like.userId());
                        return Boolean.TRUE;
                    },
                    _ -> refreshAll());
            return;
        }

        executeActionSync("Failed to pass", () -> performPassOn(userId, like.userId()), this::refreshAll);
    }

    public void withdrawLike(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (like.likeId() == null) {
            return;
        }

        if (shouldRunAsync()) {
            asyncScope.run(
                    "withdraw like",
                    () -> {
                        performWithdrawLike(like.likeId());
                        return Boolean.TRUE;
                    },
                    _ -> refreshAll());
            return;
        }

        executeActionSync("Failed to withdraw like", () -> performWithdrawLike(like.likeId()), this::refreshAll);
    }

    private void refreshAllAsync(UUID userId, String userName, long capturedEpoch) {
        asyncScope.runLatest(
                "matches-refresh",
                "refresh matches",
                () -> fetchRefreshPayload(userId, userName, capturedEpoch),
                payload -> {
                    if (refreshEpoch.get() == payload.epoch()) {
                        updateObservableLists(
                                payload.matchResult(), payload.received(), payload.sent(), payload.userName());
                    }
                });
    }

    private void refreshAllSync(UUID userId, String userName, long capturedEpoch) {
        setLoadingState(true);
        try {
            RefreshPayload payload = fetchRefreshPayload(userId, userName, capturedEpoch);
            if (refreshEpoch.get() == payload.epoch()) {
                updateObservableLists(payload.matchResult(), payload.received(), payload.sent(), payload.userName());
            }
        } catch (Exception e) {
            logWarn("Failed to refresh matches: {}", e.getMessage(), e);
            notifyError("Failed to refresh matches", e);
        } finally {
            if (refreshEpoch.get() == capturedEpoch) {
                setLoadingState(false);
            }
        }
    }

    private RefreshPayload fetchRefreshPayload(UUID userId, String userName, long capturedEpoch) {
        MatchFetchResult matchResult = fetchMatchesFromStorage(userId, capturedEpoch);
        List<LikeCardData> received = fetchReceivedLikesFromStorage(userId);
        List<LikeCardData> sent = fetchSentLikesFromStorage(userId);
        return new RefreshPayload(capturedEpoch, matchResult, received, sent, userName);
    }

    private LoadMorePayload fetchNextPagePayload(UUID userId, long capturedEpoch) {
        try {
            MatchFetchResult result = fetchMatchesFromStorage(userId, capturedEpoch);
            return new LoadMorePayload(capturedEpoch, result);
        } finally {
            isFetchingNextPage.set(false);
        }
    }

    private void performLikeBack(UUID userId, UUID targetUserId) {
        if (!dailyService.canLike(userId)) {
            throw new IllegalStateException("Daily like limit reached");
        }
        if (matchingUseCases != null) {
            matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.ui(userId), targetUserId, Like.Direction.LIKE, true));
            return;
        }
        matchingService.recordLike(Like.create(userId, targetUserId, Like.Direction.LIKE));
    }

    private void performPassOn(UUID userId, UUID targetUserId) {
        if (matchingUseCases != null) {
            matchingUseCases.recordLike(
                    new RecordLikeCommand(UserContext.ui(userId), targetUserId, Like.Direction.PASS, false));
            return;
        }
        matchingService.recordLike(Like.create(userId, targetUserId, Like.Direction.PASS));
    }

    private void performWithdrawLike(UUID likeId) {
        User user = resolveCurrentUser();
        if (matchingUseCases != null && user != null) {
            matchingUseCases.removeLike(new RemoveLikeCommand(UserContext.ui(user.getId()), likeId));
            return;
        }
        matchData.deleteLike(likeId);
    }

    private void executeActionSync(
            String userMessage, ViewModelAsyncScope.ThrowingRunnable action, Runnable onSuccess) {
        setLoadingState(true);
        try {
            action.run();
            onSuccess.run();
        } catch (Exception e) {
            logWarn("{}: {}", userMessage, e.getMessage(), e);
            notifyError(userMessage, e);
        } finally {
            setLoadingState(false);
        }
    }

    private boolean shouldRunAsync() {
        return isFxToolkitAvailable() && Platform.isFxApplicationThread();
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "matches", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
    }

    private record RefreshPayload(
            long epoch,
            MatchFetchResult matchResult,
            List<LikeCardData> received,
            List<LikeCardData> sent,
            String userName) {}

    private record LoadMorePayload(long epoch, MatchFetchResult result) {}

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
