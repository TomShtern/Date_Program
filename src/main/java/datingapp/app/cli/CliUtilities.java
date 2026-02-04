package datingapp.app.cli;

import datingapp.core.AppSession;
import java.util.Locale;
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

    private static final Logger logger = LoggerFactory.getLogger(CliUtilities.class);

    /**
     * Executes the action only if a user is logged in to AppSession.
     * Logs "Please select a user first" if not logged in.
     *
     * @param action The action to execute if logged in
     * @return true if action was executed, false if not logged in
     */
    public static boolean requireLogin(Runnable action) {
        if (!AppSession.getInstance().isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return false;
        }
        action.run();
        return true;
    }
}
