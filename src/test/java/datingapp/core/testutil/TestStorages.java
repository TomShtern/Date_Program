package datingapp.core.testutil;

import datingapp.core.AppClock;
import datingapp.core.model.*;
import datingapp.core.model.Achievement;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.Match;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.Messaging.Message;
import datingapp.core.model.Standout;
import datingapp.core.model.Stats.PlatformStats;
import datingapp.core.model.Stats.UserStats;
import datingapp.core.model.User;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.User.UserState;
import datingapp.core.model.UserInteractions.Block;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.model.UserInteractions.Report;
import datingapp.core.service.*;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.time.LocalDate;
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
 * var trustSafetyStorage = new TestStorages.TrustSafety();
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
     * In-memory TrustSafetyStorage implementation for testing.
     */
    public static class TrustSafety implements TrustSafetyStorage {
        private final Set<Block> blocks = new HashSet<>();
        private final List<Report> reports = new ArrayList<>();

        // ─── Block operations ───

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
        public boolean deleteBlock(UUID blockerId, UUID blockedId) {
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

        // ─── Report operations ───

        @Override
        public void save(Report report) {
            reports.add(report);
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return (int) reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .count();
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return reports.stream()
                    .anyMatch(r -> r.reporterId().equals(reporterId)
                            && r.reportedUserId().equals(reportedUserId));
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .toList();
        }

        @Override
        public int countReportsBy(UUID userId) {
            return (int)
                    reports.stream().filter(r -> r.reporterId().equals(userId)).count();
        }

        // ─── Test Helpers ───

        /** Clears all blocks and reports */
        public void clear() {
            blocks.clear();
            reports.clear();
        }

        /** Returns number of blocks stored */
        public int blockSize() {
            return blocks.size();
        }

        /** Returns number of reports stored */
        public int reportSize() {
            return reports.size();
        }

        /** Returns total size (blocks + reports) */
        public int size() {
            return blocks.size() + reports.size();
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
    public static class Undos implements datingapp.core.model.UndoState.Storage {
        private final Map<UUID, datingapp.core.model.UndoState> undoStates = new HashMap<>();

        @Override
        public void save(datingapp.core.model.UndoState state) {
            undoStates.put(state.userId(), state);
        }

        @Override
        public java.util.Optional<datingapp.core.model.UndoState> findByUserId(UUID userId) {
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
        public java.util.List<datingapp.core.model.UndoState> findAll() {
            return new java.util.ArrayList<>(undoStates.values());
        }

        public void clear() {
            undoStates.clear();
        }

        public int size() {
            return undoStates.size();
        }
    }

    /**
     * In-memory StatsStorage implementation for testing.
     */
    public static class Stats implements StatsStorage {
        private final Map<UUID, List<UserStats>> userStats = new HashMap<>();
        private final List<PlatformStats> platformStats = new ArrayList<>();
        private final Map<String, Instant> profileViews = new HashMap<>();
        private final Map<UUID, List<UserAchievement>> achievements = new HashMap<>();
        private final Set<String> dailyPickViews = new HashSet<>();

        @Override
        public void saveUserStats(UserStats stats) {
            userStats
                    .computeIfAbsent(stats.userId(), ignored -> new ArrayList<>())
                    .add(stats);
        }

        @Override
        public Optional<UserStats> getLatestUserStats(UUID userId) {
            List<UserStats> list = userStats.get(userId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }

        @Override
        public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
            List<UserStats> list = userStats.getOrDefault(userId, List.of());
            return list.subList(Math.max(0, list.size() - limit), list.size());
        }

        @Override
        public List<UserStats> getAllLatestUserStats() {
            List<UserStats> result = new ArrayList<>();
            for (List<UserStats> list : userStats.values()) {
                if (!list.isEmpty()) {
                    result.add(list.get(list.size() - 1));
                }
            }
            return result;
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            return 0;
        }

        @Override
        public void savePlatformStats(PlatformStats stats) {
            platformStats.add(stats);
        }

        @Override
        public Optional<PlatformStats> getLatestPlatformStats() {
            return platformStats.isEmpty()
                    ? Optional.empty()
                    : Optional.of(platformStats.get(platformStats.size() - 1));
        }

        @Override
        public List<PlatformStats> getPlatformStatsHistory(int limit) {
            int size = platformStats.size();
            return platformStats.subList(Math.max(0, size - limit), size);
        }

        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {
            profileViews.put(viewerId + "_" + viewedId, Instant.now());
        }

        @Override
        public int getProfileViewCount(UUID userId) {
            return (int) profileViews.keySet().stream()
                    .filter(k -> k.endsWith("_" + userId))
                    .count();
        }

        @Override
        public int getUniqueViewerCount(UUID userId) {
            return getProfileViewCount(userId);
        }

        @Override
        public List<UUID> getRecentViewers(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return profileViews.containsKey(viewerId + "_" + viewedId);
        }

        @Override
        public void saveUserAchievement(UserAchievement achievement) {
            achievements
                    .computeIfAbsent(achievement.userId(), ignored -> new ArrayList<>())
                    .add(achievement);
        }

        @Override
        public List<UserAchievement> getUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, List.of());
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.getOrDefault(userId, List.of()).stream().anyMatch(a -> a.achievement() == achievement);
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, List.of()).size();
        }

        @Override
        public void markDailyPickAsViewed(UUID userId, LocalDate date) {
            dailyPickViews.add(userId + "_" + date);
        }

        @Override
        public boolean isDailyPickViewed(UUID userId, LocalDate date) {
            return dailyPickViews.contains(userId + "_" + date);
        }

        @Override
        public int deleteDailyPickViewsOlderThan(LocalDate before) {
            return 0;
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant cutoff) {
            return 0;
        }

        public void clear() {
            userStats.clear();
            platformStats.clear();
            profileViews.clear();
            achievements.clear();
            dailyPickViews.clear();
        }
    }

    /**
     * In-memory SocialStorage implementation for testing.
     */
    public static class Social implements SocialStorage {
        private final Map<UUID, FriendRequest> friendRequests = new HashMap<>();
        private final Map<UUID, Notification> notifications = new HashMap<>();

        @Override
        public void saveFriendRequest(FriendRequest request) {
            friendRequests.put(request.id(), request);
        }

        @Override
        public void updateFriendRequest(FriendRequest request) {
            friendRequests.put(request.id(), request);
        }

        @Override
        public Optional<FriendRequest> getFriendRequest(UUID id) {
            return Optional.ofNullable(friendRequests.get(id));
        }

        @Override
        public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
            return friendRequests.values().stream()
                    .filter(FriendRequest::isPending)
                    .filter(r -> (r.fromUserId().equals(user1) && r.toUserId().equals(user2))
                            || (r.fromUserId().equals(user2) && r.toUserId().equals(user1)))
                    .findFirst();
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
            return friendRequests.values().stream()
                    .filter(r -> r.toUserId().equals(userId) && r.isPending())
                    .toList();
        }

        @Override
        public void deleteFriendRequest(UUID id) {
            friendRequests.remove(id);
        }

        @Override
        public void saveNotification(Notification notification) {
            notifications.put(notification.id(), notification);
        }

        @Override
        public void markNotificationAsRead(UUID id) {
            Notification n = notifications.get(id);
            if (n != null && !n.isRead()) {
                notifications.put(
                        id,
                        new Notification(
                                n.id(), n.userId(), n.type(), n.title(), n.message(), n.createdAt(), true, n.data()));
            }
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return notifications.values().stream()
                    .filter(n -> n.userId().equals(userId))
                    .filter(n -> !unreadOnly || !n.isRead())
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList();
        }

        @Override
        public Optional<Notification> getNotification(UUID id) {
            return Optional.ofNullable(notifications.get(id));
        }

        @Override
        public void deleteNotification(UUID id) {
            notifications.remove(id);
        }

        @Override
        public void deleteOldNotifications(Instant before) {
            notifications.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(before));
        }

        public void clear() {
            friendRequests.clear();
            notifications.clear();
        }
    }

    /**
     * In-memory MessagingStorage implementation for testing.
     */
    public static class Messaging implements MessagingStorage {
        private final Map<String, Conversation> conversations = new HashMap<>();
        private final Map<String, List<Message>> messages = new HashMap<>();

        @Override
        public void saveConversation(Conversation conversation) {
            conversations.put(conversation.getId(), conversation);
        }

        @Override
        public Optional<Conversation> getConversation(String conversationId) {
            return Optional.ofNullable(conversations.get(conversationId));
        }

        @Override
        public Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
            String id = Conversation.generateId(userA, userB);
            return getConversation(id);
        }

        @Override
        public List<Conversation> getConversationsFor(UUID userId) {
            return conversations.values().stream()
                    .filter(c -> c.involves(userId))
                    .toList();
        }

        @Override
        public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
            // No-op for tests
        }

        @Override
        public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
            // No-op for tests
        }

        @Override
        public void archiveConversation(String conversationId, Match.ArchiveReason reason) {
            // No-op for tests
        }

        @Override
        public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
            // No-op for tests
        }

        @Override
        public void deleteConversation(String conversationId) {
            conversations.remove(conversationId);
        }

        @Override
        public void saveMessage(Message message) {
            messages.computeIfAbsent(message.conversationId(), ignored -> new ArrayList<>())
                    .add(message);
        }

        @Override
        public List<Message> getMessages(String conversationId, int limit, int offset) {
            return messages.getOrDefault(conversationId, List.of());
        }

        @Override
        public Optional<Message> getLatestMessage(String conversationId) {
            List<Message> list = messages.get(conversationId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }

        @Override
        public int countMessages(String conversationId) {
            return messages.getOrDefault(conversationId, List.of()).size();
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            return 0;
        }

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            return 0;
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
            return 0;
        }

        @Override
        public void deleteMessagesByConversation(String conversationId) {
            messages.remove(conversationId);
        }

        public void clear() {
            conversations.clear();
            messages.clear();
        }
    }
}
