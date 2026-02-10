package datingapp.core.testutil;

import datingapp.core.AppClock;
import datingapp.core.Match;
import datingapp.core.Standout;
import datingapp.core.User;
import datingapp.core.User.ProfileNote;
import datingapp.core.User.UserState;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated in-memory storage implementations for unit testing.
 * All implementations are simple HashMap-backed mocks with test helper methods.
 *
 * <p>Usage:
 * <pre>
 * var userStorage = new TestStorages.Users();
 * var likeStorage = new TestStorages.Likes();
 * var matchStorage = new TestStorages.Matches();
 * var blockStorage = new TestStorages.Blocks();
 * </pre>
 */
public final class TestStorages {
    private TestStorages() {} // Utility class - no instantiation

    /**
     * In-memory UserStorage implementation for testing.
     */
    public static class Users implements UserStorage {
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
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == UserState.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
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

        // === Test Helpers ===

        /** Clears all users */
        public void clear() {
            users.clear();
        }

        /** Returns number of users stored */
        public int size() {
            return users.size();
        }

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
        }
    }

    /**
     * In-memory BlockStorage implementation for testing.
     */
    public static class Blocks implements BlockStorage {
        private final Set<Block> blocks = new HashSet<>();

        @Override
        public void save(Block block) {
            blocks.add(block);
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.stream()
                    .anyMatch(b -> (b.blockerId().equals(userA) && b.blockedId().equals(userB))
                            || (b.blockerId().equals(userB) && b.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Block block : blocks) {
                if (block.blockerId().equals(userId)) {
                    result.add(block.blockedId());
                } else if (block.blockedId().equals(userId)) {
                    result.add(block.blockerId());
                }
            }
            return result;
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
        public int countBlocksGiven(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockerId().equals(userId)).count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockedId().equals(userId)).count();
        }

        // === Test Helpers ===

        /** Clears all blocks */
        public void clear() {
            blocks.clear();
        }

        /** Returns number of blocks stored */
        public int size() {
            return blocks.size();
        }
    }

    /**
     * In-memory MatchStorage implementation for testing.
     */
    public static class Matches implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

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
            return matches.values().stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.remove(matchId);
        }

        // === Test Helpers ===

        /** Clears all matches */
        public void clear() {
            matches.clear();
        }

        /** Returns number of matches stored */
        public int size() {
            return matches.size();
        }

        /** Returns all matches */
        public List<Match> getAll() {
            return new ArrayList<>(matches.values());
        }
    }

    /**
     * In-memory LikeStorage implementation for testing.
     */
    public static class Likes implements LikeStorage {
        private final Map<String, Like> likes = new HashMap<>();

        @Override
        public boolean exists(UUID from, UUID to) {
            return likes.containsKey(key(from, to));
        }

        @Override
        public void save(Like like) {
            likes.put(key(like.whoLikes(), like.whoGotLiked()), like);
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoLikes().equals(userId)) {
                    result.add(like.whoGotLiked());
                }
            }
            return result;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.add(like.whoLikes());
                }
            }
            return result;
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            List<Map.Entry<UUID, Instant>> result = new ArrayList<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.add(Map.entry(like.whoLikes(), like.createdAt()));
                }
            }
            return result;
        }

        @Override
        public boolean mutualLikeExists(UUID user1, UUID user2) {
            Like like1 = likes.get(key(user1, user2));
            Like like2 = likes.get(key(user2, user1));
            return like1 != null
                    && like1.direction() == Like.Direction.LIKE
                    && like2 != null
                    && like2.direction() == Like.Direction.LIKE;
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoGotLiked().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && mutualLikeExists(userId, l.whoGotLiked()))
                    .count();
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.ofNullable(likes.get(key(fromUserId, toUserId)));
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && !l.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.PASS
                            && !l.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.values().removeIf(like -> like.id().equals(likeId));
        }

        // === Test Helpers ===

        /** Clears all likes */
        public void clear() {
            likes.clear();
        }

        /** Returns number of likes stored */
        public int size() {
            return likes.size();
        }

        private String key(UUID from, UUID to) {
            return from.toString() + "->" + to.toString();
        }
    }

    /**
     * In-memory Standout.Storage implementation for testing.
     */
    public static class Standouts implements Standout.Storage {
        private final Map<String, List<Standout>> standoutsByDate = new HashMap<>();

        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, java.time.LocalDate date) {
            standoutsByDate.put(key(seekerId, date), new ArrayList<>(standouts));
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, java.time.LocalDate date) {
            return standoutsByDate.getOrDefault(key(seekerId, date), List.of());
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, java.time.LocalDate date) {
            List<Standout> list = standoutsByDate.get(key(seekerId, date));
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    Standout s = list.get(i);
                    if (s.standoutUserId().equals(standoutUserId)) {
                        list.set(i, s.withInteraction(AppClock.now()));
                        break;
                    }
                }
            }
        }

        @Override
        public int cleanup(java.time.LocalDate before) {
            int removed = 0;
            var iter = standoutsByDate.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                java.time.LocalDate date =
                        java.time.LocalDate.parse(entry.getKey().split("\\|")[1]);
                if (date.isBefore(before)) {
                    removed += entry.getValue().size();
                    iter.remove();
                }
            }
            return removed;
        }

        private String key(UUID seekerId, java.time.LocalDate date) {
            return seekerId.toString() + "|" + date.toString();
        }

        public void clear() {
            standoutsByDate.clear();
        }
    }

    /**
     * In-memory UndoState.Storage implementation for testing.
     */
    public static class Undos implements datingapp.core.UndoState.Storage {
        private final Map<UUID, datingapp.core.UndoState> undoStates = new HashMap<>();

        @Override
        public void save(datingapp.core.UndoState state) {
            undoStates.put(state.userId(), state);
        }

        @Override
        public java.util.Optional<datingapp.core.UndoState> findByUserId(UUID userId) {
            return java.util.Optional.ofNullable(undoStates.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return undoStates.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            int removed = 0;
            var iter = undoStates.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (entry.getValue().isExpired(now)) {
                    iter.remove();
                    removed++;
                }
            }
            return removed;
        }

        @Override
        public java.util.List<datingapp.core.UndoState> findAll() {
            return new java.util.ArrayList<>(undoStates.values());
        }

        public void clear() {
            undoStates.clear();
        }

        public int size() {
            return undoStates.size();
        }
    }
}
