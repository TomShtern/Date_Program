package datingapp.ui.viewmodel.data;

import datingapp.core.Match;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges the UI layer to the core match/like/block storage interfaces.
 *
 * <p>This adapter is the only place in the ViewModel package that holds references to
 * {@code core.storage} types. ViewModels depend on {@link UiMatchDataAccess} instead.
 */
public final class StorageUiMatchDataAccess implements UiMatchDataAccess {

    private final MatchStorage matchStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;

    public StorageUiMatchDataAccess(MatchStorage matchStorage, LikeStorage likeStorage, BlockStorage blockStorage) {
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = Objects.requireNonNull(blockStorage, "blockStorage cannot be null");
    }

    @Override
    public List<Match> getActiveMatchesFor(UUID userId) {
        return matchStorage.getActiveMatchesFor(userId);
    }

    @Override
    public List<Match> getAllMatchesFor(UUID userId) {
        return matchStorage.getAllMatchesFor(userId);
    }

    @Override
    public Set<UUID> getBlockedUserIds(UUID userId) {
        return blockStorage.getBlockedUserIds(userId);
    }

    @Override
    public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
        return likeStorage.getLikedOrPassedUserIds(userId);
    }

    @Override
    public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
        return likeStorage.getLike(fromUserId, toUserId);
    }

    @Override
    public void deleteLike(UUID likeId) {
        likeStorage.delete(likeId);
    }
}
