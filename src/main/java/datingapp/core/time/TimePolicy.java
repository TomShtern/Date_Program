package datingapp.core.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Single source of truth for time operations.
 * Feature code must use this instead of {@code ZoneId.systemDefault()}
 * or {@code AppConfig.defaults()}.
 */
public interface TimePolicy {

    /** Returns the current instant (delegates to AppClock). */
    Instant now();

    /** Returns today's date in the user's configured timezone. */
    LocalDate today();

    /** Returns the configured user timezone. */
    ZoneId userZone();

    /** Returns the formatter with the user's timezone applied. */
    DateTimeFormatter withUserZone(DateTimeFormatter formatter);
}
