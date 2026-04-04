package datingapp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.storage.DatabaseManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";
    private InputStream originalIn;

    @BeforeEach
    void setUp() {
        originalIn = System.in;
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:main-bootstrap-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(PROFILE_PROPERTY);
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
        System.setIn(new ByteArrayInputStream("0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

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
