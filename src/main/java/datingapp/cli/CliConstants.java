package datingapp.cli;

public class CliConstants {
  private CliConstants() {} // Prevent instantiation

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
  public static final String INVALID_INPUT_MSG = "\n❌ Invalid input.\n";
}
