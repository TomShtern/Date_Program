package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.matching.RecommendationService.DailyPick;
import datingapp.core.model.Gender;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DailyServiceTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.Analytics analyticsStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private CandidateFinder candidateFinder;
    private RecommendationService service;
    private AppConfig config;
    private Clock fixedClock;
    private Instant todayStart;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        analyticsStorage = new TestStorages.Analytics();
        trustSafetyStorage = new TestStorages.TrustSafety();

        config = AppConfig.builder()
                .dailyLikeLimit(5)
                .dailyPassLimit(10)
                .userTimeZone(ZoneId.of("UTC"))
                .build();

        todayStart = LocalDate.of(2026, 2, 6).atStartOfDay(ZoneId.of("UTC")).toInstant();
        fixedClock = Clock.fixed(todayStart.plus(Duration.ofHours(12)), ZoneId.of("UTC")); // Noon UTC

        candidateFinder = new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage);
        service = createService(config, fixedClock);
    }

    @Nested
    @DisplayName("Daily Limits Tests")
    class LimitTests {
        @Test
        @DisplayName("canLike returns true when under limit")
        void canLike_underLimit() {
            UUID userId = UUID.randomUUID();
            assertTrue(service.canLike(userId));

            // Add 4 likes (limit is 5)
            for (int i = 0; i < 4; i++) {
                interactionStorage.save(new Like(
                        UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(i)));
            }
            assertTrue(service.canLike(userId));
        }

        @Test
        @DisplayName("canLike returns false when at limit")
        void canLike_atLimit() {
            UUID userId = UUID.randomUUID();
            // Add 5 likes
            for (int i = 0; i < 5; i++) {
                interactionStorage.save(new Like(
                        UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(i)));
            }
            assertFalse(service.canLike(userId));
        }

        @Test
        @DisplayName("canLike handles unlimited (-1)")
        void canLike_unlimited() {
            AppConfig unlimitedConfig = AppConfig.builder()
                    .dailyLikeLimit(-1)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            RecommendationService unlimitedService = createService(unlimitedConfig, fixedClock);

            UUID userId = UUID.randomUUID();
            for (int i = 0; i < 100; i++) {
                interactionStorage.save(new Like(
                        UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(i)));
            }
            assertTrue(unlimitedService.canLike(userId));
        }

        @Test
        @DisplayName("getStatus calculation of remaining likes")
        void getStatus_remainingLikes() {
            UUID userId = UUID.randomUUID();
            // 2 likes used out of 5
            interactionStorage.save(new Like(
                    UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(1)));
            interactionStorage.save(new Like(
                    UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(2)));

            RecommendationService.DailyStatus status = service.getStatus(userId);
            assertEquals(2, status.likesUsed());
            assertEquals(3, status.likesRemaining());
        }

        @Test
        @DisplayName("getStatus remaining likes for unlimited")
        void getStatus_remainingUnlimited() {
            AppConfig unlimitedConfig = AppConfig.builder()
                    .dailyLikeLimit(-1)
                    .userTimeZone(ZoneId.of("UTC"))
                    .build();
            RecommendationService unlimitedService = createService(unlimitedConfig, fixedClock);

            RecommendationService.DailyStatus status = unlimitedService.getStatus(UUID.randomUUID());
            assertEquals(-1, status.likesRemaining());
            assertTrue(status.hasUnlimitedLikes());
        }
    }

    @Nested
    @DisplayName("Daily Picks Tests")
    class PickTests {
        @Test
        @DisplayName("getDailyPick filters out already liked users")
        void getDailyPick_filtersInteracted() {
            User seeker = TestUserFactory.createActiveUser("Seeker");
            User candidate1 = TestUserFactory.createActiveUser("Candidate1");
            User candidate2 = TestUserFactory.createActiveUser("Candidate2");

            // Ensure mutual interest
            seeker.setGender(Gender.MALE);
            seeker.setInterestedIn(Set.of(Gender.FEMALE));
            candidate1.setGender(Gender.FEMALE);
            candidate1.setInterestedIn(Set.of(Gender.MALE));
            candidate2.setGender(Gender.FEMALE);
            candidate2.setInterestedIn(Set.of(Gender.MALE));

            userStorage.save(seeker);
            userStorage.save(candidate1);
            userStorage.save(candidate2);

            // Like candidate1
            interactionStorage.save(Like.create(seeker.getId(), candidate1.getId(), Like.Direction.LIKE));

            // candidate1 should be filtered out, candidate2 must be picked
            Optional<DailyPick> pick = service.getDailyPick(seeker);
            assertTrue(pick.isPresent(), "Should have picked candidate2");
            assertEquals(candidate2.getId(), pick.get().user().getId());
        }

        @Test
        @DisplayName("getDailyPick is deterministic for same user/date")
        void getDailyPick_deterministic() {
            User seeker = TestUserFactory.createActiveUser("Seeker");
            seeker.setGender(Gender.MALE);
            seeker.setInterestedIn(Set.of(Gender.FEMALE));

            for (int i = 0; i < 10; i++) {
                User candidate = TestUserFactory.createActiveUser("Candidate" + i);
                candidate.setGender(Gender.FEMALE);
                candidate.setInterestedIn(Set.of(Gender.MALE));
                userStorage.save(candidate);
            }
            userStorage.save(seeker);

            Optional<DailyPick> pick1 = service.getDailyPick(seeker);
            Optional<DailyPick> pick2 = service.getDailyPick(seeker);

            assertTrue(pick1.isPresent(), "Pick 1 should be present");
            assertTrue(pick2.isPresent(), "Pick 2 should be present");
            assertEquals(pick1.get().user().getId(), pick2.get().user().getId());
            assertEquals(pick1.get().reason(), pick2.get().reason());
        }

        @Test
        @DisplayName("hasViewedDailyPick returns correctly")
        void hasViewedDailyPick() {
            UUID userId = UUID.randomUUID();
            assertFalse(service.hasViewedDailyPick(userId));

            service.markDailyPickViewed(userId);
            assertTrue(service.hasViewedDailyPick(userId));
            assertTrue(analyticsStorage.isDailyPickViewed(userId, LocalDate.now(fixedClock)));
        }

        @Test
        @DisplayName("cleanupOldDailyPickViews removes old entries")
        void cleanupOldDailyPickViews() {
            LocalDate today = AppClock.today(ZoneId.of("UTC"));
            service.cleanupOldDailyPickViews(today);
            // Verify it doesn't crash and returns 0 for mock
            assertEquals(0, service.cleanupOldDailyPickViews(today));
        }
    }

    @Nested
    @DisplayName("Daily Limits Secondary Tests")
    class LimitSecondaryTests {
        @Test
        @DisplayName("canPass tests")
        void canPass() {
            UUID userId = UUID.randomUUID();
            assertTrue(service.canPass(userId));
        }

        @Test
        @DisplayName("DailyStatus edge cases")
        void dailyStatus_edgeCases() {
            RecommendationService.DailyStatus status = new RecommendationService.DailyStatus(
                    0, 5, 0, 10, AppClock.today(ZoneId.of("UTC")), AppClock.now());
            assertFalse(status.hasUnlimitedLikes());
            assertFalse(status.hasUnlimitedPasses());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RecommendationService.DailyStatus(
                            -1, 5, 0, 10, AppClock.today(ZoneId.of("UTC")), AppClock.now()));
        }
    }

    @Nested
    @DisplayName("Time Reset Tests")
    class ResetTests {
        @Test
        @DisplayName("getTimeUntilReset returns time to midnight")
        void getTimeUntilReset() {
            // Noon UTC to Midnight UTC should be 12 hours
            Duration timeUntilReset = service.getTimeUntilReset();
            assertEquals(Duration.ofHours(12), timeUntilReset);
        }

        @Test
        @DisplayName("formatDuration formats correctly")
        void formatDuration() {
            assertEquals("12h 00m", RecommendationService.formatDuration(Duration.ofHours(12)));
            assertEquals("30m", RecommendationService.formatDuration(Duration.ofMinutes(30)));
            assertEquals("45m", RecommendationService.formatDuration(Duration.ofMinutes(45)));
        }
    }

    private RecommendationService createService(AppConfig config, Clock clock) {
        var standoutStorage = new TestStorages.Standouts();
        var profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        return RecommendationService.builder()
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .clock(clock)
                .build();
    }
}
