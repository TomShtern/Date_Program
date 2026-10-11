package datingapp.app.usecase.social;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.EventPublishing;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.storage.CommunicationStorage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Social-graph and trust/safety use-cases shared by application adapters. */
public class SocialUseCases {

    private static final Logger logger = LoggerFactory.getLogger(SocialUseCases.class);
    private static final String COMMUNICATION_STORAGE_NOT_CONFIGURED = "CommunicationStorage is not configured";
    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String CONTEXT_AND_TARGET_REQUIRED = "Context and target user are required";
    private static final String CONNECTION_SERVICE_NOT_CONFIGURED = "ConnectionService is not configured";
    private static final String CONNECTION_SERVICE_REQUIRED = "connectionService cannot be null";
    private static final String TRUST_SAFETY_SERVICE_REQUIRED = "trustSafetyService cannot be null";
    private static final String COMMUNICATION_STORAGE_REQUIRED = "communicationStorage cannot be null";
    private static final String TRUST_SAFETY_SERVICE_NOT_CONFIGURED = "TrustSafetyService is not configured";

    private static final AppEventBus COMPATIBILITY_NO_OP_EVENT_BUS = new AppEventBus() {
        @Override
        public void publish(AppEvent event) {
            if (logger.isWarnEnabled()) {
                logger.warn("Dropping event {} because no real event bus is configured", event);
            }
        }

        @Override
        public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
            // Compatibility shim for non-production construction paths.
        }

        @Override
        public <T extends AppEvent> void subscribe(
                Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
            // Compatibility shim for non-production construction paths.
        }
    };

    private final ConnectionService connectionService;
    private final TrustSafetyService trustSafetyService;
    private final CommunicationStorage communicationStorage;
    private final AppEventBus eventBus;

    /** Compatibility factory for moderation-only callers. */
    public static SocialUseCases forTrustSafetyOnly(TrustSafetyService trustSafetyService) {
        return new SocialUseCases(trustSafetyService);
    }

    /** Compatibility factory for relationship workflows without event publication. */
    public static SocialUseCases forWorkflowAccess(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage) {
        return new SocialUseCases(connectionService, trustSafetyService, communicationStorage);
    }

    /** Named compatibility mode for moderation-only callers. */
    private SocialUseCases(TrustSafetyService trustSafetyService) {
        this.connectionService = null;
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, TRUST_SAFETY_SERVICE_REQUIRED);
        this.communicationStorage = null;
        this.eventBus = COMPATIBILITY_NO_OP_EVENT_BUS;
    }

    /** Named compatibility mode for relationship workflows without event publication. */
    private SocialUseCases(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage) {
        this.connectionService = Objects.requireNonNull(connectionService, CONNECTION_SERVICE_REQUIRED);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, TRUST_SAFETY_SERVICE_REQUIRED);
        this.communicationStorage = Objects.requireNonNull(communicationStorage, COMMUNICATION_STORAGE_REQUIRED);
        this.eventBus = COMPATIBILITY_NO_OP_EVENT_BUS;
    }

    /** Canonical runtime constructor — all workflow dependencies explicit. */
    public SocialUseCases(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage,
            AppEventBus eventBus) {
        this.connectionService = Objects.requireNonNull(connectionService, CONNECTION_SERVICE_REQUIRED);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, TRUST_SAFETY_SERVICE_REQUIRED);
        this.communicationStorage = Objects.requireNonNull(communicationStorage, COMMUNICATION_STORAGE_REQUIRED);
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    public UseCaseResult<TrustSafetyService.BlockResult> blockUser(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return validationFailure(CONTEXT_AND_TARGET_REQUIRED);
        }
        if (trustSafetyService == null) {
            return dependencyFailure(TRUST_SAFETY_SERVICE_NOT_CONFIGURED);
        }
        try {
            TrustSafetyService.BlockResult result =
                    trustSafetyService.block(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableBlockMessage(result.errorMessage())));
            }
            logModerationAction("block", command.context().userId(), command.targetUserId(), null);
            EventPublishing.publishOrWarn(
                    eventBus,
                    new AppEvent.UserBlocked(command.context().userId(), command.targetUserId(), AppClock.now()),
                    "Failed to publish user blocked event",
                    logger);
            return UseCaseResult.success(result);
        } catch (Exception e) {
            return internalFailure("block user", e);
        }
    }

    public UseCaseResult<List<BlockedUserSummary>> listBlockedUsers(ListBlockedUsersQuery query) {
        if (query == null || query.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        if (trustSafetyService == null) {
            return dependencyFailure(TRUST_SAFETY_SERVICE_NOT_CONFIGURED);
        }
        try {
            List<BlockedUserSummary> blockedUsers =
                    trustSafetyService.getBlockedUsers(query.context().userId()).stream()
                            .map(user -> new BlockedUserSummary(user.getId(), user.getName()))
                            .toList();
            return UseCaseResult.success(blockedUsers);
        } catch (Exception e) {
            return internalFailure("load blocked users", e);
        }
    }

    public UseCaseResult<Void> unblockUser(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return validationFailure(CONTEXT_AND_TARGET_REQUIRED);
        }
        if (trustSafetyService == null) {
            return dependencyFailure(TRUST_SAFETY_SERVICE_NOT_CONFIGURED);
        }
        try {
            boolean success = trustSafetyService.unblock(command.context().userId(), command.targetUserId());
            if (!success) {
                return UseCaseResult.failure(UseCaseError.conflict("Failed to unblock user"));
            }
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return internalFailure("unblock user", e);
        }
    }

    public UseCaseResult<ReportOutcome> reportUser(ReportCommand command) {
        if (command == null
                || command.context() == null
                || command.targetUserId() == null
                || command.reason() == null) {
            return validationFailure("Context, target user and reason are required");
        }
        if (trustSafetyService == null) {
            return dependencyFailure(TRUST_SAFETY_SERVICE_NOT_CONFIGURED);
        }
        try {
            TrustSafetyService.ReportResult result = trustSafetyService.report(
                    command.context().userId(),
                    command.targetUserId(),
                    command.reason(),
                    command.description(),
                    command.blockUser());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableReportMessage(result.errorMessage())));
            }
            logModerationAction(
                    "report",
                    command.context().userId(),
                    command.targetUserId(),
                    command.reason().name());
            EventPublishing.publishOrWarn(
                    eventBus,
                    new AppEvent.UserReported(
                            command.context().userId(),
                            command.targetUserId(),
                            command.reason().name(),
                            command.blockUser(),
                            result.userWasFlagged(),
                            AppClock.now()),
                    "Failed to publish user reported event",
                    logger);
            return UseCaseResult.success(ReportOutcome.from(result, command.blockUser()));
        } catch (Exception e) {
            return internalFailure("report user", e);
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> unmatch(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return validationFailure(CONTEXT_AND_TARGET_REQUIRED);
        }
        if (connectionService == null) {
            return dependencyFailure(CONNECTION_SERVICE_NOT_CONFIGURED);
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.unmatch(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableRelationshipMessage(result.errorMessage())));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    AppEvent.RelationshipTransitionState.MATCHED,
                    AppEvent.RelationshipTransitionState.UNMATCHED);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return internalFailure("unmatch users", e);
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> requestFriendZone(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return validationFailure(CONTEXT_AND_TARGET_REQUIRED);
        }
        if (connectionService == null) {
            return dependencyFailure(CONNECTION_SERVICE_NOT_CONFIGURED);
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.requestFriendZone(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableRelationshipMessage(result.errorMessage())));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    AppEvent.RelationshipTransitionState.MATCHED,
                    AppEvent.RelationshipTransitionState.FRIEND_ZONE_REQUESTED);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return internalFailure("request friend-zone transition", e);
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> gracefulExit(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return validationFailure(CONTEXT_AND_TARGET_REQUIRED);
        }
        if (connectionService == null) {
            return dependencyFailure(CONNECTION_SERVICE_NOT_CONFIGURED);
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.gracefulExit(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableRelationshipMessage(result.errorMessage())));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    AppEvent.RelationshipTransitionState.ACTIVE,
                    AppEvent.RelationshipTransitionState.GRACEFUL_EXIT);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return internalFailure("gracefully exit relationship", e);
        }
    }

    public UseCaseResult<List<FriendRequest>> pendingFriendRequests(FriendRequestsQuery query) {
        if (query == null || query.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        if (connectionService == null) {
            return dependencyFailure(CONNECTION_SERVICE_NOT_CONFIGURED);
        }
        try {
            return UseCaseResult.success(
                    connectionService.getPendingRequestsFor(query.context().userId()));
        } catch (Exception e) {
            return internalFailure("load pending friend requests", e);
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> respondToFriendRequest(RespondFriendRequestCommand command) {
        if (command == null || command.context() == null || command.requestId() == null || command.action() == null) {
            return validationFailure("Context, requestId and action are required");
        }
        if (connectionService == null) {
            return dependencyFailure(CONNECTION_SERVICE_NOT_CONFIGURED);
        }
        try {
            ConnectionService.TransitionResult result =
                    switch (command.action()) {
                        case ACCEPT ->
                            connectionService.acceptFriendZone(
                                    command.requestId(), command.context().userId());
                        case DECLINE ->
                            connectionService.declineFriendZone(
                                    command.requestId(), command.context().userId());
                    };
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableRelationshipMessage(result.errorMessage())));
            }
            if (command.action() == FriendRequestAction.ACCEPT && result.friendRequest() != null) {
                var req = result.friendRequest();
                String matchId = Match.generateId(req.fromUserId(), req.toUserId());
                EventPublishing.publishOrWarn(
                        eventBus,
                        new AppEvent.FriendRequestAccepted(
                                req.id(), req.fromUserId(), req.toUserId(), matchId, AppClock.now()),
                        "Failed to publish friend request accepted event",
                        logger);
            }
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return internalFailure("respond to friend request", e);
        }
    }

    public UseCaseResult<List<Notification>> notifications(NotificationsQuery query) {
        if (query == null || query.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        if (communicationStorage == null) {
            return dependencyFailure(COMMUNICATION_STORAGE_NOT_CONFIGURED);
        }
        try {
            return UseCaseResult.success(
                    communicationStorage.getNotificationsForUser(query.context().userId(), query.unreadOnly()));
        } catch (Exception e) {
            return internalFailure("load notifications", e);
        }
    }

    public UseCaseResult<Void> markNotificationRead(MarkNotificationReadCommand command) {
        if (command == null || command.context() == null || command.notificationId() == null) {
            return validationFailure("Context and notificationId are required");
        }
        if (communicationStorage == null) {
            return dependencyFailure(COMMUNICATION_STORAGE_NOT_CONFIGURED);
        }
        try {
            return switch (communicationStorage.markNotificationAsReadChecked(
                    command.context().userId(), command.notificationId())) {
                case UPDATED -> UseCaseResult.success(null);
                case NOT_FOUND -> UseCaseResult.failure(UseCaseError.notFound("Notification not found"));
                case NOT_OWNED ->
                    UseCaseResult.failure(UseCaseError.forbidden("Notification does not belong to current user"));
            };
        } catch (Exception e) {
            return internalFailure("mark notification as read", e);
        }
    }

    public UseCaseResult<Integer> markAllNotificationsRead(MarkAllNotificationsReadCommand command) {
        if (command == null || command.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        if (communicationStorage == null) {
            return dependencyFailure(COMMUNICATION_STORAGE_NOT_CONFIGURED);
        }
        try {
            return UseCaseResult.success(communicationStorage.markAllNotificationsAsRead(
                    command.context().userId()));
        } catch (Exception e) {
            return internalFailure("mark notifications as read", e);
        }
    }

    private static <T> UseCaseResult<T> validationFailure(String message) {
        return UseCaseResult.failure(UseCaseError.validation(message));
    }

    private static <T> UseCaseResult<T> dependencyFailure(String message) {
        return UseCaseResult.failure(UseCaseError.dependency(message));
    }

    private static <T> UseCaseResult<T> internalFailure(String action, Exception error) {
        return UseCaseResult.failure(UseCaseError.internalFailure(action, error));
    }

    /**
     * Maps block-service internals to a stable user-facing message. Raw storage details
     * stay server-side; the API surface only exposes whether the block target was invalid
     * or the request was malformed.
     */
    public static String stableBlockMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Cannot block user.";
        }
        String lower = detail.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("yourself") || lower.contains("self")) {
            return "Cannot block yourself.";
        }
        if (lower.contains("not found") || lower.contains("inactive") || lower.contains("active")) {
            return "Block target is not available.";
        }
        return "Cannot block user.";
    }

    /**
     * Maps report-service internals to a stable user-facing message. Raw storage details
     * stay server-side; the API surface only exposes whether the report target was invalid
     * or the request was malformed.
     */
    public static String stableReportMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Cannot report user.";
        }
        String lower = detail.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("yourself") || lower.contains("self")) {
            return "Cannot report yourself.";
        }
        if (lower.contains("not found") || lower.contains("inactive") || lower.contains("active")) {
            return "Report target is not available.";
        }
        if (lower.contains("reason") || lower.contains("description")) {
            return "Invalid report.";
        }
        if (lower.contains("already") || lower.contains("duplicate")) {
            return "User already reported.";
        }
        return "Cannot report user.";
    }

    /**
     * Maps relationship-transition internals to a stable user-facing message. Raw
     * storage/state details stay server-side; the API surface only exposes whether the
     * transition was unavailable or the request was malformed.
     */
    public static String stableRelationshipMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Cannot update relationship.";
        }
        String lower = detail.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("not found")) {
            return "Relationship not found.";
        }
        if (lower.contains("transition") || lower.contains("state") || lower.contains("already")) {
            return "Relationship cannot be updated in its current state.";
        }
        return "Cannot update relationship.";
    }

    private void logModerationAction(String action, UUID actorId, UUID targetUserId, String reason) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            logger.info("Moderation action={} actorId={} targetUserId={}", action, actorId, targetUserId);
            return;
        }
        logger.info("Moderation action={} actorId={} targetUserId={} reason={}", action, actorId, targetUserId, reason);
    }

    private void publishRelationshipTransitionEvent(
            UUID initiatorId,
            UUID targetUserId,
            AppEvent.RelationshipTransitionState fromState,
            AppEvent.RelationshipTransitionState toState) {
        String matchId = Match.generateId(initiatorId, targetUserId);
        EventPublishing.publishOrWarn(
                eventBus,
                new AppEvent.RelationshipTransitioned(
                        matchId, initiatorId, targetUserId, fromState, toState, AppClock.now()),
                "Failed to publish relationship transition event",
                logger);
    }

    public static record RelationshipCommand(UserContext context, UUID targetUserId) {}

    public static record ListBlockedUsersQuery(UserContext context) {}

    public static record BlockedUserSummary(UUID userId, String name) {}

    public static record ReportCommand(
            UserContext context, UUID targetUserId, Report.Reason reason, String description, boolean blockUser) {}

    public static record ReportOutcome(boolean autoFlagged, boolean blockedByReporter) {
        static ReportOutcome from(TrustSafetyService.ReportResult result, boolean blockedByReporter) {
            return new ReportOutcome(result.userWasFlagged(), blockedByReporter);
        }

        public boolean userWasFlagged() {
            return autoFlagged;
        }

        /** @deprecated Use {@link #autoFlagged()}; kept for one release. */
        @Deprecated(since = "1.1", forRemoval = true)
        public boolean autoBanned() {
            return autoFlagged;
        }

        /** @deprecated Use {@link #userWasFlagged()}; kept for one release. */
        @Deprecated(since = "1.1", forRemoval = true)
        public boolean userWasBanned() {
            return autoFlagged;
        }
    }

    public static record FriendRequestsQuery(UserContext context) {}

    public enum FriendRequestAction {
        ACCEPT,
        DECLINE
    }

    public static record RespondFriendRequestCommand(UserContext context, UUID requestId, FriendRequestAction action) {}

    public static record RelationshipTransitionOutcome(FriendRequest friendRequest) {
        static RelationshipTransitionOutcome from(ConnectionService.TransitionResult result) {
            return new RelationshipTransitionOutcome(result.friendRequest());
        }

        public UUID friendRequestId() {
            return friendRequest != null ? friendRequest.id() : null;
        }
    }

    public static record NotificationsQuery(UserContext context, boolean unreadOnly) {}

    public static record MarkNotificationReadCommand(UserContext context, UUID notificationId) {}

    public static record MarkAllNotificationsReadCommand(UserContext context) {}
}
