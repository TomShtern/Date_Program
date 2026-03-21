package datingapp.core.profile;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/** Sanitizes untrusted text input. */
public final class SanitizerUtils {

    /** Policy that strips all HTML markup (for profile fields). */
    static final PolicyFactory STRICT_TEXT = new HtmlPolicyBuilder().toFactory();

    /** Policy that allows safe formatting tags for messages. */
    static final PolicyFactory MESSAGE_TEXT =
            new HtmlPolicyBuilder().allowElements("b", "i", "em", "strong", "u").toFactory();

    private SanitizerUtils() {
        // Utility class
    }

    /**
     * Sanitizes profile field input by stripping all HTML markup.
     *
     * @param input the input string
     * @return sanitized string with all HTML removed, or null if input is null
     */
    public static String sanitize(String input) {
        return input == null ? null : STRICT_TEXT.sanitize(input);
    }

    /**
     * Sanitizes message content by allowing safe formatting tags (b, i, em, strong, u)
     * while stripping all script tags and dangerous markup.
     *
     * @param input the message content
     * @return sanitized string with safe tags preserved, dangerous tags stripped, or null if input is null
     */
    public static String sanitizeMessage(String input) {
        return input == null ? null : MESSAGE_TEXT.sanitize(input);
    }
}
