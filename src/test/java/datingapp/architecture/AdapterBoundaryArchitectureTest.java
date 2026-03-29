package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
        if (!java.nio.file.Files.isDirectory(vmRoot)) {
            fail("Expected ViewModel directory does not exist: " + vmRoot
                    + ". If the directory was renamed/moved or SOURCE_ROOT is incorrect, update this test.");
        }
        List<String> violations = ArchitectureTestSupport.collectImportViolations(
                vmRoot,
                path -> VIEWMODEL_STORAGE_ALLOWLIST.contains(path.getFileName().toString()),
                VIEWMODEL_STORAGE_TYPE_ALLOWLIST::contains,
                importText -> importText.startsWith("import datingapp.core.storage."));
        assertTrue(
                violations.isEmpty(),
                "ViewModels import core.storage directly (should use UiDataAdapters):\n"
                        + String.join("\n", violations));
    }

    @Test
    void corePackageDoesNotImportFrameworks() throws IOException {
        Path coreRoot = SOURCE_ROOT.resolve("datingapp/core");
        assertTrue(
                java.nio.file.Files.isDirectory(coreRoot),
                "Expected core directory does not exist: " + coreRoot
                        + ". If the package was intentionally removed/renamed, update this test.");
        List<String> violations = ArchitectureTestSupport.collectImportViolations(
                coreRoot, path -> false, line -> false, line -> FORBIDDEN_CORE_IMPORTS.stream()
                        .anyMatch(line::startsWith));
        assertTrue(violations.isEmpty(), "Core package imports framework libraries:\n" + String.join("\n", violations));
    }
}
