package datingapp.core.matching;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Structured moderation audit event.
 *
 * <p>Allowed context keys are restricted to operational metadata used for moderation auditing and retention-safe
 * telemetry. Any non-whitelisted keys are dropped, and values that appear to contain PII are redacted.
 *
 * <p>Recommended context keys include:
 *
 * <ul>
 *   <li>{@code moderation_reason_code}
 *   <li>{@code policy_id}
 *   <li>{@code action_source}
 *   <li>operational flags/metrics in snake_case (for example {@code report_count}, {@code threshold},
 *       {@code block_requested}, {@code conversation_archived})
 * </ul>
 */
public record ModerationAuditEvent(
        Instant timestamp, UUID actorId, UUID targetId, Action action, Outcome outcome, Map<String, String> context) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> ALLOWED_CONTEXT_KEYS = Set.of(
            "moderation_reason_code",
            "policy_id",
            "action_source",
            "error_code",
            "report_count",
            "threshold",
            "block_requested",
            "ban_persisted",
            "block_removed",
            "conversation_storage_configured",
            "conversation_archived",
            "match_updated",
            "description_provided",
            "description_length",
            "max_description_length");

    private static final Map<String, String> CONTEXT_KEY_ALIASES = Map.ofEntries(
            Map.entry("reason", "moderation_reason_code"),
            Map.entry("errorCode", "error_code"),
            Map.entry("reportCount", "report_count"),
            Map.entry("blockRequested", "block_requested"),
            Map.entry("banPersisted", "ban_persisted"),
            Map.entry("blockRemoved", "block_removed"),
            Map.entry("conversationStorageConfigured", "conversation_storage_configured"),
            Map.entry("conversationArchived", "conversation_archived"),
            Map.entry("matchUpdated", "match_updated"),
            Map.entry("descriptionProvided", "description_provided"),
            Map.entry("descriptionLength", "description_length"),
            Map.entry("maxDescriptionLength", "max_description_length"));

    public enum Action {
        REPORT,
        BLOCK,
        UNBLOCK,
        AUTO_BAN
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public ModerationAuditEvent {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(outcome, "outcome cannot be null");
        context = sanitizeContext(context);
    }

    private static Map<String, String> sanitizeContext(Map<String, String> rawContext) {
        if (rawContext == null || rawContext.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawContext.entrySet()) {
            String canonicalKey = canonicalContextKey(entry.getKey());
            if (canonicalKey == null) {
                continue;
            }

            String value = sanitizeValue(canonicalKey, entry.getValue());
            if (value == null || value.isBlank()) {
                continue;
            }
            sanitized.put(canonicalKey, value);
        }

        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static String canonicalContextKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        if (ALLOWED_CONTEXT_KEYS.contains(rawKey)) {
            return rawKey;
        }
        String alias = CONTEXT_KEY_ALIASES.get(rawKey);
        if (alias == null) {
            return null;
        }
        return ALLOWED_CONTEXT_KEYS.contains(alias) ? alias : null;
    }

    private static String sanitizeValue(String key, String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (looksLikePii(key, trimmed)) {
            return REDACTED;
        }

        return trimmed;
    }

    private static boolean looksLikePii(String key, String value) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        if (EMAIL_PATTERN.matcher(value).find()) {
            return true;
        }

        if (normalizedKey.contains("email")
                || normalizedKey.contains("username")
                || normalizedKey.contains("user_name")) {
            return true;
        }

        return (normalizedKey.contains("description")
                        || normalizedKey.contains("message")
                        || normalizedKey.contains("note"))
                && containsPotentialFreeText(value);
    }

    private static boolean containsPotentialFreeText(String value) {
        return value.length() > 32 && value.indexOf(' ') >= 0;
    }
}
