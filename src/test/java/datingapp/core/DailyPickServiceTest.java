package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.model.*;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.profile.ProfileService;
import datingapp.core.recommendation.*;
import datingapp.core.recommendation.RecommendationService.DailyPick;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DailyPickServiceTest {

    private RecommendationService service;
    private InMemoryUserStorage userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private CandidateFinder candidateFinder;
    private AppConfig config;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        userStorage = new InMemoryUserStorage();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        config = AppConfig.defaults();

        candidateFinder = new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, config);
        // Create dummies for missing dependencies
        var standoutStorage = new TestStorages.Standouts();
        var profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        service = RecommendationService.builder()
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .clock(AppClock.clock())
                .build();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    void getDailyPick_sameDateSameUser_returnsSamePick() {
        // Create seeker and candidates
        User seeker = createActiveUser("Alice", 25);
        User candidate1 = createActiveUser("Bob", 26);
        User candidate2 = createActiveUser("Charlie", 27);
        User candidate3 = createActiveUser("David", 28);

        userStorage.save(seeker);
        userStorage.save(candidate1);
        userStorage.save(candidate2);
        userStorage.save(candidate3);

        // Get daily pick twice in the same call
        Optional<DailyPick> pick1 = service.getDailyPick(seeker);
        Optional<DailyPick> pick2 = service.getDailyPick(seeker);

        assertTrue(pick1.isPresent());
        assertTrue(pick2.isPresent());
        assertEquals(
                pick1.get().user().getId(), pick2.get().user().getId(), "Same user should get same pick on same date");
        assertEquals(pick1.get().reason(), pick2.get().reason(), "Reason should be same for deterministic pick");
    }

    @Test
    void getDailyPick_differentUsers_mayReturnDifferentPicks() {
        // Create two seekers and candidates
        User alice = createActiveUser("Alice", 25);
        User bob = createActiveUser("Bob", 26);
        User candidate1 = createActiveUser("Charlie", 27);
        User candidate2 = createActiveUser("David", 28);
        User candidate3 = createActiveUser("Eve", 29);

        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(candidate1);
        userStorage.save(candidate2);
        userStorage.save(candidate3);

        // Get daily picks for different users
        Optional<DailyPick> alicePick = service.getDailyPick(alice);
        Optional<DailyPick> bobPick = service.getDailyPick(bob);

        assertTrue(alicePick.isPresent());
        assertTrue(bobPick.isPresent());
        // Different users should generally get different picks (deterministic per user)
        // Note: They could theoretically get same pick by chance with only 3 candidates
    }

    @Test
    void getDailyPick_respectsBlockList() {
        User seeker = createActiveUser("Alice", 25);
        User candidate1 = createActiveUser("Bob", 26);
        User candidate2 = createActiveUser("Charlie", 27);

        userStorage.save(seeker);
        userStorage.save(candidate1);
        userStorage.save(candidate2);

        // Block one candidate
        trustSafetyStorage.save(Block.create(seeker.getId(), candidate1.getId()));

        // Get daily pick - should not include blocked user
        Optional<DailyPick> pick = service.getDailyPick(seeker);
        assertTrue(pick.isPresent());
        assertNotEquals(candidate1.getId(), pick.get().user().getId(), "Blocked user should not appear as daily pick");
    }

    @Test
    void getDailyPick_excludesAlreadySwiped() {
        User seeker = createActiveUser("Alice", 25);
        User candidate1 = createActiveUser("Bob", 26);
        User candidate2 = createActiveUser("Charlie", 27);
        User candidate3 = createActiveUser("David", 28);

        userStorage.save(seeker);
        userStorage.save(candidate1);
        userStorage.save(candidate2);
        userStorage.save(candidate3);

        // Like one candidate
        interactionStorage.save(Like.create(seeker.getId(), candidate1.getId(), Like.Direction.LIKE));

        // Get daily pick - should not include already-liked user
        Optional<DailyPick> pick = service.getDailyPick(seeker);
        assertTrue(pick.isPresent());
        assertNotEquals(
                candidate1.getId(), pick.get().user().getId(), "Already swiped user should not appear as daily pick");
    }

    @Test
    void getDailyPick_noCandidates_returnsEmpty() {
        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        Optional<DailyPick> pick = service.getDailyPick(seeker);

        assertFalse(pick.isPresent(), "Should return empty when no candidates");
    }

    @Test
    void getDailyPick_allCandidatesExcluded_returnsEmpty() {
        User seeker = createActiveUser("Alice", 25);
        User candidate = createActiveUser("Bob", 26);

        userStorage.save(seeker);
        userStorage.save(candidate);

        // Swipe on the only candidate
        interactionStorage.save(Like.create(seeker.getId(), candidate.getId(), Like.Direction.PASS));

        Optional<DailyPick> pick = service.getDailyPick(seeker);

        assertFalse(pick.isPresent(), "Should return empty when all candidates excluded");
    }

    @Test
    void getDailyPick_reasonIsNeverNull() {
        User seeker = createActiveUser("Alice", 25);
        User candidate = createActiveUser("Bob", 26);

        userStorage.save(seeker);
        userStorage.save(candidate);

        Optional<DailyPick> pick = service.getDailyPick(seeker);

        assertTrue(pick.isPresent());
        assertNotNull(pick.get().reason());
        assertFalse(pick.get().reason().isBlank(), "Reason should not be blank");
    }

    @Test
    void getDailyPick_setsTodaysDate() {
        User seeker = createActiveUser("Alice", 25);
        User candidate = createActiveUser("Bob", 26);

        userStorage.save(seeker);
        userStorage.save(candidate);

        Optional<DailyPick> pick = service.getDailyPick(seeker);

        assertTrue(pick.isPresent());
        assertEquals(AppClock.today(config.userTimeZone()), pick.get().date());
    }

    @Test
    void hasViewedDailyPick_returnsFalse_whenNotViewed() {
        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        assertFalse(service.hasViewedDailyPick(seeker.getId()));
    }

    @Test
    void hasViewedDailyPick_returnsTrue_afterMarking() {
        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        service.markDailyPickViewed(seeker.getId());

        assertTrue(service.hasViewedDailyPick(seeker.getId()));
    }

    @Test
    void markDailyPickViewed_storesCorrectDate() {
        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        service.markDailyPickViewed(seeker.getId());

        assertTrue(service.hasViewedDailyPick(seeker.getId()));
        assertEquals(0, service.cleanupOldDailyPickViews(AppClock.today(config.userTimeZone())));
        assertEquals(
                1,
                service.cleanupOldDailyPickViews(
                        AppClock.today(config.userTimeZone()).plusDays(1)));
    }

    @Test
    void cleanupOldDailyPickViews_removesOldEntries() {
        LocalDate oldDate = AppClock.today(config.userTimeZone()).minusDays(10);
        ZoneId zone = config.userTimeZone();
        Clock oldClock = Clock.fixed(oldDate.atStartOfDay(zone).toInstant(), zone);
        RecommendationService oldService = RecommendationService.builder()
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new TestStorages.Standouts())
                .profileService(new ProfileService(
                        config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage))
                .config(config)
                .clock(oldClock)
                .build();

        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        oldService.markDailyPickViewed(seeker.getId());

        int removed = oldService.cleanupOldDailyPickViews(oldDate.plusDays(7));
        assertEquals(1, removed);
        assertFalse(oldService.hasViewedDailyPick(seeker.getId()));
    }

    @Test
    void getDailyPick_cachedPickSurvivesCandidateListChanges() {
        User seeker = createActiveUser("Seeker", 25);
        User candidate1 = createActiveUser("Candidate1", 26);
        User candidate2 = createActiveUser("Candidate2", 27);
        User candidate3 = createActiveUser("Candidate3", 28);

        userStorage.save(seeker);
        userStorage.save(candidate1);
        userStorage.save(candidate2);
        userStorage.save(candidate3);

        DailyPick firstPick = service.getDailyPick(seeker).orElseThrow();
        UUID pickedId = firstPick.user().getId();

        if (!candidate1.getId().equals(pickedId)) {
            userStorage.delete(candidate1.getId());
        } else {
            userStorage.delete(candidate2.getId());
        }

        DailyPick secondPick = service.getDailyPick(seeker).orElseThrow();

        assertEquals(pickedId, secondPick.user().getId());
    }

    // Helper methods

    private User createActiveUser(String name, int age) {
        return createActiveUser(name, age, User.Gender.FEMALE);
    }

    private User createActiveUser(String name, int age, User.Gender gender) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(gender);
        // Set mutual interest - everyone interested in everyone for simple test
        // matching
        user.setInterestedIn(EnumSet.of(User.Gender.MALE, User.Gender.FEMALE, User.Gender.OTHER));
        user.setLocation(40.7128, -74.0060); // NYC
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    // In-memory test storage implementations

    private static class InMemoryUserStorage implements UserStorage {
        private final List<User> users = new ArrayList<>();
        private final java.util.Map<String, ProfileNote> profileNotes = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(User user) {
            users.removeIf(u -> u.getId().equals(user.getId()));
            users.add(user);
        }

        @Override
        public User get(UUID id) {
            return users.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users);
        }

        @Override
        public List<User> findActive() {
            return users.stream()
                    .filter(u -> u.getState() == User.UserState.ACTIVE)
                    .toList();
        }

        @Override
        public void delete(UUID id) {
            users.removeIf(u -> u.getId().equals(id));
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
                    .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
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
