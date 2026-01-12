package datingapp.core;

/** Dimensions for communication style preferences. */
public enum CommunicationStyle {
    TEXT_ONLY("Text only"),
    VOICE_NOTES("Voice notes"),
    VIDEO_CALLS("Video calls"),
    IN_PERSON_ONLY("In person only"),
    MIX_OF_EVERYTHING("Mix of everything"); // Treated as wildcard in scoring

    private final String displayName;

    CommunicationStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
