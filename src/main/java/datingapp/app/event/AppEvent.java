package datingapp.app.event;

import datingapp.core.connection.ConnectionModels.Like;
import java.time.Instant;
import java.util.UUID;

/**
 * Sealed hierarchy for all application events.
 * Events are immutable records carrying the minimum context needed.
 */
public sealed interface AppEvent
        permits AppEvent.SwipeRecorded,
                AppEvent.MatchCreated,
                AppEvent.ProfileSaved,
                AppEvent.ProfileCompleted,
                AppEvent.ProfileNoteSaved,
                AppEvent.ProfileNoteDeleted,
                AppEvent.ConversationArchived,
                AppEvent.LocationUpdated,
                AppEvent.DailyLimitReset,
                AppEvent.MatchExpired,
                AppEvent.AccountDeleted,
                AppEvent.FriendRequestAccepted,
                AppEvent.RelationshipTransitioned,
                AppEvent.MessageSent,
                AppEvent.UserBlocked,
                AppEvent.UserReported {

    Instant occurredAt();

    enum DeletionReason {
        USER_REQUEST,
        PRIVACY_REQUEST,
        SAFETY_ACTION,
        ANONYMIZED_CODE
    }

    enum RelationshipTransitionState {
        MATCHED,
        UNMATCHED,
        FRIEND_ZONE_REQUESTED,
        ACTIVE,
        GRACEFUL_EXIT
    }

    record SwipeRecorded(
            UUID swiperId, UUID targetId, Like.Direction direction, boolean resultedInMatch, Instant occurredAt)
            implements AppEvent {}

    record MatchCreated(String matchId, UUID userA, UUID userB, Instant occurredAt) implements AppEvent {}

    record ProfileSaved(UUID userId, boolean activated, Instant occurredAt) implements AppEvent {}

    record ProfileCompleted(UUID userId, Instant occurredAt) implements AppEvent {}

    record ProfileNoteSaved(UUID authorId, UUID subjectId, int contentLength, Instant occurredAt) implements AppEvent {}

    record ProfileNoteDeleted(UUID authorId, UUID subjectId, Instant occurredAt) implements AppEvent {}

    record ConversationArchived(String conversationId, UUID archivedByUserId, Instant occurredAt) implements AppEvent {}

    record LocationUpdated(UUID userId, double latitude, double longitude, Instant occurredAt) implements AppEvent {}

    record DailyLimitReset(UUID userId, Instant occurredAt) implements AppEvent {}

    record MatchExpired(String matchId, UUID userA, UUID userB, Instant occurredAt) implements AppEvent {}

    record AccountDeleted(UUID userId, DeletionReason reason, Instant occurredAt) implements AppEvent {}

    record FriendRequestAccepted(UUID requestId, UUID fromUserId, UUID toUserId, String matchId, Instant occurredAt)
            implements AppEvent {}

    record RelationshipTransitioned(
            String matchId,
            UUID initiatorId,
            UUID targetId,
            RelationshipTransitionState fromState,
            RelationshipTransitionState toState,
            Instant occurredAt)
            implements AppEvent {}

    record MessageSent(UUID senderId, UUID recipientId, UUID messageId, Instant occurredAt) implements AppEvent {}

    record UserBlocked(UUID blockerId, UUID blockedUserId, Instant occurredAt) implements AppEvent {}

    record UserReported(
            UUID reporterId,
            UUID reportedUserId,
            String reason,
            boolean blockedUser,
            boolean validated,
            Instant occurredAt)
            implements AppEvent {}
}
