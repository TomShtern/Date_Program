package datingapp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for ApplicationStartup config loading helpers.
 */
@Timeout(5)
@SuppressWarnings("unused")
@DisplayName("ApplicationStartup Config Loading")
class ConfigLoaderTest {

    @Nested
    @DisplayName("load from file")
    class LoadFromFile {

        @Test
        @DisplayName("Should load config from JSON file")
        void loadsFromJsonFile(@TempDir Path tempDir) throws IOException {
            Path configFile = tempDir.resolve("config.json");
            String json = """
                    {
                      "dailyLikeLimit": 25,
                      "maxDistanceKm": 75,
                      "minAge": 21
                    }
                    """;
            Files.writeString(configFile, json);

            AppConfig config = ApplicationStartup.load(configFile);

            assertEquals(25, config.matching().dailyLikeLimit());
            assertEquals(75, config.matching().maxDistanceKm());
            assertEquals(21, config.validation().minAge());
        }

        @Test
        @DisplayName("Should return defaults when file not found")
        void returnsDefaultsWhenFileNotFound(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("nonexistent.json");

            AppConfig config = ApplicationStartup.load(nonExistent);

            // Should have default values
            assertEquals(
                    AppConfig.defaults().matching().dailyLikeLimit(),
                    config.matching().dailyLikeLimit());
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void handlesMalformedJson(@TempDir Path tempDir) throws IOException {
            Path configFile = tempDir.resolve("bad.json");
            Files.writeString(configFile, "{ invalid json }}}");

            // Should not throw, should return defaults
            AppConfig config = ApplicationStartup.load(configFile);
            assertNotNull(config);
        }
    }

    @Nested
    @DisplayName("fromJson")
    class FromJson {

        @Test
        @DisplayName("Should parse all limit fields")
        void parsesLimitFields() {
            String json = """
                    {
                      "autoBanThreshold": 10,
                      "dailyLikeLimit": 50,
                      "dailySuperLikeLimit": 3,
                      "rematchCooldownHours": 24,
                      "dailyPassLimit": 200,
                      "sharedInterestsPreviewCount": 4,
                      "maxInterests": 15,
                      "maxPhotos": 6,
                      "maxBioLength": 1000,
                      "maxMessageLength": 900,
                      "maxProfileNoteLength": 400
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(10, config.safety().autoBanThreshold());
            assertEquals(50, config.matching().dailyLikeLimit());
            assertEquals(3, config.matching().dailySuperLikeLimit());
            assertEquals(24, config.matching().rematchCooldownHours());
            assertEquals(200, config.matching().dailyPassLimit());
            assertEquals(4, config.matching().sharedInterestsPreviewCount());
            assertEquals(15, config.validation().maxInterests());
            assertEquals(6, config.validation().maxPhotos());
            assertEquals(1000, config.validation().maxBioLength());
            assertEquals(900, config.validation().maxMessageLength());
            assertEquals(400, config.validation().maxProfileNoteLength());
        }

        @Test
        @DisplayName("Should parse session fields")
        void parsesSessionFields() {
            String json = """
                    {
                      "sessionTimeoutMinutes": 60,
                "queryTimeoutSeconds": 45,
                      "maxSwipesPerSession": 200,
                      "suspiciousSwipeVelocity": 2.5,
                      "suspiciousSwipeVelocityBlockingEnabled": false
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(60, config.safety().sessionTimeoutMinutes());
            assertEquals(45, config.storage().queryTimeoutSeconds());
            assertEquals(200, config.matching().maxSwipesPerSession());
            assertEquals(2.5, config.matching().suspiciousSwipeVelocity(), 0.01);
            assertFalse(config.matching().suspiciousSwipeVelocityBlockingEnabled());
        }

        @Test
        @DisplayName("Should reject unknown config keys")
        void rejectsUnknownConfigKeys() {
            String json = """
              {
                "dailyLikeLimit": 50,
                "unknownLegacyKey": 123
              }
              """;

            IllegalStateException error =
                    assertThrows(IllegalStateException.class, () -> ApplicationStartup.fromJson(json));
            assertTrue(error.getMessage().contains("unknownLegacyKey"));
        }

        @Test
        @DisplayName("Should parse algorithm thresholds")
        void parsesAlgorithmThresholds() {
            String json = """
                    {
                      "nearbyDistanceKm": 10,
                      "closeDistanceKm": 25,
                      "similarAgeDiff": 3,
                      "compatibleAgeDiff": 8,
                      "minSharedInterests": 5
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(10, config.algorithm().nearbyDistanceKm());
            assertEquals(25, config.algorithm().closeDistanceKm());
            assertEquals(3, config.algorithm().similarAgeDiff());
            assertEquals(8, config.algorithm().compatibleAgeDiff());
            assertEquals(5, config.matching().minSharedInterests());
        }

        @Test
        @DisplayName("Should parse weight fields")
        void parsesWeightFields() {
            String json = """
                    {
                      "distanceWeight": 0.25,
                      "ageWeight": 0.15,
                      "interestWeight": 0.20,
                      "lifestyleWeight": 0.20,
                      "paceWeight": 0.10,
                      "responseWeight": 0.10
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(0.25, config.matching().distanceWeight(), 0.01);
            assertEquals(0.15, config.matching().ageWeight(), 0.01);
            assertEquals(0.20, config.matching().interestWeight(), 0.01);
            assertEquals(0.20, config.matching().lifestyleWeight(), 0.01);
            assertEquals(0.10, config.matching().paceWeight(), 0.01);
            assertEquals(0.10, config.matching().responseWeight(), 0.01);
        }

        @Test
        @DisplayName("Should parse cleanup settings")
        void parsesCleanupSettings() {
            String json = """
                    {
                      "cleanupRetentionDays": 60
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(60, config.safety().cleanupRetentionDays());
        }

        @Test
        @DisplayName("Should parse timezone")
        void parsesTimezone() {
            String json = """
                    {
                      "userTimeZone": "America/New_York"
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals("America/New_York", config.safety().userTimeZone().getId());
        }

        @Test
        @DisplayName("Should fall back to defaults for invalid timezone values")
        void fallsBackToDefaultsForInvalidTimezone() {
            String json = """
          {
            "userTimeZone": "Not/A_Real_Zone"
          }
          """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(
                    AppConfig.defaults().safety().userTimeZone(),
                    config.safety().userTimeZone());
        }

        @Test
        @DisplayName("Should use defaults for missing fields")
        void usesDefaultsForMissing() {
            String json = "{}";

            AppConfig config = ApplicationStartup.fromJson(json);
            AppConfig defaults = AppConfig.defaults();

            assertEquals(defaults.matching().dailyLikeLimit(), config.matching().dailyLikeLimit());
            assertEquals(
                    defaults.matching().rematchCooldownHours(),
                    config.matching().rematchCooldownHours());
            assertEquals(
                    defaults.matching().sharedInterestsPreviewCount(),
                    config.matching().sharedInterestsPreviewCount());
            assertEquals(defaults.validation().minAge(), config.validation().minAge());
            assertEquals(defaults.validation().maxAge(), config.validation().maxAge());
            assertEquals(
                    defaults.validation().maxMessageLength(),
                    config.validation().maxMessageLength());
            assertEquals(
                    defaults.validation().maxProfileNoteLength(),
                    config.validation().maxProfileNoteLength());
        }

        @Test
        @DisplayName("Should handle empty JSON string")
        void handlesEmptyJson() {
            AppConfig config = ApplicationStartup.fromJson("{}");
            assertNotNull(config);
        }
    }

    @Nested
    @DisplayName("Field coverage")
    class FieldCoverage {

        @Test
        @DisplayName("Should parse undo settings")
        void parsesUndoSettings() {
            String json = """
                    {
                      "undoWindowSeconds": 60
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(60, config.safety().undoWindowSeconds());
        }

        @Test
        @DisplayName("Should parse achievement tiers")
        void parsesAchievementTiers() {
            String json = """
                    {
                      "achievementMatchTier1": 2,
                      "achievementMatchTier2": 10,
                      "achievementMatchTier3": 25,
                      "achievementMatchTier4": 75,
                      "achievementMatchTier5": 200
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(2, config.safety().achievementMatchTier1());
            assertEquals(10, config.safety().achievementMatchTier2());
            assertEquals(25, config.safety().achievementMatchTier3());
            assertEquals(75, config.safety().achievementMatchTier4());
            assertEquals(200, config.safety().achievementMatchTier5());
        }

        @Test
        @DisplayName("Should parse validation settings")
        void parsesValidationSettings() {
            String json = """
                    {
                      "minHeightCm": 100,
                      "maxHeightCm": 250,
                      "minDistanceKm": 5,
                      "maxNameLength": 100,
                      "minAgeRangeSpan": 3
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(100, config.validation().minHeightCm());
            assertEquals(250, config.validation().maxHeightCm());
            assertEquals(5, config.validation().minDistanceKm());
            assertEquals(100, config.validation().maxNameLength());
            assertEquals(3, config.validation().minAgeRangeSpan());
        }

        @Test
        @DisplayName("Should parse matching preview settings")
        void parsesMatchingPreviewSettings() {
            String json = """
                    {
                      "sharedInterestsPreviewCount": 5,
                      "minSharedInterests": 2
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(5, config.matching().sharedInterestsPreviewCount());
            assertEquals(2, config.matching().minSharedInterests());
        }

        @Test
        @DisplayName("Should parse response time settings")
        void parsesResponseTimeSettings() {
            String json = """
                    {
                      "responseTimeExcellentHours": 2,
                      "responseTimeGreatHours": 6,
                      "responseTimeGoodHours": 12,
                      "paceCompatibilityThreshold": 60
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(2, config.algorithm().responseTimeExcellentHours());
            assertEquals(6, config.algorithm().responseTimeGreatHours());
            assertEquals(12, config.algorithm().responseTimeGoodHours());
            assertEquals(60, config.algorithm().paceCompatibilityThreshold());
        }
    }
}
