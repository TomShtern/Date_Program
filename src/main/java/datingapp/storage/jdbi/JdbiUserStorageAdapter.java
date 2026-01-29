package datingapp.storage.jdbi;

import datingapp.core.User;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * Adapter that implements UserStorage by delegating to JdbiUserStorage.
 * Handles the UserBindingHelper wrapping for save operations.
 */
public class JdbiUserStorageAdapter implements UserStorage {

    private final Jdbi jdbi;

    public JdbiUserStorageAdapter(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void save(User user) {
        jdbi.useExtension(JdbiUserStorage.class, dao -> dao.save(new UserBindingHelper(user)));
    }

    @Override
    public User get(UUID id) {
        return jdbi.withExtension(JdbiUserStorage.class, dao -> dao.get(id));
    }

    @Override
    public List<User> findActive() {
        return jdbi.withExtension(JdbiUserStorage.class, JdbiUserStorage::findActive);
    }

    @Override
    public List<User> findAll() {
        return jdbi.withExtension(JdbiUserStorage.class, JdbiUserStorage::findAll);
    }

    @Override
    public void delete(UUID id) {
        jdbi.useExtension(JdbiUserStorage.class, dao -> dao.delete(id));
    }
}
