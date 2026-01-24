package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.UserInteractions.Like;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for DailyLimitService. Uses in-memory mock storage for isolated testing. */
@SuppressWarnings("unused") // IDE false positives for @Nested classes and @BeforeEach
class DailyLimitServiceTest {

    private InMemoryLikeStorage likeStorage;
    private AppConfig config;
    private DailyLimitService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        likeStorage = new InMemoryLikeStorage();
        config = AppConfig.builder()
                .dailyLikeLimit(3)
                .dailyPassLimit(-1) // unlimited
                .userTimeZone(ZoneId.of("UTC"))
                .build();
        service = new DailyLimitService(likeStorage, config);
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("canLike tests")
    class CanLikeTests {

        @Test
        @DisplayName("User under limit can like")
        void canLike_underLimit_returnsTrue() {
            // No likes yet
            assertTrue(service.canLike(userId));
        }

        @Test
        @DisplayName("User at limit cannot like")
        void canLike_atLimit_returnsFalse() {
            // Add 3 likes today (at limit)
            Instant now = Instant.now();
            for (int i = 0; i < 3; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                likeStorage.save(like);
            }

            assertFalse(service.canLike(userId));
        }

        @Test
        @DisplayName("User with 1 remaining like can like")
        void canLike_oneRemaining_returnsTrue() {
            Instant now = Instant.now();
            for (int i = 0; i < 2; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                likeStorage.save(like);
            }

            assertTrue(service.canLike(userId));
        }

        @Test
        @DisplayName("Unlimited likes always returns true")
        void canLike_unlimited_alwaysTrue() {
            AppConfig unlimitedConfig = AppConfig.builder()
                    .dailyLikeLimit(-1)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            DailyLimitService unlimitedService = new DailyLimitService(likeStorage, unlimitedConfig);

            // Add many likes
            Instant now = Instant.now();
            for (int i = 0; i < 100; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                likeStorage.save(like);
            }

            assertTrue(unlimitedService.canLike(userId));
        }
    }

    @Nested
    @DisplayName("canPass tests")
    class CanPassTests {

        @Test
        @DisplayName("User with unlimited passes can always pass")
        void canPass_unlimited_alwaysTrue() {
            // Default config has unlimited passes
            assertTrue(service.canPass(userId));

            // Even with many passes recorded
            Instant now = Instant.now();
            for (int i = 0; i < 100; i++) {
                Like pass = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now);
                likeStorage.save(pass);
            }

            assertTrue(service.canPass(userId));
        }

        @Test
        @DisplayName("User at pass limit cannot pass")
        void canPass_atLimit_returnsFalse() {
            AppConfig limitedPassConfig = AppConfig.builder()
                    .dailyLikeLimit(100)
                    .dailyPassLimit(2)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            DailyLimitService limitedPassService = new DailyLimitService(likeStorage, limitedPassConfig);

            // Add 2 passes today (at limit)
            Instant now = Instant.now();
            for (int i = 0; i < 2; i++) {
                Like pass = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now);
                likeStorage.save(pass);
            }

            assertFalse(limitedPassService.canPass(userId));
        }
    }

    @Nested
    @DisplayName("getStatus tests")
    class GetStatusTests {

        @Test
        @DisplayName("Status shows correct counts")
        void getStatus_returnsCorrectCounts() {
            Instant now = Instant.now();
            // 2 likes, 1 pass
            likeStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            likeStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            likeStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now));

            DailyLimitService.DailyStatus status = service.getStatus(userId);

            assertEquals(2, status.likesUsed());
            assertEquals(1, status.likesRemaining()); // 3 - 2 = 1
            assertEquals(1, status.passesUsed());
        }

        @Test
        @DisplayName("Status shows unlimited when configured")
        void getStatus_showsUnlimited() {
            AppConfig unlimitedConfig = AppConfig.builder()
                    .dailyLikeLimit(-1)
                    .dailyPassLimit(-1)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            DailyLimitService unlimitedService = new DailyLimitService(likeStorage, unlimitedConfig);

            DailyLimitService.DailyStatus status = unlimitedService.getStatus(userId);

            assertTrue(status.hasUnlimitedLikes());
            assertTrue(status.hasUnlimitedPasses());
        }

        @Test
        @DisplayName("Status returns correct date")
        void getStatus_returnsCorrectDate() {
            DailyLimitService.DailyStatus status = service.getStatus(userId);
            assertEquals(LocalDate.now(ZoneOffset.UTC), status.date());
        }
    }

    @Nested
    @DisplayName("Time reset tests")
    class TimeResetTests {

        @Test
        @DisplayName("Reset time is in the future")
        void getTimeUntilReset_isPositive() {
            Duration timeUntilReset = service.getTimeUntilReset();
            assertTrue(timeUntilReset.toSeconds() > 0);
        }

        @Test
        @DisplayName("Reset time is less than 24 hours")
        void getTimeUntilReset_lessThan24Hours() {
            Duration timeUntilReset = service.getTimeUntilReset();
            assertTrue(timeUntilReset.toHours() < 24);
        }

        @Test
        @DisplayName("formatDuration works correctly")
        void formatDuration_formatsCorrectly() {
            assertEquals(
                    "4h 30m",
                    DailyLimitService.formatDuration(Duration.ofHours(4).plusMinutes(30)));
            assertEquals("5m", DailyLimitService.formatDuration(Duration.ofMinutes(5)));
            assertEquals("12h 00m", DailyLimitService.formatDuration(Duration.ofHours(12)));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Zero limit blocks all likes")
        void zeroLimit_blocksAllLikes() {
            AppConfig zeroConfig = AppConfig.builder()
                    .dailyLikeLimit(0)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            DailyLimitService zeroService = new DailyLimitService(likeStorage, zeroConfig);

            assertFalse(zeroService.canLike(userId));
        }

        @Test
        @DisplayName("Passes don't count against like limit")
        void passes_dontCountAgainstLikes() {
            Instant now = Instant.now();
            // 3 passes
            for (int i = 0; i < 3; i++) {
                likeStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now));
            }

            // Should still be able to like
            assertTrue(service.canLike(userId));
        }

        @Test
        @DisplayName("Likes don't count against pass limit")
        void likes_dontCountAgainstPasses() {
            AppConfig limitedPassConfig = AppConfig.builder()
                    .dailyLikeLimit(100)
                    .dailyPassLimit(2)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            DailyLimitService limitedPassService = new DailyLimitService(likeStorage, limitedPassConfig);

            Instant now = Instant.now();
            // 5 likes
            for (int i = 0; i < 5; i++) {
                likeStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            }

            // Should still be able to pass
            assertTrue(limitedPassService.canPass(userId));
        }
    }

    // === In-Memory Mock LikeStorage ===

    private static class InMemoryLikeStorage implements LikeStorage {
        private final Map<String, Like> likes = new HashMap<>();

        @Override
        public void save(Like like) {
            likes.put(key(like.whoLikes(), like.whoGotLiked()), like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return likes.containsKey(key(from, to));
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            Like likeA = likes.get(key(a, b));
            Like likeB = likes.get(key(b, a));
            return likeA != null
                    && likeB != null
                    && likeA.direction() == Like.Direction.LIKE
                    && likeB.direction() == Like.Direction.LIKE;
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return Set.of();
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return Set.of();
        }

        @Override
        public java.util.Map<UUID, java.time.Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            java.util.Map<UUID, java.time.Instant> result = new java.util.HashMap<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoGotLiked().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return 0;
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.ofNullable(likes.get(key(fromUserId, toUserId)));
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && !l.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.PASS
                            && !l.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.values().removeIf(like -> like.id().equals(likeId));
        }

        private String key(UUID from, UUID to) {
            return from + "->" + to;
        }
    }
}
