package datingapp.storage.jdbi;

import datingapp.core.User;
import datingapp.core.User.ProfileNote;
import datingapp.core.storage.UserStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    public Map<UUID, User> findByIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // JDBI handles collection binding with <userIds> syntax
        return jdbi.withHandle(handle -> {
            List<User> users = handle.createQuery(
                            "SELECT " + JdbiUserStorage.ALL_COLUMNS + " FROM users WHERE id IN (<userIds>)")
                    .bindList("userIds", new ArrayList<>(ids))
                    .map(new JdbiUserStorage.Mapper())
                    .list();

            Map<UUID, User> result = new HashMap<>();
            for (User user : users) {
                result.put(user.getId(), user);
            }
            return result;
        });
    }

    @Override
    public void delete(UUID id) {
        jdbi.useExtension(JdbiUserStorage.class, dao -> dao.delete(id));
    }

    @Override
    public void saveProfileNote(ProfileNote note) {
        jdbi.useExtension(JdbiUserStorage.class, dao -> dao.saveProfileNote(note));
    }

    @Override
    public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
        return jdbi.withExtension(JdbiUserStorage.class, dao -> dao.getProfileNote(authorId, subjectId));
    }

    @Override
    public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
        return jdbi.withExtension(JdbiUserStorage.class, dao -> dao.getProfileNotesByAuthor(authorId));
    }

    @Override
    public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
        return jdbi.withExtension(JdbiUserStorage.class, dao -> dao.deleteProfileNote(authorId, subjectId));
    }
}
