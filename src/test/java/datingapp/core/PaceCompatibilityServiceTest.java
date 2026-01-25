package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.PacePreferences;
import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaceCompatibilityServiceTest {

    private PaceCompatibilityService service;

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        service = new PaceCompatibilityService();
    }

    @Test
    void perfectMatch_returns100() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);

        assertEquals(100, service.calculateCompatibility(p1, p2));
    }

    @Test
    void extremeOpposites_returnsLowScore() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.RARELY,
                TimeToFirstDate.QUICKLY,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.SMALL_TALK);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.CONSTANTLY,
                TimeToFirstDate.MONTHS,
                CommunicationStyle.IN_PERSON_ONLY,
                DepthPreference.EXISTENTIAL);

        // Messaging: dist 2 -> 5
        // Time: dist 3 -> 5
        // Comm: dist 3 -> 5
        // Depth: dist 2 -> 5
        // Total: 20
        assertEquals(20, service.calculateCompatibility(p1, p2));
    }

    @Test
    void communicationStyleWildcard_appliesWildcardScore() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT);

        // Messaging: 25
        // Time: 25
        // Comm: wildcard -> 20
        // Depth: 25
        // Total: 95
        assertEquals(95, service.calculateCompatibility(p1, p2));
    }

    @Test
    void depthPreferenceWildcard_appliesWildcardScore() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEPENDS_ON_VIBE);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        // Messaging: 25
        // Time: 25
        // Comm: 25
        // Depth: wildcard -> 20
        // Total: 95
        assertEquals(95, service.calculateCompatibility(p1, p2));
    }

    @Test
    void incompletePreferences_returnsNegativeOne() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN, null, CommunicationStyle.VOICE_NOTES, DepthPreference.SMALL_TALK);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        assertEquals(-1, service.calculateCompatibility(p1, p2));
        assertEquals(-1, service.calculateCompatibility(null, p2));
    }

    @Test
    void lowCompatibilityThreshold_detectsCorrectly() {
        assertTrue(service.isLowCompatibility(45));
        assertTrue(service.isLowCompatibility(0));
        assertFalse(service.isLowCompatibility(50));
        assertFalse(service.isLowCompatibility(100));
        assertFalse(service.isLowCompatibility(-1));
    }

    @Test
    void adjacencyLogic_worksCorrectly() {
        PacePreferences p1 = new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new Preferences.PacePreferences(
                MessagingFrequency.RARELY, // dist 1 -> 15
                TimeToFirstDate.QUICKLY, // dist 1 -> 15
                CommunicationStyle.TEXT_ONLY, // dist 1 -> 15
                DepthPreference.SMALL_TALK // dist 1 -> 15
                );

        assertEquals(60, service.calculateCompatibility(p1, p2));
    }
}
