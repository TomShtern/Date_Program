package datingapp.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class ArchitectureTestSupport {

    private ArchitectureTestSupport() {}

    static List<String> collectViolations(
            Path root, Predicate<Path> skipFile, Predicate<String> skipLine, Predicate<String> violationLine)
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                if (skipFile.test(path)) {
                    return;
                }
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (skipLine.test(line)) {
                            continue;
                        }
                        if (violationLine.test(line)) {
                            violations.add(formatViolation(path, i + 1, line));
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return violations;
    }

    static List<String> collectImportViolations(
            Path root, Predicate<Path> skipFile, Predicate<String> skipImport, Predicate<String> violationImport)
            throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                if (!skipFile.test(path)) {
                    javaFiles.add(path);
                }
            });
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java compiler is required to parse imports");
        }

        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, null, List.of("--enable-preview", "--release", "25"), null, sources);
            Iterable<? extends CompilationUnitTree> compilationUnits = task.parse();
            Trees trees = Trees.instance(task);

            List<String> violations = new ArrayList<>();
            for (CompilationUnitTree unit : compilationUnits) {
                Path sourcePath = Path.of(unit.getSourceFile().toUri());
                for (ImportTree importTree : unit.getImports()) {
                    String importText = importTree.toString().strip();
                    if (skipImport.test(importText)) {
                        continue;
                    }
                    if (violationImport.test(importText)) {
                        long startPosition = trees.getSourcePositions().getStartPosition(unit, importTree);
                        int lineNumber = (int) unit.getLineMap().getLineNumber(startPosition);
                        violations.add(formatViolation(sourcePath, lineNumber, importText));
                    }
                }
            }
            return violations;
        }
    }

    private static String formatViolation(Path path, int lineNumber, String line) {
        return path.toString().replace('\\', '/') + ":" + lineNumber + "  " + line.strip();
    }
}
