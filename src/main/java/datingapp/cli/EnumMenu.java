package datingapp.cli;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic utility for displaying enum options and parsing user selection.
 * Reduces repetitive switch statements in CLI handlers.
 */
public final class EnumMenu {

    private static final Logger logger = LoggerFactory.getLogger(EnumMenu.class);

    private EnumMenu() {} // Utility class

    /**
     * Prompts user to select a single value from an enum.
     *
     * @param <E>       Enum type
     * @param reader    Input reader
     * @param enumClass The enum class
     * @param prompt    The prompt to display
     * @param allowSkip If true, adds "0=Skip" option
     * @return Selected value, or null if skipped/invalid
     */
    public static <E extends Enum<E>> E prompt(
            CliUtilities.InputReader reader, Class<E> enumClass, String prompt, boolean allowSkip) {

        E[] values = enumClass.getEnumConstants();

        if (logger.isInfoEnabled()) {
            logger.info("\n{}", prompt);
            for (int i = 0; i < values.length; i++) {
                logger.info("  {}. {}", i + 1, getDisplayName(values[i]));
            }
            if (allowSkip) {
                logger.info("  0. Skip");
            }
        }

        String input = reader.readLine("Your choice: ");
        try {
            int choice = Integer.parseInt(input.trim());
            if (choice == 0 && allowSkip) {
                return null;
            }
            if (choice >= 1 && choice <= values.length) {
                return values[choice - 1];
            }
        } catch (NumberFormatException ignored) {
            logger.debug("Invalid numeric selection", ignored);
        }

        logger.info("⚠️ Invalid selection, skipping.");
        return null;
    }

    /**
     * Prompts for multiple selections (comma-separated).
     * Used for dealbreakers where user can accept multiple values.
     *
     * @param <E>       Enum type
     * @param reader    Input reader
     * @param enumClass The enum class
     * @param prompt    The prompt to display
     * @return Set of selected values (may be empty if user chooses "0" or enters
     *         invalid input)
     */
    public static <E extends Enum<E>> Set<E> promptMultiple(
            CliUtilities.InputReader reader, Class<E> enumClass, String prompt) {

        E[] values = enumClass.getEnumConstants();

        if (logger.isInfoEnabled()) {
            logger.info("\n{} (comma-separated, e.g., 1,2,3)", prompt);
            for (int i = 0; i < values.length; i++) {
                logger.info("  {}. {}", i + 1, getDisplayName(values[i]));
            }
            logger.info("  0. Clear/None");
        }

        String input = reader.readLine("Your choices: ");
        if ("0".equals(input.trim())) {
            return EnumSet.noneOf(enumClass);
        }

        Set<E> result = EnumSet.noneOf(enumClass);
        for (String part : input.split(",")) {
            try {
                int choice = Integer.parseInt(part.trim());
                if (choice >= 1 && choice <= values.length) {
                    result.add(values[choice - 1]);
                }
            } catch (NumberFormatException ignored) {
                logger.debug("Skipping invalid selection entry", ignored);
            }
        }
        return result;
    }

    /**
     * Gets display name for an enum value.
     * Tries getDisplayName() method first, falls back to formatted name.
     *
     * @param <E>   Enum type
     * @param value The enum value
     * @return Display-friendly string
     */
    private static <E extends Enum<E>> String getDisplayName(E value) {
        try {
            var method = value.getClass().getMethod("getDisplayName");
            return (String) method.invoke(value);
        } catch (ReflectiveOperationException e) {
            // Fallback: convert ENUM_NAME to "Enum name"
            String name = value.name().replace("_", " ").toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }
}
