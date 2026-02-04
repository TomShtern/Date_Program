package datingapp.core;

/** Consolidates communication and dating pace preference enums and record. */
public record PacePreferences(
        MessagingFrequency messagingFrequency,
        TimeToFirstDate timeToFirstDate,
        CommunicationStyle communicationStyle,
        DepthPreference depthPreference) {

    public PacePreferences {
        boolean anySet = messagingFrequency != null
                || timeToFirstDate != null
                || communicationStyle != null
                || depthPreference != null;
        boolean anyMissing = messagingFrequency == null
                || timeToFirstDate == null
                || communicationStyle == null
                || depthPreference == null;

        if (anySet && anyMissing) {
            throw new IllegalArgumentException("PacePreferences must be all set or all null");
        }
    }

    public boolean isComplete() {
        return messagingFrequency != null
                && timeToFirstDate != null
                && communicationStyle != null
                && depthPreference != null;
    }

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

    /** Dimensions for how soon a user wants to go on a first date. */
    public enum TimeToFirstDate {
        QUICKLY("Quickly (1-2 days)"),
        FEW_DAYS("A few days"),
        WEEKS("Weeks"),
        MONTHS("Months"),
        WILDCARD("No preference");

        private final String displayName;

        TimeToFirstDate(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** Dimensions for communication style preferences. */
    public enum CommunicationStyle {
        TEXT_ONLY("Text only"),
        VOICE_NOTES("Voice notes"),
        VIDEO_CALLS("Video calls"),
        IN_PERSON_ONLY("In person only"),
        MIX_OF_EVERYTHING("Mix of everything");

        private final String displayName;

        CommunicationStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** Dimensions for conversation depth preferences. */
    public enum DepthPreference {
        SMALL_TALK("Small talk"),
        DEEP_CHAT("Deep chat"),
        EXISTENTIAL("Existential exploration"),
        DEPENDS_ON_VIBE("Depends on the vibe");

        private final String displayName;

        DepthPreference(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
