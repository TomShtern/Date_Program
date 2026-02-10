package datingapp.ui.viewmodel.data;

import datingapp.core.User;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges the UI layer to the core {@link UserStorage} interface.
 *
 * <p>This adapter is the only place in the ViewModel package that holds a reference to
 * a {@code core.storage} type. ViewModels depend on {@link UiUserStore} instead.
 */
public final class StorageUiUserStore implements UiUserStore {

    private final UserStorage userStorage;

    public StorageUiUserStore(UserStorage userStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
    }

    @Override
    public List<User> findAll() {
        return userStorage.findAll();
    }

    @Override
    public void save(User user) {
        userStorage.save(user);
    }

    @Override
    public Map<UUID, User> findByIds(Set<UUID> ids) {
        return userStorage.findByIds(ids);
    }
}
