package datingapp.app.cli;

import datingapp.core.AppSession;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared CLI constants and utility methods.
 * Merged from CliConstants + CliUtilities (R-010).
 */
public final class CliSupport {

    private CliSupport() {} // Utility class

    private static final Logger logger = LoggerFactory.getLogger(CliSupport.class);

    // ═══ Display Constants ═══

    public static final String SEPARATOR_LINE = "═══════════════════════════════════════";
    public static final String SECTION_LINE = "  ───────────────────────────────────";

    // Box drawing characters for profile cards
    public static final String BOX_TOP = "┌─────────────────────────────────────────┐";
    public static final String BOX_BOTTOM = "└─────────────────────────────────────────┘";
    public static final String PROFILE_BIO_FORMAT = "│ 📝 {}";

    public static final String INVALID_SELECTION = "\n❌ Invalid selection.\n";
    public static final String INVALID_INPUT = "❌ Invalid input.\n";
    public static final String PLEASE_SELECT_USER = "\n⚠️  Please select or create a user first.\n";
    public static final String CANCELLED = "Cancelled.\n";

    public static final String BLOCK_PREFIX = "Block ";
    public static final String CONFIRM_SUFFIX = "? (y/n): ";

    // Menu dividers (standardize the 39-char width)
    public static final String MENU_DIVIDER = "───────────────────────────────────────";
    public static final String MENU_DIVIDER_WITH_NEWLINES = "\n───────────────────────────────────────\n";

    // Section headers
    public static final String HEADER_BROWSE_CANDIDATES = "--- Browse Candidates ---";
    public static final String HEADER_BLOCK_USER = "\n--- Block a User ---\n";
    public static final String HEADER_REPORT_USER = "\n--- Report a User ---\n";
    public static final String HEADER_BLOCKED_USERS = "\n--- Blocked Users ---\n";
    public static final String HEADER_INTERESTS_HOBBIES = "--- Interests & Hobbies (max 10) ---";
    public static final String HEADER_LIFESTYLE = "--- Lifestyle (optional, helps with matching) ---";
    public static final String HEADER_YOUR_STATISTICS = "         YOUR DATING STATISTICS";
    public static final String HEADER_YOUR_ACHIEVEMENTS = "         🏆 YOUR ACHIEVEMENTS";

    // Action prompts
    public static final String PROMPT_LIKE_PASS_QUIT = "  [L]ike / [P]ass / [Q]uit browsing: ";
    public static final String PROMPT_LIKE_PASS_SKIP = "  [L]ike / [P]ass / [S]kip for now: ";
    public static final String PROMPT_VIEW_UNMATCH_BLOCK = "[V]iew details / [U]nmatch / [B]lock / [Enter] to go back";
    public static final String PROMPT_UNMATCH_BLOCK_BACK = "  (U)nmatch  (B)lock  (Enter to go back)";

    // Feedback messages
    public static final String MSG_STOPPING_BROWSE = "\nStopping browse.\n";

    // Gender selection text
    public static final String GENDER_OPTIONS = "Gender options: 1=MALE, 2=FEMALE, 3=OTHER";
    public static final String INTERESTED_IN_PROMPT =
            "Interested in (comma-separated, e.g., 1,2):\n  1=MALE, 2=FEMALE, 3=OTHER";

    // Stats section headers
    public static final String STATS_ACTIVITY = "  📊 ACTIVITY";
    public static final String STATS_MATCHES = "  💕 MATCHES";
    public static final String STATS_SCORES = "  🎯 YOUR SCORES";
    public static final String STATS_SAFETY = "  ⚠️  SAFETY";

    // ═══ Utility Methods ═══

    /**
     * Validates user input against a set of valid choices (case-insensitive).
     *
     * @param input        The user input to validate
     * @param validChoices Array of valid choices (e.g., "l", "p", "v", "b")
     * @return Optional containing normalized lowercase input if valid, empty otherwise
     */
    public static Optional<String> validateChoice(String input, String... validChoices) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (String valid : validChoices) {
            if (normalized.equals(valid.toLowerCase(Locale.ROOT))) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    /**
     * Executes the action only if a user is logged in to AppSession.
     * Logs "Please select a user first" if not logged in.
     *
     * @param action The action to execute if logged in
     * @return true if action was executed, false if not logged in
     */
    public static boolean requireLogin(Runnable action) {
        if (!AppSession.getInstance().isLoggedIn()) {
            logger.info(PLEASE_SELECT_USER);
            return false;
        }
        action.run();
        return true;
    }
}
