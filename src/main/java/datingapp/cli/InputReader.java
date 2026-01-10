package datingapp.cli;

import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputReader {
  private static final Logger logger = LoggerFactory.getLogger(InputReader.class);
  private final Scanner scanner;

  public InputReader(Scanner scanner) {
    this.scanner = scanner;
  }

  public String readLine(String prompt) {
    logger.info(prompt);
    if (scanner.hasNextLine()) {
      return scanner.nextLine().trim();
    }
    return "";
  }
}
