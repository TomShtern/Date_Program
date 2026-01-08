package datingapp.core;

import java.util.Optional;

/**
 * Business logic for processing likes and creating matches.
 * Pure Java - no framework dependencies.
 */
public class MatchingService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private SessionService sessionService; // Optional (Phase 0.5b)

    public MatchingService(LikeStorage likeStorage, MatchStorage matchStorage) {
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
    }

    /**
     * Enhanced constructor with session tracking (Phase 0.5b).
     */
    public MatchingService(LikeStorage likeStorage, MatchStorage matchStorage, SessionService sessionService) {
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
        this.sessionService = sessionService;
    }

    /**
     * Records a like action and checks for mutual match.
     * If session tracking is enabled, also updates the user's session.
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
                    like.whoLikes(),
                    like.direction(),
                    false // We don't know if it's a match yet
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
            // Create match
            Match match = Match.create(like.whoLikes(), like.whoGotLiked());

            // Check if match already exists (shouldn't happen, but defensive)
            if (!matchStorage.exists(match.getId())) {
                try {
                    matchStorage.save(match);

                    // Update session with match count (Phase 0.5b)
                    if (sessionService != null) {
                        sessionService.recordMatch(like.whoLikes());
                    }

                    return Optional.of(match);
                } catch (Exception e) {
                    // Race condition: match was created by another thread just now
                    // Return the existing match if possible, or simple empty (idempotent)
                    return matchStorage.get(match.getId());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Get the session service (for UI access to session info).
     */
    public Optional<SessionService> getSessionService() {
        return Optional.ofNullable(sessionService);
    }
}
