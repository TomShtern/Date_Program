package datingapp.storage.jdbi;

import datingapp.core.UndoState;
import datingapp.core.UserInteractions.Like;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * Adapter that implements UndoState.Storage using JDBI.
 * Bridges the domain interface to the JDBI SQL object.
 */
public class JdbiUndoStorageAdapter implements UndoState.Storage {

    private final Jdbi jdbi;

    public JdbiUndoStorageAdapter(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void save(UndoState state) {
        jdbi.useExtension(JdbiUndoStorage.class, storage -> {
            Like like = state.like();
            storage.upsert(
                    state.userId(),
                    like.id(),
                    like.whoLikes(),
                    like.whoGotLiked(),
                    like.direction().name(),
                    like.createdAt(),
                    state.matchId(),
                    state.expiresAt());
        });
    }

    @Override
    public Optional<UndoState> findByUserId(UUID userId) {
        return jdbi.withExtension(JdbiUndoStorage.class, storage -> Optional.ofNullable(storage.findByUserId(userId)));
    }

    @Override
    public boolean delete(UUID userId) {
        return jdbi.withExtension(JdbiUndoStorage.class, storage -> storage.deleteByUserId(userId) > 0);
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbi.withExtension(JdbiUndoStorage.class, storage -> storage.deleteExpired(now));
    }

    @Override
    public List<UndoState> findAll() {
        return jdbi.withExtension(JdbiUndoStorage.class, JdbiUndoStorage::findAll);
    }
}
