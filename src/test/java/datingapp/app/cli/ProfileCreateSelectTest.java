package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import datingapp.core.User;
import datingapp.core.ValidationService;
import datingapp.core.storage.UserStorage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for user creation and selection functionality in ProfileHandler
 * using in-memory mocks.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileCreateSelectTest {

    private InMemoryUserStorage userStorage;
    private CliUtilities.UserSession userSession;
    private ProfileHandler handler;

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        userStorage = new InMemoryUserStorage();
        userSession = new CliUtilities.UserSession();
    }

    private ProfileHandler createHandler(String input) {
        CliUtilities.InputReader inputReader = new CliUtilities.InputReader(new Scanner(new StringReader(input)));
        // ProfilePreviewService and AchievementService can be null for create/select
        // tests
        // since they aren't used by these methods
        return new ProfileHandler(userStorage, null, null, new ValidationService(), userSession, inputReader);
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
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

        @ParameterizedTest(name = "Rejects invalid name: \"{0}\"")
        @ValueSource(strings = {"", "   "})
        @DisplayName("Rejects empty and blank names")
        void rejectsInvalidNames(String invalidName) {
            handler = createHandler(invalidName + "\n");

            handler.createUser();

            assertEquals(0, userStorage.findAll().size(), "Should not create user with invalid name");
            assertNull(userSession.getCurrentUser(), "Should not set current user");
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

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
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

        @ParameterizedTest(name = "Invalid selection: \"{0}\" - {1}")
        @MethodSource("datingapp.app.cli.ProfileCreateSelectTest#invalidSelectionsProvider")
        @DisplayName("Handles invalid selections")
        void handlesInvalidSelections(String input, String description) {
            User alice = createUser("Alice");
            userStorage.save(alice);

            handler = createHandler(input + "\n");

            handler.selectUser();

            assertNull(userSession.getCurrentUser(), "Should not select user with " + description);
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via @MethodSource reflection
    static Stream<Arguments> invalidSelectionsProvider() {
        return Stream.of(
                Arguments.of("0", "cancelled selection"),
                Arguments.of("5", "index too high"),
                Arguments.of("-1", "negative index"),
                Arguments.of("abc", "non-numeric input"));
    }

    // === Helper Methods ===

    private User createUser(String name) {
        return new User(UUID.randomUUID(), name);
    }

    // === In-Memory Mock Storage ===

    private static class InMemoryUserStorage implements UserStorage {
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
