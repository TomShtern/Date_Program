package datingapp.core.matching;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.PageData;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
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
    private static final Object IN_FLIGHT_SENTINEL = new Object();

    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final UserStorage userStorage;
    private final Map<String, Object> swipeInFlight = new ConcurrentHashMap<>();
    private final Optional<ActivityMetricsService> activityMetricsService;
    private final UndoService undoService;
    private final RecommendationService dailyService;
    private final CandidateFinder candidateFinder;

    /** Constructor with all dependencies except CandidateFinder and ActivityMetricsService. */
    public MatchingService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage,
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
                null);
    }

    public MatchingService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage,
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
        private UserStorage userStorage;
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

        public Builder userStorage(UserStorage storage) {
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
     * Records a like action and checks for mutual match. If session tracking is
     * enabled, also updates
     * the user's session.
     *
     * @param like The like to record
     * @return The created Match if mutual like exists, empty otherwise
     */
    public Optional<Match> recordLike(Like like) {
        Objects.requireNonNull(like, LIKE_REQUIRED);
        InteractionStorage.LikeMatchWriteResult writeResult = interactionStorage.saveLikeAndMaybeCreateMatch(like);
        if (writeResult.createdMatch().isPresent()) {
            activityMetricsService.ifPresent(svc -> svc.recordSwipe(like.whoLikes(), like.direction(), true));
        }
        return writeResult.createdMatch();
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

        if (superLike && !liked) {
            return SwipeResult.configError("Super like requires liked=true");
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

        String inFlightKey = currentUser.getId() + ">" + candidate.getId();
        if (swipeInFlight.putIfAbsent(inFlightKey, IN_FLIGHT_SENTINEL) != null) {
            return SwipeResult.configError("A swipe for this candidate is already in progress");
        }
        try {
            Like.Direction direction = resolveDirection(liked, superLike);
            Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
            Optional<Match> match = recordLike(like);
            undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
            invalidateCandidateCaches(currentUser.getId(), candidate.getId());

            if (match.isPresent()) {
                return SwipeResult.matched(match.get(), like);
            }
            return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
        } finally {
            swipeInFlight.remove(inFlightKey);
        }
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
     * Result of a swipe action containing success status, match information, and
     * user-friendly message.
     */
    public static record SwipeResult(boolean success, boolean matched, Match match, Like like, String message) {
        public SwipeResult {
            Objects.requireNonNull(message, "message cannot be null");
        }

        public static SwipeResult matched(Match m, Like l) {
            Objects.requireNonNull(m, "match cannot be null");
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
