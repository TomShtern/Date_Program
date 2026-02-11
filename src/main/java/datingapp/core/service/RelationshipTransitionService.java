package datingapp.core.service;

import datingapp.core.AppClock;
import datingapp.core.model.Match;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RelationshipTransitionService {

    /** Exception thrown when a relationship transition is invalid. */
    public static class TransitionValidationException extends RuntimeException {
        public TransitionValidationException(String message) {
            super(message);
        }
    }

    private final MatchStorage matchStorage;
    private final SocialStorage socialStorage;
    private final MessagingStorage messagingStorage;

    public RelationshipTransitionService(
            MatchStorage matchStorage, SocialStorage socialStorage, MessagingStorage messagingStorage) {
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
        this.socialStorage = Objects.requireNonNull(socialStorage, "socialStorage cannot be null");
        this.messagingStorage = Objects.requireNonNull(messagingStorage, "messagingStorage cannot be null");
    }

    /**
     * Initiates a request to transition a match to the "Friend Zone".
     *
     * @param fromUserId   The user initiating the request
     * @param targetUserId The user they want to be friends with
     * @return The created friend request
     * @throws TransitionValidationException if no active match exists or a request
     *                                       is already pending
     */
    public FriendRequest requestFriendZone(UUID fromUserId, UUID targetUserId) {
        String matchId = Match.generateId(fromUserId, targetUserId);
        Optional<Match> matchOpt = matchStorage.get(matchId);

        if (matchOpt.isEmpty() || !matchOpt.get().isActive()) {
            throw new TransitionValidationException("An active match is required to request the Friend Zone.");
        }

        // Check for existing pending request
        Optional<FriendRequest> existing = socialStorage.getPendingFriendRequestBetween(fromUserId, targetUserId);
        if (existing.isPresent()) {
            throw new TransitionValidationException("A friend zone request is already pending between these users.");
        }

        FriendRequest request = FriendRequest.create(fromUserId, targetUserId);
        socialStorage.saveFriendRequest(request);

        // Send Notification
        socialStorage.saveNotification(Notification.create(
                targetUserId,
                Notification.Type.FRIEND_REQUEST,
                "New Friend Request",
                "Someone wants to move your match to the Friend Zone.",
                Map.of("fromUserId", fromUserId.toString())));

        return request;
    }

    /**
     * Accepts a pending friend zone request.
     *
     * @param requestId   The ID of the request to accept
     * @param responderId The user responding (must be the toUserId)
     * @throws TransitionValidationException if request not found or not pending
     */
    public void acceptFriendZone(UUID requestId, UUID responderId) {
        FriendRequest request = socialStorage
                .getFriendRequest(requestId)
                .orElseThrow(() -> new TransitionValidationException("Friend request not found."));

        if (!request.toUserId().equals(responderId)) {
            throw new TransitionValidationException("Only the recipient can accept a friend request.");
        }

        if (!request.isPending()) {
            throw new TransitionValidationException("Request is no longer pending.");
        }

        // 1. Update match state
        String matchId = Match.generateId(request.fromUserId(), request.toUserId());
        Match match = matchStorage
                .get(matchId)
                .orElseThrow(() -> new IllegalStateException("Match disappeared from storage."));

        match.transitionToFriends(request.fromUserId()); // Originator of request is considered starter of transition
        matchStorage.update(match);

        // 2. Update request status
        FriendRequest updated = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                AppClock.now());
        socialStorage.updateFriendRequest(updated);

        // 3. Send Notification
        socialStorage.saveNotification(Notification.create(
                request.fromUserId(),
                Notification.Type.FRIEND_REQUEST_ACCEPTED,
                "Friend Request Accepted",
                "Your match with the other user has successfully transitioned to the Friend Zone.",
                Map.of("responderId", responderId.toString())));
    }

    /**
     * Declines a pending friend zone request.
     *
     * @param requestId   The ID of the request to decline
     * @param responderId The user responding
     */
    public void declineFriendZone(UUID requestId, UUID responderId) {
        FriendRequest request = socialStorage
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
        socialStorage.updateFriendRequest(updated);
    }

    /**
     * Gracefully exits a match or friendship.
     *
     * @param initiatorId  The user ending the relationship
     * @param targetUserId The other user
     */
    public void gracefulExit(UUID initiatorId, UUID targetUserId) {
        String matchId = Match.generateId(initiatorId, targetUserId);
        Match match = matchStorage
                .get(matchId)
                .orElseThrow(
                        () -> new TransitionValidationException("No active relationship found between these users."));

        if (!match.isActive() && match.getState() != Match.State.FRIENDS) {
            throw new TransitionValidationException("Relationship has already ended.");
        }

        // 1. Update Match State
        match.gracefulExit(initiatorId);
        matchStorage.update(match);

        // 2. Archive Conversation
        Optional<Conversation> convoOpt = messagingStorage.getConversationByUsers(initiatorId, targetUserId);
        convoOpt.ifPresent(convo -> {
            convo.archive(Match.ArchiveReason.GRACEFUL_EXIT);
            messagingStorage.archiveConversation(convo.getId(), Match.ArchiveReason.GRACEFUL_EXIT);
        });

        // 3. Send Notification to target user
        socialStorage.saveNotification(Notification.create(
                targetUserId,
                Notification.Type.GRACEFUL_EXIT,
                "Relationship Ended",
                "The other user has gracefully moved on from this relationship.",
                Map.of("initiatorId", initiatorId.toString())));
    }

    public List<FriendRequest> getPendingRequestsFor(UUID userId) {
        return socialStorage.getPendingFriendRequestsForUser(userId);
    }
}
