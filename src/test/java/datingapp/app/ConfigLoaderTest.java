package datingapp.app;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
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

            assertEquals(25, config.dailyLikeLimit());
            assertEquals(75, config.maxDistanceKm());
            assertEquals(21, config.minAge());
        }

        @Test
        @DisplayName("Should return defaults when file not found")
        void returnsDefaultsWhenFileNotFound(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("nonexistent.json");

            AppConfig config = ApplicationStartup.load(nonExistent);

            // Should have default values
            assertEquals(AppConfig.defaults().dailyLikeLimit(), config.dailyLikeLimit());
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
                      "dailyPassLimit": 200,
                      "maxInterests": 15,
                      "maxPhotos": 8,
                      "maxBioLength": 1000
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(10, config.autoBanThreshold());
            assertEquals(50, config.dailyLikeLimit());
            assertEquals(3, config.dailySuperLikeLimit());
            assertEquals(200, config.dailyPassLimit());
            assertEquals(15, config.maxInterests());
            assertEquals(8, config.maxPhotos());
            assertEquals(1000, config.maxBioLength());
        }

        @Test
        @DisplayName("Should parse session fields")
        void parsesSessionFields() {
            String json = """
                    {
                      "sessionTimeoutMinutes": 60,
                      "maxSwipesPerSession": 200,
                      "suspiciousSwipeVelocity": 2.5
                    }
                    """;

            AppConfig config = ApplicationStartup.fromJson(json);

            assertEquals(60, config.sessionTimeoutMinutes());
            assertEquals(200, config.maxSwipesPerSession());
            assertEquals(2.5, config.suspiciousSwipeVelocity(), 0.01);
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

            assertEquals(10, config.nearbyDistanceKm());
            assertEquals(25, config.closeDistanceKm());
            assertEquals(3, config.similarAgeDiff());
            assertEquals(8, config.compatibleAgeDiff());
            assertEquals(5, config.minSharedInterests());
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

            assertEquals(0.25, config.distanceWeight(), 0.01);
            assertEquals(0.15, config.ageWeight(), 0.01);
            assertEquals(0.20, config.interestWeight(), 0.01);
            assertEquals(0.20, config.lifestyleWeight(), 0.01);
            assertEquals(0.10, config.paceWeight(), 0.01);
            assertEquals(0.10, config.responseWeight(), 0.01);
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

            assertEquals(60, config.cleanupRetentionDays());
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

            assertEquals("America/New_York", config.userTimeZone().getId());
        }

        @Test
        @DisplayName("Should use defaults for missing fields")
        void usesDefaultsForMissing() {
            String json = "{}";

            AppConfig config = ApplicationStartup.fromJson(json);
            AppConfig defaults = AppConfig.defaults();

            assertEquals(defaults.dailyLikeLimit(), config.dailyLikeLimit());
            assertEquals(defaults.minAge(), config.minAge());
            assertEquals(defaults.maxAge(), config.maxAge());
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

            assertEquals(60, config.undoWindowSeconds());
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

            assertEquals(2, config.achievementMatchTier1());
            assertEquals(10, config.achievementMatchTier2());
            assertEquals(25, config.achievementMatchTier3());
            assertEquals(75, config.achievementMatchTier4());
            assertEquals(200, config.achievementMatchTier5());
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

            assertEquals(100, config.minHeightCm());
            assertEquals(250, config.maxHeightCm());
            assertEquals(5, config.minDistanceKm());
            assertEquals(100, config.maxNameLength());
            assertEquals(3, config.minAgeRangeSpan());
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

            assertEquals(2, config.responseTimeExcellentHours());
            assertEquals(6, config.responseTimeGreatHours());
            assertEquals(12, config.responseTimeGoodHours());
            assertEquals(60, config.paceCompatibilityThreshold());
        }
    }
}
