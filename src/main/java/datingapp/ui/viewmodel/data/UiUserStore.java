package datingapp.ui.viewmodel.data;

import datingapp.core.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UI-layer adapter interface for user data access.
 * Decouples ViewModels from core storage interfaces so that
 * {@code datingapp.core.storage.*} imports are not needed in the ViewModel package.
 */
public interface UiUserStore {

    /** Returns all users regardless of state. */
    List<User> findAll();

    /** Persists the given user (insert or update). */
    void save(User user);

    /** Batch lookup of users by their IDs. */
    Map<UUID, User> findByIds(Set<UUID> ids);
}
