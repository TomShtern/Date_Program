package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture guardrail tests that enforce adapter boundary rules.
 */
class AdapterBoundaryArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    private static final Set<String> VIEWMODEL_STORAGE_ALLOWLIST = Set.of("UiDataAdapters.java");

    /**
     * Storage type imports that are permitted in ViewModels because they appear as
     * return types in the UiDataAdapters interface contracts.
     */
    private static final Set<String> VIEWMODEL_STORAGE_TYPE_ALLOWLIST =
            Set.of("import datingapp.core.storage.PageData;");

    private static final List<String> FORBIDDEN_CORE_IMPORTS =
            List.of("import javafx.", "import org.jdbi.", "import io.javalin.", "import com.fasterxml.jackson.");

    @Test
    void viewModelsDoNotImportCoreStorage() throws IOException {
        Path vmRoot = SOURCE_ROOT.resolve("datingapp/ui/viewmodel");
        List<String> violations = new ArrayList<>();
        if (!Files.isDirectory(vmRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(vmRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (VIEWMODEL_STORAGE_ALLOWLIST.contains(fileName)) {
                    return;
                }
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).strip();
                        if (line.contains("import datingapp.core.storage.")
                                && !VIEWMODEL_STORAGE_TYPE_ALLOWLIST.contains(line)) {
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
        assertTrue(
                violations.isEmpty(),
                "ViewModels import core.storage directly (should use UiDataAdapters):\n"
                        + String.join("\n", violations));
    }

    @Test
    void corePackageDoesNotImportFrameworks() throws IOException {
        Path coreRoot = SOURCE_ROOT.resolve("datingapp/core");
        List<String> violations = new ArrayList<>();
        if (!Files.isDirectory(coreRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(coreRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        for (String forbidden : FORBIDDEN_CORE_IMPORTS) {
                            if (line.contains(forbidden)) {
                                String normalizedPath = path.toString().replace('\\', '/');
                                violations.add(normalizedPath + ":" + (i + 1) + "  " + line.strip());
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        assertTrue(violations.isEmpty(), "Core package imports framework libraries:\n" + String.join("\n", violations));
    }
}
