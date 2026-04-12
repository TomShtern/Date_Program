package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.RemoveLikeCommand;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.ui.async.UiThreadDispatcher;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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
public class MatchesViewModel extends BaseViewModel {
    private static final String LIKE_REQUIRED = "like cannot be null";
    private static final String MATCH_REQUIRED = "match cannot be null";
    private static final String REFRESH_FAILURE_MESSAGE = "Failed to refresh matches";
    private static final String LOAD_MORE_FAILURE_MESSAGE = "Failed to load more matches";

    /** Number of matches returned per page from storage. */
    private static final int PAGE_SIZE = 20;

    private final RecommendationService dailyService;
    private final MatchingUseCases matchingUseCases;
    private final SocialUseCases socialUseCases;
    private final MatchListLoader matchListLoader;
    private final RelationshipActionRunner relationshipActionRunner;
    private final AppSession session;
    private final AsyncExecutionMode asyncExecutionMode;
    private final ObservableList<MatchCardData> matches = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesReceived = FXCollections.observableArrayList();
    private final ObservableList<LikeCardData> likesSent = FXCollections.observableArrayList();
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesReceivedCount = new SimpleIntegerProperty(0);
    private final IntegerProperty likesSentCount = new SimpleIntegerProperty(0);
    private final BooleanProperty loadFailed = new SimpleBooleanProperty(false);
    private final StringProperty loadFailureMessage = new SimpleStringProperty("");

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

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    public enum AsyncExecutionMode {
        SYNC,
        ASYNC
    }

    public record Dependencies(
            RecommendationService dailyService,
            MatchingUseCases matchingUseCases,
            ProfileUseCases profileUseCases,
            SocialUseCases socialUseCases,
            AppConfig config,
            AsyncExecutionMode asyncExecutionMode) {

        public Dependencies(
                RecommendationService dailyService,
                MatchingUseCases matchingUseCases,
                ProfileUseCases profileUseCases,
                SocialUseCases socialUseCases,
                AppConfig config) {
            this(dailyService, matchingUseCases, profileUseCases, socialUseCases, config, AsyncExecutionMode.SYNC);
        }

        public Dependencies {
            Objects.requireNonNull(dailyService, "dailyService cannot be null");
            Objects.requireNonNull(matchingUseCases, "matchingUseCases cannot be null");
            Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
            Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
            Objects.requireNonNull(config, "config cannot be null");
            Objects.requireNonNull(asyncExecutionMode, "asyncExecutionMode cannot be null");
        }
    }

    public MatchesViewModel(Dependencies dependencies, AppSession session, UiThreadDispatcher uiDispatcher) {
        super("matches", Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null"));
        Dependencies resolvedDependencies = Objects.requireNonNull(dependencies, "dependencies cannot be null");
        this.dailyService = resolvedDependencies.dailyService();
        this.matchingUseCases = resolvedDependencies.matchingUseCases();
        this.socialUseCases = resolvedDependencies.socialUseCases();
        this.matchListLoader = new MatchListLoader(
                resolvedDependencies.matchingUseCases(),
                resolvedDependencies.profileUseCases(),
                resolvedDependencies.socialUseCases(),
                resolvedDependencies.config());
        this.relationshipActionRunner = new RelationshipActionRunner(asyncScope);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncExecutionMode = resolvedDependencies.asyncExecutionMode();
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
    public void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
    }

    @Override
    protected void onDispose() {
        matches.clear();
        likesReceived.clear();
        likesSent.clear();
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
     * Updates the observable lists with fetched data.
     */
    private void updateObservableLists(
            MatchFetchResult matchResult, List<LikeCardData> received, List<LikeCardData> sent, String userName) {
        clearLoadFailure();
        matches.setAll(matchResult.cards());
        matchCount.set(matches.size());
        totalMatchCount.set(matchResult.totalCount());
        hasMoreMatches.set(matchResult.hasMore());

        likesReceived.setAll(received);
        likesReceivedCount.set(likesReceived.size());

        likesSent.setAll(sent);
        likesSentCount.set(likesSent.size());

        logInfo("Refreshed all matches and likes for {}", userName);
    }

    /**
     * Private result record returned by {@link #fetchMatchesFromUseCases(UUID, long)} so that
     * pagination metadata (total count, has-more flag) can travel alongside the UI card data without
     * an extra use-case round-trip.
     */
    private record MatchFetchResult(List<MatchCardData> cards, int totalCount, boolean hasMore) {}

    private MatchFetchResult fetchMatchesFromUseCases(UUID userId, long expectedEpoch) {
        // Reserve PAGE_SIZE items atomically to prevent concurrent callers from fetching the same page
        // (TOCTOU race). currentMatchOffset is advanced immediately; we adjust it downward below if the
        // use-case call returns fewer items.
        int offset = currentMatchOffset.getAndAdd(PAGE_SIZE);

        MatchListLoader.MatchPageResult pageResult = matchListLoader.loadMatchPage(userId, PAGE_SIZE, offset);
        int actualCount = pageResult.cards().size();
        if (actualCount < PAGE_SIZE && refreshEpoch.get() == expectedEpoch) {
            currentMatchOffset.addAndGet(actualCount - PAGE_SIZE);
        }
        return new MatchFetchResult(pageResult.cards(), pageResult.totalCount(), pageResult.hasMore());
    }

    private List<LikeCardData> fetchReceivedLikesFromUseCases(UUID userId) {
        return matchListLoader.loadReceivedLikes(userId);
    }

    private List<LikeCardData> fetchSentLikesFromUseCases(UUID userId) {
        return matchListLoader.loadSentLikes(userId);
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
        clearLoadFailure();
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
        markLoadFailure(e.getMessage());
        notifyError(LOAD_MORE_FAILURE_MESSAGE, e);
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

        runAction("like back", "Failed to like back", () -> performLikeBack(userId, like.userId()), this::refreshAll);
    }

    public void passOn(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot pass");
            return;
        }

        UUID userId = user.getId();

        runAction("pass on like", "Failed to pass", () -> performPassOn(userId, like.userId()), this::refreshAll);
    }

    public void withdrawLike(LikeCardData like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        if (like.likeId() == null) {
            return;
        }

        runAction(
                "withdraw like", "Failed to withdraw like", () -> performWithdrawLike(like.likeId()), this::refreshAll);
    }

    public void requestFriendZone(MatchCardData match) {
        Objects.requireNonNull(match, MATCH_REQUIRED);
        performRelationshipAction(
                "request friend zone", match.userId(), this::performFriendZoneRequest, this::refreshAll);
    }

    public void gracefulExit(MatchCardData match) {
        Objects.requireNonNull(match, MATCH_REQUIRED);
        performRelationshipAction("graceful exit", match.userId(), this::performGracefulExit, this::refreshAll);
    }

    public void unmatch(MatchCardData match) {
        Objects.requireNonNull(match, MATCH_REQUIRED);
        performRelationshipAction("unmatch", match.userId(), this::performUnmatch, this::refreshAll);
    }

    public void blockMatch(MatchCardData match) {
        Objects.requireNonNull(match, MATCH_REQUIRED);
        performRelationshipAction("block match", match.userId(), this::performBlockUser, this::refreshAll);
    }

    public void reportMatch(MatchCardData match, Report.Reason reason, String description, boolean blockUser) {
        Objects.requireNonNull(match, MATCH_REQUIRED);
        Objects.requireNonNull(reason, "reason cannot be null");
        performRelationshipAction(
                "report match",
                match.userId(),
                targetUserId -> performReportUser(targetUserId, reason, description, blockUser),
                this::refreshAll);
    }

    private void refreshAllAsync(UUID userId, String userName, long capturedEpoch) {
        asyncScope.runLatest(
                "matches-refresh",
                "refresh matches",
                () -> fetchRefreshPayload(userId, userName, capturedEpoch),
                payload -> applyRefreshPayload(payload, capturedEpoch));
    }

    private void refreshAllSync(UUID userId, String userName, long capturedEpoch) {
        setLoadingState(true);
        try {
            RefreshPayload payload = fetchRefreshPayload(userId, userName, capturedEpoch);
            applyRefreshPayload(payload, capturedEpoch);
        } catch (Exception e) {
            logWarn("Failed to refresh matches: {}", e.getMessage(), e);
            markLoadFailure(e.getMessage());
            notifyError(REFRESH_FAILURE_MESSAGE, e);
        } finally {
            if (refreshEpoch.get() == capturedEpoch) {
                setLoadingState(false);
            }
        }
    }

    private RefreshPayload fetchRefreshPayload(UUID userId, String userName, long capturedEpoch) {
        try {
            MatchFetchResult matchResult = fetchMatchesFromUseCases(userId, capturedEpoch);
            List<LikeCardData> received = fetchReceivedLikesFromUseCases(userId);
            List<LikeCardData> sent = fetchSentLikesFromUseCases(userId);
            return RefreshPayload.success(capturedEpoch, matchResult, received, sent, userName);
        } catch (Exception e) {
            return RefreshPayload.failure(capturedEpoch, userName, e);
        }
    }

    private void applyRefreshPayload(RefreshPayload payload, long capturedEpoch) {
        if (capturedEpoch != refreshEpoch.get()) {
            return;
        }
        if (payload.success()) {
            updateObservableLists(payload.matchResult(), payload.received(), payload.sent(), payload.userName());
            return;
        }

        markLoadFailure(payload.failureMessage());
        notifyError(REFRESH_FAILURE_MESSAGE, payload.failureException());
    }

    private LoadMorePayload fetchNextPagePayload(UUID userId, long capturedEpoch) {
        try {
            MatchFetchResult result = fetchMatchesFromUseCases(userId, capturedEpoch);
            return new LoadMorePayload(capturedEpoch, result);
        } finally {
            isFetchingNextPage.set(false);
        }
    }

    private void performLikeBack(UUID userId, UUID targetUserId) {
        if (matchingUseCases == null) {
            throw new IllegalStateException("Matching use cases not configured");
        }
        if (!dailyService.canLike(userId)) {
            throw new IllegalStateException("Daily like limit reached");
        }
        matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.ui(userId), targetUserId, Like.Direction.LIKE, true));
    }

    private void performPassOn(UUID userId, UUID targetUserId) {
        if (matchingUseCases == null) {
            throw new IllegalStateException("Matching use cases not configured");
        }
        matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.ui(userId), targetUserId, Like.Direction.PASS, false));
    }

    private void performWithdrawLike(UUID likeId) {
        if (matchingUseCases == null) {
            throw new IllegalStateException("Matching use cases not configured");
        }
        User user = resolveCurrentUser();
        if (user != null) {
            matchingUseCases.removeLike(new RemoveLikeCommand(UserContext.ui(user.getId()), likeId));
        }
    }

    private void performRelationshipAction(
            String actionName, UUID targetUserId, RelationshipAction action, Runnable onSuccess) {
        User user = resolveCurrentUser();
        if (user == null) {
            logWarn("No current user, cannot {}", actionName);
            return;
        }
        if (socialUseCases == null) {
            notifyError("Relationship actions are not configured", null);
            return;
        }

        runAction(actionName, "Failed to " + actionName, () -> action.run(targetUserId), onSuccess);
    }

    private void performFriendZoneRequest(UUID targetUserId) {
        User user = resolveCurrentUser();
        var result =
                socialUseCases.requestFriendZone(new RelationshipCommand(UserContext.ui(user.getId()), targetUserId));
        ensureSocialSuccess(
                result.success(), result.error() != null ? result.error().message() : null);
    }

    private void performGracefulExit(UUID targetUserId) {
        User user = resolveCurrentUser();
        var result = socialUseCases.gracefulExit(new RelationshipCommand(UserContext.ui(user.getId()), targetUserId));
        ensureSocialSuccess(
                result.success(), result.error() != null ? result.error().message() : null);
    }

    private void performUnmatch(UUID targetUserId) {
        User user = resolveCurrentUser();
        var result = socialUseCases.unmatch(new RelationshipCommand(UserContext.ui(user.getId()), targetUserId));
        ensureSocialSuccess(
                result.success(), result.error() != null ? result.error().message() : null);
    }

    private void performBlockUser(UUID targetUserId) {
        User user = resolveCurrentUser();
        var result = socialUseCases.blockUser(new RelationshipCommand(UserContext.ui(user.getId()), targetUserId));
        ensureSocialSuccess(
                result.success(), result.error() != null ? result.error().message() : null);
    }

    private void performReportUser(UUID targetUserId, Report.Reason reason, String description, boolean blockUser) {
        User user = resolveCurrentUser();
        var result = socialUseCases.reportUser(
                new ReportCommand(UserContext.ui(user.getId()), targetUserId, reason, description, blockUser));
        ensureSocialSuccess(
                result.success(), result.error() != null ? result.error().message() : null);
    }

    private static void ensureSocialSuccess(boolean success, String errorMessage) {
        if (!success) {
            throw new IllegalStateException(errorMessage == null ? "Relationship action failed" : errorMessage);
        }
    }

    @FunctionalInterface
    private interface RelationshipAction {
        void run(UUID targetUserId);
    }

    private void runAction(String taskName, String userMessage, Runnable action, Runnable onSuccess) {
        relationshipActionRunner.run(
                shouldRunAsync(),
                taskName,
                userMessage,
                action,
                onSuccess,
                new RelationshipActionRunner.SyncCallbacks(
                        () -> setLoadingState(true),
                        () -> setLoadingState(false),
                        (message, error) -> logWarn("{}: {}", message, error.getMessage(), error),
                        this::notifyError));
    }

    private boolean shouldRunAsync() {
        return asyncExecutionMode == AsyncExecutionMode.ASYNC;
    }

    public BooleanProperty loadFailedProperty() {
        return loadFailed;
    }

    public StringProperty loadFailureMessageProperty() {
        return loadFailureMessage;
    }

    private void clearLoadFailure() {
        loadFailed.set(false);
        loadFailureMessage.set("");
    }

    private void markLoadFailure(String message) {
        loadFailed.set(true);
        loadFailureMessage.set(message == null ? "" : message);
    }

    private record RefreshPayload(
            long epoch,
            MatchFetchResult matchResult,
            List<LikeCardData> received,
            List<LikeCardData> sent,
            String userName,
            boolean success,
            String failureMessage,
            Exception failureException) {

        private static RefreshPayload success(
                long epoch,
                MatchFetchResult matchResult,
                List<LikeCardData> received,
                List<LikeCardData> sent,
                String userName) {
            return new RefreshPayload(
                    epoch, matchResult, List.copyOf(received), List.copyOf(sent), userName, true, "", null);
        }

        private static RefreshPayload failure(long epoch, String userName, Exception error) {
            String message = error != null ? error.getMessage() : REFRESH_FAILURE_MESSAGE;
            return new RefreshPayload(epoch, null, List.of(), List.of(), userName, false, message, error);
        }
    }

    private record LoadMorePayload(long epoch, MatchFetchResult result) {}

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
            String matchId,
            UUID userId,
            String userName,
            String matchedTimeAgo,
            Instant matchedAt,
            Integer compatibilityScore,
            String compatibilityLabel) {}

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
