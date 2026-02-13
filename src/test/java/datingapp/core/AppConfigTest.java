package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for AppConfig configuration. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class AppConfigTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfiguration {

        @Test
        @DisplayName("Defaults have expected values")
        void defaultsHaveExpectedValues() {
            AppConfig defaults = AppConfig.defaults();

            assertEquals(3, defaults.autoBanThreshold(), "Default auto-ban threshold should be 3");
            assertEquals(100, defaults.dailyLikeLimit(), "Default daily like limit should be 100");
            assertEquals(1, defaults.dailySuperLikeLimit(), "Default super like limit should be 1");
            assertEquals(10, defaults.maxInterests(), "Default max interests should be 10");
            assertEquals(2, defaults.maxPhotos(), "Default max photos should be 2");
            assertEquals(500, defaults.maxBioLength(), "Default max bio length should be 500");
            assertEquals(500, defaults.maxReportDescLength(), "Default report desc length should be 500");
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class Validation {

        @Test
        @DisplayName("Builder creates config with custom values")
        void builderCreatesCustomConfig() {
            AppConfig config = AppConfig.builder()
                    .autoBanThreshold(5)
                    .dailyLikeLimit(50)
                    .dailySuperLikeLimit(3)
                    .maxInterests(10)
                    .build();

            assertEquals(5, config.autoBanThreshold());
            assertEquals(50, config.dailyLikeLimit());
            assertEquals(3, config.dailySuperLikeLimit());
            assertEquals(10, config.maxInterests());
        }

        @Test
        @DisplayName("Builder uses defaults for unset values")
        void builderUsesDefaultsForUnset() {
            AppConfig config = AppConfig.builder().autoBanThreshold(10).build();

            assertEquals(10, config.autoBanThreshold(), "Custom value should be set");
            assertEquals(100, config.dailyLikeLimit(), "Unset value should use default");
            assertEquals(500, config.maxBioLength(), "Unset value should use default");
        }

        @Test
        @DisplayName("Builder is chainable")
        void builderIsChainable() {
            AppConfig.Builder builder = AppConfig.builder();

            // Verify all methods return the builder for chaining
            assertSame(builder, builder.autoBanThreshold(1));
            assertSame(builder, builder.dailyLikeLimit(1));
            assertSame(builder, builder.dailySuperLikeLimit(1));
            assertSame(builder, builder.maxInterests(1));
            assertSame(builder, builder.maxPhotos(1));
            assertSame(builder, builder.maxBioLength(1));
            assertSame(builder, builder.maxReportDescLength(1));
        }
    }

    @Nested
    @DisplayName("Record Immutability")
    class RecordImmutability {

        @Test
        @DisplayName("Config is immutable record")
        void configIsImmutableRecord() {
            AppConfig config1 = AppConfig.defaults();
            AppConfig config2 = AppConfig.defaults();

            // Records with same values should be equal
            assertEquals(config1, config2, "Two default configs should be equal");
            assertEquals(config1.hashCode(), config2.hashCode(), "Hash codes should match");
        }

        @Test
        @DisplayName("Different configs are not equal")
        void differentConfigsNotEqual() {
            AppConfig config1 = AppConfig.defaults();
            AppConfig config2 = AppConfig.builder().autoBanThreshold(10).build();

            assertNotEquals(config1, config2, "Different configs should not be equal");
        }
    }

    @Nested
    @DisplayName("Weight Validation")
    class WeightValidation {

        @Test
        @DisplayName("Defaults have weights that sum to 1.0")
        void defaultsSumToOne() {
            AppConfig config = AppConfig.defaults();
            double total = config.distanceWeight()
                    + config.ageWeight()
                    + config.interestWeight()
                    + config.lifestyleWeight()
                    + config.paceWeight()
                    + config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Invalid weight sum throws")
        void invalidWeightSumThrows() {
            assertThrows(IllegalArgumentException.class, () -> AppConfig.builder()
                    .distanceWeight(0.5)
                    .ageWeight(0.5)
                    .interestWeight(0.5)
                    .lifestyleWeight(0.0)
                    .paceWeight(0.0)
                    .responseWeight(0.0)
                    .build());
        }
    }
}
