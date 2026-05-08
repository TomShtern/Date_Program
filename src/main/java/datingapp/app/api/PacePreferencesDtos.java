package datingapp.app.api;

import datingapp.core.profile.MatchPreferences.PacePreferences;

final class PacePreferencesDtos {
    private PacePreferencesDtos() {}

    static record Read(
            String messagingFrequency, String timeToFirstDate, String communicationStyle, String depthPreference) {
        static Read from(PacePreferences pace) {
            if (pace == null) {
                return null;
            }
            return new Read(
                    pace.messagingFrequency() != null
                            ? pace.messagingFrequency().name()
                            : null,
                    pace.timeToFirstDate() != null ? pace.timeToFirstDate().name() : null,
                    pace.communicationStyle() != null
                            ? pace.communicationStyle().name()
                            : null,
                    pace.depthPreference() != null ? pace.depthPreference().name() : null);
        }

        static Read blankWhenMissing(PacePreferences pace) {
            if (pace == null) {
                return new Read(null, null, null, null);
            }
            return from(pace);
        }
    }

    static record Write(
            PacePreferences.MessagingFrequency messagingFrequency,
            PacePreferences.TimeToFirstDate timeToFirstDate,
            PacePreferences.CommunicationStyle communicationStyle,
            PacePreferences.DepthPreference depthPreference) {

        PacePreferences toPacePreferences() {
            if (messagingFrequency == null
                    && timeToFirstDate == null
                    && communicationStyle == null
                    && depthPreference == null) {
                return null;
            }
            return new PacePreferences(messagingFrequency, timeToFirstDate, communicationStyle, depthPreference);
        }
    }
}
