package datingapp.core.testutil;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** In-memory storages for tests. */
public final class TestStorages {

    private static final String NOTE_DELIMITER = ":";
    private static final String DAILY_PICK_DELIMITER = "|";
    private static final String STANDOUT_DELIMITER = "|";

    private TestStorages() {}

    private static String profileNoteKey(UUID authorId, UUID subjectId) {
        return authorId + NOTE_DELIMITER + subjectId;
    }

    private static String dailyPickKey(UUID userId, LocalDate date) {
        return userId + DAILY_PICK_DELIMITER + date;
    }

    private static String standoutKey(UUID seekerId, LocalDate date) {
        return seekerId + STANDOUT_DELIMITER + date;
    }

    public static class Users implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();
        private final Map<String, ProfileNote> profileNotes = new HashMap<>();

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
                    .filter(user -> user.getState() == UserState.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findCandidates(
                UUID excludeId,
                Set<Gender> genders,
                int minAge,
                int maxAge,
                double seekerLat,
                double seekerLon,
                int maxDistanceKm) {
            // In-memory pre-filter: only active-state and gender checks.
            // Age is approximate; exact checks remain in CandidateFinder.
            return users.values().stream()
                    .filter(u -> !u.getId().equals(excludeId))
                    .filter(u -> u.getState() == UserState.ACTIVE)
                    .filter(u -> genders == null || genders.isEmpty() || genders.contains(u.getGender()))
                    .filter(u -> u.getAge() >= minAge && u.getAge() <= maxAge)
                    .toList();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public Map<UUID, User> findByIds(Set<UUID> ids) {
            if (ids == null || ids.isEmpty()) {
                return Map.of();
            }
            Map<UUID, User> found = new HashMap<>();
            for (UUID id : ids) {
                User user = users.get(id);
                if (user != null) {
                    found.put(id, user);
                }
            }
            return found;
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(profileNoteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(profileNoteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .sorted(Comparator.comparing(ProfileNote::updatedAt).reversed())
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(profileNoteKey(authorId, subjectId)) != null;
        }

        public void clear() {
            users.clear();
            profileNotes.clear();
        }

        public int size() {
            return users.size();
        }
    }

    public static class Interactions implements InteractionStorage {
        private final Map<UUID, Like> likes = new HashMap<>();
        private final Map<String, Match> matches = new HashMap<>();

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return likes.values().stream()
                    .filter(like -> like.whoLikes().equals(fromUserId))
                    .filter(like -> like.whoGotLiked().equals(toUserId))
                    .findFirst();
        }

        @Override
        public void save(Like like) {
            likes.put(like.id(), like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return getLike(from, to).isPresent();
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            boolean forward = likes.values().stream()
                    .anyMatch(like -> like.whoLikes().equals(a)
                            && like.whoGotLiked().equals(b)
                            && like.direction() == Like.Direction.LIKE);
            boolean backward = likes.values().stream()
                    .anyMatch(like -> like.whoLikes().equals(b)
                            && like.whoGotLiked().equals(a)
                            && like.direction() == Like.Direction.LIKE);
            return forward && backward;
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return likes.values().stream()
                    .filter(like -> like.whoLikes().equals(userId))
                    .map(Like::whoGotLiked)
                    .collect(Collectors.toSet());
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return likes.values().stream()
                    .filter(like -> like.whoGotLiked().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.LIKE)
                    .map(Like::whoLikes)
                    .collect(Collectors.toSet());
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            return likes.values().stream()
                    .filter(like -> like.whoGotLiked().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.LIKE)
                    .sorted(Comparator.comparing(Like::createdAt))
                    .map(like -> Map.entry(like.whoLikes(), like.createdAt()))
                    .toList();
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(like -> like.whoLikes().equals(userId))
                    .filter(like -> like.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(like -> like.whoGotLiked().equals(userId))
                    .filter(like -> like.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            Set<UUID> likedByUser = likes.values().stream()
                    .filter(like -> like.whoLikes().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.LIKE)
                    .map(Like::whoGotLiked)
                    .collect(Collectors.toSet());
            return (int) likes.values().stream()
                    .filter(like -> like.whoGotLiked().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.LIKE)
                    .map(Like::whoLikes)
                    .filter(likedByUser::contains)
                    .distinct()
                    .count();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(like -> like.whoLikes().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.LIKE)
                    .filter(like -> !like.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(like -> like.whoLikes().equals(userId))
                    .filter(like -> like.direction() == Like.Direction.PASS)
                    .filter(like -> !like.createdAt().isBefore(startOfDay))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.remove(likeId);
        }

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
            Match match = matches.get(matchId);
            if (match == null || match.getDeletedAt() != null) {
                return Optional.empty();
            }
            return Optional.of(match);
        }

        @Override
        public boolean exists(String matchId) {
            Match match = matches.get(matchId);
            return match != null && match.getDeletedAt() == null;
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(match -> match.getDeletedAt() == null)
                    .filter(match -> match.getState() == MatchState.ACTIVE)
                    .filter(match -> match.involves(userId))
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(match -> match.getDeletedAt() == null)
                    .filter(match -> match.involves(userId))
                    .toList();
        }

        @Override
        public void delete(String matchId) {
            Match match = matches.get(matchId);
            if (match != null) {
                match.restoreDeletedAt(AppClock.now());
            }
        }

        @Override
        public int purgeDeletedBefore(Instant threshold) {
            int before = matches.size();
            matches.entrySet().removeIf(entry -> {
                Instant deletedAt = entry.getValue().getDeletedAt();
                return deletedAt != null && deletedAt.isBefore(threshold);
            });
            return before - matches.size();
        }

        @Override
        public boolean atomicUndoDelete(UUID likeId, String matchId) {
            Like removed = likes.remove(likeId);
            if (removed == null) {
                return false;
            }
            if (matchId != null) {
                matches.remove(matchId);
            }
            return true;
        }

        public void clear() {
            likes.clear();
            matches.clear();
        }

        public int likeSize() {
            return likes.size();
        }

        public int matchSize() {
            return matches.size();
        }

        public int size() {
            return likes.size() + matches.size();
        }
    }

    public static class Communications implements CommunicationStorage {
        private final Map<String, Conversation> conversations = new HashMap<>();
        private final Map<String, List<Message>> messagesByConversation = new HashMap<>();
        private final Map<UUID, FriendRequest> friendRequests = new HashMap<>();
        private final Map<UUID, Notification> notifications = new HashMap<>();

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
            return getConversation(Conversation.generateId(userA, userB));
        }

        @Override
        public List<Conversation> getConversationsFor(UUID userId) {
            return conversations.values().stream()
                    .filter(conversation -> conversation.involves(userId))
                    .sorted(Comparator.comparing((Conversation conversation) -> {
                                Instant last = conversation.getLastMessageAt();
                                return last != null ? last : conversation.getCreatedAt();
                            })
                            .reversed())
                    .toList();
        }

        @Override
        public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation != null) {
                conversation.updateLastMessageAt(timestamp);
            }
        }

        @Override
        public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation != null && conversation.involves(userId)) {
                conversation.updateReadTimestamp(userId, timestamp);
            }
        }

        @Override
        public void archiveConversation(String conversationId, MatchArchiveReason reason) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation != null) {
                conversation.archive(reason);
            }
        }

        @Override
        public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
            Conversation conversation = conversations.get(conversationId);
            if (conversation != null && conversation.involves(userId)) {
                conversation.setVisibility(userId, visible);
            }
        }

        @Override
        public void deleteConversation(String conversationId) {
            conversations.remove(conversationId);
            messagesByConversation.remove(conversationId);
        }

        @Override
        public void saveMessage(Message message) {
            messagesByConversation
                    .computeIfAbsent(message.conversationId(), ignored -> new ArrayList<>())
                    .add(message);
        }

        @Override
        public List<Message> getMessages(String conversationId, int limit, int offset) {
            if (limit <= 0 || offset < 0) {
                return List.of();
            }
            List<Message> all = messagesByConversation.getOrDefault(conversationId, List.of()).stream()
                    .sorted(Comparator.comparing(Message::createdAt))
                    .toList();
            if (offset >= all.size()) {
                return List.of();
            }
            int end = Math.min(all.size(), offset + limit);
            return new ArrayList<>(all.subList(offset, end));
        }

        @Override
        public Optional<Message> getLatestMessage(String conversationId) {
            return messagesByConversation.getOrDefault(conversationId, List.of()).stream()
                    .max(Comparator.comparing(Message::createdAt));
        }

        @Override
        public int countMessages(String conversationId) {
            return messagesByConversation
                    .getOrDefault(conversationId, List.of())
                    .size();
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            return (int) messagesByConversation.getOrDefault(conversationId, List.of()).stream()
                    .filter(message -> message.createdAt().isAfter(after))
                    .count();
        }

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            return (int) messagesByConversation.getOrDefault(conversationId, List.of()).stream()
                    .filter(message -> !message.senderId().equals(senderId))
                    .count();
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
            return (int) messagesByConversation.getOrDefault(conversationId, List.of()).stream()
                    .filter(message -> message.createdAt().isAfter(after))
                    .filter(message -> !message.senderId().equals(excludeSenderId))
                    .count();
        }

        @Override
        public void deleteMessagesByConversation(String conversationId) {
            messagesByConversation.remove(conversationId);
        }

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
                    .filter(request -> request.status() == FriendRequest.Status.PENDING)
                    .filter(request -> (request.fromUserId().equals(user1)
                                    && request.toUserId().equals(user2))
                            || (request.fromUserId().equals(user2)
                                    && request.toUserId().equals(user1)))
                    .findFirst();
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
            return friendRequests.values().stream()
                    .filter(request -> request.status() == FriendRequest.Status.PENDING)
                    .filter(request -> request.toUserId().equals(userId))
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
            Notification existing = notifications.get(id);
            if (existing == null || existing.isRead()) {
                return;
            }
            Notification updated = new Notification(
                    existing.id(),
                    existing.userId(),
                    existing.type(),
                    existing.title(),
                    existing.message(),
                    existing.createdAt(),
                    true,
                    existing.data());
            notifications.put(id, updated);
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return notifications.values().stream()
                    .filter(notification -> notification.userId().equals(userId))
                    .filter(notification -> !unreadOnly || !notification.isRead())
                    .sorted(Comparator.comparing(Notification::createdAt).reversed())
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
            notifications
                    .values()
                    .removeIf(notification -> notification.createdAt().isBefore(before));
        }
    }

    public static class Analytics implements AnalyticsStorage {
        private final Map<UUID, List<UserStats>> userStatsHistory = new HashMap<>();
        private final List<PlatformStats> platformStatsHistory = new ArrayList<>();
        private final List<ProfileViewEvent> profileViews = new ArrayList<>();
        private final Map<UUID, List<UserAchievement>> achievements = new HashMap<>();
        private final Set<String> dailyPickViews = new HashSet<>();
        private final Map<UUID, Session> sessions = new HashMap<>();

        @Override
        public void saveUserStats(UserStats stats) {
            userStatsHistory
                    .computeIfAbsent(stats.userId(), ignored -> new ArrayList<>())
                    .add(stats);
        }

        @Override
        public Optional<UserStats> getLatestUserStats(UUID userId) {
            return userStatsHistory.getOrDefault(userId, List.of()).stream()
                    .max(Comparator.comparing(UserStats::computedAt));
        }

        @Override
        public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
            if (limit <= 0) {
                return List.of();
            }
            return userStatsHistory.getOrDefault(userId, List.of()).stream()
                    .sorted(Comparator.comparing(UserStats::computedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<UserStats> getAllLatestUserStats() {
            return userStatsHistory.values().stream()
                    .map(list -> list.stream().max(Comparator.comparing(UserStats::computedAt)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        @Override
        public int deleteUserStatsOlderThan(Instant cutoff) {
            int removed = 0;
            for (List<UserStats> stats : userStatsHistory.values()) {
                int before = stats.size();
                stats.removeIf(item -> item.computedAt().isBefore(cutoff));
                removed += before - stats.size();
            }
            return removed;
        }

        @Override
        public void savePlatformStats(PlatformStats stats) {
            platformStatsHistory.add(stats);
        }

        @Override
        public Optional<PlatformStats> getLatestPlatformStats() {
            return platformStatsHistory.stream().max(Comparator.comparing(PlatformStats::computedAt));
        }

        @Override
        public List<PlatformStats> getPlatformStatsHistory(int limit) {
            if (limit <= 0) {
                return List.of();
            }
            return platformStatsHistory.stream()
                    .sorted(Comparator.comparing(PlatformStats::computedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void recordProfileView(UUID viewerId, UUID viewedId) {
            if (viewerId.equals(viewedId)) {
                return;
            }
            profileViews.add(new ProfileViewEvent(viewerId, viewedId, AppClock.now()));
        }

        @Override
        public int getProfileViewCount(UUID userId) {
            return (int) profileViews.stream()
                    .filter(event -> event.viewedId().equals(userId))
                    .count();
        }

        @Override
        public int getUniqueViewerCount(UUID userId) {
            return (int) profileViews.stream()
                    .filter(event -> event.viewedId().equals(userId))
                    .map(ProfileViewEvent::viewerId)
                    .distinct()
                    .count();
        }

        @Override
        public List<UUID> getRecentViewers(UUID userId, int limit) {
            if (limit <= 0) {
                return List.of();
            }
            Map<UUID, Instant> newestViewByViewer = new HashMap<>();
            for (ProfileViewEvent event : profileViews) {
                if (!event.viewedId().equals(userId)) {
                    continue;
                }
                newestViewByViewer.merge(
                        event.viewerId(),
                        event.viewedAt(),
                        (current, candidate) -> candidate.isAfter(current) ? candidate : current);
            }
            return newestViewByViewer.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Instant>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
            return profileViews.stream()
                    .anyMatch(event -> event.viewerId().equals(viewerId)
                            && event.viewedId().equals(viewedId));
        }

        @Override
        public void saveUserAchievement(UserAchievement achievement) {
            List<UserAchievement> unlocked =
                    achievements.computeIfAbsent(achievement.userId(), ignored -> new ArrayList<>());
            unlocked.removeIf(existing -> existing.achievement() == achievement.achievement());
            unlocked.add(achievement);
        }

        @Override
        public List<UserAchievement> getUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, List.of()).stream()
                    .sorted(Comparator.comparing(UserAchievement::unlockedAt).reversed())
                    .toList();
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            return achievements.getOrDefault(userId, List.of()).stream()
                    .anyMatch(unlocked -> unlocked.achievement() == achievement);
        }

        @Override
        public int countUnlockedAchievements(UUID userId) {
            return achievements.getOrDefault(userId, List.of()).size();
        }

        @Override
        public void markDailyPickAsViewed(UUID userId, LocalDate date) {
            dailyPickViews.add(dailyPickKey(userId, date));
        }

        @Override
        public boolean isDailyPickViewed(UUID userId, LocalDate date) {
            return dailyPickViews.contains(dailyPickKey(userId, date));
        }

        @Override
        public int deleteDailyPickViewsOlderThan(LocalDate before) {
            int beforeCount = dailyPickViews.size();
            dailyPickViews.removeIf(key -> {
                String[] parts = key.split("\\Q" + DAILY_PICK_DELIMITER + "\\E");
                if (parts.length != 2) {
                    return false;
                }
                LocalDate viewedDate = LocalDate.parse(parts[1]);
                return viewedDate.isBefore(before);
            });
            return beforeCount - dailyPickViews.size();
        }

        @Override
        public int deleteExpiredDailyPickViews(Instant cutoff) {
            return deleteDailyPickViewsOlderThan(
                    cutoff.atZone(AppConfig.defaults().userTimeZone()).toLocalDate());
        }

        @Override
        public void saveSession(Session session) {
            sessions.put(session.getId(), session);
        }

        @Override
        public Optional<Session> getSession(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<Session> getActiveSession(UUID userId) {
            return sessions.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .filter(Session::isActive)
                    .max(Comparator.comparing(Session::getStartedAt));
        }

        @Override
        public List<Session> getSessionsFor(UUID userId, int limit) {
            if (limit <= 0) {
                return List.of();
            }
            return sessions.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .sorted(Comparator.comparing(Session::getStartedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Session> getSessionsInRange(UUID userId, Instant start, Instant end) {
            return sessions.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .filter(session -> !session.getStartedAt().isBefore(start))
                    .filter(session -> !session.getStartedAt().isAfter(end))
                    .sorted(Comparator.comparing(Session::getStartedAt).reversed())
                    .toList();
        }

        @Override
        public SessionAggregates getSessionAggregates(UUID userId) {
            List<Session> userSessions = sessions.values().stream()
                    .filter(session -> session.getUserId().equals(userId))
                    .toList();
            if (userSessions.isEmpty()) {
                return SessionAggregates.empty();
            }

            int totalSessions = userSessions.size();
            int totalSwipes =
                    userSessions.stream().mapToInt(Session::getSwipeCount).sum();
            int totalLikes =
                    userSessions.stream().mapToInt(Session::getLikeCount).sum();
            int totalPasses =
                    userSessions.stream().mapToInt(Session::getPassCount).sum();
            int totalMatches =
                    userSessions.stream().mapToInt(Session::getMatchCount).sum();
            double totalDurationSeconds =
                    userSessions.stream().mapToLong(Session::getDurationSeconds).sum();
            double avgSessionDurationSeconds = totalDurationSeconds / totalSessions;
            double avgSwipesPerSession = totalSwipes / (double) totalSessions;
            double avgSwipeVelocity = totalDurationSeconds == 0.0 ? 0.0 : totalSwipes / totalDurationSeconds;

            return new SessionAggregates(
                    totalSessions,
                    totalSwipes,
                    totalLikes,
                    totalPasses,
                    totalMatches,
                    avgSessionDurationSeconds,
                    avgSwipesPerSession,
                    avgSwipeVelocity);
        }

        @Override
        public int endStaleSessions(Duration timeout) {
            int ended = 0;
            for (Session session : sessions.values()) {
                if (session.isActive() && session.isTimedOut(timeout)) {
                    session.end();
                    ended++;
                }
            }
            return ended;
        }

        @Override
        public int deleteExpiredSessions(Instant cutoff) {
            int before = sessions.size();
            sessions.values().removeIf(session -> session.getStartedAt().isBefore(cutoff));
            return before - sessions.size();
        }

        private record ProfileViewEvent(UUID viewerId, UUID viewedId, Instant viewedAt) {}
    }

    public static class TrustSafety implements TrustSafetyStorage {
        private final Map<UUID, Block> blocks = new HashMap<>();
        private final Map<UUID, Report> reports = new HashMap<>();

        @Override
        public void save(Block block) {
            blocks.put(block.id(), block);
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.values().stream()
                    .anyMatch(block -> (block.blockerId().equals(userA)
                                    && block.blockedId().equals(userB))
                            || (block.blockerId().equals(userB)
                                    && block.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            return blocks.values().stream()
                    .filter(block -> block.blockerId().equals(userId))
                    .map(Block::blockedId)
                    .collect(Collectors.toSet());
        }

        @Override
        public List<Block> findByBlocker(UUID blockerId) {
            return blocks.values().stream()
                    .filter(block -> block.blockerId().equals(blockerId))
                    .toList();
        }

        @Override
        public boolean deleteBlock(UUID blockerId, UUID blockedId) {
            List<UUID> toDelete = blocks.values().stream()
                    .filter(block -> block.blockerId().equals(blockerId))
                    .filter(block -> block.blockedId().equals(blockedId))
                    .map(Block::id)
                    .toList();
            toDelete.forEach(blocks::remove);
            return !toDelete.isEmpty();
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int) blocks.values().stream()
                    .filter(block -> block.blockerId().equals(userId))
                    .count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int) blocks.values().stream()
                    .filter(block -> block.blockedId().equals(userId))
                    .count();
        }

        @Override
        public void save(Report report) {
            reports.put(report.id(), report);
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return (int) reports.values().stream()
                    .filter(report -> report.reportedUserId().equals(userId))
                    .count();
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return reports.values().stream()
                    .anyMatch(report -> report.reporterId().equals(reporterId)
                            && report.reportedUserId().equals(reportedUserId));
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return reports.values().stream()
                    .filter(report -> report.reportedUserId().equals(userId))
                    .toList();
        }

        @Override
        public int countReportsBy(UUID userId) {
            return (int) reports.values().stream()
                    .filter(report -> report.reporterId().equals(userId))
                    .count();
        }
    }

    public static class Undos implements Undo.Storage {
        private final Map<UUID, Undo> byUser = new HashMap<>();

        @Override
        public void save(Undo state) {
            byUser.put(state.userId(), state);
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(byUser.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return byUser.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            int before = byUser.size();
            byUser.values().removeIf(state -> state.expiresAt().isBefore(now));
            return before - byUser.size();
        }

        @Override
        public List<Undo> findAll() {
            return new ArrayList<>(byUser.values());
        }
    }

    public static class Standouts implements Standout.Storage {
        private final Map<String, List<Standout>> bySeekerAndDate = new HashMap<>();
        private final Clock clock;

        public Standouts() {
            this(Clock.systemUTC());
        }

        public Standouts(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        }

        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            bySeekerAndDate.put(standoutKey(seekerId, date), new ArrayList<>(standouts));
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return new ArrayList<>(bySeekerAndDate.getOrDefault(standoutKey(seekerId, date), List.of()));
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            String key = standoutKey(seekerId, date);
            List<Standout> existing = bySeekerAndDate.getOrDefault(key, List.of());
            List<Standout> updated = existing.stream()
                    .map(standout -> standout.standoutUserId().equals(standoutUserId)
                            ? standout.withInteraction(Instant.now(clock))
                            : standout)
                    .toList();
            bySeekerAndDate.put(key, new ArrayList<>(updated));
        }

        @Override
        public int cleanup(LocalDate before) {
            int prior = bySeekerAndDate.size();
            bySeekerAndDate.keySet().removeIf(key -> {
                String[] parts = key.split("\\Q" + STANDOUT_DELIMITER + "\\E");
                if (parts.length != 2) {
                    return false;
                }
                LocalDate date = LocalDate.parse(parts[1]);
                return date.isBefore(before);
            });
            return prior - bySeekerAndDate.size();
        }
    }
}
