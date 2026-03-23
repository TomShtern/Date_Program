package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.profile.SanitizerUtils;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("SanitizerUtils")
class SanitizerUtilsTest {

    @ParameterizedTest(name = "sanitize strips markup for {0}")
    @MethodSource("strictSanitizeCases")
    void sanitize_stripsMarkup(String input, String expected) {
        if (expected == null) {
            assertNull(SanitizerUtils.sanitize(input));
            return;
        }

        assertEquals(expected, SanitizerUtils.sanitize(input));
    }

    @ParameterizedTest(name = "sanitizeMessage preserves allowed formatting for {0}")
    @MethodSource("messageSanitizeCases")
    void sanitizeMessage_preservesAllowedFormatting(String input, String expected) {
        if (expected == null) {
            assertNull(SanitizerUtils.sanitizeMessage(input));
            return;
        }

        assertEquals(expected, SanitizerUtils.sanitizeMessage(input));
    }

    static Stream<Arguments> strictSanitizeCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("This is plain text with no HTML.", "This is plain text with no HTML."),
                Arguments.of("Hello <b>world</b> <script>alert('xss')</script>", "Hello world "),
                Arguments.of("<script>alert('xss')</script>", ""),
                Arguments.of("Check this: <iframe src='malicious.com'></iframe>", "Check this: "));
    }

    static Stream<Arguments> messageSanitizeCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("This is plain text with no HTML.", "This is plain text with no HTML."),
                Arguments.of("This is <b>bold</b> text", "This is <b>bold</b> text"),
                Arguments.of("This is <i>italic</i> text", "This is <i>italic</i> text"),
                Arguments.of("This is <em>emphasized</em> text", "This is <em>emphasized</em> text"),
                Arguments.of("This is <strong>strong</strong> text", "This is <strong>strong</strong> text"),
                Arguments.of("This is <u>underlined</u> text", "This is <u>underlined</u> text"),
                Arguments.of("Hello <script>alert('xss')</script> there", "Hello  there"),
                Arguments.of("<div>Check this: <iframe src='malicious'></iframe></div>", "Check this: "),
                Arguments.of(
                        "This is <b>bold</b> <script>xss</script> and <i>italic</i>",
                        "This is <b>bold</b>  and <i>italic</i>"),
                Arguments.of("<b onclick=\"alert('xss')\">Click me</b>", "<b>Click me</b>"),
                Arguments.of("<b>safe<script>alert('xss')</script></b>", "<b>safe</b>"),
                Arguments.of("Look <style>body{background:url(javascript:alert(1))}</style> here", "Look  here"),
                Arguments.of("<a href=\"javascript:alert(1)\" onclick=\"alert(2)\">Click</a>", "Click"));
    }

    @ParameterizedTest(name = "sanitizeMessage strips malformed or unsafe payloads for {0}")
    @MethodSource("unsafeMessagePayloads")
    void sanitizeMessage_stripsUnsafePayloads(String input) {
        String sanitized = SanitizerUtils.sanitizeMessage(input);

        assertTrue(sanitized.contains("Broken") || sanitized.contains("Click") || sanitized.contains("Here"));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("style="));
        assertFalse(sanitized.contains("javascript:"));
        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("<style"));
    }

    static Stream<Arguments> unsafeMessagePayloads() {
        return Stream.of(
                Arguments.of("<b>Broken <i>markup</b></i>"),
                Arguments.of("<span style=\"color:red\" onclick=\"alert(1)\">Here</span>"));
    }
}
