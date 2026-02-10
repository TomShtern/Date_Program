package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("AppClock")
class AppClockTest {

    @AfterEach
    void tearDown() {
        AppClock.reset();
    }

    @Nested
    @DisplayName("Default behavior")
    class DefaultBehavior {

        @Test
        @DisplayName("now() returns current system time (within tolerance)")
        void nowReturnsCurrentTime() {
            Instant before = AppClock.clock().instant();
            Instant result = AppClock.now();
            Instant after = AppClock.clock().instant();

            assertFalse(result.isBefore(before.minus(1, ChronoUnit.SECONDS)));
            assertFalse(result.isAfter(after.plus(1, ChronoUnit.SECONDS)));
        }

        @Test
        @DisplayName("today() returns current date")
        void todayReturnsCurrentDate() {
            LocalDate result = AppClock.today();
            LocalDate expected = LocalDate.now(AppClock.clock());
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("today(zone) returns date in specified timezone")
        void todayWithZoneReturnsCorrectDate() {
            ZoneId tokyo = ZoneId.of("Asia/Tokyo");
            LocalDate result = AppClock.today(tokyo);
            LocalDate expected = LocalDate.now(AppClock.clock().withZone(tokyo));
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Fixed clock")
    class FixedClock {

        @Test
        @DisplayName("setFixed() freezes time at given instant")
        void setFixedFreezesTime() {
            Instant fixed = Instant.parse("2025-06-15T10:30:00Z");
            AppClock.setFixed(fixed);

            assertEquals(fixed, AppClock.now());
            assertEquals(fixed, AppClock.now()); // Stays same
        }

        @Test
        @DisplayName("setFixed() freezes today() date")
        void setFixedFreezesToday() {
            Instant fixed = Instant.parse("2025-12-25T10:00:00Z");
            AppClock.setFixed(fixed);

            assertEquals(LocalDate.of(2025, 12, 25), AppClock.today());
        }

        @Test
        @DisplayName("throws on null fixedInstant")
        void throwsOnNullInstant() {
            assertThrows(NullPointerException.class, () -> AppClock.setFixed(null));
        }
    }

    @Nested
    @DisplayName("Custom clock")
    class CustomClock {

        @Test
        @DisplayName("setClock() uses provided clock")
        void setClockUsesProvidedClock() {
            Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
            Clock customClock = Clock.fixed(fixed, ZoneId.of("UTC"));
            AppClock.setClock(customClock);

            assertEquals(fixed, AppClock.now());
        }

        @Test
        @DisplayName("throws on null clock")
        void throwsOnNullClock() {
            assertThrows(NullPointerException.class, () -> AppClock.setClock(null));
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("reset() restores system clock after setFixed()")
        void resetRestoresSystemClock() {
            AppClock.setFixed(Instant.parse("2020-01-01T00:00:00Z"));
            assertEquals(Instant.parse("2020-01-01T00:00:00Z"), AppClock.now());

            AppClock.reset();

            Instant afterReset = AppClock.now();
            assertTrue(
                    afterReset.isAfter(Instant.parse("2025-01-01T00:00:00Z")),
                    "Clock should be back to real time, got: " + afterReset);
        }
    }

    @Test
    @DisplayName("clock() returns the underlying Clock instance")
    void clockReturnsUnderlyingClock() {
        assertNotNull(AppClock.clock());

        Instant fixed = Instant.parse("2025-06-01T00:00:00Z");
        AppClock.setFixed(fixed);
        Clock clock = AppClock.clock();
        assertEquals(fixed, clock.instant());
    }
}
