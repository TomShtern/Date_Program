package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for AchievementService. Uses in-memory mock storage for isolated testing. */
@SuppressWarnings("unused") // IDE false positives for @Nested classes and @BeforeEach
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class AchievementServiceTest {

    private InMemoryUserAchievementStorage achievementStorage;
    private InMemoryMatchStorage matchStorage;
    private InMemoryLikeStorage likeStorage;
    private InMemoryUserStorage userStorage;
    private InMemoryReportStorage reportStorage;
    private ProfilePreviewService profilePreviewService;
    private AchievementService service;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        achievementStorage = new InMemoryUserAchievementStorage();
        matchStorage = new InMemoryMatchStorage();
        likeStorage = new InMemoryLikeStorage();
        userStorage = new InMemoryUserStorage();
        reportStorage = new InMemoryReportStorage();
        profilePreviewService = new ProfilePreviewService();

        service = new AchievementService(
                achievementStorage,
                matchStorage,
                likeStorage,
                userStorage,
                reportStorage,
                profilePreviewService,
                AppConfig.defaults());

        userId = UUID.randomUUID();
        user = createActiveUser(userId, "Test User");
        userStorage.save(user);
    }

    @Nested
    @DisplayName("Matching Milestone Tests")
    class MatchingMilestoneTests {

        @Test
        @DisplayName("First match unlocks FIRST_SPARK")
        void checkAndUnlock_firstMatch_unlocksFirstSpark() {
            // Add 1 match
            addMatches(userId, 1);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.FIRST_SPARK));
            assertTrue(achievementStorage.hasAchievement(userId, Achievement.FIRST_SPARK));
        }

        @Test
        @DisplayName("5 matches unlocks SOCIAL_BUTTERFLY")
        void checkAndUnlock_fiveMatches_unlocksSocialButterfly() {
            addMatches(userId, 5);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.SOCIAL_BUTTERFLY));
        }

        @Test
        @DisplayName("10 matches unlocks POPULAR")
        void checkAndUnlock_tenMatches_unlocksPopular() {
            addMatches(userId, 10);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.POPULAR));
        }

        @Test
        @DisplayName("Already unlocked achievements are not duplicated")
        void checkAndUnlock_alreadyUnlocked_noDuplicate() {
            addMatches(userId, 1);

            // First check - should unlock
            List<UserAchievement> firstCall = service.checkAndUnlock(userId);
            assertTrue(hasAchievement(firstCall, Achievement.FIRST_SPARK));

            // Second check - should NOT unlock again
            List<UserAchievement> secondCall = service.checkAndUnlock(userId);
            assertFalse(hasAchievement(secondCall, Achievement.FIRST_SPARK));

            // Storage should only have 1 entry
            assertEquals(1, achievementStorage.countForAchievement(userId, Achievement.FIRST_SPARK));
        }
    }

    @Nested
    @DisplayName("Profile Excellence Tests")
    class ProfileExcellenceTests {

        @Test
        @DisplayName("Complete profile unlocks COMPLETE_PACKAGE")
        void checkAndUnlock_completeProfile_unlocksCompletePackage() {
            // Set up a complete profile
            user.setBio("Complete bio with more than enough text to be valid.");
            user.setBirthDate(LocalDate.now().minusYears(25));
            user.setGender(User.Gender.FEMALE);
            user.setInterestedIn(Set.of(User.Gender.MALE));
            user.setLocation(32.0, 34.0);
            user.addPhotoUrl("http://example.com/photo.jpg");
            user.setHeightCm(170);
            user.setSmoking(Lifestyle.Smoking.NEVER);
            user.setDrinking(Lifestyle.Drinking.SOCIALLY);
            user.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
            user.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
            user.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
            userStorage.save(user);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.COMPLETE_PACKAGE));
        }

        @Test
        @DisplayName("Bio over 100 chars unlocks STORYTELLER")
        void checkAndUnlock_longBio_unlocksStoryteller() {
            user.setBio("This is a very detailed bio that goes on and on about the person's interests, "
                    + "hobbies, and what they are looking for in a match. It definitely exceeds 100 characters.");
            userStorage.save(user);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.STORYTELLER));
        }

        @Test
        @DisplayName("All lifestyle fields unlocks LIFESTYLE_GURU")
        void checkAndUnlock_allLifestyleFields_unlocksLifestyleGuru() {
            user.setHeightCm(175);
            user.setSmoking(Lifestyle.Smoking.NEVER);
            user.setDrinking(Lifestyle.Drinking.SOCIALLY);
            user.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
            user.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
            userStorage.save(user);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.LIFESTYLE_GURU));
        }
    }

    @Nested
    @DisplayName("Behavior Achievement Tests")
    class BehaviorAchievementTests {

        @Test
        @DisplayName("Low like ratio (< 20%) with 50+ swipes unlocks SELECTIVE")
        void checkAndUnlock_lowLikeRatio_unlocksSelective() {
            // 10 likes, 50 passes = 16.7% like ratio
            addSwipes(userId, 10, 50);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.SELECTIVE));
        }

        @Test
        @DisplayName("High like ratio (> 60%) with 50+ swipes unlocks OPEN_MINDED")
        void checkAndUnlock_highLikeRatio_unlocksOpenMinded() {
            // 45 likes, 15 passes = 75% like ratio
            addSwipes(userId, 45, 15);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.OPEN_MINDED));
        }

        @Test
        @DisplayName("Behavior achievements require 50+ swipes")
        void checkAndUnlock_lowSwipeCount_noBehaviorAchievements() {
            // 49 total swipes - should not unlock SELECTIVE even if ratio is low
            addSwipes(userId, 5, 44);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertFalse(hasAchievement(unlocked, Achievement.SELECTIVE));
            assertFalse(hasAchievement(unlocked, Achievement.OPEN_MINDED));
        }
    }

    @Nested
    @DisplayName("Safety Achievement Tests")
    class SafetyAchievementTests {

        @Test
        @DisplayName("Reporting a user unlocks GUARDIAN")
        void checkAndUnlock_reportSubmitted_unlocksGuardian() {
            reportStorage.addReportBy(userId);

            List<UserAchievement> unlocked = service.checkAndUnlock(userId);

            assertTrue(hasAchievement(unlocked, Achievement.GUARDIAN));
        }
    }

    @Nested
    @DisplayName("Progress Tracking Tests")
    class ProgressTrackingTests {

        @Test
        @DisplayName("getProgress returns correct percentages")
        void getProgress_returnsCorrectPercentages() {
            // 3 matches out of 5 for SOCIAL_BUTTERFLY
            addMatches(userId, 3);

            List<AchievementService.AchievementProgress> progress = service.getProgress(userId);

            AchievementService.AchievementProgress socialButterfly = progress.stream()
                    .filter(p -> p.achievement() == Achievement.SOCIAL_BUTTERFLY)
                    .findFirst()
                    .orElseThrow();

            assertEquals(3, socialButterfly.current());
            assertEquals(5, socialButterfly.target());
            assertEquals(60, socialButterfly.getProgressPercent());
            assertFalse(socialButterfly.unlocked());
        }

        @Test
        @DisplayName("Unlocked achievements show 100% progress")
        void getProgress_unlockedShow100Percent() {
            addMatches(userId, 1);
            service.checkAndUnlock(userId);

            List<AchievementService.AchievementProgress> progress = service.getProgress(userId);

            AchievementService.AchievementProgress firstSpark = progress.stream()
                    .filter(p -> p.achievement() == Achievement.FIRST_SPARK)
                    .findFirst()
                    .orElseThrow();

            assertTrue(firstSpark.unlocked());
            assertEquals(100, firstSpark.getProgressPercent());
        }
    }

    // === Helper Methods ===

    private User createActiveUser(UUID id, String name) {
        User u = new User(id, name);
        u.setBirthDate(LocalDate.now().minusYears(25));
        u.setGender(User.Gender.MALE);
        u.setInterestedIn(Set.of(User.Gender.FEMALE));
        u.setMaxDistanceKm(50);
        return u;
    }

    private void addMatches(UUID userId, int count) {
        for (int i = 0; i < count; i++) {
            UUID matchId = UUID.randomUUID();
            Match match = Match.create(userId, matchId);
            matchStorage.save(match);
        }
    }

    private void addSwipes(UUID userId, int likes, int passes) {
        for (int i = 0; i < likes; i++) {
            likeStorage.addLike(userId, Like.Direction.LIKE);
        }
        for (int i = 0; i < passes; i++) {
            likeStorage.addLike(userId, Like.Direction.PASS);
        }
    }

    private boolean hasAchievement(List<UserAchievement> list, Achievement achievement) {
        return list.stream().anyMatch(ua -> ua.achievement() == achievement);
    }

    // === In-Memory Mock Storage Classes ===

    private static class InMemoryUserAchievementStorage implements UserAchievementStorage {
        private final List<UserAchievement> achievements = new ArrayList<>();

        @Override
        public void save(UserAchievement achievement) {
            achievements.add(achievement);
        }

        @Override
        public List<UserAchievement> getUnlocked(UUID userId) {
            return achievements.stream().filter(a -> a.userId().equals(userId)).toList();
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.stream().anyMatch(a -> a.userId().equals(userId) && a.achievement() == achievement);
        }

        @Override
        public int countUnlocked(UUID userId) {
            return (int)
                    achievements.stream().filter(a -> a.userId().equals(userId)).count();
        }

        int countForAchievement(UUID userId, Achievement achievement) {
            return (int) achievements.stream()
                    .filter(a -> a.userId().equals(userId) && a.achievement() == achievement)
                    .count();
        }
    }

    private static class InMemoryMatchStorage implements MatchStorage {
        private final List<Match> matches = new ArrayList<>();

        @Override
        public void save(Match match) {
            matches.add(match);
        }

        @Override
        public void update(Match match) {
            // Not needed for achievement tests
        }

        @Override
        public Optional<Match> get(String matchId) {
            return matches.stream().filter(m -> m.getId().equals(matchId)).findFirst();
        }

        @Override
        public boolean exists(String matchId) {
            return matches.stream().anyMatch(m -> m.getId().equals(matchId));
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.removeIf(m -> m.getId().equals(matchId));
        }
    }

    private static class InMemoryLikeStorage implements LikeStorage {
        private final Map<UUID, List<Like>> likes = new HashMap<>();

        void addLike(UUID userId, Like.Direction direction) {
            likes.computeIfAbsent(userId, k -> new ArrayList<>())
                    .add(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), direction, Instant.now()));
        }

        @Override
        public void save(Like like) {
            likes.computeIfAbsent(like.whoLikes(), k -> new ArrayList<>()).add(like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return false;
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return false;
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
            for (List<Like> likeList : likes.values()) {
                for (Like like : likeList) {
                    if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                        result.put(like.whoLikes(), like.createdAt());
                    }
                }
            }
            return result;
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
            return new ArrayList<>(getLikeTimesForUsersWhoLiked(userId).entrySet());
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.getOrDefault(userId, List.of()).stream()
                    .filter(l -> l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return 0;
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return 0;
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.empty();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {
            // Not needed for achievement tests
        }
    }

    private static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();

        @Override
        public void save(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }
    }

    private static class InMemoryReportStorage implements ReportStorage {
        private final Map<UUID, Integer> reportsByUser = new HashMap<>();

        void addReportBy(UUID userId) {
            reportsByUser.merge(userId, 1, Integer::sum);
        }

        @Override
        public void save(Report report) {
            // Not needed for achievement tests
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return 0;
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return false;
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return List.of();
        }

        @Override
        public int countReportsBy(UUID userId) {
            return reportsByUser.getOrDefault(userId, 0);
        }
    }
}
