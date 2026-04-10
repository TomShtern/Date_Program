package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for AppConfig configuration. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class AppConfigTest {

    private static final String UNSET_VALUE_MESSAGE = "Unset value should use default";

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfiguration {

        @Test
        @DisplayName("Defaults have expected values")
        void defaultsHaveExpectedValues() {
            AppConfig defaults = AppConfig.defaults();

            assertDefaultMatchingConfig(defaults);
            assertDefaultStorageConfig(defaults);
            assertDefaultValidationConfig(defaults);
            assertDefaultAlgorithmConfig(defaults);
            assertEquals(3, defaults.safety().autoBanThreshold(), "Default auto-ban threshold should be 3");
        }

        private void assertDefaultMatchingConfig(AppConfig defaults) {
            assertEquals(100, defaults.matching().dailyLikeLimit(), "Default daily like limit should be 100");
            assertEquals(1, defaults.matching().dailySuperLikeLimit(), "Default super like limit should be 1");
            assertEquals(
                    168, defaults.matching().rematchCooldownHours(), "Default rematch cooldown hours should be 168");
            assertEquals(
                    3,
                    defaults.matching().sharedInterestsPreviewCount(),
                    "Default shared interests preview count should be 3");
            assertTrue(
                    defaults.matching().suspiciousSwipeVelocityBlockingEnabled(),
                    "Default suspicious swipe blocking should be enabled");
        }

        private void assertDefaultStorageConfig(AppConfig defaults) {
            assertEquals("H2", defaults.storage().databaseDialect(), "Default database dialect should be H2");
            assertEquals(
                    "jdbc:h2:./data/dating",
                    defaults.storage().databaseUrl(),
                    "Default database URL should keep the existing H2 runtime path");
            assertEquals("sa", defaults.storage().databaseUsername(), "Default database username should be sa");
            assertEquals(30, defaults.storage().queryTimeoutSeconds(), "Default query timeout should be 30");
            assertEquals(10, defaults.storage().maxPoolSize(), "Default max pool size should be 10");
            assertEquals(2, defaults.storage().minIdle(), "Default min idle should be 2");
            assertEquals(
                    5, defaults.storage().connectionTimeoutSeconds(), "Default connection timeout should be 5 seconds");
            assertEquals(
                    3, defaults.storage().validationTimeoutSeconds(), "Default validation timeout should be 3 seconds");
            assertEquals(600, defaults.storage().idleTimeoutSeconds(), "Default idle timeout should be 600 seconds");
            assertEquals(1800, defaults.storage().maxLifetimeSeconds(), "Default max lifetime should be 1800 seconds");
            assertEquals(0, defaults.storage().keepaliveTimeSeconds(), "Default keepalive should be disabled");
        }

        private void assertDefaultValidationConfig(AppConfig defaults) {
            assertEquals(10, defaults.validation().maxInterests(), "Default max interests should be 10");
            assertEquals(
                    User.MAX_PHOTOS,
                    defaults.validation().maxPhotos(),
                    "Default max photos should match User.MAX_PHOTOS");
            assertEquals(
                    Message.MAX_LENGTH,
                    defaults.validation().maxMessageLength(),
                    "Default max message length should match Message.MAX_LENGTH");
            assertEquals(
                    ProfileNote.MAX_LENGTH,
                    defaults.validation().maxProfileNoteLength(),
                    "Default max profile note length should match ProfileNote.MAX_LENGTH");
            assertEquals(500, defaults.validation().maxBioLength(), "Default max bio length should be 500");
            assertEquals(500, defaults.validation().maxReportDescLength(), "Default report desc length should be 500");
        }

        private void assertDefaultAlgorithmConfig(AppConfig defaults) {
            assertEquals(
                    3, defaults.algorithm().standoutDiversityDays(), "Default standout diversity days should be 3");
            assertEquals(40, defaults.algorithm().standoutMinScore(), "Default standout minimum score should be 40");
            assertEquals(
                    90, defaults.algorithm().starExcellentThreshold(), "Default star excellent threshold should be 90");
            assertEquals(75, defaults.algorithm().starGreatThreshold(), "Default star great threshold should be 75");
            assertEquals(60, defaults.algorithm().starGoodThreshold(), "Default star good threshold should be 60");
            assertEquals(40, defaults.algorithm().starFairThreshold(), "Default star fair threshold should be 40");
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
                    .rematchCooldownHours(72)
                    .sharedInterestsPreviewCount(4)
                    .maxInterests(10)
                    .build();

            assertEquals(5, config.safety().autoBanThreshold());
            assertEquals(50, config.matching().dailyLikeLimit());
            assertEquals(3, config.matching().dailySuperLikeLimit());
            assertEquals(72, config.matching().rematchCooldownHours());
            assertEquals(4, config.matching().sharedInterestsPreviewCount());
            assertEquals(10, config.validation().maxInterests());
        }

        @Test
        @DisplayName("Builder uses defaults for unset values")
        void builderUsesDefaultsForUnset() {
            AppConfig config = AppConfig.builder().autoBanThreshold(10).build();

            assertEquals(10, config.safety().autoBanThreshold(), "Custom value should be set");
            assertEquals(100, config.matching().dailyLikeLimit(), UNSET_VALUE_MESSAGE);
            assertEquals(500, config.validation().maxBioLength(), UNSET_VALUE_MESSAGE);
            assertEquals(Message.MAX_LENGTH, config.validation().maxMessageLength(), UNSET_VALUE_MESSAGE);
            assertEquals(ProfileNote.MAX_LENGTH, config.validation().maxProfileNoteLength(), UNSET_VALUE_MESSAGE);
        }

        @Test
        @DisplayName("Builder is chainable")
        void builderIsChainable() {
            AppConfig.Builder builder = AppConfig.builder();

            assertBuilderChainableCoreMethods(builder);
            assertBuilderChainableStorageMethods(builder);
            assertBuilderChainableAlgorithmMethods(builder);
        }

        private void assertBuilderChainableCoreMethods(AppConfig.Builder builder) {
            assertSame(builder, builder.autoBanThreshold(1));
            assertSame(builder, builder.dailyLikeLimit(1));
            assertSame(builder, builder.dailySuperLikeLimit(1));
            assertSame(builder, builder.rematchCooldownHours(1));
            assertSame(builder, builder.sharedInterestsPreviewCount(1));
            assertSame(builder, builder.suspiciousSwipeVelocityBlockingEnabled(true));
            assertSame(builder, builder.maxInterests(1));
            assertSame(builder, builder.maxPhotos(1));
            assertSame(builder, builder.maxBioLength(1));
            assertSame(builder, builder.maxReportDescLength(1));
            assertSame(builder, builder.maxMessageLength(1));
            assertSame(builder, builder.maxProfileNoteLength(1));
        }

        private void assertBuilderChainableStorageMethods(AppConfig.Builder builder) {
            assertSame(builder, builder.databaseDialect("POSTGRESQL"));
            assertSame(builder, builder.databaseUrl("jdbc:postgresql://localhost:5432/datingapp"));
            assertSame(builder, builder.databaseUsername("datingapp"));
            assertSame(builder, builder.queryTimeoutSeconds(1));
            assertSame(builder, builder.maxPoolSize(1));
            assertSame(builder, builder.minIdle(0));
            assertSame(builder, builder.connectionTimeoutSeconds(1));
            assertSame(builder, builder.validationTimeoutSeconds(1));
            assertSame(builder, builder.idleTimeoutSeconds(0));
            assertSame(builder, builder.maxLifetimeSeconds(0));
            assertSame(builder, builder.keepaliveTimeSeconds(0));
        }

        private void assertBuilderChainableAlgorithmMethods(AppConfig.Builder builder) {
            assertSame(builder, builder.standoutDiversityDays(1));
            assertSame(builder, builder.standoutMinScore(1));
            assertSame(builder, builder.starExcellentThreshold(1));
            assertSame(builder, builder.starGreatThreshold(1));
            assertSame(builder, builder.starGoodThreshold(1));
            assertSame(builder, builder.starFairThreshold(1));
        }

        @Test
        @DisplayName("Builder maps fields into the correct sub-records")
        void builderMapsMatchingFieldsIntoCorrectSubRecord() {
            AppConfig config = createCustomConfig();

            assertEquals(111, config.matching().dailyLikeLimit());
            assertEquals(7, config.matching().dailySuperLikeLimit());
            assertEquals(36, config.matching().rematchCooldownHours());
            assertEquals(222, config.matching().dailyPassLimit());
            assertEquals(333, config.matching().maxSwipesPerSession());
            assertEquals(4.5, config.matching().suspiciousSwipeVelocity(), 0.001);
            assertFalse(config.matching().suspiciousSwipeVelocityBlockingEnabled());
            assertEquals(0.10, config.matching().distanceWeight(), 0.001);
            assertEquals(0.20, config.matching().ageWeight(), 0.001);
            assertEquals(0.30, config.matching().interestWeight(), 0.001);
            assertEquals(0.15, config.matching().lifestyleWeight(), 0.001);
            assertEquals(0.15, config.matching().paceWeight(), 0.001);
            assertEquals(0.10, config.matching().responseWeight(), 0.001);
            assertEquals(9, config.matching().minSharedInterests());
            assertEquals(4, config.matching().sharedInterestsPreviewCount());
            assertEquals(444, config.matching().maxDistanceKm());
        }

        @Test
        @DisplayName("Builder maps fields into the validation sub-record")
        void builderMapsValidationFieldsIntoCorrectSubRecord() {
            AppConfig config = createCustomConfig();

            assertEquals(21, config.validation().minAge());
            assertEquals(87, config.validation().maxAge());
            assertEquals(101, config.validation().minHeightCm());
            assertEquals(231, config.validation().maxHeightCm());
            assertEquals(777, config.validation().maxBioLength());
            assertEquals(778, config.validation().maxReportDescLength());
            assertEquals(88, config.validation().maxNameLength());
            assertEquals(901, config.validation().maxMessageLength());
            assertEquals(402, config.validation().maxProfileNoteLength());
            assertEquals(6, config.validation().minAgeRangeSpan());
            assertEquals(3, config.validation().minDistanceKm());
            assertEquals(17, config.validation().maxInterests());
            assertEquals(6, config.validation().maxPhotos());
            assertEquals(66, config.validation().messageMaxPageSize());
        }

        @Test
        @DisplayName("Builder maps fields into the storage sub-record")
        void builderMapsStorageFieldsIntoCorrectSubRecord() {
            AppConfig config = createCustomConfig();

            assertEquals("POSTGRESQL", config.storage().databaseDialect());
            assertEquals(
                    "jdbc:postgresql://localhost:5432/datingapp",
                    config.storage().databaseUrl());
            assertEquals("datingapp", config.storage().databaseUsername());
            assertEquals(62, config.storage().queryTimeoutSeconds());
            assertEquals(14, config.storage().maxPoolSize());
            assertEquals(4, config.storage().minIdle());
            assertEquals(7, config.storage().connectionTimeoutSeconds());
            assertEquals(5, config.storage().validationTimeoutSeconds());
            assertEquals(900, config.storage().idleTimeoutSeconds());
            assertEquals(2400, config.storage().maxLifetimeSeconds());
            assertEquals(120, config.storage().keepaliveTimeSeconds());
        }

        @Test
        @DisplayName("Builder maps fields into the algorithm sub-record")
        void builderMapsAlgorithmFieldsIntoCorrectSubRecord() {
            AppConfig config = createCustomConfig();

            assertEquals(12, config.algorithm().nearbyDistanceKm());
            assertEquals(24, config.algorithm().closeDistanceKm());
            assertEquals(4, config.algorithm().similarAgeDiff());
            assertEquals(11, config.algorithm().compatibleAgeDiff());
            assertEquals(70, config.algorithm().paceCompatibilityThreshold());
            assertEquals(2, config.algorithm().responseTimeExcellentHours());
            assertEquals(7, config.algorithm().responseTimeGreatHours());
            assertEquals(13, config.algorithm().responseTimeGoodHours());
            assertEquals(169, config.algorithm().responseTimeWeekHours());
            assertEquals(721, config.algorithm().responseTimeMonthHours());
            assertEquals(9, config.algorithm().standoutDiversityDays());
            assertEquals(58, config.algorithm().standoutMinScore());
            assertEquals(98, config.algorithm().starExcellentThreshold());
            assertEquals(88, config.algorithm().starGreatThreshold());
            assertEquals(77, config.algorithm().starGoodThreshold());
            assertEquals(66, config.algorithm().starFairThreshold());
            assertEquals(0.11, config.algorithm().standoutDistanceWeight(), 0.001);
            assertEquals(0.12, config.algorithm().standoutAgeWeight(), 0.001);
            assertEquals(0.13, config.algorithm().standoutInterestWeight(), 0.001);
            assertEquals(0.14, config.algorithm().standoutLifestyleWeight(), 0.001);
            assertEquals(0.15, config.algorithm().standoutCompletenessWeight(), 0.001);
            assertEquals(0.35, config.algorithm().standoutActivityWeight(), 0.001);
        }

        @Test
        @DisplayName("Builder maps fields into the safety sub-record")
        void builderMapsSafetyFieldsIntoCorrectSubRecord() {
            AppConfig config = createCustomConfig();
            ZoneId zone = ZoneId.of("UTC");

            assertEquals(8, config.safety().autoBanThreshold());
            assertEquals(zone, config.safety().userTimeZone());
            assertEquals(61, config.safety().sessionTimeoutMinutes());
            assertEquals(31, config.safety().undoWindowSeconds());
            assertEquals(2, config.safety().achievementMatchTier1());
            assertEquals(12, config.safety().achievementMatchTier2());
            assertEquals(26, config.safety().achievementMatchTier3());
            assertEquals(76, config.safety().achievementMatchTier4());
            assertEquals(201, config.safety().achievementMatchTier5());
            assertEquals(55, config.safety().minSwipesForBehaviorAchievement());
            assertEquals(0.21, config.safety().selectiveThreshold(), 0.001);
            assertEquals(0.61, config.safety().openMindedThreshold(), 0.001);
            assertEquals(111, config.safety().bioAchievementLength());
            assertEquals(6, config.safety().lifestyleFieldTarget());
            assertEquals(61, config.safety().cleanupRetentionDays());
            assertEquals(91, config.safety().softDeleteRetentionDays());
        }

        @Test
        @DisplayName("Builder rejects non-monotonic response time thresholds")
        void builderRejectsNonMonotonicResponseTimeThresholds() {
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, this::buildWithNonMonotonicResponseTimeThresholds);

            assertEquals("responseTimeExcellentHours must be <= responseTimeGreatHours", ex.getMessage());
        }

        @Test
        @DisplayName("Builder rejects non-monotonic safety achievement tiers")
        void builderRejectsNonMonotonicSafetyAchievementTiers() {
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, this::buildWithNonMonotonicSafetyAchievementTiers);

            assertEquals("achievementMatchTier1 must be <= achievementMatchTier2", ex.getMessage());
        }

        private void buildWithNonMonotonicResponseTimeThresholds() {
            AppConfig.builder()
                    .responseTimeExcellentHours(6)
                    .responseTimeGreatHours(2)
                    .build();
        }

        private void buildWithNonMonotonicSafetyAchievementTiers() {
            AppConfig.builder()
                    .achievementMatchTier1(5)
                    .achievementMatchTier2(4)
                    .build();
        }

        private AppConfig createCustomConfig() {
            return AppConfig.builder()
                    .dailyLikeLimit(111)
                    .dailySuperLikeLimit(7)
                    .rematchCooldownHours(36)
                    .dailyPassLimit(222)
                    .maxSwipesPerSession(333)
                    .suspiciousSwipeVelocity(4.5)
                    .suspiciousSwipeVelocityBlockingEnabled(false)
                    .distanceWeight(0.10)
                    .ageWeight(0.20)
                    .interestWeight(0.30)
                    .lifestyleWeight(0.15)
                    .paceWeight(0.15)
                    .responseWeight(0.10)
                    .minSharedInterests(9)
                    .sharedInterestsPreviewCount(4)
                    .maxDistanceKm(444)
                    .minAge(21)
                    .maxAge(87)
                    .minHeightCm(101)
                    .maxHeightCm(231)
                    .maxBioLength(777)
                    .maxReportDescLength(778)
                    .maxNameLength(88)
                    .maxMessageLength(901)
                    .maxProfileNoteLength(402)
                    .minAgeRangeSpan(6)
                    .minDistanceKm(3)
                    .maxInterests(17)
                    .maxPhotos(6)
                    .messageMaxPageSize(66)
                    .nearbyDistanceKm(12)
                    .closeDistanceKm(24)
                    .similarAgeDiff(4)
                    .compatibleAgeDiff(11)
                    .paceCompatibilityThreshold(70)
                    .responseTimeExcellentHours(2)
                    .responseTimeGreatHours(7)
                    .responseTimeGoodHours(13)
                    .responseTimeWeekHours(169)
                    .responseTimeMonthHours(721)
                    .standoutDiversityDays(9)
                    .standoutMinScore(58)
                    .starExcellentThreshold(98)
                    .starGreatThreshold(88)
                    .starGoodThreshold(77)
                    .starFairThreshold(66)
                    .standoutDistanceWeight(0.11)
                    .standoutAgeWeight(0.12)
                    .standoutInterestWeight(0.13)
                    .standoutLifestyleWeight(0.14)
                    .standoutCompletenessWeight(0.15)
                    .standoutActivityWeight(0.35)
                    .autoBanThreshold(8)
                    .userTimeZone(ZoneId.of("UTC"))
                    .sessionTimeoutMinutes(61)
                    .undoWindowSeconds(31)
                    .achievementMatchTier1(2)
                    .achievementMatchTier2(12)
                    .achievementMatchTier3(26)
                    .achievementMatchTier4(76)
                    .achievementMatchTier5(201)
                    .minSwipesForBehaviorAchievement(55)
                    .selectiveThreshold(0.21)
                    .openMindedThreshold(0.61)
                    .bioAchievementLength(111)
                    .lifestyleFieldTarget(6)
                    .cleanupRetentionDays(61)
                    .softDeleteRetentionDays(91)
                    .databaseDialect("POSTGRESQL")
                    .databaseUrl("jdbc:postgresql://localhost:5432/datingapp")
                    .databaseUsername("datingapp")
                    .queryTimeoutSeconds(62)
                    .maxPoolSize(14)
                    .minIdle(4)
                    .connectionTimeoutSeconds(7)
                    .validationTimeoutSeconds(5)
                    .idleTimeoutSeconds(900)
                    .maxLifetimeSeconds(2400)
                    .keepaliveTimeSeconds(120)
                    .build();
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
            double total = config.matching().distanceWeight()
                    + config.matching().ageWeight()
                    + config.matching().interestWeight()
                    + config.matching().lifestyleWeight()
                    + config.matching().paceWeight()
                    + config.matching().responseWeight();

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
