package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrustSafetyService {

    private static final Logger logger = LoggerFactory.getLogger(TrustSafetyService.class);
    private static final String AUDIT_KEY_BLOCK_REQUESTED = "block_requested";
    private static final String AUDIT_KEY_BAN_PERSISTED = "ban_persisted";
    private static final String AUDIT_KEY_BLOCK_REMOVED = "block_removed";
    private static final String AUDIT_KEY_CONVERSATION_STORAGE_CONFIGURED = "conversation_storage_configured";
    private static final String AUDIT_KEY_DESCRIPTION_LENGTH = "description_length";
    private static final String AUDIT_KEY_DESCRIPTION_PROVIDED = "description_provided";
    private static final String AUDIT_KEY_ERROR_CODE = "error_code";
    private static final String AUDIT_KEY_MATCH_UPDATED = "match_updated";
    private static final String AUDIT_KEY_MAX_DESCRIPTION_LENGTH = "max_description_length";
    private static final String AUDIT_KEY_MODERATION_REASON_CODE = "moderation_reason_code";
    private static final String AUDIT_KEY_REPORT_COUNT = "report_count";
    private static final String AUDIT_KEY_THRESHOLD = "threshold";
    private final TrustSafetyStorage trustSafetyStorage;
    private final OperationalInteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final AppConfig config;
    private final CommunicationStorage communicationStorage; // nullable
    private final RelationshipWorkflowPolicy workflowPolicy;
    private final ModerationAuditLogger moderationAuditLogger = new ModerationAuditLogger();
    private CandidateFinder candidateFinder;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(
            TrustSafetyStorage trustSafetyStorage,
            OperationalInteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config,
            CommunicationStorage communicationStorage) {
        return builder()
                .trustSafetyStorage(trustSafetyStorage)
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .config(config)
                .communicationStorage(communicationStorage);
    }

    public static Builder builder(
            TrustSafetyStorage trustSafetyStorage,
            OperationalInteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config) {
        return builder()
                .trustSafetyStorage(trustSafetyStorage)
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .config(config);
    }

    private TrustSafetyService(Builder builder) {
        Builder resolvedBuilder = Objects.requireNonNull(builder, "builder cannot be null");
        this.trustSafetyStorage =
                Objects.requireNonNull(resolvedBuilder.trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.interactionStorage =
                Objects.requireNonNull(resolvedBuilder.interactionStorage, "interactionStorage cannot be null");
        this.userStorage = Objects.requireNonNull(resolvedBuilder.userStorage, "userStorage cannot be null");
        this.config = Objects.requireNonNull(resolvedBuilder.config, "config cannot be null");
        this.communicationStorage = resolvedBuilder.communicationStorage;
        this.workflowPolicy = Objects.requireNonNull(resolvedBuilder.workflowPolicy, "workflowPolicy cannot be null");
    }

    public static final class Builder {
        private TrustSafetyStorage trustSafetyStorage;
        private OperationalInteractionStorage interactionStorage;
        private UserStorage userStorage;
        private AppConfig config;
        private CommunicationStorage communicationStorage;
        private RelationshipWorkflowPolicy workflowPolicy = new RelationshipWorkflowPolicy();

        private Builder() {}

        public Builder trustSafetyStorage(TrustSafetyStorage trustSafetyStorage) {
            this.trustSafetyStorage = trustSafetyStorage;
            return this;
        }

        public Builder interactionStorage(OperationalInteractionStorage interactionStorage) {
            this.interactionStorage = interactionStorage;
            return this;
        }

        public Builder userStorage(UserStorage userStorage) {
            this.userStorage = userStorage;
            return this;
        }

        public Builder config(AppConfig config) {
            this.config = config;
            return this;
        }

        public Builder communicationStorage(CommunicationStorage communicationStorage) {
            this.communicationStorage = communicationStorage;
            return this;
        }

        public Builder workflowPolicy(RelationshipWorkflowPolicy workflowPolicy) {
            this.workflowPolicy = workflowPolicy;
            return this;
        }

        public TrustSafetyService build() {
            return new TrustSafetyService(this);
        }
    }

    /** Report a user for inappropriate behavior and return moderation action. */
    public ReportResult report(
            UUID reporterId, UUID reportedUserId, Report.Reason reason, String description, boolean blockUser) {

        Optional<ReportResult> invalidRequest =
                validateReportRequest(reporterId, reportedUserId, reason, description, blockUser);
        if (invalidRequest.isPresent()) {
            return invalidRequest.get();
        }

        ReportParticipantValidation participantValidation =
                validateReportParticipants(reporterId, reportedUserId, blockUser);
        if (participantValidation.hasFailure()) {
            return participantValidation.failure();
        }

        Report report = Report.create(reporterId, reportedUserId, reason, description);
        Optional<ReportResult> persisted = persistReport(report, reporterId, reportedUserId, blockUser);
        if (persisted.isPresent()) {
            return persisted.get();
        }

        auditModeration(
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                reporterId,
                reportedUserId,
                contextOf(
                        AUDIT_KEY_MODERATION_REASON_CODE,
                        reason,
                        AUDIT_KEY_BLOCK_REQUESTED,
                        blockUser,
                        AUDIT_KEY_DESCRIPTION_PROVIDED,
                        description != null,
                        AUDIT_KEY_DESCRIPTION_LENGTH,
                        description == null ? null : description.length()));

        boolean autoBanned = applyAutoBanIfThreshold(reportedUserId);

        applyRequestedBlockAfterReport(reporterId, reportedUserId, blockUser);

        return new ReportResult(true, autoBanned, null);
    }

    private Optional<ReportResult> validateReportRequest(
            UUID reporterId, UUID reportedUserId, Report.Reason reason, String description, boolean blockUser) {
        if (reporterId == null) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    null,
                    reportedUserId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "missing_reporter_id", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return Optional.of(new ReportResult(false, false, "reporterId is required"));
        }
        if (reportedUserId == null) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    null,
                    contextOf(AUDIT_KEY_ERROR_CODE, "missing_reported_user_id", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return Optional.of(new ReportResult(false, false, "reportedUserId is required"));
        }
        if (reason == null) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    reportedUserId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "missing_report_reason", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return Optional.of(new ReportResult(false, false, "reason is required"));
        }
        if (reporterId.equals(reportedUserId)) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    reportedUserId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "self_report", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return Optional.of(new ReportResult(false, false, "Cannot report yourself"));
        }
        if (description != null && description.length() > config.validation().maxReportDescLength()) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    reportedUserId,
                    contextOf(
                            AUDIT_KEY_ERROR_CODE,
                            "description_too_long",
                            AUDIT_KEY_BLOCK_REQUESTED,
                            blockUser,
                            AUDIT_KEY_DESCRIPTION_LENGTH,
                            description.length(),
                            AUDIT_KEY_MAX_DESCRIPTION_LENGTH,
                            config.validation().maxReportDescLength()));
            return Optional.of(new ReportResult(
                    false,
                    false,
                    "Description too long (max " + config.validation().maxReportDescLength() + " characters)"));
        }
        return Optional.empty();
    }

    private ReportParticipantValidation validateReportParticipants(
            UUID reporterId, UUID reportedUserId, boolean blockUser) {
        User reporter = userStorage.get(reporterId).orElse(null);
        if (reporter == null || reporter.getState() != UserState.ACTIVE) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    reportedUserId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "inactive_reporter", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return ReportParticipantValidation.failure(new ReportResult(false, false, "Reporter must be active user"));
        }

        User reported = userStorage.get(reportedUserId).orElse(null);
        if (reported == null) {
            auditModeration(
                    ModerationAuditEvent.Action.REPORT,
                    ModerationAuditEvent.Outcome.FAILURE,
                    reporterId,
                    reportedUserId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "reported_user_not_found", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
            return ReportParticipantValidation.failure(new ReportResult(false, false, "Reported user not found"));
        }

        return ReportParticipantValidation.success(new ReportParticipants(reporter, reported));
    }

    private Optional<ReportResult> persistReport(
            Report report, UUID reporterId, UUID reportedUserId, boolean blockUser) {
        try {
            trustSafetyStorage.save(report);
            return Optional.empty();
        } catch (RuntimeException exception) {
            if (isDuplicateKeyViolation(exception)) {
                auditModeration(
                        ModerationAuditEvent.Action.REPORT,
                        ModerationAuditEvent.Outcome.FAILURE,
                        reporterId,
                        reportedUserId,
                        contextOf(AUDIT_KEY_ERROR_CODE, "duplicate_report", AUDIT_KEY_BLOCK_REQUESTED, blockUser));
                return Optional.of(new ReportResult(false, false, "Already reported this user"));
            }
            throw exception;
        }
    }

    private void applyRequestedBlockAfterReport(UUID reporterId, UUID reportedUserId, boolean blockUser) {
        if (!blockUser || trustSafetyStorage.isBlocked(reporterId, reportedUserId)) {
            return;
        }

        try {
            BlockResult blockResult = block(reporterId, reportedUserId);
            if (!blockResult.success() && logger.isWarnEnabled()) {
                logger.warn(
                        "Report succeeded but follow-up block failed (reporterId={}, reportedUserId={}, blockUser={}) with error={}",
                        reporterId,
                        reportedUserId,
                        blockUser,
                        blockResult.errorMessage());
            }
        } catch (RuntimeException exception) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Report succeeded but follow-up block failed (reporterId={}, reportedUserId={}, blockUser={})",
                        reporterId,
                        reportedUserId,
                        blockUser,
                        exception);
            }
        }
    }

    /**
     * Applies auto-ban decision under distributed lock to coordinate concurrent
     * reports across multiple instances.
     */
    private boolean applyAutoBanIfThreshold(UUID reportedUserId) {
        return userStorage.withUserLock(reportedUserId, lockedUsers -> {
            int reportCount = trustSafetyStorage.countReportsAgainst(reportedUserId);
            if (reportCount < config.safety().autoBanThreshold()) {
                auditModeration(
                        ModerationAuditEvent.Action.AUTO_BAN,
                        ModerationAuditEvent.Outcome.FAILURE,
                        null,
                        reportedUserId,
                        contextOf(
                                AUDIT_KEY_REPORT_COUNT,
                                reportCount,
                                AUDIT_KEY_THRESHOLD,
                                config.safety().autoBanThreshold(),
                                AUDIT_KEY_MODERATION_REASON_CODE,
                                "below_threshold"));
                return false;
            }

            User latestReported = lockedUsers
                    .get(reportedUserId)
                    .map(TrustSafetyService::copyUser)
                    .orElse(null);
            if (latestReported == null || latestReported.getState() == UserState.BANNED) {
                auditModeration(
                        ModerationAuditEvent.Action.AUTO_BAN,
                        ModerationAuditEvent.Outcome.FAILURE,
                        null,
                        reportedUserId,
                        contextOf(
                                AUDIT_KEY_REPORT_COUNT,
                                reportCount,
                                AUDIT_KEY_THRESHOLD,
                                config.safety().autoBanThreshold(),
                                AUDIT_KEY_MODERATION_REASON_CODE,
                                latestReported == null ? "user_missing" : "already_banned"));
                return false;
            }

            latestReported.ban();
            try {
                lockedUsers.save(latestReported);
                auditModeration(
                        ModerationAuditEvent.Action.AUTO_BAN,
                        ModerationAuditEvent.Outcome.SUCCESS,
                        null,
                        reportedUserId,
                        contextOf(
                                AUDIT_KEY_REPORT_COUNT,
                                reportCount,
                                AUDIT_KEY_THRESHOLD,
                                config.safety().autoBanThreshold(),
                                AUDIT_KEY_BAN_PERSISTED,
                                true));
                return true;
            } catch (RuntimeException exception) {
                logger.error(
                        "Auto-ban save failed for user {} after {} reports; ban was not persisted",
                        reportedUserId,
                        reportCount,
                        exception);
                auditModeration(
                        ModerationAuditEvent.Action.AUTO_BAN,
                        ModerationAuditEvent.Outcome.FAILURE,
                        null,
                        reportedUserId,
                        contextOf(
                                AUDIT_KEY_REPORT_COUNT,
                                reportCount,
                                AUDIT_KEY_THRESHOLD,
                                config.safety().autoBanThreshold(),
                                AUDIT_KEY_MODERATION_REASON_CODE,
                                "save_failed"));
                return false;
            }
        });
    }

    private static boolean isDuplicateKeyViolation(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null) {
                String upper = message.toUpperCase(Locale.ROOT);
                if (upper.contains("UNIQUE") || upper.contains("DUPLICATE") || upper.contains("CONSTRAINT")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static User copyUser(User user) {
        return user.copy();
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

    private record BlockTransitionData(Optional<Match> updatedMatch, Optional<Conversation> archivedConversation) {}

    private BlockTransitionData prepareBlockTransition(UUID blockerId, UUID blockedId) {
        Optional<Match> updatedMatch = interactionStorage
                .getByUsers(blockerId, blockedId)
                .filter(match -> workflowPolicy.canBlock(match).isAllowed())
                .map(TrustSafetyService::copyMatch)
                .map(match -> {
                    match.block(blockerId);
                    return match;
                });

        Optional<Conversation> archivedConversation = Optional.empty();
        if (communicationStorage != null) {
            archivedConversation = communicationStorage
                    .getConversationByUsers(blockerId, blockedId)
                    .map(Conversation::copyOf)
                    .map(conversation -> {
                        conversation.archive(blockerId, MatchArchiveReason.BLOCK);
                        conversation.setVisibility(blockerId, false);
                        return conversation;
                    });
        }

        return new BlockTransitionData(updatedMatch, archivedConversation);
    }

    private boolean applyBlockTransition(UUID blockerId, UUID blockedId, BlockTransitionData transitionData) {
        boolean persisted = interactionStorage.blockTransition(
                blockerId, blockedId, transitionData.updatedMatch(), transitionData.archivedConversation());
        if (persisted) {
            invalidateCandidateCaches(blockerId, blockedId);
        }
        return persisted;
    }

    private void rollbackBlockSave(UUID blockerId, UUID blockedId) {
        if (!trustSafetyStorage.deleteBlock(blockerId, blockedId) && logger.isWarnEnabled()) {
            logger.warn("Failed to roll back persisted block for blockerId={} blockedId={}", blockerId, blockedId);
        }
    }

    /**
     * Updates the match state to BLOCKED if a match exists between the two users,
     * and archives any existing conversation from the blocker's perspective.
     * Silently succeeds if no match exists.
     */
    public BlockResult block(UUID blockerId, UUID blockedId) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");

        if (blockerId.equals(blockedId)) {
            auditModeration(
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.FAILURE,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "self_block"));
            return new BlockResult(false, "Cannot block yourself");
        }

        User blocker = userStorage.get(blockerId).orElse(null);
        if (blocker == null || blocker.getState() != UserState.ACTIVE) {
            auditModeration(
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.FAILURE,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "inactive_blocker"));
            return new BlockResult(false, "Blocker must be an active user");
        }

        User blocked = userStorage.get(blockedId).orElse(null);
        if (blocked == null) {
            auditModeration(
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.FAILURE,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "blocked_user_not_found"));
            return new BlockResult(false, "User to block not found");
        }

        if (trustSafetyStorage.isBlocked(blockerId, blockedId)) {
            auditModeration(
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.FAILURE,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_ERROR_CODE, "already_blocked"));
            return new BlockResult(false, "User is already blocked");
        }

        BlockTransitionData transitionData = prepareBlockTransition(blockerId, blockedId);
        Block block = Block.create(blockerId, blockedId);

        try {
            trustSafetyStorage.save(block);
            boolean transitionApplied = applyBlockTransition(blockerId, blockedId, transitionData);
            if (!transitionApplied) {
                rollbackBlockSave(blockerId, blockedId);
                auditModeration(
                        ModerationAuditEvent.Action.BLOCK,
                        ModerationAuditEvent.Outcome.FAILURE,
                        blockerId,
                        blockedId,
                        contextOf(AUDIT_KEY_ERROR_CODE, "block_transition_failed"));
                return new BlockResult(false, "Failed to apply block transition");
            }

            auditModeration(
                    ModerationAuditEvent.Action.BLOCK,
                    ModerationAuditEvent.Outcome.SUCCESS,
                    blockerId,
                    blockedId,
                    contextOf(
                            AUDIT_KEY_MATCH_UPDATED,
                            transitionData.updatedMatch().isPresent(),
                            AUDIT_KEY_CONVERSATION_STORAGE_CONFIGURED,
                            communicationStorage != null));

            logger.info("User {} blocked user {}", blockerId, blockedId);
            return new BlockResult(true, null);
        } catch (RuntimeException exception) {
            rollbackBlockSave(blockerId, blockedId);
            throw exception;
        }
    }

    public void setCandidateFinder(CandidateFinder candidateFinder) {
        this.candidateFinder = candidateFinder;
    }

    private void invalidateCandidateCaches(UUID firstUserId, UUID secondUserId) {
        if (candidateFinder == null) {
            return;
        }
        candidateFinder.invalidateCacheFor(firstUserId);
        candidateFinder.invalidateCacheFor(secondUserId);
    }

    /** Result of a block action. */
    public static record BlockResult(boolean success, String errorMessage) {
        public BlockResult {
            if (success && errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null on success");
            }
            if (!success && (errorMessage == null || errorMessage.isBlank())) {
                throw new IllegalArgumentException("errorMessage is required on failure");
            }
        }
    }

    /**
     * Unblocks a previously blocked user.
     *
     * @param blockerId the user who created the block
     * @param blockedId the user who was blocked
     * @return true if the block was removed, false if no block existed
     */
    public boolean unblock(UUID blockerId, UUID blockedId) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");

        boolean deleted = trustSafetyStorage.deleteBlock(blockerId, blockedId);
        if (deleted) {
            logger.info("User {} unblocked user {}", blockerId, blockedId);
            auditModeration(
                    ModerationAuditEvent.Action.UNBLOCK,
                    ModerationAuditEvent.Outcome.SUCCESS,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_BLOCK_REMOVED, true));
        } else {
            logger.debug("No block found between {} and {}", blockerId, blockedId);
            auditModeration(
                    ModerationAuditEvent.Action.UNBLOCK,
                    ModerationAuditEvent.Outcome.FAILURE,
                    blockerId,
                    blockedId,
                    contextOf(AUDIT_KEY_BLOCK_REMOVED, false));
        }
        return deleted;
    }

    private void auditModeration(
            ModerationAuditEvent.Action action,
            ModerationAuditEvent.Outcome outcome,
            UUID actorId,
            UUID targetId,
            Map<String, String> context) {
        moderationAuditLogger.log(
                new ModerationAuditEvent(AppClock.now(), actorId, targetId, action, outcome, context));
    }

    private static Map<String, String> contextOf(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("context keyValues must contain an even number of entries");
        }

        Map<String, String> context = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key == null || value == null) {
                continue;
            }
            context.put(String.valueOf(key), String.valueOf(value));
        }
        return context.isEmpty() ? Map.of() : Map.copyOf(context);
    }

    /**
     * Retrieves all users that the given user has blocked.
     *
     * @param userId the user whose blocked users to retrieve
     * @return list of blocked users
     */
    public List<User> getBlockedUsers(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        return trustSafetyStorage.findByBlocker(userId).stream()
                .map(block -> userStorage.get(block.blockedId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isBlocked(UUID userA, UUID userB) {
        Objects.requireNonNull(userA, "userA cannot be null");
        Objects.requireNonNull(userB, "userB cannot be null");
        return trustSafetyStorage.isBlocked(userA, userB);
    }

    /** Result of a report action, including moderation outcome. */
    public static record ReportResult(boolean success, boolean userWasBanned, String errorMessage) {

        public ReportResult {
            if (success && errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null on success");
            }
            if (!success && (errorMessage == null || errorMessage.isBlank())) {
                throw new IllegalArgumentException("errorMessage is required on failure");
            }
            if (!success && userWasBanned) {
                throw new IllegalArgumentException("userWasBanned cannot be true on failure");
            }
        }
    }

    private record ReportParticipants(User reporter, User reported) {}

    private record ReportParticipantValidation(ReportParticipants participants, ReportResult failure) {
        private static ReportParticipantValidation success(ReportParticipants participants) {
            return new ReportParticipantValidation(participants, null);
        }

        private static ReportParticipantValidation failure(ReportResult failure) {
            return new ReportParticipantValidation(null, failure);
        }

        private boolean hasFailure() {
            return failure != null;
        }
    }
}
