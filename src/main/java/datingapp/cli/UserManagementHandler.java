package datingapp.cli;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datingapp.core.User;
import datingapp.core.UserStorage;

public class UserManagementHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementHandler.class);
    private final UserStorage userStorage;
    private final UserSession userSession;
    private final InputReader inputReader;

    public UserManagementHandler(UserStorage userStorage, UserSession userSession, InputReader inputReader) {
        this.userStorage = userStorage;
        this.userSession = userSession;
        this.inputReader = inputReader;
    }

    public void createUser() {
        logger.info("\n--- Create New User ---\n");

        String name = inputReader.readLine("Enter your name: ");
        if (name.isBlank()) {
            logger.info("❌ Name cannot be empty.\n");
            return;
        }

        User user = new User(UUID.randomUUID(), name);
        userStorage.save(user);
        userSession.setCurrentUser(user);

        logger.info("\n✅ User created! ID: {}", user.getId());
        logger.info("   Status: {} (Complete your profile to become ACTIVE)\n", user.getState());
    }

    public void selectUser() {
        logger.info("\n--- Select User ---\n");

        List<User> users = userStorage.findAll();
        if (users.isEmpty()) {
            logger.info("No users found. Create one first!\n");
            return;
        }

        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            logger.info("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = inputReader.readLine("\nSelect user number (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= users.size()) {
                if (idx != -1)
                    logger.info("\n❌ Invalid selection.\n");
                return;
            }
            userSession.setCurrentUser(users.get(idx));
            logger.info("\n✅ Selected: {}\n", userSession.getCurrentUser().getName());
        } catch (NumberFormatException e) {
            logger.info("❌ Invalid input.\n");
        }
    }
}
