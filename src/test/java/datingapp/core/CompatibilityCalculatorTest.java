package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.CompatibilityCalculator;
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

@DisplayName("CompatibilityCalculator")
class CompatibilityCalculatorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @Test
    @DisplayName("distance score handles negatives and max-distance boundary")
    void calculateDistanceScoreBoundaries() {
        CompatibilityCalculator calculator =
                new CompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        assertEquals(0.5, calculator.calculateDistanceScore(-1, 100), 0.0001);
        assertEquals(0.0, calculator.calculateDistanceScore(100, 100), 0.0001);
        assertTrue(calculator.calculateDistanceScore(5, 100) > 0.9);
    }

    @Test
    @DisplayName("response score decreases as response time grows")
    void calculateResponseScoreMonotonic() {
        CompatibilityCalculator calculator =
                new CompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        double fast = calculator.calculateResponseScore(Duration.ofHours(1));
        double slow = calculator.calculateResponseScore(Duration.ofDays(40));

        assertTrue(fast > slow);
        assertEquals(0.5, calculator.calculateResponseScore(Duration.ZERO), 0.0001);
    }

    @Test
    @DisplayName("pace wildcard gives moderate compatibility")
    void calculatePaceScoreSupportsWildcard() {
        CompatibilityCalculator calculator =
                new CompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

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
        CompatibilityCalculator calculator =
                new CompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

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

    @Test
    @DisplayName("age score reads each user's age once")
    void calculateAgeScoreUsesEachAgeOnce() {
        CompatibilityCalculator calculator =
                new CompatibilityCalculator(AppConfig.defaults(), Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));

        CountingUser me = new CountingUser(UUID.randomUUID(), "Me");
        me.setBirthDate(LocalDate.of(1995, 1, 1));
        me.setAgeRange(18, 40, 18, 120);

        CountingUser them = new CountingUser(UUID.randomUUID(), "Them");
        them.setBirthDate(LocalDate.of(2001, 1, 1));
        them.setAgeRange(18, 40, 18, 120);

        double score = calculator.calculateAgeScore(me, them);

        assertTrue(score > 0.0);
        assertEquals(1, me.ageLookupCount());
        assertEquals(1, them.ageLookupCount());
    }

    @Test
    @DisplayName("protected no-arg constructor fails fast on live use")
    void protectedNoArgConstructorFailsFastOnLiveUse() {
        CompatibilityCalculator calculator = new CompatibilityCalculator() {};
        User me = new User(UUID.randomUUID(), "Me");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> calculator.calculateResponseScore(Duration.ofHours(1)));

        assertEquals("CompatibilityCalculator config is not initialized", ex.getMessage());
        assertThrows(IllegalStateException.class, () -> calculator.calculateActivityScore(me));
    }

    private static final class CountingUser extends User {

        private int ageLookupCount;

        private CountingUser(UUID id, String name) {
            super(id, name);
        }

        @Override
        public java.util.Optional<Integer> getAge(ZoneId timezone) {
            ageLookupCount++;
            return super.getAge(timezone);
        }

        private int ageLookupCount() {
            return ageLookupCount;
        }
    }
}
