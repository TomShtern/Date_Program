package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PacePreferencesDtos")
class PacePreferencesDtosTest {

    @Test
    @DisplayName("Read.from(null) returns null")
    void readFromNullReturnsNull() {
        assertNull(PacePreferencesDtos.Read.from(null));
    }

    @Test
    @DisplayName("Read.blankWhenMissing(null) returns a blank DTO")
    void readBlankWhenMissingNullReturnsBlankDto() {
        PacePreferencesDtos.Read dto = PacePreferencesDtos.Read.blankWhenMissing(null);

        assertAll(
                () -> assertNotNull(dto),
                () -> assertNull(dto.messagingFrequency()),
                () -> assertNull(dto.timeToFirstDate()),
                () -> assertNull(dto.communicationStyle()),
                () -> assertNull(dto.depthPreference()));
    }

    @Test
    @DisplayName("Write(null, null, null, null).toPacePreferences() returns null")
    void writeAllNullToPacePreferencesReturnsNull() {
        assertNull(new PacePreferencesDtos.Write(null, null, null, null).toPacePreferences());
    }

    @Test
    @DisplayName("Write round-trips enum values into PacePreferences")
    void writeRoundTripsEnumValuesIntoPacePreferences() {
        PacePreferencesDtos.Write write = new PacePreferencesDtos.Write(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.DEEP_CHAT);

        PacePreferences expected = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.DEEP_CHAT);

        assertEquals(expected, write.toPacePreferences());
    }
}
