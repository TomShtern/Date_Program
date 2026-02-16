package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TextUtil")
class TextUtilTest {

    @Nested
    @DisplayName("renderProgressBar()")
    class RenderProgressBar {

        @Test
        @DisplayName("renders empty bar for 0%")
        void rendersEmptyBarForZero() {
            String bar = TextUtil.renderProgressBar(0, 10);
            assertEquals("[----------] 0%", bar);
        }

        @Test
        @DisplayName("renders full bar for 100%")
        void rendersFullBarFor100() {
            String bar = TextUtil.renderProgressBar(100, 10);
            assertEquals("[##########] 100%", bar);
        }

        @Test
        @DisplayName("renders partial bar for 50%")
        void rendersPartialBarFor50() {
            String bar = TextUtil.renderProgressBar(50, 10);
            assertEquals("[#####-----] 50%", bar);
        }

        @Test
        @DisplayName("renders Unicode progress bar correctly")
        void rendersUnicodeProgressBar() {
            assertEquals("█████", TextUtil.renderProgressBar(1.0, 5));
            assertEquals("░░░░░", TextUtil.renderProgressBar(0.0, 5));
            assertEquals("██░░░", TextUtil.renderProgressBar(0.4, 5));
        }
    }
}
