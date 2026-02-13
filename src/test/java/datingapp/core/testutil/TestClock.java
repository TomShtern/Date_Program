package datingapp.core.testutil;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;

/**
 * Test helper for controlling the application clock.
 *
 * <p>Use {@link #setFixed(Instant)} in setup and {@link #reset()} in teardown
 * to keep tests deterministic.</p>
 */
public final class TestClock {

    private TestClock() {
        // Utility class
    }

    /** Freeze time at a fixed instant for deterministic tests. */
    public static void setFixed(Instant fixedInstant) {
        Objects.requireNonNull(fixedInstant, "fixedInstant cannot be null");
        AppClock.setFixed(fixedInstant);
    }

    /** Reset the clock to system UTC. */
    public static void reset() {
        AppClock.reset();
    }
}
