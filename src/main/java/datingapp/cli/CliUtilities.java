package datingapp.cli;

import datingapp.core.User;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidation of CLI utility classes. Contains session management and input reading helpers for
 * the command-line interface.
 */
public final class CliUtilities {
    private CliUtilities() {} // Utility class - prevent instantiation

    /** Tracks the currently logged-in user for the CLI session. */
    public static class UserSession {
        private User currentUser;

        public User getCurrentUser() {
            return currentUser;
        }

        public void setCurrentUser(User currentUser) {
            this.currentUser = currentUser;
        }

        public boolean isLoggedIn() {
            return currentUser != null;
        }

        public boolean isActive() {
            return currentUser != null && currentUser.getState() == User.State.ACTIVE;
        }
    }

    /** Handles user input from the console, providing prompt display and line reading. */
    public static class InputReader {
        private static final Logger logger = LoggerFactory.getLogger(InputReader.class);
        private final Scanner scanner;

        /** Creates a new InputReader with the given scanner. */
        public InputReader(Scanner scanner) {
            this.scanner = scanner;
        }

        /**
         * Displays a prompt and reads a line of input.
         *
         * @param prompt the prompt to display to the user
         * @return the trimmed user input, or empty string if no input available
         */
        public String readLine(String prompt) {
            logger.info(prompt);
            if (scanner.hasNextLine()) {
                return scanner.nextLine().trim();
            }
            return "";
        }
    }
}
