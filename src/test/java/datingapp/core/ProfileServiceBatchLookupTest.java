package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileService Batch Lookup")
class ProfileServiceBatchLookupTest {

    @Test
    @DisplayName("getUsersByIds returns matching users and skips missing IDs")
    void getUsersByIdsReturnsMatchingUsersOnly() {
        TestStorages.Users userStorage = new TestStorages.Users();
        var service = new ProfileService(userStorage);

        User alice = new User(UUID.randomUUID(), "Alice");
        User bob = new User(UUID.randomUUID(), "Bob");
        UUID missing = UUID.randomUUID();
        userStorage.save(alice);
        userStorage.save(bob);

        var found = service.getUsersByIds(Set.of(alice.getId(), bob.getId(), missing));

        assertEquals(2, found.size());
        assertEquals("Alice", found.get(alice.getId()).getName());
        assertEquals("Bob", found.get(bob.getId()).getName());
        assertTrue(!found.containsKey(missing));
    }

    @Test
    @DisplayName("getUsersByIds returns empty map for empty input")
    void getUsersByIdsReturnsEmptyForEmptyInput() {
        TestStorages.Users userStorage = new TestStorages.Users();
        var service = new ProfileService(userStorage);

        var found = service.getUsersByIds(Set.of());

        assertTrue(found.isEmpty());
    }
}
