package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.storage.PageData;
import datingapp.core.storage.TrustSafetyStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchingService {

    private static final String LIKE_REQUIRED = "like cannot be null";
    private static final String MATCH_REQUIRED = "match cannot be null";
    private static final String BLOCKED_SWIPE_MESSAGE = "Cannot swipe on a blocked user.";
    private static final Object IN_FLIGHT_SENTINEL = new Object();

    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final OperationalUserStorage userStorage;
    private final Map<String, Object> swipeInFlight = new ConcurrentHashMap<>();
    private final Optional<ActivityMetricsService> activityMetricsService;
    private final UndoService undoService;
    private final RecommendationService dailyService;
    private final CandidateFinder candidateFinder;

    /** Constructor with all dependencies except CandidateFinder; ActivityMetricsService is optional. */
    public MatchingService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            OperationalUserStorage userStorage,
            ActivityMetricsService activityMetricsService,
            UndoService undoService,
            RecommendationService dailyService) {
        this(
                interactionStorage,
                trustSafetyStorage,
                userStorage,
                activityMetricsService,
                undoService,
                dailyService,
                defaultCandidateFinder(interactionStorage, trustSafetyStorage, userStorage));
    }

    public MatchingService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            OperationalUserStorage userStorage,
            ActivityMetricsService activityMetricsService,
            UndoService undoService,
            RecommendationService dailyService,
            CandidateFinder candidateFinder) {
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.undoService = Objects.requireNonNull(undoService, "undoService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
        this.activityMetricsService = Optional.ofNullable(activityMetricsService);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InteractionStorage interactionStorage;
        private TrustSafetyStorage trustSafetyStorage;
        private OperationalUserStorage userStorage;
        private ActivityMetricsService activityMetricsService;
        private UndoService undoService;
        private RecommendationService dailyService;
        private CandidateFinder candidateFinder;

        public Builder interactionStorage(InteractionStorage storage) {
            this.interactionStorage = storage;
            return this;
        }

        public Builder trustSafetyStorage(TrustSafetyStorage storage) {
            this.trustSafetyStorage = storage;
            return this;
        }

        public Builder userStorage(OperationalUserStorage storage) {
            this.userStorage = storage;
            return this;
        }

        public Builder activityMetricsService(ActivityMetricsService service) {
            this.activityMetricsService = service;
            return this;
        }

        public Builder undoService(UndoService service) {
            this.undoService = service;
            return this;
        }

        public Builder dailyService(RecommendationService service) {
            this.dailyService = service;
            return this;
        }

        public Builder candidateFinder(CandidateFinder candidateFinder) {
            this.candidateFinder = candidateFinder;
            return this;
        }

        public MatchingService build() {
            return new MatchingService(
                    interactionStorage,
                    trustSafetyStorage,
                    userStorage,
                    activityMetricsService,
                    undoService,
                    dailyService,
                    candidateFinder);
        }
    }

    /**
     * Records a like action and checks for mutual match.
     *
     * @param like The like to record
     * @return the primitive persistence outcome for the like attempt
     */
    public RecordLikeOutcome recordLike(Like like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        final RecordLikeOutcome[] resultHolder = new RecordLikeOutcome[1];
        userStorage.executeWithUserLock(
                like.whoLikes(), () -> storeRecordLikeOutcome(resultHolder, recordLikeWithinLock(like)));
        RecordLikeOutcome outcome = resultHolder[0];
        if (outcome.persisted()) {
            invalidateCandidateCaches(like.whoLikes(), like.whoGotLiked());
        }
        return outcome;
    }

    private RecordLikeOutcome recordLikeWithinLock(Like like) {
        Optional<String> eligibilityError = validateRecordLikeEligibility(like);
        if (eligibilityError.isPresent()) {
            return RecordLikeOutcome.rejected(eligibilityError.orElseThrow());
        }
        InteractionStorage.LikeMatchWriteResult writeResult = persistLikeAndMaybeCreateMatch(like);
        if (!writeResult.likePersisted()) {
            Like persistedLike = interactionStorage
                    .getLike(like.whoLikes(), like.whoGotLiked())
                    .orElseThrow(() -> new IllegalStateException("Duplicate like was not found in storage"));
            return RecordLikeOutcome.duplicate(persistedLike);
        }
        return RecordLikeOutcome.persisted(like, writeResult.createdMatch());
    }

    private InteractionStorage.LikeMatchWriteResult persistLikeAndMaybeCreateMatch(Like like) {
        return interactionStorage.saveLikeAndMaybeCreateMatch(like);
    }

    private Optional<String> validateRecordLikeEligibility(Like like) {
        Optional<String> eligibilityError = validatePersistedSwipeEligibility(like.whoLikes(), like.whoGotLiked());
        if (eligibilityError.isPresent()) {
            return eligibilityError;
        }
        return switch (like.direction()) {
            case LIKE ->
                dailyService.canLike(like.whoLikes()) ? Optional.empty() : Optional.of("Daily like limit reached.");
            case SUPER_LIKE ->
                dailyService.canSuperLike(like.whoLikes())
                        ? Optional.empty()
                        : Optional.of("Daily super-like limit reached.");
            case PASS ->
                dailyService.canPass(like.whoLikes()) ? Optional.empty() : Optional.of("Daily pass limit reached.");
        };
    }

    public List<Match> getMatchesForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return interactionStorage.getAllMatchesFor(userId);
    }

    /**
     * Returns a bounded page of all matches (active + ended) for the given user,
     * newest first.
     *
     * <p>
     * This is the paginated alternative to {@link #getMatchesForUser(UUID)} — use
     * this
     * in any context where the user may have a large number of matches, such as the
     * REST API
     * and future admin tooling.
     *
     * @param userId the user whose matches to page through
     * @param offset zero-based start index
     * @param limit  maximum number of matches per page
     * @return a {@link PageData} wrapper with items, totalCount, and pagination
     *         helpers
     */
    public PageData<Match> getPageOfMatchesForUser(UUID userId, int offset, int limit) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return interactionStorage.getPageOfMatchesFor(userId, offset, limit);
    }

    /** Get the session service (for UI access to session info). */
    public Optional<ActivityMetricsService> getActivityMetricsService() {
        return activityMetricsService;
    }

    /**
     * Processes a swipe action (like or pass) for the current user on a candidate.
     * Checks daily limits, records the interaction, and returns the result.
     *
     * @param currentUser The user performing the swipe
     * @param candidate   The candidate being swiped on
     * @param liked       True if liking, false if passing
     * @return SwipeResult containing success status and match information
     */
    public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
        return processSwipe(currentUser, candidate, liked, false);
    }

    public SwipeResult processSwipe(User currentUser, User candidate, boolean liked, boolean superLike) {
        Objects.requireNonNull(currentUser, "currentUser cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");

        Optional<String> eligibilityError = evaluateSwipeEligibility(currentUser, candidate);
        if (eligibilityError.isPresent()) {
            return SwipeResult.configError(eligibilityError.orElseThrow());
        }

        if (superLike && !liked) {
            return SwipeResult.configError("Super like requires liked=true");
        }

        String inFlightKey = currentUser.getId() + ">" + candidate.getId();
        if (swipeInFlight.putIfAbsent(inFlightKey, IN_FLIGHT_SENTINEL) != null) {
            return SwipeResult.configError("A swipe for this candidate is already in progress");
        }
        try {
            final SwipeResult[] resultHolder = new SwipeResult[1];
            userStorage.executeWithUserLock(
                    currentUser.getId(),
                    () -> storeSwipeResult(
                            resultHolder, processSwipeWithinLock(currentUser, candidate, liked, superLike)));
            return resultHolder[0];
        } finally {
            swipeInFlight.remove(inFlightKey);
        }
    }

    private static void storeRecordLikeOutcome(RecordLikeOutcome[] resultHolder, RecordLikeOutcome result) {
        resultHolder[0] = result;
    }

    private static void storeSwipeResult(SwipeResult[] resultHolder, SwipeResult result) {
        resultHolder[0] = result;
    }

    private SwipeResult processSwipeWithinLock(User currentUser, User candidate, boolean liked, boolean superLike) {
        Optional<String> persistedEligibilityError =
                validatePersistedSwipeEligibility(currentUser.getId(), candidate.getId());
        if (persistedEligibilityError.isPresent()) {
            return SwipeResult.configError(persistedEligibilityError.orElseThrow());
        }

        if (superLike && !dailyService.canSuperLike(currentUser.getId())) {
            return SwipeResult.dailySuperLikeLimitReached();
        }

        if (!superLike && liked && !dailyService.canLike(currentUser.getId())) {
            return SwipeResult.dailyLimitReached();
        }

        if (!liked && !dailyService.canPass(currentUser.getId())) {
            return SwipeResult.passLimitReached();
        }

        Like.Direction direction = resolveDirection(liked, superLike);
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        InteractionStorage.LikeMatchWriteResult writeResult = persistLikeAndMaybeCreateMatch(like);
        if (!writeResult.likePersisted()) {
            return SwipeResult.alreadySwiped();
        }

        Optional<Match> match = writeResult.createdMatch();
        // Keep undo state inside the user lock so the most recent swipe remains serialized per user.
        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));

        if (match.isPresent()) {
            return SwipeResult.matched(match.get(), like);
        }
        return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
    }

    private Optional<String> validatePersistedSwipeEligibility(UUID currentUserId, UUID candidateId) {
        if (currentUserId.equals(candidateId)) {
            return Optional.of("Cannot swipe on yourself.");
        }

        Optional<User> persistedCurrentUser = userStorage.get(currentUserId);
        if (persistedCurrentUser.isEmpty() || persistedCurrentUser.get().getState() != UserState.ACTIVE) {
            return Optional.of("Current user must be ACTIVE to swipe.");
        }

        Optional<User> persistedCandidate = userStorage.get(candidateId);
        if (persistedCandidate.isEmpty() || persistedCandidate.get().getState() != UserState.ACTIVE) {
            return Optional.of("Candidate must be ACTIVE to receive swipes.");
        }

        return evaluateSwipeEligibility(persistedCurrentUser.orElseThrow(), persistedCandidate.orElseThrow());
    }

    private Optional<String> evaluateSwipeEligibility(User currentUser, User candidate) {
        if (currentUser.getState() != UserState.ACTIVE) {
            return Optional.of("Current user must be ACTIVE to swipe.");
        }

        if (candidate.getState() != UserState.ACTIVE) {
            return Optional.of("Candidate must be ACTIVE to receive swipes.");
        }

        if (currentUser.getId().equals(candidate.getId())) {
            return Optional.of("Cannot swipe on yourself.");
        }

        if (trustSafetyStorage.isBlocked(currentUser.getId(), candidate.getId())) {
            return Optional.of(BLOCKED_SWIPE_MESSAGE);
        }

        return Optional.empty();
    }

    private static Like.Direction resolveDirection(boolean liked, boolean superLike) {
        if (!liked) {
            return Like.Direction.PASS;
        }
        return superLike ? Like.Direction.SUPER_LIKE : Like.Direction.LIKE;
    }

    private void invalidateCandidateCaches(UUID firstUserId, UUID secondUserId) {
        candidateFinder.invalidateCacheFor(firstUserId);
        candidateFinder.invalidateCacheFor(secondUserId);
    }

    private static CandidateFinder defaultCandidateFinder(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            OperationalUserStorage userStorage) {
        return new CandidateFinder(
                Objects.requireNonNull(userStorage, "userStorage cannot be null"),
                Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null"),
                Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null"),
                AppClock.clock().getZone());
    }

    // ================== Liker Browser Methods ==================

    /**
     * Returns all users that liked {@code currentUserId} and the current user has
     * not responded to.
     * Requires userStorage and blockStorage to be initialized.
     */
    public List<User> findPendingLikers(UUID currentUserId) {
        return findPendingLikersWithTimes(currentUserId).stream()
                .map(PendingLiker::user)
                .toList();
    }

    /**
     * Same as {@link #findPendingLikers(UUID)}, but also includes when the like
     * happened. Requires
     * userStorage and blockStorage to be initialized.
     */
    public List<PendingLiker> findPendingLikersWithTimes(UUID currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId cannot be null");

        Set<UUID> alreadyInteracted = interactionStorage.getLikedOrPassedUserIds(currentUserId);
        Set<UUID> blocked = trustSafetyStorage.getBlockedUserIds(currentUserId);
        Set<UUID> matched = interactionStorage.getMatchedCounterpartIds(currentUserId);

        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blocked);
        excluded.addAll(matched);

        var likeTimes = interactionStorage.getLikeTimesForUsersWhoLiked(currentUserId);

        // Batch-load all potential likers in one query
        Set<UUID> likerIds = new HashSet<>();
        for (var entry : likeTimes) {
            UUID likerId = entry.getKey();
            if (!excluded.contains(likerId)) {
                likerIds.add(likerId);
            }
        }
        Map<UUID, User> likerUsers = userStorage.findByIds(likerIds);

        List<PendingLiker> result = new ArrayList<>();
        for (var entry : likeTimes) {
            UUID likerId = entry.getKey();
            User liker = likerUsers.get(likerId);
            if (liker == null || liker.getState() != UserState.ACTIVE) {
                continue;
            }

            Instant likedAt = entry.getValue();
            result.add(new PendingLiker(liker, likedAt));
        }

        result.sort(Comparator.comparing(PendingLiker::likedAt).reversed());
        return result;
    }

    /**
     * Represents a user who liked the current user but hasn't been responded to
     * yet.
     */
    public static record PendingLiker(User user, Instant likedAt) {
        public PendingLiker {
            Objects.requireNonNull(user, "user cannot be null");
            Objects.requireNonNull(likedAt, "likedAt cannot be null");
        }
    }

    /**
     * Outcome of the primitive recordLike path.
     *
     * <p>The like is present for persisted writes and idempotent duplicate reads;
     * it is empty only when the request was rejected before persistence.
     */
    public static record RecordLikeOutcome(
            boolean persisted, Optional<Like> like, Optional<Match> match, Optional<String> rejectionMessage) {
        public RecordLikeOutcome {
            Objects.requireNonNull(like, LIKE_REQUIRED);
            Objects.requireNonNull(match, MATCH_REQUIRED);
            Objects.requireNonNull(rejectionMessage, "rejectionMessage cannot be null");
        }

        public static RecordLikeOutcome persisted(Like like, Optional<Match> match) {
            return new RecordLikeOutcome(
                    true,
                    Optional.of(Objects.requireNonNull(like, LIKE_REQUIRED)),
                    Objects.requireNonNull(match, MATCH_REQUIRED),
                    Optional.empty());
        }

        public static RecordLikeOutcome duplicate(Like like) {
            return new RecordLikeOutcome(
                    false,
                    Optional.of(Objects.requireNonNull(like, LIKE_REQUIRED)),
                    Optional.empty(),
                    Optional.empty());
        }

        public static RecordLikeOutcome rejected(String message) {
            return new RecordLikeOutcome(
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(Objects.requireNonNull(message, "message cannot be null")));
        }

        public boolean rejected() {
            return rejectionMessage.isPresent();
        }
    }

    /**
     * Result of a swipe action containing success status, match information, and
     * user-friendly message.
     */
    public static record SwipeResult(boolean success, boolean matched, Match match, Like like, String message) {
        public SwipeResult {
            Objects.requireNonNull(message, "message cannot be null");
        }

        public static SwipeResult matched(Match m, Like l) {
            Objects.requireNonNull(m, MATCH_REQUIRED);
            Objects.requireNonNull(l, LIKE_REQUIRED);
            return new SwipeResult(true, true, m, l, "It's a match!");
        }

        public static SwipeResult liked(Like l) {
            Objects.requireNonNull(l, LIKE_REQUIRED);
            return new SwipeResult(true, false, null, l, "Liked!");
        }

        public static SwipeResult passed(Like l) {
            Objects.requireNonNull(l, LIKE_REQUIRED);
            return new SwipeResult(true, false, null, l, "Passed.");
        }

        public static SwipeResult alreadySwiped() {
            return new SwipeResult(true, false, null, null, "Already swiped.");
        }

        public static SwipeResult dailyLimitReached() {
            return new SwipeResult(false, false, null, null, "Daily like limit reached.");
        }

        public static SwipeResult dailySuperLikeLimitReached() {
            return new SwipeResult(false, false, null, null, "Daily super-like limit reached.");
        }

        public static SwipeResult passLimitReached() {
            return new SwipeResult(false, false, null, null, "Daily pass limit reached.");
        }

        public static SwipeResult configError(String reason) {
            Objects.requireNonNull(reason, "reason cannot be null");
            return new SwipeResult(false, false, null, null, reason);
        }
    }
}
