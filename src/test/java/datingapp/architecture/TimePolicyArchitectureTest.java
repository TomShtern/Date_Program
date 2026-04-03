package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Architecture guardrail tests that detect policy violations in feature code.
 * Scans Java source files for forbidden patterns.
 */
class TimePolicyArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    static final Set<String> ZONE_DEFAULT_ALLOWLIST = Set.of("AppConfig.java", "AppClock.java");

    static final Set<String> CONFIG_DEFAULTS_ALLOWLIST = Set.of("ApplicationStartup.java", "StorageFactory.java");

    @Test
    void noFeatureCodeUsesZoneIdSystemDefault() throws IOException {
        List<String> violations = findViolations("ZoneId.systemDefault()", ZONE_DEFAULT_ALLOWLIST);
        assertTrue(violations.isEmpty(), "ZoneId.systemDefault() violations found:\n" + String.join("\n", violations));
    }

    @Test
    void noFeatureCodeUsesAppConfigDefaults() throws IOException {
        // This guard can be enabled now: the current src/main/java scan has no runtime
        // AppConfig.defaults() usage, and the remaining matches are documentation-only.
        List<String> violations = ArchitectureTestSupport.collectMethodInvocationViolations(
                SOURCE_ROOT,
                path -> CONFIG_DEFAULTS_ALLOWLIST.contains(path.getFileName().toString()),
                invocation -> false,
                "AppConfig",
                "defaults");
        assertTrue(violations.isEmpty(), "AppConfig.defaults() violations found:\n" + String.join("\n", violations));
    }

    private List<String> findViolations(String pattern, Set<String> allowlist) throws IOException {
        return ArchitectureTestSupport.collectViolations(
                SOURCE_ROOT,
                path -> allowlist.contains(path.getFileName().toString()),
                line -> {
                    String stripped = line.stripLeading();
                    return stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*");
                },
                line -> line.contains(pattern));
    }
}
