package datingapp.core;

/** Represents a user's communication and dating pace preferences. */
public record PacePreferences(
        MessagingFrequency messagingFrequency,
        TimeToFirstDate timeToFirstDate,
        CommunicationStyle communicationStyle,
        DepthPreference depthPreference) {
    public boolean isComplete() {
        return messagingFrequency != null
                && timeToFirstDate != null
                && communicationStyle != null
                && depthPreference != null;
    }
}
