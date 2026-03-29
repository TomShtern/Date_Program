package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(5)
@DisplayName("ApplicationStartup bootstrap configuration")
class ApplicationStartupBootstrapTest {

    @Test
    @DisplayName("load should prefer an explicit system property override")
    void loadUsesSystemPropertyOverride(@TempDir Path tempDir) throws IOException {
        Path overrideConfig = tempDir.resolve("override-config.json");
        Files.writeString(overrideConfig, """
                {
                  "dailyLikeLimit": 17
                }
                """);

        String previous = System.getProperty("datingapp.config");
        try {
            System.setProperty("datingapp.config", overrideConfig.toString());

            AppConfig config = ApplicationStartup.load();

            assertEquals(17, config.matching().dailyLikeLimit());
        } finally {
            restoreSystemProperty("datingapp.config", previous);
        }
    }

    @Test
    @DisplayName("load should fall back to the bundled app config when override is missing")
    void loadFallsBackToBundledConfig(@TempDir Path tempDir) {
        String previous = System.getProperty("datingapp.config");
        try {
            System.setProperty(
                    "datingapp.config", tempDir.resolve("missing-config.json").toString());

            AppConfig config = ApplicationStartup.load();

            assertEquals(100, config.matching().dailyLikeLimit());
        } finally {
            restoreSystemProperty("datingapp.config", previous);
        }
    }

    @Test
    @DisplayName("applyEnvironmentOverrides should fail fast on invalid numeric values")
    void invalidNumericEnvValuesFailFast() {
        AppConfig.Builder builder = AppConfig.builder();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ApplicationStartup.applyEnvironmentOverrides(
                        builder, name -> "DATING_APP_DAILY_LIKE_LIMIT".equals(name) ? "not-a-number" : null));

        assertTrue(error.getMessage().contains("DATING_APP_DAILY_LIKE_LIMIT"));
    }

    @Test
    @DisplayName("applyEnvironmentOverrides should fail fast on invalid timezone values")
    void invalidTimezoneEnvValuesFailFast() {
        AppConfig.Builder builder = AppConfig.builder();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ApplicationStartup.applyEnvironmentOverrides(
                        builder, name -> "DATING_APP_USER_TIME_ZONE".equals(name) ? "Not/A_Zone" : null));

        assertTrue(error.getMessage().contains("DATING_APP_USER_TIME_ZONE"));
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
