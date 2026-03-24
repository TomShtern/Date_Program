package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

@DisplayName("ModerationAuditLogger")
class ModerationAuditLoggerTest {

    @Test
    @DisplayName("logs structured key-value fields")
    void logsStructuredKeyValueFields() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger("audit.moderation");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ModerationAuditLogger auditLogger = new ModerationAuditLogger();
            UUID actorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            ModerationAuditEvent event = new ModerationAuditEvent(
                    Instant.parse("2026-03-24T10:15:30Z"),
                    actorId,
                    targetId,
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.SUCCESS,
                    Map.of("matchUpdated", "true", "conversationArchived", "false"));

            auditLogger.log(event);

            assertEquals(1, appender.list.size());
            ILoggingEvent loggingEvent = appender.list.get(0);
            assertEquals(Level.INFO, loggingEvent.getLevel());
            assertEquals("moderation_audit", loggingEvent.getFormattedMessage());

            List<KeyValuePair> keyValuePairs = loggingEvent.getKeyValuePairs();
            assertTrue(keyValuePairs.stream()
                    .anyMatch(pair -> pair.key.equals("timestamp") && pair.value.equals(event.timestamp())));
            assertTrue(
                    keyValuePairs.stream().anyMatch(pair -> pair.key.equals("actorId") && pair.value.equals(actorId)));
            assertTrue(keyValuePairs.stream()
                    .anyMatch(pair -> pair.key.equals("targetId") && pair.value.equals(targetId)));
            assertTrue(keyValuePairs.stream()
                    .anyMatch(pair -> pair.key.equals("action") && pair.value.equals(event.action())));
            assertTrue(keyValuePairs.stream()
                    .anyMatch(pair -> pair.key.equals("outcome") && pair.value.equals(event.outcome())));
            assertTrue(keyValuePairs.stream()
                    .anyMatch(pair -> pair.key.equals("context") && pair.value.equals(event.context())));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
