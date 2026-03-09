package datingapp.core.profile;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/** Sanitizes untrusted text input by stripping all HTML markup. */
public final class SanitizerUtils {

    static final PolicyFactory STRICT_TEXT = new HtmlPolicyBuilder().toFactory();

    private SanitizerUtils() {
        // Utility class
    }

    public static String sanitize(String input) {
        return input == null ? null : STRICT_TEXT.sanitize(input);
    }
}
