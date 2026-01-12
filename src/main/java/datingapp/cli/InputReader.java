package datingapp.cli;

import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles user input from the console, providing prompt display and line reading. */
public class InputReader {
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
