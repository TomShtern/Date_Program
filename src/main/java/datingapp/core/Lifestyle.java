package datingapp.core;

/** Container for lifestyle-related enums. Used for profile data and dealbreaker filtering. */
public final class Lifestyle {

  private Lifestyle() {
    // Utility class - prevent instantiation
  }

  /** Smoking habits. */
  public enum Smoking {
    NEVER("Never"),
    SOMETIMES("Sometimes"),
    REGULARLY("Regularly");

    private final String displayName;

    Smoking(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /** Drinking habits. */
  public enum Drinking {
    NEVER("Never"),
    SOCIALLY("Socially"),
    REGULARLY("Regularly");

    private final String displayName;

    Drinking(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /** Stance on having children. */
  public enum WantsKids {
    NO("Don't want"),
    OPEN("Open to it"),
    SOMEDAY("Want someday"),
    HAS_KIDS("Have kids");

    private final String displayName;

    WantsKids(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /** Relationship goals. */
  public enum LookingFor {
    CASUAL("Something casual"),
    SHORT_TERM("Short-term dating"),
    LONG_TERM("Long-term relationship"),
    MARRIAGE("Marriage"),
    UNSURE("Not sure yet");

    private final String displayName;

    LookingFor(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /** Education level. */
  public enum Education {
    HIGH_SCHOOL("High school"),
    SOME_COLLEGE("Some college"),
    BACHELORS("Bachelor's degree"),
    MASTERS("Master's degree"),
    PHD("PhD/Doctorate"),
    TRADE_SCHOOL("Trade school"),
    OTHER("Other");

    private final String displayName;

    Education(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}
