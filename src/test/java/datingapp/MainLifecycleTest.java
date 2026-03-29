package datingapp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliTextAndInput.InputReader;
import java.io.StringReader;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("Main lifecycle shutdown handling")
class MainLifecycleTest {

    @Test
    @DisplayName("runWithShutdown should always run shutdown when the body throws")
    void runWithShutdownAlwaysInvokesShutdown() {
        AtomicBoolean shutdownCalled = new AtomicBoolean(false);

        assertThrows(
                RuntimeException.class,
                () -> Main.runWithShutdown(
                        () -> {
                            throw new RuntimeException("boom");
                        },
                        () -> shutdownCalled.set(true)));

        assertTrue(shutdownCalled.get());
    }

    @Test
    @DisplayName("menu EOF should stop the main loop")
    void menuEofShouldStopTheMainLoop() {
        InputReader inputReader = new InputReader(new Scanner(new StringReader("")));

        inputReader.readLine("Choose an option: ");

        assertTrue(Main.shouldExitMainMenu(inputReader));
    }
}
