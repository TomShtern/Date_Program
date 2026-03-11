package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrustSafetyService {

    private static final Logger logger = LoggerFactory.getLogger(TrustSafetyService.class);
    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);

    private final TrustSafetyStorage trustSafetyStorage;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final AppConfig config;
    private final CommunicationStorage communicationStorage; // nullable
    private final Duration verificationTtl;
    private final Random random;
    private final RelationshipWorkflowPolicy workflowPolicy;
    private CandidateFinder candidateFinder;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(
            TrustSafetyStorage trustSafetyStorage,
            InteractionStorage interactionStorage,
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
            InteractionStorage interactionStorage,
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
        this.verificationTtl =
                Objects.requireNonNull(resolvedBuilder.verificationTtl, "verificationTtl cannot be null");
        this.random = Objects.requireNonNull(resolvedBuilder.random, "random cannot be null");
        this.workflowPolicy = Objects.requireNonNull(resolvedBuilder.workflowPolicy, "workflowPolicy cannot be null");
    }

    public static final class Builder {
        private TrustSafetyStorage trustSafetyStorage;
        private InteractionStorage interactionStorage;
        private UserStorage userStorage;
        private AppConfig config;
        private CommunicationStorage communicationStorage;
        private Duration verificationTtl = DEFAULT_VERIFICATION_TTL;
        private Random random = new Random();
        private RelationshipWorkflowPolicy workflowPolicy = new RelationshipWorkflowPolicy();

        private Builder() {}

        public Builder trustSafetyStorage(TrustSafetyStorage trustSafetyStorage) {
            this.trustSafetyStorage = trustSafetyStorage;
            return this;
        }

        public Builder interactionStorage(InteractionStorage interactionStorage) {
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

        public Builder verificationTtl(Duration verificationTtl) {
            this.verificationTtl = verificationTtl;
            return this;
        }

        public Builder random(Random random) {
            this.random = random;
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

    /** Generates a six-digit verification code. */
    public String generateVerificationCode() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    /** Returns true if the verification code has expired. */
    public boolean isExpired(Instant sentAt) {
        return sentAt == null || sentAt.plus(verificationTtl).isBefore(AppClock.now());
    }

    /**
     * Validates a user-provided verification code against stored user data.
     *
     * @param user      the user to verify
     * @param inputCode the user-provided code
     * @return true if the code matches and is not expired
     */
    public boolean verifyCode(User user, String inputCode) {
        Objects.requireNonNull(user, "user cannot be null");
        if (inputCode == null || inputCode.isBlank()) {
            return false;
        }

        String expected = user.getVerificationCode();
        if (expected == null) {
            return false;
        }

        return !isExpired(user.getVerificationSentAt()) && expected.equals(inputCode.trim());
    }

    /** Report a user for inappropriate behavior and return moderation action. */
    public ReportResult report(
            UUID reporterId, UUID reportedUserId, Report.Reason reason, String description, boolean blockUser) {

        if (reporterId == null) {
            return new ReportResult(false, false, "reporterId is required");
        }
        if (reportedUserId == null) {
            return new ReportResult(false, false, "reportedUserId is required");
        }
        if (reason == null) {
            return new ReportResult(false, false, "reason is required");
        }
        if (reporterId.equals(reportedUserId)) {
            return new ReportResult(false, false, "Cannot report yourself");
        }
        // Validate description length against configured maximum
        if (description != null && description.length() > config.validation().maxReportDescLength()) {
            return new ReportResult(
                    false,
                    false,
                    "Description too long (max " + config.validation().maxReportDescLength() + " characters)");
        }

        User reporter = userStorage.get(reporterId).orElse(null);
        if (reporter == null || reporter.getState() != UserState.ACTIVE) {
            return new ReportResult(false, false, "Reporter must be active user");
        }

        User reported = userStorage.get(reportedUserId).orElse(null);
        if (reported == null) {
            return new ReportResult(false, false, "Reported user not found");
        }

        if (trustSafetyStorage.hasReported(reporterId, reportedUserId)) {
            return new ReportResult(false, false, "Already reported this user");
        }

        Report report = Report.create(reporterId, reportedUserId, reason, description);
        trustSafetyStorage.save(report);

        boolean autoBanned = applyAutoBanIfThreshold(reportedUserId);

        if (!autoBanned && blockUser && !trustSafetyStorage.isBlocked(reporterId, reportedUserId)) {
            Block block = Block.create(reporterId, reportedUserId);
            trustSafetyStorage.save(block);
            updateMatchStateForBlock(reporterId, reportedUserId);
        }

        return new ReportResult(true, autoBanned, null);
    }

    /**
     * Applies auto-ban decision atomically inside this service instance to avoid
     * check-then-act races during concurrent reports.
     */
    private boolean applyAutoBanIfThreshold(UUID reportedUserId) {
        synchronized (this) {
            int reportCount = trustSafetyStorage.countReportsAgainst(reportedUserId);
            if (reportCount < config.safety().autoBanThreshold()) {
                return false;
            }

            User latestReported = userStorage.get(reportedUserId).orElse(null);
            if (latestReported == null || latestReported.getState() == UserState.BANNED) {
                return false;
            }

            latestReported.ban();
            userStorage.save(latestReported);
            return true;
        }
    }

    /**
     * Updates the match state to BLOCKED if a match exists between the two users,
     * and archives any existing conversation from the blocker's perspective.
     * Silently succeeds if interactionStorage is not configured or no match exists.
     */
    private void updateMatchStateForBlock(UUID blockerId, UUID blockedId) {
        if (interactionStorage == null) {
            logger.debug("InteractionStorage not configured; skipping match state update for block");
            return;
        }
        interactionStorage.getByUsers(blockerId, blockedId).ifPresent(match -> {
            if (workflowPolicy.canBlock(match).isAllowed()) {
                match.block(blockerId);
                interactionStorage.update(match);
                if (logger.isInfoEnabled()) {
                    logger.info("Match {} transitioned to BLOCKED by user {}", match.getId(), blockerId);
                }
            }
        });

        if (communicationStorage != null) {
            communicationStorage.getConversationByUsers(blockerId, blockedId).ifPresent(convo -> {
                communicationStorage.archiveConversation(convo.getId(), blockerId, MatchArchiveReason.BLOCK);
                communicationStorage.setConversationVisibility(convo.getId(), blockerId, false);
            });
        }

        invalidateCandidateCaches(blockerId, blockedId);
    }

    /**
     * Blocks a user without filing a report. Updates both the block storage and
     * any existing match state.
     *
     * @param blockerId the user initiating the block
     * @param blockedId the user being blocked
     * @return result of the block operation
     */
    public BlockResult block(UUID blockerId, UUID blockedId) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");

        if (trustSafetyStorage == null || userStorage == null) {
            throw new IllegalStateException("Block dependencies are not configured");
        }

        if (blockerId.equals(blockedId)) {
            return new BlockResult(false, "Cannot block yourself");
        }

        User blocker = userStorage.get(blockerId).orElse(null);
        if (blocker == null || blocker.getState() != UserState.ACTIVE) {
            return new BlockResult(false, "Blocker must be an active user");
        }

        User blocked = userStorage.get(blockedId).orElse(null);
        if (blocked == null) {
            return new BlockResult(false, "User to block not found");
        }

        if (trustSafetyStorage.isBlocked(blockerId, blockedId)) {
            return new BlockResult(false, "User is already blocked");
        }

        Block block = Block.create(blockerId, blockedId);
        trustSafetyStorage.save(block);
        updateMatchStateForBlock(blockerId, blockedId);

        logger.info("User {} blocked user {}", blockerId, blockedId);
        return new BlockResult(true, null);
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

        if (trustSafetyStorage == null) {
            throw new IllegalStateException("TrustSafetyStorage is not configured");
        }

        boolean deleted = trustSafetyStorage.deleteBlock(blockerId, blockedId);
        if (deleted) {
            logger.info("User {} unblocked user {}", blockerId, blockedId);
        } else {
            logger.debug("No block found between {} and {}", blockerId, blockedId);
        }
        return deleted;
    }

    /**
     * Retrieves all users that the given user has blocked.
     *
     * @param userId the user whose blocked users to retrieve
     * @return list of blocked users
     */
    public List<User> getBlockedUsers(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        if (trustSafetyStorage == null || userStorage == null) {
            throw new IllegalStateException("TrustSafetyStorage or UserStorage is not configured");
        }

        return trustSafetyStorage.findByBlocker(userId).stream()
                .map(block -> userStorage.get(block.blockedId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
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
}
