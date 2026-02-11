package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.*;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.service.*;
import datingapp.core.service.DailyService.DailyPick;
import datingapp.core.storage.StatsStorage;
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
    private TestStorages.Likes likeStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private DailyPickViewStorageMock statsStorageMock;
    private CandidateFinder candidateFinder;
    private DailyService service;
    private AppConfig config;
    private Clock fixedClock;
    private Instant todayStart;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        likeStorage = new TestStorages.Likes();
        trustSafetyStorage = new TestStorages.TrustSafety();
        statsStorageMock = new DailyPickViewStorageMock();

        config = AppConfig.builder()
                .dailyLikeLimit(5)
                .dailyPassLimit(10)
                .userTimeZone(ZoneId.of("UTC"))
                .build();

        todayStart = LocalDate.of(2026, 2, 6).atStartOfDay(ZoneId.of("UTC")).toInstant();
        fixedClock = Clock.fixed(todayStart.plus(Duration.ofHours(12)), ZoneId.of("UTC")); // Noon UTC

        candidateFinder = new CandidateFinder(userStorage, likeStorage, trustSafetyStorage, config);
        service = new DailyService(
                userStorage, likeStorage, trustSafetyStorage, statsStorageMock, candidateFinder, config, fixedClock);
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
                likeStorage.save(new Like(
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
                likeStorage.save(new Like(
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
            DailyService unlimitedService = new DailyService(
                    userStorage,
                    likeStorage,
                    trustSafetyStorage,
                    statsStorageMock,
                    candidateFinder,
                    unlimitedConfig,
                    fixedClock);

            UUID userId = UUID.randomUUID();
            for (int i = 0; i < 100; i++) {
                likeStorage.save(new Like(
                        UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(i)));
            }
            assertTrue(unlimitedService.canLike(userId));
        }

        @Test
        @DisplayName("getStatus calculation of remaining likes")
        void getStatus_remainingLikes() {
            UUID userId = UUID.randomUUID();
            // 2 likes used out of 5
            likeStorage.save(new Like(
                    UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(1)));
            likeStorage.save(new Like(
                    UUID.randomUUID(), userId, UUID.randomUUID(), Like.Direction.LIKE, todayStart.plusSeconds(2)));

            DailyService.DailyStatus status = service.getStatus(userId);
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
            DailyService unlimitedService = new DailyService(
                    userStorage,
                    likeStorage,
                    trustSafetyStorage,
                    statsStorageMock,
                    candidateFinder,
                    unlimitedConfig,
                    fixedClock);

            DailyService.DailyStatus status = unlimitedService.getStatus(UUID.randomUUID());
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
            seeker.setGender(User.Gender.MALE);
            seeker.setInterestedIn(Set.of(User.Gender.FEMALE));
            candidate1.setGender(User.Gender.FEMALE);
            candidate1.setInterestedIn(Set.of(User.Gender.MALE));
            candidate2.setGender(User.Gender.FEMALE);
            candidate2.setInterestedIn(Set.of(User.Gender.MALE));

            userStorage.save(seeker);
            userStorage.save(candidate1);
            userStorage.save(candidate2);

            // Like candidate1
            likeStorage.save(Like.create(seeker.getId(), candidate1.getId(), Like.Direction.LIKE));

            // candidate1 should be filtered out, candidate2 must be picked
            Optional<DailyPick> pick = service.getDailyPick(seeker);
            assertTrue(pick.isPresent(), "Should have picked candidate2");
            assertEquals(candidate2.getId(), pick.get().user().getId());
        }

        @Test
        @DisplayName("getDailyPick is deterministic for same user/date")
        void getDailyPick_deterministic() {
            User seeker = TestUserFactory.createActiveUser("Seeker");
            seeker.setGender(User.Gender.MALE);
            seeker.setInterestedIn(Set.of(User.Gender.FEMALE));

            for (int i = 0; i < 10; i++) {
                User candidate = TestUserFactory.createActiveUser("Candidate" + i);
                candidate.setGender(User.Gender.FEMALE);
                candidate.setInterestedIn(Set.of(User.Gender.MALE));
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
            assertTrue(statsStorageMock.isDailyPickViewed(userId, LocalDate.now(fixedClock)));
        }

        @Test
        @DisplayName("cleanupOldDailyPickViews removes old entries")
        void cleanupOldDailyPickViews() {
            LocalDate today = AppClock.today(ZoneId.of("UTC"));
            service.cleanupOldDailyPickViews(today);
            // Verify it doesn't crash and returns 0 for mock
            assertEquals(0, service.cleanupOldDailyPickViews(today));
        }

        @Test
        @DisplayName("ensureDailyPickDependencies throws if missing")
        void ensureDailyPickDependencies_throws() {
            DailyService incompleteService = new DailyService(likeStorage, config);
            assertThrows(IllegalStateException.class, () -> incompleteService.getDailyPick(null));
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
            DailyService.DailyStatus status =
                    new DailyService.DailyStatus(0, 5, 0, 10, AppClock.today(ZoneId.of("UTC")), AppClock.now());
            assertFalse(status.hasUnlimitedLikes());
            assertFalse(status.hasUnlimitedPasses());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DailyService.DailyStatus(-1, 5, 0, 10, AppClock.today(ZoneId.of("UTC")), AppClock.now()));
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
            assertEquals("12h 00m", DailyService.formatDuration(Duration.ofHours(12)));
            assertEquals("30m", DailyService.formatDuration(Duration.ofMinutes(30)));
            assertEquals("45m", DailyService.formatDuration(Duration.ofMinutes(45)));
        }
    }

    // Minimal StatsStorage mock — only daily pick view methods are meaningful
    @SuppressWarnings("unused")
    private static class DailyPickViewStorageMock implements StatsStorage {
        private final Set<String> viewed = new java.util.HashSet<>();

        @Override
        public void markDailyPickAsViewed(UUID userId, LocalDate date) {
            viewed.add(userId + "_" + date);
        }

        @Override
        public boolean isDailyPickViewed(UUID userId, LocalDate date) {
            return viewed.contains(userId + "_" + date);
        }

        @Override
        public int deleteDailyPickViewsOlderThan(LocalDate date) {
            return 0;
        }

        // Remaining StatsStorage methods — not used by DailyService
        @Override
        public void saveUserStats(datingapp.core.model.Stats.UserStats stats) {
            /* not used */
        }

        @Override
        public java.util.Optional<datingapp.core.model.Stats.UserStats> getLatestUserStats(UUID userId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<datingapp.core.model.Stats.UserStats> getUserStatsHistory(UUID userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<datingapp.core.model.Stats.UserStats> getAllLatestUserStats() {
            return java.util.List.of();
        }

        @Override
        public int deleteUserStatsOlderThan(java.time.Instant cutoff) {
            return 0;
        }

        @Override
        public void savePlatformStats(datingapp.core.model.Stats.PlatformStats stats) {
            /* not used */
        }

        @Override
        public java.util.Optional<datingapp.core.model.Stats.PlatformStats> getLatestPlatformStats() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<datingapp.core.model.Stats.PlatformStats> getPlatformStatsHistory(int limit) {
            return java.util.List.of();
        }

        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {
            /* not used */
        }

        @Override
        public int getProfileViewCount(UUID userId) {
            return 0;
        }

        @Override
        public int getUniqueViewerCount(UUID userId) {
            return 0;
        }

        @Override
        public java.util.List<UUID> getRecentViewers(UUID userId, int limit) {
            return java.util.List.of();
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return false;
        }

        @Override
        public void saveUserAchievement(datingapp.core.model.Achievement.UserAchievement achievement) {
            /* not used */
        }

        @Override
        public java.util.List<datingapp.core.model.Achievement.UserAchievement> getUnlockedAchievements(UUID userId) {
            return java.util.List.of();
        }

        @Override
        public boolean hasAchievement(UUID userId, datingapp.core.model.Achievement achievement) {
            return false;
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return 0;
        }

        @Override
        public int deleteExpiredDailyPickViews(java.time.Instant cutoff) {
            return 0;
        }
    }
}
