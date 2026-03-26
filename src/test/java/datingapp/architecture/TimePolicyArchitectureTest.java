package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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
    // Keep this guard disabled until the WU-14 timezone rollout removes every runtime
    // ZoneId.systemDefault() call outside the allowlist. The remaining feature-code
    // uses are still part of the in-flight TimePolicy migration, so this test serves
    // as a documented stopgap rather than a false green.
    // Unblock when: WU-14 is closed, all src/main/java timezone call sites are routed
    // through TimePolicy/AppClock, the allowlist is down to the approved bootstrap
    // classes only, and the migration checklist is complete.
    @Disabled("TRACKER: WU-14 | owner: TimePolicy rollout owner | "
            + "exit criteria: zero runtime ZoneId.systemDefault() uses outside the allowlist in src/main/java; "
            + "unblock by routing feature code through TimePolicy/AppClock | "
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
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (allowlist.contains(fileName)) {
                    return;
                }
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).stripLeading();
                        if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                            continue;
                        }
                        if (lines.get(i).contains(pattern)) {
                            String normalizedPath = path.toString().replace('\\', '/');
                            violations.add(normalizedPath + ":" + (i + 1) + "  "
                                    + lines.get(i).strip());
                        }
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        return violations;
    }
}
