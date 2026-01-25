package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.Preferences.Interest;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.LikeStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
class LikerBrowserServiceTest {

    @Test
    @DisplayName("Filters out interacted, blocked, matched, and non-active likers")
    void filtersCorrectly() {
        UUID currentUserId = UUID.randomUUID();

        UUID pendingLikerId = UUID.randomUUID();
        UUID alreadyInteractedId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID matchedId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();

        InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();
        likeStorage.addIncomingLike(pendingLikerId);
        likeStorage.addIncomingLike(alreadyInteractedId);
        likeStorage.addIncomingLike(blockedId);
        likeStorage.addIncomingLike(matchedId);
        likeStorage.addIncomingLike(inactiveId);

        likeStorage.addAlreadyInteracted(alreadyInteractedId);

        InMemoryBlockStorage blockStorage = new InMemoryBlockStorage();
        blockStorage.blocked.add(blockedId);

        InMemoryMatchStorage matchStorage = new InMemoryMatchStorage();
        matchStorage.matches.add(Match.create(currentUserId, matchedId));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(pendingLikerId, "Pending"));
        userStorage.put(activeUser(alreadyInteractedId, "Interacted"));
        userStorage.put(activeUser(blockedId, "Blocked"));
        userStorage.put(activeUser(matchedId, "Matched"));
        userStorage.put(incompleteUser(inactiveId, "Inactive"));

        LikerBrowserService service = new LikerBrowserService(likeStorage, userStorage, matchStorage, blockStorage);

        List<User> pending = service.findPendingLikers(currentUserId);

        assertEquals(1, pending.size());
        assertEquals(pendingLikerId, pending.getFirst().getId());
    }

    @Test
    @DisplayName("Orders pending likers by most recent like")
    void ordersByLikedAtDesc() {
        UUID currentUserId = UUID.randomUUID();

        UUID olderLikerId = UUID.randomUUID();
        UUID newerLikerId = UUID.randomUUID();

        InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();
        likeStorage.addIncomingLike(olderLikerId, Instant.parse("2026-01-01T00:00:00Z"));
        likeStorage.addIncomingLike(newerLikerId, Instant.parse("2026-01-02T00:00:00Z"));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(olderLikerId, "Older"));
        userStorage.put(activeUser(newerLikerId, "Newer"));

        LikerBrowserService service = new LikerBrowserService(
                likeStorage, userStorage, new InMemoryMatchStorage(), new InMemoryBlockStorage());

        List<LikerBrowserService.PendingLiker> pending = service.findPendingLikersWithTimes(currentUserId);

        assertEquals(2, pending.size());
        assertEquals(newerLikerId, pending.getFirst().user().getId());
        assertEquals(olderLikerId, pending.get(1).user().getId());
    }

    private static User activeUser(UUID id, String name) {
        return baseUser(id, name, User.State.ACTIVE);
    }

    private static User incompleteUser(UUID id, String name) {
        return baseUser(id, name, User.State.INCOMPLETE);
    }

    private static User baseUser(UUID id, String name, User.State state) {
        User.DatabaseRecord data = User.DatabaseRecord.builder()
                .id(id)
                .name(name)
                .bio(null)
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(User.Gender.OTHER)
                .interestedIn(EnumSet.of(User.Gender.OTHER))
                .lat(0.0)
                .lon(0.0)
                .maxDistanceKm(50)
                .minAge(18)
                .maxAge(99)
                .photoUrls(List.of())
                .state(state)
                .createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH)
                .interests(EnumSet.noneOf(Interest.class))
                .smoking(null)
                .drinking(null)
                .wantsKids(null)
                .lookingFor(null)
                .education(null)
                .heightCm(null)
                .email(null)
                .phone(null)
                .isVerified(false)
                .verificationMethod(null)
                .verificationCode(null)
                .verificationSentAt(null)
                .verifiedAt(null)
                .pacePreferences(null)
                .build();

        return User.fromDatabase(data);
    }

    private static class InMemoryLikeStorage implements LikeStorage {
        private final Set<UUID> whoLikedCurrent = new HashSet<>();
        private final Set<UUID> alreadyInteracted = new HashSet<>();
        private final Map<UUID, Instant> likeTimes = new HashMap<>();

        void addIncomingLike(UUID fromUserId) {
            addIncomingLike(fromUserId, Instant.EPOCH);
        }

        void addIncomingLike(UUID fromUserId, Instant likedAt) {
            whoLikedCurrent.add(fromUserId);
            likeTimes.put(fromUserId, likedAt);
        }

        void addAlreadyInteracted(UUID toUserId) {
            alreadyInteracted.add(toUserId);
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Like like) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return alreadyInteracted;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return whoLikedCurrent;
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            return new HashMap<>(likeTimes);
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID likeId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();

        void put(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public void save(User user) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryMatchStorage implements MatchStorage {
        private final List<Match> matches = new ArrayList<>();

        @Override
        public void save(Match match) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Match match) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Match> get(String matchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String matchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches;
        }

        @Override
        public void delete(String matchId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryBlockStorage implements BlockStorage {
        private final Set<UUID> blocked = new HashSet<>();

        @Override
        public void save(Block block) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            return blocked;
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            throw new UnsupportedOperationException();
        }
    }
}
