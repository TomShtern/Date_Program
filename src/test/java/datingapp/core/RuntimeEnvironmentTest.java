package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("RuntimeEnvironment")
class RuntimeEnvironmentTest {

    @AfterEach
    void tearDown() {
        RuntimeEnvironment.clearCacheForTests();
    }

    @Test
    @DisplayName("getEnv falls back to .env file when process env is missing")
    void getEnvFallsBackToDotEnv(@TempDir Path tempDir) throws Exception {
        Path dotEnv = tempDir.resolve(".env");
        Files.write(dotEnv, List.of("DATING_APP_DB_PASSWORD=datingapp"));

        String value = RuntimeEnvironment.getEnv("DATING_APP_DB_PASSWORD", name -> null, dotEnv);

        assertEquals("datingapp", value);
    }

    @Test
    @DisplayName("lookup prefers system property over process env and .env")
    void lookupPrefersSystemProperty(@TempDir Path tempDir) throws Exception {
        Path dotEnv = tempDir.resolve(".env");
        Files.write(dotEnv, List.of("DATING_APP_DB_PASSWORD=dot-env-password"));

        String value = RuntimeEnvironment.lookup(
                "datingapp.db.password",
                "DATING_APP_DB_PASSWORD",
                name -> "property-password",
                name -> "env-password",
                dotEnv);

        assertEquals("property-password", value);
    }

    @Test
    @DisplayName("parseDotEnvLines ignores comments and strips matching quotes")
    void parseDotEnvLinesIgnoresCommentsAndStripsQuotes() {
        var parsed = RuntimeEnvironment.parseDotEnvLines(List.of(
                "# comment",
                "DATING_APP_DB_USERNAME=datingapp",
                "DATING_APP_DB_PASSWORD=\"quoted-password\"",
                "export DATING_APP_DB_URL='jdbc:postgresql://localhost:55432/datingapp'"));

        assertEquals("datingapp", parsed.get("DATING_APP_DB_USERNAME"));
        assertEquals("quoted-password", parsed.get("DATING_APP_DB_PASSWORD"));
        assertEquals("jdbc:postgresql://localhost:55432/datingapp", parsed.get("DATING_APP_DB_URL"));
        assertNull(parsed.get("MISSING_KEY"));
    }
}
