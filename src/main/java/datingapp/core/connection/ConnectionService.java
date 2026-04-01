package datingapp.core.connection;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.profile.SanitizerUtils;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import datingapp.core.workflow.WorkflowDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Consolidated messaging + relationship transition service. */
public class ConnectionService {

    private static final String SENDER_NOT_FOUND = "Sender not found or inactive";
    private static final String RECIPIENT_NOT_FOUND = "Recipient not found or inactive";
    private static final String NO_ACTIVE_MATCH = "Cannot message: no active match";
    private static final String USER_ID_REQUIRED = "userId cannot be null";
    private static final String CONVERSATION_ID_REQUIRED = "conversationId cannot be null";
    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String EMPTY_MESSAGE = "Message cannot be empty";
    private static final String MESSAGE_TOO_LONG = "Message too long (max %d characters)";
    private static final String ATOMIC_TRANSITIONS_REQUIRED = "Relationship transition requires atomic storage support";

    private final AppConfig config;
    private final CommunicationStorage communicationStorage;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final RelationshipWorkflowPolicy workflowPolicy;

    /** Compatibility constructor for tests. */
    public ConnectionService(
            AppConfig config,
            CommunicationStorage communicationStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage) {
        this(config, communicationStorage, interactionStorage, userStorage, new RelationshipWorkflowPolicy());
    }

    /** Canonical constructor — all dependencies explicit. */
    public ConnectionService(
            AppConfig config,
            CommunicationStorage communicationStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            RelationshipWorkflowPolicy workflowPolicy) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.communicationStorage = Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.workflowPolicy = Objects.requireNonNull(workflowPolicy, "workflowPolicy cannot be null");
    }

    public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {
        User sender = userStorage.get(senderId).orElse(null);
        User recipient = userStorage.get(recipientId).orElse(null);
        String matchId = Match.generateId(senderId, recipientId);
        Match match = interactionStorage.get(matchId).orElse(null);

        WorkflowDecision decision = workflowPolicy.canSendMessage(match, sender, recipient);
        if (decision.isDenied()) {
            String reasonCode = ((WorkflowDecision.Denied) decision).reasonCode();
            return switch (reasonCode) {
                case "SENDER_NOT_ACTIVE" -> SendResult.failure(SENDER_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
                case "RECIPIENT_NOT_ACTIVE" ->
                    SendResult.failure(RECIPIENT_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
                default -> SendResult.failure(NO_ACTIVE_MATCH, SendResult.ErrorCode.NO_ACTIVE_MATCH);
            };
        }

        if (content == null || content.isBlank()) {
            return SendResult.failure(EMPTY_MESSAGE, SendResult.ErrorCode.EMPTY_MESSAGE);
        }
        content = SanitizerUtils.sanitizeMessage(content);
        if (content == null || content.isBlank()) {
            return SendResult.failure(EMPTY_MESSAGE, SendResult.ErrorCode.EMPTY_MESSAGE);
        }
        content = content.trim();
        int maxMessageLength = config.validation().maxMessageLength();
        if (content.length() > maxMessageLength) {
            return SendResult.failure(
                    MESSAGE_TOO_LONG.formatted(maxMessageLength), SendResult.ErrorCode.MESSAGE_TOO_LONG);
        }

        String conversationId = Conversation.generateId(senderId, recipientId);
        boolean createdConversation = false;
        if (communicationStorage.getConversation(conversationId).isEmpty()) {
            Conversation newConvo = Conversation.create(senderId, recipientId);
            communicationStorage.saveConversation(newConvo);
            createdConversation = true;
        }

        Message message = Message.create(conversationId, senderId, content);
        try {
            communicationStorage.saveMessageAndUpdateConversationLastMessageAt(message);
        } catch (RuntimeException e) {
            if (createdConversation) {
                communicationStorage.deleteConversationWithMessages(conversationId);
            }
            throw e;
        }

        return SendResult.success(message);
    }

    public MessageLoadResult getMessages(UUID userId, UUID otherUserId, int limit, int offset) {
        if (limit < 1 || limit > config.validation().messageMaxPageSize()) {
            return MessageLoadResult.failure("Invalid limit");
        }
        if (offset < 0) {
            return MessageLoadResult.failure("Invalid offset");
        }

        String conversationId = Conversation.generateId(userId, otherUserId);
        Optional<Conversation> convoOpt = communicationStorage.getConversation(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return MessageLoadResult.failure("Conversation not found or unauthorized");
        }

        return MessageLoadResult.success(communicationStorage.getMessages(conversationId, limit, offset));
    }

    public MessageLoadResult getMessages(String conversationId, int limit, int offset) {
        if (conversationId == null || conversationId.isBlank()) {
            return MessageLoadResult.failure("Conversation ID cannot be empty");
        }
        if (limit < 1 || limit > config.validation().messageMaxPageSize()) {
            return MessageLoadResult.failure("Invalid limit");
        }
        if (offset < 0) {
            return MessageLoadResult.failure("Invalid offset");
        }
        if (communicationStorage.getConversation(conversationId).isEmpty()) {
            return MessageLoadResult.failure(CONVERSATION_NOT_FOUND);
        }
        return MessageLoadResult.success(communicationStorage.getMessages(conversationId, limit, offset));
    }

    public int countMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        return communicationStorage.countMessages(conversationId);
    }

    public Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
        Objects.requireNonNull(conversationIds, "conversationIds cannot be null");
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return communicationStorage.countMessagesByConversationIds(conversationIds);
    }

    public int getTotalMessagesExchanged(UUID userId) {
        List<Conversation> conversations = communicationStorage.getAllConversationsFor(userId);
        if (conversations.isEmpty()) {
            return 0;
        }

        Set<String> conversationIds = new HashSet<>();
        for (Conversation conversation : conversations) {
            conversationIds.add(conversation.getId());
        }

        return countMessagesByConversationIds(conversationIds).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public List<ConversationPreview> getConversations(UUID userId, int limit, int offset) {
        if (limit < 1 || limit > config.validation().messageMaxPageSize()) {
            return List.of();
        }
        if (offset < 0) {
            return List.of();
        }
        List<Conversation> conversations = communicationStorage.getConversationsFor(userId, limit, offset);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> otherUserIds =
                conversations.stream().map(c -> c.getOtherUser(userId)).toList();
        Map<UUID, User> otherUsers = userStorage.findByIds(new HashSet<>(otherUserIds));
        Set<String> conversationIds =
                conversations.stream().map(Conversation::getId).collect(java.util.stream.Collectors.toSet());
        Map<String, Optional<Message>> latestMessages =
                communicationStorage.getLatestMessagesByConversationIds(conversationIds);
        Map<String, Integer> unreadCounts =
                communicationStorage.countUnreadMessagesByConversationIds(userId, conversationIds);

        List<ConversationPreview> previews = new ArrayList<>();
        for (Conversation convo : conversations) {
            UUID otherUserId = convo.getOtherUser(userId);
            User otherUser = otherUsers.get(otherUserId);

            if (otherUser == null) {
                continue;
            }

            Optional<Message> lastMessage = latestMessages.getOrDefault(convo.getId(), Optional.empty());
            int unreadCount = unreadCounts.getOrDefault(convo.getId(), 0);
            previews.add(new ConversationPreview(convo, otherUser, lastMessage, unreadCount));
        }

        return previews;
    }

    public ConversationPreview getConversationPreview(UUID userId, String conversationId) {
        Objects.requireNonNull(userId, USER_ID_REQUIRED);
        Objects.requireNonNull(conversationId, CONVERSATION_ID_REQUIRED);

        Optional<Conversation> conversationOpt = communicationStorage.getConversation(conversationId);
        if (conversationOpt.isEmpty()) {
            return null;
        }

        Conversation conversation = conversationOpt.get();
        if (!conversation.involves(userId)) {
            return null;
        }

        User otherUser = userStorage.get(conversation.getOtherUser(userId)).orElse(null);
        if (otherUser == null) {
            return null;
        }

        Optional<Message> lastMessage = communicationStorage.getLatestMessage(conversationId);
        int unreadCount = calculateUnreadCount(userId, conversation);
        return new ConversationPreview(conversation, otherUser, lastMessage, unreadCount);
    }

    public void markAsRead(UUID userId, String conversationId) {
        Optional<Conversation> convoOpt = communicationStorage.getConversation(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return;
        }

        if (calculateUnreadCount(userId, convoOpt.get()) == 0) {
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
        List<Conversation> conversations = communicationStorage.getAllConversationsFor(userId);
        int total = 0;
        for (Conversation convo : conversations) {
            total += calculateUnreadCount(userId, convo);
        }
        return total;
    }

    public int getUnreadNotificationCount(UUID userId) {
        return communicationStorage.getNotificationsForUser(userId, true).size();
    }

    public boolean canMessage(UUID userA, UUID userB) {
        String matchId = Match.generateId(userA, userB);
        Optional<Match> matchOpt = interactionStorage.get(matchId);
        return matchOpt.isPresent() && matchOpt.get().canMessage();
    }

    public Conversation getOrCreateConversation(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        Optional<Conversation> existing = communicationStorage.getConversation(conversationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (!canMessage(userA, userB)) {
            throw new IllegalArgumentException(NO_ACTIVE_MATCH);
        }

        return communicationStorage.getConversation(conversationId).orElseGet(() -> {
            Conversation newConvo = Conversation.create(userA, userB);
            communicationStorage.saveConversation(newConvo);
            return newConvo;
        });
    }

    public void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason) {
        Objects.requireNonNull(conversationId, CONVERSATION_ID_REQUIRED);
        Objects.requireNonNull(userId, USER_ID_REQUIRED);
        Objects.requireNonNull(reason, "reason cannot be null");

        Conversation conversation = communicationStorage
                .getConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        if (!conversation.involves(userId)) {
            throw new IllegalArgumentException("Conversation not found or unauthorized");
        }
        communicationStorage.archiveConversation(conversationId, userId, reason);
    }

    public void deleteConversation(UUID userId, String conversationId) {
        Objects.requireNonNull(userId, USER_ID_REQUIRED);
        Objects.requireNonNull(conversationId, CONVERSATION_ID_REQUIRED);

        Conversation conversation = communicationStorage
                .getConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        if (!conversation.involves(userId)) {
            throw new IllegalArgumentException("Conversation not found or unauthorized");
        }
        communicationStorage.deleteConversationWithMessages(conversationId);
    }

    public void deleteMessage(UUID userId, String conversationId, UUID messageId) {
        Objects.requireNonNull(userId, USER_ID_REQUIRED);
        Objects.requireNonNull(conversationId, CONVERSATION_ID_REQUIRED);
        Objects.requireNonNull(messageId, "messageId cannot be null");

        Conversation conversation = communicationStorage
                .getConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        if (!conversation.involves(userId)) {
            throw new IllegalArgumentException("Conversation not found or unauthorized");
        }

        Message message = communicationStorage
                .getMessage(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!conversationId.equals(message.conversationId())) {
            throw new IllegalArgumentException("Message not found in conversation");
        }
        if (!message.senderId().equals(userId)) {
            throw new IllegalArgumentException("Only the sender can delete a message");
        }
        communicationStorage.deleteMessage(messageId);
    }

    public TransitionResult requestFriendZone(UUID fromUserId, UUID targetUserId) {
        String matchId = Match.generateId(fromUserId, targetUserId);
        Optional<Match> matchOpt = interactionStorage.get(matchId);

        WorkflowDecision decision = workflowPolicy.canRequestFriendZone(matchOpt.orElse(null));
        if (decision.isDenied()) {
            return TransitionResult.failure("An active match is required to request the Friend Zone.");
        }

        Optional<FriendRequest> existing =
                communicationStorage.getPendingFriendRequestBetween(fromUserId, targetUserId);
        if (existing.isPresent()) {
            return TransitionResult.failure("A friend zone request is already pending between these users.");
        }

        FriendRequest request = FriendRequest.create(fromUserId, targetUserId);
        Notification notification = Notification.create(
                targetUserId,
                Notification.Type.FRIEND_REQUEST,
                "New Friend Request",
                "Someone wants to move your match to the Friend Zone.",
                Map.of("fromUserId", fromUserId.toString()));

        try {
            communicationStorage.saveFriendRequestWithNotification(request, notification);
        } catch (Exception e) {
            return TransitionResult.failure("Failed to save friend request: " + e.getMessage());
        }

        return TransitionResult.okWithRequest(request);
    }

    public TransitionResult acceptFriendZone(UUID requestId, UUID responderId) {
        Optional<FriendRequest> requestOpt = communicationStorage.getFriendRequest(requestId);
        if (requestOpt.isEmpty()) {
            return TransitionResult.failure("Friend request not found.");
        }
        FriendRequest request = requestOpt.get();

        if (!request.toUserId().equals(responderId)) {
            return TransitionResult.failure("Only the recipient can accept a friend request.");
        }

        if (!request.isPending()) {
            return TransitionResult.failure("Request is no longer pending.");
        }

        String matchId = Match.generateId(request.fromUserId(), request.toUserId());
        Optional<Match> matchOpt = interactionStorage.get(matchId);
        if (matchOpt.isEmpty()) {
            return TransitionResult.failure("Could not find the associated match.");
        }
        Match updatedMatch = copyMatch(matchOpt.get());

        FriendRequest updated = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                AppClock.now());

        if (!interactionStorage.supportsAtomicRelationshipTransitions()) {
            return TransitionResult.failure(ATOMIC_TRANSITIONS_REQUIRED);
        }

        try {
            updatedMatch.transitionToFriends(request.fromUserId());
            boolean transitioned = interactionStorage.acceptFriendZoneTransition(updatedMatch, updated, null);
            if (!transitioned) {
                return TransitionResult.failure("Failed to persist friend-zone acceptance.");
            }
            return TransitionResult.okWithRequest(updated);
        } catch (Exception e) {
            return TransitionResult.failure("Failed to persist friend-zone acceptance: " + e.getMessage());
        }
    }

    public TransitionResult declineFriendZone(UUID requestId, UUID responderId) {
        Optional<FriendRequest> requestOpt = communicationStorage.getFriendRequest(requestId);
        if (requestOpt.isEmpty()) {
            return TransitionResult.failure("Friend request not found.");
        }
        FriendRequest request = requestOpt.get();

        if (!request.toUserId().equals(responderId)) {
            return TransitionResult.failure("Only the recipient can decline a friend request.");
        }

        if (!request.isPending()) {
            return TransitionResult.failure("Request is no longer pending.");
        }

        FriendRequest updated = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.DECLINED,
                AppClock.now());
        communicationStorage.updateFriendRequest(updated);
        return TransitionResult.ok();
    }

    public TransitionResult gracefulExit(UUID initiatorId, UUID targetUserId) {
        String matchId = Match.generateId(initiatorId, targetUserId);
        Optional<Match> matchOpt = interactionStorage.get(matchId);
        if (matchOpt.isEmpty()) {
            return TransitionResult.failure("No active relationship found between these users.");
        }
        Match match = matchOpt.get();

        WorkflowDecision decision = workflowPolicy.canGracefulExit(match);
        if (decision.isDenied()) {
            return TransitionResult.failure("Relationship has already ended.");
        }

        if (!interactionStorage.supportsAtomicRelationshipTransitions()) {
            return TransitionResult.failure(ATOMIC_TRANSITIONS_REQUIRED);
        }

        Optional<Conversation> convoOpt = communicationStorage.getConversationByUsers(initiatorId, targetUserId);
        Optional<Conversation> archivedConversation = convoOpt.map(ConnectionService::copyConversation);
        archivedConversation.ifPresent(convo -> {
            convo.archive(convo.getUserA(), MatchArchiveReason.GRACEFUL_EXIT);
            convo.archive(convo.getUserB(), MatchArchiveReason.GRACEFUL_EXIT);
        });
        Match updatedMatch = copyMatch(match);

        try {
            updatedMatch.gracefulExit(initiatorId);
            boolean transitioned = interactionStorage.gracefulExitTransition(updatedMatch, archivedConversation, null);
            if (!transitioned) {
                return TransitionResult.failure("Failed to persist graceful exit transition.");
            }
            return TransitionResult.ok();
        } catch (Exception e) {
            return TransitionResult.failure("Failed to persist graceful exit transition: " + e.getMessage());
        }
    }

    /**
     * Unmatches two users by transitioning the match to UNMATCHED and archiving
     * any existing conversation from both sides.
     *
     * @param initiatorId the user initiating the unmatch
     * @param targetId    the other user
     * @return result of the unmatch operation
     */
    public TransitionResult unmatch(UUID initiatorId, UUID targetId) {
        Objects.requireNonNull(initiatorId, "initiatorId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");

        Optional<Match> matchOpt = interactionStorage.getByUsers(initiatorId, targetId);
        if (matchOpt.isEmpty()) {
            return TransitionResult.failure("No match found between these users.");
        }
        Match match = matchOpt.get();

        WorkflowDecision decision = workflowPolicy.canUnmatch(match);
        if (decision.isDenied()) {
            return TransitionResult.failure("Match cannot be unmatched from its current state.");
        }

        if (!interactionStorage.supportsAtomicRelationshipTransitions()) {
            return TransitionResult.failure(ATOMIC_TRANSITIONS_REQUIRED);
        }

        // Archive the conversation for both participants, if one exists.
        Optional<Conversation> convoOpt = communicationStorage.getConversationByUsers(initiatorId, targetId);
        Optional<Conversation> archivedConversation = convoOpt.map(ConnectionService::copyConversation);
        archivedConversation.ifPresent(convo -> {
            convo.archive(convo.getUserA(), MatchArchiveReason.UNMATCH);
            convo.archive(convo.getUserB(), MatchArchiveReason.UNMATCH);
        });
        Match updatedMatch = copyMatch(match);

        try {
            updatedMatch.unmatch(initiatorId);
            boolean transitioned = interactionStorage.unmatchTransition(updatedMatch, archivedConversation);
            if (!transitioned) {
                return TransitionResult.failure("Failed to persist unmatch transition.");
            }
            return TransitionResult.ok();
        } catch (Exception e) {
            return TransitionResult.failure("Failed to persist unmatch transition: " + e.getMessage());
        }
    }

    private static Match copyMatch(Match match) {
        return new Match(
                match.getId(),
                match.getUserA(),
                match.getUserB(),
                match.getCreatedAt(),
                match.getUpdatedAt(),
                match.getState(),
                match.getEndedAt(),
                match.getEndedBy(),
                match.getEndReason(),
                match.getDeletedAt());
    }

    private static Conversation copyConversation(Conversation conversation) {
        return Conversation.copyOf(conversation);
    }

    public List<FriendRequest> getPendingRequestsFor(UUID userId) {
        return communicationStorage.getPendingFriendRequestsForUser(userId);
    }

    /** Returns the count of pending friend-zone requests sent to {@code userId}. */
    public int countPendingRequestsFor(UUID userId) {
        return communicationStorage.countPendingFriendRequestsForUser(userId);
    }

    /** Exception thrown when a relationship transition is invalid. */
    public static class TransitionValidationException extends RuntimeException {
        public TransitionValidationException(String message) {
            super(message);
        }
    }

    /** Result of a relationship transition operation. */
    public static record TransitionResult(boolean success, FriendRequest friendRequest, String errorMessage) {
        public TransitionResult {
            if (!success) {
                Objects.requireNonNull(errorMessage, "errorMessage required on failure");
            }
        }

        public static TransitionResult ok() {
            return new TransitionResult(true, null, null);
        }

        public static TransitionResult okWithRequest(FriendRequest req) {
            return new TransitionResult(true, Objects.requireNonNull(req), null);
        }

        public static TransitionResult failure(String error) {
            return new TransitionResult(false, null, Objects.requireNonNull(error));
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
        public enum ErrorCode {
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

    /** Result of loading messages. */
    public static record MessageLoadResult(boolean success, List<Message> messages, String errorMessage) {
        public MessageLoadResult {
            if (success) {
                Objects.requireNonNull(messages, "messages cannot be null when success is true");
                if (errorMessage != null) {
                    throw new IllegalArgumentException("errorMessage must be null on success");
                }
            } else {
                Objects.requireNonNull(errorMessage, "errorMessage cannot be null when success is false");
                if (messages != null) {
                    throw new IllegalArgumentException("messages must be null on failure");
                }
            }
        }

        public static MessageLoadResult success(List<Message> messages) {
            return new MessageLoadResult(true, messages, null);
        }

        public static MessageLoadResult failure(String error) {
            return new MessageLoadResult(false, null, error);
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
