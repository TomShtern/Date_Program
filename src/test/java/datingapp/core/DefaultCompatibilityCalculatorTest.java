package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultCompatibilityCalculator")
class DefaultCompatibilityCalculatorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @Test
    @DisplayName("distance score handles negatives and max-distance boundary")
    void calculateDistanceScoreBoundaries() {
        DefaultCompatibilityCalculator calculator =
                new DefaultCompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        assertEquals(0.5, calculator.calculateDistanceScore(-1, 100), 0.0001);
        assertEquals(0.0, calculator.calculateDistanceScore(100, 100), 0.0001);
        assertTrue(calculator.calculateDistanceScore(5, 100) > 0.9);
    }

    @Test
    @DisplayName("response score decreases as response time grows")
    void calculateResponseScoreMonotonic() {
        DefaultCompatibilityCalculator calculator =
                new DefaultCompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        double fast = calculator.calculateResponseScore(Duration.ofHours(1));
        double slow = calculator.calculateResponseScore(Duration.ofDays(40));

        assertTrue(fast > slow);
        assertEquals(0.5, calculator.calculateResponseScore(Duration.ZERO), 0.0001);
    }

    @Test
    @DisplayName("pace wildcard gives moderate compatibility")
    void calculatePaceScoreSupportsWildcard() {
        DefaultCompatibilityCalculator calculator =
                new DefaultCompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        PacePreferences a = new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT);
        PacePreferences b = new PacePreferences(
                PacePreferences.MessagingFrequency.CONSTANTLY,
                PacePreferences.TimeToFirstDate.WEEKS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.DEPENDS_ON_VIBE);

        double score = calculator.calculatePaceScore(a, b);
        assertTrue(score > 0.4 && score < 1.0);
    }

    @Test
    @DisplayName("interest score rewards overlap")
    void calculateInterestScoreRewardsOverlap() {
        DefaultCompatibilityCalculator calculator =
                new DefaultCompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        User me = new User(UUID.randomUUID(), "Me");
        me.setBirthDate(LocalDate.of(1998, 1, 1));
        me.setAgeRange(18, 40, 18, 120);
        me.addInterest(Interest.TRAVEL);
        me.addInterest(Interest.MUSIC);

        User them = new User(UUID.randomUUID(), "Them");
        them.setBirthDate(LocalDate.of(1998, 1, 1));
        them.setAgeRange(18, 40, 18, 120);
        them.addInterest(Interest.TRAVEL);

        assertTrue(calculator.calculateInterestScore(me, them) > 0.0);
    }
}
