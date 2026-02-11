package datingapp.storage.jdbi;

import datingapp.core.model.Match;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.storage.InteractionStorage;
import datingapp.storage.DatabaseManager.StorageException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * JDBI adapter that implements {@link InteractionStorage} by delegating to the internal
 * {@link JdbiLikeStorage} and {@link JdbiMatchStorage} SQL Object DAOs, plus inlined transaction
 * support for atomic undo operations.
 */
public final class JdbiInteractionStorageAdapter implements InteractionStorage {

    private final Jdbi jdbi;
    private final JdbiLikeStorage likeDao;
    private final JdbiMatchStorage matchDao;

    public JdbiInteractionStorageAdapter(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.likeDao = jdbi.onDemand(JdbiLikeStorage.class);
        this.matchDao = jdbi.onDemand(JdbiMatchStorage.class);
    }

    // ═══ Like Operations ═══

    @Override
    public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
        return likeDao.getLike(fromUserId, toUserId);
    }

    @Override
    public void save(Like like) {
        likeDao.save(like);
    }

    @Override
    public boolean exists(UUID from, UUID to) {
        return likeDao.exists(from, to);
    }

    @Override
    public boolean mutualLikeExists(UUID a, UUID b) {
        return likeDao.mutualLikeExists(a, b);
    }

    @Override
    public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
        return likeDao.getLikedOrPassedUserIds(userId);
    }

    @Override
    public Set<UUID> getUserIdsWhoLiked(UUID userId) {
        return likeDao.getUserIdsWhoLiked(userId);
    }

    @Override
    public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
        return likeDao.getLikeTimesForUsersWhoLiked(userId);
    }

    @Override
    public int countByDirection(UUID userId, Like.Direction direction) {
        return likeDao.countByDirection(userId, direction);
    }

    @Override
    public int countReceivedByDirection(UUID userId, Like.Direction direction) {
        return likeDao.countReceivedByDirection(userId, direction);
    }

    @Override
    public int countMutualLikes(UUID userId) {
        return likeDao.countMutualLikes(userId);
    }

    @Override
    public int countLikesToday(UUID userId, Instant startOfDay) {
        return likeDao.countLikesToday(userId, startOfDay);
    }

    @Override
    public int countPassesToday(UUID userId, Instant startOfDay) {
        return likeDao.countPassesToday(userId, startOfDay);
    }

    @Override
    public void delete(UUID likeId) {
        likeDao.delete(likeId);
    }

    // ═══ Match Operations ═══

    @Override
    public void save(Match match) {
        matchDao.save(match);
    }

    @Override
    public void update(Match match) {
        matchDao.update(match);
    }

    @Override
    public Optional<Match> get(String matchId) {
        return matchDao.get(matchId);
    }

    @Override
    public boolean exists(String matchId) {
        return matchDao.exists(matchId);
    }

    @Override
    public List<Match> getActiveMatchesFor(UUID userId) {
        return matchDao.getActiveMatchesFor(userId);
    }

    @Override
    public List<Match> getAllMatchesFor(UUID userId) {
        return matchDao.getAllMatchesFor(userId);
    }

    @Override
    public void delete(String matchId) {
        matchDao.delete(matchId);
    }

    @Override
    public int purgeDeletedBefore(Instant threshold) {
        return matchDao.purgeDeletedBefore(threshold);
    }

    // ═══ Transaction Operations ═══

    @Override
    public boolean atomicUndoDelete(UUID likeId, String matchId) {
        try {
            return jdbi.inTransaction(handle -> {
                int likesDeleted = handle.createUpdate("DELETE FROM likes WHERE id = :id")
                        .bind("id", likeId)
                        .execute();

                if (likesDeleted == 0) {
                    return false;
                }

                if (matchId != null) {
                    handle.createUpdate("DELETE FROM matches WHERE id = :id")
                            .bind("id", matchId)
                            .execute();
                }

                return true;
            });
        } catch (Exception e) {
            throw new StorageException("Atomic undo delete failed", e);
        }
    }
}
