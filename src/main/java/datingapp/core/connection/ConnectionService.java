package datingapp.core.connection;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Consolidated messaging + relationship transition service. */
public class ConnectionService {

    private static final String SENDER_NOT_FOUND = "Sender not found or inactive";
    private static final String RECIPIENT_NOT_FOUND = "Recipient not found or inactive";
    private static final String NO_ACTIVE_MATCH = "Cannot message: no active match";
    private static final String EMPTY_MESSAGE = "Message cannot be empty";
    private static final String MESSAGE_TOO_LONG = "Message too long (max %d characters)";

    private static final AppConfig CONFIG = AppConfig.defaults();

    private final CommunicationStorage communicationStorage;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;

    public ConnectionService(InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        this(communicationStorage, interactionStorage, null);
    }

    public ConnectionService(
            CommunicationStorage communicationStorage, InteractionStorage interactionStorage, UserStorage userStorage) {
        this.communicationStorage = Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.userStorage = userStorage;
    }

    private void ensureMessagingDependencies() {
        if (userStorage == null) {
            throw new IllegalStateException("Messaging dependencies are not configured");
        }
    }

    public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {
        ensureMessagingDependencies();
        User sender = userStorage.get(senderId);
        if (sender == null || sender.getState() != User.UserState.ACTIVE) {
            return SendResult.failure(SENDER_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
        }

        User recipient = userStorage.get(recipientId);
        if (recipient == null || recipient.getState() != User.UserState.ACTIVE) {
            return SendResult.failure(RECIPIENT_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
        }

        String matchId = Match.generateId(senderId, recipientId);
        Optional<Match> matchOpt = interactionStorage.get(matchId);
        if (matchOpt.isEmpty() || !matchOpt.get().canMessage()) {
            return SendResult.failure(NO_ACTIVE_MATCH, SendResult.ErrorCode.NO_ACTIVE_MATCH);
        }

        if (content == null || content.isBlank()) {
            return SendResult.failure(EMPTY_MESSAGE, SendResult.ErrorCode.EMPTY_MESSAGE);
        }
        content = content.trim();
        if (content.length() > Message.MAX_LENGTH) {
            return SendResult.failure(
                    MESSAGE_TOO_LONG.formatted(Message.MAX_LENGTH), SendResult.ErrorCode.MESSAGE_TOO_LONG);
        }

        String conversationId = Conversation.generateId(senderId, recipientId);
        if (communicationStorage.getConversation(conversationId).isEmpty()) {
            Conversation newConvo = Conversation.create(senderId, recipientId);
            communicationStorage.saveConversation(newConvo);
        }

        Message message = Message.create(conversationId, senderId, content);
        communicationStorage.saveMessage(message);
        communicationStorage.updateConversationLastMessageAt(conversationId, message.createdAt());

        return SendResult.success(message);
    }

    public List<Message> getMessages(UUID userId, UUID otherUserId, int limit, int offset) {
        if (limit < 1 || limit > CONFIG.messageMaxPageSize()) {
            return List.of();
        }
        if (offset < 0) {
            return List.of();
        }

        String conversationId = Conversation.generateId(userId, otherUserId);
        Optional<Conversation> convoOpt = communicationStorage.getConversation(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return List.of();
        }

        return communicationStorage.getMessages(conversationId, limit, offset);
    }

    public List<ConversationPreview> getConversations(UUID userId) {
        ensureMessagingDependencies();
        List<Conversation> conversations = communicationStorage.getConversationsFor(userId);

        List<UUID> otherUserIds =
                conversations.stream().map(c -> c.getOtherUser(userId)).toList();
        Map<UUID, User> otherUsers = userStorage.findByIds(new HashSet<>(otherUserIds));

        List<ConversationPreview> previews = new ArrayList<>();
        for (Conversation convo : conversations) {
            UUID otherUserId = convo.getOtherUser(userId);
            User otherUser = otherUsers.get(otherUserId);

            if (otherUser == null) {
                continue;
            }

            Optional<Message> lastMessage = communicationStorage.getLatestMessage(convo.getId());
            int unreadCount = calculateUnreadCount(userId, convo);
            previews.add(new ConversationPreview(convo, otherUser, lastMessage, unreadCount));
        }

        return previews;
    }

    public void markAsRead(UUID userId, String conversationId) {
        Optional<Conversation> convoOpt = communicationStorage.getConversation(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return;
        }

        communicationStorage.updateConversationReadTimestamp(conversationId, userId, AppClock.now());
    }

    public int getUnreadCount(UUID userId, String conversationId) {
        Optional<Conversation> convoOpt = communicationStorage.getConversation(conversationId);
        if (convoOpt.isEmpty()) {
            return 0;
        }
        return calculateUnreadCount(userId, convoOpt.get());
    }

    private int calculateUnreadCount(UUID userId, Conversation convo) {
        if (!convo.involves(userId)) {
            return 0;
        }

        Instant lastReadAt = convo.getLastReadAt(userId);
        if (lastReadAt == null) {
            return communicationStorage.countMessagesNotFromSender(convo.getId(), userId);
        }

        return communicationStorage.countMessagesAfterNotFrom(convo.getId(), lastReadAt, userId);
    }

    public int getTotalUnreadCount(UUID userId) {
        List<Conversation> conversations = communicationStorage.getConversationsFor(userId);
        int total = 0;
        for (Conversation convo : conversations) {
            total += calculateUnreadCount(userId, convo);
        }
        return total;
    }

    public boolean canMessage(UUID userA, UUID userB) {
        String matchId = Match.generateId(userA, userB);
        Optional<Match> matchOpt = interactionStorage.get(matchId);
        return matchOpt.isPresent() && matchOpt.get().canMessage();
    }

    public Conversation getOrCreateConversation(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        return communicationStorage.getConversation(conversationId).orElseGet(() -> {
            Conversation newConvo = Conversation.create(userA, userB);
            communicationStorage.saveConversation(newConvo);
            return newConvo;
        });
    }

    public FriendRequest requestFriendZone(UUID fromUserId, UUID targetUserId) {
        String matchId = Match.generateId(fromUserId, targetUserId);
        Optional<Match> matchOpt = interactionStorage.get(matchId);

        if (matchOpt.isEmpty() || !matchOpt.get().isActive()) {
            throw new TransitionValidationException("An active match is required to request the Friend Zone.");
        }

        Optional<FriendRequest> existing =
                communicationStorage.getPendingFriendRequestBetween(fromUserId, targetUserId);
        if (existing.isPresent()) {
            throw new TransitionValidationException("A friend zone request is already pending between these users.");
        }

        FriendRequest request = FriendRequest.create(fromUserId, targetUserId);
        communicationStorage.saveFriendRequest(request);

        communicationStorage.saveNotification(Notification.create(
                targetUserId,
                Notification.Type.FRIEND_REQUEST,
                "New Friend Request",
                "Someone wants to move your match to the Friend Zone.",
                Map.of("fromUserId", fromUserId.toString())));

        return request;
    }

    public void acceptFriendZone(UUID requestId, UUID responderId) {
        FriendRequest request = communicationStorage
                .getFriendRequest(requestId)
                .orElseThrow(() -> new TransitionValidationException("Friend request not found."));

        if (!request.toUserId().equals(responderId)) {
            throw new TransitionValidationException("Only the recipient can accept a friend request.");
        }

        if (!request.isPending()) {
            throw new TransitionValidationException("Request is no longer pending.");
        }

        String matchId = Match.generateId(request.fromUserId(), request.toUserId());
        Match match = interactionStorage
                .get(matchId)
                .orElseThrow(() -> new IllegalStateException("Match disappeared from storage."));

        match.transitionToFriends(request.fromUserId());
        interactionStorage.update(match);

        FriendRequest updated = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                AppClock.now());
        communicationStorage.updateFriendRequest(updated);

        communicationStorage.saveNotification(Notification.create(
                request.fromUserId(),
                Notification.Type.FRIEND_REQUEST_ACCEPTED,
                "Friend Request Accepted",
                "Your match with the other user has successfully transitioned to the Friend Zone.",
                Map.of("responderId", responderId.toString())));
    }

    public void declineFriendZone(UUID requestId, UUID responderId) {
        FriendRequest request = communicationStorage
                .getFriendRequest(requestId)
                .orElseThrow(() -> new TransitionValidationException("Friend request not found."));

        if (!request.toUserId().equals(responderId)) {
            throw new TransitionValidationException("Only the recipient can decline a friend request.");
        }

        if (!request.isPending()) {
            throw new TransitionValidationException("Request is no longer pending.");
        }

        FriendRequest updated = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.DECLINED,
                AppClock.now());
        communicationStorage.updateFriendRequest(updated);
    }

    public void gracefulExit(UUID initiatorId, UUID targetUserId) {
        String matchId = Match.generateId(initiatorId, targetUserId);
        Match match = interactionStorage
                .get(matchId)
                .orElseThrow(
                        () -> new TransitionValidationException("No active relationship found between these users."));

        if (!match.isActive() && match.getState() != Match.State.FRIENDS) {
            throw new TransitionValidationException("Relationship has already ended.");
        }

        match.gracefulExit(initiatorId);
        interactionStorage.update(match);

        Optional<Conversation> convoOpt = communicationStorage.getConversationByUsers(initiatorId, targetUserId);
        convoOpt.ifPresent(convo -> {
            convo.archive(Match.ArchiveReason.GRACEFUL_EXIT);
            communicationStorage.archiveConversation(convo.getId(), Match.ArchiveReason.GRACEFUL_EXIT);
        });

        communicationStorage.saveNotification(Notification.create(
                targetUserId,
                Notification.Type.GRACEFUL_EXIT,
                "Relationship Ended",
                "The other user has gracefully moved on from this relationship.",
                Map.of("initiatorId", initiatorId.toString())));
    }

    public List<FriendRequest> getPendingRequestsFor(UUID userId) {
        return communicationStorage.getPendingFriendRequestsForUser(userId);
    }

    /** Exception thrown when a relationship transition is invalid. */
    public static class TransitionValidationException extends RuntimeException {
        public TransitionValidationException(String message) {
            super(message);
        }
    }

    /** Result of sending a message. */
    public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {

        public SendResult {
            if (success) {
                Objects.requireNonNull(message, "message cannot be null when success is true");
                if (errorMessage != null || errorCode != null) {
                    throw new IllegalArgumentException("Error details must be null on success");
                }
            } else {
                Objects.requireNonNull(errorMessage, "errorMessage cannot be null when success is false");
                Objects.requireNonNull(errorCode, "errorCode cannot be null when success is false");
            }
        }

        /** Error codes for message sending failures. */
        public static enum ErrorCode {
            NO_ACTIVE_MATCH,
            USER_NOT_FOUND,
            EMPTY_MESSAGE,
            MESSAGE_TOO_LONG
        }

        public static SendResult success(Message message) {
            return new SendResult(true, message, null, null);
        }

        public static SendResult failure(String error, ErrorCode code) {
            return new SendResult(false, null, error, code);
        }
    }

    /** Preview of a conversation for list display. */
    public static record ConversationPreview(
            Conversation conversation, User otherUser, Optional<Message> lastMessage, int unreadCount) {

        public ConversationPreview {
            Objects.requireNonNull(conversation, "conversation cannot be null");
            Objects.requireNonNull(otherUser, "otherUser cannot be null");
            Objects.requireNonNull(lastMessage, "lastMessage cannot be null");
            if (unreadCount < 0) {
                throw new IllegalArgumentException("unreadCount cannot be negative");
            }
        }
    }
}
