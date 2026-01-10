package datingapp.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.User;
import datingapp.core.UserStorage;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for UserManagementHandler using in-memory mocks. */
class UserManagementHandlerTest {

  private InMemoryUserStorage userStorage;
  private UserSession userSession;
  private UserManagementHandler handler;

  @BeforeEach
  void setUp() {
    userStorage = new InMemoryUserStorage();
    userSession = new UserSession();
  }

  @Nested
  @DisplayName("Create User")
  class CreateUser {

    @Test
    @DisplayName("Creates user with valid name")
    void createsUserWithValidName() {
      InputReader inputReader = createInputReader("Alice\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

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
      InputReader inputReader = createInputReader("\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.createUser();

      assertEquals(0, userStorage.findAll().size(), "Should not create user with empty name");
      assertNull(userSession.getCurrentUser(), "Should not set current user");
    }

    @Test
    @DisplayName("Rejects blank name")
    void rejectsBlankName() {
      InputReader inputReader = createInputReader("   \n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.createUser();

      assertEquals(0, userStorage.findAll().size(), "Should not create user with blank name");
      assertNull(userSession.getCurrentUser());
    }

    @Test
    @DisplayName("Generates unique UUID for each user")
    void generatesUniqueIds() {
      InputReader inputReader1 = createInputReader("Alice\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader1);
      handler.createUser();
      UUID id1 = userSession.getCurrentUser().getId();

      InputReader inputReader2 = createInputReader("Bob\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader2);
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
      InputReader inputReader = createInputReader("1\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

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

      InputReader inputReader = createInputReader("1\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

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

      InputReader inputReader = createInputReader("2\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.selectUser();

      assertEquals(bob, userSession.getCurrentUser(), "Should select second user");
    }

    @Test
    @DisplayName("Handles cancel selection (0)")
    void handlesCancelSelection() {
      User alice = createUser("Alice");
      userStorage.save(alice);

      InputReader inputReader = createInputReader("0\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.selectUser();

      assertNull(userSession.getCurrentUser(), "Should not select user when cancelled");
    }

    @Test
    @DisplayName("Handles invalid selection (too high)")
    void handlesInvalidSelectionTooHigh() {
      User alice = createUser("Alice");
      userStorage.save(alice);

      InputReader inputReader = createInputReader("5\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.selectUser();

      assertNull(userSession.getCurrentUser(), "Should not select user with invalid index");
    }

    @Test
    @DisplayName("Handles invalid selection (negative)")
    void handlesInvalidSelectionNegative() {
      User alice = createUser("Alice");
      userStorage.save(alice);

      InputReader inputReader = createInputReader("-1\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.selectUser();

      assertNull(userSession.getCurrentUser(), "Should not select user with negative index");
    }

    @Test
    @DisplayName("Handles non-numeric input")
    void handlesNonNumericInput() {
      User alice = createUser("Alice");
      userStorage.save(alice);

      InputReader inputReader = createInputReader("abc\n");
      handler = new UserManagementHandler(userStorage, userSession, inputReader);

      handler.selectUser();

      assertNull(userSession.getCurrentUser(), "Should not select user with invalid input");
    }
  }

  // === Helper Methods ===

  private User createUser(String name) {
    return new User(UUID.randomUUID(), name);
  }

  private InputReader createInputReader(String input) {
    return new InputReader(new Scanner(new StringReader(input)));
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
      return users.values().stream().filter(u -> u.getState() == User.State.ACTIVE).toList();
    }

    @Override
    public List<User> findAll() {
      return new ArrayList<>(users.values());
    }
  }
}
