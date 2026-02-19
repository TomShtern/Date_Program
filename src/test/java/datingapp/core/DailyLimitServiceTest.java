package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for RecommendationService daily limits. Uses in-memory mock
 * storage for isolated testing.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DailyLimitServiceTest {

    private TestStorages.Interactions interactionStorage;
    private AppConfig config;
    private RecommendationService service;
    private UUID userId;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        interactionStorage = new TestStorages.Interactions();
        config = AppConfig.builder()
                .dailyLikeLimit(3)
                .dailyPassLimit(-1) // unlimited
                .userTimeZone(ZoneId.of("UTC"))
                .build();
        service = createService(config);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
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
            Instant now = AppClock.now();
            for (int i = 0; i < 3; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                interactionStorage.save(like);
            }

            assertFalse(service.canLike(userId));
        }

        @Test
        @DisplayName("User with 1 remaining like can like")
        void canLike_oneRemaining_returnsTrue() {
            Instant now = AppClock.now();
            for (int i = 0; i < 2; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                interactionStorage.save(like);
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
            RecommendationService unlimitedService = createService(unlimitedConfig);

            // Add many likes
            Instant now = AppClock.now();
            for (int i = 0; i < 100; i++) {
                Like like = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now);
                interactionStorage.save(like);
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
            Instant now = AppClock.now();
            for (int i = 0; i < 100; i++) {
                Like pass = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now);
                interactionStorage.save(pass);
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
            RecommendationService limitedPassService = createService(limitedPassConfig);

            // Add 2 passes today (at limit)
            Instant now = AppClock.now();
            for (int i = 0; i < 2; i++) {
                Like pass = new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now);
                interactionStorage.save(pass);
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
            Instant now = AppClock.now();
            // 2 likes, 1 pass
            interactionStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            interactionStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            interactionStorage.save(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now));

            RecommendationService.DailyStatus status = service.getStatus(userId);

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
            RecommendationService unlimitedService = createService(unlimitedConfig);

            RecommendationService.DailyStatus status = unlimitedService.getStatus(userId);

            assertTrue(status.hasUnlimitedLikes());
            assertTrue(status.hasUnlimitedPasses());
        }

        @Test
        @DisplayName("Status returns correct date")
        void getStatus_returnsCorrectDate() {
            RecommendationService.DailyStatus status = service.getStatus(userId);
            assertEquals(AppClock.today(ZoneOffset.UTC), status.date());
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
                    "04:30:00",
                    RecommendationService.formatDuration(Duration.ofHours(4).plusMinutes(30)));
            assertEquals("00:05:00", RecommendationService.formatDuration(Duration.ofMinutes(5)));
            assertEquals("12:00:00", RecommendationService.formatDuration(Duration.ofHours(12)));
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
            RecommendationService zeroService = createService(zeroConfig);

            assertFalse(zeroService.canLike(userId));
        }

        @Test
        @DisplayName("Passes don't count against like limit")
        void passes_dontCountAgainstLikes() {
            Instant now = AppClock.now();
            // 3 passes
            for (int i = 0; i < 3; i++) {
                interactionStorage.save(
                        new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.PASS, now));
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
            RecommendationService limitedPassService = createService(limitedPassConfig);

            Instant now = AppClock.now();
            // 5 likes
            for (int i = 0; i < 5; i++) {
                interactionStorage.save(
                        new Like(UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, now));
            }

            // Should still be able to pass
            assertTrue(limitedPassService.canPass(userId));
        }
    }

    private RecommendationService createService(AppConfig config) {
        var userStorage = new TestStorages.Users();
        var analyticsStorage = new TestStorages.Analytics();
        var trustSafetyStorage = new TestStorages.TrustSafety();
        var standoutStorage = new TestStorages.Standouts();
        var candidateFinder = new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage);
        var profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        return RecommendationService.builder()
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .build();
    }
}
