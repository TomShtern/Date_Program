package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        likeStorage.addIncomingLike(currentUserId, pendingLikerId);
        likeStorage.addIncomingLike(currentUserId, alreadyInteractedId);
        likeStorage.addIncomingLike(currentUserId, blockedId);
        likeStorage.addIncomingLike(currentUserId, matchedId);
        likeStorage.addIncomingLike(currentUserId, inactiveId);

        likeStorage.addAlreadyInteracted(currentUserId, alreadyInteractedId);

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
        likeStorage.addIncomingLike(currentUserId, olderLikerId, Instant.parse("2026-01-01T00:00:00Z"));
        likeStorage.addIncomingLike(currentUserId, newerLikerId, Instant.parse("2026-01-02T00:00:00Z"));

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
        return User.fromDatabase(
                id,
                name,
                null,
                LocalDate.of(1990, 1, 1),
                User.Gender.OTHER,
                EnumSet.of(User.Gender.OTHER),
                0.0,
                0.0,
                50,
                18,
                99,
                List.of(),
                User.State.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH,
                EnumSet.noneOf(Interest.class),
                null, // smoking
                null, // drinking
                null, // wantsKids
                null, // lookingFor
                null, // email
                null, // phone
                false, // isVerified
                null, // verificationMethod
                null, // verificationCode
                null, // verificationSentAt
                null, // verifiedAt
                null); // pacePreferences
    }

    private static User incompleteUser(UUID id, String name) {
        return User.fromDatabase(
                id,
                name,
                null,
                null,
                null,
                null,
                0.0,
                0.0,
                50,
                18,
                99,
                List.of(),
                User.State.INCOMPLETE,
                Instant.EPOCH,
                Instant.EPOCH,
                EnumSet.noneOf(Interest.class),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static class InMemoryLikeStorage implements LikeStorage {
        private final Set<UUID> whoLikedCurrent = new HashSet<>();
        private final Set<UUID> alreadyInteracted = new HashSet<>();
        private final Map<UUID, Instant> likeTimes = new HashMap<>();

        void addIncomingLike(UUID toUserId, UUID fromUserId) {
            addIncomingLike(toUserId, fromUserId, Instant.EPOCH);
        }

        void addIncomingLike(UUID toUserId, UUID fromUserId, Instant likedAt) {
            whoLikedCurrent.add(fromUserId);
            likeTimes.put(fromUserId, likedAt);
        }

        void addAlreadyInteracted(UUID fromUserId, UUID toUserId) {
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
