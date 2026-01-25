package datingapp.core;

import datingapp.core.Match.MatchStorage;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.LikeStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Returns users who already liked the current user, but are still pending response. */
public class LikerBrowserService {

    private final LikeStorage likeStorage;
    private final UserStorage userStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;

    public LikerBrowserService(
            LikeStorage likeStorage, UserStorage userStorage, MatchStorage matchStorage, BlockStorage blockStorage) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
    }

    /**
     * Returns all users that liked {@code currentUserId} and the current user has not responded to.
     */
    public List<User> findPendingLikers(UUID currentUserId) {
        return findPendingLikersWithTimes(currentUserId).stream()
                .map(PendingLiker::user)
                .toList();
    }

    /** Same as {@link #findPendingLikers(UUID)}, but also includes when the like happened. */
    public List<PendingLiker> findPendingLikersWithTimes(UUID currentUserId) {
        Objects.requireNonNull(currentUserId, "currentUserId cannot be null");

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
        for (var entry : likeTimes.entrySet()) {
            UUID likerId = entry.getKey();
            User liker = userStorage.get(likerId);
            if (excluded.contains(likerId) || liker == null || liker.getState() != User.State.ACTIVE) {
                continue;
            }

            java.time.Instant likedAt = entry.getValue();
            result.add(new PendingLiker(liker, likedAt));
        }

        result.sort(java.util.Comparator.comparing(PendingLiker::likedAt).reversed());
        return result;
    }

    private static UUID otherUserId(Match match, UUID currentUserId) {
        return match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
    }

    /** Represents a user who liked the current user but hasn't been responded to yet. */
    public record PendingLiker(User user, java.time.Instant likedAt) {}
}
