package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.User.ProfileNote;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.*;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Unit tests for AchievementService. Uses in-memory mock storage for isolated testing. */
@SuppressWarnings("unused") // IDE false positives for @Nested classes and @BeforeEach
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class AchievementServiceTest {

    private InMemoryStatsStorage achievementStorage;
    private InMemoryMatchStorage matchStorage;
    private InMemoryLikeStorage likeStorage;
    private InMemoryUserStorage userStorage;
    private InMemoryReportStorage reportStorage;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");
    private ProfileCompletionService profileCompletionService;
    private AchievementService service;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        achievementStorage = new InMemoryStatsStorage();
        matchStorage = new InMemoryMatchStorage();
        likeStorage = new InMemoryLikeStorage();
        userStorage = new InMemoryUserStorage();
        reportStorage = new InMemoryReportStorage();
        profileCompletionService = new ProfileCompletionService(AppConfig.defaults());

        service = new AchievementService(
                achievementStorage,
                matchStorage,
                likeStorage,
                userStorage,
                reportStorage,
                profileCompletionService,
                AppConfig.defaults());

        userId = UUID.randomUUID();
        user = createActiveUser(userId, "Test User");
        userStorage.save(user);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Matching Milestone Tests")
    class MatchingMilestoneTests {

        @Test
        @DisplayName("First match unlocks FIRST_SPARK")
        void checkAndUnlock_firstMatch_unlocksFirstSpark() {
            // Add 1 match
            addMatches(userId, 1);
            user.setBirthDate(AppClock.today().minusYears(25));
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
            user.setBirthDate(AppClock.today().minusYears(25));
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

        @Test
        @DisplayName("Inactive matches still count toward match milestones")
        void inactiveMatchesStillCount() {
            Match active = Match.create(userId, UUID.randomUUID());
            matchStorage.save(active);

            Match unmatched1 = Match.create(userId, UUID.randomUUID());
            unmatched1.unmatch(userId);
            matchStorage.save(unmatched1);

            Match unmatched2 = Match.create(userId, UUID.randomUUID());
            unmatched2.unmatch(userId);
            matchStorage.save(unmatched2);

            List<AchievementService.AchievementProgress> progress = service.getProgress(userId);

            AchievementService.AchievementProgress firstSpark = progress.stream()
                    .filter(p -> p.achievement() == Achievement.FIRST_SPARK)
                    .findFirst()
                    .orElseThrow();

            assertEquals(3, firstSpark.current());
        }
    }

    // === Helper Methods ===

    private User createActiveUser(UUID id, String name) {
        User u = new User(id, name);
        u.setBirthDate(AppClock.today().minusYears(25));
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

    private static class InMemoryStatsStorage implements StatsStorage {
        private final List<UserAchievement> achievements = new ArrayList<>();

        // Achievement methods (only ones needed for tests)
        @Override
        public void saveUserAchievement(UserAchievement achievement) {
            achievements.add(achievement);
        }

        @Override
        public List<UserAchievement> getUnlockedAchievements(UUID userId) {
            return achievements.stream().filter(a -> a.userId().equals(userId)).toList();
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.stream().anyMatch(a -> a.userId().equals(userId) && a.achievement() == achievement);
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return (int)
                    achievements.stream().filter(a -> a.userId().equals(userId)).count();
        }

        int countForAchievement(UUID userId, Achievement achievement) {
            return (int) achievements.stream()
                    .filter(a -> a.userId().equals(userId) && a.achievement() == achievement)
                    .count();
        }

        // User Stats methods (stubs - not needed for achievement tests)
        @Override
        public void saveUserStats(datingapp.core.Stats.UserStats stats) {
            throw new UnsupportedOperationException("Not needed for achievement tests");
        }

        @Override
        public Optional<datingapp.core.Stats.UserStats> getLatestUserStats(UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<datingapp.core.Stats.UserStats> getUserStatsHistory(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public List<datingapp.core.Stats.UserStats> getAllLatestUserStats() {
            return List.of();
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            return 0;
        }

        // Platform Stats methods (stubs - not needed for achievement tests)
        @Override
        public void savePlatformStats(datingapp.core.Stats.PlatformStats stats) {
            throw new UnsupportedOperationException("Not needed for achievement tests");
        }

        @Override
        public Optional<datingapp.core.Stats.PlatformStats> getLatestPlatformStats() {
            return Optional.empty();
        }

        @Override
        public List<datingapp.core.Stats.PlatformStats> getPlatformStatsHistory(int limit) {
            return List.of();
        }

        // Profile View methods (stubs - not needed for achievement tests)
        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {
            throw new UnsupportedOperationException("Not needed for achievement tests");
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
        public List<UUID> getRecentViewers(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return false;
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant cutoff) {
            return 0; // Not needed for achievement tests
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
                    .add(new Like(UUID.randomUUID(), userId, UUID.randomUUID(), direction, AppClock.now()));
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
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            List<Map.Entry<UUID, Instant>> result = new ArrayList<>();
            for (List<Like> likeList : likes.values()) {
                for (Like like : likeList) {
                    if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                        result.add(Map.entry(like.whoLikes(), like.createdAt()));
                    }
                }
            }
            return result;
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
        private final Map<String, ProfileNote> profileNotes = new ConcurrentHashMap<>();

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
                    .filter(u -> u.getState() == User.UserState.ACTIVE)
                    .toList();
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(noteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(noteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(noteKey(authorId, subjectId)) != null;
        }

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
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
