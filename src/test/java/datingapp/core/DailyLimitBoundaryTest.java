package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.testutil.TestStorages;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DailyLimitBoundaryTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant BEFORE_MIDNIGHT = Instant.parse("2026-03-25T23:59:59Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-03-26T00:00:00Z");
    private static final Instant MIDDAY_UTC = Instant.parse("2026-03-25T12:00:00Z");
    private static final Instant TOKYO_LATE_EVENING = Instant.parse("2026-03-25T23:30:00Z");
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    void likeCountResetsAtMidnight() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        saveLike(interactions, BEFORE_MIDNIGHT);

        DailyLimitService beforeMidnight = createService(interactions, BEFORE_MIDNIGHT, UTC);
        assertFalse(beforeMidnight.canLike(USER_ID));
        assertEquals(1, beforeMidnight.getStatus(USER_ID).likesUsed());

        DailyLimitService afterMidnight = createService(interactions, MIDNIGHT, UTC);
        DailyLimitService.DailyStatus afterMidnightStatus = afterMidnight.getStatus(USER_ID);

        assertTrue(afterMidnight.canLike(USER_ID));
        assertEquals(0, afterMidnightStatus.likesUsed());
        assertEquals(LocalDate.of(2026, 3, 26), afterMidnightStatus.date());
    }

    @Test
    void resetDateIsInUserTimezone() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        saveLike(interactions, TOKYO_LATE_EVENING);

        DailyLimitService service = createService(interactions, TOKYO_LATE_EVENING, TOKYO);
        DailyLimitService.DailyStatus status = service.getStatus(USER_ID);

        assertEquals(1, status.likesUsed());
        assertEquals(LocalDate.of(2026, 3, 26), status.date());
        assertEquals(LocalDate.of(2026, 3, 27).atStartOfDay(TOKYO).toInstant(), status.resetsAt());
    }

    @Test
    void differentTimezonesResetAtDifferentAbsoluteTimes() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        saveLike(interactions, MIDDAY_UTC);

        DailyLimitService utcService = createService(interactions, MIDDAY_UTC, UTC);
        DailyLimitService tokyoService = createService(interactions, MIDDAY_UTC, TOKYO);

        DailyLimitService.DailyStatus utcStatus = utcService.getStatus(USER_ID);
        DailyLimitService.DailyStatus tokyoStatus = tokyoService.getStatus(USER_ID);

        assertEquals(1, utcStatus.likesUsed());
        assertEquals(1, tokyoStatus.likesUsed());
        assertEquals(LocalDate.of(2026, 3, 25), utcStatus.date());
        assertEquals(LocalDate.of(2026, 3, 25), tokyoStatus.date());
        assertEquals(Instant.parse("2026-03-26T00:00:00Z"), utcStatus.resetsAt());
        assertEquals(Instant.parse("2026-03-25T15:00:00Z"), tokyoStatus.resetsAt());
        assertNotEquals(utcStatus.resetsAt(), tokyoStatus.resetsAt());
    }

    @Test
    void configuredTimezoneOverridesClockZoneForDailyBoundaries() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        saveLike(interactions, Instant.parse("2026-03-25T14:30:00Z"));

        DefaultDailyLimitService service =
                createService(interactions, Instant.parse("2026-03-25T15:30:00Z"), UTC, TOKYO);
        DailyLimitService.DailyStatus status = service.getStatus(USER_ID);

        assertEquals(0, status.likesUsed());
        assertEquals(LocalDate.of(2026, 3, 26), status.date());
        assertEquals(Instant.parse("2026-03-26T15:00:00Z"), status.resetsAt());
    }

    private static DefaultDailyLimitService createService(
            TestStorages.Interactions interactions, Instant now, ZoneId zone) {
        return createService(interactions, now, zone, zone);
    }

    private static DefaultDailyLimitService createService(
            TestStorages.Interactions interactions, Instant now, ZoneId clockZone, ZoneId configuredZone) {
        AppConfig config = AppConfig.builder()
                .dailyLikeLimit(1)
                .userTimeZone(configuredZone)
                .build();
        Clock clock = Clock.fixed(now, clockZone);
        return new DefaultDailyLimitService(interactions, config, clock);
    }

    private static void saveLike(TestStorages.Interactions interactions, Instant createdAt) {
        interactions.save(new Like(UUID.randomUUID(), USER_ID, OTHER_USER_ID, Like.Direction.LIKE, createdAt));
    }
}
