package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.shared.CliTextAndInput.EnumMenu;
import datingapp.app.cli.shared.CliTextAndInput.InputReader;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("EnumMenu")
class EnumMenuTest {

    private enum SampleOption {
        FIRST,
        SECOND,
        THIRD
    }

    private InputReader readerFor(String input) {
        return new InputReader(new Scanner(new StringReader(input)));
    }

    @Nested
    @DisplayName("prompt")
    class PromptTests {

        @Test
        @DisplayName("returns selected value when valid input")
        void returnsSelectedValue() {
            InputReader reader = readerFor("2\n");

            Optional<SampleOption> result = EnumMenu.prompt(reader, SampleOption.class, "Pick:", false);

            assertEquals(Optional.of(SampleOption.SECOND), result);
        }

        @Test
        @DisplayName("returns empty when skip selected")
        void returnsEmptyOnSkip() {
            InputReader reader = readerFor("0\n");

            Optional<SampleOption> result = EnumMenu.prompt(reader, SampleOption.class, "Pick:", true);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty on invalid input")
        void returnsEmptyOnInvalidInput() {
            InputReader reader = readerFor("abc\n");

            Optional<SampleOption> result = EnumMenu.prompt(reader, SampleOption.class, "Pick:", true);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("promptMultiple")
    class PromptMultipleTests {

        @Test
        @DisplayName("returns empty set when cleared")
        void returnsEmptySetOnClear() {
            InputReader reader = readerFor("0\n");

            Set<SampleOption> result = EnumMenu.promptMultiple(reader, SampleOption.class, "Pick:");

            assertEquals(EnumSet.noneOf(SampleOption.class), result);
        }

        @Test
        @DisplayName("returns selected values for comma-separated input")
        void returnsSelectedValues() {
            InputReader reader = readerFor("1,3\n");

            Set<SampleOption> result = EnumMenu.promptMultiple(reader, SampleOption.class, "Pick:");

            assertEquals(EnumSet.of(SampleOption.FIRST, SampleOption.THIRD), result);
        }

        @Test
        @DisplayName("ignores invalid entries")
        void ignoresInvalidEntries() {
            InputReader reader = readerFor("2,abc,9\n");

            Set<SampleOption> result = EnumMenu.promptMultiple(reader, SampleOption.class, "Pick:");

            assertEquals(EnumSet.of(SampleOption.SECOND), result);
        }
    }
}
