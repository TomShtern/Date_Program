package datingapp.core.matching;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchingService.class);

    private static final String LIKE_REQUIRED = "like cannot be null";

    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final UserStorage userStorage;
    private ActivityMetricsService activityMetricsService; // Optional
    private UndoService undoService; // Optional
    private RecommendationService dailyService; // Optional

    /** Constructor with all dependencies (optional dependencies may be null). */
    public MatchingService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage,
            ActivityMetricsService activityMetricsService,
            UndoService undoService,
            RecommendationService dailyService) {
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.activityMetricsService = activityMetricsService;
        this.undoService = undoService;
        this.dailyService = dailyService;
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

        public MatchingService build() {
            return new MatchingService(
                    interactionStorage,
                    trustSafetyStorage,
                    userStorage,
                    activityMetricsService,
                    undoService,
                    dailyService);
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
        // Check if already exists
        if (interactionStorage.exists(like.whoLikes(), like.whoGotLiked())) {
            return Optional.empty(); // Already recorded
        }

        // Save the like
        interactionStorage.save(like);

        // If it's a PASS, no match possible
        if (like.direction() == Like.Direction.PASS) {
            if (activityMetricsService != null) {
                activityMetricsService.recordSwipe(like.whoLikes(), like.direction(), false);
            }
            return Optional.empty();
        }

        Optional<Match> matchResult = Optional.empty();

        // Check for mutual LIKE
        if (interactionStorage.mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
            // Create match with deterministic ID (allows idempotent saves)
            Match match = Match.create(like.whoLikes(), like.whoGotLiked());

            try {
                // Save using upsert semantics (MERGE) - idempotent operation
                interactionStorage.save(match);

                matchResult = Optional.of(match);
            } catch (RuntimeException ex) {
                // Handle storage conflicts (duplicate key, race condition)
                // JdbiException extends RuntimeException
                if (logger.isWarnEnabled()) {
                    logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());
                }
                // Guard fallback query (EH-004 fix)
                try {
                    matchResult = interactionStorage
                            .get(match.getId())
                            .filter(existing -> existing.getState() == MatchState.ACTIVE);
                } catch (RuntimeException fallbackEx) {
                    if (logger.isErrorEnabled()) {
                        logger.error("Fallback match lookup also failed for {}", match.getId(), fallbackEx);
                    }
                    // matchResult remains empty — safe to continue
                }
            }
        }

        if (activityMetricsService != null) {
            activityMetricsService.recordSwipe(like.whoLikes(), like.direction(), matchResult.isPresent());
        }

        return matchResult;
    }

    /** Get the session service (for UI access to session info). */
    public Optional<ActivityMetricsService> getActivityMetricsService() {
        return Optional.ofNullable(activityMetricsService);
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
        if (dailyService == null || undoService == null) {
            return SwipeResult.configError("dailyService and undoService required for processSwipe");
        }
        Objects.requireNonNull(currentUser, "currentUser cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");
        if (liked && !dailyService.canLike(currentUser.getId())) {
            return SwipeResult.dailyLimitReached();
        }

        Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = recordLike(like);
        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));

        if (match.isPresent()) {
            return SwipeResult.matched(match.get(), like);
        }
        return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
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

        Set<UUID> matched = new HashSet<>();
        for (Match match : interactionStorage.getAllMatchesFor(currentUserId)) {
            matched.add(otherUserId(match, currentUserId));
        }

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

    private static UUID otherUserId(Match match, UUID currentUserId) {
        return match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
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

        public static SwipeResult configError(String reason) {
            Objects.requireNonNull(reason, "reason cannot be null");
            return new SwipeResult(false, false, null, null, reason);
        }
    }
}
