package datingapp.core;

import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for processing likes, creating matches, and browsing pending
 * likers. Pure Java - no
 * framework dependencies.
 */
public class MatchingService {

    private static final String LIKE_REQUIRED = "like cannot be null";

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private SessionService sessionService; // Optional (Phase 0.5b)
    private UndoService undoService; // Optional
    private DailyService dailyService; // Optional

    /** Basic constructor for minimal usage. */
    public MatchingService(LikeStorage likeStorage, MatchStorage matchStorage) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.userStorage = null;
        this.blockStorage = null;
    }

    /** Full constructor with all dependencies for liker browsing functionality. */
    public MatchingService(
            LikeStorage likeStorage, MatchStorage matchStorage, UserStorage userStorage, BlockStorage blockStorage) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
    }

    /** Enhanced constructor with session tracking (Phase 0.5b). */
    public MatchingService(
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            SessionService sessionService) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
        this.sessionService = sessionService;
        this.undoService = null;
        this.dailyService = null;
    }

    /** Enhanced constructor with swipe processing capabilities. */
    public MatchingService(
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            SessionService sessionService,
            UndoService undoService,
            DailyService dailyService) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
        this.sessionService = sessionService;
        this.undoService = Objects.requireNonNull(undoService, "undoService cannot be null");
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
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
        // Check if already exists
        if (likeStorage.exists(like.whoLikes(), like.whoGotLiked())) {
            return Optional.empty(); // Already recorded
        }

        // Record in session if tracking is enabled (Phase 0.5b)
        if (sessionService != null) {
            // Record the swipe for session tracking (result ignored - we don't block likes)
            sessionService.recordSwipe(
                    like.whoLikes(), like.direction(), false // We don't know if it's a match yet
                    );
        }

        // Save the like
        likeStorage.save(like);

        // If it's a PASS, no match possible
        if (like.direction() == Like.Direction.PASS) {
            return Optional.empty();
        }

        // Check for mutual LIKE
        if (likeStorage.mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
            // Create match with deterministic ID (allows idempotent saves)
            Match match = Match.create(like.whoLikes(), like.whoGotLiked());

            try {
                // Save using upsert semantics (MERGE) - idempotent operation
                matchStorage.save(match);

                // Update session with match count (Phase 0.5b)
                if (sessionService != null) {
                    sessionService.recordMatch(like.whoLikes());
                }

                return Optional.of(match);
            } catch (RuntimeException _) {
                // Handle storage conflicts (duplicate key, race condition)
                // JdbiException extends RuntimeException
                return matchStorage.get(match.getId());
            }
        }

        return Optional.empty();
    }

    /** Get the session service (for UI access to session info). */
    public Optional<SessionService> getSessionService() {
        return Optional.ofNullable(sessionService);
    }

    /**
     * Processes a swipe action (like or pass) for the current user on a candidate.
     * Checks daily limits, records the interaction, and returns the result.
     *
     * @param currentUser The user performing the swipe
     * @param candidate The candidate being swiped on
     * @param liked True if liking, false if passing
     * @return SwipeResult containing success status and match information
     */
    public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
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

        if (userStorage == null || blockStorage == null) {
            throw new IllegalStateException(
                    "userStorage and blockStorage required for liker browsing. Use full constructor.");
        }

        Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUserId);
        Set<UUID> blocked = blockStorage.getBlockedUserIds(currentUserId);

        Set<UUID> matched = new HashSet<>();
        for (Match match : matchStorage.getAllMatchesFor(currentUserId)) {
            matched.add(otherUserId(match, currentUserId));
        }

        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blocked);
        excluded.addAll(matched);

        var likeTimes = likeStorage.getLikeTimesForUsersWhoLiked(currentUserId);

        List<PendingLiker> result = new ArrayList<>();
        for (var entry : likeTimes) {
            UUID likerId = entry.getKey();
            User liker = userStorage.get(likerId);
            if (excluded.contains(likerId) || liker == null || liker.getState() != UserState.ACTIVE) {
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
    public record PendingLiker(User user, Instant likedAt) {
        public PendingLiker {
            Objects.requireNonNull(user, "user cannot be null");
            Objects.requireNonNull(likedAt, "likedAt cannot be null");
        }
    }

    /**
     * Result of a swipe action containing success status, match information, and
     * user-friendly message.
     */
    public record SwipeResult(boolean success, boolean matched, Match match, Like like, String message) {
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
    }
}
