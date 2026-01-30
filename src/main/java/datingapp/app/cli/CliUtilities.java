package datingapp.app.cli;

import datingapp.core.User;
import java.util.Locale;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidation of CLI utility classes. Contains session management and input
 * reading helpers for
 * the command-line interface.
 */
public final class CliUtilities {
    private CliUtilities() {} // Utility class - prevent instantiation

    /**
     * Validates user input against a set of valid choices (case-insensitive).
     *
     * @param input        The user input to validate
     * @param validChoices Array of valid choices (e.g., "l", "p", "v", "b")
     * @return Optional containing normalized lowercase input if valid, empty
     *         otherwise
     */
    public static java.util.Optional<String> validateChoice(String input, String... validChoices) {
        if (input == null || input.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (String valid : validChoices) {
            if (normalized.equals(valid.toLowerCase(Locale.ROOT))) {
                return java.util.Optional.of(normalized);
            }
        }
        return java.util.Optional.empty();
    }

    /** Tracks the currently logged-in user for the CLI session. */
    public static class UserSession {
        private static final Logger logger = LoggerFactory.getLogger(UserSession.class);
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

        /**
         * Executes the action only if a user is logged in.
         * Logs "Please select a user first" if not logged in.
         *
         * @param action The action to execute if logged in
         * @return true if action was executed, false if not logged in
         */
        public boolean requireLogin(Runnable action) {
            if (!isLoggedIn()) {
                logger.info(CliConstants.PLEASE_SELECT_USER);
                return false;
            }
            action.run();
            return true;
        }
    }

    /**
     * Handles user input from the console, providing prompt display and line
     * reading.
     */
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
