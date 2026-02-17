package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.model.Gender;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for the liker browser functionality in MatchingService. These methods
 * allow users to see
 * who has liked them.
 */
@SuppressWarnings("unused")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
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

        InMemoryInteractionStorage interactionStorage = new InMemoryInteractionStorage();
        interactionStorage.addIncomingLike(pendingLikerId);
        interactionStorage.addIncomingLike(alreadyInteractedId);
        interactionStorage.addIncomingLike(blockedId);
        interactionStorage.addIncomingLike(matchedId);
        interactionStorage.addIncomingLike(inactiveId);

        interactionStorage.addAlreadyInteracted(alreadyInteractedId);

        InMemoryTrustSafetyStorage trustSafetyStorage = new InMemoryTrustSafetyStorage();
        trustSafetyStorage.save(Block.create(currentUserId, blockedId));

        interactionStorage.addMatch(Match.create(currentUserId, matchedId));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(pendingLikerId, "Pending"));
        userStorage.put(activeUser(alreadyInteractedId, "Interacted"));
        userStorage.put(activeUser(blockedId, "Blocked"));
        userStorage.put(activeUser(matchedId, "Matched"));
        userStorage.put(incompleteUser(inactiveId, "Inactive"));

        MatchingService service = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .build();

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

        InMemoryInteractionStorage interactionStorage = new InMemoryInteractionStorage();
        interactionStorage.addIncomingLike(olderLikerId, Instant.parse("2026-01-01T00:00:00Z"));
        interactionStorage.addIncomingLike(newerLikerId, Instant.parse("2026-01-02T00:00:00Z"));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(olderLikerId, "Older"));
        userStorage.put(activeUser(newerLikerId, "Newer"));

        MatchingService service = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(new InMemoryTrustSafetyStorage())
                .userStorage(userStorage)
                .build();

        List<PendingLiker> pending = service.findPendingLikersWithTimes(currentUserId);

        assertEquals(2, pending.size());
        assertEquals(newerLikerId, pending.getFirst().user().getId());
        assertEquals(olderLikerId, pending.get(1).user().getId());
    }

    private static User activeUser(UUID id, String name) {
        return baseUser(id, name, UserState.ACTIVE);
    }

    private static User incompleteUser(UUID id, String name) {
        return baseUser(id, name, UserState.INCOMPLETE);
    }

    private static User baseUser(UUID id, String name, UserState state) {
        return User.StorageBuilder.create(id, name, Instant.EPOCH)
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.OTHER)
                .interestedIn(EnumSet.of(Gender.OTHER))
                .state(state)
                .updatedAt(Instant.EPOCH)
                .verified(false)
                .build();
    }

    private static class InMemoryInteractionStorage implements InteractionStorage {
        private final List<Map.Entry<UUID, Instant>> incomingLikes = new ArrayList<>();
        private final Set<UUID> alreadyInteracted = new HashSet<>();
        private final Map<String, Match> matches = new HashMap<>();

        void addIncomingLike(UUID fromUserId) {
            addIncomingLike(fromUserId, Instant.now());
        }

        void addIncomingLike(UUID fromUserId, Instant likedAt) {
            incomingLikes.add(Map.entry(fromUserId, likedAt));
        }

        void addAlreadyInteracted(UUID userId) {
            alreadyInteracted.add(userId);
        }

        void addMatch(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.empty();
        }

        @Override
        public void save(Like like) {}

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
            return Set.copyOf(alreadyInteracted);
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return incomingLikes.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            return List.copyOf(incomingLikes);
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return 0;
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
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {}

        @Override
        public void save(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public void update(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Match> get(String matchId) {
            return Optional.ofNullable(matches.get(matchId));
        }

        @Override
        public boolean exists(String matchId) {
            return matches.containsKey(matchId);
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return getAllMatchesFor(userId).stream().filter(Match::isActive).toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.remove(matchId);
        }

        @Override
        public boolean atomicUndoDelete(UUID likeId, String matchId) {
            return false;
        }
    }

    private static class InMemoryTrustSafetyStorage implements TrustSafetyStorage {
        private final List<Block> blocks = new ArrayList<>();
        private final List<Report> reports = new ArrayList<>();

        @Override
        public void save(Block block) {
            blocks.add(block);
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.stream()
                    .anyMatch(block -> (block.blockerId().equals(userA)
                                    && block.blockedId().equals(userB))
                            || (block.blockerId().equals(userB)
                                    && block.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            return blocks.stream()
                    .filter(block -> block.blockerId().equals(userId))
                    .map(Block::blockedId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<Block> findByBlocker(UUID blockerId) {
            return blocks.stream()
                    .filter(block -> block.blockerId().equals(blockerId))
                    .toList();
        }

        @Override
        public boolean deleteBlock(UUID blockerId, UUID blockedId) {
            return blocks.removeIf(block ->
                    block.blockerId().equals(blockerId) && block.blockedId().equals(blockedId));
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int) blocks.stream()
                    .filter(block -> block.blockerId().equals(userId))
                    .count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int) blocks.stream()
                    .filter(block -> block.blockedId().equals(userId))
                    .count();
        }

        @Override
        public void save(Report report) {
            reports.add(report);
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return (int) reports.stream()
                    .filter(report -> report.reportedUserId().equals(userId))
                    .count();
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return reports.stream()
                    .anyMatch(report -> report.reporterId().equals(reporterId)
                            && report.reportedUserId().equals(reportedUserId));
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return reports.stream()
                    .filter(report -> report.reportedUserId().equals(userId))
                    .toList();
        }

        @Override
        public int countReportsBy(UUID userId) {
            return (int) reports.stream()
                    .filter(report -> report.reporterId().equals(userId))
                    .count();
        }
    }

    private static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();
        private final Map<String, ProfileNote> profileNotes = new java.util.concurrent.ConcurrentHashMap<>();

        void put(User user) {
            users.put(user.getId(), user);
        }

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
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
            return users.values().stream()
                    .filter(user -> user.getState() == UserState.ACTIVE)
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
                    .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(noteKey(authorId, subjectId)) != null;
        }
    }
}
