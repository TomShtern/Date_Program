package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
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
    // Keep this guard disabled until the live source tree actually satisfies the rule.
    // The current codebase still has runtime ZoneId.systemDefault() call sites in
    // presentation-layer code, and there is no src/main/java/datingapp/core/time/TimePolicy.java
    // implementation to route them through yet. Re-enable only after the remaining
    // runtime call sites are migrated and the allowlist can be reduced to the genuine
    // bootstrap-only exceptions.
    @Disabled("TRACKER: WU-14 | owner: TimePolicy rollout owner | "
            + "current state: src/main/java still contains runtime ZoneId.systemDefault() call sites in UI/CLI "
            + "presentation code and no TimePolicy implementation exists yet; "
            + "exit criteria: all runtime timezone lookups route through the dedicated policy layer and only approved "
            + "bootstrap exceptions remain | "
            + "TODO deadline: 2026-04-30")
    void noFeatureCodeUsesZoneIdSystemDefault() throws IOException {
        List<String> violations = findViolations("ZoneId.systemDefault()", ZONE_DEFAULT_ALLOWLIST);
        assertTrue(violations.isEmpty(), "ZoneId.systemDefault() violations found:\n" + String.join("\n", violations));
    }

    @Test
    void noFeatureCodeUsesAppConfigDefaults() throws IOException {
        // This guard can be enabled now: the current src/main/java scan has no runtime
        // AppConfig.defaults() usage, and the remaining matches are documentation-only.
        List<String> violations = findViolations("AppConfig.defaults()", CONFIG_DEFAULTS_ALLOWLIST);
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
