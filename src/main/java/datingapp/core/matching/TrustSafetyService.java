package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustSafetyService {

    private static final Logger logger = LoggerFactory.getLogger(TrustSafetyService.class);
    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);

    private final TrustSafetyStorage trustSafetyStorage;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final AppConfig config;
    private final CommunicationStorage communicationStorage; // nullable
    private final Duration verificationTtl;
    private final Random random;

    /** Convenience constructor without communication storage (for tests and simple setups). */
    public TrustSafetyService(
            TrustSafetyStorage trustSafetyStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config) {
        this(trustSafetyStorage, interactionStorage, userStorage, config, null, DEFAULT_VERIFICATION_TTL, new Random());
    }

    /** Constructor with communication storage for full conversation archiving on block. */
    public TrustSafetyService(
            TrustSafetyStorage trustSafetyStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config,
            CommunicationStorage communicationStorage) {
        this(
                trustSafetyStorage,
                interactionStorage,
                userStorage,
                config,
                communicationStorage,
                DEFAULT_VERIFICATION_TTL,
                new Random());
    }

    /** Full constructor with all dependencies. */
    public TrustSafetyService(
            TrustSafetyStorage trustSafetyStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config,
            Duration verificationTtl,
            Random random) {
        this(trustSafetyStorage, interactionStorage, userStorage, config, null, verificationTtl, random);
    }

    /** Canonical constructor — all dependencies explicit. */
    public TrustSafetyService(
            TrustSafetyStorage trustSafetyStorage,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            AppConfig config,
            CommunicationStorage communicationStorage,
            Duration verificationTtl,
            Random random) {
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.communicationStorage = communicationStorage;
        this.verificationTtl = Objects.requireNonNull(verificationTtl, "verificationTtl cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
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
        if (description != null && description.length() > config.maxReportDescLength()) {
            return new ReportResult(
                    false, false, "Description too long (max " + config.maxReportDescLength() + " characters)");
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
            if (reportCount < config.autoBanThreshold()) {
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
            if (match.getState() != MatchState.BLOCKED) {
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
