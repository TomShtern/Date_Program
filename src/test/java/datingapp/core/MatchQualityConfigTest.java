package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for MatchQualityConfig record.
 */
@DisplayName("MatchQualityConfig Tests")
class MatchQualityConfigTest {

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Default config weights sum to 1.0")
        void defaultConfigSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.defaults();

            double total = config.distanceWeight() + config.ageWeight() +
                    config.interestWeight() + config.lifestyleWeight() +
                    config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Proximity focused config weights sum to 1.0")
        void proximityFocusedSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.proximityFocused();

            double total = config.distanceWeight() + config.ageWeight() +
                    config.interestWeight() + config.lifestyleWeight() +
                    config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Lifestyle focused config weights sum to 1.0")
        void lifestyleFocusedSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.lifestyleFocused();

            double total = config.distanceWeight() + config.ageWeight() +
                    config.interestWeight() + config.lifestyleWeight() +
                    config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Throws if weights don't sum to 1.0")
        void throwsIfWeightsDontSumToOne() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(0.5, 0.5, 0.5, 0.5, 0.5));
        }

        @Test
        @DisplayName("Throws if any weight is negative")
        void throwsIfNegativeWeight() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(-0.1, 0.3, 0.3, 0.3, 0.2));
        }

        @Test
        @DisplayName("Allows custom valid weights")
        void allowsCustomValidWeights() {
            MatchQualityConfig config = new MatchQualityConfig(0.2, 0.2, 0.2, 0.2, 0.2);

            assertEquals(0.2, config.distanceWeight());
            assertEquals(0.2, config.ageWeight());
            assertEquals(0.2, config.interestWeight());
            assertEquals(0.2, config.lifestyleWeight());
            assertEquals(0.2, config.responseWeight());
        }
    }
}
