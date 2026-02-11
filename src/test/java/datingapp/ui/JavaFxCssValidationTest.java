package datingapp.ui;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.*;
import datingapp.core.service.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Validates JavaFX CSS files using JavaFX's own CSS parser.
 *
 * <p>This is the PROPER way to validate JavaFX CSS - using JavaFX itself, not web CSS linters that
 * don't understand JavaFX pseudo-classes like :focused, :pressed, :selected, etc.
 *
 * <p>Run with: mvn test -Dtest=JavaFxCssValidationTest
 */
@DisplayName("JavaFX CSS Validation")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class JavaFxCssValidationTest {

    private static final String[] CSS_FILES = {"/css/theme.css", "/css/light-theme.css"};

    private static boolean javafxInitialized = false;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        if (javafxInitialized) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);

        Logger.getLogger("com.sun.javafx.application.PlatformImpl").setLevel(Level.SEVERE);
        Logger.getLogger("javafx").setLevel(Level.SEVERE);

        // Initialize JavaFX toolkit
        try {
            Platform.startup(() -> {
                javafxInitialized = true;
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // Already initialized
            javafxInitialized = true;
            latch.countDown();
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX initialization timed out");
    }

    @Test
    @DisplayName("theme.css should be valid JavaFX CSS")
    void themecssShouldBeValid() throws Exception {
        validateCssFile("/css/theme.css");
    }

    @Test
    @DisplayName("light-theme.css should be valid JavaFX CSS")
    void lightThemeCssShouldBeValid() throws Exception {
        validateCssFile("/css/light-theme.css");
    }

    @Test
    @DisplayName("All CSS files should exist and be loadable")
    void allCssFilesShouldExist() {
        for (String cssFile : CSS_FILES) {
            URL url = getClass().getResource(cssFile);
            assertNotNull(url, "CSS file not found: " + cssFile);
        }
    }

    @Test
    @DisplayName("CSS files should have valid syntax structure")
    void cssFilesShouldHaveValidSyntax() throws Exception {
        for (String cssFile : CSS_FILES) {
            URL url = getClass().getResource(cssFile);
            assertNotNull(url, "CSS file not found: " + cssFile);

            String content = Files.readString(Path.of(url.toURI()));

            // Check for basic structural issues
            List<String> errors = validateCssSyntax(content, cssFile);

            if (!errors.isEmpty()) {
                fail("CSS syntax errors in " + cssFile + ":\n" + String.join("\n", errors));
            }
        }
    }

    /**
     * Validates a CSS file by loading it into a JavaFX Scene and checking for parser errors.
     *
     * <p>JavaFX logs CSS parsing errors to System.err, so we capture those.
     */
    private void validateCssFile(String cssPath) throws Exception {
        URL cssUrl = getClass().getResource(cssPath);
        assertNotNull(cssUrl, "CSS file not found: " + cssPath);

        AtomicReference<List<String>> errors = new AtomicReference<>(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            // Capture stderr to catch JavaFX CSS parser errors
            PrintStream originalErr = System.err;
            ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
            PrintStream captureStream = new PrintStream(errCapture);

            try {
                System.setErr(captureStream);

                // Create a scene and apply the stylesheet
                Pane root = new Pane();
                Scene scene = new Scene(root, 100, 100);
                scene.getStylesheets().add(cssUrl.toExternalForm());

                // Force CSS to be parsed by requesting a layout pass
                root.applyCss();

                // Restore stderr and check for errors
                System.setErr(originalErr);
                captureStream.flush();

                String errorOutput = errCapture.toString();
                if (!errorOutput.isEmpty()) {
                    // Parse the error output for CSS-related errors
                    List<String> cssErrors = parseCssErrors(errorOutput);
                    errors.set(cssErrors);
                }

            } catch (Exception e) {
                errors.get().add("Exception during CSS validation: " + e.getMessage());
            } finally {
                System.setErr(originalErr);
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS), "CSS validation timed out");

        List<String> foundErrors = errors.get();
        if (!foundErrors.isEmpty()) {
            fail("JavaFX CSS parser errors in " + cssPath + ":\n" + String.join("\n", foundErrors));
        }
    }

    /**
     * Parses JavaFX CSS error messages from stderr output.
     *
     * <p>JavaFX CSS errors look like: "WARNING: CSS Error parsing..." or contain "Could not
     * resolve" messages.
     */
    private List<String> parseCssErrors(String errorOutput) {
        List<String> errors = new ArrayList<>();
        String[] lines = errorOutput.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            // Filter for actual CSS parsing errors, not general warnings
            if (trimmed.contains("CSS Error")
                    || trimmed.contains("Could not resolve")
                    || trimmed.contains("Unknown property")
                    || trimmed.contains("Parse error")
                    || (trimmed.contains("WARNING") && trimmed.contains("CSS"))) {

                // Ignore known false positives (none currently, but this is where we'd add them)
                errors.add(trimmed);
            }
        }

        return errors;
    }

    /**
     * Performs basic CSS syntax validation without JavaFX.
     *
     * <p>Checks for: - Unbalanced braces - Empty selectors - Invalid property format
     */
    private List<String> validateCssSyntax(String content, String filename) {
        List<String> errors = new ArrayList<>();

        // Check for balanced braces
        int braceCount = 0;
        int lineNumber = 0;
        for (String line : content.split("\n")) {
            lineNumber++;
            for (char c : line.toCharArray()) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (braceCount < 0) {
                    errors.add(filename + ":" + lineNumber + " - Unexpected closing brace");
                    braceCount = 0; // Reset to continue checking
                }
            }
        }

        if (braceCount != 0) {
            int missingClosing = Math.abs(braceCount);
            errors.add(filename + " - Unbalanced braces (missing " + missingClosing + " closing brace(s))");
        }

        // Check for empty rule blocks (selector {})
        Pattern emptyRule = Pattern.compile("\\{\\s*\\}");
        Matcher emptyMatcher = emptyRule.matcher(content);
        while (emptyMatcher.find()) {
            int pos = emptyMatcher.start();
            int line = content.substring(0, pos).split("\n").length;
            errors.add(filename + ":" + line + " - Empty rule block");
        }

        // Check for missing semicolons (property without ; before })
        // This is a common error that JavaFX CSS parser catches
        Pattern missingSemicolon = Pattern.compile("[a-zA-Z0-9)\"']\\s*\\n\\s*\\}");
        Matcher semiMatcher = missingSemicolon.matcher(content);
        while (semiMatcher.find()) {
            int pos = semiMatcher.start();
            int line = content.substring(0, pos).split("\n").length;
            // Only flag if not a comment
            String context = content.substring(Math.max(0, pos - 50), pos);
            if (!context.contains("/*") && !context.contains("//")) {
                errors.add(filename + ":" + line + " - Possible missing semicolon before closing brace");
            }
        }

        return errors;
    }
}
