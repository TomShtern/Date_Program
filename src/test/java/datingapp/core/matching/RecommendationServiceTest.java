package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.DailyLimitService.DailyStatus;
import datingapp.core.matching.DailyPickService.DailyPick;
import datingapp.core.model.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("RecommendationService")
class RecommendationServiceTest {

    private static final UUID SEEKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PICKED_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STANDOUT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDate TODAY = LocalDate.of(2026, 2, 1);
    private static final Instant RESET_AT = Instant.parse("2026-02-02T00:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-02-01T12:00:00Z");

    private FakeDailyLimitService dailyLimitService;
    private FakeDailyPickService dailyPickService;
    private FakeStandoutService standoutService;
    private RecommendationService recommendationService;
    private User seeker;
    private User pickedUser;
    private User standoutUser;

    @BeforeEach
    void setUp() {
        dailyLimitService = new FakeDailyLimitService();
        dailyPickService = new FakeDailyPickService();
        standoutService = new FakeStandoutService();
        recommendationService = new RecommendationService(dailyLimitService, dailyPickService, standoutService);

        seeker = new User(SEEKER_ID, "Seeker");
        pickedUser = new User(PICKED_ID, "Picked");
        standoutUser = new User(STANDOUT_ID, "Standout");
    }

    @Test
    @DisplayName("delegates limit checks to the daily limit service")
    void delegatesLimitChecks() {
        dailyLimitService.canLikeReturn = true;
        dailyLimitService.canSuperLikeReturn = true;
        dailyLimitService.canPassReturn = false;

        assertTrue(recommendationService.canLike(SEEKER_ID));
        assertTrue(recommendationService.canSuperLike(SEEKER_ID));
        assertFalse(recommendationService.canPass(SEEKER_ID));

        assertEquals(1, dailyLimitService.canLikeCalls);
        assertEquals(1, dailyLimitService.canSuperLikeCalls);
        assertEquals(1, dailyLimitService.canPassCalls);
        assertEquals(SEEKER_ID, dailyLimitService.lastCanLikeUserId);
        assertEquals(SEEKER_ID, dailyLimitService.lastCanSuperLikeUserId);
        assertEquals(SEEKER_ID, dailyLimitService.lastCanPassUserId);
    }

    @Test
    @DisplayName("wraps the daily limit status without changing values")
    void wrapsDailyLimitStatus() {
        dailyLimitService.statusReturn = new DailyStatus(5, 10, 0, 999, 2, 8, TODAY, RESET_AT);
        dailyLimitService.timeUntilResetReturn = Duration.ofHours(6);

        RecommendationService.DailyStatus status = recommendationService.getStatus(SEEKER_ID);

        assertEquals(5, status.likesUsed());
        assertEquals(10, status.likesRemaining());
        assertEquals(0, status.superLikesUsed());
        assertEquals(999, status.superLikesRemaining());
        assertEquals(2, status.passesUsed());
        assertEquals(8, status.passesRemaining());
        assertEquals(TODAY, status.date());
        assertEquals(RESET_AT, status.resetsAt());
        assertEquals(Duration.ofHours(6), recommendationService.getTimeUntilReset());
        assertEquals(1, dailyLimitService.statusCalls);
        assertEquals(1, dailyLimitService.timeUntilResetCalls);
    }

    @Test
    @DisplayName("delegates daily pick queries and maintenance calls")
    void delegatesDailyPickMethods() {
        DailyPick expectedPick = new DailyPick(pickedUser, TODAY, "Great match", false);
        dailyPickService.dailyPickReturn = Optional.of(expectedPick);
        dailyPickService.hasViewedReturn = true;
        dailyPickService.cleanupReturn = 3;

        Optional<RecommendationService.DailyPick> result = recommendationService.getDailyPick(seeker);

        assertTrue(result.isPresent());
        assertEquals(pickedUser, result.orElseThrow().user());
        assertEquals(TODAY, result.orElseThrow().date());
        assertEquals("Great match", result.orElseThrow().reason());
        assertFalse(result.orElseThrow().alreadySeen());
        assertTrue(recommendationService.hasViewedDailyPick(SEEKER_ID));

        recommendationService.markDailyPickViewed(SEEKER_ID);
        assertEquals(3, recommendationService.cleanupOldDailyPickViews(TODAY.minusDays(7)));

        assertEquals(seeker, dailyPickService.lastDailyPickSeeker);
        assertEquals(SEEKER_ID, dailyPickService.lastViewedUserId);
        assertEquals(TODAY.minusDays(7), dailyPickService.lastCleanupBefore);
        assertEquals(1, dailyPickService.getDailyPickCalls);
        assertEquals(1, dailyPickService.hasViewedCalls);
        assertEquals(1, dailyPickService.markViewedCalls);
        assertEquals(1, dailyPickService.cleanupCalls);
    }

    @Test
    @DisplayName("delegates standout queries, interaction tracking, and user resolution")
    void delegatesStandoutMethods() {
        Standout standout = new Standout(
                STANDOUT_ID, SEEKER_ID, standoutUser.getId(), TODAY, 1, 98, "Great match", CREATED_AT, null);
        StandoutService.Result standoutResult = StandoutService.Result.of(List.of(standout), 12, true);
        standoutService.resultReturn = standoutResult;
        standoutService.resolveReturn = Map.of(standoutUser.getId(), standoutUser);

        RecommendationService.Result result = recommendationService.getStandouts(seeker);

        assertFalse(result.isEmpty());
        assertEquals(1, result.count());
        assertEquals(12, result.totalCandidates());
        assertTrue(result.fromCache());
        assertNull(result.message());

        assertEquals(Map.of(standoutUser.getId(), standoutUser), recommendationService.resolveUsers(List.of(standout)));

        recommendationService.markInteracted(SEEKER_ID, standoutUser.getId());

        assertEquals(seeker, standoutService.lastStandoutsSeeker);
        assertEquals(List.of(standout), standoutService.lastResolvedStandouts);
        assertEquals(SEEKER_ID, standoutService.lastMarkedSeekerId);
        assertEquals(standoutUser.getId(), standoutService.lastMarkedStandoutUserId);
        assertEquals(1, standoutService.getStandoutsCalls);
        assertEquals(1, standoutService.resolveUsersCalls);
        assertEquals(1, standoutService.markInteractedCalls);
    }

    @ParameterizedTest(name = "formatDuration({0}) -> {1}")
    @MethodSource("formatDurationCases")
    void formatDuration_formatsExpectedValues(Duration input, String expected) {
        assertEquals(expected, RecommendationService.formatDuration(input));
    }

    static Stream<Arguments> formatDurationCases() {
        return Stream.of(
                Arguments.of(null, "00:00:00"),
                Arguments.of(Duration.ZERO, "00:00:00"),
                Arguments.of(Duration.ofMinutes(15).plusSeconds(30), "00:15:30"),
                Arguments.of(Duration.ofHours(2).plusMinutes(30).plusSeconds(45), "02:30:45"),
                Arguments.of(Duration.ofHours(1).plusSeconds(5), "01:00:05"),
                Arguments.of(Duration.ofHours(-1).plusMinutes(-30), "-01:30:00"),
                Arguments.of(Duration.ofHours(24).plusMinutes(1).plusSeconds(1), "24:01:01"));
    }

    private static final class FakeDailyLimitService implements DailyLimitService {
        private boolean canLikeReturn;
        private boolean canSuperLikeReturn;
        private boolean canPassReturn;
        private DailyStatus statusReturn = new DailyStatus(0, 0, 0, 0, 0, 0, TODAY, RESET_AT);
        private Duration timeUntilResetReturn = Duration.ZERO;
        private UUID lastCanLikeUserId;
        private UUID lastCanSuperLikeUserId;
        private UUID lastCanPassUserId;
        private int canLikeCalls;
        private int canSuperLikeCalls;
        private int canPassCalls;
        private int statusCalls;
        private int timeUntilResetCalls;

        @Override
        public boolean canLike(UUID userId) {
            lastCanLikeUserId = userId;
            canLikeCalls++;
            return canLikeReturn;
        }

        @Override
        public boolean canSuperLike(UUID userId) {
            lastCanSuperLikeUserId = userId;
            canSuperLikeCalls++;
            return canSuperLikeReturn;
        }

        @Override
        public boolean canPass(UUID userId) {
            lastCanPassUserId = userId;
            canPassCalls++;
            return canPassReturn;
        }

        @Override
        public DailyStatus getStatus(UUID userId) {
            statusCalls++;
            return statusReturn;
        }

        @Override
        public Duration getTimeUntilReset() {
            timeUntilResetCalls++;
            return timeUntilResetReturn;
        }

        @Override
        public String formatDuration(Duration duration) {
            return RecommendationService.formatDuration(duration);
        }
    }

    private static final class FakeDailyPickService implements DailyPickService {
        private Optional<DailyPick> dailyPickReturn = Optional.empty();
        private boolean hasViewedReturn;
        private int cleanupReturn;
        private User lastDailyPickSeeker;
        private UUID lastViewedUserId;
        private LocalDate lastCleanupBefore;
        private int getDailyPickCalls;
        private int hasViewedCalls;
        private int markViewedCalls;
        private int cleanupCalls;

        @Override
        public Optional<DailyPick> getDailyPick(User seeker) {
            lastDailyPickSeeker = seeker;
            getDailyPickCalls++;
            return dailyPickReturn;
        }

        @Override
        public boolean hasViewedDailyPick(UUID userId) {
            lastViewedUserId = userId;
            hasViewedCalls++;
            return hasViewedReturn;
        }

        @Override
        public void markDailyPickViewed(UUID userId) {
            lastViewedUserId = userId;
            markViewedCalls++;
        }

        @Override
        public int cleanupOldDailyPickViews(LocalDate before) {
            lastCleanupBefore = before;
            cleanupCalls++;
            return cleanupReturn;
        }
    }

    private static final class FakeStandoutService implements StandoutService {
        private StandoutService.Result resultReturn = StandoutService.Result.empty(null);
        private Map<UUID, User> resolveReturn = Map.of();
        private User lastStandoutsSeeker;
        private List<Standout> lastResolvedStandouts = List.of();
        private UUID lastMarkedSeekerId;
        private UUID lastMarkedStandoutUserId;
        private int getStandoutsCalls;
        private int resolveUsersCalls;
        private int markInteractedCalls;

        @Override
        public Result getStandouts(User seeker) {
            lastStandoutsSeeker = seeker;
            getStandoutsCalls++;
            return resultReturn;
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId) {
            lastMarkedSeekerId = seekerId;
            lastMarkedStandoutUserId = standoutUserId;
            markInteractedCalls++;
        }

        @Override
        public Map<UUID, User> resolveUsers(List<Standout> standouts) {
            lastResolvedStandouts = List.copyOf(standouts);
            resolveUsersCalls++;
            return resolveReturn;
        }
    }
}
