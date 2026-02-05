package datingapp.app.api;

import datingapp.core.Match;
import datingapp.core.MatchingService;
import datingapp.core.ServiceRegistry;
import datingapp.core.User;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API routes for matching operations.
 *
 * <p>Handles listing matches, liking users, and passing on users.
 */
public class MatchRoutes {

    private final UserStorage userStorage;
    private final MatchStorage matchStorage;
    private final MatchingService matchingService;

    public MatchRoutes(ServiceRegistry services) {
        Objects.requireNonNull(services, "services cannot be null");
        this.userStorage = services.getUserStorage();
        this.matchStorage = services.getMatchStorage();
        this.matchingService = services.getMatchingService();
    }

    /** GET /api/users/{id}/matches - Get all matches for a user. */
    public void getMatches(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        validateUserExists(userId);

        List<MatchSummary> matches = matchStorage.getAllMatchesFor(userId).stream()
                .map(m -> toSummary(m, userId))
                .toList();
        ctx.json(matches);
    }

    /** POST /api/users/{id}/like/{targetId} - Like a user. */
    public void likeUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam("targetId"));

        validateUserExists(userId);
        validateUserExists(targetId);

        Like like = Like.create(userId, targetId, Like.Direction.LIKE);
        Optional<Match> match = matchingService.recordLike(like);

        if (match.isPresent()) {
            ctx.status(201);
            ctx.json(new LikeResponse(true, "It's a match!", MatchSummary.from(match.get(), userId)));
        } else {
            ctx.status(200);
            ctx.json(new LikeResponse(false, "Like recorded", null));
        }
    }

    /** POST /api/users/{id}/pass/{targetId} - Pass on a user. */
    public void passUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam("targetId"));

        validateUserExists(userId);
        validateUserExists(targetId);

        Like pass = Like.create(userId, targetId, Like.Direction.PASS);
        matchingService.recordLike(pass);
        ctx.status(200);
        ctx.json(new PassResponse("Passed"));
    }

    private void validateUserExists(UUID userId) {
        if (userStorage.get(userId) == null) {
            throw new NotFoundResponse("User not found: " + userId);
        }
    }

    private MatchSummary toSummary(Match match, UUID currentUserId) {
        UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
        User otherUser = userStorage.get(otherUserId);
        String otherUserName = otherUser != null ? otherUser.getName() : "Unknown";
        return new MatchSummary(
                match.getId(), otherUserId, otherUserName, match.getState().name(), match.getCreatedAt());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, ex);
        }
    }

    /** Match summary for API responses. */
    public record MatchSummary(
            String matchId, UUID otherUserId, String otherUserName, String state, Instant createdAt) {
        public static MatchSummary from(Match match, UUID currentUserId) {
            UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
            return new MatchSummary(
                    match.getId(), otherUserId, "Unknown", match.getState().name(), match.getCreatedAt());
        }
    }

    /** Response for like action. */
    public record LikeResponse(boolean isMatch, String message, MatchSummary match) {}

    /** Response for pass action. */
    public record PassResponse(String message) {}
}
