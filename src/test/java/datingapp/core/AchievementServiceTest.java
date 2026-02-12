package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.ConnectionModels.Report;
import datingapp.core.model.EngagementDomain.Achievement;
import datingapp.core.model.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.service.ProfileService;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class AchievementServiceTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private TestStorages.Analytics analyticsStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private InMemoryUserStorage userStorage;
    private ProfileService service;
    private AppConfig config;
    private UUID userId;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        analyticsStorage = new TestStorages.Analytics();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        userStorage = new InMemoryUserStorage();
        config = AppConfig.defaults();
        service = new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("returns empty when user missing")
    void returnsEmptyWhenUserMissing() {
        assertTrue(service.checkAndUnlock(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("unlocks FIRST_SPARK at match tier 1")
    void unlocksFirstSparkAtTierOne() {
        createActiveUser(userId);
        addMatches(config.achievementMatchTier1());

        List<UserAchievement> unlocked = service.checkAndUnlock(userId);

        assertTrue(unlocked.stream().map(UserAchievement::achievement).toList().contains(Achievement.FIRST_SPARK));
        assertTrue(analyticsStorage.hasAchievement(userId, Achievement.FIRST_SPARK));
    }

    @Test
    @DisplayName("unlocks GUARDIAN when user reports another profile")
    void unlocksGuardianWhenReporting() {
        createActiveUser(userId);
        trustSafetyStorage.save(Report.create(userId, UUID.randomUUID(), Report.Reason.SPAM, "spam report"));

        List<UserAchievement> unlocked = service.checkAndUnlock(userId);

        assertTrue(unlocked.stream().map(UserAchievement::achievement).toList().contains(Achievement.GUARDIAN));
    }

    @Test
    @DisplayName("progress marks unlocked achievements")
    void progressMarksUnlockedAchievements() {
        createActiveUser(userId);
        addMatches(config.achievementMatchTier1());
        service.checkAndUnlock(userId);

        ProfileService.AchievementProgress firstSpark =
                findProgress(service.getProgress(userId), Achievement.FIRST_SPARK);
        ProfileService.AchievementProgress legend = findProgress(service.getProgress(userId), Achievement.LEGEND);

        assertTrue(firstSpark.unlocked());
        assertEquals(100, firstSpark.getProgressPercent());
        assertFalse(legend.unlocked());
    }

    @Test
    @DisplayName("progress grouped by category contains matching achievements")
    void progressGroupedByCategoryContainsMatching() {
        createActiveUser(userId);

        Map<Achievement.Category, List<ProfileService.AchievementProgress>> grouped =
                service.getProgressByCategory(userId);

        assertTrue(grouped.containsKey(Achievement.Category.MATCHING));
        assertTrue(grouped.get(Achievement.Category.MATCHING).stream()
                .map(ProfileService.AchievementProgress::achievement)
                .toList()
                .contains(Achievement.FIRST_SPARK));
    }

    @Test
    @DisplayName("countUnlocked reflects stored unlock count")
    void countUnlockedReflectsStorage() {
        createActiveUser(userId);
        addMatches(config.achievementMatchTier2());
        service.checkAndUnlock(userId);

        assertTrue(service.countUnlocked(userId) >= 2);
    }

    private ProfileService.AchievementProgress findProgress(
            List<ProfileService.AchievementProgress> progress, Achievement achievement) {
        return progress.stream()
                .filter(item -> item.achievement() == achievement)
                .findFirst()
                .orElseThrow();
    }

    private void createActiveUser(UUID id) {
        User user = User.StorageBuilder.create(id, "Test User", FIXED_INSTANT)
                .bio("A complete-enough test profile bio")
                .birthDate(LocalDate.of(1999, 1, 1))
                .gender(User.Gender.MALE)
                .interestedIn(EnumSet.of(User.Gender.FEMALE))
                .maxDistanceKm(50)
                .ageRange(20, 30)
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .state(User.UserState.ACTIVE)
                .updatedAt(FIXED_INSTANT)
                .build();
        userStorage.save(user);
    }

    private void addMatches(int count) {
        for (int i = 0; i < count; i++) {
            interactionStorage.save(Match.create(userId, UUID.randomUUID()));
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
                    .filter(user -> user.getState() == User.UserState.ACTIVE)
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
}
