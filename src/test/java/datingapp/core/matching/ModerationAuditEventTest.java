package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModerationAuditEvent")
class ModerationAuditEventTest {

    @Test
    @DisplayName("sanitizes context by whitelisting allowed keys")
    void sanitizesContextByWhitelistingAllowedKeys() {
        ModerationAuditEvent event = new ModerationAuditEvent(
                Instant.parse("2026-03-24T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                Map.of(
                        "moderation_reason_code", "SPAM",
                        "policy_id", "POLICY-42",
                        "action_source", "auto_rule",
                        "description", "free text should not be retained",
                        "username", "john_doe"));

        assertEquals("SPAM", event.context().get("moderation_reason_code"));
        assertEquals("POLICY-42", event.context().get("policy_id"));
        assertEquals("auto_rule", event.context().get("action_source"));
        assertFalse(event.context().containsKey("description"));
        assertFalse(event.context().containsKey("username"));
    }

    @Test
    @DisplayName("redacts pii-looking values in allowed fields")
    void redactsPiiLookingValuesInAllowedFields() {
        ModerationAuditEvent event = new ModerationAuditEvent(
                Instant.parse("2026-03-24T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                Map.of(
                        "moderation_reason_code", "reporter@example.com",
                        "policy_id", "POLICY-42",
                        "action_source", "admin user text with spaces"));

        assertEquals("[REDACTED]", event.context().get("moderation_reason_code"));
        assertEquals("POLICY-42", event.context().get("policy_id"));
        assertTrue(event.context().containsKey("action_source"));
    }

    @Test
    @DisplayName("normalizes legacy camelCase keys to canonical snake_case")
    void normalizesLegacyCamelCaseKeysToCanonicalSnakeCase() {
        ModerationAuditEvent event = new ModerationAuditEvent(
                Instant.parse("2026-03-24T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                Map.of("reason", "SPAM", "matchUpdated", "true", "reportCount", "3"));

        assertEquals("SPAM", event.context().get("moderation_reason_code"));
        assertEquals("true", event.context().get("match_updated"));
        assertEquals("3", event.context().get("report_count"));
        assertFalse(event.context().containsKey("reason"));
        assertFalse(event.context().containsKey("matchUpdated"));
        assertFalse(event.context().containsKey("reportCount"));
    }
}
