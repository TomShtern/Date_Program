package datingapp.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.User;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for user creation and selection functionality in ProfileHandler using in-memory mocks.
 */
@SuppressWarnings("unused")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileCreateSelectTest {

    private InMemoryUserStorage userStorage;
    private CliUtilities.UserSession userSession;
    private ProfileHandler handler;

    @BeforeEach
    void setUp() {
        userStorage = new InMemoryUserStorage();
        userSession = new CliUtilities.UserSession();
    }

    private ProfileHandler createHandler(String input) {
        CliUtilities.InputReader inputReader = new CliUtilities.InputReader(new Scanner(new StringReader(input)));
        // ProfilePreviewService and AchievementService can be null for create/select tests
        // since they aren't used by these methods
        return new ProfileHandler(userStorage, null, null, userSession, inputReader);
    }

    @Nested
    @DisplayName("Create User")
    class CreateUser {

        @Test
        @DisplayName("Creates user with valid name")
        void createsUserWithValidName() {
            handler = createHandler("Alice\n");

            handler.createUser();

            assertEquals(1, userStorage.findAll().size(), "Should have 1 user");
            User created = userStorage.findAll().get(0);
            assertEquals("Alice", created.getName());
            assertEquals(User.State.INCOMPLETE, created.getState());
            assertEquals(created, userSession.getCurrentUser(), "Should set as current user");
        }

        @Test
        @DisplayName("Rejects empty name")
        void rejectsEmptyName() {
            handler = createHandler("\n");

            handler.createUser();

            assertEquals(0, userStorage.findAll().size(), "Should not create user with empty name");
            assertNull(userSession.getCurrentUser(), "Should not set current user");
        }

        @Test
        @DisplayName("Rejects blank name")
        void rejectsBlankName() {
            handler = createHandler("   \n");

            handler.createUser();

            assertEquals(0, userStorage.findAll().size(), "Should not create user with blank name");
            assertNull(userSession.getCurrentUser());
        }

        @Test
        @DisplayName("Generates unique UUID for each user")
        void generatesUniqueIds() {
            handler = createHandler("Alice\n");
            handler.createUser();
            UUID id1 = userSession.getCurrentUser().getId();

            handler = createHandler("Bob\n");
            handler.createUser();
            UUID id2 = userSession.getCurrentUser().getId();

            assertNotEquals(id1, id2, "Each user should have unique ID");
        }
    }

    @Nested
    @DisplayName("Select User")
    class SelectUser {

        @Test
        @DisplayName("Handles empty user list")
        void handlesEmptyUserList() {
            handler = createHandler("1\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user from empty list");
        }

        @Test
        @DisplayName("Selects first user")
        void selectsFirstUser() {
            User alice = createUser("Alice");
            User bob = createUser("Bob");
            userStorage.save(alice);
            userStorage.save(bob);

            handler = createHandler("1\n");

            handler.selectUser();

            assertEquals(alice, userSession.getCurrentUser(), "Should select first user");
        }

        @Test
        @DisplayName("Selects second user")
        void selectsSecondUser() {
            User alice = createUser("Alice");
            User bob = createUser("Bob");
            userStorage.save(alice);
            userStorage.save(bob);

            handler = createHandler("2\n");

            handler.selectUser();

            assertEquals(bob, userSession.getCurrentUser(), "Should select second user");
        }

        @Test
        @DisplayName("Handles cancel selection (0)")
        void handlesCancelSelection() {
            User alice = createUser("Alice");
            userStorage.save(alice);

            handler = createHandler("0\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user when cancelled");
        }

        @Test
        @DisplayName("Handles invalid selection (too high)")
        void handlesInvalidSelectionTooHigh() {
            User alice = createUser("Alice");
            userStorage.save(alice);

            handler = createHandler("5\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user with invalid index");
        }

        @Test
        @DisplayName("Handles invalid selection (negative)")
        void handlesInvalidSelectionNegative() {
            User alice = createUser("Alice");
            userStorage.save(alice);

            handler = createHandler("-1\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user with negative index");
        }

        @Test
        @DisplayName("Handles non-numeric input")
        void handlesNonNumericInput() {
            User alice = createUser("Alice");
            userStorage.save(alice);

            handler = createHandler("abc\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user with invalid input");
        }
    }

    // === Helper Methods ===

    private User createUser(String name) {
        return new User(UUID.randomUUID(), name);
    }

    // === In-Memory Mock Storage ===

    private static class InMemoryUserStorage implements User.Storage {
        private final Map<UUID, User> users = new LinkedHashMap<>();

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

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }
    }
}
