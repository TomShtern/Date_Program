package datingapp.core;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public final class AppClock {

    private static volatile Clock clock = Clock.systemUTC();

    private AppClock() {
        // Utility class
    }

    /** Returns the current instant according to the application clock. */
    public static Instant now() {
        return clock.instant();
    }

    /** Returns today's date according to the application clock. */
    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Returns today's date in the specified timezone. */
    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Returns the underlying clock (useful for APIs that accept a Clock). */
    public static Clock clock() {
        return clock;
    }

    /**
     * Sets a fixed clock that always returns the given instant.
     * Primarily for testing.
     *
     * @param fixedInstant the instant to freeze time at
     */
    public static void setFixed(Instant fixedInstant) {
        Objects.requireNonNull(fixedInstant, "fixedInstant cannot be null");
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
    }

    /**
     * Replaces the application clock with the given one.
     * Primarily for testing.
     *
     * @param newClock the clock to use
     */
    public static void setClock(Clock newClock) {
        Objects.requireNonNull(newClock, "clock cannot be null");
        clock = newClock;
    }

    /** Resets the clock to the system UTC clock. Call in test teardown. */
    public static void reset() {
        clock = Clock.systemUTC();
    }
}
