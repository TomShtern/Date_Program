package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ArchitectureTestSupport")
class ArchitectureTestSupportTest {

    @Test
    @DisplayName("method invocation scan ignores string literals that mention AppConfig.defaults()")
    void methodInvocationScanIgnoresStringLiterals(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("Example.java");
        Files.writeString(sourceFile, """
                package example;

                class Example {
                    String text() {
                        return \"AppConfig.defaults() is documentation text\";
                    }
                }
                """);

        List<String> violations = ArchitectureTestSupport.collectMethodInvocationViolations(
                tempDir, path -> false, invocation -> false, "AppConfig", "defaults");

        assertTrue(violations.isEmpty(), "string literals should not be treated as method invocation violations");
    }
}
