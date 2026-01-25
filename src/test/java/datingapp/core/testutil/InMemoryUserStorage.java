package datingapp.core.testutil;

import datingapp.core.User;
import java.util.*;

/**
 * In-memory User.Storage for testing. Provides test helper methods.
 */
public class InMemoryUserStorage implements User.Storage {
    private final Map<UUID, User> users = new HashMap<>();

    @Override
    public void save(User user) {
        users.put(user.getId(), user);
    }

    @Override
    public User get(UUID id) {
        return users.get(id);
    }

    @Override
    public List<User> findActive() {
        return users.values().stream()
                .filter(u -> u.getState() == User.State.ACTIVE)
                .toList();
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    // === Test Helpers ===

    /** Clears all users */
    public void clear() {
        users.clear();
    }

    /** Returns number of users stored */
    public int size() {
        return users.size();
    }
}
