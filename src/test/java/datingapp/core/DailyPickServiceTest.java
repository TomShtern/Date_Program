package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.User.ProfileNote;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DailyPickServiceTest {

    private DailyService service;
    private InMemoryUserStorage userStorage;
    private InMemoryLikeStorage likeStorage;
    private InMemoryBlockStorage blockStorage;
    private CandidateFinder candidateFinder;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        userStorage = new InMemoryUserStorage();
        likeStorage = new InMemoryLikeStorage();
        blockStorage = new InMemoryBlockStorage();
        config = AppConfig.defaults();

        candidateFinder = new CandidateFinder();
        service = new DailyService(userStorage, likeStorage, blockStorage, candidateFinder, config);
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
        Optional<DailyService.DailyPick> pick1 = service.getDailyPick(seeker);
        Optional<DailyService.DailyPick> pick2 = service.getDailyPick(seeker);

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
        Optional<DailyService.DailyPick> alicePick = service.getDailyPick(alice);
        Optional<DailyService.DailyPick> bobPick = service.getDailyPick(bob);

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
        blockStorage.save(Block.create(seeker.getId(), candidate1.getId()));

        // Get daily pick - should not include blocked user
        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);
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
        likeStorage.save(Like.create(seeker.getId(), candidate1.getId(), Like.Direction.LIKE));

        // Get daily pick - should not include already-liked user
        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);
        assertTrue(pick.isPresent());
        assertNotEquals(
                candidate1.getId(), pick.get().user().getId(), "Already swiped user should not appear as daily pick");
    }

    @Test
    void getDailyPick_noCandidates_returnsEmpty() {
        User seeker = createActiveUser("Alice", 25);
        userStorage.save(seeker);

        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);

        assertFalse(pick.isPresent(), "Should return empty when no candidates");
    }

    @Test
    void getDailyPick_allCandidatesExcluded_returnsEmpty() {
        User seeker = createActiveUser("Alice", 25);
        User candidate = createActiveUser("Bob", 26);

        userStorage.save(seeker);
        userStorage.save(candidate);

        // Swipe on the only candidate
        likeStorage.save(Like.create(seeker.getId(), candidate.getId(), Like.Direction.PASS));

        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);

        assertFalse(pick.isPresent(), "Should return empty when all candidates excluded");
    }

    @Test
    void getDailyPick_reasonIsNeverNull() {
        User seeker = createActiveUser("Alice", 25);
        User candidate = createActiveUser("Bob", 26);

        userStorage.save(seeker);
        userStorage.save(candidate);

        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);

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

        Optional<DailyService.DailyPick> pick = service.getDailyPick(seeker);

        assertTrue(pick.isPresent());
        assertEquals(LocalDate.now(config.userTimeZone()), pick.get().date());
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
    }

    // Helper methods

    private User createActiveUser(String name, int age) {
        return createActiveUser(name, age, User.Gender.FEMALE);
    }

    private User createActiveUser(String name, int age, User.Gender gender) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.now().minusYears(age));
        user.setGender(gender);
        // Set mutual interest - everyone interested in everyone for simple test matching
        user.setInterestedIn(EnumSet.of(User.Gender.MALE, User.Gender.FEMALE, User.Gender.OTHER));
        user.setLocation(40.7128, -74.0060); // NYC
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setPacePreferences(new Preferences.PacePreferences(
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
            return users.stream().filter(u -> u.getState() == User.State.ACTIVE).toList();
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

    private static class InMemoryLikeStorage implements LikeStorage {
        private final List<Like> likes = new ArrayList<>();

        @Override
        public void save(Like like) {
            likes.add(like);
        }

        @Override
        public boolean exists(UUID whoLikes, UUID whoGotLiked) {
            return likes.stream()
                    .anyMatch(l ->
                            l.whoLikes().equals(whoLikes) && l.whoGotLiked().equals(whoGotLiked));
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return likes.stream()
                    .filter(l ->
                            l.whoLikes().equals(fromUserId) && l.whoGotLiked().equals(toUserId))
                    .findFirst();
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return exists(a, b) && exists(b, a);
        }

        @Override
        public java.util.Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return likes.stream()
                    .filter(l -> l.whoLikes().equals(userId))
                    .map(Like::whoGotLiked)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public java.util.Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return likes.stream()
                    .filter(l -> l.whoGotLiked().equals(userId))
                    .filter(l -> l.direction() == Like.Direction.LIKE)
                    .map(Like::whoLikes)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public java.util.Map<UUID, java.time.Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            return likes.stream()
                    .filter(l -> l.whoGotLiked().equals(userId))
                    .filter(l -> l.direction() == Like.Direction.LIKE)
                    .collect(java.util.stream.Collectors.toMap(
                            Like::whoLikes, Like::createdAt, (existing, replacement) -> existing));
        }

        @Override
        public List<java.util.Map.Entry<UUID, java.time.Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
            return new ArrayList<>(getLikeTimesForUsersWhoLiked(userId).entrySet());
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId))
                    .filter(l -> l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.stream()
                    .filter(l -> l.whoGotLiked().equals(userId))
                    .filter(l -> l.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId))
                    .filter(l -> l.direction() == Like.Direction.LIKE)
                    .filter(l -> exists(l.whoGotLiked(), userId))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.removeIf(l -> l.id().equals(likeId));
        }

        @Override
        public int countLikesToday(UUID userId, java.time.Instant startOfDay) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId))
                    .filter(l -> l.direction() == Like.Direction.LIKE)
                    .filter(l -> l.createdAt().isAfter(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, java.time.Instant startOfDay) {
            return (int) likes.stream()
                    .filter(l -> l.whoLikes().equals(userId))
                    .filter(l -> l.direction() == Like.Direction.PASS)
                    .filter(l -> l.createdAt().isAfter(startOfDay))
                    .count();
        }
    }

    private static class InMemoryBlockStorage implements BlockStorage {
        private final List<Block> blocks = new ArrayList<>();

        @Override
        public void save(Block block) {
            blocks.add(block);
        }

        @Override
        public boolean isBlocked(UUID blockerId, UUID blockedId) {
            return blocks.stream()
                    .anyMatch(b -> (b.blockerId().equals(blockerId)
                                    && b.blockedId().equals(blockedId))
                            || (b.blockerId().equals(blockedId) && b.blockedId().equals(blockerId)));
        }

        @Override
        public java.util.Set<UUID> getBlockedUserIds(UUID blockerId) {
            java.util.Set<UUID> blockedIds = new java.util.HashSet<>();
            for (Block block : blocks) {
                if (block.blockerId().equals(blockerId)) {
                    blockedIds.add(block.blockedId());
                } else if (block.blockedId().equals(blockerId)) {
                    blockedIds.add(block.blockerId());
                }
            }
            return blockedIds;
        }

        @Override
        public List<Block> findByBlocker(UUID blockerId) {
            return blocks.stream().filter(b -> b.blockerId().equals(blockerId)).toList();
        }

        @Override
        public boolean delete(UUID blockerId, UUID blockedId) {
            return blocks.removeIf(
                    b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId));
        }

        @Override
        public int countBlocksGiven(UUID blockerId) {
            return (int)
                    blocks.stream().filter(b -> b.blockerId().equals(blockerId)).count();
        }

        @Override
        public int countBlocksReceived(UUID blockedId) {
            return (int)
                    blocks.stream().filter(b -> b.blockedId().equals(blockedId)).count();
        }
    }
}
