package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.FriendRequest.Status;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Notification.Type;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.OperationalCommunicationStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Consolidated JDBI storage for conversations, messages, friend requests, and
 * notifications.
 */
public final class JdbiConnectionStorage implements OperationalCommunicationStorage {

    private static final String ERR_CONVERSATION_IDS_NULL = "conversationIds cannot be null";
    private static final String ERR_USER_ID_NULL = "userId cannot be null";
    private static final String PARAM_CONVERSATION_IDS = "conversationIds";
    private static final String COLUMN_CONVERSATION_ID = "conversation_id";
    private static final String SQL_LATEST_MESSAGES_BY_CONVERSATION_IDS = """
                        SELECT ranked.id, ranked.conversation_id, ranked.sender_id, ranked.content, ranked.created_at
                        FROM (
                                SELECT m.id, m.conversation_id, m.sender_id, m.content, m.created_at,
                                             ROW_NUMBER() OVER (
                                                     PARTITION BY m.conversation_id
                                                     ORDER BY m.created_at DESC, m.id DESC
                                             ) AS rn
                                FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                                WHERE m.conversation_id IN (<conversationIds>)
                                    AND m.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                        ) ranked
                        WHERE ranked.rn = 1
                        """;
    private static final String SQL_UNREAD_COUNTS_BY_CONVERSATION_IDS = """
                        SELECT c.id AS conversation_id, COUNT(m.id) AS unread_count
                        FROM conversations c
                        LEFT JOIN messages m
                            ON m.conversation_id = c.id
                         AND m.deleted_at IS NULL
                         AND m.sender_id <> :userId
                         AND (
                                 CASE
                                         WHEN c.user_a = :userId THEN c.user_a_last_read_at
                                         WHEN c.user_b = :userId THEN c.user_b_last_read_at
                                         ELSE NULL
                                 END IS NULL
                                 OR m.created_at > CASE
                                         WHEN c.user_a = :userId THEN c.user_a_last_read_at
                                         WHEN c.user_b = :userId THEN c.user_b_last_read_at
                                         ELSE NULL
                                 END
                         )
                        WHERE c.id IN (<conversationIds>)
                            AND c.deleted_at IS NULL
                            AND (
                                (c.user_a = :userId AND c.visible_to_user_a = TRUE) OR
                                (c.user_b = :userId AND c.visible_to_user_b = TRUE)
                            )
                        GROUP BY c.id
                        """;
    private static final String SQL_VISIBLE_CONVERSATIONS_FOR_USER = """
                        SELECT id, user_a, user_b, created_at, last_message_at,
                            user_a_last_read_at, user_b_last_read_at,
                            archived_at_a AS user_a_archived_at, archive_reason_a AS user_a_archive_reason,
                            archived_at_b AS user_b_archived_at, archive_reason_b AS user_b_archive_reason,
                            visible_to_user_a, visible_to_user_b,
                            COALESCE(last_message_at, created_at) AS _sort_time
                        FROM conversations
                        WHERE user_a = :userId
                          AND visible_to_user_a = TRUE
                          AND deleted_at IS NULL

                        UNION ALL

                        SELECT id, user_a, user_b, created_at, last_message_at,
                            user_a_last_read_at, user_b_last_read_at,
                            archived_at_a AS user_a_archived_at, archive_reason_a AS user_a_archive_reason,
                            archived_at_b AS user_b_archived_at, archive_reason_b AS user_b_archive_reason,
                            visible_to_user_a, visible_to_user_b,
                            COALESCE(last_message_at, created_at) AS _sort_time
                        FROM conversations
                        WHERE user_b = :userId
                          AND visible_to_user_b = TRUE
                          AND deleted_at IS NULL

                        ORDER BY _sort_time DESC, id DESC
                        """;

    private final Jdbi jdbi;
    private final MessagingDao messagingDao;
    private final SocialDao socialDao;

    public JdbiConnectionStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.messagingDao = jdbi.onDemand(MessagingDao.class);
        this.socialDao = jdbi.onDemand(SocialDao.class);
    }

    @Override
    public void saveConversation(Conversation conversation) {
        messagingDao.saveConversation(conversation);
    }

    @Override
    public Optional<Conversation> getConversation(String conversationId) {
        return messagingDao.getConversation(conversationId);
    }

    @Override
    public Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
        return messagingDao.getConversationByUsers(userA, userB);
    }

    @Override
    public List<Conversation> getConversationsFor(UUID userId, int limit, int offset) {
        return messagingDao.getConversationsFor(userId, limit, offset);
    }

    @Override
    public List<Conversation> getAllConversationsFor(UUID userId) {
        return messagingDao.getAllConversationsFor(userId);
    }

    @Override
    public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
        messagingDao.updateConversationLastMessageAt(conversationId, timestamp);
    }

    @Override
    public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
        messagingDao.updateConversationReadTimestamp(conversationId, userId, timestamp);
    }

    @Override
    public void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason) {
        messagingDao.archiveConversation(conversationId, userId, reason);
    }

    @Override
    public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
        messagingDao.setConversationVisibility(conversationId, userId, visible);
    }

    @Override
    public void deleteConversation(String conversationId) {
        messagingDao.deleteConversation(conversationId);
    }

    @Override
    public void saveMessage(Message message) {
        messagingDao.saveMessage(message);
    }

    @Override
    public void saveMessageAndUpdateConversationLastMessageAt(Message message) {
        Objects.requireNonNull(message, "message cannot be null");

        jdbi.useTransaction(handle -> {
            MessagingDao transactionalDao = handle.attach(MessagingDao.class);
            transactionalDao.saveMessage(message);
            transactionalDao.updateConversationLastMessageAt(message.conversationId(), message.createdAt());
        });
    }

    @Override
    public List<Message> getMessages(String conversationId, int limit, int offset) {
        return messagingDao.getMessages(conversationId, limit, offset);
    }

    @Override
    public Optional<Message> getMessage(UUID messageId) {
        return messagingDao.getMessage(messageId);
    }

    @Override
    public Optional<Message> getLatestMessage(String conversationId) {
        return messagingDao.getLatestMessage(conversationId);
    }

    @Override
    public Map<String, Optional<Message>> getLatestMessagesByConversationIds(Set<String> conversationIds) {
        Objects.requireNonNull(conversationIds, ERR_CONVERSATION_IDS_NULL);
        if (conversationIds.isEmpty()) {
            return Map.of();
        }

        return mapConversationBatchResults(
                conversationIds,
                handle -> handle
                        .createQuery(SQL_LATEST_MESSAGES_BY_CONVERSATION_IDS)
                        .bindList(PARAM_CONVERSATION_IDS, conversationIds)
                        .map(new MessageMapper())
                        .list()
                        .stream()
                        .map(message -> Map.entry(message.conversationId(), Optional.of(message)))
                        .toList(),
                ignored -> Optional.<Message>empty());
    }

    @Override
    public int countMessages(String conversationId) {
        return messagingDao.countMessages(conversationId);
    }

    @Override
    public Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
        Objects.requireNonNull(conversationIds, ERR_CONVERSATION_IDS_NULL);
        if (conversationIds.isEmpty()) {
            return Map.of();
        }

        return mapConversationBatchResults(
                conversationIds,
                handle -> handle.createQuery("SELECT m.conversation_id, COUNT(*) AS message_count FROM messages m "
                                + "JOIN conversations c ON c.id = m.conversation_id "
                                + "WHERE m.conversation_id IN (<conversationIds>) "
                                + "AND m.deleted_at IS NULL "
                                + "AND c.deleted_at IS NULL "
                                + "AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE) "
                                + "GROUP BY m.conversation_id")
                        .bindList(PARAM_CONVERSATION_IDS, conversationIds)
                        .map((rs, ctx) -> Map.entry(rs.getString(COLUMN_CONVERSATION_ID), rs.getInt("message_count")))
                        .list(),
                ignored -> 0);
    }

    @Override
    public int countMessagesAfter(String conversationId, Instant after) {
        return messagingDao.countMessagesAfter(conversationId, after);
    }

    @Override
    public int countMessagesNotFromSender(String conversationId, UUID senderId) {
        return messagingDao.countMessagesNotFromSender(conversationId, senderId);
    }

    @Override
    public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
        return messagingDao.countMessagesAfterNotFrom(conversationId, after, excludeSenderId);
    }

    @Override
    public Map<String, Integer> countUnreadMessagesByConversationIds(UUID userId, Set<String> conversationIds) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        Objects.requireNonNull(conversationIds, ERR_CONVERSATION_IDS_NULL);
        if (conversationIds.isEmpty()) {
            return Map.of();
        }

        return mapConversationBatchResults(
                conversationIds,
                handle -> handle.createQuery(SQL_UNREAD_COUNTS_BY_CONVERSATION_IDS)
                        .bind("userId", userId)
                        .bindList(PARAM_CONVERSATION_IDS, conversationIds)
                        .map((rs, ctx) -> Map.entry(rs.getString(COLUMN_CONVERSATION_ID), rs.getInt("unread_count")))
                        .list(),
                ignored -> 0);
    }

    @Override
    public void deleteMessage(UUID messageId) {
        messagingDao.deleteMessage(messageId, AppClock.now());
    }

    @Override
    public void deleteMessagesByConversation(String conversationId) {
        messagingDao.deleteMessagesByConversation(conversationId, AppClock.now());
    }

    @Override
    public void deleteConversationWithMessages(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId cannot be null");

        jdbi.useTransaction(handle -> {
            MessagingDao transactionalDao = handle.attach(MessagingDao.class);
            transactionalDao.deleteConversation(conversationId);
            transactionalDao.deleteMessagesByConversation(conversationId, AppClock.now());
        });
    }

    @Override
    public void saveFriendRequest(FriendRequest request) {
        socialDao.saveFriendRequest(request);
    }

    @Override
    public void saveFriendRequestWithNotification(FriendRequest request, Notification notification) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(notification, "notification cannot be null");

        jdbi.useTransaction(handle -> {
            SocialDao transactionalDao = handle.attach(SocialDao.class);
            transactionalDao.saveFriendRequest(request);
            transactionalDao.saveNotification(notification);
        });
    }

    @Override
    public void updateFriendRequest(FriendRequest request) {
        socialDao.updateFriendRequest(request);
    }

    @Override
    public Optional<FriendRequest> getFriendRequest(UUID id) {
        return socialDao.getFriendRequest(id);
    }

    @Override
    public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
        return socialDao.getPendingFriendRequestBetween(user1, user2);
    }

    @Override
    public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
        return socialDao.getPendingFriendRequestsForUser(userId);
    }

    @Override
    public int countPendingFriendRequestsForUser(UUID userId) {
        return socialDao.countPendingFriendRequestsForUser(userId);
    }

    @Override
    public void deleteFriendRequest(UUID id) {
        socialDao.deleteFriendRequest(id);
    }

    @Override
    public void saveNotification(Notification notification) {
        socialDao.saveNotification(notification);
    }

    @Override
    public int markAllNotificationsAsRead(UUID userId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);

        return jdbi.inTransaction(handle -> handle.attach(SocialDao.class).markAllNotificationsAsRead(userId));
    }

    @Override
    public void markNotificationAsRead(UUID userId, UUID id) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        Objects.requireNonNull(id, "id cannot be null");
        socialDao.markNotificationAsRead(userId, id);
    }

    @Override
    public CommunicationStorage.MarkNotificationReadResult markNotificationAsReadChecked(
            UUID userId, UUID notificationId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        Objects.requireNonNull(notificationId, "notificationId cannot be null");
        return jdbi.inTransaction(handle -> {
            var dao = handle.attach(SocialDao.class);
            var ownerOpt = dao.getNotificationOwnerId(notificationId);
            if (ownerOpt.isEmpty()) {
                return CommunicationStorage.MarkNotificationReadResult.NOT_FOUND;
            }
            if (!ownerOpt.get().equals(userId)) {
                return CommunicationStorage.MarkNotificationReadResult.NOT_OWNED;
            }
            dao.markNotificationAsRead(userId, notificationId);
            return CommunicationStorage.MarkNotificationReadResult.UPDATED;
        });
    }

    @Override
    public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
        return socialDao.getNotificationsForUser(userId, unreadOnly);
    }

    @Override
    public Optional<Notification> getNotification(UUID id) {
        return socialDao.getNotification(id);
    }

    @Override
    public int deleteNotificationsForUser(UUID userId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);

        return jdbi.inTransaction(handle -> handle.attach(SocialDao.class).deleteNotificationsForUser(userId));
    }

    @Override
    public void deleteNotification(UUID userId, UUID id) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        Objects.requireNonNull(id, "id cannot be null");
        socialDao.deleteNotification(userId, id);
    }

    @Override
    public void deleteOldNotifications(Instant before) {
        socialDao.deleteOldNotifications(before);
    }

    private <T> Map<String, T> mapConversationBatchResults(
            Set<String> conversationIds,
            Function<org.jdbi.v3.core.Handle, List<Map.Entry<String, T>>> fetchEntries,
            Function<String, T> defaultValueFactory) {
        return jdbi.withHandle(handle -> {
            Map<String, T> results = new java.util.HashMap<>();
            conversationIds.forEach(id -> results.put(id, defaultValueFactory.apply(id)));
            fetchEntries.apply(handle).forEach(entry -> results.put(entry.getKey(), entry.getValue()));
            return Map.copyOf(results);
        });
    }

    @RegisterRowMapper(ConversationMapper.class)
    @RegisterRowMapper(MessageMapper.class)
    private interface MessagingDao {

        @SqlUpdate("""
                INSERT INTO conversations (id, user_a, user_b, created_at, last_message_at,
                                           user_a_last_read_at, user_b_last_read_at,
                                           archived_at_a, archive_reason_a, archived_at_b, archive_reason_b,
                                           visible_to_user_a, visible_to_user_b)
                VALUES (:id, :userA, :userB, :createdAt, :lastMessageAt,
                        :userAReadAt, :userBReadAt,
                        :userAArchivedAt, :userAArchiveReason, :userBArchivedAt, :userBArchiveReason,
                        :visibleToUserA, :visibleToUserB)
                """)
        void saveConversation(@BindBean Conversation conversation);

        @SqlQuery("""
                SELECT id, user_a, user_b, created_at, last_message_at,
                    user_a_last_read_at, user_b_last_read_at,
                    archived_at_a AS user_a_archived_at, archive_reason_a AS user_a_archive_reason,
                    archived_at_b AS user_b_archived_at, archive_reason_b AS user_b_archive_reason,
                    visible_to_user_a, visible_to_user_b
                FROM conversations
                WHERE id = :conversationId AND deleted_at IS NULL
                """)
        Optional<Conversation> getConversation(@Bind("conversationId") String conversationId);

        default Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
            String conversationId = Conversation.generateId(userA, userB);
            return getConversation(conversationId);
        }

        @SqlQuery(SQL_VISIBLE_CONVERSATIONS_FOR_USER + " LIMIT :limit OFFSET :offset")
        List<Conversation> getConversationsFor(
                @Bind("userId") UUID userId, @Bind("limit") int limit, @Bind("offset") int offset);

        @SqlQuery(SQL_VISIBLE_CONVERSATIONS_FOR_USER)
        List<Conversation> getAllConversationsFor(@Bind("userId") UUID userId);

        @SqlUpdate(
                "UPDATE conversations SET last_message_at = :timestamp WHERE id = :conversationId AND deleted_at IS NULL")
        void updateConversationLastMessageAt(
                @Bind("conversationId") String conversationId, @Bind("timestamp") Instant timestamp);

        @SqlUpdate("""
                UPDATE conversations
                SET user_a_last_read_at = CASE WHEN user_a = :userId THEN :timestamp ELSE user_a_last_read_at END,
                    user_b_last_read_at = CASE WHEN user_b = :userId THEN :timestamp ELSE user_b_last_read_at END
                WHERE id = :conversationId AND (user_a = :userId OR user_b = :userId) AND deleted_at IS NULL
                """)
        void updateConversationReadTimestamp(
                @Bind("conversationId") String conversationId,
                @Bind("userId") UUID userId,
                @Bind("timestamp") Instant timestamp);

        default void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason) {
            archiveConversationInternal(conversationId, userId, AppClock.now(), reason);
        }

        @SqlUpdate("""
                UPDATE conversations
                SET archived_at_a = CASE WHEN user_a = :userId THEN :archivedAt ELSE archived_at_a END,
                    archive_reason_a = CASE WHEN user_a = :userId THEN :reason ELSE archive_reason_a END,
                    archived_at_b = CASE WHEN user_b = :userId THEN :archivedAt ELSE archived_at_b END,
                    archive_reason_b = CASE WHEN user_b = :userId THEN :reason ELSE archive_reason_b END
                WHERE id = :conversationId AND (user_a = :userId OR user_b = :userId) AND deleted_at IS NULL
                """)
        void archiveConversationInternal(
                @Bind("conversationId") String conversationId,
                @Bind("userId") UUID userId,
                @Bind("archivedAt") Instant archivedAt,
                @Bind("reason") MatchArchiveReason reason);

        @SqlUpdate("""
                UPDATE conversations
                SET visible_to_user_a = CASE WHEN user_a = :userId THEN :visible ELSE visible_to_user_a END,
                    visible_to_user_b = CASE WHEN user_b = :userId THEN :visible ELSE visible_to_user_b END
                WHERE id = :conversationId AND deleted_at IS NULL
                """)
        void setConversationVisibility(
                @Bind("conversationId") String conversationId,
                @Bind("userId") UUID userId,
                @Bind("visible") boolean visible);

        default void deleteConversation(String conversationId) {
            deleteConversationInternal(conversationId, AppClock.now());
        }

        @SqlUpdate("UPDATE conversations SET deleted_at = :now WHERE id = :conversationId AND deleted_at IS NULL")
        void deleteConversationInternal(@Bind("conversationId") String conversationId, @Bind("now") Instant now);

        @SqlUpdate("""
                INSERT INTO messages (id, conversation_id, sender_id, content, created_at)
                VALUES (:id, :conversationId, :senderId, :content, :createdAt)
                """)
        void saveMessage(@org.jdbi.v3.sqlobject.customizer.BindMethods Message message);

        @SqlQuery("""
                                SELECT m.id, m.conversation_id, m.sender_id, m.content, m.created_at
                                FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                        WHERE m.conversation_id = :conversationId
                                AND m.deleted_at IS NULL
                                AND c.deleted_at IS NULL
                                AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                ORDER BY m.created_at ASC, m.id ASC
                LIMIT :limit OFFSET :offset
                """)
        List<Message> getMessages(
                @Bind("conversationId") String conversationId, @Bind("limit") int limit, @Bind("offset") int offset);

        @SqlQuery("""
                        SELECT m.id, m.conversation_id, m.sender_id, m.content, m.created_at
                        FROM messages m
                        JOIN conversations c ON c.id = m.conversation_id
                        WHERE m.id = :messageId
                            AND m.deleted_at IS NULL
                            AND c.deleted_at IS NULL
                            AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
            """)
        Optional<Message> getMessage(@Bind("messageId") UUID messageId);

        @SqlQuery("""
                                SELECT m.id, m.conversation_id, m.sender_id, m.content, m.created_at
                                FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                        WHERE m.conversation_id = :conversationId
                                AND m.deleted_at IS NULL
                                AND c.deleted_at IS NULL
                                AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
                """)
        Optional<Message> getLatestMessage(@Bind("conversationId") String conversationId);

        @SqlQuery("""
                                SELECT COUNT(*) FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                                WHERE m.conversation_id = :conversationId
                                    AND m.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                """)
        int countMessages(@Bind("conversationId") String conversationId);

        @SqlQuery("""
                                SELECT COUNT(*) FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                                WHERE m.conversation_id = :conversationId
                                    AND m.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                    AND m.created_at > :after
                """)
        int countMessagesAfter(@Bind("conversationId") String conversationId, @Bind("after") Instant after);

        @SqlQuery("""
                                SELECT COUNT(*) FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                                WHERE m.conversation_id = :conversationId
                                    AND m.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                    AND m.sender_id != :senderId
                """)
        int countMessagesNotFromSender(@Bind("conversationId") String conversationId, @Bind("senderId") UUID senderId);

        @SqlQuery("""
                                SELECT COUNT(*) FROM messages m
                                JOIN conversations c ON c.id = m.conversation_id
                                WHERE m.conversation_id = :conversationId
                                    AND m.deleted_at IS NULL
                                    AND c.deleted_at IS NULL
                                    AND (c.visible_to_user_a = TRUE OR c.visible_to_user_b = TRUE)
                                    AND m.created_at > :after
                                    AND m.sender_id != :excludeSenderId
                """)
        int countMessagesAfterNotFrom(
                @Bind("conversationId") String conversationId,
                @Bind("after") Instant after,
                @Bind("excludeSenderId") UUID excludeSenderId);

        @SqlUpdate("UPDATE messages SET deleted_at = :now WHERE id = :messageId AND deleted_at IS NULL")
        void deleteMessage(@Bind("messageId") UUID messageId, @Bind("now") Instant now);

        @SqlUpdate(
                "UPDATE messages SET deleted_at = :now WHERE conversation_id = :conversationId AND deleted_at IS NULL")
        void deleteMessagesByConversation(@Bind("conversationId") String conversationId, @Bind("now") Instant now);
    }

    @RegisterRowMapper(FriendRequestMapper.class)
    @RegisterRowMapper(NotificationMapper.class)
    private interface SocialDao {

        @SqlUpdate("""
                INSERT INTO friend_requests (
                    id, from_user_id, to_user_id, created_at, status, responded_at, pair_key, pending_marker
                )
                VALUES (
                    :id, :fromUserId, :toUserId, :createdAt, :status, :respondedAt,
                    CASE
                        WHEN CAST(:fromUserId AS VARCHAR) <= CAST(:toUserId AS VARCHAR)
                            THEN CONCAT(CAST(:fromUserId AS VARCHAR), '|', CAST(:toUserId AS VARCHAR))
                        ELSE CONCAT(CAST(:toUserId AS VARCHAR), '|', CAST(:fromUserId AS VARCHAR))
                    END,
                    CASE WHEN :status = 'PENDING' THEN 'PENDING' ELSE NULL END
                )
                """)
        void saveFriendRequest(@org.jdbi.v3.sqlobject.customizer.BindMethods FriendRequest request);

        @SqlUpdate("""
                UPDATE friend_requests
                SET status = :status,
                    responded_at = :respondedAt,
                    pending_marker = CASE WHEN :status = 'PENDING' THEN 'PENDING' ELSE NULL END
                WHERE id = :id
                """)
        void updateFriendRequest(@org.jdbi.v3.sqlobject.customizer.BindMethods FriendRequest request);

        @SqlQuery("""
                SELECT id, from_user_id, to_user_id, created_at, status, responded_at
                FROM friend_requests WHERE id = :id
                """)
        Optional<FriendRequest> getFriendRequest(@Bind("id") UUID id);

        default Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
            return getPendingFriendRequestByPairKey(friendRequestPairKey(user1, user2));
        }

        @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE pair_key = :pairKey AND pending_marker = 'PENDING'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
        Optional<FriendRequest> getPendingFriendRequestByPairKey(@Bind("pairKey") String pairKey);

        @SqlQuery("""
                SELECT id, from_user_id, to_user_id, created_at, status, responded_at
                FROM friend_requests
                WHERE to_user_id = :userId AND status = 'PENDING'
                """)
        List<FriendRequest> getPendingFriendRequestsForUser(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT COUNT(*) FROM friend_requests
                WHERE to_user_id = :userId AND status = 'PENDING'
                """)
        int countPendingFriendRequestsForUser(@Bind("userId") UUID userId);

        @SqlUpdate("DELETE FROM friend_requests WHERE id = :id")
        void deleteFriendRequest(@Bind("id") UUID id);

        @SqlUpdate("""
                INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
                VALUES (:id, :userId, :type, :title, :message, :createdAt, :isRead, :dataJson)
                """)
        void saveNotificationInternal(
                @org.jdbi.v3.sqlobject.customizer.BindMethods Notification notification,
                @Bind("dataJson") String dataJson);

        default void saveNotification(Notification notification) {
            String dataJson = toJson(notification.data());
            saveNotificationInternal(notification, dataJson);
        }

        @SqlUpdate("UPDATE notifications SET is_read = TRUE WHERE id = :id AND user_id = :userId")
        void markNotificationAsRead(@Bind("userId") UUID userId, @Bind("id") UUID id);

        @SqlUpdate("UPDATE notifications SET is_read = TRUE WHERE user_id = :userId AND is_read = FALSE")
        int markAllNotificationsAsRead(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT id, user_id, type, title, message, created_at, is_read, data_json
                FROM notifications WHERE user_id = :userId
                ORDER BY created_at DESC
                """)
        List<Notification> getAllNotificationsForUser(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT id, user_id, type, title, message, created_at, is_read, data_json
                FROM notifications WHERE user_id = :userId AND is_read = FALSE
                ORDER BY created_at DESC
                """)
        List<Notification> getUnreadNotificationsForUser(@Bind("userId") UUID userId);

        default List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return unreadOnly ? getUnreadNotificationsForUser(userId) : getAllNotificationsForUser(userId);
        }

        @SqlQuery("SELECT id, user_id, type, title, message, created_at, is_read, data_json "
                + "FROM notifications WHERE id = :id")
        Optional<Notification> getNotification(@Bind("id") UUID id);

        @SqlQuery("SELECT user_id FROM notifications WHERE id = :id")
        Optional<UUID> getNotificationOwnerId(@Bind("id") UUID id);

        @SqlUpdate("DELETE FROM notifications WHERE user_id = :userId")
        int deleteNotificationsForUser(@Bind("userId") UUID userId);

        @SqlUpdate("DELETE FROM notifications WHERE id = :id AND user_id = :userId")
        void deleteNotification(@Bind("userId") UUID userId, @Bind("id") UUID id);

        @SqlUpdate("DELETE FROM notifications WHERE created_at < :before")
        void deleteOldNotifications(@Bind("before") Instant before);

        private static String friendRequestPairKey(UUID user1, UUID user2) {
            String first = user1.toString();
            String second = user2.toString();
            return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
        }

        static String toJson(Map<String, String> data) {
            return JdbiNotificationJson.write(data);
        }
    }

    public static class ConversationMapper implements RowMapper<Conversation> {
        private static final String COL_CREATED_AT = "created_at";

        @Override
        public Conversation map(ResultSet rs, StatementContext ctx) throws SQLException {
            String id = rs.getString("id");
            UUID userA = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_a");
            UUID userB = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_b");
            Instant createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, COL_CREATED_AT);
            Instant lastMessageAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "last_message_at");
            Instant userAReadAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_a_last_read_at");
            Instant userBReadAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_b_last_read_at");
            Instant userAArchivedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_a_archived_at");
            MatchArchiveReason userAArchiveReason =
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "user_a_archive_reason", MatchArchiveReason.class);
            Instant userBArchivedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_b_archived_at");
            MatchArchiveReason userBArchiveReason =
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "user_b_archive_reason", MatchArchiveReason.class);
            boolean visibleToUserA = rs.getBoolean("visible_to_user_a");
            boolean visibleToUserB = rs.getBoolean("visible_to_user_b");

            return Conversation.fromStorage(
                    id,
                    userA,
                    userB,
                    createdAt,
                    lastMessageAt,
                    userAReadAt,
                    userBReadAt,
                    userAArchivedAt,
                    userAArchiveReason,
                    userBArchivedAt,
                    userBArchiveReason,
                    visibleToUserA,
                    visibleToUserB);
        }
    }

    public static class MessageMapper implements RowMapper<Message> {
        @Override
        public Message map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var conversationId = rs.getString(COLUMN_CONVERSATION_ID);
            var senderId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "sender_id");
            var content = rs.getString("content");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, ConversationMapper.COL_CREATED_AT);
            return new Message(id, conversationId, senderId, content, createdAt);
        }
    }

    public static class FriendRequestMapper implements RowMapper<FriendRequest> {
        @Override
        public FriendRequest map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var fromUserId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "from_user_id");
            var toUserId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "to_user_id");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, ConversationMapper.COL_CREATED_AT);
            var status = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "status", Status.class);
            var respondedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "responded_at");

            return new FriendRequest(id, fromUserId, toUserId, createdAt, status, respondedAt);
        }
    }

    public static class NotificationMapper implements RowMapper<Notification> {
        @Override
        public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id");
            var type = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "type", Type.class);
            var title = rs.getString("title");
            var message = rs.getString("message");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, ConversationMapper.COL_CREATED_AT);
            var isRead = rs.getBoolean("is_read");
            var data = JdbiNotificationJson.read(rs.getString("data_json"));

            return new Notification(id, userId, type, title, message, createdAt, isRead, data);
        }
    }
}
