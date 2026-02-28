package datingapp.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimePolicyTest {

    private TimePolicy policy;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(Instant.parse("2026-06-15T10:30:00Z"));
        policy = new DefaultTimePolicy(ZoneId.of("Asia/Jerusalem"));
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    void nowDelegatesToAppClock() {
        assertEquals(Instant.parse("2026-06-15T10:30:00Z"), policy.now());
    }

    @Test
    void todayUsesUserZone() {
        // 10:30 UTC = 13:30 Asia/Jerusalem (+3) → same day
        assertEquals(LocalDate.of(2026, 6, 15), policy.today());
    }

    @Test
    void todayHandlesDayBoundary() {
        // 22:00 UTC = 01:00+3 next day in Asia/Jerusalem
        TestClock.setFixed(Instant.parse("2026-06-14T22:00:00Z"));
        assertEquals(LocalDate.of(2026, 6, 15), policy.today());
    }

    @Test
    void userZoneReturnsConfiguredZone() {
        assertEquals(ZoneId.of("Asia/Jerusalem"), policy.userZone());
    }

    @Test
    void withUserZoneAppliesZone() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter zoned = policy.withUserZone(formatter);
        String formatted = zoned.format(Instant.parse("2026-06-15T10:30:00Z"));
        assertEquals("13:30", formatted);
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new DefaultTimePolicy(null));
    }
}
