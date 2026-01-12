package datingapp.core;

/** Dimensions for messaging frequency preferences. */
public enum MessagingFrequency {
    RARELY("Rarely"),
    OFTEN("Often"),
    CONSTANTLY("Constantly"),
    WILDCARD("No preference");

    private final String displayName;

    MessagingFrequency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
