package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModerationAuditEvent")
class ModerationAuditEventTest {

    @Test
    @DisplayName("copies context defensively and preserves core fields")
    void copiesContextDefensivelyAndPreservesCoreFields() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, String> context = new HashMap<>();
        context.put("reason", "SPAM");
        context.put("blockRequested", "true");

        ModerationAuditEvent event = new ModerationAuditEvent(
                Instant.parse("2026-03-24T10:15:30Z"),
                actorId,
                targetId,
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                context);

        context.put("reason", "HARASSMENT");

        assertEquals(Instant.parse("2026-03-24T10:15:30Z"), event.timestamp());
        assertEquals(actorId, event.actorId());
        assertEquals(targetId, event.targetId());
        assertEquals(ModerationAuditEvent.Action.REPORT, event.action());
        assertEquals(ModerationAuditEvent.Outcome.SUCCESS, event.outcome());
        assertEquals(Map.of("reason", "SPAM", "blockRequested", "true"), event.context());
        Map<String, String> eventContext = event.context();
        assertThrows(UnsupportedOperationException.class, () -> eventContext.put("extra", "value"));
    }
}
