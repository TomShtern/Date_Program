package datingapp.core;

/** Dimensions for conversation depth preferences. */
public enum DepthPreference {
    SMALL_TALK("Small talk"),
    DEEP_CHAT("Deep chat"),
    EXISTENTIAL("Existential exploration"),
    DEPENDS_ON_VIBE("Depends on the vibe"); // Treated as wildcard in scoring

    private final String displayName;

    DepthPreference(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
