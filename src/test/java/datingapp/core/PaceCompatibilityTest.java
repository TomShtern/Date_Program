package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.matching.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.testutil.TestStorages;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for pace compatibility calculation in MatchQualityService. Formerly
 * PaceCompatibilityServiceTest.
 */
@SuppressWarnings("unused")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PaceCompatibilityTest {

    private MatchQualityService service;

    @BeforeEach
    void setUp() {
        service = new MatchQualityService(
                new TestStorages.Users(), new TestStorages.Interactions(), AppConfig.defaults());
    }

    @Test
    @DisplayName("Perfect match returns 100")
    void perfectMatch_returns100() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);

        assertEquals(100, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Extreme opposites return low score")
    void extremeOpposites_returnsLowScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.RARELY,
                TimeToFirstDate.QUICKLY,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.SMALL_TALK);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.CONSTANTLY,
                TimeToFirstDate.MONTHS,
                CommunicationStyle.IN_PERSON_ONLY,
                DepthPreference.EXISTENTIAL);

        // Messaging: dist 2 -> 5
        // Time: dist 3 -> 5
        // Comm: dist 3 -> 5
        // Depth: dist 2 -> 5
        // Total: 20
        assertEquals(20, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Communication style wildcard applies wildcard score")
    void communicationStyleWildcard_appliesWildcardScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT);

        // Messaging: 25
        // Time: 25
        // Comm: wildcard -> 20
        // Depth: 25
        // Total: 95
        assertEquals(95, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Depth preference wildcard applies wildcard score")
    void depthPreferenceWildcard_appliesWildcardScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEPENDS_ON_VIBE);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        // Messaging: 25
        // Time: 25
        // Comm: 25
        // Depth: wildcard -> 20
        // Total: 95
        assertEquals(95, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Null preferences return -1")
    void nullPreferences_returnsNegativeOne() {
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        // Null PacePreferences should return -1
        assertEquals(-1, service.calculatePaceCompatibility(null, p2));
        assertEquals(-1, service.calculatePaceCompatibility(p2, null));
        assertEquals(-1, service.calculatePaceCompatibility(null, null));
    }

    @Test
    @DisplayName("Low compatibility threshold detects correctly")
    void lowCompatibilityThreshold_detectsCorrectly() {
        assertTrue(service.isLowPaceCompatibility(45));
        assertTrue(service.isLowPaceCompatibility(0));
        assertFalse(service.isLowPaceCompatibility(50));
        assertFalse(service.isLowPaceCompatibility(100));
        assertFalse(service.isLowPaceCompatibility(-1));
    }

    @Test
    @DisplayName("Adjacency logic works correctly")
    void adjacencyLogic_worksCorrectly() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.RARELY, // dist 1 -> 15
                TimeToFirstDate.QUICKLY, // dist 1 -> 15
                CommunicationStyle.TEXT_ONLY, // dist 1 -> 15
                DepthPreference.SMALL_TALK // dist 1 -> 15
                );

        assertEquals(60, service.calculatePaceCompatibility(p1, p2));
    }
}
