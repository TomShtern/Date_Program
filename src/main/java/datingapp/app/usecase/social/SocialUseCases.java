package datingapp.app.usecase.social;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
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

    private enum CompatibilityMode {
        COMPATIBILITY
    }

    private static final AppEventBus COMPATIBILITY_NO_OP_EVENT_BUS = new AppEventBus() {
        @Override
        public void publish(AppEvent event) {
            // Compatibility shim for non-production construction paths.
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

    private enum RelationshipTransitionState {
        MATCHED,
        UNMATCHED,
        FRIEND_ZONE_REQUESTED,
        ACTIVE,
        GRACEFUL_EXIT
    }

    private final ConnectionService connectionService;
    private final TrustSafetyService trustSafetyService;
    private final CommunicationStorage communicationStorage;
    private final AppEventBus eventBus;

    public SocialUseCases(ConnectionService connectionService, TrustSafetyService trustSafetyService) {
        this(
                connectionService,
                trustSafetyService,
                null,
                COMPATIBILITY_NO_OP_EVENT_BUS,
                CompatibilityMode.COMPATIBILITY);
    }

    public SocialUseCases(TrustSafetyService trustSafetyService) {
        this(null, trustSafetyService, null, COMPATIBILITY_NO_OP_EVENT_BUS, CompatibilityMode.COMPATIBILITY);
    }

    public SocialUseCases(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage) {
        this(
                connectionService,
                trustSafetyService,
                communicationStorage,
                COMPATIBILITY_NO_OP_EVENT_BUS,
                CompatibilityMode.COMPATIBILITY);
    }

    public SocialUseCases(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage,
            AppEventBus eventBus) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        this.communicationStorage = Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    private SocialUseCases(
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            CommunicationStorage communicationStorage,
            AppEventBus eventBus,
            CompatibilityMode compatibilityMode) {
        Objects.requireNonNull(compatibilityMode, "compatibilityMode cannot be null");
        this.connectionService = connectionService;
        this.trustSafetyService = trustSafetyService;
        this.communicationStorage = communicationStorage;
        this.eventBus = eventBus;
    }

    public UseCaseResult<TrustSafetyService.BlockResult> blockUser(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_TARGET_REQUIRED));
        }
        if (trustSafetyService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("TrustSafetyService is not configured"));
        }
        try {
            TrustSafetyService.BlockResult result =
                    trustSafetyService.block(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            logModerationAction("block", command.context().userId(), command.targetUserId(), null);
            publishEvent(
                    "user blocked",
                    new AppEvent.UserBlocked(command.context().userId(), command.targetUserId(), AppClock.now()));
            return UseCaseResult.success(result);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to block user: " + e.getMessage()));
        }
    }

    public UseCaseResult<ReportOutcome> reportUser(ReportCommand command) {
        if (command == null
                || command.context() == null
                || command.targetUserId() == null
                || command.reason() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, target user and reason are required"));
        }
        if (trustSafetyService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("TrustSafetyService is not configured"));
        }
        try {
            TrustSafetyService.ReportResult result = trustSafetyService.report(
                    command.context().userId(),
                    command.targetUserId(),
                    command.reason(),
                    command.description(),
                    command.blockUser());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            logModerationAction(
                    "report",
                    command.context().userId(),
                    command.targetUserId(),
                    command.reason().name());
            publishEvent(
                    "user reported",
                    new AppEvent.UserReported(
                            command.context().userId(),
                            command.targetUserId(),
                            command.reason().name(),
                            command.blockUser(),
                            false,
                            AppClock.now()));
            return UseCaseResult.success(ReportOutcome.from(result, command.blockUser()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to report user: " + e.getMessage()));
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> unmatch(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_TARGET_REQUIRED));
        }
        if (connectionService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(CONNECTION_SERVICE_NOT_CONFIGURED));
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.unmatch(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    RelationshipTransitionState.MATCHED,
                    RelationshipTransitionState.UNMATCHED);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to unmatch users: " + e.getMessage()));
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> requestFriendZone(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_TARGET_REQUIRED));
        }
        if (connectionService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(CONNECTION_SERVICE_NOT_CONFIGURED));
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.requestFriendZone(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    RelationshipTransitionState.MATCHED,
                    RelationshipTransitionState.FRIEND_ZONE_REQUESTED);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to request friend-zone transition: " + e.getMessage()));
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> gracefulExit(RelationshipCommand command) {
        if (command == null || command.context() == null || command.targetUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_TARGET_REQUIRED));
        }
        if (connectionService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(CONNECTION_SERVICE_NOT_CONFIGURED));
        }
        try {
            ConnectionService.TransitionResult result =
                    connectionService.gracefulExit(command.context().userId(), command.targetUserId());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            publishRelationshipTransitionEvent(
                    command.context().userId(),
                    command.targetUserId(),
                    RelationshipTransitionState.ACTIVE,
                    RelationshipTransitionState.GRACEFUL_EXIT);
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to gracefully exit relationship: " + e.getMessage()));
        }
    }

    public UseCaseResult<List<FriendRequest>> pendingFriendRequests(FriendRequestsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (connectionService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(CONNECTION_SERVICE_NOT_CONFIGURED));
        }
        try {
            return UseCaseResult.success(
                    connectionService.getPendingRequestsFor(query.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to load pending friend requests: " + e.getMessage()));
        }
    }

    public UseCaseResult<RelationshipTransitionOutcome> respondToFriendRequest(RespondFriendRequestCommand command) {
        if (command == null || command.context() == null || command.requestId() == null || command.action() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, requestId and action are required"));
        }
        if (connectionService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(CONNECTION_SERVICE_NOT_CONFIGURED));
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
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            if (command.action() == FriendRequestAction.ACCEPT && result.friendRequest() != null) {
                var req = result.friendRequest();
                String matchId = Match.generateId(req.fromUserId(), req.toUserId());
                publishEvent(
                        "friend request accepted",
                        new AppEvent.FriendRequestAccepted(
                                req.id(), req.fromUserId(), req.toUserId(), matchId, AppClock.now()));
            }
            return UseCaseResult.success(RelationshipTransitionOutcome.from(result));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to respond to friend request: " + e.getMessage()));
        }
    }

    public UseCaseResult<List<Notification>> notifications(NotificationsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (communicationStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(COMMUNICATION_STORAGE_NOT_CONFIGURED));
        }
        try {
            return UseCaseResult.success(
                    communicationStorage.getNotificationsForUser(query.context().userId(), query.unreadOnly()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load notifications: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> markNotificationRead(MarkNotificationReadCommand command) {
        if (command == null || command.context() == null || command.notificationId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and notificationId are required"));
        }
        if (communicationStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(COMMUNICATION_STORAGE_NOT_CONFIGURED));
        }
        try {
            Notification notification = communicationStorage
                    .getNotification(command.notificationId())
                    .orElse(null);
            if (notification == null) {
                return UseCaseResult.failure(UseCaseError.notFound("Notification not found"));
            }
            if (!notification.userId().equals(command.context().userId())) {
                return UseCaseResult.failure(UseCaseError.forbidden("Notification does not belong to current user"));
            }
            communicationStorage.markNotificationAsRead(command.context().userId(), command.notificationId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to mark notification as read: " + e.getMessage()));
        }
    }

    public UseCaseResult<Integer> markAllNotificationsRead(MarkAllNotificationsReadCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (communicationStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(COMMUNICATION_STORAGE_NOT_CONFIGURED));
        }
        try {
            return UseCaseResult.success(communicationStorage.markAllNotificationsAsRead(
                    command.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to mark notifications as read: " + e.getMessage()));
        }
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
            RelationshipTransitionState fromState,
            RelationshipTransitionState toState) {
        String matchId = Match.generateId(initiatorId, targetUserId);
        publishEvent(
                "relationship transition",
                new AppEvent.RelationshipTransitioned(
                        matchId, initiatorId, targetUserId, fromState.name(), toState.name(), AppClock.now()));
    }

    private void publishEvent(String eventName, AppEvent event) {
        try {
            eventBus.publish(event);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to publish {} event: {}", eventName, e.getMessage(), e);
            }
        }
    }

    public static record RelationshipCommand(UserContext context, UUID targetUserId) {}

    public static record ReportCommand(
            UserContext context, UUID targetUserId, Report.Reason reason, String description, boolean blockUser) {}

    public static record ReportOutcome(boolean autoBanned, boolean blockedByReporter) {
        static ReportOutcome from(TrustSafetyService.ReportResult result, boolean blockedByReporter) {
            return new ReportOutcome(result.userWasBanned(), blockedByReporter);
        }

        public boolean userWasBanned() {
            return autoBanned;
        }
    }

    public static record FriendRequestsQuery(UserContext context) {}

    public static enum FriendRequestAction {
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
