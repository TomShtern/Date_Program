package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.Main;
import datingapp.storage.DatabaseManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

@Timeout(10)
@DisplayName("Main bootstrap lifecycle")
class MainBootstrapLifecycleTest {

    private static final String CONFIG_OVERRIDE_PROPERTY = "datingapp.config";
    private static final String PROFILE_PROPERTY = "datingapp.db.profile";
    private InputStream originalIn;
    private Path tempConfigFile;

    @BeforeEach
    void setUp() throws Exception {
        originalIn = System.in;
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        ApplicationStartup.setEnvironmentLookupForTests(key -> null);
        System.setProperty(PROFILE_PROPERTY, "test");

        tempConfigFile = Files.createTempFile("main-bootstrap-config-", ".json");
        Files.writeString(tempConfigFile, """
                {
                  "databaseDialect": "H2",
                  "databaseUrl": "%s",
                  "databaseUsername": "sa",
                  "queryTimeoutSeconds": 30
                }
                """.formatted(
                        "jdbc:h2:mem:main-bootstrap-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        System.setProperty(CONFIG_OVERRIDE_PROPERTY, tempConfigFile.toString());
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        ApplicationStartup.setEnvironmentLookupForTests(null);
        System.clearProperty(PROFILE_PROPERTY);
        System.clearProperty(CONFIG_OVERRIDE_PROPERTY);
        if (tempConfigFile != null) {
            try {
                Files.deleteIfExists(tempConfigFile);
            } catch (Exception _) {
                // Best-effort temp-file cleanup for test isolation.
            }
        }
    }

    @Test
    @DisplayName("main bootstraps and shuts down cleanly on EOF")
    void mainBootstrapsAndShutsDownCleanlyOnEof() {
        System.setIn(new ByteArrayInputStream(new byte[0]));

        Main.main(new String[0]);

        IllegalStateException error = assertThrows(IllegalStateException.class, ApplicationStartup::getServices);
        assertTrue(error.getMessage().contains("initialize"));
    }

    @Test
    @DisplayName("main renders logged-out menu and exits on explicit zero selection")
    void mainRendersLoggedOutMenuAndExitsOnExplicitZeroSelection() {
        System.setIn(new ByteArrayInputStream("0\n".getBytes(StandardCharsets.UTF_8)));

        Logger mainLogger = (Logger) LoggerFactory.getLogger(Main.class);
        Level previousLevel = mainLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        mainLogger.setLevel(Level.INFO);
        appender.start();
        mainLogger.addAppender(appender);

        try {
            Main.main(new String[0]);

            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("Welcome to Dating App")));
            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("Current User: [None]")));
            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("Goodbye!")));

            IllegalStateException error = assertThrows(IllegalStateException.class, ApplicationStartup::getServices);
            assertTrue(error.getMessage().contains("initialize"));
        } finally {
            mainLogger.detachAppender(appender);
            mainLogger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
